---
title: Classes
---

Jactl supports user-defined classes.
Classes are defined using the `class` keyword followed by a class name which must start with an uppercase letter.

As with Java, classes can define fields and methods with appropriate types (or `def` if dynamic typing is being
used).
Since Jactl is a scripting language, for simplification, there is no concept of `public`, `protected`, or
`private` - all fields and methods are effectively `public`.

For example:
```groovy
class Point { double x; double y }
def p = new Point(x:1, y:2)
p.x              // 1
p.y              // 2
```

We can add a method to this class:
```groovy
class Point {
  double x
  double y
  def distance(other) {
    def dx = x - other.x
    def dy = y - other.y
    (dx*dx + dy*dy).sqrt()
  }
}
def p1 = new Point(x:0, y:0)
def p2 = new Point(x:3, y:4)
p1.distance(p2)                  // 5
```

We used `def` as the return type for our method, but we could have used `Decimal` or `double`, for example,
if we so desired.

## Default Values

Fields can be given default values if needed.
These default values can be based on the values of other fields:

```groovy
class X {
  int x
  int y = x * x
  int z = 0
}
```

## Const Fields

Classes can have constant fields for commonly used numeric and String values.
Const fields use the `const` keyword to indicate that they are constant values, then followed by a standard
field declaration with a value assignment.

For example:
```groovy
class X {
  const long MAX_VALUE = 100000
  ...
}
```

Only numeric types and String values are support for constant fields.

If the type is omitted, Jactl will infer the type from the value being assigned:
```groovy
class X {
  const MAX_VALUE = 100000L
  ...
}
```

## Static Methods

Methods of a class can be defined as `static` which means that they can be invoked directly without needing
a class instance.
It is therefore an error for a static method to refer to any class fields since there is no class instance
associated with the invocation.

For example, here is a class with a static factory method for creating `Circle` instances:
```groovy
class Circle {
  Point  centre
  double radius
  static Circle create(double x, double y, double r) {
    die 'Radius must be > 0' if r <= 0
    return new Circle(new Point(x,y), r)
  }
}
```

## No Static Fields

Jactl does not support static fields.
The reason that they are not allowed is twofold:
1. Jactl is intended to run in distributed applications where there are multiple instances running.
   Having a static data member in a class for sharing information across all class instances makes
   no sense when there are multiple application instances running since there would then be multiple
   instances of the static data.
2. By avoiding the use of static data, it also means that there is no way for multiple scripts on
   different threads to be trying to access and modify the same data.
   This means that Jactl does not need to provide any thread sychronisation mechanisms that are
   notoriously error-prone and avoids having to worry about deadlocks.

## Constructors

To construct an instance of a class you used the `new` keyword followed by the class name and then parameters
corresponding to one of the class constructors.

Unlike Java, Jactl classes have constructors built for them and does not allow user to create their own.
There are two constructors built for each class:
1. A constructor taking positional parameters, and
2. A constructor taking named parameters.

The constructor taking positional parameters has one parameter per mandatory field in the class in the order in
which the fields were defined.

For example:
```groovy
class Car {
  String make
  String model
  int    year
  String colour = 'white'
}

def car = new Car('Holden', 'Kingswood', 1975)
```

The `make`, `model`, and `year` fields are mandatory fields (since they have no default value) and so the
default positional constructor is created with a parameter for each of these mandatory fields.

The named parameter constructor can be used to make it clearer what fields are being set, or when you want to
set the values of optional fields.
The order of the fields is not important when using named parameters.

For example:
```groovy
def car = new Car(make:'Ford', model:'Falcon', year:1987, colour:'green')
```

If you want to create your own constructor in order to perform validation, for example, you can create static
factory method for creating class instances (see example of `Circle` class above).

## Inheritance

Classes can inherit from other classes using the `extends` keyword.

For example:
```groovy
class ColouredPoint extends Point { 
  String colour
}
def cp = new ColoredPoint(x:1, y:2, color:'red')
cp.x             // 1
cp.colour        // red
```

Child classes can override methods of a parent class to provide their own implementation.
For example, we can have a tree of nodes, some of which are directories, and some of which are
files.
Each type overrides the `size()` method as needed:
```groovy
class Node {
  Node   parent
  String name
  long size() { 0 }
}

class Dir extends Node {
  List children = []
  def  addChild(Node child) { children <<= child }
  long size() { children.map(size).sum() }
}

class File extends Node {
  long fileSize
  long size() { fileSize }
}
```

## `this` Keyword

Within a regular (non-static) class method, the keyword `this` refers to the current instance.
This allows you to explicitly refer to fields where it is otherwise ambiguous, for example:
```groovy
class Point {
  double x,y
  double distanceTo(double x, double y) {
    def dx = this.x - x
    def dy = this.y - y
    (dx*dx + dy*dy).sqrt()
  }
}
```

It is also necessary to use `this` if a method needs to pass the current instance to another function.

## `super` Keyword

If you are overriding a method, the `super` keyword allows you to invoke the parent's version of the method
from within the overriding method.
For example:
```groovy
class X {
   def doSomething() {
      ...
   }
}

class Y extends X {
   def doSomething() {
      super.doSomething()
      ...
   }
}
```

## Packages

Classes can be declared in the scripts where they are used (as shown above), but classes can also be put into packages
just like in Java so that they can then be used by multiple scripts.

:::note
This package hierarchy sits in its own namespace (defined by the application) in order not to conflict with any Java
pacakges.
:::

Like in Java, when putting classes in packages, each top level class must be declared in a file that matches the class
name (with a `.jactl` suffix).

For example, this could be the definition for our `Point` class in a file called `Point.jactl` and in package called
`app.utils`:
```groovy
package app.utils

class Point {
   double x,y
}
```

Note that package names must be all lowercase.

Classes and scripts that belong to the same package automatically have access to all other classes within the package.
If a script or class needs to access a class in different package, they need to use the `import` statement.

## Import Statement

The `import` statement is how a script or class imports the definition of another class in order to use that class.

For example:
```groovy
import app.utils.Point

class Square {
   Point p1
   Point p2
   static 
}
```

It is possible to import references to all classes of a package if there are too many to list individually by using
`*`:
```groovy
import app.utils.*
```

You can rename a class during import using `as` if it clashes with another class name or if you simply want a shorter
name to use within the given script/class:
```groovy
import app.utils.Point as P

class Square {
   P p1, p2
}
```

## Static Import

The `import` statement, as well as importing class definitions, can be used to import static methods and constants
from other classes.
As for importing classes, the imported methods/constants can be renamed as part of the import by using `as`:
```groovy
import static utils.Math.PI
import static utils.Math.circleArea as area

def radius = 3
def area = circleArea(radius)
def circumference = 2 * PI * radius
```

It is also possible to import all static methods and constants of a class:
```groovy
import static utils.Math.*
```

## Compilation of Class Files

If you are embedding Jactl in your application, it is up to the application to make sure that the classes are
compiled in order for scripts to have access to them.
See the [Integration Guide](../integration-guide/introduction) for more details.

When using classes with command line scripts, the `-P` option allows you to specify where the package root(s) of
your Jactl classes are located so that the script can access your classes.
See the [Command-Line Scripts Guide](../command-line-scripts.md) for more information.
