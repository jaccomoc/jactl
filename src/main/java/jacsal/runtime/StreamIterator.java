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

package jacsal.runtime;

import java.lang.invoke.MethodHandle;
import java.util.Iterator;

/**
 * Async iterator for stream() function.
 * Iterates by invoking (possibly async) closure until closure returns null.
 */
public class StreamIterator implements Iterator {
  String       source;
  int          offset;
  MethodHandle closure;
  Object       nextValue;
  boolean      haveValue = false;

  final static Object[] emptyArgs = new Object[0];

  StreamIterator(String source, int offset, MethodHandle closure) {
    this.source = source;
    this.offset = offset;
    this.closure = closure;
  }

  private static MethodHandle hasNextHandle = RuntimeUtils.lookupMethod(StreamIterator.class, "hasNext$c", Object.class, StreamIterator.class, Continuation.class);

  public static Object hasNext$c(StreamIterator iter, Continuation c) {
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
      nextValue = closure.invokeExact((Continuation)null, source, offset, emptyArgs);
      haveValue = true;
      return nextValue != null;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNextHandle.bindTo(this), 0, null, null);
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

  private static MethodHandle nextHandle = RuntimeUtils.lookupMethod(StreamIterator.class, "next$c", Object.class, StreamIterator.class, Continuation.class);

  public static Object next$c(StreamIterator iter, Continuation c) {
    return iter.nextValue;
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
      throw new Continuation(cont, nextHandle.bindTo(this), 0, null, null);
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

}
