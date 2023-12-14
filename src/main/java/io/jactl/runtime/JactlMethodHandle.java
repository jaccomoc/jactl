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
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;

import static io.jactl.JactlType.FUNCTION;

/**
 * JactlMethodHandle is a wrapper around the standard MethodHandle and also
 * has enough information to be able to save and restore our state and recreate
 * the MethodHandle from this saved state (possibly in another process).
 */
public abstract class JactlMethodHandle implements Checkpointable {
  private static int VERSION = 1;
  protected MethodHandle handle;

  enum HandleType {
    HANDLE,
    ITERATOR_HANDLE,
    BUILTIN_FUNCTION,
    BOUND
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

  public static JactlMethodHandle create(MethodHandle handle, Class handleClass, String handleName) {
    return new Handle(handle, handleClass, handleName);
  }

  public static JactlMethodHandle create(MethodHandle handle, JactlIterator.IteratorType type, String handleName) {
    return new IteratorHandle(handle, type, handleName);
  }

  public static JactlMethodHandle createFuncHandle(MethodHandle handle, JactlType type, String name) {
    return new FunctionWrapperHandle(handle, type, name);
  }

  /////////////////////////////////////////////////////////////

  public static JactlMethodHandle create(int type) {
    switch (HandleType.values()[type]) {
      case HANDLE:             return new Handle();
      case ITERATOR_HANDLE:    return new IteratorHandle();
      case BUILTIN_FUNCTION:   return new FunctionWrapperHandle();
      case BOUND:              return new BoundHandle();
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
      checkpointer.writeCint(HandleType.HANDLE.ordinal());
      checkpointer.writeCint(VERSION);

      // For JactlObject handles we write the class name but for built-in function classes
      // we write an id that has been registered with BuiltinFunctions.
      if (JactlObject.class.isAssignableFrom(handleClass)) {
        checkpointer._writeBoolean(true);
        checkpointer.writeObject(Type.getInternalName(handleClass));
      }
      else {
        checkpointer._writeBoolean(false);
        checkpointer.writeCint(BuiltinFunctions.getClassId(handleClass));
        checkpointer.writeCint(handleClass.getName().hashCode());
      }
      checkpointer.writeObject(handleName);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCint(HandleType.HANDLE.ordinal(), "Expected HANDLE");
      restorer.expectCint(VERSION, "Bad version");
      boolean isJactlClass = restorer.readBoolean();
      if (isJactlClass) {
        handleClass = restorer.getJactlClass((String)restorer.readObject());
      }
      else {
        handleClass = BuiltinFunctions.getClass(restorer.readCint());
        restorer.expectCint(handleClass.getName().hashCode(), "Class name hash does not match for " + handleClass.getName());
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
      checkpointer.writeCint(HandleType.ITERATOR_HANDLE.ordinal());
      checkpointer.writeCint(VERSION);
      checkpointer.writeCint(type.ordinal());
      checkpointer.writeObject(handleName);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCint(HandleType.ITERATOR_HANDLE.ordinal(), "Expected HANDLE");
      restorer.expectCint(VERSION, "Bad version");
      type       = JactlIterator.IteratorType.values()[restorer.readCint()];
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

  private static class FunctionWrapperHandle extends JactlMethodHandle {
    private JactlType type;    // Can be null for global functions
    private String    name;
    FunctionWrapperHandle(){}
    public FunctionWrapperHandle(MethodHandle handle, JactlType type, String name) {
      this.handle = handle;
      this.type   = type;
      this.name   = name;
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(FUNCTION);
      checkpointer.writeCint(HandleType.BUILTIN_FUNCTION.ordinal());
      checkpointer.writeCint(VERSION);
      checkpointer.writeType(type);
      checkpointer.writeObject(name);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCint(HandleType.BUILTIN_FUNCTION.ordinal(), "Expected BUILTIN_FUNCTION");
      restorer.expectCint(VERSION, "Bad version");
      type = restorer.readType();
      name = (String)restorer.readObject();
      FunctionDescriptor func = type == null ? BuiltinFunctions.lookupGlobalFunction(name) : Functions.lookupMethod(type, name);
      if (func == null) {
        throw new IllegalStateException("Could not find function " + name + " for type " + type);
      }
      handle = func.wrapperHandle.handle;
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
      checkpointer.writeCint(HandleType.BOUND.ordinal());
      checkpointer.writeCint(VERSION);
      checkpointer.writeObject(wrappedHandle);
      checkpointer.writeObject(boundObj);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.FUNCTION);
      restorer.expectCint(HandleType.BOUND.ordinal(), "Expected BOUND");
      restorer.expectCint(VERSION, "Bad version");
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
