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

package jacsal;

import jacsal.runtime.ClassDescriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JacsalContext {

  JacsalEnv executionEnv     = null;

  boolean printSize          = false;
  boolean evaluateConstExprs = true;
  boolean printLoop          = false;   // Whether to wrap script in "while (it=nextLine()) { <script> ; println it }"
  boolean nonPrintLoop       = false;   // Whether to wrap script in "while (it=nextLine()) { <script> }"

  int     minScale           = Utils.DEFAULT_MIN_SCALE;

  // In repl mode top level vars are stored in the globals map and their type
  // is tracked here. We also allow redefinitions of existing vars. We don't
  // allow shadowing of actual global vars that already exist in the globals map.
  boolean replMode           = false;

  // In repl mode we keep track of the type of the top level vars here
  // and store their actual values in the globals map that is passed in
  // at run time.
  final Map<String,Expr.VarDecl> globalVars = new HashMap<>();

  // Whether to dump byte code during compilation
  int debugLevel = 0;

  String javaPackage = Utils.JACSAL_PKG;   // The Java package under which compiled classes will be generated

  Set<String>                 packages    = new HashSet<>();
  Map<String,ClassDescriptor> classLookup = new HashMap<>();  // Keyed on internal name

  DynamicClassLoader          classLoader = new DynamicClassLoader();

  ///////////////////////////////

  public static JacsalContextBuilder create() {
    return new JacsalContext().getJacsalContextBuilder();
  }

  private JacsalContext() {}

  private JacsalContextBuilder getJacsalContextBuilder() {
    return new JacsalContextBuilder();
  }

  Class<?> defineClass(ClassDescriptor descriptor, byte[] bytes) {
    String className = descriptor.getInternalName().replaceAll("/", ".");
    if (classLoader.getClass(className) != null) {
      // Redefining existing class so create a new ClassLoader. This allows already defined classes that
      // want to refer to the old version of the class to continue working.
      classLoader = new DynamicClassLoader(classLoader);
    }
    var clss = classLoader.defineClass(className, bytes);
    addClass(descriptor);
    return clss;
  }

  public class JacsalContextBuilder {
    private JacsalContextBuilder() {}

    public JacsalContextBuilder replMode(boolean mode)            { replMode           = mode;    return this; }
    public JacsalContextBuilder minScale(int scale)               { minScale           = scale;   return this; }
    public JacsalContextBuilder evaluateConstExprs(boolean value) { evaluateConstExprs = value;   return this; }
    public JacsalContextBuilder debug(int value)                  { debugLevel         = value;   return this; }
    public JacsalContextBuilder printSize(boolean value)          { printSize          = value;   return this; }
    public JacsalContextBuilder javaPackage(String pkg)           { javaPackage        = pkg;     return this; }
    public JacsalContextBuilder printLoop(boolean value)          { printLoop          = value;   return this; }
    public JacsalContextBuilder nonPrintLoop(boolean value)       { nonPrintLoop       = value;   return this; }
    public JacsalContextBuilder environment(JacsalEnv env)        { executionEnv       = env;     return this; }

    public JacsalContext build() {
      if (executionEnv == null) {
        executionEnv = new DefaultEnv();
      }
      return JacsalContext.this;
    }
  }

  //////////////////////////////////

  public boolean printLoop()    { return printLoop; }
  public boolean nonPrintLoop() { return nonPrintLoop; }

  public boolean packageExists(String name) {
    return packages.contains(name);
  }

  public ClassDescriptor getClassDescriptor(String packageName, String className) {
    String pname = packageName == null || packageName.equals("") ? "" : packageName + '.';
    String name  = javaPackage + '.' + pname + className.replaceAll("\\.", "$");
    name = name.replaceAll("\\.", "/");
    return classLookup.get(name);
  }

  public ClassDescriptor getClassDescriptor(String internalName) {
    return classLookup.get(internalName);
  }

  public Object getThreadContext() { return executionEnv.getThreadContext(); }

  public void scheduleEvent(Object threadContext, Runnable event) {
    executionEnv.scheduleEvent(threadContext, event);
  }

  public void scheduleEvent(Runnable runnable, long timeMs) {
    executionEnv.scheduleEvent(runnable, timeMs);
  }

  public void scheduleEvent(Object threadContext, Runnable runnable, long timeMs) {
    executionEnv.scheduleEvent(threadContext, runnable, timeMs);
  }

  public void scheduleBlocking(Runnable blocking) {
    executionEnv.scheduleBlocking(blocking);
  }

  //////////////////////////////////

  private void addClass(ClassDescriptor descriptor) {
    String packageName = descriptor.getPackageName();
    packages.add(packageName);
    classLookup.put(descriptor.getInternalName(), descriptor);
  }

  private class DynamicClassLoader extends ClassLoader {
    private Map<String,Class<?>> classes  = new HashMap<>();
    private DynamicClassLoader   previous = null;

    DynamicClassLoader() {
      super(Thread.currentThread().getContextClassLoader());
    }

    DynamicClassLoader(DynamicClassLoader prev) {
      this.previous = prev;
    }

    Class<?> getClass(String name) {
      Class<?> clss = classes.get(name);
      if (clss == null && previous != null) {
        clss = previous.getClass(name);
      }
      return clss;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      Class<?> clss = getClass(name);
      if (clss != null) {
        return clss;
      }
      return super.findClass(name);
    }

    Class<?> defineClass(String name, byte[] bytes) {
      Class<?> clss = defineClass(name, bytes, 0, bytes.length);
      classes.put(name, clss);
      return clss;
    }
  }
}
