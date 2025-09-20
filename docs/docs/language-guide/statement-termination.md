---
title: Statement Termination
---

Jactl syntax borrows heavily from Java, Groovy, and Perl and so while Java and Perl both require a semicolon to terminate
simple statements, Jactl adopts the Groovy approach where semicolons are optional.
The only time a semicolon is required as a statement separator is if more than one statement appears on the
same line:
```groovy
int x = 1; println 'x = ' + x; println 'x is 2' if x == 2
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
