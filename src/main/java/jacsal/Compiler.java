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
import java.util.function.Function;

public class Compiler {

  public static Object run(String source, Map<String,Object> bindings) {
    Parser parser = new Parser(new Tokeniser(source));
    Expr   expr   = parser.parseExpression();
    expr.accept(new Resolver());
    Function<Map<String,Object>,Object> compiled = compile(expr);
    return compiled.apply(bindings);
  }

  private static Function<Map<String,Object>,Object> compile(Expr expr) {
    ClassCompiler compiler = new ClassCompiler(new CompileContext(), expr);
    return compiler.compile();
  }
}
