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

import static io.jactl.JactlType.ITERATOR;

/**
 * Iterator for the filter method. It takes the supplied iterator and wraps it in an iterator that only returns
 * elements that match the given criteria (specified as a closure).
 * This FilterIterator handles async behaviour in the wrapped iterator (supports iter.hasNext() and iter.next()
 * both throwing Continuation) as well as async behaviour in the closure passed to it.
 */
class FilterIterator extends JactlIterator {
  private static int VERSION = 1;
  JactlIterator     iter;
  String            source;
  int               offset;
  JactlMethodHandle closure;
  Object            next;
  boolean           hasNext = false;

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCInt(IteratorType.FILTER.ordinal());
    checkpointer.writeCInt(VERSION);
    checkpointer.writeObject(iter);
    checkpointer.writeObject(source);
    checkpointer.writeCInt(offset);
    checkpointer.writeObject(closure);
    checkpointer.writeObject(next);
    checkpointer._writeBoolean(hasNext);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCInt(IteratorType.FILTER.ordinal(), "Expected FILTER");
    restorer.expectCInt(VERSION, "Bad version");
    iter     = (JactlIterator)restorer.readObject();
    source   = (String)restorer.readObject();
    offset   = restorer.readCInt();
    closure  = (JactlMethodHandle)restorer.readObject();
    next     = restorer.readObject();
    hasNext  = restorer.readBoolean();
  }

  FilterIterator() {}

  FilterIterator(JactlIterator iter, String source, int offset, JactlMethodHandle closure) {
    this.iter = iter;
    this.source = source;
    this.offset = offset;
    this.closure = closure;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(FilterIterator.class, IteratorType.FILTER,  "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((FilterIterator)c.localObjects[0]).hasNext();
  }

  @Override
  public boolean hasNext() {
    if (hasNext) {
      return true;
    }
    try {
      findNext(null);
      return hasNext;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, 0, null, new Object[]{this });
    }
  }

  public static JactlMethodHandle findNext$cHandle = RuntimeUtils.lookupMethod(FilterIterator.class, IteratorType.FILTER,"findNext$c", Object.class, Continuation.class);
  public static Object findNext$c(Continuation c) {
    ((FilterIterator)c.localObjects[1]).findNext(c);
    return null;
  }

  private void findNext(Continuation c) {
    int     location   = c == null ? 0 : c.methodLocation;
    Object  elem       = c == null ? null : c.localObjects[0];
    Object  filterCond = null;
    try {
      // Implement a simple state machine. Every even state is where we attempt to synchronously do something.
      // The immediately following odd state is for when the even state throws due to async behaviour and we
      // then get the result back in the Continuation.result field. If the even state does not throw we set the
      // location to skip straight to the next even state. When the odd state throws we catch and rethrow our
      // own Continuation chained to the caught one and set the location in our Continuation to location + 1
      // so that when we are resumed we go to the corresponding odd state.
      while (true) {
        // We loop until we find a matching element or until there are no more elements from our wrapped iterator
        switch (location) {
          case 0:
            hasNext = iter.hasNext();
            location = 2;
            break;
          case 1:
            hasNext = (boolean) c.getResult();
            location = 2;
            break;
          case 2:
            // Return if there are no more elements
            if (!hasNext) {
              return;
            }
            elem = iter.next();
            location = 4;
            break;
          case 3:
            elem = c.getResult();
            location = 4;
            break;
          case 4:
            elem = RuntimeUtils.mapEntryToList(elem);
            filterCond = closure == null ? RuntimeUtils.isTruth(elem, false)
                                         : closure.invoke((Continuation) null, source, offset, new Object[]{elem});
            location = 6;
            break;
          case 5:
            filterCond = c.getResult();
            location = 6;
            break;
          case 6:
            // If we have found an element that satisfies the filter condition then return
            if (RuntimeUtils.isTruth(filterCond, false)) {
              next = elem;
              return;
            }
            // Element does not match so go around again and try the next element (if there are any left)
            location = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + location);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, findNext$cHandle, location + 1, null, new Object[]{elem, this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  public static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(FilterIterator.class, IteratorType.FILTER, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((FilterIterator)c.localObjects[0]).doNext(c);
  }

  @Override
  public Object next() {
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    if (c == null) {
      if (!hasNext) {
        try {
          next = null;
          findNext(null);
        }
        catch (Continuation cont) {
          throw new Continuation(cont, next$cHandle, 0, null, new Object[]{this });
        }
      }
    }
    hasNext = false;
    return next;
  }
}
