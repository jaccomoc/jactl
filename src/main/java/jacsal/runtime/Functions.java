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

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Functions {

  private static final Map<Class, ClassLookup>              classes = new ConcurrentHashMap<>();
  private static final Map<String,List<FunctionDescriptor>> methods = new ConcurrentHashMap<>();
  private static final FunctionDescriptor NO_SUCH_METHOD = new FunctionDescriptor();

  private static class ClassLookup {
    Map<String,FunctionDescriptor> methods = new ConcurrentHashMap<>();
  }

  public static class FunctionDescriptor {
    public JacsalType       type;            // Type method is for
    public String           name;            // Jacsal method/function name
    public JacsalType       returnType;
    public List<JacsalType> paramTypes;
    public int              mandatoryArgCount;
    public String           implementingClass;
    public String           implementingMethod;
    public MethodHandle     wrapperHandle;   // Handle to wrapper: Object wrapper(clss, String source, int offset, Object args)

    public FunctionDescriptor(JacsalType type, String name, JacsalType returnType, List<JacsalType> paramTypes, int mandatoryArgCount, String implementingClass, String implementingMethod, MethodHandle wrapperHandle) {
      this.type = type;
      this.name = name;
      this.returnType = returnType;
      this.paramTypes = paramTypes;
      this.mandatoryArgCount = mandatoryArgCount;
      this.implementingClass = implementingClass;
      this.implementingMethod = implementingMethod;
      this.wrapperHandle = wrapperHandle;
    }

    private FunctionDescriptor() {}
  }

  /**
   * Register a method of a class. This can be used to register methods on
   * classes that don't normally have methods (e.g. numbers).
   */
  static void registerMethod(FunctionDescriptor descriptor) {
    var functions = methods.computeIfAbsent(descriptor.name, k -> new ArrayList<>());
    functions.add(descriptor);
  }

  /**
   * Register a global function
   */
  static void registerFunction() {
  }

  /**
   * Lookup method at compile time and return FunctionDescriptor if method exists
   */
  public static FunctionDescriptor lookupMethod(JacsalType type, String methodName) {
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
    Class parentClass = parent.getClass();
    var classLookup = classes.get(parentClass);
    if (classLookup == null) {
      classLookup = new ClassLookup();
      classes.put(parentClass, classLookup);
    }
    FunctionDescriptor function = classLookup.methods.get(methodName);
    if (function == null) {
      JacsalType parentType = JacsalType.typeOf(parent);
      function = findMatching(parentType, methodName);
      classLookup.methods.put(methodName, function);
    }

    if (function == NO_SUCH_METHOD) {
      return null;
    }

    return function.wrapperHandle.bindTo(parent);
  }

  private static FunctionDescriptor findMatching(JacsalType type, String methodName) {
    var functions = methods.get(methodName);
    if (functions != null) {
      // Look for exact match. TODO: handle subclasses/interfaces (e.g. Number)?
      var match = functions.stream().filter(f -> f.type == type).findFirst();
      return match.orElse(NO_SUCH_METHOD);
    }
    return NO_SUCH_METHOD;
  }
}
