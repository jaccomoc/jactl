---
title:  "Advent Of Code 2022 - Day 2"
date:   2023-04-06 10:15:00 +1000
authors: [james]
---

# Day 2 - Rock Paper Scissors

See [Day 2](https://adventofcode.com/2022/day/2) for a detailed description of the problem.

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

Although Jactl is similar to Groovy, some differences to note if you are familiar with Groovy:
* `stream(nextLine)` calls `nextLine()` to repeatedly read each line until there is no more input
* `%` is always non-negative (unlike Java and Groovy where `-1 % 3` would be `-1` rather than `2`)

## Part 1

This problem involved calculating the outcome of a series of Rock/Paper/Scissors games based on a specific
scoring mechanism.
Each line of input is three characters in the form `x y` where `x` is one of `A`, `B`, or `C` and `y` is `X`, `Y`, or `Z`,
representing Rock(A or X), Paper(B or Y), and Scissors(C or Z).

The following code first maps our input lines into a pair of values being the offset from `A` and `X` so we end up
with pairs of numbers each being `0`, `1`, or `2`.
Then, the second line works out the scoring for whether there is a win (6 pts), loss (0 pts), or draw (3 pts) and
then (based on the requirements) adds 1, 2, or 3 based on whether the second player has chosen Rock, Paper, or Scissors.
Finally, the result is the sum of the scores of each game:
```groovy
stream(nextLine).map{ [(int)it[0] - (int)'A', (int)it[2] - (int)'X'] }
                .map{ a,x -> [3, 6, 0][(x - a) % 3] + x + 1 }
                .sum()
```

Note that `[3, 6, 0][(x - a) % 3]` is a list of values (`[3, 6, 0]`) from which we take the entry corresponding to
the value of the expression `(x - a) % 3`.
The expression will always result in a value of `0`, `1`, or `2` since `%` is guaranteed to produce non-negative
values (which is different to `%` in Java).

## Part 2

For Part 2, the second letter now maps to the desired outcome (`X` - lose, `Y` - draw, `Z` - win) and we have to
choose the appropriate Rock, Paper, or Scissors to achieve the outcome.
The scoring stays the same, so we just add the second line below to map from desired outcome to the choice
we need to make:
```groovy
stream(nextLine).map{ [(int)it[0] - (int)'A', (int)it[2] - (int)'X'] }
                .map{ a,x -> [a, (a + (x-1)) % 3] }
                .map{ a,x -> [3, 6, 0][(x - a) % 3] + x + 1 }
                .sum()
```
