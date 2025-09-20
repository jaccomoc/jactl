---
title: Introduction
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It is written to be integrated into Java applications to provide a secure, locked-down, way for
the application's customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its built-in suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

For highly available, distributed applications, it provides a way for the execution state of scripts to be checkpointed and persisted or distributed over a network
to allow scripts to be recovered and resumed after a failure.

## Why do we need yet another language?

The reason that Jactl exists is to provide a scripting language that Java applications can embed to allow their users
to provide customisations and extensions that have the following characteristics:
* **Tightly Controlled**
  <p/>
  The application developer should be able to control what the users can and can't do in the scripting
  language.
  Existing languages are often intended to be general purpose, with JVM based languages
  usually providing a way to invoke arbitrary Java code and not preventing users from accessing files, networks,
  databases, etc. that they shouldn't be touching or prevent them from spawning threads or other processes.

* **Familiar Syntax**
  <p/>Jactl has a syntax similar to Java for ease of adoption.

* **Non Blocking**
  <p/>Jactl allows blocking operations where the script needs to wait for something
  (such as invoking a function that accesses the database, or performs a remote request to another server) but which
  doesn't block the thread of execution.
  In Jactl, the code suspend itself and is resumed once the long-running operation completes.
  This allows the scripting language to be used in event-loop/reactive applications where you are never allowed to
  block the event-loop threads.

* **Hidden Asynchronous Behaviour**
  <p/>While scripts shouldn't block the event loop thread, script writers should not have to be aware, when invoking a
  function, whether the function is asynchronous or not.
  In Jactl, the scripts look completely synchronous even when invoking long-running operations, but, under the covers,  
  the Jactl code takes care of all the asynchronous behaviour.

* **Checkpointing**
  <p/>Ability for script execution state to be checkpointed where necessary and for this state to be able to be persisted
  or replicated so that scripts can be restored and resumed from where they were up to when a failure occurs.

## Who is it for?

It is intended to be used in any Java based application where the application developer wants to provide customisation
capabilities to their users.

It could just be used as a mechanism for reading in a configuration file.
For example, the Jactl commandline scripts and REPL assume a `.jactlrc` [configuration file](command-line-scripts#jactlrc-file)
which contains Jactl code that is read and executed at startup to set the values of some properties in a Map.

Other uses would be to provide a customisation mechanism for:
* **Game Engines**
* **Database Engine extensions**
* **Real-time Applications**
* **Backend customisations for complex multi-tenant web applications**
* **FaaS (Function as a Service)**:
  Scripts could act as functions and their secure nature means that many functions can be served from the same process
  to avoid having to spin up instances or processes for each function

## Features

### Familiar Syntax
Jactl syntax is concise and is mostly just a subset of Java and Groovy syntax with a bit of Perl thrown in for
good measure, so it is easy to pick up for anyone familiar with Java:

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

Jactl is a multi-paradigm language and so has both Object Oriented and Functional programming features.
Here is an example showing a more functional programming approach using some built-in higher-order
functions and built-in regex support to take a file of markdown, extract the top level headings, and generate
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

Another example showing Jactl's pattern matching with destructuring:
```groovy
switch (x) {
  /X=(\d+),Y=(\d+)/n -> $1 + $2    // regex match with capture vars
  [1,*]              -> 'matched'  // list whose first element is 1
  [_,_]              -> 'matched'  // list with 2 elements
  [int,String,_]     -> 'matched'  // 3 element list. 1st is an int, 2nd is a String
  [a,_,a]            -> a * 2      // 1st and last elements the same in 3 element list
  [a,*,a] if a < 10  -> a * 3      // list with at least 2 elements. 1st and last the same and < 10
  [a,${2*a},${3*a}]  -> a          // match if list is of form [2,4,6] or [3,6,9] etc
  [a,b,c,d]          -> a+b+c+d    // 4 element list
}
```

A simple quicksort:
```groovy
def qsort(x) {
  switch (x) {
    [],[_] -> x
    [h,*t] -> qsort(t.filter{it < h}) + h + qsort(t.filter{it >= h})
  }
}
```

See [Language Features](language-features) for more language features and examples.

### Compiles to Java bytecode
Jactl scripts compile to bytecode to take advantage of the performance capabilities of the JVM.

### Supports Java 8+
Compatible with Java 8 and later versions.

### Comes with an IntelliJ Plugin
There is an IntelliJ plugin that supports syntax colouring, auto-indenting, completions, find definition/usages,
running scripts, debugging scripts, and more.
See [IntelliJ Plugin](https://github.com/jaccomoc/jactl-intellij-plugin) for more details.

### Never blocks
Jactl never blocks the execution thread on which a script is running.
This is to support execution in reactive or event loop based applications.

Blocking operations in Jactl can only occur when invoking a blocking/asynchronous function.
When executing such a function, the script will be suspended and when the blocking function completes,
the script execution state is resumed from the point at which it was suspended.
This is done via an internal _continuation_ mechanism.

Script-writers can write their code as though it blocks and do not have to be concerned about which functions are
asynchronous and which are synchronous or have to jump through the normal asynchronous programming hoops with the
use of callbacks to be invoked after a long-running operation completes.

When a blocking operation is invoked, Jactl creates a Continuation object that captures the state of the running
script such as the value of all local variables and the current call stack.
When the long-running operation completes, the script state is restored and the script continues
from where it left off.

### Checkpointing

The execution state of a script can be checkpointed when needed and this checkpointed state can be persisted or
distibuted over a network in order to recover and resume scripts after a failure.

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
* Functional programming
  * Functions as values
  * Higher-order functions
  * Map, filter, collect, each, etc.
  * [Pattern matching with destructuring](language-guide/switch-expressions)
* Classes with inheritance
* Strong/weak typing
* Parameters with default values
* Named parameters for function calls
* Multi-line strings
* Strings with interpolated expressions
* Regex matching syntax
* Regex capture variables

See [Language Features](language-features) for an overview with examples of the language
features.

## Related GitHub projects:

* The [Jactl REPL project](https://github.com/jaccomoc/jactl-repl) provides a simple Read-Eval-Print-Loop shell for running Jactl code interactively.
* The [Jactl-Vertx library](https://github.com/jaccomoc/jactl-vertx) provides some basic Vert.x integration capabilities.
