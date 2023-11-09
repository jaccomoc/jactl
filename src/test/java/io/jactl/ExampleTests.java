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

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Examples used in documentation
 */
public class ExampleTests {
  @Test public void example1() {
    var context = JactlContext.create().build();
    var x = Jactl.eval("class X { int i; def f(n) { i * n } }; new X(2)", Map.of(), context);
    var globals = new HashMap<String,Object>();
    globals.put("x", x);
    int result = (int)Jactl.eval("x.f(3)", globals, context);         // result will be 6
    assertEquals(6, result);
  }

  @Test public void example2() {
    var globals        = new HashMap<String,Object>();
    JactlScript script = Jactl.compileScript("3 + 4", globals);
    Object result      = script.runSync(globals);          // result will be 7
    assertEquals(7, result);
  }

  @Test public void example3() {
    var globals = new HashMap<String,Object>();
    globals.put("x", null);
    globals.put("y", null);
    var script  = Jactl.compileScript("x + y", globals);

    var globalValues = new HashMap<String,Object>();
    globalValues.put("x", 7);
    globalValues.put("y", 3);
    script.run(globalValues, result -> System.out.println("Result is " + result));
  }

  @Test public void example4() {
    JactlContext context = JactlContext.create()
                                       .build();

    var globals = new HashMap<String,Object>();
    var script  = Jactl.compileScript("13 * 17", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));    // Output will be: "Result is 221"

    Object result = Jactl.eval("13 * 17", globals, context);            // returns value of 221
    assertEquals(221, result);
  }

  @Test public void example5() {
    var context = JactlContext.create().build();
    Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);

    var globals = new HashMap<String,Object>();
    var script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));
  }

  @Test public void example6() {
    var context = JactlContext.create().build();
    Jactl.compileClass("package a.b.c; class Multiplier { int n; def mult(x){ n * x } }", context);

    var globals = new HashMap<String,Object>();
    var script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));

    // Or import the class
    script  = Jactl.compileScript("import a.b.c.Multiplier; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));

    // Or put script in same package
    script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));
  }

  public static class Base64Functions {
    public static void registerFunctions() {
      Jactl.method(JactlType.arrayOf(JactlType.BYTE))
           .name("base64Encode")
           .impl(Base64Functions.class, "base64Encode")
           .register();

      Jactl.method(JactlType.STRING)
           .name("base64Decode")
           .impl(Base64Functions.class, "base64Decode")
           .register();
    }

    public static void deregisterFunctions() {
      Jactl.deregister(JactlType.arrayOf(JactlType.BYTE), "base64Encode");
      Jactl.deregister(JactlType.STRING, "base64Decode");
    }

    public static Object base64EncodeData;
    public static String base64Encode(byte[] data) {
      return new String(Base64.getEncoder().encode(data));
    }

    public static Object base64DecodeData;
    public static byte[] base64Decode(String data) {
      return Base64.getDecoder().decode(data);
    }
  }

  @Test public void testBase64() throws Exception {
    Base64Functions.registerFunctions();
    try {
      assertEquals(new String(Base64.getEncoder().encode(new byte[]{ 1,2,3,4 })),
                   Jactl.eval("def x = [1,2,3,4] as byte[]; x.base64Encode()"));
    }
    finally {
      Base64Functions.deregisterFunctions();
    }
  }

  private static class TestJactEnv implements JactlEnv {
    @Override public void scheduleEvent(Object threadContext, Runnable event) {}
    @Override public void scheduleEvent(Object threadContext, Runnable event, long timeMs) {}
    @Override public void scheduleEvent(Runnable event, long timeMs) {}
    @Override public void scheduleBlocking(Runnable blocking) {}
    @Override public Object getThreadContext() { return null; }

    static JactlEnv env;

    @Override
    public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
      Object threadContext = env.getThreadContext();
      env.scheduleBlocking(() -> {
        Object retVal = result;
        try {
          FileOutputStream fileOutput = new FileOutputStream("/tmp/checkpoints.data");
          fileOutput.write(checkpoint);
          fileOutput.close();
        }
        catch (IOException e) {
          retVal = new RuntimeError("Error persisting checkpoint", source, offset, e);
        }
        Object finalRetVal = retVal;
        env.scheduleEvent(threadContext, () -> resumer.accept(finalRetVal));
      });
    }
  }
}
