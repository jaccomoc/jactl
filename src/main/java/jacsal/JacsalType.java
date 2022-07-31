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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static jacsal.TokenType.PLUS;
import static jacsal.TokenType.STAR;

public enum JacsalType {

  BOOLEAN,
  INT,
  LONG,
  DOUBLE,
  DECIMAL,
  STRING,
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
    new TypePair(INT, BOOLEAN),    INT,
    new TypePair(INT, LONG),       LONG,
    new TypePair(INT, DOUBLE),     DOUBLE,
    new TypePair(INT, DECIMAL),    DECIMAL,
    new TypePair(INT, STRING),     STRING,
    new TypePair(INT, ANY),        ANY,

    new TypePair(DOUBLE, BOOLEAN), DOUBLE,
    new TypePair(DOUBLE, LONG),    DOUBLE,
    new TypePair(DOUBLE, DECIMAL), DECIMAL,
    new TypePair(DOUBLE, STRING),  STRING,
    new TypePair(DOUBLE, ANY),     ANY,

    new TypePair(DECIMAL, BOOLEAN), DECIMAL,
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
    if (operator.is(PLUS) && (type1 == STRING || type2 == STRING)) {
      return STRING;
    }
    if (operator.is(STAR) && type1 == STRING && type2.is(INT,LONG,ANY)) {
      return STRING;
    }
    if (operator.getType().isNumericOperator()) {
      if (!type1.isNumeric()) { throw new CompileError("Non-numeric operand for left-hand side of '" + operator.getStringValue() + "'", operator); }
      if (!type2.isNumeric()) { throw new CompileError("Non-numeric operand for right-hand side of '" + operator.getStringValue() + "'", operator); }
      if (type1 == BOOLEAN && type2 == BOOLEAN) {
        return INT;
      }
    }
    if (operator.getType().isBooleanOperator()) {
      return BOOLEAN;
    }
    if (type1 == type2)               { return type1; }
    if (type1 == ANY || type2 == ANY) { return ANY; }
    return resultMap.get(new TypePair(type1, type2));
  }
}
