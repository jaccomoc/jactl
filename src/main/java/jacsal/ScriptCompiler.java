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

import jacsal.runtime.AsyncTask;
import jacsal.runtime.Continuation;
import jacsal.runtime.RuntimeUtils;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

public class ScriptCompiler extends ClassCompiler {

  ScriptCompiler(String source, JacsalContext context, Stmt.ClassDecl classDecl) {
    super(source, context, null, classDecl, classDecl.name.getStringValue() + ".jacsal");
  }

  public Function<Map<String,Object>, Future<Object>> compile() {
    FieldVisitor globalVars = cv.visitField(ACC_PRIVATE, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class), null, null);
    globalVars.visitEnd();

    compileInnerClasses();
    compileScriptMain();
    finishClassCompile();

    try {
      MethodType methodType = MethodCompiler.getMethodType(classDecl.scriptMain.declExpr);
      boolean    isAsync    = classDecl.scriptMain.declExpr.functionDescriptor.isAsync;
      MethodHandle mh      = MethodHandles.publicLookup().findVirtual(compiledClass, Utils.JACSAL_SCRIPT_MAIN, methodType);
      return map -> {
        var future = new CompletableFuture<>();
        Object out = map.get("out");
        RuntimeUtils.setOutput(out);
        try {
          Object instance = compiledClass.getDeclaredConstructor().newInstance();
          Object result = isAsync ? mh.invoke(instance, (Continuation)null, map)
                                  : mh.invoke(instance, map);
          future.complete(result);
        }
        catch (Continuation c) {
          blockingWork(future, c);
        }
        catch (JacsalError|IllegalStateException|IllegalArgumentException e) {
          future.complete(e);
        }
        catch (Throwable t) {
          future.complete(new IllegalStateException("Invocation error: " + t, t));
        }
        return future;
      };
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  private void blockingWork(CompletableFuture<Object> future, Continuation c) {
    // Need to execute async task on some sort of blocking work scheduler and then reschedule
    // continuation back onto the event loop or non-blocking scheduler (might even need to be
    // the same thread as we are on).
    Object executionContext = context.getThreadContext();
    context.scheduleBlocking(() -> {
      final var asyncTask   = c.getAsyncTask();
      Object    asyncResult = asyncTask.execute();
      context.scheduleEvent(executionContext, () -> resumeContinuation(future, asyncResult, asyncTask));
    });
  }

  private void resumeContinuation(CompletableFuture<Object> future, Object asyncResult, AsyncTask asyncTask) {
    try {
      Object result = asyncTask.resumeContinuation(asyncResult);
      // We finally get the real result out of the script execution
      future.complete(result);
    }
    catch (Continuation c) {
      blockingWork(future, c);
    }
    catch (Throwable t) {
      future.complete(t);
    }
  }

  private void compileScriptMain() {
    var method = classDecl.scriptMain.declExpr;
    String methodName =  method.functionDescriptor.implementingMethod;

    doCompileMethod(method, methodName, true);
    addHandleToClass(method);
  }
}
