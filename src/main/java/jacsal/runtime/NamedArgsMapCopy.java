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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used when copying named args into map that we use at runtime to track which args we
 * have processed. We remove as we process and at the end if there are any left over we
 * know that we have additional arguments that shouldn't be there.
 * We use a new type here so that when invoking super.init() we know whether the args
 * have already been copied or not and whether to check for additional args at the end.
 * If the base class init sees a type of NamedArgsMapCopy it knows not to copy and not
 * to check for additional args.
 */
public class NamedArgsMapCopy extends LinkedHashMap {

  public NamedArgsMapCopy(Map arg) {
    super(arg);
  }
}
