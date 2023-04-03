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

import java.util.List;

public class CompileError extends JactlError {

  private List<CompileError> errors;

  /**
   * Create a compile error
   * @param error   the error message
   * @param token   the location where error occurred
   */
  public CompileError(String error, Location token) {
    super(error, token, true);
  }

  public CompileError(List<CompileError> errors) {
    super(null, null, true);
    this.errors = errors;
  }

  @Override
  public String getMessage() {
    if (this.errors != null) {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("%d error%s found:\n", errors.size(), errors.size() > 1 ? "s" : ""));
      errors.forEach(e -> sb.append(e.getMessage()).append('\n'));
      return sb.toString();
    }
    else {
      return super.getMessage();
    }
  }
}
