---
layout:     post
title:      "Advent Of Code 2022 - Day 25"
date:       2023-05-09 09:48:49 +1000
categories: blog
author:     "James Crawford"
---

# Day 25: Full of Hot Air

See [Day 25](https://adventofcode.com/2022/day/25) for a detailed description of the problem.

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

This is the last challenge for Advent of Code 2022 and day 25 has only one part, unlike the other days.

A fairly simple challenge for the last day.
The goal is to take a list of numbers encoded in a code they have called SNAFU, sum them, and then output the
sum as a SNAFU number.

In SNAFU the numbers are base 5 but the twist is that the digits are `=`, `-`, `0`, `1`, `2` with `=` meaning `-2`
and `-` meaning `-1`.
Decoding these numbers is pretty straightforward but encoding is a bit trickier.

In the end, not a lot of code for this challenge.

```groovy
def n = stream(nextLine).map{ it.reduce(0L){ v,d -> v*5 + ['2':2,'1':1,'0':0,'-':-1,'=':-2][d] } }.sum()
for (def dval = 0, value = ''; ; n /= 5) {
  dval = (n % 5) + (dval > 2 ? 1 : 0)
  value = ['0','1','2','=','-'][dval % 5] + value
  return value unless n > 0 || dval > 2
}
```
