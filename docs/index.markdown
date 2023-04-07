---
layout: home
---

<h1 style="text-align:center;color:#2240aa;">A scripting language for Java-based applications</h1>

Jactl is a powerful scripting language for the Java-based applications whose syntax is a combination of bits
from Java, Groovy, and Perl.


<div class="row">
  <div class="column" markdown="1">
### **Familiar Syntax**
Subset of Java/Groovy syntax with a dash of Perl mixed in.
  </div>
  <div class="column" markdown="1">
### **Compiles to Java Bytecode**
Compiles to bytecode for fast execution times.
  </div>
</div>

<div class="row">
  <div class="column" markdown="1">
### **Never Blocks**
Builtin continuation mechanism allows scripts to suspend execution while waiting for asynchronous reponses and
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
### **Easy to Integrate**
Easy to integrate with any Java-based application.
  </div>
  <div class="column" markdown="1">
### **No Dependencies**
Jactl does not have any dependencies on any other libraries (apart from an embedded instance of the stand-alone ASM
library).
  </div>
</div>

<br/>
Jactl is intended to be integrated into Java applications where it provides a secure, locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its builtin suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

It has a Read-Evaluate-Print-Loop (REPL) for trying out code interactively and can also be used for
commandline scripts.

## Example

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

## Getting Started

See the [Jactl REPL project](https://github.com/jaccomoc/jactl-repl) for how to download and run the Jactl REPL
which provides a quick, interactive, way to run Jactl code.

The [Jactl Language Guide](https://jaccomoc.github.io/jactl/language-guide) describes the Jactl Language.

The [Jactl Commandline Scripts Guide](https://jaccomoc.github.io/jactl/command-line-scripts) describes how to run Jactl scripts at the commandline.

The [Jactl Integration Guide](https://jaccomoc.github.io/jactl/integration-guide) describes how to integrate Jactl into an application and how
to provide additional functions and methods to extend the language.

The [Jactl-Vertx library](https://github.com/jaccomoc/jactl-vertx) provides some basic Vert.x integration capabilities
as well as JSON functions, a function for sending a web request, and an example application.

You can find the source code for Jactl at GitHub: [jactl](https://github.com/jaccomoc/jactl)
