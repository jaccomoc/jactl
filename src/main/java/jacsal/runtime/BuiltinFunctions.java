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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static jacsal.JacsalType.ITERATOR;
import static jacsal.JacsalType.OBJECT_ARR;

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
      registerMethod("lines", "stringLines", false);

      registerGlobalFunction("timeStamp", "timeStamp", false, 0);
      registerGlobalFunction("sprintf", "sprintf", true, 1);
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
    registerFunction(name, methodName, false, objType, needsLocation, mandatoryArgCount);
  }

  private static void registerFunction(String name, String methodName, boolean isStatic, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    var descriptor = getFunctionDescriptor(name, methodName, isStatic, objType, needsLocation, mandatoryArgCount);
    Functions.registerMethod(descriptor);
  }

  private static FunctionDescriptor getFunctionDescriptor(String name, String methodName, boolean isStatic, JacsalType objType, boolean needsLocation, int mandatoryArgCount) {
    try {
      Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
      Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
      if (method == null) {
        throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
      }
      JacsalType firstArgType = isStatic ? null : JacsalType.typeFromClass(method.getParameterTypes()[0]);
      if (objType == null) {
        objType = firstArgType;
      }
      JacsalType returnType = JacsalType.typeFromClass(method.getReturnType());
      List<JacsalType> paramTypes = Arrays.stream(method.getParameterTypes())
                                          .skip((isStatic ? 0 : 1) + (needsLocation ? 2 : 0))
                                          .map(JacsalType::typeFromClass)
                                          .collect(Collectors.toList());
      var wrapperMethod = methodName + "Wrapper";
      var methodType    = isStatic ? MethodType.methodType(Object.class,
                                                           String.class,
                                                           int.class,
                                                           Object.class)
                                   : MethodType.methodType(Object.class,
                                                           firstArgType.classFromType(),
                                                           String.class,
                                                           int.class,
                                                           Object.class);
      MethodHandle wrapperHandle = MethodHandles.lookup().findStatic(BuiltinFunctions.class, wrapperMethod,
                                                                     methodType);
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
      return descriptor;
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Couldn't find wrapper method for " + methodName, e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("Error looking up wrapper method for " + methodName, e);
    }
  }

  /////////////////////////////////////
  // Global Functions
  /////////////////////////////////////

  // = timestamp
  public static long timeStamp() { return System.currentTimeMillis(); }
  public static Object timeStampWrapper(String source, int offset, Object args) {
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
  public static Object sprintfWrapper(String source, int offset, Object args) {
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

  // = size

  public static int mapSize(Map map) { return map.size(); }
  public static Object mapSizeWrapper(Map map, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return map.size();
  }

  public static int listSize(List list) { return list.size(); }
  public static Object listSizeWrapper(List list, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return list.size();
  }

  public static int objArrSize(Object[] list) { return list.length; }
  public static Object objArrSizeWrapper(Object[] list, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return list.length;
  }

  public static int iteratorSize(Iterator iterator) { return RuntimeUtils.convertIteratorToList(iterator).size(); }
  public static Object iteratorSizeWrapper(Iterator iterator, String source, int offset, Object args) {
    validateArgCount(args, 0, 0, source, offset);
    return RuntimeUtils.convertIteratorToList(iterator).size();
  }

  ////////////////////////////////

  // = filter

  public static Iterator iteratorFilter(Object iterable, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new Iterator() {
      Object next = null;
      @Override public boolean hasNext() {
        if (next != null) {
          return true;
        }
        findNext();
        return next != null;
      }
      private void findNext() {
        while (iter.hasNext()) {
          try {
            Object elem = iter.next();
            if (elem instanceof Map.Entry) {
              var entry = (Map.Entry)elem;
              elem = new Object[] { entry.getKey(), entry.getValue() };
            }
            boolean cond = true;
            if (closure != null) {
              Object result = closure.invokeExact(source, offset, (Object)(new Object[]{ elem }));
              cond = RuntimeUtils.isTruth(result, false);
            }
            if (cond) {
              next = elem;
              break;
            }
          }
          catch (RuntimeException e) { throw e; }
          catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
        }
      }
      @Override public Object next() {
        if (next == null) {
          findNext();
        }
        Object result = next;
        next = null;
        return result;
      }
    };
  }
  public static Object iteratorFilterWrapper(Object iterable, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorFilter(iterable, source, offset, arr.length == 0 ? null : (MethodHandle)arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = map

  public static Iterator iteratorMap(Object iterable, String source, int offset, MethodHandle closure) {
    Iterator iter = createIterator(iterable);
    return new Iterator() {
      @Override public boolean hasNext() { return iter.hasNext(); }
      @Override public Object next() {
        try {
          Object elem = iter.next();
          if (elem instanceof Map.Entry) {
            var entry = (Map.Entry)elem;
            elem = new Object[] { entry.getKey(), entry.getValue() };
          }
          return closure == null ? elem : closure.invokeExact(source, offset, (Object)(new Object[]{ elem }));
        }
        catch (RuntimeException e) { throw e; }
        catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
      }
    };
  }
  public static Object iteratorMapWrapper(Object iterable, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorMap(iterable, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
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

  public static Object iteratorEach(Object iterable, String source, int offset, MethodHandle closure) {
    for (Iterator iter = createIterator(iterable); iter.hasNext(); ) {
      try {
        Object elem = iter.next();
        if (elem instanceof Map.Entry) {
          var entry = (Map.Entry)elem;
          elem = new Object[] { entry.getKey(), entry.getValue() };
        }
        Object ignored = closure == null ? elem : closure.invokeExact(source, offset, (Object)(new Object[]{elem}));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    }
    return null;
  }
  public static Object iteratorEachWrapper(Object iterable, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorEach(iterable, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  ////////////////////////////////

  // = collect

  public static List iteratorCollect(Object iterable, String source, int offset, MethodHandle closure) {
    List result = new ArrayList();
    for (Iterator iter = createIterator(iterable); iter.hasNext(); ) {
      try {
        Object elem = iter.next();
        if (elem instanceof Map.Entry) {
          var entry = (Map.Entry)elem;
          elem = new Object[] { entry.getKey(), entry.getValue() };
        }
        result.add(closure == null ? elem : closure.invokeExact(source, offset, (Object)(new Object[]{elem})));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    }
    return result;
  }
  public static Object iteratorCollectWrapper(Object iterable, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorCollect(iterable, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  /////////////////////////////

  // = sort

  public static List iteratorSort(Object iterable, String source, int offset, MethodHandle closure) {
    List result = iterable instanceof List ? new ArrayList((List)iterable)
                                           : RuntimeUtils.convertIteratorToList(createIterator(iterable));
    if (closure == null) {
      try {
        result.sort(null);
      }
      catch (Throwable t) { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    }
    else {
      result.sort((a,b) -> {
        Object comparison;
        try {
          comparison = closure.invokeExact(source, offset, (Object)(new Object[]{new Object[]{a,b}}));
        }
        catch (RuntimeException e) { throw e; }
        catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
        if (!(comparison instanceof Integer)) {
          throw new RuntimeError("Comparator for sort must return integer value not " + RuntimeUtils.className(comparison), source, offset);
        }
        return (int)comparison;
      });
    }
    return result;
  }
  public static Object iteratorSortWrapper(Object iterable, String source, int offset, Object args) {
    validateArgCount(args, 0, 1, source, offset);
    Object[] arr = (Object[])args;
    try {
      return iteratorSort(iterable, source, offset, arr.length == 0 ? null : (MethodHandle) arr[0]);
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Cannot convert arg type " + RuntimeUtils.className(arr[0]) + " to Function", source, offset);
    }
  }

  /////////////////////////////

  // = lines

  public static Iterator stringLines(String str) {
    return RuntimeUtils.lines(str).iterator();
  }
  public static Object stringLinesWrapper(String str, String source, int offset, Object args) {
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
