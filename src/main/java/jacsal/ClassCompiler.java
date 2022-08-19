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
  private final Stmt.Script    script;
  private       ClassWriter    cw;

  ClassCompiler(String source, CompileContext context, String pkg, String className, Stmt.Script script) {
    this.context   = context;
    this.pkg       = pkg;
    this.className = className;
    internalName   = Utils.JACSAL_PKG + "/" + (pkg == null ? "" : pkg + "/") + className;
    this.script    = script;
    this.source    = source;
    cv = cw = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
    if (debug()) {
      cv = new TraceClassVisitor(cw, new PrintWriter(System.out));
    }
  }

  ClassCompiler(String source, CompileContext context, Stmt.Script script) {
    this(source, context, null, "JacsalScript_" + counter++, script);
  }

  public Function<Map<String,Object>,Object> compile() {
    cv.visit(V11, ACC_PUBLIC, internalName,
             null, "java/lang/Object", null);

    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    // Default constructor
    MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(ALOAD, 0);
    methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    methodVisitor.visitInsn(RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();

    String methodDescriptor = Type.getMethodDescriptor(Type.getType(Object.class),
                                                       Type.getType(Map.class));
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, (String)script.function.name.getValue(),
                                      methodDescriptor,
                                      null, null);
    mv.visitCode();
    // Assign globals map to field so we can access it from anywhere
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitFieldInsn(PUTFIELD, internalName, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class));

    MethodCompiler methodCompiler = new MethodCompiler(this, script.function, mv);
    methodCompiler.compile();
    mv.visitEnd();
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

  boolean debug() {
    return context.debug;
  }
}
