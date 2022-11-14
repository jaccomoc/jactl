/*
 * Copyright 2022 James Crawford
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
 */

package jacsal;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class BaseTest {
  boolean debug = false;

  protected void doTest(String code, Object expected) {
    doTest(code, true, false, false, expected);
  }

  private void doTest(String code, boolean evalConsts, boolean replMode, boolean testAsync, Object expected) {
    if (expected instanceof String && ((String) expected).startsWith("#")) {
      expected = new BigDecimal(((String) expected).substring(1));
    }
    try {
      JacsalContext jacsalContext = JacsalContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debug)
                                                 .build();
      Object result = Compiler.run(code, jacsalContext, testAsync, createGlobals());
      if (expected instanceof Object[]) {
        assertTrue(result instanceof Object[]);
        assertTrue(Arrays.equals((Object[]) expected, (Object[]) result));
      }
      else {
        assertEquals(expected, result);
      }
    }
    catch (Exception e) {
      fail(e);
      e.printStackTrace();
    }
  }

  protected void test(String code, Object expected) {
    doTest(code, true, false, false, expected);
    doTest(code, false, false, false, expected);
    doTest(code, true, true, false, expected);
    doTest(code, false, true, false, expected);
    doTest(code, true, true, true, expected);
    doTest(code, true, false, true, expected);
  }

  protected void testError(String code, String expectedError) {
    doTestError(code, true, false, expectedError);
    doTestError(code, false, false, expectedError);

    // Tests in repl mode may have different errors or no errors compared
    // to normal mode when accessing global vars so we can't run tests that
    // generate an error and expect the same results
    //    doTestError(code, true, true, expectedError);
    //    doTestError(code, false, true, expectedError);
  }

  private void doTestError(String code, boolean evalConsts, boolean replMode, String expectedError) {
    try {
      JacsalContext jacsalContext = JacsalContext.create()
                                                 .evaluateConstExprs(evalConsts)
                                                 .replMode(replMode)
                                                 .debug(debug)
                                                 .build();
      Compiler.run(code, jacsalContext, new HashMap<>());
      fail("Expected JacsalError");
    }
    catch (JacsalError e) {
      if (!e.getMessage().toLowerCase().contains(expectedError.toLowerCase())) {
        e.printStackTrace();
        fail("Message did not contain expected string '" + expectedError + "'. Message=" + e.getMessage());
      }
    }
  }

  protected Map<String, Object> createGlobals() {
    try {
      var globals = new HashMap<String, Object>();
      //      MethodHandle handle  = MethodHandles.lookup().findStatic(CompilerTest.class, "timestamp", MethodType.methodType(Object.class, String.class, int.class, Object.class));
      //      globals.put("timestamp", handle);
      return globals;
    }
    catch (Exception e) {
      fail(e);
      return null;
    }
  }

  protected Function<Map<String, Object>, Future<Object>> compile(String code) {
    JacsalContext jacsalContext = JacsalContext.create()
                                               .evaluateConstExprs(true)
                                               .replMode(true)
                                               .debug(false)
                                               .build();
    Map<String, Object> globals = new HashMap<>();
    return Compiler.compile(code, jacsalContext, globals);
  }
}
