// @ts-nocheck
import { browser } from 'fumadocs-mdx/runtime/browser';
import type * as Config from '../source.config';

const create = browser<typeof Config, import("fumadocs-mdx/runtime/types").InternalTypeConfig & {
  DocData: {
  }
}>();
const browserCollections = {
  docs: create.doc("docs", {"index.mdx": () => import("../content/docs/index.mdx?collection=docs"), "guide/authentication.mdx": () => import("../content/docs/guide/authentication.mdx?collection=docs"), "guide/calendar.mdx": () => import("../content/docs/guide/calendar.mdx?collection=docs"), "guide/community.mdx": () => import("../content/docs/guide/community.mdx?collection=docs"), "guide/file-upload.mdx": () => import("../content/docs/guide/file-upload.mdx?collection=docs"), "guide/notifications.mdx": () => import("../content/docs/guide/notifications.mdx?collection=docs"), }),
};
export default browserCollections;