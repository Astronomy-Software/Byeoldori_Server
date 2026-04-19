import { generateFiles } from 'fumadocs-openapi';
import { createOpenAPI } from 'fumadocs-openapi/server';

const openapi = createOpenAPI({
  input: ['./openapi.json'],
});

await generateFiles({
  input: openapi,
  output: './content/docs/api',
});

console.log('API docs generated from openapi.json');
