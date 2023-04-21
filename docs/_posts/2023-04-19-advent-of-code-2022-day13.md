---
layout:     post
title:      "Advent Of Code 2022 - Day 13"
date:       2023-04-19 09:44:49 +1000
categories: blog
author:     "James Crawford"
---

# Day 13 - Distress Signal

See [Day 13](https://adventofcode.com/2022/day/13) for a detailed description of the problem.

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

For this challenge we are given as input a series of pairs of lists separated by blank lines like this:
```groovy
[1,1,3,1,1]
[1,1,5,1,1]

[[1],[2,3,4]]
[[1],4]

[9]
[[8,7,6]]
```

Luckily, the format is identical to Jactl syntax for list literals which means I could use the built-in `eval`
statement to parse the input into lists.

The requirement is to compare each pair of lists to find which pairs are already in the correct sort order.
The comparison is based on standard list comparison but with one difference which is that a bare element when
compared with a list is first wrapped in a list before doing the comparison.
For example `4` is the same as `[4]` for the purpose of the comparison.
This means we can't just use the `<=>` operator to compare the lists, unfortunately.

So based on the requirements, I wrote a simple `compare()` function that returns `-1`, `0`, or `1` just like
the `<=>` operator does.

Then it was a simple matter of reading in the input, discarding blank lines, grouping into pairs and finding which
pairs are in order.
The result is the sum of the indices of the pairs that are in the correct order.

```groovy
def compare(lhs,rhs) {
  return 1           if rhs == null
  return lhs <=> rhs if lhs !instanceof List && rhs !instanceof List
  rhs = [rhs]        if rhs !instanceof List
  lhs = [lhs]        if lhs !instanceof List
  def cmp = lhs.mapWithIndex{ it,i -> compare(it,rhs[i]) }.filter().limit(1)[0]    // Note 1
  return cmp ? cmp : (rhs.size() > lhs.size() ? -1 : 0)                            // Note 2
}

stream(nextLine).filter().map{ eval(it) }                                          // Note 3
                .grouped(2)                                                        // Note 4
                .mapWithIndex{ it,i -> compare(it[0],it[1]) <= 0 ? i+1 : 0 }       // Note 5
                .sum()
```

Notes:
1. We compare each element in the two lists until the first one which returns non-zero (`filter().limit(1)`) up
until the end of the `lhs` list.
2. If the comparison returned non-zero then that is the result, otherwise each element compared equally and the
result is: `-1` if there are still more elements in `rhs` or, if the sizes are equal, `0`.
3. Using `filter()` will filter for lines that are "true" which for strings means non-empty. Then `eval` is used to
evaluate the line as though it is Jactl. It will return the result which will be the list (or the number if it is
just a single element on the line by itself).
4. Uses `grouped(2)` to group each pair of consecutive elements into a list of size 2.
5. Since we are only interested in pairs which are in the right order, if `compare()` returns `-1` or `0` then we
map to the index plus 1 (requirements are for counting to start at 1), otherwise we map to 0 so that when we sum
we will be summing only indexes where entries were in correct order.

## Part 2

For part 2 we now need to include two "divider" entries `[[2]]` and `[[6]]` and sort all entries including these
two dividers.
Then the result is the product of the indices of where the two dividers ended up in the sorted list.

Since we already have our `compare()` function that returns `-1`, `0`, or `1` when comparing two lists this made
part 2 pretty easy.

We add the two dividers to the data and then just sort using our comparison function and use `mapWithIndex()` to
map each value to `1` if it is not a divider, or the index plus 1 if it is.
The result is then the product of all of these values.

```groovy
def compare(lhs,rhs) {
  return 1           if rhs == null
  return lhs <=> rhs if lhs !instanceof List && rhs !instanceof List
  rhs = [rhs]        if rhs !instanceof List
  lhs = [lhs]        if lhs !instanceof List
  def cmp = lhs.mapWithIndex{ it,i -> compare(it,rhs[i]) }.filter().limit(1)[0]
  return cmp ? cmp : (rhs.size() > lhs.size() ? -1 : 0)
}

def dividers = [ [[2]], [[6]] ]
def data     = stream(nextLine).filter().map { eval(it) } + dividers          // Note 1
data.sort{ a,b -> compare(a,b) }
    .mapWithIndex{ it,i -> it in dividers ? i+1 : 1 }
    .reduce(1){ m,it -> m*it }                                                // Note 2
```

Notes:
1. Read in the lines and add the two divider entries.
2. Use `reduce()` to multiply all the mapped values together.
