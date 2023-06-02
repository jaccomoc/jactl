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

import java.util.UUID;

public class JactlScriptObject implements Checkpointable {
  private UUID instanceId   = RuntimeUtils.randomUUID();
  private int  checkpointId = 0;

  public UUID getInstanceId() {
    return instanceId;
  }

  public boolean isCheckpointed() {
    return checkpointId > 0;
  }

  public int checkpointId() {
    return checkpointId;
  }

  public void incrementCheckpointId() {
    checkpointId++;
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeLong(instanceId.getMostSignificantBits());
    checkpointer.writeLong(instanceId.getLeastSignificantBits());
    checkpointer.writeCint(checkpointId);
  }

  @Override public void _$j$restore(Restorer restorer) {
    instanceId   = new UUID(restorer.readLong(), restorer.readLong());
    checkpointId = restorer.readCint();
  }
}
