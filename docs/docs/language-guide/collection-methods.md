---
title: Collection Methods
description: "Built-in methods for lists, maps, strings, and numbers in Jactl: map, filter, reduce, groupBy, and more."
---

Jactl provides a rich set of methods for working with collections.
A collection can be a `List`, `Map`, `String`, or a number (a number `n` behaves as the sequence
`0`, `1`, ..., `n-1`).

Two things to keep in mind throughout this page:

- **Maps are iterated as `[key, value]` pairs.** When a collection method is applied to a `Map`, each
  element is a two-element `[key, value]` list. Methods that return a collection therefore return a
  `List` of such pairs, not a `Map`. Use [`collectEntries`](#collectentries) to turn a list of pairs
  back into a `Map`. A closure that takes a single element can also declare two parameters to
  destructure the pair, so `{ k, v -> ... }` works for `each`/`map`/`filter`/etc.
- **Most methods are lazy.** Methods such as `map`, `filter`, and `flatMap` return an `Iterator` and
  do no work until the result is consumed (by `sum`, `join`, `each`, iterating in a `for` loop,
  assigning into a `List`, and so on). Use [`collect`](#collect) to force a stage to be fully
  evaluated when a mapping closure has side effects that must happen in order.

## each

The `each` method iterates over the elements of a collection and invokes a closure for each element.

For example:
```groovy
[1,2,3].each{ println it }
// Output:
// 1
// 2
// 3

'abc'.each{ println it }
// Output:
// a
// b
// c

3.each{ println it }
// Output:
// 0
// 1
// 2

[a:1, b:2].each{ k,v -> println "$k -> $v" }
// Output:
// a -> 1
// b -> 2
```

## map

The `map` method transforms a collection by applying a closure to each element.
It returns a new collection containing the results of the closure invocations.

For example:
```groovy
[1,2,3].map{ it * it }                // [1, 4, 9]
'abc'.map{ it.toUpperCase() }         // ['A', 'B', 'C']
3.map{ it + 1 }                       // [1, 2, 3]
[a:1, b:2].map{ k,v -> "$k -> $v" }   // ['a -> 1', 'b -> 2']
```

If no closure is supplied, `map` uses the identity mapping, which is a convenient way to turn any
collection (including a `String` or number) into a `List`:
```groovy
'abc'.map()                           // ['a', 'b', 'c']
```

## mapWithIndex

The `mapWithIndex` method works like `map` but the closure receives two arguments: the element and
its index (starting at `0`).

For example:
```groovy
['a','b','c'].mapWithIndex{ x,i -> "$i:$x" }   // ['0:a', '1:b', '2:c']
```

`mapi` is a shorter alias for `mapWithIndex`.

## flatMap

The `flatMap` method maps each element and, if the result is itself a collection, flattens it so that
the members of the result are added to the stream of values.

For example:
```groovy
[[1,2],[3,4]].flatMap()                   // [1, 2, 3, 4]
[a:[1,2], b:[3,4]].flatMap()              // ['a', [1, 2], 'b', [3, 4]]
[1,2,3].flatMap{ [it, it] }               // [1, 1, 2, 2, 3, 3]
['a','abc','x'].flatMap{ it.map() }       // ['a', 'a', 'b', 'c', 'x']
```

With no closure, `flatMap` flattens one level.
`fmap` is a shorter alias for `flatMap`.

## filter

The `filter` method creates a new collection containing only the elements of the original collection
that satisfy a predicate closure.

For example:
```groovy
[1,2,3,4,5].filter{ it % 2 == 0 }             // [2, 4]
'abcABC'.filter{ it == it.toUpperCase() }     // ['A', 'B', 'C']
10.filter{ it % 3 == 0 }                      // [0, 3, 6, 9]
[a:1, b:2, c:3].filter{ k,v -> v > 1 }        // [['b', 2], ['c', 3]]
```

Note that filtering a `Map` returns a `List` of `[key, value]` pairs (see the note at the top of this
page).
Pass the result to [`collectEntries`](#collectentries) to get a `Map` back.

## collect

The `collect` method works the same way as the `map` method except that it forces the creation of an
intermediate `List` result if used in the middle of a chain of methods.
Normally, chains of methods acting on collections process one element at a time through the entire
chain.
This makes for efficient processing since intermediate lists do not need to be created to hold the
intermediate results.
To force each method to process the entire collection before moving on to the next method in the
chain, the `collect` method can be used.
In particular, if the closure used for the mapping has side effects, then `collect` might be needed
to make sure that the side effects occur in the right order.

```groovy
[1,2,3,4].collect{ it * it + 1 }.filter{ it % 2 }    // [5, 17]
```

## collectEntries

The `collectEntries` method builds a `Map` from a collection.
Each element (or the closure's result, if a closure is supplied) must be a two-element
`[key, value]` list, and these become the entries of the resulting `Map`.

For example:
```groovy
[['a',1],['b',2]].collectEntries()                    // [a:1, b:2]
['a','b','c'].collectEntries{ [it, it.toUpperCase()] } // [a:'A', b:'B', c:'C']
```

This is the inverse of iterating a `Map` (which produces `[key, value]` pairs), so it is the usual
way to turn the pair-list result of `filter`/`map`/`sort` on a `Map` back into a `Map`.

## reduce

The `reduce` method combines the elements of a collection into a single value.
It takes an initial value and a closure that combines the accumulated value with the next element.

For example:
```groovy
[1,2,3,4,5].reduce(0){ sum, i -> sum + i }        // 15
'abc'.reduce(''){ s, c -> s + c.toUpperCase() }   // ABC
10.reduce(1){ prod, i -> prod * (i+1) }           // 3628800
[a:1, b:2, c:3].reduce(0){ sum, e -> sum + e[1] } // 6
```

For a `Map`, the second closure parameter is each `[key, value]` pair.

## sum

The `sum` method calculates the sum of the numeric elements of a collection.

For example:
```groovy
[1,2,3,4,5].sum()      // 15
10.sum()               // 45
```

The elements must be numbers. Summing a `Map` directly is an error because its elements are
`[key, value]` pairs; sum the values instead, e.g. `m.map{ k,v -> v }.sum()`.

## avg

The `avg` method returns the average (as a `Decimal`) of the numeric elements of a collection.

For example:
```groovy
[1,2,3,4].avg()        // 2.5
```

## min

The `min` method returns the smallest element of a collection.
An optional closure can be supplied to derive the value used for the comparison.

For example:
```groovy
[3,1,2].min()                       // 1
['abc','de','f'].min{ it.size() }   // f
```

## max

The `max` method returns the largest element of a collection.
An optional closure can be supplied to derive the value used for the comparison.

For example:
```groovy
[3,1,2].max()                       // 3
['abc','de','f'].max{ it.size() }   // abc
```

## size

The `size` method returns the number of elements in a collection.

For example:
```groovy
[1,2,3].size()         // 3
'abcd'.size()          // 4
[a:1, b:2].size()      // 2
```

## allMatch

The `allMatch` method returns `true` if every element of the collection satisfies the given predicate
closure.

For example:
```groovy
[2,4,6].allMatch{ it % 2 == 0 }     // true
```

## anyMatch

The `anyMatch` method returns `true` if at least one element of the collection satisfies the given
predicate closure.

For example:
```groovy
[1,2,3].anyMatch{ it > 2 }          // true
```

## noneMatch

The `noneMatch` method returns `true` if no element of the collection satisfies the given predicate
closure.

For example:
```groovy
[1,2,3].noneMatch{ it > 5 }         // true
```

## sort

The `sort` method sorts the elements of a collection.
It takes an optional comparator closure that returns a negative number, `0`, or a positive number
(the [`<=>`](expressions-and-operators#comparator-operator) operator is handy here).

For example:
```groovy
[3,1,2].sort()                   // [1, 2, 3]
'cba'.sort()                     // ['a', 'b', 'c']
[3,1,2].sort{ a,b -> b <=> a }   // [3, 2, 1]
```

Sorting a `Map` sorts its `[key, value]` pairs and returns them as a `List`:
```groovy
[a:3, b:1, c:2].sort{ a,b -> a[1] <=> b[1] }   // [['b', 1], ['c', 2], ['a', 3]]
```

## reverse

The `reverse` method reverses the order of the elements of a collection.
Note that reversing a `String` returns a `List` of its characters in reverse order, not a `String`.

For example:
```groovy
[1,2,3].reverse()       // [3, 2, 1]
'abc'.reverse()         // ['c', 'b', 'a']
```

## unique

The `unique` method removes **consecutive** duplicate elements from a collection (like the Unix
`uniq` command).
Non-adjacent duplicates are not removed, so to remove all duplicates, sort the
collection first.

For example:
```groovy
[1,1,2,3,3].unique()         // [1, 2, 3]
[1,2,2,3,1].unique()         // [1, 2, 3, 1]  — the trailing 1 is not adjacent to the first
'aabbcc'.unique()            // ['a', 'b', 'c']
```

## groupBy

The `groupBy` method groups the elements of a collection into a `Map` keyed by the value returned by
the closure.
Each map value is the `List` of elements that produced that key.

For example:
```groovy
[1,2,3,4].groupBy{ it % 2 == 0 ? 'even' : 'odd' }   // [odd:[1, 3], even:[2, 4]]
```

## grouped

The `grouped` method splits a collection into consecutive, non-overlapping sub-lists of the given
size.

For example:
```groovy
[1,2,3,4,5,6].grouped(2)     // [[1, 2], [3, 4], [5, 6]]
```

## windowed/windowSliding

The `windowed` (aliased to `windowSliding` for backwards compatibility) method returns a sliding window of the given size over the collection.
Successive windows overlap, advancing by one element at a time.

For example:
```groovy
[1,2,3,4].windowed(2)   // [[1, 2], [2, 3], [3, 4]]
```

## transpose

The `transpose` method transposes a collection of collections by converting rows to columns and
columns to rows.

For example:
```groovy
[[1,2,3],[4,5,6]].transpose()        // [[1, 4], [2, 5], [3, 6]]
```

## limit

The `limit` method returns the first `count` elements of a collection (or all of them if the
collection is smaller).

For example:
```groovy
[1,2,3,4,5].limit(3)         // [1, 2, 3]
```

If the argument to `limit()` is negative, then the result is all elements up until the last `n`:

For example:
```groovy
[1,2,3,4,5].limit(-3)        // [1, 2]
```

## skip

The `skip` method returns the collection with the first `count` elements removed.

For example:
```groovy
[1,2,3,4,5].skip(2)          // [3, 4, 5]
```

If the `count` argument is negative then the result is the last `count` elements of the collection:
```groovy
[1,2,3,4,5].skip(-2)         // [4, 5]
```

## subList

The `subList` method returns the portion of a list from a start index (inclusive) to an optional end
index (exclusive).
With a single argument it returns everything from the start index to the end.

For example:
```groovy
[1,2,3,4,5].subList(1, 3)    // [2, 3]
[1,2,3,4,5].subList(2)       // [3, 4, 5]
```

If the arguments are negative then the index is relative to the end of the collection:
```groovy
[1,2,3,4,5].subList(-3,-1)   // [3, 4]
[1,2,3,4,5].subList(-2)      // [4, 5]
```

## join

The `join` method concatenates the string form of the elements of a collection into a single string.
It takes an optional separator string (the default is no separator).

For example:
```groovy
[1,2,3].join()           // 123
[1,2,3].join(', ')       // 1, 2, 3
'abc'.join('-')          // a-b-c
3.join(':')              // 0:1:2
```
