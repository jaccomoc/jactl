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

import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static jacsal.TokenType.*;

public class JacsalType {

  enum TypeEnum {
    BOOLEAN,
    INT,
    LONG,
    DOUBLE,
    DECIMAL,
    STRING,
    MAP,
    LIST,
    INSTANCE,
    ANY,
    FUNCTION
  }

  public static JacsalType BOOLEAN       = createPrimitive(TypeEnum.BOOLEAN);
  public static JacsalType BOXED_BOOLEAN = createBoxedType(TypeEnum.BOOLEAN);
  public static JacsalType INT           = createPrimitive(TypeEnum.INT);
  public static JacsalType BOXED_INT     = createBoxedType(TypeEnum.INT);
  public static JacsalType LONG          = createPrimitive(TypeEnum.LONG);
  public static JacsalType BOXED_LONG    = createBoxedType(TypeEnum.LONG);
  public static JacsalType DOUBLE        = createPrimitive(TypeEnum.DOUBLE);
  public static JacsalType BOXED_DOUBLE  = createBoxedType(TypeEnum.DOUBLE);
  public static JacsalType DECIMAL       = createRefType(TypeEnum.DECIMAL);
  public static JacsalType STRING        = createRefType(TypeEnum.STRING);
  public static JacsalType MAP           = createRefType(TypeEnum.MAP);
  public static JacsalType LIST          = createRefType(TypeEnum.LIST);
  public static JacsalType ANY           = createRefType(TypeEnum.ANY);
  public static JacsalType FUNCTION      = createRefType(TypeEnum.FUNCTION);

  private TypeEnum type;
  private boolean  boxed;
  private boolean  isRef;

  private JacsalType(TypeEnum type, boolean boxed, boolean isRef) {
    this.type = type;
    this.boxed = boxed;
    this.isRef = isRef;
  }

  private static JacsalType createPrimitive(TypeEnum type) {
    return new JacsalType(type, false, false);
  }

  private static JacsalType createBoxedType(TypeEnum type) {
    return new JacsalType(type, true, true);
  }

  private static JacsalType createRefType(TypeEnum type) {
    return new JacsalType(type, false, true);
  }

  public TypeEnum getType() {
    return type;
  }

  /**
   * Return true if type is a boxed primitive type
   * @return
   */
  public boolean isBoxed() {
    return boxed;
  }

  public static JacsalType valueOf(TokenType tokenType) {
    switch (tokenType) {
      case BOOLEAN:   return BOOLEAN;
      case INT:       return INT;
      case LONG:      return LONG;
      case DOUBLE:    return DOUBLE;
      case DECIMAL:   return DECIMAL;
      case STRING:    return STRING;
      case MAP:       return MAP;
      case LIST:      return LIST;
      case DEF:       return ANY;
      default:  throw new IllegalStateException("Internal error: unexpected token " + tokenType);
    }
  }

  public boolean isNumeric() {
    switch (this.type) {
      case INT:
      case LONG:
      case DOUBLE:
      case DECIMAL:
        return true;
      default:
        return false;
    }
  }

  public boolean isPrimitive() {
    return !isRef;
  }

  public JacsalType boxed() {
    switch (getType()) {
      case BOOLEAN:   return BOXED_BOOLEAN;
      case INT:       return BOXED_INT;
      case LONG:      return BOXED_LONG;
      case DOUBLE:    return BOXED_DOUBLE;
      default: return this;
    }
  }

  public JacsalType unboxed() {
    switch (getType()) {
      case BOOLEAN:   return BOOLEAN;
      case INT:       return INT;
      case LONG:      return LONG;
      case DOUBLE:    return DOUBLE;
      default: return this;
    }
  }

  /**
   * Check if type is one of the supplied types
   * @param types array of types
   * @return true if type is one of the types
   */
  public boolean is(JacsalType... types) {
    for (JacsalType type: types) {
      if (this == type) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if type is one of the supplied types ignoring whether
   * any type is boxed (allows quick check for INT, LONG, etc
   * without having to worry about checking whether either type
   * is boxed).
   */
  public boolean isBoxedOrUnboxed(JacsalType... types) {
    for (JacsalType type: types) {
      if (this == type || this == type.boxed() || this.boxed() == type) {
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

  private static List resultTypes = List.of(
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
    // Boolean comparisons
    if (operator.is(EQUAL_EQUAL,BANG_EQUAL,AMPERSAND_AMPERSAND,PIPE_PIPE)) { return BOOLEAN; }
    if (operator.getType().isBooleanOperator()) {
      if (type1.is(ANY) || type2.is(ANY))                  { return BOOLEAN; }
      if (type1.isNumeric() && type2.isNumeric())          { return BOOLEAN; }
      if (type1.is(BOOLEAN,STRING) && type1.equals(type2)) { return BOOLEAN; }
      throw new CompileError("Type " + type1 + " cannot be compared to " + type2, operator);
    }

    if (type1.is(ANY))                               { return ANY;       }
    if (operator.is(PLUS) && type1.is(STRING))       { return STRING;    }

    if (operator.is(STAR) && type1.is(STRING) &&
        type2.is(INT,BOXED_INT,LONG,BOXED_LONG,ANY)) { return STRING;    }

    if (operator.is(LEFT_SQUARE,QUESTION_SQUARE) &&
        type1.is(STRING))                            { return STRING;    }

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
    if (type1.isBoxedOrUnboxed(type2)) { return type1.unboxed(); }
    if (type2 == ANY)                  { return ANY; }

    JacsalType result = resultMap.get(new TypePair(type1.unboxed(), type2.unboxed()));
    if (result == null) {
      throw new CompileError("Arguments of type " + type1 + " and " + type2 + " not supported by operator '" + operator.getChars() + "'", operator);
    }
    return result;
  }

  /**
   * Check if type is compatible and can be converted to given type
   * @param type  the type to be converted to
   * @return true if convertible
   */
  public boolean isConvertibleTo(JacsalType type) {
    if (isBoxedOrUnboxed(type))              { return true; }
    if (is(ANY) || type.is(ANY))             { return true; }
    if (isNumeric() && type.isNumeric())     { return true; }
    if (type.is(STRING) && !is(FUNCTION))    { return true; }
    return false;
  }

  public String descriptor() {
    switch (this.type) {
      case BOOLEAN:        return Type.getDescriptor(isBoxed() ? Boolean.class : Boolean.TYPE);
      case INT:            return Type.getDescriptor(isBoxed() ? Integer.class : Integer.TYPE);
      case LONG:           return Type.getDescriptor(isBoxed() ? Long.class : Long.TYPE);
      case DOUBLE:         return Type.getDescriptor(isBoxed() ? Double.class : Double.TYPE);
      case DECIMAL:        return Type.getDescriptor(BigDecimal.class);
      case STRING:         return Type.getDescriptor(String.class);
      case MAP:            return Type.getDescriptor(Map.class);
      case LIST:           return Type.getDescriptor(List.class);
      case INSTANCE:       throw new UnsupportedOperationException();
      case ANY:            return Type.getDescriptor(Object.class);
      case FUNCTION:       return Type.getDescriptor(MethodHandle.class);
      default:             throw new UnsupportedOperationException();
    }
  }

  public Type descriptorType() {
    switch (this.type) {
      case BOOLEAN:        return Type.getType(isBoxed() ? Boolean.class : Boolean.TYPE);
      case INT:            return Type.getType(isBoxed() ? Integer.class : Integer.TYPE);
      case LONG:           return Type.getType(isBoxed() ? Long.class : Long.TYPE);
      case DOUBLE:         return Type.getType(isBoxed() ? Double.class : Double.TYPE);
      case DECIMAL:        return Type.getType(BigDecimal.class);
      case STRING:         return Type.getType(String.class);
      case MAP:            return Type.getType(Map.class);
      case LIST:           return Type.getType(List.class);
      case INSTANCE:       throw new UnsupportedOperationException();
      case ANY:            return Type.getType(Object.class);
      case FUNCTION:       return Type.getType(MethodHandle.class);
      default:             throw new UnsupportedOperationException();
    }
  }

  public String getBoxedClass() {
    switch (this.type) {
      case BOOLEAN:  return Type.getInternalName(Boolean.class);
      case INT:      return Type.getInternalName(Integer.class);
      case LONG:     return Type.getInternalName(Long.class);
      case DOUBLE:   return Type.getInternalName(Double.class);
      case DECIMAL:  return Type.getInternalName(BigDecimal.class);
      case STRING:   return Type.getInternalName(String.class);
      case MAP:      return Type.getInternalName(Map.class);
      case LIST:     return Type.getInternalName(List.class);
      case INSTANCE: throw new UnsupportedOperationException();
      case ANY:      return Type.getInternalName(Object.class);
      case FUNCTION: return Type.getInternalName(MethodHandle.class);
      default:
        throw new IllegalStateException("Unexpected value: " + this);
    }
  }

  private static void checkIsNumeric(JacsalType type, String leftOrRight, Token operator) {
    if (!type.isNumeric() && !type.is(ANY)) {
      throw new CompileError("Non-numeric operand for " + leftOrRight + "-hand side of '" + operator.getChars() + "': was " + type, operator);
    }
  }

  public String typeName() {
    switch (type) {
      case BOOLEAN:    return isBoxed() ? "Boolean" : "boolean";
      case INT:        return isBoxed() ? "Integer" : "int";
      case LONG:       return isBoxed() ? "Long" : "long";
      case DOUBLE:     return isBoxed() ? "Double" : "double";
      case DECIMAL:    return "Decimal";
      case STRING:     return "String";
      case MAP:        return "Map";
      case LIST:       return "List";
      case INSTANCE:   return "Instance<>";
      case ANY:        return "def";
      case FUNCTION:   return "Function";
    }
    throw new IllegalStateException("Internal error: unexpected type " + type);
  }

  @Override public String toString() {
    return typeName();
  }
}
