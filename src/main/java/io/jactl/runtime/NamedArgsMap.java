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

import io.jactl.JactlType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used a bit like a marker interface to indicate to runtime argument handling that the Map
 * being passed in should be treated as named args rather than a single argument of type Map.
 */
public class NamedArgsMap extends LinkedHashMap implements Checkpointable {
  private static int VERSION = 1;

  public NamedArgsMap() {}

  protected NamedArgsMap(Map map) {
    super(map);
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeTypeEnum(JactlType.TypeEnum.BUILTIN);
    checkpointer.writeCInt(BuiltinFunctions.getClassId(this.getClass()));
    checkpointer.writeCInt(VERSION);
    checkpointer.writeMap(this);
  }

  @Override
  public void _$j$restore(Restorer restorer) {
    restorer.skipType();
    restorer.expectCInt(BuiltinFunctions.getClassId(this.getClass()), "Bad class id - expecting NamedArgsMap");
    restorer.expectCInt(VERSION, "Bad version");
    restorer.skipType();
    restorer.restoreMap(this);
  }
}
