/*
 * Copyright © 2022,2023 James Crawford
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
 *
 */

package io.jactl;

import io.jactl.compiler.Compiler;
import io.jactl.runtime.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>The Jactl class is the main entry point into running and compiling Jactl scripts.
 * It provides a main() method for the commandline script execution utility as
 * well as methods for compiling Jactl scripts for use within a Java application.</p>
 * <p>For eval() and compileScript() calls that take a Map of global variables,
 * the values for the variables should be one of the following types:</p>
 * <ul>
 *   <li>Boolean</li>
 *   <li>Integer</li>
 *   <li>Long</li>
 *   <li>Double</li>
 *   <li>BigDecimal</li>
 *   <li>String</li>
 *   <li>List</li>
 *   <li>Map</li>
 *   <li>null - type of Object with value null</li>
 * </ul>
 * <p>Also supported are object instances of Jactl classes that have been returned
 * from a previous script invocation.</p>
 * <p>This class also provides the {@link #function()} and {@link #method(JactlType)} calls
 * for creating global functions methods to be registered with the Jactl runtime.</p>
 */
public class Jactl {

  /**
   * <p>Evaluate a Jactl script and return the result.</p>
   * <p>NOTE: the preferred way is to compile the script and then execute the script.
   * See {@link #compileScript(String, Map)}</p>
   * @param source  the source code to run
   * @return the result returned from the script
   * @throws CompileError if error during compile
   * @throws RuntimeError if error during invocation
   */
  public static Object eval(String source) {
    return eval(source, new LinkedHashMap<String,Object>());
  }

  /**
   * <p>Evaluate a Jactl script and return the result.</p>
   * <p>NOTE: the preferred way is to compile the script and then execute the script.
   * See {@link #compileScript(String, Map)}</p>
   * @param source  the source code to run
   * @param vars    a map of variables that the script can read and modify
   * @return the result returned from the script
   * @throws CompileError if error during compile
   * @throws RuntimeError if error during invocation
   */
  public static Object eval(String source, Map<String,Object> vars) {
    return Compiler.eval(source, JactlContext.create().build(), vars);
  }

  /**
   * <p>Evaluate a Jactl script and return the result.</p>
   * <p>NOTE: the preferred way is to compile the script and then execute the script.
   * See {@link #compileScript(String, Map)}</p>
   * @param source  the source code to run
   * @param vars    a map of variables that the script can read and modify
   * @param input   input to feed to script (if it uses nextLine())
   * @param output  where print/println output will go
   * @return the result returned from the script
   * @throws CompileError if error during compile
   * @throws RuntimeError if error during invocation
   */
  public static Object eval(String source, Map<String,Object> vars, BufferedReader input, PrintStream output) {
    return Compiler.eval(source, JactlContext.create().build(), vars, input, output);
  }

  /**
   * <p>Evaluate a Jactl script and return the result.</p>
   * <p>NOTE: the preferred way is to compile the script and then execute the script.
   * See {@link #compileScript(String, Map)}</p>
   * @param source  the source code to run
   * @param vars    a map of variables that the script can read and modify
   * @param context the JactlContext
   * @param input   input to feed to script (if it uses nextLine())
   * @param output  where print/println output will go
   * @return the result returned from the script
   * @throws CompileError if error during compile
   * @throws RuntimeError if error during invocation
   */
  public static Object eval(String source, Map<String,Object> vars, JactlContext context, BufferedReader input, PrintStream output) {
    return Compiler.eval(source, context, vars, input, output);
  }

  /**
   * <p>Evaluate a Jactl script and return the result.</p>
   * <p>NOTE: the preferred way is to compile the script and then execute the script.
   * See {@link #compileScript(String, Map, JactlContext)}</p>
   * @param source  the source code to run
   * @param vars    a map of variables that the script can read and modify
   * @param context the JactlContext
   * @return the result returned from the script
   * @throws CompileError if error during compile
   * @throws RuntimeError if error during invocation
   */
  public static Object eval(String source, Map<String,Object> vars, JactlContext context) {
    return Compiler.eval(source, context, vars);
  }

  /**
   * <p>Compile the given Jactl source code into a JactlScript object.</p>
   * <p>NOTE: the globals is only used at compile time whether a global variable exists.
   * The value of the globals at compile time is irrelevant.</p>
   * @param source   the source code
   * @param globals  Map of global variables the script can reference
   * @return a JactlScript object that can be invoked
   * @throws CompileError if error during compile
   */
  public static JactlScript compileScript(String source, Map<String, Object> globals) {
    return Compiler.compileScript(source, JactlContext.create().build(), Utils.DEFAULT_JACTL_PKG, globals);
  }

  /**
   * <p>Compile the given Jactl source code into a JactlScript object.</p>
   * <p>NOTE: the globals is only used at compile time whether a global variable exists.
   * The value of the globals at compile time is irrelevant.</p>
   * @param source   the source code
   * @param globals  Map of global variables the script can reference
   * @param context  the JactlContext
   * @return a JactlScript object that can be invoked
   * @throws CompileError if error during compile
   */
  public static JactlScript compileScript(String source, Map<String, Object> globals, JactlContext context) {
    return Compiler.compileScript(source, context, Utils.DEFAULT_JACTL_PKG, globals);
  }

  /**
   * <p>Compile the given Jactl source code into a JactlScript object.</p>
   * <p>NOTE: the globals is only used at compile time whether a global variable exists.
   * The value of the globals at compile time is irrelevant.</p>
   * <p>This method allows explicitly setting the Jactl package which the script lives in.
   * If script doesn't declare a package or declares a package of same name then the script
   * will be compiled into the given package. If the script declares a different package name
   * then this will result in a CompilerError.</p>
   * @param source   the source code
   * @param globals  Map of global variables the script can reference
   * @param context  the JactlContext
   * @param pkgName  the Jactl package to compile the script into
   * @return a JactlScript object that can be invoked
   * @throws CompileError if error during compile
   */
  public static JactlScript compileScript(String source, Map<String, Object> globals, JactlContext context, String pkgName) {
    return Compiler.compileScript(source, context, pkgName, globals);
  }

  /**
   * Compile a Jactl class. If no package declaration in the class then class will be
   * put into the root package.
   * @param source        the source code for the class declaration
   * @param jactlContext  the JactlContext
   * @throws CompileError if error during compile
   */
  public static void compileClass(String source, JactlContext jactlContext) {
    Compiler.compileClass(source, jactlContext, "");
  }

  /**
   * Compile a Jactl class.
   * <p>This method allows explicitly setting the Jactl package which the class will live in.
   * The pkgName can have one of the following values:</p>
   * <dl>
   *   <dt>null</dt><dd>Error if package not specified in class declaration.</dd>
   *   <dt>""</dt><dd>If package name not declared use "" as package name.</dd>
   *   <dt>anything else</dt><dd>If package name not declared use this as package name.
   *   If package declared it must match the given value.</dd>
   * </dl>
   * @param source        the source code for the class declaration
   * @param jactlContext  the JactlContext
   * @param pkgName       the Jactl package to compile the script into
   * @throws CompileError if error during compile
   */
  public static void compileClass(String source, JactlContext jactlContext, String pkgName) {
    Compiler.compileClass(source, jactlContext, pkgName);
  }

  /**
   * Compile a Jactl class.
   * <p>This method allows explicitly setting the Jactl package which the class will live in.
   * The pkgName can have one of the following values:</p>
   * <dl>
   *   <dt>null</dt><dd>Error if package not specified in class declaration.</dd>
   *   <dt>""</dt><dd>If package name not declared use "" as package name.</dd>
   *   <dt>anything else</dt><dd>If package name not declared use this as package name.
   *   If package declared it must match the given value.</dd>
   * </dl>
   * @param source        the source code for the class declaration
   * @param jactlContext  the JactlContext
   * @param pkgName       the Jactl package to compile the script into
   * @param globals       Map of global variables the class can access
   * @throws CompileError if error during compile
   */
  public static void compileClass(String source, JactlContext jactlContext, String pkgName, Map<String, Object> globals) {
    Compiler.compileClass(source, jactlContext, pkgName, globals);
  }

  /**
   * Create a JactlFunction for a global function to be registered.
   * @return the new JactlFunction object
   */
  public static JactlFunction function() {
    return new JactlFunction();
  }

  /**
   * <p>Create a JactlFunction for a method that will be registered.
   * The type should be one of:</p>
   * <ul>
   *   <li>JactlType.ANY</li>
   *   <li>JactlType.STRING</li>
   *   <li>JactlType.LIST</li>
   *   <li>JactlType.MAP</li>
   *   <li>JactlType.ITERATOR</li>
   *   <li>JactlType.BOOLEAN</li>
   *   <li>JactlType.INT</li>
   *   <li>JactlType.LONG</li>
   *   <li>JactlType.DOUBLE</li>
   *   <li>JactlType.DECIMAL</li>
   *   <li>JactlType.NUMBER</li>
   * </ul>
   * @param type  the type for which the method applies
   * @return the new JactlFunction object
   */
  public static JactlFunction method(JactlType type) {
    return new JactlFunction(type);
  }

  /**
   * Deregister a method
   * @param type   the type which owns the method
   * @param name   the name of the method
   */
  public static void deregister(JactlType type, String name) {
    Functions.INSTANCE.deregisterFunction(type, name);
  }

  /**
   * Deregister a global function
   * @param name  the name of the global function
   */
  public static void deregister(String name) {
    Functions.INSTANCE.deregisterFunction(name);
  }

  ////////////////////////////////////////////

  /**
   * Mainline for running Jactl commandline scripts
   * @param args   - run with no args to get a summary of usage
   * @throws IOException if an error occurs
   */
  public static void main(String[] args) throws IOException {
    new Jactl().run(args);
  }

  ////////////////////////////////////

  final static String usage =
    "Usage: jactl [options] [programFile] [inputFile]* [--] [arguments]* \n" +
    "         -p               : run in a print loop reading input from stdin or files\n" +
    "         -n               : run in a loop without printing each line\n" +
    "         -e script        : script string is interpreted as Jactl code (programFile not used)\n" +
    "         -v               : show verbose errors (give stack trace)\n" +
    "         -V var=value     : initialise Jactl variable before running script\n" +
    "         -P path,path,... : paths to package root for importing classes\n" +
    "         -k pkgName       : run script as though it belongs to this package\n" +
    "         -d               : debug: output generated code\n" +
    "         -c               : do not read .jactlrc config file\n" +
    "         -g path          : run script to get map of global variable values\n" +
    "         -h               : print this help\n";

  JactlContext context;
  boolean      verbose;

  private void run(String[] args) throws IOException {
    final Map<Character, Object> argMap = Utils.parseArgs(args, "d*vcCpng:e:P:V:*", usage);
    List<String> files     = (List<String>) argMap.get('*');
    List<String> arguments = (List<String>) argMap.get('@');
    String       script    = null;
    verbose = argMap.containsKey('v');
    String scriptClassName;
    if (argMap.containsKey('e')) {
      script = (String) argMap.get('e');
      scriptClassName = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(script);
    }
    else {
      if (files.size() == 0) {
        System.out.println(usage);
        throw new IllegalArgumentException("Missing '-e' option and no programFile specified");
      }
      String file = files.remove(0);
      if (argMap.containsKey('C')) {
        // If -C option then programFile is actually a class name
        scriptClassName = file;
      }
      else {
        script = new String(Files.readAllBytes(Paths.get(file)));
        scriptClassName = file.replaceAll("^.*" + (File.separatorChar == '\\' ? "\\\\" : File.separator), "")
                              .replaceAll("\\.[^\\.]*$", "");
      }
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

    try {
      if (!argMap.containsKey('c')) {
        JactlOptions.initOptions();
      }

      globals.put("args", arguments);
      List<InputStream> fileStreams = files.stream().map(Jactl::getFileStream).collect(Collectors.toList());
      InputStream inputStream = fileStreams.size() > 0 ? new SequenceInputStream(Collections.enumeration(fileStreams))
                                                       : System.in;
      BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
      boolean[] printInvoked = new boolean[]{ false };
      PrintStream output = new PrintStream(System.out) {
        @Override
        public void print(String s) {
          super.print(s);
          printInvoked[0] = true;
        }

        @Override
        public void println(String x) {
          super.println(x);
          super.flush();
          printInvoked[0] = true;
        }
      };

      JactlContext.JactlContextBuilder builder = JactlContext.create()
                                                             .replMode(argMap.containsKey('p') || argMap.containsKey('n'))
                                                             .debug(argMap.containsKey('d') ? (int)argMap.get('d') : 0)
                                                             .printLoop(argMap.containsKey('p'))
                                                             .nonPrintLoop(argMap.containsKey('n'));

      if (argMap.containsKey('P')) {
        String[] paths = ((String)argMap.get('P')).split(",");
        List<File> roots = Arrays.stream(paths).map(p -> {
          File root = new File(p);
          if (!root.exists()) {
            error("Class package root '" + p + "' does not exist");
          }
          if (!root.isDirectory()) {
            error("Class package root '" + p + "' is not a directory");
          }
          return root;
        }).collect(Collectors.toList());
        builder = builder.packageChecker(pkgName -> roots.stream().anyMatch(root -> {
                           File file = new File(root, pkgName.replace('.', File.separatorChar));
                           return file.exists() && file.isDirectory();
                         }))
                         .classLookup(name -> lookup(name, roots));
      }

      context = builder.build();

      if (argMap.containsKey('g')) {
        String globalsScriptFile = (String) argMap.get('g');
        String globalsScript = new String(Files.readAllBytes(Paths.get(globalsScriptFile)));
        try {
          Object globalsObj = Jactl.eval(globalsScript, globals, context);
          if (globalsObj != null && !(globalsObj instanceof Map)) {
            error("Script '" + globalsScriptFile + "' for global variables returned non-map object of type: " + RuntimeUtils.className(globalsObj));
          }
          globals.putAll((Map) globalsObj);
        }
        catch (CompileError e) {
          error("Error in globals script file '" + globalsScriptFile + "': " + e.getErrors().get(0).getMessage());
        }
      }

      Object result = null;
      BuiltinFunctions.registerBuiltinFunctions();
      if (argMap.containsKey('C')) {
        Class           clazz     = Class.forName(scriptClassName);
        AtomicReference resultRef = new AtomicReference();
        Function<Map<String,Object>,Object> invoker = JactlScript.createInvoker(clazz, context);
        JactlScript     jactlScript = JactlScript.createScript(invoker, context);

        result = jactlScript.runSync(globals, input, output);
      }
      else {
        JactlScript compiled = Compiler.compileScript(script, context, scriptClassName, argMap.containsKey('k') ? (String) argMap.get('k') : Utils.DEFAULT_JACTL_PKG, globals);
        result = compiled.runSync(globals, input, output);
      }

      // Print result only if non-null and if we aren't in a stdin loop and script hasn't invoked print itself
      if (!context.printLoop() && !context.nonPrintLoop && result != null && !printInvoked[0]) {
        System.out.println(result);
      }
    }
    catch (Throwable t) {
      error(t);
    }
    System.exit(0);
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

  private static InputStream getFileStream(String name) {
    try {
      return name.equals("-") ? System.in : new FileInputStream(name);
    }
    catch (FileNotFoundException e) {
      System.err.println("Error reading " + name + ": " + e.getMessage());
      System.exit(1);
      return null;
    }
  }

  private void error(Throwable t) {
    if (t instanceof CompileError) {
      error(((CompileError)t).getMessage(verbose));
    }
    if (verbose) {
      t.printStackTrace(System.err);
      System.exit(1);
    }
    else {
      error(t.getClass().getName() + ": " + t.getMessage());
    }
  }

  private static void error(String msg) {
    System.err.println(msg);
    System.exit(1);
  }

  private ClassDescriptor lookup(String internalName, List<File> roots) {
    ClassDescriptor classDescriptor = context.getExistingClassDescriptor(internalName);
    if (classDescriptor != null) {
      return classDescriptor;
    }

    String jactlName;
    if (internalName.startsWith(context.internalJavaPackage)) {
      jactlName = internalName.substring(context.internalJavaPackage.length() + 1);
    }
    else {
      throw new IllegalStateException("Class package must begin with " + context.internalJavaPackage + " but was '" + internalName + "'");
    }

    // Get base a.b.c.X name without any inner class names
    int dollarIdx = jactlName.indexOf('$');
    String fileClass = jactlName;
    if (dollarIdx != -1) {
      fileClass = jactlName.substring(0, dollarIdx);
    }
    fileClass = fileClass + ".jactl";

    String fileName = fileClass;
    File file = roots.stream()
                     .map(root -> new File(root,fileName))
                     .filter(f -> f.exists() && !f.isDirectory())
                     .findFirst()
                     .orElse(null);

    if (file == null) {
      return null;
    }

    try {
      String text = new String(Files.readAllBytes(file.toPath()));

      // Compile the class since we haven't yet encountered it
      Jactl.compileClass(text, context);
      return context.getExistingClassDescriptor(internalName);
    }
    catch (CompileError e) {
      error(e.getMessage(verbose));
    }
    catch (Throwable t) {
      error(t.getMessage());
    }
    return null;
  }
}
