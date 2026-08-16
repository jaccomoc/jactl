import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const GITHUB_URL = 'https://github.com/jaccomoc/jactl';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className={clsx('container', styles.heroContent)}>
        <Heading as="h1" className={styles.heroTitle}>
          {siteConfig.title}
        </Heading>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className={clsx('button button--lg', styles.btnPrimary)}
            to="/docs/getting-started">
            Get Started
          </Link>
          <Link
            className={clsx('button button--lg', styles.btnGhost)}
            to="/playground">
            Playground
          </Link>
          <Link
            className={clsx('button button--lg', styles.btnGhost)}
            href={GITHUB_URL}>
            ★ Star on GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}

import SimpleFunction from '!!raw-loader!./examples/simple.jactl'
import HelloWorld from '!!raw-loader!./examples/hello-world.jactl'
import StringExpressions from '!!raw-loader!./examples/string-expressions.jactl'
import ListAndMaps from '!!raw-loader!./examples/lists-and-maps.jactl'
import Classes from '!!raw-loader!./examples/classes.jactl'
import Regexes from '!!raw-loader!./examples/regexes.jactl'
import QuickSort from '!!raw-loader!./examples/qsort.jactl'

function Example({title, code, children}) {
  return (
    <div className={styles.example}>
      <div className={styles.exampleText}>
        <Heading as="h3">{title}</Heading>
        {children}
      </div>
      <div className={styles.exampleCode}>
        <CodeBlock language="groovy">{code}</CodeBlock>
      </div>
    </div>
  );
}

function ExamplesSection() {
  return (
    <div className="container">
      <div className={styles.sectionHeading}>
        <Heading as="h2">A quick tour</Heading>
        <p>A taste of Jactl — familiar, concise, and expressive.</p>
      </div>

      <Example title="Hello World" code={HelloWorld}>
        <p>Simple program to print "Hello World!".</p>
      </Example>

      <Example title="String Expressions" code={StringExpressions}>
        <p>
          Strings with double quotes allow embedded references to variables using <b>$</b> or to arbitrary expressions
          using <b>$&#123;</b>...<b>&#125;</b>.
          Triple single quoted strings and triple double quoted strings can be used for multi-line strings and
          string-expressions.
        </p>
      </Example>

      <Example title="List and Map Literals" code={ListAndMaps}>
        <p>
          Lists literals can be constructed using comma separated values betwen <b>[ ]</b>.
          Map literals use comma separated <b>key:value</b> pairs.
        </p>
        <p>
          Many built-in list methods exist such as <b>map()</b>, <b>filter()</b>, <b>sort()</b>, etc., for easy processing of
          list data.
        </p>
      </Example>

      <Example title="Optional Typing" code={SimpleFunction}>
        <p>
          Types can be provided and will be enforced by the compiler or can be omitted if dynamic typing is preferred.
          Note that functions return the value of the last expression if <b>return</b> is not used.
        </p>
      </Example>

      <Example title="Classes" code={Classes}>
        <p>Jactl provides the ability to define classes using a simplified syntax.</p>
      </Example>

      <Example title="Regular Expressions" code={Regexes}>
        <p>
          Jactl provides built-in support for regular expressions and capture variables <b>$1</b>, <b>$2</b>, etc.,
          for capturing parts of the matched string.
        </p>
      </Example>

      <Example title="Switch Expressions with Destructuring" code={QuickSort}>
        <p>
          Switch statements/expressions allow matching on structure as well as matching on simple values and allow
          variables to be bound to parts of the structure.
          This makes the standard quick sort implementation very simple as we can bind a variable <i>head</i> to the
          head of the list (the first element) and a variable <i>tail</i> to the rest of the list, and then
          recurse as shown.
        </p>
      </Example>
    </div>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Secure Embeddable Scripting Language for Java"
      description="Jactl is a secure, open source scripting language for the JVM that compiles to bytecode, supports async execution via continuations, and provides execution state checkpointing for Java applications.">
      <HomepageHeader/>
      <main>
        <section className={styles.features}>
          <HomepageFeatures/>
        </section>
        <section className={styles.opensource}>
          <div className="container">
            <div className={styles.opensourceCard}>
              <Heading as="h2">Open Source</Heading>
              <p>Licensed under the Apache 2.0 license.</p>
              <p>
                Please consider giving the project a ⭐ on GitHub:{' '}
                <a href={GITHUB_URL}>{GITHUB_URL}</a>
              </p>
            </div>
          </div>
        </section>
        <section className={styles.ecosystem}>
          <div className="container">
            <div className={styles.ecosystemCard}>
              <div className={styles.ecosystemText}>
                <Heading as="h2">Works with Apache Camel</Heading>
                <p>
                  Use Jactl as a secure, sandboxed scripting language for
                  predicates, expressions, and message transformations in your
                  Apache Camel routes.
                </p>
              </div>
              <Link
                className="button button--primary button--lg"
                to="/blog/2026/07/21/camel-jactl-benchmarks">
                Learn more →
              </Link>
            </div>
          </div>
        </section>
        <section className={styles.examples}>
          <ExamplesSection/>
        </section>
      </main>
    </Layout>
  );
}
