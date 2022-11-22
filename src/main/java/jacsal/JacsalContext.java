/*
 * Copyright 2022 James Crawford
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
 */

package jacsal;

import jacsal.runtime.ClassDescriptor;
import jacsal.runtime.JacsalPackage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JacsalContext {

  private final DynamicClassLoader classLoader = new DynamicClassLoader();

  private ExecutorService    eventLoop;
  private ExecutorService    blockingExecutor;

  int eventLoopThreads = Runtime.getRuntime().availableProcessors();
  int blockingThreads  = Runtime.getRuntime().availableProcessors() * 4;

  private boolean initialised = false;

  boolean printSize          = false;
  boolean evaluateConstExprs = true;

  // TODO: make BigDecimal division work same as for Groovy
  int     maxScale           = 20;

  // In repl mode top level vars are stored in the globals map and their type
  // is tracked here. We also allow redefinitions of existing vars. We don't
  // allow shadowing of actual global vars that already exist in the globals map.
  boolean replMode           = false;

  // In repl mode we keep track of the type of the top level vars here
  // and store their actual values in the globals map that is passed in
  // at run time.
  final Map<String,Expr.VarDecl> globalVars = new HashMap<>();

  // Whether to dump byte code during compilation
  boolean debug = false;

  String javaPackage = Utils.JACSAL_PKG;   // The Java package under which compiled classes will be generated

  Map<String,JacsalPackage> packages = new HashMap<>();

  ///////////////////////////////

  public static JacsalContext create() {
    return new JacsalContext();
  }

  private JacsalContext() {}

  Class<?> defineClass(ClassDescriptor descriptor, byte[] bytes) {
    String className = descriptor.getInternalName().replaceAll("/", ".");
    var clss = classLoader.defineClass(className, bytes);
    addClass(descriptor);
    return clss;
  }

  public JacsalContext eventLoopThreads(int threads)     { this.eventLoopThreads   = threads; return this; }
  public JacsalContext replMode(boolean mode)            { this.replMode           = mode;    return this; }
  public JacsalContext maxScale(int scale)               { this.maxScale           = scale;   return this; }
  public JacsalContext evaluateConstExprs(boolean value) { this.evaluateConstExprs = value;   return this; }
  public JacsalContext debug(boolean value)              { this.debug              = value;   return this; }
  public JacsalContext printSize(boolean value)          { this.printSize          = value;   return this; }
  public JacsalContext javaPackage(String pkg)           { this.javaPackage        = pkg;     return this; }

  public JacsalContext build() {
    eventLoop        = Executors.newFixedThreadPool(eventLoopThreads);
    blockingExecutor = Executors.newFixedThreadPool(blockingThreads);
    initialised = true;
    return this;
  }

  //////////////////////////////////

  public JacsalPackage getPackage(String name) {
    return packages.get(name);
  }

  public Object getThreadContext() { return null; }

  public void scheduleEvent(Object threadContext, Runnable event) {
    if (!initialised) {
      throw new IllegalStateException("JacsalContext not initialised. build() has not been invoked.");
    }
    eventLoop.submit(event);
  }

  public void scheduleBlocking(Runnable blocking) {
    if (!initialised) {
      throw new IllegalStateException("JacsalContext not initialised. build() has not been invoked.");
    }
    blockingExecutor.submit(blocking);
  }

  //////////////////////////////////

  private void addClass(ClassDescriptor descriptor) {
    String packageName = descriptor.getPackageName();
    var pkg = packages.get(packageName);
    if (pkg == null) {
      pkg = new JacsalPackage(packageName);
      packages.put(packageName, pkg);
    }
    pkg.addClass(descriptor);
  }

  private static class DynamicClassLoader extends ClassLoader {
    private Map<String,Class<?>> classes = new HashMap<>();

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      Class<?> clss = classes.get(name);
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
