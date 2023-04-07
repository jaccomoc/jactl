---
layout: post
title:  "Welcome to Jactl"
date:   2023-04-05 16:59:11 +1000
categories: blog
author: "James Crawford"
---

Jactl is a new programming language for JVM based applications.
It provides a secure way for application developers to provide customisation and extension
capabilities to their users but can also be used via a REPL and for commandline scripts.

The language has a syntax that borrows heavily from Groovy with a smattering of Perl mixed in
but is simpler than both of those languages.

It is especially suited to event-loop/reactive applications due to its builtin suspend/resume
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
The only things available to the script writer are the builtin functions and the functions that you provide.
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

Check out the [Jactl docs website][jactl-docs] for more info on how to get the most out of Jactl.
File all bugs/feature requests at [Jactl’s GitHub repo][jactl-gh].

[jactl-docs]: https://jacommoc.github.io/jactl
[jactl-gh]:   https://github.com/jaccomoc/jactl