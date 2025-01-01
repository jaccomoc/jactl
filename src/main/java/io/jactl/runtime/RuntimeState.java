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

import io.jactl.JactlContext;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.Map;

/**
 * For the moment we hold the input and output here.
 * Need to work out whether to support per script input/output and if so how to
 * checkpoint and restore them.
 */
public class RuntimeState {
  private JactlContext        context;
  private Map<String, Object> globals;
  private PrintStream         output;
  private BufferedReader      input;

  private static ThreadLocal<RuntimeState> threadLocalState = ThreadLocal.withInitial(RuntimeState::new);

  public static RuntimeState getState() {
    RuntimeState state = threadLocalState.get();
    if (state == null) {
      state = new RuntimeState();
      threadLocalState.set(state);
    }
    return state;
  }

  public static String GET_CURRENT_GLOBALS = "getCurrentGlobals";
  public static Map getCurrentGlobals() {
    RuntimeState state = getState();
    if (state == null) {
      return null;
    }
    return state.globals;
  }

  public static void setState(RuntimeState value) {
    threadLocalState.set(value);
  }

  public static void resetState() {
    threadLocalState.set(null);
  }

  public static void setState(JactlContext context, Map<String, Object> globals, BufferedReader input, PrintStream output) {
    RuntimeState state = getState();
    state.context = context;
    state.input = input;
    state.output = output;
    state.globals = globals;
  }

  public BufferedReader getInput() {
    return input;
  }

  public PrintStream getOutput() {
    return output;
  }

  public Map<String, Object> getGlobals() {
    return globals;
  }

  public JactlContext getContext() { return context; }
}
