---
layout: page
title: "Commandline Scripts"
permalink: /command-line-scripts
---

* Table of contents 
{:toc}

Jactl scripts can be run from the shell command line and can be used in situations where you might normally
use Unix utilities such as `sed` or `awk` and even some situations where you might have used `perl` in the past.

The Jactl JAR file, when run using `java -jar` will compile and run a given script.

## Command Line Arguments

If you run the JAR file without any arguments it prints this help text:
```shell
$ java -jar jactl-{{ site.content.jactl_version }}.jar
Usage: jactl [options] [programFile] [inputFile]* [--] [arguments]*
         -p           : run in a print loop reading input from stdin or files
         -n           : run in a loop but do not print each line
         -e script    : script string is interpreted as jactl code (programFile not used)
         -v           : show verbose errors (give stack trace)
         -V var=value : initialise jactl variable before running script
         -d           : debug: output generated code
         -h           : print this help

Exception in thread "main" java.lang.IllegalArgumentException: Missing '-e' option and no programFile specified
	at io.jactl.Jactl.main(Jactl.java:52)
```

## Jactl Shell Script

It is recommended that you create a shell script for invoking Jactl called `jactl` to save having to type the
`java -jar <path_to>/jactl-{{ site.content.jactl_version }}.jar` every time.

For a shell compatible with `bash` you can create a shell script like the following called `jactl` and add it to
a directory in your execution path:
```shell
#!/bin/bash
java -jar <path_to>/jactl-{{ site.content.jactl_version }}.jar "$@"
```

Note that the `/bin/bash` should be the location of your shell and `<path_to>` should be replaced with the location
of the `jactl-{{ site.content.jactl_version }}.jar` file.

We will assume from now on that you have such a shell script and will use `jactl` in place of `java -jar jactl-{{ site.content.jactl_version }}.jar`. 

## Running Scripts

To run a file containing a Jactl script just pass the file name directly to the `jactl` command.
For example, assume there is a file `myscript.jactl` containing this:
```groovy
def fact(x) { x <= 1 ? 1 : x * fact(x - 1) }

10.map{ it + 1 }
  .map{ [it, fact(it)] }
  .each{ n,fact -> println "Factorial of $n is $fact" }
```

To run this script:
```shell
$ jactl myscript.jactl
Factorial of 1 is 1
Factorial of 2 is 2
Factorial of 3 is 6
Factorial of 4 is 24
Factorial of 5 is 120
Factorial of 6 is 720
Factorial of 7 is 5040
Factorial of 8 is 40320
Factorial of 9 is 362880
Factorial of 10 is 3628800
```

## The -e Option

The `-e` option allows you to run some Jactl code entered directly on the command line:
```shell
$ jactl -e '10.map{ it + 1 }.sum()'
55
```

Note that `-e` only accepts a single argument so you should wrap the code in `'` single quotes so that the shell
won't split the code into multiple arguments when a space is encountered.

If you want to be able to use single quotes in your script then in `bash`, if the first quote is prefixed by `$`, you
are then allowed to use `\` to escape other characters within the single quotes (including single quotes):
```shell
$ jactl -e $'10.map{ (int)\'a\' + it }.map{ it.asChar() }.join()'
abcdefghij
```

## Printing Result

By default, when not using the `-p` or `-n` options (see below), Jactl will output the value of the final expression
in the Jactl script or command line script:
```shell
$ jactl -e '3 + 4'
7
```

If the script itself already invokes `print` or `println` then it assumes that the script wants to control its output
and the default printing of the final expression is suppressed:
```shell
$ jactl -e 'println "Result is ${3 + 4}"'
Result is 7
```

## The -V Option

The `-V` option allows you to define a variable and a string value for that variable that the script can access:
```shell
$ jactl -V n=10 -e '(n as int).map{ (it+1).sqr() }'
[1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
```

The option can be used multiple times:
```shell
$ jactl -V n=10 -V power=3 -e '(n as int).map{ (it+1).pow(power as int) }'
[1, 8, 27, 64, 125, 216, 343, 512, 729, 1000]
```

See [Passing Arguments to Script](#passing-arguments-to-script) for another way to pass in values from the command line
to a script.

## The -v Option

The `-v` option is used when there are errors to show more verbose output.
This means that the error stack trace will be shown along with the error where as
normally only the error message is shown.

## The -d Option

The `-d` option is the debug option to print out the generated code.
If given twice it outputs even more detail.

This option should only be used when reporting problems.

## Input

The Jactl scripts can read from `stdin` using `nextLine()`.
Assume there is a file called `some_file` with this content:
```shell
This is the first line in a file
This is the second line in the same file
This is the third line
```

Then, to prepend the line length to each line we can `cat` the file to our jactl script:
```shell
$ cat some_file | jactl -e 'def line; while ((line = nextLine()) != null) { println "${line.size()}: $line" }'
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
```

This could also be written more idiomatically as:
```shell
$ cat some_file | jactl -e 'stream(nextLine).each{ println "${it.size()}: $it" }'
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
```

Jactl supports a list of files being passed to it on the command line so this achieves the same thing:
```shell
$ jactl -e 'stream(nextLine).each{ println "${it.size()}: $it" }' some_file
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
```

Multiple file names can be listed and the script will concatenate the multiple files into one stream of input.
Assume that there is another file called `another_file` with this content:
```shell
this is some text in another file
and this is another line in that file
```

Then if we pass both file names to the script we will have this output:
```shell
$ jactl -e 'stream(nextLine).each{ println "${it.size()}: $it" }' some_file another_file
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
33: this is some text in another file
37: and this is another line in that file
```

When file names are passed to the script, the `stdin` input is not read.
If you want to include `stdin` in the input processed by the script you use a single hyphen `-` as the file name.
For example:
```shell
$ jactl -e 'stream(nextLine).each{ println "${it.size()}: $it" }' some_file - another_file
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
text typed in at the terminal
29: text typed in at the terminal
and more text
13: and more text
33: this is some text in another file
37: and this is another line in that file
```

Note that lines not starting with a number were typed in at the terminal and so form the input for `stdin`.
To terminate the `stdin` input `<ctrl-D>` was used.
Since the `-` file name was the second one in the list the `stdin` input was added between the two files.

## The -p and -n Options

The `-p` option causes the script to be wrapped in the following code:
```groovy
def it
while ((it = nextLine()) != null) {
  ...      // your script inserted here
  
  println it
}
```

This means that the `it` variable will iterate over the lines of the input (whether `stdin` or files listed on command
line).

For example, given the files we saw in the previous section called `some_file` and `another_file`, we can do this to
prepend the length to every line:
```shell
$ jactl -p -e 's/^/${it.length()}: /' some_file another_file
32: This is the first line in a file
40: This is the second line in the same file
22: This is the third line
33: this is some text in another file
37: and this is another line in that file
```

This works because `s/^/${it.length()}: /` by default operates on the `it` variable and so it modifies `it` which is
then automatically printed by the implicit `println it` in the wrapping code.

Note that when there are multiple options, they can be joined together (unless they take an argument) so
`-p -e 's/^/${it.length()}: /'` can also be written `-pe 's/^/${it.length()}: /'` since `-p` doesn't take an
argument.

If you don't want to automatically print every line then the `-n` has the same behaviour except that it doesn't
automatically print the line each time, so in order to print only lines that match some regex we could do this:
```shell
$ jactl -ne '/ s.*file/r and println it' some_file another_file
This is the second line in the same file
this is some text in another file
```

To print lines that do not match a regex, this would work:
```shell
$ jactl -ne '/ s.*file/r or println it' some_file another_file
This is the first line in a file
This is the third line
and this is another line in that file
```

Or another way to print lines that don't match:
```shell
$ jactl -ne 'println it unless / s.*file/r' some_file another_file
This is the first line in a file
This is the third line
and this is another line in that file
```

And yet another way:
```shell
$ jactl -ne '!/ s.*file/r and println it' some_file another_file
This is the first line in a file
This is the third line
and this is another line in that file
```

Note that these options can also be used when the script is in a separate file rather than on the command line.
So imagine we have a script called `freq.jactl` that counts letter frequency for a line:
```groovy
println it.filter{ it != " " }
          .reduce([:]){ m,c -> m[c]++; m }
          .sort{ a,b -> b[1] <=> a[1] }
          .map{ c,freq -> "$c:$freq" }
          .join(" ")
```

To run this over all lines of our input files we do this:
```shell
$ jactl -n freq.jactl some_file another_file
i:6 s:3 e:3 h:2 t:2 f:2 l:2 n:2 T:1 r:1 a:1
e:6 i:5 s:4 h:3 n:3 t:2 l:2 T:1 c:1 o:1 d:1 a:1 m:1 f:1
i:4 h:3 s:2 t:2 e:2 T:1 r:1 d:1 l:1 n:1
t:4 i:4 e:4 s:3 h:2 o:2 n:2 m:1 x:1 a:1 r:1 f:1 l:1
i:5 n:4 t:4 a:3 h:3 e:3 s:2 l:2 d:1 o:1 r:1 f:1
```

Since the script is wrapped in a `while` loop, it is possible for the script to use `continue` and `break` where
it makes sense to control the behaviour of the loop:

```shell
$ jactl -pe '/second/r and continue; s/This is//' some_file another_file
 the first line in a file
 the third line
this is some text in another file
and this is another line in that file
```

Note in this example how the `continue` skips the processing and the printing if the input line matches `/second/`.

## BEGIN and END Sections

When running with the `-p` or `-n` options you might want to do some initialisation at the very beginning or print
out a result at the very end of the input.

You can use a `BEGIN` section to perform any up-front initialisation before any input is processed and an `END`
section to perform any work after the input has all been processed.

Here is an example where we modify the `freq.jactl` script to add a `BEGIN` section to initialise a Map that will
keep letter frequencies across all lines, and an `END` section to print the results at the end:
```groovy
BEGIN {
  freq = [:]
}

println it.filter{ it != " " }
          .reduce([:]){ m,c ->
            m[c]++
            freq[c]++     // update global frequency count
            m
          }
          .sort{ a,b -> b[1] <=> a[1] }
          .map{ c,freq -> "$c:$freq" }
          .join(" ")

END {
  println "\nTotals: " + freq.sort{ a,b -> b[1] <=> a[1] }
                             .map{ c,freq -> "$c:$freq" }
                             .join(" ")
}
```
Then when we run it:
```shell
$ jactl -n freq.jactl some_file another_file
i:6 s:3 e:3 h:2 t:2 f:2 l:2 n:2 T:1 r:1 a:1
e:6 i:5 s:4 h:3 n:3 t:2 l:2 T:1 c:1 o:1 d:1 a:1 m:1 f:1
i:4 h:3 s:2 t:2 e:2 T:1 r:1 d:1 l:1 n:1
t:4 i:4 e:4 s:3 h:2 o:2 n:2 m:1 x:1 a:1 r:1 f:1 l:1
i:5 n:4 t:4 a:3 h:3 e:3 s:2 l:2 d:1 o:1 r:1 f:1

Totals: i:24 e:18 s:14 t:14 h:13 n:12 l:8 a:6 f:5 r:4 o:4 T:3 d:3 m:2 c:1 x:1
```

Note that when using `-p` and `-n`, global variables do not have to be declared before they can be used.
Normally we would declare the `freq` variable in the `BEGIN` section like this:
```groovy
BEGIN {
  def freq = [:]
}
```

If we do that, however, the scope of `freq` is the block within which it is declared, so it would not be visible outside
the `BEGIN` block.

That is why, when running with `-p` and `-n` options, global variables do not need to be declared:
assigning to a global variable will cause it to be created.

## Passing Arguments to Script

As well as passing in variables using the `-V` option, you can additionally pass in arguments for a script at the
command line by using `--` (double hyphen) and then listing the arguments.
The `--` tells Jactl that there are no more options for Jactl itself (including no more file names) and that
all arguments after the `--` should be passed as an array in a variable called `args` that the script can then
access.

For example:
```shell
$ jactl -e 'args.each{ println "arg: $it" }' -- additional args 
arg: additional
arg: args
```

## `.jactlrc` File

At start up time the contents of `~/.jactlrc` are read.
This file, if it exists, is itself a Jactl script and allows you to customise the behaviour of the Jactl command
line scripts by setting the values of some global variables.
This file is also used by the Jactl REPL.

At the moment, all the variables are to do with allowing you to customise Jactl by providing your own
execution environment (for your event-loop specific application environment) and your own functions/methods.
The values of the following variables can be set:

| Variable            | Type   |    Default Value    | Description                                                                                                                                                                                                                    |
|:--------------------|:-------|:-------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `environmentClass`  | String | `io.jactl.DefaultEnv` | The name of the class which will is used to encapsulate the Jactl runtime environment. See [Integration Guide](integration-guide) for more details.                                                                            |
| `extraJars`         | List   |        `[]`         | A list of file names for any additional JARs that should be added to the classpath.                                                                                                                                            |  
| `functionClasses`   | List   |       `[]`          | A list of classes having a static method called `registerFunctions(JactlEnv env)` that will be invoked at start up. This allows you to dynamically add new functions (from one of the `extraJars` files) to the Jactl runtime. |

For example, there is a [jactl-vertx project](https://github.com/jaccomoc/jactl-vertx) for use when integrating
with a [Vert.x](https://vertx.io/) based application.
It uses a specific `JactlVertxEnv` environment that delegates event scheduling to Vert.x and provides some 
Json methods for converting to/from JSON and an example function for sending/receiving JSON messages over HTTP.

Since the `sendReceiveJson()` functions is provided as an example, it lives in the test jar of the jactl-vertx
project.
So to include these additional functions in your Jactl REPL or Jactl command line scripts you need to list
these two jars in the `extraJars` list.

> **Note**<br/>
> The `jactl-vertx` test jar is built as an "uber" jar and includes the dependencies it needs (including the
> Vert.x libraries) so we don't need to separately list the Vert.x jars as well.

To register these additional functions with the Jactl runtime we need to have created classes that have
a static method called `registerFunctions(JactlEnv env)` which do the registration of the functions.
We then need to tell the runtime the name of these classes so that these static methods can be invoked which
will in turn register the functions.

For the `jactl-vertx` library, there are two classes that handle the registration of these functions/methods:
* `jactl.vertx.JsonFunctions`
* `jactl.vertx.example.VertxFunctions`

We therefore need to list these classes in the `functionClasses` list of our `.jactlrc` file.

If the jars are located under `~/projects/jactl-vertx/{{ site.content.jactl_version }}` then a `.jactlrc` file that allows
the Jactl REPL and the Jactl commandline script execution to use Vert.x and these new functions would look like this:
```groovy
environmentClass = 'jactl.vertx.JactlVertxEnv'
extraJars        = [ '~/projects/jactl-vertx/{{ site.content.jactl_version }}/jactl-vertx-{{ site.content.jactl_version }}.jar',
                     '~/projects/jactl-vertx/{{ site.content.jactl_version }}/jactl-vertx-{{ site.content.jactl_version }}-tests.jar' ]
functionClasses  = [ 'jactl.vertx.JsonFunctions',
                     'jactl.vertx.example.VertxFunctions' ]
```

> **Note**<br/>
> The use of `~` in the file names will be replaced with the location of the current user's home directory.

Since the file is Jactl code we could also write it like this:
```groovy
def VERSION = '{{ site.content.jactl_version }}'     // The jactl-vertx version to use
def LIBS    = "~/projects/jactl-vertx/${VERSION}"    // Location of the jars

// Specify the Vertx environment class to use
environmentClass = 'jactl.vertx.JactlVertxEnv'

// List the extra jactl-vertx jars
extraJars        = [ "$LIBS/jactl-vertx-${VERSION}.jar",
                     "$LIBS/jactl-vertx-${VERSION}-tests.jar" ]

// List the function registration classes
functionClasses  = [ 'jactl.vertx.JsonFunctions',
                     'jactl.vertx.example.VertxFunctions' ]
```

> **Note**<br/>
> The extra jars can also be provided via the normal way of specifying in them your Java classpath if you
> prefer to do it that way.

To integrate with additional libraries, just add the jars to the `extraJars` list and add any function
registration classes to the `functionClasses` list.
