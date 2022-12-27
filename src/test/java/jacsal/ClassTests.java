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

import static org.junit.jupiter.api.Assertions.fail;

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
    testError("def x = 1; class A { static def f(){x} }; A.f()", "unknown variable");
    testError("def x = 1; class A { def f(){x} }; A.f()", "unknown variable");
    testError("class X {}; def x = X", "cannot convert");
    testError("class X {}; def x; x = X", "cannot convert");
    testError("class X {}; def f(){X}; def x; x = f()", "not compatible");
  }

  @Test public void simpleClass() {
    test("class X {}", null);
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
    testError("class X { int i = sleep(0,-1)+sleep(0,2); static def f(){ return sleep(0,{ sleep(0,++i - 1)+sleep(0,1) }) } }; def x = new X(); def g = x.f(); g() + g() + x.i", "field in static function");
  }

  @Test public void simpleStaticMethods() {
    useAsyncDecorator = false;    // sleep(0,X) won't compile so can't test with async decorator

    test("class X { static def f(){3} }; X.f()", 3);
    test("class X { static int f(x){x*x} }; X.f(3)", 9);
    test("class X { static def f(x){x*x} }; new X().f(3)", 9);
    test("class X { static int f(x){x*x} }; def g = X.f; g(3)", 9);
    test("class X { static int f(x){x*x} }; def g = new X().f; g(3)", 9);
    test("class X { static int f(x){x*x} }; X.\"${'f'}\"(3)", 9);
    test("class X { static int f(x){x*x} }; new X().\"${'f'}\"(3)", 9);
    test("class X { static int f(x){x*x} }; def g = X.\"${'f'}\"; g(3)", 9);
    test("class X { static def f(x){x*x} }; def g = new X().f; g(3)", 9);

    test("class X { static def f(){sleep(0,2) + sleep(0,1)} }; X.f()", 3);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; X.f(3)", 9);
    test("class X { static def f(x){sleep(0,x)*sleep(0,x)} }; new X().f(3)", 9);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; def g = X.f; g(3)", 9);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; def g = new X().f; g(3)", 9);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; X.\"${'f'}\"(3)", 9);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; new X().\"${'f'}\"(3)", 9);
    test("class X { static int f(x){sleep(0,x)*sleep(0,x)} }; def g = X.\"${'f'}\"; g(3)", 9);
    test("class X { static def f(x){sleep(0,x)*sleep(0,x)} }; def g = new X().f; g(3)", 9);

    testError("class X { static int f(x){x*x} }; def g = X.ff; g(3)", "No such field or method 'ff'");
    testError("class X { int i = 0; static def f() { this } }; X.f()", "reference to 'this' in static function");
    testError("class X { int i = 0; static def f() { i } }; X.f()", "reference to field in static function");
  }

  @Test public void fieldClashWithBuiltinMethod() {
    testError("class X { int toString = 1 }; new X().toString", "clashes with builtin method");
  }

  @Test public void simpleFields() {
    test("class X { int i = 1 }; new X().i", 1);
    test("class X { int i }; new X(1).i", 1);
    test("class X { def i = 1 }; new X().i", 1);
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
    testError("class X { int i = 1 }; new X(3).i", "too many arguments");
    test("class X { int i }; new X(3).i", 3);
    test("class X { int i }; new X(i:3).i", 3);
    test("class X { int i }; new X(3)['i']", 3);
    test("class X { int i = 1 }; new X(i:3)['i']", 3);
    test("class X { int i = 1 }; X x = new X(i:3); x['i']", 3);
    test("class X { int i = 1 }; def x = new X(i:3); x['i']", 3);
    test("class X { int i = 1 }; def a = 'i'; new X(i:3)[a]", 3);
    test("class X { int i = 1 }; def a = 'i'; X x = new X(i:3); x[a]", 3);
    test("class X { int i = 1 }; def a = 'i'; def x = new X(i:3); x[a]", 3);
    test("class X { int i = 1; int j = i+1 }; new X().j", 2);
    test("class X { int i; int j = i+1 }; new X(3).j", 4);
    test("class X { int i = 1; int j = i+1 }; new X(i:3,j:7).j", 7);
    testError("class X { int i; int j=2; int k }; new X(i:1,j:2).j", "missing mandatory field: k");
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).k", 3);
    test("class X { int i; int j=2; int k }; new X(i:1,k:3).j", 2);
    test("class X { int i, j=2, k }; new X(i:1,k:3).j", 2);
    testError("int sum = 0; for(int i = 0; i < 10; i++) { class X { int x=i }; sum += new X().x }; sum", "class declaration not allowed here");
    test("class X { Y y }; class Y { int j = 3 }; X x = new X(new Y(j:4)); x.y.j", 4);
    test("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x.y.j", 4);
    testError("class X { Y y }; class Y { int j = 3 }; def x = new X(new Y(j:4)); x.y.z", "no such field 'z'");
    test("class X { Y y }; class Y { int j = 3 }; def x = new X(y:new Y(j:4)); x.y.j", 4);
    test("class X { Y y }; class Y { int j = 3 }; def x = new X(y:new Y(j:4)); x['y'].j", 4);
    testError("class X { Y y }; class Y { int j = 3 }; def x = new X(y:new Y(j:4)); x.['y'].j", "invalid token");
    test("class X { Y y }; class Y { int j = 3 }; X x = new X(y:new Y(j:4)); x.y.j", 4);
    testError("class X { Y y }; class Y { int j = 3 }; X x = new X(new Y(j:4)); x['xx'].j", "no such field or method");
    test("class X { Y y }; class Y { int j = 3 }; X x = new X(y:new Y(j:4)); x['y'].j", 4);
  }

  @Test public void fieldsWithInitialisers() {
    test("class X { int i = 1; long j = i + 2 }; new X().j", 3L);
    test("class X { int i = j+1; long j = i + 2 }; new X().j", 3L);
    test("class X { int i = 1; long j = i+1; long k = j+1 }; new X().k", 3L);

    test("class X { int i = 1; long j = this.i+2 }; new X().j", 3L);
    test("class X { int i = this.j+1; long j = this.i+2 }; new X().j", 3L);

    test("class X { int x; def z={++x + ++y}; def y=++x+2 }; new X(3).z()", 12);
    test("class X { int x; def z(){++x + ++y}; def y=++x+2 }; new X(3).z()", 12);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; new X(3).z()", 12);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; new X(x:3,y:2).z()", 7);
    test("class X { int x; def y=++x+2; def z={++x + ++y} }; def x = new X(0); x.x++; x.z()", 7);
    test("class X { String x = null }; new X().x", null);
    test("class X { String x = null }; new X().x = null", null);
    test("class X { List x = null }; new X().x", null);
    test("class X { List x = null }; new X().x = null", null);
    test("class X { Map x = null }; new X().x", null);
    test("class X { Map x = null }; new X().x = null", null);
  }

  @Test public void methodsAsValues() {
    testError("class X { def f() { h = f }; def h(){3} }; def x = new X(); x.f()", "cannot assign to function");
    test("class X { def h(){3}; def f() { def g = h; g() }; }; new X().f()", 3);
    test("class X { def f() { def g = h; g() }; def h(){3} }; new X().f()", 3);
    test("class X { def f() { def g = h; g() }; def h(){3} }; X x = new X(); x.f()", 3);
    test("class X { def f() { def g = h; g() }; def h(){3} }; def x = new X(); x.f()", 3);
    testError("class X { def f() { def g = h; g() }; def h(){3} }; X x = new X(); x.f = x.h", "field expected");
    testError("class X { def f() { def g = h; g() }; def h(){3} }; def x = new X(); x.f = x.h", "field expected");
    testError("class X { def f() { def g = h; g() }; def h(){3} }; X x = new X(); x.\"${'f'}\" = x.h", "field expected");
    testError("class X { def f() { def g = h; g() }; def h(){3} }; def x = new X(); x.\"${'f'}\" = x.h", "field expected");
    test("class X { def f() { def g = h; g() }; static def h(){3} }; new X().f()", 3);
    test("class X { def f() { def g = h; g() }; static def h(){3} }; def a = new X().f; a()", 3);
    test("class X { int i; def f() { def g = h; g() }; def h(){i} }; def a = new X(4).f; a()", 4);
    test("class X { int i; def f() { def x = i; def g(){ return { i + ++x } }; g() } }; def a = new X(4).f; a()()", 9);
    test("class X { int i; def f() { def x = i; def g(){ return { i + ++x } }; g() } }; def a = new X(4).f; def b = a(); b() + b() + new X(2).f()()", 9 + 10 + 5);
    testError("class X { }; new X(2).a()()", "no such method");
  }

  @Test public void staticMethodAsValues() {
    useAsyncDecorator = false;
    test("class X { static def f() { def g = h; g() }; static def h(){3} }; X.f()", 3);
  }

  @Test public void methodArgsAsList() {
    test("def a = [1,2,3]; class X { def f(int x, int y=7, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { def f(x,y=7,z=8) { x + y + z }}; new X().f(a)", List.of(1, 2, 3, 7, 8));
    test("class X { def f(String x, int y) { x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { def f(String x, int y) { x + y }}; def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { def f = {String x, int y -> x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { def f = {String x, int y -> x + y } };  def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { def f(x,y,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { def f = { x,y,z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { def f = { x,y,z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("class X { def f(x, y) { x + y }}; new X().f([1,2])", 3);
    test("class X { def f(x, y=3) { x + y }}; new X().f([1,2])", List.of(1, 2, 3));
    test("class X { def f(x, y) { x + y }}; def a = [1,2]; new X().f(a)", 3);
    test("class X { def f(x, y=3) { x + y }; }; def a = [1,2]; new X().f(a)", List.of(1, 2, 3));
    test("class X { def f(x, y=3) { x + y }}; new X().f(1,2)", 3);
    test("class X { def f(List x, y=3) { x + y }}; new X().f([1,2])", List.of(1, 2, 3));
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
    test("def a = [1,2,3]; class X { def f(x,y=7,z=8) { x + y + z }}; new X().\"${'f'}\"(a)", List.of(1, 2, 3, 7, 8));
    test("class X { def f(String x, int y) { x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f(String x, int y) { x + y }}; def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f = {String x, int y -> x + y }}; def a = ['x',2]; new X().\"${'f'}\"(a)", "x2");
    testError("class X { def f = {String x, int y -> x + y } };  def a = [2,'x']; new X().\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { def f(int x, int y, int z) { x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z }}; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { def f = { int x, int y, int z -> x + y + z } }; def a = [1,2,3]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("class X { def f = { int x, int y, int z=3 -> x + y + z }; }; def a = [1,2]; X x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("class X { def f(x, y) { x + y }}; new X().\"${'f'}\"([1,2])", 3);
    test("class X { def f(x, y=3) { x + y }}; new X().\"${'f'}\"([1,2])", List.of(1, 2, 3));
    test("class X { def f(x, y) { x + y }}; def a = [1,2]; new X().\"${'f'}\"(a)", 3);
    test("class X { def f(x, y=3) { x + y }; }; def a = [1,2]; new X().\"${'f'}\"(a)", List.of(1, 2, 3));
    test("class X { def f(x, y=3) { x + y }}; new X().\"${'f'}\"(1,2)", 3);
    test("class X { def f(List x, y=3) { x + y }}; new X().\"${'f'}\"([1,2])", List.of(1, 2, 3));
    testError("class X { def f(List x, y=4, z) { x + y + z }}; new X().\"${'f'}\"([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; new X().f(a)", List.of(1, 2, 3, 7, 8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; new X().f(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; new X().f(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { static int f(int x,int y, int z) { x + y + z }}; X x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; new X().f([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; new X().f([1,2])", List.of(1, 2, 3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; new X().f(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; new X().f(a)", List.of(1, 2, 3));
    test("class X { static def f(x, y=3) { x + y }}; new X().f(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; new X().f([1,2])", List.of(1, 2, 3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; new X().f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; def x = new X(); x.f(1,2,3) + x.f(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; def x = new X(); x.f(a)", List.of(1, 2, 3, 7, 8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; def x = new X(); x.f(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; def x = new X(); x.f(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; def x = new X(); x.f(1,2,3) + x.f([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; def x = new X(); x.f([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.f([1,2])", List.of(1, 2, 3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; def x = new X(); x.f(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; def x = new X(); x.f(a)", List.of(1, 2, 3));
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.f(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; def x = new X(); x.f([1,2])", List.of(1, 2, 3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; def x = new X(); x.f([1, 2, 3])", "int cannot be cast to List");

    test("def a = [1,2,3]; class X { static def f(x,y=7,z) { x + y + z }}; def x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"(a)", 12);
    test("def a = [1,2,3]; class X { static def f(x,y=7,z=8) { x + y + z }}; def x = new X(); x.\"${'f'}\"(a)", List.of(1, 2, 3, 7, 8));
    test("class X { static def f(String x, int y) { x + y }}; def a = ['x',2]; def x = new X(); x.\"${'f'}\"(a)", "x2");
    testError("class X { static def f(String x, int y) { x + y }}; def a = [2,'x']; def x = new X(); x.\"${'f'}\"(a)", "cannot convert object of type int to string");
    test("class X { static def f(x,y,z) { x + y + z }}; def x = new X(); x.\"${'f'}\"(1,2,3) + x.\"${'f'}\"([1,2,3])", 12);
    test("class X { static def f(x, y) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", 3);
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", List.of(1, 2, 3));
    test("class X { static def f(x, y) { x + y }}; def a = [1,2]; def x = new X(); x.\"${'f'}\"(a)", 3);
    test("class X { static def f(x, y=3) { x + y }; }; def a = [1,2]; def x = new X(); x.\"${'f'}\"(a)", List.of(1, 2, 3));
    test("class X { static def f(x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"(1,2)", 3);
    test("class X { static def f(List x, y=3) { x + y }}; def x = new X(); x.\"${'f'}\"([1,2])", List.of(1, 2, 3));
    testError("class X { static def f(List x, y=4, z) { x + y + z }}; def x = new X(); x.\"${'f'}\"([1, 2, 3])", "int cannot be cast to List");
  }

  @Test public void staticMethodArgsAsList() {
    useAsyncDecorator = false;

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

  @Test public void staticNamedMethodArgs() {
    useAsyncDecorator = false;

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
  }

  @Test public void constructorArgsAsList() {
    test("class X { String a; int b; List c; Map d }; new X('abc',1,[1,2,3],[a:5]).d.a", 5);
    test("class X { String a; int b; List c = []; Map d = [:] }; new X('abc',1).b", 1);
    testError("class X { String a; int b; List c = []; Map d = [:] }; new X('abc').b", "missing mandatory field");
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc').b", "cannot convert argument of type string to parameter type of int");
    test("class X { String a; int b; List c; Map d = [:] }; new X('abc', 1, [1,2,3]).c[2]", 3);
    testError("class X { String a = ''; int b; List c = []; Map d = [:] }; new X('abc', 1, [1,2,3], 5, 6).c[2]", "too many arguments");
    testError("class X { String a; int b; List c; Map d }; new X('abc', 1, [1,2,3], 5).c[2]", "cannot convert argument of type int");
    testError("class X { String a; int b; List c; Map d }; new X(123, 1, [1,2,3], 5).c[2]", "cannot convert argument of type int");
    test("class X { X x; int i}; new X(new X(null,2),1).x.i", 2);
    test("class X { X x; int i}; new X([x:null,i:2],1).x.i", 2);
    test("class X { X x; int i}; new X([\"${'x'}\":null,i:2],1).x.i", 2);
    test("class X { X x; int i}; Map a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i, j=3 }; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i; int j=3 }; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i}; def a = [x:null,i:2]; new X(a,1).x.i", 2);
    test("class X { X x; int i}; new X([x:null,i:2],1).x.i", 2);
    test("class X { X x; int i}; new X([x:[x:null,i:3],i:2],1).x.x.i", 3);
  }

  @Test public void namedConstructorArgs() {
    test("class X { def a }; new X([a:3]).a.a", 3);
    test("class X { def a; def b = 1 }; new X([a:3]).a.a", 3);
    test("class X { def a; X x = null }; new X([a:3]).a.a", 3);
    test("class X { def a; X x = null }; def z = [a:2,x:null]; new X([a:3,x:z]).a.a", 3);
    test("class X { def a; X x = null }; def z = [a:2,x:null]; new X(a:3,x:z).x.a", 2);
    testError("class X { def a; X x = null }; int z = 1; new X(a:3,x:z).x.a", "cannot convert");
    testError("class X { def a; X x = null }; def z = 1; new X(a:3,x:z).x.a", "int cannot be cast to");
    testError("class X { def a; X x = null }; def z = [a:4,x:4]; new X(a:3,x:z).x.a", "int cannot be cast to");
    testError("class X { def a; X x = null }; def z = [a:4,x:[1,null]]; new X(a:3,x:z).x.a", "List cannot be cast to");
    test("class X { def a }; new X(a:3).a", 3);
    test("class X { def a }; def z = [a:3]; new X(z).a.a", 3);
    test("class X { int i }; new X(2).i", 2);
    test("class X { int i }; new X(i:2).i", 2);
    testError("class X { int i = 0 }; new X(2).i", "too many arguments");
    test("class X { int i = 0 }; new X(i:2).i", 2);
    test("class X { String a; int b; List c; Map d }; new X(a:'abc',b:1,c:[1,2,3],d:[a:5]).d.a", 5);
    test("class X { String a; int b=4; List c; Map d }; def x = new X(a:'abc',c:[1,2,3],d:[a:5]); x.d.a + x.b", 9);
    test("class X { String a; int b=4; List c; Map d }; X x = new X(a:'abc',c:[1,2,3],d:[a:5]); x.d.a + x.b", 9);
    testError("class X { String a; int b=4; List c; Map d }; X x = new X([a:'abc',c:[1,2,3],d:[a:5]]); x.d.a + x.b", "missing mandatory fields: c, d");
    testError("class X { String a; int b=4; List c; Map d }; def args = [a:'abc',c:[1,2,3],d:[a:5]]; X x = new X(args); x.d.a + x.b", "missing mandatory fields: c, d");
    test("class X { Map a; int b; List c=[]; Map d=[:] }; def args = [a:'abc',c:[1,2,3],d:[a:5]]; X x = new X(args, 3); x.a.d.a + x.b", 8);
    testError("class X { String a; int b; List c; Map d }; new X(a:'abc',c:[1,2,3],d:[a:5]).d.a", "missing mandatory field");
  }

  @Test public void complexTypesAsFunctionArgs() {
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; f(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def g = f; g([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; def g = f; g(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def g = f; g([i:1,j:2])", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; f(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; def a = [i:1,j:2]; def g = f; g(a)", 3);
    test("class X { int i; int j }; def f(X x) {x.i + x.j}; f([i:1,j:2])", 3);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; new X(3,4).f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def g = new X(3,4).f; g([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [i:1,j:2]; new X(3,4).f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def a = [i:1,j:2]; def g = new X(3,4).f; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.f(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); x.\"${'f'}\"([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; X x = new X(3,4); def g = x.\"${'f'}\"; g([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.f([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); x.\"${'f'}\"([i:1,j:2])", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [i:1,j:2]; x.\"${'f'}\"(a)", 10);
    test("class X { int i; int j; def f(X x) {x.i + x.j + i + j} }; def x = new X(3,4); def a = [i:1,j:2]; def g = x.\"${'f'}\"; g(a)", 10);
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
    test("class Y { var d=2.0 }; class X { Y y }; def x = new X(y:new Y(d:3)); x.y.d", "#3");
    test("class Y { var d=2.0 }; class X { Y y }; def a = [y:new Y(d:3)]; def x = a as X; x.y.d", "#3");

    test("class Y { var d=2.0 }; class X { Y y = new Y(); Z z = null }; class Z { Y z }; new X().y.d", "#2.0");
    testError("class Y { var d=2.0 }; class X { Y y = new Y(); X z = null }; class X { Y z }; new X().y.d", "already exists");
  }

  @Test public void recursiveClasses() {
    test("class X { X x }; new X(x:null).x", null);
    test("class X { X x }; new X(x:[x:null]).x.x", null);
    test("class X { X x }; def a = [x:[x:null]]; new X(a).x.x.x", null);
    test("class X { X x = null; def y = null }; X x = new X(x:null,y:'xyz'); x.y", "xyz");
    test("class X { X x; def y = null }; X x = new X([x:null,y:'xyz']); x.x.y", "xyz");
    test("class X { X x; def y = null }; X x = new X([\"${'x'}\":null,y:'xyz']); x.x.y", "xyz");
    test("class X { X x; def y = null }; def a = [\"${'x'}\":null,y:'xyz']; X x = new X(a); x.x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = new X(x:null,y:'xyz'); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; X x = null; x = new X(x:null,y:'xyz'); new X(x:x,y:'abc').x.y", "xyz");
    test("class X { X x; def y = null }; def a = [x:null,y:'xyz']; X x = new X(a); new X(x:x,y:'abc').x.x.y", "xyz");
    test("class X { X x = null; def y = null }; new X(x:new X(y:'xyz'),y:'abc').x.y", "xyz");
    testError("class X { X x; def y = null }; new X(x:[x:null,y:'xyz',z:'abc',yy:3],y:'abc').x.y", "no such fields: z, yy");
    testError("class X { X x; def y = null }; def a = [x:null,y:'xyz',z:'abc',yy:3]; new X(a).x.y", "no such fields: z, yy");
    testError("class X { X x; def y = null }; def a = [x:[x:null,y:'xyz',z:'abc',yy:3],y:'abc']; new X(a).x.y", "no such fields: z, yy");
    test("class X { X x; def y = null }; new X(x:[x:null,y:'xyz'],y:'abc').x.y", "xyz");
    test("class X { X x = null; def y = null }; new X(x:[y:'xyz'],y:'abc').x.y", "xyz");

    test("class X { Y y }; class Y { X x }; X x = new X(new Y(new X(null))); Y y = new Y(x); y.x == x && x.y.x.y == null", true);
    test("class X { Y y }; class Y { X x }; X x = new X(y:[x:[y:null]]); Y y = new Y(x); y.x == x && x.y.x.y == null", true);
    test("class X { Y y }; class Y { X x }; def a = [x:[y:[x:[y:null]]]]; def x = new X(a); Y y = new Y(x); y.x == x && x.y.x.y.x.y == null", true);
  }

  @Test public void assignmentsAndEquality() {
    test("class X { int i }; X x = [i:2]; x.i", 2);
    test("class X { int i = 1}; X x; x = [i:2]; x.i", 2);
    test("class X { int i }; X x = [i:2]; x.i = 3", 3);
    test("class X { int i }; X x = [i:2]; x.i = 3; x.i", 3);
    test("class X { int i }; X x = [i:2]; x['i'] = 3", 3);
    test("class X { int i }; X x = [i:2]; x['i'] = 3; x['i']", 3);
    testError("class X { int i }; X x = [2]; x.i", "cannot coerce from list to instance");
    test("class X { int i }; X x = new X(2); X y = new X(2); x == y", true);
    test("class X { int i }; X x = new X(2); X y = new X(2); x != y", false);
    test("class X { int i }; X x = new X(2); X y = new X(3); x = y; x.i", 3);
    test("class X { int i }; X x = new X(2); X y = new X(3); x = y; y.i", 3);
    test("class X { int i }; X x = new X(2); def y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); def y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); X y = new X(2); x == y", true);
    test("class X { int i }; def x = new X(2); x == [i:2] as X", true);
    testError("class X { int i }; def x = new X(2); x == [2] as X", "cannot coerce from list to instance");
    test("class X { int i }; X x = new X(2); x == [i:2] as X", true);
    test("class X { X x }; X x = new X(null); x.x = x; Map m = x as Map; m.x == m", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,3); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,3); x1 != x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,2); x1 != x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(1,2); X x2 = new X(1,2); x1 == x2", true);
    test("class X { int i; int j; X x = null }; X x1 = new X(3,2); X x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(3,2); X x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; X x }; X x1 = new X(1,2,[i:3,j:4,x:null]); X x2 = new X(1,2,null); x1 == x2", false);
    test("class X { int i; int j; X x }; X x1 = new X(1,2,[i:3,j:4,x:null]); X x2 = new X(1,2,null); x1 != x2", true);
    test("class X { int i; int j; X x }; X x1 = new X(1,2,[i:3,j:4,x:null]); X x2 = new X(1,2,[i:3,j:4,x:null]); x1 == x2", true);
    test("class X { int i; int j; X x }; X x1 = new X(1,2,[i:3,j:4,x:null]); X x2 = new X(1,2,[i:3,j:4,x:null]); x1 != x2", false);
    test("class X { int i; int j; X x = null }; def a = [i:3,j:4]; X x1 = new X(i:1,j:2,x:a); X x2 = new X(i:1,j:2,x:[i:4,j:4]); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(i:1,j:2,x:[i:3,j:4]); X x2 = new X(i:1,j:2,x:[i:4,j:4]); x1 == x2", false);
    test("class X { int i; int j; X x = null }; X x1 = new X(i:1,j:2,x:[i:3,j:4]); X x2 = new X(i:1,j:2,x:[i:4,j:4]); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,3); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,3); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,2); x1 != x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(1,2); def x2 = new X(1,2); x1 == x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(3,2); def x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(3,2); def x2 = new X(1,2); x1 != x2", true);
    test("class X { int i; int j; def x = null }; def x1 = new X(i:1,j:2,x:[i:3,j:4]); def x2 = new X(1,2); x1 == x2", false);
    test("class X { int i; int j; def x = null }; def x1 = new X(i:1,j:2,x:[i:3,j:4]); def x2 = new X(1,2); x1 != x2", true);
    test("Z f() { return new Z(3) }; class Z { int i }; Z z = f(); z instanceof Z", true);
    test("def f() { return new Z(3) }; class Z { int i }; Z z = f(); z instanceof Z", true);
    test("class X { int i=1; int j=2 }; new X() == [i:1,j:2]", true);
    test("class X { int i=1; int j=2 }; new X() != [i:1,j:2]", false);
    test("class X { int i=1; int j=2 }; new X() == [i:1,j:2,k:3]", false);
    test("class X { int i=1; int j=2 }; new X() != [i:1,j:2,k:3]", true);
    test("class X { int i=1; int j=2 }; [i:1,j:2] == new X()", true);
    test("class X { int i=1; int j=2 }; [i:1,j:2] != new X()", false);
    test("class X { int i=1; int j=2 }; def x = new X(); def y = [i:1,j:2]; x == y", true);
    test("class X { int i=1; int j=2 }; def x = new X(); def y = [i:1,j:2]; x != y", false);
    test("class X { int i=1; int j=2 }; def x = [i:1,j:2]; def y = new X(); x == y", true);
    test("class X { int i=1; int j=2 }; def x = [i:1,j:2]; def y = new X(); x != y", false);
    test("class X { int i=1; int j=2; X x = new X(i:3,j:4,x:null) }; new X() == [i:1,j:2,x:[i:3,j:4,x:null]]", true);
    test("class X { int i=1; int j=2; X x = new X(i:3,j:4,x:null) }; new X() != [i:1,j:2,x:[i:3,j:4,x:null]]", false);
    test("class X { int i=1; int j=2; X x = new X(i:3,j:4,x:null) }; def x = new X(); def y = [i:1,j:2,x:[i:3,j:4,x:null]]; x == y && y == x", true);
    test("class X { int i=1; int j=2; X x = new X(i:3,j:4,x:null) }; def x = new X(); def y = [i:1,j:2,x:[i:3,j:4,x:null]]; x != y && y != x", false);
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
    test("class X { Y y }; class Y { X x }; def a = [y:[x:[y:null]]]; def x = a as X; Y y = new Y(x); x instanceof X && y instanceof Y && x !instanceof Y && y !instanceof X && x.y instanceof Y && y.x instanceof X", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z; z instanceof X", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z instanceof X", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z instanceof Y", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z instanceof Z", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z !instanceof X", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z !instanceof Y", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Z(); z !instanceof Z", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z instanceof X", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z instanceof Y", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z instanceof Z", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z !instanceof X", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z !instanceof Y", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Z(); z !instanceof Z", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z instanceof X", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z instanceof Y", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z instanceof Z", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z !instanceof X", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z !instanceof Y", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Y z = new Y(); z !instanceof Z", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z instanceof X", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z instanceof Y", true);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z instanceof Z", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z !instanceof X", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z !instanceof Y", false);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def z = new Y(); z !instanceof Z", true);
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
    test("class X { Y y }; class Y { X x }; def a = [y:[x:[y:null]]]; def x = a as X; x.toString()", "[y:[x:[y:null]]]");
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
    testError("class X { int i = 2; class Y { int j = i+1 }}; new X.Y().j", "reference to unknown variable 'i'");
    testError("class X { int i = 2; class Y { def f(){i} }}; new X.Y().f()", "reference to unknown variable 'i'");
  }

  @Test public void innerClassesStaticMethod() {
    useAsyncDecorator = false;
    testError("class X { int i; class Y { int i; static def f() { return new Z.ZZ(3) } }; class Z { class ZZ { int i } }}; X.Y.f() instanceof X.Y.Z.ZZ", "unknown class");
    test("class X { int i; class Y { int i; static def f() { return new Z.ZZ(3) }; class Z { class ZZ { int i } }}}; X.Y.f() instanceof X.Y.Z.ZZ", true);
    test("class X { int i; class Y { int i; static def f() { return new Z.ZZ(3) } }; class Z { class ZZ { int i } }}; X.Y.f().i", 3);
    test("class X { int i; class Y { int i; static def f() { return new Z.ZZ(3) }; class Z { class ZZ { int i; static def f(){4} } }}}; X.Y.Z.ZZ.f()", 4);
  }

  @Test public void closedOverThis() {
    test("class X { int i = 1; def f(){ return { ++i } } }; def x = new X(); def g = x.f(); g() + g() + x.i", 8);
  }

  @Test public void autoCreateFieldsDuringAssignment() {
    test("class X { Y y }; class Y { int i = 3 }; X x = new X(null); x.y.i = 4; x.y.i", 4);
    test("class X { Y y }; class Y { int i = 3 }; def x = new X(null); x.y.i = 4; x.y.i", 4);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i = 4; x.y.z.i", 4);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i = 4; x.y.z.i", 4);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i += 4; x.y.z.i", 7);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i += 4; x.y.z.i", 7);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i *= 4; x.y.z.i", 12);
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; def x = new X(null); x.y.z.i *= 4; x.y.z.i", 12);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def a = [y:[z:[x:[y:[z:[i:0]]]]]]; X x = a; x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.y.z.i = 4; x.y.\"${'z'}\".x['y'].z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.\"${'y'}\".z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.\"${'x'}\".y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.\"${'z'}\".x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.\"${'z'}\".x['y'].z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.z.x.y.z.i = 4; x.y.z.x.y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.z.x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.z.x.y.z.i = 4; x.y.\"${'z'}\".x['y'].z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.z.x.\"${'y'}\".z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.z.\"${'x'}\".y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.\"${'z'}\".x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; def x = new X(); x.y.\"${'z'}\".x['y'].z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
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
    testError("class X { Y y }; class Y { String j = null }; def x = new X(null); x.y.j.i += 4", "cannot set field to map");
    testError("class X { Y y }; class Y { String j = null }; def x = new X(null); x.y.j[2].i += 4", "cannot set field to list");
    test("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i ?= 4; x.y.z.i", 4);
    testError("class X { Y y }; class Y { Z z }; class Z { int i = 3 }; X x = new X(null); x.y.z = 4; x.y.z", "cannot convert from int");
    testError("class X { Y y }; class Y { Z z }; class Z { int i = 3 }; X x = new X(null); x.y.z.i = 4; x.y.z.i", "could not auto-create");
    testError("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i", "null object");
    testError("class X { Y y }; class Y { Z z = null }; class Z { int i = 3 }; X x = new X(null); x.y.z.i ?= true ? null : 4; x.y.z.i", "null object");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.f()", "null object");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; def x = new X(null); x.y.f()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.\"${'f'}\"()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2; def f(){i} }; class Z { int i = 3; def f() {i} }; def x = new X(null); x.y.\"${'f'}\"()", "null value");
    testError("class X { Y y }; class Y { Z z = null; int i = 2 }; class Z { int i = 3; def f() {i} }; X x = new X(null); x.y.i = 4; x.y.z.f()", "null object");
  }

  @Test public void newlinesInClassDecl() {
    test("class\nX\n{\nint\ni\n=\n1\ndef\nf(\n)\n{\ni\n+\ni\n}\n}\nnew\nX\n(\n)\n.\ni", 1);
  }

  @Test public void nestedFunctionsWithinClasses() {
    test("class A { def a() { int x = 1; return { x++ } } }; def f = new A().a(); f() + f() + f()", 6);
    test("class A { def a() { int x = 1; def f() { def g() { x++ }; g() }; return { x + f() }; } }; def b = new A().a(); b() + b() + b()", 12);
    test("class A { def a() { long x = 1; return { x++ } } }; def f = new A().a(); f() + f() + f()", 6L);
    test("class A { def a() { long x = 1; def f() { def g() { x++ }; g() }; return { x + f() }; } }; def b = new A().a(); b() + b() + b()", 12L);
    test("class A { long x = 1; def a() { def f() { def g() { x++ }; g() }; return { x + f() }; } }; def b = new A().a(); b() + b() + b()", 12L);
    test("class A { String x = '1'; def f = { def g() { x + x }; g() } }; new A().f()", "11");
    test("class A { long x = 1; def f = { def g() { x + x }; g() } }; new A().f()", 2L);
    test("class A { double x = 1; def f = { def g() { x + x }; g() } }; new A().f()", 2D);
    test("class A { Decimal x = 1; def f = { def g() { x + x }; g() } }; new A().f()", "#2");
    test("class A { def x = { it=2 -> it + it }; def f = { def g() { x() + x() }; g() }}; new A().f()", 8);
    test("class A { def x = { y=2 -> y+y }; def f = { def g = { def a(x){x+x}; a(x()) + a(x()) }; g() }}; new A().f()", 16);
    test("class A { def f(x,y=++x) { def g() { x+y }; g() }}; new A().f(3)", 8);
    test("class A { def f(x,y={++x}) { def g() { y()+x }; g() }}; new A().f(3)", 8);
    test("class A { def f(x,y=++x+2,z={++x + ++y}) { def g() { x++ + y++ + z() }; g() }}; new A().f(3)", 24);
    test("class A { def f(x,y={++x}()) { def g() { y+x }; g() }}; new A().f(3)", 8);
    test("class A { def f(x) { def g(x) { f(x) + f(x) }; if (x == 1) 1 else g(x-1) }}; new A().f(3)", 4);
    test("class A { def f(x=1) { def g={x+x}; x++; g() }}; new A().f()", 4);
    test("class A { def f(x=1,y=2) { def g(x) { f(x) + f(x) + y }; if (x == 1) 1 else g(x-1) }}; new A().f(3)", 10);
    test("class A { def f(x=1,y=2) { def g(x) { f(x,y) + f(x,y) + y }; if (x == 1) 1 else g(x-1) }}; new A().f(3)", 10);
    test("class A { def f(x=1,y=f(1,1)+1) { def g(x) { f(x,y) + f(x,y) + y }; if (x == 1) 1 else g(x-1) }}; new A().f(3)", 10);
    test("class A { def f() { int x = 1; [{ it + x++}, {it - x++}]}}; def x = new A().f(); x[0](5) + x[0](5) + x[1](0) + x[1](0)", 6);
    test("class A { def f(x=1,g=f,y=x) { if (x == 1) 1 else x + g(x-1) + y }}; new A().f(2)", 5);
    test("class A { def f(x=1,g=f,y=x) { if (x == 1) 1 else x + g(x-1) + y }}; new A().f(4)", 19);
    testError("class A { def a() { def x=1; def f(x){def ff() {g(x)}; ff()}; def g(a){x+a}; f(2) } }; new A().a()", "requires passing closed over variable x");

    test("class A { def func() { def g(x){x}; def f() { def a = g; a(3) }; f()}}; new A().func()", 3);
    test("class A { def func() { def g(x){x}; def f(x){def a = g; x == 1 ? 1 : x+a(x-1)}; f(2)}}; new A().func()", 3);
    test("class A { def func() { def g(x){x}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)}}; new A().func()", 3);
    testError("class A { def func() { def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; f(2)}}; new A().func()", "closed over variable h");
    testError("class A { def func() { def f() { def a = g; a() }; def g(){4}; f()}}; new A().func()", "closes over variable g that has not yet been initialised");
    testError("class A { def func() { def f() { def a = g; def b=y; a()+b() }; def y(){2}; def g(){4}; f()}}; new A().func()", "closes over variables g,y that have not yet been initialised");
    test("class A { def func() { def x = 'x'; def f() { x += 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def x = 'x'; def f() { x = x + 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def x = 'x'; def f() { x *= 3 }; f(); x}}; new A().func()", "xxx");
    test("class A { def func() { def x = 'x'; def f() { x = x * 3 }; f(); x}}; new A().func()", "xxx");
    test("class A { def func() { def x = /x/; def f() { x += 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def x = /x/; def f() { x = x + 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def x = /x/; def f() { x *= 3 }; f(); x}}; new A().func()", "xxx");
    test("class A { def func() { def x = /x/; def f() { x = x * 3 }; f(); x}}; new A().func()", "xxx");
    test("class A { def func() { def it = 'x'; def x = /x/; def f() { x += 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def it = 'x'; def x = /x/; def f() { x = x + 'abc' }; f(); x}}; new A().func()", "xabc");
    test("class A { def func() { def it = 'x'; def x = /x/; def f() { x *= 3 }; f(); x}}; new A().func()", "xxx");
    test("class A { def func() { def it = 'x'; def x = /x/; def f() { x = x * 3 }; f(); x}}; new A().func()", "xxx");
  }

  @Test public void nestedStaticFunctions() {
    useAsyncDecorator = false;

    test("class A { static def a() { int x = 1; return { x++ } } }; def f = A.a(); f() + f() + f()", 6);
    test("class A { static def a() { int x = 1; def f() { def g() { x++ }; g() }; return { x + f() }; } }; def b = A.a(); b() + b() + b()", 12);
    test("class A { static def a() { long x = 1; return { x++ } } }; def f = A.a(); f() + f() + f()", 6L);
    test("class A { static def a() { long x = 1; def f() { def g() { x++ }; g() }; return { x + f() }; } }; def b = A.a(); b() + b() + b()", 12L);
    test("class A { static def a() { def x = { y=2 -> y+y }; def f = { def g = { def a(x){x+x}; a(x()) + a(x()) }; g() }}}; A.a()()", 16);
    test("class A { static def f(x,y=++x) { def g() { x+y }; g() }}; A.f(3)", 8);
    test("class A { static def f(x,y={++x}) { def g() { y()+x }; g() }}; A.f(3)", 8);
    test("class A { static def f(x=1,g=f,y=x) { if (x == 1) 1 else x + g(x-1) + y }}; A.f(2)", 5);
    test("class A { static def func() { def g(x){x}; def f() { def a = g; a(3) }; f()}}; A.func()", 3);
    test("class A { static def func() { def g(x){x}; def f(x){def a = g; x == 1 ? 1 : x+a(x-1)}; f(2)}}; A.func()", 3);
    test("class A { static def func() { def g(x){x}; def f(x,a=g){x == 1 ? 1 : x+a(x-1)}; f(2)}}; A.func()", 3);
    testError("class A { static def func() { def g(x){f(x)}; var h=g; def f(x,a=h){x == 1 ? 1 : x+a(x-1)}; f(2)}}; A.func()", "closed over variable h");
    testError("class A { static def func() { def f() { def a = g; a() }; def g(){4}; f()}}; A.func()", "closes over variable g that has not yet been initialised");
    testError("class A { static def func() { def f() { def a = g; def b=y; a()+b() }; def y(){2}; def g(){4}; f()}}; A.func()", "closes over variables g,y that have not yet been initialised");
    test("class A { static def func() { def x = 'x'; def f() { x += 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def x = 'x'; def f() { x = x + 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def x = 'x'; def f() { x *= 3 }; f(); x}}; A.func()", "xxx");
    test("class A { static def func() { def x = 'x'; def f() { x = x * 3 }; f(); x}}; A.func()", "xxx");
    test("class A { static def func() { def x = /x/; def f() { x += 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def x = /x/; def f() { x = x + 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def x = /x/; def f() { x *= 3 }; f(); x}}; A.func()", "xxx");
    test("class A { static def func() { def x = /x/; def f() { x = x * 3 }; f(); x}}; A.func()", "xxx");
    test("class A { static def func() { def it = 'x'; def x = /x/; def f() { x += 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def it = 'x'; def x = /x/; def f() { x = x + 'abc' }; f(); x}}; A.func()", "xabc");
    test("class A { static def func() { def it = 'x'; def x = /x/; def f() { x *= 3 }; f(); x}}; A.func()", "xxx");
    test("class A { static def func() { def it = 'x'; def x = /x/; def f() { x = x * 3 }; f(); x}}; A.func()", "xxx");
  }

  @Test public void noGlobalsAccess() {
    globals.put("g", "abc");
    test("g + g", "abcabc");
    testError("class X { def f(){g} }; new X().f()", "access to globals not permitted");
  }

  @Test public void baseClasses() {
    testError("class X { int f(){1} }; class Y extends X { long f(){1} }; new Y().f()", "'long' not compatible");
    test("class X { int i = 3 }; class Y extends X { int j = 1 }; Y y = new Y(); y.i + y.j", 4);
    testError("class X { int i = 3 }; class Y extends X { int i = 1 }; Y y = new Y(); y.i + y.j", "field 'i' clashes");
    testError("class X { int i = 3 }; class Y extends X { def i(x){x} }; Y y = new Y(); y.i + y.j", "method name 'i' clashes");
    testError("class X { int i = 2 }; class Y extends X { int j = 1 }; Y y = new Y(3); y.i + y.j", "too many arguments");
    test("class X { int i }; class Y extends X { int j = 1 }; Y y = new Y(3); y.i + y.j", 4);
    test("class X { int i = 2 }; class Y extends X { int j = i+1 }; Y y = new Y(); y.i + y.j", 5);
    test("class X { int i }; class Y extends X { int j = i+1 }; Y y = new Y(3); y.i + y.j", 7);
    test("class X { int i }; class Y extends X { int j = this.i+1 }; Y y = new Y(3); y.i + y.j", 7);
    test("class X { def f(){2} }; class Y extends X { def f(){3} }; Y y = new Y(); y.f()", 3);
    test("class X { int i = 3; X f(){this} }; class Y extends X { Y f(){this} }; Y y = new Y(); y.f().i", 3);
    testError("class X { int i = 3; Y f(){this} }; class Y extends X { X f(){this} }; Y y = new Y(); y.f().i", "not compatible");
    testError("class X { int i = 3; X f(int j){this} }; class Y extends X { Y f(long j){this} }; Y y = new Y(); y.f().i", "different parameter type");
    testError("class X { int i = 3; X f(int j){this} }; class Y extends X { Y f(int j, long jj = 0){this} }; Y y = new Y(); y.f().i", "different number of parameters");
    testError("class X { int i = 3; X f(int j){this} }; class Y extends X { Y f(int jj){this} }; Y y = new Y(); y.f().i", "different parameter name");
    testError("class X { def f(){2} }; class Y extends X { def f(){3} }; X y = null; y.f()", "null object");
    test("class X { def f(){2} }; class Y extends X { def f(){3} }; X y = new Y(); y.f()", 3);
    testError("class X { def f(){2} }; class Y extends X { int f = 3 }; X y = new Y(); y.f()", "field 'f' clashes");
    test("class X { def f(){2} }; class Y extends X { def f(){3} }; def y = new Y(); y.f()", 3);
    test("class X { def f(){2} }; class Y extends X { def f(){3} }; def y = new Y(); y.\"${'f'}\"()", 3);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def g(X x){x.f()}; g(new Z())", 3);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def g(X x){x.f()}; g(new Y())", 2);
    test("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; def g(X x){x.f()}; g(new X())", 1);
    test("class X { def f(){1}; class Y extends X { def f(){2} }}; class Z extends X.Y { def f(){3} }; def g(X x){x.f()}; g(new X())", 1);
    test("class Y extends X { def f(){2} }; class X { def f(){1} }; class Z extends Y { def f(){3} }; def g(X x){x.f()}; g(new Z())", 3);
    testError("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z = new Y()", "cannot convert");
    testError("class X { def f(){1} }; class Y extends X { def f(){2} }; class Z extends Y { def f(){3} }; Z z; z = new Y()", "cannot convert");
    testError("class X extends Y{}; class Y extends X{}; new X()", "current class as a base class");
    testError("class X extends Y{}; class Y extends Z{}; class Z extends X{}; new X()", "current class as a base class");
    testError("class X extends X{}; new X()", "class cannot extend itself");
  }

  @Test public void superReferences() {
    testError("class X { def f(){super.f()} }; X x = new X(); x.f()", "does not extend any base class");
    testError("class X {}; class Y extends X { def f(){super.f()} }; Y y = new Y(); y.f()", "no such method/field 'f'");
    testError("class X {}; class Y extends X { int i = 1; def f(){super.i} }; Y y = new Y(); y.f()", "no such field or method 'i'");
    test("class X {int i = 3}; class Y extends X { def f(){super.i} }; Y y = new Y(); y.f()", 3);
    testError("class X {int i = 3}; class Y extends X { def f(){super.\"${'i'}\"} }; Y y = new Y(); y.f()", "dynamic field lookup not supported");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.\"${'f'}\"() + 3} }; Y y = new Y(); y.f()", "dynamic field lookup not supported");
    testError("class X { def f(){2} }; class Y extends X { def f(){super[\"${'f'}\"]() + 3} }; Y y = new Y(); y.f()", "dynamic field lookup not supported");
    test("class X { def f(x) { x == 0 ? 0 : f(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; X x = new X(); x.f(4)", 10);
    test("class X { def f(x) { x == 0 ? 0 : f(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; Y y = new Y(); y.f(4)", 15);
    test("class X { def f(x) { x == 0 ? 0 : this.\"${'f'}\"(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; Y y = new Y(); y.f(4)", 15);
    test("class X { def f(x) { x == 0 ? 0 : f(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; def x = new X(); x.f(4)", 10);
    test("class X { def f(x) { x == 0 ? 0 : f(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; def y = new Y(); y.f(4)", 15);
    test("class X { def f(x) { x == 0 ? 0 : this.\"${'f'}\"(x-1) + x }}; class Y extends X { def f(x) { super.f(x) + 1 } }; def y = new Y(); y.f(4)", 15);
    test("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; Y y = new Y(); y.f()", 5);
    testError("class X { def f(){2} }; class Y extends X { def f(){super = new X(); super.f() + 3} }; Y y = new Y(); y.f()", "cannot assign to 'super'");
    testError("class X { def f(){i}; int i = 2 }; class Y extends X { def f(){super.super.i = 3; super.f() + 3} }; Y y = new Y(); y.f()", "no such field or method 'super'");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.super = new X(); super.f() + 3} }; Y y = new Y(); y.f()", "no such field 'super'");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.super.f() + super.f() + 3} }; Y y = new Y(); y.f()", "no such field or method");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.'super' = new X(); super.f() + 3} }; Y y = new Y(); y.f()", "no such field 'super'");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; Y y = new Y(); y.super.f()", "no such field");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; class Z extends Y { def f(){this.super.f() + super.f()} }; Z z = new Z(); z.f()", "no such field or method 'super'");
    testError("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; class Z extends Y { def f(){super['super']['f']() + super.f()} }; Z z = new Z(); z.f()", "cannot be performed via '[]'");
    testError("class X { def f(){2} }; class Y extends X { def f(){super?['f']() + 3} }; Y y = new Y(); y.f()", "cannot be performed via '?[]'");
    test("class X { def f(){2} }; class Y extends X { def f(){super.'f'() + 3} }; Y y = new Y(); y.f()", 5);
    test("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; class Z extends Y { def f(){super.f() + super.f()} }; Z z = new Z(); z.f()", 10);
    test("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; class Z extends Y { def f(){super.f() + super.f()} }; X z = new Z(); z.f()", 10);
    test("class X { def f(){2} }; class Y extends X { def f(){super.f() + 3} }; class Z extends Y { def f(){super.f() + super.f()} }; def z = new Z(); z.f()", 10);
  }

  @Test public void asyncTests() {
    test("class X { int i = 1 }; new X().\"${sleep(0,'i')}\"", 1);
    test("class X { int abc = 1 }; new X().\"${sleep(0,'a') + sleep(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; X x = new X(); x.\"${sleep(0,'a') + sleep(0,'bc')}\"", 1);
    test("class X { int abc = 1 }; def x = new X(); x.\"${sleep(0,'a') + sleep(0,'bc')}\"", 1);
    test("class X { int abc = sleep(0,1) + sleep(0,new X(abc:3).abc) }; def x = new X(); x.abc", 4);
    test("class X { int i = sleep(0,-1)+sleep(0,2); def f(){ return sleep(0,{ sleep(0,++i - 1)+sleep(0,1) }) } }; def x = new X(); def g = x.f(); g() + g() + x.i", 8);
    test("class X { Y y = sleep(0,null) }; class Y { int i = sleep(0,1) }; X x = new X(); x.y.i = 2; x.y.i", 2);
    test("class X { Y y = sleep(0,null) }; class Y { int i = sleep(0,1) }; def x = new X(); x.y.i = 2; x.y.i", 2);
    test("class X { def f() { sleep(0,1) + sleep(0,2) }}; class Y extends X { def f() { sleep(0,5) + sleep(0,4)} }; def y = new Y(); y.f()", 9);
    test("class X { def f() { 1 + 2 }}; class Y extends X { def f() { super.f() + 5 + 4} }; def y = new Y(); y.f()", 12);
    test("class X { def f() { sleep(0,1) + sleep(0,2) }}; class Y extends X { def f() { super.f() + sleep(0,5) + sleep(0,4)} }; def y = new Y(); y.f()", 12);
    test("class X { def f(x) { x == 0 ? 0 : sleep(0,f(x-1)) + sleep(0,x) }}; class Y extends X { def f(x) { sleep(0, super.f(x)) + sleep(0,1) } }; def y = new Y(); y.f(4) + y.f(5)", 15 + 21);
    test("class X { def f(x) { x == 0 ? 0 : f(x-1) + x }}; class Y extends X { def f(x) { sleep(0, super.f(x)) + sleep(0,1) } }; def y = new Y(); y.f(4) + y.f(5)", 15 + 21);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.(sleep(0,'y')).z.x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.(sleep(0,'z')).x.y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.(sleep(0,'x')).y.z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.(sleep(0,'y')).z.i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.y.(sleep(0,'z')).i = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { Y y = null }; class Y { Z z = null }; class Z { int i = 3; X x = null }; X x = new X(); x.y.z.x.y.z.(sleep(0,'i')) = 4; x.y.z.\"${'x'}\".y.z.i", 4);
    test("class X { int i = sleep(0,3) }; sleep(0,new X()).i", 3);
    test("class X { int i }; sleep(0,new X(i:sleep(0,3))).i", 3);
    test("class X { Y y = null }; class Y { int i = sleep(0,1) }; X x = new X(); x.y.i = 2", 2);
    test("class X { Y y = null }; class Y { int i = sleep(0,1) }; def x = new X(); x.y.i = 2", 2);
    test("class X { Y y = null }; class Y { int i = 1 }; def x = new X(); x.y.i = 2", 2);
  }

  @Test public void cast() {
    testError("class X {int i}; int i = 3; X x = (X)i", "cannot cast");
    testError("class X {int i}; def i = 3; X x = (X)i", "cannot be cast to");
    test("class X {int i}; def a = [i:3]; X x = (X)a; x.i", 3);  // ???
    test("class X {int i}; Map a = [i:3]; X x = (X)a; x.i", 3);  // ???
    test("class X {int i=3}; X x = new X(); ((X)x).i", 3);
    test("class X {int i=3}; def x = new X(); ((X)x).i", 3);
    test("class X { class Y { int i = 3 }; def f(){ def y = new Y(); ((Y)y).i }}; def x = new X(); x.f()", 3);
    test("class X { class Y { int i = 3 }; def f(){ def y = new Y(); ((Y)y).i }}; def x = new X(); ((X)x).f()", 3);
    test("class X { class Y extends X { int i = 3 }; def f(){ def y = new Y(); ((Y)y).i }}; def x = new X.Y(); ((X)x).f()", 3);
    testError("class X {}; class Y {}; X x = new X(); Y y = (Y)x", "cannot cast");
    test("class X {def f(){2}}; class Y extends X {def f(){3}}; Y y = new Y(); X x = (X)y; x.f()", 3);
    test("class X {}; class Y extends X {def f(){3}}; X x = new Y(); Y y = (Y)x; y.f()", 3);
    testError("class X {}; class Y {}; def x = new X(); def y = (Y)x", "cannot be cast to");
    test("class X {def f(){2}}; class Y extends X {def f(){3}}; def y = new Y(); def x = (X)y; x.f()", 3);
    test("class X {}; class Y extends X {def f(){3}}; def x = new Y(); def y = (Y)x; y.f()", 3);
    test("class X{int i=3}; X f(){[i:4]}; f().i", 4);
    testError("class X{}; class Y{}; def x = new Y(); ((X)x)", "cannot be cast to");
  }

  @Test public void classCompilationExtraStatements() {
    testError(List.of("int i = 1; class X{}"), "", "expecting 'class'");
    testError(List.of("\nint i = 1; class X{}"), "", "expecting 'class'");
    testError(List.of("class X{}; int i = 1"), "", "expecting 'end-of-file'");
    testError(List.of("class X{}\n int i = 1"), "", "expecting 'end-of-file'");
  }

  @Test public void classRecompilation() {
    test(List.of("class X{}", "class X{ def f(){3}}"), "new X().f()", 3);
    testError(List.of("class X{def f(){1}}",
                 "class Y extends X{def f(){2 + super.f()}}",
                 "class X extends Y{def f(){3}}"),
         "def x = new Y(); ((X)x).f() + ((Y)x).f() + x.f()", "cannot be cast");
  }

  @Test public void packages() {
    packageName = "x.y.z";
    testError("package x.y.z; class X { static def f(){3}; }; x.y.z.Y.f()", "unknown class 'Y'");
    testError("package x.y.z; class X { static def f(){3}; }; a.b.c.Y.f()", "unknown variable 'a'");
    testError(List.of("package a.b.c; class X { static def f(){3}; }"), "x.y.z.X.f()", "'a.b.c' conflicts with package name");
    test(List.of("package x.y.z; class X { static def f(){3}; }"),"x.y.z.X.f()", 3);
    test(List.of("class X { def f(){3} }"), "new X().f()", 3);
    test(List.of("class X { class Y { def f(){3} } }"), "new X.Y().f()", 3);
    test(List.of("class X { class Y { def f(){3} } }"), "new x.y.z.X.Y().f()", 3);
    test(List.of("class Y { def f(){3} }", "class X { Y y = null }"), "new x.y.z.X(y:new Y()).y.f()", 3);
    test(List.of("package x.y.z; class Y { def f(){3} }", "package x.y.z; class X { Y y = null }"), "new x.y.z.X(y:new x.y.z.Y()).y.f()", 3);
    test(List.of("package x.y.z; class Y { def f(){3} }", "package x.y.z; class X { Y y = null }"), "new X(y:new Y()).y.f()", 3);
  }

  @Test public void packageStaticMethods() {
    // async tests don't work with static calls when no package specified because sleep(0,X).f() doesn't work when X is a class
    useAsyncDecorator = false;
    packageName = "x.y.z";
    test(List.of("class X { static def f(){3} }"), "X.f()", 3);
    packageName = "";
    test(List.of("class X { static def f(){3}; }"), "X.f()", 3);
  }

  @Test public void rootPackage() {
    packageName = "";
    testError("package a.b.c; class X { static def f(){3}; }; x.y.z.X.f()", "conflicts with package name");
    test(List.of("class X { static def f(){3}; }"), "new X().f()", 3);
    test(List.of("class X { def f(){3} }"), "new X().f()", 3);
    test(List.of("class X { class Y { def f(){3} } }"), "new X.Y().f()", 3);
    packageName = null;
    test(List.of("package x.y.z; class X { class Y { def f(){3} } }"), "package a; new x.y.z.X.Y().f()", 3);
  }

  @Test public void noPackage() {
    packageName = null;
    testError("def x = 1", "package name not declared");
    testError(List.of("class X{}"), "def x = 1", "package name not declared");
  }

  @Test public void separateClassCompilation() {
    packageName = null;
    test(List.of("package a.b; class X{def f(){1}}", "package a.c; class Y extends a.b.X{def f(){2}}"),
         "package a.b; def x = new a.c.Y(); ((X)x).f() + ((a.c.Y)x).f() + x.f()", 6);
    test(List.of("package a.b; class X{def f(){sleep(0,1)}}", "package a.c; class Y extends a.b.X{def f(){sleep(0,2) + super.f()}}"),
         "package a.b; def x = new a.c.Y(); ((X)x).f() + ((a.c.Y)x).f() + x.f()", 9);
    test(List.of("package a.b; class X{def f(){sleep(0,1)}}", "package a.c; class Y extends a.b.X{ a.b.X x = new a.b.X(); def f(){sleep(0,2) + super.f() + x.f()}}"),
         "package a.b; def x = new a.c.Y(); ((X)x).f() + ((a.c.Y)x).f() + x.f()", 12);
  }

  @Test public void importStatements() {
    useAsyncDecorator = false;
    packageName = null;
    test(List.of("package a.b.c; class X{def f(){3}}"), "package a.b.c; new X().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package a.b.c; import a.b.c.X; new X().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package a.b.c; import a.b.c.X as Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package a.b.c; import X; new X().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package a.b.c; import X as Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package x.y.z; import a.b.c.X; new X().f()", 3);
    test(List.of("package a.b.c; class X{def f(){3}}"), "package x.y.z; import a.b.c.X as Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package a.b.c; X.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package a.b.c; import a.b.c.X; X.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package a.b.c; import a.b.c.X as Y; Y.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package a.b.c; import X; X.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package a.b.c; import X as Y; Y.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package x.y.z; import a.b.c.X; X.f()", 3);
    test(List.of("package a.b.c; class X{static def f(){3}}"), "package x.y.z; import a.b.c.X as Y; Y.f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package x.y.z; import a.b.c.X.Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package x.y.z; import a.b.c.X.Y as C; new C().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{static def f(){3}} }"), "package x.y.z; import a.b.c.X; X.Y.f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package x.y.z; import a.b.c.X; new X.Y().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package x.y.z; import a.b.c.X as C; new C.Y().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package a.b.c; import a.b.c.X.Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package a.b.c; import a.b.c.X.Y as C; new C().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package a.b.c; import X.Y; new Y().f()", 3);
    test(List.of("package a.b.c; class X{ class Y{def f(){3}} }"), "package a.b.c; import X.Y as C; new C().f()", 3);
    testError(List.of("package a.b.c; class X{def f(){3}}"), "package x.y.z; import a.b.c.X as Y; import a.b.c.X as Y; new Y().f()", "class of name 'Y' already imported");
    testError(List.of("package a.b.c; class X{def f(){3}}"), "package x.y.z; import a.b.c.X; import a.b.c.X; new Y().f()", "class of name 'X' already imported");
  }

  @Test public void tripleEquals() {
    test("class X{}; new X() === new X()", false);
    test("class X{}; new X() !== new X()", true);
    test("class X{}; X x = new X(); X y = new X(); x === y", false);
    test("class X{}; X x = new X(); X y = new X(); x !== y", true);
    test("class X{}; X x = new X(); X y = x; x === y", true);
    test("class X{}; X x = new X(); X y = x; x !== y", false);
    test("class X{}; def x = new X(); def y = new X(); x === y", false);
    test("class X{}; def x = new X(); def y = new X(); x !== y", true);
    test("class X{}; def x = new X(); def y = x; x === y", true);
    test("class X{}; def x = new X(); def y = x; x !== y", false);
    test("class X{}; def x = new X(); def y = 1; x !== y", true);
    test("class X{int i=1}; X x = new X(); x == [i:1]", true);
    test("class X{int i=1}; X x = new X(); x === [i:1]", false);
    test("class X{int i=1}; X x = new X(); x !== [i:1]", true);
    test("class X{int i=1}; def x = new X(); x == [i:1]", true);
    test("class X{int i=1}; def x = new X(); x === [i:1]", false);
    test("class X{int i=1}; def x = new X(); x !== [i:1]", true);
    test("class X{int i=1}; X x = new X(); [i:1] == x", true);
    test("class X{int i=1}; X x = new X(); [i:1] != x", false);
    test("class X{int i=1}; X x = new X(); [i:1] !== x", true);
    test("class X{int i=1}; def x = new X(); [i:1] == x", true);
    test("class X{int i=1}; def x = new X(); [i:1] === x", false);
    test("class X{int i=1}; def x = new X(); [i:1] !== x", true);
  }
}
