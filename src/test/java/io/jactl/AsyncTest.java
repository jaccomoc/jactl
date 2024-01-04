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

import io.jactl.resolver.Resolver;
import io.jactl.runtime.RuntimeState;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncTest {

  int debugLevel = 0;

  private boolean autoCreateAsyncAllowed() {
    return JactlContext.create().build().autoCreateAsync();
  }

  private boolean isAsync(String source, JactlContext context) {
    Parser         parser = new Parser(new Tokeniser(source), context, Utils.DEFAULT_JACTL_PKG);
    Stmt.ClassDecl script   = parser.parseScript("AsyncScriptTest");
    Resolver       resolver = new Resolver(context, Utils.mapOf(), script.location);
    resolver.resolveScript(script);
    Analyser analyser = new Analyser(context);
    analyser.analyseClass(script);
    return script.scriptMain.declExpr.functionDescriptor.isAsync;
  }

  private void sync(String source, Object expected) {
    sync(source, "", expected);
  }
  private void sync(String source, String input, Object expected) {
    sync(Utils.listOf(), source, input, expected);
  }
  private void sync(List<String> classSources, String source, String input, Object expected) {
    runTest(classSources, source, input, expected, false);
  }

  private void async(String source, Object expected) {
    async(source,"", expected);
  }
  private void async(String source, String input, Object expected) {
    runTest(Utils.listOf(), source, input, expected, true);
  }

  private void runTest(List<String> classSources, String source, String input, Object expected, boolean isAsync) {
    JactlContext context = JactlContext.create().debug(debugLevel).build();
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String)((String) expected).substring(1)));
    }
    classSources.forEach(s -> Jactl.compileClass(s, context));
    assertEquals(isAsync, isAsync(source, context), isAsync ? "Was not async" : "Was not sync");
    RuntimeState.setInput(new BufferedReader(new StringReader(input)));
    JactlScript script = Jactl.compileScript(source, Utils.mapOf(), context);
    assertEquals(expected, script.runSync(Utils.mapOf()));
  }

  @Test public void asyncTests() {
    async("sleep(0,2)", 2);
    async("def f = sleep; f(0,2)", 2);
    sync("def f(x){x}; f(2)", 2);
    sync("def f(x){x}; def g = f; g(2)", 2);                       // g is final
    async("def f(x){x}; def g; g = f; g(2)", 2);                   // g is not final
    sync("def f(x){sleep(0,x)}; def g; g = f; 2", 2);              // f not invoked
    async("def f(x){sleep(0,x)}; def g = f; g = {it}; g(2)", 2);   // g not final
    async("def f(x){x}; def g = f; g = {it}; g(2)", 2);            // g not final
    sync("def g(x){x*x}; def f(x){g(x)*x}; f(2)", 8);
    sync("def g = {it*it}; def f(x){g(x)*x}; f(2)", 8);
    async("def g; g = {it*it}; def f(x){g(x)*x}; f(2)", 8);
    sync("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; h(2)", 8);
    async("def g = {it*it}; def h = { g(it)*it }; g = {sleep(0,it)*it}; h(2)", 8);
    async("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {sleep(0,it)*it}; h(2)", 8);
    async("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {it*it}; h(2)", 8);
    async("def g = {it*it}; def h = { def f(x){g(x)*x}; g={it*it}; f(it) }; h(2)", 8);
    sync("{it*it*it}(2)", 8);
    async("{it*it*sleep(0,it)}(2)", 8);
    async("def f(x=sleep(0,2)){x*x}; f(3)", 9);     // For the moment if wrapper is async then we treat func as async
    async("def f(x=sleep(0,2)){x*x}; f()", 4);
    async("def f(x){g(x)*g(x)}; def g(x){sleep(0,1)+sleep(0,x-1)}; f(2)", 4);
    sync("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){x+x}; f(2)", 5);
    async("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){sleep(0,x)+sleep(0,x)}; f(2)", 5);
    async("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(sleep(0,-23)+sleep(0,x+23))}; h(x) }; def j(x){x+x}; f(2)", 5);
    sync("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    async("def f(x){x<=1?1:g(sleep(0,x-23)+sleep(0,23))}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    async("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)+j(x)}; h(x) }; def j(x){f(sleep(0,x)-1)+f(sleep(0,x)-1)}; f(2)", 5);
    sync("def f = {it}; f(2)", 2);
    async("def f = {sleep(0,it)+sleep(0,it)}; f(2)", 4);
    async("def f(x){g(x)}; def g(x){sleep(0,x)+sleep(0,x)}; f(2)+f(3)", 10);
    async("def s = sleep; def f(x){x<=1?s(0,1):g(x)}; def g(x){s(0,x)+s(0,f(x-1))}; f(2)+f(3)", 9);
    async("def f(x){x<=1?1:g(x)}; def g(x){def s = sleep; s(0,x) + s(0,f(x-1))}; f(2)+f(3)", 9);
    sync("class X { Y y = null; }; class Y { int i = 1+2 }; def x = new X(); x.y.i = 5; x.y.i", 5);
    if (autoCreateAsyncAllowed()) {
      async("class X { Y y = null; }; class Y { int i = sleep(0,1) + sleep(0,2) }; def x = new X(); x.y.i = 5; x.y.i", 5);
      async("Map m; def x; m.a.b.c ?= x.a", null);
    }
    else {
      sync("Map m; def x; m.a.b.c ?= x.a", null);
    }

    // MAYBE TODO: analyse MethodCall/Call expressions to see if return value could be async closure/function
    //sync("class A { static def a() { int x = 1; return { x++ } } }; def f = A.a(); f() + f() + f()", 6);
  }

  @Test public void classes() {
    async("class X { int i; int j = sleep(0,i) }; Map m = [:]; m.a = new X(1); m.a.j", 1);
    sync("class X{ int i = 3 }; new X().i;", 3);
    async("class X{ int x; int i = sleep(0,3) }; new X(1).i;", 3);
    if (autoCreateAsyncAllowed()) {
      async("class X{ Y y = null }; class Y { int i = sleep(0,3); int j = 4; }; X x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
      async("class X{ Y y = null }; class Y { int i = sleep(0,3); int j = 4; }; def x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
      async("class X{ Y y = null }; class Y { int i = 3; int j = 4; }; def x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
      async("class X{ Y y = null }; class Y { int i = sleep(0,3); int j = 4; }; def x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
      async("class X { Y y = null }; class Y { int i = sleep(0,1) }; X x = new X(); x.y.i = 2", 2);
    }
    else {
      sync("class X{ Y y = null }; class Y { int i = 3; int j = 4; }; def x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
    }
    sync("class X{ Y y = null }; class Y { int i = 3; int j = 4; }; X x = new X(); x.y.j = 5; x.y.j + x.y.i", 8);
    sync("class X { Y y = null }; class Y { int i = 1 }; X x = new X(); x.y.i = 2", 2);

    // Call to f() is async since one day another class may override with async version
    async("class X { Y y = null; def f(x){ y = new Y(x) } }; class Y { int i }; X x = new X(); x.f(3); x.y.i", 3);

    // Call to f() is sync since it is final and doesn't call anything async
    sync("class X { Y y = null; final def f(x){ y = new Y(x) } }; class Y { int i }; X x = new X(); x.f(3); x.y.i", 3);

    async("class X { Y y = null; final def f(x){ y = new Y(sleep(0,x)) } }; class Y { int i }; X x = new X(); x.f(3); x.y.i", 3);

    // Async because X.f() is async since it is not final
    async("class X { def f(){1} }; class Y extends X { final def f(){ 3 } }; new Y().f()", 3);

    sync("class X { }; class Y extends X { final def f(){ 3 } }; new Y().f()", 3);
    async("class X { }; class Y extends X { final def f(){ 3 } }; new Y().\"${'f'}\"()", 3);
    async("class X { }; class Y extends X { final def f(){ 3 } }; def x = new Y(); x.f()", 3);

    async("class X { Y y = null; def f(x){ y = new Y(sleep(0,x)) } }; class Y { int i }; X x = new X(); x.f(3); x.y.i", 3);
    async("class X { Y y = null; def f(x){ y = sleep(0,new Y(x)) } }; class Y { int i }; X x = new X(); x.f(3); x.y.i", 3);
    async("class X { int i = sleep(0,1) }; new X().i", 1);
    sync("class X { int i = 1 }; new X().i", 1);
  }

  @Test public void classesFromJson() {
    sync("class X { int i = 1 }; X.fromJson('{\"i\":2}').i", 2);
    sync(Utils.listOf("class X { int i = 1 }", "class Y extends X { int j = 2; int k = 3 }"), "new Y(j:2).i", "", 1);
    async("class X { int i = sleep(0,1); long j = sleep(0,2) }; X.fromJson('{\"i\":4}').i", 4);
    async("class X { int i = sleep(0,1); long j = sleep(0,2) }; X.fromJson('{\"i\":4}').j", 2L);
    async("class X { int i = sleep(0,1); long j = sleep(0,2) }; class Y extends X {}; Y.fromJson('{\"i\":4}').j", 2L);
    sync("class X { int i = 1; long j = 2 }; class Y extends X { int k = 5 }; def y = Y.fromJson('{\"i\":4}'); y.i + y.k", 9);
    async("class X { int i = 1; long j = 2 }; class Y extends X { int k = sleep(0,5) }; def y = Y.fromJson('{\"i\":4}'); y.j + y.k", 7L);
    async("class X { int i = sleep(0,1); long j = sleep(0,2) }; class Y extends X { String s = sleep(0,'abc') }; Y.fromJson('{\"i\":4}').s", "abc");
    async("class X { int i = sleep(0,1); long j = sleep(0,2) }; class Y extends X { String s = sleep(0,'abc') }; Y.fromJson('{\"i\":4,\"s\":\"xyz\"}').s", "xyz");
    async("class Y { var d = (Decimal)sleep(0,2.0) }; class X { Y y }; def x = new X(null); x.y", null);
  }

  @Test public void builtinFunctions() {
    async("def s = sleep; sleep(0,1)", 1);
    sync("sprintf('%03d',1)", "001");
    sync("def s = sprintf; s('%03d',1)", "001");
    sync("[1,2,3].size()", 3);
    sync("[{it},{it*it}].size()", 2);
    sync("[1,2].map{it*it}", Utils.listOf(1,4));
    async("[{it},{it*it}].map{it(2)}", Utils.listOf(2,4));
    async("[{it},{it*it}].map{it(2)}.size()", 2);
    async("def f = [1,2,3].size; f()", 3);
    async("def f = [1,2,3].map{sleep(0,it)}.size; f()", 3);
    sync("[1,2,3,4,5].filter{it>2}.map{it+it}", Utils.listOf(6,8,10));
    async("[1,2,3,4,5].filter{sleep(0,it)>2}.map{it+it}", Utils.listOf(6,8,10));
    sync("[1,2,3].size()", 3);
    sync("[1,2,3].map{it}.size()", 3);
    async("[1,2,3].map{sleep(0,it)}.size()", 3);
    sync("[3,2,1].sort()", Utils.listOf(1,2,3));
    sync("[3,2,1].map{it}.sort()", Utils.listOf(1,2,3));
    async("[3,2,1].map{sleep(0,it)}.sort()", Utils.listOf(1,2,3));
    async("[3,2,1].map{it}.sort{a,b -> sleep(0,a) <=> sleep(0,b) }", Utils.listOf(1,2,3));
    sync("[1,2,3].join(':')", "1:2:3");
    sync("[1,2,3].map{it}.join(':')", "1:2:3");
    sync("[].collectEntries()", Utils.mapOf());
    sync("[3,6,4,2,3,5].sort().toString()", "[2, 3, 3, 4, 5, 6]");
    async("[1,2,3].map{sleep(0,it)}.collectEntries{[sleep(0,it.toString()),sleep(0,it)*sleep(0,it)]}", Utils.mapOf("1",1,"2",4,"3",9));
    async("[1,2,3].map{sleep(0,it)}.join(':')", "1:2:3");
    sync("[1,2,3].reduce([]){v,e -> v + e}", Utils.listOf(1,2,3));
    async("[1,2,3].map{sleep(0,it) + sleep(0,it)}.reduce([]){v,e -> v + e}", Utils.listOf(2,4,6));
    async("[1,2,3].reduce([]){v,e -> sleep(0,v) + sleep(0,e)}", Utils.listOf(1,2,3));
    sync("[1,2,3].sum()", 6);
    sync("[1,2,3].avg()", "#2");
    async("[1,2,3].map{sleep(0,it)}.sum()", 6);
    sync("[1,2,3].map{it}.sum()", 6);
    async("[1,2,3].map{it.size()}.sum()", 6);   // don't know type of it so have to assume async
    sync("[1,2,3].map{int it -> it.size()}.sum()", 6);
    async("[1,2,3].map{sleep(0,it)}.avg()", "#2");
    async("stream{nextLine()}", "1\n2\n3", Utils.listOf("1","2","3"));
    async("stream(nextLine)", "1\n2\n3", Utils.listOf("1","2","3"));
    async("def f = nextLine; stream(f)", "1\n2\n3", Utils.listOf("1","2","3"));
    async("def f = null; f = nextLine; stream(f)", "1\n2\n3", Utils.listOf("1","2","3"));
    sync("def i = 0; stream{ i++ < 3 ? i : null }", Utils.listOf(1,2,3));
    async("eval('1 + 2')", 3);
    async("eval('sleep(0,3) + sleep(0,2)') + sleep(0,-3) + sleep(0,2)", 4);
    sync("[[1,2,3],[1],[2,3]].min(closure:{List it -> it.size()})", Utils.listOf(1));
    async("[[1,2,3],[1],[2,3]].min(closure:{it.size()})", Utils.listOf(1));
  }
}
