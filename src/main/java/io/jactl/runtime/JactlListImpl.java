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

import java.util.*;
import java.util.stream.Stream;

public class JactlListImpl implements JactlList {
  private List<Object> list;

  public JactlListImpl() {
    list = new ArrayList<>();
  }

  public JactlListImpl(int size) {
    list = new ArrayList<>(size);
  }

  public JactlListImpl(JactlList other) {
    list = new ArrayList<>(((JactlListImpl)other).list);
  }

  public JactlListImpl(List<Object> list) {
    this.list = list;
  }

  @Override public int size()                         { return list.size(); }
  @Override public Object get(int idx)                { return list.get(idx); }
  @Override public boolean add(Object elem)           { return list.add(elem); }
  @Override public void add(int index, Object elem)   { list.add(index, elem); }
  @Override public Object set(int index, Object elem) { return list.set(index, elem); }

  @Override
  public JactlList subList(int fromIndex, int toIndex) {
    return new JactlListImpl(list.subList(fromIndex, toIndex));
  }

  @Override public Object remove(int index)           { return list.remove(index); }
  @Override public boolean addAll(JactlList other)    { return list.addAll(((JactlListImpl)other).list); }
  @Override public Stream<Object> stream()            { return list.stream(); }
  @Override public Object[] toArray()                 { return list.toArray(); }
  @Override public boolean isEmpty()                  { return list.isEmpty(); }

  @Override public Iterator<Object> iterator()        { return list.iterator(); }

  @Override public void sort(Comparator comparator)   { list.sort(comparator); }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof JactlList)) return false;
    JactlList other = (JactlList)o;
    if (size() != other.size()) return false;
    for (int i = 0; i < size(); i++) {
      if (!Objects.equals(get(i), other.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public JactlMap createMap(int index, String source, int offset) {
    if (index < size()) {
      Object value = list.get(index);
      if (value != null) {
        if (value instanceof JactlMap) {
          return (JactlMap)value;
        }
        else {
          throw new RuntimeError("Expected map at index " + index + " but was " + RuntimeUtils.className(value), source, offset);
        }
      }
    }
    JactlMap map = RuntimeUtils.createMap();
    for (int i = list.size(); i < index + 1; i++) {
      list.add(null);
    }
    list.set(index, map);
    return map;
  }

  @Override
  public JactlList createList(int index, String source, int offset) {
    if (index < size()) {
      Object value = list.get(index);
      if (value != null) {
        if (value instanceof JactlList) {
          return (JactlList)value;
        }
        else {
          throw new RuntimeError("Expected list at index " + index + " but was " + RuntimeUtils.className(value), source, offset);
        }
      }
    }
    JactlList elem = RuntimeUtils.createList();
    for (int i = list.size(); i < index + 1; i++) {
      list.add(null);
    }
    list.set(index, elem);
    return elem;
  }

  @Override public int hashCode() {
    return Objects.hash(list);
  }

  @Override public String toString() {
    return list.toString();
  }
}
