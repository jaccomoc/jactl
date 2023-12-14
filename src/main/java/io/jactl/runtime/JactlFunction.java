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
 * Class that captures information about a built-in function or method and registers it with the Jactl runtime.
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
 *   Jactl.method(STRING)
 *        .name("substring")
 *        .param("start")
 *        .param("end", Integer.MAX_VALUE)
 *        .impl(BuiltinFunctions.class, "stringSubstring")
 *        .register();
 * </pre>
 * <p>The register() call must be the last call in the chain of calls.</p>
 * <p>NOTE: the impl() method needs the name of a public static Object field in the same
 * class that is used to cache some information. By default "Data" is appended to the
 * method name to get the name of the field. So by default this:</p>
 * <pre>
 *        .impl(BuiltinFunctions.class, "stringSubstring")
 * </pre>
 * is the same as:
 * <pre>
 *        .impl(BuiltinFunctions.class, "stringSubstring", "stringSubstringData")
 * </pre>
 * <p>You can supply a different third argument to impl() if you wish to use a different
 * field name.</p>
 * <p>Other method calls that are supported:</p>
 * <dl>
 *   <dt>alias(String name)</dt><dd>Allow function/method to be invoked via another name.</dd>
 *   <dt>asyncParam(String name), asyncParam(String name, Object defaultValue)</dt>
 *   <dd>Define a parameter that when async itself (e.g. an async closure) makes the function async.</dd>
 *   <dt>asyncInstance(boolean value)</dt>
 *   <dd>For methods, if this is true then if the object instance is async, the method is async.</dd>
 * </dl>
 * @see <a href="https://github.com/jaccomoc/jactl/blob/master/docs/pages/integration-guide.md">Integration Guide</a>
 */
public class JactlFunction extends FunctionDescriptor {
  List<String>      aliases           = new ArrayList<>();
  Class             implementingClass;
  JactlMethodHandle methodHandle;
  Method            method;

  // total args including obj (for methods), and source/offset if needsLocation, and Continuation for async funcs
  int          argCount;

  String[]    paramNamesArr;                   // Cache of the names as an array for faster runtime access
  Class[]     paramClassesArr;                 // Class of each parameter
  Object[]    defaultVals = new Object[0];     // Default values for each parameter (or null)
  boolean     isAsyncInstance;                 // Actually async if instance is async (e.g. async ITERATOR)
  int         additionalArgs  = 0;             // needsLocation and methods need more args than just passed in

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

  /**
   * Register the function.
   * <p>Must be last method called on function.</p>
   */
  public void register() {
    BuiltinFunctions.registerFunction(this);
  }

  /**
   * Set name of function/method
   * @param name  the name
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction name(String name)  { this.name = name;    return alias(name); }

  /**
   * Create an alias for the function
   * @param name  the alias
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction alias(String name) { aliases.add(name);   return this;        }

  /**
   * Mandatory parameter
   * @param name  the parameter name
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction param(String name) { return param(name, MANDATORY, false); }

  /**
   * Optional parameter with default value.
   * @param name        the parameter name
   * @param defaultVal  the default value to use
   * @return the current JactlFunction for method chaining
   */

  public JactlFunction param(String name, Object defaultVal) { return param(name, defaultVal, false); }

  /**
   * Mandatory async parameter. If this parameter is async then this makes the function async.
   * For example, if parameter is a closure that the function invokes then if an async closure
   * is passed in, the function will be async.
   * @param name  the parameter name
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction asyncParam(String name) { return param(name, MANDATORY, true); }

  /**
   * Optional async parameter. If this parameter is async then this makes the function async.
   * For example, if parameter is a closure that the function invokes then if an async closure
   * is passed in, the function will be async.
   * @param name        the parameter name
   * @param defaultVal  the default value to use
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction asyncParam(String name, Object defaultVal) { return param(name, defaultVal, true); }

  /**
   * If object that method acts on is async then this makes function async. For example methods that
   * act on iterators which can be async.
   * @param value  true or false
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction asyncInstance(boolean value) { isAsyncInstance = value;  return this; }

  /**
   * Define a parameter. Internal use only.
   * @param name          the parameter name
   * @param defaultValue  the default value if parameter value not supplied
   * @param async         true if argument being async (e.g. async closure) makes this function async
   * @return the current JactlFunction for method chaining
   */
  private JactlFunction param(String name, Object defaultValue, boolean async) {
    paramNames.add(name);
    defaultVals = new ArrayList(Arrays.asList(defaultVals)){{ add(defaultValue); }}.toArray(new Object[0]);
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
   * @return the current JactlFunction for method chaining
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
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction impl(Class clss, String methodName, String fieldName) {
    this.implementingClass     = clss;
    this.implementingClassName = Type.getInternalName(clss);
    this.implementingMethod    = methodName;
    this.wrapperHandleField    = fieldName;
    return this;
  }

  /**
   * Name of public static method in impl class that can generate inline code
   * for functions that support inlining.
   * @param methodName  name of the public static method
   * @return the current JactlFunction for method chaining
   */
  public JactlFunction inline(String methodName) {
    this.inlineMethodName = methodName;
    return this;
  }

  ////////////////////////////////////////////////////////

  private Set<String> getMandatoryParams() {
    return IntStream.range(0, defaultVals.length)
                    .filter(i -> defaultVals[i] == MANDATORY)
                    .mapToObj(i -> paramNames.get(i))
                    .collect(Collectors.toSet());
  }

  void init() {
    if (name == null) {
      throw new IllegalArgumentException("Missing name for function");
    }
    this.method               = findMethod(implementingClass, implementingMethod);
    this.inlineMethod         = inlineMethodName == null ? null : findMethod(implementingClass, inlineMethodName);
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
      MethodHandle handle =
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

      handle = handle.bindTo(this);
      wrapperHandle = JactlMethodHandle.createFuncHandle(handle, type, name);

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
    this.paramClassesArr  = paramTypes.stream().map(t -> t.classFromType()).toArray(Class[]::new);
    this.firstArgtype     = firstParamType();
    this.returnType       = getReturnType();
    this.paramNamesArr    = paramNames.toArray(new String[paramNames.size()]);
    this.mandatoryParams  = getMandatoryParams();
    this.wrapperMethod    = null;
    this.isBuiltin        = true;
    this.isStatic         = true;   // Builtins are Java static methods even if they might be Jactl methods
    this.isAsync          = isAsync();
    this.isGlobalFunction = !isMethod();
  }

  public void cleanUp() {
    try {
      Field field = implementingClass.getDeclaredField(wrapperHandleField);
      field.set(null, null);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
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
        if (isVarArgs && p == paramNamesArr.length - 1 && (value instanceof List || value instanceof Object[])) {
          Object[] vargs = value instanceof List ? ((List)value).toArray() : (Object[])value;
          args = addVarArgs(args, i, vargs);
          i += vargs.length;
        }
        else {
          value = value == null ? null : RuntimeUtils.castTo(paramClassesArr[p], value, source, offset);
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

      // Check types and fill in any missing default values where value not supplied
      Object[] argVals = new Object[Math.max(argCount, args.length + additionalArgs)];
      int i = commonArgs(obj, source, offset, argVals);
      for (int p = 0; p < Math.min(args.length, paramClassesArr.length); p++) {
        if (isVarArgs && p == paramClassesArr.length - 1) {
          // Rest of args are varargs so just copy them over
          int varArgCount = args.length - p;
          System.arraycopy(args, p, argVals, i, varArgCount);
          i += varArgCount;
        }
        else {
          argVals[i++] = RuntimeUtils.castTo(paramClassesArr[p], args[p], source, offset);
        }
      }
      if (i < argCount) {
        // Copy any remaining default values needed
        for (int p = args.length; p < defaultVals.length; p++) {
          if (isVarArgs && p == defaultVals.length - 1 && defaultVals[p] instanceof Object[]) {
            argVals = addVarArgs(argVals, i, (Object[])defaultVals[p]);
            break;
          }
          argVals[i++] = defaultVals[p];
        }
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

  /**
   * Add values from vargs ito args at given position.
   * If args not of exact size required then copy into a new array and return it.
   * @param args   the arg array with current values
   * @param pos    position we are up to in args
   * @param vargs  the values to be copied
   * @return args or new args array
   */
  private static Object[] addVarArgs(Object[] args, int pos, Object[] vargs) {
    if (args.length != pos + vargs.length) {
      Object[] newArgs = new Object[pos + vargs.length];
      System.arraycopy(args, 0, newArgs, 0, pos);
      args = newArgs;
    }
    System.arraycopy(vargs, 0, args, pos, vargs.length);
    return args;
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
    List<Method> methods = Arrays.stream(clss.getDeclaredMethods())
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
