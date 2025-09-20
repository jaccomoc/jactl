import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Familiar Syntax',
    Svg: require('@site/static/img/familiar-syntax.svg').default,
    description: (
      <>
        Subset of Java/Groovy syntax with a touch of Perl mixed in.
      </>
    ),
  },
  {
    title: 'Compiles to Java Bytecode',
    Svg: require('@site/static/img/bytecode.svg').default,
    description: (
      <>
        Compiles to bytecode for fast execution times.
        Supports Java 8 (and later).
      </>
    ),
  },
  {
    title: 'Secure',
    Svg: require('@site/static/img/secure-code.svg').default,
    description: (
      <>
        Scripts are tightly controlled.
        They can only perform operations allowed by the application in which Jactl is
        embedded.
      </>
    ),
  },
  {
    title: 'Never Blocks',
    Svg: require('@site/static/img/continuation.svg').default,
    description: (
      <>
        Built-in continuation mechanism allows scripts to suspend execution while waiting for asynchronous responses and
        then resume from where they left off.
        Execution thread is never blocked while waiting for a long-running response.
      </>
    ),
  },
  {
    title: 'Execution State Checkpointing',
    Svg: require('@site/static/img/checkpoint.svg').default,
    description: (
      <>
        Execution state can be checkpointed and persisted or distributed over a network to allow scripts to be recovered
        and resumed from where they left off after a failure.
      </>
    ),
  },
  {
    title: 'No Dependencies',
    Svg: require('@site/static/img/dependencies.svg').default,
    description: (
      <>
        Jactl does not have any dependencies on any other libraries (apart from an embedded instance of the stand-alone ASM
        library).
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
  );
}
