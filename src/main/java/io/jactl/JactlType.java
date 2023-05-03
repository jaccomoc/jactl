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

package io.jactl;

import io.jactl.runtime.*;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JactlType objects are used to represent different types at compile time. They should not be used
 * at runtime.
 * <p>Types can be compared using is(type,...) which returns true if the type matches any of the supplied
 * types. Types are checked by using the static constant fields BOOLEAN, INT, LONG, etc rather than
 * referring to the enum type.</p>
 * <p>Note that types should not be compared using '==' even though most of the time this will work since
 * for INSTANCE/CLASS types we create new JactlType objects each time. There is also a DelegatingJactlType
 * class which wraps a JactlType and delegates calls to it which means that '==' would fail for these objects
 * as well.</p>
 * <p>For variables/fields defined as 'var' fields we don't know their type up front but need to infer the
 * type from their initialiser expression. For these situations we create a new DelegatingJactlType object of
 * type UNKNOWN. The object has an embedded Expr and will delegate to the Expr.type object once known. This
 * Expr.type starts off as null but once the Resolver has resolved types and variables it will have an actual
 * type that the DelegatingJactlType will start delegating to.</p>
 */
public class JactlType {

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
    CLASS,
    ANY,
    FUNCTION,
    ARRAY,

    // internal use only
    HEAPLOCAL,
    OBJECT_ARR,
    LONG_ARR,
    STRING_ARR,
    ITERATOR,
    NUMBER,
    MATCHER,
    CONTINUATION,
    UNKNOWN        // Used as placeholder for "var" until we know type of initialiser for a variable
  }

  public static JactlType BOOLEAN       = createPrimitive(TypeEnum.BOOLEAN);
  public static JactlType BOXED_BOOLEAN = createBoxedType(TypeEnum.BOOLEAN);
  public static JactlType INT           = createPrimitive(TypeEnum.INT);
  public static JactlType BOXED_INT     = createBoxedType(TypeEnum.INT);
  public static JactlType LONG          = createPrimitive(TypeEnum.LONG);
  public static JactlType BOXED_LONG    = createBoxedType(TypeEnum.LONG);
  public static JactlType DOUBLE        = createPrimitive(TypeEnum.DOUBLE);
  public static JactlType BOXED_DOUBLE  = createBoxedType(TypeEnum.DOUBLE);
  public static JactlType DECIMAL       = createRefType(TypeEnum.DECIMAL);
  public static JactlType STRING        = createRefType(TypeEnum.STRING);
  public static JactlType MAP           = createRefType(TypeEnum.MAP);
  public static JactlType LIST          = createRefType(TypeEnum.LIST);
  public static JactlType ARRAY         = createRefType(TypeEnum.ARRAY);
  public static JactlType ANY           = createRefType(TypeEnum.ANY);
  public static JactlType FUNCTION      = createRefType(TypeEnum.FUNCTION);

  public static JactlType HEAPLOCAL    = createRefType(TypeEnum.HEAPLOCAL);
  public static JactlType OBJECT_ARR   = createRefType(TypeEnum.OBJECT_ARR);
  public static JactlType LONG_ARR     = createRefType(TypeEnum.LONG_ARR);
  public static JactlType STRING_ARR   = createRefType(TypeEnum.STRING_ARR);
  public static JactlType ITERATOR     = createRefType(TypeEnum.ITERATOR);
  public static JactlType NUMBER       = createRefType(TypeEnum.NUMBER);
  public static JactlType MATCHER      = createRefType(TypeEnum.MATCHER);
  public static JactlType CONTINUATION = createRefType(TypeEnum.CONTINUATION);
  public static JactlType INSTANCE     = createInstanceType(ClassDescriptor.getJactlObjectDescriptor());
  public static JactlType CLASS        = createRefType(TypeEnum.CLASS);
  public static JactlType UNKNOWN      = createRefType(TypeEnum.UNKNOWN);

  private TypeEnum type;
  private boolean  boxed;
  private boolean  isRef;

  // Used for INSTANCE and CLASS types
  private String          internalName    = null;
  private ClassDescriptor classDescriptor = null;
  private List<Expr>      className       = null;  // Unresolved className which resolves to classDescriptor

  // For array types
  private JactlType       arrayType       = null;

  protected JactlType() {}

  private JactlType(TypeEnum type, boolean boxed, boolean isRef) {
    this.type = type;
    this.boxed = boxed;
    this.isRef = isRef;
  }

  private static JactlType createPrimitive(TypeEnum type) {
    return new JactlType(type, false, false);
  }
  private static JactlType createBoxedType(TypeEnum type) {
    return new JactlType(type, true, true);
  }
  private static JactlType createRefType(TypeEnum type) {
    return new JactlType(type, false, true);
  }

  public static JactlType arrayOf(JactlType type) {
    var arrType = createRefType(TypeEnum.ARRAY);
    arrType.arrayType = type;
    return arrType;
  }

  /**
   * Create instance type from class type
   * @param clss  the Java class representing the instance type
   * @return a mew JactlType of type INSTANCE
   */
  public static JactlType createInstanceType(Class clss) {
    JactlType type   = createRefType(TypeEnum.INSTANCE);
    type.internalName = Type.getInternalName(clss);
    return type;
  }

  /**
   * Create instance type from class type
   * @param clss  the ClassDescriptor for the class
   * @return a mew JactlType of type INSTANCE
   */
  public static JactlType createInstanceType(ClassDescriptor clss) {
    JactlType type      = createRefType(TypeEnum.INSTANCE);
    type.classDescriptor = clss;
    type.internalName    = type.classDescriptor.getInternalName();
    return type;
  }

  public static JactlType createInstanceType(List<Expr> className) {
    JactlType type = createRefType(TypeEnum.INSTANCE);
    type.className  = className;
    return type;
  }

  public static JactlType createClass(List<Expr> className) {
    JactlType classType = createInstanceType(className);
    classType.type = TypeEnum.CLASS;
    return classType;
  }

  // Create instance type from class type
  public JactlType createInstanceType() {
    if (!is(CLASS)) {
      throw new IllegalStateException("Internal error: unexpected type " + this);
    }
    return createInstanceType(getClassDescriptor());
  }

  public static JactlType createClass(ClassDescriptor descriptor) {
    var classType = createInstanceType(descriptor);
    classType.type = TypeEnum.CLASS;
    return classType;
  }

  public static JactlType createUnknown() {
    return new DelegatingJactlType(UNKNOWN);
  }

  public void typeDependsOn(Expr expr) {
    throw new IllegalStateException("Internal error: typeDependsOn() should only be invoke on UNKNOWN types not " + this);
  }

  public TypeEnum getType() {
    return type;
  }

  /**
   * Return true if type is a boxed primitive type
   * @return true if boxed primitive
   */
  public boolean isBoxed() {
    return boxed;
  }

  /**
   * Create a JactlType from a TokenType
   * @param tokenType  the TokenType
   * @return the static object for the corresponding JactlType
   */
  public static JactlType valueOf(TokenType tokenType) {
    switch (tokenType) {
      case BOOLEAN:    return BOOLEAN;
      case INT:        return INT;
      case LONG:       return LONG;
      case DOUBLE:     return DOUBLE;
      case DECIMAL:    return DECIMAL;
      case STRING:     return STRING;
      case MAP:        return MAP;
      case LIST:       return LIST;
      case DEF:        return ANY;
      case OBJECT:     return ANY;
      case OBJECT_ARR: return OBJECT_ARR;
      case LONG_ARR:   return LONG_ARR;
      case STRING_ARR: return STRING_ARR;
      case VAR:        return createUnknown();
      default:  throw new IllegalStateException("Internal error: unexpected token " + tokenType);
    }
  }

  /**
   * Return the TokenType that would generate this JactlType
   * @return the TokenType
   */
  public TokenType tokenType() {
    switch (this.type) {
      case BOOLEAN:    return TokenType.BOOLEAN;
      case INT:        return TokenType.INT;
      case LONG:       return TokenType.LONG;
      case DOUBLE:     return TokenType.DOUBLE;
      case DECIMAL:    return TokenType.DECIMAL;
      case STRING:     return TokenType.STRING;
      case MAP:        return TokenType.MAP;
      case LIST:       return TokenType.LIST;
      case ANY:        return TokenType.DEF;
      case FUNCTION:   return TokenType.DEF;
      case NUMBER:     return TokenType.NUMBER;
      case OBJECT_ARR: return TokenType.OBJECT_ARR;
      case LONG_ARR:   return TokenType.LONG_ARR;
      case STRING_ARR: return TokenType.STRING_ARR;
      case UNKNOWN:    return TokenType.VAR;
      default: throw new IllegalStateException("Internal error: unexpected type " + this.type);
    }
  }

  public boolean isRef() {
    return isRef;
  }

  public boolean isNumeric() {
    switch (this.type) {
      case INT:
      case LONG:
      case DOUBLE:
      case DECIMAL:
      case NUMBER:
        return true;
      default:
        return false;
    }
  }

  public boolean isPrimitive() {
    return !isRef;
  }

  public JactlType boxed() {
    switch (getType()) {
      case BOOLEAN:   return BOXED_BOOLEAN;
      case INT:       return BOXED_INT;
      case LONG:      return BOXED_LONG;
      case DOUBLE:    return BOXED_DOUBLE;
      default: return this;
    }
  }

  public JactlType unboxed() {
    switch (getType()) {
      case BOOLEAN:   return BOOLEAN;
      case INT:       return INT;
      case LONG:      return LONG;
      case DOUBLE:    return DOUBLE;
      default: return this;
    }
  }

  /**
   * Check if type is one of the supplied types.
   * @param types array of types
   * @return true if type is one of the types
   */
  public boolean is(JactlType... types) {
    for (JactlType type: types) {
      type = type.getDelegate();
      if (this == type) {
        return true;
      }
      // Can't use == for INSTANCE/CLASS type since we create a new one for each class
      if (this.type == TypeEnum.INSTANCE && type.type == TypeEnum.INSTANCE) {
        return true;
      }
      if (this.type == TypeEnum.CLASS && type.type == TypeEnum.CLASS) {
        return true;
      }
      if (this.type == TypeEnum.ARRAY && type.type == TypeEnum.ARRAY) {
        return this.arrayType == null || type.arrayType == null || this.arrayType.is(type.arrayType);
      }
    }
    return false;
  }

  /**
   * Check if type is one of the supplied types ignoring whether
   * any type is boxed (allows quick check for INT, LONG, etc
   * without having to worry about checking whether either type
   * is boxed).
   * @param types  list of JactlTypes to compare to
   * @return true if type is one of the types in the list
   */
  public boolean isBoxedOrUnboxed(JactlType... types) {
    for (JactlType type: types) {
      type = type.getDelegate();
      if (this.type == type.type) {
        return true;
      }
    }
    return false;
  }

  protected JactlType getDelegate() {
    return this;
  }

  public JactlType getArrayType() { return arrayType; }

  ///////////////////////////////////////

  private static class TypePair {
    JactlType t1; JactlType t2;
    TypePair(JactlType t1, JactlType t2) { this.t1 = t1; this.t2 = t2; }

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

  private static List                            resultTypes = List.of(
    new TypePair(INT,     LONG),      LONG,
    new TypePair(INT,     DOUBLE),    DOUBLE,
    new TypePair(INT,     DECIMAL),   DECIMAL,
    new TypePair(INT,     STRING),    STRING,
    new TypePair(INT,     ANY),       ANY,

    new TypePair(DOUBLE,   LONG),     DOUBLE,
    new TypePair(DOUBLE,   DECIMAL),  DECIMAL,
    new TypePair(DOUBLE,   STRING),   STRING,
    new TypePair(DOUBLE,   ANY),      ANY,

    new TypePair(DECIMAL,  LONG),     DECIMAL,
    new TypePair(DECIMAL,  STRING),   STRING,
    new TypePair(DECIMAL,  ANY),      ANY,

    new TypePair(STRING,   BOOLEAN),  STRING,
    new TypePair(STRING,   LONG),     STRING,
    new TypePair(STRING,   ANY),      ANY,
    new TypePair(LIST,     ITERATOR), LIST
  );
  private static final Map<TypePair, JactlType> resultMap   = new HashMap<>();
  static {
    for (Iterator it = resultTypes.iterator(); it.hasNext(); ) {
      resultMap.put((TypePair)it.next(), (JactlType)it.next());
    }
  }

  /**
   * In an expression with two operands determine the resulting type of the expression.
   * @param type1    type of operand1
   * @param operator the operator
   * @param type2    type of operand2
   * @return resulting type
   */
  public static JactlType result(JactlType type1, Token operator, JactlType type2) {
    type1 = type1.getDelegate().unboxed();
    type2 = type2.getDelegate().unboxed();
    if (operator.is(TokenType.IN, TokenType.BANG_IN)) {
      if (!type2.is(ANY,STRING,LIST,MAP,ITERATOR)) {
        throw new CompileError("Type " + type2 + " is not a valid type for right-hand side of '" + operator.getChars() + "'", operator);
      }
      return BOOLEAN;
    }

    // Boolean comparisons
    if (operator.is(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL, TokenType.TRIPLE_EQUAL,
                    TokenType.BANG_EQUAL_EQUAL, TokenType.AMPERSAND_AMPERSAND, TokenType.PIPE_PIPE))   { return BOOLEAN; }
    if (operator.getType().isBooleanOperator()) {
      if (type1.is(ANY) || type2.is(ANY))                              { return BOOLEAN; }
      if (type1.isNumeric() && type2.isNumeric())                      { return BOOLEAN; }
      if (type1.is(BOOLEAN,STRING) && type1.equals(type2))             { return BOOLEAN; }
      throw new CompileError("Type " + type1 + " cannot be compared to " + type2, operator);
    }

    if (operator.is(TokenType.COMPARE)) {
      if (type1.is(BOOLEAN,STRING,ANY,LIST) &&
          type2.is(BOOLEAN,STRING,ANY,LIST) &&
          (type1.is(ANY,type2) || type2.is(ANY,type1)))        { return INT;     }
      if (type1.isNumeric() && type2.isNumeric())              { return INT;     }
      if (type1.isNumeric() && type2.is(ANY))                  { return INT;     }
      if (type2.isNumeric() && type1.is(ANY))                  { return INT;     }
      throw new CompileError("Cannot compare objects of type " + type1 + " and " + type2, operator);
    }

    if (operator.is(TokenType.EQUAL_GRAVE, TokenType.BANG_GRAVE)) {
      if (type1.is(ANY,STRING) && type2.is(ANY,STRING))           { return BOOLEAN;   }
      throw new CompileError("Cannot do regex match on types " + type1 + " and " + type2, operator);
    }

    if (operator.is(TokenType.PLUS, TokenType.MINUS) && type1.is(ANY))                 { return ANY;       }
    if (operator.is(TokenType.PLUS) && type1.is(STRING))                    { return STRING;    }
    if (operator.is(TokenType.PLUS) && type1.is(LIST, ITERATOR))             { return LIST;      }
    if (operator.is(TokenType.PLUS) && type1.is(MAP) && type2.is(MAP, ANY))  { return MAP;       }
    if (operator.is(TokenType.MINUS) && type1.is(MAP) && type2.is(MAP, LIST, ANY)) { return MAP; }
    if (operator.is(TokenType.PLUS, TokenType.MINUS) && type1.is(MAP)) {
      throw new CompileError("Cannot " + (operator.is(TokenType.PLUS) ? "add" : "subtract") + " " + type2
                             + (operator.is(TokenType.PLUS) ? " to " : " from ") + "Map", operator);
    }

    if (operator.is(TokenType.STAR) && type1.is(STRING) &&
        (type2.isNumeric() || type2.is(ANY)))                     { return STRING;    }

    if (operator.is(TokenType.LEFT_SQUARE, TokenType.QUESTION_SQUARE) &&
        type1.is(STRING))                                         { return STRING;    }

    if (operator.is(TokenType.EQUAL, TokenType.QUESTION_COLON, TokenType.QUESTION)) {
      if (type1 == type2)                                         { return type1; }
      if (!type2.isConvertibleTo(type1)) {
        throw new CompileError("Right-hand operand of type " + type2 + " cannot be converted to " + type1, operator);
      }
      if (operator.is(TokenType.QUESTION)) {
        if (type1.is(ANY) || type2.is(ANY))                       { return ANY; }
        JactlType result = resultType(type1, type2);
        if (result != null) {
          return result;
        }
        throw new CompileError("Types for both true and false cases must be compatible", operator);
      }
      return type1;
    }

    if (operator.is(TokenType.DOUBLE_LESS_THAN)) {
      if (type1.is(LIST,ITERATOR))                             { return LIST; }
      if (type1.is(ANY))                                       { return ANY;  }
    }

    if (operator.getType().isBooleanOperator()) {
      return BOOLEAN;
    }

    // Only numeric operations left so must have numeric (or ANY) operands
    checkIsNumeric(type1, "left", operator);
    checkIsNumeric(type2, "right", operator);

    // Check for bit manipulation operations which only work on int/long values
    if (operator.getType().isBitOperator()) {
      if (!type1.is(INT,LONG,ANY)) {
        throw new CompileError("Left-hand operand for '" + operator.getChars() + "' must be int or long", operator);
      }
      if (!type2.is(INT,LONG,ANY)) {
        throw new CompileError("Right-hand operand for '" + operator.getChars() + "' must be int or long", operator);
      }
      if (operator.getType().isBitShift()) { return type1;  }  // Result of bit shift is always type on lhs
      if (type1.is(ANY) || type2.is(ANY))  { return ANY;    }
      // If both args are ints then result is int. Otherwise default to long.
      return type1.is(LONG) || type2.is(LONG) ? LONG: INT;
    }

    if (type1.is(type2))                { return type1.unboxed(); }
    if (type1.is(ANY) || type2.is(ANY)) { return ANY;             }

    JactlType result = resultType(type1, type2);
    if (result == null) {
      throw new CompileError("Arguments of type " + type1 + " and " + type2 + " not supported by operator '" + operator.getChars() + "'", operator);
    }
    return result;
  }

  private static JactlType resultType(JactlType type1, JactlType type2) {
    if (type1.is(type2)) {
      return type1;
    }
    return resultMap.get(new TypePair(type1.getDelegate().unboxed(), type2.getDelegate().unboxed()));
  }

  /**
   * Check if type is compatible and can be converted to given type
   * @param otherType  the type to be converted to
   * @return true if convertible
   */
  public boolean isConvertibleTo(JactlType otherType) {
    if (otherType.isBoxedOrUnboxed(BOOLEAN))                                    { return true; }
    if (this.type == TypeEnum.INSTANCE && otherType.type == TypeEnum.INSTANCE)  { return otherType.isAssignableFrom(this); }
    if (is(CLASS) || otherType.is(CLASS))                                       { return false; }
    if (isBoxedOrUnboxed(otherType))                                            { return true; }
    if (is(ANY) || otherType.is(ANY))                                           { return true; }
    if (isNumeric() && otherType.isNumeric())                                   { return true; }
    if (is(MAP) && otherType.is(INSTANCE))                                      { return true; }
    if (is(MAP,INSTANCE) && otherType.is(MAP,INSTANCE))                         { return true; }
    if (is(LIST,ITERATOR,OBJECT_ARR) && otherType.is(LIST,ITERATOR,OBJECT_ARR)) { return true; }
    return false;
  }

  public boolean isAssignableFrom(JactlType otherType) {
    if (this.type == TypeEnum.INSTANCE && otherType.type == TypeEnum.INSTANCE) {
      if (getClassDescriptor() == null)           { throw new IllegalStateException("Internal error: classDescriptor should be set"); }
      if (otherType.getClassDescriptor() == null) { throw new IllegalStateException("Internal error: classDescriptor should be set"); }
      return this.getClassDescriptor().isAssignableFrom(otherType.getClassDescriptor());
    }
    return false;
  }

  public String descriptor() {
    switch (this.type) {
      case BOOLEAN:        return Type.getDescriptor(isBoxed() ? Boolean.class : boolean.class);
      case INT:            return Type.getDescriptor(isBoxed() ? Integer.class : int.class);
      case LONG:           return Type.getDescriptor(isBoxed() ? Long.class    : long.class);
      case DOUBLE:         return Type.getDescriptor(isBoxed() ? Double.class  : double.class);
      case DECIMAL:        return Type.getDescriptor(BigDecimal.class);
      case STRING:         return Type.getDescriptor(String.class);
      case MAP:            return Type.getDescriptor(Map.class);
      case LIST:           return Type.getDescriptor(List.class);
      case INSTANCE:       return "L" + getInternalName() + ";";
      case CLASS:          return "L" + getInternalName() + ";";
      case ANY:            return Type.getDescriptor(Object.class);
      case FUNCTION:       return Type.getDescriptor(MethodHandle.class);
      case HEAPLOCAL:      return Type.getDescriptor(HeapLocal.class);
      case OBJECT_ARR:     return Type.getDescriptor(Object[].class);
      case LONG_ARR:       return Type.getDescriptor(long[].class);
      case STRING_ARR:     return Type.getDescriptor(String[].class);
      case ITERATOR:       return Type.getDescriptor(Iterator.class);
      case MATCHER:        return Type.getDescriptor(RegexMatcher.class);
      case CONTINUATION:   return Type.getDescriptor(Continuation.class);
      case ARRAY:          return "[" + arrayType.descriptor();
      default:             throw new IllegalStateException("Internal error: unexpected type " + this.type);
    }
  }

  public Type descriptorType() {
    switch (this.type) {
      case BOOLEAN:        return Type.getType(isBoxed() ? Boolean.class : boolean.class);
      case INT:            return Type.getType(isBoxed() ? Integer.class : int.class);
      case LONG:           return Type.getType(isBoxed() ? Long.class    : long.class);
      case DOUBLE:         return Type.getType(isBoxed() ? Double.class  : double.class);
      case DECIMAL:        return Type.getType(BigDecimal.class);
      case STRING:         return Type.getType(String.class);
      case MAP:            return Type.getType(Map.class);
      case LIST:           return Type.getType(List.class);
      case INSTANCE:       return Type.getType(descriptor());
      case CLASS:          return Type.getType(descriptor());
      case ARRAY:          return Type.getType(descriptor());
      case ANY:            return Type.getType(Object.class);
      case FUNCTION:       return Type.getType(MethodHandle.class);
      case HEAPLOCAL:      return Type.getType(HeapLocal.class);
      case OBJECT_ARR:     return Type.getType(Object[].class);
      case LONG_ARR:       return Type.getType(long[].class);
      case STRING_ARR:     return Type.getType(String[].class);
      case ITERATOR:       return Type.getType(Iterator.class);
      case NUMBER:         return Type.getType(Number.class);
      case MATCHER:        return Type.getType(RegexMatcher.class);
      case CONTINUATION:   return Type.getType(Continuation.class);
      default:             throw new IllegalStateException("Internal error: unexpected type " + this.type);
    }
  }

  public String getInternalName() {
    switch (this.type) {
      case BOOLEAN:      return Type.getInternalName(isBoxed() ? Boolean.class : boolean.class);
      case INT:          return Type.getInternalName(isBoxed() ? Integer.class : int.class);
      case LONG:         return Type.getInternalName(isBoxed() ? Long.class    : long.class);
      case DOUBLE:       return Type.getInternalName(isBoxed() ? Double.class  : double.class);
      case DECIMAL:      return Type.getInternalName(BigDecimal.class);
      case STRING:       return Type.getInternalName(String.class);
      case MAP:          return Type.getInternalName(Map.class);
      case LIST:         return Type.getInternalName(List.class);
      case ANY:          return Type.getInternalName(Object.class);
      case FUNCTION:     return Type.getInternalName(MethodHandle.class);
      case HEAPLOCAL:    return Type.getInternalName(HeapLocal.class);
      case OBJECT_ARR:   return Type.getInternalName(Object[].class);
      case LONG_ARR:     return Type.getInternalName(long[].class);
      case STRING_ARR:   return Type.getInternalName(String[].class);
      case ITERATOR:     return Type.getInternalName(Iterator.class);
      case NUMBER:       return Type.getInternalName(Number.class);
      case MATCHER:      return Type.getInternalName(RegexMatcher.class);
      case CONTINUATION: return Type.getInternalName(Continuation.class);
      case ARRAY: {
        if (arrayType.is(ARRAY)) {
          return "[" + arrayType.getInternalName();
        }
        if (arrayType.isPrimitive()) {
          return "[" + arrayType.descriptor();
        }
        return "[L" + arrayType.getInternalName() + ";";
      }
      case INSTANCE:
      case CLASS:
        if (internalName != null) {
          return internalName;
        }
        return getClassDescriptor().getInternalName();
      default:
        throw new IllegalStateException("Unexpected value: " + this);
    }
  }

  public static JactlType typeOf(Object obj) {
    if (obj instanceof Boolean)      return BOOLEAN;
    if (obj instanceof Integer)      return INT;
    if (obj instanceof Long)         return LONG;
    if (obj instanceof Double)       return DOUBLE;
    if (obj instanceof BigDecimal)   return DECIMAL;
    if (obj instanceof String)       return STRING;
    if (obj instanceof Map)          return MAP;
    if (obj instanceof List)         return LIST;
    if (obj instanceof MethodHandle) return FUNCTION;
    if (obj instanceof HeapLocal)    return HEAPLOCAL;
    if (obj instanceof long[])       return LONG_ARR;
    if (obj instanceof String[])     return STRING_ARR;
    if (obj instanceof Object[])     return OBJECT_ARR;
    if (obj instanceof int[])        return JactlType.arrayOf(INT);
    if (obj instanceof boolean[])    return JactlType.arrayOf(BOOLEAN);
    if (obj instanceof double[])     return JactlType.arrayOf(DOUBLE);
    if (obj instanceof Iterator)     return ITERATOR;
    return ANY;
  }

  public static JactlType typeFromClass(Class clss) {
    if (clss == boolean.class)      { return BOOLEAN;       }
    if (clss == Boolean.class)      { return BOXED_BOOLEAN; }
    if (clss == int.class)          { return INT;           }
    if (clss == Integer.class)      { return BOXED_INT;     }
    if (clss == long.class)         { return LONG;          }
    if (clss == Long.class)         { return BOXED_LONG;    }
    if (clss == double.class)       { return DOUBLE;        }
    if (clss == Double.class)       { return BOXED_DOUBLE;  }
    if (clss == BigDecimal.class)   { return DECIMAL;       }
    if (clss == String.class)       { return STRING;        }
    if (clss == Map.class)          { return MAP;           }
    if (clss == List.class)         { return LIST;          }
    if (clss == MethodHandle.class) { return FUNCTION;      }
    if (clss == HeapLocal.class)    { return HEAPLOCAL;     }
    if (clss == long[].class)       { return LONG_ARR;      }
    if (clss == String[].class)     { return STRING_ARR;    }
    if (clss == Object[].class)     { return OBJECT_ARR;    }
    if (clss == Iterator.class)     { return ITERATOR;      }
    if (clss == Number.class)       { return NUMBER;        }
    if (clss == RegexMatcher.class) { return MATCHER;       }
    if (clss == Continuation.class) { return CONTINUATION;  }
    if (clss == Object.class)       { return ANY;           }
    if (clss == Class.class)        { return CLASS;         }
    if (clss == JactlObject.class || JactlObject.class.isAssignableFrom(clss))  {
      return INSTANCE;
    }
    return ANY;
  }

  public Class classFromType() {
    switch (this.type) {
      case BOOLEAN:       return isBoxed() ? Boolean.class : boolean.class;
      case INT:           return isBoxed() ? Integer.class : int.class;
      case LONG:          return isBoxed() ? Long.class    : long.class;
      case DOUBLE:        return isBoxed() ? Double.class  : double.class;
      case DECIMAL:       return BigDecimal.class;
      case STRING:        return String.class;
      case MAP:           return Map.class;
      case LIST:          return List.class;
      case INSTANCE:      return JactlObject.class;
      case ANY:           return Object.class;
      case FUNCTION:      return MethodHandle.class;
      case HEAPLOCAL:     return HeapLocal.class;
      case LONG_ARR:      return long[].class;
      case STRING_ARR:    return String[].class;
      case OBJECT_ARR:    return Object[].class;
      case ITERATOR:      return Iterator.class;
      case MATCHER:       return RegexMatcher.class;
      case CONTINUATION:  return Continuation.class;
      case NUMBER:        return Number.class;
      case CLASS:         return Class.class;
      default: throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  private static void checkIsNumeric(JactlType type, String leftOrRight, Token operator) {
    if (!type.isNumeric() && !type.is(ANY)) {
      throw new CompileError("Non-numeric operand for " + leftOrRight + "-hand side of '" + operator.getChars() + "': was " + type, operator);
    }
  }

  public String typeName() {
    switch (type) {
      case BOOLEAN:      return isBoxed() ? "Boolean" : "boolean";
      case INT:          return isBoxed() ? "Integer" : "int";
      case LONG:         return isBoxed() ? "Long" : "long";
      case DOUBLE:       return isBoxed() ? "Double" : "double";
      case DECIMAL:      return "Decimal";
      case STRING:       return "String";
      case MAP:          return "Map";
      case LIST:         return "List";
      case ANY:          return "def";
      case FUNCTION:     return "Function";
      case HEAPLOCAL:    return "HeapLocal";
      case OBJECT_ARR:   return "Object[]";
      case LONG_ARR:     return "long[]";
      case STRING_ARR:   return "String[]";
      case MATCHER:      return "Matcher";
      case ITERATOR:     return "Iterator";
      case NUMBER:       return "Number";
      case CONTINUATION: return "Continuation";
      case INSTANCE:     return "Instance<" + getPackagedName() + ">";
      case CLASS:        return "Class<" + getPackagedName() + ">";
      case ARRAY:        return "Array<" + arrayType + ">";
      case UNKNOWN:      return "UNKNOWN";
    }
    throw new IllegalStateException("Internal error: unexpected type " + type);
  }

  private String getPackagedName() {
    if (getClassDescriptor() == null) {
      return internalName;
    }
    return classDescriptor.getPackagedName();
  }

  public List<Expr> getClassName() {
    return className;
  }

  @Override public String toString() {
    return typeName();
  }

  public void setClassDescriptor(ClassDescriptor descriptor) {
    this.classDescriptor = descriptor;
  }

  public ClassDescriptor getClassDescriptor() {
    return classDescriptor;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JactlType)) {
      return false;
    }
    if (obj instanceof DelegatingJactlType) {
      obj = ((DelegatingJactlType)obj).getDelegate();
    }
    JactlType other = (JactlType)obj;
    if (this.type != other.type) {
      return false;
    }
    if (is(INSTANCE,CLASS)) {
      if (this.getInternalName() != null) {
        return this.getInternalName().equals(other.getInternalName());
      }
      return this.getClassDescriptor().equals(other.getClassDescriptor());
    }
    return true;
  }
}
