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

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest extends BaseTest {

  public static Object timestamp(String source, int offset, Object args) {
    return System.currentTimeMillis();
  }

  @Test public void comments() {
    test("  // Strip '#' comments\n1", 1);
    test("[1,2,3].map{it}", List.of(1,2,3));
    test("[1,2,3].map{\n\nit}", List.of(1,2,3));
    test("[1,2,3].map{\n // Strip '#' comments\nit}", List.of(1,2,3));
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
    test(Byte.toString(Byte.MAX_VALUE), (int) Byte.MAX_VALUE);
    test(Integer.toString(Byte.MAX_VALUE + 1), (int) Byte.MAX_VALUE + 1);
    test(Short.toString(Short.MAX_VALUE), (int) Short.MAX_VALUE);
    test(Integer.toString(Short.MAX_VALUE + 1), (int) Short.MAX_VALUE + 1);
    test(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    testError("" + (Integer.MAX_VALUE + 1L), "number too large");
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
    test("[] && true", false);
    test("[1] && true", true);
    test("[:] && true", false);
    test("[a:1] && true", true);
    test("def x; [a:1].each{ x = it && true }; x", true);
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
    testError("-true", "cannot be applied to type");
    testError("-'abc'", "cannot be applied to type");
    testError("+true", "cannot be applied to type");
    testError("+'abc'", "cannot be applied to type");
    test("!true", false);
    test("!false", true);
    test("!!true", true);
    test("!!false", false);
    test("!(!!true)", false);
    test("!!(!false)", true);
    test("true && true", true);
    test("false && true", false);
    test("true && false", false);
    test("false && false", false);
    test("true || true", true);
    test("false || true", true);
    test("true || false", true);
    test("false || false", false);
    test("!false || false", true);
    test("false || true && false", false);
    test("true  || true && false", true);
    test("true  || true && true", true);
    test("false || !false && true", true);
    test("null && true", false);
    test("true && null", false);
    test("null && null", false);
    test("null || true", true);
    test("true || null", true);
    test("false || null", false);
    test("null || false", false);
  }

  @Test public void simpleConstExpressions() {
    testError("1 + true", "non-numeric operand for right-hand side");
    testError("false + 1", "non-numeric operand for left-hand side");
    testError("1 - 'abc'", "non-numeric operand for right-hand side");
    testError("'abc' - 1", "non-numeric operand for left-hand side");
    testError("false + true", "non-numeric operand for left-hand side");
    testError("1 + null", "null operand for right-hand side");
    testError("null + 1", "null operand for left-hand side");
    testError("1/0", "divide by zero");
    test("1.0D/0", Double.POSITIVE_INFINITY);
    test("-1.0D/0", Double.NEGATIVE_INFINITY);
    testError("1L/0", "divide by zero");
    testError("1.0/0", "divide by zero");
    testError("1/0.0", "divide by zero");
    testError("1%0", "divide by zero");
    test("1.0D%0", Double.NaN);
    test("-1.0D%0", Double.NaN);
    testError("1L%0", "divide by zero");
    testError("1.0%0", "divide by zero");
    testError("1%0.0", "divide by zero");
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

  @Test public void simpleVariableArithmetic() {
    testError("int x = 1; boolean b = true; x + b", "non-numeric operand for right-hand side");
    testError("def x = 1; def b = true; x + b", "non-numeric operand for right-hand side");
    testError("boolean b = false; int x = 1; b + x", "non-numeric operand for left-hand side");
    testError("def b = false; def x = 1; b + x", "non-numeric operand for left-hand side");
    testError("int i = 1; String s = 'abc'; i - s", "non-numeric operand for right-hand side");
    testError("def i = 1; def s = 'abc'; i - s", "non-numeric operand for right-hand side");
    testError("'abc' - 1", "non-numeric operand for left-hand side");
    testError("String s = 'abc'; int x = 1; s - x", "non-numeric operand for left-hand side");
    testError("def s = 'abc'; def x = 1; s - x", "non-numeric operand for left-hand side");
    testError("boolean b1 = false; boolean b2 = true; b1 + b2", "non-numeric operand for left-hand side");
    testError("def b1 = false; def b2 = true; b1 + b2", "non-numeric operand for left-hand side");
    testError("int i = 1; def x = null; i + x", "null operand for right-hand side");
    testError("def i = 1; def x = null; i + x", "null operand for right-hand side");
    testError("def x = null; int i = 1; x + i", "null operand for left-hand side");
    testError("def x = null; def i = 1; x + i", "null operand for left-hand side");
    testError("int x = 1; int y = 0; x/y", "divide by zero");
    testError("def x = 1; def y = 0; x/y", "divide by zero");
    test("double x = 1.0D; int y = 0; x/y", Double.POSITIVE_INFINITY);
    test("def x = 1.0D; def y = 0; x/y", Double.POSITIVE_INFINITY);
    test("double x = -1.0D; int y = 0; x / y", Double.NEGATIVE_INFINITY);
    test("double x = -1.0D; double y = 0; x / y", Double.NEGATIVE_INFINITY);
    test("def x = -1.0D; def y = 0; x / y", Double.NEGATIVE_INFINITY);
    testError("long x = 1L; int y = 0; x/y", "divide by zero");
    testError("def x = 1L; def y = 0; x/y", "divide by zero");
    testError("Decimal x = 1.0; int y = 0; x/y", "divide by zero");
    testError("Decimal x = 1.0; Decimal y = 0; x/y", "divide by zero");
    testError("Decimal x = 1.0; long y = 0; x/y", "divide by zero");
    testError("Decimal x = 1.0; double y = 0; x/y", "divide by zero");
    testError("def x = 1.0; def y = 0; x/y", "divide by zero");
    testError("def x = 1.0; def y = 0D; x/y", "divide by zero");
    testError("def x = 1.0; def y = 0L; x/y", "divide by zero");
    testError("def x = 1.0; def y = 0.0; x/y", "divide by zero");
    testError("int x = 1; Decimal y = 0.0; x/y", "divide by zero");
    testError("def x = 1; def y = 0.0; x/y", "divide by zero");
    testError("int x = 1; int y = 0; x % y", "divide by zero");
    testError("def x = 1; def y = 0; x % y", "divide by zero");
    test("double x = 1.0D; int y = 0; x % y", Double.NaN);
    test("def x = 1.0D; def y = 0; x % y", Double.NaN);
    test("def x = 1.0D; def y = 0L; x % y", Double.NaN);
    test("double x = -1.0D; int y = 0; x % y", Double.NaN);
    test("def x = -1.0D; def y = 0; x % y", Double.NaN);
    testError("long x = 1L; int y = 0; x % y", "divide by zero");
    testError("def x = 1L; def y = 0; x % y", "divide by zero");
    testError("Decimal x = 1.0; int y = 0; x % y", "divide by zero");
    testError("def x = 1.0; def y = 0; x % y", "divide by zero");
    testError("int x = 1; Decimal y = 0.0; x % y", "divide by zero");
    testError("def x = 1; def y = 0.0; x % y", "divide by zero");
    test("int x = 1; int y = 2; x + y", 3);
    test("def x = 1; def y = 2; x + y", 3);
    test("int x = 1; def y = 2; x + y", 3);
    test("def x = 1; int y = 2; x + y", 3);
    test("int x = 1; int y = 2; x - y", -1);
    test("def x = 1; def y = 2; x - y", -1);
    test("def x = 1; int y = 2; x - y", -1);
    test("int x = 1; def y = 2; x - y", -1);
    test("int x = 1; int y = 2; x - -y", 3);
    test("def x = 1; def y = 2; x - -y", 3);
    test("def x = 1; int y = 2; x - -y", 3);
    test("int x = 1; def y = 2; x - -y", 3);
    test("int x = 2; int y = 3; x * y", 6);
    test("def x = 2; def y = 3; x * y", 6);
    test("int x = 2; def y = 3; x * y", 6);
    test("def x = 2; int y = 3; x * y", 6);
    test("int x = 6; int y = 3; x / y", 2);
    test("def x = 6; def y = 3; x / y", 2);
    test("int x = 6; def y = 3; x / y", 2);
    test("def x = 6; int y = 3; x / y", 2);
    test("int x = 8; int y = 5; x % y", 3);
    test("def x = 8; def y = 5; x % y", 3);
    test("int x = 8; def y = 5; x % y", 3);
    test("def x = 8; int y = 5; x % y", 3);
    test("int x = 8; int y = 1; x % y", 0);
    test("def x = 8; def y = 1; x % y", 0);
    test("int x = 8; def y = 1; x % y", 0);
    test("def x = 8; int y = 1; x % y", 0);
    test("long x = 1L; long y = 2L; x + y", 3L);
    test("def x = 1L; def y = 2L; x + y", 3L);
    test("long x = 1L; def y = 2L; x + y", 3L);
    test("def x = 1L; long y = 2L; x + y", 3L);
    test("long x = 1L; long y = 2L; x - y", -1L);
    test("def x = 1L; def y = 2L; x - y", -1L);
    test("long x = 1L; def y = 2L; x - y", -1L);
    test("def x = 1L; long y = 2L; x - y", -1L);
    test("long x = 1L; long y = 2L; x - -y", 3L);
    test("def x = 1L; def y = 2L; x - -y", 3L);
    test("long x = 1L; def y = 2L; x - -y", 3L);
    test("def x = 1L; long y = 2L; x - -y", 3L);
    test("long x = 2L; long y = 3L; x * y", 6L);
    test("def x = 2L; def y = 3L; x * y", 6L);
    test("long x = 2L; def y = 3L; x * y", 6L);
    test("def x = 2L; long y = 3L; x * y", 6L);
    test("long x = 6L; long y = 3L; x / y", 2L);
    test("def x = 6L; def y = 3L; x / y", 2L);
    test("long x = 6L; def y = 3L; x / y", 2L);
    test("def x = 6L; long y = 3L; x / y", 2L);
    test("long x = 8L; long y = 5L; x % y", 3L);
    test("def x = 8L; def y = 5L; x % y", 3L);
    test("long x = 8L; def y = 5L; x % y", 3L);
    test("def x = 8L; long y = 5L; x % y", 3L);
    test("int x = 1; long y = 2L; x + y", 3L);
    test("def x = 1; def y = 2L; x + y", 3L);
    test("int x = 1; def y = 2L; x + y", 3L);
    test("def x = 1; long y = 2L; x + y", 3L);
    test("long x = 1L; int y = 2; x - y", -1L);
    test("def x = 1L; def y = 2; x - y", -1L);
    test("long x = 1L; def y = 2; x - y", -1L);
    test("def x = 1L; int y = 2; x - y", -1L);
    test("int x = 1; long y = 2L; x - -y", 3L);
    test("def x = 1; def y = 2L; x - -y", 3L);
    test("int x = 1; def y = 2L; x - -y", 3L);
    test("def x = 1; long y = 2L; x - -y", 3L);
    test("long x = 2L; int y = 3; x * y", 6L);
    test("def x = 2L; def y = 3; x * y", 6L);
    test("long x = 2L; def y = 3; x * y", 6L);
    test("def x = 2L; int y = 3; x * y", 6L);
    test("int x = 6; long y = 3L; x / y", 2L);
    test("def x = 6; def y = 3L; x / y", 2L);
    test("int x = 6; def y = 3L; x / y", 2L);
    test("def x = 6; long y = 3L; x / y", 2L);
    test("long x = 8L; int y = 5; x % y", 3L);
    test("def x = 8L; def y = 5; x % y", 3L);
    test("long x = 8L; def y = 5; x % y", 3L);
    test("def x = 8L; int y = 5; x % y", 3L);
    test("int x = 8; long y = 5L; x % y", 3L);
    test("def x = 8; def y = 5L; x % y", 3L);
    test("int x = 8; def y = 5L; x % y", 3L);
    test("def x = 8; long y = 5L; x % y", 3L);
    test("double x = 1.0D; double y = 2.0D; x + y", 3.0D);
    test("def x = 1.0D; def y = 2.0D; x + y", 3.0D);
    test("double x = 1.0D; def y = 2.0D; x + y", 3.0D);
    test("def x = 1.0D; double y = 2.0D; x + y", 3.0D);
    test("double x = 1.0D; double y = 2.0D; x - y", -1.0D);
    test("def x = 1.0D; def y = 2.0D; x - y", -1.0D);
    test("double x = 1.0D; def y = 2.0D; x - y", -1.0D);
    test("def x = 1.0D; double y = 2.0D; x - y", -1.0D);
    test("double x = 1.0D; double y = 2.0D; x - -y", 3.0D);
    test("def x = 1.0D; def y = 2.0D; x - -y", 3.0D);
    test("double x = 1.0D; def y = 2.0D; x - -y", 3.0D);
    test("def x = 1.0D; double y = 2.0D; x - -y", 3.0D);
    test("double x = 2.0D; double y = 3.0D; x * y", 6D);
    test("def x = 2.0D; def y = 3.0D; x * y", 6D);
    test("double x = 2.0D; def y = 3.0D; x * y", 6D);
    test("def x = 2.0D; double y = 3.0D; x * y", 6D);
    test("double x = 6.0D; double y = 3.0D; x / y", 2.0D);
    test("def x = 6.0D; def y = 3.0D; x / y", 2.0D);
    test("double x = 6.0D; def y = 3.0D; x / y", 2.0D);
    test("def x = 6.0D; double y = 3.0D; x / y", 2.0D);
    test("double x = 8.0D; double y = 5.0D; x % y", 3D);
    test("def x = 8.0D; def y = 5.0D; x % y", 3D);
    test("double x = 8.0D; def y = 5.0D; x % y", 3D);
    test("def x = 8.0D; double y = 5.0D; x % y", 3D);
    test("long x = 1L; double y = 2.0D; x + y", 3.0D);
    test("def x = 1L; def y = 2.0D; x + y", 3.0D);
    test("long x = 1L; def y = 2.0D; x + y", 3.0D);
    test("def x = 1L; double y = 2.0D; x + y", 3.0D);
    test("double x = 1.0D; long y = 2L; x - y", -1.0D);
    test("def x = 1.0D; def y = 2L; x - y", -1.0D);
    test("double x = 1.0D; def y = 2L; x - y", -1.0D);
    test("def x = 1.0D; long y = 2L; x - y", -1.0D);
    test("long x = 1L; double y = 2.0D; x - -y", 3.0D);
    test("def x = 1L; def y = 2.0D; x - -y", 3.0D);
    test("long x = 1L; def y = 2.0D; x - -y", 3.0D);
    test("def x = 1L; double y = 2.0D; x - -y", 3.0D);
    test("double x = 2.0D; long y = 3L; x * y", 6D);
    test("def x = 2.0D; def y = 3L; x * y", 6D);
    test("double x = 2.0D; def y = 3L; x * y", 6D);
    test("def x = 2.0D; long y = 3L; x * y", 6D);
    test("long x = 6L; double y = 3.0D; x / y", 2.0D);
    test("def x = 6L; def y = 3.0D; x / y", 2.0D);
    test("long x = 6L; def y = 3.0D; x / y", 2.0D);
    test("def x = 6L; double y = 3.0D; x / y", 2.0D);
    test("double x = 8.0D; long y = 5L; x  % y", 3D);
    test("def x = 8.0D; def y = 5L; x  % y", 3D);
    test("double x = 8.0D; def y = 5L; x % y", 3D);
    test("def x = 8.0D; long y = 5L; x % y", 3D);
    test("Decimal x = 1.0; Decimal y = 2.0; x + y", "#3.0");
    test("def x = 1.0; def y = 2.0; x + y", "#3.0");
    test("Decimal x = 1.0; def y = 2.0; x + y", "#3.0");
    test("def x = 1.0; Decimal y = 2.0; x + y", "#3.0");
    test("Decimal x = 1.0; Decimal y = 2.0; x - y", "#-1.0");
    test("def x = 1.0; def y = 2.0; x - y", "#-1.0");
    test("Decimal x = 1.0; def y = 2.0; x - y", "#-1.0");
    test("def x = 1.0; Decimal y = 2.0; x - y", "#-1.0");
    test("Decimal x = 1.0; Decimal y = 2.0; x - -y", "#3.0");
    test("def x = 1.0; def y = 2.0; x - -y", "#3.0");
    test("Decimal x = 1.0; def y = 2.0; x - -y", "#3.0");
    test("def x = 1.0; Decimal y = 2.0; x - -y", "#3.0");
    test("Decimal x = 2.0; Decimal y = 3.0; x * y", "#6.00");
    test("def x = 2.0; def y = 3.0; x * y", "#6.00");
    test("Decimal x = 2.0; def y = 3.0; x * y", "#6.00");
    test("def x = 2.0; Decimal y = 3.0; x * y", "#6.00");
    test("Decimal x = 6.0; Decimal y = 3.0; x / y", "#2");
    test("def x = 6.0; def y = 3.0; x / y", "#2");
    test("Decimal x = 6.0; def y = 3.0; x / y", "#2");
    test("def x = 6.0; Decimal y = 3.0; x / y", "#2");
    test("Decimal x = 6.0; Decimal y = 7.0; x / y", "#0.85714285714285714286");
    test("def x = 6.0; def y = 7.0; x / y", "#0.85714285714285714286");
    test("Decimal x = 6.0; def y = 7.0; x / y", "#0.85714285714285714286");
    test("def x = 6.0; Decimal y = 7.0; x / y", "#0.85714285714285714286");
    test("Decimal x = 8.0; Decimal y = 5.0; x % y", "#3.0");
    test("def x = 8.0; def y = 5.0; x % y", "#3.0");
    test("Decimal x = 8.0; def y = 5.0; x % y", "#3.0");
    test("def x = 8.0; Decimal y = 5.0; x % y", "#3.0");
    test("int x = 1; double y = 2D; x + y", 3D);
    test("def x = 1; def y = 2D; x + y", 3D);
    test("int x = 1; def y = 2D; x + y", 3D);
    test("def x = 1; double y = 2D; x + y", 3D);
    test("double x = 1D; int y = 2; x - y", -1D);
    test("def x = 1D; def y = 2; x - y", -1D);
    test("double x = 1D; def y = 2; x - y", -1D);
    test("def x = 1D; int y = 2; x - y", -1D);
    test("int x = 1; double y = 2D; x - -y", 3D);
    test("def x = 1; def y = 2D; x - -y", 3D);
    test("int x = 1; def y = 2D; x - -y", 3D);
    test("def x = 1; double y = 2D; x - -y", 3D);
    test("double x = 2D; int y = 3; x * y", 6D);
    test("def x = 2D; def y = 3; x * y", 6D);
    test("double x = 2D; def y = 3; x * y", 6D);
    test("def x = 2D; int y = 3; x * y", 6D);
    test("int x = 6; double y = 3D; x / y", 2D);
    test("def x = 6; def y = 3D; x / y", 2D);
    test("int x = 6; def y = 3D; x / y", 2D);
    test("def x = 6; double y = 3D; x / y", 2D);
    test("double x = 8D; int y = 5; x % y", 3D);
    test("def x = 8D; def y = 5; x % y", 3D);
    test("double x = 8D; def y = 5; x % y", 3D);
    test("def x = 8D; int y = 5; x % y", 3D);
    test("int x = 8; double y = 5D; x % y", 3D);
    test("def x = 8; def y = 5D; x % y", 3D);
    test("int x = 8; def y = 5D; x % y", 3D);
    test("def x = 8; double y = 5D; x % y", 3D);
    test("int x = 1; Decimal y = 2.0; x + y", "#3.0");
    test("def x = 1; def y = 2.0; x + y", "#3.0");
    test("int x = 1; def y = 2.0; x + y", "#3.0");
    test("def x = 1; Decimal y = 2.0; x + y", "#3.0");
    test("Decimal x = 1.0; int y = 2; x - y", "#-1.0");
    test("def x = 1.0; def y = 2; x - y", "#-1.0");
    test("Decimal x = 1.0; def y = 2; x - y", "#-1.0");
    test("def x = 1.0; int y = 2; x - y", "#-1.0");
    test("int x = 1; Decimal y = 2.0; x - -y", "#3.0");
    test("def x = 1; def y = 2.0; x - -y", "#3.0");
    test("int x = 1; def y = 2.0; x - -y", "#3.0");
    test("def x = 1; Decimal y = 2.0; x - -y", "#3.0");
    test("Decimal x = 2.0; int y = 3; x * y", "#6.0");
    test("def x = 2.0; def y = 3; x * y", "#6.0");
    test("Decimal x = 2.0; def y = 3; x * y", "#6.0");
    test("def x = 2.0; int y = 3; x * y", "#6.0");
    test("int x = 6; Decimal y = 3.0; x / y", "#2");
    test("def x = 6; def y = 3.0; x / y", "#2");
    test("int x = 6; def y = 3.0; x / y", "#2");
    test("def x = 6; Decimal y = 3.0; x / y", "#2");
    test("Decimal x = 6.0; int y = 3; x / y", "#2.0");
    test("def x = 6.0; def y = 3; x / y", "#2.0");
    test("Decimal x = 6.0; def y = 3; x / y", "#2.0");
    test("def x = 6.0; int y = 3; x / y", "#2.0");
    test("Decimal x = 8.0; int y = 5; x % y", "#3.0");
    test("def x = 8.0; def y = 5; x % y", "#3.0");
    test("Decimal x = 8.0; def y = 5; x % y", "#3.0");
    test("def x = 8.0; int y = 5; x % y", "#3.0");
    test("int x = 8; Decimal y = 5.0; x % y", "#3.0");
    test("def x = 8; def y = 5.0; x % y", "#3.0");
    test("int x = 8; def y = 5.0; x % y", "#3.0");
    test("def x = 8; Decimal y = 5.0; x % y", "#3.0");
    test("long x = 1L; Decimal y = 2.0; x + y", "#3.0");
    test("def x = 1L; def y = 2.0; x + y", "#3.0");
    test("long x = 1L; def y = 2.0; x + y", "#3.0");
    test("def x = 1L; Decimal y = 2.0; x + y", "#3.0");
    test("Decimal x = 1.0; long y = 2L; x - y", "#-1.0");
    test("def x = 1.0; def y = 2L; x - y", "#-1.0");
    test("Decimal x = 1.0; def y = 2L; x - y", "#-1.0");
    test("def x = 1.0; long y = 2L; x - y", "#-1.0");
    test("long x = 1L; Decimal y = 2.0; x - -y", "#3.0");
    test("def x = 1L; def y = 2.0; x - -y", "#3.0");
    test("long x = 1L; def y = 2.0; x - -y", "#3.0");
    test("def x = 1L; Decimal y = 2.0; x - -y", "#3.0");
    test("Decimal x = 2.0; long y = 3L; x * y", "#6.0");
    test("def x = 2.0; def y = 3L; x * y", "#6.0");
    test("Decimal x = 2.0; def y = 3L; x * y", "#6.0");
    test("def x = 2.0; long y = 3L; x * y", "#6.0");
    test("long x = 6L; Decimal y = 3.0; x / y", "#2");
    test("def x = 6L; def y = 3.0; x / y", "#2");
    test("long x = 6L; def y = 3.0; x / y", "#2");
    test("def x = 6L; Decimal y = 3.0; x / y", "#2");
    test("Decimal x = 8.0; long y = 5L; x % y", "#3.0");
    test("def x = 8.0; def y = 5L; x % y", "#3.0");
    test("Decimal x = 8.0; def y = 5L; x % y", "#3.0");
    test("def x = 8.0; long y = 5L; x % y", "#3.0");
    test("long x = 8L; Decimal y = 5.0; x % y", "#3.0");
    test("def x = 8L; def y = 5.0; x % y", "#3.0");
    test("long x = 8L; def y = 5.0; x % y", "#3.0");
    test("def x = 8L; Decimal y = 5.0; x % y", "#3.0");
    test("double x = 1D; Decimal y = 2.0; x + y", "#3.0");
    test("def x = 1D; def y = 2.0; x + y", "#3.0");
    test("double x = 1D; def y = 2.0; x + y", "#3.0");
    test("def x = 1D; Decimal y = 2.0; x + y", "#3.0");
    test("Decimal x = 1.0; double y = 2D; x - y", "#-1.0");
    test("def x = 1.0; def y = 2D; x - y", "#-1.0");
    test("Decimal x = 1.0; def y = 2D; x - y", "#-1.0");
    test("def x = 1.0; double y = 2D; x - y", "#-1.0");
    test("double x = 1D; Decimal y = 2.0; x - -y", "#3.0");
    test("def x = 1D; def y = 2.0; x - -y", "#3.0");
    test("double x = 1D; def y = 2.0; x - -y", "#3.0");
    test("def x = 1D; Decimal y = 2.0; x - -y", "#3.0");
    test("Decimal x = 2.0; double y = 3D; x * y", "#6.00");
    test("def x = 2.0; def y = 3D; x * y", "#6.00");
    test("Decimal x = 2.0; def y = 3D; x * y", "#6.00");
    test("def x = 2.0; double y = 3D; x * y", "#6.00");
    test("double x = 6D; Decimal y = 3.0; x / y", "#2");
    test("def x = 6D; def y = 3.0; x / y", "#2");
    test("double x = 6D; def y = 3.0; x / y", "#2");
    test("def x = 6D; Decimal y = 3.0; x / y", "#2");
    test("Decimal x = 8.0; double y = 5D; x % y", "#3.0");
    test("def x = 8.0; def y = 5D; x % y", "#3.0");
    test("Decimal x = 8.0; def y = 5D; x % y", "#3.0");
    test("def x = 8.0; double y = 5D; x % y", "#3.0");
    test("double x = 8D; Decimal y = 5.0; x % y", "#3.0");
    test("def x = 8D; def y = 5.0; x % y", "#3.0");
    test("double x = 8D; def y = 5.0; x % y", "#3.0");
    test("def x = 8D; Decimal y = 5.0; x % y", "#3.0");
  }

  @Test public void numericConversions() {
    test("int x = 1L", 1);
    test("int x = 1D", 1);
    test("int x = 1.0", 1);
    test("long x = 1", 1L);
    test("long x = 1D", 1L);
    test("long x = 1.0", 1L);
    test("double x = 1", 1D);
    test("double x = 1L", 1D);
    test("double x = 1.0", 1D);
    test("Decimal x = 1", "#1");
    test("Decimal x = 1L", "#1");
    test("Decimal x = 1D", "#1.0");

    test("int x; x = x + 1L", 1);
    test("int x; x = x + 1D", 1);
    test("int x; x = x + 1.0", 1);
    test("long x; x = x + 1", 1L);
    test("long x; x = x + 1D", 1L);
    test("long x; x = x + 1.0", 1L);
    test("double x; x = x + 1", 1D);
    test("double x; x = x + 1L", 1D);
    test("double x; x = x + 1.0", 1D);
    test("Decimal x; x = x + 1", "#1");
    test("Decimal x; x = x + 1L", "#1");
    test("Decimal x; x = x + 1D", "#1.0");

    test("int x; def y = 1L; x = x + y", 1);
    test("int x; def y = 1D; x = x + y", 1);
    test("int x; def y = 1.0; x = x + y", 1);
    test("long x; def y = 1; x = x + y", 1L);
    test("long x; def y = 1D; x = x + y", 1L);
    test("long x; def y = 1.0; x = x + y", 1L);
    test("double x; def y = 1; x = x + y", 1D);
    test("double x; def y = 1L; x = x + y", 1D);
    test("double x; def y = 1.0; x = x + y", 1D);
    test("Decimal x; def y = 1; x = x + y", "#1");
    test("Decimal x; def y = 1L; x = x + y", "#1");
    test("Decimal x; def y = 1D; x = x + y", "#1.0");

    test("int x = 1; x += 2L", 3);
    test("int x = 1; x += 2L; x", 3);
    test("int x = 1; x += 2D", 3);
    test("int x = 1; x += 2D; x", 3);
    test("int x = 1; x += 2.0", 3);
    test("int x = 1; x += 2.0; x", 3);
    test("double x = 1D; x += 2L", 3D);
    test("double x = 1D; x += 2L; x", 3D);
    test("double x = 1D; x += 2; x", 3D);
    test("double x = 1D; x += 2.0", 3D);
    test("double x = 1D; x += 2.0; x", 3D);
    test("long x = 1L; x += 2", 3L);
    test("long x = 1L; x += 2; x", 3L);
    test("long x = 1L; x += 2D", 3L);
    test("long x = 1L; x += 2D; x", 3L);
    test("long x = 1L; x += 2.0", 3L);
    test("long x = 1L; x += 2.0; x", 3L);
    test("Decimal x = 1.0; x += 2", "#3.0");
    test("Decimal x = 1.0; x += 2; x", "#3.0");
    test("Decimal x = 1.0; x += 2D", "#3.0");
    test("Decimal x = 1.0; x += 2D; x", "#3.0");
    test("Decimal x = 1.0; x += 2.0", "#3.0");
    test("Decimal x = 1.0; x += 2.0; x", "#3.0");

    test("int x = 3; x *= 2L", 6);
    test("int x = 3; x *= 2L; x", 6);
    test("int x = 3; x *= 2D", 6);
    test("int x = 3; x *= 2D; x", 6);
    test("int x = 3; x *= 2.0", 6);
    test("int x = 3; x *= 2.0; x", 6);
    test("double x = 3D; x *= 2L", 6D);
    test("double x = 3D; x *= 2L; x", 6D);
    test("double x = 3D; x *= 2; x", 6D);
    test("double x = 3D; x *= 2.0", 6D);
    test("double x = 3D; x *= 2.0; x", 6D);
    test("long x = 3L; x *= 2", 6L);
    test("long x = 3L; x *= 2; x", 6L);
    test("long x = 3L; x *= 2D", 6L);
    test("long x = 3L; x *= 2D; x", 6L);
    test("long x = 3L; x *= 2.0", 6L);
    test("long x = 3L; x *= 2.0; x", 6L);
    test("Decimal x = 3.0; x *= 2", "#6.0");
    test("Decimal x = 3.0; x *= 2; x", "#6.0");
    test("Decimal x = 3.0; x *= 2D", "#6.00");
    test("Decimal x = 3.0; x *= 2D; x", "#6.00");
    test("Decimal x = 3.0; x *= 2.0", "#6.00");
    test("Decimal x = 3.0; x *= 2.0; x", "#6.00");

    test("int x = 'A'[0]", 65);
    testError("int x = 'A' as int", "not a valid int");
    testError("int x = 'A'", "cannot convert initialiser");
    test("int x = (int)'A'", 65);
  }

  @Test public void simpleVariables() {
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

  @Test public void multipleVarDecls() {
    test("int i,j; i + j", 0);
    test("int i = 1,j; i + j", 1);
    test("int i = 1,j=3; i + j", 4);
    test("int i =\n1,\nj =\n3\n; i + j", 4);
  }

  @Test public void variableAssignments() {
    test("int v = 1; v = 2; v", 2);
    test("int v = 1; v = 2", 2);
    test("int v = 1; v = v + 1", 2);
    testError("1 = 2", "invalid lvalue");
    test("def x = 1; int y = x + 1", 2);
    test("int x = 1; int y = 2; x = y = 4", 4);
    test("int x = 1; int y = 2; x = y = 4; x", 4);
    test("int x = 1; int y = 2; x = (x = y = 4) + 5; x", 9);
    test("int x = 1; int y = 2; x = y = 4; y", 4);
    test("int x = 1; int y = 2; def z = 5; x = y = z = 3; y", 3);
    test("int x = 1; int y = 2; def z = 5; 4 + (x = y = z = 3) + y", 10);
    test("int x = 1; int y = 2; def z = 5; 4 + (x = y = z = 3) + y; x", 3);
  }

  @Test public void questionColon() {
    useAsyncDecorator = false;
    test("null ?: 1", 1);
    test("null ? 0 : 1", 1);
    test("1 ?: null", 1);
    test("1 ? 1 : null", 1);
    test("1 ? null : 1", null);
    test("1 ?: 2", 1);
    test("1 ?: 2", 1);
    test("null ?: null ?: 1", 1);
    test("1 + 2 ?: 3 + 4 ?: 5 * 6 ?: 7", 3);
    test("def x; x ?: 2", 2);
    test("def x; x ? 1 : 2", 2);
    testError("true ? 'abc':1", "not compatible");
    test("false ? 'abc':'1'", "1");
    test("def x = 'abc'; true ? x:1", "abc");
    test("def x = 'abc'; true ? 1:x", 1);
    test("def x = 'abc'; false ? x:1", 1);
    testError("def x; x ? 1 : [1,2,3]", "are not compatible");
    testError("def x; x ? { 1 } : 2", "not compatible");
    test("def x; var y = true ? x : 1", null);
    test("def x; var y = true ? 1 : x", 1);
    test("true ? 1 : 2L", 1L);
    test("false ? 1 : 2L", 2L);
    test("true ? true ? 1 : 2 : 3", 1);
    test("true ? true ? 1 + 2 : 4 : 5", 3);
    test("true ? false ? 1 + 2 : true ? 4 : 5 : 6", 4);
    test("Map x = [a:1]; true ? false ? 1 + 2 + x.a : true ? 5 + x.a : 7 : 8", 6);
    test("true ? null ?: 4 : 5", 4);
    test("true ? null ?: false ? 4 : 5 : 6", 5);
    test("def x = 1; true ? 1 : 2L", 1L);
    test("def x = 1; x ?: 2L", 1);
    test("Map m = true ? null : [:]", null);
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
    test("def x = [a:2]; x.a += (x.a += 3)", 7);
    test("Map x = [a:2]; x.a += (x.a += 3)", 7);
    test("def x = [:]; x.a += (x.a += 3)", 3);
    test("Map x; x.a += (x.a += 3)", 3);
    test("def x = [:]; x.a += 'xxx'", "xxx");
    test("Map x; x.a += 'xxx'", "xxx");
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
    testError("def x; x *= 2", "null operand for left-hand side");
    test("def x = [:]; x.a *= 2", 0);
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
    testError("int x = 1; x /= 0", "divide by zero");
    testError("long x = 1; x /= 0", "divide by zero");
    test("double x = 1; x /= 0", Double.POSITIVE_INFINITY);
    testError("Decimal x = 1; x /= 0", "divide by zero");
  }

  @Test public void bitOperations() {
    test("1 & 1", 1);
    test("1 & 0", 0);
    test("0 & -1", 0);
    test("-1 & 1L", 1L);
    test("5L & -1", 5L);
    test("5 & 4", 4);
    test("12345678901234L & 1234567", 12345678901234L & 1234567);
    test("1 | 0", 1);
    test("1 | 0L", 1L);
    test("12345678901234L | 1234567", 12345678901234L | 1234567);
    test("1 ^ 0", 1);
    test("5 ^ 7", 2);
    test("1L ^ 0", 1L);
    test("5 ^ 7L", 2L);
    test("12345678901234L ^ 1234567", 12345678901234L ^ 1234567);
    testError("1.0 & 1", "must be int or long");
    testError("1 & 1.0", "must be int or long");
    testError("1D & 1", "must be int or long");
    testError("1 & 1D", "must be int or long");
    testError("1.0 | 1", "must be int or long");
    testError("1 | 1.0", "must be int or long");
    testError("1D | 1", "must be int or long");
    testError("1 | 1D", "must be int or long");
    testError("1.0 ^ 1", "must be int or long");
    testError("1 ^ 1.0", "must be int or long");
    testError("1D ^ 1", "must be int or long");
    testError("1 ^ 1D", "must be int or long");
    testError("1 ^ 'abc'", "non-numeric operand");
    test("~1", ~1);
    test("~1L", ~1L);
    test("~0", ~0);
    test("~0L", ~0L);
    test("~12345", ~12345);
    test("~123456789012345L", ~123456789012345L);
    testError("~'abc'", "must be int or long");
    testError("~1.0", "must be int or long");
    testError("~1.0D", "must be int or long");
    test("1 << 2", 1 << 2);
    test("1 << 2L", 1 << 2L);
    test("1L << 40", 1L << 40);
    test("1 << ((1L << 32) + 1)", 1 << ((1L << 32) + 1));
    test("0 << 7", 0);
    test("0L << 7", 0L);
    test("2 << 40", 2 << 40);
    test("2L << 40", 2L << 40);
    test("1234567 >> 5", 1234567 >> 5);
    test("1234567 >> 5L", 1234567 >> 5L);
    test("123456789012345L >> 5", 123456789012345L >> 5);
    test("123456789012345L >> 5L", 123456789012345L >> 5L);
    test("-1 >> 20", -1);
    test("-1L >> 20", -1L);
    test("-1 >>> 20", -1 >>> 20);
    test("-1L >>> 20", -1L >>> 20);

    test("def x = 1; def y = 1; x & y", 1);
    test("def x = 1; def y = 0; x & y", 0);
    test("def x = 0; def y = -1; x & y", 0);
    test("def x = -1; def y = 1L; x & y", 1L);
    test("def x = 5L; def y = -1; x & y", 5L);
    test("def x = 5; def y = 4; x & y", 4);
    test("def x = 12345678901234L; def y = 1234567; x & y", 12345678901234L & 1234567);
    test("def x = 1; def y = 0; x | y", 1);
    test("def x = 1; def y = 0L; x | y", 1L);
    test("def x = 12345678901234L; def y = 1234567; x | y", 12345678901234L | 1234567);
    test("def x = 1; def y = 0; x ^ y", 1);
    test("def x = 5; def y = 7; x ^ y", 2);
    test("def x = 1L; def y = 0; x ^ y", 1L);
    test("def x = 5; def y = 7L; x ^ y", 2L);
    test("def x = 12345678901234L; def y = 1234567; x ^ y", 12345678901234L ^ 1234567);
    testError("def x = 1.0; def y = 1; x & y", "must be int or long");
    testError("def x = 1; def y = 1.0; x & y", "must be int or long");
    testError("def x = 1D; def y = 1; x & y", "must be int or long");
    testError("def x = 1; def y = 1D; x & y", "must be int or long");
    testError("def x = 1.0; def y = 1; x | y", "must be int or long");
    testError("def x = 1; def y = 1.0; x | y", "must be int or long");
    testError("def x = 1D; def y = 1; x | y", "must be int or long");
    testError("def x = 1; def y = 1D; x | y", "must be int or long");
    testError("def x = 1.0; def y = 1; x ^ y", "must be int or long");
    testError("def x = 1; def y = 1.0; x ^ y", "must be int or long");
    testError("def x = 1D; def y = 1; x ^ y", "must be int or long");
    testError("def x = 1; def y = 1D; x ^ y", "must be int or long");
    testError("def x = 1; def y = 'abc'; x ^ y", "must be int or long");
    test("def x = 1; ~x", ~1);
    test("def x = 1L; ~x", ~1L);
    test("def x = 0; ~x", ~0);
    test("def x = 0L; ~x", ~0L);
    test("def x = 12345; ~x", ~12345);
    test("def x = 123456789012345L; ~x", ~123456789012345L);
    testError("def x = 'abc'; ~x", "must be int or long");
    testError("def x = 1.0; ~x", "must be int or long");
    testError("def x = 1.0D; ~x", "must be int or long");
    test("def x = 1; def y = 2; x << y", 1 << 2);
    test("def x = 1; def y = 2L; x << y", 1 << 2L);
    test("def x = 1L; def y = 40; x << y", 1L << 40);
    test("def x = 1; def y = ((1L << 32) + 1); x << y", 1 << ((1L << 32) + 1));
    test("def x = 0; def y = 7; x << y", 0);
    test("def x = 0L; def y = 7; x << y", 0L);
    test("def x = 2; def y = 40; x << y", 2 << 40);
    test("def x = 2L; def y = 40; x << y", 2L << 40);
    test("def x = 1234567; def y = 5; x >> y", 1234567 >> 5);
    test("def x = 1234567; def y = 5L; x >> y", 1234567 >> 5L);
    test("def x = 123456789012345L; def y = 5; x >> y", 123456789012345L >> 5);
    test("def x = 123456789012345L; def y = 5L; x >> y", 123456789012345L >> 5L);
    test("def x = -1; def y = 20; x >> y", -1);
    test("def x = -1L; def y = 20; x >> y", -1L);
    test("def x = -1; def y = 20; x >>> y", -1 >>> 20);
    test("def x = -1L; def y = 20; x >>> y", -1L >>> 20);

    test("def x = 1; def y = 1; x &= y; x", 1);
    test("def x = 1; def y = 0; x &= y; x", 0);
    test("def x = 0; def y = -1; x &= y; x", 0);
    test("def x = -1; def y = 1L; x &= y; x", 1L);
    test("def x = 5L; def y = -1; x &= y; x", 5L);
    test("def x = 5; def y = 4; x &= y; x", 4);
    test("def x = 12345678901234L; def y = 1234567; x &= y; x", 12345678901234L & 1234567);
    test("def x = 1; def y = 0; x |= y; x", 1);
    test("def x = 1; def y = 0L; x |= y; x", 1L);
    test("def x = 12345678901234L; def y = 1234567; x |= y; x", 12345678901234L | 1234567);
    test("def x = 1; def y = 0; x ^= y; x", 1);
    test("def x = 5; def y = 7; x ^= y; x", 2);
    test("def x = 1L; def y = 0; x ^= y; x", 1L);
    test("def x = 5; def y = 7L; x ^= y; x", 2L);
    test("def x = 12345678901234L; def y = 1234567; x ^= y; x", 12345678901234L ^ 1234567);
    testError("def x = 1.0; def y = 1; x &= y; x", "must be int or long");
    testError("def x = 1; def y = 1.0; x &= y; x", "must be int or long");
    testError("def x = 1D; def y = 1; x &= y; x", "must be int or long");
    testError("def x = 1; def y = 1D; x &= y; x", "must be int or long");
    testError("def x = 1.0; def y = 1; x |= y; x", "must be int or long");
    testError("def x = 1; def y = 1.0; x |= y; x", "must be int or long");
    testError("def x = 1D; def y = 1; x |= y; x", "must be int or long");
    testError("def x = 1; def y = 1D; x |= y; x", "must be int or long");
    testError("def x = 1.0; def y = 1; x ^= y; x", "must be int or long");
    testError("def x = 1; def y = 1.0; x ^= y; x", "must be int or long");
    testError("def x = 1D; def y = 1; x ^= y; x", "must be int or long");
    testError("def x = 1; def y = 1D; x ^= y; x", "must be int or long");
    testError("def x = 1; def y = 'abc'; x ^= y; x", "must be int or long");
    test("def x = 1; def y = 2; x <<= y", 1 << 2);
    test("def x = 1; def y = 2L; x <<= y", 1 << 2L);
    test("def x = 1L; def y = 40; x <<= y", 1L << 40);
    test("def x = 1; def y = ((1L << 32) + 1); x <<= y", 1 << ((1L << 32) + 1));
    test("def x = 0; def y = 7; x <<= y", 0);
    test("def x = 0L; def y = 7; x <<= y", 0L);
    test("def x = 2; def y = 40; x <<= y", 2 << 40);
    test("def x = 2L; def y = 40; x <<= y", 2L << 40);
    test("def x = 1234567; def y = 5; x >>= y", 1234567 >> 5);
    test("def x = 1234567; def y = 5L; x >>= y", 1234567 >> 5L);
    test("def x = 123456789012345L; def y = 5; x >>= y", 123456789012345L >> 5);
    test("def x = 123456789012345L; def y = 5L; x >>= y", 123456789012345L >> 5L);
    test("def x = -1; def y = 20; x >>= y", -1);
    test("def x = -1L; def y = 20; x >>= y", -1L);
    test("def x = -1; def y = 20; x >>>= y", -1 >>> 20);
    test("def x = -1L; def y = 20; x >>>= y", -1L >>> 20);

    test("var x = 1; def y = 1; x &= y; x", 1);
    test("var x = 1; def y = 0; x &= y; x", 0);
    test("var x = -1; def y = 0; x &= y; x", 0);
    test("var x = -1; def y = 1L; x &= y; x", 1);
    test("var x = -1L; def y = 5; x &= y; x", 5L);
    test("var x = 5; def y = 4; x &= y; x", 4);
    test("var x = 12345678901234L; def y = 1234567; x &= y; x", 12345678901234L & 1234567);
    test("var x = 1; def y = 0; x |= y; x", 1);
    test("var x = 1; def y = 0L; x |= y; x", 1);
    test("var x = 12345678901234L; def y = 1234567; x |= y; x", 12345678901234L | 1234567);
    test("var x = 1; def y = 0; x ^= y; x", 1);
    test("var x = 5; def y = 7; x ^= y; x", 2);
    test("var x = 1L; def y = 0; x ^= y; x", 1L);
    test("var x = 5L; def y = 7; x ^= y; x", 2L);
    test("var x = 12345678901234L; def y = 1234567; x ^= y; x", 12345678901234L ^ 1234567);
    testError("var x = 1.0; def y = 1; x &= y; x", "must be int or long");
    testError("var x = 1; def y = 1.0; x &= y; x", "must be int or long");
    testError("var x = 1D; def y = 1; x &= y; x", "must be int or long");
    testError("var x = 1; def y = 1D; x &= y; x", "must be int or long");
    testError("var x = 1.0; def y = 1; x |= y; x", "must be int or long");
    testError("var x = 1; def y = 1.0; x |= y; x", "must be int or long");
    testError("var x = 1D; def y = 1; x |= y; x", "must be int or long");
    testError("var x = 1; def y = 1D; x |= y; x", "must be int or long");
    testError("var x = 1.0; def y = 1; x ^= y; x", "must be int or long");
    testError("var x = 1; def y = 1.0; x ^= y; x", "must be int or long");
    testError("var x = 1D; def y = 1; x ^= y; x", "must be int or long");
    testError("var x = 1; def y = 1D; x ^= y; x", "must be int or long");
    testError("var x = 1; def y = 'abc'; x ^= y; x", "must be int or long");
    test("var x = 1; def y = 2; x <<= y", 1 << 2);
    test("var x = 1; def y = 2L; x <<= y", 1 << 2L);
    test("var x = 1L; def y = 40; x <<= y", 1L << 40);
    test("var x = 1; def y = ((1L << 32) + 1); x <<= y", 1 << ((1L << 32) + 1));
    test("var x = 0; def y = 7; x <<= y", 0);
    test("var x = 0L; def y = 7; x <<= y", 0L);
    test("var x = 2; def y = 40; x <<= y", 2 << 40);
    test("var x = 2L; def y = 40; x <<= y", 2L << 40);
    test("var x = 1234567; def y = 5; x >>= y", 1234567 >> 5);
    test("var x = 1234567; def y = 5L; x >>= y", 1234567 >> 5L);
    test("var x = 123456789012345L; def y = 5; x >>= y", 123456789012345L >> 5);
    test("var x = 123456789012345L; def y = 5L; x >>= y", 123456789012345L >> 5L);
    test("var x = -1; def y = 20; x >>= y", -1);
    test("var x = -1L; def y = 20; x >>= y", -1L);
    test("var x = -1; def y = 20; x >>>= y", -1 >>> 20);
    test("var x = -1L; def y = 20; x >>>= y", -1L >>> 20);

    test("def x = 1; var y = 1; x &= y; x", 1);
    test("def x = 1; var y = 0; x &= y; x", 0);
    test("def x = -1; var y = 0; x &= y; x", 0);
    test("def x = -1; var y = 1L; x &= y; x", 1L);
    test("def x = -1; var y = 5L; x &= y; x", 5L);
    test("def x = 5; var y = 4; x &= y; x", 4);
    test("def x = 12345678901234L; var y = 1234567; x &= y; x", 12345678901234L & 1234567);
    test("def x = 1; var y = 0; x |= y; x", 1);
    test("def x = 1; var y = 0L; x |= y; x", 1L);
    test("def x = 12345678901234L; var y = 1234567; x |= y; x", 12345678901234L | 1234567);
    test("def x = 1; var y = 0; x ^= y; x", 1);
    test("def x = 5; var y = 7; x ^= y; x", 2);
    test("def x = 1L; var y = 0; x ^= y; x", 1L);
    test("def x = 5; var y = 7L; x ^= y; x", 2L);
    test("def x = 12345678901234L; var y = 1234567; x ^= y; x", 12345678901234L ^ 1234567);
    testError("def x = 1.0; var y = 1; x &= y; x", "must be int or long");
    testError("def x = 1; var y = 1.0; x &= y; x", "must be int or long");
    testError("def x = 1D; var y = 1; x &= y; x", "must be int or long");
    testError("def x = 1; var y = 1D; x &= y; x", "must be int or long");
    testError("def x = 1.0; var y = 1; x |= y; x", "must be int or long");
    testError("def x = 1; var y = 1.0; x |= y; x", "must be int or long");
    testError("def x = 1D; var y = 1; x |= y; x", "must be int or long");
    testError("def x = 1; var y = 1D; x |= y; x", "must be int or long");
    testError("def x = 1.0; var y = 1; x ^= y; x", "must be int or long");
    testError("def x = 1; var y = 1.0; x ^= y; x", "must be int or long");
    testError("def x = 1D; var y = 1; x ^= y; x", "must be int or long");
    testError("def x = 1; var y = 1D; x ^= y; x", "must be int or long");
    testError("def x = 1; var y = 'abc'; x ^= y; x", "non-numeric operand");
    test("def x = 1; var y = 2; x <<= y", 1 << 2);
    test("def x = 1; var y = 2L; x <<= y", 1 << 2L);
    test("def x = 1L; var y = 40; x <<= y", 1L << 40);
    test("def x = 1; var y = ((1L << 32) + 1); x <<= y", 1 << ((1L << 32) + 1));
    test("def x = 0; var y = 7; x <<= y", 0);
    test("def x = 0L; var y = 7; x <<= y", 0L);
    test("def x = 2; var y = 40; x <<= y", 2 << 40);
    test("def x = 2L; var y = 40; x <<= y", 2L << 40);
    test("def x = 1234567; var y = 5; x >>= y", 1234567 >> 5);
    test("def x = 1234567; var y = 5L; x >>= y", 1234567 >> 5L);
    test("def x = 123456789012345L; var y = 5; x >>= y", 123456789012345L >> 5);
    test("def x = 123456789012345L; var y = 5L; x >>= y", 123456789012345L >> 5L);
    test("def x = -1; var y = 20; x >>= y", -1);
    test("def x = -1L; var y = 20; x >>= y", -1L);
    test("def x = -1; var y = 20; x >>>= y", -1 >>> 20);
    test("def x = -1L; var y = 20; x >>>= y", -1L >>> 20);
  }

  @Test public void percentEquals(){
    test("int x = 18; x %= 7L", 4);
    test("int x = 18; x %= 7; x", 4);
    test("int x = 18; (x %= 7) + (x %= 3)", 5);
    test("int x = 18; (x %= 7) + (x %= 3); x", 1);
    test("int x = 18; x %= 7; x", 4);
    test("def x = 18; x %= 7L", 4L);
    test("def x = 18; x %= 7; x", 4);
    test("var x = 18; x %= 7L", 4);
    test("var x = 18; x %= 7; x", 4);
    test("long x = 18; x %= 7L", 4L);
    test("long x = 18; x %= 7; x", 4L);
    test("double x = 18; x %= 7L", 4.0D);
    test("double x = 18; x %= 7; x", 4.0D);
    test("Decimal x = 18.0; x %= 7L", "#4.0");
    test("Decimal x = 18.0; x %= 7; x", "#4.0");
    test("var x = 18.0; x %= 7L", "#4.0");
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
    testError("def v = 1; v + true", "non-numeric operand for right-hand side");
    testError("def v = false; v + 1", "non-numeric operand for left-hand side");
    testError("def v = true; 1 + v", "non-numeric operand for right-hand side");
    testError("def v = 1; false + 1", "non-numeric operand for left-hand side");
    testError("def x = 1; def y = true; x + y", "non-numeric operand for right-hand side");
    testError("def x = 1; x - 'abc'", "non-numeric operand for right-hand side");
    testError("def x = 'abc'; 1 - x", "non-numeric operand for right-hand side");
    testError("def x = 'abc'; x - 1", "non-numeric operand for left-hand side");
    testError("def x = 'abc'; def y = 1; x - y", "non-numeric operand for left-hand side");
    testError("def x = 1; 'abc' - x", "non-numeric operand for left-hand side");
    testError("def x = false; x + true", "non-numeric operand for left-hand side");
    testError("def x = true; false + x", "non-numeric operand for left-hand side");
    testError("def x = false; def y = true; x + y", "non-numeric operand for left-hand side");
    testError("def x = 1; x + null", "null operand for right-hand side");
    testError("def x = null; 1 + x", "null operand for right-hand side");
    testError("def x = 1; def y =null; x + y", "null operand for right-hand side");
    testError("def x = null; x + 1", "null operand for left-hand side");
    testError("def x = 1; null + x", "null operand for left-hand side");
    testError("def x = null; def y = 1; x + y", "null operand for left-hand side");

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

    testError("def x = 1; x / 0", "divide by zero");
    testError("def x = 1.0; x / 0", "divide by zero");
    testError("def x = 1L; x / 0", "divide by zero");
    test("def x = 1.0D; x / 0", Double.POSITIVE_INFINITY);
  }

  @Test public void booleanValues() {
    test("boolean TRUE=true; boolean FALSE=false; !TRUE", false);
    test("boolean TRUE=true; boolean FALSE=false; !FALSE", true);
    test("boolean TRUE=true; boolean FALSE=false; !!TRUE", true);
    test("boolean TRUE=true; boolean FALSE=false; !!FALSE", false);
    test("boolean TRUE=true; boolean FALSE=false; !(!!TRUE)", false);
    test("boolean TRUE=true; boolean FALSE=false; !!(!FALSE)", true);
    test("boolean TRUE=true; boolean FALSE=false; TRUE && TRUE", true);
    test("boolean TRUE=true; boolean FALSE=false; FALSE && TRUE", false);
    test("boolean TRUE=true; boolean FALSE=false; TRUE && FALSE", false);
    test("boolean TRUE=true; boolean FALSE=false; FALSE && FALSE", false);
    test("boolean TRUE=true; boolean FALSE=false; TRUE || TRUE", true);
    test("boolean TRUE=true; boolean FALSE=false; FALSE || TRUE", true);
    test("boolean TRUE=true; boolean FALSE=false; TRUE || FALSE", true);
    test("boolean TRUE=true; boolean FALSE=false; FALSE || FALSE", false);
    test("boolean TRUE=true; boolean FALSE=false; !FALSE || FALSE", true);
    test("boolean TRUE=true; boolean FALSE=false; FALSE || TRUE && FALSE", false);
    test("boolean TRUE=true; boolean FALSE=false; TRUE  || TRUE && FALSE", true);
    test("boolean TRUE=true; boolean FALSE=false; TRUE  || TRUE && TRUE", true);
    test("boolean TRUE=true; boolean FALSE=false; FALSE || !FALSE && TRUE", true);

    test("var TRUE=true; var FALSE=false; !TRUE", false);
    test("var TRUE=true; var FALSE=false; !FALSE", true);
    test("var TRUE=true; var FALSE=false; !!TRUE", true);
    test("var TRUE=true; var FALSE=false; !!FALSE", false);
    test("var TRUE=true; var FALSE=false; !(!!TRUE)", false);
    test("var TRUE=true; var FALSE=false; !!(!FALSE)", true);
    test("var TRUE=true; var FALSE=false; TRUE && TRUE", true);
    test("var TRUE=true; var FALSE=false; FALSE && TRUE", false);
    test("var TRUE=true; var FALSE=false; TRUE && FALSE", false);
    test("var TRUE=true; var FALSE=false; FALSE && FALSE", false);
    test("var TRUE=true; var FALSE=false; TRUE || TRUE", true);
    test("var TRUE=true; var FALSE=false; FALSE || TRUE", true);
    test("var TRUE=true; var FALSE=false; TRUE || FALSE", true);
    test("var TRUE=true; var FALSE=false; FALSE || FALSE", false);
    test("var TRUE=true; var FALSE=false; !FALSE || FALSE", true);
    test("var TRUE=true; var FALSE=false; FALSE || TRUE && FALSE", false);
    test("var TRUE=true; var FALSE=false; TRUE  || TRUE && FALSE", true);
    test("var TRUE=true; var FALSE=false; TRUE  || TRUE && TRUE", true);
    test("var TRUE=true; var FALSE=false; FALSE || !FALSE && TRUE", true);

    test("def TRUE=true; def FALSE=false; !TRUE", false);
    test("def TRUE=true; def FALSE=false; !FALSE", true);
    test("def TRUE=true; def FALSE=false; !!TRUE", true);
    test("def TRUE=true; def FALSE=false; !!FALSE", false);
    test("def TRUE=true; def FALSE=false; !(!!TRUE)", false);
    test("def TRUE=true; def FALSE=false; !!(!FALSE)", true);
    test("def TRUE=true; def FALSE=false; TRUE && TRUE", true);
    test("def TRUE=true; def FALSE=false; FALSE && TRUE", false);
    test("def TRUE=true; def FALSE=false; TRUE && FALSE", false);
    test("def TRUE=true; def FALSE=false; FALSE && FALSE", false);
    test("def TRUE=true; def FALSE=false; TRUE || TRUE", true);
    test("def TRUE=true; def FALSE=false; FALSE || TRUE", true);
    test("def TRUE=true; def FALSE=false; TRUE || FALSE", true);
    test("def TRUE=true; def FALSE=false; FALSE || FALSE", false);
    test("def TRUE=true; def FALSE=false; !FALSE || FALSE", true);
    test("def TRUE=true; def FALSE=false; FALSE || TRUE && FALSE", false);
    test("def TRUE=true; def FALSE=false; TRUE  || TRUE && FALSE", true);
    test("def TRUE=true; def FALSE=false; TRUE  || TRUE && TRUE", true);
    test("def TRUE=true; def FALSE=false; FALSE || !FALSE && TRUE", true);

    test("def TRUE=true; def FALSE=null; !FALSE", true);
    test("def TRUE=true; def FALSE=null; !!FALSE", false);
    test("def TRUE=true; def FALSE=null; !!(!FALSE)", true);
    test("def TRUE=true; def FALSE=null; FALSE && TRUE", false);
    test("def TRUE=true; def FALSE=null; TRUE && FALSE", false);
    test("def TRUE=true; def FALSE=null; FALSE && FALSE", false);
    test("def TRUE=true; def FALSE=null; FALSE || TRUE", true);
    test("def TRUE=true; def FALSE=null; TRUE || FALSE", true);
    test("def TRUE=true; def FALSE=null; FALSE || FALSE", false);
    test("def TRUE=true; def FALSE=null; !FALSE || FALSE", true);
    test("def TRUE=true; def FALSE=null; FALSE || TRUE && FALSE", false);
    test("def TRUE=true; def FALSE=null; TRUE  || TRUE && FALSE", true);
    test("def TRUE=true; def FALSE=null; TRUE  || TRUE && TRUE", true);
    test("def TRUE=true; def FALSE=null; FALSE || !FALSE && TRUE", true);

    test("int TRUE=7; def FALSE=null; !FALSE", true);
    test("int TRUE=7; def FALSE=null; !!FALSE", false);
    test("int TRUE=7; def FALSE=null; !!(!FALSE)", true);
    test("int TRUE=7; def FALSE=null; FALSE && TRUE", false);
    test("int TRUE=7; def FALSE=null; TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=null; FALSE && FALSE", false);
    test("int TRUE=7; def FALSE=null; FALSE || TRUE", true);
    test("int TRUE=7; def FALSE=null; TRUE || FALSE", true);
    test("int TRUE=7; def FALSE=null; FALSE || FALSE", false);
    test("int TRUE=7; def FALSE=null; !FALSE || FALSE", true);
    test("int TRUE=7; def FALSE=null; FALSE || TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=null; TRUE  || TRUE && FALSE", true);
    test("int TRUE=7; def FALSE=null; TRUE  || TRUE && TRUE", true);
    test("int TRUE=7; def FALSE=null; FALSE || !FALSE && TRUE", true);

    test("int TRUE=7; def FALSE=''; !FALSE", true);
    test("int TRUE=7; def FALSE=''; !!FALSE", false);
    test("int TRUE=7; def FALSE=''; !!(!FALSE)", true);
    test("int TRUE=7; def FALSE=''; FALSE && TRUE", false);
    test("int TRUE=7; def FALSE=''; TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=''; FALSE && FALSE", false);
    test("int TRUE=7; def FALSE=''; FALSE || TRUE", true);
    test("int TRUE=7; def FALSE=''; TRUE || FALSE", true);
    test("int TRUE=7; def FALSE=''; FALSE || FALSE", false);
    test("int TRUE=7; def FALSE=''; !FALSE || FALSE", true);
    test("int TRUE=7; def FALSE=''; FALSE || TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=''; TRUE  || TRUE && FALSE", true);
    test("int TRUE=7; def FALSE=''; TRUE  || TRUE && TRUE", true);
    test("int TRUE=7; def FALSE=''; FALSE || !FALSE && TRUE", true);

    test("int TRUE=7; def FALSE=0; !FALSE", true);
    test("int TRUE=7; def FALSE=0; !!FALSE", false);
    test("int TRUE=7; def FALSE=0; !!(!FALSE)", true);
    test("int TRUE=7; def FALSE=0; FALSE && TRUE", false);
    test("int TRUE=7; def FALSE=0; TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=0; FALSE && FALSE", false);
    test("int TRUE=7; def FALSE=0; FALSE || TRUE", true);
    test("int TRUE=7; def FALSE=0; TRUE || FALSE", true);
    test("int TRUE=7; def FALSE=0; FALSE || FALSE", false);
    test("int TRUE=7; def FALSE=0; !FALSE || FALSE", true);
    test("int TRUE=7; def FALSE=0; FALSE || TRUE && FALSE", false);
    test("int TRUE=7; def FALSE=0; TRUE  || TRUE && FALSE", true);
    test("int TRUE=7; def FALSE=0; TRUE  || TRUE && TRUE", true);
    test("int TRUE=7; def FALSE=0; FALSE || !FALSE && TRUE", true);

    test("def TRUE=7; double FALSE=0; !FALSE", true);
    test("def TRUE=7; double FALSE=0; !!FALSE", false);
    test("def TRUE=7; double FALSE=0; !!(!FALSE)", true);
    test("def TRUE=7; double FALSE=0; FALSE && TRUE", false);
    test("def TRUE=7; double FALSE=0; TRUE && FALSE", false);
    test("def TRUE=7; double FALSE=0; FALSE && FALSE", false);
    test("def TRUE=7; double FALSE=0; FALSE || TRUE", true);
    test("def TRUE=7; double FALSE=0; TRUE || FALSE", true);
    test("def TRUE=7; double FALSE=0; FALSE || FALSE", false);
    test("def TRUE=7; double FALSE=0; !FALSE || FALSE", true);
    test("def TRUE=7; double FALSE=0; FALSE || TRUE && FALSE", false);
    test("def TRUE=7; double FALSE=0; TRUE  || TRUE && FALSE", true);
    test("def TRUE=7; double FALSE=0; TRUE  || TRUE && TRUE", true);
    test("def TRUE=7; double FALSE=0; FALSE || !FALSE && TRUE", true);

    test("double TRUE=7; Decimal FALSE=0; !FALSE", true);
    test("double TRUE=7; Decimal FALSE=0; !!FALSE", false);
    test("double TRUE=7; Decimal FALSE=0; !!(!FALSE)", true);
    test("double TRUE=7; Decimal FALSE=0; FALSE && TRUE", false);
    test("double TRUE=7; Decimal FALSE=0; TRUE && FALSE", false);
    test("double TRUE=7; Decimal FALSE=0; FALSE && FALSE", false);
    test("double TRUE=7; Decimal FALSE=0; FALSE || TRUE", true);
    test("double TRUE=7; Decimal FALSE=0; TRUE || FALSE", true);
    test("double TRUE=7; Decimal FALSE=0; FALSE || FALSE", false);
    test("double TRUE=7; Decimal FALSE=0; !FALSE || FALSE", true);
    test("double TRUE=7; Decimal FALSE=0; FALSE || TRUE && FALSE", false);
    test("double TRUE=7; Decimal FALSE=0; TRUE  || TRUE && FALSE", true);
    test("double TRUE=7; Decimal FALSE=0; TRUE  || TRUE && TRUE", true);
    test("double TRUE=7; Decimal FALSE=0; FALSE || !FALSE && TRUE", true);

    test("Decimal TRUE=7; Decimal FALSE=0; !FALSE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; !!FALSE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; !!(!FALSE)", true);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE && TRUE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; TRUE && FALSE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE && FALSE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE || TRUE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; TRUE || FALSE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE || FALSE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; !FALSE || FALSE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE || TRUE && FALSE", false);
    test("Decimal TRUE=7; Decimal FALSE=0; TRUE  || TRUE && FALSE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; TRUE  || TRUE && TRUE", true);
    test("Decimal TRUE=7; Decimal FALSE=0; FALSE || !FALSE && TRUE", true);

    testError("def x; x.a && true", "null value");
    test("def x; true || x.a", true);
    test("def x; false && x.a", false);
  }

  @Test
  public void explicitReturnFromScript() {
    test("double v = 2; return v", 2D);
    test("int v = 2; { v = v + 1; return v }; v = v + 3", 3);
    test("String v = '2'; { v = v + 1; { return v }}", "21");
    test("var v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("Decimal v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("def v = '2'; { v = v + 1; { return v }}", "21");
    testError("double v = 2; return v; v = v + 1", "unreachable statement");
    testError("double v = 2; return v; { v = v + 1 }", "unreachable statement");
    test("return true and false", true);
    test("false or return true and false", true);
    test("false and return true and false", false);
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
    test("13 + 12 % 7L", 18L);
    test("13 + 12 % 7 - 3", 15);
    test("13 + 12 % ++6 - 3", 15);
  }

  @Test public void constStringConcatenation() {
    testError("'abc' - '123'", "non-numeric operand");
    test("'abc' + '123'", "abc123");
    test("'abc' + null", "abcnull");
    testError("null + 'abc'", "null operand");
    test("'abc' + 'def' + 'ghi'", "abcdefghi");
    test("'abc' + ('1' + '2' + '3') + 'def'", "abc123def");
    test("'' + 'abc'", "abc");
    test("'abc' + ''", "abc");
  }

  @Test
  public void constStringRepeat() {
    test("'abc' * 2", "abcabc");
    test("'abc' * 0", "");
    testError("'abc' * -1", "string repeat count must be >= 0");
  }

  @Test public void defaultValues() {
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
    testError("var x", "Initialiser expression required");
  }

  @Test void prefixResultUsage() {
    test("int x = 1; ~x; 3", 3);
    test("def x = 1; ~x; 3", 3);
    test("boolean x = true; !x; 3", 3);
    test("def x = true; !x; 3", 3);
    test("def x = 1; !x; 3", 3);
    test("int x = 1; -x; 3", 3);
    test("def x = 1; -x; 3", 3);
  }

  @Test
  public void prefixIncOrDec() {
    testError("++null", "null value encountered");
    testError("--null", "null value encountered");
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
    testError("def x = 1; (x + x)++ ++", "expecting end of statement");

    testError("def x = 'a'; ++x", "non-numeric operand");
    testError("def x = [a:'a']; ++x.a", "non-numeric operand");
  }

  @Test public void postfixIncOrDec() {
    testError("null++", "null value encountered");
    testError("null--", "null value encountered");
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

    testError("def x = 'a'; x++", "non-numeric operand");
    testError("def x = [a:'a']; x.a++", "non-numeric operand");
    testError("def x = 'a'; x--", "non-numeric operand");
    testError("def x = [a:'a']; x.a--", "non-numeric operand");
    testError("def x = 'a'; ++x", "non-numeric operand");
    testError("def x = [a:'a']; ++x.a", "non-numeric operand");
    testError("def x = 'a'; --x", "non-numeric operand");
    testError("def x = [a:'a']; --x.a", "non-numeric operand");
  }

  @Test public void varScoping() {
    testError("def x = x + 1", "variable initialisation cannot refer to itself");
    test("def x = 2; if (true) { def x = 4; x++ }; x", 2);
    test("def x = 2; if (true) { def x = 4; x++; { def x = 17; x = x + 5 } }; x", 2);
    testError("def x = 2; { def x = 3; def x = 4; }", "clashes with previously declared variable");
    testError("int f() { def x = 3; def x = 4; }", "clashes with previously declared variable");
    testError("int f(x) { def x = 3; }", "clashes with previously declared variable");
  }

  @Test public void exprStrings() {
    test("def x = 1; \"$x\"", "1");
    test("def x = 1; \"\\$x\"", "$x");
    test("def x = 1; \"$x\\\"\"", "1\"");
    test("\"${1}\"", "1");
    test("def x = 1; \"${x}\"", "1");
    test("\"${}\"", "null");
    test("\"${\"${1}\"}\"", "1");
    test("\"'a'\"", "'a'");
    test("\"'a'\\n\"", "'a'\n");
    test("\"${1 + 2}\"", "3");
    test("\"x${1 + 2}y\"", "x3y");
    test("\"x${\"1\" + 2}y\"", "x12y");
    test("\"x${\"${2*4}\" + 2}y\"", "x82y");
    test("boolean x; \"$x\"*2", "falsefalse");
    test("boolean x; \"$x${\"${2*4}\" + 2}y\"", "false82y");
    test("boolean x; boolean y = true; \"$x${\"${\"$x\"*4}\" + 2}$y\"", "falsefalsefalsefalsefalse2true");
    test("def x = 3;\"x = ${x}\"", "x = 3");
    test("def x = 3;\"x = $x\"", "x = 3");
    test("\"\"\"x${1 + 2}y\n${3.0*3}\"\"\"", "x3y\n9.0");
    test("def x = 3; \"x=$x=${\"\"\"${1+2}\"\"\"}\"", "x=3=3");

    testError("def x = 3; \"\"\"${1+2}$\"\"\" + 'abc'", "unexpected character '\"'");
    testError("def x = 3; \"$x=${\"\"\"${1+2}${\"\"\"}\"", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${\"\"\" + 'abc'", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${x\"\"\" + 'abc'", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${x\"\"\"} + 'abc'", "unexpected end of file");
    testError("def x = 3; \"$x=${\"\"\"${1+2}${\"\"\"}\"", "unexpected end of file");
    testError("def x = 3; \"$x=${\"\"\"${1+2}$\"\"\"}\"", "expecting start of identifier");
    testError("def x = 3; \"\"\"${1+2}$\"\"\" + 'abc'", "expecting start of identifier");

    testError("def x = 3; \"$x=${\"\"\"${1\n+2}\"\"\"}\"", "new line not allowed");
    testError("def x = 3; \"$x=${\"\"\"\n${1\n+2}\n\"\"\"}\"", "new line not allowed");

    test("def x = 1; /$x/", "1");
    test("def x = 1; def y = /$x/; y", "1");
    test("def x = 1; def y = /\\$x/; y", "$x");
    test("def x = 1; def y = /$x\\//; y", "1/");
    test("def x = /${1}/; x", "1");
    test("def x = 1; def y = /${x}/; y", "1");
    test("def x = /${}/; x", "null");
    test("/${/${1}/}/", "1");
    test("def x = /'a'/; x", "'a'");
    test("def x = /'a'\\n/; x", "'a'\\n");
    test("def x = /${1 + 2}/; x", "3");
    test("def x = /x${1 + 2}y/; x", "x3y");
    test("def x = /x${/1/ + 2}y/; x", "x12y");
    test("def x = /x${\"${2*4}\" + 2}y/; x", "x82y");
    test("boolean x; def y = /$x/*2; y", "falsefalse");
    test("boolean x; def z = /$x${\"${2*4}\" + 2}y/; z", "false82y");
    test("boolean x; boolean y = true; def z = /$x${\"${\"$x\"*4}/\"+ 2}$y/; z", "falsefalsefalsefalsefalse/2true");
    test("def x = 3;def z = /x = ${x+//comment\nx}/; z", "x = 6");
    test("def x = 3; def z = /x = $x/; z", "x = 3");
    test("def x = 3; def z = /x = $x/; z", "x = 3");
    testError("def x = 3; def z = /$x=${\"${1+2}${\"}/", "unexpected end of file");
    testError("def x = 3; def z = \"$x=${/${1+2\n}/}\"", "new line not allowed");
    testError("def x = 3; def z = \"$x=${/\n${1\n+2}\n/}\"", "unexpected new line");

    test("Map x = [a:[b:1]]; x.a.\"${'b'}\"", 1);
    test("Map x = [a:[b:1]]; x.a./${'b'}/", 1);
    test("Map m = [a:[b:1]]; def x = \"${'b'}\"; m.a[x]", 1);
    test("def m = [a:[b:1]]; String x = 'b'; m.a.(x)", 1);
    test("Map m = [a:[b:1]]; String x = 'b'; m.a.(x)", 1);
    test("Map m = [a:[b:1]]; def x = 'b'; m.a.(x)", 1);
    test("Map m = [a:[b:1]]; def x = \"${'b'}\"; m.a.(x)", 1);
    test("def it = 'itx'; Map m = [a:[b:1],c:2]; def x = \"${'size'}\"; m.(x)()", 2);
    test("def it = 'itx'; Map m = [a:[b:1],c:2]; def x = /${'size'}/; m.(x)()", 2);
    test("def it = 'itx'; Map m = [a:[b:1]]; def x = /${'b'}/; m.a.(x)", 1);
    test("def it = 'itx'; Map m = [a:[b:1]]; def x = /${'b'}/; m.a[x]", 1);
    test("def it = 'itx'; def x = /${'abcde'}/; x[2]", "c");

    testError("def x = 1; \"\"\"$x\"\"", "unexpected end of file");

    test("\"${return 'abc'}\"", "abc");
    test("def x = 'xx'; \"${return x}\"", "xx");
    test("def x = 'xx'; x = \"${return x}\" + 'yy'", "xxyy");
    test("def x = 'xx'; x = \"${return x}\" + 'yy'; x", "xxyy");
    test("def x = 'xx'; \"${x += 'yy'; return x}\"", "xxyy");
    test("def x = 'xx'; \"${x += 'yy'; return x}\"; x", "xxyy");
    test("def it = 'xx'; \"${it += 'yy'; return it}\"; it", "xxyy");
    test("\"x = ${ def x = 1 + 2; x}\"", "x = 3");
    test("\"5 pyramid numbers: ${ def p(n){ n == 1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }\"", "5 pyramid numbers: 1, 5, 14, 30, 55");
    test("['a','b'].map{\"\"\"\"$it=\" + $it\"\"\" }.join(' + \", \" + ')", "\"a=\" + a + \", \" + \"b=\" + b");
  }

  @Test public void regexMatch() {
    test("'abc' =~ 'a'", true);
    test("'abc' =~ /a/", true);
    test("'abc' =~ /a/i", true);
    test("'abc' =~ /A/i", true);
    test("'Abc' =~ /a/i", true);
    test("'abc' =~ /^a/", true);
    test("'abc' =~ /^b/", false);
    test("'abc' =~ ''", true);
    test("'abc' =~ /^abc$/", true);
    test("'abc' =~ /c$/", true);
    testError("'abc' =~ /(c$/", "unclosed group");
    test("'ab\\nc' =~ /\\nc$/", true);
    test("'ab\\nc\\n' =~ /\\nc$/", true);   // Weird but $ matches the trailing \n
    test("'ab\\nc\\n' =~ /\\nc\\z/", false);
    test("'ab\\nc\\n' =~ /\\nc\\n\\z/", true);
    test("'ab\\nc\\n' =~ /\\nc$.*/", true);

    testError("'abc' =~ /a/x", "unrecognised regex modifier 'x'");
    testError("'abc' =~ /a/figsmx", "unrecognised regex modifier");

    test("'abc' !~ 'a'", false);
    test("'abc' !~ /a/", false);
    test("'abc' !~ /a/i", false);
    test("'abc' !~ /A/i", false);
    test("'Abc' !~ /a/i", false);
    test("'abc' !~ /^a/", false);
    test("'abc' !~ /^b/", true);
    test("'abc' !~ ''", false);
    test("'abc' !~ /^abc$/", false);
    test("'abc' !~ /c$/", false);
    testError("'abc' !~ /(c$/", "unclosed group");
    test("'ab\\nc' !~ /\\nc$/", false);
    test("'ab\\nc\\n' !~ /\\nc$/", false);   // Weird but $ is supposed to match the trailing \n
    test("'ab\\nc\\n' !~ /\\nc\\z/", true);
    test("'ab\\nc\\n' !~ /\\nc\\n\\z/", false);
    test("'ab\\nc\\n' !~ /\\nc$.*/", false);

    test("'a\\nbc' =~ /a$/", false);
    test("'a\\nbc' =~ /a$/m", true);
    test("'a\\nbc' =~ /a./", false);
    test("'a\\nbc' =~ /a./s", true);
    test("'a\\nbc' =~ /a.bc$/s", true);
    test("'a\\n\\nbc' =~ /a.$/s", false);
    test("'a\\n\\nbc' =~ /a.$/ms", true);

    test("def x = 'abc'; def y = 'a'; x =~ y", true);
    test("def x = 'abc'; def y = /a/; x =~ y", true);
    test("def x = 'abc'; def y = /^a/; x =~ y", true);
    test("def x = 'abc'; def y = /^b/; x =~ y", false);
    test("def x = 'abc'; def y = 'a'; x !~ y", false);
    test("def x = 'abc'; def y = /a/; x !~ y", false);
    test("def x = 'abc'; def y = /^a/; x !~ y", false);
    test("def x = 'abc'; def y = /^b/; x !~ y", true);
    test("def x = 'abc'; def y = ''; x =~ y", true);
    test("def x = 'abc'; def y = /^abc$/; x =~ y", true);
    test("def x = 'abc'; def y = /c$/; x =~ y", true);
    testError("def x = 'abc'; def y = /(c$/; x =~ y", "unclosed group");
    test("def x = 'ab\\nc'; def y = /\nc$/; x =~ y", true);
    test("def x = 'ab\\nc\\n'; def y = /\nc\\z/; x =~ y", false);
    test("def x = 'ab\\nc\\n'; def y = /\nc$.*/; x =~ y", true);
    test("def it = 'xyyz'; /yy/f and it = 'baa'; /aa$/f and return 'xxx'", "xxx");
    test("def it = 'xyz'; String x = /x/; x", "x");
    test("def it = 'xyz'; def x = /x/; x == 'x'", true);
    test("def it = 'xyz'; def x = /x/f; x", true);
    testError("def x = /x/f; x", "no 'it' variable");
    testError("def it = 'xyz'; boolean x = /x/; x", "cannot convert");
    test("def it = 'xyz'; boolean x = /x/f; x", true);
    test("def it = 'xyz'; boolean x = /a/f; x", false);
    test("def str = 'xyz'; def x; str =~ /(x)(y)(z)/ and !(str =~ /abc/) and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and !/abc/f and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and x = \"$3$2$1\"; x", "zyx");
    test("def it = 'xyz'; def x; !/(x)(y)(z)/f or x = \"$3$2$1\"; x", "zyx");
    test("def it = 'xyz'; def x = /x/f; { x ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /x/; { x == 'x' ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /x/f; { x == 'x' ? 'match' : 'nomatch' }()", "nomatch");
    test("def it = 'xyz'; def x = /X/i; { x ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /X/i; { x == 'x' ? 'match' : 'nomatch' }()", "nomatch");
    test("['abc','xzt','sas',''].map{/a/f ? true : false}", List.of(true,false,true,false));
    test("['abc','xzt','sas',''].map{ if (/a/f) true else false}", List.of(true,false,true,false));
    test("['abc','xzt','sas',''].map{ /a/f and return true; false}", List.of(true,false,true,false));

    testError("def it = 'abc'; def x; /a/ and x = 'x'; x", "regex string used in boolean context");
    testError("def it = 'abc'; def x; /a/ ? true : false", "regex string used in boolean context");
    testError("def it = 'abc'; def x; if (/a/) true else false", "regex string used in boolean context");
    testError("def it = 'abc'; def x; while (/a/) ;", "regex string used in boolean context");
    testError("def it = 'abc'; def x; for (; /a/;) ;", "regex string used in boolean context");
    test("def x = 'abc'; def y; x =~ /a/ and y = 'y'; y", "y");
    test("def x = 'a=b'; x =~ /=b/", true);
  }

  @Test public void regexCaptureVars() {
    test("def x = ''; 'abc' =~ /$x/", true);
    test ("def x = 'abc'; x =~ /${''}/", true);
    testError("def x; 'abc' =~ x", "null regex");
    testError("def x; x =~ /abc/", "null string");
    test("'abcaaxy' =~ /(a+)/", true);
    test("'bcaaxy' =~ /(a+)/ and return $1", "aa");
    test("def it = 'bcaaxy'; /(a+)/f and return $1", "aa");
    test("def x = 'abcaaxy'; x =~ /(a+)/", true);
    test("def x = 'bcaaxy'; x =~ /(a+)/ and return $1", "aa");
    test("def x; 'bcaaxy' =~ /(a+)/ and x = $1; 'abc' =~ /(a).(c)/ and x += $2; x", "aac");
    test("def x; 'bcaaxy' =~ /(a+)/ and x = $1; { 'abc' =~ /(a).(c)/ and x += $2 }; x", "aac");
    test("def x; 'bcaaxy' =~ /(a+)/ and x = $1; { 'abc' =~ /(a).(c)/ and x += $2 }; x += $1", "aacaa");
    test("def x; 'bcaaxy' =~ /(a+)/ and x = $1; 'abc' =~ /(a).(c)/; \"$1$2\"", "ac");
    testError("{ 'bcaaxy' =~ /(a+)/ }; \"$1$2\"", "no regex match");
    test("'abc' =~ /a/; $0", "a");
    test("'abc' =~ /a(bc)/; $0 + $1", "abcbc");
    test("'abc' =~ /a(bc)/; $2", null);
    test("'abc' =~ /a(bc)/ and 'xyz' =~ /(y)/; $1", "y");
    test("def it = 'abc'; /a/f; $0", "a");
    test("def it = 'abc'; /a(bc)/f; $0 + $1", "abcbc");
    test("def it = 'abc'; /a(bc)/f; $2", null);
    test("def str = 'xyz'; def x; str =~ /(x)(y)(z)/ and str =~ /x/ and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and /x/f and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and { x = \"$3$2$1\" }(); x", "zyx");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and { x = \"$3$2$1\"; /a(b)/f and x += $1 }('abc'); x", "zyxb");
    test("def it = 'xyz'; def x; /(x)(y)(z)/f and { x = \"$3$2$1\"; /a(b)/f and x += $1 }('abc'); x += $3; x", "zyxbz");
    test("def it = 'xyz'; def x; it =~ sleep(0,/(x)(y)(z)/) and { x = \"$3$2$1\" }(); x", "zyx");
  }

  @Test public void regexGlobalMatch() {
    test("String x = 'abc'; def y = ''; x =~ /([a-z])/g and y += $1; x =~ /([a-z])/g and y += $1; y", "ab");
    test("def x = 'abc'; def y = ''; x =~ /([a-z])/g and y += $1; x =~ /([a-z])/g and y += $1; y", "ab");
    test("def it = 'abc'; def y = ''; /([a-z])/g and y += $1; /([a-z])/g and y += $1; y", "ab");
    test("def it = 'ab'; def y = ''; /([a-z])/g and y += $1; /([a-z])/g and y += $1; /([a-z])/g and y = 'fail'; y", "ab");
    test("def it = 'abc'; def y = ''; while (/([a-z])/g) { y += $1 }; y += $1", "abcnull");
    testError("def it = 'abc'; def y = ''; for (int i = 0; /${''}/g && i < 10; i++) { y += $1 }; y += $1", "no regex match");
    test("def it = 'abc'; def y = ''; for (int i = 0; /${''}/g && i < 3; i++) { y += $1 }; y", "nullnullnull");
    test("def it = 'abcd'; def y = ''; for (int i = 0; /([a-z])./g && i < 10; i++) { y += $1 }; y", "ac");
    test("def it = 'abcd'; def y = ''; for (int i = 0; /([A-Z])./ig && i < 10; i++) { y += $1 }; y", "ac");
    test("def x = 'abcd'; def y = ''; for (int i = 0; x =~/([A-Z])./ig && i < 10; i++) { y += $1 }; y", "ac");

    test("def it = 'abcd'; def x = ''; /([a-z])/f and x += $1; /([a-z])/f and x += $1; x", "aa");
  }

  @Test public void regexSubstitute() {
    test("def it = 'abc'; s/a/x/", "xbc");
    test("def it = 'abc'; s/a/x/; it", "xbc");
    test("String x = 'abc'; x =~ s/a/x/", "xbc");
    test("String x = 'abc'; x =~ s/a/x/; x", "xbc");
    test("def x = [a:'abc']; x.a =~ s/a/x/", "xbc");
    test("def x = [a:'abc']; x.a =~ s/a/x/; x.a", "xbc");
    test("def it = 'abaac'; s/a/x/g", "xbxxc");
    test("def it = 'abaac'; s/a/x/g; it", "xbxxc");
    test("String x = 'abaac'; x =~ s/a/x/g", "xbxxc");
    test("String x = 'abaac'; x =~ s/a/x/g; x", "xbxxc");
    test("def x = [a:'abaac']; x.a =~ s/a/x/g", "xbxxc");
    test("def x = [a:'abaac']; x.a =~ s/a/x/fg", "xbxxc");
    test("def x = [a:'abaac']; x.a =~ s/a/x/fg; x.a", "abaac");
    test("def x = [a:'abaac']; x.a =~ s/a/x/g; x.a", "xbxxc");
    testError("def x = [a:'abc']; (x.a + 'xyz') =~ s/a/x/; x.a", "invalid lvalue");
    test("def it = 'abaac'; s/a//g", "bc");
    test("def it = 'abaac'; s/a//g; it", "bc");
    test("def it = 'abaac'; s///g", "abaac");
    test("def it = 'abaac'; s///g; it", "abaac");
    test("def it = 'abaac'; s//a/g", "aaabaaaaaca");
    test("def it = 'abaac'; s//a/g; it", "aaabaaaaaca");
    test("def it = 'ab\\ncd'; /b$/mf", true);
    test("def it = 'ab\\n#d'; /b$\\n#/mf", true);
    test("def it = 'ab\\ncd'; /b\\$\\nc/mf", true);
    test("def it = 'ab\\ncd'; /b\\$.c/smf", true);
    test("def it = 'ab\\ncd'; /b$.c/smf", true);
    test("def it = 'ab\\ncd'; /b.c/smf", true);
  }

  @Test public void regexSubstituteExprString() {
    test("def it = 'abaAc'; s/(A)/x$1/i; ", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/i; it", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/i; ", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/i; x", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/i; ", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/i; x.a", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/fi; ", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/fi; it", "abaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/fi; ", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/fi; x", "abaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/fi; ", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/fi; x.a", "abaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/ig; x.a", "xabxaxAc");
    test("def y = 'y'; def x = [a:'abaAc']; x.a =~ s/(A)/x$1$y/ig; x.a", "xaybxayxAyc");
    test("def x = 'abcdefghijklmn'; x =~ s/(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)/x$10$11/ig", "xjkmn");
    test("def x = 'abcdefghijklmn'; x =~ s/(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)/x$10$11/ig; x", "xjkmn");
    test("def x = 'abcdefghijklmn'; x =~ s/(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)/x$10$11/i", "xjkmn");
    test("def x = 'abcdefghijk123456789xy11'; x =~ s/(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)/X$10$11/ig", "XjkXxy11");
    test("def x = 'abcdefghijk123456789xy11'; x =~ s/(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)(.)/X$10$11/ig; x", "XjkXxy11");
    testError("def x = [a:'abaAc']; x.a =~ s/(A)/x$1$2/ig; x.a", "no group");
    test("def a = 'a'; def it = 'abcd'; s/(.)(.)/${a}${$1}${$2*2 + $1*2}/g", "aabbaaacddcc");
    test("def a = 'a'; def it = 'abc'; s/([a-z])/\\$a\\$1/g", "$a$1$a$1$a$1");
    test("def it = 'abc'; s/([a-z])/\\$1\\$1/g", "$1$1$1$1$1$1");
    test("def it = 'abc'; s/([a-z])/\\$1\\$1${$1 + $1}/g", "$1$1aa$1$1bb$1$1cc");
  }

  @Test public void doBlock() {
    test("true and do { true } and return true", true);
    test("true and do { for(int i=0;i<10;i++); false } and return true", true);
    test("true and do { for(int i=0;i<10;i++); return false } and return true", false);
    test("def it = 'abc'; /ab/f and do { /c/f and return false } and return true", false);
    test("def x; def it = 'abc'; /ab/f and do { it = 'xyz'; x = 'x' } and return \"$it$x\"", "xyzx");
  }

  @Test public void filter() {
    test("[].filter{it>1}", List.of());
    test("[].filter()", List.of());
    test("[:].filter()", List.of());
    test("def x = []; x.filter{it>1}", List.of());
    test("def x = []; x.filter()", List.of());
    test("def x = [:]; x.filter()", List.of());
    testError("null.filter()", "null value");
    testError("''.filter()", "no such method");
    testError("def x = 'abc'; x.filter()", "no such method");
    testError("def x = null; x.filter()", "null value");
    test("[1,2,3].filter()", List.of(1,2,3));
    test("[1,2,3].filter{it>1}", List.of(2,3));
    test("[a:true,b:false,c:true].filter{it[1]}.map{it[0]}", List.of("a","c"));
    test("def f = [a:true,b:false,c:true].filter; f{it[1]}.map{it[0]}", List.of("a","c"));
    test("def x = [a:true,b:false,c:true]; x.filter{it[1]}.map{it[0]}", List.of("a","c"));
    testError("[1,2,3].filter('abc')", "cannot convert");
    testError("def f = [1,2,3].filter; f('abc')", "cannot convert");
  }

  @Test public void lines() {
    test("''.lines()", List.of());
    testError("[].lines()", "no such method");
    testError("def x = []; x.lines()", "no such method");
    test("' '.lines()", List.of(" "));
    test("'\\n'.lines()", List.of("",""));
    test("'abc\\nxyz'.lines()", List.of("abc","xyz"));
    test("def x = ''; x.lines()", List.of());
    test("def x = ' '; x.lines()", List.of(" "));
    test("def x = '\\n'; x.lines()", List.of("",""));
    test("def x = 'abc\\nxyz'; x.lines()", List.of("abc","xyz"));
    test("'abc\\n\\nxyz'.lines()", List.of("abc","","xyz"));
    test("'abc\\n\\nxyz\\n'.lines()", List.of("abc","","xyz",""));
    test("'abc\\n\\nxyz\\n\\n'.lines()", List.of("abc","","xyz","",""));
    test("'\\nabc\\n\\nxyz\\n\\n'.lines()", List.of("","abc","","xyz","",""));
    test("'\\n\\nabc\\n\\nxyz\\n\\n'.lines()", List.of("","","abc","","xyz","",""));
  }

  @Test public void stringLength() {
    test("''.length()", 0);
    test("''.size()", 0);
    test("def x = ''; x.length()", 0);
    test("def x = ''; x.size()", 0);
    test("def x = ''; def f = x.length; f()", 0);
    test("def x = ''; def f = x.size; f()", 0);
    test("'abc'.length()", 3);
    test("'abc'.size()", 3);
    test("def x = 'abc'; x.length()", 3);
    test("def x = 'abc'; x.size()", 3);
    testError("1.length()", "no such method");
    testError("[1,2,3].length()", "no such method");
    testError("def x = 1; x.length()", "no such method");
  }

  @Test public void stringToUpperLowerCase() {
    test("'ABC'.toLowerCase()", "abc");
    test("'ABC'.toLowerCase(1)", "aBC");
    test("'ABC'.toLowerCase(4)", "abc");
    test("'ABC'.toLowerCase(0)", "ABC");
    test("''.toLowerCase(0)", "");
    test("''.toLowerCase()", "");
    test("''.toLowerCase(1)", "");
    test("def x  = 'ABC'; x.toLowerCase()", "abc");
    test("def x  = 'ABC'; x.toLowerCase(1)", "aBC");
    test("def x  = 'ABC'; x.toLowerCase(4)", "abc");
    test("def x  = 'ABC'; x.toLowerCase(0)", "ABC");
    test("def x  = 'ABC'; def f = x.toLowerCase; f(2)", "abC");
    test("def x  = ''; x.toLowerCase(0)", "");
    test("def x  = ''; x.toLowerCase()", "");
    test("def x  = ''; x.toLowerCase(1)", "");
    testError("def x = []; x.toLowerCase()", "no such method");
    test("'AbC.D_eF'.toLowerCase()", "abc.d_ef");

    test("'abc'.toUpperCase()", "ABC");
    test("'abc'.toUpperCase(1)", "Abc");
    test("'abc'.toUpperCase(4)", "ABC");
    test("'abc'.toUpperCase(0)", "abc");
    test("''.toUpperCase(0)", "");
    test("''.toUpperCase()", "");
    test("''.toUpperCase(1)", "");
    test("def x  = 'abc'; x.toUpperCase()", "ABC");
    test("def x  = 'abc'; x.toUpperCase(1)", "Abc");
    test("def x  = 'abc'; x.toUpperCase(4)", "ABC");
    test("def x  = 'abc'; x.toUpperCase(0)", "abc");
    test("def x  = 'abc'; def f = x.toUpperCase; f(2)", "ABc");
    test("def x  = ''; x.toUpperCase(0)", "");
    test("def x  = ''; x.toUpperCase()", "");
    test("def x  = ''; x.toUpperCase(1)", "");
    testError("def x = []; x.toUpperCase()", "no such method");
    test("'AbC.D_eF'.toUpperCase()", "ABC.D_EF");
  }

  @Test public void substring() {
    test("''.substring(0)", "");
    testError("''.substring(-1)", "string index out of range");
    testError("''.substring(1)", "string index out of range");
    test("'abc'.substring(0)", "abc");
    test("'abc'.substring(1)", "bc");
    test("'abc'.substring(2)", "c");
    test("'abc'.substring(3)", "");
    testError("'abc'.substring(4)", "string index out of range");
    test("'abcdef'.substring(1,4)", "bcd");
    test("'abcdef'.substring(1,6)", "bcdef");
    testError("'abcdef'.substring(1,7)", "StringIndexOutOfBoundsException");
    testError("'abcdef'.substring(-1,6)", "StringIndexOutOfBoundsException");
    test("'abcdef'.substring(0,6)", "abcdef");
    test("'abcdef'.substring(0,1)", "a");
    test("'abcdef'.substring(5,6)", "f");
    test("\"a${'bc'}def\".substring(2,5)", "cde");
    test("/a${'bc'}def/.substring(2,5)", "cde");

    test("def x = ''; x.substring(0)", "");
    testError("def x = ''; x.substring(-1)", "string index out of range");
    testError("def x = ''; x.substring(1)", "string index out of range");
    test("def x = 'abc'; x.substring(0)", "abc");
    test("def x = 'abc'; x.substring(1)", "bc");
    test("def x = 'abc'; x.substring(2)", "c");
    test("def x = 'abc'; x.substring(3)", "");
    testError("def x = 'abc'; x.substring(4)", "string index out of range");
    test("def x = 'abcdef'; x.substring(1,4)", "bcd");
    test("def x = 'abcdef'; x.substring(1,6)", "bcdef");
    testError("def x = 'abcdef'; x.substring(1,7)", "StringIndexOutOfBoundsException");
    testError("def x = 'abcdef'; x.substring(-1,6)", "StringIndexOutOfBoundsException");
    test("def x = 'abcdef'; x.substring(0,6)", "abcdef");
    test("def x = 'abcdef'; x.substring(0,1)", "a");
    test("def x = 'abcdef'; x.substring(5,6)", "f");
    test("def x = \"a${'bc'}def\"; x.substring(2,5)", "cde");
    test("def x = /a${'bc'}def/; x.substring(2,5)", "cde");
    test("def x = /a${'bc'}def/; def f = x.substring; f(2,5)", "cde");
    test("def x = /a${'bc'}def/; def f = x.substring; f(2)", "cdef");
    testError("def x = /a${'bc'}def/; def f = x.substring; f(2,7)", "StringIndexOutOfBounds");
  }

  @Test public void join() {
    test("[].join()", "");
    test("[].join('')", "");
    test("[].join('x')", "");
    test("[:].join()", "");
    test("[:].join('')", "");
    test("['x'].join()", "x");
    test("['x'].join(',')", "x");
    test("['x','y'].join(',')", "x,y");
    test("['x','y',1].join(', ')", "x, y, 1");
    test("[x:1,y:2].join(',')", "x=1,y=2");
    test("[[x:1],[y:2]].join(', ')", "[x:1], [y:2]");
    test("def x = []; x.join()", "");
    test("def x = []; x.join('')", "");
    test("def x = []; x.join('x')", "");
    test("def x = [:]; x.join()", "");
    test("def x = [:]; x.join('')", "");
    test("def x = ['x']; x.join()", "x");
    test("def x = ['x']; x.join(',')", "x");
    test("def x = ['x','y']; x.join(',')", "x,y");
    test("def x = ['x','y',1]; x.join(', ')", "x, y, 1");
    test("def x = [x:1,y:2]; x.join(',')", "x=1,y=2");
    test("def x = [[x:1],[y:2]]; x.join(', ')", "[x:1], [y:2]");
    test("['x','y','1','2','a'].filter{ /[a-z]/f }.join(',')", "x,y,a");
    test("def x = ['x','y','1','2','a']; x.filter{ /[a-z]/f }.join(',')", "x,y,a");
  }

  @Test public void listLiterals() {
    test("[]", List.of());
    test("[1]", List.of(1));
    testError("[1,", "unexpected EOF");
    test("[1,2,3]", List.of(1,2,3));
    test("[1,2+3,3]", List.of(1,5,3));
    test("[[]]", List.of(List.of()));
    test("[[1]]", List.of(List.of(1)));
    test("[[1],2]", List.of(List.of(1),2));
    test("[][0]", null);
    test("[1,2,3][0]", 1);
    test("[1,2,3][3]", null);
    test("[1,[2,3,4],5][1]", List.of(2,3,4));
    testError("String x = []", "cannot convert");
    testError("String x = [1,2,3]", "cannot convert");
    testError("def y = []; String x = y", "cannot convert");
    testError("def y = [1,2,3]; String x = y", "cannot convert");
    test("'' + [1,2,3]", "[1, 2, 3]");
    test("'' + []", "[]");
    test("'' + [1,[2,3]]", "[1, [2, 3]]");
    test("def x = [1,2,3]; ''+x", "[1, 2, 3]");
    test("def x = []; ''+x", "[]");
    test("def x = [1,[2,3]]; ''+x", "[1, [2, 3]]");
  }

  @Test public void mapLiterals() {
    test("[:]", new HashMap<>());
    test("[a:1]", Map.of("a",1));
    testError("[:", "unexpected EOF");
    testError("[:123]", "unexpected token");
    test("[for:1]", Map.of("for",1));
    test("['for':1]", Map.of("for",1));
    test("[a:1,b:2]", Map.of("a",1, "b", 2));
    test("['a':1,'b':2]", Map.of("a",1, "b", 2));
    test("[('a'+'b'):1,b:2]", Map.of("ab",1, "b", 2));
    test("[\"ab\":1,b:2]", Map.of("ab",1, "b", 2));
    test("[a:1,b:[c:2]]", Map.of("a",1, "b", Map.of("c",2)));
    test("{:}", new HashMap<>());
    test("{a:1}", Map.of("a",1));
    testError("{:", "unexpected EOF");
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
    testError("String x = [a:1]", "cannot convert");
    testError("String x = {a:1,b:2}", "cannot convert");
    testError("String x = [:]", "cannot convert");

    test("String x = '' + [a:1]", "[a:1]");
    test("String x = '' + [a:1,b:2]", "[a:1, b:2]");
    test("String x = '' + {a:1,b:2}", "[a:1, b:2]");
    test("String x = '' + [:]", "[:]");
    test("String x = '' + [a:[1,2,3]]", "[a:[1, 2, 3]]");
    test("String x = '' + [a:[1,[b:2],3]]", "[a:[1, [b:2], 3]]");

    test("[\"a${1+2}\":1,b:2]", Map.of("a3",1, "b", 2));
    test("[this:1, if:2, while:[if:[z:3],for:3]].while.if.z", 3);
    test("[a:1]['a']", 1);
    testError("[(1):2]", "map key must be string");
    testError("[a:2,b:3,a:4]", "key 'a' occurs multiple times");
  }

  @Test public void listMapVariables() {
    testError("Map x = 1", "cannot convert");
    testError("List x = 1", "cannot convert");
    test("Map x = [a:1]; x.a", 1);
    test("Map x = [a:1]", Map.of("a", 1));
    test("Map x = [a:1]; 1", 1);
    testError("List list = [1]; list.a", "invalid object type");
    testError("int x = 1; x.a", "invalid object type");
    testError("int x = 1; x[0]", "invalid object type");
    test("Map map = [:]; map[0]", null);
    testError("Map map = [:]; map = 1", "cannot convert from type of right hand side");
    testError("List list = []; list = 1", "cannot convert from type of right hand side");

    test("var x = [a:1]; x.a", 1);
    test("var x = [a:1]", Map.of("a", 1));
    test("var x = [a:1]; 1", 1);
    testError("var list = [1]; list.a", "invalid object type");
    testError("var x = 1; x.a", "invalid object type");
    testError("var x = 1; x[0]", "invalid object type");
    test("var map = [:]; map[0]", null);
    testError("var map = [:]; map = 1", "cannot convert from type of right hand side");
    testError("var list = []; list = 1", "cannot convert from type of right hand side");

    test("def m = [a:1]", Map.of("a",1));
    test("def m = [1]", List.of(1));
    test("def m = [a:1]; m.a", 1);
    test("def m = [a:1]; m.b", null);
    test("def m = [a:[b:2]]; m.a.b", 2);
    test("def m = [a:[b:2]]; m?.a?.b", 2);
    test("def m = [a:[b:2]]; m?['a']?['b']", 2);
    test("def m = [a:[b:2]]; m?['a'].b", 2);
    test("def m = [a:[b:2]]; m.a.x?.y", null);
    testError("def m = [a:[b:2]]; m.a.x?.y.z", "null value");

    test("def x = [1,2,3]", List.of(1,2,3));
    test("def x = []", List.of());
    test("def x = []; x[0]", null);
    test("def x = [1,2,3][4]; x", null);
    test("def x = [1,2,3]; x[1]", 2);
    testError("def x = []; x[-1]", "index must be >= 0");
    test("def x = []; def y = 7; x[y + y * y - y]", null);
    test("def x = [1,2,3,4]; def y = 7; x[y + y * y - y * y - 5]", 3);
    test("def x = [0,1,2,3,4]; def y = [a:7]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.0]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.5]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7.5D]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,1,2,3,4]; def y = [a:7L]; x[--y.a - y.a-- + --y.a - 3]", 1);
    test("def x = [0,[1,2,3],4]; def y = 2; x[1 + y + 3 - y - 3][y/2]", 2);

    test("def x = [1,2,3]; def y = [2,3]; x == y", false);
    test("def x = [1,2,3]; def y = [1,2,3]; x == y", true);
    test("def x = [a:1,b:2,c:3]; def y = [b:2,c:3]; x == y", false);
    test("def x = [a:1,b:2,c:3]; def y = [b:2,a:1,c:3]; x == y", true);
    test("[1,2,3] == [2,3]", false);
    test("[1,2,3] == [1,2,3]", true);
    test("[a:1,b:2,c:3] == [b:2,c:3]", false);
    test("[a:1,b:2,c:3] == [b:2,a:1,c:3]", true);

    test("def x = [1,2,3]; def y = [2,3]; x != y", true);
    test("def x = [1,2,3]; def y = [1,2,3]; x != y", false);
    test("def x = [a:1,b:2,c:3]; def y = [b:2,c:3]; x != y", true);
    test("def x = [a:1,b:2,c:3]; def y = [b:2,a:1,c:3]; x != y", false);
    test("[1,2,3] != [2,3]", true);
    test("[1,2,3] != [1,2,3]", false);
    test("[a:1,b:2,c:3] != [b:2,c:3]", true);
    test("[a:1,b:2,c:3] != [b:2,a:1,c:3]", false);
    test("def x = [a:1]; x['a']", 1);
    test("def x = [a:1]; x['a'] = 2; x.a", 2);
    test("def x = [a:1]; def f = 'a'; x[f]", 1);
    test("def x = [a:1]; def f = 'a'; x[f] = 2; x.a", 2);
    test("Map x = [a:1]; x['a']", 1);
    test("Map x = [a:1]; x['a'] = 2; x.a", 2);
    test("Map x = [a:1]; def f = 'a'; x[f]", 1);
    test("Map x = [a:1]; def f = 'a'; x[f] = 2; x.a", 2);
  }

  @Test public void fieldAssignments() {
    testError("Map m = [a:1]; m*a = 2", "invalid lvalue");
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
    testError("def m; m.a = 1", "null value");

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
    test("Map m = [:]; def x = 'ab'; m.(x).c = 1; m.ab.c", 1);
    test("Map m = [:]; def x = 'ab'; m.('a'+'b').c = 1; m.(x).c", 1);

    test("Map m = [:]; m.a += 4", 4);
    test("Map m = [:]; m.a.b += 4", 4);
    test("def m = [:]; m.a.b += 4", 4);

    testError("def x = [:]; x.a['b'] = 1", "non-numeric value for index");
    test("Map m = [:]; m.a[1] = 4; m.a[1]", 4);
    test("Map m = [:]; m.a[1] += 4; m.a[1]", 4);
    test("def m = [:]; m.a[2].b += 4; m.a[2].b", 4);

    test("def m = [:]; m.a[0].c += 4; m.a[0].c", 4);
    test("Map m = [a:1]; m.a++", 1);
    test("Map m = [a:1]; m.a++; m.a", 2);
    test("Map m = [a:1]; ++m.a", 2);
    test("Map m = [a:1]; ++m.a; m.a", 2);
    test("Map m = [:]; m.a++", 0);
    test("Map m = [:]; ++m.a", 1);
    test("Map m = [:]; m.a++; m.a", 1);
    test("Map m = [:]; ++m.a; m.a", 1);
    test("Map m = [:]; m.a.b++", 0);
    test("Map m = [:]; ++m.a.b", 1);
    test("Map m = [:]; m.a.b++; m.a.b", 1);
    test("Map m = [:]; ++m.a.b; m.a.b", 1);
    test("Map m = [:]; m.a.b *= 1", 0);
    test("Map m = [:]; m.a.b *= 1; m.a.b", 0);

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

    testError("def m = [:]; m.a.b() = 1", "invalid lvalue");
    testError("def m = [:]; m.a.['b']", "unexpected token '['");
    testError("def m = [:]; m.a?.?['b']", "unexpected token '?['");
    test("Map m; sleep(0,m).a.b = 1", 1);
    test("Map m; sleep(0,m).(sleep(0,'a')).b = 1", 1);
  }

  @Test public void conditionalAssignment() {
    test("Map x = [a:1]; 2 + (x.a?=3)", 5);
    test("Map x = [a:1]; (x.a?=3) + 2", 5);
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
    test("Map m; def x = [a:3]; m.a.b ?= 3", 3);
    test("def x; x ?= 2", 2);
    test("def x; x ?= 2L", 2L);
    test("def x; x ?= 2.0D", 2.0D);
    test("def x; x ?= 2.0", "#2.0");
    test("def x = [:]; x.a[1].b ?= 2", 2);
    test("def x = [:]; x.a[1].b ?= 2L", 2L);
    test("def x = [:]; x.a[1].b ?= 2.0D", 2.0D);
    test("def x = [:]; x.a[1].b ?= 2.0", "#2.0");
    test("int x; x ?= 2", 2);
    test("long x; x ?= 2L", 2L);
    test("double x; x ?= 2.0D", 2.0D);
    test("Decimal x; x ?= 2.0", "#2.0");
    test("def y; int x = 1; x ?= y", null);
    test("def y; long x = 1; x ?= y", null);
    test("def y; double x = 1; x ?= y", null);
    test("def y; Decimal x = 1; x ?= y", null);
    test("def y; int x = 1; x ?= y; x", 1);
    test("def y; long x = 1; x ?= y; x", 1L);
    test("def y; double x = 1; x ?= y; x", 1D);
    test("def y; Decimal x = 1; x ?= y; x", "#1");
    test("def y; String x = '1'; x ?= y", null);
    test("def y; String x = '1'; x ?= y; x", "1");

    test("Map m; def x = [a:3]; 1 + (m.a.b ?= 2) + (m.a.c ?= 3)", 6);
    testError("Map m; def x = [a:3]; 1 + (m.a.b ?= m.xxx) + (m.a.c ?= 3)", "null operand");
    test("def x = [:]; def y; 1 + (x.a.b ?= 2) + (x.a.c ?= 3)", 6);
    test("def x = [a:3]; def y; y ?= x.z", null);
    test("def x = [a:3]; def y; y ?= x.a", 3);
    test("def x = [a:3]; def y; (y ?= x.a) + (y ?= x.a)", 6);
    testError("def x = [a:3]; def y; (y ?= x.a) + (y ?= x.xxx)", "null operand");
    testError("def x = [a:3]; def y; (y ?= x.xxx) + (y ?= x.xxx)", "null operand");
    test("def x = [a:3]; def y; (y ?= x.a) + (x.b.b[2].c ?= 3)", 6);
    testError("def x = [a:3]; def y; (y ?= x.x) + (x.a.b[2].c ?= x.x)", "null operand");
    test("Map m; def x = [a:3]; (m.a.b.c ?= x.a) + (m.a.b ?= 3)", 6);

    test("def x = [a:3]; def y; x.b += (y ?= x.a)", 3);
    testError("def x = [a:3]; def y; x.b += (y ?= x.xxx)", "null operand");

    test("def x; 1 + (x ?= 2)", 3);
    test("def x; 1 + (x ?= 2L)", 3L);
    test("def x; 1 + (x ?= 2.0D)", 3D);
    test("def x; 1 + (x ?= 2.0)", "#3.0");

    test("def x; def y; (x ?= 1) + (y ?= 2)", 3);
    test("def x; def y; def z = 1; (x ?= z) + (y ?= z)", 2);
    test("Map m; Map x; (m.a ?= 1) + (x.a ?= 2)", 3);
    test("Map m; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += 2", 3);
    test("Map m; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += (x.c ?= 2)", 3);
    test("Map m; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += (x.c ?= 2); m.a.b.c", 3);
    test("Map m; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += 2", 3);
    test("Map m; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += (x ?= 2)", 3);
    test("Map m; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += (x ?= 2); m.a.b.c", 3);
    test("def x; def y; y ?= (y ?= x.a); y", null);
    test("def x; def y; y ?= (y ?= x.size()); y", null);
    test("def x; def y; y ?= (y ?= x.a) ?: 4; y", 4);
    test("def x; def y; y ?= (y ?= x.a) ?: (y ?= x.b) ?: 4; y", 4);
    test("def x; def y; y ?= (y ?= x.size()) ?: 4; y", 4);
    test("def x; def y; y ?= (y ?= 3) + ((y ?= x.size()) ?: 4); y", 7);
    testError("String x; x.a = 'abc'", "invalid object type");
    testError("String x; x.a += 'abc'", "invalid object type");
    testError("String x; x['a'] += 'abc'", "invalid object type");
    testError("String x; x[0] += 'abc'", "cannot assign to element of string");
  }

  @Test public void nullValues() {
    test("String s; s", "");
    test("String s = null", null);
    test("Map x; x", Map.of());
    test("Map x = null; x", null);
    test("List x; x", List.of());
    test("List x = null; x", null);
    testError("int i = null", "cannot convert null");
    testError("1.0 + null", "null operand");
    testError("def x; int i = x", "cannot convert null");
    test("String x = null; x = null", null);
    test("List x = null; x = null", null);
    test("Map x = null; x = null", null);
    test("def f(String x){x}; f(null)", null);
    test("def f(List x){x}; f(null)", null);
    test("def f(Map x){x}; f(null)", null);
  }

  @Test public void stringIndexing() {
    test("'abc'[0]", "a");
    testError("''[0]", "index (0) too large");
    test("def x = 'abcdef'; def i = 3; x[i]", "d");
    test("def x = 'abcdef'; def i = 3; x?[i]", "d");
    test("String x = 'abcdef'; def i = 3; x[i]", "d");
    test("String x = 'abcdef'; def i = 3; x?[i]", "d");
    test("var x = 'abcdef'; def i = 3; x[i]", "d");
    test("var x = 'abcdef'; def i = 3; x?[i]", "d");
    test("def x; x?[0]", null);
    testError("String s = 'abc'; s[1] = s[2]", "cannot assign to element of string");
    testError("String s = 'abc'; s.a", "invalid object type");
    testError("def s = 'abc'; s.a", "field access not supported");
    testError("def s = 'abc'; s.a()", "no such method");
  }

  @Test public void ifStatement() {
    test("if (true) true", true);
    test("if (true) true else false", true);
    test("if (false) true", null);
    test("if (false) true else false", false);
    testError("if (true) int i = 2", "unexpected token 'int'");
    test("int x; if (x) { x += 1 } else { x += 2 }", 2);
    test("int x; if (x) { x += 1 }", null);
    test("int x; if (x) { x += 1; x+= 2\n}", null);
    test("int x; if (x) { x += 1 } else { x += 2;\n}", 2);
    test("int x; if (x) { x += 1 } else { x += 2\n}", 2);
    test("int x; if (x)\n{\nx += 1\nx+=7\n}\n else \n{\n x += 2;\n x+=3 }\n", 5);
    test("long x = 2; if (x * 2 == 4) ++x", 3L);
    test("long x = 2; if (x * 2 == 4) ++x else if (!x) { x-- }", 3L);
    test("def x = 2; if (x * 2 < 5) ++x else { --x }", 3);
    test("def x = 2; if (x * 2 < -5) ++x else { --x }", 1);
    test("def x = 2; if (x == 2) { def x = 5; x++ }; x", 2);
    test("def x = 2; if (x == 2) { def x = 5; x++; if (true) { def x = 1; x-- } }; x", 2);
    testError("if (true) { if(true){1} if(true){1} }", "expecting end of statement");
    test("if (true) { if(true){1}; if(true){1} }", 1);
    testError("int f() { if (true) { println } }; f()", "implicit return of null");
    test("if (true) {}", null);
  }

  @Test public void ifUnless() {
    test("true if true", true);
    test("true unless true", null);
    test("def x = 3; x = 4 unless x == 3; x", 3);
    test("def x = 3; x = 4 unless x != 3; x", 4);
    test("def x = 3; x = 4 if x != 3; x", 3);
    test("def x = 3; x = 4 and return x/2 if x == 3; x", 2);
    testError("def x = 3; x = 4 and return x/2 if x == 3 unless true; x", "unexpected token 'unless'");
    testError("unless true", "unexpected token 'unless'");
    testError("if true", "unexpected token 'true'");
    test("def x; x ?= x if true", null);
    test("def x; x ?= 1 if true", 1);
    test("int x; for (int i=0; i < 10; i++) { continue if i > 5; x += i }; x", 15);
    test("int x; for (int i=0; i < 10; i++) { break unless i < 5; x += i }; x", 10);
    test("def it = 'abc'; return if /x/f; s/b/x/g;", "axc");
    test("return if true; 1", null);
    test("return if false; 1", 1);
    test("return unless true; 1", 1);
    test("return unless false; 1", null);
    test("return 7 if true; 1", 7);
    test("return 7 if false; 1", 1);
    test("return 7 unless true; 1", 1);
    test("return 7 unless false; 1", 7);
    test("return and true if true; 1", null);
    test("return and true if false; 1", 1);
    test("return and true unless true; 1", 1);
    test("return and true unless false; 1", null);
    test("true and return if true; 1", null);
    test("false or return if false; 1", 1);
    test("true and return unless true; 1", 1);
    test("false or return unless false; 1", null);
  }

  @Test public void booleanNonBooleanComparisons() {
    test("true != 1", true);
    test("true == 1", false);
    test("1 != false", true);
    test ("1 == false", false);
    testError("1 < true", "cannot be compared");
    testError("1L < true", "cannot be compared");
    testError("1D < true", "cannot be compared");
    testError("1.0 < true", "cannot be compared");
    test("true != 1L", true);
    test("true == 1L", false);
    test("1L != false", true);
    test ("1L == false", false);
    test("true != 1D", true);
    test("true == 1D", false);
    test("1D != false", true);
    test ("1D == false", false);
    test("true != 1.0", true);
    test("true == 1.0", false);
    test("1.0 != false", true);
    test ("1.0 == false", false);
    test("boolean x = true; int y = 1; x != y", true);
    test("boolean x = true; int y = 1; x == y", false);
    test("int x = 1; boolean y = false; x != y", true);
    test ("int x = 1; boolean y = false; x == y", false);
    test("def x = true; def y = 1; x != y", true);
    test("def x = true; def y = 1; x == y", false);
    test("def x = 1; def y = false; x != y", true);
    test ("def x = 1; def y = false; x == y", false);
    test("boolean x = true; long y = 1; x != y", true);
    test("boolean x = true; long y = 1; x == y", false);
    test("long x = 1; boolean y = false; x != y", true);
    test ("long x = 1; boolean y = false; x == y", false);
    test("def x = true; def y = 1L; x != y", true);
    test("def x = true; def y = 1L; x == y", false);
    test("def x = 1L; def y = false; x != y", true);
    test ("def x = 1L; def y = false; x == y", false);

    test("boolean x = true; double y = 1; x != y", true);
    test("boolean x = true; double y = 1; x == y", false);
    test("double x = 1; boolean y = false; x != y", true);
    test ("double x = 1; boolean y = false; x == y", false);
    test("def x = true; def y = 1D; x != y", true);
    test("def x = true; def y = 1D; x == y", false);
    test("def x = 1D; def y = false; x != y", true);
    test ("def x = 1D; def y = false; x == y", false);

    test("boolean x = true; Decimal y = 1; x != y", true);
    test("boolean x = true; Decimal y = 1; x == y", false);
    test("Decimal x = 1; boolean y = false; x != y", true);
    test ("Decimal x = 1; boolean y = false; x == y", false);
    test("def x = true; def y = 1.0; x != y", true);
    test("def x = true; def y = 1.0; x == y", false);
    test("def x = 1.0; def y = false; x != y", true);
    test ("def x = 1.0; def y = false; x == y", false);

    test("boolean x = true; String y = '1'; x != y", true);
    test("boolean x = true; String y = '1'; x == y", false);
    test("String x = '1'; boolean y = false; x != y", true);
    test ("String x = '1'; boolean y = false; x == y", false);
    test("def x = true; def y = '1'; x != y", true);
    test("def x = true; def y = '1'; x == y", false);
    test("def x = '1'; def y = false; x != y", true);
    test ("def x = '1'; def y = false; x == y", false);

    test("int x = 1; String y = '1'; x != y", true);
    test("int x = 1; String y = '1'; x == y", false);
    test("String x = '1'; int y = 1; x != y", true);
    test ("String x = '1'; int y = 1; x == y", false);
    test("def x = 1; def y = '1'; x != y", true);
    test("def x = 1; def y = '1'; x == y", false);
    test("def x = '1'; def y = 1; x != y", true);
    test ("def x = '1'; def y = 1; x == y", false);
  }

  @Test public void constBooleanComparisons() {
    test("null == null", true);
    test("null == true", false);
    test("true == null", false);
    test("null == false", false);
    test("false == null", false);
    test("false == true", false);
    test("false == false", true);
    test("true == false", false);
    test("true == true", true);

    test("null != null", false);
    test("null != true", true);
    test("true != null", true);
    test("null != false", true);
    test("false != null", true);
    test("false != true", true);
    test("false != false", false);
    test("true != false", true);
    test("true != true", false);

    test("null < null", false);
    test("null < true", true);
    test("true < null", false);
    test("null < false", true);
    test("false < null", false);
    test("false < true", true);
    test("false < false", false);
    test("true < false", false);
    test("true < true", false);

    test("null <= null", true);
    test("null <= true", true);
    test("true <= null", false);
    test("null <= false", true);
    test("false <= null", false);
    test("false <= true", true);
    test("false <= false", true);
    test("true <= false", false);
    test("true <= true", true);

    test("null > null", false);
    test("null > true", false);
    test("true > null", true);
    test("null > false", false);
    test("false > null", true);
    test("false > true", false);
    test("false > false", false);
    test("true > false", true);
    test("true > true", false);

    test("null >= null", true);
    test("null >= true", false);
    test("true >= null", true);
    test("null >= false", false);
    test("false >= null", true);
    test("false >= true", false);
    test("false >= false", true);
    test("true >= false", true);
    test("true >= true", true);
  }

  BiConsumer<String,String> comparisonTests = (smaller, bigger) -> {
    String init = "int i3=3; long l5=5L; double d7=7.0D; Decimal dec13=13.0; String sabc = 'abc';" +
                  "def di3=3; def dl5=5L; def dd7=7.0D; def ddec13=13.0; def dsabc = 'abc';";
    BiConsumer<String,Object> _test = (code,expected) -> test(init + code, expected);

    _test.accept("null == " + bigger, false);
    _test.accept(bigger + " == null", false);
    _test.accept("null == " + smaller, false);
    _test.accept(smaller + " == null", false);
    _test.accept(smaller + " == " + bigger, false);
    _test.accept(smaller + " == " + smaller, true);
    _test.accept(bigger + " == " + smaller, false);
    _test.accept(bigger + " == " + bigger, true);

    _test.accept("null != " + bigger, true);
    _test.accept(bigger + " != null", true);
    _test.accept("null != " + smaller, true);
    _test.accept(smaller + " != null", true);
    _test.accept(smaller + " != " + bigger, true);
    _test.accept(smaller + " != " + smaller, false);
    _test.accept(bigger + " != " + smaller, true);
    _test.accept(bigger + " != " + bigger, false);

    _test.accept("null < " + bigger, true);
    _test.accept(bigger + " < null", false);
    _test.accept("null < " + smaller, true);
    _test.accept(smaller + " < null", false);
    _test.accept(smaller + " < " + bigger, true);
    _test.accept(smaller + " < " + smaller, false);
    _test.accept(bigger + " < " + smaller, false);
    _test.accept(bigger + " < " + bigger, false);

    _test.accept("null <= " + bigger, true);
    _test.accept(bigger + " <= null", false);
    _test.accept("null <= " + smaller, true);
    _test.accept(smaller + " <= null", false);
    _test.accept(smaller + " <= " + bigger, true);
    _test.accept(smaller + " <= " + smaller, true);
    _test.accept(bigger + " <= " + smaller, false);
    _test.accept(bigger + " <= " + bigger, true);

    _test.accept("null > " + bigger, false);
    _test.accept(bigger + " > null", true);
    _test.accept("null > " + smaller, false);
    _test.accept(smaller + " > null", true);
    _test.accept(smaller + " > " + bigger, false);
    _test.accept(smaller + " > " + smaller, false);
    _test.accept(bigger + " > " + smaller, true);
    _test.accept(bigger + " > " + bigger, false);

    _test.accept("null >= " + bigger, false);
    _test.accept(bigger + " >= null", true);
    _test.accept("null >= " + smaller, false);
    _test.accept(smaller + " >= null", true);
    _test.accept(smaller + " >= " + bigger, false);
    _test.accept(smaller + " >= " + smaller, true);
    _test.accept(bigger + " >= " + smaller, true);
    _test.accept(bigger + " >= " + bigger, true);

    _test.accept(bigger + "<=>" + smaller, 1);
    _test.accept(smaller + "<=>" + bigger, -1);
    _test.accept(smaller + "<=>" + smaller, 0);
    _test.accept(bigger + "<=>" + bigger, 0);
  };

  @Test public void constComparisons() {
    comparisonTests.accept("-3", "7");
    comparisonTests.accept("-3L", "7L");
    comparisonTests.accept("-3.0D", "7.0D");
    comparisonTests.accept("-3.0", "7.0");

    comparisonTests.accept("-3", "7L");
    comparisonTests.accept("-3L", "7");
    comparisonTests.accept("-3", "7D");
    comparisonTests.accept("-3D", "7");
    comparisonTests.accept("-3L", "7D");
    comparisonTests.accept("-3D", "7L");

    comparisonTests.accept("-3", "7.0");
    comparisonTests.accept("-3.0", "7");
    comparisonTests.accept("-3L", "7.0");
    comparisonTests.accept("-3.0", "7L");
    comparisonTests.accept("-3.0D", "7.0");
    comparisonTests.accept("-3.0", "7.0D");

    comparisonTests.accept("'axcde'", "'azcde'");
    comparisonTests.accept("'axcde'", "'axcdef'");

    comparisonTests.accept("4*2", "-3*-7");
  }

  @Test public void compareOperator() {
    test("1 <=> null", 1);
    test("null <=> null", 0);
    test("null <=> 1", -1);
    test("'' <=> null", 1);
    test("null <=> ''", -1);
    test("0 <=> null", 1);
    test("null <=> 0", -1);
    test("1L <=> null", 1);
    test("null <=> 1L", -1);
    test("1D <=> null", 1);
    test("null <=> 1D", -1);
    test("1.0 <=> null", 1);
    test("null <=> 1.0", -1);

    test("def x = 1; def y = null; x <=> y", 1);
    test("def x = null; def y = null; x <=> y", 0);
    test("def x = null; def y = 1; x <=> y", -1);
    test("def x = ''; def y = null; x <=> y", 1);
    test("def x = null; def y = ''; x <=> y", -1);
    test("def x = 0; def y = null; x <=> y", 1);
    test("def x = null; def y = 0; x <=> y", -1);
    test("def x = 1L; def y = null; x <=> y", 1);
    test("def x = null; def y = 1L; x <=> y", -1);
    test("def x = 1D; def y = null; x <=> y", 1);
    test("def x = null; def y = 1D; x <=> y", -1);
    test("def x = 1.0; def y = null; x <=> y", 1);
    test("def x = null; def y = 1.0; x <=> y", -1);

    testError("[1,2,3] <=> [2,3,4]", "cannot compare");
    testError("[a:1] <=> [:]", "cannot compare");
    testError("[1,2,3] <=> 'xxx'", "cannot compare");
    testError("[1,2,3] <=> 'xxx'", "cannot compare");
  }

  @Test public void expressionComparisons() {
    //String init = "int i3=3; long l5=5L; double d7=7.0D; Decimal dec13=13.0; String sabc = 'abc';" +
    //              "def di3=3; def dl5=5L; def dd7=7.0D; def ddec13=13.0; def dsabc = 'abc';";
    comparisonTests.accept("-i3", "l5");
    comparisonTests.accept("-i3 * d7", "dec13");
    comparisonTests.accept("-i3 * l5", "dec13");
    comparisonTests.accept("-di3 * 5", "l5");
    comparisonTests.accept("-di3 * 5", "l5 * ddec13");

    comparisonTests.accept("sabc", "sabc + 'z'");
    comparisonTests.accept("sabc", "dsabc + 'z'");
    comparisonTests.accept("sabc * 2", "sabc * 3");
    comparisonTests.accept("dsabc * 2", "dsabc * 3");

    test("def x = 2L; 2 * x == 4", true);
    test("long x = 2L; 2 * x == 4", true);
  }

  @Test public void instanceOfTests() {
    test("true instanceof boolean", true);
    test("1 instanceof boolean", false);
    test("def x = true; x instanceof boolean", true);
    test("def x = true; (x && x || x) instanceof boolean", true);
    test("String x; x instanceof boolean", false);
    test("String x; x instanceof boolean || x instanceof String", true);
    test("def x = 'abc'; x instanceof boolean || x instanceof String", true);

    test("1 instanceof Map", false);
    test("1 instanceof List", false);
    test("1 instanceof boolean", false);
    test("1 instanceof String", false);
    test("1 instanceof int", true);
    test("1 instanceof long", false);
    test("1 instanceof double", false);
    test("1 instanceof Decimal", false);
    test("1L instanceof Map", false);
    test("1L instanceof List", false);
    test("1L instanceof boolean", false);
    test("1L instanceof String", false);
    test("1L instanceof int", false);
    test("1L instanceof long", true);
    test("1L instanceof double", false);
    test("1L instanceof Decimal", false);

    test("1D instanceof Map", false);
    test("1D instanceof List", false);
    test("1D instanceof boolean", false);
    test("1D instanceof String", false);
    test("1D instanceof int", false);
    test("1D instanceof long", false);
    test("1D instanceof double", true);
    test("1D instanceof Decimal", false);

    test("1.0 instanceof Map", false);
    test("1.0 instanceof List", false);
    test("1.0 instanceof boolean", false);
    test("1.0 instanceof String", false);
    test("1.0 instanceof int", false);
    test("1.0 instanceof long", false);
    test("1.0 instanceof double", false);
    test("1.0 instanceof Decimal", true);

    test("[] instanceof Map", false);
    test("[] instanceof List", true);
    test("[] instanceof boolean", false);
    test("[] instanceof String", false);
    test("[] instanceof int", false);
    test("[] instanceof long", false);
    test("[] instanceof double", false);
    test("[] instanceof Decimal", false);

    test("[:] instanceof Map", true);
    test("[:] instanceof List", false);
    test("[:] instanceof boolean", false);
    test("[:] instanceof String", false);
    test("[:] instanceof int", false);
    test("[:] instanceof long", false);
    test("[:] instanceof double", false);
    test("[:] instanceof Decimal", false);

    test("def x = 1 ; x instanceof Map", false);
    test("def x = 1 ; x instanceof List", false);
    test("def x = 1 ; x instanceof boolean", false);
    test("def x = 1 ; x instanceof String", false);
    test("def x = 1 ; x instanceof int", true);
    test("def x = 1 ; x instanceof long", false);
    test("def x = 1 ; x instanceof double", false);
    test("def x = 1 ; x instanceof Decimal", false);
    test("def x = 1L ; x instanceof Map", false);
    test("def x = 1L ; x instanceof List", false);
    test("def x = 1L ; x instanceof boolean", false);
    test("def x = 1L ; x instanceof String", false);
    test("def x = 1L ; x instanceof int", false);
    test("def x = 1L ; x instanceof long", true);
    test("def x = 1L ; x instanceof double", false);
    test("def x = 1L ; x instanceof Decimal", false);

    test("def x = 1D ; x instanceof Map", false);
    test("def x = 1D ; x instanceof List", false);
    test("def x = 1D ; x instanceof boolean", false);
    test("def x = 1D ; x instanceof String", false);
    test("def x = 1D ; x instanceof int", false);
    test("def x = 1D ; x instanceof long", false);
    test("def x = 1D ; x instanceof double", true);
    test("def x = 1D ; x instanceof Decimal", false);

    test("def x = 1.0 ; x instanceof Map", false);
    test("def x = 1.0 ; x instanceof List", false);
    test("def x = 1.0 ; x instanceof boolean", false);
    test("def x = 1.0 ; x instanceof String", false);
    test("def x = 1.0 ; x instanceof int", false);
    test("def x = 1.0 ; x instanceof long", false);
    test("def x = 1.0 ; x instanceof double", false);
    test("def x = 1.0 ; x instanceof Decimal", true);

    test("def x = [] ; x instanceof Map", false);
    test("def x = [] ; x instanceof List", true);
    test("def x = [] ; x instanceof boolean", false);
    test("def x = [] ; x instanceof String", false);
    test("def x = [] ; x instanceof int", false);
    test("def x = [] ; x instanceof long", false);
    test("def x = [] ; x instanceof double", false);
    test("def x = [] ; x instanceof Decimal", false);

    test("def x = [:] ; x instanceof Map", true);
    test("def x = [:] ; x instanceof List", false);
    test("def x = [:] ; x instanceof boolean", false);
    test("def x = [:] ; x instanceof String", false);
    test("def x = [:] ; x instanceof int", false);
    test("def x = [:] ; x instanceof long", false);
    test("def x = [:] ; x instanceof double", false);
    test("def x = [:] ; x instanceof Decimal", false);


    test("true !instanceof boolean", false);
    test("1 !instanceof boolean", true);
    test("def x = true; x !instanceof boolean", false);
    test("def x = true; (x && x || x) !instanceof boolean", false);
    test("String x; x !instanceof boolean", true);
    test("String x; x !instanceof boolean && x !instanceof String", false);
    test("def x = 'abc'; x !instanceof boolean && x !instanceof String", false);

    test("1 !instanceof Map", true);
    test("1 !instanceof List", true);
    test("1 !instanceof boolean", true);
    test("1 !instanceof String", true);
    test("1 !instanceof int", false);
    test("1 !instanceof long", true);
    test("1 !instanceof double", true);
    test("1 !instanceof Decimal", true);
    test("1L !instanceof Map", true);
    test("1L !instanceof List", true);
    test("1L !instanceof boolean", true);
    test("1L !instanceof String", true);
    test("1L !instanceof int", true);
    test("1L !instanceof long", false);
    test("1L !instanceof double", true);
    test("1L !instanceof Decimal", true);

    test("1D !instanceof Map", true);
    test("1D !instanceof List", true);
    test("1D !instanceof boolean", true);
    test("1D !instanceof String", true);
    test("1D !instanceof int", true);
    test("1D !instanceof long", true);
    test("1D !instanceof double", false);
    test("1D !instanceof Decimal", true);

    test("1.0 !instanceof Map", true);
    test("1.0 !instanceof List", true);
    test("1.0 !instanceof boolean", true);
    test("1.0 !instanceof String", true);
    test("1.0 !instanceof int", true);
    test("1.0 !instanceof long", true);
    test("1.0 !instanceof double", true);
    test("1.0 !instanceof Decimal", false);

    test("[] !instanceof Map", true);
    test("[] !instanceof List", false);
    test("[] !instanceof boolean", true);
    test("[] !instanceof String", true);
    test("[] !instanceof int", true);
    test("[] !instanceof long", true);
    test("[] !instanceof double", true);
    test("[] !instanceof Decimal", true);

    test("[:] !instanceof Map", false);
    test("[:] !instanceof List", true);
    test("[:] !instanceof boolean", true);
    test("[:] !instanceof String", true);
    test("[:] !instanceof int", true);
    test("[:] !instanceof long", true);
    test("[:] !instanceof double", true);
    test("[:] !instanceof Decimal", true);

    test("def x = 1 ; x !instanceof Map", true);
    test("def x = 1 ; x !instanceof List", true);
    test("def x = 1 ; x !instanceof boolean", true);
    test("def x = 1 ; x !instanceof String", true);
    test("def x = 1 ; x !instanceof int", false);
    test("def x = 1 ; x !instanceof long", true);
    test("def x = 1 ; x !instanceof double", true);
    test("def x = 1 ; x !instanceof Decimal", true);
    test("def x = 1L ; x !instanceof Map", true);
    test("def x = 1L ; x !instanceof List", true);
    test("def x = 1L ; x !instanceof boolean", true);
    test("def x = 1L ; x !instanceof String", true);
    test("def x = 1L ; x !instanceof int", true);
    test("def x = 1L ; x !instanceof long", false);
    test("def x = 1L ; x !instanceof double", true);
    test("def x = 1L ; x !instanceof Decimal", true);

    test("def x = 1D ; x !instanceof Map", true);
    test("def x = 1D ; x !instanceof List", true);
    test("def x = 1D ; x !instanceof boolean", true);
    test("def x = 1D ; x !instanceof String", true);
    test("def x = 1D ; x !instanceof int", true);
    test("def x = 1D ; x !instanceof long", true);
    test("def x = 1D ; x !instanceof double", false);
    test("def x = 1D ; x !instanceof Decimal", true);

    test("def x = 1.0 ; x !instanceof Map", true);
    test("def x = 1.0 ; x !instanceof List", true);
    test("def x = 1.0 ; x !instanceof boolean", true);
    test("def x = 1.0 ; x !instanceof String", true);
    test("def x = 1.0 ; x !instanceof int", true);
    test("def x = 1.0 ; x !instanceof long", true);
    test("def x = 1.0 ; x !instanceof double", true);
    test("def x = 1.0 ; x !instanceof Decimal", false);

    test("def x = [] ; x !instanceof Map", true);
    test("def x = [] ; x !instanceof List", false);
    test("def x = [] ; x !instanceof boolean", true);
    test("def x = [] ; x !instanceof String", true);
    test("def x = [] ; x !instanceof int", true);
    test("def x = [] ; x !instanceof long", true);
    test("def x = [] ; x !instanceof double", true);
    test("def x = [] ; x !instanceof Decimal", true);

    test("def x = [:] ; x !instanceof Map", false);
    test("def x = [:] ; x !instanceof List", true);
    test("def x = [:] ; x !instanceof boolean", true);
    test("def x = [:] ; x !instanceof String", true);
    test("def x = [:] ; x !instanceof int", true);
    test("def x = [:] ; x !instanceof long", true);
    test("def x = [:] ; x !instanceof double", true);
    test("def x = [:] ; x !instanceof Decimal", true);

    testError("def x = 'int'; x instanceof x", "unexpected token 'x'");
    testError("def x = 'int'; x !instanceof x", "unexpected token 'x'");

    test("def x = [a:[1,2]]; x.a instanceof List", true);
    test("def x = [a:[1,2]]; x.a !instanceof Map", true);
    test("def x = [a:[1,2]]; x.a[0] instanceof int", true);
    test("def x = [a:[1,2]]; x.a[0] !instanceof Map", true);

    test("null instanceof Map", false);
    test("null instanceof List", false);
    test("null instanceof boolean", false);
    test("null instanceof String", false);
    test("null instanceof int", false);
    test("null instanceof long", false);
    test("null instanceof double", false);
    test("null instanceof Decimal", false);
    test("null !instanceof Map", true);
    test("null !instanceof List", true);
    test("null !instanceof boolean", true);
    test("null !instanceof String", true);
    test("null !instanceof int", true);
    test("null !instanceof long", true);
    test("null !instanceof double", true);
    test("null !instanceof Decimal", true);

    test("def x = null; x instanceof Map", false);
    test("def x = null; x instanceof List", false);
    test("def x = null; x instanceof boolean", false);
    test("def x = null; x instanceof String", false);
    test("def x = null; x instanceof int", false);
    test("def x = null; x instanceof long", false);
    test("def x = null; x instanceof double", false);
    test("def x = null; x instanceof Decimal", false);
    test("def x = null; x !instanceof Map", true);
    test("def x = null; x !instanceof List", true);
    test("def x = null; x !instanceof boolean", true);
    test("def x = null; x !instanceof String", true);
    test("def x = null; x !instanceof int", true);
    test("def x = null; x !instanceof long", true);
    test("def x = null; x !instanceof double", true);
    test("def x = null; x !instanceof Decimal", true);
  }

  @Test public void typeCasts() {
    test("(int)1L", 1);
    test("(int)1L instanceof int", true);
    testError("(String)1", "cannot cast");
    testError("def x = 1; (String)x", "cannot convert");
    test("(String)null", null);
    test("(Map)null", null);
    test("(List)null", null);
    test("def x = null; (Map)x", null);
    test("def x = null; (List)x", null);
    testError("(int)null", "cannot convert null");
    testError("(long)null", "cannot convert null");
    testError("(double)null", "cannot convert null");
    testError("(Decimal)null", "null value for decimal");
    testError("def x; (int)x", "cannot convert null");
    testError("def x; (long)x", "cannot convert null");
    testError("def x; (double)x", "cannot convert null");
    testError("def x; (Decimal)x", "null value for decimal");

    test("(int)'a'", 97);
    test("def x = 'a'; (int)x", 97);
    testError("(int)'abc'", "string with multiple chars cannot be cast");
    testError("def x = 'abc'; (int)x", "string with multiple chars cannot be cast");
    testError("(int)''", "empty string cannot be cast");
    testError("String s; (int)s", "empty string cannot be cast");
    testError("def x = ''; (int)x", "empty string cannot be cast");

    testError("(Map)1", "cannot cast from int to map");
    testError("(List)1", "cannot cast from int to list");
    testError("int x = 1; (Map)x", "cannot cast from");
    testError("int x = 1; (List)x", "cannot cast from");
    testError("def x = 1; (Map)x", "cannot be cast");
    testError("def x = 1; (List)x", "cannot be cast");
    testError("def x = { it }; (Map)x", "cannot be cast");
    testError("def x = { it }; (List)x", "cannot be cast");
    testError("def x(){ 1 }; (Map)x", "cannot cast from");
    testError("def x(){ 1 }; (List)x", "cannot cast from");
    testError("def x = { it }; (int)x", "cannot be cast");
    testError("def x = { it }; (long)x", "cannot be cast");
    testError("def x = { it }; (double)x", "cannot be cast");
    testError("def x = { it }; (Decimal)x", "cannot be cast");
    testError("def x = { it }; (String)x", "cannot convert");
    test("def x = { it }; '' + x", "MethodHandle(Continuation,String,int,Object[])Object");
    testError("def x(){ 1 }; (int)x", "cannot cast from");
    testError("def x(){ 1 }; (long)x", "cannot cast from");
    testError("def x(){ 1 }; (double)x", "cannot cast from");
    testError("def x(){ 1 }; (Decimal)x", "cannot cast from");

    testError("def x(){ 1 }; (String)x", "cannot cast from");

    test("(int)1", 1);
    test("int x = 1; (int)x", 1);
    test("(long)1", 1L);
    test("int x = 1; (long)x", 1L);
    test("(double)1", 1D);
    test("int x = 1; (double)x", 1D);
    test("(Decimal)1", "#1");
    test("int x = 1; (Decimal)x", "#1");

    test("(int)1L", 1);
    test("long x = 1L; (int)x", 1);
    test("(long)1L", 1L);
    test("long x = 1L; (long)x", 1L);
    test("(double)1L", 1D);
    test("long x = 1L; (double)x", 1D);
    test("(Decimal)1L", "#1");
    test("long x = 1L; (Decimal)x", "#1");

    test("(int)1D", 1);
    test("double x = 1D; (int)x", 1);
    test("(long)1D", 1L);
    test("double x = 1D; (long)x", 1L);
    test("(double)1D", 1D);
    test("double x = 1D; (double)x", 1D);
    test("(Decimal)1D", "#1.0");
    test("double x = 1D; (Decimal)x", "#1.0");

    test("(int)1.0", 1);
    test("Decimal x = 1.0; (int)x", 1);
    test("(long)1.0", 1L);
    test("Decimal x = 1.0; (long)x", 1L);
    test("(double)1.0", 1D);
    test("Decimal x = 1.0; (double)x", 1D);
    test("(Decimal)1.0", "#1.0");
    test("Decimal x = 1.0; (Decimal)x", "#1.0");
  }

  @Test public void asChar() {
    test("97.asChar()", "a");
    test("def x = 97; x.asChar()", "a");
    test("def x = 97; def f = x.asChar; f()", "a");
    test("def x = ((int)'X').asChar(); x", "X");
    test("def f = ((int)'X').asChar; f()", "X");
    testError("'a'.asChar()", "no such method");
    testError("def x = 'a'; x.asChar()", "no such method");
    testError("1L.asChar()", "no such method");
    testError("def x = 1L; x.asChar()", "no such method");
  }

  @Test public void asType() {
    testError("true as int", "cannot coerce");
    testError("(int)true", "cannot cast");
    test("1L as int", 1);
    test("(1L as int) instanceof int", true);
    test("1 as String", "1");
    test("def x = 1; x as String", "1");
    testError("null as int", "null value");
    testError("null as long", "null value");
    testError("null as double", "null value");
    testError("null as Decimal", "null value");
    test("null as String", null);
    test("null as Map", null);
    test("null as List", null);
    testError("def x = null; x as int", "null value");
    testError("def x = null; x as long", "null value");
    testError("def x = null; x as double", "null value");
    testError("def x = null; x as Decimal", "null value");
    test("def x = null; x as String", null);
    test("def x = null; x as Map", null);
    test("def x = null; x as List", null);
    testError("def x; x as int", "null value");
    testError("def x; x as long", "null value");
    testError("def x; x as double", "null value");
    testError("def x; x as Decimal", "null value");

    testError("1 as Map", "cannot coerce");
    testError("1 as List", "cannot coerce");
    testError("int x = 1; x as Map", "cannot coerce");
    testError("int x = 1; x as List", "cannot coerce");
    testError("def x = 1; x as Map", "cannot coerce");
    testError("def x = 1; x as List", "cannot coerce");
    testError("def x = { it }; x as Map", "cannot coerce");
    testError("def x = { it }; x as List", "cannot coerce");
    testError("def x(){ 1 }; x as Map", "cannot coerce");
    testError("def x(){ 1 }; x as List", "cannot coerce");
    testError("def x = { it }; x as int", "cannot coerce");
    testError("def x = { it }; x as long", "cannot coerce");
    testError("def x = { it }; x as double", "cannot coerce");
    testError("def x = { it }; x as Decimal", "cannot coerce");
    test("def x = { it }; x as String", "MethodHandle(Continuation,String,int,Object[])Object");
    testError("def x(){ 1 }; x as int", "cannot coerce");
    testError("def x(){ 1 }; x as long", "cannot coerce");
    testError("def x(){ 1 }; x as double", "cannot coerce");
    testError("def x(){ 1 }; x as Decimal", "cannot coerce");

    test("def x(){ 1 }; x as String", "MethodHandle(Continuation,String,int,Object[])Object");

    test("1 as int", 1);
    test("int x = 1; x as int", 1);
    test("1 as long", 1L);
    test("int x = 1; x as long", 1L);
    test("1 as double", 1D);
    test("int x = 1; x as double", 1D);
    test("1 as Decimal", "#1");
    test("int x = 1; x as Decimal", "#1");

    test("1L as int", 1);
    test("long x = 1L; x as int", 1);
    test("1L as long", 1L);
    test("long x = 1L; x as long", 1L);
    test("1L as double", 1D);
    test("long x = 1L; x as double", 1D);
    test("1L as Decimal", "#1");
    test("long x = 1L; x as Decimal", "#1");

    test("1D as int", 1);
    test("double x = 1D; x as int", 1);
    test("1D as long", 1L);
    test("double x = 1D; x as long", 1L);
    test("1D as double", 1D);
    test("double x = 1D; x as double", 1D);
    test("1D as Decimal", "#1.0");
    test("double x = 1D; x as Decimal", "#1.0");

    test("1.0 as int", 1);
    test("Decimal x = 1.0; x as int", 1);
    test("1.0 as long", 1L);
    test("Decimal x = 1.0; x as long", 1L);
    test("1.0 as double", 1D);
    test("Decimal x = 1.0; x as double", 1D);
    test("1.0 as Decimal", "#1.0");
    test("Decimal x = 1.0; x as Decimal", "#1.0");

    test("def x = 1; x as int", 1);
    test("def x = 1; x as long", 1L);
    test("def x = 1; x as double", 1D);
    test("def x = 1; x as Decimal", "#1");

    test("def x = 1L; x as int", 1);
    test("def x = 1L; x as long", 1L);
    test("def x = 1L; x as double", 1D);
    test("def x = 1L; x as Decimal", "#1");

    test("def x = 1D; x as int", 1);
    test("def x = 1D; x as long", 1L);
    test("def x = 1D; x as double", 1D);
    test("def x = 1D; x as Decimal", "#1.0");

    test("def x = 1.0; x as int", 1);
    test("def x = 1.0; x as long", 1L);
    test("def x = 1.0; x as double", 1D);
    test("def x = 1.0; x as Decimal", "#1.0");

    test("1 as String", "1");
    test("1.0 as String", "1.0");
    test("1D as String", "1.0");
    test("1L as String", "1");
    test("[] as String", "[]");
    test("[1,2,3] as String", "[1, 2, 3]");
    test("[:] as String", "[:]");
    test("[a:1,b:2] as String", "[a:1, b:2]");
    test("def x = 1; x as String", "1");
    test("def x = 1.0; x as String", "1.0");
    test("def x = 1D; x as String", "1.0");
    test("def x = 1L; x as String", "1");
    test("def x = []; x as String", "[]");
    test("def x = [1,2,3]; x as String", "[1, 2, 3]");
    test("def x = [:]; x as String", "[:]");
    test("def x = [a:1,b:2]; x as String", "[a:1, b:2]");
    test("def x = 1; x as String as int", 1);
    test("def x = 1; (x as String as int) + 2", 3);
    testError("def x = 1; x as String as int + 2", "unexpected token '+'");
    testError("def x = 1.0; x as String as int", "not a valid int");
    test("def x = 1.0; x as String as double", 1.0D);
    test("def x = 1.0; x as String as Decimal", "#1.0");
    test("def x = 1D; x as String as double", 1.0D);
    test("def x = 1L; x as String as long", 1L);

    test("[:] as List", List.of());
    test("[a:1] as List", List.of(List.of("a",1)));
    test("[a:1,b:2] as List", List.of(List.of("a",1), List.of("b",2)));
    test("[a:1,b:2] as List as Map", Map.of("a",1,"b",2));
    testError("'abc' as Map", "cannot coerce");
    test("'abc' as List", List.of("a","b","c"));
    test("'' as List", List.of());
    test("('abc' as List).join()", "abc");
    testError("'123456789123456789123456789' as long", "not a valid long");
    testError("'89.123.123' as double", "not a valid double");
  }

  @Test public void inOperator() {
    test("'b' in 'abc'", true);
    test("'x' in 'abc'", false);
    test("'b' !in 'abc'", false);
    test("'x' !in 'abc'", true);
    test("def x = 'b'; def y = 'abc'; x in y", true);
    test("def x = 'x'; def y = 'abc'; x in y", false);
    test("def x = 'b'; def y = 'abc'; x !in y", false);
    test("def x = 'x'; def y = 'abc'; x !in y", true);
    testError("1 in 'abc'", "expecting string for left-hand side");
    testError("'1' in 1", "int is not a valid type for right-hand side of 'in'");
    testError("'1' !in 1", "int is not a valid type for right-hand side of '!in'");
    testError("def x = 1; def y = 'abc'; x in y", "expecting string for left-hand side");
    testError("def x = '1'; def y = 1; x in y", "expecting String/List/Map for right-hand side");
    testError("def x = '1'; def y = 1; x !in y", "expecting String/List/Map for right-hand side");
    testError("null in 'abc'", "expecting string for left-hand side");
    test("2 in [1,2,3]", true);
    test("2 !in [1,2,3]", false);
    test("1 in []", false);
    test("1 !in []", true);
    test("[] in [[]]", true);
    test("2 in [1,2,3,2,4]", true);
    test("def f = { it*it}; f in ['a',f]", true);
    test("[a:1,b:[1,2,3]] in [1,2,[a:1,b:[1,2,3]],3]", true);
    test("[] !in [[]]", false);
    test("2 !in [1,2,3,2,4]", false);
    test("def f = { it*it}; f !in ['a',f]", false);
    test("[a:1,b:[1,2,3]] !in [1,2,[a:1,b:[1,2,3]],3]", false);
    test("def x = 2 ; def y = [1,2,3]; x in y", true);
    test("def x = 2 ; def y = [1,2,3]; x !in y", false);
    test("def x = 1 ; def y = []; x in y", false);
    test("def x = 1 ; def y = []; x !in y", true);
    test("def x = [] ; def y = [[]]; x in y", true);
    test("def x = 2 ; def y = [1,2,3,2,4]; x in y", true);
    test("def f = { it*it}; f ; def y = ['a',f]; f in y", true);
    test("def x = [a:1,b:[1,2,3]] ; def y = [1,2,[a:1,b:[1,2,3]],3]; x in y", true);
    test("def x = [] ; def y = [[]]; x !in y", false);
    test("def x = 2 ; def y = [1,2,3,2,4]; x !in y", false);
    test("def f = { it*it}; f ; def y = ['a',f]; f !in y", false);
    test("def x = [a:1,b:[1,2,3]] ; def y = [1,2,[a:1,b:[1,2,3]],3]; x !in y", false);
    test("'a' in [a:1]", true);
    test("def x = 'a'; def y = [a:1]; x in y", true);
    test("'a' !in [a:1]", false);
    test("def x = 'a'; def y = [a:1]; x !in y", false);
    test("'a' in [:]", false);
    test("'a' !in [:]", true);
    test("'b' in [a:1]", false);
    test("'b' !in [a:1]", true);
    test("'b' in [a:1,b:[1,2,3]]", true);
    test("'b' !in [a:1,b:[1,2,3]]", false);
    test("[] in [:]", false);
    test("def x = 'a'; def y = [:]; x in y", false);
    test("def x = 'a' ; def y = [:]; x !in y", true);
    test("def x = 'b' ; def y = [a:1]; x in y", false);
    test("def x = 'b' ; def y = [a:1]; x !in y", true);
    test("def x = 'b' ; def y = [a:1,b:[1,2,3]]; x in y", true);
    test("def x = 'b' ; def y = [a:1,b:[1,2,3]]; x !in y", false);
    test("def x = [] ; def y = [:]; x in y", false);
    test("'a' in [a:1,b:2].map{ a,b -> a }", true);
    test("'a' !in [a:1,b:2].map{ a,b -> a }", false);
    test("def x = 'a'; x in [a:1,b:2].map{ a,b -> a }", true);
    test("def x = 'a'; x !in [a:1,b:2].map{ a,b -> a }", false);
  }

  @Test public void whileLoops() {
    test("int i = 0; while (i < 10) i++; i", 10);
    test("int i = 0; int sum = 0; while (i < 10) sum += i++; sum", 45);
    test("int i = 0; int sum = 0; while (i < 10) { sum += i; i++ }; sum", 45);
    test("int i = 0; int sum = 0; while (i < 10) i++", null);
    testError("while (false) i++;", "unknown variable 'i'");
    test("int i = 1; while (false) i++; i", 1);
    test("int i = 1; while (false) ;", null);
    test("int i = 1; while (++i < 10); i", 10);
  }

  @Test public void forLoops() {
    test("int sum = 0; for (int i = 0; i < 10; i++) sum += i; sum", 45);
    testError("int sum = 0; for (int i = 0; i < 10; i++) sum += i; i", "unknown variable");
    test("int sum = 0; for (int i = 0,j=10; i < 10; i++,j--) sum += i + j; sum", 100);
    test("int sum = 0; int i,j; for (sum = 20, i = 0,j=10; i < 10; i++,j--) sum += i + j; sum", 120);
    test("int sum = 0; int i,j; for (sum = 20, i = 0,j=10; i < 10; i++,j--) { sum += i + j; def i = 3; i++ }; sum", 120);
  }

  @Test public void breakContinue() {
    testError("break", "break must be within");
    testError("continue", "continue must be within");
    testError("if (true) { break }", "break must be within");
    testError("if (true) { continue }", "continue must be within");
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5) continue; sum += i }; sum", 15);
    test("def sum = 0; def i = 0.0D; while (i++ < 10) { if (i > 5) continue; sum += i }; sum", 15.0D);
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5) continue; sum = sum * 1 + i }; sum", 15);
    test("def sum = 0; def i = 0.0D; while (i++ < 10) { if (i > 5) continue; sum = sum * 1 + i }; sum", 15.0D);
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5 && i < 7) continue; sum += i }; sum", 49);
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5 && i < 7) { if (true) continue } ; sum += i }; sum", 49);
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5) break; sum += i }; sum", 15);
    test("int sum = 0; double i = 0; while (i++ < 10) { if (i > 5) { if (true) break}; sum += i }; sum", 15);
    test("int sum = 0; for (double i = 0; i < 10; i++) { if (i > 5) continue; sum += i }; sum", 15);
    test("int sum = 0; for (double i = 0; i < 10; i++) { if (i > 5 && i < 7) continue; sum += i }; sum", 39);
    test("int sum = 0; for (double i = 0; i < 10; i++) { if (i > 5) break; sum += i }; sum", 15);
    test("int sum = 0; for (int i = 0; i < 4; i++) { for (int j = 0; j < 4; j++) { sum += i * j } }; sum", 36);
    test("int sum = 0; for (int i = 0; i < 4; i++) { for (int j = 0; j < 4; j++) { if (j == 3) break; sum += i * j } }; sum", 18);
    test("int sum = 0; for (int i = 0; i < 4; i++) { for (int j = 0; j < 4; j++) { if (j == 3) continue; sum += i * j } }; sum", 18);
    test("int x; for (int i = 0; i < 10; i++) { i < 5 and continue or x += i }; x", 35);
    test("int x; for (int i = 0; i < 10; i++) { i > 5 and break or x += i }; x", 15);
    test("int sum = 0; double i = 0; while (i++ < 10) { i > 5 and continue; sum += i }; sum", 15);
    test("int sum = 0; double i = 0; while (i++ < 10) { i > 5 and sleep(0,continue); sum += i }; sum", 15);
    test("def f(x){x}; int sum = 0; double i = 0; while (i++ < 10) { i > 5 and f(continue); sum += i }; sum", 15);
  }

  @Test public void simpleFunctions() {
    testError("def f; f()", "null value for function");
    test("def f() {return}; f()", null);
    test("int f(int x) { x * x }; f(2)", 4);
    test("def f(def x) { x * x }; f(2)", 4);
    test("def f(x) { x * x }; f(2)", 4);
    test("int f(x) { if (x == 1) 1 else x * f(x - 1) }; f(3)", 6);
    test("def f(x) { if (x == 1) 1 else x * f(x - 1) }; f(3) + f(4)", 30);
    testError("def f(int x) { def x = 1; x++ }", "clashes with previously declared variable");

    test("int f(x) { { def x = 3; return x } }; f(1)", 3);
    test("int f(x) { { def x = 3; x } }; f(1)", 3);
    test("int f() { 3L }; f()", 3);
    test("int f() { 3.0 }; f()", 3);
    test("int f() { 3.0D }; f()", 3);
    test("long f() { 3 }; f()", 3L);
    test("long f() { 3.0 }; f()", 3L);
    test("long f() { 3.0D }; f()", 3L);
    test("double f() { 3 }; f()", 3D);
    test("Decimal f() { 3 }; f()", "#3");

    test("def f(x,y) { x + y }; f(1,2)", 3);
    test("def f(x,y) { x + y }; f(1L,2D)", 3D);
    test("def f(x,y) { x + y }; f(1L,2.0)", "#3.0");
    test("int f(x,y) { x + y }; f(1L,2.0)", 3);
    test("int f(long x, double y, Decimal z) { ++x + ++y + ++z }; f(1,2,3)", 9);

    test("def f(x) { x + x }; f('abc')", "abcabc");
    test("def f(x) { x * 2 }; f('abc')", "abcabc");
    test("String f(String x, int y) { x * y }; f('abc', 2)", "abcabc");

    test("def f(x) { def g(x) { x * x }; g(x) }; f(3)", 9);
    test("def f(x) { def g(x) { x * x }; g(x) }; def g(x) { f(x) }; g(3)", 9);
    test("def f(x) { def g(x) { def f(x) {x+x}; f(x) }; g(x) * g(x) }; f(3)", 36);
    testError("def f(String x) { x + x }; f(1)", "cannot convert");
    test("def f(def x) { '' + x + x }; f(1)", "11");
    testError("def f(String x) { x }; f([1,2,3])", "cannot convert");
    test("def f(def x) { '' + x }; f([1,2,3])", "[1, 2, 3]");
    testError("def f(String x) { x }; f([a:1,b:2])", "cannot convert");
    test("def f(def x) { '' + x }; f([a:1,b:2])", "[a:1, b:2]");

    testError("null()", "null value for function");
    testError("3()", "cannot be called");
    testError("'abc'()", "cannot be called");
    testError("1D()", "cannot be called");
    testError("(3.0 + 2.0)()", "cannot be called");
    testError("def f(){1}; def g(){2}; f=g; f()", "cannot assign to function");
    testError("def f(){1}; def g(){2}; f ?= g; f()", "cannot assign to function");
    testError("def f(){1}; def g(){2}; f += g; f()", "non-numeric operand");

    test("def f(x) { if (x == 1) 1 else x + f(x-1) }; f(4)", 10);
    test("def f() { int i = 1; return {++i}}; def x=f(); def y=f(); [x()+x()+x(),y()]", List.of(9,2));
  }

  @Test public void functionsAsValues() {
    test("def f(x) { x + x }; def g = f; g(2)", 4);
    test("def f(x) { x + x }; def g = f; g('abc')", "abcabc");
    testError("def f(x) { x + x }; def g = f; g()", "missing mandatory arguments");
    test("def f() { def g() { 3 } }; def h = f(); h()", 3);
    test("def f() { def g(){ def h(x){x*x} } }; f()()(3)", 9);
    test("def f() { def g(){ def h(x){x*x} }; [a:g] }; f().a()(3)", 9);
    test("def x = [:]; int f(x){x*x}; x.a = f; (x.a)(2)", 4);
    test("def x = [:]; int f(x){x*x}; x.a = f; x.a(2)", 4);
    testError("def f(int x) { x*x }; def g = f; g('abc')", "cannot be cast");
    testError("def f(String x) { x + x }; def g = f; g(1)", "cannot convert");
    test("def x = [1,2,3]; def f = x.map; f{it+it}", List.of(2,4,6));
    test("def x = [1,2,3]; def f = x.map; x = [4,5]; f{it+it}", List.of(2,4,6));
  }

  @Test public void functionsForwardReference() {
    test("def x = f(2); def f(z){3*z}; x", 6);
    test("def y=3; def x=f(2); def f(z){y*z}; x", 6);
    test("{ def y=3; def x=f(2); def f(z){y*z}; x }", 6);
    test("def f(x) { 2 * g(x) }; def g(x) { x * x }; f(3)", 18);
    testError("def y = 3; def f(x) { 2 * g(x) }; def z = f(3); def g(x) { x * y }; z", "closed over variable y");
    test("def f(x) { if (x==1) x else 2 * g(x) }; def g(x) { x + f(x-1) }; f(3)", 18);
    test("def f(x) { g(x) }; def g(x) { x }; f(3)", 3);
    testError("def x = f(2); def y = 3; def f(z){y*z}; x", "closes over variable y not yet declared");
    testError("{ def x = f(2); def y = 3; def f(z){y*z}; x }", "closes over variable y not yet declared");
    testError("def f(x) { g(x) }; def y = 2; def g(x) { x + y}; f(3)", "closes over variable y not yet declared");
    testError("def f(x){def h=g; h(x)}; def g(x){x*x}; f(2)", "closes over variable g that has not yet been initialised");
  }

  @Test public void functionsWithOptionalArgs() {
    test("def f(int x = 5) { x * x }; f()", 25);
    test("def f(int x = 5) { x * x }; f(3)", 9);
    test("def f(int x = 5) { x * x }; def g = f; g()", 25);
    test("def f(int x = 5) { x * x }; def g = f; g(3)", 9);
    test("def f(int x = 5) { x * x }; var g = f; g()", 25);
    test("def f(int x = 5) { x * x }; var g = f; g(3)", 9);
    test("int f(int x = 5) { x * x }; f()", 25);
    test("int f(int x = 5) { x * x }; f(3)", 9);
    test("int f(int x = 5) { x * x }; var g = f; g()", 25);
    test("int f(int x = 5) { x * x }; var g = f; g(3)", 9);
    testError("def f(int x = 5) { x * x }; def g = f; g(1,2)", "too many arguments");
    testError("Decimal f(long x,int y=5,def z){x+y+z}; f(1)", "missing mandatory arguments");
    test("Decimal f(long x,int y=5,def z){x+y+z}; def g=f; g(1,2,3)", "#6");
    testError("Decimal f(long x,int y=5,def z){x+y+z}; def g=f; g(1)", "missing mandatory arguments");
    test("String f(x='abc') { x + x }; f()", "abcabc");
    test("String f(x=\"a${'b'+'c'}\") { x + x }; f()", "abcabc");
    test("String f(x=\"a${'b'+'c'}\") { x + x }; f('x')", "xx");
    test("int f(x = f(1)) { if (x == 1) 4 else x + f(x-1) }; f()", 13);
    test("int f(x = f(1)) { if (x == 1) 4 else x + f(x-1) }; f(2)", 6);
    test("def f(int x,int y=x+1) { x + y }; f(2)", 5);
    test("def f(int x,int y=++x+1) { x + y }; f(2)", 7);
    test("def f(int x,int y=++x+1, int z=x+1) { x + y + z }; f(2)", 11);
    test("def f(x,y=x+1) { x + y }; f(2)", 5);
    testError("def f(x,y=z+1,z=2) { x + y }; f(2)", "reference to unknown variable");
    test("def f(int x = f(1)) { if (x == 1) 9 else x }; f(3)", 3);
    test("def f(int x = f(1)) { if (x == 1) 9 else x }; f()", 9);
    test("def g(x){def y=g; f(x)}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    testError("def g(x){f(x)}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", "requires passing closed over variable g");
    test("def f(x,a=f){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    testError("def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; f(2)", "requires passing closed over variable h");
    test("def f(String x, int y=1, var z='abc') { x + y + z }; f('z')", "z1abc");
  }

  @Test public void passingArgsAsList() {
    test("def x = f([]); def f(){3}; x", 3);
    test("def f(){3}; f([])", 3);
    test("def f = { -> 3}; f([])", 3);
    test("def f = { -> 3}; def a = []; f(a)", 3);
    test("def f = {3}; f([])", 3);
    test("def f(){3}; def g = f; g([])", 3);
    testError("def f(){3}; f([4])", "too many arguments");
    testError("def f(){3}; def g = f; g([4])", "too many arguments");
    testError("def f(int x){x}; f([3])", "cannot convert");
    testError("def f = { int x -> x}; f([3])", "cannot be cast");
    testError("def f(int x){x}; def g = f; g([3])", "cannot be cast");
    testError("def f(int x){x}; def a = [3]; f(a)", "cannot be cast");
    testError("def f(int x, int y=0){x+y}; def a = [3]; f(a)", "cannot be cast");
    test("def f(int x,int y){x+y}; def a = [3,2]; f(a)", 5);
    test("def f(int x,int y){x+y}; def a = [3,2]; def g = f; g(a)", 5);
    testError("def f(List x, int y) { x + y }; f([1,2])", "cannot be cast to List");
    testError("def f = { List x, int y -> x + y }; f([1,2])", "cannot be cast to List");
    testError("def f = { List x, int y -> x + y }; def a = [1,2]; f(a)", "cannot be cast to List");
    testError("def f(List x, int y) { x + y }; def a = [1,2]; f(a)", "cannot be cast to List");
    testError("def f(List x, int y) { x + y }; def a = [1,2]; def g = f; g(a)", "cannot be cast to List");
    testError("def f(List x, int y) { x + y }; f([1,2])", "cannot be cast to List");
    testError("def f(int x, int y, int z) { x + y }; f(1,2)", "missing mandatory argument");
    testError("def f(int x, int y, int z) { x + y }; f([1,2])", "missing mandatory argument");
    testError("def f(List x, int y) { x + y }; def a = [[1,2]]; def g = f; g(a)", "missing mandatory arguments");
    test("def f(List x, int y) { x + y }; f([[1,2],3])", List.of(1,2,3));
    test("def f = { List x, int y -> x + y }; f([[1,2],3])", List.of(1,2,3));
    test("def f = { List x, int y -> x + y }; def a = [[1,2],3]; f(a)", List.of(1,2,3));
    test("def f(List x, int y) { x + y }; def a = [[1,2],3]; f(a)", List.of(1,2,3));
    test("def f(List x, int y) { x + y }; def a = [[1,2],3]; def g = f; g(a)", List.of(1,2,3));
    test("def f(List x, int y = 4) { x + y }; f([[1,2],3])", List.of(List.of(1,2),3,4));
    test("def f = {List x, int y = 4 -> x + y }; f([[1,2],3])", List.of(List.of(1,2),3,4));
    test("def f = {List x, int y = 4 -> x + y }; def a = [[1,2],3]; f(a)", List.of(List.of(1,2),3,4));
    test("def f(List x, int y = 4) { x + y }; def a = [[1,2],3]; f(a)", List.of(List.of(1,2),3,4));
    test("def f(List x, int y = 4) { x + y }; def a = [[1,2],3]; def g = f; g(a)", List.of(List.of(1,2),3,4));
    test("def f(int x, int y) { x + y }; f([1,2])", 3);
    test("def f(int x, int y) { x + y }; def g = f; g([1,2])", 3);
    testError("def f = {int x, int y = 4 -> x + y }; f([1,2])", "cannot be cast to int");
    test("def f = {int x, int y -> x + y }; f([1,2])", 3);
    test("def f = {int x, int y -> x + y }; def g = f; g([1,2])", 3);
    testError("def f = {int x, int y = 4 -> x + y }; def a = [1,2]; f(a)", "cannot be cast to int");
    test("def f = {int x = 3, int y = 4 -> x + y }; def a = [1,2]; f(a)", 3);
    test("def f = {int x = 3, int y = 4 -> x + y }; def a = [1,2]; def g = f; g(a)", 3);
    testError("def f(int x, int y = 4) { x + y }; def a = [1,2]; f(a)", "cannot be cast to int");
    test("def f(int x = 3, int y = 4) { x + y }; def a = [1,2]; f(a)", 3);
    test("def f(int x = 3, int y = 4) { x + y }; def a = [1,2]; def g = f; g(a)", 3);
    test("def f(int x = 3, int y = 4) { x + y }; def a = [1D,2D]; def g = f; g(a)", 3);
    test("def f(int x = 3, int y = 4) { x + y }; def a = [1.0,2L]; def g = f; g(a)", 3);

    test("def f(long t, def x) { sleep(t,x) }; f([1,2])", 2);
    testError("def f(long t, def x) { sleep(t,x) }; f(['1',2])", "cannot be cast to number");
    test("def f(long t, def x) { sleep(t,x) }; def a = [1,2]; f(a)", 2);
    testError("def f(long t, def x) { sleep(t,x) }; def a = ['1',2]; f(a)", "cannot be cast to number");
    test("def f(long t, def x) { sleep(t,x) }; [[1,2],[3,4]].map{ a,b -> f([a,b]) }", List.of(2,4));
    test("def f(long t, def x) { sleep(t,x) }; [a:2,b:4].map{ a,b -> f([b,a]) }", List.of("a","b"));
    test("def f(long t, def x) { sleep(t,x) }; def x = [[1,2],[3,4]]; x.map{ a,b -> f([a,b]) }", List.of(2,4));
    test("def f(long t, def x) { sleep(t,x) }; def x = [a:2,b:4]; x.map([{ a,b -> f([b,a]) }])", List.of("a","b"));
    test("def f(long t, def x) { sleep(t,x) }; def g = [[1,2],[3,4]].map; g([{ a,b -> f([a,b]) }])", List.of(2,4));
    test("def f(long t, def x) { sleep(t,x) }; def g = [a:2,b:4].map; g([{ a,b -> f([b,a]) }])", List.of("a","b"));
    test("def f(long t, def x) { sleep(t,x) }; def x = [[1,2],[3,4]]; def g = x.map; g([{ a,b -> f([a,b]) }])", List.of(2,4));
    test("def f(long t, def x) { sleep(t,x) }; def x = [a:2,b:4]; def g = x.map; g([{ a,b -> f([b,a]) }])", List.of("a","b"));
    test("def a = [1,2,3]; def f(x,y=7,z) { x + y + z }; f(1,2,3) + f(a)", 12);
    test("def a = [1,2,3]; def f(x,y=7,z=8) { x + y + z }; f(a)", List.of(1,2,3,7,8));
    test("def f(String x, int y) { x + y }; def a = ['x',2]; f(a)", "x2");
    test("def f = {String x, int y -> x + y }; def a = ['x',2]; f(a)", "x2");
    testError("def f = {String x, int y -> x + y }; def a = [2,'x']; f(a)", "cannot convert object of type int to string");
    test("def f(x,y,z) { x + y + z }; f(1,2,3) + f([1,2,3])", 12);
    test("def f = { x,y,z -> x + y + z }; f(1,2,3) + f([1,2,3])", 12);
    test("def f = { x,y,z -> x + y + z }; def a = [1,2,3]; f(1,2,3) + f(a)", 12);
    test("def f = { x,y,z=3 -> x + y + z }; def a = [1,2]; f(1,2,3) + f(a)", 12);
    test("def f(x, y) { x + y }; f([1,2])", 3);
    test("def f(x, y=3) { x + y }; f([1,2])", List.of(1,2,3));
    test("def f(x, y) { x + y }; def a = [1,2]; f(a)", 3);
    test("def f(x, y=3) { x + y }; def a = [1,2]; f(a)", List.of(1,2,3));
    test("def f(x, y=3) { x + y }; f(1,2)", 3);
    test("def f(List x, y=3) { x + y }; f([1,2])", List.of(1,2,3));
    testError("def f(List x, y=4, z) { x + y + z }; f([1, 2, 3])", "int cannot be cast to List");
  }

  @Test public void passingNamedArgs() {
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,yy:2)", "no such parameter: yy");
    testError("def f(int x, y=[a:3]) { x + y }; f(x:'1',y:2)", "cannot convert argument of type string to parameter type of int");
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,(''+'y'):2)", "invalid parameter name");
    test("def f(x, y=[a:3]) { x + y }; f(x:1,y:2)", 3);
    testError("def f(x, y) { x + y }; f([x:1,y:2])", "missing mandatory argument: y");
    test("def f(x, y=[a:3]) { x + y }; f([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("def f(x,y) { x*y }; f(x:2,y:3)", 6);
    test("def f(int x, int y) { x*y }; f(x:2,y:3)", 6);
    test("def f(int x, int y=3) { x*y }; f(x:2)", 6);
    test("def f(Map x, int y=3) { x.y = y; x }; f([x:2])", Map.of("x",2,"y",3));
    testError("def f(Map x, int y=3) { x.y = y; x }; f(x:2)", "cannot convert argument of type int to parameter type of Map");
    test("def f(def x, int y=3) { x.y = y; x }; f([x:2])", Map.of("x",2,"y",3));
    testError("def f(def x, int y=3) { x.y = y; x }; f(x:2)", "invalid object type");
    test("def f(Map x, int y=3) { x.y = y; x }; f([x:2,y:4])", Map.of("x",2,"y",3));
    test("def f(def x, int y=3) { x.y = y; x }; f([x:2,y:4])", Map.of("x",2,"y",3));
    test("def f(int x) { x*3 }; f(x:2)", 6);
    test("def f(int x=2) { x*3 }; f(x:2)", 6);
    test("def f(x,y) { x + y }; f(x:2,y:3)", 5);
    test("def f(x,y=[a:1]) { x + y }; f([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("def f(x,y=[a:1]) { x + y }; f(x:2,y:3)", 5);
    testError("def f(x,y,z) {x + y + z}; f(x:2,y:3)", "missing mandatory argument: z");
    testError("def f(x,y,z) {x + y + z}; f(y:3)", "missing mandatory arguments");
    testError("def f(x,y,z) {x + y + z}; f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("def f(x,y,z) {x + y + z}; f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
    testError("def f = { x,y -> x*y }; def a = [x:2,y:3]; f(a)", "missing mandatory arguments");
    test("def f = { x,y=[a:1] -> x + y }; def a = [x:2,y:3]; f(a)", Map.of("x",2,"y",3,"a",1));
    test("def f = { x,y=[a:1] -> x + y }; f(x:2,y:3)", 5);
    testError("def f = { x,y,z -> x + y + z}; def a = [x:2,y:3]; f(a)", "missing mandatory arguments");
    testError("def f = { x,y,z -> x + y + z}; def a = [y:3]; f(a)", "missing mandatory arguments");
    testError("sleep(x:1,y:2)", "does not support invocation with named arguments");
    testError("sleep([x:1,y:2])", "cannot convert argument of type map");
    testError("def a = [x:{it*it}]; [1,2,3].map(a)", "cannot be cast to function");
    testError("def f(a, b, c) { c(a+b) }; f(a:2,b:3) { it*it }", "missing mandatory argument: c");
    test("def f(a = '',b = 2) {a+b}; f('',null)", "null");
    test("def f(a = '',b = 2) {a+b}; f(a:'',b:null)", "null");
  }

  @Test public void simpleClosures() {
    test("{;}()", null);
    test("def f = { -> 10 }; f()", 10);
    test("var f = { -> 10 }; var g = {20}; f = g; f()", 20);
    testError("def f = { -> 10 }; f(3)", "too many arguments");
    test("int i = 1; { int i = 2; i++; }; i", 1);
    test("def f = { x -> x * x }; f(2)", 4);
    test("def f = { int x -> x * x }; f(2)", 4);
    test("def f = { int x=3 -> x * x }; f(2)", 4);
    test("def f = { int x=3 -> x * x }; f()", 9);
    test("{ x -> x * x }(2)", 4);
    test("def f = { x -> { x -> x * x }(x) }; f(2)", 4);
    test("def f = { int x=3, long y=9 -> x * y }; f()", 27L);
    test("def f = { int x=3, long y=9 -> x * y }; f(2,3)", 6L);
    test("var f = { int x=3, long y=9 -> x * y * 1.0 }; f(2,3)", "#6.0");
    testError("var f = { x -> x*x }; f = 3", "cannot convert from type of right hand side");
    test("def f = { x -> def g(x) { x*x }; g(x) }; f(3)", 9);
    test("def f = { def g(x) { x*x } }; f()(3)", 9);
    test("def f = { def g = { 3 } }; f()()", 3);
    test("def f = { -> 3 }; f()", 3);
    test("def f = { it -> it * it }; f(3)", 9);
    test("def f = { it * it }; f(3)", 9);
    testError("def f = { -> it * it }; f(3)", "unknown variable 'it'");
    test("def f = { { it * it }(it) }; f(3)", 9);
    test("def f = { it = 2 -> { it * it }(it) }; f(3)", 9);
    test("def f = { it = 2 -> { it * it }(it) }; f()", 4);
    testError("def f = { it = f(it) -> { it * it }(it) }; f()", "variable initialisation cannot refer to itself");
    testError("def f = { x, y=1, z -> x + y + z }; f(0)", "missing mandatory arguments");
    testError("def f = { x, y=1, z -> x + y + z }; f(1,2,3,4)", "too many arguments");
    test("def f = { var z='abc' -> z }; f('z')", "z");
    testError("def f = { x, y=1, z='abc' -> x + y + z }; f(1)", "non-numeric operand");
    test("def f = { x, y=1, z='abc' -> x + y + z }; f('z')", "z1abc");
    test("def f = { x, var y=1, var z='abc' -> x + y + z }; f('z')", "z1abc");
    test("def f = { String x, int y=1, var z='abc' -> x + y + z }; f('z')", "z1abc");
    testError("def f = { String x, int y=1, var z='abc' -> x + y + z }; f(123)", "cannot convert");
    test("def f = { String x, int y=1, var z='abc' -> x + y + z }; f('123')", "1231abc");
    test("def f = { for(var i=0;i<10;i++); }; f()", null);
    test("def f; f = { it = 2 -> { it * it }(it) }; f()", 4);
  }

  @Test public void closedOverVars() {
    test("int x = 1; def f() { x-- }; x = 2", 2);
    test("int x = 1; def f() { x-- }; x++", 1);
    test("int x = 1; def f() { x-- }; ++x", 2);
    test("int x = 1; def f() { x++ }; f(); x", 2);
    test("int x = 1; def f() { x++ }; f() + x", 3);
    test("int x = 1; def f() { def g() { x++ }; g() }; f(); x", 2);
    test("int x = 1; def f = { x++ }; f(); x", 2);
    test("int x = 1; def f = { x++ }; f() + x", 3);
    test("int x = 1; def f = { def g() { x++ }; g() }; f(); x", 2);
    test("def F; def G; def f(x) { if (x==1) x else 2 * G(x) }; def g(x) { x + F(x-1) }; F=f; G=g; F(3)", 18);
    test("String x = '1'; def f = { def g() { x + x }; g() }; f(); x", "1");
    test("String x = '1'; def f = { def g() { x + x }; g() }; f()", "11");
    test("long x = 1; def f = { def g() { x + x }; g() }; f()", 2L);
    test("double x = 1; def f = { def g() { x + x }; g() }; f()", 2D);
    test("Decimal x = 1; def f = { def g() { x + x }; g() }; f()", "#2");
    test("def x = { it=2 -> it + it }; def f = { def g() { x() + x() }; g() }; f()", 8);
    test("def x(y=2) { y+y }; def f = { def g() { x() + x() }; g() }; f()", 8);
    test("def x = { y=2 -> y+y }; def f = { def g() { x() + x() }; g() }; f()", 8);
    test("def x = { y=2 -> y+y }; def f = { def g = { x() + x() }; g() }; f()", 8);
    test("def x = { y=2 -> y+y }; def f = { def g = { def a(x){x+x}; a(x()) + a(x()) }; g() }; f()", 16);
    test("def f(x) { def g() { x+x }; g() }; f(3)", 6);
    test("def f(x,y=x) { def g() { x+y }; g() }; f(3)", 6);
    test("def f(x,y=++x) { def g() { x+y }; g() }; f(3)", 8);
    test("def f(x,y={++x}) { def g() { y()+x }; g() }; f(3)", 8);
    test("def f(x,y=++x+2,z={++x + ++y}) { def g() { x++ + y++ + z() }; g() }; f(3)", 24);
    test("def f(x,y={++x}()) { def g() { y+x }; g() }; f(3)", 8);
    test("def f(x) { def g(x) { f(x) + f(x) }; if (x == 1) 1 else g(x-1) }; f(3)", 4);
    test("def f(x=1) { def g={x+x}; x++; g() }; f()", 4);
    test("def f(x=1,y=2) { def g(x) { f(x) + f(x) + y }; if (x == 1) 1 else g(x-1) }; f(3)", 10);
    test("def f(x=1,y=2) { def g(x) { f(x,y) + f(x,y) + y }; if (x == 1) 1 else g(x-1) }; f(3)", 10);
    test("def f(x=1,y=f(1,1)+1) { def g(x) { f(x,y) + f(x,y) + y }; if (x == 1) 1 else g(x-1) }; f(3)", 10);
    test("def f() { int x = 1; [{ it + x++}, {it - x++}]}; def x = f(); x[0](5) + x[0](5) + x[1](0) + x[1](0)", 6);
    test("def f(x=1,g=f,y=x) { if (x == 1) 1 else x + g(x-1) + y }; f(2)", 5);
    test("def f(x=1,g=f,y=x) { if (x == 1) 1 else x + g(x-1) + y }; f(4)", 19);
    test("def x=16; def f = { it = x -> it }; f()", 16);
    test("def f; f = { it = f(2) -> { it * it }(it) }; f()", 16);
    testError("def x=1; def f(a){g(a)}; def g(a){x+a}; f(2)", "requires passing closed over variable x");
    testError("def x=1; def f(x){def ff() {g(x)}; ff()}; def g(a){x+a}; f(2)", "requires passing closed over variable x");
    test("def x=1; def f(a){x+g(a)}; def g(a){x+a}; f(2)", 4);
    testError("def x=1; def y=2; def f(a){g(a)}; def g(a){x+y+a}; f(2)", "requires passing closed over variables x,y");
    test("def x=1; def y=2; def f(a){x;y;g(a)}; def g(a){x+y+a}; f(2)", 5);
    testError("def x=1; def f = { g(it) }; def g(a){x+a}; f(2)", "requires passing closed over variable x");
    test("def x=1; def f = {x+g(it)}; def g(a){x+a}; f(2)", 4);
    testError("def x=1; def y=2; def f = {g(it)}; def g(a){x+y+a}; f(2)", "requires passing closed over variables x,y");
    test("def x=1; def y=2; def f = {x;y;g(it)}; def g(a){x+y+a}; f(2)", 5);
    test("def g(x){x}; def f() { def a = g; a(3) }; f()", 3);
    test("def g(x){x}; def f(x){def a = g; x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    test("def g(x){x}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    testError("def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; f(2)", "closed over variable h");
    testError("def f() { def a = g; a() }; def g(){4}; f()", "closes over variable g that has not yet been initialised");
    testError("def f() { def a = g; def b=y; a()+b() }; def y(){2}; def g(){4}; f()", "closes over variables g,y that have not yet been initialised");
    test("def x = 'x'; def f() { x += 'abc' }; f(); x", "xabc");
    test("def x = 'x'; def f() { x = x + 'abc' }; f(); x", "xabc");
    test("def x = 'x'; def f() { x *= 3 }; f(); x", "xxx");
    test("def x = 'x'; def f() { x = x * 3 }; f(); x", "xxx");
    test("def x = /x/; def f() { x += 'abc' }; f(); x", "xabc");
    test("def x = /x/; def f() { x = x + 'abc' }; f(); x", "xabc");
    test("def x = /x/; def f() { x *= 3 }; f(); x", "xxx");
    test("def x = /x/; def f() { x = x * 3 }; f(); x", "xxx");
    test("def it = 'x'; def x = /x/; def f() { x += 'abc' }; f(); x", "xabc");
    test("def it = 'x'; def x = /x/; def f() { x = x + 'abc' }; f(); x", "xabc");
    test("def it = 'x'; def x = /x/; def f() { x *= 3 }; f(); x", "xxx");
    test("def it = 'x'; def x = /x/; def f() { x = x * 3 }; f(); x", "xxx");
    test("def f(x) { def g() { sleep(0,x)+sleep(0,x) }; g() }; f(3)", 6);
    test("def f(x,y=sleep(0,{++x})) { def g() { y()+x }; g() }; f(3)", 8);
    test("def f(x,y={8}) { def g() { y() }; g() }; f(3)", 8);
    test("def f(x,y=++x) { def g() { sleep(0,y)+x }; g() }; f(3)", 8);
    test("def f(x,y=++x) { def g() { sleep(0,y)+sleep(0,x) }; g() }; f(3)", 8);
  }

  @Test public void closurePassingSyntax() {
    test("def f(n,x){ for(int i=0;i<n;i++) x(i) }; int sum=0; f(10,{sum+=it}); sum", 45);
    test("def f(n,x){ for(int i=0;i<n;i++) x(i) }; int sum=0; f(10){sum+=it}; sum", 45);
    test("def f(x){ x() }; int sum=0; f{sum=30}; sum", 30);
    test("def f(x,y){ x(); y() }; int sum=0; f{sum+=20}{sum+=30}; sum", 50);
    testError("def f(x,y){ x(); y() }; int sum=0; f{sum+=20}{sum+=30}{sum+=40}; sum", "too many arguments");
    test("def f = { n,x -> for(int i=0;i<n;i++) x(i) }; int sum=0; f(10,{sum+=it}); sum", 45);
    test("def f = { n,x -> for(int i=0;i<n;i++) x(i) }; int sum=0; f(10){sum+=it}; sum", 45);
    test("def f = { it() }; int sum=0; f{sum=30}; sum", 30);
    test("def f = { x,y -> x(); y() }; int sum=0; f{sum+=20}{sum+=30}; sum", 50);
    testError("def f = { x,y -> x(); y() }; int sum=0; f{sum+=20}{sum+=30}{sum+=40}; sum", "too many arguments");
    test("def f(n,x){ for(int i=0;i<n;i++) x(i) }; int sum=0; def g=f; g(10,{sum+=it}); sum", 45);
    test("def f(n,x){ for(int i=0;i<n;i++) x(i) }; int sum=0; def g=f; g(10){sum+=it}; sum", 45);
    test("def f(x){ x() }; int sum=0; def g=f; g{sum=30}; sum", 30);
    test("def f(x,y){ x(); y() }; int sum=0; def g=f; g{sum+=20}{sum+=30}; sum", 50);
    testError("def f(x,y){ x(); y() }; int sum=0; def g=f; g{sum+=20}{sum+=30}{sum+=40}; sum", "too many arguments");
    test("def f(x){x()}; f{return {it*it}}(2)", 4);
  }

  @Test public void builtinMethods() {
    test("List x; x.size()", 0);
    test("List x = []; x.size()", 0);
    test("List x = [1,2,3]; x.size()", 3);
    test("def x = []; x.size()", 0);
    test("def x = []; x?.size()", 0);
    test("def x; x?.size()", null);
    test("def x = [1,2,3]; x.size()", 3);
    test("Map x; x.size()", 0);
    test("Map x = [:]; x.size()", 0);
    test("Map x = [a:1,b:2]; x.size()", 2);
    test("Map x = [a:1,b:[c:3]]; x.b.size()", 1);
    testError("Map x = [a:1,b:[c:3]]; x.a.size()", "no such method");
    testError("def x; x.size()", "null value");
    test("def x; def y; y ?= x.size(); y", null);
    test("def x = [:]; x.size()", 0);
    test("def x = [a:1,b:2]; x.size()", 2);
    test("def x = [a:1,b:2]; x?.size()", 2);
    test("def x = [a:1,b:[c:3]]; x.b.size()", 1);
    test("def x = [a:1,b:[1,2,3]]; x.b.size()", 3);
    testError("def x = [a:1,b:[c:3]]; x.a.size()", "no such method");
    test("def x = [1,2,3]; def f = x.size; f()", 3);
    test("List x = [1,2,3]; def f = x.size; f()", 3);
    test("def x = [a:1,b:2,c:3]; def f = x.size; f()", 3);
    test("Map x = [a:1,b:2,c:3]; def f = x.size; f()", 3);
    test("List x = [1,2,3]; x.'size'()", 3);
    testError("def x = 1; x.size()", "no such method");
    testError("def x = [1]; x.sizeXXX()", "no such method");
    testError("1.size()", "no such method");
    test("def x; def y; y = x?.size()?.size(); y", null);
    test("def x = 'size'; [1,2,3].\"$x\"()", 3);
    test("def x = 'size'; def y = [1,2,3]; y.\"$x\"()", 3);
  }

  @Test public void listEach() {
    test("[].each{}", null);
    test("int sum = 0; [].each{sum++}; sum", 0);
    test("int sum = 0; [10].each{ sum += it }; sum", 10);
    test("int sum = 0; [10].each({ sum += it }); sum", 10);
    test("int sum = 0; [10].each(){ sum += it }; sum", 10);
    test("int sum = 0; [1,2,3,4].each{ x -> sum += x }; sum", 10);
    test("int sum = 0; def x = [1,2,3,4]; x.each{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x = [1,2,3,4]; x.each{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x = [1,2,3,4]; def f = x.each; f{ x -> sum += x }; sum", 10);
    test("int sum = 0; def x = [1,2,3,4]; def f = x.each; f{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x; x.each{ x -> sum += x }; sum", 0);
    test("int sum = 0; [1,2,3].each{ x,y=2 -> sum += x+y }; sum", 12);
    test("int sum = 0; [1,2,3].each{ x=7,y=2 -> sum += x+y }; sum", 12);
    testError("int sum = 0; [1,2,3].each{ x,y -> sum += x+y }; sum", "missing mandatory argument");
  }

  @Test public void mapEach() {
    test("[:].each{ x,y -> ; }", null);
    test("[:].each{}", null);
    test("[:].each{ x-> }", null);
    test("def result = ''; [a:1,b:2].each{ x,y -> result += \"[$x,$y]\" }; result", "[a,1][b,2]");
    test("def result = ''; [a:1,b:2].each{ result += \"[${it[0]},${it[1]}]\" }; result", "[a,1][b,2]");
    test("def result = ''; [a:1,b:2].each{ x -> result += \"[${x[0]},${x[1]}]\" }; result", "[a,1][b,2]");
    test("def result = 0; [a:1,b:2].each{ x -> result += x.size() }; result", 4);
    test("def result = ''; [a:1,b:2].each{ x -> result += x }; result", "['a', 1]['b', 2]");
    test("def result = 0; [a:1,b:2].each{ x -> x.each{ result++ } }; result", 4);
    test("def result = 0; [a:1,b:2].each{ List x -> x.each{ result++ } }; result", 4);
    test("def result = ''; [a:1,b:2].each{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; [a:1,b:2].each{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; Map x = [a:1,b:2]; x.each{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; def x = [a:1,b:2]; x.each{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; Map x = [a:1,b:2]; def f = x.each; f{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; def x = [a:1,b:2]; def f = x.each; f{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
  }

  @Test public void listCollect() {
    testError("[].collect{}{}", "too many arguments");
    testError("def x = []; x.collect{}{}", "too many arguments");
    test("[].collect()", List.of());
    test("[].collect{}", List.of());
    test("[1,2,3].collect{}", Arrays.asList(null, null, null));
    test("[1,2,3].collect()", List.of(1,2,3));
    test("def x = [1,2,3]; x.collect{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.collect()", List.of(1,2,3));
    test("def x = []; x.collect{}", List.of());
    test("def x = []; x.collect()", List.of());
    test("List x = []; x.collect{}", List.of());
    testError("def x; x.collect{}", "null value");
    test("List x = [1,2,3,4]; x.collect{it*it}", List.of(1,4,9,16));
    test("List x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.collect{it*it}.collect; f{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{ x -> return {x*x}}.collect{it()}", List.of(1,4,9,16));
  }

  @Test public void mapCollect() {
    test("[:].collect{}", List.of());
    test("[:].collect()", List.of());
    testError("def x = 1; x.collect{}", "no such method 'collect'");
    test("[a:1,b:2,c:3].collect{ [it[0]+it[0],it[1]+it[1]] }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].collect{ it[0]+it[0]+it[1]+it[1] }", List.of("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].collect{ it.collect{ it+it } }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].collect{ x,y -> x + y }", List.of("a1","b2","c3"));
  }

  @Test public void collectEntries() {
    test("[].collectEntries()", Map.of());
    test("[].collectEntries{}", Map.of());
    test("[:].collectEntries()", Map.of());
    test("[:].collectEntries{}", Map.of());
    test("[a:1].collectEntries()", Map.of("a", 1));
    test("[a:1].collectEntries{ it }", Map.of("a", 1));
    test("[a:1].collectEntries{ a,b -> [a,b] }", Map.of("a", 1));
    test("[a:1,b:2].collectEntries{ it }", Map.of("a", 1,"b",2));
    test("[a:1,b:2].collectEntries{ a,b -> [a,b] }", Map.of("a", 1,"b",2));
    testError("[a:1].collectEntries{}", "got null");
    test("[['a',1]].collectEntries()", Map.of("a",1));
    test("[['a',1],['a',2]].collectEntries()", Map.of("a",2));
    test("[a:1,b:2].collectEntries()", Map.of("a",1,"b",2));
    test("[1,2,3,4].collectEntries{ [it.toString()*it,it*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.map{ a,b -> [a,b] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it.toString()*it,it*it] }.collectEntries()", Map.of("1",1,"22",4,"333",9,"4444",16));
    testError("[1,2,3,4].map{ [it,it*it] }.collectEntries()", "expected string type for key");
    test("def x = []; x.collectEntries()", Map.of());
    test("def x = []; x.collectEntries{}", Map.of());
    test("def x = [:]; x.collectEntries()", Map.of());
    test("def x = [:]; x.collectEntries{}", Map.of());
    test("def x = [a:1]; x.collectEntries()", Map.of("a", 1));
    test("def x = [a:1]; x.collectEntries{ it }", Map.of("a", 1));
    test("def x = [a:1]; x.collectEntries{ a,b -> [a,b] }", Map.of("a", 1));
    test("def x = [a:1,b:2]; x.collectEntries{ it }", Map.of("a", 1,"b",2));
    test("def x = [a:1,b:2]; x.collectEntries{ a,b -> [a,b] }", Map.of("a", 1,"b",2));
    testError("def x = [a:1]; x.collectEntries{}", "got null");
    test("def x = [['a',1]]; x.collectEntries()", Map.of("a",1));
    test("def x = [['a',1],['a',2]]; x.collectEntries()", Map.of("a",2));
    test("def x = [a:1,b:2]; x.collectEntries()", Map.of("a",1,"b",2));
    test("def x = [1,2,3,4]; x.collectEntries{ [it.toString()*it,it*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it.toString()*it,it*it] }.collectEntries()", Map.of("1",1,"22",4,"333",9,"4444",16));
    testError("def x = [1,2,3,4]; x.map{ [it,it*it] }.collectEntries()", "expected string type for key");
    test("def x = [1,2,3,4]; x.collectEntries{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [sleep(0,it),sleep(0,it)*sleep(0,it)] }.collectEntries{ a,b -> [sleep(0,a.toString())*sleep(0,a),sleep(0,b)] }", Map.of("1",1,"22",4,"333",9,"4444",16));
  }

  @Test public void collectionMap() {
    test("[].map()", List.of());
    test("def f = [].map; f()", List.of());
    testError("[].map{}{}", "too many arguments");
    testError("def x = []; x.map{}{}", "too many arguments");
    test("[].map{}", List.of());
    test("[1,2,3].map{}", Arrays.asList(null, null, null));
    test("[1,2,3].map()", List.of(1,2,3));
    test("def x = [1,2,3]; x.map{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.map()", List.of(1,2,3));
    test("def x = []; x.map{}", List.of());
    test("def x = []; x.map()", List.of());
    test("List x = []; x.map{}", List.of());
    testError("def x; x.map{}", "null value");
    test("List x = [1,2,3,4]; x.map{it*it}", List.of(1,4,9,16));
    test("List x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.map{it*it}.map; f{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{ x -> return {x*x}}.map{it()}", List.of(1,4,9,16));
    test("[:].map{}", List.of());
    test("[:].map()", List.of());
    testError("def x = 1; x.map{}", "no such method 'map'");
    test("[a:1,b:2,c:3].map{ [it[0]+it[0],it[1]+it[1]] }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].map{ it[0]+it[0]+it[1]+it[1] }", List.of("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].map{ it.map{ it+it } }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].map{ x,y -> x + y }", List.of("a1","b2","c3"));
    test("def x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", List.of(2, 2));
    test("def x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", List.of(2, 2));
    test("def x = [1,2,3]; x.map{}.size()", 3);
    test("def x = [1,2,3]; x.map().size()", 3);
    test("var x = [1,2,3]; x.map().size()", 3);
    test("def x = [1,2,3]; x.map{it+it}.size()", 3);
    test("var x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.\"${'map'}\"{it+it+i++}; i", 3);
    test("var x = [1,2,3]; def i = 0; x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def f(x){i}; f(x.map{i++}); i", 3);
    test("def x = [1,2,3]; x.map{it*it}.collect{it+it}.map{it-1}", List.of(1, 7, 17));
    test("[1,2,3].map{it} instanceof List", true);
    test("def x = [1,2,3]; x.map{it} instanceof List", true);
    test("def f = [1,2,3].map; f{it+it}", List.of(2,4,6));
    test("def f = [1,2,3].map; f()", List.of(1,2,3));
    testError("def f = [1,2,3].map; f('x')", "cannot convert");
    test("[1].map{ [it,it] }.map{ a,b -> a + b }", List.of(2));
    test("def x = [1,2,3]; def f = x.map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("def f = [1,2,3].map; f{it+it}", List.of(2,4,6));
    test("def f = [1,2,3].map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("[1,2,3].map{it*it}[1]", 4);
    testError("[1,2,3].map{ -> }", "too many arguments");
  }

  @Test public void mapEntryAsList() {
    test("def x; [a:1].each{ x = it }; x", List.of("a",1));
    test("def x; [a:1].each{ x = it }; x; x.size()", 2);
  }

  @Test public void collectionSort() {
    test("[].sort()", List.of());
    test("[].sort{it[1] <=> it[0]}", List.of());
    test("[1].sort{it[1] <=> it[0]}", List.of(1));
    test("[1,2].sort{it[1] <=> it[0]}", List.of(2,1));
    test("[3,2,1].sort()", List.of(1,2,3));
    test("[3,3,4,1,2,2,5].sort()", List.of(1,2,2,3,3,4,5));
    test("[3,3,4,1,2,2,5].sort{ a,b -> a <=> b }", List.of(1,2,2,3,3,4,5));
    test("def f = [3,2,1].sort; f()", List.of(1,2,3));
    test("def f = [3,2,1].sort; f{a,b -> b <=> a}", List.of(3,2,1));
    testError("def f = [3,2,1].sort; f('z')", "cannot convert");
    test("[1,2,3].sort()", List.of(1,2,3));
    test("[3,2,1,4,5].sort{a,b -> b <=> a}", List.of(5,4,3,2,1));
    test("def x = [3,2,1,4,5].sort{a,b -> b <=> a}; x", List.of(5,4,3,2,1));
    test("[3,2,1,4,5].sort{it[1] <=> it[0]}", List.of(5,4,3,2,1));
    test("def x = [3,2,1,4,5]; x.sort{a,b -> b <=> a}", List.of(5,4,3,2,1));
    test("def x = [3,2,1,4,5]; x.sort{it[1] <=> it[0]}", List.of(5,4,3,2,1));
    test("def x = [3,2,1,4,5]; def f = x.sort; f{a,b -> b <=> a}", List.of(5,4,3,2,1));
    test("def x = [3,2,1,4,5]; def f = x.sort; f{it[1] <=> it[0]}", List.of(5,4,3,2,1));
    testError("def x = [3,2,'a',4,5]; def f = x.sort{it[1] <=> it[0]}", "cannot compare");
    test("def x = [a:1,x:4,e:3,g:7]; x.sort{a,b -> a[0] <=> b[0]}",
         List.of(List.of("a",1),List.of("e",3),List.of("g",7),List.of("x",4)));
    test("def x = [a:1,x:4,e:3,g:7]; x.sort{it[0][0] <=> it[1][0]}",
         List.of(List.of("a",1),List.of("e",3),List.of("g",7),List.of("x",4)));
    final int SORT_SIZE = ThreadLocalRandom.current().nextInt(260);
    List<Integer> randomNums = IntStream.range(0, SORT_SIZE)
                                        .mapToObj(i -> ThreadLocalRandom.current().nextInt(100000))
                                        .collect(Collectors.toList());
    ArrayList<Integer> sorted = new ArrayList(randomNums);
    sorted.sort(null);
    test("" + randomNums + ".sort{ a,b -> a <=> b }", sorted);
    test("" + randomNums + ".sort()", sorted);
    test("" + randomNums + ".sort{ a,b -> sleep(sleep(0,0),a) <=> sleep(0,sleep(0,b)) }", sorted);
  }

  @Test public void stringAddRepeat() {
    test("'a' + 'b'", "ab");
    test("'' + ''", "");
    test("'' + null", "null");
    test("def x = 'a'; def y = 'b'; x+y", "ab");
    test("var x = 'a'; var y = 'b'; x+y", "ab");
    test("def x = 'a'; def y = 'b'; x += y", "ab");
    test("var x = 'a'; var y = 'b'; x += y", "ab");
    test("def x = 'a'; def y = 'b'; x += y; x", "ab");
    test("var x = 'a'; var y = 'b'; x += y; x", "ab");
    test("var x = 'x'; x += 'y'", "xy");
    test("var x = 'x'; x += 'y'; x", "xy");
    test("String x = 'x'; x += 'y'", "xy");
    test("String x = 'x'; x += 'y'; x", "xy");
    test("String x = 'x'; x += 1", "x1");
    test("String x = 'x'; x += 1; x", "x1");
    testError("String x = 'xx'; x++", "operator '++' cannot be applied to type string");
    testError("def x = 'xx'; x++", "non-numeric operand");
    testError("String x = 'xx'; ++x", "operator '++' cannot be applied to type string");
    testError("def x = 'xx'; ++x", "non-numeric operand");
    testError("String x = 'xx'; x--", "operator '--' cannot be applied to type string");
    testError("def x = 'xx'; x--", "non-numeric operand");
    testError("String x = 'xx'; --x", "operator '--' cannot be applied to type string");
    testError("def x = 'xx'; --x", "non-numeric operand");

    test("'a' * 1", "a");
    test("'a' * 0", "");
    test("'' * 0", "");
    test("'' * 1", "");
    test("'' * 2", "");
    test("'a' * 2", "aa");
    test("'ab' * 2", "abab");
    test("def x = 'a'; x * 1", "a");
    test("def x = 'a'; x * 1.5", "a");
    test("def x = 'a'; x * 1L", "a");
    test("def x = 'a'; x * 1.234D", "a");
    test("def x = 'a'; x * 0", "");
    test("def x = ''; x * 0", "");
    test("var x = ''; x * 1", "");
    test("def x = ''; x * 2", "");
    test("def x = ''; x * 2.678", "");
    test("def x = 'a'; def y = 2.678; x * y", "aa");
    test("def x = 'a'; def y = 2; x * y", "aa");
    test("def x = 'ab'; def y = 2; x * y", "abab");
    test("def x = 'a'; x *= 1", "a");
    test("def x = 'a'; x *= 1; x", "a");
    test("def x = 'a'; x *= 0", "");
    test("def x = 'a'; x *= 0; x", "");
    test("def x = ''; x *= 0", "");
    test("def x = ''; x *= 0; x", "");
    test("var x = ''; x *= 1", "");
    test("var x = ''; x *= 1; x", "");
    test("def x = ''; x *= 2", "");
    test("def x = ''; x *= 2; x", "");
    test("def x = 'a'; def y = 2; x *= y", "aa");
    test("def x = 'a'; def y = 2; x *= y; x", "aa");
    test("def x = 'ab'; def y = 2; x *= y", "abab");
    test("def x = 'ab'; def y = 2; x *= y; x", "abab");
    testError("'ab' * -1", "repeat count must be >= 0");
    testError("'ab' * -1.234", "repeat count must be >= 0");
  }

  @Test public void listAdd() {
    test("[]+[]", List.of());
    test("def x = []; x + x", List.of());
    test("[] + 1", List.of(1));
    test("[] + [a:1]", List.of(Map.of("a",1)));
    test("[1] + 2", List.of(1,2));
    test("[1,2] + [3,4]", List.of(1,2,3,4));
    test("def x = [1,2]; x + [3,4]", List.of(1,2,3,4));
    test("def x = [1,2]; def y = [3,4]; x + y", List.of(1,2,3,4));
    test("['a','b'] + 'c'", List.of("a","b","c"));
    test("['a','b'] + 1", List.of("a","b",1));
    test("def x = ['a','b']; def y = 'c'; x + y", List.of("a","b","c"));
    test("var x = ['a','b']; x += 'c'", List.of("a","b","c"));
    test("def x = ['a','b']; x += 'c'", List.of("a","b","c"));
    test("var x = ['a','b']; x += ['c','d']", List.of("a","b","c","d"));
    test("def x = ['a','b']; x += ['c','d']", List.of("a","b","c","d"));
    test("var x = ['a','b']; x += x", List.of("a","b","a","b"));
    test("def x = ['a','b']; x += x", List.of("a","b","a","b"));
    test("var x = ['a','b']; def y = 'c'; x += y", List.of("a","b","c"));
    test("var x = ['a','b']; var y = 'c'; x += y", List.of("a","b","c"));
    test("var x = ['a','b']; def y = 'c'; x += y; x", List.of("a","b","c"));
    test("def x = ['a','b']; def y = 'c'; x += y", List.of("a","b","c"));
    test("def x = ['a','b']; var y = 'c'; x += y", List.of("a","b","c"));
    test("var x = ['a','b']; def y = ['c','d']; x += y", List.of("a","b","c","d"));
    test("var x = ['a','b']; var y = ['c','d']; x += y", List.of("a","b","c","d"));
    test("def x = ['a','b']; var y = ['c','d']; x += y", List.of("a","b","c","d"));
    test("var x = ['a','b']; x += x", List.of("a","b","a","b"));
    test("def x = ['a','b']; x += x", List.of("a","b","a","b"));
    test("var x = ['a','b']; x += x; x", List.of("a","b","a","b"));
    test("def x = ['a','b']; x += x; x", List.of("a","b","a","b"));
    test("var x = ['a','b']; x += 1; x", List.of("a","b",1));
    test("def x = ['a','b']; x += 1; x", List.of("a","b",1));
    testError("var x = ['a','b']; x -= 1; x", "non-numeric operand");
    testError("def x = ['a','b']; x -= 1; x", "non-numeric operand");
    testError("var x = ['a','b']; x++", "unary operator '++' cannot be applied to type list");
    testError("def x = ['a','b']; x++", "non-numeric operand");
    testError("var x = ['a','b']; ++x", "operator '++' cannot be applied to type list");
    testError("def x = ['a','b']; ++x", "non-numeric operand");
    testError("var x = ['a','b']; x--", "operator '--' cannot be applied to type list");
    testError("def x = ['a','b']; x--", "non-numeric operand");
    testError("var x = ['a','b']; --x", "operator '--' cannot be applied to type list");
    testError("def x = ['a','b']; --x", "non-numeric operand");
  }

  @Test public void mapAdd() {
    test("[:] + [:]", Map.of());
    test("def x = [:]; x + x", Map.of());
    test("[:] + [a:1]", Map.of("a",1));
    test("def x = [a:1]; [:] + x", Map.of("a",1));
    test("def x = [a:1]; x + x", Map.of("a",1));
    test("[a:1] + [b:2]", Map.of("a",1,"b",2));
    test("def x = [a:1]; def y = [b:2]; x + y", Map.of("a",1,"b",2));
    test("def x = [a:1]; def y = [a:2]; x + y", Map.of("a",2));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x + y", Map.of("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y", Map.of("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y", Map.of("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x", Map.of("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x", Map.of("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x += x", Map.of("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x += x", Map.of("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x += x; x", Map.of("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x += x; x", Map.of("a",2,"b",2,"c",3));
    testError("var x = [a:1,b:2]; x -= 1; x", "non-numeric operand");
    testError("def x = [a:1,b:2]; x -= 1; x", "non-numeric operand");
    testError("var x = [a:1,b:2]; x++", "unary operator '++' cannot be applied to type map");
    testError("def x = [a:1,b:2]; x++", "non-numeric operand");
    testError("var x = [a:1,b:2]; ++x", "operator '++' cannot be applied to type map");
    testError("def x = [a:1,b:2]; ++x", "non-numeric operand");
    testError("var x = [a:1,b:2]; x--", "operator '--' cannot be applied to type map");
    testError("def x = [a:1,b:2]; x--", "non-numeric operand");
    testError("var x = [a:1,b:2]; --x", "operator '--' cannot be applied to type map");
    testError("def x = [a:1,b:2]; --x", "non-numeric operand");
  }

  @Test public void andOrNot() {
    test("not true", false);
    test("not false", true);
    test("not not true", true);
    test("not not false", false);
    test("not (not not true)", false);
    test("not not (not false)", true);
    test("true and true", true);
    test("false and true", false);
    test("true and false", false);
    test("false and false", false);
    test("true or true", true);
    test("false or true", true);
    test("true or false", true);
    test("false or false", false);
    test("not false or false", true);
    test("false or true and false", false);
    test("true or true and false", true);
    test("true or true and true", true);
    test("false or not false and true", true);
    test("null and true", false);
    test("true and null", false);
    test("null and null", false);
    test("null or true", true);
    test("true or null", true);
    test("false or null", false);
    test("null or false", false);
    test("true and (true or false and true) or not (true and false)", true);
    test("def x = 1; true and x = 2; x", 2);
    test("def x = 1; x = 2 and true", true);
    test("def x = 1; x = 2 and true; x", 2);
    test("def it = 'abc'; /a/f ? true : false", true);
    test("def it = 'abc'; def x; /a/f and x = 'xxx'; x", "xxx");
    test("def x = 0; for (int i = 0; i < 10; i++) { i < 5 and do { x += i } and continue; x++ }; x", 15);
    test("int f() { true and return 1; return 0 }; f()", 1);
  }

  @Test public void builtinFunctions() {
    test("timeStamp() > 50*356*86400*1000", true); // more than 50 yrs since epoch started in 1970
    test("def f = timeStamp; f() > 0", true);
    test("sprintf('x')", "x");
    test("sprintf('x')", "x");
    test("sprintf('%s','x')", "x");
    test("sprintf('%s%d','x',1)", "x1");
    test("def f = sprintf; f('x')", "x");
    test("def f = sprintf; f('x')", "x");
    test("def f = sprintf; f('%s','x')", "x");
    test("def f = sprintf; f('%s%d','x',1)", "x1");
    testError("sprintf('%s%d','x','y')", "bad format string");
    testError("def f = sprintf; f('%s%d','x','y')", "bad format string");
    testError("sprintf('%zs%d','x','y')", "bad format string");
    testError("sprintf()", "missing mandatory arguments");
    testError("def f = sprintf; f()", "missing mandatory arguments");
  }

  @Test public void asyncFunctions() {
    test("sleep(1,2)", 2);
    test("sleep(1,2L)", 2L);
    test("sleep(1,2D)", 2D);
    test("sleep(1,2.0)", "#2.0");
    test("sleep(1,[])", List.of());
    test("sleep(1,[:])", Map.of());
    test("sleep(1,{it*it})(2)", 4);
    test("var x=1L; var y=1D; sleep(1,2)", 2);
    test("sleep(1,2) + sleep(1,3)", 5);
    test("List l=[1,2]; Map m=[a:1]; String s='asd'; int i=1; long L=1L; double d=1.0; Decimal D=1D; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("var l=[1,2]; var m=[a:1]; var s='asd'; var i=1; var L=1L; var d=1.0; var D=1D; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("def l=[1,2]; def m=[a:1]; def s='asd'; def i=1; def L=1L; def d=1.0; def D=1D; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("sleep(1,sleep(1,2))", 2);
    test("sleep(sleep(1,1),2)", 2);
    test("sleep(sleep(1,1),sleep(1,2))", 2);
    test("sleep(1,sleep(sleep(1,1),2)) + sleep(1,3)", 5);
    test("def y; y ?= sleep(1,2); y", 2);
    test("def y; y ?= sleep(1,null); y", null);
    test("def x; def y; y ?= sleep(1,x)?.size(); y", null);
    test("def x; def y; y ?= x?.(sleep(1,'si') + sleep(1,'ze'))()?.size(); y", null);
    test("def x = [1,2,3]; def y; y ?= x?.(sleep(1,'si') + sleep(1,'ze'))(); y", 3);
    test("def x = [1,2,3]; def y; y ?= x?.(sleep(sleep(1,1),'si') + sleep(sleep(1,1),'ze'))(); y", 3);
    test("def f(int x) { sleep(1,x) + sleep(1,x) }; f(1 )", 2);
    test("def f(int x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2);
    test("def f(long x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2L);
    test("def f(double x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2D);
    test("def f(int x = sleep(1,1) + sleep(1,2)) { sleep(1,x) + sleep(1,x) }; f()", 6);
    test("def f(int x = sleep(sleep(1,1),sleep(1,1))) { sleep(1,x) + sleep(1,x) }; f()", 2);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { x * y }; f()", 8);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { x * y }; f(sleep(1,3),sleep(1,5))", 15);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { sleep(1,x) * sleep(1,y) }; f()", 8);
    test("def f(x=8) { def g = {sleep(1,it)}; g(x) }; f()", 8);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { def g = { sleep(1,it) + sleep(1,it) }; g(x)*g(y) }; f()", 32);
    test("\"${sleep(1,2) + sleep(1,3)}\"", "5");
    test("\"${sleep(1,'a')}:2\"", "a:2");
    test("\"${sleep(1,'a')}:${sleep(1,2)}\"", "a:2");
    test("\"${sleep(1,'a')}:${sleep(1,2) + sleep(1,3)}\"", "a:5");
    test("def x = 0; for (int i=sleep(1,1),j=sleep(1,2*i),k=0; k<sleep(1,5) && i < sleep(1,100); k=sleep(1,k)+1,i=i+sleep(1,1),j=sleep(1,j+1)) { x += sleep(1,k+i+j); }; x", 45);

    test("sleep(0,2)", 2);
    test("def f() { sleep(0,1) }; f()", 1);
    test("def f = sleep; f(0,2)", 2);
    test("def f(x){sleep(0,x)}; f(sleep(0,2))", 2);
    test("def f(x){sleep(0,x)}; def g = f; g(2)", 2);
    test("def f(x){sleep(0,x)}; def g; g = f; g(2)", 2);
    test("def f(x){sleep(0,x)}; def g; g = f; 2", 2);
    test("def f(x){sleep(0,x)}; def g = f; g = {it}; g(2)", 2);
    test("def f(x){x}; def g = f; g = {it}; g(2)", 2);
    test("def g(x){sleep(0,x)*sleep(0,x)}; def f(x){g(sleep(0,x)*sleep(0,1))*sleep(0,x)}; f(2)", 8);
    test("def g = {sleep(0,it)*sleep(0,it)}; def f(x,y){g(x)*sleep(0,x)*sleep(0,y)}; f(2,1)", 8);
    test("def g; g = {it*it}; def f(x){g(x)*x}; f(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; h(2)", 8);
    test("def g = {it*it}; def h = { g(it)*it }; g = {sleep(0,it)*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {sleep(0,it)*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {it*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; g={it*it}; f(it) }; h(2)", 8);
    test("{it*it*it}(2)", 8);
    test("{it*it*sleep(0,it)}(2)", 8);
    test("def f(x=sleep(0,2)){x*x}; f(3)", 9);
    test("def f(x=sleep(0,2)){x*x}; f()", 4);
    test("def f(x){g(x)*g(x)}; def g(x){sleep(0,1)+sleep(0,x-1)}; f(2)", 4);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){x+x}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){sleep(0,x)+sleep(0,x)}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(sleep(0,-23)+sleep(0,x+23))}; h(x) }; def j(x){x+x}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    test("def f(x){x<=1?1:g(sleep(0,x-23)+sleep(0,23))}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)+j(x)}; h(x) }; def j(x){f(sleep(0,x)-1)+f(sleep(0,x)-1)}; f(2)", 5);
    test("def f = {it}; f(2)", 2);
    test("def f = {sleep(0,it)+sleep(0,it)}; f(2)", 4);
    test("def f(x){g(x)}; def g(x){sleep(0,x)+sleep(0,x)}; f(2)+f(3)", 10);
    test("def s = sleep; def f(x){x<=1?s(0,1):g(x)}; def g(x){s(0,x)+s(0,f(x-1))}; f(2)+f(3)", 9);
    test("def f(x){x<=1?1:g(x)}; def g(x){def s = sleep; s(0,x) + s(0,f(x-1))}; f(2)+f(3)", 9);
    test("int i = 1; def f(){ return sleep(0,{ ++i }) }; def g = f(); g() + g()", 5);
    test("int i = sleep(0,-1)+sleep(0,2); def f(){ return sleep(0,{ sleep(0,++i - 1)+sleep(0,1) }) }; def g = f(); g() + g()", 5);
    test("int i = 5; def f(int x = sleep(0,1)+sleep(0,1), long y=sleep(0,{x+i}())+sleep(0,{x+i+1}()), double z=3) { sleep(0,x)+sleep(0,y)+sleep(0,z) }; f()", 20D);
    test("def x = 1; while (true) { (false and break) or x = sleep(0,2); break }; x", 2);
    test("def x = 1; true and sleep(0, x = 2); x", 2);
    test("def x = 1; true and sleep(0, x ?= 2); x", 2);
    test("int x = 1; x += sleep(0,x) + sleep(0,x); x", 3);
    test("def x = 1; x += sleep(0,x) + sleep(0,x); x", 3);
  }

  @Test public void asyncFieldAccess() {
    test("Map m = [(sleep(0,'a')):sleep(0,1)]; m.\"${sleep(0,'a')}\"", 1);
    test("def m = [(sleep(0,'a')):sleep(0,1)]; m.\"${sleep(0,'a')}\"", 1);
  }

  @Test public void asyncCollectionClosures() {
    test("def x = [1,2,3].map{ sleep(1,it) * sleep(1,it) }; x.size()", 3);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }.size()", 3);
    test("def f = [1,2,3].map; f{ sleep(1,it) * sleep(1,it) }.size()", 3);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[1,2,3].map{ sleep(1,it) }.map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[a:1,b:2,c:3].map{ sleep(1,it) }.map{ sleep(sleep(1,1),it[1]) }.map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[a:1,b:2,c:3].map{ sleep(1,it) }.map{ \"${sleep(1,it[0])}:${sleep(1,it[1])*sleep(1,it[1])}\" }", List.of("a:1","b:4","c:9"));
    test("[1,2,3,4].filter{ sleep(1,true) }", List.of(1,2,3,4));
    test("[1,2,3,4].filter{ sleep(1,it) % 2 }", List.of(1,3));
    test("[1,2,3,4].filter{ sleep(1,it) % sleep(1,2) }", List.of(1,3));
    test("[1,2,3,4].filter{ sleep(1,true) }.filter{ sleep(1,it) % sleep(1,2) }", List.of(1,3));
    test("def x = 0; [1,2,3,4].each{ x += sleep(1,it) + sleep(1,it) }; x", 20);
    test("def x = 0; [1,2,3,4].map{ sleep(1,it) + sleep(1,it) }.each{ x += sleep(1,it) + sleep(1,it) }; x", 40);
    test("[1,2,3,4].collect{ sleep(1,it) }", List.of(1,2,3,4));
    test("[1,2,3,4].collect{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }", List.of(2,4,6,8));
    test("[1,2,3,4].collect{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }.size()", 4);
    test("def x = 0; def c = [1,2,3,4].collect{ sleep(1,it) }; c.each{ x += sleep(1,it) }; x", 10);
    test("def x = 0; [1,2,3,4].collect{ sleep(1,it) }.each{ x += sleep(1,it) }; x", 10);
    test("[1,2,3,4].map{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }", List.of(2,4,6,8));
    test("def x = 0; [1,2,3,4].map{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }.each{ x += sleep(1,it) }; x", 20);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }.filter{ sleep(1,it) > 3 }.collect{ sleep(1,it) + sleep(1,it) }", List.of(8,18));
    test("[5,4,1,3,2].sort{ a,b -> sleep(1,a) <=> b }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ a,b -> sleep(1,a) <=> sleep(1,b) }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ sleep(1,it[0]) <=> sleep(1,it[1]) }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ sleep(1,it[1]) <=> sleep(1,it[0]) }", List.of(5,4,3,2,1));
    test("[4,2,1,5,3].map{ sleep(1,it) * sleep(1,it) }.collect{ it+it }", List.of(32,8,2,50,18));
    test("[4,2,1,5,3].map{ sleep(1,it) * sleep(1,it) }.sort{ sleep(1,it[1]) <=> sleep(1,it[0]) }", List.of(25,16,9,4,1));
    test("sleep(0,[1,2,3].map{sleep(0,it)*sleep(0,it)})", List.of(1,4,9));
    test("def f = [1,2,3].map; sleep(0,f{sleep(0,it)*sleep(0,it)})", List.of(1,4,9));
  }

  @Test public void toStringTest() {
    testError("null.toString()", "tried to invoke method on null");
    test("1.toString()", "1");
    test("def x = 1; x.toString()", "1");
    test("def x = 1; def f = x.toString; f()", "1");
    test("[].toString()", "[]");
    test("[1,2,3].toString()", "[1, 2, 3]");
    test("[:].toString()", "[:]");
    test("[a:1].toString()", "[a:1]");
    test("[a:1,b:2].toString()", "[a:1, b:2]");
    test("['123':1,b:2].toString()", "['123':1, b:2]");
    test("['a b c':1,b:2].toString()", "['a b c':1, b:2]");
    test("[a:1,b:['x','y',[c:3]]].toString()", "[a:1, b:['x', 'y', [c:3]]]");
    test("1D.toString()", "1.0");
    test("1.0D.toString()", "1.0");
    test("1.0.toString()", "1.0");
    test("12345678901234L.toString()", "12345678901234");
    test("def x = []; x.toString()", "[]");
    test("def x = [1,2,3]; x.toString()", "[1, 2, 3]");
    test("def x = [:]; x.toString()", "[:]");
    test("def x = [a:1]; x.toString()", "[a:1]");
    test("def x = [a:1,b:2]; x.toString()", "[a:1, b:2]");
    test("def x = ['123':1,b:2]; x.toString()", "['123':1, b:2]");
    test("def x = ['a b c':1,b:2]; x.toString()", "['a b c':1, b:2]");
    test("def x = [a:1,b:['x','y',[c:3]]]; x.toString()", "[a:1, b:['x', 'y', [c:3]]]");
    test("def x = 1D; x.toString()", "1.0");
    test("def x = 1.0D; x.toString()", "1.0");
    test("def x = 1.0; x.toString()", "1.0");
    test("def x = 12345678901234L; x.toString()", "12345678901234");
    test("def x = ''; [a:1].each{ x += it.toString() }; x", "['a', 1]");
    test("def f(x) {x*x}; f.toString()", "MethodHandle(Continuation,String,int,Object[])Object");
    test("def f = { x -> x*x}; f.toString()", "MethodHandle(Continuation,String,int,Object[])Object");
    test("def x = 'abc'; def f = x.toString; f()", "abc");
  }

  @Test public void globals() {
    //test("timestamp() > 0", true);
    JacsalContext jacsalContext = JacsalContext.create()
                                               .evaluateConstExprs(true)
                                               .replMode(true)
                                               .debug(debugLevel)
                                               .build();
    Map<String,Object> globals = createGlobals();
    BiConsumer<String,Object> runtest = (code,expected) -> {
      Object result = Compiler.run(code, jacsalContext, globals);
      assertEquals(expected, result);
    };

    runtest.accept("def x = 1", 1);
    runtest.accept("x", 1);
    runtest.accept("def f(x){x*x}; f(2)", 4);
    runtest.accept("f(3)", 9);
  }

  @Test public void endOfLine() {
    test("1 + \n2", 3);
    test("def x =\n1 + \n2", 3);
    test("def x =\n1 + \n(2\n)", 3);
    test("def x =\n1 + (\n2)\n", 3);
    test("def x =\n1 + (\n2)\n", 3);
    test("[1,2,3].map{\n\nit}", List.of(1,2,3));
    test("['a','b'].map{\n\n\"\"\"\"$it=\" + $it\"\"\" }.\n\njoin(' + \", \" + ')", "\"a=\" + a + \", \" + \"b=\" + b");
    test("def f = {\nreturn }; f()", null);
    test("def f = {\nreturn\n}; f()", null);
    test("while (false) { 1 }\n2", 2);
    test("if (true) {\n if (true) \n1\n\n [1].map{ it }\n}\n", List.of(1));
    test("if (true) {\n if (true) {\n1\n}\n [1].map{ it }\n}\n", List.of(1));
    test("true or false\nand true", true);
    test("true \nor \nreturn", true);
    test("true \nor \nnot return", true);
    test("true or not\nreturn", true);
    test("true or not\nnot\nfalse\nand true", true);
    test("true &&\nfalse", false);
    test("true \n && \n false", false);
    test("true &&\n!\n!\n!\nfalse", true);
    test("def x=1; ++\nx", 2);
    test("def x=1; ++\n++\nx", 3);
    test("[a\n:\n1\n]", Map.of("a",1));
    test("1\n[1].map{ it }", List.of(1));
    test("'123'[2]", "3");
    test("'123'\n[2]", List.of(2));
    test("('123'\n[2])", "3");
    test("def x=[1,2,3]; x\n[2]", List.of(2));
    test("def x=[1,2,3]; def f={it}; f(x\n[2])", 3);
    test("def x = 1; ++\n++\nx\n++\n++\nx\nx", 3);
    testError("def x = 1; ++\n++\nx\n++\n++", "expected start of expression");
    test("[\n1\n,\n2\n,\n3\n]", List.of(1,2,3));
    test("def f(x\n,\ny\n)\n{\nx\n+\ny\n}\nf(1,2)", 3);
    testError("4\n/2", "unexpected end of file in string");
    test("4\n-3*2", -6);
    test("int\ni\n=\n3\n", 3);
    test("int\ni\n=\n1,\nj\n=\n2\n,k\ni+j", 3);
    test("for\n(\nint\ni\n=\n4-\n3\n,\nj\n=\n4/\n2\n;\ni\n<\n10\n;\ni++\n)\n;", null);
    test("def x = [1,2]; if\n(\nx\n[\n0\n]\n)\n4", 4);
    test("def x = [1,2]; def i = 0; while\n(\nx\n[\n0\n]\n>\n10\n||\ni++\n<2\n)\nx[\n2\n]\n=\n7\nx[2]", 7);
  }

  @Test public void eof() {
    testError("def ntimes(n,x) {\n for (int i = 0; i < n; i++) {\n", "unexpected eof");
  }

  @Test public void fib() {
    final String fibDef = "int cnt; def fib(def x) { /*cnt++;*/ x <= 2 ? 1 : fib(x-1) + fib(x-2) }";
    final String fibInt = "int cnt; int fib(int x) { /*cnt++;*/ x <= 2 ? 1 : fib(x-1) + fib(x-2) }";
    final String fibDefSleep = "int cnt; def fib(def x) { /*cnt++;*/ x <= 2 ? 1 : sleep(0, fib(x-1)) + sleep(0, fib(x-2)) }";
    final String fibIntSleep = "int cnt; int fib(int x) { /*cnt++;*/ x <= 2 ? 1 : sleep(0, fib(x-1)) + sleep(0, fib(x-2)) }";

    BiConsumer<Integer,Object> runFibDef = (num, expected) -> doTest(fibDef + "; def result = fib(" + num + "); //println cnt; result", expected);
    BiConsumer<Integer,Object> runFibInt = (num, expected) -> doTest(fibInt + "; def result = fib(" + num + "); //println cnt; result", expected);
    BiConsumer<Integer,Object> runFibSleepDef = (num, expected) -> doTest(fibDefSleep + "; def result = fib(" + num + "); //println cnt; result", expected);
    BiConsumer<Integer,Object> runFibSleepInt = (num, expected) -> doTest(fibIntSleep + "; def result = fib(" + num + "); //println cnt; result", expected);

//    debug=true;
    runFibDef.accept(40, 102334155);
    runFibInt.accept(40, 102334155);
    runFibDef.accept(20, 6765);
    runFibInt.accept(20, 6765);
    runFibSleepDef.accept(20, 6765);
    runFibSleepInt.accept(20, 6765);

    BiConsumer<Integer,Runnable> ntimes = (n,runnable) -> IntStream.range(0,n).forEach(i -> runnable.run());

    final int ITERATIONS = 0;
    Map<String,Object> globals  = new HashMap<>();
    var scriptDef = compile(fibDef + "; fib(40)");
    var scriptInt = compile(fibInt + "; fib(40)");
    var scriptSleepDef = compile(fibDefSleep + "; fib(20)");
    var scriptSleepInt = compile(fibIntSleep + "; fib(20)");
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      Compiler.runSync(scriptDef, globals);
      System.out.println("Def Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      Compiler.runSync(scriptInt, globals);
      System.out.println("Int Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      Compiler.runSync(scriptSleepDef, globals);
      System.out.println("sleep Def Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      Compiler.runSync(scriptSleepInt, globals);
      System.out.println("sleep Int Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
  }

}