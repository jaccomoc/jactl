---
title: Jactl for Groovy Programmers
description: "A quick overview of the similarities and differences between Jactl and Groovy for programmers who are already familiar with Groovy."
---

# Jactl for Groovy Programmers

> This section was co-authored with [Claude](https://claude.ai).

A fast-onboarding guide for Groovy programmers. Jactl deliberately borrowed a lot of its surface
syntax from Groovy — closures, `it`, GString-style interpolation, `?.`, `?:`, `<=>`, list/map
literals, optional typing — so most of what you already know transfers directly. This guide skips
the parts that are identical and concentrates on the differences that will actually trip you up:
things that *look* the same but behave differently, things Groovy has that Jactl leaves out, and
the handful of things Jactl does that Groovy can't.

It's a "how to write code" guide, not a "which should I choose" pitch. For the evaluation angle
(performance, security, tooling, ecosystem) see the [Groovy vs Jactl](/blog/2026/03/16/groovy-vs-jactl) comparison.

---

## The 60-Second Orientation

| Category                | Jactl vs Groovy                                                                              |
|-------------------------|---------------------------------------------------------------------------------------------|
| **Identical**           | closures, `it`, GStrings, `?.`, `?:`, `<=>`, `[..]`/`[a:1]` literals, `instanceof`, `as`, `in`, `def`/`var`, truthiness, `package`/`import` |
| **Renamed**             | `collect`→`map`, `findAll`→`filter`, `inject`→`reduce`, `every`/`any`→`allMatch`/`anyMatch` (also `noneMatch`), `average`→`avg`, `collectMany`→`flatMap` |
| **Same name, different behaviour** | `%` (modulo, not remainder), `collect` (forces eager eval, isn't `map`), collection chains (lazy, not eager), `unique` (consecutive-only, not global), `Map` collection ops (return `[k,v]` pair lists, not a `Map`) |
| **Gone**                | `try`/`catch`/`throw`, `Set`, `Range` (`..`), `short`/`char`/`float`, generics, interfaces, traits, custom constructors, visibility modifiers, static mutable fields, metaobject protocol, operator overloading, spread operator, `with` |
| **New**                 | postfix `if`/`unless`, `and`/`or`/`not`, `do{}` expression, `do/until`, `$1`/`$2` capture vars, `s///`, destructuring `switch` and `for`, auto-vivification, continuations, checkpointing, sandboxing |

---

## 1. Things You Can Keep Doing Verbatim

These carry over from Groovy essentially unchanged:

- `//` and `/* */` comments; semicolons optional (only to separate multiple statements on a line).
- Closures: `{ x, y -> x + y }`, the implicit `it`, trailing-closure syntax (`list.map { it * 2 }`),
  and closing over / mutating outer variables.
- `def` for dynamic typing; `var` for inferred static typing.
- List literals `[1,2,3]` and map literals `[a:1, b:2]` (unquoted keys if they're valid identifiers
  — even keywords: `[for:1, while:2]`), plus JSON-style `{"a":1}` for maps.
- Double-quoted interpolation (`"Value of $x is ${x*2}"`) and triple-quoted multi-line strings.
- `?.`, `?:`, `<=>`, `==`/`!=` (value equality), `===`/`!==` (identity — Groovy uses `.is()`, or
  `===`/`!==` in Groovy 3+).
- `instanceof`/`!instanceof`, `as` for conversion, `in`/`!in` for membership.
- Multiple assignment and multi-declaration: `def (x, y) = [1, 2]` (and `(x, y) = [y, x]` to swap),
  including per-variable types (`def (int i, String s) = [1, 'abc']`) and destructuring a `String`
  into its leading characters (`def (a, b) = 'abc'` → `a='a', b='b'`). A too-short right-hand side
  pads with `null`; extras are ignored. All of this matches Groovy (verified against Groovy 5).
- Truthiness — `false`, `0`, `null`, `''`, `[]`, `[:]` are false, everything else true.
- `package` / `import` / `import static`, including aliasing with `as`.

Everything below is where the two languages diverge.

---

## 2. Typing: One Compiler, Two Modes

This is the most important conceptual shift. Groovy is dynamic by default; you *can* declare types,
but plain typed Groovy still only checks them at runtime unless you reach for `@CompileStatic`:

```groovy
// Groovy: compiles fine, throws at runtime.
int i = 'this is a string'
```

In Jactl a declared type is **always** enforced at compile time — there is no separate static mode to
opt into:

```groovy
// Jactl: this is a compile-time error, full stop.
int i = 'this is a string'
```

So `def` in Jactl isn't just "how you write Groovy" — it's a real per-declaration choice between two
modes: fully dynamic (`def`, checked at runtime, like plain Groovy) or fully typed (checked at
compile time, no annotation required). You decide per variable / parameter / field / return type.

`var` works as in Groovy/Java — it infers a concrete static type from the initialiser:

```groovy
var x = 1      // int
var y = 2L     // long
var z = x + y  // long
```

There are **no generics** (`List<String>`) and no `@CompileStatic`-style annotation, because there's
only ever one compiler behaviour to opt into, per type, everywhere.

---

## 3. Numbers

Both languages default undecorated decimal literals to a `BigDecimal`-backed type for exact
arithmetic — Jactl just calls it `Decimal`:

```groovy
12.12 + 12.11        // Decimal, exact: 24.23
12.12D + 12.11D      // double, approximate: 24.229999999999997
```

Groovy gives you the whole Java numeric zoo (`byte`, `short`, `char`, `int`, `long`, `float`,
`double`). Jactl has only **`int`, `long`, `double`, `Decimal`, and `byte`** — no `short`, `char`,
or `float`. Force an integer literal to `long` with `L`, and a floating-point literal to `double`
with `D` (a bare literal with a decimal point is `Decimal`):

```groovy
long   n = 9223372036854775807L
double d = amount * 1.5D
```

`byte` is treated as **unsigned** (0–255), unlike Java/Groovy's signed byte — mostly relevant when
handling byte arrays coming from a registered Java function.

### `%` is modulo, not remainder

This one will bite you if you paste in Java/Groovy arithmetic. Jactl splits the single Java `%` into
two operators:

```groovy
-5 %% 3   // -2   — remainder, exactly like Java/Groovy '%': sign follows the dividend
-5 %  3   //  1   — true modulo: sign follows the divisor (like Python's %)
```

`%%` compiles to a single JVM instruction; `%` is defined as `((x %% y) + y) %% y` and always yields
a result with the divisor's sign — which is what you want for "wrap this index into `0..n-1`". Use
`%%` when you need Java-identical behaviour or when you know the left side is non-negative and care
about the extra instructions.

### No `Range` type

There is no `Range` type and no `..` / `..<` operators. A plain number **is** the sequence `0` to
`n-1` — see the next section.

---

## 4. Strings and Characters

Single / double / triple-quoted strings, GString interpolation, slashy `/.../` strings, and negative
indexing all behave as in Groovy. The one genuine difference is the character type.

**There is no `char` type** — but this matches Groovy more than you might expect. In *both* languages
a single-quoted `'a'` is a `String` (neither has a char *literal* — single and double quotes are both
strings), and indexing a string returns a length-1 `String`:

```groovy
'abc'[0]         // 'a'   — a length-1 String, same as Groovy
'abc'[-1]        // 'c'   — negative indexes count from the end, same as Groovy
'abc'[0] == 'a'  // true
```

The difference is that Groovy still keeps the Java `char` *type* around — `char c = 'a'`,
`'a' as char`, and `(char)97` all produce a `Character` — whereas Jactl has no `char` type at all. To
get a character's Unicode value, cast the length-1 string to `int`; `asChar()` converts back:

```groovy
(int)'a'         // 97    — Groovy produces 97 here too
97.asChar()      // 'a'   — Jactl's way back (in Groovy you'd write `97 as char`)
```

Jactl's pattern/regex delimiter `/.../` works like Groovy's slashy strings — multi-line, backslash
escapes left intact for the regex engine, and `${...}` interpolation supported:

```groovy
String pattern = /c\.d\[12[0-9]]/
x =~ pattern
```

(As in Groovy, an empty `//` isn't a valid pattern — both languages parse it as a line comment, so
an empty slashy/pattern string simply isn't representable.) The genuinely Jactl-specific regex
features — Perl-style capture variables and `s///` substitution — are covered in
[§10](#10-regular-expressions).

---

## 5. Collections

`List` (`[1,2,3]`) and `Map` (`[a:1,b:2]`) work like Groovy's, including `+`, `-`, `<<`, `+=`,
subscripts, and `?[` (null-safe subscript). Maps also JSON-decode `{"a":1}` syntax. Map keys must be
strings.

There is **no `Set`**, and no arbitrary Java collection types — Jactl ships only `List` and
`Map`. The idiom for set semantics is a `Map` keyed by the members:

```groovy
def seen = [:]
seen['abc'] = true
'abc' in seen      // true — 'in' checks Map keys
```

### Method names follow Java Streams, not Groovy

| Groovy                    | Jactl                                      |
|---------------------------|--------------------------------------------|
| `collect { }` (transform) | `map { }`                                  |
| `findAll { }`             | `filter { }`                               |
| `inject(init) { }`        | `reduce(init) { }`                         |
| `every { }` / `any { }`   | `allMatch { }` / `anyMatch { }` (+ `noneMatch { }`) |
| `collectMany { }`         | `flatMap { }`                              |
| `average()`               | `avg()`                                    |

`each`, `sort`, `join`, `reverse`, `unique`, `sum`, `min`, `max`, `groupBy`, and `transpose` keep
their familiar names, though `unique` and `reverse` share the
name but not the behaviour (see [below](#same-name-different-behaviour)).
Two things to watchout for:

- Groovy's transform `collect` becomes **`map`**. Jactl *also* has a `collect`, which works the same way as `map` but forces
  eager evaluation (see below).
- There is **no `flatten`** in Jactl. Flatten a nested list with `flatMap()`
  (`[[1,2],[3,4]].flatMap()` → `[1, 2, 3, 4]`).

### Same name, different behaviour

A few methods share Groovy's name but don't behave identically:

- **`unique` removes only *consecutive* duplicates** (like Unix `uniq`), whereas Groovy's `unique()`
  removes them globally. `[1,2,2,3,1].unique()` is `[1, 2, 3, 1]` in Jactl but `[1, 2, 3]` in Groovy.
  To dedup globally in Jactl, sort first: `list.sort().unique()`.
- **Collection methods on a `Map` return a `List` of `[key, value]` pairs**, not a `Map`. Groovy's
  `map.findAll{}` / `map.sort{}` give you back a `Map`; Jactl's `filter` / `sort` on a map yield
  `[['b', 2], ['c', 3]]`-style lists. Wrap the result in `collectEntries` to rebuild a map. (Closures
  still destructure each pair, so `{ k, v -> ... }` works.)
- **`reverse` on a `String` returns a list of characters** (`['c', 'b', 'a']`) in Jactl, where Groovy returns a reversed `String` (`'cba'`). Use `str.reverse().join()` to get back a reversed string.

Jactl also adds stream-style methods that Groovy doesn't have under those names — `mapWithIndex`,
`collectEntries`, `grouped`, `windowSliding`, `limit`, `skip`, `subList` — see [Collection
Methods](language-guide/collection-methods) for the full list.

### Chains are lazy, not eager

In Groovy each `.collect{}` / `.findAll{}` materialises a whole new list before the next step. In
Jactl elements flow through the entire chain one at a time and **nothing runs until something forces
it** (`.sum()`, `.join()`, `.each{}`, etc.):

```groovy
[1,2,3,4].map{ it * it }.filter{ it > 4 }.sum()   // builds only the final result, no intermediates
```

Careful: Jactl's `.collect{}` still exists and still transforms elements, but its real job here is to
**force eager evaluation** of a pipeline stage (useful when a mapping closure has side effects that
must all happen before the next stage). If you write `.collect{}` out of Groovy habit it works, but
it is *not* just an alias for `.map{}`.

### A number is a collection

With no `Range`, the replacement for Groovy's `(0..<n)` is that a number is itself the sequence `0`
to `n-1`, and every collection method works on it directly:

```groovy
// Groovy                       // Jactl
(0..<5).each { println it }     5.each { println it }
                                10.map{ it + 1 }.sum()          // 55
                                for (int i in 3) { println i }  // 0, 1, 2
```

Strings are iterable the same way: `'abc'.map{ it.toUpperCase() }`.

---

## 6. Null Safety and Auto-Vivification

`?.`, `?:`, and `?[` (null-safe subscript) all behave as in Groovy — including `?[`, which Groovy
has had since 3.0:

```groovy
def x
x?.a      // null, no error
x?[0]     // null, no error
```

The genuinely new part: assigning *through* a chain of Map/List accesses auto-creates the
intermediate structures instead of throwing:

```groovy
def x = [:]
x.a.b.c = 1
x                 // [a:[b:[c:1]]]

x.d.e[2] = 'abc'  // subscript ⇒ a List is created here
x                 // [a:[b:[c:1]], d:[e:[null, null, 'abc']]]
```

No more `if (x.a == null) x.a = [:]` ladders before writing a deep field. (Subscript context creates
a `List`; field-access context creates a `Map`.)

---

## 7. Functions and Closures

Closures are unchanged. Functions/methods add two conveniences Groovy lacks in this exact form.

**Named arguments bind to real parameter names of any function**, with defaults and any order:

```groovy
def format(num, int base = 10) { sprintf("%9s", num.toBase(base) as String) }

format(300)                  // positional
format(base: 16, num: 300)   // named, any order
```

(Groovy's `foo(x:1, y:2)` actually collects the pairs into an implicit `Map` argument — the same
mechanism behind `new Point(x:1, y:2)` — rather than matching an arbitrary method's real parameters.)

**Function values need no `.&` operator** — just use the bare name:

```groovy
def add(x, y) { x + y }
def g = add
g(3, 4)                          // 7
def apply(f, x, y) { f(x, y) }
apply(add, 3, 4)                 // 7
```

---

## 8. Extra Control Flow

Several control-flow constructs that Groovy doesn't have (or spells differently):

**Postfix `if` / `unless`** — Perl/Ruby-style statement modifiers:

```groovy
println x if x % 10 == 0
avg = total / count unless count == 0
```

**Low-precedence `and` / `or` / `not`** — even lower than `=`, so they read as guard clauses (`&&`,
`||`, `!` still exist at their usual high precedence):

```groovy
x == 7 and y = x + (y % 13)             // assign only when x == 7
/^\$ *cd +\/$/r and do { cwd = root; return }
```

**`do { }` as an expression** — a block of statements whose value is its last statement, handy for
passing a multi-statement computation directly as an argument:

```groovy
def x = do { def i = 3; def j = 4; i + j }   // 7
```

**`do/until`** — a bottom-tested loop that runs *until* the condition becomes true (the inverse of
Java's `do/while`):

```groovy
def i = 0
do { println i; i++ } until (i == 5)
```

**`eval(str)` / `eval(str, vars)`** — a built-in that compiles and runs a string of Jactl at
runtime, optionally against a map the code can read and write. (Groovy can eval at runtime too, via
`Eval.me(...)` or `new GroovyShell().evaluate(...)`; the difference is that Jactl's `eval` is a plain
global function that runs inside the same sandbox and shares the supplied variables.)

```groovy
def vars = [x:3, y:4]
eval('x += y', vars)   // 7
vars                   // [x:7, y:4]
```

Loop **labels** work with `break`/`continue` (`OUTER: for (...) { ... continue OUTER if ... }`), and
`for`, `while`, and the classic C-style `for (init; cond; update)` are all present.

---

## 9. `switch` Expressions and Destructuring

Jactl's `switch` is **always an expression** — its value is the value of the branch that matched —
and its headline feature is **structural destructuring**: matching against the *shape* of a `List`,
`Map`, or class instance while binding parts of it in the same step.

```groovy
def qsort(x) {
  switch (x) {
    [], [_]  -> x
    [h, *t]  -> qsort(t.filter{ it < h }) + [h] + qsort(t.filter{ it >= h })
  }
}
```

Pattern vocabulary:

- `_` matches and discards one value; `*` matches any number of list elements (and can bind, e.g.
  `[head, *tail]`).
- A bare identifier binds that position; repeating the same identifier requires equal values at each
  position.
- A type name (`int`, `String`, a user class) matches only values of that type.
- Maps match on key presence: `[name:_, age:_, *]` (the trailing `*` allows extra keys).
- Classes match with constructor-like syntax, positional or named, including nesting:
  `Point(x:0, y:_)`, `ZZZ(X(a,b), Y(5,6,c))`.

The **same pattern language works in `for` loops** — genuinely new territory with no Groovy
equivalent:

```groovy
for ([name: n, age: a, *] in customers) {
  println "$n is $a"
}
```

One sharp edge: `switch` compares numeric literals by **exact type**, not just value — `1L` won't
match a bare `1` pattern (which expects `int`), even though `1L == 1` is `true` elsewhere. This is
intentional, so you can pattern-match on exact type.

---

## 10. Regular Expressions

Groovy's `=~` hands you a `Matcher` to query. Jactl is closer to Perl: capture groups populate
variables `$1`, `$2`, … (`$0` is the whole match), and there's a dedicated substitution operator.

```groovy
if ('Total: 14ms' =~ /: (\d+)(.*)$/) {
  println "Amount is $1, unit is $2"   // Amount is 14, unit is ms
}

def str = 'This Example Text Is Not Complex'
str =~ s/Example/Simple/           // substitutes in place, returns true/false
def copy = str =~ s/Example/Simple/r   // 'r' = non-destructive, returns the new string
```

Modifiers append after the closing `/`:

| Mod | Meaning |
|-----|---------|
| `i` | case-insensitive |
| `m` | multi-line `^`/`$` |
| `s` | dot matches newline |
| `g` | global — iterate all matches; **only legal inside a `while`/`for` condition** |
| `n` | numeric capture groups become `long`/`Decimal` |
| `r` | treat a bare `/pattern/` as a match against implicit `it` (`it =~ /pattern/`) rather than a plain string value; for `s///` makes the substitution non-destructive |

Omit the string operand and the match/substitution runs against the implicit `it` — handy inside
`.filter{}`/`.map{}` and in `-p`/`-n` command-line modes:

```groovy
list.map{ s/[aeiou]//g }   // strip vowels from every element
```

---

## 11. Classes

Same big shape — `class Name { ... }`, `extends`, `this`, `super`, method override — but the details
differ:

**No visibility modifiers.** No `private`/`protected`/`public`; every field and method is public.

**No custom constructors.** Each class gets two generated constructors: positional (one parameter per
*mandatory* field — those without a default — in declaration order) and named
(`new Car(make:'Ford', model:'Falcon', year:1987)`, any order, can set optional fields too). For
validation logic, write a `static` factory method:

```groovy
class Circle {
  Point  centre
  double radius
  static Circle create(double x, double y, double r) {
    die 'Radius must be > 0' if r <= 0
    return new Circle(new Point(x, y), r)
  }
}
```

**No static fields** (mutable state), by design — Jactl targets multi-tenant, distributed execution,
so there's no shared mutable state to synchronise or leak. `static` is for stateless methods only;
use `const` for numeric/String constants.

**No interfaces, traits, or generics.** `interface`, `implements`, and `sealed` are *reserved
keywords* in the grammar, but nothing currently parses or compiles them — not a feature yet. Traits
don't exist at all (`trait` isn't even a reserved word). There's also no metaobject protocol: no
`metaClass`, Categories, `Expando`, operator overloading, `with`, or spread operator.

Field defaults may reference earlier fields:

```groovy
class X {
  int x
  int y = x * x
}
```

---

## 12. Error Handling: `die`, not `try`/`catch`

There is no `throw`, no `try`, no `catch`. To abort with an error, use `die`:

```groovy
die 'Radius must be > 0' if r <= 0
```

Unhandled runtime errors (null dereference, bad index, …) propagate up and terminate the script;
there is currently no way to catch and recover from them inside Jactl. In exchange, error messages
point at the exact **column** that failed:

```
io.jactl.runtime.RuntimeError: Index out of bounds: 2 @ line 3, column 34
println arr[i++] + arr[i++] + arr[i++]
                                 ^
```

---

## 13. Built-in JSON

JSON is baked into every value — no import required. Every object gets `toJson()`, every string gets
`fromJson()`, and every user class gets a generated `toJson()` instance method plus a static
`fromJson()`:

```groovy
class X { int i = 123; String s = 'abc' }
new X().toJson()                       // {"i":123,"s":"abc"}
X.fromJson('{"i":456,"s":"xyz"}')      // an X instance
```

---

## 14. The Sandbox: No Java Access by Default

A mindset shift more than a syntax one. Groovy scripts have the full JVM at hand — any classpath
class, threads, file I/O, sockets, reflection. Jactl scripts by default can touch **only** what the
embedding application hands them: bound global variables, plus functions and classes the host has
registered. No `new File(...)`, no reflection, no threads — unless the host opts a script in.

So the Groovy reflex of `new`-ing up arbitrary Java classes doesn't carry over until the host has
registered the class:

```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```

```groovy
'AQIDBA=='.base64Decode()   // works only because the host registered it
```

(The sandbox *can* be disabled, or selectively weakened per class, for trusted scripts — but that's
opt-in, not the default.)

---

## 15. Translation Cheat Sheet

| Groovy                                              | Jactl                                                        |
|-----------------------------------------------------|-------------------------------------------------------------|
| `(0..<n).each { }`                                  | `n.each { }`                                                 |
| `list.findAll { }`                                  | `list.filter { }`                                            |
| `list.inject(0) { acc, x -> ... }`                  | `list.reduce(0) { acc, x -> ... }`                           |
| `list.every { }` / `list.any { }`                   | `list.allMatch { }` / `list.anyMatch { }`                    |
| `x as Set` / `new HashSet()`                        | no `Set` — use a `Map` with `true` values                    |
| `(str =~ /(\d+)/)[0][1]`                            | `str =~ /(\d+)/` then `$1`                                    |
| `str.replaceAll(/x/, 'y')`                          | `str =~ s/x/y/g`                                             |
| `try { risky() } catch (e) { ... }`                 | no equivalent — guard with `if`/`unless` or `die '...' if !ok` |
| `throw new RuntimeException('...')`                 | `die '...'`                                                  |
| `@CompileStatic` for real type checking             | just declare a type instead of `def` — always checked        |
| custom constructor `Foo(int x) { ... }`             | not allowed — use a `static` factory method                  |
| `private` / `protected` members                     | not supported — everything is public                         |
| mutable `static` field                              | not supported — use `const`, or an instance field            |
| `interface` / `implements` / `trait`               | not supported (first two are reserved-but-unimplemented keywords; `trait` isn't a keyword) |
| `List<String>` generics                             | not supported                                                |
| `metaClass`, Categories, `Expando`, operator overloading, `with`, spread | not supported                             |
| `new JsonSlurper().parseText(j)` / `JsonOutput.toJson(x)` | `j.fromJson()` / `x.toJson()`                          |
| `%` (remainder)                                     | `%%` for the same; plain `%` is true modulo                  |

---

## 16. Sharp Edges to Watch For

- **`%` is modulo.** Negative operands behave differently from Java/Groovy. Use `%%` for
  remainder semantics.
- **`collect` isn't `map`.** It forces eager evaluation of a pipeline stage; use `map` for the
  transform.
- **Chains are lazy.** Side effects in `map`/`filter` closures don't run until something consumes the
  result — reach for `collect`/`each` to force ordering.
- **`unique` is consecutive-only** (Unix `uniq`), not Groovy's global dedup. Sort first to dedup fully.
- **Collection methods on a `Map` return `[key,value]` pair lists**, not a `Map` — use `collectEntries`
  to rebuild one. (`reverse` on a `String` likewise returns a char list, not a string.)
- **`switch` matches numeric literals by exact type.** `1L` won't match pattern `1`.
- **A bare `/pattern/` is just a string**, not a match. In a boolean/implicit context add the `r`
  modifier (`/pattern/r`) to make it a match against `it` (`it =~ /pattern/`).
- **`//` is a comment, not an empty regex.**
- **No `try`/`catch`.** A runtime error you don't guard against ends the script.
- **`byte` is unsigned** (0–255).
- **No Java by default.** `new SomeJavaClass()` doesn't work unless the host application has explicitly permitted it.

---

## Further Reading

- [Language Guide](language-guide/introduction) — the full reference this guide draws
  from (types, operators, statements, classes, closures, switch, regex, collection methods, …).
- [Switch Expressions](language-guide/switch-expressions) and
  [Statements](language-guide/statements) — the complete destructuring/pattern spec (in `switch` and in `for`).
- [Collection Methods](language-guide/collection-methods) and
  [Built-in Methods](language-guide/builtin-methods) — the full method lists.
- [Integration Guide](integration-guide/introduction) — embedding Jactl and opting out of the sandbox.
- [Groovy vs Jactl](/blog/2026/03/16/groovy-vs-jactl) — a comparison of
  performance, security, and when to choose which.
