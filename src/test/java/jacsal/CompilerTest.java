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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

  private boolean evaluateConstExprs = true;

  @BeforeEach private void setUp() {
    evaluateConstExprs = true;
  }

  private void doTest(String code, Object expected) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    try {
      Object result = Compiler.run(code, new CompileContext().setEvaluateConstExprs(evaluateConstExprs), null);
      assertEquals(expected, result);
    }
    catch (JacsalError e) {
      fail(e);
      e.printStackTrace();
    }
  }

  private void test(String code, Object expected) {
    evaluateConstExprs = true;
    doTest(code, expected);
    evaluateConstExprs = false;
    doTest(code, expected);
  };
  
  private void testFail(String code, String expectedError) {
    evaluateConstExprs = true;
    doTestFail(code, expectedError);
    evaluateConstExprs = false;
    doTestFail(code, expectedError);
  }

  private void doTestFail(String code, String expectedError) {
    try {
      Compiler.run(code, new CompileContext().setEvaluateConstExprs(evaluateConstExprs), null);
      fail("Expected JacsalError");
    }
    catch (JacsalError e) {
      if (!e.getMessage().toLowerCase().contains(expectedError.toLowerCase())) {
        e.printStackTrace();
        fail("Message did not contain expected string '" + expectedError + "'. Message=" + e.getMessage());
      }
    }
  };

  private void runTests(Runnable runnable) {
    evaluateConstExprs = true;
    runnable.run();
    evaluateConstExprs = false;
    runnable.run();
  }

  @Test public void literals() {
    test("42", 42);
    test("0", 0);
    test("1", 1);
    test("2", 2);
    test("3", 3);
    test("4", 4);
    test("5", 5);
    test("6", 6);
    test(Byte.toString(Byte.MAX_VALUE), (int)Byte.MAX_VALUE);
    test(Integer.toString(Byte.MAX_VALUE + 1), (int)Byte.MAX_VALUE + 1);
    test(Short.toString(Short.MAX_VALUE), (int)Short.MAX_VALUE);
    test(Integer.toString(Short.MAX_VALUE + 1), (int)Short.MAX_VALUE + 1);
    test(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    testFail("" + (Integer.MAX_VALUE + 1L), "number too large");
    test("" + ((long)Integer.MAX_VALUE + 1L) + "L", (long)Integer.MAX_VALUE + 1L);
    test("1D", 1D);
    test("1.0D", 1.0D);
    test("0.0D", 0.0D);
    test("0.1D", 0.1D);
    test("1.0", "#1.0");
    test("0.0", "#0.0");
    test("0.1", "#0.1");
    test("0.123456789123456789", "#0.123456789123456789");
    test("''", "");
    test("'123'", "123");
    test("'1\\'23'", "1'23");
    test("'''1'23'''", "1'23");
    test("'''1'23''\\''''", "1'23'''");
    test("'\\t\\f\\b\\r\\n'", "\t\f\b\r\n");
    test("true", true);
    test("false", false);
    test("null", null);
  }

  @Test public void booleanConversion() {
    test("!-1", false);
    test("!1", false);
    test("!2", false);
    test("!0", true);
    test("!-0", true);
    test("!-1L", false);
    test("!2L", false);
    test("!0L", true);
    test("!-0L", true);
    test("!null", true);
    test("!0.0", true);
    test("!-0.0", true);
    test("!1.0", false);
    test("!-1.0", false);
    test("!2.0", false);
    test("!0.0D", true);
    test("!-0.0D", true);
    test("!1.0D", false);
    test("!-1.0D", false);
    test("!2.0D", false);
    test("!'x'", false);
    test("!''", true);
  }

  @Test public void constUnaryExpressions() {
    test("-1", -1);
    test("-1D", -1D);
    test("-1L", -1L);
    test("-1.0", "#-1.0");
    test("+1", 1);
    test("+1D", 1D);
    test("+1L", 1L);
    test("+1.0", "#1.0");
    test("+-1", -1);
    test("+-1D", -1D);
    test("+-1L", -1L);
    test("+-1.0", "#-1.0");
    test("-+1", -1);
    test("-+1D", -1D);
    test("-+1L", -1L);
    test("-+1.0", "#-1.0");
    test("- -1", 1);
    test("- -1D", 1D);
    test("- -1L", 1L);
    test("- -1.0", "#1.0");
    test("-(-1)", 1);
    test("-(-1D)", 1D);
    test("-(-1L)", 1L);
    test("-(-1.0)", "#1.0");
    testFail("-true", "cannot be applied to type");
    testFail("-'abc'", "cannot be applied to type");
    testFail("+true", "cannot be applied to type");
    testFail("+'abc'", "cannot be applied to type");
    test("!true", false);
    test("!false", true);
    test("!!true", true);
    test("!!false", false);
    test("!(!!true)", false);
    test("!!(!false)", true);
  }

  @Test public void simpleConstExpressios() {
    testFail("1 + true", "non-numeric operand for right-hand side");
    testFail("false + 1", "non-numeric operand for left-hand side");
    testFail("1 - 'abc'", "non-numeric operand for right-hand side");
    testFail("'abc' - 1", "non-numeric operand for left-hand side");
    testFail("false + true", "non-numeric operand for left-hand side");
    testFail("1 + null", "non-numeric operand for right-hand side");
    testFail("null + 1", "left-hand side of '+' cannot be null");
    test("1 + 2", 3);
    test("1 - 2", -1);
    test("1 - -2", 3);
    test("2 * 3", 6);
    test("6 / 3", 2);
    test("8 % 5", 3);
    test("1L + 2L", 3L);
    test("1L - 2L", -1L);
    test("1L - -2L", 3L);
    test("2L * 3L", 6L);
    test("6L / 3L", 2L);
    test("8L % 5L", 3L);
    test("1 + 2L", 3L);
    test("1L - 2", -1L);
    test("1 - -2L", 3L);
    test("2L * 3", 6L);
    test("6 / 3L", 2L);
    test("8L % 5", 3L);
    test("8 % 5L", 3L);
    test("1.0D + 2.0D", 3.0D);
    test("1.0D - 2.0D", -1.0D);
    test("1.0D - -2.0D", 3.0D);
    test("2.0D * 3.0D", 6D);
    test("6.0D / 3.0D", 2.0D);
    test("8.0D % 5.0D", 3D);
    test("1L + 2.0D", 3.0D);
    test("1.0D - 2L", -1.0D);
    test("1L - -2.0D", 3.0D);
    test("2.0D * 3L", 6D);
    test("6L / 3.0D", 2.0D);
    test("8.0D % 5L", 3D);
    test("1.0 + 2.0", "#3.0");
    test("1.0 - 2.0", "#-1.0");
    test("1.0 - -2.0", "#3.0");
    test("2.0 * 3.0", "#6.00");
    test("6.0 / 3.0", "#2");
    test("6.0 / 7.0", "#0.85714285714285714286");
    test("8.0 % 5.0", "#3.0");
    test("1 + 2D", 3D);
    test("1D - 2", -1D);
    test("1 - -2D", 3D);
    test("2D * 3", 6D);
    test("6 / 3D", 2D);
    test("8D % 5", 3D);
    test("8 % 5D", 3D);
    test("1 + 2.0", "#3.0");
    test("1.0 - 2", "#-1.0");
    test("1 - -2.0", "#3.0");
    test("2.0 * 3", "#6.0");
    test("6 / 3.0", "#2");
    test("8.0 % 5", "#3.0");
    test("8 % 5.0", "#3.0");
    test("1L + 2.0", "#3.0");
    test("1.0 - 2L", "#-1.0");
    test("1L - -2.0", "#3.0");
    test("2.0 * 3L", "#6.0");
    test("6L / 3.0", "#2");
    test("8.0 % 5L", "#3.0");
    test("8L % 5.0", "#3.0");
    test("1D + 2.0", "#3.0");
    test("1.0 - 2D", "#-1.0");
    test("1D - -2.0", "#3.0");
    test("2.0 * 3D", "#6.00");
    test("6D / 3.0", "#2");
    test("8.0 % 5D", "#3.0");
    test("8D % 5.0", "#3.0");
  }

  @Test public void simpleVariables() {
    test("int v = 1; v", 1);
    test("int v = 1", 1);
    test("var v = 1; v", 1);
    test("long v = 1; v", 1L);
    test("var v = 1L; v", 1L);
    test("double v = 1; v", 1D);
    test("var v = 1D; v", 1D);
    test("Decimal v = 1; v", "#1");
    test("var v = 1.0; v", "#1.0");
  }

  @Test public void simpleVariableAssignments() {
    test("int v = 1; v = 2; v", 2);
    test("int v = 1; v = 2", 2);
    test("int v = 1; v = v + 1", 2);
    testFail("1 = 2", "valid lvalue");
  }

  @Test public void defVariableArithmetic() {
    testFail("def v = 1; v + true", "non-numeric operand for right-hand side");
    testFail("def v = false; v + 1", "non-numeric operand for left-hand side");
    testFail("def v = true; 1 + v", "non-numeric operand for right-hand side");
    testFail("def v = 1; false + 1", "non-numeric operand for left-hand side");
    testFail("def x = 1; def y = true; x + y", "non-numeric operand for right-hand side");
    testFail("def x = 1; x - 'abc'", "non-numeric operand for right-hand side");
    testFail("def x = 'abc'; 1 - x", "non-numeric operand for right-hand side");
    testFail("def x = 'abc'; x - 1", "non-numeric operand for left-hand side");
    testFail("def x = 'abc'; def y = 1; x - y", "non-numeric operand for left-hand side");
    testFail("def x = 1; 'abc' - x", "non-numeric operand for left-hand side");
    testFail("def x = false; x + true", "non-numeric operand for left-hand side");
    testFail("def x = true; false + x", "non-numeric operand for left-hand side");
    testFail("def x = false; def y = true; x + y", "non-numeric operand for left-hand side");
    testFail("def x = 1; x + null", "non-numeric operand for right-hand side");
    testFail("def x = null; 1 + x", "non-numeric operand for right-hand side");
    testFail("def x = 1; def y =null; x + y", "non-numeric operand for right-hand side");
    testFail("def x = null; x + 1", "left-hand side of '+' cannot be null");
    testFail("def x = 1; null + x", "left-hand side of '+' cannot be null");
    testFail("def x = null; def y = 1; x + y", "left-hand side of '+' cannot be null");

    test("def x = 1; x + 2", 3);
    test("def x = 2; 1 + x", 3);
    test("def x = 1; def y = 2; x + y", 3);
    test("def x = 1; x - 2", -1);
    test("def x = 2; 1 - x", -1);
    test("def x = 1; def y = 2; x - y", -1);
    test("def x = 1; x - -2", 3);
    test("def x = 2; 1 - -x", 3);
    test("def x = 1; def y = 2; x - -y", 3);
    test("def x = 2; x * 3", 6);
    test("def x = 3; 2 * x", 6);
    test("def x = 2; def y = 3; x * y", 6);
    test("def x = 6; x / 3", 2);
    test("def x = 3; 6 / x", 2);
    test("def x = 6; def y = 3; x / y", 2);
    test("def x = 8; x % 5", 3);
    test("def x = 5; 8 % x", 3);
    test("def x = 8; def y = 5; x % y", 3);
    test("def x = 1L; x + 2L", 3L);
    test("def x = 2L; 1L + x", 3L);
    test("def x = 1L; def y = 2L; x + y", 3L);
    test("def x = 1L; x - 2L", -1L);
    test("def x = 2L; 1L - x", -1L);
    test("def x = 1L; def y = 2L; x - y", -1L);
    test("def x = 1L; x - -2L", 3L);
    test("def x = 2L; 1L - -x", 3L);
    test("def x = 1L; def y = 2L; x - -y", 3L);
    test("def x = 2L; x * 3L", 6L);
    test("def x = 3L; 2L * x", 6L);
    test("def x = 2L; def y = 3L; x * y", 6L);
    test("def x = 6L; x / 3L", 2L);
    test("def x = 3L; 6L / x", 2L);
    test("def x = 6L; def y = 3L; x / y", 2L);
    test("def x = 8L; x % 5L", 3L);
    test("def x = 5L; 8L % x", 3L);
    test("def x = 8L; def y = 5L; x % y", 3L);
    test("def x = 1; x + 2L", 3L);
    test("def x = 2L; 1 + x", 3L);
    test("def x = 1; def y = 2L; x + y", 3L);
    test("def x = 1L; x - 2", -1L);
    test("def x = 2; 1L - x", -1L);
    test("def x = 1L; def y = 2; x - y", -1L);
    test("def x = 1; x - -2L", 3L);
    test("def x = 2L; 1 - -x", 3L);
    test("def x = 1; def y = 2L; x - -y", 3L);
    test("def x = 2L; x * 3", 6L);
    test("def x = 3; 2L * x", 6L);
    test("def x = 2L; def y = 3; x * y", 6L);
    test("def x = 6; x / 3L", 2L);
    test("def x = 3L; 6 / x", 2L);
    test("def x = 6; def y = 3L; x / y", 2L);
    test("def x = 8L; x % 5", 3L);
    test("def x = 5; 8L % x", 3L);
    test("def x = 8L; def y = 5; x % y", 3L);
    test("def x = 8; x % 5L", 3L);
    test("def x = 5L; 8 % x", 3L);
    test("def x = 8; def y = 5L; x % y", 3L);

    test("def x = 1.0D; x + 2.0D", 3.0D);
    test("def x =  2.0D; 1.0D + x", 3.0D);
    test("def x = 1.0D; def y = 2.0D; x + y", 3.0D);
    test("def x = 1.0D; x - 2.0D", -1.0D);
    test("def x =  2.0D; 1.0D - x", -1.0D);
    test("def x = 1.0D; def y = 2.0D; x - y", -1.0D);
    test("def x = 1.0D; x - -2.0D", 3.0D);
    test("def x =  -2.0D; 1.0D - x", 3.0D);
    test("def x = 1.0D; def y = 2.0D; x - -y", 3.0D);
    test("def x = 2.0D; x * 3.0D", 6D);
    test("def x =  3.0D; 2.0D * x", 6D);
    test("def x = 2.0D; def y = 3.0D; x * y", 6D);
    test("def x = 6.0D; x / 3.0D", 2.0D);
    test("def x =  3.0D; 6.0D / x", 2.0D);
    test("def x = 6.0D; def y = 3.0D; x / y", 2.0D);
    test("def x = 8.0D; x % 5.0D", 3D);
    test("def x =  5.0D; 8.0D % x", 3D);
    test("def x = 8.0D; def y = 5.0D; x % y", 3D);
    test("def x = 1L; x + 2.0D", 3.0D);
    test("def x =  2.0D; 1L + x", 3.0D);
    test("def x = 1L; def y = 2.0D; x + y", 3.0D);
    test("def x = 1.0D; x - 2L", -1.0D);
    test("def x =  2L; 1.0D - x", -1.0D);
    test("def x = 1.0D; def y = 2L; x - y", -1.0D);
    test("def x = 1L; x - -2.0D", 3.0D);
    test("def x =  -2.0D; 1L - x", 3.0D);
    test("def x = 1L; def y = 2.0D; x - -y", 3.0D);
    test("def x = 2.0D; x * 3L", 6D);
    test("def x =  3L; 2.0D * x", 6D);
    test("def x = 2.0D; def y = 3L; x * y", 6D);
    test("def x = 6L; x / 3.0D", 2.0D);
    test("def x =  3.0D; 6L / x", 2.0D);
    test("def x = 6L; def y = 3.0D; x / y", 2.0D);
    test("def x = 8.0D; x % 5L", 3D);
    test("def x =  5L; 8.0D % x", 3D);
    test("def x = 8.0D; def y = 5L; x % y", 3D);
    test("def x = 1.0; x + 2.0", "#3.0");
    test("def x =  2.0; 1.0 + x", "#3.0");
    test("def x = 1.0; def y = 2.0; x + y", "#3.0");
    test("def x = 1.0; x - 2.0", "#-1.0");
    test("def x =  2.0; 1.0 - x", "#-1.0");
    test("def x = 1.0; def y = 2.0; x - y", "#-1.0");
    test("def x = 1.0; x - -2.0", "#3.0");
    test("def x =  -2.0; 1.0 - x", "#3.0");
    test("def x = 1.0; def y = 2.0; x - -y", "#3.0");
    test("def x = 2.0; x * 3.0", "#6.00");
    test("def x =  3.0; 2.0 * x", "#6.00");
    test("def x = 2.0; def y = 3.0; x * y", "#6.00");
    test("def x = 6.0; x / 3.0", "#2");
    test("def x =  3.0; 6.0 / x", "#2");
    test("def x = 6.0; def y = 3.0; x / y", "#2");
    test("def x = 6.0; x / 7.0", "#0.85714285714285714286");
    test("def x =  7.0; 6.0 / x", "#0.85714285714285714286");
    test("def x = 6.0; def y = 7.0; x / y", "#0.85714285714285714286");
    test("def x = 8.0; x % 5.0", "#3.0");
    test("def x =  5.0; 8.0 % x", "#3.0");
    test("def x = 8.0; def y = 5.0; x % y", "#3.0");
    test("def x = 1; x + 2D", 3D);
    test("def x =  2D; 1 + x", 3D);
    test("def x = 1; def y = 2D; x + y", 3D);
    test("def x = 1D; x - 2", -1D);
    test("def x =  2; 1D - x", -1D);
    test("def x = 1D; def y = 2; x - y", -1D);
    test("def x = 1; x - -2D", 3D);
    test("def x =  -2D; 1 - x", 3D);
    test("def x = 1; def y = 2D; x - -y", 3D);
    test("def x = 2D; x * 3", 6D);
    test("def x =  3; 2D * x", 6D);
    test("def x = 2D; def y = 3; x * y", 6D);
    test("def x = 6; x / 3D", 2D);
    test("def x =  3D; 6 / x", 2D);
    test("def x = 6; def y = 3D; x / y", 2D);
    test("def x = 8D; x % 5", 3D);
    test("def x =  5; 8D % x", 3D);
    test("def x = 8D; def y = 5; x % y", 3D);
    test("def x = 8; x % 5D", 3D);
    test("def x =  5D; 8 % x", 3D);
    test("def x = 8; def y = 5D; x % y", 3D);
    test("def x = 1; x + 2.0", "#3.0");
    test("def x =  2.0; 1 + x", "#3.0");
    test("def x = 1; def y = 2.0; x + y", "#3.0");
    test("def x = 1.0; x - 2", "#-1.0");
    test("def x =  2; 1.0 - x", "#-1.0");
    test("def x = 1.0; def y = 2; x - y", "#-1.0");
    test("def x = 1; x - -2.0", "#3.0");
    test("def x =  -2.0; 1 - x", "#3.0");
    test("def x = 1; def y = 2.0; x - -y", "#3.0");
    test("def x = 2.0; x * 3", "#6.0");
    test("def x =  3; 2.0 * x", "#6.0");
    test("def x = 2.0; def y = 3; x * y", "#6.0");
    test("def x = 6; x / 3.0", "#2");
    test("def x =  3.0; 6 / x", "#2");
    test("def x = 6; def y = 3.0; x / y", "#2");
    test("def x = 8.0; x % 5", "#3.0");
    test("def x =  5; 8.0 % x", "#3.0");
    test("def x = 8.0; def y = 5; x % y", "#3.0");
    test("def x = 8; x % 5.0", "#3.0");
    test("def x =  5.0; 8 % x", "#3.0");
    test("def x = 8; def y = 5.0; x % y", "#3.0");
    test("def x = 1L; x + 2.0", "#3.0");
    test("def x =  2.0; 1L + x", "#3.0");
    test("def x = 1L; def y = 2.0; x + y", "#3.0");
    test("def x = 1.0; x - 2L", "#-1.0");
    test("def x =  2L; 1.0 - x", "#-1.0");
    test("def x = 1.0; def y = 2L; x - y", "#-1.0");
    test("def x = 1L; x - -2.0", "#3.0");
    test("def x =  -2.0; 1L - x", "#3.0");
    test("def x = 1L; def y = 2.0; x - -y", "#3.0");
    test("def x = 2.0; x * 3L", "#6.0");
    test("def x =  3L; 2.0 * x", "#6.0");
    test("def x = 2.0; def y = 3L; x * y", "#6.0");
    test("def x = 6L; x / 3.0", "#2");
    test("def x =  3.0; 6L / x", "#2");
    test("def x = 6L; def y = 3.0; x / y", "#2");
    test("def x = 8.0; x % 5L", "#3.0");
    test("def x =  5L; 8.0 % x", "#3.0");
    test("def x = 8.0; def y = 5L; x % y", "#3.0");
    test("def x = 8L; x % 5.0", "#3.0");
    test("def x =  5.0; 8L % x", "#3.0");
    test("def x = 8L; def y = 5.0; x % y", "#3.0");
    test("def x = 1D; x + 2.0", "#3.0");
    test("def x =  2.0; 1D + x", "#3.0");
    test("def x = 1D; def y = 2.0; x + y", "#3.0");
    test("def x = 1.0; x - 2D", "#-1.0");
    test("def x =  2D; 1.0 - x", "#-1.0");
    test("def x = 1.0; def y = 2D; x - y", "#-1.0");
    test("def x = 1D; x - -2.0", "#3.0");
    test("def x =  -2.0; 1D - x", "#3.0");
    test("def x = 1D; def y = 2.0; x - -y", "#3.0");
    test("def x = 2.0; x * 3D", "#6.00");
    test("def x =  3D; 2.0 * x", "#6.00");
    test("def x = 2.0; def y = 3D; x * y", "#6.00");
    test("def x = 6D; x / 3.0", "#2");
    test("def x =  3.0; 6D / x", "#2");
    test("def x = 6D; def y = 3.0; x / y", "#2");
    test("def x = 8.0; x % 5D", "#3.0");
    test("def x =  5D; 8.0 % x", "#3.0");
    test("def x = 8.0; def y = 5D; x % y", "#3.0");
    test("def x = 8D; x % 5.0", "#3.0");
    test("def x =  5.0; 8D % x", "#3.0");
    test("def x = 8D; def y = 5.0; x % y", "#3.0");
  }

  @Test public void explicitReturnFromScript() {
    test("double v = 2; return v", 2D);
    test("int v = 2; { v = v + 1; return v } v = v + 3", 3);
    test("String v = '2'; { v = v + 1; { return v }}", "21");
    test("var v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("Decimal v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("def v = '2'; { v = v + 1; { return v }}", "21");
    testFail("double v = 2; return v; v = v + 1", "unreachable statement");
    testFail("double v = 2; return v; { v = v + 1 }", "unreachable statement");
  }

  @Test public void implicitReturnFromScript() {
    test("double v = 2; v", 2D);
    test("int v = 2; { v = v + 1; v }", 3);
    test("String v = '2'; { v = v + 1; { v }}", "21");
    test("var v = 2.0; { v = v + 1; { v }}", "#3.0");
    test("def v = '2'; { v = v + 1; { v }}", "21");
  }

  @Test public void constExprArithmeticPrecedence() {
    test("1 + -2 * -3 + 4", 11);
    test("1 + (2 + 3) * 4 - 5", 16);
    test("13 + 12 % 7", 18);
    test("13 + 12 % 7 - 3", 15);
  }

  @Test public void constStringConcatenation() {
    testFail("'abc' - '123'", "non-numeric operand");
    test("'abc' + '123'", "abc123");
    test("'abc' + null", "abcnull");
    testFail("null + 'abc'", "left-hand side of '+' cannot be null");
    test("'abc' + 'def' + 'ghi'", "abcdefghi");
    test("'abc' + ('1' + '2' + '3') + 'def'", "abc123def");
    test("'' + 'abc'", "abc");
    test("'abc' + ''", "abc");
  }

  @Test public void constStringRepeat() {
    test("'abc' * 2", "abcabc");
    test("'abc' * 0", "");
    testFail("'abc' * -1", "string repeat count must be >= 0");
  }

  @Test public void defaultValues() {
    test("int x", 0);
    test("int x; x", 0);
    test("long x", 0L);
    test("long x; x", 0L);
    test("double x", 0D);
    test("double x; x", 0D);
    test("Decimal x", "#0");
    test("Decimal x; x", "#0");
    testFail("var x", "Initialiser expression required");
  }
}