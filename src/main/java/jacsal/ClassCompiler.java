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

import jacsal.runtime.AsyncTask;
import jacsal.runtime.Continuation;
import jacsal.runtime.HeapLocal;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static jacsal.JacsalType.ANY;
import static jacsal.JacsalType.HEAPLOCAL;
import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  private static       long   counter            = 0;

                ClassVisitor   cv;
          final JacsalContext context;
          final String        internalName;
          final String         source;
  private final String         pkg;
  private final String         className;
  private final Stmt.ClassDecl classDecl;
  private       ClassWriter    cw;
  private       MethodVisitor  constructor;

  ClassCompiler(String source, JacsalContext context, Stmt.ClassDecl classDecl) {
    this.context   = context;
    this.pkg       = null;
    this.className = classDecl.name.getStringValue();
    internalName   = Utils.JACSAL_PKG + "/" + (pkg == null ? "" : pkg + "/") + className;
    this.classDecl = classDecl;
    this.source    = source;
    cv = cw = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
    if (debug()) {
      cv = new TraceClassVisitor(cw, new PrintWriter(System.out));
    }
  }

  public Function<Map<String,Object>,Future<Object>> compile() {
    cv.visit(V11, ACC_PUBLIC, internalName,
             null, "java/lang/Object", null);

    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    // Default constructor
    constructor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

    compileMethod(classDecl.scriptMain.declExpr);

    constructor.visitInsn(RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();

    compileClassInit();
    cv.visitEnd();

    Class<?> clss = context.loadClass(internalName.replaceAll("/", "."), cw.toByteArray());
    try {
      MethodHandle mh = MethodHandles.publicLookup().findVirtual(clss, Utils.JACSAL_SCRIPT_MAIN, MethodType.methodType(Object.class, Continuation.class, Map.class));
      return map -> {
        var future = new CompletableFuture<>();
        try {
          Object instance = clss.getDeclaredConstructor().newInstance();
          Object result = mh.invoke(instance, (Continuation)null, map);
          future.complete(result);
        }
        catch (Continuation c) {
          blockingWork(future, c);
        }
        catch (JacsalError e) {
          future.complete(e);
        }
        catch (Throwable t) {
          future.complete(new IllegalStateException("Invocation error: " + t, t));
        }
        return future;
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  private void blockingWork(CompletableFuture<Object> future, Continuation c) {
    // Need to execute async task on some sort of blocking work scheduler and then reschedule
    // continuation back onto the event loop or non-blocking scheduler (might even need to be
    // the same thread as we are on).
    Object executionContext = context.getThreadContext();
    context.scheduleBlocking(() -> {
      final var asyncTask   = c.getAsyncTask();
      Object    asyncResult = asyncTask.execute();
      context.scheduleEvent(executionContext, () -> resumeContinuation(future, asyncResult, asyncTask));
    });
  }

  private void resumeContinuation(CompletableFuture<Object> future, Object asyncResult, AsyncTask asyncTask) {
    try {
      Object result = asyncTask.resumeContinuation(asyncResult);
      // We finally get the real result out of the script execution
      future.complete(result);
    }
    catch (Continuation c) {
      blockingWork(future, c);
    }
    catch (Throwable t) {
      future.complete(t);
    }
  }

  /**
   * Initialise all the static handles to point to their methods
   */
  private void compileClassInit() {
    MethodVisitor classInit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    classInit.visitCode();

    // Create a continuation method for every async method and every wrapper. The continuation method takes a single
    // Continuation argument and extracts the parameter values before invoking the underlying function or function
    // wrapper as needed when continuing from an async suspend.
    Consumer<Expr.FunDecl> createContinuationHandle = (decl) -> {
      String       continuationMethod = Utils.continuationMethod(decl.functionDescriptor.implementingMethod);
      String       handleName         = Utils.continuationHandle(decl.functionDescriptor.implementingMethod);
      FieldVisitor handleVar          = cv.visitField(ACC_PRIVATE + ACC_STATIC, handleName, Type.getDescriptor(MethodHandle.class), null, null);
      handleVar.visitEnd();

      classInit.visitVarInsn(ALOAD, 0);
      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      classInit.visitLdcInsn(continuationMethod);
      Utils.loadConst(classInit, Object.class);    // return type
      Utils.loadConst(classInit, Continuation.class);
      classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);

      // Do the lookup
      classInit.visitMethodInsn(INVOKEVIRTUAL,
                                "java/lang/invoke/MethodHandles$Lookup",
                                decl.isStatic ? "findStatic" : "findVirtual",
                                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                false);

      // Store handle in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, handleName, "Ljava/lang/invoke/MethodHandle;");
    };

    Label tryStart   = new Label();
    Label tryEnd     = new Label();
    Label catchBlock = new Label();
    classInit.visitTryCatchBlock(tryStart, tryEnd, catchBlock, "java/lang/Exception");
    classInit.visitLabel(tryStart);
    classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
    classInit.visitVarInsn(ASTORE, 0);

    // For every method except the script main method
    Stream<Expr.FunDecl> methodsAndClosures = Stream.concat(classDecl.methods.stream(),
                                                            classDecl.closures.stream());
    // Iterate over the wrapper methods
    methodsAndClosures.forEach(funDecl -> {
      if (!funDecl.isScriptMain) {
        var wrapper = funDecl.wrapper;
        // Create the method descriptor for the method to be looked up.
        // Since we create a handle to the wrapper method the signature will be based on
        // the closed over vars it needs plus the source, offset, and then an Object that
        // will be the Object[] or Map form of the args.
        classInit.visitVarInsn(ALOAD, 0);
        classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
        String methodName       = wrapper.functionDescriptor.implementingMethod;
        String staticHandleName = Utils.staticHandleName(methodName);
        classInit.visitLdcInsn(methodName);
        // Wrapper methods return Object since caller won't know what type they would normally return
        Utils.loadConst(classInit, ANY);

        // Get all parameter types
        List<Class> paramTypes = new ArrayList<>();
        if (wrapper.heapLocalParams.size() > 0) {
          paramTypes.addAll(Collections.nCopies(wrapper.heapLocalParams.size(), jacsal.runtime.HeapLocal.class));
        }
        // Wrapper method always has a Continuation argument even if it doesn't need it since when invoking through
        // a MethodHandle we have no way of knowing whether it needs a Continuation or not
        paramTypes.add(Continuation.class);
        paramTypes.add(String.class);    // source
        paramTypes.add(int.class);       // offset
        paramTypes.add(Object.class);    // args

        // Load first parameter type and then load the rest in an array
        Utils.loadConst(classInit, paramTypes.remove(0));

        // We need an array for the rest of the type args
        Utils.loadConst(classInit, paramTypes.size());
        classInit.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        int i;
        for (i = 0; i < paramTypes.size(); i++) {
          classInit.visitInsn(DUP);
          Utils.loadConst(classInit, i);
          Utils.loadConst(classInit, paramTypes.get(i));
          classInit.visitInsn(AASTORE);
        }
        classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);

        // Do the lookup
        classInit.visitMethodInsn(INVOKEVIRTUAL,
                                  "java/lang/invoke/MethodHandles$Lookup",
                                  wrapper.isStatic ? "findStatic" : "findVirtual",
                                  "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                  false);

        // Store handle in static field
        classInit.visitFieldInsn(PUTSTATIC, internalName, staticHandleName, "Ljava/lang/invoke/MethodHandle;");

        createContinuationHandle.accept(wrapper);
      }

      if (funDecl.isScriptMain || funDecl.functionDescriptor.isAsync) {
        createContinuationHandle.accept(funDecl);
      }
    });

    classInit.visitLabel(tryEnd);
    Label end = new Label();
    classInit.visitJumpInsn(GOTO, end);
    classInit.visitLabel(catchBlock);
    classInit.visitVarInsn(ASTORE, 0);

    classInit.visitTypeInsn(NEW, "java/lang/RuntimeException");
    classInit.visitInsn(DUP);
    classInit.visitLdcInsn("Error initialising class " + internalName);
    classInit.visitVarInsn(ALOAD, 0);
    classInit.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
    classInit.visitInsn(ATHROW);

    classInit.visitLabel(end);
    classInit.visitInsn(RETURN);

    if (debug()) {
      classInit.visitEnd();
      cv.visitEnd();
    }

    classInit.visitMaxs(0, 0);
    classInit.visitEnd();
  }

  void compileMethod(Expr.FunDecl method) {
    String methodName =  method.functionDescriptor.implementingMethod;

    boolean isScriptMain = method.isScriptMain;
    // Create handles for all methods except the script main (since it is not callable)
    if (!isScriptMain) {
      // We compile the method and create a static method handle field that points to the method
      // so that we can pass the method by value (by passing the method handle).
      String       handleName = Utils.staticHandleName(method.wrapper.functionDescriptor.implementingMethod);
      FieldVisitor handleVar = cv.visitField(ACC_PRIVATE + ACC_STATIC, handleName, Type.getDescriptor(MethodHandle.class), null, null);
      handleVar.visitEnd();
      if (!method.isStatic) {
        // For non-static methods we also create a non-static method handle that will be bound to the instance.
        handleVar = cv.visitField(ACC_PRIVATE, Utils.handleName(methodName), Type.getDescriptor(MethodHandle.class), null, null);
        handleVar.visitEnd();

        // Add code to constructor to initialise this handle
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitFieldInsn(GETSTATIC, internalName, handleName, "Ljava/lang/invoke/MethodHandle;");
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
        constructor.visitFieldInsn(PUTFIELD, internalName, Utils.handleName(methodName), "Ljava/lang/invoke/MethodHandle;");
      }
    }

    doCompileMethod(method, methodName, isScriptMain);
    // For all methods that have parameters (apart from script main method), we generate a wrapper
    // method to handle filling in of optional parameter values and to support named parameter
    // invocation
    if (!isScriptMain) {
      doCompileMethod(method.wrapper, method.wrapper.functionDescriptor.implementingMethod, false);
      emitContinuationWrapper(method.wrapper);
    }
    if (isScriptMain || method.functionDescriptor.isAsync) {
      emitContinuationWrapper(method);
    }
  }

  private void doCompileMethod(Expr.FunDecl method, String methodName, boolean isScriptMain) {
    // Parameter types: heapLocals + params
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, methodName,
                                      MethodCompiler.getMethodDescriptor(method),
                                      null, null);
    mv.visitCode();

    // If main script method then initialise our globals field
    if (isScriptMain) {
      // Assign globals map to field so we can access it from anywhere
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(PUTFIELD, internalName, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class));
    }

    MethodCompiler methodCompiler = new MethodCompiler(this, method, mv);
    methodCompiler.compile();
    mv.visitEnd();
  }

  private void emitContinuationWrapper(Expr.FunDecl funDecl) {
    String methodName = Utils.continuationMethod(funDecl.functionDescriptor.implementingMethod);
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, methodName,
                                      Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Continuation.class)),
                                      null, null);
    mv.visitCode();

    // Load parameters from the Continuation and invoke the function
    mv.visitVarInsn(ALOAD, 0);

    final Runnable loadObjectArr = () -> {
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(GETFIELD, "jacsal/runtime/Continuation", "localObjects", "[Ljava/lang/Object;");
    };

    int slot = 1;
    for (int i = 0; i < funDecl.heapLocalParams.size(); i++) {
      Utils.restoreValue(mv, slot++, HEAPLOCAL, () -> Utils.loadContinuationArray(mv, 1, HEAPLOCAL));
    }

    mv.visitVarInsn(ALOAD, 1);   // Continuation
    slot++;

    for (int i = 0; i < funDecl.parameters.size(); i++) {
      final var  declExpr = funDecl.parameters.get(i).declExpr;
      JacsalType type     = declExpr.isPassedAsHeapLocal ? HEAPLOCAL : declExpr.type;
      Utils.restoreValue(mv, slot++, type, () -> Utils.loadContinuationArray(mv, 1, type));
    }

    mv.visitMethodInsn(INVOKEVIRTUAL, internalName, funDecl.functionDescriptor.implementingMethod, MethodCompiler.getMethodDescriptor(funDecl), false);
    Utils.box(mv, funDecl.returnType);   // Box if primitive
    mv.visitInsn(ARETURN);               // Always return Object from continuation wrapper

    if (debug()) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  boolean debug() {
    return context.debug;
  }
}
