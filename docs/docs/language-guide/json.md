---
title: JSON Support
---

Jactl has built-in support for JSON.

## toJson

The `toJson` method converts an object to a JSON string.

For example:
```groovy
[a:1, b:[c:2]].toJson()            // {"a":1,"b":{"c":2}}
```

## fromJson

The `fromJson` method converts a JSON string to an object.

For example:
```groovy
'{"a":1,"b":{"c":2}}'.fromJson()   // [a:1, b:[c:2]]
```

## User Classes

The JSON support also extends to user defined classes.
Each class has a `toJson()` and a `fromJson()` generated for it.

### toJson

The `toJson()` function is an instance method, so to use it just invoke it on the object needed to be converted
to JSON:
```groovy
class X {
  int i = 123
  String s = 'abc'
}

def x = new X()
x.toJson()          // {"i":123,"s":"abc"} 
```

### fromJson

The `fromJson()` method on a user defined class is a static method which is passed a string and returns a new
instance of that class based on the contents of the string:
```groovy
class X {
  int i = 123
  String s = 'abc'
}

X.fromJson('{"i":456,"s":"xyz"}')   // equivalent to new X(456, 'xyz')
```
