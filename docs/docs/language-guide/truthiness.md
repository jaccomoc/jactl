---
title: Truthiness
---

In Jactl we often want to know whether an expression is `true` or not. The _truthiness_ of an expression
(a term stolen from Groovy) is used to determine which branch of an `if` statement to evaluate, or whether
a `for` loop or a `while` loop should continue
or not, for example. In any situation where a boolean `true` or `false` is expected we need to evaluate the given
expression to determine whether it is true or not.

Obviously, if the expression is a simple boolean or boolean value then there is no issue with how to intepret the value:
`true` is true, and `false` is false.

Other types of expressions can also be evalauted in a boolean context and return `true` or `false`.

The rules are:
* `false` is `false`
* `0` is `false`
* `null` values are `false`
* Empty list (`[]`) or empty map (`[:]`) is `false`
* Empty String (`''`) is false
* All other values are `true`

For example:
```groovy
[] && true         // false
[[]] && true       // true
[:] && true        // false
[false] && true    // true
[a:1] && true      // true
'' && true         // false
'abc' && true      // true
1 && true          // true
0 && true          // false
null && true       // false
```

