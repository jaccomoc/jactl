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

package io.jactl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultEnv implements JactlEnv {
  private static int eventLoopThreads = Runtime.getRuntime().availableProcessors();
  private static int blockingThreads  = Runtime.getRuntime().availableProcessors() * 4;

  // Make static for the moment to avoid creating too many threads during unit test executions
  private static ExecutorService          eventLoop        = Executors.newFixedThreadPool(eventLoopThreads);
  private static ExecutorService          blockingExecutor = Executors.newFixedThreadPool(blockingThreads);
  private static ScheduledExecutorService timerService     = Executors.newSingleThreadScheduledExecutor();

  @Override
  public void scheduleEvent(Object threadContext, Runnable event) {
    eventLoop.submit(event);
  }

  @Override
  public void scheduleEvent(Object threadContext, Runnable event, long timeMs) {
    if (timeMs <= 0) {
      scheduleEvent(threadContext, event);
    }
    else {
      timerService.schedule(event, timeMs, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void scheduleEvent(Runnable event, long timeMs) {
    if (timeMs <= 0) {
      scheduleEvent(null, event);
    }
    else {
      timerService.schedule(event, timeMs, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void scheduleBlocking(Runnable blocking) {
    blockingExecutor.submit(blocking);
  }

  @Override
  public Object getThreadContext() {
    return null;
  }
}
