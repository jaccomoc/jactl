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

package jacsal.runtime;

import jacsal.*;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jacsal.JacsalType.*;

public class BuiltinFunctions {

  private static Map<String,FunctionDescriptor> globalFunctions = new HashMap<>();
  private static boolean initialised = false;

  static {
    BuiltinFunctions.registerBuiltinFunctions();
  }

  public static void registerBuiltinFunctions() {
    if (!initialised) {
      // Collection methods
      registerMethod("size", "listSize", LIST, false, 0, List.of(0));
      registerMethod("size", "objArrSize", OBJECT_ARR, false, 0, List.of(0));
      registerMethod("size", "mapSize", MAP, false, 0, List.of(0));
      registerMethod("size", "iteratorSize", ITERATOR, false, 0, List.of(0));
      registerMethod("remove", "mapRemove", MAP, false, 1, List.of(0));
      registerMethod("remove", "listRemove", LIST, true, 1, List.of(0));
      registerMethod("add", "listAdd", LIST, true, 1, List.of(0));
      registerMethod("reverse", "iteratorReverse", ITERATOR, false, 0, List.of(0));
      registerMethod("each", "iteratorEach", ITERATOR, true, 0, List.of(0,1));
      registerMethod("reduce", "iteratorReduce", ITERATOR, true, 2, List.of(0,2));
      registerMethod("min", "iteratorMin", ITERATOR, true, 0, List.of(0,1));
      registerMethod("max", "iteratorMax", ITERATOR, true, 0, List.of(0,1));
      registerMethod("avg", "iteratorAvg", ITERATOR, true, 0, List.of(0));
      registerMethod("sum", "iteratorSum", ITERATOR, true, 0, List.of(0));
      registerMethod("skip", "iteratorSkip", ITERATOR, true, 1, List.of(0));
      registerMethod("limit", "iteratorLimit", ITERATOR, true, 1, List.of(0));
      registerMethod("unique", "iteratorUnique", ITERATOR, true, 0, List.of(0));
      registerMethod("collect", "iteratorCollect", ITERATOR, true, 0, List.of(0,1));
      registerMethod("collectEntries", "iteratorCollectEntries", ITERATOR, true, 0, List.of(0,1));
      registerMethod("join", "iteratorJoin", ITERATOR, true, 0, List.of(0));
      registerMethod("sort", "iteratorSort", ITERATOR, true, 0, List.of(0,1));
      registerMethod("map", "iteratorMap", ITERATOR, true, 0, List.of(0,1));
      registerMethod("mapWithIndex", "iteratorMapWithIndex", ITERATOR, true, 0, List.of(0,1));
      registerMethod("flatMap", "iteratorFlatMap", ITERATOR, true, 0, List.of(0,1));
      registerMethod("grouped", "iteratorGrouped", ITERATOR, true, 1, List.of(0));
      registerMethod("filter", "iteratorFilter", ITERATOR, true, 0, List.of(0,1));
      registerMethod("subList", "listSubList", LIST, true, 1);
      registerMethod("subList", "iteratorSubList", ITERATOR, true, 1);

      // String methods
      registerMethod("lines", "stringLines", STRING, false);
      registerMethod("length", "stringLength", STRING, false);
      registerMethod("size", "stringLength", STRING, false);
      registerMethod("toLowerCase", "stringToLowerCase", STRING, false, 0);
      registerMethod("toUpperCase", "stringToUpperCase", STRING, false, 0);
      registerMethod("substring", "stringSubstring", STRING, true, 1);
      registerMethod("split", "stringSplit", STRING, true, 0);
      registerMethod("asNum", "stringAsNum", STRING, true, 0);

      // int methods
      registerMethod("asChar", "intAsChar", INT, false, 0);
      registerMethod("toBase", "intToBase", INT, true, 1);
      registerMethod("sqr", "intSqr", INT, true, 0);
      registerMethod("abs", "intAbs", INT, false, 0);

      // long methods
      registerMethod("toBase", "longToBase", LONG, true, 1);
      registerMethod("sqr", "longSqr", LONG, true, 0);
      registerMethod("abs", "longAbs", LONG, false, 0);

      // double methods
      registerMethod("sqr", "doubleSqr", DOUBLE, true, 0);
      registerMethod("abs", "doubleAbs", DOUBLE, false, 0);

      // decimal methods
      registerMethod("sqr", "decimalSqr", DECIMAL, true, 0);
      registerMethod("abs", "decimalAbs", DECIMAL, false, 0);

      // Number methods
      registerMethod("pow", "numberPow", NUMBER, true, 1);
      registerMethod("sqrt", "numberSqrt", NUMBER, true, 0);

      // Object methods
      registerMethod("toString", "objectToString", ANY, false, 0);

      // Global functions
      registerGlobalFunction("timestamp", "timestamp", false, 0);
      registerGlobalFunction("nanoTime", "nanoTime", false, 0);
      registerGlobalFunction("sprintf", "sprintf", true, 1);
      registerGlobalFunction("sleep", "sleep", false, 1);
      registerGlobalFunction("nextLine", "nextLine", false, 0);
      registerGlobalFunction("stream", "stream", true, 1, List.of(0));
      registerGlobalFunction("eval", "eval", false, 1);

      initialised = true;
    }
  }

  public static FunctionDescriptor lookupGlobalFunction(String name) {
    return globalFunctions.get(name);
  }

  public static MethodHandle lookupMethodHandle(String name) {
    FunctionDescriptor descriptor = globalFunctions.get(name);
    if (descriptor == null) {
      throw new IllegalStateException("Internal error: attept to get MethodHandle to unknown builtin function " + name);
    }
    return descriptor.wrapperHandle;
  }

  public static Collection<FunctionDescriptor> getBuiltinFunctions() {
    return globalFunctions.values();
  }

  ///////////////////////////////////////////////////

  private static void registerGlobalFunction(String name, String methodName, boolean needsLocation, int mandatoryArgCount) {
    var descriptor = getFunctionDescriptor(name, methodName,null, needsLocation, mandatoryArgCount);
    globalFunctions.put(name, descriptor);
  }

  private static void registerGlobalFunction(String name, String methodName, boolean needsLocation, int mandatoryArgCount,
                                             List<Integer> asyncArgs) {
    var descriptor = getFunctionDescriptor(name, methodName,null, needsLocation, mandatoryArgCount);
    descriptor.asyncArgs = asyncArgs;
    globalFunctions.put(name, descriptor);
  }

  private static void registerMethod(String name, String methodName, JacsalType objType, boolean needsLocation) {
    registerMethod(name, methodName, objType, needsLocation, -1);
  }

  private static void registerMethod(String name, String methodName, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    registerMethod(name, methodName, objType, needsLocation, mandatoryArgCount, List.of());
  }

  /**
   * <p>Register builtin method for given object type. We lookup the given static method on this class (BuiltinFunctions)
   * and use that as the implementation for the method. We assume another method with "Wrapper" appeneded to the name
   * exists and will use that method when method is invoked from places where we need to dynamically lookup the method
   * or when the number of arguments does not match (i.e. some optional args not supplied).</p>
   * <p>The object type specifies the type of Jacsal objects for which we want the method to apply. If null is passed as
   * a value we derive the type from the type of the first argument of the static method. We allow a different type to
   * be supplied to support scenarios where we want a method to work for Iterator types (List, Map, Iterator, Object[])
   * and for which not natural super class exists. In this case the type of the first argument is Object but the objType
   * parameter will be JacsalType.ITERATOR .</p>
   * <p>Methods are flagged as async if arg1 (the arg after the 0th arg which is the object on which the method applies)
   * has type Continuation. This means that when invoking the method we need to generate the scaffolding that allows
   * Continuations to be thrown and caught and capture state. As well as flagging the method as async or not we allow
   * a list of argument numbers to be passed in for situations where the invocation is only async if one of the given
   * arguments itself is async.</p>
   * <p>For example,
   * <pre>  x.map{ ... }</pre>
   * is only async if the closure passed in is itself async. However, if the calls are chained:
   * <pre>  x.map{ ... }.filter{ ... }</pre>
   * then the collection methods (map, filter, each,
   * collect, etc) are async if either the closure passed in is async or if any of the preceding calls in the chain
   * were async.
   * <p>Since the preceding call is the object on which the current method is being applied, we can think of such a chain
   * of method calls like this:</p>
   * <pre>  filter(map(x, {...}), {...})</pre>
   * <p>So, the call is async if arg0 is async or if arg1 is async. This means that for filter, map, etc. we pass in
   * List.of(0,1) to indicate that if arg0 or arg1 is async then the method call is async.</p>
   * @param name                the name that the method will have in Jacsal
   * @param methodName          the name of the static method in this class that provides the implementation
   * @param objType             the Jacsal type for which the method applies
   * @param needsLocation       true if we need to pass the location (source + offset) at invocation time (for runtime errors)
   * @param mandatoryArgCount   how many args are mandatory
   * @param asyncArgs           List of int values specifying which arguments contribute to async behaviour
   */
  private static void registerMethod(String name, String methodName, JacsalType objType,
                                     boolean needsLocation, int mandatoryArgCount,
                                     List<Integer> asyncArgs) {
    var descriptor = getMethodDescriptor(name, methodName, objType, needsLocation, mandatoryArgCount);
    descriptor.asyncArgs = asyncArgs;
    Functions.registerMethod(descriptor);
  }

  /**
   * Generate FunctionDescriptor for a builtin method
   */
  private static FunctionDescriptor getMethodDescriptor(String name, String methodName, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
    Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
    if (method == null) {
      throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
    }

    Class[]    paramClasses = method.getParameterTypes();

    // Sometimes the first arg type is different to the type that the method acts on (e.g. using Object for
    // methods that act on ITERATOR since ITERATOR means Iterable or Iterator or Object[])
    JacsalType firstArgType = paramClasses.length > 0 ? JacsalType.typeFromClass(paramClasses[0]) : null;
    if (objType == null) {
      objType = firstArgType;
    }

    JacsalType returnType   = JacsalType.typeFromClass(method.getReturnType());
    boolean    isAsync      = paramClasses.length > 1 && paramClasses[1].equals(Continuation.class);

    // Reserver params for object plus optionally: Continuation, String source, int offset
    int reservedParams = 1 + (isAsync ? 1 : 0) + (needsLocation ? 2 : 0);
    List<JacsalType> paramTypes = Arrays.stream(paramClasses)
                                        .skip(reservedParams)
                                        .map(JacsalType::typeFromClass)
                                        .collect(Collectors.toList());

    var wrapperMethod = methodName + "Wrapper";
    MethodHandle wrapperHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                           wrapperMethod,
                                                           Object.class,    // Return type
                                                           firstArgType.boxed().classFromType(),
                                                           Continuation.class,
                                                           String.class,
                                                           int.class,
                                                           Object[].class);
    if (mandatoryArgCount < 0) {
      mandatoryArgCount = paramTypes.size();
    }
    boolean varargs = paramTypes.size() > 0 && paramTypes.get(paramTypes.size() - 1).is(OBJECT_ARR);
    var descriptor = new FunctionDescriptor(objType,
                                            firstArgType,
                                            name,
                                            returnType,
                                            paramTypes,
                                            varargs,
                                            mandatoryArgCount,
                                            Type.getInternalName(BuiltinFunctions.class),
                                            methodName,
                                            needsLocation,
                                            wrapperHandle);
    descriptor.wrapperMethod = wrapperMethod;
    descriptor.isBuiltin = true;
    descriptor.isStatic = true;
    descriptor.isAsync = isAsync;
    descriptor.isGlobalFunction = false;
    return descriptor;
  }

  /**
   * Generate FunctionDescriptor for global function
   */
  private static FunctionDescriptor getFunctionDescriptor(String name, String methodName, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
    Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
    if (method == null) {
      throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
    }

    Class[]    paramClasses = method.getParameterTypes();
    JacsalType returnType   = JacsalType.typeFromClass(method.getReturnType());
    boolean    isAsync      = paramClasses.length > 0 && paramClasses[0].equals(Continuation.class);

    int reservedParams = (needsLocation ? 2 : 0) + (isAsync ? 1 : 0);
    List<JacsalType> paramTypes = Arrays.stream(paramClasses)
                                        .skip(reservedParams)
                                        .map(JacsalType::typeFromClass)
                                        .collect(Collectors.toList());

    var wrapperMethod = methodName + "Wrapper";
    MethodHandle wrapperHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                           wrapperMethod,
                                                           Object.class,    // Return type
                                                           Continuation.class,
                                                           String.class,
                                                           int.class,
                                                           Object[].class);

    if (mandatoryArgCount < 0) {
      mandatoryArgCount = paramTypes.size();
    }

    boolean varargs = paramTypes.size() > 0 && paramTypes.get(paramTypes.size() - 1).is(OBJECT_ARR);
    var descriptor = new FunctionDescriptor(null,
                                            null,
                                            name,
                                            returnType,
                                            paramTypes,
                                            varargs,
                                            mandatoryArgCount,
                                            Type.getInternalName(BuiltinFunctions.class),
                                            methodName,
                                            needsLocation,
                                            wrapperHandle);

    descriptor.wrapperMethod = wrapperMethod;
    descriptor.isBuiltin = true;
    descriptor.isStatic = true;
    descriptor.isAsync = isAsync;
    descriptor.isGlobalFunction = true;
    return descriptor;
  }

  /////////////////////////////////////
  // Global Functions
  /////////////////////////////////////

  // = sleep
  public static Object sleep(Continuation c, long timeMs, Object result) {
    if (timeMs >=0) {
      throw Continuation.create(() -> {
        doSleep(timeMs);
        return result;
      });
    }
    return result;
  }
  public static Object sleepWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, null, 2, source, offset);
    try {
      sleep(c, ((Number)args[0]).longValue(), args.length == 2 ? args[1] : null);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert argument of type " + RuntimeUtils.className(args[0]) + " to long", source, offset);
    }
    return null;
  }
  private static void doSleep(long ms) {
    try { if (ms > 0) Thread.sleep(ms); } catch (InterruptedException e) {}
  }

  // = timestamp
  public static long timestamp() { return System.currentTimeMillis(); }
  public static Object timestampWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return System.currentTimeMillis();
  }

  // = nanoTime
  public static long nanoTime() { return System.nanoTime(); }
  public static Object nanoTimeWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return System.nanoTime();
  }

  // = sprintf
  public static String sprintf(String source, int offset, String format, Object... args) {
    try {
      return String.format(format, args);
    }
    catch (IllegalFormatException e) {
      throw new RuntimeError("Bad format string", source, offset, e);
    }
  }
  public static Object sprintfWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, null, -1, source, offset);
    try {
      return sprintf(source, offset, (String)args[0], Arrays.copyOfRange(args, 1, args.length));
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert argument of type " + RuntimeUtils.className(args[0]) + " to String", source, offset);
    }
  }

  // = nextLine()
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
        throw Continuation.create(() -> readLine(input));
      }
    }
    catch (IOException ignored) {}
    if (result == null) {
      RuntimeState.setInput(null);
    }
    return result;
  }
  public static Object nextLineWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return nextLine(null);
  }

  private static String readLine(BufferedReader input) {
    try {
      input.mark(1);
      boolean eof = input.read() == -1;
      if (eof) {
        return null;
      }
      input.reset();
      String line = input.readLine();
      return line;
    }
    catch (IOException e) {
      return null;
    }
  }

  // = stream
  public static Iterator stream(Continuation c, String source, int offset, MethodHandle closure) {
    return new StreamIterator(source, offset, closure);
  }
  public static Object streamWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, null,1, source, offset);
    try {
      return stream(null, source, offset, (MethodHandle)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to stream() must be a function/closure not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  // = eval
  public static Object eval(Continuation c, String code, Map bindings) {
    if (c != null) {
      return c.getResult();
    }
    try {
      bindings = bindings == null ? new LinkedHashMap() : bindings;
      var script = RuntimeUtils.compileScript(code, bindings);
      var result = script.apply(bindings);
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, evalHandle, 0, null, null);
    }
    catch (JacsalError e) {
      if (bindings != null) {
        bindings.put(Utils.EVAL_ERROR, e.toString());
      }
      return null;
    }
  }
  public static Object evalWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, null,2, source, offset);
    if (args.length > 1 && !(args[1] instanceof Map)) {
      throw new RuntimeError("Argument of type " + RuntimeUtils.className(args[1]) + " cannot be converted to Map", source, offset);
    }
    try {
      return eval(null, (String)args[0], args.length > 1 ? (Map)args[1] : null);
    }
    catch (ClassCastException e) {
      if (e.getMessage().contains("to class java.lang.String")) {
        throw new RuntimeError("First argument to eval() must be a String not " + RuntimeUtils.className(args[0]), source, offset);
      }
      else {
        throw new RuntimeError("Second argument to eval() must be a Map not " + RuntimeUtils.className(args[0]), source, offset);
      }
    }
  }
  private static MethodHandle evalHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "eval$c", Object.class, Continuation.class);
  public static Object eval$c(Continuation c) {
    return eval(c, null, null);
  }

  // = abs
  public static int intAbs(int n)                   { return n < 0 ? -n : n; }
  public static long longAbs(long n)                { return n < 0 ? -n : n; }
  public static double doubleAbs(double n)          { return n < 0 ? -n : n; }
  public static BigDecimal decimalAbs(BigDecimal n) { return n.abs(); }
  public static Object intAbsWrapper(Integer n, Continuation c, String source, int offset, Object[] args) {
    validateArgCount(args, 0, INT,0, source, offset);
    return n < 0 ? -n : n;
  }
  public static Object longAbsWrapper(Long n, Continuation c, String source, int offset, Object[] args) {
    validateArgCount(args, 0, LONG,0, source, offset);
    return n < 0 ? -n : n;
  }
  public static Object doubleAbsWrapper(Double n, Continuation c, String source, int offset, Object[] args) {
    validateArgCount(args, 0, DOUBLE,0, source, offset);
    return n < 0 ? -n : n;
  }
  public static Object decimalAbsWrapper(BigDecimal n, Continuation c, String source, int offset, Object[] args) {
    validateArgCount(args, 0, DOUBLE,0, source, offset);
    return n.abs();
  }

  /////////////////////////////////////
  // Methods
  /////////////////////////////////////

  // = asChar
  public static String intAsChar(int c) { return String.valueOf((char)c); }
  public static Object intAsCharWrapper(Integer obj, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return String.valueOf((char)(int)obj);
  }

  // = toBase
  public static String longToBase(long num, String source, int offset, int base) {
    if (base < Character.MIN_RADIX || base > Character.MAX_RADIX) {
      throw new RuntimeError("Base must be between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, source, offset);
    }
    return Long.toUnsignedString(num, base).toUpperCase();
  }
  public static Object longToBaseWrapper(Long num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, LONG, 1, source, offset);
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("Argument to toBase must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    int base = ((Number) args[0]).intValue();
    return longToBase(num, source, offset, base);
  }
  public static String intToBase(int num, String source, int offset, int base) {
    if (base < Character.MIN_RADIX || base > Character.MAX_RADIX) {
      throw new RuntimeError("Base must be between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, source, offset);
    }
    return Integer.toUnsignedString(num, base).toUpperCase();
  }
  public static Object intToBaseWrapper(Integer num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, INT, 1, source, offset);
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("Argument to toBase must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    int base = ((Number) args[0]).intValue();
    return intToBase(num, source, offset, base);
  }

  // = sqrt
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
  public static Object numberSqrtWrapper(Number num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, NUMBER, 0, source, offset);
    return numberSqrt(num, source, offset);
  }

  // = sqr
  public static int intSqr(int num, String source, int offset) { return num * num; }
  public static Object intSqrWrapper(Integer num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, INT, 0, source, offset);
    return num * num;
  }
  public static long longSqr(long num, String source, int offset) { return num * num; }
  public static Object longSqrWrapper(Long num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, LONG, 0, source, offset);
    return num * num;
  }
  public static double doubleSqr(double num, String source, int offset) { return num * num; }
  public static Object doubleSqrWrapper(Double num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, DOUBLE, 0, source, offset);
    return num * num;
  }
  public static BigDecimal decimalSqr(BigDecimal num, String source, int offset) { return num.pow(2); }
  public static Object decimalSqrWrapper(BigDecimal num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, INT, 0, source, offset);
    return num.pow(2);
  }

  // = pow
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
  public static Object numberPowWrapper(Number num, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, NUMBER, 1, source, offset);
    try {
      return numberPow(num, source, offset, (Number)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Value for power must be numeric not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  // = toString

  public static String objectToString(Object obj, int indent) { return RuntimeUtils.toString(obj, indent); }
  public static Object objectToStringWrapper(Object obj, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null, 1, source, offset);
    try {
      int indent = args.length > 0 ? Integer.parseInt(args[0].toString()) : 0;
      if (indent >= 0) {
        return RuntimeUtils.toString(obj, indent);
      }
    }
    catch (NumberFormatException e) {
      // Fall through
    }
    throw new RuntimeError("Argument to toString() must be a positive integer", source, offset);
  }

  // = remove

  public static Object mapRemove(Map map, String field) {
    return map.remove(field);
  }
  public static Object mapRemoveWrapper(Map map, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, MAP,1, source, offset);
    try {
      return mapRemove(map, (String) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to Map remove() must be a String not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

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
  public static Object listRemoveWrapper(List list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, LIST,1, source, offset);
    try {
      return listRemove(list, source, offset, (int)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to List remove() must be an integer not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  // = add

  public static List listAdd(List list, String source, int offset, int index, Object elem) {
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
  public static Object listAddWrapper(List list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, LIST,2, source, offset);
    try {
      if (args.length == 2) {
        return listAdd(list, source, offset, (int) args[0], args[1]);
      }
      return listAdd(list, source, offset, list.size(), args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Index argument to List add() must be an integer not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  // = size

  public static int mapSize(Map map) { return map.size(); }
  public static Object mapSizeWrapper(Map map, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return map.size();
  }

  public static int listSize(List list) {
    return list.size();
  }
  public static Object listSizeWrapper(List list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null, 0, source, offset);
    return list.size();
  }

  public static int objArrSize(Object[] list) { return list.length; }
  public static Object objArrSizeWrapper(Object[] list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return list.length;
  }

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
  public static Object iteratorSizeWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return iteratorSize(iterable, c);
  }

  ////////////////////////////////

  // = sublist
  public static List listSubList(List list, String source, int offset, int start, int end) {
    try {
      return list.subList(start, end);
    }
    catch (Exception e) {
      throw new RuntimeError("SubList error", source, offset, e);
    }
  }
  public static Object listSubListWrapper(List list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, LIST, 2, source, offset);
    int begin = -1;
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("First argument to subList must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    begin = ((Number)args[0]).intValue();
    try {
      if (args.length > 1) {
        if (!(args[1] instanceof Number)) {
          throw new RuntimeError("Second argument to subList must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
        }
        int end = ((Number) args[1]).intValue();
        return list.subList(begin, end);
      }
      else {
        return list.subList(begin, list.size());
      }
    }
    catch (Exception e) {
      throw new RuntimeError("SubList error", source, offset, e);
    }
  }

  public static List iteratorSubList(Object iterable, Continuation c, String source, int offset, int start, int end) {
    source = c == null ? source : (String)c.localObjects[0];
    offset = c == null ? offset : (int)c.localPrimitives[0];
    start  = c == null ? start  : (int)c.localPrimitives[1];
    end    = c == null ? end    : (int)c.localPrimitives[2];
    try {
      List list = c == null ? RuntimeUtils.convertIteratorToList(iterable, null)
                            : (List) c.getResult();
      return listSubList(list, source, offset, start, end == Integer.MIN_VALUE ? list.size() : end);
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSubListHandle.bindTo(iterable), 0, new long[]{offset, start,end}, new Object[]{source});
    }
  }
  private static MethodHandle iteratorSubListHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSubList$c", Object.class, Object.class, Continuation.class);
  public static Object iteratorSubList$c(Object iterable, Continuation c) {
    return iteratorSubList(iterable, c, null, 0, 0, 0);
  }
  public static Object iteratorSubListWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, ITERATOR,2, source, offset);
    int begin = -1;
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("First argument to subList must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    begin = ((Number)args[0]).intValue();
    try {
      if (args.length > 1) {
        if (!(args[1] instanceof Number)) {
          throw new RuntimeError("Second argument to subList must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
        }
        int end = ((Number) args[1]).intValue();
        return iteratorSubList(iterable, c, source, offset, begin, end);
      }
      else {
        return iteratorSubList(iterable, c, source, offset, begin, Integer.MIN_VALUE);
      }
    }
    catch (Exception e) {
      throw new RuntimeError("SubList error", source, offset, e);
    }
  }

  ////////////////////////////////

  // = filter

  public static Iterator iteratorFilter(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new FilterIterator(iter, source, offset, closure);
  }
  public static Object iteratorFilterWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR, 1, source, offset);
    try {
      return iteratorFilter(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = reverse

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
      throw new Continuation(cont, iteratorSizeHandle.bindTo(iterable), 0, null, null);
    }
  }
  private static MethodHandle iteratorReverseHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSize$c", Object.class, Object.class, Continuation.class);
  public static Object iteratorReverse$c(Object iterable, Continuation c) {
    return iteratorReverse(iterable, c);
  }
  public static Object iteratorReverseWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return iteratorReverse(iterable, c);
  }

  ////////////////////////////////

  // = unique

  public static Iterator iteratorUnique(Object iterable, Continuation c, String source, int offset) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new UniqueIterator(iter);
  }
  public static Object iteratorUniqueWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null, 0, source, offset);
    return iteratorUnique(iterable, c, source, offset);
  }

  ////////////////////////////////

  // = skip

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

  public static Object iteratorSkipWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, ITERATOR, 1, source, offset);
    try {
      return iteratorSkip(iterable, c, source, offset, args.length == 0 ? null : (int)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = limit

  public static Iterator iteratorLimit(Object iterable, Continuation c, String source, int offset, int limit) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    if (limit >= 0) {
      return new LimitIterator(iter, limit);
    }
    return new NegativeLimitIterator(iter, -limit);
  }
  public static Object iteratorLimitWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, ITERATOR, 1, source, offset);
    try {
      return iteratorLimit(iterable, c, source, offset, args.length == 0 ? null : (int)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = grouped

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

  public static Object iteratorGroupedWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, ITERATOR,1, source, offset);
    try {
      return iteratorGrouped(iterable, c, source, offset, (int)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to grouped() must be an integer not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  ////////////////////////////////

  // = map

  public static Iterator iteratorMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure);
  }

  public static Object iteratorMapWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorMap(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = mapWithIndex

  public static Iterator iteratorMapWithIndex(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new MapIterator(iter, source, offset, closure, true);
  }

  public static Object iteratorMapWithIndexWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorMapWithIndex(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = flatMap

  public static Iterator iteratorFlatMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = RuntimeUtils.createIterator(iterable);
    return new FlatMapIterator(iter, source, offset, closure);
  }

  public static Object iteratorFlatMapWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorFlatMap(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  //////////////////////////////////

  // = each

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
  public static Object iteratorEachWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, ITERATOR, 1, source, offset);
    try {
      return iteratorEach(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = collect

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
  public static Object iteratorCollectWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR, 1, source, offset);
    try {
      return iteratorCollect(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  /////////////////////////////

  // = reduce

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
  public static Object iteratorReduceWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 2, ITERATOR, 2, source, offset);
    try {
      return iteratorReduce(iterable, c, source, offset, args[0], (MethodHandle) args[1]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[1]) + " to Function", source, offset);
    }
  }

  /////////////////////////////

  // = join

  public static String iteratorJoin(Object iterable, String joinStr, Continuation c, String source, int offset) {
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

  public static Object iteratorJoinWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorJoin(iterable, args.length == 0 ? null : (String)args[0], c, source, offset);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to String", source, offset);
    }
  }

  /////////////////////////////

  // = avg

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
  public static Object iteratorAvgWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return iteratorAvg(iterable, c, source, offset);
  }

  // = sum

  public static Object iteratorSum(Object iterable, Continuation c, String source, int offset) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, 0,
                            (value,elem) -> addNumbers(value, elem, source, offset),
                            (value) -> value,
                            c);
  }

  public static Object iteratorSumWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return iteratorSum(iterable, c, source, offset);
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

  public static Object iteratorMin(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, null,
                            (value,elem) -> minMaxInvoker(source, offset, closure, false, value, elem, c),
                            (value) -> value == null ? null : ((Object[])value)[1],
                            c);
  }

  public static Object iteratorMinWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorMin(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to min() must be a function/closure not " + RuntimeUtils.className(args[0]), source, offset);
    }
  }

  public static Object iteratorMax(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return iteratorReduce$c(RuntimeUtils.createIterator(iterable), source, offset, null,
                            (value,elem) -> minMaxInvoker(source, offset, closure, true, value, elem, c),
                            (value) -> value == null ? null : ((Object[])value)[1],
                            c);
  }
  public static Object iteratorMaxWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorMax(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Argument to max() must be a function/closure not " + RuntimeUtils.className(args[0]), source, offset);
    }
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

  public static Object iteratorCollectEntries(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    BiFunction<Object,Object,Object> collector = (mapObj,elem) -> {
      RuntimeUtils.addMapEntry((Map) mapObj, elem, source, offset);
      return mapObj;
    };
    return iteratorCollect$c(RuntimeUtils.createIterator(iterable), new HashMap(), collector, source, offset, closure, null);
  }

  public static Object iteratorCollectEntriesWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR, 1, source, offset);
    try {
      return iteratorCollectEntries(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }


  /////////////////////////////

  // = sort

  private static MethodHandle iteratorSortHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSort$c",
                                                                             Object.class, Object.class, String.class,
                                                                             Integer.class,MethodHandle.class, Continuation.class);

  public static Object iteratorSort$c(Object iterable, String source, Integer offset, MethodHandle closure, Continuation c) {
    return iteratorSort(iterable, c, source, offset, closure);
  }

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
  public static Object iteratorSortWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, ITERATOR,1, source, offset);
    try {
      return iteratorSort(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset, e);
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
  public static Iterator stringLines(String str) {
    return RuntimeUtils.lines(str).iterator();
  }
  public static Object stringLinesWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null,0, source, offset);
    return RuntimeUtils.lines(str).iterator();
  }

  // = length
  public static int stringLength(String str) {
    return str.length();
  }
  public static Object stringLengthWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, null, 0, source, offset);
    return ((String)str).length();
  }

  // = toLowerCase
  public static String stringToLowerCase(String str, int length) {
    if (length < 0 || length >= str.length()) {
      return str.toLowerCase();
    }
    if (length == 0) {
      return str;
    }
    return str.substring(0, length).toLowerCase() + str.substring(length);
  }
  public static Object stringToLowerCaseWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, STRING, 1, source, offset);
    if (args.length > 0) {
      if (!(args[0] instanceof Number)) {
        throw new RuntimeError("Argument to toLowerCase must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
      }
      return stringToLowerCase(str, ((Number)args[0]).intValue());
    }
    return ((String)str).toLowerCase();
  }

  // = toUpperCase
  public static String stringToUpperCase(String str, int length) {
    if (length < 0 || length >= str.length()) {
      return str.toUpperCase();
    }
    if (length == 0) {
      return str;
    }
    return str.substring(0, length).toUpperCase() + str.substring(length);
  }
  public static Object stringToUpperCaseWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, STRING, 1, source, offset);
    if (args.length > 0) {
      if (!(args[0] instanceof Number)) {
        throw new RuntimeError("Argument to toUpperCase must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
      }
      return stringToUpperCase(str, ((Number)args[0]).intValue());
    }
    return str.toUpperCase();
  }

  // = substring
  public static String stringSubstring(String str, String source, int offset, int start, int end) {
    try {
      return str.substring(start, end);
    }
    catch (Exception e) {
      throw new RuntimeError("Substring error", source, offset, e);
    }
  }
  public static Object stringSubstringWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 1, STRING, 2, source, offset);
    int begin = -1;
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("First argument to substring must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    begin = ((Number)args[0]).intValue();
    try {
      if (args.length > 1) {
        if (!(args[1] instanceof Number)) {
          throw new RuntimeError("Second argument to substring must be a number not '" + RuntimeUtils.className(args[1]) + "'", source, offset);
        }
        int end = ((Number) args[1]).intValue();
        return str.substring(begin, end);
      }
      else {
        return str.substring(begin);
      }
    }
    catch (Exception e) {
      throw new RuntimeError("Substring error", source, offset, e);
    }
  }

  // = asNum
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
  public static Object stringAsNumWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, STRING, 1, source, offset);
    int base = 10;
    if (args.length > 0) {
      if (!(args[0] instanceof Number)) {
        throw new RuntimeError("Argument to asNum must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
      }
      base = ((Number) args[0]).intValue();
    }
    return stringAsNum(str, source, offset, base);
  }

  // = split
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
  public static Object stringSplitWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, STRING, 2, source, offset);
    String regex;
    try {
      regex = args.length == 0 ? null : (String) args[0];
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Regex for split() must be String not " + RuntimeUtils.className(args[0]), source, offset);
    }
    String modifiers;
    try {
      modifiers = args.length <= 1 ? "" : (String) args[1];
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Modifiers for split() must be String not " + RuntimeUtils.className(args[0]), source, offset);
    }
    return stringSplit(str, source, offset, regex, modifiers);
  }



  /////////////////////////////

  private static Object[] validateArgCount(Object[] args, int mandatoryCount, JacsalType methodObjType, int paramCount, String source, int offset) {
    int argCount = args.length;

    // If we have a single arg which is a List and
    //   - we have no parameters, or
    //   - we have more than one mandatory parameter, or
    //   - we have single parameter that is not a List
    // then
    //   we treat the List as our args
    if (argCount == 1 && args[0] instanceof List &&
        (paramCount == 0 ||
         !methodObjType.is(LIST,ANY) ||
         mandatoryCount > 1)) {
      args = ((List) args[0]).toArray();
      argCount = args.length;
    }
    else if (argCount > 0 && args[0] instanceof NamedArgsMap) {
      throw new RuntimeError("Named args not supported for built-in functions", source, offset);
    }

    if (argCount < mandatoryCount) {
      String atLeast = mandatoryCount == paramCount ? "" : "at least ";
      throw new RuntimeError("Missing mandatory arguments (arg count of " + argCount + " but expected " + atLeast + mandatoryCount + ")", source, offset);
    }
    if (paramCount >= 0 && argCount > paramCount) {
      throw new RuntimeError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", source, offset);
    }
    return args;
  }
}
