---
layout: page
title: About
permalink: /about/
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It was written to be integrated into Java applications to provide a secure locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its built-in suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

It also allows the execution state of scripts to be checkpointed and persisted or distributed over a network
to allow scripts to be recovered and resumed after a failure.

# Why?

I wrote Jactl because I wanted a scripting language that Java applications could embed to allow their users
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

# Who is it for?

It is intended to be used in any Java based application where the application developer wants to provide customisation
capabilities to their users.

It could just be used as a mechanism for reading in a configuration file.
For example, the Jactl commandline scripts and REPL assume a `.jactlrc` [configuration file](/command-line-scripts#jactlrc-file)
which contains Jactl code that is read and executed at startup to set the values of some properties in a Map.

Other uses would be to provide a customisation mechanism for:
* **Database engine extensions**
* **Real-time applications**
* **Backend customisations for complex multi-tenant web applications**
* **FaaS (Function as a Service)**

  Scripts could act as functions and their secure nature means that many functions can be served from the same process
  to avoid having to spin up instancesor processes for each function

# Getting Started

You can download the Jactl library and find the source code for Jactl at GitHub: [jactl](https://github.com/jaccomoc/jactl)

The to start playing wth Jactl you can look at running some simple scripts using the [command line scripts](/command-line-scripts)
facility.

For testing out code interactively, you can also use the [Read-Evaluate-Print-Loop (REPL) utility](https://github.com/jaccomoc/jactl-repl).

To learn how to integrate Jactl into your application see the [Integration Guide](/integration-guide).

To integrate Jactl into a [Vert.x](https://vertx.io) based application have a look at the
[jactl-vertx library](https://github.com/jaccomoc/jactl-vertx).

To learn more about the language itself read the [Language Guide](/language-guide).

<br/>

_James Crawford_ &nbsp; April 2023
