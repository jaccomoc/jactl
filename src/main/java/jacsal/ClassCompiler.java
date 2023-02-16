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
import jacsal.runtime.Continuation;
import jacsal.runtime.JacsalObject;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.LONG;
import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  private static       long   counter            = 0;

  final JacsalContext  context;
  final String         internalName;
  final String         source;
  final String         pkg;
  final String         className;
  final Stmt.ClassDecl classDecl;
  final String         sourceName;      // Name of source file
  protected ClassVisitor    cv;
  protected ClassWriter     cw;
  protected MethodVisitor   constructor;
  protected MethodVisitor   classInit;   // MethodVisitor for static class initialiser
  protected ClassDescriptor classDescriptor;
  protected Class           compiledClass;

  private   Label         classInitTryStart   = new Label();
  private   Label         classInitTryEnd     = new Label();
  private   Label         classInitCatchBlock = new Label();

  ClassCompiler(String source, JacsalContext context, String jacsalPkg, Stmt.ClassDecl classDecl, String sourceName) {
    this.context         = context;
    this.pkg             = jacsalPkg;
    this.className       = classDecl.name.getStringValue();
    this.classDecl       = classDecl;
    this.classDescriptor = classDecl.classDescriptor;
    internalName         = classDescriptor.getInternalName();
    this.source          = source;
    this.sourceName      = sourceName;
    cv = cw = new JacsalClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES, context);
    if (debug()) {
      cv = new TraceClassVisitor(cw, new PrintWriter(System.out));
    }

    String baseName = classDecl.baseClass != null ? classDecl.baseClass.getInternalName() : Type.getInternalName(Object.class);
    cv.visit(V11, ACC_PUBLIC, internalName, null, baseName, new String[] { Type.getInternalName(JacsalObject.class) });
    cv.visitSource(sourceName, null);

    classInit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    classInit.visitCode();

    // Default constructor
    constructor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V", false);

    var fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC, Utils.JACSAL_FIELDS_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACSAL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACSAL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACSAL_FIELDS_METHODS_MAP, "Ljava/util/Map;");
    fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC, Utils.JACSAL_STATIC_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACSAL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACSAL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACSAL_STATIC_METHODS_MAP, "Ljava/util/Map;");

    // Add instance method and static for retrieving map of static Jacsal methods
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACSAL_STATIC_METHODS_GETTER,
                                      "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, Utils.JACSAL_STATIC_METHODS_STATIC_GETTER,
                                      "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // Add method for retrieving map of fields and methods
    mv = cv.visitMethod(ACC_PUBLIC, Utils.JACSAL_FIELDS_METHODS_GETTER,
                        "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_FIELDS_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    if (classDecl.baseClass != null) {
      // Add all fields/methods from parent class to this one
      classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_STATIC_METHODS_MAP, MAP.descriptor());
      classInit.visitFieldInsn(GETSTATIC, classDecl.baseClass.getInternalName(), Utils.JACSAL_STATIC_METHODS_MAP, MAP.descriptor());
      classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "putAll", "(Ljava/util/Map;)V", true);
      classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_FIELDS_METHODS_MAP, MAP.descriptor());
      classInit.visitFieldInsn(GETSTATIC, classDecl.baseClass.getInternalName(), Utils.JACSAL_FIELDS_METHODS_MAP, MAP.descriptor());
      classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "putAll", "(Ljava/util/Map;)V", true);
    }

    classInit.visitTryCatchBlock(classInitTryStart, classInitTryEnd, classInitCatchBlock, "java/lang/Exception");
    classInit.visitLabel(classInitTryStart);
    classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
    classInit.visitVarInsn(ASTORE, 0);
  }

  public void compileClass() {
    compileSingleClass();
    compileInnerClasses();
  }

  protected void compileSingleClass() {
    classDecl.fieldVars.forEach((field,varDecl) -> defineField(field, varDecl.type));
    classDecl.methods.forEach(method -> compileMethod(method.declExpr));
    finishClassCompile();
  }

  protected void compileInnerClasses() {
    var orderedInnerClasses = allInnerClasses(classDecl).sorted((a,b) -> Integer.compare(ancestorCount(a), ancestorCount(b))).collect(Collectors.toList());
    orderedInnerClasses.forEach(clss -> {
      if (clss != classDecl) {
        var compiler = new ClassCompiler(source, context, pkg, clss, sourceName);
        compiler.compileSingleClass();
      }
    });
  }

  private Stream<Stmt.ClassDecl> allInnerClasses(Stmt.ClassDecl classDecl) {
    return Stream.concat(Stream.of(classDecl), classDecl.innerClasses.stream().flatMap(this::allInnerClasses));
  }

  private int ancestorCount(Stmt.ClassDecl classDecl) {
    int count = 0;
    for (var baseClass = classDecl.classDescriptor.getBaseClass(); baseClass != null; baseClass = baseClass.getBaseClass()) {
      count++;
    }
    return count;
  }

  void finishClassCompile() {
    constructor.visitInsn(RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();

    classInit.visitLabel(classInitTryEnd);
    Label end = new Label();
    classInit.visitJumpInsn(GOTO, end);
    classInit.visitLabel(classInitCatchBlock);
    classInit.visitVarInsn(ASTORE, 0);

    classInit.visitTypeInsn(NEW, "java/lang/RuntimeException");
    classInit.visitInsn(DUP);
    classInit.visitLdcInsn("Error initialising class " + internalName);
    classInit.visitVarInsn(ALOAD, 0);
    classInit.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
    classInit.visitInsn(ATHROW);

    classInit.visitLabel(end);
    classInit.visitInsn(RETURN);

//    if (debug()) {
//      classInit.visitEnd();
//      cv.visitEnd();
//    }

    classInit.visitMaxs(0, 0);
    classInit.visitEnd();

    cv.visitEnd();

    byte[]   bytes = cw.toByteArray();
    if (context.printSize) {
      System.out.println("Class " + className + ": compiled size = " + bytes.length);
    }
    compiledClass = context.defineClass(classDescriptor, bytes);
  }

  /**
   * Create static handle to point to varargs wrapper method and another one for
   * the continuation handle if needed
   */
  protected void addHandleToClass(Expr.FunDecl funDecl) {
    // Helper for creating handle to continuation wrapper
    Consumer<Expr.FunDecl> createContinuationHandle = (decl) -> {
      String       continuationMethod = Utils.continuationMethod(decl.functionDescriptor.implementingMethod);
      String       handleName         = Utils.continuationHandle(decl.functionDescriptor.implementingMethod);
      FieldVisitor handleVar          = cv.visitField(ACC_PUBLIC + ACC_STATIC, handleName, Type.getDescriptor(MethodHandle.class), null, null);
      handleVar.visitEnd();

      classInit.visitVarInsn(ALOAD, 0);
      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      classInit.visitLdcInsn(continuationMethod);
      Utils.loadConst(classInit, Object.class);    // return type
      Utils.loadConst(classInit, Continuation.class);
      classInit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);

      if (!decl.isStatic()) {
        classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      }

      // Do the lookup
      classInit.visitMethodInsn(INVOKEVIRTUAL,
                                "java/lang/invoke/MethodHandles$Lookup",
                                decl.isStatic() ? "findStatic" : "findSpecial",
                                decl.isStatic() ? "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                                                : "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                false);

      // Store handle in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, handleName, "Ljava/lang/invoke/MethodHandle;");
    };

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
      if (wrapper.heapLocals.size() > 0) {
        paramTypes.addAll(Collections.nCopies(wrapper.heapLocals.size(), jacsal.runtime.HeapLocal.class));
      }
      // Wrapper method always has a Continuation argument even if it doesn't need it since when invoking through
      // a MethodHandle we have no way of knowing whether it needs a Continuation or not
      paramTypes.add(Continuation.class);
      paramTypes.addAll(wrapper.parameters.stream()
                                          .map(p -> p.declExpr.type.classFromType())
                                          .collect(Collectors.toList()));

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
                                wrapper.isStatic() ? "findStatic" : "findVirtual",
                                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                false);

      if (!funDecl.isClosure()) {
        classInit.visitInsn(DUP);   // For actual functions/methods we will also store in a map so duplicate first
      }

      // Store in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, staticHandleName, "Ljava/lang/invoke/MethodHandle;");

      // For methods/functions store in either _$j$FieldsAndMethods or _$j$StaticMethods as appropriate
      if (!funDecl.isClosure()) {
        String mapName = funDecl.isStatic() ? Utils.JACSAL_STATIC_METHODS_MAP : Utils.JACSAL_FIELDS_METHODS_MAP;
        classInit.visitFieldInsn(GETSTATIC, internalName, mapName, MAP.descriptor());
        classInit.visitInsn(SWAP);
        Utils.loadConst(classInit, funDecl.nameToken.getStringValue());
        classInit.visitInsn(SWAP);
        classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
      }

      if (wrapper.functionDescriptor.isAsync) {
        createContinuationHandle.accept(wrapper);
      }
    }

    if (funDecl.functionDescriptor.isAsync) {
      createContinuationHandle.accept(funDecl);
    }
  }

  void compileMethod(Expr.FunDecl method) {
    String methodName =  method.functionDescriptor.implementingMethod;

    // We compile the method and create a static method handle field that points to the method
    // so that we can pass the method by value (by passing the method handle).
    String       wrapperMethodName = method.wrapper.functionDescriptor.implementingMethod;
    String       staticHandleName  = Utils.staticHandleName(wrapperMethodName);
    FieldVisitor handleVar          = cv.visitField(ACC_PUBLIC + ACC_STATIC, staticHandleName, Type.getDescriptor(MethodHandle.class), null, null);
    handleVar.visitEnd();
    if (!method.isStatic() && method.isClosure()) {
      // For non-static closures we also create a non-static method handle for the wrapper method
      // that will be bound to the instance. For non-static functions we bind the handle when we
      // compile the declaration and store it in the variable.
      String instanceHandleName = Utils.handleName(wrapperMethodName);
      handleVar = cv.visitField(ACC_PUBLIC, instanceHandleName, Type.getDescriptor(MethodHandle.class), null, null);
      handleVar.visitEnd();

      // Add code to constructor to initialise this handle
      constructor.visitVarInsn(ALOAD, 0);
      constructor.visitFieldInsn(GETSTATIC, internalName, staticHandleName, "Ljava/lang/invoke/MethodHandle;");
      constructor.visitVarInsn(ALOAD, 0);
      constructor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
      constructor.visitFieldInsn(PUTFIELD, internalName, instanceHandleName, "Ljava/lang/invoke/MethodHandle;");
    }

    doCompileMethod(method, methodName, false);
    // For all methods that have parameters (apart from script main method), we generate a wrapper
    // method to handle filling in of optional parameter values and to support named parameter
    // invocation
    doCompileMethod(method.wrapper, wrapperMethodName, false);
    addHandleToClass(method);
  }

  protected void doCompileMethod(Expr.FunDecl method, String methodName, boolean isScriptMain) {
    // Parameter types: heapLocals + params
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + (method.isStatic() ? ACC_STATIC : 0), methodName,
                                      MethodCompiler.getMethodDescriptor(method),
                                      null, null);
    mv.visitCode();

    // If main script method then initialise our globals field
    if (isScriptMain) {
      // FieldAssign globals map to field so we can access it from anywhere
      mv.visitVarInsn(ALOAD, 0);
      boolean isAsync = method.functionDescriptor.isAsync;
      mv.visitVarInsn(ALOAD, isAsync ? 2 : 1);
      mv.visitFieldInsn(PUTFIELD, internalName, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class));
    }

    MethodCompiler methodCompiler = new MethodCompiler(this, method, methodName, mv);
    methodCompiler.compile();
    mv.visitEnd();

    if (method.functionDescriptor.isAsync) {
      emitContinuationWrapper(method);
    }
  }

  // Create a continuation method for every async method. The continuation method takes a single Continuation argument
  // and extracts the parameter values before invoking the underlying function or function wrapper as needed when
  // continuing from an async suspend.
  protected void emitContinuationWrapper(Expr.FunDecl funDecl) {
    String methodName = Utils.continuationMethod(funDecl.functionDescriptor.implementingMethod);
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC + (funDecl.isStatic() ? ACC_STATIC : 0), methodName,
                                      Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Continuation.class)),
                                      null, null);
    mv.visitCode();

    // Load parameters from the Continuation and invoke the function
    int slot = 0;
    if (!funDecl.isStatic()) {
      mv.visitVarInsn(ALOAD, slot++);
    }
    int continuationSlot = slot++;

    // Generate code for loading saved parameter values back onto stack
    int numHeapLocals = funDecl.heapLocals.size();
    for (int i = 0; i < numHeapLocals; i++, slot++) {
      Utils.loadStoredValue(mv, slot, HEAPLOCAL, () -> Utils.loadContinuationArray(mv, continuationSlot, HEAPLOCAL));
    }

    mv.visitVarInsn(ALOAD, continuationSlot);   // Continuation

    // Load parameter values from our long[] or Object[] in the Continuation.
    // The index into the long[]/Object[] will be paramIdx + heapLocals.size() since the parameters come immediately
    // after the heaplocals (and the Continuation that we don't store).
    for (int i = 0; i < funDecl.parameters.size(); i++, slot++) {
      final var  declExpr = funDecl.parameters.get(i).declExpr;
      JacsalType type     = declExpr.isPassedAsHeapLocal ? HEAPLOCAL : declExpr.type;
      Utils.loadStoredValue(mv, slot, type, () -> Utils.loadContinuationArray(mv, continuationSlot, type));
      if (type.is(LONG,JacsalType.DOUBLE)) {
        slot++;
      }
    }

    // INVOKESTATIC or INVOKESPECIAL to make sure we don't get overridden method (if child class has overridden us).
    mv.visitMethodInsn(funDecl.isStatic() ? INVOKESTATIC : INVOKESPECIAL, internalName, funDecl.functionDescriptor.implementingMethod, MethodCompiler.getMethodDescriptor(funDecl), false);
    Utils.box(mv, funDecl.returnType);   // Box if primitive
    mv.visitInsn(ARETURN);               // Always return Object from continuation wrapper

//    if (debug()) {
//      mv.visitEnd();
//      cv.visitEnd();
//    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  public void defineField(String name, JacsalType type) {
    var fieldVisitor = cv.visitField(ACC_PUBLIC, name, type.descriptor(), null, null);
    fieldVisitor.visitEnd();

    // Add code to class initialiser to find a VarHandle and add to our static fieldsAndMethods map
    classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACSAL_FIELDS_METHODS_MAP, MAP.descriptor());
    Utils.loadConst(classInit, name);

    classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
    classInit.visitLdcInsn(name);
    classInit.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);

    classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
  }

  boolean debug() {
    return context.debugLevel > 0;
  }

  boolean annotate() {
    return context.debugLevel > 1;
  }

  //////////////////////////////////////

  private static class JacsalClassWriter extends ClassWriter {

    JacsalContext context;

    public JacsalClassWriter(int flags, JacsalContext context) {
      super(flags);
      this.context = context;
    }

    private static final String objectClass = Type.getInternalName(Object.class);

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
      if (type1.equals(type2)) {
        return type1;
      }
      if (type1.equals(objectClass) || type2.equals(objectClass)) {
        return objectClass;
      }
      String jacsalObjectClass = Type.getInternalName(JacsalObject.class);
      if (type1.startsWith(context.javaPackage) && type2.equals(jacsalObjectClass)) {
        return jacsalObjectClass;
      }
      if (type2.startsWith(context.javaPackage) && type1.equals(jacsalObjectClass)) {
        return jacsalObjectClass;
      }
      try {
        if (type1.startsWith(context.javaPackage) && type2.startsWith(context.javaPackage)) {
          var clss1 = context.getClassDescriptor(type1);
          var clss2 = context.getClassDescriptor(type2);
          if (clss1 != null && clss2 != null) {
            if (clss1.isAssignableFrom(clss2)) {
              return type1;
            }
            if (clss2.isAssignableFrom(clss1)) {
              return type2;
            }
            if (clss1.isInterface() || clss2.isInterface()) {
              return jacsalObjectClass;
            }
            else {
              do {
                clss1 = clss1.getBaseClass();
              }
              while (clss1 != null && !clss1.isAssignableFrom(clss2));

              return clss1 == null ? jacsalObjectClass : clss1.getInternalName();
            }
          }
        }
        else
        if (type1.startsWith(context.javaPackage) || type2.startsWith(context.javaPackage)) {
          // Common ancestor or Jacsal class and non-Jacsal class is Object but I am not sure
          // if returning Object is the right answer or whether this is just hiding an issue
          // in the generated bytecode...
          return Type.getInternalName(Object.class);
        }
        return super.getCommonSuperClass(type1, type2);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

}
