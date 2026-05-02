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
import java.lang.reflect.Method;

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
    if (handle == null) { populateHandle(); }
    return handle.type().parameterCount();
  }

  public Object invoke(JactlObject obj, Continuation c, String source, int offset, Object[] args) throws Throwable {
    if (handle == null) { populateHandle(); }
    return handle.invoke(obj, c, source, offset, args);
  }

  public Object invoke(Continuation c) throws Throwable {
    if (handle == null) { populateHandle(); }
    return handle.invokeExact(c);
  }

  public Object invoke(Continuation c, String source, int offset, Object[] args) throws Throwable {
    if (handle == null) { populateHandle(); }
    return handle.invokeExact(c, source, offset, args);
  }

  public Object invokeWithArguments(Object... args) throws Throwable {
    if (handle == null) { populateHandle(); }
    return handle.invokeWithArguments(args);
  }

  public JactlMethodHandle bindTo(Object obj) {
    return new BoundHandle(this, obj);
  }

  /**
   * Create a Handle that wraps a MethodHandle pointing to a given method of a class.
   * Used for built-in functions and host class methods.
   * For built-in functions the handleName is the name of a field in the handleClass
   * that holds the value of handle. This is used for recovery. We store the handle
   * name in the checkpoint and use this to then lookup the handle during restore.
   * Note: not sure why we don't just do another MethodHandles.lookup at that point
   * since both mechanisms need reflection to work.
   * @param handle       the handle returned by MethodHandles.lookup() 
   * @param handleClass  the class in which the method lives
   * @param handleName   the name of a static field in the handleClass that holds the handle value
   * @return the new Handle object
   */
  public static JactlMethodHandle create(MethodHandle handle, Class handleClass, String handleName) {
    return new Handle(handle, handleClass, handleName);
  }

  /**
   * Return an InteratorHandle that points to the static method of a JactlIterator class.
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
   * @param handle    a MethodHandle pointing to HostClassMethodInvoker.wrapper() bound to an instance of that type
   * @param isStatic  whether the host method is static or not
   * @return the HostClassWrapperHandle
   */
  public static HostClassWrapperHandle createHostClassHandle(MethodHandle handle, boolean isStatic) {
    return new HostClassWrapperHandle(handle, isStatic);
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

  protected void populateHandle() {
  }

  private static class Handle extends JactlMethodHandle {
    private Class        handleClass;
    private String       handleName;
    Handle(){}
    public Handle(MethodHandle handle, Class handleClass, String handleName) {
      this.handle      = handle;
      this.handleClass = handleClass;
      this.handleName  = handleName;
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCInt(HandleType.HANDLE.ordinal());
      checkpointer.writeCInt(VERSION);

      // For JactlObject handles we write the class name but for built-in function classes
      // we write an id that has been registered with BuiltinFunctions.
      if (JactlObject.class.isAssignableFrom(handleClass)) {
        checkpointer.writeBoolean(true);
        checkpointer.writeObject(Type.getInternalName(handleClass));
      }
      else {
        checkpointer.writeBoolean(false);
        checkpointer.writeCInt(BuiltinFunctions.getClassId(handleClass));
        checkpointer.writeCInt(handleClass.getName().hashCode());
      }
      checkpointer.writeObject(handleName);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCInt(HandleType.HANDLE.ordinal(), "Expected HANDLE");
      restorer.expectCInt(VERSION, "Bad version");
      boolean isJactlClass = restorer.readBoolean();
      if (isJactlClass) {
        handleClass = restorer.getJactlClass((String)restorer.readObject());
      }
      else {
        handleClass = BuiltinFunctions.getClass(restorer.readCInt());
        restorer.expectCInt(handleClass.getName().hashCode(), "Class name hash does not match for " + handleClass.getName());
      }
      handleName = (String)restorer.readObject();
      try {
        Field handleField = handleClass.getDeclaredField(handleName);
        handle = ((JactlMethodHandle)handleField.get(null)).handle;
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        throw new IllegalStateException("Error accessing field " + handleName + " in class " + handleClass.getName(), e);
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

  // NOTE: These don't support checkpoint/restore because of the risk of somone being able to inject
  // a malicious class/method name at restoration time that then gets invoked.
  public static class HostClassWrapperHandle extends JactlMethodHandle {
    boolean isStatic;
    public HostClassWrapperHandle(MethodHandle handle, boolean isStatic) {
      this.handle   = handle;
      this.isStatic = isStatic;
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
    public FunctionDescriptor getFunction() {
      return function;
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
    @Override protected void populateHandle() {
      if (handle == null) {
        wrappedHandle.populateHandle();
        handle = wrappedHandle.handle.bindTo(boundObj);
      }
    }
  }
}
