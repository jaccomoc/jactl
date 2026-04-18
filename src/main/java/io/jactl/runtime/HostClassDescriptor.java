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
import io.jactl.JactlType;
import org.objectweb.asm.Type;

public class HostClassDescriptor implements ClassDescriptor {
  private Class<?>        hostClass;
  private JactlContext    jactlContext;
  private JactlType       jactlType;
  
  public HostClassDescriptor(Class<?> hostClass, JactlType type, JactlContext jactlContext) {
    this.hostClass    = hostClass;
    this.jactlType    = type;
    this.jactlContext = jactlContext;
  }
  
  HostClassDescriptor(Class<?> hostClass, JactlContext jactlContext) {
    this(hostClass, JactlType.createHostClass(hostClass, jactlContext), jactlContext);
  }

  public Class<?> getHostClass() {
    return hostClass;
  }

  @Override public String getSimpleName() {
    return hostClass.getSimpleName();
  }

  @Override public String getPackagedName() {
    return hostClass.getName();
  }

  @Override
  public String getNamePath() {
    return hostClass.getName().replaceAll(".*\\.", "");
  }
  
  @Override
  public String getPackageName() {
    return hostClass.getPackage().getName();
  }

  @Override
  public String getInternalName() {
    return Type.getInternalName(hostClass);
  }

  @Override
  public ClassDescriptor getBaseClass() {
    for (Class<?> base = hostClass.getSuperclass(); base != Object.class; base = base.getSuperclass()) {
      if (jactlContext.allowHostClassLookup.test(base.getName())) {
        return new HostClassDescriptor(base, jactlContext);
      }
    }
    return null;
  }

  @Override
  public JactlType getClassType() {
    return jactlType;
  }

  @Override
  public JactlType getBaseClassType() {
    ClassDescriptor baseClassDesc = getBaseClass();
    return baseClassDesc == null ? null : baseClassDesc.getClassType();
  }

  @Override
  public boolean isAssignableFrom(ClassDescriptor other) {
    return other instanceof HostClassDescriptor && hostClass.isAssignableFrom(((HostClassDescriptor)other).hostClass);
  }
  
  @Override
  public ClassDescriptor getInnerClass(String name) {
    String fullName = hostClass.getName() + "$" + name;
    if (jactlContext.allowHostClassLookup.test(fullName)) {
      Class<?> inner = null;
      try {
        inner = Class.forName(fullName);
        return new HostClassDescriptor(inner, jactlContext); 
      }
      catch (ClassNotFoundException ignored) {
      }
    }
    return null;
  }
}
