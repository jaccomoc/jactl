---
layout:     post
title:      "Advent Of Code 2022 - Day 22"
date:       2023-05-08 14:58:35 +1000
categories: blog
author:     "James Crawford"
---

# Day 22: Monkey Map

See [Day 22](https://adventofcode.com/2022/day/22) for a detailed description of the problem.

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

In this challenge we are given a map and a series of moves with each move being a number representing how many
steps to take, or `R` meaning turn right, or `L` meaning turn left.
The rules state that when moving a number of steps, if we hit an obstacle we have to stop in the square immediately
before the obstacle and if a step would take us off the edge of a map, we wrap around to the other side of the map
and continue from there.

The final result is calculated as 1000 times the row number plus 4 times the column number plus 0 if we are facing
right, 1 if we are facing down, 2 if we are facing left, and 3 if we are facing up.

To solve this puzzle, I kept track of the current position and direction we are facing in a simple Map like
`[x:xpos, y:ypos, d:direction]` with direction being 0, 1, 2, or 3 for right, down, left, and up.

Then we process each move and adjust the current position and direction.

```groovy
def grid = stream{ it = nextLine(); it ? it : null }
def width = grid.map{ it.size() }.max(), height = grid.size()
def moves = []
for (def line = nextLine(); line =~ /(\d+|[RL])/ng; ) { moves <<= $1 }

def moveCount(pos, num) {
  def lastGoodPos = pos
  while (num > 0) {
    pos = newPos(pos)
    continue if pos.x >= grid[pos.y].size()
    grid[pos.y][pos.x] == '#' and return lastGoodPos
    grid[pos.y][pos.x] == '.' and lastGoodPos = pos and num--
  }
  return pos
}
def newPos(p) { [x:(p.x+[1,0,-1,0][p.d])%width, y:(p.y+[0,1,0,-1][p.d])%height, d:p.d] }

def pos = [x:0, y:0, d:0]   // d: 0 - right, 1 - down, 2 - left, 3 - up
moves.each{ move ->
  pos   = moveCount(pos, move)           if move !in ['R', 'L']
  pos.d = (pos.d + [R:1,L:-1][move]) % 4 if move  in ['R', 'L']
}
1000 * (pos.y+1) + 4 * (pos.x+1) + pos.d
```

## Part 2

For part 2 the map has to now be interpreted as an unrolled cube with 50x50 size faces.
Now, when we move off the edge of the map or when we move into empty space we have to find the corresponding
place on our imaginary cube and continue from there.
Moving from one face to another may also involve having to rotate ourselves in order to keep moving in the right
direction.

I have to say that this challenge was quite a mind bender in terms of having to imagine how an unrolled cube then
rolls up and how the moves from one face to another would work.

Since I already knew what the input looked like I could have hard coded how the faces were stitched together,
but that felt a bit like cheating, so I made a general solution that will work with any shape for the unrolled
cube.

The code for all this was much longer than the solution in part 1 and ended up being the longest solution by far 
for any of the days.

```groovy
def SIZE = 50, RIGHT = 0, DOWN = 1, LEFT = 2, UP = 3
def grid = stream{ it = nextLine(); it ? it : null }
def faceData = 7.map{ 7.map{ [] } }  // 7 x 7 grid of cube faces, allows room on all sides
grid.grouped(SIZE).mapWithIndex{ it,y -> it.each{ line ->
  (line.size() / SIZE).each{ x -> def row = line.substring(x * SIZE, (x + 1) * SIZE)
    faceData[y+1][x+1] <<= row if row !~ /^ *$/ }
}}

def faceCoords = 7.flatMap{ y -> 7.filter{ x -> faceData[y][x] }.map{ x -> [x,y] } }
def faces = 7.map{ x -> 7.map{ y -> faceData[y][x] ? [data:faceData[y][x],coords:[x,y], side:[]] : null }}
def cube = faceCoords.map{ x,y -> faces[x][y] }
faceCoords.each{ x,y ->
  faces[x-1][y] and faces[x][y].side[LEFT]  = [face:faces[x-1][y], rot:0]
  faces[x+1][y] and faces[x][y].side[RIGHT] = [face:faces[x+1][y], rot:0]
  faces[x][y-1] and faces[x][y].side[UP]    = [face:faces[x][y-1], rot:0]
  faces[x][y+1] and faces[x][y].side[DOWN]  = [face:faces[x][y+1], rot:0]
}

def rotate(dir, count) { (dir + count) % 4 }

def stillMissing
while (stillMissing = cube.filter{ face -> face.side.filter{it}.size() < 4 }) {
  stillMissing.each{ face ->
    [LEFT,RIGHT,UP,DOWN].filter{ !face.side[it] }.each{ side ->
      // Look for neighbour on one of our attached sides that we can then rotate and attach to us
      def attach = [[side:rotate(side,1),rot:1], [side:rotate(side,-1),rot:-1]]
        .filter{ face.side[it.side] }
        .map{ [side:it.side, rot1:it.rot, rot2:face.side[it.side].rot] }
        .map{ [face:face.side[it.side].face.side[rotate(side,it.rot2)], rot:it.rot1 + it.rot2] }
        .filter{ it.face }
        .map{ [face:it.face.face, rot:(it.rot + it.face.rot) % 4]}
        .limit(1)
      attach and face.side[side] = [face:attach[0].face, rot:attach[0].rot]
    }
  }
}

def moveCount(pos, num) {
  def charAt(pos) { pos.face.data[pos.y][pos.x] }
  def lastGoodPos = pos
  for (; num > 0; num--) {
    pos      = newPos(pos)
    def char = charAt(pos)
    char == '#' and return lastGoodPos
    char == '.' and lastGoodPos = pos
  }
  return pos
}

def newPos(p) {
  def face = p.face, x = p.x + [1,0,-1,0][p.d], y = p.y + [0,1,0,-1][p.d], d = p.d
  if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
    def rot = face.side[p.d].rot
    face    = p.face.side[p.d].face
    d = rotate(p.d, rot)
    x %= SIZE
    y %= SIZE
    def coords
    rot == 0 and coords = [x,y]
    rot == 1 and coords = [[SIZE-1-y,0], [SIZE-1,x], [SIZE-1-y,SIZE-1], [0,x]][p.d]
    rot == 2 and coords = [SIZE-1-x, SIZE-1-y]
    rot == 3 and coords = [[y,SIZE-1], [0,SIZE-1-x], [y,0], [SIZE-1,SIZE-1-x]][p.d]
    x = coords[0]
    y = coords[1]
  }
  [face:face, x:x, y:y, d:d]
}

// Find first face and top left most point on that face
def facey = faceCoords.map{ x,y -> y }.min()
def facex = faceCoords.filter{ x,y -> y == facey }.map{ x,y -> x}.min()
def pos = [face:faces[facex][facey], x:0, y:0, d:RIGHT]

def moves = []
for (def line = nextLine(); line =~ /(\d+|[RL])/ng; ) { moves <<= $1 }
moves.each{ move ->
  pos   = moveCount(pos, move)           if move !in ['R', 'L']
  pos.d = (pos.d + [R:1,L:-1][move]) % 4 if move  in ['R', 'L']
}
1000 * ((pos.face.coords[1] - 1) * SIZE + pos.y + 1) + 4 * ((pos.face.coords[0] - 1) * SIZE + pos.x + 1) + pos.d
```
