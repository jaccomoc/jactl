---
layout:     post
title:      "Advent Of Code 2023 - Day 8"
date:       2023-12-09T11:28:29+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

Another fun day. As usual part 2 could not be brute forced so needed some lateral thinking.

# Day 8 - Haunted Wasteland

See [Day 8](https://adventofcode.com/2023/day/8) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent08.jactl`) and run it like this (where advent08.txt
is your input file from the Advent of Code site for Day 8):
```shell
$ cat advent08.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent08.jactl 
```

## Part 1

For day 8 we are given input that has the first line as a list of right/left moves (a series of `R` and `L`)
followed by a list of nodes where each node describes which of the neighbours is their left neighbour and which is
their right one.

For example:

```
RLLRRRLRL

AAA = (BBB,CCC)
BBB = (DDD,EEE)
CCC = (EEE,FFF)
...
```

The idea is to use the moves to move through the nodes from the staring node `AAA` until we get to node `ZZZ` and
then output how many steps it took.
If we run out of moves before we have reached `ZZZ` we cycle back to the start of the moves list.

The code looks like this:
```groovy
01  def m = nextLine(); nextLine();
02  def nodes = stream(nextLine).map{ /(...) = .(...), (...)./r; [$1, [L:$2,R:$3]] } as Map
03  def cnt = 0
04  for (def n = 'AAA'; n != 'ZZZ'; n = nodes[n][m[cnt++ % m.size()]]) {}
05  cnt
```

At line 1 we read in the moves as a string and advance past the blank line.

Line 2 reads all the nodes and uses a regex to split out the neighbours.
We generate a map of maps where each submap has two members keyed on `'L'` and `'R'`.

Line 4 then iterates from `AAA` until we get to `ZZZ` by looking up the neighbour of the current node based on the
current move, incrementing `cnt` each time.
We look up which move using the current `cnt` as an index after using `%` to make sure it wraps around if we get past
the end of the moves string.
Since `node[n]` is a map keyed on `L` and `R` we use the move as the key to look up which neighbour to move to and
assign that to `n`.

Line 5 returns the `cnt` value.

## Part 2

In part 2 there are multiple "ghosts" moving through the nodes.
There is on ghost per starting node (which is now any node ending in `A`) and each ghosts moves until it reaches a node
ending in `Z`.

We need to work out how many moves it will take for all ghosts to simultaneously be at a terminating node.

Since the number of moves it takes is different for each ghost we need to work out what the cycle length is for each
ghost and then determine the least common multiple for these cycle lengths since at that point they will all be at
a terminating node.

Note we assume that the cycle length for a ghost is not how many steps it takes to reach the first terminating node.
In general, this does not have to be the case since the next node after the first terminating node could lead to
another sequence finishing at yet another terminating node, and so forth.
This would make the challenge much harder.
Luckily the input we are given matches this assumption so we don't have to deal with the more complex general problem.

Here is the code:
```groovy
01  def m = nextLine(); nextLine();
02  def nodes = stream(nextLine).map{ /(...) = .(...), (...)./r; [$1, [L:$2,R:$3]] } as Map
03
04  def count(n) {
05    def cnt = 0
06    while (n !~ /Z$/) { n = nodes[n][m[cnt++ % m.size()]] }
07    cnt
08  }
09
10  def pfactors(n, long start=2, factors=[]) {
11    def p = (n.sqrt()-start+1).map{it+start}.filter{ n % it == 0 }.limit(1)[0]
12    p ? pfactors(n/p,p,factors << p) : factors << n
13  }
14
15  def lcm(nums) {
16    nums.map{ pfactors(it).reduce([:]){ m,it -> m[it as String]++; m } }
17        .reduce([:]){ m,facts -> facts.each{ f,c -> m[f] = [m[f]?:0,c].max() }; m }
18        .flatMap{ f,c -> c.map{ f as long } }
19        .reduce(1L){ p,it -> p * it }
20  }
21
22  lcm(nodes.map{ it[0] }.filter{ /A$/r }.map{ count(it) })
```

We read in the input as per part 1 at lines 1 and 2.

At line 4 we have a function that counts the steps from a given starting node to any terminating node.

Line 10 declares a recursive function that determines the prime factors for a given number `n`, excluding `1`, and
excluding `n` unless `n` is itself prime.
It iterates from the given starting number up until the square root of `n` looking for the first number `p` by which
`n` is divisible.
If we find such a number we add it to a list of factors and then add all factors of `n/p` by recursively calling
ourselves.
If there were no number `p` then we must be prime, so we add ourselves to the list and return that.

Line 15 declares the `lcm(nums)` function for finding the lowest common multiple of a list of numbers.
It finds the prime factors of each number at line 16 and turns the list into a map of counts for each factor.

Then, at line 17 we combine the maps by finding, for each factor, the maximum count needed for each of the input numbers.

At line 18 we turn the map of these counts back into a list of factors where each factor appears the number of times
corresponding to the maximum count calculated at line 17.

Then line 19 multiplies them altogether to get the least common multiple.

Line 22 finds all nodes ending in `A` and finds the cycle length for that node by invokeing `count(it)`.
Then this list of counts is passed to our `lcm()` function to calculate the least common multiple.
