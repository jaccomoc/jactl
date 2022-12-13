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

import static org.junit.jupiter.api.Assertions.*;

public class AsyncTest {

  int debugLevel = 0;

  private boolean isAsync(String source) {
    var context  = JacsalContext.create().debug(debugLevel).build();
    var parser   = new Parser(new Tokeniser(source), context, Utils.DEFAULT_JACSAL_PKG);
    var script   = parser.parse("AsyncScriptTest");
    var resolver = new Resolver(context, Map.of());
    resolver.resolveScript(script);
    var analyser = new Analyser();
    analyser.analyseScript(script);
    return script.scriptMain.declExpr.functionDescriptor.isAsync;
  }

  private void sync(String source, Object expected) {
    assertFalse(isAsync(source));
    var context  = JacsalContext.create().debug(debugLevel).build();
    assertEquals(expected, Compiler.run(source, context, Map.of()));
  }

  private void async(String source, Object expected) {
    assertTrue(isAsync(source));
    var context  = JacsalContext.create().debug(debugLevel).build();
    assertEquals(expected, Compiler.run(source, context, Map.of()));
  }

  @Test public void asyncTests() {
    async("sleep(0,2)", 2);
    async("def f = sleep; f(0,2)", 2);
    sync("def f(x){x}; f(2)", 2);
    sync("def f(x){x}; def g = f; g(2)", 2);                         // g is final
    async("def f(x){x}; def g; g = f; g(2)", 2);                     // g is not final
    sync("def f(x){sleep(0,x)}; def g; g = f; 2", 2);              // f not invoked
    async("def f(x){sleep(0,x)}; def g = f; g = {it}; g(2)", 2);   // g not final
    async("def f(x){x}; def g = f; g = {it}; g(2)", 2);              // g not final
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

    // MAYBE TODO: analyse MethodCall/Call expressions to see if return value could be async closure/function
    //sync("class A { static def a() { int x = 1; return { x++ } } }; def f = A.a(); f() + f() + f()", 6);
  }

  @Test public void builtinFunctions() {
    async("def s = sleep; sleep(0,1)", 1);
    sync("sprintf('%03d',1)", "001");
    sync("def s = sprintf; s('%03d',1)", "001");
    sync("[1,2,3].size()", 3);
    sync("[{it},{it*it}].size()", 2);
    sync("[1,2].map{it*it}", List.of(1,4));
    async("[{it},{it*it}].map{it(2)}", List.of(2,4));
    async("[{it},{it*it}].map{it(2)}.size()", 2);
    async("def f = [1,2,3].size; f()", 3);
    async("def f = [1,2,3].map{sleep(0,it)}.size; f()", 3);
    sync("[1,2,3,4,5].filter{it>2}.map{it+it}", List.of(6,8,10));
    async("[1,2,3,4,5].filter{sleep(0,it)>2}.map{it+it}", List.of(6,8,10));
    sync("[1,2,3].size()", 3);
    sync("[1,2,3].map{it}.size()", 3);
    async("[1,2,3].map{sleep(0,it)}.size()", 3);
    sync("[3,2,1].sort()", List.of(1,2,3));
    sync("[3,2,1].map{it}.sort()", List.of(1,2,3));
    async("[3,2,1].map{sleep(0,it)}.sort()", List.of(1,2,3));
    async("[3,2,1].map{it}.sort{a,b -> sleep(0,a) <=> sleep(0,b) }", List.of(1,2,3));
    sync("[1,2,3].join(':')", "1:2:3");
    sync("[1,2,3].map{it}.join(':')", "1:2:3");
//    async("[1,2,3].map{sleep(0,it)}.join(':')", "1:2:3");
  }
}
