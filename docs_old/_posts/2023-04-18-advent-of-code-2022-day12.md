---
layout:     post
title:      "Advent Of Code 2022 - Day 12"
date:       2023-04-18 08:32:27 +1000
categories: blog
author:     "James Crawford"
---

# Day 12 - Hill Climbing Algorithm

See [Day 12](https://adventofcode.com/2022/day/12) for a detailed description of the problem.

> Continuing to solve the Advent of Code 2022 problems
> (see [Advent of Code - Day 1]({{ site.baseurl }}{% link _posts/2023-04-06-advent-of-code-2022-day1.md %})).
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

For this challenge, we are given a grid of lowercase letters, with each letter representing a height (`a` &mdash; lowest
to `z` &mdash; highest).
In addition, there is one square marked `S` for "start" and one marked `E` for "end".
The start square has a height of `a` and the end square has a height of `z`.

The task is to find the shortest path from the start to the end and return the distance along that path.
The rule is that we can move from one square to the next if it is an immediate neighbour (not including
diagonals) and if the height of the square we are moving to is the same or lower than us, or if the height
of the destination square is at most one more than our current height.

I used [Dijkstra's Algorithm](https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm) to solve this.
We start at the first square and mark it with a distance of 0 and add it our current squares we are visiting.

Then we iterate, finding all "reachable" neighbours (ones whose height is such that we are allowed to
move there) and adding them to the current list of squares.
If the neighbour is reachable, then we mark it with a distance of our current distance plus 1 unless it
already has a smaller distance.
All such reachable neighbours are then added to the current list of squares we are visiting.

As we visit each square we mark it as visited and remove it from the current list.
If we have already visited a square we ignore it.

We finish iterating once we find the end square or when there are no more squares left in the list to process
(which would mean there was no path from the start to the end).

```groovy
def grid = [], y = 0, start, end
stream(nextLine).each{ it.size().each{ x -> grid[x][y] = [x:x, y:y, height:it[x], dist:0x7fffffff] }; y++ }

def inGrid(x,y) { x >= 0 && x < grid.size() && y >= 0 && y < grid[0].size() }
def neighbours(sq) { [[sq.x-1,sq.y],[sq.x+1,sq.y],[sq.x,sq.y-1],[sq.x,sq.y+1]].filter(inGrid).map{ x,y -> grid[x][y] } }

grid.flatMap{ it.map() }.filter{ it.height == 'S' }.each{ start = it; start.height = 'a'; start.dist = 0 }
grid.flatMap{ it.map() }.filter{ it.height == 'E' }.each{ end = it; end.height = 'z' }

for(def current = [start]; current.noneMatch{ it == end } && current; ) {
   current = current.filter{ !it.visited }.flatMap{ sq ->
      sq.visited = true
      neighbours(sq).filter{ (int)it.height <= (int)sq.height + 1 }
                    .map{ it.dist = [sq.dist + 1,it.dist].min(); it }
   }
}
end.dist
```


## Part 2

For part 2 we had to find all paths from squares of height `a` to the end square and return the minimum path
length of all such paths.

This meant wrapping our loop in a function and initialising all squares each time to reset their `visited` and `dist`
fields.

```groovy
def grid = [], y = 0, end
stream(nextLine).each{ it.size().each{ x -> grid[x][y] = [x:x, y:y, height:it[x]] }; y++ }

def inGrid(x,y) { x >= 0 && x < grid.size() && y >= 0 && y < grid[0].size() }
def neighbours(sq) { [[sq.x-1,sq.y],[sq.x+1,sq.y],[sq.x,sq.y-1],[sq.x,sq.y+1]].filter(inGrid).map{ x,y -> grid[x][y] } }

grid.flatMap{ it.map() }.filter{ it.height == 'S' }.each{ it.height = 'a' }
grid.flatMap{ it.map() }.filter{ it.height == 'E' }.each{ end = it; it.height = 'z' }

def findPath(start, end) {
   grid.each{ it.each{ it.visited = false; it.dist = 0x7fffffff } }
   start.dist = 0
   for(def current = [start]; current.noneMatch{ it == end } && current; ) {
      current = current.filter{ !it.visited }.flatMap{ sq ->
         sq.visited = true
         neighbours(sq).filter{ (int)it.height <= (int)sq.height + 1 }
                       .map{ it.dist = [sq.dist + 1,it.dist].min(); it }
      }
   }
   return end.dist
}
grid.flatMap{ it.filter{ it.height == 'a' } }.map{ findPath(it, end) }.min()
```
