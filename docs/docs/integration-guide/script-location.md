---
title: Location of Scripts and Classes
---

In the examples shown we have always used constant strings as the source code for our Jactl scripts and classes
but this is unlikely to be the case in a proper application.
In general, the scripts and classes will be read from files or from the database since the whole idea of embedding
Jactl in an application is to allow users of the application a way to customise the application behaviour via these
scripts.

See [Example Application](https://github.com/jaccomoc/jactl-vertx#example-application) which gives an example
application that reads Jactl scripts on demand from the file system in order to respond to incoming web service requests.
