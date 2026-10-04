import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {PUBLIC_PAGES,renderPublicShell,replaceShell} from './public-shell.mjs';
const check=process.argv.includes('--check');
let drift=false;
for (const [file,pagePath] of Object.entries(PUBLIC_PAGES)) {
    const path=fileURLToPath(new URL(`../src/main/resources/static/${file}`,import.meta.url));
    const source=readFileSync(path,'utf8');
    if (!source.includes('/js/site-shell.js?v=20261004-1')) {
        throw new Error(`Missing shell enhancer: ${file}`);
    }
    const result=replaceShell(source,renderPublicShell({pagePath}));
    if (result===source) continue;
    if (check) {console.error(`Shell drift: ${file}`);drift=true;}
    else writeFileSync(path,result);
}
if (drift) process.exitCode=1;
