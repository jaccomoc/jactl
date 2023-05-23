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
import io.jactl.runtime.RuntimeState;
import io.jactl.runtime.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.jactl.JactlType.CONTINUATION;
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
        asyncWork(completion, c);
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

  public static AtomicInteger checkpointCount = new AtomicInteger(0);
  public static AtomicLong    checkpointSize  = new AtomicLong(0);
  public static void resetCheckpointCounts() {
    checkpointCount = new AtomicInteger(0);
    checkpointSize  = new AtomicLong(0);
  }

  private void asyncWork(Consumer<Object> completion, Continuation c) {
    // Need to execute async task on some sort of blocking work scheduler and then reschedule
    // continuation back onto the event loop or non-blocking scheduler (might even need to be
    // the same thread as we are on).
    try {
      final var    asyncTask    = c.getAsyncTask();
      Continuation asyncTaskCont = asyncTask.getContinuation();
      if (context.checkpoint()) {
        Checkpointer checkpointer = Checkpointer.get(asyncTask.getSource(), asyncTask.getOffset());
        checkpointer.checkpoint(asyncTaskCont);
        //System.out.println("DEBUG: checkpoint = \n" + Utils.dumpHex(checkpointer.getBuffer(), checkpointer.getLength()) + "\n");
        byte[] buf = new byte[checkpointer.getLength()];
        System.arraycopy(checkpointer.getBuffer(), 0, buf, 0, checkpointer.getLength());
        checkpointer.reset();
        checkpointCount.getAndIncrement();
        checkpointSize.addAndGet(buf.length);
        if (context.restore()) {
          Restorer     restorer = Restorer.get(context, buf, asyncTask.getSource(), asyncTask.getOffset());
          Continuation cont1    = (Continuation) restorer.restore();
          asyncTask.execute(context, cont1.localObjects[0], result -> resumeContinuation(completion, result, cont1));
        }
        else {
          asyncTask.execute(context, asyncTaskCont.localObjects[0], result -> resumeContinuation(completion, result, asyncTaskCont));
        }
      }
      else {
        asyncTask.execute(context, asyncTaskCont.localObjects[0], result -> resumeContinuation(completion, result, asyncTaskCont));
      }
    }
    catch (Throwable t) {
      completion.accept(t);
    }
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
