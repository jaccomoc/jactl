---
title: JactlContext Object
description: "Configuring the JactlContext object for controlling script compilation and execution."
---

Although most of the examples shown so far haven't used a JactlContext, in general, you will need to create an object
of type `io.jactl.JactlContext` when evaluating or compiling scripts.
The `JactlContext` allows you to set some options (including the execution environment - see below) and provides
a way to compile sets of related Jactl scripts and Jactl classes.
Jactl classes can only be referenced from scripts and other classes that have been compiled using the same `JactlContext`.

Each `JactlContext` represents a secure sandbox that prevents scrips and classes within that sandbox from being
able to access anything that has not been defined with that `JactlContext` object. 
This provides a level of separation if you want to be able to run a multi-tenant application where
each tenant has their own set of scripts and classes, for example.

An application can also decide to expose different sets of functions/methods and different
sets of additional built-in types to each `JactlContext`.

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

Map<String,Object> globals = new HashMap<>();
JactlScript script = Jactl.compileScript("13 * 17", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));    // Output will be: "Result is 221"

Object result = Jactl.eval("13 * 17", globals, context);
assertEquals(221, result);
```

## javaPackage(String pkg)

Jactl scripts are compiled into Java classes.
They have a Jactl package which could be `''` if unspecified or something like `x.y.z` if a package declaration
exists at the top of the script.

In order not to clash with Java packages, the Jactl package hierarchy is placed inside a default Java package of
`jactl.pkg`.
Therefore, scripts without a Jactl package will end up in the package `jactl.pkg` and with a package declaration
like `x.y.z` the package will be `jactl.pkg.x.y.z`.

If you would like to change what Java package the Jactl scripts and classes should reside under you can use the
`javaPackage()` method when building your `JactlContext`:
```java
JactlContext context = JactlContext.create()
                                   .javaPackage("jactl.pkg.abc")
                                   .build();
```

> **Note**<br/>
> If you are trying to keep different sets of Jactl scripts and classes separate from other sets of scripts and
> classes then it makes sense to put them in different package hierarchies to avoid any name clashes.

## minScale(int scale)

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

## environment(JactlEnv env)

The `JactlContext` is also used to configure what runtime execution environment you are using.
If no environment is specified, Jactl defaults to using an environment of type `io.jactl.DefaultEnv` which uses two
`java.util.concurrent.ExecutorService` based thread pools: one for the event-loop threads and another one for the
blocking threads.

If you are running in a different type of event-loop based application then you should create a `JactlEnv` class
appropriate for your environment (see [Jactl Execution Environment](jactl-execution-environment) for more details),
or use one provided (for example the `io.jactl.vertx.JactlVertxEnv` environment class from the `jactl-vertx` library).

Here is an example that specifies a `JactlVertxEnv` object as the execution environment:

```java
Vertx vertx          = Vertx.vertx();
JactlVertxEnv env    = new JactlVertxEnv(vertx);

JactlContext context = JactlContext.create()
                                   .environment(env)
                                   .build();
```

## allowHostAccess(boolean allowed)

This method, in combination with the `allowHostClassLookup()` method, allows you to permit scripts to perform
operations on host classes (i.e. classes from the Java application in which Jactl is embedded).
If this method sets this flag to `true` then when an unknown class is encountered, Jactl will pass the
name of the class to the predicate configured with `allowHostClassLookup()` and if the predicate returns true,
Jactl will permit method calls on instances of that class and permit static method calls on that class.

:::warning
Use of these flags will weaken the sandbox security built-in to Jactl.
They should only be used with trusted host classes and trusted scripts.
:::

## allowHostClassLookup(Predicate&lt;String&gt; predicate)

If the `allowHostAccess()` flag is set, then setting a predicate using this method allows you to control
exactly which host classes are permitted to be accessed from Jactl scripts.
The predicate will be passed a Java class name (e.g. `a.b.c.SomeClass`) and the predicate should return `true`
if access is allowed or `false` otherwise.
Note that inner classes will be passed using Java syntax (e.g. `a.b.c.SomeClass$InnerClass`).

## allowHostClassLookup(boolean allowed)

You can pass `true` as the value for this method if you are running trusted scripts and want to allow access to
**all** host classes.

:::warning
This should be used only when you have complete trust in the scripts you are running.
:::

## debug(int level)

The `debug()` method allows you to enable debug output.
It outputs the generated compiled JVM instructions for use when troubleshooting.

The supported values are:
* `0` &mdash; no output (the default value)
* `1` &mdash; output instructions
* `2` &mdash; output instructions with additional information about code locations

## classAccessToGlobals(boolean accessAllowed)

This method controls whether access to the global variables is allowed from within a class
(methods and field initialisers).

By default, classes are not allowed to access globals and access will result in a compile-time error.
If for your application it makes sense for classes to have access to globals then you can invoke this method
with `true`.

## hasOwnFunctions(boolean value)

This controls whether the `JactlContext` object will have its own set of functions/methods registered with it.
By default, all `JactlContext` objects share the same functions/methods registered using `Jactl.function() ... .register()`
or `Jactl.method(type) ... .register()` (see section below on [Adding New Functions/Methods](adding-new-functions)).

If you would like to have different sets of functions/methods for different sets of scripts you can create different
`JactlContext` objects and register different sets of functions/methods with each object.

Note that whatever functions/methods have been registered at the time that the `JactlContext` is created will be
available to scripts compiled with that `JactlContext` so it makes sense to register all functions/methods that you
would like to be available to all scripts before creating any `JactlContext` objects.

See [Adding New Functions/Methods](adding-new-functions) for more details.

## hasOwnBuiltIns(boolean value)

This controls whether the `JactlContext` object will have its own set of built-in types registered with it.
By default, all `JactlContext` objects share the same set of built-ins registered using `Jactl.createClass() ... .register()`
(see section below on [Adding New Built-In Types](adding-new-builtins)).

If you would like to have different sets of built-in types for different sets of scripts you can create different
`JactlContext` objects and register different classes for the built-in types with each object.

Note that the classes that have already been registered at the time that the `JactlContext` is created will be
available to scripts compiled with that `JactlContext` so you should register all classes that you
would like all scripts to have access to before creating any `JactlContext` objects.

See [Adding New Built-In TYpes](adding-new-builtins) for more details.

## async(boolean enabled)

By default, Jactl support asynchronous functions that can block waiting on a long-running operation to complete
by suspending the script and then resuming it once the operation has completed.
In many situations this functionality is not needed.

For example, if running on a modern version of Java (Java 21 or later), Java now has VirtualThreads that implement
a similar mechanism to that of Jactl's Continuations and Jactl's suspend/resume may not be wanted.

Even if not running on Java 21 or later, users may not have any async functions that their scripts will invoke and
will want to avoid the small overhead that async scripts incur.

This `async(false)` method allows you to tell Jactl not to generate async handling code.

This will have a marginal improvement in compilation times and will allow more compact byte code to be
generated in some situations.

:::warning
This also means that checkpointing will be disabled since it relies on the same suspend/resume mechanism
that the async handling uses.
Any call to `checkpoint()` will invoke the `commit` closure every time without actually checkpointing the state.
The rationale for this is that this way you can measure the overhead of checkpointing by running a test with and
without async enabled and still have the scripts function normally.
:::

If async mode is disabled there is no need for a `JactlEnv` object to be supplied and the default environment will
not be created since everything runs on the thread on which the script is invoked and there is no need for events
to be scheduled on other threads.

## Disabling Some Types of Statements

For various reasons, applications may want to allow scripts but prevent them from invoking
certain statement types.
The following flags control whether scripts are allow to use the `eval()`, `print`, `println`
, and `die` statements.

### disableEval(boolean value)

Passing `true` to this will prevent the `eval()` statement being a legal statement.
A compile-time error will be generated if `eval()` is invoked.
This flag can be used by applications that want to prevent `eval()` usage in scripts.

### disablePrint(boolean value)

If an application wants to prevent scripts from being able to invoke `print` or `println`
they can pass `true` to this method.
If `print` or `println` are then invoked there will be a compile-time error.

### disableDie(boolean value)

The `disableDie()` method allows applications to disable the use of the `die` statement.
A call to `die` will then cause a compile-time error if this flag has been set.

## Chaining Method Calls

The methods for building a `JactlContext` can be chained in any order (apart from `create()` which must be first
and `build()` which must come last).
So to explicitly build a `JactlContext` with all the default values:
```java
JactlContext context = JactlContext.create()
                                   .javaPackage("jactl.pkg")
                                   .environment(new io.jactl.DefaultEnv())
                                   .minScale(10)
                                   .classAccessToGlobals(false)
                                   .hasOwnFunctions(false)
                                   .hasOwnBuiltIns(false)
                                   .disableEval(false)
                                   .disablePrint(false)
                                   .disableDie(false)
                                   .debug(0)
                                   .build();

// This is equivalent to:
JactlContext context = JactlContext.create().build();
```
