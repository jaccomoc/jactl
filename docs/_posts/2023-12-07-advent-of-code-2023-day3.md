---
layout:     post
title:      "Advent Of Code 2023 - Day 3"
date:       2023-12-07T11:28:09+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

After an easyish day 2, the difficulty level bumped up a bit again for day 3.

# Day 3 - Gear Ratios

See [Day 2](https://adventofcode.com/2023/day/3) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent03.jactl`) and run it like this (where advent03.txt
is your input file from the Advent of Code site for Day 3):
```shell
$ cat advent03.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent03.jactl 
```

## Part 1

In part 1 we are given a grid consisting of digits, dots, and symbols (such as '+', '#', '*').
The idea is to find in the grid sequences of digits and treat these sequences as a number and sum all numbers in
the grid that match the criteria that one of the digits in the number must be adjacent to a symbol.
Adjacent, for this puzzle, means any neighbour in the 8 surrounding squares of the grid.

As usual, for puzzles involving grids, I added a border of dots around the grid to avoid having to check for
out-of-bounds conditions when looking for neighbours at the edge of the grid.

Here is the code:
```groovy
def (rows, D) = [stream(nextLine), [-1,0,1]]
def g = ['.' * (rows[0].size()+2)] + rows.map{ '.'+it+'.' } + ['.' * (rows[0].size()+2)]

def numNearSym(x,y) { g[y][x] =~ /\d/ && D.flatMap{ dx -> D.map{ dy -> [x+dx,y+dy] } }.anyMatch{ x1,y1 -> g[y1][x1] !~ /[.\d]/ } }

g.mapWithIndex{ r,y -> r.size().map{ x -> g[y][x] + (numNearSym(x, y) ? 'S' : '') }.join() }
 .flatMap{ it.split(/[^\dS]+/).filter{ 'S' in it }.map{ s/[^\d]+//g }
 .map{ it as int } }.sum()
```

The first line assigns all input lines to `rows` and assigns `[-1,0,1]` to `D` for use when calculating deltas
to `x,y` coordinates later.

Then we create our grid of lines in the variable `g`, adding a row at the beginning and end and adding a dot to the
beginning and end of each row.

The function `numNearSym()` checks if at `x,y` we have a digit and then looks for a neighbour that is not a digit and
is not a `.` and returns true if we are a digit with such a neighbour.

Then we iterate over the rows using `mapWithIndex()` and append `S` to each digit in the row that has a neighbouring
symbol.

We take the resulting lines with these `S` characters and use the regex split to split the line into a list of substrings
that were separated by anything that was not a digit or `S`.
This gives us our candidate numbers.
We filter for only numbers with an embeded `S` (since these are the ones with symbol neighbours), remove the `S` 
characters and convert into numbers before summing them.

## Part 2

For part 2 we are only interested in numbers that have a neighbour that is a `*` and only if that `*` has exactly
two numbers for which it is a neighbour.
Then we multiply the two numbers together and sum the resulting products.

```groovy
01 def lines = stream(nextLine)
02 def g = ['.' * (lines[0].size()+2)] + lines.map{ '.' + it + '.' } + ['.' * (lines[0].size()+2)]
03 
04 def nearest2Nums(x,y) {
05   def nums = [[-1,0,1].flatMap{ dx -> [-1,0,1].map{dy -> [x+dx, y+dy] } }
06                       .filter{ x1, y1 -> g[y1][x1] =~ /\d/ }
07                       .map{ x1,y1 -> [x1 - (x1+1).filter{ g[y1][x1-it] !~ /\d/ }.limit(1)[0] + 1, y1] }
08                       .sort().unique()].filter{ it.size() == 2 }[0]
09   nums ? nums.map{ x1,y1 -> g[y1].substring(x1,(g[y1].size()-x1).map{x1+it+1}.filter{ g[y1][it] !~ /\d/ }.limit(1)[0]) as int }
10             .grouped(2).map{ it[0] * it[1] }[0]
11        : null
12 }
13
14 g.size().flatMap{ y -> g[y].size().filter{ g[y][it] == '*' }.flatMap{ nearest2Nums(it, y) } }.sum()
```

As in part 1, we read the lines and add a border of dots on all sides.

We define a function `nearest2Nums(x,y)` that for a given location check if any neighbours are digits (line 6).
Then at line 7 we find the start location in the current row for the number that contains that digit and return the
`[x,y]` location for that number.

At line 8 we sort these coordinates and eliminate duplicates (since one number might have multiple digits neighbouring
that '*') and then filter solutions where there are exactly 2 numbers.
Since we have wrapped the result in `[` and `]` we actually have a list of a list of coordinate pairs and so the filter
will return either this list (if it has 2 elements) or null if the list is filtered out.
The `[0]` unwraps the resulting list back into a simple list of coordinate pairs.

At line 9, if the `nums` result was non-null, we map the coordinates into the numbers at those locations by finding
the ending digit, grabbing the substring and converting to an `int`.

Line 10 turns the 2 element list into a list of a list of 2 elements so we can multiply the two numbers together.

Line 11 returns `null` if the location did not have exactly two neighbouring numbers.

Line 14 searches all grid locations for '*' and uses the `nearest2Nums()` function to return null or the product of
the 2 numbers (if they exist).
By using `flatMap()` we ignore `null` values and can then invoke `sum()` to sum everything together.
