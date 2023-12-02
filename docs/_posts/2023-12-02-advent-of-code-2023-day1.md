---
layout: post
title:  "Advent Of Code 2023 - Day 1"
date:   2023-12-02T13:53:36+11:00
categories: blog
author: "James Crawford"
---

Another year has come around quickly and so another chance to have some fun solving the Advent Of Code puzzles for 2023.
Once again, I will be attempting to solve these puzzles using [Jactl](https://github.com/jaccomoc/jactl).
The version I am using is the latest version (1.3.1 as of time of writing).

# Day 1 - Trebuchet?!

See [Day 1](https://adventofcode.com/2023/day/1) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent01.jactl`) and run it like this (where advent01.txt
is your input file from the Advent of Code site for Day 1):
```shell
$ cat advent01.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent01.jactl 
```

## Part 1

For Part 1 we are given input where each line contains set of characters and digits.
The idea is to find the first and last digits and combine them into a two-digit number and sum these numbers across
all the lines of input.
If there is only one digit in the line then that digit should be used twice.

So input like:

    abc2def3x
    xyz4ynn

Would produce `23` for the first line and `44` for the second line.

A nice simple problem for day 1 as they usually are:
```groovy
stream(nextLine).map{ /^[^\d]*(\d).*?((\d)[^\d]*)?$/n; $1*10 + ($3 ?: $1) }.sum()
```
I just used a regex to extract both the digits and then multiply the first digit by 10 before adding the second
digit.
The only tricky part was getting the optional matching for the last digit to work.

The first part of the pattern `^[^\d]*(\d)` is pretty straightforward.
It says to match from the beginning of the line any number of non-digits followed by a digit which we then capture
in a capture group so `$1` will end up having the value of this digit.

The next part of the pattern is `.*?` which is a non-greedy match for any characters.
If we had a greedy match instead then it would always grab all characters until the end of line and our second
match would never find anything.
By using non-greedy matching here we allow the optional second match to take place if it can.

The next part of the pattern is `((\d)[^\d]*)?$`.
This says to optionally match a digit followed by any number of non-digits up until the end of the string (in this
case the end of the line).
There are two groups here and the inner group will capture the value of the last digit (if it exists) as `$3`.
If there is no match for a second digit then `$3` will be null so we use `$3 ?: $1` to get the value of `$3`
if it exists or return `$1` when `$3` is null.

## Part 2

For Part 2 we need to treat lines that have digit spelled out as letters as well as digits themselves.
So assuming we have a line like this:
    
    xoney3zfour2z

We then need to convert that to `12` rather than `32`.

I was determined to make this work with regular expressions, but I have to say that it was not a trivial job to
get the right regular expression.

One of the problems I encountered is that making it work for examples like the one above was not too hard but
if the line was instead something like this:

    x3zatwoneyz

we have to get `31` but my initial naive approach would return `32` because the `two` was matched even though
`one` was actually the match we wanted.

In the end this is the solution I came up with:
```groovy
def vals = [zero:0, one:1, two:2, three:3, four:4, five:5, six:6, seven:7, eight:8, nine:9] + (10.map{ ["$it",it] } as Map)
def n = vals.map{it[0]}.join('|')      // number pattern
stream(nextLine).map{ /^.*?($n)(.*($n)((?!($n)).)*$)?/r; 10*vals[$1] + vals[$3 ?: $1] }.sum()
```

The approach I took was to construct a map of words to numeric values, including the digits themselves as keys in
the map.
Then I create a pattern string `n` for matching the words or digits.
It ends up being:

    zero|one|two|three|four|five|six|seven|eight|nine|0|1|2|3|4|5|6|7|8|9

I then plug this string into the actual pattern in three different places in the pattern `/^.*?($n)(.*($n)((?!($n)).)*$)?/`
1. where we match the first "digit",
2. the match for the second digit, and
3. where we use a pattern to indicate anything that does not match this digit pattern.

By using a greedy match at the start of the second group (the optional matching group) we make sure that in situations
where we have `twone` or `oneight`, for example, we skip the first number and use the last characters as the ones to
match on.
