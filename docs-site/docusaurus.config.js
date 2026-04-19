// @ts-check
const { themes } = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: '별도리 API',
  tagline: '별도리 백엔드 API 문서',
  favicon: 'img/favicon.ico',
  url: 'https://byeoldori-docs.pages.dev',
  baseUrl: '/',
  organizationName: 'Astronomy-Software',
  projectName: 'Byeoldori_Server',
  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',
  i18n: { defaultLocale: 'ko', locales: ['ko'] },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          docItemComponent: '@theme/ApiItem',
        },
        blog: false,
        theme: { customCss: './src/css/custom.css' },
      }),
    ],
  ],

  plugins: [
    [
      'docusaurus-plugin-openapi-docs',
      {
        id: 'api',
        docsPluginId: 'classic',
        config: {
          byeoldori: {
            specPath: 'openapi.json',
            outputDir: 'docs/api',
            sidebarOptions: { groupPathsBy: 'tag' },
          },
        },
      },
    ],
  ],

  themes: ['docusaurus-theme-openapi-docs'],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: '⭐ 별도리 API',
        items: [
          { to: '/docs/api', label: 'API Reference', position: 'left' },
          {
            href: 'https://github.com/Astronomy-Software/Byeoldori_Server',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        copyright: `Byeoldori Backend · Internal Docs`,
      },
      prism: {
        theme: themes.github,
        darkTheme: themes.dracula,
        additionalLanguages: ['kotlin', 'java', 'bash', 'json'],
      },
      colorMode: { defaultMode: 'dark', disableSwitch: false },
    }),
};

module.exports = config;
