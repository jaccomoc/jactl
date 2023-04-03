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

import java.lang.invoke.MethodHandle;
import java.util.Iterator;

/**
 * Async iterator to support iterator.skip(n) when n is < 0.
 * In these circumstances n represents an offset from the end of the "list" so we
 * need to keep a buffer of elements abs(n) big to support this behaviour.
 */
public class SkipIterator implements Iterator {
  Iterator               iter;
  int                    count;
  CircularBuffer<Object> buffer;
  boolean                reachedEnd = false;

  SkipIterator(Iterator iter, int count) {
    this.iter = iter;
    this.count  = count;
    this.buffer = new CircularBuffer<>(count);
  }

  private static MethodHandle hasNextHandle = RuntimeUtils.lookupMethod(SkipIterator.class, "hasNext$c", Object.class, SkipIterator.class, Continuation.class);

  public static Object hasNext$c(SkipIterator iter, Continuation c) {
    return iter.doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    return doHasNext(null);
  }

  private boolean doHasNext(Continuation c) {
    if (reachedEnd) {
      return buffer.size() > 0;
    }
    // Can't tell if we can return elements until we hit the end of the iterator
    boolean hasNext  = false;
    Object  elem     = null;
    int     location = c == null ? 0 : c.methodLocation;
    while (!reachedEnd) {
      try {
        switch (location) {
          case 0:
            hasNext = iter.hasNext();
            location = 2;
            break;
          case 1:
            hasNext = (boolean)c.getResult();
            location = 2;
            break;
          case 2:
            if (!hasNext) {
              reachedEnd = true;
            }
            else {
              elem = iter.next();
              location = 4;
            }
            break;
          case 3:
            elem = c.getResult();
            location = 4;
            break;
          case 4:
            buffer.add(elem);
            location = 0;
            break;
        }
      }
      catch (Continuation cont) {
        throw new Continuation(cont, hasNextHandle.bindTo(this), location + 1, null, null);
      }
    }
    return buffer.size() > 0;
  }

  @Override
  public Object next() {
    // Assume that hasNext() has already been called so no need for async handling
    if (!reachedEnd) {
      throw new IllegalStateException("next() called before hasNext()");
    }
    return buffer.remove();
  }
}
