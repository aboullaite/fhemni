import { createHash, randomUUID } from 'node:crypto';
import { readFile, writeFile, mkdir, rename, unlink } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import projection from './election-history-campaign-data.js';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const defaultSourcePath = path.join(repoRoot,
    'src/main/resources/static/data/elections/history.json');
const defaultOutputPath = path.join(repoRoot,
    'docs/social/election-history-launch/campaign-data.json');

export async function writeCampaignData({
    sourcePath = defaultSourcePath,
    outputPath = defaultOutputPath
} = {}) {
    const source = await readFile(sourcePath);
    const sourceSha256 = createHash('sha256').update(source).digest('hex');
    const payload = JSON.parse(source.toString('utf8'));
    const data = projection.buildCampaignData(payload, { sourceSha256 });
    const serialized = JSON.stringify(data, null, 2) + '\n';
    await mkdir(path.dirname(outputPath), { recursive: true });
    const temporaryPath = `${outputPath}.${process.pid}.${randomUUID()}.tmp`;
    try {
        await writeFile(temporaryPath, serialized, { flag: 'wx' });
        await rename(temporaryPath, outputPath);
    } catch (error) {
        await unlink(temporaryPath).catch(cleanupError => {
            if (cleanupError.code !== 'ENOENT') throw cleanupError;
        });
        throw error;
    }
    return data;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        const data = await writeCampaignData();
        console.log(`Wrote ${defaultOutputPath} from history.json SHA-256 ${data.provenance.sourceSha256}`);
    } catch (error) {
        console.error(error);
        process.exitCode = 1;
    }
}
