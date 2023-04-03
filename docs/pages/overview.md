---
title: Overview
---

## What is it?
A pragmatic scripting language for Java based applications.

Jactl is a JVM scripting language that you provide to the users of your application to allow them to customise the
behaviour of your application.

## Tightly Controlled Execution Environment

Unlike other JVM based scripting languages, in Jactl the things that a script write can do are tightly controlled.
The only things available to the script writer are the built-in functions and the functions that you provide.
This allows Jactl scripts to be executed in frameworks where creating threads, writing to files, connecting
to remote systems, and performing other blocking operations would not normally be allowed.

When you integrate the Jactl compiler into your application you provide it with the functions that you want
your script writers to have access to and this ensures they can only do things that you permit them to do. This
means you don't need to worry about them accessing files or data that they shouldn't be accessing, for example.

## Familiar Syntax

Jactl syntax is mostly just a subset of Java and Groovy syntax with a bit of Perl thrown in for good measure
so it is easy to pick up for anyone familiar with Java:

```groovy
int fib(int x) {
  return x < 2 ? x : fib(x-1) + fib(x-2);
}
println 'fib(20) = ' + fib(20);
```

Jactl adopts a lot of Groovy syntax so semi-colons are optional, typing is optional, double-quoted strings
allow for embedded expressions, and "return" is optional for the last expression of a function so the previous example
could also be written like this:

```groovy
def fib(x) { x < 2 ? x : fib(x-1) + fib(x-2) }
println "fib(20) = ${fib(20)}"
```

Languange features offered by Jactl:
* Closures
* Classes with inheritance
* Strong/weak typing as desired
* Named parameters for function calls
* Parameters with default values
* Multi-line strings
* Strings with interpolated expressions
* Functional programming idioms (map, filter, collect, each, etc)
* Regex matching syntax
* Regex capture variables

## Non-Blocking

Jactl never blocks the execution thread on which a script is running. This is to support execution in reactive or event
loop based applications.

Blocking operations in Jactl can only occur when invoking a blocking/asynchronous function. When executing such a
function, the script will be suspended and when the blocking function completes, the script execution state is resumed
from the point at which it was suspended. This is done via an internal _continuation_ mechanism.

Script writers can write their code as though it blocks and do not have to be concerned about which functions are
aysnchronous and which are synchronous or have to jump through the normal asynchronous programming hoops with use of
callbacks to continue when a long-running operation completes.

When a blocking operation is invoked, Jactl creates a Continuation object that captures the state of the running
script such as the value of all local variables and the current call stack. When the long-running operation
completes, the script state is restored and the script continues from where it left off.

## Compiles to Bytecode

Jactl compiles to bytecode and is thus able to take advantage of the benefits that the JVM hotspot compiler offers
in terms of performance.

## Command-line REPL

Jactl comes with a REPL (read-evaluate-print-loop) that enables you to play with the language and try out code
interactively.

## Command Line Scripting

As well as being able to use Jactl as a scripting language for Java-based applications, Jactl can also be invoked
from the command line to run standalone scripts. 

## Where should I go next?

* [Getting Started](/pages/getting-started/): Get started with Jactl
* [Examples](/pages/examples/): Check out some more example code snippets giving an overview of the language features
