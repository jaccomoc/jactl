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

import io.jactl.JactlType;

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.jactl.JactlType.*;

public class Functions {

  private static final Map<Class, ClassLookup>              classes        = new ConcurrentHashMap<>();
  private static final Map<String,List<FunctionDescriptor>> methods        = new ConcurrentHashMap<>();
  private static final FunctionDescriptor                   NO_SUCH_METHOD = new FunctionDescriptor();

  private static class ClassLookup {
    Map<String, FunctionDescriptor> methods = new ConcurrentHashMap<>();
  }

  /**
   * Register a method of a class. This can be used to register methods on
   * classes that don't normally have methods (e.g. numbers).
   */
  static void registerMethod(String name, FunctionDescriptor descriptor) {
    var functions = methods.computeIfAbsent(name, k -> new ArrayList<>());
    functions.add(descriptor);
  }

  static void deregisterMethod(JactlType type, String name) {
    Class typeClass = type.classFromType();
    if (classes.containsKey(typeClass)) {
      classes.get(typeClass).methods.remove(name);
    }
    if (methods.containsKey(name)) {
      for (var iter = methods.get(name).iterator(); iter.hasNext(); ) {
        var func = iter.next();
        if (func.name.equals(name)) {
          iter.remove();
        }
      }
      if (methods.get(name).size() == 0) {
        methods.remove(name);
      }
    }
  }

  /**
   * Lookup method at compile time and return FunctionDescriptor if method exists
   */
  public static FunctionDescriptor lookupMethod(JactlType type, String methodName) {
    var function = findMatching(type, methodName);
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
  static MethodHandle lookupWrapper(Object parent, String methodName) {
    Class parentClass = parent instanceof Iterator ? Iterator.class : parent.getClass();
    var classLookup = classes.computeIfAbsent(parentClass, clss -> new ClassLookup());
    var function    = classLookup.methods.computeIfAbsent(methodName, name -> {
      JactlType parentType = JactlType.typeOf(parent);
      return findMatching(parentType, name);
    });

    if (function == NO_SUCH_METHOD) {
      return null;
    }

    return function.wrapperHandle.bindTo(parent);
  }

  private static FunctionDescriptor findMatching(JactlType type, String methodName) {
    var functions = methods.get(methodName);
    if (functions == null) {
      return NO_SUCH_METHOD;
    }

    // Look for exact match and then generic match.
    // List/Map/Object[]/String can match on Iterable.
    var match = functions.stream().filter(f -> f.type.is(type)).findFirst();
    if (match.isEmpty()) {
      if (type.is(LIST,MAP,OBJECT_ARR,STRING,INT,LONG,DOUBLE,DECIMAL)) {
        match = functions.stream().filter(f -> f.type == ITERATOR).findFirst();
      }
    }
    if (match.isEmpty()) {
      if (type.is(INT,LONG,DOUBLE,DECIMAL)) {
        match = functions.stream().filter(f -> f.type == NUMBER).findFirst();
      }
    }
    // Final check is for ANY
    if (match.isEmpty()) {
      match = functions.stream().filter(f -> f.type.is(ANY)).findFirst();
    }
    return match.orElse(NO_SUCH_METHOD);
  }
}
