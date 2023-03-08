---
title: Jacsal Language Guide
---

# The REPL

The easiest way to start learning about the Jacsal language is to use the REPL (read-evaluate-print-loop) that comes
with Jacsal:

```shell
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

# Command Line Scripts

Jacsal scripts can also be invoked from the command line:
```shell
$ java -jar jacsal-1.0.jar
Usage: jacsal [switches] [programFile] [inputFile]* [--] [arguments]*
         -p           : run in a print loop reading input from stdin or files
         -n           : run in a loop but don't print each line
         -e script    : script string is interpreted as jacsal code (programFile not used)
         -V var=value : initialise jacsal variable before running script
         -d           : debug: output generated code
         -h           : print this help
```

Using the _-e_ option allows you to supply the script on the comand line itself:
```shell
$ java -jar jacsal-1.0.jar -e '3 * 14'
12
```

If you have a Jacsal script in a file called `myscript.jacsal` then you can the script like this:
```shell
$ java -jar jacsal-1.0.jar myscript.jacsal
```

See [Command Line Invocation](/pages/language-guide/#commandline) for more details about how to invoke scripts
from the command line.

# Statement Termination: Newlines and Semicolons

Jacsal syntax borrows heavily from Java, Groovy, and Perl and so while Java and Perl both require a semicolon to terminate
simple statements, Jacsal adopts the Groovy approach where semicolons are optional.
The only time a semicolon is required as a statement separator is if more than one statement appears on the
same line:
```groovy
> int x = 1; println 'x = ' + x; println 'x is 2' if x == 2
x = 1
```

The Jacsal compiler, when it encounters a newline, will try to see if the current expression or statement continues
on the next line.
Sometimes it can be ambiguous whether the newline terminates the current statement or not and in these
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

Comments in Jacsal are the same as for Java and Groovy and are denoted using either line comment form, where `//` is
used to begin a comment that extends to the end of the line, or with a pair of `/*` and `*/` that delimit a comment that
can be either embeeded within a single line or span multiple lines:
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

After declaration, variables can have new values assigned to them as long as the new value is compatible with the type
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
Unexpected token 'for': Expecting identifier @ line 1, column 5
int for = 3
    ^
```

> Since class names in Jacsal must start with a capital letter it is good practice not
> to start variable names with a capital letter in order to make your code easier to read.

> Unlike Java, the dollar sign `$` is not a valid character for a variable name in Jacsal.

While most of the examples presented here have single character variable names, in general, your code should use
names that are more meaningful. It is recommended that when a variable name consists of more than one word that
_camel case_ is used to indicate the word boundaries by capitalizing the first letter of following words.
For example:
* firstName
* accountNumber
* emailAddress

# Keywords
{: #keywords}

The following table list the reserved keywords used by Jacsal:

| Key Words |         |     |         |       |
|-----------|---------|-----|---------|-------|
| BEGIN     | Decimal | END | List    | Map   |
| String    | and     | as  | boolean | break |
| class     | continue | def | die | do |
| double    | else | extends | false | for |
| if        | implements | import | in | instanceof |
| int       | interface | long | new | not |
| null      | or | package | print | println |
| return    | static | true | unless | var |
| void      | while | | | |

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

While Jacsal supports the use of specific types when declaring variables and functions, Jacsal can also be used as
a weakly or dynamically typed language (also known as _duck typing_). The keyword `def` is used to define variables and
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

As with Java, 32-bit integers are represented by `int` while `long` is used for 64-bit integers.

The range of values are as follows:

| Type   | Minimum value | Maximum Value |
|--------|---------------|---------------|
| `int`  | -2147483648   | 2147483647    |
| `long` | -9223372036854775808 | 9223372036854775807 | 

To force a literal value to be a `long` rather than an `int` append `L` to the number:
```groovy
> 9223372036854775807
Error at line 1, column 1: Integer literal too large for int
9223372036854775807
^
> 9223372036854775807L
9223372036854775807
```

### Floating Point

In Jacsal, by default, floating point numbers are represented by the `Decimal` type and numeric literals that have
a decimal point will be interpreted as Decimal numbers.

Decimal numbers are represented internally using the Java `BigDecimal` class. This avoids the problems
of trying to store base 10 numbers inexactly using a binary floating point representation.

Jacsal also offers the ability to use native floating point numbers by using the type `double` (which corresponds to
the Java type `double`) for situations where preformance is more important than having exact values.
When using doubles, constants of type `double` should use the `D` suffix to prevent them being interpreted
as Decimal constants and to avoid unnecessary overhead:
```groovy
> double d = amount * 1.5D
```

To illustrate how Decimal values and double values behave differently, consider the following example:
```groovy
> 12.12D + 12.11D     // floating point double values give approximate value
24.229999999999997
> 12.12 + 12.11       // Decimal values give exact value
24.23
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
> 'a\\b\t\'c\'\td\ne'
a\b	'c'	d
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
If two objects are added using `+` and the left-hand side of the `+` is a string then the other is converted to a
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

The `in` and `!in` operators can be used to check for substrings within a string:
```groovy
> 'x' in 'xyz'
true
> 'bc' in 'abcd'
true
> 'xy' in 'abc'
false
> 'ab' !in 'xyz'
true
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
> "This is a ${((int)'a'+2).asChar() + 'on' + "tr${'i' * (3*6 % 17) + 118.asChar()}e" + 'd'} example"
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

While it is good practice to use only simple expressions within a `${}` section of an expression string,
it is actually possible for the code block within the `${}` to contain multiple statements.
If the embeded expression contains multiple statements then the value of the last statement is used as the value
to be inserted into the string:
```groovy
> "First 5 pyramid numbers: ${ def p(n){ n==1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }"
First 5 pyramid numbers: 1, 5, 14, 30, 55
```

You can use `return` statements from anywhere within the block of statements to return the value to be used
for the embedded expression.
The `return` just returns a value from the embedded expression; it does not cause a return
to occur in the surrounding function where the expression string resides.
For example:
```groovy
> def x = 3; "x is ${return 'odd' if x % 2 == 1; return 'even' if x % 2 == 0}"
x is odd
```

### Pattern Strings

In order to better support regular expressions, pattern strings can be delimited with `/` and are multi-line strings
where standard backslash escaping for `\n`, `\r`, etc. is not performed. Backslashes can be used to escape `/`, `$`,
and any other regex specific characters such as `[`, `{`, `(` etc. to treat those characters as normal and not have
them be interpreted as regex syntax:
```groovy
> String x = 'abc.d[123]'
abc[123]
> String pattern = /c\.d\[12[0-9]]/
c\.d\[12[0-9]]
> x =~ pattern     // check if x matches pattern using =~ regex operator
true
```

Pattern strings are also expression strings and thus support embedded expressions within `${}` sections of the regex
string:
```groovy
> def x = 'abc.d[123]'
abc.d[123]
> x =~ /abc\.d\[${100+23}]/
true
```

Pattern strings also support multiple lines:
```groovy
> def p = /this is
  a multi-line regex string/
this is
a multi-line regex string
```

> Note that an empty pattern string `//` is not supported since this is treated as the start of a line comment.

## Lists

A Jacsal `List` represents a list of values of any type. Lists can have a mixture of types within them. Lists are
created using the `[]` syntax where the elements are a list of comma separated values:
```groovy
> []            // empty list
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
Note how `2` now appears twice: lists keep track of all elements, whether duplicated or not.

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

Consider this example:
```groovy
> def x = [3]
[3]
> [1,2] + x
[1, 2, 3]
```
We are adding two lists so the result is the list of all the elements but what if we wanted to add `x` itself to the
list and we didn't know whether `x` was itself a list or any other type?
We could do this:
```groovy
> [1,2] + [x]
[1, 2, [3]]
```
Another way to force the value of `x` to be added to the list is to use the `<<` operator:
```groovy
> [1,2] << x
[1, 2, [3]]
```
The `<<` operator does not care whether the item being added is a list or not - it treats all items the same and adds
appends them to the list.

There are also corresponding `+=` and `<<=` operators for appending to an existing list:
```groovy
> def y = [1,2]
[1, 2]
> y += 3
[1, 2, 3]
> y
[1, 2, 3]
> y <<= ['a']
[1, 2, 3, ['a']]
> y
[1, 2, 3, ['a']]
```
Note that both `+=` and `<<=` append to the existing list rather than creating a new list.

The `in` operator allows you to check whether an element already exists in a list and there is also `!in` which
checks for an element not being in a list:
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
> find the element, so they are not very efficient once the list reaches any significant size. You might want to rethink
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

As well as using `[]` to access individual elements, you can also use `?[]` as a null-safe way of retrieving elements.
The difference is that if the list is null, instead of getting a null error (when using `[]`) you will get null as the
value:
```groovy
> def x = [1,2,3]
[1, 2, 3]
> x?[1]
2
> x = null
> x[1]
Null value for parent during field access @ line 1, column 3
x[1]
  ^
> x?[1] == null
true
```

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
> y.while
2
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
If the second map has a key that matches a key in the first list then its value is used in the resulting map:
```groovy
> [a:1,b:2] + [b:4]
[a:1, b:4]
```
Keys in the left-hand map value that don't exist in the right-hand map have their values taken from the left-hand map.

The `+=` operator adds the values to an existing map rather than creating a new map:
```groovy
> def x = [a:1,b:2]
[a:1, b:2]
> x += [c:3,a:2]
[a:2, b:2, c:3]
> x
[a:2, b:2, c:3]
```

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

As well as `.` you can use the `?.` operator for null-safe field access.
The difference being that if the map was null and you try to retrieve a field with `.` you will get a null error
but when using `?.` the value returned will be null.
This makes it easy to retrieve nested fields without having to check at each level if the value is null:
```groovy
> def x = [:]
[:]
> x.a.b
Null value for parent during field access @ line 1, column 5
x.a.b
    ^
> x.a?.b == null
true
> x?.a?.b?.c == null
true
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

There is also the `?[]` null-safe access as well if you don't know whether the map is null and don't want to
check beforehand.

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

The field access mechanism can also be used to automatically create missing maps or lists, based on the context,
when used on the left-hand side of an assignment.

Imagine that we need to execute something like this:
```groovy
> x.a.b.c.d = 1
1
```
If we don't actually know whether all the intermediate fields have been created then we would need to implement
something like this:
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
With Jacsal these intermediate fields will be automatically created if they don't exist so we only need write
the last line of script:
```groovy
> x.a.b.c.d = 1
1
> x
[a:[b:[c:[d:1]]]]
```

Note that Jacsal will not automatically create a value for the top level variable, however, if it does not yet
have a value. In this case, for example:
```groovy
> def x
> x.a.b.c.d = 1
Null value for Map/List during field access @ line 1, column 2
x.a.b.c.d.e = 1
 ^
```

If part of the context of the field assignment looks like a List rather than a Map then a List will be
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
Note that in this case `x.a.b` is an embedded List, not a Map.

Normally access to fields of a Map can also be done via subscript notation but if the field does not exist then
Jacsal will assume that access via subscript notation implies that an empty List should be created if the field is
missing, rather than a Map:
```groovy
> def x = [:]
[:]
> x.a['b'] = 1
Non-numeric value for index during List access @ line 1, column 4
x.a['b'] = 1
   ^
```

### in and !in Operators

The `in` and `!in` operators can be used to check for existent of a key in a Map:
```groovy
> def x = [a:1, b:2]
[a:1, b:2]
> 'a' in x
true
> 'x' in x
false
> 'ab' !in x
true
```

# Boolean Operators



# Truthiness

In Jacsal we often want to know whether an expression is `true` or not. The _truthiness_ of an expression is used to
determine which branch of an `if` statement to evaluate, or whether a `for` loop or a `while` loop should continue
or not, for example. In any situation where a boolean `true` or `false` is expected we need to evaluate the given
expression to determine whether it is true or not.

Obviously, if the expression is a simple boolean or boolean value then there is no issue with how to intepret the value:
`true` is true, and `false` is false.

Other types of expressions can also be evalauted in a boolean context and return `true` or `false`.

The rules are:
* `false` is `false`
* `0` is `false`
* `null` values are `false`
* Empty list or empty map is `false`
* All other values are `true` 

For example:
```groovy
> [] && true
false
> [[]] && true
true
> [:] && true
false
> [false] && true
true
> [a:1] && true
true
> '' && true
false
> 'abc' && true
true
> 1 && true
true
> 0 && true
false
> null && true
false
```

# Expressions and Operators

## Operator Precedence

Jacsal supports the following operators. Operators are shown in increasing precedence order and operators of the same
precedence are shown with the same precedence value:

| Precedence Level |            Operator            | Description                                         |
|:----------------:|:------------------------------:|:----------------------------------------------------|
|        1         |              `or`              | Logical or                                          |
|        2         |             `and`              | Logical and                                         |
|        3         |             `not`              | Logical not                                         |
|        4         |              `=`               | Assignment                                          |
|                  |              `?=`              | Conditional assignment                              |
|                  | `+=` `-=` `*=` `/=` `%=` `%%=` | Arithmetic assignment operators                     |
|                  |       `<<=` `>>=` `>>>=`       | Shift assignment operators                          |
|                  |      `&=` `&#124;=` `^=`       | Bitwise assignment operators                        |
|        5         |             `? :`              | Ternary conditional opeartor                        |
|                  |              `?:`              | Default value operator                              |
|        6         |         `&#124;&#124;`         | Boolean or                                          |
 |        7         |              `&&`              | Boolean and                                         |
 |        8         |            `&#124;`            | Bitwise or                                          |
|        9         |              `^`               | Bitwise xor                                         |
|        10        |              `&`               | Bitwise and                                         |
|        11        |     `==` `!=` `===` `!==`      | Equality and inequality operators                   |
|                  |             `<=>`              | Compator operator                                   |
|                  |           `=~` `!~`            | Regex compare and regex not compare                 |
 |        12        |       `<` `<=` `>` `>=`        | Less than and greater than operators                |
|                  |   `instanceof` `!instanceof`   | Instance of and not instance of operators           |
 |                  |           `in` `!in`           | In and not in operators                             |
|                  |              `as`              | Conversion operator                                 |
|        13        |        `<<` `>>` `>>>`         | Shift operators                                     |
|        14        |            `+` `-`             | Addition and subtraction                            |
|        15        |            `*` `/`             | Multiplication and division operators               |
|                  |            `%` `%%`            | Modulo and remainder operators                      |
|        16        |              `~`               | Bitwise negate                                      |
|                  |              `!`               | Boolean not                                         |
|                  |           `++` `--`            | Prefix and postfix increment/decrement operators    |
|                  |            `+` `-`             | Prefix minus and plus operators                     |
|                  |            `(type)`            | Type cast                                           |
|        17        |            `.` `?.`            | Field access and null-safe field access             |
|                  |          `[ ]` `?[ ]`          | Map/List/String element access and null-safe access |
|                  |              `()`              | Function/method invocation                          |
|                  |              `{}`              | Function/method invocation (closure passing syntax) |
|                  |             `new`              | New instance operator                               |

When evaluating expressions in Jacsal operators with higher precedence are evaluated before operators with lower
preedence.
For example in the following expression the multiplicaton is evaluated before the addition or subtraction because
it has higher precedence than addition or substraction:
```groovy
> 3 + 2 * 4 - 1
10
```
Bracketing can be used to force the order of evaluation of sub-expressions where necessary:
```groovy
> (3 + 2) * (4 - 1)
15
```

## Assignment and Conditional Assignment

Variables can have values assigned to them using the `=` operator:
```groovy
> def x = 1
1
> x = 2
2
```

Since an assignment is an expression and has a value (the value being assigned) it is possible to use an assignment
within another expression:
```groovy
> def x = 1
1
> def y = x = 3
3
> x
3
> y
3
> y = (x = 4) + 2
6
> x
4
```
Conditional assignment uses the `?=` operator and means that the assignment only occurs if the expression on the right
hand side is non-null.
So `x ?= y` means `x` will be assigned the value of `y` only if `y` is not null:
```groovy
> def x = 1
1
> def y          // no initialiser so y will be null
> x ?= y
> x              // x still has its original value since y was null
1
```

## Basic Arithmetic Operators

The standard arithmetic operators `+`, `-`, `*`, `/` are supported for addition, subtraction, multiplication, and
division:
```groovy
> 3 * 4 + 6 / 2 + 5 - 10
10
```
Remember that `*` and `/` have higher precedence and are evaluated before any addition or subtraction. 

## Prefix `+` and `-`

The `+` and `-` operators can also be used as prefix operators:
```groovy
> -(3 - 4)
1
> +(3 - 4)
-1
```
The `-` prefix operator negates the value following expression while the `+` prefix operator does nothing but exists
to correspond to the `-` case so that things like `-3` and `+3` can both be written.

## Bitwise Operators

The bitwise operators are `|`, `&`, `^`, and `~`.

The `|` operator performs a binary _or_ at the bit level.
For each _bit_ of the left-hand and right-hand side the corresponding _bit_ in the result will be 1 if the _bit_ in
either the left-hand side or right-hand side was 1.

For example, `5 | 3` is the same in binary as `0b101 | 0b011` which at the bit level results in `0b111` which is 7:
```groovy
> 5 | 3
7
> 0b101 | 0b011     // result will be 0b111
7
```

The `&` operator does an _and_ of each bit and the resulting bit is 1 only if both left-hand side and right-hand side
bit values were 1:
```groovy
> 5 & 3
1
> 0b101 & 0b011    // result is 0b001
1
```

The `^` operator is an _xor_ and the result for each bit value is 0 if both left-hand side and right-hand side bit values
are the same and 1 if they are different:
```groovy
> 5 ^ 3
6
> 0b101 ^ 0b011     // result is 0b110
6
```

The `~` is a prefix operator and does a bitwise _not_ of the expression that follows so the result for each bit is the
opposite of the bit value in the expression:
```groovy
> ~5
-6
> ~0b00000101    // result will be 0b11111111111111111111111111111001 which is -6
-6
```

## Shift Operators

The shift operators `<<`, `>>`, `>>>` work at the bit level and shift bits of a number by the given number of positions.

The `<<` operator shifts the bits to the left meaning that the number gets bigger since we are multiplying by powers of 2.
For example, shifting left by 1 bit will multiply the number by 2, shifting left by 2 will multiply by 4, etc.

For example:
```groovy
> 5 << 1          // 0b0101 << 1 --> 0b1010 which is 10
10
> 5 << 4          // same as multiplying by 16
80
```

The '>>' and '>>>' operators shift the bits to the right.
The difference between the two is how negative numbers are treated.
The `>>` operator is a signed shift right and preserves the sign bit (the top most bit) so if it is 1 it remains 1 and
1 is shifted right each time from this top bit position.
The '>>>' operator treats the top bit like any other bit and shifts in 0s to replace the top bits when the bits are
shifted right.

For example:
```groovy
> def x = 0b11111111000011110000111100001111 >> 16     // shift right 16 but shift in 1s at left since -ve
-241
> x.toBase(2)
11111111111111111111111100001111
> x = 0b11111111000011110000111100001111 >>> 16        // shift right 16 but shift in 0s at left
65295
> x.toBase(2)                                          // note that leading 0s not shown
1111111100001111
```

## Modulo `%` and Remainder `%%` operators

In addition to the basic four operators, Jacsal also has `%` (modulo) and `%%` (remainder) operators.
Both operators perform similar functions in that they calculate the "remainder" after dividing by some number.
The difference comes from how they treat negative numbers.

The remainder operator `%%` works exactly the same way as the Java remainder operator (which in Java is represented
by a single `%` rather than the `%%` in Jacsal):
> `x %% y` is defined as being `x - (int)(x/y) * y`

The problem is if `x` is less than `0` then the result will also be less than 0:
```groovy
> -5 %% 3
-2
```

When doing modulo arithmetic you usually want (in my opinion) values to only be between `0` and `y - 1` when evaluating
something modulo `y` (where `y` is postive) and between `0` and `-(y - 1)` if `y` is negative.

Jacsal, therefore, the definition of `%` is:
> `x % y` is defined as `((x %% y) + y) %% y`

This means that in Jacsal `%` returns only postive values if the right-hand side is positive and only negative values
if the right-hand side is negative.
Jacsal retains `%%` for scenarios where you want compatibility with how Java does things or for when you know that
the left-hand side will always be positive and you care about performance (since `%%` compiles to a single JVM
instruction while `%` is several instructions).

## Increment/Decrement Operators

Jacsal offers increment `++` and decrement `--` operators that increment or decrement a value by `1`.
Both prefix and postfix versions of these operators exist.
In prefix form the result is the result after applying the increment or decrement while in postfix form the value
is the value before the increment or decrement occurs.

For example:
```groovy
> def x = 1
1
> x++    // increment x but return value of x before increment
1
> x
2
> ++x    // increment x and return new value
3
> x
3
> --x    // decrement x and return new value
2
> x--    // decrement x but return old value
2
> x
1
```

If the expression is not an actual variable or field that can be incremented then there is nothing to increment or
decrement but in the prefix case the value returned is as though the value had been incremented or decremented:
```groovy
> 3++
3
> ++3
4
> --(4 * 5)
19
> (4 * 5)--
20
```

## Comparison Operators

The following table shows the operators that can be used to compare two values:

| Operator | Description                                                                                  |
|:--------:|:---------------------------------------------------------------------------------------------|
|   `==`   | Equality: evaluates to true if the values are value-wise equal                               |
|   `!=`   | Inequality: evaluates to true if the values are not equal                                    |
|  `===`   | Identity: evaluates to true if the object on both sides is the same object                   |
|  `!==`   | Non-Identity: evaluates to true if the objects are not the same object                       |
|   `<`    | Less-than: true if left-hand side is less than right-hand side                               |
|   `<=`   | Less-than-or-equal-to: true if left-hand side is less than or equal to right-hand side       |
|   `>`    | Greater-than: true if left-hand side is greater than right-hand side                         |
|   `>=`   | Greater-than-or-equal-to: true if left-hand side is greater than or equal to right-hand side |

The `<`, `<=`, `>`, `>=` operators can be used to compare any two numbers to check which is bigger than the other and
can also be used to compare strings.

For strings, the lexographic order of the strings determines which one is less than the other.
String comparison is done character by character.
If all characters are equal at the point at which one string ends then the shorter string is less than the other one.
For example:
```groovy
> 'a' < 'ab'
true
> 'abcde' < 'xyz'
true
```

The difference between `==` and `===` (and `!=` and `!==`) is that `==` compares values.
This makes a difference when comparing Lists and Maps (and class instances).
The `==` operator will compare the values of all elements and fields to see if the values are equivalent.
The `===` operator, on the other hand, is solely interested in knowing if the two Lists (or Maps, or objects) are the
same object or not.
For example:
```groovy
> def x = [1, [2,3]]
[1, [2, 3]]
> def y = [1, [2,3]]
[1, [2, 3]]
> x == y           // values are the same
true
> x === y          // objects are not the same
false
> x !== y
true
> x = y
[1, [2, 3]]
> x === y
true
> x !== y
false
```

## Comparator Operator

The `<=>` comparator operator evaluates to -1 if the left-hand side is less than the right-hand side, 0 if the two
values are equal, and 1 if the left-hand side is greater than the right-hand side:
```groovy
> 1 <=> 2
-1
> 2.3 <=> 2.3
0
> 4 <=> 3
1
> 'abc' <=> 'ab'
1
> 'a' <=> 'ab'
-1
```

The operator can be particularly useful when sorting lists as the `sort` method takes as an argument a function
that needs to return -1, 0, or 1 based on the comparison of any two elements in the list:
```groovy
> def employees = [[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
[[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
> employees.sort{ a,b -> a.salary <=> b.salary }      // sort by salary increasing
[[name:'Joe', salary:1500], [name:'Frank', salary:2000], [name:'Daisy', salary:3000]]
> employees.sort{ a,b -> b.salary <=> a.salary }      // sort by salary decreasing
[[name:'Daisy', salary:3000], [name:'Frank', salary:2000], [name:'Joe', salary:1500]]
```

## Logical/Boolean Operators

There are two sets of logical or boolean operators:
1. `&&`, `||`, `!`, and
2. `and`, `or`, `not`

For both `&&` and `and` the result is true if both sides are true:
```groovy
> 3.5 > 2 && 'abc' == 'ab' + 'c'
true
> 5 == 3 + 2 and 7 < 10
true
> 5 == 3 + 2 and 7 > 10
false
```

With the `||` and `or` operators the result is true if either side evaluates to true:
```groovy
> 5 == 3 + 2 || 7 > 10
true
> 5 < 4 or 7 < 10
true
```

The `!` and `not` operators negate the result of the following boolean expression:
```groovy
> ! 5 == 3 + 2
false
> not 5 < 4
true
```

The difference between the two sets of operators is that the operators `and`, `or`, and `not` have very low precedence;
even lower than the assignment operators.
This makes it possible to write things like this somewhat contrived example:
```groovy
> def x = 7
7
> def y = 8
8        
> x == 7 and y = x + (y % 13)       // assign new value to y if x == 7
true
> y
15
```

In this case, there are obviously other ways to achieve the same thing (such as an if statement) but there are
occasions where having these low-precedence versions of the boolean operators is useful.
It comes down to personal preference whether and when to use them.

## Conditional Operator

The conditional operator allows you to embed an `if` statement inside an expression.
It takes the form:
> condition `?` valueIfTrue `:` valueIfFalse

The condition expression is evaluated and if it evaluates to `true` then the result of the entire expression
is the value of the second expression (valueIfTrue).
If the condition is false then the result is the third expression (valueIfFalse).

For example:
```groovy
> def x = 7
7
> def y = x % 2 == 1 ? 'odd' : 'even'
odd
> def z = y.size() > 3 ? x+y.size() : x - y.size()
4
```

## Default Value Operator

The default value operator `?:` evaluates to the left-hand side if the left-hand side is not null and evaluates to
the right-hand side if the left-hand side is null.
It allows you to specify a default value in case an expression evaluates to null:
```groovy
> def x
> def y = x ?: 7
7
> y ?: 8
7
```

## Other Assignment Operators

Many operators also have assignment versions that perform the operation and then assign the result to the
left-hand side.
The corresponding assignment operator is the original operator followed immediately (no spaces) by the `=` sign.
For example, the assignment operator for `+` is `+=`.
An expression such as `x += 5` is just shorthand for `x = x + 5`.

The full list of assignment operators is:

> `+=` `-=` `*=` `/=` `%=` `%%=` `<<=` `>>=` `>>>=` `&=` `&#124;=` `^=`  

For example:
```groovy
> def x = 5
5
> x += 2 * x
15
> x
15
> x /= 3
5
> x %= 3
2
> x <<= 4
32
> x |= 15
47
```

## Instance Of

The `instanceof` and `!instanceof` operators allow you to check if an object is an instance (or not an instance) of a
specific type.
The type can be a builtin type like `int`, `String`, `List`, etc. or can be a user defined class:
```groovy
> def x = 1
1
> x instanceof int
true
> x !instanceof String
true
> x = [a:1, b:[c:[1,2,3]]]
[a:1, b:[c:[1, 2, 3]]]
> x.b instanceof Map
true
> x.b.c instanceof List
true
> class X { int i = 1 }
> x = new X()
[i:1]
> x instanceof X
true
```

## Type Casts

In Java, type casting is done for two reasons:
1. You are passed an object of some generic type but you know it is actually a sepcific type and you want to treat it as that specific type (to invoke a method on it, for example), or
2. You need to convert a primitive number type to another number type (for example, converting a long value to an int)
 
In Jacsal there is less need to cast for the first reason since if the object supports the method you can always invoke
that method even if the reference to the object is a `def` type.
The reason why you may still wish to cast to the specific type in this case is for readability to make it clear what type
is expected at that point in the code or for performance reasons since after the value has been cast to the desired
type the compiler then can use a more efficient means of invoking methods and other operations on the object.

A type cast is done by prefixing `(type)` to an expression where _type_ is the type to cast to. Type can be any builtin
type or any user defined class.

For example we could check the type of `x` before invoking the List method `sum()` on it:
```groovy
> def x = [1,2,3]
[1, 2, 3]
> def sum = x instanceof List ? ((List)x).sum() : 0
6
```
Whereas in Java the cast would be required, since Jacsal supports dynamic typing, the cast in this case is not
necessary (but might make the execution slightly faster).

The other use for casts is to convert primitive number types to one another.
For example, you can use casts to convert a double or decimal value to its corresponding integer representation 
(discarding anything after the decimal point):
```groovy
> def hoursOwed = 175.15
175.15
> def hoursPerDay = 7.5
7.5
> def daysOwed = (int)(hoursOwed / hoursPerDay)
23
```

The other special case for cast is to cast a character to an integer value to get its Unicode value.
Remember that characters in Jacsal are just single character strings so if you cast a single character string to
`int` you will get is Unicode value:
```groovy
> (int)'A'
65
```

## As Operator

The `as` operator is used to convert values from one type to another (where such a conversion is possible).
The operator is an infix operator where the left-hand side is the expression to be converted and the right-hand side
is the type to conver to.
It is similar to a type cast and can be used to do that same thing in some circumstances:
```groovy
> (int)3.6
3
> 3.6 as int
3
```

You can use `as` to convert a string representation of a number into the number:
```groovy
> '123' as int
123
> '123.4' as Decimal
123.4
```

You can use it to convert anything to a string:
```groovy
> 123 as String
123
> [1,2,3] as String
[1, 2, 3]
```

Of course, you can do the same thing with the builtin `toString()` method as well:
```groovy
> 123.toString()
123
> [1,2,3].toString()
[1, 2, 3]
```

The `as` operator can convert between Lists and Maps (as long as such a conversion makes sense):
```groovy
> [a:1,b:2] as List
[['a', 1], ['b', 2]]
> [['x',3],['y',4]] as Map
[x:3, y:4]
```

It is also possible to use `as` to convert between objects of user defined classes and Maps (see section on Classes):
```groovy
> class Point{ int x; int y }
> def x = [x:3, y:4] as Point
[x:3, y:4]
> x instanceof Point
true
> x as Map
[x:3, y:4]
```

## In Operator

The `in` and `!in` operators are used to test if an object exists or not within a list of values.
For example:
```groovy
> def country = 'France'
France
> country in ['Germany', 'Italy', 'France']
true
> def myCountries = ['Germany', 'Italy', 'France']
['Germany', 'Italy', 'France']
> country !in myCountries
false
```

This operator works by iterating through the list and comparing each value until it finds a match so if the list of values is particularly
large then this will not be very efficient.

The `in` and `!in` operators also work on Maps and allow you to check if a key exists in the Map:
```groovy
> def m = [abc:1, xyz:2]
[abc:1, xyz:2]
> 'abc' in m
true
> 'xyz' !in m
false
```

## Field Access Operators

The `.` `?.` `[]` and `?[]` operators are used for accessing fields of Maps and class objects while the `[]` and `?[]`
are also used to access elements of Lists.
The `?` form of the operators will return null instead of producing an error if the List/Map/object is null.

For example:
```groovy
> Point p = new Point(x:1, y:2)
[x:1, y:2]
> p.x
1
> p['x']
1
> p = null
> p?.x == null
true
> p?['x'] == null
true
> [abc:1].('a'+'bc')
1
> [abc:1]?.('a'+'bc')
1
> [abc:1]?.x?.y?.z == null
true
```

# Regular Expressions

## Regex Matching

Jacsal provides two operators for doing regular expression (regex) find and regex subsitutions.
The `=~` operator is used for matching a string against a regex pattern.
It does the equivalent of Java's `Matcher.find()` to check if a substring matches the given pattern.
The regex pattern syntax is the same as that used by the Pattern class in Java so for detail information about how to
use regular expressions and what is supported see the Javadoc for the Pattern class.

Some examples:
```groovy
> 'abc' =~ 'b'
true
> 'This should match' =~ '[A-Z][a-z]+ sho.l[a-f]'
true
```

Regular expression patterns are usually expressed in pattern strings to cut down on the number of backslashes needed.
For example `/\d\d\s+[A-Z][a-z]*\s+\d{4}/` is easier to read and write than `'\\d\\d\\s+[A-Z][a-z]*\\s+\\d{4}'`:
```groovy
> '24 Mar 2014' =~ '\\d\\d\\s+[A-Z][a-z]*\\s+\\d{4}'   // pattern written as standard string
true
> '24 Mar 2014' =~ /\d\d\s+[A-Z][a-z]*\s+\d{4}/        // same pattern written as pattern string
true
```

The `!~` operator tests that the string does not match the pattern:
```groovy
> '24 Mar 2014' !~ /\d\d\s+[A-Z][a-z]*\s+\d{4}/
false
```

## Modifiers

When using a regex string (a string delimited with `/`) you can append modifiers to the regex pattern to control the
behaviour of the pattern match.
For example by appending `i` you can make the pattern match case-insensitive:
```groovy
> 'This is a sentence.' =~ /this/
false
> 'This is a sentence.' =~ /this/i
true
```

More than on modifier can be appended and the order of the modifiers is unimportant.
The supported modifiers are:

| Modifier | Description                                                                                                                                                                      |
|:--------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|    i     | Pattern match will be case-insensitive.                                                                                                                                          |
|    m     | Multi-line mode: this makes `^` and `$` match beginning and endings of lines rather than beginning and ending of entire string.                                                  |
|    s     | Dot-all mode: makes `.` match line terminator (by default `.` won't match end-of-line characters).                                                                               |
|    g     | Global find: remembers where previous pattern occurred and starts next find from previous location. This can be used to find all occurences of a pattern within a string.        |
|    r     | Regex match: this forces the pattern string to be interpreted as a regex match for implicit matching (see below). For substitutions this makes the substitution non-destructive. |

The `g` modifier for global find can be used to iterate over a string, finding all instances of a given pattern within
the string:
```groovy
> def str = 'This Example Text Is Not Complex'
This Example Text Is Not Complex
> int i = 0
0
> while (str =~ /Ex/ig) { i++ }   // count how often pattern matches
> i
3
```

Note that the 'g' modifier on a regex match is only supported within the condition of a while or for statement.
If you try to use it in any other context you will get an error:
```groovy
> def x = 'abc'
abc
> x =~ /ab/g
Cannot use 'g' modifier outside of condition for while/for loop @ line 1, column 6
x =~ /ab/g
^
```

The other limitation is that only one occurrence of a pattern with a 'g' modifier can appear within
the condition expression.
Other regex matches can be present in the condition expression as long as they don't use the 'g' global modifier:
```groovy
> def x = 'abc'; def y = 'xyz'; def i = 0
0
> while (x =~ /[a-c]/g && y =~ /[x-z]/g) { i++ }
Regex match with global modifier can only occur once within while/for condition @ line 1, column 30
while (x =~ /[a-c]/g && y =~ /[x-z]/g) { i++ }
        ^
> while (x =~ /[a-c]/g && y =~ /[x-z]/) { i++ }
> i
3
```

These limitations are due to what makes sense in terms of the capture variable values and due to the way in which
Jacsal saves the state of the match to remember where to continue from the next time.

The `g` modifier is more useful when used with capture variables as described in the next section.

## Capture Variables

With regex patterns you can provide capture groups using `(` and `)`.
These groupings then create _capture_ _variables_ `$1` `$2` etc. for each of the capture groups that correspond
to the characters in the source string that match that part of the pattern.
There is also a `$0` variable that captures the portion of the source string that matches the entire pattern.
For example:
```groovy
> def str = 'This Example Text Is Not Complex'
This Example Text Is Not Complex
> str =~ /p.*(.ex.).*(N[a-z].)/
true
> $1
Text
> $2
Not
> $0
ple Text Is Not
```

Note that if there are nested groups the capture variables numbering is based on the number of `(` so far encountered
irrespective of the nesting.

You can use `\` to escape the `(` and `)` characters if you want to match against those characters.

Using capture variables with the `g` global modifier allows you to extract all parts of a string that match:
```shell
> def data = 'AAPL=$151.03, MSFT=$255.29, GOOG=$94.02'
AAPL=$151.03, MSFT=$255.29, GOOG=$94.02
> def stocks = [:]
[:]
> while (data =~ /(\w*)=\$(\d+.\d+)/g) { stocks[$1] = $2 as Decimal }
> stocks
[AAPL:151.03, MSFT:255.29, GOOG:94.02]
```

## Regex Substitution

Jacsal supports regex substitution where substrings in the source string that match the given pattern are replaced
with a supplied substitution string.
The same regex match operator `=~` is used but the right-hand side now has the form `s/pattern/subst/mods` where
`pattern` is the regex pattern, `subst` is the substitution string, and `mods` are the optional modifiers:
```groovy
> def x = 'This is the original string'
This is the original string
> x =~ s/the/not an/
This is not an original string
> x
This is not an original string
```
Notice that the value on the left-hand side is modified so this form cannot be applied to string literals since they
cannot be modified:
```groovy
> 'this is the string' =~ s/the/not a/
Invalid left-hand side for '=~' operator (invalid lvalue) @ line 1, column 2
'this is the string' =~ s/the/not a/
 ^
```

If you want to perform the substitution and get the new string value without altering the left-hand side then use
the `r` modifier to perform a non-destructive replacement:
```groovy
> 'this is the string' =~ s/the/not a/r
this is not a string
```

The other modifiers supported work the same way as for a regex match (see above): `i` forces the match to be case-insensitve,
`m` is for multi-line mode, `s` allows `.` to match line terminators, and `g` changes all occurrences of the pattern
in the string.

For example, to change all upper case letters to `xxx`:
```groovy
>  'This SentenCe has Capital letTErs' =~ s/[A-Z]/xxx/rg
xxxhis xxxentenxxxe has xxxapital letxxxxxxrs
```

Both the pattern and the substitution strings are expression strings so expressions are allowed within the strings:
```groovy
> def x = 'A-Z'; def y = 'y'
y
> 'This SentenCe has Capital letTers' =~ s/[$x]/${y * 3}/rg
yyyhis yyyentenyyye has yyyapital letyyyers
```

Furthermore, capture variables are supported in the substitution string to allow expressions using parts of the
source string.
For example to append the size of each word to each word in a sentence:
```groovy
> 'This SentenCe has Capital letTers' =~ s/\b(\w+)\b/$1[${$1.size()}]/rg
This[4] SentenCe[8] has[3] Capital[7] letTers[7]
```

## Implicit Matching and Substitution

For both regex matches and regex subsitutions, if no left-hand side is given, Jacsal will assume that the match or
the substitution should be done against the default `it` variable.
This variable is the default variable passed in to _closures_ (see later) when no variable name is specified.

The following example takes some input, splits it into lines, and then uses `filter` with a regex to filter the lines
that match `/Evac.*Pause/` and then uses `map` to perform a regex substitute to transform these lines into a different
form: 
```groovy
> def data = '''[251.993s][info][gc] GC(281) Pause Young (Normal) (G1 Evacuation Pause) 2486M->35M(4096M) 6.630ms
  [252.576s][info][gc] GC(282) Pause Young (Concurrent Start) (Metadata GC Threshold) 1584M->34M(4096M) 10.571ms
  [252.576s][info][gc] GC(283) Concurrent Cycle
  [252.632s][info][gc] GC(283) Pause Remark 48M->38M(4096M) 49.430ms
  [252.636s][info][gc] GC(283) Pause Cleanup 45M->45M(4096M) 0.065ms
  [252.638s][info][gc] GC(283) Concurrent Cycle 62.091ms
  [253.537s][info][gc] GC(284) Pause Young (Normal) (G1 Evacuation Pause) 2476M->25M(4096M) 5.818ms
  [254.453s][info][gc] GC(285) Pause Young (Normal) (G1 Evacuation Pause) 2475M->31M(4096M) 6.040ms
  [255.358s][info][gc] GC(286) Pause Young (Normal) (G1 Evacuation Pause) 2475M->31M(4096M) 5.070ms
  [256.272s][info][gc] GC(287) Pause Young (Normal) (G1 Evacuation Pause) 2477M->34M(4096M) 5.024ms'''
> data.lines().filter{ /Evac.*Pause/r }.map{ s/^\[([0-9.]+)s\].* ([0-9.]*)ms$/At $1s: Pause $2ms/ }.each{ println it }
At 251.993s: Pause 6.630ms
At 253.537s: Pause 5.818ms
At 254.453s: Pause 6.040ms
At 255.358s: Pause 5.070ms
At 256.272s: Pause 5.024ms
```

See later sections on _closures_, and _collections_, for a description of how the methods like `filter()` and `map()`
and `each()` work with closures and with the implicit `it` variable.

You can, of cource, define your own `it` variable and have it automatically matched against:
```groovy
> def it = 'my value for my xplictily defined it variable'
my value for my explictily defined it variable
> /MY/i        // same as writing it =~ /MY/i
true
> s/MY/a random/ig
a random value for a random explictily defined it variable
> it
a random value for a random explictily defined it variable
```

Note, for matching, there needs to be a modifier since a regex pattern string delimited by `/` with no modifiers is
treated as a string in Jacsal.
To force the regex pattern to do a match against the `it` variable add a modifier.
The `r` modifier can be used to force a regex match if no other modifier makes sense:
```groovy
> def it = 'abc'
abc
> /a/             // just a string
a
> /a/r            // force a regex match
true
```

If there is no `it` variable in the current scope you will get an error:
```groovy
> s/MY/a random/ig
Reference to unknown variable 'it' @ line 1, column 1
s/MY/a random/ig
^
```

## Split Method

The `split()` method is another place where regex patterns are used.
Split operates on strings to split them into their constituent parts where the separator is based on a supplied
regex pattern:
```groovy
> '1, 2,3,  4'.split(/\s*,\s*/)    // split on comma ignoring whitespace
['1', '2', '3', '4']
```
The result of the split invocation is a list of the substrings formed by splitting the string whenever the pattern
is encountered.

Unlike regex matches and substitutions, modifiers are optionally passed as the second argument to the method:
```groovy
'1abc2Bad3Dead'.split(/[a-d]+/,'i')
['1', '2', '3', 'e', '']
```

Note that if the pattern occurs at the end of the string (as in the example) then an empty string will be created as
the last element in the resulting list.
Similarly, if the pattern occurs at the start of the string, an empty string will be the first element in the result
list.

If more than one modifier is needed then they are passed as a string (e.g. `'ism'` or `'ms'`, etc.).
The order of the modifiers is unimportant.

The only modifiers supported for `split` are `i` (case-insensitive), `s` (dot matches line terminators), and
`m` (multi-line mode).)

# If/Else Statements

Jacsal `if` statements work in the same way as Java and Groovy `if` statements.
The syntax is: 
> `if (<cond>) <trueStmts> [ else <falseStmts> ]`

The `<cond>` condition will be evalauted as `true` or `false` based on the [truthiness](#Truthiness) of the expression.
If it evaluates to `true` then the `<trueStmts>` will be executed.
If it evaulates to `false` then the `<falseStmts>` will be executed if the `else` clause has been provided.

The `<trueStmts>` and `<falseStmts>` are either single statements or blocks of multiple statements wrapped in `{}`.

For example:
```groovy
> def x = 'Fred Smith'
'Fred Smith'
> if (x =~ /^[A-Z][a-z]*\s+[A-Z][a-z]*$/ and x.size() > 3) println "Valid name: $x"
Valid name: Fred Smith
```

Since the Jacsal REPL will execute code as soon as a valid statement is parsed, it is difficult to show some multi-line
examples by capturing REPL interactions so here is an example Jacsal script showing a multi-line `if/else`: 
```groovy
def x = 'abc'
if (x.size() > 3) {
 x = x.substring(0,3)
}
else {
 x = x * 2
}

println x             // should print: abcabc
```

It is possible to string multiple `if/else` statements together:
```groovy
def x = 'abc'
if (x.size() > 3) {
 x = x.substring(0,3)
}
else
if (x.size() == 3) {
  x = x * 2
}
else {
 die "Unexpected size for $x: ${x.size()}"
}
```

# If/Unless Statements

In addition to the `if/else` statements, Jacsal supports single statement `if` and `unless` statements where the
test for the condition comes at the end of the statement rather than the beginning.
The syntax is:
> `<statment> if <cond>`

There is no need for `()` brackets around the `<cond>` expression.
For example:
```groovy
> def x = 'abc', y = 'xyz'
xyz
> x = x * 2 and y = "Y:$x" if x.size() < 4
true
> x
abcabc
> y
Y:abcabc
```

As well as supporting tests with `if`, Jacsal supports using `unless` which just inverts the test.
The statement is executed unless the given condition is true:
```groovy
> def x = 'abc'
xyz
> x = 'xxx' unless x.size() == 3
> x
abc
> die "x has invalid value: $x" unless x.size() == 3
```

# While Loops

Jacsal `while` loops work like Java and Groovy:
> `while (<cond>) <statement>`

The `<statement>` can be a single statement or a block of statements wrapped in `{}`.
The statement or block of statements is repeatedly executed while the condition is `true`.

For example:
```groovy
int i = 0, sum = 0
while (i < 5) {
  sum += i
  i++
}
die if sum != 10
```

## Break and Continue

Like Java, `break` can be used to exit the while loop at any point and `continue` can be used to goto the next
iteration of the loop before the current iteration has completed:
```groovy
int sum = 0
int i = 0
while (i < 10) {
  sum += i
  break if sum >= 20    // exit loop once sum is >= 20
  i++
}

die unless sum == 21 && i == 6 
```

Another example using `continue`:
```groovy
int sum = 0
int i = 0
while (i < 100) {
  i++
  continue unless i % 3 == 0     // only interested in numbers that are multiples of 3   
  sum += i
}

die unless sum == 1683
```

# For Loops

A `for` loop is similar to a `while` loop in that it executes a block of statements while a given condition is met.
It additionally has some initialisation that is run before the loop and some statements that are run for every loop
iteration.
The syntax is:
> `for (<init>; <cond>; <update>) <statement>`

The `<statement` can be a single statement or a block of statements surrounded by `{}`.

The `<init>` initialiser is a single declaration which can declare a list of variables of the same type with
(optionally) initial values supplied.

The `<cond>` is a condition that is evaulated for [truthiness](#Truthiness) each time to determine if the loop
should execute.

The `<update>` is a comma separated list of (usually) assignment-like expressions that are evaluated each time
an iteration of the loop completes.

The canonical example of a `for` loop is to loop a given number of times:
```groovy
int result
for (int i = 0; i < 10; i++) {
  result += i if i % 3 == 0
}

die unless result == 18
```

Here is an example with multiple initialisers and updates:
```groovy
int result = 0
for (int i = 0, j = 0; i < 10 && j < 10; i++, j += 2) {
 result += i + j
}
die unless result == 30 
```

As for `while` statements, a `for` loop supports `break` to exit the loop and `continue` to continue with the next
iteration.
When `continue` is used the `<update>` statements are executed before the condition is re-evaluated.

Note that any or all of the `<init>` `<cond>` and `<update>` sections of the `for` loop can be empty:
```groovy
int i = 0, sum = 0
for(; i++ < 10; ) {    // empty initialiser and update section
 sum += i
}
die unless sum == 55 
```

If all sections are empty then it is the same as using `while (true)` and will iterate until a `break` statement
exits the loop:
```groovy
int i = 0, sum = 0
for (;;) {
 sum += ++i
 break if i >= 10
}
die unless sum == 55 
```

# Labels for While/For Statements

With `while` or `for` loops you can always break out of or continue the current loop using `break` or `continue`.
Jacasal also allows you to break out of or continue an outer loop by labelling the loops and using the label in the
`break` or `continue`.

Labels are a valid name followed by `:` and can be attached to a `while` or `for` statement if they occur immediately
before the loop:
```groovy
int sum = 0
OUTER: for (int i = 0; i < 10; i++) {
  int j = 0
  INNER: while (true) {
    sum += ++j
    continue OUTER if j > i
    break    OUTER if sum > 30 
  }
}
die unless sum == 36 
```

Label names are any valid identifier (letter or underscore )

# Do statements

# Print Statements

# Die Statements

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

# Command Line Invocation
{: #commandline}

# TODO
* multi-value return from functions
* named arg passing
* passing list as list of args
* lazy vs non-lazy iteration (Java vs Groovy)
** force non-lazy by using collect()
* Classes
** no static state in classes and no static initialiser blocks either (for same reason)
** casting
* do{}
* if/unless single statements
* regex match/substitute
** use with implicit it
** capture vars
* implicit it
* builtin functions and methods
* functions as values
* 