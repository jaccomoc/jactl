/*
 * Copyright © 2022-2026 James Crawford
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

import io.jactl.compiler.Compiler;
import io.jactl.runtime.TimeoutError;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

public class CompilerTest3 extends BaseTest {
  @Test public void die() {
    testError("die \"ab${2+3}\"", "ab5");
    testError("die if true", "die if true");
    testError("die if true", "script death");
    testError("die", "script death");
    testError("die", "die");
  }

  @Test public void eval() {
    test("eval('1',[:])", 1);
    test("eval('1')", 1);
    test("eval('x',[x:null])", null);
    test("eval('x + (byte)1',[x:(byte)3])", (byte) 4);
    test("eval('x + 1',[x:3])", 4);
    test("eval('x + 1L',[x:3])", 4L);
    test("eval('x + 1',[x:3L])", 4L);
    test("eval('x + 1',[x:'3'])", "31");
    test("eval('x + 1',[x:3]) + eval('x+3',[x:3])", 10);
    test("eval('x x + 1',[x:3])", null);
    test("def vars = [x:3]; eval('x x + 1',vars); vars.'$error' =~ /unexpected token/i", true);
    test("def vars = [output:null]; eval('''def x = 'abc'; output = x.size()''',vars); vars.output", 3);
    test("eval('''result = 0; for(int i = 0; i < 5; i++) result += i; result''',[result:null])", 10);
    test("['[1,2]','[3]'].map{ eval(it,[:]) }", Utils.listOf(Utils.listOf(1, 2), Utils.listOf(3)));
    test("['[1,2]','[3]'].map{ sleep(0,it) }.map{ eval(it,[:]) }", Utils.listOf(Utils.listOf(1, 2), Utils.listOf(3)));
  }

  @Test public void evalWithAsync() {
    test("eval('sleep(0,1)')", 1);
    test("eval('sleep(0,1)+sleep(0,2)')+eval('sleep(0,3)+sleep(0,4)')", 10);
    test("eval('''['[1,2]','[3]'].map{ sleep(0,it) }.map{ eval(it,[:]) }''')", Utils.listOf(Utils.listOf(1, 2), Utils.listOf(3)));
    test("eval('''result = 0; for(int i = 0; i < 5; i++) result += sleep(0,i-1)+sleep(0,1); result''',[result:null])", 10);
    test("eval('''eval('sleep(0,1)+sleep(0,2)')+eval('sleep(0,3)+sleep(0,4)')''')", 10);
  }

  @Test public void asyncFunctions() {
    useAsyncDecorator = false;
    test("sleep(1,2)", 2);
    test("sleep(timeMs:1,data:2)", 2);
    test("def f = sleep; f(timeMs:1,data:2)", 2);
    testError("def f = sleep; f(timeMs:1,datax:2)", "no such parameter");
    testError("sleep('abc')", "cannot be cast to number");
    testError("def f = sleep; f('abc')", "cannot be cast");
    test("sleep(1,(byte)2)", (byte)2);
    test("sleep(1,2L)", 2L);
    test("sleep(1,2D)", 2D);
    test("sleep(1,2.0)", "#2.0");
    test("sleep(1,[])", Utils.listOf());
    test("sleep(1,[:])", Utils.mapOf());
    test("sleep(1,{it*it})(2)", 4);
    test("var x=1L; var y=1D; sleep(1,2)", 2);
    test("sleep(1,2) + sleep(1,3)", 5);
    test("List l=[1,2]; Map m=[a:1]; String s='asd'; int i=1; long L=1L; double d=1.0D; Decimal D=1.0; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("var l=[1,2]; var m=[a:1]; var s='asd'; var i=1; var L=1L; var d=1.0D; var D=1.0; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("def l=[1,2]; def m=[a:1]; def s='asd'; def i=1; def L=1L; def d=1.0D; def D=1.0; sleep(1,2) + l.size() + m.size() + i + L + d + D + sleep(1,3) + l.size() + m.size() + i + L + d + D", "#19.0");
    test("sleep(1,sleep(1,2))", 2);
    test("sleep(sleep(1,1),2)", 2);
    test("sleep(sleep(1,1),sleep(1,2))", 2);
    test("sleep(1,sleep(sleep(1,1),2)) + sleep(1,3)", 5);
    test("def y; y ?= sleep(1,2); y", 2);
    test("def y; y ?= sleep(1,null); y", null);
    test("def x; def y; y ?= sleep(1,x)?.size(); y", null);
    test("def x; def y; y ?= x?.(sleep(1,'si') + sleep(1,'ze'))()?.size(); y", null);
    test("def x = [1,2,3]; def y; y ?= x?.(sleep(1,'si') + sleep(1,'ze'))(); y", 3);
    test("def x = [1,2,3]; def y; y ?= x?.(sleep(sleep(1,1),'si') + sleep(sleep(1,1),'ze'))(); y", 3);
    test("def f(int x) { sleep(1,x) + sleep(1,x) }; f(1)", 2);
    test("def f(int x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2);
    test("def f(long x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2L);
    test("def f(double x = sleep(1,1)) { sleep(1,x) + sleep(1,x) }; f()", 2D);
    test("def f(int x = sleep(1,1) + sleep(1,2)) { sleep(1,x) + sleep(1,x) }; f()", 6);
    test("def f(int x = sleep(sleep(1,1),sleep(1,1))) { sleep(1,x) + sleep(1,x) }; f()", 2);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { x * y }; f()", 8);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { x * y }; f(sleep(1,3),sleep(1,5))", 15);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { sleep(1,x) * sleep(1,y) }; f()", 8);
    test("def f(x=8) { def g = {sleep(1,it)}; g(x) }; f()", 8);
    test("def f(x = sleep(1,2),y=sleep(1,x*x)) { def g = { sleep(1,it) + sleep(1,it) }; g(x)*g(y) }; f()", 32);
    test("\"${sleep(1,2) + sleep(1,3)}\"", "5");
    test("\"${sleep(1,'a')}:2\"", "a:2");
    test("\"${sleep(1,'a')}:${sleep(1,2)}\"", "a:2");
    test("\"${sleep(1,'a')}:${sleep(1,2) + sleep(1,3)}\"", "a:5");
    test("def x = 0; for (int i=sleep(1,1),j=sleep(1,2*i),k=0; k<sleep(1,5) && i < sleep(1,100); k=sleep(1,k)+1,i=i+sleep(1,1),j=sleep(1,j+1)) { x += sleep(1,k+i+j); }; x", 45);

    test("sleep(0,2)", 2);
    test("def f() { sleep(0,1) }; f()", 1);
    test("def f = sleep; f(0,2)", 2);
    test("def f(x){sleep(0,x)}; f(sleep(0,2))", 2);
    test("def f(x){sleep(0,x)}; def g = f; g(2)", 2);
    test("def f(x){sleep(0,x)}; def g; g = f; g(2)", 2);
    test("def f(x){sleep(0,x)}; def g; g = f; 2", 2);
    test("def f(x){sleep(0,x)}; def g = f; g = {it}; g(2)", 2);
    test("def f(x){x}; def g = f; g = {it}; g(2)", 2);
    test("def g(x){sleep(0,x)*sleep(0,x)}; def f(x){g(sleep(0,x)*sleep(0,1))*sleep(0,x)}; f(2)", 8);
    test("def g = {sleep(0,it)*sleep(0,it)}; def f(x,y){g(x)*sleep(0,x)*sleep(0,y)}; f(2,1)", 8);
    test("def g; g = {it*it}; def f(x){g(x)*x}; f(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; h(2)", 8);
    test("def g = {it*it}; def h = { g(it)*it }; g = {sleep(0,it)*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {sleep(0,it)*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; f(it) }; g = {it*it}; h(2)", 8);
    test("def g = {it*it}; def h = { def f(x){g(x)*x}; g={it*it}; f(it) }; h(2)", 8);
    test("{it*it*it}(2)", 8);
    test("{it*it*sleep(0,it)}(2)", 8);
    test("def f(x=sleep(0,2)){x*x}; f(3)", 9);
    test("def f(x=sleep(0,2)){x*x}; f()", 4);
    test("def f(x){g(x)*g(x)}; def g(x){sleep(0,1)+sleep(0,x-1)}; f(2)", 4);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){x+x}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){sleep(0,x)+sleep(0,x)}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(sleep(0,-23)+sleep(0,x+23))}; h(x) }; def j(x){x+x}; f(2)", 5);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    test("def f(x){x<=1?1:g(sleep(0,x-23)+sleep(0,23))}; def g(x){ def h(x){f(x-1)+j(x)}; h(x) }; def j(x){f(x-1)+f(x-1)}; f(2)", 3);
    test("def f(x){x<=1?1:g(x)}; def g(x){ def h(x){f(x-1)+j(x)+j(x)}; h(x) }; def j(x){f(sleep(0,x)-1)+f(sleep(0,x)-1)}; f(2)", 5);
    test("def f = {it}; f(2)", 2);
    test("def f = {sleep(0,it)+sleep(0,it)}; f(2)", 4);
    test("def f(x){g(x)}; def g(x){sleep(0,x)+sleep(0,x)}; f(2)+f(3)", 10);
    test("def s = sleep; def f(x){x<=1?s(0,1):g(x)}; def g(x){s(0,x)+s(0,f(x-1))}; f(2)+f(3)", 9);
    test("def f(x){x<=1?1:g(x)}; def g(x){def s = sleep; s(0,x) + s(0,f(x-1))}; f(2)+f(3)", 9);
    test("int i = 1; def f(){ return sleep(0,{ ++i }) }; def g = f(); g() + g()", 5);
    test("int i = sleep(0,-1)+sleep(0,2); def f(){ return sleep(0,{ sleep(0,++i - 1)+sleep(0,1) }) }; def g = f(); g() + g()", 5);
    test("byte i = 5; def f(byte x = sleep(0,(byte)1)+sleep(0,(byte)1), byte y=sleep(0,{x+i}())+sleep(0,{x+i+(byte)1}()), byte z=3) { sleep(0,x)+sleep(0,y)+sleep(0,z) }; f()", (byte)20);
    test("int i = 5; def f(int x = sleep(0,1)+sleep(0,1), long y=sleep(0,{x+i}())+sleep(0,{x+i+1}()), double z=3) { sleep(0,x)+sleep(0,y)+sleep(0,z) }; f()", 20D);
    test("def x = 1; while (true) { (false and break) or x = sleep(0,2); break }; x", 2);
    test("def x = 1; true and sleep(0, x = 2); x", 2);
    test("def x = 1; true and sleep(0, x ?= 2); x", 2);
    test("int x = 1; x += sleep(0,x) + sleep(0,x); x", 3);
    test("def x = 1; x += sleep(0,x) + sleep(0,x); x", 3);
    test("def f(int x, long y, String z, double d) { sleep(0,x++); sleep(0,y++); sleep(0,d++); z = sleep(0,z) * sleep(0,x); z + \": x=$x,y=$y,d=$d\" }; f(1,2,'x',3D)", "xx: x=2,y=3,d=4.0");
    test("int x = 1; long y = 2; double d = 3; sleep(0, d = sleep(0, y = sleep(0, x += sleep(0,x=3)) + x) + y) + x", 20L);
    test("def x = 1; x ?= sleep(0, null as int); x", 1);
    test("def f = null; f = { null as int }; def x = 1; x ?= sleep(0, 1) + f(); x", 1);
    test("def f = null; f = { sleep(0,null) as int }; def x = 1; x ?= f(); x", 1);
    test("def f(x) { x == 1 ? 3 : f(sleep(0,x.a)) }; f([a:[a:[a:1]]])", 3);
    testError("def f(x) { x == 1 ? 3 : f(sleep(0,x.a)) }; f([a:[a:[a:2]]])", "invalid parent object type");
  }

  @Test public void asyncFieldAccess() {
    test("Map m = [(sleep(0,'a')):sleep(0,1)]; m.\"${sleep(0,'a')}\"", 1);
    test("def m = [(sleep(0,'a')):sleep(0,1)]; m.\"${sleep(0,'a')}\"", 1);
  }

  @Test public void asyncErrorPropagation() {
    testError("def f(m) { m.y.z == 1 ? 1 : sleep(0,m.x) + sleep(0,f([x:m.x-1])) }; f([x:3])", "null value for parent");
    testError("def f(m) { m.x == ('x'+m.x) as Decimal ? 1 : sleep(0,m.x) + sleep(0,f([x:m.x-1])) }; f([x:3])", "string value is not a valid decimal");
  }

  @Test public void keywordAsIdentifier() {
    testError("int for = 3", "expecting identifier");
    testError("int for(x){x}", "expecting identifier");
  }

  @Test public void endOfLine() {
    test("1 + \n2", 3);
    test("def x =\n1 + \n2", 3);
    test("def x =\n1 + \n(2\n)", 3);
    test("def x =\n1 + (\n2)\n", 3);
    test("def x =\n1 + (\n2)\n", 3);
    test("[1,2,3].map{\n\nit}", Utils.listOf(1,2,3));
    test("['a','b'].map{\n\n\"\"\"\"$it=\" + $it\"\"\" }.\n\njoin(' + \", \" + ')", "\"a=\" + a + \", \" + \"b=\" + b");
    test("def f = {\nreturn }; f()", null);
    test("def f = {\nreturn\n}; f()", null);
    test("while (false) { 1 }\n2", 2);
    test("if (true) {\n if (true) \n1\n\n [1].map{ it }\n}\n", Utils.listOf(1));
    test("if (true) {\n if (true) {\n1\n}\n [1].map{ it }\n}\n", Utils.listOf(1));
    test("true or false\nand true", true);
    test("true \nor \nreturn", true);
    test("true \nor \nnot return", true);
    test("true or not\nreturn", true);
    test("true or not\nnot\nfalse\nand true", true);
    test("true &&\nfalse", false);
    test("true \n && \n false", false);
    test("true &&\n!\n!\n!\nfalse", true);
    test("def x=1; ++\nx", 2);
    test("def x=1; ++\n++\nx", 3);
    test("[a\n:\n1\n]", Utils.mapOf("a",1));
    test("1\n[1].map{ it }", Utils.listOf(1));
    test("'123'[2]", "3");
    test("'123'\n[2]", Utils.listOf(2));
    test("('123'\n[2])", "3");
    test("def x=[1,2,3]; x\n[2]", Utils.listOf(2));
    test("def x=[1,2,3]; def f={it}; f(x\n[2])", 3);
    test("def x = 1; ++\n++\nx\n++\n++\nx\nx", 3);
    testError("def x = 1; ++\n++\nx\n++\n++", "expected start of expression");
    test("[\n1\n,\n2\n,\n3\n]", Utils.listOf(1,2,3));
    test("def f(x\n,\ny\n)\n{\nx\n+\ny\n}\nf(1,2)", 3);
    testError("4\n/2", "unexpected end of file in string");
    test("4\n-3*2", -6);
    test("int\ni\n=\n3\n", 3);
    test("int\ni\ni\n=\n3\n", 3);
    test("int\ni\ni\n=\n3\ni", 3);
    test("int\ni =\n3\n", 3);
    test("int\ni =\n1,\nj =\n2,\nk\ni+j", 3);
    test("for\n(\nint\ni =\n4-\n3,\nj =\n4/\n2\n;\ni\n<\n10\n;\ni++\n)\n;", null);
    test("def x = [1,2]; if\n(\nx\n[\n0\n]\n)\n4", 4);
    test("def x = [1,2]; def i = 0; while\n(\nx\n[\n0\n]\n>\n10\n||\ni++\n<2\n)\nx[\n2\n]\n=\n7\nx[2]", 7);
    test("def (i\n,\nj\n)\n=\n[\n1\n,\n2\n]\ni+j\n", 3);
    test("(1 + 2)\n + 3\n", 6);
  }

  @Test public void eof() {
    testError("def ntimes(n,x) {\n for (int i = 0; i < n; i++) {\n", "unexpected end-of-file");
  }

  @Test public void globalsOptimisation() {
    globals.put("x", "abc");
    test("sleep(0,x) + sleep(0,'d')", "abcd");
    test("sleep(0,x) + sleep(0,'d') + sleep(0,x)", "abcdabc");
    globals.put("x", 123);
    test("x", 123);
    test("x + 1", 124);
    test("def f(n) { x = n }; x = 1; x + 'abc'.size() + x", 5);
    test("def f(n) { x = n }; x = 1; x + f(3) + x", 7);
    test("def f(n) { x = n }; x = 1; def y = x + f(3) + x; x = 3; y + x", 10);
    test("def f(n) { x = n }; x = 1; def y = ++x + x + f(3) + x; x = 3; y + x", 13);
    test("def f(n) { x = n }; x = 1; def y = (x += 1) + x + f(3) + x; x = 3; y += x; f(1); y + x", 14);
    test("def g(n) { x = n }; x = 1; def f = g; def y = (x += 1) + x + f(3); y += x; x = 3; y += x; f(1); y + x", 14);
    test("def g(n) { x = n }; x = 1; def f = g; def y = (x += 1) + x + f(3); y += x; x = 3; y += x; f(1); y + x", 14);
    test("def g(n) { if (false) { sleep(0) }; x = n }; x = 1; def f = g; def y = (x += 1) + x + f(3); y += x; x = 3; y += x; f(1); y + x", 14);
    test("sleep(0); x if true; sleep(0,x)", 123);
    test("def f() { def m = [:]; x ?= m.a.b() }; f()", null);
    test("def m = [:]; x ?= m.a.b()", null);
  }

  private interface InputOutputTest {
    void accept(String code, String input, Object expectedResult, String expectedOutput);
  }

  InputOutputTest replTest = (code, input, expectedResult, expectedOutput) -> {
    testCounter++;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    doTest(Utils.listOf(), code, input, output, true, true, false, false, expectedResult);
    assertEquals(expectedOutput, output.toString());

    output = new ByteArrayOutputStream();
    doTest(Utils.listOf(), code, input, output, false, true, false, false, expectedResult);
    assertEquals(expectedOutput, output.toString());

    output = new ByteArrayOutputStream();
    doTest(Utils.listOf(), code, input, output, true, true, true, false, expectedResult);
    assertEquals(expectedOutput, output.toString());
  };

  @Test public void beginEndBlocks() {
    // Test in repl mode only so that we can use global vars
    // Auto-creation ???
    //    test.accept("x = x + 1", 1, "");
    //    test.accept("++x", 1, "");
    //    test.accept("x.a = 1", 1, "");
    //    test.accept("x.a = 1; x.a", 1, "");

    replTest.accept("BEGIN{ x = 1 }; x", null, 1, "");
    replTest.accept("BEGIN{ x = [:] }; x.a = 1", null, 1, "");
    replTest.accept("BEGIN{ x = [:] }; x.a; BEGIN { x.a = 1 }", null, 1, "");
    replTest.accept("END { print 'x' }", null, true, "x");
    replTest.accept("END { x = 7 }; x = 2; BEGIN { x = 3 }", null, 7, "");
    replTest.accept("BEGIN { def x = 7 }; x = 2; END { x + x }", null, 4, "");
    //    replTest.accept("BEGIN { def x = 7 }; x = 2; END { println 'end1'; x + x }; BEGIN{ x += 3 }; END { println 'end2'; x + x + x }", null, 6, "end1\nend2\n");
  }

  @Test public void nextLine() {
    replTest.accept("nextLine() == null", null,true, "");
    replTest.accept("nextLine() == 'x'", "x",true, "");
    replTest.accept("nextLine() == 'x' && nextLine() == null", "x",true, "");
    replTest.accept("while (it = nextLine()) println it", "x",null, "x\n");
    replTest.accept("while ((it = nextLine()) != null) println it", "x\ny\n\nz\n",null, "x\ny\n\nz\n");
    replTest.accept("stream(nextLine).map{ eval(it,[:]) }", "[1,2]\n[3]\n", Utils.listOf(Utils.listOf(1,2), Utils.listOf(3)), "");
    replTest.accept("stream{sleep(0,nextLine())}.filter{ !/^$/r }.map{ eval(it,[:]) }.grouped(2).map{ it[0].size() + it[1].size() }.filter{ true }",
                    "[1,2]\n[3]\n\n", Utils.listOf(3), "");
    try {
      replTest.accept("def x = 0; while(nextLine() =~ /(\\d)/ng) { x+= $1 }; x", null, 0, "");
      fail("Expected error");
    }
    catch (AssertionError e) {
      // We expect error
    }
    replTest.accept("def x = 0; while(nextLine() =~ /(\\d)/ng) { x+= $1 }; x", "", 0, "");
    replTest.accept("def x = 0; while(nextLine() =~ /(\\d)/ng) { x+= $1 }; x", "1\n2\n3\n", 6L, "");
    replTest.accept("def x = 0; while(nextLine() =~ /(\\d)/ng) { x+= $1 }; x", "123\n666\n", 7L, "");
    replTest.accept("def x = 0; while(sleep(1,nextLine()) =~ /(\\d)/ng) { x+= $1 }; x", "123\n666\n", 7L, "");
  }

  @Test public void stream() {
    replTest.accept("stream{nextLine()}", "1\n4\n3\n", Utils.listOf("1","4","3"), "");
    replTest.accept("stream{nextLine()}.size()", "1\n4\n3\n", 3, "");
    replTest.accept("stream{nextLine() as int}", "1\n4\n3\n", Utils.listOf(1,4,3), "");
    replTest.accept("stream(nextLine).max{it as int}", "1\n4\n3\n", "4", "");
    replTest.accept("stream(closure:nextLine).max{it as int}", "1\n4\n3\n", "4", "");
    replTest.accept("def f = stream; f(nextLine).max{it as int}", "1\n4\n3\n", "4", "");
    replTest.accept("def f = stream; f(closure:nextLine).max{it as int}", "1\n4\n3\n", "4", "");
  }

  @Test public void groupedRepl() {
    replTest.accept("[''].map{sleep(0,it)}.filter{ !/^$/r }.map{ it.size() }.grouped(2).map{ it }.filter{ true }","\n", Utils.listOf(), "");
  }

  @Test public void globalVars() {
    replError("x", "unknown variable 'x'");
    replTest.accept("x = (byte)1", "", (byte)1, "");
    replTest.accept("x = 1", "", 1, "");
    replTest.accept("x = 1; x", "", 1, "");
    replTest.accept("x = x", "", null, "");
  }

  @Test public void fib() {
    final String fibDef = "int cnt; def fib(def x) { /*cnt++;*/ x <= 2 ? 1 : fib(x-1) + fib(x-2) }";
    final String fibInt = "int cnt; int fib(int x) { /*cnt++;*/ x <= 2 ? 1 : fib(x-1) + fib(x-2) }";
    final String fibDefSleep = "int cnt; def fib(def x) { /*cnt++;*/ x <= 2 ? 1 : sleep(0, fib(x-1)) + sleep(0, fib(x-2)) }";
    final String fibIntSleep = "int cnt; int fib(int x) { /*cnt++;*/ x <= 2 ? 1 : sleep(0, fib(x-1)) + sleep(0, fib(x-2)) }";

    BiConsumer<Integer,Object> runFibDef = (num, expected) -> doTest(fibDef + "; def result = fib(" + num + "); //println cnt; result", expected);
    BiConsumer<Integer,Object> runFibInt = (num, expected) -> doTest(fibInt + "; def result = fib(" + num + "); //println cnt; result", expected);
    BiConsumer<Integer,Object> runFibSleepDef = (num, expected) -> doTest(fibDef + "; def result = fib(" + num + "); //println cnt; result", expected, true);
    BiConsumer<Integer,Object> runFibSleepInt = (num, expected) -> doTest(fibInt + "; def result = fib(" + num + "); //println cnt; result", expected, true);
    //    BiConsumer<Integer,Object> runFibSleepDef = (num, expected) -> doTest(fibDefSleep + "; def result = fib(" + num + "); //println cnt; result", expected);
    //    BiConsumer<Integer,Object> runFibSleepInt = (num, expected) -> doTest(fibIntSleep + "; def result = fib(" + num + "); //println cnt; result", expected);

    //    debug=true;
    runFibDef.accept(40, 102334155);
    runFibInt.accept(40, 102334155);
    runFibDef.accept(20, 6765);
    runFibInt.accept(20, 6765);
    runFibSleepDef.accept(20, 6765);
    runFibSleepInt.accept(20, 6765);

    BiConsumer<Integer,Runnable> ntimes = (n,runnable) -> IntStream.range(0, n).forEach(i -> runnable.run());

    final int          ITERATIONS = 0;
    Map<String,Object> globals    = new HashMap<>();
    JactlScript        scriptDef  = compile(fibDef + "; fib(40)");
    JactlScript        scriptInt = compile(fibInt + "; fib(40)");
    JactlScript scriptSleepDef = compile(fibDefSleep + "; fib(20)");
    JactlScript scriptSleepInt = compile(fibIntSleep + "; fib(20)");
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      scriptDef.eval(globals);
      System.out.println("Def Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      scriptInt.eval(globals);
      System.out.println("Int Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      scriptSleepDef.eval(globals);
      System.out.println("sleep Def Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
    ntimes.accept(ITERATIONS, () -> {
      long start = System.currentTimeMillis();
      scriptSleepInt.eval(globals);
      System.out.println("sleep Int Duration=" + (System.currentTimeMillis() - start) + "ms");
    });
  }

  @Test public void globalsTest() {
    JactlContext jactlContext = JactlContext.create()
                                            .evaluateConstExprs(true)
                                            .classAccessToGlobals(true)
                                            .replMode(true)
                                            .debug(debugLevel)
                                            .build();

    Jactl.compileClass("class Z { int i }", jactlContext);
    Object z = Jactl.eval("new Z(2)", new HashMap(), jactlContext);

    Map<String,Object> globals = createGlobals();
    globals.put("z", z);
    globals.put("xxx", "xxx");
    BiConsumer<String,Object> runtest = (code,expected) -> {
      testCounter++;
      Object result = Compiler.eval(code, jactlContext, globals);
      assertEquals(expected, result);
    };

    runtest.accept("def i = 1; (xxx + i).size()", 4);
    runtest.accept("def x = (byte)1", (byte)1);
    runtest.accept("def x = 1", 1);
    runtest.accept("x", 1);
    runtest.accept("def f(x){x*x}; f(2)", 4);
    runtest.accept("f(3)", 9);
    runtest.accept("z instanceof Z", true);
    runtest.accept("z.i", 2);
    runtest.accept("xxx + xxx", "xxxxxx");
    globals.put("xxx", null);
    try {
      runtest.accept("xxx + xxx", "not used");
      fail("Expected error");
    }
    catch (JactlError e) {
      assertTrue(e.getMessage().contains("Left hand side of String concatenation is null"));
    }
    try {
      runtest.accept("xxx.size()", "not used");
      fail("Expected error");
    }
    catch (JactlError e) {
      assertTrue(e.getMessage().contains("null object"));
    }
    try {
      globals.put("x", null);
      runtest.accept("x + x", "not used");
      fail("Expected error");
    }
    catch (JactlError e) {
      assertTrue(e.getMessage().contains("Null operand"));
    }
  }

  @Test public void inifiniteLoopDetection() {
    //    JactlContext jactlContext = JactlContext.create().build();
    //
    //    for (int i = 0; i < 3; i++) {
    //      long start = System.nanoTime();
    //      long x     = (long) Jactl.eval("long x; def f() { for (i=0; i < 1000; i++) { x++ } }; def g() { for (i=0;i<1000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; x", new HashMap(), jactlContext);
    //      assertEquals(100100100L, x);
    //      System.out.println("Duration = " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
    //    }

    JactlContext jactlContext2 = JactlContext.create()
                                             .maxLoopIterations(100_100_100L)
                                             .build();

    //    for (int i = 0; i < 3; i++) {
    //      long start = System.nanoTime();
    //      long x     = (long) Jactl.eval("long x; def f() { for (i=0; i < 1000; i++) { x++ } }; def g() { for (i=0;i<1000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; x", new HashMap(), jactlContext2);
    //      assertEquals(100100100L, x);
    //      System.out.println("With loop limit: Duration = " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
    //    }

    try {
      Jactl.eval("long x; def f() { for (i=0; i < 1000; i++) { x++ } }; def g() { for (i=0;i<1000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; for (i=0;i<1;i++){}; x", new HashMap(), jactlContext2);
      fail("Should have thrown");
    }
    catch (TimeoutError e) {
      assertTrue(e.getMessage().contains("line 1, column 142"));
    }
    assertThrows(TimeoutError.class, () -> Jactl.eval("long x; def f() { for (i=0; i < 1000; i++) { x++ } }; def g() { for (i=0;i<1000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; for (i=0;i<1;i++){}; x", new HashMap(), jactlContext2));
    assertThrows(TimeoutError.class, () -> Jactl.eval("def f() { for (i=0; i < 100000; i++) {} }; def g() { for (i=0;i<100000;i++) { f() } }; for (i = 0; i < 100000; i++) { f() }", new HashMap(), jactlContext2));
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) {}", new HashMap(), jactlContext2));
    assertThrows(TimeoutError.class, () -> Jactl.eval("do {} until (false)", new HashMap(), jactlContext2));
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) { for (i = 0; i < 100000; i++) {} }", new HashMap(), jactlContext2));

    JactlContext jactlContext3 = JactlContext.create()
                                             .maxLoopIterations(1000L)
                                             .build();
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) { for (i = 0; i < 100000; i++) { sleep(0,0); } }", new HashMap(), jactlContext3));
    assertThrows(TimeoutError.class, () -> Jactl.eval("long x = 0; sleep(1, { while (true) { for (i = 0; i < 100; i++) { sleep(0, x++); } } }())", new HashMap(), jactlContext3));
  }

  @Test public void longRunningScriptTimeout() {
    JactlContext jactlContext = JactlContext.create()
                                            .maxExecutionTime(100)
                                            .build();

    assertThrows(TimeoutError.class, () -> Jactl.eval("long x; def f() { for (i=0; i < 100000; i++) { x++ } }; def g() { for (i=0;i<10000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; for (i=0;i<1;i++){}; x", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("def f() { for (i=0; i < 100000; i++) {} }; def g() { for (i=0;i<100000;i++) { f() } }; for (i = 0; i < 100000; i++) { f() }", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) {}", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("do {} until (false)", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) { for (i = 0; i < 100000; i++) {} }", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("while (true) { for (i = 0; i < 100000; i++) { sleep(0,0); } }", new HashMap(), jactlContext));
    assertThrows(TimeoutError.class, () -> Jactl.eval("long x = 0; sleep(1, { while (true) { for (i = 0; i < 100; i++) { sleep(0, x++); } } }())", new HashMap(), jactlContext));
  }

  @Test public void loopAndTimeoutDetection() {
    JactlContext jactlContext = JactlContext.create()
                                            .maxExecutionTime(100)
                                            .maxLoopIterations(1000)
                                            .build();

    BiConsumer<String,String> test = (script,err) -> {
      testCounter++;
      try {
        Jactl.eval(script, new HashMap(), jactlContext);
        fail("Expected TimeoutError");
      }
      catch (TimeoutError e) {
        assertTrue(e.getMessage().contains(err));
      }
    };

    test.accept("long x; def f() { for (i=0; i < 100000; i++) { x++ } }; def g() { for (i=0;i<10000;i++) { f(); x++ } }; for (i = 0; i < 100; i++) { g(); x++ }; for (i=0;i<1;i++){}; x",
                "Loop iterations");
    test.accept("def f() { for (i=0; i < 100000; i++) {} }; def g() { for (i=0;i<100000;i++) { f() } }; for (i = 0; i < 100000; i++) { f() }",
                "Loop iterations");
    test.accept("while (true) {}", "Loop iterations");
    test.accept("do {} until (false)", "Loop iterations");
    test.accept("while (true) { for (i = 0; i < 100; i++) { sleep(2,0); } }", "exceeded max time");
    test.accept("long x = 0; sleep(1, { while (true) { for (i = 0; i < 100; i++) { sleep(2, x++); } } }())", "exceeded max time");
  }

  @Test public void disablePrint() {
    JactlContext context = JactlContext.create()
                                       .disablePrint(true)
                                       .build();

    try {
      Jactl.eval("for (i=0;i<10;i++) {\n" +
                 "  println i\n", new HashMap(), context);
      fail("Expected compile error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("println has been disabled"));
    }
    Object result = Jactl.eval("def m = [x:1]; eval('print x',m); m", new HashMap(), context);
    String error  = (String)((Map) result).get("$error");
    assertTrue(error.contains("print has been disabled"));
  }

  @Test public void disableDie() {
    JactlContext context = JactlContext.create()
                                       .disableDie(true)
                                       .build();

    try {
      Jactl.eval("for (i=0;i<10;i++) {\n" +
                 "  die 'error:' + \n", new HashMap(), context);
      fail("Expected compile error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("die has been disabled"));
    }
    Object result = Jactl.eval("def m = [x:1]; eval('die x',m); m", new HashMap(), context);
    String error  = (String)((Map) result).get("$error");
    assertTrue(error.contains("die has been disabled"));
  }

  @Test public void disableEval() {
    JactlContext context = JactlContext.create()
                                       .disableEval(true)
                                       .build();

    try {
      Jactl.eval("for (i=0;i<10;i++) {\n" +
                 "  eval('println i', [i:i])\n", new HashMap(), context);
      fail("Expected compile error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("eval has been disabled"));
    }
  }

  @Test public void undeclaredGlobals() {
    testError("x", "unknown variable");
    allowUndeclaredGlobals = true;
    test("x", null);
    testError("class X { def f() { unknown } }; new X().f()", "unknown variable 'unknown'");
    testError(Utils.listOf("class X { def f() { unknown } }"), "new X().f()", "unknown variable 'unknown'");
    classAccessToGlobals = true;
    test("class X { def f() { unknown } }; new X().f()", null);
    JactlContext ctx = getJactlContext(false);
    JactlScript script = Jactl.compileScript("unknown", new HashMap(), ctx);
    assertEquals(null, script.eval(new HashMap<>()));
    assertEquals("xxx", script.eval(Utils.mapOf("unknown", "xxx")));
    assertEquals(123, script.eval(Utils.mapOf("unknown", 123)));
    Jactl.compileClass("class X { def f() { unknown } }", ctx);
    script = Jactl.compileScript("new X().f()", new HashMap<>(), ctx);
    assertEquals(null, script.eval(new HashMap<>()));
    assertEquals("xxx", script.eval(Utils.mapOf("unknown", "xxx")));
    assertEquals(123, script.eval(Utils.mapOf("unknown", 123)));
    globals.put("unknown", "abcdef");
    test(Utils.listOf("class X { def f() { unknown } }"), "new X().f()", "abcdef");
  }
  
}
