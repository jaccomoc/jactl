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

import org.objectweb.asm.Type;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static jacsal.TokenType.*;

public enum JacsalType {

  // NOTE: these names must match the corresponding token type names so that
  //       we can use JacsalType.valueOf(tokenType) to get a JacsalType from
  //       a TokenType. Only exception is for INSTANCE and ANY since INSTANCE
  //       doesn't have a corresponding token and ANY which is roughly equivalent
  //       to DEF.
  BOOLEAN,
  INT,
  LONG,
  DOUBLE,
  DECIMAL,
  STRING,
  INSTANCE,
  ANY;

  public boolean isNumeric() {
    switch (this) {
      case INT:
      case LONG:
      case DOUBLE:
      case DECIMAL:
        return true;
      default:
        return false;
    }
  }

  public boolean isIntegral() {
    return this == INT || this == LONG;
  }

  public boolean isPrimitive() {
    switch (this) {
      case BOOLEAN:
      case INT:
      case LONG:
      case DOUBLE:
        return true;
      default:
        return false;
    }
  }

  public boolean is(JacsalType... types) {
    for (JacsalType type: types) {
      if (this == type) {
        return true;
      }
    }
    return false;
  }

  ///////////////////////////////////////

  private static class TypePair {
    JacsalType t1; JacsalType t2;
    TypePair(JacsalType t1, JacsalType t2) { this.t1 = t1; this.t2 = t2; }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof TypePair) {
        // Order is unimportant
        return ((TypePair)obj).t1 == t1 && ((TypePair)obj).t2 == t2 ||
               ((TypePair)obj).t1 == t2 && ((TypePair)obj).t2 == t1;
      }
      return false;
    }

    @Override
    public int hashCode() {
      // Need to make sure that hashcode is not dependent on order of t1, t2
      return t1.hashCode() + t2.hashCode();
    }
  }

  private static       List                     resultTypes = List.of(
    new TypePair(INT, LONG),       LONG,
    new TypePair(INT, DOUBLE),     DOUBLE,
    new TypePair(INT, DECIMAL),    DECIMAL,
    new TypePair(INT, STRING),     STRING,
    new TypePair(INT, ANY),        ANY,

    new TypePair(DOUBLE, LONG),    DOUBLE,
    new TypePair(DOUBLE, DECIMAL), DECIMAL,
    new TypePair(DOUBLE, STRING),  STRING,
    new TypePair(DOUBLE, ANY),     ANY,

    new TypePair(DECIMAL, LONG),    DECIMAL,
    new TypePair(DECIMAL, STRING),  STRING,
    new TypePair(DECIMAL, ANY),     ANY,

    new TypePair(STRING, BOOLEAN), STRING,
    new TypePair(STRING, LONG),    STRING,
    new TypePair(STRING, ANY),     ANY
  );
  private static final Map<TypePair,JacsalType> resultMap   = new HashMap<>();
  static {
    for (Iterator it = resultTypes.iterator(); it.hasNext(); ) {
      resultMap.put((TypePair)it.next(), (JacsalType)it.next());
    }
  }

  /**
   * In an expression with two operands determine the resulting type of the expression.
   * @param type1  type of operand1
   * @param type2  type of operand2
   * @return resulting type
   */
  public static JacsalType result(JacsalType type1, Token operator, JacsalType type2) {
    if (type1.is(ANY)) {
      return ANY;
    }
    if (operator.is(PLUS) && type1.is(STRING)) {
      return STRING;
    }
    if (operator.is(STAR) && type1 == STRING && type2.is(INT,LONG,ANY)) {
      return STRING;
    }
    if (operator.is(EQUAL)) {
      if (!type2.isConvertibleTo(type1)) {
        throw new CompileError("Right hand operand of type " + type2 + " cannot be converted to " + type1, operator);
      }
      return type1;
    }

    if (operator.getType().isBooleanOperator()) {
      return BOOLEAN;
    }

    // TBD: Check for bitwise operations which should result in int/long

    // Must be numeric operation
    checkIsNumeric(type1, "left", operator);
    checkIsNumeric(type2, "right", operator);
    if (type1 == type2)               { return type1; }
    if (type2 == ANY)                 { return ANY; }

    JacsalType result = resultMap.get(new TypePair(type1, type2));
    if (result == null) {
      throw new CompileError("Arguments of type " + type1 + " and " + type2 + " not supported by operator " + operator, operator);
    }
    return result;
  }

  /**
   * Check if type is compatible and can be converted to given type
   * @param type  the type to be converted to
   * @return true if convertible
   */
  public boolean isConvertibleTo(JacsalType type) {
    if (is(type))                                   { return true; }
    if (type.is(ANY))                               { return true; }
    if (is(INT)  && type.is(LONG,DOUBLE,DECIMAL))   { return true; }
    if (is(LONG) && type.is(DOUBLE,DECIMAL))        { return true; }
    if (is(DOUBLE,DECIMAL) && type.isNumeric())     { return true; }
    return false;
  }

  public String descriptor() {
    switch (this) {
      case BOOLEAN:        return Type.getDescriptor(Boolean.TYPE);
      case INT:            return Type.getDescriptor(Integer.TYPE);
      case LONG:           return Type.getDescriptor(Long.TYPE);
      case DOUBLE:         return Type.getDescriptor(Double.TYPE);
      case DECIMAL:        return Type.getDescriptor(BigDecimal.class);
      case STRING:         return Type.getDescriptor(String.class);
      case INSTANCE:       throw new UnsupportedOperationException();
      case ANY:            return Type.getDescriptor(Object.class);
      default:             throw new UnsupportedOperationException();
    }
  }

  private static void checkIsNumeric(JacsalType type, String leftOrRight, Token operator) {
    if (!type.isNumeric() && !type.is(ANY)) {
      throw new CompileError("Non-numeric operand for " + leftOrRight + "-hand side of '" + operator.getStringValue() + "': was " + type, operator);
    }
  }
}
