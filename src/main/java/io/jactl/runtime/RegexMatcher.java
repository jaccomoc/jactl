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

import io.jactl.JactlType;
import io.jactl.Utils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Container to hold the Matcher and the source string so that we can tell
 * when doing "global" matching whether we continue matching from where we left
 * off or not.
 * <p>
 * We use this object for capture variables. Every reference to $1, $2, etc. is
 * turned into a reference that points to the pseudo $@ variable in the local scope
 * which is an instance of this class.
 * This means that the same RegexMatcher can be used by multiple regex patterns if
 * the current code block has multiple regexes.
 * </p>
 * In particular, we can support code like this:
 * <pre>
 *   while (x =~ /a/ &amp;&amp; y =~ /([a-z])/g) { x += $1 }
 * </pre>
 * <p>
 * The looping of the while block and use of the 'g' modifier requires us to remember
 * where we were when matching against y but since we reuse the same RegexMatcher (and
 * same Matcher) for the two regex matches we can't use the Matcher object to remember
 * where we were up to and have to track this ourselves.
 * </p>
 * <p>
 * Note that we only support one regex with the 'g' modifier in any while/for loop and
 * this is detected at compile time.
 * </p>
 * <p>
 * We therefore keep two Matcher objects, one for 'g' patterns and one for other patterns
 * but since we support checkpointing and restoration of state, we need to be able to
 * restore our state and continue from where we were which means we need to track our
 * matching anyway and can't rely on the Matcher internal state.
 * </p>
 */
public class RegexMatcher implements Checkpointable {
  private static int VERSION = 1;

  private static final ThreadLocal<LinkedHashMap<String, Pattern>> patternCache = ThreadLocal.withInitial(
    () -> new LinkedHashMap<>(16, 0.75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
        return size() > patternCacheSize;
      }
    });

  public static int patternCacheSize = 100;   // Per thread cache size

  private JactlMatcher globalMatcher    = new GlobalMatcher();
  private JactlMatcher nonGlobalMatcher = new NonGlobalMatcher();
  private boolean      lastWasGlobal;         // Whether last match was a global one
  public  boolean      captureAsNums;         // Whether to try to parse strings that look like numbers as numbers

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeType(JactlType.MATCHER);
    checkpointer.writeCint(VERSION);
    globalMatcher.checkpoint(checkpointer);
    nonGlobalMatcher.checkpoint(checkpointer);
    checkpointer.writeBoolean(lastWasGlobal);
    checkpointer._writeBoolean(captureAsNums);
  }

  @Override public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.MATCHER);
    restorer.expectCint(VERSION, "Bad version");
    globalMatcher.restore(restorer);
    nonGlobalMatcher.restore(restorer);
    lastWasGlobal = restorer.readBoolean();
    captureAsNums = restorer.readBoolean();
  }

  /**
   * Get Matcher for given string, regex, and modifiers. Uses a cache of Pattern objects to avoid continual
   * recompilation of the same patterns.
   *
   * @param str       the string
   * @param regex     the regex pattern
   * @param modifiers the modifiers
   * @param source    source code (for error reporting)
   * @param offset    offset into source code (for error reporting)
   * @return a Matcher
   */
  public static Matcher getMatcher(String str, String regex, String modifiers, String source, int offset) {
    if (str == null) {
      throw new NullError("Null string in regex match", source, offset);
    }
    if (regex == null) {
      throw new NullError("Null regex in regex match", source, offset);
    }
    var     cache   = patternCache.get();
    String  key     = regex + "/" + modifiers;
    Pattern pattern = cache.get(key);
    if (pattern == null) {
      try {
        int flags = 0;
        for (int i = 0; i < modifiers.length(); i++) {
          switch (modifiers.charAt(i)) {
            case Utils.REGEX_CASE_INSENSITIVE:
              flags += Pattern.CASE_INSENSITIVE;
              break;
            case Utils.REGEX_MULTI_LINE_MODE:
              flags += Pattern.MULTILINE;
              break;
            case Utils.REGEX_DOTALL_MODE:
              flags += Pattern.DOTALL;
              break;
            default:
              throw new RuntimeError("Unexpected regex modifier '" + modifiers.charAt(i) + "'", source, offset);
          }
        }
        pattern = Pattern.compile(regex, flags);
      }
      catch (PatternSyntaxException e) {
        throw new RuntimeError("Pattern error: " + e.getMessage(), source, offset);
      }
      cache.put(key, pattern);
      if (cache.size() > patternCacheSize) {
        cache.remove(cache.keySet().iterator().next());
      }
    }
    return pattern.matcher(str);
  }

  /**
   * We are doing a "find" rather than a "match" if the global modifier is set and the source string is unchanged. In
   * this case we continue the searching from the last unmatched char in the source string. If the source string has
   * changed then we revert to a "match". We update the Matcher in the RegexMatcher object if the Matcher changes.
   *
   * @param str            the string being matched
   * @param regex          the regex pattern
   * @param globalModifier true if find is a global find ('g' modifier used)
   * @param modifiers      other modifiers for the search (doesn't include 'g' or 'r' or 'n')
   * @param source         the source code
   * @param offset         the offset into the source
   * @return true if regex find/match succeeds
   */
  public boolean regexFind(String str, String regex, boolean globalModifier, String modifiers, String source, int offset) {
    if (globalModifier) {
      lastWasGlobal = true;
      return globalMatcher.regexFind(str, regex, modifiers, source, offset);
    }

    // No global modifier so start from scratch and leave lastPos untouched
    lastWasGlobal = false;
    return nonGlobalMatcher.regexFind(str, regex, modifiers, source, offset);
  }

  public boolean regexFindNext() {
    // Only ever called on global modifier for global search/replace
    return globalMatcher.regexFindNext();
  }

  public String regexSubstitute(String str, String regex, String replace, boolean globalModifier, String modifiers, String source, int offset) {
    if (globalModifier) {
      lastWasGlobal = true;
      return globalMatcher.regexSubstitute(str, regex, replace, modifiers, source, offset);
    }

    lastWasGlobal = false;
    return nonGlobalMatcher.regexSubstitute(str, regex, replace, modifiers, source, offset);
  }

  public Object regexGroup(int group) {
    return (lastWasGlobal ? globalMatcher : nonGlobalMatcher).regexGroup(group, captureAsNums);
  }

  public void appendReplacement(StringBuilder sb, String replacement) {
    (lastWasGlobal ? globalMatcher : nonGlobalMatcher).appendReplacement(sb, replacement);
  }

  public void appendTail(StringBuilder sb) {
    (lastWasGlobal ? globalMatcher : nonGlobalMatcher).appendTail(sb);
  }

  //////////////////////////////////////////////////////////////////

  private abstract static class JactlMatcher {
    public Matcher matcher;
    public String  str;            // String to match against
    public String  originalStr;    // Original value before being chopped down after checkpoint/restore
    public boolean matched;        // Result of last match
    public int     lastStart = -1; // Last start pos (if global matching)
    public int     lastPos;        // For global matches remembers where last match finished

    public String  regex;
    public String  modifiers;

    abstract boolean regexFind(String str, String regex, String modifiers, String source, int offset);
    abstract boolean regexFindNext();
    abstract String  regexSubstitute(String str, String regex, String replace, String modifiers, String source, int offset);
    abstract void _restore(boolean haveMatcher);

    public void checkpoint(Checkpointer checkpointer) {
      checkpointer.writeObject(str);
      checkpointer.writeObject(originalStr);
      checkpointer._writeBoolean(matched);
      checkpointer.writeCint(lastStart);
      checkpointer.writeCint(lastPos);
      checkpointer.writeObject(regex);
      checkpointer.writeObject(modifiers);
      checkpointer.writeBoolean(matcher != null);
    }

    public void restore(Restorer restorer) {
      str         = (String) restorer.readObject();
      originalStr = (String) restorer.readObject();
      matched     = restorer.readBoolean();
      lastStart   = restorer.readCint();
      lastPos     = restorer.readCint();
      regex       = (String) restorer.readObject();
      modifiers   = (String) restorer.readObject();
      boolean haveMatcher = restorer.readBoolean();
      _restore(haveMatcher);
    }

    protected void initMatcher(String str, String regex, String modifiers, String source, int offset) {
      this.matcher     = getMatcher(str, regex, modifiers, source, offset);
      this.str         = str;
      this.originalStr = str;
      this.regex       = regex;
      this.modifiers   = modifiers;
    }

    public Object regexGroup(int group, boolean captureAsNums) {
      if (!matched) {
        return null;
      }
      if (group > matcher.groupCount()) {
        return null;
      }
      if (!captureAsNums) {
        try {
          return matcher.group(group);
        }
        catch (IllegalStateException e) {
          // Can happen when using $1 etc after a /xxx/g pattern
          // has finished its matching
          return null;
        }
      }

      // See if we have a number we can parse
      String value = matcher.group(group);
      if (value == null) {
        return null;
      }
      if (value.indexOf('.') == -1) {
        try {
          return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
          // Too big
          return value;
        }
      }
      try {
        return new BigDecimal(value);
      }
      catch (NumberFormatException e) {
        return value;
      }
    }

    public void appendReplacement(StringBuilder sb, String replacement) {
      matcher.appendReplacement(sb, replacement);
    }

    public void appendTail(StringBuilder sb) {
      matcher.appendTail(sb);
    }
  }

  private static class GlobalMatcher extends JactlMatcher {
    @Override public boolean regexFind(String str, String regex, String modifiers, String source, int offset) {
      if (str == null) {
        return false;      // null never matches anything
      }

      // Check to see if the Matcher has the same source string (note we use == not .equals())
      if (!str.equals(this.originalStr) || !regex.equals(matcher.pattern().pattern()) || lastPos == -1) {
        lastPos = -1;
        initMatcher(str, regex, modifiers, source, offset);
      }
      return regexFindNext();
    }

    @Override public boolean regexFindNext() {
      matched = matcher.find();
      if (!matched) {
        lastPos = -1;
      }
      else {
        lastStart = lastPos;
        lastPos = matcher.end();
      }
      return matched;
    }

    @Override public String regexSubstitute(String str, String regex, String replace, String modifiers, String source, int offset) {
      initMatcher(str, regex, modifiers, source, offset);
      try {
        return matcher.replaceAll(replace);
      }
      catch (Exception e) {
        throw new RuntimeError("Error during regex substitution", source, offset, e);
      }
    }

    @Override void _restore(boolean haveMatcher) {
      if (haveMatcher) {
        if (lastStart > 0) {
          // Use shortened string if we have already matched and advanced before
          str = str.substring(lastStart);
          lastPos -= lastStart;
          lastStart = -1;
        }
        matcher = getMatcher(str, regex, modifiers, "", 0);
        matcher.find();
      }
    }
  }

  private static class NonGlobalMatcher extends JactlMatcher {
    @Override public boolean regexFind(String str, String regex, String modifiers, String source, int offset) {
      if (str == null) {
        return false;       // null never matches
      }
      initMatcher(str, regex, modifiers, source, offset);
      return matched = matcher.find();
    }

    @Override public boolean regexFindNext() {
      throw new IllegalStateException("Internal error: regexFindNext() invoked on non-global matcher");
    }

    @Override public String regexSubstitute(String str, String regex, String replace, String modifiers, String source, int offset) {
      initMatcher(str, regex, modifiers, source, offset);
      try {
        return matcher.replaceFirst(replace);
      }
      catch (Exception e) {
        throw new RuntimeError("Error during regex substitution", source, offset, e);
      }
    }

    @Override void _restore(boolean haveMatcher) {
      if (haveMatcher) {
        matcher = getMatcher(str, regex, modifiers, "", 0);
        // Repopulate match groups for capture vars
        matcher.find();
      }
    }
  }
}
