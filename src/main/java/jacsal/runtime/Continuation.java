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

package jacsal.runtime;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

public class Continuation extends RuntimeException {

  private AsyncTask    asyncTask;          // The blocking task that needs to be done asynchronously
  private Continuation parentContinuation; // Continuation for our parent stack frame
  private MethodHandle methodHandle;       // Handle pointing to continuation wrapper function
  private RuntimeState runtimeState;       // ThreadLocal runtime state that needs to be restored on resumption
  public  int          methodLocation;     // Location within method where resuumption should continue
  public  long[]       localPrimitives;
  public  Object[]     localObjects;
  public  Object       result;             // Result of the async call when continuing after suspend

  Continuation(AsyncTask asyncTask) {
    super(null, null, false, false);
    this.asyncTask    = asyncTask;
    this.runtimeState = RuntimeState.getState();
  }

  /**
   * Create a continuation for the async task. When the task completes it will invoke this continuation to continue from
   * where we left off. If there are multiple stack frames whose state we need to capture we will create a new
   * continuation for each frame.
   *
   * @param asyncWork
   * @return
   */
  public static Continuation create(Supplier<Object> asyncWork) {
    AsyncTask    task         = new AsyncTask(asyncWork);
    Continuation continuation = new Continuation(task);
    task.setContinuation(continuation);
    return continuation;
  }

  /**
   * Chained continuation. When capturing continuation for each frame we copy the asyncTask from the previous
   * Continuation object to the new one so that when we finally get to the last frame and return to the caller we have
   * access to the asyncTask and can invoke the blocking task on a blocking work scheduler of some sort once the entire
   * Continuation state has been captured. The reason for doing it this way is to make sure that we don't execute the
   * blocking code before we have finished capturing our state. Otherwise the blocking code might finish first and try
   * to start continuing before we have finished capturing our state.
   *
   * @param continuation    the previous continuation in our chain (the one from our called child)
   * @param methodHandle    method handle to continuation wrapper function for function
   * @param codeLocation    the "location" to continue from
   * @param localPrimitives array of values for our local vars that are primitives
   * @param localObjects    array of object values for our local non-primitive vars
   */
  public Continuation(Continuation continuation, MethodHandle methodHandle, int codeLocation, long[] localPrimitives, Object[] localObjects) {
    super(null, null, false, false);
    this.asyncTask = continuation.asyncTask;
    continuation.asyncTask = null;
    continuation.parentContinuation = this;    // chain ourselves to our child
    this.methodLocation = codeLocation;
    this.methodHandle = methodHandle;
    this.localPrimitives = localPrimitives;
    this.localObjects = localObjects;
  }

  public Object continueExecution(Object result) {
    // Restore ThreadLocal state
    RuntimeState.setState(runtimeState);

    // First Continuation object has no stack/locals as it was the one constructed in the async function.
    // Just invoke our parent's continuation to continue the code.
    return parentContinuation.doContinue(result);
  }

  private Object doContinue(Object result) {
    for (Continuation c = this; c != null; c = c.parentContinuation) {
      try {
        c.result = result;
        result = c.methodHandle.invokeExact(c);
      }
      catch (Continuation cont) {
        // Make new continuation point back to existing chain at point we are up to so that when it
        // is continued it will then continue from where we were. We point to the current parent rather
        // than the continuation we were in, since the continuation we were in has just been suspended
        // and the new state for that function call is in the newly created continuation we just caught.
        cont.parentContinuation = c.parentContinuation;
        throw cont;
      }
      catch (Throwable e) {
        throw new IllegalStateException("Internal error: " + e.getMessage(), e);
      }
    }
    return result;
  }

  public AsyncTask getAsyncTask() {
    return asyncTask;
  }
}