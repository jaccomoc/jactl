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

package jacsal.runtime;

/**
 * We use objects of this type to hold the string and pattern we want to match against
 * in situations where it is ambiguous whether a regex match is required or whether we
 * should just turn the /regex/ into a string.
 *
 * For example:
 *   def x = /xyz/
 * In this context we don't know whether x should be a boolean which is the result of
 *   it =~ /xyz/
 * or whether x should be the string "xyz".
 *
 * If there is no variable called it then we know it should not ever be a regex match
 * and we treat it as a string. If there is a variable (implicit or explicit) called "it"
 * then we do the match since we may need to access $n variables and remember whether it
 * was a match or not.
 *
 * If x is then used in a context which requires a boolean then we know we should do the
 * regex match but if it is used as a string we just return the string.
 *
 * We delay the regex pattern match until we know that we actually need to do the match.
 */
public class RegexMatch {
  boolean matched;
  String  pattern;
  public RegexMatch(boolean matched, String pattern) {
    this.matched = matched;
    this.pattern = pattern;
  }

  @Override public String toString() {
    return pattern;
  }
}
