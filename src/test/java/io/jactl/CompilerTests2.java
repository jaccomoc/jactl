/*
 * Copyright © 2022,2023,2024  James Crawford
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.function.BiConsumer;

public class CompilerTests2 extends BaseTest {

//  @BeforeEach
//  public void setUp() {
//    super.setUp();
//    isAsync = false;
//  }

  @Test public void questionQuestion() {
    test("?? true", true);
    test("?? (byte)1", true);
    test("?? 1", true);
    test("?? (1 if false)", false);
    test("?? 1D", true);
    test("?? 1.0", true);
    test("?? 'abc'", true);
    test("?? 'abc' && ?? null || ?? null", false);
    test("?? false", true);
    test("?? null", false);
    test("?? ?? null", true);
    test("def x; ?? x", false);
    test("def x = []; ?? x", true);
    test("def x = [:]; ?? x", true);
    test("def x = [:]; ?? x.a", false);
    test("def x = [:]; ?? x?.a", false);
  }

  @Test public void explicitReturnFromScript() {
    test("double v = 2; return v", 2D);
    test("byte v = 2; { v = v + 1; return v }; v = v + 3", (byte)3);
    test("int v = 2; { v = v + 1; return v }; v = v + 3", 3);
    test("String v = '2'; { v = v + 1; { return v }}", "21");
    test("var v = 2.0; { v = v + 1; { return v }}", "#3.0");
    test("const v = 2.0; { def x = v + 1; { return x+v }}", "#5.0");
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
    test("byte v = 2; { v = v + 1; v }", (byte)3);
    test("int v = 2; { v = v + 1; v }", 3);
    test("String v = '2'; { v = v + 1; { v }}", "21");
    test("var v = 2.0; { v = v + 1; { v }}", "#3.0");
    test("const v = 2.0; { def x = v + 1; { x+v }}", "#5.0");
    test("def v = '2'; { v = v + 1; { v }}", "21");
  }

  @Test
  public void constExprArithmeticPrecedence() {
    alwaysEvalConsts = true;
    test("1 + -2 * -3 + 4", 11);
    test("1 + (2 + 3) * 4 - 5", 16);
    test("13 + 12 %% 7L", 18L);
    test("13 + 12 %% 7 - 3", 15);
    testError("13 + 12 %% ++6 - 3", "cannot modify a constant value");
    test("13 + 12 % 7L", 18L);
    test("13 + 12 % 7 - 3", 15);
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
    test("'abc' + 1", "abc1");
    test("'abc' + (byte)1", "abc1");
    test("'abc' + ('xxx' if true)", "abcxxx");
    test("'abc' + ('xxx' if false)", "abcnull");
  }

  @Test
  public void constStringRepeat() {
    test("'abc' * (byte)2", "abcabc");
    test("'abc' * 2", "abcabc");
    test("'abc' * 0", "");
    testError("'abc' * -1", "string repeat count must be >= 0");
  }

  @Test public void defaultValues() {
    test("boolean x", false);
    test("boolean x; x", false);
    test("byte x", (byte)0);
    test("byte x; x", (byte)0);
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
    test("byte x = 1; ~x; 3", 3);
    test("int x = 1; ~x; 3", 3);
    test("def x = 1; ~x; 3", 3);
    test("boolean x = true; !x; 3", 3);
    test("def x = true; !x; 3", 3);
    test("def x = 1; !x; 3", 3);
    test("byte x = 1; -x; 3", 3);
    test("int x = 1; -x; 3", 3);
    test("def x = 1; -x; 3", 3);
  }

  @Test
  public void prefixIncOrDec() {
    alwaysEvalConsts = true;
    testError("++null", "null value");
    testError("--null", "null value");
    testError("++(byte)1", "cannot modify a constant value");
    testError("--(byte)1", "cannot modify a constant value");
    testError("++1", "cannot modify a constant value");
    testError("--1", "cannot modify a constant value");
    testError("++1L", "cannot modify a constant value");
    testError("--1L", "cannot modify a constant value");
    testError("++1D", "cannot modify a constant value");
    testError("--1D", "cannot modify a constant value");
    testError("++1.0", "cannot modify a constant value");
    testError("--1.0", "cannot modify a constant value");
    test("byte x = 1; ++x", (byte)2);
    test("byte x = 1; --x", (byte)0);
    test("byte x = 3; --x + --x", (byte)3);
    test("byte x = 3; ++x + ++x", (byte)9);
    test("int x = 1; ++x", 2);
    test("int x = 1; --x", 0);
    test("int x = 3; --x + --x", 3);
    test("int x = 3; ++x + ++x", 9);
    test("long x = 1; ++x", 2L);
    test("long x = 1; --x", 0L);
    test("long x = 3; --x + --x", 3L);
    test("long x = 3; ++x + ++x", 9L);
    test("double x = 1; ++x", 2D);
    test("double x = 1; --x", 0D);
    test("double x = 1.5D; --x", 0.5D);
    test("double x = 3; --x + --x", 3D);
    test("double x = 3.5D; --x + --x", 4D);
    test("double x = 3.5D; ++x + ++x", 10D);
    test("Decimal x = 1; ++x", "#2");
    test("Decimal x = 1.5; ++x", "#2.5");
    test("Decimal x = 1; --x", "#0");
    test("Decimal x = 1.5; --x", "#0.5");
    test("Decimal x = 3; --x + --x", "#3");
    test("Decimal x = 3.5; --x + ++x", "#6.0");

    test("def x = (byte)1; ++x", (byte)2);
    test("def x = (byte)1; --x", (byte)0);
    test("def x = (byte)3; --x + --x", (byte)3);
    test("def x = (byte)3; ++x + ++x", (byte)9);
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
    alwaysEvalConsts = true;
    testError("null++", "null value encountered");
    testError("null--", "null value encountered");
    testError("(byte)1++", "cannot modify a constant value");
    testError("(byte)1--", "cannot modify a constant value");
    testError("1++", "cannot modify a constant value");
    testError("1--", "cannot modify a constant value");
    testError("1L++", "cannot modify a constant value");
    testError("1L--", "cannot modify a constant value");
    testError("1D++", "cannot modify a constant value");
    testError("1D--", "cannot modify a constant value");
    testError("1.0++", "cannot modify a constant value");
    testError("1.0--", "cannot modify a constant value");
    test("byte x = 1; x++", (byte)1);
    test("byte x = 1; x--", (byte)1);
    test("byte x = 1; x++; x", (byte)2);
    test("byte x = 1; x--; x", (byte)0);
    test("byte x = 3; x-- + x--", (byte)5);
    test("byte x = 3; x-- + x--; x", (byte)1);
    test("byte x = 3; x++ + x++", (byte)7);
    test("byte x = 3; x++ + x++; x", (byte)5);
    test("int x = 1; x++", 1);
    test("int x = 1; x--", 1);
    test("int x = 1; x++; x", 2);
    test("int x = 1; x--; x", 0);
    test("int x = 3; x-- + x--", 5);
    test("int x = 3; x-- + x--; x", 1);
    test("int x = 3; x++ + x++", 7);
    test("int x = 3; x++ + x++; x", 5);
    test("long x = 1; x++", 1L);
    test("long x = 1; x++; x", 2L);
    test("long x = 1; x--", 1L);
    test("long x = 1; x--; x", 0L);
    test("long x = 3; x-- + x--", 5L);
    test("long x = 3; x-- + x--; x", 1L);
    test("long x = 3; x++ + x++", 7L);
    test("long x = 3; x++ + x++; x", 5L);
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

    test("def x = (byte)1; x++", (byte)1);
    test("def x = (byte)1; x--", (byte)1);
    test("def x = (byte)1; x++; x", (byte)2);
    test("def x = (byte)1; x--; x", (byte)0);
    test("def x = (byte)3; x-- + x--", (byte)5);
    test("def x = (byte)3; x-- + x--; x", (byte)1);
    test("def x = (byte)3; x++ + x++", (byte)7);
    test("def x = (byte)3; x++ + x++; x", (byte)5);
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
    test("def x = 1; -- -- -- ++x--", -1);

    test("def x = 1; def y = 3; x + --y * ++y++ - 2", 5);
    test("def x = 1; def y = 3; x + --y * ++++y++ - 2", 7);
    test("def x = 1; def y = 3; x + --y * ++++y++ - 2; y", 3);
    test("def x = (byte)1; def y = (byte)3; x + --y * ++y++ - (byte)2", (byte)5);
    test("def x = (byte)1; def y = (byte)3; x + --y * ++++y++ - (byte)2", (byte)7);
    test("def x = (byte)1; def y = (byte)3; x + --y * ++++y++ - (byte)2; y", (byte)3);

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
    testError("def x = 2; { int x = 3; String x = 'abc'; }", "clashes with previously declared variable");
    testError("int f() { def x = 3; def x = 4; }", "clashes with previously declared variable");
    testError("int f(x) { def x = 3; }", "clashes with previously declared variable");
  }

  @Test public void constVars() {
    alwaysEvalConsts = true;
    testError("const int i = 1; i = 2", "cannot modify a constant");
    testError("const int i = 1; i++", "cannot modify a constant");
    testError("const int i = 1; ++i", "cannot modify a constant");
    testError("const int i; i", "initialiser expression required");
    testError("const var i; i", "unexpected token 'var'");
    testError("const def i; i", "unexpected token 'def'");
    test("var i = 1", 1);
    test("var X = 1", 1);
    test("const int i = 1; i", 1);
    test("const i = 1", 1);
    test("const i = 1; i", 1);
    test("var i = (byte)-1", (byte)-1);
    test("var X = (byte)-1", (byte)-1);
    test("const byte i = (byte)-1; i", (byte)-1);
    test("const i = (byte)-1", (byte)-1);
    test("const i = (byte)-1; i", (byte)-1);
    testError("const List i = [1]; i", "constants can only be simple types");
    testError("const int[] i = [1]; i", "constants can only be simple types");
    testError("const i; i", "initialiser expression required");
    test("const int X = 1; X", 1);
    test("const X = 1; X", 1);
    testError("const X = [1,2,3]; X", "simple constant value");
    test("def f() { const int i = 1; i }; f()", 1);
    testError("def f() { const def i = 1; i }; f()", "unexpected token 'def'");
    test("def f() { const i = 1; i }; f()", 1);
    test("const x = 2; def f() { const i = 1; i+x }; f()", 3);
    test("const X = 2; def f() { const i = 1; i+X }; f()", 3);
    test("const X = 2; def f() { const X = 1; X }; f()", 1);
    test("class X{}; const X = 1", 1);
    testError("class X{}; const X i = 1", "can only be simple types");
    testError("class X{}; const X[] i = 1", "can only be simple types");
    test("const byte i = 1; i + 1", 2);
    test("const i = (byte)1; i + 1", 2);
    test("const int i = 1; i + 1", 2);
    test("const i = 1; i + 1", 2);
    test("const long i = 1; i + 1", 2L);
    test("const i = 1L; i + 1", 2L);
    test("const double i = 1; i + 1", 2D);
    test("const i = 1D; i + 1", 2D);
    test("const Decimal i = 1; i + 1", "#2");
    test("const Decimal i = 1D; i + 1", "#2.0");
    test("const Decimal i = 1L; i + 1", "#2");
    test("const i = (Decimal)1; i + 1", "#2");
    test("const i = 1.0; i + 1", "#2.0");
    test("const boolean i = true; i && true", true);
    test("const i = true; i && true", true);
    test("const String i = 'abc'; i + 'x'", "abcx");
    test("const i = 'abc'; i + 'x'", "abcx");
  }

  @Test public void exprStrings() {
    testError("/\n/int\n", "unrecognised regex modifier 't'");
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

    testError("def x = 3; \"\"\"$\"\"\" + 'abc'", "unexpected character '\"'");
    testError("def x = 3; \"$\" + 'abc'", "unexpected character '\"'");
    testError("def x = 3; \"$", "unexpected end of file");
    testError("def x = 3; \"\"\"$", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}$\"\"\" + 'abc'", "unexpected character '\"'");
    testError("def x = 3; \"$x=${\"\"\"${1+2}${\"\"\"}\"", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${\"\"\" + 'abc'", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${x + \"\"\" + 'abc'", "unexpected end of file");
    testError("def x = 3; \"\"\"${1+2}${x + \"\"\"} + 'abc'", "unexpected end of file");
    testError("def x = 3; \"$x=${\"\"\"${1+2}${\"\"\"}\"", "unexpected end of file");
    testError("def x = 3; \"$x=${\"\"\"${1+2}$\"\"\"}\"", "expecting start of identifier");
    testError("def x = 3; \"\"\"${1+2}$\"\"\" + 'abc'", "expecting start of identifier");

    testError("def x = 3; \"$x=${\"\"\"${1\n+2}\"\"\"}\"", "new line not allowed");
    testError("def x = 3; \"$x=${\"\"\"\n${1\n+2}\n\"\"\"}\"", "unexpected new line");

    test("def x = (byte)1; /$x/", "1");
    test("def x = 1; /$x/", "1");
    test("def x = 1; def y = /$x/; y", "1");
    test("def x = 1; def y = /\\$x/; y", "\\$x");
    test("def x = 1; def y = /$x\\//; y", "1/");
    test("def x = /${(byte)1}/; x", "1");
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

    test("Map x = [a:[b:(byte)1]]; x.a.\"${'b'}\"", (byte)1);
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
    test("def x = 'xx'; \"${x += 'yy' and return x}xx\"", "xxyyxx");
    test("def x = 'xx'; \"${x += 'yy'; return x}\"; x", "xxyy");
    test("def it = 'xx'; \"${it += 'yy'; return it}\"; it", "xxyy");
    test("\"x = ${ def x = 1 + 2; x}\"", "x = 3");
    test("\"5 pyramid numbers: ${ def p(n){ n == 1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }\"", "5 pyramid numbers: 1, 5, 14, 30, 55");
    test("def p(x){x*x}; \"5 pyramid numbers: ${ def p(n){ n == 1 ? 1 : n*n + p(n-1) }; [1,2,3,4,5].map{p(it)}.join(', ') }: ${p(3)}\"", "5 pyramid numbers: 1, 5, 14, 30, 55: 9");
    test("['a','b'].map{\"\"\"\"$it=\" + $it\"\"\" }.join(' + \", \" + ')", "\"a=\" + a + \", \" + \"b=\" + b");

    testError("XYZ.fromJson(\"{\\", "unexpected end-of-file");
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
    test("def it = 'xyyz'; /yy/r and it = 'baa'; /aa$/r and return 'xxx'", "xxx");
    test("def it = 'xyz'; String x = /x/; x", "x");
    test("def it = 'xyz'; def x = /x/; x == 'x'", true);
    test("def it = 'xyz'; def x = /x/r; x", true);
    testError("def x = /x/r; x", "no 'it' variable");
    testError("def it = 'xyz'; boolean x = /x/; x", "regex string used in boolean context");
    test("def it = 'xyz'; boolean x = /x/r; x", true);
    test("def it = 'xyz'; boolean x = /a/r; x", false);
    test("def str = 'xyz'; def x; str =~ /(x)(y)(z)/ and !(str =~ /abc/) and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and !/abc/r and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and x = \"$3$2$1\"; x", "zyx");
    test("def it = 'xyz'; def x; !/(x)(y)(z)/r or x = \"$3$2$1\"; x", "zyx");
    test("def it = 'xyz'; def x = /x/r; { x ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /x/; { x == 'x' ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /x/r; { x == 'x' ? 'match' : 'nomatch' }()", "nomatch");
    test("def it = 'xyz'; def x = /X/i; { x ? 'match' : 'nomatch' }()", "match");
    test("def it = 'xyz'; def x = /X/i; { x == 'x' ? 'match' : 'nomatch' }()", "nomatch");
    test("['abc','xzt','sas',''].map{/a/r ? true : false}", Utils.listOf(true,false,true,false));
    test("['abc','xzt','sas',''].map{ if (/a/r) true else false}", Utils.listOf(true,false,true,false));
    test("['abc','xzt','sas',''].map{ /a/r and return true; false}", Utils.listOf(true,false,true,false));

    testError("def it = 'abc'; def x; /a/ and x = 'x'; x", "regex string used in boolean context");
    testError("def it = 'abc'; def x; /a/ ? true : false", "regex string used in boolean context");
    testError("def it = 'abc'; def x; if (/a/) true else false", "regex string used in boolean context");
    testError("def it = 'abc'; def x; while (/a/) ;", "regex string used in boolean context");
    testError("def it = 'abc'; def x; for (; /a/;) ;", "regex string used in boolean context");
    test("def x = 'abc'; def y; x =~ /a/ and y = 'y'; y", "y");
    test("def x = 'a=b'; x =~ /=b/", true);
    test("'^ cd' =~ /^\\^ cd/", true);
    test("'$ cd' =~ /^\\$/", true);
    test("'''\n:''' =~ /^$.:$/ms", true);
    test("def x = 'aa'; 'abcaa' =~ /$x/", true);
    test("def x = 'aa'; 'abcaa' =~ /\\$x/", false);
    test("def x = 'aa'; 'abc$x' =~ /\\$x/", true);
    test("def x = 'aa'; 'abc$x' =~ /\\$x$/", true);

    test("['a','b','c'].map{/(.)/r\n[name:$1]\n }.map{it.name}", Utils.listOf("a","b","c"));
    test("def x = ['a','b','c'].map{/(.)/r\n[name:$1]\n }.map{it.name}; x", Utils.listOf("a","b","c"));

    test("def it = 'ab\\ncd'; /b$/mr", true);
    test("def it = 'ab\\n#d'; /b$\\n#/mr", true);
    test("def it = 'ab\\ncd'; /b\\$\\nc/mr", false);
    test("def it = 'ab\\ncd'; /b$.c/smr", true);
    test("def it = 'ab\\ncd'; /b\\$.c/smr", false);
    test("def it = 'ab\\ncd'; /b.c/smr", true);

    testError("1 =~ /abc/", "cannot convert");
    testError("def x = 1; x =~ /abc/", "cannot convert");
    test("'abc' =~ /\\\\/", false);
    test("'ab\\\\c' =~ /\\\\/", true);
    test("'ab\\\\c' =~ s/\\\\/x/gr", "abxc");
    test("def it = '=A'; it =~ /=a/i", true);
    test("def it = '='; /=/r", true);
  }

  @Test public void regexCaptureVars() {
    test("def x = ''; 'abc' =~ /$x/", true);
    test ("def x = 'abc'; x =~ /${''}/", true);
    testError("def x; 'abc' =~ x", "null regex");
    test("def x; x =~ /abc/", false);
    test("'abcaaxy' =~ /(a+)/", true);
    test("'bcaaxy' =~ /(a+)/ and return $1", "aa");
    test("def it = 'bcaaxy'; /(a+)/r and return $1", "aa");
    test("def it = 'bcaaxy'; /(a+)/r and return $2", null);
    testError("def it = 'bcaaxy'; /(a+)/r and return $1000", "capture variable number too large");
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
    test("def it = 'abc'; /a/r; $0", "a");
    test("def it = 'abc'; /a(bc)/r; $0 + $1", "abcbc");
    test("def it = 'abc'; /a(bc)/r; $2", null);
    test("def str = 'xyz'; def x; str =~ /(x)(y)(z)/ and str =~ /x/ and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and /x/r and x = \"$3$2$1\"; x", "nullnullnull");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and { x = \"$3$2$1\" }(); x", "zyx");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and { x = \"$3$2$1\"; /a(b)/r and x += $1 }('abc'); x", "zyxb");
    test("def it = 'xyz'; def x; /(x)(y)(z)/r and { x = \"$3$2$1\"; /a(b)/r and x += $1 }('abc'); x += $3; x", "zyxbz");
    test("def it = 'xyz'; def x; it =~ sleep(0,/(x)(y)(z)/) and { x = \"$3$2$1\" }(); x", "zyx");

    test("'a123b' =~ /.(\\d+)./; $1", "123");
    test("'a123b' =~ /.(\\d+)./n; $1", 123L);
    test("'a123.4b' =~ /.([\\d.]+)./n; $1", "#123.4");
    test("'a-1234b' =~ /.([\\d.-]+)./n; $1", -1234L);
    test("'a-123.4b' =~ /.([\\d.-]+)./n; $1", "#-123.4");
    test("'a123.4.5b' =~ /.([\\d.]+)./n; $1", "123.4.5");
    test("'a12345123451234512341234123412341234123412341234b' =~ /.([\\d.]+)./n; $1", "12345123451234512341234123412341234123412341234");
    test("'1bc' =~ /(\\d)(.*(\\d))?/n; $2", null);
    test("def f(x){x}; 'a123b' =~ /.(\\d+)./n; f($1)", 123L);
  }

  @Test public void regexGlobalMatch() {
    testError("String x = 'abc'; def y = ''; x =~ /([a-z])/g and y += $1; x =~ /([a-z])/g and y += $1; y", "cannot use 'g' modifier");
    test("String x = 'abc'; def y = ''; while (x =~ /([a-z])/g) { y += $1 }; y", "abc");
    testError("String x = 'abc'; def y = ''; while (y =~ /^$|a$/g && x =~ /([a-z])/g) { y += $1 }; y", "can only occur once");
    testError("def it = 'abc'; def y = ''; while (y =~ /^$|a$/g && /([a-z])/g) { y += $1 }; y", "can only occur once");
    testError("def it = 'abc'; def y = ''; while (/^$|a$/g && /([a-z])/g) { y += $1 }; y", "can only occur once");
    test("String x = 'abc'; def y = ''; while (y =~ /^$|a$/ && x =~ /([a-z])/g) { y += $1 }; y", "ab");
    test("String x = 'abc'; def y = 0; while (y.toString() =~ /[0-2]/ && x =~ /([a-z])/g && y.toString() !~ /x/) { y++ }; y", 3);
    test("def it = 'abc'; def y = ''; while (/([a-z])/g) { y += $1 }; y", "abc");
    test("def it = 'abc'; def y = ''; while (y =~ /^$|a$/ && /([a-z])/g) { y += $1 }; y", "ab");
    test("def it = 'abc'; def y = 0; while (y.toString() =~ /[0-2]/ && /([a-z])/g && y.toString() !~ /x/) { y++ }; y", 3);

    testError("def it = 'abc'; def y = ''; while (/([a-z])/g) { y += $1 }; y += $1", "in scope where no regex match has occurred");
    testError("def it = 'abc'; def y = ''; for (int i = 0; /${''}/g && i < 10; i++) { y += $1 }; y += $1", "in scope where no regex match has occurred");
    test("def it = 'abc'; def y = ''; for (int i = 0; /${''}/g && i < 3; i++) { y += $1 }; y", "nullnullnull");
    test("def it = 'abcd'; def y = ''; for (int i = 0; /([a-z])./g && i < 10; i++) { y += $1 }; y", "ac");
    test("def it = 'abcd'; def y = ''; for (int i = 0; /([A-Z])./ig && i < 10; i++) { y += $1 }; y", "ac");
    test("def x = 'abcd'; def y = ''; for (int i = 0; x =~/([A-Z])./ig && i < 10; i++) { y += $1 }; y", "ac");
    test("def it = 'abcd'; def x = ''; while (/([a-z])/gr) { x += $1 }; while (/([A-Z])/ig) { x += $1 }; x", "abcdabcd");
    test("def it = 'abc'; def x = ''; while (/([a-z])/gr) { x += $1; while (/([A-Z])/ig) { x += $1 } }; x", "aabcbabccabc");
    test("def it = null; def x = 'empty'; while (/([a-z])/gr) { x += $1; while (/([A-Z])/ig) { x += $1 } }; x", "empty");
    test("def x = 'abcde'; int i = 0; while (x =~ /([ace])/g) { i++; x = 'aaaa' }; i", 5);
    test("def x = 0; def f() { '123' }; while(f() =~ /(\\d)/ng) { x+= $1 }; x", 6L);
    testError("def x = 0; int i = 0; def f() { die if i++ > 0; '123' }; while(f() =~ /(\\d)/ng) { x+= $1 }; x", "script death");
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
    test("def x = [a:'abaac']; x.a =~ s/a/x/rg", "xbxxc");
    test("def x = [a:'abaac']; x.a =~ s/a/x/rg; x.a", "abaac");
    test("def x = [a:'abaac']; x.a =~ s/a/x/g; x.a", "xbxxc");
    testError("def x = [a:'abc']; (x.a + 'xyz') =~ s/a/x/; x.a", "invalid lvalue");
    test("def it = 'abaac'; s/a//g", "bc");
    test("def it = '=ab=a=ac'; s/=a//g", "bc");
    test("def it = '=ab=a=ac'; s/=a/=/g", "=b==c");
    test("def it = 'abaac'; s/a//g; it", "bc");
    test("def it = 'abaac'; s///g", "abaac");
    test("def it = 'abaac'; s///g; it", "abaac");
    test("def it = 'abaac'; s//a/g", "aaabaaaaaca");
    test("def it = 'abaac'; s//a/g; it", "aaabaaaaaca");
    testError("1 =~ s/abc/xyz/", "invalid lvalue");
    testError("def x = 1; x =~ s/abc/xyz/", "cannot convert");
    testError("'a123c' =~ /(\\d+)/; $1 =~ s/123/abc/; $1", "invalid lvalue");
    test("def it = '123456'; s/([0-9])/${ $1 * $1 }/rng", "149162536");
    test("def it = 'a1b2c3def4g56'; s/([0-9])/${ $1 * $1 }/rng", "a1b4c9def16g2536");
    test("def it = 'abcdef'; s/([a-z])/${ $1 + $1 }/rg", "aabbccddeeff");
    test("def it = '   *# parseScript -&gt; packageDecl? script;'; s/^.*#//; s/^ *([^ ]*) *-&gt;/${ $1.toUpperCase(1) }->/g; $1 == null", true);
  }

  @Test public void regexSubstituteExprString() {
    test("def it = 'abaAc'; s/(A)/x$1/i; ", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/i; it", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/i; ", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/i; x", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/i; ", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/i; x.a", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/ri; ", "xabaAc");
    test("def it = 'abaAc'; s/(A)/x$1/ri; it", "abaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/ri; ", "xabaAc");
    test("def x = 'abaAc'; x =~ s/(A)/x$1/ri; x", "abaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/ri; ", "xabaAc");
    test("def x = [a:'abaAc']; x.a =~ s/(A)/x$1/ri; x.a", "abaAc");
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
    testError("def root = 'abc'; def m=[:]; root =~ s/abc/\"${m{'abc'}}\"/\n", "object of type map");
    test("def x = 'This SentenCe has Capital letTErs'; x =~ s/([A-Z][a-z])/${$1 =~ /^((.*))$/; $2.toLowerCase()}/g; x", "this sentence has capital letTers");
    test("def x = 'This'; x =~ s/[a-z]/${1+2}/g; x = 'This'; x =~ s/[a-z]/${1+2}/rg; x = 'This'; x =~ s/[a-z]/${1+2}/g; x", "T333");
    test("'This SentenCe has Capital letTErs' =~ s/([A-Z][a-z])/${$1 =~ /^((.*))$/; $2.toLowerCase()}/rg", "this sentence has capital letTers");
    test("'This' =~ s/[a-z]/${1+2}/rg; 'This' =~ s/[a-z]/${1+2}/rg; 'This' =~ s/[a-z]/${1+2}/rg", "T333");
    test("def x = 'This SentenCe has Capital letTErs'; sleep(0, x =~ s/([A-Z][a-z])/${$1 =~ /^((.*))$/; $2.toLowerCase()}/g); x", "this sentence has capital letTers");
    test("'This SentenCe has Capital letTErs' =~ s/([A-Z][a-z])/${sleep(0,$1) =~ sleep(0,/^((.*))$/); sleep(0,sleep(0,$2).toLowerCase())}/rg", "this sentence has capital letTers");
    test("def f(x,y,z){ sleep(0,0) if false; z }; f(null, 13, 'abc' =~ s/([A-Z][a-z])/${f(null, 17,$1)}/rg)", "abc");
    test("def f(x,y,z){ z }; f(null, 13, 'abc' =~ s/([A-Z][a-z])/${f(null, 17,$1)}/rg)", "abc");
    test("'12345' =~ s/([0-9])/${$1+$1}/rng", "246810");
    testError("'12345' =~ s/([0-9])/${$1+$1}/ng", "invalid lvalue");
    testError("1 =~ s/([0-9])/${$1+$1}/ng", "invalid lvalue");
    test("'abcdefg' =~ s/(..)/${ $1 =~ s/(.)/${$1 + $1}/rg }/rg", "aabbccddeeffg");
    test("'abcdefg' =~ s/(..)/${ $1 + ('abcdefg' =~ s/(..)/x/rg) }/rg", "abxxxgcdxxxgefxxxgg");
    testError("for(int i = 0; i < 10; i++) { 'abc' =~ s/(.)/${ continue }/rg }", "must be within a while/for loop");
    test("def x = 7; for (int i=0;i<10;i++) { i == 3 and continue\ni in [1,2,3] and x++\ni==4 and break\nx++}\nx", 12);
  }

  @Test public void doBlock() {
    test("true and do { true } and return true", true);
    test("true and do { for(byte i=0;i<10;i++); false } and return true", false);
    test("true and do { for(byte i=0;i<10;i++); true } and return true", true);
    test("true and do { for(int i=0;i<10;i++); false } and return true", false);
    test("true and do { for(int i=0;i<10;i++); if (true) 1 == 1 } and return true", true);
    test("true and do { for(int i=0;i<10;i++); return false } and return true", false);
    test("def it = 'abc'; /ab/r and do { /c/r and return false } and return true", false);
    test("def x; def it = 'abc'; /ab/r and do { it = 'xyz'; x = 'x' } and return \"$it$x\"", "xyzx");
    test("do { 1 }", 1);
    test("{ do { 1 } }", 1);
    test("do { do { 1 } }", 1);
    test("def f = { do { 1 } }; f()", 1);
    test("def f() { do { 1 } }; f()", 1);
    test("do { if (true) 1 }", 1);
    test("do { int x = 1 }", 1);
    test("do { def x = 1 }", 1);
    test("do { 1L }", 1L);
    test("{ do { 1L } }", 1L);
    test("do { do { 1L } }", 1L);
    test("def f = { do { 1L } }; f()", 1L);
    test("def f() { do { 1L } }; f()", 1L);
    test("do { if (true) 1L }", 1L);
    test("do { long x = 1L }", 1L);
    test("do { def x = 1L }", 1L);
    test("do { 1D }", 1D);
    test("{ do { 1D } }", 1D);
    test("do { do { 1D } }", 1D);
    test("def f = { do { 1D } }; f()", 1D);
    test("def f() { do { 1D } }; f()", 1D);
    test("do { if (true) 1D }", 1D);
    test("do { double x = 1D }", 1D);
    test("do { def x = 1D }", 1D);
    test("do { 1.0 }", "#1.0");
    test("{ do { 1.0 } }", "#1.0");
    test("do { do { 1.0 } }", "#1.0");
    test("def f = { do { 1.0 } }; f()", "#1.0");
    test("def f() { do { 1.0 } }; f()", "#1.0");
    test("do { if (true) 1.0 }", "#1.0");
    test("do { Decimal x = 1.0 }", "#1.0");
    test("do { def x = 1.0 }", "#1.0");
    test("do { 'abc' }", "abc");
    test("{ do { 'abc' } }", "abc");
    test("do { do { 'abc' } }", "abc");
    test("def f = { do { 'abc' } }; f()", "abc");
    test("do { if (true) 'abc' }", "abc");
    test("do { String x = 'abc' }", "abc");
    test("do { def x = 'abc' }", "abc");
    test("def f() { do { { -> 'abc' } } }; f()()", "abc");
    test("def f() { sleep(0, do { { -> 'abc' } }) }; f()()", "abc");
    test("do { [1,2,3] }", Utils.listOf(1,2,3));
    test("{ do { [1,2,3] } }", Utils.listOf(1,2,3));
    test("do { do { [1,2,3] } }", Utils.listOf(1,2,3));
    test("def f = { do { [1,2,3] } }; f()", Utils.listOf(1,2,3));
    test("do { if (true) [1,2,3] }", Utils.listOf(1,2,3));
    test("do { List x = [1,2,3] }", Utils.listOf(1,2,3));
    test("do { def x = [1,2,3] }", Utils.listOf(1,2,3));
    test("def f() { do { { -> [1,2,3] } } }; f()()", Utils.listOf(1,2,3));
    test("def f() { sleep(0, do { { -> [1,2,3] } }) }; f()()", Utils.listOf(1,2,3));
    test("do {}", null);
    test("do { do {} }", null);
    test("do {while(false){}}", null);
    test("do { do {while(false){}} }", null);
    test("do {for(int i = 0; i < 10; i++){}}", null);
    test("do { if (true) 1 == 1 }", true);
    test("do { if (false) 1 == 1 }", null);
    test("do { if (true) 1 == 1 } and true", true);
    test("do { if (true) 1 == 1 } if true", true);
    test("do { if (true) 1 == 1 } and true if true", true);
    test("class X{int i}; do { if (true) new X(1) } == new X(1)", true);
    test("class X{int i}; do { if (false) new X(1) } == null", true);
    test("class X{int i}; do { do{ if (true) new X(1) } } == new X(1)", true);
    test("class X{int i}; do { do{ if (false) new X(1) } } == null", true);
  }

  @Test public void blocks() {
    test("{ 1 }", 1);
    test("{ 1; 2 }", 2);
    test("{ { 1; 2 } }", 2);
    test("{ { 1; { 2 } } }", 2);
    test("def x = { { 1; { 2 } } }()", 2);
    test("def x; x = { { 1; { 2 } } }()", 2);
    test("int x; x = { { 1; { 2 } } }()", 2);
  }

  @Test public void ifexpr() {
    test("123 if (false)\n{ 456 }\n", 456);
    test("123 if\n (false)\n{ 456 }\n", 456);
    test("123\nif (false)\n{ 456 }\n", null);
    test("return 123 if true", 123);
    test("return (123 if true) if true", 123);
    test("return (123 if true) if false", null);
    test("123 unless (true)\n{ 456 }\n", 456);
    test("123 unless\n (true)\n{ 456 }\n", 456);
    testError("123\nunless (true)\n{ 456 }\n", "unexpected token");
    test("return 123 unless false", 123);
    test("return (123 unless false) unless false", 123);
    test("return (123 unless false) unless true", null);
    test("(123 if true) + 456", 579);
  }

  @Test public void conditionalAssignment() {
    test("Map x = [a:1]; 2 + (x.a?=3)", 5);
    test("Map x = [a:1]; (x.a?=3) + 2", 5);
    test("def x = [:]; int i; i ?= x.a", null);
    test("def x; int i; i ?= x.a", null);
    test("def x = [a:3]; long y; y ?= x.a", 3);
    test("Map x; int i; i ?= x.a", null);
    test("Map x = [a:[:]]; byte i = 5; i ?= x.a.b.c; i", (byte) 5);
    test("Map x = [a:[:]]; int i = 5; i ?= x.a.b.c; i", 5);
    test("Map x = [a:[:]]; int i = 5; i ?= x.a.b[0].c; i", 5);
    test("Map x = [a:[]]; int i = 5; i ?= x.a[2].b[0].c; i", 5);
    test("Map x = [a:[]]; int i = 5; i ?= x.a[2]?.b?[0].c; i", 5);
    test("Map m = [a:[]]; m.a ?= m.a", Utils.listOf());
    test("Map m = [a:[]]; m.a ?= m.b", null);
    test("Map m = [a:3]; m.a ?= m.b; m.a", 3);
    test("Map m = [a:[]]; def x; m.a ?= x", null);
    test("Map m; def x; m.a.b.c ?= x.a", null);
    test("Map m; def x; m.a.b.c ?= x.a; m.a", null);
    test("Map m; def x; m.a.b.c ?= x.a; m.a?.b", null);
    test("Map m; def x = [a:3]; m.a.b.c ?= x.a; m.a?.b", Utils.mapOf("c", 3));
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
    test("def m = [:]; Map x; (m.a ?= 1) + (x.a ?= 2)", 3);
    test("def m = [:]; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += 2", 3);
    test("def m = [:]; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += (x.c ?= 2)", 3);
    test("def m = [:]; Map x; m.a.b.c = 1; m.a.(x.b ?= 'b').c += (x.c ?= 2); m.a.b.c", 3);
    test("def m = [:]; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += 2", 3);
    test("def m = [:]; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += (x ?= 2)", 3);
    test("def m = [:]; def x; m.a.b.c = 1; m.a.(x ?= 'b').c += (x ?= 2); m.a.b.c", 3);
    test("def m = [:]; def x; m.a.b.c = 1; m.a.(x ?= (x ?= 'b')).c += 2", 3);
    test("def m = [:]; def x; m.a.b.c = 1; m.a.(x ?= (x ?= true ? true ? 'b' : 'z' : 'zz')).c += (x ?= 2)", 3);
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

    test("def x; def y; def z; z = 99; z ?= x + y; z", 99);
    test("def x; def y; def z; z = 99; z ?= x & y; z", 99);
    test("def x; def y; def z; z = 99; z ?= x | y; z", 99);
    test("def x; def y; def z; z = 99; z ?= x ^ y; z", 99);
    test("def x; def y; def z; z = 99; z ?= ~x; z", 99);
    test("def x; int z; z = 99; z ?= x + 3; z", 99);
    test("def x; int z; z = 99; z ?= x & 3; z", 99);
    test("def x; int z; z = 99; z ?= x | 3; z", 99);
    test("def x; int z; z = 99; z ?= x ^ 3; z", 99);
    test("def x; int z; z = 99; z ?= ~x; z", 99);
  }

  @Test public void nullValues() {
    test("String s; s", "");
    test("String s = null", null);
    test("Map x; x", Utils.mapOf());
    test("Map x = null; x", null);
    test("List x; x", Utils.listOf());
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
    test("'abc'[0 if true]", "a");
    testError("'abc'[0 if false]", "cannot convert null value to int");
    testError("''[0]", "index out of bounds");
    testError("''[-1]", "index out of bounds");
    test("def x = 'abcdef'; def i = 3; x[(byte)i]", "d");
    test("def x = 'abcdef'; def i = 3; x[i]", "d");
    test("def x = 'abcdef'; def i = -1; x[i]", "f");
    test("def x = 'abcdef'; def i = -6; x[i]", "a");
    testError("def x = 'abcdef'; def i = -7; x[i]", "index out of bounds");
    test("def x = 'abcdef'; def i = 3; x?[i]", "d");
    test("String x = 'abcdef'; def i = 3; x[i]", "d");
    test("String x = 'abcdef'; def i = -1; x[i]", "f");
    test("String x = 'abcdef'; def i = -6; x[i]", "a");
    testError("String x = 'abcdef'; def i = -7; x[i]", "index out of bounds");
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
    test("byte x; if (x) { x += 1 } else { x += 2 }", 2);
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
    testError("if (false) false; else true", "unexpected token 'else'");
    testError("if (false) false; else if (true) true", "unexpected token 'else'");
    testError("if (false) false;\nelse true", "unexpected token 'else'");
    testError("if (false) false;\nelse if (true) true", "unexpected token 'else'");
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
    test("def it = 'abc'; return if /x/r; s/b/x/g;", "axc");
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
    test("(byte)1 != false", true);
    test ("(byte)1 == false", false);
    test("1 != false", true);
    test ("1 == false", false);
    testError("(byte)1 < true", "cannot be compared");
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

    test("1 == 1.0", true);
    test("1 == 1L", true);
    test("1 == 1D", true);
    test("1L == 1D", true);
    test("1L == 1.0", true);
    test("1D == 1.0", true);
    test("1 != 1.0", false);
    test("1 != 1L", false);
    test("1 != 1D", false);
    test("1L != 1D", false);
    test("1L != 1.0", false);
    test("1D != 1.0", false);
    test("def x = 1; def y = 1.0; x == y", true);
    test("def x = 1; def y = 1L; x == y", true);
    test("def x = 1; def y = 1D; x == y", true);
    test("def x = 1L; def y = 1D; x == y", true);
    test("def x = 1L; def y = 1.0; x == y", true);
    test("def x = 1D; def y = 1.0; x == y", true);
    test("def x = 1; def y = 1.0; x != y", false);
    test("def x = 1; def y = 1L; x != y", false);
    test("def x = 1; def y = 1D; x != y", false);
    test("def x = 1L; def y = 1D; x != y", false);
    test("def x = 1L; def y = 1.0; x != y", false);
    test("def x = 1D; def y = 1.0; x != y", false);

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

    test("[1,2,3] == [1,2,3]", true);
    test("[1,2,3] != [1,2,3]", false);
    test("[a:1,b:[1,2,3],c:[x:1]] == [a:1,b:[1,2,3],c:[x:1]]", true);
    test("[a:1,b:[1,2,3],c:[x:1]] != [a:1,b:[1,2,3],c:[x:1]]", false);
    test("[1,2,3] == 1", false);
    test("[a:1,b:[1,2,3],c:[x:1]] == 1", false);
    test("[1,2,3] != 1", true);
    test("[a:1,b:[1,2,3],c:[x:1]] != 1", true);
    test("def x = [1,2,3]; def y= [1,2,3]; x == y", true);
    test("def x = [1,2,3]; def y = [1,2,3]; x != y", false);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = [a:1,b:[1,2,3],c:[x:1]]; x == y", true);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = [a:1,b:[1,2,3],c:[x:1]]; x != y", false);
    test("def x = [1,2,3]; def y = 1; x == y", false);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = 1; x == y", false);
    test("def x = [1,2,3]; def y = 1; x != y", true);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = 1; x != y", true);
    test("[] == []", true);
    test("[] != []", false);
    test("[] == [:]", false);
    test("[] != [:]", true);
    test("def x = []; def y = []; x == y", true);
    test("def x = []; def y = []; x != y", false);
    test("def x = []; def y = [:]; x == y", false);
    test("def x = []; def y = [:]; x != y", true);
  }

  @Test public void tripleEqualsNotEquals() {
    test("true !== (byte)1", true);
    test("true === (byte)1", false);
    test("true !== 1", true);
    test("true === 1", false);
    test("1 !== false", true);
    test ("1 === false", false);
    test("true !== 1L", true);
    test("true === 1L", false);
    test("1L !== false", true);
    test ("1L === false", false);
    test("true !== 1D", true);
    test("true === 1D", false);
    test("1D !== false", true);
    test ("1D === false", false);
    test("true !== 1.0", true);
    test("true === 1.0", false);
    test("1.0 !== false", true);
    test ("1.0 === false", false);
    test("boolean x = true; int y = 1; x !== y", true);
    test("boolean x = true; int y = 1; x === y", false);
    test("int x = 1; boolean y = false; x !== y", true);
    test ("int x = 1; boolean y = false; x === y", false);
    test("def x = true; def y = 1; x !== y", true);
    test("def x = true; def y = 1; x === y", false);
    test("def x = 1; def y = false; x !== y", true);
    test ("def x = 1; def y = false; x === y", false);
    test("boolean x = true; long y = 1; x !== y", true);
    test("boolean x = true; long y = 1; x === y", false);
    test("long x = 1; boolean y = false; x !== y", true);
    test ("long x = 1; boolean y = false; x === y", false);
    test("def x = true; def y = 1L; x !== y", true);
    test("def x = true; def y = 1L; x === y", false);
    test("def x = 1L; def y = false; x !== y", true);
    test ("def x = 1L; def y = false; x === y", false);

    test("1 === 1", true);
    test("(byte)1 === 1", true);
    test("(byte)1 === (byte)1", true);
    test("(byte)1 === (byte)0", false);
    test("(byte)1 === 1L", true);
    test("1 === 1L", true);
    test("1 === 1D", true);
    test("(byte)1 === 1D", true);
    test("1 === 1.0", true);
    test("1L === 1D", true);
    test("1L === 1.0", true);
    test("1D === 1.0", true);
    test("1 !== 1L", false);
    test("1 !== 1D", false);
    test("(byte)1 !== 1.0", false);
    test("1 !== 1.0", false);
    test("1L !== 1D", false);
    test("1L !== 1.0", false);
    test("1D !== 1.0", false);
    test("def x = 1; def y = 1.0; x === y", true);
    test("def x = 1; def y = 1L; x === y", true);
    test("def x = 1; def y = 1D; x === y", true);
    test("def x = 1L; def y = 1D; x === y", true);
    test("def x = 1L; def y = 1.0; x === y", true);
    test("def x = 1D; def y = 1.0; x === y", true);
    test("def x = (byte)1; def y = 1.0; x !== y", false);
    test("def x = 1; def y = 1.0; x !== y", false);
    test("def x = 1; def y = 1L; x !== y", false);
    test("def x = 1; def y = 1D; x !== y", false);
    test("def x = 1L; def y = 1D; x !== y", false);
    test("def x = 1L; def y = 1.0; x !== y", false);
    test("def x = 1D; def y = 1.0; x !== y", false);

    test("boolean x = true; double y = 1; x !== y", true);
    test("boolean x = true; double y = 1; x === y", false);
    test("double x = 1; boolean y = false; x !== y", true);
    test ("double x = 1; boolean y = false; x === y", false);
    test("def x = true; def y = 1D; x !== y", true);
    test("def x = true; def y = 1D; x === y", false);
    test("def x = 1D; def y = false; x !== y", true);
    test ("def x = 1D; def y = false; x === y", false);

    test("boolean x = true; Decimal y = 1; x !== y", true);
    test("boolean x = true; Decimal y = 1; x === y", false);
    test("Decimal x = 1; boolean y = false; x !== y", true);
    test ("Decimal x = 1; boolean y = false; x === y", false);
    test("def x = true; def y = 1.0; x !== y", true);
    test("def x = true; def y = 1.0; x === y", false);
    test("def x = 1.0; def y = false; x !== y", true);
    test ("def x = 1.0; def y = false; x === y", false);

    test("boolean x = true; String y = '1'; x !== y", true);
    test("boolean x = true; String y = '1'; x === y", false);
    test("String x = '1'; boolean y = false; x !== y", true);
    test ("String x = '1'; boolean y = false; x === y", false);
    test("def x = true; def y = '1'; x !== y", true);
    test("def x = true; def y = '1'; x === y", false);
    test("def x = '1'; def y = false; x !== y", true);
    test ("def x = '1'; def y = false; x === y", false);

    test("byte x = 1; String y = '1'; x !== y", true);
    test("int x = 1; String y = '1'; x !== y", true);
    test("int x = 1; String y = '1'; x === y", false);
    test("String x = '1'; int y = 1; x !== y", true);
    test ("String x = '1'; int y = 1; x === y", false);
    test("def x = 1; def y = '1'; x !== y", true);
    test("def x = 1; def y = '1'; x === y", false);
    test("def x = '1'; def y = 1; x !== y", true);
    test ("def x = '1'; def y = 1; x === y", false);

    // Can only run these tests if not wrapping in sleep(0,x) since that turns constants
    // into non-constants (because we don't know what will happen to return results)
    {
      useAsyncDecorator = false;
      test("[1,2,3] === [1,2,3]", true);
      test("[1,2,3] !== [1,2,3]", false);
      test("[a:1,b:[1,2,3],c:[x:1]] === [a:1,b:[1,2,3],c:[x:1]]", true);
      test("[a:1,b:[1,2,3],c:[x:1]] !== [a:1,b:[1,2,3],c:[x:1]]", false);
      test("[] === []", true);
      test("[] !== []", false);
      test("[] === [:]", false);
      test("[] !== [:]", true);
      useAsyncDecorator = true;
    }

    test("[1,2,3] === 1", false);
    test("[a:1,b:[1,2,3],c:[x:1]] === 1", false);
    test("[1,2,3] !== 1", true);
    test("[a:1,b:[1,2,3],c:[x:1]] !== 1", true);
    test("def x = [1,2,3]; def y= [1,2,3]; x === y", false);
    test("def x = [1,2,3]; def y= x; x === y", true);
    test("def x = [1,2,3]; def y = [1,2,3]; x !== y", true);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = [a:1,b:[1,2,3],c:[x:1]]; x === y", false);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = x; x === y", true);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = x; x !== y", false);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = [a:1,b:[1,2,3],c:[x:1]]; x !== y", true);
    test("def x = [1,2,3]; def y = 1; x === y", false);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = 1; x === y", false);
    test("def x = [1,2,3]; def y = 1; x !== y", true);
    test("def x = [a:1,b:[1,2,3],c:[x:1]]; def y = 1; x !== y", true);
    test("def x = []; def y = []; x === y", false);
    test("def x = []; def y = []; x !== y", true);
    test("def x = []; def y = [:]; x === y", false);
    test("def x = []; def y = [:]; x !== y", true);
  }

  @Test public void errorLineNumber() {
    testError("def a = true\ndef b = 1\n a <=> b", "@ line 3");
  }

  @Test public void constBooleanComparisons() {
    test("1 == true", false);
    test("1 == false", false);
    test("0 == false", false);
    test("0 == true", false);
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
    testCounter++;
    String init = "byte b2 = (byte)2; int i3=3; long l5=5L; double d7=7.0D; Decimal dec13=13.0; String sabc = 'abc';" +
                  "def db2 = (byte)2; def di3=3; def dl5=5L; def dd7=7.0D; def ddec13=13.0; def dsabc = 'abc';";
    BiConsumer<String,Object> runtest = (code, expected) -> _test(init + code, expected);

    runtest.accept("null == " + bigger, false);
    runtest.accept(bigger + " == null", false);
    runtest.accept("null == " + smaller, false);
    runtest.accept(smaller + " == null", false);
    runtest.accept(smaller + " == " + bigger, false);
    runtest.accept(smaller + " == " + smaller, true);
    runtest.accept(bigger + " == " + smaller, false);
    runtest.accept(bigger + " == " + bigger, true);

    runtest.accept("null != " + bigger, true);
    runtest.accept(bigger + " != null", true);
    runtest.accept("null != " + smaller, true);
    runtest.accept(smaller + " != null", true);
    runtest.accept(smaller + " != " + bigger, true);
    runtest.accept(smaller + " != " + smaller, false);
    runtest.accept(bigger + " != " + smaller, true);
    runtest.accept(bigger + " != " + bigger, false);

    runtest.accept("null < " + bigger, true);
    runtest.accept(bigger + " < null", false);
    runtest.accept("null < " + smaller, true);
    runtest.accept(smaller + " < null", false);
    runtest.accept(smaller + " < " + bigger, true);
    runtest.accept(smaller + " < " + smaller, false);
    runtest.accept(bigger + " < " + smaller, false);
    runtest.accept(bigger + " < " + bigger, false);

    runtest.accept("null <= " + bigger, true);
    runtest.accept(bigger + " <= null", false);
    runtest.accept("null <= " + smaller, true);
    runtest.accept(smaller + " <= null", false);
    runtest.accept(smaller + " <= " + bigger, true);
    runtest.accept(smaller + " <= " + smaller, true);
    runtest.accept(bigger + " <= " + smaller, false);
    runtest.accept(bigger + " <= " + bigger, true);

    runtest.accept("null > " + bigger, false);
    runtest.accept(bigger + " > null", true);
    runtest.accept("null > " + smaller, false);
    runtest.accept(smaller + " > null", true);
    runtest.accept(smaller + " > " + bigger, false);
    runtest.accept(smaller + " > " + smaller, false);
    runtest.accept(bigger + " > " + smaller, true);
    runtest.accept(bigger + " > " + bigger, false);

    runtest.accept("null >= " + bigger, false);
    runtest.accept(bigger + " >= null", true);
    runtest.accept("null >= " + smaller, false);
    runtest.accept(smaller + " >= null", true);
    runtest.accept(smaller + " >= " + bigger, false);
    runtest.accept(smaller + " >= " + smaller, true);
    runtest.accept(bigger + " >= " + smaller, true);
    runtest.accept(bigger + " >= " + bigger, true);

    runtest.accept(bigger + "<=>" + smaller, 1);
    runtest.accept(smaller + "<=>" + bigger, -1);
    runtest.accept(smaller + "<=>" + smaller, 0);
    runtest.accept(bigger + "<=>" + bigger, 0);
  };

  @Test public void constComparisons() {
    comparisonTests.accept("(byte)3", "(byte)7");
    comparisonTests.accept("(byte)3", "7");
    comparisonTests.accept("-3", "(byte)7");
    comparisonTests.accept("-3", "7");
    comparisonTests.accept("-3L", "7L");
    comparisonTests.accept("-3.0D", "7.0D");
    comparisonTests.accept("(byte)3", "7.0D");
    comparisonTests.accept("-3.0D", "(byte)7");
    comparisonTests.accept("-3.0", "7.0");

    comparisonTests.accept("(byte)3", "7L");
    comparisonTests.accept("-3", "7L");
    comparisonTests.accept("-3L", "(byte)7");
    comparisonTests.accept("-3L", "7");
    comparisonTests.accept("(byte)3", "7D");
    comparisonTests.accept("-3", "7D");
    comparisonTests.accept("-3D", "(byte)7");
    comparisonTests.accept("-3D", "7");
    comparisonTests.accept("-3L", "7D");
    comparisonTests.accept("-3D", "7L");

    comparisonTests.accept("(byte)3", "7.0");
    comparisonTests.accept("-3", "7.0");
    comparisonTests.accept("-3.0", "(byte)7");
    comparisonTests.accept("-3.0", "7");
    comparisonTests.accept("-3L", "7.0");
    comparisonTests.accept("-3.0", "7L");
    comparisonTests.accept("-3.0D", "7.0");
    comparisonTests.accept("-3.0", "7.0D");

    comparisonTests.accept("'axcde'", "'azcde'");
    comparisonTests.accept("'axcde'", "'axcdef'");

    comparisonTests.accept("4*2", "-3*-7");
  }

  @Test public void expressionComparisons() {
    //String init = "byte b2 = 2; int i3=3; long l5=5L; double d7=7.0D; Decimal dec13=13.0; String sabc = 'abc';" +
    //              "def db2 = (byte)2; def di3=3; def dl5=5L; def dd7=7.0D; def ddec13=13.0; def dsabc = 'abc';";
    comparisonTests.accept("l5", "-b2");
    comparisonTests.accept("-i3", "l5");
    comparisonTests.accept("-i3 * d7", "dec13");
    comparisonTests.accept("-i3 * l5", "dec13");
    comparisonTests.accept("l5", "-db2 * 5");
    comparisonTests.accept("-di3 * 5", "l5");
    comparisonTests.accept("-di3 * (byte)5", "l5");
    comparisonTests.accept("-di3 * 5", "l5 * ddec13");
    comparisonTests.accept("-di3 * (byte)5", "l5 * ddec13");

    comparisonTests.accept("sabc", "sabc + 'z'");
    comparisonTests.accept("sabc", "dsabc + 'z'");
    comparisonTests.accept("sabc * 2", "sabc * 3");
    comparisonTests.accept("dsabc * 2", "dsabc * 3");

    test("def x = 2L; 2 * x == 4", true);
    test("long x = 2L; 2 * x == 4", true);
  }

  @Test public void compareOperator() {
    test("(byte)1 <=> null", 1);
    test("1 <=> null", 1);
    test("null <=> null", 0);
    test("null <=> (byte)1", -1);
    test("null <=> 1", -1);
    test("'' <=> null", 1);
    test("null <=> ''", -1);
    test("(byte)0 <=> null", 1);
    test("0 <=> null", 1);
    test("null <=> (byte)0", -1);
    test("null <=> 0", -1);
    test("1L <=> null", 1);
    test("null <=> 1L", -1);
    test("1D <=> null", 1);
    test("null <=> 1D", -1);
    test("1.0 <=> null", 1);
    test("null <=> 1.0", -1);

    test("def x = (byte)1; def y = null; x <=> y", 1);
    test("def x = 1; def y = null; x <=> y", 1);
    test("def x = null; def y = null; x <=> y", 0);
    test("def x = null; def y = (byte)1; x <=> y", -1);
    test("def x = null; def y = 1; x <=> y", -1);
    test("def x = ''; def y = null; x <=> y", 1);
    test("def x = null; def y = ''; x <=> y", -1);
    test("def x = (byte)0; def y = null; x <=> y", 1);
    test("def x = 0; def y = null; x <=> y", 1);
    test("def x = null; def y = 0; x <=> y", -1);
    test("def x = 1L; def y = null; x <=> y", 1);
    test("def x = null; def y = 1L; x <=> y", -1);
    test("def x = 1D; def y = null; x <=> y", 1);
    test("def x = null; def y = 1D; x <=> y", -1);
    test("def x = 1.0; def y = null; x <=> y", 1);
    test("def x = null; def y = 1.0; x <=> y", -1);

    test("(byte)1 <=> 2", -1);
    test("1 <=> (byte)2", -1);
    test("(byte)1 <=> (byte)2", -1);
    test("1 <=> 2", -1);
    test("def x = 1; x <=> 2", -1);
    test("def x = 'abc'; 'bcd' <=> x", 1);
    test("[1,2,3] <=> [2,3,4]", -1);
    test("[1,2,3] <=> [1,2,3]", 0);
    test("[3,2,3] <=> [2,3,4]", 1);
    test("[1,2,3,4] <=> [2,3,4]", -1);
    test("[1,2,3,4] <=> [1,2,3]", 1);
    test("[1] <=> []", 1);
    test("[] <=> []", 0);
    test("[] <=> [1]", -1);
    test("[1,[2,3]] <=> [1,[3,4]]", -1);
    test("[1,[2,3]] <=> [1,[1,3,4]]", 1);
    test("[1,[2,3]] <=> [1,[2,3]]", 0);
    test("[1,[null,3]] <=> [1,[2,3]]", -1);
    test("[1,[null,3]] <=> [1,[null,3]]", 0);
    test("[1,null] <=> [1,[null,3]]", -1);
    test("[1] <=> null", 1);
    test("null <=> [1]", -1);
    testError("[1] <=> ['a']", "cannot compare");
    testError("[a:1] <=> [:]", "cannot compare");
    testError("[1,2,3] <=> 'xxx'", "cannot compare");
    testError("[1,2,3] <=> 'xxx'", "cannot compare");
    test("def x = [1,2,3]; x <=> [2,3,4]", -1);
    test("def x = [1,2,3]; x <=> [1,2,3]", 0);
    test("def x = [3,2,3]; x <=> [2,3,4]", 1);
    test("def x = [1,2,3,4]; x <=> [2,3,4]", -1);
    test("def x = [1,2,3,4]; x <=> [1,2,3]", 1);
    test("def x = [1]; x <=> []", 1);
    test("def x = []; x <=> []", 0);
    test("def x = []; x <=> [1]", -1);
    test("def x = [1,[2,3]]; x <=> [1,[3,4]]", -1);
    test("def x = [1,[2,3]]; x <=> [1,[1,3,4]]", 1);
    test("def x = [1,[2,3]]; x <=> [1,[2,3]]", 0);
    test("def x = [1,[2,3]]; x <=> [1,[2L,3]]", 0);
    test("def x = [1]; x <=> null", 1);
    testError("def x = [1]; x <=> ['a']", "cannot compare");
    testError("def x = [a:1]; x <=> [:]", "cannot compare");
    testError("def x = [1,2,3]; x <=> 'xxx'", "cannot compare");
    testError("def x = [1,2,3]; x <=> 'xxx'", "cannot compare");
  }

  @Test public void instanceOfTests() {
    test("true instanceof boolean", true);
    test("1 instanceof boolean", false);
    test("def x = true; x instanceof boolean", true);
    test("def x = true; (x && x || x) instanceof boolean", true);
    test("String x; x instanceof boolean", false);
    test("String x; x instanceof boolean || x instanceof String", true);
    test("def x = 'abc'; x instanceof boolean || x instanceof String", true);

    test("(byte)1 instanceof Map", false);
    test("(byte)1 instanceof List", false);
    test("(byte)1 instanceof boolean", false);
    test("(byte)1 instanceof String", false);
    test("(byte)1 instanceof byte", true);
    test("(byte)1 instanceof int", false);
    test("(byte)1 instanceof long", false);
    test("(byte)1 instanceof double", false);
    test("(byte)1 instanceof Decimal", false);
    test("1 instanceof Map", false);
    test("1 instanceof List", false);
    test("1 instanceof boolean", false);
    test("1 instanceof String", false);
    test("1 instanceof int", true);
    test("1 instanceof byte", false);
    test("1 instanceof long", false);
    test("1 instanceof double", false);
    test("1 instanceof Decimal", false);
    test("1L instanceof Map", false);
    test("1L instanceof List", false);
    test("1L instanceof boolean", false);
    test("1L instanceof String", false);
    test("1L instanceof byte", false);
    test("1L instanceof int", false);
    test("1L instanceof long", true);
    test("1L instanceof double", false);
    test("1L instanceof Decimal", false);

    test("1D instanceof Map", false);
    test("1D instanceof List", false);
    test("1D instanceof boolean", false);
    test("1D instanceof String", false);
    test("1D instanceof byte", false);
    test("1D instanceof int", false);
    test("1D instanceof long", false);
    test("1D instanceof double", true);
    test("1D instanceof Decimal", false);

    test("1.0 instanceof Map", false);
    test("1.0 instanceof List", false);
    test("1.0 instanceof boolean", false);
    test("1.0 instanceof String", false);
    test("1.0 instanceof byte", false);
    test("1.0 instanceof int", false);
    test("1.0 instanceof long", false);
    test("1.0 instanceof double", false);
    test("1.0 instanceof Decimal", true);

    test("[] instanceof Map", false);
    test("[] instanceof List", true);
    test("[] instanceof boolean", false);
    test("[] instanceof String", false);
    test("[] instanceof byte", false);
    test("[] instanceof int", false);
    test("[] instanceof long", false);
    test("[] instanceof double", false);
    test("[] instanceof Decimal", false);

    test("[:] instanceof Map", true);
    test("[:] instanceof List", false);
    test("[:] instanceof boolean", false);
    test("[:] instanceof String", false);
    test("[:] instanceof byte", false);
    test("[:] instanceof int", false);
    test("[:] instanceof long", false);
    test("[:] instanceof double", false);
    test("[:] instanceof Decimal", false);

    test("def x = (byte)1; x instanceof Map", false);
    test("def x = (byte)1; x instanceof List", false);
    test("def x = (byte)1; x instanceof boolean", false);
    test("def x = (byte)1; x instanceof String", false);
    test("def x = (byte)1; x instanceof byte", true);
    test("def x = (byte)1; x instanceof int", false);
    test("def x = (byte)1; x instanceof long", false);
    test("def x = (byte)1; x instanceof double", false);
    test("def x = (byte)1; x instanceof Decimal", false);
    test("def x = 1 ; x instanceof Map", false);
    test("def x = 1 ; x instanceof List", false);
    test("def x = 1 ; x instanceof boolean", false);
    test("def x = 1 ; x instanceof String", false);
    test("def x = 1 ; x instanceof byte", false);
    test("def x = 1 ; x instanceof int", true);
    test("def x = 1 ; x instanceof long", false);
    test("def x = 1 ; x instanceof double", false);
    test("def x = 1 ; x instanceof Decimal", false);
    test("def x = 1L ; x instanceof Map", false);
    test("def x = 1L ; x instanceof List", false);
    test("def x = 1L ; x instanceof boolean", false);
    test("def x = 1L ; x instanceof String", false);
    test("def x = 1L ; x instanceof byte", false);
    test("def x = 1L ; x instanceof int", false);
    test("def x = 1L ; x instanceof long", true);
    test("def x = 1L ; x instanceof double", false);
    test("def x = 1L ; x instanceof Decimal", false);

    test("def x = 1D ; x instanceof Map", false);
    test("def x = 1D ; x instanceof List", false);
    test("def x = 1D ; x instanceof boolean", false);
    test("def x = 1D ; x instanceof String", false);
    test("def x = 1D ; x instanceof byte", false);
    test("def x = 1D ; x instanceof int", false);
    test("def x = 1D ; x instanceof long", false);
    test("def x = 1D ; x instanceof double", true);
    test("def x = 1D ; x instanceof Decimal", false);

    test("def x = 1.0 ; x instanceof Map", false);
    test("def x = 1.0 ; x instanceof List", false);
    test("def x = 1.0 ; x instanceof boolean", false);
    test("def x = 1.0 ; x instanceof String", false);
    test("def x = 1.0 ; x instanceof byte", false);
    test("def x = 1.0 ; x instanceof int", false);
    test("def x = 1.0 ; x instanceof long", false);
    test("def x = 1.0 ; x instanceof double", false);
    test("def x = 1.0 ; x instanceof Decimal", true);

    test("def x = [] ; x instanceof Map", false);
    test("def x = [] ; x instanceof List", true);
    test("def x = [] ; x instanceof boolean", false);
    test("def x = [] ; x instanceof String", false);
    test("def x = [] ; x instanceof byte", false);
    test("def x = [] ; x instanceof int", false);
    test("def x = [] ; x instanceof long", false);
    test("def x = [] ; x instanceof double", false);
    test("def x = [] ; x instanceof Decimal", false);

    test("def x = [:] ; x instanceof Map", true);
    test("def x = [:] ; x instanceof List", false);
    test("def x = [:] ; x instanceof boolean", false);
    test("def x = [:] ; x instanceof String", false);
    test("def x = [:] ; x instanceof byte", false);
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

    test("(byte)1 !instanceof Map", true);
    test("(byte)1 !instanceof List", true);
    test("(byte)1 !instanceof boolean", true);
    test("(byte)1 !instanceof String", true);
    test("(byte)1 !instanceof byte", false);
    test("(byte)1 !instanceof int", true);
    test("(byte)1 !instanceof long", true);
    test("(byte)1 !instanceof double", true);
    test("(byte)1 !instanceof Decimal", true);
    test("1 !instanceof Map", true);
    test("1 !instanceof List", true);
    test("1 !instanceof boolean", true);
    test("1 !instanceof String", true);
    test("1 !instanceof byte", true);
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
    test("null !instanceof byte", true);
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
    test("def x = null; x !instanceof byte", true);
    test("def x = null; x !instanceof int", true);
    test("def x = null; x !instanceof long", true);
    test("def x = null; x !instanceof double", true);
    test("def x = null; x !instanceof Decimal", true);
  }

  @Test public void typeCasts() {
    test("(byte)1L", (byte)1);
    test("(byte)1", (byte)1);
    test("(int)(byte)1L", 1);
    test("(int)1L", 1);
    test("(int)1L instanceof int", true);
    testError("(String)1", "cannot convert");
    testError("def x = 1; (String)x", "cannot convert");
    test("(String)null", null);
    test("(Map)null", null);
    test("(List)null", null);
    test("(Decimal)null", null);
    test("def x = null; (Map)x", null);
    test("def x = null; (List)x", null);
    test("def x = null; (Decimal)x", null);
    testError("(int)null", "cannot convert null");
    testError("(long)null", "cannot convert null");
    testError("(double)null", "cannot convert null");
    testError("def x; (int)x", "cannot convert null");
    testError("def x; (long)x", "cannot convert null");
    testError("def x; (double)x", "cannot convert null");

    test("(int)'a'", 97);
    test("(byte)'a'", (byte)97);
    test("def x = 'a'; (int)x", 97);
    test("def x = 'a'; (byte)x", (byte)97);
    testError("(byte)'abc'", "string with multiple chars cannot be cast");
    testError("(int)'abc'", "string with multiple chars cannot be cast");
    testError("def x = 'abc'; (byte)x", "string with multiple chars cannot be cast");
    testError("def x = 'abc'; (int)x", "string with multiple chars cannot be cast");
    testError("(byte)''", "empty string cannot be cast");
    testError("(int)''", "empty string cannot be cast");
    testError("String s; (byte)s", "empty string cannot be cast");
    testError("String s; (int)s", "empty string cannot be cast");
    testError("def x = ''; (byte)x", "empty string cannot be cast");
    testError("def x = ''; (int)x", "empty string cannot be cast");

    testError("(Map)1", "cannot convert from int to map");
    testError("(List)1", "cannot convert from int to list");
    testError("int x = 1; (Map)x", "cannot convert from");
    testError("int x = 1; (List)x", "cannot convert from");
    testError("def x = 1; (Map)x", "cannot be cast");
    testError("def x = 1; (List)x", "cannot be cast");
    testError("def x = { it }; (Map)x", "cannot be cast");
    testError("def x = { it }; (List)x", "cannot be cast");
    testError("def x(){ 1 }; (Map)x", "cannot convert from");
    testError("def x(){ 1 }; (List)x", "cannot convert from");
    testError("def x = { it }; (byte)x", "cannot be cast");
    testError("def x = { it }; (int)x", "cannot be cast");
    testError("def x = { it }; (long)x", "cannot be cast");
    testError("def x = { it }; (double)x", "cannot be cast");
    testError("def x = { it }; (Decimal)x", "cannot be cast");
    testError("def x = { it }; (String)x", "cannot convert");
    test("def x = { it }; ('' + x) =~ /Function@\\d+/", true);
    testError("def x(){ 1 }; (int)x", "cannot convert from");
    testError("def x(){ 1 }; (long)x", "cannot convert from");
    testError("def x(){ 1 }; (double)x", "cannot convert from");
    testError("def x(){ 1 }; (Decimal)x", "cannot convert from");

    testError("def x(){ 1 }; (String)x", "cannot convert from");

    test("(byte)1", (byte)1);
    test("(int)1", 1);
    test("byte x = 1; (byte)x", (byte)1);
    test("int x = 1; (int)x", 1);
    test("(long)1", 1L);
    test("int x = 1; (long)x", 1L);
    test("(double)1", 1D);
    test("int x = 1; (double)x", 1D);
    test("(Decimal)1", "#1");
    test("int x = 1; (Decimal)x", "#1");

    test("(byte)1L", (byte)1);
    test("(int)1L", 1);
    test("long x = 1L; (int)x", 1);
    test("(long)1L", 1L);
    test("long x = 1L; (long)x", 1L);
    test("(double)1L", 1D);
    test("long x = 1L; (double)x", 1D);
    test("(Decimal)1L", "#1");
    test("long x = 1L; (Decimal)x", "#1");

    test("(byte)1D", (byte)1);
    test("(int)1D", 1);
    test("double x = 1D; (int)x", 1);
    test("(long)1D", 1L);
    test("double x = 1D; (long)x", 1L);
    test("(double)1D", 1D);
    test("double x = 1D; (double)x", 1D);
    test("(Decimal)1D", "#1.0");
    test("double x = 1D; (Decimal)x", "#1.0");

    test("(byte)1.0", (byte)1);
    test("(int)1.0", 1);
    test("Decimal x = 1.0; (int)x", 1);
    test("(long)1.0", 1L);
    test("Decimal x = 1.0; (long)x", 1L);
    test("(double)1.0", 1D);
    test("Decimal x = 1.0; (double)x", 1D);
    test("(Decimal)1.0", "#1.0");
    test("Decimal x = 1.0; (Decimal)x", "#1.0");
  }

  @Test public void asType() {
    testError("true as byte", "cannot coerce");
    testError("true as int", "cannot coerce");
    testError("(int)true", "cannot convert");
    test("(byte)1 as int", 1);
    test("(byte)1 as long", 1L);
    test("(byte)1 as double", 1D);
    test("(byte)1 as Decimal", "#1");
    test("1 as byte", (byte)1);
    test("1L as byte", (byte)1);
    test("1L as int", 1);
    test("(1L as int) instanceof int", true);
    test("1 as String", "1");
    test("def x = 1; x as String", "1");
    testError("null as byte", "null value");
    testError("null as int", "null value");
    testError("null as long", "null value");
    testError("null as double", "null value");
    testError("null as Decimal", "null value");
    test("null as String", null);
    test("null as Map", null);
    test("null as List", null);
    testError("def x = null; x as byte", "null value");
    testError("def x = null; x as int", "null value");
    testError("def x = null; x as long", "null value");
    testError("def x = null; x as double", "null value");
    testError("def x = null; x as Decimal", "null value");
    test("def x = null; x as String", null);
    test("def x = null; x as Map", null);
    test("def x = null; x as List", null);
    testError("def x; x as byte", "null value");
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
    test("def x = { it }; (x as String) =~ /Function@(\\d+)/", true);
    testError("def x(){ 1 }; x as int", "cannot coerce");
    testError("def x(){ 1 }; x as long", "cannot coerce");
    testError("def x(){ 1 }; x as double", "cannot coerce");
    testError("def x(){ 1 }; x as Decimal", "cannot coerce");

    test("def x(){ 1 }; (x as String) =~ /Function@(\\d+)/", true);

    test("1 as int", 1);
    test("int x = 1; x as int", 1);
    test("1 as long", 1L);
    test("int x = 1; x as long", 1L);
    test("1 as double", 1D);
    test("int x = 1; x as double", 1D);
    test("1 as Decimal", "#1");
    test("int x = 1; x as Decimal", "#1");

    test("1L as byte", (byte)1);
    test("1L as int", 1);
    test("long x = 1L; x as int", 1);
    test("1L as long", 1L);
    test("long x = 1L; x as long", 1L);
    test("1L as double", 1D);
    test("long x = 1L; x as double", 1D);
    test("1L as Decimal", "#1");
    test("long x = 1L; x as Decimal", "#1");

    test("1D as byte", (byte)1);
    test("1D as int", 1);
    test("double x = 1D; x as int", 1);
    test("1D as long", 1L);
    test("double x = 1D; x as long", 1L);
    test("1D as double", 1D);
    test("double x = 1D; x as double", 1D);
    test("1D as Decimal", "#1.0");
    test("double x = 1D; x as Decimal", "#1.0");

    test("1.0 as byte", (byte)1);
    test("1.0 as int", 1);
    test("Decimal x = 1.0; x as int", 1);
    test("1.0 as long", 1L);
    test("Decimal x = 1.0; x as long", 1L);
    test("1.0 as double", 1D);
    test("Decimal x = 1.0; x as double", 1D);
    test("1.0 as Decimal", "#1.0");
    test("Decimal x = 1.0; x as Decimal", "#1.0");

    test("def x = 1; x as byte", (byte)1);
    test("def x = 1; x as int", 1);
    test("def x = 1; x as long", 1L);
    test("def x = 1; x as double", 1D);
    test("def x = 1; x as Decimal", "#1");

    test("def x = 1L; x as byte", (byte)1);
    test("def x = 1L; x as int", 1);
    test("def x = 1L; x as long", 1L);
    test("def x = 1L; x as double", 1D);
    test("def x = 1L; x as Decimal", "#1");

    test("def x = 1D; x as byte", (byte)1);
    test("def x = 1D; x as int", 1);
    test("def x = 1D; x as long", 1L);
    test("def x = 1D; x as double", 1D);
    test("def x = 1D; x as Decimal", "#1.0");

    test("def x = 1.0; x as byte", (byte)1);
    test("def x = 1.0; x as int", 1);
    test("def x = 1.0; x as long", 1L);
    test("def x = 1.0; x as double", 1D);
    test("def x = 1.0; x as Decimal", "#1.0");

    test("(byte)1 as String", "1");
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
    test("def x = 1; x as String as byte", (byte)1);
    test("def x = 1; x as String as int", 1);
    test("def x = 1; (x as String as int) + 2", 3);
    testError("def x = 1; x as String as int + 2", "unexpected token '+'");
    testError("def x = 1.0; x as String as int", "not a valid int");
    test("def x = 1.0; x as String as double", 1.0D);
    test("def x = 1.0; x as String as Decimal", "#1.0");
    test("def x = 1D; x as String as double", 1.0D);
    test("def x = 1L; x as String as long", 1L);
    test("def x = 1L; x as String as byte", (byte)1);

    test("[:] as List", Utils.listOf());
    test("[a:1] as List", Utils.listOf(Utils.listOf("a",1)));
    test("[a:1,b:2] as List", Utils.listOf(Utils.listOf("a",1), Utils.listOf("b",2)));
    test("[a:1,b:2] as List as Map", Utils.mapOf("a",1,"b",2));
    test("[1,2,3].map{it*it}.map{[it.toString(),it]} as Map", Utils.mapOf("1",1,"4",4,"9",9));
    testError("'abc' as Map", "cannot coerce");
    test("'abc' as List", Utils.listOf("a","b","c"));
    test("'' as List", Utils.listOf());
    test("('abc' as List).join()", "abc");
    testError("'123456789123456789123456789' as long", "not a valid long");
    testError("'89.123.123' as double", "not a valid double");
    test("(123 as String).length()", 3);
    test("(123 as String) + 'abc'", "123abc");

    test("'' as byte[]", new byte[0]);
    test("([] as byte[]) as String", "");
    test("'abc' as byte[]", new byte[]{97,98,99});
    test("def x = 'abc'; x as byte[]", new byte[]{97,98,99});
    test("([97,98,99] as byte[]) as String", "abc");
    test("byte[] x = [97,98,99]; x as String", "abc");
    test("def x = [97,98,99] as byte[]; x as String", "abc");

    test("def x = 1 as long\n[x,x].sum()\n", 2L);
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
    test("(byte)1 in []", false);
    test("1 in []", false);
    test("(byte)1 !in []", true);
    test("1 !in []", true);
    test("[] in [[]]", true);
    test("2 in [1,2L,3,2L,4]", true);
    test("(byte)2 in [1,2,3,2,4]", true);
    test("2 in [1,(byte)2,3,(byte)2,4]", true);
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
    test("int i = 0; int sum = 0; while (i < 10) { int j = 0; while (j < i) { sum++; j++ }; i++ }; sum", 45);
    test("int i = 0; int sum = 0; while (i < 10) i++", null);
    testError("while (false) i++;", "unknown variable 'i'");
    test("byte i = 1; while (false) i++; i", (byte)1);
    test("int i = 1; while (false) i++; i", 1);
    test("int i = 1; while (false) ;", null);
    test("byte i = 1; while (++i < 10); i", (byte)10);
    test("int i = 1; while (++i < 10); i", 10);
    test("int i = 1; while (i if false) { i++ }; i", 1);
    test("int i = 1; while (true if i < 10) { i++ }; i", 10);
    test("int i = 1; while() { break if i > 4; i++ }; i", 5);
    testError("LABEL: int i = 1", "labels can only be applied to for, while, and do/until");
    test("LOOP: while() {\n  while () {\n    break LOOP\n  }\n}", null);
    testError("int i = 1; while() { break i }; i", "could not find enclosing loop");
  }

  @Test public void doUntilLoops() {
    test("int i = 0; do { i++ } until (i == 10); i", 10);
    testError("int i = 0; do i++ until (i == 10); i", "unexpected token 'i'");
    test("int i = 0; int sum = 0; do { sum += i++} until (i == 10); sum", 45);
    test("int i = 0; int sum = 0; do\n{\nsum += i++\n}\nuntil\n(\ni ==\n 10\n)\nsum", 45);
    test("int i = 0; int sum = 0; do { sum += i; i++ } until (i == 10); sum", 45);
    test("int i = 0; int sum = 0; do { int j = 0; while (j < i) { sum++; j++ }; i++ } until (i == 10); sum", 45);
    testError("do { i++ } until (true);", "unknown variable 'i'");
    test("byte i = 1; do { i++ } until (true); i", (byte)2);
    test("int i = 1; do { i++ } until (true); i", 2);
    test("int i = 1; do { i++ } until (true if true); i", 2);
    test("int i = 1; do {} until (true)", null);
    test("int i = 1; LABEL: do { break LABEL if i > 4; i++ } until (false); i", 5);
    test("int i = 1; LABEL:\ndo { break LABEL if i > 4; i++ } until (false); i", 5);
    testError("int i = 1; LABEL:\ndo { break LABEL2 if i > 4; i++ } until (false); i",  "could not find enclosing loop with label LABEL2");
  }

  @Test public void forLoops() {
    testError("for (const i = 0; i < 10; i++) {}", "unexpected token 'const'");
    testError("for (int i = 0;", "Unexpected end-of-file");
    testError("for (int i = 0;\n", "Unexpected end-of-file");
    testError("for (int i = 0; i < 10;", "Unexpected end-of-file");
    testError("for (int i = 0; i < 10; i++", "Unexpected end-of-file");
    test("def x = 5; for (i = 0; i < 10; i++) { x++ }; x", 15);
    test("def x = 5; for (i = 0; i < 10; i++) { x++ }; x", 15);
    test("def x = 0; for (i = 0; i < 10; i++) {}; i", 10);
    test("def x = 0; for (i = 0, j = 5; i < 10; i++,j++) {}; i + j", 25);
    test("int i, j; for (i = 0, j = 5; i < 10; i++,j++) {}; i + j", 25);
    test("int i = 5; for (i = 0; i < 10; i++) {}; i", 10);
    test("int i = 3, j = 6; for (i = 0; i < 5; i++) { j++ }; j", 11);
    testError("for (int i = 0;", "Unexpected end-of-file");
    testError("for (int i = 0;\n", "Unexpected end-of-file");
    testError("for (int i = 0; i < 10;", "Unexpected end-of-file");
    testError("for (int i = 0; i < 10; i++", "Unexpected end-of-file");
    testError("for (int i = 0)", "Unexpected token ')'");
    testError("for (int i = 0\n)", "Unexpected token ')'");
    testError("for (int i = 0;)", "Unexpected token ')'");
    testError("for (int i = 0;\n)", "Unexpected token ')'");
    testError("for (int i = 0; i < 10;)", "Unexpected end-of-file");
    testError("for (int i = 0; i < 10; i++)", "Unexpected end-of-file");
    test("int sum = 0; for (int i = 0; i < 10; i++) sum += i; sum", 45);
    test("int sum = 0; for (int i = (0 if true); i < 10 if true; i++ if true) sum += i; sum", 45);
    testError("int sum = 0; for (int i = 0; i < 10; i++) sum += i; i", "unknown variable");
    test("int sum = 0; for (int i = 0,j=10; i < 10; i++,j--) sum += i + j; sum", 100);
    test("int sum = 0; int i,j; for (sum = 20, i = 0,j=10; i < 10; i++,j--) sum += i + j; sum", 120);
    test("int sum = 0; int i,j; for (sum = 20, i = 0,j=10; i < 10; i++,j--) { sum += i + j; def i = 3; i++ }; sum", 120);
    test("int sum = 0; for (int i = 0; ;) { break if i > 5; i++; sum += i }; sum", 21);
    test("int sum = 0; for (int i = 0; true;) { break if i > 5; i++; sum += i }; sum", 21);
    test("int sum = 0; for (int i = 0; true; true) { break if i > 5; i++; sum += i }; sum", 21);
    test("int sum = 0; int i = 0; for (;;) { break if i > 5; i++; sum += i }; sum", 21);
    test("def f() { for (int i = 0; i < 10; ) i++ }; f()", null);
    test("def f() { for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("String f() { if (false) { return '' } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("Decimal f() { if (false) { return 7 } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("class X{}; X f() { if (false) { return new X() } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("int[] f() { if (false) { return new int[0] } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("int[][] f() { if (false) { return new int[0][] } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    test("long[] f() { if (false) { return new long[0] } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", null);
    testError("int f() { if (false) { return 7 } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", "implicit return of null incompatible with function return type");
    testError("long f() { if (false) { return 7 } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", "implicit return of null incompatible with function return type");
    testError("double f() { if (false) { return 7 } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", "implicit return of null incompatible with function return type");
    testError("boolean f() { if (false) { return 7 } else for (int i = 0; i < 10; ) { i++ if i >= 0 } }; f()", "implicit return of null incompatible with function return type");
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
    test("int sum = 0; double i = 0; do { if (i > 5) continue; sum += i } until (i++ == 10); sum", 15);
    test("def sum = 0; def i = 0.0D; do { if (i > 5) continue; sum += i } until (i++ == 10); sum", 15.0D);
    test("int sum = 0; double i = 0; do { if (i > 5) continue; sum = sum * 1 + i } until (i++ == 10); sum", 15);
    test("def sum = 0; def i = 0.0D; do { if (i > 5) continue; sum = sum * 1 + i } until (i++ == 10); sum", 15.0D);
    test("int sum = 0; double i = 0; do { if (i > 5 && i < 7) continue; sum += i } until (i++ == 10); sum", 49);
    test("int sum = 0; double i = 0; do { if (i > 5 && i < 7) { if (true) continue } ; sum += i } until (i++ == 10); sum", 49);
    test("int sum = 0; double i = 0; do { if (i > 5) break; sum += i } until (i++ == 10); sum", 15);
    test("int sum = 0; double i = 0; do { if (i > 5) { if (true) break}; sum += i } until (i++ == 10); sum", 15);
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
    test("def s=[1]; while (s.size())\n{\nfalse and continue\n(0 + (2*3) - 10 < s.size()) and s.remove(0) and continue\n}\ns", Utils.listOf());
    test("int i = 0; int sum = 0; while (i < 10) { int j = 0; LABEL: while (j < i) { sum++; j++; continue LABEL }; i++ }; sum", 45);
    test("if (true) { LABEL: while(false){}; 17 }", 17);
    testError("def x = 0; if (true) { LABEL: x++; while(false){}; 17 }", "label applied to statement that is not for/while");
    test("int i = 0; int sum = 0; while (i < 10) { XXX: while(false){}; int j = 0; LABEL: while (j < i) { sum++; j++; continue LABEL }; i++ }; sum", 45);
    test("int i = 0; int sum = 0; OUTER: while (i < 10) { int j = 0; LABEL: while (j < i) { sum++; j++; i++; continue OUTER }; i++ }; sum", 9);
    test("int i = 0; int sum = 0; OUTER:\n while (i < 15) { int j = 0; LABEL:\n while (j < i) { break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ }; sum++; i++ }; sum", 55);
    testError("int i = 0; int sum = 0; OUTER:\n while (i < 15) { int j = 0; LABEL:\n while (j < i) { break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ }; sum++; continue LABEL; i++ }; sum", "could not find enclosing loop");
    testError("int i = 0; int sum = 0; OUTER: while (i < 15) { int j = 0; LABEL: while (j < i) { break XXX if i >= 10; sum++; j++; continue LABEL }; i++ }; sum", "could not find enclosing loop");
    test("int i = 0; int sum = 0; do{ XXX: do{}until(true); int j = 0; LABEL: do{ sum++; j++; continue LABEL } until (j >= i); i++ } until (i >= 10); sum", 46);
    test("int i = 0; int sum = 0; OUTER: do{ int j = 0; LABEL: do{ sum++; j++; i++; continue OUTER } until (j >= i); i++ } until (i >= 10); sum", 10);
    test("int i = 0; int sum = 0; OUTER:\n do{ int j = 0; LABEL:\n do{ break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ } until (j >= i); sum++; i++ } until (i >=15); sum", 56);
    testError("int i = 0; int sum = 0; OUTER:\n do{ int j = 0; LABEL:\n do{ break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ } until (j >= i); sum++; continue LABEL; i++ } until (i >= 15); sum", "could not find enclosing loop");
    testError("int i = 0; int sum = 0; OUTER: do{ int j = 0; LABEL: do{ break XXX if i >= 10; sum++; j++; continue LABEL } until (j >= i); i++ } until (i >= 15); sum", "could not find enclosing loop");
    test("int sum = 0; LABEL: for (int i=0; i < 10; i++) { sum += i }; sum", 45);
    test("int sum = 0; for (int i=0; i < 10; ) { LABEL: for (int j = 0; j < i; ) { sum++; j++; continue LABEL }; i++ }; sum", 45);
    test("int sum = 0; OUTER: for (int i = 0; i < 10; ) { LABEL: for (int j = 0; j < i; j++) { sum++; i++; continue OUTER }; i++ }; sum", 9);
    test("int sum = 0; OUTER:\n for (int i = 0; i < 15; ) { LABEL:\n for (int j = 0; j < i; ) { break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ }; sum++; i++ }; sum", 55);
    testError("int sum = 0; OUTER: for (int i = 0; i < 10; ) { LABEL: for (int j = 0; j < i; j++) { sum++; i++; continue XXX }; i++ }; sum", "could not find enclosing loop");
    testError("int sum = 0; OUTER:\n for (int i = 0; i < 15; ) { LABEL:\n for (int j = 0; j < i; ) { break OUTER if i >= 10; sum++; j++; continue LABEL; j++; i++ }; sum++; continue LABEL; i++ }; sum",  "could not find enclosing loop");
    testError("LABEL: println 'xxx'", "labels can only be applied to for, while, and do/until");
    testError("LABEL\n: while (false) {}; 1", "unexpected token ':'");
  }

  @Test public void simpleFunctions() {
    testError("def f; f()", "null value for function");
    test("def f() {return}; f()", null);
    test("int f(byte x) { x * x }; f(2)", 4);
    test("int f(int x) { x * x }; f(2)", 4);
    test("def f(def x) { x * x }; f(2)", 4);
    test("def f(x) { x * x }; f(2)", 4);
    test("byte f(x) { if (x == 1) 1 else x * f(x - 1) }; f(3)", (byte)6);
    test("int f(x) { if (x == 1) 1 else x * f(x - 1) }; f(3)", 6);
    test("def f(x) { if (x == 1) 1 else x * f(x - 1) }; f(3) + f(4)", 30);
    testError("def f(int x) { def x = 1; x++ }", "clashes with previously declared variable");

    test("byte f(x) { { def x = 3; return x } }; f(1)", (byte)3);
    test("byte f(x) { { def x = 3; x } }; f(1)", (byte)3);
    test("byte f() { 3L }; f()", (byte)3);
    test("byte f() { 3.0 }; f()", (byte)3);
    test("byte f() { 3.0D }; f()", (byte)3);
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
    test("byte f(x,y) { x + y }; f(1L,2.0)", (byte)3);
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
    test("def f() { int i = 1; return {++i}}; def x=f(); def y=f(); [x()+x()+x(),y()]", Utils.listOf(9,2));
    test("String f(x) { x.toString() }; f(1)", "1");
    test("List f(x) { [x] }; f(1)", Utils.listOf(1));
    test("Map f(x) { [x:x] }; f(1)", Utils.mapOf("x",1));

    test("def f(int x) { x }; f(1L)", 1);
    test("def f(byte x) { x }; f(1L)", (byte)1);
    test("def f(byte x, byte y) { x+y }; f(1L if true, 2 unless false)", (byte)3);
  }

  @Test public void badFunctionDecl() {
    testError("def f(a,) {}", "expected valid type or parameter name");
    testError("def f(int a,) {}", "expected valid type or parameter name");
    testError("def f(int,) {}", "expecting identifier");
    testError("def f(,b) {}", "expected valid type or parameter name");
    testError("def f(,int b) {}", "expected valid type or parameter name");
    testError("def f(,) {}", "expected valid type or parameter name");
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
    test("def x = [1,2,3]; def f = x.map; f{it+it}", Utils.listOf(2,4,6));
    test("def x = [1,2,3]; def f = x.map; x = [4,5]; f{it+it}", Utils.listOf(2,4,6));
    test("def f(x){x*x}; def m = [g:f, '1':f, true:f, false:f, null:f]; m.g(2) + m.1(3) + m.true(4) + m.false(5) + m.null(6)", 4+9+16+25+36);
    test("def f(x){x*x}; def m = [g:f, if:f, true:f, false:f, null:f]; m.g(2) + m.if(3) + m.true(4) + m.false(5) + m.null(6)", 4+9+16+25+36);
  }

  @Test public void functionsForwardReference() {
    test("def x = f(2); def f(z){3*z}; x", 6);
    test("def y=3; def x=f(2); def f(z){y*z}; x", 6);
    test("{ def y=3; def x=f(2); def f(z){y*z}; x }", 6);
    test("def f(x) { 2 * g(x) }; def g(x) { x * x }; f(3)", 18);
    test("def y = 3; def f(x) { 2 * g(x) }; def z = f(3); def g(x) { x * y }; z", 18);
    test("def f(x) { if (x==1) x else 2 * g(x) }; def g(x) { x + f(x-1) }; f(3)", 18);
    test("def f(x) { g(x) }; def g(x) { x }; f(3)", 3);
    testError("def x = f(2); def y = 3; def f(z){y*z}; x", "closes over variable y not yet declared");
    testError("{ def x = f(2); def y = 3; def f(z){y*z}; x }", "closes over variable y not yet declared");
    testError("def f(x) { g(x) }; def y = 2; def g(x) { x + y}; f(3)", "closes over variable y not yet declared");
    testError("def f(x){def h=g; h(x)}; def g(x){x*x}; f(2)", "closes over variable g that has not yet been initialised");
  }

  @Test public void functionsWithOptionalArgs() {
    test("def f(byte x = 5) { x * x }; f()", (byte)25);
    test("def f(byte x = 5) { x * x }; f(3)", (byte)9);
    test("def f(byte x = 5) { x * x }; def g = f; g()", (byte)25);
    test("def f(byte x = 5) { x * x }; def g = f; g(3)", (byte)9);
    test("def f(byte x = 5) { x * x }; var g = f; g()", (byte)25);
    test("def f(byte x = 5) { x * x }; var g = f; g(3)", (byte)9);
    test("byte f(byte x = 5) { x * x }; f()", (byte)25);
    test("byte f(byte x = 5) { x * x }; f(3)", (byte)9);
    test("byte f(byte x = 5) { x * x }; var g = f; g()", (byte)25);
    test("byte f(byte x = 5) { x * x }; var g = f; g(3)", (byte)9);
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
    test("def f(byte x,byte y=x+1) { x + y }; f(2)", (byte)5);
    test("def f(byte x,byte y=++x+1) { x + y }; f(2)", (byte)7);
    test("def f(byte x,byte y=++x+1, byte z=x+1) { x + y + z }; f(2)", (byte)11);
    test("def f(int x,int y=x+1) { x + y }; f(2)", 5);
    test("def f(int x,int y=++x+1) { x + y }; f(2)", 7);
    test("def f(int x,int y=++x+1, int z=x+1) { x + y + z }; f(2)", 11);
    test("def f(x,y=x+1) { x + y }; f(2)", 5);
    testError("def f(x,y=z+1,z=2) { x + y }; f(2)", "reference to unknown variable");
    test("def f(int x = f(1)) { if (x == 1) 9 else x }; f(3)", 3);
    test("def f(int x = f(1)) { if (x == 1) 9 else x }; f()", 9);
    test("def g(x){def y=g; f(x)}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    test("def g(x){f(x)}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    test("def f(x,a=f){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    testError("def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; f(2)", "closes over variable h that has not yet been initialised");
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
    test("def f(List x, byte y) { x + y }; f([[1,2],3])", Utils.listOf(1,2,(byte)3));
    test("def f(List x, int y) { x + y }; f([[1,2],3])", Utils.listOf(1,2,3));
    test("def f = { List x, int y -> x + y }; f([[1,2],3])", Utils.listOf(1,2,3));
    test("def f = { List x, int y -> x + y }; def a = [[1,2],3]; f(a)", Utils.listOf(1,2,3));
    test("def f(List x, int y) { x + y }; def a = [[1,2],3]; f(a)", Utils.listOf(1,2,3));
    test("def f(List x, int y) { x + y }; def a = [[1,2],3]; def g = f; g(a)", Utils.listOf(1,2,3));
    test("def f(List x, int y = 4) { x + y }; f([[1,2],3])", Utils.listOf(Utils.listOf(1,2),3,4));
    test("def f = {List x, int y = 4 -> x + y }; f([[1,2],3])", Utils.listOf(Utils.listOf(1,2),3,4));
    test("def f = {List x, int y = 4 -> x + y }; def a = [[1,2],3]; f(a)", Utils.listOf(Utils.listOf(1,2),3,4));
    test("def f(List x, int y = 4) { x + y }; def a = [[1,2],3]; f(a)", Utils.listOf(Utils.listOf(1,2),3,4));
    test("def f(List x, int y = 4) { x + y }; def a = [[1,2],3]; def g = f; g(a)", Utils.listOf(Utils.listOf(1,2),3,4));
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
    test("def f(byte x = 3, byte y = 4) { x + y }; def a = [1.0,2L]; def g = f; g(a)", (byte)3);

    test("def f(long t, def x) { sleep(t,x) }; f([1,2])", 2);
    testError("def f(long t, def x) { sleep(t,x) }; f(['123',2])", "cannot be cast to number");
    test("def f(long t, def x) { sleep(t,x) }; def a = [1,2]; f(a)", 2);
    testError("def f(long t, def x) { sleep(t,x) }; def a = ['123',2]; f(a)", "cannot be cast to number");
    test("def f(long t, def x) { sleep(t,x) }; [[1,2],[3,4]].map{ a,b -> f([a,b]) }", Utils.listOf(2,4));
    test("def f(long t, def x) { sleep(t,x) }; [a:2,b:4].map{ a,b -> f([b,a]) }", Utils.listOf("a","b"));
    test("def f(long t, def x) { sleep(t,x) }; def x = [[1,2],[3,4]]; x.map{ a,b -> f([a,b]) }", Utils.listOf(2,4));
    testError("def f(long t, def x) { sleep(t,x) }; def x = [a:2,b:4]; x.map([{ a,b -> f([b,a]) }])", "cannot be cast");
    testError("def f(long t, def x) { sleep(t,x) }; def g = [[1,2],[3,4]].map; g([{ a,b -> f([a,b]) }])", "cannot be cast");
    testError("def f(long t, def x) { sleep(t,x) }; def g = [a:2,b:4].map; g([{ a,b -> f([b,a]) }])", "cannot be cast");
    test("def a = [1,2,3]; def f(x,y=7,z) { x + y + z }; f(1,2,3) + f(a)", 12);
    test("def a = [1,2,3]; def f(x,y=7,z=8) { x + y + z }; f(a)", Utils.listOf(1,2,3,7,8));
    test("def f(String x, int y) { x + y }; def a = ['x',2]; f(a)", "x2");
    test("def f = {String x, int y -> x + y }; def a = ['x',2]; f(a)", "x2");
    testError("def f = {String x, int y -> x + y }; def a = [2,'x']; f(a)", "cannot convert object of type int to string");
    test("def f(x,y,z) { x + y + z }; f(1,2,3) + f([1,2,3])", 12);
    test("def f = { x,y,z -> x + y + z }; f(1,2,3) + f([1,2,3])", 12);
    test("def f = { x,y,z -> x + y + z }; def a = [1,2,3]; f(1,2,3) + f(a)", 12);
    test("def f = { x,y,z=3 -> x + y + z }; def a = [1,2]; f(1,2,3) + f(a)", 12);
    test("def f(x, y) { x + y }; f([1,2])", 3);
    test("def f(x, y=3) { x + y }; f([1,2])", Utils.listOf(1,2,3));
    test("def f(x, y) { x + y }; def a = [1,2]; f(a)", 3);
    test("def f(x, y=3) { x + y }; def a = [1,2]; f(a)", Utils.listOf(1,2,3));
    test("def f(x, y=3) { x + y }; f(1,2)", 3);
    test("def f(List x, y=3) { x + y }; f([1,2])", Utils.listOf(1,2,3));
    testError("def f(List x, y=4, z) { x + y + z }; f([1, 2, 3])", "int cannot be cast to List");
  }

  @Test public void passingNamedArgs() {
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,yy:2)", "no such parameter: yy");
    testError("def f(int x, y=[a:3]) { x + y }; f(x:'123',y:2)", "cannot be cast to int");
    testError("def f(x, y=[a:3]) { x + y }; f(x:1,(''+'y'):2)", "invalid parameter name");
    test("def f(x, y=[a:3]) { x + y }; f(x:1,y:2)", 3);
    testError("def f(x, y) { x + y }; f([x:1,y:2])", "missing mandatory argument: y");
    test("def f(x, y=[a:3]) { x + y }; f([x:1,y:2])", Utils.mapOf("x",1,"y",2,"a",3));
    test("def f(x,y) { x*y }; f(x:2,y:3)", 6);
    test("def f(byte x, byte y) { x*y }; f(x:2,y:3)", (byte)6);
    test("def f(int x, int y) { x*y }; f(x:2,y:3)", 6);
    test("def f(int x, int y=3) { x*y }; f(x:2)", 6);
    test("def f(Map x, int y=3) { x.y = y; x }; f([x:2])", Utils.mapOf("x",2,"y",3));
    testError("def f(Map x, int y=3) { x.y = y; x }; f(x:2)", "cannot convert argument of type int to parameter type of Map");
    test("def f(def x, int y=3) { x.y = y; x }; f([x:2])", Utils.mapOf("x",2,"y",3));
    testError("def f(def x, int y=3) { x.y = y; x }; f(x:2)", "invalid object type");
    test("def f(Map x, int y=3) { x.y = y; x }; f([x:2,y:4])", Utils.mapOf("x",2,"y",3));
    test("def f(def x, int y=3) { x.y = y; x }; f([x:2,y:4])", Utils.mapOf("x",2,"y",3));
    test("def f(int x) { x*3 }; f(x:2)", 6);
    test("def f(int x=2) { x*3 }; f(x:2)", 6);
    test("def f(x,y) { x + y }; f(x:2,y:3)", 5);
    test("def f(x,y=[a:1]) { x + y }; f([x:2,y:3])", Utils.mapOf("x",2,"y",3,"a",1));
    test("def f(x,y=[a:1]) { x + y }; f(x:2,y:3)", 5);
    testError("def f(x,y,z) {x + y + z}; f(x:2,y:3)", "missing mandatory argument: z");
    testError("def f(x,y,z) {x + y + z}; f(y:3)", "missing mandatory arguments");
    testError("def f(x,y,z) {x + y + z}; f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("def f(x,y,z) {x + y + z}; f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
    testError("def f = { x,y -> x*y }; def a = [x:2,y:3]; f(a)", "missing mandatory arguments");
    test("def f = { x,y=[a:1] -> x + y }; def a = [x:2,y:3]; f(a)", Utils.mapOf("x",2,"y",3,"a",1));
    test("def f = { x,y=[a:1] -> x + y }; f(x:2,y:3)", 5);
    testError("def f = { x,y,z -> x + y + z}; def a = [x:2,y:3]; f(a)", "missing mandatory arguments");
    testError("def f = { x,y,z -> x + y + z}; def a = [y:3]; f(a)", "missing mandatory arguments");
    testError("sleep(x:1,y:2)", "no such parameters");
    testError("sleep([x:1,y:2])", "cannot convert argument of type map");
    testError("def a = [x:{it*it}]; [1,2,3].map(a)", "cannot be cast to function");
    testError("def f(a, b, c) { c(a+b) }; f(a:2,b:3) { it*it }", "missing mandatory argument: c");
    test("def f(a = '',b = 2) {a+b}; f('',null)", "null");
    test("def f(a = '',b = 2) {a+b}; f(a:'',b:null)", "null");
    test("def f(a = '',b = 2) {a+b}; f(a:'' if true,b:null unless true)", "null");
    test("def f(a = ('' if true),b = 2) {a+b}; f(a:'' if true,b:null unless true)", "null");
    testError("def f(a,b) {a+b}; f(a:123,", "Unexpected end-of-file");
  }

  @Test public void simpleClosures() {
    test("{;}()", null);
    test("int i = 1, j = 2; boolean b = i == j-1; b == true",  true);
    test("{ int i = 1, j = 2; j - i == i }()",  true);
    test("def f = { -> 10 }; f()", 10);
    test("var f = { -> 10 }; var g = {20}; f = g; f()", 20);
    testError("def f = { -> 10 }; f(3)", "too many arguments");
    test("byte i = 1; { byte i = 2; i++; }; i", (byte)1);
    test("byte i = 1; { int i = 2; i++; }; i", (byte)1);
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
    testError("var f = { x -> x*x }; f = 3", "cannot convert from int to function");
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
    test("List trees = [[1,2],[3,4]]; def f = { x,y -> \ndef h = trees[x][y]\ntrees[x][y] + h}; f(1,0)", 6);
    test("def trees = [[1,2],[3,4]]; def f = { x,y -> \ndef h = trees[x][y]\ntrees[x][y] + h}; f(1,0)", 6);
  }

  @Test public void closedOverVars() {
    test("List s = null; { s }(); s", null);
    test("Map s = null; { s }(); s", null);
    test("Decimal s = null; { s }(); s", null);
    test("String s = null; { s }(); s", null);
    test("int x = 1; def f() { x.size() }; f()", 1);
    test("byte x = 1; def f() { x-- }; x = 2; x", (byte)2);
    test("byte x = 1; def f() { x-- }; x++", (byte)1);
    test("byte x = 1; def f() { x-- }; ++x", (byte)2);
    test("byte x = 1; def f() { x++ }; f(); x", (byte)2);
    test("byte x = 1; def f() { x++ }; f() + x", (byte)3);
    test("byte x = 1; def f() { def g() { x++ }; g() }; f(); x", (byte)2);
    test("byte x = 1; def f = { x++ }; f(); x", (byte)2);
    test("byte x = 1; def f = { x++ }; f() + x", (byte)3);
    test("byte x = 1; def f = { def g() { x++ }; g() }; f(); x", (byte)2);
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
    test("def x=1; def f(a){g(a)}; def g(a){x+a}; f(2)", 3);
    test("def x=1; def f(y){def ff() {g(y)}; ff()}; def g(a){x+a}; f(2)", 3);
    test("def x=1; def f(x){def ff() {g(x)}; ff()}; def g(a){x+a}; f(2)", 3);
    test("def x=1; def f(a){x+g(a)}; def g(a){x+a}; f(2)", 4);
    test("def x=1; def y=2; def f(a){g(a)}; def g(a){x+y+a}; f(2)", 5);
    test("def x=1; def y=2; def f(a){x;y;g(a)}; def g(a){x+y+a}; f(2)", 5);
    test("def x=1; def f = { g(it) }; def g(a){x+a}; f(2)", 3);
    test("def x=1; def f = {x+g(it)}; def g(a){x+a}; f(2)", 4);
    test("def x=1; def y=2; def f = {g(it)}; def g(a){x+y+a}; f(2)", 5);
    test("def x=1; def y=2; def f = {x;y;g(it)}; def g(a){x+y+a}; f(2)", 5);
    test("def g(x){x}; def f() { def a = g; a(3) }; f()", 3);
    test("def g(x){x}; def f(x){def a = g; x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    test("def g(x){x}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)", 3);
    test("def i=3; def g(x){f(x)}; def f(x){x+i}; g(2)", 5);
    test("def i=3; def g(x){f(x)}; def f(x,y=i){x+y}; g(2)", 5);
    test("def h; def g(x){f(x)}; h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; g(2)", 3);
    testError("def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; g(2)", "closes over variable h");
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
    test("def i = 0; def f(x){ x == 1 ? ++i * x : ++i * f(x-1) }; f(3) + i", 9);
    test("def i = 1; def f(x){x+i}; def g(x){f(x)}; g(2)", 3);
    test("def i = 1; def f(x){x+i}; def g(i){f(i)}; g(2)", 3);
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
    test("String x = 'x'; x += (byte)1", "x1");
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

    test("'a' * (byte)1", "a");
    test("'a' * 1", "a");
    test("'a' * 0", "");
    test("'' * 0", "");
    test("'' * 1", "");
    test("'' * 2", "");
    test("'a' * (byte)2", "aa");
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
    test("[]+[]", Utils.listOf());
    test("def x = []; x + x", Utils.listOf());
    test("[] + (byte)1", Utils.listOf((byte)1));
    test("[] + 1", Utils.listOf(1));
    test("[] + [a:1]", Utils.listOf(Utils.mapOf("a",1)));
    test("[1] + 2", Utils.listOf(1,2));
    test("[1] + [2]", Utils.listOf(1,2));
    test("[1,2] + [3,4]", Utils.listOf(1,2,3,4));
    test("def x = [1,2]; x + [3,4]", Utils.listOf(1,2,3,4));
    test("def x = [1,2]; x + [[3,4]]", Utils.listOf(1,2,Utils.listOf(3,4)));
    test("def x = ['a','b']; x + [[3,4]]", Utils.listOf("a","b",Utils.listOf(3,4)));
    test("def x = [1,2]; def y = [3,4]; x + y", Utils.listOf(1,2,3,4));
    test("['a','b'] + 'c'", Utils.listOf("a","b","c"));
    test("['a','b'] + 1", Utils.listOf("a","b",1));
    test("def x = ['a','b']; def y = 'c'; x + y", Utils.listOf("a","b","c"));
    test("var x = ['a','b']; x += 'c'", Utils.listOf("a","b","c"));
    test("def x = ['a','b']; x += 'c'", Utils.listOf("a","b","c"));
    test("var x = ['a','b']; x += ['c','d']", Utils.listOf("a","b","c","d"));
    test("def x = ['a','b']; x += ['c','d']", Utils.listOf("a","b","c","d"));
    test("var x = ['a','b']; x += x", Utils.listOf("a","b","a","b"));
    test("def x = ['a','b']; x += x", Utils.listOf("a","b","a","b"));
    test("var x = ['a','b']; def y = 'c'; x += y", Utils.listOf("a","b","c"));
    test("var x = ['a','b']; var y = 'c'; x += y", Utils.listOf("a","b","c"));
    test("var x = ['a','b']; def y = 'c'; x += y; x", Utils.listOf("a","b","c"));
    test("def x = ['a','b']; def y = 'c'; x += y", Utils.listOf("a","b","c"));
    test("def x = ['a','b']; var y = 'c'; x += y", Utils.listOf("a","b","c"));
    test("var x = ['a','b']; def y = ['c','d']; x += y", Utils.listOf("a","b","c","d"));
    test("var x = ['a','b']; var y = ['c','d']; x += y", Utils.listOf("a","b","c","d"));
    test("def x = ['a','b']; var y = ['c','d']; x += y", Utils.listOf("a","b","c","d"));
    test("var x = ['a','b']; x += x", Utils.listOf("a","b","a","b"));
    test("def x = ['a','b']; x += x", Utils.listOf("a","b","a","b"));
    test("var x = ['a','b']; x += x; x", Utils.listOf("a","b","a","b"));
    test("def x = ['a','b']; x += x; x", Utils.listOf("a","b","a","b"));
    test("var x = ['a','b']; x += 1; x", Utils.listOf("a","b",1));
    test("def x = ['a','b']; x += 1; x", Utils.listOf("a","b",1));
    test("'abc'.map{it} + 'd'", Utils.listOf("a","b","c","d"));
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

  @Test public void listAddSingle() {
    test("[] << 1", Utils.listOf(1));
    testError("[] <<= 1", "invalid lvalue");
    test("def x = []; x << (byte)1", Utils.listOf((byte)1));
    test("def x = []; x << 1", Utils.listOf(1));
    test("def x = []; x << 1; x", Utils.listOf());
    test("List x = []; x << 1; x", Utils.listOf());
    test("def x = []; x <<= 1; x", Utils.listOf(1));
    test("List x = []; x <<= 1; x", Utils.listOf(1));
    test("List x = []; x <<= 1 << 2; x", Utils.listOf(4));
    test("List x = []; x <<= [1] << 2; x", Utils.listOf(Utils.listOf(1,2)));
    test("List x = ['a','b']; x <<= [1] << 2", Utils.listOf("a","b",Utils.listOf(1,2)));
    test("List x = ['a','b']; x <<= [1] << 2; x", Utils.listOf("a","b",Utils.listOf(1,2)));
    test("def x = ['a','b']; def y = [1] << 2; x <<= y", Utils.listOf("a","b",Utils.listOf(1,2)));
    test("def x = ['a','b']; def y = [1] << 2; x <<= y; x", Utils.listOf("a","b",Utils.listOf(1,2)));
    test("[1,2] << [a:1]", Utils.listOf(1,2,Utils.mapOf("a",1)));
    test("[1,2].flatMap{ [it,it] } << [a:1]", Utils.listOf(1,1,2,2,Utils.mapOf("a",1)));
  }


  @Test public void mapAdd() {
    test("[:] + [:]", Utils.mapOf());
    test("def x = [:]; x + x", Utils.mapOf());
    test("[:] + [a:1]", Utils.mapOf("a",1));
    test("def x = [a:1]; [:] + x", Utils.mapOf("a",1));
    test("def x = [a:1]; x + x", Utils.mapOf("a",1));
    test("[a:1] + [b:2]", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1]; def y = [b:2]; x + y", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1]; def y = [a:2]; x + y", Utils.mapOf("a",2));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x + y", Utils.mapOf("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y", Utils.mapOf("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y", Utils.mapOf("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x", Utils.mapOf("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x", Utils.mapOf("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x += x", Utils.mapOf("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x += x", Utils.mapOf("a",2,"b",2,"c",3));
    test("def x = [a:1,b:2]; def y = [a:2,c:3]; x += y; x += x; x", Utils.mapOf("a",2,"b",2,"c",3));
    test("var x = [a:1,b:2]; var y = [a:2,c:3]; x += y; x += x; x", Utils.mapOf("a",2,"b",2,"c",3));
    testError("var x = [a:1,b:2]; x -= 1; x", "cannot subtract int from map");
    testError("def x = [a:1,b:2]; x -= 1; x", "cannot subtract");
    testError("var x = [a:1,b:2]; x++", "unary operator '++' cannot be applied to type map");
    testError("def x = [a:1,b:2]; x++", "non-numeric operand");
    testError("var x = [a:1,b:2]; ++x", "operator '++' cannot be applied to type map");
    testError("def x = [a:1,b:2]; ++x", "non-numeric operand");
    testError("var x = [a:1,b:2]; x--", "operator '--' cannot be applied to type map");
    testError("def x = [a:1,b:2]; x--", "non-numeric operand");
    testError("var x = [a:1,b:2]; --x", "operator '--' cannot be applied to type map");
    testError("def x = [a:1,b:2]; --x", "non-numeric operand");
    testError("[:] + [1,2,3]", "cannot add list");
    testError("def x = [:]; def y = [1,2,3]; x + y", "cannot add list");
  }

  @Test public void mapSubtract() {
    test("[:] - [:]", Utils.mapOf());
    test("[a:1] - [:]", Utils.mapOf("a",1));
    test("[:] - [a:1]", Utils.mapOf());
    test("[a:1,b:2,c:[a:1]] - [c:1]", Utils.mapOf("a",1,"b",2));
    test("Map x = [:]; x - [:]", Utils.mapOf());
    test("Map x = [a:1]; x - [:]", Utils.mapOf("a",1));
    test("Map x = [:]; x - [a:1]", Utils.mapOf());
    test("Map x = [a:1,b:2,c:[a:1]]; x - [c:1]", Utils.mapOf("a",1,"b",2));
    test("Map x = [a:1,b:2,c:[a:1]]; x -= [c:1]; x", Utils.mapOf("a",1,"b",2));
    test("Map x = [a:1,b:2,c:[a:1]]; x - [c:1] - [b:4]", Utils.mapOf("a",1));
    test("def x = [:]; x - [:]", Utils.mapOf());
    test("def x = [a:1]; x - [:]", Utils.mapOf("a",1));
    test("def x = [:]; x - [a:1]", Utils.mapOf());
    test("def x = [a:1,b:2,c:[a:1]]; x - [c:1]", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1,b:2,c:[a:1]]; x - [c:1] - [b:4]", Utils.mapOf("a",1));
    test("def x = [:]; def y = [:]; x - y", Utils.mapOf());
    test("def x = [a:1]; def y = [:]; x - y", Utils.mapOf("a",1));
    test("def x = [:]; def y = [a:1]; x - y", Utils.mapOf());
    test("def x = [a:1,b:2,c:[a:1]]; def y = [c:1]; x - y", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1,b:2,c:[a:1]]; def y = [c:1]; x = x - y; x - [b:4]", Utils.mapOf("a",1));
    test("def x = [a:1,b:2,c:[a:1]]; def y = [c:1]; x -= y; x - [b:4]", Utils.mapOf("a",1));
    test("[:] - []", Utils.mapOf());
    test("[a:1] - []", Utils.mapOf("a",1));
    test("[:] - ['a']", Utils.mapOf());
    test("[a:1,b:2,c:[a:1]] - ['c']", Utils.mapOf("a",1,"b",2));
    test("Map x = [:]; x - []", Utils.mapOf());
    test("Map x = [a:1]; x - []", Utils.mapOf("a",1));
    test("Map x = [:]; x - ['a']", Utils.mapOf());
    test("Map x = [a:1,b:2,c:[a:1]]; x - ['c']", Utils.mapOf("a",1,"b",2));
    test("Map x = [a:1,b:2,c:[a:1]]; x -= ['c']; x", Utils.mapOf("a",1,"b",2));
    test("Map x = [a:1,b:2,c:[a:1]]; x - ['c'] - [b:4]", Utils.mapOf("a",1));
    test("def x = [:]; x - []", Utils.mapOf());
    test("def x = [a:1]; x - []", Utils.mapOf("a",1));
    test("def x = [:]; x - ['a']", Utils.mapOf());
    test("def x = [a:1,b:2,c:[a:1]]; x - ['c']", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1,b:2,c:[a:1]]; x - ['c'] - [b:4]", Utils.mapOf("a",1));
    test("def x = [:]; def y = []; x - y", Utils.mapOf());
    test("def x = [a:1]; def y = []; x - y", Utils.mapOf("a",1));
    test("def x = [:]; def y = ['a']; x - y", Utils.mapOf());
    test("def x = [a:1,b:2,c:[a:1]]; def y = ['c']; x - y", Utils.mapOf("a",1,"b",2));
    test("def x = [a:1,b:2,c:[a:1]]; def y = ['c']; x = x - y; x - [b:4]", Utils.mapOf("a",1));
    test("def x = [a:1,b:2,c:[a:1]]; def y = ['c']; x -= y; x - [b:4]", Utils.mapOf("a",1));
    testError("[a:1] - 1", "cannot subtract");
    testError("def x = [a:1]; x - 1", "cannot subtract");
  }

  @Test public void classNameErrors() {
    testError("class X{}\nX\n1", "class name not allowed");
    testError("class X{}; ++X", "class name not allowed");
    testError("class X{ class Y{} }; X.Y += 4", "non-numeric operand");
    testError("class X{ class Y{} }; X.Y++", "non-numeric operand");
    testError("class X{ class Y{} }; ++X.Y", "non-numeric operand");
    testError("class X{}; X++", "class name not allowed");
    testError("class X{}; !X", "class name not allowed");
    testError("class X{ class Y{} }; !X.Y", "class name not allowed");
    testError("class X{}; X == 1", "class name not allowed");
    testError("class X{}; X; 1", "class name not allowed");
    testError("class X{}; X", "class name not allowed");
    testError("class X{}; def x = X; 1", "class name not allowed");
    testError("int; 1", "unexpected token 'int'");
    testError("def x = int; 1", "unexpected token 'int'");
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
    test("def x =\n1;\ntrue\nand\nx = 2;\n x", 2);
    test("def x = (byte)1; x = 2 and true", true);
    test("def x = 1; x = 2 and true", true);
    test("def x = 1; x = 2 and true; x", 2);
    test("def it = 'abc'; /a/r ? true : false", true);
    test("def it = 'abc'; def x; /a/r and x = 'xxx'; x", "xxx");
    test("def x = 0; for (int i = 0; i < 10; i++) { i < 5 and do { x += i; true } and continue; x++ }; x", 15);
    test("int f() { true and return 1; return 0 }; f()", 1);
    testError("superFields and print 'xxx'", "reference to unknown variable");
  }
}
