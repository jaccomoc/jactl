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

import java.lang.invoke.MethodHandle;

/**
 * JactlMethodHandle is a wrapper around the standard MethodHandle and also
 * has enough information to be able to save and restore our state and recreate
 * the MethodHandle from this saved state (possibly in another process).
 */
public class JactlMethodHandle {
  protected MethodHandle handle;

  public int parameterCount() {
    return handle.type().parameterCount();
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

  public static JactlMethodHandle create(MethodHandle handle, Class handleClass, String handleName) {
    return new Handle(handle, handleClass, handleName);
  }

  public static JactlMethodHandle createFuncHandle(MethodHandle handle, JactlType type, String name) {
    return new FunctionWrapperHandle(handle, type, name);
  }

  private static class Handle extends JactlMethodHandle {
    private Class        handleClass;
    private String       handleName;
    public Handle(MethodHandle handle, Class handleClass, String handleName) {
      this.handle      = handle;
      this.handleClass = handleClass;
      this.handleName  = handleName;
    }
  }

  private static class FunctionWrapperHandle extends JactlMethodHandle {
    private JactlType type;    // Can be null for global functions
    private String    name;
    public FunctionWrapperHandle(MethodHandle handle, JactlType type, String name) {
      this.handle = handle;
      this.type   = type;
      this.name   = name;
    }
  }

  private static class BoundHandle extends JactlMethodHandle {
    private JactlMethodHandle wrappedHandle;
    private Object            boundObj;
    public BoundHandle(JactlMethodHandle jmh, Object obj) {
      this.handle        = jmh.handle.bindTo(obj);
      this.wrappedHandle = jmh;
      this.boundObj      = obj;
    }
  }
}
