---
title: Built-in Global Functions
---

Jactl provides a number of built-in global functions.

## print args

The `print` function prints its arguments to the console.

## println args

The `println` function prints its arguments to the console, followed by a newline.

## sprintf(String format, Object... args)

The `sprintf` function formats a string according to a format specifier.

## nextLine()

The `nextLine` function reads a line of input from the console.

## stream(Function fn)

The `stream` function creates a stream of values by calling the supplied function/closure
repeatedly until the return value is `null`.

## timestamp()

The `timestamp` function returns the current value of time in milliseconds.
It is equivalent to Java's `System.currentTimeMillis()`.

## nanoTime()

The `nanoTime` function returns the current value of the system's high-resolution time source, in nanoseconds.

## sleep(long milliseconds)

The `sleep` function suspends the current thread for a specified number of milliseconds.

## uuid()

The `uuid` function returns a random UUID value.

## random(long bound)

The `random` function returns a random number between 0 and the upper (exclusive) bound specified.

## checkpoint()

The `checkpoint` function saves the current state of the script.
See [Checkpointing](checkpointing) for more details.

