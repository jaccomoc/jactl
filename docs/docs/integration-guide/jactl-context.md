---
title: JactlContext Object
---

Although most of the examples shown so far haven't used a JactlContext, in general, you will need to create an object
of type `io.jactl.JactlContext` when evaluating or compiling scripts.
The `JactlContext` allows you to set some options (including the execution environment - see below) and provides
a way to compile sets of related Jactl scripts and Jactl classes.
Jactl classes can only be referenced from scripts and other classes that have been compiled using the same `JactlContext`.
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
By default, all `JactlContext` share the same functions/methods registered using `Jactl.function() ... .register()`
or `Jactl.method(type) ... .register()` (see section below on [Adding New Functions/Methods](adding-new-functions)).

If you would like to have different sets of functions/methods for different sets of scripts you can create different
`JactlContext` objects and register different sets of functions/methods with each object.

Note that whatever functions/methods have been registered at the time that the `JactlContext` is created will be
available to scripts compiled with that `JactlContext` so it makes sense to register all functions/methods that you
would like to be available to all scripts before creating any `JactlContext` objects.

See [Adding New Functions/Methods](adding-new-functions) for more details.

## Chaining Method Calls

The methods for building a `JactlContext` can be chained in any order (apart from `create()` which must be first
and `build()` which must come last).
So to explicitly build a `JactlContext` with all the default values:
```java
JactlContext context = JactlContext.create()
                                   .javaPackage("io.jactl.pkg")
                                   .environment(new io.jactl.DefaultEnv())
                                   .minScale(10)
                                   .classAccessToGlobals(false)
                                   .hasOwnFunctions(false)
                                   .debug(0)
                                   .build();

// This is equivalent to:
JactlContext context = JactlContext.create().build();
```
