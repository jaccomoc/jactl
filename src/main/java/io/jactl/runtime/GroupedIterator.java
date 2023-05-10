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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator that groups the elements of list into sublists of given size.
 * E.g. turning a list into a list of pairs.
 */
public class GroupedIterator implements Iterator {
  Iterator     iter;
  String       source;
  int          offset;
  int          size;
  boolean      haveNext = false;
  boolean      finished = false;
  List<Object> group = null;

  GroupedIterator(Iterator iter, String source, int offset, int size) {
    this.iter   = iter;
    this.source = source;
    this.offset = offset;
    this.size   = size;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(GroupedIterator.class, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    var iter = (GroupedIterator)c.localObjects[0];
    iter.haveNext = true;
    boolean result = (boolean)c.getResult();
    iter.finished = !result;
    return result;
  }

  @Override public boolean hasNext() {
    try {
      if (group != null) {
        return true;
      }
      if (finished) {
        return false;
      }
      if (!haveNext) {
        haveNext = true;
        finished = !iter.hasNext();
      }
      return !finished;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, 0, null, new Object[]{this });
    }
  }

  public static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(GroupedIterator.class, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((GroupedIterator)c.localObjects[0]).doNext(c);
  }

  @Override public Object next() {
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext = false;
      Object  elem    = null;
      while (!finished && (group == null || group.size() < size)) {
        switch (location) {
          case 0:  hasNext = haveNext || iter.hasNext(); location = 2; break;
          case 1:  hasNext = (boolean)c.getResult();     location = 2; break;
          case 2:
            haveNext = false;
            if (!hasNext) {
              finished = true;
              break;
            }
            elem     = iter.next();
            location = 4;
            break;
          case 3:  elem = c.getResult();                 location = 4; break;
          case 4:
            elem = RuntimeUtils.mapEntryToList(elem);
            group = group == null ? new ArrayList<>(size) : group;
            group.add(elem);
            location = 0;
            break;
        }
      }
      var result = group;
      group = null;
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, next$cHandle,
                             location + 1, null, new Object[]{ this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
}
