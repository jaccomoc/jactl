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
    test("class X { def f = {it*it} }; new X().f(2)", 4);
    test("class X { def f = {it*it} }; new X().\"${'f'}\"(2)", 4);
    test("class X { def f = {it*it} }; X x = new X(); x.f(2)", 4);
    test("class X { def f = {it*it} }; X x = new X(); x.\"${'f'}\"(2)", 4);
    test("class X { def f = {it*it} }; def x = new X(); x.f(2)", 4);
    test("class X { def f = {it*it} }; def x = new X(); x.\"${'f'}\"(2)", 4);
    test("class X { var f = {it*it} }; new X().f(2)", 4);
    test("class X { var f = {it*it} }; new X().\"${'f'}\"(2)", 4);
    test("class X { var f = {it*it} }; X x = new X(); x.f(2)", 4);
    test("class X { var f = {it*it} }; X x = new X(); x.\"${'f'}\"(2)", 4);
    test("class X { var f = {it*it} }; def x = new X(); x.f(2)", 4);
    test("class X { var f = {it*it} }; def x = new X(); x.\"${'f'}\"(2)", 4);
//    test("class X { Y y }; class Y { X x };", null);
  }

  @Test public void fieldClashWithBuiltinMethod() {
    testError("class X { int toString = 1 }; new X().toString", "clashes with builtin method");
  }

  @Test public void simpleFields() {
    test("class X { var i = 1 }; new X().i", 1);
    testError("class X { int i }; new X().i", "missing mandatory field: i");
    test("class X { long i=0 }; new X().i", 0L);
    test("class X { double i=0 }; new X().i", 0D);
    test("class X { Decimal i=0 }; new X().i", "#0");
    test("class X { String i='' }; new X().i", "");
    test("class X { Map i=[:] }; new X().i", Map.of());
    test("class X { List i=[] }; new X().i", List.of());
    test("class X { def i=null }; new X().i", null);
    test("class X { int i = 1 }; new X().i", 1);
    test("class X { int i = 1 }; new X().\"${'i'}\"", 1);
    test("class X { int i = 1 }; X x = new X(); x.\"${'i'}\"", 1);
    test("class X { int i = 1 }; def x = new X(); x.\"${'i'}\"", 1);
    test("class X { int i = 1 }; new X().i = 3", 3);
    test("class X { int i = 1 }; new X(3).i", 3);
    test("class X { int i = 1; int j = i+1 }; new X().j", 2);
    test("class X { int i = 1; int j = i+1 }; new X(3).j", 4);
    test("class X { int i = 1; int j = i+1 }; new X(3,7).j", 7);
    testError("class X { int i; int j=2; int k }; new X(1,2).j", "missing mandatory field: k");
    testError("class X { int i; int j=2; int k }; new X(i:1,j:2).j", "missing mandatory field: k");
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).k", 3);
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).j", 2);
  }

  @Test public void fieldsWithInitialisers() {
    test("class X { int i = 1; long j = i + 2 }; new X().j", 3L);
    testError("class X { int i = j+1; long j = i + 2 }; new X().j", "forward reference");
    test("class X { int i = 1; long j = i+1; long k = j+1 }; new X().k", 3L);

    test("class X { int i = 1; long j = this.i+2 }; new X().j", 3L);
    test("class X { int i = this.j+1; long j = this.i+2 }; new X().j", 3L);

    testError("class X { int x; def z={++x + ++y}; def y=++x+2 }; new X(3).z()", "forward reference");
    test("class X { int x; def z(){++x + ++y}; def y=++x+2 }; new X(3).z()", 12);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; new X(3).z()", 12);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; new X(3,2).z()", 7);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; def x = new X(0); x.x++; x.z()", 7);
    test("class X { String x = null }; new X().x", null);
    test("class X { String x = null }; new X().x = null", null);
    test("class X { List x = null }; new X().x", null);
    test("class X { List x = null }; new X().x = null", null);
    test("class X { Map x = null }; new X().x", null);
    test("class X { Map x = null }; new X().x = null", null);
  }

  @Test public void methodArgsAsList() {
    test("def a = [1,2,3]; class X { def f(x,y=7,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { def f(x,y=7,z=8) { x + y + z }}; new X().f(a)", List.of(1,2,3,7,8));
    test("class X { def f(String x, int y) { x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { def f(String x, int y) { x + y }}; def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { def f = {String x, int y -> x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { def f = {String x, int y -> x + y } };  def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { def f(x,y,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { def f = { x,y,z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { def f(x, y) { x + y }}; new X().f([1,2])", 3);
    test("class X { def f(x, y=3) { x + y }}; new X().f([1,2])", List.of(1,2,3));
    test("class X { def f(x, y) { x + y }}; def a = [1,2]; new X().f(a)", 3);
    test("class X { def f(x, y=3) { x + y }; }; def a = [1,2]; new X().f(a)", List.of(1,2,3));
    test("class X { def f(x, y=3) { x + y }}; new X().f(1,2)", 3);
    test("class X { def f(List x, y=3) { x + y }}; new X().f([1,2])", List.of(1,2,3));
    testError("class X { def f(List x, y=4, z) { x + y + z }}; new X().f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { def f(x,y=7,z) { x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("def a = [1,2,3]; class X { def f(x,y=7,z=8) { x + y + z }}; new X().\"${'f'}\"(a)", List.of(1,2,3,7,8));
    test("class X { def f(String x, int y) { x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f(String x, int y) { x + y }}; def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f = {String x, int y -> x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f = {String x, int y -> x + y } };  def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f(x,y,z) { x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("class X { def f = { x,y,z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("class X { def f(x, y) { x + y }}; new X().\"${'f'}\"([1,2])", 3);
    test("class X { def f(x, y=3) { x + y }}; new X().\"${'f'}\"([1,2])", List.of(1,2,3));
    test("class X { def f(x, y) { x + y }}; def a = [1,2]; new X().\"${'f'}\"(a)", 3);
    test("class X { def f(x, y=3) { x + y }; }; def a = [1,2]; new X().\"${'f'}\"(a)", List.of(1,2,3));
    test("class X { def f(x, y=3) { x + y }}; new X().\"${'f'}\"(1,2)", 3);
    test("class X { def f(List x, y=3) { x + y }}; new X().\"${'f'}\"([1,2])", List.of(1,2,3));
    testError("class X { def f(List x, y=4, z) { x + y + z }}; new X().\"${'f'}\"([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; new X().f(a)", List.of(1,2,3,7,8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; new X().f([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; new X().f([1,2])", List.of(1,2,3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; new X().f(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; new X().f(a)", List.of(1,2,3));
    test("class X { static def f(x, y=3) { x + y }}; new X().f(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; new X().f([1,2])", List.of(1,2,3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; new X().f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; def x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; def x = new X(); x.f(a)", List.of(1,2,3,7,8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; def x = new X(); x.f(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; def x = new X(); x.f(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; def x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; def x = new X(); x.f([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.f([1,2])", List.of(1,2,3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; def x = new X(); x.f(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; def x = new X(); x.f(a)", List.of(1,2,3));
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.f(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; def x = new X(); x.f([1,2])", List.of(1,2,3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; def x = new X(); x.f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; def x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; def x = new X(); x.\"${'f'}\"(a)", List.of(1,2,3,7,8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; def x = new X(); x.\"${'f'}\"(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; def x = new X(); x.\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; def x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", List.of(1,2,3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; def x = new X(); x.\"${'f'}\"(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; def x = new X(); x.\"${'f'}\"(a)", List.of(1,2,3));
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", List.of(1,2,3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; def x = new X(); x.\"${'f'}\"([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; X.f(1,2,3) + X.f(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; X.f(a)", List.of(1,2,3,7,8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; X.f(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; X.f(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; X.f(1,2,3) + X.f([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; X.f([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; X.f([1,2])", List.of(1,2,3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; X.f(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; X.f(a)", List.of(1,2,3));
    test("class X { static def f(x, y=3) { x + y }}; X.f(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; X.f([1,2])", List.of(1,2,3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; X.f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; X.\"${'f'}\"(1,2,3) + X.\"${'f'}\"(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; X.\"${'f'}\"(a)", List.of(1,2,3,7,8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; X.\"${'f'}\"(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; X.\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; X.\"${'f'}\"(1,2,3) + X.\"${'f'}\"([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; X.\"${'f'}\"([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; X.\"${'f'}\"([1,2])", List.of(1,2,3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; X.\"${'f'}\"(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; X.\"${'f'}\"(a)", List.of(1,2,3));
    test("class X { static def f(x, y=3) { x + y }}; X.\"${'f'}\"(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; X.\"${'f'}\"([1,2])", List.of(1,2,3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; X.\"${'f'}\"([1, 2, 3])", "int cannot be cast to List");
  }

  @Test public void namedMethodArgs() {
    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().f(x:1,yy:2)", "no such parameter: yy");
    testError("class X { def f(int x, y=[a:3]) { x + y }}; new X().f(x:'1',y:2)", "cannot convert argument of type string to parameter type of int");
    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().f(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { def f(x, y=[a:3]) { x + y }}; new X().f(x:1,y:2)", 3);
    testError("class X { def f(x, y) { x + y }}; new X().f([x:1,y:2])", "missing mandatory argument: y");
    test("class X { def f(x, y=[a:3]) { x + y }}; new X().f([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { def f(x,y) { x*y }}; new X().f(x:2,y:3)", 6);
    test("class X { def f(int x, int y) { x*y }}; new X().f(x:2,y:3)", 6);
    test("class X { def f(int x, int y=3) { x*y }}; new X().f(x:2)", 6);
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().f(x:2)", "cannot convert argument of type int to parameter type of Map");
    test("class X { def f(def x, int y=3) { x.y = y; x }}; new X().f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(def x, int y=3) { x.y = y; x }}; new X().f(x:2)", "invalid object type");
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(def x, int y=3) { x.y = y; x }}; new X().f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(int x) { x*3 }}; new X().f(x:2)", 6);
    test("class X { def f(int x=2) { x*3 }}; new X().f(x:2)", 6);
    test("class X { def f(x,y) { x + y }}; new X().f(x:2,y:3)", 5);
    test("class X { def f(x,y=[a:1]) { x + y }}; new X().f([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { def f(x,y=[a:1]) { x + y }}; new X().f(x:2,y:3)", 5);
    testError("class X { def f(x,y,z) {x + y + z}}; new X().f(x:2,y:3)", "missing mandatory argument: z");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().f(y:3)", "missing mandatory arguments: x, z");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: a, b");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; new X().f(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; new X().f(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; new X().f(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; new X().f(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; new X().f(a)", "missing mandatory argument");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().f(a:2,b:3) { it*it }", "missing mandatory argument: c");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().f(a:2,b:3) { it*it }", "missing mandatory argument: c");

    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().\"${'f'}\"(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().\"${'f'}\"(x:1,yy:2)", "no such parameter");
    testError("class X { def f(int x, y=[a:3]) { x + y }}; new X().\"${'f'}\"(x:'abc',y:2)", "cannot be cast to int");
    testError("class X { def f(x, y=[a:3]) { x + y }}; new X().\"${'f'}\"(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { def f(x, y=[a:3]) { x + y }}; new X().\"${'f'}\"(x:1,y:2)", 3);
    testError("class X { def f(x, y) { x + y }}; new X().\"${'f'}\"([x:1,y:2])", "missing mandatory argument");
    test("class X { def f(x, y=[a:3]) { x + y }}; new X().\"${'f'}\"([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { def f(x,y) { x*y }}; new X().\"${'f'}\"(x:2,y:3)", 6);
    test("class X { def f(int x, int y) { x*y }}; new X().\"${'f'}\"(x:2,y:3)", 6);
    test("class X { def f(int x, int y=3) { x*y }}; new X().\"${'f'}\"(x:2)", 6);
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"(x:2)", "cannot be cast to Map");
    test("class X { def f(def x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(def x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"(x:2)", "invalid object type");
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(def x, int y=3) { x.y = y; x }}; new X().\"${'f'}\"([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(int x) { x*3 }}; new X().\"${'f'}\"(x:2)", 6);
    test("class X { def f(int x=2) { x*3 }}; new X().\"${'f'}\"(x:2)", 6);
    test("class X { def f(x,y) { x + y }}; new X().\"${'f'}\"(x:2,y:3)", 5);
    test("class X { def f(x,y=[a:1]) { x + y }}; new X().\"${'f'}\"([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { def f(x,y=[a:1]) { x + y }}; new X().\"${'f'}\"(x:2,y:3)", 5);
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:2,y:3)", "missing value for mandatory parameter/field 'z'");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(y:3)", "missing value for mandatory parameter/field 'x'");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:[1],y:3,z:4,a:1)", "no such parameter");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:[1],b:2,y:3,z:4,a:1)", "no such parameter");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; new X().\"${'f'}\"(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");

    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.f(x:1,yy:2)", "no such parameter: yy");
    testError("class X { static def f(int x, y=[a:3]) { x + y }}; X.f(x:'abc',y:2)", "cannot convert argument of type string to parameter type of int");
    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.f(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { static def f(x, y=[a:3]) { x + y }}; X.f(x:1,y:2)", 3);
    testError("class X { static def f(x, y) { x + y }}; X.f([x:1,y:2])", "missing mandatory argument");
    test("class X { static def f(x, y=[a:3]) { x + y }}; X.f([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { static def f(x,y) { x*y }}; X.f(x:2,y:3)", 6);
    test("class X { static def f(int x, int y) { x*y }}; X.f(x:2,y:3)", 6);
    test("class X { static def f(int x, int y=3) { x*y }}; X.f(x:2)", 6);
    test("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.f(x:2)", "cannot convert argument of type int to parameter type of Map");
    test("class X { static def f(def x, int y=3) { x.y = y; x }}; X.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { static def f(def x, int y=3) { x.y = y; x }}; X.f(x:2)", "invalid object type");
    test("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { static def f(def x, int y=3) { x.y = y; x }}; X.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { static def f(int x) { x*3 }}; X.f(x:2)", 6);
    test("class X { static def f(int x=2) { x*3 }}; X.f(x:2)", 6);
    test("class X { static def f(x,y) { x + y }}; X.f(x:2,y:3)", 5);
    test("class X { static def f(x,y=[a:1]) { x + y }}; X.f([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { static def f(x,y=[a:1]) { x + y }}; X.f(x:2,y:3)", 5);
    testError("class X { static def f(x,y,z) {x + y + z}}; X.f(x:2,y:3)", "missing mandatory argument: z");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.f(y:3)", "missing mandatory arguments: x, z");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: a, b");
    test("class X { static def f(a, b, c) { c(a+b) }}; X.f(a:2,b:3,c:{ it*it })", 25);
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.f(a:2,b:3) { it*it }", "missing mandatory argument: c");
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.f(a:2,b:3) { it*it }", "missing mandatory argument: c");

    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.\"${'f'}\"(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.\"${'f'}\"(x:1,yy:2)", "no such parameter");
    testError("class X { static def f(int x, y=[a:3]) { x + y }}; X.\"${'f'}\"(x:'abc',y:2)", "cannot be cast to int");
    testError("class X { static def f(x, y=[a:3]) { x + y }}; X.\"${'f'}\"(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { static def f(x, y=[a:3]) { x + y }}; X.\"${'f'}\"(x:1,y:2)", 3);
    testError("class X { static def f(x, y) { x + y }}; X.\"${'f'}\"([x:1,y:2])", "missing mandatory argument");
    test("class X { static def f(x, y=[a:3]) { x + y }}; X.\"${'f'}\"([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { static def f(x,y) { x*y }}; X.\"${'f'}\"(x:2,y:3)", 6);
    test("class X { static def f(int x, int y) { x*y }}; X.\"${'f'}\"(x:2,y:3)", 6);
    test("class X { static def f(int x, int y=3) { x*y }}; X.\"${'f'}\"(x:2)", 6);
    test("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.\"${'f'}\"([x:2])", Map.of("x",2,"y",3));
    testError("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.\"${'f'}\"(x:2)", "cannot be cast to Map");
    test("class X { static def f(def x, int y=3) { x.y = y; x }}; X.\"${'f'}\"([x:2])", Map.of("x",2,"y",3));
    testError("class X { static def f(def x, int y=3) { x.y = y; x }}; X.\"${'f'}\"(x:2)", "invalid object type");
    test("class X { static def f(Map x, int y=3) { x.y = y; x }}; X.\"${'f'}\"([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { static def f(def x, int y=3) { x.y = y; x }}; X.\"${'f'}\"([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { static def f(int x) { x*3 }}; X.\"${'f'}\"(x:2)", 6);
    test("class X { static def f(int x=2) { x*3 }}; X.\"${'f'}\"(x:2)", 6);
    test("class X { static def f(x,y) { x + y }}; X.\"${'f'}\"(x:2,y:3)", 5);
    test("class X { static def f(x,y=[a:1]) { x + y }}; X.\"${'f'}\"([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { static def f(x,y=[a:1]) { x + y }}; X.\"${'f'}\"(x:2,y:3)", 5);
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:2,y:3)", "missing value for mandatory parameter/field 'z'");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(y:3)", "missing value for mandatory parameter/field 'x'");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:[1],y:3,z:4,a:1)", "no such parameter");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:[1],b:2,y:3,z:4,a:1)", "no such parameter");
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");

    testError("class X { def f(x, y=[a:3]) { x + y }}; X x = new X(); x.f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { def f(x, y=[a:3]) { x + y }}; X x = new X(); x.f(x:1,yy:2)", "no such parameter: yy");
    testError("class X { def f(int x, y=[a:3]) { x + y }}; X x = new X(); x.f(x:'abc',y:2)", "cannot convert argument of type string to parameter type of int");
    testError("class X { def f(x, y=[a:3]) { x + y }}; X x = new X(); x.f(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { def f(x, y=[a:3]) { x + y }}; X x = new X(); x.f(x:1,y:2)", 3);
    testError("class X { def f(x, y) { x + y }}; X x = new X(); x.f([x:1,y:2])", "missing mandatory argument");
    test("class X { def f(x, y=[a:3]) { x + y }}; X x = new X(); x.f([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { def f(x,y) { x*y }}; X x = new X(); x.f(x:2,y:3)", 6);
    test("class X { def f(int x, int y) { x*y }}; X x = new X(); x.f(x:2,y:3)", 6);
    test("class X { def f(int x, int y=3) { x*y }}; X x = new X(); x.f(x:2)", 6);
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; X x = new X(); x.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(Map x, int y=3) { x.y = y; x }}; X x = new X(); x.f(x:2)", "cannot convert argument of type int to parameter type of Map");
    test("class X { def f(def x, int y=3) { x.y = y; x }}; X x = new X(); x.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(def x, int y=3) { x.y = y; x }}; X x = new X(); x.f(x:2)", "invalid object type");
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; X x = new X(); x.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(def x, int y=3) { x.y = y; x }}; X x = new X(); x.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(int x) { x*3 }}; X x = new X(); x.f(x:2)", 6);
    test("class X { def f(int x=2) { x*3 }}; X x = new X(); x.f(x:2)", 6);
    test("class X { def f(x,y) { x + y }}; X x = new X(); x.f(x:2,y:3)", 5);
    test("class X { def f(x,y=[a:1]) { x + y }}; X x = new X(); x.f([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { def f(x,y=[a:1]) { x + y }}; X x = new X(); x.f(x:2,y:3)", 5);
    testError("class X { def f(x,y,z) {x + y + z}}; X x = new X(); x.f(x:2,y:3)", "missing mandatory argument: z");
    testError("class X { def f(x,y,z) {x + y + z}}; X x = new X(); x.f(y:3)", "missing mandatory arguments: x, z");
    testError("class X { def f(x,y,z) {x + y + z}}; X x = new X(); x.f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("class X { def f(x,y,z) {x + y + z}}; X x = new X(); x.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: a, b");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; X x = new X(); x.f(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; X x = new X(); x.f(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; X x = new X(); x.f(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; X x = new X(); x.f(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; X x = new X(); x.f(a)", "missing mandatory argument");
    testError("class X { def f(a, b, c) { c(a+b) }}; X x = new X(); x.f(a:2,b:3) { it*it }", "missing mandatory argument: c");
    testError("class X { def f(a, b, c) { c(a+b) }}; X x = new X(); x.f(a:2,b:3) { it*it }", "missing mandatory argument: c");

    testError("class X { def f(x, y=[a:3]) { x + y }}; def x = new X(); x.f(x:1,x:2,y:2)", "parameter 'x' occurs multiple times");
    testError("class X { def f(x, y=[a:3]) { x + y }}; def x = new X(); x.f(x:1,yy:2)", "no such parameter");
    testError("class X { def f(int x, y=[a:3]) { x + y }}; def x = new X(); x.f(x:'abc',y:2)", "cannot be cast to int");
    testError("class X { def f(x, y=[a:3]) { x + y }}; def x = new X(); x.f(x:1,(''+'y'):2)", "invalid parameter name");
    test("class X { def f(x, y=[a:3]) { x + y }}; def x = new X(); x.f(x:1,y:2)", 3);
    testError("class X { def f(x, y) { x + y }}; def x = new X(); x.f([x:1,y:2])", "missing mandatory argument");
    test("class X { def f(x, y=[a:3]) { x + y }}; def x = new X(); x.f([x:1,y:2])", Map.of("x",1,"y",2,"a",3));
    test("class X { def f(x,y) { x*y }}; def x = new X(); x.f(x:2,y:3)", 6);
    test("class X { def f(int x, int y) { x*y }}; def x = new X(); x.f(x:2,y:3)", 6);
    test("class X { def f(int x, int y=3) { x*y }}; def x = new X(); x.f(x:2)", 6);
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; def x = new X(); x.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(Map x, int y=3) { x.y = y; x }}; def x = new X(); x.f(x:2)", "cannot be cast to Map");
    test("class X { def f(def x, int y=3) { x.y = y; x }}; def x = new X(); x.f([x:2])", Map.of("x",2,"y",3));
    testError("class X { def f(def x, int y=3) { x.y = y; x }}; def x = new X(); x.f(x:2)", "invalid object type");
    test("class X { def f(Map x, int y=3) { x.y = y; x }}; def x = new X(); x.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(def x, int y=3) { x.y = y; x }}; def x = new X(); x.f([x:2,y:4])", Map.of("x",2,"y",3));
    test("class X { def f(int x) { x*3 }}; def x = new X(); x.f(x:2)", 6);
    test("class X { def f(int x=2) { x*3 }}; def x = new X(); x.f(x:2)", 6);
    test("class X { def f(x,y) { x + y }}; def x = new X(); x.f(x:2,y:3)", 5);
    test("class X { def f(x,y=[a:1]) { x + y }}; def x = new X(); x.f([x:2,y:3])", Map.of("x",2,"y",3,"a",1));
    test("class X { def f(x,y=[a:1]) { x + y }}; def x = new X(); x.f(x:2,y:3)", 5);
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:2,y:3)", "missing value for mandatory parameter/field 'z'");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(y:3)", "missing value for mandatory parameter/field 'x'");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: a, b");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; def x = new X(); x.f(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; def x = new X(); x.f(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; def x = new X(); x.f(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; def x = new X(); x.f(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; def x = new X(); x.f(a)", "missing mandatory arguments");
    testError("class X { def f(a, b, c) { c(a+b) }}; def x = new X(); x.f(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");
    testError("class X { def f(a, b, c) { c(a+b) }}; def x = new X(); x.f(a:2,b:3) { it*it }", "missing value for mandatory parameter/field 'c'");
  }

  @Test public void constructorArgsAsList() {
    test("class X { String a; int b; List c; Map d }; new X('abc',1,[1,2,3],[a:5]).d.a", 5);
    test("class X { String a; int b; List c = []; Map d = [:] }; new X('abc',1).b", 1);
    testError("class X { String a; int b; List c = []; Map d = [:] }; new X('abc').b", "missing mandatory field");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc').b", "missing mandatory field");
    test("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3]).c[2]", 3);
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3], 5, 6).c[2]", "too many arguments");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3], 5).c[2]", "cannot convert 4th argument");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X(123, 1, [1,2,3], 5).c[2]", "cannot convert 1st argument");
  }

  @Test public void namedConstructorArgs() {
    test("class X { int i }; new X(2).i", 2);
    test("class X { int i }; new X(i:2).i", 2);
    test("class X { int i = 0 }; new X(2).i", 2);
    test("class X { int i = 0 }; new X(i:2).i", 2);
    test("class X { String a; int b; List c; Map d }; new X(a:'abc',b:1,c:[1,2,3],d:[a:5]).d.a", 5);
    test("class X { String a; int b=4; List c; Map d }; def x = new X(a:'abc',c:[1,2,3],d:[a:5]); x.d.a + x.b", 9);
    test("class X { String a; int b=4; List c; Map d }; X x = new X(a:'abc',c:[1,2,3],d:[a:5]); x.d.a + x.b", 9);
    test("class X { String a; int b=4; List c; Map d }; X x = new X([a:'abc',c:[1,2,3],d:[a:5]]); x.d.a + x.b", 9);
    test("class X { String a; int b=4; List c; Map d }; def args = [a:'abc',c:[1,2,3],d:[a:5]]; X x = new X(args); x.d.a + x.b", 9);
    test("class X { Map a; int b=4; List c=[]; Map d=[:] }; def args = [a:'abc',c:[1,2,3],d:[a:5]]; X x = new X(args, 3); x.a.d.a + x.b", 8);
    testError("class X { String a; int b; List c; Map d }; new X(a:'abc',c:[1,2,3],d:[a:5]).d.a", "missing mandatory field");
    testError("class X { String a; int b; List c; Map d }; new X([a:'abc',c:[1,2,3],d:[a:5]]).d.a", "missing value for mandatory parameter/field");
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
    testError("class X { List a }; new X().\"${'a'}\" = [3]", "missing mandatory field: a");
    test("class X { List a = []}; new X().a = [3]", List.of(3));
    test("class X { List a = []}; new X().\"${'a'}\" = [3]", List.of(3));
    test("class X { List a = [1] }; X x = new X(); x.a = [3]", List.of(3));
    test("class X { List a = [1] }; X x = new X(); x.\"${'a'}\" = [3]", List.of(3));
    test("class X { List a = [1] }; X x = new X(); x.\"${'a'}\" = [3]; x.a", List.of(3));
    test("class X { List a = [] }; def x = new X(); x.\"${'a'}\" = [3]", List.of(3));
    test("class X { List a = [] }; def x = new X(); x.\"${'a'}\" = [3]; x.a", List.of(3));
    test("class X { List a = [1] }; new X().a += 3", List.of(1, 3));
    test("class X { List a = [] }; new X().\"${'a'}\" += 3", List.of(3));
    test("class X { List a = [] }; X x = new X(); x.\"${'a'}\" += 3", List.of(3));
    test("class X { List a = [] }; X x = new X(); x.\"${'a'}\" += 3; x.a", List.of(3));
    test("class X { List a = [1] }; def x = new X(); x.\"${'a'}\" += 3", List.of(1, 3));
    test("class X { List a = [1] }; def x = new X(); x.\"${'a'}\" += 3; x.a", List.of(1, 3));

    testError("class X { Map a }; new X().a = [a:3]", "missing mandatory field");
    testError("class X { Map a }; new X().\"${'a'}\" = [a:3]", "missing mandatory field");
    test("class X { Map a = [:] }; new X().a = [a:3]", Map.of("a", 3));
    test("class X { Map a = [:] }; new X().\"${'a'}\" = [a:3]", Map.of("a", 3));
    test("class X { Map a = [b:1] }; X x = new X(); x.\"${'a'}\" = [a:3]", Map.of("a",3));
    test("class X { Map a = [b:1] }; X x = new X(); x.a = [a:3]", Map.of("a",3));
    test("class X { Map a = [b:1] }; X x = new X(); x.\"${'a'}\" = [a:3]; x.a", Map.of("a",3));
    test("class X { Map a = [:] }; def x = new X(); x.\"${'a'}\" = [a:3]", Map.of("a",3));
    test("class X { Map a = [:] }; def x = new X(); x.\"${'a'}\" = [a:3]; x.a", Map.of("a",3));
    test("class X { Map a = [a:1] }; new X().a += [b:3]", Map.of("a",1, "b",3));
    test("class X { Map a = [:] }; new X().\"${'a'}\" += [b:3]", Map.of("b",3));
    test("class X { Map a = [:] }; X x = new X(); x.\"${'a'}\" += [b:3]", Map.of("b",3));
    test("class X { Map a = [:] }; X x = new X(); x.a += [b:3]", Map.of("b",3));
    test("class X { Map a = [:] }; X x = new X(); x.a += [b:3]; x.a", Map.of("b",3));
    test("class X { Map a = [:] }; X x = new X(); x.\"${'a'}\" += [b:3]; x.a", Map.of("b",3));
    test("class X { Map a = [a:1] }; def x = new X(); x.\"${'a'}\" += [b:3]", Map.of("a",1, "b",3));
    test("class X { Map a = [a:1] }; def x = new X(); x.\"${'a'}\" += [b:3]; x.a", Map.of("a",1, "b",3));

    test("class X { String i = '1' }; new X().i = '3'", "3");
    test("class X { String i = '1' }; new X().\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; X x = new X(); x.i = '3'", "3");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" = '3'; x.i", "3");
    test("class X { String i = '1' }; def x = new X(); x.i = '3'", "3");
    test("class X { String i = '1' }; def x = new X(); x.i = '3'; x.i", "3");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" = '3'", "3");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" = '3'; x.i", "3");
    test("class X { String i = '1' }; new X().i += '3'", "13");
    test("class X { String i = '1' }; new X().\"${'i'}\" += '3'", "13");
    test("class X { String i = '1' }; X x = new X(); x.i += '3'", "13");
    test("class X { String i = '1' }; X x = new X(); x.i += '3'; x.i", "13");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" += '3'", "13");
    test("class X { String i = '1' }; X x = new X(); x.\"${'i'}\" += '3'; x.i", "13");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" += '3'", "13");
    test("class X { String i = '1' }; def x = new X(); x.\"${'i'}\" += '3'; x.i", "13");
  }

  @Test public void fieldsOfOtherClasses() {
    test("class Y { var d=2.0 }; class X { Y y = new Y() }; new X().y.d", "#2.0");
    test("class Y { var d=2.0 }; class X { Y y = null }; def x = new X(); x.y = new Y(); x.y.d", "#2.0");
    test("class Y { var d=2.0 }; class X { Y y }; def x = new X(null); x.y = new Y(); x.y.d", "#2.0");
    test("class Y { var d=2.0 }; class X { Y y }; def x = new X(y:new Y(d:1)); x.y.d", "#1");
    testError("class Y { var d=2.0 }; class X { Y y }; def x = new X(); x.y = new Y(); x.y.d", "missing mandatory field: y");
    test("class Y { var d=2.0 }; class X { Y y }; def x = new X(y:null); x.y = new Y(); x.y.d", "#2.0");
    //test("class Y { var d=2.0 }; class X { Y y = new Y(); Z z = null }; class Z { Y z }; new X().y.d", "#2.0");
  }

  @Test public void recursiveClass() {
    test("class X { X x = null; def y = null }; X x = new X(x:null,y:'xyz'); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; new X(x:new X(y:'xyz'),y:'abc').x.y", "xyz");
  }

  @Test public void closedOverThis() {
    test("class X { int i = 1; def f(){ return { ++i } } }; def x = new X(); def g = x.f(); g() + g() + x.i", 8);
  }

  @Test public void autoCreateFieldsDuringAssign() {
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
