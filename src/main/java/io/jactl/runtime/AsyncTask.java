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

import java.util.function.Consumer;

/**
 * Async task that will run in background (either on a blocking thread or as an async operation
 * like sending/receiving message).
 */
public abstract class AsyncTask {
  private Continuation     continuation;   // the continuation to invoke once work has completed

  public void setContinuation(Continuation continuation) {
    this.continuation = continuation;
  }

  public Continuation getContinuation() {
    return continuation;
  }

  public abstract void execute(JactlContext context, Consumer<Object> resume);
}