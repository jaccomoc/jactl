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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class for capturing some blocking work that needs to occur asynchronously without blocking
 * the event loop thread.
 * The idea is that the Supplier object passed into us will be executed on a blocking thread
 * where blocking work is allowed to occur and once finished we will get the result and then
 * continue the original work on an event loop thread.
 */
public class BlockingAsyncTask extends AsyncTask {
  private Function<Object,Object> asyncWork;      // the blocking async work

  public BlockingAsyncTask(Function<Object,Object> asyncWork, String source, int offset) {
    super(source, offset);
    this.asyncWork = asyncWork;
  }

  @Override
  public void execute(JactlContext context, JactlScriptObject instance, Object data, Consumer<Object> resume) {
    Object executionContext = context.getThreadContext();     // current thread, so we can resume on same thread if possible
    context.scheduleBlocking(() -> {
      Object asyncResult = asyncWork.apply(data);
      context.scheduleEvent(executionContext, () -> resume.accept(asyncResult));
    });
  }
}
