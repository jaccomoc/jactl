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

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RuntimeUtils {

  public static final String PLUS    = "+";
  public static final String MINUS   = "-";
  public static final String STAR    = "*";
  public static final String SLASH   = "/";
  public static final String PERCENT = "%";

  public static String getOperatorType(TokenType op) {
    switch (op) {
      case PLUS:    return PLUS;
      case MINUS:   return MINUS;
      case STAR:    return STAR;
      case SLASH:   return SLASH;
      case PERCENT: return PERCENT;
      default: throw new IllegalStateException("Internal error: operator " + op + " not supported");
    }
  }

  public static BigDecimal decimalBinaryOperation(BigDecimal left, BigDecimal right, String operator, int maxScale) {
    BigDecimal result;
    switch (operator) {
      case PLUS:    result = left.add(right);       break;
      case MINUS:   result = left.subtract(right);  break;
      case STAR:    result = left.multiply(right);  break;
      case PERCENT: result = left.remainder(right); break;
      case SLASH:
        result = left.divide(right, maxScale, RoundingMode.HALF_EVEN);
        result = result.stripTrailingZeros();
        break;
      default: throw new IllegalStateException("Internal error: operator " + operator + " not supported for decimals");
    }
    if (result.scale() > maxScale) {
      result = result.setScale(maxScale, RoundingMode.HALF_EVEN);
    }
    return result;
  }

  /**
   * Perform binary operation when types are not known at compile time.
   * NOTE: operator is a String that must be one of the static strings defined in this class
   *       as '==' is used to compare the strings for performance reasons.
   * @param left      left operand
   * @param right     right operand
   * @param operator  operator (as a String)
   * @param maxScale  maximum scale for BigDecimal operations
   * @param source    source code
   * @param offset    offset into source code of operator
   * @return result of operation
   */
  public static Object binaryOp(Object left, Object right, String operator, int maxScale, String source, int offset) {
    if (left == null) {
      throw new NullError("Left-hand side of '" + operator + "' cannot be null", source, offset, false);
    }
    if (left instanceof String) {
      if (operator == PLUS) {
        return ((String) left).concat(toString(right));
      }
      if (operator == STAR) {
        if (right instanceof Number) {
          return ((String)left).repeat(((Number)right).intValue());
        }
        throw new RuntimeError("Right-hand side of string repeat operator must be numeric but found " + className(right), source, offset, true);
      }
    }

    // If boolean operation...
    // TBD

    // All other operations expect numbers so check we have numbers
    if (!(left instanceof Number))  { throw new RuntimeError("Non-numeric operand for left-hand side of '" + operator + "': was " + className(left), source, offset, true); }
    if (!(right instanceof Number)) { throw new RuntimeError("Non-numeric operand for right-hand side of '" + operator + "': was " + className(right), source, offset, true); }

    // Check for bitwise operations since we don't want to unnecessarily convert to double/BigDecimal...
    // TBD

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale);
    }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number)left).doubleValue();
      double rhs = ((Number)right).doubleValue();
      switch (operator) {
        case PLUS:    return lhs + rhs;
        case MINUS:   return lhs - rhs;
        case STAR:    return lhs * rhs;
        case SLASH:   return lhs / rhs;
        case PERCENT: return lhs % rhs;
        default: throw new IllegalStateException("Internal error: unknown operator " + operator);
      }
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number)left).longValue();
      long rhs = ((Number)right).longValue();
      switch (operator) {
        case PLUS:    return lhs + rhs;
        case MINUS:   return lhs - rhs;
        case STAR:    return lhs * rhs;
        case SLASH:   return lhs / rhs;
        case PERCENT: return lhs % rhs;
        default: throw new IllegalStateException("Internal error: unknown operator " + operator);
      }
    }

    // Must be integers
    int lhs = ((Number)left).intValue();
    int rhs = ((Number)right).intValue();
    switch (operator) {
      case PLUS:    return lhs + rhs;
      case MINUS:   return lhs - rhs;
      case STAR:    return lhs * rhs;
      case SLASH:   return lhs / rhs;
      case PERCENT: return lhs % rhs;
      default: throw new IllegalStateException("Internal error: unknown operator " + operator);
    }
  }

  public static Object negateNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).negate(); }
    if (obj instanceof Double)     { return -(double)obj; }
    if (obj instanceof Long)       { return -(long)obj; }
    if (obj instanceof Integer)    { return -(int)obj; }
    throw new RuntimeError("Type " + className(obj) + " cannot be negated", source, offset, true);
  }

  public static Object incNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).add(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj + 1; }
    if (obj instanceof Long)       { return (long)obj + 1; }
    if (obj instanceof Integer)    { return (int)obj + 1; }
    throw new RuntimeError("Type " + className(obj) + " cannot be incremented", source, offset, true);
  }

  public static Object decNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).subtract(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj - 1; }
    if (obj instanceof Long)       { return (long)obj - 1; }
    if (obj instanceof Integer)    { return (int)obj - 1; }
    throw new RuntimeError("Type " + className(obj) + " cannot be decremented", source, offset, true);
  }

  public static String stringRepeat(String str, int count, String source, int offset) {
    if (count < 0) {
      throw new RuntimeError("String repeat count must be >= 0", source, offset, true);
    }
    return str.repeat(count);
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
    return obj.toString();
  }

  //////////////////////////////////////

  private static BigDecimal toBigDecimal(Object val) {
    if (val instanceof BigDecimal) { return (BigDecimal)val; }
    if (val instanceof Integer)    { return BigDecimal.valueOf((int)val); }
    if (val instanceof Long)       { return BigDecimal.valueOf((long)val); }
    if (val instanceof Double)     { return BigDecimal.valueOf((double)val); }
    return null;
  }

  private static String className(Object obj) {
    if (obj == null)               { return "null"; }
    if (obj instanceof String)     { return "String"; }
    if (obj instanceof BigDecimal) { return "Decimal"; }
    if (obj instanceof Double)     { return "double"; }
    if (obj instanceof Long)       { return "long"; }
    if (obj instanceof Integer)    { return "int"; }
    if (obj instanceof Boolean)    { return "boolean"; }
    return "def";
  }

  private static void ensureNonNull(Object obj, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null value encountered where null not allowed", source, offset, false);
    }
  }
}
