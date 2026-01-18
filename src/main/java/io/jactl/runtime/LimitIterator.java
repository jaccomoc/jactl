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
 * Async JactlIterator to support iterator.limit(n) where n &gt;= 0
 */
public class LimitIterator extends JactlIterator {
  private static int VERSION = 1;
  JactlIterator iter;
  boolean       reachedEnd = false;
  int           limit;
  int           count = 0;

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCInt(IteratorType.LIMIT.ordinal());
    checkpointer.writeCInt(VERSION);
    checkpointer.writeObject(iter);
    checkpointer._writeBoolean(reachedEnd);
    checkpointer.writeCInt(limit);
    checkpointer.writeCInt(count);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCInt(IteratorType.LIMIT.ordinal(), "Expected LIMIT");
    restorer.expectCInt(VERSION, "Bad version");
    iter       = (JactlIterator)restorer.readObject();
    reachedEnd = restorer.readBoolean();
    limit      = restorer.readCInt();
    count      = restorer.readCInt();
  }

  LimitIterator() {}

  LimitIterator(JactlIterator iter, int limit) {
    this.iter = iter;
    this.limit = limit;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(LimitIterator.class, IteratorType.LIMIT, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    return ((LimitIterator)c.localObjects[0]).doHasNext(c);
  }

  @Override
  public boolean hasNext() {
    if (reachedEnd || count >= limit) {
      return false;
    }
    return doHasNext(null);
  }

  private boolean doHasNext(Continuation c) {
    boolean hasNext  = false;
    int     location = c == null ? 0 : c.methodLocation;
    try {
      while (true) {
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
            if (!hasNext) {
              reachedEnd = true;
              return false;
            }
            return true;
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, location + 1, null, new Object[]{ this });
    }
  }

  public static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(LimitIterator.class, IteratorType.LIMIT, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((LimitIterator)c.localObjects[0]).doNext(c);
  }

  @Override
  public Object next() {
    if (reachedEnd || count >= limit) {
      throw new IllegalStateException("next() called after end reached");
    }
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      Object elem = null;
      while (true) {
        switch (location) {
          case 0: {
            elem = iter.next();
            location = 2;
            break;
          }
          case 1: {
            elem = c.getResult();
            location = 2;
            break;
          }
          case 2: {
            count++;
            return elem;
          }
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, next$cHandle, location + 1, null, new Object[]{this });
    }
  }
}
