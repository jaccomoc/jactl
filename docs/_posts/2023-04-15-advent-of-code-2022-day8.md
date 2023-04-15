---
layout:     post
title:      "Advent Of Code 2022 - Day 8"
date:       2023-04-15 16:02:57 +1000
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

## Day 8 - Treetop Tree House

See [Day 8](https://adventofcode.com/2022/day/8) for a detailed description of the problem.

### Part 1

For part 1 we are given a grid of digits with each digit representing the height of the tree at that location.
We need to determine how many trees in the grid are visible from outside the grid by looking for trees that block
the visibility in the north, south, east, and west directions (in straight lines only).
If there is a tree of the same height or greater in all of these four directions then the tree is not visible, and we
don't count it.

```groovy
def rows = stream(nextLine).map{ it.map{ it as int } }
def cols = rows[0].size().map{ col -> rows.map{ it[col] } }

def invisible = { x,y,h ->
  rows[x].skip(y+1).filter{ it >= h } && rows[x].limit(y).filter{ it >= h } &&
  cols[y].skip(x+1).filter{ it >= h } && cols[y].limit(x).filter{ it >= h }
}

rows.size()
    .flatMap{ x -> cols.size().filter{ y -> !invisible(x,y,rows[x][y]) } }
    .size()
```

We read the grid in row by row and then also convert the rows to columns.
Then we create a function that determines if a tree is invisible.
It is invisible if there is a tree in the same row (in both directions) and a tree in the same column (in both
directions).

Then we just count the trees which aren't invisible.

### Part 2

For part 2 we have to calculate the "scenic score" for each position in the grid and return the maximum value
for this scoring function.
The scenic score is determined by calculating how many trees are visible in each of the four directions before being
blocked by a tree of the same height or greater and multiplying these counts together.

```groovy
def rows = stream(nextLine).map{ it.map{ it as int } }
def cols = rows[0].size().map{ col -> rows.map{ it[col] } }

def score(h,t) { t.mapWithIndex().reduce(null){ r,v -> r ?: (v[0] >= h ? v[1]+1 : null) } ?: t.size() }

def scenicScore = { x,y,h ->
  score(h, rows[x].skip(y+1)) * score(h, rows[x].limit(y).reverse()) *
  score(h, cols[y].skip(x+1)) * score(h, cols[y].limit(x).reverse())
}

rows.size()
    .flatMap{ x -> cols.size().map{ y -> scenicScore(x,y,rows[x][y]) } }
    .max()
```

The `score()` function maps the elements of the tree list in `t` to a sequence of pairs of `[height, index]`
and then uses the `reduce()` method to find the first element which is greater or equal to `h` and return
the index plus 1.
If we have already found such a value then `r` won't be null, and we will ignore any subsequent values in the list. 
