package io.jactl.runtime;

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

import io.jactl.JactlType;
import io.jactl.Utils;

import java.util.ArrayList;
import java.util.List;

import static io.jactl.JactlType.ITERATOR;

/**
 * Iterator for zipping multiple lists/iterators together.
 * For n lists returns an iterator where each entry is a list of the values of the n lists
 * for the same index.
 * E.g.:
 * <pre>
 *   [ [1,2,3], [4,5,6] ].zip()
 *   Result:
 *   [ [1,4], [2,5], [3,6] ]
 * </pre>
 */
public class TranposeIterator extends JactlIterator {
  private static int VERSION = 1;
  String              source;
  int                 offset;
  List<JactlIterator> inputIters;
  List                current;
  boolean             nonEmptySeen;

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCInt(IteratorType.FLATMAP.ordinal());
    checkpointer.writeCInt(VERSION);
    checkpointer.writeObject(source);
    checkpointer.writeCInt(offset);
    checkpointer.writeObject(inputIters);
    checkpointer.writeObject(current);
    checkpointer.writeBoolean(nonEmptySeen);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCInt(IteratorType.FLATMAP.ordinal(), "Expected FLATMAP");
    restorer.expectCInt(VERSION, "Bad version");
    source   = (String)restorer.readObject();
    offset   = restorer.readCInt();
    inputIters = (List<JactlIterator>) restorer.readObject();
    current  = (List)restorer.readObject();
    nonEmptySeen = restorer.readBoolean();
  }

  TranposeIterator() {}

  TranposeIterator(List inputs, String source, int offset) {
    this.source = source;
    this.offset = offset;
    this.inputIters = new ArrayList();
    int i = 0;
    for (Object input: inputs) {
      i++;
      JactlIterator iter = RuntimeUtils.createIteratorOrNull(input);
      if (iter == null) {
        throw new RuntimeError(Utils.nth(i) + " input to transpose() is invalid. Must be List/array (type is " + RuntimeUtils.className(input) + ")", source, offset);
      }
      inputIters.add(iter);
    }
    reset();
  }

  private void reset() {
    current = new ArrayList();
    nonEmptySeen = false;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(TranposeIterator.class, IteratorType.FLATMAP, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((TranposeIterator)c.localObjects[0]).doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    return doHasNext(null);
  }

  /**
   * We have a next value as long as all our inputs have a next value (if includeNulls is false)
   * or any of them have a value (if includeNulls is true).
   * @param c the Continuation
   * @return true if calling next() would return another value
   */
  private boolean doHasNext(Continuation c) {
    int     location       = c == null ? 0 : c.methodLocation;
    boolean iterHasNext    = false;
    Object  iterNext       = null;
    try {
      while (true) {
        // State machine. Even states (locations) are where possibly async operations are invoked
        // (hasNext/next) and the corresponding odd state is where the async result comes back.
        switch (location) {
          case 0: {
            if (inputIters.size() == 0) {
              return false;
            }
            // Check if we have enough inputs for current index
            if (current.size() == inputIters.size()) {
                return nonEmptySeen;
            }
            JactlIterator iter = inputIters.get(current.size());
            iterHasNext = iter.hasNext();
            location = 2;
            break;
          }
          case 1: {
            iterHasNext = (boolean) c.getResult();
            location = 2;
            break;
          }
          case 2: {
            if (!iterHasNext) {
              current.add(null);
              // Get next value for current tuple
              location = 0;
            }
            else {
              nonEmptySeen = true;
              iterNext = inputIters.get(current.size()).next();
              location = 4;
            }
            break;
          }
          case 3: {
            iterNext = c.getResult();
            location = 4;
            break;
          }
          case 4: {
            iterNext = RuntimeUtils.mapEntryToList(iterNext);
            current.add(iterNext);
            location = 0;
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

  private static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(TranposeIterator.class, IteratorType.FLATMAP, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((TranposeIterator)c.localObjects[0]).doNext(c);
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
            List result = current;
            reset();
            return result;
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
