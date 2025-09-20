---
title:      "Advent Of Code 2022 - Day 20"
date:       2023-05-07 15:18:32 +1000
categories: blog
authors: [james]
---

# Day 20: Grove Positioning System

See [Day 20](https://adventofcode.com/2022/day/20) for a detailed description of the problem.

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

In this challenge you are given a list of numbers.
The numbers have to be moved within the list by the number of positions equal to the value of the number being moved.
The list should be treated like a circular buffer, so if a number moves off the end of the list it wraps around to
the beginning again.
The numbers can be negative in which case they move backwards that many positions.

After all the moves have been completed we need to find the index of the number 0 in the list and then find the
values for the numbers at the position 1000, 2000, and 3000 after the 0 and sum the values.
Again, the list should be treated as a circular buffer from the point of view of the indexing.

To move each number we work out the index for where the number will end up and then progressively swap each number
between the start index and the destination index.
We keep two lists, one for the original numbers, and one for the shuffled numbers.
Each element in the lists is a pair of the number and its current index in the shuffled list.
This allows us to iterate through the original list and find the position in the shuffled list where the number
currently sits.

The only other trick is to note that the count is adjusted to be the positive remainder modulo `size - 1` since
the number of moves needs to exclude the number itself.
This also means that negative moves are converted to positive moves which makes the code slightly simpler.

```groovy
List elems = stream(nextLine).mapWithIndex{ line,idx -> [line as int,idx] }
List shuffled = [] + elems
int  size = elems.size()

elems.each{
  int count = it[0] % (size - 1)
  for (int idx = it[1], endIdx = (it[1] + count) % size, newIdx; idx != endIdx; idx = newIdx) {
    newIdx = (idx + 1) % size
    def tmp             = shuffled[idx]
    shuffled[idx]       = shuffled[newIdx]
    shuffled[idx][1]    = idx
    shuffled[newIdx]    = tmp
    shuffled[newIdx][1] = newIdx
  }
}

def zero = shuffled.filter{ it[0] == 0 }.limit(1)[0]
3.map{ shuffled[(zero[1] + 1000 * (it+1)) % size][0] }.sum()
```

## Part 2

For part 2 we need to multiply every number in the list by `811589153` which means everything is now done in terms
of `long` values rather than `int` values, and we need to do the shuffling of the numbers 10 times in a row before
working out the sum of the 1000th, 2000th, and 3000th numbers occurring after `0`.

I decided to use `long` arrays this time instead of lists to speed up the execution time.

```groovy
long key = 811589153
long[][] elems    = stream(nextLine).mapWithIndex{ line,idx -> [(line as long) * key,idx] }
long[][] shuffled = (elems as List) as long[][]     // make a copy
int size = elems.size()

10.each{
  elems.each{
    int count = it[0] % (size - 1)
    for (long idx = it[1], endIdx = (it[1] + count % (size - 1)) % size, newIdx; idx != endIdx; idx = newIdx) {
      newIdx = (idx + 1) % size
      def tmp             = shuffled[idx]
      shuffled[idx]       = shuffled[newIdx]
      shuffled[idx][1]    = idx
      shuffled[newIdx]    = tmp
      shuffled[newIdx][1] = newIdx
    }
  }
}

def zero = elems.filter{ it[0] == 0 }.limit(1)[0]
3.map{ shuffled[(zero[1] + 1000 * (it+1)) % size][0] }.sum()
```
