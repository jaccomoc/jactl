---
title: Jactl Execution Environment
---

In order for Jactl to support event-loop based applications with non-blocking execution of scripts, it needs
to know how to schedule blocking and non-blocking operations.

An implementation of the `io.jactl.JactlEnv` interface must be provided that supplies a bridge from Jactl to
the execution environment in which it is running.

The interface has the following methods that need to be implemented:
```java
public interface JactlEnv {
  Object getThreadContext();
  void scheduleEvent(Object threadContext, Runnable event);
  void scheduleEvent(Object threadContext, Runnable event, long timeMs);
  void scheduleEvent(Runnable event, long timeMs);
  void scheduleBlocking(Runnable blocking);
}
```

There are also two other optional methods with default implementations (`saveCheckpoint()` and `deleteCheckpoint()`)
that will be described in the section on [Checkpoints](checkpoints).

## getThreadContext()

In order to support event-loop based applications where resumption of a long-running operation should occur on the
original thread that spawned the long-running task, there is a method called `getThreadContext()` that should return
a handle that identifies the current event-loop thread, and which, when passed to `scheduleEvent()` will allow
the new event to be rescheduled on the same original event-loop thread.

If there is no such concept of rescheduling on the same thread then `getThreadContext()` is allowed to return `null`.
If `getThreadContext()` is called from a non-event-loop thread then it should return `null` unless it makes
sense to return a context for a specific event-loop thread (for example if there is some way for it to know
that the currently running code is somehow related to a previous event on a specific event-loop thread).

## scheduleEvent()

The `scheduleEvent()` methods are used to schedule non-blocking events onto an event-loop thread.

If passed a thread context that was returned from `getThreadContext()` then the event should be scheduled onto the
same event-loop thread.
If not passed a thread context then it is free to pick an event-loop to schedule the event on.

If passed a `timeMs` value then the event should be scheduled to run after the specified amount of time has expired.

## scheduleBlocking()

This method is used to add execution of some blocking code to a queue for execution by a blocking thread once
one becomes available.

## Example Implementation

As an example of how to write a `JactlEnv` class have a look at the
[`io.jactl.vertx.JactlVertxEnv`](https://github.com/jaccomoc/jactl-vertx/blob/main/src/main/java/io/jactl/vertx/JactlVertxEnv.java)
class provided in the [`jactl-vertx`](https://github.com/jaccomoc/jactl-vertx) project or see the default
[`io.jactl.DefaultEnv`](https://github.com/jaccomoc/jactl/blob/master/src/main/java/io/jactl/DefaultEnv.java) implementation.

## .jactlrc File

If your environment class has a no-arg constructor, and you want to be able to run your extension functions and methods
(see below) in the [Jactl REPL](https://github.com/jaccomoc/jactl-repl) or in [command-line](../command-line-scripts) scripts,
then you can configure your `.jactlrc` file with the name of your environment class.

See [`.jactlrc` File](../command-line-scripts#jactlrc-file) for more details.

