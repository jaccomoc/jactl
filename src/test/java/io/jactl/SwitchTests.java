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
    testError("switch ('a') { 'abc','1' => 2; '2','abc' => 3 }", "literal match occurs multiple times");
    test("switch (1) { 1,2 => 2 }", 2);
    test("switch (1L) { 1,2 => 2 }", 2);
    test("switch (1L) { 1L,2 => 2 }", 2);
    test("def x = 1L; switch (x) { 1L,2 => 2 }", 2);
    test("def x = 1L; switch (x) { 1,2 => 2 }", 2);
    test("switch (1.0) { 1,2 => 2 }", 2);
    test("switch (1.0) { 1.0,2 => 2 }", 2);
    test("switch (1.0) { 1.00,2 => 2 }", 2);
    test("def x = 1.0; switch (x) { 1,2 => 2 }", 2);
    test("def x = 1.00; switch (x) { 1.0,2 => 2 }", 2);
    test("var x = 1.0; switch (x) { 1,2 => 2 }", 2);
    test("var x = 1.00; switch (x) { 1.0,2 => 2 }", 2);
    test("switch (1.0D) { 1,2 => 2 }", 2);
    test("switch (1.0D) { 1.0,2 => 2 }", 2);
    test("switch (1.0D) { 1.0D,2 => 2 }", 2);
    test("def x = 1.0D; switch (x) { 1,2 => 2 }", 2);
    test("var x = 1.0D; switch (x) { 1,2 => 2 }", 2);
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
    testError("switch{ 'abc' => 1; default => 2 }", "unknown variable 'it'");
    test("def it = 'abc'; switch (it[0]) { 'a' => 1; default => 2 }", 1);
    test("def it = 'abc'; switch (it[0]) { 'a' => it; default => 2 }", "a");
    test("def it = 'abc'; def x = switch (it[0]) { 'a' => it; default => 2 }; it + x", "abca");
    test("switch([1,2,3].map{it+it}) { [2,4,6] => 3; default => 0 }", 3);
    test("switch([1,2,3] as int[]) { [1,2,3] => 2; default => 0 }", 2);
    test("def x = [1,2,3].map{it+it}; switch (x) { [2,4,6] => 3; default => 0 }", 3);
    test("def x = [1,2,3] as int[]; switch (x) { [1,2,3] => 2; default => 0 }", 2);
    test("def it = [1,2,3] as int[]; switch{ [1,2,3] => 2; default => 0 }", 2);
    test("def it = [1,2,3] as int[]; switch\n{\n [\n1,\n2,\n3\n]\n =>\n 2\ndefault\n =>\n 0\n }", 2);
    testError("int x = 2\nswitch (x) {\n  1,2 => x\n  'abc' => x\n}", "cannot compare type int to string");
  }

  @Test public void switchWithArrays() {
    test("int[] x = [1,2,3]; switch (x) { [1,2],[1,2,4] => 1; [1,2,3] => 2 }", 2);
  }

  @Test public void switchWithRecursion() {
    test("def f(it) { switch { [a,b] => f(a)+f(b); [c] => c; d => d } }; f([[1,2],[3]])", 6);
    test("def f(it) { switch { [a,b] => f(a)+f(b); [c] => c; d => d } }; f([[1,2],[[5],[1,2]]])", 11);
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
    test("def x = 'abc'; switch (x) { String,1 => 2; int => 3 }", 2);
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

  @Test public void patternCoverages() {
    testError("List a = [1,2]; switch(a) { [x,y],[_,x] => x }", "covered by previous");
    test("List a = [1,2]; switch(a) { [x,y] if x == 1,[_,x] => x }", 1);
    testError("List a = ['aa','bb','cc']; switch(a) { [_,i,'cc'] => i; [_,i,'cc'] => 7 }", "covered by previous");
    testError("def x = 1; switch (x) { String,1 => 2; int,'abc' => 3 }", "covered by previous");
    test("def a = [1,2]; switch(a) { [1,2],[_,z] => z }", null);
    testError("def a; switch(a) { [_,i,'cc'] => i; [_,[x:i],'cc'] => 7 }", "covered by previous");
    test("def a=[1,[x:3]]; switch(a) { [_,i,'cc'] => i; [_,[x:i]] => i }", 3);
    test("def a=[1,[x:3]]; switch(a) { [_,int i] => i; [_,[x:i]] => i }", 3);
  }

  @Test public void switchOnMap() {
    test("switch ([:]) { Map => 1; default => 2 }", 1);
    test("switch ([:]) { Map x => x; default => 2 }", Utils.mapOf());
    test("switch ([:]) { [:] => 1; default => 2 }", 1);
    test("switch ([:]) { [a:_] => 1; default => 2 }", 2);
    test("switch ([:]) { [a:_,*] => 1; default => 2 }", 2);
    test("switch ([a:1]) { [a:1] => 1; default => 2 }", 1);
    testError("switch ([a:1]) { [_] => 1; default => 2 }", "can never match");
    test("switch ([a:1]) { [a:_] => 1; default => 2 }", 1);
    test("switch ([a:1]) { [b:_] => 1; default => 2 }", 2);
    testError("switch ([a:1]) { [*] => 1; default => 2 }", "can never match a list");
    test("switch ([a:1,b:2]) { [a:_] => 1; default => 2 }", 2);
    test("switch ([a:1,b:2]) { [a:_,b:_] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:_,*] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [b:_,*] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [*,b:_] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:1,*] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:1,b:_] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:1,*,b:_] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:2,*,b:_] => 1; default => 2 }", 2);
    test("switch ([a:1,b:2]) { [a:1,*,b:_] => 1; _ => 2 }", 1);
    test("switch ([a:1,b:2]) { [a:2,*,b:_] => 1; _ => 2 }", 2);
    test("switch ([a:1,b:2,c:3]) { [a:1,*,b:_] => 1; default => 2 }", 1);
    test("switch ([a:1,b:2,c:3]) { [a:1,b:_] => 1; default => 2 }", 2);
    test("switch ([a:1,b:[2],c:3]) { [a:1,b:[_],*] => 1; default => 2 }", 1);
    test("switch ([a:1,b:[2,3],c:3]) { [a:1,b:[_,_],*] => 1; default => 2 }", 1);
    test("switch ([a:1,b:[2,3],c:3]) { [a:1,b:[x,y],*] => x+y; default => 2 }", 5);
    test("switch ([a:1,b:[2,3],c:3]) { [a:1,b:[int x,y],*] => x+y; default => 2 }", 5);
    test("switch ([a:1,b:[2,3],c:3]) { [a:1,b:[int x,long y],*] => x+y; default => 2 } == 2", true);
    test("switch ([a:1,b:[2,3],c:3]) { [a:1,b:x,*] => x; default => 2 }", Utils.listOf(2,3));
    test("switch ([a:1,b:[z:4],c:3]) { [a:1,b:x,*] => x; default => 2 }", Utils.mapOf("z",4));
    test("def val =[:]; switch(val) { Map => 1; default => 2 }", 1);
    test("def val =[:]; switch(val) { Map x => x; default => 2 }", Utils.mapOf());
    test("def val =[:]; switch(val) { [:] => 1; default => 2 }", 1);
    test("def val =[:]; switch(val) { [a:_] => 1; default => 2 }", 2);
    test("def val =[:]; switch(val) { [a:_,*] => 1; default => 2 }", 2);
    test("def val =[a:1]; switch(val) { [a:1] => 1; default => 2 }", 1);
    test("def val =[a:1]; switch(val) { [i] => i; default => 2 }", 2);
    test("def val = 1; switch(val) { [i] => i; default => 2 }", 2);
    test("def val =[1]; switch(val) { [a:i] => i; default => 2 }", 2);
    test("def val =[a:1]; switch(val) { [_] => 1; default => 2 }", 2);
    test("def val =[a:1]; switch(val) { [a:_] => 1; default => 2 }", 1);
    test("def val =[a:1]; switch(val) { [b:_] => 1; default => 2 }", 2);
    test("def val =[a:1]; switch(val) { [*] => 1; default => 2 }", 2);
    test("def val =[a:1,b:2]; switch(val) { [a:_] => 1; default => 2 }", 2);
    test("def val =[a:1,b:2]; switch(val) { [a:_,b:_] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:_,*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [b:_,*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [*,b:_] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:1,*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:1,b:_] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:1,*,b:_] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:2,*,b:_] => 1; default => 2 }", 2);
    test("def val =[a:1,b:2]; switch(val) { [a:1,*,b:_] => 1; _ => 2 }", 1);
    test("def val =[a:1,b:2]; switch(val) { [a:1,*,b:x] => x*x; default => 0 }", 4);
    test("def val =[a:1,b:2,c:3]; switch(val) { [a:1,*,b:_] => 1; default => 2 }", 1);
    test("def val =[a:1,b:2,c:3]; switch(val) { [a:1,b:_] => 1; default => 2 }", 2);
    test("def val =[a:1,b:[2],c:3]; switch(val) { [a:1,b:[_],*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:[2,3],c:3]; switch(val) { [a:1,b:[_,_],*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:[d:4],c:3]; switch(val) { [a:1,b:Map,*] => 1; default => 2 }", 1);
    test("def val =[a:1,b:[d:4],c:3]; switch(val) { [a:1,b:[d:x],*] => x; default => 2 }", 4);
    test("def val =[a:1,b:[2,3],c:3]; switch(val) { [a:1,b:[x,y],*] => x+y; default => 2 }", 5);
    test("def val =[a:1,b:[2,3],c:3]; switch(val) { [a:1,b:[int x,y],*] => x+y; default => 2 }", 5);
    test("def val =[a:1,b:[2,3],c:3]; switch(val) { [a:1,b:[int x,long y],*] => x+y; default => 2 } == 2", true);
    test("def val =[a:1,b:[2,3],c:3]; switch(val) { [a:1,b:x,*] => x; default => 2 }", Utils.listOf(2,3));
    test("def val =[a:1,b:[z:4],c:3]; switch(val) { [a:1,b:x,*] => x; default => 2 }", Utils.mapOf("z",4));
  }

  @Test public void switchOnList() {
    test("def x = 1; switch (x) { [a,b] => 1; _ => 2 }", 2);
    testError("switch('abc') { ['a','b','c'] => true; default => false }", "can never match a list");
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
    test("switch ([1,2]) { [[_],2] => 1; [_,*] => 3; default => 2 }", 3);
    test("def a = [1,2]; switch (a) { [[_],2] => 1; [_,*] => 3; default => 2 }", 3);
    test("switch ([[1],2]) { [[_],2] => 1; default => 2 }", 1);
    test("switch ([[1],2]) { [[1],2] => 1; default => 2 }", 1);
    test("switch ([1,2,3]) { [_,2,_] => 1; default => 2 }", 1);
    testError("switch ([1,2,3]) { _ => 1; default => 2 }", "default case is never applicable");
    testError("switch ([1,2,3]) { _ => 1; _ => 2 }", "unreachable switch case");
    testError("switch ([1,2,3]) { _ => 1; [1,2,3] => 2 }", "unreachable switch case");
    test("switch ([1,2,3]) { [1,2] => 2; _ => 1 }", 1);
    test("switch ([1,2,3]) { [1,2] => 2; [1,2,4],[1,2,3] => 1 }", 1);
    test("switch ([1,2,3]) { [1,2] => 2; [_],[_,_,_] => 1 }", 1);
    test("switch ([1,2,3]) { [1,2] => 2; [int],[_,int,_] => 1 }", 1);
    testError("switch ([1,2,3]) { [1,2] => 2; _,_ => 1 }", "unreachable");
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
    test("List x = [1,2,3]; switch(x) { [1,i,3] => i; [1,4,4] => 7 }", 2);
    test("List x = [1,2,3]; switch(x) { [1,int i,3] => i; [1,4,4] => 7 }", 2);
    test("List x = [1,2,3]; switch(x) { [1,def i,3] => i; [1,4,4] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,2,2]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List x = [1,'abc','abc']; switch(x) { [1,int i,_] => i; default => 7 }", 7);
    test("List x = [1,2,'abc']; switch(x) { [1,int i,i] => i; default => 7 }", 7);
    testError("List x = [1,2,2]; switch(x) { [1,int i,int i] => i; [1,4,3] => 7 }", "'i' already declared");
    testError("List a = [1,2,3]; switch(a) { i => i; [1,4,3] => 7 }", "unreachable switch case");
    testError("List a = [1,2,3]; switch(a) { List i => i; [1,4,3] => 7 }", "unreachable switch case");
    test("List a = [1,2,3]; switch(a) { i => i }", Utils.listOf(1,2,3));
    testError("List a = [1,2,3]; switch(a) { int i => i }", "type of binding variable not compatible");
    test("List a = [1,2,3]; switch(a) { List i => i }", Utils.listOf(1,2,3));
    testError("List a = [1,2,3]; switch(a) { List a => a }", "binding variable 'a' shadows another variable");
    test("List a = [1,2,3]; switch(a) { def i => i }", Utils.listOf(1,2,3));
    test("List a = [1,2,3]; switch(a.size()) { int i => i }", 3);
    testError("List a = [1,2,3]; switch(a.size()) { long i => i }", "can never be long");
    testError("List a = [1,2,3]; switch(a) { [1,i,3] => i; [1,4,3] => 7 }", "covered by previous");
    test("List a = [1,2,3]; switch(a) { [1,i,3] => i; [1,3] => 7 }", 2);
    test("List a = [1,2,3]; switch(a) { [1,int i,3] => i; [1,3] => 7 }", 2);
    test("List a = [1,2,3]; switch(a) { [1,def i,3] => i; [1,3] => 7 }", 2);
    test("List a = [1,2,2]; switch(a) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("List a = [1,2,2]; switch(a) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List a = [1,2,2]; switch(a) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("List a = [1,'abc','abc']; switch(a) { [1,int i,_] => i; default => 7 }", 7);
    test("List a = [1,2,'abc']; switch(a) { [1,int i,i] => i; default => 7 }", 7);
    testError("List a = [1,2,2]; switch(a) { [1,int i,int i] => i; [1,4,3] => 7 }", "'i' already declared");
    test("def a = [1,2,3]; switch(a) { i => i }", Utils.listOf(1,2,3));
    test("def a = [1,2,3]; switch(a) { int i => i; default => 5 }", 5);
    test("def a = [1,2,3]; switch(a) { List i => i }", Utils.listOf(1,2,3));
    test("def a = [1,2,3]; switch(a) { def i => i }", Utils.listOf(1,2,3));
    test("def a = [1,2,3]; switch(a.size()) { int i => i }", 3);
    test("def a = [1,2,3]; switch(a.size()) { long i => i; int j => j*j }", 9);
    test("def a = [1,2,3]; switch(a) { [1,i,3] => i; [1,3] => 7 }", 2);
    test("def a = [1,2,3]; switch(a) { [1,int i,3] => i; [1,3] => 7 }", 2);
    test("def a = [1,2,2]; switch(a) { [1,i,i] => i; [1,3] => 7 }", 2);
    test("def a = [1,2,2]; switch(a) { [1,int i,i] => i; [1,3] => 7 }", 2);
    test("def a = [1,2,2]; switch(a) { [1,int i,i] => i; [1,3] => 7 }", 2);
    test("def a = [1,'abc','abc']; switch(a) { [1,int i,_] => i; default => 7 }", 7);
    test("def a = [1,2,'abc']; switch(a) { [1,int i,i] => i; default => 7 }", 7);
    testError("def a = [1,2,2]; switch(a) { [1,int i,int i] => i; [1,4,3] => 7 }", "'i' already declared");
    test("def a = [1,2,3,2]; switch(a) { [_,z,_,z] => z }", 2);
    test("def a = [1,2,3,[4,5,[2,7]]]; switch(a) { [_,x,_,[*,[x,y]]] => y }", 7);
    test("def a = [1,2,3,[4,5,[2,7]]]; switch(a) { [_,x,_,[*,[x,int y]]] => y }", 7);
    test("def a = [1,[2,[3,4],2],3,[4,5,[2,[3,4]]]]; switch(a) { [_,[x,List y,x],_,[*,[x,y]]] => y }", Utils.listOf(3,4));
    test("def a = [1,[2,[3,4],2],3,[4,5,[2,[3,4]]]]; switch(a) { [_,[x,y,x],_,[*,[x,y]]] => y }", Utils.listOf(3,4));
    test("List a = ['aa','bb','cc']; switch(a) { List i => i }", Utils.listOf("aa","bb","cc"));
    test("List a = ['aa','bb','cc']; switch(a) { def i => i }", Utils.listOf("aa","bb","cc"));
    test("List a = ['aa','bb','cc']; switch(a.size()) { int i => i }", 3);
    testError("List a = ['aa','bb','cc']; switch(a.size()) { long i => i }", "can never be long");
    test("List a = ['aa','bb','cc']; switch(a) { ['aa',i,'cc'] => i; [4,'cc'] => 7 }", "bb");
    test("List a = ['aa','bb','cc']; switch(a) { ['aa',String i,'cc'] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("List a = ['aa','bb','cc']; switch(a) { ['aa',def i,'cc'] => i; ['aa','cc'] => 7 }", "bb");
    test("List a = ['aa','bb','bb']; switch(a) { ['aa',i,i] => i; ['aa','cc'] => 7 }", "bb");
    test("List a = ['aa','bb','bb']; switch(a) { ['aa',String i,i] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("List a = ['aa','bb','bb']; switch(a) { ['aa',String i,i] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("List a = ['aa',2,'abc']; switch(a) { ['aa',String i,_] => i; default => 7 }", 7);
    test("List a = ['aa','bb','abc']; switch(a) { ['aa',String i,i] => i; default => 7 }", 7);
    testError("List a = ['aa','bb','bb']; switch(a) { ['aa',String i,String i] => i; ['aa',4,'cc'] => 7 }", "'i' already declared");
    test("List a = ['aa','bb','cc','bb']; switch(a) { [_,x,_,x] => x }", "bb");
    test("def a = ['aa','bb','cc']; switch(a) { i => i }", Utils.listOf("aa","bb","cc"));
    test("def a = ['aa','bb','cc']; switch(a) { String i => i; default => 5 }", 5);
    test("def a = ['aa','bb','cc']; switch(a) { List i => i }", Utils.listOf("aa","bb","cc"));
    test("def a = ['aa','bb','cc']; switch(a) { def i => i }", Utils.listOf("aa","bb","cc"));
    test("def a = ['aa','bb','cc']; switch(a.size()) { String i => i; int i => i }", 3);
    test("def a = ['aa','bb','cc']; switch(a.size()) { long i => i; String j => j; int i => i*i }", 9);
    test("def a = ['aa','bb','cc']; switch(a) { ['aa',i,'cc'] => i; ['aa','cc'] => 7 }", "bb");
    test("def a = ['aa','bb','cc']; switch(a) { ['aa',String i,'cc'] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("def a = ['aa','bb','bb']; switch(a) { ['aa',i,i] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("def a = ['aa','bb','bb']; switch(a) { ['aa',String i,i] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("def a = ['aa','bb','bb']; switch(a) { ['aa',String i,i] => i; ['aa',4,'cc'] => 7 }", "bb");
    test("def a = ['aa','abc','abc']; switch(a) { ['aa',int i,_] => i; default => 7 }", 7);
    test("def a = ['aa','bb','abc']; switch(a) { ['aa',String i,i] => i; default => 7 }", 7);
    test("def a = ['aa','bb','bb']; switch(a) { ['aa',String i,i] => i; default => 7 }", "bb");
    testError("def a = ['aa','bb','bb']; switch(a) { ['aa',String i,String i] => i; ['aa',4,'cc'] => 7 }", "'i' already declared");
    test("def a = ['aa','bb','cc','bb']; switch(a) { [_,x,_,x] => x }", "bb");
    test("def a = ['aa','bb','cc',[4,5,['bb',7]]]; switch(a) { [_,x,_,[*,[x,y]]] => y }", 7);
    test("def a = ['aa','bb','cc',[4,5,['bb',7]]]; switch(a) { [_,x,_,[*,[x,int y]]] => y }", 7);
    test("def a = ['aa',['bb',['cc',4],'bb'],'cc',[4,5,['bb',['cc',4]]]]; switch(a) { [_,[x,List y,x],_,[*,[x,y]]] => y }", Utils.listOf("cc",4));
    test("def a = ['aa',['bb',['cc',4],'bb'],'cc',[4,5,['bb',['cc',4]]]]; switch(a) { [_,[x,y,x],_,[*,[x,y]]] => y }", Utils.listOf("cc",4));
    test("List a = [1,2]; switch(a) { [z],[_,z] => z; default => 3 }", 2);
    test("List a = [1,2,3,2]; switch(a) { [z],[_,z,_,z] => z; default => 3 }", 2);
    testError("List a = [1,2,3,2]; def x = 1; switch(a) { [z],[_,z,(x+1-1),z] => z; default => 3 }", "expressions not supported");
  }

  @Test public void switchArrays() {
    test("def x = [1,2,3] as int[]; switch(x) { i => i }", new int[]{ 1,2,3 });
    test("def x = [1,2,3] as int[]; switch(x) { int i => i; default => 5 }", 5);
    test("def x = [1,2,3] as int[]; switch(x) { List i => i; _ => 7 }", 7);
    test("def x = [1,2,3] as int[]; switch(x) { List i => 4; int[] => 5; _ => 7 }", 5);
    test("def x = [1,2,3] as int[]; switch(x) { List i => 4; int[] i => i; _ => 7 }", new int[]{ 1,2,3 });
    test("def x = [1,2,3] as int[]; switch(x) { def i => i }", new int[]{ 1,2,3 });
    test("def x = [1,2,3] as int[]; switch(x.size()) { int i => i }", 3);
    test("def x = [1,2,3] as int[]; switch(x.size()) { long i => i; int j => j*j }", 9);
    test("def x = [1,2,3] as int[]; switch(x) { [1,i,3] => i; [1,3] => 7 }", 2);
    test("def x = [1,2,3] as int[]; switch(x) { [1,int i,3] => i; [1,3] => 7 }", 2);
    test("def x = [1,2,2] as int[]; switch(x) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,2] as int[]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("def x = [1,2,2] as int[]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("int[] x = [1,2,3] as int[]; switch(x) { i => i }", new int[]{ 1,2,3 });
    testError("int[] x = [1,2,3] as int[]; switch(x) { int i => i; default => 5 }", "not compatible");
    test("int[] x = [1,2,3] as int[]; switch(x) { int[] i => i }", new int[]{ 1,2,3 });
    test("int[] x = [1,2,3] as int[]; switch(x) { def i => i }", new int[]{ 1,2,3 });
    test("int[] x = [1,2,3] as int[]; switch(x.size()) { int i => i }", 3);
    testError("int[] x = [1,2,3] as int[]; switch(x.size()) { long i => i; int j => j*j }", "can never be long");
    test("int[] x = [1,2,3] as int[]; switch(x) { [1,i,3] => i; [1,3] => 7 }", 2);
    test("int[] x = [1,2,3] as int[]; switch(x) { [1,int i,3] => i; [1,3] => 7 }", 2);
    test("int[] x = [1,2,2] as int[]; switch(x) { [1,i,i] => i; [1,4,3] => 7 }", 2);
    test("int[] x = [1,2,2] as int[]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("int[] x = [1,2,2] as int[]; switch(x) { [1,int i,i] => i; [1,4,3] => 7 }", 2);
    test("int[][] x = [[1],[1,2,3],[4],[4]]; switch(x) { [_,j,[int i],[i]] => ((j as List) + i) as int[]; _ => 7 }", new int[]{ 1,2,3,4 });
    test("def x = [[1],[1,2,3],[4],[4]] as int[][]; switch(x) { [_,j,[int i],[i]] => ((j as List) + i) as int[]; [1,4,3] => 7 }", new int[]{ 1,2,3,4 });
    test("def x = [[1],[1,2,3],[4],[4]] as int[][]; switch(x) { [_,[_,j,_],[int i],[i]] => j+i; [1,4,3] => 7 }", 6);
    test("int[] x = [1,2,3]; switch (x) { [1,2],[1,2,4] => 1; [1,2,3] => 2 }", 2);
  }

  @Test public void switchOnClassTypes() {
    test("class X{}; X x = new X(); switch(x) { X => 1 }", 1);
    testError("class X{}; X x = new X(); switch(x) { X => 1; int => 2 }", "can never be int");
    testError("class X{}; X x = new X(); switch(x) { X => 1; def => 2 }", "unreachable switch case");
    testError("class X{}; X x = new X(); switch(x) { def => 2; X => 1 }",  "unreachable switch case");
    test("class X{}; def x = new X(); switch(x) { X => 1; int => 2 }", 1);
    test("class X{}; def x = new X(); switch(x) { X => 1; def => 2 }", 1);
    testError("class X{}; def x = new X(); switch(x) { def => 2; X => 1 }",  "unreachable switch case");
    test("class X{int i}; def x = new X(3); switch(x) { X xxx => xxx.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new Y(3); switch(x) { X xxx => xxx.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { X xxx => xxx.i; def => 2 }", 3);
    test("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { Y xxx => xxx.i; def => 2 }", 2);
    testError("class X{int i}; class Y extends X{}; def x = new X(3); switch(x) { X xxx => xxx.i; Y => 2 }", "unreachable switch case");
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
    test("int x = 1; x.map{ switch { 0,1,2 => x.size() }}", Utils.listOf(1));
  }

  @Test public void switchWithIfExpr() {
    test("def a = 7; switch (a) { 1,2,3 => it; 7 if true => 11 }", 11);
    test("def a = 7; switch (a) { 1,2,3 => it; 7 if false => 11; _ => 0 }", 0);
    test("def a = 7; switch (a) { 1 if it != 2,2 if it == 2,3 => it; 7 if it == 3 => 11; _ => 0 }", 0);
    test("def a = 7; switch (a) { 1 if it != 2,2 if it == 2,3 => it; 7 if it == 7 => 11; _ => 0 }", 11);
    test("def a = 7; switch (a) { 1 if it != 2,2 if it == 2,3 => it; 7 if it == sleep(0,7) => 11; _ => 0 }", 11);
    test("def a = 'abc'; switch (a) { /a(.*)/r if $1 == 'xx' => it + $1; /a(.*)/r if $1 == 'bc' => it + $1*2 }", "abcbcbc");
  }
}
