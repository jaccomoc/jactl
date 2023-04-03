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

public class DieError extends RuntimeError {
  String dieError;

  public DieError(String error, String source, int offset) {
    super(error == null || error.isEmpty() ? "Script death" : error, source, offset);
    dieError = error;
  }

  @Override
  public String getMessage() {
    if (dieError == null || dieError.isEmpty()) {
      return super.getMessage();
    }
    return dieError;
  }
}
