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

  @Test public void nameScoping() {
    test("class X{}; int X = 1; X", 1);
    test("class X{static def f(){2}}; Map X = [f:{3}]; X.f()", 3);
    test("class X{static def f(){2}}; def X = [f:{3}]; X.f()", 3);
    test("class X{int i=2}; Map X = [i:1]; X x = new X(); X.i", 1);
    test("class X{int i=2}; Map X = [i:1]; def x = new X(); X.i", 1);
    test("class X{int i=2}; X X = new X(); X.i", 2);
    test("class X{int i=2}; def X = new X(); X.i", 2);
    test("class X{int i=2}; { X X = new X(); return X.i}", 2);
    test("class X{int i=2}; {def X = new X(); return X.i}", 2);
  }

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
    testError("class X { int i = 0; static def f() { this } }; X.f()", "reference to 'this' in static function");
    testError("class X { int i = 0; static def f() { i } }; X.f()", "reference to field in static function");
    testError("class X { int i = sleeper(0,-1)+sleeper(0,2); static def f(){ return sleeper(0,{ sleeper(0,++i - 1)+sleeper(0,1) }) } }; def x = new X(); def g = x.f(); g() + g() + x.i", "field in static function");
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
    test("class X { int i = 1 }; new X(3)['i']", 3);
    test("class X { int i = 1 }; X x = new X(3); x['i']", 3);
    test("class X { int i = 1 }; def x = new X(3); x['i']", 3);
    test("class X { int i = 1 }; def a = 'i'; new X(3)[a]", 3);
    test("class X { int i = 1 }; def a = 'i'; X x = new X(3); x[a]", 3);
    test("class X { int i = 1 }; def a = 'i'; def x = new X(3); x[a]", 3);
    test("class X { int i = 1; int j = i+1 }; new X().j", 2);
    test("class X { int i = 1; int j = i+1 }; new X(3).j", 4);
    test("class X { int i = 1; int j = i+1 }; new X(3,7).j", 7);
    testError("class X { int i; int j=2; int k }; new X(1,2).j", "missing mandatory field: k");
    testError("class X { int i; int j=2; int k }; new X(i:1,j:2).j", "missing mandatory field: k");
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).k", 3);
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).j", 2);
    test("class X { int i, j=2, k }; new X(i:1,k:3).j", 2);
    testError("int sum = 0; for(int i = 0; i < 10; i++) { class X { int x=i }; sum += new X().x }; sum", "class declaration not allowed here");
    testError("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x.y.z", "no such field 'z'");
    test("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x.y.j", 4);
    test("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x['y'].j", 4);
    testError("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x.['y'].j", "invalid token");
    test("class X { Y y }; class Y { int j = 3 }; X x = new X(new Y(j:4)); x.y.j", 4);
    testError("class X { Y y }; class Y { int j = 3 }; X x = new X(new Y(j:4)); x['xx'].j", "no such field or method");
    test("class X { Y y }; class Y { int j = 3 }; X x = new X(new Y(j:4)); x['y'].j", 4);
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
    test("def a = [1,2,3]; class X { def f(int x, int y=7, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
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

    test("def a = [1,2,3]; class X { int f(int x, int y=7, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { int f(int x, int y, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { def f = { int x, int y, int z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { int f(x, y) { x + y }}; new X().f([1,2])", 3);
    test("class X { int f(x, y) { x + y }}; def a = [1,2]; new X().f(a)", 3);
    test("class X { int f(x, y=3) { x + y }}; new X().f(1,2)", 3);

    test("def a = [1,2,3]; class X { def f(x,y=7,z) { x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("def a = [1,2,3]; class X { def f(x,y=7,z=8) { x + y + z }}; new X().\"${'f'}\"(a)", List.of(1,2,3,7,8));
    test("class X { def f(String x, int y) { x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f(String x, int y) { x + y }}; def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f = {String x, int y -> x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f = {String x, int y -> x + y } };  def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f(int x, int y, int z) { x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("class X { def f = { int x, int y, int z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
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
    test("class X { static int f(int x,int y, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
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
    testError("class X { def f(x,y,z) {x + y + z}}; new X().f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
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
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:2,y:3)", "missing value for mandatory parameter 'z'");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(y:3)", "missing value for mandatory parameter 'x'");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:[1],y:3,z:4,a:1)", "no such parameter");
    testError("class X { def f(x,y,z) {x + y + z}}; new X().\"${'f'}\"(x:[1],b:2,y:3,z:4,a:1)", "no such parameter");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; new X().\"${'f'}\"(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; new X().\"${'f'}\"(a)", "missing mandatory argument");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");
    testError("class X { def f(a, b, c) { c(a+b) }}; new X().\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");

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
    testError("class X { static def f(x,y,z) {x + y + z}}; X.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
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
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:2,y:3)", "missing value for mandatory parameter 'z'");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(y:3)", "missing value for mandatory parameter 'x'");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:[1],y:3,z:4,a:1)", "no such parameter");
    testError("class X { static def f(x,y,z) {x + y + z}}; X.\"${'f'}\"(x:[1],b:2,y:3,z:4,a:1)", "no such parameter");
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");
    testError("class X { static def f(a, b, c) { c(a+b) }}; X.\"${'f'}\"(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");

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
    testError("class X { def f(x,y,z) {x + y + z}}; X x = new X(); x.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
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
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:2,y:3)", "missing value for mandatory parameter 'z'");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(y:3)", "missing value for mandatory parameter 'x'");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:[1],y:3,z:4,a:1)", "no such parameter: a");
    testError("class X { def f(x,y,z) {x + y + z}}; def x = new X(); x.f(x:[1],b:2,y:3,z:4,a:1)", "no such parameters: b, a");
    testError("class X { def f = { x,y -> x*y }}; def a = [x:2,y:3]; def x = new X(); x.f(a)", "missing mandatory argument");
    test("class X { def f = { x,y=[a:1] -> x + y }}; def a = [x:2,y:3]; def x = new X(); x.f(a)", Map.of("x",2,"y",3,"a",1));
    test("class X { def f = { x,y=[a:1] -> x + y }}; def x = new X(); x.f(x:2,y:3)", 5);
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [x:2,y:3]; def x = new X(); x.f(a)", "missing mandatory argument");
    testError("class X { def f = { x,y,z -> x + y + z}}; def a = [y:3]; def x = new X(); x.f(a)", "missing mandatory arguments");
    testError("class X { def f(a, b, c) { c(a+b) }}; def x = new X(); x.f(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");
    testError("class X { def f(a, b, c) { c(a+b) }}; def x = new X(); x.f(a:2,b:3) { it*it }", "missing value for mandatory parameter 'c'");
  }

  @Test public void constructorArgsAsList() {
    test("class X { String a; int b; List c; Map d }; new X('abc',1,[1,2,3],[a:5]).d.a", 5);
    test("class X { String a; int b; List c = []; Map d = [:] }; new X('abc',1).b", 1);
    testError("class X { String a; int b; List c = []; Map d = [:] }; new X('abc').b", "missing mandatory field");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc').b", "missing mandatory field");
    test("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3]).c[2]", 3);
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3], 5, 6).c[2]", "too many arguments");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3], 5).c[2]", "cannot convert argument of type int");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X(123, 1, [1,2,3], 5).c[2]", "cannot convert argument of type int");
    test("class X { X x; int i}; new X(new X(null,2),1).x.i", 2);
    test("class X { X x; int i}; new X([x:null,i:2],1).x.i", 2);
    test("class X { X x; int i}; new X([\"${'x'}\":null,i:2],1).x.i", 2);
    test("class X { X x; int i}; Map a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i, j=3 }; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i; int j=3 }; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i}; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i}; new X([null,2],1).x.i", 2);
    test("class X { X x; int i}; new X([x:[null,3],i:2],1).x.x.i", 3);
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
    testError("class X { String a; int b; List c; Map d }; new X([a:'abc',c:[1,2,3],d:[a:5]]).d.a", "missing mandatory field: b");
  }

  @Test public void complexTypesAsFunctionArgs() {
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; f(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def g = f; g([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; def g = f; g(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([1,2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def g = f; g([1,2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [1,2]; f(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [1,2]; def g = f; g(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([1,2])", 3);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; new X(3,4).f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def g = new X(3,4).f; g([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [i:1,j:2]; new X(3,4).f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [i:1,j:2]; def g = new X(3,4).f; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; new X(3,4).f([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [1,2]; new X(3,4).f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [1,2]; def g = new X(3,4).f; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.\"${'f'}\"([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def g = x.\"${'f'}\"; g([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.f([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.\"${'f'}\"([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def g = x.\"${'f'}\"; g([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def g = x.f; g([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [1,2]; x.f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [1,2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [1,2]; def g = x.f; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.\"${'f'}\"([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.f([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [1,2]; x.f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.\"${'f'}\"([1,2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [1,2]; x.\"${'f'}\"(a)", 10);
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
    test("class Y { var d=2.0 }; class X { Y y }; def x = new X([y:new Y([d:3])]); x.y.d", "#3");
    test("class Y { var d=2.0 }; class X { Y y }; def a = [y:new Y([d:3])]; def x = new X(a); x.y.d", "#3");

    test("class Y { var d=2.0 }; class X { Y y = new Y(); Z z = null }; class Z { Y z }; new X().y.d", "#2.0");
    testError("class Y { var d=2.0 }; class X { Y y = new Y(); X z = null }; class X { Y z }; new X().y.d", "already exists");
  }

  @Test public void recursiveClasses() {
    test("class X { X x }; new X(x:null).x", null);
    test("class X { X x }; new X(x:[x:null]).x.x", null);
    test("class X { X x }; def a = [x:[x:null]]; new X(a).x.x", null);
    test("class X { X x = null; def y = null }; X x = new X(x:null,y:'xyz'); x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = new X([x:null,y:'xyz']); x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = new X([\"${'x'}\":null,y:'xyz']); x.y", "xyz");
    test("class X { X x = null; def y = null }; def a = [\"${'x'}\":null,y:'xyz']; X x = new X(a); x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = new X(x:null,y:'xyz'); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = null; x = new X(x:null,y:'xyz'); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; def a = [x:null,y:'xyz']; X x = new X(a); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; new X(x:new X(y:'xyz'),y:'abc').x.y", "xyz");
    testError("class X { X x; def y = null }; new X(x:[x:null,y:'xyz',z:'abc',yy:3],y:'abc').x.y", "no such fields: z, yy");
    testError("class X { X x; def y = null }; def a = [x:null,y:'xyz',z:'abc',yy:3]; new X(a).x.y", "no such fields: z, yy");
    testError("class X { X x; def y = null }; def a = [x:[x:null,y:'xyz',z:'abc',yy:3],y:'abc']; new X(a).x.y", "no such fields: z, yy");
    test("class X { X x; def y = null }; new X(x:[x:null,y:'xyz'],y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; new X(x:[y:'xyz'],y:'abc').x.y", "xyz");

    test("class X { Y y }; class Y { X x }; X x = new X(new Y(new X(null))); Y y = new Y(x); y.x == x && x.y.x.y == null", true);
    test("class X { Y y }; class Y { X x }; X x = new X([y:[x:[y:null]]]); Y y = new Y(x); y.x == x && x.y.x.y == null", true);
    test("class X { Y y }; class Y { X x }; def a = [y:[x:[y:null]]]; def x = new X(a); Y y = new Y(x); y.x == x && x.y.x.y == null", true);
  }

  @Test public void assignmentsAndEquality() {
    test("class X { int i }; X x = [i:2]; x.i", 2);
    test("class X { int i }; X x = [i:2]; x.i = 3", 3);
    test("class X { int i }; X x = [i:2]; x.i = 3; x.i", 3);
    test("class X { int i }; X x = [i:2]; x['i'] = 3", 3);
    test("class X { int i }; X x = [i:2]; x['i'] = 3; x['i']", 3);
    test("class X { int i }; X x = [2]; x.i", 2);
    test("class X { int i }; X x = new X(2); X y = new X(2); x == y", true);
    test("class X { int i }; X x = new X(2); X y = new X(2); x != y", false);
    test("class X { int i }; X x = new X(2); X y = new X(3); x = y; x.i", 3);
    test("class X { int i }; X x = new X(2); X y = new X(3); x = y; y.i", 3);
    test("class X { int i }; X x = new X(2); def y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); def y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); X y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); x == [i:2] as X", true);
    test("class X { int i }; def x = new X(2); x == [2] as X", true);
    test("class X { int i }; X x = new X(2); x == [i:2] as X", true);
    test("class X { int i }; X x = new X(2); x == [2] as X", true);
    test("class X { X x }; X x = new X(null); x.x = x; Map m = x as Map; m.x == m", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,3); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,3); x1 != x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,2); x1 != x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,2); x1 == x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(3,2); X x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(3,2); X x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[3,4]); x1 == x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[3,4]); x1 != x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[4,4]); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[4,4]); x1 != x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[3,5]); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2,[3,4]); X x2 = new X(1,2,[3,5]); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,3); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,3); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,2); x1 != x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,2); x1 == x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(3,2); def x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(3,2); def x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[3,4]); x1 == x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[3,4]); x1 != x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[4,4]); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[4,4]); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[3,5]); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2,[3,4]); def x2 = new X(1,2,[3,5]); x1 != x2", true);
    test("Z f() { return new Z(3) }; class Z { int i }; Z z = f(); z instanceof Z", true);
    test("def f() { return new Z(3) }; class Z { int i }; Z z = f(); z instanceof Z", true);
  }

  @Test public void mapConversions() {
    test("class X { int i = 0 }; X x = [i:2]; x.i", 2);
    test("class X { int i = 0 }; X x = [i:2]; (x as Map).i", 2);
    test("class X { int i = 0 }; def x = [i:2]; (x as Map).i", 2);
    test("class X { int i = 0 }; def a = [i:2]; X x = a; x.i", 2);
    test("class X { int i = 0 }; def a = [i:2]; (a as X).i", 2);
    test("class X { int i = 0 }; def a = [i:2]; def b = (a as X); b.i", 2);
    test("class X { int i = 0; long j = 2 }; new X() as Map", Map.of("i",0,"j",2L));
    test("class X { int i = 0; long j = 2 }; X x = new X(); x as Map", Map.of("i",0,"j",2L));
    test("class X { int i = 0; long j = 2 }; def x = new X(); x as Map", Map.of("i",0,"j",2L));
    test("class X { int i = 0; long j = 2; X x = null }; def x = new X(); (x as Map).toString()", "[i:0, j:2, x:null]");
    test("class X { int i = 0; long j = 2; X x = null }; def x = new X(); x.x = x; x.toString()", "[i:0, j:2, x:<CIRCULAR_REF>]");
    test("class X { int i = 0; long j = 2; X x = null }; def x = new X(); x.x = x; (x as Map).toString()", "[i:0, j:2, x:<CIRCULAR_REF>]");
    testError("class X { int i = 0; long j = 2 }; new X() as List", "cannot coerce");
    testError("class X { int i = 0; long j = 2 }; def x = new X(); x as List", "cannot coerce");
  }

  @Test public void instanceOf() {
    test("class X { int i=0 }; new X() instanceof X", true);
    test("class X { int i=0 }; X x = new X(); x instanceof X", true);
    test("class X { int i=0 }; def x = new X(); x instanceof X", true);
    test("class X { int i=0 }; new X() !instanceof X", false);
    test("class X { int i=0 }; X x = new X(); x !instanceof X", false);
    test("class X { int i=0 }; def x = new X(); x !instanceof X", false);
    test("class X { Y y }; class Y { X x }; def a = [y:[x:[y:null]]]; def x = new X(a); Y y = new Y(x); x instanceof X && y instanceof Y && x !instanceof Y && y !instanceof X && x.y instanceof Y && y.x instanceof X", true);
  }

  @Test public void toStringTest() {
    test("class X { X x; int i; String s }; new X(x:[x:null,i:2,s:'abc'],i:3,s:'xyz').toString()", "[x:[x:null, i:2, s:'abc'], i:3, s:'xyz']");
    test("class X { X x; int i; String s }; X x = new X(x:[x:null,i:2,s:'abc'],i:3,s:'xyz'); x.toString()", "[x:[x:null, i:2, s:'abc'], i:3, s:'xyz']");
    test("class X { X x; int i; String s }; def x = new X(x:[x:null,i:2,s:'abc'],i:3,s:'xyz'); x.toString()", "[x:[x:null, i:2, s:'abc'], i:3, s:'xyz']");
    test("class X {}; new X().toString()", "[:]");
    test("class X {}; X x = new X(); x.toString()", "[:]");
    test("class X {}; def x = new X(); x.toString()", "[:]");
    test("class X {X x; long i}; new X(null,3).toString()", "[x:null, i:3]");
    test("class X {X x; int i}; def x = new X(null,3); x.x = x; x.toString()", "[x:<CIRCULAR_REF>, i:3]");
    test("class X {X x; int i}; def x = new X(null,3); x.x = x; (x as Map).toString()", "[x:<CIRCULAR_REF>, i:3]");
    test("class X { Y y }; class Y { X x }; def a = [y:[x:[y:null]]]; def x = new X(a); x.toString()", "[y:[x:[y:null]]]");
  }

  @Test public void innerClasses() {
    test("class X { int i; class Y { int i }}; X x = new X(1); X.Y y = new X.Y(2); x.i + y.i", 3);
    test("class X { int i; class Y { int i; Z.ZZ f() { return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; new X.Y(3).f() instanceof Z.ZZ", true);
    test("class X { int i; class Y { int i; def f() { return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; new X.Y(3).f() instanceof Z.ZZ", true);
    test("class Y { int i; Z f() { return new Z(i) } }; class Z { int i }; Z z = new Y(3).f(); z instanceof Z", true);
    test("class Y { int i; def f() { return new Z(i) } }; class Z { int i }; Z z = new Y(3).f(); z instanceof Z", true);
    test("class X { int i; class Y { int i; def f() { return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; Z.ZZ z = new X.Y(3).f(); z instanceof Z.ZZ", true);
    test("class X { int i; class Y { int i; def f() { return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; def z = new X.Y(3).f(); z instanceof Z.ZZ", true);
    test("class X { int i; class Y { int i; Z.ZZ f() { return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; new X.Y(3).f().i", 3);
    test("class X { int i; class Y { int i; Z.ZZ f(){ return new Z.ZZ(i) if this instanceof Y && this instanceof X.Y && this !instanceof Z.ZZ } }}; class Z { class ZZ { int i } }; new X.Y(3).f().i", 3);
    test("class X { int i; class Y { int i; def f(){ this instanceof Y && this instanceof X.Y && this !instanceof Z.ZZ and return new Z.ZZ(i) } }}; class Z { class ZZ { int i } }; new X.Y(3).f().i", 3);
  }

  @Test public void closedOverThis() {
    test("class X { int i = 1; def f(){ return { ++i } } }; def x = new X(); def g = x.f(); g() + g() + x.i", 8);
  }

  @Test public void testStuff2() {
    debug = true;
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; Map a = [y:[z:[x:[y:[z:[i:0]]]]]]; X x = new X(a); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
  }

  @Test public void testStuff() {
    debug = true;
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; Map a = null; X x = new X(a); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
  }

  @Test public void autoCreateFieldsDuringAssignment() {
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i = 4; x.y.z.i", 4);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i = 4; x.y.z.i", 4);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i += 4; x.y.z.i", 7);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i += 4; x.y.z.i", 7);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i *= 4; x.y.z.i", 12);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i *= 4; x.y.z.i", 12);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def a = [y:[z:[x:[y:[z:[i:0]]]]]]; X x = new X(a); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def a = null; X x = new X(a); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.z.x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.z.x.y.z.i = 4; x.y.\"${'z'}\".x['y'].z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.z.x.\"${'y'}\".z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.z.\"${'x'}\".y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.\"${'z'}\".x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(null); x.y.\"${'z'}\".x['y'].z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.z.x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.z.x.y.z.i = 4; x.y.\"${'z'}\".x['y'].z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.z.x.\"${'y'}\".z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.z.\"${'x'}\".y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.\"${'z'}\".x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(null); x.y.\"${'z'}\".x['y'].z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { def y }; X x = new X(null); x.y.z.i += 4; x.y.z.i", 4);
    test("class X { def y }; def x = new X(null); x.y.z.i += 4; x.y.z.i", 4);
    test("class X { List y }; X x = new X(null); x.y[2].z.i += 4; x.y[2].z.i", 4);
    test("class X { List y }; def x = new X(null); x.y[2].z.i += 4; x.y[2].z.i", 4);
    test("class X { Y y }; class Y { def z = null }; X x = new X(null); x.y.z[2].i += 4; x.y.z[2].i", 4);
    test("class X { Y y }; class Y { def z = null }; def x = new X(null); x.y.z[2].i += 4; x.y.z[2].i", 4);
    test("class X { Y y }; class Y { def z = null }; def x = new X(null); x['y']['z'][2].i += 4; x.y.z[2].i", 4);
    test("class X { Y y }; class Y { def z = null }; X x = new X(null); x['y']['z'][2].i += 4; x.y.z[2].i", 4);
    testError("class X { Y y }; class Y { int j = 3 }; def x = new X(null); x.y.z[2].i += 4; x.y.z[2].i", "no such field 'z'");
    testError("class X { Y y }; class Y { int j =3 }; def x = new X(null); x.y.j.i += 4", "expected map/list");
    testError("class X { Y y }; class Y { String j = null }; def x = new X(null); x.y.j.i += 4", "expected field compatible with map");
    testError("class X { Y y }; class Y { String j = null }; def x = new X(null); x.y.j[2].i += 4", "expected field compatible with list");
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i ?= 4; x.y.z.i", 4);
    testError("class X { Y y }; class Y { Z z }; class Z { int i = 3 }; X x = new X(null); x.y.z = 4; x.y.z", "cannot convert from int");
    testError("class X { Y y }; class Y { Z z }; class Z { int i = 3 }; X x = new X(null); x.y.z.i = 4; x.y.z.i", "missing values for mandatory fields");
    testError("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i", "null object");
    testError("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i ?= true ? null : 4; x.y.z.i", "null object");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.f()", "null object");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; def x = new X(null); x.y.f()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.\"${'f'}\"()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; def x = new X(null); x.y.\"${'f'}\"()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2 }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.i = 4; x.y.z.f()", "null object");
  }

  @Test public void importClasses() {
  }

  @Test public void newlinesInClassDecl() {
    test("class\nX\n{\nint\ni\n=\n1\ndef\nf(\n)\n{\ni\n+\ni\n}\n}\nnew\nX\n(\n3\n)\n.\ni", 3);
  }

  @Test public void asyncTests() {
    test("class X { int i = 1 }; new X().\"${sleeper(0,'i')}\"", 1);
    test("class X { int abc = 1 }; new X().\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; X x = new X(); x.\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; def x = new X(); x.\"${sleeper(0,'a') + sleeper(0,'bc')}\"", 1);
    test("class X { int abc = sleeper(0,1) + sleeper(0,new X(3).abc) }; def x = new X(); x.abc", 4);
    test("class X { int i = sleeper(0,-1)+sleeper(0,2); def f(){ return sleeper(0,{ sleeper(0,++i - 1)+sleeper(0,1) }) } }; def x = new X(); def g = x.f(); g() + g() + x.i", 8);
  }

  @Test public void nestedFunctionsWithinClasses() {

  }

//  @Test public void packageTests() {
//    packagName = "x.y.z";
//    test("package x.y.z; class X { static def f(){3}; }; x.y.z.X.f()", 3);
//    test("class X { static f(){3} }; X.f()", 3);
//  }

}
