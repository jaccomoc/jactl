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

package jacsal;

import jacsal.runtime.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

public class ScriptCompiler extends ClassCompiler {

  ScriptCompiler(String source, JacsalContext context, Stmt.ClassDecl classDecl) {
    super(source, context, null, classDecl, classDecl.name.getStringValue() + ".jacsal");
  }

  /**
   * Return a compiled script that accepts a Map of globals and Consumer which is the completion
   * code to run once the script has finished (and which will accept the result).
   * @return the script
   */
  JacsalScript compileWithCompletion() {
    var scriptMain = compile();

    return new JacsalScript(context, (map,completion) -> {
      RuntimeState.setOutput(map.get(Utils.JACSAL_GLOBALS_OUTPUT));
      RuntimeState.setInput(map.get(Utils.JACSAL_GLOBALS_INPUT));
      try {
        Object result = scriptMain.apply(map);
        completion.accept(result);
      }
      catch (Continuation c) {
        asyncWork(completion, c);
      }
      catch (JacsalError | IllegalStateException | IllegalArgumentException e) {
        completion.accept(e);
      }
      catch (Throwable t) {
        completion.accept(new IllegalStateException("Invocation error: " + t, t));
      }
    });
  }

  Function<Map<String,Object>, Object> compile() {
    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    compileInnerClasses();
    compileScriptMain();
    finishClassCompile();

    try {
      MethodType   methodType = MethodCompiler.getMethodType(classDecl.scriptMain.declExpr);
      boolean      isAsync    = classDecl.scriptMain.declExpr.functionDescriptor.isAsync;
      MethodHandle mh         = MethodHandles.publicLookup().findVirtual(compiledClass, Utils.JACSAL_SCRIPT_MAIN, methodType);
      return map -> {
        if (map.containsKey(Utils.JACSAL_GLOBALS_OUTPUT)) {
          RuntimeState.setOutput(map.get(Utils.JACSAL_GLOBALS_OUTPUT));
        }
        if (map.containsKey(Utils.JACSAL_GLOBALS_INPUT)) {
          RuntimeState.setInput(map.get(Utils.JACSAL_GLOBALS_INPUT));
        }
        try {
          Object instance = compiledClass.getDeclaredConstructor().newInstance();
          Object result = isAsync ? mh.invoke(instance, (Continuation) null, map)
                                  : mh.invoke(instance, map);
          return result;
        }
        catch (Continuation c) {
          throw c;
        }
        catch (RuntimeError e) {
          throw e;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  private void asyncWork(Consumer<Object> completion, Continuation c) {
    // Need to execute async task on some sort of blocking work scheduler and then reschedule
    // continuation back onto the event loop or non-blocking scheduler (might even need to be
    // the same thread as we are on).
    final var asyncTask = c.getAsyncTask();
    asyncTask.execute(context, result -> resumeContinuation(completion, result, asyncTask.getContinuation()));
  }

  private void resumeContinuation(Consumer<Object> completion, Object asyncResult, Continuation cont) {
    try {
      Object result = cont.continueExecution(asyncResult);
      // We finally get the real result out of the script execution
      completion.accept(result);
    }
    catch (Continuation c) {
      asyncWork(completion, c);
    }
    catch (Throwable t) {
      completion.accept(t);
    }
  }

  private void compileScriptMain() {
    var method = classDecl.scriptMain.declExpr;
    String methodName =  method.functionDescriptor.implementingMethod;

    doCompileMethod(method, methodName, true);
    addHandleToClass(method);
  }
}
