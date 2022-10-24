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

package pragma;

import pragma.runtime.BuiltinFunctions;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class Compiler {

  static {
    BuiltinFunctions.registerBuiltinFunctions();
  }

  private static final AtomicInteger counter = new AtomicInteger();

  public static Object run(String source, Map<String,Object> bindings) {
    return run(source, PragmaContext.create().build(), bindings);
  }

  static Object run(String source, PragmaContext pragmaContext, boolean testAsync, Map<String,Object> bindings) {
    Function<Map<String,Object>,Future<Object>> compiled = compile(source, pragmaContext, testAsync, bindings);
    return runSync(compiled, bindings);
  }

  public static Object run(String source, PragmaContext pragmaContext, Map<String,Object> bindings) {
    Function<Map<String,Object>,Future<Object>> compiled = compile(source, pragmaContext, false, bindings);
    return runSync(compiled, bindings);
  }

  public static Function<Map<String, Object>,Future<Object>> compile(String source, PragmaContext pragmaContext, Map<String, Object> bindings) {
    return compile(source, pragmaContext, false, bindings);
  }

  private static Function<Map<String, Object>,Future<Object>> compile(String source, PragmaContext pragmaContext, boolean testAsync, Map<String, Object> bindings) {
    var parser   = new Parser(new Tokeniser(source));
    var script   = parser.parse();
    var resolver = new Resolver(pragmaContext, bindings);
    resolver.testAsync = testAsync;
    resolver.resolve(script);
    script.name = new Token(TokenType.IDENTIFIER, script.name).setValue("PragmaScript_" + counter.incrementAndGet());
    return compile(source, pragmaContext, script);
  }

  private static Function<Map<String,Object>,Future<Object>> compile(String source, PragmaContext pragmaContext, Stmt.ClassDecl script) {
    var compiler = new ClassCompiler(source, pragmaContext, script);
    return compiler.compile();
  }

  public static Object runSync(Function<Map<String,Object>,Future<Object>> script, Map<String,Object> globals) {
    Future<Object> future = script.apply(globals);
    Object result = null;
    try {
      result = future.get();
    }
    catch (InterruptedException|ExecutionException e) {
      throw new IllegalStateException("Internal error: " + e.getMessage(), e);
    }
    if (result instanceof RuntimeException) {
      throw (RuntimeException)result;
    }
    return result;
  }
}
