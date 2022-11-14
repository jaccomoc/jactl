---
title: Jacsal Language Guide
---

# The REPL

The easiest way to start learning about the Jacsal language is to use the REPL (read-evaluate-print-loop) that comes
with Jacsal:

```sh
$ java -cp jacsal-repl-1.0.jar jacsal.Repl
Jacsal version 0.90
> 
```

To exit the REPL use the `:q` command or `ctrl-D`.

Anything that does not start with `:` is interpreted as Jacsal code and is evaluated immediately and the result
of the evaluation is printed before prompting again (hence the term read-evaluate-print-loop). For each line that 
is read, if the code does not look like it is complete the REPL will keep reading until it gets a complete
expression or statement that can be executed:
```groovy
Jacsal version 0.90
> 3 * 4
12
> int x = (17 * 13) % 6
5        
> if (x < 4) {
    println 'x is less than 4'
  } else {
    println 'x is not less than 4'
  }
x is not less than 4
>
```

# Statement Termination: Newlines and Semicolons

Jacsal syntax borrows from Java, Groovy, and Perl and so while Java and Perl both require a semicolon to terminate
simple statements, Jacsal adopts the Groovy approach where semicolons are optional.
The only time a semicolon is really required is if more than one statement appears on the same line where they are
used as a statement separator:
```groovy
> int x = 1; println 'x = ' + x; if (x == 2) println 'x is 2'
x = 1
```

The Jacsal compiler, when it encounters a newline, will try to see if the current expression or statement continues
on the next line.
Sometimes it is ambiguous whether the newline terminates the current statement or not and in these
situations, Jacsal will treat the newline is a terminator. For example, consider this:
```groovy
def x = [1,2,3]
x
[0]
```
Since expressions on their own are valid statements, it is not clear whether the last two lines should be interpreted
as `x[0]` or `x` and another statement `[0]` which is a list containing `0`.
In this case, Jacsal will compile the code as two separate statements.

If such a situation occurs within parentheses or where it is clear that the current statement has not finished,
Jacsal will treat the newline as whitespace. For example:
```groovy
def x = [1,2,3]
if (
  x
  [0]
) {
  x = [4,5,6]
}
```
In this case, the newlines within the `if` statement condition will be treated as whitespace and the expression
will be interpreted as `x[0]`.  

# Comments

Comments in Jacsal are denoted using either line comments where `//` is used to begin a comment that extends to the end
of the line or with a pair of `/*` and `*/` that delimit a comment that can be either embeeded within a single line
or span multiple lines:
```groovy
> int x = 3         // define a new variable x
3
> int y = /* comment */ x * 4
12
> int /* this
    is a multi-line comment
  */ z = x * y // another comment at the end of the line
36
```

# Variables

A variable in Jacsal is a symbol that is used to refer to a value. Variables are declared by specifying a type followed
by their name and an optional expression to use as an initial value for the variable. If no initialization expression
is given then the default value for that type is assigned.

After declaration, variables can have new value assigned to them as long as the new value is compatible with the type
that was specified for the variable at declaration time. Variables can be used in expressions at which point their
current value is retrieved and used within that expression:
```groovy
> int x = 4
4
> x = 3 * 8
24
> int y    // gets default value of 0
0
> y = x / 6
4
```

## Valid Variable Names

Variable names must be a valid _identifier_. Identifiers start with a letter or with an underscore `_` followed
by a combination of letters, digits and underscores. The only special case is that a single underscore `_` is not a
valid identifier.

Variable names cannot clash with any built-in language [keywords](/pages/language-guide/#keywords):
```groovy
> int for = 3
Unexpected token 'for': Expecting IDENTIFIER @ line 1, column 5
int for = 3
    ^
```

> Since class names in Jacsal must start with a capital letter it is good practice not
> to start variable names with a captial letter in order to make your code easier to read.

> Unlike Java, the dollar sign `$` is not a valid character for a variable name in Jacsal.

While most of the examples presented here have single character variable names, in general, your code should use
names that are more meaningful. It is recommended that when a variable name consists of more than one word that
so-called _camel case_ is used to indicate the word boundaries by capitalizing the first letter of following words.
For example:
* firstName
* accountNumber
* emailAddress

# Keywords
{: #keywords}

The following table list the reserved keywords used by Jacsal:

| Decimal | List | Map | String | and |
| as | boolean | break | class | continue |
| def | do | double | else | extends |
| false | for | if | implements | import |
| in | instanceof | int | interface | long |
| not | null | or | print | println |
| return | true | unless | var | void |
| while | | | | |

# Types

## Standard Types

Jacsal supports a few built-in types as well as the ability to build your own types by creating
[Classes](classes).

Types are used when declaring variables, fields of classes, function return types, and function parameter types.

The standard types are:

| Name     | Description                | Default Value | Examples                       |
|----------|----------------------------|---------------|--------------------------------|
| boolean  | True or false              | `false`       | `true`, `false`                |
| int      | Integers (32 bit)          | `0`           | `0`, `1`, `-99`                |
| long     | Large integers (64 bit)    | `0L`          | `0L`, `99999999999L`           |
| double   | Floating point numbers     | `0D`          | `0.01D`, `1.234D`, `-99.99D`   |
| Decimal  | Decimal numbers            | '0'           | `0.0`, `0.2345`, `1244.35`     |
| String   | Strings                    | `''`          | `'abc'`, `'some string value'`, `"y=${x * 4}"`, `/^x?[0-9]*$/` |
| List     | Used for lists of values   | `[]`          | `[1,2,3]`, `['a',[1,2,3],'c']` |
| Map      | Map of key/value pairs     | `[:]`         | `[a:1]`, `[x:1, y:[1,2,3], z:[a:1,b:2]]`, `{d:2,e:5}` |
| def      | Used for untyped variables | `null`        | |

## Inferred Type

Variables can also be declared using the `var` keyword if an initialiser expression is given.
Jacsal will then create a variable of the same type as the initialisation expression:

```groovy
> var x = 1       // x will have type int
1
> var y = 2L      // y will have type long
2
> var z = x + y   // z will have type long since adding int and long results in a long
3
> var label = 'some place'  // label has type String
some place
```

## Dynamic/Weak Typing

While Jacsal supports the used of specific types when declaring variables and functions, Jacsal can also be used as
a weakly or dynamically typed language (also known as "duck typing"). The keyword `def` is used to define variables and
functions in situations where the type may not be known up front or where the variable will contain values of different
types at different points in time:
```groovy
> def x = 123
123
> x = 'string value'
string value
```

Although we haven't covered functions yet, here is an example of creating a function where the return type and
parameter type are specified as `int`:
```groovy
> int fib(int x) { x < 2 ? x : fib(x-1) + fib(x-2) }
Function@1534755892
> fib(20)
6765
```
Here is the same function where we use `def` instead:
```groovy
> def fib(def x) { x < 2 ? x : fib(x-1) + fib(x-2) }
Function@26757919
> fib(20)
6765
```

For parameters, the type is optional and if not present it is as though `def` were specified:
```groovy
> def fib(x) { x < 2 ? x : fib(x-1) + fib(x-2) }
Function@45822040
> fib(20)
6765
```

## Numbers

### Integers

Jacsal supports two types of integers: `int` and `long`.

As with Java, 32 bit integers are represented by `int` while `long` is used for 64 bit integers.

The range of values are as follows:

| Type   | Minimum value | Maximum Value |
|--------|---------------|---------------|
| `int`  | -2147483648   | 2147483647    |
| `long` | -9223372036854775808 | 9223372036854775807 | 

To force a literal value to be a `long` rather than an `int` append `L` to the number:
```groovysh
> 9223372036854775807
Error at line 1, column 1: Integer literal too large for int
9223372036854775807
^
> 9223372036854775807L
9223372036854775807
```

### Floating Point

In Jacsal, by default, floating point numbers are represented by the `Decimal` type and numeric literals that have a decimal point
will be interpreted as Decimal numbers.

Decimal numbers are represented internally using the Java `BigDecimal` class. This means that we avoid the problems
of trying to store base 10 numbers inexactly using a binary floating point representation and makes Jacsal a language
suitable for manipulating monetary values.

> The maximum number of decimal places that Jacsal will keep for `BigDecimal` values is
> configurable and defaults to `20`. See [JacsalContext](jacsal_context) for more details.

Jacsal also offers the ability to use native floating point numbers by using the type `double` (which corresponds to
the Java type `double`) for situations where preformance is more important than having exact values.
When using doubles, constants of type `double` should use the `D` suffix to prevent them being interpreted
as Decimal constants and to avoid unnecessary overhead:
```groovy
> double d = amount * 1.5D
```

## Strings

Strings in Jacsal are usually delimited with single quotes:
```groovy
> 'abc'
abc
```
Multi-line strings use triple quotes as delimiters:
```groovy
> '''this is
  a multi-line
  string'''
this is
a multi-line
string
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
> 'a\\b\tc\td\ne'
a\b     c       d
e
```

### Subscripts and Characters

Subscript notation can be used to access individual characters:
```groovy
> 'abc'[0]
a
```
Note that characters in Jacsal are just strings whose length is 1. Unlike Java, there is no separate `char` type to
represent an individual character:
```groovy
> 'abc'[2] == 'c'
true
```

If you need to get the Unicode number for a given character you can cast the single character string into an int:
```groovy
> (int)'a'
97
```

To convert back from a Unicode number to a single character string use the `asChar` method that exists for int values:
```groovy
> 97.asChar()
a
> def x = (int)'X'
88
> x.asChar()
X
```

### String Operators

Strings can be concatenated using `+`:
```groovy
> 'abc' + 'def'
abcdef
```
If two objects are added using `+` and the left hand side of the `+` is a string then the other is converted to a
string before concatenation takes place:
```groovy
> 'abc' + 1 + 2
abc12
```
The `*` operator can be used to repeat multiple instances of a string:
```groovy
> 'abc' * 3
abcabcabc
```

### Expression Strings

Strings that are delimited with double quotes can have embedded expressions inside them where `$` is used to denote the
start of the expression. If the expression is a simple identifier then it identifies a variable whose value should be
expanded in the string:
```groovy
> def x = 5
5
> "x = $x"
x = 5
> def msg = 'some message'
some message
> "Received message '$msg' from sender"
Received message 'some message' from sender
```
If the expression is surrounded in `{}` then any arbitrary Jacsal expression can be included:
```groovy
> def str = 'xyz'
xyz
> def i = 3
3
> "Here are $i copies of $str: ${str * i}"
Here are 3 copies of xyz: xyzxyzxyz
```
You can, of course, have further nested interpolated strings within the expression itself:
```groovy
> "This is a ${'con' + "tr${'i' * (3*6 % 17) + 'v'}e" + 'd'} example"
This is a contrived example
```
As with standard single quoted strings, multi-line interpolated strings are supported with the use of triple double
quotes:
```groovy
> def x = 'pol'
pol
> def y = 'ate'
ate
> """This is a multi-line
  inter${x + y + 'abcd'[3]} string"""
This is a multi-line
interpolated string
```

While it is good practice to include simple expressions within a `${}` section of an expression string,
it is actually possible for the code block within the `${}` to contain multiple statements.
If the embeded expression contains multiple statements then the value of the last statement is used as the value
to be inserted into the string:
```groovy
> "First 5 pyramid numbers: ${ def p(n){ n==1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }"
First 5 pyramid numbers: 1, 5, 14, 30, 55
```

You can use `return` statements to return a value to be used from anywhere within
the block of statements.
The `return` just returns a value from the embedded expression; it does not cause a return
to occur in the surrounding function where the expression string resides.


### Regex Strings

In order to better support regular expressions, regex strings can be delimited with `/` and are multi-line strings
where standard backslash escaping for `\n`, `\r`, etc is not performed. Backslashes can be used to escape `/`, `$`,
and any other regex specific characters such as `[`, `{`, `(` etc. to treat those characters as normal and not have
them be interpreted as regex syntax:
```groovy
> def x = 'abc.d[123]'
abc[123]
> x =~ /c\.d\[12[0-9]]/
true
```

Regex strings are also expression strings and thus support embedded expressions within `${}` sections of the regex
string:
```groovy
> def x = 'abc.d[123]'
abc.d[123]
> x =~ /abc\.d\[${100+23}]/
true
```

> Note that an empty regex string `//` is treated as the start of a line comment so is not supported.

## Lists

A Jacsal `List` represents a list of values of any type. Lists can have a mixture of types within them. Lists are
created using the `[]` syntax where the elements are a list of comma separated values:
```groovy
> []
[]        
> [1,2,3]
[1, 2, 3]
> ['value1', 2, ['a','b']]
['value1', 2, ['a', 'b']]
```
The elements of a `List` can themseles be a `List` (as shown) or a `Map` or any type supported by Jacsal (including
instances of user defined classes).

The `size()` function gives you the number of elements in a list. It can be invoked with the list as the argument or
can be used as a method call on the list by placing the call after the list:
```groovy
> List x = ['value1', 2, ['a', 'b']]
['value1', 2, ['a', 'b']]
> size(x)
3
> x.size()
3
```

There are many other built-in functions provided that work with `List` objects. These are described in more detail in the
section on [Collections](#collections).

Lists can be added together:
```groovy
> [1,2] + [2,3,4]
[1, 2, 2, 3, 4]
```
Note how `2` now appears twice. Lists keep track of all elements, whether duplicated or not.

Single elements can be added to a list:
```groovy
> ['a','b','c'] + 'd'
['a', 'b', 'c', 'd']
> def x = 'e'
e
> ['a','b','c'] + 'd' + x
['a', 'b', 'c', 'd', 'e']
> def y = ['a','b','c'] + 'd' + x    // Can use a 'def' variable to hold a list
['a', 'b', 'c', 'd', 'e']
> y += 'f'
['a', 'b', 'c', 'd', 'e', 'f']
```

There is also the `in` operator that allows you to check whether an element already exists in a list and `!in` which
checks that an element is not in a list:
```groovy
> def x = ['a', 'b']
['a', 'b']
> def y = 'b'
b
> y in x
true
> 'a' !in x
false
```
> The `in` and `!in` operators will search the list from the beginning of the list to try to
> find the element so they are not very efficient once the list reaches any significant size. You might want to rethink
> your use of data structure if you are using `in` or `!in` on lists with more than a few elements.

You can retrieve invidual elements of a list using subscript notation where the index of the element  is enclosed
in `[]` immediately after the list and indexes start at `0` (so the first element is at position `0`):
```groovy
> def x = ['value1', 2, ['a', 'b']]
['value1', 2, ['a', 'b']]
> x[0]
value1
> x[1]
2
> x[2]
['a', 'b']
> x[2][1]
b
```
Note how the last example retrieves an element from the list nested within `x`.

You can also assign to elements of a list using the subscript notation. You can even assign to an element beyond the
current size of the list which will fill any gaps with `null`:
```groovy
> def x = ['value1', 2, ['a', 'b']]
['value1', 2, ['a', 'b']]
> x[1] = 3
3
> x
['value1', 3, ['a', 'b']]
> x[10] = 10
10
> x
['value1', 3, ['a', 'b'], null, null, null, null, null, null, null, 10]
```

## Maps

A `Map` in Jacsal is used hold a set of key/value pairs and provides efficient lookup of a value based on a given key
value.

> Unlike Java, Jacsal `Map` objects only support keys which are strings. If you try to use an
> object that is not a `String` as a key, Jacsal will throw an error.

Maps can be constructed as a list of colon seperated `key:value` pairs:
```groovy
> Map x = ['a':1, 'b':2, 'key with spaces':[1,2,3]]
[a:1, b:2, 'key with spaces':[1, 2, 3]]
```
If the key is a valid identifier (or keyword) then the key does not need to be quoted:
```groovy
> def x = [a:1, b:2]
[a:1, b:2]
> def y = [for:1, while:2, import:3]    // keywords allowed as keys
[for:1, while:2, import:3]
```

### Variable Value as Key
If you have a variable whose value you wish to use as the map key then you should surround the key in parentheses `()`
to tell the compiler to use the variable value and not the identifier as the key:
```groovy
> def a = 'my key'
my key
> def x = [(a):1, b:2]
['my key':1, b:2]
```
You could also use an interpolated string as another way to achieve the same thing:
```groovy
> def a = 'my key'
my key
> def x = ["$a":1, b:2]
['my key':1, b:2]
```

### Map Addition
As with lists, you can add maps together:
```groovy
> [a:1,b:2] + [c:3,d:4]
[a:1, b:2, c:3, d:4]
```
If the second map has a key that matches a key in the first list then it is assumed that the key should be updated
with the new value:
```groovy
> [a:1,b:2] + [b:4]
[a:1, b:4]
```
Keys in the left-hand map value that don't exist in the right-hand map have their values left untouched.

### JSON Syntax
Jacsal also supports JSON-like syntax for maps. This makes it handy if you want to cut-and-paste some JSON into your
Jacsal script:
```groovy
> {"name":"Fred Smith", "employeeId":1234, "address":{"street":["Apartment 456", "123 High St"], "town":"Freetown"} }
[name:'Fred Smith', employeeId:1234, address:[street:['Apartment 456', '123 High St'], town:'Freetown']]
```

### toString(indent)
Maps can be used to build up complex, nested data structures. The normal `toString()` will convert the map to its
standard compact form but if you specify an indent amount it will provide a more readable form:
```groovy
> def employee = [ name:'Fred Smith', employeeId:1234, dateOfBirth:'1-Jan-1970',
                   address:[street:'123 High St', suburb:'Freetown', postcode:'1234'], 
                   phone:'555-555-555']
[name:'Fred Smith', employeeId:1234, dateOfBirth:'1-Jan-1970', address:[street:'123 High St', suburb:'Freetown', postcode:'1234'], phone:'555-555-555']
> employee.toString(2)
[
  name: 'Fred Smith',
  employeeId: 1234,
  dateOfBirth: '1-Jan-1970',
  address: [
    street: '123 High St',
    suburb: 'Freetown',
    postcode: '1234'
  ],
  phone: '555-555-555'
]
```
> The `toString()` Jacsal function outputs values in a form that is legal, executable
> Jacsal code, which is useful for cut-and-pasting into scripts and when working with the REPL command line.

### Map Field Access

Maps can be used as though they are objects with fields using `.`:
```groovy
> def x = [a:1, b:2]
[a:1, b:2]
> x.a
1
```
The value of the field could itself be another map so you can chain the access as needed:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> x.c.d
4
```
If you want the field name (the key) to itself be the value of another variable or expression then you can either use
subscript notation (see below) or use an interpolated string expression:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> def y = 'c'
c
> x."$y".e
5
```
As well as retrieving the value for an entry in a map, the field access notation can also be used to add a field or
update the value of a field within the map:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> x.b = 4
4
> x
[a:1, b:4, c:[d:4, e:5]]
> x.c.e = [gg:2, hh:3]
[gg:2, hh:3]
> x
[a:1, b:4, c:[d:4, e:[gg:2, hh:3]]]
> x.c.f = [1,2,3]
[1, 2, 3]
> x
[a:1, b:4, c:[d:4, e:[gg:2, hh:3], f:[1, 2, 3]]]
```

### Map Subscript Access

Maps can also be accessed using subscript notation:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> x['b']
2
> x['c']['d']
4
```
With subscript based access the value of the "index" within the `[]` is an expression that evaluates to the field (key)
name to be looked up. This makes accessing a field whose name comes from a variable more straightforward:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> def y = 'c'
c
> x[y]
[d:4, e:5]
```
As with the field notation access, new fields can be added and values for existing ones can be updated:
```groovy
> def x = [a:1, b:2, c:[d:4,e:5]]
[a:1, b:2, c:[d:4, e:5]]
> x['b'] = 'abc'
abc
> x['zz'] = '123'
123
> x
[a:1, b:'abc', c:[d:4, e:5], zz:'123']
```

### Auto-Creation

The field access mechanism can also be used to automatically create missing maps or lists, based on the context, when used on the left hand side of an assignment.

Imagine that we need to execute something like this:
```groovy
> x.a.b.c.d = 1
1
```
If at the point in the script we don't actually know whether all of the intermediate fields have been created then
we would need to implement this like this:
```groovy
> if (x == null) { x = [:] }
[:]
> if (x.a == null) { x.a = [:] }
[:]
> if (x.a.b == null) { x.a.b = [:] }
[:]
> if (x.a.b.c == null) { x.a.b.c = [:] }
[:]
> x.a.b.c.d = 1
1
> x
[a:[b:[c:[d:1]]]]
```
With Jacsal these intermediate fields will be automatically created if they don't exist. Jacsal will not automatically create the top level variable, however, if it does not yet have a value:
```groovy
> def x
> x.a.b.c.d = 1
Null value for Map/List during field access @ line 1, column 2
x.a.b.c.d.e = 1
 ^
```
If part of the context of the field assignment looks like a list rather an a map then a list will be
created as required:
```groovy
> def x = [:]
> x.a.b[0].c.d = 1
1
> x.a.b[1].c.d = 2
2
> x
[a:[b:[[c:[d:1]], [c:[d:2]]]]]
```

While normally access to fields of a Map can also be done via subscript notation, if the field does not exist then
Jacsal will assume that access via subscript notation implies that an empty List should be created if the field is
missing rather than a Map:
```groovy
> def x = [:]
[:]
> x.a['b'] = 1
Non-numeric value for index during List access @ line 1, column 4
x.a['b'] = 1
   ^
```

# in Operator

The `in` operator can be used to check for existent of an element in a List or Map and can also be used to check for
a string being a substring of another string.
```groovy

```

# Truthiness

In Jacsal we often want to know whether an expression is `true` or not. The _truthiness_ of an expression is used to
determine which branch of an `if` statement to evaluate, or whether a `for` loop or a `while` loop should continue
or not, for example. In any situation where a boolean `true` or `false` is expected we need to evaluate the given
expression to determine wether it is true or not.

Obviously, if the expression is a simple boolean or boolean value then there it is obvious how to intepret the value:
`true` is true, and `false` is false.

Other types of expressions can also be evalauted in a boolean context and return `true` or `false`.

The rules are:
* `false` is `false`
* `0` is `false`
* `null` values are `false`
* Empty list or empty map is `false`
* All other values are `true` 


# Field Paths

Use of strings/expressions as field names



# Functions

```groovy
> def f(x) { x + x }
asdasd
> f(2)
4
> f('abc')
'abcabc'
```

Of course, whether that behaviour is considered a feature or a bug is a matter of personal choice. You can always specify a type to make it clear what was intended:
```groovy
> int f(int x) { x + x }
```

The difference between function and closure is that functions can make forward references to functions not yet declared.

# Collections
{: #collections}


# Keywords
{: #keywords}


# TODO
* multi-value return from functions
* named arg passing
* passing list as list of args
* lazy vs non-lazy iteration (Java vs Groovy)
** force non-lazy by using collect()
* no static state in classes and no static initialiser blocks either (for same reason)
* 