/*
 * Copyright 2022 James Crawford
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
 */

package jacsal;

public class JacsalError extends RuntimeException {

  private String errorMessage;
  private Token  token;

  /**
   * Constructor
   * @param error              the error message
   * @param token              the location where error occurred
   * @param captureStackTrace  whether to get stack trace or not
   */
  public JacsalError(String error, Token token, boolean captureStackTrace) {
    super(null, null, false, captureStackTrace);
    this.errorMessage = error;
    this.token        = token;
  }

  @Override
  public String getMessage() {
    return String.format("%s @ line %d, column %d%n%s", errorMessage, token.getLine(), token.getColumn(), token.getMarkedSourceLine());
  }
}
