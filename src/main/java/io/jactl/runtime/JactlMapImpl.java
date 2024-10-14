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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class JactlMapImpl implements JactlMap {
  private LinkedHashMap map;

  public JactlMapImpl() {
    map = new LinkedHashMap<>();
  }

  public JactlMapImpl(JactlMap map) {
    this.map = new LinkedHashMap<>(((JactlMapImpl)map).map);
  }

  public JactlMapImpl(Map map) {
    this.map = new LinkedHashMap<>(map);
  }

  @Override public int size()                           { return map.size(); }
  @Override public boolean isEmpty()                    { return map.isEmpty(); }
  @Override public Object get(Object key)               { return map.get(key); }
  @Override public Object put(Object key, Object value) { return map.put(key, value); }
  @Override public Object remove(Object key)            { return map.remove(key); }
  @Override public boolean containsKey(Object key)      { return map.containsKey(key); }
  @Override public void putAll(JactlMap other)          { map.putAll(((JactlMapImpl)other).map); }
  @Override public Set<String> keySet()                 { return ((LinkedHashMap)map).keySet(); }
  @Override public Stream<Map.Entry<String,Object>> entryStream() {
    return map.entrySet().stream();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JactlMap)) return false;
    JactlMap other = (JactlMap)o;
    if (map.size() != other.size()) return false;
    for (Object key : map.keySet()) {
      if (!other.containsKey(key)) return false;
      if (!map.get(key).equals(other.get(key))) return false;
    }
    return true;
  }

  @Override public int hashCode() {
    return map.hashCode();
  }

  @Override
  public String toString() {
    return map.toString();
  }
}
