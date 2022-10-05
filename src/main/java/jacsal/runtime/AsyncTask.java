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

import java.util.concurrent.Future;
import java.util.function.Supplier;

public class AsyncTask {
  private Supplier<Object> asyncWork;      // the blocking async work
  private Continuation     continuation;   // the continuation to invoke once work has completed

  public AsyncTask(Supplier<Object> asyncWork) {
    this.asyncWork = asyncWork;
  }

  public void setContinuation(Continuation continuation) {
    this.continuation = continuation;
  }

  public Object execute() {
    return asyncWork.get();
  }

  public Object resumeContinuation(Object result) {
    return continuation.continueExecution(result);
  }
}
