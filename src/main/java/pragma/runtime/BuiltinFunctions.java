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

package pragma.runtime;

import pragma.PragmaType;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static pragma.PragmaType.*;

public class BuiltinFunctions {

  private static Map<String,FunctionDescriptor> globalFunctions = new HashMap<>();
  private static boolean initialised = false;

  public static void registerBuiltinFunctions() {
    if (!initialised) {
      registerMethod("size", "listSize", false);
      registerMethod("size", "objArrSize", false);
      registerMethod("size", "mapSize", false);
      registerMethod("size", "iteratorSize", false);
      registerMethod("each", "iteratorEach", ITERATOR, true, 0);
      registerMethod("collect", "iteratorCollect", ITERATOR, true, 0);
      registerMethod("sort", "iteratorSort", ITERATOR, true, 0);
      registerMethod("map", "iteratorMap", ITERATOR, true, 0);
      registerMethod("filter", "iteratorFilter", ITERATOR, true, 0);
      registerMethod("join", "iteratorJoin", ITERATOR, false, 0);
      registerMethod("lines", "stringLines", false);

      registerMethod("toString", "objectToString", ANY, false, 0);

      registerGlobalFunction("timeStamp", "timeStamp", false, 0);
      registerGlobalFunction("sprintf", "sprintf", true, 1);
      registerGlobalFunction("sleeper", "sleeper", false, 2);
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
    var descriptor = getFunctionDescriptor(name, methodName, true, null, needsLocation, mandatoryArgCount);
    globalFunctions.put(name, descriptor);
  }

  private static void registerMethod(String name, String methodName, boolean needsLocation) {
    registerMethod(name, methodName, null, needsLocation, -1);
  }

  private static void registerMethod(String name, String methodName, PragmaType objType, boolean needsLocation, int mandatoryArgCount) {
    registerFunction(name, methodName, false, objType, needsLocation, mandatoryArgCount);
  }

  private static void registerFunction(String name, String methodName, boolean isStatic, PragmaType objType, boolean needsLocation, int mandatoryArgCount) {
    var descriptor = getFunctionDescriptor(name, methodName, isStatic, objType, needsLocation, mandatoryArgCount);
    Functions.registerMethod(descriptor);
  }

  private static FunctionDescriptor getFunctionDescriptor(String name, String methodName, boolean isStatic, PragmaType objType, boolean needsLocation, int mandatoryArgCount) {
    Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
    Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
    if (method == null) {
      throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
    }
    PragmaType firstArgType = isStatic ? null : PragmaType.typeFromClass(method.getParameterTypes()[0]);
    if (objType == null) {
      objType = firstArgType;
    }
    PragmaType returnType   = PragmaType.typeFromClass(method.getReturnType());
    Class[]    paramClasses = method.getParameterTypes();
    boolean    isAsync      = paramClasses.length > (isStatic ? 0 : 1) && paramClasses[isStatic ? 0 : 1].equals(Continuation.class);
    List<PragmaType> paramTypes = Arrays.stream(paramClasses)
                                        .skip((isStatic ? 0 : 1) + (isAsync ? 1 : 0) + (needsLocation ? 2 : 0))
                                        .map(PragmaType::typeFromClass)
                                        .collect(Collectors.toList());
    var wrapperMethod = methodName + "Wrapper";
    MethodHandle wrapperHandle = isStatic ? RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                                      wrapperMethod,
                                                                      Object.class,
                                                                      Continuation.class,
                                                                      String.class,
                                                                      int.class,
                                                                      Object.class)
                                          : RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                                      wrapperMethod,
                                                                      Object.class,
                                                                      firstArgType.classFromType(),
                                                                      Continuation.class,
                                                                      String.class,
                                                                      int.class,
                                                                      Object.class);
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
    descriptor.isStatic = isStatic;
    descriptor.isAsync = isAsync;
    return descriptor;
  }

  /////////////////////////////////////
  // Global Functions
  /////////////////////////////////////

  // = sleeper
  public static Object sleeper(Continuation c, long timeMs, Object result) {
    throw Continuation.create(() -> { doSleep(timeMs); return result; });
  }
  public static Object sleeperWrapper(Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 2, 2, source, offset);
    final var argArr = (Object[]) args;
    try {
      sleeper(c, (long)argArr[0], argArr[1]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert argument of type " + RuntimeUtils.className(argArr[0]) + " to long", source, offset);
    }
    return null;
  }
  private static void doSleep(long ms) {
    try { if (ms > 0) Thread.sleep(ms); } catch (InterruptedException e) {}
  }

  // = timestamp
  public static long timeStamp() { return System.currentTimeMillis(); }
  public static Object timeStampWrapper(Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return System.currentTimeMillis();
  }

  // = sprintf
  public static String sprintf(String source, int offset, String format, Object... args) {
    try {
      return String.format(format, args);
    }
    catch (IllegalFormatException e) {
      throw new RuntimeError("Bad format string: " + e.getMessage(), source, offset, e);
    }
  }
  public static Object sprintfWrapper(Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 1, -1, source, offset);
    Object[] argArr = (Object[])args;
    try {
      return sprintf(source, offset, (String)argArr[0], Arrays.copyOfRange(argArr, 1, argArr.length));
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert argument of type " + RuntimeUtils.className(argArr[0]) + " to String", source, offset);
    }
  }

  /////////////////////////////////////
  // Methods
  /////////////////////////////////////

  // = toString

  public static String objectToString(Object obj) { return RuntimeUtils.toString(obj); }
  public static Object objectToStringWrapper(Object obj, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return RuntimeUtils.toString(obj);
  }

  // = size

  public static int mapSize(Map map) { return map.size(); }
  public static Object mapSizeWrapper(Map map, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return map.size();
  }

  public static int listSize(List list) { return list.size(); }
  public static Object listSizeWrapper(List list, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return list.size();
  }

  public static int objArrSize(Object[] list) { return list.length; }
  public static Object objArrSizeWrapper(Object[] list, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return list.length;
  }

  public static int iteratorSize(Iterator iterator, Continuation c) {
    try {
      List list = c == null ? RuntimeUtils.convertIteratorToList(iterator, null)
                            : (List)c.result;
      return list.size();
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSizeHandle.bindTo(iterator), 0, null, null);
    }
  }
  private static MethodHandle iteratorSizeHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorSize$c", Object.class, Iterator.class, Continuation.class);
  public static Object iteratorSize$c(Iterator iterator, Continuation c) {
    return iteratorSize(iterator, c);
  }
  public static Object iteratorSizeWrapper(Iterator iterator, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return iteratorSize(iterator, c);
  }

  ////////////////////////////////

  // = filter

  public static Iterator iteratorFilter(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new FilterIterator(iter, source, offset, closure);
  }
  public static Object iteratorFilterWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorFilter(iterable, c, source, offset, arr.length == 0 ? null : (MethodHandle)arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = map

  public static Iterator iteratorMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new MapIterator(iter, source, offset, closure);
  }

  public static Object iteratorMapWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorMap(iterable, c, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  private static Iterator createIterator(Object obj) {
    if (obj instanceof Iterator) { return (Iterator)obj;                    }
    if (obj instanceof Iterable) { return ((Iterable)obj).iterator();       }
    if (obj instanceof Map)      { return ((Map)obj).entrySet().iterator(); }
    if (obj instanceof Object[]) {
      Object[] arr = (Object[])obj;
      return new Iterator() {
        int index = 0;
        @Override public boolean hasNext() { return index < arr.length; }
        @Override public Object next()     { return arr[index++];       }
      };
    }
    throw new IllegalStateException("Internal error: unexpected type " + obj.getClass().getName() + " for iterable");
  }

  /////////////////////////////////////

  // = each

  public static Object iteratorEach(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return doIteratorEach(createIterator(iterable), c, source, offset, closure);
  }
  public static Object doIteratorEach(Iterator iter, Continuation c, String source, int offset, MethodHandle closure) {
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
            hasNext = (boolean)c.result;
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
            elem = c.result;
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            if (elem instanceof Map.Entry) {
              var entry = (Map.Entry) elem;
              elem = new Object[] { entry.getKey(), entry.getValue() };
            }
            Object ignored = closure == null ? null : closure.invokeExact((Continuation)null, source, offset, (Object)(new Object[]{ elem }));
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
      throw new Continuation(cont, iteratorEachHandle.bindTo(iter), methodLocation + 1, null, new Object[] { source, offset, closure });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t);
    }
  }
  private static MethodHandle iteratorEachHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorEach$c", Object.class, Iterator.class, Continuation.class);
  public static Object iteratorEach$c(Iterator iterator, Continuation c) {
    return doIteratorEach(iterator, c, (String)c.localObjects[0], (int)c.localObjects[1], (MethodHandle)c.localObjects[2]);
  }
  public static Object iteratorEachWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorEach(iterable, c, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = collect

  public static Object iteratorCollect(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    return doIteratorCollect(createIterator(iterable), c, source, offset, closure);
  }
  public static Object doIteratorCollect(Iterator iter, Continuation c, String source, int offset, MethodHandle closure) {
    List collected = c == null ? new ArrayList() : (List)c.localObjects[3];
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
            hasNext = (boolean)c.result;
            methodLocation = 2;
            break;
          case 2:                      // Have a value for "hasNext"
            if (!hasNext) {
              return collected;        // EXIT: exit loop when underlying iterator has no more elements
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.result;
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            if (elem instanceof Map.Entry) {
              var entry = (Map.Entry) elem;
              elem = new Object[] { entry.getKey(), entry.getValue() };
            }
            transformedElem = closure == null ? elem : closure.invokeExact((Continuation)null, source, offset, (Object)(new Object[]{ elem }));
            methodLocation = 6;
            break;
          case 5:
            transformedElem = c.result;
            methodLocation = 6;
            break;
          case 6:
            collected.add(transformedElem);
            methodLocation = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorCollectHandle.bindTo(iter), methodLocation + 1, null, new Object[] { source, offset, closure, collected });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t);
    }
  }
  private static MethodHandle iteratorCollectHandle = RuntimeUtils.lookupMethod(BuiltinFunctions.class, "iteratorCollect$c", Object.class, Iterator.class, Continuation.class);
  public static Object iteratorCollect$c(Iterator iterator, Continuation c) {
    return doIteratorCollect(iterator, c, (String)c.localObjects[0], (int)c.localObjects[1], (MethodHandle)c.localObjects[2]);
  }
  public static Object iteratorCollectWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorCollect(iterable, c, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
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
                                          : RuntimeUtils.convertIteratorToList(createIterator(iterable), null);
        location = 2;
      }
      if (location == 1) {
        result = (List)c.result;
        location = 2;
      }
      if (location == 2) {
        if (closure == null) {
          try {
            result.sort(null);
            return result;
          }
          catch (Throwable t) {
            throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t);
          }
        }
        else {
          return (List) mergeSort(result, closure, source, offset, null);
        }
      }
      else {
        return (List)c.result;
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, iteratorSortHandle.bindTo(iterable).bindTo(source).bindTo((Integer)offset).bindTo(closure),
                             location + 1, null, null);
    }
  }
  public static Object iteratorSortWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorSort(iterable, c, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
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
      comparison = c.result;
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
            comparison = comparator.invokeExact((Continuation) null, source, (int)offset, (Object) (new Object[]{new Object[]{elem1, elem2}}));
          }
          catch (Continuation cont) {
            throw new Continuation(cont, mergeHandle.bindTo(src).bindTo(dst).bindTo(start1).bindTo(end1).bindTo(start2).bindTo(end2).bindTo(comparator).bindTo(source).bindTo(offset),
                                   0, new long[] { i1, i2, dstPos }, null);
          }
          catch (RuntimeException e) { throw e; }
          catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
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

  // = join

  public static String iteratorJoin(Object iterable, String joinStr) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Iterator iter = createIterator(iterable); iter.hasNext(); ) {
      if (!first && joinStr != null) {
        sb.append(joinStr);
      }
      else {
        first = false;
      }
      sb.append(RuntimeUtils.toString(iter.next()));
    }
    return sb.toString();
  }
  public static Object iteratorJoinWrapper(Object iterable, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorJoin(iterable, arr.length == 0 ? null : (String)arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to String", source, offset);
    }
  }

  /////////////////////////////

  // = lines

  public static Iterator stringLines(String str) {
    return RuntimeUtils.lines(str).iterator();
  }
  public static Object stringLinesWrapper(String str, Continuation c, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return RuntimeUtils.lines(str).iterator();
  }

  /////////////////////////////

  private static void validateArgCount(Object args, int mandatoryCount, int paramCount, String source, int offset) {
    if (!(args instanceof Object[])) {
      throw new IllegalStateException("Internal error: expecting Object[] for args not " + args.getClass().getName());
    }
    int argCount = ((Object[])args).length;
    if (argCount < mandatoryCount) {
      String atLeast = mandatoryCount == paramCount ? "" : "at least ";
      throw new RuntimeError("Missing mandatory arguments (arg count of " + argCount + " but expected " + atLeast + mandatoryCount + ")", source, offset);
    }
    if (paramCount >= 0 && argCount > paramCount) {
      throw new RuntimeError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", source, offset);
    }
  }
}
