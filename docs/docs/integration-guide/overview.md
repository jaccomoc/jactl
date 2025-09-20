---
title: Overview
---

## Jactl.eval()

The simplest way to run a Jactl script is to use the `io.jactl.Jactl` class and the `eval()` method:
```java
Object result = Jactl.eval("3 + 4");         // Will return 7
```

Since this has to compile the script each time, it is not the most efficient way to invoke Jactl scripts and,
due to the way it waits synchronously for a result even if the script does an asynchronous operation, this call
is not generally suitable if running in an event-loop based application.
(The preferred way to run Jactl code is to use `Jactl.compile()` to compile into a JactlScript object that can
then be invoked multiple times as discussed below).

## Sharing Data with Scripts

To share data between the Java application and the script, it is possible to pass in a Map of global variables
that the script can then access.
```java
Map<String,Object> globals = new HashMap<>();
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
JactlContext context = JactlContext.create().build();
Object x             = Jactl.eval("class X { int i; def f(n) { i * n } }; new X(2)", Utils.mapOf(), context);
Map<String,Object> globals = new HashMap<>();
globals.put("x", x);
Object result = (int)Jactl.eval("x.f(3)", globals, context);
assertEquals(6, result);
```

## Setting input/output for scripts

If the script uses the `nextLine()` function to read lines from some input then you can specify a `BufferedReader`
for the script's input.
Similarly, you can also supply a `PrintStream` object that will be where any output from `print` or `println`
is sent.

For example:

```java
Map<String,Object> globals = new HashMap<>();
globals.put("prefix", "DEBUG:");
BufferedReader input  = new BufferedReader(new InputStreamReader(System.in));
PrintStream    output = System.out;
Jactl.eval("stream(nextLine).each{ println \"$prefix: $it\\n\" }", globals, input, output);
```

## Jactl.compile()

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
Map<String,Object> globals = new HashMap<>();
JactlScript script = Jactl.compileScript("3 + 4", globals);
Object      result = script.runSync(globals);          // result will be 7
assertEquals(7, result);
```

Again, you can use the Map of globals as a way of sharing data between the script and the application.
The globals you pass in at compile time should contain all the global variables that the script will refer to.
The values in the Map can all be `null` at this point since the compiler just uses the Map to decide if a variable
exists or not.

The globals Map passed into `runSync()` (and `run()`) will be then be the one that the script
uses at runtime.
This can be a different Map each time but should, obviously, contain an entry for each variable passed in at compile
time or you will get a runtime error when the script tries to access a global variable not present in the map passed
in.

The `run()` method should be used in situations where you don't want to block the current thread (for example,
because you are already on an event-loop thread).
It takes two arguments:
1. the globals Map for global variables, and
2. a completion callback (of type `Consumer<Object>`) that will be passed the script result when the script completes.

:::note
If the script is entirely synchronous (doesn't use any asynchronous functions like `sleep()`) then the completion
will be invoked in the current thread before the call to `run()` returns to the caller.
:::

Here is an example:
```java
Map<String,Object> globals = new HashMap<>();
globals.put("x", null);
globals.put("y", null);
JactlScript script  = Jactl.compileScript("x + y", globals);

Map<String,Object> globalValues = new HashMap<>();
globalValues.put("x", 7);
globalValues.put("y", 3);

// Invoke run() with a completion callback
script.run(globalValues, result -> System.out.println("Result is " + result));
```

## Input/Output

Both `run()` and `runSync()` have overloaded verisons that also accept a `BufferedReader` and `PrintStream`
for the input/output of the script.

## Errors

If an error is detected at compile time then an exception of type `io.jactl.CompileError` will be thrown.

If a runtime error occurs then an exception of type `io.jactl.runtime.RuntimeError` will be thrown.

A special subclass of `RuntimeError` called `io.jactl.runtime.DieError` will be thrown if the script invokes the `die` statement.

All these exception classes are subclasses of the top level `io.jactl.JactlError`.

:::warning
All of these classes are unchecked exceptions so be sure to catch them at the appropriate place in your code.
:::

## Default Execution Environment and Shutdown

By default, if you have not provided an implementation of the `JactlEnv` interface
(see [Jactl Execution Environment](jactl-execution-environment) below) you will be using the
built-in `io.jactl.DefaultEnv`.

This class creates static thread pools for the non-blocking event loop threads, the blocking
threads, and a thread for timers.

Since these thread-pools are daemon threads, if you want to cleanly exit without invoking
`System.exit()`, you will need stop these thread-pools using the static `io.jactl.DefaultEnv.shutdown()`
method:

```java
class MyTest {
  public static void main(String[] args) {
    Object result = Jactl.eval("3 + 5");
    System.out.println("Result is " + result);
    io.jactl.DefaultEnv.shutdown();
  }
}
```
