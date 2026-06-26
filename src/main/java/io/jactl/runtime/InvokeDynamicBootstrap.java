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

/**
 * Bootstrap for InvokeDynamic sites
 */
public class InvokeDynamicBootstrap {

  private static final MethodHandle ADAPTER;                  // Adapter for invoking MethodHandle
  private static final MethodHandle IS_SAME;                  // Checks the MethodHandle is still the same one
  private static final MethodHandle GET_BOUND_AT_LEVEL;       // Extracts bound object at a given nesting depth
  private static final MethodHandle INVOKE_WRAPPER_HANDLE;    // Invokes wrapper method handle as fallback

  private static final MethodHandle METHOD_OR_FIELD_ADAPTER;     // Adapter for invoking a given method/field
  private static final MethodHandle IS_SAME_CLASS;               // Check classes are still the same
  private static final MethodHandle METHOD_OR_FIELD_INVOKER;     // Invokes wrapper as fallback
  private static final MethodHandle SAME_CLASSES;                // Checks if arg types are still the same
  
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
  private static final MethodHandle INT_VALUE;
  private static final MethodHandle LONG_VALUE;
  private static final MethodHandle DOUBLE_VALUE;
  private static final MethodHandle DECIMAL_VALUE;
  private static final MethodHandle IS_TRUTH;
  
  private static final MethodType   GET_CLASS_TYPE = MethodType.methodType(Class.class);
  
  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      ADAPTER                 = lookup.findStatic(InvokeDynamicBootstrap.class, "adapter", MethodType.methodType(Object.class, MaxDepthCallSite.class, Object[].class));
      IS_SAME                 = lookup.findStatic(InvokeDynamicBootstrap.class, "isSame", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class));
      GET_BOUND_AT_LEVEL      = lookup.findStatic(InvokeDynamicBootstrap.class, "getBoundAtLevel", MethodType.methodType(Object.class, int.class, JactlMethodHandle.class));
      INVOKE_WRAPPER_HANDLE   = lookup.findStatic(InvokeDynamicBootstrap.class, "invokeWrapper", MethodType.methodType(Object.class, JactlMethodHandle.class, Continuation.class, String.class, int.class, Object[].class));
       
      METHOD_OR_FIELD_ADAPTER = lookup.findStatic(InvokeDynamicBootstrap.class, "methodOrFieldAdapter", MethodType.methodType(Object.class, MaxDepthCallSite.class, MethodHandles.Lookup.class, String.class, Object[].class));
      IS_SAME_CLASS           = lookup.findStatic(InvokeDynamicBootstrap.class, "isSameClass", MethodType.methodType(boolean.class, Class.class, Class.class));
      METHOD_OR_FIELD_INVOKER = lookup.findStatic(InvokeDynamicBootstrap.class, "invokeMethodOrField", MethodType.methodType(Object.class, String.class, Object.class, Continuation.class, String.class, int.class, Object[].class));
      SAME_CLASSES            = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClasses", MethodType.methodType(boolean.class, Class[].class, Object[].class));
      SAME_JMH                = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmh", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, int.class));
      SAME_JMH_OBJECT1        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject1", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class));
      SAME_JMH_OBJECT2        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject2", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class, Object.class));
      SAME_JMH_OBJECT3        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObject3", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class, Object.class, Object.class));
      SAME_JMH_OBJECTN        = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhObjectN", MethodType.methodType(boolean.class, JactlMethodHandle.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object[].class));
      SAME_JMH_ARG1           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg1", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class));
      SAME_JMH_ARG2           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg2", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, Class.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class, Object.class));
      SAME_JMH_ARG3           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArg3", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class.class, Class.class, Class.class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object.class, Object.class, Object.class));
      SAME_JMH_ARGN           = lookup.findStatic(InvokeDynamicBootstrap.class, "sameJmhArgN", MethodType.methodType(boolean.class, JactlMethodHandle.class, Class[].class, JactlMethodHandle.class, Object.class, Object.class, int.class, Object[].class));
      SAME_CLASS              = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClass", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, int.class));
      SAME_CLASS_OBJECT1      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject1", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class));
      SAME_CLASS_OBJECT2      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject2", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class, Object.class));
      SAME_CLASS_OBJECT3      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObject3", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class, Object.class, Object.class));
      SAME_CLASS_OBJECTN      = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassObjectN", MethodType.methodType(boolean.class, Class.class, Object.class, Object.class, Object.class, int.class, Object[].class));
      SAME_CLASS_ARG1         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg1", MethodType.methodType(boolean.class, Class.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class));
      SAME_CLASS_ARG2         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg2", MethodType.methodType(boolean.class, Class.class, Class.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class, Object.class));
      SAME_CLASS_ARG3         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArg3", MethodType.methodType(boolean.class, Class.class, Class.class, Class.class, Class.class, Object.class, Object.class, Object.class, int.class, Object.class, Object.class, Object.class));
      SAME_CLASS_ARGN         = lookup.findStatic(InvokeDynamicBootstrap.class, "sameClassArgN", MethodType.methodType(boolean.class, Class.class, Class[].class, Object.class, Object.class, Object.class, int.class, Object[].class));
      
      BYTE_VALUE              = lookup.findVirtual(Number.class, "byteValue", MethodType.methodType(byte.class));
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
   * @return the new call site
   */
  public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType siteType) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeAdapter(siteType, cs));
    return cs;
  }

  private static MethodHandle makeAdapter(MethodType siteType, MutableCallSite cs) {
    return ADAPTER.bindTo(cs)
                  .asCollector(Object[].class, siteType.parameterCount())
                  .asType(siteType);
  }

  /**
   * Used for calls to x.y() where y may be a method or a field of the object/map
   * On stack: Object obj, String src, int offset, arg1, arg2, ...
   * @param lookup         the lookup object
   * @param methodOrField  the name of the field/method
   * @param siteType       the call site argument types
   * @return the call site
   */
  public static CallSite bootstrapMethodOrField(MethodHandles.Lookup lookup, String methodOrField, MethodType siteType) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeMethodOrFieldAdapter(siteType, cs, methodOrField, lookup));
    return cs;
  }
  
  public static CallSite boostrapClosure(MethodHandles.Lookup lookup, String name, MethodType siteType) {
    MaxDepthCallSite cs = new MaxDepthCallSite(siteType);
    cs.setTarget(makeAdapter(siteType, cs));
    return cs;
  }

  private static MethodHandle makeMethodOrFieldAdapter(MethodType siteType, MaxDepthCallSite cs, String methodOrFieldName, MethodHandles.Lookup lookup) {
    return METHOD_OR_FIELD_ADAPTER.bindTo(cs)
                                  .bindTo(lookup)
                                  .bindTo(methodOrFieldName)
                                  .asCollector(Object[].class, siteType.parameterCount())
                                  .asType(siteType);
  }
  
  private static MethodHandle makeMethodOrFieldInvoker(MethodType siteType, String methodOrField) {
    int extraArgCount = siteType.parameterCount() - 4;  // number of args after Object, Continuation, String, int
    return METHOD_OR_FIELD_INVOKER.bindTo(methodOrField)
                                  .asCollector(Object[].class, extraArgCount)
                                  .asType(siteType);
  }

  // Builds a MethodHandle for invoking wrapper method handle
  // It calls jmh.invoke(c, source, offset, args) where the args are passed as Object[].
  private static MethodHandle makeWrapperInvoker(MethodType siteType) {
    int extraArgCount = siteType.parameterCount() - 4;  // number of args after JMH, Continuation, String, int
    return INVOKE_WRAPPER_HANDLE.asCollector(Object[].class, extraArgCount)
                                .asType(siteType);
  }

  // args: args[0] = JMH, args[1] = Continuation, args[2] = source, args[3] = offset, args[4..] = typed call args.
  public static Object adapter(MaxDepthCallSite cs, Object[] args) throws Throwable {
    JactlMethodHandle jmh      = (JactlMethodHandle) args[0];
    if (jmh == null) {
      throw new RuntimeError("Cannot invoke null closure", (String)args[2], (int)args[3]);
    }
    MethodType        siteType = cs.type();
    
    Object[] actualArgs = Arrays.copyOfRange(args, 4, args.length);

    // Guard against chain depth that becomes unwieldy
    cs.depth++;
    if (cs.depth > MaxDepthCallSite.MAX_DEPTH) {
      cs.setTarget(makeWrapperInvoker(siteType));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return jmh.invoke((Continuation)args[1], (String)args[2], (int)args[3], actualArgs);
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
    // adapted.type() = (Object x N, [Continuation?], [String, int?], params...) -> Object

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
    // adapted.type() = (JMH x N, [Continuation?], [String, int]?, params...) -> Object

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
      // adapted.type() = (JMH, [Continuation?], [String, int?], params...) -> Object
    }
    
    if (boundArgCount == 0) {
      // Not a BoundHandle: prepend a dropped JMH arg so siteType's arg[0] is consumed.
      adapted = MethodHandles.dropArguments(adapted, 0, JactlMethodHandle.class);
    }

    // Both paths produce: adapted.type() = (JMH, <remaining underlying params>) -> Object
    // Insert drops for args the siteType always provides but the underlying doesn't need.
    if (!jmh.isAsync()) {
      adapted = MethodHandles.dropArguments(adapted, 1, Continuation.class);
    }
    if (!jmh.needsLocation()) {
      adapted = MethodHandles.dropArguments(adapted, 2, String.class, int.class);
    }
    // adapted.type() should now be (JMH, Continuation, String, int, params...) -> Object


    // We need to build a guard that tests for changes of the JactlMethodHandle and changes of argument types
    // so that we can install a new call site in case we need different coercions applied. We optimise for
    // parameters that are of type Object.class since no coercion is done so argument type changing has no effect.
    MethodHandle guard = createGuard(jmh, siteType, adapted.type(), args);

    try {
      // Check that args are compatible
      adapted = coerceArgumentsOrFail(siteType, adapted, args, 4);
      adapted = adapted.asType(siteType);
    }
    catch (WrongMethodTypeException e) {
      // Type coercion failed (e.g. param count mismatch). Install a stable wrapper-path guard.
      cs.setTarget(MethodHandles.guardWithTest(guard, makeWrapperInvoker(siteType), cs.getTarget()));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return jmh.invoke((Continuation)args[1], (String)args[2], (int)args[3], actualArgs);
    }

    // Install the direct-call guard.
    cs.setTarget(MethodHandles.guardWithTest(guard, adapted, cs.getTarget()));
    MutableCallSite.syncAll(new MutableCallSite[]{cs});

    // For the first call, use the wrapper rather than invokeWithArguments(args).
    // invokeWithArguments boxes everything to Object[] then converts back.
    // The cached direct-call chain handles future calls at the JVM bytecode level.
    return jmh.invoke((Continuation)args[1], (String)args[2], (int)args[3], actualArgs);
  }

  // args: args[0] = obj, args[1] = Continuation, args[2] = source, args[3] = offset, args[4..] = typed call args.
  public static Object methodOrFieldAdapter(MaxDepthCallSite cs, MethodHandles.Lookup lookup, String methodOrField, Object[] args) throws Throwable {
    Object     parent   = args[0];
    String     source   = (String) args[2];
    int        offset   = (int) args[3];
    MethodType siteType = cs.type();

    Object[] actualArgs = Arrays.copyOfRange(args, 4, args.length);

    // Guard against chain depth that becomes unwieldy
    cs.depth++;
    if (cs.depth > MaxDepthCallSite.MAX_DEPTH) {
      cs.setTarget(makeMethodOrFieldInvoker(siteType, methodOrField));
      MutableCallSite.syncAll(new MutableCallSite[]{cs});
      return invokeMethodOrField(methodOrField, parent, null, source, offset, actualArgs);
    }

    JactlContext jactlContext = RuntimeState.getState().getContext();

    MethodHandle getClassMH  = lookup.findVirtual(Object.class, "getClass", GET_CLASS_TYPE);
    MethodHandle isSameClass = MethodHandles.filterArguments(IS_SAME_CLASS.bindTo(parent.getClass()), 0, getClassMH);
    isSameClass = MethodHandles.dropArguments(isSameClass, 1, siteType.parameterList().subList(1, siteType.parameterCount()));

    // If we have a JactlObject then we can look up method/field
    MethodHandle fallback = cs.getTarget();
    if (parent instanceof JactlObject) {
      Object            fieldOrMethod = ((JactlObject) parent)._$j$getFieldsAndMethods().get(methodOrField);
      JactlMethodHandle jmh;
      if (fieldOrMethod instanceof JactlMethodHandle) {
        jmh = (JactlMethodHandle) fieldOrMethod;
        // Now find the actual method
        Method method = Arrays.stream(parent.getClass().getMethods())
                              .filter(m -> m.getName().equals(methodOrField))
                              .findFirst()
                              .orElseThrow(() -> new NoSuchMethodException("Internal error: could not find '" + methodOrField + "' in class '" + parent.getClass().getName() + "'"));

        MethodHandle adapted = lookup.unreflect(method);

        // Ignore Continuation and/or source/offset if not needed.
        // NOTE: confusingly, dropArguments actually inserts ignored arguments to make
        // the call site and the method types reconcile so this needs to be done BEFORE
        // the call to adapted.asType()
        if (!jmh.isAsync()) {
          adapted = MethodHandles.dropArguments(adapted, 1, Continuation.class);
        }
        if (!jmh.needsLocation()) {
          adapted = MethodHandles.dropArguments(adapted, 2, String.class, int.class);
        }

        MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);
        
        // Install call site that checks that the parent class is still the same and that the argument
        // types haven't changed and then invokes the method. If there are conversion errors then we catch
        // the exception and install the wrapper fallback instead.
        try {
          adapted = coerceArgumentsOrFail(siteType, adapted, args, 4);          
          adapted = adapted.asType(siteType);
          cs.setTarget(MethodHandles.guardWithTest(guard, adapted, fallback));
        }
        catch (WrongMethodTypeException e) {
          // Argument types not directly compatible so fall back to invoking wrapper to let it do any
          // coercion required and fill in missing arguments etc (or throw an error if needed).
          cs.setTarget(MethodHandles.guardWithTest(guard, makeMethodOrFieldInvoker(siteType, methodOrField), fallback));
        }
      }
      else {
        // We have a field so we install something that checks parent class and invokes slow path through
        // invokeMethodOrField() which loads field to get JactlMethodHandle and invokes it.
        cs.setTarget(MethodHandles.guardWithTest(isSameClass, makeMethodOrFieldInvoker(siteType, methodOrField), fallback));

      }
      MutableCallSite.syncAll(new MutableCallSite[]{cs});

      return invokeMethodOrField(methodOrField, parent, null, source, offset, actualArgs);
    }

    // See if there is a method of this name
    FunctionDescriptor func = jactlContext.getFunctions().lookupFunction(parent, methodOrField);

    // If no method then check for a field and invoke method handle contained in the field
    if (func == null) {
      // If we have a map then we might have a field
      if (parent instanceof Map) {
        cs.setTarget(MethodHandles.guardWithTest(isSameClass, makeMethodOrFieldInvoker(siteType, methodOrField), fallback));
      }
      else {
        // Check for host class
        JactlMethodHandle jmh = null;
        if (jactlContext.allowHostAccess) {
          jmh = jactlContext.lookupWrapperForHostClass(parent, methodOrField, actualArgs, source, offset);
        }
        if (jmh == null) {
          // No method for this class so install something that throws the appropriate RuntimeError.
          // If a different class comes through the guard will fail and we will try again.
          RuntimeError error = new RuntimeError("No such method '" + methodOrField + "' for class " + parent.getClass().getName(), source, offset);
          MethodHandle throwErr        = MethodHandles.throwException(Object.class, RuntimeError.class);
          MethodHandle throwErrWithArg = MethodHandles.insertArguments(throwErr, 0, error);
          MethodHandle thrower         = MethodHandles.dropArguments(throwErrWithArg, 0, siteType.parameterList());
          cs.setTarget(MethodHandles.guardWithTest(isSameClass, thrower, fallback));
        }
        else {
          jmh = jmh.getInnerHandle();     // Get unbound handle
          // Drop the Continuation and source/offset since these won't be used by a host class method
          int          pos     = jmh.isBoundHandle() ? 1 : 0;
          MethodHandle adapted = jmh.handleToUnderlyingFunction();
          if (!jmh.isBoundHandle()) {
            // Ignore the object since we have a static method
            adapted = MethodHandles.dropArguments(adapted, pos++, Object.class);
          }
          adapted = MethodHandles.dropArguments(adapted, pos, Continuation.class, String.class, int.class);
          pos += 3;

          MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);
          
          try {
            adapted = coerceArgumentsOrFail(siteType, adapted, args, pos);
            adapted = adapted.asType(siteType);
            cs.setTarget(MethodHandles.guardWithTest(guard, adapted, fallback));
          }
          catch (WrongMethodTypeException e) {
            // Argument types not directly compatible so fall back to slow path to let it do any coercion required
            cs.setTarget(MethodHandles.guardWithTest(guard, makeMethodOrFieldInvoker(siteType, methodOrField), fallback));
          }
        }
      }
      MutableCallSite.syncAll(new MutableCallSite[]{cs});

      // First time we still invoke the wrapper after installing call site
      return invokeMethodOrField(methodOrField, parent, null, source, offset, actualArgs);
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
      if (func.isStaticMethod) {
        adapted = MethodHandles.dropArguments(adapted, 0, Object.class);
      }
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException("Internal error: could not find class '" + className + "'", e);
    }

    // Adjust arguments based on whether function is async or needs location passed to it
    if (!func.isAsync()) {
      adapted = MethodHandles.dropArguments(adapted, 1, Continuation.class);
    }
    if (!func.needsLocation) {
      adapted = MethodHandles.dropArguments(adapted, 2, String.class, int.class);
    }

    int adjustedParamCount = func.paramCount + 4;
    int argStart = 4;
    
    // To start with we assume we can invoke directly if we don't have too many arguments
    boolean directInvoke = args.length <= adjustedParamCount;
    
    // We need to insert default values for missing arguments (if they are simple values)
    if (args.length < adjustedParamCount) {
      for (int i = args.length - argStart; i < func.defaultVals.length; i++) {
        if (func.defaultVals[i] == FunctionDescriptor.NON_TRIVIAL) {
          directInvoke = false;
          break;
        }
      }
      if (directInvoke) {
        // Insert missing values after the parent object and the other args
        adapted = MethodHandles.insertArguments(adapted, args.length, Arrays.copyOfRange(func.defaultVals, args.length - argStart, func.defaultVals.length));
      }
    }

    MethodHandle guard = createGuardForMethod(args[0].getClass(), siteType, adapted.type(), args);

    try {
      // Insert argument coercion for numeric types or if incompatible types
      // we will throw WrongMethodTypeException and fall back to invoking wrapper
      adapted = coerceArgumentsOrFail(siteType, adapted, args, argStart);
      adapted = adapted.asType(siteType);
    }
    catch (WrongMethodTypeException e) {
      directInvoke = false;
    }
    
    adapted = MethodHandles.guardWithTest(guard, directInvoke ? adapted : makeMethodOrFieldInvoker(siteType, methodOrField), fallback);
    cs.setTarget(adapted);
    MutableCallSite.syncAll(new MutableCallSite[]{cs});

    // First time we still invoke the wrapper after installing call site
    return invokeMethodOrField(methodOrField, parent, null, source, offset, actualArgs);
  }

  private static final HashMap<Class<?>, MethodHandle> NUMERIC_FILTERS = new HashMap<>();
  private static final HashMap<Class<?>, Integer>      NUMERIC_RANK    = new HashMap<>();
  static {
    NUMERIC_FILTERS.put(byte.class,       BYTE_VALUE);
    NUMERIC_FILTERS.put(int.class,        INT_VALUE);
    NUMERIC_FILTERS.put(long.class,       LONG_VALUE);
    NUMERIC_FILTERS.put(double.class,     DOUBLE_VALUE);
    NUMERIC_FILTERS.put(BigDecimal.class, DECIMAL_VALUE);

    // Special case for BigDecimal as we need can't rely on automatic coercion at all 
    NUMERIC_RANK.put(BigDecimal.class, -1);
    
    NUMERIC_RANK.put(Byte.class,       1);
    NUMERIC_RANK.put(Integer.class,    2);
    NUMERIC_RANK.put(Long.class,       3);
    NUMERIC_RANK.put(Double.class,     4);
  }
  
  // NOTE: adapted is the MethodHandle for the actual method with potentially some default values inserted
  private static MethodHandle coerceArgumentsOrFail(MethodType siteType, MethodHandle adapted, Object[] args, int argStart) {
    MethodType adaptedType = adapted.type();
    if (args.length > adaptedType.parameterCount()) {
      throw new WrongMethodTypeException();
    }
    for (int i = argStart; i < args.length; i++) {
      Object   arg       = args[i];
      Class<?> argType   = boxedType(arg == null ? siteType.parameterType(i) : arg.getClass());
      Class<?> paramType = boxedType(adaptedType.parameterType(i));
      if (argType == paramType || paramType.isAssignableFrom(argType)) {
        continue;
      }
      if (Number.class.isAssignableFrom(paramType)) {
        if (Number.class.isAssignableFrom(argType)) {
          // If widening then no need to do anything since asType() takes care of this
          Integer prank = NUMERIC_RANK.get(paramType);
          Integer arank = NUMERIC_RANK.get(argType);
          if (prank == null || arank == null) {
            throw new WrongMethodTypeException();
          }
          if (prank > 0 && arank > 0 && prank > arank) {
            continue;
          }
          MethodHandle filter = NUMERIC_FILTERS.get(paramType);
          if (filter == null) {
            // This shouldn't happen but just in case...
            throw new WrongMethodTypeException();
          }
          adapted = MethodHandles.filterArguments(adapted, i, filter); 
        }
        else {
          // We don't have a compatible conversion
          throw new WrongMethodTypeException();
        }
      }
      else if (paramType == boolean.class) {
        adapted = MethodHandles.filterArguments(adapted, i, IS_TRUTH);
      }
      else {
        // Arg type is not compatible or we don't have a coercion we can do
        // here so use wrapper function
        throw new WrongMethodTypeException();
      }
    }
    return adapted;
  }
  
  // Guard test: true when the incoming JMH refers to the same underlying function as the cached one.
  // For BoundHandle chains, handleToUnderlyingFunction() returns the stable singleton implementing
  // method handle, so this correctly matches any instance of the same closure/method regardless of
  // which specific bound objects (instance, HeapLocals) it carries.
  public static boolean isSame(JactlMethodHandle cached, JactlMethodHandle incoming) {
    return cached == incoming || cached.handleToUnderlyingFunction() == incoming.handleToUnderlyingFunction();
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
  public static Object invokeMethodOrField(String methodOrField, Object parent, Continuation c, String source, int offset, Object[] args) {
    return RuntimeUtils.invokeMethodOrField(parent, methodOrField, false, false, args, true, source, offset); 
  }
  
  public static boolean isSameClass(Class<?> clss1, Class<?> clss2) {
    return clss1 == clss2;
  }

  /**
   * Check that the argument types match the expected classes.
   * Note that we also pass in the parent/target object in position 0 so this also makes sure
   * that the object type on whom we are invoking the method hasn't changed. 
   * @param expected array of expected classes
   * @param args     the actual arguments
   * @return true if the classes match
   */
  public static boolean sameClasses(Class<?>[] expected, Object[] args) {
    if (expected.length != args.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      Object arg = args[i];
      if (arg == null && expected[i] == null) {
        continue;
      }
      if (arg.getClass() != expected[i]) {
        return false;
      }
    }
    return true;
  }
  
  public static class MaxDepthCallSite extends MutableCallSite {
    int depth = 0;
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
    // NOTE: we start from argument position 4 to skip the always present JMH, Continuation, String, int
    boolean allObjectParams = true;
    for (int idx = 4; idx < type.parameterCount(); idx++) {
      if (type.parameterType(idx) != Object.class) {
        allObjectParams = false;
        break;
      }
    }
    
    Class[] argTypes = Arrays.stream(Arrays.copyOfRange(args, 4, args.length)).map(a -> a == null ? null : a.getClass()).toArray(Class[]::new);

    if (allObjectParams) {
      switch (args.length - 4) {
        case 0:  return SAME_JMH.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_JMH_OBJECT1.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_JMH_OBJECT2.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_JMH_OBJECT3.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_JMH_OBJECTN.bindTo(jmh).asCollector(Object[].class, args.length - 4).asType(siteType.changeReturnType(boolean.class));
      }
    }
    else {
      switch (args.length - 4) {
        case 0:  return SAME_JMH.bindTo(jmh).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_JMH_ARG1.bindTo(jmh).bindTo(argTypes[0]).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_JMH_ARG2.bindTo(jmh).bindTo(argTypes[0]).bindTo(argTypes[1]).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_JMH_ARG3.bindTo(jmh).bindTo(argTypes[0]).bindTo(argTypes[1]).bindTo(argTypes[2]).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_JMH_ARGN.bindTo(jmh).bindTo(argTypes).asCollector(Object[].class, args.length - 4).asType(siteType.changeReturnType(boolean.class));
      }
    }
  }

  private static MethodHandle createGuardForMethod(Class<?> expected, MethodType siteType, MethodType type, Object[] args) {
    // Create a guard that checks if arg0 matches the expected target class and checks if the argument types
    // have changed.
    // We have a special case if all parameters are of type Object since no coercion is required and args
    // will always be passed in irrespective of their type.
    // NOTE: we start from argument position 4 to skip the always present Object target, Continuation, String, int
    boolean allObjectParams = true;
    for (int idx = 4; idx < type.parameterCount(); idx++) {
      if (type.parameterType(idx) != Object.class) {
        allObjectParams = false;
        break;
      }
    }
    
    Class[] argTypes = Arrays.stream(Arrays.copyOfRange(args, 4, args.length)).map(a -> a == null ? null : a.getClass()).toArray(Class[]::new);

    if (allObjectParams) {
      switch (args.length - 4) {
        case 0:  return SAME_CLASS.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_CLASS_OBJECT1.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_CLASS_OBJECT2.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_CLASS_OBJECT3.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_CLASS_OBJECTN.bindTo(args[0].getClass()).asCollector(Object[].class, args.length - 4).asType(siteType.changeReturnType(boolean.class));
      }
    }
    else {
      switch (args.length - 4) {
        case 0:  return SAME_CLASS.bindTo(args[0].getClass()).asType(siteType.changeReturnType(boolean.class));
        case 1:  return SAME_CLASS_ARG1.bindTo(args[0].getClass()).bindTo(argTypes[0]).asType(siteType.changeReturnType(boolean.class));
        case 2:  return SAME_CLASS_ARG2.bindTo(args[0].getClass()).bindTo(argTypes[0]).bindTo(argTypes[1]).asType(siteType.changeReturnType(boolean.class));
        case 3:  return SAME_CLASS_ARG3.bindTo(args[0].getClass()).bindTo(argTypes[0]).bindTo(argTypes[1]).bindTo(argTypes[2]).asType(siteType.changeReturnType(boolean.class));
        default: return SAME_CLASS_ARGN.bindTo(args[0].getClass()).bindTo(argTypes).asCollector(Object[].class, args.length - 4).asType(siteType.changeReturnType(boolean.class));
      }
    }
  }
  
  private static boolean sameJmh(JactlMethodHandle expected, JactlMethodHandle jmh, Object continuation, Object source, int offset) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject1(JactlMethodHandle expected, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject2(JactlMethodHandle expected, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4, Object arg5) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObject3(JactlMethodHandle expected, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4,  Object arg5, Object arg6) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }
  
  private static boolean sameJmhObjectN(JactlMethodHandle expected, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object[] args) {
    return expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction(); 
  }

  private static boolean sameJmhArg1(JactlMethodHandle expected, Class<?> c4, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4) {
    return ((expected == jmh) || (expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction())) &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4));
  }
  
  private static boolean sameJmhArg2(JactlMethodHandle expected, Class<?> c4, Class<?> c5, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4, Object arg5) {
    return (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4)) &&
           (c5 == null ? arg5 == null : (arg5 != null && arg5.getClass() == c5));
  }

  private static boolean sameJmhArg3(JactlMethodHandle expected, Class<?> c4, Class<?> c5, Class<?> c6, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object arg4, Object arg5, Object arg6) {
    return (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4)) &&
           (c5 == null ? arg5 == null : (arg5 != null && arg5.getClass() == c5)) &&
           (c6 == null ? arg6 == null : (arg6 != null && arg6.getClass() == c6));
  }

  private static boolean sameJmhArgN(JactlMethodHandle expected, Class<?>[] classes, JactlMethodHandle jmh, Object continuation, Object source, int offset, Object[] args) {
    if (expected == jmh || expected.handleToUnderlyingFunction() == jmh.handleToUnderlyingFunction()) {
      if (args.length != classes.length) {
        return false;
      }
      for (int i = 0; i < args.length; i++) {
        if (classes[i] == null ? args[i] == null : (args[i] != null && args[i].getClass() == classes[i])) {
          continue;
        }
        return false;
      }
      return true;
    }
    return false;
  }

  private static boolean sameClass(Class<?> expected, Object target, Object continuation, Object source, int offset) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject1(Class<?> expected, Object target, Object continuation, Object source, int offset, Object arg4) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject2(Class<?> expected, Object target, Object continuation, Object source, int offset, Object arg4, Object arg5) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObject3(Class<?> expected, Object target, Object continuation, Object source, int offset, Object arg4,  Object arg5, Object arg6) {
    return expected == target.getClass();
  }
  
  private static boolean sameClassObjectN(Class<?> expected, Object target, Object continuation, Object source, int offset, Object[] args) {
    return expected == target.getClass();
  }

  private static boolean sameClassArg1(Class<?> expected, Class<?> c4, Object target, Object continuation, Object source, int offset, Object arg4) {
    return expected == target.getClass() &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4));
  }
  
  private static boolean sameClassArg2(Class<?> expected, Class<?> c4, Class<?> c5, Object target, Object continuation, Object source, int offset, Object arg4, Object arg5) {
    return expected == target.getClass() &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4)) &&
           (c5 == null ? arg5 == null : (arg5 != null && arg5.getClass() == c5));
  }

  private static boolean sameClassArg3(Class<?> expected, Class<?> c4, Class<?> c5, Class<?> c6, Object target, Object continuation, Object source, int offset, Object arg4, Object arg5, Object arg6) {
    return expected == target.getClass() &&
           (c4 == null ? arg4 == null : (arg4 != null && arg4.getClass() == c4)) &&
           (c5 == null ? arg5 == null : (arg5 != null && arg5.getClass() == c5)) &&
           (c6 == null ? arg6 == null : (arg6 != null && arg6.getClass() == c6));
  }

  private static boolean sameClassArgN(Class<?> expected, Class<?>[] classes, Object target, Object continuation, Object source, int offset, Object[] args) {
    if (expected == target.getClass()) {
      if (args.length != classes.length) {
        return false;
      }
      for (int i = 0; i < args.length; i++) {
        if (classes[i] == null ? args[i] == null : (args[i] != null && args[i].getClass() == classes[i])) {
          continue;
        }
        return false;
      }
      return true;
    }
    return false;
  }
}
