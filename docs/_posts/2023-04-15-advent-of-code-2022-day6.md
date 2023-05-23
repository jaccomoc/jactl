---
layout:     post
title:      "Advent Of Code 2022 - Day 6"
date:       2023-04-15 10:30:20 +1000
categories: blog
author:     "James Crawford"
---

# Day 6 - Tuning Trouble

See [Day 6](https://adventofcode.com/2022/day/6) for a detailed description of the problem.

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

Pretty easy today.
We get a line of characters and have to find the position of the character after the first occurrence of 4 unique
characters in a row.

We just iterate through the string looking at each 4 character substring.
We sort the substring, and then use `unique()` to eliminate duplicates.
If we still have 4 characters we know that they are unique.

```groovy
def n = 4
def line = nextLine();
line.size()
    .skip(n)
    .filter{ line.substring(it - n,it).sort().unique().size() == n }
    .limit(1)[0]
```

Note that `limit(1)` returns a list.
In this case the list will have a single element so we extract it using `limit(1)[0]`.

## Part 2

For part 2 the only difference is that now the number of unique characters has to be 14:

```groovy
def n = 14
def line = nextLine();
line.size()
    .skip(n)
    .filter{ line.substring(it - n,it).sort().unique().size() == n }
    .limit(1)[0]
```
