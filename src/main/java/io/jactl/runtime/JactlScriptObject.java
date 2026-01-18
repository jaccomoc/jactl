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
  private UUID _$j$instanceId   = RuntimeUtils.randomUUID();
  private int  _$j$checkpointId = 0;

  public UUID _$j$getInstanceId() {
    return _$j$instanceId;
  }

  public boolean _$j$isCheckpointed() {
    return _$j$checkpointId > 0;
  }

  public int _$j$checkpointId() {
    return _$j$checkpointId;
  }

  public void _$j$incrementCheckpointId() {
    _$j$checkpointId++;
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeLong(_$j$instanceId.getMostSignificantBits());
    checkpointer.writeLong(_$j$instanceId.getLeastSignificantBits());
    checkpointer.writeCInt(_$j$checkpointId);
  }

  @Override public void _$j$restore(Restorer restorer) {
    _$j$instanceId = new UUID(restorer.readLong(), restorer.readLong());
    _$j$checkpointId = restorer.readCInt();
  }
}
