---
title:      "Advent Of Code 2022 - Day 24"
date:       2023-05-09 08:34:33 +1000
categories: blog
authors: [james]
---

# Day 24: Blizzard Basin

See [Day 24](https://adventofcode.com/2022/day/24) for a detailed description of the problem.

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

The challenge for day 24 was to navigate from one side of a map to the other.
The twist is that in many of the squares there are blizzards.
The blizzards are represented by `>`, `<`, `^`, or `v` which indicates the direction in which they move (right, left,
up, or down).
For every turn they will move in that direction until they reach the end of the map, at which point they wrap around
to the other side and continue from there.

The challenge is to work out a path from the given entrance at the top of the map to the given entrance at the bottom
without landing on a square where there is a blizzard.
This is a bit like playing [Frogger](https://en.wikipedia.org/wiki/Frogger) but with obstacles moving in all four
directions.

I realised that it was unnecessary to actually simulate the movement of the blizzards meaning that I could avoid
having to create a new map for every move.
Since the blizzards wrap around at the map boundaries, it is simple to determine for a given square at a given time
whether a blizzard will be occupying that square by checking whether in the same row at time `0` (our initial map)
there is a blizzard that is `time` squares away to the left of type `>` or a blizzard `time` squares to the right
with type `<` where the distance is calculated modulo the width of the map.
Similarly, the same check is done for the column looking for blizzards of type `^` and `v` that are `time` squares
away.

Then, I just did a breadth-first search to find a path from the start to the end.

```groovy
def rows   = stream(nextLine).map{ it.map{it} }, width = rows[0].size(), height = rows.size()
def cols   = width.map{ x -> rows.map{ it[x] } }
def start = [rows[0].mapWithIndex{ it }.filter{ it[0] == '.' }.map{ it[1] }.limit(1)[0], 0]
def end   = [rows[-1].mapWithIndex{ it }.filter{ it[0] == '.' }.map{ it[1] }.limit(1)[0], rows.size() - 1]

def wrapped(n, maxn) { (n-1) % (maxn-2) + 1 }
for (def current = [start], time = 0; ; time++) {
  current = current.flatMap{ pos ->
    [[1,0],[0,1],[-1,0],[0,-1],[0,0]]
      .map{ dx,dy -> [pos[0]+dx, pos[1]+dy] }
      .filter{ x,y -> y >= 0 && y < height && cols[x][y] != '#' }
      .filter{ x,y -> rows[y][wrapped(x+time,width)]  != '<' && rows[y][wrapped(x-time,width)]  != '>' }
      .filter{ x,y -> cols[x][wrapped(y+time,height)] != '^' && cols[x][wrapped(y-time,height)] != 'v' }
  }.sort().unique()
  return time if end in current
}
```

## Part 2

For part 2 we need to determine the time it takes to go from the start to the end, back to the start, and then
back to the end again.

This was pretty easy.
I wrapped the existing bread-first search in a function that takes the start and end positions along with the start
time and then invoked it three times.
The time returned for the last invocation is then the total time needed.

```groovy
def rows   = stream(nextLine).map{ it.map{it} }, width = rows[0].size(), height = rows.size()
def cols   = width.map{ x -> rows.map{ it[x] } }
def start = [rows[0].mapWithIndex{ it }.filter{ it[0] == '.' }.map{ it[1] }.limit(1)[0], 0]
def end   = [rows[-1].mapWithIndex{ it }.filter{ it[0] == '.' }.map{ it[1] }.limit(1)[0], rows.size() - 1]

def wrapped(n, maxn) { (n-1) % (maxn-2) + 1 }
def shortestTime(start, end, time) {
  for (def current = [start]; ; time++) {
    current = current.flatMap{ pos ->
      [[1,0],[0,1],[-1,0],[0,-1],[0,0]]
        .map{ dx,dy -> [pos[0]+dx, pos[1]+dy] }
        .filter{ x,y -> y >= 0 && y < height && cols[x][y] != '#' }
        .filter{ x,y -> rows[y][wrapped(x+time,width)]  != '<' && rows[y][wrapped(x-time,width)]  != '>' }
        .filter{ x,y -> cols[x][wrapped(y+time,height)] != '^' && cols[x][wrapped(y-time,height)] != 'v' }
    }.sort().unique()
    return time if end in current
  }
}
[[start,end],[end,start],[start,end]].reduce(0){ t,it -> shortestTime(it[0],it[1],t) }
```
