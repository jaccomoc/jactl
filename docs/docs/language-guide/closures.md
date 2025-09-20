---
title: Closures
---

Closures are anonymous functions that can be defined inline.
They are defined using the `{}` syntax.
Parameters are declared after the opening `{` and are separated by commas.
The `->` symbol separates the parameters from the body of the closure.

For example:
```groovy
def add = { x,y -> x + y }
```

Closures are invoked like functions, using `()` call syntax:
```groovy
add(3,4)          // 7
```

## Implicit _it_ Parameter

If a closure has no declared parameters then it has an implicit parameter called `it`.

For example:
```groovy
def twice = { it + it }
twice(3)          // 6
```

## Closure Passing Syntax

If the last parameter to a function is a closure then the closure can be written after the closing `)` for the other
arguments:
```groovy
def logResult(prefix, clos) { "$prefix: result is ${ clos() }"}
logResult("Addition", { 2 + 3 })     // Pass closure as 2nd argument
                                     // result: Addition: result is 5
logResult("Addition"){ 2 + 3 }       // Closure arg can be provided after the ')'
                                     // result: Addition: result is 5
```

If there are no other arguments then the parentheses are optional:
```groovy
def measure(clos) { def start = nanoTime(); clos(); nanoTime() - start }
measure{ 100.map{ it * it }.sum() }  // result is number of nanoseconds to evaluate 100.map{ it * it }.sum()
```

## Closures and Scope

Closures can access variables in the scope in which they are defined.
They can also modify the values of these variables.

For example:
```groovy
def x = 5
def sumx = { y -> x + y }
sumx(3)              // 8
def incx = { x++ }
incx()               // 5
x                    // 6
```
