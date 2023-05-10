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

import java.util.Iterator;

/**
 * Iterate and use given predicate to determine how many elements match predicate based on type of matching
 * we are doing:
 * <dl>
 *   <dd>ALL</dd><dt>True if all elements match (empty list returns true)</dt>
 *   <dd>NONE</dd><dt>True if no element matches (empty list returns true)</dt>
 *   <dd>ANY</dd><dt>True if any element matches (empty list returns false)</dt>
 * </dl>
 */
public class MatchCounter {
  Iterator     iter;
  String       source;
  int          offset;
  JactlMethodHandle predicate;
  MatchType    matchType;

  enum MatchType { NONE, ANY, ALL };

  MatchCounter(Iterator iter, String source, int offset, JactlMethodHandle predicate, MatchType matchType) {
    this.iter      = iter;
    this.source    = source;
    this.offset    = offset;
    this.predicate = predicate;
    this.matchType = matchType;
  }

  public static JactlMethodHandle matching$cHandle = RuntimeUtils.lookupMethod(MatchCounter.class, "matching$c", Object.class, Continuation.class);
  public static Object matching$c(Continuation c) {
    return ((MatchCounter)c.localObjects[2]).matching(c);
  }

  public boolean matching(Continuation c) {
    int     location   = c == null ? 0     : c.methodLocation;
    boolean hasNext    = c == null ? false : (boolean)c.localObjects[0];
    Object  elem       = c == null ? null  : c.localObjects[1];
    Object  filterCond = null;
    try {
      // Implement a simple state machine. Every even state is where we attempt to synchronously do something.
      // The immediately following odd state is for when the even state throws due to async behaviour, and we
      // then get the result back in the Continuation.result field. If the even state does not throw, we set the
      // location to skip straight to the next even state. When the odd state throws we catch and rethrow our
      // own Continuation chained to the caught one and set the location in our Continuation to location + 1
      // so that when we are resumed we go to the corresponding odd state.
      while (true) {
        // We loop until we find a matching element or until there are no more elements from our wrapped iterator
        switch (location) {
          case 0:
            hasNext = iter.hasNext();
            location = 2;
            break;
          case 1:
            hasNext = (boolean) c.getResult();
            location = 2;
            break;
          case 2:
            // Return if there are no more elements
            if (!hasNext) {
              return matchType == MatchType.ALL || matchType == MatchType.NONE;
            }
            elem = iter.next();
            location = 4;
            break;
          case 3:
            elem = c.getResult();
            location = 4;
            break;
          case 4:
            elem = RuntimeUtils.mapEntryToList(elem);
            filterCond = predicate == null ? RuntimeUtils.isTruth(elem, false)
                                           : predicate.invoke((Continuation) null, source, offset, new Object[]{elem});
            location = 6;
            break;
          case 5:
            filterCond = c.getResult();
            location = 6;
            break;
          case 6:
            // If we have found an element that satisfies the filter condition then return
            boolean matches = RuntimeUtils.isTruth(filterCond, false);
            switch (matchType) {
              case NONE: if (matches)  { return false; }; break;
              case ANY:  if (matches)  { return true;  }; break;
              case ALL:  if (!matches) { return false; }; break;
            }
            // Keep going
            location = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + location);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, matching$cHandle, location + 1, null, new Object[]{hasNext, elem, this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
}
