---
title:      "Jactl Continuations and Virtual Threads in Java 8"
date:       2026-08-28T11:27:00+01:00
authors:    [james]
tags:       [jvm, design, benchmarks]
description: "Design of Jactl's continuation based mechanism for simulating Virtual Threads along with some benchmarks showing their performance impact."
---

## Introduction

Jactl is a secure, embeddable scripting language for Java applications.
When I first started developing Jactl, I wanted a scripting language that compiled to bytecode for 
optimum performance, was secure so applications could control exactly what scripts could
and couldn't do, and most of all, did not block the execution thread when long-running, blocking operations
were performed.

<!-- truncate -->

When I began developing Jactl, Java 21 and Virtual Threads did not exist, and event-driven,
reactive applications (such as ones based on [Vert.x](https://vertx.io/)) were the way in which
high-throughput Java applications were written.
I also had a need for a scripting language that would work on applications still stuck on Java 8
or Java 11.

:::note
Now, with the later versions of Java, Java applications that want to use
Virtual Threads rather than adopting an event-based architecture can choose to disable
the built-in Jactl async mechanism described here by configuring
the [JactlContext.async(false)](/docs/integration-guide/jactl-context#asyncboolean-enabled) flag.
:::

A reactive application is one where a pool of event-loop threads process events from a queue.
The golden rule is that events should never block as this will pause one of the event-loop threads,
preventing it from processing any more events until that blocking operation completes.
A blocking operation is one where the thread is no longer actively processing code but is waiting
for the result of an operation such as a database request or a remote procedure call.
If blocking operations can occur on an event-loop thread, you will eventually see situations where
all threads are waiting for long-running operations and no events are being processed.

I wanted a scripting language that could be invoked from an event-loop thread but, when it
performed any blocking operation, it would somehow save its state and return, freeing up the
thread to process further events.
When the result of the long-running operation was available the script would be resumed from the
point where it left off and continue with its processing.
In Java 21 and later, Virtual Threads provide the same functionality - they preserve the call stack
with all the local variables and allow the thread to continue performing other work and when the
blocking operation is complete the call stack is restored and the program continues from where it
left off.

The goal was only to save the execution state of the Jactl code, not the state of the Java
code that was invoking a Jactl script.
Since the Java application is event-based, the script will complete as a new event on an
event-loop thread and a completion callback will be invoked once the script
finishes that calls back into the Java application with the script result.
The callback provided by the application can hold onto any state that the application needs.

## Continuations

In Java 8, of course, there is no way to preserve the call stack, either in Java or in JVM
bytecode, so I had to use a different mechanism to achieve the same end.

Imagine that we have a script that needs to invoke a function (or method) that performs a
long-running operation.
For the sake of the example, let's assume that the function needs to perform `sleep()` for some
period of time before it does something else.
There will be a Java call stack with a stack frame for each nested method call and then the rest
of the stack will be Jactl stack frames, one for each nested Jactl function call, with
the topmost stack frame being the stack frame for the `sleep()` function itself.
Each stack frame tracks where in the code the function invocation occurs, along with the
values for its local variables:

![Call stack at the moment sleep() suspends the script](/img/callstack.svg)

To capture the execution state, I figured that the easiest thing to do would be to throw an exception at
the start of a long-running operation like `sleep()` and generate code in each Jactl 
method/function that catches the exception, saves its state, and throws a new exception that is 
chained to the one it just caught.
I called the class for the exception being thrown `Continuation` since a continuation
is a representation of the execution state of a program.

The implementation of the `sleep()` function will then look something like this:
```java
public static Object sleep(long timeMs) {
  Continuation continuation = new Continuation();
  scheduleEvent(timeMs, () -> continuation.continueExecution());
  throw continuation;
}
```

As this exception unwinds the call stack, the generated code for each Jactl stack frame catches
it, creates its own `Continuation` to record where it was up to (the location of the call it was
waiting on) and the values of its local variables, and then throws a new `Continuation` chained
to the one it just caught.
By the time the exception reaches the bottom of the Jactl call stack, the original call stack has been
replaced by a chain of `Continuation` objects, one per frame, that together capture the entire
execution state of the script:

![The Jactl call stack unwinds into a chain of Continuation objects](/img/continuation-chain.svg)

:::note
For anyone who has done performance tuning of Java applications, the idea of throwing exceptions
instantly makes one think of the cost involved, but in reality, the cost of throwing an exception is
mostly in the generation of the stack trace that goes along with it.
As long as you throw an exception that does not fill in the stack trace, it is actually very
efficient.
:::

## Async Functions

The Jactl compiler knows which global functions are functions that can perform long-running
operations and can throw a `Continuation` object.
These functions are called _async_ functions and the Jactl compiler tracks which methods and
functions invoke these async functions and marks these as _async_ as well.
This continues up the call chain so the compiler knows at any point in time whether a call
could potentially throw a `Continuation` object, even if the built-in
global function that is the one to actually throw the first `Continuation` is buried many
levels within a set of nested calls.

## Invoking Async Methods/Functions

When the compiler is generating code that invokes a function that has been flagged as _async_,
it wraps the call in a `try/catch` that catches any `Continuation` that is thrown.

The code for the `catch` block creates a new `Continuation` object and stores a 
`MethodHandle` and a _location_ inside it.
The `MethodHandle` points to the current function and the location is a logical location
that records where in the current function the call to the async function
that threw the `Continuation` occurred.

As well as the `MethodHandle` and location, the compiler generates code to also store the values of
the local variables in scope at the time and any values that are currently on the local stack.
There are two separate arrays used: a `long[]` that is used for local variables and stack values that are
primitives, and an `Object[]` that is used for all other types.

Every async function is implicitly passed a `Continuation` object as its first argument.
The first time through, the argument is null, but if the function was suspended due to
a long-running operation and is then later resumed, it will be reinvoked with the 
`Continuation` it originally threw when it was suspended.
The generated code checks if the continuation argument is non-null and if so, uses the
location within the `Continuation object to work out where in the function to jump to in
order to continue execution.

Here is some pseudocode that shows what the generated code from the compiler for a function
that invokes another async function might look like:
```java
static MethodHandle processOrderHandle = MethodHandles.lookup().findStatic("processOrder");

Object processOrder(Continuation cont, ...) {
  Order  order;
  Widget widget;
  int    count;
  if (cont != null) {
    // Resume from where we left off after restoring any local variables
    switch (cont.location) {
      case 0:
        // Restore locals
        order  = cont.objArr[0];
        widget = cont.objArr[1];
        count  = cont.longArr[0];
        goto LOCATION_0;
      case 1:
        ...
        goto LOCATION_1;
    }
  }
  // Code for the function
  ...
  try {
    checkInventory(widget, count);
  }
  catch (Continuation c) {
    throw new Continuation(c, processOrderHandle, 
                           0,   // the location
                           new long[]{ count },
                           new Object[]{ order, widget });
  }
 LOCATION_0:
  ...
}
```
Note that the `Continuation` constructor chains itself to the just caught `Continuation`
so that the chain starts with the `Continuation` from the top of the stack.

## Resuming Execution

Once a long-running operation completes, the initial `Continuation` object in the chain is
resumed by invoking its `continueExecution(Object result)` method.
This method extracts the `MethodHandle` and calls it, passing in the
`Continuation` as previously described so that the function can restore any local variables
and work out where to continue from.

Since the call stack no longer matches the original call stack, 
when the function returns, instead of it returning to the original parent function,
it will return to the `Continuation.continueExecution()` method which then extracts the
next `Continuation` object in the chain and calls its `MethodHandle`.
This continues until there are no more `Continuation` objects in the chain and the
registered completion from the application is invoked with the final result.

## Invoking a Subsequent Async Function

While walking the chain of `Continuation` objects and resuming them, another async function may
be invoked that throws a new `Continuation` object for a new long-running operation, possibly
from a function many nested calls further in.
When this happens, we take the new chain of continuations and add the remainder of the existing
chain to the end of that chain:

![While resuming the original chain, checkInventory() suspends again and the remainder of the old chain is appended to the tail of the new one](/img/continuation-splice.svg)

When the new long-running operation completes and its `Continuation` chain is resumed, the chain now consists
of all the new continuations as well as the remaining old ones.
It will first resume each of the new continuations and then continue on to the remaining ones in the old chain.

## Checkpointing Execution State

Once Jactl had the ability to save the current execution state in a chain of continuations, I realised that if
these continuations could be serialised into a byte array, I could use this as a way to checkpoint the state
of a script.
Jactl offers a `checkpoint()` function that allows a script to checkpoint its state it
important steps during its processing.
Once a script state has been checkpointed, the state can be persisted to disk, or into a database, or replicated
across a network to another application instance.
Once the state has been persisted or replicated, it can be resumed at any point in time
if the original application host fails, for example.

For every built-in type and every user defined class, Jactl generates code to store instances of these 
types into a byte array, along with other types used internally by the Jactl runtime.
Jactl then provides hooks that the application can use to persist or replicate these script states as part
of a redundancy solution for application state.
There is a corresponding Jactl mechanism that the application can use to resume the state when needed.
See [Checkpointing Proof of Concept](2023-11-10-checkpoint-poc.md) for more details and a description
of a proof-of-concept implementation of checkpointing for application redundancy.

## Benchmarks

I created a benchmark using the [JMH](https://github.com/openjdk/jmh) library to show how the continuation mechanism impacts peformance.
The [SuspendResumeBenchmark](https://github.com/jaccomoc/jactl-vertx/blob/main/src/jmh/java/io/jactl/vertx/benchmark/SuspendResumeBenchmark.java)
uses [Vert.x](https://vertx.io/) for the event scheduling and execution and
benchmarks a Jactl script that processes batches of 200 orders.

<details>
<summary>The script</summary>

```groovy
var totals    = [:]
var itemCount = 0
var grandTotal = 0.0
var topCategory = ''
var topAmount = -1.0
var slept = 0

def checkInventory(widget, count) {
  sleep(0) if slept++ < sleepCount
  return true
}

def processOrder(order) {
  var price    = order.price
  var qty      = order.quantity
  var category = order.category

  return unless checkInventory(order.category, order.quantity)

  var discount = 0.0
  if      (qty >= 100) { discount = 0.20 }
  else if (qty >=  50) { discount = 0.10 }
  else if (qty >=  20) { discount = 0.05 }

  var lineTotal = price * qty * (1.0 - discount)

  if (totals[category] == null) {
    totals[category] = 0.0
  }
  totals[category] = totals[category] + lineTotal
  grandTotal       = grandTotal + lineTotal
  itemCount        = itemCount + 1

  if (totals[category] > topAmount) {
    topAmount   = totals[category]
    topCategory = category
  }
}

for (order in orders) {
  processOrder(order)
}

'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory
```                                                                                                                                   

</details>

For each batch, `processOrder()` is called for each order in the batch which then
invokes `checkInventory()` (which always returns true) for each item in the order.
The `checkInventory()` function invokes `sleep(0)` the first `n` times it is invoked
so we can then measure the overhead of suspending and resuming a script from inside
a nested call stack.
The call to `sleep(0)` will suspend the script by throwing a `Continuation` as described but,
since the sleep time is 0, it will then immediately schedule a resume event to continue
the script execution.

The benchmark measures performance for when the script performs 0, 1, 2, 5, and 10 calls to `sleep(0)`.
Here are the results:

<img src="/charts/suspend_resume_chart.svg" alt="Throughput vs number of suspend/resume operations" />

As the chart shows, in this benchmark, he impact of each suspend/resume is quite small.
In a real world scenario, the relative impact will be based on how much work the script is doing, how
nested the stack is, and the number of local variables (including parameters) there are at each level
of the stack.
Note that the overhead measured also includes the Vert.x scheduler overhead involved in scheduling and
executing both the scripts and the resume events.

## Conclusion

For reactive applications that need to run on older versions of Java, the Jactl continuation based mechanism for
handling blocking operations provides a convenient and efficient way for applications to offer customisation via scripting,
without needing to be concerned about scripts blocking event-loop threads.
Scripts can be written with inlined blocking operations in a natural manner - there is no need
to pollute the code with `async/await` or deal with `Futures` or `Promises` or other
mechanisms that programming languages have used to deal with asynchronous behaviour in the
past.
From a script point of view, Jactl provides the equivalent programming model as Virtual Threads in Java 21 provide
to Java programs.

With modern versions of Java, the Jactl continuation based approach can be disabled and Jactl can take advantage
of Virtual Threads to provide support for blocking operations that don't block the carrier thread.

## Postscript

I later became aware of other libraries that use a similar mechanism to implement continuations for
arbitrary Java code (see for example [Apache Javaflow](https://commons.apache.org/sandbox/commons-javaflow/) and 
[Java Continuations Library](https://github.com/oltolm/continuations) - no longer maintained).
They rely on bytecode instrumentation to insert the appropriate instructions into the codebase.
I haven't looked at the implementation of these to know how closely they match the Jactl approach, but they
appear to use the same idea of throwing exceptions and then catching them at each stack frame to record local state.
