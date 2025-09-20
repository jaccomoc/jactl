---
title: Collection Methods
---

Jactl provides a rich set of methods for working with collections.
Collections can be `List`, `Map`, `String`, or a number.

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

## filter

The `filter` method creates a new collection containing only the elements of the original collection that satisfy a
predicate closure.

For example:
```groovy
[1,2,3,4,5].filter{ it % 2 == 0 }             // [2, 4]
'abcABC'.filter{ it == it.toUpperCase() }     // ['A', 'B', 'C']
10.filter{ it % 3 == 0 }                      // [0, 3, 6, 9]
[a:1, b:2, c:3].filter{ k,v -> v > 1 }        // [b:2, c:3]
```

## collect

The `collect` method works the same way as the `map` method except that it forces the creation of an intermediate
List result if used in the middle of chain of methods.
Normally, chains of methods acting on collections process one element at a time through the entire chain.
This makes for efficient processing since intermediate lists to not need to be created to hold the intermediate
results.
To force each method to process the entire collection before moving on to the next method in the chain, the
`collect` method can be used.
In particular, if the closure used for the mapping has side effects, then `collect` might be needed
to make sure that the side effects occur in the right order.

```groovy
[1,2,3,4].collect{ it * it + 1 }.filter{ it % 2 }    // [5, 17]
```

## reduce

The `reduce` method combines the elements of a collection into a single value.
It takes an initial value and a closure that combines the current value with the next element.

For example:
```groovy
> [1,2,3,4,5].reduce(0){ sum, i -> sum + i }
15
> 'abc'.reduce(''){ s, c -> s + c.toUpperCase() }
ABC
> 10.reduce(1){ prod, i -> prod * (i+1) }
3628800
> [a:1, b:2, c:3].reduce(0){ sum, k,v -> sum + v }
6
```

## sum

The `sum` method calculates the sum of the elements of a collection.

For example:
```groovy
> [1,2,3,4,5].sum()
15
> 'abc'.sum()
abc
> 10.sum()
45
> [a:1, b:2, c:3].sum()
6
```

## join

The `join` method concatenates the elements of a collection into a string.
It takes an optional separator string.

For example:
```groovy
> [1,2,3].join()
123
> [1,2,3].join(', ')
1, 2, 3
> 'abc'.join('-')
a-b-c
> 3.join(':')
0:1:2
> [a:1, b:2].join('; ')
a=1; b=2
```

## sort

The `sort` method sorts the elements of a collection.
It takes an optional comparator closure.

For example:
```groovy
> [3,1,2].sort()
[1, 2, 3]
> 'cba'.sort()
['a', 'b', 'c']
> [a:3, b:1, c:2].sort()
[b:1, c:2, a:3]
> [3,1,2].sort{ a,b -> b <=> a }
[3, 2, 1]
```

## reverse

The `reverse` method reverses the order of the elements of a collection.

For example:
```groovy
> [1,2,3].reverse()
[3, 2, 1]
> 'abc'.reverse()
cba
> [a:1, b:2].reverse()
[b:2, a:1]
```

## unique

The `unique` method removes duplicate elements from a collection.

For example:
```groovy
> [1,2,2,3,1].unique()
[1, 2, 3]
> 'abacaba'.unique()
['a', 'b', 'c']
```

## flatten

The `flatten` method flattens a nested collection.

For example:
```groovy
> [[1,2],[3,4]].flatten()
[1, 2, 3, 4]
> [a:[1,2], b:[3,4]].flatten()
[1, 2, 3, 4]
```

## transpose

The `transpose` method transposes a collection of collections.

For example:
```groovy
> [[1,2,3],[4,5,6]].transpose()
[[1, 4], [2, 5], [3, 6]]
```

## withIndex

The `withIndex` method returns a new collection where each element is a list containing the original element and its
index.

For example:
```groovy
> [1,2,3].withIndex()
[[1, 0], [2, 1], [3, 2]]
> 'abc'.withIndex()
[['a', 0], ['b', 1], ['c', 2]]
```
