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
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

  private boolean evaluateConstExprs = true;

  @BeforeEach private void setUp() {
    evaluateConstExprs = true;
  }

  BiConsumer<String,Object> test   = (code, expected) -> {
    assertEquals(expected instanceof String && ((String)expected).startsWith("#") ? new BigDecimal(((String)expected).substring(1)) : expected,
                 Compiler.run(code, new CompileContext().setEvaluateConstExprs(evaluateConstExprs), null));
  };

  BiConsumer<String,String> testFail = (code,expected) -> {
    try {
      Compiler.run(code, new CompileContext().setEvaluateConstExprs(evaluateConstExprs), null);
      fail("Expected CompileError");
    }
    catch (JacsalError e) {
      if (!e.getMessage().toLowerCase().contains(expected)) {
        fail("Message did not contain expected string '" + expected + "'. Message=" + e.getMessage());
      }
    }
  };

  @Test public void literals() {
    test.accept("42", 42);
    test.accept("0", 0);
    test.accept("1", 1);
    test.accept("2", 2);
    test.accept("3", 3);
    test.accept("4", 4);
    test.accept("5", 5);
    test.accept("6", 6);
    test.accept(Byte.toString(Byte.MAX_VALUE), (int)Byte.MAX_VALUE);
    test.accept(Integer.toString(Byte.MAX_VALUE + 1), (int)Byte.MAX_VALUE + 1);
    test.accept(Short.toString(Short.MAX_VALUE), (int)Short.MAX_VALUE);
    test.accept(Integer.toString(Short.MAX_VALUE + 1), (int)Short.MAX_VALUE + 1);
    test.accept(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    testFail.accept("" + (Integer.MAX_VALUE + 1L), "number too large");
    test.accept("" + ((long)Integer.MAX_VALUE + 1L) + "L", (long)Integer.MAX_VALUE + 1L);
    test.accept("1D", 1D);
    test.accept("1.0D", 1.0D);
    test.accept("0.0D", 0.0D);
    test.accept("0.1D", 0.1D);
    test.accept("1.0", "#1.0");
    test.accept("0.0", "#0.0");
    test.accept("0.1", "#0.1");
    test.accept("0.123456789123456789", "#0.123456789123456789");
    test.accept("''", "");
    test.accept("'123'", "123");
    test.accept("'1\\'23'", "1'23");
    test.accept("'''1'23'''", "1'23");
    test.accept("'''1'23''\\''''", "1'23'''");
    test.accept("'\\t\\f\\b\\r\\n'", "\t\f\b\r\n");
    test.accept("true", true);
    test.accept("false", false);
    test.accept("null", null);
  }

  @Test public void booleanConversion() {
    doBooleanConversion();   // Test const expressions
    evaluateConstExprs = false;
    doBooleanConversion();   // Now test compiled expressions
  }

  private void doBooleanConversion() {
    test.accept("!-1", false);
    test.accept("!1", false);
    test.accept("!2", false);
    test.accept("!0", true);
    test.accept("!-0", true);
    test.accept("!-1L", false);
    test.accept("!2L", false);
    test.accept("!0L", true);
    test.accept("!-0L", true);
    test.accept("!null", true);
    test.accept("!0.0", true);
    test.accept("!-0.0", true);
    test.accept("!1.0", false);
    test.accept("!-1.0", false);
    test.accept("!2.0", false);
    test.accept("!0.0D", true);
    test.accept("!-0.0D", true);
    test.accept("!1.0D", false);
    test.accept("!-1.0D", false);
    test.accept("!2.0D", false);
    test.accept("!'x'", false);
    test.accept("!''", true);
  }

  @Test public void constUnaryExpressions() {
    doConstUniaryExpressions();
    evaluateConstExprs = false;
    doConstUniaryExpressions();
  }

  private void doConstUniaryExpressions() {
    test.accept("-1", -1);
    test.accept("-1D", -1D);
    test.accept("-1L", -1L);
    test.accept("-1.0", "#-1.0");
    test.accept("+1", 1);
    test.accept("+1D", 1D);
    test.accept("+1L", 1L);
    test.accept("+1.0", "#1.0");
    test.accept("+-1", -1);
    test.accept("+-1D", -1D);
    test.accept("+-1L", -1L);
    test.accept("+-1.0", "#-1.0");
    test.accept("-+1", -1);
    test.accept("-+1D", -1D);
    test.accept("-+1L", -1L);
    test.accept("-+1.0", "#-1.0");
    test.accept("- -1", 1);
    test.accept("- -1D", 1D);
    test.accept("- -1L", 1L);
    test.accept("- -1.0", "#1.0");
    test.accept("-(-1)", 1);
    test.accept("-(-1D)", 1D);
    test.accept("-(-1L)", 1L);
    test.accept("-(-1.0)", "#1.0");
    testFail.accept("-true", "cannot be applied to type");
    testFail.accept("-'abc'", "cannot be applied to type");
    testFail.accept("+true", "cannot be applied to type");
    testFail.accept("+'abc'", "cannot be applied to type");
    test.accept("!true", false);
    test.accept("!false", true);
    test.accept("!!true", true);
    test.accept("!!false", false);
    test.accept("!(!!true)", false);
    test.accept("!!(!false)", true);
  }

  @Test public void simpleConstExpressios() {
    doSimpleConstExpressions();
    evaluateConstExprs = false;
    doSimpleConstExpressions();
  }

  private void doSimpleConstExpressions() {
    testFail.accept("1 + true", "non-numeric operand for right-hand side");
    testFail.accept("false + 1", "non-numeric operand for left-hand side");
    testFail.accept("1 - 'abc'", "non-numeric operand for right-hand side");
    testFail.accept("'abc' - 1", "non-numeric operand for left-hand side");
    testFail.accept("false + true", "non-numeric operand for left-hand side");
    testFail.accept("1 + null", "non-numeric operand for right-hand side");
    testFail.accept("null + 1", "non-numeric operand for left-hand side");
    test.accept("1 + 2", 3);
    test.accept("1 - 2", -1);
    test.accept("1 - -2", 3);
    test.accept("2 * 3", 6);
    test.accept("6 / 3", 2);
    test.accept("8 % 5", 3);
    test.accept("1L + 2L", 3L);
    test.accept("1L - 2L", -1L);
    test.accept("1L - -2L", 3L);
    test.accept("2L * 3L", 6L);
    test.accept("6L / 3L", 2L);
    test.accept("8L % 5L", 3L);
    test.accept("1 + 2L", 3L);
    test.accept("1L - 2", -1L);
    test.accept("1 - -2L", 3L);
    test.accept("2L * 3", 6L);
    test.accept("6 / 3L", 2L);
    test.accept("8L % 5", 3L);
    test.accept("8 % 5L", 3L);
    test.accept("1.0D + 2.0D", 3.0D);
    test.accept("1.0D - 2.0D", -1.0D);
    test.accept("1.0D - -2.0D", 3.0D);
    test.accept("2.0D * 3.0D", 6D);
    test.accept("6.0D / 3.0D", 2.0D);
    test.accept("8.0D % 5.0D", 3D);
    test.accept("1L + 2.0D", 3.0D);
    test.accept("1.0D - 2L", -1.0D);
    test.accept("1L - -2.0D", 3.0D);
    test.accept("2.0D * 3L", 6D);
    test.accept("6L / 3.0D", 2.0D);
    test.accept("8.0D % 5L", 3D);
    test.accept("1.0 + 2.0", "#3");
    test.accept("1.0 - 2.0", "#-1");
    test.accept("1.0 - -2.0", "#3");
    test.accept("2.0 * 3.0", "#6");
    test.accept("6.0 / 3.0", "#2");
    test.accept("6.0 / 7.0", "#0.85714285714285714286");
    test.accept("8.0 % 5.0", "#3");
    test.accept("1 + 2D", 3D);
    test.accept("1D - 2", -1D);
    test.accept("1 - -2D", 3D);
    test.accept("2D * 3", 6D);
    test.accept("6 / 3D", 2D);
    test.accept("8D % 5", 3D);
    test.accept("8 % 5D", 3D);
    test.accept("1 + 2.0", "#3");
    test.accept("1.0 - 2", "#-1");
    test.accept("1 - -2.0", "#3");
    test.accept("2.0 * 3", "#6");
    test.accept("6 / 3.0", "#2");
    test.accept("8.0 % 5", "#3");
    test.accept("8 % 5.0", "#3");
    test.accept("1L + 2.0", "#3");
    test.accept("1.0 - 2L", "#-1");
    test.accept("1L - -2.0", "#3");
    test.accept("2.0 * 3L", "#6");
    test.accept("6L / 3.0", "#2");
    test.accept("8.0 % 5L", "#3");
    test.accept("8L % 5.0", "#3");
    test.accept("1D + 2.0", "#3");
    test.accept("1.0 - 2D", "#-1");
    test.accept("1D - -2.0", "#3");
    test.accept("2.0 * 3D", "#6");
    test.accept("6D / 3.0", "#2");
    test.accept("8.0 % 5D", "#3");
    test.accept("8D % 5.0", "#3");
  }

  @Test public void constExprArithmeticPrecedence() {
    doConstExprArithmeticPrecedence();
    evaluateConstExprs = false;
    doConstExprArithmeticPrecedence();
  }

  private void doConstExprArithmeticPrecedence() {
    test.accept("1 + -2 * -3 + 4", 11);
    test.accept("1 + (2 + 3) * 4 - 5", 16);
    test.accept("13 + 12 % 7", 18);
    test.accept("13 + 12 % 7 - 3", 15);
  }

  @Test public void constStringConcatenation() {
    doConstStringConcatenation();
    evaluateConstExprs = false;
    doConstStringConcatenation();
  }

  private void doConstStringConcatenation() {
    testFail.accept("'abc' - '123'", "non-numeric operand");
    test.accept("'abc' + '123'", "abc123");
    test.accept("'abc' + null", "abcnull");
    test.accept("null + 'abc'", "nullabc");
    test.accept("'abc' + 'def' + 'ghi'", "abcdefghi");
    test.accept("'abc' + ('1' + '2' + '3') + 'def'", "abc123def");
    test.accept("'' + 'abc'", "abc");
    test.accept("'abc' + ''", "abc");
  }

  @Test public void constStringRepeat() {
    doConstStringRepeat();
    evaluateConstExprs = false;
    doConstStringRepeat();
  }

  private void doConstStringRepeat() {
    test.accept("'abc' * 2", "abcabc");
    test.accept("'abc' * 0", "");
    testFail.accept("'abc' * -1", "string repeat count must be >= 0");
  }
}