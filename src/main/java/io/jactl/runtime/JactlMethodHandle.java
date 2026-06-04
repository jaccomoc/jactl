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

import io.jactl.JactlType;
import io.jactl.Utils;
import io.jactl.compiler.MethodRef;

import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.List;

import static io.jactl.JactlType.FUNCTION;

/**
 * JactlMethodHandle is a wrapper around the standard MethodHandle and also
 * has enough information to be able to save and restore our state and recreate
 * the MethodHandle from this saved state (possibly in another process).
 */
public abstract class JactlMethodHandle implements Checkpointable {
  private static int VERSION = 1;
  protected MethodHandle handle;

  public static final MethodRef BIND_TO_METHOD = Utils.getMethod(JactlMethodHandle.class, "bindTo", Object.class);
  public static final MethodRef INVOKE_METHOD  = Utils.getMethod(JactlMethodHandle.class, "invoke", Continuation.class, String.class, int.class, Object[].class);
  
  public static final String TYPE_DESCRIPTOR = Type.getDescriptor(JactlMethodHandle.class);
  
  enum HandleType {
    HANDLE,
    ITERATOR_HANDLE,
    BUILTIN_FUNCTION,
    BOUND,
    HOST_METHOD
  }

  public int parameterCount() {
    return handle.type().parameterCount();
  }
  
  public Class[] parameterTypes() {
    return handle.type().parameterArray();
  }

  public Object invoke(JactlObject obj, Continuation c, String source, int offset, Object[] args) throws Throwable {
    return handle.invoke(obj, c, source, offset, args);
  }

  public Object invoke(Continuation c) throws Throwable {
    return handle.invokeExact(c);
  }

  public Object invoke(Continuation c, String source, int offset, Object[] args) throws Throwable {
    return handle.invokeExact(c, source, offset, args);
  }

  public Object invokeWithArguments(Object... args) throws Throwable {
    return handle.invokeWithArguments(args);
  }

  public JactlMethodHandle bindTo(Object obj) {
    return new BoundHandle(this, obj);
  }
  
  public MethodHandle handleToUnderlyingFunction() {
    throw new UnsupportedOperationException();
  }
  
  public boolean   isAsync() {
    throw new UnsupportedOperationException();
  }
  public boolean   needsLocation() {
    throw new UnsupportedOperationException();
  }
  
  private static final boolean[] EMPTY_BOOLEAN_ARR = new boolean[0];
  public boolean[] heapLocalsPassedToFn() { return EMPTY_BOOLEAN_ARR; }

  public boolean            isBoundHandle()  { return false; }
  public Object             getBoundObject() { return null; }
  public JactlMethodHandle  getInnerHandle() { return this; }
  public void               populateBoundHandles(List<JactlMethodHandle> boundHandles) {}
  
  /**
   * Used when creating MethodHandles to continuation methods (for built-in functions)
   * @param handle        the handle to the method returned by MethodHandles.lookup()
   * @param handleClass   the class in which the static field holding the wrapper handle lives
   * @param handleName    the name of a static field in the handleClass that holds the wrapper handle value
   * @return the new Handle object
   */
  public static JactlMethodHandle create(MethodHandle handle, Class<?> handleClass, String handleName) {
    return new Handle(null, false, false, null, handle, handleClass, handleName);
  }

  /**
   * Used by ClassCompiler when creating wrapper handles for methods/functions.
   * @param methodHandle  handle to actual method
   * @param isAsync       true if method is async
   * @param needsLocation true if method needs location passed to it
   * @param heapLocalsPassedToFn  array of booleans indicating which heapLocals from wrapper are passed to function
   * @param wrapperHandle the wrapper handle
   * @param className     the class name (for the method and the location of the handle field)
   * @param handleName    the name of the static field holding the JactlMethodHandle
   * @return the JactlMethodHandle
   */
  public static JactlMethodHandle create(MethodHandle methodHandle, boolean isAsync, boolean needsLocation, boolean[] heapLocalsPassedToFn, MethodHandle wrapperHandle, Class<?> className, String handleName) {
    return new Handle(methodHandle, isAsync, needsLocation, heapLocalsPassedToFn, wrapperHandle, className, handleName);
  }

  /**
   * Return an IteratorHandle that points to the static method of a JactlIterator class.
   * @param handle      the MethodHandle for the method
   * @param type        the type of iterator
   * @param handleName  the static field of the class holding the value of the underlying MethodHandle
   * @return the IteratorHandle
   */
  public static JactlMethodHandle create(MethodHandle handle, JactlIterator.IteratorType type, String handleName) {
    return new IteratorHandle(handle, type, handleName);
  }

  /**
   * Create a FunctionWrapperHandle that points to the wrapper function for a built-in function.
   * @param handle   the MethodHandle pointing to the wrapper function
   * @param type     the Jactl type for which this is a built-in method or null if this is a global function 
   * @param name     the method/function name
   * @param function the FunctionDescriptor for the function/method
   * @return the FunctionWrapperHandle
   */
  public static FunctionWrapperHandle createFuncHandle(MethodHandle handle, JactlType type, String name, FunctionDescriptor function) {
    return new FunctionWrapperHandle(handle, type, name, function);
  }

  /**
   * When host access is enabled this returns a HostClassWrapperHandle that points to the wrapper()
   * method of HostClassMethodInvoker that has been bound to an instance of that class that knows
   * how to invoke the given host class method.
   * @param handle           a MethodHandle pointing to the host class method
   * @param wrapperHandle    a MethodHandle pointing to HostClassMethodInvoker.wrapper() bound to an instance of that type
   * @param isStatic         whether the host method is static or not
   * @return the HostClassWrapperHandle
   */
  public static HostClassWrapperHandle createHostClassHandle(MethodHandle handle, MethodHandle wrapperHandle, boolean isStatic) {
    return new HostClassWrapperHandle(handle, wrapperHandle, isStatic);
  }

  /////////////////////////////////////////////////////////////

  // Used by Restorer
  static JactlMethodHandle create(int type) {
    switch (HandleType.values()[type]) {
      case HANDLE:             return new Handle();
      case ITERATOR_HANDLE:    return new IteratorHandle();
      case BUILTIN_FUNCTION:   return new FunctionWrapperHandle();
      case BOUND:              return new BoundHandle();
      case HOST_METHOD:        throw new UnsupportedOperationException("Checkpointing not supported for host class methods");
    }
    return null;
  }

  private static class Handle extends JactlMethodHandle {
    private Class  className;
    private String handleName;
    private MethodHandle methodHandle;
    private boolean isAsync;
    private boolean   needsLocation;
    private boolean[] heapLocalsPassedToFn;
    Handle(){}
    public Handle(MethodHandle methodHandle, boolean isAsync, boolean needsLookup, boolean[] heapLocalsPassedToFn, MethodHandle wrapperHandle, Class className, String handleName) {
      this.handle       = wrapperHandle;
      this.className    = className;
      this.handleName   = handleName;
      this.methodHandle = methodHandle;
      this.isAsync        = isAsync;
      this.needsLocation = needsLookup;
      this.heapLocalsPassedToFn = heapLocalsPassedToFn;
    }

    @Override public MethodHandle handleToUnderlyingFunction() { return methodHandle; } 
    @Override public boolean isAsync() { return isAsync; }
    @Override public boolean needsLocation() { return needsLocation; }
    public boolean[] heapLocalsPassedToFn() {
      return heapLocalsPassedToFn;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) { return true; }
      if (obj instanceof Handle) {
        return this.methodHandle == ((Handle)obj).methodHandle;
      }
      return false;
    }

    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCInt(HandleType.HANDLE.ordinal());
      checkpointer.writeCInt(VERSION);

      // For JactlObject handles we write the class name but for built-in function classes
      // we write an id that has been registered with BuiltinFunctions.
      if (JactlObject.class.isAssignableFrom(className)) {
        checkpointer.writeBoolean(true);
        checkpointer.writeObject(Type.getInternalName(className));
      }
      else {
        checkpointer.writeBoolean(false);
        checkpointer.writeCInt(BuiltinFunctions.getClassId(className));
        checkpointer.writeCInt(className.getName().hashCode());
      }
      checkpointer.writeObject(handleName);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCInt(HandleType.HANDLE.ordinal(), "Expected HANDLE");
      restorer.expectCInt(VERSION, "Bad version");
      boolean isJactlClass = restorer.readBoolean();
      if (isJactlClass) {
        className = restorer.getJactlClass((String)restorer.readObject());
      }
      else {
        className = BuiltinFunctions.getClass(restorer.readCInt());
        restorer.expectCInt(className.getName().hashCode(), "Class name hash does not match for " + className.getName());
      }
      handleName = (String)restorer.readObject();
      try {
        Field handleField = className.getDeclaredField(handleName);
        Handle jactlMethodHandle = (Handle) handleField.get(null);
        handle = jactlMethodHandle.handle;
        methodHandle = jactlMethodHandle.methodHandle;
        isAsync = jactlMethodHandle.isAsync;
        needsLocation = jactlMethodHandle.needsLocation;
        heapLocalsPassedToFn = jactlMethodHandle.heapLocalsPassedToFn;
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        throw new IllegalStateException("Error accessing field " + handleName + " in class " + className.getName(), e);
      }
    }
  }

  private static class IteratorHandle extends JactlMethodHandle {
    private JactlIterator.IteratorType type;
    private String                     handleName;
    IteratorHandle(){}
    public IteratorHandle(MethodHandle handle, JactlIterator.IteratorType type, String handleName) {
      this.handle     = handle;
      this.type       = type;
      this.handleName = handleName;
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCInt(HandleType.ITERATOR_HANDLE.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeCInt(type.ordinal());
      checkpointer.writeObject(handleName);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) { return true; }
      if (obj instanceof IteratorHandle) {
        return this.handle == ((IteratorHandle)obj).handle;
      }
      return false;
    }

    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCInt(HandleType.ITERATOR_HANDLE.ordinal(), "Expected HANDLE");
      restorer.expectCInt(VERSION, "Bad version");
      type       = JactlIterator.IteratorType.values()[restorer.readCInt()];
      handleName = (String)restorer.readObject();
      Class handleClass = JactlIterator.classFromType(type);
      try {
        Field handleField = handleClass.getDeclaredField(handleName);
        handle = ((JactlMethodHandle)handleField.get(null)).handle;
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        throw new IllegalStateException("Error accessing field " + handleName + " in class " + handleClass.getName(), e);
      }
    }
  }

  // NOTE: These don't support checkpoint/restore because of the risk of someone being able to inject
  // a malicious class/method name at restoration time that then gets invoked.
  public static class HostClassWrapperHandle extends JactlMethodHandle {
    boolean isStatic;
    MethodHandle methodHandle;
    public HostClassWrapperHandle(MethodHandle handle, MethodHandle wrapperHandle, boolean isStatic) {
      this.handle       = wrapperHandle;
      this.methodHandle = handle;
      this.isStatic     = isStatic;
    }

    public MethodHandle handleToUnderlyingFunction() { return methodHandle; }
    public boolean isAsync() { return false; }
    public boolean needsLocation() { return false; }

    @Override
    public boolean equals(Object other) {
      if (other == this) return true;
      if (other instanceof HostClassWrapperHandle) {
        HostClassWrapperHandle hh = (HostClassWrapperHandle)other;
        return this.methodHandle.equals(hh.methodHandle);
      }
      return false;
    }

    @Override public JactlMethodHandle bindTo(Object obj) {
      return isStatic ? this : super.bindTo(obj);
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      throw new UnsupportedOperationException("Checkpointing not supported for host class method invocations");
    }
    @Override public void _$j$restore(Restorer restorer) {
      throw new UnsupportedOperationException("Checkpointing not supported for host class method invocations");
    }
  }  

  public static class FunctionWrapperHandle extends JactlMethodHandle {
    private JactlType type;    // Can be null for global functions
    private String    name;
    private FunctionDescriptor function;
    FunctionWrapperHandle(){}
    public FunctionWrapperHandle(MethodHandle handle, JactlType type, String name, FunctionDescriptor function) {
      this.handle = handle;
      this.type   = type;
      this.name   = name;
      this.function = function;
    }
    public FunctionDescriptor getFunction() { return function; }
    @Override public MethodHandle handleToUnderlyingFunction() { return function.getMethodHandle(); }
    @Override public boolean isAsync() { return function.isAsync(); }
    @Override public boolean needsLocation() { return function.needsLocation; }

    @Override
    public boolean equals(Object other) {
      if (other == this) return true;
      if (other instanceof FunctionWrapperHandle) {
        FunctionWrapperHandle fh = (FunctionWrapperHandle) other;
        return this.function == fh.function;
      }
      return false;
    }

    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCInt(HandleType.BUILTIN_FUNCTION.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeType(type);
      checkpointer.writeObject(name);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCInt(HandleType.BUILTIN_FUNCTION.ordinal(), "Expected BUILTIN_FUNCTION");
      restorer.expectCInt(VERSION, "Bad version");
      type = restorer.readType();
      name = (String)restorer.readObject();
      function = type == null ? restorer.getContext().getFunctions().lookupGlobalFunction(name) : restorer.getContext().getFunctions().lookupMethod(type, name);
      if (function == null) {
        function = type.getJactlClassDescriptor().getMethod(name);
      }
      if (function == null) {
        throw new IllegalStateException("Could not find function " + name + " for type " + type);
      }
      handle = function.wrapperHandle.handle;
    }
  }

  private static class BoundHandle extends JactlMethodHandle {
    private JactlMethodHandle wrappedHandle;
    private Object            boundObj;
    BoundHandle(){}
    public BoundHandle(JactlMethodHandle jmh, Object obj) {
      this.wrappedHandle = jmh;
      this.boundObj      = obj;
      this.handle        = wrappedHandle.handle.bindTo(boundObj);
    }

    @Override public MethodHandle      handleToUnderlyingFunction() { return wrappedHandle.handleToUnderlyingFunction(); }
    @Override public boolean[]         heapLocalsPassedToFn()       { return wrappedHandle.heapLocalsPassedToFn(); }
    @Override public boolean           isAsync()                    { return wrappedHandle.isAsync(); }
    @Override public boolean           needsLocation()              { return wrappedHandle.needsLocation(); }
    @Override public boolean           isBoundHandle()              { return true; }
    @Override public Object            getBoundObject()             { return boundObj; }
    @Override public JactlMethodHandle getInnerHandle()             { return wrappedHandle; }
    @Override public void              populateBoundHandles(List<JactlMethodHandle> boundHandles) {
      wrappedHandle.populateBoundHandles(boundHandles);
      boundHandles.add(this);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) return true;
      if (other instanceof BoundHandle) {
        BoundHandle bh = (BoundHandle)other;
        if (this.wrappedHandle == bh.wrappedHandle && this.boundObj == bh.boundObj && this.handle == bh.handle) return true;
        return this.wrappedHandle.equals(bh.wrappedHandle) && this.boundObj.equals(bh.boundObj) && this.handle.equals(bh.handle);
      }
      return false;
    }

    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCInt(HandleType.BOUND.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(wrappedHandle);
      checkpointer.writeObject(boundObj);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCInt(HandleType.BOUND.ordinal(), "Expected BOUND");
      restorer.expectCInt(VERSION, "Bad version");
      wrappedHandle = (JactlMethodHandle)restorer.readObject();
      boundObj      = restorer.readObject();
    }

    @Override public Object invoke(JactlObject obj, Continuation c, String source, int offset, Object[] args) throws Throwable {
      populateHandle();
      return handle.invoke(obj, c, source, offset, args);
    }

    @Override public Object invoke(Continuation c) throws Throwable {
      populateHandle();
      return handle.invokeExact(c);
    }

    @Override public Object invoke(Continuation c, String source, int offset, Object[] args) throws Throwable {
      populateHandle();
      return handle.invokeExact(c, source, offset, args);
    }

    @Override public Object invokeWithArguments(Object... args) throws Throwable {
      populateHandle();
      return handle.invokeWithArguments(args);
    }

    @Override public int parameterCount() {
      populateHandle();
      return handle.type().parameterCount();
    }

    private void populateHandle() {
      if (handle == null) {
        if (wrappedHandle instanceof BoundHandle) {
          ((BoundHandle) wrappedHandle).populateHandle();
        }
        handle = wrappedHandle.handle.bindTo(boundObj);
      }
    }
  }
}
