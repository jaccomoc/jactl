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

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static jacsal.JacsalType.ANY;
import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  private static       long   counter            = 0;

                ClassVisitor   cv;
          final CompileContext context;
          final String         internalName;
          final String         source;
  private final String         pkg;
  private final String         className;
  private final Stmt.ClassDecl classDecl;
  private       ClassWriter    cw;

  ClassCompiler(String source, CompileContext context, Stmt.ClassDecl classDecl) {
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

  public Function<Map<String,Object>,Object> compile() {
    cv.visit(V11, ACC_PUBLIC, internalName,
             null, "java/lang/Object", null);

    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    // Default constructor
    MethodVisitor constructor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

    classDecl.methods.forEach(method -> compileMethod(method, constructor));
    classDecl.closures.forEach(closure -> compileMethod(closure, constructor));

    constructor.visitInsn(RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();

    compileClassInit();
    cv.visitEnd();

    Class<?> clss = context.loadClass(internalName.replaceAll("/", "."), cw.toByteArray());
    try {
      MethodHandle mh = MethodHandles.publicLookup().findVirtual(clss, Utils.JACSAL_SCRIPT_MAIN, MethodType.methodType(Object.class, Map.class));
      return map -> {
        try {
          Object instance = clss.getDeclaredConstructor().newInstance();
          return mh.invoke(instance, map);
        }
        catch (JacsalError e) {
          throw e;
        }
        catch (Throwable e) {
          throw new IllegalStateException("Invocation error: " + e, e);
        }
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  /**
   * Initialise all the static handles to point to their methods
   */
  private void compileClassInit() {
    MethodVisitor classInit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    classInit.visitCode();
    Label tryStart   = new Label();
    Label tryEnd     = new Label();
    Label catchBlock = new Label();
    classInit.visitTryCatchBlock(tryStart, tryEnd, catchBlock, "java/lang/Exception");
    classInit.visitLabel(tryStart);
    classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
    classInit.visitVarInsn(ASTORE, 0);

    // For every method except the script main method
    Stream<Expr.FunDecl> methodsAndClosures = Stream.concat(classDecl.methods.stream()
                                                                             .filter(m -> !m.methodName.equals(Utils.JACSAL_SCRIPT_MAIN)),
                                                            classDecl.closures.stream());
    methodsAndClosures.forEach(method -> {
      // Create the method descriptor for the method to be looked up.
      // Since we create a handle to the wrapper method the signature will be based on
      // the closed over vars it needs plus the source, offset, and then an Object that
      // will be the Object[] or Map form of the args.
      classInit.visitVarInsn(ALOAD, 0);
      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      String methodName = method.methodName;
      String staticHandleName = Utils.staticHandleName(methodName);
      String wrapperMethodName = Utils.wrapperName(methodName);
      classInit.visitLdcInsn(wrapperMethodName);
      // Wrapper methods return Object since caller won't know what type they would normally return
      Utils.loadConst(classInit, ANY);
      // TODO: handle closed over vars (heap locals)
      Utils.loadConst(classInit, JacsalType.STRING);
      // We need an array for the rest of the type args
      Utils.loadConst(classInit, 2);
      classInit.visitTypeInsn(ANEWARRAY, "java/lang/Class");
      classInit.visitInsn(DUP);
      Utils.loadConst(classInit, 0);
      Utils.loadConst(classInit, JacsalType.INT);
      classInit.visitInsn(AASTORE);
      classInit.visitInsn(DUP);
      Utils.loadConst(classInit, 1);
      Utils.loadConst(classInit, ANY);
      classInit.visitInsn(AASTORE);
      classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);

      // Do the lookup
      classInit.visitMethodInsn(INVOKEVIRTUAL,
                                "java/lang/invoke/MethodHandles$Lookup",
                                method.isStatic ? "findStatic" : "findVirtual",
                                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                false);

      // Store handle in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, staticHandleName, "Ljava/lang/invoke/MethodHandle;");
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

  private void compileMethod(Expr.FunDecl method, MethodVisitor constructor) {
    String methodName =  method.methodName;

    boolean isScriptMain = methodName.equals(Utils.JACSAL_SCRIPT_MAIN);
    // Create handles for all methods except the script main (since it is not callable)
    if (!isScriptMain) {
      // We compile the method and create a static method handle field that points to the method
      // so that we can pass the method by value (by passing the method handle).
      FieldVisitor handleVar = cv.visitField(ACC_PRIVATE + ACC_STATIC, Utils.staticHandleName(methodName), Type.getDescriptor(MethodHandle.class), null, null);
      handleVar.visitEnd();
      if (!method.isStatic) {
        // For non-static methods we also create a non-static method handle that will be bound to the instance.
        handleVar = cv.visitField(ACC_PRIVATE, Utils.handleName(methodName), Type.getDescriptor(MethodHandle.class), null, null);
        handleVar.visitEnd();

        // Add code to constructor to initialise this handle
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitFieldInsn(GETSTATIC, internalName, Utils.staticHandleName(methodName), "Ljava/lang/invoke/MethodHandle;");
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
        constructor.visitFieldInsn(PUTFIELD, internalName, Utils.handleName(methodName), "Ljava/lang/invoke/MethodHandle;");
      }
    }

    var paramDescriptors = method.parameters.stream().map(varDecl -> varDecl.declExpr.type.descriptorType()).toArray(Type[]::new);
    String methodDescriptor = Type.getMethodDescriptor(method.returnType.descriptorType(),
                                                       paramDescriptors);
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, methodName,
                                      methodDescriptor,
                                      null, null);
    mv.visitCode();

    // If main script method then initialise our globals field
    if (isScriptMain) {
      // Assign globals map to field so we can access it from anywhere
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, internalName, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class));
    }

    MethodCompiler methodCompiler = new MethodCompiler(this, method, mv);
    methodCompiler.compile();
    mv.visitEnd();

    // For all methods that have parameters (apart from script main method), we generate a wrapper
    // method to handle filling in of optional parameter values and to support named parameter
    // invocation
    if (!isScriptMain) {
      Type returnType = ANY.descriptorType();    // Wrappers return Object
      String wrapperDescriptor = Type.getMethodDescriptor(returnType,
                                                          Type.getType(String.class),
                                                          Type.getType(int.class),
                                                          Type.getType(Object.class));
      mv = cv.visitMethod(ACC_PUBLIC, Utils.wrapperName(methodName),
                          wrapperDescriptor,
                          null, null);
      mv.visitCode();

      methodCompiler = new MethodCompiler(this, method, mv);
      methodCompiler.compileWrapper();
      mv.visitEnd();
    }
  }

  boolean debug() {
    return context.debug;
  }
}
