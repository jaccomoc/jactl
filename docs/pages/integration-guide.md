# Integration Guide

This guide details how to integrate Jactl as a scripting language into a Java application and how to extend the language
with additional methods/functions.

## Dependency

To use Jactl you will need to add a dependency on the Jactl library.

### Gradle

In the `dependencies` section of your `build.gradle` file:
```groovy
implementation group: 'io.jactl', name: 'jactl', version: '1.0'
```

### Maven

In the `dependencies` section of your `pom.xml`:
```xml
<dependency>
 <groupId>io.jactl</groupId>
 <artifactId>jactl</artifactId>
 <version>1.0</version>
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

## Jactl Execution Environment

In order for Jactl to support event-loop based applications with non-blocking execution of scripts, it needs
to know how to schedule blocking and non-blocking operations.

In implementation of the `io.jactl.JactlEnv` interface must be provided that supplies a bridge from Jactl to
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

This method is used to add executiong of some blocking code to a queue for execution by a blocking thread once
one becomes available.

