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

import java.util.Iterator;

/**
 * Iterator for iterating over a list of iterators.
 * The mapping function transforms an element into either a single element or another iterator.
 * E.g.:
 * <pre>
 *   [1,[2,3,4],5].flatMap{ it instanceof List ? it.map{it*it} : it*it }
 *         --&gt; [1,4,9,16,25]
 * </pre>
 */
public class FlatMapIterator implements Iterator {
  Iterator     iter;
  Iterator     subIter;   // Iterator for each element of iter
  String       source;
  int          offset;
  JactlMethodHandle closure;

  FlatMapIterator(Iterator iter, String source, int offset, JactlMethodHandle closure) {
    this.iter = iter;
    this.source = source;
    this.offset = offset;
    this.closure = closure;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(FlatMapIterator.class, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((FlatMapIterator)c.localObjects[0]).doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    return doHasNext(null);
  }

  private boolean doHasNext(Continuation c) {
    int     location       = c == null ? 0 : c.methodLocation;
    boolean iterHasNext    = false;
    Object  iterNext       = null;
    Object  mappedNext     = null;
    boolean subIterHasNext = false;
    try {
      while (true) {
        // State machine. Even states (locations) are where possibly async operations are
        // invoked (hasNext/next) and corresponding odd state is where async result comes back.
        switch (location) {
          case 0: {
            if (subIter == null) {
              iterHasNext = iter.hasNext();
              location = 2;
            }
            else {
              location = 8;    // Jump straight to state where we know we have subIter already
            }
            break;
          }
          case 1: {
            iterHasNext = (boolean) c.getResult();
            location = 2;
            break;
          }
          case 2: {
            if (!iterHasNext) {
              return false;
            }
            iterNext = iter.next();
            location = 4;
            break;
          }
          case 3: {
            iterNext = c.getResult();
            location = 4;
            break;
          }
          case 4: {
            mappedNext = closure == null ? iterNext
                                         : closure.invoke((Continuation)null, source, offset, new Object[]{RuntimeUtils.mapEntryToList(iterNext)});
            location = 6;
            break;
          }
          case 5: {
            mappedNext = c.getResult();
            location = 6;
            break;
          }
          case 6: {
            if (mappedNext == null) {
              location = 0;        // Back to state 0 since null returned from closure
              break;
            }
            subIter = RuntimeUtils.createIteratorFlatMap(mappedNext);
            location = 8;
            break;
          }
          case 8: {
            subIterHasNext = subIter.hasNext();
            location = 10;
            break;
          }
          case 9: {
            subIterHasNext = (boolean) c.getResult();
            location = 10;
            break;
          }
          case 10: {
            if (subIterHasNext) {
              return true;
            }
            subIter = null;    // Finished this subIter so we need the next one.
            location = 0;      // Start again at state 0.
            break;
          }
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, location + 1, null, new Object[]{this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  private static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(FlatMapIterator.class, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((FlatMapIterator)c.localObjects[0]).doNext(c);
  }

  @Override
  public Object next() {
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext = false;
      Object  elem    = null;
      while (true) {
        switch (location) {
          case 0: {
            hasNext = hasNext();
            location = 2;
            break;
          }
          case 1: {
            hasNext = (boolean)c.getResult();
            location = 2;
            break;
          }
          case 2: {
            if (!hasNext) {
              return null;
            }
            elem = subIter.next();
            location = 4;
            break;
          }
          case 3: {
            elem = c.getResult();
            location = 4;
            break;
          }
          case 4: {
            return elem;
          }
        }
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
