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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class Compiler {

  private static final AtomicInteger counter = new AtomicInteger();

  public static Object eval(String source, JactlContext jactlContext, String packageName, Map<String,Object> bindings) {
    var compiled = compileScript(source, jactlContext, packageName, bindings);
    return compiled.runSync(bindings);
  }

  public static Object eval(String source, JactlContext jactlContext, Map<String,Object> bindings) {
    var compiled = compileScript(source, jactlContext, bindings);
    return compiled.runSync(bindings);
  }

  public static void compileClass(String source, JactlContext jactlContext) {
    compileClass(source, jactlContext, null);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, Map<String, Object> bindings) {
    String className = Utils.JACTL_SCRIPT_PREFIX + counter.incrementAndGet();
    return compileScript(source, jactlContext, className, Utils.DEFAULT_JACTL_PKG, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String packageName, Map<String, Object> bindings) {
    String className = Utils.JACTL_SCRIPT_PREFIX + counter.incrementAndGet();
    return compileScript(source, jactlContext, className, packageName, bindings);
  }

  public static JactlScript compileScript(String source, JactlContext jactlContext, String className, String packageName, Map<String, Object> bindings) {
    var parser   = new Parser(new Tokeniser(source), jactlContext, packageName);
    var script   = parser.parseScript(className);
    var resolver = new Resolver(jactlContext, bindings);
    resolver.resolveScript(script);
    var analyser = new Analyser(jactlContext);
    analyser.analyseClass(script);
    return compileWithCompletion(source, jactlContext, script);
  }

  /**
   * For internal use by eval() function.
   */
  public static Function<Map<String, Object>,Object> compileScriptInternal(String source, JactlContext jactlContext, String packageName, Map<String, Object> bindings) {
    String className = Utils.JACTL_SCRIPT_PREFIX + counter.incrementAndGet();
    var parser   = new Parser(new Tokeniser(source), jactlContext, packageName);
    var script   = parser.parseScript(className);
    var resolver = new Resolver(jactlContext, bindings);
    resolver.resolveScript(script);
    var analyser = new Analyser(jactlContext);
    analyser.analyseClass(script);
    var compiler = new ScriptCompiler(source, jactlContext, script);
    return compiler.compile();
  }

  public static void compileClass(String source, JactlContext jactlContext, String packageName) {
    var parser      = new Parser(new Tokeniser(source), jactlContext, packageName);
    var scriptClass = parser.parseClass();
    var resolver = new Resolver(jactlContext, Map.of());
    resolver.resolveClass(scriptClass);
    var analyser = new Analyser(jactlContext);
    analyser.analyseClass(scriptClass);
    compileClass(source, jactlContext, packageName, scriptClass);
  }

  static JactlScript compileWithCompletion(String source, JactlContext jactlContext, Stmt.ClassDecl script) {
    var compiler = new ScriptCompiler(source, jactlContext, script);
    return compiler.compileWithCompletion();
  }

  static void compileClass(String source, JactlContext jactlContext, String packageName, Stmt.ClassDecl clss) {
    var compiler = new  ClassCompiler(source, jactlContext, packageName, clss, clss.name.getStringValue() + ".jactl");
    compiler.compileClass();
  }
}
