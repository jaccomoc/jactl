/*
 * Copyright © 2022-2026 James Crawford
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

package io.jactl.compiler;

import io.jactl.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

public class ScriptCompiler extends ClassCompiler {

  public ScriptCompiler(String source, JactlContext context, Stmt.ClassDecl classDecl) {
    super(source, context, null, classDecl, classDecl.name.getStringValue() + ".jactl");
  }

  JactlScript compile() {
    Class<?> compiledClass = compileToClass();
    if (compiledClass == null) {
      return null;
    }
    return JactlScript.createScript(compiledClass, context, classDecl.scriptMain.declExpr.functionDescriptor.isAsync());
  }
  
  private Class<?> compileToClass() {
    FieldVisitor globalVars = cv.visitField(ACC_PUBLIC, Utils.JACTL_GLOBALS_NAME, JactlType.MAP_TYPE_DESCRIPTOR, null, null);
    globalVars.visitEnd();

    compileInnerClasses();
    compileScriptMain();
    compileJactlObjectFunctions();
    finishClassCompile();
    return compiledClass;
  }

  private void compileScriptMain() {
    Expr.FunDecl method     = classDecl.scriptMain.declExpr;
    String       methodName =  method.functionDescriptor.implementingMethod;

    doCompileMethod(method, methodName, true);
    addHandleToClass(method);
  }

}
