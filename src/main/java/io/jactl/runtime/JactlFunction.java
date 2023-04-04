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
import io.jactl.Utils;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class that captures information about a builtin function or method and registers it with the Jactl runtime.
 * Example usage for a global function:
 * <pre>
 *   Jactl.function()
 *        .name("sprintf")
 *        .param("format")
 *        .param("args", new Object[0])
 *        .impl(BuiltinFunctions.class, "sprintf")
 *        .register();
 * </pre>
 *
 * For methods, pass the JactlType of the method class to the JactlFunction constructor:
 * <pre>
 *      new JactlFunction(STRING).name("substring")
 *                                .param("start")
 *                                .param("end", Integer.MAX_VALUE)
 *                                .impl(BuiltinFunctions.class, "stringSubstring"));
 * </pre>
 * <p>
 * The impl() call specifies the implementing class and the name of the static method within that class that
 * has the implementation to be invoked.
 * In addition, callers must guarantee that a public static field exists in that class of type "Object" that
 * Jactl can use for caching some information that will be used at runtime. By default this field has the same
 * name as the method name specified in impl() but if desired a different name can be supplied as an optional
 * third parameter:
 * <pre>
 *      new JactlFunction(STRING).name("substring")
 *                                .param("start")
 *                                .param("end", Integer.MAX_VALUE)
 *                                .impl(BuiltinFunctions.class, "stringSubstring", "stringSubstringJactlData"));
 * </pre>
 * </p>
 * <p>
 * We look up the given static method on the specified implementation class and use that as the implementation for
 * the method/function.
 * <p>For methods, the object type specifies the type of Jactl objects for which we want the method to apply.
 * The first argument of the given static method will be supplied with the instance for which the method should
 * be invoked.
 * We allow a different type to be specified than the actual type of the first argument to support scenarios where
 * we want a method to work for Iterator types (List, Map, Iterator, Object[]) and for which not natural super class
 * exists. In this case the type of the first argument is Object but the objType parameter will be JactlType.ITERATOR.
 * </p>
 * <p>Methods are flagged as async if arg1 (the arg after the 0th arg which is the object on which the method applies)
 * has type Continuation. This means that when invoking the method we need to generate the scaffolding that allows
 * Continuations to be thrown and caught and capture state. As well as flagging the method as async or not we allow
 * a list of argument numbers to be passed in for situations where the invocation is only async if one of the given
 * arguments itself is async.</p>
 * <p>For example,
 * <pre>  x.map{ ... }</pre>
 * is only async if the closure passed in is itself async. However, if the calls are chained:
 * <pre>  x.map{ ... }.filter{ ... }</pre>
 * then the collection methods (map, filter, each, collect, etc) are async if either the closure passed in is async
 * or if any of the preceding calls in the chain were async.
 * <p>Since the preceding call is the object on which the current method is being applied, we can think of such a chain
 * of method calls like this:</p>
 * <pre>  filter(map(x, {...}), {...})</pre>
 * <p>So, the call is async if arg0 is async or if arg1 is async. This means that for filter, map, etc. we specify:
 * <pre>
 *   new JactlFunction(ITERATOR).name(...)
 *                               .asyncInstance(true)       // async if object being acted on is async
 *                               .asyncParam("closure")     // async if closure passed in is async
 * </pre>
 */

public class JactlFunction extends FunctionDescriptor {
  List<String> aliases           = new ArrayList<>();
  Class        implementingClass;
  MethodHandle methodHandle;
  Method       method;

  // total args including obj (for methods), and source/offset if needsLocation, and Continuation for async funcs
  int          argCount;

  String[]     paramNamesArr = new String[0];   // Cache of the names as an array for faster runtime access
  Object[]     defaultVals   = new Object[0];
  boolean      isAsyncInstance;                 // Actually async if instance is async (e.g. async ITERATOR)
  int          additionalArgs  = 0;             // needsLocation and methods need more args than just passed in

  private static final Object MANDATORY = new Object();

  /**
   * Constructor for methods
   * @param methodClass  the JactlType that the method is for
   */
  public JactlFunction(JactlType methodClass) {
    this.type = methodClass;
  }

  /**
   * Constructor for global functions
   */
  public JactlFunction() {}

  public void register() {
    BuiltinFunctions.registerFunction(this);
  }

  public JactlFunction name(String name)  { this.name = name;    return alias(name); }
  public JactlFunction alias(String name) { aliases.add(name);   return this;        }

  /**
   * Mandatory parameter
   * @param name  the parameter name
   */
  public JactlFunction param(String name)                    { return param(name, MANDATORY, false); }
  public JactlFunction param(String name, Object defaultVal) { return param(name, defaultVal, false); }

  public JactlFunction asyncParam(String name)                    { return param(name, MANDATORY, true); }
  public JactlFunction asyncParam(String name, Object defaultVal) { return param(name, defaultVal, true); }

  public JactlFunction asyncInstance(boolean value) { isAsyncInstance = value;  return this; }

  /**
   * Parameter
   * @param name          the parameter name
   * @param defaultValue  the default value if parameter value not supplied
   * @param async         true if argument being async (e.g. async closure) makes this function async
   */
  private JactlFunction param(String name, Object defaultValue, boolean async) {
    paramNames.add(name);
    defaultVals = new ArrayList<>(Arrays.asList(defaultVals)){{ add(defaultValue); }}.toArray(Object[]::new);
    // If no optional params then mandatory count must be all existing params
    if (Arrays.stream(defaultVals).allMatch(v -> v == MANDATORY)) {
      mandatoryArgCount = defaultVals.length;
    }
    if (async) {
      asyncArgs.add(paramNames.size() - 1);
    }
    return this;
  }

  /**
   * The implementing class and method for the function/method.
   * Data field defaults to methodName + "Data" (i.e. method name with Data appended to it).
   * @param clss        the class
   * @param methodName  the name of the method
   */
  public JactlFunction impl(Class clss, String methodName) {
    return impl(clss, methodName, methodName + "Data");
  }

  /**
   * The implementing class and method for the function/method.
   * @param clss        the class
   * @param methodName  the name of the method
   * @param fieldName   name of a public static class field of type Object where Jactl can cache some runtime data.
   *                    Field must not have been initialised to anything (other than null).
   */
  public JactlFunction impl(Class clss, String methodName, String fieldName) {
    this.implementingClass     = clss;
    this.implementingClassName = Type.getInternalName(clss);
    this.implementingMethod    = methodName;
    this.wrapperHandleField    = fieldName;
    return this;
  }

  public boolean isVarArgs() {
    return isVarArgs;
  }

  public Set<String> getMandatoryParams() {
    return IntStream.range(0, defaultVals.length)
                    .filter(i -> defaultVals[i] == MANDATORY)
                    .mapToObj(i -> paramNames.get(i))
                    .collect(Collectors.toSet());
  }

  ////////////////////////////////////////////////////////

  void init() {
    if (name == null) {
      throw new IllegalArgumentException("Missing name for function");
    }
    this.method               = findMethod(implementingClass, implementingMethod);
    Class<?>[] parameterTypes = method.getParameterTypes();
    this.methodHandle         = RuntimeUtils.lookupMethod(implementingClass, implementingMethod, method.getReturnType(), parameterTypes);
    this.argCount             = method.getParameterCount();
    this.isVarArgs            = parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].equals(Object[].class);
    this.isAsync = isMethod() ? parameterTypes.length > 1 && parameterTypes[1].equals(Continuation.class)
                              : parameterTypes.length > 0 && parameterTypes[0].equals(Continuation.class);
    if ((isAsyncInstance || asyncArgs.size() > 0) && !isAsync) {
      throw new IllegalArgumentException("Function " + name + ": Cannot register function with async params or asyncInstance if function does not have Continuation parameter");
    }
    if (isAsync) {
      if (isMethod()) {
        // Bump argument counting for methods as 0 means the instance
        IntStream.range(0, asyncArgs.size()).forEach(i -> asyncArgs.set(i, asyncArgs.get(i) + 1));
      }
      if (isAsyncInstance) {
        // Add "0" to indicate that the instance could be async
        asyncArgs.add(0, 0);
      }
    }
    additionalArgs = (isMethod() ? 1 : 0) + (isAsync() ? 1 : 0);
    int firstArg = additionalArgs;

    // Check if method needs location passed to it
    this.paramCount = paramNames.size();
    if (paramCount + additionalArgs + 2 == argCount &&
        parameterTypes[firstArg].equals(String.class) && parameterTypes[firstArg+1].equals(int.class)) {
      needsLocation = true;
      additionalArgs += 2;
    }
    if (paramCount + additionalArgs != argCount) {
      throw new IllegalArgumentException("Inconsistent argument count: method " + implementingClass.getName() + "." +
                                         implementingMethod + "() has " + argCount + " args but derived count of "
                                         + (paramCount + additionalArgs) +
                                         " from function registration");
    }

    try {
      wrapperHandle =
        isMethod() ? MethodHandles.lookup().findVirtual(JactlFunction.class, "wrapper",
                                                        MethodType.methodType(Object.class,
                                                                              Object.class,
                                                                              Continuation.class,
                                                                              String.class,
                                                                              int.class,
                                                                              Object[].class))
                            : MethodHandles.lookup().findVirtual(JactlFunction.class, "wrapper",
                                                                 MethodType.methodType(Object.class,
                                                                                       Continuation.class,
                                                                                       String.class,
                                                                                       int.class,
                                                                                       Object[].class));
      wrapperHandle = wrapperHandle.bindTo(this);

      // Store handle in static field we have been given
      Field field = implementingClass.getDeclaredField(wrapperHandleField);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Field " + wrapperHandleField + " in class " +
                                           implementingClass.getName() + " must be static");

      }
      if (field.get(null) != null) {
        throw new IllegalArgumentException("Field " + wrapperHandleField + " in class " +
                                           implementingClass.getName() + " already has a value");
      }
      field.set(null, wrapperHandle);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalArgumentException("Error accessing " + wrapperHandleField + " in class " +
                                         implementingClass.getName() + ": " + e.getMessage(), e);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Error accessing wrapper() in JactlFunction: " + e.getMessage(), e);
    }

    this.paramTypes       = getParamTypes();
    this.firstArgtype     = firstParamType();
    this.returnType       = getReturnType();
    this.paramNamesArr    = paramNames.toArray(String[]::new);
    this.mandatoryParams  = getMandatoryParams();
    this.wrapperMethod    = null;
    this.isBuiltin        = true;
    this.isStatic         = true;   // Builtins are Java static methods even if they might be Jactl methods
    this.isAsync          = isAsync();
    this.isGlobalFunction = !isMethod();
  }

  ////////////////////////////////////////////////////////

  public boolean isAsync() {
    return isAsync;
  }

  public JactlType getReturnType() {
    return JactlType.typeFromClass(method.getReturnType());
  }

  public List<JactlType> getParamTypes() {
    int reservedParams = (isMethod() ? 1 : 0) + (isAsync() ? 1 : 0) + (needsLocation ? 2 : 0);
    return Arrays.stream(method.getParameterTypes())
                 .skip(reservedParams)
                 .map(JactlType::typeFromClass)
                 .collect(Collectors.toList());
  }

  public JactlType firstParamType() {
    if (isMethod()) {
      return JactlType.typeFromClass(method.getParameterTypes()[0]);
    }
    return null;
  }

  public boolean isMethod() {
    return type != null;
  }

  public JactlType declaredMethodClass() {
    if (!isMethod()) {
      return null;
    }
    return JactlType.typeFromClass(method.getParameterTypes()[0]);
  }

  public Object wrapper(Continuation c, String source, int offset, Object[] args) {
    return wrapper(null, c, source, offset, args);
  }

  public Object wrapper(Object obj, Continuation c, String source, int offset, Object[] args) {
    // Named args
    if (args.length == 1 && args[0] instanceof NamedArgsMap) {
      Map<String,Object> argMap = new LinkedHashMap((Map)args[0]);
      args = new Object[argCount];
      int i = commonArgs(obj, source, offset, args);
      for (int p = 0; p < paramNamesArr.length; p++) {
        String paramName = paramNamesArr[p];
        Object value;
        if (argMap.containsKey(paramName)) {
          value = argMap.remove(paramName);
        }
        else {
          value = defaultVals[p];
          if (value == MANDATORY) {
            throw new RuntimeError("Missing value for mandatory parameter '" + paramName + "'", source, offset);
          }
        }
        if (isVarArgs && p == paramNamesArr.length - 1 && value instanceof List) {
          List vargs = (List)value;
          var newArgs = new Object[argCount + vargs.size() - 1];
          System.arraycopy(args, 0, newArgs, 0, i);
          args = newArgs;
          System.arraycopy(vargs.toArray(), 0, args, i, vargs.size());
          i += vargs.size();
        }
        else {
          args[i++] = value;
        }
      }
      if (argMap.size() > 0) {
        throw new RuntimeError("No such " + Utils.plural("parameter", argMap.size()) + ": " +
                               String.join(", ", argMap.keySet()), source, offset);
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

      // Fill in any missing default values where value not supplied
      if (additionalArgs > 0 || args.length != argCount) {
        Object[] argVals = new Object[Math.max(argCount, args.length + additionalArgs)];
        int i = commonArgs(obj, source, offset, argVals);
        System.arraycopy(args, 0, argVals, i, args.length);
        i += args.length;
        if (i < argCount) {
          // Copy any remaining default values needed
          System.arraycopy(defaultVals, args.length, argVals, i, argCount - i);
        }
        args = argVals;
      }
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

  /**
   * Add needed values to arg array and return count of how many were added
   */
  private int commonArgs(Object obj, String source, int offset, Object[] argVals) {
    int i = 0;
    if (isMethod()) {
      argVals[i++] = obj;
    }
    if (isAsync) {
      argVals[i++] = null;       // Continuation
    }
    if (needsLocation) {
      argVals[i++] = source;
      argVals[i++] = offset;
    }
    return i;
  }

  private void validateArgCount(Object[] args, String source, int offset) {
    int argCount   = args.length;
    int paramCount = isVarArgs ? -1 : paramNamesArr.length;

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

  private static Method findMethod(Class clss, String methodName) {
    var methods = Arrays.stream(clss.getDeclaredMethods())
                        .filter(m -> m.getName().equals(methodName))
                        .filter(m -> Modifier.isStatic(m.getModifiers()))
                        .collect(Collectors.toList());
    if (methods.size() == 0) {
      throw new IllegalArgumentException("Could not find static method " + methodName + " in class " + clss.getName());
    }
    if (methods.size() > 1) {
      throw new IllegalArgumentException("Found multiple static methods called " + methodName + " in class " + clss.getName());
    }
    return methods.get(0);
  }
}
