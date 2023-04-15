---
layout:     post
title:      "Advent Of Code 2022 - Day 7"
date:       2023-04-15 11:41:30 +1000
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

## Day 7 - No Space Left On Device

See [Day 7](https://adventofcode.com/2022/day/7) for a detailed description of the problem.

### Part 1

The puzzles are starting to get a little more complicated now.

For this one we have to parse some input consisting of `cd` and `ls` commands showing the contents of a file system
and then from that input find all directories whose total size (including children) is no more than 100000 and
calculate the total size of these directories.

For this solution we create a `File` class and a `Dir` class (which extends the `File` class) to represent the files
and directories.
The only two methods that the classes implement are `totalSize()` which for Files is the file size and for Dirs is
the combined size of all of its descendants, and `descendants()` which returns a list of all the descendants.
For Files the `descendants()` is just itself, while for Dirs the `descendants()` returns all the children, and the
children of the children, etc.

```groovy
class File {
  String name
  int    size
  def totalSize()   { size }
  def descendants() { this }
}

class Dir extends File {
  Dir parent
  Map children = [:]
  def totalSize()   { children.map{ it[1].totalSize() }.sum() }
  def descendants() { [this] + children.map{ it[1] }.flatMap{ it.descendants() } }
}

Dir root    = new Dir('/',0,null)
root.parent = root
Dir cwd     = root

stream(nextLine).each{
  /^\$ *cd +\/$/r   and do { cwd = root;             return }    // cd /
  /^\$ *cd +\.\.$/r and do { cwd = cwd.parent;       return }    // cd ..
  /^\$ *cd +(.*)$/r and do { cwd = cwd.children[$1]; return }    // cd some_dir
  /^\$ *ls/r        and return                                   // nothing to do for ls
  /^dir +(.*)$/r    and cwd.children[$1] = new Dir($1,-1,cwd)    // create directory
  /^(\d+) +(.*)$/n  and cwd.children[$2] = new File($2,$1)       // use /.../n to get numeric size
}

root.descendants()
    .filter{ it instanceof Dir }
    .map{ it.totalSize() }
    .filter{ it <= 100000 }
    .sum()
```

### Part 2

For part 2 we leave the parsing and creation of the data structures the same.
The difference for part 2 is now we need to find a directory, that when deleted (along with its descendants) frees
up enough space to leave a total free space of at least 30000000.
Out of all the directories that we could delete we need the smallest one.

We know that the total disk space is 70000000, so we calculate the current free space by subtracting `root.totalSize()`
and then use this to work out how much we need to delete to get to our desired value.

Then, it is a simple matter of filtering for directories whose total size is greater than or equal to this threshold
and return the size of the smallest such directory.

```groovy
class File {
  String name
  int    size
  def totalSize()   { size }
  def descendants() { this }
}

class Dir extends File {
  Dir parent
  Map children = [:]
  def totalSize()   { children.map{ it[1].totalSize() }.sum() }
  def descendants() { [this] + children.map{ it[1] }.flatMap{ it.descendants() } }
}

Dir root    = new Dir('/',0,null)
root.parent = root
Dir cwd     = root

stream(nextLine).each{
  /^\$ *cd +\/$/r   and do { cwd = root;             return }
  /^\$ *cd +\.\.$/r and do { cwd = cwd.parent;       return }
  /^\$ *cd +(.*)$/r and do { cwd = cwd.children[$1]; return }
  /^\$ *ls/r        and return
  /^dir +(.*)$/r    and cwd.children[$1] = new Dir($1,-1,cwd)
  /^(\d+) +(.*)$/n  and cwd.children[$2] = new File($2,$1)
}

def usedSize = root.totalSize()
def DISK_SIZE  = 70000000
def freeSpace = DISK_SIZE - root.totalSize()
def NEEDED_FREE_SPACE = 30000000
def needToDelete = NEEDED_FREE_SPACE - freeSpace

root.descendants()
    .filter{ it instanceof Dir }
    .map{ it.totalSize() }
    .filter{ it >= needToDelete }
    .min()
```
