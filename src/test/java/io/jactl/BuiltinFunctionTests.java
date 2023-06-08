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

import io.jactl.runtime.Continuation;
import io.jactl.runtime.RuntimeError;
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

  @Test public void filter() {
    test("[].filter{it>1}", List.of());
    test("[].filter()", List.of());
    test("[1,'',2].filter{it}", List.of(1,2));
    test("[1,'',2].filter(predicate:{it})", List.of(1,2));
    test("def f = [1,'',2].filter; f(predicate:{it})", List.of(1,2));
    testError("def f = [1,'',2].filter; f(closurex:{it})", "no such parameter");
    test("[1,'',2].filter()", List.of(1,2));
    test("[:].filter()", List.of());
    test("def x = []; x.filter{it>1}", List.of());
    test("def x = []; x.filter()", List.of());
    test("def x = [:]; x.filter()", List.of());
    testError("null.filter()", "null value");
    test("''.filter()", List.of());
    test("'abacad'.filter{it != 'a'}.join()", "bcd");
    test("def f = 'abacad'.filter; f{it != 'a'}.join()", "bcd");
    test("def x = 'abc'; x.filter().join()", "abc");
    testError("def x = null; x.filter()", "null value");
    test("[1,2,3].filter()", List.of(1,2,3));
    test("[1,2,3].filter{it>1}", List.of(2,3));
    test("[a:true,b:false,c:true].filter{it[1]}.map{it[0]}", List.of("a","c"));
    test("def f = [a:true,b:false,c:true].filter; f{it[1]}.map{it[0]}", List.of("a","c"));
    test("def x = [a:true,b:false,c:true]; x.filter{it[1]}.map{it[0]}", List.of("a","c"));
    testError("[1,2,3].filter('abc')", "cannot convert");
    testError("def f = [1,2,3].filter; f('abc')", "cannot be cast to function");
    test("[null,null,'P'].filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it}", List.of("P"));
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
    test("''.lines()", List.of(""));
    testError("[].lines()", "no such method");
    testError("def x = []; x.lines()", "no such method");
    test("' '.lines()", List.of(" "));
    test("'\\n'.lines()", List.of(""));
    test("'abc\\nxyz'.lines()", List.of("abc","xyz"));
    test("def x = ''; x.lines()", List.of(""));
    test("def x = ' '; x.lines()", List.of(" "));
    test("def x = '\\n'; x.lines()", List.of(""));
    test("def x = 'abc\\nxyz'; x.lines()", List.of("abc","xyz"));
    test("'abc\\n\\nxyz'.lines()", List.of("abc","","xyz"));
    test("'abc\\n\\nxyz\\n'.lines()", List.of("abc","","xyz"));
    test("'abc\\n\\nxyz\\n\\n'.lines()", List.of("abc","","xyz",""));
    test("'\\nabc\\n\\nxyz\\n\\n'.lines()", List.of("","abc","","xyz",""));
    test("'\\n\\nabc\\n\\nxyz\\n\\n'.lines()", List.of("","","abc","","xyz",""));
    test("def f = '\\n\\nabc\\n\\nxyz\\n\\n'.lines; f()", List.of("","","abc","","xyz",""));
  }

  @Test public void split() {
    test("'a:b:c'.split(/:/)", List.of("a","b","c"));
    test("'a:b:c'.split(regex:/:/)", List.of("a","b","c"));
    test("'a:b:c'.split(regex:/:/,modifiers:'i')", List.of("a","b","c"));
    test("def f = 'a:b:c'.split; f(regex:/:/,modifiers:'i')", List.of("a","b","c"));
    testError("def f = 'a:b:c'.split; f(regexx:/:/,modifiers:'i')", "no such parameter");
    test("'abc'.split()", List.of("abc"));
    test("'abc'.split(null)", List.of("abc"));
    testError("'abc'.split(1)", "cannot convert argument of type int");
    testError("'abc'.split(/a/,1)", "cannot convert argument of type int");
    testError("'abc'.split(/a/,'1')", "unexpected regex modifier");
    testError("'abc'.split(/a/,'f')", "unexpected regex modifier");
    testError("'abc'.split(/a/,'',1)", "too many arguments");
    test("'abc'.split('')", List.of("a","b","c"));
    test("'abc'.split(/./)", List.of("","","",""));
    test("'aXbXYcX'.split(/[A-Z]+/)", List.of("a","b","c",""));
    test("':aX:bXY:cX:'.split(/[A-Z]+/,'i')", List.of(":",":",":",":"));
    test("'a::b:\\nc'.split(/:./)", List.of("a","b:\nc"));
    test("'aX:bX\\nc'.split(/x./,'is')", List.of("a","b","c"));
    test("'aX:bX\\nc'.split(/x./,'si')", List.of("a","b","c"));
    test("'aX:bX\\nc'.split(/x$/,'si')", List.of("aX:bX\nc"));
    test("'aX:bX\\nc'.split(/x$/,'mi')", List.of("aX:b","\nc"));
    test("'aX:bX\\nc'.split(/x$./,'mi')", List.of("aX:bX\nc"));
    test("'aX:bX\\nc'.split(/x$./,'smi')", List.of("aX:b","c"));
    test("def f = 'aX:bX\\nc'.split; f(/x$./,'smi')", List.of("aX:b","c"));
    test("def x = 'aX:bX\\nc'; def f = x.split; f(/x$./,'smi')", List.of("aX:b","c"));
    test("def x = 'a:b:c'; x.split(/:/)", List.of("a","b","c"));
    test("def x = 'abc'; x.split()", List.of("abc"));
    test("def x = 'abc'; x.split('')", List.of("a","b","c"));
    test("def x = 'abc'; x.split(/./)", List.of("","","",""));
    test("def x = 'aXbXYcX'; x.split(/[A-Z]+/)", List.of("a","b","c",""));
    test("def x = 'aXbXYcX'; x.split(/[A-Z]+/).map{sleep(0,it)+sleep(0,'x')+sleep(0,it)}.join()", "axabxbcxcx");
    test("def x = 'aXbXYcX'; def f = x.split; f(/[A-Z]+/)", List.of("a","b","c",""));
    testError("[1,2,3].split(/[A-Z]+/)", "no such method");
    testError("'abc'.split(/[A-Z]+/,'n')", "unexpected regex modifier 'n'");
    testError("'abc'.split(/[A-Z]+/,'r')", "unexpected regex modifier 'r'");
  }

  @Test public void minMax() {
    test("[1,2,3].min{ it }", 1);
    test("[1,2,3].min()", 1);
    test("'zxyab'.min()", "a");
    test("def f = [1,2,3].min; f()", 1);
    test("[2L,3.5,1.0,4D].min()", "#1.0");
    test("['z','a','bbb'].min()", "a");
    test("[[1,2,3],[1],[2,3]].min{it.size()}", List.of(1));
    test("[[1,2,3],[1],[2,3]].min(closure:{it.size()})", List.of(1));
    test("def f = [[1,2,3],[1],[2,3]].min; f(closure:{it.size()})", List.of(1));
    testError("def f = [[1,2,3],[1],[2,3]].min; f(closurex:{it.size()})", "no such parameter");
    test("['123.5','244','124'].min{ it as double }", "123.5");
    test("['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.min{ it as double }", "123.5");
    test("def f = ['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.min; f{ it as double }", "123.5");
    test("def x = [1,2,3]; x.min()", 1);
    test("def x = 'zxyab'; x.min()", "a");
    test("def x = [1,2,3]; def f = x.min; f()", 1);
    test("def x = [2L,3.5,1.0,4D]; x.min()", "#1.0");
    test("def x = ['z','a','bbb']; x.min()", "a");
    test("def x = [[1,2,3],[1],[2,3]]; x.min{it.size()}", List.of(1));
    test("def x = ['123.5','244','124']; x.min{ it as double }", "123.5");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.min{ it as double }", "123.5");
    test("def x = ['123.5','244','124']; def f = x.map{sleep(0,it)+sleep(0,'')}.min; f{ it as double }", "123.5");
    test("1.min()", 0);

    test("[1,2,3].max()", 3);
    test("'zxyab'.max()", "z");
    test("def f = [1,2,3].max; f()", 3);
    test("[2L,3.5,1.0,4D].max()", 4D);
    test("['z','a','bbb'].max()", "z");
    test("[[1,2,3],[1],[2,3]].max{it.size()}", List.of(1,2,3));
    test("['123.5','244','124'].max{ it as double }", "244");
    test("['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.max{ it as double }", "244");
    test("def f = ['123.5','244','124'].map{sleep(0,it)+sleep(0,'')}.max; f{ it as double }", "244");
    test("def x = [1,2,3]; x.max()", 3);
    test("def x = 'zxyab'; x.max()", "z");
    test("def x = [1,2,3]; def f = x.max; f()", 3);
    test("def x = [2L,3.5,1.0,4D]; x.max()", 4D);
    test("def x = ['z','a','bbb']; x.max()", "z");
    test("def x = [[1,2,3],[1],[2,3]]; x.max{it.size()}", List.of(1,2,3));
    test("def x = ['123.5','244','124']; x.max{ it as double }", "244");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.max{ it as double }", "244");
    test("def x = ['123.5','244','124']; x.map{sleep(0,it)+sleep(0,'')}.max(closure:{ it as double })", "244");
    test("def x = ['123.5','244','124']; def f = x.map{sleep(0,it)+sleep(0,'')}.max; f{ it as double }", "244");
    test("1.max()", 0);
    test("3.max()", 2);
  }

  @Test public void testSumAvg() {
    test("[1,2,3].sum()", 6);
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
    testError("''.substring(-1)", "string index out of range");
    testError("''.substring(1)", "string index out of range");
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
    test("int[] x = [1,2,3] as int[]; x.join(',')", "1,2,3");
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
    test("97.asChar()", "a");
    test("def x = 97; x.asChar()", "a");
    test("def x = 97; def f = x.asChar; f()", "a");
    test("def x = ((int)'X').asChar(); x", "X");
    test("def f = ((int)'X').asChar; f()", "X");
    testError("'a'.asChar()", "no such method");
    testError("def x = 'a'; x.asChar()", "no such method");
    testError("1L.asChar()", "no such method");
    testError("def x = 1L; x.asChar()", "no such method");
  }

  @Test public void className() {
    test("1.className()", "int");
    test("'abc'.className()", "String");
    test("new int[10].className()", "int[]");
    test("new long[10].className()", "long[]");
    test("new double[10][].className()", "double[][]");
    test("new Object[10][].className()", "Object[][]");
    test("new String[10][].className()", "String[][]");
    test("new Decimal[10][].className()", "Decimal[][]");
    test("class X { class Y{} }; new X.Y[10].className()", "X.Y[]");
    test("class X { class Y{} }; def x = new X.Y[10]; x.className()", "X.Y[]");
  }

  @Test public void size() {
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
    test("([1,2,3] as int[]).size()", 3);
    test("def x = [1,2,3] as int[]; x.size()", 3);
    test("def f = ([1,2,3] as int[]).size; f()", 3);
    test("def f = ([1,2,3] as int[]).\"${'size'}\"; f()", 3);
    testError("def f = ([1,2,3] as int[]).sizexxx; f()", "invalid object type");
    test("def x = [1,2,3] as int[]; def f = x.size; f()", 3);
    test("def x = [1,2,3] as int[]; def f = x.\"${'size'}\"; f()", 3);
    testError("def x = [1,2,3] as int[]; def f = x.sizexxx; f()", "invalid parent");
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
    testError("def x = [1,2,3] as long[]; def f = x.sizexxx; f()", "invalid parent");
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
    testError("def x = [1,2,3] as double[]; def f = x.sizexxx; f()", "invalid parent");
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
    test("3.map{ it+1 }", List.of(1,2,3));
    test("(-3).map{ it+1 }", List.of());
    test("[a:'abc'].each{ it[1] =~ s/b/x/ }", null);
    test("[a:1].each{ it[1] = 2 }", null);
  }

  @Test public void listCollect() {
    testError("[].collect{}{}", "too many arguments");
    testError("def x = []; x.collect{}{}", "too many arguments");
    test("[].collect()", List.of());
    test("[].collect{}", List.of());
    test("[1,2,3].collect{}", Arrays.asList(null, null, null));
    test("[1,2,3].collect()", List.of(1,2,3));
    test("def x = [1,2,3]; x.collect{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.collect()", List.of(1,2,3));
    test("def x = []; x.collect{}", List.of());
    test("def x = []; x.collect()", List.of());
    test("List x = []; x.collect{}", List.of());
    testError("def x; x.collect{}", "null value");
    test("List x = [1,2,3,4]; x.collect{it*it}", List.of(1,4,9,16));
    test("List x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{it*it}.collect{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.collect{it*it}.collect; f{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.collect{ x -> return {x*x}}.collect{it()}", List.of(1,4,9,16));
    test("def x = [1,2,3,4]; x.collect(mapper:{ x -> return {x*x}}).collect{it()}", List.of(1,4,9,16));
    testError("def x = [1,2,3,4]; x.collect(closurex:{ x -> return {x*x}}).collect{it()}", "no such parameter");
    test("def x = [1,2,3,4]; x.collect(mapper:{ x -> return {sleep(0,x)*sleep(0,x)}}).collect{it()}", List.of(1,4,9,16));
    test("def x = [1,2,3,4]; def f = x.collect; f(mapper:{ x -> return {x*x}}).collect{it()}", List.of(1,4,9,16));
  }

  @Test public void mapCollect() {
    test("[:].collect{}", List.of());
    test("[:].collect()", List.of());
    test("def x = 1; x.collect{it}", List.of(0));
    test("[a:1,b:2,c:3].collect{ [it[0]+it[0],it[1]+it[1]] }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].collect{ it[0]+it[0]+it[1]+it[1] }", List.of("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].collect{ it.collect{ it+it } }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].collect{ x,y -> x + y }", List.of("a1","b2","c3"));
    test("[a:1,b:2,c:3].collect(mapper:{ x,y -> x + y })", List.of("a1","b2","c3"));
  }

  @Test public void collectEntries() {
    test("[].collectEntries()", Map.of());
    test("[].collectEntries{}", Map.of());
    test("[:].collectEntries()", Map.of());
    test("[:].collectEntries{}", Map.of());
    test("[a:1].collectEntries()", Map.of("a", 1));
    test("[a:1].collectEntries{ it }", Map.of("a", 1));
    test("[a:1].collectEntries{ a,b -> [a,b] }", Map.of("a", 1));
    test("[a:1,b:2].collectEntries{ it }", Map.of("a", 1,"b",2));
    test("[a:1,b:2].collectEntries{ a,b -> [a,b] }", Map.of("a", 1,"b",2));
    testError("[a:1].collectEntries{}", "got null");
    test("[['a',1]].collectEntries()", Map.of("a",1));
    test("[['a',1],['a',2]].collectEntries()", Map.of("a",2));
    test("[a:1,b:2].collectEntries()", Map.of("a",1,"b",2));
    test("[1,2,3,4].collectEntries{ [it.toString()*it,it*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.map{ a,b -> [a,b] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("[1,2,3,4].map{ [it.toString()*it,it*it] }.collectEntries()", Map.of("1",1,"22",4,"333",9,"4444",16));
    testError("[1,2,3,4].map{ [it,it*it] }.collectEntries()", "expected string type for key");
    test("def x = []; x.collectEntries()", Map.of());
    test("def x = []; x.collectEntries{}", Map.of());
    test("def x = [:]; x.collectEntries()", Map.of());
    test("def x = [:]; x.collectEntries{}", Map.of());
    test("def x = [a:1]; x.collectEntries()", Map.of("a", 1));
    test("def x = [a:1]; x.collectEntries{ it }", Map.of("a", 1));
    test("def x = [a:1]; x.collectEntries{ a,b -> [a,b] }", Map.of("a", 1));
    test("def x = [a:1,b:2]; x.collectEntries{ it }", Map.of("a", 1,"b",2));
    test("def x = [a:1,b:2]; x.collectEntries{ a,b -> [a,b] }", Map.of("a", 1,"b",2));
    testError("def x = [a:1]; x.collectEntries{}", "got null");
    test("def x = [['a',1]]; x.collectEntries()", Map.of("a",1));
    test("def x = [['a',1],['a',2]]; x.collectEntries()", Map.of("a",2));
    test("def x = [a:1,b:2]; x.collectEntries()", Map.of("a",1,"b",2));
    test("def x = [1,2,3,4]; x.collectEntries{ [it.toString()*it,it*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it,it*it] }.collectEntries{ a,b -> [a.toString()*a,b] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.map{ [it.toString()*it,it*it] }.collectEntries()", Map.of("1",1,"22",4,"333",9,"4444",16));
    testError("def x = [1,2,3,4]; x.map{ [it,it*it] }.collectEntries()", "expected string type for key");
    test("def x = [1,2,3,4]; x.collectEntries{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] }", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; x.collectEntries(mapper:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", Map.of("1",1,"22",4,"333",9,"4444",16));
    test("def x = [1,2,3,4]; def f = x.collectEntries; f(mapper:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", Map.of("1",1,"22",4,"333",9,"4444",16));
    testError("def x = [1,2,3,4]; def f = x.collectEntries; f(closurex:{ [sleep(0,it.toString())*sleep(0,it),sleep(0,it)*it] })", "no such parameter");
    test("def x = [1,2,3,4]; x.map{ [sleep(0,it),sleep(0,it)*sleep(0,it)] }.collectEntries{ a,b -> [sleep(0,a.toString())*sleep(0,a),sleep(0,b)] }", Map.of("1",1,"22",4,"333",9,"4444",16));
  }

  @Test public void mapWithIndex() {
    test("[null].map{it}", Collections.singletonList(null));
    var list = new ArrayList<>();
    list.add(null);
    list.add(0);
    test("[null].mapWithIndex{it}", List.of(list));
    test("[].mapWithIndex{ it[0] + it[1] }", List.of());
    test("def x = []; x.mapWithIndex{ it[0] + it[1] }", List.of());
    test("def x = [:]; x.mapWithIndex{ it[0] + it[1] }", List.of());
    test("[1,2,3].mapWithIndex{ it[0] + it[1] }.sum()", 9);
    test("[1,2,3].mapWithIndex()", List.of(List.of(1,0),List.of(2,1),List.of(3,2)));
    test("def x = [1,2,3]; x.mapWithIndex{ it[0] + it[1] }.sum()", 9);
    test("def x = [a:1,b:2]; x.mapWithIndex{ it,i -> it[0] + it[1] + i }", List.of("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f{ it,i -> it[0] + it[1] + i }", List.of("a10","b21"));
    test("def x = [a:1,b:2]; x.mapWithIndex{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i }", List.of("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i }", List.of("a10","b21"));
    test("def x = [a:1,b:2]; x.mapWithIndex(mapper:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", List.of("a10","b21"));
    test("def x = [a:1,b:2]; def f = x.mapWithIndex; f(mapper:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", List.of("a10","b21"));
    testError("def x = [a:1,b:2]; def f = x.mapWithIndex; f(closurex:{ it,i -> sleep(0,it[0]) + sleep(0,it[1]) + i })", "no such parameter");
  }

  @Test public void collectionMap() {
    test("2.map{it}", List.of(0,1));
    test("def x = 2; x.map{it}", List.of(0,1));
    test("def x = 2; def f = x.map; f{it}", List.of(0,1));
    test("[].map()", List.of());
    test("def f = [].map; f()", List.of());
    testError("[].map{}{}", "too many arguments");
    testError("def x = []; x.map{}{}", "too many arguments");
    test("[].map{}", List.of());
    test("[1,2,3].map{}", Arrays.asList(null, null, null));
    test("[1,2,3].map()", List.of(1,2,3));
    test("def x = [1,2,3]; x.map{}", Arrays.asList(null, null, null));
    test("def x = [1,2,3]; x.map()", List.of(1,2,3));
    test("def x = []; x.map{}", List.of());
    test("def x = []; x.map()", List.of());
    test("List x = []; x.map{}", List.of());
    testError("def x; x.map{}", "null value");
    test("List x = [1,2,3,4]; x.map{it*it}", List.of(1,4,9,16));
    test("List x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{it*it}.map{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; def f = x.map{it*it}.map; f{ x -> x + x }", List.of(2,8,18,32));
    test("def x = [1,2,3,4]; x.map{ x -> return {x*x}}.map{it()}", List.of(1,4,9,16));
    test("[:].map{}", List.of());
    test("[:].map()", List.of());
    test("def x = 1; x.map{}", Collections.singletonList(null));
    test("def x = 1; x.map{it}", List.of(0));
    test("def x = 1; x.map(mapper:{it})", List.of(0));
    test("def x = 1; def f = x.map; f(mapper:{sleep(0,it)+sleep(0,1)-sleep(0,1)})", List.of(0));
    testError("def x = 1; x.map(closurex:{it})", "no such parameter");
    testError("def x = 1; def f = x.map; f(closurex:{it})", "no such parameter");
    test("[a:1,b:2,c:3].map{ [it[0]+it[0],it[1]+it[1]] }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].map{ it[0]+it[0]+it[1]+it[1] }", List.of("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].map{ it.map{ it+it } }", List.of(List.of("aa",2),List.of("bb",4),List.of("cc",6)));
    test("[a:1,b:2,c:3].map{ x,y -> x + y }", List.of("a1","b2","c3"));
    test("[a:1,b:2,c:3].map(mapper:{ x,y -> x + y })", List.of("a1","b2","c3"));
    test("[a:1,b:2,c:3].map(mapper:{ x,y -> sleep(0,x) + sleep(0,y) })", List.of("a1","b2","c3"));
    testError("[a:1,b:2,c:3].map(closurex:{ x,y -> x + y })", "no such parameter");
    testError("def f = [a:1,b:2,c:3]; f.map(closurex:{ x,y -> x + y })", "no such parameter");
    test("def x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [1,2,3]; def y = x.map{}; y.size()", 3);
    test("var x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", List.of(2, 2));
    test("def x = [a:1,b:2]; def y = x.map{ def z = it.map(); z.size() }; y", List.of(2, 2));
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
    test("def x = [1,2,3]; x.map{it*it}.collect{it+it}.map{it-1}", List.of(1, 7, 17));
    test("[1,2,3].map{it} instanceof List", true);
    test("def x = [1,2,3]; x.map{it} instanceof List", true);
    test("def f = [1,2,3].map; f{it+it}", List.of(2,4,6));
    test("def f = [1,2,3].map; f()", List.of(1,2,3));
    testError("def f = [1,2,3].map; f('x')", "cannot be cast");
    test("[1].map{ [it,it] }.map{ a,b -> a + b }", List.of(2));
    test("def x = [1,2,3]; def f = x.map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("def f = [1,2,3].map; f{it+it}", List.of(2,4,6));
    test("def f = [1,2,3].map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("[1,2,3].map{it*it}[1]", 4);
    test("'abcde'.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; x.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.map; f{it+it}.join()", "aabbccddee");
    testError("[1,2,3].map{ -> }", "too many arguments");
    test("[1,2,3].map{ int i -> i * i }", List.of(1,4,9));
    test("int[] a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2,4,6));
    test("long[] a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2L,4L,6L));
    test("double[] a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2.0,4.0,6.0));
    test("Decimal[] a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("String[] a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", List.of("11","22","33"));
    test("var a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2,4,6));
    test("var a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2L,4L,6L));
    test("var a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2.0,4.0,6.0));
    test("var a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("var a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", List.of("11","22","33"));
    test("def a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2,4,6));
    test("def a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2L,4L,6L));
    test("def a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2.0,4.0,6.0));
    test("def a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("def a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", List.of("11","22","33"));
    test("Object a = new int[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2,4,6));
    test("Object a = new long[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2L,4L,6L));
    test("Object a = new double[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(2.0,4.0,6.0));
    test("Object a = new Decimal[3]; a[0] = 1; a[1] = 2; a[2] = 3; a.map{ it+it }", List.of(new BigDecimal(2),new BigDecimal(4),new BigDecimal(6)));
    test("Object a = new String[3]; a[0] = '1'; a[1] = '2'; a[2] = '3'; a.map{ it+it }", List.of("11","22","33"));
    test("int[][] a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", List.of(4,8,12));
    test("def a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", List.of(4,8,12));
    test("var a = new int[3][2]; a[0][0] = 1; a[0][1] = 1; a[1][0] = 2; a[1][1] = 2; a[2][0] = 3; a[2][1] = 3; a.map{ it.sum()+it.sum() }", List.of(4,8,12));
  }

  @Test public void reverse() {
    test("[].reverse()", List.of());
    test("[1].reverse()", List.of(1));
    test("1.reverse()", List.of(0));
    test("'abc'.reverse().join()", "cba");
    test("[1,2,3].reverse()", List.of(3,2,1));
    test("[1,2,3,4].reverse()", List.of(4,3,2,1));
    test("3.reverse()", List.of(2,1,0));
    test("def x = []; x.reverse()", List.of());
    test("def x = [1]; x.reverse()", List.of(1));
    test("def x = 1; x.reverse()", List.of(0));
    test("def x = 'abc'; x.reverse().join()", "cba");
    test("def x = [1,2,3]; x.reverse()", List.of(3,2,1));
    test("def x = [1,2,3,4]; x.reverse()", List.of(4,3,2,1));
    test("def x = [1,2,3,4]; def f = x.reverse; f()", List.of(4,3,2,1));
    test("def x = 3; x.reverse()", List.of(2,1,0));
    test("[1,2,3,4].map{ sleep(0,it)+sleep(0,it) }.reverse()", List.of(8,6,4,2));
  }

  @Test public void collectionFlatMap() {
    test("[].flatMap()", List.of());
    test("def f = [].flatMap; f()", List.of());
    testError("[].flatMap{}{}", "too many arguments");
    testError("def x = []; x.flatMap{}{}", "too many arguments");
    test("[].flatMap{}", List.of());
    test("[1,2,3].flatMap{}", List.of());
    test("[1,2,3].flatMap()", List.of(1,2,3));
    test("['a','abc','xyz'].flatMap{ it.map{it} }", List.of("a", "a", "b", "c", "x", "y", "z"));
    test("def x = [1,2,3]; x.flatMap{}", List.of());
    test("def x = [1,2,3]; x.flatMap()", List.of(1,2,3));
    test("def x = []; x.flatMap{}", List.of());
    test("def x = []; x.flatMap()", List.of());
    test("List x = []; x.flatMap{}", List.of());
    testError("def x; x.flatMap{}", "null value");
    test("[:].flatMap{}", List.of());
    test("[:].flatMap()", List.of());
    test("[a:1,b:2,c:3].flatMap{ [it[0]+it[0],it[1]+it[1]] }", List.of("aa", 2, "bb", 4, "cc", 6));
    test("def x = [1,2,3]; def y = x.flatMap{}; y.size()", 0);
    test("var x = [1,2,3]; def y = x.flatMap{}; y.size()", 0);
    test("def x = [1,2,3]; def y = x.flatMap{[it]}; y.size()", 3);
    test("'abcde'.flatMap{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; x.flatMap{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.flatMap; f{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.flatMap; f{sleep(0,it)+sleep(0,it)}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.flatMap; f(mapper:{sleep(0,it)+sleep(0,it)}).join()", "aabbccddee");
    testError("def x = 'abcde'; def f = x.flatMap; f(closurex:{sleep(0,it)+sleep(0,it)}).join()", "no such parameter");
    test("def x = 'abcde'; def f = x.flatMap(mapper:{sleep(0,it)+sleep(0,it)}).join()", "aabbccddee");
    testError("[1,2,3].flatMap{ -> }", "too many arguments");
    test("[1,[2,3,4]].flatMap()", List.of(1,2,3,4));
    test("[1,[2,3,4]].flatMap{ it instanceof List ? it : [it] }", List.of(1,2,3,4));
    test("[1,[2,[3,4],5]].flatMap()", List.of(1,2,List.of(3,4), 5));
    test("[1,[2,[3,4],5]].flatMap{ it instanceof List ? it.flatMap() : it }", List.of(1,2,3,4,5));
    test("[a:1,b:2,c:3].flatMap{ [it[0]+it[0],it[1]+it[1]] }", List.of("aa",2,"bb",4,"cc",6));
    test("[a:1,b:2,c:3].flatMap{ it[0]+it[0]+it[1]+it[1] }", List.of("aa11","bb22","cc33"));
    test("[a:1,b:2,c:3].flatMap{ it.flatMap{ it+it } }", List.of("aa",2,"bb",4,"cc",6));
    test("[a:1,b:2,c:3].flatMap(mapper:{ it.flatMap{ sleep(0,it)+sleep(0,it) } })", List.of("aa",2,"bb",4,"cc",6));
    testError("def f = [a:1,b:2,c:3].flatMap; f(closurex:{ it.flatMap{ it+it } })", "no such parameter");
    test("[a:1,b:2,c:3].flatMap{ x,y -> x + y }", List.of("a1","b2","c3"));
    test("[a:1,b:2,c:3].flatMap(mapper:{ x,y -> x + y })", List.of("a1","b2","c3"));
    test("[a:1,b:2,c:3].flatMap(mapper:{ x,y -> sleep(0,x) + sleep(0,y) })", List.of("a1","b2","c3"));
    testError("[a:1,b:2,c:3].flatMap(closurex:{ x,y -> x + y })", "no such parameter");
    testError("def f = [a:1,b:2,c:3]; f.flatMap(closurex:{ x,y -> x + y })", "no such parameter");
  }

  @Test public void mapEntryAsList() {
    test("def x; [a:1].each{ x = it }; x", List.of("a",1));
    test("def x; [a:1].each{ x = it }; x; x.size()", 2);
  }

  @Test public void reduce() {
    test("[].reduce(null){}", null);
    test("[].reduce(null){}", null);
    test("[1].reduce(null){}", null);
    test("[1].reduce(0){v,e -> v + e}", 1);
    testError("[1].reduce(0)", "missing mandatory argument");
    testError("def f = [1].reduce; f(0)", "missing mandatory argument");
    test("[1,2,3].reduce(0){v,e -> v + e}", 6);
    test("[1,2,3].reduce(10){v,e -> v + e}", 16);
    test("[1,2,3].reduce([]){v,e -> v + e}", List.of(1,2,3));
    test("def f = [1,2,3].reduce; f([]){v,e -> v + e}", List.of(1,2,3));
    test("[1,2,3].reduce([]){it[0] + it[1]}", List.of(1,2,3));
    test("def f = [1,2,3].reduce; f([]){it[0] + it[1]}", List.of(1,2,3));
    test("def x = [1,2,3]; x.reduce([]){v,e -> sleep(0,v) + sleep(0,e)}", List.of(1,2,3));
    test("def x = [1,2,3]; x.reduce(initial:[],accumulator:{v,e -> sleep(0,v) + sleep(0,e)})", List.of(1,2,3));
    test("def x = [1,2,3]; def f = x.reduce; f(initial:[],accumulator:{v,e -> sleep(0,v) + sleep(0,e)})", List.of(1,2,3));
    testError("def x = [1,2,3]; def f = x.reduce; f(initialx:[],accumulator:{v,e -> sleep(0,v) + sleep(0,e)})", "missing value");
    testError("def x = [1,2,3]; def f = x.reduce; f(initial:[],x:1,accumulator:{v,e -> sleep(0,v) + sleep(0,e)})", "no such parameter");
    testError("def x = [1,2,3]; x.reduce(initial:[]){v,e -> sleep(0,v) + sleep(0,e)}", "missing value for mandatory parameter 'accumulator'");
    test("[1,2,3].reduce([]){v,e -> sleep(0,v) + sleep(0,e)}", List.of(1,2,3));
    test("def f = [1,2,3].reduce; f([]){v,e -> sleep(0,v) + sleep(0,e)}", List.of(1,2,3));
    test("[a:1,b:2].reduce(''){ it[0] + it[1] }", "['a', 1]['b', 2]");
    test("def x = [a:1,b:2]; def f = x.reduce; f(''){ v,e -> v + e }", "['a', 1]['b', 2]");
    test("'abcasfiieefaeiihsaiggaaasdh'.reduce([:]){m,c -> m[c]++; m}.sort{a,b -> b[1] <=> a[1]}.join(':')", "['a', 7]:['i', 5]:['s', 3]:['e', 3]:['f', 2]:['h', 2]:['g', 2]:['b', 1]:['c', 1]:['d', 1]");
  }

  @Test public void skipLimit() {
    test("[1,2,3].skip(1)", List.of(2, 3));
    test("[1,2,3].skip(0)", List.of(1, 2, 3));
    test("[1,2,3].skip(3)", List.of());
    test("[1,2,3].skip(4)", List.of());
    test("[].skip(1)", List.of());
    test("[1,2,3].map{it+it}.skip(1)", List.of(4, 6));
    test("[1,2,3].map{sleep(0,it)+sleep(0,it)}.skip(1).map{it*it}", List.of(16, 36));
    test("[1,2,3].map{sleep(0,it)+sleep(0,it)}.skip(2).map{it*it}", List.of(36));
    test("'abc'.skip(2).join()", "c");
    test("[a:1,b:2,c:3].skip(1) as Map", Map.of("b", 2, "c", 3));
    test("def x = [1,2,3]; x.skip(1)", List.of(2, 3));
    test("def x = [1,2,3]; x.skip(0)", List.of(1, 2, 3));
    test("def x = [1,2,3]; x.skip(3)", List.of());
    test("def x = [1,2,3]; x.skip(4)", List.of());
    test("def x = []; x.skip(1)", List.of());
    test("def x = [1,2,3]; x.map{it+it}.skip(1)", List.of(4, 6));
    test("def x = [1,2,3]; x.map{sleep(0,it)+sleep(0,it)}.skip(1).map{it*it}", List.of(16, 36));
    test("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.skip; f(1).map{it*it}", List.of(16, 36));
    test("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.skip(1).map; f{it*it}", List.of(16, 36));
    test("def x = [1,2,3]; x.map{sleep(0,it)+sleep(0,it)}.skip(2).map{it*it}", List.of(36));
    test("def x = 'abc'; x.skip(2).join()", "c");
    test("def x = [a:1,b:2,c:3]; x.skip(1) as Map", Map.of("b", 2, "c", 3));
    test("def x = [a:1,b:2,c:3]; def f = x.skip; f(1) as Map", Map.of("b", 2, "c", 3));
    test("[].skip(-1)", List.of());
    test("[1,2,3].skip(-1)", List.of(3));
    test("[1,2,3].skip(-2)", List.of(2,3));
    test("[1,2,3].skip(-3)", List.of(1,2,3));
    test("[1,2,3].skip(-4)", List.of(1,2,3));
    test("[1,2,3,4,5,6].skip(-2)", List.of(5,6));
    test("[1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip(-2)", List.of(4,6));
    test("[1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip(count:-2)", List.of(4,6));
    testError("[1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip(countx:-2)", "no such parameter");
    testError("def f = [1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip; f(countx:-2)", "missing value");
    testError("def f = [1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip; f(count:-2, x:1)", "no such parameter");
    test("def x = []; x.skip(-1)", List.of());
    test("def x = [1,2,3]; x.skip(-1)", List.of(3));
    test("def x = [1,2,3]; x.skip(-2)", List.of(2,3));
    test("def x = [1,2,3]; x.skip(-3)", List.of(1,2,3));
    test("def x = [1,2,3]; x.skip(-4)", List.of(1,2,3));
    test("def x = [1,2,3,4,5,6]; x.skip(-2)", List.of(5,6));
    test("def x = [1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}; x.skip(-2)", List.of(4,6));
    test("def x = [1,2,3,4,5,6]; def f = x.map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.skip; f(-2)", List.of(4,6));

    test("[1,2,3].limit(1)", List.of(1));
    test("[1,2,3].limit(0)", List.of());
    test("[1,2,3].limit(3)", List.of(1, 2, 3));
    test("[1,2,3].limit(4)", List.of(1, 2, 3));
    test("[1,2,3].limit(2)", List.of(1, 2));
    test("[1,2,3].limit(2).skip(1)", List.of(2));
    test("[].limit(1)", List.of());
    test("[].limit(0)", List.of());
    test("[1,2,3].map{it+it}.limit(2)", List.of(2, 4));
    test("[1,2,3].map{sleep(0,it)+sleep(0,it)}.limit(2).map{it*it}", List.of(4, 16));
    test("'abc'.limit(2).join()", "ab");
    test("[a:1,b:2,c:3].limit(2) as Map", Map.of("a", 1, "b", 2));
    test("def x = [1,2,3]; x.limit(1)", List.of(1));
    test("def x = [1,2,3]; x.limit(0)", List.of());
    test("def x = [1,2,3]; x.limit(3)", List.of(1, 2, 3));
    test("def x = [1,2,3]; x.limit(4)", List.of(1, 2, 3));
    test("def x = [1,2,3]; x.limit(2)", List.of(1, 2));
    test("def x = [1,2,3]; x.limit(2).skip(1)", List.of(2));
    test("def x = []; x.limit(1)", List.of());
    test("def x = []; x.limit(0)", List.of());
    test("def x = [1,2,3]; x.map{it+it}.limit(2)", List.of(2, 4));
    test("def x = [1,2,3]; x.map{sleep(0,it)+sleep(0,it)}.limit(2).map{it*it}", List.of(4, 16));
    test("def x = [1,2,3]; x.map{sleep(0,it)+sleep(0,it)}.limit(count:2).map{it*it}", List.of(4, 16));
    test("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.limit; f(count:2).map{it*it}", List.of(4, 16));
    testError("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.limit; f(countx:2).map{it*it}", "missing value");
    testError("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.limit; f(count:2,x:1).map{it*it}", "no such parameter");
    test("def x = [1,2,3]; def f = x.map{sleep(0,it)+sleep(0,it)}.limit; f(2).map{it*it}", List.of(4, 16));
    test("def x = 'abc'; x.limit(2).join()", "ab");
    test("def x = [a:1,b:2,c:3]; x.limit(2) as Map", Map.of("a", 1, "b", 2));
    test("[].limit(-1)", List.of());
    test("[1,2,3].limit(-1)", List.of(1,2));
    test("[1,2,3].limit(-2)", List.of(1));
    test("[1,2,3].limit(-3)", List.of());
    test("[1,2,3].limit(-4)", List.of());
    test("[1,2,3,4,5,6].limit(-2)", List.of(1,2,3,4));
    test("[1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.limit(-2)", List.of(2));
    test("def x = []; x.limit(-1)", List.of());
    test("def x = [1,2,3]; x.limit(-1)", List.of(1,2));
    test("def x = [1,2,3]; x.limit(-2)", List.of(1));
    test("def x = [1,2,3]; x.limit(-3)", List.of());
    test("def x = [1,2,3]; x.limit(-4)", List.of());
    test("def x = [1,2,3,4,5,6]; x.limit(-2)", List.of(1,2,3,4));
    test("def x = [1,2,3,4,5,6].map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}; x.limit(-2)", List.of(2));
    test("def x = [1,2,3,4,5,6]; def f = x.map{sleep(0,it-1)+sleep(0,1)}.filter{it%2 == 0}.limit; f(-2)", List.of(2));

    test("[1,2,3,4].limit(1L)", List.of(1));
    test("[1,2,3,4].limit(count:1L)", List.of(1));
    test("[1,2,3,4].limit(1.123D)", List.of(1));
    test("[1,2,3,4].limit(1.123)", List.of(1));
    testError("[1,2,3,4].limit('123')", "cannot be cast");
    testError("def f = [1,2,3,4].limit; f('123')", "cannot be cast to int");
    test("def f = [1,2,3,4].limit; f('a')", List.of(1,2,3,4));    // 'a' converted to Unicode value
    testError("[1,2,3,4].limit(count:'123')", "cannot be cast");
    testError("def f = [1,2,3,4].limit; f(count:'123')", "cannot be cast to int");
    test("def f = [1,2,3,4].limit; f(count:'a')", List.of(1,2,3,4));    // 'a' converted to Unicode value

    // Async mode fails because we wrap each method invocation with a sleep which means that the
    // map() call is forced to generate entire list before passing to limit()
    doTest("def i = 0; [1,2,3,4,5].map{ i++; it }.limit(2); i", 2);
    doTest("def i = 0; [1,2,3,4,5].map{ i++; it }.map{ sleep(0, sleep(0,it)) }.limit(2); i", 2);
    doTest("def i = 0; [1,2,3,4,5].map{ i++; it }.filter{ sleep(0,it) }.map{ sleep(0, sleep(0,it)) }.limit(2); i", 2);
  }

  @Test public void unique() {
    test("[].unique()", List.of());
    test("def x = []; x.unique()", List.of());
    test("[1].unique()", List.of(1));
    test("[1,1].unique()", List.of(1));
    test("[2,2,1,1,2].unique()", List.of(2,1,2));
    test("def x = [1]; x.unique()", List.of(1));
    test("def x = [1,1]; x.unique()", List.of(1));
    test("def x = [2,2,1,1,2]; x.unique()", List.of(2,1,2));
    test("def x = [2,2,1,1,2]; def f = x.unique; f()", List.of(2,1,2));
    test("[[a:1],[b:2],[b:2],[a:1]].unique()", List.of(Map.of("a",1),Map.of("b",2),Map.of("a",1)));
    test("[[a:1],[b:2],[b:2],[a:1]].unique()", List.of(Map.of("a",1),Map.of("b",2),Map.of("a",1)));
    test("[a:1,b:2].unique().collectEntries()", Map.of("a",1,"b",2));
  }

  @Test public void collectionSort() {
    test("[].sort()", List.of());
    test("[].sort{it[1] <=> it[0]}", List.of());
    test("[1].sort{it[1] <=> it[0]}", List.of(1));
    test("[1,2].sort{it[1] <=> it[0]}", List.of(2, 1));
    test("[3,2,1].sort()", List.of(1, 2, 3));
    test("[3,3,4,1,2,2,5].sort()", List.of(1, 2, 2, 3, 3, 4, 5));
    test("[3,3,4,1,2,2,5].sort{ a,b -> a <=> b }", List.of(1, 2, 2, 3, 3, 4, 5));
    test("def f = [3,2,1].sort; f()", List.of(1, 2, 3));
    test("def f = [3,2,1].sort; f{a,b -> b <=> a}", List.of(3, 2, 1));
    testError("def f = [3,2,1].sort; f('z')", "cannot be cast");
    test("[1,2,3].sort()", List.of(1, 2, 3));
    test("[3,2,1,4,5].sort{a,b -> b <=> a}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5].sort{a,b -> b <=> a}; x", List.of(5, 4, 3, 2, 1));
    test("[3,2,1,4,5].sort{it[1] <=> it[0]}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; x.sort{a,b -> b <=> a}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; x.map{sleep(0,it-1)+sleep(0,1)}.sort{a,b -> b <=> a}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; x.map{sleep(0,it-1)+sleep(0,1)}.sort(comparator:{a,b -> b <=> a})", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; def f = x.map{sleep(0,it-1)+sleep(0,1)}.sort; f(comparator:{a,b -> sleep(0,b) <=> sleep(0,a)})", List.of(5, 4, 3, 2, 1));
    testError("def x = [3,2,1,4,5]; def f = x.map{sleep(0,it-1)+sleep(0,1)}.sort; f(x:1, comparator:{a,b -> sleep(0,b) <=> sleep(0,a)})", "no such parameter");
    test("def x = [3,2,1,4,5]; x.sort{it[1] <=> it[0]}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; def f = x.sort; f{a,b -> b <=> a}", List.of(5, 4, 3, 2, 1));
    test("def x = [3,2,1,4,5]; def f = x.sort; f{it[1] <=> it[0]}", List.of(5, 4, 3, 2, 1));
    testError("def x = [3,2,'a',4,5]; def f = x.sort{it[1] <=> it[0]}", "cannot compare");
    test("def x = [a:1,x:4,e:3,g:7]; x.sort{a,b -> a[0] <=> b[0]}",
         List.of(List.of("a", 1), List.of("e", 3), List.of("g", 7), List.of("x", 4)));
    test("def x = [a:1,x:4,e:3,g:7]; x.sort{it[0][0] <=> it[1][0]}",
         List.of(List.of("a", 1), List.of("e", 3), List.of("g", 7), List.of("x", 4)));
    test("def x = [a:1,x:4,e:3,g:7]; x.sort{sleep(0,it[0][0]) <=> sleep(0,it[1][0])}",
         List.of(List.of("a", 1), List.of("e", 3), List.of("g", 7), List.of("x", 4)));
    test("def x = [a:1,x:4,e:3,g:7]; x.sort(comparator:{sleep(0,it[0][0]) <=> sleep(0,it[1][0])})",
         List.of(List.of("a", 1), List.of("e", 3), List.of("g", 7), List.of("x", 4)));
    test("def x = [a:1,x:4,e:3,g:7]; def f = x.sort; f(comparator:{sleep(0,it[0][0]) <=> sleep(0,it[1][0])})",
         List.of(List.of("a", 1), List.of("e", 3), List.of("g", 7), List.of("x", 4)));
    testError("def x = [a:1,x:4,e:3,g:7]; def f = x.sort; f(x:1, comparator:{sleep(0,it[0][0]) <=> sleep(0,it[1][0])})", "no such parameter");
    testError("def x = [a:1,x:4,e:3,g:7]; def f = x.sort; f(comparatorx:{sleep(0,it[0][0]) <=> sleep(0,it[1][0])})", "no such parameter");
    test("'afedcba'.sort().join()", "aabcdef");
    test("'afedcba'.sort{a,b -> a <=> b}.join()", "aabcdef");
    test("'afedcba'.sort{a,b -> b <=> a}.join()", "fedcbaa");
    test("''.sort{a,b -> b <=> a}.join()", "");
    testError("[[a:1],[b:2]].sort()", "cannot compare objects of type map");
    test("[[1],[2]].sort()", List.of(List.of(1),List.of(2)));
    test("[[2],[1]].sort()", List.of(List.of(1),List.of(2)));
    test("[[1,2],[1]].sort()", List.of(List.of(1),List.of(1,2)));
    test("[[1,2],[1]].sort{ a,b -> b <=> a }", List.of(List.of(1,2),List.of(1)));
  }

  @Test public void largeSort() {
    useAsyncDecorator = false;

    final int SORT_SIZE = ThreadLocalRandom.current().nextInt(500);
    List<Integer> randomNums = IntStream.range(0, SORT_SIZE)
                                        .mapToObj(i -> ThreadLocalRandom.current().nextInt(100000))
                                        .collect(Collectors.toList());
    ArrayList<Integer> sorted = new ArrayList(randomNums);
    sorted.sort(null);
    test("" + randomNums + ".sort{ a,b -> a <=> b }", sorted);
    test("" + randomNums + ".sort()", sorted);
    test("" + randomNums + ".sort{ a,b -> sleep(sleep(0,0),a) <=> sleep(0,sleep(0,b)) }", sorted);
  }

  @Test public void listAddAt() {
    test("[].add(1)", List.of(1));
    test("[].add(element:1)", List.of(1));
    test("def x = []; def f = x.add; f(element:1)", List.of(1));
    testError("[].add(elemx:1)", "no such parameter");
    testError("def f = [].add; f(elemx:1)", "missing value");
    test("[].add([])", List.of(List.of()));
    test("def x = []; x.add([])", List.of(List.of()));
    test("def x = []; def f = x.add; f([])", List.of(List.of()));
    test("[].addAt(0,1)", List.of(1));
    test("def f = [].addAt; f(0,1)", List.of(1));
    test("[].addAt(0,1).addAt(0,2)", List.of(2,1));
    test("[].addAt(index:0,element:1).addAt(index:0,element:2)", List.of(2,1));
    test("def x = []; def f = x.addAt; f(index:0,element:1).addAt(index:0,element:2)", List.of(2,1));
    testError("def x = []; def f = x.addAt; f(indexx:0,element:1).addAt(index:0,element:2)", "missing value");
    testError("def x = []; def f = x.addAt; f(x:1, index:0,element:1).addAt(index:0,element:2)", "no such parameter");
    test("[].addAt(0,1).addAt(0,2).addAt(2,3)", List.of(2,1,3));
    test("[].addAt(0,1).addAt(0,2).addAt(2,3).addAt(-1,4)", List.of(2,1,4,3));
    test("[].addAt(0,1).addAt(0,2).addAt(2,3).add(4)", List.of(2,1,3,4));
    test("def x = []; x.addAt(0,1)", List.of(1));
    test("def x = []; def f = x.addAt; f(0,1)", List.of(1));
    test("def x = [].addAt(0,1); x.addAt(0,2)", List.of(2,1));
    test("def x = []; x.addAt(0,1).addAt(0,2).addAt(2,3)", List.of(2,1,3));
    test("def x = [].addAt(0,1).addAt(0,2).addAt(2,3); x.addAt(-1,4)", List.of(2,1,4,3));
    test("def x = [].addAt(0,1).addAt(0,2).addAt(2,3); x.add(4)", List.of(2,1,3,4));
    test("def x = [2,1,3]; x.add(4)", List.of(2,1,3,4));
    test("def x = [2,1,3]; def f = x.add; f(4)", List.of(2,1,3,4));
  }

  @Test public void subList() {
    test("[].subList(0)", List.of());
    test("[1,2,3].subList(1,2)", List.of(2));
    test("[1,2,3].subList(1,3)", List.of(2,3));
    test("[1,2,3].subList(1,-1)", List.of(2));
    test("def x = []; x.subList(0)", List.of());
    test("def x = [1,2,3]; x.subList(1,2)", List.of(2));
    test("def x = [1,2,3]; x.subList(1,3)", List.of(2,3));
    test("def x = [1,2,3]; def f = x.subList; f(1,3)", List.of(2,3));
    test("[1,2,3].map{it}.subList(1,3)", List.of(2,3));
    test("[1,2,3].map{sleep(0,it)+sleep(0,0)}.subList(1,3)", List.of(2,3));
    test("[1,2,3,4,5].subList(2,4)", List.of(3,4));
    test("[1,2,3,4,5].subList(4,5)", List.of(5));
    test("[1,2,3,4,5].subList(start:4,end:5)", List.of(5));
    testError("[1,2,3,4,5].subList(startx:4,end:5,x:1)", "no such parameter");
    testError("[1,2,3,4,5].subList(start:4,end:5,x:1)", "no such parameter");
    testError("def x = [1,2,3,4,5]; def f = x.subList; f(startx:4,end:5,x:1)", "missing value");
    testError("def f = [1,2,3,4,5].subList; f(start:4,end:5,x:1)", "no such parameter");
    test("[1,2,3,4,5].subList(2)", List.of(3,4,5));
    test("[1,2,3,4,5].subList(0)", List.of(1,2,3,4,5));
    test("[1,2,3,4,5].subList(5)", List.of());
    test("'abcdef'.subList(2,4).join()", "cd");
    test("'abcde'.subList(4,5).join()", "e");
    test("'abcde'.subList(5).join()", "");
    test("'abcdef'.subList(2).join()", "cdef");
    test("def f = 'abcdef'.subList; f(2).join()", "cdef");
    test("10.subList(5,8)", List.of(5,6,7));
    test("def f = 10.subList; f(5,8)", List.of(5,6,7));
    test("10.subList(5)", List.of(5,6,7,8,9));
    test("5.map{it+5}.subList(0)", List.of(5,6,7,8,9));
    test("5.map{it+5}.subList(4,5)", List.of(9));
    test("5.map{it+5}.subList(start:4,end:5)", List.of(9));
    test("5.map{sleep(0,it)+sleep(0,5)}.subList(start:4,end:5)", List.of(9));
    test("def f = 5.map{sleep(0,it)+sleep(0,5)}.subList; f(start:4,end:5)", List.of(9));
    testError("5.map{it+5}.subList(start:4,endx:5)", "no such parameter");
    testError("def f = 5.map{it+5}.subList; f(start:4,endx:5)", "no such parameter");
    test("5.map{it+5}.subList(5)", List.of());
    test("[a:1,b:2,c:3].subList(1,3)", List.of(List.of("b",2),List.of("c",3)));
    test("[a:1,b:2,c:3].subList(start:1,end:3)", List.of(List.of("b",2),List.of("c",3)));
    test("def f = [a:1,b:2,c:3].subList; f(start:1,end:3)", List.of(List.of("b",2),List.of("c",3)));
    test("def f = [a:1,b:2,c:3].subList; f(1,3)", List.of(List.of("b",2),List.of("c",3)));
    test("[a:1,b:2,c:3].subList(1)", List.of(List.of("b",2),List.of("c",3)));
    test("[a:1,b:2,c:3].subList(0)", List.of(List.of("a",1),List.of("b",2),List.of("c",3)));
    test("[a:1,b:2,c:3].subList(2,3)", List.of(List.of("c",3)));
    test("[a:1,b:2,c:3].subList(3)", List.of());
    test("def f = [a:1,b:2,c:3].subList; f(3)", List.of());
    test("def x = [1,2,3,4,5]; x.subList(2,4)", List.of(3,4));
    test("def x = [1,2,3,4,5]; x.subList(4,5)", List.of(5));
    testError("def x = [1,2,3,4,5]; x.subList(4,6)", "indexoutofbounds");
    test("def x = [1,2,3,4,5]; x.subList(2)", List.of(3,4,5));
    test("def x = [1,2,3,4,5]; x.subList(0)", List.of(1,2,3,4,5));
    test("def x = [1,2,3,4,5]; x.subList(5)", List.of());
    test("def x = [1,2,3,4,5]; x.subList(-1)", List.of(5));
    test("def x = [1,2,3,4,5]; x.subList(-5)", List.of(1,2,3,4,5));
    testError("def x = [1,2,3,4,5]; x.subList(-6)", "indexoutofbounds");
    test("List x = [1,2,3,4,5]; x.subList(-1)", List.of(5));
    test("List x = [1,2,3,4,5]; x.subList(-5)", List.of(1,2,3,4,5));
    testError("List x = [1,2,3,4,5]; x.subList(-6)", "indexoutofbounds");
    test("List x = [1,2,3,4,5]; x.subList(-2,-1)", List.of(4));
    test("List x = [1,2,3,4,5]; x.subList(2,-1)", List.of(3,4));
    test("List x = [1,2,3,4,5]; x.subList(-5,-2)", List.of(1,2,3));
    testError("List x = [1,2,3,4,5]; x.subList(-6,-1)", "indexoutofbounds");
    test("def x = [1,2,3,4,5]; def f = x.subList; f(5)", List.of());
    test("def x = 'abcdef'; x.subList(2,4).join()", "cd");
    test("def x = 'abcde'; x.subList(4,5).join()", "e");
    test("def x = 'abcde'; x.subList(5).join()", "");
    test("def x = 'abcdef'; x.subList(2).join()", "cdef");
    test("def x = 'abcdef'; def f = x.subList; f(2).join()", "cdef");
    test("def x = 10; x.subList(5,8)", List.of(5,6,7));
    test("def x = 10; x.subList(5)", List.of(5,6,7,8,9));
    test("def x = 5.map{it+5}; x.subList(0)", List.of(5,6,7,8,9));
    test("def x = 5.map{it+5}; x.subList(4,5)", List.of(9));
    test("def x = 5.map{it+5}; x.subList(5)", List.of());
    test("def x = 5.map{it+5}; def f = x.subList; f(5)", List.of());
    test("def x = [a:1,b:2,c:3]; x.subList(1,3)", List.of(List.of("b",2),List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; def f = x.subList; f(1,3)", List.of(List.of("b",2),List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; x.subList(1)", List.of(List.of("b",2),List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; x.subList(0)", List.of(List.of("a",1),List.of("b",2),List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; x.subList(2,3)", List.of(List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; x.subList(3)", List.of());
    test("def x = [a:1,b:2,c:3]; x.subList(-1)", List.of(List.of("c",3)));
    test("def x = [a:1,b:2,c:3]; x.subList(-2,-1)", List.of(List.of("b",2)));
  }

  @Test public void mapRemove() {
    testError("Map m = null; m.remove('a')", "null object");
    testError("def m = null; m.remove('a')", "null value");
    test("[:].remove('a')", null);
    test("[b:2].remove('a')", null);
    test("Map m = [:]; m.remove('a')", null);
    test("Map m = [:]; m.remove('a'); m", Map.of());
    test("def m = [:]; m.remove('a'); m", Map.of());
    test("Map m = [a:1]; m.remove('a')", 1);
    test("def m = [a:1]; m.remove('a')", 1);
    test("Map m = [a:1]; m.remove('a'); m", Map.of());
    test("Map m = [a:1]; m.remove(key:'a'); m", Map.of());
    testError("Map m = [a:1]; m.remove(keyx:'a'); m", "no such parameter");
    test("Map m = [a:1]; def f = m.remove; f(key:'a'); m", Map.of());
    test("def m = [a:1]; def f = m.remove; f(key:'a'); m", Map.of());
    testError("Map m = [a:1]; def f = m.remove; f(keyx:'a'); m", "missing value");
    test("def m = [a:1]; m.remove('a'); m", Map.of());
    test("def m = [a:1]; def f = m.remove; f('a')", 1);
    test("def m = [a:1]; def f = m.remove; f('a'); m", Map.of());
    test("def m = [a:1,b:2]; def f = m.remove; f('a')", 1);
    test("def m = [a:1,b:2]; def f = m.remove; f('a'); m", Map.of("b",2));
    test("def m = [a:1,b:2]; def f = m.remove; f('c'); m", Map.of("a",1,"b",2));
  }

  @Test public void listRemove() {
    testError("List m = null; m.remove(0)", "null object");
    testError("def m = null; m.remove(0)", "null value");
    testError("[].remove(0)", "too large");
    testError("def x = []; x.remove(0)", "too large");
    test("[2].remove(0)", 2);
    test("[2].remove(-1)", 2);
    testError("[2].remove(-2)", "out of bounds");
    test("def x = [2]; x.remove(0)", 2);
    test("def x = [2]; x.remove(0); x", List.of());
    test("[1,2,3].remove(1)", 2);
    test("def x = [1,2,3]; x.remove(0)", 1);
    test("def x = [1,2,3]; x.remove(0); x", List.of(2,3));
    test("def x = [1,2,3]; x.remove(1)", 2);
    test("def x = [1,2,3]; x.remove(1); x", List.of(1,3));
    test("def x = [1,2,3]; x.remove(2)", 3);
    test("def x = [1,2,3]; x.remove(2); x", List.of(1,2));
    test("def x = [1,2,3]; x.remove(-3)", 1);
    test("def x = [1,2,3]; x.remove(index:-3)", 1);
    test("def x = [1,2,3]; def f = x.remove; f(index:-3)", 1);
    testError("def x = [1,2,3]; x.remove(indexx:-3)", "missing value");
    testError("def x = [1,2,3]; def f = x.remove; f(indexx:-3)", "missing value");
    test("def x = [1,2,3]; x.remove(-3); x", List.of(2,3));
    test("def x = [1,2,3]; x.remove(-2)", 2);
    test("def x = [1,2,3]; x.remove(-2); x", List.of(1,3));
    test("def x = [1,2,3]; x.remove(-1)", 3);
    test("def x = [1,2,3]; x.remove(-1); x", List.of(1,2));
    test("def x = [1,2,3]; def f = x.remove; f(-1); x", List.of(1,2));
    testError("[1,2,3].map{it}.remove(0)", "no such method");
    test("[1,null,2].remove(1)", null);
    test("[1,null,2].remove(1)", null);
    test("[1,null,2].remove(1)", null);
    test("def x = [1,null,2]; x.remove(1)", null);
    test("def x = [1,null,2]; x.remove(1); x", List.of(1,2));
    test("def x = [1,null,2]; x.remove(2)", 2);
    test("def x = [1,null,2]; x.remove(2); x.size()", 2);
    test("def x = [1,null,2]; x.remove(2); x[1]", null);
  }

  @Test public void timestamp() {
    test("timestamp() > 50*356*86400*1000", true); // more than 50 yrs since epoch started in 1970
    test("def f = timestamp; f() > 0", true);
    //    test("def start = nanoTime(); sleep(2); nanoTime() - start > 100000", true);
    //    test("def start = nanoTime(); sleep(2); nanoTime() - start < 3000000", true);
  }

  @Test public void sprintf() {
    test("sprintf('x')", "x");
    test("sprintf('x')", "x");
    test("sprintf('%s','x')", "x");
    test("sprintf('%s%d','x',1)", "x1");
    testError("sprintf(formatx:'%s%d',args:['x',1])", "no such parameter");
    testError("sprintf(format:'%s%d',argsx:['x',1])", "no such parameter");
    testError("def f = sprintf(format:'%s%d',argsx:['x',1])", "no such parameter");
    test("sprintf(format:'%s%d',args:['x',1])", "x1");
    test("sprintf(format:'x1')", "x1");
    test("sprintf(format:'x1',args:[])", "x1");
    test("sprintf(format:'%s',args:['x1'])", "x1");
    testError("sprintf(format:'%s',args:'x1')", "cannot convert argument of type string to parameter type of list");
    test("def f = sprintf; f('x')", "x");
    test("def f = sprintf; f('x')", "x");
    test("def f = sprintf; f('%s','x')", "x");
    test("def f = sprintf; f('%s%d','x',1)", "x1");
    test("def f = sprintf; f(format:'%s%d',args:['x',1])", "x1");
    testError("sprintf('%s%d','x','y')", "bad format string");
    testError("def f = sprintf; f('%s%d','x','y')", "bad format string");
    testError("sprintf('%zs%d','x','y')", "bad format string");
    testError("sprintf()", "missing mandatory argument");
    testError("def f = sprintf; f()", "missing mandatory argument");
    testError("sprintf(format:'%s%d',args:['x','a'])", "bad format string");
  }

  @Test public void asNum() {
    testError("'1'.asNum(1)", "base was 1 but must be at least 2");
    testError("'1'.asNum(37)", "base was 37 but must be no more than 36");
    testError("'az'.asNum()", "invalid character for number with base 10");
    testError("''.asNum()", "empty string cannot be converted");
    test("'1'.asNum()", 1L);
    test("'0'.asNum()", 0L);
    test("'10'.asNum(2)", 2L);
    test("'aa'.asNum(16)", 0xaaL);
    test("'aa'.asNum(base:16)", 0xaaL);
    test("def x = 'aa'; x.asNum(base:16)", 0xaaL);
    test("def x = 'aa'; def f = x.asNum; f(base:16)", 0xaaL);
    testError("'aa'.asNum(basex:16)", "no such parameter");
    testError("def f = 'aa'.asNum; f(basex:16)", "no such parameter");
    testError("def x = 'aa'; def f = x.asNum; f(basex:16)", "no such parameter");
    test("'abcdef'.asNum(16)", 0xABCDEFL);
    test("'7abcdef012345678'.asNum(16)", 0x7abcdef012345678L);
    test("def s = '7abcdef012345678'; s.asNum(16)", 0x7abcdef012345678L);
    test("def s = '7abcdef012345678'; def f = s.asNum; f(16)", 0x7abcdef012345678L);
    testError("'AABCDEF0123456789'.asNum(16)", "number is too large");
  }

  @Test public void toBase() {
    test("1L.toBase(10)", "1");
    test("1.toBase(10)", "1");
    testError("1.0.toBase(10)", "no such method");
    testError("1.0D.toBase(10)", "no such method");
    test("0b1010101.toBase(2)", "1010101");
    test("0b1010101.toBase(16)", "55");
    test("0xAbCdEf012345L.toBase(16)", "ABCDEF012345");
    test("def x = 0xAbCdEf012345L; x.toBase(16)", "ABCDEF012345");
    test("def x = 0xAbCdEf012345L; def f = x.toBase; f(16)", "ABCDEF012345");
    test("-6.toBase(16)", "FFFFFFFA");
    test("def x = -6; x.toBase(16)", "FFFFFFFA");
    test("def x = -6; x.toBase(base:16)", "FFFFFFFA");
    testError("def x = -6; x.toBase(basex:16)", "missing value");
    test("def x = -6; def f = x.toBase; f(base:16)", "FFFFFFFA");
    testError("def x = -6; def f = x.toBase; f(basex:16)", "missing value");
    test("def x = -6; def f = x.toBase; f(16)", "FFFFFFFA");
    test("def x = -6L; def f = x.toBase; f(16)", "FFFFFFFFFFFFFFFA");
    test("def x = -6L; def f = x.toBase; f(16).asNum(16)", -6L);
    test("123456789.toBase(36)", "21I3V9");
    testError("123456789.toBase(37)", "base must be between 2 and 36");
    testError("123456789.toBase(1)", "base must be between 2 and 36");
    test("123456789L.toBase(36)", "21I3V9");
    testError("123456789L.toBase(37)", "base must be between 2 and 36");
    testError("123456789L.toBase(1)", "base must be between 2 and 36");
  }

  @Test public void grouped() {
    test("[].grouped(2)", List.of());
    test("[a:1,b:2,c:3,d:4].grouped(2)", List.of(List.of(List.of("a",1),List.of("b",2)),List.of(List.of("c",3),List.of("d",4))));
    test("[1,2].grouped(0)", List.of(1,2));
    test("[1,2].grouped(1)", List.of(List.of(1),List.of(2)));
    test("[1,2].grouped(2)", List.of(List.of(1,2)));
    test("[1].grouped(2)", List.of(List.of(1)));
    test("[1,2,3,4].grouped(2)", List.of(List.of(1,2),List.of(3,4)));
    test("[1,2,3].grouped(2)", List.of(List.of(1,2),List.of(3)));
    test("[1,2,3,4].map{sleep(0,it)+sleep(0,0)}.grouped(2)", List.of(List.of(1,2),List.of(3,4)));
    test("[1,2,3].map{sleep(0,it)+sleep(0,0)}.grouped(2)", List.of(List.of(1,2),List.of(3)));
    test("[1,2,3,4].map{sleep(0,it)+sleep(0,0)}.grouped(2).map{sleep(0,it)}", List.of(List.of(1,2),List.of(3,4)));
  }

  @Test public void sqrt() {
    test("4.sqrt()", 2);
    test("4.sqrt() + 4.sqrt()", 4);
    test("(123456789L*123456789L).sqrt()", 123456789);
    test("(12345678901.0*12345678901.0).sqrt()", "#12345678901.0");
    test("def f = (12345678901.0*12345678901.0).sqrt; f()", "#12345678901.0");
    test("(0.1234D*0.1234D).sqrt()", 0.1234D);
    test("def f = (0.1234D*0.1234D).sqrt; f()", 0.1234D);
    testError("-1.sqrt()", "square root of negative number");
    testError("-1.0.sqrt()", "square root of negative number");
    testError("-1.0D.sqrt()", "square root of negative number");
    testError("-1L.sqrt()", "square root of negative number");
    test("def x = 4; x.sqrt()", 2);
    test("def x = (123456789L*123456789L); x.sqrt()", 123456789);
    test("def x = (12345678901.0*12345678901.0); x.sqrt()", "#12345678901.0");
    test("def x = (0.1234D*0.1234D); x.sqrt()", 0.1234D);
    test("def x = (0.1234D*0.1234D); def f = x.sqrt; f()", 0.1234D);
    testError("def x = -1; x.sqrt()", "square root of negative number");
    testError("def x = -1.0; x.sqrt()", "square root of negative number");
    testError("def x = -1.0D; x.sqrt()", "square root of negative number");
    testError("def x = -1L; x.sqrt()", "square root of negative number");
  }

  @Test public void sqr() {
    test("2.sqr().sqrt()", 2);
    test("2.sqr() + 2.sqr()", 8);
    test("123456789L.sqr().sqrt()", 123456789);
    test("12345678901.0.sqr().sqrt()", "#12345678901.0");
    test("0.1234D.sqr().sqrt()", 0.1234D);
    test("-1.sqr()", 1);
    test("def f = -1.sqr; f()", 1);
    test("-4.sqr()", 16);
    test("-4L.sqr()", 16L);
    test("def f = -4L.sqr; f()", 16L);
    test("def x = 4; x.sqr()", 16);
    test("def x = -4; x.sqr()", 16);
    test("def x = 4L; x.sqr()", 16L);
    test("def x = -4L; x.sqr()", 16L);
    test("def x = 4.01; x.sqr()", "#16.0801");
    test("def x = -4.01; x.sqr()", "#16.0801");
    test("def x = 4.0D; x.sqr()", 16D);
    test("def x = 4.0D; def f = x.sqr; f()", 16D);
    test("def x = -4.0D; x.sqr()", 16D);
    test("def x = 123456789L; x.sqr()", 123456789L*123456789L);
    test("def x = 123456789.0; x.sqr()", "#" + 123456789L*123456789L + ".00");
    test("def x = 0.1234D; x.sqr()", 0.1234D * 0.1234D);
    test("def f = 0.1234D.sqr; f()", 0.1234D * 0.1234D);
  }

  @Test public void pow() {
    test("4.pow(0)", 1);
    test("4.pow(0.5)", 2);
    test("4.pow(3)", 64);
    test("16.pow(-0.5)", 0.25);
    test("def f = 16.pow; f(-0.5)", 0.25);
    test("-4.pow(-1)", -0.25);
    test("4L.pow(0)", 1);
    test("4L.pow(0.5)", 2);
    test("4L.pow(3)", 64);
    test("def f = 4L.pow; f(3)", 64);
    test("16L.pow(-0.5)", 0.25);
    test("-4L.pow(-1)", -0.25);
    test("4.0.pow(0.5)", 2);
    test("4.0.pow(3)", "#64.000");
    test("16.0.pow(-0.5)", 0.25);
    test("-4.0.pow(0)", "#1");
    test("-4.0.pow(-1)", -0.25);
    test("0.0D.pow(0)", 1);
    test("4.0D.pow(0.5)", 2);
    test("4.0D.pow(3)", 64);
    test("4.0D.pow(3) + 4.0D.pow(3)", 128);
    test("16.0D.pow(-0.5)", 0.25);
    test("def f = 16.0D.pow; f(-0.5)", 0.25);
    test("-4.0D.pow(-1)", -0.25);
    test("(1234567890.0*1234567890.0).pow(0.5)", 1234567890);
    test("(1234567890L*1234567890L).pow(0.5)", 1234567890);
    test("def x = 4; x.pow(0.5)", 2);
    test("def x = 4; x.pow(3)", 64);
    test("def x = 16; x.pow(-0.5)", 0.25);
    test("def x = -4; x.pow(-1)", -0.25);
    test("def x = 4L; x.pow(0.5)", 2);
    test("def x = 4L; x.pow(3)", 64);
    test("def x = 16L; x.pow(-0.5)", 0.25);
    test("def x = 16L; def f = x.pow; f(-0.5)", 0.25);
    test("def x = -4L; x.pow(-1)", -0.25);
    test("def x = 4.0; x.pow(0.5)", 2);
    test("def x = 4.0; x.pow(3)", "#64.000");
    test("def x = 16.0; x.pow(-0.5)", 0.25);
    test("def x = -4.0; x.pow(-1)", -0.25);
    test("def x = 4.0D; x.pow(0.5)", 2);
    test("def x = 4.0D; x.pow(3)", 64);
    test("def x = 16.0D; x.pow(-0.5)", 0.25);
    test("def x = -4.0D; x.pow(-1)", -0.25);
    test("def x = (1234567890.0*1234567890.0); x.pow(0.5)", 1234567890);
    test("def x = (1234567890L*1234567890L); x.pow(0.5)", 1234567890);
    testError("-4.pow(0.5)", "illegal request");
    testError("-4.pow(-0.5)", "illegal request");
    testError("-4.0.pow(0.5)", "illegal request");
    testError("-4.0D.pow(0.5)", "illegal request");
    testError("-4L.pow(0.5)", "illegal request");
  }

  @Test public void asyncCollectionClosures() {
    test("def x = [1,2,3].map{ sleep(1,it) * sleep(1,it) }; x.size()", 3);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }.size()", 3);
    test("def f = [1,2,3].map; f{ sleep(1,it) * sleep(1,it) }.size()", 3);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[1,2,3].map{ sleep(1,it) }.map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[a:1,b:2,c:3].map{ sleep(1,it) }.map{ sleep(sleep(1,1),it[1]) }.map{ sleep(1,it) * sleep(1,it) }", List.of(1,4,9));
    test("[a:1,b:2,c:3].map{ sleep(1,it) }.map{ \"${sleep(1,it[0])}:${sleep(1,it[1])*sleep(1,it[1])}\" }", List.of("a:1","b:4","c:9"));
    test("[1,2,3,4].filter{ sleep(1,true) }", List.of(1,2,3,4));
    test("[1,2,3,4].filter{ sleep(1,it) % 2 }", List.of(1,3));
    test("[1,2,3,4].filter{ sleep(1,it) % sleep(1,2) }", List.of(1,3));
    test("[1,2,3,4].filter{ sleep(1,true) }.filter{ sleep(1,it) % sleep(1,2) }", List.of(1,3));
    test("def x = 0; [1,2,3,4].each{ x += sleep(1,it) + sleep(1,it) }; x", 20);
    test("def x = 0; [1,2,3,4].map{ sleep(1,it) + sleep(1,it) }.each{ x += sleep(1,it) + sleep(1,it) }; x", 40);
    test("[1,2,3,4].collect{ sleep(1,it) }", List.of(1,2,3,4));
    test("[1,2,3,4].collect{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }", List.of(2,4,6,8));
    test("[1,2,3,4].collect{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }.size()", 4);
    test("def x = 0; def c = [1,2,3,4].collect{ sleep(1,it) }; c.each{ x += sleep(1,it) }; x", 10);
    test("def x = 0; [1,2,3,4].collect{ sleep(1,it) }.each{ x += sleep(1,it) }; x", 10);
    test("[1,2,3,4].map{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }", List.of(2,4,6,8));
    test("def x = 0; [1,2,3,4].map{ sleep(1,it) }.collect{ sleep(1,it) + sleep(1,it) }.each{ x += sleep(1,it) }; x", 20);
    test("[1,2,3].map{ sleep(1,it) * sleep(1,it) }.filter{ sleep(1,it) > 3 }.collect{ sleep(1,it) + sleep(1,it) }", List.of(8,18));
    test("[5,4,1,3,2].sort{ a,b -> sleep(1,a) <=> b }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ a,b -> sleep(1,a) <=> sleep(1,b) }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ sleep(1,it[0]) <=> sleep(1,it[1]) }", List.of(1,2,3,4,5));
    test("[5,4,1,3,2].sort{ sleep(1,it[1]) <=> sleep(1,it[0]) }", List.of(5,4,3,2,1));
    test("[4,2,1,5,3].map{ sleep(1,it) * sleep(1,it) }.collect{ it+it }", List.of(32,8,2,50,18));
    test("[4,2,1,5,3].map{ sleep(1,it) * sleep(1,it) }.sort{ sleep(1,it[1]) <=> sleep(1,it[0]) }", List.of(25,16,9,4,1));
    test("sleep(0,[1,2,3].map{sleep(0,it)*sleep(0,it)})", List.of(1,4,9));
    test("def f = [1,2,3].map; sleep(0,f{sleep(0,it)*sleep(0,it)})", List.of(1,4,9));
    testError("def f(x){sleep(0,null) as int}; [1].map(f)", "null value cannot be coerced to int");
  }

  @Test public void toStringTest() {
    testError("null.toString()", "tried to invoke method on null");
    test("1.toString()", "1");
    test("def x = 1; x.toString()", "1");
    test("def x = 1; def f = x.toString; f()", "1");
    test("[].toString()", "[]");
    test("[1,2,3].toString()", "[1, 2, 3]");
    test("[:].toString()", "[:]");
    test("[a:1].toString()", "[a:1]");
    test("[a:1,b:2].toString()", "[a:1, b:2]");
    test("['123':1,b:2].toString()", "['123':1, b:2]");
    test("['a b c':1,b:2].toString()", "['a b c':1, b:2]");
    test("[a:1,b:['x','y',[c:3]]].toString()", "[a:1, b:['x', 'y', [c:3]]]");
    test("1D.toString()", "1.0");
    test("1.0D.toString()", "1.0");
    test("1.0.toString()", "1.0");
    test("12345678901234L.toString()", "12345678901234");
    test("def x = []; x.toString()", "[]");
    test("def x = [1,2,3]; x.toString()", "[1, 2, 3]");
    test("def x = [:]; x.toString()", "[:]");
    test("def x = [a:1]; x.toString()", "[a:1]");
    test("def x = [a:1,b:2]; x.toString()", "[a:1, b:2]");
    test("def x = ['123':1,b:2]; x.toString()", "['123':1, b:2]");
    test("def x = ['a b c':1,b:2]; x.toString()", "['a b c':1, b:2]");
    test("def x = [a:1,b:['x','y',[c:3]]]; x.toString()", "[a:1, b:['x', 'y', [c:3]]]");
    test("def x = 1D; x.toString()", "1.0");
    test("def x = 1.0D; x.toString()", "1.0");
    test("def x = 1.0; x.toString()", "1.0");
    test("def x = 12345678901234L; x.toString()", "12345678901234");
    test("def x = ''; [a:1].each{ x += it.toString() }; x", "['a', 1]");
    test("def f(x) {x*x}; f.toString() =~ /Function@(\\d+)/", true);
    test("def f = { x -> x*x}; f.toString() =~ /Function@(\\d+)/", true);
    test("def x = 'abc'; def f = x.toString; f()", "abc");
  }

  @Test public void toJson() {
    test("0.toJson()", "0");
    test("1.toJson()", "1");
    test("1L.toJson()", "1");
    test("-1.toJson()", "-1");
    test("-1L.toJson()", "-1");
    test("" + Long.MAX_VALUE + "L.toJson()", "" + Long.MAX_VALUE + "");
    test("'abc'.toJson()", "\"abc\"");
    test("1.234.toJson()", "1.234");
    test("[].toJson()", "[]");
    test("[:].toJson()", "{}");
    test("[1,2,3].toJson()", "[1,2,3]");
    test("[a:1,b:2,c:[1,2,3]].toJson()", "{\"a\":1,\"b\":2,\"c\":[1,2,3]}");
    test("class X { int i = 1 }; new X().toJson()", "{\"i\":1}");
    test("class X { String s = null }; new X().toJson()", "{\"s\":null}");
    test("class X { int i = 1, j = 2; def m = [a:1] }; new X().toJson()", "{\"i\":1,\"j\":2,\"m\":{\"a\":1}}");
    test("class X { int i = 1; long j = 2; double d = 1.23D; var b = 1.2345; var s = 'abc'; def m = [a:1]; List list =[1,2,3]; Map m2=[a:1,b:2] }; new X().toJson()", "{\"i\":1,\"j\":2,\"d\":1.23,\"b\":1.2345,\"s\":\"abc\",\"m\":{\"a\":1},\"list\":[1,2,3],\"m2\":{\"a\":1,\"b\":2}}");
    test("class X { int i = 1; long j = 2; double d = 1.23D; Decimal b = 1.2345; String s = null; def m = [a:1]; List list =[1,2,3]; Map m2=[a:1,b:2] }; new X().toJson()", "{\"i\":1,\"j\":2,\"d\":1.23,\"b\":1.2345,\"s\":null,\"m\":{\"a\":1},\"list\":[1,2,3],\"m2\":{\"a\":1,\"b\":2}}");
    test("class X { List l = []; int i = 1; long j = 2; double d = 1.23D; var b = 1.2345; var s = 'abc'; def m = [a:1]; List list =[1,2,3]; Map m2=[a:1,b:2] }; new X().toJson()", "{\"l\":[],\"i\":1,\"j\":2,\"d\":1.23,\"b\":1.2345,\"s\":\"abc\",\"m\":{\"a\":1},\"list\":[1,2,3],\"m2\":{\"a\":1,\"b\":2}}");
    test("class X { int i = 1, j = 2; def m = [a:1] }; [x:new X()].toJson()", "{\"x\":{\"i\":1,\"j\":2,\"m\":{\"a\":1}}}");
    test("class X { int i = 1, j = 2; def m = [a:1]; X x = null }; [x:new X(x:new X())].toJson()", "{\"x\":{\"i\":1,\"j\":2,\"m\":{\"a\":1},\"x\":{\"i\":1,\"j\":2,\"m\":{\"a\":1},\"x\":null}}}");
    testError("class X { int i = 1, j = 2; def m = [a:1]; X x = null }; def x = new X(); x.x = x; x.toJson()", "StackOverflow");
    test("127.map{ it.asChar() }.join().toJson()", "\"\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b\\t\\n\\u000B\\f\\r\\u000E\\u000F\\u0010\\u0011\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017\\u0018\\u0019\\u001A\\u001B\\u001C\\u001D\\u001E\\u001F !\\\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\"");
    test("[a:null,b:123].toJson()", "{\"a\":null,\"b\":123}");
    test("class X { int i = 3; X x = null }; new X().toJson()", "{\"i\":3,\"x\":null}");
    test("class X { int i = 3; Y y = new Y() }; class Y { X x = null }; new X().toJson()", "{\"i\":3,\"y\":{\"x\":null}}");
  }

  @Test public void fromJson() {
    test("\"[1]\".fromJson()", List.of(1));
    test("'\"\\\\\\\\\"'.fromJson()", "\\");
    test("'\"\\\\\"\"'.fromJson()", "\"");
    testError("'\"'.fromJson()", "unterminated string");
    test("'\"a\"'.fromJson()", "a");
    test("'1'.fromJson()", 1);
    test("'1.2'.fromJson()", "#1.2");
    test("'-1.2'.fromJson()", "#-1.2");
    test("' -1.2 '.fromJson()", "#-1.2");
    testError("'-'.fromJson()", "end of json reading negative number");
    test("'-1'.fromJson()", -1);
    test("'" + Integer.MAX_VALUE + "'.fromJson()", Integer.MAX_VALUE);
    test("'" + Long.MAX_VALUE + "'.fromJson()", Long.MAX_VALUE);
    test("'-" + Long.MAX_VALUE + "'.fromJson()", -Long.MAX_VALUE);
    test("'" + Long.MAX_VALUE + "1'.fromJson().toString()", "" + Long.MAX_VALUE + "1");
    test("'" + Long.MAX_VALUE + "1'.fromJson()", "#" + Long.MAX_VALUE + "1");
    test("'-" + Long.MAX_VALUE + "1'.fromJson().toString()", "-" + Long.MAX_VALUE + "1");
    test("'-" + Long.MAX_VALUE + "1'.fromJson()", "#-" + Long.MAX_VALUE + "1");
    test("'-1.23456e7'.fromJson()", "#-1.23456e7");
    testError("'-1.234e56e7'.fromJson()", "illegally formatted number");
    test("'null'.fromJson()", null);
    test("'true'.fromJson()", true);
    test("'false'.fromJson()", false);
    test("'\"\"'.fromJson()", "");
    test("'\"abc\"'.fromJson()", "abc");
    test("'\"ab\\nc\"'.fromJson()", "ab\nc");
    test("'[]'.fromJson()", List.of());
    test("'[1.2,2.3,-1.234,4]'.fromJson()", List.of(new BigDecimal("1.2"), new BigDecimal("2.3"), new BigDecimal("-1.234"), 4));
    test("'[null]'.fromJson()", new ArrayList() {{ add(null); }});
    test("'[\"a\",\"b\",\"c\"]'.fromJson()", List.of("a", "b", "c"));
    test("'{\"a\":[1,2,3],\"b\":2,\"c\":{\"x\":1234}}'.fromJson()", Map.of("a", List.of(1, 2, 3), "b", 2, "c", Map.of("x", 1234)));
    test("'{\"a\":[1,[4,5,6,[2]],3],\"b\":2,\"c\":{\"x\":1234}}'.fromJson()", Map.of("a", List.of(1, List.of(4,5,6,List.of(2)), 3), "b", 2, "c", Map.of("x", 1234)));
    test("' 1'.fromJson()", 1);
    test("'1 '.fromJson()", 1);
    testError("' -'.fromJson()", "end of json reading negative number");
    test("' -1 '.fromJson()", -1);
    test("' " + Long.MAX_VALUE + " '.fromJson()", Long.MAX_VALUE);
    test("' -" + Long.MAX_VALUE + " '.fromJson()", -Long.MAX_VALUE);
    test("' " + Long.MAX_VALUE + "1 '.fromJson().toString()", "" + Long.MAX_VALUE + "1");
    test("' " + Long.MAX_VALUE + "1 '.fromJson()", "#" + Long.MAX_VALUE + "1");
    test("' -" + Long.MAX_VALUE + "1 '.fromJson().toString()", "-" + Long.MAX_VALUE + "1");
    test("' -" + Long.MAX_VALUE + "1 '.fromJson()", "#-" + Long.MAX_VALUE + "1");
    test("' -1.23456e7'.fromJson()", "#-1.23456e7");
    testError("'-1.234e56e7 '.fromJson()", "illegally formatted number");
    test("' null'.fromJson()", null);
    test("'null '.fromJson()", null);
    test("' true'.fromJson()", true);
    test("' false '.fromJson()", false);
    test("' \"\" '.fromJson()", "");
    test("' \"abc\" '.fromJson()", "abc");
    test("'\"ab\\nc\" '.fromJson()", "ab\nc");
    test("'[ ]'.fromJson()", List.of());
    test("'[ ] '.fromJson()", List.of());
    test("' [] '.fromJson()", List.of());
    test("' {} '.fromJson()", Map.of());
    test("' { } '.fromJson()", Map.of());
    test("' [1.2 , 2.3,-1.234 , 4 ] '.fromJson()", List.of(new BigDecimal("1.2"), new BigDecimal("2.3"), new BigDecimal("-1.234"), 4));
    test("'[ null]'.fromJson()", new ArrayList() {{ add(null); }});
    test("'[\"a\", \"b\",\"c\"]'.fromJson()", List.of("a", "b", "c"));
    test("' { \"a\" : [1 , 2, 3 ] , \"b\" :2 , \"c\": { \"x\":1234 } } '.fromJson()", Map.of("a", List.of(1, 2, 3), "b", 2, "c", Map.of("x", 1234)));
    testError("' { \"a\" : [1 , 2, 3 ] , \"b\" :2 , \"a\": { \"x\":1234 } } '.fromJson()", "duplicate field");
    test("'\"\\\\u0001\"'.fromJson().toJson()", "\"\\u0001\"");
    test("def s = 0x5c.asChar(); s.toJson().fromJson() == s", true);
    test("def s = 127.map{ it.asChar() }.join(); s.toJson().fromJson() == s", true);
  }

  @Test public void classFromJson() {
    useAsyncDecorator = false;
    test("class X { int i; }; X.fromJson('{\"i\":3}').i", 3);
    test("class X { int i; long j }; X x = X.fromJson('{\"i\":3,\"j\":4}'); x.i + x.j", 7L);
    test("class X { int i; long j }; X x = X.fromJson('{\"i\":3 , \"j\" : 4 }'); x.i + x.j", 7L);
    testError("class X { int i }; X.fromJson('{\"i\":3,\"i\":4}').i", "appears multiple times");
    testError("class X { int i; long j }; X.fromJson('{\"i\":3,\"j\":5,\"i\":4}').i", "field 'i'");
    testError("class X { int i; X x }; X.fromJson('{\"i\":3}').i", "missing field(s): x");
    test("class X { int i; Y yval }; class Y { String s }; X.fromJson('{\"i\":3,\"yval\":{\"s\":\"abc\"}}').yval.s", "abc");
    test("class X { int i; long j; String s; double d; Decimal dd; boolean b1; boolean b2; Map m; List l }; X.fromJson('''{'l':[1,2,3],'m':{'a':123,'b':4},'b2':false,'b1':true,'dd':1.234,'d':3.456,'s':'abc','j':123456789123456789,'i':-1234}''' =~ s/'/\"/rg).toString()", "[i:-1234, j:123456789123456789, s:'abc', d:3.456, dd:1.234, b1:true, b2:false, m:[a:123, b:4], l:[1, 2, 3]]");
    testError("class X { int i1,i2,i3,i4,i5,i6,i7,i8,i9,i10,i11,i12,i13,i14,i15,i16,i17,i18,i19,i20,i21,i22,i23,i24,i25,i26,i27,i28,i29,i30,i31,i32,i33,i34,i35,i36,i37,i38,i39,i40 }\n" +
              "X.fromJson('''{'i1':1,'i2':2,'i3':3,'i4':4,'i5':5,'i6':6,'i7':7,'i8':8,'i9':9,'i10':10,'i11':11,'i12':12,'i13':13,'i14':14,'i15':15,'i16':16,'i17':17,'i18':18,'i19':19,'i20':1,'i21':1,'i22':1,'i23':1,'i24':1,'i25':1,'i26':1,'i27':1,'i28':1,'i29':1,'i30':1,'i31':1,'i32':1,'i33':1,'i34':1,'i35':1,'i37':1,'i38':1,'i39':1}''' =~ s/'/\"/rg)",
              "missing field(s): i36,i40");
    testError("class X { int i1,i2,i3,i4,i5,i6,i7,i8,i9,i10,i11,i12,i13,i14,i15,i16,i17,i18,i19,i20,i21,i22,i23,i24,i25,i26,i27,i28,i29,i30,i31,i32,i33,i34,i35,i36,i37,i38,i39,i40 }\n" +
              "X.fromJson('''{'i1':1,'i2':2,'i3':3,'i4':4,'i5':5,'i6':6,'i7':7,'i8':8,'i9':9,'i10':10,'i11':11,'i12':12,'i13':13,'i14':14,'i15':15,'i16':16,'i17':17,'i18':18,'i19':19,'i20':1,'i21':1,'i22':1,'i23':1,'i24':1,'i25':1,'i26':1,'i27':1,'i28':1,'i29':1,'i30':1,'i31':1,'i33':1,'i34':1,'i35':1,'i37':1,'i38':1,'i39':1}''' =~ s/'/\"/rg)",
              "missing field(s): i32");
    test("class X { int i; Y y }; class Y { Map m }; X.fromJson('{\"i\":3,\"y\":{\"m\":null}}').i", 3);
    test("class X { int i; Y y }; class Y { List a }; X.fromJson('{\"i\":3,\"y\":{\"a\":null}}').i", 3);
    test("class X { int i; X x }; X.fromJson('{\"i\":3,\"x\":null}').i", 3);
    test("class X { int i; String x }; X.fromJson('{\"i\":3,\"x\":null}').i", 3);
    test("class X { int i; String x }; X.fromJson('{\"i\":3,\"x\":null}').x", null);
    test("class X { int i; Decimal x }; X.fromJson('{\"i\":3,\"x\":null}').i", 3);
    test("class X { int i; Decimal x }; X.fromJson('{\"i\":3,\"x\":null}').x", null);
    testError("class X { int i; int x }; X.fromJson('{\"i\":3,\"x\":null}').i", "integer value cannot be null");
    testError("class X { int i; long x }; X.fromJson('{\"i\":3,\"x\":null}').i", "long value cannot be null");
    testError("class X { int i; double x }; X.fromJson('{\"i\":3,\"x\":null}').i", "double value cannot be null");
    test("class X { int i; X x = null }; X.fromJson('{\"i\":3}').i", 3);
    test("class X { int i; }; X.fromJson('null')", null);
    test("class X { int i; X x = sleep(0,null) }; X.fromJson('{\"i\":3}').i", 3);
    test("class X { int i; String x }; def f = X.fromJson; f('{\"i\":3,\"x\":null}').i", 3);

    // Test with fields that map to same hashCode:
    test("class X { int Aa; String BB }; X.fromJson('{\"Aa\":3,\"BB\":null}').Aa", 3);
    test("class X { int Aa; String BB }; def x = X.fromJson('{\"Aa\":3,\"BB\":\"xxx\"}'); x.BB + x.Aa", "xxx3");
  }

  @Test public void arrayFieldsToJson() {
    test("class X { int[] a = new int[4] }; new X().toJson()", "{\"a\":[0,0,0,0]}");
    test("class X { int[] a = new int[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,0,0,4]}");
    test("class X { long[] a = new long[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,0,0,4]}");
    test("class X { int[][] a = new int[4][2] }; def x = new X(); x.a[0][1] = 1; x.a[3][0] = 4; x.toJson()", "{\"a\":[[0,1],[0,0],[0,0],[4,0]]}");
    test("class X { int[][] a = new int[4][] }; def x = new X(); x.a[0] = new int[3]; x.a[0][1] = 1; x.a[3] = new int[0]; x.a[1] = new int[1]; x.a[1][0] = 4; x.toJson()", "{\"a\":[[0,1,0],[4],null,[]]}");
    test("class X { long[] a = new long[4] }; new X().toJson()", "{\"a\":[0,0,0,0]}");
    test("class X { long[] a = new long[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,0,0,4]}");
    test("class X { long[] a = new long[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,0,0,4]}");
    test("class X { long[][] a = new long[4][2] }; def x = new X(); x.a[0][1] = 1; x.a[3][0] = 4; x.toJson()", "{\"a\":[[0,1],[0,0],[0,0],[4,0]]}");
    test("class X { long[][] a = new long[4][] }; def x = new X(); x.a[0] = new long[3]; x.a[0][1] = 1; x.a[3] = new long[0]; x.a[1] = new long[1]; x.a[1][0] = 4; x.toJson()", "{\"a\":[[0,1,0],[4],null,[]]}");
    test("class X { double[] a = new double[4] }; new X().toJson()", "{\"a\":[0.0,0.0,0.0,0.0]}");
    test("class X { double[] a = new double[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1.0,0.0,0.0,4.0]}");
    test("class X { double[] a = new double[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1.0,0.0,0.0,4.0]}");
    test("class X { double[][] a = new double[4][2] }; def x = new X(); x.a[0][1] = 1; x.a[3][0] = 4; x.toJson()", "{\"a\":[[0.0,1.0],[0.0,0.0],[0.0,0.0],[4.0,0.0]]}");
    test("class X { double[][] a = new double[4][] }; def x = new X(); x.a[0] = new double[3]; x.a[0][1] = 1; x.a[3] = new double[0]; x.a[1] = new double[1]; x.a[1][0] = 4; x.toJson()", "{\"a\":[[0.0,1.0,0.0],[4.0],null,[]]}");
    test("class X { Decimal[] a = new Decimal[4] }; new X().toJson()", "{\"a\":[null,null,null,null]}");
    test("class X { Decimal[] a = new Decimal[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,null,null,4]}");
    test("class X { Decimal[] a = new Decimal[4] }; def x = new X(); x.a[0] = 1; x.a[3] = 4; x.toJson()", "{\"a\":[1,null,null,4]}");
    test("class X { Decimal[][] a = new Decimal[4][2] }; def x = new X(); x.a[0][1] = 1; x.a[3][0] = 4; x.toJson()", "{\"a\":[[null,1],[null,null],[null,null],[4,null]]}");
    test("class X { Decimal[][] a = new Decimal[4][] }; def x = new X(); x.a[0] = new Decimal[3]; x.a[0][1] = 1; x.a[3] = new Decimal[0]; x.a[1] = new Decimal[1]; x.a[1][0] = 4; x.toJson()", "{\"a\":[[null,1,null],[4],null,[]]}");
    test("class X { String[] a = new String[4] }; new X().toJson()", "{\"a\":[null,null,null,null]}");
    test("class X { String[] a = new String[4] }; def x = new X(); x.a[0] = \"123\"; x.a[3] = \"abc\"; x.toJson()", "{\"a\":[\"123\",null,null,\"abc\"]}");
    test("class X { String[] a = new String[4] }; def x = new X(); x.a[0] = \"123\"; x.a[3] = \"abc\"; x.toJson()", "{\"a\":[\"123\",null,null,\"abc\"]}");
    test("class X { String[][] a = new String[4][2] }; def x = new X(); x.a[0][1] = \"123\"; x.a[3][0] = \"abc\"; x.toJson()", "{\"a\":[[null,\"123\"],[null,null],[null,null],[\"abc\",null]]}");
    test("class X { String[][] a = new String[4][] }; def x = new X(); x.a[0] = new String[3]; x.a[0][1] = \"123\"; x.a[3] = new String[0]; x.a[1] = new String[1]; x.a[1][0] = \"abc\"; x.toJson()", "{\"a\":[[null,\"123\",null],[\"abc\"],null,[]]}");
    test("class X { Object[] a = new Object[4] }; new X().toJson()", "{\"a\":[null,null,null,null]}");
    test("class X { Object[] a = new Object[4] }; def x = new X(); x.a[0] = \"123\"; x.a[3] = \"abc\"; x.toJson()", "{\"a\":[\"123\",null,null,\"abc\"]}");
    test("class X { Object[] a = new Object[4] }; def x = new X(); x.a[0] = \"123\"; x.a[3] = \"abc\"; x.toJson()", "{\"a\":[\"123\",null,null,\"abc\"]}");
    test("class X { Object[][] a = new Object[4][2] }; def x = new X(); x.a[0][1] = \"123\"; x.a[3][0] = \"abc\"; x.toJson()", "{\"a\":[[null,\"123\"],[null,null],[null,null],[\"abc\",null]]}");
    test("class X { Object[][] a = new Object[4][] }; def x = new X(); x.a[0] = new Object[3]; x.a[0][1] = \"123\"; x.a[3] = new Object[0]; x.a[1] = new Object[1]; x.a[1][0] = \"abc\"; x.toJson()", "{\"a\":[[null,\"123\",null],[\"abc\"],null,[]]}");
    test("class X { Object[][] a = new Object[4][] }; def x = new X(); x.a[0] = new String[3]; x.a[0][1] = \"123\"; x.a[3] = new Object[0]; x.a[1] = new String[1]; x.a[1][0] = \"abc\"; x.toJson()", "{\"a\":[[null,\"123\",null],[\"abc\"],null,[]]}");
    test("class X { Object[][] a = new Object[4][] }; def x = new X(); x.a[0] = new String[3]; x.a[0][1] = \"123\"; x.a[3] = new Decimal[0]; x.a[1] = new Decimal[1]; x.a[1][0] = 1234512345123451234L; x.toJson()", "{\"a\":[[null,\"123\",null],[1234512345123451234],null,[]]}");
  }

  @Test public void arrayFieldsFromJson() {
    useAsyncDecorator = false;
    test("class X { boolean[] a }; X.fromJson('{\"a\":[false,true,true]}').a", new boolean[]{false,true,true});
    test("class X { int[] a }; X.fromJson('{\"a\":[1,2,3]}').a", new int[]{1,2,3});
    test("class X { int[] a }; X.fromJson('{\"a\":[]}').a", new int[0]);
    test("class X { int[] Aa,BB }; X.fromJson('{\"BB\":[1,2,3],\"Aa\":[4,5,6]}').Aa", new int[]{4,5,6});
    test("class X { int[] Aa,BB }; X.fromJson('{\"BB\":[1,2,3],\"Aa\":[4,5,6]}').BB", new int[]{1,2,3});
    test("class X { int[] a }; X.fromJson('{\"a\":null}').a", null);
    test("class X { long[] a }; X.fromJson('{\"a\":[1,2,3]}').a", new long[]{1,2,3});
    test("class X { double[] a }; X.fromJson('{\"a\":[1,2,3]}').a", new double[]{1,2,3});
    test("class X { Decimal[] a }; X.fromJson('{\"a\":[1,2,3]}').a", new BigDecimal[]{new BigDecimal("1"),new BigDecimal("2"),new BigDecimal("3")});
    test("class X { String[] a }; X.fromJson('{\"a\":[\"1\",\"2\"]}').a", new String[]{"1","2"});
    test("class X { Object[] a }; X.fromJson('{\"a\":[\"1\",\"2\"]}').a", new Object[]{"1","2"});
    test("class X { Object[] a }; X.fromJson('{\"a\":[1,\"2\"]}').a", new Object[]{1,"2"});
    test("class X { int[] a }; X.fromJson('{\"a\":[]}').a", new int[0]);
    test("class X { int[][] a }; X.fromJson('{\"a\":[[1],[2,3],[]]}').a.toString()", "[[1], [2, 3], []]");
    testError("class X { int[] a }; X.fromJson('{\"a\":[1,\"abc\",3]}').a", "invalid character");
    testError("class X { boolean[] a }; X.fromJson('{\"a\":[0,1,2]}').a", "invalid json");
    test("class X { String a }; class Y { X[] x }; def y = Y.fromJson('{\"x\":[{\"a\":\"1\"},{\"a\":\"2\"}]}'); y.x[0] instanceof X", true);
    test("class X { String a }; class Y { X[] x }; def y = Y.fromJson('{\"x\":[{\"a\":\"1\"},{\"a\":\"2\"}]}'); y.toString()", "[x:[[a:'1'], [a:'2']]]");
    test("class X { String a }; class Y { X[] x }; def y = Y.fromJson(' { \"x\" : [ { \"a\" : \"1\" } , { \"a\":\"2\" } ] } '); y.toString()", "[x:[[a:'1'], [a:'2']]]");
  }

  @Test public void instanceArrayToJson() {
    //fail("not implemented");
  }

  @Test public void instanceArrayFromJson() {
    //fail("not implemented");
  }

  @Test public void uuid() {
    test("uuid().size()", 36);
    test("uuid() != uuid()", true);
  }

  @Test public void _checkpoint() throws ExecutionException, InterruptedException {
    Map<UUID,byte[]> checkpoints = new HashMap<>();
    jactlEnv = new DefaultEnv() {
      @Override public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
        checkpoints.put(id, checkpoint);
        resumer.accept(result);
      }
    };
    JactlContext context = getJactlContext();
    JactlScript script = Jactl.compileScript("_checkpoint(123)", Map.of(), context);
    assertEquals(123, script.runSync(Map.of()));
    assertEquals(1, checkpoints.size());
    CompletableFuture result = new CompletableFuture();
    context.recoverCheckpoint(checkpoints.values().iterator().next(), value -> result.complete(value));
    assertEquals(123, result.get());
  }

  private void checkpointTest(String source, Object commitExpected, Object recoverExpected) throws InterruptedException, ExecutionException {
    Map<UUID,List<Pair<Integer,byte[]>>> checkpoints = new HashMap<>();
    jactlEnv = new DefaultEnv() {
      @Override public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
        checkpoints.putIfAbsent(id, new ArrayList<>());
        var list = checkpoints.get(id);
        int nextId = list.size() == 0 ? 1 : list.get(list.size() - 1).first + 1;
        if (nextId != checkpointId) {
          resumer.accept(new RuntimeError("Checkpoint id (" + checkpointId + ") does not match expected value of " + nextId, source, offset));
        }
        else {
          list.add(Pair.create(checkpointId, checkpoint));
          resumer.accept(result);
        }
      }
    };
    JactlContext context = getJactlContext();
    JactlScript script = Jactl.compileScript(source, Map.of(), context);
    assertEquals(commitExpected, script.runSync(Map.of()));
    assertEquals(1, checkpoints.size());
    var key  = checkpoints.keySet().iterator().next();
    var list = checkpoints.get(key);
    for (int i = 0; i < list.size(); i++) {
      var pair = list.get(i);
      checkpoints.clear();
      checkpoints.put(key, new ArrayList<>() {{ add(pair); }});
      CompletableFuture future = new CompletableFuture();
      context.recoverCheckpoint(pair.second, value -> future.complete(value));
      Object result = future.get();
      if (result instanceof Throwable) {
        ((Throwable) result).printStackTrace();
        fail(((Throwable) result).getMessage());
      }
      if (i == list.size() - 1) {
        assertEquals(recoverExpected, result);
      }
      else {
        assertEquals(commitExpected, result);
      }
    }
    if (list.size() > 1) {
      checkpoints.put(key, list);
      CompletableFuture result = new CompletableFuture();
      context.recoverCheckpoint(list.get(0).second, value -> result.complete(value));
      Object err = result.get();
      assertTrue(err instanceof RuntimeError && ((RuntimeError) err).getMessage().contains("does not match expected"));
    }
  }

  @Test public void checkpoint() throws ExecutionException, InterruptedException {
    checkpointTest("checkpoint{ 1 }{ 2 }", 1, 2);
    checkpointTest("checkpoint{ [1,2,3] }{ 'abc' }", List.of(1,2,3), "abc");
    checkpointTest("checkpoint{1}{2}; checkpoint{3}{4}", 3, 4);
    checkpointTest("checkpoint{1}{2}; checkpoint{5}{6}; checkpoint{3}{4}", 3, 4);
    checkpointTest("checkpoint{1}{2}; checkpoint{sleep(0,5)}{sleep(0,6)}; checkpoint{sleep(0,3)}{sleep(0,4)}", 3, 4);
    checkpointTest("10.each{ checkpoint{it}{it+1} }; checkpoint{3}{4}", 3, 4);
    checkpointTest("10.each{ checkpoint{sleep(0,it)}{sleep(0,it+1)} }; checkpoint{sleep(0,3)}{sleep(0,4)}", 3, 4);
    checkpointTest("checkpoint{ [1,2,3].map{ sleep(0,it) } }{ 'abc'.map{ sleep(0,it) }.join() }", List.of(1,2,3), "abc");
    checkpointTest("def x = checkpoint{ [1,2,3] }{ 'abc' }; checkpoint{ [1,2,3] }{ 'abc' }", List.of(1,2,3), "abc");
    checkpointTest("def x = checkpoint{ [1,2,3] }{ 'abc' }; checkpoint{ sleep(0,[1,2,3]) }{ sleep(0,'abc') }", List.of(1,2,3), "abc");
    checkpointTest("def x = checkpoint{ [1,2,3].map{ sleep(0,it) } }{ 'abc'.map{ sleep(0,it) }.join() }; checkpoint{ sleep(0,[1,2,3]) }{ sleep(0,'abc') }", List.of(1,2,3), "abc");
  }

  @Test public void functionWithDefaults() {
    try {
      Jactl.function().name("testFunc").param("arg", 10).impl(BuiltinFunctionTests.class, "testFunc").register();
      test("testFunc(3)", 9);
      test("testFunc()", 100);
    }
    finally {
      Jactl.deregister("testFunc");
      testFuncData = null;
    }
  }

  @Test public void methodWithDefaults() {
    try {
      Jactl.method(INT).name("testMethod").param("arg", 10).impl(BuiltinFunctionTests.class, "testMethod").register();
      test("3.testMethod(3)", 9);
      test("3.testMethod()", 30);
    }
    finally {
      Jactl.deregister("testFunc");
      testFuncData = null;
    }
  }

  @Test public void varArgs() {
    try {
      Jactl.function().name("varArgsFunc").param("str").param("i").param("args", new Object[0]).impl(BuiltinFunctionTests.class, "varArgsFunc").register();
      Jactl.function().name("varArgsFuncAsync").param("str").param("i").param("args", new Object[0]).impl(BuiltinFunctionTests.class, "varArgsFuncAsync").register();
      Jactl.method(ANY).name("varArgsMethod").param("str").param("i").param("args", new Object[0]).impl(BuiltinFunctionTests.class, "varArgsMethod").register();
      Jactl.method(ANY).name("varArgsMethodAsync").param("str").param("i").param("args", new Object[0]).impl(BuiltinFunctionTests.class, "varArgsMethodAsync").register();

      test("varArgsFunc('abc', 1L)", "abc1");
      test("varArgsFunc('abc', 1.1234)", "abc1");
      test("varArgsFunc('abc', 1, 2, 'b', [1,2,3])", "abc12:b:[1, 2, 3]");
      test("def x = 'abc'; def y = 1L; varArgsFunc(x, y)", "abc1");
      test("def x = 'abc'; def y = 1.234; varArgsFunc(x, y)", "abc1");
      test("varArgsFunc(str:'abc', i:1L)", "abc1");
      test("varArgsFunc(str:'abc', i:1.1234)", "abc1");
      test("varArgsFunc(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "abc12:b:[1, 2, 3]");
      test("def f = varArgsFunc; f('abc', 1L)", "abc1");
      test("def f = varArgsFunc; f('abc', 1.1234)", "abc1");
      test("def f = varArgsFunc; f('abc', 1, 2, 'b', [1,2,3])", "abc12:b:[1, 2, 3]");
      test("def x = 'abc'; def y = 1L; def f = varArgsFunc; f(x, y)", "abc1");
      test("def x = 'abc'; def y = 1.234; def f = varArgsFunc; f(x, y)", "abc1");
      test("def f = varArgsFunc; f(str:'abc', i:1L)", "abc1");
      test("def f = varArgsFunc; f(str:'abc', i:1.1234)", "abc1");
      test("def f = varArgsFunc; f(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "abc12:b:[1, 2, 3]");

      test("varArgsFuncAsync('abc', 1L)", "abc1");
      test("varArgsFuncAsync('abc', 1.1234)", "abc1");
      test("varArgsFuncAsync('abc', 1, 2, 'b', [1,2,3])", "abc12:b:[1, 2, 3]");
      test("def x = 'abc'; def y = 1L; varArgsFuncAsync(x, y)", "abc1");
      test("def x = 'abc'; def y = 1.234; varArgsFuncAsync(x, y)", "abc1");
      test("varArgsFuncAsync(str:'abc', i:1L)", "abc1");
      test("varArgsFuncAsync(str:'abc', i:1.1234)", "abc1");
      test("varArgsFuncAsync(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "abc12:b:[1, 2, 3]");
      test("def f = varArgsFuncAsync; f('abc', 1L)", "abc1");
      test("def f = varArgsFuncAsync; f('abc', 1.1234)", "abc1");
      test("def f = varArgsFuncAsync; f('abc', 1, 2, 'b', [1,2,3])", "abc12:b:[1, 2, 3]");
      test("def x = 'abc'; def y = 1L; def f = varArgsFuncAsync; f(x, y)", "abc1");
      test("def x = 'abc'; def y = 1.234; def f = varArgsFuncAsync; f(x, y)", "abc1");
      test("def f = varArgsFuncAsync; f(str:'abc', i:1L)", "abc1");
      test("def f = varArgsFuncAsync; f(str:'abc', i:1.1234)", "abc1");
      test("def f = varArgsFuncAsync; f(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "abc12:b:[1, 2, 3]");

      test("def obj = 'obj'; obj.varArgsMethod('abc', 1L)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethod('abc', 1.1234)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethod('abc', 1, 2, 'b', [1,2,3])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def x = 'abc'; def y = 1L; obj.varArgsMethod(x, y)", "objabc1");
      test("def obj = 'obj'; def x = 'abc'; def y = 1.234; obj.varArgsMethod(x, y)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethod(str:'abc', i:1L)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethod(str:'abc', i:1.1234)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethod(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f('abc', 1L)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f('abc', 1.1234)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f('abc', 1, 2, 'b', [1,2,3])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def x = 'abc'; def y = 1L; def f = obj.varArgsMethod; f(x, y)", "objabc1");
      test("def obj = 'obj'; def x = 'abc'; def y = 1.234; def f = obj.varArgsMethod; f(x, y)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f(str:'abc', i:1L)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f(str:'abc', i:1.1234)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethod; f(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "objabc12:b:[1, 2, 3]");

      test("def obj = 'obj'; obj.varArgsMethodAsync('abc', 1L)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethodAsync('abc', 1.1234)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethodAsync('abc', 1, 2, 'b', [1,2,3])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def x = 'abc'; def y = 1L; obj.varArgsMethodAsync(x, y)", "objabc1");
      test("def obj = 'obj'; def x = 'abc'; def y = 1.234; obj.varArgsMethodAsync(x, y)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethodAsync(str:'abc', i:1L)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethodAsync(str:'abc', i:1.1234)", "objabc1");
      test("def obj = 'obj'; obj.varArgsMethodAsync(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f('abc', 1L)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f('abc', 1.1234)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f('abc', 1, 2, 'b', [1,2,3])", "objabc12:b:[1, 2, 3]");
      test("def obj = 'obj'; def x = 'abc'; def y = 1L; def f = obj.varArgsMethodAsync; f(x, y)", "objabc1");
      test("def obj = 'obj'; def x = 'abc'; def y = 1.234; def f = obj.varArgsMethodAsync; f(x, y)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f(str:'abc', i:1L)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f(str:'abc', i:1.1234)", "objabc1");
      test("def obj = 'obj'; def f = obj.varArgsMethodAsync; f(str:'abc', i:1, args:[2, 'b', [1,2,3]])", "objabc12:b:[1, 2, 3]");
    }
    finally {
      Jactl.deregister("varArgsFunc");
      Jactl.deregister("varArgsFuncAsync");
      Jactl.deregister("varArgsMethod");
      Jactl.deregister("varArgsMethodAsync");
      testFuncData = null;
    }
  }

  public static int testFunc(Continuation c, int val) {
    return val * val;
  }
  public static Object testFuncData;

  public static int testMethod(int obj, Continuation c, int val) {
    return obj * val;
  }
  public static Object testMethodData;

  public static String varArgsFunc(String s, int i, Object... vargs) {
    return s + i + Arrays.stream(vargs).map(Object::toString).collect(Collectors.joining(":"));
  }
  public static Object varArgsFuncData;

  public static String varArgsFuncAsync(Continuation c, String source, int offset, String s, int i, Object... vargs) {
    return s + i + Arrays.stream(vargs).map(Object::toString).collect(Collectors.joining(":"));
  }
  public static Object varArgsFuncAsyncData;

  public static String varArgsMethod(Object obj, String s, int i, Object... vargs) {
    return obj.toString() + s + i + Arrays.stream(vargs).map(Object::toString).collect(Collectors.joining(":"));
  }
  public static Object varArgsMethodData;

  public static String varArgsMethodAsync(Object obj, Continuation c, String source, int offset, String s, int i, Object... vargs) {
    return obj.toString() + s + i + Arrays.stream(vargs).map(Object::toString).collect(Collectors.joining(":"));
  }
  public static Object varArgsMethodAsyncData;
}
