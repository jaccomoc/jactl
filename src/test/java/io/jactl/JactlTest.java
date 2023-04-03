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

import io.jactl.runtime.RuntimeError;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JactlTest {

  @Test void eval() {
    assertEquals(7, Jactl.eval("3 + 4"));
    var bindings = new HashMap<String,Object>(){{ put("x", 3); put("y", 4); }};
    assertEquals(7, Jactl.eval("x + y", bindings));
    assertEquals(7, Jactl.eval("x += 4", bindings));
    assertEquals(7, bindings.get("x"));
    try {
      Jactl.eval("z = x + y", bindings);
      fail("Expecting compile error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().contains("unknown variable 'z'"));
    }
  }

  @Test void eval2() {
    var globals = new HashMap<String,Object>();
    globals.put("x", null);
    Jactl.eval("class X { int x; def f(n) { x * n } }; x = new X(2)", globals);
    int result = (int)Jactl.eval("x.f(3)", globals);         // result will be 6
    assertEquals(6, result);
  }

  @Test void compileRunSync() {
    var globals = new HashMap<String,Object>();
    JactlScript script = Jactl.compileScript("3 + 4", globals);
    Object result = script.runSync(globals);          // result will be 7
    assertEquals(7, result);
  }

  @Test void compileRunWithCompletion() {
    var globals = new HashMap<String,Object>();
    globals.put("x", null);
    globals.put("y", null);
    var script  = Jactl.compileScript("x + y", globals);

    var globalValues = new HashMap<String,Object>();
    globalValues.put("x", 7);
    globalValues.put("y", 3);
    script.run(globalValues, result -> System.out.println("Result is " + result));
  }
}