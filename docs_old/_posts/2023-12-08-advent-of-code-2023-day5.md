---
layout:     post
title:      "Advent Of Code 2023 - Day 5"
date:       2023-12-08T10:45:15+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

Part 2 of day 5 was a bit of a challenge to get right.
A nice feeling to have completed it.

# Day 5 - If You Give a Seed a Fertilizer

See [Day 5](https://adventofcode.com/2023/day/5) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent05.jactl`) and run it like this (where advent05.txt
is your input file from the Advent of Code site for Day 5):
```shell
$ cat advent05.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent05.jactl 
```

## Part 1

For day 5 we are given a list of numbers (called "seeds" in the puzzle) and then 7 different mappings to apply in
sequence to each of the initial numbers.

Each mapping is a list of rules about how to transform a given input range into an output range.
If none of the rules in a mapping apply to the input number then the input number is left as is.

See [Day 5](https://adventofcode.com/2023/day/5) for a proper explanation of what the input looks like and how
the rules should be applied.

Here is the code for part 1:
```groovy
01  def seeds = nextLine().split(/: */)[1].split(/ +/).map{ it as long }; nextLine()
02  def maps = stream(nextLine).filter{ ':' !in it }.join('\n').split('\n\n').map{ it.lines().map{ it.split(/ +/).map{ it as long } } }
03  def mapping(m,s) { (m.map{ s >= it[1] && s < it[1]+it[2] ? it[0]+s-it[1] : null }.filter()+[s])[0] }
04  seeds.map{ maps.reduce(it){ p,it -> mapping(it,p) } }.min()
```

Line 1 reads the seed numbers which are just a space seperated list of numbers appearing after `seeds: ` on the first
line.

Line 2 reads each mapping.
We join all the remaining lines together (ignoring lines with `:` which indicate a mapping label that is not
relevant to how the rules work) and then split on blank lines (which separate each mapping).
We then capture each mapping rule into a list of numbers. 

Each rule in a mapping is of the form `dst src count` which means to transform the input value `n`, if it is in the range
`src <= n < src + count` by turning it into `dst + (n - src)`.

Line 3 creates a function that given a mapping (a list of these rules), and a seed number `s` returns the transformed
value (if any of the rules apply) or `s` if none of the source ranges apply.
We iterate over the rules and return the mapped values for each rule, finally adding `[s]` at the end.
Then, we end up with either `[x, s]` if a rule applied or `[s]` if no rules applied and so `[0]` returns the first
value of our list.
Note that only one rule will apply but even if there were multiple rules that worked for a given mapping, only the
first rule that applies will win.

Finally, line 4 runs all the mappings in sequence for every seed number and returns the minimum result (as required
by the puzzle).

## Part 2

Part 2 changes the rules so that the seed numbers are not actual numbers but pairs of numbers giving a range of
values.
So `4 5 6 7` now means inclusive intervals of `4-8` and `6-12`.

The goal is still to find the minimum value after mapping all the numbers in the input ranges but since the numbers
in the input are so large iterating over the numbers in the ranges would take an inordinately long time so we need
to find a better solution.

The approach, now, is to track the intervals (ranges) of numbers as they flow through the mappings.

The problem is that since a rule in a mapping might apply to only part of the input interval we need to split the
input interval into sub-intervals each time we apply a rule that matches.

For example, a rule like `10 20 5` given an input interval of `20-30` results in two intervals: `10-14` and `25-30`.
Then these two intervals need to be applied to each subsequent rule in a mapping potentially resulting in even more
intervals.

The next mapping has to take the intervals output from the previous mapping and do the same thing.

Here is the code:
```groovy
01  def seeds = nextLine().split(/: */)[1].split(/ +/).map{ it as long }; nextLine()
02  def maps = stream(nextLine).filter{ ':' !in it }.join('\n').split('\n\n')
03                             .map{ it.lines().map{ it.split(/ +/).map{ it as long } } }
04                             .map{ m -> m.map{ [it[0],[it[1],it[1]+it[2]-1]] } }
05
06  def intersections(s, ivls) { ivls.filter{ intersects(s, it) }.map{ [[s[0],it[0]].max(), [s[1],it[1]].min()] } }
07  def intersects(a, b) { !(a[1] < b[0] || a[0] > b[1]) }
08  def subtract(a, b) { [[[a[0], b[0]].min(), [a[0], b[0]].max()- 1], [[a[1], b[1]].min()+ 1, [a[1], b[1]].max()] ].filter{it[1] >= it[0] } }
09  def subtractAll(a, ivls) { ivls = ivls.filter{ intersects(a, it) }; !ivls ? [a] : ivls.flatMap{ subtract(a, it) } }
10
11  def mapping(m,ranges) {
12    def result = m.reduce([src:ranges,dst:[]]){ p,mi ->
13      def mappable  = intersections(mi[1], p.src)
14      def notMapped = p.src.flatMap{ subtractAll(it, mappable) }
15      [src:notMapped, dst:p.dst + mappable.map{ [mi[0] + it[0]-mi[1][0], mi[0] + it[1]-mi[1][0]] }]
16    }
17    result.src + result.dst
18  }
19
20  seeds.grouped(2)
21       .map{ [it[0], it.sum() - 1] }
22       .flatMap{ maps.reduce([it]){ p,m -> mapping(m,p) } }
23       .min{ it[0] }[0]
```

Line 1 reads in the seeds the same way as in part 1.

Lines 2-4 read in the mappings as in part 1 but this time we convert the input rules from `dst srcStart count` to
`dst srcStart srcEnd` by adding the count to `src` and substracting `1` to make it an inclusive range, so we can
treat is an interval of numbers.

Line 6 creates a function that takes an interval `s` and a list of intervals `ivls` and returns a list of intervals
which are the intersections between `s` and each interval in `ivls` (when such an intersection exists).

Line 7 is a function that returns true if the two intervals intersect.

Line 8 returns the list of intervals resulting in substracting interval `b` from interval `a`.
Note that this can return 0, 1, or 2 values depending on how the intervals overlap (or don't).

Line 9 returns a list of intervals resulting from substracting each interval in `ivls` from the interval `a`.

Line 11 declares a function that applies the rules in a mapping `m` to the list of intervals `ranges`.
The result will be another list of intervals.
This function works by iterating over the rules for the mapping (line 12) and calculating the intersections between
the source range of the rule and the current list of input intervals (line 13). 
These intersections are the ones that we can map using the current rule.

Line 14 then removes these intervals from the current set of input intervals leaving behind a new set of intervals
for the inputs that are not mappable by this rule.
These non-mapped intervals will then be used as the input (`src`) for the next rule in the mapping in line 15 where we
also pass in the results of mapping the mappable intervals into their destination values (`dst`).

At line 17 we return the result which is any intervals from the input that were not mapped by any rule appended with
the results of the mappings for sub-intervals that could be mapped.

Line 20 groups the seeds into pairs and at line 21 we turn the pair into an interval.

Then at line 21 we apply the mappings to this interval to get a list of resulting mapped intervals.
By using `flatMap()` we turn the results for all seeds into one list of results.

Finally, at line 23, we find the interval with the lowest lower bound and return that lower bound as the result.
