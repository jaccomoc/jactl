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
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.jactl.JactlType.INT;

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
    testError("def f = [1,2,3].filter; f('abc')", "incompatible argument type");
    test("[null,null,'P'].filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it != ' '}", Arrays.asList(null, null, "P"));
    test("[null,null,'P'].map{sleep(0,it)}.filter{it}", List.of("P"));
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
    testError("'abcdef'.substring(-1,6)", "StringIndexOutOfBoundsException");
    test("'abcdef'.substring(0,6)", "abcdef");
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
    testError("def x = 'abcdef'; x.substring(-1,6)", "StringIndexOutOfBoundsException");
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
    test("List x = [1,2,3]; x.'size'()", 3);
    testError("def x = [1]; x.sizeXXX()", "no such method");
    test("def x; def y; y = x?.size()?.size(); y", null);
    test("def x = 'size'; [1,2,3].\"$x\"()", 3);
    test("def x = 'size'; def y = [1,2,3]; y.\"$x\"()", 3);
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
    testError("def f = [1,2,3].map; f('x')", "incompatible argument type");
    test("[1].map{ [it,it] }.map{ a,b -> a + b }", List.of(2));
    test("def x = [1,2,3]; def f = x.map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("def f = [1,2,3].map; f{it+it}", List.of(2,4,6));
    test("def f = [1,2,3].map{it*it}.map; f{it+it}", List.of(2,8,18));
    test("[1,2,3].map{it*it}[1]", 4);
    test("'abcde'.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; x.map{it+it}.join()", "aabbccddee");
    test("def x = 'abcde'; def f = x.map; f{it+it}.join()", "aabbccddee");
    testError("[1,2,3].map{ -> }", "too many arguments");
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
    testError("def f = [3,2,1].sort; f('z')", "incompatible argument type");
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
    testError("[[1],[2]].sort()", "cannot compare objects of type list");
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
    testError("[1,2,3].subList(1,-1)", "illegalargument");
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
    test("def x = [1,2,3,4,5]; x.subList(2)", List.of(3,4,5));
    test("def x = [1,2,3,4,5]; x.subList(0)", List.of(1,2,3,4,5));
    test("def x = [1,2,3,4,5]; x.subList(5)", List.of());
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

  public static int testFunc(Continuation c, int val) {
    return val * val;
  }
  public static Object testFuncData;

  public static int testMethod(int obj, Continuation c, int val) {
    return obj * val;
  }
  public static Object testMethodData;
}