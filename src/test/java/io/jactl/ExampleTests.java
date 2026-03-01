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
import io.jactl.runtime.JactlMethodHandle;
import io.jactl.runtime.RuntimeError;
import io.jactl.runtime.RuntimeUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examples used in documentation
 */
public class ExampleTests {
  @Test public void example1() {
    JactlContext context = JactlContext.create().build();
    Object       x       = Jactl.eval("class X { int i; def f(n) { i * n } }; new X(2)", Utils.mapOf(), context);
    HashMap<String, Object> globals = new HashMap<String,Object>();
    globals.put("x", x);
    int result = (int)Jactl.eval("x.f(3)", globals, context);         // result will be 6
    assertEquals(6, result);
  }

  @Test public void example2() {
    HashMap<String, Object> globals = new HashMap<String,Object>();
    JactlScript             script  = Jactl.compileScript("3 + 4", globals);
    Object result      = script.runSync(globals);          // result will be 7
    assertEquals(7, result);
  }

  @Test public void example3() throws ExecutionException, InterruptedException {
    HashMap<String, Object> globals = new HashMap<String,Object>();
    globals.put("x", null);
    globals.put("y", null);
    JactlScript script = Jactl.compileScript("x + y", globals);

    HashMap<String, Object> globalValues = new HashMap<String,Object>();
    globalValues.put("x", 7);
    globalValues.put("y", 3);
    script.run(globalValues, result -> System.out.println("Result is " + result));

    Future<Object> future = script.run(globalValues);
    System.out.println("Result is " + future.get());
    assertEquals(10, future.get());
  }

  @Test public void exampleWithInputOutput() throws ExecutionException, InterruptedException {
    HashMap<String, Object> globals = new HashMap<String,Object>();
    globals.put("x", null);
    globals.put("y", null);
    JactlScript script = Jactl.compileScript("def result = stream(nextLine).map{ it as int }.sum() + x + y; println 'Result is ' + result; return result", globals);

    HashMap<String, Object> globalValues = new HashMap<String,Object>();
    globalValues.put("x", 7);
    globalValues.put("y", 3);
    ByteArrayOutputStream out    = new ByteArrayOutputStream();
    AtomicInteger         result = new AtomicInteger();
    script.run(globalValues, new BufferedReader(new StringReader("1\n2\n3\n")), new PrintStream(out), res -> result.set((int)res));
    assertEquals(16, result.get());
    assertEquals("Result is 16\n", out.toString());

    out.reset();
    Future<Object> future = script.run(globalValues, new BufferedReader(new StringReader("1\n2\n3\n")), new PrintStream(out));
    assertEquals(16, future.get());
    assertEquals("Result is 16\n", out.toString());
  }

  @Test public void example4() {
    JactlContext context = JactlContext.create()
                                       .build();

    HashMap<String, Object> globals = new HashMap<String,Object>();
    JactlScript             script  = Jactl.compileScript("13 * 17", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));    // Output will be: "Result is 221"

    Object result = Jactl.eval("13 * 17", globals, context);            // returns value of 221
    assertEquals(221, result);
  }

  @Test public void example5() {
    JactlContext context = JactlContext.create().build();
    Jactl.compileClass("class Multiplier { int n; def mult(x){ n * x } }", context);

    HashMap<String, Object> globals = new HashMap<String,Object>();
    JactlScript             script  = Jactl.compileScript("def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));
  }

  @Test public void example6() {
    JactlContext context = JactlContext.create().build();
    Jactl.compileClass("package a.b.c; class Multiplier { int n; def mult(x){ n * x } }", context);

    HashMap<String, Object> globals = new HashMap<String,Object>();
    JactlScript             script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));

    // Or import the class
    script  = Jactl.compileScript("import a.b.c.Multiplier; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));

    // Or put script in same package
    script  = Jactl.compileScript("package a.b.c; def x = new Multiplier(13); x.mult(17)", globals, context);
    script.run(globals, result -> System.out.println("Result: " + result));
  }
  
  @Test public void asyncExample() {
    try {
      Jactl.function()
           .name("measure")
           .asyncParam("closure")     // only async when closure passed in is async
           .impl(ExampleTests.class, "measure")
           .register();

      JactlScript script   = Jactl.compileScript("measure{ sleep(1000) }", new HashMap<>());
      long        duration = (long)script.runSync(new HashMap<>());
      assertTrue(duration >= Duration.ofMillis(1000).toNanos() && duration <= Duration.ofMillis(1100).toNanos());
    }
    finally {
      Jactl.deregister("measure");
    }
  }

  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
    long start = System.nanoTime();
    try {
      RuntimeUtils.invoke(closure, source, offset);
      return System.nanoTime() - start;
    }
    catch(Continuation cont) {
      throw new Continuation(cont, measureResumeHandle,
                             0,
                             new long[]{ start },
                             new Object[0]);
    }
  }

  public static Object measureResume(Continuation c) {
    long start = c.localPrimitives[0];
    return System.nanoTime() - start;
  }

  private static JactlMethodHandle measureResumeHandle = RuntimeUtils.lookupMethod(ExampleTests.class,
                                                                                   "measureResume",
                                                                                   Object.class,
                                                                                   Continuation.class);

//  public static JactlMethodHandle measure$cHandle = RuntimeUtils.lookupMethod(ExampleTests.class, "measure$c", Object.class, Continuation.class);
//  public static Object measure$c(Continuation c) {
//    return measure(c, (String)c.localObjects[0], (int)c.localPrimitives[0], null);
//  }
//
//  public static long measure(Continuation c, String source, int offset, JactlMethodHandle closure) {
//    Instant start = c == null ? Instant.now() : (Instant)c.localObjects[1];
//    if (c == null) {
//      try {
//        closure.invoke(c, source, offset, new Object[0]);
//      }
//      catch (Continuation cont) {
//        throw new Continuation(cont, measure$cHandle, 0, new long[]{offset}, new Object[]{source, start});
//      }
//      catch (Throwable t) {
//        throw new RuntimeError(t.getMessage(), source, offset, t);
//      }
//    }
//    return Duration.between(start, Instant.now()).toMillis();
//  }

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

    public static String base64Encode(byte[] data) {
      return new String(Base64.getEncoder().encode(data));
    }
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

  public static class Point {
    public double x, y;
    Point(double x, double y)                  { this.x = x; this.y = y; }
    public static Point of(double x, double y) { return new Point(x,y); }
    public double distanceTo(Point other) {
      return Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y));
    }
  }
  
  @Test public void testPoint() throws NoSuchMethodException {
    JactlType pointType = Jactl.createClass("jactl.draw.Point")
                               .javaClass(Point.class)
                               .autoImport(true)
                               .method("of", "of", "x", double.class, "y", double.class)
                               .method("distanceTo", "distanceTo", "other", Point.class)
                               .register();
    
    assertEquals(2.8284271247461903D, Jactl.eval("Point p = Point.of(1,2); p.distanceTo(Point.of(3,4))"));
  }
}
