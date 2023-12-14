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

import java.util.Arrays;

import static io.jactl.JactlType.TypeEnum.BUILTIN;

/**
 * CircularBuffer of given size.
 * <p>
 * NOTE: we allocate one element more so that we can tell
 * when buffer is full vs empty.
 * </p>
 * <pre>
 * head == tail     --&gt; empty
 * head - tail == 1 --&gt; full
 * </pre>
 * <p>
 *  If canGrow is set we will double size of underlying array if buffer fills up.
 *  Otherwise, we discard oldest element when new one is added.
 * </p>
 */
public class CircularBuffer<T> implements Checkpointable {
  private static int VERSION = 1;
  Object[] buffer;
  int      bufferSize;
  boolean  canGrow = false;  // true if we can grow buffer when it fills up
  int      head = 0;
  int      tail = 0;

  public CircularBuffer() {}    // for checkpoint/restore

  CircularBuffer(int capacity) {
    this.bufferSize = capacity + 1;
    buffer = new Object[this.bufferSize];
  }

  CircularBuffer(int capacity, boolean canGrow) {
    this(capacity);
    this.canGrow = canGrow;
  }

  /**
   * Number of elements in the buffer
   */
  int size() {
    return (tail + bufferSize - head) % bufferSize;
  }

  void add(T t) {
    if (free() == 0) {
      if (canGrow) {
        Object[] newBuf = new Object[bufferSize << 1];
        for (int i = head, dst = 0; i != tail; i = (i + 1) % bufferSize) {
          newBuf[dst++] = buffer[i];
          buffer[i] = null;
        }
        int size = size();
        bufferSize <<= 1;
        buffer = newBuf;
        head   = 0;
        tail   = size;
      }
      else {
        // Discard oldest if buffer full
        remove();
      }
    }
    buffer[tail] = t;
    tail = (tail + 1) % bufferSize;
  }

  T remove() {
    if (size() == 0) {
      return null;
    }

    T elem = (T)buffer[head];
    buffer[head] = null;
    head = (head + 1) % bufferSize;
    return elem;
  }

  int free() {
    return bufferSize - 1 - size();
  }

  void clear() {
    Arrays.fill(buffer, null);
    head = tail = 0;
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeTypeEnum(BUILTIN);
    checkpointer.writeCint(BuiltinFunctions.getClassId(CircularBuffer.class));
    checkpointer.writeCint(VERSION);
    checkpointer.writeCint(buffer.length);
    checkpointer._writeBoolean(canGrow);
    int size = size();
    checkpointer.writeCint(size);
    for (int i = head; i != tail; i = (i + 1) % buffer.length) {
      checkpointer.writeObject(buffer[i]);
    }
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(BUILTIN);
    restorer.expectCint(BuiltinFunctions.getClassId(CircularBuffer.class), "Bad class id");
    restorer.expectCint(VERSION, "Bad version");
    bufferSize   = restorer.readCint();
    canGrow      = restorer.readBoolean();
    buffer       = new Object[bufferSize];
    int size     = restorer.readCint();
    for (int i = 0; i < size; i++) {
      buffer[i] = restorer.readObject();
    }
    head = 0;
    tail = size;
  }
}
