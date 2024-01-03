---
layout: page
title: "FAQ"
permalink: /faq
---

# Frequently Asked Questions

* [Why do we need yet another JVM language?](#why-do-we-need-yet-another-jvm-language)
* [What does it mean that it never blocks?](#what-does-it-mean-that-it-never-blocks)
* [Why the need for Jactl to have non-blocking code now that Java 21 has virtual threads?](#why-the-need-for-jactl-to-have-non-blocking-code-now-that-java-21-has-virtual-threads)
* [Why can't Jactl scripts directly invoke Java library functions?](#why-cant-jactl-scripts-directly-invoke-java-library-functions)
* [Can I run Jactl code without having to embed it in another appplication?](#can-i-run-jactl-code-without-having-to-embed-it-in-another-appplication)
* [Is there a REPL for experimenting with Jactl?](#is-there-a-repl-for-experimenting-with-jactl)
* [What language is the Jactl compiler written in?](#what-language-is-the-jactl-compiler-written-in)
* [How big is the compiler?](#how-big-is-the-compiler)
* [How many test cases are there?](#how-many-test-cases-are-there)
* [What other libraries does Jactl depend on?](#what-other-libraries-does-jactl-depend-on)
* [Is the Jactl language Object-Oriented or Functional?](#is-the-jactl-language-object-oriented-or-functional)
* [Is Jactl strongly-typed?](#is-jactl-strongly-typed)
* [How easy is Jactl to extend?](#how-easy-is-jactl-to-extend)
* [Does Jactl provide any thread synchronisation mechanisms?](#does-jactl-provide-any-thread-synchronisation-mechanisms)
* [Why can't classes have static data members?](#why-cant-classes-have-static-data-members)
* [Can closures mutate variables in outer scopes?](#can-closures-mutate-variables-in-outer-scopes)
* [Do collection methods like `map` and `filter` create new collections?](#do-collection-methods-like-map-and-filter-create-new-collections)
* [What is the performance of `switch` expressions?](#what-is-the-performance-of-switch-expressions)
* [Why do Maps only support Strings for keys?](#why-do-maps-only-support-strings-for-keys)
* [Why is there no Set type in Jactl?](#why-is-there-no-set-type-in-jactl)
* [I still didn't find the answer to my question](#i-still-didnt-find-the-answer-to-my-question)


### Why do we need yet another JVM language?

I wrote Jactl because I wanted a scripting language that Java applications could embed to allow their users
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

* **Checkpointing**

  Ability for script execution state to be checkpointed where necessary and for this state to be able to be persisted
  or replicated so that scripts can be restored and resumed from where they were up to when a failure occurs.

* **Fun to Code**

  I wanted a language that was fun to code in &mdash; a language that provided a nice concise syntax with powerful
  features that I would want to use to write scripts in.

I could not find any language that met all the criteria, and so I decided to write one instead.

### What does it mean that it never blocks?

Most programs that deal with long-running operations such as sending a request and waiting for
a response take one of two approaches:
1. Tie up an entire thread waiting for the response, or
2. Use an async library where you register callbacks that are invoked once the response is returned.

The first approach does not scale if you need to deal with many thousands of concurrent requests
in a highly multithreaded application, and does not work if the application is an event-loop based
application where the event loop threads are not allowed to block.

The second approach works, and is able to scale, but is a cumbersome and error-prone way for
programmers to have to express the logic of their code.
The code has to now be structured in a way that does not follow the high-level logic that the
programmer wants to express.

Jactl allows code to be written as though the first approach is being used but, under the covers,
it implements the second approach.

When a long-running operation takes place in a script, Jactl captures the execution state of that
script in a `Continuation` object and returns, thus freeing up the event-loop thread.
Once the result of the long-running operation is available, the `Continuation` is resumed which
will continue the operation of the script from the point where it was suspended. 

### Why the need for Jactl to have non-blocking code now that Java 21 has virtual threads?

Java 21 now has virtual threads which implement a similar approach to Jactl in terms
of suspending code for a long-running operation and resuming once the operation has completed.
This works for (almost) arbitrary Java code, so in theory, Jactl could piggyback off that without
having to implement its own suspend/resume functionality. 

For the moment, though:
1. It is not yet clear what the performance implications of moving an application from an event-loop
   approach to one that uses virtual threads are.
2. The suspend/resume functionality in Jactl also allows it to offer a checkpoint mechanism where the
   execution state of a script can be replicated/persisted and restored after a failure to continue
   running from where it left off.
3. Jactl supports Java 8+ so Jactl supports applications that are not yet running on the latest Java.

### Why can't Jactl scripts directly invoke Java library functions?

Jactl is intended to be used as a scripting language for Java applications, and therefore, 
it tightly controls what scripts are and aren't allowed to do.
Jactl prevents scripts from directly interacting with the file system, the network, and the JVM
in order to provide a secure scripting language where the application has complete control over
what scripts can do.
The only way in which scripts can interact with their environment is via extension functions
and methods provided by the application in which it is embedded.

### Can I run Jactl code without having to embed it in another appplication?

If you just want to run Jactl scripts on their own, you can run them from the command line.
In this mode they can read from _stdin_ and write to _stdout_ and so are useful for performing
scripting that might otherwise be done by Perl, awk, sed, etc.

See [Command Line Scripting](command-line-scripts.md) for more details.

Jactl has been used in this way to solve [Advent Of Code](https://adventofcode.com/) puzzles.
See the [Jactl Blog](https://jactl.io/blog) for posts with solutions for some past Advent Of Code
problems.

### Is there a REPL for experimenting with Jactl?

Jactl comes with a REPL (Read-Evaluate-Print-Loop) that provides a prompt where you can enter
Jactl code and have it evaluated immediately.
This is useful for experimenting with Jactl to see how the different language features work.

The REPL comes as a separate JAR file as it is bundled with the excellent JLine library to provide
command-line history and editing.

See the [Jactl REPL](https://github.com/jaccomoc/jactl-repl) project for more details including
a link for where the JAR file can be downloaded from.

### What language is the Jactl compiler written in?

The Jactl compiler is written almost entirely in Java (compatible with Java 8+).
There is one Perl script that is used to generate the AST classes from a simplified Java class
specification (see Expr.java and Stmt.java).

### How big is the compiler?

The Jactl source code currently is about 25K lines of code after stripping out comments and
blank lines (or 33K including comments and blank lines).

### How many test cases are there?

There are currently over 11K individual test cases.
Each test case is a Jactl script that is compiled, run, and then verified against the expected
result (or the expected error).

### What other libraries does Jactl depend on?

Jactl is completely stand-alone, with no dependencies on any other libraries apart from the
[ASM](https://asm.ow2.io/) library which is used for generating the bytecode.
The ASM library is embedded inside the Jactl JAR file where it has been renamed to avoid clashes
with any other versions of the library that might be used by the application in which Jactl is
running.

### Is the Jactl language Object-Oriented or Functional?

Jactl is a multi-paradigm language and offers both Object-Oriented and Functional programming
idioms.
The script writer can choose whether to use one or the other or to use a combination of both.

### Is Jactl strongly-typed?

Jactl is an optionally typed language, so it can be used as a dynamic programming language
(known as _duck typing_) or you can provide types for variables and return types in which case
Jactl will enforce these types.
If type information is provided, then Jactl can make use of this information to compile to more
optimal code in many cases.

### How easy is Jactl to extend?

Jactl is intended to be embedded in Java applications which then provide their own 
application-specific functions as extensions to the Jactl compiler for use by Jactl scripts.
For example, assume that you have this class in your application somewhere for decoding base64
encoded strings:
```java
public class Base64Functions {
  public static byte[] base64Decode(String data) { return Base64.getDecoder().decode(data); }
  public static Object base64DecodeData;
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
See the [Integration Guide](integration-guide.md) for more information.

### Does Jactl provide any thread synchronisation mechanisms?

Jactl is intended to run in highly multithreaded, event-loop based applications.
To avoid unintended deadlocks, and to avoid blocking event-loop threads, there is no mechanism
in Jactl to explicitly synchronise or wait on other threads.
Since there is no global data of any sort (see next question), there is no need to have a way to
control multiple threads accessing this data from within Jactl.

### Can classes have static fields?

Jactl supports static final data fields for classes where the fields are simple types (primitives
and Strings).
This allows the user to create names for constant values that can then be used multiple times.

Non-final static fields that would support modification are not allowed by Jactl.
The reason that they are not allowed is twofold:
1. 
2. Jactl is intended to run in distributed applications where there are multiple instances running.
   Having a static data member in a class for sharing information across all class instances makes
   no sense when there are multiple application instances running since there would then be multiple
   instances of the static data.
2. By avoiding the use of static data, it also means that there is no way for multiple scripts on
   different threads to be trying to access and modify the same data.
   This means that Jactl does not need to provide any thread sychronisation mechanisms that are
   notoriously error-prone and avoids having to worry about deadlocks.

### Can closures mutate variables in outer scopes?

In Jactl, unlike Java, closures (lambda functions in Java) and functions can mutate the value of variables
in an outer scope.
This is allowed in Jactl:
```groovy
def i = 0
def f() { ++i }
```

### Do collection methods like `map` and `filter` create new collections?

When chaining together multiple invocations of collection methods such as `map()`, `flatMap()`,
and `filter()`, Jactl does not create intermediate collections.
For example, consider this:
```groovy
def result = myBigList.map{ it * it }.map{ it + it }.filter{ it % 17 < 5 } 
```
The only time a new collection is created is at the end to create the list containing the
final result.
This makes processing lists like this very efficient in Jactl.

The only exception to this are the methods `collect()` and `collectEntries()` which provide a way
to explicitly force an intermediate collection to be created.
This could be needed if the functions being invoked have side effects and the order of these
side effects needs to be based on fully processing the collection at each step.

### What is the performance of `switch` expressions?

If you are using simple numeric and String literals as the values to match against in your `switch`
expressions (with no `if` expression) then the performance should be similar to what you would get in Java.

If the values are integers and the total range (the difference between the maximum value and the minimum
value) is not too high compared to the total number of entries (current threshold is 5 times the number
of entries) then a fast O(1) table lookup is done.

If the range is too high compared to the number of entries, or there are non-integers in the mix,
then a less efficient (but still reasonably fast) O(log n) lookup is implemented.
The lookup will be done on the hash code of the values, and the JVM will do a binary search to find
the matching hash.

Any other types of matching in the `switch` expression such as matching on type, or using patterns
with destructuring, or using `if` expressions will result in the code needing to check one-by-one,
in order, to see which pattern matches so this runs in O(n) time.

One optimisation that Jactl does is that even if there is a mixture of patterns, if it can
find a run of patterns in the `switch` expression that are simple literals, it will output an
efficient lookup for those values before reverting back to checking each one individually.

For example, the following will result in an O(1) table lookup for the `1,2,3,4,5,6` values,
followed by individual checks for `String` or `List`, followed by an O(log n) table lookup
for the `'abc','def','xyz'` values:
```java
switch (x) {
  1,3,5                  -> true
  2,4,6                  -> false
  String if x.size() > 5 -> true
  List   if x.size() > 5 -> true
  'abc', 'def'           -> false
  'xyz'                  -> true
}
```

### Why do Maps only support Strings for keys?

In Jactl, the built-in Map type currently only supports key values that are Strings.
This seemed like a reasonable limitation for a scripting language to start with.
In the future, there might be an enhancement where Maps could support other key types.

### Why is there no Set type in Jactl?


Jactl does not currently have a built-in Set type, but equivalent behaviour can be obtained using
Maps:
```groovy
def set = [:]       // empty Map
set['abc'] = true   // add 'abc' to the set

// Check if set contains a specific value
if (set[k]) { 
  println "Set contains $k"
}

set.remove(k)      // Remove element of set
```

The only limitation is that Maps currently only support keys of type String, so using a Map as a subsitute for
a Set only works if the values in the set are all String values:
```groovy
def set = [:]
def p = [1,2]
set[p.toString()] = true     // Need to convert to a string first before putting in the set
```

Adding Sets as a built-in type in Jactl is a possible future enhancement.

### What is the difference between the % and %% operators?

In Jactl, the `%` operator works as a modulus operator and always returns a value between `0` and the number on
the right-hand side:
```groovy
 7 %  5     //  7 mod 5  is  2
-2 %  5     // -2 mod 5  is  3
 3 % -5     //  3 mod -5 is -2
-3 % -5     // -3 mod -5 is -3
```
So, if the number on the right-hand side is positive, then the result will always be postive, regardless of whether
the left-hand number is positive or negative.
Similarly, if the number on the right-hand side is negative, the result will always be negative.

In Java, the `%` operator works as a remainder operator whose definition is:
```java
a % b = a - b * (int)(a / b)
```
This leads to results like `-2 % 5` being `-2`.

For me, I felt that having a modulus operation was more useful than a remainder operation, so that is why the Jactl
`%` operator works differently to the Java `%` operator.
Other languages (e.g. Python) also take the approach of using `%` for modulus.

Jactl has the `%%` operator which works the same as the remainder operator in Java for situations where you really
want to do a remainder rather than a modulus operation.
Since it corresponds to the native Java operation, it is also slightly more efficient.

### I still didn't find the answer to my question

Please use the [discussions](https://github.com/jaccomoc/jactl/discussions) section in GitHub to
ask your questions, and I will endeavour to respond in a timely fashion.
