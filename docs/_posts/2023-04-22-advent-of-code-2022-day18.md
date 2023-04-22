---
layout:     post
title:      "Advent Of Code 2022 - Day 18"
date:       2023-04-22 13:53:38 +1000
categories: blog
author:     "James Crawford"
---

# Day 18: Boiling Boulders

See [Day 18](https://adventofcode.com/2022/day/18) for a detailed description of the problem.

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

In this challenge you are given a set of points representing 1x1x1 cubes which are supposed to be droplets of lava
with the goal being to count the number of faces across all the cubes that are not immediately connected to another cube.

This part was pretty easy.
We read in the comma separated points and split them and store them as 3 element lists inside another list called
`cubes`.
The only trick is to add 1 to each index so that if there is a point on the boundary we don't go negative when looking
for neighbours.

We iterate over the cubes and set the value in our three-dimensional grid for each point to 1.

Then, for every element of `cubes` we count the number of our immediate neighbouring grid locations that are empty
and sum across all the cubes.

Note that we use `grid[x]?[y]?[z]` to return null if any of the indices returns a null list.
This is because we have a sparse array where only the points from the input are set.

```groovy
def grid = [], cubes = stream(nextLine).map{ it.split(/,/).map{ (it as int)+1 } }
cubes.each{ x,y,z -> grid[x][y][z] = 1 }
def adjacent(x,y,z) { [[-1,0,0],[1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].map{ dx,dy,dz -> [x+dx,y+dy,z+dz] } }
cubes.map{ adjacent(it).filter{ x,y,z -> !grid[x]?[y]?[z] }.size() }.sum()
```

## Part 2

Part 2 is a bit trickier.
For part 2 we need to treat everything outside the droplets of lava as steam and count the total external surface area
of the droplets ignoring any air pockets within the large shape formed by the droplets.

The idea is that everything outside the droplets is steam and that the steam expands to fill all available space by
moving to its immediate neighbours if there is nothing there.
Therefore, if there are any air pockets within the droplet structure, the steam will not be able to get there and they
remain unoccupied.

We read in the droplet cube locations as before but now we need to fill all locations outside the droplets with steam.
We start at `[0,0,0]` which we know is outside the droplets since we added 1 to every coordinate when we read them
in.
Then we continually iterate, finding neighbours that are empty, filling them with steam, and finding their neighbours,
etc. until there are no more empty neighbours left.

Once done we count all the faces of the droplets that are immediately adjacent to steam and this will give us our
total external surface area.

```groovy
def DROPLET = 1, STEAM = 2, AIR = 3
def grid = []
def cubes = stream(nextLine).filter{it}.map{ it.split(/,/).map{ (it as int) + 1 } }
cubes.each{ x, y, z ->  grid[x][y][z] = DROPLET }
def MAXX = cubes.map{it[0]}.max() + 1, MAXY = cubes.map{it[1]}.max() + 1, MAXZ = cubes.map{it[2]}.max() + 1
def gridAt(x,y,z) { grid[x]?[y]?[z] }
def faces = [[-1,0,0],[1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]]
def adjacent(x,y,z) { faces.map{ dx,dy,dz -> [x+dx,y+dy,z+dz] }.filter{ it.allMatch{ it >= 0 } }
                           .filter{ x,y,z -> x <= MAXX && y <= MAXY && z <= MAXZ } }
for (def steamCells = [[0,0,0]]; steamCells; ) {
  steamCells.each{ x,y,z -> grid[x][y][z] = STEAM }
  steamCells = steamCells.flatMap{ p -> adjacent(p).filter{ !gridAt(it) } }.sort().unique()
}
cubes.map{ adjacent(it).filter{ gridAt(it) == STEAM }.size() }.sum()
```
