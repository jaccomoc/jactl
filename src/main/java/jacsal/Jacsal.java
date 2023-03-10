/*
 * Copyright 2022 James Crawford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jacsal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Jacsal {
  public static void main(String[] args) throws IOException {
    String usage = "Usage: jacsal [switches] [programFile] [inputFile]* [--] [arguments]* \n" +
                   "         -p           : run in a print loop reading input from stdin or files\n" +
                   "         -n           : run in a loop but don't print each line\n" +
                   "         -e script    : script string is interpreted as jacsal code (programFile not used)\n" +
                   "         -V var=value : initialise jacsal variable before running script\n" +
                   "         -d           : debug: output generated code\n" +
                   "         -h           : print this help\n";
    var argMap = Utils.parseArgs(args, "d*pne:V:*",
                                 usage);
    var files     = (List<String>) argMap.get('*');
    var arguments = (List<String>) argMap.get('@');
    String script;
    if (argMap.containsKey('e')) {
      script = (String) argMap.get('e');
    }
    else {
      if (files.size() == 0) {
        System.out.println(usage);
        throw new IllegalArgumentException("Missing '-e' option and no programFile specified");
      }
      String file = files.remove(0);
      script = Files.readString(Path.of(file));
    }
    if (files.size() > 0 && !argMap.containsKey('p') && !argMap.containsKey('n')) {
      // If no '-p' or '-n' then "files" are just arguments to our script
      arguments = Stream.concat(files.stream(), arguments.stream()).collect(Collectors.toList());
    }
    Map<String,Object> globals   = new HashMap<>();
    if (argMap.containsKey('V')) {
      List<String> variables = (List<String>) argMap.get('V');
      variables.forEach(varValue -> {
        int    index = varValue.indexOf('=');
        String name  = index == -1 ? varValue : varValue.substring(0, index);
        validateName(name);
        Object value = index == -1 ? Boolean.TRUE : varValue.substring(index + 1);
        globals.put(name, value);
      });
    }

    globals.put("args", arguments);
    globals.put("input", new BufferedReader(new InputStreamReader(System.in)));
    boolean[] printInvoked = new boolean[]{ false };
    globals.put("output", new PrintStream(System.out) {
      @Override public void print(String s) {
        super.print(s);
        printInvoked[0] = true;
      }

      @Override public void println(String x) {
        super.println(x);
        super.flush();
        printInvoked[0] = true;
      }
    });

    try {
      var context = JacsalContext.create()
                                 .replMode(argMap.containsKey('e'))
                                 .debug(argMap.containsKey('d') ? (int)argMap.get('d') : 0)
                                 .printLoop(argMap.containsKey('p'))
                                 .nonPrintLoop(argMap.containsKey('n'))
                                 .build();
      var result = Compiler.run(script, context, globals);

      // Print result only if non-null and if we aren't in a stdin loop and script hasn't invoked print itself
      if (!context.printLoop() && !context.nonPrintLoop && result != null && !printInvoked[0]) {
        System.out.println(result);
      }
      System.exit(0);
    }
    catch (CompileError e) {
      System.err.println(e);
      System.exit(1);
    }
  }

  private static void validateName(String name) {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Variable name cannot be empty");
    }
    char start = name.charAt(0);
    if (!Character.isJavaIdentifierStart(start) || start == '$') {
      throw new IllegalArgumentException("Variable name cannot start with '" + start + "'");
    }
    for (int i = 1; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!Character.isJavaIdentifierPart(c) || c == '$') {
        throw new IllegalArgumentException("Variable name cannot contain '" + c + "'");
      }
    }
  }
}