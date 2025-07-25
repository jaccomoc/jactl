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
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * We keep some state for the current script invocation in a ThreadLocal.
 * When suspending/resuming we need to make sure that this state is preserved so that the resumed
 * script invocation can still see the original state.
 * </p><p>
 * For the moment we hold the input and output here.
 * Need to work out whether to support per script input/output and if so how to
 * checkpoint and restore them.
 * </p>
 * We also keep a loop iteration count here which is used if the maxLoopIterations limit is set
 * on the JactlContext. If enabled at compile time, we periodically update this counter to keep
 * track of the total number of loop iterations performed and if the limit is reached we will
 * throw a TimeoutError.
 */
public class RuntimeState {
  private JactlContext        context;
  private Map<String, Object> globals;
  private PrintStream         output;
  private BufferedReader      input;
  private long                loopIterationCount;
  private long                endTime;

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
  public static Map getCurrentGlobals() { return getState().globals; }

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
    if (context.maxExecutionTimeMs >= 0) {
      state.endTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(context.maxExecutionTimeMs);
    }
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

  private static final int TIMEOUT_FREQ_CHECK = Integer.getInteger("jactl.loop.timeout-freq-check", 100);

  public static final String UPDATE_ITERATION_COUNT = "updateIterationCount";
  public static void updateIterationCount(String source, int offset) {
    RuntimeState state = getState();
    long         limit = state.context.maxLoopLimit;
    if (limit >= 0 && state.loopIterationCount >= limit) {
      throw new TimeoutError("Loop iterations limit of " + limit + " exceeded (count=" + state.loopIterationCount + ")", source, offset);
    }
    // Increment after because we insert check just before body of loop
    state.loopIterationCount++;

    // Every 100th time check for timeout
    if (state.loopIterationCount % TIMEOUT_FREQ_CHECK == 0) {
      checkTimeout(source, offset);
    }
  }

  public static final String CHECK_TIMEOUT = "checkTimeout";
  public static void checkTimeout(String source, int offset) {
    RuntimeState state = getState();
    if (state.endTime > 0 && System.nanoTime() >= state.endTime) {
      throw new TimeoutError("Script execution exceeded max time of " + state.context.maxExecutionTimeMs + "ms", source, offset);
    }
  }
}
