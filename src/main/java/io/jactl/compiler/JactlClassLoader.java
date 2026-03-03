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

package io.jactl.compiler;

import io.jactl.Utils;
import io.jactl.runtime.RuntimeError;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class JactlClassLoader extends ClassLoader {

  private static JactlClassLoader theLoader = new JactlClassLoader();
  
  private Map<String,Class> classes  = Collections.synchronizedMap(new HashMap<>());
  private Set<String>       packages = Collections.synchronizedSet(new HashSet() {{ add(Utils.JACTL_PKG); }});
  
  JactlClassLoader() {
    super(JactlClassLoader.class.getClassLoader());
  }

  public static JactlClassLoader getInstance() {
    return theLoader;
  }
  
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    Class<?> clss = classes.get(name);
    if (clss != null) {
      return clss;
    }
    try {
      clss = super.findClass(name);
      return clss;
    }
    catch (ClassNotFoundException e) {}
    try {
      clss = Thread.currentThread().getContextClassLoader().loadClass(name);
      return clss;
    }
    catch (ClassNotFoundException e) {}
    clss = ClassLoader.getSystemClassLoader().loadClass(name);
    return clss;
  }

  public static Class<?> defineClass(String name, byte[] bytes) {
    Class<?> clss = theLoader.defineClass(name, bytes, 0, bytes.length);
    theLoader.classes.put(name, clss);
    return clss;
  }
  
  public static Class<?> forName(String name) throws ClassNotFoundException {
    return Class.forName(name, true, theLoader);
  }

  public static Class<?> forName(String name, boolean initialise) throws ClassNotFoundException {
    return Class.forName(name, initialise, theLoader);
  }

  public static void registerJactlPkg(String pkg) {
    theLoader.packages.add(pkg);
  }
  
  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // Check if this is a class within a Jactl package we can load it ourselves and
    // be the class loader for the class. This is important if it then needs to load
    // further classes (such as newly added built-in types) that we have defined.
    // Otherwise, if we let the parent class loader load the class, the class loader
    // for the class won't be able to find these additional classes when needed.
    if (packages.stream().anyMatch(name::startsWith)) {
      try (InputStream is = this.getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
        if (is != null) {
          byte[]   bytes = Utils.readAllBytes(is);
          Class<?> clss  = defineClass(name, bytes);
          if (resolve) {
            resolveClass(clss);
          }
          return clss;
        }
      }
      catch (IOException e) {
        throw new ClassNotFoundException("Error reading class file resource when loading class " + name, e);
      }
    }
    return super.loadClass(name, resolve);
  }

}
