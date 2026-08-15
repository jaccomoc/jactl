/**
 * Monaco setup for the Jactl playground.
 *
 * This module is imported by playground.js at module scope (so it also loads
 * during Docusaurus's server-side render). It therefore must NOT import
 * `monaco-editor` at the top level — that package references `window`/`self`
 * and would break `docusaurus build`. Instead the monaco instance is pulled in
 * lazily via require() inside the functions below, which are only ever called
 * from within a <BrowserOnly> block (client side).
 */

let loaderConfigured = false;
let languageRegistered = false;

/**
 * Point @monaco-editor/react at a bundled, self-hosted monaco-editor instead of
 * fetching it from a CDN (keeps the site free of external runtime requests).
 * Also installs a no-op web worker so Monaco doesn't error trying to load its
 * editor worker — we only use main-thread Monarch highlighting, no language
 * services, so a dummy worker is sufficient.
 */
export function configureMonacoLoader(loader) {
  if (loaderConfigured || typeof window === 'undefined') return;
  loaderConfigured = true;

  window.MonacoEnvironment = {
    getWorker() {
      const blob = new Blob(['self.onmessage=function(){};'], {
        type: 'application/javascript',
      });
      return new Worker(URL.createObjectURL(blob));
    },
  };

  const monaco = require('monaco-editor');
  loader.config({monaco});
}

// Minimal Monarch grammar for Jactl. Covers comments, keywords, numbers, and
// the various string forms (single/double, triple-quoted) with $var / ${...}
// interpolation. Regex literals are deliberately left untokenised to avoid the
// classic division-vs-regex ambiguity producing false positives.
const JACTL_MONARCH = {
  defaultToken: '',
  keywords: [
    'def', 'var', 'const', 'final', 'static', 'class', 'interface', 'extends',
    'implements', 'sealed', 'import', 'package', 'as', 'instanceof', 'in',
    'new', 'return', 'if', 'unless', 'else', 'while', 'until', 'for', 'do',
    'switch', 'default', 'and', 'or', 'not', 'print', 'println', 'true',
    'false', 'null', 'it', 'continue', 'break', 'BEGIN', 'END',
    'boolean', 'byte', 'int', 'long', 'double', 'Decimal', 'String', 'void',
    'Object', 'Map', 'List',
  ],
  operators: [
    '=', '==', '===', '!=', '!==', '<', '>', '<=', '>=', '<=>', '&&', '||',
    '!', '+', '-', '*', '/', '%', '**', '?:', '?.', '=~', '!~', '..', '<<',
    '>>', '&', '|', '^', '~', '++', '--', '+=', '-=', '*=', '/=', '?=',
  ],
  symbols: /[=><!~?:&|+\-*/^%]+/,
  tokenizer: {
    root: [
      [/\/\/.*$/, 'comment'],
      [/\/\*/, 'comment', '@comment'],
      [/[a-zA-Z_$][\w$]*/, {cases: {'@keywords': 'keyword', '@default': 'identifier'}}],
      [/\d+\.\d+([eE][+-]?\d+)?[DLdl]?/, 'number.float'],
      [/\d[\d_]*[LDld]?/, 'number'],
      [/"""/, {token: 'string.quote', next: '@tstringd'}],
      [/'''/, {token: 'string.quote', next: '@tstrings'}],
      [/"/, {token: 'string.quote', next: '@stringd'}],
      [/'/, {token: 'string.quote', next: '@strings'}],
      [/[{}()[\]]/, '@brackets'],
      [/@symbols/, {cases: {'@operators': 'operator', '@default': ''}}],
      [/[;,.]/, 'delimiter'],
    ],
    comment: [
      [/[^/*]+/, 'comment'],
      [/\*\//, 'comment', '@pop'],
      [/[/*]/, 'comment'],
    ],
    stringd: [
      [/\$\{/, {token: 'delimiter.bracket', next: '@interp'}],
      [/\$[a-zA-Z_]\w*/, 'variable'],
      [/[^\\"$]+/, 'string'],
      [/\\./, 'string.escape'],
      [/"/, {token: 'string.quote', next: '@pop'}],
    ],
    strings: [
      [/[^\\']+/, 'string'],
      [/\\./, 'string.escape'],
      [/'/, {token: 'string.quote', next: '@pop'}],
    ],
    tstringd: [
      [/\$\{/, {token: 'delimiter.bracket', next: '@interp'}],
      [/\$[a-zA-Z_]\w*/, 'variable'],
      [/"""/, {token: 'string.quote', next: '@pop'}],
      [/[^\\"$]+/, 'string'],
      [/\\./, 'string.escape'],
      [/"/, 'string'],
    ],
    tstrings: [
      [/'''/, {token: 'string.quote', next: '@pop'}],
      [/[^']+/, 'string'],
      [/'/, 'string'],
    ],
    interp: [
      [/\}/, {token: 'delimiter.bracket', next: '@pop'}],
      {include: 'root'},
    ],
  },
};

const LANG_CONFIG = {
  comments: {lineComment: '//', blockComment: ['/*', '*/']},
  brackets: [['{', '}'], ['[', ']'], ['(', ')']],
  autoClosingPairs: [
    {open: '{', close: '}'},
    {open: '[', close: ']'},
    {open: '(', close: ')'},
    {open: "'", close: "'"},
    {open: '"', close: '"'},
  ],
  surroundingPairs: [
    {open: '{', close: '}'},
    {open: '[', close: ']'},
    {open: '(', close: ')'},
    {open: "'", close: "'"},
    {open: '"', close: '"'},
  ],
};

/**
 * beforeMount hook for @monaco-editor/react: register the `jactl` language and
 * two brand-tinted themes. Safe to pass on every render — runs its body once.
 */
export function registerJactlLanguage(monaco) {
  if (languageRegistered) return;
  languageRegistered = true;

  monaco.languages.register({id: 'jactl', extensions: ['.jactl'], aliases: ['Jactl', 'jactl']});
  monaco.languages.setMonarchTokensProvider('jactl', JACTL_MONARCH);
  monaco.languages.setLanguageConfiguration('jactl', LANG_CONFIG);

  monaco.editor.defineTheme('jactl-light', {
    base: 'vs',
    inherit: true,
    rules: [
      {token: 'keyword', foreground: '1042ae', fontStyle: 'bold'},
      {token: 'variable', foreground: '2563eb'},
      {token: 'comment', foreground: '6b7280', fontStyle: 'italic'},
    ],
    colors: {'editor.background': '#ffffff'},
  });
  monaco.editor.defineTheme('jactl-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      {token: 'keyword', foreground: '7098f2', fontStyle: 'bold'},
      {token: 'variable', foreground: '9ecbff'},
      {token: 'comment', foreground: '8b96a8', fontStyle: 'italic'},
    ],
    colors: {'editor.background': '#1b1b1d'},
  });
}
