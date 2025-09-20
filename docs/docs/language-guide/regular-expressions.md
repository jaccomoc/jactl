---
title: Regular Expressions
---

## Regex Matching

Jactl provides two operators for doing regular expression (regex) find and regex subsitutions.
The `=~` operator is used for matching a string against a regex pattern.
It does the equivalent of Java's `Matcher.find()` to check if a substring matches the given pattern.
The regex pattern syntax is the same as that used by the Pattern class in Java so for detail information about how to
use regular expressions and what is supported see the Javadoc for the Pattern class.

Some examples:
```groovy
'abc' =~ 'b'                                         // true
'This should match' =~ '[A-Z][a-z]+ sho.l[a-f]'      // true
```

Regular expression patterns are usually expressed in pattern strings to cut down on the number of backslashes needed.
For example `/\d\d\s+[A-Z][a-z]*\s+\d{4}/` is easier to read and write than `'\d\d\s+[A-Z][a-z]*\s+\d{4}'`:
```groovy
'24 Mar 2014' =~ '\\d\\d\\s+[A-Z][a-z]*\\s+\\d{4}'   // pattern written as standard string
'24 Mar 2014' =~ /\d\d\s+[A-Z][a-z]*\s+\d{4}/        // same pattern written as pattern string
```

The `!~` operator tests that the string does not match the pattern:
```groovy
'24 Mar 2014' !~ /\d\d\s+[A-Z][a-z]*\s+\d{4}/       // false
```

## Modifiers

When using a regex string (a string delimited with `/`) you can append modifiers to the regex pattern to control the
behaviour of the pattern match.
For example by appending `i` you can make the pattern match case-insensitive:
```groovy
'This is a sentence.' =~ /this/          // false
'This is a sentence.' =~ /this/i         // true
```

More than on modifier can be appended and the order of the modifiers is unimportant.
The supported modifiers are:

| Modifier | Description                                                                                                                                                                      |
|:--------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|    i     | Pattern match will be case-insensitive.                                                                                                                                          |
|    m     | Multi-line mode: this makes `^` and `$` match beginning and endings of lines rather than beginning and ending of entire string.                                                  |
|    s     | Dot-all mode: makes `.` match line terminator (by default `.` won't match end-of-line characters).                                                                               |
|    g     | Global find: remembers where previous pattern occurred and starts next find from previous location. This can be used to find all occurrences of a pattern within a string.       |
|    n     | If a capture group is numeric then interpret as a number.                                                                                                                        |
|    r     | Regex match: this forces the pattern string to be interpreted as a regex match for implicit matching (see below). For substitutions this makes the substitution non-destructive. |

The `g` modifier for global find can be used to iterate over a string, finding all instances of a given pattern within
the string:
```groovy
def str = 'This Example Text Is Not Complex'
int i = 0
while (str =~ /Ex/ig) { i++ }   // count occurrences of 'ex' (case-insensitive)

i         // 3
```

Note that the 'g' modifier on a regex match is only supported within the condition of a while or for statement.
If you try to use it in any other context you will get an error:
```groovy
def x = 'abc'
x =~ /ab/g     // error: Cannot use 'g' modifier outside of condition for while/for loop
```

The other limitation is that only one occurrence of a pattern with a 'g' modifier can appear within
the condition expression.
Other regex matches can be present in the condition expression as long as they don't use the 'g' global modifier:
```groovy
def x = 'abc'; def y = 'xyz'; def i = 0
while (x =~ /[a-c]/g && y =~ /[x-z]/g) { i++ }  // error: Regex match with global modifier can only occur once within while/for condition
while (x =~ /[a-c]/g && y =~ /[x-z]/) { i++ }
i         // 3
```

These restrictions are due to what makes sense in terms of how capture variables work (see below) and due to the
way in which Jactl saves the state of the match to remember where to continue from the next time.

The `g` modifier is more useful when used with capture variables as described in the next section.

## Capture Variables

With regex patterns you can provide capture groups using `(` and `)`.
These groupings then create _capture_ _variables_ `$1` `$2` etc. for each of the capture groups that correspond
to the characters in the source string that match that part of the pattern.
There is also a `$0` variable that captures the portion of the source string that matches the entire pattern.
For example:
```groovy
def str = 'This Example Text Is Not Complex'
str =~ /p.*(.ex.).*(N[a-z].)/        // true
$1                                   // Text
$2                                   // Not
$0                                   // ple Text Is Not
```

Note that if there are nested groups, the capture variables numbering is based on the number of `(` so far encountered,
irrespective of the nesting.

You can use `\` to escape the `(` and `)` characters if you want to match against those characters.

Using capture variables with the `g` global modifier allows you to extract all parts of a string that match:
```shell
def data = 'AAPL=$151.03, MSFT=$255.29, GOOG=$94.02'
def stocks = [:]
while (data =~ /(\w*)=\$(\d+.\d+)/g) { stocks[$1] = $2 as Decimal }
stocks               // [AAPL:151.03, MSFT:255.29, GOOG:94.02]
```

The `n` modifier will make the capture variable a number if the string captured is numeric.
If the number has no decimal place then it will be converted to a `long` value (as long as the value is not too large).
If it has a decimal point then it will become a `Decimal` value.
For example:
```groovy
'rate=-1234' =~ /(\w+)=([\d-]+)/n          // true
$1                                         // rate
$2                                        // -1234
$2 instanceof long                        // true
'rate=56.789' =~ /(\w+)=([\d-.]+)/n       // true
$2                                        // 56.789
$2 instanceof Decimal                     // true

```

## Regex Substitution

The `=~` operator can also be used to perform regex substitutions.
The syntax is `s/pattern/replacement/modifiers`.
The `pattern` is the regex pattern to search for and the `replacement` is the string to replace it with.
The `modifiers` are the same as for regex matching.

For example:
```groovy
def str = 'This Example Text Is Not Complex'
str =~ s/Example/Simple/      // true
str                           // This Simple Text Is Not Complex
```
Note that the substitution is done in-place on the string variable.
If you want to do a non-destructive substitution then use the `r` modifier:
```groovy
def str = 'This Example Text Is Not Complex'
def newStr = str =~ s/Example/Simple/r    // true
newStr                                    // This Simple Text Is Not Complex
str                                       // This Example Text Is Not Complex
```

The `g` modifier can be used to replace all occurrences of the pattern:
```groovy
> def str = 'This Example Text Is Not Complex'
str =~ s/Ex/X/ig                        // true
str                                     // This Xample TXt Is Not ComplX
```

The replacement string can contain capture variables `$1`, `$2`, etc. to refer to the captured parts of the string
that matched the pattern:
```groovy
def str = 'This Example Text Is Not Complex'
str =~ s/(\w+)\s+(\w+)/$2 $1/          // true
str                                    // Example This Text Is Not Complex
```

The replacement string can also contain embedded expressions using `${}`:
```groovy
def str = 'This Example Text Is Not Complex'
str =~ s/(\w+)\s+(\w+)/${$2.toUpperCase()} ${$1.toLowerCase()}/
str               // EXAMPLE this Text Is Not Complex
```

## Implicit Matching

If the `=~` operator is not used then the regex match or substitution is performed on the implicit `it` variable.

There are a couple of situations where an implicit `it` variable is created, with the most common example being in
a closure without explicit parameters defined.
For example, one way to find elements of a list matching a given pattern might be:
```groovy
def list = ['abbc', 'xXyz', 'def']
list.filter{ elem -> elem =~ /bb|xx/i }      // ['abbc', 'xXyz'] 
```

We can rewrite this using the implicit `it` parameter that will be declared for the closure passed to `filter`:
```groovy
def list = ['abbc', 'xXyz', 'def']
list.filter{ /bb|xx/i }      // ['abbc', 'xXyz'] 
```

This is also useful when using the `-p` or `-n` command line options if running Jactl command line scripts
for processing input from files or stdin.
Each line is assigned to the implicit `it` variable so we can use a substitution or pattern match without having
to specify the variable name.
For example, to replace all occurrences of `abc` with `xyz` in a file:
```shell
$ jactl -p -e 's/abc/xyz/g'
```

The `r` modifier can be used to force a regex match since without any modifiers, and when using implicit match against `it`,
the compiler can't tell whether a regex string or an arithmetic division is wanted:
```shell
$ jactl -n -e '/abc/r and println it'
```
This will print all lines that contain `abc`.
