---
title: Security and Sandboxing
description: "How Jactl sandboxes untrusted scripts, how to selectively grant access to host classes, and the security implications of registering custom functions, methods, and built-in types."
---

> This section was co-authored with [Claude](https://claude.ai).

Jactl is designed from the ground up to run untrusted, user-supplied scripts safely inside a host Java
application.
A common reason for embedding a scripting language is to let end users (or tenants, or rule authors)
customise or extend an application without giving them the keys to the JVM it runs in.

This page describes the security model, what the default sandbox does and does not allow, how to selectively
open up access to specific host classes, how to bound the resources a script can consume, and the security
implications of extending Jactl with your own functions, methods, and built-in types.

## Threat Model

The core assumption is that the **script author may be hostile** but the **host application is trusted**.
The goal of the sandbox is to ensure that a script can only interact with the application through the
surface area that the application has *deliberately* chosen to expose, and nothing more.

A well-behaved sandbox for untrusted scripts needs to prevent a script from:

* reading or writing arbitrary files,
* opening network connections,
* starting processes or calling `System.exit()`,
* reflecting on or invoking arbitrary Java classes,
* reaching into the internals of the host application, and
* consuming unbounded CPU, memory, or wall-clock time.

Jactl addresses the first five with a "deny by default" language design, and the last one with configurable
resource limits (see [Limiting Resource Usage](#limiting-resource-usage)).

:::note
As always, a language-level sandbox is one layer of defence, not a complete one.
For hostile-multi-tenant workloads it should be combined with the usual JVM and OS controls
(a dedicated `ClassLoader`/module boundary, an OS-level sandbox or container, memory/CPU cgroups,
and so on).
Jactl removes the language as an attack vector; it does not replace operating-system isolation.
:::

## The Default Sandbox

Out of the box &mdash; with a plain `JactlContext.create().build()` &mdash; a Jactl script has a deliberately
tiny window onto the outside world.

A script can:

* perform computation using the built-in types (numbers, strings, lists, maps, etc.),
* define and use its own functions, closures, and classes,
* call the [built-in functions and methods](../language-guide/builtin-methods) that ship with Jactl
  (string manipulation, collection operations, JSON encoding/decoding, and so on),
* read from the `Reader` and write to the `Writer` that the host supplies when it runs the script, and
* read and update the global variables (bindings) that the host explicitly passes in.

A script **cannot**, by default:

* open files, sockets, or start processes &mdash; the language has no syntax for this and the standard
  built-in functions provide no file, network, or process primitives,
* call `System.exit()`, read system properties, or otherwise touch the JVM,
* name, load, instantiate, or invoke methods on arbitrary Java/host classes, or
* reach any application object that was not handed to it as a global variable or via a registered function.

The key structural point is that **the base language has no I/O primitives at all**.
All interaction with the outside world happens through three well-defined channels, each fully controlled by
the host:

1. the **globals** map passed in at compile/run time,
2. the input `Reader` / output `Writer` supplied when running the script, and
3. the **functions, methods, and built-in types the host has registered** (see
   [Security of Custom Extensions](#security-of-custom-extensions)).

If the host registers nothing extra, provides no host-class access, and passes only plain data as globals,
then there is no code path by which a script can affect anything outside its own computation and the data it
was given.

## The JactlContext as a Security Boundary

Every script and class is compiled with a `JactlContext`, and that context *is* the sandbox boundary.
Jactl classes compiled with one `JactlContext` can only be referenced by scripts and classes compiled with
the **same** `JactlContext`.
Nothing compiled in one context is visible to another.

This makes the `JactlContext` a natural unit of isolation for multi-tenant applications: give each tenant
(or trust level, or customer) its own `JactlContext` and their scripts and classes cannot see or interfere
with each other's.

A `JactlContext` can additionally be given its own private set of registered functions/methods and its own
private set of built-in types, so different tenants can be offered different capabilities from the same
application (see [Isolating Extensions per Context](#isolating-extensions-per-context)).

See [JactlContext Object](jactl-context) for the full list of options.

## Controlling Host Class Access

By default a script cannot call methods on, or otherwise use, objects whose class is a host (application or
Java) class.
If a script somehow gets hold of such an object (for example via a global variable) and tries to invoke a
method on it, Jactl raises a runtime error rather than dispatching the call.

For some integrations, though, you *want* scripts to be able to call into specific, trusted application
classes &mdash; for example to expose an application API object.
This is controlled by two `JactlContext` options that work together:

### allowHostAccess(boolean)

This is the master switch.
Unless `allowHostAccess(true)` is set, **all** access to host classes is denied and the other option below has
no effect.

### allowHostClassLookup(Predicate&lt;String&gt;)

When host access is enabled, this predicate decides *which* host classes are permitted.
It is given a fully-qualified Java class name (e.g. `com.acme.Application`) and returns `true` if that class
is allowed and `false` otherwise.

```java
JactlContext context = JactlContext.create()
                                   .allowHostAccess(true)
                                   .allowHostClassLookup(name -> name.equals("com.acme.Application"))
                                   .build();
```

The predicate acts as an allow-list.
The safe pattern is to enumerate exactly the classes you intend to expose and deny everything else &mdash; the
default predicate denies all classes (`s -> false`), so you are opting *in*, class by class.

:::warning
Be as specific as possible.
A predicate like `name -> name.startsWith("com.acme.")` may look convenient but will expose *every* class in
that package hierarchy, including ones you did not intend to and ones added in future.
Prefer an explicit set of allowed class names.
:::

#### Both the object's class and the declaring class are checked

When a script invokes a method on a host object, Jactl consults the predicate **twice**, and both checks must
pass for the call to be allowed:

1. the runtime class of the object the method is being invoked on, and
2. the class that actually *declares* the method being invoked (which, because of inheritance, may be a base
   class of the object's class rather than the object's class itself).

This prevents a subtle escape: allowing a subclass does not implicitly grant access to methods it inherits
from a base class you did not allow.
If a permitted class inherits behaviour from an un-permitted base class, calls that resolve to the base
class's methods are still rejected.

Note that inner classes are named using Java's binary syntax, e.g. `com.acme.SomeClass$InnerClass`, when
passed to the predicate.

### allowHostClassLookup(boolean)

There is also a convenience overload that takes a boolean.
Passing `true` allows access to **all** host classes.

```java
JactlContext context = JactlContext.create()
                                   .allowHostAccess(true)
                                   .allowHostClassLookup(true)   // allow EVERYTHING
                                   .build();
```

:::warning
`allowHostClassLookup(true)` disables the class allow-list entirely.
A script would then be able to reach arbitrary classes on the classpath.
This effectively removes the sandbox and must only ever be used for scripts you completely trust.
:::

## Limiting Resource Usage

Blocking access to files, the network, and host classes stops a script from reaching *outside* the JVM, but a
script can still misbehave by consuming resources &mdash; for example with an infinite loop or a runaway
recursion.
`JactlContext` provides two limits to bound this.

### maxExecutionTime(int limitMs)

Sets a ceiling, in milliseconds, on how long a single script invocation may run.
Jactl checks the elapsed time periodically during execution and throws a
`io.jactl.runtime.TimeoutError` if the limit is exceeded.

```java
JactlContext context = JactlContext.create()
                                   .maxExecutionTime(5000)   // 5 seconds per invocation
                                   .build();
```

### maxLoopIterations(long limit)

Sets a ceiling on the total number of loop iterations a single script invocation may perform, guarding against
tight loops that would otherwise spin.
Exceeding the limit also throws a `TimeoutError`.

```java
JactlContext context = JactlContext.create()
                                   .maxLoopIterations(10_000_000L)
                                   .build();
```

Both limits default to `-1`, meaning *no limit*.
For untrusted scripts you should almost always set at least one of them.

:::note
These limits bound CPU/time and loop-driven work; they do not bound heap allocation.
A script can still attempt to allocate large data structures, so if memory exhaustion by a hostile script is
part of your threat model, combine these limits with JVM/OS-level memory controls.
`TimeoutError` is a subclass of `RuntimeError`, so it can be caught in the usual way.
:::

## Restricting Language Features

`JactlContext` can also disable individual language features that an application may not want untrusted scripts
to use.
Each of these produces a **compile-time** error if the feature is used, so a disallowed script never even
gets to run.

* **`disableEval(boolean)`** &mdash; disables the `eval()` statement, which compiles and runs Jactl source
  from a string at runtime. Disabling it removes a layer of dynamic compilation that can make scripts harder
  to reason about and review.
* **`disablePrint(boolean)`** &mdash; disables the `print` and `println` statements.
* **`disableDie(boolean)`** &mdash; disables the `die` statement.

```java
JactlContext context = JactlContext.create()
                                   .disableEval(true)
                                   .disablePrint(true)
                                   .build();
```

See [JactlContext Object](jactl-context) for the full details of these options.

## Security of Custom Extensions

The whole point of embedding Jactl is usually to give scripts *some* additional capabilities beyond pure
computation.
Jactl lets you do this by registering:

* **global functions** and **methods** &mdash; via `Jactl.function()` and `Jactl.method()`
  (see [Adding New Functions and Methods](adding-new-functions)), and
* **new built-in types** &mdash; via `Jactl.createClass()`
  (see [Adding New Built-In Types](adding-new-builtins)).

Everything you register becomes part of the **trusted computing base** of the sandbox.
It is your Java code, running with the full privileges of the host JVM, callable by any script that has access
to it.
The sandbox constrains what the *language* can do; it does **not** constrain what your registered Java code
does once a script calls it.

This has some important consequences.

### Registered code runs with full JVM privileges

If you register a function that reads a file, opens a socket, executes a query, or invokes an application API,
then **every script that can call that function can do that thing**.
The function is the capability.
Deciding what to register is therefore a security decision, not just a convenience one &mdash; you are defining
the exact set of powers your scripts have.

Register the minimum needed.
A narrow, purpose-built function (`lookupCustomer(id)`) is far safer to expose than a general-purpose one
(`runSql(query)`) that lets the script decide what to do.

### Validate and constrain arguments

Because a hostile script controls the arguments it passes, a registered function must treat all of its inputs
as untrusted.
If a function takes a path, a URL, a query, or an identifier, validate and constrain it inside the function
&mdash; do not assume the script will only pass "reasonable" values.
Classic pitfalls apply: path traversal, SQL/command injection, server-side request forgery, and so on.

### Don't leak powerful objects

A method registered on, or a value returned from, a custom function can hand a script a reference to a Java
object.
If that object exposes powerful methods, and host-class access is enabled such that those methods can be
invoked, you may have widened the sandbox further than intended.
Prefer returning plain data (numbers, strings, lists, maps) or your own narrowly-scoped types rather than
returning general-purpose framework or JDK objects.

## Isolating Extensions per Context

By default all `JactlContext` objects share the same globally-registered set of functions/methods and the same
set of built-in types.
When you are running scripts at different trust levels, you often want different trust levels to have different
capabilities.

Two `JactlContext` options let a context carry its *own* private extensions rather than sharing the global set:

* **`hasOwnFunctions(true)`** &mdash; the context gets its own set of registered functions/methods, so you can
  register a different set for it than for other contexts.
* **`hasOwnBuiltIns(true)`** &mdash; the context gets its own set of registered built-in types.

```java
// A restricted context that only shares whatever was globally registered,
// plus its own extra, tenant-specific function set.
JactlContext tenantContext = JactlContext.create()
                                         .hasOwnFunctions(true)
                                         .hasOwnBuiltIns(true)
                                         .maxExecutionTime(2000)
                                         .maxLoopIterations(1_000_000L)
                                         .build();
```

This lets you build tiers &mdash; for example a "trusted" context with a rich API and generous limits, and an
"untrusted" context with a minimal API and strict resource limits &mdash; from the same application.

## Checklist for Running Untrusted Scripts

When you are running scripts you do not fully trust, a good baseline is:

* **Leave host-class access off.** Do not call `allowHostAccess(true)` unless you genuinely need it, and when
  you do, use an explicit, minimal `allowHostClassLookup` allow-list &mdash; never `allowHostClassLookup(true)`.
* **Set resource limits.** Configure `maxExecutionTime()` and/or `maxLoopIterations()` so a script cannot run
  or loop forever.
* **Register the minimum API.** Expose only the narrow, purpose-built functions/methods and built-in types
  scripts actually need, and treat every argument they receive as hostile.
* **Return data, not power.** Have custom functions return plain data or narrowly-scoped types rather than
  general-purpose JDK/framework objects.
* **Isolate by context.** Give different trust levels different `JactlContext` objects, using
  `hasOwnFunctions`/`hasOwnBuiltIns` to give each the capabilities appropriate to it.
* **Consider disabling features.** Use `disableEval()` (and `disablePrint()`/`disableDie()` where relevant) to
  reduce the language surface exposed to untrusted authors.
* **Pass only what's needed as globals.** Globals are a direct channel into the script; pass plain data and
  avoid handing scripts references to powerful application objects.
