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

import io.jactl.*;

import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RuntimeUtils {

  public static final long[] EMPTY_LONG_ARR = new long[0];

  public static final String PLUS_PLUS             = "++";
  public static final String MINUS_MINUS           = "--";
  public static final String PLUS                  = "+";
  public static final String MINUS                 = "-";
  public static final String STAR                  = "*";
  public static final String COMPARE               = "<=>";
  public static final String SLASH                 = "/";
  public static final String PERCENT               = "%";
  public static final String PERCENT_PERCENT       = "%%";
  public static final String PLUS_EQUAL            = "+=";
  public static final String MINUS_EQUAL           = "-=";
  public static final String STAR_EQUAL            = "*=";
  public static final String SLASH_EQUAL           = "/=";
  public static final String PERCENT_EQUAL         = "%=";
  public static final String PERCENT_PERCENT_EQUAL = "%%=";

  public static final String BANG_EQUAL         = "!=";
  public static final String EQUAL_EQUAL        = "==";
  public static final String BANG_EQUAL_EQUAL   = "!==";
  public static final String TRIPLE_EQUAL       = "===";
  public static final String LESS_THAN          = "<";
  public static final String LESS_THAN_EQUAL    = "<=";
  public static final String GREATER_THAN       = ">";
  public static final String GREATER_THAN_EQUAL = ">=";
  public static final String IN                 = "in";
  public static final String BANG_IN            = "!in";
  public static final String INSTANCE_OF        = "instanceof";
  public static final String BANG_INSTANCE_OF   = "!instanceof";

  public static final String AMPERSAND           = "&";
  public static final String PIPE                = "|";
  public static final String ACCENT              = "^";
  public static final String GRAVE               = "~";
  public static final String DOUBLE_LESS_THAN    = "<<";
  public static final String DOUBLE_GREATER_THAN = ">>";
  public static final String TRIPLE_GREATER_THAN = ">>>";

  // Special marker value to indicate that we should use whatever type of default makes sense
  // in the context of the operation being performed. Note that we use an integer value of 0
  // as a special marker since that way when we need the before value of an inc/dec it will
  // have the right value, but we can still check specifically for this value when doing
  // x.a += 'some string' as this turns into x.a = DEFAULT_VALUE + 'some string' and so
  // when the rhs is a string we turn DEFAULT_VALUE into ''.
  public static final Object DEFAULT_VALUE = 0;

  private static SecureRandom  secureRandom = new SecureRandom();
  private static AtomicInteger uuidCounter  = new AtomicInteger();
  private static AtomicLong    uuidMsb      = new AtomicLong();
  private static AtomicLong    uuidLsb      = new AtomicLong();

  static {
    refreshUUIDValues();
  }

  public static String getOperatorType(TokenType op) {
    if (op == null) {
      return null;
    }
    switch (op) {
      case PLUS_PLUS:             return PLUS_PLUS;
      case MINUS_MINUS:           return MINUS_MINUS;
      case PLUS:                  return PLUS;
      case MINUS:                 return MINUS;
      case STAR:                  return STAR;
      case SLASH:                 return SLASH;
      case PERCENT:               return PERCENT;
      case PERCENT_PERCENT:       return PERCENT_PERCENT;
      case COMPARE:               return COMPARE;
      case PLUS_EQUAL:            return PLUS_EQUAL;
      case MINUS_EQUAL:           return MINUS_EQUAL;
      case STAR_EQUAL:            return STAR_EQUAL;
      case SLASH_EQUAL:           return SLASH_EQUAL;
      case PERCENT_EQUAL:         return PERCENT_EQUAL;
      case PERCENT_PERCENT_EQUAL: return PERCENT_PERCENT_EQUAL;
      case BANG_EQUAL:            return BANG_EQUAL;
      case EQUAL_EQUAL:           return EQUAL_EQUAL;
      case BANG_EQUAL_EQUAL:      return BANG_EQUAL_EQUAL;
      case TRIPLE_EQUAL:          return TRIPLE_EQUAL;
      case LESS_THAN:             return LESS_THAN;
      case LESS_THAN_EQUAL:       return LESS_THAN_EQUAL;
      case GREATER_THAN:          return GREATER_THAN;
      case GREATER_THAN_EQUAL:    return GREATER_THAN_EQUAL;
      case IN:                    return IN;
      case BANG_IN:               return BANG_IN;
      case INSTANCE_OF:           return INSTANCE_OF;
      case BANG_INSTANCE_OF:      return BANG_INSTANCE_OF;
      case AMPERSAND:             return AMPERSAND;
      case PIPE:                  return PIPE;
      case ACCENT:                return ACCENT;
      case GRAVE:                 return GRAVE;
      case DOUBLE_LESS_THAN:      return DOUBLE_LESS_THAN;
      case DOUBLE_GREATER_THAN:   return DOUBLE_GREATER_THAN;
      case TRIPLE_GREATER_THAN:   return TRIPLE_GREATER_THAN;
      default:
        throw new IllegalStateException("Internal error: operator " + op + " not supported");
    }
  }

  public static BigDecimal decimalBinaryOperation(BigDecimal left, BigDecimal right, String operator, int minScale, String source, int offset) {
    BigDecimal result;
    switch (operator) {
      case PLUS:
        result = left.add(right);
        break;
      case MINUS:
        result = left.subtract(right);
        break;
      case STAR:
        result = left.multiply(right);
        break;
      case PERCENT_PERCENT:
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
      case PERCENT:
        try {
          result = (left.remainder(right).add(right)).remainder(right);
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
        result = decimalDivide(left, right, minScale, source, offset);
        break;
      default:
        throw new IllegalStateException("Internal error: operator " + operator + " not supported for decimals");
    }
    return result;
  }

  public static BigDecimal decimalDivide(BigDecimal left, BigDecimal right, int minScale, String source, int offset) {
    BigDecimal result;
    try {
      result = left.divide(right);
    }
    catch (ArithmeticException e) {
      if (e.getMessage().startsWith("Division by zero")) {
        throw new RuntimeError("Divide by zero error", source, offset);
      }

      // Result is non-terminating so try again with restricted scale and precision
      int precision = Math.max(left.precision(), right.precision()) + minScale;
      result = left.divide(right, new MathContext(precision));
      int scale = Math.max(Math.max(left.scale(), right.scale()), minScale);
      result = result.scale() > scale ? result.setScale(scale, RoundingMode.HALF_UP) : result;
      result = result.stripTrailingZeros();
    }
    return result;
  }

  public static boolean equals(Object obj1, Object obj2) {
    if (obj1 == null && obj2 == null) {
      return true;
    }
    if (obj1 == null || obj2 == null) {
      return false;
    }
    return obj1.equals(obj2);
  }

  public static int compareTo(Object obj1, Object obj2, String source, int offset) {
    if (obj1 == null && obj2 == null) {
      return 0;
    }
    if (obj1 == null) {
      return -1;
    }
    if (obj2 == null) {
      return 1;
    }
    if (obj1 instanceof Number && obj2 instanceof Number) {
      if (obj1 instanceof BigDecimal || obj2 instanceof BigDecimal) {
        return toBigDecimal(obj1).compareTo(toBigDecimal(obj2));
      }

      if (obj1 instanceof Byte) { obj1 = ((int)(byte)obj1) & 0xff; }
      if (obj2 instanceof Byte) { obj2 = ((int)(byte)obj2) & 0xff; }
      Number n1 = (Number) obj1;
      Number n2 = (Number) obj2;
      if (obj1 instanceof Double || obj2 instanceof Double) {
        return Double.compare(n1.doubleValue(), n2.doubleValue());
      }
      return Long.compare(n1.longValue(), n2.longValue());
    }
    if (obj1 instanceof Boolean && obj2 instanceof Boolean) {
      return Boolean.compare((boolean) obj1, (boolean) obj2);
    }
    if (obj1 instanceof Comparable && obj1.getClass().equals(obj2.getClass())) {
      int result = ((Comparable) obj1).compareTo(obj2);
      return result < 0 ? -1 : result == 0 ? 0 : 1;
    }
    if (obj1 instanceof List && obj2 instanceof List) {
      List list1 = (List)obj1;
      List list2 = (List)obj2;
      for (int i = 0; i < list1.size(); i++) {
        if (i >= list2.size()) {
          return 1;
        }
        int cmp = compareTo(list1.get(i), list2.get(i), source, offset);
        if (cmp != 0) {
          return cmp;
        }
      }
      if (list2.size() > list1.size()) {
        return -1;
      }
      return 0;
    }

    throw new RuntimeError("Cannot compare objects of type " + className(obj1) + " and " + className(obj2), source, offset);
  }

  /**
   * Perform binary operation when types are not known at compile time. NOTE: operator is a String that must be one of
   * the static strings defined in this class as '==' is used to compare the strings for performance reasons.
   *
   * @param left              left operand
   * @param right             right operand
   * @param operator          operator (as a String)
   * @param originalOperator  original operator (as a String) (will be -- or ++ if inc/dec turned into binaryOp or null
   *                          otherwise)
   * @param minScale          maximum scale for BigDecimal operations
   * @param captureStackTrace
   * @param source            source code
   * @param offset            offset into source code of operator
   * @return result of operation
   */
  private static Object binaryOp(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (left == null) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
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
          return repeat(leftString, ((Number) right).intValue(), source, offset);
        }
        throw new RuntimeError("Right-hand side of string repeat operator must be numeric but found " + className(right), source, offset);
      }
    }

    if (left instanceof List) {
      if (operator != PLUS) {
        throw new RuntimeError("Non-numeric operand for " + originalOperator + " of type " + className(left), source, offset);
      }
      return listAdd((List) left, right, originalOperator == PLUS_EQUAL);
    }

    if (left instanceof Map) {
      if (operator != PLUS && operator != MINUS) {
        throw new RuntimeError("Non-numeric operand for " + originalOperator + " of type " + className(left), source, offset);
      }
      if (operator == PLUS) {
        if (!(right instanceof Map)) {
          throw new RuntimeError("Cannot add " + className(right) + " to Map", source, offset);
        }
        return mapAdd((Map) left, (Map) right, originalOperator == PLUS_EQUAL);
      }
      return mapSubtract((Map)left, right, originalOperator == MINUS_EQUAL, source, offset);
    }

    // All other operations expect numbers so check we have numbers
    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    if (operator != PLUS && operator != MINUS) {
      throw new IllegalStateException("Internal error: unexpected operation '" + operator + "' on types " + className(left) + " and " + className(right));
    }

    // Check for bitwise operations since we don't want to unnecessarily convert to double/BigDecimal...
    // TBD

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    boolean resultIsByte = left instanceof Byte && right instanceof Byte;
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs + rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number) left).longValue();
      long rhs = ((Number) right).longValue();
      return lhs + rhs;
    }

    int lhs = ((Number) left).intValue();
    int rhs = ((Number) right).intValue();
    int result = lhs + rhs;
    if (resultIsByte) { return (byte)result; }
    return result;
  }

  public static String PLUS_METHOD = "plus";
  public static Object plus(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (left == DEFAULT_VALUE || !(left instanceof Number)) {
      return binaryOp(left, right, operator, originalOperator, minScale, captureStackTrace, source, offset);
    }

    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    if (left instanceof Byte && right instanceof Byte) {
      return (byte)((byte)left + (byte)right);
    }
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs + rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number) left).longValue();
      long rhs = ((Number) right).longValue();
      return lhs + rhs;
    }

    int lhs = ((Number) left).intValue();
    int rhs = ((Number) right).intValue();
    return lhs + rhs;
  }

  public static String MULTIPLY_METHOD = "multiply";
  public static Object multiply(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (isString(left)) {
      return binaryOp(left, right, operator, originalOperator, minScale, captureStackTrace, source, offset);
    }

    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    if (left instanceof Byte && right instanceof Byte) {
      return (byte)((byte)left * (byte)right);
    }
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs * rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number) left).longValue();
      long rhs = ((Number) right).longValue();
      return lhs * rhs;
    }

    int lhs = ((Number) left).intValue();
    int rhs = ((Number) right).intValue();
    return lhs * rhs;
  }

  public static String MINUS_METHOD = "minus";
  public static Object minus(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (left instanceof Map) {
      return binaryOp(left, right, operator, originalOperator, minScale, captureStackTrace, source, offset);
    }
    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    if (left instanceof Byte && right instanceof Byte) {
      return (byte)((byte)left - (byte)right);
    }
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs - rhs;
    }

    if (left instanceof Long || right instanceof Long) {
      long lhs = ((Number) left).longValue();
      long rhs = ((Number) right).longValue();
      return lhs - rhs;
    }

    int lhs = ((Number) left).intValue();
    int rhs = ((Number) right).intValue();
    return lhs - rhs;
  }

  public static String DIVIDE_METHOD = "divide";
  public static Object divide(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    boolean resultIsByte = left instanceof Byte && right instanceof Byte;
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs / rhs;
    }

    try {
      if (left instanceof Long || right instanceof Long) {
        long lhs = ((Number) left).longValue();
        long rhs = ((Number) right).longValue();
        return lhs / rhs;
      }

      int lhs = ((Number) left).intValue();
      int rhs = ((Number) right).intValue();
      int result = lhs / rhs;
      if (resultIsByte) { return (byte)result; }
      return result;
    }
    catch (ArithmeticException e) {
      throw new RuntimeError("Divide by zero", source, offset);
    }
  }

  public static String REMAINDER_METHOD = "remainder";
  public static Object remainder(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    boolean resultIsByte = left instanceof Byte && right instanceof Byte;
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return lhs % rhs;
    }

    try {
      if (left instanceof Long || right instanceof Long) {
        long lhs = ((Number) left).longValue();
        long rhs = ((Number) right).longValue();
        return lhs % rhs;
      }

      int lhs = ((Number) left).intValue();
      int rhs = ((Number) right).intValue();
      int result = lhs % rhs;
      if (resultIsByte) { return (byte)result; }
      return result;
    }
    catch (ArithmeticException e) {
      throw new RuntimeError("Divide by zero", source, offset);
    }
  }

  public static String MODULO_METHOD = "modulo";
  public static Object modulo(Object left, Object right, String operator, String originalOperator, int minScale, boolean captureStackTrace, String source, int offset) {
    if (!(left instanceof Number)) {
      throwOperandError(left, true, operator, captureStackTrace, source, offset);
    }
    if (!(right instanceof Number)) {
      throwOperandError(right, false, operator, captureStackTrace, source, offset);
    }

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, minScale, source, offset);
    }

    boolean resultIsByte = left instanceof Byte && right instanceof Byte;
    if (left  instanceof Byte) { left  = ((int)(byte)left) & 0xff; }
    if (right instanceof Byte) { right = ((int)(byte)right) & 0xff; }

    if (left instanceof Double || right instanceof Double) {
      double lhs = ((Number) left).doubleValue();
      double rhs = ((Number) right).doubleValue();
      return ((lhs % rhs)+rhs) % rhs;
    }

    try {
      if (left instanceof Long || right instanceof Long) {
        long lhs = ((Number) left).longValue();
        long rhs = ((Number) right).longValue();
        return ((lhs % rhs) + rhs) % rhs;
      }

      int lhs = ((Number) left).intValue();
      int rhs = ((Number) right).intValue();
      int result = ((lhs % rhs) + rhs) % rhs;
      if (resultIsByte) { return (byte)result; }
      return result;
    }
    catch (ArithmeticException e) {
      throw new RuntimeError("Divide by zero", source, offset);
    }
  }

  private static void throwOperandError(Object obj, boolean isLeft, String operator, boolean captureStackTrace, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null operand for " + (isLeft ? "left" : "right") + "-hand side of '" + operator + "'", source, offset, captureStackTrace);
    }
    throw new RuntimeError("Non-numeric operand for " + (isLeft ? "left" : "right") + "-hand side of '" + operator + "': was " + className(obj), source, offset);
  }

  enum OperandType { LEFT, RIGHT, UNARY }
  private static void throwBitOperandError(Object obj, OperandType opType, String operator, boolean captureStackTrace, String source, int offset) {
    String operand = opType == OperandType.UNARY ? "" : (opType == OperandType.LEFT ? "left" : "right") + "-hand side of '";
    if (obj == null) {
      throw new NullError("Null operand for " + operand + operator + "'", source, offset, captureStackTrace);
    }
    throw new RuntimeError("Operand for " + operand + operator + "' must be int, byte, or long: was " + className(obj), source, offset);
  }

  public static final String BOOLEAN_OP = "booleanOp";
  public static boolean booleanOp(Object left, Object right, String operator, String source, int offset) {
    if (operator == TRIPLE_EQUAL) {
      if (left == right)                                        { return true;  }
      if (left instanceof Boolean || left instanceof String)    { return left.equals(right); }
      if (!(left instanceof Number && right instanceof Number)) { return left == right; }
      // If both Number then fall through...
    }
    if (operator == BANG_EQUAL_EQUAL) {
      if (left instanceof Boolean || left instanceof String)    { return !left.equals(right); }
      if (!(left instanceof Number && right instanceof Number)) { return left != right; }
      // If both Number then fall through...
    }

    if (operator == EQUAL_EQUAL || operator == TRIPLE_EQUAL) {
      if (left == right)                 { return true;  }
      if (left == null || right == null) { return false; }
    }
    if (operator == BANG_EQUAL || operator == BANG_EQUAL_EQUAL) {
      if (left == right)                 { return false; }
      if (left == null || right == null) { return true;  }
    }

    // We are left with the comparison operators
    if ((operator == EQUAL_EQUAL || operator == BANG_EQUAL)) {
      if (left instanceof List || left instanceof Map || left instanceof JactlObject ||
          right instanceof List || right instanceof Map || right instanceof JactlObject ||
          left.getClass().isArray() || right.getClass().isArray()) {
        return equality(left, right, operator, source, offset);
      }
      // Deal with Numbers below. Everything else reverts to Object.equals():
      if (!(left instanceof Number) || !(right instanceof Number)) {
        return left.equals(right) == (operator == EQUAL_EQUAL);
      }
    }

    int comparison = compareTo(left, right, source, offset);
    if (operator == EQUAL_EQUAL || operator == TRIPLE_EQUAL)    { return comparison == 0; }
    if (operator == BANG_EQUAL || operator == BANG_EQUAL_EQUAL) { return comparison != 0; }
    if (operator == LESS_THAN)                                  { return comparison < 0; }
    if (operator == LESS_THAN_EQUAL)                            { return comparison <= 0; }
    if (operator == GREATER_THAN)                               { return comparison > 0; }
    if (operator == GREATER_THAN_EQUAL)                         { return comparison >= 0; }
    throw new IllegalStateException("Internal error: unexpected operator " + operator);
  }

  public static boolean isEquals(Object left, Object right, String source, int offset) {
    return booleanOp(left, right, EQUAL_EQUAL, source, offset);
  }

  /**
   * Check for == or != for List,Map,JactlObject types
   *
   * @param operator EQUAL_EQUAL or BANG_EQUAL
   * @return true if equal or not equal based on value of operator
   */
  private static boolean equality(Object leftObj, Object rightObj, String operator, String source, int offset) {
    if (leftObj == rightObj)                 { return operator == EQUAL_EQUAL; }
    if (leftObj == null || rightObj == null) { return operator == BANG_EQUAL; }

    if (leftObj instanceof List || rightObj instanceof List || leftObj.getClass().isArray() || rightObj.getClass().isArray()) {
      return listEquals(leftObj, rightObj, operator, source, offset);
    }

    if (leftObj instanceof Map && rightObj instanceof Map) {
      Map<String,Object> left = (Map<String,Object>)leftObj;
      Map<String,Object> right = (Map<String,Object>)rightObj;
      if (left.size() != right.size()) { return operator == BANG_EQUAL; }
      for (Object key: left.keySet()) {
        if (!right.containsKey(key)) {
          return operator == BANG_EQUAL;
        }
        if (!isEquals(left.get(key), right.get(key), source, offset)) {
          return operator == BANG_EQUAL;
        }
      }
      return operator == EQUAL_EQUAL;
    }

    if (leftObj instanceof JactlObject && rightObj instanceof JactlObject) {
      if (!leftObj.getClass().equals(rightObj.getClass())) {
        return operator == BANG_EQUAL;
      }
      // Have two instances of same class so check that each field is equal
      Map<String, Object> fieldAndMethods = ((JactlObject) leftObj)._$j$getFieldsAndMethods();
      for (Iterator<Map.Entry<String, Object>> iter = fieldAndMethods.entrySet().stream().filter(entry -> entry.getValue() instanceof Field).iterator();
           iter.hasNext(); ) {
        Map.Entry<String, Object> entry = iter.next();
        Field                     field = (Field) entry.getValue();
        try {
          // If field values differ then we are done
          if (!isEquals(field.get(leftObj), field.get(rightObj), source, offset)) {
            return operator == BANG_EQUAL;
          }
        }
        catch (IllegalAccessException e) {
          throw new IllegalStateException("Internal error: accessing field '" + entry.getKey() + "': " + e, e);
        }
      }
      return operator == EQUAL_EQUAL;
    }

    if (leftObj instanceof JactlObject && rightObj instanceof Map || leftObj instanceof Map && rightObj instanceof JactlObject) {
      Map<String,Object> map = (Map<String,Object>)(leftObj instanceof Map ? leftObj : rightObj);
      JactlObject       obj = (JactlObject)(leftObj instanceof JactlObject ? leftObj : rightObj);
      Set<String>         mapKeys         = new HashSet<>(map.keySet());
      Map<String, Object> fieldAndMethods = obj._$j$getFieldsAndMethods();
      for (Iterator<Map.Entry<String, Object>> iter = fieldAndMethods.entrySet().stream().filter(entry -> entry.getValue() instanceof Field).iterator();
           iter.hasNext(); ) {
        Map.Entry<String, Object> entry = iter.next();
        Field                     field = (Field) entry.getValue();
        String key  = entry.getKey();
        try {
          // If field values differ then we are done
          if (!isEquals(field.get(obj), map.get(key), source, offset)) {
            return operator == BANG_EQUAL;
          }
          mapKeys.remove(key);
        }
        catch (IllegalAccessException e) {
          throw new IllegalStateException("Internal error: accessing field '" + key + "': " + e, e);
        }
      }
      if (mapKeys.size() > 0) {
        return operator == BANG_EQUAL;
      }
      return operator == EQUAL_EQUAL;
    }

    return leftObj.equals(rightObj) == (operator == EQUAL_EQUAL);
  }

  /**
   * Compare lists or arrays
   */
  private static boolean listEquals(Object leftObj, Object rightObj, String operator, String source, int offset) {
    boolean leftIsList     = leftObj  instanceof List;
    boolean rightIsList    = rightObj instanceof List;
    List    left  = leftIsList  ? (List)leftObj  : null;
    List    right = rightIsList ? (List)rightObj : null;

    if (leftIsList && rightIsList) {
      if (left.size() != right.size()) { return operator == BANG_EQUAL; }
      for (int i = 0; i < left.size(); i++) {
        if (!isEquals(left.get(i), right.get(i), source, offset)) {
          return operator == BANG_EQUAL;
        }
      }
      return operator == EQUAL_EQUAL;
    }

    if (leftIsList || rightIsList) {
      List   list = leftIsList ? left : right;
      Object obj  = leftIsList ? rightObj : leftObj;
      if (!obj.getClass().isArray())           { return operator == BANG_EQUAL; }
      if (list.size() != Array.getLength(obj)) { return operator == BANG_EQUAL; }
      for (int i = 0; i < list.size(); i++) {
        if (!isEquals(list.get(i), Array.get(obj, i), source, offset)) {
          return operator == BANG_EQUAL;
        }
      }
      return operator == EQUAL_EQUAL;
    }

    // Both arrays
    if (leftObj.getClass().isArray() && rightObj.getClass().isArray()) {
      int length = Array.getLength(leftObj);
      if (length != Array.getLength(rightObj)) {
        return operator == BANG_EQUAL;
      }
      for (int i = 0; i < length; i++) {
        if (!isEquals(Array.get(leftObj, i), Array.get(rightObj, i), source, offset)) {
          return operator == BANG_EQUAL;
        }
      }
      return operator == EQUAL_EQUAL;
    }

    // One of them is not a list and not an array so can't be equal
    return operator == BANG_EQUAL;
  }

  /**
   * Perform bitwise operation.
   * Also caters for list &lt;&lt; elem and list &lt;&lt; elem
   * @param left          the lhs
   * @param right         the rhs
   * @param operator      the operator (without '=': e.g. &lt;&lt;= is just &lt;&lt;)
   * @param isAssignment  whether original operator ended in '=' (e.g. was &lt;&lt;=). only used for list &lt;&lt;= elem
   * @param captureStackStrace true if we capture stack trace for NullError (not in a try/catch NullError)
   * @param source        source code
   * @param offset        offset where operator is
   * @return the result of the operation
   */
  public static Object bitOperation(Object left, Object right, String operator, boolean isAssignment, boolean captureStackStrace, String source, int offset) {
    if (left instanceof List && operator == DOUBLE_LESS_THAN) {
      return listAddSingle((List)left, right, isAssignment);
    }

    boolean isShift = operator == DOUBLE_LESS_THAN || operator == DOUBLE_GREATER_THAN || operator == TRIPLE_GREATER_THAN;

    boolean leftIsLong = false;
    if (left instanceof Long) {
      leftIsLong = true;
    }
    else if (!(left instanceof Integer || left instanceof Byte)) {
      throwBitOperandError(left, OperandType.LEFT, operator, captureStackStrace, source, offset);
    }

    if (!(right instanceof Long) && !(right instanceof Integer || right instanceof Byte)) {
      throwBitOperandError(right, OperandType.RIGHT, operator, captureStackStrace, source, offset);
    }

    long rhs = ((Number) right).longValue();
    if (isShift) {
      // Maximum shift amount based on size of lhs
      rhs = leftIsLong ? rhs : left instanceof Integer ? rhs & 0x1f : rhs & 0x7;
    }
    else if (rhs < 0) {
      // For non-shift don't sign extend ints and bytes
      if (right instanceof Integer) {
        rhs &= 0xffffffff;
      }
      if (right instanceof Byte) {
        rhs &= 0xff;
      }
    }

    long result;

    // >> is special since it works differently for negative numbers
    if (operator == DOUBLE_GREATER_THAN) {
      long lhs = leftIsLong ? ((Number) left).longValue() : left instanceof Byte ? ((int)(byte)left) & 0xff : (int)left;
      result = lhs >> rhs;
    }
    else {
      long lhs = leftIsLong ? ((Number) left).longValue() : left instanceof Byte ? ((long)(byte)left) & 0xff : ((long)(int)left) & 0xffffffffL;
      if (left instanceof Byte && operator == DOUBLE_LESS_THAN) {
        rhs = rhs & 0x07;
      }
      switch (operator) {
        case DOUBLE_LESS_THAN:    result = lhs << rhs;  break;
        case TRIPLE_GREATER_THAN: result = lhs >>> rhs; break;
        case AMPERSAND:           result = lhs & rhs;   break;
        case PIPE:                result = lhs | rhs;   break;
        case ACCENT:              result = lhs ^ rhs;   break;
        default:                  throw new IllegalStateException("Internal error: operator " + operator + " not supported");
      }
    }
    if (isShift) {
      // Only left hand side matters for return type when shifting since shift amount is not relevant
      if (leftIsLong)              { return result; }
      if (left instanceof Integer) { return (int)result; }
      return (byte)result;
    }

    if (leftIsLong || right instanceof Long)                 { return result; }
    if (left instanceof Integer || right instanceof Integer) { return (int)result; }
    return (byte)result;
  }

  public static final String ARITHMETIC_NOT = "arithmeticNot";
  public static Object arithmeticNot(Object obj, boolean captureStackTrace, String source, int offset) {
    if (!(obj instanceof Long) && !(obj instanceof Integer || obj instanceof Byte)) {
      throwBitOperandError(obj, OperandType.UNARY, "~", captureStackTrace, source, offset);
      throw new RuntimeError("Operand for '~' must be int, byte, or long (not " + className(obj) + ")", source, offset);
    }

    if (obj instanceof Integer) { return ~(int)obj;   }
    if (obj instanceof Long)    { return ~(long)obj;   }
    return (byte)~(byte)obj;
  }

  public static String NEGATE_NUMBER = "negateNumber";
  public static Object negateNumber(Object obj, boolean captureStackTrace, String source, int offset) {
    ensureNonNull(obj, captureStackTrace, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal) obj).negate(); }
    if (obj instanceof Double)     { return -(double) obj; }
    if (obj instanceof Long)       { return -(long) obj; }
    if (obj instanceof Integer)    { return -(int) obj; }
    if (obj instanceof Byte)       { return (byte)-(byte)obj; }
    throw new RuntimeError("Type " + className(obj) + " cannot be negated", source, offset);
  }

  public static String INC_NUMBER = "incNumber";
  public static Object incNumber(Object obj, String operator, boolean captureStackTrace, String source, int offset) {
    ensureNonNull(obj, captureStackTrace, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal) obj).add(BigDecimal.ONE);  }
    if (obj instanceof Double)     { return (double) obj + 1;                        }
    if (obj instanceof Long)       { return (long) obj + 1;                          }
    if (obj instanceof Integer)    { return (int) obj + 1;                           }
    if (obj instanceof Byte)       { return (byte)((byte)obj + 1);                   }
    return binaryOp(obj, 1, PLUS, operator, -1, captureStackTrace, source, offset);
  }

  public static String DEC_NUMBER = "decNumber";
  public static Object decNumber(Object obj, String operator, boolean captureStackTrace, String source, int offset) {
    ensureNonNull(obj, captureStackTrace, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal) obj).subtract(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double) obj - 1;                            }
    if (obj instanceof Long)       { return (long) obj - 1;                              }
    if (obj instanceof Integer)    { return (int) obj - 1;                               }
    if (obj instanceof Byte)       { return (byte)((byte)obj - 1);                       }
    return binaryOp(obj, 1, MINUS, operator, -1, captureStackTrace, source, offset);
  }

  public static String STRING_REPEAT = "stringRepeat";
  public static String stringRepeat(String str, int count, boolean captureStackTrace, String source, int offset) {
    if (count < 0) {
      throw new RuntimeError("String repeat count must be >= 0", source, offset);
    }
    ensureNonNull(str, captureStackTrace, source, offset);
    return Utils.repeat(str, count);
  }

  /**
   * Add to a list. If second object is a list then we concatenate the lists. Otherwise,
   * object is added to and of the list.
   * @param list        the list
   * @param obj         object to add or merge with list
   * @param isPlusEqual whether we are doing += or just +
   * @return new list which is result of adding second object to first list
   */
  public static List listAdd(List list, Object obj, boolean isPlusEqual) {
    // If ++= then add to existing list rather than creating a new one
    List result = isPlusEqual ? list : new ArrayList<>(list);
    if (obj instanceof List) {
      result.addAll((List) obj);
    }
    else {
      result.add(obj);
    }
    return result;
  }

  /**
   * Add single element to a list even if element is itself a list.
   * @param list         the list
   * @param elem         the element
   * @param isAssignment true if &lt;&lt;= (add to existing list), false creates a new list
   * @return the list (same list for &lt;&lt;= and new list for &lt;&lt;)
   */
  public static List listAddSingle(List list, Object elem, boolean isAssignment) {
    List result = isAssignment ? list : new ArrayList(list);
    result.add(elem);
    return result;
  }

  /**
   * Add twp maps together. The result is a new map with a merge of the keys and values from the two maps. If the same
   * key appears in both maps the value from the second map "overwrites" the other value and becomes the value of the
   * key in the resulting map.
   * @param map1         the first map
   * @param map2         the second map
   * @param isPlusEqual  true if using += rather than just +
   * @return a new map which is the combination of the two maps
   */
  public static Map mapAdd(Map map1, Map map2, boolean isPlusEqual) {
    // If plusEqual then just merge map2 into map1
    Map result = isPlusEqual ? map1 : new LinkedHashMap(map1);
    result.putAll(map2);
    return result;
  }

  public static Map mapSubtract(Map map1, Object obj2, boolean isMinusEqual, String source, int offset) {
    Map result = isMinusEqual ? map1 : new LinkedHashMap(map1);
    if (obj2 instanceof Map) {
      ((Map)obj2).keySet().forEach(result::remove);
      return result;
    }
    if (obj2 instanceof List) {
      ((List)obj2).forEach(result::remove);
      return result;
    }
    throw new RuntimeError("Cannot subtract object of type " + className(obj2) + " from Map", source, offset);
  }

  /**
   * Return true if object satisfies truthiness check:
   * <pre>
   * null    --&gt; false
   * boolean --&gt; value of the boolean
   * number  --&gt; true if non-zero
   * String  --&gt; true if non-empty
   * Object  --&gt; true if non-null
   * </pre>
   * If negated is true then test is inverted, and we test for "falsiness".
   * @param value   the value to test
   * @param negated true if result is negated (i.e. "true" returns false)
   * @return negated if object is "true" and !negated otherwise
   */
  public static boolean isTruth(Object value, boolean negated) {
    if (value == null)               { return negated; }
    if (value instanceof Boolean)    { return negated != (boolean) value; }
    if (value instanceof String)     { return negated == ((String) value).isEmpty(); }
    if (value instanceof Integer)    { return negated == ((int) value == 0); }
    if (value instanceof Byte)       { return negated == ((byte) value == 0); }
    if (value instanceof Long)       { return negated == ((long) value == 0); }
    if (value instanceof Double)     { return negated == ((double) value == 0); }
    if (value instanceof BigDecimal) { return negated == ((BigDecimal) value).stripTrailingZeros().equals(BigDecimal.ZERO); }
    if (value instanceof List)       { return negated == ((List) value).isEmpty(); }
    if (value instanceof Map)        { return negated == ((Map) value).isEmpty(); }
    if (value instanceof Object[])    { return negated == (((Object[]) value).length == 0); }
    return !negated;
  }

  public static String toStringOrNull(Object obj) {
    if (obj == null) {
      return null;
    }
    // Special case for byte[]
    if (obj instanceof byte[]) {
      return new String((byte[])obj, StandardCharsets.UTF_8);
    }
    return toString(obj);
  }

  public static String toString(Object obj) {
    return doToString(obj, new HashSet<>(), "", 0);
  }

  public static String toString(Object obj, int indent) {
    return doToString(obj, new HashSet<>(), "", indent);
  }

  /**
   * Output a nice string form.
   *
   * @param obj             the object
   * @param previousObjects set of previous values we have seen (for detecting circular references)
   * @return
   */
  private static String doToString(Object obj, Set<Object> previousObjects, String prefix, int indent) {
    if (obj == null) {
      return "null";
    }

    // Since iterating can be async and we don't want to make every call to toString() async due to the
    // additional code that is generated we will (for the moment anyway) just return "Iterator" if toString()
    // is invoked on an iterator. To actually get a toString() users can use "as String" or call collect()
    // first or assign to a variable before invoking toString().
    if (obj instanceof JactlIterator) {
      return "Iterator";
    }

    if (obj instanceof List || obj instanceof Map || obj instanceof JactlObject || obj.getClass().isArray()) {
      // If we have already visited this object then we have a circular reference so to avoid infinite recursion
      // we output "<CIRCULAR_REF>"
      if (!previousObjects.add(System.identityHashCode(obj))) {
        return "<CIRCULAR_REF>";
      }
    }

    if (obj instanceof Object[]) {
      obj = Arrays.asList((Object[]) obj);
    }
    if (obj instanceof boolean[]) { return Arrays.toString((boolean[])obj); }
    if (obj instanceof byte[])    { return byteArrayToString((byte[])obj); }
    if (obj instanceof int[])     { return Arrays.toString((int[])obj); }
    if (obj instanceof long[])    { return Arrays.toString((long[])obj); }
    if (obj instanceof double[])  { return Arrays.toString((double[])obj); }

    try {
      if (obj instanceof List) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        List list = (List) obj;
        for (int i = 0; i < list.size(); i++) {
          if (i > 0) {
            sb.append(", ");
          }
          sb.append(toQuotedString(list.get(i), previousObjects, "", 0));
        }
        sb.append(']');
        return sb.toString();
      }

      if (obj instanceof JactlObject) {
        JactlObject jobj = (JactlObject)obj;
        Object toString = jobj._$j$getFieldsAndMethods().get(Utils.TO_STRING);
        if (toString instanceof JactlMethodHandle) {
          JactlMethodHandle mh = (JactlMethodHandle)toString;
          if (mh.parameterCount() == 5) {
            try {
              Object result = mh.invoke(jobj, (Continuation)null, null, 0, Utils.EMPTY_OBJ_ARR);
              return result == null ? "null" : result.toString();
            }
            catch (RuntimeError e) {
              throw e;
            }
            catch (Throwable e) {
              throw new IllegalStateException("Unexpected error in toString(): " + e, e);
            }
          }
        }
      }

      if (obj instanceof JactlObject || obj instanceof Map) {
        boolean       isMap = obj instanceof Map;
        StringBuilder sb    = new StringBuilder();
        sb.append('[');
        Iterator<Map.Entry<String, Object>> iterator = isMap ? ((Map<String, Object>) obj).entrySet().iterator()
                                                             : ((JactlObject) obj)._$j$getFieldsAndMethods()
                                                   .entrySet()
                                                   .stream()
                                                   .filter(entry -> entry.getValue() instanceof Field)
                                                   .iterator();
        boolean first = true;
        while (iterator.hasNext()) {
          if (!first) {
            sb.append(", ");
          }
          else {
            first = false;
          }
          Map.Entry<String, Object> entry = iterator.next();
          try {
            Object value = entry.getValue();
            if (!isMap) {
              value = ((Field) entry.getValue()).get(obj);
            }
            if (indent > 0) {
              sb.append('\n').append(prefix).append(Utils.repeat(" ", indent));
            }
            sb.append(keyToString(entry.getKey())).append(':').append(toQuotedString(value, previousObjects, prefix + Utils.repeat(" ", indent), indent));
          }
          catch (IllegalAccessException e) {
            throw new IllegalStateException("Internal error: problem accessing field '" + entry.getKey() + "': " + e, e);
          }
        }
        if (first) {
          // Empty map
          sb.append(':');
        }
        if (indent > 0) {
          sb.append('\n').append(prefix);
        }
        sb.append(']');
        return sb.toString();
      }

      if (obj instanceof BigDecimal) {
        return ((BigDecimal)obj).toPlainString();
      }

      if (obj instanceof JactlMethodHandle) {
        return "Function@" + System.identityHashCode(obj);
      }

      if (obj instanceof Byte) {
        return Integer.toString(((int)(byte)obj) & 0xff);
      }

      // All other types use default toString()
      return obj.toString();
    }
    finally {
      previousObjects.remove(System.identityHashCode(obj));
    }
  }

  private static String byteArrayToString(byte[] array) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < array.length; i++) {
      if (i > 0) { sb.append(", "); }
      sb.append(((int)array[i]) & 0xff);
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Quote if actual string, otherwise delegate back to toString(obj). This is to make the output of toString() for Map
   * and List objects is valid Jactl code so we can cut-and-paste output into actual scripts for testing and use in
   * REPL.
   */
  private static String toQuotedString(Object obj, Set<Object> previousObjects, String prefix, int indent) {
    if (obj instanceof String) {
      return "'" + obj + "'";
    }
    return doToString(obj, previousObjects, prefix, indent);
  }

  private static String keyToString(Object obj) {
    if (obj == null) {
      return "null";
    }
    if (!(obj instanceof String)) {
      String keyAsString = toString(obj);
      return '(' + keyAsString + ')';
    }
    String str = obj.toString();
    // If string is a valid identifier then no need to quote
    if (str.isEmpty()) {
      return "''";
    }
    final char start = str.charAt(0);
    if (Character.isJavaIdentifierStart(start) && start != '$') {
      for (int i = 1; i < str.length(); i++) {
        final char ch = str.charAt(i);
        if (!Character.isJavaIdentifierPart(ch) || ch == '$') {
          return "'" + str + "'";
        }
      }
      return str;
    }
    return "'" + str + "'";
  }

  /**
   * Load field from map/list or return default value if field does not exist
   *
   * @param parent       parent (map or list)
   * @param field        field (field name or list index)
   * @param isDot        true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional   true if access type is '?.' or '?['
   * @param defaultValue default value to return if field does not exist
   * @param captureStackTrace whether to capture stack trace for any NullError generated
   * @param source       source code
   * @param offset       offset into source for operation
   * @return the field value or the default value
   */
  public static Object loadFieldOrDefault(Object parent, Object field, boolean isDot, boolean isOptional, Object defaultValue, boolean captureStackTrace, String source, int offset) {
    Object value = loadField(parent, field, isDot, isOptional, captureStackTrace, source, offset);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }
  public static String LOAD_FIELD_OR_DEFAULT = "loadFieldOrDefault";

  public static String INVOKE_METHOD_OR_FIELD = "invokeMethodOrField";
  public static Object invokeMethodOrField(Object parent, String field, boolean onlyField, boolean isOptional, Object[] args, boolean captureStackTrace, String source, int offset) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Tried to invoke method on null value", source, offset, captureStackTrace);
    }

    Object value = null;
    if (parent instanceof JactlObject) {
      value = loadInstanceField((JactlObject) parent, field, source, offset);
    }
    else {
      // Fields of a map cannot override built-in methods so look up method first
      value = onlyField ? null : RuntimeState.getState().getContext().getFunctions().lookupWrapper(parent, field);
      if (value == null && parent instanceof Map) {
        value = ((Map) parent).get(field);
      }
    }

    if (value == null) {
      throw new RuntimeError("No such method '" + field + "' for type " + className(parent), source, offset);
    }

    if (!(value instanceof JactlMethodHandle)) {
      throw new RuntimeError("Cannot invoke value of '" + field + "' (type is " + className(value) + ")", source, offset);
    }

    try {
      return ((JactlMethodHandle) value).invoke((Continuation) null, source, offset, args);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeError("Error during method invocation", source, offset, e);
    }
  }

  /**
   * Load field from map/list (return null if field or index does not exist)
   *
   * @param parent     parent (map or list)
   * @param field      field (field name or list index)
   * @param isDot      true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional true if access type is '?.' or '?['
   * @param captureStackTrace true if stack trace should be captured if NullError thrown
   * @param source     source code
   * @param offset     offset into source for operation
   * @return the field value or null
   */
  public static Object loadField(Object parent, Object field, boolean isDot, boolean isOptional, boolean captureStackTrace, String source, int offset) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Null value for parent during field access", source, offset, captureStackTrace);
    }
    if (field == null) {
      if (parent instanceof Map || parent instanceof JactlObject) {
        throw new NullError("Null value for field", source, offset, captureStackTrace);
      }
      else {
        throw new NullError("Null value for index", source, offset, captureStackTrace);
      }
    }

    if (parent instanceof Map) {
      return loadMapField(parent, field, captureStackTrace, source, offset);
    }

    if (parent instanceof JactlObject) {
      return loadInstanceField((JactlObject) parent, field, source, offset);
    }

    return loadOther(parent, field, isDot, source, offset);
  }
  public static final String LOAD_FIELD = "loadField";

  private static Object loadOther(Object parent, Object field, boolean isDot, String source, int offset) {
    if (parent instanceof Class) {
      return loadStaticFieldOrMethod(parent, field, source, offset);
    }

    // Check for accessing method by name
    String fieldString = castToString(field);
    if (isDot && fieldString != null) {
      JactlMethodHandle method = RuntimeState.getState().getContext().getFunctions().lookupWrapper(parent, fieldString);
      if (method != null) {
        return method;
      }
    }

    String parentString = null;
    if (!(parent instanceof List) && (parentString = castToString(parent)) == null && !(parent instanceof Object[])) {
      throw new RuntimeError("Invalid parent object type (" + className(parent) + "): expected Map/List" +
                             (isDot ? "" : " or String"), source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for " + className(parent), source, offset);
    }

    int index = -1;
    try { index = ((Number) field).intValue(); }  catch (ClassCastException e) { throw new RuntimeError("Non-numeric value for indexed access", source, offset); }

    if (parent instanceof List) {
      return loadListElem((List) parent, index, source, offset);
    }

    if (parentString != null) {
      return loadStringChar(parentString, index, source, offset);
    }

    return loadObjectArrElem((Object[]) parent, index, source, offset);
  }

  /**
   * Load field from map/list (return null if field or index does not exist)
   *
   * @param parent     parent (map or list)
   * @param field      field (field name or list index)
   * @param isDot      true if access type is '.' or '?.' (false for '[' or '?[')
   * @param isOptional true if access type is '?.' or '?['
   * @param isMap      if creating missing field we create a Map if true or a List if false
   * @param captureStackTrace true if we should capture stack trace when throwing NullError
   * @param source     source code
   * @param offset     offset into source for operation
   * @return the field value or null
   */
  public static Object loadOrCreateField(Object parent, Object field, boolean isDot, boolean isOptional,
                                         boolean isMap, boolean captureStackTrace, String source, int offset) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Null value for parent during field access", source, offset, captureStackTrace);
    }

    if (parent instanceof Map) {
      return loadOrCreateMapField(parent, field, captureStackTrace, source, offset, isMap);
    }

    if (parent instanceof Class) {
      return loadStaticFieldOrMethod(parent, field, source, offset);
    }

    if (parent instanceof JactlObject) {
      return loadOrCreateInstanceField((JactlObject) parent, field, source, offset, isMap);
    }

    String parentString = null;
    if (!(parent instanceof List) && (parentString = castToString(parent)) == null && !(parent instanceof Object[])) {
      throw new RuntimeError("Invalid parent object type (" + className(parent) + "): expected Map/List" +
                             (isDot ? "" : " or String"), source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for " + className(parent), source, offset);
    }

    // Must be a List or String so field should be an index
    if (!(field instanceof Number)) {
      throw new RuntimeError("Non-numeric value for indexed access", source, offset);
    }

    int index = ((Number) field).intValue();
    if (parentString != null) {
      return loadStringChar(parentString, index, source, offset);
    }

    if (parent instanceof Object[]) {
      return loadObjectArrElem((Object[]) parent, index, source, offset);
    }

    return loadOrCreateListElem((List) parent, index, isMap, source, offset);
  }
  public static String LOAD_OR_CREATE_FIELD = "loadOrCreateField";

  private static Object loadOrCreateListElem(List parent, int index, boolean isMap, String source, int offset) {
    List   list  = parent;
    Object value = null;
    if (index < 0) {
      int newIndex = list.size() + index;
      if (newIndex < 0) {
        throw new RuntimeError("Index (" + index + ") out of range for List (size=" + list.size() + ")", source, offset);
      }
      index = newIndex;
    }
    if (index < list.size()) {
      value = list.get(index);
    }

    if (value == null) {
      value = isMap ? new LinkedHashMap<>() : new ArrayList<>();
      for (int i = list.size(); i < index + 1; i++) {
        list.add(null);
      }
      list.set(index, value);
    }
    return value;
  }

  private static Object loadListElem(List parent, int index, String source, int offset) {
    List   list  = parent;
    Object value = null;
    if (index < 0) {
      int newIndex = list.size() + index;
      if (newIndex < 0) {
        throw new RuntimeError("Index (" + index + ") out of range for List (size=" + list.size() + ")", source, offset);
      }
      index = newIndex;
    }
    if (index < list.size()) {
      value = list.get(index);
    }
    return value;
  }

  private static Object loadObjectArrElem(Object[] parent, int index, String source, int offset) {
    Object[] arr = parent;
    if (index < 0) {
      int newIndex = arr.length + index;
      if (newIndex < 0) {
        throw new RuntimeError("Index (" + index + ") out of range for Object[] (length=" + arr.length + ")", source, offset);
      }
      index = newIndex;
    }
    if (index < arr.length) {
      return arr[index];
    }
    return null;
  }

  private static String loadStringChar(String parentString, int index, String source, int offset) {
    if (index < 0) {
      int newIndex = parentString.length() + index;
      if (newIndex < 0) {
        throw new RuntimeError("Index (" + index + ") out of range for String (length=" + parentString.length() + ")", source, offset);
      }
      index = newIndex;
    }
    else
    if (index >= parentString.length()) {
      throw new RuntimeError("Index (" + index + ") too large for String (length=" + parentString.length() + ")", source, offset);
    }
    return Character.toString(parentString.charAt(index));
  }

  private static Object loadStaticFieldOrMethod(Object parent, Object field, String source, int offset) {
    Class clss = (Class) parent;
    String fieldName = field.toString();
    if (JactlObject.class.isAssignableFrom(clss)) {
      try {
        // Need to get map of static methods via getter rather than directly accessing the
        // _$j$StaticMethods field because the field exists in the parent JactlObject class
        // which means we can't guarantee that class init for the actual class (which populates
        // the map) has been run yet.
        Method                         staticMethods = clss.getMethod(Utils.JACTL_STATIC_FIELDS_METHODS_STATIC_GETTER);
        Map<String, JactlMethodHandle> map           = (Map<String, JactlMethodHandle>) staticMethods.invoke(null);
        Object value = map.get(fieldName);
        if (value == null && !map.containsKey(fieldName)) {
          throw new RuntimeError("No static field/method '" + fieldName + "' for class " + parent, source, offset);
        }
        return value;
      }
      catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      // Check for static method of a registered class (note: static const fields not yet supported for registered classes)
      // TODO: const static fields
      return getFunctionWrapperHandle(clss, fieldName, source, offset);
    }
  }

  private static JactlMethodHandle.FunctionWrapperHandle getFunctionWrapperHandle(Class<?> clss, String fieldName, String source, int offset) {
    ClassDescriptor descriptor = RuntimeState.getState().getContext().getRegisteredClasses().getClassDescriptor(clss);
    if (descriptor == null) {
      throw new RuntimeError("Class " + className(clss.getName()) + " not a supported Jactl type", source, offset);
    }
    FunctionDescriptor funDesc = descriptor.getMethod(fieldName);
    if (funDesc == null) {
      throw new RuntimeError("No static method '" + fieldName + "' for class " + className(clss.getName()), source, offset);
    }
    return funDesc.wrapperHandle;
  }

  private static Object loadOrCreateMapField(Object parent, Object field, boolean captureStackTrace, String source, int offset, boolean isMap) {
    if (field == null) {
      throw new NullError("Null value for field name", source, offset, captureStackTrace);
    }
    Map    map       = (Map) parent;
    Object value     = map.get(field);
    if (value == null) {
      value = isMap ? new LinkedHashMap<>() : new ArrayList<>();
      map.put(field, value);
    }
    if (value == null && field instanceof String) {
      // If we still can't find a field then if we have a method of the name return
      // its method handle
      value = RuntimeState.getState().getContext().getFunctions().lookupWrapper(parent, (String)field);
    }

    return value;
  }

  public static String LOAD_MAP_FIELD = "loadMapField";
  public static Object loadMapField(Object parent, Object field, boolean isOptional, boolean captureStackTrace, String source, int offset) {
    if (parent == null) {
      if (isOptional) {
        return null;
      }
      throw new NullError("Null value for parent during field access", source, offset, captureStackTrace);
    }
    return loadMapField(parent, field, captureStackTrace, source, offset);
  }

  private static Object loadMapField(Object parent, Object field, boolean captureStackTrace, String source, int offset) {
    if (field == null) {
      throw new NullError("Null value for field name", source, offset, captureStackTrace);
    }
    Map map = (Map) parent;
    Object value = map.get(field);
    if (value == null && field instanceof String) {
      // If we still can't find a field then if we have a method of the name return
      // its method handle
      value = RuntimeState.getState().getContext().getFunctions().lookupWrapper(parent, (String)field);
    }

    return value;
  }

  public static final String LOAD_ARRAY_FIELD = "loadArrayField";
  public static Object loadArrayField(Object parent, Object idxObj, String source, int offset) {
    int idx = castToInt(idxObj, true, source, offset);
    return loadArrayFieldInt(parent, idx, source, offset);
  }

  public static final String LOAD_ARRAY_FIELD_INT = "loadArrayFieldInt";
  public static Object loadArrayFieldInt(Object parent, int idx, String source, int offset) {
    try {
      if (parent instanceof String) {
        String str = (String)parent;
        return String.valueOf(str.charAt(idx >= 0 ? idx : idx + str.length()));
      }
      if (parent instanceof List) {
        return ((List)parent).get(idx);
      }
      if (parent instanceof int[]) {
        int[] arr = (int[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
      if (parent instanceof byte[]) {
        byte[] arr = (byte[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
      if (parent instanceof Object[]) {
        Object[] arr = (Object[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
      if (parent instanceof long[]) {
        long[] arr = (long[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
      if (parent instanceof double[]) {
        double[] arr = (double[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
      if (parent instanceof boolean[]) {
        boolean[] arr = (boolean[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length];
      }
    }
    catch (StringIndexOutOfBoundsException|ArrayIndexOutOfBoundsException e) {
      throw new RuntimeError("Index out of bounds: " + idx, source, offset);
    }
    throw new IllegalStateException("Internal error: unexpected array type " + className(parent));
  }

  public static Object storeArrayField(Object parent, Object field, Object value, String source, int offset) {
    if (!(field instanceof Number)) {
      throw new RuntimeError("Non-numeric array index (" + className(field) + ")", source, offset);
    }
    int idx = ((Number)field).intValue();
    try {
      if (parent instanceof int[]) {
        int[] arr = (int[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = ((Number)value).intValue();
      }
      if (parent instanceof BigDecimal[]) {
        BigDecimal[] arr = (BigDecimal[])parent;
        BigDecimal result;
        if      (value instanceof BigDecimal) { result = (BigDecimal)value; }
        else if (value instanceof Double)     { result = BigDecimal.valueOf((double)value); }
        else                                  { result = BigDecimal.valueOf(((Number)value).longValue()); }
        return arr[idx >= 0 ? idx : idx + arr.length] = result;
      }
      if (parent instanceof Object[]) {
        Object[] arr = (Object[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = value;
      }
      if (parent instanceof byte[]) {
        byte[] arr = (byte[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = ((Number)value).byteValue();
      }
      if (parent instanceof long[]) {
        long[] arr = (long[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = ((Number)value).longValue();
      }
      if (parent instanceof double[]) {
        double[] arr = (double[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = ((Number)value).doubleValue();
      }
      if (parent instanceof boolean[]) {
        boolean[] arr = (boolean[])parent;
        return arr[idx >= 0 ? idx : idx + arr.length] = isTruth(value, false);
      }
    }
    catch (ArrayIndexOutOfBoundsException e) {
      throw new RuntimeError("Index out of bounds: " + idx, source, offset);
    }
    catch (ArrayStoreException|ClassCastException e) {
      throw new RuntimeError("Cannot store object of type " + className(value) + " in " + className(parent), source, offset);
    }
    throw new IllegalStateException("Internal error: unexpected array type " + className(parent));
  }

  /**
   * Get the value for given field of a JactlObject. Field could be an actual field or a method. If createIfMissing is
   * set and field is null then we will create a default value for the field. For fields that are of type Object we
   * create a Map or List based on the isMap field. NOTE: createIfMissing is only ever set in a Map/List context on lhs
   * of assignment or assignment-like expression so we know that we want something that looks like a Map/List.
   */
  private static Object loadOrCreateInstanceField(JactlObject parent, Object field, String source, int offset, boolean isMap) {
    // Check for field, instance method, static method, and then if that fails check if there
    // is a generic built-in method that applies
    String fieldName     = field.toString();
    Object fieldOrMethod = parent._$j$getFieldsAndMethods().get(fieldName);
    if (fieldOrMethod instanceof JactlMethodHandle) {
      // Need to bind method handle to instance
      fieldOrMethod = ((JactlMethodHandle) fieldOrMethod).bindTo(parent);
    }
    if (fieldOrMethod == null) {
      throw new RuntimeError("No such field '" + fieldName + "' for type " + parent.getClass().getName(), source, offset);
    }
    // If we have a field handle then we need to get the field value
    if (fieldOrMethod instanceof Field) {
      Field classField = (Field) fieldOrMethod;
      try {
        Object value = classField.get(parent);
        if (value == null) {
          Class<?> fieldType = classField.getType();
          if (JactlObject.class.isAssignableFrom(fieldType)) {
            JactlObject fieldObj = (JactlObject) fieldType.getConstructor().newInstance();
            try {
              fieldObj._$j$init$$w(null, source, offset, new Object[0]);
            }
            catch (Continuation c) {
              throw new RuntimeError("Detected async code in field initialiser for " + fieldName + " in type " + parent.getClass().getName(), source, offset);
            }
            classField.set(parent, fieldObj);
            value = fieldObj;
          }
          else {
            // Check field is of compatible type
            if (fieldType == Object.class ||
                isMap && fieldType.isAssignableFrom(Map.class) ||
                fieldType.isAssignableFrom(List.class)) {
              value = defaultValue(isMap ? Utils.JACTL_MAP_TYPE : Utils.JACTL_LIST_TYPE, source, offset);
              classField.set(parent, value);
            }
            else {
              throw new RuntimeError("Field '" + fieldName + "' of type " + fieldType + " cannot be set to " +
                                     (isMap ? "Map" : "List"), source, offset);
            }
          }
        }
        return value;
      }
      catch (IllegalAccessException | InvocationTargetException | InstantiationException | NoSuchMethodException e) {
        throw new IllegalStateException("Internal error: " + e, e);
      }
    }
    else {
      return fieldOrMethod;
    }
  }

  private static Object loadInstanceField(JactlObject parent, Object field, String source, int offset) {
    // Check for field, instance method, static method, and then if that fails check if there
    // is a generic built-in method that applies
    String fieldName     = field.toString();
    Object fieldOrMethod = parent._$j$getFieldsAndMethods().get(fieldName);
    if (fieldOrMethod instanceof JactlMethodHandle) {
      // Need to bind method handle to instance
      fieldOrMethod = ((JactlMethodHandle) fieldOrMethod).bindTo(parent);
    }
    if (fieldOrMethod == null) {
      // search for matching static field or method
      fieldOrMethod = parent._$j$getStaticFieldsAndMethods().get(fieldName);
      if (fieldOrMethod == null) {
        fieldOrMethod = RuntimeState.getState().getContext().getFunctions().lookupWrapper(parent, fieldName);
      }
    }
    if (fieldOrMethod == null) {
      throw new RuntimeError("No such field '" + fieldName + "' for type " + parent.getClass().getName(), source, offset);
    }
    // If we have a field handle then we need to get the field value
    if (fieldOrMethod instanceof Field) {
      Field classField = (Field) fieldOrMethod;
      try {
        return classField.get(parent);
      }
      catch (IllegalAccessException e) {
        throw new IllegalStateException("Internal error: " + e, e);
      }
    }
    else {
      return fieldOrMethod;
    }
  }

  public static String GET_FIELD_GETTER = "getFieldGetter";
  public static Field getFieldGetter(Object parentObj, Object field, boolean captureStackTrace, String source, int offset) {
    JactlObject parent = (JactlObject) parentObj;
    if (field == null) {
      throw new NullError("Null value for field name", source, offset, captureStackTrace);
    }
    if (!(field instanceof String)) {
      throw new RuntimeError("Expected String for field name, not " + className(field), source, offset);
    }
    String fieldName = (String) field;
    Object result    = parent._$j$getFieldsAndMethods().get(fieldName);
    if (result == null) {
      throw new RuntimeError("No such field '" + fieldName + "' for type " + className(parent), source, offset);
    }
    if (result instanceof Field) {
      return (Field) result;
    }
    throw new RuntimeError("Expected field '" + fieldName + "' but found method", source, offset);
  }

  public static Object defaultValue(Class clss, String source, int offset) {
    if (Map.class.isAssignableFrom(clss)) {
      return new LinkedHashMap<>();
    }
    if (List.class.isAssignableFrom(clss)) {
      return new ArrayList<>();
    }
    if (String.class.isAssignableFrom(clss)) {
      return "";
    }
    if (Boolean.class.isAssignableFrom(clss)) {
      return false;
    }
    if (Integer.class.isAssignableFrom(clss)) {
      return 0;
    }
    if (Long.class.isAssignableFrom(clss)) {
      return 0L;
    }
    if (Double.class.isAssignableFrom(clss)) {
      return 0D;
    }
    if (BigDecimal.class.isAssignableFrom(clss)) {
      return BigDecimal.ZERO;
    }
    throw new RuntimeError("Default value for " + clss.getName() + " not supported", source, offset);
  }

  public static String STORE_PARENT_FIELD_VALUE = "storeParentFieldValue";
  public static Object storeParentFieldValue(Object parent, Object field, Object value, boolean isDot, boolean captureStackTrace, String source, int offset) {
    return storeValueParentField(value, parent, field, isDot, captureStackTrace, source, offset);
  }

  public static String STORE_VALUE_PARENT_FIELD = "storeValueParentField";
  public static Object storeValueParentField(Object value, Object parent, Object field, boolean isDot, boolean captureStackTrace, String source, int offset) {
    if (parent instanceof Map) {
      return storeMapField(value, (Map)parent, field, captureStackTrace, source, offset);
    }

    if (parent == null) {
      throw new NullError("Null value for Map/List/Object storing field value", source, offset, captureStackTrace);
    }

    if (!isDot && parent.getClass().isArray()) {
      value = castTo(parent.getClass().getComponentType(), value, captureStackTrace, source, offset);
      storeArrayField(parent, field, value, source, offset);
      return value;
    }

    if (parent instanceof JactlObject) {
      return storeInstanceField(parent, field, value, captureStackTrace, source, offset);
    }

    if (!(parent instanceof List)) {
      throw new RuntimeError("Invalid object type (" + className(parent) + ") for storing value: expected Map/List/Array", source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for object of type " + className(parent), source, offset);
    }

    return storeListField((List) parent, field, value, source, offset);
  }

  private static Object storeListField(List parent, Object field, Object value, String source, int offset) {
    int index = -1;
    try { index = ((Number)field).intValue(); } catch (ClassCastException e) { throw new RuntimeError("Non-numeric value for index during List access", source, offset); }
    if (index < 0) {
      int originalIndex = index;
      index += parent.size();
      if (index < 0) {
        throw new RuntimeError("Negative index (" + originalIndex + ") out of range (list size is " + parent.size() + ")", source, offset);
      }
    }
    if (index >= parent.size()) {
      // Grow list to required size
      for (int i = parent.size(); i < index + 1; i++) {
        parent.add(null);
      }
    }
    parent.set(index, value);
    return value;
  }

  private static Object storeInstanceField(Object parent, Object field, Object value, boolean captureStackTrace, String source, int offset) {
    String       fieldName     = field.toString();
    JactlObject jactlObj     = (JactlObject) parent;
    Object       fieldOrMethod = jactlObj._$j$getFieldsAndMethods().get(fieldName);
    if (fieldOrMethod == null) {
      if (jactlObj._$j$getStaticFieldsAndMethods().get(fieldName) != null) {
        throw new RuntimeError("Cannot modify a constant field '" + fieldName + "' of class " + className(parent), source, offset);
      }
      throw new RuntimeError("No such field '" + fieldName + "' for class " + className(parent), source, offset);
    }
    if (!(fieldOrMethod instanceof Field)) {
      throw new RuntimeError("Found method " + fieldName + "() for class " + className(parent) + " where field expected", source, offset);
    }
    try {
      Field fieldRef = (Field) fieldOrMethod;
      value = castTo(fieldRef.getType(), value, captureStackTrace, source, offset);
      fieldRef.set(parent, value);
      return value;
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("Internal error: " + e, e);
    }
  }

  public static String STORE_MAP_FIELD = "storeMapField";
  public static Object storeMapField(Object value, Map parent, Object field, boolean captureStackTrace, String source, int offset) {
    if (parent == null) {
      throw new NullError("Null value for Map/List/Object storing field value", source, offset, captureStackTrace);
    }

    if (field == null) {
      throw new NullError("Null value for field name", source, offset, captureStackTrace);
    }
    parent.put(field, value);
    return value;
  }

  /**
   * Create an iterator from List, Map, Object[], or if not one of those
   * then return an iterator over singleton list containing given object.
   * Used by flatMap to give results that more closely match what would
   * be naively expected rather than createIterator() which would turn
   * String and Number objects into lists of chars or list of numbers.
   * @param obj  the object of type List, Map, Object[] or single object
   * @return an iterator that iterates over the object
   */
  public static JactlIterator createIteratorFlatMap(Object obj) {
    JactlIterator iter = createIteratorOrNull(obj);
    if (iter != null) {
      return iter;
    }
    if (obj == null) {
      return JactlIterator.of();
    }
    return JactlIterator.of(obj);
  }

  public static JactlIterator createIterator(Object obj) {
    JactlIterator iter = createIteratorOrNull(obj);
    if (iter != null) {
      return iter;
    }
    if (obj instanceof Number) { return JactlIterator.numberIterator((Number)obj); }
    if (obj instanceof String) { return JactlIterator.stringIterator((String)obj); }
    throw new IllegalStateException("Internal error: unexpected type " + obj.getClass().getName() + " for iterable");
  }

  public static JactlIterator createIteratorOrNull(Object obj) {
    if (obj instanceof JactlIterator) { return (JactlIterator)obj;               }
    if (obj instanceof List)          { return JactlIterator.of((List)obj);      }
    if (obj instanceof Map)           { return JactlIterator.of((Map)obj);       }
    if (obj instanceof Object[])      { return JactlIterator.of((Object[])obj);  }
    if (obj instanceof int[])         { return JactlIterator.of((int[])obj);     }
    if (obj instanceof byte[])        { return JactlIterator.of((byte[])obj);    }
    if (obj instanceof long[])        { return JactlIterator.of((long[])obj);    }
    if (obj instanceof boolean[])     { return JactlIterator.of((boolean[])obj); }
    if (obj instanceof double[])      { return JactlIterator.of((double[])obj);  }
    return null;
  }

  public static List concat(Object... objs) {
    ArrayList<Object> result = new ArrayList<>();
    for (Object obj: objs) {
      if (obj instanceof List) {
        result.addAll((List) obj);
      }
      else
      if (obj instanceof Object[]) {
        result.addAll(Arrays.asList((Object[])obj));
      }
      else {
        result.add(obj);
      }
    }
    return result;
  }

  public static final String CREATE_MAP = "createMap";
  public static Map createMap() {
    return new LinkedHashMap();
  }

  public static Map createMap(Map map) {
    return new LinkedHashMap(map);
  }

  public static final String CREATE_LIST = "createList";
  public static List createList() {
    return new ArrayList();
  }


  private enum FieldType {
    ANY,
    BOOLEAN,
    BYTE,
    INT,
    LONG,
    DOUBLE,
    DECIMAL,
    STRING,
    MAP,
    LIST,
    FUNCTION;
  }

  private static Map<Class, FieldType> classToType = Utils.mapOf(
    boolean.class, FieldType.BOOLEAN,
    byte.class, FieldType.BYTE,
    int.class, FieldType.INT,
    long.class, FieldType.LONG,
    double.class, FieldType.DOUBLE,
    BigDecimal.class, FieldType.DECIMAL,
    String.class, FieldType.STRING,
    Map.class, FieldType.MAP,
    List.class, FieldType.LIST,
    JactlMethodHandle.class, FieldType.FUNCTION,
    JactlObject.class, FieldType.ANY
  );

  public static Object castTo(Class clss, Object value, boolean captureStackTrace, String source, int offset) {
    if (clss.isArray()) {
      return castToArray(value, clss, captureStackTrace, source, offset);
    }
    FieldType type = classToType.get(clss);
    if (type == null) {
      if (value == null || clss.isAssignableFrom(value.getClass())) {
        return value;
      }
    }
    else {
      switch (type) {
        case BOOLEAN:  return isTruth(value, false);
        case BYTE:     return castToByte(value, captureStackTrace, source, offset);
        case INT:      return castToInt(value, captureStackTrace, source, offset);
        case LONG:     return castToLong(value, captureStackTrace, source, offset);
        case DOUBLE:   return castToDouble(value, captureStackTrace, source, offset);
        case DECIMAL:  return castToDecimal(value, source, offset);
        case STRING:   return castToString(value, source, offset);
        case MAP:      return castToMap(value, source, offset);
        case LIST:     return castToList(value, source, offset);
        case FUNCTION: return castToFunction(value, captureStackTrace, source, offset);
        case ANY:      return value;
      }
    }
    throw new RuntimeError("Cannot convert from " + className(value) + " to type " + clss.getName(), source, offset);
  }

  public static String CAST_TO_INT_VALUE = "castToIntValue";
  public static int castToIntValue(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof Integer) { return (int) obj; }
    if (obj instanceof Long)    { return (int)(long) obj; }
    if (obj instanceof Byte)    { return (byte)obj; }
    if (obj == null)            { throw new NullError("Null value for int", source, offset, captureStackTrace); }
    throw new RuntimeError("Must be int or long: cannot cast object of type " + className(obj) + " to int", source, offset);
  }

  public static String CAST_TO_LONG_VALUE = "castToLongValue";
  public static long castToLongValue(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof Long)    { return (long) obj; }
    if (obj instanceof Integer) { return (int) obj; }
    if (obj instanceof Byte)    { return (byte) obj; }
    if (obj == null)            { throw new NullError("Null value for long", source, offset, captureStackTrace); }
    throw new RuntimeError("Must be int or long: cannot cast object of type " + className(obj) + " to long", source, offset);
  }

  public static String castToString(Object obj, String source, int offset) {
    if (obj instanceof String) {
      return (String) obj;
    }
    if (obj == null) {
      return null;
    }
    if (obj instanceof byte[]) {
      return new String((byte[])obj, StandardCharsets.UTF_8);
    }
    throw new RuntimeError("Cannot convert object of type " + className(obj) + " to String", source, offset);
  }

  public static Map castToMap(Object obj, String source, int offset) {
    if (obj instanceof Map) {
      return (Map) obj;
    }
    if (obj == null) {
      return null;
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Map", source, offset);
  }

  public static List castToList(Object obj, String source, int offset) {
    if (obj instanceof List) {
      return (List) obj;
    }
    if (obj instanceof Object[]) {
      return Arrays.asList((Object[]) obj);
    }
    if (obj == null) {
      return null;
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to List", source, offset);
  }

  public static JactlMethodHandle convertIteratorToList$cHandle = lookupMethod(RuntimeUtils.class, "convertIteratorToList$c", Object.class, Continuation.class);
  public static Object convertIteratorToList$c(Continuation c) {
    return convertIteratorToList(c.localObjects[1], c);
  }

  public static final String CONVERT_ITERATOR_TO_LIST = "convertIteratorToList";
  public static List convertIteratorToList(Object iterable, Continuation c) {
    JactlIterator iter = createIterator(iterable);

    // If we are continuing (c != null) then get objects we have stored previously.
    // Object arr will have: result
    Object[] objects = c == null ? null : c.localObjects;

    List result = objects == null ? new ArrayList() : (List) objects[0];

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
            hasNext = (boolean) c.getResult();
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
            elem = c.getResult();
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            if (elem instanceof Map.Entry) {
              Map.Entry entry = (Map.Entry) elem;
              elem = Utils.listOf(entry.getKey(), entry.getValue());
            }
            result.add(elem);
            methodLocation = 0;       // Back to initial state
            break;
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, convertIteratorToList$cHandle,methodLocation + 1, null, new Object[]{ result, iterable });
    }
  }

  public static Byte byteValueOf(int b) {
    b = b & 0xff;
    return Byte.valueOf(b >= 128 ? (byte)(b - 256) : (byte)b);
  }

  public static String CAST_TO_BYTE = "castToByte";
  public static byte castToByte(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof Number) {
      return ((Number) obj).byteValue();
    }
    if (obj instanceof String) {
      String value = (String) obj;
      if (value.length() != 1) {
        throw new RuntimeError((value.isEmpty() ? "Empty String" : "String with multiple chars") + " cannot be cast to byte", source, offset);
      }
      return (byte) (value.charAt(0));
    }
    if (obj == null) {
      throw new NullError("Cannot convert null value to byte", source, offset, captureStackTrace);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to byte", source, offset);
  }

  public static String CAST_TO_INT = "castToInt";
  public static int castToInt(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof Byte) {
      return ((int)(byte)obj) & 0xff;
    }
    if (obj instanceof Number) {
      return ((Number) obj).intValue();
    }
    if (obj instanceof String) {
      String value = (String) obj;
      if (value.length() != 1) {
        throw new RuntimeError((value.isEmpty() ? "Empty String" : "String with multiple chars") + " cannot be cast to int", source, offset);
      }
      return (int) (value.charAt(0));
    }
    if (obj == null) {
      throw new NullError("Cannot convert null value to int", source, offset, captureStackTrace);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to int", source, offset);
  }

  public static Number castToLong(Object obj, boolean captureStackTrace, String source, int offset) {
    return castToNumber(obj, captureStackTrace, source, offset).longValue();
  }

  public static Number castToDouble(Object obj, boolean captureStackTrace, String source, int offset) {
    return castToNumber(obj, captureStackTrace, source, offset).doubleValue();
  }

  public static String CAST_TO_NUMBER = "castToNumber";
  public static Number castToNumber(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof Byte) {
      return ((int)(byte)obj) & 0xff;
    }
    if (obj instanceof Number) {
      return (Number) obj;
    }
    if (obj == null) {
      throw new NullError("Cannot convert null value to Number", source, offset, captureStackTrace);
    }
    if (obj instanceof String) {
      String value = (String)obj;
      if (value.length() != 1) {
        throw new RuntimeError((value.isEmpty() ? "Empty String" : "String with multiple chars") + " cannot be cast to number", source, offset);
      }
      return (int) (value.charAt(0));
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Number", source, offset);
  }

  public static BigDecimal castToDecimal(Object obj, String source, int offset) {
    if (obj instanceof Number) {
      return toBigDecimal(obj);
    }
    if (obj == null) {
      return null;
    }
    if (obj instanceof String) {
      String value = (String)obj;
      if (value.length() != 1) {
        throw new RuntimeError((value.isEmpty() ? "Empty String" : "String with multiple chars") + " cannot be cast to Decimal", source, offset);
      }
      return BigDecimal.valueOf(value.charAt(0));
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Decimal", source, offset);
  }

  public static String CAST_TO_FUNCTION = "castToFunction";
  public static JactlMethodHandle castToFunction(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj instanceof JactlMethodHandle) {
      return (JactlMethodHandle) obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Function", source, offset, captureStackTrace);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Function", source, offset);
  }

  public static String AS_ARRAY = "asArray";
  public static Object asArray(Object obj, Class clss, boolean captureStackTrace, String source, int offset) {
    return convertToArray(obj, clss, false, captureStackTrace, source, offset);
  }

  public static String CAST_TO_ARRAY = "castToArray";
  public static Object castToArray(Object obj, Class clss, boolean captureStackTrace, String source, int offset) {
    return convertToArray(obj, clss, true, captureStackTrace, source, offset);
  }

  private static Object convertToArray(Object obj, Class clss, boolean isCast, boolean captureStackTrace, String source, int offset) {
    if (obj == null) {
      return null;
    }
    if (clss.isAssignableFrom(obj.getClass())) {
      return obj;
    }
    Class  componentType = clss.getComponentType();
    if (obj instanceof List) {
      List   list = (List)obj;
      Object arr  = Array.newInstance(componentType, list.size());
      for (int i = 0; i < list.size(); i++) {
        Array.set(arr, i, castToType(list.get(i), componentType, isCast, captureStackTrace, source, offset));
      }
      return arr;
    }
    if (obj.getClass().isArray()) {
      int    length = Array.getLength(obj);
      Object arr    = Array.newInstance(componentType, length);
      for (int i = 0; i < length; i++) {
        Array.set(arr, i, castToType(Array.get(obj, i), componentType, isCast, captureStackTrace, source, offset));
      }
      return arr;
    }
    if (obj instanceof String && componentType == byte.class) {
      return ((String)obj).getBytes(StandardCharsets.UTF_8);
    }
    throw new RuntimeError("Cannot cast from " + className(obj) + " to " + RuntimeState.getState().getContext().typeFromClass(clss), source, offset);
  }

  private static Object castToType(Object obj, Class clss, boolean isCast, boolean captureStackTrace, String source, int offset) {
    if (clss.isArray())                        { return convertToArray(obj, clss, isCast, captureStackTrace, source, offset); }
    if (clss.isPrimitive() || clss == BigDecimal.class) {
      ensureNonNull(obj, captureStackTrace, source, offset);
      if (clss == boolean.class)               { return isTruth(obj, false); }
      if (obj instanceof String && ((String)obj).length() == 1) {
        obj = (int)((String)obj).charAt(0);
      }
      if (obj instanceof Number) {
        Number num = (Number)obj;
        if (clss == byte.class)                { return num.byteValue(); }
        if (clss == int.class)                 { return num.intValue(); }
        if (clss == long.class)                { return num.longValue(); }
        if (clss == double.class)              { return num.doubleValue(); }
        if (clss == BigDecimal.class) {
          if (num instanceof BigDecimal)       { return num; }
          return num instanceof Double ? BigDecimal.valueOf((double)num)
                                       : BigDecimal.valueOf(num.longValue());
        }
        throw new RuntimeError("Incompatible types. Cannot convert " + className(obj) + " to " + RuntimeState.getState().getContext().typeFromClass(clss), source, offset);
      }
      throw new RuntimeError("Incompatible types. Cannot convert " + className(obj) + " to " + RuntimeState.getState().getContext().typeFromClass(clss), source, offset);
    }
    if (!isCast && clss == String.class)       { return toStringOrNull(obj); }
    if (clss.isAssignableFrom(obj.getClass())) { return obj; }
    throw new RuntimeError("Incompatible types. Cannot convert " + className(obj) + " to " + RuntimeState.getState().getContext().typeFromClass(clss), source, offset);
  }

  public static final String CAN_CAST_TO_TYPE = "canCastToType";
  public static boolean canCastToType(Object obj, Class clss, Class unboxedClass) {
    if (obj == null)                           { return false; }
    if (clss.equals(Object.class))             { return true; }
    Class<?> objClass = obj.getClass();
    if (objClass.equals(clss))                 { return true; }
    if (unboxedClass.isPrimitive() || clss.equals(BigDecimal.class)) {
      if (clss.equals(Boolean.class))          { return false; }   // Since obj.class != clss from above
      return unboxedClass.equals(int.class) && objClass.equals(Byte.class);
    }
    if (clss.isArray())                        { return canConvertToArray(obj, clss); }
    if (clss.equals(String.class))             { return false; }
    return clss.isAssignableFrom(objClass);
  }

  public static boolean canConvertToArray(Object obj, Class clss) {
    if (obj == null)                           { return true; }
    if (clss.isAssignableFrom(obj.getClass())) { return true; }
    Class  componentType = clss.getComponentType();
    if (obj instanceof List) {
      List   list = (List)obj;
      for (int i = 0; i < list.size(); i++) {
        if (!canCastToType(list.get(i), componentType, componentType)) {
          return false;
        }
      }
      return true;
    }
    if (obj.getClass().isArray()) {
      int    length = Array.getLength(obj);
      for (int i = 0; i < length; i++) {
        if (!canCastToType(Array.get(obj, i), componentType, componentType)) {
          return false;
        }
      }
      return true;
    }
    if (obj instanceof String && componentType == byte.class) {
      return true;
    }
    return false;
  }

  /**
   * Same as Object.equals() except that Byte and Integer are treated
   * as equivalent to allow byte values to match int literals in switch
   * expressions.
   * Also allow o1 and o2 to be null.
   * Note that o1 can be an array in which case it can be compared with
   * a list value for o2.
   */
  public static final String SWITCH_EQUALS = "switchEquals";
  public static boolean switchEquals(Object o1, Object o2) {
    if (o1 == null || o2 == null) {
      return o1 == o2;
    }
    if (o1 instanceof Byte && o2 instanceof Integer) {
      return ((Byte) o1).intValue() == ((Integer) o2).intValue();
    }
    if (o2 instanceof Byte && o1 instanceof Integer) {
      return ((Byte) o2).intValue() == ((Integer) o1).intValue();
    }
    if (o1 instanceof List && o2 instanceof List) {
      List l1 = (List)o1;
      List l2 = (List)o2;
      if (l1.size() != l2.size()) { return false; }
      for (int i = 0; i < l1.size(); i++) {
        if (!switchEquals(l1.get(i),l2.get(i))) {
          return false;
        }
      }
      return true;
    }
    if (o1.getClass().isArray() && o2 instanceof List) {
      List l2 = (List)o2;
      if (Array.getLength(o1) != l2.size()) { return false; }
      for (int i = 0; i < l2.size(); i++) {
        if (!switchEquals(Array.get(o1,i),l2.get(i))) {
          return false;
        }
      }
      return true;
    }
    if (o1 instanceof Map && o2 instanceof Map) {
      Map<String,Object> m1 = (Map)o1;
      Map<String,Object> m2 = (Map)o2;
      if (m1.size() != m2.size()) { return false; }
      for (Object key: m1.keySet()) {
        if (!switchEquals(m1.get(key), m2.get(key))) {
          return false;
        }
      }
      return true;
    }
    return o1.equals(o2);
  }

  /**
   * Get integer value or null if not int/byte
   */
  public static final String INT_VALUE = "intValue";
  public static Integer intValue(Object obj) {
    if (obj instanceof Integer) {
      return (Integer)obj;
    }
    if (obj instanceof Byte) {
      return ((Byte)obj).intValue();
    }
    return null;
  }

  public static final String IS_PATTERN_COMPATIBLE = "isPatternCompatible";
  public static boolean isPatternCompatible(Object obj, Class clss) {
    if (clss.isArray())  { return isArrayType(obj, clss); }
    if (obj == null)     { return clss.equals(Object.class); }
    JactlType clssType = RuntimeState.getState().getContext().typeFromClass(clss).unboxed();
    JactlType objType  = RuntimeState.getState().getContext().typeOf(obj).unboxed();
    if (clssType.isNumeric() && objType.isNumeric()) {
      return objType.is(clssType) || objType.is(JactlType.BYTE) && clssType.is(JactlType.INT);
    }
    if (obj instanceof JactlObject) {
      return clss.isAssignableFrom(obj.getClass());
    }
    if (clss.equals(List.class) && obj.getClass().isArray()) {
      return true;
    }
    if (clss.isAssignableFrom(obj.getClass())) {
      return true;
    }
    return obj.getClass().equals(clss);
  }

  public static boolean isArrayType(Object obj, Class clss) {
    if (clss.isAssignableFrom(obj.getClass())) { return true; }
    Class  componentType = clss.getComponentType();
    if (obj instanceof List) {
      List   list = (List)obj;
      for (int i = 0; i < list.size(); i++) {
        if (!isPatternCompatible(list.get(i), componentType)) {
          return false;
        }
      }
      return true;
    }
    if (obj.getClass().isArray()) {
      int    length = Array.getLength(obj);
      for (int i = 0; i < length; i++) {
        if (!isPatternCompatible(Array.get(obj, i), componentType)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static boolean print(String obj) {
    return doPrint(obj, false);
  }

  public static boolean println(String obj) {
    return doPrint(obj, true);
  }

  private static boolean doPrint(String obj, boolean newLine) {
    if (obj == null) { obj = "null"; }
    RuntimeState state = RuntimeState.getState();
    PrintStream  out   = state.getOutput();
    if (out == null) {
      out = System.out;
    }
    if (newLine) {
      out.println(obj);
    }
    else {
      out.print(obj);
    }
    return true;
  }

  //////////////////////////////////////

  public static BigDecimal toBigDecimal(Object val) {
    if (val instanceof BigDecimal) { return (BigDecimal)val; }
    if (val instanceof Integer)    { return BigDecimal.valueOf((int)val); }
    if (val instanceof Long)       { return BigDecimal.valueOf((long)val); }
    if (val instanceof Double)     { return BigDecimal.valueOf((double)val); }
    if (val instanceof Byte)       { return BigDecimal.valueOf(((int)(byte)val) & 0xff); }
    return null;
  }

  public static String className(Object obj) {
    if (obj == null)                      { return "null"; }
    if (obj instanceof String)            { return "String"; }
    if (obj instanceof BigDecimal)        { return "Decimal"; }
    if (obj instanceof Double)            { return "double"; }
    if (obj instanceof Long)              { return "long"; }
    if (obj instanceof Byte)              { return "byte"; }
    if (obj instanceof Integer)           { return "int"; }
    if (obj instanceof Boolean)           { return "boolean"; }
    if (obj instanceof Map)               { return "Map"; }
    if (obj instanceof List)              { return "List"; }
    if (obj instanceof JactlIterator)          { return "JactlIterator"; }
    if (obj instanceof JactlMethodHandle) { return "Function"; }
    if (obj.getClass().isArray())         { return componentType(obj.getClass().getComponentType()) + "[]"; }
    if (obj instanceof JactlObject) {
      try {
        return (String)obj.getClass().getDeclaredField(Utils.JACTL_PRETTY_NAME_FIELD).get(null);
      }
      catch (IllegalAccessException | NoSuchFieldException e) {
        throw new IllegalStateException("Internal error: error accessing " + Utils.JACTL_PRETTY_NAME_FIELD, e);
      }
    }
    JactlContext context = RuntimeState.getState().getContext();
    ClassDescriptor desc = context.getRegisteredClasses().getClassDescriptor(obj.getClass());
    if (desc != null) {
      return desc.getClassName();
    }
    return obj.getClass().getName();
  }

  private static String componentType(Class clss) {
    if (clss.equals(Object.class))            { return "Object"; }
    if (clss.equals(String.class))            { return "String"; }
    if (clss.equals(BigDecimal.class))        { return "Decimal"; }
    if (clss.equals(Double.class))            { return "double"; }
    if (clss.equals(Long.class))              { return "long"; }
    if (clss.equals(Integer.class))           { return "int"; }
    if (clss.equals(Boolean.class))           { return "boolean"; }
    if (clss.equals(Map.class))               { return "Map"; }
    if (clss.equals(List.class))              { return "List"; }
    if (clss.equals(JactlIterator.class))          { return "Iterator"; }
    if (clss.equals(JactlMethodHandle.class)) { return "Function"; }
    if (clss.isArray())                       { return componentType(clss.getComponentType()) + "[]"; }
    if (JactlObject.class.isAssignableFrom(clss)) {
      try {
        return (String)clss.getDeclaredField(Utils.JACTL_PRETTY_NAME_FIELD).get(null);
      }
      catch (IllegalAccessException | NoSuchFieldException e) {
        throw new IllegalStateException("Internal error: error accessing " + Utils.JACTL_PRETTY_NAME_FIELD, e);
      }
    }
    return clss.getName();
  }

  private static void ensureNonNull(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null value encountered where null not allowed", source, offset, captureStackTrace);
    }
  }

  public static List<String> lines(String str) {
    if (str.isEmpty()) {
      return Utils.listOf(str);
    }
    List<String> lines = new ArrayList<>();
    int offset = 0;
    int lastOffset = 0;
    while ((offset = str.indexOf('\n', lastOffset)) != -1) {
      lines.add(str.substring(lastOffset, offset));
      lastOffset = offset + 1;  // skip new line
    }
    if (lastOffset < str.length()) {
      lines.add(str.substring(lastOffset));
    }
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

  /**
   * Return a JactlMethodHandle that points to the given static method of the given class.
   * Assumes there is a public static data member of the same class of type JactlMethodHandle
   * where this method handle will be stored (used for externalising execution state).
   * @param clss       the class
   * @param method     the name of the static method
   * @param returnType the return type of the method
   * @param argTypes   the argument types for the method
   * @return the JactlMethodHandle
   * @throws IllegalStateException if method does not exist
   */
  public static JactlMethodHandle lookupMethod(Class clss, String method, Class returnType, Class... argTypes) {
    try {
      return JactlMethodHandle.create(MethodHandles.lookup().findStatic(clss, method, MethodType.methodType(returnType, argTypes)),
                                      clss, method + "Handle");
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Error finding method " + method, e);
    }
  }
  
  public static JactlMethodHandle lookupMethod(Class clss, String jactlMethodName, Method method) {
    try {
      return JactlMethodHandle.create(MethodHandles.lookup().unreflect(method), clss,  jactlMethodName + "Handle");
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Return a JactlMethodHandle that points to the given static method of the given JactlIterator class.
   * @param clss       teh class
   * @param type       the JactlIterator type (one of JactlIterator.IteratorType)
   * @param method     the name of the static method
   * @param returnType the return type of the method
   * @param argTypes   the argument types for the method
   * @return the JactlMethodHandle
   * @throws IllegalStateException if method does not exist
   */
  public static JactlMethodHandle lookupMethod(Class clss, JactlIterator.IteratorType type, String method, Class returnType, Class... argTypes) {
    try {
      return JactlMethodHandle.create(MethodHandles.lookup().findStatic(clss, method, MethodType.methodType(returnType, argTypes)),
                                      type, method + "Handle");
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Error finding method " + method, e);
    }
  }

  /**
   * <p>Invoke a method handle that points to a closure/function.</p>
   * <p>NOTE: invoking a closure/function can throw a Continuation if closure/function is async
   * so you will need to catch the Continuation and rethrow a new one with your captured state
   * so that you can be resumes at some later point.</p>
   * @param handle  the method handle
   * @param source  the source code
   * @param offset  offset into source where call occurs
   * @param args    varargs set of arguments for closure/function
   * @return the result of invoking the closure/function
   * @throws RuntimeError if error occurs
   * @throws Continuation if execution state needs to be suspended
   */
  public static Object invoke(JactlMethodHandle handle, String source, int offset, Object... args) {
    try {
      return (Object)handle.invoke((Continuation)null, source, offset, new Object[0]);
    }
    catch (RuntimeError|Continuation e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeError("Error invoking closure/function", source, offset, e);
    }
  }


  /**
   * If we have a Map.Entry then convert to List so that when passed to a closure
   * the closure will see a list of [key,value]
   * @param elem  the element
   * @return a two entry List if elem is a Map.Entry or return elem otherwise
   */
  public static Object mapEntryToList(Object elem) {
    if (elem instanceof Map.Entry) {
      Map.Entry entry = (Map.Entry) elem;
      elem = Arrays.asList(entry.getKey(), entry.getValue());
    }
    return elem;
  }

  public static Object[] listToObjectArray(Object obj) {
    return ((List)obj).toArray();
  }

  public static boolean inOperator(Object elem, Object collection, boolean isIn, String source, int offset) {
    // Handle List, Map, and String
    if (collection instanceof String) {
      if (!(elem instanceof String)) {
        throw new RuntimeError("Operator '" + (isIn?"in":"!in") + "': Expecting String for left-hand side not " + className(elem), source, offset);
      }
      return ((String)collection).contains((String)elem) == isIn;
    }
    if (collection instanceof List) {
      List list = (List)collection;
      int size = list.size();
      for (int i = 0; i < size; i++) {
        if (isEquals(list.get(i), elem, source, offset)) {
          return isIn;
        }
      }
      return !isIn;
    }
    if (collection instanceof Map) {
      return ((Map)collection).containsKey(elem) == isIn;
    }
    throw new RuntimeError("Operator '" + (isIn?"in":"!in") + "': Expecting String/List/Map for right-hand side not " + className(collection), source, offset);
  }

  public static final String LENGTH = "length";
  public static int length(Object obj, String source, int offset) {
    if (obj instanceof List)      { return ((List)obj).size(); }
    if (obj instanceof Map)       { return ((Map)obj).size();  }
    if (obj instanceof String)    { return ((String)obj).length(); }
    if (obj.getClass().isArray()) { return Array.getLength(obj); }
    throw new RuntimeError("Cannot get array length/list size of object of type " + className(obj), source, offset);
  }

  /////////////////////////////////////

  // Methods for converting object to given type where possible. Conversion may include parsing a
  // String to get a number or converting a List into a Map using the collectEntries funcionality.
  // This conversion is used by the "as" operator which is much more forgiving than a straight cast. The
  // "as" operator tries to convert anything where it makes sense to do so whereas with cast the object
  // must already be the right type (or very close to it - e.g for numbers).

  public static byte asByte(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)              { throw new NullError("Null value cannot be coerced to byte", source, offset, captureStackTrace); }
    if (obj instanceof Number)    { return ((Number)obj).byteValue(); }
    if (!(obj instanceof String)) { throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to byte", source, offset); }
    try {
      return (byte)Integer.parseInt((String)obj);
    }
    catch (NumberFormatException e) { throw new RuntimeError("String value is not a valid number", source, offset, e); }
  }

  public static int asInt(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)              { throw new NullError("Null value cannot be coerced to int", source, offset, captureStackTrace); }
    if (obj instanceof Number)    { return ((Number)obj).intValue(); }
    if (!(obj instanceof String)) { throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to int", source, offset); }
    try {
      return Integer.parseInt((String)obj);
    }
    catch (NumberFormatException e) { throw new RuntimeError("String value is not a valid int", source, offset, e); }
  }

  public static long asLong(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)              { throw new NullError("Null value cannot be coerced to long", source, offset, captureStackTrace); }
    if (obj instanceof Number)    { return ((Number)obj).longValue(); }
    if (!(obj instanceof String)) { throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to long", source, offset); }
    try {
      return Long.parseLong((String)obj);
    }
    catch (NumberFormatException e) { throw new RuntimeError("String value is not a valid long", source, offset, e); }
  }

  public static double asDouble(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)              { throw new NullError("Null value cannot be coerced to double", source, offset, captureStackTrace); }
    if (obj instanceof Number)    { return ((Number)obj).doubleValue(); }
    if (!(obj instanceof String)) { throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to double", source, offset); }
    try {
      return Double.parseDouble((String)obj);
    }
    catch (NumberFormatException e) { throw new RuntimeError("String value is not a valid double", source, offset, e); }
  }

  public static BigDecimal asDecimal(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)               { throw new NullError("Null value cannot be coerced to Decimal", source, offset, captureStackTrace); }
    if (obj instanceof BigDecimal) { return (BigDecimal)obj; }
    if (obj instanceof Number) {
      if (obj instanceof Double) {
        return BigDecimal.valueOf(((Number) obj).doubleValue());
      }
      return BigDecimal.valueOf(((Number)obj).longValue());
    }
    if (!(obj instanceof String)) {
      throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to Decimal", source, offset);
    }
    try {
      return new BigDecimal((String)obj);
    }
    catch (NumberFormatException e) { throw new RuntimeError("String value is not a valid Decimal", source, offset); }
  }

  public static List asList(Object obj, boolean captureStackTrace, String source, int offset) {
    if (obj == null)           { return null; }
    if (obj instanceof List)   { return (List)obj; }
    if (obj instanceof String) { return ((String)obj).chars().mapToObj(c -> String.valueOf((char)c)).collect(Collectors.toList()); }
    if (obj instanceof Map) {
      Map<Object,Object> map = (Map)obj;
      return map.entrySet().stream().map(e -> Utils.listOf(e.getKey(), e.getValue())).collect(Collectors.toList());
    }
    if (obj instanceof Object[]) {
      return Arrays.asList((Object[])obj);
    }
    if (obj instanceof int[]) {
      int[] arr = (int[])obj;
      List list = new ArrayList(arr.length);
      for (int i = 0; i < arr.length; i++) {
        list.add(arr[i]);
      }
      return list;
    }
    if (obj instanceof byte[]) {
      byte[] arr = (byte[])obj;
      List list = new ArrayList(arr.length);
      for (int i = 0; i < arr.length; i++) {
        list.add(arr[i]);
      }
      return list;
    }
    if (obj instanceof long[]) {
      long[] arr = (long[])obj;
      List list = new ArrayList(arr.length);
      for (int i = 0; i < arr.length; i++) {
        list.add(arr[i]);
      }
      return list;
    }
    if (obj instanceof boolean[]) {
      boolean[] arr = (boolean[])obj;
      List list = new ArrayList(arr.length);
      for (int i = 0; i < arr.length; i++) {
        list.add(arr[i]);
      }
      return list;
    }
    if (obj instanceof double[]) {
      double[] arr = (double[])obj;
      List list = new ArrayList(arr.length);
      for (int i = 0; i < arr.length; i++) {
        list.add(arr[i]);
      }
      return list;
    }
    throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to List", source, offset);
  }

  public static Map asMap(Object obj, boolean captureStackTrace, String source, int offset) {
    return doAsMap(obj, source, offset, new HashMap<>());
  }

  private static Map doAsMap(Object obj, String source, int offset, Map<Object,Map> fieldValues) {
    if (obj == null)           { return null; }
    if (obj instanceof Map)    { return (Map)obj; }
    if (obj instanceof List) {
      List list = (List)obj;
      Map result = new LinkedHashMap();  // Utils.JACTL_MAP_TYPE
      list.forEach(elem -> addMapEntry(result, elem, source, offset));
      return result;
    }
    if (obj instanceof JactlObject) {
      Map<String,Object> fieldsAndMethods = ((JactlObject)obj)._$j$getFieldsAndMethods();
      Map result = new LinkedHashMap();   // Utils.JACTL_MAP_TYPE
      fieldValues.put(obj,result);        // We use this to detect circular references
      fieldsAndMethods.entrySet().stream().filter(entry -> entry.getValue() instanceof Field).forEach(entry -> {
        String field = entry.getKey();
        try {
          Object value = ((Field) entry.getValue()).get(obj);
          if (value instanceof JactlObject) {
            // Check we don't already have a value for this object due to circular reference somewhere
            value = fieldValues.get(value);
            if (value == null) {
              value = doAsMap(value, source, offset, fieldValues);
            }
          }
          result.put(field, value);
        }
        catch (IllegalAccessException e) {
          throw new IllegalStateException("Internal error: problem accessing field '" + field + "': " + e, e);
        }
      });
      return result;
    }
    throw new RuntimeError("Cannot coerce object of type " + className(obj) + " to Map", source, offset);
  }

  public static void addMapEntry(Map mapObj, Object elem, String source, int offset) {
    Map map = mapObj;
    Object key;
    Object value;
    int length = 0;
    if (elem instanceof Object[]) {
      Object[] keyVal = (Object[]) elem;
      length = keyVal.length;
      key    = length == 2 ? keyVal[0] : null;
      value  = length == 2 ? keyVal[1] : null;
    }
    else
    if (elem instanceof List) {
      List list = (List) elem;
      length = list.size();
      key    = length == 2 ? list.get(0) : null;
      value  = length == 2 ? list.get(1) : null;
    }
    else {
      throw new RuntimeError("Expected Map entry [key,value] but got " + className(elem), source, offset);
    }
    if (length != 2) {
      throw new RuntimeError("Expected list with [key,value] but got list of " + length + (length == 1 ? " entry" : " entries"), source, offset);
    }
    map.put(key, value);
  }

  public static Map copyArg0AsMap(Object[] args) {
    return new LinkedHashMap((Map)args[0]);
  }

  public static Map copyNamedArgs(Object arg) {
    return new NamedArgsMapCopy((Map)arg);
  }

  public static final String REMOVE_OR_THROW = "removeOrThrow";
  public static Object removeOrThrow(Map map, String key, boolean isInitMethod, String source, int offset) {
    if (map.containsKey(key)) {
      return map.remove(key);
    }
    throw new RuntimeError("Missing value for mandatory " + (isInitMethod ? "field" : "parameter") + " '" + key + "'", source, offset);
  }

  public static boolean checkForExtraArgs(Map<String,Object> map, boolean isInitMethod, String source, int offset) {
    if (!map.isEmpty()) {
      String names = map.keySet().stream().collect(Collectors.joining(", "));
      throw new RuntimeError("No such " + (isInitMethod ? "field" : "parameter") + (map.size() > 1 ? "s":"") + ": " + names, source, offset);
    }
    return true;
  }

  public static String DEBUG_BREAK_POINT = "debugBreakPoint";
  public static Object debugBreakPoint(Object data, String info) {
    System.out.println("DEBUG: " + info + ": " + data);
    return data;
  }

  public static Object evalScript(Continuation c, String code, Map bindings, ClassLoader classLoader) {
    if (c != null) {
      return c.getResult();
    }
    try {
      bindings = bindings == null ? new LinkedHashMap() : bindings;
      Function<Map<String, Object>, Object> script = compileScript(code, bindings, RuntimeState.getState().getContext());
      Object                                result = script.apply(bindings);
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, eval$cHandle, 0, null, null);
    }
    catch (JactlError e) {
      if (bindings != null) {
        bindings.put(Utils.EVAL_ERROR, e.toString());
      }
      return null;
    }
  }
  public static JactlMethodHandle eval$cHandle = RuntimeUtils.lookupMethod(RuntimeUtils.class, "eval$c", Object.class, Continuation.class);
  public static Object eval$c(Continuation c) {
    return evalScript(c, null, null, null);
  }

  private static Function<Map<String,Object>,Object> compileScript(String code, Map bindings, JactlContext context) {
    return context.getEvalScript(code, bindings);
  }

  public static boolean die(String msg, String source, int offset) {
    throw new DieError(msg, source, offset);
  }

  private static final int MAX_COUNTER = 1 << 23;
  /**
   * Generate a random UUID.
   * <p>We use a 13 byte random number and a 3 byte counter and refresh the random 13 bytes whenever
   *  the counter is getting close to overflowing. This way we wear the expense of the randomness
   *  infrequently rather than for every UUID and we also minimise the use of entropy which might be
   *  in short supply.</p>
   * <p>NOTE: this is not a secure random UUID since we use an incrementing count for part of it.</p>
   * @return the type 4 UUID
   */
  public static UUID randomUUID() {
    int counter = uuidCounter.incrementAndGet();
    if (counter > MAX_COUNTER) {
      synchronized (secureRandom) {
        counter = uuidCounter.incrementAndGet();
        if (counter > MAX_COUNTER) {
          refreshUUIDValues();
          uuidCounter.set(0);
          counter = 0;
        }
      }
    }
    return new UUID(uuidMsb.get(), uuidLsb.get() | counter);
  }

  private static void refreshUUIDValues() {
    byte[] randomBytes = new byte[13];
    secureRandom.nextBytes(randomBytes);
    // Set version information
    randomBytes[6] &= 0x0f;
    randomBytes[6] |= 0x40;
    randomBytes[8] &= 0x3f;
    randomBytes[8] |= 0x80;

    long uuidMsbVal = 0;
    long uuidLsbVal = 0;
    int i = 0;
    for (; i < 8; i++) {
      uuidMsbVal = (uuidMsbVal << 8) | (randomBytes[i] & 0xff);
    }
    for (; i < 13; i++) {
      uuidLsbVal = (uuidLsbVal << 8) | (randomBytes[i] & 0xff);
    }
    uuidLsbVal <<= 24;
    uuidMsb.set(uuidMsbVal);
    uuidLsb.set(uuidLsbVal);
  }

  public static final String ERROR_WITH_MSG = "errorWithMsg";
  public static RuntimeError errorWithMsg(String msg, String prefix, String source, int offset) {
    return new RuntimeError(prefix + (msg == null ? "" : ": " + msg), source, offset);
  }

  private static String repeat(String str, int repeat, String source, int offset) {
    if (repeat < 0) {
      throw new RuntimeError("String repeat count cannot be negative (was " + repeat + ")", source, offset);
    }
    return Utils.repeat(str, repeat);
  }

  public static final String LOOKUP_METHOD_HANDLE = "lookupMethodHandle";
  public static JactlMethodHandle lookupMethodHandle(String name) {
    return RuntimeState.getState().getContext().getFunctions().lookupMethodHandle(name);
  }
}
