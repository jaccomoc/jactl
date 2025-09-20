---
layout:     post
title:      "Advent Of Code 2023 - Day 7"
date:       2023-12-08T10:45:15+11:00
categories: blog
image:      /assets/logo-picture.jpg
author:     "James Crawford"
---

Today did not require too much cleverness but there were a couple of traps to watch out for.

# Day 7 - Camel Cards

See [Day 7](https://adventofcode.com/2023/day/7) for a detailed description of the problem.

To run the Jactl shown here save the code into a file (e.g. `advent07.jactl`) and run it like this (where advent07.txt
is your input file from the Advent of Code site for Day 7):
```shell
$ cat advent07.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent07.jactl 
```

## Part 1

For day 7 we are given a list consisting of hands of cards along with a bid (an integer amount).
Each hand consists of five cards and each card can be one of A, K, Q, J, T, 9, 8, 7, 6, 5, 4, 3, 2 with this
order also being the rank order of the cards.
Cards have no suit in this game and there can be five cards of the same type in a hand.

The puzzle requires us to sort the hands into a ranking order based on rules that are similar to poker:

- Five-of-a-kind beats four-of-a-kind
- Foud-of-a-kind beats a full house
- A full house beats three-of-a-kind
- etc

One slight twist that if two hands are classified the same way (e.g. both three-of-a-kind) then the cards
are compared (in order) using their ranking to determine which hand has a higher ranking.

Once the cards have been ranked, from the lowest ranking to highest, we  need to calculate the sum of the each cards
bid multiplied by their rank (where ran starts with 1).

The code looks like this:
```groovy
01  def cards = stream(nextLine).map{ it.split(/ /) }
02  def rnk = '23456789TJQKA'.mapWithIndex() as Map
03  def stronger(a,b) {
04    def counts = { it.reduce([:]){ m,it -> m[it]++; m } }
05    def (ca, cb) = [counts(a), counts(b)]
06    def hcntCmp  = ca.max{it[1]}[1] <=> cb.max{it[1]}[1]
07    ca.size() != cb.size() ? cb.size() <=> ca.size() :
08                   hcntCmp ? hcntCmp : a.map{rnk[it]} <=> b.map{rnk[it]}
09  }
10  cards.sort{a,b -> stronger(a[0],b[0]) }.mapWithIndex{ c,i -> (i+1) * (c[1] as long) }.sum()
```

At line 1 we read the cards and split them into two parts: the hand (just a 5 character string) and the bid amount.

Line 2 creates a `rnk` map which returns the rank for an individual card.

Line 3 declares a function `stronger()` which is a comparator function for two hands `a` and `b`.
If `a` is stronger, then it returns 1, if `a` is weaker, it returns -1, and if they are the same it returns 0.

At line 4 we create a function that creates a map from a hand of card to count which we then use in line 5 to create
the two map of counts `ca` and `cb`.

Line 6 assigns the comparison between the highest count for any card in card `a` and the highest count for any
card in hand `b` to `hcntCmp`.
So if hand `a` is `AJQA3` then its highest count is `2` since we have a single pair and if hand `b` were `AT9TT` then
its highest count would be `3` since we have a three-of-a-kind and `hcntCmp` would be `-1` because `2` is less than `3`.
This will help us to compare the two hands.

Line 7 and 8 return the comparator result for the two hands.
If the number of distinct cards differs then we return `cb.size() <=> ca.size()` which means that that hand with fewer
distinct cards will win.Note that we use `cb.size() <=> ca.size()` and *not* `ca.size() <=> cb.size()` since here
the lowest number wins.

Otherwise if `hcntCmp` is non-zero it means that the highest card counts differ and so we want the hand with the
highest card count to win and we return `hcntCmp`.

If the highest card counts are the same and the number of distinct cards are the same then we need to compare the
hands based on the ordered ranking of the cards, so we map the cards to their ranking and return the comparator
for the two lists of card rankings as our result.

Line 10 sorts the cards based on this `stronger(a,b)` comparator function and then for each card multiplies the
card bid by their index in the sorted ranking and sums the results.

## Part 2

In part 2 the rules change so that J is now a joker and can match any other card in the hand.
This means that we want use the joker to maximise the rank of the hand by making it match the most frequent other
non-joker card in the hand.
However, when comparing hands based on the card rankings (when they are otherwise of equal strength), the ranking
of a joker is now lower than any other card.

One additional twist to watch out for is that a hand can have 5 jokers.

Here is the code:
```groovy
01  def cards = stream(nextLine).map{ it.split(/ /) }
02  def rnk = 'J23456789TQKA'.mapWithIndex() as Map
03  def ranked(a,b) { rnk[a] <=> rnk[b] }
04  def stronger(a,b) {
05    def counts = { it.reduce([:]){ m,it -> m[it]++; m } }
06    def (ca, cb) = [counts(a =~ s/J//gr), counts(b =~ s/J//gr)]
07    def (aBest, bBest) = [ca,cb].map{ cs -> (['J'] + cs.filter{ it[1]==cs.max{it[1]}[1] }.sort(ranked))[-1][0] }
08    (ca, cb) = [counts(a =~ s/J/$aBest/gr), counts(b =~ s/J/$bBest/gr)]
09    def hcntCmp = ca.max{it[1]}[1] <=> cb.max{it[1]}[1]
10    ca.size() != cb.size() ? cb.size() <=> ca.size() :
11                   hcntCmp ? hcntCmp : a.map{rnk[it]} <=> b.map{rnk[it]}
12  }
13
14  cards.sort{ a,b -> stronger(a[0],b[0]) }.mapWithIndex{ c,i -> (i+1) * (c[1] as long) }.sum()
```

Line 2 now ranks the cards using the new ranking for part 2.

Line 3 declares a function that returns a comparator for comparing the rankings of two cards that we can use in a sort
later on.

Line 4 is where we have the `stronger()` function for determining which hand of `a` or `b` is stronger.
We still have a function for returning a map of card counts at line 5.
We use this function to calculate the counts for `a` and `b` in the maps `ca` and `cb` (after stripping out all
the jokers) at line 6.

Note that `a =~ s/J//gr` is a regex expression that substitutes all occurrences of `'J'` with `''`.
The `r` after the expressions means that it returns the value after substitutions and doesn't modify the value `a`
as it would normally.

Line 7 is where we find the best card to use in case we have any jokers.
We use the card counts in `ca` and `cb` to find the card with the highest count and return it
as `aBest` for hand `a` and `bBest` for hand `b`.
We generate a list of cards that have the highest count.
There may be multiple if we have two pairs, for example, or 5 different cards with no multiples.
We then sort the list based on ranking and return the last card in the list as it has the highest ranking.
We add 'J' to front of the list to cater for the case when the list is empty (because all cards are jokers).

At line 8 we recalculate the counts after substituting jokers for our best card.

The rest of the code works exactly as in part 1.
