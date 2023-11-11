---
layout: page
title: "Integration Guide"
permalink: /integration-guide
---

* Table of Contents
{:toc}

This guide details how to integrate Jactl as a scripting language into a Java application and how to extend the language
with additional methods/functions.

## Dependency

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

## Overview

### Jactl.eval()

The simplest way to run a Jactl script is to use the `io.jactl.Jactl` class and the `eval()` method:
```java
Object result = Jactl.eval("3 + 4");         // Will return 7
```

Since this has to compile the script each time, it is not the most efficient way to invoke Jactl scripts and, 
due to the way it waits synchronously for a result even if the script does an asynchronous operation, this call
is not generally suitable if running in an event-loop based application.
(The preferred way to run Jactl code is to use `Jactl.compile()` to compile into a JactlScript object that can
then be invoked multiple times as discussed below).

### Sharing Data with Scripts

To share data between the Java application and the script, it is possible to pass in a Map of global variables
that the script can then access.
```java
var    globals = new HashMap<String,Object>();
globals.put("x", 3);
globals.put("y", 4);
Object result = Jactl.eval("x += y", globals);
int    xval   = (int)globals.get("x");         // xval will be 7 
```

Jactl supports the following object types as values for the global variables:
* Boolean
* Byte
* Integer
* Long
* Double
* BigDecimal
* String
* List
* Map

For Maps, the keys must be Strings and for both Lists and Maps, the values in the List or Map should be one of the
types listed above.

It is also allowed that the value of a variable is `null` which can be used as a way to create the variable when there is no
initial value that makes sense for it.

Objects which are instances of a user defined Jactl class are also supported, so, if a previous script invocation has
returned such a value, then this same value can be passed to another script invocation. Note that this requires
that both invocations use the same JactlContext object (explained later):

```java
var context = JactlContext.create().build();
var x       = Jactl.eval("class X { int i; def f(n) { i * n } }; new X(2)", Map.of(), context);
var globals = new HashMap<String,Object>();
globals.put("x", x);
var result  = (int)Jactl.eval("x.f(3)", globals, context);
assertEquals(6, result);
```

### Jactl.compile()

The preferred way to run Jactl scripts is to compile them using `Jactl.compile()`.
This returns a `JactlScript` object which can then be run as many times as needed.

`JactlScript` objects can be run using the `runSync()` or `run()` methods.

If a scripts perform an asynchronous or blocking operation (for example invoking `sleep()` or performing a database 
operation) then Jactl suspends the script and resumes it once the result is ready.
This allows event-loop based applications to run Jactl scrips without worrying about blocking the event-loop thread that
invokes a Jactl script.
The `runSync()` method works like `eval()` in that it waits for the script to complete before returning the result to
the caller.
If invoking scripts from an event-loop thread of your application be aware that this might therefore block that thread
if the script does something asynchronous.
If the threading model of the application requires that the result of an asynchronous operation is processed on the
same event-loop thread that invoked the operation (for example Vert.x based applications) then using `runSync()` will
cause the event-loop thread to block forever if the script does something asynchronous since the thread waiting for
the script to complete is also the thread that the result needs to be processed on before the script can return.
This is why `runSync()` should only be used if the caller can guarantee that the script does not invoke an asynchronous
function or if the caller is not running on an event-loop thread.

```java
var         globals = new HashMap<String,Object>();
JactlScript script  = Jactl.compileScript("3 + 4", globals);
Object      result  = script.runSync(globals);          // result will be 7
assertEquals(7, result);
```

Again, you can use the Map of globals as a way of sharing data between the script and the application.
The globals you pass in at compile time should contain all the global variables that the script will refer to.
The values in the Map can all be `null` at this point since the compiler just uses the Map to decide if a variable
exists or not.

The globals Map passed into the `runSync()` call (and the `run()` call) will be then be the one that the script
uses at runtime.
This can be a different Map each time but should, obviously, contain an entry for each variable passed in at compile
time or you will get a runtime error when the script tries to access a global variable not present in the map passed
in.

The `run()` method should be used in situations where you don't want to block the current thread (for example,
because you are already on an event-loop thread).
It takes two arguments:
1. the globals Map for global variables, and
2. a completion (of type `Consumer<Object>`) that will be passed the script result when the script completes.

> **Note**<br/>
> If the script is entirely synchronous (doesn't use any asynchronous functions like `sleep()`) then the completion
> will be invoked in the current thread before the call to `run()` returns to the caller.

Here is an example:
```java
var globals = new HashMap<String,Object>();
globals.put("x", null);
globals.put("y", null);
var script  = Jactl.compileScript("x + y", globals);

var globalValues = new HashMap<String,Object>();
globalValues.put("x", 7);
globalValues.put("y", 3);
script.run(globalValues, result -> System.out.println("Result is " + result));
```

### Errors

If an error is detected at compile time then an exception of type `io.jactl.CompileError` will be thrown.

If a runtime error occurs then an exception of type `io.jactl.runtime.RuntimeError` will be thrown.

A special subclass of `RuntimeError` called `io.jactl.runtime.DieError` will be thrown if the script invokes the `die` statement.

All these exception classes are subclasses of the top level `io.jactl.JactlError`.

> **Note**<br/>
> All of these classes are unchecked exceptions so be sure to catch them at the appropriate place in your code.

## JactlContext

Although most of the examples shown so far haven't used a JactlContext, in general, you will need to create an object
of type `io.jactl.JactlContext` when evaluating or compiling scripts.
The `JactlContext` allows you to set some options (including the execution environment - see below) and provides
a way to compile sets of related Jactl scripts and Jactl classes.
Jactl classes can only be referenced from scripts and classes that are compiled using the same `JactlContext`.
For example, this provides a level of separation if you want to be able to run a multi-tenant application where 
each tenant has their own set of scripts and classes.

To create a `JactlContext` object you call the class static method `create()` followed by a number of fluent-style
methods for setting various options and then finally invoke `build()` to return the instance.

So to get an instance with defaults for everything:
```java
JactlContext context = JactlContext.create()
                                   .build();
```

Then to use the `JactlContext` you have built, pass it to the `Jactl.eval()` or `Jactl.compileScript()` methods:
```java
JactlContext context = JactlContext.create()
                                   .build();

var globals = new HashMap<String,Object>();
var script  = Jactl.compileScript("13 * 17", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));    // Output will be: "Result is 221"

Object result = Jactl.eval("13 * 17", globals, context);
assertEquals(221, result);
```

### javaPackage(String pkg)

Jactl scripts are compiled into Java classes.
They have a Jactl package which could be `''` if unspecified or something like `x.y.z` if a package declaration
exists at the top of the script.

In order not to clash with Java packages, the Jactl package hierarchy is placed inside a default Java package of
`io.jactl.pkg`.
Therefore, scripts without a Jactl package will end up in the package `io.jactl.pkg` and with a package declaration
like `x.y.z` the package will be `io.jactl.pkg.x.y.z`.

If you would like to change what Java package the Jactl scripts and classes should reside under you can use the
`javaPackage()` method when building your `JactlContext`:
```java
JactlContext context = JactlContext.create()
                                   .javaPackage("io.jactl.pkg.abc")
                                   .build();
```

> **Note**<br/>
> If you are trying to keep different sets of Jactl scripts and classes separate from other sets of scripts and
> classes then it makes sense to put them in different package hierarchies to avoid any name clashes.

### minScale(int scale)

By default, Jactl uses `BigDecimal` for numbers with decimal points (although `double` is also supported).
Since BigDecimal supports arbitrary precision we need to decide, when doing division, how many decimal places
to keep if the result has a non-terminating series of digits after the decimal place.

The default is to take the maximum precision of the two operands plus an additional `10` digits (the `minScale` value).
You can change the default of `10` by using the `minScale()` method:
```java
JactlContext context = JactlContext.create()
                                   .minScale(5)
                                   .build();
```

### environment(JactlEnv env)

The `JactlContext` is also used to configure what runtime execution environment you are using.
If no environment is specified, Jactl defaults to using an environment of type `io.jactl.DefaultEnv` which uses two
`java.util.concurrent.ExecutorService` based thread pools: one for the event-loop threads and another one for the 
blocking threads.

If you are running in a different type of event-loop based application then you should create a `JactlEnv` class 
appropriate for your environment (see [Jactl Execution Environment](#jactl-execution-environment) for more details),
or use one provided (for example the `io.jactl.vertx.JactlVertxEnv` environment class from the `jactl-vertx` library).

Here is an example that specifies a `JactlVertxEnv` object as the execution environment:

```java
var vertx   = Vertx.vertx();
var env     = new JactlVertxEnv(vertx);

var context = JactlContext.create()
                          .environment(env)
                          .build();
```

### debug(int level)

The `debug()` method allows you to enable debug output.
It outputs the generated compiled JVM instructions for use when troubleshooting.

The supported values are:
* `0` &mdash; no output (the default value)
* `1` &mdash; output instructions
* `2` &mdash; output instructions with additional information about code locations

### Chaining Method Calls

The methods for building a `JactlContext` can be chained in any order (apart from `create()` which must be first
and `build()` which must come last).
So to explicitly build a `JactlContext` with all the default values:
```java
var context = JactlContext.create()
                          .javaPackage("io.jactl.pkg")
                          .environment(new io.jactl.DefaultEnv())
                          .minScale(10)
                          .debug(0)
                          .build();

// This is equivalent to:
var context = JactlContext.create().build()
```

## Compiling Classes

As well as compiling Jactl scripts, you may also need to compile Jactl classes.
As mentioned previously, Jactl classes, once compiled, can only be accessed directly by other Jactl scripts and
Jactl classes that use the same `JactlContext`.

To compile a class use one of the `Jactl.compileClass()` methods:
```java
var context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);
```

Then to use the class, compile and run a script that refers to it using the same context:
```java
var context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);

var globals = new HashMap<String,Object>();
var script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));
```

> **Note**<br/>
> When compiling classes there is no way to pass in a set of global variables because Jactl classes have no access
> to global variables.
> Global variables are only accessible from Jactl scripts.

If the class has a Jactl package declaration and the script is in a different package, then the script should either
import the class from the other package or use a fully qualified class name:
```java
var context = JactlContext.create().build();
Jactl.compileClass("package a.b.c; class Multiplier { int n; def mult(x){ n * x } }", context);

var globals = new HashMap<String,Object>();
var script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));

// Or import the class
script  = Jactl.compileScript("import a.b.c.Multiplier; def x = new Multiplier(13); x.mult(17)", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));

// Or put script in same package
script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));
```

When compiling a class you can also specify the Jactl package name for the class explicitly:
```java
JactlContext context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context, "a.b.c");
```

The Jactl package name passed in can be:

| Value             | Description                                                                                                    |
|:------------------|:---------------------------------------------------------------------------------------------------------------|
| `null`            | Error if package not specified in class declaration.                                                           |
| `""`              | If package name not specified in class declaration then it will default to `""` and be placed in root package. |
| any other value   | If not specified then default to this value. If specified it must match this value or an error will occur.     | 

There is also a similar `Jactl.compileScript()` method where you can pass in the Jactl package name for the script as well:
```java
var context = JactlContext.create().build();
var globals = new HashMap<String,Object>();
var pkgName = "a.b.c";
var script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context, pkgName);
```
The only reason for passing an explicit Jactl package when compiling a script is so to allow the script to access
classes in the same package without having to explicitly qualify them with a package name or to import them.

> **Note**<br>
> Like classes, scripts can specify a package themselves using the `package` directive and the rules about how package
> names apply are the same as for classes (see table above).

## Location of Jactl Scripts and Classes 

In the examples we have shown we have always used constant strings as the source code for our Jactl scripts and classes
but this is unlikely to be the case in a proper application.
In general, the scripts and classes will be read from files or from the database since the whole idea using Jactl to
is to that Jactl scripts provide a way to customise the behaviour of an application.

See [Example Application](https://github.com/jaccomoc/jactl-vertx#example-application) which gives an example
application that reads Jactl scripts on demand from the file system in order to respond to incoming web service requests.

## Jactl Execution Environment

In order for Jactl to support event-loop based applications with non-blocking execution of scripts, it needs
to know how to schedule blocking and non-blocking operations.

An implementation of the `io.jactl.JactlEnv` interface must be provided that supplies a bridge from Jactl to
the execution environment in which it is running.

The interface has the following methods that need to be implemented:
```java
public interface JactlEnv {
  Object getThreadContext();
  void scheduleEvent(Object threadContext, Runnable event);
  void scheduleEvent(Object threadContext, Runnable event, long timeMs);
  void scheduleEvent(Runnable event, long timeMs);
  void scheduleBlocking(Runnable blocking);
}
```

There are also two other optional methods with default implementations (`saveCheckpoint()` and `deleteCheckpoint()`)
that will be described below in the section on [Checkpoints](#Checkpoints).

### getThreadContext()

In order to support event-loop based applications where resumption of a long-running operation should occur on the
original thread that spawned the long-running task, there is a method called `getThreadContext()` that should return
a handle that identifies the current event-loop thread, and which, when passed to `scheduleEvent()` will allow
the new event to be rescheduled on the same original event-loop thread.

If there is no such concept of rescheduling on the same thread then `getThreadContext()` is allowed to return `null`.
If `getThreadContext()` is called from a non-event-loop thread then it should return `null` unless it makes
sense to return a context for a specific event-loop thread (for example if there is some way for it to know
that the currently running code is somehow related to a previous event on a specific event-loop thread).

### scheduleEvent()

The `scheduleEvent()` methods are used to schedule non-blocking events onto an event-loop thread.

If passed a thread context that was returned from `getThreadContext()` then the event should be scheduled onto the
same event-loop thread.
If not passed a thread context then it is free to pick an event-loop to schedule the event on.

If passed a `timeMs` value then the event should be scheduled to run after the specified amount of time has expired.

### scheduleBlocking()

This method is used to add execution of some blocking code to a queue for execution by a blocking thread once
one becomes available.

### Example Implementation

As an example of how to write a `JactlEnv` class have a look at the
[`io.jactl.vertx.JactlVertxEnv`](https://github.com/jaccomoc/jactl-vertx/blob/main/src/main/java/io/jactl/vertx/JactlVertxEnv.java)
class provided in the [`jactl-vertx`](https://github.com/jaccomoc/jactl-vertx) project or see the default 
[`io.jactl.DefaultEnv`](https://github.com/jaccomoc/jactl/blob/master/src/main/java/io/jactl/DefaultEnv.java) implementation.

### .jactlrc File

If your environment class has a no-arg constructor, and you want to be able to run your extension functions and methods
(see below) in the [Jactl REPL](https://github.com/jaccomoc/jactl-repl) or in [commandline](command-line) scripts,
then you can configure your `.jactlrc` file with the name of your environment class.

See [`.jactlrc` File](command-line-scripts#.jactlrc-file) for more details.

## Checkpoints

Jactl provides a `checkpoint()` function that allows scripts to checkpoint their current state with aim of then being
able to restore this state elsewhere and continue execution from where the script left off in the event of an outage.
Jactl provides the checkpoint state as a `byte[]` object, but it is up to the execution environment in which Jactl is
embedded to provide a way to preserve this state and to then decide when it is appropriate to continue the execution
state of the checkpoint on another instance (or after a restart on the same instance, for example).

The checkpoint state could be stored to a database, or replicated over a network, or written to the file system.
It is up to the application to decide what is appropriate based on the overall architecture of the solution.
If the scripts never invoke `checkpoint()` or there is no need to replicate or preserve script state then this feature
can be ignored.

The `JactlEnv` implementation discussed above has two additional methods for dealing with checkpoints:
1. `saveCheckpoint()`, and
2. `deleteCheckpoint()`

If an application wants to be able to save checkpoint state then the application should provide an implementation for
both of these methods, however, since these methods have `default` implementations that do nothing, it is not mandatory
to provide implementations for these methods.

### saveCheckpoint()

The signature for this method is:
```java
  void saveCheckpoint(UUID             id,
                      int              checkpointId,
                      byte[]           checkpoint,
                      String           source,
                      int              offset,
                      Object           result,
                      Consumer<Object> resumer);
```

The parameters are:

| Parameter    | Description                                                                                                                                         |
|:-------------|:----------------------------------------------------------------------------------------------------------------------------------------------------|
| id           | UUID that uniquely identifies the running script instance.                                                                                          |
| checkpointId | If the script checkpoints multiple times this identifies which checkpoint we are up to. It is guaranteed to be an incrementing number with no gaps. |
| checkpoint   | The actual checkpointed state of the script instance as a `byte[]` object.                                                                          |
| source       | The location in the source code where `checkpoint()` is called (for error reporting).                                                               | 
| offset       | Offset into source where `checkpoint()` is called (for error reporting).                                                                            |
| result       | The result that should be passed to the `resumer` once the checkpoint has been saved.                                                               |
| resumer      | A callback that resumes execution of the script instance once the checkpoint has been saved.                                                        |

All script executions are given a unique UUID to identify the executing instance regardless of what actual script is
being executed.
Over the lifetime of the application many script instances will be executed for many scripts and the UUID identifies
each of these instances.

When a script instance invokes the `checkpoint()` function to checkpoint its state it keeps track of how many times it
has been checkpointed before and the `checkpointId` parameter is an incrementing count that allows us to identify the
checkpoint for that particular script instance.
The combination of the `id` and the `checkpointId` uniquely identifies a given checkpoint state.

The call to saveCheckpoint() is invoked from an event-loop thread so implementations need to be careful
not to block this thread while saving the checkpoint.

Once the checkpoint has been saved, the `resumer` object should be invoked, passing in the `result` or a `RuntimeError`
object if an error has occurred.
It is up to the implementation to make sure that this is done on an event-loop (non-blocking scheduler) thread.

A naive, incomplete, implementation of `saveCheckpoint()` (one that doesn't scale) could look like this:
```java
  static JactlEnv env;
    
  @Override
  public void saveCheckpoint(UUID             id,
                             int              checkpointId,
                             byte[]           checkpoint,
                             String           source,
                             int              offset,
                             Object           result,
                             Consumer<Object> resumer) {
    // If environement supports scheduling on a specific thread then remember current thread
    Object threadContext = env.getThreadContext();
    
    env.scheduleBlocking(() -> {
      Object retVal = result;
      try {
        FileOutputStream fileOutput = new FileOutputStream("/tmp/checkpoints.data");
        fileOutput.write(checkpoint);
        fileOutput.close();
      }
      catch (IOException e) {
        retVal = new RuntimeError("Error persisting checkpoint", source, offset, e);
      }
      Object finalRetVal = retVal;
      
      // Make sure resumption of script is done on event-loop thread (pass in threadContext if environment
      // supports scheduling onto a specific thread)
      env.scheduleEvent(threadContext, () -> resumer.accept(finalRetVal));
    });
  }
```

> **Note**<br>
> Once a checkpoint has been saved it is safe to delete the previous checkpoint for that script instance.
> Alternatively, you can wait for the final `deleteCheckpoint()` call to know when to clean up the old
> checkpoints.

### deleteCheckpoint()

In order to know when it is safe to clean up old checkpoints, at the end of a script instance execution the
`deleteCheckpoint()` is invoked, passing in the id of the script instance and the last checkpoint id that was
saved for that instance.

The `deleteCheckpoint()` call is done on an event-loop thread and, since there is no need to actually guarantee that
the deletion has completed before returning, if your implementation does anything that will block then you should make 
sure to schedule the work on a separate blocking thread.

## Adding New Functions/Methods

Since the idea of integrating Jactl into an application is to provide a way to customise behaviour, it is likely that
the application would want to offer some additional global functions and methods that the Jactl scripts and classes
can use.

For example, assume we want to provide a method on byte arrays for encoding into base64 and a method on Strings for
decoding from base64:
```groovy
> def x = [ 1, 2, 3, 4] as byte[]
> x.base64Encode()
AQIDBA==
> 'AQIDBA=='.base64Decode()
[1, 2, 3, 4]
```

In addition, to show how we can add new global functions, we will use the example function `sendReceiveJson()` that
is provided by the [`jactl-vertx`](https://github.com/jaccomoc/jactl-vertx) project.
It is an example function for sending/receiving JSON messages over HTTP:
```groovy
> sendReceiveJson(url:'http://localhost:52178/wordCount', request:[text:'here are some more words to be counted'])
[response:8, statusCode:200]
```

To make Jactl aware of new methods and global functions we use `Jactl.method()` (for methods) and `Jactl.function()`
(for functions) and use fluent-style methods to build the method/function, before finally invoking `.register()`
to register it with the Jactl.

For example to register the `base64Decode()` method the following should be invoked sometime during application
initialisation (before any Jactl scripts/classes are compiled):
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```

The `Jactl.method()` method takes an argument, being the type of object on which the new method should exist.
For `base64Decode()` the method exists only on String objects, so we use `JactlType.STRING` to specify the Jactl
type for Strings.

The types that can be used here are:

| Type               | Description                                        | Java Type  |
|:-------------------|:---------------------------------------------------|:-----------|
| JactlType.ANY      | Any object                                         | Object     |
| JactlType.STRING   | Strings                                            | String     |
| JactlType.LIST     | Lists                                              | List       |
| JactlType.MAP      | Maps                                               | Map        |
| JactlType.ITERATOR | Anything that is iterable (List,Map,String,Number) | Object     | 
| JactlType.BOOLEAN  | boolean objects                                    | boolean    |
| JactlType.BYTE     | byte objects                                       | byte       |
| JactlType.INT      | int objects                                        | int        |
| JactlType.LONG     | long objects                                       | long       |
| JactlType.DOUBLE   | double objects                                     | double     |
| JactlType.DECIMAL  | BigDecimal objects                                 | BigDecimal | 
| JactlType.NUMBER   | All numeric types                                  | Number     |

> **Note**<br/>
> In Jactl, unlike Java, primitive types can have methods defined for them.

For the `base64Encode()` method we need to specify that the method applies to byte arrays, so we use the `JactlType.arrayOf()`
method and pass in `JactlType.BYTE` to specify an array of bytes:
```java
Jactl.method(JactlType.arrayOf(JactlType.BYTE))
     .name("base64Encode")
     .impl(Base64Functions.class, "base64Encode")
     .register();
```

For global functions, there is no type that owns the function, so we use `Jactl.function()` instead of `Jactl.method()`
to construct the new function object and then use the same fluent methods to provide information about the function to
Jactl:
```java
Jactl.function()
     .name("sendReceiveJson")
     .param("url")
     .param("request")
     .impl(VertxFunctions.class, "sendReceiveJson")
     .register();
```

For both methods and global functions, the `name()` method must be specified as shown in the examples.
This is the name that Jactl scripts will use to refer to the method or function.

The other mandatory method is `impl()` which tells Jactl the class and the name of the static method to invoke
for the implementation of the method/function.
In addition, if a third argument is passed to `impl()` it is the name of a public static field of type Object
in the implementing class that the Jactl runtime can use to cache some data needed for the function/method.
If no third argument is present it will default the name to the static method name appended with `Data`.

So this:
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```

is the same as this:
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode", "base64DecodeData")
     .register();
```

> **Note**<br/>
> Each function/method being registered needs to provide its own public static field of type Object.
> These fields cannot be shared between functions.

If there are no parameters to the function/method then this is all that is needed before invoking the
`register()` method to register the function.

For methods, the first argument to the static implementing method will be the object on which the method is being
invoked.
For the `base64Encode()` method, the static implementation function should take a `byte[]` argument, and 
the `base64Decode()` static function should take a `String` argument.
Since there are no parameters for these methods, the implementing class can be as simple as this:
```java
  public class Base64Functions {
    public static String base64Encode(byte[] data) {
      return new String(Base64.getEncoder().encode(data));
    }
    public static Object base64EncodeData;

    public static byte[] base64Decode(String data) {
      return Base64.getDecoder().decode(data);
    }
    public static Object base64DecodeData;
  }
```

### Parameters

Our example `base64Encode()` and `base64Decode()` methods did not have any parameters but the `sendReceiveJson()`
global function takes two parameters: the URL to send to, and the request object to be sent as JSON.
When registering a method or function, if there are parameters then they need to be declared using the `.param()`
method.
This allows Jactl to support named parameter passing.
For example, we declared the `url` and `request` parameters for the `sendReceiveJson()` function like this:
```java
Jactl.function()
     .name("sendReceiveJson")
     .param("url")
     .param("request")
     .impl(VertxFunctions.class, "sendReceiveJson")
     .register();
```
This allows us to invoke the function from Jactl using either positional or named parameters:
```groovy
> sendReceiveJson('http://localhost:52178/wordCount', [text:'here are some words to be counted'])
[response:7, statusCode:200]
> sendReceiveJson(url:'http://localhost:52178/wordCount', request:[text:'here are some more words to be counted'])
[response:8, statusCode:200]
```

Jactl infers the type of the parameters from the actual Java parameter types of the implementation method so there
is no need to specify a type for the parameters.

> **Note**<br/>
> The names that you register the parameters with using `param("...")` do not have to match the names in the
> Java static method but do have to be declared in the right order.
> The names will be used to validate any calls that are done using named arguments.

### Default Values

When registering the parameter names you can also specify a default value for parameters that are optional.
For example, the built-in `String.asNum(base)` method parses a String that could be in any base (up to 36).
By default, it assumes base 10 but can be used to parse hex strings or binary strings etc.:
```java
Jactl.method(STRING)
     .name("asNum")
     .param("base", 10)
     .impl(BuiltinFunctions.class, "stringAsNum")
     .register();
```

The `param("base", 10)` means that if no parameter is supplied it will be automatically filled in with `10`.

### Exception Handling

If you would like your method or function to indicate that a runtime error has occurred where it doesn't make sense
to continue execution of the script then you should throw a `io.jactl.runtime.RuntimeError` exception.
There are two constructors of `RuntimeError` that can be used:
```java
  public RuntimeError(String error, String source, int offset);
  public RuntimeError(String error, String source, int offset, Throwable cause);
```

The `source` and `offset` parameters refer to the source code and offset into the source code where the error occurs.
In order to know where your method/function is being invoked in the source code, you just declare a `String` and `int`
parameter in your static implementation method and Jactl will take care of populating these parameters with the values
from where in the code the method/function was invoked.
Don't declare these using `.param()` since they are not explicit parameters that the script writer passes in.

Any actual parameters for the method/function should be declared in the implementation method after these `String` and
`int` parameters.
Note that for methods, the `String` and `int` parameters come after the object that the method is being invoked on.

Here is an example class showing a global function implementation and a method implementation that both need to
throw `RuntimeError` exceptions:
```java
public class ExampleFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.function()
         .name("exampleFunction")
         .param("param1", "default-value")
         .impl(ExampleFunctions.class, "exampleFunctionImpl")
         .register();
    
    Jactl.method(JactlType.ANY)
         .name("exampleMethod")
         .param("firstArg", -1)
         .impl(ExampleFunctions.class, "exampleMethodImpl")
         .register();
  }
  
  public static Object exampleFunctionImpl(String source, int offset, String param1) {
    try {
      ...
    }
    catch (Exception e) {
      throw new RuntimeError("Error invoking exampleFunction", source, offset, e);
    }
  }
  
  public static int exampleMethodImpl(Object obj, String source, int offset, int firstArg) {
    try {
      ...
    }
    catch (Exception e) {
      throw new RuntimeException("Error invoking exampleMethod", source, offset, e);
    }
  }
}
```

### Suspending Execution

Some functions/methods need to perform long-running operations and wait for the operation to complete before returning
a result. For example, the `sendReceiveJson()` function sends a request to a remote service and waits for the response
before returning the response to the caller.

The problem is that we don't want our `sendReceiveJson()` function to actually block and wait for the response.
The response may take a long time, or we might time out waiting, and we don't want to block the event-loop thread
for that entire time.
We want the function to suspend execution of the current script until the result is returned, freeing up the event-loop
thread to process other events.

Any function that is going to suspend the current execution needs to accept a `Continuation` object as its first
argument to allow the function to be able to be resumed/continued once the asynchronous operation has finished.
The idea is that the function can check the `Continuation` object to see if it is being resumed (continued) after a
suspension and can also obtain the result of the long-running asynchronous operation from the `Continuation` object
if it is being resumed and then perform any further processing it needs to do after the asynchronous operation has
returned a result.
If the `Continuation` object is `null` it means that this is the first invocation and that it is not being resumed
after a suspension.

There is no need to tell Jactl that the function/method is asynchronous as the presence of this `Continuation` 
parameter in the signature of the implementation method already indicates that the function/method can suspend
execution.

For global functions that are asynchronous, the `Continuation` parameter of the function is always the first parameter.
For methods that perform asynchronous operations, the `Continuation` parameters must be the second parameter after the
parameter that represents the object on which the method is being invoked:
```java
  public static Object someAsyncFunction(Continuation c, Object param1, String param2) { 
    ...
  }

  public static String someAsyncMethod(String obj, Continuation c, String param1, Object param2, int param3) {
    ...
  }
```

When performing asynchronous operations, there will invariably be errors that need to be thrown as exceptions, and so
the usual signature for an asynchronous function will have the `Continuation` parameter followed by the `String`
parameter for the source code, and the `int` parameter for the offset into the source where the invocation is taking
place so the example above would more likely look like this:
```java
  public static Object someAsyncFunction(Continuation c, String source, int offset, Object param1, String param2) { 
    ...
  }

  public static String someAsyncMethod(String obj, Continuation c, String source, int offset, String param1, Object param2, int param3) {
    ...
  }
```

In order to actually suspend the execution state, there are two static methods provided on the `Continuation` class:
1. `Continuation.suspendBlocking()`, and
2. `Continuation.suspendNonBlocking()`.

The `suspendBlocking()` call is for use where the work to be done will be queued to occur on a blocking thread from
the pool of blocking threads.
Once a blocking thread is available it will run the work until completion and then schedule processing of the result
back onto an event-loop thread (possibly the original event-loop thread if the execution environment provides that
level of control).

The `sendReceiveJson()` function, for example, needs to send a request, wait for a response, and return the response
to the caller.
Since we can't do this on an event-loop thread, we could schedule all of this to occur on a blocking thread using the
`Continuation.suspendBlocking()` call like so:
```java
  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    Continuation.suspendBlocking(() -> {
      try {
        // send request
        ...
        // wait for response
        ...
        return result;
      }
      catch (Exception e) {
        return new RuntimeError("Error invoking " + url, source, offset, e);   // Note: "return" not "throw"
      }
    });
  }
```

> **Note**<br/>
> Any errors that occur on the blocking thread should be **returned** (not thrown) as `RuntimeError` objects as shown.

The use of `Continuation.suspendBlocking()` like this should be when the work being done requires using a blocking API
and there is no away to avoid having thread blocked while the work is being performed.
An interface to an SQL database might use this approach, for example, if the only API provided is a synchronous one.

In the case of the `sendReceiveJson()` function, however, since we are using Vert.x to do the send and receive, and
Vert.x is by nature an asynchronous library, we don't need to tie up an entire blocking thread while we wait for the
response. 
Instead, we use the `Continuation.suspendNonBlocking()` and pass it a handler that will initiate the request and 
register its own handler with Vert.x to be invoked when the response is received.

> **Warning**<br>
> It is important that the initiation of the async request be done inside the handler passed to 
> `Continuation.suspendNonBlocking()` and not before, in order to guard against race conditions where the 
> response might be received before we have finished suspending our execution.

The handler that we pass to `Continuation.8suspendNonBlocking()` accepts two arguments:
1. a `JactlContext` in case we need it, and
2. a resume handler that will take care of resuming the execution and that must be passed the result once available.

In our example we don't need the `JactlContext` so we just need to make sure that when the response handler we register
with Vert.x is invoked we then invoke the resume handler we get passed in to us with the result.
This will ensure that the script resumes on an event-loop thread (since Vert.x schedules response handlers on the
same event-loop thread that the asynchronous operation was scheduled from) and that the result is handed back to the
script.

A simple implementation of all this could look like this:
```java
public class VertxFunctions {
  private static WebClient webClient;

  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    Continuation.suspendNonBlocking((context, resumer) -> {
      try {
        webClient.postAbs(url)
                 .sendJson(request)
                 .onSuccess(response -> {
                   // Our Vert.x response handler
                   resumer.accept(getResult(response));   // continue our script with result
                 });
      }
      catch (Exception e) {
        resumer.accept(new RuntimeError("Error invoking " + url, source, offset, e));   // Return error as result
      }
    });
    return null;       // never happens since function is async
  }
  
  ...
}
```

> **Note**<br>
> Some details (such as better error handling) have been left out for brevity.
> To see the full implementation see the [`VertxFunctions` example class](https://github.com/jaccomoc/jactl-vertx/blob/main/src/test/java/io/jactl/vertx/example/VertxFunctions.java).

> **Note**<br>
> Any errors should be returned as `RuntimeError` objects to the `resumer` handler as a result as shown.

Both of the `Continuation.suspendBlocking()` and `Coninuation.suspendNonBlocking()` calls work by throwing a 
`Continuation` object.
Each function in the call stack can then catch the `Continuation` and throw a new `Continuation` chained to the old
one that captures any state they need to preserve.
When continued after a resumption, the `Continuation` passed in will have this preserved state to allow the function
to work out where it was up to and to continue with all the state it had before it was suspended.

### Integration with REPL and CommandLine Scripts

For ease of integration with the Jactl REPL and Jactl commandline scripts, it is recommended that you have a
public static function called `registerFunctions(JactlEnv env)` in one of your classes (generally the same one
where the implementation is) that takes care of registering your functions.
This allows you to configure the class name in your `.jactlrc` file and have the functions automatically available
in the REPL and in commandline scripts.

For example:
```groovy
class MyFunctions {
  public static registerFunctions(JactlEnv env) {
    Jactl.method(JactlType.ANY)
         .name("toJson")
         .impl(JsonFunctions.class, "toJson")
         .register();

   Jactl.method(JactlType.STRING)
        .name("fromJson")
        .impl(JsonFunctions.class, "fromJson")
        .register();
  }
  ...
}
```

See [here](command-line-scripts#.jactlrc-file) for more details.

### Async Parameters

Any function or method that suspends the current execution is known as asynchronous.
Asynchronous functions and methods are flagged as being asynchronous by declaring a `Continuation` argument as discussed.

Sometimes a function or method is only asynchronous because it invokes something on one of its arguments that 
is asynchronous and could suspend the current execution.
For example, a method that takes a closure/function as an argument and invokes it at some point has no way of knowing
it the closure passed to it will suspend or not and so it has to assume that it can be suspended and declare a
`Continuation` argument as discussed previously.

When the compiler detects that it is generating code to invoke a function that is potentially asynchronous it needs
to generate some code to capture the current execution state.
If the function or method is only asynchronous when one of its parameter values is an asynchronous function then
sometimes it can be invoked with a value that is asynchronous, and sometimes it might be invoked with a value that
isn't.
To prevent the compiler from having to generate unnecessary code for capturing execution state, the parameter can be
flagged as being "async" and the compiler won't generate this additional code if it knows that the argument being passed
in cannot perform any asynchronous operations.

Parameters like these should be declared with `asyncParam()` instead of `param()` when registering the method or function.
For example, if we had a function called `measure()` that invoked a passed in closure/function, and then measured how
long it took to complete, it would be registered like this:
```java
Jactl.function()
     .name("measure")
     .asyncParam("closure")     // only async when closure passed in is async
     .impl(MyFunctions.class, "measure")
     .register()
```

The implementation would look like this (closures/functions passed as arguments are always `JactlMethodHandle` objects):
```java
class MyFunctions {
  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    
  }
  public static Object measureData;
}
```

### Async Instance

For methods that act on `JacsalType.ITERATOR` objects, we allow the object to be one of the following types:
* List
* Map
* String &mdash; iterates of the characters of the string
* Number &mdash; iterates from `0` to `n-1` (i.e. `n` times)
* Iterator &mdash; the result of methods such as `map()`, `filter()` etc

If the object is an actual `Iterator` then it could be in a chain of method calls where asynchronous operations
are occurring.
For example:
```groovy
> [1,2,3].map{ sleep(1, it) + sleep(1, it) }.filter{ it != 2 }.each{ println it } 
```

In this example the object that `filter()` acts on is an `Iterator` and calling `next()` would result in the
closure passed to `map()` being invoked which will suspend since it invokes `sleep()`.
So even though `filter()` itself does not have any asynchronous arguments in this instance, it acts on an
`Iterator` object that is asynchronous in nature.
The `asyncInstance(true)` call when registering methods tells Jactl that a method is asynchronous when the object
on which it acts is asynchronous.

So the registration of the `filter()` method looks like this:
```groovy
Jactl.method(ITERATOR)
     .name("filter")
     .asyncInstance(true)
     .asyncParam("predicate", null)
     .impl(BuiltinFunctions.class, "iteratorFilter")
     .register();
```
This tells the compiler that the `filter()` call is only asynchronous if the object it acts on is asynchronous or
the argument passed to it (the predicate closure) is asynchronous.

> **Note**<br>
> As shown, `asyncParam()` calls are also allowed to specify a default value where appropriate.

### Handling Resumption

Our `sendReceiveJson()` function from before did not have to consider how to resume itself after suspending because
when the resumption occurred we already had the result so there was no more processing for the function to do.

We will now examine how our example `measure()` function might work to see how to handle functions that do need to
do more processing when they are resumed.

A naive implementation of `measure()` might look like this:
```groovy
class MyFunctions {
  public static void registerFunctions(JacsalEnv env) {
    Jactl.function()
         .name("measure")
         .param("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }
 
  public static long measure(String source, int offset, JactlMethodHandle closure) {
    long start = System.nanoTime();
    RuntimeUtils.invoke(closure, source, offset);
    return System.nanoTime() - start;
  }
  public static Object measureData;
}
```

We use the `io.vertx.runtime.RuntimeUtils.invoke()` helper method to invoke the closure.
It gets passed the JactlMethodHandle, the source and offset, and then a varargs set of any arguments that
the closure/function expects.
In this case we are assuming a zero-arg closure/function was passed to us so there are no arguments that need to
be passed in.

Once we have configured our `.jactlrc` file (see [here](command-line-scripts#.jactlrc-file) for details)
we can include our new function when running the Jactl REPL:
```groovy
$ java -jar jactl-repl-{{ site.content.jactl_version }}.jar
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@1846982837
> measure{ fib(40) }
184077675
```

As you can see, it returns the number of nanoseconds it took to invoke `fib(40)` which equated to around 184ms.

Since the closure being invoked by `measure()` could suspend and wait for some asynchronous operation, `measure()`
needs to gain control once it completes in order to work out how long it all took.
In order to do that, we need to:
1. catch the `Continuation` thrown when the closure suspends itself,
2. create a new `Continuation` chained to the one it caught,
3. pass in a `JactlMethodHandle` to the new `Continuation` that points to a method that will be invoked when we are resumed,
4. capture any state we need in this new `Continuation` object (including the values of the parameters originally 
passed in if needed), and
5. throw the new `Continuation`.

The constructor of a `Continuation` looks like this:
```java
public Continuation(Continuation      continuation,
                    JactlMethodHandle methodHandle,
                    int               codeLocation,
                    long[]            localPrimitives,
                    Object[]          localObjects)
```

The first parameter is the `Continuation` we just caught.
This allows the `Continuation` objects to be chained together.

The second parameter is a `JactlMethodHandle` that will be invoked when we are resumed.
It needs to point to a continuation method that takes a single `Continuation` argument.
When resumed, it will be passed the `Continuation` we are constructing and throwing here.

The third parameter is an `int` called `location` which represents a logical location in our method where we
were suspended.
It allows us to record where in the method we were when we were suspended in case there are multiple locations
where this can occur.
We can pass in any value as long as we understand the relationship between the different values and the actual
locations in the code.

The last two parameters are used to capture the current state.
One is an array of `long` values, which we can use for storing any primitive values we need, and 
the other one is an array of `Object` values, where we store any non-primitive values we need.

In our case, the only state we need to capture is the start time which is a `long` so our code to catch
a `Continuation` and then throw a new chained one would look like this:
```java
  try {
    ...
  }
  catch(Continuation cont) {
    throw new Continuation(cont, measureResumeHandle, 0, new long[]{ start }, new Object[0]);
  }
```

We just pass location as a value of 0 for the moment since there is only one place where we can be suspended.

We need a continuation method that can be invoked when we are resumed so that we can create the `JactlMethodHandle`
called `measureResumeHandle` that points to it and pass this handle to the `Continuation` on construction.
The continuation method must take only a `Continuation` as an argument and must return `Object`.

In our case we want the resumption method to return the duration.
In order to do that it needs to extract the value for the start time from our saved state in the `Continuation`.
The `Continuation` has a field called `localPrimitives` for the `long` array and a field called `localObject`
for the `Object` array that we passed in when we created it.

This means that our resumption method can look like this:
```java
  public static Object measureResume(Continuation c) {
    long start = c.localPrimitives[0];
    return System.nanoTime() - start;
  }
```

To get a `JactlMethodHandle` we can use the utility method `RuntimeUtils.lookupMethod()`.
This utility method takes a `Class`, the name of the static method, the return type, and then a varargs
list of argument types.

Rather than invoke this every time it is better to do it once and store it in a static field for when we need it:
```java
  private static JactlMethodHandle methodResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class, 
                                                                                  "measureResume",
                                                                                  Object.class,
                                                                                  Continuation.class);
```

We now need to make sure to tell Jactl that our function has an argument that makes us async if it is async
by changing `param()` to `asyncParam()`:
```java
   Jactl.function()
        .name("measure")
        .asyncParam("closure")
        .impl(MyFunctions.class, "measure")
        .register();
```

We also need to add a `Continuation` parameter to our function since it is now potentially async:
```java
  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    ...
  }
```

Putting this all together, our class now looks like this:
```java
class MyFunctions {
  public static void registerFunctions(JacsalEnv env) {
    Jactl.function()
         .name("measure")
         .asyncParam("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }

  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    long start = System.nanoTime();
    try {
      RuntimeUtils.invoke(closure, source, offset);
      return System.nanoTime() - start;
    }
    catch(Continuation cont) {
     throw new Continuation(cont, measureResumeHandle, 0, new long[]{ start }, new Object[0]);
    }
  }
  public static Object measureData;

  public static Object measureResume(Continuation c) {
    long start = c.localPrimitives[0];
    return System.nanoTime() - start;
  }

  private static JactlMethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
                                                                                   "measureResume",
                                                                                   Object.class,
                                                                                   Continuation.class);
}
```

Now we can measure how many nanoseconds it takes (wall clock time) for a closure to finish even if it does asynchronous
operations:
```groovy
> measure{ sleep(1000) }
1001947542
```

To illustrate a slightly more complicated scenario, imagine that we actually want to run the code we are measuring
multiple times and return the average.
To avoid having to duplicate code, our resume method should re-invoke our original method.
The original method will then check if the `Continuation` argument is null or not to know whether it is the
original call or a resumption of a previous call.

In order for the resume method to invoke the original method, it will need to be able to pass in values for
`source`, `offset`, and `closure`, so we will need to store these as part of our state when throwing a
`Continuation`, and we will use the `location` parameter to record which iteration we are up to.

Now our code looks like this:
```java
class MyFunctions {
  public static void registerFunctions(JacsalEnv env) {
    Jactl.function()
         .name("measure")
         .param("count", 1)
         .asyncParam("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }

  public static long measure(Continuation c, String source, int offset, int count, JactlMethodHandle closure) {
    long start = c == null ? System.nanoTime() : c.localPrimitives[2];
    int  i     = c == null ? 0                 : c.methodLocation;
    try {
      for (; i < count; i++) {
        RuntimeUtils.invoke(closure, source, offset);
      }
      return (System.nanoTime() - start) / count;
    }
    catch(Continuation cont) {
      throw new Continuation(cont, measureResumeHandle,
                             i + 1,                      // location is next loop counter value
                             new long[]  { offset, count, start },
                             new Object[]{ source, closure });
    }
  }
  public static Object measureData;

  public static Object measureResume(Continuation c) {
    var source  = (String)c.localObjects[0];
    var offset  = (int)c.localPrimitives[0];
    var count   = (int)c.localPrimitives[1];
    var closure = (JactlMethodHandle)c.localObjects[1];
    return measure(c, source, offset, count, closure);
  }

  private static JactlMethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
                                                                                   "measureResume",
                                                                                   Object.class,
                                                                                   Continuation.class);
}
```

Now we invoke our `measure()` function with a count and a closure:
```groovy
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@1998137093
> measure(10){ fib(40) }
184077675
```

It will also do the right thing if we do some asynchronous operation (like a `sleep()`) inside the closure:
```groovy
> measure(10){ sleep(100); fib(40) }
284705195
```

Of course, it is much easier to write this in Jactl itself since the language already takes care of suspending
and resuming for you:
```groovy
> def measure(n, closure) {
    def start = nanoTime()
    n.each{ closure() }
    (nanoTime() - start) / n
  }
Function@1230013344
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@727860268
> measure(10){ sleep(100); fib(40) }
284375954
```

## Example Application

In the `jacsal-vertx` project, an example application is provided that listens for JSON based web requests and
runs a Jactl script based on the URI present in the request.

See [Example Application](https://github.com/jaccomoc/jactl-vertx#example-application) for more details.
