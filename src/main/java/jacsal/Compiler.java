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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class Compiler {

  private static final AtomicInteger counter = new AtomicInteger();

  public static Object run(String source, Map<String,Object> bindings) {
    return run(source, new CompileContext(), bindings);
  }

  public static Object run(String source, CompileContext compileContext, Map<String,Object> bindings) {
    Function<Map<String, Object>, Object> compiled = compile(source, compileContext, bindings);
    return compiled.apply(bindings);
  }

  public static Function<Map<String, Object>, Object> compile(String source, CompileContext compileContext, Map<String, Object> bindings) {
    var parser   = new Parser(new Tokeniser(source));
    var script   = parser.parse();
    var resolver = new Resolver(compileContext, bindings);
    resolver.resolve(script);
    script.name = new Token(TokenType.IDENTIFIER, script.name).setValue("JacsalScript_" + counter.incrementAndGet());
    return compile(source, compileContext, script);
  }

  private static Function<Map<String,Object>,Object> compile(String source, CompileContext compileContext, Stmt.ClassDecl script) {
    var compiler = new ClassCompiler(source, compileContext, script);
    return compiler.compile();
  }
}
