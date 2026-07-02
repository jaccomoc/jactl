/*
 * Copyright © 2022-2026 James Crawford
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;

/**
 * Static methods that InvokeDynamic can dispatch to based on the runtime type of
 * the operands for the 6 arithmetic binary operators: +, -, *, /, %, and %%.
 * The left hand operand dictates which method is used and then each method checks
 * the type of the right hand side to decide how to perform the operation.
 * If non-numeric types are involved (e.g. adding lists together) the methods
 * fallback to invoking the appropriate RuntimeUtils.xxx() method that handles all
 * types.
 */
public class BinaryOpMethods {

  private static final MethodHandle STRING_PLUS;
  private static final MethodHandle INT_PLUS;
  private static final MethodHandle LONG_PLUS;
  private static final MethodHandle DOUBLE_PLUS;
  private static final MethodHandle DECIMAL_PLUS;
  private static final MethodHandle INT_MINUS;
  private static final MethodHandle LONG_MINUS;
  private static final MethodHandle DOUBLE_MINUS;
  private static final MethodHandle DECIMAL_MINUS;
  private static final MethodHandle INT_MULTIPLY;
  private static final MethodHandle LONG_MULTIPLY;
  private static final MethodHandle DOUBLE_MULTIPLY;
  private static final MethodHandle DECIMAL_MULTIPLY;
  private static final MethodHandle INT_DIVIDE;
  private static final MethodHandle LONG_DIVIDE;
  private static final MethodHandle DOUBLE_DIVIDE;
  private static final MethodHandle INT_MODULO;
  private static final MethodHandle LONG_MODULO;
  private static final MethodHandle DOUBLE_MODULO;
  private static final MethodHandle INT_REMAINDER;
  private static final MethodHandle LONG_REMAINDER;
  private static final MethodHandle DOUBLE_REMAINDER;
  private static final MethodHandle DEFAULT_PLUS;
  private static final MethodHandle DEFAULT_MINUS;
  private static final MethodHandle DEFAULT_MULTIPLY;
  private static final MethodHandle DEFAULT_DIVIDE;
  private static final MethodHandle DEFAULT_MODULO;
  private static final MethodHandle DEFAULT_REMAINDER;
  
  static {
    try {
      STRING_PLUS         = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "stringPlus", MethodType.methodType(Object.class, String.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_PLUS            = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intPlus", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_PLUS           = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longPlus", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_PLUS         = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doublePlus", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DECIMAL_PLUS        = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "decimalPlus", MethodType.methodType(Object.class, BigDecimal.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_MINUS           = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intMinus", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_MINUS          = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longMinus", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_MINUS        = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doubleMinus", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DECIMAL_MINUS       = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "decimalMinus", MethodType.methodType(Object.class, BigDecimal.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_MULTIPLY        = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intMultiply", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_MULTIPLY       = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longMultiply", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_MULTIPLY     = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doubleMultiply", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DECIMAL_MULTIPLY    = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "decimalMultiply", MethodType.methodType(Object.class, BigDecimal.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_DIVIDE          = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intDivide", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_DIVIDE         = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longDivide", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_DIVIDE       = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doubleDivide", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_MODULO          = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intModulo", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_MODULO         = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longModulo", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_MODULO       = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doubleModulo", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      INT_REMAINDER       = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "intRemainder", MethodType.methodType(Object.class, int.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      LONG_REMAINDER      = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "longRemainder", MethodType.methodType(Object.class, long.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DOUBLE_REMAINDER    = MethodHandles.publicLookup().findStatic(BinaryOpMethods.class, "doubleRemainder", MethodType.methodType(Object.class, double.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_PLUS        = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "plus", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_MINUS       = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "minus", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_MULTIPLY    = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "multiply", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_DIVIDE      = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "divide", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_MODULO      = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "modulo", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
      DEFAULT_REMAINDER   = MethodHandles.publicLookup().findStatic(RuntimeUtils.class, "remainder", MethodType.methodType(Object.class, Object.class, Object.class, String.class, String.class, int.class, boolean.class, String.class, int.class));
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  static MethodHandle lookupMethod(String lhsType, String opName) {
    switch (lhsType + '.' + opName) {
      case "java.lang.String.plus":          return STRING_PLUS;
      case "java.lang.Integer.plus":         return INT_PLUS;
      case "java.lang.Long.plus":            return LONG_PLUS;
      case "java.lang.Double.plus":          return DOUBLE_PLUS;
      case "java.math.BigDecimal.plus":      return DECIMAL_PLUS;
      case "java.lang.Integer.minus":        return INT_MINUS;
      case "java.lang.Long.minus":           return LONG_MINUS;
      case "java.lang.Double.minus":         return DOUBLE_MINUS;
      case "java.math.BigDecimal.minus":     return DECIMAL_MINUS;
      case "java.lang.Integer.multiply":     return INT_MULTIPLY;
      case "java.lang.Long.multiply":        return LONG_MULTIPLY;
      case "java.lang.Double.multiply":      return DOUBLE_MULTIPLY;
      case "java.math.BigDecimal.multiply":  return DECIMAL_MULTIPLY;
      case "java.lang.Integer.divide":       return INT_DIVIDE;
      case "java.lang.Long.divide":          return LONG_DIVIDE;
      case "java.lang.Double.divide":        return DOUBLE_DIVIDE;
      case "java.lang.Integer.modulo":       return INT_MODULO;
      case "java.lang.Long.modulo":          return LONG_MODULO;
      case "java.lang.Double.modulo":        return DOUBLE_MODULO;
      case "java.lang.Integer.remainder":    return INT_REMAINDER;
      case "java.lang.Long.remainder":       return LONG_REMAINDER;
      case "java.lang.Double.remainder":     return DOUBLE_REMAINDER;
      default: { 
        switch (opName) {
          case "plus":      return DEFAULT_PLUS;
          case "minus":     return DEFAULT_MINUS;
          case "multiply":  return DEFAULT_MULTIPLY;
          case "divide":    return DEFAULT_DIVIDE;
          case "modulo":    return DEFAULT_MODULO;
          case "remainder": return DEFAULT_REMAINDER;
          default: throw new IllegalStateException("Unknown operator " + opName);
        }
      }
    }
  }

  public static Object stringPlus(String lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    return lhs + RuntimeUtils.toString(rhs);
  }

  public static Object intPlus(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs + (int)rhs;
    if (rhs instanceof Long)       return (long)lhs + (long)rhs;
    if (rhs instanceof Double)     return lhs + (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).add((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs + ((byte)rhs & 0xff);
    return RuntimeUtils.plus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longPlus(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs + (int)rhs;
    if (rhs instanceof Long)       return lhs + (long)rhs;
    if (rhs instanceof Double)     return lhs + (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).add((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs + ((byte)rhs & 0xff);
    return RuntimeUtils.plus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doublePlus(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs + (int)rhs;
    if (rhs instanceof Long)       return lhs + (long)rhs;
    if (rhs instanceof Double)     return lhs + (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).add((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs + ((byte)rhs & 0xff);
    return RuntimeUtils.plus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object decimalPlus(BigDecimal lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs.add(BigDecimal.valueOf((int)rhs));
    if (rhs instanceof Long)       return lhs.add(BigDecimal.valueOf((long)rhs));
    if (rhs instanceof Double)     return lhs.add(BigDecimal.valueOf((double)rhs));
    if (rhs instanceof BigDecimal) return lhs.add((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs.add(BigDecimal.valueOf((byte)rhs & 0xff));
    return RuntimeUtils.plus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object intMinus(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs - (int)rhs;
    if (rhs instanceof Long)       return (long)lhs - (long)rhs;
    if (rhs instanceof Double)     return lhs - (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).subtract((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs - ((byte)rhs & 0xff);
    return RuntimeUtils.minus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longMinus(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs - (int)rhs;
    if (rhs instanceof Long)       return lhs - (long)rhs;
    if (rhs instanceof Double)     return lhs - (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).subtract((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs - ((byte)rhs & 0xff);
    return RuntimeUtils.minus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doubleMinus(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs - (int)rhs;
    if (rhs instanceof Long)       return lhs - (long)rhs;
    if (rhs instanceof Double)     return lhs - (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).subtract((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs - ((byte)rhs & 0xff);
    return RuntimeUtils.minus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object decimalMinus(BigDecimal lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs.subtract(BigDecimal.valueOf((int)rhs));
    if (rhs instanceof Long)       return lhs.subtract(BigDecimal.valueOf((long)rhs));
    if (rhs instanceof Double)     return lhs.subtract(BigDecimal.valueOf((double)rhs));
    if (rhs instanceof BigDecimal) return lhs.subtract((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs.subtract(BigDecimal.valueOf((byte)rhs & 0xff));
    return RuntimeUtils.minus(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object intMultiply(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs * (int)rhs;
    if (rhs instanceof Long)       return (long)lhs * (long)rhs;
    if (rhs instanceof Double)     return lhs * (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).multiply((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs * ((byte)rhs & 0xff);
    return RuntimeUtils.multiply(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longMultiply(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs * (int)rhs;
    if (rhs instanceof Long)       return lhs * (long)rhs;
    if (rhs instanceof Double)     return lhs * (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).multiply((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs * ((byte)rhs & 0xff);
    return RuntimeUtils.multiply(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doubleMultiply(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs * (int)rhs;
    if (rhs instanceof Long)       return lhs * (long)rhs;
    if (rhs instanceof Double)     return lhs * (double)rhs;
    if (rhs instanceof BigDecimal) return BigDecimal.valueOf(lhs).multiply((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs * ((byte)rhs & 0xff);
    return RuntimeUtils.multiply(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object decimalMultiply(BigDecimal lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs.multiply(BigDecimal.valueOf((int)rhs));
    if (rhs instanceof Long)       return lhs.multiply(BigDecimal.valueOf((long)rhs));
    if (rhs instanceof Double)     return lhs.multiply(BigDecimal.valueOf((double)rhs));
    if (rhs instanceof BigDecimal) return lhs.multiply((BigDecimal)rhs);
    if (rhs instanceof Byte)       return lhs.multiply(BigDecimal.valueOf((byte)rhs & 0xff));
    return RuntimeUtils.multiply(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object intDivide(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs / (int)rhs;
    if (rhs instanceof Long)       return (long)lhs / (long)rhs;
    if (rhs instanceof Double)     return lhs / (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalDivide(BigDecimal.valueOf(lhs), (BigDecimal)rhs, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs / ((byte)rhs & 0xff);
    return RuntimeUtils.divide(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longDivide(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs / (int)rhs;
    if (rhs instanceof Long)       return lhs / (long)rhs;
    if (rhs instanceof Double)     return lhs / (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalDivide(BigDecimal.valueOf(lhs), (BigDecimal)rhs, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs / ((byte)rhs & 0xff);
    return RuntimeUtils.divide(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doubleDivide(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs / (int)rhs;
    if (rhs instanceof Long)       return lhs / (long)rhs;
    if (rhs instanceof Double)     return lhs / (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalDivide(BigDecimal.valueOf(lhs), (BigDecimal)rhs, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs / ((byte)rhs & 0xff);
    return RuntimeUtils.divide(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object intModulo(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    { int right = (int)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Long)       { long right = (long)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Double)     { double right = (double)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       { int right = (byte)rhs & 0xff; return ((lhs % right) + right) % right; }  
    return RuntimeUtils.modulo(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longModulo(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    { int right = (int)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Long)       { long right = (long)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Double)     { double right = (double)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       { int right = (byte)rhs & 0xff; return ((lhs % right) + right) % right; }  
    return RuntimeUtils.modulo(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doubleModulo(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    { int right = (int)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Long)       { long right = (long)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof Double)     { double right = (double)rhs; return ((lhs % right) + right) % right; }
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       { int right = (byte)rhs & 0xff; return ((lhs % right) + right) % right; }  
    return RuntimeUtils.modulo(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object intRemainder(int lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs % (int)rhs;
    if (rhs instanceof Long)       return lhs % (long)rhs;
    if (rhs instanceof Double)     return lhs % (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs % ((byte)rhs & 0xff);
    return RuntimeUtils.remainder(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object longRemainder(long lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs % (int)rhs;
    if (rhs instanceof Long)       return lhs % (long)rhs;
    if (rhs instanceof Double)     return lhs % (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs % ((byte)rhs & 0xff);
    return RuntimeUtils.remainder(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }

  public static Object doubleRemainder(double lhs, Object rhs, String op, String originalOp, int minScale, boolean captureStackTrace, String source, int offset) {
    if (rhs instanceof Integer)    return lhs % (int)rhs;
    if (rhs instanceof Long)       return lhs % (long)rhs;
    if (rhs instanceof Double)     return lhs % (double)rhs;
    if (rhs instanceof BigDecimal) return RuntimeUtils.decimalBinaryOperation(BigDecimal.valueOf(lhs), (BigDecimal)rhs, op, minScale, source, offset);
    if (rhs instanceof Byte)       return lhs % ((byte)rhs & 0xff);
    return RuntimeUtils.remainder(lhs, rhs, op, originalOp, minScale, captureStackTrace, source, offset);
  }
}
