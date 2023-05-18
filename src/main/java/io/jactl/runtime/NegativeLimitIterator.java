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
 * Async iterator that supports limit(n) where n &lt; 0 which means discard last n.
 */
public class NegativeLimitIterator extends JactlIterator {
  private static int VERSION = 1;
  JactlIterator          iter;
  int                    count;
  CircularBuffer<Object> buffer;
  boolean                reachedEnd = false;

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCint(IteratorType.NEGATIVE_LIMIT.ordinal());
    checkpointer.writeCint(VERSION);
    checkpointer.writeObject(iter);
    checkpointer.writeCint(count);
    checkpointer.writeObject(buffer);
    checkpointer._writeBoolean(reachedEnd);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCint(IteratorType.NEGATIVE_LIMIT.ordinal(), "Expected NEGATIVE_LIMIT");
    restorer.expectCint(VERSION, "Bad version");
    iter       = (JactlIterator)restorer.readObject();
    count      = restorer.readCint();
    buffer     = new CircularBuffer<>();
    buffer     = (CircularBuffer<Object>)restorer.readObject();
    reachedEnd = restorer.readBoolean();
  }

  NegativeLimitIterator() {}

  NegativeLimitIterator(JactlIterator iter, int count) {
    this.iter = iter;
    this.count  = count;
    this.buffer = new CircularBuffer<>(count + 1);
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(NegativeLimitIterator.class, IteratorType.NEGATIVE_LIMIT, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((NegativeLimitIterator)c.localObjects[0]).doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    return doHasNext(null);
  }

  private boolean doHasNext(Continuation c) {
    if (reachedEnd) {
      return false;
    }
    // We can return elements once buffer is full
    boolean hasNext  = false;
    Object  elem     = null;
    int     location = c == null ? 0 : c.methodLocation;
    while (!reachedEnd && buffer.free() > 0) {
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
        throw new Continuation(cont, hasNext$cHandle, location + 1, null, new Object[]{this });
      }
    }
    // Either we have reachedEnd of underlying iterator or we have a full buffer
    return !reachedEnd;
  }

  @Override
  public Object next() {
    // Assume that hasNext() has already been called so no need for async handling
    if (buffer.free() > 0) {
      throw new IllegalStateException("next() called before hasNext()");
    }
    return buffer.remove();
  }

}
