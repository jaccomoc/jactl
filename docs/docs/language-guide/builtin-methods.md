---
title: Built-in Methods
---

Jactl provides a number of built-in methods for the standard types.

## List Methods
```groovy
List add(Object element)
List addAt(int index, Object element)
boolean allMatch(Function predicate=null)
boolean anyMatch(Function predicate=null)
Decimal avg()
List collect(Function mapper=null)
List collectEntries(Function mapper=null)
List each(Function action=null)
Iterator filter(Function predicate=null)
Iterator flatMap(Function mapper=null)
Iterator fmap(Function mapper=null)
Map groupBy(Function closure)
Iterator grouped(int size)
String join(String separator='')
Iterator limit(int count)
Iterator map(Function mapper=null)
Iterator mapWithIndex(Function mapper=null)
Iterator mapi(Function mapper=null)
Object max(Function closure=null)
Object min(Function closure=null)
boolean noneMatch(Function predicate=null)
Object reduce(Object initial, Function accumulator)
Object remove(int index)
List reverse()
int size()
Iterator skip(int count)
List sort(Function comparator=null)
List subList(int start)
List subList(int start, int end)
Object sum()
Iterator transpose()
Iterator unique()
Iterator windowSliding(int size)
```

## Map Methods

Maps can be treated as Lists so all List methods can also be applied to Maps.
In addition, these methods are specific to Maps:

```groovy
Object get(Object key)
Map put(Object key, Object value)
Map putAll(Map values)
Object remove(String key)
```

## String Methods

Strings can be treated as a list of characters so List methods can also be applied to String objects.
Strings also have these methods:

```groovy
long asNum(int base=10)
Iterator lines()
int size()
Iterator split(String regex=null, String modifiers='')
String substring(int start)
String substring(int start, int end)
String toLowerCase()
String toLowerCase(int count)
String toUpperCase()
String toUpperCase(int count)
```

## Number Methods

Number methods apply to all numeric types (`int`, `long`, `byte`, `double`, and `BigDecimal`).

```groovy
Number abs()
String asChar()
Number pow(Number power)
Number sqr()
Number sqrt()
Number toBase(int base)
```
