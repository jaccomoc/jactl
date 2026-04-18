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

import io.jactl.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import static org.junit.jupiter.api.Assertions.*;

class JactlScriptEngineFactoryTest {

  ScriptEngineFactory factory;
  
  @BeforeEach public void setUp() {
    factory = new ScriptEngineManager().getEngineByExtension("jactl").getFactory();
  }
  
  @Test
  void getEngineName() {
    assertEquals("Jactl", factory.getEngineName()); 
  }

  @Test
  void getEngineVersion() {
    assertEquals("Jactl " + Utils.JACTL_VERSION, factory.getEngineVersion()); 
  }

  @Test
  void getExtensions() {
    assertEquals(Utils.listOf("jactl"), factory.getExtensions()); 
  }

  @Test
  void getMimeTypes() {
    assertEquals(Utils.listOf("application/jactl", "text/jactl"), factory.getMimeTypes()); 
  }

  @Test
  void getNames() {
    assertEquals(Utils.listOf("jactl"), factory.getNames()); 
  }

  @Test
  void getLanguageName() {
    assertEquals("Jactl", factory.getLanguageName()); 
  }

  @Test
  void getLanguageVersion() {
    assertEquals(Utils.JACTL_LANGUAGE_VERSION, factory.getLanguageVersion()); 
  }

  @Test
  void getParameter() {
    assertEquals("Jactl", factory.getParameter(ScriptEngine.ENGINE));
    assertEquals("Jactl " + Utils.JACTL_VERSION, factory.getParameter(ScriptEngine.ENGINE_VERSION));
    assertEquals("Jactl", factory.getParameter(ScriptEngine.LANGUAGE));
    assertEquals(Utils.JACTL_LANGUAGE_VERSION, factory.getParameter(ScriptEngine.LANGUAGE_VERSION));
    assertEquals("jactl", factory.getParameter(ScriptEngine.NAME));
    assertEquals("THREAD-ISOLATED", factory.getParameter("THREADING"));
  }

  @Test
  void getMethodCallSyntax() {
    assertEquals("xyz.mmm(i, j)", factory.getMethodCallSyntax("xyz", "mmm", "i", "j"));
  }

  @Test
  void getOutputStatement() {
    assertEquals("print 'xyz';", factory.getOutputStatement("xyz"));
  }

  @Test
  void getProgram() {
    assertEquals("def x = 'xxx'\nprintln 'xyz:' + x\n", factory.getProgram("def x = 'xxx'", "println 'xyz:' + x"));
  }

  @Test
  void getScriptEngine() {
    ScriptEngine engine = factory.getScriptEngine();
    assertNotNull(engine);
    assertTrue(engine instanceof JactlScriptEngine);
  }
}