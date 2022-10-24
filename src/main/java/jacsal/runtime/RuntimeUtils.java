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

import jacsal.TokenType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RuntimeUtils {

  public static final String PLUS_PLUS     = "++";
  public static final String MINUS_MINUS   = "--";
  public static final String PLUS          = "+";
  public static final String MINUS         = "-";
  public static final String STAR          = "*";
  public static final String COMPARE       = "<=>";
  public static final String SLASH         = "/";
  public static final String PERCENT       = "%";
  public static final String PLUS_EQUAL    = "+=";
  public static final String MINUS_EQUAL   = "-=";
  public static final String STAR_EQUAL    = "*=";
  public static final String SLASH_EQUAL   = "/=";
  public static final String PERCENT_EQUAL = "%=";

  public static final String BANG_EQUAL         = "!=";
  public static final String EQUAL_EQUAL        = "==";
  public static final String LESS_THAN          = "<";
  public static final String LESS_THAN_EQUAL    = "<=";
  public static final String GREATER_THAN       = ">";
  public static final String GREATER_THAN_EQUAL = ">=";
  public static final String IN                 = "in";
  public static final String BANG_IN            = "!in";
  public static final String INSTANCE_OF        = "instanceof";
  public static final String BANG_INSTANCE_OF   = "!instanceof";

  private static final ThreadLocal<LinkedHashMap<String, Pattern>> patternCache = new ThreadLocal<>() {
    @Override protected LinkedHashMap<String, Pattern> initialValue() {
      return new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
          return size() > patternCacheSize;
        }
      };
    }
  };

  public  static int patternCacheSize = 100;   // Per thread cache size

  // Special marker value to indicate that we should use whatever type of default makes sense
  // in the context of the operation being performed. Note that we use an integer value of 0
  // as a special marker since that way when we need the before value of an inc/dec it will
  // have the right value but we can still check specifically for this value when doing
  // x.a += 'some string' as this turns into x.a = DEFAULT_VALUE + 'some string' and so
  // when the rhs is a string we turn DEFAULT_VALUE into ''.
  public static final Object DEFAULT_VALUE = new Integer(0);

  public static String getOperatorType(TokenType op) {
    if (op == null) {
      return null;
    }
    switch (op) {
      case PLUS_PLUS:          return PLUS_PLUS;
      case MINUS_MINUS:        return MINUS_MINUS;
      case PLUS:               return PLUS;
      case MINUS:              return MINUS;
      case STAR:               return STAR;
      case SLASH:              return SLASH;
      case PERCENT:            return PERCENT;
      case COMPARE:            return COMPARE;
      case PLUS_EQUAL:         return PLUS_EQUAL;
      case MINUS_EQUAL:        return MINUS_EQUAL;
      case STAR_EQUAL:         return STAR_EQUAL;
      case SLASH_EQUAL:        return SLASH_EQUAL;
      case PERCENT_EQUAL:      return PERCENT_EQUAL;
      case BANG_EQUAL:         return BANG_EQUAL;
      case EQUAL_EQUAL:        return EQUAL_EQUAL;
      case LESS_THAN:          return LESS_THAN;
      case LESS_THAN_EQUAL:    return LESS_THAN_EQUAL;
      case GREATER_THAN:       return GREATER_THAN;
      case GREATER_THAN_EQUAL: return GREATER_THAN_EQUAL;
      case IN:                 return IN;
      case BANG_IN:            return BANG_IN;
      case INSTANCE_OF:        return INSTANCE_OF;
      case BANG_INSTANCE_OF:   return BANG_INSTANCE_OF;
      default: throw new IllegalStateException("Internal error: operator " + op + " not supported");
    }
  }

  public static BigDecimal decimalBinaryOperation(BigDecimal left, BigDecimal right, String operator, int maxScale, String source, int offset) {
    BigDecimal result;
    switch (operator) {
      case PLUS:    result = left.add(right);       break;
      case MINUS:   result = left.subtract(right);  break;
      case STAR:    result = left.multiply(right);  break;
      case PERCENT:
        try {
          result = left.remainder(right);
        }
        catch (ArithmeticException e) {
          if (e.getMessage().startsWith("Division by zero")) {
            throw new RuntimeError("Divide by zero error", source, offset);
          }
          else {
            throw new RuntimeError("Decimal error: " + e.getMessage(), source, offset);
          }
        }
        break;
      case SLASH:
        try {
          result = left.divide(right);
        }
        catch (ArithmeticException e) {
          if (e.getMessage().startsWith("Division by zero")) {
            throw new RuntimeError("Divide by zero error", source, offset);
          }

          // Result is non-terminating so try again with restricted scale
          result = left.divide(right, maxScale, RoundingMode.HALF_EVEN);
          result = result.stripTrailingZeros();
        }
        break;
      default: throw new IllegalStateException("Internal error: operator " + operator + " not supported for decimals");
    }
    if (result.scale() > maxScale) {
      result = result.setScale(maxScale, RoundingMode.HALF_EVEN);
    }
    return result;
  }

  public static int compareTo(Object obj1, Object obj2, String source, int offset) {
    if (obj1 == null && obj2 == null) { return 0;  }
    if (obj1 == null)                 { return -1; }
    if (obj2 == null)                 { return 1;  }
    if (obj1 instanceof Number && obj2 instanceof Number) {
      if (obj1 instanceof BigDecimal || obj2 instanceof BigDecimal) {
        return toBigDecimal(obj1).compareTo(toBigDecimal(obj2));
      }
      Number n1 = (Number)obj1;
      Number n2 = (Number)obj2;
      if (obj1 instanceof Double || obj2 instanceof Double) {
        return Double.compare(n1.doubleValue(), n2.doubleValue());
      }
      if (obj1 instanceof Long || obj2 instanceof Long) {
        return Long.compare(n1.longValue(), n2.longValue());
      }
      return Integer.compare(n1.intValue(), n2.intValue());
    }
    else
    if (obj1 instanceof Boolean && obj2 instanceof Boolean) {
      return Boolean.compare((boolean)obj1, (boolean)obj2);
    }
    else
    if (obj1 instanceof String && obj2 instanceof String) {
      int result = ((String)obj1).compareTo((String)obj2);
      return result < 0 ? -1 : result == 0 ? 0 : 1;
    }
    else {
      throw new RuntimeError("Cannot compare objects of type " + className(obj1) + " and " + className(obj2), source, offset);
    }
  }

  /**
   * Perform binary operation when types are not known at compile time.
   * NOTE: operator is a String that must be one of the static strings defined in this class
   *       as '==' is used to compare the strings for performance reasons.
   * @param left              left operand
   * @param right             right operand
   * @param operator          operator (as a String)
   * @param originalOperator  original operator (as a String) (will be -- or ++ if inc/dec turned
   *                          into binaryOp or null otherwise)
   * @param maxScale          maximum scale for BigDecimal operations
   * @param source            source code
   * @param offset            offset into source code of operator
   * @return result of operation
   */
  public static Object binaryOp(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (left == null) {
      throwOperandError(left, true, operator, source, offset);
    }
    String rightString = castToString(right);
    if (left == DEFAULT_VALUE && rightString != null && operator == PLUS) {
      // Special case to support: x.a += 'some string'
      // If x.a doesn't yet exist we create with default of empty string in this context.
      left = "";
    }

    if (!(left instanceof Number) && (originalOperator == PLUS_PLUS || originalOperator == MINUS_MINUS)) {
      throw new RuntimeError("Non-numeric operand for left-hand side of " + (originalOperator == PLUS_PLUS ? "++" : "--") + ": was String", source, offset);
    }

    String leftString = castToString(left);
    if (leftString != null) {
      // Make sure we are not trying to inc/dec a string since -- or ++ on an unknown type
      // will be turned into equivalent of x = x + 1 (for example) and it won't be until we
      // get here that we can check if x is a string or not.
      if (operator == PLUS) {
        return leftString.concat(right == null ? "null"
                                               : rightString == null ? toString(right) : rightString);
      }
      if (operator == STAR) {
        if (right instanceof Number) {
          return leftString.repeat(((Number)right).intValue());
        }
        throw new RuntimeError("Right-hand side of string repeat operator must be numeric but found " + className(right), source, offset);
      }
    }

    if (left instanceof List) {
      if (operator != PLUS) {
        throw new RuntimeError("Non-numeric operand for " + originalOperator + " of type " + className(left), source, offset);
      }
      return listAdd((List)left, right, originalOperator == PLUS_EQUAL);
    }

    if (left instanceof Map && right instanceof Map) {
      if (operator != PLUS) {
        throw new RuntimeError("Non-numeric operand for " + originalOperator + " of type " + className(left), source, offset);
      }
      return mapAdd((Map)left, (Map)right, originalOperator == PLUS_EQUAL);
    }

    // All other operations expect numbers so check we have numbers
    if (!(left instanceof Number))  { throwOperandError(left, true, operator, source, offset); }
    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    if (operator != PLUS && operator != MINUS) {
      throw new IllegalStateException("Internal error: unexpected operation '" + operator + "' on types " + className(left) + " and " + className(right));
    }

    // Check for bitwise operations since we don't want to unnecessarily convert to double/BigDecimal...
    // TBD

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs + rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      return lhs + rhs;
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    return lhs + rhs;
  }

  public static Object plus(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (left == DEFAULT_VALUE || !(left instanceof Number)) {
      return binaryOp(left, right, operator, originalOperator, maxScale, source, offset);
    }

    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs + rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      return lhs + rhs;
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    return lhs + rhs;
  }

  public static Object multiply(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (isString(left)) {
      return binaryOp(left, right, operator, originalOperator, maxScale, source, offset);
    }

    if (!(left instanceof Number))  { throwOperandError(left, true, operator, source, offset); }
    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs * rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      return lhs * rhs;
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    return lhs * rhs;
  }

  public static Object minus(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (!(left instanceof Number))  { throwOperandError(left, true, operator, source, offset); }
    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs - rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      return lhs - rhs;
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    return lhs - rhs;
  }

  public static Object divide(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (!(left instanceof Number))  { throwOperandError(left, true, operator, source, offset); }
    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs / rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      try {
        return lhs / rhs;
      }
      catch (ArithmeticException e) {
        throw new RuntimeError("Divide by zero", source, offset);
      }
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    try {
      return lhs / rhs;
    }
    catch (ArithmeticException e) {
      throw new RuntimeError("Divide by zero", source, offset);
    }
  }

  public static Object remainder(Object left, Object right, String operator, String originalOperator, int maxScale, String source, int offset) {
    if (!(left instanceof Number))  { throwOperandError(left, true, operator, source, offset); }
    if (!(right instanceof Number)) { throwOperandError(right, false, operator, source, offset); }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs % rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      try {
        return lhs % rhs;
      }
      catch (ArithmeticException e) {
        throw new RuntimeError("Divide by zero", source, offset);
      }
    }

    // Must be integers
    int lhs = (int)left;
    int rhs = (int)right;
    try {
      return lhs % rhs;
    }
    catch (ArithmeticException e) {
      throw new RuntimeError("Divide by zero", source, offset);
    }
  }

  private static void throwOperandError(Object obj, boolean isLeft, String operator, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null operand for " + (isLeft?"left":"right") + "-hand side of '" + operator + "'", source, offset);
    }
    throw new RuntimeError("Non-numeric operand for " + (isLeft?"left":"right") + "-hand side of '" + operator + "': was " + className(obj), source, offset);
  }

  public static boolean booleanOp(Object left, Object right, String operator, String source, int offset) {
    // Use == to compare since we guarantee that the actual string for the operator is passed in
    if (operator == EQUAL_EQUAL) {
      if (left == right)                 { return true;  }
      if (left == null || right == null) { return false; }
    }
    if (operator == BANG_EQUAL) {
      if (left == right)                 { return false; }
      if (left == null || right == null) { return true;  }
    }
    if (operator == IN || operator == BANG_IN) {
      throw new UnsupportedOperationException();
    }

    String leftString;
    String rightString;

    // We are left with the comparison operators
    int comparison;
    if (left == null && right == null)  { comparison = 0;  }
    else if (left == null)              { comparison = -1; }
    else if (right == null)             { comparison = 1;  }
    else if (left instanceof Boolean && right instanceof Boolean) {
      comparison = Boolean.compare((boolean)left, (boolean)right);
    }
    else if (left instanceof Number && right instanceof Number) {
      if (left instanceof BigDecimal || right instanceof BigDecimal) {
        comparison = toBigDecimal(left).compareTo(toBigDecimal(right));
      }
      else
      if (left instanceof Double || right instanceof Double) {
        comparison = Double.compare(((Number)left).doubleValue(), ((Number)right).doubleValue());
      }
      else
      if (left instanceof Long || right instanceof Long) {
        comparison = Long.compare(((Number)left).longValue(), ((Number)right).longValue());
      }
      else {
        comparison = Integer.compare((int)left, (int)right);
      }
    }
    else if ((leftString = castToString(left)) != null && (rightString = castToString(right)) != null) {
      comparison = leftString.compareTo(rightString);
    }
    else if (operator == EQUAL_EQUAL || operator == BANG_EQUAL) {
      return (operator == EQUAL_EQUAL) == left.equals(right);
    }
    else {
      throw new RuntimeError("Object of type " + className(left) + " not comparable with object of type " + className(right), source, offset);
    }

    if (operator == EQUAL_EQUAL)        { return comparison == 0; }
    if (operator == BANG_EQUAL)         { return comparison != 0; }
    if (operator == LESS_THAN)          { return comparison < 0; }
    if (operator == LESS_THAN_EQUAL)    { return comparison <= 0; }
    if (operator == GREATER_THAN)       { return comparison > 0; }
    if (operator == GREATER_THAN_EQUAL) { return comparison >= 0; }
    throw new IllegalStateException("Internal error: unexpected operator " + operator);
  }

  public static Object negateNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).negate(); }
    if (obj instanceof Double)     { return -(double)obj; }
    if (obj instanceof Long)       { return -(long)obj; }
    if (obj instanceof Integer)    { return -(int)obj; }
    throw new RuntimeError("Type " + className(obj) + " cannot be negated", source, offset);
  }

  public static Object incNumber(Object obj, String operator, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).add(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj + 1; }
    if (obj instanceof Long)       { return (long)obj + 1; }
    if (obj instanceof Integer)    { return (int)obj + 1; }
    return binaryOp(obj, 1, PLUS, operator, -1, source, offset);
  }

  public static Object decNumber(Object obj, String operator, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).subtract(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj - 1; }
    if (obj instanceof Long)       { return (long)obj - 1; }
    if (obj instanceof Integer)    { return (int)obj - 1; }
    return binaryOp(obj, 1, MINUS, operator, -1, source, offset);
  }

  public static String stringRepeat(String str, int count, String source, int offset) {
    if (count < 0) {
      throw new RuntimeError("String repeat count must be >= 0", source, offset);
    }
    ensureNonNull(str, source, offset);
    return str.repeat(count);
  }

  private static Matcher getMatcher(String str, String regex, String modifiers, String source, int offset) {
    var cache = patternCache.get();
    String key = regex + "/" + modifiers;
    Pattern pattern = cache.get(key);
    if (pattern == null) {
      try {
        int flags = 0;
        for (int i = 0; i < modifiers.length(); i++) {
          switch (modifiers.charAt(i)) {
            case 'i': flags += Pattern.CASE_INSENSITIVE; break;
            case 'm': flags += Pattern.MULTILINE;        break;
            case 's': flags += Pattern.DOTALL;           break;
            default: throw new IllegalStateException("Internal error: unexpected regex modifier '" + modifiers.charAt(i) + "'");
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
   * We are doing a "find" rather than a "match" if the global modifier is set and the
   * source string is unnchanged. In this case we continue the searching from the last
   * unmatched char in the source string.
   * If the source string has changed then we revert to a "match".
   * We update the Matcher in the RegexMatcher object if the Matcher changes.
   * @return true if regex find/match succeeds
   */
  public static boolean regexFind(RegexMatcher regexMatcher, String str, String regex, boolean globalModifier, String modifiers, String source, int offset) {
    if (globalModifier) {
      // Check to see if the Matcher has the same source string (note we use == not .equals())
      if (regexMatcher.str == str) {
        Matcher matcher = regexMatcher.matcher;
        if (regex.equals(matcher.pattern().pattern())) {
          return regexMatcher.matched = matcher.find();
        }
        // Same string but regex has changed
        int pos = matcher.end();
        regexMatcher.matcher = matcher = getMatcher(str, regex, modifiers, source, offset);
        return regexMatcher.matched = matcher.find();
      }
    }

    // Different string or no global modifier so start again from scratch
    Matcher matcher = getMatcher(str, regex, modifiers, source, offset);
    regexMatcher.matcher = matcher;
    regexMatcher.str = str;
    return regexMatcher.matched = matcher.find();
  }

  public static String regexSubstitute(RegexMatcher regexMatcher, String str, String regex, String replace, boolean globalModifier, String modifiers, String source, int offset) {
    Matcher matcher = regexMatcher.matcher = getMatcher(str, regex, modifiers, source, offset);
    regexMatcher.str = null;   // We never want to continue regex search after a substitute
    try {
      if (globalModifier) {
        return matcher.replaceAll(replace);
      }
      else {
        return matcher.replaceFirst(replace);
      }
    }
    catch (Exception e) {
      throw new RuntimeError("Error during regex substitution: " + e.getMessage(), source, offset, e);
    }
  }

  public static String regexGroup(RegexMatcher regexMatcher, int group) {
    if (!regexMatcher.matched) {
      return null;
    }
    final var matcher = regexMatcher.matcher;
    if (group > matcher.groupCount()) {
      return null;
    }
    return matcher.group(group);
  }

  /**
   * Add to a list. If second object is a list then we concatenat the lists.
   * Otherwise object is added to and of the list.
   * @param list         the list
   * @param obj          object to add or merge with list
   * @param isPlusEqual  whether we are doing += or just +
   * @return new list which is result of adding second object to first list
   */
  public static List listAdd(List list, Object obj, boolean isPlusEqual) {
    // If ++= then add to existing list rather than creating a new one
    List result = isPlusEqual ? list : new ArrayList<>(list);
    if (obj instanceof List) {
      result.addAll((List)obj);
    }
    else {
      result.add(obj);
    }
    return result;
  }

  /**
   * Add twp maps together. The result is a new map with a merge of the
   * keys and values from the two maps. If the same key appears in both
   * maps the value from the second map "overwrites" the other value and
   * becomes the value of the key in the resulting map.
   * @return a new map which is the combination of the two maps
   */
  public static Map mapAdd(Map map1, Map map2, boolean isPlusEqual) {
    // If plusEqual then just merge map2 into map1
    Map result = isPlusEqual ? map1 : new HashMap(map1);
    result.putAll(map2);
    return result;
  }

  /**
   * Return true if object satisfies truthiness check:
   *   null    --> false
   *   boolean --> value of the boolean
   *   number  --> true if non-zero
   *   String  --> true if non-empty
   *   Object  --> true if non-null
   * If negated is true then test is inverted and we test for "falsiness".
   */
  public static boolean isTruth(Object value, boolean negated) {
    if (value == null)               { return negated; }
    if (value instanceof Boolean)    { return negated != (boolean) value; }
    if (value instanceof String)     { return negated == ((String) value).isEmpty(); }
    if (value instanceof Integer)    { return negated == ((int) value == 0); }
    if (value instanceof Long)       { return negated == ((long)value == 0); }
    if (value instanceof Double)     { return negated == ((double)value == 0); }
    if (value instanceof BigDecimal) { return negated == ((BigDecimal) value).stripTrailingZeros().equals(BigDecimal.ZERO); }
    return !negated;
  }

  public static String toString(Object obj) {
    if (obj == null) {
      return "null";
    }
    if (obj instanceof Object[]) {
      obj = Arrays.asList((Object[])obj);
    }
    if (obj instanceof List) {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      List list = (List)obj;
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) { sb.append(", "); }
        sb.append(toString(list.get(i)));
      }
      sb.append(']');
      return sb.toString();
    }
    if (obj instanceof Map) {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      boolean first = true;
      for (Iterator<Map.Entry<String,Object>> iter = ((Map)obj).entrySet().iterator(); iter.hasNext();) {
        if (!first) {
          sb.append(", ");
        }
        else {
          first = false;
        }
        Map.Entry entry = iter.next();
        sb.append(toString(entry.getKey())).append(':').append(toString(entry.getValue()));
      }
      if (first) {
        // Empty map
        sb.append(':');
      }
      sb.append(']');
      return sb.toString();
    }
    return obj.toString();
  }

  /**
   * Load field from map/list or return default value if field does not exist
   * @param parent        parent (map or list)
   * @param field         field (field name or list index)
   * @param isDot         true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional    true if access type is '?.' or '?['
   * @param defaultValue  default value to return if field does not exist
   * @param source        source code
   * @param offset        offset into source for operation
   * @return the field value or the default value
   */
  public static Object loadFieldOrDefault(Object parent, Object field, boolean isDot, boolean isOptional, Object defaultValue, String source, int offset) {
    Object value = loadField(parent, field, isDot, isOptional, source, offset);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  public static Object invokeMethodOrField(Object parent, String field, boolean isOptional, Object args, String source, int offset) {
    if (parent == null) {
      if (isOptional) { return null; }
      throw new NullError("Tried to invoke method on null value", source, offset);
    }

    MethodHandle handle = Functions.lookupWrapper(parent, field);
    if (handle == null && parent instanceof Map) {
      Object value = ((Map)parent).get(field);
      if (value != null && !(value instanceof MethodHandle)) {
        throw new RuntimeError("Cannot invoke value of " + field + " (type is " + className(value) + ")", source, offset);
      }
      handle = (MethodHandle)value;
    }
    if (handle == null) {
      throw new NullError("No such method " + field + " for type " + className(parent), source, offset);
    }
    try {
      return handle.invokeExact((Continuation)null, source, offset, args);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeError("Error during method invocation: " + e.getMessage(), source, offset, e);
    }
  }

  public static Object invokeField(Object parent, String field, boolean isOptional, Object args, String source, int offset) {
    if (parent == null) {
      if (isOptional) { return null; }
      throw new NullError("Tried to invoke method on null valuet", source, offset);
    }

    if (!(parent instanceof Map)) {
      throw new IllegalStateException("Object of type " + className(parent) +  " does not support field access");
    }

    Object value = ((Map)parent).get(field);
    if (value == null) {
      throw new NullError("No such method " + field + " for type " + className(parent), source, offset);
    }
    if (!(value instanceof MethodHandle)) {
      throw new RuntimeError("Cannot invoke value of " + field + " (type is " + className(value) + ")", source, offset);
    }
    try {
      return ((MethodHandle)value).invokeExact((Continuation)null, source, offset, args);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeError("Error during method invocation: " + e.getMessage(), source, offset, e);
    }
  }

  /**
   * Load field from map/list (return null if field or index does not exist)
   * @param parent        parent (map or list)
   * @param field         field (field name or list index)
   * @param isDot         true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional    true if access type is '?.' or '?['
   * @param source        source code
   * @param offset        offset into source for operation
   * @return the field value or null
   */
  public static Object loadField(Object parent, Object field, boolean isDot, boolean isOptional, String source, int offset) {
    return doLoadOrCreateField(parent, field, isDot, isOptional, source, offset, false, false);
  }

  /**
   * Load method or field. Parent can be of any type in which case we first look for a method
   * of given name (in BuiltinFunctions) and return a MethodHandle to that method. If that
   * returns nothing we invoke the usual loadField method to get the field value.
   * @param parent        parent
   * @param field         field (field name or list index)
   * @param isDot         always true
   * @param isOptional    true if access type is '?.' or '?['
   * @param source        source code
   * @param offset        offset into source for operation
   * @return the field value or null
   */
  public static Object loadMethodOrField(Object parent, Object field, boolean isDot, boolean isOptional, String source, int offset) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Null value for Map/List during field access", source, offset);
    }

    if (field == null) {
      throw new NullError("Null value for field name", source, offset);
    }

    MethodHandle handle = Functions.lookupWrapper(parent, field.toString());
    if (handle != null) {
      return handle;
    }
    return doLoadOrCreateField(parent, field, isDot, isOptional, source, offset, false, false);
  }

  /**
   * Load field from map/list (return null if field or index does not exist)
   * @param parent        parent (map or list)
   * @param field         field (field name or list index)
   * @param isDot         true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional    true if access type is '?.' or '?['
   * @param isMap         if creating missing field we create a Map if true or a List if false
   * @param source        source code
   * @param offset        offset into source for operation
   * @return the field value or null
   */
  public static Object loadOrCreateField(Object parent, Object field, boolean isDot, boolean isOptional,
                                         boolean isMap, String source, int offset) {
    return doLoadOrCreateField(parent, field, isDot, isOptional, source, offset, true, isMap);
  }

  private static Object doLoadOrCreateField(Object parent, Object field, boolean isDot, boolean isOptional,
                                            String source, int offset, boolean createIfMissing, boolean isMap) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Null value for Map/List during field access", source, offset);
    }

    if (parent instanceof Map) {
      if (field == null) {
        throw new NullError("Null value for field name", source, offset);
      }
      String fieldName = field.toString();
      Map    map       = (Map) parent;
      Object value     = map.get(fieldName);
      if (createIfMissing && value == null) {
        value = isMap ? new HashMap<>() : new ArrayList<>();
        map.put(fieldName, value);
      }
      if (value == null) {
        // If we still can't find a field then if we have a method of the name return
        // its MethodHandle
        value = Functions.lookupWrapper(parent, fieldName);
      }

      return value;
    }

    // Check for accessing method by name
    String fieldString = castToString(field);
    if (isDot && !createIfMissing && fieldString != null) {
      var method = Functions.lookupWrapper(parent, fieldString);
      if (method != null) {
        return method;
      }
    }

    String parentString = null;
    if (!(parent instanceof List) && (parentString = castToString(parent)) == null && !(parent instanceof Object[])) {
      throw new RuntimeError("Invalid object type (" + className(parent) + "): expected Map/List" +
                             (isDot ? "" : " or String"), source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for " +
                             (parentString != null ? "String" : "List") +
                             " object", source, offset);
    }

    // Must be a List or String so field should be an index
    if (!(field instanceof Number)) {
      throw new RuntimeError("Non-numeric value for indexed access", source, offset);
    }

    int index = ((Number)field).intValue();
    if (index < 0) {
      throw new RuntimeError("Index must be >= 0 (was " + index + ")", source, offset);
    }

    if (parentString != null) {
      if (index >= parentString.length()) {
        throw new RuntimeError("Index (" + index + ") too large for String (length=" + parentString.length() + ")", source, offset);
      }
      return Character.toString(parentString.charAt(index));
    }

    if (parent instanceof Object[]) {
      Object[] arr = (Object[])parent;
      if (index < arr.length) {
        return arr[index];
      }
      return null;
    }

    List list = (List)parent;
    Object value = null;
    if (index < list.size()) {
      value = list.get(index);
    }

    if (createIfMissing && value == null) {
      value = isMap ? new HashMap<>() : new ArrayList<>();
      for (int i = list.size(); i < index + 1; i++) {
        list.add(null);
      }
      list.set(index, value);
    }
    return value;
  }

  /**
   * Store value into map/list field
   * @param parent        parent (map or list)
   * @param field         field (field name or list index)
   * @param value         the value to store
   * @param isDot         true if access type is '.' or '?.' (false for '[' or '?[')
   * @param source        source code
   * @param offset        offset into source for operation
   */
  public static void storeField(Object parent, Object field, Object value, boolean isDot, String source, int offset) {
    if (parent == null) {
      throw new NullError("Null value for Map/List storing field value", source, offset);
    }

    if (parent instanceof Map) {
      if (field == null) {
        throw new NullError("Null value for field name", source, offset);
      }
      String fieldName = field.toString();
      ((Map)parent).put(fieldName, value);
      return;
    }

    if (!(parent instanceof List)) {
      throw new RuntimeError("Invalid object type (" + className(parent) + ") for storing value: expected Map/List", source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for List object", source, offset);
    }

    // Must be a List
    if (!(field instanceof Number)) {
      throw new RuntimeError("Non-numeric value for index during List access", source, offset);
    }

    List list = (List)parent;
    int index = ((Number)field).intValue();
    if (index < 0) {
      throw new RuntimeError("Index for List access must be >= 0 (was " + index + ")", source, offset);
    }
    if (index >= list.size()) {
      // Grow list to required size
      for (int i = list.size(); i < index + 1; i++) {
        list.add(null);
      }
    }
    ((List)parent).set(index, value);
  }

  public static String castToString(Object obj, String source, int offset) {
    if (obj instanceof String) {
      return (String)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for String", source, offset);
    }
    throw new RuntimeError("Cannot convert object of type " + className(obj) + " to String", source, offset);
  }

  public static Map castToMap(Object obj, String source, int offset) {
    if (obj instanceof Map) {
      return (Map)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Map", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Map", source, offset);
  }

  public static List castToList(Object obj, String source, int offset) {
    if (obj instanceof List)      { return (List)obj;                    }
    if (obj instanceof Object[])  { return Arrays.asList((Object[])obj); }
    if (obj == null) {
      throw new NullError("Null value for List", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to List", source, offset);
  }

  private static MethodHandle convertIteratorToListHandle = lookupMethod("convertIteratorToList$c", Object.class, Object.class, Continuation.class);
  public static Object convertIteratorToList$c(Object iterAsObj, Continuation c) {
    return convertIteratorToList(iterAsObj, c);
  }

  public static List convertIteratorToList(Object iterAsObj, Continuation c) {
    Iterator iter = (Iterator)iterAsObj;

    // If we are continuing (c != null) then get objects we have stored previously.
    // Object arr will have: result
    Object[] objects = c == null ? null : c.localObjects;

    List result = objects == null ? new ArrayList() : (List)objects[0];

    int methodLocation = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext = true;
      Object  elem    = null;
      // Implement as a simple state machine since iter.hasNext() and iter.next() can both throw a Continuation.
      // iter.hasNext() can throw if we have chained iterators and hasNext() needs to get the next value of the
      // previous iterator in the chain to see if it has a value.
      // We track our state using the methodLocation that we pass to our own Continuation when/if we throw.
      // Even states are the synchronous behavior and the odd states are for handling the async case if the
      // synchronouse state throws and is later continued.
      while (true) {
        switch (methodLocation) {
          case 0:                       // Initial state
            hasNext = iter.hasNext();
            methodLocation = 2;         // hasNext() returned synchronously so jump straight to state 2
            break;
          case 1:                       // Continuing after hasNext() threw Continuation last time
            hasNext = (boolean)c.result;
            methodLocation = 2;
            break;
          case 2:                      // Have a value for "hasNext"
            if (!hasNext) {
              return result;
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.result;
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            if (elem instanceof Map.Entry) {
              var entry = (Map.Entry) elem;
              elem = List.of(entry.getKey(), entry.getValue());
            }
            result.add(elem);
            methodLocation = 0;       // Back to initial state
            break;
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, convertIteratorToListHandle.bindTo(iterAsObj), methodLocation + 1, null, new Object[] { result });
    }
  }

  public static Number castToNumber(Object obj, String source, int offset) {
    if (obj instanceof Number) {
      return (Number)obj;
    }
    if (obj == null) {
      throw new NullError("Cannot convert null value to Number", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Number", source, offset);
  }

  public static BigDecimal castToDecimal(Object obj, String source, int offset) {
    if (obj instanceof Number) {
      return toBigDecimal(obj);
    }
    if (obj == null) {
      throw new NullError("Null value for Decimal", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Decimal", source, offset);
  }

  public static MethodHandle castToFunction(Object obj, String source, int offset) {
    if (obj instanceof MethodHandle) {
      return (MethodHandle)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Function", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Function", source, offset);
  }

  public static Object[] castToObjectArr(Object obj, String source, int offset) {
    if (obj instanceof Object[]) {
      return (Object[])obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Object[]", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Object[]", source, offset);
  }

  public static boolean print(Object obj) {
    if (obj == null) { obj = "null"; }
    System.out.print(obj);
    return true;
  }

  public static boolean println(Object obj) {
    if (obj == null) { obj = "null"; }
    System.out.println(obj);
    return true;
  }

  //////////////////////////////////////

  private static BigDecimal toBigDecimal(Object val) {
    if (val instanceof BigDecimal) { return (BigDecimal)val; }
    if (val instanceof Integer)    { return BigDecimal.valueOf((int)val); }
    if (val instanceof Long)       { return BigDecimal.valueOf((long)val); }
    if (val instanceof Double)     { return BigDecimal.valueOf((double)val); }
    return null;
  }

  public static String className(Object obj) {
    if (obj == null)               { return "null"; }
    if (obj instanceof String)     { return "String"; }
    if (obj instanceof BigDecimal) { return "Decimal"; }
    if (obj instanceof Double)     { return "double"; }
    if (obj instanceof Long)       { return "long"; }
    if (obj instanceof Integer)    { return "int"; }
    if (obj instanceof Boolean)    { return "boolean"; }
    if (obj instanceof Map)        { return "Map"; }
    if (obj instanceof List)       { return "List"; }
    if (obj instanceof Iterator)   { return "Iterator"; }
    return obj.getClass().getName();
  }

  private static void ensureNonNull(Object obj, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null value encountered where null not allowed", source, offset);
    }
  }

  public static List<String> lines(String str) {
    if (str.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    List<String> lines = new ArrayList<>();
    int offset = 0;
    int lastOffset = 0;
    while ((offset = str.indexOf('\n', lastOffset)) != -1) {
      lines.add(str.substring(lastOffset, offset));
      lastOffset = offset + 1;  // skip new line
    }
    lines.add(str.substring(lastOffset));
    return lines;
  }

  /**
   * Get the string value of an object.
   */
  private static String castToString(Object value) {
    if (value instanceof String)     { return (String)value; }
    return null;
  }
  private static boolean isString(Object value) { return value instanceof String; }

  private static MethodHandle lookupMethod(String method, Class returnType, Class... argTypes) {
    return lookupMethod(RuntimeUtils.class, method, returnType, argTypes);
  }

  public static MethodHandle lookupMethod(Class clss, String method, Class returnType, Class... argTypes) {
    try {
      return MethodHandles.lookup().findStatic(clss, method, MethodType.methodType(returnType, argTypes));
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Error finding method " + method, e);
    }
  }
}
