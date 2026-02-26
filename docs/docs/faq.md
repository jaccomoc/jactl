---
title: FAQ
---

## Why do we need yet another JVM language?

Jactl exists due the desire to have a scripting language that Java applications could embed that would
allow their users to provide customisations and extensions that have the following characteristics:
* **Tightly Controlled**

  The application developer should be able to control what the users can and can't do in the scripting
  language.
  For example, existing mainstream JVM based languages do not have a way to prevent users from accessing
  files, networks, databases, etc. that they should not be touching or prevent them from spawning threads or other processes.

* **Familiar Syntax**

  The language had to use a syntax similar to Java for ease of adoption.

* **Non Blocking**

  A language where scripts can perform blocking operations that need to wait for the result of an
  asynchronous request (such as invoking a function that accesses the database, or performing a remote request to
  another server) but which doesn't block the thread of execution.
  A script should be able to suspend itself and be able to be resumed once the long-running operation completed.
  This allows the scripting language to be used in event-loop/reactive applications where you are never allowed to
  block the event-loop threads.

* **No Await/Async**

  While not wanting scripts to block, script writers should not have to be aware, when invoking a
  function, whether the function is asynchronous or not.
  No [coloured functions](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/)
  and no await/async.
  The language should look completely synchronous but, under the covers, take care of all the asynchronous
  behaviour.

* **Checkpointing**

  Ability for script execution state to be checkpointed where necessary and for this state to be able to be persisted
  or replicated so that scripts can be restored and resumed from where they were up to when a failure occurs.

* **Fun to Code**

  The language should be fun to code in &mdash; a language that provides a nice concise syntax with powerful
  features.

## What does it mean that it never blocks?

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

## Why the need for Jactl to have non-blocking code now that Java 21 has virtual threads?

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

## Why can't Jactl scripts directly invoke Java library functions?

Jactl is intended to be used as a scripting language for Java applications, and therefore, 
it tightly controls what scripts are and aren't allowed to do.
Jactl prevents scripts from directly interacting with the file system, the network, and the JVM
in order to provide a secure scripting language where the application has complete control over
what scripts can do.
The only way in which scripts can interact with their environment is via extension functions
and methods provided by the application in which it is embedded.

## Can I run Jactl code without having to embed it in another appplication?

If you just want to run Jactl scripts on their own, you can run them from the command line.
In this mode they can read from _stdin_ and write to _stdout_ and so are useful for performing
scripting that might otherwise be done by Perl, awk, sed, etc.

See [Command Line Scripting](command-line-scripts) for more details.

Jactl has been used in this way to solve [Advent Of Code](https://adventofcode.com/) puzzles.
See the [Jactl Blog](../blog) for posts with solutions for some past Advent Of Code
problems.

## Is there a REPL for experimenting with Jactl?

Jactl comes with a REPL (Read-Evaluate-Print-Loop) that provides a prompt where you can enter
Jactl code and have it evaluated immediately.
This is useful for experimenting with Jactl to see how the different language features work.

The REPL comes as a separate JAR file as it is bundled with the excellent JLine library to provide
command-line history and editing.

See the [Jactl REPL](https://github.com/jaccomoc/jactl-repl) project for more details including
a link for where the JAR file can be downloaded from.

## What language is the Jactl compiler written in?

The Jactl compiler is written almost entirely in Java (compatible with Java 8+).
There is one Perl script that is used to generate the AST classes from a simplified Java class
specification (see Expr.java and Stmt.java).

## How big is the compiler?

The Jactl source code currently is about 30K lines of code after stripping out comments and
blank lines (or 40K lines including comments and blank lines).

## How many test cases are there?

There are currently roughly 15,000 individual test cases.
Each test case is a Jactl script that is compiled, run, and then verified against the expected
result (or the expected error).

## Was any of Jactl written by AI?

No, AI was not used in the development of Jactl, nor was it used to produce any of the documentation.
The only use of AI was to help in producing the drawings on the home page of the Jactl website.

## What other libraries does Jactl depend on?

Jactl is completely stand-alone, with no dependencies on any other libraries apart from the
[ASM](https://asm.ow2.io/) library which is used for generating the bytecode.
The ASM library is embedded inside the Jactl JAR file where it has been renamed to avoid clashes
with any other versions of the library that might be used by the application in which Jactl is
running.

## Is the Jactl language Object-Oriented or Functional?

Jactl is a multi-paradigm language and offers both Object-Oriented and Functional programming
idioms.
The script writer can choose whether to use one or the other or to use a combination of both.

## Is Jactl strongly-typed?

Jactl is an optionally typed language, so it can be used as a dynamic programming language
(known as _duck typing_) or you can provide types for variables and return types in which case
Jactl will enforce these types.
If type information is provided, then Jactl can make use of this information to compile to more
optimal code in many cases.

## How easy is Jactl to extend?

Jactl is intended to be embedded in Java applications which then provide their own 
application-specific functions as extensions to the Jactl compiler for use by Jactl scripts.
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
See the [Integration Guide](integration-guide/introduction) for more information.

## Why doesn't my Java program exit after evaluating a Jactl script?

If you have a simple Java program where you evaluate a Jactl script using the default execution
environment, there will be some daemon threads that have been started in the background and
if you don't stop these threads, the JVM will not normally exit (unless `System.exit()` is called).

Note that these threads are shared and only started up once.

You can use the static method `io.jactl.DefaultEnv.shutdown()` to stop these threads.

See [Integration Guide](integration-guide/introduction) for more information.

## Is there an IntelliJ plugin for Jactl?

Yes, there is an IntelliJ plugin.
Within IntelliJ, you can search for `jactl` within `Settings...| Plugins | Marketplace` to download and install
the plugin.

The source code and documentation for the plugin can be found here: [Jactl IntelliJ Plugin](https://github.com/jaccomoc/jactl-intellij-plugin)

## How can I improve the runtime speed of my Jactl script?

If you have a large script or class method it is possible that the compiled version exceeds the default
threshold that the JVM uses to determine whether a method is a candidate for hotspot compilation and
thus the JVM is not running the code in the most efficient way it can.
Jactl will also generate additional bytecode to cater for invocation of asynchronous functions where it needs
to capture state in order to suspend execution when an asynchronous function is invoked.
This can also lead to scripts/methods exceeding the hotspot compilation threshold.

To force the JVM to use hotspot compilation even on large methods add the `-XX:-DontCompileHugeMethods`
command line flag to the `java` command.
For example:
```bash
java -XX:-DontCompileHugeMethods -jar jactl-2.2.0-jar
```

## Does Jactl provide any thread synchronisation mechanisms?

Jactl is intended to run in highly multithreaded, event-loop based applications.
To avoid unintended deadlocks, and to avoid blocking event-loop threads, there is no mechanism
in Jactl to explicitly synchronise or wait on other threads.
Since there is no global data of any sort (see next question), there is no need to have a way to
control multiple threads accessing this data from within Jactl.

## Can classes have static fields?

Jactl supports constant data fields for classes using the `const` keyword, where the fields
are simple types (primitives and Strings).
This allows the user to create names for constant values that can then be used multiple times.

Non-final static fields that would support modification are not allowed by Jactl.
The reason that they are not allowed is twofold:
1. Jactl is intended to run in distributed applications where there are multiple instances running.
   Having a static data member in a class for sharing information across all class instances makes
   no sense when there are multiple application instances running since there would then be multiple
   instances of the static data.
2. By avoiding the use of static data, it also means that there is no way for multiple scripts on
   different threads to be trying to access and modify the same data.
   This means that Jactl does not need to provide any thread sychronisation mechanisms that are
   notoriously error-prone and avoids having to worry about deadlocks.

## Can Functions/Methods be Overloaded?

At the moment, Jactl does not support function or method overloading where the multiple functions/methods
are declared with the same name but with different argument types.

Jactl supports default values for arguments which goes someway to supporting a similar type of
functionality.

One reason that Jactl does not support overloading is that functions and methods can be passed
by value by simply referring to the name.
If there are multiple versions of the function with the same name then this would no longer
work since it would be ambiguous which function was being referred to.

A future enhancement may allow overloading by providing a way to name the individual functions
when overloading has been used.

Instead of overloading, another approach is to use a `switch` based on type to descriminate
between the different argument types.
For example, suppose you have a function `f()` and you want these three overloaded versions:
```groovy
def f(int x) { x + x }
def f(String x) { x * 2 }         // String repeat twice
def f(String x, int y) { x * y }  // String repeat y times 
```
In Jactl, you could implement this:
```groovy
def f(x, y = null) {
  switch ([x,y]) {
    [int a, null]     -> a + a
    [String a, null]  -> a * 2
    [String a, int b] -> a * b 
  }
}
```
Note that you won't get compile-time checking of argument types, and you won't be able to
have different return types, so you will need to use `def` or `Object` as the return type
if the different implementations return incompatible types.

## Can closures mutate variables in outer scopes?

In Jactl, unlike Java, closures (lambda functions in Java) and functions can mutate the value of variables
in an outer scope.
This is allowed in Jactl:
```groovy
def i = 0
def f() { ++i }
```

## Do collection methods like `map` and `filter` create new collections?

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

## What is the performance of `switch` expressions?

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

One optimisation that Jactl does is that even where there is a mixture of patterns, if it can
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

## Do Maps support keys that are not String values?

Earlier versions of Jactl limited Map keys to String values.
As of version 2.1.0, Maps can now be keyed on arbitrary types.

Note that Jactl will automatically create `hashCode()` and `equals()` methods for any user-defined
classes and that these use "value" semantics for their comparisons.
This means that the values of the fields of the object will be used to determine equality.
Any two objects with the same field contents will be considered identical.

## What about Map keys that are numbers?

If you use a number as a key for a Map you need to be careful about what the actual underlying
type is.
This is because Maps are implemented using the standard Java `java.util.LinkedHashMap` class
which uses `hashCode()` and `equals()` when determining whether a key exists in a Map.
If you use an `int` value for a key and then try to look it up using a `long` value, the `equals()`
call will determine that they are not equal and the key won't be found.

For example:
```groovy
Map m = [:]
m[1] = 'abc'
m[1L] == null
```
In this example, `m[1L]` will return `null` because `1` and `1L` are not equal from a Java point
of view.

This also applies to numbers embedded in other objects such as lists:
```groovy
Map m = [:]
m[[1,2,3]] = 'abc'
m[[1L,2,3]] == null
```

## Why is there no Set type in Jactl?

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

Adding Sets as a built-in type in Jactl is a possible future enhancement.

## What is the difference between the % and %% operators?

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

With Jactl, it was felt that having a modulus operation was more useful than a remainder operation, so that is why the Jactl
`%` operator works differently to the Java `%` operator.
Other languages (e.g. Python) also take the approach of using `%` for modulus.

Jactl has the `%%` operator which works the same as the remainder operator in Java for situations where you really
want to do a remainder rather than a modulus operation.
Since it corresponds to the native Java operation, it is also slightly more efficient.

## Why is there a do/until loop instead of a do/while loop?

Jactl does not offer a `do/while` loop because of the ambiguities it would create.
Jactl has the concept of a `do` block which turns a set of statements into an expression.
For example:
```groovy
x == 1 and do { y++; z += y }
a = do { for(int i = 0; i < 10; i++) { z += i }; z }  // Assign final value of z to a
```

If Jactl supported `do/while` then consider what this might mean:
```groovy
do { for(int i = 0; i < 10; i++) { z += i }; z }
while (x-- > 0)
{ int j = f(z); z += j }
```

It could be parsed in either of these ways:
```groovy
// Do/while
do { 
  for(int i = 0; i < 10; i++) { 
    z += i 
  }
  z
} while (x-- > 0);
// New code block
{
  int j = f(z)
  z += j 
}
```
Or:
```groovy
// Do block
do { for(int i = 0; i < 10; i++) { z += i }; z }
// While loop  
while (x-- > 0) {
  int j = f(z)
  z += j
}
```

To avoid these problems, Jactl, instead, offers a `do/until` loop which works the same way as a `do/while`
except that the loop continues _until_ the condition is met (rather than while the condition is met):
```groovy
do {
  Token token = nextToken()
  count++
} until (token.isEof())
```

## I still didn't find the answer to my question

Please use the [discussions](https://github.com/jaccomoc/jactl/discussions) section in GitHub to
ask your questions, and we will endeavour to respond in a timely fashion.
