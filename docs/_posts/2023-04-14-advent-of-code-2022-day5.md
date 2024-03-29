---
layout:     post
title:      "Advent Of Code 2022 - Day 5"
date:       2023-04-14 15:30:20 +1000
categories: blog
author:     "James Crawford"
---

# Day 5 - Supply Stacks

See [Day 5](https://adventofcode.com/2022/day/5) for a detailed description of the problem.

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

In part 1 we are given a set of stacks of crates abd then a list of moves where each move moves `n` crates from one
stack to another one.
Then at the end we have to list out the top crate on each stack.

This wasn't difficult but the parsing of the crate configuration was a little messy.

```groovy
def input  = stream(nextLine)
def config = input.filter{ /[\[]/r }.map{ it.grouped(4).map{ it[1] } }
def stacks = config[0].size().map{ i -> ["${i+1}", config.map{ it[i] }.filter{ it != ' '}] }.collectEntries()

input.each{
  /move (.*) from (.*) to (.*)/n or return;
  stacks[$3] = stacks[$2].limit($1).reverse() + stacks[$3]
  stacks[$2] = stacks[$2].skip($1)
}

stacks.map{ a,b -> b[0] ?: ' ' }.join()
```

The first line reads all the input.
Then the second line parses the crate configuration by looking for lines with `[` and splitting the line into
groups of 4 characters, taking the second character from each group.
This allows us to parse lines like:
```
[C]     [E]
[A] [Z] [X]
 1   2   3
```
where sometimes there is no crate.
We will extract the letter for the crate or a space if there is no crate for that stack.

The `it.grouped(4)` splits the string into a sequence of characters and then groups them into sub lists of size 4.
If there are not enough letters for the last group of 4 then the `grouped(4)` will return a list with null for any
missing elements.

The thrid line builds a map from stack label to a list of crates for that stack with the first element in the
list being the top of the stack and the last element being the bottom.

The next section just iterates over the move commands in the input and moves the crates from the given source stack
to the given destination stack.
The `/move .../n or return` is how we skip lines that don't match that pattern.
The `return` in this instance is returning from the closure passed to `each()` which will then move on to the next
line.

Note that when move multiple crates, the crates are moved one at a time starting with the topmost one, so we have
to reverse the order of the crates when moving from one stack to the other.

## Part 2

For part 2 everything is exactly the same as for part 1 except that now when moving `n` crates instead of having
to move them one at a time, we move `n` at once so we no longer have to reverse their order.

```groovy
def input  = stream(nextLine)
def config = input.filter{ /[\[]/r }.map{ it.grouped(4).map{ it[1] } }
def stacks = config[0].size().map{ i -> ["${i+1}", config.map{ it[i] }.filter{ it != ' '}] }.collectEntries()

input.each{
  /move (.*) from (.*) to (.*)/n or return;
  stacks[$3] = stacks[$2].limit($1) + stacks[$3]
  stacks[$2] = stacks[$2].skip($1)
}

stacks.map{ a,b -> b[0] ?: ' ' }.join()
```
