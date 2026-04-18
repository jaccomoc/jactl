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

package io.jactl.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.script.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class JactlScriptEngineTest {

  ScriptEngineManager mgr;
  JactlScriptEngine engine;
  ScriptContext ctx;
  Bindings engineBindings;
  Bindings globalBindings;
  ByteArrayOutputStream baos;

  private void assertThrows(Executable executable, Class<? extends Throwable> exceptionClass) {
    assertThrows(executable, exceptionClass, "");
  }
  
  private void assertThrows(Executable executable, Class<? extends Throwable> exceptionClass, String message) {
    try {
      executable.execute();
      fail("Should have thrown " + exceptionClass.getName() + " but nothing thrown");
    }
    catch (Throwable e) {
      if (!exceptionClass.isInstance(e)) {
        e.printStackTrace();
        fail("Should have thrown " + exceptionClass.getName() + " but threw " + e.getClass().getName());
      }
      if (e.getMessage() == null && !message.isEmpty() ||
          e.getMessage() != null && !e.getMessage().toLowerCase().contains(message.toLowerCase())) {
        fail("Message '" + e.getMessage() + "' did not contain expected text of '" + message + "'");
      }
    }
  }
  
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
  
  @Test void evalWithContext() throws ScriptException {
    engineBindings.put("x", "xxx");
    assertEquals(true, engine.eval("println x + x; true", ctx));
    assertEquals("xxxxxx\n", baos.toString());
    engineBindings.put("x", 2);
    assertEquals(4, engine.eval("println x + x; x + x", ctx));
    assertEquals("xxxxxx\n4\n", baos.toString());
    ScriptContext ctx2 = new SimpleScriptContext();
    ctx2.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
    ctx2.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);
    engineBindings.put("x", 33);
    ctx2.setReader(new StringReader("123\n456\n"));
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    ctx2.setWriter(new PrintWriter(baos2));
    assertEquals(66, engine.eval("println x.toString() + x + nextLine() + nextLine(); x + x", ctx2));
    assertEquals("3333123456\n", baos2.toString());
    engineBindings.put("x", null);
    assertEquals(true, engine.eval("x == null"));
  }

  @Test void evalWithBindings() throws ScriptException {
    Bindings bindings = engine.createBindings();
    bindings.put("x", "xxx");
    assertEquals("xxxxxx", engine.eval("x + x", bindings));
  }
  
  @Test void evalWithNoReader() throws ScriptException {
    ScriptContext ctx2 = new SimpleScriptContext();
    ctx2.setReader(null);
    assertEquals(3, engine.eval("1 + 2", ctx2));
    assertEquals(null, engine.eval("nextLine()", ctx2));
  }

  @Test void evalWithEngineBindings() throws ScriptException {
    engine.put("a1", "xxx");
    System.setOut(new PrintStream(baos));
    assertEquals("xxxxxx", engine.eval("a1 + a1"));
    engine.put("m", new HashMap());
    engine.eval("m.put('a', 'b')");
    assertEquals("b", ((Map)engine.get("m")).get("a"));
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

  @Test void evalWithSyntaxError() throws ScriptException {
    assertThrows(() -> engine.eval("if if if"), ScriptException.class, "unexpected token 'if'");
  }
  
  @Test void evalWithRuntimeError() throws ScriptException {
    assertThrows(() -> engine.eval("def m; m.x"), ScriptException.class, "null value");
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
    assertThrows(() -> engine.invokeMethod(obj, "f", "abc"), NoSuchMethodException.class);
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

  @Test void invokeMethodWithNullObj() throws ScriptException {
    engine.put("x", "xxx");
    engine.put("y", "yyy");
    mgr.put("x", "XXX");
    mgr.put("a", "aaa");
    engine.eval("class X { int f(String s) { (x + s).size() }; int p(i) { println x + i; (x+i).size() } }; new X()", ctx);
    assertThrows(() -> engine.invokeMethod(null, "f", "abcd"), NullPointerException.class);
  }

  @Test void invokeMethodWithNullName() throws ScriptException {
    engine.put("x", "xxx");
    engine.put("y", "yyy");
    mgr.put("x", "XXX");
    mgr.put("a", "aaa");
    Object obj = engine.eval("class X { int f(String s) { (x + s).size() }; int p(i) { println x + i; (x+i).size() } }; new X()", ctx);
    assertThrows(() -> engine.invokeMethod(obj, null, "abcd"), NullPointerException.class);
  }

  @Test void invokeMethodBuiltIn() throws ScriptException {
    Object obj = engine.eval("LocalDate.parse('2026-03-08')", ctx);
    assertThrows(() -> engine.invokeMethod(obj, "atTime()",LocalDateTime.parse("2026-03-08T12:13:14"), engine.invokeMethod(obj, "atTime()", LocalTime.parse("12:13:14"))), ScriptException.class, "target object is not a JactlObject");
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
    assertThrows(() -> engine.invokeMethod(obj, "toString"), ScriptException.class, "target object is not a JactlObject");
  }

  @Test void invokeMethodError2() throws ScriptException {
    assertEquals(1, engine.eval("1", ctx));
    Object obj = new Object();
    assertThrows(() -> engine.invokeMethod(obj, "toString"), ScriptException.class, "target object is not a JactlObject");
  }
  
  @Test void invokeFunction() throws ScriptException {
    engine.put("x", 1);
    engine.put("y", "y");
    mgr.put("y", 2);
    assertEquals("y1", engine.eval("def f(a,b) { y + x + a + b }; int g(String s, int i) { s.size() + i + x + y.size() }; y + x", ctx));
    assertEquals("y123", engine.invokeFunction("f", 2, 3));
    assertEquals(10, engine.invokeFunction("g", "456", 5));
  }
  
  @Test void invokeFunctionBeforeEval() {
    assertThrows(() -> engine.invokeFunction("f", 2, 3), ScriptException.class, "instance has not used eval");
  }

  @Test void invokeFunctionNonExistent() throws ScriptException {
    engineBindings.put("x", 1);
    engineBindings.put("y", "y");
    globalBindings.put("y", 2);
    assertEquals("y1", engine.eval("def f(a,b) { y + x + a + b }; int g(String s, int i) { s.size() + i + x + y.size() }; y + x", ctx));
    assertThrows(() -> engine.invokeFunction("fff", 2, 3), ScriptException.class, "unknown function 'fff'");
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
    assertThrows(() -> obj.f(2, 3), RuntimeException.class, "unknown function 'f'");
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
    assertThrows(() -> obj2.p("abcde"), RuntimeException.class, "no such method 'p'");
  }
  
  @Test void compileWithNullBindingValue() throws ScriptException {
    engine.put("x", 1);
    mgr.put("z", 3);
    CompiledScript script = engine.compile("x + z");
    assertEquals(4, script.eval());
    assertEquals(2, engine.eval("z - x", ctx));
    mgr.getBindings().remove("z");
    assertThrows(() -> script.eval(), ScriptException.class, "null operand");
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
    engine.put("x", 2);
    mgr.put("z", 7);
    CompiledScript script = engine.compile(new StringReader("x + z"));
    assertEquals(2, engine.eval("z - x", ctx));
    assertEquals(9, script.eval());
  }
  
  @Test void compileThenEval() throws ScriptException {
    engine.put("x", 7);
    mgr.put("z", 4);
    CompiledScript script = engine.compile("x + z");
    assertEquals(11, script.eval());
    assertEquals(2, engine.eval("z - x", ctx));
  }
  
  @Test void disablePrint() {
    engine.put("x", 7);
    mgr.put("z", 4);
    engine.put("jactl.disablePrint", true);
    assertThrows(() -> engine.compile("println x + z"), ScriptException.class, "println has been disabled");
    assertThrows(() -> engine.eval("println z - x", ctx), ScriptException.class, "println has been disabled");
  }
  
  @Test void evalStatement() throws ScriptException {
    engine.put("x", 7);
    mgr.put("z", 4);
    CompiledScript script = engine.compile("eval('x + z', [x:x,z:z])");
    assertEquals(11, script.eval());
    assertEquals(2, engine.eval("eval('z - x', [x:x, z:z])", ctx));
  }

  @Test void disableEval() {
    engine.put("x", 7);
    mgr.put("z", 4);
    engine.put("jactl.disableEval", true);
    assertThrows(() -> engine.compile("eval('x + z')"), ScriptException.class, "eval has been disabled");
    assertThrows(() -> engine.eval("eval('z - x')", ctx), ScriptException.class, "eval has been disabled");
  }

  @Test void dieStatement() throws ScriptException {
    engine.put("x", 7);
    mgr.put("z", 4);
    CompiledScript script = engine.compile("die 'some error'");
    assertThrows(() -> script.eval(), ScriptException.class, "some error");
    assertThrows(() -> engine.eval("die 'some other error'", ctx), ScriptException.class, "some other error");
  }

  @Test void disableDie() throws ScriptException {
    engine.put("x", 7);
    mgr.put("z", 4);
    engine.put("jactl.disableDie", true);
    assertThrows(() -> engine.compile("die 'some error'"), ScriptException.class, "die has been disabled");
    assertThrows(() -> engine.eval("die 'some other error'", ctx), ScriptException.class, "die has been disabled");
  }

  @Test void compileThenEvalWithRemovedBinding() throws ScriptException {
    engine.put("x", 7);
    mgr.put("z", 4);
    CompiledScript script = engine.compile("x + z");
    assertEquals(11, script.eval());
    engine.getBindings(ScriptContext.ENGINE_SCOPE).remove("x");
    assertThrows(script::eval, ScriptException.class, "null operand");
  }
  
  @Test void compileWithSyntaxError() throws ScriptException {
    assertThrows(() -> engine.compile("x +"), ScriptException.class, "unexpected end-of-file");
  }
  
  @Test void example() throws ScriptException {
    ScriptEngineManager engineMgr = new ScriptEngineManager();
    ScriptEngine        engine    = engineMgr.getEngineByName("jactl");

    engine.put("x", 3);                         // engine binding scope
    engineMgr.put("z", 5);                      // global binding scope
    Object result = engine.eval("x + z");
    System.out.println("Result is " + result);
  }
  
  @Test void accessHostNotAllowed() throws ScriptException {
    class NewType {
      String prefix;
      NewType(String prefix) { this.prefix = prefix; }
      public String process(String x) { return prefix + ": " + x; }
    }
    engine.put("x", new NewType("prefix"));
    assertThrows(() -> engine.eval("x.process('abc')"), ScriptException.class, "access to host classes not allowed");
  }

  @Test void accessHostAllowedNonPublicClass() throws ScriptException {
    class NewType {
      String prefix;
      NewType(String prefix) { this.prefix = prefix; }
      public String process(String x) { return prefix + ": " + x; }
    }
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertThrows(() -> engine.eval("x.process('abc')"), ScriptException.class, "class is not public");
  }

  public class NewTypeBase {
    public String base() { return "xyz"; }
  }
  
  public class NewType extends NewTypeBase {
    String prefix;
    NewType(String prefix) { this.prefix = prefix; }
    private String privateMethod(String x) { return "private: " + prefix + ": " + x; }
    public String process(String x) { return prefix + ": " + x; }
    public String overloadedMethod(String x) { return prefix + ": " + x; }
    public String overloadedMethod(int x) { return prefix + ": " + x; }
    public String overloadedMethod2(String x) { return prefix + ": " + x; }
    public String overloadedMethod2(NewTypeBase base) { return prefix + ": " + base.base(); }
    public void voidMethod(Map x) { x.put("x","xyz"); }
  }

  @Test void accessHostClassLookupFails() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    engine.put("x", new NewType("prefix"));
    assertThrows(() -> engine.eval("x.process('abc')"), ScriptException.class, "not allowed");
  }

  @Test void accessHostNonPublicMethod() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertThrows(() -> engine.eval("x.privateMethod('abc')"), ScriptException.class, "could not find public method");
  }

  @Test void accessToClassNotAllowed() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertEquals("prefix: abc", engine.eval("x.process('abc')"));
    assertEquals("xyz", engine.eval("x.base()"));
    assertEquals(NewType.class.getName(), engine.eval("x.className()"));
    assertThrows(() -> engine.eval("x.getClass().forName('xyz')"), ScriptException.class, "not an allowed host class");
  }

  @Test void allowHostAccess() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertEquals("prefix: abc", engine.eval("x.process('abc')"));
    assertEquals("xyz", engine.eval("x.base()"));
    assertEquals(NewType.class.getName(), engine.eval("x.className()"));
    assertEquals(11, engine.eval("x.process('abc').size()"));
  }

  @Test void hostAccessBadArgType() {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertThrows(() -> engine.eval("x.process(123)"), ScriptException.class, "cannot convert object of type int to String");
  }
  
  @Test void overloadedMethod() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertEquals("prefix: abc", engine.eval("x.overloadedMethod('abc')"));
    assertEquals("prefix: 123", engine.eval("x.overloadedMethod(123)"));
    assertEquals("prefix: abc", engine.eval("x.overloadedMethod('abc')"));
  }

  @Test void overloadedMethod2() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    assertEquals("prefix: abc", engine.eval("x.overloadedMethod2('abc')"));
    assertEquals("prefix: xyz", engine.eval("x.overloadedMethod2(x)"));
    assertEquals("prefix: xyz", engine.eval("x.overloadedMethod2(x)"));
  }

  @Test void voidMethod() throws ScriptException {
    engine.put("jactl.allowHostAccess", true);
    Predicate<String> classNameLookup = name -> name.contains("NewType");
    engine.put("jactl.allowHostClassLookup", classNameLookup);
    engine.put("x", new NewType("prefix"));
    engine.put("m", new HashMap<>());
    engine.eval("x.voidMethod(m)");
    assertEquals("xyz", ((Map)engine.get("m")).get("x"));
  }
}
