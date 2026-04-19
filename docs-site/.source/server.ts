// @ts-nocheck
import * as __fd_glob_7 from "../content/docs/guide/notifications.mdx?collection=docs"
import * as __fd_glob_6 from "../content/docs/guide/file-upload.mdx?collection=docs"
import * as __fd_glob_5 from "../content/docs/guide/community.mdx?collection=docs"
import * as __fd_glob_4 from "../content/docs/guide/calendar.mdx?collection=docs"
import * as __fd_glob_3 from "../content/docs/guide/authentication.mdx?collection=docs"
import * as __fd_glob_2 from "../content/docs/index.mdx?collection=docs"
import { default as __fd_glob_1 } from "../content/docs/guide/meta.json?collection=docs"
import { default as __fd_glob_0 } from "../content/docs/meta.json?collection=docs"
import { server } from 'fumadocs-mdx/runtime/server';
import type * as Config from '../source.config';

const create = server<typeof Config, import("fumadocs-mdx/runtime/types").InternalTypeConfig & {
  DocData: {
  }
}>({"doc":{"passthroughs":["extractedReferences"]}});

export const docs = await create.docs("docs", "content/docs", {"meta.json": __fd_glob_0, "guide/meta.json": __fd_glob_1, }, {"index.mdx": __fd_glob_2, "guide/authentication.mdx": __fd_glob_3, "guide/calendar.mdx": __fd_glob_4, "guide/community.mdx": __fd_glob_5, "guide/file-upload.mdx": __fd_glob_6, "guide/notifications.mdx": __fd_glob_7, });