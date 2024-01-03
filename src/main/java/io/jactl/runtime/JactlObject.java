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

import java.util.Map;

/**
 * All JactlObjects implement this. It provides runtime access to fields and methods.
 */
public interface JactlObject extends Checkpointable {
  public Map<String,Object> _$j$getFieldsAndMethods();
  public Map<String,Object> _$j$getStaticFieldsAndMethods();

  public Object      _$j$init$$w(Continuation c, String source, int offset, Object[] args);
  public void        _$j$writeJson(JsonEncoder buffer);

  /**
   * Used internally for decoding JSON.
   * Returns an array of 64 bit flags showing which (optional) fields were missing.
   * @param decoder  the decoder to use
   * @return flags for missing fields
   */
  public long[]      _$j$readJson(JsonDecoder decoder);
}
