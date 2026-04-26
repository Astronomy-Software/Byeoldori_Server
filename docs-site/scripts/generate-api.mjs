import { copyFileSync, mkdirSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const src = resolve(__dirname, '../openapi.json');
const dest = resolve(__dirname, '../public/openapi.json');

mkdirSync(dirname(dest), { recursive: true });
copyFileSync(src, dest);
console.log('openapi.json → public/openapi.json 복사 완료');
