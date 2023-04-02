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

package jacsal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class Compiler {

  private static final AtomicInteger counter = new AtomicInteger();

  public static Object run(String source, Map<String,Object> bindings) {
    return run(source, JacsalContext.create().build(), bindings);
  }

  public static Object run(String source, JacsalContext jacsalContext, String packageName, Map<String,Object> bindings) {
    var compiled = compileScript(source, jacsalContext, packageName, bindings);
    return compiled.runSync(bindings);
  }

  public static Object run(String source, JacsalContext jacsalContext, Map<String,Object> bindings) {
    var compiled = compileScript(source, jacsalContext, bindings);
    return compiled.runSync(bindings);
  }

  public static void compileClass(String source, JacsalContext jacsalContext) {
    compileClass(source, jacsalContext, null);
  }

  public static JacsalScript compileScript(String source, JacsalContext jacsalContext, Map<String, Object> bindings) {
    String className = Utils.JACSAL_SCRIPT_PREFIX + counter.incrementAndGet();
    return compileScript(source, jacsalContext, className, Utils.DEFAULT_JACSAL_PKG, false, bindings);
  }

  public static JacsalScript compileScript(String source, JacsalContext jacsalContext, String packageName, Map<String, Object> bindings) {
    String className = Utils.JACSAL_SCRIPT_PREFIX + counter.incrementAndGet();
    return compileScript(source, jacsalContext, className, packageName, bindings);
  }

  public static JacsalScript compileScript(String source, JacsalContext jacsalContext, String className, String packageName, Map<String, Object> bindings) {
    return compileScript(source, jacsalContext, className, packageName, false, bindings);
  }

  public static JacsalScript compileScript(String source, JacsalContext jacsalContext, String className, String packageName, boolean testAsync, Map<String, Object> bindings) {
    var parser   = new Parser(new Tokeniser(source), jacsalContext, packageName);
    var script   = parser.parseScript(className);
    var resolver = new Resolver(jacsalContext, bindings);
    resolver.resolveScript(script);
    var analyser = new Analyser(jacsalContext);
    analyser.testAsync = testAsync;
    analyser.analyseClass(script);
    return compileWithCompletion(source, jacsalContext, script);
  }

  /**
   * For internal use by eval() function.
   */
  public static Function<Map<String, Object>,Object> compileScriptInternal(String source, JacsalContext jacsalContext, String packageName, Map<String, Object> bindings) {
    String className = Utils.JACSAL_SCRIPT_PREFIX + counter.incrementAndGet();
    var parser   = new Parser(new Tokeniser(source), jacsalContext, packageName);
    var script   = parser.parseScript(className);
    var resolver = new Resolver(jacsalContext, bindings);
    resolver.resolveScript(script);
    var analyser = new Analyser(jacsalContext);
    analyser.analyseClass(script);
    var compiler = new ScriptCompiler(source, jacsalContext, script);
    return compiler.compile();
  }

  public static void compileClass(String source, JacsalContext jacsalContext, String packageName) {
    var parser      = new Parser(new Tokeniser(source), jacsalContext, packageName);
    var scriptClass = parser.parseClass();
    var resolver = new Resolver(jacsalContext, Map.of());
    resolver.resolveClass(scriptClass);
    var analyser = new Analyser(jacsalContext);
    analyser.analyseClass(scriptClass);
    compileClass(source, jacsalContext, packageName, scriptClass);
  }

  static JacsalScript compileWithCompletion(String source, JacsalContext jacsalContext, Stmt.ClassDecl script) {
    var compiler = new ScriptCompiler(source, jacsalContext, script);
    return compiler.compileWithCompletion();
  }

  static void compileClass(String source, JacsalContext jacsalContext, String packageName, Stmt.ClassDecl clss) {
    var compiler = new  ClassCompiler(source, jacsalContext, packageName, clss, clss.name.getStringValue() + ".jacsal");
    compiler.compileClass();
  }
}
