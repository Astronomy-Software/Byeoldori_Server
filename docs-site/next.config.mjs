import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
const config = {
  reactStrictMode: true,
  output: 'export',
  typescript: { ignoreBuildErrors: true },
  eslint: { ignoreDuringBuilds: true },
  turbopack: {
    resolveAlias: {
      'fumadocs-mdx:collections/server': './.source/server.ts',
      'fumadocs-mdx:collections/browser': './.source/browser.ts',
      'fumadocs-mdx:collections/dynamic': './.source/dynamic.ts',
    },
  },
};

export default withMDX(config);
