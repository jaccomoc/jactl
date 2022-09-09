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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class RuntimeUtils {

  public static final String PLUS_PLUS     = "++";
  public static final String MINUS_MINUS   = "--";
  public static final String PLUS          = "+";
  public static final String MINUS         = "-";
  public static final String STAR          = "*";
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
      throw new NullError("Left-hand side of '" + operator + "' cannot be null", source, offset);
    }
    if (left == DEFAULT_VALUE && right instanceof String && operator == PLUS) {
      // Special case to support: x.a += 'some string'
      // If x.a doesn't yet exist we create with default of empty string in this context.
      left = "";
    }

    if (left instanceof String) {
      // Make sure we are not trying to inc/dec a string since -- or ++ on an unknown type
      // will be turned into equivalent of x = x + 1 (for example) and it won't be until we
      // get here that we can check if x is a string or not.
      if (originalOperator == PLUS_PLUS || originalOperator == MINUS_MINUS) {
        throw new RuntimeError("String cannot be " + (originalOperator == PLUS_PLUS ? "incremented" : "decremented"), source, offset);
      }
      if (operator == PLUS) {
        return ((String) left).concat(right == null ? "null" : toString(right));
      }
      if (operator == STAR) {
        if (right instanceof Number) {
          return ((String)left).repeat(((Number)right).intValue());
        }
        throw new RuntimeError("Right-hand side of string repeat operator must be numeric but found " + className(right), source, offset);
      }
    }

    // All other operations expect numbers so check we have numbers
    if (!(left instanceof Number))  { throw new RuntimeError("Non-numeric operand for left-hand side of '" + operator + "': was " + className(left), source, offset); }
    if (!(right instanceof Number)) { throw new RuntimeError("Non-numeric operand for right-hand side of '" + operator + "': was " + className(right), source, offset); }

    // Check for bitwise operations since we don't want to unnecessarily convert to double/BigDecimal...
    // TBD

    // Must be numeric so convert to appropriate type and perform operation
    if (left instanceof BigDecimal || right instanceof BigDecimal) {
      return decimalBinaryOperation(toBigDecimal(left), toBigDecimal(right), operator, maxScale, source, offset);
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

        case PERCENT:
        case SLASH:
          try {
            return operator == PERCENT ? lhs % rhs : lhs / rhs;
          }
          catch (ArithmeticException e) {
          throw new RuntimeError("Divide by zero", source, offset);
          }
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

      case PERCENT:
      case SLASH:
        try {
          return operator == PERCENT ?  lhs % rhs : lhs / rhs;
        }
        catch (ArithmeticException e) {
          throw new RuntimeError("Divide by zero", source, offset);
        }
      default: throw new IllegalStateException("Internal error: unknown operator " + operator);
    }
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
    if (operator == INSTANCE_OF || operator == BANG_INSTANCE_OF) {
      throw new UnsupportedOperationException();
    }

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
    else if (left instanceof String && right instanceof String) {
      comparison = ((String)left).compareTo((String)right);
    }
    else if (operator == EQUAL_EQUAL || operator == BANG_EQUAL) {
      return (operator == EQUAL_EQUAL) == (left == right);
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

  public static Object incNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).add(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj + 1; }
    if (obj instanceof Long)       { return (long)obj + 1; }
    if (obj instanceof Integer)    { return (int)obj + 1; }
    throw new RuntimeError("Type " + className(obj) + " cannot be incremented", source, offset);
  }

  public static Object decNumber(Object obj, String source, int offset) {
    ensureNonNull(obj, source, offset);
    if (obj instanceof BigDecimal) { return ((BigDecimal)obj).subtract(BigDecimal.ONE); }
    if (obj instanceof Double)     { return (double)obj - 1; }
    if (obj instanceof Long)       { return (long)obj - 1; }
    if (obj instanceof Integer)    { return (int)obj - 1; }
    throw new RuntimeError("Type " + className(obj) + " cannot be decremented", source, offset);
  }

  public static String stringRepeat(String str, int count, String source, int offset) {
    if (count < 0) {
      throw new RuntimeError("String repeat count must be >= 0", source, offset);
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
      return null;
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
      return value;
    }

    if (!(parent instanceof List) && !(parent instanceof String)) {
      throw new RuntimeError("Invalid object type (" + className(parent) + "): expected Map/List" +
                             (isDot ? "" : " or String"), source, offset);
    }

    // Check that we are doing a list operation
    if (isDot) {
      throw new RuntimeError("Field access not supported for " +
                             (parent instanceof List ? "List" : "String") +
                             " object", source, offset);
    }

    // Must be a List
    if (!(field instanceof Number)) {
      throw new RuntimeError("Non-numeric value for indexed access", source, offset);
    }

    int index = ((Number)field).intValue();
    if (index < 0) {
      throw new RuntimeError("Index must be >= 0 (was " + index + ")", source, offset);
    }

    if (parent instanceof String) {
      String str = (String)parent;
      if (index >= str.length()) {
        throw new RuntimeError("Index (" + index + ") too large for String (length=" + str.length() + ")", source, offset);
      }
      return Character.toString(str.charAt(index));
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
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof String) {
      return (String)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for String", source, offset);
    }
    throw new RuntimeError("Cannot convert object of type " + className(obj) + " to String", source, offset);
  }

  public static Map castToMap(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof Map) {
      return (Map)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Map", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Map", source, offset);
  }

  public static List castToList(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof List) {
      return (List)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for List", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to List", source, offset);
  }

  public static Number castToNumber(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof Number) {
      return (Number)obj;
    }
    if (obj == null) {
      throw new NullError("Cannot convert null value to Number", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Number", source, offset);
  }

  public static BigDecimal castToDecimal(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof Number) {
      return toBigDecimal(obj);
    }
    if (obj == null) {
      throw new NullError("Null value for Decimal", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Decimal", source, offset);
  }

  public static MethodHandle castToFunction(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof MethodHandle) {
      return (MethodHandle)obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Function", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Function", source, offset);
  }

  public static Object[] castToObjectArr(Object obj, String source, int offset) {
    if (obj instanceof HeapLocal) {
      obj = ((HeapLocal)obj).getValue();
    }

    if (obj instanceof Object[]) {
      return (Object[])obj;
    }
    if (obj == null) {
      throw new NullError("Null value for Object[]", source, offset);
    }
    throw new RuntimeError("Object of type " + className(obj) + " cannot be cast to Object[]", source, offset);
  }

  public static void print(Object obj) {
    if (obj == null) { obj = "null"; }
    System.out.print(obj);
  }

  public static void println(Object obj) {
    if (obj == null) { obj = "null"; }
    System.out.println(obj);
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
    if (obj instanceof Map)        { return "Map"; }
    if (obj instanceof List)       { return "List"; }
    return obj.getClass().getName();
  }

  private static void ensureNonNull(Object obj, String source, int offset) {
    if (obj == null) {
      throw new NullError("Null value encountered where null not allowed", source, offset);
    }
  }

  public static List<String> lines(String str) {
    List<String> lines = new ArrayList<>();
    int offset = 0;
    int lastOffset = 0;
    while ((offset = str.indexOf('\n', lastOffset)) != -1) {
      lines.add(str.substring(lastOffset, offset));
      lastOffset = offset + 1;  // skip new line
    }
    lines.add(str.substring(lastOffset,str.length()));
    return lines;
  }
}
