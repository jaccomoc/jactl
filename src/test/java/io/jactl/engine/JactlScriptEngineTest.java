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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.*;

class JactlScriptEngineTest {

  ScriptEngineManager mgr;
  JactlScriptEngine engine;
  
  @BeforeEach void setUp() {
    mgr = new ScriptEngineManager();
    engine = (JactlScriptEngine) mgr.getEngineByExtension("jactl");
    assertTrue(engine != null);
  }
  
  @Test void eval() throws ScriptException {
    assertEquals(true, engine.eval("println 'test'; true"));
    ScriptContext ctx = new SimpleScriptContext();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    assertEquals(true, engine.eval("println 'test'; true", ctx));
    assertEquals("test\n", baos.toString());
  }
  
  @Test void evalWithBindings() throws ScriptException {
    ScriptContext ctx = new SimpleScriptContext();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    Bindings bindings = engine.createBindings();
    bindings.put("x", "xxx");
    ctx.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    assertEquals(true, engine.eval("println x + x; true", ctx));
    assertEquals("xxxxxx\n", baos.toString());
  }

  @Test void evalWithBindingUpdate() throws ScriptException {
    ScriptContext ctx = new SimpleScriptContext();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    Bindings bindings = engine.createBindings();
    bindings.put("x", "xxx");
    ctx.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    assertEquals("yyy", engine.eval("x = 'yyy'; println x + x; x", ctx));
    assertEquals("yyyyyy\n", baos.toString());
    assertEquals("yyy", bindings.get("x"));
  }

  @Test void evalWithLocalAndGlobalBindingUpdate() throws ScriptException {
    ScriptContext ctx = new SimpleScriptContext();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    Bindings engineBindings = engine.createBindings();
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    Bindings globalBindings = engine.createBindings();
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    ctx.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
    ctx.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);
    assertEquals("yyybbb", engine.eval("x = 'yyy'; a='bbb'; println x + a; x + a", ctx));
    assertEquals("yyybbb\n", baos.toString());
    assertEquals("yyy", engineBindings.get("x"));
    assertEquals("bbb", globalBindings.get("a"));
  }

  @Test void evalWithLocalAndGlobalBindings() throws ScriptException {
    ScriptContext ctx = new SimpleScriptContext();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ctx.setWriter(new PrintWriter(baos));
    Bindings engineBindings = engine.createBindings();
    engineBindings.put("x", "xxx");
    engineBindings.put("y", "yyy");
    Bindings globalBindings = engine.createBindings();
    globalBindings.put("x", "XXX");
    globalBindings.put("a", "aaa");
    ctx.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
    ctx.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);
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
}