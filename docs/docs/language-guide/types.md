---
title: Types
---

## Standard Types

Jactl supports a few built-in types as well as the ability to build your own types by creating
[Classes](classes).

Types are used when declaring variables, fields of classes, function return types, and function parameter types.

The standard types are:

| Name     | Description                | Default Value | Examples                       |
|----------|----------------------------|---------------|--------------------------------|
| boolean  | True or false              | `false`       | `true`, `false`                |
| int      | Integers (32 bit)          | `0`           | `0`, `1`, `-99`                |
| long     | Large integers (64 bit)    | `0L`          | `0L`, `99999999999L`           |
| double   | Floating point numbers     | `0D`          | `0.01D`, `1.234D`, `-99.99D`   |
| Decimal  | Decimal numbers            | `0`           | `0.0`, `0.2345`, `1244.35`     |
| String   | Strings                    | `''`          | `'abc'`, `'some string value'`, `"y=${x * 4}"`, `/^x?[0-9]*$/` |
| List     | Used for lists of values   | `[]`          | `[1,2,3]`, `['a',[1,2,3],'c']` |
| Map      | Map of key/value pairs     | `[:]`         | `[a:1]`, `[x:1, y:[1,2,3], z:[a:1,b:2]]`, `{d:2,e:5}` |
| def      | Used for untyped variables | `null`        | | 

## Inferred Type

Variables can also be declared using the `var` keyword if an initialiser expression is given.
Jactl will then create a variable of the same type as the initialisation expression:

```groovy
var x = 1       // x will have type int

var y = 2L      // y will have type long

var z = x + y   // z will have type long since adding int and long results in a long

var label = 'some place'  // label has type String
```

## Dynamic/Weak Typing

While Jactl supports the use of specific types when declaring variables and functions, Jactl can also be used as
a weakly or dynamically typed language (also known as _duck typing_). The keyword `def` is used to define variables and
functions in situations where the type may not be known up front or where the variable will contain values of different
types at different points in time:
```groovy
def x = 123
x = 'string value'
```

Although we haven't covered functions yet, here is an example of creating a function where the return type and
parameter type are specified as `int`:
```groovy
int fib(int x) { x < 2 ? x : fib(x-1) + fib(x-2) }
```

Here is the same function where we use `def` instead:
```groovy
def fib(def x) { x < 2 ? x : fib(x-1) + fib(x-2) }
```

For parameters, the type is optional and if not present it is as though `def` were specified:
```groovy
def fib(x) { x < 2 ? x : fib(x-1) + fib(x-2) }
```

## Numbers

### Integers

Jactl supports two types of integers: `int` and `long`.

As with Java, 32-bit integers are represented by `int` while `long` is used for 64-bit integers.

The range of values are as follows:

| Type   | Minimum value | Maximum Value |
|--------|---------------|---------------|
| `int`  | -2147483648   | 2147483647    |
| `long` | -9223372036854775808 | 9223372036854775807 | 

To force a literal value to be a `long` rather than an `int` append `L` to the number:
```groovy
9223372036854775807L
```

### Floating Point

In Jactl, by default, floating point numbers are represented by the `Decimal` type and numeric literals that have
a decimal point will be interpreted as Decimal numb ers.

Decimal numbers are represented internally using the Java `BigDecimal` class. This avoids the problems
of trying to store base 10 numbers inexactly using a binary floating point representation.

Jactl also offers the ability to use native floating point numbers by using the type `double` (which corresponds to
the Java type `double`) for situations where preformance is more important than having exact values.
When using doubles, constants of type `double` should use the `D` suffix to prevent them being interpreted
as Decimal constants and to avoid unnecessary overhead:
```groovy
double d = amount * 1.5D
```

To illustrate how Decimal values and double values behave differently, consider the following example:
```groovy
12.12D + 12.11D   // floating point double values give approximate value of:
                  // 24.229999999999997
12.12 + 12.11     // Decimal values give exact value:
                  // 24.23
```

## Strings

Strings in Jactl are usually delimited with single quotes:
```groovy
'abc'
```

Multi-line strings use triple quotes as delimiters and will include embedded newlines
as newline characters inside the string:
```groovy
'''this is
  a multi-line
  string'''
```

### Special Characters

Special characters such as newlines can be embedded in strings by using the appropriate escape sequence. The following
escape sequences are supported:

| Character       | Escape Sequence |
|-----------------|-----------------|
| Newline         | \n              |
| Carriage return | \r              |
| Tab             | \t              |
| Formfeed        | \f              |
| Backspace       | \b              |
| Backslash       | \\              |
| Single quote    | \'              |

For example:
```groovy
println 'a\\b\t\'c\'\td\ne'
```
The resulting output is:
```
a\b	'c'	d
e
```

### Subscripts and Characters

Subscript notation can be used to access individual characters:
```groovy
'abc'[0]  // value is 'a'
```
Note that characters in Jactl are just strings whose length is 1. Unlike Java, there is no separate `char` type to
represent an individual character:
```groovy
'abc'[2] == 'c'
```

Subscripts can be negative which is interpreted as an offset from the end of the string.
So to get the last character from a string use an index of `-1`:
```groovy
'xyz'[-1]   // value is 'z'
```

If you need to get the Unicode number for a given character you can cast the single character string into an int:
```groovy
(int)'a'   // result is 97
```

To convert back from a Unicode number to a single character string use the `asChar` method that exists for int values:
```groovy
97.asChar()        // result is 'a'
def x = (int)'X'   // result is 88
x.asChar()         // result is 'X'
```

### String Operators

Strings can be concatenated using `+`:
```groovy
'abc' + 'def'      // result is 'abcdef'
```
If two objects are added using `+` and the left-hand side of the `+` is a string then the other is converted to a
string before concatenation takes place:
```groovy
'abc' + 1 + 2      // result is 'abc12'0
```
The `*` operator can be used to repeat multiple instances of a string:
```groovy
'abc' * 3          // result is 'abcabcabc'
```

The `in` and `!in` operators can be used to check for substrings within a string:
```groovy
'x' in 'xyz'       // true
'bc' in 'abcd'     // true
'xy' in 'abc'      // false
'ab' !in 'xyz'     // true
```

### Expression Strings

Strings that are delimited with double quotes can have embedded expressions inside them where `$` is used to denote the
start of the expression. If the expression is a simple identifier then it identifies a variable whose value should be
expanded in the string:
```groovy
def x = 5
println "x = $x"    // Output: x = 5
def msg = 'some message'
println "Received message '$msg' from sender"
// Output: Received message 'some message' from sender
```
If the expression is surrounded in `{}` then any arbitrary Jactl expression can be included:
```groovy
def str = 'xyz'
def i = 3
println "Here are $i copies of $str: ${str * i}"
// Output: Here are 3 copies of xyz: xyzxyzxyz
```
You can, of course, have further nested interpolated strings within the expression itself:
```groovy
println "This is a ${((int)'a'+2).asChar() + 'on' + "tr${\'i\' * (3*6 % 17) + 118.asChar()}e" + 'd'} example"
// Output: This is a contrived example
```
As with standard single quoted strings, multi-line interpolated strings are supported with the use of triple double
quotes:
```groovy
def x = 'pol'
def y = 'ate'
println """This is a multi-line
inter${x + y + 'abcd'[3]} string"""
// Output:
// This is a multi-line
// interpolated string
```

While it is good practice to use only simple expressions within a `${}` section of an expression string,
it is actually possible for the code block within the `${}` to contain multiple statements.
If the embeded expression contains multiple statements then the value of the last statement is used as the value
to be inserted into the string:
```groovy
println "First 5 pyramid numbers: ${ def p(n){ n==1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }"
// Output: First 5 pyramid numbers: 1, 5, 14, 30, 55
```

You can use `return` statements from anywhere within the block of statements to return the value to be used
for the embedded expression.
The `return` just returns a value from the embedded expression; it does not cause a return
to occur in the surrounding function where the expression string resides.
For example:
```groovy
def x = 3; "x is ${return 'odd' if x % 2 == 1; return 'even' if x % 2 == 0}"
// Output: x is odd
```

### Pattern Strings

In order to better support regular expressions, pattern strings can be delimited with `/` and are multi-line strings
where standard backslash escaping for `\n`, `\r`, etc. is not performed. Backslashes can be used to escape `/`, `$`,
and any other regex specific characters such as `[`, `{`, `(` etc. to treat those characters as normal and not have
them be interpreted as regex syntax:
```groovy
String x = 'abc.d[123]'
String pattern = /c\.d\[12[0-9]]/

// check if x matches pattern using =~ regex operator
x =~ pattern     // result is true
```

Pattern strings are also expression strings and thus support embedded expressions within `${}` sections of the regex
string:
```groovy
def x = 'abc.d[123]'
x =~ /abc\.d\[${100+23}]/     // result is true
```

Pattern strings also support multiple lines:
```groovy
def p = /this is
a multi-line regex string/
```

> Note that an empty pattern string `//` is not supported since this is treated as the start of a line comment.

## Lists

A Jactl `List` represents a list of values of any type. Lists can have a mixture of types within them. Lists are
created using the `[]` syntax where the elements are a list of comma separated values:
```groovy
[]            // empty list
[1,2,3]
['value1', 2, ['a','b']]
```
The elements of a `List` can themseles be a `List` (as shown) or a `Map` or any type supported by Jactl (including
instances of user defined classes).

The `size()` function gives you the number of elements in a list. It can be invoked with the list as the argument or
can be used as a method call on the list by placing the call after the list:
```groovy
List x = ['value1', 2, ['a', 'b']]
size(x)      // result is 3 
x.size()     // result is 3
```

There are many other built-in functions provided that work with `List` objects. These are described in more detail in the
section on [Collection Methods](collection-methods).

Lists can be added together:
```groovy
[1,2] + [2,3,4]    // result is [1, 2, 2, 3, 4]
```
Note how `2` now appears twice: lists keep track of all elements, whether duplicated or not.

Single elements can be added to a list:
```groovy
['a','b','c'] + 'd'
def x = 'e'
['a','b','c'] + 'd' + x
def y = ['a','b','c'] + 'd' + x    // Can use a 'def' variable to hold a list

y += 'f'           // result is ['a', 'b', 'c', 'd', 'e', 'f']
```

Consider this example:
```groovy
def x = [3]
[1,2] + x          // result is [1, 2, 3]
```
We are adding two lists so the result is the list of all the elements but what if we wanted to add `x` itself to the
list and we didn't know whether `x` was itself a list or any other type?
We could do this:
```groovy
[1,2] + [x]       // result is [1, 2, [3]]
```
Another way to force the value of `x` to be added to the list is to use the `<<` operator:
```groovy
[1,2] << x        // result is [1, 2, [3]]
```
The `<<` operator does not care whether the item being added is a list or not - it treats all items the same and adds
appends them to the list.

There are also corresponding `+=` and `<<=` operators for appending to an existing list:
```groovy
def y = [1,2]
y += 3            // result is [1, 2, 3]
y <<= ['a']       // result is [1, 2, 3, ['a']]
```
Note that both `+=` and `<<=` append to the existing list rather than creating a new list.

The `in` operator allows you to check whether an element already exists in a list and there is also `!in` which
checks for an element not being in a list:
```groovy
def x = ['a', 'b']
def y = 'b'
y in x            // true
'a' !in x         // false
```

:::warning
The `in` and `!in` operators will search the list from the beginning of the list to try to
find the element, so they are not very efficient once the list reaches any significant size. You might want to rethink
your use of data structure if you are using `in` or `!in` on lists with more than a few elements.
:::

You can retrieve invidual elements of a list using subscript notation where the index of the element  is enclosed
in `[]` immediately after the list and indexes start at `0` (so the first element is at position `0`):
```groovy
def x = ['value1', 2, ['a', 'b']]
x[0]            // value1
x[1]            // 2
x[2]            // ['a', 'b']
x[2][1]         // b
```
Note how the last example retrieves an element from the list nested within `x`.

As well as using `[]` to access individual elements, you can also use `?[]` as a null-safe way of retrieving elements.
The difference is that if the list is null, instead of getting a null error (when using `[]`) you will get null as the
value:
```groovy
def x = [1,2,3]
x?[1]           // 2
x = null
x[1]            // error
x?[1] == null   // true
```

You can also assign to elements of a list using the subscript notation. You can even assign to an element beyond the
current size of the list which will fill any gaps with `null`:
```groovy
def x = ['value1', 2, ['a', 'b']]
x[1] = 3
x                // ['value1', 3, ['a', 'b']]
x[5] = 10
x                // ['value1', 3, ['a', 'b'], null, null, 10]
```

## Maps

A `Map` in Jactl is used hold a set of key/value pairs and provides efficient lookup of a value based on a given key
value.

> Unlike Java, Jactl `Map` objects only support keys which are strings. If you try to use an
> object that is not a `String` as a key, Jactl will throw an error.

Maps can be constructed as a list of colon seperated `key:value` pairs:
```groovy
Map x = ['a':1, 'b':2, 'key with spaces':[1,2,3]]
```
If the key is a valid identifier (or keyword) then the key does not need to be quoted:
```groovy
def x = [a:1, b:2]
def y = [for:1, while:2, import:3]    // keywords allowed as keys
y.while   // 2
```

### Variable Value as Key
If you have a variable whose value you wish to use as the map key then you should surround the key in parentheses `()`
to tell the compiler to use the variable value and not the identifier as the key:
```groovy
def a = 'my key'
def x = [(a):1, b:2]             // ['my key':1, b:2]
```
You could also use an interpolated string as another way to achieve the same thing:
```groovy
def a = 'my key'
def x = ["$a":1, b:2]            // ['my key':1, b:2]
```

### Map Addition
As with lists, you can add maps together:
```groovy
[a:1,b:2] + [c:3,d:4]            // [a:1, b:2, c:3, d:4]
```
If the second map has a key that matches a key in the first list then its value is used in the resulting map:
```groovy
[a:1,b:2] + [b:4]                // [a:1, b:4]
```
Keys in the left-hand map value that don't exist in the right-hand map have their values taken from the left-hand map.

The `+=` operator adds the values to an existing map rather than creating a new map:
```groovy
def x = [a:1,b:2]
x += [c:3,a:2]                   // [a:2, b:2, c:3]
```

### Map Subtraction
You can subtract from a Map to remove specific keys from the Map (see also [Map.remove()](#map-remove)).
To subtract one map from the other:
```groovy
[a:1,b:2,c:3] - [a:3,b:[1,2,3]]  // [c:3]
```
This will produce a new Map where the entries in the first Map that match the keys in the second Map have been
removed.

Keys in the second Map that don't exist in the first Map will have no effect on the result:
```groovy
[a:1,b:2,c:3] - [x:'abc']        // [a:1, b:2, c:3]
```

You can also subtract a List of values from a Map where the List is treated as a list of keys to be removed:
```groovy
[a:1,b:2,c:3] - ['a','c']        // [b:2]
```

### Map Remove

You can remove individual entries from a Map using the `remove(key)` method:
```groovy
def m = [a:1, b:2]
m.remove('a')                    // returns old value of key: 1
m                                // [b:2]
```

The return value of `remove()` is the value of that key in the map or `null` if the key did not exist.

### JSON Syntax
Jactl also supports JSON-like syntax for maps. This makes it handy if you want to cut-and-paste some JSON into your
Jactl script:
```groovy
{"name":"Fred Smith", "employeeId":1234, "address":{"street":["Apartment 456", "123 High St"], "town":"Freetown"} }
```

### toString(indent)
Maps can be used to build up complex, nested data structures. The normal `toString()` will convert the map to its
standard compact form but if you specify an indent amount it will provide a more readable form:
```groovy
def employee = [ name:'Fred Smith', employeeId:1234, dateOfBirth:'1-Jan-1970',
                 address:[street:'123 High St', suburb:'Freetown', postcode:'1234'],
                 phone:'555-555-555']
println employee.toString(2)
// Output:
// [
//         name: 'Fred Smith',
//         employeeId: 1234,
//         dateOfBirth: '1-Jan-1970',
//         address: [
//                 street: '123 High St',
//                 suburb: 'Freetown',
//                 postcode: '1234'
//         ],
//         phone: '555-555-555'
// ]
```
> The `toString()` Jactl function outputs values in a form that is legal, executable
> Jactl code, which is useful for cut-and-pasting into scripts and when working with the REPL command line.

### Map Field Access

Maps can be used as though they are objects with fields using `.`:
```groovy
def x = [a:1, b:2]
x.a        // 1
```
The value of the field could itself be another map so you can chain the access as needed:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
x.c.d      // 4
```
If you want the field name (the key) to itself be the value of another variable or expression then you can either use
subscript notation (see below) or use an interpolated string expression:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
def y = 'c'
x."$y".e   // 5
```

As well as `.` you can use the `?.` operator for null-safe field access.
The difference being that if the map was null and you try to retrieve a field with `.` you will get a null error
but when using `?.` the value returned will be null.
This makes it easy to retrieve nested fields without having to check at each level if the value is null:
```groovy
def x = [:]
x.a.b                // error: Null value for parent during field access
x.a?.b == null       // true
x?.a?.b?.c == null   // true
```

As well as retrieving the value for an entry in a map, the field access notation can also be used to add a field or
update the value of a field within the map:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
x.b = 4
x                     // [a:1, b:4, c:[d:4, e:5]]
x.c.e = [gg:2, hh:3]
x                     // [a:1, b:4, c:[d:4, e:[gg:2, hh:3]]]
x.c.f = [1,2,3]
x                     // [a:1, b:4, c:[d:4, e:[gg:2, hh:3], f:[1, 2, 3]]]
```

### Map Subscript Access

Maps can also be accessed using subscript notation:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
x['b']                // 2
x['c']['d']           // 4
```
With subscript based access the value of the "index" within the `[]` is an expression that evaluates to the field (key)
name to be looked up. This makes accessing a field whose name comes from a variable more straightforward:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
def y = 'c'
x[y]                  // [d:4, e:5]
```

There is also the `?[]` null-safe access as well if you don't know whether the map is null and don't want to
check beforehand.

As with the field notation access, new fields can be added and values for existing ones can be updated:
```groovy
def x = [a:1, b:2, c:[d:4,e:5]]
x['b'] = 'abc'
x['zz'] = '123'
x                     // [a:1, b:'abc', c:[d:4, e:5], zz:'123']
```

## Auto-Creation of Map and List Entries 

The field access mechanism can also be used to automatically create missing maps or lists, based on the context,
when used on the left-hand side of an assignment.

Imagine that we need to execute something like this:
```groovy
x.a.b.c.d = 1
```
If we don't actually know whether all the intermediate fields have been created then we would need to implement
something like this:
```groovy
if (x == null) { x = [:] }
if (x.a == null) { x.a = [:] }
if (x.a.b == null) { x.a.b = [:] }
if (x.a.b.c == null) { x.a.b.c = [:] }
x.a.b.c.d = 1
x                 // [a:[b:[c:[d:1]]]]
```
With Jactl these intermediate fields will be automatically created if they don't exist so we only need write
the last line of script:
```groovy
x.a.b.c.d = 1
x                 // [a:[b:[c:[d:1]]]]
```

If part of the context of the field assignment looks like a List rather than a Map then a List will be
created as required:
```groovy
def x = [:]
x.a.b[0].c.d = 1
x.a.b[1].c.d = 2
x                 // [a:[b:[[c:[d:1]], [c:[d:2]]]]]
```
Note that in this case `x.a.b` is an embedded List, not a Map.

Normally access to fields of a Map can also be done via subscript notation but if the field does not exist then
Jactl will assume that access via subscript notation implies that an empty List should be created if the field is
missing, rather than a Map:
```groovy
def x = [:]
x.a['b'] = 1      // error: Non-numeric value for index during List access
```
