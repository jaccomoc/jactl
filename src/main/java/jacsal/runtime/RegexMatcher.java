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

import java.util.regex.Matcher;

/**
 * Container to hold the Matcher and the source string so that we can tell
 * when doing "global" matching whether we continue matching from where we left
 * off or not.
 */
public class RegexMatcher {
  public  Matcher    matcher;
  public  String     str;          // String to match against
  public  boolean    matched;      // Result of last match
}
