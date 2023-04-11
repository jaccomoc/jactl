---
layout: page
title: "Jactl Language Guide"
permalink: /language-guide
---

<!-- To generate this list: jactl -pe '/^## /r or continue; s/## //; it = "* [$it](#${it.toLowerCase() =~ s/ /-/gr =~ s/[^\w-]//gr})"' language-guide.md -->
* [The REPL](#the-repl)
* [Command Line Scripts](#command-line-scripts)
* [Statement Termination: Newlines and Semicolons](#statement-termination-newlines-and-semicolons)
* [Comments](#comments)
* [Variables](#variables)
* [Keywords](#keywords)
* [Types](#types)
* [Truthiness](#truthiness)
* [Expressions and Operators](#expressions-and-operators)
* [Regular Expressions](#regular-expressions)
* [Eval Statement](#eval-statement)
* [If/Else Statements](#ifelse-statements)
* [If/Unless Statements](#ifunless-statements)
* [While Loops](#while-loops)
* [For Loops](#for-loops)
* [Labels for While/For Statements](#labels-for-whilefor-statements)
* [Do Expression](#do-expression)
* [Print Statements](#print-statements)
* [Die Statements](#die-statements)
* [Functions](#functions)
* [Closures](#closures)
* [Collection Methods](#collection-methods)
* [Classes](#classes)
* [Built-in Global Functions](#builtin-global-functions)
* [Built-in Methods](#builtin-methods)

## The REPL

The easiest way to start learning about the Jactl language is to use the REPL (read-evaluate-print-loop) that comes
with Jactl.
It gives you a `>` prompt where you can enter Jactl code for immediate execution.

```shell
$ java -cp jactl-repl-{{ site.content.jactl_version }}.jar jactl.Repl
> int x = 3 + 4
7
```

To exit the REPL use the `:q` command or `ctrl-D`.

Anything that does not start with `:` is interpreted as Jactl code and is evaluated immediately and the result
of the evaluation is printed before prompting again (hence the term read-evaluate-print-loop). For each line that 
is read, if the code does not look like it is complete the REPL will keep reading until it gets a complete
expression or statement that can be executed:
```groovy
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

See [Jactl REPL](https://github.com/jaccomoc/jactl-repl) for more details on how to run the REPL.

## Command Line Scripts

Jactl scripts can also be invoked from the command line:
```shell
$ java -jar jactl-{{ site.content.jactl_version }}.jar
Usage: jactl [switches] [programFile] [inputFile]* [--] [arguments]*
         -p           : run in a print loop reading input from stdin or files
         -n           : run in a loop without printing each line
         -e script    : script string is interpreted as Jactl code (programFile not used)
         -v           : show verbose errors (give stack trace)
         -V var=value : initialise Jactl variable before running script
         -d           : debug: output generated code
         -h           : print this help
```

Using the `-e` option allows you to supply the script on the comand line itself:
```shell
$ java -jar jactl-{{ site.content.jactl_version }}.jar -e '3 * 4'
12
```

If you have a Jactl script in a file called `myscript.jactl` then you can the script like this:
```shell
$ java -jar jactl-{{ site.content.jactl_version }}.jar myscript.jactl
```

See [Command Line Scripts](command-line-scripts.md) for more details about how to invoke scripts
from the command line.

## Statement Termination: Newlines and Semicolons

Jactl syntax borrows heavily from Java, Groovy, and Perl and so while Java and Perl both require a semicolon to terminate
simple statements, Jactl adopts the Groovy approach where semicolons are optional.
The only time a semicolon is required as a statement separator is if more than one statement appears on the
same line:
```groovy
> int x = 1; println 'x = ' + x; println 'x is 2' if x == 2
x = 1
```

The Jactl compiler, when it encounters a newline, will try to see if the current expression or statement continues
on the next line.
Sometimes it can be ambiguous whether the newline terminates the current statement or not and in these
situations, Jactl will treat the newline is a terminator. For example, consider this:
```groovy
def x = [1,2,3]
x
[0]
```
Since expressions on their own are valid statements, it is not clear whether the last two lines should be interpreted
as `x[0]` or `x` and another statement `[0]` which is a list containing `0`.
In this case, Jactl will compile the code as two separate statements.

If such a situation occurs within parentheses or where it is clear that the current statement has not finished,
Jactl will treat the newline as whitespace. For example:
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

## Comments

Comments in Jactl are the same as for Java and Groovy and are denoted using either line comment form, where `//` is
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

## Variables

A variable in Jactl is a symbol that is used to refer to a value. Variables are declared by specifying a type followed
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

### Valid Variable Names

Variable names must be a valid _identifier_. Identifiers start with a letter or with an underscore `_` followed
by a combination of letters, digits and underscores. The only special case is that a single underscore `_` is not a
valid identifier.

Variable names cannot clash with any built-in language [keywords](/language-guide/#keywords):
```groovy
> int for = 3
Unexpected token 'for': Expecting identifier @ line 1, column 5
int for = 3
    ^
```

> **Note**<br/>
> Since class names in Jactl must start with a capital letter it is good practice not
> to start variable names with a capital letter in order to make your code easier to read.

> **Note**<br/>
> Unlike Java, the dollar sign `$` is not a valid character for a variable name in Jactl.

While most of the examples presented here have single character variable names, in general, your code should use
names that are more meaningful. It is recommended that when a variable name consists of more than one word that
_camel case_ is used to indicate the word boundaries by capitalizing the first letter of following words.
For example:
* firstName
* accountNumber
* emailAddress

## Keywords

The following table list the reserved keywords used by Jactl:

| Key Words  |          |             |          |             |
|------------|----------|-------------|----------|-------------|
| BEGIN      | Decimal  | END         | List     | Map         |
| String     | and      | as          | boolean  | break       |
| class      | continue | def         | die      | do          |
| double     | else     | extends     | false    | final       |
| for        | if       | implements  | import   | in          |
| instanceof | int      | interface   | long     | new         |
| not        | null     | or          | package  | print       |
| println    | return   | sealed      | static   | true        |
| unless     | var      | void        | while    |             |


## Types

### Standard Types

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

### Inferred Type

Variables can also be declared using the `var` keyword if an initialiser expression is given.
Jactl will then create a variable of the same type as the initialisation expression:

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

### Dynamic/Weak Typing

While Jactl supports the use of specific types when declaring variables and functions, Jactl can also be used as
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

### Numbers

#### Integers

Jactl supports two types of integers: `int` and `long`.

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

#### Floating Point

In Jactl, by default, floating point numbers are represented by the `Decimal` type and numeric literals that have
a decimal point will be interpreted as Decimal numbers.

Decimal numbers are represented internally using the Java `BigDecimal` class. This avoids the problems
of trying to store base 10 numbers inexactly using a binary floating point representation.

Jactl also offers the ability to use native floating point numbers by using the type `double` (which corresponds to
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

### Strings

Strings in Jactl are usually delimited with single quotes:
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

#### Special Characters

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

#### Subscripts and Characters

Subscript notation can be used to access individual characters:
```groovy
> 'abc'[0]
a
```
Note that characters in Jactl are just strings whose length is 1. Unlike Java, there is no separate `char` type to
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

#### String Operators

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

#### Expression Strings

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
If the expression is surrounded in `{}` then any arbitrary Jactl expression can be included:
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

#### Pattern Strings

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

### Lists

A Jactl `List` represents a list of values of any type. Lists can have a mixture of types within them. Lists are
created using the `[]` syntax where the elements are a list of comma separated values:
```groovy
> []            // empty list
[]        
> [1,2,3]
[1, 2, 3]
> ['value1', 2, ['a','b']]
['value1', 2, ['a', 'b']]
```
The elements of a `List` can themseles be a `List` (as shown) or a `Map` or any type supported by Jactl (including
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

> **Note**<br/>
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

### Maps

A `Map` in Jactl is used hold a set of key/value pairs and provides efficient lookup of a value based on a given key
value.

> Unlike Java, Jactl `Map` objects only support keys which are strings. If you try to use an
> object that is not a `String` as a key, Jactl will throw an error.

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

#### Variable Value as Key
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

#### Map Addition
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

#### Map Subtraction
You can subtract from a Map to remove specific keys from the Map (see also [Map.remove()](#Map.remove())).
To subtract one map from the other:
```groovy
> [a:1,b:2,c:3] - [a:3,b:[1,2,3]]
[c:3]
```
This will produce a new Map where the entries in the first Map that match the keys in the second Map have been
removed.

Keys in the second Map that don't exist in the first Map will have no effect on the result:
```groovy
> [a:1,b:2,c:3] - [x:'abc']
[a:1, b:2, c:3]
```

You can also subtract a List of values from a Map where the List is treated as a list of keys to be removed:
```groovy
> [a:1,b:2,c:3] - ['a','c']
[b:2]
```

#### JSON Syntax
Jactl also supports JSON-like syntax for maps. This makes it handy if you want to cut-and-paste some JSON into your
Jactl script:
```groovy
> {"name":"Fred Smith", "employeeId":1234, "address":{"street":["Apartment 456", "123 High St"], "town":"Freetown"} }
[name:'Fred Smith', employeeId:1234, address:[street:['Apartment 456', '123 High St'], town:'Freetown']]
```

#### toString(indent)
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
> The `toString()` Jactl function outputs values in a form that is legal, executable
> Jactl code, which is useful for cut-and-pasting into scripts and when working with the REPL command line.

#### Map Field Access

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

#### Map Subscript Access

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

#### Auto-Creation

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
With Jactl these intermediate fields will be automatically created if they don't exist so we only need write
the last line of script:
```groovy
> x.a.b.c.d = 1
1
> x
[a:[b:[c:[d:1]]]]
```

Note that Jactl will not automatically create a value for the top level variable, however, if it does not yet
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
Jactl will assume that access via subscript notation implies that an empty List should be created if the field is
missing, rather than a Map:
```groovy
> def x = [:]
[:]
> x.a['b'] = 1
Non-numeric value for index during List access @ line 1, column 4
x.a['b'] = 1
   ^
```

#### in and !in Operators

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

## Truthiness

In Jactl we often want to know whether an expression is `true` or not. The _truthiness_ of an expression is used to
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

## Expressions and Operators

### Operator Precedence

Jactl supports the following operators. Operators are shown in increasing precedence order and operators of the same
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
|                  |         `&=` `\|=` `^=`         | Bitwise assignment operators                        |
|        5         |             `? :`              | Ternary conditional opeartor                        |
|                  |              `?:`              | Default value operator                              |
|        6         |        `\|\|`                  | Boolean or                                          |
 |        7        |              `&&`              | Boolean and                                         |
 |        8        |            `\|`                | Bitwise or                                          |
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

When evaluating expressions in Jactl operators with higher precedence are evaluated before operators with lower
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

### Assignment and Conditional Assignment

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

### Basic Arithmetic Operators

The standard arithmetic operators `+`, `-`, `*`, `/` are supported for addition, subtraction, multiplication, and
division:
```groovy
> 3 * 4 + 6 / 2 + 5 - 10
10
```
Remember that `*` and `/` have higher precedence and are evaluated before any addition or subtraction. 

### Prefix `+` and `-`

The `+` and `-` operators can also be used as prefix operators:
```groovy
> -(3 - 4)
1
> +(3 - 4)
-1
```
The `-` prefix operator negates the value following expression while the `+` prefix operator does nothing but exists
to correspond to the `-` case so that things like `-3` and `+3` can both be written.

### Bitwise Operators

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

### Shift Operators

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

### Modulo `%` and Remainder `%%` operators

In addition to the basic four operators, Jactl also has `%` (modulo) and `%%` (remainder) operators.
Both operators perform similar functions in that they calculate the "remainder" after dividing by some number.
The difference comes from how they treat negative numbers.

The remainder operator `%%` works exactly the same way as the Java remainder operator (which in Java is represented
by a single `%` rather than the `%%` in Jactl):
> `x %% y` is defined as being `x - (int)(x/y) * y`

The problem is if `x` is less than `0` then the result will also be less than 0:
```groovy
> -5 %% 3
-2
```

When doing modulo arithmetic you usually want (in my opinion) values to only be between `0` and `y - 1` when evaluating
something modulo `y` (where `y` is postive) and between `0` and `-(y - 1)` if `y` is negative.

Jactl, therefore, the definition of `%` is:
> `x % y` is defined as `((x %% y) + y) %% y`

This means that in Jactl `%` returns only postive values if the right-hand side is positive and only negative values
if the right-hand side is negative.
Jactl retains `%%` for scenarios where you want compatibility with how Java does things or for when you know that
the left-hand side will always be positive and you care about performance (since `%%` compiles to a single JVM
instruction while `%` is several instructions).

### Increment/Decrement Operators

Jactl offers increment `++` and decrement `--` operators that increment or decrement a value by `1`.
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

### Comparison Operators

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

### Comparator Operator

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

### Logical/Boolean Operators

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

### Conditional Operator

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

### Default Value Operator

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

### Other Assignment Operators

Many operators also have assignment versions that perform the operation and then assign the result to the
left-hand side.
The corresponding assignment operator is the original operator followed immediately (no spaces) by the `=` sign.
For example, the assignment operator for `+` is `+=`.
An expression such as `x += 5` is just shorthand for `x = x + 5`.

The full list of assignment operators is:

> `+=` `-=` `*=` `/=` `%=` `%%=` `<<=` `>>=` `>>>=` `&=` `|=` `^=`  

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

### Instance Of

The `instanceof` and `!instanceof` operators allow you to check if an object is an instance (or not an instance) of a
specific type.
The type can be a built-in type like `int`, `String`, `List`, etc. or can be a user defined class:
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

### Type Casts

In Java, type casting is done for two reasons:
1. You are passed an object of some generic type but you know it is actually a sepcific type and you want to treat it as that specific type (to invoke a method on it, for example), or
2. You need to convert a primitive number type to another number type (for example, converting a long value to an int)
 
In Jactl there is less need to cast for the first reason since if the object supports the method you can always invoke
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
Whereas in Java the cast would be required, since Jactl supports dynamic typing, the cast in this case is not
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
Remember that characters in Jactl are just single character strings so if you cast a single character string to
`int` you will get is Unicode value:
```groovy
> (int)'A'
65
```

### As Operator

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

Of course, you can do the same thing with the built-in `toString()` method as well:
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

### In Operator

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

### Field Access Operators

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

## Regular Expressions

### Regex Matching

Jactl provides two operators for doing regular expression (regex) find and regex subsitutions.
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

### Modifiers

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
|    g     | Global find: remembers where previous pattern occurred and starts next find from previous location. This can be used to find all occurrences of a pattern within a string.       |
|    n     | If a capture group is numeric then interpret as a number.                                                                                                                        |
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

These restrictions are due to what makes sense in terms of how capture variables work (see below) and due to the way in which
Jactl saves the state of the match to remember where to continue from the next time.

The `g` modifier is more useful when used with capture variables as described in the next section.

### Capture Variables

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

The `n` modifier will make the capture variable a number if the string captured is numeric.
If the number has no decimal place then it will be converted to a `long` value (as long as the value is not too large).
If it has a decimal point then it will become a `Decimal` value.
For example:
```groovy
> 'rate=-1234' =~ /(\w+)=([\d-]+)/n
true
> $1
rate
> $2
-1234
> $2 instanceof long
true
> 'rate=56.789' =~ /(\w+)=([\d-.]+)/n
true
> $2
56.789
> $2 instanceof Decimal
true
```

### Regex Substitution

Jactl supports regex substitution where substrings in the source string that match the given pattern are replaced
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

### Implicit Matching and Substitution

For both regex matches and regex substitutions, if no left-hand side is given, Jactl will assume that the match or
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

You can, of course, define your own `it` variable and have it automatically matched against:
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
treated as a string in Jactl.
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

### Split Method

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

## Eval Statement

The `eval()` statement can be used to evaluate arbitrary Jactl code.
It takes the string passed to it and compiles and runs it.
The return value is the value of the last expression in the script:
```groovy
> eval('def f(n) { n == 1 ? 1 : n * f(n-1)}; f(5)')
120
```

You can return any type of value including a function or closure:
```groovy
> def f = eval('def f(n) { n == 1 ? 1 : n * f(n-1)}')
Function@1894369629
> f(5)
120
```

You can pass an optional Map to the function if you want to prepopulate the values of some variables:
```groovy
> eval('def f(n) { n == 1 ? 1 : n * f(n-1)}; f(x)', [x:6])
720
```

You can also use the Map as a way to capture additional output from the script.
Top level variables in the script will become entries in the Map:
```groovy
> def vars = [x:6]
[x:6]
> eval('def f(n) { n == 1 ? 1 : n * f(n-1)}; def first10 = 10.map{ f(it+1) }; f(x)', vars)
720
> vars
[x:6, f:Function@445918232, first10:[1, 2, 6, 24, 120, 720, 5040, 40320, 362880, 3628800]]
```

Since it is compiled, there could be compile errors that prevent the script running.
In this case the return value will be null:
```groovy
> def vars = [x:6]
[x:6]        
> def result = eval('def f(n) { : n == 1 ? 1 : n * f(n-1)}; def first10 = 10.map{ f(it+1) }; f(x)', vars)
> result == null
true
```

If you passed in a Map then the `$error` entry in the Map will hold the compile error:
```groovy
> vars.'$error'
io.jactl.CompileError: Unexpected token ':': Expected start of expression @ line 1, column 12
def f(n) { : n == 1 ? 1 : n * f(n-1)}; def first10 = 10.map{ f(it+1) }; f(x)
           ^
```

Note that this is not the most efficient way to run Jactl code since it has to compile the code before it can run
it, so if it is to be used it should be used judiciously.

## If/Else Statements

Jactl `if` statements work in the same way as Java and Groovy `if` statements.
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

Since the Jactl REPL will execute code as soon as a valid statement is parsed, it is difficult to show some multi-line
examples by capturing REPL interactions so here is an example Jactl script showing a multi-line `if/else`: 
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

## If/Unless Statements

In addition to the `if/else` statements, Jactl supports single statement `if` and `unless` statements where the
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

As well as supporting tests with `if`, Jactl supports using `unless` which just inverts the test.
The statement is executed unless the given condition is true:
```groovy
> def x = 'abc'
xyz
> x = 'xxx' unless x.size() == 3
> x
abc
> die "x has invalid value: $x" unless x.size() == 3
```

## While Loops

Jactl `while` loops work like Java and Groovy:
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

### Break and Continue

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

## For Loops

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

## Labels for While/For Statements

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

Label names are any valid identifier (any combination of letters, digits, and underscore as long as first character
is not a digit and identifier is not a single underscore).
Label names can be the same as other variable names (although not recommended practice).

## Do Expression

Jactl has a `do` expression that allows you to execute a block of statements where normally an expression would be
expected.
For example:
```groovy
def commands = ['right 5', 'up 7', 'left 2', 'up 3', 'right 4']
int x, y, distance
commands.each{
  /up (\d*)/r    and do { y += $1 as int; distance += $1 as int }
  /down (\d*)/r  and do { y -= $1 as int; distance += $1 as int }
  /right (\d*)/r and do { x += $1 as int; distance += $1 as int }
  /left (\d*)/r  and do { x -= $1 as int; distance += $1 as int }
}

die unless [x, y, distance] == [7, 10, 21] 
```

A `do` expression always returns `true` so it can be chained with other boolean expressions:
```groovy
def x = 1
def found = false
while (true) {
  x == 1 and do { found = true; x++ } and break
}
```

You can comnbine `do` with `if/unless`:
```groovy
do { found = true; println "Found multiple of 17: $x" } if x % 17 == 0
```

> **Note**<br/>
> `do/while` statements like those in Java are not currently supported in Jactl.

## Print Statements

Jactl has `print` and `println` statements for printing a string.
`println` will print the string followed by a new-line.

For example:
```groovy
> for (int i = 1; i <= 5; i++) { println "$i squared is ${i*i}" }
1 squared is 1
2 squared is 4
3 squared is 9
4 squared is 16
5 squared is 25
```

The output will, by default, be printed to `System.out` (standard out) but can be directed elsewhere (for example
to a diagnostics log file).
See [Integration Guide](integration-guide.md) for more details.

## Die Statements

The `die` statement allows you to instantly exit a Jactl script with an optional error message.
It should be used only in situations where something unexpected has occurred and it makes no sense to continue.

## Functions

Jactl supports functions that take arguments and return a result.
Functions always return a result (even if it is just `null`) - there is no concept of `void` functions in Jactl.

Jactl supports the Java syntax for creating a function (although Java doesn't really have functions as everything
has to be a method).
For example this code is valid Jactl and valid Java:
```groovy
// Recursive implementation for Fibonacci numbers
int fib(int n) { 
  return n <= 2 ? 1 : fib(n - 1) + fib(n - 2); 
}
die unless fib(20) == 6765
```

In Jactl the return value of a function is the result of the last statement in the function so `return` is optional
if you are returning at the end of the function anyway.
Sometimes it makes it clearer what is going on and if you need to return a value from the middle of the function
it is obviously still useful.

Parameter types are optional (default type will be `def` meaning any type if not specified) and `def` can be used
as the return type for the function as well.

So with `return` and paramter type not needed and with semi-colon statement terminators being optional, the function
above could be rewritten in Jactl as:
```groovy
def fib(n) { n <= 2 ? 1 : fib(n - 1) + fib(n - 2) }

die unless fib(20) == 6765
```

Functions can take multiple parameters. Here is a function that joins pairs of strings in two lists with a given
separator and returns a list of the joined strings.
If one of the lists is shorter than the other then it uses an empty string for the missing elements:
```groovy
List joinStrings(List left, List right, String separator) {
  int  count  = left.size() > right.size() ? left.size() : right.size() 
  List result = []
  for (int i = 0; i < count; i++) {
    result <<= (left[i] ?: '') + separator + (right[i] ?: '')
  }
  return result
}

die unless joinStrings(['a','b','c'],['AA','BB'],':') == ['a:AA', 'b:BB', 'c:']
```

After reading the [section](#collections) on collections and functional programming you will see that this could
have been written more succinctly as:
```groovy
List joinStrings(List left, List right, String separator) {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

die unless joinStrings(['a','b','c'],['AA','BB'],':') == ['a:AA', 'b:BB', 'c:']
```

### Default Parameter Values

You can supply default values for parameters in situations where having a default value makes sense.
For example we could default the separator to `':'` in the function above:
```groovy
List joinStrings(List left, List right, String separator = ':') {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

die unless joinStrings(['a','b','c'],['AA','BB']) == ['a:AA', 'b:BB', 'c:']
```

Now we can leave out the third parameter because when not present it will be set to `':'`.

Default values can still be supplied even if the type is not given:
```groovy
def joinStrings(left, right, separator = ':') {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

die unless joinStrings(['a','b','c'],['AA','BB']) == ['a:AA', 'b:BB', 'c:']
```

If a default value is supplied then it is possible to define the parameter as a `var` parameter in which case it will
get its type from the type of the initialiser (as for variable declarations).
For example:
```groovy
def joinStrings(List left, var right = [], var separator = ':') {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

die unless joinStrings(['a','b','c'],['AA','BB']) == ['a:AA', 'b:BB', 'c:']
die unless joinStrings(['a','b']) == ['a:', 'b:']
```

It is also possible for the parameter default values to use values from other parameter values as long as they refer
only to earlier parameters:
```groovy
def joinStrings(List left, var right = left, var separator = ':') {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

die unless joinStrings(['a','b']) == ['a:a', 'b:b']
```

### Named Argument Invocation

It is possible to invoke functions using named arguments where the names and the argument values are given
as a list of colon separated pairs:
```groovy
def joinStrings(List left, var right = left, var separator = ':') {
  [left.size(), right.size()].max().map{ (left[it] ?: '') + separator + (right[it] ?: '') }
}

// Argument order is unimportant when using named args
die unless joinStrings(separator:'=', left:['a','b'], right:['A','B']) == ['a=A', 'b=B']
```

### Declaration Scope

Functions can be declared wherever it makes sense based on where they need to be invoked from.
Their visibility is based on the current scope in which they are declared, just like any variable.

For example, in the following script we define the function within the `for` loop where it is needed.
The function cannot be invoked from outside this `for` loop:
```groov
def getFactorials(n) {
  def result = []
  for (int i = 1; i <= n; i++) { 
    def fact(n) { n == 1 ? 1 : n * fact(n - 1) }
    result <<= fact(i)
  }
  return result
}

die unless getFactorials(10) == [1, 2, 6, 24, 120, 720, 5040, 40320, 362880, 3628800]
```

Again, this could be written more simply like this:
```groovy
def factorials(n) {
  def fact(n) { n == 1 ? 1 : n * fact(n - 1) }
  n.map{ fact(it+1) }
}

die unless factorials(10) == [1, 2, 6, 24, 120, 720, 5040, 40320, 362880, 3628800]
```

### Functions as Values

A function declaration can be treated as a value and assigned to other variables, passed as an argument to another
function, or returned as a value from a function.
For example:
```groovy
def fact(n) { n == 1 ? 1 : n * fact(n - 1) }
def g = fact          // simple assignment

die unless g(5) == 120
```

Another example showing functions being passed to another function that returns yet another function that composes
the two original functions:
```groovy
def compose(f, g) { def composed(x){ f(g(x)) }; return composed }
def sqr(x) { x * x }
def twice(x) { x + x }

def func = compose(sqr, twice)   // create new function that invokes sqr with result of twice

die unless func(3) == 36         // should be sqr(twice(3))
```

While you can assign functions to other variables, the function variable itself (the name of the function) cannot be
assigned to.

### Invocation With List of Arguments

Functions can be invoked with a single list supplying the argument values for the function (in the declared order).

For example:
```groovy
def f(a,b,c) { a + b + c }
def x = [1, 2, 3]
def y = ['x','abc','123']

die unless f(x) == 6
die unless f(y) == 'xabc123'   // since parameters are untyped we can pass strings and get string concatenation 
```

## Closures

Closures in Jactl are modelled after the closure syntax of Groovy.
Closures are similar to functions in that they take one or more parameters and return a result.
They are decalred with slightly different syntax:
```groovy
def sqr = { int x -> return x * x }        // Assign closure to sqr

die unless sqr(4) == 16
```

The syntax of a closure is an open brace `{` and then a list of parameters declarations followed by `->` and then
the actual code followed by a closing `}`.

Multiple parameters can be declared:
```groovy
def f = { String str, int x -> 
  String result
  for (int i = 0; i < x; i++) {
    result += str
  }
  return result
}

die unless f('ab', 3) == 'ababab'
die unless 'ab' * 3   == 'ababab'   // much simpler way of achieving the same thing
```

As for functions, the type is optional and default values can be supplied:
```groovy
def f = { str, x = 2 -> str * x } 

die unless f('ab', 3) == 'ababab'
die unless f('ab') == 'abab'
```

### Implicit `it` Parameter

It is common enough for a closure to have a single argument and Jactl follows the Groovy convention of creating an
implicit `it` parameter if there is no `->` at the start of the closure:
```groovy
def f = { it * it }

die unless f(3) == 9
```

### No Parameter Closures

If you want a closure that has no arguments use the `->` and don't list any parameters:
```groovy
def f = { -> "The current timestamp is ${timestamp()}" }

die unless f() =~ /The current timestamp is/
```

### Closure Passing Syntax

Closures can be passed as values like functions:
```groovy
def ntimes(n, f) {
  for (int i = 0; i < n; i++) {
    f(i)
  }
}
def clos = { x -> println x }

ntimes(5, clos)   // this will result in 0, 1, 2, 3, 4 being printed
```

It is possible to pass the closure itself directly to the function without having to store it in an intermediate
variable:
```groovy
def ntimes(n, f) {
  for (int i = 0; i < n; i++) {
    f(i)
  }
}

ntimes(5, { println it })     // use implicit it variable this time
```

If the last argument to a function is a closure then it can appear directly after the closing `)`:
```groovy
def ntimes(n, f) {
  for (int i = 0; i < n; i++) {
    f(i)
  }
}

ntimes(5){ println it }
```

There is a built-in methods on Lists, Maps, Strings, and numbers called `each`, `map`, etc. that takes a single argument
which is a closure.
For numbers, the `each` method iterates `n` times and passes each value from `0` to `n-1` to the closure.
For example:
```groovy
> 5.each({ x -> println x })
0
1
2
3
4
```

If the only argument being passed is a closure then the brackets are optional so this can be written like so:
```groovy
5.each{ x -> println x }
```

And since we can use the implicit `it` variable it can also be written like this:
```groovy
5.each{ println it }
```

Similarly, there is a `map` method on numbers (and Lists, Maps, and Strings) which maps the parameter value passed in
to another value calculated by the closure and returns that:
```groovy
> 5.map{
    it++
    it * it      // last expression in closure/function is return value
  }
[1, 4, 9, 16, 25]
```

Here is another example where we create a function for timing the execution of a passed in closure and get it to time
how long it takes to calculate the first 40 Fibonacci numbers.
(Note that the recursive Fibonacci function is a very inefficient way of calculating Fibonacci numbers - it is just used
as an example of something that will take some time to run.)
```groovy
def timeIt(f) {
  def start = timestamp()
  def result = f()
  println "Duration: ${timestamp() - start}ms,  result = $result"
}

// How long to calculate first 40 Fibonacci numbers
timeIt{
 int fib(int n) { n <= 2 ? 1 : fib(n-1) + fib(n-2) }
 40.map{ fib(it+1) }
}

// On my laptop this gives output of:
// Duration: 488ms,  result = [1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610,
// 987, 1597, 2584, 4181, 6765, 10946, 17711, 28657, 46368, 75025, 121393, 196418, 317811,
// 514229, 832040, 1346269, 2178309, 3524578, 5702887, 9227465, 14930352, 24157817, 39088169,
// 63245986, 102334155]
```

### Functions vs Closures

Consider the following:
```groovy
def f(x) { x + x }
def c  = { x -> x + x }
```

The function `f` and the closure assigned to `c` both do the same thing so what are the differences between a function
and a closure?

In the example above `c` is just a variable and can have other values assigned to it whereas `f` cannot be assigned to
since it is a function.
In fact, `c` is not a closure but happens to have a closure as its value until it is assigned a different value.

Closures have an implicit parameter `it` decalared for them if they don't define any expicit parameters.
This is not the case for functions. 

Functions can be invoked before they are declared (as long as they are in the same scope):
```groovy
int i = f(3)    // forward reference

def f(x) { x + x }
```

Since closures are anonymous values that can be assigned to variables the variable cannot be referenced before it
is declared and so it is not possible to invoke a closure via a forward reference.

Functions support recursion.
A function can invoke itself while a closure cannot refer to the variable to which it is being assigned if the
closure is the initialiser for the variable:
```groovy
def f(x) { x == 1 ? 1 : x + f(x - 1) }

def c = { it == 1 ? 1 : it + c(it - 1) }   // error: Variable initialisation cannot refer to itself
```

The closure could have been split into a declaration and an assignment to allow the recursion:
```groovy
def c
c = { it == 1 ? 1 : it + c(it - 1) }
```

This is not recommended, however, since there is no guarantee that `c` won't be reassigned later in the code.

Since functions support forward references it is possible to have mutually recursive functions (where each function
invokes the other one):
```groovy
def f(x) { x == 1 ? 1 : x + g(x - 1) }
def g(x) { x == 1 ? 1 : x * f(x - 1) }
```

### Closing Over Variables

Both functions and closures can "close over" variables in outer scopes, which means that they have access to
these variables and can read and/or modify them.
Closures are similar to lambda functions in Java but whereas lambda functions can only close over `final` or effectively
final variables (and therefore cannot modify the values of these variables), closures and functions in Jactl can
access variables whether they are effectively final or not and are able to modify their values.
(In Java, lambda functions close over the value of the variable rather than the variable itself which is why the
variable needs to be effectively final and why the lambda function is not able to modify the variable's value).

Here is an example of a Jactl closure that access a variable in an outer scope:
```groovy
def countThings(x) {
  int count = 0
  if (x instanceof List) {
    x.each{ count++ if /thing/i }     // increment count in outer scope
  }
  return count
}
```

> **Note**<br/>
> This just an example. A better way to count items matching a regex would be something like:
> `x.filter{ /thing/i }.size()`

Now consider the following code where a function returns a closure that closes over a variable `count` which is 
local to the scope of the function:
```groovy
def counter() {
 int count = 0
 return { -> ++count }
}

def x = counter()
def y = counter()

println x()
println x()
println y()
```
The output from this script will be:
```groovy
1
2
1
```
The reason that the call to `y()` returns a value of `1` and not `3` is because each time `counter()` is invoked it
returns a new closure that is bound to a brand new `counter` variable.
Even though the `counter` variable is no longer in scope once `counter()` returns, and would normally be discarded,
in this case the closure, having closed over it, retains a binding to it, and it lives on as long as the closure
remains in existence.
In the example, `x` and `y` are two different instances of the closure which each have their own `counter` variable.

> **Note**<br/>
> In functional programming, side effects such as modifying variables outside the scope of the closure are 
> generally frowned upon since pure functions don't modify state, they just return values.

## Collection Methods

Jactl has a number of built-in methods that operate on collections and things that can be iterated over.
In Jactl this means Lists, Maps, Strings, and numbers.

### each()

Consider this code that iterates over the elements of a List:
```groovy
def list = [1, 2, 3, 4, 5]
for (int i = 0; i < list.size(); i++) {
 println list[i]
}
```

Aside from the annoying boilerplate code that has to be written each time to do this iteration, it also makes it easier
to introduce bugs if the iteration code is not done correctly.
To achieve the same thing using a more Functional Programming style:
```groovy
def list = [1, 2, 3, 4, 5]
list.each{ println it }
```
We take the code that was in the `for` loop and pass it as a closure to the `each()` List method.
This method iterates over the list and passes each element into the closure.
This makes the code more concise, easier to understand, and less error-prone to write.

In general, when explicitly iterating over a collection of some sort, consider whether there is a better
way to achieve the same thing using the built-in collection methods.

### map()

Another important collection method is `map()` which, like `each()`, iterates and passes each element to the given
closure. The difference is that the closure passed to `map()` is expected to return a value so the result of using
`map()` on a list is another list with these new values:
```groovy
> [1,2,3,4].map{ it * it }
[1, 4, 9, 16]
```

The method is called `map` because it maps one set of values to another set of values.

Since the output of map is another list we can chain them together:
```groovy
> [1,2,3,4].map{ it * it }.map{ it + it }.each{ println it }
2
8
18
32
```

### Map Iteration and collectEntries()

As well as applying to Lists, these methods can also be applied to Map objects.
For Maps, each element passed into the closure is a two-element list consisting of the key and the value:
```groovy
> [a:1, b:2, c:3].each{ println "Key=${it[0]}, value=${it[1]}" }
Key=a, value=1
Key=b, value=2
Key=c, value=3
```

For closures passed to methods that act on Maps you can choose to pass a closure with a single parameter as shown
or provide a closure that takes two parameters.
If you provide a closure that takes two parameters then the first parameter will be set to the key and the second
parameter will be the value:
```groovy
> [a:1, b:2, c:3].each{ k, v -> println "Key=$k, value=$v" }
Key=a, value=1
Key=b, value=2
Key=c, value=3
```

To convert a list of key/value pairs back into a Map you can use the `collectEntries()` method:
```groovy
> [a:1, b:2, c:3].map{ k, v -> [k, v + v] }.collectEntries()
[a:2, b:4, c:6]
```

The `collectEntries()` method takes an optional closure to apply to each element before adding to the Map so the
above can also be written as this:
```groovy
> [a:1, b:2, c:3].collectEntries{ k, v -> [k, v + v] }
[a:2, b:4, c:6]
```

The closure passed to `collectEntries()` must return a two-element list with the first element being a String which
becomes the key in the Map and the second element being the value for that key.

If there are multiple entries with the same key then the value for the last element in the list with that key will
become the value in the Map.

### String Iteration and join()

Strings can also be iterated over in which case the elements are the characters of the string.
(Remember that a character is just a single-character string.)
For example:
```groovy
> 'abc'.each{ println it }
a
b
c
> 'abc'.map{ it + it.toUpperCase() }
['aA', 'bB', 'cC']
```

To turn a list of characters (actually a list of strings) back into a string use the `join()` method which takes an
optional argument which is the separator to use when joining the multiple strings together:
```groovy
> 'abc'.map{ it + it.toUpperCase() }.join()
aAbBcC
> 'abc'.map{ it + it.toUpperCase() }.join(':')
aA:bB:cC
```

### Number Iteration

Numbers can also be "iterated" over.
In this case the number acts as the number of times to iterate with the values of the iteration being the numbers
from `0` until one less than the number.
If the number is not an integer then the fractional part is ignored for the purpose of working out how many iterations
to do.
For example:
```groovy
> 5.each{ println it }
0
1
2
3
4
> 10.5.map{ it + 1 }.map{ it * it }
[1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
```

### filter()

The `filter()` method can be used to retain only elements that match a given condition.
It is passed a closure that should evaluate to `true` if the element should be retained or `false` if the element
should be discarded.
The [truthiness](#Truthiness) of the result is used to determine whether the result is `true` or not.

For example, to find which of the first 40 Fibonacci numbers are odd multiples of 3:  
```groovy
> int fib(int x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@124888672
> 40.map{ fib(it+1) }.filter{ it & 1 }.filter{ it % 3 == 0 }
[3, 21, 987, 6765, 317811, 2178309, 102334155]
```

`filter()` without any argument will test each element for truthiness, so it is equivalent to `filter{ it }`:
```groovy
> ['a', 0, 1, '', 2, null, [], false, 3].filter()
['a', 1, 2, 3]
> ['a', 0, 1, '', 2, null, [], false, 3].filter{ it }
['a', 1, 2, 3]
```

### mapWithIndex()

The `mapWithIndex()` method works like the `map()` method except that as well as passing in the element to the
closure it will also pass in the index.
For a closure with a single argument you get a two-element list where the first entry is the list element and the
second is the index:
```groovy
> ['a', 'b', 'c'].mapWithIndex{ "Element ${it[1]} is ${it[0]}" }
['Element 0 is a', 'Element 1 is b', 'Element 2 is c']
```

Note that indexes start at 0 and go up to one less than the list size.

If you give it a closure that takes two arguments then the first argument will be the list element and the second
one will be the index:
```groovy
> ['a', 'b', 'c'].mapWithIndex{ v, idx -> "Element $idx is $v" }
['Element 0 is a', 'Element 1 is b', 'Element 2 is c']
```

Without any closure passed to it `mapWithIndex()` creates a two-element list for each element with the first value
being the list element and the second being the index:
```groovy
> ['a','b','c'].mapWithIndex()
[['a', 0], ['b', 1], ['c', 2]]
```

### flatMap()

The `flatMap()` method is similar to the `map()` method in that it is expected that each element is transformed into
a new value by the given closure:
```groovy
> ['a', 'b', 'c'].flatMap{ it.toUpperCase() }
['A', 'B', 'C']
```

The difference with `flatMap()` is that if the closure returns a List rather than an individual value, the elements
of that list will be added to the result:
```groovy
> ['a', 'b', 'c'].flatMap{ [it, it.toUpperCase()] }
['a', 'A', 'b', 'B', 'c', 'C']
```

The other difference is that a `null` value as the result of the closure will mean that nothing is added to the result
for that input element:
```groovy
> ['a', 'b', 'c'].flatMap{ it == 'b' ? null : [it, it.toUpperCase()] }
['a', 'A', 'c', 'C']
```

If you have a list of lists, then `flatMap()` without any closure will flatten the list of lists into a single list.
It is same as though the closure `{ it }` was passed in :
```groovy
> [[1,2], [3,4], [5,6]].flatMap()
[1, 2, 3, 4, 5, 6]
> [[1,2], [3,4], [5,6]].flatMap{ it }
[1, 2, 3, 4, 5, 6]
```

Note that only one level of flattening will result:
```groovy
> [[1,2], [3,4], [5,[6,7,8]]].flatMap()
[1, 2, 3, 4, 5, [6, 7, 8]]
```

### skip() and limit()

If you know that you are not interested in the first `n` elements or are only interested in the first `n` elements
of the result you can use `skip()` and `limit()` to skip elements and limit the result.

For example:
```groovy
> 26.map{ (int)'a' + it }.map{ it.asChar() }.skip(10).limit(5)
['k', 'l', 'm', 'n', 'o']
```

You can use negative numbers for offset relative to the end of the list.
So `skip(-2)` means skip until there are only two elements left:
```groovy
> [1,2,3,4,5].skip(-2)
[4, 5]
```

`limit(-3)` means discard the last 3 elements:
```groovy
> [1,2,3,4,5].limit(-3)
[1, 2]
```

### Lazy Evaluation, Side Effects, and collect()

In Jactl, if a collection method results in another list of values then most methods don't actually generate the
list unless they are the last method in the invocation chain.
Consider this code:
```groovy
long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
def values = 1000.map{ fib(it+1) }.filter{ it & 1 }.filter{ it % 3 == 0 }.limit(5)
```

The code tries to calculate the first 1000 Fibonacci numers and then filter them for odd multiples of 3
limiting the result to the first 5 such numbers found.

As you can see, the output of the first `map()` method is another list of transformed values (in this case the actual
Fibonacci numbers) which then passes this list to `filter()` which outputs another list of only the odd numbers.
This list is then filtered again by another `filter()` method call to get a list of multiples of 3 and then finally
this is passed to `limit()` to limit output to the first 5 elements.

None of these lists is actually created until the last result when it is needed in order to be stored into the `values`
variable.
In fact, we don't calculate the first 1000 Fibonacci numbers (which would take a prohibitively long time using the
inefficient algorithm we have implemented here). We only calculate as many as are needed to get the first 5 odd
multiples of 3.

Jactl uses _lazy_ evaluation to avoid creating unnecessary List objects for intermediate results.
You can think of the chain of method calls acting as a conveyor belt where each element flows through each method
before the next element is processed.

This works well, and is obviously much more efficient than creating the intermediate lists, but if you use side effects
in your closures then you need to be aware of how this way of executing the methods works.
Consider this code:
```groovy
> int i = 0
0
> ['a','b','c'].map{ it + ++i }.map{ [it, i] }
[['a1', 1], ['b2', 2], ['c3', 3]]
```

As you can see, the value of `i` is incremented as each element is completely processed by all methods in the
call chain and so the second element of each pair is `1`, `2`, and `3`.
This may not be what was intended.
It might be intended that the final value of `i` (`3` in this case) is the second value of each sub-list as
though the first `map()` call had finished to completion before the second `map()` call was invoked.

If you would like to force the creation of these intermediate lists in order then you can use the `collect()` method
to force a list to be created wherever needed:
```groovy
> int i = 0
0
> ['a','b','c'].map{ it + ++i }.collect().map{ [it, i] }.collect()
[['a1', 3], ['b2', 3], ['c3', 3]]
```

In Jactl, the `collect()` also takes an optional closure like the Groovy form and so it is better to just write:
```groovy
> int i = 0
0
> ['a','b','c'].collect{ it + ++i }.collect{ [it, i] }
[['a1', 3], ['b2', 3], ['c3', 3]]
```

Note that in Java, when using streams, it is necessary to invoke `collect()` to convert the final stream of 
values back into a list.
In Jactl, this is not necessary since this will automatically be done when the last method in the chain has
finished.
So in Jactl these two expressions are equivalent:
```groovy
> [1,2,3].map{ it * it }.collect()
[1, 4, 9]
> [1,2,3].map{ it * it }
[1, 4, 9]
```

### grouped()

The `grouped()` method groups elements into sub-lists.
So `grouped(2)` will create a list of pairs of elements from the source list, while `grouped(3)` would split the list
into a list of three-element sub-lists.

For example:
```groovy
> ['a','b','c','d','e'].grouped(2)
[['a', 'b'], ['c', 'd'], ['e']]
> ['a','b','c','d','e'].grouped(3)
[['a', 'b', 'c'], ['d', 'e']]
```

If there are not enough elements to complete the last sub-list then the last sub-list will just have whatever elements
there are leftover.

Note that `grouped(0)` returns the list of elements unchanged and `grouped(1)` will create a list of single element
sub-lists:
```groovy
> [1,2,3].grouped(0)
[1, 2, 3]
> [1,2,3].grouped(1)
[[1], [2], [3]]
```

### sort()

The `sort()` method will sort a list of elements.
With no argument it will sort based on natural sort order if one exists:
```groovy
> [3, 4, -1, 1, 10, 5].sort()
[-1, 1, 3, 4, 5, 10]
> ['this', 'is', 'a', 'list', 'of', 'words'].sort()
['a', 'is', 'list', 'of', 'this', 'words']
```

If elements have not natural ordering then you will get an error:
```groovy
> [[1,2,3],[1,2]].sort()
Unexpected error: Cannot compare objects of type List and List @ line 1, column 17
[[1,2,3],[1,2]].sort()
                ^ (RuntimeError) @ line 1, column 17
[[1,2,3],[1,2]].sort()
                ^
> [1,'a'].sort()
Unexpected error: Cannot compare objects of type String and int @ line 1, column 9
[1,'a'].sort()
        ^ (RuntimeError) @ line 1, column 9
[1,'a'].sort()
        ^
```

You can pass a closure to the `sort()` method that will be passed two elements and needs to return a negative number
(e.g. `-1`) if the first element is smaller than the second one, `0` if they are the same, or a positive number
(e.g. `1`) if the first element is bigger than the second one:
```groovy
> ['this', 'is', 'a', 'list', 'of', 'words'].sort{ it[0] < it[1] ? -1 : it[0] == it[1] ? 0 : 1 }
['a', 'is', 'list', 'of', 'this', 'words']
```

If the closure accepts two arguments then this can be written as:
```groovy
> ['this', 'is', 'a', 'list', 'of', 'words'].sort{ a,b -> a < b ? -1 : a == b ? 0 : 1 }
['a', 'is', 'list', 'of', 'this', 'words']
```

This can be made more concise by using the comparator operator `<=>` which will return `-1`, `0`, or `1` if the
left-hand side is less than, equal, or greater than the right-hand side:
```groovy
> ['this', 'is', 'a', 'list', 'of', 'words'].sort{ a,b -> a <=> b }
['a', 'is', 'list', 'of', 'this', 'words']
```

If you want to reverse the sort order you can swap the left-hand and right-hand sides:
```groovy
> ['this', 'is', 'a', 'list', 'of', 'words'].sort{ a,b -> b <=> a }
['words', 'this', 'of', 'list', 'is', 'a']
```

Since you can sort based on arbitrary criteria, you can sort arbitrary objects:
```groovy
> def employees = [[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
[[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
> employees.sort{ a,b -> a.salary <=> b.salary }      // sort by salary increasing
[[name:'Joe', salary:1500], [name:'Frank', salary:2000], [name:'Daisy', salary:3000]]
```

### unique()

The `unique()` method allows you to eliminate duplicate elements in a list.
It works like the Unix `uniq` command in that it only considers elements next to each other in the list to determine
what is unique.

For example: 
```groovy
> ['a','a','b','c','c','c','a'].unique()
['a', 'b', 'c', 'a']
```

Note that `'a'` still occurs twice since only runs of the same value were eliminated.

If you want to eliminate all duplicates regardless of where they are in the list then sort the list first:
```groovy
> ['a','a','b','c','c','c','a'].sort().unique()
['a', 'b', 'c']
```

### reverse()

The `reverse()` method will reverse the order of the elements being iterated over:
```groovy
> [1, 2, 3].reverse()
[3, 2, 1]
> 10.map{ it+1 }.map{ it*it }.reverse()
[100, 81, 64, 49, 36, 25, 16, 9, 4, 1]
```

### reduce()

The `reduce()` method will iterate over a list and invoke a given closure on each element.
In addition to passing in the element to the closure, `reduce()` passes in the previous value that the
closure returned for the previous element so that the closure can calculate the new value based on the previous
value and the current element.

The final result is whatever the closure returns when passed in the final element and the previous calculated value.

Here is an example of how to use `reduce()` to calculate the sum of the elements in a list (of course, using `sum()`
is simpler but this is just an example):
```groovy
> [3, 4, -1, 1, 10, 5].reduce(0){ prev, it -> prev + it }
22
```

Note that `reduce()` takes two arguments: the initial value to pass in, and the closure.
The closure can have one or two parameters.
If it has one parameter it is passed a list of two values with the first being the previous value calculated (or the
initial value) and the second being the current element of the list.
If it takes two parameters then the first one is the previous value and second one is the element. 

Here is another example where we want to count the letter frequency in some text and print out the top 5 letters.
We use reduce to build a Map keyed on the letter with the value being the number of occurrences:
```groovy
> def text = 'this is some example text to use to count letter frequency'
this is some example text to use to count letter frequency
> text.filter{ it != ' ' }.reduce([:]){ m, letter -> m[letter]++; m }.sort{ a,b -> b[1] <=> a[1] }.limit(5)
[['e', 9], ['t', 8], ['s', 4], ['o', 4], ['u', 3]]
```

### min() and max()

You can use `min()` and `max()` to find the minimum or maximum element from a list:
```groovy
> [3, 4, -1, 1, 10, 5].min()
-1
> [3, 4, -1, 1, 10, 5].max()
10
> ['this', 'is', 'a', 'list', 'of', 'words'].min()
a
> ['this', 'is', 'a', 'list', 'of', 'words'].max()
words
```

If you want to be able to decide what comparison to use to decide which element is the one that is the smallest or
the biggest, you can pass a closure that returns something that can be compared.
For example, to find the employee with the smallest or biggest salary:
```groovy
> def employees = [[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
[[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
> employees.min{ it.salary }
[name:'Joe', salary:1500]
> employees.max{ it.salary }
[name:'Daisy', salary:3000]
```

### avg() and sum()

The `avg()` and `sum()` methods only work on lists of numbers and return the average or the sum of the values in the
list:
```groovy
> [3, 4, -1, 1, 10, 5].sum()
22
> [3, 4, -1, 1, 10, 5].avg()
3.6666666667
```

## Classes

Like Java, Jactl supports user defined classes but in a more simplified form.
Jactl uses a syntax that (mostly) follows that of Java.

Classes provide a way to encapsulate state with fields, and behaviour with methods. 

Here is an example of a simple class:
```groovy
class Point {
  int x
  int y
}
```

The class declares two fields `x` and `y` of type `int`.

If we enter the above class into the REPL we can then instantiate instances of the class:
```groovy
> class Point {
    int x
    int y
  }
> def point = new Point(1,2)
[x:1, y:2]
```

Note that in Jactl, you do not write explicit constructors for classes.
To instantiate a class you use the `new` operator followed by the class name and then a list of arguments that become
the values for the mandatory fields of the class (in the order in which the fields are declared).

Notice that by default, the value of the object when printed by the REPL is shown as though the object were a Map of
field values.

### Field Access

To access the fields of the class object you use `.` or `?.` just as for Maps:
```groovy
> point.x
1
> point.y
2
```

You can use expressions that evaluate to the field name:
```groovy
> point.('xyz'[0])
1
> point."${('x' + 'y').skip(1)[0]}"
2
```

You can also use '[]' and '?[]' to access the fields:
```groovy
> point['x']
1
> point['y']
2
```

It is recommended to use '.' and '?.' with explicit names rather than expressions where possible as this is a
more efficient access mechanism.

### Class Name as Type

When declaring a variable to contain a class instance you can make the variable strongly typed if you want.
If you then try to assign something else to the variable that is not a Point you will get an error:
```groovy
> Point point = new Point(3,4)
[x:3, y:4]
> point = new Point(5,5)
[x:5, y:5]
> point = 'abc'
Cannot convert from type of right hand side (String) to Instance<Point> @ line 1, column 7
point = 'abc'
        ^
```

You can use the class name wherever a type would normally be expected such as for parameter types and return types
of functions:
```groovy
> Point midPoint(Point p1, Point p2) {
    new Point((p1.x + p2.x)/2, (p1.y + p2.y)/2)
  }
Function@375457936
> midPoint(new Point(1,2), new Point(4,4))
[x:2, y:3]
```

### Field Initialisers and Mandatory Fields

Like variables, fields can be declared with initialisers:
```groovy
> class Point {
 int x = 0
 int y = 0
}
> def point = new Point()
[x:0, y:0]
```

Fields without intialisers are mandatory and a value must be supplied when instantiating instances with `new`:
```groovy
> class Point {
    int x
    int y = 0
  }
> new Point()
Missing mandatory field: x @ line 1, column 10
new Point()
         ^
```

Only values for mandatory fields can be supplied this way:
```groovy
> new Point(1,2)
Too many arguments for constructor (passed 2 but there is only 1 mandatory field) @ line 1, column 10
new Point(1,2)
         ^
> new Point(1)
[x:1, y:0]
```

Initialisers can use values from other fields:
```groovy
> class Point {
 int x
 int y = x
}
> new Point(3)
[x:3, y:3]
```

Note that if a field ininitialiser refers to a prior field it will use the value that the field has been assigned
but if it refers to a later field, since that field has not yet been initialised, it will just get whatever the
default value is for the type of field:
```groovy
> class Point {
    int x = y
    int y
  }
> new Point(7)
[x:0, y:7]
```

### Named Argument Passing for `new`

To set values for non-mandatory fields you can use the named argument way of invoking `new` which allows you set
the values of any fields (as long as all mandatory fields are given a value):
```groovy
> class Point {
    int x
    int y = 0
  }
> new Point(x:3, y:4)
[x:3, y:4]
> new Point(x:4)
[x:4, y:0]
```

### Converting between Maps and Class Instances

As shown, instances, when printed out look like Maps and passing named arguments to `new` is like constructing a
Map literal.

Jactl also supports constructing instances by coercing a Map using the `as` operator:
```groovy
> Point p = [x:3, y:5] as Point
[x:3, y:5]
```

You can also convert the other way and take a class instance and coerce it to a Map:
```groovy
> Map m = p as Map
[x:3, y:5]
```

When constructing an instance this way, you need to supply values for all mandatory fields but can leave out values
for the non-mandatory fields:
```groovy
> [y:5] as Point
Missing value for mandatory field 'x' @ line 1, column 7
[y:5] as Point
      ^
> [x:6] as Point
[x:6, y:0]
```

### Field Types

We have shown, so far, only examples of fields with type `int` but, as for variables and function parameters,
fields can have any type:
```groovy
> class X {
    int i = 0, j = i
    String str = 'a string'
    long longField = i * j
    var decimalField = 3.5
    double d = 1.23D
    def closure = { it * it }
  }
> new X()
[i:0, j:0, str:'a string', longField:0, decimalField:3.5, d:1.23, closure:Function@33533830]
```

Classes can even have fields of the same type as the class they are embedded in:
```groovy
class Tree {
  Tree left  = null
  Tree right = null
  def        data
}
```

### Auto-Creation

Just as for Maps, Jactl supports "auto-creation" of fields when a field reference appears as a left-hand side of an
assignment like operator such as `=`, `?=`, `+=`, `-=`, etc., as long as the types of the fields are types that can
be created with no arguments.
In other words, the types must not have any mandatory fields.

For example:
```groovy
> class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3 }
> def x = new X()
[y:null]
> x.y.z.i = 4
4
> x
[y:[z:[i:4]]]
```

By assigning a value to `x.y.z.i` we automatically created the missing values for `x.y` and `x.y.z` since they
were types that could be auto-created.

If a type has a mandatory field then the auto-create will fail:
```groovy
> class X { Y y = null }; class Y { int j; Z z = null }; class Z { int i = 3 }
> def x = new X()
[y:null]
> x.y.z.i = 4
Cannot auto-create instance of type Class<Y> as there are mandatory fields @ line 1, column 3
x.y.z.i = 4
        ^
```



### Instance Methods

We have seen classes with fields but classes are more than just a data structure for grouping related pieces of
data.
Classes can also have instance methods defined for them:
```groovy
> class Point { int x,y }class Point { int x,y }
> class Rect {
    Point p1, p2

    int area() { (p1.x - p2.x).abs() * (p1.y - p2.y).abs() }

    boolean contains(Point p) {
      p.x >= [p1.x, p2.x].min() && p.x <= [p1.x, p2.x].max() &&
      p.y >= [p1.y, p2.y].min() && p.y <= [p1.y, p2.y].max()
    }
  }
```

Instance methods are associated with an instance of the class and reference to the fields within a method
access the fields of that instance.

Methods are accessed the same way as fields and then invoked using `()` just as for functions:
```groovy
> Rect rect = new Rect(new Point(3,4), new Point(7,8))
[p1:[x:3, y:4], p2:[x:7, y:8]]
> rect.area()
16
> rect.contains(new Point(0,2))
false
> rect.contains(new Point(5,6))
true
```

Since methods can be accessed like they are fields you can use `?.` and `[]` and `?[]` to access them:
```groovy
> rect?."${'contains'}"(new Point(5,6))
true
> rect['are' + 'a']()
16
```

### This

Within instance methods instance fields are accessed by referring directly to their names:
```groovy
> class X { 
    int i = 3
    int f() { i }       // return the value of the i field
  }
> new X().f()
3
```

There is an implicit variable `this` for the instance that can also be used to refer to the fields.
This is handy if there are local variables or parameters that have the same name as on of the fields:
```groovy
> class X {
    int i = 3
    int add(int i) { this.i + i }   // Add this.i to the parameter i
  }
> new X().add(4)
7
```

You need to use `this` in situations where you want to pass a reference to the current instance to another function
or method:
```groovy
> class X { int i = 3; def doSomething(closure) { closure(this) } }
> new X().doSomething{ println it }
[i:3]
```

### Final Methods

Instance methods can be marked as `final` which means that they cannot be overridden by a child class.
In addition, in situations where we know the type of the object and we know that the method is final, there
are optimisations that the compiler can make if it can also determine that the invocation cannot invoke something 
that will suspend due to an asynchronous function being invoked.

There are also optimisations that the Java Virtual Machine can do when invoking `final` methods.

Here is an example of the use of `final`:
```groovy
> class X { final def func() { 'final function' } }
> class Y extends X { def func() { 'trying to override final function' } }
Method func() is final in base class X and cannot be overridden @ line 1, column 21
class Y extends X { def func() { 'trying to override final function' } }
                    ^
```

### Static Methods

The methods we saw previously were methods that are instance methods and apply to an instance of a class.
Jactl also supports methods that are defined as `static` which are class methods rather than instance methods.
Instead of invoking these methods via a class instance, they are invoked using the classname itself and are
not associated with any specific instance of the class.

For example:
```groovy
> class Point { 
    int x, y
 
    static Point midPoint(Point p1, Point p2) {
      new Point((p1.x + p2.x)/2, (p1.y + p2.y)/2)
    }
  }
> def mid = Point.midPoint(new Point(1,2), new Point(3,4))
[x:2, y:3]
```

If you try to access an instance field from within a static method you will get a compile error:
```groovy
> class X { 
    int i = 0
    static def staticMethod() { i++ }
  }
Reference to field in static function @ line 3, column 33
static def staticMethod() { i++ }
                            ^
```

As well as using the classname with the method invocation, you can invoke static methods through a class instance:
```groovy
> Point p = new Point(15,16)
[x:15, y:16]
> p.midPoint(new Point(1,2), new Point(3,4))
[x:2, y:3]
```

Invoking a static method via an instance works even if the type of the variable is not known at compile time:
```groovy
> def x = new Point(10,11)
[x:10, y:11]
> x.midPoint(new Point(1,2), new Point(3,4))
[x:2, y:3]
```

### No Static Fields

In Jactl, there is no support for static fields.
This differs from Java and Groovy which support static fields that exist at the class level rather than the class
instance level.

The reason that Jactl has this restriction is to do with the intended use of the language.
The language is intended to be used in event driven/reactive programming applications to provide a way for
customers of these applications to provide their own extensions and customisations.

Jactl scripts are intended to be run in a distributed multithreaded application where multiple threads in multiple
application instances can be running the same script at the same time.
It makes no sense in such a scenario to have class level fields since they would not be global fields but would 
be per application instance.
So rather than offering the illusion of global state, Jactl has taken the decision to not offer this feature at all.

If global state is really required then it is up to the application to provide its own functions for its Jactl
scripts to use that can update/read some global state, potentially in a database, or in a distributed in-memory
key/value store of some sort.

### Methods as Values

Like normal functions, methods can be assigned as values to variables and passed as values to other function/methods:
```groovy
> class Point {
    int x, y

    static Point midPoint(Point p1, Point p2) {
      new Point((p1.x + p2.x)/2, (p1.y + p2.y)/2)
    }
  }
> class Rect {
    Point p1, p2

    int area() { (p1.x - p2.x).abs() * (p1.y - p2.y).abs() }
  }
```

We can get the value of the `midPoint` static method and invoke it through a different variable:
```groovy
> def mp = Point.midPoint
Function@438589491
> mp(new Point(1,2), new Point(3,4))
[x:2, y:3]
```

If we try to get the `area` instance method of the `Rect` class the same way we get this error:
```groovy
> def a = Rect.area
Static access to non-static method 'area' for class Class<Rect> @ line 1, column 14
def a = Rect.area
             ^
```

We need to access it through an instance:
```groovy
> def rect = new Rect(new Point(1,2), new Point(3,4))
[p1:[x:1, y:2], p2:[x:3, y:4]]
> def area = rect.area
Function@597307515
> area()
4
```

Note what happens if we now change the value of the `rect` variable:
```groovy
> rect = new Rect(new Point(2,3), new Point(10,12))
[p1:[x:2, y:3], p2:[x:10, y:12]]
> rect.area()
72
> area()
4
```

The `area` variable points to the `area()` method bound to the original instance so even if `rect` gets a new value
`area` is still bound to the old value.

### Inner Classes

Inner classes can be defined within a class if desired:
```groovy
> class Shapes {
    class Point  { def x,y }
    class Rect   { 
      Point p1, p2
      def area()   { (p1.x - p2.x).abs() * (p1.y - p2.y).abs() }
      def centre() { new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2) }
    }
    class Circle { 
      Point centrePoint
      def radius
      def area() { radius * radius * 3.1415926536 }
      def centre() { centrePoint }
    }

    List shapes = []
 
    Rect   createRect(x1,y1,x2,y2) {
      def rect = new Rect(new Point(x1,y1),new Point(x2,y2))
      shapes <<= rect
      return rect
    }
    Circle createCircle(x,y,radius) {
      def circ = new Circle(new Point(x,y),radius)
      shapes <<= circ
      return circ
    }
 
    def areas()   { shapes.map{ it.area() }   }
    def centres() { shapes.map{ it.centre() } }
  }
> def shapes = new Shapes()
[shapes:[]]
> shapes.createRect(1,2,5,6)
[p1:[x:1, y:2], p2:[x:5, y:6]]
> shapes.createCircle(3,4,5)
[centrePoint:[x:3, y:4], radius:5]
> shapes.areas()
[16, 78.5398163400]
> shapes.centres()
[[x:3, y:4], [x:3, y:4]]
```

You can access the inner classes from outside their outer class by fully qualifying them with the outer
class name:
```groovy
> new Shapes.Circle(new Shapes.Point(3,4), 12).area()
452.3893421184
```

You can, of course, also nest inner classes within inner classes:
```groovy
> class X{ class Y { class Z { int i = 1 } } }
> new X.Y.Z().i
1
```

Note that Jactl inner classes are always "static inner classes" in Java terminology.
This means that instances of inner classes are not implicitly bound to an instance of an outer class. 
There is no way in Jactl to declare a non-static inner class.

### toString()

When converting a class instance to a string for the purposes of printing them or because they are being coerced
into a string use `as` then the output is shown as though the instance were a Map:
```groovy
> class X { int i,j }
> def x = new X(3,4)
[i:3, j:4]
> x as String
[i:3, j:4]
```

When printing a class instance, Jactl will first check to see if there is a `toString()` method defined or not
before using the default string conversion.
If `toString()` has been defined then this will be used instead so this is how you can customise the way in which
class instances for a given class should be converted into strings:
```groovy
> class X { int i,j; def toString() { "i=$i, j=$j" } }
> new X(3,4)
i=3, j=4
```

The `toString()` method must have no mandatory arguments and must have a return type of `String` or `def`.

### Duck Typing

Jactl allows you to use strong typing, where fields, variables, and parameters are given an expicit type, or dynamic
typing where fields, variables, and parameters can be defined as `def` which allows them to hold values of any type.

If you use strong typing then in most cases the Jactl compiler can determine at compile time what method is being
invoked and can do complile time checking of argument count and argument types.

Dynamic typing (or weak typing) means that the method invocation can only be checked at runtime and a runtime error
will be produced if the method doesn't exist or the argument types don't match.
This is also known as "duck typing" which means that we don't actually care if the object is a duck, we only want to
know that it "talks like a duck" in that it has a method of the given name we are looking for. 

For example the function `func()` doesn't care what type of object it is given as long as there is a method called
`f()` it can invoke on it:
```groovy
> class X { def f() { 'invoked X.f()' } }
> class Y { def f() { 'invoked Y.f()' } }
> def func(x) { x.f() }
Function@2130192211
> func(new X())
invoked X.f()
> func(new Y())
invoked Y.f()
```

Jactl does not take any position on whether strong typing or weak typing is more superior.
It is up to you as the developer to decide what makes sense or what feels more natural.

### Packages

Related classes can be grouped together into packages.
When using the Jactl REPL all classes are put into the top level package and there is no way to specify any other
package.
Packages are useful when integrating Jactl scripts into a Java application.

A package can be thought of like a library.
They allow a set of customisation classes to be grouped together into a library that can then be used by other
Jactl scripts.

See the [Integration Guide](integration-guide.md) for more information about how packages work when compiling
Jactl scripts and classes in your Java application.

When compiling Jactl classes and scripts the first line of the file can contain a package statement which specifies
what package the script or class should be compiled into:
```groovy
package a.b.c

class X {
  int i,j
 
  class Y {
    static def someMethod() { 'some string' }
  }
}
```

The package name that come after the `package` keyword must be a dot separated list of lowercase names.
The package name can be a single name such as `util` or can be list like `xyz.util.messaging`.

The packages form a tree where a package `a.b.c` is contained with the package `a.b` which is inside `a`.
Classes can exist at any level of the tree, not just at the most nested levels.

### Fully Qualified Class Names and Import

When using packages, classes in the same package can be accessed directly via their name.
If a class exists in a different package then you can refer to the class using its fully qualified name which is
the package name followed by a `.` and then the classname.

For example if there is a class `X` in package `a.b.c` then you access the class using `a.b.c.X`.
For example:
```groovy
new a.b.c.X(1,2)
```

If there is a nested class `Y` in side the class `X` then it can be accessed as `a.b.c.X.Y`.
For example, to access a static method of class `Y` you would use this:
```groovy
a.b.c.X.Y.someMethod()
```

To save having to continually type the full package name each time you use a class from another package you can
use `import` to import the class definition into the current Jactl script or class file:
```groovy
package x.y.z

import a.b.c.X

def x = new X(1,2)
X.Y.someMethod()     // Inner classes can now be accessed directly as X.xxx
```

You can also use `import as` to import the class and give it an alias.
If you have a long class name you might want to use it with a different alias within the current file:
```groovy
package x.y.z

import a.b.c.MyLongNamedClass as MLNC

def x = new MLNC()
```

Using `import as` is also useful if you have classes of the same name in different packages that you need
access to.

### Scoping

Unlike functions which can be declared in any scope and then have visibility within that scope, classes can only
be declared in two places:
1. Top level of a script or class file, or
2. As an inner class within another class declaration.

## Built-in Global Functions

There are a handful of global functions.

### timestamp() and nanoTime()

The `timestamp()` function returns the current epoch time in milliseconds.
It is equivalent to Java's `System.currentTimeMillis()`:
```groovy
> timestamp()
1678632694373
```

The `nanoTime()` function returns the value of the system timer in nanoseconds.
It is equivalent in Java to `System.nanoTime()`.
It is a number that can be used for timing but has no correlation with system or wall-clock time.
It has no value except within the currently running Java Virtual Machine instance, so it cannot be compared
to values from other processes even ones running on the same machine.

For example:
```groovy
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@1000966072
> def time(closure) {
    def start  = nanoTime()
    def result = closure()
    println "Result: $result, duration: ${nanoTime() - start} nanoseconds" 
  }
Function@2050339061
> time{ fib(40) }
Result: 102334155, duration: 199072125 nanoseconds
```

### sprintf()

You can use `sprintf` to format strings.
It takes a format string as its first argument and then a list of arguments that are formatted according to the
format string.

The format string uses `%s` for formatting strings, `%f` for floating point numbers, `%d` for integer amounts, and
so forth.
Between the `%` and the letter indicating the type can be numbers controlling the width and whether the field is
left-aligned or right-aligned as well as number of decimal points for floating point numbers.
It uses Java's `String.format()` function so for a full description of how the format string works see
the Javadoc for `String.format()`.

Here is an example:
```groovy
> def employees = [[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
[[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]
> println sprintf('%-10s %-10s   %s', 'Name', 'Salary', 'Hourly Rate'); employees.each{
    println sprintf('%-10s $%-10d  $%.2f', it.name, it.salary, it.salary / 4.333333 / 37.5)
  }
Name       Salary       Hourly Rate
Frank      $2000        $12.31
Daisy      $3000        $18.46
Joe        $1500        $9.23
```

### sleep()

The `sleep()` function will pause execution of the script until the given delay time has expired.
The argument to `sleep()` is the number of milliseconds to pause for.

For example:
```groovy
> sleep(500)
```

Note that the script is suspended during this pause time and resumed once the time period has expired.
The event loop thread on which the script is executing does not block.
The REPL waits for the entire script to complete so in the REPL the next prompt won't show until the sleep
has completed.

There is a second optional argument which is the value returned by `sleep()` once it has finished.
This is mainly just used for internal testing of Jactl for testing the suspending and resuming works correctly:
```groovy
> sleep(500, 3) + sleep(500, 2)
5
> sleep(500, 'ab') + sleep(500, 'c')
abc
```

### eval()

The [Eval Function](#Eval Function) section describes how this function works.

### nextLine()

When running Jactl scripts from the [command line](#command-line-scripts) this function reads the next line from
the input.

When [integrated](integration-guide.md) into a Java application, this function will read the next line from the input that the application
provides to the script.
This is one way to pass information to a script.

If reading the next line of input would block then the script is suspended and resumed when the next line becomes
available.

The function will return `null` when there are no more lines to read.

For example here is a command line script that assumes each line is a number and adds them all together and
prints out the result:
```groovy
def n, sum = 0
while ((n = nextLine()) != null) {
  sum += n as Decimal
}
println sum
```

### stream()

The `stream()` function takes a closure/function as argument and produces a stream of values by continually
invoking the closure/function until it returns `null`.
These values can then be iterated over using any of the collection methods discussed previously.

For example, here is a complicated way to print the numbers 0 to 4:
```groovy
> def i = 0
0
> def incrementer = { -> i < 5 ? i++ : null }
Function@1787189503
> stream(incrementer).each{ println it }
0
1
2
3
4
```

Since the `nextLine()` function returns `null` when it has reached the end of the input, you can use `stream()`
in conjunction with `nextLine()` to iterate over the input lines:
```groovy
def next = { -> nextLine() }
stream(next).map{ it as Decimal }.sum()
```

This is the same as:
```groovy
stream{ nextLine() }.map{ it as Decimal }.sum()
```

Functions can be passed as values and are themselves callable, so you can pass the function directly as an
argument:
```groovy
stream(nextLine).map{ it as Decimal }.sum()
```

To read all lines into a list:
```groovy
def lines = stream(nextLine)
```

## Built-in Methods

### Common Methods for Collections

In the section on [Collection Methods](#collection-methods) we have covered all the methods that work on any type
of collection such as Lists, Maps, and Strings (as well on numbers when they act as a stream of integers):

| Method             | Description                                                     |
|:-------------------|:----------------------------------------------------------------|
| `each()`           | Apply closure to each element                                   |
| `map()`            | Map value of each element to a new value                        |
| `mapWithIndex()`   | Map value and index of each element to a new value              |
| `flatMap()`        | Map element to a new value, flattening if new value is a list   |
| `filter()`         | Filter elements that match given criteria                       |
| `collect()`        | Collect values into a new List                                  |
| `collectEntries()` | Collect values into a new Map                                   |
| `skip()`           | Skip first n elements                                           |
| `limit()`          | Limit to first n elements                                       |
| `unique()`         | Remove sequences of duplicate elements                          |
| `join()`           | Join elements into a string with given separator                |
| `sort()`           | Sort elements based on given sort order                         |
| `reverse()`        | Reverse order of elements                                       |
| `grouped()`        | Group elements into sub-lists of given size                     |
| `reduce()`         | Apply given function to reduce list of elements to single value |
| `min()`            | Find minimum value                                              |
| `max()`            | Find maximum value                                              |
| `sum()`            | Calculate sum of values                                         |
| `avg()`            | Calculate average of values                                     |
| `size()`           | Number of elements in the list                                  |

Following sections will list the other methods for these types as well as methods for other types.

### List Methods

#### List.size()

The `size()` method returns the number of elements in a List, including any elements that are `null`:
```groovy
> [1, 'a', null, [1,2,3], null].size()
5
```

#### List.add()

The `add()` method will add an element to the end of a list.
It works like the `<<=` operator:
```groovy
> def x = [1,2,3]
[1, 2, 3]
> x.add(4)
[1, 2, 3, 4]
> x
[1, 2, 3, 4]
> x <<= 5
[1, 2, 3, 4, 5]
> x
[1, 2, 3, 4, 5]
```

#### List.addAt()

To add an element at a given position in the list you can use the  `addAt()` method.
This will insert the given value into the position given by the first argument (with `0` being
the first position in the list):
```groovy
>  def x = ['a', 'b', 'c']
['a', 'b', 'c']
> x.addAt(0,'z')
['z', 'a', 'b', 'c']
> x.addAt(1, 'y')
['z', 'y', 'a', 'b', 'c']
> x
['z', 'y', 'a', 'b', 'c']
```

If you add an element to a position equal to the size of the list, then the list is expanded to include this additional
value:
```groovy
> def x = ['a', 'b', 'c']
['a', 'b', 'c']
> x.addAt(3, 'z')
['a', 'b', 'c', 'z']
```

If you add an element beyond the size of the list, then you will get an error:
```groovy
> def x = ['a', 'b', 'c']
['a', 'b', 'c']
> x.addAt(10, 'z')
Index out of bounds: (10 is too large) @ line 1, column 3
x.addAt(10, 'z')
  ^
```

Note that `x.addAt(3, 'z')` is different to `x[3] = 'z'`:
```groovy
> def x = ['a', 'b', 'c', 'd']
['a', 'b', 'c', 'd']
> x.addAt(3, 'z')
['a', 'b', 'c', 'z', 'd']
> def x = ['a', 'b', 'c', 'd']
['a', 'b', 'c', 'd']
> x[3] = 'z'
z
> x
['a', 'b', 'c', 'z']
```

The `addAt()` method inserts into the list whereas `[]` replaces what was at the position with the new value.

#### List.remove()

The `remove()` method removes the element at the given position from the list:
```groovy
> def x = ['a', 'b', 'c', 'd']
['a', 'b', 'c', 'd']
> x.remove(3)
d
> x
['a', 'b', 'c'] 
```

The value returned from `remove()` is the value of the element that was removed.

#### List.subList()

The `subList()` method returns a sub-list of the list it is applied to.
It can have one or two arguments.

With one argument it returns the sub-list from the given position until the end of the list:
```groovy
> [1, 2, 3, 4].subList(2)
[3, 4]
> ['a', 'b', 'c'].subList(1)
['b', 'c']
```

A value of `0`, therefore, returns a copy of the original list:
```groovy
> ['a', 'b', 'c'].subList(0)
['a', 'b', 'c']
```

With two arguments, the first argument is the start index, and the second is one more than the end index.
This means that to extract a sub-list of length `n` at index `i` you would use `subList(i, i + n)`:
```groovy
> [1, 2, 3, 4].subList(1,3)   // extract subList of length 2 at index 1
[2, 3]
```

Since Maps, Strings, and numbers can be iterated over, `subList()` can also be applied those types of objects as well:
```groovy
> 'abcdef'.subList(2,4)
['c', 'd']
> [a:1,b:2,c:3].subList(2)
[['c', 3]]
> [a:1,b:2,c:3].subList(1,3)
[['b', 2], ['c', 3]]
> 10.subList(5,8)
[5, 6, 7]
```

### Map Methods

#### Map.size()

The `size()` method returns the number of entries in the Map:
```groovy
> ['a':1, 'b':2, 'c':3].size()
3
```

#### Map.remove()

The `remove()` method removes the entry with the given key from the Map and returns the value for that key:
```groovy
> def x = ['a':1, 'b':2, 'c':3]
[a:1, b:2, c:3]
> x.remove('b')
2
> x
[a:1, c:3]
```

If there is no such entry that matches the key, `remove()` will return `null`:
```groovy
> def x = ['a':1, 'b':2, 'c':3]
[a:1, b:2, c:3]
> x.remove('z') == null
true
> x
[a:1, b:2, c:3]
```

### String Methods

### String.size() and String.length()

Jactl supports the use of both `size()` and `length()` for getting the length of a string.
`length()` is supported in order to make it easier for Java progammers who are used to using `length()`
while `size()` is supported for consitency in naming across Lists, Maps, and Strings:
```groovy
> 'abcde'.size()
5
> 'abcde'.length()
5
```

### String.lines()

The `lines()` method splits the string into a list of strings, one per line in the source string:
```groovy
> def data = '''multi-line
  string
  on
  four lines'''
multi-line
string
on
four lines
> data.lines()
['multi-line', 'string', 'on ', 'four lines']
> ''.lines()
['']
```

### String.toUpperCase() and String.toLowerCase()

These methods turn a string into all upper case or all lower case:
```groovy
> 'abc'.toUpperCase()
ABC
> 'A String With Capitals'.toLowerCase()
a string with capitals
```

### String.substring()

The `substring()` method allows you to extract a substring starting at a given index.
Like `subList()` it has a single argument version that returns the remaining string from the given index, and a two
argument version that gives the two indexes that bound the substring.

The single argument version work like this:
```groovy
> 'abcdef'.substring(3)
def
> 'abcdef'.substring(0)
abcdef
```

The two argument version extracts the substring starting at the first index until one less than the value of the second
index.
This means that to extract `n` chars at index `i` you use `substring(i, i + n)`:
```groovy
> 'abcde'.substring(2,4)
cd
```

### String.split()

The `split()` method splits a string based on a separator specified by a regex with optional modifiers.
See the previous section on the [Split Method](#split-method) for more information.

### String.asNum()

The `asNum()` method parses a string of digits and returns their numeric value.
It takes an optional argument which is the base (or radix) for the number being parsed.

For example:
```groovy
> '1234'.asNum()
1234
> 'ff14'.asNum(16)
65300
> '101011100110'.asNum(2)
2790
```

A base of up to 36 is supported:
```groovy
> 'abzyAj13'.asNum(36)
809760160983
> 'abzyAj13'.asNum(37)
Base was 37 but must be no more than 36 @ line 1, column 12
'abzyAj13'.asNum(37)
           ^
```

Both lowercase and uppercase letters are supported for bases greater than 10:
```groovy
> 'abcdef99'.asNum(16)
2882400153
> 'ABCDEF99'.asNum(16)
2882400153
```

### Int Methods

#### int.asChar()

The `asChar()` method converts a Unicode value back into its corresponding character (which is a single-character
string in Jactl):

```groovy
> 0x41.asChar()
A
```

To convert from a character to its Unicode value, cast the single-character string to `int`:
```groovy
> (int)'Z'
90
> 90.asChar()
Z
```

### Numeric Methods

Apart from `toBase()`, these methods apply to all number types (`int`, `long`, `double`, and `Decimal`).

#### Number.toBase()

The `toBase()` method converts an `int` or `long` to its character representation in the specified base:
```groovy
> 1234.toBase(16)
4D2
> '4D2'.asNum(16)
1234
```

Bases between 2 and 36 are supported:
```groovy
> 1234567879.toBase(37)
Base must be between 2 and 36 @ line 1, column 12
1234567879.toBase(37)
           ^
```

#### Number.abs()

The `abs()` method returns the absolute value of the number:
```groovy
> -15.abs()
15
> def distance(x1, x2) { (x1 - x2).abs() }
Function@1987169128
> distance(11, 121)
110
```

#### Number.sqr()

The `sqr()` method returns the square of the given number:
```groovy
> -1234.56.sqr()
1524138.3936
```

#### Number.sqrt()

The `sqrt()` method returns the square root of the number:
```groovy
> def x = 1234.5678.sqrt()
35.13641700572214
```

#### Number.pow()

The `pow()` method allows you to calculate one number raised to the power of another.
For example, to calculate the cube of a number:
```groovy
> def x = 123
123
> x.pow(3)   // cube of x
1860867
```

The value of the exponent passed to `pow()` can be fractional and can be negative:
```groovy
> 16.pow(0.5)    // another way to get square root
4
> 16.pow(-1.5)
0.015625
```

### Object Methods

The only method that applies to objects of all types is the `toString()` method.
If passed no argument it prints the string representation of the object:
```groovy
> 1234.toString()
1234
> def x = [a:1, b:[c:3,d:[1,2,3]]]
[a:1, b:[c:3, d:[1, 2, 3]]]
> x.toString()
[a:1, b:[c:3, d:[1, 2, 3]]]
```

If an optional indent amount is passed to it then it will do a pretty-print of complex objects such as Maps, Lists,
and class instances:
```groovy
> def x = [a:1, b:[c:3,d:[1,2,3]]]
[a:1, b:[c:3, d:[1, 2, 3]]]
> x.toString(2)
[
  a:1,
  b:[
    c:3,
    d:[1, 2, 3]
  ]
]
```
