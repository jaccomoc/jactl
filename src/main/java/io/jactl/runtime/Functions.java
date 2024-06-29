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

import io.jactl.Expr;
import io.jactl.JactlType;
import io.jactl.Utils;

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.jactl.JactlType.*;

public class Functions {

  private static final Map<Class, ClassLookup>              classes        = new ConcurrentHashMap<>();
  private static final Map<String,List<FunctionDescriptor>> methods        = new ConcurrentHashMap<>();
  private static final FunctionDescriptor                   NO_SUCH_METHOD = new FunctionDescriptor();

  static {
    // Make sure builtin functions are registered if not already done
    BuiltinFunctions.registerBuiltinFunctions();
  }

  private static class ClassLookup {
    Map<String, FunctionDescriptor> methods = new ConcurrentHashMap<>();
  }

  /**
   * Register a method of a class. This can be used to register methods on
   * classes that don't normally have methods (e.g. numbers).
   */
  static void registerMethod(String name, FunctionDescriptor descriptor) {
    List<FunctionDescriptor> functions = methods.computeIfAbsent(name, k -> new ArrayList<>());
    functions.add(descriptor);
  }

  static void deregisterMethod(JactlType type, String name) {
    Class typeClass = type.classFromType();
    if (classes.containsKey(typeClass)) {
      classes.get(typeClass).methods.remove(name);
    }
    if (methods.containsKey(name)) {
      for (Iterator<FunctionDescriptor> iter = methods.get(name).iterator(); iter.hasNext(); ) {
        FunctionDescriptor func = iter.next();
        if (func.name.equals(name)) {
          iter.remove();
        }
        if (func instanceof JactlFunction) {
          ((JactlFunction)func).cleanUp();
        }
      }
      if (methods.get(name).size() == 0) {
        methods.remove(name);
      }
    }
  }

  public static Expr.VarDecl getGlobalFunDecl(String name) {
    return BuiltinFunctions.getGlobalFunDecl(name);
  }

  public static Set<String> getGlobalFunctionNames() {
    return BuiltinFunctions.getGlobalFunctionNames();
  }

  /**
   * Lookup method at compile time and return FunctionDescriptor if method exists
   * @param type        the type that owns the method
   * @param methodName  the name of the method
   * @return the FunctionDescriptor for the method or null if no such method
   */
  public static FunctionDescriptor lookupMethod(JactlType type, String methodName) {
    FunctionDescriptor function = findMatching(type, methodName);
    if (function == NO_SUCH_METHOD) {
      return null;
    }
    return function;
  }

  /**
   * Lookup method for given parent based on parent class.
   * We cache the lookups to make searches more efficient.
   * We return a MethodHandle that points to the wrapper function and is bound
   * to the parent.
   * @param parent      the parent object
   * @param methodName  the name of the method to look for
   * @return MethodHandle bound to parent
   */
  static JactlMethodHandle lookupWrapper(Object parent, String methodName) {
    Class       parentClass = parent instanceof JactlIterator ? JactlIterator.class : parent.getClass();
    ClassLookup classLookup = classes.computeIfAbsent(parentClass, clss -> new ClassLookup());
    FunctionDescriptor function    = classLookup.methods.computeIfAbsent(methodName, name -> {
      JactlType parentType = JactlType.typeOf(parent);
      return findMatching(parentType, name);
    });

    if (function == NO_SUCH_METHOD) {
      return null;
    }

    return function.wrapperHandle.bindTo(parent);
  }

  private static FunctionDescriptor findMatching(JactlType objType, String methodName) {
    JactlType                type      = objType.unboxed();
    List<FunctionDescriptor> functions = methods.get(methodName);
    if (functions == null) {
      return NO_SUCH_METHOD;
    }

    // Look for exact match and then generic match.
    // List/Map/Object[]/String can match on Iterable.
    Optional<FunctionDescriptor> match = functions.stream().filter(f -> f.type.is(type)).findFirst();
    if (!match.isPresent() && type.is(ARRAY) && type.isCastableTo(OBJECT_ARR)) {
      match = functions.stream().filter(f -> f.type.is(OBJECT_ARR)).findFirst();
    }
    if (!match.isPresent() && type.is(LIST,MAP,STRING,BYTE,INT,LONG,DOUBLE,DECIMAL,ARRAY)) {
      match = functions.stream().filter(f -> f.type.is(ITERATOR)).findFirst();
    }
    if (!match.isPresent() && type.is(BYTE,INT,LONG,DOUBLE,DECIMAL)) {
      match = functions.stream().filter(f -> f.type.is(NUMBER)).findFirst();
    }
    // Final check is for ANY
    if (!match.isPresent()) {
      match = functions.stream().filter(f -> f.type.is(ANY)).findFirst();
    }
    return match.orElse(NO_SUCH_METHOD);
  }

  public static List<FunctionDescriptor> getAllMethods(JactlType objType) {
    if (objType.is(UNKNOWN)) {
      return Utils.listOf();
    }
    return methods.keySet()
                  .stream()
                  .map(name -> findMatching(objType, name))
                  .filter(m -> m != NO_SUCH_METHOD)
                  .collect(Collectors.toList());
  }
}
