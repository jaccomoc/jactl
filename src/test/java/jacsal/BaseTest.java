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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class BaseTest {
  private   static int scriptNum = 1;
  protected int                debugLevel = 0;
  protected String             packagName = Utils.DEFAULT_JACSAL_PKG;
  protected Map<String,Object> globals    = new HashMap<String,Object>();
  protected boolean            useAsyncDecorator = true;

  protected void doTest(String code, Object expected) {
    doTest(code, true, false, false, expected);
  }

  protected void doTest(String code, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    try {
      JacsalContext jacsalContext = JacsalContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debugLevel)
                                                 .build();

      var    bindings = createGlobals();
      //var    compiled = Compiler.compileScript(code, jacsalContext, packagName, testAsync, bindings);
      var    compiled = compileScript(code, jacsalContext, "Script" + scriptNum++, packagName, testAsync, bindings);
      Object result   = Compiler.runSync(compiled, bindings);
      if (expected instanceof Object[]) {
        assertTrue(result instanceof Object[]);
        assertTrue(Arrays.equals((Object[]) expected, (Object[]) result));
      }
      else {
        assertEquals(expected, result);
      }
    }
    catch (Exception e) {
      fail(e);
      e.printStackTrace();
    }
  }

  public Function<Map<String, Object>,Future<Object>> compileScript(String source, JacsalContext jacsalContext, String className, String packageName, boolean testAsync, Map<String, Object> bindings) {
    var parser   = new Parser(new Tokeniser(source), jacsalContext, packageName);
    var script   = parser.parse(className);
    if (testAsync) {
      ExprDecorator exprDecorator = !useAsyncDecorator ? new ExprDecorator(Function.identity())
                                         : new ExprDecorator(
        expr -> {
          if (expr instanceof Expr.VarDecl || expr instanceof Expr.Noop ||
              expr instanceof Expr.TypeExpr || expr instanceof Expr.FunDecl ||
              expr instanceof Expr.MapLiteral && ((Expr.MapLiteral)expr).isNamedArgs ||
              expr instanceof Expr.Binary && ((Expr.Binary)expr).createIfMissing ||
              expr instanceof Expr.InvokeNew) {
            return expr;
          }
          Expr newExpr = new Expr.Call(expr.location,
                                       new Expr.Identifier(expr.location.newIdent("sleep")),
                                       List.of(new Expr.Literal(new Token(TokenType.INTEGER_CONST, expr.location).setValue(0)),
                                               expr));
          newExpr.isResultUsed = expr.isResultUsed;
          expr.isResultUsed = true;
          return newExpr;
        });
      exprDecorator.decorate(script);
    }
    var resolver = new Resolver(jacsalContext, bindings);
    resolver.resolveScript(script);
    var analyser = new Analyser();
    //analyser.testAsync = testAsync;
    analyser.analyseScript(script);
    return Compiler.compile(source, jacsalContext, script);
  }

  protected void test(String code, Object expected) {
    doTest(code, true, false, false, expected);
    doTest(code, false, false, false, expected);
    doTest(code, true, true, false, expected);
    doTest(code, false, true, false, expected);
    doTest(code, true, false, true, expected);
    doTest(code, true, true, true, expected);
  }

  protected void testError(String code, String expectedError) {
    doTestError(code, true, false, expectedError);
    doTestError(code, false, false, expectedError);

    // Tests in repl mode may have different errors or no errors compared
    // to normal mode when accessing global vars so we can't run tests that
    // generate an error and expect the same results
    //    doTestError(code, true, true, expectedError);
    //    doTestError(code, false, true, expectedError);
  }

  private void doTestError(String code, boolean evalConsts, boolean replMode, String expectedError) {
    try {
      JacsalContext jacsalContext = JacsalContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debugLevel)
                                                 .build();
      Compiler.run(code, jacsalContext, createGlobals());
      fail("Expected JacsalError");
    }
    catch (JacsalError e) {
      if (!e.getMessage().toLowerCase().contains(expectedError.toLowerCase())) {
        e.printStackTrace();
        fail("Message did not contain expected string '" + expectedError + "'. Message=" + e.getMessage());
      }
    }
  }

  protected Map<String, Object> createGlobals() {
    var map = new HashMap<String, Object>();
    map.putAll(globals);
    return map;
  }

  protected Function<Map<String, Object>, Future<Object>> compile(String code) {
    JacsalContext jacsalContext = JacsalContext.create()
                                               .evaluateConstExprs(true)
                                               .replMode(true)
                                               .debug(0)
                                               .build();
    Map<String, Object> globals = new HashMap<>();
    return Compiler.compileScript(code, jacsalContext, globals);
  }
}
