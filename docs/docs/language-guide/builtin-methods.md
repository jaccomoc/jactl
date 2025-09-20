---
title: Built-in Methods
---

Jactl provides a number of built-in methods for the standard types.

## String Methods

* `size()`
* `toUpperCase()`
* `toLowerCase()`
* `lines()`
* `substring(start)`
* `substring(start, end)`
* `split(separator)`
* `asNum(base)`
* `fromJson()`

## List Methods

* `size()`
* `remove(index)`
* `add(element)`
* `addAt(index, element)`
* `reverse()`
* `each(closure)`
* `reduce(initial, accumulator)`
* `min(closure)`
* `max(closure)`
* `avg()`
* `sum()`
* `skip(count)`
* `limit(count)`
* `unique()`
* `filter(predicate)`
* `allMatch(predicate)`
* `anyMatch(predicate)`
* `noneMatch(predicate)`
* `map(mapper)`
* `flatMap(mapper)`
* `mapWithIndex(mapper)`
* `mapi(mapper)`
* `collect(mapper)`
* `collectEntries(mapper)`
* `join(separator)`
* `sort(comparator)`
* `groupBy(closure)`
* `grouped(size)`
* `windowSliding(size)`
* `subList(start)`
* `subList(start, end)`
* `transpose()`
* `toJson()`

## Map Methods

Maps can be treated as Lists so all List methods can be used on a Map.
In addition, these Map specific methods also exist:

* `remove(key)`
* `get(key)`
* `put(key, value)`
* `putAll(values)`

## Number Methods

* `asChar()`
* `sqr()`
* `sqrt()`
* `abs()`
* `pow(power)`
* `toBase(base)`
