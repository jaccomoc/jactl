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
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.jactl.JactlType.*;
import static java.util.stream.Collectors.groupingBy;
import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  final JactlContext        context;
  final String              internalName;
  final String              source;
  final String              pkg;
  final String              className;
  final Stmt.ClassDecl      classDecl;
  final String              sourceName;      // Name of source file
  protected String          internalBaseName;
  protected ClassVisitor    cv;
  protected ClassWriter     cw;
  protected MethodVisitor   constructor;
  protected MethodVisitor   classInit;   // MethodVisitor for static class initialiser
  protected ClassDescriptor classDescriptor;
  protected Class           compiledClass;

  private   Label           classInitTryStart   = new Label();
  private   Label           classInitTryEnd     = new Label();
  private   Label           classInitCatchBlock = new Label();

  final         Map<Object,String> classConstantNames = new HashMap<>();
  private       int                classConstantCnt   = 0;

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

    internalBaseName = classDecl.baseClass != null ? classDecl.baseClass.getInternalName() : null;
    if (classDecl.isScriptClass()) {
      internalBaseName = Type.getInternalName(JactlScriptObject.class);
    }
    String superName = internalBaseName == null ? Type.getInternalName(Object.class) : internalBaseName;

    // Supporting Java 8 at the moment so passing V1_8. Change to later version once we no longe support Java 8.
    cv.visit(V1_8, ACC_PUBLIC, internalName, null,
             superName, new String[] {Type.getInternalName(JactlObject.class) });
    cv.visitSource(sourceName, null);

    classInit = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    classInit.visitCode();

    // Default constructor
    constructor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);

    FieldVisitor fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACTL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACTL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, "Ljava/util/Map;");
    fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, Utils.JACTL_STATIC_METHODS_MAP, MAP.descriptor(), null, null);
    fieldVisitor.visitEnd();
    classInit.visitTypeInsn(NEW, Type.getInternalName(Utils.JACTL_MAP_TYPE));
    classInit.visitInsn(DUP);
    classInit.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Utils.JACTL_MAP_TYPE), "<init>", "()V", false);
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    fieldVisitor = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, Utils.JACTL_PRETTY_NAME_FIELD, Type.getDescriptor(String.class), null, null);
    fieldVisitor.visitEnd();
    classInit.visitLdcInsn(classDescriptor.getPrettyName());
    classInit.visitFieldInsn(PUTSTATIC, internalName, Utils.JACTL_PRETTY_NAME_FIELD, Type.getDescriptor(String.class));

    // Add instance method and static for retrieving map of static Jactl methods
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_STATIC_METHODS_GETTER,
                                      "()Ljava/util/Map;", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_STATIC_METHODS_MAP, "Ljava/util/Map;");
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, Utils.JACTL_STATIC_METHODS_STATIC_GETTER,
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

    // Create class static fields for all list/map constants
    classDecl.classConstants.forEach(c -> {
      assert c instanceof List || c instanceof Map;
      String       fieldName  = Utils.JACTL_PREFIX + "constant_" + classConstantCnt++;
      String       descriptor = c instanceof List ? LIST.descriptor() : MAP.descriptor();
      FieldVisitor fv         = cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, fieldName, descriptor, null, null);
      fv.visitEnd();
      loadConstant(c);
      classInit.visitFieldInsn(PUTSTATIC, internalName, fieldName, descriptor);
      classConstantNames.put(c, fieldName);
    });
  }

  public void compileClass() {
    compileSingleClass();
    compileInnerClasses();
  }

  protected void compileSingleClass() {
    classDecl.fieldVars.forEach((field,varDecl) -> defineField(field, varDecl.type));
    classDecl.methods.forEach(method -> compileMethod(method.declExpr));
    compileJactlObjectFunctions();
    finishClassCompile();
  }

  protected void compileInnerClasses() {
    List<Stmt.ClassDecl> orderedInnerClasses = allInnerClasses(classDecl).sorted((a, b) -> Integer.compare(ancestorCount(a), ancestorCount(b))).collect(Collectors.toList());
    orderedInnerClasses.forEach(clss -> {
      if (clss != classDecl) {
        ClassCompiler compiler = new ClassCompiler(source, context, pkg, clss, sourceName);
        compiler.compileSingleClass();
      }
    });
  }

  private Stream<Stmt.ClassDecl> allInnerClasses(Stmt.ClassDecl classDecl) {
    return Stream.concat(Stream.of(classDecl), classDecl.innerClasses.stream().flatMap(this::allInnerClasses));
  }

  private int ancestorCount(Stmt.ClassDecl classDecl) {
    int count = 0;
    for (ClassDescriptor baseClass = classDecl.classDescriptor.getBaseClass(); baseClass != null; baseClass = baseClass.getBaseClass()) {
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
      FieldVisitor handleVar          = cv.visitField(ACC_PUBLIC + ACC_STATIC + ACC_FINAL, handleName, Type.getDescriptor(JactlMethodHandle.class), null, null);
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

      // Wrap in a JactlMethodHandle
      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      classInit.visitLdcInsn(handleName);
      classInit.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/JactlMethodHandle", "create", "(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Lio/jactl/runtime/JactlMethodHandle;", false);

      // Store handle in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, handleName, Type.getDescriptor(JactlMethodHandle.class));
    };

    if (!funDecl.isScriptMain) {
      Expr.FunDecl wrapper = funDecl.wrapper;
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

      // Wrap in a JactlMethodHandle
      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      classInit.visitLdcInsn(staticHandleName);
      classInit.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/JactlMethodHandle", "create", "(Ljava/lang/invoke/MethodHandle;Ljava/lang/Class;Ljava/lang/String;)Lio/jactl/runtime/JactlMethodHandle;", false);

      if (!funDecl.isClosure()) {
        classInit.visitInsn(DUP);   // For actual functions/methods we will also store in a map so duplicate first
      }

      // Store in static field
      classInit.visitFieldInsn(PUTSTATIC, internalName, staticHandleName, Type.getDescriptor(JactlMethodHandle.class));

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
    FieldVisitor handleVar          = cv.visitField(ACC_PUBLIC + ACC_STATIC + ACC_FINAL, staticHandleName, Type.getDescriptor(JactlMethodHandle.class), null, null);
    handleVar.visitEnd();
    if (!method.isStatic() && method.isClosure()) {
      // For non-static closures we also create a non-static method handle for the wrapper method
      // that will be bound to the instance. For non-static functions we bind the handle when we
      // compile the declaration and store it in the variable.
      String instanceHandleName = Utils.handleName(wrapperMethodName);
      handleVar = cv.visitField(ACC_PUBLIC, instanceHandleName, Type.getDescriptor(JactlMethodHandle.class), null, null);
      handleVar.visitEnd();

      // Add code to constructor to initialise this handle
      constructor.visitVarInsn(ALOAD, 0);
      constructor.visitFieldInsn(GETSTATIC, internalName, staticHandleName, Type.getDescriptor(JactlMethodHandle.class));
      constructor.visitVarInsn(ALOAD, 0);
      constructor.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/JactlMethodHandle", "bindTo", "(Ljava/lang/Object;)Lio/jactl/runtime/JactlMethodHandle;", false);
      constructor.visitFieldInsn(PUTFIELD, internalName, instanceHandleName, Type.getDescriptor(JactlMethodHandle.class));
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
      final Expr.VarDecl declExpr = funDecl.parameters.get(i).declExpr;
      JactlType          type     = declExpr.isPassedAsHeapLocal ? HEAPLOCAL : declExpr.type;
      Utils.loadStoredValue(mv, continuationSlot, slot, type);
      if (type.is(JactlType.LONG, JactlType.DOUBLE)) {
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
    FieldVisitor fieldVisitor = cv.visitField(ACC_PUBLIC, name, type.descriptor(), null, null);
    fieldVisitor.visitEnd();

    // If not an internal field
    if (!name.startsWith(Utils.JACTL_PREFIX)) {
      // Add code to class initialiser to find a VarHandle and add to our static fieldsAndMethods map
      classInit.visitFieldInsn(GETSTATIC, internalName, Utils.JACTL_FIELDS_METHODS_MAP, MAP.descriptor());
      Utils.loadConst(classInit, name);

      classInit.visitLdcInsn(Type.getType("L" + internalName + ";"));
      classInit.visitLdcInsn(name);
      classInit.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);

      classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
    }
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
      if (type1.startsWith(context.internalJavaPackage) && type2.equals(jactlObjectClass)) {
        return jactlObjectClass;
      }
      if (type2.startsWith(context.internalJavaPackage) && type1.equals(jactlObjectClass)) {
        return jactlObjectClass;
      }
      try {
        if (type1.startsWith(context.internalJavaPackage) && type2.startsWith(context.internalJavaPackage)) {
          ClassDescriptor clss1 = context.getClassDescriptor(type1);
          ClassDescriptor clss2 = context.getClassDescriptor(type2);
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
        if (type1.startsWith(context.internalJavaPackage) || type2.startsWith(context.internalJavaPackage)) {
          // Common ancestor or Jactl class and non-Jactl class is Object, but I am not sure
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

  protected void compileJactlObjectFunctions() {
    compileToJsonFunction();
    compileReadJsonFunction();
    compileCheckpointFunction();
    compileRestoreFunction();
    compileInitNoAsync();
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
    for (Iterator<Map.Entry<String, JactlType>> iter = classDescriptor.getAllFieldsStream().iterator(); iter.hasNext(); ) {
      Map.Entry<String, JactlType> f     = iter.next();
      String                       field = f.getKey();
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
      case BYTE:
      case INT:     invoke.accept("writeInt", int.class);           break;
      case BOOLEAN: invoke.accept("writeBoolean", boolean.class);   break;
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
        Utils.loadArrayElement(mv, type.getArrayElemType());
        writeField(mv, type.getArrayElemType(), invoke, arraySlots + 2);
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

    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_READ_JSON, Type.getMethodDescriptor(Type.getType(long[].class), Type.getType(JsonDecoder.class)),
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
    // We get all fields, including fields from our base classes.
    List<String>               fieldNames   = classDecl.classDescriptor.getAllFieldNames();
    Map<Integer, List<String>> fieldNameMap = fieldNames.stream().collect(groupingBy(Object::hashCode));
    List<Integer>              hashCodeList = fieldNameMap.keySet().stream().sorted().collect(Collectors.toList());
    Label[]       fieldLabels  = IntStream.range(0, fieldNameMap.size()).mapToObj(i -> new Label()).toArray(Label[]::new);

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

    mv.visitLookupSwitchInsn(DEFAULT_LABEL, hashCodeList.stream().mapToInt(i -> i).toArray(), fieldLabels);
    for (int h = 0; h < hashCodeList.size(); h++) {
      mv.visitLabel(fieldLabels[h]);
      List<String> hashFields = fieldNameMap.get(hashCodeList.get(h));   // Fields with same hash
      Label        NEXT       = null;
      for (int hashIdx = 0; hashIdx < hashFields.size(); hashIdx++) {
        if (NEXT != null && NEXT != DEFAULT_LABEL) {
NEXT:     mv.visitLabel(NEXT);
        }
        NEXT = hashIdx == hashFields.size() - 1 ? DEFAULT_LABEL : new Label();
        String fieldName = hashFields.get(hashIdx);
        // Find field index by searching for field name in list of field names
        // (TODO: optimise if necessary to eliminate linear search to improve compile time performance)
        int fidx = fieldNames.indexOf(fieldName);
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
        JactlType type = getField(fieldName);
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
      int     m           = i / 64;
      int     b           = i % 64;
      String  fieldName   = fieldNames.get(i);
      boolean isMandatory = isMandatory(fieldName);
      if (isMandatory) {
        mandatoryFlags[m] |= (1L << b);
        mandatoryCount++;
      }
      else {
        optionalFlags[m] |= (1L << b);
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
      // Work out what optional fields are missing and return array of longs with flags
      // NOTE: We reserve a bit in the flags for each field whether optional or not.
      //       We could be smarter and only use bits for optional fields but that would
      //       require generating code to iterate through the field flags to then set
      //       these other flags. Easier just to return the field flags we have.
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
    else {
      // No flags to return
      mv.visitFieldInsn(GETSTATIC, Type.getInternalName(RuntimeUtils.class), "EMPTY_LONG_ARR", Type.getInternalName(long[].class));
    }

    mv.visitInsn(ARETURN);

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private JactlType getField(String fieldName) {
    return classDescriptor.getField(fieldName);
  }

  private boolean isMandatory(String fieldName) {
    return classDescriptor.getAllMandatoryFields().containsKey(fieldName);
  }

  private void readJsonField(MethodVisitor mv, JactlType type, String fieldName, Label ERROR, int DECODER_SLOT, int TMP_OBJ, int ARR_SLOTS) {
    String typeName = type.getType().toString().charAt(0) + type.getType().toString().toLowerCase().substring(1);
    switch (type.getType()) {
      case BOOLEAN: case BYTE: case INT: case LONG: case DOUBLE: case DECIMAL: case STRING: case MAP: case LIST: case ANY:
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
                           Type.getMethodDescriptor(Type.getType(long[].class),
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
                           async ? Type.getMethodDescriptor(type.descriptorType(), Type.getType(Continuation.class), Type.getType(long[].class))
                                 : Type.getMethodDescriptor(type.descriptorType(), Type.getType(long[].class)),
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
    readJsonField(mv, type.getArrayElemType(), fieldName, ERROR, DECODER_SLOT, TMP_OBJ, ARR_SLOTS + 2);
    Utils.storeArrayElement(mv, type.getArrayElemType());
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

  private static final int VERSION = 1;

  private void compileCheckpointFunction() {
    final int THIS_SLOT         = 0;
    final int CHECKPOINTER_SLOT = 1;
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_CHECKPOINT_FN, Type.getMethodDescriptor(Type.getType(void.class), Type.getType(Checkpointer.class)),
                                      null, null);
    BiConsumer<String, Class> invoke = (method,type) -> mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Checkpointer.class), method,
                                                                           Type.getMethodDescriptor(Type.getType(void.class), Type.getType(type)),
                                                                           false);

    mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
    Utils.loadConst(mv, INSTANCE.getType().ordinal());
    mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Checkpointer", "writeCint", "(I)V", false);
    mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
    Utils.loadConst(mv, internalName);
    mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Checkpointer", "writeObject", "(Ljava/lang/Object;)V", false);
    mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
    Utils.loadConst(mv, VERSION);
    mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Checkpointer", "writeCint", "(I)V", false);

    if (classDecl.isScriptClass()) {
      // Save globals field
      mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitFieldInsn(GETFIELD, internalName, Utils.JACTL_GLOBALS_NAME, MAP.descriptor());
      mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Checkpointer", "writeObject", "(Ljava/lang/Object;)V", false);
    }

    if (internalBaseName != null) {
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
      mv.visitMethodInsn(INVOKESPECIAL, internalBaseName, "_$j$checkpoint", "(Lio/jactl/runtime/Checkpointer;)V", false);
    }

    classDecl.fields.forEach(f -> {
      mv.visitVarInsn(ALOAD, CHECKPOINTER_SLOT);
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitFieldInsn(GETFIELD, internalName, f.name.getStringValue(), f.declExpr.type.descriptor());
      BiConsumer<String,String> writer = (name, type) -> mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Checkpointer", name, "(" + type + ")V", false);
      switch (f.declExpr.type.getType()) {
        case BOOLEAN: writer.accept("writeBoolean", "Z");                      break;
        case BYTE:    writer.accept("writeByte", "B");                         break;
        case INT:     writer.accept("writeCint", "I");                         break;
        case LONG:    writer.accept("writeClong", "J");                        break;
        case DOUBLE:  writer.accept("writeDouble", "D");                       break;
        case DECIMAL: writer.accept("writeDecimal", "Ljava/math/BigDecimal;"); break;
        default:      writer.accept("writeObject", "Ljava/lang/Object;");      break;
      }
    });

    mv.visitInsn(RETURN);

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void compileRestoreFunction() {
    final int THIS_SLOT         = 0;
    final int RESTORER_SLOT     = 1;
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_RESTORE_FN, Type.getMethodDescriptor(Type.getType(void.class), Type.getType(Restorer.class)),
                                      null, null);
    BiConsumer<String, Class> invoke = (method,type) -> mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Restorer.class), method,
                                                                           Type.getMethodDescriptor(Type.getType(void.class), Type.getType(type)),
                                                                           false);

    mv.visitVarInsn(ALOAD, RESTORER_SLOT);
    mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Restorer", "skipType", "()V", false);
    mv.visitVarInsn(ALOAD, RESTORER_SLOT);
    Utils.loadConst(mv, VERSION);
    Utils.loadConst(mv, "Bad version");
    mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Restorer", "expectCint", "(ILjava/lang/String;)V", false);

    BiConsumer<String,String> reader = (name, type) -> mv.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Restorer", name, "()" + type, false);

    if (classDecl.isScriptClass()) {
      // Restore globals
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitVarInsn(ALOAD, RESTORER_SLOT);
      reader.accept("readObject", "Ljava/lang/Object;");
      Utils.checkCast(mv, MAP);
      mv.visitFieldInsn(PUTFIELD, internalName, Utils.JACTL_GLOBALS_NAME, MAP.descriptor());
    }

    if (internalBaseName != null) {
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitVarInsn(ALOAD, RESTORER_SLOT);
      mv.visitMethodInsn(INVOKESPECIAL, internalBaseName, "_$j$restore", "(Lio/jactl/runtime/Restorer;)V", false);
    }

    classDecl.fields.forEach(f -> {
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitVarInsn(ALOAD, RESTORER_SLOT);
      switch (f.declExpr.type.getType()) {
        case BOOLEAN: reader.accept("readBoolean", "Z");                      break;
        case BYTE:    reader.accept("readByte", "B");                         break;
        case INT:     reader.accept("readCint", "I");                         break;
        case LONG:    reader.accept("readClong", "J");                        break;
        case DOUBLE:  reader.accept("readDouble", "D");                       break;
        case DECIMAL: reader.accept("readDecimal", "Ljava/math/BigDecimal;"); break;
        default:
          reader.accept("readObject", "Ljava/lang/Object;");
          Utils.checkCast(mv, f.declExpr.type);
          break;
      }
      mv.visitFieldInsn(PUTFIELD, internalName, f.name.getStringValue(), f.declExpr.type.descriptor());
    });

    mv.visitInsn(RETURN);

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  /**
   * Compile initNoAsync method which initialises an instance created via auto-creation
   * (e.g. x.y.z = ...). Needs to make sure that no Continuation is thrown and needs to
   * make sure that there are no mandatory fields for this class (or parent class).
   */
  private void compileInitNoAsync() {
    final int THIS_SLOT         = 0;
    final int SOURCE_SLOT       = 1;
    final int OFFSET_SLOT       = 2;

    Type classType = Type.getType(classDecl.classDescriptor.getClassType().descriptor());
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, Utils.JACTL_INIT_NOASYNC,
                                      Type.getMethodDescriptor(classType, Type.getType(String.class), Type.getType(int.class)),
                                      null, null);

    mv.visitCode();

    Consumer<String> error = msg -> {
      mv.visitTypeInsn(NEW, Type.getInternalName(RuntimeError.class));
      mv.visitInsn(DUP);
      Utils.loadConst(mv, msg);
      mv.visitVarInsn(ALOAD, SOURCE_SLOT);
      mv.visitVarInsn(ILOAD, OFFSET_SLOT);
      mv.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
      mv.visitInsn(ATHROW);
    };

    if (!classDescriptor.getAllMandatoryFields().isEmpty()) {
      error.accept("Cannot auto-create instance of " + className + " since it has mandatory fields");
    }
    else if (classDescriptor.allFieldsAreDefaults()) {
      // If we all fields are just default values then we have nothing to do
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitInsn(ARETURN);
    }
    else {
      // NOTE: since we are invoking our init wrapper we will also initialise fields in our base class

      // Invoke init wrapper and if it throws a Continuation turn it into an error
      Label blockStart = new Label();
      Label blockEnd   = new Label();
      Label catchLabel = new Label();
      mv.visitTryCatchBlock(blockStart, blockEnd, catchLabel, Type.getInternalName(Continuation.class));
      mv.visitLabel(blockStart);
      mv.visitVarInsn(ALOAD, THIS_SLOT);
      mv.visitInsn(ACONST_NULL);      // Continuation
      mv.visitVarInsn(ALOAD, SOURCE_SLOT);
      mv.visitVarInsn(ILOAD, OFFSET_SLOT);
      mv.visitInsn(ACONST_NULL);
      mv.visitMethodInsn(INVOKEVIRTUAL, internalName, Utils.JACTL_INIT_WRAPPER,
                         Type.getMethodDescriptor(Type.getType(Object.class),
                                                  Type.getType(Continuation.class), Type.getType(String.class),
                                                  Type.getType(int.class), Type.getType(Object[].class)),
                         false);
      mv.visitTypeInsn(CHECKCAST, internalName);
      mv.visitInsn(ARETURN);
      mv.visitLabel(blockEnd);

      mv.visitLabel(catchLabel);
      error.accept("Detected async function invocation during instance auto-creation (which is not allowed)");
    }

    if (debug(3)) {
      mv.visitEnd();
      cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void loadConstant(Object obj) {
    if (obj instanceof List) {
      List list = (List)obj;
      String listClassName = Type.getInternalName(Utils.JACTL_LIST_TYPE);
      classInit.visitTypeInsn(NEW, listClassName);
      classInit.visitInsn(DUP);
      classInit.visitMethodInsn(INVOKESPECIAL, listClassName, "<init>", "()V", false);
      list.forEach(elem -> {
        classInit.visitInsn(DUP);
        loadConstant(elem);
        classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        classInit.visitInsn(POP);   // don't need result of add
      });
      classInit.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;", false);
      return;
    }
    if (obj instanceof Map) {
      Map map = (Map)obj;
      String mapClassName = Type.getInternalName(Utils.JACTL_MAP_TYPE);
      classInit.visitTypeInsn(NEW, mapClassName);
      classInit.visitInsn(DUP);
      classInit.visitMethodInsn(INVOKESPECIAL, mapClassName, "<init>", "()V", false);
      map.forEach((key,value) -> {
        classInit.visitInsn(DUP);
        loadConstant(key);
        loadConstant(value);
        classInit.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        classInit.visitInsn(POP);   // don't need result of put
      });
      classInit.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "unmodifiableMap", "(Ljava/util/Map;)Ljava/util/Map;", false);
      return;
    }
    // Must be a simple value
    Utils.loadConst(classInit, obj);
    Utils.box(classInit, JactlType.typeOf(obj));
  }
}
