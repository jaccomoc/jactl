---
title: Getting Started
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

You can use Jactl in three different ways:
1. From the command line,
2. In an interactive REPL shell, or
3. You can integrate it into your application.

## Command Line Scripts

To run command line scripts you only need the Jactl jar which can be downloaded from Maven Central:
[https://repo1.maven.org/maven2/io/jactl/jactl/2.3.0/jactl-2.3.0.jar](https://repo1.maven.org/maven2/io/jactl/jactl/2.3.0/jactl-2.3.0.jar)

Then, to run a script you can use `-e` to specify the script on the command line:
```shell
java -jar jactl-2.3.0.jar -e '10.map{ it + 1 }.sum()'
```
Or, you can provide the name of a file that contains the script:
```shell
java -jar jactl-2.3.0.jar myscript.jactl
```

You can also use [JBang](https://www.jbang.dev/), if you have it installed, to run scripts.
JBang will take care of finding and downloading the JAR for you.
For example:
```shell
jbang run io.jactl:jactl:2.3.0 -e '10.map{ it + 1 }.sum()'
```

```shell
jbang run io.jactl:jactl:2.3.0 myscript.jactl
```

## REPL

You can also download the Jactl REPL, which gives you an interactive shell for trying out Jactl code, see the
[jactl-repl](https://github.com/jaccomoc/jactl-repl) project.

You can either download the JAR or use JBang.

To get the JAR, download it from [https://repo1.maven.org/maven2/io/jactl/jactl-repl/2.3.0/jactl-repl-2.3.0.jar](https://repo1.maven.org/maven2/io/jactl/jactl-repl/2.3.0/jactl-repl-2.3.0.jar)
and then run it directly:
```shell
$ java -jar jactl-repl-2.3.0.jar
> 10.map{ it + 1 }.sum()
55
```

With JBang, you can run it directly and it will download it for you if you don't already have it:
```shell
$ jbang run io.jactl:jactl-repl:2.3.0
[jbang] Resolving dependencies...
[jbang]    io.jactl:jactl-repl:2.3.0
[jbang] Dependencies resolved
> 10.map{ it + 1 }.sum()
55
> :h

Available commands:
  :h       Help - print this text
  :?       Alias for :h
  :x       Exit
  :q       Quit - alias for :x
  :c       Clear current buffer
  :r file  Read and execute contents of file
  :l       Load - alias for :r
  :s       Show variables and their values (concise form)
  :S       Show variables and their values in pretty printed form
  :p       Purge variables
  :e arg   Enable/disable stack traces for errors (true - enable, false - disable)
  :d level Enable/disable debug output for errors (0 - off, 1 - on, 2 - more detail)
  :H [n]   Show recent history (last n entries - defaults to 50)
  :! n     Recall history entry with given number
>  
```

## Integrating Jactl into an Application

### Dependency

To use Jactl you will need to add a dependency on the Jactl library.

<Tabs>
<TabItem value="Gradle" label="Gradle" default>
In the `dependencies` section of your `build.gradle` file:
```groovy
implementation group: 'io.jactl', name: 'jactl', version: '2.3.0'
```
</TabItem>
<TabItem value="Maven" label="Maven">
In the `dependencies` section of your `pom.xml`:
```xml
<dependency>
 <groupId>io.jactl</groupId>
 <artifactId>jactl</artifactId>
 <version>2.3.0</version>
</dependency>
```
</TabItem>
</Tabs>

### Jactl.eval()

The simplest way to run a Jactl script inside your application is to use the `io.jactl.Jactl` class and the `eval()` method:
```java
Object result = Jactl.eval("3 + 4");         // Will return 7
```

The result of the last expression of a script or the value returned in a `return` statement
will be the value returned from `Jactl.eval()`.

### Sharing Data

To share data with a Jactl script you can pass in a `java.util.Map` of name/value pairs.
The Jactl script can read and write values to these global variables:
```java
Map<String,Object> globals = new HashMap<>();
globals.put("x", 3);
globals.put("y", 4);
Object result = Jactl.eval("x += y", globals);
int    xval   = (int)globals.get("x");         // xval will be 7 
```

### Compiling Jactl Scripts for Reuse

Since you will probably want to run the Jactl scripts multiple times, you will probably
want to compile them once and run them as needed rather than having to recompile them
every time.
This is a much more efficient way to run Jactl code.

This will compile a script, run it, and then wait for it to complete:
```java
Map<String,Object> globals = new HashMap<>();
JactlScript script = Jactl.compileScript("3 + 4", globals);
Object      result = script.runSync(globals);          // result will be 7
assertEquals(7, result);
```

You can also have it run and have it invoke a callback when it is finished by passing in a
lambda that takes the result as its argument:
```java
script.run(globalValues, result -> System.out.println("Result is " + result));
```

### Adding a New Function

It is simple to add new functions for Jactl scripts to invoke.
For example, assume that you have this class in your application somewhere for decoding base64
encoded strings:
```java
public class Base64Functions {
  public static byte[] base64Decode(String data) { return Base64.getDecoder().decode(data); }
  public static Object base64DecodeData;
}
```
Then, to register this as a Jactl method on String objects just invoke this in your application
initialisation:
```java
Jactl.method(JactlType.STRING)
     .name("base64Decode")
     .impl(Base64Functions.class, "base64Decode")
     .register();
```
Now in your Jactl scripts you can invoke the function like this:
```groovy
def encoded = 'AQIDBA=='
def decoded = x.base64Decode()    // will be array of bytes: [1, 2, 3, 4]
```

Note that a `static Object` field is used by Jactl and by default should be named with
the same name as the function with a `Data` suffix (e.g. `base64DecodeData`) as shown in the example.

## Building Jactl from Source Code

### Requirements

* Java 8+
* Gradle 8.0.2
* ASM 9.6

### Build

Download the source code from GitHub as a zip file or use `git` to clone the repository:
```shell
git clone https://github.com/jaccomoc/jactl.git
cd jactl
./gradlew build
```

That will build `jactl-${VERSION}.jar` under the `build/libs` directory where `${VERSION}` is the current version or
whatever version you have downloaded/checked out.

To push to your local Maven repository you can use `publishToMavenLocal`:
```shell
./gradlew build publishToMavenLocal
```

## Further Information

For more information see the various guides under in the [documentation](../docs/introduction) section
of the [Jactl](https://jactl.io) website including:
* [Language Features](language-features) for a brief overview of the various features of the Jactl language
* [Language Guide](language-guide) for a complete description of the language
* [Integration Guide](integration-guide/introduction) for more information about how to integrate Jactl into a Java application
* [Command-line Scripts](command-line-scripts) for more information about how to run scripts on the command line
* [FAQ](faq) for some commonly asked questions and the answers 
