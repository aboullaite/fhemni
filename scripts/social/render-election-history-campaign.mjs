import { mkdir, rename, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const CAMPAIGN_ASSETS = Object.freeze([
    Object.freeze({ rootId: 'campaign-01-overview', filename: '01-overview.png', width: 1600, height: 900 }),
    Object.freeze({ rootId: 'campaign-02-parties', filename: '02-party-movement.png', width: 1600, height: 900 }),
    Object.freeze({ rootId: 'campaign-03-regions', filename: '03-regions.png', width: 1600, height: 900 }),
    Object.freeze({ rootId: 'campaign-04-profiles', filename: '04-profiles.png', width: 1600, height: 900 }),
    Object.freeze({ rootId: 'campaign-05-quotient', filename: '05-quotient.png', width: 1600, height: 900 }),
    Object.freeze({ rootId: 'campaign-story', filename: 'instagram-story.png', width: 1080, height: 1920 })
]);

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

export function readPngDimensions(buffer) {
    if (!Buffer.isBuffer(buffer) || buffer.length < PNG_SIGNATURE.length) {
        throw new Error('PNG is too short to contain a signature');
    }
    if (!buffer.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)) {
        throw new Error('Invalid PNG signature');
    }
    if (buffer.length < 33) throw new Error('PNG is too short to contain a complete IHDR chunk');
    if (buffer.readUInt32BE(8) !== 13 || buffer.toString('ascii', 12, 16) !== 'IHDR') {
        throw new Error('PNG has a malformed or missing IHDR chunk');
    }
    const width = buffer.readUInt32BE(16);
    const height = buffer.readUInt32BE(20);
    if (!width || !height) throw new Error('PNG dimensions must be greater than zero');
    return { width, height };
}

export function assertCaptureReady(status) {
    if (!status || typeof status !== 'object') throw new Error('Campaign readiness result is missing');
    if (status.dataError) throw new Error(`Campaign data failed to load: ${status.dataError}`);
    if (!status.fontsReady) throw new Error('Campaign fonts are not ready');
    if (!status.campaignReady) throw new Error('Campaign ready flag is not set');
    if (!Array.isArray(status.logos) || status.logos.length !== CAMPAIGN_ASSETS.length
            || status.logos.some(logo => !logo.complete || logo.naturalWidth <= 0
                || logo.naturalHeight <= 0)) {
        throw new Error('Campaign logo is missing or failed to decode');
    }
    if (!Array.isArray(status.roots)) throw new Error('Campaign roots are missing');
    for (const asset of CAMPAIGN_ASSETS) {
        const root = status.roots.find(candidate => candidate.id === asset.rootId);
        if (!root || !root.ready) throw new Error(`Campaign root is not ready: ${asset.rootId}`);
        if (!(root.width > 0 && root.height > 0)) {
            throw new Error(`Campaign root has empty bounds: ${asset.rootId}`);
        }
        if (root.width !== asset.width || root.height !== asset.height) {
            throw new Error(`Campaign root has incorrect bounds: ${asset.rootId} `
                + `(${root.width}x${root.height})`);
        }
    }
}

export function assertCaptureBounds(bounds, asset) {
    if (bounds.width !== asset.width || bounds.height !== asset.height) {
        throw new Error(`Campaign root changed size before capture: ${asset.rootId}`);
    }
    if (bounds.x !== 0 || bounds.y !== 0) {
        throw new Error(`Campaign root is not at the viewport origin: ${asset.rootId} `
            + `(${bounds.x},${bounds.y})`);
    }
}

class CdpClient {
    constructor(socket) {
        this.socket = socket;
        this.nextId = 1;
        this.pending = new Map();
        socket.addEventListener('message', event => this.receive(event));
        socket.addEventListener('close', () => this.rejectPending(new Error('CDP connection closed')));
        socket.addEventListener('error', () => this.rejectPending(new Error('CDP connection failed')));
    }

    static async connect(webSocketUrl) {
        const socket = new WebSocket(webSocketUrl);
        await new Promise((resolve, reject) => {
            socket.addEventListener('open', resolve, { once: true });
            socket.addEventListener('error', reject, { once: true });
        });
        return new CdpClient(socket);
    }

    receive(event) {
        let message;
        try {
            message = JSON.parse(typeof event.data === 'string'
                ? event.data : Buffer.from(event.data).toString('utf8'));
        } catch (error) {
            this.rejectPending(error);
            return;
        }
        if (!message.id) return;
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        if (message.error) {
            pending.reject(new Error(`${pending.method}: ${message.error.message}`));
        } else {
            pending.resolve(message.result || {});
        }
    }

    rejectPending(error) {
        for (const pending of this.pending.values()) pending.reject(error);
        this.pending.clear();
    }

    send(method, params = {}) {
        const id = this.nextId++;
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject, method });
            try {
                this.socket.send(JSON.stringify({ id, method, params }));
            } catch (error) {
                this.pending.delete(id);
                reject(error);
            }
        });
    }

    close() {
        this.socket.close();
    }
}

function endpoint(cdpUrl, pathname) {
    return new URL(pathname, cdpUrl.endsWith('/') ? cdpUrl : `${cdpUrl}/`);
}

async function createTarget(cdpUrl) {
    const url = endpoint(cdpUrl, 'json/new');
    url.search = 'about%3Ablank';
    const response = await fetch(url, { method: 'PUT' });
    if (!response.ok) {
        throw new Error(`Could not create CDP target: ${response.status} ${await response.text()}`);
    }
    const target = await response.json();
    if (!target.webSocketDebuggerUrl) throw new Error('CDP target has no WebSocket debugger URL');
    return target;
}

async function closeTarget(cdpUrl, targetId) {
    if (!targetId) return;
    try {
        await fetch(endpoint(cdpUrl, `json/close/${encodeURIComponent(targetId)}`));
    } catch {
        // The page target may already have closed with the WebSocket.
    }
}

function readinessExpression(timeoutMs) {
    const assets = JSON.stringify(CAMPAIGN_ASSETS);
    return `(() => {
        const assets = ${assets};
        const timeoutMs = ${timeoutMs};
        return new Promise((resolve, reject) => {
            const deadline = performance.now() + timeoutMs;
            const inspect = async () => {
                const dataError = document.body?.dataset?.campaignError || null;
                if (dataError) {
                    reject(new Error('Campaign data failed to load: ' + dataError));
                    return;
                }
                const fontsReady = document.fonts?.status === 'loaded';
                const campaignReady = window.__FHEMNI_CAMPAIGN_READY__ === true;
                if (document.readyState === 'complete' && fontsReady && campaignReady) {
                    try {
                        const logoNodes = [...document.querySelectorAll('.campaign-logo')];
                        await Promise.all(logoNodes.map(image => image.decode()));
                        resolve({
                            fontsReady: document.fonts.status === 'loaded',
                            campaignReady: window.__FHEMNI_CAMPAIGN_READY__ === true,
                            dataError: document.body?.dataset?.campaignError || null,
                            logos: logoNodes.map(image => ({
                                complete: image.complete,
                                naturalWidth: image.naturalWidth,
                                naturalHeight: image.naturalHeight
                            })),
                            roots: assets.map(asset => {
                                const node = document.getElementById(asset.rootId);
                                const rect = node?.getBoundingClientRect();
                                return {
                                    id: asset.rootId,
                                    ready: node?.dataset.ready === 'true',
                                    width: rect?.width || 0,
                                    height: rect?.height || 0
                                };
                            })
                        });
                    } catch (error) {
                        reject(error);
                    }
                    return;
                }
                if (performance.now() >= deadline) {
                    reject(new Error('Timed out waiting for fonts, campaign data, and ready flags'));
                    return;
                }
                setTimeout(inspect, 50);
            };
            inspect();
        });
    })()`;
}

async function evaluate(client, expression, { awaitPromise = false } = {}) {
    const response = await client.send('Runtime.evaluate', {
        expression,
        awaitPromise,
        returnByValue: true
    });
    if (response.exceptionDetails) {
        const description = response.exceptionDetails.exception?.description
            || response.exceptionDetails.text || 'Browser evaluation failed';
        throw new Error(description);
    }
    return response.result?.value;
}

async function isolateRoot(client, asset) {
    const value = await evaluate(client, `(() => {
        const node = document.getElementById(${JSON.stringify(asset.rootId)});
        if (!node) throw new Error('Missing campaign root: ${asset.rootId}');
        document.documentElement.style.margin = '0';
        document.documentElement.style.padding = '0';
        document.documentElement.style.overflow = 'hidden';
        document.body.style.margin = '0';
        document.body.style.padding = '0';
        document.body.style.overflow = 'hidden';
        document.body.style.direction = 'ltr';
        const sheet = document.querySelector('.campaign-sheet');
        sheet.style.display = 'block';
        sheet.style.width = ${JSON.stringify(`${asset.width}px`)};
        sheet.style.height = ${JSON.stringify(`${asset.height}px`)};
        sheet.style.margin = '0';
        sheet.style.padding = '0';
        [...document.querySelectorAll('.campaign-card')].forEach(card => {
            card.style.display = card === node ? 'block' : 'none';
        });
        node.style.position = 'fixed';
        node.style.top = '0';
        node.style.right = 'auto';
        node.style.bottom = 'auto';
        node.style.left = '0';
        node.style.margin = '0';
        node.style.transform = 'none';
        const rect = node.getBoundingClientRect();
        return {
            x: rect.left,
            y: rect.top,
            width: rect.width,
            height: rect.height
        };
    })()`);
    if (!(value?.width > 0 && value?.height > 0)) {
        throw new Error(`Campaign root has empty bounds: ${asset.rootId}`);
    }
    return value;
}

async function captureAssets(client) {
    const captures = [];
    for (const asset of CAMPAIGN_ASSETS) {
        await client.send('Emulation.setDeviceMetricsOverride', {
            width: asset.width,
            height: asset.height,
            deviceScaleFactor: 1,
            mobile: false
        });
        const bounds = await isolateRoot(client, asset);
        assertCaptureBounds(bounds, asset);
        const screenshot = await client.send('Page.captureScreenshot', {
            format: 'png',
            fromSurface: true,
            captureBeyondViewport: true,
            clip: { ...bounds, scale: 1 }
        });
        const buffer = Buffer.from(screenshot.data || '', 'base64');
        const dimensions = readPngDimensions(buffer);
        if (dimensions.width !== asset.width || dimensions.height !== asset.height) {
            throw new Error(`Captured ${asset.filename} at ${dimensions.width}x${dimensions.height}; `
                + `expected ${asset.width}x${asset.height}`);
        }
        captures.push({ asset, buffer, dimensions });
    }
    return captures;
}

async function writeCaptures(outputDir, captures) {
    await mkdir(outputDir, { recursive: true });
    const temporary = [];
    try {
        for (const capture of captures) {
            const finalPath = path.join(outputDir, capture.asset.filename);
            const temporaryPath = `${finalPath}.${process.pid}.${crypto.randomUUID()}.tmp`;
            await writeFile(temporaryPath, capture.buffer);
            temporary.push({ temporaryPath, finalPath, capture });
        }
        const results = [];
        for (const item of temporary) {
            await rename(item.temporaryPath, item.finalPath);
            results.push({
                rootId: item.capture.asset.rootId,
                filename: item.capture.asset.filename,
                path: item.finalPath,
                width: item.capture.dimensions.width,
                height: item.capture.dimensions.height,
                bytes: item.capture.buffer.length
            });
        }
        return results;
    } finally {
        await Promise.all(temporary.map(item => unlink(item.temporaryPath).catch(() => {})));
    }
}

export async function renderCampaignAssets({ cdpUrl, pageUrl, outputDir }) {
    if (!cdpUrl || !pageUrl || !outputDir) {
        throw new TypeError('renderCampaignAssets requires cdpUrl, pageUrl, and outputDir');
    }
    const target = await createTarget(cdpUrl);
    let client;
    try {
        client = await CdpClient.connect(target.webSocketDebuggerUrl);
        await client.send('Page.enable');
        await client.send('Runtime.enable');
        const navigation = await client.send('Page.navigate', { url: pageUrl });
        if (navigation.errorText) throw new Error(`Campaign navigation failed: ${navigation.errorText}`);
        const status = await evaluate(client, readinessExpression(15000), { awaitPromise: true });
        assertCaptureReady(status);
        return await writeCaptures(path.resolve(outputDir), await captureAssets(client));
    } finally {
        if (client) client.close();
        await closeTarget(cdpUrl, target.id);
    }
}

function parseArguments(argv) {
    const options = {
        cdpUrl: 'http://127.0.0.1:9333',
        pageUrl: 'http://127.0.0.1:8099/docs/social/election-history-launch/campaign.html',
        outputDir: 'docs/social/election-history-launch/assets'
    };
    for (let index = 0; index < argv.length; index += 1) {
        const flag = argv[index];
        const value = argv[index + 1];
        if (flag === '--cdp') options.cdpUrl = value;
        else if (flag === '--page') options.pageUrl = value;
        else if (flag === '--output') options.outputDir = value;
        else throw new Error(`Unknown renderer argument: ${flag}`);
        if (!value) throw new Error(`Missing value for renderer argument: ${flag}`);
        index += 1;
    }
    return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        const results = await renderCampaignAssets(parseArguments(process.argv.slice(2)));
        for (const result of results) {
            process.stdout.write(`${result.filename}: ${result.width}x${result.height} `
                + `(${result.bytes} bytes)\n`);
        }
    } catch (error) {
        process.stderr.write(`${error.stack || error.message}\n`);
        process.exitCode = 1;
    }
}
