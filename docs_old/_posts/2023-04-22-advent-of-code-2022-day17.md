---
layout:     post
title:      "Advent Of Code 2022 - Day 17"
date:       2023-04-22 10:33:52 +1000
categories: blog
author:     "James Crawford"
---

# Day 17: Pyroclastic Flow

See [Day 17](https://adventofcode.com/2022/day/17) for a detailed description of the problem.

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

The challenge for today is to simulate falling rocks where the rock shapes just happen to coincide with the game of
Tetris.
As the rocks fall jets of air blow them left or right (like a player moving the shapes in Tetris).
This is represented as a sequence of commands being `<` for a jet that blows the shape one square to the left and
`>` for a jet that blows the shape to the right.
If the shape reaches the edge of the shaft then it stops moving in the sideways direction but still moves down.

When a rock starts falling it always starts at 3 positions higher than the highest shape (or the floor of the
cavern).
The usual Tetris rules apply in terms of the rocks stopping once they find one of the squares immediately below
occupied but, unlike Tetris, there are no commands for rotating the shapes.

The shaft is only 7 squares wide but has no limit in terms of height.

The order in which the shapes arrive is always the following (and they repeat indefinitely):
```
####

.#.
###
.#.

..#
..#
###

#
#
#
#

##
##
```

The aim of the puzzle is to work out how high the stack of shapes gets after 2022 shapes have fallen based on the
commands given as the puzzle input.
Note that the commands also repeat once we reach the end of the string of commands.

I have represented the shaft as a list of integers where the bits in the integer are used to represent whether
there is a rock at that position or not.
The edges of the shaft are represented as rocks to make the collision detection easier so each initially empty row
in the shaft has a value of `0b100000001` to represent a rock on the left and right edges of 7 empty squares.

To draw a shape at a given position, I xor the bits of the shape with the contents of the shaft.
Since I am using xor, it means that if I redraw a shape in the same position it will clear the shape.

To detect a collision I use `&` to and the bits of the shape with the contents of the shaft.
If anything comes back as non-zero, then it means there is already something in one of the squares where the shape
would be drawn.

So, the `move()` function first invokes `draw()` to "draw" the shape in its old position (which clears it due to
using xor), then checks if there would a collision in the new position, and if not uses `draw()` to draw the shape
into the new position and returns `true`.
If a collision is detected, it uses `draw()` to redraw the shape in its old position and returns `false`.

The main loop iterates over the number of rocks (2022) and for each rock keeps moving the shape by moving left or right
based on the current command (if possible) and then moving down a line until it is blocked by another
previous shape or the floor of the shaft.

```groovy
def ROCKS = 2022, WIDTH = 9, NEWLINE = 0b100000001, shaft = [0b111111111] + 3.map{ NEWLINE }
def commands = nextLine()
def bitMaps  = [[0b1111], [0b010, 0b111, 0b010], [0b111, 0b001, 0b001], [1, 1, 1, 1], [0b11, 0b11]]
def shapes   = bitMaps.map{ [bits:it, width:it.map{ row -> 16.filter{ row & (1 << it) }.max() + 1 }.max()] }
def move(shape, oldx, oldy, newx, newy) {
  draw(shape, oldx, oldy)
  collides(shape, newx, newy) and draw(shape, oldx, oldy) and return false
  draw(shape, newx, newy)
}
int line(y)          { shaft[y] ?: (shaft[y] = NEWLINE) }
int xshift(v, w, x)  { v << WIDTH - x - (w- 1) }
def draw(sh,x,y)     { sh.bits.size().each{ shaft[y+it] = line(y+it) ^ xshift(sh.bits[it], sh.width, x) }; true }
def collides(sh,x,y) { sh.bits.size().anyMatch{line(y+it) & xshift(sh.bits[it], sh.width, x) } }

int shapeIdx = 0, commandIdx = 0, max = 1
ROCKS.each{
  def line = max + 3, xpos = 3 + 1, shape = shapes[shapeIdx++ % shapes.size()]
  draw(shape, xpos, line)                                     // draw shape at top of shaft
  for (;;) {
    def newx = xpos + (commands[commandIdx++ % commands.size()] == '<' ? -1 : 1)
    move(shape, xpos, line, newx, line) and xpos = newx       // move left or right if possible
    // Move down if possible or if not put back in old position and move on to next shape
    move(shape, xpos, line, xpos, --line) or do { max = [max, line+1+shape.bits.size()].max(); break }
  }
}
println max - 1
```

## Part 2

For part 2, the only difference is that instead of having `2022` rocks, this time we have to find the height after
`1000000000000` rocks have fallen.
This is too many to just simulate, so we need to find another way to work out the answer.

I realised that since there are only 5 shapes that cycle around and the number of commands is also limited it is
likely that the pattern will be repeated at some point.
If we can find a repeating pattern at the same shape position and command position as a previous occurrence of that
pattern then we can just work out how many cycles there are and determine the answer that way.

The problem is how to work out when a pattern is a repeating pattern.
How many rows do we have to inspect before we can be sure that the pattern really will repeat as it did before?

If we look at the top few rows then, if there are any gaps across the shaft then it is possible that a vertical line
shape could pass down beyond those rows and alter the pattern going forward from how it occurred before since the
rows below the rows we are inspecting could have been different last time.
This means that when looking for a repeating pattern we have to at least make sure that the full width of the shaft
is covered by the top few rows.

Since I am using integers to represent the contents of each row in the shaft, and the shaft itself is only 7 squares
wide (ignoring the edges), we can easily encode 8 rows of the shaft into a `long`.
I decided, therefore, to look for repeating patterns when we have 8 rows at the top that fully cover the width of
the shaft.
It turns out that 8 rows was enough for my input although in general there is no guarantee that 8 would always work. 
If necessary it would be pretty simple to extend this by encoding the rows in a list or a string rather than using
a `long` if we had look for a larger number of rows in order to find a number where we had full coverage of the
width of the shaft.

Each time we find 8 rows where we have full coverage of the shaft width, we create a key based on the encoded rows,
the shape id, and the command offset and store the current rock number and current height.
If we find that there is already an entry in the map for our key it means that at the same shape id and command
we previously had the same rows at the top of the shaft and so we have discovered a cycle.
We work out how many cycles we have and the cycle maximum height difference (how much the maximum height has
changed since the previous cycle).
We jump to the last cycle by updating the rock counter and then when the loop finishes we just need to adjust the
maximum height by adding the cycle height different multiplied by the number of cycles.

```groovy
def ROWCOUNT = 8
def ROCKS = 1000000000000L, WIDTH = 9, NEWLINE = 0b100000001, shaft = [0b111111111] + 3.map{ NEWLINE }
def commands = nextLine()
def bitMaps  = [[0b1111], [0b010, 0b111, 0b010], [0b111, 0b001, 0b001], [1, 1, 1, 1], [0b11, 0b11]]
def shapes   = bitMaps.map{ [bits:it, width:it.map{ row -> 16.filter{ row & (1 << it) }.max() + 1 }.max()] }
def move(shape, oldx, oldy, newx, newy) {
  draw(shape, oldx, oldy)
  collides(shape, newx, newy) and draw(shape, oldx, oldy) and return false
  draw(shape, newx, newy)
}
int line(y)          { shaft[y] ?: (shaft[y] = NEWLINE) }
int xshift(v, w, x)  { v << WIDTH - x - (w- 1) }
def draw(sh,x,y)     { sh.bits.size().each{ shaft[y+it] = line(y+it) ^ xshift(sh.bits[it], sh.width, x) }; true }
def collides(sh,x,y) { sh.bits.size().anyMatch{line(y+it) & xshift(sh.bits[it], sh.width, x) } }
def coversWidth(row) { row >= ROWCOUNT && ROWCOUNT.reduce(0) {v, it -> shaft[row - it] | v } == 0x1ff }
def encodeRows(row)  { ROWCOUNT.reduce(0) {v, it -> v*8L + (shaft[row - it] & 0b011111110) } }

def states = [:], heightDiff = 0, foundCycle = false, shapeIdx = 0, commandIdx = 0, max = 1, startCmd
for (long rock = 0; rock < ROCKS; rock++) {
  def line = max + 3, xpos = 3 + 1, shape = shapes[shapeIdx++]
  shapeIdx %= shapes.size()
  draw(shape, xpos, line)
  for (startCmd = commandIdx % commands.size(); ; ) {
    def newx = xpos + (commands[commandIdx++ % commands.size()] == '<' ? -1 : 1)
    move(shape, xpos, line, newx, line) and xpos = newx
    !move(shape, xpos, line, xpos, --line) and max = [max, line+1+shape.bits.size()].max() and break
  }
  continue if foundCycle
  int row = shaft.size().map{ shaft.size()-1-it }.filter{ (shaft[it]?:0) & 0b011111110 }.limit(1)[0]
  continue unless coversWidth(row)
  def key = "${encodeRows(row)}:$shapeIdx:$startCmd", previous = states[key]
  states[key] = [rock: rock, height: max]
  if (foundCycle = (boolean)previous) {           // Found cycle if there was an entry in the map
    def cycle = rock-previous.rock, cycles = (long)(ROCKS-rock)/cycle - 1
    heightDiff = (max - previous.height) * cycles
    rock += cycles * cycle
  }
}
println max - 1 + heightDiff
```
