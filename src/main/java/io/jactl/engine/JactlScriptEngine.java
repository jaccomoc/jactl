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

import io.jactl.*;

import javax.script.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;

/**
 * ScriptEngine for Jactl to support JSR 223 scripting language API
 */
public class JactlScriptEngine extends AbstractScriptEngine {
  
  private JactlContext             jactlContext;
  private JactlScriptEngineFactory factory;
  
  JactlScriptEngine(JactlScriptEngineFactory factory) {
    this.factory = factory;
    jactlContext = JactlContext.create()
                               .debug(0)
                               .classAccessToGlobals(true)
                               .build();
  }
  
  @Override public Object eval(String script, ScriptContext context) throws ScriptException {
    Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
    Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
    Map<String, Object> bindings;
    if (globalBindings == null) {
      bindings = engineBindings;
    }
    else {
      // Build a pretend Map that combines engine and global bindings with
      // engine bindings searched first
      bindings = new AbstractMap<String, Object>() {
        @Override public Object get(Object key) {
          if (engineBindings.containsKey(key)) { return engineBindings.get(key); }
          return globalBindings.get(key);
        }
        @Override public boolean containsKey(Object key) { return engineBindings.containsKey(key) || globalBindings.containsKey(key); }
        @Override public Object put(String key, Object value) {
          if (engineBindings.containsKey(key)) { return engineBindings.put(key, value); }
          if (globalBindings.containsKey(key)) { return globalBindings.put(key, value); }
          return engineBindings.put(key, value);
        }
        @Override public Object remove(Object key) {
          if (engineBindings.containsKey(key)) { return engineBindings.remove(key); }
          return globalBindings.remove(key);
        }
        @Override public Set<String> keySet() { return new HashSet(globalBindings.keySet()) {{ addAll(engineBindings.keySet()); }}; }
        @Override public Set<Entry<String, Object>> entrySet() { return new HashSet(globalBindings.entrySet()) {{ addAll(engineBindings.entrySet()); }}; }
      };
    }
    Function<Map<String, Object>, Object> scriptMain = jactlContext.getEvalScript(script, bindings);
    JactlScript jactlScript = JactlScript.createScript(scriptMain, jactlContext);
    return jactlScript.runSync(bindings, context.getReader(), context.getWriter());
  }

  @Override public Object eval(Reader reader, ScriptContext context) throws ScriptException {
    String script;
    try {
      script = Utils.readAllChars(reader);
    }
    catch (IOException e) {
      throw new ScriptException("Error reading script: " + e.getMessage());
    }
    return eval(script, context);
  }

  @Override public Bindings createBindings() {
    return new SimpleBindings();
  }

  @Override public ScriptEngineFactory getFactory() {
    return factory;
  }
}
