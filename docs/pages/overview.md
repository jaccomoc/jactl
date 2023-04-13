---
layout: page
title: "Overview"
permalink: /overview
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It can be integrated into JVM based applications to provide a secure, locked-down mechanism
for customers/users to extend and customise application behaviour under the control of
the application developer.
It comes with a REPL and can also be used for the commandline scripting.

It is especially suited to event-loop/reactive applications due to its built-in suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

## Features

### Familiar Syntax
Jactl syntax is mostly just a subset of Java and Groovy syntax with a bit of Perl thrown in for good measure,
so it is easy to pick up for anyone familiar with Java:

```groovy
int fib(int x) {
  return x <= 2 ? 1 : fib(x-1) + fib(x-2);
}
println 'fib(20) = ' + fib(20);
```

Jactl adopts a lot of Groovy syntax so semicolons are optional, typing is optional, double-quoted strings
allow for embedded expressions, and `return` is optional for the last expression of a function so the previous example
could also be written like this:

```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
println "fib(20) = ${fib(20)}"
```

### Compiles to Java bytecode
Jactl scripts compile to bytecode to take advantage of the performance capabilities of the JVM.

### Never blocks
Jactl never blocks the execution thread on which a script is running.
This is to support execution in reactive or event loop based applications.

Blocking operations in Jactl can only occur when invoking a blocking/asynchronous function.
When executing such a function, the script will be suspended and when the blocking function completes,
the script execution state is resumed from the point at which it was suspended.
This is done via an internal _continuation_ mechanism.

Script writers can write their code as though it blocks and do not have to be concerned about which functions are
asynchronous and which are synchronous or have to jump through the normal asynchronous programming hoops with the
use of callbacks to be invoked after a long-running operation completes.

When a blocking operation is invoked, Jactl creates a Continuation object that captures the state of the running
script such as the value of all local variables and the current call stack.
When the long-running operation completes, the script state is restored and the script continues
from where it left off.

### Extendable
Easy to integrate additional functions and methods into the language for application-specific needs.

### Secure
Unlike many other JVM based languages, in Jactl the things that a script writer can do are tightly controlled.
The only things available to the script writer are the built-in functions and the functions that you provide.
This allows Jactl scripts to be safely executed in frameworks where creating threads, writing to files,
connecting to remote systems, and performing other blocking operations would not normally be allowed.

When you integrate the Jactl compiler into your application you provide it with the functions that you want
your script writers to have access to and this ensures they can only do things that you permit them to do.
This means you don't need to worry about them accessing files or data that they shouldn't be accessing,
for example.

### No Dependencies
The core Jactl language has no dependencies on external libraries apart from the ASM library
which it embeds within itself after renaming (to avoid clashes with any other instance of ASM
on the classpath).

### Dynamic or Static Typing
The language allows you to choose whether to specify a type for your variables, parameters,
and return types.
You can choose to provide types for stricter type checking or for optimisation reasons, or you
can leave things dynamically typed.

### Jactl REPL
Jactl has a REPL (Read-Evaluate-Print-Loop) to allow you to easily play with the language and prototype
code at the commandline.

### Commandline Scripts
Jactl scripts can be run from the commandline to replace use of various Unix utilities such as
`awk`, `sed`, or even `Perl`.

### Language Features
* Closures
* Classes with inheritance
* Strong/weak typing
* Parameters with default values
* Named parameters for function calls
* Multi-line strings
* Strings with interpolated expressions
* Functional programming idioms (map, filter, collect, each, etc.)
* Regex matching syntax
* Regex capture variables

## Download

To run command line scripts you only need the Jactl jar which can be downloaded from Maven Central:
[https://repo1.maven.org/maven2/io/jactl/jactl/1.1.0/jactl-1.1.0.jar](https://repo1.maven.org/maven2/io/jactl/jactl/1.1.0/jactl-1.1.0.jar)

To download the Jactl REPL, which gives you an interactive shell for trying out Jactl code, see the
[jactl-repl](https://github.com/jaccomoc/jactl-repl) project.

## Building

### Requirements

* Java 11+
* Gradle 8.0.2
* ASM 9.3

### Build

```shell
git clone https://github.com/jaccomoc/jactl.git
cd jactl
./gradlew build
```

That will build `jactl-{{ site.content.jactl_version }}.jar` under the `build/libs` directory.

To push to your Maven repository you can use `publishToMaven`:
```shell
./gradlew build publishToMaven
```

## Integration

To use Jactl you will need to add a dependency on the Jactl library.

### Gradle

In the `dependencies` section of your `build.gradle` file:
```groovy
implementation group: 'io.jactl', name: 'jactl', version: '{{ site.content.jactl_version }}'
```

### Maven

In the `dependencies` section of your `pom.xml`:
```xml
<dependency>
 <groupId>io.jactl</groupId>
 <artifactId>jactl</artifactId>
 <version>{{ site.content.jactl_version }}</version>
</dependency>
```

## Learn More

See the [Jactl REPL project](https://github.com/jaccomoc/jactl-repl) for how to download and run the Jactl REPL.

The [Jactl Language Guide](language-guide) describes the Jactl Language.

The [Jactl Commandline Scripts Guide](command-line-scripts) describes how to run Jactl scripts at the commandline.

The [Jactl Integration Guide](integration-guide) describes how to integrate Jactl into an application and how
to provide additional functions and methods to extend the language.

The [Jactl-Vertx library](https://github.com/jaccomoc/jactl-vertx) provides some basic Vert.x integration capabilities
as well as JSON functions, a function for sending a web request, and an example application.
