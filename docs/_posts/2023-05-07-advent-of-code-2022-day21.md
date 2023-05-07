---
layout:     post
title:      "Advent Of Code 2022 - Day 21"
date:       2023-05-07 15:54:15 +1000
categories: blog
author:     "James Crawford"
---

# Day 21: Monkey Math

See [Day 21](https://adventofcode.com/2022/day/21) for a detailed description of the problem.

> Continuing to solve the Advent of Code 2022 problems
> (see [Advent of Code - Day 1]({{ site.baseurl }}{% link _posts/2023-04-06-advent-of-code-2022-day1.md %})).
>
> Links:
> * [Jactl Programming Language](https://jactl.io)
> * [Jactl on Github](https://github.com/jaccomoc/jactl)
>
> To run the example code in this post save the code into file such as `advent.jactl` and take your input from the
> Advent of Code site (e.g. `advent.txt`) and run it like this:
> ```shell
> $ cat advent.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent.jactl 
> ```

## Part 1

In this challenge you are given a list of symbols and a simple mathematical formula for how to work out the value of
each symbol.
The formula can be a number or a binary expression of two other symbols.
The goal is to evaluate all the expressions in order to work out the value of the `root` symbol.

Each line can look like:
```groovy
abcd: 3
```
or
```groovy
efgh: zyxw * abcd
```

I decided not to bother about coding an efficient solution, and instead chose to take advantage of the built-in
`eval` function in Jactl to transform the expressions in to Jactl and then evaluate them.
I pass in a Map of the variables each time and continually evaluate the expressions until I stop getting a value
of `null` back.
If any of the `eval` calls returns `null` it means that there was some sort of error (such as one of the symbols
having a `null` value).
Once every expression returns non-null it means we have a value for all symbols, and so we can just return the value
of `root`.

```groovy
def monkeys = stream(nextLine).map{ s/:/=/; s/(\d+)/$1L/ }.filter{ it }
Map vars = monkeys.map{ s/=.*// }.collectEntries{ [it,null] }
while (monkeys.filter{ eval(it, vars) == null }) {}
vars.root
```

There are quite a few expressions in the actual input, so it does take around 14s to fully evaluate everything
since `eval` has to compile and execute each expression every time.

## Part 2

For part 2 the requirements change a bit.
The expression for the `root` symbol must now be interpreted as an equality check (irrespective of whatever operator
it has).
So something like `root: abcd + efgh` should be interpreted as meaning that we have to verify that `abcd` and `efgh`
are equal.

The trick is that now we have to find the number value for the `humn` symbol that ensures that the equality check
for the `root` symbol will succeed.

First of all, we replace the value for the `humn` symbol with a marker value of `XXXX`.
Then we recursively replace the symbols for the `root` expression with their expressions and replace any symbols
in those expressions with their expressions and so on, building up an expression tree.

This leaves us with a tree where every node is either a number (or `XXXX`) or a subtree with
`[lhs:lhsnode, op:op, rhs:rhsnode]` where the node values can themselves be further subtrees.

Fortunately, `humn` only occurs in one expression so there is only one marker in our tree structure.
This allows us to iteratively manipulate our root expression tree until we end up with `XXXX` on one side on
its own and a subtree on the otherside that we can evaluate to work out the value for `XXXX` and thus give 
the required value for the `humn` symbol.

Since our top root expression is an equality, as long as we perform the same operation to both sides of the equality
we still have an equality that is true.
So if we add the same thing to both subtrees or subtract the same thing, for example, we won't violate the equality.

For the purpose of doing these manipulations we will keep a `lhs` variable which is the side where the marker lives
and an `rhs` variable which is the other side of the equality.
The `lhs` variable will be of the form `lhsnode op rhsnode`.
(For the sake of illustrating how this works, we will assume that the marker is always
in the `lhsnode` subtree of the `lhs` variable.)
We then iterate, peforming the opposite of the current operation.
For example, if the operator is `+` then we would do this:
```groovy
lhs = lhsnode
rhs = [lhs:rhs, op:'-', rhs:rhsnode]
```
When `lhs` is just the marker on its own we have finished.

At that point we convert the `rhs` tree back into a string and evaluate it using `eval`.

```groovy
def monkeys = stream(nextLine).collectEntries{ /(.*): (.*)$/r; [$1, $2] }
def root = monkeys.'root'
def marker = 'XXXX'
monkeys.humn = marker

def parse(it) {
  /^[\d]+$/r              and return it as long
  /^[a-z]+$/r             and return parse(monkeys[it])
  /^${marker}$/r          and return it
  /(.*) ([\/*+-=]) (.*)/r and return [lhs:parse($1), op:$2, rhs:parse($3)]
}
def containsMarker(node) { node == marker || node instanceof Map && (containsMarker(node.lhs) || containsMarker(node.rhs)) }

def tree = parse(root)
def lhs  = containsMarker(tree.lhs) ? tree.lhs : tree.rhs
def rhs  = containsMarker(tree.lhs) ? tree.rhs : tree.lhs
while (lhs.size() == 3) {
  def isLhs = containsMarker(lhs.lhs)
  lhs.op == '+' and rhs = isLhs ? [lhs:rhs, op:'-', rhs:lhs.rhs] : [lhs:rhs,     op:'-', rhs:lhs.lhs]
  lhs.op == '*' and rhs = isLhs ? [lhs:rhs, op:'/', rhs:lhs.rhs] : [lhs:rhs,     op:'/', rhs:lhs.lhs]
  lhs.op == '-' and rhs = isLhs ? [lhs:rhs, op:'+', rhs:lhs.rhs] : [lhs:lhs.lhs, op:'-', rhs:rhs]
  lhs.op == '/' and rhs = isLhs ? [lhs:rhs, op:'*', rhs:lhs.rhs] : [lhs:lhs.lhs, op:'/', rhs:rhs]
  lhs = isLhs ? lhs.lhs : lhs.rhs
}

def toExpr(node) { node !instanceof Map ? "${node}L" : "(${toExpr(node.lhs)} ${node.op} ${toExpr(node.rhs)})" }
eval(toExpr(rhs))
```
