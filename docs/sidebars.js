/**
 * Creating a sidebar enables you to:
 * - create an ordered group of docs
 * - render a sidebar for each doc of that group
 * - provide next/previous navigation
 *
 * The sidebars can be generated from the filesystem, or explicitly defined here.
 *
 * Create as many sidebars as you want.
 */
module.exports = {
  docsSidebar: [
    {
      type: 'category',
      label: 'Jactl',
      items: [
        'introduction',
        'getting-started',
        'language-features',
        {
          type: 'category',
          label: 'Language Guide',
          link: { type: 'doc', id: 'language-guide/introduction' },
          items: [
            'language-guide/statement-termination',
            'language-guide/comments',
            'language-guide/variables',
            'language-guide/keywords',
            'language-guide/types',
            'language-guide/truthiness',
            'language-guide/expressions-and-operators',
            'language-guide/regular-expressions',
            'language-guide/statements',
            'language-guide/functions',
            'language-guide/closures',
            'language-guide/checkpointing',
            'language-guide/classes',
            'language-guide/switch-expressions',
            'language-guide/json',
            'language-guide/collection-methods',
            'language-guide/global-functions',
            'language-guide/builtin-methods',
          ]
        },
        'command-line-scripts',
        {
          type: 'category',
          label: 'Integration Guide',
          link: { type: 'doc', id: 'integration-guide/introduction' },
          items: [
            'integration-guide/dependency',
            'integration-guide/overview',
            'integration-guide/jactl-context',
            'integration-guide/compiling-classes',
            'integration-guide/script-location',
            'integration-guide/jactl-execution-environment',
            'integration-guide/checkpoints',
            'integration-guide/adding-new-functions',
            'integration-guide/adding-new-builtins',
            'integration-guide/example-application',
          ]
        },
        'faq',
      ],
    },
  ],
};
