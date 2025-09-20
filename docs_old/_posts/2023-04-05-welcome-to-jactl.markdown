---
layout: post
title:  "Welcome to Jactl"
date:   2023-04-05 16:59:11 +1000
categories: blog
author: "James Crawford"
---

Jactl is a new programming language for JVM based applications.
It provides a secure way for application developers to provide customisation and extension
capabilities to their users but can also be used for commandline scripts.
An interactive REPL is provided for testing out code snippets.

The language has a syntax that borrows heavily from Groovy with a smattering of Perl mixed in
but is simpler than both of those languages.

It is especially suited to event-loop/reactive applications due to its built-in suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

# Why does the world need yet another programming language?

I wrote Jactl because I wanted a scripting language that Java applications could embed in order to allow their users
to provide customisations and extensions that had the following characteristics:

* **Tightly Controlled**

  I wanted the application developer to be able to control what the users could and couldn't do in the scripting
  language.
  I didn't want to use an existing language where there was no way to prevent users from accessing files, networks,
  databases, etc. that they should be touching or spawning threads or other processes.

* **Familiar Syntax**

  I wanted a language that had a syntax similar to Java for ease of adoption.
 
* **Non Blocking**

  I wanted script writers to be able to perform blocking operations where the script needs to wait for something
  (such as invoking a function that accesses the database, or performs a remote request to another server) but which
  doesn't block the thread of execution.
  I wanted to have the script code suspend itself and be able to be resumed once the long-running operation completed.
  This allows the scripting language to be used in event-loop/reactive applications where you are never allowed to
  block the event-loop threads.

* **Hidden Asynchronous Behaviour**

  While not wanting scripts to block, I also did not want the script writers to have to be aware, when invoking a
  function, whether the function was asynchronous or not.
  I wanted a language that looked completely synchronous but which, under the covers, took care of all the asynchronous
  behaviour.

The final motivation for writing a new language compiler was that I was looking for something fun to work on and
at some point I had stumbled across a marvellous book called _Crafting Interpreters_ by Robert Nystrom
and this inspired me to want to write my own compiler.
I highly recommend the book and there is even a free web version available from the
[Crafting Interpreters site](https://craftinginterpreters.com/).

# Who is it for?

It is intended to be used in any Java based application where the application developer wants to provide customisation
capabilities to their users.

It could just be used as a mechanism for reading in a configuration file.
For example, the Jactl commandline scripts and REPL assume a `.jactlrc` [configuration file](https://jactl.io/command-line-scripts#jactlrc-file)
which contains Jactl code that is read and executed at startup to set the values of some properties in a Map.

Other uses could be to provide a customisation mechanism for:
* **Database engine extensions**
* **Real-time applications**
* **Backend customisations for complex multi-tenant web applications**
* **FaaS (Function as a Service)**

  Scripts could act as functions and their secure nature means that many functions can be served from the same process
  to avoid having to spin up instances or processes for each function

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

Check out the [Jactl docs website][jactl-docs] for more info on how to get the most out of Jactl.
File all bugs/feature requests at [Jactl’s GitHub repo][jactl-gh].

[jactl-docs]: https://jactl.io
[jactl-gh]:   https://github.com/jaccomoc/jactl
