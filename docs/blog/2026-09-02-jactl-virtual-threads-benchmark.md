---
title:      "Virtual Threads vs Vert.x Event-Loops"
date:       2026-09-02T18:33:00+01:00
authors:    [james]
tags:       [jvm, benchmarks]
description: "Comparing performance of Jactl code that blocks when using Virtual Threads as opposed to Vert.x event loops"
---

## Introduction

In the article about [Jactl Continuations and Virtual Threads in Java 8](/blog/2026/08/28/jactl-virtual-threads),
I discussed the continuation-based mechanism that Jactl uses to support blocking operations in Java versions
that don't support Virtual Threads.
The continuation mechanism provides support for suspending a script performing a long-running operation, and 
then resuming it once the operation completes, exactly where it left off, all without blocking
the event-loop thread.

As far as the script writer is concerned, they do not need to know whether an operation is a blocking one or not.
Jactl provides a Virtual Thread-like way for scripts to have perfectly natural code without needing to
be concerned about `async/await` or other async programming mechanisms like `Futures`, `Promises`, or
callbacks.

The article also provided a benchmark showing the performance impact of suspending and resuming scripts when
running on [Vert.x](https://vertx.io) event-loop threads.
Jactl provides a way to disable this mechanism for applications running on more modern versions of Java and
wanting to take advantage of the built-in Virtual Threads in Java.
I wanted to compare the performance of the two approaches.

This article showcases a new benchmark that compares the Jactl/Vert.x approach with Jactl/Virtual Threads
and presents the results from running on both Java 21 and Java 25. 

<!-- truncate -->

## The Benchmark

The [benchmark](https://github.com/jaccomoc/jactl-vertx/blob/main/src/jmh/java/io/jactl/vertx/benchmark/VirtualThreadBenchmark.java)
is based on a Jactl script that was previously benchmarked in the [article](/blog/2026/08/28/jactl-virtual-threads) about Jactl Continuations.
The script is configured to do a `sleep(10)` from within a nested function a configurable number of times.
Different tests within the benchmark run the script with 0, 1, 2, 5, and 10 invocations of `sleep(10)`
so we can compare the impact of multiple blocking operations within the one script.

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
  sleep(10) if slept++ < sleepCount
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


What I wanted to test was the throughput differences between Jactl with Virtual Threads and Jactl suspend/resume with
Vert.x in terms of how many events per second could be processed.
Each benchmark creates 100,000 events that are then scheduled and executed using a `Executor.newVirtualThreadPerTaskExecutor()` executor for the Virtual Thread tests or the Vert.x event-loop scheduler
for the Vert.x based tests.
Each event runs the script and the tests then wait for all events to complete before returning so we can measure the
total execution time.

In these tests, the `sleep(10)` is intended to represent a blocking operation such as a database query, or
a web services call.
I picked 10ms as a representative duration for these types of operations.

For the Jactl Virtual Thread tests, the Jactl script is compiled with [`async(false)`](/docs/integration-guide/jactl-context#asyncboolean-enabled)
so that the calls to `sleep()` block and then rely on the virtual thread mechanism to suspend the thread
until the operation completes.

For the Jactl Vert.x tests, the `sleep()` call will create a `Continuation` by throwing an exception and capturing
the execution state so that it can be resumed once the sleep completes.
When the script suspends, the script returns and the event-loop thread is then able to process the next
event in its queue.

## Benchmark Execution

The tests all use the [JMH](https://github.com/openjdk/jmh) library which warms up the JVM and does multiple
iterations of each test before calculating the average.

The tests were run with the following versions:

| Component | Version |
|-----------|---------|
| Jactl     | 2.9.2   |
| Vert.x    | 4.5.33  |
| Java      | 21.0.11 |
| Java      | 25.0.2  |

The tests run across multiple threads since they test throughput of multi-thread scheduling and execution.
They were run on a MacBook Pro M5 Max which has 18 cores.

## Results

### Java 21

Here are the results of the benchmarks showing the number of script executions per second when run with Java 21:

<img src="/charts/vertx-vs-virtual-threads-throughput-java21.svg" alt="Throughput comparison of Vert.x event-loops vs Virtual Threads as the number of sleep(10) calls per script increases" />

When there are no calls to `sleep()` we are effectively just comparing the scheduling efficiency of Vert.x with
that of the virtual threads executor and virtual threads achieves a throughput around 15% more than the Vert.x
version.

With the introduction of a single `sleep()` call, the results reverse, and the Jactl suspend/resume with Vert.x
combination comes out ahead.

The more calls to `sleep()` there are, the more pronounced the advantage of the Jactl/Vert.x version is over the
virtual threads version.
By the time there are 10 calls to `sleep()`, the Jactl/Vert.x version has 2.5x the throughput.

### Java 25

When the benchmark is run on a more recent Java 25 version of Java we get these results:

<img src="/charts/vertx-vs-virtual-threads-throughput-java25.svg" alt="Throughput comparison of Vert.x event-loops vs Virtual Threads as the number of sleep(10) calls per script increases" />

Again, with no `sleep()` calls, the virtual threads version wins, but this time, the Jactl/VirtualThreads combination
has higher throughput for all the benchmarks.
As the number of `sleep()` calls increase, the gap narrows, but the Jactl/Vert.x version never overtakes the
Jactl/VirualThreads combination.

## Conclusion

From the results we can see that the Jactl suspend/resume mechanism in combination with Vert.x performs very well.

If you are running on a Java version earlier than Java 21 you will obviously not have the option of using virtual
threads, but you can be confident in the performance of the Jactl/Vert.x implementation.

If you are running on Java 21 and you already have a Vert.x based application and are using Jactl scripts that
perform blocking operations, then the Jactl/Vert.x thread version will give superior performance so there is no
performance benefit in rearchitecting your application to use virtual threads.

Since Java 21, however, significant improvements to the virtual thread performance have been made.
If you can use Java 25, then using virtual threads will give you better throughput, but the Jactl/Vert.x 
implementation is still a well performing option.

At the end of the day, however, the choice to use virtual threads is more about the Java application and how it
is architected.
Jactl scripts don't change whether the Java application is a reactive event-loop based application,
or whether it is using virtual threads.
The choice about whether to use virtual threads should be more about the advantages of being able to write
Java code without having to deal with futures and callbacks.
