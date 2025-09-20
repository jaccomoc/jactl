---
title:      "Advent Of Code 2023 - Day 9"
date:       2023-12-10T09:11:29+11:00
categories: blog
image:      /assets/logo-picture.jpg
authors: [james]
---

An easy day where we had to calculate the next number for each of a given set of number series.
After stumbling on a stupid mistake with an iterative approach, the recursive approach worked
first time and ended up as a much nicer solution.

<!--truncate-->

# Day 9 - Mirage Maintenance

See [Day 9](https://adventofcode.com/2023/day/9) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent09.jactl`) and run it like this (where advent09.txt
is your input file from the Advent of Code site for Day 9):
```shell
$ cat advent09.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent09.jactl 
```

## Part 1

For today's challenge we are given a list of number series, one per line, and we need to work out the next number in
each series.
The challenge is pretty prescriptive and even describes the algorithm to use which consists of generating a new
series from the old one based on the differences between consecutive pairs of numbers in the original series until
the new series consists only of 0s.
Then add up the last element of all the sub-series created and add this to the last element of the original series to
get the next number in the original series.

For example:
```
3   10   19    30    43   58
  7    9    11    13    15 
     2    2     2     2
        0    0    0
```
The next number in the first series ends up being `0 + 2 + 15 + 58` which is `75`.

The goal is to find these next numbers for each series and sum them all.

The code is pretty simple:
```groovy
01  def seqs = stream(nextLine).map{ it.split(/ +/).map{ it as long } }
02  def next(s) { s[-1] + (s.allMatch{it == 0} ? 0 : next(s.windowSliding(2).map{it[1]-it[0]})) }
03  seqs.map{ next(it) }.sum()
```

Each series is on a single line so at line 1 we read each line and split on spaces and convert the values into numbers.

Line 2 is our recursive function that takes the last element of the current series `s[-1]` and adds it to either `0`,
if every element is already `0`, or a recursive call to itself passing in a new series which is the differences
between each pair of numbers in the current series.
We use `windowSliding(2)` which turns a list of values into a list of consecutive pairs.

Finally, line 3 invokes our recursive `next()` function on each series and sums the results.

## Part 2

For part 2, the goal is to do the same as in part 1 but instead of finding the next number in the series, we need
to find the previous number in the series.

This can be done by taking the first element of each series and subtracting the first element of the series generated
from the differences of the pairs.

This means a simple change of our code in part 1 at line 2 to change `s[-1] + ...` in our recursive function to `s[0] - ...`:
```groovy
01  def seqs = stream(nextLine).map{ it.split(/ +/).map{ it as long } }
02  def next(s) { s[0] - (s.allMatch{ it == 0 } ? 0 : next(s.windowSliding(2).map{it[1] - it[0]})) }
03  seqs.map{ next(it) }.sum()
```

As was pointed out to me, though, another approach is to just reverse the series and use the original function
from part 1:
```groovy
01  def seqs = stream(nextLine).map{ it.split(/ +/).map{ it as long } }
02  def next(s) { s[-1] + (s.allMatch{it == 0} ? 0 : next(s.windowSliding(2).map{it[1]-it[0]})) }
03  seqs.map{ next(it.reverse()) }.sum()
```

At line 3 we just pass in the reverse of the series using the built-in `reverse()` method.
