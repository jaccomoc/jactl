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

import org.junit.jupiter.api.Test;

public class SwitchTests extends BaseTest {

  @Test public void switchExprs() {
    test("switch (1) { 1 => 2 }", 2);
    testError("switch (1) { 1 => 2; 1 => 3 }", "literal match occurs multiple times");
    testError("switch (1) { 1,1 => 2; 2 => 3 }", "literal match occurs multiple times");
    test("switch (1) { 1,2 => 2 }", 2);
    test("switch (3) { 1,2 => 2 }", null);
    test("def it = 1; switch{ 1 => 2 }", 2);
    test("def it = 1; switch{ 1,2,3 => 2 }", 2);
    test("def it = 4; switch{ 1,2,3 => 2 }", null);
    testError("int x = 1; switch (x) { 1,'abc',2 => 2 }", "cannot compare type int to String");
    test("def x = 1; switch (x) { 1,'abc',2 => 2 }", 2);
    test("def it = 1; switch{ 1,'abc',2 => 2 }", 2);
    test("def x = 3; switch (x) { 1,'abc',2 => 2 }", null);
    test("def it = 3; switch{ 1,'abc',2 => 2 }", null);
    test("def x = 'A'; switch (x) { 1,'abc',2 => 2; 65 => 'a' }", "a");
    test("def it = 'A'; switch{ 1,'abc',2 => 2\n65 => 'a' }", "a");
    test("switch ('A') { 1,'abc',2 => 2; 65 => 'a' }", "a");
    testError("switch ('AB') { 1,'abc',2 => 2\n65 => 'a' }", "multiple chars cannot be cast to int");
    testError("switch (1) { default => 3; default => 4 }", "cannot have multiple 'default'");
    test("List x = []; switch (x) { [] => 1 }", 1);
    test("def x = []; switch (x) { [] => 1 }", 1);
    test("switch([]) { [] => 1 }", 1);
    test("switch([1,2,3]) { [1,2],[1,2,3] => 1 }", 1);
    test("def x = [1,2,3]; switch (x) { [1,2],[1,2,4] => 1; [1,2,3] => 2 }", 2);
    test("List x = [1,2,3]; switch (x) { [1,2],[1,2,4] => 1; [1,2,3] => 2 }", 2);
    testError("List x = [1,2,3]; switch (x) { [1,2],[1,2,3] => 1; [1,2,3] => 2 }", "literal match occurs multiple times");
    test("def i = 2; switch (i) { 1 => 1; default => 2 }", 2);
    testError("switch(0) { 0 || 1 || 2 => 2\n3 => 4\ndefault => 5 }", "unexpected token");
    testError("switch(0) { 0 or 1 or 2 => 2; 3 => 4; default => 5 }", "unexpected token 'or'");
    test("def it = 'abc'; switch{ 'abc' => 1; default => 2 }", 1);
    test("def it = 'abc'; switch (it[0]) { 'a' => 1; default => 2 }", 1);
    test("def it = 'abc'; switch (it[0]) { 'a' => it; default => 2 }", "a");
    test("def it = 'abc'; def x = switch (it[0]) { 'a' => it; default => 2 }; it + x", "abca");
    test("switch([1,2,3].map{it+it}) { [2,4,6] => 3; default => 0 }", 3);
    test("switch([1,2,3] as int[]) { [1,2,3] => 2; default => 0 }", 2);
    test("def x = [1,2,3].map{it+it}; switch (x) { [2,4,6] => 3; default => 0 }", 3);
    test("def x = [1,2,3] as int[]; switch (x) { [1,2,3] => 2; default => 0 }", 2);
  }

  @Test public void switchWithArrays() {
    test("int[] x = [1,2,3]; switch (x) { [1,2],[1,2,4] => 1; [1,2,3] => 2 }", 2);
  }

  @Test public void switchWithRegex() {
    test("switch('abc') { /a/r => 1 }", 1);
    test("switch('abc') { /a/r => 1; default => 2 }", 1);
    test("switch('abc') { /z/r => 1; default => 2 }", 2);
    test("def it = 'abc'; switch{ /a/r => 1; default => 2 }", 1);
    test("def it = 'abc'; switch{ /a(.)c/r => $1; default => 2 }", "b");
    testError("def it = ['abc']; switch{ /a(.)c/r => $1; default => 2 }", "cannot convert object");
    testError("switch (['abc']) { /a(.)c/r => $1; default => 2 }", "cannot compare");
    testError("def it = 'abc'; switch{ /a(.)c/ => $1; default => $1 }", "reference to regex capture variable");
    test("switch('abc') { /a/ => 1 }", null);
    test("def x = 'bc'; switch('abc') { /a$x/ => 1 }", 1);
    testError("switch (['abc']) { /a(.)c/ => $1; default => 2 }", "no regex match");
  }

  @Test public void switchOnType() {
    test("switch (1) { int => 2 }", 2);
    testError("String x = switch (1) { int => 2 }", "cannot convert object of type int to string");
    testError("switch (1) { String => 2 }", "can never be string");
    testError("switch (1) { String,int => 2 }", "can never be string");
    test("def x = 1; switch (x) { String,int => 2 }", 2);
    test("def x = 1; switch (x) { String => 2; int => 3 }", 3);
    test("def x = 1; switch (x) { String,1 => 2; int,'abc' => 3 }", 2);
    test("def x = 'abc'; switch (x) { String,1 => 2; int,'abc' => 3 }", 2);
    test("def x = 'abc'; switch (x) { long,1 => 2; int,'abc' => 3 }", 3);
    test("def x = []; switch (x) { String => 2\n int => 3\n List => 4\n default => 5 }", 4);
    test("def x = [:]; switch (x) { String => 2\n Map,int => 3; List => 4\n default => 5 }", 3);
    testError("int x = 3; switch(x) { long => 4; default => 2}", "can never be long");
    testError("int x = 3; switch(x) { long i => i; default => 2}", "can never be long");
    test("def x = 3; switch(x) { long i => i; int i => i*i; default => 2} == 9", true);
    test("switch(null) { long => 1; int => 2; String => 3; def => 4 }", 4);
    test("def x = null; switch(x) { long => 1; int => 2; String => 3; def => 4 }", 4);
    testError("List x = null; switch(x) { long => 1; int => 2; String => 3; def => 4 }", "can never be long");
    test("def x = 'abc' as byte[]; switch(x) { long => 1; int => 2; String => 3; def => 4 }", 4);
  }

//  @Test public void switchOnMap() {
//    test("switch ([:]) { [:] => 1; default => 2 }", 1);
//    test("switch ([a:1]) { [a:1] => 1; default => 2 }", 1);
//    testError("switch ([a:1]) { [_] => 1; default => 2 }", "can never match");
//    test("switch ([a:1]) { [a:_] => 1; default => 2 }", 2);
//    test("switch ([1]) { [*] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [_] => 1; default => 2 }", 2);
//    test("switch ([1,2]) { [_,*] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [*, _] => 1; default => 2 }", 1);
//    test("switch ([1,2,3]) { [_,_,_] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [_, *, _] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [*, 3] => 1; default => 2 }", 2);
//    test("switch ([1,2]) { [*, 2] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [1,*] => 1; default => 2 }", 1);
//    test("switch ([1,2]) { [1,*,_] => 1; default => 2 }", 1);
//  }

  @Test public void switchOnList() {
    test("switch ([]) { [] => 1; default => 2 }", 1);
    test("switch ([1]) { [] => 1; default => 2 }", 2);
    test("switch ([1]) { [_] => 1; default => 2 }", 1);
    test("switch ([1]) { [*] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [_] => 1; default => 2 }", 2);
    test("switch ([1,2]) { [_,*] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [*, _] => 1; default => 2 }", 1);
    test("switch ([1,2,3]) { [_,_,_] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [_, *, _] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [*, 3] => 1; default => 2 }", 2);
    test("switch ([1,2]) { [*, 2] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [1,*] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [1,*,_] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [1,*,2] => 1; default => 2 }", 1);
    test("switch ([1,2]) { [1,_,2] => 1; default => 2 }", 2);
    test("switch ([1,2]) { [2,*,_] => 1; default => 2 }", 2);
    test("switch ([1,2]) { [_,2] => 1; default => 2 }", 1);
    testError("switch ([1,2]) { [[_],2] => 1; [_,*] => 3; default => 2 }", "object of type int cannot be cast to list");
    test("switch ([[1],2]) { [[_],2] => 1; default => 2 }", 1);
    test("switch ([[1],2]) { [[1],2] => 1; default => 2 }", 1);
    test("switch ([1,2,3]) { [_,2,_] => 1; default => 2 }", 1);
    testError("switch ([1,2,3]) { _ => 1; default => 2 }", "default case is never applicable");
    testError("switch ([1,2,3]) { _ => 1; _ => 2 }", "unreachable switch case");
    testError("switch ([1,2,3]) { _ => 1; [1,2,3] => 2 }", "unreachable switch case");
    test("switch ([1,2,3]) { [1,2] => 2; _ => 1 }", 1);
    test("switch ([1,2,3]) { [1,2] => 2; [1,2,4],[1,2,3] => 1 }", 1);
    testError("switch ([1,2,3]) { [1,2] => 2; [_],[_,_] => 1 }", "only supported for simple literal values");
    testError("switch ([1,2,3]) { [1,2] => 2; _,_ => 1 }", "only supported for simple literal values");
    test("switch ([1,2,3]) { [1,2,int] => 2; _ => 1 }", 2);
    test("switch ([1,2,3]) { [_,2,int] => 2; _ => 1 }", 2);
    test("switch ([1,[2,'abc'],3]) { [_,[_,String],int] => 2; _ => 1 }", 2);
    test("switch ([1,[2,'abc'],3]) { [*,[2,String],int] => 2; _ => 1 }", 2);
    test("switch ([1,[2,'abc'],3]) { [*,[*,String],int] => 2; _ => 1 }", 2);
    testError("List x = [1,2,3]; switch(x) { i => i; [1,4,3] => 7 }", "unreachable switch case");
    testError("List x = [1,2,3]; switch(x) { List i => i; [1,4,3] => 7 }", "unreachable switch case");
    test("List x = [1,2,3]; switch(x) { i => i }", Utils.listOf(1,2,3));
    testError("List x = [1,2,3]; switch(x) { int i => i }", "type of binding variable not compatible");
    test("List x = [1,2,3]; switch(x) { List i => i }", Utils.listOf(1,2,3));
    test("List x = [1,2,3]; switch(x) { def i => i }", Utils.listOf(1,2,3));
    test("List x = [1,2,3]; switch(x.size()) { int i => i }", 3);
    testError("List x = [1,2,3]; switch(x.size()) { long i => i }", "can never be long");
    test("List x = [1,2,3]; switch(x) { [1,i,3] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,3]; switch(x) { [1,int i,3] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,3]; switch(x) { [1,def i,3] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,'abc','abc']; switch(x) { [1,int i,_] => i; default => 7 }", 7);
    test("List x = [1,2,'abc']; switch(x) { [1,int i,i] => i; default => 7 }", 7);
    testError("List x = [1,2,2]; switch(x) { [1,int i,int i] => i; [1,4,3] => 7 }", "'i' already declared");
    test("List x = [1,2,3,2]; switch(x) { [_,x,_,x] => x }", 2);
    test("def x = [1,2,3]; switch(x) { i => i }", Utils.listOf(1,2,3));
    test("def x = [1,2,3]; switch(x) { int i => i; default => 5 }", 5);
    test("def x = [1,2,3]; switch(x) { List i => i }", Utils.listOf(1,2,3));
    test("def x = [1,2,3]; switch(x) { def i => i }", Utils.listOf(1,2,3));
    test("def x = [1,2,3]; switch(x.size()) { int i => i }", 3);
    test("def x = [1,2,3]; switch(x.size()) { long i => i; int j => j*j }", 9);
    test("def x = [1,2,3]; switch(x) { [1,i,3] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,3]; switch(x) { [1,int i,3] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,2]; switch(x) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,'abc','abc']; switch(x) { [1,int i,_] => i; default => 7 }", 7);
    test("def x = [1,2,'abc']; switch(x) { [1,int i,i] => i; default => 7 }", 7);
    testError("def x = [1,2,2]; switch(x) { [1,int i,int i] => i; [1,4,3] => 7 }", "'i' already declared");
    test("def x = [1,2,3,2]; switch(x) { [_,x,_,x] => x }", 2);
    test("def x = [1,2,3,[4,5,[2,7]]]; switch(x) { [_,x,_,[*,[x,y]]] => y }", 7);
    test("def x = [1,2,3,[4,5,[2,7]]]; switch(x) { [_,x,_,[*,[x,int y]]] => y }", 7);
    test("def x = [1,[2,[3,4],2],3,[4,5,[2,[3,4]]]]; switch(x) { [_,[x,List y,x],_,[*,[x,y]]] => y }", Utils.listOf(3,4));
    test("def x = [1,[2,[3,4],2],3,[4,5,[2,[3,4]]]]; switch(x) { [_,[x,y,x],_,[*,[x,y]]] => y }", Utils.listOf(3,4));
  }

  @Test public void switchOnClassTypes() {
    test("class X{}; X x = new X(); switch(x) { X => 1 }", 1);
    testError("class X{}; X x = new X(); switch(x) { X => 1; int => 2 }", "can never be int");
    testError("class X{}; X x = new X(); switch(x) { X => 1; def => 2 }", "unreachable switch case");
    testError("class X{}; X x = new X(); switch(x) { def => 2; X => 1 }",  "unreachable switch case");
    test("class X{}; def x = new X(); switch(x) { X => 1; int => 2 }", 1);
    test("class X{}; def x = new X(); switch(x) { X => 1; def => 2 }", 1);
    testError("class X{}; def x = new X(); switch(x) { def => 2; X => 1 }",  "unreachable switch case");
    test("class X{int i}; def x = new X(3); switch(x) { X x => x.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new Y(3); switch(x) { X x => x.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { X x => x.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { Y x => x.i; def => 2 }", 2);
    testError("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { X x => x.i; Y => 2 }", "unreachable switch case");
  }

  @Test public void switchExprWithBlocks() {
    test("List x = [1,2,3]; switch(x) { [1,2],[1,2,3] => {1}; [1,4,3] => 2 }", 1);
    test("List x = [1,2,3]; switch(x) { [1,2],[1,2,3] => { def j = 3; j-2 }; [1,4,3] => 2 }", 1);
    test("switch(2) { 2 => { while(0){} }; 3 => 2 }", null);
    test("switch(1) { 1 => { switch(2) { 2 => { while(0){} }; 3 => 2 } }; 2 => 3}", null);
    test("switch(1) { 1 => switch(2) { 2 => { while(0){} }; 3 => 2 }; 2 => 3}", null);
    test("switch(1) { 1 => sleep(0, switch(2) { 2 => { while(0){} }; 3 => 2 }); 2 => 3}", null);
    test("switch(1) { 1 => { sleep(0, switch(2) { 2 => { while(0){} }; 3 => 2 }) }; 2 => 3}", null);
    test("switch(1) { 1 => { switch(2) { 2,4,5 => { while(sleep(0,0)){} }; 3 => 2 } }; 2 => 3}", null);
    test("switch(1) { 1 => { switch(2) { 2 => { while(sleep(0,0)){}; sleep(0,4) }; 3 => 2 } }; 2 => 3}", 4);
    test("switch(1) { 1 => { switch(2) { 2 => { if (false) 1 }; 3 => 2 } }; 2 => 3}", null);
    test("switch(1) { 1 => { switch(2) { 2 => { if (true) { int x = 1 } }; 3 => 2 } }; 2 => 3}", 1);

    doTest("switch(1) { 1 => { switch(2) { 2 => { if (false) { int x = 1 } else { 4 } }; default => 2L } }; 2 => 3}", 4L, false);
    doTest("switch(1) { 1 => { switch(2) { 2 => { if (false) { int x = 1 } else { 4 } }; default => 2L } }; 2 => 3}", 4, true);

    test("switch(1) { 1 => { return 2 }; 2 => 3 }", 2);
    test("switch(1) { 1 => { if (true) return 2 }; 2 => 3 }", 2);
    test("switch(1) { 1 => { if (false) return 2 }; 2 => 3 }", null);
  }

}
