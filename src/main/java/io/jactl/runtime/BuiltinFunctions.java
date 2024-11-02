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

package io.jactl.runtime;

import io.jactl.*;
import org.objectweb.asm.MethodVisitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.jactl.JactlType.*;
import static io.jactl.runtime.Reducer.Type.JOIN;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

public class BuiltinFunctions {

  private static final Map<String,FunctionDescriptor> globalFunctions = new HashMap<>();
  private static       boolean                        initialised     = false;
  private static final Map<Class,Integer> classId     = new HashMap<>();
  private static final List<Class>        fnClasses   = new ArrayList<>();

  private static final Map<String,Expr.VarDecl> globalFunDecls = new HashMap<>();

  static {
    allocateId(MatchCounter.class);
    allocateId(RuntimeUtils.class);
    allocateId(Reducer.class);
    allocateId(CircularBuffer.class);
    allocateId(NamedArgsMap.class);
    allocateId(NamedArgsMapCopy.class);
  }

  public static void registerBuiltinFunctions() {
    if (!initialised) {
      // Collection methods
      Jactl.method(LIST)
           .name("size")
           .impl(BuiltinFunctions.class, "listSize")
           .register();

      Jactl.method(MAP)
           .name("size")
           .impl(BuiltinFunctions.class, "mapSize")
           .register();

      Jactl.method(ITERATOR)
           .name("size")
           .asyncInstance(true)
           .impl(BuiltinFunctions.class, "iteratorSize")
           .register();

      Jactl.method(MAP)
           .name("remove")
           .param("key")
           .impl(BuiltinFunctions.class, "mapRemove")
           .register();

      Jactl.method(LIST)
           .name("remove")
           .param("index")
           .impl(BuiltinFunctions.class, "listRemove")
           .register();

      Jactl.method(LIST)
           .name("add")
           .param("element")
           .impl(BuiltinFunctions.class, "listAdd")
           .register();

      Jactl.method(LIST)
           .name("addAt")
           .param("index")
           .param("element")
           .impl(BuiltinFunctions.class, "listAddAt")
           .register();

      Jactl.method(ITERATOR)
           .name("reverse")
           .asyncInstance(true)
           .impl(BuiltinFunctions.class, "iteratorReverse")
           .register();

      Jactl.method(ITERATOR)
           .name("each")
           .asyncInstance(true)
           .asyncParam("action", null)
           .impl(BuiltinFunctions.class, "iteratorEach")
           .register();

      Jactl.method(ITERATOR)
           .name("reduce")
           .asyncInstance(true)
           .param("initial")
           .asyncParam("accumulator")
           .impl(BuiltinFunctions.class, "iteratorReduce")
           .register();

      Jactl.method(LIST)
           .name("min")
           .asyncParam("closure", null)
           .impl(BuiltinFunctions.class, "listMin")
           .register();

      Jactl.method(LIST)
           .name("max")
           .asyncParam("closure", null)
           .impl(BuiltinFunctions.class, "listMax")
           .register();

      Jactl.method(ITERATOR)
           .name("min")
           .asyncInstance(true)
           .asyncParam("closure", null)
           .impl(BuiltinFunctions.class, "iteratorMin")
           .register();

      Jactl.method(ITERATOR)
           .name("max")
           .asyncInstance(true)
           .asyncParam("closure", null)
           .impl(BuiltinFunctions.class, "iteratorMax")
           .register();

      Jactl.method(LIST)
           .name("avg")
           .impl(BuiltinFunctions.class, "listAvg")
           .register();

      Jactl.method(LIST)
           .name("sum")
           .impl(BuiltinFunctions.class, "listSum")
           .register();

      Jactl.method(ITERATOR)
           .name("avg")
           .asyncInstance(true)
           .impl(BuiltinFunctions.class, "iteratorAvg")
           .register();

      Jactl.method(ITERATOR)
           .name("sum")
           .asyncInstance(true)
           .impl(BuiltinFunctions.class, "iteratorSum")
           .register();

      Jactl.method(ITERATOR)
           .name("groupBy")
           .asyncInstance(true)
           .asyncParam("closure")
           .impl(BuiltinFunctions.class, "iteratorGroupBy")
           .register();

      Jactl.method(ITERATOR)
           .name("skip")
           .asyncInstance(true)
           .param("count")
           .impl(BuiltinFunctions.class, "iteratorSkip")
           .register();

      Jactl.method(ITERATOR)
           .name("limit")
           .asyncInstance(true)
           .param("count")
           .impl(BuiltinFunctions.class, "iteratorLimit")
           .register();

      Jactl.method(ITERATOR)
           .name("unique")
           .asyncInstance(true)
           .impl(BuiltinFunctions.class, "iteratorUnique")
           .register();

      Jactl.method(ITERATOR)
           .name("collect")
           .asyncInstance(true)
           .asyncParam("mapper", null)
           .impl(BuiltinFunctions.class, "iteratorCollect")
           .register();

      Jactl.method(ITERATOR)
           .name("collectEntries")
           .asyncInstance(true)
           .asyncParam("mapper", null)
           .impl(BuiltinFunctions.class, "iteratorCollectEntries")
           .register();

      Jactl.method(ITERATOR)
           .name("map")
           .asyncInstance(true)
           .asyncParam("mapper", null)
           .impl(BuiltinFunctions.class, "iteratorMap")
           .register();

      Jactl.method(ITERATOR)
           .name("mapWithIndex")
           .asyncInstance(true)
           .asyncParam("mapper", null)
           .impl(BuiltinFunctions.class, "iteratorMapWithIndex")
           .register();

      Jactl.method(ITERATOR)
           .name("flatMap")
           .asyncInstance(true)
           .asyncParam("mapper", null)
           .impl(BuiltinFunctions.class, "iteratorFlatMap")
           .register();

      Jactl.method(ITERATOR)
           .name("join")
           .asyncInstance(true)
           .param("separator", "")
           .impl(BuiltinFunctions.class, "iteratorJoin")
           .register();

      Jactl.method(ITERATOR)
           .name("sort")
           .asyncInstance(true)
           .asyncParam("comparator", null)
           .impl(BuiltinFunctions.class, "iteratorSort")
           .register();

      Jactl.method(ITERATOR)
           .name("grouped")
           .asyncInstance(true)
           .param("size")
           .impl(BuiltinFunctions.class, "iteratorGrouped")
           .register();

      Jactl.method(ITERATOR)
           .name("windowSliding")
           .asyncInstance(true)
           .param("size")
           .impl(BuiltinFunctions.class, "iteratorWindowSliding")
           .register();

      Jactl.method(ITERATOR)
           .name("filter")
           .asyncInstance(true)
           .asyncParam("predicate", null)
           .impl(BuiltinFunctions.class, "iteratorFilter")
           .register();

      Jactl.method(ITERATOR)
           .name("allMatch")
           .asyncInstance(true)
           .asyncParam("predicate", null)
           .impl(BuiltinFunctions.class, "iteratorAllMatch")
           .register();

      Jactl.method(ITERATOR)
           .name("anyMatch")
           .asyncInstance(true)
           .asyncParam("predicate", null)
           .impl(BuiltinFunctions.class, "iteratorAnyMatch")
           .register();

      Jactl.method(ITERATOR)
           .name("noneMatch")
           .asyncInstance(true)
           .asyncParam("predicate", null)
           .impl(BuiltinFunctions.class, "iteratorNoneMatch")
           .register();

      Jactl.method(ITERATOR)
           .name("subList")
           .asyncInstance(true)
           .param("start")
           .param("end", Integer.MAX_VALUE)
           .impl(BuiltinFunctions.class, "iteratorSubList")
           .register();

      Jactl.method(LIST)
           .name("subList")
           .param("start")
           .param("end", Integer.MAX_VALUE)
           .impl(BuiltinFunctions.class, "listSubList")
           .register();

      Jactl.method(LIST)
           .name("transpose")
           .impl(BuiltinFunctions.class, "listTranspose")
           .register();

      Jactl.method(ITERATOR)
           .name("transpose")
           .impl(BuiltinFunctions.class, "iteratorTranspose")
           .register();

      // String methods
      Jactl.method(STRING)
           .name("lines")
           .impl(BuiltinFunctions.class, "stringLines")
           .register();

      Jactl.method(STRING)
           .name("size")
           .alias("length")
           .impl(BuiltinFunctions.class, "stringLength")
           .register();

      Jactl.method(STRING)
           .name("toLowerCase")
           .param("count", Integer.MAX_VALUE)
           .impl(BuiltinFunctions.class, "stringToLowerCase")
           .register();

      Jactl.method(STRING)
           .name("toUpperCase")
           .param("count", Integer.MAX_VALUE)
           .impl(BuiltinFunctions.class, "stringToUpperCase")
           .register();

      Jactl.method(STRING)
           .name("substring")
           .param("start")
           .param("end", Integer.MAX_VALUE)
           .impl(BuiltinFunctions.class, "stringSubstring")
           .register();

      Jactl.method(STRING)
           .name("split")
           .param("regex", null)
           .param("modifiers", "")
           .impl(BuiltinFunctions.class, "stringSplit")
           .register();

      Jactl.method(STRING)
           .name("asNum")
           .param("base", 10)
           .impl(BuiltinFunctions.class, "stringAsNum")
           .register();

      Jactl.method(STRING)
           .name("fromJson")
           .impl(Json.class, "fromJson")
           .register();

      // byte methods
      Jactl.method(BYTE)
           .name("asChar")
           .impl(BuiltinFunctions.class, "byteAsChar")
           .register();

      Jactl.method(BYTE)
           .name("sqr")
           .impl(BuiltinFunctions.class, "byteSqr")
           .register();

      Jactl.method(BYTE)
           .name("abs")
           .impl(BuiltinFunctions.class, "byteAbs")
           .register();

      Jactl.method(BYTE)
           .name("toBase")
           .param("base")
           .impl(BuiltinFunctions.class, "byteToBase")
           .register();

      // int methods
      Jactl.method(INT)
           .name("asChar")
           .impl(BuiltinFunctions.class, "intAsChar")
           .register();

      Jactl.method(INT)
           .name("sqr")
           .impl(BuiltinFunctions.class, "intSqr")
           .register();

      Jactl.method(INT)
           .name("abs")
           .impl(BuiltinFunctions.class, "intAbs")
           .register();

      Jactl.method(INT)
           .name("toBase")
           .param("base")
           .impl(BuiltinFunctions.class, "intToBase")
           .register();

      // long methods
      Jactl.method(LONG)
           .name("sqr")
           .impl(BuiltinFunctions.class, "longSqr")
           .register();

      Jactl.method(LONG)
           .name("abs")
           .impl(BuiltinFunctions.class, "longAbs")
           .register();

      Jactl.method(LONG)
           .name("toBase")
           .param("base")
           .impl(BuiltinFunctions.class, "longToBase")
           .register();

      // double methods
      Jactl.method(DOUBLE)
           .name("sqr")
           .impl(BuiltinFunctions.class, "doubleSqr")
           .register();

      Jactl.method(DOUBLE)
           .name("abs")
           .impl(BuiltinFunctions.class, "doubleAbs")
           .register();

      // decimal methods
      Jactl.method(DECIMAL)
           .name("sqr")
           .impl(BuiltinFunctions.class, "decimalSqr")
           .register();

      Jactl.method(DECIMAL)
           .name("abs")
           .impl(BuiltinFunctions.class, "decimalAbs")
           .register();

      // Number methods
      Jactl.method(NUMBER)
           .name("pow")
           .param("power")
           .impl(BuiltinFunctions.class, "numberPow")
           .register();

      Jactl.method(NUMBER)
           .name("sqrt")
           .impl(BuiltinFunctions.class, "numberSqrt")
           .register();

      // Object methods
      Jactl.method(ANY)
           .name("toString")
           .param("indent", 0)
           .impl(BuiltinFunctions.class, "objectToString")
           .register();

      Jactl.method(ANY)
           .name("toJson")
           .impl(Json.class, "toJson")
           .register();

      Jactl.method(ANY)
           .name("className")
           .impl(BuiltinFunctions.class, "objectClassName")
           .inline("objectClassNameInline")
           .register();

      // Global functions
      Jactl.function()
           .name("timestamp")
           .impl(BuiltinFunctions.class, "timestamp")
           .register();

      Jactl.function()
           .name("nanoTime")
           .impl(BuiltinFunctions.class, "nanoTime")
           .register();

      Jactl.function()
           .name("nextLine")
           .impl(BuiltinFunctions.class, "nextLine")
           .register();

      Jactl.function()
           .name("sprintf")
           .param("format")
           .param("args", new Object[0])
           .impl(BuiltinFunctions.class, "sprintf")
           .register();

      Jactl.function()
           .name("sleep")
           .param("timeMs")
           .param("data", null)
           .impl(BuiltinFunctions.class, "sleep")
           .register();

      Jactl.function()
           .name("stream")
           .asyncParam("closure")
           .impl(BuiltinFunctions.class, "stream")
           .register();

      Jactl.function()
           .name("uuid")
           .impl(BuiltinFunctions.class, "uuid")
           .register();

      Jactl.function()
           .name("random")
           .param("bound")
           .impl(BuiltinFunctions.class, "random")
           .register();

      Jactl.function()
           .name("checkpoint")
           .param("commit", null)
           .param("recover", null)
           .impl(BuiltinFunctions.class, "checkpoint")
           .register();

      BuiltinArrayFunctions.registerFunctions();

      initialised = true;
    }
  }

  public static FunctionDescriptor lookupGlobalFunction(String name) {
    return globalFunctions.get(name);
  }

  public static JactlMethodHandle lookupMethodHandle(String name) {
    FunctionDescriptor descriptor = globalFunctions.get(name);
    if (descriptor == null) {
      throw new IllegalStateException("Internal error: attempt to get MethodHandle to unknown built-in function " + name);
    }
    return descriptor.wrapperHandle;
  }

  static Expr.VarDecl getGlobalFunDecl(String name) {
    return globalFunDecls.get(name);
  }

  static Set<String> getGlobalFunctionNames() {
    return globalFunctions.keySet();
  }

  ///////////////////////////////////////////////////

  public static void registerFunction(JactlFunction function) {
    function.init();
    if (function.wrapperHandleField == null) {
      throw new IllegalStateException("Missing value for wrapperHandleField for " + function.name);
    }
    if (function.isMethod()) {
      function.aliases.forEach(alias -> Functions.registerMethod(alias, function));
    }
    else {
      function.aliases.forEach(alias -> globalFunctions.put(alias, function));
      globalFunDecls.put(function.name, Utils.funcDescriptorToVarDecl(function));
    }

    allocateId(function.implementingClass);
  }

  private static void allocateId(Class clss) {
    Integer id = classId.get(clss);
    if (id == null) {
      id = fnClasses.size();
      classId.put(clss, id);
      fnClasses.add(clss);
    }
  }

  public static void deregisterFunction(JactlType type, String name) {
    Functions.deregisterMethod(type, name);
  }

  public static void deregisterFunction(String name) {
    FunctionDescriptor fn = globalFunctions.remove(name);
    if (fn instanceof JactlFunction) {
      ((JactlFunction)fn).cleanUp();
    }
  }

  public static int getClassId(Class clss) {
    Integer id = classId.get(clss);
    if (id == null) {
      throw new IllegalStateException("Unknown class for built-in functions: " + clss.getName());
    }
    return id;
  }

  public static Class getClass(int id) {
    if (id >= fnClasses.size()) {
      throw new IllegalStateException("Invalid class id " + id + " for built-in function classes (size=" + fnClasses.size() + ")");
    }
    return fnClasses.get(id);
  }


  /////////////////////////////////////
  // Global Functions
  /////////////////////////////////////

  // = checkpoint

  public static Object _checkpointData;
  public static Object _checkpoint(Continuation c, String source, int offset, Object data) {
    Continuation.checkpoint(source, offset, data);
    return data;
  }

  public static Object checkpointData;
  public static Object checkpoint(Continuation c, String source, int offset, JactlMethodHandle commitClosure, JactlMethodHandle recoveryClosure) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      switch (location) {
        case 0:
          Continuation.checkpoint(source, offset, commitClosure, recoveryClosure);
          break;
        case 1:
          JactlMethodHandle closure = (JactlMethodHandle)c.getResult();
          if (closure != null) {
            return closure.invoke(null, source ,offset, Utils.EMPTY_OBJ_ARR);
          }
          return null;
        case 2:
          return c.getResult();
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, checkpoint$cHandle, location + 1, null, new Object[]{ source, offset });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Error invoking closure: " + t.getMessage(), source, offset, t);
    }
    return null;
  }
  public static JactlMethodHandle checkpoint$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "checkpoint$c", Object.class, Continuation.class);
  public static Object checkpoint$c(Continuation c) {
    return checkpoint(c, (String)c.localObjects[0], (int)c.localObjects[1], null, null);
  }


  // = uuid

  public static Object uuidData;
  public static String uuid() {
    return RuntimeUtils.randomUUID().toString();
  }

  // = random

  public static Object randomData;
  public static long random(long bound) {
    return ThreadLocalRandom.current().nextLong(bound);
  }

  // = sleep
  public static Object sleepData;
  public static Object sleep(Continuation c, String source, int offset, long timeMs, Object data) {
    if (timeMs >= 0) {
      Continuation.suspendNonBlocking(source, offset, data, (context, dataObj, resumer) -> {
        context.scheduleEvent(() -> resumer.accept(dataObj), timeMs);
      });
    }
    return data;
  }

  // = timestamp
  public static Object timestampData;
  public static long timestamp() { return System.currentTimeMillis(); }

  // = nanoTime
  public static Object nanoTimeData;
  public static long nanoTime() { return System.nanoTime(); }

  // = sprintf
  public static Object sprintfData;
  public static String sprintf(String source, int offset, String format, Object... args) {
    try {
      return String.format(format, args);
    }
    catch (IllegalFormatException e) {
      throw new RuntimeError("Bad format string", source, offset, e);
    }
  }

  // = nextLine()
  public static Object nextLineData;
  public static String nextLine(Continuation c, String source, int offset) {
    BufferedReader input = RuntimeState.getState().getInput();
    if (input == null) {
      return null;
    }
    String result = null;
    try {
      if (input.ready()) {
        result = readLine(input);
      }
      else {
        // Might block so schedule blocking operation and suspend
        Continuation.suspendBlocking(source, offset, null, data -> readLine(input));
      }
    }
    catch (IOException ignored) {}
    if (result == null) {
      RuntimeState.resetState();
    }
    return result;
  }

  private static String readLine(BufferedReader input) {
    try {
      return input.readLine();
    }
    catch (IOException e) {
      return null;
    }
  }

  // = stream
  public static Object streamData;
  public static JactlIterator stream(Continuation c, String source, int offset, JactlMethodHandle closure) {
    return new StreamIterator(source, offset, closure);
  }

  /////////////////////////////////////
  // Methods
  /////////////////////////////////////

  // = abs
  public static Object byteAbsData;
  public static byte byteAbs(byte n)                { return n; }   // All byte values treated as 0-255
  public static Object intAbsData;
  public static int intAbs(int n)                   { return n < 0 ? -n : n; }
  public static Object longAbsData;
  public static long longAbs(long n)                { return n < 0 ? -n : n; }
  public static Object doubleAbsData;
  public static double doubleAbs(double n)          { return n < 0 ? -n : n; }
  public static Object decimalAbsData;
  public static BigDecimal decimalAbs(BigDecimal n) { return n.abs(); }

  // = asChar
  public static Object intAsCharData;
  public static String intAsChar(int c) { return String.valueOf((char)c); }
  public static Object byteAsCharData;
  public static String byteAsChar(byte c) { return String.valueOf((char)c); }

  // = toBase
  public static Object longToBaseData;
  public static String longToBase(long num, String source, int offset, int base) {
    if (base < Character.MIN_RADIX || base > Character.MAX_RADIX) {
      throw new RuntimeError("Base must be between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, source, offset);
    }
    return Long.toUnsignedString(num, base).toUpperCase();
  }

  public static Object intToBaseData;
  public static String intToBase(int num, String source, int offset, int base) {
    if (base < Character.MIN_RADIX || base > Character.MAX_RADIX) {
      throw new RuntimeError("Base must be between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, source, offset);
    }
    return Integer.toUnsignedString(num, base).toUpperCase();
  }

  public static Object byteToBaseData;
  public static String byteToBase(byte num, String source, int offset, int base) {
    if (base < Character.MIN_RADIX || base > Character.MAX_RADIX) {
      throw new RuntimeError("Base must be between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, source, offset);
    }
    return Integer.toUnsignedString(num & 0xff, base).toUpperCase();
  }

  // = sqrt
  public static Object numberSqrtData;
  public static Object numberSqrt(Number num, String source, int offset) {
    if (num instanceof Byte) {
      num = ((int)(byte)num) & 0xff;
    }
    if (num.doubleValue() < 0) {
      throw new RuntimeError("Attempt to take square root of negative number: " + num, source, offset);
    }
//    if (num instanceof BigDecimal) {
//      return ((BigDecimal)num).sqrt(MathContext.DECIMAL64);
//    }
    double result = Math.sqrt(num.doubleValue());
    if (num instanceof BigDecimal) {
      return BigDecimal.valueOf(result);
    }
    if (num instanceof Double) {
      return result;
    }
    long   longResult = (long) result;
    if ((double)longResult == result) {
      if ((long)(int)longResult == longResult) {
        return (int)longResult;
      }
      return longResult;
    }
    return result;
  }

  // = sqr
  public static Object byteSqrData;
  public static int byteSqr(byte num, String source, int offset) { int n = num < 0 ? num + 256 : num; return n * n; }
  public static Object intSqrData;
  public static int intSqr(int num, String source, int offset) { return num * num; }
  public static Object longSqrData;
  public static long longSqr(long num, String source, int offset) { return num * num; }
  public static Object doubleSqrData;
  public static double doubleSqr(double num, String source, int offset) { return num * num; }
  public static Object decimalSqrData;
  public static BigDecimal decimalSqr(BigDecimal num, String source, int offset) { return num.pow(2); }

  // = pow
  public static Object numberPowData;
  public static Object numberPow(Number num, String source, int offset, Number power) {
    if (num instanceof Byte) { num = ((int)(byte)num) & 0xff; }
    if (power instanceof Byte) { power = ((int)(byte)power) & 0xff; }
    if (num instanceof BigDecimal && !(power instanceof Double || power instanceof BigDecimal) && power.longValue() >= 0) {
      try {
        long exponent = power.longValue();
        if (exponent > Integer.MAX_VALUE) {
          throw new RuntimeError("Illegal request: exponent (" + exponent + ") too large", source, offset);
        }
        return ((BigDecimal)num).pow((int)exponent);
      }
      catch (Exception e) {
        throw new RuntimeError("Illegal request: raising " + num + " to " + power, source, offset);
      }
    }
    double result = Math.pow(num.doubleValue(), power.doubleValue());
    if (Double.isNaN(result)) {
      throw new RuntimeError("Illegal request: raising " + num + " to " + power, source, offset);
    }
    if (num instanceof BigDecimal) {
      return BigDecimal.valueOf(result);
    }
    if (num instanceof Double) {
      return result;
    }
    long longResult = (long) result;
    if ((double)longResult == result) {
      if (num instanceof Long) {
        return longResult;
      }
      if ((long)(int)longResult == longResult) {
        return (int)longResult;
      }
      return longResult;
    }
    return result;
  }

  // = toString

  public static Object objectToStringData;
  public static String objectToString(Object obj, int indent) { return RuntimeUtils.toString(obj, indent); }

  // = className

  public static Object objectClassNameData;
  public static String objectClassName(Object obj) { return RuntimeUtils.className(obj); }
  public static void objectClassNameInline(MethodVisitor mv) {
    mv.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/RuntimeUtils", "className", "(Ljava/lang/Object;)Ljava/lang/String;", false);
  }

  // = remove

  public static Object mapRemoveData;
  public static Object mapRemove(JactlMap map, String field) {
    return map.remove(field);
  }

  public static Object listRemoveData;
  public static Object listRemove(JactlList list, String source, int offset, int index) {
    if (index < 0) {
      index += list.size();
    }
    if (index < 0) {
      throw new RuntimeError("Index out of bounds: negative index (" + (index-list.size()) + ") resolves to location before start of list", source, offset);
    }
    if (index >= list.size()) {
      throw new RuntimeError("Index out of bounds:  (" + index + " is too large)", source, offset);
    }
    return list.remove(index);
  }

  // = add

  public static Object listAddData;
  public static JactlList listAdd(JactlList list, Object elem) {
    list.add(elem);
    return list;
  }

  // = addAt

  public static Object listAddAtData;
  public static JactlList listAddAt(JactlList list, String source, int offset, int index, Object elem) {
    if (index < 0) {
      index += list.size();
    }

    if (index < 0) {
      throw new RuntimeError("Index out of bounds: negative index (" + (index-list.size()) + ") resolves to location before start of list", source, offset);
    }
    if (index > list.size()) {
      throw new RuntimeError("Index out of bounds: (" + index + " is too large)", source, offset);
    }
    list.add(index, elem);
    return list;
  }

  // = size

  public static Object mapSizeData;
  public static int mapSize(JactlMap map) { return map.size(); }

  public static Object listSizeData;
  public static int listSize(JactlList list) {
    return list.size();
  }

  public static Object iteratorSizeData;
  public static int iteratorSize(Object iterable, Continuation c) {
    try {
      JactlList list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (JactlList) c.getResult();
      return list.size();
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSize$cHandle, 0, null, new Object[] { iterable });
    }
  }
  public static JactlMethodHandle iteratorSize$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSize$c", Object.class, Continuation.class);
  public static Object iteratorSize$c(Continuation c) {
    return iteratorSize(c.localObjects[0], c);
  }

  ////////////////////////////////

  // = sublist
  public static Object listSubListData;
  public static JactlList listSubList(JactlList list, String source, int offset, int start, int end) {
    try {
      if (end == Integer.MAX_VALUE) {
        end = list.size();
      }
      if (start < 0) {
        start += list.size();
      }
      if (end < 0) {
        end += list.size();
      }
      return list.subList(start, end);
    }
    catch (Exception e) {
      throw new RuntimeError("SubList error", source, offset, e);
    }
  }

  public static Object iteratorSubListData;
  public static JactlList iteratorSubList(Object iterable, Continuation c, String source, int offset, int start, int end) {
    source = c == null ? source : (String)c.localObjects[1];
    offset = c == null ? offset : (int)c.localPrimitives[0];
    start  = c == null ? start  : (int)c.localPrimitives[1];
    end    = c == null ? end    : (int)c.localPrimitives[2];
    try {
      JactlList list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (JactlList) c.getResult();
      return listSubList(list, source, offset, start, end == Integer.MAX_VALUE ? list.size() : end);
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSubList$cHandle,
                             0, new long[]{offset, start,end}, new Object[]{iterable, source});
    }
  }
  public static JactlMethodHandle iteratorSubList$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSubList$c", Object.class, Continuation.class);
  public static Object iteratorSubList$c(Continuation c) {
    return iteratorSubList(c.localObjects[0], c, null, 0, 0, 0);
  }

  ////////////////////////////////

  // = filter

  public static Object iteratorFilterData;
  public static JactlIterator iteratorFilter(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    return new FilterIterator(iter, source, offset, closure);
  }

  ////////////////////////////////

  // = reverse

  public static Object iteratorReverseData;
  public static JactlList iteratorReverse(Object iterable, Continuation c) {
    try {
      JactlList list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (JactlList) c.getResult();
      JactlList result = RuntimeUtils.createList(list.size());
      for (int i = list.size() - 1; i >= 0; i--) {
        result.add(list.get(i));
      }
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorReverse$cHandle,
                             0, null, new Object[]{ iterable });
    }
  }
  public static JactlMethodHandle iteratorReverse$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorReverse$c", Object.class, Continuation.class);
  public static Object iteratorReverse$c(Continuation c) {
    return iteratorReverse(c.localObjects[0], c);
  }

  ////////////////////////////////

  // = unique

  public static Object iteratorUniqueData;
  public static JactlIterator iteratorUnique(Object iterable, Continuation c, String source, int offset) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    return new UniqueIterator(iter, source, offset);
  }

  ////////////////////////////////

  // = skip

  public static Object iteratorSkipData;
  public static JactlIterator iteratorSkip(Object iterable, Continuation c, String source, int offset, int count) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    if (count >= 0) {
      return new FilterIterator(iter, source, offset, shouldNotSkipHandle.bindTo(count).bindTo(new int[]{ 0 }));
    }
    return new SkipIterator(iter, -count);
  }

  public static JactlMethodHandle shouldNotSkipHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "shouldNotSkip",
                                                                              Object.class, Integer.class, int[].class,
                                                                              Continuation.class, String.class, int.class, Object[].class);
  public static Object shouldNotSkip(Integer skipAmount, int[] index, Continuation ignore1, String ignore2, int ignore3, Object[] ignore4) {
    return index[0]++ >= skipAmount;
  }

  ////////////////////////////////

  // = limit

  public static Object iteratorLimitData;
  public static JactlIterator iteratorLimit(Object iterable, Continuation c, String source, int offset, int limit) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    if (limit >= 0) {
      return new LimitIterator(iter, limit);
    }
    return new NegativeLimitIterator(iter, -limit);
  }

  ////////////////////////////////

  // = grouped

  public static Object iteratorGroupedData;
  public static JactlIterator iteratorGrouped(Object iterable, Continuation c, String source, int offset, int size) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    if (size == 0) {
      return iter;
    }
    if (size < 0) {
      throw new RuntimeError("Value for grouped() must be >= 0 (was " + size + ")", source, offset);
    }
    return new GroupedIterator(iter, source, offset, size, false);
  }

  ////////////////////////////////

  // = windowSliding

  public static Object iteratorWindowSlidingData;
  public static JactlIterator iteratorWindowSliding(Object iterable, Continuation c, String source, int offset, int size) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    if (size == 0) {
      return iter;
    }
    if (size < 0) {
      throw new RuntimeError("Value for windowSliding() must be >= 0 (was " + size + ")", source, offset);
    }
    return new GroupedIterator(iter, source, offset, size, true);
  }

  ////////////////////////////////

  // = map

  public static Object iteratorMapData;
  public static JactlIterator iteratorMap(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure);
  }

  ////////////////////////////////

  // = mapWithIndex

  public static Object iteratorMapWithIndexData;
  public static JactlIterator iteratorMapWithIndex(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure, true);
  }

  ////////////////////////////////

  // = flatMap

  public static Object iteratorFlatMapData;
  public static JactlIterator iteratorFlatMap(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    JactlIterator iter = RuntimeUtils.createIterator(iterable);
    return new FlatMapIterator(iter, source, offset, closure);
  }

  //////////////////////////////////

  // = each

  public static Object iteratorEachData;
  public static Object iteratorEach(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return doIteratorEach(RuntimeUtils.createIterator(iterable), source, offset, closure, null);
  }
  public static Object doIteratorEach(JactlIterator iter, String source, int offset, JactlMethodHandle closure, Continuation c) {
    int methodLocation = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext = true;
      Object  elem    = null;
      // Implement as a simple state machine since iter.hasNext() and iter.next() can both throw a Continuation.
      // iter.hasNext() can throw if we have chained iterators and hasNext() needs to get the next value of the
      // previous iterator in the chain to see if it has a value.
      // We track our state using the methodLocation that we pass to our own Continuation when/if we throw.
      // Even states are the synchronous behavior and the odd states are for handling the async case if the
      // synchronous state throws and is later continued.
      while (true) {
        switch (methodLocation) {
          case 0:                       // Initial state
            hasNext = iter.hasNext();
            methodLocation = 2;         // hasNext() returned synchronously so jump straight to state 2
            break;
          case 1:                       // Continuing after hasNext() threw Continuation last time
            hasNext = (boolean) c.getResult();
            methodLocation = 2;
            break;
          case 2:                      // Have a value for "hasNext"
            if (!hasNext) {
              return null;             // EXIT: exit loop when underlying iterator has no more elements
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.getResult();
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            elem = RuntimeUtils.mapEntryToList(elem);
            Object ignored = closure == null ? null : closure.invoke((Continuation)null, source, (int)offset, new Object[]{ elem });
            methodLocation = 0;       // Back to initial state to get next element
            break;
          case 5:
            methodLocation = 0;       // Back to initial state to get next element
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorEach$cHandle,methodLocation + 1, new long[]{ offset }, new Object[]{ iter, source, closure });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
  public static Object iteratorEach$c(Continuation c) {
    return doIteratorEach((JactlIterator)c.localObjects[0], (String)c.localObjects[1], (int)c.localPrimitives[0], (JactlMethodHandle)c.localObjects[2], c);
  }
  public static JactlMethodHandle iteratorEach$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorEach$c",
                                                                                   Object.class, Continuation.class);

  ////////////////////////////////

  // = collect

  public static Object iteratorCollectData;
  public static Object iteratorCollect(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return doIteratorCollect(RuntimeUtils.createIterator(iterable), RuntimeUtils.createList(), false, source, offset, closure, null);
  }
  public static Object doIteratorCollect(JactlIterator iter, Object result, boolean isCollectEntries, String source, int offset, JactlMethodHandle closure, Continuation c) {
    int methodLocation = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext         = true;
      Object  elem            = null;
      Object  transformedElem = null;
      // Implement as a simple state machine since iter.hasNext() and iter.next() can both throw a Continuation.
      // iter.hasNext() can throw if we have chained iterators and hasNext() needs to get the next value of the
      // previous iterator in the chain to see if it has a value.
      // We track our state using the methodLocation that we pass to our own Continuation when/if we throw.
      // Even states are the synchronous behavior and the odd states are for handling the async case if the
      // synchronous state throws and is later continued.
      while (true) {
        switch (methodLocation) {
          case 0:                       // Initial state
            hasNext = iter.hasNext();
            methodLocation = 2;         // hasNext() returned synchronously so jump straight to state 2
            break;
          case 1:                       // Continuing after hasNext() threw Continuation last time
            hasNext = (boolean) c.getResult();
            methodLocation = 2;
            break;
          case 2:                      // Have a value for "hasNext"
            if (!hasNext) {
              return result;           // EXIT: exit loop when underlying iterator has no more elements
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.getResult();
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            elem = RuntimeUtils.mapEntryToList(elem);
            transformedElem = closure == null ? elem : closure.invoke((Continuation)null, source, (int)offset, new Object[]{ elem });
            methodLocation = 6;
            break;
          case 5:
            transformedElem = c.getResult();
            methodLocation = 6;
            break;
          case 6:
            if (isCollectEntries) {
              RuntimeUtils.addMapEntry((JactlMap)result, transformedElem, source, offset);
            }
            else {
              ((JactlList)result).add(transformedElem);
            }
            methodLocation = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorCollect$cHandle, methodLocation + 1, new long[]{ isCollectEntries ? 1 : 0, offset }, new Object[] { iter, result, source, closure });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  public static Object iteratorCollect$c(Continuation c) {
    return doIteratorCollect((JactlIterator)c.localObjects[0], c.localObjects[1], c.localPrimitives[0] == 0 ? false : true,
                             (String)c.localObjects[2], (int)c.localPrimitives[1], (JactlMethodHandle)c.localObjects[3], c);
  }

  public static JactlMethodHandle iteratorCollect$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorCollect$c",
                                                                                      Object.class, Continuation.class);

  /////////////////////////////

  // = reduce

  public static Object iteratorReduceData;
  public static Object iteratorReduce(Object iterable, Continuation c, String source, int offset, Object initialValue, JactlMethodHandle closure) {
    return new Reducer(Reducer.Type.REDUCE, RuntimeUtils.createIterator(iterable), source, offset, initialValue, closure).reduce(null);
  }

  /////////////////////////////

  // = join

  public static Object iteratorJoinData;
  public static String iteratorJoin(Object iterable, Continuation c, String source, int offset, String joinStr) {
    return (String)new Reducer(JOIN, RuntimeUtils.createIterator(iterable), source, offset, joinStr, null).reduce(null);
  }

  /////////////////////////////

  // = allMatches

  public static Object iteratorAllMatchData;
  public static boolean iteratorAllMatch(Object iterable, Continuation c, String source, int offset, JactlMethodHandle predicate) {
    return new MatchCounter(RuntimeUtils.createIterator(iterable), source, offset, predicate, MatchCounter.MatchType.ALL).matching(c);
  }

  // = anyMatches

  public static Object iteratorAnyMatchData;
  public static boolean iteratorAnyMatch(Object iterable, Continuation c, String source, int offset, JactlMethodHandle predicate) {
    return new MatchCounter(RuntimeUtils.createIterator(iterable), source, offset, predicate, MatchCounter.MatchType.ANY).matching(c);
  }

  // = noneMatches

  public static Object iteratorNoneMatchData;
  public static boolean iteratorNoneMatch(Object iterable, Continuation c, String source, int offset, JactlMethodHandle predicate) {
    return new MatchCounter(RuntimeUtils.createIterator(iterable), source, offset, predicate, MatchCounter.MatchType.NONE).matching(c);
  }

  /////////////////////////////

  // = avg

  public static Object listAvgData;
  public static Object listAvg(JactlList list, String source, int offset) {
    int size = list.size();
    if (size == 0) {
      throw new RuntimeError("Empty list for avg() function", source, offset);
    }
    Object value = listSum(list, source, offset);
    if (value instanceof Double)             { value = BigDecimal.valueOf((double)value); }
    else if (!(value instanceof BigDecimal)) { value = BigDecimal.valueOf(((Number)value).longValue()); }
    return RuntimeUtils.decimalDivide((BigDecimal)value, BigDecimal.valueOf(size), Utils.DEFAULT_MIN_SCALE, source, offset);
  }

  public static Object iteratorAvgData;
  public static Object iteratorAvg(Object iterable, Continuation c, String source, int offset) {
    return new Reducer(Reducer.Type.AVG, RuntimeUtils.createIterator(iterable), source, offset, BigDecimal.ZERO, null).reduce(null);
  }

  // = sum

  public static Object listSumData;
  public static Object listSum(JactlList list, String source, int offset) {
    Object sum = 0;
    int size = list.size();
    for (int i = 0; i < size; i++) {
      sum = Reducer.addNumbers(sum, list.get(i), source, offset);
    }
    return sum;
  }

  public static Object iteratorSumData;
  public static Object iteratorSum(Object iterable, Continuation c, String source, int offset) {
    return new Reducer(Reducer.Type.SUM, RuntimeUtils.createIterator(iterable), source, offset, 0, null).reduce(null);
  }

  // = min/max

  public static Object listMinData;
  public static Object listMin(JactlList list, Continuation c, String source, int offset, JactlMethodHandle closure) {
    if (closure != null) {
      return iteratorMin(list, c, source, offset, closure);
    }
    int size = list.size();
    Object min = null;
    for (int i = 0; i < size; i++) {
      Object current = list.get(i);
      if (i == 0 || RuntimeUtils.compareTo(current, min, source, offset) < 0) {
        min = current;
      }
    }
    return min;
  }

  public static Object listMaxData;
  public static Object listMax(JactlList list, Continuation c, String source, int offset, JactlMethodHandle closure) {
    if (closure != null) {
      return iteratorMax(list, c, source, offset, closure);
    }
    int size = list.size();
    Object max = null;
    for (int i = 0; i < size; i++) {
      Object current = list.get(i);
      if (i == 0 || RuntimeUtils.compareTo(current, max, source, offset) > 0) {
        max = current;
      }
    }
    return max;
  }

  public static Object iteratorMinData;
  public static Object iteratorMin(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return new Reducer(Reducer.Type.MIN, RuntimeUtils.createIterator(iterable), source, offset, null, closure).reduce(null);
  }

  public static Object iteratorMaxData;
  public static Object iteratorMax(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return new Reducer(Reducer.Type.MAX, RuntimeUtils.createIterator(iterable), source, offset, null, closure).reduce(null);
  }

  // = groupBy

  public static Object iteratorGroupByData;
  public static JactlMap iteratorGroupBy(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return (JactlMap)new Reducer(Reducer.Type.GROUP_BY, RuntimeUtils.createIterator(iterable), source, offset, RuntimeUtils.createMap(), closure).reduce(null);
  }

  // = transpose

  public static Object listTransposeData;
  public static JactlIterator listTranspose(JactlList inputs, Continuation c, String source, int offset) {
    return new TranposeIterator(inputs, source, offset);
  }

  // = transpose

  public static Object iteratorTransposeData;
  public static JactlList iteratorTranspose(Object iterable, Continuation c, String source, int offset) {
    return (JactlList)new Reducer(Reducer.Type.TRANSPOSE, RuntimeUtils.createIterator(iterable), source, offset, RuntimeUtils.createList(), null).reduce(null);
  }

  //////////////////////////////////////

  // = collectEntries

  public static Object iteratorCollectEntriesData;
  public static Object iteratorCollectEntries(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    return doIteratorCollect(RuntimeUtils.createIterator(iterable), RuntimeUtils.createMap(), true, source, offset, closure, null);
  }

  /////////////////////////////

  // = sort

  public static JactlMethodHandle iteratorSort$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSort$c",
                                                                                    Object.class, Continuation.class);

  public static Object iteratorSort$c(Continuation c) {
    return iteratorSort(c.localObjects[0], c, (String)c.localObjects[1], (int)c.localPrimitives[0], (JactlMethodHandle)c.localObjects[2]);
  }

  public static Object iteratorSortData;
  public static JactlList iteratorSort(Object iterable, Continuation c, String source, int offset, JactlMethodHandle closure) {
    JactlList result = null;
    int location = c == null ? 0 : c.methodLocation;
    try {
      if (location == 0) {
        result = iterable instanceof JactlList ? RuntimeUtils.createList((JactlList)iterable)
                                               : RuntimeUtils.convertIteratorToList(RuntimeUtils.createIterator(iterable), null);
        location = 2;
      }
      if (location == 1) {
        result = (JactlList) c.getResult();
        location = 2;
      }
      if (location == 2) {
        if (closure == null) {
          try {
            result.sort((a,b) -> RuntimeUtils.compareTo(a,b,source,offset));
            return result;
          }
          catch (Throwable t) {
            throw new RuntimeError("Unexpected error", source, offset, t);
          }
        }
        else {
          return (JactlList) mergeSort(result, closure, source, offset, null);
        }
      }
      else {
        return (JactlList) c.getResult();
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSort$cHandle, location + 1, new long[]{ offset }, new Object[]{ iterable, source, closure });
    }
  }

  /**
   * Bottom up merge sort.
   * @param list     the list to be sorted
   * @param closure  the closure for the comparator (returns -1, 0, or 1 for a pair of values from list)
   * @param source   the source code
   * @param offset   offset into source code where sort is being performed
   * @param c        the Continuation object for when closure is async
   * @return the sorted list
   * @throws Continuation if closure invokes async operation which suspends
   */
  public static Object mergeSort(JactlList list, JactlMethodHandle closure, String source, Integer offset, Continuation c) {
    int size = list.size();
    JactlList src;
    JactlList dst;
    int width = 1;

    if (c == null) {
      src = list;
      dst = RuntimeUtils.createList(size);
    }
    else {
      src = (JactlList)c.localObjects[0];
      dst = (JactlList)c.localObjects[1];
      width = (int)c.localPrimitives[0];
    }
    for (; width < size; width *= 2) {
      int i = c == null ? 0 : (int)c.localPrimitives[1] + 2 * width;
      c = null;
      for (; i <= size; i += 2 * width) {
        final int start2 = Math.min(i + width, size);
        try {
          merge(src, dst, i, start2, start2, Math.min(i + 2 * width, size), closure, source, offset, null);
        }
        catch (Continuation cont) {
          throw new Continuation(cont, mergeSort$cHandle, 0, new long[] {width, i, offset }, new Object[] {src, dst, list, closure, source });
        }
      }
      JactlList tmp = src;
      src = dst;
      dst = tmp;
    }
    return src;
  }
  public static Object mergeSort$c(Continuation c) {
    return mergeSort((JactlList)c.localObjects[2], (JactlMethodHandle)c.localObjects[3], (String)c.localObjects[4], (int)c.localPrimitives[2], c);
  }
  public static JactlMethodHandle mergeSort$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "mergeSort$c",
                                                                                Object.class, Continuation.class);

  public static JactlMethodHandle merge$cHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "merge$c",
                                                                            Object.class, Continuation.class);
  public static Object merge$c(Continuation c) {
    return merge((JactlList)c.localObjects[0], (JactlList)c.localObjects[1], (int)c.localPrimitives[3], (int)c.localPrimitives[4],
                 (int)c.localPrimitives[5], (int)c.localPrimitives[6], (JactlMethodHandle)c.localObjects[2],
                 (String)c.localObjects[3], (int)c.localPrimitives[7], c);
  }

  // Merge two sorted sublists of src into same position in dst
  public static Object merge(JactlList src, JactlList dst, int start1, int end1, int start2, int end2, JactlMethodHandle comparator, String source, int offset, Continuation c) {
    int count = end1 - start1 + end2 - start2;
    int i1 = start1;
    int i2 = start2;
    int dstPos = start1;

    Object comparison = null;

    // If continuing then continue from where we left off
    if (c != null) {
      i1 = (int)c.localPrimitives[0];
      i2 = (int)c.localPrimitives[1];
      dstPos = (int)c.localPrimitives[2];
      comparison = c.getResult();
    }

    // Copy, in order, to dst where dstPos is current position in dst
    for (; dstPos < start1 + count; dstPos++) {
      Object elem = null;
      if (i1 == end1) {
        elem = src.get(i2++);
      }
      else
      if (i2 == end2) {
        elem = src.get(i1++);
      }
      else {
        Object elem1 = src.get(i1);
        Object elem2 = src.get(i2);
        if (comparison == null) {
          try {
            comparison = comparator.invoke((Continuation) null, source, (int)offset, new Object[]{ RuntimeUtils.listOf(elem1, elem2) });
          }
          catch (Continuation cont) {
            throw new Continuation(cont, merge$cHandle,
                                   0, new long[] { i1, i2, dstPos, start1, end1, start2, end2, offset }, new Object[]{ src, dst, comparator, source });
          }
          catch (RuntimeException e) { throw e; }
          catch (Throwable t)        { throw new RuntimeError("Unexpected error", source, offset, t); }
        }
        if (!(comparison instanceof Integer)) {
          throw new RuntimeError("Comparator for sort must return integer value not " + RuntimeUtils.className(comparison), source, offset);
        }
        if ((int) comparison <= 0) {
          elem = elem1;
          i1++;
        }
        else {
          elem = elem2;
          i2++;
        }
        comparison = null;
      }
      if (dstPos == dst.size()) {
        dst.add(elem);
      }
      else {
        dst.set(dstPos, elem);
      }
    }
    return null;
  }

  /////////////////////////////
  //// String methods

  // = lines
  public static Object stringLinesData;
  public static JactlIterator stringLines(String str) {
    return JactlIterator.of(RuntimeUtils.createList(RuntimeUtils.lines(str).stream()));
  }

  // = length
  public static Object stringLengthData;
  public static int stringLength(String str) { return str.length();  }

  // = toLowerCase
  public static Object stringToLowerCaseData;
  public static String stringToLowerCase(String str, int length) {
    if (length < 0) {
      // Allow offset from end of string
      length += str.length();
    }
    if (length < 0 || length >= str.length()) {
      return str.toLowerCase();
    }
    if (length == 0) {
      return str;
    }
    return str.substring(0, length).toLowerCase() + str.substring(length);
  }

  // = toUpperCase
  public static Object stringToUpperCaseData;
  public static String stringToUpperCase(String str, int length) {
    if (length < 0) {
      // Allow offset from end of string
      length += str.length();
    }
    if (length < 0 || length >= str.length()) {
      return str.toUpperCase();
    }
    if (length == 0) {
      return str;
    }
    return str.substring(0, length).toUpperCase() + str.substring(length);
  }

  // = substring
  public static Object stringSubstringData;
  public static String stringSubstring(String str, String source, int offset, int start, int end) {
    try {
      if (start < 0) {
        start += str.length();
      }
      if (end < 0) {
        end += str.length();
      }
      if (end < 0 || end == Integer.MAX_VALUE) {
        return str.substring(start);
      }
      return str.substring(start, end);
    }
    catch (Exception e) {
      throw new RuntimeError("Substring error", source, offset, e);
    }
  }

  // = asNum
  public static Object stringAsNumData;
  public static long stringAsNum(String str, String source, int offset, int base) {
    if (base < Character.MIN_RADIX) { throw new RuntimeError("Base was " + base + " but must be at least " + Character.MIN_RADIX, source, offset); }
    if (base > Character.MAX_RADIX) { throw new RuntimeError("Base was " + base + " but must be no more than " + Character.MAX_RADIX, source, offset); }
    if (str.isEmpty())               { throw new RuntimeError("Empty string cannot be converted to a number", source, offset); }
    try {
      return Long.parseUnsignedLong(str, base);
    }
    catch (NumberFormatException e) {
      throw new RuntimeError("Input '" + str + "': invalid character for number with base " + base + " or number is too large", source, offset);
    }
  }

  // = split
  public static Object stringSplitData;
  public static JactlIterator stringSplit(String str, String source, int offset, String regex, String modifiers) {
    if (regex == null)   { return JactlIterator.of(str); }
    if (regex.isEmpty()) { return JactlIterator.stringIterator(str); }
    return JactlIterator.stringSplitIterator(str, regex, modifiers, source, offset);
  }
}
