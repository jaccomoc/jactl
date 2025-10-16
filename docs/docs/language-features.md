---
title: "Language Features"
---

# Language Features

This page describes a few of the language features that Jactl offers.
Jactl syntax is largely based on Java with parts also liberally borrowed from Groovy and Perl.

## Semicolon Optional

Semicolon statement terminator is optional and is only needed if multiple statements are on the same line:
```groovy
int  x = 1
long y = 2
```
or
```groovy
int x = 1; long y = 2
```

## Functions Return Value of Last Statement

Functions can explicitly return a value using the `return` statement but if the last statement is not a `return` then
its value will be returned:
```groovy
int fib(int x) { return x <= 2 ? 1 : fib(x-1) + fib(x-2) }
```
is the same as:
```groovy
int fib(int x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
```

## Dynamic Typing with Optional Static Typing

Sppecifying types is optional in Jactl when declaring variables, parameters, and fields.
If a type is specified then Jactl can perform compile-time checking and some optimisations.

Static typing:
```groovy
int fib(int x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
```

Dynamic typing:
```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
```

## Map and List Literals

Map and list literals:
```groovy
// Lists
[1,2,3,4]
['abc', 'def', 'xyz']

// Maps (keys only need to be quoted if they are not simple identifiers)
[a:1, b:2, c:3]
['a key':'a value', b:'another value']

// Can easily return maps or lists from functions
def addAndMultiply(x,y) { [sum:x + y, product:x * y] }  
```

Maps can use the canonical `[ ]` form or can also be provided in JSON syntax:
```groovy
Map m1 = [x:1, y:2]
Map m2 = {"x":1, "y":2}
```

## Interpolated Strings

Strings can be delimited with single quotes for simple strings, or double quotes for interpolated strings where `$`
is used to insert the value of a variable:
```groovy
def x = 'this is a simple string'
def y = "Value of x = $x"
```

More complex expressions can be inserted into interpolated strings using `${ }`:
```groovy
def x = 3
def y = "x * x is ${x * x}"

// Expressions can contain other interpolated strings
def z = "Interpolated with ${"embedded interpolated (x*x=${x*x})"} string"
```

## Multi-Line Strings

Multi-line simple strings and interpolated strings can be specified using triple quotes:
```groovy
def simple = '''
multi-line
simple string'''

def interpolated = """
This is not 
a $simple"""
```

Triple quotes are useful when you want to embed quotes and don't want to have to escape them:
```groovy
def x = 3
def y = """Value "${x*x}" for input of $x"""
def z = '''Triple quoted simple 'string' with embedded quotes'''
```

## Functions as Values

Functions are just objects and can be passed around as values:
```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }

def applyArg(f, x) { f(x) }

def g = fib        // assign to another variable
g(40)

applyArg(fib, 40)  // pass as arg to a function
```

Methods passed as value are bound to the owning object:
```groovy
class Multiplier {
  int multiple = 1
  def multiply(int x) { x * multiple }
}

def m = new Multplier(multiple:4)

g = m.multiply
g(3)                      // result is 12

applyArg(m.multiply, 5)   // result is 20
```

## Closures

Closures in Jactl follow the Groovy syntax where the parameters are declared (with optional type) after the
initial `{`:
```groovy
def add = { x,y -> x + y }

add(2,3)           // result is 5

def f = add
f('abc', 'xyz')    // result is 'abcxyz'
```

Closures can close over variables visible in the existing scope:
```groovy
def x = 2
def sumx = { y -> x + y }

sumx(3)   // result is 5
x = 7
sumx(3)   // result is 10
```

Variables can be modified within closures:
```groovy
def x = 5
def incx = { x++ }
incx()    // x will now be 6    
```

## Closure Passing Syntax

If the last parameter to a function is a closure then the closure can be written after the closing `)` for the other
arguments:
```groovy
def logResult(prefix, clos) { "$prefix: result is ${ clos() }"}

logResult("Addition", { 2 + 3 })   // Pass closure as 2nd argument

logResult("Addition"){ 2 + 3 }     // Closure arg can be provided after the ')'
```

If there are no other arguments then the parentheses are optional:
```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
def measure(clos) { def start = nanoTime(); clos(); nanoTime() - start }

def totalTime

totalTime = measure{
  for (int i = 0; i < 40; i++) {
    fib(i + 1)
  }
}

// Or using built-in "each" method
totalTime = measure{ 40.each{ i -> fib(i + 1) } } 
```

## Higher-Order Functions

Since functions and closures can be passed by value it is possible to write other functions that
operate on a function.
For example, we can create a higher-order function `compose` that returns a new function that is
the composition of two other functions:
```groovy
def compose(f,g) { return { x -> f(g(x)) } }
def twice(x) { x * 2 }
def plus3(x) { x + 3 }

def plus3Twice = compose(twice, plus3)

plus3Twice(7)        // returns 20
```

## Efficient Built-in Higher-Order Functions

There are also many built-in higher-order functions for operating on collection types (List, Map, array)
including `map`, `flatMap`, `filter`, and `each`:
```groovy
def fib(x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }

// Find first fibonacci number under 1000 that is a multiple of 57
def x = 1000.map{ [it, fib(it)] }.filter{ n,fib -> fib % 57 == 0 }.limit(1)[0]

// Returns [36, 14930352] so 36th fibonacci number (14930352) is a multiple of 57  
```

These higher-order functions that operate over collections work by iterating over the collections
rather than creating a new collection each time.
So in the example above where we iterate over the first 1000 Fibonacci numbers looking for the
first one that is a multiple of 57, we don't actually calculate the first 1000 Fibonacci numbers.
The `limit(1)` means that we stop iterating as soon as we find the first one that matches.
Each number flows through each of the higher-order functions one at a time before we iterate
over the subsequent number.

## Regex Support with Capture Variables $1, $2, ...

Regular expression matching is part of the language rather than being delegated to library calls.
The `=~` operator searches a string for a substring that matches the given pattern:
```groovy
'abcxdef' =~ /x/     // Result is true
```

Capture expressions in a regex pattern cause variables `$1`, `$2`, and so on to get populated with the corresponding
values from the string being matched:
```groovy
if ('Total: 14ms' =~ /: (\d+)(.*)$/) {
  println "Amount is $1, unit is $2"   // Prints: Amount is 14, unit is ms     
}

def x = 'a=1,bcd=234,e=3'
def result = []
// Extract all key=value values from x into list of [key,value] pairs:
while (x =~ /([^,=]+)=(\d+)/g) {
  result <<= [$1, $2]
}
println result
// Prints: [['a', '1'], ['bcd', '234'], ['e', '3']]
```

Regex substitutions can be done using `s/.../.../` syntax:
```groovy
def x = 'abcdef'
x =~ s/[ace]/x/   // Substitute only first match
println x         // Prints: xbcdef

x =~ s/[ace]/x/g  // Replace all matching substrings
println x         // Prints: xbxdxf
```

Embedded expressions can be used in the replacement string and can refer to capture variables:
```groovy
def x = 'abcdef'
x =~ s/([ace])(.)/${ $2 + $1 }/g
println x                            // Prints: badcfe 
```

## Implicit _it_ Parameter

Closures that don't declare a parameter have an automatic `it` parameter created for them:
```groovy
def twice = { it + it }
twice(3)          // 6

// All numbers from 1 to 100 that are multiples of 7 and whose digits sum to be a multiple of 5:
def nums = 100.map{ it + 1 }
              .filter{ it % 7 == 0 }
              .map{ [it, it.toString().map{ it as int }.sum()] }
              .filter{ it[1] % 5 == 0 }
              .map{ it[0] }
// Result: [14, 28, 91]
```

Regular expressions operate on the implicit `it` variable if no string value is provided:
```groovy
// Sanitise text to make suitable for a link
def linkify = { s/ /-/g;  s/[^\w-]//g }

// Find all top level headings in input and generate markdown for table of contents:
stream(nextLine).filter{ /^# /r }
                .map{ s/# // }
                .map{ "* [$it](#${ linkify(it.toLowerCase()) })" }
                .each{ println it }
```

## Pattern Matching with Destructuring

Jactl provides `switch` expressions that can match on literal values but can also be used to
match on the structure of the data and bind variables to different parts of the structure:
```groovy
switch (x) {
  'abc'              -> 'matched'    // match against simple values
  /X=(\d+),Y=(\d+)/n -> $1 + $2      // regex match with capture vars
  [1,*]              -> 'matched'    // list whose first element is 1
  [_,_]              -> 'matched'    // list with 2 elements
  [int,String,_]     -> 'matched'    // 3 element list. 1st is an int, 2nd is a String
  [a,_,a]            -> a * 2        // 1st and last elements the same in 3 element list
  [a,*,a] if a < 10  -> a * 3        // list with at least 2 elements. 1st and last the same and < 10
  [a,${2*a},${3*a}]  -> a            // match if list is of form [2,4,6] or [3,6,9] etc
  [a,b,c,d]          -> a+b+c+d      // 4 element list
  ['abc',*mid,'xyz'] -> m.join(':')  // mid binds to list without first and last elements  
}
```

Simple quicksort:
```groovy
def qsort(x) {
  switch(x) {
    [], [_] -> x
    [h, *t] -> qsort(t.filter{it < h}) + [h] + qsort(t.filter{it >= h}) 
  }
}
```

## Statement _if_/_unless_ Condition

As well as standard `if` statements, it is possible to have the `if` and the condition come after the statement to
be executed so this standard form of `if`:
```groovy
def fib(x) {
  if (x <= 2) {
    return 1
  }
  return fib(x-1) + fib(x-2)
}
```
can be written:
```groovy
def fib(x) {
  return 1 if x <= 2
  return fib(x-2) + fib(x-1)
}
```

It is also possible to us `unless` instead of `if` when that is more natural:
```groovy
def fib(x) {
  return 1 unless x > 2
  return fib(x-2) + fib(x-1)
}
```

## Additional _and_, _or_, and _not_ Operators

As well as the standard `&&`, `||`, and `!` boolean operators, there are `and`, `or`, and `not` operators that have
much lower precedence (lower than assignment expressions, for example).
This allows for this style of programming where it makes sense:
```groovy
stream(nextLine).each{
  /^\$ *cd +\/$/r   and do { cwd = root;             return }
  /^\$ *cd +\.\.$/r and do { cwd = cwd.parent;       return }
  /^\$ *cd +(.*)$/r and do { cwd = cwd.children[$1]; return }
  /^\$ *ls/r        and return
  /^dir +(.*)$/r    and cwd.children[$1] = new Dir($1,-1,cwd)
  /^(\d+) +(.*)$/n  and cwd.children[$2] = new File($2,$1)
}
```

## Default Parameter Values

Functions and closures can have parameters with default values:
```groovy
def format(num, int base = 10) { sprintf("%9s", num.toBase(base) as String) }

format(300)      // Defaults to decimal: '      300'
format(300, 16)  // Hex:                 '      12C' 
format(300, 2)   // Binary:              '100101100'
```

## Named Arguments

When invoking fuctions/closures, the parameter names can be supplied:
```groovy
def format(num, int base = 10) { sprintf("%9s", num.toBase(base) as String) }

format(num:400, base:16)
format(base:8,  num:400)       // any order supported for named args 
```

## _eval()_ Statement

Jactl has a built-in `eval` statement which will compile and execute a string of Jactl code at run-time.
A map of values for variables that the script references can be passed in as an optional argument:
```groovy
eval('3 + 4')                                        // returns 7
eval('x + y', [x:3, y:4])                            // returns 7
eval('def twice = {it+it}; twice(x+y)', [x:3, y:4])  // returns 14
```

## Built-in JSON Support

Jactl can convert objects into JSON using the `toJson()` method:
```groovy
def x = [a:1,b:[x:3,y:4],c:['a','b','c']]
x.toJson()         // {"a":1,"b":{"x":3,"y":4},"c":["a","b","c"]}
```

The corresponding `fromJson()` method on strings will convert back from JSON to simple objects:
```groovy
def json = '{"a":1,"b":{"x":3,"y":4},"c":["a","b","c"]}'
json.fromJson()   // [a:1, b:[x:3, y:4], c:['a', 'b', 'c']] 
```

For user defined classes, Jactl will generate a `toJson()` method and a class static method `fromJson()`:
```groovy
class X { int i; String s }
X x = new X(i:3, s:'abc')
x.toJson()      // {"i":3,"s":"abc"}
x = X.fromJson('{"i":3,"s":"abc"}')
```

## Decimal Number Support

Jactl uses `BigDecimal` for "floating point" numbers by default (known as `Decimal` numbers in Jactl).
This makes it suitable for manipulating currency amounts where arbitrary precision is required.
Jactl also offers `double` numbers for situations where speed is more important than accuracy.
For example:
```groovy
1211.12 / 100            // Result is: 12.1112
((double)1211.12) / 100  // Result is: 12.111199999999998
```

## Default Value Operator _?:_

Jactl offers the `?:` operator that will return the value on the right-hand side if the left-hand side is null:
```groovy
def x
x ?: 123    // value will be 123 since x is null
```

## Spaceship Operator _&#60;=&#62;_

The `<=>` operator is a comparator operator that returns `-1`, `0`, or `1` if the
left-hand side is less than, equal, or greater than the right-hand side respectively.
This makes it easy to create a comparator function for sorting things:
```groovy
def employees = [[name:'Mary', salary:10000], [name:'Susan', salary:5000], [name:'Fred', salary:7000]]

// Sort according to salary
employees.sort{ a,b -> a.salary <=> b.salary }

// Result: [[name:'Susan', salary:5000], [name:'Fred', salary:7000], [name:'Mary', salary:10000]]
```

## Field Access _?._ and _?[_ Operators

When accessing fields of a map or a list/array the `?.` operator (for maps) and the `?[` operator (for lists) allows
you to safely dereference a null value.
If the map/list is null then the resulting field reference will also be null if you use these operators:
```groovy
def x
x.a            // Generates a NullError since x is null
x?.a           // Value will be null
x?[0]          // Value will be null
x?.a?.b?[0]    // Value will be null
```

## Auto-Creation of Fields

When assigning a value to a field expression like `a.b.c` the values for intermediate fields will be automatically
constructed where possible.
For example:
```groovy
def x = [:]       // empty map
x.a.b.c = '123'   // automatically create fields for x.a and x.a.b
x                 // x now has a value of: [a:[b:[c:'123']]]
x.d.e[2] = 'abc'  // x now has a value of: [a:[b:[c:'123']], d:[e:[null, null, 'abc']]]
```

## Multi-Assignments

Multiple variables can be declared and initialised in one statement and multiple assignments
can also be done in one statement:
```groovy
def (x,y,z) = [4,5,6]                    // initialisation
(x,y,z) = [0,1,2]                        // assignment
(x,y) += [2,3]                           // supports all assignment operators +=, -=, *=, etc

def a = [:]
(a.b.c[x], a.b.c[y]) = [x + y, y + z]    // multi-assign with auto-creation of subfields
```

You can use a multi-assignment to swap the values of two variables:
```groovy
(x,y) = [y,x]      // swaps x and y
```

## Classes

Users can define their own classes:
```groovy
class X {
  int i
  String field
  def anotherField
  def method() { field + ':' + anotherField + ':' + i }
}

class Y extends X {
  // Override method in base class 
  def method() { "Y: " + super.method() }
}

def y = new Y(i:4, field:'value1', anotherField:'value2')
y.method()      // returns 'Y: value1:value2:4'
```

## Packages and Import Statements

Classes can be grouped into packages (libraries) just like in Java/Groovy using
a package statement:

```groovy
package org.customer.utils

class Useful {
  static def func() {
    ...
  }
}
```

Import statements allow you to use a class without needing to fully qualify
it with its package name each time:
```groovy
import org.customer.utils.Useful
def x = Useful.func()
```
Import allows you to give a class an alias using `as`:
```groovy
import org.customer.utils.Useful as U
def x = U.func()
```
You can also import static functions and class constants using `import static`:
```groovy
import static org.customer.utils.Useful.func as f
def x = f()   // invokes Useful.func()
```
