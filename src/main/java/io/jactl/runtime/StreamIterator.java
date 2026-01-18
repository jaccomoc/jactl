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
 * Async iterator for stream() function.
 * Iterates by invoking (possibly async) closure until closure returns null.
 */
public class StreamIterator extends JactlIterator {
  private static int VERSION = 1;
  String            source;
  int               offset;
  JactlMethodHandle closure;
  Object            nextValue;
  boolean           haveValue = false;

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(ITERATOR);
    checkpointer.writeCInt(IteratorType.STREAM.ordinal());
    checkpointer.writeCInt(VERSION);
    checkpointer.writeObject(source);
    checkpointer.writeCInt(offset);
    checkpointer.writeObject(closure);
    checkpointer.writeObject(nextValue);
    checkpointer._writeBoolean(haveValue);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
    restorer.expectCInt(IteratorType.STREAM.ordinal(), "Expected STREAM");
    restorer.expectCInt(VERSION, "Bad version");
    source    = (String)restorer.readObject();
    offset    = restorer.readCInt();
    closure   = (JactlMethodHandle)restorer.readObject();
    nextValue = restorer.readObject();
    haveValue = restorer.readBoolean();
  }

  final static Object[] emptyArgs = new Object[0];

  StreamIterator() {}

  StreamIterator(String source, int offset, JactlMethodHandle closure) {
    this.source = source;
    this.offset = offset;
    this.closure = closure;
  }

  public static JactlMethodHandle hasNext$cHandle = RuntimeUtils.lookupMethod(StreamIterator.class, IteratorType.STREAM, "hasNext$c", Object.class, Continuation.class);
  public static Object hasNext$c(Continuation c) {
    StreamIterator iter = (StreamIterator)c.localObjects[0];
    iter.haveValue = true;
    try {
      iter.nextValue = c.getResult();
    }
    catch (NullError e) {
      iter.nextValue = null;
    }
    return iter.nextValue != null;
  }

  @Override
  public boolean hasNext() {
    if (haveValue) {
      return nextValue != null;
    }
    try {
      nextValue = closure.invoke((Continuation)null, source, offset, emptyArgs);
      haveValue = true;
      return nextValue != null;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNext$cHandle, 0, null, new Object[]{this });
    }
    catch (NullError e) {
      haveValue = true;
      nextValue = null;
      return false;
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeError("Unexpected error: " + e, source, offset, e);
    }
  }

  public static JactlMethodHandle next$cHandle = RuntimeUtils.lookupMethod(StreamIterator.class, IteratorType.STREAM, "next$c", Object.class, Continuation.class);
  public static Object next$c(Continuation c) {
    return ((StreamIterator)c.localObjects[0]).nextValue;
  }

  @Override
  public Object next() {
    try {
      if (!haveValue) {
        hasNext();
      }
      if (nextValue != null) {
        haveValue = false;
      }
      return nextValue;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, next$cHandle, 0, null, new Object[]{this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

}
