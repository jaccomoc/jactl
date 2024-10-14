/*
 * Copyright © 2022,2023,2024  James Crawford
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
import java.util.Set;
import java.util.stream.Stream;

public interface JactlMap {
  /**
   * Put value into map at given key.
   * @param key   the String key
   * @param value  the value to be added
   * @return the previous value at the key or null if there was no value
   */
  Object put(Object key, Object value);

  Object get(Object key);

  /**
   * Remove element with given key
   * @param key  the string key
   * @return the value being removed or null if no entry with that key
   */
  Object remove(Object key);

  boolean containsKey(Object key);
  void putAll(JactlMap map);
  int size();
  Set<String> keySet();
  Stream<Map.Entry<String,Object>> entryStream();

  default boolean isEmpty() {
    return size() == 0;
  }
}
