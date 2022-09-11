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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuiltinFunctions {

  public static void registerBuiltinFunctions() {
    register("size", "mapSize");
    register("size", "listSize");
  }

  private static void register(String name, String methodName) {
    try {
      Method[] methods = BuiltinFunctions.class.getDeclaredMethods();
      Method   method  = Arrays.stream(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
      if (method == null) {
        throw new IllegalStateException("Couldn't find method " + methodName + " in BuiltinFunctions");
      }
      JacsalType objType    = JacsalType.typeFromClass(method.getParameterTypes()[0]);
      JacsalType returnType = JacsalType.typeFromClass(method.getReturnType());
      List<JacsalType> paramTypes = Arrays.stream(method.getParameterTypes()).skip(1)
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
                                                                wrapperHandle));
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Couldn't find wrapper method for " + methodName, e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("Error looking up wrapper method for " + methodName, e);
    }

  }

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
