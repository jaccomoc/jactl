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

import java.util.List;
import java.util.Map;

public class ClassTests extends BaseTest {

  @Test public void simpleClass() {
    test("class X {}", null);
    test("class X { static def f(){3} }; X.f()", 3);
    test("class X { static int f(x){x*x} }; X.f(3)", 9);
    testError("class X { static int f(x){x*x} }; def g = X.ff; g(3)", "No such field or method 'ff'");
    test("class X { static def f(x){x*x} }; new X().f(3)", 9);
    test("class X { static int f(x){x*x} }; def g = X.f; g(3)", 9);
    test("class X { static int f(x){x*x} }; def g = new X().f; g(3)", 9);
    test("class X { static int f(x){x*x} }; X.\"${'f'}\"(3)", 9);
    test("class X { static int f(x){x*x} }; new X().\"${'f'}\"(3)", 9);
    test("class X { static int f(x){x*x} }; def g = X.\"${'f'}\"; g(3)", 9);
    test("class X { static def f(x){x*x} }; def g = new X().f; g(3)", 9);
    test("class X { def f(){3} }; new X().f()", 3);
    test("class X { def f(){3} }; new X().\"${'f'}\"()", 3);
    test("class X { def f(){3} }; def g = new X().f; g()", 3);
    test("class X { def f(x){x*x} }; new X().f(3)", 9);
    test("class X { def f(x){x*x} }; def g = new X().f; g(3)", 9);
    test("class X { int i = 2; def f(x){i*x} }; new X().f(3)", 6);
    test("class X { int i = 2; def f(x){i*x} }; def g = new X().f; g(3)", 6);
    test("class X { int i = 2; def f(x){i*x} }; def g = new X().\"${'f'}\"; g(3)", 6);
    test("class X { def f(x){x*x} }; X x = new X(); x.f(3)", 9);
    test("class X { def f(x){x*x} }; X x = new X(); def g = x.f; g(3)", 9);
    test("class X { int i = 2; def f(x){i*x} }; X x = new X(); def g = x.f; g(3)", 6);
    test("class X { int i = 2; def f(x){i*x} }; X x = new X(); def g = x.\"${'f'}\"; g(3)", 6);
    test("class X { def f(x){x*x} }; def x = new X(); x.f(3)", 9);
    test("class X { def f(x){x*x} }; def x = new X(); def g = x.f; g(3)", 9);
    test("class X { int i = 2; def f(x){i*x} }; def x = new X(); def g = x.f; g(3)", 6);
    test("class X { int i = 2; def f(x){i*x} }; def x = new X(); def g = x.\"${'f'}\"; g(3)", 6);
//    test("class X { Y y }; class Y { X x };", null);
  }

  @Test public void fieldClashWithBuiltinMethod() {
    testError("class X { int toString = 1 }; new X().toString", "clashes with builtin method");
  }

  @Test public void simpleFields() {
    test("class X { var i = 1 }; new X().i", 1);
    test("class X { int i }; new X().i", 0);
    test("class X { long i }; new X().i", 0L);
    test("class X { double i }; new X().i", 0D);
    test("class X { Decimal i }; new X().i", "#0");
    test("class X { String i }; new X().i", "");
    test("class X { Map i }; new X().i", Map.of());
    test("class X { List i }; new X().i", List.of());
    test("class X { def i }; new X().i", null);
    test("class X { int i = 1 }; new X().i", 1);
    test("class X { int i = 1 }; new X().\"${'i'}\"", 1);
    test("class X { int i = 1 }; X x = new X(); x.\"${'i'}\"", 1);
    test("class X { int i = 1 }; def x = new X(); x.\"${'i'}\"", 1);
    test("class X { int i = 1 }; new X().i = 3", 3);
    test("class X { int i = 1 }; new X(3).i", 3);
    test("class X { int i = 1; int j = i+1 }; new X().j", 2);
    test("class X { int i = 1; int j = i+1 }; new X(3).j", 4);
    test("class X { int i = 1; int j = i+1 }; new X(3,7).j", 7);
  }

  @Test public void mutatingPrimitiveFieldValues() {
    test("class X { int i = 1 }; new X().i = 3", 3);
    test("class X { int i = 1 }; new X().\"${'i'}\" = 3", 3);
    test("class X { int i = 1 }; X x = new X(); x.\"${'i'}\" = 3", 3);
    test("class X { int i = 1 }; def x = new X(); x.\"${'i'}\" = 3", 3);
    test("class X { int i = 1 }; new X().i += 3", 4);
    test("class X { int i = 1 }; new X().\"${'i'}\" += 3", 4);
    test("class X { int i = 1 }; X x = new X(); x.\"${'i'}\" += 3", 4);
    test("class X { int i = 1 }; def x = new X(); x.\"${'i'}\" += 3", 4);
    test("class X { int i = 1 }; new X().i++", 1);
    test("class X { int i = 1 }; ++new X().i", 2);
    test("class X { int i = 1 }; def x = new X(); x.i++; x.i", 2);
    test("class X { int i = 1 }; ++new X().\"${'i'}\"", 2);
    test("class X { int i = 1 }; X x = new X(); ++x.\"${'i'}\"", 2);
    test("class X { int i = 1 }; def x = new X(); x.\"${'i'}\"++", 1);
    test("class X { int i = 1 }; def x = new X(); ++x.\"${'i'}\"", 2);
    test("class X { int i = 1 }; new X().i = 3L", 3);
    test("class X { int i = 1 }; new X().\"${'i'}\" = 3L", 3);
    test("class X { int i = 1 }; new X().i += 3L", 4);
    test("class X { int i = 1 }; new X().\"${'i'}\" += 3L", 4);
    test("class X { int i = 1 }; new X().i = 3.0D", 3);
    test("class X { int i = 1 }; new X().\"${'i'}\" = 3.0D", 3);
    test("class X { int i = 1 }; new X().i += 3.0", 4);
    test("class X { int i = 1 }; new X().\"${'i'}\" += 3.0", 4);

    test("class X { var i = 1 }; new X().i = 3", 3);
    test("class X { var i = 1 }; new X().\"${'i'}\" = 3", 3);
    test("class X { var i = 1 }; X x = new X(); x.\"${'i'}\" = 3", 3);
    test("class X { var i = 1 }; def x = new X(); x.\"${'i'}\" = 3", 3);
    test("class X { var i = 1 }; new X().i += 3", 4);
    test("class X { var i = 1 }; new X().\"${'i'}\" += 3", 4);
    test("class X { var i = 1 }; X x = new X(); x.\"${'i'}\" += 3", 4);
    test("class X { var i = 1 }; def x = new X(); x.\"${'i'}\" += 3", 4);
    test("class X { var i = 1 }; new X().i++", 1);
    test("class X { var i = 1 }; ++new X().i", 2);
    test("class X { var i = 1 }; def x = new X(); x.i++; x.i", 2);
    test("class X { var i = 1 }; ++new X().\"${'i'}\"", 2);
    test("class X { var i = 1 }; X x = new X(); ++x.\"${'i'}\"", 2);
    test("class X { var i = 1 }; def x = new X(); x.\"${'i'}\"++", 1);
    test("class X { var i = 1 }; def x = new X(); ++x.\"${'i'}\"", 2);
    test("class X { var i = 1 }; new X().i = 3L", 3);
    test("class X { var i = 1 }; new X().\"${'i'}\" = 3L", 3);
    test("class X { var i = 1 }; new X().i += 3L", 4);
    test("class X { var i = 1 }; new X().\"${'i'}\" += 3L", 4);
    test("class X { var i = 1 }; new X().i = 3.0D", 3);
    test("class X { var i = 1 }; new X().\"${'i'}\" = 3.0D", 3);
    test("class X { var i = 1 }; new X().i += 3.0", 4);
    test("class X { var i = 1 }; new X().\"${'i'}\" += 3.0", 4);

    test("class X { long i = 1 }; new X().i = 3", 3L);
    test("class X { long i = 1 }; new X().\"${'i'}\" = 3", 3L);
    test("class X { long i = 1 }; X x = new X(); x.\"${'i'}\" = 3", 3L);
    test("class X { long i = 1 }; def x = new X(); x.\"${'i'}\" = 3", 3L);
    test("class X { long i = 1 }; new X().i += 3", 4L);
    test("class X { long i = 1 }; new X().\"${'i'}\" += 3", 4L);
    test("class X { long i = 1 }; X x = new X(); x.\"${'i'}\" += 3", 4L);
    test("class X { long i = 1 }; def x = new X(); x.\"${'i'}\" += 3", 4L);
    test("class X { long i = 1 }; new X().i++", 1L);
    test("class X { long i = 1 }; ++new X().i", 2L);
    test("class X { long i = 1 }; def x = new X(); x.i++; x.i", 2L);
    test("class X { long i = 1 }; ++new X().\"${'i'}\"", 2L);
    test("class X { long i = 1 }; X x = new X(); ++x.\"${'i'}\"", 2L);
    test("class X { long i = 1 }; def x = new X(); x.\"${'i'}\"++", 1L);
    test("class X { long i = 1 }; def x = new X(); ++x.\"${'i'}\"", 2L);
    test("class X { long i = 1 }; new X().i = 3D", 3L);
    test("class X { long i = 1 }; new X().\"${'i'}\" = 3D", 3L);
    test("class X { long i = 1 }; new X().i += 3D", 4L);
    test("class X { long i = 1 }; new X().\"${'i'}\" += 3D", 4L);
    test("class X { long i = 1 }; new X().i = 3.0", 3L);
    test("class X { long i = 1 }; new X().\"${'i'}\" = 3.0", 3L);
    test("class X { long i = 1 }; new X().i += 3.0", 4L);
    test("class X { long i = 1 }; new X().\"${'i'}\" += 3.0", 4L);

    test("class X { double i = 1 }; new X().i = 3", 3D);
    test("class X { double i = 1 }; new X().\"${'i'}\" = 3", 3D);
    test("class X { double i = 1 }; X x = new X(); x.\"${'i'}\" = 3", 3D);
    test("class X { double i = 1 }; def x = new X(); x.\"${'i'}\" = 3", 3D);
    test("class X { double i = 1 }; new X().i += 3", 4D);
    test("class X { double i = 1 }; new X().\"${'i'}\" += 3", 4D);
    test("class X { double i = 1 }; X x = new X(); x.\"${'i'}\" += 3", 4D);
    test("class X { double i = 1 }; def x = new X(); x.\"${'i'}\" += 3", 4D);
    test("class X { double i = 1 }; new X().i++", 1D);
    test("class X { double i = 1 }; ++new X().i", 2D);
    test("class X { double i = 1 }; def x = new X(); x.i++; x.i", 2D);
    test("class X { double i = 1 }; ++new X().\"${'i'}\"", 2D);
    test("class X { double i = 1 }; X x = new X(); ++x.\"${'i'}\"", 2D);
    test("class X { double i = 1 }; def x = new X(); x.\"${'i'}\"++", 1D);
    test("class X { double i = 1 }; def x = new X(); ++x.\"${'i'}\"", 2D);
    test("class X { double i = 1 }; new X().i = 3L", 3D);
    test("class X { double i = 1 }; new X().\"${'i'}\" = 3L", 3D);
    test("class X { double i = 1 }; new X().i += 3L", 4D);
    test("class X { double i = 1 }; new X().\"${'i'}\" += 3L", 4D);
    test("class X { double i = 1 }; new X().i = 3.0", 3D);
    test("class X { double i = 1 }; new X().\"${'i'}\" = 3.0", 3D);
    test("class X { double i = 1 }; new X().i += 3.0", 4D);
    test("class X { double i = 1 }; new X().\"${'i'}\" += 3.0", 4D);

    test("class X { Decimal i = 1 }; new X().i = 3", "#3");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" = 3", "#3");
    test("class X { Decimal i = 1 }; X x = new X(); x.\"${'i'}\" = 3", "#3");
    test("class X { Decimal i = 1 }; def x = new X(); x.\"${'i'}\" = 3", "#3");
    test("class X { Decimal i = 1 }; new X().i += 3", "#4");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" += 3", "#4");
    test("class X { Decimal i = 1 }; X x = new X(); x.\"${'i'}\" += 3", "#4");
    test("class X { Decimal i = 1 }; def x = new X(); x.\"${'i'}\" += 3", "#4");
    test("class X { Decimal i = 1 }; new X().i++", "#1");
    test("class X { Decimal i = 1 }; ++new X().i", "#2");
    test("class X { Decimal i = 1 }; def x = new X(); x.i++; x.i", "#2");
    test("class X { Decimal i = 1 }; ++new X().\"${'i'}\"", "#2");
    test("class X { Decimal i = 1 }; X x = new X(); ++x.\"${'i'}\"", "#2");
    test("class X { Decimal i = 1 }; def x = new X(); x.\"${'i'}\"++", "#1");
    test("class X { Decimal i = 1 }; def x = new X(); ++x.\"${'i'}\"", "#2");
    test("class X { Decimal i = 1 }; new X().i = 3L", "#3");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" = 3L", "#3");
    test("class X { Decimal i = 1 }; new X().i += 3L", "#4");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" += 3L", "#4");
    test("class X { Decimal i = 1 }; new X().i = 3.0D", "#3.0");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" = 3.0D", "#3.0");
    test("class X { Decimal i = 1 }; new X().i += 3.0D", "#4.0");
    test("class X { Decimal i = 1 }; new X().\"${'i'}\" += 3.0D", "#4.0");
  }

  @Test public void mutatingComplexFieldValues() {
    test("class X { List a }; new X().\"${'a'}\" = [3]", List.of(3));
    test("class X { List a = [1] }; X x = new X(); x.\"${'a'}\" = [3]", List.of(3));
    test("class X { List a }; def x = new X(); x.\"${'a'}\" = [3]", List.of(3));
    test("class X { List a = [1] }; new X().a += 3", List.of(1, 3));
    test("class X { List a }; new X().\"${'a'}\" += 3", List.of(3));
    test("class X { List a }; X x = new X(); x.\"${'a'}\" += 3", List.of(3));
    test("class X { List a = [1] }; def x = new X(); x.\"${'a'}\" += 3", List.of(1, 3));

    test("class X { Map a }; new X().\"${'a'}\" = [a:3]", Map.of("a", 3));
    test("class X { Map a = [b:1] }; X x = new X(); x.\"${'a'}\" = [a:3]", Map.of("a",3));
    test("class X { Map a }; def x = new X(); x.\"${'a'}\" = [a:3]", Map.of("a",3));
    test("class X { Map a = [a:1] }; new X().a += [b:3]", Map.of("a",1, "b",3));
    test("class X { Map a }; new X().\"${'a'}\" += [b:3]", Map.of("b",3));
    test("class X { Map a }; X x = new X(); x.\"${'a'}\" += [b:3]", Map.of("b",3));
    test("class X { Map a = [a:1] }; def x = new X(); x.\"${'a'}\" += [b:3]", Map.of("a",1, "b",3));

    test("class X { String i = '1' }; new X().\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; new X().i += '3'", "13");
    test("class X { String i = '1' }; new X().\"${'i'}\" += '3'", "13");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" += '3'", "13");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" += '3'", "13");
  }

  @Test public void newlinesInClassDecl() {
    test("class\nX\n{\nint\ni\n=\n1\ndef\nf(\n)\n{\ni\n+\ni\n}\n}\nnew\nX\n(\n3\n)\n.\ni", 3);
  }

  @Test public void asyncTests() {
    test("class X { int i = 1 }; new X().\"${sleeper(0,'i')}\"", 1);
    test("class X { int abc = 1 }; new X().\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; X x = new X(); x.\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; def x = new X(); x.\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
  }

//  @Test public void packageTests() {
//    packagName = "x.y.z";
//    test("package x.y.z; class X { static def f(){3}; }; x.y.z.X.f()", 3);
//    test("class X { static f(){3} }; X.f()", 3);
//  }

}
