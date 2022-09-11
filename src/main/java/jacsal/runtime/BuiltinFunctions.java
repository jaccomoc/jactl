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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuiltinFunctions {

  public static void registerBuiltinFunctions() {
    register("size", "listSize", false);
    register("size", "objArrSize", false);
    register("size", "mapSize", false);
    register("each", "listEach", true);
    register("each", "objArrEach", true);
    register("each", "mapEach", true);
    register("collect", "listCollect", true);
    register("collect", "objArrCollect", true);
    register("collect", "mapCollect", true);
  }

  private static void register(String name, String methodName, boolean needsLocation) {
    try {
      Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
      Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
      if (method == null) {
        throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
      }
      JacsalType objType    = JacsalType.typeFromClass(method.getParameterTypes()[0]);
      JacsalType returnType = JacsalType.typeFromClass(method.getReturnType());
      List<JacsalType> paramTypes = Arrays.stream(method.getParameterTypes()).skip(needsLocation ? 3 : 1)
                                          .map(JacsalType::typeFromClass)
                                          .collect(Collectors.toList());
      MethodHandle wrapperHandle = MethodHandles.lookup().findStatic(BuiltinFunctions.class, methodName + "Wrapper",
                                                                     MethodType.methodType(Object.class,
                                                                                           objType.classFromType(), String.class, int.class, Object.class));
      Functions.registerMethod(new Functions.FunctionDescriptor(objType,
                                                                name,
                                                                returnType,
                                                                paramTypes,
                                                                0,
                                                                Type.getInternalName(BuiltinFunctions.class),
                                                                methodName,
                                                                needsLocation,
                                                                wrapperHandle));
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Couldn't find wrapper method for " + methodName, e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("Error looking up wrapper method for " + methodName, e);
    }

  }

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

  ////////////////////////////////

  // = each

  public static Object listEach(List list, String source, int offset, MethodHandle closure) {
    list.forEach(elem -> {
      try {
        Object ignored = closure.invokeExact(source, offset, (Object)(new Object[]{elem}));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    });
    return null;
  }
  public static Object listEachWrapper(List list, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return listEach(list, source, offset, (MethodHandle)((Object[])args)[0]);
  }

  public static Object objArrEach(Object[] arr, String source, int offset, MethodHandle closure) {
    for (Object elem: arr) {
      try {
        Object ignored = closure.invokeExact(source, offset, (Object)(new Object[]{elem}));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    }
    return null;
  }
  public static Object objArrEachWrapper(Object[] arr, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return objArrEach(arr, source, offset, (MethodHandle)((Object[])args)[0]);
  }

  public static Object mapEach(Map map, String source, int offset, MethodHandle closure) {
    map.forEach((key,value) -> {
      try {
        // We pass key/value as a single Object[] within our vargs Object[]. If the closure
        // takes more than one arg then the wrapper function will assign our two values to
        // the first two args. If the closure takes only one arg then it will be passed the
        // Object[] and the two values can be acess as though it were a list: it[0] and it[1],
        // for example.
        Object ignored = closure.invokeExact(source, offset, (Object)(new Object[]{new Object[]{key,value}}));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    });
    return null;
  }
  public static Object mapEachWrapper(Map map, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return mapEach(map, source, offset, (MethodHandle)((Object[])args)[0]);
  }

  ////////////////////////////////

  // = collect

  public static List listCollect(List list, String source, int offset, MethodHandle closure) {
    List result = new ArrayList();
    list.forEach(elem -> {
      try {
        result.add(closure.invokeExact(source, offset, (Object)(new Object[]{elem})));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    });
    return result;
  }
  public static Object listCollectWrapper(List list, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return listCollect(list, source, offset, (MethodHandle)((Object[])args)[0]);
  }

  public static List objArrCollect(Object[] arr, String source, int offset, MethodHandle closure) {
    List result = new ArrayList();
    for (Object elem: arr) {
      try {
        result.add(closure.invokeExact(source, offset, (Object)(new Object[]{elem})));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    }
    return result;
  }
  public static Object objArrCollectWrapper(Object[] arr, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return objArrCollect(arr, source, offset, (MethodHandle)((Object[])args)[0]);
  }

  public static Object mapCollect(Map map, String source, int offset, MethodHandle closure) {
    List result = new ArrayList();
    map.forEach((key,value) -> {
      try {
        // We pass key/value as a single Object[] within our vargs Object[]. If the closure
        // takes more than one arg then the wrapper function will assign our two values to
        // the first two args. If the closure takes only one arg then it will be passed the
        // Object[] and the two values can be acess as though it were a list: it[0] and it[1],
        // for example.
        result.add(closure.invokeExact(source, offset, (Object)(new Object[]{new Object[]{key,value}})));
      }
      catch (RuntimeException e) { throw e; }
      catch (Throwable t)        { throw new RuntimeError("Unexpected error: " + t.getMessage(), source, offset, t); }
    });
    return result;
  }
  public static Object mapCollectWrapper(Map map, String source, int offset, Object args) {
    validateArgCount(args, 1, 1, source, offset);
    return mapCollect(map, source, offset, (MethodHandle)((Object[])args)[0]);
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
    if (argCount > paramCount) {
      throw new RuntimeError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", source, offset);
    }
  }
}
