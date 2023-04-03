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

import java.io.BufferedReader;
import java.io.PrintStream;

public class RuntimeState {
  PrintStream    output;
  BufferedReader input;

  private static ThreadLocal<RuntimeState> state = new ThreadLocal<>() {
    @Override
    protected RuntimeState initialValue() {
      return new RuntimeState();
    }
  };

  static RuntimeState getState() {
    return state.get();
  }

  static void setState(RuntimeState value) {
    state.set(value);
  }

  public static void setOutput(Object out) {
    if (out != null && !(out instanceof PrintStream)) {
      throw new IllegalArgumentException("Global 'out' must be a PrintStream not " + out.getClass().getName());
    }
    getState().output = (PrintStream)out;
  }

  public static void setInput(Object in) {
    if (in != null && !(in instanceof BufferedReader)) {
      throw new IllegalArgumentException("Global 'in' must be a BufferedReader not " + in.getClass().getName());
    }
    getState().input = (BufferedReader)in;
  }
}
