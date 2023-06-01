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
 * A non-blocking async task where the work is initiated from the event loop thread
 * after the script has been suspended. The original event loop thread returns from
 * the script execution and when the result of the asynchronous task has completed
 * the result is passed to the script, and it continues from where it left off on an
 * event loop thread.
 * <p>
 * The difference between a NonBlockingAsyncTask and a BlockingAsyncTask is that the
 * BlockingAsyncTask executes completely on a blocking thread while the NonBlockingAsyncTask
 * is executed on the event loop since it will just initiate the work in a non-blocking way
 * (e.g. set a timer or send a message or initiate an async database operation) and then return
 * immediately. When the background task completes (message received, timer expires, etc.) the
 * result is then used to resume the script on an event loop thread.
 */
public class NonBlockingAsyncTask extends AsyncTask {
  private final TriConsumer<JactlContext,Object,Consumer<Object>> asyncWork;

  public NonBlockingAsyncTask(TriConsumer<JactlContext,Object,Consumer<Object>> asyncWork, String source, int offset) {
    super(source, offset);
    this.asyncWork = asyncWork;
  }

  @Override
  public void execute(JactlContext context, JactlScriptObject instance, Object data, Consumer<Object> resumer) {
    asyncWork.accept(context, data, resumer);
  }
}
