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

package io.jactl.compiler;

import io.jactl.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

public class ScriptCompiler extends ClassCompiler {

  public ScriptCompiler(String source, JactlContext context, Stmt.ClassDecl classDecl) {
    super(source, context, null, classDecl, classDecl.name.getStringValue() + ".jactl");
  }

  /**
   * Return a compiled script that accepts a Map of globals and Consumer which is the completion
   * code to run once the script has finished (and which will accept the result).
   * @return the script
   */
  JactlScript compileWithCompletion() {
    Function<Map<String, Object>, Object> scriptMain = compile();
    return JactlScript.createScript(scriptMain, context);
  }

  public Function<Map<String,Object>, Object> compile() {
    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACTL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    compileInnerClasses();
    compileScriptMain();
    compileJactlObjectFunctions();
    finishClassCompile();

    if (compiledClass == null) {
      return null;
    }
    return JactlScript.createInvoker(compiledClass, context);
  }

  private void compileScriptMain() {
    Expr.FunDecl method     = classDecl.scriptMain.declExpr;
    String       methodName =  method.functionDescriptor.implementingMethod;

    doCompileMethod(method, methodName, true);
    addHandleToClass(method);
  }

}
