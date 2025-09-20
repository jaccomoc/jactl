---
title: Expression and Operators
---

## Operator Precedence

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
|                  | `&=` `|` `^=` | Bitwise assignment operators                        |
|        5         |             `? :`              | Ternary conditional opeartor                        |
|                  |              `?:`              | Default value operator                              |
|        6         |   `||`    | Boolean or                                          |
|        7         |              `&&`              | Boolean and                                         |
|        8         |      `|`       | Bitwise or                                          |
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
3 + 2 * 4 - 1          // 10
```
Bracketing can be used to force the order of evaluation of sub-expressions where necessary:
```groovy
(3 + 2) * (4 - 1)      // 15
```

## Assignment and Conditional Assignment

Variables can have values assigned to them using the `=` operator:
```groovy
def x = 1
x = 2
```

Since an assignment is an expression and has a value (the value being assigned) it is possible to use an assignment
within another expression:
```groovy
def x = 1
def y = x = 3
x                    // 3
y                    // 3
y = (x = 4) + 2      // 6
x                    // 4
```
Conditional assignment uses the `?=` operator and means that the assignment only occurs if the expression on the right
hand side is non-null.
So `x ?= y` means `x` will be assigned the value of `y` only if `y` is not null:
```groovy
def x = 1
def y          // no initialiser so y will be null
x ?= y
x              // x still has its original value of 1 since y was null
```

## Basic Arithmetic Operators

The standard arithmetic operators `+`, `-`, `*`, `/` are supported for addition, subtraction, multiplication, and
division:
```groovy
3 * 4 + 6 / 2 + 5 - 10    // 10
```
Remember that `*` and `/` have higher precedence and are evaluated before any addition or subtraction.

## Prefix _+_ and _-_

The `+` and `-` operators can also be used as prefix operators:
```groovy
-(3 - 4)          // 1
+(3 - 4)          // -1
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
5 | 3               // 7
0b101 | 0b011       // 7 
```

The `&` operator does an _and_ of each bit and the resulting bit is 1 only if both left-hand side and right-hand side
bit values were 1:
```groovy
5 & 3              // 1
0b101 & 0b011      // 1
```

The `^` operator is an _xor_ and the result for each bit value is 0 if both left-hand side and right-hand side bit values
are the same and 1 if they are different:
```groovy
5 ^ 3             // 6
0b101 ^ 0b011     // 6
```

The `~` is a prefix operator and does a bitwise _not_ of the expression that follows so the result for each bit is the
opposite of the bit value in the expression:
```groovy
~5             // -6
~0b00000101    // -6
```

## Shift Operators

The shift operators `<<`, `>>`, `>>>` work at the bit level and shift bits of a number by the given number of positions.

The `<<` operator shifts the bits to the left meaning that the number gets bigger since we are multiplying by powers of 2.
For example, shifting left by 1 bit will multiply the number by 2, shifting left by 2 will multiply by 4, etc.

For example:
```groovy
5 << 1          // 0b0101 << 1 --> 0b1010 which is 10
5 << 4          // multiply by 16: result is 80
```

The '>>' and '>>>' operators shift the bits to the right.
The difference between the two is how negative numbers are treated.
The `>>` operator is a signed shift right and preserves the sign bit (the top most bit) so if it is 1 it remains 1 and
1 is shifted right each time from this top bit position.
The '>>>' operator treats the top bit like any other bit and shifts in 0s to replace the top bits when the bits are
shifted right.

For example:
```groovy
def x = 0b11111111000011110000111100001111 >> 16     // shift right 16 but shift in 1s at left since -ve
                                                     // -241
x.toBase(2)                                          // 11111111111111111111111100001111
x = 0b11111111000011110000111100001111 >>> 16        // shift right 16 but shift in 0s at left
                                                     // 65295
x.toBase(2)                                          // 1111111100001111
```

## Modulo _%_ and Remainder _%%_ operators

In addition to the basic four operators, Jactl also has `%` (modulo) and `%%` (remainder) operators.
Both operators perform similar functions in that they calculate the "remainder" after dividing by some number.
The difference comes from how they treat negative numbers.

:::note
Java only has the remainder operator - it does not have a modulo operator.
:::

The Jactl remainder operator `%%` works exactly the same way as the Java remainder operator (which in Java is represented
by a single `%`):
> `x %% y` is defined as being `x - (int)(x/y) * y`

If `x` is less than `0` then the result will also be less than 0:
```groovy
-5 %% 3     // -2
```

When doing modulo arithmetic values should only be between `0` and `y - 1` when evaluating
something modulo `y` (where `y` is postive) and between `0` and `-(y - 1)` if `y` is negative.

The definition, in Jactl, of the modulo operator `%` is:
> `x % y` is defined as `((x %% y) + y) %% y`

This means that in Jactl `%` returns only postive values if the right-hand side is positive and only negative values
if the right-hand side is negative.

Jactl retains `%%` for scenarios where you want compatibility with how Java does things or for when you know that
the left-hand side will always be positive, and you care about performance (since `%%` compiles to a single JVM
instruction while `%` is several instructions).

## Increment/Decrement Operators

Jactl offers increment `++` and decrement `--` operators that increment or decrement a value by `1`.
Both prefix and postfix versions of these operators exist.
In prefix form the result is the result after applying the increment or decrement while in postfix form the value
is the value before the increment or decrement occurs.

For example:
```groovy
def x = 1
x++    // increment x but return value of x before increment: result is 1
x      // 2
++x    // increment x and return new value: result is 3
x      // 3
--x    // decrement x and return new value: result is 2
x--    // decrement x but return old value: result is 2
x      // 1
```

If the expression is not an actual variable or field that can be incremented then there is nothing to increment or
decrement but in the prefix case the value returned is as though the value had been incremented or decremented:
```groovy
3++          // 3
++3          // 4
--(4 * 5)    // 19
(4 * 5)--    // 20
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
'a' < 'ab'         // true
'abcde' < 'xyz'    // true
```

The difference between `==` and `===` (and `!=` and `!==`) is that `==` compares values.
This makes a difference when comparing Lists and Maps (and class instances).
The `==` operator will compare the values of all elements and fields to see if the values are equivalent.
The `===` operator, on the other hand, is solely interested in knowing if the two Lists (or Maps, or objects) are the
same object or not.
For example:
```groovy
def x = [1, [2,3]]
def y = [1, [2,3]]
x == y              // true
x === y             // false
x !== y             // true
x = y
x === y             // true
x !== y             // false
```

## Comparator Operator

The `<=>` comparator operator evaluates to -1 if the left-hand side is less than the right-hand side, 0 if the two
values are equal, and 1 if the left-hand side is greater than the right-hand side:
```groovy
1 <=> 2          // -1
2.3 <=> 2.3      // 0
4 <=> 3          // 1
'abc' <=> 'ab'   // 1
'a' <=> 'ab'     // -1
```

The operator can be particularly useful when sorting lists as the `sort` method takes as an argument a function
that needs to return -1, 0, or 1 based on the comparison of any two elements in the list:
```groovy
def employees = [[name:'Frank', salary:2000], [name:'Daisy', salary:3000], [name:'Joe', salary:1500]]

employees.sort{ a,b -> a.salary <=> b.salary }      // sort by salary increasing
  // [[name:'Joe', salary:1500], [name:'Frank', salary:2000], [name:'Daisy', salary:3000]]

employees.sort{ a,b -> b.salary <=> a.salary }      // sort by salary decreasing
  // [[name:'Daisy', salary:3000], [name:'Frank', salary:2000], [name:'Joe', salary:1500]]
```

## Logical/Boolean Operators

There are two sets of logical or boolean operators:
1. `&&`, `||`, `!`, and
2. `and`, `or`, `not`

For both `&&` and `and` the result is true if both sides are true:
```groovy
3.5 > 2 && 'abc' == 'ab' + 'c'       // true
5 == 3 + 2 and 7 < 10                // true
5 == 3 + 2 and 7 > 10                // false
```

With the `||` and `or` operators the result is true if either side evaluates to true:
```groovy
5 == 3 + 2 || 7 > 10                 // true
5 < 4 or 7 < 10                      // true
```

The `!` and `not` operators negate the result of the following boolean expression:
```groovy
! 5 == 3 + 2                         // false
not 5 < 4                            // true
```

The difference between the two sets of operators is that the operators `and`, `or`, and `not` have very low precedence;
even lower than the assignment operators.
This makes it possible to write things like this somewhat contrived example:
```groovy
def x = 7
def y = 8
x == 7 and y = x + (y % 13)       // assign new value to y if x == 7
y                                 // 15
```

In this case, there are obviously other ways to achieve the same thing (such as an if statement) but there are
occasions where having these low-precedence versions of the boolean operators is useful.
It comes down to personal preference whether, and when, to use them.

## Conditional Operator

The conditional operator allows you to embed an `if` statement inside an expression.
It takes the form:
> condition `?` valueIfTrue `:` valueIfFalse

The condition expression is evaluated and if it is `true` then the result of the entire expression
is the value of the second expression (valueIfTrue).
If the condition is false then the result is the third expression (valueIfFalse).

For example:
```groovy
def x = 7
def y = x % 2 == 1 ? 'odd' : 'even'                     // odd
def z = y.size() > 3 ? x+y.size() : x - y.size()        // 4
```

## Default Value Operator

The default value operator `?:` evaluates to the left-hand side if the left-hand side is not null and evaluates to
the right-hand side if the left-hand side is null.
It allows you to specify a default value in case an expression evaluates to null:
```groovy
def x
def y = x ?: 7
y ?: 8           // 7
```

## Other Assignment Operators

Many operators also have assignment versions that perform the operation and then assign the result to the
left-hand side.
The corresponding assignment operator is the original operator followed immediately (no spaces) by the `=` sign.
For example, the assignment operator for `+` is `+=`.
An expression such as `x += 5` is just shorthand for `x = x + 5`.

The full list of assignment operators is:

> `+=` `-=` `*=` `/=` `%=` `%%=` `<<=` `>>=` `>>>=` `&=` `|=` `^=`

For example:
```groovy
def x = 5
x += 2 * x
x            // 15
x /= 3       // 5
x %= 3       // 2
x <<= 4      // 32
x |= 15      // 47
```

## Multi-Assignment

You assign to multiple variables at the same time by listing the variables within `(` and `)` and providing
a list of values on the right-hand side of the assignment operator:
```groovy
def x; def y
(x,y) = [3,4]
println "x=$x, y=$y"      // output: x=3, y=4
```

The right-hand side can be another variable or expression that evaluates to a list:
```groovy
def x,y
def str = 'abc'
(x,y) = str           // extract the first and second characters of our string
println "x=$x, y=$y"  // output: x=a, y=b
```

You can use any of the assignment operators such as `+=` or `-=`:
```groovy
def (x,y) = [1,2]
(x,y) += [3,4]
println "x=$x, y=$y"   // output: x=4, y=6
```

Any expression that can appear on the left-hand side of a normal assignment can appear in a multi-assignment (not just
simple variable names):
```groovy
def x = [:]
(x.('a' + '1').b, x.a1.c) = ['xab', 'xac']

x              // [a1:[b:'xab', c:'xac']]
```

The conditional assignment operator `?=` is also supported.
In a multi-assignment, each of the individual assignments is evaluated to see if the value in the list on
the right-hand side is null or not so some values can be assigned while others aren't:
```groovy
> def (x,y) = [1,2]
2        
> def z        
> (x,y) ?= [3,z]       // y unchanged since z has no value
> "x=$x, y=$y"
x=3, y=2
```

Multi-assignment can be used to swap the values of two variables:
```groovy
> def (x,y) = [1,2]
2
> (x,y) = [y,x]       // swap x and y
1
> "x=$x, y=$y"
x=2, y=1
```

## Instance Of

The `instanceof` and `!instanceof` operators allow you to check if an object is an instance (or not an instance) of a
specific type.
The type can be a built-in type like `int`, `String`, `List`, etc. or can be a user defined class:
```groovy
def x = 1
x instanceof int            // true
x !instanceof String        // true
x = [a:1, b:[c:[1,2,3]]]
x.b instanceof Map          // true
x.b.c instanceof List       // true

class X { int i = 1 }
x = new X()
x instanceof X              // true
```

## Type Casts

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
def x = [1,2,3]
def sum = x instanceof List ? ((List)x).sum() : 0   // 6
```
Whereas in Java the cast would be required, since Jactl supports dynamic typing, the cast in this case is not
necessary (but might make the execution slightly faster).

The other use for casts is to convert primitive number types to one another.
For example, you can use casts to convert a double or decimal value to its corresponding integer representation
(discarding anything after the decimal point):
```groovy
def hoursOwed = 175.15
def hoursPerDay = 7.5
def daysOwed = (int)(hoursOwed / hoursPerDay)       // 23
```

The other special case for cast is to cast a character to an integer value to get its Unicode value.
Remember that characters in Jactl are just single character strings so if you cast a single character string to
`int` you will get is Unicode value:
```groovy
(int)'A'          // 65
```

## As Operator

The `as` operator is used to convert values from one type to another (where such a conversion is possible).
The operator is an infix operator where the left-hand side is the expression to be converted and the right-hand side
is the type to convert to.
It is similar to a type cast and can be used to do that same thing in some circumstances:
```groovy
(int)3.6
3.6 as int      // 3
```

You can use `as` to convert a string representation of a number into the number:
```groovy
'123' as int             // 123
'123.4' as Decimal       // 123.4
```

You can use it to convert anything to a string:
```groovy
123 as String            // '123'
[1,2,3] as String        // '[1, 2, 3]'
```

Of course, you can do the same thing with the built-in `toString()` method as well:
```groovy
123.toString()           // '123'
[1,2,3].toString()       // '[1, 2, 3]'
```

The `as` operator can convert between Lists and Maps (as long as such a conversion makes sense):
```groovy
[a:1,b:2] as List         // [['a', 1], ['b', 2]]
[['x',3],['y',4]] as Map  // [x:3, y:4]
```

Lists of pairs (as shown in the example) are able to be converted to Maps by taking the first element of each pair
as the key and the second element as the value for that key.

It is also possible to use `as` to convert between objects of user defined classes and Maps (see section on Classes):
```groovy
class Point{ int x; int y }
def x = [x:3, y:4] as Point
x instanceof Point     // true
x as Map               // [x:3, y:4]
```

## In Operator

The `in` and `!in` operators are used to test if an object exists or not within a list of values.
For example:
```groovy
def country = 'France'
country in ['Germany', 'Italy', 'France']            // true
def myCountries = ['Germany', 'Italy', 'France']
country !in myCountries                              // false
```

This operator works by iterating through the list and comparing each value until it finds a match so if the list of values is particularly
large then this will not be very efficient.

For Maps, the `in` and `!in` operators allow you to check if a key exists in the Map:
```groovy
def m = [abc:1, xyz:2]
'abc' in m                     // true
'xyz' !in m                    // false
```

The `in` and `!in` operators also work on Strings to see if one string is contained within another:
```groovy
'the' in 'some of the time'    // true
```

## Field Access Operators

The `.` `?.` `[]` and `?[]` operators are used for accessing fields of Maps and class objects while the `[]` and `?[]`
are also used to access elements of Lists.
The `?` form of the operators will return null instead of producing an error if the List/Map/object is null.

For example:
```groovy
Point p = new Point(x:1, y:2)
p.x                        // 1
p['x']                     // 1
p = null
p?.x == null               // true
p?['x'] == null            // true
[abc:1].('a'+'bc')         // 1
[abc:1]?.('a'+'bc')        // 1
[abc:1]?.x?.y?.z == null   // true
```
