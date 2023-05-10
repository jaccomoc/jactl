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

/**
 * Iterator that wraps another iterator and transforms the elements returned by the wrapped iterator based on
 * the supplied closure.
 * This MapIterator handles async behaviour in the underlying iterator (iter.hasNext() and iter.next() can throw
 * Continuation) as well as async behaviour in the closure that maps the values of the wrapped iterator into new
 * values.
 */
class MapIterator implements Iterator {
  Iterator     iter;
  String       source;
  int          offset;
  JactlMethodHandle closure;
  boolean      withIndex;
  int          index = 0;

  MapIterator(Iterator iter, String source, int offset, JactlMethodHandle closure) {
    this(iter, source, offset, closure, false);
  }

  MapIterator(Iterator iter, String source, int offset, JactlMethodHandle closure, boolean withIndex) {
    this.iter      = iter;
    this.source    = source;
    this.offset    = offset;
    this.closure   = closure;
    this.withIndex = withIndex;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(MapIterator.class, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return c.getResult();
  }

  @Override
  public boolean hasNext() {
    try {
      return iter.hasNext();
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, 0, null, null);
    }
  }

  public static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(MapIterator.class, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((MapIterator)c.localObjects[0]).doNext(c);
  }

  @Override
  public Object next() {
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      Object elem = null;
      if (location == 0) {
        elem = iter.next();
        location = 2;
      }
      if (location == 1) {
        elem = c.getResult();
        location = 2;
      }
      if (location == 2) {
        elem = RuntimeUtils.mapEntryToList(elem);
        if (withIndex) {
          var elemList = new ArrayList<>();
          elemList.add(elem);
          elemList.add(index++);
          elem = elemList;
        }
        return closure == null ? elem : closure.invoke((Continuation) null, source, offset, new Object[]{elem});
      }
      else {
        // location == 3
        return c.getResult();
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, next$cHandle, location + 1, null, new Object[]{this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
}
