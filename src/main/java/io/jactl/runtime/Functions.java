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

import io.jactl.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.jactl.JactlType.*;

/**
 * Class that keeps track of which functions/methods have been registered.
 * The INSTANCE static field is used for functions/methods that are common across all JactlContext
 * contexts. If different JactlContext contexts need different sets of functions/methods then
 * the JactlContext will have its own Functions object to keep track of these and will look these
 * up first before falling back to searching for functions/methods in the INSTANCE object.
 */
public class Functions {

  // Used for functions/methods that are common to all JactlContexts
  public static final Functions INSTANCE = new Functions();

  private static final FunctionDescriptor NO_SUCH_METHOD  = new FunctionDescriptor();

  private final Map<Class, ClassLookup>              classes         = new ConcurrentHashMap<>();
  private final Map<String,List<FunctionDescriptor>> methods         = new ConcurrentHashMap<>();
  private final Map<String,FunctionDescriptor>       globalFunctions = new HashMap<>();
  private final Map<String,Expr.VarDecl>             globalFunDecls  = new HashMap<>();

  private JactlContext jactlContext = null;
  
  static {
    // Make sure builtin functions are registered if not already done
    BuiltinFunctions.registerBuiltinFunctions();
  }

  private Functions() {}

  public Functions(Functions other, JactlContext jactlContext) {
    this.jactlContext = jactlContext;
    classes.putAll(other.classes);
    methods.putAll(other.methods);
    globalFunctions.putAll(other.globalFunctions);
    globalFunDecls.putAll(other.globalFunDecls);
  }

  private static class ClassLookup {
    Map<String, FunctionDescriptor> methods = new ConcurrentHashMap<>();
  }

  /**
   * Register a method of a class. This can be used to register methods on
   * classes that don't normally have methods (e.g. numbers).
   */
  void registerMethod(String name, FunctionDescriptor descriptor) {
    List<FunctionDescriptor> functions = methods.computeIfAbsent(name, k -> new ArrayList<>());
    functions.add(descriptor);
  }

  public void deregisterFunction(String name) {
    FunctionDescriptor fn = globalFunctions.remove(name);
    if (fn instanceof JactlFunction) {
      // Aliases also includes primary name
      ((JactlFunction)fn).aliases.forEach(globalFunDecls::remove);
    }
  }

  public void deregisterFunction(JactlType type, String name) {
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
      }
      if (methods.get(name).isEmpty()) {
        methods.remove(name);
      }
    }
  }

  /**
   * Lookup method at compile time and return FunctionDescriptor if method exists
   * @param type        the type that owns the method
   * @param methodName  the name of the method
   * @return the FunctionDescriptor for the method or null if no such method
   */
  public FunctionDescriptor lookupMethod(JactlType type, String methodName) {
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
  JactlMethodHandle lookupWrapper(Object parent, String methodName) {
    Class       parentClass     = parent instanceof JactlIterator ? JactlIterator.class : parent.getClass();
    ClassLookup classLookup     = classes.computeIfAbsent(parentClass, clss -> new ClassLookup());
    FunctionDescriptor function = classLookup.methods.computeIfAbsent(methodName, name -> {
      JactlType parentType = JactlContext.typeOf(parent, jactlContext);
      return findMatching(parentType, name);
    });

    if (function == NO_SUCH_METHOD) {
      return null;
    }
    
    if (function.firstArgtype != null) {
      return function.wrapperHandle.bindTo(parent);
    }
    return function.wrapperHandle;
  }

  private FunctionDescriptor findMatching(JactlType objType, String methodName) {
    JactlType                type      = objType.unboxed();
    List<FunctionDescriptor> functions = methods.get(methodName);
    if (functions == null) {
      return NO_SUCH_METHOD;
    }

    // Look for exact match and then generic match.
    // List/Map/Object[]/String can match on Iterable.
    Optional<FunctionDescriptor> match = functions.stream().filter(f -> f.type.equals(type)).findFirst();
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

  public List<Pair<String,FunctionDescriptor>> getAllMethods(JactlType objType) {
    if (objType.is(UNKNOWN)) {
      return Utils.listOf();
    }
    return methods.keySet()
                  .stream()
                  .map(name -> Pair.create(name,findMatching(objType, name)))
                  .filter(p -> p.second != NO_SUCH_METHOD)
                  .collect(Collectors.toList());
  }

  public FunctionDescriptor lookupGlobalFunction(String name) {
    return globalFunctions.get(name);
  }

  public JactlMethodHandle lookupMethodHandle(String name) {
    FunctionDescriptor descriptor = globalFunctions.get(name);
    if (descriptor == null) {
      throw new IllegalStateException("Internal error: attempt to get MethodHandle to unknown built-in function " + name);
    }
    return descriptor.wrapperHandle;
  }

  public Expr.VarDecl getGlobalFunDecl(String name) {
    return globalFunDecls.get(name);
  }

  public Set<String> getGlobalFunctionNames() {
    return globalFunctions.keySet();
  }

  public void registerFunction(FunctionDescriptor function) {
    if (function.isMethod()) {
      function.getAliases().forEach(alias -> registerMethod(alias, function));
    }
    else {
      // aliases also includes primary name
      function.getAliases().forEach(alias -> globalFunctions.put(alias, function));
      Expr.VarDecl varDecl = Utils.funcDescriptorToVarDecl(function);
      function.getAliases().forEach(alias -> globalFunDecls.put(alias, varDecl));
    }
    BuiltinFunctions.allocateId(function.getImplentingClass());
  }
}
