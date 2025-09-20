---
title:      "Advent Of Code 2023 - Day 6"
date:       2023-12-08T10:45:15+11:00
categories: blog
image:      /assets/logo-picture.jpg
authors: [james]
---

Once again, an even day seems to be easier than an odd day.
I am sure it is just a coincidence.

<!--truncate-->

# Day 6 - Wait For It

See [Day 6](https://adventofcode.com/2023/day/6) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent06.jactl`) and run it like this (where advent06.txt
is your input file from the Advent of Code site for Day 6):
```shell
$ cat advent06.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent06.jactl 
```

## Part 1

For day 6 we have to solve a puzzle about boat races.

We are given a list of races with the total time available (in ms) and the best distance (in mm) so far obtained by the
other competitors.

The input looks like this:
```
Time:      8  13  40
Distance:  9  25 300
```

The rule is that the boats can be charged such that after `n` ms the boat will have a speed of `n mm/ms`.
We can decide at what point to let our boat go in order to optimise the total distance it can travel in the given
time.

The goal is to find, for each race, the number of ways in which we can beat the current best distance achieved (i.e.
how many values of `n` will give a better result).

Then we need to multiply these values together for the final result.

The code looks like this:
```groovy
01  def (times,distances) = 2.map{ nextLine().split(/: +/)[1].split(/ +/).map{ it as int } }
02  times.mapWithIndex{ t,i -> t.map{ it * (t - it) }.filter{ it > distances[i] }.size() }
03       .reduce(1){ p,it -> it * p }
```

For part 1 I just took a brute force approach and iterate from `0` to `t` where `t` is the total time for that race,
calculating the distance travelled `it * (t - it)` where `it` is the time at which we let the boat go.

Line 1 reads in the list of times and distances for each race.

Line 2 calculates the number of time values which where will exceed the current best distance for that race,
and line 3 then multiplies these numbers together.

## Part 2

For part 2 the twist is that there is only one race and the spaces between the numbers for the times and the distances
should be ignored.
This means that the size of the numbers is too large to use our naive brute force approach.

Since we have a quadratic equation for the distance travelled after time `n` (`d = n * (T - n)`) we could use
the quadratic formula to find the two roots of `n * (T - n) = D` and calculate the difference between the two roots
to determine the number of values for `n` where our distance is greater than `D`.
I decided, however, to implement a binary search instead as it seemd more fitting for a programming challenge.

Here is the code:
```groovy
01  def (T,D) = 2.map{ (nextLine().split(/: +/)[1] =~ s/ //gr) as long }
02  def bin(a,b,fn) { def m = (a+b)/2.0; b-a < 1 ? (long)m : fn(m) < 0 ? bin(a,m,fn) : bin(m,b,fn) }
03  bin(T/2,T,{ it*(T-it) <=> D }) - bin(0,T/2,{ D <=> it*(T-it) })
```

Line 1 reads in the data and removes the spaces before converting the time and distance to a long value.

Line 2 declares a binary search function that takes a lower bound, an upper bound, and a function that should return
`-1` if we are lower than our goal or `1` if we are higher.
The function continues until the gap between the lower and upper bounds is less thnan 1.
In general, a binary search function would continue until the function returned `0` to indicate we have found the
result but in our case we are only interested in integral values and since the root might be irrational we don't
want to terminate early.

Line 3 calculates the difference between the two roots that we found using our binary search.
This is the number of integral values we can choose where our distance will be greater than `D`.
