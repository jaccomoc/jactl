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

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.jactl.JactlType.*;

public class BuiltinFunctions {

  private static Map<String,FunctionDescriptor> globalFunctions = new HashMap<>();
  private static boolean initialised = false;

  static {
    BuiltinFunctions.registerBuiltinFunctions();
  }

  public static void registerBuiltinFunctions() {
    if (!initialised) {
      // Collection methods
      Jactl.method(LIST)
           .name("size")
           .impl(BuiltinFunctions.class, "listSize")
           .register();

      Jactl.method(OBJECT_ARR)
           .name("size")
           .impl(BuiltinFunctions.class, "objArrSize")
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
           .name("filter")
           .asyncInstance(true)
           .asyncParam("predicate", null)
           .impl(BuiltinFunctions.class, "iteratorFilter")
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
           .param("data", new Object[0])
           .impl(BuiltinFunctions.class, "sleep")
           .register();

      Jactl.function()
           .name("stream")
           .asyncParam("closure")
           .impl(BuiltinFunctions.class, "stream")
           .register();

      initialised = true;
    }
  }

  public static FunctionDescriptor lookupGlobalFunction(String name) {
    return globalFunctions.get(name);
  }

  public static MethodHandle lookupMethodHandle(String name) {
    FunctionDescriptor descriptor = globalFunctions.get(name);
    if (descriptor == null) {
      throw new IllegalStateException("Internal error: attempt to get MethodHandle to unknown builtin function " + name);
    }
    return descriptor.wrapperHandle;
  }

  public static Collection<FunctionDescriptor> getBuiltinFunctions() {
    return globalFunctions.values();
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
    }
  }

  public static void deregisterFunction(JactlType type, String name) {
    Functions.deregisterMethod(type, name);
  }

  public static void deregisterFunction(String name) {
    globalFunctions.remove(name);
  }

  /////////////////////////////////////
  // Global Functions
  /////////////////////////////////////

  // = sleep
  public static Object sleepData;
  public static Object sleep(Continuation c, long timeMs, Object data) {
    if (timeMs >= 0) {
      Continuation.suspendNonBlocking((JactlContext context, Consumer<Object> resumer) -> {
        context.scheduleEvent(() -> resumer.accept(data), timeMs);
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
  public static String nextLine(Continuation c) {
    var input = RuntimeState.getState().input;
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
        Continuation.suspendBlocking(() -> readLine(input));
      }
    }
    catch (IOException ignored) {}
    if (result == null) {
      RuntimeState.setInput(null);
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
  public static Iterator stream(Continuation c, String source, int offset, MethodHandle closure) {
    return new StreamIterator(source, offset, closure);
  }

  // = abs
  public static Object intAbsData;
  public static int intAbs(int n)                   { return n < 0 ? -n : n; }
  public static Object longAbsData;
  public static long longAbs(long n)                { return n < 0 ? -n : n; }
  public static Object doubleAbsData;
  public static double doubleAbs(double n)          { return n < 0 ? -n : n; }
  public static Object decimalAbsData;
  public static BigDecimal decimalAbs(BigDecimal n) { return n.abs(); }

  /////////////////////////////////////
  // Methods
  /////////////////////////////////////

  // = asChar
  public static Object intAsCharData;
  public static String intAsChar(int c) { return String.valueOf((char)c); }

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

  // = sqrt
  public static Object numberSqrtData;
  public static Object numberSqrt(Number num, String source, int offset) {
    if (num.doubleValue() < 0) {
      throw new RuntimeError("Attempt to take square root of negative number: " + num, source, offset);
    }
    if (num instanceof BigDecimal) {
      return ((BigDecimal)num).sqrt(MathContext.DECIMAL64);
    }
    double result = Math.sqrt(num.doubleValue());
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
    if (num instanceof BigDecimal && power instanceof Integer && (int)power >= 0) {
      try {
        return ((BigDecimal)num).pow((int)power);
      }
      catch (Exception e) {
        throw new RuntimeError("Illegal request: raising " + num + " to " + power, source, offset);
      }
    }
    double result = Math.pow(num.doubleValue(), power.doubleValue());
    if (Double.isNaN(result)) {
      throw new RuntimeError("Illegal request: raising " + num + " to " + power, source, offset);
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

  // = toString

  public static Object objectToStringData;
  public static String objectToString(Object obj, int indent) { return RuntimeUtils.toString(obj, indent); }

  // = remove

  public static Object mapRemoveData;
  public static Object mapRemove(Map map, String field) {
    return map.remove(field);
  }

  public static Object listRemoveData;
  public static Object listRemove(List list, String source, int offset, int index) {
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
  public static List listAdd(List list, Object elem) {
    list.add(elem);
    return list;
  }

  // = addAt

  public static Object listAddAtData;
  public static List listAddAt(List list, String source, int offset, int index, Object elem) {
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
  public static int mapSize(Map map) { return map.size(); }

  public static Object listSizeData;
  public static int listSize(List list) {
    return list.size();
  }

  public static Object objArrSizeData;
  public static int objArrSize(Object[] list) { return list.length; }

  public static Object iteratorSizeData;
  public static int iteratorSize(Object iterable, Continuation c) {
    try {
      List list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (List) c.getResult();
      return list.size();
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSizeHandle.bindTo(iterable), 0, null, null);
    }
  }
  private static MethodHandle iteratorSizeHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSize$c", Object.class, Object.class, Continuation.class);
  public static Object iteratorSize$c(Object iterable, Continuation c) {
    return iteratorSize(iterable, c);
  }

  ////////////////////////////////

  // = sublist
  public static Object listSubListData;
  public static List listSubList(List list, String source, int offset, int start, int end) {
    try {
      if (end == Integer.MAX_VALUE) {
        end = list.size();
      }
      return list.subList(start, end);
    }
    catch (Exception e) {
      throw new RuntimeError("SubList error", source, offset, e);
    }
  }

  public static Object iteratorSubListData;
  public static List iteratorSubList(Object iterable, Continuation c, String source, int offset, int start, int end) {
    source = c == null ? source : (String)c.localObjects[0];
    offset = c == null ? offset : (int)c.localPrimitives[0];
    start  = c == null ? start  : (int)c.localPrimitives[1];
    end    = c == null ? end    : (int)c.localPrimitives[2];
    try {
      List list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (List) c.getResult();
      return listSubList(list, source, offset, start, end == Integer.MAX_VALUE ? list.size() : end);
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSubListHandle.bindTo(iterable), 0, new long[]{offset, start,end}, new Object[]{source});
    }
  }
  private static MethodHandle iteratorSubListHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSubList$c", Object.class, Object.class, Continuation.class);
  public static Object iteratorSubList$c(Object iterable, Continuation c) {
    return iteratorSubList(iterable, c, null, 0, 0, 0);
  }

  ////////////////////////////////

  // = filter

  public static Object iteratorFilterData;
  public static Iterator iteratorFilter(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new FilterIterator(iter, source, offset, closure);
  }

  ////////////////////////////////

  // = reverse

  public static Object iteratorReverseData;
  public static List iteratorReverse(Object iterable, Continuation c) {
    try {
      List list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (List) c.getResult();
      List result = new ArrayList(list.size());
      for (int i = list.size() - 1; i >= 0; i--) {
        result.add(list.get(i));
      }
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorReverseHandle.bindTo(iterable), 0, null, null);
    }
  }
  private static MethodHandle iteratorReverseHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorReverse$c", Object.class, Object.class, Continuation.class);
  public static Object iteratorReverse$c(Object iterable, Continuation c) {
    return iteratorReverse(iterable, c);
  }

  ////////////////////////////////

  // = unique

  public static Object iteratorUniqueData;
  public static Iterator iteratorUnique(Object iterable, Continuation c, String source, int offset) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new UniqueIterator(iter);
  }

  ////////////////////////////////

  // = skip

  public static Object iteratorSkipData;
  public static Iterator iteratorSkip(Object iterable, Continuation c, String source, int offset, int count) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    if (count >= 0) {
      return new FilterIterator(iter, source, offset, shouldNotSkipHandle.bindTo(count).bindTo(new AtomicInteger(0)));
    }
    return new SkipIterator(iter, -count);
  }

  private static MethodHandle shouldNotSkipHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "shouldNotSkip",
                                                                              Object.class, Integer.class, AtomicInteger.class,
                                                                              Continuation.class, String.class, int.class, Object[].class);
  public static Object shouldNotSkip(Integer skipAmount, AtomicInteger index, Continuation ignore1, String ignore2, int ignore3, Object[] ignore4) {
    return index.getAndIncrement() >= skipAmount;
  }

  ////////////////////////////////

  // = limit

  public static Object iteratorLimitData;
  public static Iterator iteratorLimit(Object iterable, Continuation c, String source, int offset, int limit) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    if (limit >= 0) {
      return new LimitIterator(iter, limit);
    }
    return new NegativeLimitIterator(iter, -limit);
  }

  ////////////////////////////////

  // = grouped

  public static Object iteratorGroupedData;
  public static Iterator iteratorGrouped(Object iterable, Continuation c, String source, int offset, int size) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    if (size == 0) {
      return iter;
    }
    if (size < 0) {
      throw new RuntimeError("Value for grouped() must be >= 0 (was " + size + ")", source, offset);
    }
    return new GroupedIterator(iter, source, offset, size);
  }

  ////////////////////////////////

  // = map

  public static Object iteratorMapData;
  public static Iterator iteratorMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure);
  }

  ////////////////////////////////

  // = mapWithIndex

  public static Object iteratorMapWithIndexData;
  public static Iterator iteratorMapWithIndex(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure, true);
  }

  ////////////////////////////////

  // = flatMap

  public static Object iteratorFlatMapData;
  public static Iterator iteratorFlatMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new FlatMapIterator(iter, source, offset, closure);
  }

  //////////////////////////////////

  // = each

  public static Object iteratorEachData;
  public static Object iteratorEach(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorEach$c(RuntimeUtils.createIterator(iterable), source, offset, closure, null);
  }
  public static Object iteratorEach$c(Iterator iter, String source, Integer offset, MethodHandle closure, Continuation c) {
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
            Object ignored = closure == null ? null : closure.invokeExact((Continuation)null, source, (int)offset, new Object[]{ elem });
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
      throw new Continuation(cont, iteratorEachHandle.bindTo(iter).bindTo(source).bindTo(offset).bindTo(closure),
                             methodLocation + 1, null, null);
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
  private static MethodHandle iteratorEachHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorEach$c",
                                                                             Object.class, Iterator.class, String.class,
                                                                             Integer.class, MethodHandle.class, Continuation.class);

  ////////////////////////////////

  // = collect

  public static Object iteratorCollectData;
  public static Object iteratorCollect(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorCollect$c(RuntimeUtils.createIterator(iterable), new ArrayList(), (list, elem) -> { ((List)list).add(elem); return list; }, source, offset, closure, null);
  }
  public static Object iteratorCollect$c(Iterator iter, Object result, BiFunction<Object,Object,Object> collector, String source, Integer offset, MethodHandle closure, Continuation c) {
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
            transformedElem = closure == null ? elem : closure.invokeExact((Continuation)null, source, (int)offset, new Object[]{ elem });
            methodLocation = 6;
            break;
          case 5:
            transformedElem = c.getResult();
            methodLocation = 6;
            break;
          case 6:
            result = collector.apply(result, transformedElem);
            methodLocation = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorCollectHandle.bindTo(iter).bindTo(result).bindTo(collector).bindTo(source).bindTo(offset).bindTo(closure),
                             methodLocation + 1, null, new Object[] { result });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  private static MethodHandle iteratorCollectHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorCollect$c",
                                                                                Object.class, Iterator.class, Object.class,
                                                                                BiFunction.class, String.class, Integer.class,
                                                                                MethodHandle.class, Continuation.class);

  /////////////////////////////

  // = reduce

  public static Object iteratorReduceData;
  public static Object iteratorReduce(Object iterable, Continuation c, String source, int offset, Object initialValue, MethodHandle closure) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, initialValue, (value, elem) -> {
      elem = Arrays.asList(value, elem);
      try {
        return closure.invokeExact((Continuation)null, source, (int)offset, new Object[]{ elem });
      }
      catch (Continuation | RuntimeError e) {
        throw e;
      }
      catch (Throwable t) {
        throw new RuntimeError("Unexpected error", source, offset, t);
      }
    }, null, c);
  }
  public static Object iteratorReduce$c(Iterator iter, String source, Integer offset, Object value,
                                        BiFunction<Object,Object,Object> invoker,
                                        Function<Object,Object> resultProcessor,
                                        Continuation c) {
    int methodLocation = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext         = true;
      Object  elem            = null;
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
            if (!hasNext) {            // EXIT: exit loop when underlying iterator has no more elements
              return resultProcessor == null ? value : resultProcessor.apply(value);
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
            value = invoker.apply(value, elem);
            methodLocation = 6;
            break;
          case 5:
            value = c.getResult();
            methodLocation = 6;
            break;
          case 6:
            methodLocation = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorReduceHandle.bindTo(iter).bindTo(source).bindTo(offset)
                                                       .bindTo(value).bindTo(invoker).bindTo(resultProcessor),
                             methodLocation + 1, null, null);
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  private static MethodHandle iteratorReduceHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorReduce$c",
                                                                                Object.class, Iterator.class, String.class, Integer.class,
                                                                                Object.class, BiFunction.class, Function.class, Continuation.class);

  /////////////////////////////

  // = join

  public static Object iteratorJoinData;
  public static String iteratorJoin(Object iterable, Continuation c, String source, int offset, String joinStr) {
    BiFunction<Object,Object,Object> joiner = (str,elem) -> {
      if (str.equals("")) {
        return RuntimeUtils.toString(elem);
      }
      else {
        String strValue = (String)str;
        return joinStr == null ? strValue.concat(elem.toString())
                               : strValue.concat(joinStr).concat(RuntimeUtils.toString(elem));
      }
    };
    return (String)iteratorCollect$c(RuntimeUtils.createIterator(iterable), "", joiner, source, offset, null, c);
  }

  /////////////////////////////

  // = avg

  public static Object iteratorAvgData;
  public static Object iteratorAvg(Object iterable, Continuation c, String source, int offset) {
    int[] counter = new int[]{0};
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, BigDecimal.ZERO,
                            (value,elem) -> {
                              counter[0]++;
                              return addNumbers(value, elem, source, offset);
                            },
                            (value) -> {
                              if (counter[0] == 0) {
                                throw new RuntimeError("Empty list for avg() function", source, offset);
                              }
                              if (value instanceof Double)                           { value = BigDecimal.valueOf((double)value); }
                              if (value instanceof Integer || value instanceof Long) { value = BigDecimal.valueOf(((Number)value).longValue()); }
                              return RuntimeUtils.decimalDivide((BigDecimal)value, BigDecimal.valueOf(counter[0]), Utils.DEFAULT_MIN_SCALE, source, offset);
                            },
                            c);
  }

  // = sum

  public static Object iteratorSumData;
  public static Object iteratorSum(Object iterable, Continuation c, String source, int offset) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, 0,
                            (value,elem) -> addNumbers(value, elem, source, offset),
                            (value) -> value,
                            c);
  }

  private static Object addNumbers(Object valueObj, Object elemObj, String source, int offset) {
    if (!(elemObj instanceof Number)) {
      throw new RuntimeError("Non-numeric element in list (type is " + RuntimeUtils.className(elemObj) + ")", source, offset);
    }
    if (valueObj instanceof BigDecimal || elemObj instanceof BigDecimal) {
      BigDecimal value = valueObj instanceof BigDecimal ? (BigDecimal)valueObj : RuntimeUtils.toBigDecimal(valueObj);
      BigDecimal elem  = elemObj instanceof BigDecimal ? (BigDecimal)elemObj : RuntimeUtils.toBigDecimal(elemObj);
      return value.add(elem);
    }
    if (valueObj instanceof Double || elemObj instanceof Double) {
      double value = ((Number)valueObj).doubleValue();
      double elem  = ((Number)elemObj).doubleValue();
      return value + elem;
    }
    if (valueObj instanceof Long || elemObj instanceof Long) {
      return ((Number)valueObj).longValue() + ((Number)elemObj).longValue();
    }
    return (int)valueObj + (int)elemObj;
  }

  // = min/max

  public static Object iteratorMinData;
  public static Object iteratorMin(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, null,
                            (value,elem) -> minMaxInvoker(source, offset, closure, false, value, elem, c),
                            (value) -> value == null ? null : ((Object[])value)[1],
                            c);
  }

  public static Object iteratorMaxData;
  public static Object iteratorMax(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, null,
                            (value,elem) -> minMaxInvoker(source, offset, closure, true, value, elem, c),
                            (value) -> value == null ? null : ((Object[])value)[1],
                            c);
  }

  private static final MethodHandle minMaxInvokerHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "minMaxInvoker",
                                                                                    Object.class, String.class, Integer.class,
                                                                                    MethodHandle.class, Boolean.class, Object.class,
                                                                                    Object.class, Continuation.class);
  public static Object minMaxInvoker(String source, Integer offset, MethodHandle closure, Boolean isMax, Object currentValueObj, Object elem, Continuation c) {
    Object[] currentValue = (Object[])currentValueObj;
    Object[] nextValue;
    if (c != null) {
      nextValue = new Object[]{c.getResult(), elem };
    }
    else {
      if (closure == null) {
        nextValue = new Object[]{ elem, elem };
      }
      else {
        try {
          nextValue = new Object[] { closure.invokeExact((Continuation) null, source, (int)offset, new Object[]{elem}), elem };
        }
        catch (Continuation cont) {
          throw new Continuation(cont, minMaxInvokerHandle.bindTo(source).bindTo(offset).bindTo(closure)
                                                          .bindTo(isMax).bindTo(currentValue).bindTo(elem),
                                 0, null, null);
        }
        catch (RuntimeError e) {
          throw e;
        }
        catch (Throwable t) {
          throw new RuntimeError("Unexpected error", source, offset, t);
        }
      }
    }
    if (currentValue == null) {
      return nextValue;
    }
    int compare = RuntimeUtils.compareTo(currentValue[0], nextValue[0], source, offset);
    return compare < 0 ? (isMax ? nextValue : currentValue)
                       : (isMax ? currentValue : nextValue);
  }

  //////////////////////////////////////

  // = collectEntries

  public static Object iteratorCollectEntriesData;
  public static Object iteratorCollectEntries(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    BiFunction<Object,Object,Object> collector = (mapObj,elem) -> {
      RuntimeUtils.addMapEntry((Map) mapObj, elem, source, offset);
      return mapObj;
    };
    return iteratorCollect$c(RuntimeUtils.createIterator(iterable), new HashMap(), collector, source, offset, closure, null);
  }

  /////////////////////////////

  // = sort

  private static MethodHandle iteratorSortHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSort$c",
                                                                             Object.class, Object.class, String.class,
                                                                             Integer.class,MethodHandle.class, Continuation.class);

  public static Object iteratorSort$c(Object iterable, String source, Integer offset, MethodHandle closure, Continuation c) {
    return iteratorSort(iterable, c, source, offset, closure);
  }

  public static Object iteratorSortData;
  public static List iteratorSort(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    List result = null;
    int location = c == null ? 0 : c.methodLocation;
    try {
      if (location == 0) {
        result = iterable instanceof List ? new ArrayList((List) iterable)
                                          : RuntimeUtils.convertIteratorToList(RuntimeUtils.createIterator(iterable), null);
        location = 2;
      }
      if (location == 1) {
        result = (List) c.getResult();
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
          return (List) mergeSort(result, closure, source, offset, null);
        }
      }
      else {
        return (List) c.getResult();
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSortHandle.bindTo(iterable).bindTo(source).bindTo((Integer)offset).bindTo(closure),
                             location + 1, null, null);
    }
  }

  private static MethodHandle mergeSortHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "mergeSort",
                                                                      Object.class, List.class, MethodHandle.class,
                                                                      String.class, Integer.class, Continuation.class);

  /**
   * Bottom up merge sort.
   */
  public static Object mergeSort(List list, MethodHandle closure, String source, Integer offset, Continuation c) {
    int size = list.size();
    List src;
    List dst;
    int width = 1;

    if (c == null) {
      src = list;
      dst = new ArrayList(size);
    }
    else {
      src = (List)c.localObjects[0];
      dst = (List)c.localObjects[1];
      width = (int)c.localPrimitives[0];
    }
    for (; width < size; width *= 2) {
      int i = c == null ? 0 : (int)c.localPrimitives[1] + 2 * width;
      c = null;
      for (; i <= size; i += 2 * width) {
        final var start2 = Math.min(i + width, size);
        try {
          merge(src, dst, i, start2, start2, Math.min(i + 2 * width, size), closure, source, offset, null);
        }
        catch (Continuation cont) {
          throw new Continuation(cont, mergeSortHandle.bindTo(list).bindTo(closure).bindTo(source).bindTo(offset),
                                 0, new long[] { width, i }, new Object[] { src, dst });
        }
      }
      List tmp = src;
      src = dst;
      dst = tmp;
    }
    return src;
  }

  private static MethodHandle mergeHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "merge",
                                                                      Object.class, List.class, List.class, Integer.class,
                                                                      Integer.class, Integer.class, Integer.class, MethodHandle.class,
                                                                      String.class, Integer.class, Continuation.class);

  // Merge two sorted sublists of src into same position in dst
  public static Object merge(List src, List dst, Integer start1, Integer end1, Integer start2, Integer end2, MethodHandle comparator, String source, Integer offset, Continuation c) {
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
            comparison = comparator.invokeExact((Continuation) null, source, (int)offset, new Object[]{ List.of(elem1, elem2) });
          }
          catch (Continuation cont) {
            throw new Continuation(cont, mergeHandle.bindTo(src).bindTo(dst).bindTo(start1).bindTo(end1).bindTo(start2).bindTo(end2).bindTo(comparator).bindTo(source).bindTo(offset),
                                   0, new long[] { i1, i2, dstPos }, null);
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
  public static Iterator stringLines(String str) {
    return RuntimeUtils.lines(str).iterator();
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
  public static Iterator stringSplit(String str, String source, int offset, String regex, String modifiers) {
    if (regex == null) {
      return new Iterator() {
        int i = 0;
        @Override public boolean hasNext() { return i++ == 0; }
        @Override public Object next()     { return str; }
      };
    }
    if (regex.isEmpty()) {
      return RuntimeUtils.createIterator(str);
    }
    var matcher = RuntimeUtils.getMatcher(str, regex, modifiers, source, offset);
    return new Iterator() {
      int index = 0;
      boolean last = false;
      boolean hasNext = false;
      boolean findNext = true;
      @Override public boolean hasNext() {
        if (!findNext) { return hasNext; }
        findNext = false;
        if (!last && matcher.find()) {
          return hasNext = true;
        }
        if (!last) {
          last = true;
          return hasNext = true;
        }
        return false;
      }
      @Override public Object next() {
        if (hasNext()) {
          findNext = true;
          if (last) {
            return str.substring(index);
          }
          int    nextIndex = matcher.start();
          String result    = str.substring(index, nextIndex);
          index = matcher.end();
          return result;
        }
        throw new IllegalStateException("Internal error: split() - no more matches");
      }
    };
  }
}