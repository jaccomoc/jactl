---
title:      "Advent Of Code 2022 - Day 23"
date:       2023-05-08 16:33:16 +1000
categories: blog
authors: [james]
---

# Day 23: Unstable Diffusion

See [Day 23](https://adventofcode.com/2022/day/23) for a detailed description of the problem.

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

The challenge for today is like a cellular automation simulation (e.g. Conway's Game of Life) but with more complicated
rules to do with elves moving in a grid.
I will not repeat the rules here (see the link above for more details) suffice to say that Elves move until they have
no neighbours and if more than one elf wants to move to the same square, none of them move.
There is no boundary to the grid on which the elves move.

We are given as input a list of squares with either `.` for an empty square or `#` for an elf.

For part 1 we need to find the bounding rectangle after 10 rounds that contains all the elves and count the number of
empty squares.

For this challenge I decided to just keep a list of the elves and build a sparse Map keyed on each elf's coordinates.
If the Map contains a given set of coordinates then that square is occupied, and if the Map does not have that key
then the square is empty.
It is not particularly efficient but gets the job done.

```groovy
def elves = stream(nextLine).map{ it.map{ it } }
                            .mapWithIndex{ it,y -> it.mapWithIndex{ it,x -> [x,y,it] }.filter{ it[2] == '#' } }
                            .flatMap()
def key(p)        { "x:${p[0]},y:${p[1]}"}
def add(p,offset) { [p[0]+offset[0], p[1]+offset[1]] }
def neighbours = [[[0,-1],[-1,-1],[1,-1]], [[0,1],[-1,1],[1,1]], [[-1,0],[-1,-1],[-1,1]], [[1,0],[1,-1],[1,1]]]
def MOVES = 10
MOVES.each{ move ->
  def grid = elves.collectEntries{ [key(it), true] }
  def nextMove(elf,move) { 4.map{ (it+move) % 4}.filter{ !neighbours[it].filter{ grid[key(add(elf,it))] } }.map{ add(elf, neighbours[it][0]) }.limit(1)[0] }
  def hasNeighbour(elf) { neighbours.flatMap().filter{ grid[key(add(elf,it))] } }
  def nonMovingElves = elves.filter{ !hasNeighbour(it) }
  def movingElves    = elves.filter{ hasNeighbour(it) }
  def proposed       = movingElves.map{ elf -> [current:elf, proposed:nextMove(elf, move)] }
  def proposedCount  = proposed.filter{it.proposed}.reduce([:]){ m,it -> m[key(it.proposed)]++; m }
  elves = nonMovingElves + proposed.map{!it.proposed || proposedCount[key(it.proposed)] > 1 ? it.current : it.proposed }
}
def minxy = [elves.map{ it[0] }.min(), elves.map{ it[1] }.min()]
def maxxy = [elves.map{ it[0] }.max(), elves.map{ it[1] }.max()]
(maxxy[0]-minxy[0]+1) * (maxxy[1]-minxy[1]+1) - elves.size()
```

## Part 2

For part 2 we have to keep the simulation going until we get to a point where there are no elves that can move
according to the rules of the simulation.
The move number where this occurs is the solution to the challenge.

This means just adding a `return` when the size of the set of elves not moving is equal to the number of elves.

```groovy
def elves = stream(nextLine).map{ it.map{ it } }
                            .mapWithIndex{ it,y -> it.mapWithIndex{ it,x -> [x,y,it] }.filter{ it[2] == '#' } }
                            .flatMap()
def key(p)        { "x:${p[0]},y:${p[1]}"}
def add(p,offset) { [p[0]+offset[0], p[1]+offset[1]] }
def neighbours = [[[0,-1],[-1,-1],[1,-1]], [[0,1],[-1,1],[1,1]], [[-1,0],[-1,-1],[-1,1]], [[1,0],[1,-1],[1,1]]]
for (int move = 0; ; move++) {
  def grid = elves.collectEntries{ [key(it), true] }
  def nextMove(elf,move) { 4.map{ (it+move) % 4}.filter{ !neighbours[it].filter{ grid[key(add(elf,it))] } }.map{ add(elf, neighbours[it][0]) }.limit(1)[0] }
  def hasNeighbour(elf) { neighbours.flatMap().filter{ grid[key(add(elf,it))] } }
  def nonMovingElves = elves.filter{ !hasNeighbour(it) }
  return move + 1 if nonMovingElves.size() == elves.size()
  def movingElves   = elves.filter{ hasNeighbour(it) }
  def proposed      = movingElves.map{ elf -> [current:elf, proposed:nextMove(elf, move)] }
  def proposedCount = proposed.filter{it.proposed}.reduce([:]){ m,it -> m[key(it.proposed)]++; m }
  elves = nonMovingElves + proposed.map{!it.proposed || proposedCount[key(it.proposed)] > 1 ? it.current : it.proposed }
}
```
