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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.function.Consumer;

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

  public CompileError(String error, Location token, boolean captureStackTrace) {
    super(error, token, captureStackTrace);
  }

  public CompileError(List<CompileError> errors) {
    super(null, null, true);
    this.errors = errors;
  }

  public List<CompileError> getErrors() {
    return errors == null ? Utils.listOf(this) : errors;
  }

  @Override
  public String getMessage() {
    if (errors == null) {
      return super.getMessage();
    }
    return getMessage(false);
  }

  public String getMessage(boolean verbose) {
    StringBuilder sb = new StringBuilder();
    Consumer<CompileError> printError = e -> {
      if (verbose) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter out = new PrintWriter(stringWriter)) {
          e.printStackTrace(out);
          sb.append(stringWriter);
        }
        sb.append('\n');
      }
      else {
        sb.append(e.getMessage()).append('\n');
      }
    };

    if (this.errors != null) {
      sb.append(String.format("%d error%s found:\n", errors.size(), errors.size() > 1 ? "s" : ""));
      errors.forEach(printError);
    }
    else {
      printError.accept(this);
    }
    return sb.toString();
  }
}
