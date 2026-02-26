---
title: Adding New Built-In Types
---

Jactl provides a way to add new built-in types that can then be used by the Jactl scripts/classes.
Each type corresponds to a Java class that is registered with the Jactl runtime.

A built-in type needs to be configured with a Jactl classname (and package) as well as the Java class
that provides the implementation and a list of methods from the Java class to expose in the Jactl
class.

Here is a very simple `Point` class that we will add as a new built-in type for Jactl scripts:
```java
package app.jactl;

public class Point {
  public double x, y;
  public static Point of(double x, double y) {
    Point p = new Point();
    p.x = x;
    p.y = y;
    return p;
  }
  public double distanceTo(Point other) {
    return Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y));
  }
}
```
To register this class as a new built-in type:
```java
JactlType pointType = Jactl.createClass("jactl.draw.Point")
                           .javaClass(app.jactl.Point.class)
                           .autoImport(true)
                           .method("of", "of", "x", double.class, "y", double.class)
                           .method("distanceTo", "distanceTo", "other", Point.class)
                           .register();
```

Now we can use this class in Jactl scripts:
```groovy
Point p = Point.of(1,2)
p.distanceTo(Point.of(3,4))  // result: 2.8284271247461903
```

## createClass() and register()

The `createClass()` method creates a new built-in type with the given fully qualified Jactl class name.
You can then use fluent style method invocation to configure the new type before finally invoking `register()`
to register it with the Jactl runtime.

All of these methods return the newly created `JactlType` that corresponds to this new built-in type.

## javaClass()

The call to `javaClass()` passes the `Class` the provides the implementation for the new built-in type.

## autoImport()

The `autoImport(true)` means that the class becomes automatically imported into all Jactl scripts/classes.
If set to false, or not specified, the new built-in type would need to be imported or fully qualified to be
used within a script.
For example, if the `Point` class did not have `autoImport(true)`:
```groovy
import jactl.draw.Point

Point p = Point.of(1,2)
p.distanceTo(Point.of(3,4))  // result: 2.8284271247461903
```

## method() and methodCanThrow()

Each method that you want to be able to be invoked from Jactl scripts needs to be declared using `.method()` or
`.methodCanThrow()`.
Both of these take as arguments the Jactl name, the Java name, and a list of parameter name and types.

Specifying a Jactl name as well as a Java name allows you to rename the method for Jactl scripts if desired.
Since Jactl does not allow overloaded methods, when there are multiple methods with the same Java name that 
you want to expose in Jactl, this allows you to give them different Jactl names.

The list of parameter names and types is a list of pairs of `String` and class type.
The name is used to support method invocation with named arguments and does not have to match the name of the
corresponding Java parameter.
The type is the `Class` that corresponds to the Java parameter type.

### Exception Handling

If the Java method declares that it throws an exception, Jactl will detect this and make sure that if at
runtime the method does throw an exception it is caught and converted into a Jactl `RuntimeError` that also
captures the call site at which the method was invoked to provide standard Jactl error handling and reporting.

If the Java method can throw unchecked exceptions, then Jactl has no way of detecting this from the method signature.
For these methods you should use `methodCanThrow()` instead of `method()` when exposing the method to Jactl
so that the appropriate error handling is generated.

### Adding Additional Methods

After registering the new built-in type you can still use the mechanism discussed in
[Adding New Functions/Methods](adding-new-functions) to add new instance and static methods
that will be available in Jactl.
For example, to add a static `midPoint()` method to the `Point` class:
```java
public class PointBuiltIn {
  public static void registerType() throws NoSuchMethodException {
    JactlType pointType = Jactl.createClass("jactl.draw.Point")
                               .javaClass(app.jactl.Point.class)
                               .autoImport(true)
                               .method("of", "of", "x", double.class, "y", double.class)
                               .method("distanceTo", "distanceTo", "other", Point.class)
                               .register();

    // Add static method for determining mid-point
    Jactl.method(pointType)
         .name("midPoint")
         .isStatic(true)
         .param("p1")
         .param("p2")
         .impl(PointBuiltIn.class, "midPoint")
         .register();
  }
  
  public static Point midPoint(Point p1, Point p2) {
    return Point.of((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
  }
}
```
Then, in Jactl:
```groovy
Point p1 = Point.of(1,2)
Point p2 = Point.of(3,4)
Point.midPoint(p1,p2)     // result: Point.of(2,3)
```
The ability to add methods like this is particularly useful if you are exposing a Java class that you
cannot modify.

## baseClass()

If you are adding a new built-in type that extends a base class that should also be exposed in Jactl then you
can use `baseClass()` to specify the Jactl class that is the base class for this type.
The Jactl class for the base class must already be registered with the Jactl runtime.

For example, a `Circle` class might have `Shape` as its base class:
```java
JactlType shape = Jactl.createClass("jactl.draw.Shape")
                       .javaClass(app.jactl.Shape.class)
                       .autoImport(true)
                       .method("area", "area")
                       .register();

JactlType circle = Jactl.createClass("jactl.draw.Circle")
                        .javaClass(app.jactl.Circle.class)
                        .baseClass("jactl.draw.Shape")
                        .method("area", "area")
                        .method("of", "of", "p", Point.class, "radius", double.class)
                        .register();
```

## mapType()

In situations where the Java code references a base class, for example, but you know that every instance will always
be a child class of that base class, you can use `mapType(Class from, Class to)` to tell Jactl to treat occurrences
of the `from` class as an instance of the `to` class.

For example, when exposing `LocalDateTime` as a Jactl type, we use this to map all occurrences of `ChronoLocalDateTime`
to `LocalDateTime` because we know that the only supported child class of `ChronoLocalDateTime` will be `LocalDateTime`:
```java
      JactlType localDateTimeType =
        Jactl.createClass("jactl.time.LocalDateTime")
             .javaClass(LocalDateTime.class)
             .baseClass("jactl.time.Temporal")
             .autoImport(autoImport)
             .mapType(ChronoLocalDateTime.class, LocalDateTime.class)
             .method("atZone", "atZone", "zoneId", ZoneId.class)
             .method("getDayOfMonth", "getDayOfMonth")
          ...
```

## checkpoint() and restore()

If you are not using the [Checkpointing](../language-guide/checkpointing) feature of Jactl then you can ignore the
`checkpoint()` and `restore()` methods.

If you are using checkpointing, then the `checkpoint(BiConsumer<Checkpointer,Object> checkpoint)` method allows you
to provide a way to checkpoint the state of instances of the new built-in type and the `restore(Function<Restorer,Object> restore)`
method allows you to provide a way to restore the state of an instance from a previously checkpointed state.

Here is an example of how these could be implemented for the `Point` class where we only need to save the values of
our `x` and `y` fields in order to be able to reconstruct the `Point` in `restore()`:
```java
JactlType pointType = Jactl.createClass("jactl.draw.Point")
                           .javaClass(app.jactl.Point.class)
                           .autoImport(true)
                           .method("of", "of", "x", double.class, "y", double.class)
                           .method("distanceTo", "distanceTo", "other", Point.class)
                           .checkpoint((checkpointer,obj) -> {
                             Point p = (Point)obj;
                             checkpointer.writeDouble(p.x);
                             checkpointer.writeDouble(p.y);
                           })
                           .restore(restorer -> Point.of(restorer.readDouble(), restorer.readDouble()))
                           .register();
```

The `Checkpointer` and `Restorer` classes provide methods for writing and reading various primitive types but also
provides `writeObject()` and `readObject()` calls for writing and reading complex types (that already have their
own checkpoint/restore implementations).

## Forward Declarations

You can use forward declarations to avoid problems where there are circular dependencies using the
`Jactl.declareClass(String jactlClass, Class javaClass)` method.
This tells Jactl that there will be a Jactl type defined with the `jactlClass` fully qualified name that uses
the given Java class as its implementation.

## Constructors

At the moment there is no support for instantiating instances of newly registered built-in types using a constructor.
Jactl does not support explicit constructors and so, to get around this, you can expose a static factory method
(for example, see the `Point.of()` method) or methods as a way to construct instances of the type. 

## Fields

There is no support at the moment for exposing fields of a newly registered type.
If you want access to the fields, then you will need to provide methods that return the field values.

## JactlContext Specific Built-Ins

If you have different types of Jactl scripts that need different built-in types, then you can register new
built-in types against a specific `JactlContext` object that you then use for compiling and running the scripts.
The calls are all the same but instead of using `Jactl.xxx()` you use `jactlContext.xxx()`.

You need to specify the `hasOwnBuiltIns(true)` call on the `JactlContext` to specify that it will have its own set
of built-in types.

For example, to register our `Point` class as a new built-in type for a given `JactlContext`:
```java
JactlContext jactlContext = JactlContext.create()
                                        .hasOwnBuiltIns(true)
                                        .build();

JactlType pointType = jactlContext.createClass("jactl.draw.Point")
                                  .javaClass(app.jactl.Point.class)
                                  .autoImport(true)
                                  .method("of", "of", "x", double.class, "y", double.class)
                                  .method("distanceTo", "distanceTo", "other", Point.class)
                                  .register();
```

## Examples

More detailed examples of how to expose Java classes as new built-in types for Jactl can be found in the
source code for [DateTimeClasses.java](https://github.com/jaccomoc/jactl/blob/main/src/main/java/io/jactl/runtime/DateTimeClasses.java)
where we have exposed a number of Java date/time classes in Jactl.
