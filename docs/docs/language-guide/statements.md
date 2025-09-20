---
title: Statements
---

## If/Else Statements

The `if` statement allows you to conditionally execute a block of code.
The syntax is:
> `if` `(` _condition_ `)` _statement_

The _condition_ is an expression that is evaluated and if it is `true` then the _statement_ is executed.
The _statement_ can be a single statement or a block of statements enclosed in `{}`.

For example:
```groovy
def x = 3
if (x > 2) println 'x is greater than 2'    // output: x is greater than 2
if (x > 2) {
  x++
  println 'x is greater than 2'
}
// output: 
// x is greater than 2
```

The `if` statement can also have an `else` clause that is executed if the condition is `false`:
> `if` `(` _condition_ `)` _statement1_ `else` _statement2_

For example:
```groovy
def x = 3
if (x > 2) println 'x is greater than 2' else println 'x is not greater than 2'
// output: x is greater than 2
if (x > 4) {
  println 'x is greater than 4'
} else {
  println 'x is not greater than 4'
}
// output: x is not greater than 4
```

The `else` clause can itself be another `if` statement:
```groovy
def x = 3
if (x > 4) {
  println 'x is greater than 4'
} else if (x > 2) {
  println 'x is greater than 2'
} else {
  println 'x is not greater than 2'
}
// output: x is greater than 2
```

## Postfix If/Unless

As well as the standard `if` statement, Jactl also supports a statement modifier form of `if` where the `if` and the
condition come after the statement to be executed:
> _statement_ `if` _condition_

For example:
```groovy
def x = 3
println 'x is greater than 2' if x > 2
// output: x is greater than 2
```

This form of `if` is useful for making code more readable in some situations.

There is also an `unless` form that is the opposite of `if`:
> _statement_ `unless` _condition_

The statement is executed if the condition is `false`:
```groovy
def x = 3
println 'x is not greater than 4' unless x > 4
// output: x is not greater than 4
```

## Loop Statements

Jactl provides `for`, `while`, and `do/until` loops for iterating over a block of code.

### For Loops

The `for` loop has two forms.
The first form is the same as in Java and Groovy:
> `for` `(` _initialiser_ `;` _condition_ `;` _increment_ `)` _statement_

The _initialiser_ is a statement that is executed once at the start of the loop.
The _condition_ is an expression that is evaluated before each iteration of the loop and the loop continues as long as
the condition is `true`.
The _increment_ is a statement that is executed after each iteration of the loop.

For example:
```groovy
for (int i = 0; i < 5; i++) { println i }
```

The second form of `for` loop is the "for-each" loop that iterates over a collection of values:
> `for` `(` _variable_ `in` _collection_ `)` _statement_

The _collection_ can be a `List`, `Map`, `String`, or a number.
The _variable_ is a variable that will be assigned the value of each element in the collection in turn.

For example:
```groovy
for (i in [1,2,3]) { println i }
// Output:
// 1
// 2
// 3
for (c in 'abc') { println c }
// Output:
// a
// b
// c
for (i in 3) { println i }
// Output:
// 0
// 1
// 2
```

If the collection is a `Map` then the variable will be a two-element list containing the key and value of each entry
in the map:
```groovy
for (entry in [a:1, b:2]) { println entry }
// Output:
// ['a', 1]
// ['b', 2]
```

You can also use a multi-variable declaration to get the key and value in separate variables:
```groovy
for ((key,value) in [a:1, b:2]) { println "$key -> $value" }
// Output:
// a -> 1
// b -> 2
```

### While Loops

The `while` loop executes a block of code as long as a condition is `true`:
> `while` `(` _condition_ `)` _statement_

For example:
```groovy
def i = 0
while (i < 5) { println i; i++ }
// Output:
// 0
// 1
// 2
// 3
// 4
```

### Do/Until Loops

The `do/until` loop is similar to a `do/while` loop in other languages.
It executes a block of code and then checks a condition to see if it should continue.
The loop continues until the condition is `true`.

> `do` _statement_ `until` `(` _condition_ `)`

For example:
```groovy
def i = 0
do { println i; i++ } until (i == 5)
// Output:
// 0
// 1
// 2
// 3
// 4
```

### Break and Continue

The `break` and `continue` statements can be used to control the flow of a loop.
The `break` statement will exit the loop immediately.
The `continue` statement will skip the rest of the current iteration and start the next one.

For example:
```groovy
for (i in 10) {
  if (i == 3) continue
  if (i == 5) break
  println i
}
// Output:
// 0
// 1
// 2
// 4
```

## Do Expression

The `do` expression allows you to turn a block of statements into an expression.
The value of the `do` expression is the value of the last statement in the block.

For example:
```groovy
def x = do { def i = 3; def j = 4; i + j }
x      // 7
```

This is useful when you want to assign the result of a complex calculation to a variable:
```groovy
def x = do {
  def sum = 0
  for (i in 10) {
    sum += i
  }
  sum
}
x      // 45
```

## Eval Statement

The `eval` statement allows you to compile and execute a string of Jactl code at runtime.
The string can be a simple expression or can be a series of statements.
The value of the `eval` statement is the value of the last expression in the string.

For example:
```groovy
eval('3 + 4')                              // 7
eval('def x = 3; def y = 4; x + y')        // 7
```

You can pass in a map of variables that the script can access:
```groovy
eval('x + y', [x:3, y:4])                  // 7
```

The script can modify the values in the map:
```groovy
def vars = [x:3, y:4]
eval('x += y', vars)                      // 7
vars                                      // [x:7, y:4]
```

## Print Statements

The `print` and `println` statements are used to output values.
The `print` statement outputs the value as a string.
The `println` statement outputs the value as a string followed by a newline.

For example:
```groovy
print 'abc'
print 'def'
println 'ghi'
// Output:
// abcdefghi
```

The `print` and `println` statements can take multiple arguments which are concatenated together with spaces in between:
```groovy
def x = 3
println 'x is', x, 'and x*x is', x*x
// Output:
// x is 3 and x*x is 9
```

## Die Statements

The `die` statement is used to terminate the execution of a script immediately.
It can be used to report an error and exit.

For example:
```groovy
def x = 3
die 'x is too big' if x > 2
```

