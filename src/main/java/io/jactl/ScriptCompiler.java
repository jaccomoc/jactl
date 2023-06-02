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
import io.jactl.runtime.RuntimeError;
import io.jactl.runtime.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

public class ScriptCompiler extends ClassCompiler {

  ScriptCompiler(String source, JactlContext context, Stmt.ClassDecl classDecl) {
    super(source, context, null, classDecl, classDecl.name.getStringValue() + ".jactl");
  }

  /**
   * Return a compiled script that accepts a Map of globals and Consumer which is the completion
   * code to run once the script has finished (and which will accept the result).
   * @return the script
   */
  JactlScript compileWithCompletion() {
    var scriptMain = compile();

    return new JactlScript(context, (map,completion) -> {
      try {
        Object result = scriptMain.apply(map);
        completion.accept(result);
      }
      catch (Continuation c) {
        context.asyncWork(completion, c, c.scriptInstance);
      }
      catch (JactlError | IllegalStateException | IllegalArgumentException e) {
        completion.accept(e);
      }
      catch (Throwable t) {
        completion.accept(new IllegalStateException("Invocation error: " + t, t));
      }
    });
  }

  Function<Map<String,Object>, Object> compile() {
    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACTL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    compileInnerClasses();
    compileScriptMain();
    compileJactlObjectFunctions();
    finishClassCompile();

    try {
      MethodType   methodType = MethodCompiler.getMethodType(classDecl.scriptMain.declExpr);
      boolean      isAsync    = classDecl.scriptMain.declExpr.functionDescriptor.isAsync;
      MethodHandle mh         = MethodHandles.publicLookup().findVirtual(compiledClass, Utils.JACTL_SCRIPT_MAIN, methodType);
      return map -> {
        JactlScriptObject instance = null;
        try {
          instance        = (JactlScriptObject)compiledClass.getDeclaredConstructor().newInstance();
          Object result   = isAsync ? mh.invoke(instance, (Continuation) null, map)
                                    : mh.invoke(instance, map);
          return result;
        }
        catch (Continuation c) {
          throw c;
        }
        catch (RuntimeError e) {
          cleanUp(instance);
          throw e;
        }
        catch (Throwable e) {
          cleanUp(instance);
          throw new RuntimeException(e);
        }
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  private void compileScriptMain() {
    var method = classDecl.scriptMain.declExpr;
    String methodName =  method.functionDescriptor.implementingMethod;

    doCompileMethod(method, methodName, true);
    addHandleToClass(method);
  }

  private void cleanUp(JactlScriptObject instance) {
    if (instance.isCheckpointed()) {
      context.deleteCheckpoint(instance.getInstanceId());
    }
  }
}
