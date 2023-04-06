---
layout: page
title: About
permalink: /about/
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It was written to be integrated into Java applications to provide a secure locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its builtin suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

It comes with a [Read-Evaluate-Print-Loop (REPL) utility](https://github.com/jaccomoc/jactl-repl)
for testing out code interactively and can also be used for [command line scripts](command-line-scripts).

To learn how to integrate Jactl into your application see the [Integration Guide](integration-guide).

You can find the source code for Jactl at GitHub: [jactl](https://github.com/jaccomoc/jactl)

# Why?

I wrote Jactl because I wanted a scripting language that Java applications could embed and allow their users
to provide customisations and extensions that had the following characteristics:
* **Tightly Controlled**
 
  I wanted the application developer to be able to control what the users could and couldn't do in the scripting
  language.
  I didn't want to use an existing language where there was no way to prevent users from accessing files, networks,
  databases, etc. that they should be touching or spawning threads or other processes.
* **Familiar Syntax**

  I wanted a language that had a syntax similar to Java for ease of adoption.
* **Non Blocking**

  I wanted script writers to be able to perform blocking operations where the script needs to wait for something
  (such as invoking a function that accesses the database, or performs a remote request to another server) but which
  doesn't block the thread of execution.
  I wanted to have the script code suspend itself and be able to be resumed once the long-running operation completed.
  This allows the scripting language to be used in event-loop/reactive applications where you are never allowed to
  block the event-loop threads.

* **Hidden Asynchronous Behaviour**

  While not wanting scripts to block, I also did not want the script writers to have to be aware, when invoking a
  function, whether the function was asynchronous or not. 
  I wanted a language that looked completely synchronous but which, under the covers, took care of all the asynchronous
  behaviour. 

The final motivation for writing a new language compiler was that I was looking for something fun to work on and
at some point I had stumbled across a marvellous book called _Crafting Interpreters_ by Robert Nystrom
and this inspired me to want to write my own compiler.
I highly recommend the book and there is even a free web version available from the
[Crafting Interpreters site](https://craftinginterpreters.com/).

_James Crawford_ &nbsp; April 2023
