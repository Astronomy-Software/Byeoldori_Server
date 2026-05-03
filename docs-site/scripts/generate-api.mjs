import { copyFileSync, mkdirSync, rmSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { createOpenAPI } from 'fumadocs-openapi/server';
import { generateFiles } from 'fumadocs-openapi';

const __dirname = dirname(fileURLToPath(import.meta.url));
const src = resolve(__dirname, '../openapi.json');

// public/에 복사 (Scalar 등 다른 도구용 fallback)
const dest = resolve(__dirname, '../public/openapi.json');
mkdirSync(dirname(dest), { recursive: true });
copyFileSync(src, dest);
console.log('openapi.json → public/openapi.json 복사 완료');

// 기존 생성 파일 초기화
const apiOutputDir = resolve(__dirname, '../content/docs/api');
rmSync(apiOutputDir, { recursive: true, force: true });

// tag별 MDX 생성
const openapi = createOpenAPI({ input: [src] });

await generateFiles({
  input: openapi,
  output: apiOutputDir,
  per: 'tag',
  meta: true,
});

console.log('API Reference MDX 생성 완료 →', apiOutputDir);
