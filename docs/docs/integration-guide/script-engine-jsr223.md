---
title: Java Scripting API (JSR 223)
description: "How to use the Java Scripting API (JSR223) to run Jactl scripts."
---

Since Java 6 there has been a standard [Java Scripting API](https://docs.oracle.com/javase/8/docs/technotes/guides/scripting/prog_guide/api.html) 
(defined by [JSR223](https://jcp.org/en/jsr/detail?id=223) for accessing embedded script engines from a Java application.

Jactl supports this API, but since the API is a standardised API that works across many different script types,
not all the flexibility offered by the `Jactl` and `JactlContext` classes is available via this API.

To use this API, you will need to first create a `ScriptEngineManager` and then use it to create a Jactl
`ScriptEngine` by looking it up by the name `jactl`:

```java
import javax.script.*;

ScriptEngineManager engineMgr   = new ScriptEngineManager();
ScriptEngine        jactlEngine = mgr.getEngineByName("jactl");
```

You can then evaluate scripts using the `ScriptEngine` instance:
```java
Object result = jactlEngine.eval("2 + 3");
System.out.println("Result is " + result);
```

To pass variables to the scripts, you can set binding variables in the `ScriptEngineManager` or in the `ScriptEngine`
instance.
Bindings set in the `ScriptEngineManager` are called global bindings and are shared by all `ScriptEngine` instances
created by that `ScriptEngineManager`.
Bindings set on the `ScriptEngine` are known as engine bindings and local to that engine but will be shared across
all script invocations done using that engine instance.

For example:
```java
engineMgr.put("x", 2);
jactlEngine.put("y", 3);
Object result = jactlEngine.eval("x + y");   // returns 5
```

Another way to set binding values is to use a `ScriptContext` instance and use the `eval(String,ScriptContext)`
method:

```java
ScriptContext ctx = new SimpleScriptContext();
Bindings engineBindings = jactlEngine.createBindings();
ctx.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
Bindings globalBindings = jactlEngine.createBindings();
ctx.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);
globalBindings.put("x", 2);
engineBindings.put("y", 3);
Object result = jactlEngine.eval("x + y", ctx);
```

Using a `ScriptContext` also allows you to set the `Reader` (using `setReader()`) and `Writer` (using `setWriter()`) 
that the script will use for its input (if it uses `nextLine()`) and output (if it uses `print/println`).

You can also pass a `Bindings` object to `eval()`:
```java
Bindings bindings = jactlEngine.createBindings();
bindings.put("x", 2);
bindings.put("y", 3);
Object result = jactlEngine.eval("x + y", bindings);
```

## CompiledScript

Note that the Jactl `ScriptEngine` instance will cache scripts to avoid having to recompile a script if it has seen
it before but the Java Scripting API has a way for `ScriptEngine` instances to provide a way to compile scripts into
a `CompiledScript` object that can be evaluated when needed, thus avoiding having to recompile a script each time.

The way this is done is via an optional `Compilable` interface that `ScriptEngine` classes can choose to implement.

The `JactlScriptEngine` class implements this interface and so offers the ability to create `CompiledScript` instances
from a script:

```java
import javax.script.*;

ScriptEngineManager engineMgr = new ScriptEngineManager();
JactlScriptEngine   jactlEngine = (JactlScriptEngine) engineMgr.getEngineByName("jactl");

CompiledScript script = jactlEngine.compile("x + y");
engineMgr.put("x", 2);
jactlEngine.put("y", 3);
Object result = script.eval();
```

`CompiledScript` instances also support the variants of `eval()` where you can pass either a `Bindings` object or
a `ScriptContext` object.

## Java Interoperability and Host Class Access

By default, Jactl provides a secure sandbox in which scripts can run.
This means that if you pass in a binding variable of a type that Jactl does not know about, it will, by default
give an error if a script tries to invoke a method on it.

If you wish to weaken the sandbox security of Jactl and allow scripts to invoke methods of classes belonging
to the host application (or Java built-in classes), you can enable this by setting special binding variables
on the `ScriptEngine` before evaluating or compiling any scripts with that `ScriptEngine` instance.

There are two binding variables that need to be set:
1. `jactl.allowHostAccess`: this is a boolean that controls whether host access is allowed or not, and
2. `jactl.allowHostClassLookup`: this controls which host classes are allowed to be accessed

:::warning
These settings should only be used when running trusted scripts.
Allowing scripts unrestricted access to host classes allows them to start threads, start processes, write to the
file system, access the network, and do anything that the user running the application has permission to do.
:::

The `jactl.allowHostClassLookup` binding variable can be set to `true` or `false` to enable all access to all classes
or none.
It can also be set to a `Predicate<String>` if you want more fine-grained control over which classes are accessible.
When Jactl tries to access a host class it will pass the name of the class to the predicate and if the predicate
returns true, it will allow access, and if the predicate returns false it will result in an error.

Note that the format of the class names passed to the predicate are standard Java class names including the package
name. For example `a.b.c.SomeClass` or `a.b.c.SomeClass$InnerClass`.

Here is an example of how to enable host class access where only classes within the package `org.mycompany.app` are
allowed to be accessed:
```java
import javax.script.*;

ScriptEngineManager engineMgr   = new ScriptEngineManager();
ScriptEngine        jactlEngine = mgr.getEngineByName("jactl");
jactlEngine.put("jactl.allowHostAccess", true);
jactlEngine.put("jactl.allowHostClassLookup", name -> name.startsWith("org.mycompany.app"));

Object jobs = jactlEngine.eval("import org.mycompany.app.svc.ServiceManager as SM\n" +
                               "import org.mycompany.app.sched.*\n" +  
                               "def svcMgr = new SM('test')\n" +
                               "def sched  = (Scheduler)svcMrg.getService('scheduler')\n" +
                               "return sched.currentJobs()\n");
```

## Other Configuration Options

In addition to the binding variables for allowing host class access, the following binding variables can be set
on the Jactl `ScriptEngine`.

:::note
The binding variables must be set before any scripts are evaluated or compiled.
:::

| Binding Variable             | Description                                                                                                                                                                                                  | Default Value  |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| jactl.allowUndeclaredGlobals | Whether to support compiling scripts that refer<br/>to binding variables not currently defined.                                                                                                              | false          |
| jactl.untypedGlobals         | Defaults to true and means that globals (binding variables)<br/>all have type of `java.lang.Object`.<br/>Set this to false if you want Jactl to use the type the<br/>binding variables have at compile time. | true           |
| jactl.disablePrint           | Set to true if you don't want scripts to be able to use `print` or `println`.                                                                                                                                | false          |
| jactl.disableEval            | Set to true if you don't want scripts to be able to invoke `eval()`.                                                                                                                                         | false          |
| jactl.disableDie             | Set to true if you don't want scripts to be able to invoke `die()`.                                                                                                                                          | false          |
| jactl.maxLoopIterations      | You can set this to prevent scripts from getting stuck in infinite loops.<br/>Jactl will count iterations in all loops in the script if the limit is<br/>reached it will generate an error.                  | infinite       |
| jactl.maxExecutionTime       | You can set a maximum execution time (ms) for scripts.                                                                                                                                                       | infinite       |

:::note
Setting either of `jactl.maxLoopIterations` or `jactl.maxExecutionTime` have a small overhead as Jactl needs to
generate code to periodically check whether the loop count or the execution time limits have been reached.
This is why they are not on by default.
This overhead is probably not noticeable for most types of scripts.
:::

