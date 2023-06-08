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

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Execution environment for running Jactl scripts.
 * Provides mechanisms for setting timers and running work on non-blocking event-loop threads or on
 * blocking threads.
 */
public interface JactlEnv {
  /**
   * Schedule event on event-loop thread.
   * If passed a threadContext this can be used in situations where we want to reschedule something on the
   * same thread as a previous event.
   * This object can be passed the result of a previous getThreadContext() call and (if supported) will
   * result in the new event being run on the same thread as the previous one.
   * @param threadContext  identifies thread on which we want to run the event (null if we don't care)
   * @param event          the event to run
   */
  void scheduleEvent(Object threadContext, Runnable event);

  /**
   * Schedule event on given event-loop thread after given timeout has occurred.
   * @param threadContext  identifies thread on which we want to run the event (null if we don't care)
   * @param event          the event to run
   * @param timeMs         the timeout in milliseconds
   */
  void scheduleEvent(Object threadContext, Runnable event, long timeMs);

  /**
   * Schedule event on current event-loop thread after given timeout has occurred.
   * @param event          the event to run
   * @param timeMs         the timeout in milliseconds
   */
  void scheduleEvent(Runnable event, long timeMs);

  /**
   * Schedule a blocking event on a blocking thread. These events are allowed to block since they won't
   * hold up the events running on the event-loop threads. Since there are a fixed number of blocking
   * threads, however, this could result in queueing waiting for a blocking thread to become available.
   * @param blocking   the blocking event to run
   */
  void scheduleBlocking(Runnable blocking);

  /**
   * Get the current thread context of the event-loop thread we are running on.
   * If implementation does not support scheduling events on specific threads then this can return null.
   * @return  null if not on an event-loop thread or an implementation-specific identifier that identifies
   *          current thread
   */
  Object getThreadContext();

  /**
   * Save checkpoint with given id.
   * <p>NOTE: the checkpointId is an incrementing value and must be one more than the last checkpoint
   * we stored. This method should generate a RuntimeError and pass it to the resumer if an error occurs
   * during the save.</p>
   * <p>The resumer object should be invoked on a non-blocking scheduler thread so if saving requires
   * doing anything on a blocking thread then it is up to the implementation to make sure the resume
   * is scheduled back onto a non-blocking thread (potentially same one as the one that invoked the save).</p>
   * @param id           unique id that identifies script instance
   * @param checkpointId the checkpoint id for this instance
   * @param checkpoint   the checkpointed state to be saved
   * @param source       source code line (for error reporting)
   * @param offset       offset where checkpointing occurring (for errors)
   * @param result       result to pass to resumer once checkpoint has been saved (error passed in if error during save)
   * @param resumer      the code to invoke to resume execution once checkpoint has been saved
   */
  default void saveCheckpoint(UUID id, int checkpointId, byte[] checkpoint, String source, int offset, Object result, Consumer<Object> resumer) {
    resumer.accept(result);
  }

  /**
   * Delete checkpoint data for given id once script instance has completed
   * @param id           the id of the script instance
   * @param checkpointId the last checkpoint id
   */
  default void deleteCheckpoint(UUID id, int checkpointId) {}
}
