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

import static jacsal.JacsalType.*;

public class Functions {

  private static final Map<Class, ClassLookup>      classes        = new ConcurrentHashMap<>();
  private static final Map<String,List<Descriptor>> methods        = new ConcurrentHashMap<>();
  private static final Descriptor                   NO_SUCH_METHOD = new Descriptor();

  private static class ClassLookup {
    Map<String, Descriptor> methods = new ConcurrentHashMap<>();
  }

  public static class Descriptor {
    public JacsalType       type;            // Type method is for
    public JacsalType       firstArgtype;    // Type of first arg (can be different to type - e.g. ANY)
    public String           name;            // Jacsal method/function name
    public JacsalType       returnType;
    public List<JacsalType> paramTypes;
    public int              mandatoryArgCount;
    public String           implementingClass;
    public String           implementingMethod;
    public boolean          needsLocation;
    public MethodHandle     wrapperHandle;   // Handle to wrapper: Object wrapper(clss, String source, int offset, Object args)

    public Descriptor(JacsalType type, JacsalType firstArgType, String name, JacsalType returnType, List<JacsalType> paramTypes, int mandatoryArgCount, String implementingClass, String implementingMethod, boolean needsLocation, MethodHandle wrapperHandle) {
      this.type = type;
      this.firstArgtype = firstArgType;
      this.name = name;
      this.returnType = returnType;
      this.paramTypes = paramTypes;
      this.mandatoryArgCount = mandatoryArgCount;
      this.implementingClass = implementingClass;
      this.implementingMethod = implementingMethod;
      this.needsLocation = needsLocation;
      this.wrapperHandle = wrapperHandle;
    }

    private Descriptor() {}
  }

  /**
   * Register a method of a class. This can be used to register methods on
   * classes that don't normally have methods (e.g. numbers).
   */
  static void registerMethod(Descriptor descriptor) {
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
  public static Descriptor lookupMethod(JacsalType type, String methodName) {
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
    var classLookup = classes.get(parentClass);
    if (classLookup == null) {
      classLookup = new ClassLookup();
      classes.put(parentClass, classLookup);
    }
    Descriptor function = classLookup.methods.get(methodName);
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

  private static Descriptor findMatching(JacsalType type, String methodName) {
    var functions = methods.get(methodName);
    if (functions == null) {
      return NO_SUCH_METHOD;
    }

    // Look for exact match and then generic match.
    // Number classes (int, long, doune, Decimal) can match on Number and
    // List/Map/Object[] can match on Iterable.
    var match = functions.stream().filter(f -> f.type == type).findFirst();
    if (match.isEmpty()) {
      if (type.isNumeric()) {
        // TODO
      }
      else
      if (type.is(LIST,MAP,OBJECT_ARR)) {
        match = functions.stream().filter(f -> f.type == ITERATOR).findFirst();
      }
    }
    return match.orElse(NO_SUCH_METHOD);
  }
}
