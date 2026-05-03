import { createOpenAPI } from 'fumadocs-openapi/server';
import { createAPIPage } from 'fumadocs-openapi/ui';
import path from 'path';

export const openapi = createOpenAPI({
  input: [path.resolve(process.cwd(), 'openapi.json')],
});

export const APIPage = createAPIPage(openapi);
