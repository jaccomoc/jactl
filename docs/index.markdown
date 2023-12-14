---
layout: home
image: /assets/logo-picture.jpg
---

<h1 style="text-align:center;color:#2240aa;">A scripting language for Java-based applications</h1>

[Jactl](https://github.com/jaccomoc/jactl) is a powerful scripting language for Java-based applications whose syntax 
borrows from Java and Groovy, with a dash of Perl thrown in for good measure.
See [Jactl Language Features](/language-features) for a quick overview of some of the language features
or [Jactl Language Guide](/language-guide) for a full description of the language.


<div class="row">
  <div class="column" markdown="1">
### **Familiar Syntax**
Subset of Java/Groovy syntax with a touch of Perl mixed in.
  </div>
  <div class="column" markdown="1">
### **Compiles to Java Bytecode**
Compiles to bytecode for fast execution times.
Supports Java 8 (and later).
  </div>
</div>

<div class="row">
  <div class="column" markdown="1">
### **Never Blocks**
Built-in continuation mechanism allows scripts to suspend execution while waiting for asynchronous reponses and
then resume from where they left off.
Execution thread is never blocked while waiting for a long-running response.
  </div>
  <div class="column" markdown="1">
### **Secure**
Scripts are tightly controlled.
They can only perform operations provided as language functions by the application in which Jactl is
embedded.
  </div>
</div>

<div class="row">
  <div class="column" markdown="1">
### **Checkpointing Execution State**
Execution state can be checkpointed and persisted or distributed over a network to allow scripts to be recovered
and resumed from where they left off after a failure.
  </div>
  <div class="column" markdown="1">
### **No Dependencies**
Jactl does not have any dependencies on any other libraries (apart from an embedded instance of the stand-alone ASM
library).
  </div>
</div>

<div class="row">
  <div class="column" markdown="1">
### **REPL and Commandline Scripts**
As well as being integrated into Java applications, Jactl can run as commandline scripts and has a REPL
for interactively trying out Jactl code.
  </div>
  <div class="column" markdown="1">
### **Open Source**
Jactl is open sourced [here](https://github.com/jaccomoc/jactl) under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
  </div>
</div>

<br/>
Jactl is intended to be integrated into Java applications where it provides a secure, locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its built-in suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

## Simple Example

Here is some simple Jactl code:
```groovy
int fib(int x) {
  return x <= 2 ? 1 : fib(x-1) + fib(x-2);
}
println 'fib(20) = ' + fib(20);
```

Since in Jactl semicolons are optional, typing is optional, `return` is optional for the last expression
of a function, and double-quoted strings allow for embedded expressions, so the previous example
can also be written like this:

```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
println "fib(20) = ${fib(20)}"
```

## More Advanced Example

Here is a more advanced example which streams the input as lines, searches for markdown headings and generates
a table of contents:
```groovy
// Sanitise text to make suitable for a link
def linkify = { s/ /-/g;  s/[^\w-]//g }

// Find all top level headings in input and generate markdown for table of contents:
stream(nextLine).filter{ /^# /r }
                .map{ $1 if /^# (.*)/r }
                .map{ "* [$it](#${ linkify(it.toLowerCase()) })" }
                .each{ println it }
```

## Getting Started

To get a feel for how the language looks and the type of language features that Jactl offers
see the [Language Features](/language-features) page.

You can download the Jactl library and find the source code for Jactl at GitHub: [jactl](https://github.com/jaccomoc/jactl)

To start playing with Jactl and for testing out code interactively, you can use
the [Read-Evaluate-Print-Loop (REPL) utility](https://github.com/jaccomoc/jactl-repl).

To see how to use Jactl from the command line see the page about [command line scripts](/command-line-scripts).

To learn how to integrate Jactl into your application see the [Integration Guide](/integration-guide).

To integrate Jactl into a [Vert.x](https://vertx.io) based application have a look at the
[jactl-vertx library](https://github.com/jaccomoc/jactl-vertx).

To learn more about the language itself read the [Language Guide](/language-guide).
