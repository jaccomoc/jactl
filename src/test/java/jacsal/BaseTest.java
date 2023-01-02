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
  protected int                debugLevel  = 0;
  protected String             packageName = Utils.DEFAULT_JACSAL_PKG;
  protected Map<String,Object> globals     = new HashMap<String,Object>();
  protected boolean            useAsyncDecorator = true;
  protected boolean            replMode = true;

  protected void doTest(String code, Object expected) {
    doTest(code, true, false, false, expected);
  }

  protected void doTest(String code, Object expected, boolean testAsync) {
    doTest(code, true, false, testAsync, expected);
  }

  protected void doTest(String code, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
    doTest(List.of(), code, evalConsts, replMode, testAsync, expected);
  }

  protected void doTest(List<String> classCode, String scriptCode, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
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

      classCode.forEach(code -> compileClass(code, jacsalContext, packageName, testAsync));

      var    compiled = compileScript(scriptCode, jacsalContext, "Script" + scriptNum++, packageName, testAsync, bindings);

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

  public void compileClass(String source, JacsalContext jacsalContext, String packageName, boolean testAsync) {
    doCompile(false, source, jacsalContext, null, packageName, testAsync, null);
  }

  public Function<Map<String, Object>,Future<Object>> compileScript(String source, JacsalContext jacsalContext, String className, String packageName, boolean testAsync, Map<String, Object> bindings) {
    return doCompile(true, source, jacsalContext, className, packageName, testAsync, bindings);
  }

  public Function<Map<String, Object>,Future<Object>> doCompile(boolean isScript, String source, JacsalContext jacsalContext, String className, String packageName, boolean testAsync, Map<String, Object> bindings) {
    var parser = new Parser(new Tokeniser(source), jacsalContext, packageName);
    var code   = isScript ? parser.parseScript(className) : parser.parseClass();
    if (testAsync) {
      var exprDecorator = !useAsyncDecorator ? new ExprDecorator(Function.identity())
                                             : new ExprDecorator(
        expr -> {
          assert expr != null;
          if (expr instanceof Expr.VarDecl || expr instanceof Expr.Noop ||
              expr instanceof Expr.TypeExpr || expr instanceof Expr.FunDecl ||
              expr instanceof Expr.MapLiteral && ((Expr.MapLiteral)expr).isNamedArgs ||
              expr instanceof Expr.Binary && ((Expr.Binary)expr).createIfMissing ||
              expr instanceof Expr.InvokeNew ||
              expr instanceof Expr.ClassPath) {
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
      exprDecorator.decorate(code);
    }
    var resolver = new Resolver(jacsalContext, bindings);
    resolver.resolveScript(code);
    var analyser = new Analyser(jacsalContext);
    //analyser.testAsync = testAsync;
    analyser.analyseClass(code);
    if (isScript) {
      return Compiler.compile(source, jacsalContext, code);
    }
    Compiler.compileClass(source, jacsalContext, packageName, code);
    return null;
  }

  protected void test(String code, Object expected) {
    doTest(code, true, false, false, expected);
    doTest(code, false, false, false, expected);
    doTest(code, true, false, true, expected);
    if (replMode) {
      doTest(code, true, true, false, expected);
      doTest(code, false, true, false, expected);
      doTest(code, true, true, true, expected);
    }
  }

  protected void test(List<String> classCode, String scriptCode, Object expected) {
    doTest(classCode, scriptCode, true, false, false, expected);
    doTest(classCode, scriptCode, false, false, false, expected);
    doTest(classCode, scriptCode, true, false, true, expected);
    if (replMode) {
      doTest(classCode, scriptCode, true, true, false, expected);
      doTest(classCode, scriptCode, false, true, false, expected);
      doTest(classCode, scriptCode, true, true, true, expected);
    }
  }

  protected void testError(String code, String expectedError) {
    testError(List.of(), code, expectedError);
  }

  protected void testError(List<String> classCode, String scriptCode, String expectedError) {
    doTestError(classCode, scriptCode, true, false, expectedError);
    doTestError(classCode, scriptCode, false, false, expectedError);

    // Tests in repl mode may have different errors or no errors compared
    // to normal mode when accessing global vars so we can't run tests that
    // generate an error and expect the same results
    //    doTestError(code, true, true, expectedError);
    //    doTestError(code, false, true, expectedError);
  }

  private void doTestError(List<String> classCode, String scriptCode, boolean evalConsts, boolean replMode, String expectedError) {
    try {
      JacsalContext jacsalContext = JacsalContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debugLevel)
                                                 .build();
      Map<String, Object> bindings = createGlobals();
      classCode.forEach(code -> compileClass(code, jacsalContext, packageName, false));
      Compiler.run(scriptCode, jacsalContext, packageName, bindings);
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
