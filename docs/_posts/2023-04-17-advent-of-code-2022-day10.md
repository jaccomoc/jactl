---
layout:     post
title:      "Advent Of Code 2022 - Day 10"
date:       2023-04-17 15:40:16 +1000
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

## Day 10 - Cathode-Ray Tube

See [Day 10](https://adventofcode.com/2022/day/10) for a detailed description of the problem.

### Part 1

For this exercise we need to simulate a very simple CPU that has only two instructions:
1. `noop  &nbsp;` - does nothing (takes 1 cycle to complete), and
2. `addx n` - add `n` to the `X` register (takes 2 cycles to complete). 

Given a sequence of instructions we then need to calculate the "signal strength" at cycle 20, 60, 100, 140, 180, and
220 where the signal strength is the cycle multiplied by the value of `X` at that point and then sum these 6 signal
strengths to get a final result.

This was pretty simple to achieve:

```groovy
def x = [1]
stream(nextLine).each{
  /noop/r       and x <<= x[-1]
  /addx (.*)$/n and x += [x[-1], x[-1] + $1]
}
6.map{ 20 + it*40 }.map{ it * x[it-1] }.sum()
```

We process the sequence of instructions and keep a list of the values of `X` for each cycle.
The only tricky part is that `addx`, since it takes 2 cycles to complete still has the old value of `X` after one
cycle, so we have to duplicate the previous `X` value before adding the new one to our list.

Note that we use `x[-1]` to get to the last element of the list as negative indices are treated as an offset from
the end of the list.

At the end, we calculate our 6 signal strengths and sum them.


### Part 2

For part 2, we now have to interpret the `X` values as the horizontal position of a 3 pixel wide sprite.
The cycles correspond to the position on a cathode ray tube which has 6 lines of 40 characters.
At any cycle we need to work out whether for that line if the sprite would be visible.

We take the cycle remainder 40 to get the horizontal position on the line and then, if the distance between
that position and the midpoint of the sprite (the `X` register value) is less than or equal to 1, we know that
the sprite is visible at that point, and we draw `#`.
Otherwise, we draw a space.

```groovy
def x = [1]
stream(nextLine).each{
  /noop/r       and x += x[-1]
  /addx (.*)$/n and x += [x[-1], x[-1] + $1]
}

240.map{ (x[it] - (it%40)).abs() <= 1 ? '#' : '.' }
   .grouped(40)
   .map{ it.join() }
   .join('\n')
```

For the first 240 cycles we generate a value of `#` or ` ` and then group them into lines of 40, join each line
back into a string, and then join these strings with new lines.

The six lines output will (if you squint a little) look like a series of uppercase letters which is the actual
solution to the puzzle that needs to be submitted.
