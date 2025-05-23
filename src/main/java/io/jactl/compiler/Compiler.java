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

package io.jactl.compiler;

import io.jactl.*;
import io.jactl.resolver.Resolver;
import io.jactl.runtime.ClassDescriptor;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Function;

/**
 * Internal class for invoking Jactl compiler. The methods in the {@link Jactl} class should be used instead
 * for compiling and running scripts.
 */
public class Compiler {
  public static Object eval(String source, JactlContext jactlContext, String packageName, Map<String,Object> bindings) {
    JactlScript compiled = compileScript(source, jactlContext, packageName, bindings);
    return compiled.runSync(bindings);
  }

  public static Object eval(String source, JactlContext jactlContext, Map<String,Object> bindings) {
    return eval(source, jactlContext, bindings, null, null);
  }
  public static Object eval(String source, JactlContext jactlContext, Map<String,Object> bindings, BufferedReader input, PrintStream output) {
    JactlScript compiled = compileScript(source, jactlContext, bindings);
    return compiled.runSync(bindings, input, output);
  }

  public static Object eval(String source, JactlContext jactlContext, String scriptClassName, String packageName, Map<String,Object> bindings) {
    JactlScript compiled = compileScript(source, jactlContext, scriptClassName, packageName, bindings);
    return compiled.runSync(bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, Map<String, Object> bindings) {
    return compileScript(source, jactlContext, null, Utils.DEFAULT_JACTL_PKG, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String packageName, Map<String, Object> bindings) {
    return compileScript(source, jactlContext, null, packageName, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String className, String packageName, Map<String, Object> bindings) {
    ClassDescriptor descriptor = parseAndResolve(source, jactlContext, className, packageName, bindings);
    return compileClass(descriptor, jactlContext);
  }

  public static ClassDescriptor parseAndResolve(String source, JactlContext jactlContext, String className, String packageName, Map<String, Object> bindings) {
    if (className == null) {
      className = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(source);
    }
    Parser         parser   = new Parser(new Tokeniser(source, true), jactlContext, packageName);
    Stmt.ClassDecl script   = parser.parseScript(className);
    Resolver       resolver = new Resolver(jactlContext, bindings, script.location);
    resolver.resolveScript(script);
    return script.classDescriptor;
  }

  private static JactlScript compileClass(ClassDescriptor descriptor, JactlContext jactlContext) {
    Analyser analyser = new Analyser(jactlContext);
    Stmt.ClassDecl script = descriptor.getUserData(Stmt.ClassDecl.class);
    analyser.analyseClass(script);
    return compileWithCompletion(script.location.getSource(), jactlContext, script);
  }

  // For internal use by eval() function.
  public static Function<Map<String, Object>,Object> compileScriptInternal(String source, JactlContext jactlContext, String packageName, Map<String, Object> bindings) {
    String         className = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(source);
    Parser         parser    = new Parser(new Tokeniser(source), jactlContext, packageName);
    Stmt.ClassDecl script    = parser.parseScript(className);
    Resolver       resolver = new Resolver(jactlContext, bindings, script.location);
    resolver.resolveScript(script);
    Analyser analyser = new Analyser(jactlContext);
    analyser.analyseClass(script);
    ScriptCompiler compiler = new ScriptCompiler(source, jactlContext, script);
    return compiler.compile();
  }

  public static void compileClass(String source, JactlContext jactlContext, String packageName) {
    compileClass(source, jactlContext, packageName, Utils.mapOf());
  }

  public static void compileClass(String source, JactlContext jactlContext, String packageName, Map<String, Object> globals) {
    Parser         parser      = new Parser(new Tokeniser(source), jactlContext, packageName);
    Stmt.ClassDecl scriptClass = parser.parseClass();
    Resolver       resolver    = new Resolver(jactlContext, globals, scriptClass.location);
    resolver.resolveClass(scriptClass);
    Analyser analyser = new Analyser(jactlContext);
    analyser.analyseClass(scriptClass);
    compileClass(source, jactlContext, packageName, scriptClass);
  }

  public static JactlScript compileWithCompletion(String source, JactlContext jactlContext, Stmt.ClassDecl script) {
    ScriptCompiler compiler = new ScriptCompiler(source, jactlContext, script);
    return compiler.compileWithCompletion();
  }

  public static void compileClass(String source, JactlContext jactlContext, String packageName, Stmt.ClassDecl clss) {
    ClassCompiler compiler = new ClassCompiler(source, jactlContext, packageName, clss, clss.name.getStringValue() + ".jactl");
    compiler.compileClass();
  }
}
