/*
 * Copyright © 2022-2026 James Crawford
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

import io.jactl.Utils;
import io.jactl.compiler.MethodRef;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  public static final String    INTERNAL_NAME  = Type.getInternalName(CircularBuffer.class);
  public static final MethodRef ADD_METHOD     = Utils.getMethod(CircularBuffer.class, "add", Object.class);
  public static final MethodRef REMOVE_METHOD  = Utils.getMethod(CircularBuffer.class, "remove");
  public static final MethodRef SIZE_METHOD    = Utils.getMethod(CircularBuffer.class, "size");
  public static final MethodRef FREE_METHOD    = Utils.getMethod(CircularBuffer.class, "free");
  public static final MethodRef TO_LIST_METHOD = Utils.getMethod(CircularBuffer.class, "toList");
  public static final MethodRef CLEAR_METHOD   = Utils.getMethod(CircularBuffer.class, "clear");
  
  private static      int       VERSION                       = 1;
  T[]      buffer;
  int      bufferSize;
  boolean  canGrow = false;  // true if we can grow buffer when it fills up
  int      head = 0;
  int      tail = 0;

  public CircularBuffer() {}    // for checkpoint/restore

  public CircularBuffer(int capacity) {
    this.bufferSize = capacity + 1;
    buffer = (T[])new Object[this.bufferSize];
  }

  CircularBuffer(int capacity, boolean canGrow) {
    this(capacity);
    this.canGrow = canGrow;
  }

  /**
   * Number of elements in the buffer
   * @return number elements in buffer
   */
  public int size() {
    return (tail + bufferSize - head) % bufferSize;
  }

  /**
   * Add element to circular buffer, potentially pushing out the oldest
   * element if buffer is full and canGrow is false.
   * @param t  the new element
   * @return true if buffer is not yet full
   */
  public boolean add(T t) {
    if (free() == 0) {
      if (canGrow) {
        T[] newBuf = (T[])new Object[bufferSize << 1];
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
    return canGrow || free() > 0;
  }

  public T remove() {
    if (size() == 0) {
      return null;
    }

    T elem = (T)buffer[head];
    buffer[head] = null;
    head = (head + 1) % bufferSize;
    return elem;
  }

  public List<T> toList() {
    ArrayList<T> list = new ArrayList<>(size());
    for (int i = head; i != tail; i = (i + 1) % bufferSize) {
      list.add((T)buffer[i]);
    }
    return list;
  }
  
  /**
   * How much free space in the buffer
   * @return number of unoccupied elements in buffer
   */
  public int free() {
    return bufferSize - 1 - size();
  }

  public void clear() {
    Arrays.fill(buffer, null);
    head = tail = 0;
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeTypeEnum(BUILTIN);
    checkpointer.writeCInt(BuiltinFunctions.getClassId(CircularBuffer.class));
    checkpointer.writeCInt(VERSION);
    checkpointer.writeCInt(buffer.length);
    checkpointer.writeBoolean(canGrow);
    int size = size();
    checkpointer.writeCInt(size);
    for (int i = head; i != tail; i = (i + 1) % buffer.length) {
      checkpointer.writeObject(buffer[i]);
    }
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(BUILTIN);
    restorer.expectCInt(BuiltinFunctions.getClassId(CircularBuffer.class), "Bad class id");
    restorer.expectCInt(VERSION, "Bad version");
    bufferSize   = restorer.readCInt();
    canGrow      = restorer.readBoolean();
    buffer       = (T[])new Object[bufferSize];
    int size     = restorer.readCInt();
    for (int i = 0; i < size; i++) {
      buffer[i] = (T)restorer.readObject();
    }
    head = 0;
    tail = size;
  }
}
