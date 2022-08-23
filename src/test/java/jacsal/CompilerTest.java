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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

  boolean debug = false;

  private void doTest(String code, boolean evalConsts, boolean replMode, Object expected) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    try {
      CompileContext compileContext = new CompileContext().evaluateConstExprs(evalConsts)
                                                          .replMode(replMode)
                                                          .debug(debug);
      Object         result         = Compiler.run(code, compileContext, new HashMap<>());
      assertEquals(expected, result);
    }
    catch (JacsalError e) {
      fail(e);
      e.printStackTrace();
    }
  }

  private void test(String code, Object expected) {
    doTest(code, true, false, expected);
    doTest(code, false, false, expected);
    doTest(code, true, true, expected);
    doTest(code, false, true, expected);
  }

  private void testFail(String code, String expectedError) {
    doTestFail(code, true, false, expectedError);
    doTestFail(code, false, false, expectedError);
    doTestFail(code, true, true, expectedError);
    doTestFail(code, false, true, expectedError);
  }

  private void doTestFail(String code, boolean evalConsts, boolean replMode, String expectedError) {
    try {
      CompileContext compileContext = new CompileContext().evaluateConstExprs(evalConsts)
                                                          .replMode(replMode)
                                                          .debug(debug);
      Compiler.run(code, compileContext, new HashMap<>());
      fail("Expected JacsalError");
    }
    catch (JacsalError e) {
      if (!e.getMessage().toLowerCase().contains(expectedError.toLowerCase())) {
        e.printStackTrace();
        fail("Message did not contain expected string '" + expectedError + "'. Message=" + e.getMessage());
      }
    }
  }

  @Test
  public void literals() {
    test("42", 42);
    test("0", 0);
    test("1", 1);
    test("2", 2);
    test("3", 3);
    test("4", 4);
    test("5", 5);
    test("6", 6);
    test(Byte.toString(Byte.MAX_VALUE), (int) Byte.MAX_VALUE);
    test(Integer.toString(Byte.MAX_VALUE + 1), (int) Byte.MAX_VALUE + 1);
    test(Short.toString(Short.MAX_VALUE), (int) Short.MAX_VALUE);
    test(Integer.toString(Short.MAX_VALUE + 1), (int) Short.MAX_VALUE + 1);
    test(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    testFail("" + (Integer.MAX_VALUE + 1L), "number too large");
    test("" + ((long) Integer.MAX_VALUE + 1L) + "L", (long) Integer.MAX_VALUE + 1L);
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

  @Test
  public void booleanConversion() {
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

  @Test
  public void constUnaryExpressions() {
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

  @Test
  public void simpleConstExpressions() {
    testFail("1 + true", "non-numeric operand for right-hand side");
    testFail("false + 1", "non-numeric operand for left-hand side");
    testFail("1 - 'abc'", "non-numeric operand for right-hand side");
    testFail("'abc' - 1", "non-numeric operand for left-hand side");
    testFail("false + true", "non-numeric operand for left-hand side");
    testFail("1 + null", "non-numeric operand for right-hand side");
    testFail("null + 1", "left-hand side of '+' cannot be null");
    testFail("1/0", "divide by zero");
    test("1.0D/0", Double.POSITIVE_INFINITY);
    test("-1.0D/0", Double.NEGATIVE_INFINITY);
    testFail("1L/0", "divide by zero");
    testFail("1.0/0", "divide by zero");
    testFail("1/0.0", "divide by zero");
    testFail("1%0", "divide by zero");
    test("1.0D%0", Double.NaN);
    test("-1.0D%0", Double.NaN);
    testFail("1L%0", "divide by zero");
    testFail("1.0%0", "divide by zero");
    testFail("1%0.0", "divide by zero");
    test("1 + 2", 3);
    test("1 - 2", -1);
    test("1 - -2", 3);
    test("2 * 3", 6);
    test("6 / 3", 2);
    test("8 % 5", 3);
    test("8 % 1", 0);
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
    test("6.0 / 3", "#2.0");
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

  @Test
  public void simpleVariables() {
    test("boolean v = false", false);
    test("boolean v = true", true);
    test("boolean v = false; v", false);
    test("boolean v = true; v", true);
    test("boolean v = !false; v", true);
    test("boolean v = !true; v", false);
    test("int v = 1; v", 1);
    test("int v = 1", 1);
    test("var v = 1; v", 1);
    test("long v = 1; v", 1L);
    test("var v = 1L; v", 1L);
    test("double v = 1; v", 1D);
    test("double v = 1.5; v", 1.5D);
    test("var v = 1D; v", 1D);
    test("Decimal v = 1; v", "#1");
    test("var v = 1.0; v", "#1.0");
  }

  @Test public void variableAssignments() {
    test("int v = 1; v = 2; v", 2);
    test("int v = 1; v = 2", 2);
    test("int v = 1; v = v + 1", 2);
    testFail("1 = 2", "valid lvalue");
    test("def x = 1; int y = x + 1", 2);
    test("int x = 1; int y = 2; x = y = 4", 4);
    test("int x = 1; int y = 2; x = y = 4; x", 4);
    test("int x = 1; int y = 2; x = (x = y = 4) + 5; x", 9);
    test("int x = 1; int y = 2; x = y = 4; y", 4);
    test("int x = 1; int y = 2; def z = 5; x = y = z = 3; y", 3);
    test("int x = 1; int y = 2; def z = 5; 4 + (x = y = z = 3) + y", 10);
    test("int x = 1; int y = 2; def z = 5; 4 + (x = y = z = 3) + y; x", 3);
  }

  @Test public void plusEquals(){
    test("int x = 1; x += 3", 4);
    test("int x = 1; (x += 3) + (x += 2)", 10);
    test("int x = 1; (x += 3) + (x += 2); x", 6);
    test("int x = 1; x += 3; x", 4);
    test("def x = 1; x += 3", 4);
    test("def x = 1; x += 3; x", 4);
    test("var x = 1; x += 3", 4);
    test("var x = 1; x += 3; x", 4);
    test("long x = 1; x += 3", 4L);
    test("long x = 1; x += 3; x", 4L);
    test("double x = 1; x += 3", 4.0D);
    test("double x = 1; x += 3; x", 4.0D);
    test("Decimal x = 1.0; x += 3", "#4.0");
    test("Decimal x = 1.0; x += 3; x", "#4.0");
    test("var x = 1.0; x += 3", "#4.0");
    test("var x = 1.0; x += 3; x", "#4.0");
    test("def x = 1; int y = 3; x += y", 4);
    test("def x = 1; int y = 3; x += y; x", 4);
    test("def x = 1; int y = 3; x += (y += 1)", 5);
    test("def x = 1; int y = 3; x += (y += 1); x", 5);
    test("def x = 1; int y = 3; x += (y += 1); x + y", 9);
    test("def x = 1; int y = 3; x += y += 1", 5);
    test("def x = 1; int y = 3; x += y += 1; x", 5);
    test("def x = 1; int y = 3; x += y += 1; x + y", 9);
    test("def x = 1; int y = 3; y += x; y", 4);
    test("def x = 1; int y = 3; y += x", 4);
    test("double x = 1; int y = 3; y += x", 4);
    test("double x = 1; int y = 3; x += y", 4.0D);
    test("def x = 1.0D; int y = 3; y += x", 4);
    test("def x = 1.0D; int y = 3; x += y", 4.0D);
  }

  @Test public void minusEquals() {
    test("int x = 1; x -= 3", -2);
    test("int x = 1; (x -= 3) + (x -= 2)", -6);
    test("int x = 1; (x -= 3) + (x -= 2); x", -4);
    test("int x = 1; x -= 3; x", -2);
    test("def x = 1; x -= 3", -2);
    test("def x = 1; x -= 3; x", -2);
    test("var x = 1; x -= 3", -2);
    test("var x = 1; x -= 3; x", -2);
    test("long x = 1; x -= 3", -2L);
    test("long x = 1; x -= 3; x", -2L);
    test("double x = 1; x -= 3", -2.0D);
    test("double x = 1; x -= 3; x", -2.0D);
    test("Decimal x = 1.0; x -= 3", "#-2.0");
    test("Decimal x = 1.0; x -= 3; x", "#-2.0");
    test("var x = 1.0; x -= 3", "#-2.0");
    test("var x = 1.0; x -= 3; x", "#-2.0");
    test("def x = 1; int y = 3; x -= y", -2);
    test("def x = 1; int y = 3; x -= y; x", -2);
    test("def x = 1; int y = 3; x -= (y -= 1)", -1);
    test("def x = 1; int y = 3; x -= (y -= 1); x", -1);
    test("def x = 1; int y = 3; x -= (y -= 1); x + y", 1);
    test("def x = 1; int y = 3; x -= y -= 1", -1);
    test("def x = 1; int y = 3; x -= y -= 1; x", -1);
    test("def x = 1; int y = 3; x -= y -= 1; x + y", 1);
    test("def x = 1; int y = 3; y -= x; y", 2);
    test("def x = 1; int y = 3; y -= x", 2);
    test("double x = 1; int y = 3; y -= x", 2);
    test("double x = 1; int y = 3; x -= y", -2.0D);
    test("def x = 1.0D; int y = 3; y -= x", 2);
    test("def x = 1.0D; int y = 3; x -= y", -2.0D);
  }

  @Test public void starEquals(){
    test("int x = 2; x *= 3", 6);
    test("int x = 2; (x *= 3) + (x *= 2)", 18);
    test("int x = 2; (x *= 3) + (x *= 2); x", 12);
    test("int x = 2; x *= 3; x", 6);
    test("def x = 2; x *= 3", 6);
    test("def x = 2; x *= 3; x", 6);
    test("var x = 2; x *= 3", 6);
    test("var x = 2; x *= 3; x", 6);
    test("long x = 2; x *= 3", 6L);
    test("long x = 2; x *= 3; x", 6L);
    test("double x = 2; x *= 3", 6.0D);
    test("double x = 2; x *= 3; x", 6.0D);
    test("Decimal x = 2.0; x *= 3", "#6.0");
    test("Decimal x = 2.0; x *= 3; x", "#6.0");
    test("var x = 2.0; x *= 3", "#6.0");
    test("var x = 2.0; x *= 3; x", "#6.0");
    test("def x = 2; int y = 3; x *= y", 6);
    test("def x = 2; int y = 3; x *= y; x", 6);
    test("def x = 2; int y = 3; x *= (y *= 2)", 12);
    test("def x = 2; int y = 3; x *= (y *= 2); x", 12);
    test("def x = 2; int y = 3; x *= (y *= 2); x + y", 18);
    test("def x = 2; int y = 3; x *= y *= 2", 12);
    test("def x = 2; int y = 3; x *= y *= 2; x", 12);
    test("def x = 2; int y = 3; x *= y *= 2; x + y", 18);
    test("def x = 2; int y = 3; y *= x; y", 6);
    test("def x = 2; int y = 3; y *= x", 6);
    test("def x = 2; int y = 3; def z = 4; x += y *= z += 2", 20);
    test("def x = 2; int y = 3; def z = 4; x += y *= z += 2; x", 20);
    test("def x = 2; int y = 3; def z = 4; x += y *= z += 2; y", 18);
    test("def x = 2; int y = 3; def z = 4; x += y *= z += 2; z", 6);
    test("double x = 2; int y = 3; y *= x", 6);
    test("double x = 2; int y = 3; x *= y", 6.0D);
    test("def x = 2.0D; int y = 3; y *= x", 6);
    test("def x = 2.0D; int y = 3; x *= y", 6.0D);
  }

  @Test public void slashEquals(){
    test("int x = 18; x /= 3", 6);
    test("int x = 18; (x /= 3) + (x /= 2)", 9);
    test("int x = 18; (x /= 3) + (x /= 2); x", 3);
    test("int x = 18; x /= 3; x", 6);
    test("def x = 18; x /= 3", 6);
    test("def x = 18; x /= 3; x", 6);
    test("var x = 18; x /= 3", 6);
    test("var x = 18; x /= 3; x", 6);
    test("long x = 18; x /= 3", 6L);
    test("long x = 18; x /= 3; x", 6L);
    test("double x = 18; x /= 3", 6.0D);
    test("double x = 18; x /= 3; x", 6.0D);
    test("Decimal x = 18.0; x /= 3", "#6.0");
    test("Decimal x = 18.0; x /= 3; x", "#6.0");
    test("var x = 18.0; x /= 3", "#6.0");
    test("var x = 18.0; x /= 3; x", "#6.0");
    test("def x = 18; int y = 3; x /= y", 6);
    test("def x = 18; int y = 3; x /= y; x", 6);
    test("def x = 18; int y = 6; x /= (y /= 2)", 6);
    test("def x = 18; int y = 6; x /= (y /= 2); x", 6);
    test("def x = 18; int y = 6; x /= (y /= 2); x + y", 9);
    test("def x = 18; int y = 6; x /= y /= 2", 6);
    test("def x = 18; int y = 6; x /= y /= 2; x", 6);
    test("def x = 18; int y = 6; x /= y /= 2; x + y", 9);
    test("def x = 6; int y = 18; y /= x; y", 3);
    test("def x = 6; int y = 18; y /= x", 3);
    test("double x = 3; int y = 18; y /= x", 6);
    test("double x = 18; int y = 3; x /= y", 6.0D);
    test("def x = 3.0D; int y = 18; y /= x", 6);
    test("def x = 18.0D; int y = 3; x /= y", 6.0D);
    testFail("int x = 1; x /= 0", "divide by zero");
    testFail("long x = 1; x /= 0", "divide by zero");
    test("double x = 1; x /= 0", Double.POSITIVE_INFINITY);
    testFail("Decimal x = 1; x /= 0", "divide by zero");
  }

  @Test public void percentEquals(){
    test("int x = 18; x %= 7", 4);
    test("int x = 18; x %= 7; x", 4);
    test("int x = 18; (x %= 7) + (x %= 3)", 5);
    test("int x = 18; (x %= 7) + (x %= 3); x", 1);
    test("int x = 18; x %= 7; x", 4);
    test("def x = 18; x %= 7", 4);
    test("def x = 18; x %= 7; x", 4);
    test("var x = 18; x %= 7", 4);
    test("var x = 18; x %= 7; x", 4);
    test("long x = 18; x %= 7", 4L);
    test("long x = 18; x %= 7; x", 4L);
    test("double x = 18; x %= 7", 4.0D);
    test("double x = 18; x %= 7; x", 4.0D);
    test("Decimal x = 18.0; x %= 7", "#4.0");
    test("Decimal x = 18.0; x %= 7; x", "#4.0");
    test("var x = 18.0; x %= 7", "#4.0");
    test("var x = 18.0; x %= 7; x", "#4.0");
    test("def x = 18; int y = 7; x %= y", 4);
    test("def x = 18; int y = 7; x %= y; x", 4);
    test("def x = 18; int y = 7; x %= (y %= 4)", 0);
    test("def x = 18; int y = 7; x %= (y %= 4); x", 0);
    test("def x = 18; int y = 7; x %= (y %= 4); x + y", 3);
    test("def x = 18; int y = 7; x %= y %= 4", 0);
    test("def x = 18; int y = 7; x %= y %= 4; x", 0);
    test("def x = 18; int y = 7; x %= y %= 4; x + y", 3);
    test("def x = 7; int y = 18; y %= x; y", 4);
    test("def x = 7; int y = 18; y %= x", 4);
    test("double x = 7; int y = 18; y %= x", 4);
    test("double x = 18; int y = 7; x %= y", 4.0D);
    test("def x = 7.0D; int y = 18; y %= x", 4);
    test("def x = 18.0D; int y = 7; x %= y", 4.0D);
  }

  @Test
  public void defVariableArithmetic() {
    test("def v = 1", 1);
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

    test("def x = false; !x", true);
    test("def x = true; !x", false);
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

    testFail("def x = 1; x / 0", "divide by zero");
    testFail("def x = 1.0; x / 0", "divide by zero");
    testFail("def x = 1L; x / 0", "divide by zero");
    test("def x = 1.0D; x / 0", Double.POSITIVE_INFINITY);
  }

  @Test
  public void explicitReturnFromScript() {
    test("double v = 2; return v", 2D);
    test("int v = 2; { v = v + 1; return v } v = v + 3", 3);
    test("String v = '2'; { v = v + 1; { return v }}", "21");
    test("var v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("Decimal v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("def v = '2'; { v = v + 1; { return v }}", "21");
    testFail("double v = 2; return v; v = v + 1", "unreachable statement");
    testFail("double v = 2; return v; { v = v + 1 }", "unreachable statement");
  }

  @Test
  public void implicitReturnFromScript() {
    test("double v = 2; v", 2D);
    test("int v = 2; { v = v + 1; v }", 3);
    test("String v = '2'; { v = v + 1; { v }}", "21");
    test("var v = 2.0; { v = v + 1; { v }}", "#3.0");
    test("def v = '2'; { v = v + 1; { v }}", "21");
  }

  @Test
  public void constExprArithmeticPrecedence() {
    test("1 + -2 * -3 + 4", 11);
    test("1 + (2 + 3) * 4 - 5", 16);
    test("13 + 12 % 7", 18);
    test("13 + 12 % 7 - 3", 15);
    test("13 + 12 % ++6 - 3", 15);
  }

  @Test
  public void constStringConcatenation() {
    testFail("'abc' - '123'", "non-numeric operand");
    test("'abc' + '123'", "abc123");
    test("'abc' + null", "abcnull");
    testFail("null + 'abc'", "left-hand side of '+' cannot be null");
    test("'abc' + 'def' + 'ghi'", "abcdefghi");
    test("'abc' + ('1' + '2' + '3') + 'def'", "abc123def");
    test("'' + 'abc'", "abc");
    test("'abc' + ''", "abc");
  }

  @Test
  public void constStringRepeat() {
    test("'abc' * 2", "abcabc");
    test("'abc' * 0", "");
    testFail("'abc' * -1", "string repeat count must be >= 0");
  }

  @Test
  public void defaultValues() {
    test("boolean x", false);
    test("boolean x; x", false);
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

  @Test
  public void prefixIncOrDec() {
    testFail("++null", "null value encountered");
    testFail("--null", "null value encountered");
    test("++1", 2);
    test("--1", 0);
    test("int x = 1; ++x", 2);
    test("int x = 1; --x", 0);
    test("int x = 3; --x + --x", 3);
    test("int x = 3; ++x + ++x", 9);
    test("++1L", 2L);
    test("--1L", 0L);
    test("long x = 1; ++x", 2L);
    test("long x = 1; --x", 0L);
    test("long x = 3; --x + --x", 3L);
    test("long x = 3; ++x + ++x", 9L);
    test("++1D", 2D);
    test("--1D", 0D);
    test("double x = 1; ++x", 2D);
    test("double x = 1; --x", 0D);
    test("double x = 1.5D; --x", 0.5D);
    test("double x = 3; --x + --x", 3D);
    test("double x = 3.5D; --x + --x", 4D);
    test("double x = 3.5D; ++x + ++x", 10D);
    test("++1.0", "#2.0");
    test("--1.0", "#0.0");
    test("Decimal x = 1; ++x", "#2");
    test("Decimal x = 1.5; ++x", "#2.5");
    test("Decimal x = 1; --x", "#0");
    test("Decimal x = 1.5; --x", "#0.5");
    test("Decimal x = 3; --x + --x", "#3");
    test("Decimal x = 3.5; --x + ++x", "#6.0");

    test("def x = 1; ++x", 2);
    test("def x = 1; --x", 0);
    test("def x = 3; --x + --x", 3);
    test("def x = 3; ++x + ++x", 9);
    test("def x = 1L; ++x", 2L);
    test("def x = 1L; --x", 0L);
    test("def x = 3L; --x + --x", 3L);
    test("def x = 3L; ++x + ++x", 9L);
    test("def x = 1D; ++x", 2D);
    test("def x = 1D; --x", 0D);
    test("def x = 1.5D; --x", 0.5D);
    test("def x = 3D; --x + --x", 3D);
    test("def x = 3.5D; --x + --x", 4D);
    test("def x = 3.5D; ++x + ++x", 10D);
    test("def x = 1.0; ++x", "#2.0");
    test("def x = 1.5; ++x", "#2.5");
    test("def x = 1.0; --x", "#0.0");
    test("def x = 1.5; --x", "#0.5");
    test("def x = 3.0; --x + --x", "#3.0");
    test("def x = 3.5; --x + ++x", "#6.0");

    test("def x = 1; (x + x)++", 2);
    test("def x = 1; (x + x)++; x", 1);
    testFail("def x = 1; (x + x)++ ++", "expected end of expression");
  }

  @Test
  public void postfixIncOrDec() {
    testFail("null++", "null value encountered");
    testFail("null--", "null value encountered");
    test("1++", 1);
    test("1--", 1);
    test("int x = 1; x++", 1);
    test("int x = 1; x--", 1);
    test("int x = 1; x++; x", 2);
    test("int x = 1; x--; x", 0);
    test("int x = 3; x-- + x--", 5);
    test("int x = 3; x-- + x--; x", 1);
    test("int x = 3; x++ + x++", 7);
    test("int x = 3; x++ + x++; x", 5);
    test("2L++", 2L);
    test("0L--", 0L);
    test("long x = 1; x++", 1L);
    test("long x = 1; x++; x", 2L);
    test("long x = 1; x--", 1L);
    test("long x = 1; x--; x", 0L);
    test("long x = 3; x-- + x--", 5L);
    test("long x = 3; x-- + x--; x", 1L);
    test("long x = 3; x++ + x++", 7L);
    test("long x = 3; x++ + x++; x", 5L);
    test("1D++", 1D);
    test("1D--", 1D);
    test("double x = 1D; x++", 1D);
    test("double x = 1D; x++; x", 2D);
    test("double x = 1D; x--", 1D);
    test("double x = 1D; x--; x", 0D);
    test("double x = 1.5D; x--", 1.5D);
    test("double x = 1.5D; x--; x", 0.5D);
    test("double x = 3D; x-- + x--", 5D);
    test("double x = 3D; x-- + x--; x", 1D);
    test("double x = 3.5D; x-- + x--", 6D);
    test("double x = 3.5D; x-- + x--; x", 1.5D);
    test("double x = 3.5D; x++ + x++", 8D);
    test("double x = 3.5D; x++ + x++; x", 5.5D);
    test("1.0++", "#1.0");
    test("1.0--", "#1.0");
    test("Decimal x = 1; x++", "#1");
    test("Decimal x = 1; x++; x", "#2");
    test("Decimal x = 1.5; x++", "#1.5");
    test("Decimal x = 1.5; x++; x", "#2.5");
    test("Decimal x = 1; x--", "#1");
    test("Decimal x = 1; x--; x", "#0");
    test("Decimal x = 1.5; x--", "#1.5");
    test("Decimal x = 1.5; x--; x", "#0.5");
    test("Decimal x = 3; x-- + x--", "#5");
    test("Decimal x = 3; x-- + x--; x", "#1");
    test("Decimal x = 3.5; x-- + x++", "#6.0");
    test("Decimal x = 3.5; x-- + x++; x", "#3.5");

    test("def x = 1; x++", 1);
    test("def x = 1; x--", 1);
    test("def x = 1; x++; x", 2);
    test("def x = 1; x--; x", 0);
    test("def x = 3; x-- + x--", 5);
    test("def x = 3; x-- + x--; x", 1);
    test("def x = 3; x++ + x++", 7);
    test("def x = 3; x++ + x++; x", 5);
    test("def x = 1L; x++", 1L);
    test("def x = 1L; x++; x", 2L);
    test("def x = 1L; x--", 1L);
    test("def x = 1L; x--; x", 0L);
    test("def x = 3L; x-- + x--", 5L);
    test("def x = 3L; x-- + x--; x", 1L);
    test("def x = 3L; x++ + x++", 7L);
    test("def x = 3L; x++ + x++; x", 5L);
    test("def x = 1.0D; x++", 1D);
    test("def x = 1.0D; x++; x", 2D);
    test("def x = 1.0D; x--", 1D);
    test("def x = 1.0D; x--; x", 0D);
    test("def x = 1.5D; x--", 1.5D);
    test("def x = 1.5D; x--; x", 0.5D);
    test("def x = 3.0D; x-- + x--", 5D);
    test("def x = 3.0D; x-- + x--; x", 1D);
    test("def x = 3.5D; x-- + x--", 6D);
    test("def x = 3.5D; x-- + x--; x", 1.5D);
    test("def x = 3.5D; x++ + x++", 8D);
    test("def x = 3.5D; x++ + x++; x", 5.5D);
    test("def x = 1.0; x++", "#1.0");
    test("def x = 1.0; x++; x", "#2.0");
    test("def x = 1.5; x++", "#1.5");
    test("def x = 1.5; x++; x", "#2.5");
    test("def x = 1.0; x--", "#1.0");
    test("def x = 1.0; x--; x", "#0.0");
    test("def x = 1.5; x--", "#1.5");
    test("def x = 1.5; x--; x", "#0.5");
    test("def x = 3.0; x-- + x--", "#5.0");
    test("def x = 3.0; x-- + x--; x", "#1.0");
    test("def x = 3.5; x-- + x++", "#6.0");
    test("def x = 3.5; x-- + x++; x", "#3.5");

    test("def x = 1; ++(x + x)", 3);
    test("def x = 1; ++(x + x)--", 3);
    test("def x = 1; ++x--", 2);
    test("def x = 1; ++x--; x", 0);
    test("def x = 1; ++(x--); x", 0);
    test("--1", 0);
    test("----1", -1);
    test("def x = 1; -- -- -- ++x--", -1);

    test("def x = 1; def y = 3; x + --y * ++y++ - 2", 5);
    test("def x = 1; def y = 3; x + --y * ++++y++ - 2", 7);
    test("def x = 1; def y = 3; x + --y * ++++y++ - 2; y", 3);
  }

  @Test
  public void endOfLine() {
    test("1 + \n2", 3);
    test("def x =\n1 + \n2", 3);
    test("def x =\n1 + \n(2\n)", 3);
    test("def x =\n1 + (\n2)\n", 3);
    test("def x =\n1 + (\n2)\n", 3);
  }

  @Test public void varDecl() {
    testFail("def x = x + 1", "variable initialisation cannot refer to itself");
  }

  // TODO: not yet supported
  public void exprStrings() {
    test("\"${\"${1}\"}\"", "1");
    test("\"${}\"", "null");
    test("\"'a'\"", "'a'");
    test("\"${1 + 2}\"", "3");
    test("\"x${1 + 2}y\"", "x3y");
    test("\"\"\"x${1 + 2}y\n${3.0*3}\"\"\"", "x3y\n9.0");
    test("\"x${\"1\" + 2}y\"", "x12y");
    test("\"x${\"${2*4}\" + 2}y\"", "x82y");
    test("boolean x; \"$x${\"${2*4}\" + 2}y\"", "false82y");
    test("boolean x; boolean y = true; \"$x${\"${\"$x\"*4}\" + 2}$y\"", "falsefalsefalsefalsefalse2true");
    test("\"x = ${ def x = 1 + 2; x}\"", "x = 3");
    test("def x = 3;\"x = ${x}\"", "x = 3");
    test("def x = 3;\"x = $x\"", "x = 3");
  }

  @Test public void listLiterals() {
    test("[]", List.of());
    test("[1]", List.of(1));
    testFail("[1,", "unexpected EOF");
    test("[1,2,3]", List.of(1,2,3));
    test("[1,2+3,3]", List.of(1,5,3));
    test("[[]]", List.of(List.of()));
    test("[[1]]", List.of(List.of(1)));
    test("[[1],2]", List.of(List.of(1),2));
    test("[][0]", null);
    test("[1,2,3][0]", 1);
    test("[1,2,3][3]", null);
    test("[1,[2,3,4],5][1]", List.of(2,3,4));
  }

  @Test public void mapLiterals() {
    test("[:]", new HashMap<>());
    test("[a:1]", Map.of("a",1));
    testFail("[:", "unexpected token");
    testFail("[:123]", "unexpected token");
    test("[for:1]", Map.of("for",1));
    test("['for':1]", Map.of("for",1));
    test("[a:1,b:2]", Map.of("a",1, "b", 2));
    test("['a':1,'b':2]", Map.of("a",1, "b", 2));
    test("[('a'+'b'):1,b:2]", Map.of("ab",1, "b", 2));
    test("[\"ab\":1,b:2]", Map.of("ab",1, "b", 2));
    test("[a:1,b:[c:2]]", Map.of("a",1, "b", Map.of("c",2)));
    test("{:}", new HashMap<>());
    test("{a:1}", Map.of("a",1));
    testFail("{:", "unexpected token");
    test("{for:1}", Map.of("for",1));
    test("{'for':1}", Map.of("for",1));
    test("{a:1,b:2}", Map.of("a",1, "b", 2));
    test("{a:1,b:{c:2}}", Map.of("a",1, "b", Map.of("c",2)));
    test("[a:1].a", 1);
    test("[a:[b:2]].a.b", 2);
    test("[a:[b:2]]?.a?.b", 2);
    test("[a:[b:2]]?.a?['b']", 2);
    test("[a:[b:2]]['a']['b']", 2);
    test("[a:[b:2]].c", null);
  }

//  @Test public void notYetImplemented() {
//    test("[\"a${1+2}\":1,b:2]", Map.of("a3",1, "b", 2))
//  }

  @Test public void listMapVariables() {
    testFail("Map x = 1", "cannot convert");
    testFail("List x = 1", "cannot convert");
    test("Map x = [a:1]; x.a", 1);
    test("Map x = [a:1]", Map.of("a", 1));
    test("Map x = [a:1]; 1", 1);
    testFail("List list = [1]; list.a", "invalid object type");
    testFail("int x = 1; x.a", "invalid object type");
    testFail("int x = 1; x[0]", "invalid object type");
    test("Map map = [:]; map[0]", null);
    testFail("Map map = [:]; map = 1", "cannot convert from type of right hand side");
    testFail("List list = []; list = 1", "cannot convert from type of right hand side");

    test("var x = [a:1]; x.a", 1);
    test("var x = [a:1]", Map.of("a", 1));
    test("var x = [a:1]; 1", 1);
    testFail("var list = [1]; list.a", "invalid object type");
    testFail("var x = 1; x.a", "invalid object type");
    testFail("var x = 1; x[0]", "invalid object type");
    test("var map = [:]; map[0]", null);
    testFail("var map = [:]; map = 1", "cannot convert from type of right hand side");
    testFail("var list = []; list = 1", "cannot convert from type of right hand side");

    test("def m = [a:1]", Map.of("a",1));
    test("def m = [1]", List.of(1));
    test("def m = [a:1]; m.a", 1);
    test("def m = [a:1]; m.b", null);
    test("def m = [a:[b:2]]; m.a.b", 2);
    test("def m = [a:[b:2]]; m?.a?.b", 2);
    test("def m = [a:[b:2]]; m?['a']?['b']", 2);
    test("def m = [a:[b:2]]; m?['a'].b", 2);
    test("def m = [a:[b:2]]; m.a.x?.y", null);
    testFail("def m = [a:[b:2]]; m.a.x?.y.z", "null value");

    test("def x = [1,2,3]", List.of(1,2,3));
    test("def x = []", List.of());
    test("def x = []; x[0]", null);
    test("def x = [1,2,3][4]; x", null);
    test("def x = [1,2,3]; x[1]", 2);
    testFail("def x = []; x[-1]", "index must be >= 0");
    test("def x = []; def y = 7; x[y + y * y - y]", null);
    test("def x = [1,2,3,4]; def y = 7; x[y + y * y - y * y - 5]", 3);
    test("def x = [0,1,2,3,4]; def y = [a:7]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.0]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.5]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.5D]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7L]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,[1,2,3],4]; def y = 2; x[1 + y + 3 - y - 3][y/2]", 2);
  }

  /*@Test*/ public void listMapAddition() {
    test("List list = []; list += [1,2,3]", List.of(1,2,3));
  }

  @Test public void fieldAssignments() {
    testFail("Map m = [a:1]; m*a = 2", "invalid lvalue");
    test("Map m = [:]; m.a = 1", 1);
    test("Map m = [:]; m.a = 1; m.a", 1);
    test("var m = [:]; m.a = 1", 1);
    test("var m = [:]; m.a = 1; m.a", 1);
    test("def m = [:]; m.a = 1", 1);
    test("def m = [:]; m.a = 1; m.a", 1);
    test("Map m; m.a = 1; m.a", 1);
    test("Map m; m.a = 1", 1);

    // def without initialiser is always null. We don't automatically create
    // the value for m itself. Only subfields are automatically created if
    // required when used as lvalues.
    testFail("def m; m.a = 1", "null value");

    test("Map m; m.a.b = 1", 1);
    test("Map m; m.a.b = 1; m.a", Map.of("b",1));
    test("Map m; m.a.b.c = 1; m.a.b", Map.of("c",1));
    test("Map m; m.a.b.c = 1", 1);

    test("Map m; m.a[2] = 1", 1);
    test("Map m; m.a[2] = 1; m.a[2]", 1);
    test("Map m; m.a[2] = 1; m.a", Arrays.asList(null, null, 1));
    test("Map m; m.a[3].b[2].c = 1", 1);

    test("var m = [:]; m.a.b.c = 1; m.a.b", Map.of("c",1));
    test("def m = [:]; m.a.b.c = 1; m.a.b", Map.of("c",1));
    test("Map m = [:]; m.('a' + 'b').c = 1; m.ab.c", 1);

    test("Map m = [:]; m.a += 4", 4);
    test("Map m = [:]; m.a.b += 4", 4);
    test("def m = [:]; m.a.b += 4", 4);

    testFail("def x = [:]; x.a['b'] = 1", "non-numeric value for index");
    test("Map m = [:]; m.a[1] = 4; m.a[1]", 4);
    test("Map m = [:]; m.a[1] += 4; m.a[1]", 4);
    test("def m = [:]; m.a[2].b += 4; m.a[2].b", 4);

    test("def m = [:]; m.a[0].c += 4; m.a[0].c", 4);
    test("Map m = [a:1]; m.a++", 1);
    test("Map m = [a:1]; m.a++; m.a", 2);
    test("Map m = [a:1]; ++m.a", 2);
    test("Map m = [a:1]; ++m.a; m.a", 2);
    test("Map m = [:]; m.a.b++", 0);
    test("Map m = [:]; ++m.a.b", 1);

    test("def m = []; m[0].a.b = 1", 1);
    test("def m = []; m[0].a.b += 1", 1);
    test("def m = []; m[0].a.b += 2; m[0].a.b ", 2);
    test("def m = []; m[0]++", 0);
    test("def m = []; m[0]++; m[0]", 1);
    test("def m = []; ++m[0]", 1);
    test("def m = []; ++m[0]; m[0]", 1);
    test("def m = []; m[1].a++", 0);
    test("def m = []; m[1].a++; m[1].a", 1);
    test("def m = []; ++m[1].a", 1);
    test("def m = []; ++m[1].a; m[1].a", 1);
  }

  @Test public void conditionalAssignment() {
    test("def x = [:]; int i; i ?= x.a", null);
    test("def x; int i; i ?= x.a", null);
    test("def x = [a:3]; long y; y ?= x.a", 3L);
    test("Map x; int i; i ?= x.a", null);
    test("Map x = [a:[:]]; int i = 5; i ?= x.a.b.c; i", 5);
    test("Map x = [a:[:]]; int i = 5; i ?= x.a.b[0].c; i", 5);
    test("Map x = [a:[]]; int i = 5; i ?= x.a[2].b[0].c; i", 5);
    test("Map x = [a:[]]; int i = 5; i ?= x.a[2]?.b?[0].c; i", 5);
    test("Map m = [a:[]]; m.a ?= m.a", List.of());
    test("Map m = [a:[]]; m.a ?= m.b", null);
    test("Map m = [a:3]; m.a ?= m.b; m.a", 3);
    test("Map m = [a:[]]; def x; m.a ?= x", null);
    test("Map m; def x; m.a.b.c ?= x.a", null);
    test("Map m; def x; m.a.b.c ?= x.a; m.a", null);
    test("Map m; def x; m.a.b.c ?= x.a; m.a?.b", null);
    test("Map m; def x = [a:3]; m.a.b.c ?= x.a; m.a?.b", Map.of("c",3));
  }

  @Test public void testStuff() {
    test("def x; int i; i ?= x.a", null);
  }

  @Test public void nullValues() {
    test("String s = null", null);
    testFail("int i = null", "cannot convert null");
    testFail("1.0 + null", "non-numeric operand");
    testFail("def x; int i = x", "cannot convert null");
  }

  @Test public void stringIndexing() {
    test("'abc'[0]", "a");
    testFail("''[0]", "index (0) too large");
    test("def x = 'abcdef'; def i = 3; x[i]", "d");
    test("def x = 'abcdef'; def i = 3; x?[i]", "d");
    test("String x = 'abcdef'; def i = 3; x[i]", "d");
    test("String x = 'abcdef'; def i = 3; x?[i]", "d");
    test("var x = 'abcdef'; def i = 3; x[i]", "d");
    test("var x = 'abcdef'; def i = 3; x?[i]", "d");
    test("def x; x?[0]", null);
    testFail("String s = 'abc'; s[1] = s[2]", "invalid object type");
    testFail("String s = 'abc'; s.a", "invalid object type");
    testFail("def s = 'abc'; s.a", "field access not supported");
  }

}