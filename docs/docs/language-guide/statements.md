---
title: Statements
description: "Jactl statement types including if/else, for, while, do/while, and return statements."
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
> `for` `(` _initialiser_ `;` _condition_ `;` _updates_ `)` _statement_

The _initialiser_ is a comma-separated list of statements that are executed once at the start of the loop.
The _condition_ is an expression that is evaluated before each iteration of the loop and the loop continues as long as
the condition is `true`.
The _updates_ is a comma-separated list of statements that are executed after each iteration of the loop.

For example:
```groovy
for (int i = 0; i < 5; i++) { 
  println i
}
```

If the initialiser is a variable declaration with no type specified, 
the variable will be declared with a type of `Object` and its value will be available after the loop completes:
```groovy
for (i = 0; i < 10; i++) {
  println i
}
println "Final value of i: $i"      // i will be 10
```

If the variable has already been declared earlier, it will be reused in the `for` loop.

As with normal variable declarations, multiple variables can be declared in the initialiser, all inheriting the
same type:
```groovy
for (int i = 0, j = 10; i < 10 && j < 20; i++, j += i) {
  println "i=$i, j=$j"
}
```

If other types of statements are used in the initialiser, variables cannot be declared.
The comma-separated statements must either be variable declarations, or other statement types, but not a mix of the two.

The second form of `for` loop is the "for-each" loop that iterates over a collection of values:
> `for` `(` _variable_ `in` _collection_ `)` _statement_

or

> `for` `(` _variable_ `:` _collection_ `)` _statment_

The _collection_ can be a `List`, `Map`, `String`, array, or a number.
The _variable_ is a variable that will be assigned the value of each element in the collection in turn.
It can optionally have a type attached to it.
If no type is specified and the variable does not already exist, its type will be inferred from the
element type of the collection (if the collection is an array or the types of the elements is known
at compile-time), or will default to `Object` if iterating over a list or other collection type.

For example:
```groovy
for (i in [1,2,3]) { 
  println i
}
println "Last value of i : $i"
// Output:
// 1
// 2
// 3
// Last value of i : 3

for (c in 'abc') { println c }
// Output:
// a
// b
// c

for (int i in 3) { 
  println i
}
println "Final i: $i"
// Output:
// 0
// 1
// 2
// Final i: 3

for (i : list.map{ it * it }) {
  println it
}
```

The _variable_ is actually just a special case of a more generic _pattern_ that is used to match the
elements of the collection. Without a type, a single variable will match all elements. With a type
supplied, only elements of that type will match and be used in the `for` loop.
For example:
```groovy
for (int i in [1,2,'abc',3]) {
  println i
}
// Output:
// 1
// 2
// 3
```
If you want to generate an error if there is a non-matching element, you can use `:` instead of `in` to
force a match on each element:
```groovy
// This will generate an error:
for (int i: [1,2,'abc']) {
  println i
}
```

The _pattern_ can be more complex than a single typed variable.
If you have a collection of two-element lists, for example, you can iterate over these nested lists and bind the first
and second elements of each list to two different binding variables.
This is done using a deconstructing pattern where you specify the shape of the structure for the match
(in this case a two-element list) and optionally bind variables to parts of that structure:
```groovy
for ([i,j] in [[1,2], [3,4], [4,5]]) {
  println "i=$i, j=$j"
}
// Output:
// i=1, j=2
// i=3, j=4
// i=5, j=6
```
You can specify types as well:
```groovy
for ([int idx, String str] in [[1,2], [3,'abc'], ['AAA', 'xyz']]) {
  println "idx=$idx, str=$str"
}
// Output:
// idx=3, str=abc
```
You can use a type on its own to match on the type without actually creating a binding variable:
```groovy
for ([int, String str] in [[1,2], [3,'abc'], ['AAA', 'xyz']]) {
  println "str=$str"
}
// Output:
// str=abc
```
If you don't care about the type and don't need to bind the value you can use `_` as a placeholder:
```groovy
for ([_, String str] in [[1,2], [3,'abc'], ['AAA', 'xyz']]) {
  println "str=$str"
}
// Output:
// str=abc
// str=xyz
```
As well as the binding variables, an implicit variable `it` is bound to the element:
```groovy
for ([_, String str] in [[1,2], [3,'abc'], ['AAA', 'xyz']]) {
  println "it=$it"
}
// Output:
// it=[3, 'abc']
// it=['AAA', 'xyz']
```
You can use constant values in the matching:
```groovy
for ([3, String str] in [[1,2], [3,'abc'], ['AAA', 'xyz']]) {
  println "str=$str"
}
// Output:
// str=abc
```
If you want to use a value from an existing variable to match against you can use `$` to expand
the variable at the relevant place in the pattern (similar to using `$` to expand variables in expression
strings):
```groovy
def id = 3
for ([$id, str] in [[1,2], [3,'abc'], [4,'xyz']) {
  println "str=$str"
}
// Output:
// str=abc
```
A binding variable can appear multiple times, in which case the value at each position must be the same
to match.
For example, to find all two-element sublists with have both elements the same:
```groovy
for ([x,x] in [[1,2], [3,3], [4,5]]) {
  println "x=$x"
}
// Output:
// x=3
```
If a type is specified and the binding variable appears multiple times, the type must be declared on the
first occurrence:
```groovy
for ([int x,_,x] in [[1,2,3], [3,4,3], [4,5,4]]) {
  println "x=$x"
}
// Output:
// x=3
// x=4
```
As well as using `_` as a placeholder for a single value, you can use `*` as a placeholder for any number
of values in a list:
```groovy
for ([a,*] in [[1],[2,3],[4,5,6]]) {
  println "a=$a"
}
// Output:
// a=1
// a=2
// a=4
```
You can also bind a variable to the `*` portion of the list:
```groovy
for ([head,*tail] in [[1],[2,3],[4,5,6]]) {
  println "head=$head, tail=$tail"
}
// Output:
// head=1, tail=[]
// head=2, tail=[3]
// head=4, tail=[5,6]
```
You can, of course, also match against more complex structures and bind variables to parts of those structures:
```groovy
for ([a,[_,int b, a]] in [['a',[1,2,'a']], ['b',[3,4,'c']], [1,2]]) {
  println "a=$a, b=$b"
}
// Output:
// a=a, b=2
```
Maps can also be matched against by specifying which keys the candidate elements need to have in order
to match:
```groovy
for ([name:_,age:_] in [[name:'Jane',age:57], [name:'John',age:33,phone:'1234567']]) {
  println it
}
// Output:
// [name:'Jane', age:57]
```
Note in the example that the second map did not match because it had additional keys in it.
If you would like to match maps that have more than just the keys listed you can use `*` to
match any number of keys:
```groovy
for ([name:_,age:_,*] in [[name:'Jane',age:57], [name:'John',age:33,phone:'1234567']]) {
  println it
}
// Output:
// [name:'Jane', age:57]
// [name:'John', age:33, phone:'1234567']
```
As well as matching individual maps, you can also bind variables to the values of the keys in the maps:
```groovy
for ([name:custName,age:custAge,*] in [[name:'Jane',age:57], [name:'John',age:33,phone:'1234567']]) {
  println "name=$custName, age=$custAge"
}
// Output:
// name=Jane, age=57
// name=John, age=33
```
Unlike when matching with lists, you cannot bind the `*` to a variable.

Another way to match with a deconstructing pattern is to specify a type that is a user defined class.
For example:
```groovy
class X { int i; long j }
class Y extends X { String k }

def collection = [ new X(1,2), new Y(3,4,'abc'), new X(3,4) ]
for (X : collection) {
  println it
}
// Output:
// [i:1, j:2]
// [i:3, j:4, k:'abc']
// [i:3, j:4]

for (Y y in collection) {
  println y
}
// Output:
// [i:3, j:4, k:'abc']
```

You can also use a pattern that looks like a constructor for the class and specify values for fields
you want to match against:
```groovy
for (X(3,4) in collection) {
  println it
}
// Output:
// [i:3, j:4, k:'abc']
// [i:3, j:4]
```
Note how in this example `X(3,4)` also matched the instance of `Y` because `Y` extends `X` and the
instance had fields for `X` that matched.

You can use binding variables instead of constant values:
```groovy
for (X(a,b) in collection) {
  println "a=$a, b=$b"
}
// Output:
// a=1, b=2
// a=3, b=4
// a=3, b=4
```

As well as using positional parameters for the constructor-like pattern, you can use named arguments:
```groovy
for (X(i:3,j:b) in collection) {
  println "b=$b"
}
// Output:
// b=4
// b=4
```
Note that the name of the argument cannot be bound to a variable so the identifier before the `:` 
it is always a fixed string value.
It is only that value that can be bound to a variable.

Fields that are complex types themselves can also be deconstructed in the pattern:
```groovy
class ZZZ { X x; Y y }
def collection = [new X(1,2), new ZZZ(new X(3,4), new Y(5,6,'xyz')), new Y(1,2,'a')]

for (ZZZ(X(a,b), Y(5,6,c)) in collection) {
  println "a=$a, b=$b, c=$c"
}
// Output:
// a=3, b=4, c=xyz
```

:::note
In most of the examples we have shown single letter names for binding variables but the binding variables
can be any legal variable name that does not clash with an existing variable of the same name.
:::

The following table summarises all the pattern forms supported in `for (... in ...)` / `for (... : ...)` loops:

| Pattern                   | Example                               | Description                                                                                                                 |
|---------------------------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Simple variable           | `for (i in list)`                     | Matches all elements and binds each one to `i`.<br/>Type of `i` is inferred if collection is an array or defaults to `def`. |
| Typed variable            | `for (int i in list)`                 | Matches elements that match on type and ignores others.                                                                     |
| Strict match (`:`)        | `for (int i: list)`                   | Use of `:` instead of `in` means there will be an error if an element does not match.                                       |
| List structure            | `for ([i,j] in list)`                 | Matches 2-element sub-lists and binds the contents to the binding variables.                                                |
| Typed list structure      | `for ([int i, String s] in list)`     | Matches 2-element sub-lists with elements of given types and binds values to the variables.                                 |
| Type only                 | `for ([int, String s] in list)`       | Match on type only without binding for one of the elements.                                                                 |
| Wildcard `_`              | `for ([_, String s] in list)`         | Wildcard `_` matches any value at that position.                                                                            |
| Constant value            | `for ([3, String s] in list)`         | Value at given position must match supplied constant value.                                                                 |
| Variable expansion `$`    | `for ([$id, s] in list)`              | Value to match against can come from an existing variable using `$` to expand its value.                                    |
| Repeated variable         | `for ([x, x] in list)`                | Variables occurring multiple times require match to have same value in each position.                                       |
| Wildcard `*`              | `for ([a, *] in list)`                | Wildcard `*` matches any number of elements (including none).                                                               |
| Wildcard `*` with binding | `for ([head, *tail] in list)`         | Variable can be bound to `*` part of a sub-list.                                                                            |
| Nested list               | `for ([a, [_, int b, a]] in list)`    | Recursively match nested list structure.                                                                                    |
| Map key presence          | `for ([name:_, age:_] in list)`       | Match against maps based on presence of keys.                                                                               |
| Map with extra keys `*`   | `for ([name:_, age:_, *] in list)`    | Match against maps using `*` to match against any keys.                                                                     |
| Map value binding         | `for ([name:n, age:a, *] in list)`    | Match agianst maps binding variables to values in the map.                                                                  |
| Class type match          | `for (X : list)`                      | Match based on user class type.                                                                                             |
| Class type with binding   | `for (Y y in list)`                   | Match based on user class type with binding variable.                                                                       |
| Constructor positional    | `for (X(3, 4) in list)`               | Match on user class type where instance fields match constant values.                                                       |
| Constructor with bindings | `for (X(a, b) in list)`               | Match on user class instances binding instance fields to variables.                                                         |
| Constructor named args    | `for (X(i:3, j:b) in list)`           | Match on user class instances with binding variables using named arguments.                                                 |
| Nested class pattern      | `for (ZZZ(X(a,b), Y(5,6,c)) in list)` | Nested constructors with constants and binding variables.                                                                   |

**Notes:**
- `it` is always implicitly bound to the full current element, regardless of the pattern.
- Binding variables can appear multiple times in a pattern and all positions must be equal to match.
- Type must be declared on the **first** occurrence of a repeated variable.
- Map patterns do **not** support binding the `*` wildcard to a variable (unlike list patterns).
- Use of `:` instaed of `in` means that the matching is strict and any element that does not match will generate an error.

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

When you have nested loops, and you would like to be able to use `break` or `continue` and specify which loop you
wish the `break`/`continue` to operate on, you can add labels to the loops and then pass the label to the `break`
or `continue`.
To add a label to a loop, you prepend the loop with a name and a `:` as in this example:
```groovy
int sum = 0;
OUTER: for (int i = 0; i < 10; i++) {
  for (int j = 0; j < 20; i++) {
    continue OUTER if j == i
    sum += i + j
  }
}
```

## Do Expression

The `do` expression allows you to turn a block of statements into an expression.
The value of the `do` expression is the value of the last statement in the block.

For example:
```groovy
def x = do { def i = 3; def j = 4; i + j }
x      // 7
```

This can be useful when you want to use a complex calculation where an expression is needed.
For example, rather than using a temporary variable to store the calculation you can pass the calculation
as a `do` block directly as an argument to a function:
```groovy
def func(int x) { ... }
def x = func(do { def sum = 0; for (i=0; i < 10; i++) { sum += func(i) }; sum })
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

