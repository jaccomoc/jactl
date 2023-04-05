---
layout: page
title: About
permalink: /about/
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It can be run from a REPL and from the commandline for commandline scripts but its main goal is
to be integrated into Java applications where it provides a secure, locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its builtin suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

You can find the source code for Jactl at GitHub:
[jactl](https://github.com/jaccomoc/jactl)
