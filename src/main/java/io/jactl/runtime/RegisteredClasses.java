/*
 * Copyright © 2022,2023,2024  James Crawford
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class RegisteredClasses {

  // static
  public static final RegisteredClasses INSTANCE = new RegisteredClasses(); 
  
  private Map<String,Class<?>>        classByInternalJavaName             = new HashMap<>();  // name in '/' form
  private Map<String,ClassDescriptor> registeredClassesByJavaName         = new HashMap<>();  // name in '.' form
  private Map<String,ClassDescriptor> registeredClassesByInternalJavaName = new HashMap<>();  // name in '/' form
  private Map<String,ClassDescriptor> registeredClassesByJactlName        = new HashMap<>();  // name in '.' form
  private Set<String>                 registeredJactlPackages             = new HashSet<>();  // name in '.' form
  private Map<String,ClassDescriptor> autoImportedClasses                 = new HashMap<>();  // Jactl class name (no pkg)
  
  private Map<Class<?>, BiConsumer<Checkpointer,Object>> checkpointers = new HashMap<>();
  private Map<Class<?>, Function<Restorer,Object>>       restorers     = new HashMap<>();

  public Map<String, ClassDescriptor> getAutoImportedClasses() {
    return autoImportedClasses;
  }

  public ClassDescriptor getClassDescriptor(String jactlName) {
    return registeredClassesByJactlName.get(jactlName);
  }

  public ClassDescriptor getClassDescriptor(Class<?> javaClass) {
    for (; javaClass != null; javaClass = javaClass.getSuperclass()) {
      ClassDescriptor descriptor = registeredClassesByJavaName.get(javaClass.getName());
      if (descriptor != null) {
        return descriptor;
      }
    }
    return null;
  }

  public ClassDescriptor getClassDescriptorByInternalJavaName(String internalJavaName) {
    return registeredClassesByInternalJavaName.get(internalJavaName);
  }

  public Class<?> findClassByInternalJavaName(String internalName) {
    return classByInternalJavaName.get(internalName);
  }
  
  public boolean packageExists(String name) {
    return registeredJactlPackages.contains(name);
  }
  
  public void registerClassByJavaName(String internalName, Class<?> clss) {
    if (classByInternalJavaName.put(internalName, clss) != null) {
      throw new IllegalStateException("Java class " + internalName.replace('/','.') + " already registered as Jactl type " + getClassDescriptor(clss).getPackagedName());
    }
  }

  /**
   * Declare class so it can be referenced before it is registered
   *
   * @param jactlClass the full Jactl name in '.' form (excluding base java package)
   * @param javaClass  the full class name in '.' form
   */
  public void declareClass(String jactlClass, String javaClass) {
    getOrCreateClassDescriptor(jactlClass, javaClass);
  }

  public ClassDescriptor getOrCreateClassDescriptor(String jactlClass, String javaClass) {
    String jactlClassName = jactlClass.replaceAll("^.*\\.", "");
    String jactlPackage = jactlClass.replaceAll("\\.[^.]*$", "");
    ClassDescriptor desc = registeredClassesByJactlName.computeIfAbsent(jactlClass, n -> new ClassDescriptor(jactlClassName, false, "", jactlPackage, null, null, true, javaClass));
    registeredClassesByJavaName.putIfAbsent(javaClass, desc);
    registeredClassesByInternalJavaName.putIfAbsent(javaClass.replace('.','/'), desc);
    registeredJactlPackages.add(jactlPackage);
    return desc;
  }
  
  void addAutoImported(String jactlClass, ClassDescriptor classDescriptor) {
    if (autoImportedClasses.put(jactlClass, classDescriptor) != null) {
      throw new IllegalStateException("Cannot auto-import two classes of the same name: new=" + jactlClass + ", old=" + classDescriptor.getPackagedName());
    }
  }
  
  public Class<?> getRegisteredClass(Class<?> javaClass) {
    for (Class<?> clss = javaClass; clss != null; clss = clss.getSuperclass()) {
      if (registeredClassesByJavaName.containsKey(clss.getName())) {
        return clss;
      }
    }
    throw new IllegalStateException("Cannot find class " + javaClass.getName() + " as a registered Jactl type");
  }
  
  public BiConsumer<Checkpointer,Object> getCheckpointer(Class<?> javaClass) {
    for (; javaClass != null; javaClass = javaClass.getSuperclass()) {
      BiConsumer<Checkpointer, Object> checkPointer = checkpointers.get(javaClass);
      if (checkPointer != null) {
        return checkPointer;
      }
    }
    return null;
  }
  
  void registerCheckpointer(Class<?> javaClass, BiConsumer<Checkpointer,Object> checkpointer) {
    checkpointers.put(javaClass, checkpointer);
  }
  
  public Function<Restorer, Object> getRestorer(Class<?> javaClass) {
    return restorers.get(javaClass);
  }
  
  void registerRestorer(Class<?> javaClass, Function<Restorer, Object> restorer) {
    restorers.put(javaClass, restorer);
  }
}
