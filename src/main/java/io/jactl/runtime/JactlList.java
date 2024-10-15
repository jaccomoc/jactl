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

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface JactlList extends Iterable<Object> {
  boolean add(Object elem);
  void add(int index, Object elem);
  Object get(int index);
  Object set(int index, Object elem);
  int size();
  Stream stream();
  Object[] toArray();

  /**
   * Create map at given index (if allowed)
   *
   * @param index  the index
   * @param source the source code
   * @param offset offset into source where call is occurring
   * @return the new map or throws a RuntimeError if list is not of correct type
   */
  JactlMap createMap(int index, String source, int offset);

  /**
   * Create list at given index (if allowed)
   *
   * @param index  the index
   * @param source the source code
   * @param offset offset into source where call is occurring
   * @return the new list or throws a RuntimeError if list is not of correct type
   */
  JactlList createList(int index, String source, int offset);

  JactlList subList(int fromIndex, int toIndex);

  /**
   * Add all elements in list
   * @param list  the other list of elements to add
   * @return true if this list is modified
   */
  boolean addAll(JactlList list);

  /**
   * Remove object at given index from list.
   * @param index  the index
   * @return the object that was at the given index
   */
  Object remove(int index);

  boolean equals(Object o);

  void sort(Comparator<? super Object> comparator);

  default boolean isEmpty() {
    return size() == 0;
  }

  default void forEach(Consumer<? super Object> action) {
    for (int i = 0; i < size(); i++) {
      action.accept(get(i));
    }
  }

  default Iterator<Object> iterator() {
    return stream().iterator();
  }

}
