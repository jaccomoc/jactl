---
title: Comments
---

Comments in Jactl are the same as for Java and Groovy and are denoted using either line comment form, where `//` is
used to begin a comment that extends to the end of the line, or with a pair of `/*` and `*/` that delimit a comment that
can be either embeeded within a single line or span multiple lines:
```groovy
int x = 3         // define a new variable x

int y = /* comment */ x * 4

int /* this
       is a multi-line comment
     */ z = x * y // another comment at the end of the line
```
