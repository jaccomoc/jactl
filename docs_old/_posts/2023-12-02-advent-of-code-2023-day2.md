---
layout:     post
title:      "Advent Of Code 2023 - Day 2"
date:       2023-12-02T17:48:26+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

Day 2 - Easier than day 1. Mostly a parsing exercise.

# Day 2 - Cube Conundrum

See [Day 2](https://adventofcode.com/2023/day/2) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent02.jactl`) and run it like this (where advent02.txt
is your input file from the Advent of Code site for Day 2):
```shell
$ cat advent02.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent02.jactl 
```

## Part 1

In Part 1 we are given a list of "games" with each game having a multiple lists and each list being a list of
colours and a count.
The idea is that these lists represent a random selection of coloured cubes from a bag.
Multiple lists for each game show multiple random picks from the same bag.
Each game gets a new bag with different colour cubes.

For example:

    Game 1: 3 blue, 2 red, 4 green; 1 blue, 3 red, 3 green; 2 blue, 3 red, 4 green
    Game 2: 2 blue, 6 red, 4 green; 3 blue, 4 red, 5 green; 1 blue, 7 red, 8 green
    ...

If we are told that the bag actually has 12 red cubes, 13 green cubes, and 14 blue cubes, we need to determine
which games could actually have been played with that bag.
That is we need to weed out the games where the selections had counts too high for this given bag.

For the games that could have occurred we sum their ids and that is the solution for the puzzle.

```groovy
def bag = [red:12, green:13, blue:14]
stream(nextLine).map{ 
  /Game (\d+): (.*)$/n
  [$1, $2.split(/;/)
         .flatMap{ it.split(/,/)
         .map{ /(\d+) (.*)/n; [$2,$1] }}.allMatch{ colour,count -> count <= bag[colour] }]
}.filter{ it[1] }.map{ it[0] }.sum()
```

The solution was pretty straightforward.
We take each line, extract the game id, and then split the rest of the line on `;` to get the list of picks.
Then we split on `,` to find the individual colours and the counts.
We then filter the games for games where the colour counts for all picks were less than or equal to the counts
in the bag we have been given.

Finally we sum the game ids.

## Part 2

For Part 2 we take the cube counts for each game and we need to work out what the smallest count per colour in our
bag could be for that game to have been valid.
Each game, therefore, gets its own minimum bag with a count per colour.
Then we multiply these counts together for each game and sum these products for the final solution:

```groovy
stream(nextLine).map{ it.split(/: /)[1].split(/;/)
                        .flatMap{ it.split(/,/).map{ /(\d+) (.*)/n; [$2,$1] } }
                        .sort() as Map }
                .map{ it.reduce(1){ prod,entry -> prod * entry[1] } }.sum()
```

The only trick here is to generate a simple list of [colour, count] for each game and then sort these so that
we get a list sorted by colour and then count so that the highest counts come later in the list.
Then we convert this list into a Map which means that the last value for each colour will be the value in the Map.
Since we have sorted the list this means we end up with a Map of colours with the maximum count values.

Then we used `reduce()` to generate the product of these counts and sum them together.
