---
title: Adding New Functions and Methods
---

Since the idea of integrating Jactl into an application is to provide a way to customise behaviour, it is likely that
the application would want to offer some additional global functions and methods that the Jactl scripts and classes
can use.

For example, assume we want to provide a method on byte arrays for encoding into base64 and a method on Strings for
decoding from base64:
```groovy
def x = [ 1, 2, 3, 4] as byte[]
def encoded = x.base64Encode()      // result: AQIDBA==
'AQIDBA=='.base64Decode()           // result: [1, 2, 3, 4]
```

In addition, to show how we can add new global functions, we will use the example function `sendReceiveJson()` that
is provided by the [`jactl-vertx`](https://github.com/jaccomoc/jactl-vertx) project.
It is an example function for sending/receiving JSON messages over HTTP:
```groovy
sendReceiveJson(url:'http://localhost:52178/wordCount', request:[text:'here are some more words to be counted'])
// result: [response:8, statusCode:200]
```

To make Jactl aware of new methods and global functions we use `Jactl.method()` (for methods) and `Jactl.function()`
(for functions) and use fluent-style methods to build the method/function, before finally invoking `.register()`
to register it with the Jactl.

So, to register the `base64Decode()` method, the following should be invoked sometime during application
initialisation (before any Jactl scripts/classes are compiled):
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```

The `Jactl.method()` method takes an argument, being the type of object on which the new method should exist.
For `base64Decode()` the method exists only on String objects, so we use `JactlType.STRING` to specify the Jactl
type for Strings.

The existing built-in types that can be used here are:

| Type               | Description                                        | Java Type to Use |
|:-------------------|:---------------------------------------------------|:-----------------|
| JactlType.ANY      | Any object                                         | Object           |
| JactlType.STRING   | Strings                                            | String           |
| JactlType.LIST     | Lists                                              | List             |
| JactlType.MAP      | Maps                                               | Map              |
| JactlType.ITERATOR | Anything that is iterable (List,Map,String,Number) | Object           | 
| JactlType.BOOLEAN  | boolean objects                                    | boolean          |
| JactlType.BYTE     | byte objects                                       | byte             |
| JactlType.INT      | int objects                                        | int              |
| JactlType.LONG     | long objects                                       | long             |
| JactlType.DOUBLE   | double objects                                     | double           |
| JactlType.DECIMAL  | BigDecimal objects                                 | BigDecimal       |
| JactlType.NUMBER   | All numeric types                                  | Number           |

:::info
In Jactl, unlike Java, primitive types can have methods defined for them.
:::

In addition, array types are also supported.
For the `base64Encode()` method, we need to specify that the method applies to byte arrays, so we use the `JactlType.arrayOf()`
method and pass in `JactlType.BYTE` to specify an array of bytes:
```java
Jactl.method(JactlType.arrayOf(JactlType.BYTE))
     .name("base64Encode")
     .impl(Base64Functions.class, "base64Encode")
     .register();
```

For global functions, there is no type that owns the function, so we use `Jactl.function()` instead of `Jactl.method()`
to construct the new function object and then use the same fluent methods to provide information about the function to
Jactl:
```java
Jactl.function()
     .name("sendReceiveJson")
     .param("url")
     .param("request")
     .impl(VertxFunctions.class, "sendReceiveJson")
     .register();
```

For both methods and global functions, the `name()` method must be specified as shown in the examples.
This is the name that Jactl scripts will use to refer to the method or function.

The other mandatory method is `impl()` which tells Jactl the class and the name of the static method to invoke
for the implementation of the method/function.

:::note
Previously, a public static `Object` field had to be provided for each method/function being registered.
This is now not needed but existing code will still function unchanged.
:::

If there are no parameters to the function/method then this is all that is needed before invoking the
`register()` method to register the function.

For methods, the first argument to the static implementing method will be the object on which the method is being
invoked.
For the `base64Encode()` method, the static implementation function should take a `byte[]` argument, and
the `base64Decode()` static function should take a `String` argument.
Since there are no parameters for these methods, the implementing class can be as simple as this:
```java
  public class Base64Functions {
    public static String base64Encode(byte[] data) {
      return new String(Base64.getEncoder().encode(data));
    }

    public static byte[] base64Decode(String data) {
      return Base64.getDecoder().decode(data);
    }
  }
```

## Parameters

Our example `base64Encode()` and `base64Decode()` methods did not have any parameters, but the `sendReceiveJson()`
global function takes two parameters: the URL to send to, and the request object to be sent as JSON.
When registering a method or function, if there are parameters, then they need to be declared using the `.param()`
method.
This allows Jactl to support named parameter passing.
For example, we declare the `url` and `request` parameters for the `sendReceiveJson()` function like this:
```java
Jactl.function()
     .name("sendReceiveJson")
     .param("url")
     .param("request")
     .impl(VertxFunctions.class, "sendReceiveJson")
     .register();
```
This allows us to invoke the function from Jactl using either positional or named parameters.
Using positional parameters:
```groovy
sendReceiveJson('http://localhost:52178/wordCount', [text:'here are some words to be counted'])
// result: [response:7, statusCode:200]
```

Using named parameters:
```groovy
sendReceiveJson(url:'http://localhost:52178/wordCount', request:[text:'here are some more words to be counted'])
// result: [response:8, statusCode:200]
```

Jactl infers the type of the parameters from the actual Java parameter types of the implementation method so there
is no need to specify a type for the parameters.

:::note
The names that you register the parameters with using `param("...")` do not have to match the names in the
Java static method but do have to be declared in the right order.
The names will be used to validate any calls that are done using named arguments.
:::

## Default Values

When registering the parameter names you can also specify a default value for parameters that are optional.
For example, the built-in `String.asNum(base)` method parses a String that could be in any base (up to 36).
By default, it assumes base 10 but can be used to parse hex strings or binary strings etc.:
```java
Jactl.method(STRING)
     .name("asNum")
     .param("base", 10)
     .impl(BuiltinFunctions.class, "stringAsNum")
     .register();
```

The call `param("base", 10)` means that if no parameter is supplied it will be automatically filled in with `10`.

## Exception Handling

If you would like your method or function to indicate that a runtime error has occurred and that it doesn't make sense
to continue execution of the script, then you should throw a `io.jactl.runtime.RuntimeError` exception.
There are two constructors of `RuntimeError` that can be used:
```java
  public RuntimeError(String error, String source, int offset);
  public RuntimeError(String error, String source, int offset, Throwable cause);
```

The `source` and `offset` parameters refer to the source code and offset into the source code where the error occurs.
In order to know where your method/function is being invoked in the source code, you should declare a `String` and `int`
parameter in your static implementation method and Jactl will take care of populating these parameters with the values
from where in the code the method/function was invoked.
Don't declare these using `.param()` since they are not explicit parameters that the script writer passes in.

:::note
These two parameters are optional.
If not declared by the static implementation function, then it assumed that the function/method does not need
these values since it won't throw any exceptions.
:::

Any actual parameters for the method/function should be declared in the implementation method after these `String` and
`int` parameters.
Note that for methods, the `String` and `int` parameters come after the object that the method is being invoked on.

Here is an example class showing a global function implementation and a method implementation that both need to
throw `RuntimeError` exceptions:
```java
public class ExampleFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.function()
         .name("exampleFunction")
         .param("param1", "default-value")
         .impl(ExampleFunctions.class, "exampleFunctionImpl")
         .register();
    
    Jactl.method(JactlType.STRING)
         .name("exampleMethod")
         .param("firstArg", -1)
         .impl(ExampleFunctions.class, "exampleMethodImpl")
         .register();
  }
  
  public static Object exampleFunctionImpl(String source, int offset, String param1) {
    try {
      ...
    }
    catch (Exception e) {
      throw new RuntimeError("Error invoking exampleFunction", source, offset, e);
    }
  }
  
  // Note that the target of the method is always the first parameter
  public static int exampleMethodImpl(String str, String source, int offset, int firstArg) {
    try {
      ...
    }
    catch (Exception e) {
      throw new RuntimeException("Error invoking exampleMethod", source, offset, e);
    }
  }
}
```

## Suspending Execution

Some functions/methods need to perform long-running operations and wait for the operation to complete before returning
a result. For example, the `sendReceiveJson()` function sends a request to a remote service and waits for the response
before returning the response to the caller.

The problem is that we don't want our `sendReceiveJson()` function to actually block and wait for the response.
The response may take a long time, or we might time out waiting, and we don't want to block the event-loop thread
for that entire time.
We would like to be able to the function to suspend execution of the current script until the result is returned,
freeing up the event-loop thread to process other events.

Any function that is going to suspend the current execution needs to accept a `io.jactl.runtime.Continuation` object as its first
argument to allow the function to be able to be resumed/continued once the asynchronous operation has finished.
The idea is that the function can check the `Continuation` object to see if it is being resumed (continued) after a
suspension and can also obtain the result of the long-running asynchronous operation from the `Continuation` object
if it is being resumed and then perform any further processing it needs to do after the asynchronous operation has
returned a result.
If the `Continuation` object is `null` it means that this is the first invocation and that it is not being resumed
after a suspension.

There is no need to tell Jactl that the function/method is asynchronous as the presence of this `Continuation`
parameter in the signature of the implementation method already indicates that the function/method can suspend
execution.

For global functions that are asynchronous, the `Continuation` parameter of the function is always the first parameter.
For methods that perform asynchronous operations, the `Continuation` parameters must be the second parameter after the
parameter that represents the object on which the method is being invoked:
```java
  public static Object someAsyncFunction(Continuation c, Object param1, String param2) { 
    ...
  }

  public static String someAsyncMethod(String str, Continuation c, String param1, Object param2, int param3) {
    ...
  }
```

When performing asynchronous operations, there will invariably be errors that need to be thrown as exceptions, and so
the usual signature for an asynchronous function will have the `Continuation` parameter followed by the `String`
parameter for the source code, and the `int` parameter for the offset into the source where the invocation is taking
place so the example above would more likely look like this:
```java
  public static Object someAsyncFunction(Continuation c, String source, int offset, Object param1, String param2) { 
    ...
  }

  public static String someAsyncMethod(String str, Continuation c, String source, int offset, String param1, Object param2, int param3) {
    ...
  }
```

In order to actually suspend the execution state, there are two static methods provided by the `Continuation` class
that can be invoked from within the implementation of the function/method:
1. `Continuation.suspendBlocking()`, and
2. `Continuation.suspendNonBlocking()`.

The `suspendBlocking()` call is for use where the work to be done will be queued to occur on a blocking thread from
the pool of blocking threads.
Once a blocking thread is available it will run the work until completion and then schedule processing of the result
back onto an event-loop thread (possibly the original event-loop thread if the execution environment provides that
level of control).

The `sendReceiveJson()` function, for example, needs to send a request, wait for a response, and return the response
to the caller.
Since we can't do this on an event-loop thread, we could schedule all of this to occur on a blocking thread using the
`Continuation.suspendBlocking()` call like so:
```java
  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    Continuation.suspendBlocking(() => {
      try {
        // send request
        ...
        // wait for response
        ...
        return result;
      }
      catch (Exception e) {
        return new RuntimeError("Error invoking " + url, source, offset, e);   // Note: "return" not "throw"
      }
    });
  }
```

:::warning
Any errors that occur on the blocking thread should be **returned** (not thrown) as `RuntimeError` objects, as shown.
:::

The use of `Continuation.suspendBlocking()` like this should be when the work being done requires using a blocking API
and there is no away to avoid having the thread block while the work is being performed.
An interface to an SQL database might use this approach, for example, if the only API provided is a synchronous one.

In the case of the `sendReceiveJson()` function, however, since we are using Vert.x to do the send and receive, and
Vert.x is by nature an asynchronous library, we don't need to tie up an entire blocking thread while we wait for the
response.
Instead, we use the `Continuation.suspendNonBlocking()` and pass it a handler that will initiate the request and
register its own handler with Vert.x to be invoked when the response is received.

:::warning
It is important that the initiation of the async request be done inside the handler passed to
`Continuation.suspendNonBlocking()` and not before, in order to guard against race conditions where the
response might be received before we have finished suspending our execution.
:::

The handler that we pass to `Continuation.suspendNonBlocking()` accepts two arguments:
1. a `JactlContext` in case we need it, and
2. a resume handler that will take care of resuming the execution and that must be passed the result once available.

In our example we don't need the `JactlContext` so we just need to make sure that when the response handler we register
with Vert.x is invoked we then invoke the resume handler we get passed in to us with the result.
This will ensure that the script resumes on an event-loop thread (since Vert.x schedules response handlers on the
same event-loop thread that the asynchronous operation was scheduled from) and that the result is handed back to the
script.

A simple implementation of all this could look like this:
```java
public class VertxFunctions {
  private static WebClient webClient;

  public static Map sendReceiveJson(Continuation c, String source, int offset, String url, Object request) {
    Continuation.suspendNonBlocking((context, resumer) => {
      try {
        webClient.postAbs(url)
                 .sendJson(request)
                 .onSuccess(response => {
                   // Our Vert.x response handler
                   resumer.accept(getResult(response));   // continue our script with result
                 });
      }
      catch (Exception e) {
        // Give error to the resumer
        resumer.accept(new RuntimeError("Error invoking " + url, source, offset, e));   // Return error as result
      }
    });
    return null;       // never happens since function is async
  }
  
  ...
}
```

:::note
Some details (such as better error handling) have been left out for brevity.
To see the full implementation see the [`VertxFunctions` example class](https://github.com/jaccomoc/jactl-vertx/blob/main/src/test/java/io/jactl/vertx/example/VertxFunctions.java).
:::

:::warning
Any errors should be returned as `RuntimeError` objects to the `resumer` handler as a result as shown.
:::

## Suspend/Resume and the Continuation Object Argument

Both of the `Continuation.suspendBlocking()` and `Coninuation.suspendNonBlocking()` calls work by throwing a
`Continuation` object.
Each function in the call stack can then catch the `Continuation` and throw a new `Continuation` chained to the old
one that captures any state they need to preserve.
When continued after a resumption, the `Continuation` passed in will have this preserved state to allow the function
to work out where it was up to and to continue with all the state it had before it was suspended.

Note, that unless the asynchronous function/method actually invokes other Jactl code by invoking a passed in
closure (see below), the function will only ever be invoked with a `null` value for the `Continuation` object
indicating that it is a normal invocation (not a resumption).

## Async Parameters

Any function or method that suspends the current execution is known as asynchronous.
Asynchronous functions and methods are flagged as being asynchronous by declaring a `Continuation` argument as discussed.

Sometimes, a function or method is only asynchronous because it invokes something on one of its arguments that
is asynchronous and could suspend the current execution.
For example, a method that takes a closure/function as an argument and invokes it at some point has no way of knowing
if the closure passed to it will suspend or not, and so it has to assume that it can be suspended and declare a
`Continuation` argument as discussed previously.

When the compiler detects that it is generating code to invoke a function that is potentially asynchronous, it needs
to generate some code to capture the current execution state.
If the function or method is only asynchronous when one of its parameter values is an asynchronous function then
sometimes it can be invoked with a value that is asynchronous, and sometimes it might be invoked with a value that
isn't.
To prevent the compiler from having to generate unnecessary code for capturing execution state, the parameter can be
flagged as being "async" and the compiler won't generate this additional code if it knows that the argument being passed
in cannot perform any asynchronous operations.

Parameters like these should be declared with `asyncParam()` instead of `param()` when registering the method or function.
For example, if we had a function called `measure()` that invoked a passed in closure/function, and then measured how
long it took to complete, it would be registered like this:
```java
Jactl.function()
     .name("measure")
     .asyncParam("closure")     // only async when closure passed in is async
     .impl(MyFunctions.class, "measure")
     .register()
```

The implementation would look like this (closures/functions passed as arguments are always `JactlMethodHandle` objects):
```java
class MyFunctions {
  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    ...
  }
}
```

## Async Instance

For methods that act on `JactlType.ITERATOR` objects, we allow the object to be one of the following types:
* List
* Map
* String &mdash; iterates of the characters of the string
* Number &mdash; iterates from `0` to `n-1` (i.e. `n` times)
* Iterator &mdash; the result of methods such as `map()`, `filter()` etc

If the object is an actual `Iterator` then it could be in a chain of method calls where asynchronous operations
(such as `sleep()`) are occurring.
For example:
```groovy
[1,2,3].map{ sleep(1, it) + sleep(1, it) }.filter{ it != 2 }.each{ println it } 
```

In this example the object that `filter()` acts on is an `Iterator` and calling `next()` would result in the
closure passed to `map()` being invoked which will suspend since it invokes `sleep()`.
So even though `filter()` itself does not have any asynchronous arguments in this instance, it acts on an
`Iterator` object that is asynchronous in nature.
The `asyncInstance(true)` call, when registering methods, tells Jactl that a method is asynchronous when the object
on which it acts is asynchronous.

So the registration of the `filter()` method looks like this:
```groovy
Jactl.method(ITERATOR)
     .name("filter")
     .asyncInstance(true)
     .asyncParam("predicate", null)
     .impl(BuiltinFunctions.class, "iteratorFilter")
     .register();
```
This tells the compiler that the `filter()` call is only asynchronous if the object it acts on is asynchronous or
the argument passed to it (the predicate closure) is asynchronous.

:::note
As shown, `asyncParam()` calls are also allowed to specify a default value where appropriate.
:::

## Handling Resumption

Our `sendReceiveJson()` function from before did not have to consider how to resume itself after suspending because
when the resumption occurred we already had the result and there was no more processing for the function to do.

We will now examine how our example `measure()` function might work to see how to handle functions that do need to
do more processing when they are resumed.

A naive implementation of `measure()` might look like this:
```java
class MyFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.function()
         .name("measure")
         .param("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }
 
  public static long measure(String source, int offset, JactlMethodHandle closure) {
    long start = System.nanoTime();
    RuntimeUtils.invoke(closure, source, offset);
    return System.nanoTime() - start;
  }
  public static Object measureData;
}
```

We use the `io.vertx.runtime.RuntimeUtils.invoke()` helper method to invoke the closure.
It gets passed the JactlMethodHandle, the source and offset, and then a varargs set of any arguments that
the closure/function expects.
In this case we are assuming a zero-arg closure/function was passed to us so there are no arguments that need to
be passed in.

Once we have configured our `.jactlrc` file (see [here](../command-line-scripts#jactlrc-file) for details)
we can include our new function when running the Jactl REPL:
```groovy
$ java -jar jactl-repl-2.3.0.jar
> long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
Function@1846982837
> measure{ fib(40) }
184077675
```

As you can see, it returns the number of nanoseconds it took to invoke `fib(40)` which equated to around 184ms.

Since the closure being invoked by `measure()` could suspend and wait for some asynchronous operation, `measure()`
needs to gain control once it completes in order to work out how long it all took.
In order to do that, we need to:
1. catch the `Continuation` thrown when the closure suspends itself,
2. create a new `Continuation` chained to the one it caught,
3. pass in a `JactlMethodHandle` to the new `Continuation` that points to a method that will be invoked when we are resumed,
4. capture any state we need in this new `Continuation` object (including the values of the parameters originally
   passed in if needed), and
5. throw the new `Continuation`.

The constructor of a `Continuation` looks like this:
```java
public Continuation(Continuation      parentContinuation,
                    JactlMethodHandle resumeMethodHandle,
                    int               codeLocation,
                    long[]            localPrimitives,
                    Object[]          localObjects)
```

The first parameter is the `Continuation` we just caught.
This allows the `Continuation` objects to be chained together.

The second parameter is a `JactlMethodHandle` that will be invoked when we are resumed.
It needs to point to a static method that takes a single `Continuation` argument.
When resumed, it will be passed the `Continuation` we are constructing and throwing here.

The third parameter is an `int` called `location` which represents a logical location in our method where we
were suspended.
It allows us to record where in the method we were when we were suspended in case there are multiple locations
where this can occur.
We can pass in any value as long as we understand the relationship between the different values and the actual
locations in the code.

The last two parameters are used to capture the current state.
One is an array of `long` values, which we can use for storing any primitive values we need, and
the other one is an array of `Object` values, where we store any non-primitive values we need.

In our case, the only state we need to capture is the start time which is a `long` so our code to catch
a `Continuation` and then throw a new chained one would look like this:
```java
  long start = System.nanoTime();
  try {
    ...
  }
  catch(Continuation cont) {
    throw new Continuation(cont, measureResumeHandle, 0, new long[]{ start }, new Object[0]);
  }
```

We just pass location as a value of 0 for the moment since there is only one place where we can be suspended.

We need a continuation method that can be invoked when we are resumed so that we can create the `JactlMethodHandle`
called `measureResumeHandle` that points to it and pass this handle to the `Continuation` on construction.
The continuation method must take only a `Continuation` as an argument and must return `Object`.

In our case we want the resumption method to return the duration.
In order to do that it needs to extract the value for the start time from our saved state in the `Continuation`.
The `Continuation` has a field called `localPrimitives` for the `long` array and a field called `localObject`
for the `Object` array that we passed in when we created it.

This means that our resumption method can look like this:
```java
  public static Object measureResume(Continuation c) {
    long start = c.localPrimitives[0];
    return System.nanoTime() - start;
  }
```

To get a `JactlMethodHandle` we can use the utility method `RuntimeUtils.lookupMethod()`.
This utility method takes a `Class`, the name of the static method, the return type, and then a varargs
list of argument types.

Rather than invoke this every time it is better to do it once and store it in a static field for when we need it:
```java
  private static JactlMethodHandle methodResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class, 
                                                                                  "measureResume",
                                                                                  Object.class,
                                                                                  Continuation.class);
```

We now need to make sure to tell Jactl that our function has an argument that makes us async if it is async
by changing `param()` to `asyncParam()`:
```java
   Jactl.function()
        .name("measure")
        .asyncParam("closure")
        .impl(MyFunctions.class, "measure")
        .register();
```

We also need to add a `Continuation` parameter to our function since it is now potentially async:
```java
  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    ...
  }
```

Putting this all together, our class now looks like this:
```java
class MyFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.function()
         .name("measure")
         .asyncParam("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }

  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    long start = System.nanoTime();
    try {
      RuntimeUtils.invoke(closure, source, offset);
      return System.nanoTime() - start;
    }
    catch(Continuation cont) {
       throw new Continuation(cont, measureResumeHandle,
                              0,
                              new long[]{ start },
                              new Object[0]);
    }
  }

  public static Object measureResume(Continuation c) {
    long start = c.localPrimitives[0];
    return System.nanoTime() - start;
  }

  private static JactlMethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
                                                                                   "measureResume",
                                                                                   Object.class,
                                                                                   Continuation.class);
}
```

Now we can measure how many nanoseconds it takes (wall clock time) for a closure to finish even if it does asynchronous
operations:
```groovy
measure{ sleep(1000) }
// result: 1001947542
```

To illustrate a slightly more complicated scenario, imagine that we actually want to run the code we are measuring
multiple times and return the average.
To avoid having to duplicate code, our resume method should re-invoke our original method.
The original method will then check if the `Continuation` argument is null or not to know whether it is the
original call or a resumption of a previous call.

In order for the resume method to invoke the original method, it will need to be able to pass in values for
`source`, `offset`, and `closure`, so we will need to store these as part of our state when throwing a
`Continuation`, and we will use the `location` parameter to record which iteration we are up to.

Now our code looks like this:
```java
class MyFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.function()
         .name("measure")
         .param("count", 1)
         .asyncParam("closure")
         .impl(MyFunctions.class, "measure")
         .register();
  }

  public static long measure(Continuation c, String source, int offset, int count, JactlMethodHandle closure) {
    long start = c == null ? System.nanoTime() : c.localPrimitives[2];
    int  i     = c == null ? 0                 : c.methodLocation;
    try {
      for (; i < count; i++) {
        RuntimeUtils.invoke(closure, source, offset);
      }
      return (System.nanoTime() - start) / count;
    }
    catch(Continuation cont) {
      throw new Continuation(cont, measureResumeHandle,
                             i + 1,                      // location is next loop counter value
                             new long[]  { offset, count, start },
                             new Object[]{ source, closure });
    }
  }
  public static Object measureData;

  public static Object measureResume(Continuation c) {
    String source = (String)c.localObjects[0];
    int    offset = (int)c.localPrimitives[0];
    int count  = (int)c.localPrimitives[1];
    JactlMethodHandle closure = (JactlMethodHandle)c.localObjects[1];
    return measure(c, source, offset, count, closure);
  }

  private static JactlMethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(MyFunctions.class,
                                                                                   "measureResume",
                                                                                   Object.class,
                                                                                   Continuation.class);
}
```

Now we invoke our `measure()` function with a count and a closure:
```groovy
long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }
measure(10){ fib(40) }                     // result: 184077675
```

It will also do the right thing if we do some asynchronous operation (like a `sleep()`) inside the closure:
```groovy
measure(10){ sleep(100); fib(40) }        // result: 284705195
```

Of course, it is much easier to write this in Jactl itself since the language already takes care of suspending
and resuming for you:
```groovy
def measure(n, closure) {
  def start = nanoTime()
  n.each{ closure() }
  (nanoTime() - start) / n
}

long fib(long x) { x <= 2 ? 1 : fib(x-1) + fib(x-2) }

measure(10){ sleep(100); fib(40) }         // result: 284375954
```

## Registering Functions/Methods for Specific `JactlContext` Objects

In all examples so far, the custom functions/methods that have created have been registered globally using `Jactl.function()`
and `Jactl.method(type)` and are therefore available to all scripts within the application.

If different sets of scripts should have access to different sets of functions/methods, then instead of using `Jactl.function()`
and `Jactl.method(type)` to register the function/method, you can create your `JactlContext` object and use the `function()`
and `method(type)` methods on it to register functions and methods that will only be visible to scripts compiled with
that `JactlContext`.

For example:
```java
class MyModule {
  
  private static JactlContext context; 
  
  public static void registerFunctions(JactlContext context) {
    context.method(JactlType.ANY)
           .name("toJson")
           .impl(JsonFunctions.class, "toJson")
           .register();

    context.method(JactlType.STRING)
           .name("fromJson")
           .impl(JsonFunctions.class, "fromJson")
           .register();
    
    context.function()
           .name("getState")
           .param("sessionId")
           .impl(MyModule.class, "getState")
           .register();
  }
  
  public static Object getStateData;
  public static Map getState(long sessionId) { ... }
  
  public void init(JactlEnv env) {
    context = JactlContext.create()
                          .environment(env)
                          .hasOwnFunctions(true)
                          .build();
    
    registerFunctions(context);
  }
  
  ...
}
```

The way in which the function/method is registered is identical, except that we use the `JactlContext` object rather
than the `Jactl` class (as shown in the example).

:::note
The `JactlContext` will also have access to all functions/methods that have already been registered using
`Jactl.function()` or `Jactl.method()` at the point at which the `JactlContext` is created.
If other functions/methods are later registered using `Jactl.function()` or `Jactl.method()` after the `JactlContext`
was created, these additional functions/methods will not be available to scripts compiled with that `JactlContext`.
:::

## Deregistering Functions

It is possible to deregister a function/method so that it is no longer available to any new scripts that are compiled.
This might be useful in unit tests, for example.

To deregister a global function just pass the function name to `Jactl.deregister()`:
```java
Jactl.deregister("myFunction");
```

To deregister a function from a `JactlContext`:
```java
jactlContext.deregister("myFunction");
```

To deregister a method:
```java
Jactl.deregister(JactlType.STRING, "lines");
jactlContext.deregister(JactlType.LIST, "myListMethod");
```

## Integration with REPL and CommandLine Scripts

For ease of integration with the Jactl REPL and Jactl commandline scripts, it is recommended that you have a
public static function called `registerFunctions(JactlEnv env)` in one of your classes (generally the same one
where the implementation is) that takes care of registering your functions.
This allows you to configure the class name in your `.jactlrc` file and have the functions automatically available
in the REPL and in commandline scripts.

For example:
```java
class MyFunctions {
  public static void registerFunctions(JactlEnv env) {
    Jactl.method(JactlType.ANY)
         .name("toJson")
         .impl(JsonFunctions.class, "toJson")
         .register();

   Jactl.method(JactlType.STRING)
        .name("fromJson")
        .impl(JsonFunctions.class, "fromJson")
        .register();
  }
  ...
}
```

See [here](../command-line-scripts#jactlrc-file) for more details.

