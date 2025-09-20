import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/getting-started">
            Getting Started - 5min ⏱️
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

function ExamplesSection() {
  return (
      <div className="container" >
        <div className="row">
          <div className="col">
            <h2>Hello World</h2>
            Simple program to print "Hello World!".
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{HelloWorld}</CodeBlock>
          </div>
        </div>
        <br/>
        <div className="row">
          <div className="col">
            <h2>String Expressions</h2>
            Strings with double quotes allow embedded references to variables using <b>$</b> or to arbitrary expressions
            using <b>$&#123;</b>...<b>&#125;</b>.
            Triple single quoted strings and triple double quoted strings can be used for multi-line strings and
            string-expressions.
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{StringExpressions}</CodeBlock>
          </div>
        </div>
        <br/>
        <div className="row">
          <div className="col">
            <h2>List and Map Literals</h2>
            Lists literals can be constructed using comma separated values betwen <b>[ ]</b>.
            Map literals use comma separated <b>key:value</b> pairs.
            <p/>
            Many built-in list methods exist such as <b>map()</b>, <b>filter()</b>, <b>sort()</b>, etc., for easy processing of
            list data.
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{ListAndMaps}</CodeBlock>
          </div>
        </div>
        <br/>
        <div className="row">
          <div className="col">
            <h2>Optional Typing</h2>
            Types can be provided and will be enforced by the compiler or can be omitted if dynamic typing is preferred.
            Note that functions return the value of the last expression if <b>return</b> is not used.
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{SimpleFunction}</CodeBlock>
          </div>
        </div>
        <br/>
        <div className="row">
          <div className="col">
            <h2>Classes</h2>
            Jactl provides the ability to define classes using a simplified syntax.
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{Classes}</CodeBlock>
          </div>
        </div>
        <br/>
        <div className="row">
          <div className="col">
            <h2>Regular Expressions</h2>
            Jactl provides built-in support for regular expressions and capture variables <b>$1</b>, <b>$2</b>, etc.,
            for capturing parts of the matched string.
          </div>
          <div className="col">
            <br/>
            <CodeBlock language="groovy">{Regexes}</CodeBlock>
          </div>
        </div>
      </div>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="An embeddable scripting language for Java applications">
      <HomepageHeader/>
      <main>
        <section className={styles.features}>
          <HomepageFeatures/>
        </section>
        <section className={styles.examples}>
          <ExamplesSection/>
        </section>
      </main>
    </Layout>
  );
}
