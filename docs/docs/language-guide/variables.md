---
title: Variables
---

A variable in Jactl is a symbol that is used to refer to a value. Variables are declared by specifying a type followed
by their name and an optional expression to use as an initial value for the variable. If no initialization expression
is given then the default value for that type is assigned.

After declaration, variables can have new values assigned to them as long as the new value is compatible with the type
that was specified for the variable at declaration time. Variables can be used in expressions at which point their
current value is retrieved and used within that expression:
```groovy
int x = 4
x = 3 * 8       // 24
int y           // gets default value of 0
y = x / 6       // 4
```

## Valid Variable Names

Variable names must be a valid _identifier_. Identifiers start with a letter or with an underscore `_` followed
by a combination of letters, digits and underscores. The only special case is that a single underscore `_` is not a
valid identifier.

Variable names cannot clash with any built-in language [keywords](/language-guide/#keywords):
```groovy
int for = 3
Unexpected token 'for': Expecting identifier @ line 1, column 5
int for = 3
        ^
```

:::note
Since class names in Jactl must start with a capital letter it is good practice not
to start variable names with a capital letter in order to make your code easier to read.
:::

:::note
Unlike Java, the dollar sign `$` is not a valid character for a variable name in Jactl.
:::

While most of the examples presented here have single character variable names, in general, your code should use
names that are more meaningful. It is recommended that when a variable name consists of more than one word that
_camel case_ is used to indicate the word boundaries by capitalizing the first letter of following words.
For example:
* firstName
* accountNumber
* emailAddress

## Variable Declarations

In Jactl, variables must be declared before they are used.
A variable declaration has a type followed by the variable name and then optionally an initialiser that is used
to initialise the variable:
```groovy
int i = 3
int j      // defaults to 0
```

You can use `def` to define an untyped variable (equivalent to using `Object` in Java):
```groovy
def x
def y = 'abc'
def z = 1.234
```

Multiple variables can be declared at the same time if they are of the same type:
```groovy
int i, j   // i and j will default to 0
String s1 = 'abc', s2 = 'xyz'
def x,y    // x and y default to null
```

When using an initialiser, you can specify the variable type as `var` and the type will be inferred from the
type of the initialiser:
```groovy
var i = 1         // i will be an int
var s = 'abc'     // s will be a String
```

Another way to declare multiple variables in the same statement is to surround the variables with `(` and `)` and
then provide the optional initialisers in a separate list after a `=` symbol:
```groovy
def (x,y) = [1,2]
```
The right-hand side can be any expression that evaluates to a list (or something that supports subscripting such as
a String or an array):
```groovy
def stats = { x -> [x.sum(), x.size(), x.avg()] }
def values = [1, 4, 7, 4, 5, 13]
def (sum, count, avg) = stats(values)
sum          // 34
count        // 6
avg          // 5.6666666667
def (first, second, third) = 'string value'    // grab 1st, 2nd, 3rd letters from string
first        // s
second       // t
third        // r
```

This multi-declaration form supports the type being specified per variable:
```groovy
def (int i, String s) = [123, 'abc']
```

## Declaring Constants

The `const` keyword can be used when declaring a variable to create a constant.
Constants cannot be assigned to or modified and are limited to these simple types:
* boolean
* byte
* int
* long
* double
* Decimal
* String

For example:
```groovy
const int     MAX_SIZE = 10000
const Decimal PI       = 3.1415926536
```

The type is optional and will be inferred from the value of the initialiser:
```groovy
const MAX_SIZE = 10000
const PI       = 3.1415926536
```

A `const` must have an initialiser expression to provide the value of the constant.
This expression can be a simple value (number, String, etc.) or can be a simple numerical
expression:
```groovy
const PI      = 3.1415926536
const PI_SQRD = PI * PI
```

