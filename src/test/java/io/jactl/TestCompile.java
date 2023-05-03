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
import io.jactl.runtime.Continuation;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

public class TestCompile implements Opcodes {

  public static byte[] dump () throws Exception {

    ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V11, ACC_PUBLIC, "io/jactl/pkg/_$j$Script1", null, "java/lang/Object", new String[] { "io/jactl/runtime/JactlObject" });

    classWriter.visitSource("_$j$Script1.jactl", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "java/util/LinkedHashMap");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$FieldsAndMethods", "Ljava/util/Map;");
      methodVisitor.visitTypeInsn(NEW, "java/util/LinkedHashMap");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$StaticMethods", "Ljava/util/Map;");
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
      methodVisitor.visitLabel(label0);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn(Type.getType("Lio/jactl/pkg/_$j$Script1;"));
      methodVisitor.visitLdcInsn("_$j$main$$c");
      methodVisitor.visitLdcInsn(Type.getType("Ljava/lang/Object;"));
      methodVisitor.visitLdcInsn(Type.getType("Lio/jactl/runtime/Continuation;"));
      methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
      methodVisitor.visitLdcInsn(Type.getType("Lio/jactl/pkg/_$j$Script1;"));
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSpecial", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$main$ch", "Ljava/lang/invoke/MethodHandle;");
      methodVisitor.visitLabel(label1);
      Label label3 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("Error initialising class io/jactl/pkg/_$j$Script1");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_STATIC, "_$j$FieldsAndMethods", "Ljava/util/Map;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_STATIC, "_$j$StaticMethods", "Ljava/util/Map;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "_$j$getStaticMethods", "()Ljava/util/Map;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$StaticMethods", "Ljava/util/Map;");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "_$j$StaticGetStaticMethods", "()Ljava/util/Map;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$StaticMethods", "Ljava/util/Map;");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "_$j$getFieldsAndMethods", "()Ljava/util/Map;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$FieldsAndMethods", "Ljava/util/Map;");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE, "_$j$globals", "Ljava/util/Map;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "_$j$main", "(Lio/jactl/runtime/Continuation;Ljava/util/Map;)Ljava/lang/Object;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      Label label2 = new Label();
      methodVisitor.visitVarInsn(ALOAD, 1);
      //methodVisitor.visitJumpInsn(IFNONNULL, label2);

      methodVisitor.visitJumpInsn(IFNULL, label2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "methodLocation", "I");
      Label label21 = new Label();

      Label label11 = new Label();
      Label label19 = new Label();
//      methodVisitor.visitLookupSwitchInsn(label21, new int[] { 0, 1 }, new Label[] { label11, label19 });

      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 8);
      methodVisitor.visitLookupSwitchInsn(label21, new int[] { 0 }, new Label[] { label11 });
      methodVisitor.visitLabel(label21);
      methodVisitor.visitTypeInsn(NEW, "io/jactl/runtime/RuntimeError");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("Internal error: Invalid location in continuation");
      methodVisitor.visitLdcInsn("def x; sleep(0, x ?= sleep(0,0))");
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label2);

      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitFieldInsn(PUTFIELD, "io/jactl/pkg/_$j$Script1", "_$j$globals", "Ljava/util/Map;");
      methodVisitor.visitInsn(ACONST_NULL);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(I2L);
      methodVisitor.visitVarInsn(LSTORE, 6);
      methodVisitor.visitVarInsn(ASTORE, 8);
      Label label4 = new Label();
      Label label5 = new Label();
      Label label6 = new Label();
      methodVisitor.visitTryCatchBlock(label4, label5, label6, "io/jactl/runtime/NullError");
      methodVisitor.visitLabel(label4);
      methodVisitor.visitInsn(NOP);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(I2L);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      Label label7 = new Label();
      Label label8 = new Label();
      Label label9 = new Label();
      methodVisitor.visitTryCatchBlock(label7, label8, label9, "io/jactl/runtime/Continuation");
      methodVisitor.visitLabel(label7);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/BuiltinFunctions", "sleep", "(Lio/jactl/runtime/Continuation;JLjava/lang/Object;)Ljava/lang/Object;", false);
      Label label10 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label10);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitIntInsn(BIPUSH, 9);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitIntInsn(NEWARRAY, T_LONG);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ICONST_5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitIntInsn(BIPUSH, 6);
      methodVisitor.visitVarInsn(LLOAD, 6);
      methodVisitor.visitInsn(LASTORE);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitIntInsn(BIPUSH, 8);
      methodVisitor.visitVarInsn(ALOAD, 8);
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitTypeInsn(NEW, "io/jactl/runtime/Continuation");
      methodVisitor.visitInsn(DUP_X1);
      methodVisitor.visitInsn(SWAP);
      methodVisitor.visitFieldInsn(GETSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$main$ch", "Ljava/lang/invoke/MethodHandle;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/Continuation", "<init>", "(Lio/jactl/runtime/Continuation;Ljava/lang/invoke/MethodHandle;I[J[Ljava/lang/Object;)V", false);
      methodVisitor.visitInsn(ATHROW);
      //Label label11 = new Label();
      methodVisitor.visitLabel(label11);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "localObjects", "[Ljava/lang/Object;");
      methodVisitor.visitInsn(ICONST_5);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "localPrimitives", "[J");
      methodVisitor.visitIntInsn(BIPUSH, 6);
      methodVisitor.visitInsn(LALOAD);
      methodVisitor.visitVarInsn(LSTORE, 6);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "localObjects", "[Ljava/lang/Object;");
      methodVisitor.visitIntInsn(BIPUSH, 8);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitTypeInsn(CHECKCAST, "io/jactl/runtime/Continuation");
      methodVisitor.visitVarInsn(ASTORE, 8);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Continuation", "getResult", "()Ljava/lang/Object;", false);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitInsn(DUP);
      Label label12 = new Label();
      methodVisitor.visitJumpInsn(IFNONNULL, label12);
      Label label13 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label13);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label13);
      Label label14 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label14);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitLabel(label14);
      Label label15 = new Label();
      Label label16 = new Label();
      Label label17 = new Label();
      methodVisitor.visitTryCatchBlock(label15, label16, label17, "io/jactl/runtime/Continuation");
      methodVisitor.visitLabel(label15);
      methodVisitor.visitVarInsn(ASTORE, 9);
      methodVisitor.visitVarInsn(ALOAD, 8);
      methodVisitor.visitVarInsn(LLOAD, 6);
      methodVisitor.visitVarInsn(ALOAD, 9);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/BuiltinFunctions", "sleep", "(Lio/jactl/runtime/Continuation;JLjava/lang/Object;)Ljava/lang/Object;", false);
      Label label18 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label18);
      methodVisitor.visitLabel(label16);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitIntInsn(BIPUSH, 6);
      methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ICONST_5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitTypeInsn(NEW, "io/jactl/runtime/Continuation");
      methodVisitor.visitInsn(DUP_X1);
      methodVisitor.visitInsn(SWAP);
      methodVisitor.visitFieldInsn(GETSTATIC, "io/jactl/pkg/_$j$Script1", "_$j$main$ch", "Ljava/lang/invoke/MethodHandle;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/Continuation", "<init>", "(Lio/jactl/runtime/Continuation;Ljava/lang/invoke/MethodHandle;I[J[Ljava/lang/Object;)V", false);
      methodVisitor.visitInsn(ATHROW);
//      Label label19 = new Label();
      methodVisitor.visitLabel(label19);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "localObjects", "[Ljava/lang/Object;");
      methodVisitor.visitInsn(ICONST_5);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/jactl/runtime/Continuation", "getResult", "()Ljava/lang/Object;", false);
      methodVisitor.visitLabel(label18);
      methodVisitor.visitInsn(ARETURN);
      Label label20 = new Label();
      methodVisitor.visitLabel(label20);
      methodVisitor.visitLocalVariable("_$j$globals", "Ljava/util/Map;", null, label1, label20, 2);
      methodVisitor.visitLocalVariable("x", "Ljava/lang/Object;", null, label3, label20, 5);
//      methodVisitor.visitLabel(label2);
//      methodVisitor.visitVarInsn(ALOAD, 1);
//      methodVisitor.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation", "methodLocation", "I");
//      Label label21 = new Label();
//      methodVisitor.visitLookupSwitchInsn(label21, new int[] { 0, 1 }, new Label[] { label11, label19 });
//      methodVisitor.visitLabel(label21);
//      methodVisitor.visitTypeInsn(NEW, "io/jactl/runtime/RuntimeError");
//      methodVisitor.visitInsn(DUP);
//      methodVisitor.visitLdcInsn("Internal error: Invalid location in continuation");
//      methodVisitor.visitLdcInsn("def x; sleep(0, x ?= sleep(0,0))");
//      methodVisitor.visitInsn(ICONST_0);
//      methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
//      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "_$j$main$$c", "(Lio/jactl/runtime/Continuation;)Ljava/lang/Object;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/pkg/_$j$Script1", "_$j$main", "(Lio/jactl/runtime/Continuation;Ljava/util/Map;)Ljava/lang/Object;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_STATIC, "_$j$main$ch", "Ljava/lang/invoke/MethodHandle;", null, null);
      fieldVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static void main(String[] args) throws Exception {
    String className = "io/jactl/pkg/_$j$Script1";
    var classLoader = JactlContext.create().build().classLoader;
    Class clss = classLoader.defineClass(className.replaceAll("/", "."), dump());
    Object obj = clss.getDeclaredConstructor().newInstance();
    Method jmain = clss.getDeclaredMethod(Utils.JACTL_SCRIPT_MAIN, Continuation.class, Map.class);
    jmain.invoke(obj, null, Map.of());
  }
}
