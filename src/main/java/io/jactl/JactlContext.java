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

package io.jactl;

import io.jactl.runtime.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JactlContext {

  JactlEnv executionEnv     = null;

  public boolean printSize          = false;
  public boolean evaluateConstExprs = true;
  public boolean printLoop          = false;   // Whether to wrap script in "while (it=nextLine()) { <script> ; println it }"
  public boolean nonPrintLoop       = false;   // Whether to wrap script in "while (it=nextLine()) { <script> }"
  public boolean autoCreateAsync    = false;   // Whether to allow async functions in initialisers during auto-creation

  // Testing
  boolean checkpoint = false;
  boolean restore    = false;

  public int     minScale           = Utils.DEFAULT_MIN_SCALE;

  // In repl mode top level vars are stored in the globals map and their type
  // is tracked here. We also allow redefinitions of existing vars. We don't
  // allow shadowing of actual global vars that already exist in the globals map.
  public boolean replMode           = false;

  // In repl mode we keep track of the type of the top level vars here
  // and store their actual values in the globals map that is passed in
  // at run time.
  public final Map<String,Expr.VarDecl> globalVars = new HashMap<>();

  // Whether to dump byte code during compilation
  public int debugLevel = 0;

  public String javaPackage = Utils.JACTL_PKG;   // The Java package under which compiled classes will be generated
  public String internalJavaPackage;

  Set<String>                  packages         = new HashSet<>();
  Map<String, ClassDescriptor> classDescriptors = new HashMap<>();  // Keyed on internal name

  private PackageChecker       packageChecker = name -> packages.contains(name);
  private ClassLookup          classLookup    = name -> classDescriptors.get(name);
  private ClassAdder           classAdder     = this::_defineClass;
  private boolean              isIdePlugin    = false;
  private File                 buildDir;

  DynamicClassLoader           classLoader = new DynamicClassLoader();

  ///////////////////////////////

  public static JactlContextBuilder create() {
    return new JactlContext().getJactlContextBuilder();
  }

  private JactlContext() {}

  /**
   * Get the current context by finding our thread current class loader.
   * @return the current context
   * @throws IllegalStateException if current class loader is not of correct type
   */
  public static JactlContext getContext() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader instanceof DynamicClassLoader) {
      return ((DynamicClassLoader)loader).getJactlContext();
    }
    throw new IllegalStateException("Expected class loader of type " + DynamicClassLoader.class.getName() + " but found " + loader.getClass().getName());
  }

  /**
   * Lookup class based on fully qualified internal name (a/b/c/X$Y)
   * @param internalName the fully qualified internal class name
   * @return the class or null if not found
   */
  public Class getClass(String internalName) {
    if (internalName.startsWith(internalJavaPackage)) {
      return classLoader.findClassByInternalName(internalName);
    }
    return null;
  }

  public ClassDescriptor getExistingClassDescriptor(String internalName) {
    return classDescriptors.get(internalName);
  }

  ///////////////////////////////////

  private JactlContextBuilder getJactlContextBuilder() {
    return new JactlContextBuilder();
  }

  public Class<?> defineClass(ClassDescriptor descriptor, byte[] bytes) {
    return classAdder.addClass(descriptor, bytes);
  }

  private Class<?> _defineClass(ClassDescriptor descriptor, byte[] bytes) {
    String className = descriptor.getInternalName().replaceAll("/", ".");
    if (classLoader.getClass(className) != null) {
      // Redefining existing class so create a new ClassLoader. This allows already defined classes that
      // want to refer to the old version of the class to continue working.
      classLoader = new DynamicClassLoader(classLoader);
    }
    Class<?> clss = classLoader.defineClass(descriptor.getInternalName(), className, bytes);
    addClass(descriptor);
    if (buildDir != null) {
      String dirName  = descriptor.getJavaPackagedName().replaceAll("\\.[^\\.]*$", "");
      String fileName = descriptor.getNamePath() + ".class";
      File   pkg      = new File(buildDir, dirName);
      if (!pkg.mkdirs()) {
        throw new RuntimeException("Could not create package directory '" + pkg.getPath() + "'");
      }
      try {
        FileOutputStream fileOutputStream = new FileOutputStream(new File(pkg, fileName));
        fileOutputStream.write(bytes);
        fileOutputStream.close();
      }
      catch (IOException e) {
        throw new RuntimeException("Error writing class file for " + descriptor.getPackagedName() + ": " + e, e);
      }
    }
    return clss;
  }

  public interface PackageChecker { boolean exists(String name); }

  // Helper that maps internal name (io.jactl.pkg.a.b.c.A$B$C) to class descriptor
  public interface ClassLookup    { ClassDescriptor lookup(String internalName); }
  public interface ClassAdder     { Class<?>        addClass(ClassDescriptor descriptor, byte[] bytes); }

  public class JactlContextBuilder {
    private JactlContextBuilder() {}

    public JactlContextBuilder environment(JactlEnv env)         { executionEnv       = env;     return this; }
    public JactlContextBuilder minScale(int scale)               { minScale           = scale;   return this; }
    public JactlContextBuilder javaPackage(String pkg)           { javaPackage        = pkg;     return this; }
    public JactlContextBuilder debug(int value)                  { debugLevel         = value;   return this; }
    public JactlContextBuilder evaluateConstExprs(boolean value) { evaluateConstExprs = value;   return this; }

    public JactlContextBuilder packageChecker(PackageChecker pc) { packageChecker     = pc;      return this; }
    public JactlContextBuilder classLookup(ClassLookup lookup)   { classLookup        = lookup;  return this; }
    public JactlContextBuilder classAdder(ClassAdder adder)      { classAdder         = adder;   return this; }
    public JactlContextBuilder idePlugin(boolean value)          { isIdePlugin = value;   return this; }
    public JactlContextBuilder buildDir(String buildPath) {
      buildDir = new File(buildPath);
      if (buildDir.exists()) {
        if (!buildDir.isDirectory()) {
          throw new IllegalArgumentException("'" + buildPath + "' is not a directory");
        }
      }
      else {
        if (!buildDir.mkdirs()) {
          throw new IllegalArgumentException("Could not create directory '" + buildPath + "'");
        }
      }
      return this;
    }

    // Testing only
    public JactlContextBuilder checkpoint(boolean value)         { checkpoint             = value;   return this; }
    public JactlContextBuilder restore(boolean value)            { restore                = value;   return this; }

    // The following are for internal use
    JactlContextBuilder replMode(boolean mode)      { replMode               = mode;    return this; }
    JactlContextBuilder printLoop(boolean value)    { printLoop              = value;   return this; }
    JactlContextBuilder nonPrintLoop(boolean value) { nonPrintLoop           = value;   return this; }
    JactlContextBuilder printSize(boolean value)    { printSize              = value;   return this; }

    public JactlContext build() {
      if (executionEnv == null) {
        executionEnv = new DefaultEnv();
      }
      internalJavaPackage = javaPackage.replaceAll("\\.", "/");
      return JactlContext.this;
    }
  }

  //////////////////////////////////

  public boolean printLoop()    { return printLoop; }
  public boolean nonPrintLoop() { return nonPrintLoop; }

  // Whether running in an interactive IDE editor
  public boolean isIdePlugin()  { return isIdePlugin; }

  // Testing
  public boolean testCheckpointing() { return checkpoint; }
  public boolean restore()    { return restore; }

  // Whether to support auto-creation of class instances that can suspend.
  // E.g. for x.y.z = 1 if y is auto-created do we allow it to suspend during creation
  // in its initialiser (due to field initialisation invoking something async).
  // We will disallow for now in order to reduce how many instructions generated for code
  // like: a.b.c.d.e = x
  public boolean autoCreateAsync() { return autoCreateAsync; }

  public void debugLevel(int level) {
    this.debugLevel = level;
  }

  public boolean packageExists(String name) {
    return packageChecker.exists(name);
  }

  public ClassDescriptor getClassDescriptor(String packageName, String className) {
    String pname = packageName == null || packageName.equals("") ? "" : packageName + '.';
    String name  = internalJavaPackage + '.' + pname + className.replaceAll("\\.", "$");
    name = name.replaceAll("\\.", "/");
    return getClassDescriptor(name);
  }

  public ClassDescriptor getClassDescriptor(String internalName) {
    return classLookup.lookup(internalName);
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

  public void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
    executionEnv.saveCheckpoint(id, checkpointId, checkpoint, source, offset, result, resumer);
  }

  /**
   * Delete the checkpoint (possibly asynchronously in the background).
   * We don't need to wait for delete since the worst that will happen is that if we die before
   * delete has been persisted we will resurrect the instance and resume it from last checkpoint.
   * It will then delete itself when it completes.
   * @param id            the instance id
   * @param checkpointId  the checkpoint id (incrementing version for this instance id)
   */
  public void deleteCheckpoint(UUID id, int checkpointId) {
    executionEnv.deleteCheckpoint(id, checkpointId);
  }

  /**
   * Restore checkpoint and run script from where it had been checkpointed.
   * Invoke result handler with final result once script has finished.
   * @param checkpoint     the checkpointed state of a script
   * @param resultHandler  handler to be invoked with final script result
   */
  public void recoverCheckpoint(byte[] checkpoint, Consumer<Object> resultHandler) {
    Continuation cont = (Continuation)Restorer.restore(this, checkpoint);
    // If two args then we have commit closure and recovery closure so return recovery closure on recover
    Object result = cont.localObjects.length == 1 ? cont.localObjects[0] : cont.localObjects[1];
    scheduleEvent(null, () -> resumeContinuation(resultHandler, result, cont, cont.scriptInstance));
  }

  //////////////////////////////////

  public void asyncWork(Consumer<Object> completion, Continuation c, JactlScriptObject instance) {
    // Need to execute async task on some sort of blocking work scheduler and then reschedule
    // continuation back onto the event loop or non-blocking scheduler (might even need to be
    // the same thread as we are on).
    try {
      final AsyncTask asyncTask = c.getAsyncTask();

      Continuation asyncTaskCont = asyncTask.getContinuation();

      // Test mode
      if (testCheckpointing() && !(asyncTask instanceof CheckpointTask)) {
        byte[] buf = Checkpointer.checkpoint(asyncTaskCont, asyncTask.getSource(), asyncTask.getOffset());
        //System.out.println("DEBUG: checkpoint = \n" + Utils.dumpHex(checkpointer.getBuffer(), checkpointer.getLength()) + "\n");
        checkpointCount.getAndIncrement();
        checkpointSize.addAndGet(buf.length);
        if (restore()) {
          Continuation cont1 = (Continuation)Restorer.restore(this, buf);
          asyncTask.execute(this, instance, cont1.localObjects[0], result -> resumeContinuation(completion, result, cont1, instance));
        }
        else {
          asyncTask.execute(this, instance, asyncTaskCont.localObjects[0], result -> resumeContinuation(completion, result, asyncTaskCont, instance));
        }
      }
      else {
        asyncTask.execute(this, instance, asyncTaskCont.localObjects[0], result -> resumeContinuation(completion, result, asyncTaskCont, instance));
      }
    }
    catch (Throwable t) {
      cleanUp(instance);
      completion.accept(t);
    }
  }

  public static AtomicInteger checkpointCount = new AtomicInteger(0);
  public static AtomicLong    checkpointSize  = new AtomicLong(0);
  public static void resetCheckpointCounts() {
    checkpointCount = new AtomicInteger(0);
    checkpointSize  = new AtomicLong(0);
  }

  private void resumeContinuation(Consumer<Object> completion, Object asyncResult, Continuation cont, JactlScriptObject instance) {
    try {
      Object result = cont.continueExecution(asyncResult);
      // We finally get the real result out of the script execution
      cleanUp(instance);
      completion.accept(result);
    }
    catch (Continuation c) {
      asyncWork(completion, c, instance);
    }
    catch (Throwable t) {
      cleanUp(instance);
      completion.accept(t);
    }
  }

  private void cleanUp(JactlScriptObject instance) {
    if (instance.isCheckpointed()) {
      instance.incrementCheckpointId();
      deleteCheckpoint(instance.getInstanceId(), instance.checkpointId());
    }
  }

  private void addClass(ClassDescriptor descriptor) {
    String packageName = descriptor.getPackageName();
    packages.add(packageName);
    classDescriptors.put(descriptor.getInternalName(), descriptor);
  }

  public class DynamicClassLoader extends ClassLoader {
    private Map<String,Class<?>> classes                = new HashMap<>();
    private Map<String,Class<?>> classesByInternalName  = new HashMap<>();
    private DynamicClassLoader   previous               = null;

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
      try {
        clss = Thread.currentThread().getContextClassLoader().loadClass(name);
        if (clss != null) {
          return clss;
        }
      }
      catch (ClassNotFoundException e) {}
      return this.getClass().getClassLoader().loadClass(name);
    }

    Class findClassByInternalName(String internalName) {
      Class clss = classesByInternalName.get(internalName);
      if (clss == null && previous != null) {
        clss = previous.findClassByInternalName(internalName);
      }
      return clss;
    }

    Class<?> defineClass(String internalName, String name, byte[] bytes) {
      Class<?> clss = defineClass(name, bytes, 0, bytes.length);
      classes.put(name, clss);
      classesByInternalName.put(internalName, clss);
      return clss;
    }

    public JactlContext getJactlContext() {
      return JactlContext.this;
    }
  }
}
