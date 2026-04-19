import { generateFiles } from 'fumadocs-openapi';

await generateFiles({
  input: ['./openapi.json'],
  output: './content/docs/api',
  groupBy: 'tag',
});

console.log('API docs generated from openapi.json');
