---
# Feel free to add content and custom Front Matter to this file.
# To modify the layout, see https://jekyllrb.com/docs/themes/#overriding-theme-defaults

layout: home
---

Jactl is a powerful scripting language for the JVM platform whose syntax is a combination of bits
from Java, Groovy, and Perl.
It can be run from a REPL and from the commandline for commandline scripts but its main goal is
to be integrated into Java applications where it provides a secure, locked-down, way for
customers/users to be able to customise the application behaviour.

It is especially suited to event-loop/reactive applications due to its builtin suspend/resume
mechanism based on continuations that ensures it never blocks the execution thread on which it is
running.

## Some Useful Links

See the [Jactl REPL project](https://github.com/jaccomoc/jactl-repl) for how to download and run the Jactl REPL.

The [Jactl Language Guide](https://jaccomoc.github.io/jactl/language-guide) describes the Jactl Language.

The [Jactl Commandline Scripts Guide](https://jaccomoc.github.io/jactl/command-line-scripts) describes how to run Jactl scripts at the commandline.

The [Jactl Integration Guide](https://jaccomoc.github.io/jactl/integration-guide) describes how to integrate Jactl into an application and how
to provide additional functions and methods to extend the language.

The [Jactl-Vertx library](https://github.com/jaccomoc/jactl-vertx) provides some basic Vert.x integration capabilities
as well as JSON functions, a function for sending a web request, and an example application.

You can find the source code for Jactl at GitHub: [jactl](https://github.com/jaccomoc/jactl)
