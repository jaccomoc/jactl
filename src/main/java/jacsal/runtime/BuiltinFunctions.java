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

import jacsal.JacsalType;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
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
      registerMethod("size", "listSize", false);
      registerMethod("size", "objArrSize", false);
      registerMethod("size", "mapSize", false);
      registerMethod("size", "iteratorSize", false);
      registerMethod("each", "iteratorEach", ITERATOR, true, 0, List.of(0,1));
      registerMethod("collect", "iteratorCollect", ITERATOR, true, 0, List.of(0,1));
      registerMethod("collectEntries", "iteratorCollectEntries", ITERATOR, true, 0, List.of(0,1));
      registerMethod("sort", "iteratorSort", ITERATOR, true, 0);
      registerMethod("map", "iteratorMap", ITERATOR, true, 0, List.of(0,1));
      registerMethod("filter", "iteratorFilter", ITERATOR, true, 0, List.of(0,1));
      registerMethod("join", "iteratorJoin", ITERATOR, false, 0);

      // String methods
      registerMethod("lines", "stringLines", false);
      registerMethod("length", "stringLength", false);
      registerMethod("size", "stringLength", false);
      registerMethod("toLowerCase", "stringToLowerCase", STRING, false, 0);
      registerMethod("toUpperCase", "stringToUpperCase", STRING, false, 0);
      registerMethod("substring", "stringSubstring", STRING, true, 1);

      // Object methods
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
    registerFunction(name, methodName, false, objType, needsLocation, mandatoryArgCount, asyncArgs);
  }

  private static void registerFunction(String name, String methodName, boolean isStatic, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    registerFunction(name, methodName, isStatic, objType, needsLocation, mandatoryArgCount, List.of());
  }

  private static void registerFunction(String name, String methodName, boolean isStatic, JacsalType objType,
                                       boolean needsLocation, int mandatoryArgCount, List<Integer> asyncArgs) {
    var descriptor = getFunctionDescriptor(name, methodName, isStatic, objType, needsLocation, mandatoryArgCount);
    descriptor.asyncArgs = asyncArgs;
    Functions.registerMethod(descriptor);
  }

  private static FunctionDescriptor getFunctionDescriptor(String name, String methodName, boolean isStatic, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
    Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
    if (method == null) {
      throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
    }
    JacsalType firstArgType = isStatic ? null : JacsalType.typeFromClass(method.getParameterTypes()[0]);
    if (objType == null) {
      objType = firstArgType;
    }
    JacsalType returnType   = JacsalType.typeFromClass(method.getReturnType());
    Class[]    paramClasses = method.getParameterTypes();
    boolean    isAsync      = paramClasses.length > (isStatic ? 0 : 1) && paramClasses[isStatic ? 0 : 1].equals(Continuation.class);
    List<JacsalType> paramTypes = Arrays.stream(paramClasses)
                                        .skip((isStatic ? 0 : 1) + (isAsync ? 1 : 0) + (needsLocation ? 2 : 0))
                                        .map(JacsalType::typeFromClass)
                                        .collect(Collectors.toList());
    var wrapperMethod = methodName + "Wrapper";
    MethodHandle wrapperHandle = isStatic ? RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                                      wrapperMethod,
                                                                      Object.class,
                                                                      Continuation.class,
                                                                      String.class,
                                                                      int.class,
                                                                      Object[].class)
                                          : RuntimeUtils.lookupMethod(BuiltinFunctions.class,
                                                                      wrapperMethod,
                                                                      Object.class,
                                                                      firstArgType.classFromType(),
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
  public static Object sleeperWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 2, 2, source, offset);
    try {
      sleeper(c, ((Number)args[0]).longValue(), args[1]);
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
  public static long timeStamp() { return System.currentTimeMillis(); }
  public static Object timeStampWrapper(Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return System.currentTimeMillis();
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
    args = validateArgCount(args, 1, -1, source, offset);
    try {
      return sprintf(source, offset, (String)args[0], Arrays.copyOfRange(args, 1, args.length));
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert argument of type " + RuntimeUtils.className(args[0]) + " to String", source, offset);
    }
  }

  /////////////////////////////////////
  // Methods
  /////////////////////////////////////

  // = toString

  public static String objectToString(Object obj) { return RuntimeUtils.toString(obj); }
  public static Object objectToStringWrapper(Object obj, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return RuntimeUtils.toString(obj);
  }

  // = size

  public static int mapSize(Map map) { return map.size(); }
  public static Object mapSizeWrapper(Map map, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return map.size();
  }

  public static int listSize(List list) { return list.size(); }
  public static Object listSizeWrapper(List list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return list.size();
  }

  public static int objArrSize(Object[] list) { return list.length; }
  public static Object objArrSizeWrapper(Object[] list, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
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
  public static Object iteratorSizeWrapper(Iterator iterator, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return iteratorSize(iterator, c);
  }

  ////////////////////////////////

  // = filter

  public static Iterator iteratorFilter(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new FilterIterator(iter, source, offset, closure);
  }
  public static Object iteratorFilterWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
    try {
      return iteratorFilter(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = map

  public static Iterator iteratorMap(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new MapIterator(iter, source, offset, closure);
  }

  public static Object iteratorMapWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
    try {
      return iteratorMap(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
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
    return iteratorEach$c(createIterator(iterable), source, offset, closure, null);
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
    args = validateArgCount(args, 1, 1, source, offset);
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
    return iteratorCollect$c(createIterator(iterable), new ArrayList(), (list,elem) -> ((List)list).add(elem), source, offset, closure, null);
  }
  public static Object iteratorCollect$c(Iterator iter, Object result, BiConsumer<Object,Object> collector, String source, Integer offset, MethodHandle closure, Continuation c) {
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
              return result;        // EXIT: exit loop when underlying iterator has no more elements
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.result;
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            elem = RuntimeUtils.mapEntryToList(elem);
            transformedElem = closure == null ? elem : closure.invokeExact((Continuation)null, source, (int)offset, new Object[]{ elem });
            methodLocation = 6;
            break;
          case 5:
            transformedElem = c.result;
            methodLocation = 6;
            break;
          case 6:
            collector.accept(result, transformedElem);
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
                                                                                BiConsumer.class, String.class, Integer.class,
                                                                                MethodHandle.class, Continuation.class);
  public static Object iteratorCollectWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
    try {
      return iteratorCollect(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
    }
  }

  //////////////////////////////////////

  // = collectEntries

  public static Object iteratorCollectEntries(Object iterable, Continuation c, String source, int offset, MethodHandle closure) {
    BiConsumer<Object,Object> collector = (mapObj,elem) -> {
      RuntimeUtils.addMapEntry((Map) mapObj, elem, source, offset);
    };
    return iteratorCollect$c(createIterator(iterable), new HashMap(), collector, source, offset, closure, null);
  }

  public static Object iteratorCollectEntriesWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
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
            throw new RuntimeError("Unexpected error", source, offset, t);
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
  public static Object iteratorSortWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
    try {
      return iteratorSort(iterable, c, source, offset, args.length == 0 ? null : (MethodHandle) args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to Function", source, offset);
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
  public static Object iteratorJoinWrapper(Object iterable, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 1, source, offset);
    try {
      return iteratorJoin(iterable, args.length == 0 ? null : (String)args[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(args[0]) + " to String", source, offset);
    }
  }

  /////////////////////////////
  //// String methods

  // = lines
  public static Iterator stringLines(String str) {
    return RuntimeUtils.lines(str).iterator();
  }
  public static Object stringLinesWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
    return RuntimeUtils.lines(str).iterator();
  }

  // = length
  public static int stringLength(String str) {
    return str.length();
  }
  public static Object stringLengthWrapper(String str, Continuation c, String source, int offset, Object[] args) {
    args = validateArgCount(args, 0, 0, source, offset);
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
    args = validateArgCount(args, 0, 1, source, offset);
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
    args = validateArgCount(args, 0, 1, source, offset);
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
    args = validateArgCount(args, 1, 2, source, offset);
    int begin = -1;
    if (!(args[0] instanceof Number)) {
      throw new RuntimeError("First argument to substring must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
    }
    begin = ((Number)args[0]).intValue();
    try {
      if (args.length > 1) {
        if (!(args[1] instanceof Number)) {
          throw new RuntimeError("Second argument to substring must be a number not '" + RuntimeUtils.className(args[0]) + "'", source, offset);
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

  /////////////////////////////

  private static Object[] validateArgCount(Object[] args, int mandatoryCount, int paramCount, String source, int offset) {
    int argCount = args.length;

    // Special case for functions with 2 or more params where we have single argument of type List.
    // We treat the list as the arguments in this case.
    if (mandatoryCount >= 2 && argCount == 1) {
      if (args[0] instanceof List) {
        args = ((List) args[0]).toArray();
        argCount = args.length;
      }
      else if (args[0] instanceof Map) {
        throw new RuntimeError("Named args not supported for built-in functions", source, offset);
      }
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
