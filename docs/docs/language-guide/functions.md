---
title: Functions
---

Functions are defined using the `def` keyword followed by the function name, a list of parameters in parentheses, and
a block of code in `{}`.
The return type of the function can be specified before the function name.
If no return type is specified then `def` is assumed.

For example:
```groovy
def add(x,y) { x + y }
add(3,4)                                 // 7
int multiply(int x, int y) { x * y }
multiply(3,4)                           // 12
```

## Return Statement

The `return` statement can be used to return a value from a function.
If the last statement in a function is not a `return` statement then the value of the last statement is returned.

For example:
```groovy
def add(x,y) { return x + y }
add(3,4)                             // 7
```

The `return` statement is useful when there are multiple return sites within the same function:
```groovy
def func(x,y,z) {
  return -1 unless x > 0 && y > 0 && z > 0
  if (x % y == z) {
    return x * y * z
  }
  return x + y + z
}
```

## Default Parameter Values

Function parameters can have default values.
If a parameter has a default value then it is optional when invoking the function.

For example:
```groovy
def format(num, int base = 10) { sprintf("%9s", num.toBase(base) as String) }
format(300)          // 300
format(300, 16)      // 12C
```

## Named Arguments

When invoking a function you can use named arguments to specify the values of the parameters.
The names of the arguments must match the names of the parameters in the function definition.
The order of the named arguments does not matter.

For example:
```groovy
def format(num, int base = 10) { sprintf("%9s", num.toBase(base) as String) }
format(num:400, base:16)           // 190
format(base:8, num:400)            // 620
```

## Functions as Values

Functions are first-class citizens in Jactl and can be passed around as values.
A function can be assigned to a variable and then invoked through that variable.
A function can be passed as an argument to another function.

For example:
```groovy
def add(x,y) { x + y }
def g = add
g(3,4)                            // 7
def apply(f, x, y) { f(x,y) }
apply(add, 3, 4)                  // 7
```
