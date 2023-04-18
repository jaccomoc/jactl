---
layout:     post
title:      "Advent Of Code 2022 - Day 11"
date:       2023-04-18 08:32:27 +1000
categories: blog
author:     "James Crawford"
---

Continuing to solve the Advent of Code 2022 problems
(see [Advent of Code - Day 1]({{ site.baseurl }}{% link _posts/2023-04-06-advent-of-code-2022-day1.md %})).

Links:
* [Jactl Programming Language](https://jactl.io)
* [Jactl on Github](https://github.com/jaccomoc/jactl)

To run the example code in this post save the code into file such as `advent.jactl` and take your input from the
Advent of Code site (e.g. `advent.txt`) and run it like this:
```shell
$ cat advent.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent.jactl 
```

## Day 11 - Monkey in the Middle

See [Day 11](https://adventofcode.com/2022/day/11) for a detailed description of the problem.

### Part 1

This puzzle has a bunch of monkeys throwing items amongst each other based on a "worry" score for each item
(read the [puzzle description]https://adventofcode.com/2022/day/11) for a full explanation.
Each monkey starts with a given set of items and for each round, each monkey performs an operation
like `old * 19` to calculate the new score of each of the items they hold.
The new score is then divided by 3 and then, based on whether it is divisible by a given number (different per monkey),
the item is passed to one of two other monkeys.

The input consists of parsing input like this:
```
Monkey 0:
  Starting items: 79, 98
  Operation: new = old * 19
  Test: divisible by 23
    If true: throw to monkey 2
    If false: throw to monkey 3

Monkey 1:
  Starting items: 54, 65, 75, 74
  Operation: new = old + 6
  Test: divisible by 19
    If true: throw to monkey 2
    If false: throw to monkey 0
    
...    
```

This is pretty easy to do with regex patterns, and we parse each monkey's details into a map of values that looks like
this:
```
[
  inspections:0,
  items:[79, 98],
  operation:'old * 19',
  divisible:23,
  true:2,
  false:3
]
```

I always write the code based on the test input given before trying to apply the solution to my specific input,
so I didn't know how complex the expressions for `operation` could be.
The format was not specified and the examples had `old * 19`, `old + 6`, `old * old`, and `old + 3`.
I decided to make my solution completely generic and took advantage of the `eval` statement to evaluate the
expression each time, passing in a Map which contained the `old` field set to the current worry score.
This made the code pretty simple.

Finally, the result after 20 rounds is determined by finding the two busiest monkeys (in terms of how many items
they inspect) and multiply their total item inspection counts together.

```groovy
def monkeys = [], monkey
stream(nextLine).each{
  /^Monkey (.*):/n                     and do { monkey = [inspections:0]; monkeys[$1] = monkey }
  /^ *Starting items: (.*)$/r          and do { monkey.items = $1.split(/, /).map{ it as int } }
  /^ *Operation: *new *= *(.*)$/n      and monkey.operation = $1
  /^ *Test: divisible by (.*)$/n       and monkey.divisible = $1
  /^ *If (.*): throw to monkey (.*)$/n and monkey.($1)      = $2                           // Note 1
}
def processMonkey = { monkey ->
  def items = monkey.items
  monkey.items = []
  items.each{ item ->
    item = eval(monkey.operation, [old: item]) / 3                                         // Note 2
    monkeys[item % monkey.divisible ? monkey.false : monkey.true].items <<= item           // Note 3
    monkey.inspections++
  }
}
20.each{ monkeys.each(processMonkey) }
monkeys.map{ it.inspections }.sort{ a,b -> b <=> a }.limit(2).reduce(1){ m,it -> m*it }
```

Notes:
1. `monkey.($1) = $2` will store the value from the second regex group into the field name of the first regex group 
(in this case the field will be `true` or `false`). 
2. We use `eval` to evaluate the expression, passing in the current value as the value for the `old` global variable.
We then divide by 3 as specified by the requirements (this prevents the numbers blowing up into huge values).
3. We determine whether the new score is divisible by the `monkey.divisible` and if not divisible add the item to the
monkey whose value is given by the `monkey.false` field and if it is divisible we add the item to the monkey given by 
the `monkey.true` field. 
Note that `x % y` in a boolean expression will be `true` when it is not divisible since when it is not divisible it
evaluates to non-zero. 

### Part 2

For part 2, the only difference is that we no longer divide the scores by 3 each time, and instead of 20 iterations,
we now have 10000 iterations.

This means that the values quickly explode beyond even what a 64 bit long can handle.

Since Jactl can use BigDecimal values for decimal values, we could use those instead since they can scale to whatever
value is needed (as long as you have enough memory).
The number of iterations and the size of the numbers, however, means that it would run for a prohibitively long
period of time even if there was enough memory.

We only need the value to determine which monkey to give the item to, and this is determined by whether the
score is divisible by the monkey's `divisible` value.
Since the items pass between any number of monkeys, if we calculate the product of all of the `divisible` values
and just keep the score modulo this product, this will give us a score that still gives us the correct result
when looking at the remainder based on the `divisible` value for any individual monkey.

> **Proof:** If you take the remainder of a number `n` dividing by some product `p * q` and then take the result of
> that and calculate the remainder dividing by one of the divisors of the product (e.g. `q`) the result will be the
> same as the remainder of `n` after dividing by `q`:
> ```
> (n % (p * q)) % q == n % q 
> ```
> This is easily proven by expressing `n` as `x*p*q + (y*q + z)` where `y*q + z < p*q` and `z < q` and using the
> definition of the remainder operator (`n % d == n - (int)(n/d)` when `n >= 0`).

The code for part 2 now looks like this:

```groovy
def monkeys = [], monkey
stream(nextLine).each{
  /^Monkey (.*):/n                      and do { monkey = [inspections:0L]; monkeys[$1] = monkey }
  /^ *Starting items: (.*)$/r           and do { monkey.items = $1.split(/, /).map{ it as long } }
  /^ *Operation: *new *= *(.*)$/r       and monkey.operation = $1
  /^ *Test: divisible by (.*)$/n        and monkey.divisible = $1
  /^ *If (.*): throw to monkey (.*)$/n  and monkey.($1)      = $2
  /^ *If false: throw to monkey (.*)$/n and monkey.false     = $1
}
def divisorProduct = monkeys.reduce(1){ m,it -> it.divisible * m }                           // Note 1
def processMonkey = { monkey ->
  def items = monkey.items
  monkey.items = []
  items.each{ item ->
    item = eval(monkey.operation, [old: item]) % divisorProduct                              // Note 2
    monkeys[item % monkey.divisible ? monkey.false : monkey.true].items <<= item
    monkey.inspections++
  }
}
10000.each{ monkeys.each(processMonkey) }
monkeys.map{ it.inspections }.sort{ a,b -> b <=> a }.limit(2).reduce(1){ m,it -> m*it }
```

Notes:
1. We calculate the product of all the `divisible` values of the monkeys using the `reduce()` method. This is passed
a initial value `1` and a closure which takes two values &mdash; the last value, and the current element &mdash; and
returns the next value. 
2. This is the change from part 1 where we used to divide by 3, and now we take the remainder after dividing by the
product of the `divisible` values.
