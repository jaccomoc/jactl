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

/**
 * CircularBuffer of given size.
 * NOTE: we allocated one element more so that we can tell
 * when buffer is full vs empty.
 * head == tail     --> empty
 * head - tail == 1 --> full
 */
public class CircularBuffer<T> {
  Object[] buffer;
  int      bufferSize;
  int      head = 0;
  int      tail = 0;

  CircularBuffer(int capacity) {
    this.bufferSize = capacity + 1;
    buffer = new Object[this.bufferSize];
  }

  /**
   * Number of elements in the buffer
   */
  int size() {
    return (tail + bufferSize - head) % bufferSize;
  }

  void add(T t) {
    if (free() == 0) {
      // Discard oldest if buffer full
      remove();
    }
    buffer[tail] = t;
    tail = (tail + 1) % bufferSize;
  }

  T remove() {
    if (size() == 0) {
      return null;
    }

    var elem = (T)buffer[head];
    head = (head + 1) % bufferSize;
    return elem;
  }

  int free() {
    return bufferSize - 1 - size();
  }
}
