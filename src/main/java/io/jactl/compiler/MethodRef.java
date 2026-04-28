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

package io.jactl.compiler;

import io.jactl.JactlContext;
import io.jactl.JactlType;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodRef {
  public final String     declaringClassInternal;
  public final boolean    isInterface;
  public final String     methodName;
  public final Method     method;
  public final boolean    isStatic;
  public final String     methodDescriptor;
  public final Class<?>[] parameterTypes;
  private final JactlType returnType;
  private final Class<?>  returnClass;
  
  
  public MethodRef(Method method) {
    this.method            = method;
    methodName             = method.getName();
    isStatic               = Modifier.isStatic(method.getModifiers());
    Class<?> declaringClass = method.getDeclaringClass();
    declaringClassInternal = Type.getInternalName(declaringClass);
    isInterface            = declaringClass.isInterface();
    methodDescriptor       = Type.getMethodDescriptor(method);
    returnClass            = method.getReturnType();
    parameterTypes         = method.getParameterTypes();
    
    // Set return type if it is a built-in type
    returnType       = JactlType.builtinTypeFromClass(returnClass);
  }
  
  public JactlType getReturnType(JactlContext ctx) {
    if (returnType != null) {
      return returnType;
    }
    return ctx.typeFromClass(returnClass);
  }
}
