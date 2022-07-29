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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;

import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

public class ClassCompiler {

  private static final String JACSAL_PKG         = "jacsal/pkg";
  private static final String JACSAL_PREFIX      = "_$j$";
  private static final String JACSAL_SCRIPT_MAIN = JACSAL_PREFIX + "main";
  private static       long   counter            = 0;

  private final ClassWriter    cw;
  private final CompileContext context;
  private final String         pkg;
  private final String         className;
  private final String         internalName;
  private final Expr           expr;

  ClassCompiler(CompileContext context, String pkg, String className, Expr expr) {
    cw = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
    this.context   = context;
    this.pkg       = pkg;
    this.className = className;
    internalName   = JACSAL_PKG + "/" + (pkg == null ? "" : pkg + "/") + className;
    this.expr      = expr;
  }

  ClassCompiler(CompileContext context, Expr expr) {
    this(context, null, "JacsalScript_" + counter++, expr);
  }

  public Function<Map<String,Object>,Object> compile() {
    cw.visit(V11, ACC_PUBLIC, internalName,
             null, "java/lang/Object", null);

    String methodDescriptor = Type.getMethodDescriptor(Type.getType(Object.class),
                                                       Type.getType(Map.class));
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC , JACSAL_SCRIPT_MAIN,
                                      methodDescriptor,
                                      null, null);
    MethodCompiler methodCompiler = new MethodCompiler(this, expr, mv);
    methodCompiler.compile();
    mv.visitEnd();
    cw.visitEnd();
    Class<?> clss = context.loadClass(internalName.replaceAll("/", "."), cw.toByteArray());
    try {
      MethodHandle mh = MethodHandles.publicLookup().findStatic(clss, JACSAL_SCRIPT_MAIN, MethodType.methodType(Object.class, Map.class));
      return map -> {
        try {
          return mh.invokeExact(map);
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
}
