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

package io.jactl.runtime;

import io.jactl.JactlContext;
import io.jactl.JactlType;

import java.util.function.Consumer;
import java.util.function.Function;

import static io.jactl.JactlType.CONTINUATION;

/**
 * Class that captures current execution state and can be "continued" at some point in the future.
 * When suspending we throw a Continuation object and each caller in the call stack will catch
 * any Continuation thrown and then create its own Continuation chained to the one caught where
 * it can store its state. Then the caller throws its new Continuation for its caller to then catch.
 * <p>
 * The state at each caller includes the local variable values, a MethodHandle that points to the
 * method of the caller and an integer that represents a logical location in the method that the
 * compiler generates for each location where a Continuation could be thrown.
 * <p>
 * When continuing we continue from where the first Continuation thrown left off. When that code
 * finishes, since it no longer has its caller to return to, it returns here, and we find the next
 * Continuation in the chain and continue it.
 * <p>
 * If we need to suspend when in the middle of continuing from a previous suspension then we throw
 * a new Continuation and chain it to the rest of the Continuation objects in the current chain so
 * that when it is resumed and returns here we will find the rest of the chain to continue with.
 */
public class Continuation extends RuntimeException implements Checkpointable {
  private static int               VERSION = 1;
  private        AsyncTask         asyncTask;          // The blocking task that needs to be done asynchronously
  private        Continuation      parent;             // Continuation for our parent stack frame
  private        Continuation      child;              // Continuation of child (stack frame we are calling) if it exists
  private        JactlMethodHandle methodHandle;       // Handle pointing to continuation wrapper function
  public         int               methodLocation;     // Location within method where resumption should continue
  public         long[]            localPrimitives;
  public         Object[]          localObjects;
  public         JactlScriptObject scriptInstance = null;
  private        Object            result;             // Result of the async call when continuing after suspend

  Continuation(AsyncTask asyncTask, Object data) {
    super(null, null, false, false);
    this.asyncTask = asyncTask;
    localObjects = new Object[]{ data };
  }

  Continuation(AsyncTask asyncTask, Object data1, Object data2) {
    super(null, null, false, false);
    this.asyncTask = asyncTask;
    localObjects = new Object[]{ data1, data2 };
  }

  /**
   * Suspend the current execution and capture our state in a series of chained Continuations and then schedule the
   * given task on a blocking thread. Once the blocking task has completed the execution will be resumed from where it
   * left off. NOTE: this throws a Continuation object which will be caught by our caller who will capture their state
   * and then throw a new Continuation chained to this one (and so on until we get to the top-most caller).
   *
   * @param source    the source code
   * @param offset    offset into source where async function was called
   * @param data      data to be passed to task when invoked
   * @param asyncWork the work to be run on a blocking thread once we are suspended
   * @return nothing - always throws an exception
   * @throws Continuation always
   */
  public static Continuation suspendBlocking(String source, int offset, Object data, Function<Object,Object> asyncWork) {
    BlockingAsyncTask task         = new BlockingAsyncTask(asyncWork, source, offset);
    Continuation      continuation = new Continuation(task, data);
    task.setContinuation(continuation);
    throw continuation;
  }

  /**
   * Suspend the current execution and capture our state in a series of chained Continuation objects. The difference
   * between this call and the suspendBlocking() call is that this call, rather than scheduling the async work on a
   * blocking thread, instead runs it on the current thread (once the top-most caller has caught the last Continuation).
   * The idea is that this type of async work will itself schedule something in the background (like sending a message
   * or setting a timer) and then return immediately without needing to block on a blocking scheduler thread. Once the
   * result of the async work has been received (receiving a message or timer expiring) then the execution is resumed.
   *
   * @param source    the source code
   * @param offset    offset into source where async function was called
   * @param data      data to be passed to task when invoked
   * @param asyncWork the task that will schedule some async work in the background
   * @return nothing - always throws an exception
   * @throws Continuation always
   */
  public static Continuation suspendNonBlocking(String source, int offset, Object data, TriConsumer<JactlContext, Object, Consumer<Object>> asyncWork) {
    NonBlockingAsyncTask task         = new NonBlockingAsyncTask(asyncWork, source, offset);
    Continuation         continuation = new Continuation(task, data);
    task.setContinuation(continuation);
    throw continuation;
  }

  public static Continuation checkpoint(String source, int offset, Object data) {
    CheckpointTask task = new CheckpointTask(source, offset);
    Continuation cont = new Continuation(task, data);
    task.setContinuation(cont);
    throw cont;
  }

  public static Continuation checkpoint(String source, int offset, Object data1, Object data2) {
    CheckpointTask task = new CheckpointTask(source, offset);
    Continuation cont = new Continuation(task, data1, data2);
    task.setContinuation(cont);
    throw cont;
  }

  /**
   * Chained continuation. When capturing continuation for each frame we copy the asyncTask from the previous
   * Continuation object to the new one so that when we finally get to the last frame and return to the caller we have
   * access to the asyncTask and can invoke the blocking task on a blocking work scheduler of some sort once the entire
   * Continuation state has been captured. The reason for doing it this way is to make sure that we don't execute the
   * blocking code before we have finished capturing our state. Otherwise, the blocking code might finish first and try
   * to start continuing before we have finished capturing our state.
   *
   * @param continuation    the previous continuation in our chain (the one from our called child)
   * @param methodHandle    method handle to continuation wrapper function for function
   * @param codeLocation    the "location" to continue from
   * @param localPrimitives array of values for our local vars that are primitives
   * @param localObjects    array of object values for our local non-primitive vars
   */
  public Continuation(Continuation continuation, JactlMethodHandle methodHandle, int codeLocation, long[] localPrimitives, Object[] localObjects) {
    super(null, null, false, false);
    this.asyncTask = continuation.asyncTask;
    continuation.asyncTask = null;
    continuation.parent = this;    // chain ourselves to our child
    this.child = continuation;
    this.methodLocation = codeLocation;
    this.methodHandle = methodHandle;
    this.localPrimitives = localPrimitives;
    this.localObjects = localObjects;
  }

  /**
   * Resume execution from where we left off, passing in the result of whatever asynchronous operation has now just
   * completed.
   *
   * @param result the result of the async operation that caused us to suspend
   * @return the result of resuming our execution
   */
  public Object continueExecution(Object result) {
    // First Continuation object has no stack/locals as it was the one constructed in the async function.
    // Just invoke our parent's continuation to continue the code.
    return parent.doContinue(result);
  }

  /**
   * Invoke the Continuations in the chain, one by one, passing the result of each one to the next one in the chain
   * until there are no more, or we are suspended again.
   *
   * @param result the result from our async operation
   * @return the result from the final Continuation being resumed
   */
  private Object doContinue(Object result) {
    for (Continuation c = this; c != null; c = c.parent) {
      try {
        c.result = result;
        result = c.methodHandle.invoke(c);
      }
      catch (Continuation cont) {
        // Make new continuation point back to existing chain at point we are up to so that when it
        // is continued it will then continue from where we were. We point to the current parent rather
        // than the continuation we were in, since the continuation we were in has just been suspended
        // and the new state for that function call is in the newly created continuation we just caught.
        cont.parent = c.parent;
        if (c.parent != null) {
          c.parent.child = cont;
        }
        throw cont;
      }
      catch (RuntimeError runtimeError) {
        // Need to continue execution with error so that resumption code can rethrow
        result = runtimeError;
      }
      catch (Throwable e) {
        throw new IllegalStateException("Internal error: " + (e.getMessage() == null ? e.getClass() : e.getMessage()), e);
      }
    }
    return result;
  }

  public AsyncTask getAsyncTask() {
    return asyncTask;
  }

  public void setScriptInstance(JactlScriptObject instance) {
    scriptInstance = instance;
  }

  public JactlScriptObject getScriptInstance() {
    return scriptInstance;
  }

  public Object getResult() {
    if (result instanceof RuntimeError) {
      throw (RuntimeError) result;
    }
    return result;
  }

  public Continuation() {}

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(CONTINUATION);
    checkpointer.writeCint(VERSION);
    checkpointer.writeObject(parent);
    checkpointer.writeObject(child);
    //    checkpointer.writeObject(runtimeState);
    checkpointer.writeObject(methodHandle);
    checkpointer.writeCint(methodLocation);
    checkpointer.writeObject(localPrimitives);
    checkpointer.writeObject(localObjects);
    checkpointer.writeObject(result);
    checkpointer.writeObject(scriptInstance);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.CONTINUATION);
    restorer.expectCint(VERSION, "Bad version");
    parent          = (Continuation)restorer.readObject();
    child           = (Continuation)restorer.readObject();
    //    runtimeState  = (RuntimeState) restorer.readObject();
    methodHandle    = (JactlMethodHandle)restorer.readObject();
    methodLocation  = restorer.readCint();
    localPrimitives = (long[])restorer.readObject();
    localObjects    = (Object[])restorer.readObject();
    result          = restorer.readObject();
    scriptInstance  = (JactlScriptObject)restorer.readObject();
  }
}
