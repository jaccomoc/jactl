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

public class UniqueIterator extends JactlIterator {
  private static int VERSION = 1;
  JactlIterator iter;
  String        source;
  int           offset;
  Object        current    = null;
  boolean       reachedEnd = false;
  boolean       hasValue   = false;
  boolean       first      = true;   // True until we have found first value

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCInt(IteratorType.UNIQUE.ordinal());
    checkpointer.writeCInt(VERSION);
    checkpointer.writeObject(iter);
    checkpointer.writeObject(source);
    checkpointer.writeCInt(offset);
    checkpointer.writeObject(current);
    checkpointer._writeBoolean(reachedEnd);
    checkpointer._writeBoolean(hasValue);
    checkpointer._writeBoolean(first);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCInt(IteratorType.UNIQUE.ordinal(), "Expected UNIQUE");
    restorer.expectCInt(VERSION, "Bad version");
    iter       = (JactlIterator)restorer.readObject();
    source     = (String)restorer.readObject();
    offset     = restorer.readCInt();
    current    = restorer.readObject();
    reachedEnd = restorer.readBoolean();
    hasValue   = restorer.readBoolean();
    first      = restorer.readBoolean();
  }

  UniqueIterator() {}

  UniqueIterator(JactlIterator iter, String source, int offset) {
    this.iter = iter;
    this.source = source;
    this.offset = offset;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(UniqueIterator.class, IteratorType.UNIQUE, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((UniqueIterator)c.localObjects[0]).doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    return doHasNext(null);
  }

  private boolean doHasNext(Continuation c) {
    if (reachedEnd) {
      return hasValue;
    }
    if (hasValue) {
      return true;
    }
    int location = c == null ? 0 : c.methodLocation;
    // Keep going until we get a value different to current one
    boolean hasNext = false;
    Object  nextElem = null;
    while (!reachedEnd && !hasValue) {
      try {
        // Use state machine in case iter.hasNext() or iter.next() throws Continuation
        // Even states are sync case and odd states are where we return after continuing.
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
              nextElem = iter.next();
              location = 4;
            }
            break;
          case 3:
            nextElem = c.getResult();
            location = 4;
            break;
          case 4:
            if (first || !RuntimeUtils.isEquals(current, nextElem, source, offset)) {
              current = nextElem;
              first = false;
              hasValue = true;
            }
            else {
              // Next element same as current so keep trying
              location = 0;
            }
            break;
        }
      }
      catch (Continuation cont) {
        throw new Continuation(cont, hasNext$cHandle, location + 1, null, new Object[]{this });
      }
    }
    return hasValue;
  }

  @Override
  public Object next() {
    if (!hasValue) {
      throw new IllegalStateException("Internal error: next() called before hasNext()");
    }
    hasValue = false;
    return current;
  }
}
