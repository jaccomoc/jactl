---
title:      "Jactl vs Groovy, JEXL, MVEL, and SpEL: A Performance Comparison"
date:       2026-07-14T17:00:00+10:00
authors:    [james]
tags:       [jvm, benchmarks, performance]
description: "A comprehensive JMH benchmark comparison of Jactl, Groovy, Apache Commons JEXL, MVEL, and Spring Expression Language (SpEL) across arithmetic, string handling, collection processing, regex, recursion, closures, and compilation speed."
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This post presents the results of some representative benchmarks using the JMH benchmarking library
to run comparative tests across the following five different Java-based scripting/expression languages:

| Language                                                               | Version | Notes                                                   |
|------------------------------------------------------------------------|---------|---------------------------------------------------------|
| [Jactl](https://jactl.io)                                              | 2.9.0   | Compiles to JVM bytecode                                |
| [Groovy](https://groovy-lang.org)                          | 5.0.6  | Compiles to JVM bytecode                                |
| [Apache Commons JEXL](https://commons.apache.org/proper/commons-jexl/) | 3.6.2   | AST interpreter                                         |
| [MVEL](https://github.com/mvel/mvel)                                   | 2.5.2   | AST interpreter with limited bytecode generation |
| [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) | 5.3.39  | Hybrid expression tree/bytecode interpreter             |

> Benchmarks were run on a Java 25.0.2 JVM

The benchmarks focus mostly on short, expression based tests, in an attempt to mirror the types of scenarios
where scripting languages are often used (for example in rules processing systems).
There is one longer benchmark with a script of around 30 lines, as well as benchmarks comparing the compilation
speeds.

<!-- truncate -->

All benchmarks go through a warmup phase before the results are recorded.
The source code for the benchmarks can be found here:
[`ScriptingLanguageBenchmarks.java`](https://github.com/jaccomoc/jactl/blob/main/src/jmh/java/io/jactl/benchmarks/ScriptingLanguageBenchmarks.java).

The main focus of the benchmarks is to measure the overhead of the different languages for scenarios
where scripts are compiled once and executed many times in a multithreaded application.

:::note
The absolute numbers for the benchmarks are not important as it depends on the power of
the machine on which they are run.
It is the relative performance that matters.
:::

## The Languages

### Jactl

[Jactl](https://jactl.io) is a JVM scripting language designed to be embedded in Java-based
applications to provide a secure, performant, customisation mechanism.
It compiles to bytecode and therefore takes advantage of JVM hotspot compilation optimisations.

As well as the ability to execute the types of tests performed in this benchmark suite,
Jactl also has two key features that set it apart from the other scripting languages
discussed here. Firstly, Jactl has a continuation based mechanism that allows it to suspend
execution of a script for a long-running operation and resume it later from where it was suspended.
Secondly, this same mechanism also allows it to serialise the state of a script and have it
resumed even after a process restart.
Jactl allows the application to configure whether these features should be enabled or not
and in these benchmarks, these features have not been enabled.

Jactl has a small footprint with no external dependencies and is deployed as a JAR of around 1.1MB 
in size that includes a shaded copy of the ASM library that it uses for bytecode generation.

### Groovy

[Apache Groovy](https://groovy-lang.org) is probably the most widely used scripting language
in the Java domain and is the most similar language to Jactl out of the languages compared
here.
It is a fully-featured language with extensive libraries and its own ecosystem.
Like Jactl, Groovy compiles to bytecode but suffers some overhead due to its metaobject protocol.
This metaobject protocol provides great flexibility and power but comes with some runtime cost.

One thing to note about Groovy with respect to these benchmarks is that the `Binding` object
that provides the values for binding variables passed to/from a script is bound to the script
itself which means that script invocation is not thread-safe.
The scripts are compiled to reusable `Class` objects, but for each invocation, `InvokerHelper.createScript()`
is used to create a new thread-safe instance of the class.

Although previous comparisons with Groovy have been Java 8 based and have therefore used Groovy 4,
this comparison was done with Java 25 which allows us to use the latest Groovy 5 release.

For more details on the differences between Jactl and Groovy see
the [Groovy vs Jactl](2026-03-16-groovy-vs-jactl.md) post.

### JEXL

[Apache Commons JEXL](https://commons.apache.org/proper/commons-jexl/) (Java Expression
Language) started life as a templating expression language but has grown into a more
complete scripting language that provides a configurable sandbox model to configure
accessibility to Java objects and methods.

Unlike Jactl and Groovy, JEXL does not compile to bytecode.
It parses source code into an AST that it interprets at runtime.
This makes compilation fast as it doesn't need to generate bytecode or incur the overhead
of the `defineClass()` mechanism for adding a class to the running JVM.

### MVEL

[MVEL](https://github.com/mvel/mvel) (MVFLEX Expression Language) is another embeddable
scripting language for Java applications.
It gained popularity when it was adopted by the Drools rules engine as its default expression
language.

MVEL compiles source code to an AST which it then interprets at runtime, however 
it does provide optimisations for some constructs such as field access
that are compiled directly to bytecode at first execution.

MVEL has expanded beyond being purely an expression language to a scripting language that is
capable of running scripts with variables, control structures, and user-defined functions.
It does not support closures or higher-order functions and so it was excluded from the
benchmark that tests currying and closure evaluation.

### SpEL

[Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html)
is the expression language built into the Spring Framework.
It is used throughout the Spring ecosystem, for example in Spring Security's `@PreAuthorize`
annotations, Spring Data query derivation, and Spring Batch conditional expressions.

SpEL is an expression language rather than a scripting language.
Its purpose is to be able to evaluate the value of a single expression and so
it does not support multiple statements, local variables, or user-defined functions.
It has been excluded from the benchmarks that require any of those features.

SpEL can run as an interpreted language, or it can compile to bytecode depending on how it is
configured.
It has three compiler modes:

| Mode      | Description                                       |
|-----------|---------------------------------------------------|
| OFF       | Interpreter mode (the default).                   |
| IMMEDIATE | Compiles to bytecode after the second evaluation. |
| MIXED     | Compiles to bytecode after the 100th evaluation.  |

For these benchmarks, the `IMMEDIATE` mode has been used to evaluate its fastest execution speed.

---

## Execution Benchmarks

Claude AI was used to create the benchmark suite, including choosing the scripts/expressions to use in
the benchmarks.

### 1. Arithmetic

This tests a simple expression that does simple arithmetic on five different values passed in
as binding variables.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
(a + b) * c - d / 2 + e %% 3
// bindings: a=10, b=5, c=3, d=8, e=7
// NOTE: In Jactl, '%%' is equivalent to Java's '%' (in Jactl '%' is used for the modulo operator)
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
(a + b) * c - d / 2 + e % 3
// bindings: a=10, b=5, c=3, d=8, e=7
```

</TabItem>

<TabItem value="jexl" label="JEXL">

```groovy
(a + b) * c - d / 2 + e % 3
// MapContext: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
(a + b) * c - d / 2 + e % 3
// variable map: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
(#a + #b) * #c - #d / 2 + #e % 3
// variables: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-arithmetic.html" width="660" height="420" style={{border:'none'}} />

SpEL leads at **122M ops/s**, roughly **11.3x** faster than MVEL (the slowest at 10.7M ops/s).
Jactl (110M) is close behind SpEL, and then there is a large gap to Groovy (21.5M)
followed by JEXL and MVEL, which are essentially tied at the back of the field (10.9M and 10.7M respectively).

This type of simple expression is what SpEL excels at, and it is able to generate fast bytecode
that beats the other languages.
Jactl does well with its bytecode but is not quite at the level of SpEL.

Despite Groovy compiling to bytecode, it is significantly slower than SpEL and Jactl.
This is because Groovy binding variables are untyped which means that arithmetic operations
are dispatched via the Groovy metaobject protocol to find the appropriate method to invoke.
In addition, because binding variables are untyped, Groovy uses BigDecimal values when it performs
the division which is an even bigger impact on the performance.

Note that Jactl is able to use the type of the binding variables to generate efficient bytecode
but even if the binding variables are passed as untyped values at compile time, the performance only
drops slightly to 96M ops/s (from 110M).

---

### 2. Complex Arithmetic

This benchmark tests a more complex expression where each binding variable is referenced
multiple times: `a*(a+b) - b*(b-c) + d*(d-e) + c*(a+e)`.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
a*(a+b) - b*(b-c) + d*(d-e) + c*(a+e)
// bindings: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
a*(a+b) - b*(b-c) + d*(d-e) + c*(a+e)
// bindings: a=10, b=5, c=3, d=8, e=7
```

</TabItem>

<TabItem value="jexl" label="JEXL">

```groovy
a*(a+b) - b*(b-c) + d*(d-e) + c*(a+e)
// MapContext: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
a*(a+b) - b*(b-c) + d*(d-e) + c*(a+e)
// variable map: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
#a*(#a+#b) - #b*(#b-#c) + #d*(#d-#e) + #c*(#a+#e)
// variables: a=10, b=5, c=3, d=8, e=7
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-complex-arithmetic.html" width="660" height="420" style={{border:'none'}} />

Jactl leads at **103M ops/s**, **15.1x** faster than the slowest, JEXL (6.85M).
There is a large gap to SpEL which is second at 50.0M and then another gap to
Groovy (16.8M) and MVEL (7.46M).

The key difference here is that more work is done per execution and there are repeated variable references.
Jactl creates local, typed, variable aliases for binding variables, so every subsequent
reference to `a`, `b`, etc. is a cheap local access avoiding a Map lookup.

SpEL resolves each reference node in its expression tree individually, so the per-variable overhead is compounded
the more times a variable is referenced.

---

### 3. Script with Variables

This benchmark tests a short two-line script with an intermediate variable.
Since SpEL does not support local variables or multi-line scripts, it was not included in this benchmark.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
var result = x * x + y * y
result + z
// bindings: x=3, y=4, z=10
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
int result = x * x + y * y
result + z
// bindings: x=3, y=4, z=10
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
var result = x * x + y * y;
result + z
// positional args: x=3, y=4, z=10
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
result = x * x + y * y;
result + z
// variable map: x=3, y=4, z=10
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-script-with-vars.html" width="660" height="360" style={{border:'none'}} />

Jactl achieves **124M ops/s**, more than **12.5x** faster than the slowest, JEXL (9.88M).
The result, unsurprisingly, is similar to the arithmetic benchmark: a large gap down to Groovy (42.2M),
then MVEL (10.0M) and JEXL at the back of the field.

---

### 4. Conditional Branching

This benchmark is a cascading set of `if-else` statements to test how efficient a simple categorisation
rule based on ranges is.
The score is always `50` so the benchmark falls through to the last `else` clause.
Since SpEL does not support statements, it uses a chain of ternary `? :` operators.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
if      (score >= 90) { 'A' }
else if (score >= 80) { 'B' }
else if (score >= 70) { 'C' }
else if (score >= 60) { 'D' }
else                  { 'F' }
// bindings: score=50
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
if      (score >= 90) { 'A' }
else if (score >= 80) { 'B' }
else if (score >= 70) { 'C' }
else if (score >= 60) { 'D' }
else                  { 'F' }
// bindings: score=50
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
if (score >= 90)       { 'A'; }
else if (score >= 80)  { 'B'; }
else if (score >= 70)  { 'C'; }
else if (score >= 60)  { 'D'; }
else                   { 'F'; }
// positional arg: score=50
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
if      (score >= 90) { 'A'; }
else if (score >= 80) { 'B'; }
else if (score >= 70) { 'C'; }
else if (score >= 60) { 'D'; }
else                  { 'F'; }
// variable map: score=50
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
#score >= 90 ? 'A' : #score >= 80 ? 'B' : #score >= 70 ? 'C' : #score >= 60 ? 'D' : 'F'
// variable: score=50
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-conditional.html" width="660" height="420" style={{border:'none'}} />

Jactl narrowly comes out on top at **155M ops/s**, **13.7x** faster than the slowest (JEXL).
SpEL is a close second, only about 5% behind at **147M ops/s**, followed by a large gap down to
Groovy (50.0M), MVEL (14.8M) and JEXL (11.3M).

Unsurprisingly, those languages which compile to bytecode are significantly
faster than the interpreted languages.

---

### 5. Collection Loop

This test benchmarks a loop that iterates over a 1000 element list, summing the contents of the list.
SpEL is not included as it does not support multiple statements or `for` loops.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
var sum = 0
for (item in list) { sum += item }
sum
// bindings: list = List of integers 1..1000
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
int sum = 0
for (item in list) { sum += item }
sum
// bindings: list = List of integers 1..1000
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
var sum = 0;
for (item : list) { sum += item; }
sum
// positional arg: list = List of integers 1..1000
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
sum = 0;
foreach (item : list) { sum += item; }
sum
// variable map: list = List of integers 1..1000
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-collection-loop.html" width="660" height="380" style={{border:'none'}} />

Jactl, at **30.0M ops/s** is **64.8x** faster than the slowest (JEXL), with Groovy (16.2M)
next at around half the speed.
MVEL (650K) follows a long way further behind and then JEXL (462K) is at the end.

---

### 6. String Operations

This benchmark tests the efficiency of string manipulation operations.
It does a string concatenation followed by `toUpperCase()` and a call to get the length.
Since SpEL does not support local variables, it has to concatenate the strings twice
which puts it at a disadvantage for this test.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
var s = first + ' ' + last
s.toUpperCase() + ' (' + s.size() + ')'
// bindings: first="John", last="Doe"
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
String s = first + ' ' + last
s.toUpperCase() + ' (' + s.length() + ')'
// bindings: first="John", last="Doe"
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
var s = first + ' ' + last;
s.toUpperCase() + ' (' + s.length() + ')'
// positional args: first="John", last="Doe"
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
s = first + ' ' + last;
s.toUpperCase() + ' (' + s.length() + ')'
// variable map: first="John", last="Doe"
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
(#first + ' ' + #last).toUpperCase() + ' (' + (#first + ' ' + #last).length() + ')'
// variables: first="John", last="Doe"
// (SpEL is single-expression so concatenation is repeated)
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-string-operations.html" width="660" height="420" style={{border:'none'}} />

Jactl comes in at **37.1M ops/s**, **47.1x** faster than the slowest, JEXL (787K).
Groovy, with 19.1M ops/s, finishes second, ahead of MVEL (7.27M) and SpEL (3.22M),
with JEXL trailing at the end.

JEXL is especially slow in this test because JEXL assumes that `+` is a numeric operation
first and tries to parse the operands as `BigInteger`, falling back to string concatenation
when that throws an exception.
This has a significant overhead as the exception fills in a stack trace every time
which is expensive to do.
The use of reflection for the string methods also penalises its performance.

SpEL, in this benchmark, despite being told to compile to bytecode, decides that
this expression can't be compiled and silently reverts to interpreted mode which
explains its poor showing here.

---

### 7. Map / Object Access

For this benchmark, the test reads three fields from a two-level nested `Map<String,Object>`
structure using dot notation: `record.name`, `record.address.city`, and `record.address.country`
for all languages except SpEL which uses square brackets for access to map fields.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
record.name + ' - ' + record.address.city + ', ' + record.address.country
// bindings: record = [ name: "Alice Smith", address: [ city: "London", country: "UK" ] ]
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
record.name + ' - ' + record.address.city + ', ' + record.address.country
// bindings: record = [ name: "Alice Smith", address: [ city: "London", country: "UK" ] ]
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
record.name + ' - ' + record.address.city + ', ' + record.address.country
// MapContext: record = { name: "Alice Smith", address: { city: "London", country: "UK" } }
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
record.name + ' - ' + record.address.city + ', ' + record.address.country
// variable map: record = { name: "Alice Smith", address: { city: "London", country: "UK" } }
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
#record['name'] + ' - ' + #record['address']['city'] + ', ' + #record['address']['country']
// variable: record = { name: "Alice Smith", address: { city: "London", country: "UK" } }
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-map-access.html" width="660" height="420" style={{border:'none'}} />

SpEL leads at **42.9M ops/s**, being **24.2x** faster than the slowest, JEXL (1.78M).
Jactl is a close second at **39.4M ops/s** with Groovy (16.3M) and MVEL (12.4M) next,
well ahead of JEXL, which trails a long way behind the rest of the field.

---

### 8. Regex Matching

This test is a simple regex validation an email string matches a pattern.
Jactl, Groovy, and SpEL have first-class regex operators while JEXL and MVEL delegate to the Java `String.matches()`
method.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
email =~ /^[a-z]+@[a-z]+\.[a-z]{2,}$/
// bindings: email="alice@example.com"
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
email ==~ /^[a-z]+@[a-z]+\.[a-z]{2,}$/
// bindings: email="alice@example.com"
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
email.matches('^[a-z]+@[a-z]+\\.[a-z]{2,}$')
// positional arg: email="alice@example.com"
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
email.matches('^[a-z]+@[a-z]+\\.[a-z]{2,}$')
// variable map: email="alice@example.com"
```

</TabItem>
<TabItem value="spel" label="SpEL">

```groovy
#email matches '^[a-z]+@[a-z]+\\.[a-z]{2,}$'
// variable: email="alice@example.com"
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-regex-match.html" width="660" height="420" style={{border:'none'}} />

All five implementations ultimately delegate to the underlying Java regular expression engine, which
narrows the gaps here compared to the arithmetic-heavy benchmarks.
Jactl leads at **14.1M ops/s**, with SpEL some way behind at **9.31M**.
Groovy (6.03M) and MVEL (5.97M) are almost tied for third, just ahead of JEXL (4.59M), the slowest.

---

### 9. Complex Script: Order Processing

This benchmark runs a simple script of around 30 lines to process a list of 20 orders, apply volume
discounts, and determine the top ranked category.
This benchmark is a realistic test of map literals, for-each loops, nested conditionals, map reads and writes,
fixed-point arithmetic, and string concatenation, all in the one test.

Note that SpEL is not tested due to it being an expression language rather than a scripting language and thus
being incapable of running multi-statement scripts.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
var totals     = [:]
var itemCount  = 0
var grandTotal = 0.0
var topCategory = ''
var topAmount   = -1.0

for (order : orders) {
    var price    = order.price
    var qty      = order.quantity
    var category = order.category

    var discount = 0.0
    if      (qty >= 100) { discount = 0.20 }
    else if (qty >=  50) { discount = 0.10 }
    else if (qty >=  20) { discount = 0.05 }

    var lineTotal = price * qty * (1.0 - discount)

    if (totals[category] == null) { totals[category] = 0.0 }
    totals[category] = totals[category] + lineTotal
    grandTotal       = grandTotal + lineTotal
    itemCount        = itemCount + 1

    if (totals[category] > topAmount) {
        topAmount   = totals[category]
        topCategory = category
    }
}

'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
var totals     = [:]
var itemCount  = 0
var grandTotal = 0.0
var topCategory = ''
var topAmount   = -1.0

for (order : orders) {
    var price    = order.price
    var qty      = order.quantity
    var category = order.category

    var discount = 0.0
    if      (qty >= 100) { discount = 0.20 }
    else if (qty >=  50) { discount = 0.10 }
    else if (qty >=  20) { discount = 0.05 }

    var lineTotal = price * qty * (1.0 - discount)

    if (totals[category] == null) { totals[category] = 0.0 }
    totals[category] = totals[category] + lineTotal
    grandTotal       = grandTotal + lineTotal
    itemCount        = itemCount + 1

    if (totals[category] > topAmount) {
        topAmount   = totals[category]
        topCategory = category
    }
}

'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
var totals     = {:}
var itemCount  = 0
var grandTotal = 0.0
var topCategory = ''
var topAmount   = -1.0

for (order : orders) {
    var price    = order.price
    var qty      = order.quantity
    var category = order.category

    var discount = 0.0
    if      (qty >= 100) { discount = 0.20; }
    else if (qty >=  50) { discount = 0.10; }
    else if (qty >=  20) { discount = 0.05; }

    var lineTotal = price * qty * (1.0 - discount)

    if (totals[category] == null) { totals[category] = 0.0; }
    totals[category] = totals[category] + lineTotal
    grandTotal       = grandTotal + lineTotal
    itemCount        = itemCount + 1

    if (totals[category] > topAmount) {
        topAmount   = totals[category]
        topCategory = category
    }
}

'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
totals     = new java.util.HashMap()
itemCount  = 0
grandTotal = 0.0
topCategory = ''
topAmount   = -1.0

foreach (order : orders) {
    price    = order.price
    qty      = order.quantity
    category = order.category

    discount = 0.0
    if      (qty >= 100) { discount = 0.20; }
    else if (qty >=  50) { discount = 0.10; }
    else if (qty >=  20) { discount = 0.05; }

    lineTotal = price * qty * (1.0 - discount)

    if (totals[category] == null) { totals[category] = 0.0; }
    totals[category] = totals[category] + lineTotal
    grandTotal       = grandTotal + lineTotal
    itemCount        = itemCount + 1

    if (totals[category] > topAmount) {
        topAmount   = totals[category]
        topCategory = category
    }
}

'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-complex-script.html" width="660" height="380" style={{border:'none'}} />

Jactl runs at **670K ops/s**, **11.3x** faster than JEXL (59.5K, the slowest). Groovy reaches 111K,
followed by MVEL (76.4K) and JEXL at the bottom.

---

### 10. Recursive Fibonacci

This test calculates `fib(15)` (= 610) using a simple recursive function.
`fib(15)` requires 1,973 recursive calls, making this benchmark dominated by function-call overhead.

Since SpEL does not support user-defined functions, it is not included in this test.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
long fib(int x) { x <= 1 ? x : fib(x - 1) + fib(x - 2) }
fib(n)
// bindings: n=15
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
long fib(int x) { x <= 1 ? x : fib(x - 1) + fib(x - 2) }
fib(n)
// bindings: n=15
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
function fib(n) {
    if (n <= 1) { return n; }
    return fib(n - 1) + fib(n - 2);
}
fib(n)
// positional arg: n=15
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```groovy
def fib(n) {
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);
};
fib(n);
// variable map: n=15  (fresh map copy required per call to avoid state pollution)
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-recursive-fib.html" width="660" height="380" style={{border:'none'}} />

This benchmark shows the most dramatic difference in performance between the different languages.
Jactl reaches **1.12M ops/s**, **1,531x** that of MVEL which only achieves **733 ops/s**.
Groovy (190K) and JEXL (5.59K) are in between.

For tests dominated by function calls, Groovy's metaobject protocol puts it at a significant disadvantage
from a performance point of view despite compiling to bytecode.

---

### 11. Closure Currying

This benchmark is a test of creating and invoking closures.
It uses a `multiplier` closure to create two new closures `twice` and `triple` that bind the `factor` argument
to `2` and `3` respectively, and then invokes these closures 100 times.

MVEL and SpEL do not support closures and are not included in this test.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```groovy
var multiplier = { factor -> { x -> x * factor } }
var twice  = multiplier(2)
var triple = multiplier(3)
for (int i = 0; i < 100; i++) { triple(twice(value)) }
// bindings: value=7
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
var multiplier = { factor -> { x -> x * factor } }
var twice  = multiplier(2)
var triple = multiplier(3)
for (int i = 0; i < 100; i++) { triple(twice(value)) }
// bindings: value=7
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```groovy
var multiplier = factor -> x -> x * factor;
var twice = multiplier(2);
var triple = multiplier(3);
for (var i = 0; i < 100; i++) { triple(twice(value)) }
// positional arg: value=7
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-closure-currying.html" width="660" height="320" style={{border:'none'}} />

Jactl is ahead in this test with **2.04M ops/s**, **30.5x** faster than JEXL (66.8K).
Groovy, at **1.65M**, comes in strongly in second place, a much narrower gap than in most
of the other benchmarks.
JEXL has good lambda support and gives a pretty respectable showing for an interpreted language.

---

## Compilation Speed

The benchmarks so far have all tested the execution speed of already compiled expressions/scripts.
We now test how fast the different languages compile simple expressions, and simple scripts.

### 12. Expression Compilation

This is a test to measure the performance of compiling a single arithmetic expression:
<br/>`(a + b) * c - d / 2 + e % 3`.

Note, to make the comparison fairer, for SpEL we evaluate the compiled script twice to force it to generate bytecode
and for MVEL we evaluate once to get it to produce any bytecode.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```java
// Compiles: (a + b) * c - d / 2 + e %% 3
// Generates JVM bytecode via ASM. Each call produces a new .class
// NOTE: In Jactl %% is equivalent to Java %
Compiler.compileScript("(a + b) * c - d / 2 + e %% 3", context, "ArithExpr" + n, bindings);
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```java
// Compiles: (a + b) * c - d / 2 + e % 3
// Parses and generates a full Groovy Script subclass
groovyShell.parse("(a + b) * c - d / 2 + e % 3");
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```java
// Compiles: (a + b) * c - d / 2 + e % 3
// Builds a JexlExpression AST (no bytecode generation)
jexlEngine.createExpression("(a + b) * c - d / 2 + e % 3");
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```java
// Compiles: (a + b) * c - d / 2 + e % 3
// Builds an optimised AST / partial bytecode plan on first execution
Object expr = MVEL.compileExpression("(a + b) * c - d / 2 + e % 3");
Object result = MVEL.executeExpression(expr, mvelArithVars);
```

</TabItem>
<TabItem value="spel" label="SpEL">

```java
// Compiles: (#a + #b) * #c - #d / 2 + #e % 3
// SpEL compiles to bytecode in IMMEDIATE mode after two calls to getValue()
Expression expr = spelParser.parseExpression("(#a + #b) * #c - #d / 2 + #e % 3");
expr.getValue(spelArithCtx);
expr.getValue(spelArithCtx);
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-compile-expression.html" width="660" height="420" style={{border:'none'}} />

MVEL dominates here at **233K compilations/s** and is **37.4x** faster than the slowest compiler, Groovy (6.23K).
JEXL is next fastest at **99.0K**, followed by SpEL (76.3K) and then Jactl (30.7K).

Although MVEL can produce some bytecode in some limited scenarios, in this example it does not appear
to be producing bytecode and so it avoids all the overhead that SpEL, Jactl, and Groovy have when having
to both generate the bytecode and then create and register the generated class with the JVM.
Similarly, JEXL shines in this benchmark for the same reason.

SpEL, even though this benchmark forces it through two evaluations to trigger its IMMEDIATE-mode bytecode
compilation, still has a strong showing.

### 13. Script Compilation

This test compiles the full ~30-line order-processing script.
The cost is higher for all languages but the gap closes significantly between them.
Note that SpEL is excluded since it does not support scripts.

<Tabs groupId="language">
<TabItem value="jactl" label="Jactl" default>

```java
// Compiles the full order-processing script (~30 lines)
// Jactl generates JVM bytecode for the entire script body
Compiler.compileScript(JACTL_COMPLEX_SCRIPT, context, "Script" + n, bindings);
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```java
// Compiles the full order-processing script (~30 lines)
// Groovy parses, resolves, and generates a Script subclass
groovyShell.parse(GROOVY_COMPLEX_SCRIPT);
```

</TabItem>
<TabItem value="jexl" label="JEXL">

```java
// Compiles the full order-processing script (~30 lines)
// JEXL builds an AST for the complete script
jexlEngine.createScript(JEXL_COMPLEX_SCRIPT);
```

</TabItem>
<TabItem value="mvel" label="MVEL">

```java
// Compiles the full order-processing script (~30 lines)
Object script = MVEL.compileExpression(MVEL_COMPLEX_SCRIPT);
HashMap orders = new HashMap() {{ put("orders", Collections.emptyList()); }};
// Execute once to force any byte code generation
Object result = MVEL.executeExpression(script, orders);
```

</TabItem>
</Tabs>

<iframe src="/charts/sl-compile-script.html" width="660" height="380" style={{border:'none'}} />

For full scripts, MVEL leads narrowly at **15.3K/s**, with JEXL (13.9K) close behind, then Jactl (12.4K),
and Groovy (2.84K).
As the script complexity grows, the overhead of defining and registering compiled classes with the JVM
becomes a smaller fraction of the overall time, allowing Jactl to catch up significantly.
Even Groovy improves from a relative performance point of view.

---

## Summary

The table below shows throughput relative to the slowest engine in each benchmark
(higher multiplier = faster).

| Benchmark             |      Jactl |   Groovy | JEXL | MVEL |      SpEL |
|-----------------------|-----------:|---------:|-----:|-----:|----------:|
| Arithmetic            |      10.3x |     2.0x | 1.0x | _baseline_ | **11.3x** |
| Complex Arithmetic    |  **15.1x** |     2.5x | _baseline_ | 1.1x |      7.3x |
| Script with Variables |  **12.5x** |     4.3x | _baseline_ | 1.0x |         - |
| Conditional           |  **13.7x** |     4.4x | _baseline_ | 1.3x |     13.0x |
| Collection Loop       |  **64.9x** |    35.1x | _baseline_ | 1.4x |         - |
| String Operations     |  **47.1x** |    24.3x | _baseline_ | 9.2x |      4.1x |
| Map / Object Access   |      22.1x |     9.2x | _baseline_ | 7.0x | **24.1x** |
| Regex Match           |   **3.1x** |     1.3x | _baseline_ | 1.3x |      2.0x |
| Complex Script        |  **11.3x** |     1.9x | _baseline_ | 1.3x |         - |
| Recursive Fibonacci   | **1,528x** |     259x | 7.6x | _baseline_ |         - |
| Closure Currying      |  **30.5x** |    24.7x | _baseline_ | - |         - |
| Compile Expression    |       4.9x | _baseline_ | 15.9x | **37.4x** |     12.2x |
| Compile Script        |       4.4x | _baseline_ | 4.9x | **5.4x** |         - |

### Conclusion

**Execution throughput**: Jactl leads (and often by a huge margin) in almost all the script execution benchmarks.
In two benchmarks SpEL comes out on top but Jactl is only just behind.
In the benchmarks where SpEL is able to compete it is in first or second place apart from the
String Operations benchmark where it inexplicably does not compile to bytecode and
suffers from running in interpreted mode.
MVEL bests JEXL for most of the benchmarks apart from the Recursive Fibonacci
benchmark where it is 7.6x slower and the Closure Currying benchmark where it
wasn't tested since it does not support closures.

**Compilation speed**: Here, MVEL really shines, with the fastest compilation speed of
all the languages in this comparison.
JEXL makes a respectable showing as well.
SpEL is only able to run the Compile Expression benchmark since it has no support for
multi-line scripts and is close behind JEXL.
Jactl's compilation speed for simple expressions can't compete with MVEL, JEXL, and SpEL
due to the overhead involved in generating bytecode and creating and registering the
generated class code with the JVM, but for longer scripts its performance becomes competitive
with MVEL and JEXL.
Groovy's compilation speed is the slowest.
Like Jactl, it does also improve relatively with longer scripts, but it is still more
than 4-5 times slower than the rest even on the Compile Script benchmark.

**Functionality**: Not all of these languages are feature compatible so which language is
best suited needs to also take into account whether the features required are offered by the
language in question.
SpEL, being an expression evaluation language, was not able to participate in 6 of the 13
benchmarks here.
The other languages are fully featured programming languages and offer all the features
tested here with the one exception being MVEL not supporting closures and being unable to
be tested in the Closure Currying benchmark comparison.

**SpEL** excels at simple expressions and has good compilation speed but the fact that it
can silently fall back to a slower interpreted mode is concerning.
Obviously, if you need more than simple expressions, SpEL is not the language for you.

**JEXL** being fully interpreted had the slowest execution speed apart from the Recursive
Fibonacci benchmark where it was substantially faster than MVEL and offers a good compilation
speed, second only to MVEL.

**MVEL** has the fastest compiler and for what is mostly an intepreted execution, was able
to better or equal JEXL in all but one of the execution benchmarks.
Featurewise, it compares pretty well, apart from lacking closures compared to JEXL,
Groovy, and Jactl.

**Groovy** offers good performance compared to the interpreted MVEL and JEXL, but is
quite a bit slower than Jactl on the benchmarks tested here and also has the slowest
compilation time of the bunch.
Groovy, however, is the most complete language in this comparison and comes with a strong
ecosystem around it.

**Jactl** offers a fully featured programming language with excellent runtime performance
and a competitive compilation speed but does not have the same extensibility that Groovy
offers with its metaobject protocol nor the same extensive ecosystem.
For simple expressions that are compiled once and executed multiple times, Jactl's
compilation overhead is recouped with its fast execution time.
For more complex scripts, the compilation speed becomes competitive and the fast
execution times is even more compelling.

If you need to compile expressions that are executed once and then thrown away, you
may be better off looking at MVEL or JEXL which offer compilation speed over execution speed.
If your application only requires simple expressions, then SpEL offers good compilation speed and,
for the most part, good execution speed.
