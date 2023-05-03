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
import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.LONG;
import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  private static       long   counter            = 0;

  final JactlContext  context;
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

  ClassCompiler(String source, JactlContext context, String jactlPkg, Stmt.ClassDecl classDecl, String sourceName) {
    this.context         = context;
    this.pkg             = jactlPkg;
    this.className       = classDecl.name.getStringValue();
    this.classDecl       = classDecl;
    this.classDescriptor = classDecl.classDescriptor;
    internalName         = classDescriptor.getInternalName();
    this.source          = source;
    this.sourceName      = sourceName;
    cv = cw = new JactlClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES, context);
    if (debug()) {
      cv = new TraceClassVisitor(cw, new Textifier(), new PrintWriter(System.out));
      //cv = new TraceClassVisitor(cw, new ASMifier(), new PrintWriter(System.out));
    }

    String baseName = classDecl.baseClass != null ? classDecl.baseClass.getInternalName() : Type.getInternalName(Object.class);
    cv.visit(V11, ACC_PUBLIC, internalName, null, baseName, new String[] { Type.getInternalName(JactlObject.class) });
    cv.visitSource(sourceName, null);

    classInit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    classInit.visitCode();

    // Default constructor
    constructor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V", false);

    var fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC, Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACTL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACTL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, "Ljava/util/Map;");
    fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC, Utils.JACTL_STATIC_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACTL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACTL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, "Ljava/util/Map;");

    // Add instance method and static for retrieving map of static Jactl methods
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_STATIC_METHODS_GETTER,
                                      "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, Utils.JACTL_STATIC_METHODS_STATIC_GETTER,
                                      "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // Add method for retrieving map of fields and methods
    mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_FIELDS_METHODS_GETTER,
                        "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    if (classDecl.baseClass != null) {
      // Add all fields/methods from parent class to this one
      classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, MAP.descriptor());
      classInit.visitFieldInsn(GETSTATIC, classDecl.baseClass.getInternalName(), Utils.JACTL_STATIC_METHODS_MAP, MAP.descriptor());
      classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "putAll", "(Ljava/util/Map;)V", true);
      classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor());
      classInit.visitFieldInsn(GETSTATIC, classDecl.baseClass.getInternalName(), Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor());
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
    compileJsonFunctions();
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

    if (debug(3)) {
      classInit.visitEnd();
      cv.visitEnd();
    }

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
   * @param funDecl  the Expr.FunDecl for the method
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
        paramTypes.addAll(Collections.nCopies(wrapper.heapLocals.size(), HeapLocal.class));
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
        String mapName = funDecl.isStatic() ? Utils.JACTL_STATIC_METHODS_MAP : Utils.JACTL_FIELDS_METHODS_MAP;
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
    // Wrapper methods can't be final
    assert !method.functionDescriptor.isFinal || !method.isWrapper;

    // Parameter types: heapLocals + params
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | (method.isStatic() ? ACC_STATIC : 0) | (method.functionDescriptor.isFinal ? ACC_FINAL : 0),
                                      methodName,
                                      MethodCompiler.getMethodDescriptor(method),
                                      null, null);
    mv.visitCode();

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
      Utils.loadStoredValue(mv, continuationSlot, slot, HEAPLOCAL);
    }

    mv.visitVarInsn(ALOAD, continuationSlot);   // Continuation
    if (funDecl.functionDescriptor.needsLocation) {
      Utils.loadStoredValue(mv, continuationSlot, slot++, STRING);
      Utils.loadStoredValue(mv, continuationSlot, slot++, INT);
    }

    // Load parameter values from our long[] or Object[] in the Continuation.
    // The index into the long[]/Object[] will be paramIdx + heapLocals.size() since the parameters come immediately
    // after the heaplocals (and the Continuation that we don't store).
    for (int i = 0; i < funDecl.parameters.size(); i++, slot++) {
      // If we are a scriptMain and slot is for our globals (globalsVar() will return -1 if not scriptMain)
      if (slot == funDecl.globalsVar()) {
        Utils.loadConst(mv, null);       // no need to restore globals since we previously stored it in a field
        continue;
      }
      final var  declExpr = funDecl.parameters.get(i).declExpr;
      JactlType type     = declExpr.isPassedAsHeapLocal ? HEAPLOCAL : declExpr.type;
      Utils.loadStoredValue(mv, continuationSlot, slot, type);
      if (type.is(LONG, JactlType.DOUBLE)) {
        slot++;
      }
    }

    // INVOKESTATIC or INVOKESPECIAL to make sure we don't get overridden method (if child class has overridden us).
    mv.visitMethodInsn(funDecl.isStatic() ? INVOKESTATIC : INVOKESPECIAL, internalName, funDecl.functionDescriptor.implementingMethod, MethodCompiler.getMethodDescriptor(funDecl), false);
    Utils.box(mv, funDecl.returnType);   // Box if primitive
    mv.visitInsn(ARETURN);               // Always return Object from continuation wrapper

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  public void defineField(String name, JactlType type) {
    var fieldVisitor = cv.visitField(ACC_PUBLIC, name, type.descriptor(), null, null);
    fieldVisitor.visitEnd();

    // Add code to class initialiser to find a VarHandle and add to our static fieldsAndMethods map
    classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor());
    Utils.loadConst(classInit, name);

    classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
    classInit.visitLdcInsn(name);
    classInit.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);

    classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
  }

  boolean debug() {
    return context.debugLevel > 0;
  }
  boolean debug(int level) {
    return context.debugLevel >= level;
  }

  boolean annotate() {
    return context.debugLevel == 2 || context.debugLevel == 4;
  }

  //////////////////////////////////////

  private static class JactlClassWriter extends ClassWriter {

    JactlContext context;

    public JactlClassWriter(int flags, JactlContext context) {
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
      String jactlObjectClass = Type.getInternalName(JactlObject.class);
      if (type1.startsWith(context.javaPackage) && type2.equals(jactlObjectClass)) {
        return jactlObjectClass;
      }
      if (type2.startsWith(context.javaPackage) && type1.equals(jactlObjectClass)) {
        return jactlObjectClass;
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
              return jactlObjectClass;
            }
            else {
              do {
                clss1 = clss1.getBaseClass();
              }
              while (clss1 != null && !clss1.isAssignableFrom(clss2));

              return clss1 == null ? jactlObjectClass : clss1.getInternalName();
            }
          }
        }
        else
        if (type1.startsWith(context.javaPackage) || type2.startsWith(context.javaPackage)) {
          // Common ancestor or Jactl class and non-Jactl class is Object but I am not sure
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

  private void compileJsonFunctions() {
    compileToJsonFunction();
    compileReadJsonFunction();
  }

  private void compileToJsonFunction() {
    final int THIS_SLOT      = 0;
    final int BUFF_SLOT      = 1;
    final int FIRST_SLOT     = 2;
    final int ARR_SLOT_START = 3;
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_WRITE_JSON, Type.getMethodDescriptor(Type.getType(void.class), Type.getType(JsonEncoder.class)),
                                      null, null);
    BiConsumer<String, Class> invoke = (method,type) -> mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonEncoder.class), method,
                                                                           Type.getMethodDescriptor(Type.getType(void.class), Type.getType(type)),
                                                                           false);

    mv.visitVarInsn(ALOAD, BUFF_SLOT);
    Utils.loadConst(mv, '{');
    invoke.accept("writeByte", char.class);
    boolean notFirstOutput = false;
    boolean first = true;
    boolean usingFirstSlot = false;
    for (var iter = classDescriptor.getAllFields().iterator(); iter.hasNext(); ) {
      var       f     = iter.next();
      String    field = f.getKey();
      JactlType type  = f.getValue();
      if (!type.isPrimitive()) {
        if (first) {
          // If we are first, and we are not a primitive then we need to initialise flag to know whether we have
          // output any field yet or not
          Utils.loadConst(mv, 0);         // 0 - first, non-zero for anything else
          mv.visitVarInsn(ISTORE, FIRST_SLOT);
          usingFirstSlot = true;
        }
      }

      if (notFirstOutput) {
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        Utils.loadConst(mv, ',');
        invoke.accept("writeByte", char.class);
      }
      else
      if (usingFirstSlot) {
        if (!first) {
          Label isFirst = new Label();
          mv.visitVarInsn(ILOAD, FIRST_SLOT);
          mv.visitJumpInsn(IFEQ, isFirst);
          mv.visitVarInsn(ALOAD, BUFF_SLOT);
          Utils.loadConst(mv, ',');
          invoke.accept("writeByte", char.class);
          mv.visitLabel(isFirst);
        }
        mv.visitIincInsn(FIRST_SLOT, 1);
      }

      if (type.isPrimitive()) {
        notFirstOutput = true;   // Definitely know that we have output something since primitives can't be null
      }

      mv.visitVarInsn(ALOAD, BUFF_SLOT);
      Utils.loadConst(mv, field);
      invoke.accept("writeString", String.class);

      mv.visitVarInsn(ALOAD, BUFF_SLOT);
      Utils.loadConst(mv, ':');
      invoke.accept("writeByte", char.class);
      mv.visitVarInsn(ALOAD, BUFF_SLOT);
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitFieldInsn(GETFIELD, internalName, field, type.descriptor());
      writeField(mv, type, invoke, ARR_SLOT_START);
      first = false;
    }
    mv.visitVarInsn(ALOAD, BUFF_SLOT);
    Utils.loadConst(mv, '}');
    invoke.accept("writeByte", char.class);
    mv.visitInsn(RETURN);

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  // Expect on stack: ...JsonEncoder,fieldValue
  private void writeField(MethodVisitor mv, JactlType type, BiConsumer<String, Class> invoke, int arraySlots) {
    final int THIS_SLOT    = 0;
    final int BUFF_SLOT    = 1;
    final int FIRST_SLOT   = 2;
    final int ARR_IDX_SLOT = arraySlots;
    final int ARR_SLOT     = arraySlots + 1;
    switch (type.getType()) {
      case BOOLEAN: invoke.accept("writeBoolean", boolean.class);   break;
      case INT:     invoke.accept("writeInt", int.class);           break;
      case LONG:    invoke.accept("writeLong", long.class);         break;
      case DOUBLE:  invoke.accept("writeDouble", double.class);     break;
      case STRING: {
        invoke.accept("writeString", String.class);
        break;
      }
      case DECIMAL: {
        invoke.accept("writeDecimal", BigDecimal.class);
        break;
      }
      case INSTANCE: {
        Label NULL_VAL = new Label();
        Label NEXT     = new Label();
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, NULL_VAL);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(JactlObject.class), Utils.JACTL_WRITE_JSON,
                           Type.getMethodDescriptor(Type.getType(void.class), Type.getType(JsonEncoder.class)),
                           true);
        mv.visitJumpInsn(GOTO, NEXT);
NULL_VAL: mv.visitLabel(NULL_VAL);
        mv.visitInsn(POP);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonEncoder.class), "writeNull",
                           Type.getMethodDescriptor(Type.getType(void.class)), false);
NEXT:   mv.visitLabel(NEXT);
        break;
      }
      case ARRAY: {
        Label NULL_VAL = new Label();
        Label NEXT     = new Label();
        mv.visitVarInsn(ASTORE, ARR_SLOT);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, ARR_SLOT);
        mv.visitJumpInsn(IFNULL, NULL_VAL);
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        Utils.loadConst(mv, '[');     // JsonEncoder is currently on stack
        invoke.accept("writeByte", char.class);
        Label LOOP     = new Label();
        Label END_LOOP = new Label();
        Utils.loadConst(mv,0);
        mv.visitVarInsn(ISTORE, ARR_IDX_SLOT);
LOOP:   mv.visitLabel(LOOP);
        mv.visitVarInsn(ALOAD, ARR_SLOT);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitVarInsn(ILOAD, ARR_IDX_SLOT);
        mv.visitJumpInsn(IF_ICMPLE, END_LOOP);
        mv.visitVarInsn(ILOAD, ARR_IDX_SLOT);
        Label FIRST = new Label();
        mv.visitJumpInsn(IFEQ, FIRST);
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        Utils.loadConst(mv, ',');
        invoke.accept("writeByte", char.class);
FIRST:  mv.visitLabel(FIRST);
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        mv.visitVarInsn(ALOAD, ARR_SLOT);
        mv.visitVarInsn(ILOAD, ARR_IDX_SLOT);
        Utils.loadArrayElement(mv, type.getArrayType());
        writeField(mv, type.getArrayType(), invoke, arraySlots + 2);
        mv.visitIincInsn(ARR_IDX_SLOT, 1);
        mv.visitJumpInsn(GOTO, LOOP);
END_LOOP: mv.visitLabel(END_LOOP);
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        Utils.loadConst(mv, ']');
        invoke.accept("writeByte", char.class);
        mv.visitJumpInsn(GOTO, NEXT);
NULL_VAL: mv.visitLabel(NULL_VAL);
        mv.visitVarInsn(ALOAD, BUFF_SLOT);
        Utils.loadConst(mv, "null");
        invoke.accept("writeBareString", String.class);
NEXT:   mv.visitLabel(NEXT);
        break;
      }
      default:
        invoke.accept("writeObj", Object.class);
        break;
    }
  }

  private void compileReadJsonFunction() {
    final int THIS_SLOT      = 0;
    final int DECODER_SLOT   = 1;
    final int FIRST_SLOT     = 2;   // Records whether we are decoding first field
    final int TMP_OBJ        = 3;
    final int TMP_LONG       = 4;  // two slots
    final int DUP_FLAGS      = 6;  // two slots per flag
    // Need for array and array index. If multi-dimensions we use extra two slots for each dimension
    final int ARR_SLOTS      = DUP_FLAGS + 2 + classDecl.fields.size() / 64;

    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_READ_JSON, Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(JsonDecoder.class)),
                                      null, null);

    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    Utils.loadConst(mv, '{');
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "expectOrNull", Type.getMethodDescriptor(Type.getType(boolean.class), Type.getType(char.class)), false);
    Label BRACE = new Label();
    mv.visitJumpInsn(IFNE, BRACE);
    // Was null so return null value
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ARETURN);

    // We need to build a list of field hashCodes in sort order with each entry being a list of fields that map
    // to that hash. This will be used to build the lookupTable for our switch on field name.
    var fieldNames = classDecl.fields.stream()
                                     .map(f -> f.name.getStringValue())
                                     .sorted(Comparator.comparingInt(Object::hashCode))
                                     .collect(Collectors.toList());
    var hashCodeList = new ArrayList<Pair<Integer,List<String>>>();
    int currentHash = 0;
    Pair<Integer,List<String>> entry = null;
    for (int i = 0; i < fieldNames.size(); i++) {
      String fieldName = fieldNames.get(i);
      if (i == 0 || fieldName.hashCode() != currentHash) {
        entry = Pair.create(fieldName.hashCode(), new ArrayList<>());
        hashCodeList.add(entry);
        currentHash = fieldName.hashCode();
      }
      entry.second.add(fieldName);
    }
    var fieldLabels = IntStream.range(0,hashCodeList.size()).mapToObj(i -> new Label()).toArray(Label[]::new);

BRACE: mv.visitLabel(BRACE);
    int numFlagSlots = fieldNames.size() / 64 + 1;
    // Initialise flags we use to detect duplicate fields to 0
    IntStream.range(0, numFlagSlots).forEach(i -> {
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, DUP_FLAGS + i * 2);
    });

    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, FIRST_SLOT);
    Label LOOP     = new Label();
    Label NOT_END  = new Label();
    Label END_LOOP = new Label();
    Label FIRST    = new Label();
    Label ERROR    = new Label();
    Label COMMA    = new Label();
    Label QUOTE    = new Label();

LOOP: mv.visitLabel(LOOP);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "nextChar", Type.getMethodDescriptor(Type.getType(char.class)), false);
    mv.visitInsn(DUP);
    Utils.loadConst(mv, '}');
    mv.visitJumpInsn(IF_ICMPNE, NOT_END);
    mv.visitInsn(POP);
    mv.visitJumpInsn(GOTO, END_LOOP);

NOT_END: mv.visitLabel(NOT_END);
    mv.visitVarInsn(ILOAD, FIRST_SLOT);
    mv.visitJumpInsn(IFEQ, FIRST);
    mv.visitInsn(DUP);
    Utils.loadConst(mv, ',');
    mv.visitJumpInsn(IF_ICMPEQ, COMMA);
    Utils.loadConst(mv, "Expecting ',' or '}' but got ");

ERROR: mv.visitLabel(ERROR);
    // Expect on stack:  ...char,errMsg
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitInsn(DUP_X2);
    mv.visitInsn(POP);
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "error",
                       Type.getMethodDescriptor(Type.getType(void.class), Type.getType(String.class), Type.getType(char.class)), false);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ATHROW);

COMMA: mv.visitLabel(COMMA);
    mv.visitInsn(POP);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "nextChar", Type.getMethodDescriptor(Type.getType(char.class)), false);
FIRST: mv.visitLabel(FIRST);
    mv.visitInsn(DUP);
    Utils.loadConst(mv, '"');
    mv.visitJumpInsn(IF_ICMPEQ, QUOTE);
    Utils.loadConst(mv, "Expecting '\"' but got ");
    mv.visitJumpInsn(GOTO, ERROR);
QUOTE: mv.visitLabel(QUOTE);
    mv.visitInsn(POP);
    mv.visitIincInsn(FIRST_SLOT, 1);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "decodeString", Type.getMethodDescriptor(Type.getType(String.class)), false);
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);

    Label   DEFAULT_LABEL = new Label();
    Label   DUP_FOUND     = new Label();

    mv.visitLookupSwitchInsn(DEFAULT_LABEL, hashCodeList.stream().mapToInt(h -> h.first).toArray(), fieldLabels);
    int fidx = -1;
    for (int h = 0; h < hashCodeList.size(); h++) {
      mv.visitLabel(fieldLabels[h]);
      var     hashEntry = hashCodeList.get(h).second;
      Label   NEXT      = null;
      for (int hashIdx = 0; hashIdx < hashEntry.size(); hashIdx++) {
        if (NEXT != null && NEXT != DEFAULT_LABEL) {
NEXT:     mv.visitLabel(NEXT);
        }
        NEXT = hashIdx == hashEntry.size() - 1 ? DEFAULT_LABEL : new Label();
        fidx++;
        String fieldName = hashEntry.get(hashIdx);
        mv.visitInsn(DUP);
        Utils.loadConst(mv, fieldName);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(IFEQ, NEXT);    // No match even though hashcode matched
        mv.visitInsn(POP);
        // Make sure we haven't already decoded a value for this field by storing a flag in a local long of 64 flags, one
        // per field (we overflow to multiple 64 bit longs if necessary)
        int shift = fidx % 64;                      // which bit to test/set
        int flag  = DUP_FLAGS + (fidx / 64) * 2;    // which of our longs to use
        Utils.loadConst(mv, 1L << shift);
        mv.visitVarInsn(LLOAD, flag);
        mv.visitInsn(LAND);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        Label NO_DUP = new Label();
        mv.visitJumpInsn(IFEQ, NO_DUP);
        Utils.loadConst(mv, fieldName);
        mv.visitJumpInsn(GOTO, DUP_FOUND);
NO_DUP: mv.visitLabel(NO_DUP);
        Utils.loadConst(mv, 1L << shift);
        mv.visitVarInsn(LLOAD, flag);
        mv.visitInsn(LOR);
        mv.visitVarInsn(LSTORE, flag);          // set flag to remember we have seen this field
        mv.visitVarInsn(ALOAD, DECODER_SLOT);
        Utils.loadConst(mv, ':');
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "expect", Type.getMethodDescriptor(Type.getType(void.class), Type.getType(char.class)), false);
        mv.visitVarInsn(ALOAD, THIS_SLOT);
        var type = classDecl.fieldVars.get(fieldName).type;
        readJsonField(mv, type, fieldName, ERROR, DECODER_SLOT, TMP_OBJ, ARR_SLOTS);
        mv.visitFieldInsn(PUTFIELD, internalName, fieldName, type.descriptor());
        mv.visitJumpInsn(GOTO, LOOP);
      }
    }
DEFAULT_LABEL: mv.visitLabel(DEFAULT_LABEL);
    Utils.loadConst(mv, " field in JSON data does not exist in " + className);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(String.class), "concat", Type.getMethodDescriptor(Type.getType(String.class), Type.getType(String.class)), false);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "error",
                       Type.getMethodDescriptor(Type.getType(void.class), Type.getType(String.class)), false);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ATHROW);

DUP_FOUND: mv.visitLabel(DUP_FOUND);
    Utils.loadConst(mv, "Field '");
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(String.class), "concat", Type.getMethodDescriptor(Type.getType(String.class), Type.getType(String.class)), false);
    Utils.loadConst(mv, "' for class " + className + " appears multiple times in JSON data");
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(String.class), "concat", Type.getMethodDescriptor(Type.getType(String.class), Type.getType(String.class)), false);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "error",
                       Type.getMethodDescriptor(Type.getType(void.class), Type.getType(String.class)), false);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ATHROW);

END_LOOP: mv.visitLabel(END_LOOP);

    // Generate flags which are set for mandatory fields and then check that all mandatory fields have a value
    long[] mandatoryFlags = new long[fieldNames.size() / 64 + 1];
    int mandatoryCount = 0;
    long[] optionalFlags  = new long[fieldNames.size() / 64 + 1];
    int optionalCount = 0;
    for (int i = 0; i < fieldNames.size(); i++) {
      int          m           = i / 64;
      int          b           = i % 64;
      String       fieldName   = fieldNames.get(i);
      Expr.VarDecl field       = classDecl.fieldVars.get(fieldName);
      boolean      isMandatory = field.initialiser == null;
      if (isMandatory) {
        mandatoryFlags[m] |= (1 << b);
        mandatoryCount++;
      }
      else {
        optionalFlags[m] |= (1 << b);
        optionalCount++;
      }
    }

    if (mandatoryCount > 0) {
      Label MISSING_FIELDS = new Label();
      for (int i = 0; i < mandatoryFlags.length; i++) {
        if (mandatoryFlags[i] == 0) {
          continue;
        }
        Utils.loadConst(mv, i);
        mv.visitVarInsn(LLOAD, DUP_FLAGS + i * 2);
        Utils.loadConst(mv, mandatoryFlags[i]);
        mv.visitInsn(LAND);
        Utils.loadConst(mv, mandatoryFlags[i]);
        mv.visitInsn(LXOR);
        mv.visitInsn(DUP2);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFNE, MISSING_FIELDS);
        mv.visitInsn(POP2); // xor value
        mv.visitInsn(POP);  // i
      }

      Label MISSING_FLAGS = new Label();
      mv.visitJumpInsn(GOTO, MISSING_FLAGS);
MISSING_FIELDS: mv.visitLabel(MISSING_FIELDS);
      mv.visitVarInsn(LSTORE, TMP_LONG);
      mv.visitVarInsn(ALOAD, DECODER_SLOT);
      mv.visitInsn(SWAP);         // swap with i which is still on stack
      mv.visitVarInsn(LLOAD, TMP_LONG);
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "missingFields",
                         Type.getMethodDescriptor(Type.getType(void.class), Type.getType(int.class), Type.getType(long.class), Type.getType(JactlObject.class)),
                         false);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ATHROW);

MISSING_FLAGS: mv.visitLabel(MISSING_FLAGS);
    }

    if (optionalCount > 0) {
      // Work out what optional fields are missing and return either:
      //  - single long with flags set for missing fields (if <= 64 fields), or
      //  - array of longs with flags (if > 64 fields)
      // If there are no missing fields then we return 0.
      // NOTE: We reserve a bit in the flags for each field whether optional or not.
      //       We could be smarter and only use bits for optional fields but that would
      //       require generating code to iterate through the field flags to then set
      //       these other flags. Easier just to return the field flags we have.
      Label MISSING_OPTIONAL = new Label();
      for (int flag = 0; flag < optionalFlags.length; flag++) {
        if (optionalFlags[flag] == 0) {
          continue;
        }
        mv.visitVarInsn(LLOAD, DUP_FLAGS + flag*2);
        Utils.loadConst(mv, optionalFlags[flag]);
        mv.visitInsn(LAND);
        Utils.loadConst(mv, optionalFlags[flag]);
        mv.visitInsn(LXOR);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFNE, MISSING_OPTIONAL);
      }

      // Return 0 since there are no missing fields
      mv.visitInsn(LCONST_0);
      Utils.box(mv, LONG);
      Label END = new Label();
      mv.visitJumpInsn(GOTO, END);

MISSING_OPTIONAL: mv.visitLabel(MISSING_OPTIONAL);
      // Return flags for missing optional fields
      if (optionalFlags.length == 1) {
        // Only one int to return
        mv.visitVarInsn(LLOAD, DUP_FLAGS);
        Utils.loadConst(mv, optionalFlags[0]);
        mv.visitInsn(LAND);
        Utils.loadConst(mv, optionalFlags[0]);
        mv.visitInsn(LXOR);
        Utils.box(mv, LONG);
      }
      else {
        // Return array of longs
        Utils.loadConst(mv, optionalFlags.length);
        mv.visitIntInsn(NEWARRAY, T_LONG);
        for (int flag = 0; flag < optionalFlags.length; flag++) {
          mv.visitInsn(DUP);
          Utils.loadConst(mv, flag);
          if (optionalFlags[flag] == 0) {
            Utils.loadConst(mv, 0L);
          }
          else {
            mv.visitVarInsn(LLOAD, DUP_FLAGS + flag*2);
            Utils.loadConst(mv, optionalFlags[flag]);
            mv.visitInsn(LAND);
            Utils.loadConst(mv, optionalFlags[flag]);
            mv.visitInsn(LXOR);
          }
          mv.visitInsn(LASTORE);
        }
      }

END: mv.visitLabel(END);
    }
    else {
      // No flags to return
      mv.visitInsn(LCONST_0);
      Utils.box(mv, LONG);
    }

    mv.visitInsn(ARETURN);

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void readJsonField(MethodVisitor mv, JactlType type, String fieldName, Label ERROR, int DECODER_SLOT, int TMP_OBJ, int ARR_SLOTS) {
    String typeName = type.getType().toString().charAt(0) + type.getType().toString().toLowerCase().substring(1);
    switch (type.getType()) {
      case BOOLEAN: case INT: case LONG: case DOUBLE: case DECIMAL: case STRING: case MAP: case LIST: case ANY:
        mv.visitVarInsn(ALOAD, DECODER_SLOT);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class),
                           type.is(ANY) ? "_decode" : "get" + typeName,
                           Type.getMethodDescriptor(type.descriptorType()), false);
        break;
      case INSTANCE:
        mv.visitVarInsn(ALOAD, DECODER_SLOT);
        mv.visitTypeInsn(NEW, type.getInternalName());
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, type.getInternalName(), "<init>", "()V", false);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, TMP_OBJ);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(JactlObject.class), Utils.JACTL_READ_JSON,
                           Type.getMethodDescriptor(Type.getType(Object.class),
                                                    Type.getType(JsonDecoder.class)),
                           true);
        mv.visitInsn(DUP);
        Label NULL_CHILD = new Label();
        mv.visitJumpInsn(IFNULL, NULL_CHILD);
        mv.visitVarInsn(ALOAD, TMP_OBJ);
        mv.visitInsn(SWAP);
        boolean async = type.getClassDescriptor().getMethod(Utils.JACTL_INIT_MISSING).isAsync;
        // Note we don't actually support async behaviour (it will be detected in JsonDecoder.decodeJactlObj())
        // but we need to pass in a null continuation just in case. If a Continuation is thrown it will be
        // converted into a RuntimeError.
        if (async) {
          mv.visitInsn(ACONST_NULL);
          mv.visitInsn(SWAP);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, type.getInternalName(), Utils.JACTL_INIT_MISSING,
                           async ? Type.getMethodDescriptor(type.descriptorType(), Type.getType(Continuation.class), Type.getType(Object.class))
                                 : Type.getMethodDescriptor(type.descriptorType(), Type.getType(Object.class)),
                           false);
NULL_CHILD: mv.visitLabel(NULL_CHILD);
        Utils.checkCast(mv, type);
        break;
      case ARRAY:
        readJsonArray(mv, type, fieldName, ERROR, DECODER_SLOT, TMP_OBJ, ARR_SLOTS);
        break;
      case FUNCTION:
        mv.visitVarInsn(ALOAD, DECODER_SLOT);
        Utils.loadConst(mv, "Field " + fieldName + " of type function/closure cannot be instantiated from json");
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "error",
                           Type.getMethodDescriptor(Type.getType(void.class), Type.getType(String.class)), false);
        mv.visitInsn(ACONST_NULL);
        break;
      default:
        throw new IllegalStateException("Internal error: invalid type for a field: " + type.getType());
    }
  }

  private void readJsonArray(MethodVisitor mv, JactlType type, String fieldName, Label ERROR, int DECODER_SLOT, int TMP_OBJ, int ARR_SLOTS) {
    final int INITIAL_ARR_SIZE = 16;
    final int ARR_SLOT = ARR_SLOTS;
    final int ARR_IDX  = ARR_SLOTS + 1;

    Label BRACKET = new Label();
    Label FINISH_LIST = new Label();
    Label END_LOOP    = new Label();

    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    Utils.loadConst(mv, '[');
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "expectOrNull", Type.getMethodDescriptor(Type.getType(boolean.class), Type.getType(char.class)), false);
    mv.visitJumpInsn(IFNE, BRACKET);
    // Was null so return null value
    mv.visitInsn(ACONST_NULL);
    mv.visitJumpInsn(GOTO, FINISH_LIST);

BRACKET: mv.visitLabel(BRACKET);
    Utils.loadConst(mv, INITIAL_ARR_SIZE);
    Utils.newArray(mv, type, 1);
    mv.visitVarInsn(ASTORE, ARR_SLOT);
    Utils.loadConst(mv, 0);
    mv.visitVarInsn(ISTORE, ARR_IDX);

    Label LOOP        = new Label();
    Label READ_FIELD  = new Label();
    Label FIRST_FIELD = new Label();
    Label NOT_END     = new Label();
LOOP: mv.visitLabel(LOOP);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "skipWhitespace", Type.getMethodDescriptor(Type.getType(char.class)), false);
    mv.visitInsn(DUP);
    Utils.loadConst(mv, ']');
    mv.visitJumpInsn(IF_ICMPNE, NOT_END);
    mv.visitInsn(POP);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "nextChar", Type.getMethodDescriptor(Type.getType(char.class)), false);
    mv.visitInsn(POP);
    mv.visitJumpInsn(GOTO, END_LOOP);

NOT_END: mv.visitLabel(NOT_END);
    mv.visitVarInsn(ILOAD, ARR_IDX);
    mv.visitJumpInsn(IFEQ, FIRST_FIELD);
    mv.visitInsn(DUP);
    Utils.loadConst(mv, ',');
    mv.visitJumpInsn(IF_ICMPEQ, READ_FIELD);
    Utils.loadConst(mv, "Expecting ',' or ']' but got ");
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitInsn(DUP_X2);
    mv.visitInsn(POP);
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "error",
                       Type.getMethodDescriptor(Type.getType(void.class), Type.getType(String.class), Type.getType(char.class)), false);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ATHROW);

READ_FIELD: mv.visitLabel(READ_FIELD);
    mv.visitInsn(POP);
    mv.visitVarInsn(ALOAD, DECODER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(JsonDecoder.class), "nextChar", Type.getMethodDescriptor(Type.getType(char.class)), false);

FIRST_FIELD: mv.visitLabel(FIRST_FIELD);
    mv.visitInsn(POP);
    // Check if array is big enough
    Label BIG_ENOUGH = new Label();
    mv.visitVarInsn(ILOAD, ARR_IDX);
    mv.visitVarInsn(ALOAD, ARR_SLOT);
    mv.visitInsn(ARRAYLENGTH);
    mv.visitJumpInsn(IF_ICMPLT, BIG_ENOUGH);

    // Grow array by doubling in size
    mv.visitVarInsn(ILOAD, ARR_IDX);
    Utils.loadConst(mv,2);
    mv.visitInsn(IMUL);

    Runnable copyArray = () -> {
      // Expect on stack: ...arraySize
      Utils.newArray(mv, type, 1);
      mv.visitVarInsn(ALOAD, ARR_SLOT);
      mv.visitInsn(SWAP);
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, ARR_SLOT);
      Utils.loadConst(mv, 0);
      mv.visitInsn(SWAP);
      Utils.loadConst(mv, 0);
      mv.visitVarInsn(ILOAD, ARR_IDX);
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
    };
    copyArray.run();

BIG_ENOUGH: mv.visitLabel(BIG_ENOUGH);
    mv.visitVarInsn(ALOAD, ARR_SLOT);
    mv.visitVarInsn(ILOAD, ARR_IDX);
    readJsonField(mv, type.getArrayType(), fieldName, ERROR, DECODER_SLOT, TMP_OBJ, ARR_SLOTS + 2);
    Utils.storeArrayElement(mv, type.getArrayType());
    mv.visitIincInsn(ARR_IDX, 1);
    mv.visitJumpInsn(GOTO, LOOP);

END_LOOP: mv.visitLabel(END_LOOP);
    Label NO_COPY_NEEDED = new Label();
    // Copy array into one of exact size needed
    mv.visitVarInsn(ILOAD, ARR_IDX);
    mv.visitVarInsn(ALOAD, ARR_SLOT);
    mv.visitInsn(ARRAYLENGTH);
    mv.visitJumpInsn(IF_ICMPEQ, NO_COPY_NEEDED);
    mv.visitVarInsn(ILOAD, ARR_IDX);
    copyArray.run();

NO_COPY_NEEDED: mv.visitLabel(NO_COPY_NEEDED);
    mv.visitVarInsn(ALOAD, ARR_SLOT);

FINISH_LIST: mv.visitLabel(FINISH_LIST);
  }
}
