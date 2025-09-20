---
title: Switch Expressions
---

The `switch` expression in its simplest form works like a `switch` expression in Java (first introduced in Java 17)
where you pass an expression to `switch()` and then have a list of values to match against with a result for each match.

This differs from the Java `switch` statement where each value matched results in a set of statements being
executed.
In Jactl there is no `switch` statement as but the `switch` expression can be used as either a statement (if you
ignore the result) or an expression.
Note that, unlike a `switch` statement, there is no fall through between different cases and so `break` will
not break out of the switch, but will break out of whatever enclosing `for`/`while` loop the `switch` 
is contained within.

Here is a simple example:
```groovy
switch (x) {
  1       -> println 'one'
  2       -> println 'two'
  default -> println 'unknown'
}
```

In this example there are no result values returned so this works much like a `switch` statement.

Note that, as in Java, an optional `default` case can be used to specify the behaviour when none of the
cases match.

If you want to put more than one case on the same line then `;` can be used as a separator:
```groovy
switch (x) { 1 -> println 'one'; 2 -> println 'two' }
```

If the result of the match involves multiple statements then they need to be wrapped in `{` and `}`:
```groovy
switch (name.split(/ /)[0]) {
  'Fred' -> { 
    fred++
    println "Fred: $fred"
  }
  'Barney' -> {
    barney++
    println "Barney: $barney"
  }
}
```

In Jactl, `switch` is actually an expression so the code after each `->` is evaluated and the resulting
value is returned if that case succeeded:
```groovy
def onesCount = 0
def result = switch (x) {
  1       -> { onesCount++; 'one' }   // returns value of 'one'
  2       -> 'two'
  default -> 'unknown'
}
```

Note that if the result of the code after the `->` is a code block, the result will be the result of the last statement
in the code block.
If the code for the matching case does not result in an actual value (for example the last statement is a `while` loop),
then `null` is returned.

:::warning
Invoking `return` from within a `switch` case will return from the enclosing function rather than cause
the `switch` expression to return that value.
:::

If multiple values should map to the same result you can separate the values with commas:
```groovy
switch (x) {
  1,3,5,7,9  -> 'odd'
  2,4,6,8,10 -> 'even'
}
```

## Matching on Literals

As we have seen in the examples so far, we can use `switch` expressions to match on literals which are standard
primitive types such as `int`, `long`, `double`, `String` and so on.

We can also use literals which are `List` or `Map` literals, and we can support different literal types in the same
`switch` (if the type of the value which is the subject of the `switch` permits the comparison):

```groovy
switch (x) {
  1,2,3,4,'abc' -> 'primitive'
  [1,2,3],[4,5] -> 'list'
  [a:1,b:[4,5]] -> 'map'
}
```

If you try to match against a literal whose type is incompatible with the type of the subject of the `switch` then
you will get a compile error.
For example:
```groovy
int x = 2
switch (x) {
  1,2   -> x
  'abc' -> "x=$x"
}
```
```groovy
io.jactl.CompileError: Type int can never match type String @ line 4, column 3
  'abc' -> "x=$x"
  ^
```

## Matching on Type

In addition to matching against literal values, Jactl also supports testing if an expression is of a given type:
```groovy
switch (x) {
  String         -> 'string'
  int,long       -> 'integer'
  double,Decimal -> 'floating point'
}
```

Types and literal values can be mixed in the same `switch` expression:
```groovy
switch (x) {
  1,2,String -> '1, 2, or string'
  int,long   -> 'other integral value'
}
```

Note that if the order of the matches was the other way around you will get a compile error since `int` already covers
the case of all int values:
```groovy
switch (x) {
  int,long   -> 'other integral value'
  1,2,String -> '1, 2, or string'
}
```
```groovy
Switch pattern will never be evaluated (covered by previous pattern) @ line 3, column 3
  1,2,String -> '1, 2, or string'
  ^
```

User classes can also be matched against:
```groovy
class X {}
class Y extends X {}

def x = new Y()

switch (x) {
  Y -> 'type is Y'
  X -> 'type is X'
}
```
Again, if the match on type `X` in the example was before the match on type `Y` you will get a compile error since
the match on `Y` can never succeed if it appears after the match on its parent class.

## Matching on Regex

Switch expressions can also be used to do regular expression matches and support the use of capture variables like
`$1` in the result for that match case:
```groovy
switch (x) {
  /(.*)\+(.*)/n  -> $1 + $2
  /(.*)-(.*)/n   -> $1 - $2
  default        -> 0
}
```

Note that if the regex pattern has no modifiers after it then it will be treated as a standard expression string
and a literal match will occur instead.
To force it to be a regular expression match you must use a modifier (such as `n` in the example, which forces the
capture variables to be treated as numbers where possible).
The `r` modifier can be used to force the string to be treated as a regular expression if no other modifier is
appropriate.

## Destructuring and Binding Variables

### Wildcards: `_` and `*`

In previous examples we showed matches on literals of type `List` and `Map`.
When matching against a list or map we can use `_` as a wildcard to match against arbitrary values within the list
or map:
```groovy
switch (x) {
  [_,_,_]     -> 'a list of size 3'
  [_,3]       -> 'list of size 2 where last element is 3'
  [k1:_,k2:_] -> 'a map of size 2 with keys k1 and k2'
}
```
We can use `*` to match any number of elements:
```groovy
switch (x) {
  [_,_,*] -> 'a list with at least two elements'
  [k:_,*] -> 'a map with at least one element with key k'
}
```

### Matching on Type

If you want to be able to specify the type of the wildcard you can use a type name instead of `_`:
```groovy
switch (x) {
  [int,int,String]   -> '2 ints and a string'
  [int,String,*]     -> 'list starting with int and string' 
  [k1:int,k2:String] -> 'map with k1 value being an int and k2 value being a String' 
}
```

### Binding Variables

By specifying an identifier you can create a binding variable that binds to that part of the structure in the expression
being switched on (hence the term _destructuring_):

For example, we can match a list or map and extract different elements of the list or map:
```groovy
switch (x) {
  [a,2]         -> a + a  // a will be bound to first element of list if second element is 2
  [a,*,b]       -> a + b  // a bound to 1st element and b bound to last element
  [k1:a,k2:b,*] -> a + b  // a bound to value at k1 and b to value at k2
}
```

:::note
For map literals, only the value can be bound to a binding variable.
The keys themselves are literal values, not binding variables.
:::

Binding variables can occur multiple times but will only match if the value for the variable is the same in all places
where it is used:
```groovy
switch (x) {
  [a,a]   -> "a=$a"  // match all 2 element lists where both elements are the same
  [a,_,a] -> "a=$a"  // 3 element list where first and last are the same 
}
```

Binding variables can also be typed if you want to match on the type:
```groovy
switch (x) {
  [int a, int b], [a] -> "a=$a"         // match 1 or 2 element list if first element is an int 
  [int a, b, *]       -> "a=$a, b=$b"   // match if first element is int
}
```
Note that binding variables are shared across all patterns in the same comma separated list (see first pattern list
in the example above).
This means that their type can only be specified the first time they occur in the pattern list and that if
the variable is used in a subsequent pattern (in the same comma separated list), it inherits the same type
and will only match if the type matches.

Binding variables can occur anywhere within the structure being matched against, no matter how deep the nesting:
```groovy
switch (x) {
  [[a,b],*,[[a,*],[b,*]]] -> a + b
}
```

### Binding Variables at Top Level

Binding variables can appear at the top level of a pattern as well and can also have a type:
```groovy
switch (x) {
  String s -> "string with value $s"
  int i    -> "int with value $i"
  a        -> "other type with value $a"
}
```

The wildcard variable `_` can also appear at the top level and can be used as way to provide a `default` case instead
of using `default`:
```groovy
switch (x) {
  1,2,3 -> 'number'
  _     -> 'other'
}
```
Note that if you use `_` like this where it will match everything, it must occur last since otherwise the other cases
would never be evaluated.
This is different to the `default` case which can occur anywhere in the list of match cases.

### Destructuring Regex

As well as destructured matching based on type, you can also use a regular expression to match a string at a particular
location within a nested structure:
```groovy
switch (x) {
  [/^abc/r, /xyz$/r]     -> 'list of size 2 where first starts with abc and second ends in xyz'
  [name:_, age:/^\d+$/r] -> "map has key called name and key called age whose value is a string of digits"
}
```

Note that if you use a capture group within the regular expressions then the capture variables will refer to the last
regular expression in the pattern.
For example, in the following code `$1` will never correspond to the capture group in the first regular expression.
It will only have a value for the match on the second regex:
```groovy
switch (x) {
  [/^(a|b)+/r, /^(x|y|z)/r] -> "$1 will be x or y or z"
}
```

## Implicit `it`

If you don't specify a subject expression for the `switch` to switch on then it will switch on the implicit `it`
variable.
For example, we can read lines from stdin and parse them using `switch` like this, since the closure passed to `map`
has an implicit `it` parameter:
```groovy
stream(nextLine).map{
  switch {
    /(.*)\+(.*)/n -> $1 + $2
    /(.*)-(.*)/n  -> $1 - $2
    default       -> die "Bad input: $it"
  }
}
```
If you are in a context where there is no `it` then you will get an error about a reference to an unknown `it` variable.

Within the `switch` expression itself, the `it` variable is bound to the value of the expression passed to `switch`.
For example:
```groovy
switch (x.substring(3,6)) {
  'ABC','XYZ'   -> "${it.toLowerCase()}:123"    // it has value of x.substring(3,6)
  'XXY'         -> 'xxy:789'
}
```

## Match with If

In addition to specifying a pattern or literal value to match against, each pattern/literal can also have an
optional `if` expression that is evaluated if the pattern/literal matches and which must then also evaluate to true
for that case to match:
```groovy
switch (x.substring(3,6)) {
  'ABC' if x.size() < 10, 'XYZ' if x.size() > 5 -> "${it}${it}"
  'XXY' if x[0] == 'a'                          -> 'xxy'
}
```

Within the `if` expression, references to any binding variables and to `it` are allowed:
```groovy
switch (x.filter{ it.size() < 10 }) {
  [int a,*]    if a < 3                 -> "list starts with $a < 3"
  [String a,*] if x.size() < 5          -> "x with < 5 elems starts with string $a"
  [a,*,b]      if a.size()  == b.size() -> "first and last elem of same size"
  _            if it.size() == 0        -> 'empty list/string/map'
  _            if it.size() == 1        -> 'single element list/map or single char string'
  _                                     -> 'everything else'
}
```

## Matching on Multiple Values

To match on multiple values at the same time, pass a list of the values to the `switch`:
```groovy
switch ([x,y]) {
  [a,a]         -> 'x equals y'
  [[a,*],[*,a]] -> 'first elem of x is same as last elem of y'
}
```

## Destructured Matching on Class Instances

As mentioned, you can match on a type that is a user defined class.
To match based on the field values, use the constructor form of the class as the pattern.

For example:
```groovy
class X { int i; int j; List list = [] }
X x = new X(i:2, j:3, list:[1,2,[3]])

switch (x) {
  X(i:1)        -> 'type is X: field i is 1'
  X(list:[3,4]) -> 'X with list field being [3,4]'
}
```

If you use the constructor form with named fields (as in the example above) then you only need specify which field
values you are interested in.
All other fields can have any value to match that pattern.

The positional parameter constructor form (that does not use named field values) requires you to supply values for
all mandatory fields:
```groovy
class X { int i; int j; List list = [] }
X x = new X(i:2, j:3, list:[1,2,[3]])

switch (x) {
  X(1,3) -> 'type is X: i=1, j=3'
  X(_,4) -> 'any X as long as j=4'
}
```

As for Maps and Lists, you can "destructure" the fields to bind variables to field values or values within field
values:
```groovy
switch (x) {
  X(i:i,j:j)      -> "X with i=$i, j=$j"
  X(list:[_,_,a]) -> "type is X: last elem of X.list is $a"
}
```

## Variables and Expressions Inside Patterns

If you want to have a pattern that depends on the value of an existing variable then you can use `$` to expand the
value of that variable inside the pattern to disambiguate between a binding variable and an existing variable.
Within the `if` portion of a match the variable can be referenced normally without the `$` since there is no ambiguity.
For example, to match any list whose first element has the same value as the variable `v` or to
match a two element list whose first element is twice the value of `v` and whose last element is `v`:
```groovy
int v = 23
switch (x) {
  [$v,*]             -> 'matched'
  [a,$v] if a == 2*v -> 'matched'
}
```

Just like in expression strings, if the value to be expanded is more than just a simple variable name you can wrap
the expression in `{ }`:
```groovy
int v = 23
switch (x) {
  [${2*v}, $v] -> 'matched'
}
```

You can refer to the value of binding variables (that are already bound) inside the expressions so these two `switch`
expressions are equivalent:
```groovy
switch (x) {
  [a,b,c] if b == 2*a && c == 3*a -> 'matched'
}

switch (x) {
  [a, ${2*a}, ${3*a}] -> 'matched'
}
```

## Switch Literal Comparisons

Note that `switch` expressions will compare numeric values in a slightly differently way than a standard `==` comparison.
In a `switch` expression, numeric value comparisons will only match if the types are the same.
A `long` value will not match an `int` value.
This is to allow you to be able to match on exact types, especially in pattern matches.

For example, consider this:
```groovy
switch (x) {
  1          -> 'int 1'
  1L         -> 'long 1'
  1D         -> 'double 1'
  (Decimal)1 -> 'Decimal 1'
  1.0        -> 'Decimal 1.0'
}
```
When `x` is `1` it will match the pattern that exactly matches its type and value so a `long` will
only match `1L`, for example.

Note that `(Decimal)1` is different to `1.0`.
Even though both are of type `Decimal`, `1` is treated differently to `1.0` because the number of decimal
places is significant.

This means that this will return `false` since the `long` value will not match against an `int` value
of `1`:
```groovy
def x = 1L
switch (x) {
  1       -> true
  default -> false
}
```

This is different to standard comparisons using `==`:
```groovy
def x = 1L
x == 1       // evaluates to true
```

If the compiler knows the type of the value being switched on and can tell that it can never
match one of the literals in the `switch` expression, you will get a compile error.
