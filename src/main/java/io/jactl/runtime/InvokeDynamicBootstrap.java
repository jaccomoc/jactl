/*
 * Copyright © 2022-2026 James Crawford
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
import org.objectweb.asm.Type;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bootstrap for InvokeDynamic sites
 */
public class InvokeDynamicBootstrap {

  private static final MethodHandle ADAPTER;                  // Adapter for invoking MethodHandle
  private static final MethodHandle GET_CLASS_OR_NULL;
  private static final MethodHandle GET_CLASS;
  private static final MethodHandle GET_BOUND_AT_LEVEL;       // Extracts bound object at a given nesting depth
  private static final MethodHandle INVOKE_WRAPPER_HANDLE;    // Invokes wrapper method handle as fallback

  private static final MethodHandle METHOD_OR_FIELD_ADAPTER;     // Adapter for invoking a given method/field
  private static final MethodHandle BINARY_OP_ADAPTER;           // Adapter for invoking binary operations
  private static final MethodHandle IS_SAME_CLASS;               // Check classes are still the same
  private static final MethodHandle METHOD_OR_FIELD_INVOKER;     // Invokes wrapper as fallback
  
  private static final MethodHandle SAME_JMH;
  private static final MethodHandle SAME_JMH_OBJECT1;
  private static final MethodHandle SAME_JMH_OBJECT2;
  private static final MethodHandle SAME_JMH_OBJECT3;
  private static final MethodHandle SAME_JMH_OBJECTN;
  private static final MethodHandle SAME_JMH_ARG1;
  private static final MethodHandle SAME_JMH_ARG2;
  private static final MethodHandle SAME_JMH_ARG3;
  private static final MethodHandle SAME_JMH_ARGN;
  private static final MethodHandle SAME_CLASS;
  private static final MethodHandle SAME_CLASS_OBJECT1;
  private static final MethodHandle SAME_CLASS_OBJECT2;
  private static final MethodHandle SAME_CLASS_OBJECT3;
  private static final MethodHandle SAME_CLASS_OBJECTN;
  private static final MethodHandle SAME_CLASS_ARG1;
  private static final MethodHandle SAME_CLASS_ARG2;
  private static final MethodHandle SAME_CLASS_ARG3;
  private static final MethodHandle SAME_CLASS_ARGN;

  private static final MethodHandle BYTE_VALUE;
  private static final MethodHandle SHORT_VALUE;
  private static final MethodHandle INT_VALUE;
  private static final MethodHandle LONG_VALUE;
  private static final MethodHandle DOUBLE_VALUE;
  private static final MethodHandle DECIMAL_VALUE;
  private static final MethodHandle IS_TRUTH;
  
  private static final MethodType   GET_CLASS_TYPE = MethodType.methodType(Class.class);
  
  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      ADAPTER                 = lookup.findStatic(InvokeDynamicBootstrap.class, "adapter", MethodType.methodType(Object.class, MaxDepthCallSite.class, String.class, int.class, Object[].class));
      GET_CLASS_OR_NULL       = lookup.findStatic(InvokeDynamicBootstrap.class, "getClassOrNull", MethodType.methodType(Class.class, Object.class));
      GET_CLASS               = lookup.findVirtual(Object.class, "getClass", MethodType.methodType(Class.class));
      GET_BOUND_AT_LEVEL      = lookup.findStatic(InvokeDynamicBootstrap.class, "getBoundAtLevel", MethodType.methodType(Object.class, int.class, JactlMethodHandle.class));
      INVOKE_WRAPPER_HANDLE   = lookup.findStatic(InvokeDynamicBootstrap.class, "invokeWrapper", MethodType.methodType(Object.class, JactlMethodHandle.class, Continuation.class, String.class, int.class, Object[].class));
       
      METHOD_OR_FIELD_ADAPTER = lookup.findStatic(InvokeDynamicBootstrap.class, "methodOrFieldAdapter", MethodType.methodType(Object.class, MaxDepthCallSite.class, MethodHandles.Lookup.class, String.class, String.class, int.class, boolean.class, Object[].class));
      BINARY_OP_ADAPTER       = lookup.findStatic(InvokeDynamicBootstrap.class, "binaryOpAdapter", MethodType.methodType(Object.class, MaxDepthCallSite.class, MethodHandles.Lookup.class, String.class, MethodHandle.class, String.class, String.class, int.class, int.class, String.class, int.class, Object.class, Object.class));
      IS_SAME_CLASS           = lookup.findStatic(InvokeDynamicBootstrap.class, "isSameClass", MethodType.methodType(boolean.class, Class.class, Class.class));
      METHOD_OR_FIELD_INVOKER = lookup.findStatic(InvokeDynamicBootstrap.class, "invokeMethodOrField", MethodType.methodType(Object.class, String.class, Object.class, Continuation.class, String.class, int.class, boolean.class, Object[].class));
      SAME_JMH                = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmh", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class));
      SAME_JMH_OBJECT1        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject1", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class));
      SAME_JMH_OBJECT2        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject2", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class));
      SAME_JMH_OBJECT3        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject3", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, Object.class));
      SAME_JMH_OBJECTN        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObjectN", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object[].class));
      SAME_JMH_ARG1           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg1", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, JactlMethodHandle.class, Object.class));
      SAME_JMH_ARG2           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg2", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, Class.class, JactlMethodHandle.class, Object.class, Object.class));
      SAME_JMH_ARG3           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg3", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, Class.class, Class.class, JactlMethodHandle.class, Object.class, Object.class, Object.class));
      SAME_JMH_ARGN           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArgN", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class[].class, JactlMethodHandle.class, Object[].class));
      SAME_CLASS              = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClass", MethodType.methodType(boolean.class, Class.class, Object.class));
      SAME_CLASS_OBJECT1      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject1", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class));
      SAME_CLASS_OBJECT2      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject2", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class));
      SAME_CLASS_OBJECT3      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject3", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, Object.class));
      SAME_CLASS_OBJECTN      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObjectN", MethodType.methodType(boolean.class, Class.class, Object.class, Object[].class));
      SAME_CLASS_ARG1         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg1", MethodType.methodType(boolean.class, Class.class, Class.class, Object.class, Object.class));
      SAME_CLASS_ARG2         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg2", MethodType.methodType(boolean.class, Class.class, Class.class, Class.class, Object.class, Object.class, Object.class));
      SAME_CLASS_ARG3         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg3", MethodType.methodType(boolean.class, Class.class, Class.class, Class.class, Class.class, Object.class, Object.class, Object.class, Object.class));
      SAME_CLASS_ARGN         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArgN", MethodType.methodType(boolean.class, Class.class, Class[].class, Object.class, Object[].class));
      
      BYTE_VALUE              = lookup.findVirtual(Number.class, "byteValue", MethodType.methodType(byte.class));
      SHORT_VALUE             = lookup.findVirtual(Number.class, "shortValue", MethodType.methodType(short.class));
      INT_VALUE               = lookup.findVirtual(Number.class, "intValue", MethodType.methodType(int.class));
      LONG_VALUE              = lookup.findVirtual(Number.class, "longValue", MethodType.methodType(long.class));
      DOUBLE_VALUE            = lookup.findVirtual(Number.class, "doubleValue", MethodType.methodType(double.class));
      DECIMAL_VALUE           = lookup.findStatic(InvokeDynamicBootstrap.class, "decimalValue", MethodType.methodType(BigDecimal.class, Number.class));
      
      IS_TRUTH                = lookup.findStatic(RuntimeUtils.class, "isTruth", MethodType.methodType(boolean.class, Object.class));
    }
    catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Used for calls to things that look like functions: f(...)
   * @param lookup    the lookup object
   * @param name      ignored
   * @param siteType  the site type of the call site
   * @param source    the source code
   * @param offset    offset where call is occurring
   * @return the new call site
   */
  public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType siteType, String source, int offset) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeAdapter(siteType, cs, source, offset));
    return cs;
  }

  private static MethodHandle makeAdapter(MethodType siteType, MutableCallSite cs, String source, int offset) {
    return MethodHandles.insertArguments(ADAPTER, 0, cs, source, offset)
                        .asCollector(Object[].class, siteType.parameterCount())
                        .asType(siteType);
  }

  /**
   * Used for calls to x.y() where y may be a method or a field of the object/map
   * On stack: Object obj, String src, int offset, arg1, arg2, ...
   * @param lookup             the lookup object
   * @param methodOrField      the name of the field/method
   * @param siteType           the call site argument types
   * @param source             the source
   * @param offset             offset into source for method call
   * @param captureStackTraces 1 if stack traces should be captured for null errors, 0 otherwise
   * @return the call site
   */
  public static CallSite bootstrapMethodOrField(MethodHandles.Lookup lookup, String methodOrField, MethodType siteType, String source, int offset, int captureStackTraces) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeMethodOrFieldAdapter(siteType, cs, methodOrField, lookup, source, offset, captureStackTraces == 1));
    return cs;
  }
  
  // Used for calls to x op y
  // On stack: Object x, Object y
  public static CallSite bootstrapBinaryOp(MethodHandles.Lookup lookup, String name, MethodType siteType,
                                          MethodHandle methodHandle,
                                          String operator, String originalOp, int minScale, int captureStackTrace,
                                          String source, int offset) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeBinaryOpAdapter(siteType, cs, name, lookup, methodHandle, operator, originalOp, minScale, captureStackTrace, source, offset));
    return cs;
  }

  private static MethodHandle makeBinaryOpAdapter(MethodType siteType, MaxDepthCallSite cs, String name, MethodHandles.Lookup lookup,
                                                  MethodHandle methodHandle,
                                                  String operator, String originalOp, int minScale, int captureStackTrace,
                                                  String source, int offset) {
    return MethodHandles.insertArguments(BINARY_OP_ADAPTER, 0, cs, lookup, name, methodHandle, operator, originalOp, minScale, captureStackTrace, source, offset)
                        .asType(siteType);
  }

  private static MethodHandle makeMethodOrFieldAdapter(MethodType siteType, MaxDepthCallSite cs, String methodOrFieldName, MethodHandles.Lookup lookup, String source, int offset, boolean captureStackTraces) {
    return MethodHandles.insertArguments(METHOD_OR_FIELD_ADAPTER, 0, cs, lookup, methodOrFieldName, source, offset, captureStackTraces)
                        .asCollector(Object[].class, siteType.parameterCount())
                        .asType(siteType);
  }
  
  private static MethodHandle makeMethodOrFieldInvoker(MethodType siteType, String methodOrField, String source, int offset, boolean captureStackTraces) {
    int extraArgCount = siteType.parameterCount() - 1;  // number of args after Object
    MethodHandle mh = MethodHandles.insertArguments(METHOD_OR_FIELD_INVOKER, 0, methodOrField);
    return MethodHandles.insertArguments(mh, 1,  (Continuation)null, source, offset, captureStackTraces)
                        .asCollector(Object[].class, extraArgCount)
                        .asType(siteType);
  }

  // Builds a MethodHandle for invoking wrapper method handle
  // It calls jmh.invoke(c, source, offset, args) where the args are passed as Object[].
  private static MethodHandle makeWrapperInvoker(MethodType siteType, String source, int offset) {
    int extraArgCount = siteType.parameterCount() - 1;  // number of args after JMH
    return MethodHandles.insertArguments(INVOKE_WRAPPER_HANDLE, 1, null, source, offset)
                        .asCollector(Object[].class, extraArgCount)
                        .asType(siteType);
  }

  // args: args[0] = JMH, args[1..] = typed call args.
  public static Object adapter(MaxDepthCallSite cs, String source, int offset, Object[] args) throws Throwable {
    JactlMethodHandle jmh = (JactlMethodHandle) args[0];
    if (jmh == null) {
      throw new NullError("Null value for Function", source, offset);
    }
    MethodType siteType = cs.type();
    
    Object[] actualArgs = Arrays.copyOfRange(args, 1, args.length);

    // Guard against chain depth that becomes unwieldy
    if (cs.depth.incrementAndGet() > MaxDepthCallSite.MAX_DEPTH) {
      cs.setTarget(makeWrapperInvoker(siteType, source, offset));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return jmh.invoke((Continuation)null, source, offset, actualArgs);
    }

    // Collect bound handles in innermost-first order.
    // list[i].boundObj is the value that should go to param i of the underlying method.
    List<JactlMethodHandle> boundList = new ArrayList<>();
    jmh.populateBoundHandles(boundList);
    
    boolean[] heapLocalsPassedToFn = jmh.heapLocalsPassedToFn();
    boolean staticFunction = boundList.isEmpty() || boundList.get(0).getBoundObject() instanceof HeapLocal;
    
    MethodHandle underlying = jmh.handleToUnderlyingFunction();
    MethodHandle adapted;

    // Normalise first types of args corresponding to bound objects so we can accept
    // filters that return Object regardless of the concrete type (HeapLocal, etc.)
    MethodType normalizedType = underlying.type();
    int idx = 0;
    if (!staticFunction) {
      normalizedType = normalizedType.changeParameterType(idx++, Object.class);
    }
    for (int i = 0; i < heapLocalsPassedToFn.length; ++i) {
      if (heapLocalsPassedToFn[i]) {
        normalizedType = normalizedType.changeParameterType(idx++, Object.class);
      }
    }
    int boundArgCount = idx;
    adapted = underlying.asType(normalizedType);

    // Replace each of the bound object params with a filter that extracts the
    // corresponding bound object from the incoming JMH.
    // The boundList is innermost-first, so boundList[i].boundObj = param i of underlying.
    // From the outermost JMH, param i requires traversing N-1-i levels inward where N
    // is the boundList size.
    MethodHandle[] filters = new MethodHandle[boundArgCount];
    idx = 0;
    for (int i = 0; i < boundList.size(); i++) {
      if (i == 0 && !staticFunction || heapLocalsPassedToFn[i - (staticFunction ? 0 : 1)]) {
        int level = boundList.size() - 1 - i;  // 0 = outermost (last bound), N-1 = innermost (first bound)
        filters[idx++] = MethodHandles.insertArguments(GET_BOUND_AT_LEVEL, 0, level); // (JMH) -> Object
      }
    }
    adapted = MethodHandles.filterArguments(adapted, 0, filters);

    if (boundArgCount > 0) {
      // Collapse the N JMH inputs to a single JMH at position 0 via permuteArguments
      int        oldCount      = adapted.type().parameterCount();   // N + rest
      int        newCount      = oldCount - boundArgCount + 1;      // 1 + rest
      Class<?>[] newParamTypes = new Class<?>[newCount];
      newParamTypes[0] = JactlMethodHandle.class;
      for (int i = 1; i < newCount; i++) {
        newParamTypes[i] = adapted.type().parameterType(boundArgCount - 1 + i);
      }
      MethodType newType = MethodType.methodType(adapted.type().returnType(), newParamTypes);

      int[] reorder = new int[oldCount];
      for (int i = 0; i < boundArgCount; i++) {
        reorder[i] = 0;           // all N JMH copies -> arg 0
      }
      for (int i = boundArgCount; i < oldCount; i++) {
        reorder[i] = i - boundArgCount + 1;   // rest -> arg 1, 2, ...
      }
      adapted = MethodHandles.permuteArguments(adapted, newType, reorder);
    }
    
    if (boundArgCount == 0) {
      // Not a BoundHandle: prepend a dropped JMH arg so siteType's arg[0] is consumed.
      adapted = MethodHandles.dropArguments(adapted, 0, JactlMethodHandle.class);
    }

    if (jmh.isAsync()) {
      adapted = MethodHandles.insertArguments(adapted, 1, (Continuation)null);
    }
    if (jmh.needsLocation()) {
      adapted = MethodHandles.insertArguments(adapted, 1, source, offset);
    }

    // We need to build a guard that tests for changes of the JactlMethodHandle and changes of argument types
    // so that we can install a new call site in case we need different coercions applied. We optimise for
    // parameters that are of type Object.class since no coercion is done so argument type changing has no effect.
    MethodHandle guard = createGuard(jmh, siteType, adapted.type(), args);

    try {
      // Check that args are compatible
      adapted = coerceArgumentsOrFail(siteType, adapted, args, 1);
      adapted = adapted.asType(siteType);
    }
    catch (WrongMethodTypeException e) {
      // Type coercion failed (e.g. param count mismatch). Install a stable wrapper-path guard.
      cs.setTarget(MethodHandles.guardWithTest(guard, makeWrapperInvoker(siteType, source, offset), cs.getTarget()));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return jmh.invoke(null, source, offset, actualArgs);
    }

    // Install the direct-call guard.
    cs.setTarget(MethodHandles.guardWithTest(guard, adapted, cs.getTarget()));
    MutableCallSite.syncAll(new MutableCallSite[]{cs});

    // For the first call, use the wrapper rather than invokeWithArguments(args).
    // invokeWithArguments boxes everything to Object[] then converts back.
    // The cached direct-call chain handles future calls at the JVM bytecode level.
    return jmh.invoke(null, source, offset, actualArgs);
  }

  // args: args[0] = obj, args[1..] = typed call args.
  public static Object methodOrFieldAdapter(MaxDepthCallSite cs, MethodHandles.Lookup lookup, String methodOrField, String source, int offset, boolean captureStackTraces, Object[] args) throws Throwable {
    Object     parent   = args[0];
    MethodType siteType = cs.type();

    Object[] actualArgs = Arrays.copyOfRange(args, 1, args.length);

    // Guard against chain depth that becomes unwieldy
    if (cs.depth.incrementAndGet() > MaxDepthCallSite.MAX_DEPTH) {
      cs.setTarget(makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return invokeMethodOrField(methodOrField, parent, null, source, offset, captureStackTraces, actualArgs);
    }

    JactlContext jactlContext = RuntimeState.getState().getContext();

    MethodHandle isSameClass = MethodHandles.filterArguments(IS_SAME_CLASS.bindTo(parent.getClass()), 0, GET_CLASS);
    isSameClass = MethodHandles.dropArguments(isSameClass, 1, siteType.parameterList().subList(1, siteType.parameterCount()));

    // If we have a JactlObject then we can look up method/field
    MethodHandle fallback = cs.getTarget();
    if (parent instanceof JactlObject) {
      boolean isStatic = false;
      Object fieldOrMethod = ((JactlObject) parent)._$j$getFieldsAndMethods().get(methodOrField);
      if (fieldOrMethod == null) {
        fieldOrMethod = ((JactlObject) parent)._$j$getStaticFieldsAndMethods().get(methodOrField);
        isStatic = true;
      }
      if (fieldOrMethod != null) {
        JactlMethodHandle jmh;
        if (fieldOrMethod instanceof JactlMethodHandle) {
          jmh = (JactlMethodHandle) fieldOrMethod;
          // Now get the actual method
          MethodHandle adapted = jmh.handleToUnderlyingFunction();
          if (isStatic) {
            adapted = MethodHandles.dropArguments(adapted, 0, Object.class);
          }
          
          if (jmh.isAsync()) {
            adapted = MethodHandles.insertArguments(adapted, 1, (Continuation) null);
          }
          if (jmh.needsLocation()) {
            adapted = MethodHandles.insertArguments(adapted, 1, source, offset);
          }

          MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);

          // Install call site that checks that the parent class is still the same and that the argument
          // types haven't changed and then invokes the method. If there are conversion errors then we catch
          // the exception and install the wrapper fallback instead.
          try {
            adapted = coerceArgumentsOrFail(siteType, adapted, args, 1);
            adapted = adapted.asType(siteType);
            cs.setTarget(MethodHandles.guardWithTest(guard, adapted, fallback));
          }
          catch (WrongMethodTypeException e) {
            // Argument types not directly compatible so fall back to invoking wrapper to let it do any
            // coercion required and fill in missing arguments etc (or throw an error if needed).
            cs.setTarget(MethodHandles.guardWithTest(guard, makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback));
          }
        }
        else {
          // We have a field so we install something that checks parent class and invokes slow path through
          // invokeMethodOrField() which loads field to get JactlMethodHandle and invokes it.
          cs.setTarget(MethodHandles.guardWithTest(isSameClass, makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback));
        }
        MutableCallSite.syncAll(new MutableCallSite[]{cs});

        return invokeMethodOrField(methodOrField, parent, null, source, offset, captureStackTraces, actualArgs);
      }
    }

    // See if there is a method of this name
    FunctionDescriptor func = jactlContext.getFunctions().lookupFunction(parent, methodOrField);

    // If no method then check for a field and invoke method handle contained in the field
    if (func == null) {
      // If we have a map then we might have a field
      if (parent instanceof Map) {
        cs.setTarget(MethodHandles.guardWithTest(isSameClass, makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback));
      }
      else {
        // Check for host class
        JactlMethodHandle jmh = null;
        if (jactlContext.allowHostAccess) {
          jmh = jactlContext.lookupWrapperForHostClass(parent, methodOrField, actualArgs, source, offset);
        }
        if (jmh == null) {
          // No method for this class so fallback to default mechanism that will throw appropriate error
          cs.setTarget(MethodHandles.guardWithTest(isSameClass, makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback));
        }
        else {
          int          pos     = jmh.isBoundHandle() ? 1 : 0;
          MethodHandle adapted = jmh.handleToUnderlyingFunction();
          if (!jmh.isBoundHandle()) {
            // Ignore the object since we have a static method
            adapted = MethodHandles.dropArguments(adapted, pos++, Object.class);
          }

          MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);
          
          try {
            adapted = coerceArgumentsOrFail(siteType, adapted, args, pos);
            adapted = adapted.asType(siteType);
            cs.setTarget(MethodHandles.guardWithTest(guard, adapted, fallback));
          }
          catch (WrongMethodTypeException e) {
            // Argument types not directly compatible so fall back to slow path to let it do any coercion required
            cs.setTarget(MethodHandles.guardWithTest(guard, makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback));
          }
        }
      }
      MutableCallSite.syncAll(new MutableCallSite[]{cs});

      // First time we still invoke the wrapper after installing call site
      return invokeMethodOrField(methodOrField, parent, null, source, offset, captureStackTraces, actualArgs);
    }

    MethodHandle adapted;
    String       className = func.implementingClassName.replace('/', '.');
    try {
      Class<?> clss = lookup.lookupClass().getClassLoader().loadClass(className);
      Method method = Arrays.stream(clss.getMethods())
                            .filter(m -> m.getName().equals(func.implementingMethod))
                            .filter(m -> Type.getMethodDescriptor(m).equals(func.getImplementingMethodDescriptor()))
                            .findFirst()
                            .orElseThrow(() -> new NoSuchMethodException("Internal error: could not find '" + methodOrField + "' in class '" + className + "'"));
      adapted = lookup.unreflect(method);
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException("Internal error: could not find class '" + className + "'", e);
    }

    if (func.isStaticMethod) {
      adapted = MethodHandles.dropArguments(adapted, 0, Object.class);
    }

    if (func.isAsync()) {
      adapted = MethodHandles.insertArguments(adapted, 1, (Continuation)null);
    }
    if (func.needsLocation) {
      adapted = MethodHandles.insertArguments(adapted, 1, source, offset);
    }
    
    int argCount = args.length - 1;
    
    // To start with we assume we can invoke directly if we don't have too many arguments
    boolean directInvoke = argCount <= func.paramCount;
    
    // We need to insert default values for missing arguments (if they are simple values)
    if (argCount < func.paramCount) {
      for (int i = argCount; i < func.defaultVals.length; i++) {
        if (func.defaultVals[i] == FunctionDescriptor.NON_TRIVIAL || func.defaultVals[i] == JactlFunction.MANDATORY) {
          directInvoke = false;
          break;
        }
      }
      if (directInvoke) {
        // Insert missing values after the parent object and the other args
        adapted = MethodHandles.insertArguments(adapted, args.length, Arrays.copyOfRange(func.defaultVals, argCount, func.defaultVals.length));
      }
    }

    MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);

    try {
      // Insert argument coercion for numeric types or if incompatible types
      // we will throw WrongMethodTypeException and fall back to invoking wrapper
      adapted = coerceArgumentsOrFail(siteType, adapted, args, 1);
      adapted = adapted.asType(siteType);
    }
    catch (WrongMethodTypeException e) {
      directInvoke = false;
    }
    
    adapted = MethodHandles.guardWithTest(guard, directInvoke ? adapted : makeMethodOrFieldInvoker(siteType, methodOrField, source, offset, captureStackTraces), fallback);
    cs.setTarget(adapted);
    MutableCallSite.syncAll(new MutableCallSite[]{cs});

    // First time we still invoke the wrapper after installing call site
    return invokeMethodOrField(methodOrField, parent, null, source, offset, captureStackTraces, actualArgs);
  }

  public static Object binaryOpAdapter(MaxDepthCallSite cs, MethodHandles.Lookup lookup, String operatorName,
                                       MethodHandle methodHandle, String operator, String originalOp, int minScale,
                                       int captureStackTrace, String source, int offset, Object left, Object right) throws Throwable {
    MethodType siteType = cs.type();

    if (left == null) {
      return methodHandle.invoke(left, right, operator, originalOp, minScale, captureStackTrace == 1, source, offset);
    }
    
    // Guard against chain depth that becomes unwieldy
    if (cs.depth.incrementAndGet() > MaxDepthCallSite.MAX_DEPTH) {
      cs.setTarget(MethodHandles.insertArguments(methodHandle, 2, operator, originalOp, minScale, captureStackTrace == 1, source, offset)
                                .asType(siteType));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return methodHandle.invoke(left, right, operator, originalOp, minScale, captureStackTrace == 1, source, offset);
    }

    MethodHandle isSameClass = MethodHandles.filterArguments(IS_SAME_CLASS.bindTo(left.getClass()), 0, GET_CLASS_OR_NULL);
    isSameClass = MethodHandles.dropArguments(isSameClass, 1, siteType.parameterList().subList(1, siteType.parameterCount()))
                               .asType(siteType.changeReturnType(boolean.class));

    MethodHandle fallback = cs.getTarget();

    MethodHandle adapted = MethodHandles.insertArguments(BinaryOpMethods.lookupMethod(left.getClass().getName(), operatorName), 2,
                                                         operator, originalOp, minScale, captureStackTrace == 1, source, offset)
                                        .asType(siteType);
    adapted = MethodHandles.guardWithTest(isSameClass, adapted, fallback);
    cs.setTarget(adapted);
    MutableCallSite.syncAll(new MutableCallSite[]{cs});

    // First time we still invoke the wrapper after installing call site
    return methodHandle.invoke(left, right, operator, originalOp, minScale, captureStackTrace == 1, source, offset);
  }

  private static final HashMap<Class<?>, MethodHandle> NUMERIC_FILTERS = new HashMap<>();
  private static final HashMap<Class<?>, Integer>      NUMERIC_RANK    = new HashMap<>();
  static {
    NUMERIC_FILTERS.put(Byte.class,       BYTE_VALUE);
    NUMERIC_FILTERS.put(Short.class,      SHORT_VALUE);
    NUMERIC_FILTERS.put(Integer.class,    INT_VALUE);
    NUMERIC_FILTERS.put(Long.class,       LONG_VALUE);
    NUMERIC_FILTERS.put(Double.class,     DOUBLE_VALUE);
    NUMERIC_FILTERS.put(BigDecimal.class, DECIMAL_VALUE);

    // Special case for BigDecimal as we need can't rely on automatic coercion at all 
    NUMERIC_RANK.put(BigDecimal.class, -1);
    
    NUMERIC_RANK.put(Byte.class,       1);
    NUMERIC_RANK.put(Short.class,      2);
    NUMERIC_RANK.put(Integer.class,    3);
    NUMERIC_RANK.put(Long.class,       4);
    NUMERIC_RANK.put(Double.class,     5);
  }
  
  // NOTE: adapted is the MethodHandle for the actual method with potentially some default values inserted
  private static MethodHandle coerceArgumentsOrFail(MethodType siteType, MethodHandle adapted, Object[] args, int argStart) {
    MethodType adaptedType = adapted.type();
    if (args.length > adaptedType.parameterCount()) {
      throw WRONG_METHOD_TYPE_EXCEPTION;
    }
    for (int i = argStart; i < args.length; i++) {
      Object   arg       = args[i];
      Class<?> argType   = boxedType(arg == null ? siteType.parameterType(i) : arg.getClass());
      Class<?> paramType = boxedType(adaptedType.parameterType(i));
      if (argType == paramType || paramType.isAssignableFrom(argType)) {
        continue;
      }
      if (arg != null && Number.class.isAssignableFrom(paramType)) {
        if (Number.class.isAssignableFrom(argType)) {
          // If widening then no need to do anything since asType() takes care of this
          Integer prank = NUMERIC_RANK.get(paramType);
          Integer arank = NUMERIC_RANK.get(argType);
          if (prank == null || arank == null) {
            throw WRONG_METHOD_TYPE_EXCEPTION;
          }
          // If automatic promotion will work
          if (prank > 0 && arank > 0 && prank >= arank) {
            continue;
          }
          MethodHandle filter = NUMERIC_FILTERS.get(paramType);
          if (filter == null) {
            // This shouldn't happen but just in case...
            throw WRONG_METHOD_TYPE_EXCEPTION;
          }
          adapted = MethodHandles.filterArguments(adapted, i, filter); 
        }
        else {
          // We don't have a compatible conversion
          throw WRONG_METHOD_TYPE_EXCEPTION;
        }
      }
      else if (paramType == Boolean.class) {
        adapted = MethodHandles.filterArguments(adapted, i, IS_TRUTH);
      }
      else if (arg != null || paramType == JactlMethodHandle.class) {
        // Arg type is not compatible or we don't have a coercion we can do
        // here so use wrapper function
        throw WRONG_METHOD_TYPE_EXCEPTION;
      }
    }
    return adapted;
  }
  
  private static final WrongMethodTypeException WRONG_METHOD_TYPE_EXCEPTION = new WrongMethodTypeException();
  
  public static Class<?> getClassOrNull(Object obj) {
    return obj == null ? null : obj.getClass();
  }

  // Traverses `level` steps inward through the BoundHandle chain (0 = outermost) and returns
  // the bound object at that depth.  Called at invocation time by the filter handles.
  public static Object getBoundAtLevel(int level, JactlMethodHandle jmh) {
    for (int i = 0; i < level; i++) {
      jmh = jmh.getInnerHandle();
    }
    return jmh.getBoundObject();
  }

  // Wrapper-path fallback: invokes the JMH's pre-built wrapper handle (which already has all
  // bound objects applied via the BoundHandle chain) with the standard calling convention.
  public static Object invokeWrapper(JactlMethodHandle jmh, Continuation c, String source, int offset, Object[] args) throws Throwable {
    return jmh.invoke(c, source, offset, args);
  }

  // Used as fall back when we can't invoke method/field directly
  public static Object invokeMethodOrField(String methodOrField, Object parent, Continuation c, String source, int offset, boolean captureStackTraces, Object[] args) {
    return RuntimeUtils.invokeMethodOrField(parent, methodOrField, false, false, args, captureStackTraces, source, offset); 
  }
  
  public static boolean isSameClass(Class<?> clss1, Class<?> clss2) {
    return clss1 == clss2;
  }

  public static class MaxDepthCallSite extends MutableCallSite {
    AtomicInteger depth = new AtomicInteger(0);
    static final int MAX_DEPTH = Integer.getInteger("jactl.invokeDynamic.maxDepth", 8);

    MaxDepthCallSite(MethodType type) { super(type); }
  }
  
  public static BigDecimal decimalValue(Number n) {
    if (n instanceof BigDecimal) {
      return (BigDecimal) n;
    }
    if (n instanceof Double) {
      return BigDecimal.valueOf((Double) n);
    }
    return BigDecimal.valueOf(n.longValue());
  }
  
  private static Class<?> boxedType(Class<?> type) {
    return MethodType.methodType(type).wrap().returnType();
  }
  
  private static MethodHandle createGuard(JactlMethodHandle jmh, MethodType siteType, MethodType type, Object[] args) {
    // Create a guard that checks if the JactlMethodHandle still refers to the same underlying function
    // and checks if the argument types have changed.
    // We have a special case if all parameters are of type Object since no coercion is required and args
    // will always be passed in irrespective of their type.
    // NOTE: we start from argument position 1 to skip the always present JMH
    final int startPos = 1;
    boolean allObjectParams = isAllObjectParams(type, startPos);

    Class[] argTypes = getArgTypes(type, args);

    if (allObjectParams) {
      switch (args.length - startPos) {
        case 0:  return SAME_JMH.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_JMH_OBJECT1.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_JMH_OBJECT2.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_JMH_OBJECT3.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_JMH_OBJECTN.bindTo(jmh).asCollector(Object[].class, args.length - startPos).asType(siteType.changeReturnType(boolean.class));
      }
    }
    else {
      switch (args.length - startPos) {
        case 0:  return SAME_JMH.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_JMH_ARG1.bindTo(jmh).bindTo(argTypes[0]).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_JMH_ARG2.bindTo(jmh).bindTo(argTypes[0]).bindTo(argTypes[1]).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_JMH_ARG3.bindTo(jmh).bindTo(argTypes[0]).bindTo(argTypes[1]).bindTo(argTypes[2]).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_JMH_ARGN.bindTo(jmh).bindTo(argTypes).asCollector(Object[].class, args.length - startPos).asType(siteType.changeReturnType(boolean.class));
      }
    }
  }

  private static MethodHandle createGuardForMethod(Class<?> expected, MethodType siteType, MethodType type, Object[] args) {
    // Create a guard that checks if arg0 matches the expected target class and checks if the argument types
    // have changed.
    // We have a special case if all parameters are of type Object since no coercion is required and args
    // will always be passed in irrespective of their type.
    // NOTE: we start from argument position 1 to skip the always present Object target
    int startPos = 1;
    boolean allObjectParams = isAllObjectParams(type, startPos);

    Class[] argTypes = getArgTypes(type, args);

    if (allObjectParams) {
      switch (argTypes.length) {
        case 0:  return SAME_CLASS.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_CLASS_OBJECT1.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_CLASS_OBJECT2.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_CLASS_OBJECT3.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_CLASS_OBJECTN.bindTo(args[0].getClass()).asCollector(Object[].class, args.length - startPos).asType(siteType.changeReturnType(boolean.class));
      }
    }
    else {
      switch (argTypes.length) {
        case 0:  return SAME_CLASS.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_CLASS_ARG1.bindTo(args[0].getClass()).bindTo(argTypes[0]).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_CLASS_ARG2.bindTo(args[0].getClass()).bindTo(argTypes[0]).bindTo(argTypes[1]).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_CLASS_ARG3.bindTo(args[0].getClass()).bindTo(argTypes[0]).bindTo(argTypes[1]).bindTo(argTypes[2]).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_CLASS_ARGN.bindTo(args[0].getClass()).bindTo(argTypes).asCollector(Object[].class, argTypes.length).asType(siteType.changeReturnType(boolean.class));
      }
    }
  }

  private static Class[] getArgTypes(MethodType type, Object[] args) {
    // Record the argument types so we can check if they change. If the parameter type is Object.class
    // then we don't care if the argument type changes since we don't have to worry about implicit
    // coercion problems. We record UNTYPED.class as the class type for these types of arguments.
    Class[] argTypes = new Class[args.length - 1];
    for (int i = 1; i < args.length; i++) {
      Object arg = args[i];
      Class<?> paramClass;
      if (i >= type.parameterCount()) {
        paramClass = type.parameterType(type.parameterCount() - 1) == Object[].class ? Object.class : null;
      }
      else {
        paramClass = type.parameterType(i);
      }
      argTypes[i - 1] = paramClass == Object.class ? UNTYPED.class 
                                                   : arg == null ? null : arg.getClass();
    }
    return argTypes;
  }

  private static boolean isAllObjectParams(MethodType type, int startPos) {
    for (int idx = startPos; idx < type.parameterCount(); idx++) {
      // Check for parameter type that is not Object.class and also check that we don't have a varargs Object[]
      // which also counts as "all parameter are Object.class"
      Class<?> paramType = type.parameterType(idx);
      if (paramType != Object.class && !(idx == type.parameterCount() - 1 && paramType == Object[].class)) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameJmh(JactlMethodHandle expected, JactlMethodHandle jmh) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject1(JactlMethodHandle expected, JactlMethodHandle jmh, Object arg1) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject2(JactlMethodHandle expected, JactlMethodHandle jmh, Object arg1, Object arg2) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject3(JactlMethodHandle expected, JactlMethodHandle jmh, Object arg1,  Object arg2, Object arg3) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObjectN(JactlMethodHandle expected, JactlMethodHandle jmh, Object[] args) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }

  private static boolean sameJmhArg1(JactlMethodHandle expected, Class<?> c1, JactlMethodHandle jmh, Object arg1) {
    return ((expected == jmh) || (expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction())) &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1)));
  }
  
  private static boolean sameJmhArg2(JactlMethodHandle expected, Class<?> c1, Class<?> c2, JactlMethodHandle jmh, Object arg1, Object arg2) {
    return (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1))) &&
           (c2 == UNTYPED.class || (c2 == null ? arg2 == null : (arg2 != null && arg2.getClass() == c2)));
  }

  private static boolean sameJmhArg3(JactlMethodHandle expected, Class<?> c1, Class<?> c2, Class<?> c3, JactlMethodHandle jmh, Object arg1, Object arg2, Object arg3) {
    return (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1))) &&
           (c2 == UNTYPED.class || (c2 == null ? arg2 == null : (arg2 != null && arg2.getClass() == c2))) &&
           (c3 == UNTYPED.class || (c3 == null ? arg3 == null : (arg3 != null && arg3.getClass() == c3)));
  }

  private static boolean sameJmhArgN(JactlMethodHandle expected, Class<?>[] classes, JactlMethodHandle jmh, Object[] args) {
    if (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) {
      if (args.length != classes.length) {
        return false;
      }
      for (int i = 0; i < args.length; i++) {
        Class<?> clss = classes[i];
        Object   arg  = args[i];
        if (clss == UNTYPED.class ||
            (clss == null ? arg == null : (arg != null && arg.getClass() == clss))) {
          continue;
        }
        return false;
      }
      return true;
    }
    return false;
  }

  private static boolean sameClass(Class<?> expected, Object target) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject1(Class<?> expected, Object target, Object arg1) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject2(Class<?> expected, Object target, Object arg1, Object arg2) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject3(Class<?> expected, Object target, Object arg1, Object arg2, Object arg3) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObjectN(Class<?> expected, Object target, Object[] args) {
    return expected == target.getClass();
  }

  private static boolean sameClassArg1(Class<?> expected, Class<?> c1, Object target, Object arg1) {
    return expected == target.getClass() &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1)));
  }
  
  private static boolean sameClassArg2(Class<?> expected, Class<?> c1, Class<?> c2, Object target, Object arg1, Object arg2) {
    return expected == target.getClass() &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1))) &&
           (c2 == UNTYPED.class || (c2 == null ? arg2 == null : (arg2 != null && arg2.getClass() == c2)));
  }

  private static boolean sameClassArg3(Class<?> expected, Class<?> c1, Class<?> c2, Class<?> c3, Object target, Object arg1, Object arg2, Object arg3) {
    return expected == target.getClass() &&
           (c1 == UNTYPED.class || (c1 == null ? arg1 == null : (arg1 != null && arg1.getClass() == c1))) &&
           (c2 == UNTYPED.class || (c2 == null ? arg2 == null : (arg2 != null && arg2.getClass() == c2))) &&
           (c3 == UNTYPED.class || (c3 == null ? arg3 == null : (arg3 != null && arg3.getClass() == c3)));
  }

  private static boolean sameClassArgN(Class<?> expected, Class<?>[] classes, Object target, Object[] args) {
    if (expected == target.getClass()) {
      if (args.length != classes.length) {
        return false;
      }
      for (int i = 0; i < args.length; i++) {
        Class<?> clss = classes[i];
        Object   arg  = args[i];
        if (clss == UNTYPED.class ||
            (clss == null ? arg == null : (arg != null && arg.getClass() == clss))) {
          continue;
        }
        return false;
      }
      return true;
    }
    return false;
  }

  private static class UNTYPED {}
}
