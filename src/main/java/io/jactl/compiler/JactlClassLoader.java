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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JactlClassLoader extends ClassLoader {

  private static JactlClassLoader theLoader = new JactlClassLoader();
  
  private Map<String,Class> classes = Collections.synchronizedMap(new HashMap<>());
  
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
}
