---
layout:     post
title:      "Advent Of Code 2022 - Day 3"
date:       2023-04-13 16:03:13 +1000
categories: blog
author:     "James Crawford"
---

# Day 3 - Rucksack Reorganisation

See [Day 3](https://adventofcode.com/2022/day/3) for a detailed description of the problem.

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

Part 1 involved splitting each input line in half and then finding which letters were in common between
the two halves and then generating a "priority" score for the letter (a-z: 1-26, A-Z:27-52) and summing
the priorities of these common letters.

The only difficulty was remembering to filter out duplicates.

```groovy
stream(nextLine).flatMap{
                   def half1 = it.substring(0,it.size()/2);
                   def half2 = it.substring(it.size()/2);
                   half1.filter{ it in half2 }.sort().unique()
                 }
                .map{ /[a-z]/r ? (int)it - (int)'a' + 1 : (int)it - (int)'A' + 27 }
                .sum()
```

## Part 2

For part 2 we have to group the input lines into sets of 3 and find the letter in common across each set of 3
lines and then sum their priorities as before.

Using the `grouped()` method which groups sets of consecutive elements into a series of lists, this turned out
to be fairly simple to implement.

```groovy
stream(nextLine).grouped(3)
                .flatMap{ a,b,c -> a.filter{ it in b && it in c }.sort().unique() }
                .map{ /[a-z]/r ? (int)it - (int)'a' + 1 : (int)it - (int)'A' + 27 }
                .sum()
```
