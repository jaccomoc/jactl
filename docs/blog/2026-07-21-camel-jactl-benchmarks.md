---
title:      "Jactl Meets Apache Camel: Benchmarking camel-jactl"
date:       2026-07-21T18:30:00+10:00
authors:    [james]
tags:       [jvm, benchmarks, performance]
description: "Introducing camel-jactl, an Apache Camel language module for Jactl, with JMH benchmarks comparing Jactl against Groovy and Camel's Simple language for predicates, expressions, transformations, and full routes."
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

[camel-jactl](https://github.com/jaccomoc/camel-jactl) is a new [Apache Camel](https://camel.apache.org)
language module that lets you use [Jactl](https://jactl.io) as the scripting language inside Camel for things such as
filter predicates, content-based routing rules, and message transformations.
It registers itself through Camel's Language SPI under the name `jactl`.

```java
import static io.jactl.camel.JactlLanguage.jactl;

from("direct:orders")
    .filter(jactl("body.amount > 100 && body.status == 'NEW'"))
    .to("direct:big-orders");

// Or via the Language SPI:
Language jactl = camelContext.resolveLanguage("jactl");
Predicate p    = jactl.createPredicate("body.age > 20");
Expression e   = jactl.createExpression("'Hello ' + body.name");
```

This post uses JMH benchmarks to compare the performance of Jactl against Camel's built-in
Simple language and against Groovy (via `camel-groovy`) in the places where scripting languages
typically appear in Camel applications, from single predicate evaluations up to a complete
order-processing route, with a plain-Java route as the no-scripting baseline.

<!-- truncate -->

## Jactl in Camel

Scripts in Camel routes usually make small decisions over message data: is this order valid,
which queue does it go to, what should the summary body look like.
Jactl is a good fit for this role:

* **It compiles to JVM bytecode**: predicates and expressions run at close to Java speed once compiled.
* **It is sandboxed by default**: Scripts can work freely with Map/List/String/numeric message data,
  but cannot call methods on host objects such as the exchange or a POJO body unless you opt in
  (`JactlLanguage.createWithHostAccess()`, with an optional predicate to whitelist specific classes).
* **It has no external dependencies**: a single ~1.1MB jar with a shaded copy of ASM.

Jactl borrowed much of its syntax from Groovy so users familiar with Groovy but looking for a stronger
secure sandbox model or better performance might want to consider Jactl as an alternative.
Groovy is a more powerful and flexible language with its built-in metaobject protocol but this comes
with some performance disadvantages compared to Jactl, and Groovy does not offer an out-of-the-box
locked-down execution environment like Jactl does.

Scripts see the message data as ordinary variables (`body`, `headers`, `variables`), and the rest
of Camel's usual script variables (`exchangeProperties`, `exchange`, `camelContext`, ...) are
available by opting in with `withAllowContextMapAll(true)`.

Scripts are cached so the compilation cost is only incurred once.

## The Benchmarks

The benchmarks are modelled on the [camel-jmh](https://github.com/apache/camel-performance-tests/tree/main/tests/camel-jmh)
module in Apache's own camel-performance-tests project and can be found in the
[benchmarks](https://github.com/jaccomoc/camel-jactl/tree/main/benchmarks) subproject of camel-jactl.

| Component                   | Version        |
|-----------------------------|----------------|
| Apache Camel                | 4.20.0         |
| Jactl                       | 2.9.2          |
| Groovy (via `camel-groovy`) | 5.0.5          |
| JMH                         | 1.37           |
| JVM                         | OpenJDK 25.0.2 |

Where the same script is run in more than one language, the setup asserts that all languages
produce identical results before anything is timed, so every language is doing the same work.

:::note
The absolute numbers are not important since they depend on the machine the benchmarks are run on.
It is the relative performance that matters.
:::

The three languages compared:

* **Simple** is Camel's built-in expression language. It is not a general-purpose language, but is commonly used.
* **Groovy** (`camel-groovy`) compiles each script to a Groovy `Script` class which is cached and reused.
* **Jactl** (`camel-jactl`) compiles each script to JVM bytecode via its embedded ASM and caches the compiled script.

---

## 1. Predicate and Expression Evaluation

The first set of benchmarks measures the pure evaluation cost of pre-compiled predicates and
expressions, resolved through the Language SPI and evaluated against an exchange whose body is
a `Map` (`name: "tony"`, `age: 44`) with one header (`gold: "123"`).

### Map body predicate

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>

```groovy
body.name != null && body.age > 20
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
body.name != null && body.age > 20
```

</TabItem>
<TabItem value="simple" label="Simple">

```
${body['name']} != null && ${body['age']} > 20
```

</TabItem>
</Tabs>

<iframe src="/charts/camel-map-predicate.html" width="660" height="320" style={{border:'none'}} />

Jactl leads at **96.3M evaluations/s**, 15.7x faster than Groovy (6.13M) and **56.1x** faster than
Simple (1.72M).
The Simple predicate contains two `${...}` expressions combined with `&&`, an indexed `body['name']` access,
and `!=`/`>` comparisons go through Simple's dynamic type-coercion rules on every evaluation.
Simple parses the predicate once, but evaluation still walks that parsed structure, whereas
Groovy and Jactl execute compiled bytecode.

### Header predicate

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>

```groovy
headers.gold == '123'
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
headers.gold == '123'
```

</TabItem>
<TabItem value="simple" label="Simple">

```
${header.gold} == '123'
```

</TabItem>
</Tabs>

<iframe src="/charts/camel-header-predicate.html" width="660" height="320" style={{border:'none'}} />

This is a simpler predicate for Simple and allows it to shine compared to the previous benchmark
with (56.8M evaluations/s) being 8.9x faster than Groovy.
Jactl still comes out ahead at **112M evaluations/s**, twice as fast as Simple and **17.6x**
faster than Groovy.

### Value expression

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>

```groovy
'Hello ' + body.name
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
'Hello ' + body.name
```

</TabItem>
<TabItem value="simple" label="Simple">

```
Hello ${body['name']}
```

</TabItem>
</Tabs>

<iframe src="/charts/camel-value-expression.html" width="660" height="320" style={{border:'none'}} />

Jactl is fastest at **77.6M evaluations/s**, 12.8x faster than Groovy (6.05M) and **25.6x**
faster than Simple (3.04M).

---

## 2. Filter Route

The next benchmark moves from raw evaluation to a real route: a Java DSL route whose filter
predicate calls out to the scripting language, the way an application would actually use
`camel-groovy` or `camel-jactl`.
Each invocation sends two exchanges through the route — one that passes the filter and one
that does not.

```java
from("direct:jactl")
    .filter(jactl("body.age > 20"))
    .process(e -> { });
```

<iframe src="/charts/camel-filter-route.html" width="660" height="320" style={{border:'none'}} />

Once the full Camel routing engine is between the sender and the predicate, the gaps become smaller.
Jactl completes **1.27M route invocations/s** (each invocation is two exchanges),
1.8x ahead of Groovy (695K) and 2.0x ahead of Simple (650K).

This benchmark measure includes the cost of the overall routing machinery which is why the difference
in performance is smaller than the raw execution speeds from the previous benchmarks.

---

## 3. Complex Scripts

This set of benchmarks evaluates the raw processing speed of more complex scripts over order bodies of 10 
and 200 line items.

The Groovy and Jactl sources are identical apart from idiomatic method names
(Groovy `collect`/`every` vs Jactl `map`/`allMatch`) and both languages are verified to produce
equal results before anything is timed.
Prices are integer cents so the arithmetic is exact and type-identical in both languages.

### Order summary transformation

A closure pipeline computing an order total, a tier-based discount, per-category subtotals,
and a summary string:

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>

```groovy
def customer = body.customer
def items    = body.items
def total    = items.map{ it.qty * it.unitPrice }.sum()
def discount = customer.tier == 'gold' && total >= 10000 ? 1500 : customer.tier == 'silver' && total >= 10000 ? 500 : 0
def subtotals = [:]
items.each{ subtotals[it.category] = (subtotals[it.category] ?: 0) + it.qty * it.unitPrice }
def result = [customer:customer.name, total:total, discount:discount, payable:total - discount, categories:subtotals, summary:"${customer.name}: ${items.size()} items, payable ${total - discount}c".toString()]
result
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
def customer = body.customer
def items    = body.items
def total    = items.collect{ it.qty * it.unitPrice }.sum()
def discount = customer.tier == 'gold' && total >= 10000 ? 1500 : customer.tier == 'silver' && total >= 10000 ? 500 : 0
def subtotals = [:]
items.each{ subtotals[it.category] = (subtotals[it.category] ?: 0) + it.qty * it.unitPrice }
def result = [customer:customer.name, total:total, discount:discount, payable:total - discount, categories:subtotals, summary:"${customer.name}: ${items.size()} items, payable ${total - discount}c".toString()]
result
```

</TabItem>
</Tabs>

<iframe src="/charts/camel-summary-script-10.html" width="660" height="270" style={{border:'none'}} />

<iframe src="/charts/camel-summary-script-200.html" width="660" height="270" style={{border:'none'}} />

At 10 line items Jactl evaluates the summary **2.66M times/s**, **3.6x** faster than
Groovy (746K).
At 200 line items Jactl's advantage grows to **4.7x** (235K vs 50.1K evaluations/s).

### Express-handling predicate

A whole-list scan (the data contains no `'fragile'` items, so there is no early exit) plus
short-circuit business rules:

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>

```groovy
def items = body.items
items.allMatch{ it.category != 'fragile' } && items.map{ it.qty * it.unitPrice }.sum() < 10000000 && body.customer.tier in ['gold','silver']
```

</TabItem>
<TabItem value="groovy" label="Groovy">

```groovy
def items = body.items
items.every{ it.category != 'fragile' } && items.collect{ it.qty * it.unitPrice }.sum() < 10000000 && body.customer.tier in ['gold','silver']
```

</TabItem>
</Tabs>

<iframe src="/charts/camel-express-predicate-10.html" width="660" height="270" style={{border:'none'}} />

<iframe src="/charts/camel-express-predicate-200.html" width="660" height="270" style={{border:'none'}} />

Here Jactl is **7.4x** faster at 10 items (9.12M vs 1.23M evaluations/s) and **5.2x** faster
at 200 items (472K vs 90.8K).

---

## 4. Full Order-Processing Route

The final throughput benchmark puts it all together: a realistic order-processing route where
the scripting language does the work in the three places scripts typically appear —
validation, content-based routing, and transformation:
```java
from("direct:orders-jactl")
    .filter(jactl(VALIDATION))                    // scripted validation
    .choice()
        .when(jactl(EXPRESS))                     // scripted content-based routing
            .setHeader("handling", constant("express"))
        .otherwise()
            .setHeader("handling", constant("standard"))
    .end()
    .setBody(jactl(SUMMARY))                     // scripted transformation
    .process(e -> { });
```

### VALIDATION
<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>
```groovy
body.customer != null && body.items && body.items.allMatch{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>
<TabItem value="groovy" label="Groovy">
```groovy
body.customer != null && body.items && body.items.every{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>
<TabItem value="java" label="Java">
```java
static boolean isValid(Map<String, Object> body) {
  Object customer = body.get("customer");
  List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
  return customer != null && items != null && !items.isEmpty()
    && items.stream().allMatch(it -> (Integer) it.get("qty") > 0 && (Integer) it.get("unitPrice") > 0);
}
```
</TabItem>
</Tabs>

### EXPRESS

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>
```groovy
body.customer != null && body.items && body.items.allMatch{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>
<TabItem value="groovy" label="Groovy">
```groovy
body.customer != null && body.items && body.items.every{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>

<TabItem value="java" label="Java">
```java
static boolean qualifiesExpress(Map<String, Object> body) {
  List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
  Map<String, Object> customer = (Map<String, Object>) body.get("customer");
  return items.stream().allMatch(it -> !"fragile".equals(it.get("category")))
         && items.stream().mapToInt(it -> (Integer) it.get("qty") * (Integer) it.get("unitPrice")).sum() < 10000000
         && List.of("gold", "silver").contains(customer.get("tier"));
}
```
</TabItem>
</Tabs>

### SUMMARY

<Tabs groupId="camel-language">
<TabItem value="jactl" label="Jactl" default>
```groovy
body.customer != null && body.items && body.items.allMatch{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>
<TabItem value="groovy" label="Groovy">
```groovy
body.customer != null && body.items && body.items.every{ it.qty > 0 && it.unitPrice > 0 }
```
</TabItem>

<TabItem value="java" label="Java">
```java
static Map<String, Object> summarize(Exchange exchange) {
  Map<String, Object> body = mapBody(exchange);
  Map<String, Object> customer = (Map<String, Object>) body.get("customer");
  List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
  int total = items.stream().mapToInt(it -> (Integer) it.get("qty") * (Integer) it.get("unitPrice")).sum();
  Map<String, Integer> subtotals = items.stream().collect(Collectors.groupingBy(
      it -> (String) it.get("category"),
      Collectors.summingInt(it -> (Integer) it.get("qty") * (Integer) it.get("unitPrice"))));
  String tier = (String) customer.get("tier");
  int discount = "gold".equals(tier) && total >= 10000 ? 1500 : "silver".equals(tier) && total >= 10000 ? 500 : 0;
  Map<String, Object> result = new HashMap<>();
  result.put("customer", customer.get("name"));
  result.put("handling", exchange.getIn().getHeader("handling"));
  result.put("total", total);
  result.put("discount", discount);
  result.put("payable", total - discount);
  result.put("categories", subtotals);
  result.put("summary", customer.get("name") + ": " + items.size() + " items, payable " + (total - discount) + "c");
  return result;
}
```
</TabItem>
</Tabs>

Each invocation sends one valid order and one invalid (filtered-out) order through the route,
over bodies of 10 and 200 line items.
A third route implements identical logic in plain Java as the no-scripting baseline, and the
setup asserts all three routes produce equal summaries before anything is timed.

<iframe src="/charts/camel-order-route-10.html" width="660" height="320" style={{border:'none'}} />

<iframe src="/charts/camel-order-route-200.html" width="660" height="320" style={{border:'none'}} />

At 10 line items the plain-Java route manages **632K invocations/s**, with Jactl at
**572K** (91% of the Java baseline) and **2.6x** faster than Groovy (224K, or 35% of Java).

At 200 line items, Jactl holds **70%** of the Java baseline (106K vs 151K) and is **3.9x** faster
than Groovy (27.4K, 18% of Java).

---

## 5. Compilation Speed

The benchmarks so far measure scripts that are compiled once and evaluated millions of times,
the normal case, since both `camel-groovy` and `camel-jactl` cache compiled scripts by script text.
The last benchmark measures the cost of the first compile: compiling a predicate and evaluating
it once, with unique script text per invocation to bypass the caches.

```groovy
body.name != null && body.age > 20   // plus a unique comment per invocation
```

<iframe src="/charts/camel-compile.html" width="660" height="270" style={{border:'none'}} />

Jactl can compile and evaluate **30.0K unique predicates per second** which is **9.3x** more than
Groovy's 3.23K, even though both languages are generating and loading real JVM bytecode.
For routes defined at startup this only affects startup time, but it matters if your
application creates scripts dynamically.

Note that continually creating new scripts will eventually exhaust the heap as the class loader
is bound to the JactlLanguage instance.

---

## Summary

The table below shows throughput relative to the slowest language in each benchmark
(labelled _baseline_).
The higher the multiplier, the faster the language.

| Benchmark                      |      Jactl |     Groovy |     Simple |     Java |
|--------------------------------|-----------:|-----------:|-----------:|---------:|
| Map body predicate             |  **56.1x** |       3.6x | _baseline_ |        - |
| Header predicate               |  **17.6x** | _baseline_ |       8.9x |        - |
| Value expression               |  **25.6x** |       2.0x | _baseline_ |        - |
| Filter route (end-to-end)      |   **2.0x** |       1.1x | _baseline_ |        - |
| Summary script (10 items)      |   **3.6x** | _baseline_ |          - |        - |
| Summary script (200 items)     |   **4.7x** | _baseline_ |          - |        - |
| Express predicate (10 items)   |   **7.4x** | _baseline_ |          - |        - |
| Express predicate (200 items)  |   **5.2x** | _baseline_ |          - |        - |
| Order route (10 items)         |       2.6x | _baseline_ |          - | **2.8x** |
| Order route (200 items)        |       3.9x | _baseline_ |          - | **5.5x** |
| Compile + first evaluation     |   **9.3x** | _baseline_ |          - |        - |

### Conclusion

Jactl has both good runtime performance and compilation performance and provides the benefits of
compiled code with the flexibility of a scripting language along with a configurable security
sandbox model that allows the application to control what scripts can and cannot do.

### Further Reading

The camel-jactl source, including these benchmarks, is at
[github.com/jaccomoc/camel-jactl](https://github.com/jaccomoc/camel-jactl).

There is a comparison of Jactl and Groovy here: [Groovy vs Jactl](https://jactl.io/blog/2026/03/16/groovy-vs-jactl).

There is also a benchmark of [Jactl, Groovy, MEXL, JEXL, and SpEL](https://jactl.io/blog/2026/07/14/scripting-language-benchmarks). 

The [Jactl Documentation](https://jactl.io) site has full documentation for the language and the
source code can be found in the [Jactl GitHub Repository](https://github.com/jaccomoc/jactl).