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

public class CheckpointTask extends AsyncTask {
  public CheckpointTask(String source, int offset) {
    super(source, offset);
  }

  @Override
  public void doExecute(JactlContext context, JactlScriptObject instance, Object data, Consumer<Object> resumer) {
    instance.incrementCheckpointId();
    continuation.scriptInstance = instance;
    byte[] buf = Checkpointer.checkpoint(continuation, source, offset);
    context.saveCheckpoint(instance.getInstanceId(), instance.checkpointId(), buf, source, offset, continuation.localObjects[0], resumer);
  }
}
