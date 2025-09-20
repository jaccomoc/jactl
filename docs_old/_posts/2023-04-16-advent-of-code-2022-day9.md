---
layout:     post
title:      "Advent Of Code 2022 - Day 9"
date:       2023-04-16 10:22:35 +1000
categories: blog
author:     "James Crawford"
---

# Day 9 - Rope Bridge

See [Day 9](https://adventofcode.com/2022/day/9) for a detailed description of the problem.

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

For part 1 we need to simulate the movement of a 2 segment rope on a two-dimensional grid (think of it as a snake
with a head and a tail).
We are given a series of moves for the head which are a direction (R - right, L - left, U - up, D - down) and the
number of squares to move.
The rule is that the tail has to follow the head when the head is more than one square away from the tail in
either direction.

At the end we have to work out how many squares the tail has visited.

Both the head and tail start in the same position (`[0,0]`).

```groovy
def head = [0,0], tail = [0,0], visited = ["0:0":true]

def add(p,q) { [ p[0]+q[0], p[1]+q[1] ] }
def move(m) {
  def shouldMove() { (tail[0] - head[0]).abs() > 1 || (tail[1] - head[1]).abs() > 1 }
  head = add(head, [R:[1,0], L:[-1,0], U:[0,1], D:[0,-1]][m])
  tail = add(tail, [head[0] <=> tail[0], head[1] <=> tail[1]]) if shouldMove()
  visited["${tail[0]}:${tail[1]}"] = true
}

stream(nextLine).each{ /^([RLUD]) (.*)$/n and $2.each{ move($1) } }
visited.size()
```

We model the position of the head and tail using tuples of `[x,y]` coordinates.

For each line of input we invoke `move()` the number of times specified and pass the direction in.
We use a map to map from a direction (R, L, U, D) to a delta to apply to the current head position by using:
```groovy
[R:[1,0], L:[-1,0], U:[0,1], D:[0,-1]][m]
```
where `m` is the direction to move.
This returns a delta to apply using `add()`.

We then work out how to move the tail if it should be moved by using the `<=>` comparator operator which evaluates
to `-1` if the first operand is less than the second, `0` if they are equal, and `1` if the first operand is greater
than the second.
This means that `head[0] <=> tail[0]` evaluates to the value to add to the x coordinate of the tail to move it towards
the head.

Finally, we record the fact that the tail has visited the square using a map keyed on `x:y` that we can count at the end.

## Part 2

For part 2 the rope now has 10 segments (knots) rather than 2 and the tail is therefore the 10th knot in an array
of knots and the head is the first entry.
Each knot follows the preceding knot using the same rule we had for how the tail followed the head in part 1.

```groovy
def N = 10
def knots = N.map{[0,0]}, visited = ["0:0":true]

def add(p,q) { [ p[0]+q[0], p[1]+q[1] ] }
def move(m) {
  def shouldMove(k1,k2) { (k1[0] - k2[0]).abs() > 1 || (k1[1] - k2[1]).abs() > 1 }
  knots[0] = add(knots[0], [R:[1,0], L:[-1,0], U:[0,1], D:[0,-1]][m])
  (N-1).each{
    def k1 = knots[it+1], k2 = knots[it]
    knots[it+1] = add(k1, [k2[0] <=> k1[0], k2[1] <=> k1[1]]) if shouldMove(k1,k2)
  }
  visited["${knots[N-1][0]}:${knots[N-1][1]}"] = true
}

stream(nextLine).each{ /^([RLUD]) (.*)$/n and $2.each{ move($1) } }
visited.size()
```

If I had known about part 2 before doing part 1, I would, naturally have made the solution in part 1 more general
so that the only difference between part 1 and 2 would have been the number of knots.
