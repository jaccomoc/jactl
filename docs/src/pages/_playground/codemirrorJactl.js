/**
 * CodeMirror 6 setup for the Jactl playground: a StreamLanguage tokenizer
 * (ported from the previous Monaco/Monarch grammar) plus brand-tinted
 * light/dark themes.
 *
 * Unlike monaco-editor, these packages guard their `window`/`document`
 * references, so importing them at module scope is safe under Docusaurus's
 * server-side render.
 */
import {StreamLanguage, HighlightStyle, syntaxHighlighting} from '@codemirror/language';
import {EditorView} from '@codemirror/view';
import {tags as t} from '@lezer/highlight';

const KEYWORDS = new Set([
  'def', 'var', 'const', 'final', 'static', 'class', 'interface', 'extends',
  'implements', 'sealed', 'import', 'package', 'as', 'instanceof', 'in',
  'new', 'return', 'if', 'unless', 'else', 'while', 'until', 'for', 'do',
  'switch', 'default', 'and', 'or', 'not', 'print', 'println', 'true',
  'false', 'null', 'it', 'continue', 'break', 'BEGIN', 'END',
  'boolean', 'byte', 'int', 'long', 'double', 'Decimal', 'String', 'void',
  'Object', 'Map', 'List',
]);

function popContext(state) {
  // The base 'root' frame is never popped - every pushed frame (string,
  // comment, interpolation) has a matching close that pops exactly itself.
  if (state.stack.length > 1) state.stack.pop();
}

// Tokenises Jactl source at the top level, and (via tokenInterp) inside a
// `${...}` interpolation expression - both cases can contain arbitrarily
// nested strings/comments, so nested contexts are pushed onto state.stack.
function tokenRoot(stream, state) {
  if (stream.eatSpace()) return null;
  if (stream.match('//')) { stream.skipToEnd(); return 'comment'; }
  if (stream.match('/*')) { state.stack.push({type: 'comment'}); return 'comment'; }
  if (stream.match(/^[a-zA-Z_$][\w$]*/)) {
    return KEYWORDS.has(stream.current()) ? 'keyword' : null;
  }
  if (stream.match(/^\d+\.\d+([eE][+-]?\d+)?[DLdl]?/)) return 'number';
  if (stream.match(/^\d[\d_]*[LDld]?/)) return 'number';
  if (stream.match('"""')) { state.stack.push({type: 'tstringd'}); return 'string'; }
  if (stream.match("'''")) { state.stack.push({type: 'tstrings'}); return 'string'; }
  if (stream.match('"')) { state.stack.push({type: 'stringd'}); return 'string'; }
  if (stream.match("'")) { state.stack.push({type: 'strings'}); return 'string'; }
  if (stream.match(/^[{}()[\]]/)) return 'bracket';
  if (stream.match(/^[;,.]/)) return 'punctuation';
  if (stream.match(/^[=><!~?:&|+\-*/^%]+/)) return 'operator';
  stream.next();
  return null;
}

// Inside `${...}`, behaves like tokenRoot except a `}` not matched by a `{`
// opened within the expression (e.g. a map literal) closes the
// interpolation and returns to the enclosing string.
function tokenInterp(stream, state, frame) {
  if (stream.peek() === '{') {
    frame.braceDepth++;
    stream.next();
    return 'bracket';
  }
  if (stream.peek() === '}') {
    stream.next();
    if (frame.braceDepth === 0) state.stack.pop();
    else frame.braceDepth--;
    return 'bracket';
  }
  return tokenRoot(stream, state);
}

function tokenDoubleQuoted(stream, state, triple) {
  if (triple ? stream.match('"""') : stream.match('"')) {
    popContext(state);
    return 'string';
  }
  if (stream.match('${')) {
    state.stack.push({type: 'interp', braceDepth: 0});
    return 'bracket';
  }
  if (stream.match(/^\$[a-zA-Z_]\w*/)) return 'variableName';
  if (stream.match(/^\\./)) return 'string';
  // A `$` only breaks the run if it actually starts one of the two forms
  // handled above - a bare trailing `$` (e.g. typed but not yet followed by
  // a name) is just ordinary content, and must still be consumed so this
  // token makes progress.
  while (!stream.eol()) {
    if (stream.match(/^\\./, false)) break;
    if (stream.match(/^\$[{a-zA-Z_]/, false)) break;
    if (triple ? stream.match('"""', false) : stream.peek() === '"') break;
    stream.next();
  }
  return 'string';
}

function tokenSingleQuoted(stream, state, triple) {
  if (triple ? stream.match("'''") : stream.match("'")) {
    popContext(state);
    return 'string';
  }
  if (!triple && stream.match(/^\\./)) return 'string';
  while (!stream.eol()) {
    if (!triple && stream.match(/^\\./, false)) break;
    if (triple ? stream.match("'''", false) : stream.peek() === "'") break;
    stream.next();
  }
  return 'string';
}

function tokenComment(stream, state) {
  if (stream.match('*/')) {
    popContext(state);
    return 'comment';
  }
  while (!stream.eol()) {
    if (stream.match('*/', false)) break;
    stream.next();
  }
  return 'comment';
}

export const jactlLanguage = StreamLanguage.define({
  name: 'jactl',
  startState() {
    return {stack: [{type: 'root'}]};
  },
  copyState(state) {
    return {stack: state.stack.map((frame) => ({...frame}))};
  },
  token(stream, state) {
    const frame = state.stack[state.stack.length - 1];
    switch (frame.type) {
      case 'comment': return tokenComment(stream, state);
      case 'stringd': return tokenDoubleQuoted(stream, state, false);
      case 'tstringd': return tokenDoubleQuoted(stream, state, true);
      case 'strings': return tokenSingleQuoted(stream, state, false);
      case 'tstrings': return tokenSingleQuoted(stream, state, true);
      case 'interp': return tokenInterp(stream, state, frame);
      default: return tokenRoot(stream, state);
    }
  },
  languageData: {
    commentTokens: {line: '//', block: {open: '/*', close: '*/'}},
    closeBrackets: {brackets: ['(', '[', '{', "'", '"']},
  },
});

function editorChrome(background, foreground, dark) {
  return EditorView.theme({
    '&': {
      backgroundColor: background,
      color: foreground,
      height: '100%',
      fontSize: '14px',
    },
    '.cm-content': {
      fontFamily: "'JetBrains Mono', monospace",
      padding: '12px 0',
      // JetBrains Mono ligates sequences like `->` and `=>` into a single
      // arrow glyph by default - disable so operators show as typed.
      fontVariantLigatures: 'none',
      fontFeatureSettings: '"liga" 0, "calt" 0',
    },
    '.cm-gutters': {
      backgroundColor: background,
      border: 'none',
    },
  }, {dark});
}

export const jactlLightTheme = [
  editorChrome('#ffffff', '#1b1b1d', false),
  syntaxHighlighting(HighlightStyle.define([
    {tag: t.keyword, color: '#1042ae', fontWeight: 'bold'},
    {tag: t.variableName, color: '#2563eb'},
    {tag: t.comment, color: '#6b7280', fontStyle: 'italic'},
    {tag: t.string, color: '#0a7a3d'},
    {tag: t.number, color: '#b45309'},
    {tag: t.operator, color: '#374151'},
    {tag: t.bracket, color: '#374151'},
    {tag: t.punctuation, color: '#6b7280'},
  ])),
];

export const jactlDarkTheme = [
  editorChrome('#1b1b1d', '#e5e7eb', true),
  syntaxHighlighting(HighlightStyle.define([
    {tag: t.keyword, color: '#7098f2', fontWeight: 'bold'},
    {tag: t.variableName, color: '#9ecbff'},
    {tag: t.comment, color: '#8b96a8', fontStyle: 'italic'},
    {tag: t.string, color: '#7dd3a8'},
    {tag: t.number, color: '#f2b872'},
    {tag: t.operator, color: '#c9d1d9'},
    {tag: t.bracket, color: '#c9d1d9'},
    {tag: t.punctuation, color: '#8b96a8'},
  ])),
];
