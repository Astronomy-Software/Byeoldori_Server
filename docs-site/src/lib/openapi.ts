import openapiSpec from '../../openapi.json';
import { createOpenAPI } from 'fumadocs-openapi/server';
import { createAPIPage } from 'fumadocs-openapi/ui';

export const openapi = createOpenAPI({
  input: () => ({ openapi: openapiSpec as never }),
});

export const APIPage = createAPIPage(openapi);
