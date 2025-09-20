---
title:      "Advent Of Code 2022 - Day 14"
date:       2023-04-19 12:06:43 +1000
categories: blog
authors: [james]
---

# Day 14 - Regolith Reservoir

See [Day 14](https://adventofcode.com/2022/day/14) for a detailed description of the problem.

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

Today's challenge is to do with simulating sand falling in a two-dimensional cave filled with rocks.
Each grain of sand falls until it finds an occupied spot (due to a rock or a previous grain of sand occupying that
location).
If there is an empty spot to the left of the rock it moves down and to the left but if that spot is filled it tries
to move down and to the right.
If all three spots are filled the grain comes to rest where it is.

There is an initial set of rock locations specified in the input by a series of coordinates.
Each line is a list of coordinates separated by "->" specifying a rock formation.
A rock formation is formed by drawing from the first coordinates to the second and then from the second to the third
and so on.

Sand always begins to fall from coordinate `[500,0]`.

The aim is to find out how many grains of sand fall before they start falling into the void past the last rock
formation (which will occur when a grain is pushed out to the far left or right of the rock formations due to
previous grains of sand having built up and there being no more space to capture the sand).

```groovy
def grid = []
def set(p,v)    { grid[p[0]][p[1]] = v }
def valueAt(p)  { (grid[p[0]] ?: [])[p[1]] }
def line(p1,p2) { for (def p = p1;; p = [p[0]+(p2[0]<=>p[0]), p[1]+(p2[1]<=>p[1])]) { set(p, '#'); break if p == p2 } }

                                                                                        // Note 1
stream(nextLine).each{ it.split(/ *-> */).reduce(null){ p,it -> /(\d+),(\d+)/n; line(p,[$1,$2]) if p; [$1,$2] } }
for (def count = 0, maxDepth = grid.filter().map{ it.size() }.max(); ; count++) {
  for (def next = [500,0], p = next; p; p = next) {
    return count if valueAt(p) || p[1] > maxDepth                                       // Note 2
    set(p, 'o')
    next = [0,-1,1].map{ [p[0]+it, p[1]+1] }.filter{ !valueAt(it) }.limit(1)[0]         // Note 3
    set(p, null) if next
  }
}
```

Notes:
1. We use reduce to iterate over the points passing the previous one to the next so we can draw a line between the
previous and the current one.
2. We have the result once we have fallen off the edge (`> maxDepth`) or the entry point is already full.
3. We search the three places below us to look for a spare spot, first the spot immediately below us, then the
one to the left, and then the one to the right.
The first one we find that has a null value (unoccupied) is the one we choose for `next`.
If `next` is null then there is nowhere else to go, and we leave the grain of sand where it is and move on to the next
one.

## Part 2

For part 2 the puzzle changes slightly in that we now need to simulate an infinitely wide floor for the cave that sits
at a depth of 2 more than the deepest rock formation.
This means that grains of sand will no longer fall off the edge into the void, but will pile up higher, and higher,
until they get to the entry point where new grains are dropped in.

The goal is to work out how many grains of sand it takes before we get to the point where the entry point is already
full.

Luckily, in part 1 we already check for this condition and return when either the entry point is full or we fall into
the void, so we can reuse the code from part 1.
The only changes we need to make are to capture the maximum depth value plus 2 for the `maxDepth` and to draw a line
for the floor at this level.

```groovy
def grid = [], maxDepth
def set(p,v)    { grid[p[0]][p[1]] = v }
def valueAt(p)  { (grid[p[0]] ?: [])[p[1]] }
def line(p1,p2) { for (def p = p1;; p = [p[0]+(p2[0]<=>p[0]), p[1]+(p2[1]<=>p[1])]) { set(p, '#'); break if p == p2 } }

stream(nextLine).each{ it.split(/ *-> */).reduce(null){ p,it -> /(\d+),(\d+)/n; line(p,[$1,$2]) if p; [$1,$2] } }
line([0, maxDepth = grid.filter().map{ it.size() }.max() + 1], [grid.size()+maxDepth, maxDepth])    // Note 1
for (def count = 0; ; count++) {
  for (def next = [500,0], p = next; p; p = next) {
    return count if valueAt(p)
    set(p, 'o')
    next = [0,-1,1].map{ [p[0]+it, p[1]+1] }.filter{ !valueAt(it) }.limit(1)[0]
    set(p, null) if next
  }
}
```

Notes:
1. We measure the maximum depth + 2 as one more than the largest size of our columns (since the grid is a list of
columns).
The size of a list is one more than the largest index in the list so `size() + 1` will be maximum depth plus 2.
