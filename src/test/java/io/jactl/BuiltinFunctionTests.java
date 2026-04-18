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
import io.jactl.runtime.Continuation;
import io.jactl.runtime.Functions;
import io.jactl.runtime.RuntimeError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.jactl.JactlType.*;
import static org.junit.jupiter.api.Assertions.*;

public class BuiltinFunctionTests extends BaseTest {

  @Test public void aliasedFunction() {
    Jactl.function()
         .name("testFunction")
         .alias("testFunction2")
         .impl(BuiltinFunctionTests.class, "testFunction")
         .register();

    test("testFunction()", 3);
    test("testFunction2()", 3);

    Functions.INSTANCE.deregisterFunction("testFunction");

    testError("testFunction()", "unknown");
    testError("testFunction2()", "unknown");
  }

  public static Object testFunctionData;
  public static int testFunction() { return 3; }

  @Test public void sleep() {
    test("sleep(0,1)", 1);
    test("sleep(1,1)", 1);
    test("sleep(0)", null);
    test("sleep(1)", null);
    test("sleep(0,null)", null);
    test("sleep(0,'abc')", "abc");
  }

  @Test public void filter() {
    test("[].filter{it>1}", Utils.listOf());
    test("[].filter()", Utils.listOf());
    test("[1,'',2].filter{it}", Utils.listOf(1,2));
    test("[1,'',2].filter(predicate:{it})", Utils.listOf(1,2));
    test("def f = [1,'',2].filter; f(predicate:{it})", Utils.listOf(1,2));
    testError("def f = [1,'',2].filter; f(closurex:{it})", "no such parameter");
    test("[1,'',2].filter()", Utils.listOf(1,2));
    test("[:].filter()", Utils.listOf());
    test("def x = []; x.filter{it>1}", Utils.listOf());
    test("def x = []; x.filter()", Utils.listOf());
    test("def x = [:]; x.filter()", Utils.listOf());
    testError("null.filter()", "null value");
    test("''.filter()", Utils.listOf());
    test("'abacad'.filter{it != 'a'}.join()", "bcd");
    test("def f = 'abacad'.filter; f{it != 'a'}.join()", "bcd");
    test("def x = 'abc'; x.filter().join()", "abc");
    testError("def x = null; x.filter()", "null value");
    test("[(byte)1,(byte)2,(byte)3].filter()", Utils.listOf((byte)1,(byte)2,(byte)3));
    test("[(byte)1,(byte)2,(byte)3].filter{it>1}", Utils.listOf((byte)2,(byte)3));
    test("[1,2,3].filter()", Utils.listOf(1,2,3));
    test("[1,2,3].filter{it>1}", Utils.listOf(2,3));
    test("[a:true,b:false,c:true].filter{it[1]}.map{it[0]}", Utils.listOf("a","c"));
    test("def f = [a:true,b:false,c:true].filter; f{it[1]}.map{it[0]}", Utils.listOf("a","c"));
    test("def x = [a:true,b:false,c:true]; x.filter{it[1]}.map{it[0]}", Utils.listOf("a","c"));
    testError("[1,2,3].filter('abc')", "cannot convert");
    testError("def f = [1,2,3].filter; f('abc')", "cannot be cast to function");
    test("[null,null,'P'].filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it}", Utils.listOf("P"));
  }

  @Test public void allMatch() {
    test("[].allMatch{it>1}", true);
    test("[].allMatch()", true);
    test("[1,'',2].allMatch{it}", false);
    test("[1,'',2].allMatch(predicate:{it})", false);
    test("def f = [1,'',2].allMatch; f(predicate:{it})", false);
    testError("def f = [1,'',2].allMatch; f(closurex:{it})", "no such parameter");
    test("[1,'',2].allMatch()", false);
    test("[:].allMatch()", true);
    test("def x = []; x.allMatch{it>1}", true);
    test("def x = []; x.allMatch()", true);
    test("def x = [:]; x.allMatch()", true);
    testError("null.allMatch()", "null value");
    test("''.allMatch()", true);
    test("'abacad'.allMatch{it != 'a'}", false);
    test("'abacad'.allMatch{it in 'abcd'}", true);
    test("def f = 'abacad'.allMatch; f{it != 'a'}", false);
    test("def x = 'abc'; x.allMatch()", true);
    testError("def x = null; x.allMatch()", "null value");
    test("[1,2,3].allMatch()", true);
    test("[1,2,3].allMatch{it>1}", false);
    test("[a:true,b:false,c:true].allMatch{it[1]}", false);
    test("[a:true,b:true,c:true].allMatch{it[1]}", true);
    test("def f = [a:true,b:false,c:true].allMatch; f{it[1]}", false);
    test("def f = [a:true,b:true,c:true].allMatch; f{it[1]}", true);
    testError("[1,2,3].allMatch('abc')", "cannot convert");
    testError("def f = [1,2,3].allMatch; f('abc')", "cannot be cast to function");
    test("[null,null,'P'].allMatch{it != ' '}", true);
    test("[null,null,'P'].map{sleep(0,it)}.allMatch{it != ' '}", true);
    test("[null,null,'P'].map{sleep(0,it)}.allMatch{it}", false);
  }

  @Test public void noneMatch() {
    test("[].noneMatch{it>1}", true);
    test("[].noneMatch()", true);
    test("[1,2].noneMatch{it}", false);
    test("[1,'',2].noneMatch{it}", false);
    test("['',null].noneMatch{it}", true);
    test("[false,'',null].noneMatch(predicate:{it})", true);
    test("def f = [false,'',null].noneMatch; f(predicate:{it})", true);
    testError("def f = [1,'',2].noneMatch; f(closurex:{it})", "no such parameter");
    test("[false,''].noneMatch()", true);
    test("[:].noneMatch()", true);
    test("def x = []; x.noneMatch{it>1}", true);
    test("def x = []; x.noneMatch()", true);
    test("def x = [:]; x.noneMatch()", true);
    testError("null.noneMatch()", "null value");
    test("''.noneMatch()", true);
    test("'abacad'.noneMatch{it != 'a'}", false);
    test("'aaa'.noneMatch{it != 'a'}", true);
    test("'abacad'.noneMatch{it in 'abcd'}", false);
    test("def f = 'bcd'.noneMatch; f{it == 'a'}", true);
    test("def x = 'abc'; x.noneMatch()", false);
    testError("def x = null; x.noneMatch()", "null value");
    test("[1,2,3].noneMatch()", false);
    test("[1,2,3].noneMatch{it>3}", true);
    test("[a:true,b:false,c:true].noneMatch{it[1]}", false);
    test("[a:true,b:true,c:true].noneMatch{!it[1]}", true);
    test("def f = [a:true,b:false,c:true].noneMatch; f{it[1]}", false);
    test("def f = [a:true,b:true,c:true].noneMatch; f{!it[1]}", true);
    testError("[1,2,3].noneMatch('abc')", "cannot convert");
    testError("def f = [1,2,3].noneMatch; f('abc')", "cannot be cast to function");
    test("[null,null,'P'].noneMatch{it != ' '}", false);
    test("[null,null,'P'].map{sleep(0,it)}.noneMatch{it != ' '}", false);
    test("[null,null,'P'].map{sleep(0,it)}.noneMatch{it}", false);
  }

  @Test public void anyMatch() {
    test("[].anyMatch{it>1}", false);
    test("[].anyMatch()", false);
    test("[1,2].anyMatch{it}", true);
    test("[1,'',2].anyMatch{it}", true);
    test("['',null].anyMatch{it}", false);
    test("[false,'',null].anyMatch(predicate:{it})", false);
    test("def f = [false,'',null].anyMatch; f(predicate:{it})", false);
    testError("def f = [1,'',2].anyMatch; f(closurex:{it})", "no such parameter");
    test("[false,''].anyMatch()", false);
    test("[:].anyMatch()", false);
    test("def x = []; x.anyMatch{it>1}", false);
    test("def x = []; x.anyMatch()", false);
    test("def x = [:]; x.anyMatch()", false);
    testError("null.anyMatch()", "null value");
    test("''.anyMatch()", false);
    test("'abacad'.anyMatch{it != 'a'}", true);
    test("'aaa'.anyMatch{it != 'a'}", false);
    test("'abacad'.anyMatch{it in 'abcd'}", true);
    test("def f = 'bcd'.anyMatch; f{it == 'a'}", false);
    test("def x = 'abc'; x.anyMatch()", true);
    testError("def x = null; x.anyMatch()", "null value");
    test("[1,2,3].anyMatch()", true);
    test("[1,2,3].anyMatch{it>3}", false);
    test("[a:true,b:false,c:true].anyMatch{it[1]}", true);

    test("[a:true,b:true,c:true].anyMatch{!it[1]}", false);
    test("def f = [a:true,b:false,c:true].anyMatch; f{it[1]}", true);
    test("def f = [a:true,b:true,c:true].anyMatch; f{!it[1]}", false);
    testError("[1,2,3].anyMatch('abc')", "cannot convert");
    testError("def f = [1,2,3].anyMatch; f('abc')", "cannot be cast to function");
    test("[null,null,'P'].anyMatch{it != ' '}", true);
    test("[null,null,'P'].map{sleep(0,it)}.anyMatch{it != ' '}", true);
    test("[null,null,'P'].map{sleep(0,it)}.anyMatch{it}", true);
  }

  @Test public void lines() {
    test("''.lines()", Utils.listOf(""));
    testError("[].lines()", "no such method");
    testError("def x = []; x.lines()", "no such method");
    test("' '.lines()", Utils.listOf(" "));
    test("'\\n'.lines()", Utils.listOf(""));
    test("'abc\\nxyz'.lines()", Utils.listOf("abc","xyz"));
    test("def x = ''; x.lines()", Utils.listOf(""));
    test("def x = ' '; x.lines()", Utils.listOf(" "));
    test("def x = '\\n'; x.lines()", Utils.listOf(""));
    test("def x = 'abc\\nxyz'; x.lines()", Utils.listOf("abc","xyz"));
    test("'abc\\n\\nxyz'.lines()", Utils.listOf("abc","","xyz"));
    test("'abc\\n\\nxyz\\n'.lines()", Utils.listOf("abc","","xyz"));
    test("'abc\\n\\nxyz\\n\\n'.lines()", Utils.listOf("abc","","xyz",""));
    test("'\\nabc\\n\\nxyz\\n\\n'.lines()", Utils.listOf("","abc","","xyz",""));
    test("'\\n\\nabc\\n\\nxyz\\n\\n'.lines()", Utils.listOf("","","abc","","xyz",""));
    test("def f = '\\n\\nabc\\n\\nxyz\\n\\n'.lines; f()", Utils.listOf("","","abc","","xyz",""));
  }

  @Test public void split() {
    test("'a:b:c'.split(/:/)", Utils.listOf("a","b","c"));
    test("'a:b:c'.split(regex:/:/)", Utils.listOf("a","b","c"));
    test("'a:b:c'.split(regex:/:/,modifiers:'i')", Utils.listOf("a","b","c"));
    test("def f = 'a:b:c'.split; f(regex:/:/,modifiers:'i')", Utils.listOf("a","b","c"));
    testError("def f = 'a:b:c'.split; f(regexx:/:/,modifiers:'i')", "no such parameter");
    test("'abc'.split()", Utils.listOf("abc"));
    test("'abc'.split(null)", Utils.listOf("abc"));
    testError("'abc'.split(1)", "cannot convert argument of type int");
    testError("'abc'.split(/a/,1)", "cannot convert argument of type int");
    testError("'abc'.split(/a/,'1')", "unexpected regex modifier");
    testError("'abc'.split(/a/,'f')", "unexpected regex modifier");
    testError("'abc'.split(/a/,'',1)", "too many arguments");
    test("'abc'.split('')", Utils.listOf("a","b","c"));
    test("'abc'.split(/./)", Utils.listOf("","","",""));
    test("'aXbXYcX'.split(/[A-Z]+/)", Utils.listOf("a","b","c",""));
    test("':aX:bXY:cX:'.split(/[A-Z]+/,'i')", Utils.listOf(":",":",":",":"));
    test("'a::b:\\nc'.split(/:./)", Utils.listOf("a","b:\nc"));
    test("'aX:bX\\nc'.split(/x./,'is')", Utils.listOf("a","b","c"));
    test("'aX:bX\\nc'.split(/x./,'si')", Utils.listOf("a","b","c"));
    test("'aX:bX\\nc'.split(/x$/,'si')", Utils.listOf("aX:bX\nc"));
    test("'aX:bX\\nc'.split(/x$/,'mi')", Utils.listOf("aX:b","\nc"));
    test("'aX:bX\\nc'.split(/x$./,'mi')", Utils.listOf("aX:bX\nc"));
    test("'aX:bX\\nc'.split(/x$./,'smi')", Utils.listOf("aX:b","c"));
    test("def f = 'aX:bX\\nc'.split; f(/x$./,'smi')", Utils.listOf("aX:b","c"));
    test("def x = 'aX:bX\\nc'; def f = x.split; f(/x$./,'smi')", Utils.listOf("aX:b","c"));
    test("def x = 'a:b:c'; x.split(/:/)", Utils.listOf("a","b","c"));
    test("def x = 'abc'; x.split()", Utils.listOf("abc"));
    test("def x = 'abc'; x.split('')", Utils.listOf("a","b","c"));
    test("def x = 'abc'; x.split(/./)", Utils.listOf("","","",""));
    test("def x = 'aXbXYcX'; x.split(/[A-Z]+/)", Utils.listOf("a","b","c",""));
    test("def x = 'aXbXYcX'; x.split(/[A-Z]+/).map{sleep(0,it)+sleep(0,'x')+sleep(0,it)}.join()", "axabxbcxcx");
    test("def x = 'aXbXYcX'; def f = x.split; f(/[A-Z]+/)", Utils.listOf("a","b","c",""));
    testError("[1,2,3].split(/[A-Z]+/)", "no such method");
    testError("'abc'.split(/[A-Z]+/,'n')", "unexpected regex modifier 'n'");
    testError("'abc'.split(/[A-Z]+/,'r')", "unexpected regex modifier 'r'");
  }

  @Test public void minMax() {
    test("[(byte)1,2,3].min{ it }", (byte)1);
    test("[(byte)1,(byte)2,(byte)3].min{ it }", (byte)1);
    test("[1,(byte)2,(byte)3].min{ it }", 1);
    test("[1,2,3].min{ it }", 1);
    test("[1,2,3].min()", 1);
    test("'zxyab'.min()", "a");
    test("def f = [1,2,3].min; f()", 1);
    test("[2L,3.5,1.0,4D].min()", "#1.0");
    test("['z','a','bbb'].min()", "a");
    test("[[1,2,3],[1],[2,3]].min{it.size()}", Utils.listOf(1));
    test("[[1,2,3],[1],[2,3]].min(closure:{it.size()})", Utils.listOf(1));
    test("def f = [[1,2,3],[1],[2,3]].min; f(closure:{it.size()})", Utils.listOf(1));
    testError("def f = [[1,2,3],[1],[2,3]].min; f(closurex:{it.size()})", "no such parameter");
    test("['123.5','244','124'].min{ it as double }", "123.5");
    test("['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.min{ it as double }", "123.5");
    test("def f = ['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.min; f{ it as double }", "123.5");
    test("def x = [1,2,3]; x.min()", 1);
    test("def x = 'zxyab'; x.min()", "a");
    test("def x = [1,2,3]; def f = x.min; f()", 1);
    test("def x = [2L,3.5,1.0,4D]; x.min()", "#1.0");
    test("def x = ['z','a','bbb']; x.min()", "a");
    test("def x = [[1,2,3],[1],[2,3]]; x.min{it.size()}", Utils.listOf(1));
    test("def x = ['123.5','244','124']; x.min{ it as double }", "123.5");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.min{ it as double }", "123.5");
    test("def x = ['123.5','244','124']; def f = x.map{sleep(0,it)+sleep(0,'')}.min; f{ it as double }", "123.5");
    test("((byte)1).min()", 0);
    test("1.min()", 0);

    test("[1,2,3].max()", 3);
    test("'zxyab'.max()", "z");
    test("def f = [1,2,3].max; f()", 3);
    test("[2L,3.5,1.0,4D].max()", 4D);
    test("['z','a','bbb'].max()", "z");
    test("[[1,2,3],[1],[2,3]].max{it.size()}", Utils.listOf(1,2,3));
    test("['123.5','244','124'].max{ it as double }", "244");
    test("['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.max{ it as double }", "244");
    test("def f = ['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.max; f{ it as double }", "244");
    test("def x = [1,2,3]; x.max()", 3);
    test("def x = 'zxyab'; x.max()", "z");
    test("def x = [1,2,3]; def f = x.max; f()", 3);
    test("def x = [2L,3.5,1.0,4D]; x.max()", 4D);
    test("def x = ['z','a','bbb']; x.max()", "z");
    test("def x = [[1,2,3],[1],[2,3]]; x.max{it.size()}", Utils.listOf(1,2,3));
    test("def x = ['123.5','244','124']; x.max{ it as double }", "244");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.max{ it as double }", "244");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.max(closure:{ it as double })", "244");
    test("def x = ['123.5','244','124']; def f = x.map{sleep(0,it)+sleep(0,'')}.max; f{ it as double }", "244");
    test("((byte)1).max()", 0);
    test("((byte)3).max()", 2);
    test("1.max()", 0);
    test("3.max()", 2);
  }

  @Test public void testSumAvg() {
    test("[(byte)-1,(byte)2,(byte)3].sum()", 255+2+3);
    test("[1,2,3].sum()", 6);
    test("[(byte)-1,(byte)-2,(byte)3,256].avg()", "#192");
    test("[1,2,3].avg()", "#2");
    test("[].sum()", 0);
    testError("[].avg()", "empty list for avg");
    testError("['abc'].avg()", "non-numeric element in list");
    testError("['abc'].sum()", "non-numeric element in list");
    test("[1.0,2,3].sum()", "#6.0");
    test("[1D,2,3].sum()", 6D);
    test("[1,2L,3].sum()", 6L);
    test("[1,2,3].avg()", "#2");
    test("[1,2].avg()", "#1.5");
    test("def x = [1,2,3]; x.sum()", 6);
    test("def x = [1,2,3]; x.avg()", "#2");
    test("def x = []; x.sum()", 0);
    testError("def x = []; x.avg()", "empty list for avg");
    testError("def x = ['abc']; x.avg()", "non-numeric element in list");
    testError("def x = ['abc']; x.sum()", "non-numeric element in list");
    test("def x = [1.0,2,3]; x.sum()", "#6.0");
    test("def x = [1D,2,3]; x.sum()", 6D);
    test("def x = [1,2L,3]; x.sum()", 6L);
    test("def x = [1,2L,3]; x.map{sleep(0,it)+sleep(0,it)}.sum()", 12L);
    test("def x = [1,2,3]; x.avg()", "#2");
    test("def x = [1,2,3]; x.map{sleep(0,it)+sleep(0,it)}.avg()", "#4");
    test("def x = [1,2]; x.avg()", "#1.5");
    test("def x = [1,2L,3]; def f = x.sum; f()", 6L);
    test("def x = [1,2,3]; def f = x.avg; f()", "#2");
    test("def x = 1; def y = 2; [x,y].sum()", 3);
    test("def f = { it + it }; def x = 1; def y = 2; [x,y].map(f).sum()", 6);
  }

  @Test public void groupBy() {
    testError("['a','b','a','c','b'].groupBy()", "missing mandatory argument");
    test("[].groupBy{ it }", Utils.mapOf());
    test("['a','b','a','c','b'].groupBy{ it }", Utils.mapOf("a",Utils.listOf("a","a"), "b",Utils.listOf("b","b"), "c", Utils.listOf("c")));
    test("['a','b','a','c','b'].map{ sleep(0,it) }.groupBy{ sleep(0,it) }", Utils.mapOf("a",Utils.listOf("a","a"), "b",Utils.listOf("b","b"), "c", Utils.listOf("c")));
    test("[1,2,3].groupBy{ it.toString() }.toString()", "['1':[1], '2':[2], '3':[3]]");
    test("([1,2,3] as int[]).groupBy{ it.toString() }.toString()", "['1':[1], '2':[2], '3':[3]]");
    test("'some text to use to count characters'.filter{ it != ' '}.groupBy{ it }.map{ k,v -> [k,v.size()] } as Map as String", "[s:3, o:4, m:1, e:4, t:6, x:1, u:2, c:3, n:1, h:1, a:2, r:2]");
    test("[[a:1,b:2],[a:2,b:3],[a:1,c:2]].groupBy{ it.a as String }.toString()", "['1':[[a:1, b:2], [a:1, c:2]], '2':[[a:2, b:3]]]");
    test("[[a:1,b:2],[a:2,b:3],[a:1,c:2]].groupBy{ it.a }.toString()", "[(1):[[a:1, b:2], [a:1, c:2]], (2):[[a:2, b:3]]]");
    test("[1,2,3].groupBy{ it }.toString()", "[(1):[1], (2):[2], (3):[3]]");
    test("[[1]].groupBy{ it }.toString()", "[([1]):[[1]]]");
    test("[[1],[2]].groupBy{ it }.toString()", "[([1]):[[1]], ([2]):[[2]]]");
    test("[[1],[2],[3]].groupBy{ it }.toString()", "[([1]):[[1]], ([2]):[[2]], ([3]):[[3]]]");
    test("[[1,2],[2,3],[3,4]].groupBy{ it }.toString()", "[([1, 2]):[[1, 2]], ([2, 3]):[[2, 3]], ([3, 4]):[[3, 4]]]");
    test("[[a:1,b:2],[a:2,b:3],[a:1,c:2]].groupBy{ it }.toString()", "[([a:1, b:2]):[[a:1, b:2]], ([a:2, b:3]):[[a:2, b:3]], ([a:1, c:2]):[[a:1, c:2]]]");
  }

  @Test public void listTranspose() {
    test("[].transpose()", Utils.listOf());
    testError("[{ -> 1 }].transpose()", "must be list");
    testError("[{ -> 1 }].map().transpose()", "must be list");
    testError("[1].transpose()", "must be list");
    testError("[1].map().transpose()", "must be list");
    testError("['abc'].transpose()", "must be list");
    testError("['abc'].map().transpose()", "must be list");
    test("[[1]].transpose()", Utils.listOf(Utils.listOf(1)));
    test("[[1],[2]].transpose()", Utils.listOf(Utils.listOf(1,2)));
    test("[[1],[]].transpose()", Utils.listOf(Utils.listOf(1,null)));
    test("[[1,2,3],[2]].transpose()", Utils.listOf(Utils.listOf(1,2),Utils.listOf(2,null),Utils.listOf(3,null)));
    test("[[1],[2]].transpose()", Utils.listOf(Utils.listOf(1,2)));
    test("[[1,2],[2]].transpose()", Utils.listOf(Utils.listOf(1,2),Utils.listOf(2,null)));
    test("[[1,2],[3,4],[5,6]].transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]]; x.transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]]; def f = x.transpose; f()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("int[][] x = [[1,2],[3,4],[5,6]]; x.transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("int[][] x = [[1,2],[3,4],[5,6]]; def f = x.transpose; f()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]] as int[][]; x.transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]] as int[][]; def f = x.transpose; f()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("Object[][] x = [[1,2],[3,4],[5,6]] as Object[][]; x.transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("Object[][] x = [[1,2],[3,4],[5,6]] as Object[][]; def f = x.transpose; f()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]] as Object[][]; x.transpose()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("def x = [[1,2],[3,4],[5,6]] as Object[][]; def f = x.transpose; f()", Utils.listOf(Utils.listOf(1,3,5),Utils.listOf(2,4,6)));
    test("[[1,2],[2,null]].transpose()", Utils.listOf(Utils.listOf(1,2),Utils.listOf(2,null)));
    test("[[null,2],[2,null]].transpose()", Utils.listOf(Utils.listOf(null,2),Utils.listOf(2,null)));
    test("[3.map{ sleep(0,[it,it]) }, 4.map{ sleep(0,[sleep(0,it),sleep(0,it)]) }].transpose().collect().toString()", "[[[0, 0], [0, 0]], [[1, 1], [1, 1]], [[2, 2], [2, 2]], [null, [3, 3]]]");
    test("[[a:1],[b:2]].transpose() as String", "[[['a', 1], ['b', 2]]]");
    test("def f = [[a:1],[b:2]].transpose; f() as String", "[[['a', 1], ['b', 2]]]");
    test("[[a:1,c:3],[b:2]].transpose() as String", "[[['a', 1], ['b', 2]], [['c', 3], null]]");
    test("[[a:1],[b:2]].transpose() == [[a:1].map(),[b:2].map()].transpose()", true);
    test("[[a:1],[b:2]].transpose() == [[a:1].map{sleep(0,it)},[b:2].map{sleep(0,it)}].transpose()", true);
    test("def f = [[a:1,aa:2].map{sleep(0,it)},[b:2,bb:3].map{sleep(0,it)},[c:3,cc:4].map()].transpose; f() == [[['a',1],['b',2],['c',3]],[['aa',2],['bb',3],['cc',4]]]", true);
    test("def f = [[a:1,aa:2].map{sleep(0,it)},[b:2,bb:3].map{sleep(0,it)},[c:3,cc:4].map()].transpose; f().transpose() == [[['a',1],['aa',2]],[['b',2],['bb',3]],[['c',3],['cc',4]]]", true);
    test("def x = [[a:1,aa:2].map{sleep(0,it)},[b:2,bb:3].map{sleep(0,it)},[c:3,cc:4].map()]; def f = x.transpose; f().transpose() == [[['a',1],['aa',2]],[['b',2],['bb',3]],[['c',3],['cc',4]]]", true);
    test("def f = [[a:1,aa:2].map{sleep(0,it)},[b:2,bb:3].map{sleep(0,it)},[c:3,cc:4].map()].transpose; f().transpose().transpose() == [[['a',1],['b',2],['c',3]],[['aa',2],['bb',3],['cc',4]]]", true);
    test("sleep(0,[[['a',1],['b',2],['c',3]],[['aa',2],['bb',3],['cc',4]]].map{ sleep(0,it) }.transpose()).transpose() == [[['a',1],['b',2],['c',3]],[['aa',2],['bb',3],['cc',4]]]", true);
  }

  @Test public void stringLength() {
    test("''.length()", 0);
    test("''.size()", 0);
    test("def x = ''; x.length()", 0);
    test("def x = ''; x.size()", 0);
    test("def x = ''; def f = x.length; f()", 0);
    test("def x = ''; def f = x.size; f()", 0);
    test("'abc'.length()", 3);
    test("'abc'.size()", 3);
    test("def x = 'abc'; x.length()", 3);
    test("def x = 'abc'; x.size()", 3);
    testError("1.length()", "no such method");
    testError("[1,2,3].length()", "no such method");
    testError("def x = 1; x.length()", "no such method");
    test("def i = 1; String x = 'xxx'; (x + i).size()", 4);
    globals.put("X", "xxx");
    test("def i = 1; X + i", "xxx1");
    test("def i = 1; (X + i).size()", 4);
  }

  @Test public void stringToUpperLowerCase() {
    test("'ABC'.toLowerCase()", "abc");
    test("'ABC'.toLowerCase(1)", "aBC");
    test("'ABC'.toLowerCase(4)", "abc");
    test("'ABC'.toLowerCase(0)", "ABC");
    test("'ABC'.toLowerCase(count:0)", "ABC");
    test("def f = 'ABC'.toLowerCase; f(count:0)", "ABC");
    testError("def f = 'ABC'.toLowerCase; f(countx:0)", "no such parameter");
    test("''.toLowerCase(0)", "");
    test("''.toLowerCase()", "");
    test("''.toLowerCase(1)", "");
    test("def x  = 'ABC'; x.toLowerCase()", "abc");
    test("def x  = 'ABC'; x.toLowerCase(1)", "aBC");
    test("def x  = 'ABC'; x.toLowerCase(4)", "abc");
    test("def x  = 'ABC'; x.toLowerCase(0)", "ABC");
    test("def x  = 'ABC'; def f = x.toLowerCase; f(2)", "abC");
    test("def x  = ''; x.toLowerCase(0)", "");
    test("def x  = ''; x.toLowerCase()", "");
    test("def x  = ''; x.toLowerCase(1)", "");
    testError("def x = []; x.toLowerCase()", "no such method");
    test("'AbC.D_eF'.toLowerCase()", "abc.d_ef");

    test("'abc'.toUpperCase()", "ABC");
    test("'abc'.toUpperCase(1)", "Abc");
    test("'abc'.toUpperCase(4)", "ABC");
    test("'abc'.toUpperCase(0)", "abc");
    test("'ABC'.toUpperCase(count:0)", "ABC");
    test("def f = 'ABC'.toUpperCase; f(count:0)", "ABC");
    testError("def f = 'ABC'.toUpperCase; f(countx:0)", "no such parameter");
    test("''.toUpperCase(0)", "");
    test("''.toUpperCase()", "");
    test("''.toUpperCase(1)", "");
    test("def x  = 'abc'; x.toUpperCase()", "ABC");
    test("def x  = 'abc'; x.toUpperCase(1)", "Abc");
    test("def x  = 'abc'; x.toUpperCase(4)", "ABC");
    test("def x  = 'abc'; x.toUpperCase(0)", "abc");
    test("def x  = 'abc'; def f = x.toUpperCase; f(2)", "ABc");
    test("def x  = ''; x.toUpperCase(0)", "");
    test("def x  = ''; x.toUpperCase()", "");
    test("def x  = ''; x.toUpperCase(1)", "");
    testError("def x = []; x.toUpperCase()", "no such method");
    test("'AbC.D_eF'.toUpperCase()", "ABC.D_EF");
  }

  @Test public void substring() {
    test("''.substring(0)", "");
    test("''.substring(0,0)", "");
    testError("''.substring(-1)", "string index out of range");
    testError("''.substring(1)", "string index out of range");
    test("'abc'.substring(0,0)", "");
    test("'abc'.substring(1,1)", "");
    test("'abc'.substring(0)", "abc");
    test("'abc'.substring(1)", "bc");
    test("'abc'.substring(2)", "c");
    test("'abc'.substring(3)", "");
    test("'abc'.substring(start:3)", "");
    test("def f = 'abc'.substring; f(start:3)", "");
    testError("def f = 'abc'.substring; f(start:3,end:4,xxx:3)", "no such parameter");
    testError("'abc'.substring(4)", "string index out of range");
    test("'abcdef'.substring(1,4)", "bcd");
    test("'abcdef'.substring(1,6)", "bcdef");
    test("'abcdef'.substring(start:1,end:6)", "bcdef");
    test("def f = 'abcdef'.substring; f(start:1,end:6)", "bcdef");
    testError("'abcdef'.substring(1,7)", "StringIndexOutOfBoundsException");
    testError("'abcdef'.substring(-7,6)", "StringIndexOutOfBoundsException");
    test("'abcdef'.substring(-1,6)", "f");
    test("'abcdef'.substring(0,6)", "abcdef");
    test("'abcdef'.substring(-4,-2)", "cd");
    test("'abcdef'.substring(0,1)", "a");
    test("'abcdef'.substring(5,6)", "f");
    test("\"a${'bc'}def\".substring(2,5)", "cde");
    test("/a${'bc'}def/.substring(2,5)", "cde");

    test("def x = ''; x.substring(0)", "");
    testError("def x = ''; x.substring(-1)", "string index out of range");
    testError("def x = ''; x.substring(1)", "string index out of range");
    test("def x = 'abc'; x.substring(0)", "abc");
    test("def x = 'abc'; x.substring(1)", "bc");
    test("def x = 'abc'; x.substring(2)", "c");
    test("def x = 'abc'; x.substring(3)", "");
    testError("def x = 'abc'; x.substring(4)", "string index out of range");
    test("def x = 'abcdef'; x.substring(1,4)", "bcd");
    test("def x = 'abcdef'; x.substring(1,6)", "bcdef");
    testError("def x = 'abcdef'; x.substring(1,7)", "StringIndexOutOfBoundsException");
    testError("def x = 'abcdef'; x.substring(-7,6)", "StringIndexOutOfBoundsException");
    test("def x = 'abcdef'; x.substring(-1,6)", "f");
    test("def x = 'abcdef'; x.substring(-4,-2)", "cd");
    test("def x = 'abcdef'; x.substring(0,6)", "abcdef");
    test("def x = 'abcdef'; x.substring(0,1)", "a");
    test("def x = 'abcdef'; x.substring(5,6)", "f");
    test("def x = \"a${'bc'}def\"; x.substring(2,5)", "cde");
    test("def x = /a${'bc'}def/; x.substring(2,5)", "cde");
    test("def x = /a${'bc'}def/; def f = x.substring; f(2,5)", "cde");
    test("def x = /a${'bc'}def/; def f = x.substring; f(start:2,end:5)", "cde");
    test("def x = /a${'bc'}def/; def f = x.substring; f(2)", "cdef");
    testError("def x = /a${'bc'}def/; def f = x.substring; f(2,7)", "StringIndexOutOfBounds");
    testError("'abc1234'.substring(-4,0)", "StringIndexOutOfBounds");
    testError("'abc1234'.substring(-4,1)", "StringIndexOutOfBounds");
  }

  @Test public void join() {
    test("[].join()", "");
    test("[].join('')", "");
    test("[].join('x')", "");
    test("[:].join()", "");
    test("[:].join('')", "");
    test("['x'].join()", "x");
    test("['x'].join(',')", "x");
    test("['x','y'].join(',')", "x,y");
    test("['x','y',1].join(', ')", "x, y, 1");
    test("[x:1,y:2].join(',')", "['x', 1],['y', 2]");
    test("[[x:1],[y:2]].join(', ')", "[x:1], [y:2]");
    test("def x = []; x.join()", "");
    test("def x = []; x.join('')", "");
    test("def x = []; x.join('x')", "");
    test("def x = [:]; x.join()", "");
    test("def x = [:]; x.join('')", "");
    test("def x = ['x']; x.join()", "x");
    test("def x = ['x']; x.join(',')", "x");
    test("def x = ['x','y']; x.join(',')", "x,y");
    test("byte[] x = [1,2,3]; x.join(',')", "1,2,3");
    test("byte[] x = [1,2,3] as byte[]; x.join(',')", "1,2,3");
    test("int[] x = [1,2,3] as int[]; x.join(',')", "1,2,3");
    test("byte[][] x = [[1],[2,3],[4]] as byte[][]; x.join(',')", "[1],[2, 3],[4]");
    test("int[][] x = [[1],[2,3],[4]] as int[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as int[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as int[][]; def f = x.join; f(',')", "[1],[2, 3],[4]");
    test("int[] x = [1,2,3] as int[]; def f = x.join; f(',')", "1,2,3");
    test("def x = [1,2,3] as int[]; x.join(',')", "1,2,3");
    test("def x = [1,2,3] as int[]; def f = x.join; f(',')", "1,2,3");
    test("long[] x = [1,2,3] as long[]; x.join(',')", "1,2,3");
    test("long[][] x = [[1],[2,3],[4]] as long[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as long[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as long[][]; def f = x.join; f(',')", "[1],[2, 3],[4]");
    test("long[] x = [1,2,3] as long[]; def f = x.join; f(',')", "1,2,3");
    test("def x = [1,2,3] as long[]; x.join(',')", "1,2,3");
    test("def x = [1,2,3] as long[]; def f = x.join; f(',')", "1,2,3");
    test("Object[] x = [1,2,3] as Object[]; x.join(',')", "1,2,3");
    test("Object[][] x = [[1],[2,3],[4]] as Object[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as Object[][]; x.join(',')", "[1],[2, 3],[4]");
    test("def x = [[1],[2,3],[4]] as Object[][]; def f = x.join; f(',')", "[1],[2, 3],[4]");
    test("Object[] x = [1,2,3] as Object[]; def f = x.join; f(',')", "1,2,3");
    test("def x = [1,2,3] as Object[]; x.join(',')", "1,2,3");
    test("def x = [1,2,3] as Object[]; def f = x.join; f(',')", "1,2,3");
    test("String[] x = [1,2,3] as String[]; x.join(',')", "1,2,3");
    test("String[][] x = [[1],[2,3],[4]] as String[][]; x.join(',')", "['1'],['2', '3'],['4']");
    test("def x = [[1],[2,3],[4]] as String[][]; x.join(',')", "['1'],['2', '3'],['4']");
    test("def x = [[1],[2,3],[4]] as String[][]; def f = x.join; f(',')", "['1'],['2', '3'],['4']");
    test("String[] x = [1,2,3] as String[]; def f = x.join; f(',')", "1,2,3");
    test("def x = [1,2,3] as String[]; x.join(',')", "1,2,3");
    test("def x = [1,2,3] as String[]; def f = x.join; f(',')", "1,2,3");
    test("double[] x = [1.0,2.0,3.0] as double[]; x.join(',')", "1.0,2.0,3.0");
    test("double[][] x = [[1],[2,3],[4]] as double[][]; x.join(',')", "[1.0],[2.0, 3.0],[4.0]");
    test("def x = [[1],[2,3],[4]] as double[][]; x.join(',')", "[1.0],[2.0, 3.0],[4.0]");
    test("def x = [[1],[2,3],[4]] as double[][]; def f = x.join; f(',')", "[1.0],[2.0, 3.0],[4.0]");
    test("double[] x = [1,2,3] as double[]; def f = x.join; f(',')", "1.0,2.0,3.0");
    test("def x = [1,2,3] as double[]; x.join(',')", "1.0,2.0,3.0");
    test("def x = [1,2,3] as double[]; def f = x.join; f(',')", "1.0,2.0,3.0");
    test("def x = ['x','y',1]; x.join(', ')", "x, y, 1");
    test("def x = [x:1,y:2]; x.join(',')", "['x', 1],['y', 2]");
    test("def x = [[x:1],[y:2]]; x.join(', ')", "[x:1], [y:2]");
    test("['x','y','1','2','a'].filter{ /[a-z]/r }.join(',')", "x,y,a");
    test("def x = ['x','y','1','2','a']; x.filter{ /[a-z]/r }.join(',')", "x,y,a");
    test("['a','b'].map{sleep(0,it)+sleep(0,it)}.join(':')", "aa:bb");
    test("['a','b'].map{sleep(0,it)+sleep(0,it)}.join(separator:':')", "aa:bb");
    test("def f = ['a','b'].map{sleep(0,it)+sleep(0,it)}.join; f(separator:':')", "aa:bb");
    testError("['a','b'].map{sleep(0,it)+sleep(0,it)}.join(separatorx:':')", "no such parameter");
    testError("def f = ['a','b'].map{sleep(0,it)+sleep(0,it)}.join; f(separatorx:':')", "no such parameter");
  }

  @Test public void asChar() {
    test("((byte)97).asChar()", "a");
    test("97.asChar()", "a");
    test("def f = 97.asChar; f()", "a");
    test("def x = 97; x.asChar()", "a");
    test("def x = 97; def f = x.asChar; f()", "a");
    test("int x = 97; def f = x.asChar; f()", "a");
    test("def x = ((int)'X').asChar(); x", "X");
    test("def f = ((int)'X').asChar; f()", "X");
    testError("'a'.asChar()", "no such method");
    testError("def x = 'a'; x.asChar()", "no such method");
    testError("1L.asChar()", "no such method");
    testError("def x = 1L; x.asChar()", "no such method");
  }

  @Test public void className() {
    test("((byte)1).className()", "byte");
    test("1.className()", "int");
    test("'abc'.className()", "String");
    test("new byte[10].className()", "byte[]");
    test("new int[10].className()", "int[]");
    test("new long[10].className()", "long[]");
    test("new double[10][].className()", "double[][]");
    test("new Object[10][].className()", "Object[][]");
    test("new String[10][].className()", "String[][]");
    test("new Decimal[10][].className()", "Decimal[][]");
    test("class X{}; def f = new X().className; f()", "X");
    test("class X{}; def x = new X(); def f = x.className; f()", "X");
    test("class X{}; X x = new X(); def f = x.className; f()", "X");
    test("class X { class Y{} }; new X.Y[10].className()", "X.Y[]");
    test("class X { class Y{} }; def x = new X.Y[10]; x.className()", "X.Y[]");
    test("class X { class Y{} }; def x = new X.Y[10]; def f = x.className; f()", "X.Y[]");
  }

  @Test public void size() {
    test("((byte)2).size()", 2);
    test("2.size()", 2);
    test("def x = 2; x.size()", 2);
    test("def x = 2; def f = x.size; f()", 2);
    test("List x; x.size()", 0);
    test("List x = []; x.size()", 0);
    test("List x = [1,2,3]; x.size()", 3);
    test("def x = []; x.size()", 0);
    test("def x = []; x?.size()", 0);
    test("def x; x?.size()", null);
    test("def x = [1,2,3]; x.size()", 3);
    test("Map x; x.size()", 0);
    test("Map x = [:]; x.size()", 0);
    test("Map x = [a:1,b:2]; x.size()", 2);
    test("Map x = [a:1,b:[c:3]]; x.b.size()", 1);
    testError("def x; x.size()", "null value");
    test("def x; def y; y ?= x.size(); y", null);
    test("def x = [:]; x.size()", 0);
    test("def x = [a:1,b:2]; x.size()", 2);
    test("def x = [a:1,b:2]; x?.size()", 2);
    test("def x = [a:1,b:[c:3]]; x.b.size()", 1);
    test("def x = [a:1,b:[1,2,3]]; x.b.size()", 3);
    test("def x = [a:1,b:[c:3]]; x.b.c.size()", 3);
    test("def x = [1,2,3]; def f = x.size; f()", 3);
    test("List x = [1,2,3]; def f = x.size; f()", 3);
    test("def x = [a:1,b:2,c:3]; def f = x.size; f()", 3);
    test("Map x = [a:1,b:2,c:3]; def f = x.size; f()", 3);
    test("Map x = [a:1,b:2,c:3]; def f = x.\"${'size'}\"; f()", 3);
    test("List x = [1,2,3]; x.'size'()", 3);
    testError("def x = [1]; x.sizeXXX()", "no such method");
    test("def x; def y; y = x?.size()?.size(); y", null);
    test("def x = 'size'; [1,2,3].\"$x\"()", 3);
    test("def x = 'size'; def y = [1,2,3]; y.\"$x\"()", 3);
  }

  @Test public void arraySize() {
    test("([1,2,3] as byte[]).size()", 3);
    test("def x = [1,2,3] as byte[]; x.size()", 3);
    test("def f = ([1,2,3] as byte[]).size; f()", 3);
    test("def f = ([1,2,3] as byte[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as byte[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as byte[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as byte[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as byte[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as byte[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as byte[][]; x.size()", 3);
    test("byte[][] x = [[1,2],[2],[3,4,5]] as byte[][]; x.size()", 3);
    test("byte[][] x = [[1,2],[2],[3,4,5]] as byte[][]; x[2].size()", 3);
    test("byte[][] x = [[1,2],[2],[3,4,5]] as byte[][]; def f = x[2].size; f()", 3);
    test("byte[][] x = [[1,2],[2],[3,4,5]] as byte[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as byte[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as byte[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as byte[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as byte[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as byte[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as byte[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as byte[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as int[]).size()", 3);
    test("def x = [1,2,3] as int[]; x.size()", 3);
    test("def f = ([1,2,3] as int[]).size; f()", 3);
    test("def f = ([1,2,3] as int[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as int[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as int[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as int[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as int[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as int[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as int[][]; x.size()", 3);
    test("int[][] x = [[1,2],[2],[3,4,5]] as int[][]; x.size()", 3);
    test("int[][] x = [[1,2],[2],[3,4,5]] as int[][]; x[2].size()", 3);
    test("int[][] x = [[1,2],[2],[3,4,5]] as int[][]; def f = x[2].size; f()", 3);
    test("int[][] x = [[1,2],[2],[3,4,5]] as int[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as int[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as int[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as int[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as int[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as int[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as int[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as int[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as long[]).size()", 3);
    test("def x = [1,2,3] as long[]; x.size()", 3);
    test("def f = ([1,2,3] as long[]).size; f()", 3);
    test("def f = ([1,2,3] as long[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as long[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as long[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as long[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as long[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as long[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as long[][]; x.size()", 3);
    test("long[][] x = [[1,2],[2],[3,4,5]] as long[][]; x.size()", 3);
    test("long[][] x = [[1,2],[2],[3,4,5]] as long[][]; x[2].size()", 3);
    test("long[][] x = [[1,2],[2],[3,4,5]] as long[][]; def f = x[2].size; f()", 3);
    test("long[][] x = [[1,2],[2],[3,4,5]] as long[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as long[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as long[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as long[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as long[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as long[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as long[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as long[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as double[]).size()", 3);
    test("def x = [1,2,3] as double[]; x.size()", 3);
    test("def f = ([1,2,3] as double[]).size; f()", 3);
    test("def f = ([1,2,3] as double[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as double[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as double[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as double[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as double[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as double[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as double[][]; x.size()", 3);
    test("double[][] x = [[1,2],[2],[3,4,5]] as double[][]; x.size()", 3);
    test("double[][] x = [[1,2],[2],[3,4,5]] as double[][]; x[2].size()", 3);
    test("double[][] x = [[1,2],[2],[3,4,5]] as double[][]; def f = x[2].size; f()", 3);
    test("double[][] x = [[1,2],[2],[3,4,5]] as double[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as double[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as double[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as double[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as double[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as double[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as double[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as double[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as Decimal[]).size()", 3);
    test("def x = [1,2,3] as Decimal[]; x.size()", 3);
    test("def f = ([1,2,3] as Decimal[]).size; f()", 3);
    test("def f = ([1,2,3] as Decimal[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as Decimal[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as Decimal[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as Decimal[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as Decimal[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as Decimal[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Decimal[][]; x.size()", 3);
    test("Decimal[][] x = [[1,2],[2],[3,4,5]] as Decimal[][]; x.size()", 3);
    test("Decimal[][] x = [[1,2],[2],[3,4,5]] as Decimal[][]; x[2].size()", 3);
    test("Decimal[][] x = [[1,2],[2],[3,4,5]] as Decimal[][]; def f = x[2].size; f()", 3);
    test("Decimal[][] x = [[1,2],[2],[3,4,5]] as Decimal[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Decimal[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as Decimal[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as Decimal[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as Decimal[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as Decimal[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Decimal[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as Decimal[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as String[]).size()", 3);
    test("def x = [1,2,3] as String[]; x.size()", 3);
    test("def f = ([1,2,3] as String[]).size; f()", 3);
    test("def f = ([1,2,3] as String[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as String[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as String[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as String[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as String[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as String[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as String[][]; x.size()", 3);
    test("String[][] x = [[1,2],[2],[3,4,5]] as String[][]; x.size()", 3);
    test("String[][] x = [[1,2],[2],[3,4,5]] as String[][]; x[2].size()", 3);
    test("String[][] x = [[1,2],[2],[3,4,5]] as String[][]; def f = x[2].size; f()", 3);
    test("String[][] x = [[1,2],[2],[3,4,5]] as String[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as String[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as String[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as String[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as String[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as String[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as String[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as String[][]; def f = x.sizexxx; f()", "field access not supported");
    test("([1,2,3] as Object[]).size()", 3);
    test("def x = [1,2,3] as Object[]; x.size()", 3);
    test("def f = ([1,2,3] as Object[]).size; f()", 3);
    test("def f = ([1,2,3] as Object[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as Object[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as Object[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as Object[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as Object[]; def f = x.sizexxx; f()", "field access not supported");
    test("([[1,2],[2],[3,4,5]] as Object[][]).size()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Object[][]; x.size()", 3);
    test("Object[][] x = [[1,2],[2],[3,4,5]] as Object[][]; x.size()", 3);
    test("Object[][] x = [[1,2],[2],[3,4,5]] as Object[][]; x[2].size()", 3);
    test("Object[][] x = [[1,2],[2],[3,4,5]] as Object[][]; def f = x[2].size; f()", 3);
    test("Object[][] x = [[1,2],[2],[3,4,5]] as Object[][]; def f = x[2].\"${'size'}\"; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Object[][]; x[2].size()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as Object[][]).size; f()", 3);
    test("def f = ([[1,2],[2],[3,4,5]] as Object[][]).\"${'size'}\"; f()", 3);
    testError("def f = ([[1,2],[2],[3,4,5]] as Object[][]).sizexxx; f()", "invalid object type");
    test("def x = [[1,2],[2],[3,4,5]] as Object[][]; def f = x.size; f()", 3);
    test("def x = [[1,2],[2],[3,4,5]] as Object[][]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [[1,2],[2],[3,4,5]] as Object[][]; def f = x.sizexxx; f()", "field access not supported");
  }

  @Test public void abs() {
    test("((byte)0).abs()", (byte)0);
    test("((byte)-0).abs()", (byte)0);
    test("((byte)-1).abs()", (byte)-1);
    test("((byte)1).abs()", (byte)1);
    test("0.abs()", 0);
    test("-0.abs()", 0);
    test("-1.abs()", 1);
    test("1.abs()", 1);
    test("1.0.abs()", "#1.0");
    test("-1.0.abs()", "#1.0");
    test("1.0D.abs()", 1.0D);
    test("-1.0D.abs()", 1.0D);
    test("1L.abs()", 1L);
    test("-1L.abs()", 1L);
    test("def f = -1L.abs; f()", 1L);
    testError("'abc'.abs()", "no such method");
    testError("def f = 'abc'.abs; f()", "no matching method");
    test("def x = -0; x.abs()", 0);
    test("def x = 0; x.abs()", 0);
    test("def x = -1; x.abs()", 1);
    test("def x = 1; x.abs()", 1);
    test("def x = 1.0; x.abs()", "#1.0");
    test("def x = -1.0; x.abs()", "#1.0");
    test("def x = 1.0D; x.abs()", 1.0D);
    test("def x = -1.0D; x.abs()", 1.0D);
    test("def x = 1L; x.abs()", 1L);
    test("def x = -1L; x.abs()", 1L);
    test("def x = -1L; def f = x.abs; f()", 1L);
    testError("def x = 'abc'; x.abs()", "no such method");
  }

  @Test public void listEach() {
    test("[].each{}", null);
    test("int sum = 0; [].each{sum++}; sum", 0);
    test("int sum = 0; [10].each{ sum += it }; sum", 10);
    test("int sum = 0; [10].each({ sum += it }); sum", 10);
    test("int sum = 0; [10].each(){ sum += it }; sum", 10);
    test("int sum = 0; [1,2,3,4].each{ x -> sum += x }; sum", 10);
    test("int sum = 0; def x = [1,2,3,4]; x.each{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x = [1,2,3,4]; x.each{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x = [1,2,3,4]; def f = x.each; f{ x -> sum += x }; sum", 10);
    test("int sum = 0; def x = [1,2,3,4]; def f = x.each; f{ x -> sum += x }; sum", 10);
    test("int sum = 0; List x; x.each{ x -> sum += x }; sum", 0);
    test("int sum = 0; [1,2,3].each{ x,y=2 -> sum += x+y }; sum", 12);
    test("int sum = 0; [1,2,3].each{ x=7,y=2 -> sum += x+y }; sum", 12);
    testError("int sum = 0; [1,2,3].each{ x,y -> sum += x+y }; sum", "missing mandatory argument");
    test("0.each{}", null);
    test("1.each{}", null);
    test("int sum = 0; 3.each{ x=7,y=2 -> sum += x+y }; sum", 9);
    test("''.each{}", null);
    test("'1'.each{}", null);
    test("def x=''; '1'.each{ x += it }; x", "1");
    test("def x=''; 'abc'.each{ x += it }; x", "abc");
    test("def x = 0; x.each{}", null);
    test("def x = 1; x.each{}", null);
    test("int sum = 0; def x = 3; x.each{ x=7,y=2 -> sum += x+y }; sum", 9);
    test("int sum = 0; def x = 3; x.each(action:{ x=7,y=2 -> sum += sleep(0,x)+sleep(0,y) }); sum", 9);
    test("int sum = 0; def x = 3; def f = x.each; f(action:{ x=7,y=2 -> sum += x+y }); sum", 9);
    testError("int sum = 0; def x = 3; x.each(action:{ x=7,y=2 -> sum += x+y },xxx:1); sum", "no such parameter");
    testError("int sum = 0; def x = 3; def f = x.each; f(action:{ x=7,y=2 -> sum += x+y },xxx:1); sum", "no such parameter");
    test("int sum = 0; def x = 3; def f = x.each; f{ x=7,y=2 -> sum += x+y }; sum", 9);
    test("int sum = 0; def x = 3.4; def f = x.each; f{ x=7,y=2 -> sum += x+y }; sum", 9);
    test("def x = 3\n[[1],[2]].size()", 2);
  }

  @Test public void mapEach() {
    test("[:].each{ x,y -> ; }", null);
    test("[:].each{}", null);
    test("[:].each{ x-> }", null);
    test("def result = ''; [a:1,b:2].each{ x,y -> result += \"[$x,$y]\" }; result", "[a,1][b,2]");
    test("def result = ''; [a:1,b:2].each{ result += \"[${it[0]},${it[1]}]\" }; result", "[a,1][b,2]");
    test("def result = ''; [a:1,b:2].each{ x -> result += \"[${x[0]},${x[1]}]\" }; result", "[a,1][b,2]");
    test("def result = 0; [a:1,b:2].each{ x -> result += x.size() }; result", 4);
    test("def result = ''; [a:1,b:2].each{ x -> result += x }; result", "['a', 1]['b', 2]");
    test("def result = 0; [a:1,b:2].each{ x -> x.each{ result++ } }; result", 4);
    test("def result = 0; [a:1,b:2].each(action:{ x -> def f = x.each; f(action:{ result++ }) }); result", 4);
    testError("def result = 0; [a:1,b:2].each(action:{ x -> def f = x.each; f(closurex:{ result++ }) }); result", "no such parameter");
    test("def result = 0; [a:1,b:2].each{ List x -> x.each{ result++ } }; result", 4);
    test("def result = ''; [a:1,b:2].each{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; [a:1,b:2].each{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; Map x = [a:1,b:2]; x.each{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; def x = [a:1,b:2]; x.each{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; Map x = [a:1,b:2]; def f = x.each; f{ List x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("def result = ''; def x = [a:1,b:2]; def f = x.each; f{ x -> def f = { \"${x[0]}-${x[1]}\" }; result += f() }; result", "a-1b-2");
    test("3.map{ it+1 }", Utils.listOf(1,2,3));
    test("(-3).map{ it+1 }", Utils.listOf());
    test("[a:'abc'].each{ it[1] =~ s/b/x/ }", null);
    test("[a:1].each{ it[1] = 2 }", null);
  }

  @Test public void listCollect() {
    testError("[].collect{}{}", "too many arguments");
    testError("def x = []; x.collect{}{}", "too many arguments");
    test("[].collect()", Utils.listOf());
    test("[].collect{}", Utils.listOf());
    test("[1,2,3].collect{}", Arrays.asList(null, null, null));
    test("[1,2,3].collect()", Utils.listOf(1,2,3));
    test("def x = [1,2,3]; x.collect{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.collect()", Utils.listOf(1,2,3));
    test("def x = []; x.collect{}", Utils.listOf());
    test("def x = []; x.collect()", Utils.listOf());
    test("List x = []; x.collect{}", Utils.listOf());
    testError("def x; x.collect{}", "null value");
    test("List x = [1,2,3,4]; x.collect{it*it}", Utils.listOf(1,4,9,16));
    test("List x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.collect{it*it}.collect; f{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{ x -> return {x*x}}.collect{it()}", Utils.listOf(1,4,9,16));
    test("def x = [1,2,3,4]; x.collect(mapper:{ x -> return {x*x}}).collect{it()}", Utils.listOf(1,4,9,16));
    testError("def x = [1,2,3,4]; x.collect(closurex:{ x -> return {x*x}}).collect{it()}", "no such parameter");
    test("def x = [1,2,3,4]; x.collect(mapper:{ x -> return {sleep(0,x)*sleep(0,x)}}).collect{it()}", Utils.listOf(1,4,9,16));
    test("def x = [1,2,3,4]; def f = x.collect; f(mapper:{ x -> return {x*x}}).collect{it()}", Utils.listOf(1,4,9,16));
  }

  @Test public void mapCollect() {
    test("[:].collect{}", Utils.listOf());
    test("[:].collect()", Utils.listOf());
    test("def x = 1; x.collect{it}", Utils.listOf(0));
    test("[a:1,b:2,c:3].collect{ [it[0]+it[0],it[1]+it[1]] }", Utils.listOf(Utils.listOf("aa",2),Utils.listOf("bb",4),Utils.listOf("cc",6)));
    test("[a:1,b:2,c:3].collect{ it[0]+it[0]+it[1]+it[1] }", Utils.listOf("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].collect{ it.collect{ it+it } }", Utils.listOf(Utils.listOf("aa",2),Utils.listOf("bb",4),Utils.listOf("cc",6)));
    test("[a:1,b:2,c:3].collect{ x,y -> x + y }", Utils.listOf("a1","b2","c3"));
    test("[a:1,b:2,c:3].collect(mapper:{ x,y -> x + y })", Utils.listOf("a1","b2","c3"));
  }

  @Test public void collectEntries() {
    test("[].collectEntries()", Utils.mapOf());
    test("[].collectEntries{}", Utils.mapOf());
    test("[:].collectEntries()", Utils.mapOf());
    test("[:].collectEntries{}", Utils.mapOf());
    test("[a:1].collectEntries()", Utils.mapOf("a", 1));
    test("[a:1].collectEntries{ it }", Utils.mapOf("a", 1));
    test("[a:1].collectEntries{ a,b -> [a,b] }", Utils.mapOf("a", 1));
    test("[a:1,b:2].collectEntries{ it }", Utils.mapOf("a", 1,"b",2));
    test("[a:1,b:2].collectEntries{ a,b -> [a,b] }", Utils.mapOf("a", 1,"b",2));
    testError("[a:1].collectEntries{}", "got null");
    test("[['a',1]].collectEntries()", Utils.mapOf("a",1));
    test("[['a',1],['a',2]].collectEntries()", Utils.mapOf("a",2));
    test("[a:1,b:2].collectEntries()", Utils.mapOf("a",1,"b",2));
    test("[1,2,3,4].collectEntries{ [it.toString()*it,it*it] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.map{ a,b -> [a,b] }.collectEntries{ a,b -> [a.toString()*a,b] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it.toString()*it,it*it] }.collectEntries()", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.collectEntries()", Utils.mapOf(1,1,2,4,3,9,4,16));
    test("def x = []; x.collectEntries()", Utils.mapOf());
    test("def x = []; x.collectEntries{}", Utils.mapOf());
    test("def x = [:]; x.collectEntries()", Utils.mapOf());
    test("def x = [:]; x.collectEntries{}", Utils.mapOf());
    test("def x = [a:1]; x.collectEntries()", Utils.mapOf("a", 1));
    test("def x = [a:1]; x.collectEntries{ it }", Utils.mapOf("a", 1));
    test("def x = [a:1]; x.collectEntries{ a,b -> [a,b] }", Utils.mapOf("a", 1));
    test("def x = [a:1,b:2]; x.collectEntries{ it }", Utils.mapOf("a", 1,"b",2));
    test("def x = [a:1,b:2]; x.collectEntries{ a,b -> [a,b] }", Utils.mapOf("a", 1,"b",2));
    testError("def x = [a:1]; x.collectEntries{}", "got null");
    test("def x = [['a',1]]; x.collectEntries()", Utils.mapOf("a",1));
    test("def x = [['a',1],['a',2]]; x.collectEntries()", Utils.mapOf("a",2));
    test("def x = [a:1,b:2]; x.collectEntries()", Utils.mapOf("a",1,"b",2));
    test("def x = [1,2,3,4]; x.collectEntries{ [it.toString()*it,it*it] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it.toString()*it,it*it] }.collectEntries()", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.collectEntries{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.collectEntries(mapper:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; def f = x.collectEntries; f(mapper:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
    testError("def x = [1,2,3,4]; def f = x.collectEntries; f(closurex:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", "no such parameter");
    test("def x = [1,2,3,4]; x.map{ [sleep(0,it),sleep(0,it)*sleep(0,it)] }.collectEntries{ a,b -> [sleep(0,a.toString())*sleep(0,a),sleep(0,b)] }", Utils.mapOf("1",1,"22",4,"333",9,"4444",16));
  }

  @Test public void mapWithIndex() {
    test("[null].map{it}", Collections.singletonList(null));
    ArrayList<Object> list = new ArrayList<>();
    list.add(null);
    list.add(0);
    test("[null].mapWithIndex{it}", Utils.listOf(list));
    test("[].mapWithIndex{ it[0] + it[1] }", Utils.listOf());
    test("def x = []; x.mapWithIndex{ it[0] + it[1] }", Utils.listOf());
    test("def x = [:]; x.mapWithIndex{ it[0] + it[1] }", Utils.listOf());
    test("[1,2,3].mapWithIndex{ it[0] + it[1] }.sum()", 9);
    test("[1,2,3].mapWithIndex()", Utils.listOf(Utils.listOf(1,0),Utils.listOf(2,1),Utils.listOf(3,2)));
    test("def x = [1,2,3]; x.mapWithIndex{ it[0] + it[1] }.sum()", 9);
    test("def x = [a:1,b:2]; x.mapWithIndex{ it,i -> it[0] + it[1] + i }", Utils.listOf("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f{ it,i -> it[0] + it[1] + i }", Utils.listOf("a10","b21"));
    test("def x = [a:1,b:2]; x.mapWithIndex{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i }", Utils.listOf("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i }", Utils.listOf("a10","b21"));
    test("def x = [a:1,b:2]; x.mapWithIndex(mapper:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", Utils.listOf("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f(mapper:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", Utils.listOf("a10","b21"));
    testError("def x = [a:1,b:2]; def f = x.mapWithIndex; f(closurex:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", "no such parameter");
  }

  @Test public void collectionMap() {
    test("2.map{it}", Utils.listOf(0,1));
    test("def x = 2; x.map{it}", Utils.listOf(0,1));
    test("def x = 2; def f = x.map; f{it}", Utils.listOf(0,1));
    test("[].map()", Utils.listOf());
    test("def f = [].map; f()", Utils.listOf());
    testError("[].map{}{}", "too many arguments");
    testError("def x = []; x.map{}{}", "too many arguments");
    test("[].map{}", Utils.listOf());
    test("[1,2,3].map{}", Arrays.asList(null, null, null));
    test("[1,2,3].map()", Utils.listOf(1,2,3));
    test("def x = [1,2,3]; x.map{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.map()", Utils.listOf(1,2,3));
    test("def x = []; x.map{}", Utils.listOf());
    test("def x = []; x.map()", Utils.listOf());
    test("List x = []; x.map{}", Utils.listOf());
    testError("def x; x.map{}", "null value");
    test("List x = [1,2,3,4]; x.map{it*it}", Utils.listOf(1,4,9,16));
    test("List x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.map{it*it}.map; f{ x -> x + x }", Utils.listOf(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{ x -> return {x*x}}.map{it()}", Utils.listOf(1,4,9,16));
    test("[:].map{}", Utils.listOf());
    test("[:].map()", Utils.listOf());
    test("def x = 1; x.map{}", Collections.singletonList(null));
    test("def x = 1; x.map{it}", Utils.listOf(0));
    test("def x = 1; x.map(mapper:{it})", Utils.listOf(0));
    test("def x = 1; def f = x.map; f(mapper:{sleep(0,it)+sleep(0,1)-sleep(0,1)})", Utils.listOf(0));
    testError("def x = 1; x.map(closurex:{it})", "no such parameter");
    testError("def x = 1; def f = x.map; f(closurex:{it})", "no such parameter");
    test("[a:1,b:2,c:3].map{ [it[0]+it[0],it[1]+it[1]] }", Utils.listOf(Utils.listOf("aa",2),Utils.listOf("bb",4),Utils.listOf("cc",6)));
    test("[a:1,b:2,c:3].map{ it[0]+it[0]+it[1]+it[1] }", Utils.listOf("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].map{ it.map{ it+it } }", Utils.listOf(Utils.listOf("aa",2),Utils.listOf("bb",4),Utils.listOf("cc",6)));
    test("[a:1,b:2,c:3].map{ x,y -> x + y }", Utils.listOf("a1","b2","c3"));
    test("[a:1,b:2,c:3].map(mapper:{ x,y -> x + y })", Utils.listOf("a1","b2","c3"));
    test("[a:1,b:2,c:3].map(mapper:{ x,y -> sleep(0,x) + sleep(0,y) })", Utils.listOf("a1","b2","c3"));
    testError("[a:1,b:2,c:3].map(closurex:{ x,y -> x + y })", "no such parameter");
    testError("def f = [a:1,b:2,c:3]; f.map(closurex:{ x,y -> x + y })", "no such parameter");
    test("def x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", Utils.listOf(2, 2));
    test("def x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", Utils.listOf(2, 2));
    test("def x = [1,2,3]; x.map{}.size()", 3);
    test("def x = [1,2,3]; x.map().size()", 3);
    test("var x = [1,2,3]; x.map().size()", 3);
    test("def x = [1,2,3]; x.map{it+it}.size()", 3);
    test("var x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.\"${'map'}\"{it+it+i++}; i", 3);
    test("var x = [1,2,3]; def i = 0; x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def y = x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; x.map{it+it+i++}; i", 3);
    test("def x = [1,2,3]; def i = 0; def f(x){i}; f(x.map{i++}); i", 3);
    test("def x = [1,2,3]; x.map{it*it}.collect{it+it}.map{it-1}", Utils.listOf(1, 7, 17));
    test("[1,2,3].map{it} instanceof List", true);
    test("def x = [1,2,3]; x.map{it} instanceof List", true);
    test("def f = [1,2,3].map; f{it+it}", Utils.listOf(2,4,6));
    test("def f = [1,2,3].map; f()", Utils.listOf(1,2,3));
    testError("def f = [1,2,3].map; f('x')", "cannot be cast");
    test("[1].map{ [it,it] }.map{ a,b -> a + b }", Utils.listOf(2));
    test("def x = [1,2,3]; def f = x.map{it*it}.map; f{it+it}", Utils.listOf(2,8,18));
    test("def f = [1,2,3].map; f{it+it}", Utils.listOf(2,4,6));
    test("def f = [1,2,3].map{it*it}.map; f{it+it}", Utils.listOf(2,8,18));
    test("[1,2,3].map{it*it}[1]", 4);
    test("'abcde'.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; x.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.map; f{it+it}.join()", "aabbccddee");
    testError("[1,2,3].map{ -> }", "too many arguments");
    test("[1,2,3].map{ int i -> i * i }", Utils.listOf(1,4,9));
    test("int[] a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2,4,6));
    test("long[] a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2L,4L,6L));
    test("double[] a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2.0,4.0,6.0));
    test("Decimal[] a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("String[] a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", Utils.listOf("11","22","33"));
    test("var a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2,4,6));
    test("var a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2L,4L,6L));
    test("var a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2.0,4.0,6.0));
    test("var a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("var a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", Utils.listOf("11","22","33"));
    test("def a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2,4,6));
    test("def a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2L,4L,6L));
    test("def a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2.0,4.0,6.0));
    test("def a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("def a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", Utils.listOf("11","22","33"));
    test("Object a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2,4,6));
    test("Object a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2L,4L,6L));
    test("Object a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(2.0,4.0,6.0));
    test("Object a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", Utils.listOf(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("Object a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", Utils.listOf("11","22","33"));
    test("int[][] a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", Utils.listOf(4,8,12));
    test("def a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", Utils.listOf(4,8,12));
    test("var a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", Utils.listOf(4,8,12));
  }
}
