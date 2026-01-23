/*
 * Copyright © 2022,2023,2024  James Crawford
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


import io.jactl.JactlContext;
import io.jactl.JactlType;
import io.jactl.Utils;
import io.jactl.compiler.JactlClassLoader;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *   This class is also used for representing methods on registered classes where a new type is registered along
 *   with its static and non-static methods that we want to be able to invoke from Jactl scripts.
 *   In this case, we are not registering static implementation methods of some other implementation class 
 *   that pretend to be a method of a built-in class, but are using class instance and class static methods
 *   from the actual class that will be instantiated by the scripts. This maps more closely to the way that classes
 *   defined by Jactl scripts are represented (although they use the FunctionDescriptor instances instead of this
 *   class).
 * </p>
 */
public class RegisteredClassMethod extends FunctionDescriptor {
  JactlMethodHandle methodHandle;
  Method            method;
  Class             implementingClass;
  boolean           canThrow;            // Can throw exception and requires method generated in helper class
  int               argCount;
  String[]          paramNamesArr;                   // Cache of the names as an array for faster runtime access
  Class[]           paramClassesArr;                 // Class of each parameter
  JactlContext      jactlContext;
  
  /**
   * Constructor for methods
   */
  RegisteredClassMethod(Class implementingClass, JactlContext jactlContext) {
    this.implementingClass = implementingClass;
    this.jactlContext      = jactlContext;
  }

  /**
   * Set name of function/method
   * @param name  the name
   * @return the current RegisteredClassMethod for method chaining
   */
  RegisteredClassMethod name(String name)  { this.name = name; return this; }

  /**
   * Mandatory parameter
   * @param name  the parameter name
   * @return the current RegisteredClassMethod for method chaining
   */
  RegisteredClassMethod param(String name) { 
    paramNames.add(name);
    return this;
  }

  RegisteredClassMethod impl(Method method) {
    this.method                = method;
    this.implementingClassName = Type.getInternalName(method.getDeclaringClass());
    this.implementingMethod    = method.getName();
    this.canThrow              = method.getExceptionTypes().length > 0;
    return this;
  }

  RegisteredClassMethod wrapperHandle(String wrapperHandleClass, String wrapperHandleField) {
    this.wrapperHandleClassName = wrapperHandleClass.replace('.','/');
    this.wrapperHandleField     = wrapperHandleField;
    return this;
  }
  
  RegisteredClassMethod canThrow(boolean canThrow) {
    this.canThrow = canThrow;
    return this;
  }

  RegisteredClassMethod type(JactlType type) {
    this.type = type;
    return this;
  }
  

  ////////////////////////////////////////////////////////

  void init(Map<Class,Class> mappedTypes) {
    if (wrapperHandleField == null) { throw new IllegalArgumentException("No wrapper handle field specified via impl() method"); }
    if (name == null)               { throw new IllegalArgumentException("Missing name for function"); }

    this.argCount             = method.getParameterCount() - (needsLocation ? 2 : 0);
    this.mandatoryArgCount    = argCount;
    this.methodHandle         = RuntimeUtils.lookupMethod(method.getDeclaringClass(), name, method);
    this.isStaticMethod       = Modifier.isStatic(method.getModifiers());
    this.isStaticImplementation = isStaticMethod;
    this.paramCount           = paramNames.size();
    this.paramTypes           = getParamTypes(mappedTypes);
    this.paramClassesArr      = paramTypes.stream().map(JactlType::classFromType).toArray(Class[]::new);
    this.firstArgtype         = isStaticMethod ? null : type;
    this.returnType           = getReturnType(mappedTypes);
    this.paramNamesArr        = paramNames.toArray(new String[0]);
    this.mandatoryParams      = new HashSet<>(paramNames);
    this.wrapperMethod        = null;
    this.isBuiltin            = true;
    this.isAsync              = false;
    this.isGlobalFunction     = false;

    // Convert from internal form back to dotted form
    String wrapperClassName = wrapperHandleClassName.replace('/', '.');

    try {
      MethodHandle handle =
        isStaticImplementation ? MethodHandles.lookup().findVirtual(RegisteredClassMethod.class, "wrapper",
                                                                    MethodType.methodType(Object.class,
                                                                            Continuation.class,
                                                                            String.class,
                                                                            int.class,
                                                                            Object[].class))
                               : MethodHandles.lookup().findVirtual(RegisteredClassMethod.class, "wrapper",
                                                      MethodType.methodType(Object.class,
                                                                            Object.class,
                                                                            Continuation.class,
                                                                            String.class,
                                                                            int.class,
                                                                            Object[].class));

      handle = handle.bindTo(this);
      wrapperHandle = JactlMethodHandle.createFuncHandle(handle, type, name, this);

      // Store handle in static field we have been given
      Class wrapperHandleClass = JactlClassLoader.forName(wrapperClassName);
      Field field              = wrapperHandleClass.getDeclaredField(wrapperHandleField);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Field " + wrapperHandleField + " in class " + wrapperClassName + " must be static");
      }
      Object fieldValue = field.get(null);
      if (fieldValue != null) {
        if (!(fieldValue instanceof JactlMethodHandle.FunctionWrapperHandle)) {
          throw new IllegalArgumentException("Field " + wrapperHandleField + " of " + wrapperClassName + " already has a value");
        }
        FunctionDescriptor function = ((JactlMethodHandle.FunctionWrapperHandle) fieldValue).getFunction();
        if (!isEquivalent(function)) {
          throw new IllegalArgumentException("Function shares same implementing class and field name with existing function but is not equivalent: new=" + this + ", existing=" + function);
        }
      }
      field.set(null, wrapperHandle);
    }
    catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalArgumentException("Error accessing " + wrapperHandleField + " in class " + wrapperClassName + ": " + e.getMessage(), e);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Error accessing wrapper() in RegisteredClassMethod: " + e.getMessage(), e);
    }
  }

  ////////////////////////////////////////////////////////


  @Override
  public Class getImplentingClass() {
    return implementingClass; 
  }

  @Override
  public boolean isMethod() {
    return true;
  }

  private JactlType getReturnType(Map<Class,Class> mappedTypes) {
    return JactlContext.typeFromClass(mappedTypes.getOrDefault(method.getReturnType(), method.getReturnType()), jactlContext);
  }

  private List<JactlType> getParamTypes(Map<Class,Class> mappedTypes) {
    Function<Class,JactlType> getType = clss -> {
      JactlType type = JactlContext.typeFromClass(clss, jactlContext);
      if (type == null) {
        throw new IllegalArgumentException("Java class not declared as Jactl type: " + clss);
      }
      return type;
    };
    return Arrays.stream(method.getParameterTypes())
                 .map(type -> mappedTypes.getOrDefault(type, type))
                 .map(getType)
                 .collect(Collectors.toList());
  }

  public Object wrapper(Continuation c, String source, int offset, Object[] args) {
    return wrapper(null, c, source, offset, args);
  }

  public Object wrapper(Object obj, Continuation c, String source, int offset, Object[] args) {
    // Named args
    if (args.length == 1 && args[0] instanceof NamedArgsMap) {
      Map<String,Object> argMap = new LinkedHashMap<>((Map)args[0]);
      int i = 0;
      args = new Object[argCount + (isStaticImplementation ? 0 : 1)];
      if (isInstanceMethod()) {
        args[i++] = obj;
      }
      for (int p = 0; p < paramNamesArr.length; p++) {
        String paramName = paramNamesArr[p];
        Object value;
        if (argMap.containsKey(paramName)) {
          value = argMap.remove(paramName);
        }
        else {
          throw new RuntimeError("Missing value for mandatory parameter '" + paramName + "'", source, offset);
        }
        value = value == null ? null : RuntimeUtils.castTo(paramClassesArr[p], value, true, source, offset);
        args[i++] = value;
      }
      if (!argMap.isEmpty()) {
        throw new RuntimeError("No such " + Utils.plural("parameter", argMap.size()) + ": " + String.join(", ", argMap.keySet()), source, offset);
      }
    }
    else {
      // Check for case where list of args passed in. If we have a single parameter (or a single
      // mandatory parameter) then we assume list is the value for that parameter. Otherwise, we
      // treat the list as a list of arg values.
      if (args.length == 1 && args[0] instanceof List) {
        boolean passListAsList = paramNamesArr.length == 1 || mandatoryArgCount == 1;
        if (!passListAsList) {
          args = ((List) args[0]).toArray();
        }
      }

      validateArgCount(args, source, offset);

      // Check types and fill in any missing default values where value not supplied
      Object[] argVals = new Object[Math.max(argCount, args.length + (isStaticImplementation ? 0 : 1))];
      int i = 0;
      if (!isStaticImplementation) {
        argVals[i++] = obj;
      }
      for (int p = 0; p < Math.min(args.length, paramClassesArr.length); p++) {
        argVals[i++] = RuntimeUtils.castTo(paramClassesArr[p], args[p], true, source, offset);
      }
      args = argVals;
    }

    try {
      return methodHandle.invokeWithArguments(args);
    }
    catch (Continuation cont) {
      throw cont;
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (ClassCastException e) {
      throw new RuntimeError("Incompatible argument type for " + name + "()", source, offset, e);
    }
    catch (Throwable e) {
      throw new RuntimeError("Error invoking " + name + "()", source, offset, e);
    }
  }

  private void validateArgCount(Object[] args, String source, int offset) {
    int argCount   = args.length;
    int paramCount = paramNamesArr.length;

    if (argCount > 0 && args[0] instanceof NamedArgsMap) {
      throw new RuntimeError("Named args not supported for built-in functions", source, offset);
    }

    if (argCount < mandatoryArgCount) {
      String atLeast = mandatoryArgCount == paramCount ? "" : "at least ";
      throw new RuntimeError("Missing mandatory arguments (arg count of " + argCount + " but expected " + atLeast + mandatoryArgCount + ")", source, offset);
    }
    if (paramCount >= 0 && argCount > paramCount) {
      throw new RuntimeError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", source, offset);
    }
  }

  @Override
  public String getMethodDescriptor(JactlType returnType, List<JactlType> paramTypes) {
    // Irrespective of what types we think we want, we need to use the underlying unmapped types
    // when generating the method descriptor in order to be able to find the right method to invoke.
    return Type.getMethodDescriptor(method);
  }

  @Override
  public String toString() {
    return type.toString() + "." + name + "(" + paramNames() + "):" + returnType + (isStaticImplementation ? " (static)" : "");
  }

  private String paramNames() {
    String result = "";
    for (int i = 0; i < paramCount; i++) {
      if (i > 0) { result += ","; }
      result += paramTypes.get(i) + " " + paramNames.get(i);
    }
    return result;
  }
}
