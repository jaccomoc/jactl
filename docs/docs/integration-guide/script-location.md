---
title: Location of Scripts and Classes
---

In the examples we have shown we have always used constant strings as the source code for our Jactl scripts and classes
but this is unlikely to be the case in a proper application.
In general, the scripts and classes will be read from files or from the database since the whole idea using Jactl to
is to that Jactl scripts provide a way to customise the behaviour of an application.

See [Example Application](https://github.com/jaccomoc/jactl-vertx#example-application) which gives an example
application that reads Jactl scripts on demand from the file system in order to respond to incoming web service requests.

