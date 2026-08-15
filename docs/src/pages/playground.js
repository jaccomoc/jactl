import React, {useState, useCallback, useRef} from 'react';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import BrowserOnly from '@docusaurus/BrowserOnly';
import {useColorMode} from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import styles from './playground.module.css';
import {registerJactlLanguage, configureMonacoLoader} from './_playground/monacoJactl';

// Curated starter snippets. These are inlined (rather than reusing the
// homepage example files) so every one is guaranteed to compile, run, and
// produce visible output in the playground.
const EXAMPLES = [
  {
    label: 'Hello World',
    code: `println 'Hello World!'\n`,
  },
  {
    label: 'String expressions',
    code: `String name = 'Fred'\ndef salary = 5000.00\nprintln "Name: $name, annual: \${salary * 12}"\n`,
  },
  {
    label: 'Lists and maps',
    code: `def numbers = [1, 2, 3, 4]\ndef person  = [name: 'Fred', dept: 'Sales']\n\nprintln "Name: \${person.name}, dept: \${person.dept}"\nprintln "Squares: \${numbers.map{ it * it }}"\nprintln "Sum of squares: \${numbers.map{ it * it }.sum()}"\n`,
  },
  {
    label: 'Classes',
    code: `class Person {\n  String name\n  int    age\n  String greeting() { "My name is $name and I am $age years old" }\n}\n\ndef fred = new Person(name: 'Fred Smith', age: 19)\nprintln fred.greeting()\n`,
  },
  {
    label: 'Regular expressions',
    code: `def text = 'Product: MacBook, Price: 3299.00, Quantity: 14'\n\nif (text =~ /Product: (.*), Price: (.*), Quantity: (\\d+)/n) {\n  println "Product: $1"\n  println "Total value: \${$2 * $3}"\n}\n`,
  },
  {
    label: 'Quicksort',
    code: `def qsort(x) {\n  switch (x) {\n    [], [_]       -> x\n    [head, *tail] -> qsort(tail.filter{ it < head }) +\n                     [head] +\n                     qsort(tail.filter{ it >= head })\n  }\n}\n\nprintln qsort([3, 1, 4, 1, 5, 9, 2, 6])\n`,
  },
];

const DEFAULT_CODE = EXAMPLES[0].code;

function OutputPanel({state}) {
  const {status, output, result, error, timedOut, truncated} = state;

  if (status === 'idle') {
    return <div className={styles.outputPlaceholder}>Output will appear here. Press <b>Run</b> (or Ctrl/Cmd&nbsp;+&nbsp;Enter).</div>;
  }
  if (status === 'running') {
    return <div className={styles.outputPlaceholder}><span className={styles.spinner} aria-hidden="true"/> Running…</div>;
  }
  if (status === 'neterror') {
    return <div className={styles.outputError}>{error}</div>;
  }
  // status === 'done'
  return (
    <div className={styles.outputBody}>
      {output ? <pre className={styles.outputStream}>{output}</pre> : null}
      {truncated ? <div className={styles.outputNote}>… output truncated</div> : null}
      {error
        ? <pre className={clsxError(timedOut)}>{error}</pre>
        : <div className={styles.outputResult}><span className={styles.resultLabel}>Result:</span> <code>{result}</code></div>}
    </div>
  );
}

function clsxError(timedOut) {
  return timedOut ? `${styles.outputError} ${styles.outputTimeout}` : styles.outputError;
}

function PlaygroundEditor({code, onChange, onRun}) {
  const {colorMode} = useColorMode();
  const theme = colorMode === 'dark' ? 'jactl-dark' : 'jactl-light';
  const monacoRef = useRef(null);

  // Apply the theme explicitly on colour-mode change. Relying on the `theme`
  // prop alone doesn't reliably switch a live editor across the BrowserOnly
  // boundary, so drive monaco.editor.setTheme directly once mounted.
  React.useEffect(() => {
    if (monacoRef.current) {
      monacoRef.current.editor.setTheme(theme);
    }
  }, [theme]);

  return (
    <BrowserOnly fallback={<div className={styles.editorLoading}>Loading editor…</div>}>
      {() => {
        const Monaco = require('@monaco-editor/react');
        const Editor = Monaco.default;
        configureMonacoLoader(Monaco.loader);
        return (
          <Editor
            height="100%"
            language="jactl"
            theme={theme}
            value={code}
            onChange={(v) => onChange(v ?? '')}
            beforeMount={registerJactlLanguage}
            onMount={(editor, monaco) => {
              monacoRef.current = monaco;
              editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, onRun);
            }}
            options={{
              minimap: {enabled: false},
              fontSize: 14,
              fontFamily: "'JetBrains Mono', monospace",
              scrollBeyondLastLine: false,
              automaticLayout: true,
              tabSize: 2,
              lineNumbersMinChars: 3,
              padding: {top: 12, bottom: 12},
            }}
          />
        );
      }}
    </BrowserOnly>
  );
}

export default function Playground() {
  const {siteConfig} = useDocusaurusContext();
  const apiUrl = siteConfig.customFields?.playgroundApiUrl || 'https://api.jactl.io';

  const [code, setCode] = useState(DEFAULT_CODE);
  const [out, setOut] = useState({status: 'idle'});
  const runningRef = useRef(false);
  // Keep the latest code in a ref so the editor's Ctrl+Enter command (bound once)
  // always runs the current buffer.
  const codeRef = useRef(code);
  codeRef.current = code;

  const run = useCallback(async () => {
    if (runningRef.current) return;
    runningRef.current = true;
    setOut({status: 'running'});
    try {
      const resp = await fetch(`${apiUrl}/run`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({script: codeRef.current}),
      });
      if (!resp.ok) {
        let msg = `Server returned ${resp.status}`;
        try {
          const j = await resp.json();
          if (j && j.error) msg = j.error;
        } catch (_) {}
        setOut({status: 'neterror', error: msg});
        return;
      }
      const data = await resp.json();
      setOut({
        status: 'done',
        output: data.output || '',
        result: data.result,
        error: data.error || null,
        timedOut: !!data.timedOut,
        truncated: !!data.truncated,
      });
    } catch (e) {
      setOut({
        status: 'neterror',
        error: `Could not reach the Jactl playground service. It may be offline — please try again later.`,
      });
    } finally {
      runningRef.current = false;
    }
  }, [apiUrl]);

  const loadExample = useCallback((e) => {
    const ex = EXAMPLES.find((x) => x.label === e.target.value);
    if (ex) {
      setCode(ex.code);
      setOut({status: 'idle'});
    }
  }, []);

  const clear = useCallback(() => {
    setCode('');
    setOut({status: 'idle'});
  }, []);

  return (
    <Layout
      title="Playground"
      description="Try Jactl online — write and run Jactl code snippets directly in your browser.">
      <main className={styles.page}>
        <div className={styles.header}>
          <Heading as="h1" className={styles.title}>Try Jactl</Heading>
          <p className={styles.subtitle}>
            Write some Jactl and run it. Code runs in a sandboxed environment with time and
            resource limits, so some features (host access, unbounded loops) are restricted.
          </p>
        </div>

        <div className={styles.toolbar}>
          <button className={styles.runButton} onClick={run} disabled={out.status === 'running'}>
            {out.status === 'running' ? 'Running…' : '► Run'}
          </button>
          <button
            className={styles.clearButton}
            onClick={clear}
            disabled={out.status === 'running' || !code}>
            Clear
          </button>
          <label className={styles.exampleLabel}>
            Examples:
            <select className={styles.exampleSelect} onChange={loadExample} defaultValue="">
              <option value="" disabled>Load an example…</option>
              {EXAMPLES.map((ex) => (
                <option key={ex.label} value={ex.label}>{ex.label}</option>
              ))}
            </select>
          </label>
          <span className={styles.hint}>Ctrl/Cmd + Enter to run</span>
        </div>

        <div className={styles.panes}>
          <div className={styles.editorPane}>
            <PlaygroundEditor code={code} onChange={setCode} onRun={run} />
          </div>
          <div className={styles.outputPane}>
            <OutputPanel state={out} />
          </div>
        </div>
      </main>
    </Layout>
  );
}
