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

import io.jactl.runtime.Location;

public class JactlError extends RuntimeException {

  private String   errorMessage;
  private Location location;

  /**
   * Constructor
   * @param error              the error message
   * @param location              the location where error occurred
   * @param captureStackTrace  whether to get stack trace or not
   */
  public JactlError(String error, Location location, boolean captureStackTrace) {
    this(error, location, captureStackTrace, null);
  }

  public JactlError(String error, Location location, boolean captureStackTrace, Throwable cause) {
    super(null, cause, false, captureStackTrace);
    if (cause != null) {
      String causeMsg = cause.getMessage();
      if (causeMsg == null) {
        causeMsg = cause.getClass().getSimpleName();
      }
      else {
        causeMsg += " (" + cause.getClass().getSimpleName() + ")";
      }
      error += ": " + causeMsg;
    }
    this.errorMessage = error;
    this.location = location;
  }

  public Location getLocation() {
    return location;
  }

  @Override
  public String getMessage() {
    if (location == null || location.getSource() == null) {
      return getSingleLineMessage();
    }
    return getSingleLineMessage() + "\n" + location.getMarkedSourceLine();
  }

  public String getSingleLineMessage() {
    if (location == null || location.getSource() == null) {
      return String.format("%s @ unknown location", errorMessage);
    }
    return String.format("%s @ line %d, column %d", errorMessage, location.getLineNum(), location.getColumn());
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
