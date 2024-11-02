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
import io.jactl.resolver.Resolver;
import io.jactl.runtime.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class BaseTest {
  protected int                debugLevel;
  protected String             packageName;
  protected JactlMap globals;
  protected boolean            useAsyncDecorator;
  protected boolean            classAccessToGlobals;
  protected boolean            alwaysEvalConsts;
  protected boolean            replModeEnabled;
  protected boolean            skipCheckpointTests;
  protected JactlEnv           jactlEnv;

  protected static int testCounter = 0;

  @BeforeEach
  public void setUp() {
    debugLevel           = 0;
    packageName          = Utils.DEFAULT_JACTL_PKG;
    globals              = new JactlMapImpl();
    useAsyncDecorator    = true;
    classAccessToGlobals = false;
    alwaysEvalConsts     = false;
    replModeEnabled      = true;
    skipCheckpointTests  = false;
    jactlEnv             = new DefaultEnv();

    Jactl.function()
         .name("_checkpoint")
         .param("data", null)
         .impl(BuiltinFunctions.class, "_checkpoint")
         .register();
  }

  @AfterEach
  public void cleanUp() {
    BuiltinFunctions.deregisterFunction("_checkpoint");
  }

  protected void doTest(String code, Object expected) {
    doTest(code, true, false, false, expected);
  }

  protected void doTest(String code, Object expected, boolean testAsync) {
    doTest(code, true, false, testAsync, expected);
  }

  protected void doTest(String code, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
    doTest(code, evalConsts, replMode, testAsync, false, expected);
  }

  protected void doTest(String code, boolean evalConsts, boolean replMode, boolean testAsync, boolean testCheckpoint, Object expected) {
    doTest(Utils.listOf(), code, evalConsts, replMode, testAsync, testCheckpoint, expected);
  }

  protected void doTest(List<String> classCode, String scriptCode, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
    doTest(classCode, scriptCode, evalConsts, replMode, testAsync, false, expected);
  }

  protected void doTest(List<String> classCode, String scriptCode, boolean evalConsts, boolean replMode, boolean testAsync, boolean testCheckpoint, Object expected) {
    doTest(classCode, scriptCode, null, null, evalConsts, replMode, testAsync, testCheckpoint, expected);
  }

  protected void doTest(List<String> classCode, String scriptCode, String input, ByteArrayOutputStream output, boolean evalConsts, boolean replMode, boolean testAsync, boolean testCheckpoint, Object expected) {
    RuntimeUtils.clearScriptCache();
    testCounter++;
    try {
      JactlContext jactlContext = getJactlContext(evalConsts, replMode, testCheckpoint);

      JactlMap bindings = createGlobals();

      Function<Expr,Expr> asyncDecorator = testAsync ? expr -> sleepify(expr) : null;
      classCode.forEach(code -> compileClass(code, jactlContext, packageName, asyncDecorator, createGlobals()));

      JactlScript compiled = compileScript(scriptCode, jactlContext, packageName, asyncDecorator, bindings);

      if (input == null && output == null) {
        Object result = compiled.runSync(bindings);
        checkEqual(expected, result);
      }
      else {
        Object result = compiled.runSync(bindings,
                                         input == null ? null : new BufferedReader(new StringReader(input)),
                                         output == null ? null: new PrintStream(output));
        checkEqual(expected, result);
      }

    }
    catch (CompileError e) {
      e.getErrors().forEach(Throwable::printStackTrace);
      fail(e);
    }
    catch (Exception e) {
      e.printStackTrace();
      fail(e);
    }
  }

  protected void doTestCheckpoint(String scriptCode, Object expected) {
    doTestCheckpoint(Utils.listOf(), scriptCode, expected);
  }

  protected void doTestCheckpoint(List<String> classCode, String scriptCode, Object expected) {
    testCounter++;
    try {
      int[] errors = {0};
      byte[][] savedCheckpoint = { null };
      UUID[]   savedId   = { null };
      UUID[]   deletedId = { null };
      final int[] savedCheckpointId = {0};
      final int[] lastCheckpointId = {0};
      jactlEnv = new DefaultEnv() {
        @Override public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
          if (lastCheckpointId[0] + 1 != checkpointId) {
            resumer.accept(new RuntimeError("Checkpoint id of " + checkpointId + " does not match expected value of " + lastCheckpointId[0] + 1, source, offset));
            return;
          }
          lastCheckpointId[0] = checkpointId;
          if (savedCheckpoint[0] == null) {
            savedCheckpoint[0] = checkpoint;
            savedCheckpointId[0] = checkpointId;
            savedId[0] = id;
          }
          resumer.accept(result);
        }

        @Override public void deleteCheckpoint(UUID id, int checkpointId) {
          if (lastCheckpointId[0] + 1 != checkpointId) {
            System.out.println("Delete: Checkpoint id of " + checkpointId + " does not match expected value of " + (lastCheckpointId[0] + 1));
            errors[0]++;
            return;
          }
          if (deletedId[0] != null) {
            System.out.println("Delete: checkpoint deleted multiple times, checkpointId=" + checkpointId);
            errors[0]++;
            return;
          }
          lastCheckpointId[0] = checkpointId;
          deletedId[0] = id;
        }
      };

      JactlContext jactlContext1 = getJactlContext(true, false, false);
      JactlContext jactlContext2 = getJactlContext(true, false, false);

      JactlMap bindings = createGlobals();

      Function<Expr,Expr> asyncDecorator = expr -> checkpointify(expr);
      classCode.forEach(code -> compileClass(code, jactlContext1, packageName, asyncDecorator, bindings));
      classCode.forEach(code -> compileClass(code, jactlContext2, packageName, asyncDecorator, bindings));

      JactlScript compiled = compileScript(scriptCode, jactlContext1, packageName, asyncDecorator, bindings);
      compileScript(scriptCode, jactlContext2, packageName, asyncDecorator, bindings);

      Object result   = compiled.runSync(bindings);
      checkEqual(expected, result);

      assertEquals(0, errors[0]);

      // Now loop, restoring first checkpoint until we have reached last checkpoint
      while (savedCheckpoint[0] != null) {
        assertNotNull(savedId[0]);
        assertNotNull(deletedId[0]);
        assertEquals(savedId[0], deletedId[0]);
        savedId[0] = deletedId[0] = null;
        byte[] checkpoint = savedCheckpoint[0];
        lastCheckpointId[0] = savedCheckpointId[0];
        savedCheckpoint[0] = null;
        savedCheckpointId[0] = 0;
        CompletableFuture future = new CompletableFuture();
        jactlContext2.recoverCheckpoint(checkpoint, recoveredResult -> future.complete(recoveredResult));
        checkEqual(expected, future.get());
        assertEquals(0, errors[0]);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      fail(e);
    }
  }

  protected static void checkEqual(Object expected, Object result) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    if (expected == null) {
      assertEquals(expected, result);
    }
    else {
      switch (expected.getClass().getName()) {
        case "[Z":
          assertTrue(result instanceof boolean[]);
          assertArrayEquals((boolean[]) expected, (boolean[]) result);
          break;
        case "[B":
          assertTrue(result instanceof byte[]);
          assertArrayEquals((byte[]) expected, (byte[]) result);
          break;
        case "[I":
          assertTrue(result instanceof int[]);
          assertArrayEquals((int[]) expected, (int[]) result);
          break;
        case "[J":
          assertTrue(result instanceof long[]);
          assertArrayEquals((long[]) expected, (long[]) result);
          break;
        case "[D":
          assertTrue(result instanceof double[]);
          assertArrayEquals((double[]) expected, (double[]) result);
          break;
        case "[Ljava.lang.String;":
          assertTrue(result instanceof Object[]);
          assertArrayEquals((String[]) expected, (String[]) result);
        case "[Ljava.lang.Object;":
          assertTrue(result instanceof Object[]);
          Object[] expectedArray = (Object[]) expected;
          Object[] actualArray = (Object[]) result;
          assertEquals(expectedArray.length, actualArray.length);
          for (int i = 0; i < expectedArray.length; i++) {
            checkEqual(expectedArray[i], actualArray[i]);
          }
          break;
        case "[Ljava.math.BigDecimal;":
          assertTrue(result instanceof BigDecimal[]);
          assertArrayEquals((BigDecimal[]) expected, (BigDecimal[]) result);
          break;
        default:
          if (result instanceof Throwable) {
            ((Throwable) result).printStackTrace();
            fail(((Throwable) result).getMessage());
          }
          checkEquality(expected, result);
          break;
      }
    }
  }

  protected JactlContext getJactlContext() {
    return getJactlContext(true, false, false);
  }

  protected JactlContext getJactlContext(boolean evalConsts, boolean replMode, boolean testCheckpoint) {
    JactlContext jactlContext = JactlContext.create()
                                            .environment(jactlEnv)
                                            .classAccessToGlobals(classAccessToGlobals)
                                            .evaluateConstExprs(evalConsts)
                                            .replMode(replMode)
                                            .debug(debugLevel)
                                            .checkpoint(testCheckpoint)
                                            .restore(testCheckpoint)
                                            .build();
    return jactlContext;
  }

  protected static void checkEquality(Object expected, Object result) {
    if (expected == null && result == null) {
      return;
    }
    assertTrue(expected != null, "Expected " + expected + " but was " + result);
    assertTrue(result != null, "Expected " + expected + " but was " + result);
    if (expected instanceof List && result instanceof JactlList) {
      List expectedList = (List) expected;
      JactlList resultList = (JactlList) result;
      assertEquals(expectedList.size(), resultList.size());
      for (int i = 0; i < expectedList.size(); i++) {
        checkEquality(expectedList.get(i), resultList.get(i));
      }
      return;
    }
    if (expected instanceof Map && result instanceof JactlMap) {
      Map expectedMap = (Map) expected;
      JactlMap resultMap = (JactlMap) result;
      assertEquals(expectedMap.size(), resultMap.size());
      expectedMap.forEach((k,v) -> checkEquality(expectedMap.get(k), resultMap.get(k)));
      return;
    }
    if (!expected.getClass().isArray() && !result.getClass().isArray()) {
      assertEquals(expected, result);
      return;
    }
    assertEquals(expected.getClass().isArray(), result.getClass().isArray());
    assertEquals(expected.getClass().getComponentType(), result.getClass().getComponentType());
    assertEquals(Array.getLength(expected), Array.getLength(result));
    for (int i = 0; i < Array.getLength(expected); i++) {
      checkEquality(Array.get(expected, i), Array.get(result, i));
    }
  }

  public void compileClass(String source, JactlContext jactlContext, String packageName, Function<Expr,Expr> exprDecorator, JactlMap bindings) {
    doCompile(false, source, jactlContext, packageName, exprDecorator, bindings);
  }

  public JactlScript compileScript(String source, JactlContext jactlContext, String packageName, Function<Expr,Expr> exprDecorator, JactlMap bindings) {
    return doCompile(true, source, jactlContext, packageName, exprDecorator, bindings);
  }

  public JactlScript doCompile(boolean isScript, String source, JactlContext jactlContext, String packageName, Function<Expr,Expr> exprDecorator, JactlMap bindings) {
    if (exprDecorator == null) {
      if (isScript) {
        return Compiler.compileScript(source, jactlContext, packageName, bindings);
      }
      Compiler.compileClass(source, jactlContext, packageName, bindings);
      return null;
    }

    String         className = Utils.JACTL_SCRIPT_PREFIX + Utils.md5Hash(source);
    Parser         parser    = new Parser(new Tokeniser(source, true), jactlContext, packageName);
    Stmt.ClassDecl code      = isScript ? parser.parseScript(className) : parser.parseClass();
    ExprDecorator decorator = !useAsyncDecorator ? new ExprDecorator(Function.identity())
                                                 : new ExprDecorator(
        expr -> {
          assert expr != null;
          if (expr instanceof Expr.Block ||
              expr instanceof Expr.VarDecl || expr instanceof Expr.Noop ||
              expr instanceof Expr.TypeExpr || expr instanceof Expr.FunDecl ||
              expr instanceof Expr.MapLiteral && ((Expr.MapLiteral)expr).isNamedArgs ||
              expr instanceof Expr.Binary && ((Expr.Binary)expr).createIfMissing ||
              expr instanceof Expr.RegexSubst ||
              expr instanceof Expr.InvokeNew ||
              expr instanceof Expr.SpecialVar ||
              expr instanceof Expr.ClassPath) {
            return expr;
          }
          if (expr instanceof Expr.Identifier &&
              ((Expr.Identifier)expr).identifier.getStringValue().equals(Utils.SUPER_VAR)) {
            // Don't wrap super.xxx() calls because wrapping super in async means super.f() would turn into
            // super(0,super).f() which would invoke this.f() and not super.f().
            return expr;
          }
          Expr newExpr = exprDecorator.apply(expr);
          newExpr.isResultUsed = expr.isResultUsed;
          expr.isResultUsed = true;
          return newExpr;
        });
    decorator.decorate(code);
    Resolver resolver = new Resolver(jactlContext, bindings, code.location);
    resolver.resolveScript(code);
    Analyser analyser = new Analyser(jactlContext);
    analyser.analyseClass(code);
    if (isScript) {
      return Compiler.compileWithCompletion(source, jactlContext, code);
    }
    Compiler.compileClass(source, jactlContext, packageName, code);
    return null;
  }

  private static Expr.Call sleepify(Expr expr) {
    return new Expr.Call(expr.location,
                         new Expr.Identifier(expr.location.newIdent("sleep")),
                         Utils.listOf(new Expr.Literal(new Token(TokenType.LONG_CONST, expr.location).setValue(0)),
                                 expr));
  }

  private static Expr.Call checkpointify(Expr expr) {
    return new Expr.Call(expr.location,
                         new Expr.Identifier(expr.location.newIdent("_checkpoint")),
                         Utils.listOf(expr));
  }

  protected void test(String code, Object expected) {
    doTest(code, true, false, false, expected);
    if (!alwaysEvalConsts) {
      doTest(code, false, false, false, expected);
    }
    doTest(code, true, false, true, expected);
    if (!skipCheckpointTests) {
      doTest(Utils.listOf(), code, true, false, true, true, expected);
      doTestCheckpoint(Utils.listOf(), code, expected);
    }
    if (replModeEnabled) {
      doTest(code, true, true, false, expected);
      if (!alwaysEvalConsts) {
        doTest(code, false, true, false, expected);
      }
      doTest(code, true, true, true, expected);
      if (!skipCheckpointTests) {
        doTest(Utils.listOf(), code, true, true, true, true, expected);
      }
    }
  }

  protected void test(List<String> classCode, String scriptCode, Object expected) {
    doTest(classCode, scriptCode, true, false, false, expected);
    if (!alwaysEvalConsts) {
      doTest(classCode, scriptCode, false, false, false, expected);
    }
    doTest(classCode, scriptCode, true, false, true, expected);
    if (!skipCheckpointTests) {
      doTest(classCode, scriptCode, true, false, true, true, expected);
      doTestCheckpoint(classCode, scriptCode, expected);
    }
    if (replModeEnabled) {
      doTest(classCode, scriptCode, true, true, false, expected);
      if (!alwaysEvalConsts) {
        doTest(classCode, scriptCode, false, true, false, expected);
      }
      doTest(classCode, scriptCode, true, true, true, expected);
      if (!skipCheckpointTests) {
        doTest(classCode, scriptCode, true, true, true, true, expected);
      }
    }
  }

  protected void testError(String code, String expectedError) {
    testError(Utils.listOf(), code, expectedError);
  }

  protected void testError(List<String> classCode, String scriptCode, String expectedError) {
    doTestError(classCode, scriptCode, true, false, expectedError);
    if (!alwaysEvalConsts) {
      doTestError(classCode, scriptCode, false, false, expectedError);
    }

    // Tests in repl mode may have different errors or no errors compared
    // to normal mode when accessing global vars so we can't run tests that
    // generate an error and expect the same results
    //    doTestError(code, true, true, expectedError);
    //    doTestError(code, false, true, expectedError);
  }

  protected void replError(String code, String expectedError) {
    doTestError(Utils.listOf(), code, true, true, expectedError);
  }

  protected void replTest(Object expected, String... code) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    try {
      JactlContext jactlContext = JactlContext.create()
                                              .evaluateConstExprs(true)
                                              .replMode(true)
                                              .classAccessToGlobals(classAccessToGlobals)
                                              .debug(debugLevel)
                                              .build();

      JactlMap bindings = createGlobals();
      Object              result[] = new Object[1];
      Arrays.stream(code).forEach(scriptCode -> {
        JactlScript compiled = compileScript(scriptCode, jactlContext, packageName, null, bindings);
        result[0]    = compiled.runSync(bindings);
      });
      if (expected instanceof Object[]) {
        assertTrue(result[0] instanceof Object[]);
        assertTrue(Arrays.equals((Object[]) expected, (Object[]) result[0]));
      }
      else {
        assertEquals(expected, result[0]);
      }
    }
    catch (Exception e) {
      fail(e);
      e.printStackTrace();
    }
  }

  protected void doTestError(List<String> classCode, String scriptCode, boolean evalConsts, boolean replMode, String expectedError) {
    testCounter++;
    try {
      JactlContext jactlContext = JactlContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debugLevel)
                                                 .build();
      JactlMap bindings = createGlobals();
      classCode.forEach(code -> compileClass(code, jactlContext, packageName, null, bindings));
      Compiler.eval(scriptCode, jactlContext, packageName, bindings);
      fail("Expected JactlError");
    }
    catch (JactlError e) {
      if (!e.getMessage().toLowerCase().contains(expectedError.toLowerCase())) {
        if (e instanceof CompileError) {
          CompileError ce = (CompileError) e;
          ce.getErrors().forEach(e1 -> e1.printStackTrace());
        }
        else {
          e.printStackTrace();
        }
        fail("Message did not contain expected string <" + expectedError + ">. Message=" + e.getMessage());
      }
    }
  }

  protected JactlMap createGlobals() {
    return new JactlMapImpl(globals);
  }

  protected JactlScript compile(String code) {
    JactlContext jactlContext = JactlContext.create()
                                               .evaluateConstExprs(true)
                                               .replMode(true)
                                               .debug(0)
                                               .build();
    return Compiler.compileScript(code, jactlContext, new JactlMapImpl());
  }

  protected boolean asyncAutoCreate() {
    return JactlContext.create().build().autoCreateAsync();
  }

  @BeforeAll
  public static void initCounter() {
    testCounter = 0;
  }

  @AfterAll
  public static void displayCounter() {
    System.out.println("Total tests: " + testCounter);
    testCounter = 0;
  }


}
