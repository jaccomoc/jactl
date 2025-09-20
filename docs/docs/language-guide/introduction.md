---
title: Language Guide Introduction
---

# Introduction

Jactl is a language that looks very much like a simplified Java with a few additional
features stolen from Groovy and Perl.
If you are familiar with Java it will be an easy language to pick up.

Here is an example Jactl that defines a recursive function for calculating factorials
and then invokes it:
```groovy
long fact(long x) {
  if (x <= 1) {
    return 1
  }
  return x * fact(x - 1)
}

long n = 7
println "Factorial of $n is: ${fact(n)}"
```

Functions will, by default, return the value of the last expression encountered in the function so `return` 
is often not needed and since Jactl is optionally typed we can rewrite the code above more
like this:
```groovy
def fact(x) { x <= 1 ? 1 : x * fact(x - 1) }

def n = 7
println "Factorial of $n is: ${fact(n)}"
```
