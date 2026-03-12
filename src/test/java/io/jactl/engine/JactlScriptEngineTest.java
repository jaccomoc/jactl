/*
 * Copyright © 2022,2023,2024  James Crawford
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

package io.jactl.engine;

import io.jactl.runtime.NullError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class JactlScriptEngineTest {

  ScriptEngineManager mgr;
  JactlScriptEngine engine;
  ScriptContext ctx;
  Bindings engineBindings;
  Bindings globalBindings;
  ByteArrayOutputStream baos;
  
  @BeforeEach void setUp() {
    mgr = new ScriptEngineManager();
    engine = (JactlScriptEngine) mgr.getEngineByName("jactl");
    assertTrue(engine != null);
    ctx = new SimpleScriptContext();
    baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    ctx.setReader(new StringReader(""));
    engineBindings = engine.createBindings();
    ctx.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
    globalBindings = engine.createBindings();
    ctx.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);
    engineBindings.put("x", 1);
    engineBindings.put("y", "y");
    globalBindings.put("y", 2);
    globalBindings.put("z", 3);
  }
  
  @Test void eval() throws ScriptException {
    assertEquals(true, engine.eval("println 'test'; true"));
    assertEquals(true, engine.eval("println 'test'; true", ctx));
    assertEquals("test\n", baos.toString());
  }
  
  @Test void evalReader() throws ScriptException {
    StringReader reader = new StringReader("println 'test'; true");
    assertEquals(true, engine.eval(reader));
    reader = new StringReader("println 'test'; true");
    assertEquals(true, engine.eval(reader, ctx));
    assertEquals("test\n", baos.toString());
  }
  
  @Test void evalWithBindings() throws ScriptException {
    engineBindings.put("x", "xxx");
    assertEquals(true, engine.eval("println x + x; true", ctx));
    assertEquals("xxxxxx\n", baos.toString());
    engineBindings.put("x", 2);
    assertEquals(4, engine.eval("println x + x; x + x", ctx));
    assertEquals("xxxxxx\n4\n", baos.toString());
  }

  @Test void evalWithEngineBindings() throws ScriptException {
    engine.put("a1", "xxx");
    System.setOut(new PrintStream(baos));
    assertEquals("xxxxxx", engine.eval("a1 + a1"));
  }

  @Test void evalWithGlobalBindings() throws ScriptException {
    mgr.put("a1", "xxx");
    assertEquals("xxxxxx", engine.eval("a1 + a1"));
  }

  @Test void evalWithBindingUpdate() throws ScriptException {
    engineBindings.put("x", "xxx");
    assertEquals("yyy", engine.eval("x = 'yyy'; println x + x; x", ctx));
    assertEquals("yyyyyy\n", baos.toString());
    assertEquals("yyy", engineBindings.get("x"));
  }

  @Test void evalWithLocalAndGlobalBindingUpdate() throws ScriptException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    assertEquals("yyybbb", engine.eval("x = 'yyy'; a='bbb'; println x + a; x + a", ctx));
    assertEquals("yyy", engineBindings.get("x"));
    assertEquals("yyybbb\n", baos.toString());
    assertEquals("bbb", globalBindings.get("a"));
    engineBindings.put("x",123);
    engineBindings.put("a", 456);
    assertEquals(579, engine.eval("println x + a; x + a", ctx));
    assertEquals("yyybbb\n579\n", baos.toString());
  }

  @Test void evalWithLocalAndGlobalBindings() throws ScriptException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    assertEquals("xxxyyyaaa", engine.eval("println x + y + a; x + y + a", ctx));
    assertEquals("xxxyyyaaa\n", baos.toString());
  }

  @Test void async() throws ScriptException {
    assertEquals(true, engine.eval("sleep(1); true"));
  }
  
  @Test void createBindings() {
    Bindings bindings = engine.createBindings();
    assertEquals(0, bindings.size());
  }

  @Test void getFactory() {
    assertTrue(engine.getFactory() instanceof JactlScriptEngineFactory);
  }
  
  @Test void invokeMethod() throws ScriptException, NoSuchMethodException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String x) { x.size() }; int p(x) { println x; x.size() } }; new X()", ctx);
    assertEquals(3, engine.invokeMethod(obj, "f", "abc"));
    assertEquals(5, engine.invokeMethod(obj, "p", "abcde"));
  }

  @Test void invokeField() throws ScriptException, NoSuchMethodException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String x) { x.size() }; int p(x) { println x; x.size() }; def fieldf = f; def fieldp = p }; new X()", ctx);
    assertEquals(3, engine.invokeMethod(obj, "fieldf", "abc"));
    assertEquals(5, engine.invokeMethod(obj, "fieldp", "abcde"));
  }

  @Test void invokeMethodNonExistent() throws ScriptException, NoSuchMethodException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int fff(String x) { x.size() }; int p(x) { println x; x.size() } }; new X()", ctx);
    assertThrows(NoSuchMethodException.class, () -> engine.invokeMethod(obj, "f", "abc"));
  }

  @Test void invokeMethodWithBinding() throws ScriptException, NoSuchMethodException {
    engine.put("x", "xxx");
    engine.put("y", "yyy");
    mgr.put("x", "XXX");
    mgr.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String s) { (x + s).size() }; int p(i) { println x + i; (x+i).size() } }; new X()", ctx);
    assertEquals(7, engine.invokeMethod(obj, "f", "abcd"));
    assertEquals(8, engine.invokeMethod(obj, "p", "abcde"));
  }

  @Test void invokeMethodBuiltIn() throws ScriptException {
    Object obj = engine.eval("LocalDate.parse('2026-03-08')", ctx);
    assertThrows(ScriptException.class, () -> engine.invokeMethod(obj, "atTime()",LocalDateTime.parse("2026-03-08T12:13:14"), engine.invokeMethod(obj, "atTime()", LocalTime.parse("12:13:14"))));
  }

  @Test void invokeMethodAsync() throws ScriptException, NoSuchMethodException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String x) { sleep(1,x.size()) }; int p(x) { sleep(1); println x; sleep(1); f(x) } }; new X()", ctx);
    assertEquals(3, engine.invokeMethod(obj, "f", "abc"));
    assertEquals(5, engine.invokeMethod(obj, "p", "abcde"));
  }
  
  @Test void invokeMethodError() {
    Object obj = new Object();
    assertThrows(ScriptException.class, () -> engine.invokeMethod(obj, "toString"));
  }

  @Test void invokeMethodError2() throws ScriptException {
    assertEquals(1, engine.eval("1", ctx));
    Object obj = new Object();
    assertThrows(ScriptException.class, () -> engine.invokeMethod(obj, "toString"));
  }
  
  @Test void invokeFunction() throws ScriptException {
    engine.put("x", 1);
    engine.put("y", "y");
    mgr.put("y", 2);
    assertEquals("y1", engine.eval("def f(a,b) { y + x + a + b }; int g(String s, int i) { s.size() + i + x + y.size() }; y + x", ctx));
    assertEquals("y123", engine.invokeFunction("f", 2, 3));
    assertEquals(10, engine.invokeFunction("g", "456", 5));
  }

  @Test void invokeFunctionNonExistent() throws ScriptException {
    engineBindings.put("x", 1);
    engineBindings.put("y", "y");
    globalBindings.put("y", 2);
    assertEquals("y1", engine.eval("def f(a,b) { y + x + a + b }; int g(String s, int i) { s.size() + i + x + y.size() }; y + x", ctx));
    assertThrows(ScriptException.class, () -> engine.invokeFunction("fff", 2, 3));
  }

  @Test void invokeFunctionAsync() throws ScriptException {
    engine.put("x", 1);
    engine.put("y", "y");
    mgr.put("y", 2);
    assertEquals("y1", engine.eval("def f(a,b) { sleep(1,y) + sleep(1,x) + a + sleep(1,b) }; y + x", ctx));
    assertEquals("y123", engine.invokeFunction("f", 2, 3));
  }
  
  public interface MyInterface {
    String f(Object a, int b);
    int g();
    Object z();
  }
  
  @Test void getInterface() throws ScriptException {
    engine.put("x", 1);
    engine.put("y", "y");
    mgr.put("y", 2);
    assertEquals("y1", engine.eval("String f(a, int b) { sleep(1,y) + sleep(1,x) + a + sleep(1,b) }; int g() { sleep(1); 3 }; def z(){4}; def zzz() {7}; y + x", ctx));
    assertEquals("y123", engine.invokeFunction("f", 2, 3));
    MyInterface obj = engine.getInterface(MyInterface.class);
    assertEquals("y123", obj.f(2, 3));
    assertEquals(3, obj.g());
    assertEquals(4, obj.z());
  }
  
  @Test void getInterfaceError() throws ScriptException {
    engineBindings.put("x", 1);
    engineBindings.put("y", "y");
    globalBindings.put("y", 2);
    assertEquals("y1", engine.eval("String fff(a, int b) { sleep(1,y) + sleep(1,x) + a + sleep(1,b) }; int g() { sleep(1); 3 }; def z(){4}; def zzz() {7}; y + x", ctx));
    MyInterface obj = engine.getInterface(MyInterface.class);
    assertThrows(RuntimeException.class, () -> obj.f(2, 3));
  }
  
  public interface MyInterface2 {
    int f(String x);
    int p(Object x);
  }
  
  @Test void getInterfaceWithObj() throws ScriptException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String x) { sleep(1,x.size()) }; int p(x) { sleep(1); println x; sleep(1); f(x) } }; new X()", ctx);
    MyInterface2 obj2 = engine.getInterface(obj, MyInterface2.class);
    assertEquals(3, obj2.f("abc"));
    assertEquals(5, obj2.p("abcde"));
  }

  @Test void getInterfaceWithObjWithError() throws ScriptException {
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String x) { sleep(1,x.size()) }; int pp(x) { sleep(1); println x; sleep(1); f(x) } }; new X()", ctx);
    MyInterface2 obj2 = engine.getInterface(obj, MyInterface2.class);
    assertThrows(RuntimeException.class, () -> obj2.p("abcde"));
  }
  
  @Test void compileWithNullBindingValue() throws ScriptException {
    engine.put("x", 1);
    mgr.put("z", 3);
    CompiledScript script = engine.compile("x + z");
    assertEquals(4, script.eval());
    assertEquals(2, engine.eval("z - x", ctx));
    mgr.getBindings().remove("z");
    assertThrows(NullError.class, () -> script.eval());
  }
  
  @Test void compileWithBindingTypeChange() throws ScriptException {
    engine.put("x", 1);
    mgr.put("z", 3);
    CompiledScript script = engine.compile("x + z");
    assertEquals(4, script.eval());
    assertEquals(2, engine.eval("z - x", ctx));
    engine.put("x", "abc");
    assertEquals("abc3", script.eval());
  }
  
  @Test void compile() throws ScriptException {
    engine.put("x", 1);
    mgr.put("z", 3);
    CompiledScript script = engine.compile("x + z");
    assertEquals(4, script.eval());
    assertEquals(2, engine.eval("z - x", ctx));
    engine.put("x", 2);
    engine.put("z", 4);
    assertEquals(6, script.eval());
    assertEquals(4, script.eval(ctx));
    engineBindings.put("x", 2);
    engineBindings.put("z", 3);
    assertEquals(5, script.eval(engineBindings));
    engineBindings.put("x", "abc");
    assertEquals("abc3", script.eval(engineBindings));
    CompiledScript script2 = engine.compile("def f() { x + x + z }; println x; f()");
    engine.put("x", "abc");
    assertEquals("abcabc4", script2.eval());
    Bindings bindings = engine.createBindings();
    bindings.put("x", "xyz");
    bindings.put("z", 3);
    assertEquals("xyzxyz3", script2.eval(bindings));
    ScriptContext ctx2 = new SimpleScriptContext();
    ctx2.setWriter(new PrintWriter(baos));
    ctx2.setReader(new StringReader("123\n"));
    ctx2.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    Bindings bindings2 = engine.createBindings();
    ctx2.setBindings(bindings2, ScriptContext.GLOBAL_SCOPE);
    CompiledScript script3 = engine.compile("def f() { x + x + z }; println x; println nextLine(); f()");
    assertEquals("xyzxyz3", script3.eval(ctx2));
    assertEquals("xyz\n123\n", baos.toString());
  }

  @Test void compileReader() throws ScriptException {
    engine.put("x", 1);
    mgr.put("z", 3);
    CompiledScript script = engine.compile(new StringReader("x + z"));
    assertEquals(2, engine.eval("z - x", ctx));
  }
  
  @Test void example() throws ScriptException {
    ScriptEngineManager engineMgr = new ScriptEngineManager();
    ScriptEngine        engine    = engineMgr.getEngineByName("jactl");

    engine.put("x", 3);                         // engine binding scope
    engineMgr.put("z", 5);                      // global binding scope
    Object result = engine.eval("x + z");
    System.out.println("Result is " + result);
  }
}
