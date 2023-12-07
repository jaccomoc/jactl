---
layout:     post
title:      "Advent Of Code 2023 - Day 4"
date:       2023-12-07T15:12:22+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

A fairly straightforward day after giving the brain cells a workout yesterday.

# Day 4 - Scratchcards

See [Day 4](https://adventofcode.com/2023/day/4) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent04.jactl`) and run it like this (where advent04.txt
is your input file from the Advent of Code site for Day 4):
```shell
$ cat advent04.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent04.jactl 
```

## Part 1

For today's puzzle we are given a list of scratch cards with each card having a set of winning numbers, and, for each
card we have a set of numbers that we have to match against that cards winning numbers.
For the first winning number we get one point, and then each winning number after that doubles the number of points
we have.
In other words the number of points for each card is `2^(n-1)` where `n` is the number of winning cards we have.

The lines of input look like this:
```
Card 1:  1 10 23  6 |  2 23 45 66  1
Card 2: 22  3 47 65 | 10  3 65 22 44
```
The first set of numbers are the winning numbers and the second set is our set of numbers that we have to verify
against the winning numbers.

We just need to sum the points across all of our cards.

Here is the code:
```groovy
01 stream(nextLine).map{ it.split(/:/)[1].split(/ *\| */).map{ it.split(/ +/) } }
02                 .map{ c -> c[1].filter{ it in c[0] }.size() }
03                 .filter().map{ 1 << (it - 1) }.sum()
```

We first read each line and split on `:`.
The second part of the split (the part after the `:`) is what we are interested in.
We then split this part on `/ *| */` which splits on the '|' and also ignores the spaces around it.
Then each of the two parts is split into the numbers by splitting on `/ +/`.

Line 2 counts the number of numbers in the second set of numbers that appear in the first set (using `in` to
search the list of numbers).

Line 3 then takes the count and calculates `2^n` by shifting `1` left by one less than the count.

## Part 2

For part 2 the rules change so that the number of winning numbers actually give you additional cards to match
against.
If, on card `i` you have `n` winning numbers, then you add cards `i+1,i+2,...,i+n` to the list of cards and continue
the processing of the cards.
Since these cards are already in the list of cards they will be processed multiple times.

Here is the code:
```groovy
01 def C=stream(nextLine).map{it.split(/:/)[1].split(/ *\| */).map{it.split(/ +/)}}
02                       .mapWithIndex{ x,i -> [i:i,c:x[1].filter{it in x[0]}.size()] }
03 for(int i=0;i<C.size();i++){ C+=C.subList(C[i].i+1,C[i].i+C[i].c+1) if C[i].c }
04 C.size()
```
Since there were bonus points for _golfing_ the solution (making it as short as possible), I stripped out some
whitespace.

Here is a more legible version:
```groovy
01 def C = stream(nextLine).map{ it.split(/:/)[1].split(/ *\| */).map{ it.split(/ +/) } }
02                         .mapWithIndex{ x,i -> [idx:i, cnt:x[1].filter{ it in x[0] }.size()] }
03 for (int i = 0; i < C.size(); i++) {
04   C += C.subList(C[i].idx + 1, C[i].idx + C[i].cnt + 1) if C[i].cnt
05 }
06 C.size()
```

Line 1 reads in the card data the same way as in part 1.

Line 2 then creates a list of cards where each card is of the form `[idx:i, cnt:c]` where `i` is the card index
and `c` is the winning count for that card.

Line 3 iterates over the list of cards and for each card adds the winning cards to end of the list based on the
number of cards and where we are up to in the list.

Once there are no more cards to process we return the number of cards in the list.
