---
title: Compiling Classes
---

As well as compiling Jactl scripts, you may also need to compile Jactl classes.
As mentioned previously, Jactl classes, once compiled, can only be accessed directly by other Jactl scripts and
Jactl classes that use the same `JactlContext`.

## Jactl.compileClass()

To compile a class use one of the `Jactl.compileClass()` methods:
```java
JactlContext context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);
```

Then to use the class, compile and run a script that refers to it using the same context:
```java
JactlContext context = JactlContext.create().build();
Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);

Map<String,Object> globals = new HashMap<>();
JactlScript script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context);
script.run(globals, result -> System.out.println("Result: " + result));
```

## Packages

If the class has a Jactl package declaration and the script is in a different package, then the script should either
import the class from the other package or use a fully qualified class name:
```java
JactlContext context = JactlContext.create().build();
Jactl.compileClass("package a.b.c; class Multiplier { int n; def mult(x){ n * x } }", context);

Map<String,Object> globals = new HashMap<>();

// Import the class
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
JactlContext context = JactlContext.create().build();
Map<String,Object> globals = new HashMap<>();
String pkgName = "a.b.c";
JactlScript script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context, pkgName);
```
The only reason for passing an explicit Jactl package when compiling a script is to allow the script to access
classes in the same package without having to explicitly qualify them with a package name or to import them.

:::note
Like classes, scripts can specify a package themselves using the `package` directive and the rules about how package
names apply are the same as for classes (see table above).
:::

## Class Access to Globals

By default, classes are not able to access global variables, but you can configure the `JactlContext` object to allow
globals access from classes by using `classAccessToGlobals(true)` when building your `JactlContext`:
```java
JactlContext context = JactlContext.create()
                                   .classAccessToGlobals(true)
                                   .build();
```

Then, when compiling a class, use this form of `Jactl.compileClass()`:
```java
Jactl.compileClass(classSource, jactlContext, pkgName, globals);
```
