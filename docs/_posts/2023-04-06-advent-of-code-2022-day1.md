---
layout: post
title:  "Advent Of Code 2022 - Day 1"
date:   2023-04-06 09:45:00 +1000
categories: blog
author: "James Crawford"
---

In order to have some fun and exercise Jactl on some coding problems I have decided to solve the programming
challenges from last year's [Advent of Code - 2022](https://adventofcode.com/2022).
Every year the Advent of Code publishes a challenge for each day of the Advent calendar.

Each challenge has two parts with the second part being some variation of the challenge in the first part.
The catch is that you don't know until solving the first part what change the second part will introduce.
Sometimes solving the second part is easy because you have solved the first part in a generic enough way that it
is easy to modify, but sometimes the second part requires a complete rethink.

It is a lot of fun, and I recommend that you try to solve them yourself using your programming language of choice,
so if you don't want to see my solutions stop reading this post and any other post titled "Advent of Code".

## Day 1 - Calorie Counting

See [Day 1](https://adventofcode.com/2022/day/1) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent01.jactl`) and run it like this (where advent01.txt
is your input file from the Advent of Code site for Day 1):
```shell
$ cat advent01.txt | java -jar jactl-1.0.0.jar advent01.jactl 
```

### Part 1

The task for the first part was to process a file with a list of numbers like:

    1000
    2000

    1000
    3000
    2500

    4000

Each group is separated by a blank line and the job was to sum the numbers in each group and work out which group
has the highest total.
In the example, the second group has a total of `6500` which is higher than both the other groups so the result
should be `6500`.

This was a fairly straightforward problem to solve: 
```groovy
stream(nextLine).join('\n')                                   // Read input and join into one string
                .split(/^\n/, 'm')                            // Split string on empty lines ('m' - multiline mode)
                .map{ it.lines().map{ it as int }.sum() }     // Sum each group
                .max()                                        // Return maximum value
```

### Part 2

The variation for Part 2 was to return the sum of the values for the three highest groups.
This was a simple change to sort in descending order, limit to the first three, and sum them:
```groovy
stream(nextLine).join('\n')                                   // Read input and join into one string
                .split(/^\n/, 'm')                            // Split string on empty lines ('m' - multiline mode)
                .map{ it.lines().map{ it as int }.sum() }     // Sum each group
                .sort{ a,b -> b <=> a }                       // Sort in descending order
                .limit(3)                                     // Limit to first three
                .sum()                                        // Sum them
```
