---
title:      "Advent Of Code 2022 - Day 4"
date:       2023-04-14 07:36:04 +1000
categories: blog
authors: [james]
---

# Day 4 - Camp Cleanup

See [Day 4](https://adventofcode.com/2022/day/4) for a detailed description of the problem.

<!--truncate-->

> Continuing to solve the Advent of Code 2022 problems
> (see [Advent of Code - Day 1](2023-04-06-advent-of-code-2022-day1.md)).
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

For part 1 each input line is a pair of number ranges and the job is to count how many lines consist of
number ranges where one is completely within the other.

We use the `n` regex modifier to extract numbers rather than strings, and then we use the `<=>` comparison
operator to compare the two range start values and the two range end values.
The comparison operator returns `-1` if the first value is less than the second value, `0` if they are equal,
and `1` if the first value is larger than the second value.

If we have two ranges `[a1,a2]` and `[b1,b2]` then the two ranges will completely overlap (one will be inside the other)
when:
```
a1 >= b1 && a2 <= b2 || a1 <= b1 && a2 >= b2
```
Rewriting this using the comparison operator, we get:
```
(a1 <=> b1) in [0,1] && (a2 <=> b2) in [-1,0] || (a1 <=> b1) in [-1,0] && (a2 <=> b2) in [0,1]
```
If we add the values of the comparisons then we can see that this is also the same as:
```
(a1 <=> b1) + (a2 <=> b2) in [-1,0,1] || (a1 <=> b1) + (a2 <=> b2) in [-1,0,1]
```
This reduces to just:
```
(a1 <=> b1) + (a2 <=> b2) in [-1,0,1]
```

This makes the code quite simple:

```groovy
stream(nextLine).filter{ /(\d+)-(\d+),(\d+)-(\d+)/n && ($3 <=> $1) + ($4 <=> $2) in [-1,0,1] }
                .size()
```

## Part 2

For part 2 we need to count lines where the two ranges have any overlap (rather than one having to be completely
inside the other).
It is easy to see that there is no overlap when:
```
a2 < b1 || a1 > b2
```
We just filter on the negation of this test since the opposite of no overlap is an overlap:

```groovy
stream(nextLine).filter{ /(\d+)-(\d+),(\d+)-(\d+)/n && !($2 < $3 || $1 > $4) }
                .size()
```
