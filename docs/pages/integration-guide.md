---
layout: page
title: "Integration Guide"
permalink: /integration-guide
---

This guide details how to integrate Jactl as a scripting language into a Java application and how to extend the language
with additional methods/functions.

## Dependency

To use Jactl you will need to add a dependency on the Jactl library.

### Gradle

In the `dependencies` section of your `build.gradle` file:
```groovy
implementation group: 'io.jactl', name: 'jactl', version: '1.0.0'
```

### Maven

In the `dependencies` section of your `pom.xml`:
```xml
<dependency>
 <groupId>io.jactl</groupId>
 <artifactId>jactl</artifactId>
 <version>1.0.0</version>
</dependency>
```

## Overview

### Jactl.eval()

The simplest way to run a Jactl script is to use the `io.jactl.Jactl` class and the `eval()` method:
```java
Object result = Jactl.eval("3 + 4");         // Will return 7
```

Since this has to compile the script each time, it is not the most efficient way to invoke Jactl scripts and 
due to the way it waits synchronously for a result even if the script does an asynchronous operation this call
is not generally suitable if running in an event-loop based application.
It is preferable to use `Jactl.compile()` as discussed below.

### Sharing Data with Scripts

To share data between the Java application and the script, it is possible to pass in a Map of global variables
for the script to have access to.
```java
var    globals = new HashMap<String,Object>();
globals.put("x", 3);
globals.put("y", 4);
Object result = Jactl.eval("x += y", globals);
int    xval   = (int)globals.get("x");         // xval will be 7 
```

Jactl supports the following object types as values for the global variables:
* Boolean
* Integer
* Long
* Double
* BigDecimal
* String
* List
* Map

For Maps, the keys must be Strings and for both Lists and Maps, the values in the List or Map should be one of the
types listed above.

It is also allowed for the value of a variable to be `null` as a way to create the variable when there is no
initial value that makes sense for it.

Objects which are instances of a Jactl class are also supported, so, if a previous script invocation has returned
such a value, then it is allowable to pass this same value to another script invocation:

```java
var globals = new HashMap<String,Object>();
globals.put("x", null);
Jactl.eval("class X { int x; def f(n) { x * n } }; x = new X(2)", globals);
int result = (int)Jactl.eval("x.f(3)", globals);         // result will be 6
```

### Jactl.compile()

The preferred way to run Jactl scripts is to compile them using `Jactl.compile()`.
This returns a `JactlScript` object which can then be run.

`JactlScript` objects can be run using `runSync()` or `run()`.

`runSync()` works like `eval()` in that it waits for the script to complete before returning the result to the caller.
Scripts that perform asynchronous operations will be suspended and then resumed on an event-loop thread.
If using this form, be aware of how it interacts with the threading model of your application since if you are already
on the event-loop thread that the script needs to be resumed on you will block forever.

```java
var globals = new HashMap<String,Object>();
JactlScript script = Jactl.compileScript("3 + 4", globals);
Object result = script.runSync(globals);          // result will be 7
```

Again, you can use the Map of globals as a way of sharing data between the script and the application.
The globals you pass in at compile time should contain all the global variables that the script will refer to.
The values in the Map can all be `null` at this point since the compiler just uses the Map to decide if a variable
exists or not.

The globals Map passed into the `runSync()` call (and the `run()` call) will be then be the one that the script
uses at runtime.
This can be a different Map each time but should, obviously, contain an entry for each variable passed in at compile
time.

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

In general, you will need to create an object of type `io.jactl.JactlContext` to use when compiling scripts.
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

Object result = Jactl.eval("13 * 17", globals, context);                   // returns value of 221
```

### javaPackage(String pkg)

Jactl scripts are compiled into Java classes.
They have a Jactl package which could be `''` if unspecified or something like `x.y.x` if a package declaration
exists at the top of the script.

In order not to clash with Java packages, the Jactl package hierarchy is placed inside a default Java package of
`io.jactl.pkg`.
Therefore, scripts without a Jactl package will end up in the package `io.jactl.pkg` and with a package declaration
like `x.y.x` the package will be `io.jactl.pkg.x.y.z`.

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
If no environment is specified, Jactl defaults to using an environment of type `io.jactl.DefaultEnv` which uses
`java.util.concurrent.ExecutorService` based thread pools for the event-loop threads and blocking threads.

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

Then to use the class, compile and script that refers to it using the same context:
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
> Global variables are only accessible by Jactl scripts.

If the class has a package declaration and the script is in a different package, then the script should either
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

When compiling a class you can specify also specify the package name for the class explicitly:
```java
JactlContext context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context, "a.b.c");
```

The package name can be:

| Value             | Description                                                                                                    |
|:------------------|:---------------------------------------------------------------------------------------------------------------|
| `null`            | Error if package not specified in class declaration.                                                           |
| `""`              | If package name not specified in class declaration then it will default to `""` and be placed in root package. |
 | any other value   | If not specified then default to this value. If specified it must match this value or an error will occur.     | 

There is also a similar `Jactl.compileScript()` method where you can pass in the package name for the script as well:
```java
var context = JactlContext.create().build();
var globals = new HashMap<String,Object>();
var pkgName = "a.b.c";
var script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context, pkgName);
```

## Source Location for Jactl Scripts and Classes 

In the examples we have shown we have always used constant strings as the source code for our Jactl scripts and classes
but this is unlikely to be the case in a proper application.
In general, the scripts and classes will be read from files or from the database since the idea is that the scripts
provide a way to customise the behaviour of an application.

See a later section where we show an example application that reads Jactl scripts on demand from the file system
in order to respond to incoming web service requests.

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

If your environment class as a no-arg constructor, and you want to be able to run your extension functions and methods
(see below) in the [Jactl REPL](https://github.com/jaccomoc/jactl-repl) or in [commandline](command-line) scripts,
then you can configure your `.jactlrc` file with the name of your environment class.

See [`.jactlrc` File](command-line-scripts#.jactlrc-file) for more details.

## Adding New Functions/Methods

Since the idea of integrating Jactl into an application is to provide a way to customise behaviour, it is likely that
the application would want to offer some additional global functions and methods that the Jactl scripts and classes
can use.

As an example, the [`jactl-vertx`](https://github.com/jaccomoc/jactl-vertx) project offers a new `toJson()` method
that converts a Jactl object to a JSON string and a 'fromJson()' method that converts from a JSON string back into
a Jactl object.
It also has an example global function for sending/receiving JSON messages over HTTP called `sendReceiveJson()`.

We want to be able to perform operations like this:
```groovy
> [name:'Fred Smith', userId:'fred'].toJson()
{"name":"Fred Smith","userId":"fred"}
> '{"name":"Fred Smith","userId":"fred"}'.fromJson()
[name:'Fred Smith', userId:'fred']
> sendReceiveJson(url:'http://localhost:52178/wordCount', request:[text:'here are some more words to be counted'])
[response:8, statusCode:200]
```

To register the new functions/methods with the Jactl runtime we use `Jactl.method()` and `Jactl.function()` with
fluent-style methods to build the method/function and then register it.

For example to register the `toJson()` method the following should be invoked sometime during application
initialisation (before any Jactl scripts/classes are compiled):
```java
Jactl.method(JactlType.ANY)
     .name("toJson")
     .impl(JsonFunctions.class, "toJson")
     .register();
```

The `Jactl.method()` method takes an argument being the type of object on which the new method should exist.
For `toJson()` we want all objects to have this method, so we register the type using `JactlType.ANY`.

For the `fromJson()` method, we want this to only exist for strings, so we use `JactlType.STRING`:
```java
Jactl.method(JactlType.STRING)
     .name("fromJson")
     .impl(JsonFunctions.class, "fromJson")
     .register();
```

The types that can be used here are:

| Type               | Description                                        |
|:-------------------|:---------------------------------------------------|
| JactlType.ANY      | Any object                                         |
| JactlType.STRING   | Strings                                            |
| JactlType.LIST     | Lists                                              |
| JactlType.MAP      | Maps                                               |
| JactlType.ITERATOR | Anything that is iterable (List,Map,String,Number) |
| JactlType.BOOLEAN  | boolean objects                                    |
| JactlType.INT      | int objects                                        |
| JactlType.LONG     | long objects                                       |
| JactlType.DOUBLE   | double objects                                     |
| JactlType.DECIMAL  | BigDecimal objects                                 |
| JactlType.NUMBER   | All numeric types                                  |

> **Note**<br/>
> In Jactl, unlike Java, primitive types can have methods defined for them.

For global functions, there is no type that owns the function so we use `Jactl.function()` instead:
```java
Jactl.function()
     .name("sendReceiveJson")
     .param("url")
     .param("request")
     .impl(VertxFunctions.class, "sendReceiveJson")
     .register();
```

For both methods and global functions, the `name()` method must be specified as shown in the examples.

The other mandatory method is `impl()` which tells Jactl which class and the name of the static method to invoke
for the implementation of the method/function.
In addition, if a third argument is passed to `impl()` it is the name of a public static field of type Object
in the implementing class that the Jactl runtime can use to cache some data needed for the function/method.
If no third argument is present it will default the name to the static method name appended with `Data`.

So this:
```java
Jactl.method(JactlType.ANY)
     .name("toJson")
     .impl(JsonFunctions.class, "toJson")
     .register();
```

is the same as this:
```java
Jactl.method(JactlType.ANY)
     .name("toJson")
     .impl(JsonFunctions.class, "toJson", "toJsonData")
     .register();
```

> **Note**<br/>
> Each function/method being registered needs to provide its own public static field of type Object.
> These fields cannot be shared between functions.

If there are no parameters to the function/method then this is all that is needed before invoking the
`register()` method to register the function.

For something like `toJson()` which has no parameters, the implementation can be as simple as this:
```java
class JsonFunctions {
  public static String toJson(Object obj) {
    return Json.encode(obj);
  }
  public static Object toJsonData; 
}
```

Since this is a method the first parameter of the implementing static function will be the object to which the
method "belongs".
In this case the first parameter will be of type `Object` since we have registered the method for all object types
by using `JactlType.ANY` as the argument to `method()`.

Since it is possible for `Json.encode()` to throw an exception (of type `EncodeException`) it is good
practice to catch any such exception and wrap it in a `io.jactl.runtime.RuntimeError` exception.
To create a `RuntimeError` you need to provide the source code and offset into the code where the error
occurred.
To get these passed to us, we just declare a `String` and `int` parameter and Jactl will take care of
populating these parameters with the values from where in the code the function was invoked:
```java
class JsonFunctions {
  public static String toJson(Object obj, String source, int offset) {
    try {
      return Json.encode(obj);
    }
    catch (EncodeException e) {
      throw new RuntimeError("Error encoding to Json", source, offset, e);
    }
  }
  public static Object toJsonData; 
}
```

Since these are not parameters to the Jactl method, they don't get declared in our `Jactl.method()` registration.
Jactl will automatically detect that the implementation method expects a `String` and `int` and will take care
of the rest.

If the method did have its own parameters then these should be declared after the `String source, int offset`
parameters.

For example, if we consider the `sendReceiveJson()` global function, it will need to be able to handle errors
that can occur when sending HTTP requests, so it needs the `String source` and `int offset` parameters.
It has its own parameters `url` and `request` for the URL to send to and the content to send.
Its implementation, therefore, could look something like this:
```java
public class VertxFunctions {
  private static WebClient webClient;
  
  public static Map sendReceiveJson(String source, int offset, String url, Object request) {
    try {
      // send the request and wait for the response
    }
    catch (Exception e) {
      throw new RuntimeError("Error invoking " + url, source, offset, e);
     }
  }
  public static Object sendReceiveJsonData;
}
```

> **Note**<br/>
> The names that you register the parameters with using `param("...")` do not have to match the names in the
> Java static method but do have to be declared in the right order.
> The names will be used to validate any calls that are done using named arguments.

### Default Values

When registering the parameter names you can also specify a default value for parameters that are optional.
For example, the builtin `String.asNum(base)` method parses a String that could be in any base (up to 36).
By default, it assumes base 10 but can be used to parse hex strings or binary strings etc.:
```java
Jactl.method(STRING)
     .name("asNum")
     .param("base", 10)
     .impl(BuiltinFunctions.class, "stringAsNum")
     .register();
```

The `param("base", 10)` means that if no parameter is supplied it will be automatically filled in with `10`.

### Suspending Execution

The problem is that we don't want our `sendReceiveJson()` function to actually wait for the response.
The response may take a long time, or we might time out waiting, and we don't want to block the event-loop thread
for that entire time.
We want the function to suspend execution of the current script until the result is returned.

Any function that is going to suspend the current execution needs to accept a `Continuation` object as its first
argument to allow the function to be able to be resumed/continued once the asynchronous operation has finished.
In this case our function has nothing more to do once the result is received, so it can ignore the `Continuation`
object even though it still needs to declare it:
```java
  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    ...
  }
```

For methods that suspend the current execution while something occurs in the background, the `Continuation` argument
is the second argument (so occurs after the argument which represents the owning object of the method call).
So if our `fromJson()` JSON decode function needed to suspend for some reason the static method declaration would look
like this:
```java
  public static Object fromJson(String json, Continuation c, String source, int offset) {
    ...
  }
```

In order for our `sendReceiveJson()` function to suspend our execution state there are two static methods provided
on the `Continuation` class:
1. `Continuation.suspendBlocking()`, and
2. `Continuation.suspendNonBlocking()`.

The `suspendBlocking()` call is for use where the work to be done is going to run on a blocking thread and once
finished it will return a result and this will be used to continue the script from where we left off.

So, to use this approach in our function we would implement it like this:
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
        return new RuntimeError("Error invoking " + url, source, offset, e);
      }
    });
  }
```

> **Note**<br/>
> Any errors that occur on the blocking thread should be returned as `RuntimeError` objects as shown.

Such an approach should be used when the work being done requires using a blocking API.
An interface to an SQL database might use this approach, for example, if the only API provided is a synchronous one.

In our case, however, since we are using Vert.x to do the send and receive, and Vert.x is by nature an asynchronous
library, we don't need to tie up an entire blocking thread while we wait for the response.

Instead, we use the `Continuation.suspendNonBlocking()` and pass it a handler that will initiate the request and 
register its own handler with Vert.x so that will be invoked when the response is received.
**It is important that the initiation of the async request be done inside the handler passed to 
`Continuation.suspendNonBlocking()` and not before in order to guard against race conditions where the response might
be received before we have finished suspending our execution.**

The handler that we pass to `suspendNonBlocking()` accepts two arguments:
1. a `JactlContext` in case we need it, and
2. a resume handler that will be invoked when we have a result and which will take care of resuming the execution.

In our example we don't need the `JactlContext` so we just need to make sure that when the handler we register with
Vert.x is invoked (i.e. when the response is received) we then invoke the resume handler we get passed in to us.

Our implementation of all this could look like this:
```java
  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    Continuation.suspendNonBlocking((context, resumer) -> {
      try {
        webClient.postAbs(url)
                 .sendJson(request)
                 .onSuccess(response -> {
                   // Our Vert.x response handler
                   resumer.accept(getResult(response));   // continue our script with result
                 })
      }
      catch (Exception e) {
        resumer.accept(new RuntimeError("Error invoking " + url, source, offset, e));
      }
    });
    return null;       // never happens since function is async
  }
```

Some details (such as better error handling) have been left out for brevity.
To see the full implementation see the [`VertxFunctions` example class](https://github.com/jaccomoc/jactl-vertx/blob/main/src/test/java/io/jactl/vertx/example/VertxFunctions.java).

### Integration with REPL and CommandLine Scripts

For ease of integration with the Jactl REPL and Jactl commandline scripts, it is recommended that you have a
public static function called `registerFunctions(JactlEnv env)` in one of your classes (generally the same one
where the implementation it) that takes care of registering your functions.
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
If the function or method is only asynchronous if one of its parameter values is an asynchronous function then
sometimes it can be invoked with a value that is asynchronous, and sometimes it might be invoked with a value that
isn't.
To prevent the compiler from having to generate unnecessary code for capturing execution state the parameter can be
flagged as being "async" and the compiler won't generate this additional code if it knows the argument cannot perform
any asynchronous operations.

For parameters like these, they should be declared with `asyncParam()` instead of `param()` when registering the method
or function.
For example, if we had a function called `measure()` that invoked a passed in closure/function, measured how
long it took to complete, it would be registered like this:
```java
Jactl.function()
     .name("measure")
     .asyncParam("closure")
     .impl(MyFunctions.class, "measure")
     .register()
```

The implementation would look like this (closures/functions passed as arguments are always `MethodHandle` objects):
```java
class MyFunctions {
  public static long measure(Continuation c, String source, int offset, MethodHandle closure) {
    
  }
  public static Object measureData;
}
```

For methods that act on `JacsalType.ITERATOR` objects we allow the object to be one of the following types:
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

To allow for the compiler to generate state preserving code only when needed the `filter()` method is registered
with an async parameter for its predicate parameter (since this could do a `sleep()` or equivalent) and with
`asyncInstance(true)` set.
This tells the compiler that the `filter()` call is only asynchronous if the object it acts on is asynchronous or
the argument passed to it is:
```groovy
Jactl.method(ITERATOR)
     .name("filter")
     .asyncInstance(true)
     .asyncParam("predicate", null)
     .impl(BuiltinFunctions.class, "iteratorFilter")
     .register();
```

Note how `asyncParam()` calls are also allowed to specify a default value where appropriate.

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
 
  public static long measure(String source, int offset, MethodHandle closure) {
    long start = System.nanoTime();
    RuntimeUtils.invoke(closure, source, offset);
    return System.nanoTime() - start;
  }
  public static Object measureData;
}
```

We use the `io.vertx.runtime.RuntimeUtils.invoke()` helper method to invoke the closure.
It gets passed the MethodHandle, the source and offset, and then a varargs set of any arguments that
the closure/function expects.
In this case we are assuming a zero-arg closure/function was passed to us so there are no arguments that need to
be passed in.

Once we have configured our `.jactlrc` file (see [here](command-line-scripts#.jactlrc-file) for details)
we can include our new function when running the Jactl REPL:
```groovy
$ java -jar jactl-repl-1.0.0.jar
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@1846982837
> measure{ fib(40) }
184077675
```

As you can see, it returns the number of nanoseconds it took to invoke `fib(20)`.

Since the closure being invoked by `measure()` could suspend and wait for some asynchronous operation, `measure()`
needs to gain control once it completes in order to work out how long it all took.
In order to do that, we need to:
* catch the `Continuation` thrown when the closure suspends itself,
* create a new `Continuation` chained to the one it caught,
* pass in a MethodHandle to the new `Continuation` that points to a method that will be invoked when we are resumed,
* capture any state we need in this new `Continuation` object, and
* throw the new `Continuation`.

The constructor of a `Continuation` looks like this:
```java
public Continuation(Continuation continuation,
                    MethodHandle methodHandle,
                    int codeLocation,
                    long[] localPrimitives,
                    Object[] localObjects)
```

The first parameter is the `Continuation` we just caught.
This allows the `Continuation` objects to be chained together.

The second parameter is a `MethodHandle` that will be invoked when we are resumed.
It needs to point to a method that takes a single `Continuation` argument and when resumed it will be passed
the `Continuation` we are constructing and throwing here.

The third parameter is an `int` called `location` which represents a logical location in our method when we
were suspended.
It allows us to record where in the method we were when we were suspended in case there are multiple locations
where this can occur.

The last two parameters are used to capture state.
One is an array of `long` values, which we can use for storing any primitive values we need, and 
the one is an array of `Object` values, where we store any non-primitive values we need.

In our case, the only state we need to capture is the start time which is a `long` so our code to catch
a `Continuation` and then throw a new chained one would look like this:
```java
  catch(Continuation cont) {
    throw new Continuation(cont, measureResumeHandle, 0, new long[]{ start }, new Object[0]);
  }
```

We just pass location as a value of 0 for the moment since there is only one place where we can be suspended.

We need a method that can be invoked when we are resumed so that we can create the `MethodHandle`
called `measureResumeHandle` to pass to the `Continuation`.
This method must take only a `Continuation` as an argument and must return `Object`.

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

To get a `MethodHandle` we can use the utility method `RuntimeUtils.lookupMethod()`.
This utility method takes a `Class`, the name of the static method, the return type, and then a varargs
list of argument types.

Rather than invoke this every time it is better to do it once and store it in a static field for when we need it:
```java
  private static MethodHandle methodResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class, 
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
  public static long measure(Continuation c, String source, int offset, MethodHandle closure) {
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

  public static long measure(Continuation c, String source, int offset, MethodHandle closure) {
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

  private static MethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
                                                                              "measureResume",
                                                                              Object.class,
                                                                              Continuation.class);
}
```

Now we can measure how long it takes (wall clock time) for closure to finish even if they do asynchronous
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

  public static long measure(Continuation c, String source, int offset, int count, MethodHandle closure) {
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
    var closure = (MethodHandle)c.localObjects[1];
    return measure(c, source, offset, count, closure);
  }

  private static MethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
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
> measure(10){ sleep(1); fib(40) }
185705195
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
> measure(10){ sleep(1); fib(40) }
185375954
```

## Example Application

In the `jacsal-vertx` project, an example application is provided that listens for JSON based web requests and
runs a Jactl script based on the URI present in the request.

See [Example Application](https://github.com/jaccomoc/jactl-vertx#example-application) for more details.
