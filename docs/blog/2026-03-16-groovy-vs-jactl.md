---
title:      "Groovy vs Jactl: An Honest Comparison for Embedded JVM Scripting"
date:       2026-03-16T14:12:47+01:00
authors:    [james]
tags: [jvm]
description: "An honest comparison of Groovy and Jactl for evaluating embedded JVM scripting languages."
---

## Introduction

Java applications often choose to use an embedded scripting language for reasons including the
following:
- to provide a powerful customisation mechanism for users
- to be able to change runtime behaviour without rebuilding/redeploying the application
- for providing business rules/logic
- per-tenant configuration for multi-tenant applications
- rapid prototyping for new features

When selecting a JVM scripting language there are many options to choose from including
Jactl, Groovy, Jython (Python), JRuby (Ruby), JavaScript, and Lua.
Out of these, Groovy is probably the most widely used scripting language for Java applications.
This article compares Jactl and Groovy in order to show their strengths and weaknesses and when
you might choose to use one over the other.

<!-- truncate -->

## Setup, Integration, and Dependencies

The latest Jactl JAR file weighs in at under 1MB whereas the core Groovy JAR file is currently around
7.7MB in size.

Groovy has optional dependencies that can be included for capabilities such as JSON, XML,
templating, JSR 223 support, and database access, amongst others.

Jactl has one optional library, _jactl-vertx_, that can be included if you are running in an
application that uses the Vert.x library.

Both Jactl and Groovy can be integrated into an application using the Java standard JSR 223 ScriptEngine API:
```java
import javax.script.*;

public class RunScript {
  
  private static boolean USE_JACTL = true;  // true to run Jactl, false to run Groovy
  
  public static void main(String[] args) throws ScriptException {
    ScriptEngineManager engineMgr = new ScriptEngineManager();
    ScriptEngine        engine    = engineMgr.getEngineByName(USE_JACTL ? "jactl" : "groovy");
    
    engine.put("x", 3);                         // engine binding scope
    engineMgr.put("z", 5);                      // global binding scope
    Object result = engine.eval("x + z");
    System.out.println("Result is " + result);
  }
}
```
Note that Groovy needs an additional groovy-jsr223 library for this.

There are other API calls that JSR 223 provides, including the ability to generate compiled
scripts to execute multiple times.

In addition to using JSR 223 to run scripts, both Jactl and Groovy offer their own proprietary
integration APIs that offer more flexibility and features than the JSR 223 API provides.

## Syntax and Language Features

At a high-level, Jactl syntax and Groovy syntax have a lot in common.
This is not by accident, as Jactl and Groovy are heavily influenced by Java syntax
and Jactl borrowed the `?:`, `<=>`, `?.`, and `?[` operators directly from Groovy
as well as the closure syntax and the implicit `it` parameter.
Jactl also uses the same syntax for List (`[1,2,3]`) and Map (`[a:123,b:456]`) literals
and uses the same syntax for expression strings (`"Value of $x is ${count*cost}"`) and
multi-line strings.

Some things that Groovy has that are not provided in Jactl include:
- an extensive metaobject protocol that allows programmers a powerful
  way to implement things such as builders, and DSLs where missing properties/methods
  can be delegated to a handler
- operator overloading
- the spread operator
- static class fields that are mutable
- access to arbitrary Java classes
- `with` syntax
- support for generics
- support for traits

Jactl does not allow static mutable state to guarantee thread safe access to data and to
prevent deadlocks.
Jactl offers a number of features not available in Groovy including:
- regex capture variables
- Perl-style regex search and replace
- postfix `if` and `unless`
- `and`, `or`, and `not` as well as `&&`, `||`, and `!`
- switch expressions with destructuring

### Regular Expressions and Capture Variables

Jactl uses syntax borrowed from Perl to do regular expression matching and capture the
results of groups into variables called `$1`, `$2`, etc:
```groovy
def count = 23
def x = 'Type=apple, Price=$2.34 per kg'
x =~ /Type=(.*) Price=\$(.*) per/n      // n modifier interprets as number where possible 
def (type,cost) = [$1, $2 * count]
```

Jactl also uses the Perl syntax for regex search-and-replace:
```groovy
str =~ s/:/\n/g
x =~ s/Type=([^ ]*) Price=\$([\d.]*)/$1:$2/g   // can use capture vars in replace string
```

Jactl will match against an implicit `it` variable if none is supplied in situations where `it`
exists (the implicit `it` parameter of a closure, for example).
For example:
```groovy
list.map{ s/[aeiou]//g }      // remove vowels from every entry in list
```

### Postfix `if` and `unless`

In Jactl you can add an `if` or `unless` (opposite of `if`) after a statement:
```groovy
avg = total / count unless count == 0
println x if x % 10 == 0
```

### `and`, `or`, and `not`

Jactl offers `and`, `or`, and `not` at a lower precedence than all other operators to allow
functionality such as this:
```groovy
x =~ /abc/i and found = true and count++
```

### Powerful `switch` statement with destructuring

Jactl offers a powerful `switch` statement that allows standard matching against constant
values as well as matching on type and matching on structure.
Jactl also allows matches to bind parts of the match to a variable and to use `_` when you
want to match anything but don't care what it is and `*` to match any number of elements in
a list context.
For example, a simple quicksort:
```groovy
def qsort(x) {
  switch (x) {
    [_],[]  -> x
    [h,*t]  -> qsort(t.filter{ it < h }) + [h] + qsort(t.filter{ it >= h })
  }
}
```

## Typing

Groovy is dynamically typed by default but does provide the ability to annotate classes and methods with `@CompileStatic`
to enforce static typing and compile-time checking.
Groovy is also said to be optionally typed in that you can optionally specify the types for variables, fields, parameters,
and return types, but even with the type specified, the Groovy compiler will compile invalid code and allow the runtime
checking to catch the error.
For example, this is valid Groovy code as far as the compiler is concerned:
```groovy
int i = 'this is a string'
```
It will compile but at runtime you will get a `RuntimeException` thrown.

Jactl offers the best of both worlds.
You can use `def` everywhere you use a type, and it will behave completely dynamically.
If you specify a type, however, Jactl will enforce type checking at compile-time so you can choose 
when you want dynamic typing and when you want static typing with compile-time validation and runtime efficiency
by choosing when to use `def` or an actual type.
In Jactl, the above example will generate an error at compile-time.

## Numeric Types

Both Jactl and Groovy use `BigDecimal` numbers by default when numbers have a decimal point.
This makes it useful for financial calculations where it is important to preserve exact amounts.

Groovy also provides the ability to use any of the standard Java numeric types
(`byte`, `short`, `char`, `int`, `long`, `float`, and `double`).

In Jactl the `BigDecimal` numbers are called `Decimal` and in addition, there are `byte`, `int`, `long`, and `double`
types that can be used as needed for number types.

## Collections and Functional Programming

Groovy has the full set of Java collection types available to it, while Jactl, by default, provides only the `List`
and `Map` types.
In particular, Jactl has no `Set` type at the moment but script writers can use a `Map` of keys where the values are always `true`
to emulate the same behaviour.

Both Jactl and Groovy provide a comprehensive set of collection methods for functional programming that provide
the ability to:
- iterate over values,
- map values to new values,
- filter certain values,
- inject/fold/reduce a list of values to return a single result,
- find the sum, min, or max
- join elements with a given separator
- group elements together based on given criteria (groupBy)
- reverse a list of elements
- sort a list with a given sort criteria

In Groovy collection methods are eager, in that each method in a chain of methods operates immediately creating 
a new collection that is passed to the next method.

Jactl uses lazy evaluation to avoid having to create intermediate collections (but does provide an additional `collect`
method for situations where eager evaluation is required due to side effects).
This makes the use of these methods much more efficient.

Jactl also adopts the Java stream method names for these methods (with some additional ones) although it does not use
streams in its implementation.

## Classes, Traits, Interfaces, and Generics

Groovy supports the full complement of class features that Java supports including generics.
In addition, it also offers the ability to define traits which is a further powerful way to define common
behaviour with default implementations and state.

In Jactl, classes can extend base classes but Jactl has no way at the moment to define interfaces like you can
do in Groovy and Java.
Jactl also does not support generics.

In Jactl, you cannot define your own constructor.
Jactl will build a constructor which takes as parameters, values that will be used to populate fields that have
not been declared with a default value.
A named argument constructor is also created where a map of field/values is used to populate any of the fields,
including mandatory and optional fields.
If a constructor with more functionality is needed then a static factory method can be used to build instances and
return them.

In Jactl, there is no concept of declaring a field or method as `private`, `protected`, or `public`.
For simplicity, and because Jactl is targeted as a scripting language, Jactl has taken the policy that
all fields and methods are public.

## JSON Support

Both Groovy and Jactl have built-in support for JSON encoding and decoding.
All Jactl classes are created with methods for generating JSON and for decoding JSON into an object of that class
type.

## Error Handling

Groovy supports `throw` and `try`/`catch` like Java does for handling error situations.

Jactl does not support `throw` or `try`/`catch`.
It offers a `die` statement that allows a script to exit immediately if it encounters a situation that it cannot
deal with:
```groovy
die 'Supplied cube must have 6 faces' if cube.size() != 6
```

Jactl does provide specific locations when errors occur at runtime to make it easier to see exactly what caused the
problem.
Instead of getting told that there is an `ArrayIndexOutOfBounds` exception on a given line number, Jactl
will give an error that also shows exactly where in the line the problem occurred.
For example:
```groovy
def arr = new long[2]
int i = 0
println arr[i++] + arr[i++] + arr[i++]
```
This will generate this error:
```groovy
io.jactl.runtime.RuntimeError: Index out of bounds: 2 @ line 3, column 34
println arr[i++] + arr[i++] + arr[i++]
                                 ^
```

## Security and Sandboxing

One of Jactl's main strengths is the ability to provide a secure, sandboxed environment in
which scripts can run.
Out-of-the-box, Jactl scripts can only interact with their environment by accessing and modifying
binding variables passed to it at runtime.
Applications can register new functions and built-in types that scripts can use but the
capabilities offered are then completely under the control of the application.
Applications do not need to worry about Jactl scripts creating threads, spawning processes,
accessing the file system, or accessing the network.
Jactl scripts cannot instantiate or call Java methods directly and so have no way to do
things outside the control of the application.

Groovy was intended to be a very flexible and powerful scripting language that has
access to the full power of the JVM.
It was not intended that it be limited by any type of sandboxing mechanism.
There have been attempts to try to provide a way to limit what Groovy scripts can do
such as using the [SecureASTCustomizer](https://docs.groovy-lang.org/5.0.4/html/gapi/org/codehaus/groovy/control/customizers/SecureASTCustomizer.html)
mechanism to limit what language features a script can use.
This does not, however, provide a secure sandbox (see [Groovy SecureASTCustomizer is harmful](https://kohsuke.org/2012/04/27/groovy-secureastcustomizer-is-harmful/).

## Extensibility

Since Groovy has full access to all Java classes, it automatically has access to new classes or functions that
the application provides.

Jactl provides an easy way to expose additional functions or classes to scripts.
For example, assume that you have this class in your application somewhere for decoding base64
encoded strings:
```java
public class Base64Functions {
  public static byte[] base64Decode(String data) { return Base64.getDecoder().decode(data); }
}
```
Then, to register this as a Jactl method on String objects just invoke this in your application
initialisation:
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```
Now in your Jactl scripts you can invoke the function like this:
```groovy
def encoded = 'AQIDBA=='
def decoded = x.base64Decode()    // will be array of bytes: [1, 2, 3, 4]
```

To add a new `Point` class to Jactl you register the class and the methods that you want to expose:
```java
JactlType pointType = Jactl.createClass("jactl.draw.Point")
                           .javaClass(app.jactl.Point.class)
                           .autoImport(true)
                           .method("of", "of", "x", double.class, "y", double.class)
                           .method("distanceTo", "distanceTo", "other", Point.class)
                           .register();
```

Then scripts can use this new type:
```groovy
Point p = Point.of(1,2)
p.distanceTo(Point.of(3,4))  // result: 2.8284271247461903
```

## Java Interoperability

For situations where you are running trusted scripts and would like the ability to interact with Java classes,
Jactl provides a way to disable the sandbox security and get the same level of access to Java classes as you can
get with Groovy.
See [Allow Host Access](/docs/integration-guide/jactl-context#allowhostaccessboolean-allowed) for more information.

Jactl allows you to completely disable the sandbox security, or to provide access only to select classes,
so it provides a selective weakening of the security model based on the requirements of the application
and the degree of trust in the scripts being run.

## Infinite Loop Detection

Jactl provides the ability to control whether infinite loops should be prevented.
Since it is impossible to detect all infinite loops at compile-time, Jactl offers a way
to configure a timeout or a maximum loop iteration counter and will abort any script that
exceeds these limits.

Groovy provides a way to configure at compile-time a [ThreadInterrupt annotation](https://docs.groovy-lang.org/latest/html/gapi/groovy/transform/ThreadInterrupt.html)
that injects a check for Thread.isInterrupted() at various places in the code.
The application can then provide its own timeout mechanism and interrupt any scripts that
have exceeded whatever time limit the application wishes to impose.

## Multi-Tenancy

Jactl offers a secure sandbox environment for each [JactlContext](/docs/integration-guide/jactl-context)
where scripts and classes compiled using the same `JactlContext` can only access other classes
compiled against the same `JactlContext`.
Each `JactlContext` can also have different global functions and built-in types registered
against it if it is necessary to have different features for each tenant.

Groovy provides isolation using class loaders but state can still leak between tenants via
things like ThreadLocal variables, and class static variables. 
The metaobject protocol of Groovy also allows Groovy scripts to modify behaviour of common types that will
then affect other scripts running within the same process.

## Async and Non-Blocking Execution

Jactl supports event-loop/reactive applications where scripts that perform long-running
operations such as database operations or callouts to external systems should not block
the event-loop thread while waiting for a response.

Groovy does not have any mechanism for doing this except by implementing some type of
callback mechanism.
This breaks the structure of the code since the code has to return at the point the
long-running operation is started thus unwinding the stack and any nesting within loops
and `if` statements.

Some languages, such as Kotlin, have adopted the async/await which leads to the
[Coloured Function](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/)
scenario and also means having to express the logic of your code in an unnatural fashion.

Jactl, however, supports asynchronous functions and long-running operations by transparently
saving the state of the stack in a `Continuation` at the point at which the function call is performed and when
the asynchronous result occurs, the `Continuation` object can resume the code from exactly
where it left off, including all the stack frames that existed at that point.
This works in a similar manner to the Virtual Threads feature that was introduced in Java 21.

## Checkpointing

Jactl has the ability to checkpoint the state of scripts and to be able to restore the state and
continue execution from the point at which a script was checkpointed.
This feature can be used to provide fault tolerance.
The script state can be persisted or replicated over a network and in the
event of a failure, the script can be restored, and it can continue processing from the point at
which it was last checkpointed.

Groovy does not provide any mechanism for saving execution state like this.

## Java 8

Jactl supports all versions of Java from Java 8 onwards.
The latest Groovy versions require Java 11 or later.

## Script Reuse

Both Jactl and Groovy provide the ability to compile scripts into a "script" object that can be invoked multiple
times to avoid having to recompile each time.

In addition, even when using API calls which evaluate the script code directly,
both Jactl and Groovy will cache the compiled scripts so that further evaluation of the same code can avoid the
compilation phase.

## Tooling

Jactl has an IntelliJ plugin and a REPL.
It can also be invoked from the commandline for shell scripting needs where something like `awk` or `perl`
might be used.
Debugging support in IntelliJ is provided by the plugin which includes the ability to breakpoint and step through
code.

Like Jactl, Groovy also has a REPL and the ability to run commandline scripts, but given Groovy's more widespread
adoption and longer history, it has much better tooling support in terms of libraries and frameworks as well as
IDE support.
Groovy support is usually built-in to IDEs providing support for debugging with breakpoints.

## Compile-time Performance

In order to compare compile time performance and runtime performance we will use the following versions:

| Language | Version       |
|----------|---------------|
| Groovy   | 4.0.31        |
| Jactl    | 2.7.1         |
| Java     | 8 (1.8.0_472) |

The reason we are using Groovy 4 (rather than Groovy 5) is that Groovy 4 is the most recent version that still
supports Java 8.

The [Compilation Benchmark](https://github.com/jaccomoc/jactl/blob/main/src/test/java/io/jactl/benchmarks/CompilationBenchmark.java)
compiles 100 files each time and uses the [Java Microbenchmark Harness](https://github.com/openjdk/jmh) to measure the
performance after a suitable warm-up period.

<iframe src="/charts/compilation-speed-chart-java8.html" width="660" height="320" style={{border:'none'}} />

:::note
These benchmarks are not intended to be a measure of what language is better.
They exist solely to provide additional information for people who may be evaluating what language to use.
:::

## Runtime Performance

Both Jactl and Groovy compile to byte code but the metaobject protocol mechanism in Groovy, while very powerful,
does introduce some overhead.

There are two benchmarks that were run:
1. The [GenerateClasses](https://github.com/jaccomoc/jactl/blob/main/src/test/java/io/jactl/benchmarks/GenerateClassesBenchmark.java) benchmark, and
2. the [MonteCarlo Simulation](https://github.com/jaccomoc/jactl/blob/main/src/test/java/io/jactl/benchmarks/MonteCarloBenchmark.java) benchmark.

### Generate Classes

The algorithm for the code originally came from a Perl script that was used to generate the AST classes for the Jactl
compiler itself.
This algorithm was then translated into Jactl, Groovy, and Java.
There are probably faster ways of doing things, particularly in Java, although some concessions have already been made
for the Java version (such as pre-compiling the regular expression patterns).

The source code for each version can be found here:

| Language | Source Code                                                                                                                         |
|----------|-------------------------------------------------------------------------------------------------------------------------------------|
| Jactl    | [GenerateClasses.jactl](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/GenerateClasses.jactl)   |
| Groovy   | [GenerateClasses.groovy](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/GenerateClasses.groovy) |
| Java     | [GenerateClasses.java](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/GenerateClasses.java)     |

<iframe src="/charts/generate-classes-chart-java8.html"  width="660" height="320" style={{border:'none'}} />                                                                                                                                                                                                                                                                                                                                                                                   

### Monte Carlo Simulation

This benchmark uses a Monte Carlo simulation with a pseudo-random number generator to calculate the digits of Pi.
Since it does a lot of method calls, the Groovy script suffers enormously from the metaobject protocol so we have
also included a Groovy version using the `@CompileStatic` annotation which eliminates the metaobject protocol and
enforces strict type checking at compile time.

Note that the simulations all use the same initial seed to ensure that exactly the same calculations are carried
out by each version.

| Language      | Source Code                                                                                                                               |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Jactl         | [MonteCarloPI.jactl](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/MonteCarloPI.jactl)               |
| Groovy        | [MonteCarloPI.groovy](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/MonteCarloPI.groovy)             |
| Groovy Static | [MonteCarloPIStatic.groovy](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/MonteCarloPIStatic.groovy) |
| Java          | [MonteCarloPI.java](https://github.com/jaccomoc/jactl/blob/main/src/test/resources/io/jactl/benchmarks/MonteCarloPI.java)                 |

Note that the code is essentially the same between all versions except that in Jactl `%` is the modulo operator
whereas in Java and Groovy `%` is a remainder operator so to get `a mod b` in Java you have to use `(a % b) % b`.
Jactl compiles `a % b` into the Java equivalent of `(a % b) % b` so even though the methods look different, when
compiled they are doing the same thing.
This difference can be seen in the `nextInt(int bound)` method.

<iframe src="/charts/monte-carlo-pi-chart-java8.html"  width="660" height="380" style={{border:'none'}} />                                                                                                                                                                                                                                                                                                                                                                                   

## Summary

| Feature                      | Groovy                                                                      | Jactl                                                                |
|------------------------------|-----------------------------------------------------------------------------|----------------------------------------------------------------------|
| **JAR size**                 | ~7.7MB (core)                                                               | &lt;1MB                                                              |
| **Java version**             | 11+ (Groovy 5)                                                              | 8+                                                                   |
| **Typing**                   | Dynamic by default. `@CompileStatic` for static                             | Per-variable: `def` for dynamic, typed for compile-time checking     |
| **Decimal arithmetic**       | Fixed point calculations for financial applications                         | Fixed point calculations for financial applications                  |
| **Error handling**           | `throw` / `try` / `catch`                                                   | `die` but no `try/catch`                                             |
| **Switch Expressions**       | `switch` with enhanced matching                                             | Destructuring `switch` with binding and wildcards                    |
| **Regex**                    | Enhanced syntax for pattern matching                                        | Perl-style match and search-replace with capture vars                |
| **Collections**              | Eager evaluation with full Java collection types                            | Lazy evaluation<br/>`List` and `Map` only (no `Set`)                 |
| **Classes**                  | Full support for interfaces, traits, generics                               | Inheritance only: no interfaces, traits, or generics                 |
| **Metaobject protocol**      | Extensive (builders, DSLs, operator overloading)                            | Not supported                                                        |
| **Sandboxing**               | None                                                                        | Secure by default<br/>Java access can be selectively enabled         |
| **Multi-tenancy**            | Class loader isolation but not perfect                                      | Full isolation per `JactlContext`                                    |
| **Async / non-blocking**     | Not supported natively                                                      | Support for async functions with transparent continuations           |
| **Checkpointing**            | Not supported                                                               | Execution state can be checkpointed and resumed                      |
| **Infinite loop detection**  | Via `@ThreadInterrupt` annotation and application timeout                   | Built-in timeout and loop counter                                    |
| **Java interoperability**    | Full access                                                                 | Disabled by default (selectively opt-in)                             |
| **Compile-time performance** | Slower                                                                      | Significantly faster                                                 |
| **Runtime performance**      | Slower due to metaobject protocol overhead<br/>Faster with `@CompileStatic` | Faster than dynamic Groovy<br/>Can be comparable to `@CompileStatic` |
| **JSON support**             | Built-in                                                                    | Built-in                                                             |
| **Tooling**                  | Extensive (IDEs, debuggers, libraries, frameworks)                          | IntelliJ plugin, REPL, command-line                                  |
| **Community / ecosystem**    | Large, mature                                                               | Small, early-stage                                                   |


Jactl is a scripting language with a much smaller (and simpler) footprint in terms of language features while
Groovy is a much more comprehensive, fully featured, programming language,
with a large community and ecosystem, having more libraries, and more mature tooling.

For applications where security is important, or where support for multiple tenants is required, Jactl offers
the ability to run scripts in secure, isolated, sandboxes.
Applications built on an event-loop/reactive architecture can benefit from Jactl's ability to suspend itself during
long-running asynchronous operations and resume from where it left off once the long-running operation has completed.
Fault-tolerant applications can take advantage of the ability to checkpoint the state of Jactl scripts
and restore them and resume them at a later point after a failure has occurred.

Jactl has a small footprint with fast compilation speed and good execution performance and is especially suited to
high-performance, event-based applications.

If you need a more general purpose scripting language with better library support and powerful Domain Specific Language
features, then Groovy is the better choice.
