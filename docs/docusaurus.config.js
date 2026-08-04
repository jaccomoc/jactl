// @ts-check
// `@type` JSDoc annotations allow editor autocompletion and type checking
// (when paired with `@ts-check`).
// There are various equivalent ways to declare your Docusaurus config.
// See: https://docusaurus.io/docs/api/docusaurus-config

import {themes as prismThemes} from 'prism-react-renderer';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

// Single source of truth for the Jactl version: the project's build.gradle.
// Blog posts contain a leftover Jekyll placeholder, {{ site.content.jactl_version }},
// which the markdown.preprocessor below substitutes at build time.
const JACTL_VERSION = (() => {
  try {
    const dir = path.dirname(fileURLToPath(import.meta.url));
    const gradle = fs.readFileSync(path.resolve(dir, '../build.gradle'), 'utf8');
    const match = gradle.match(/^version\s+'([^']+)'/m);
    if (match) return match[1];
    console.warn('[jactl] Could not parse version from build.gradle');
  } catch (err) {
    console.warn('[jactl] Could not read ../build.gradle:', err.message);
  }
  return '2.9.2'; // fallback if build.gradle is unavailable
})();

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Jactl',
  tagline: 'A secure, embeddable, open source scripting language for Java-based applications',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  url: 'https://jactl.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'jaccomoc', // Usually your GitHub org/user name.
  projectName: 'jactl', // Usually your repo name.
  deploymentBranch: 'gh-pages',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    // Fill in the leftover Jekyll {{ site.content.jactl_version }} placeholder
    // (used in the Advent-of-Code blog posts) with the current Jactl version.
    preprocessor: ({fileContent}) =>
      fileContent.replace(
        /\{\{\s*site\.content\.jactl_version\s*\}\}/g,
        JACTL_VERSION,
      ),
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
        },
        blog: {
          blogSidebarTitle: 'All posts',
          blogSidebarCount: 'ALL',
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          // Useful options to enforce blogging best practices
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Social card shown when a page is shared (Open Graph / Twitter).
      image: 'img/social-card.png',
      navbar: {
        style: 'dark',
        logo: {
          alt: 'Jactl - A Secure Scripting Language for Java',
          src: 'img/logo2-dark.png',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {to: '/blog', label: 'Blog', position: 'left'},
          {
            href: 'https://github.com/jaccomoc/jactl',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Introduction',
                to: '/docs/introduction',
              },
              {
                label: 'Getting Started',
                to: '/docs/getting-started',
              },
              {
                label: 'Language Features',
                to: '/docs/language-features',
              },
              {
                label: 'Language Guide',
                to: '/docs/language-guide/introduction',
              },
              {
                label: 'Integration Guide',
                to: '/docs/integration-guide/introduction',
              },
              {
                label: 'FAQ',
                to: '/docs/faq',
              },
            ]
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Twitter',
                href: 'https://x.com/jactllang',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Blog',
                to: '/blog',
              },
              {
                label: 'GitHub',
                href: 'https://github.com/jaccomoc/jactl',
              },
            ],
          },
        ],
        copyright: `Copyright © 2023-${new Date().getFullYear()} James Crawford`,
      },
      docs: {
        sidebar: {
          hideable: true,
          autoCollapseCategories: true,
        }
      },
      prism: {
        theme: prismThemes.oneLight,
        darkTheme: prismThemes.nightOwl,
        additionalLanguages: ['groovy','java','bash'],
      },
    }),
};

export default config;
