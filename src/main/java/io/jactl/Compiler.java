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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    JactlScript compiled = compileScript(source, jactlContext, bindings);
    return compiled.runSync(bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, Map<String, Object> bindings) {
    String className = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(source);
    return compileScript(source, jactlContext, className, Utils.DEFAULT_JACTL_PKG, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String packageName, Map<String, Object> bindings) {
    String className = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(source);
    return compileScript(source, jactlContext, className, packageName, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String className, String packageName, Map<String, Object> bindings) {
    Parser         parser = new Parser(new Tokeniser(source), jactlContext, packageName);
    Stmt.ClassDecl script = parser.parseScript(className);
    Resolver       resolver = new Resolver(jactlContext, bindings, script.location);
    resolver.resolveScript(script);
    Analyser analyser = new Analyser(jactlContext);
    analyser.analyseClass(script);
    return compileWithCompletion(source, jactlContext, script);
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
    Parser         parser      = new Parser(new Tokeniser(source), jactlContext, packageName);
    Stmt.ClassDecl scriptClass = parser.parseClass();
    Resolver       resolver    = new Resolver(jactlContext, Utils.mapOf(), scriptClass.location);
    resolver.resolveClass(scriptClass);
    Analyser analyser = new Analyser(jactlContext);
    analyser.analyseClass(scriptClass);
    compileClass(source, jactlContext, packageName, scriptClass);
  }

  static JactlScript compileWithCompletion(String source, JactlContext jactlContext, Stmt.ClassDecl script) {
    ScriptCompiler compiler = new ScriptCompiler(source, jactlContext, script);
    return compiler.compileWithCompletion();
  }

  static void compileClass(String source, JactlContext jactlContext, String packageName, Stmt.ClassDecl clss) {
    ClassCompiler compiler = new  ClassCompiler(source, jactlContext, packageName, clss, clss.name.getStringValue() + ".jactl");
    compiler.compileClass();
  }
}
