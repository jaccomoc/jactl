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

import jacsal.runtime.FunctionDescriptor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.math.BigDecimal;
import java.util.*;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.LONG;
import static org.objectweb.asm.Opcodes.*;

public class Utils {

  public static final String JACSAL_PKG         = "jacsal/pkg";
  public static final String JACSAL_PREFIX      = "_$j$";
  public static final String JACSAL_SCRIPT_MAIN = JACSAL_PREFIX + "main";
  public static final String JACSAL_NEW_INSTANCE= JACSAL_PREFIX + "newInstance";

  public static final String JACSAL_GLOBALS_NAME = JACSAL_PREFIX + "globals";
  public static final String JACSAL_OUT_NAME = "out";      // Name of global to use as PrintStream for print/println
  public static final String IT_VAR          = "it";       // Name of implicit arg for closures
  public static final String CAPTURE_VAR     = "$@";       // Name of internal array for capture values of a regex

  /**
   * Get the name of the static field that will contain the handle for a given method
   */
  static String staticHandleName(String methodName) {
    return methodName+"$sh";
  }

  /**
   * Get the name of the instance field that will contain the handle for a given method
   * that has been bound to the instance
   */
  static String handleName(String methodName) {
    return methodName+"$h";
  }

  static String wrapperName(String methodName) {
    return methodName + "$$w";
  }

  static String continuationMethod(String methodName) { return methodName + "$$c"; }
  static String continuationHandle(String methodName) { return methodName + "$ch"; }

  /**
   * Return the numeric type which is the "greater" of the two based on this ordering:
   *   int, long, double, BigDecimal
   */
  static JacsalType maxNumericType(JacsalType type1, JacsalType type2) {
    if (!type1.isNumeric() || !type2.isNumeric()) { return null;    }
    if (type1.is(DECIMAL)  || type2.is(DECIMAL))  { return DECIMAL; }
    if (type1.is(DOUBLE)   || type2.is(DOUBLE))   { return DOUBLE;  }
    if (type1.is(LONG)     || type2.is(LONG))     { return LONG;    }
    if (type1.is(INT)      && type2.is(INT))      { return INT;     }
    return null;
  }

  static Object convertNumberTo(JacsalType type, Object number) {
    switch (type.getType()) {
      case INT:     return toInt(number);
      case LONG:    return toLong(number);
      case DOUBLE:  return toDouble(number);
      case DECIMAL: return toDecimal(number);
      default:      throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  static boolean toBoolean(Object value) {
    if (value == null)               { return false;              }
    if (value instanceof Boolean)    { return (boolean)value;     }
    if (value instanceof Integer)    { return (int)value != 0;    }
    if (value instanceof Long)       { return (long)value != 0;   }
    if (value instanceof Double)     { return (double)value != 0; }
    if (value instanceof BigDecimal) { return !((BigDecimal)value).stripTrailingZeros().equals(BigDecimal.ZERO); }
    if (value instanceof String)     { return ((String)value).length() > 0; }
    return true;
  }

  static int toInt(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;         }
    if (value instanceof Integer)    { return (int)value;                     }
    if (value instanceof Long)       { return (int)(long)value;               }
    if (value instanceof Double)     { return ((Double)value).intValue();     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).intValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to int (value=" + value + ")");
  }

  static long toLong(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;          }
    if (value instanceof Integer)    { return (int)value;                      }
    if (value instanceof Long)       { return (long)value;                     }
    if (value instanceof Double)     { return ((Double)value).longValue();     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).longValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to long (value=" + value + ")");
  }

  static double toDouble(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;            }
    if (value instanceof Integer)    { return (int)value;                        }
    if (value instanceof Long)       { return (long)value;                       }
    if (value instanceof Double)     { return (double)value;                     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).doubleValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to double (value=" + value + ")");
  }

  static BigDecimal toDecimal(Object value) {
    if (value instanceof Boolean)    { return new BigDecimal((boolean)value ? 1 : 0); }
    if (value instanceof Integer)    { return new BigDecimal((int)value);             }
    if (value instanceof Long)       { return new BigDecimal((long)value);            }
    if (value instanceof Double)     { return BigDecimal.valueOf((double)value);      }
    if (value instanceof BigDecimal) { return (BigDecimal)value;                      }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to decimal (value=" + value + ")");
  }

  static String toString(Object value) {
    if (value == null) { return "null"; }
    return value.toString();
  }

  static JacsalType loadConst(MethodVisitor mv, Object obj) {
    if (obj == null) {
      mv.visitInsn(ACONST_NULL);
      return ANY;
    }
    else
    if (obj instanceof Boolean) {
      mv.visitInsn((boolean)obj ? ICONST_1 : ICONST_0);
      return BOOLEAN;
    }
    else
    if (obj instanceof Integer || obj instanceof Short || obj instanceof Character) {
      int value = obj instanceof Character ? (char)obj : ((Number)obj).intValue();
      switch (value) {
        case -1:  mv.visitInsn(ICONST_M1);   break;
        case 0:   mv.visitInsn(ICONST_0);    break;
        case 1:   mv.visitInsn(ICONST_1);    break;
        case 2:   mv.visitInsn(ICONST_2);    break;
        case 3:   mv.visitInsn(ICONST_3);    break;
        case 4:   mv.visitInsn(ICONST_4);    break;
        case 5:   mv.visitInsn(ICONST_5);    break;
        default:
          if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)   { mv.visitIntInsn(BIPUSH, value); break; }
          if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) { mv.visitIntInsn(SIPUSH, value); break; }
          mv.visitLdcInsn(value);
          break;
      }
      return INT;
    }
    else
    if (obj instanceof Long) {
      long value = (long)obj;
      if (value == 0L) {
        mv.visitInsn(LCONST_0);
      }
      else
      if (value == 1L) {
        mv.visitInsn(LCONST_1);
      }
      else {
        mv.visitLdcInsn((long)value);
      }
      return LONG;
    }
    else
    if (obj instanceof Double) {
      double value = (double)obj;
      if (value == 0D) {
        mv.visitInsn(DCONST_0);
      }
      else
      if (value == 1.0D) {
        mv.visitInsn(DCONST_1);
      }
      else {
        mv.visitLdcInsn(obj);
      }
      return DOUBLE;
    }
    else
    if (obj instanceof BigDecimal) {
      if (obj.equals(BigDecimal.ZERO)) {
        mv.visitFieldInsn(GETSTATIC, "java/math/BigDecimal", "ZERO", "Ljava/math/BigDecimal;");
      }
      else if (obj.equals(BigDecimal.ONE)) {
        mv.visitFieldInsn(GETSTATIC, "java/math/BigDecimal", "ONE", "Ljava/math/BigDecimal;");
      }
      else if (obj.equals(BigDecimal.TEN)) {
        mv.visitFieldInsn(GETSTATIC, "java/math/BigDecimal", "TEN", "Ljava/math/BigDecimal;");
      }
      else {
        mv.visitTypeInsn(NEW, "java/math/BigDecimal");
        mv.visitInsn(DUP);
        mv.visitLdcInsn(((BigDecimal) obj).toPlainString());
        mv.visitMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);
      }
      return DECIMAL;
    }
    else
    if (obj instanceof String) {
      mv.visitLdcInsn((String)obj);
      return STRING;
    }
    else
    if (obj instanceof Class || obj instanceof JacsalType) {
      String internalName = obj instanceof Class ? Type.getInternalName((Class)obj)
                                                 : ((JacsalType)obj).getInternalName();
      boolean isPrimive = obj instanceof Class ? ((Class)obj).isPrimitive()
                                               : ((JacsalType)obj).isPrimitive();
      if (isPrimive) {
        if (obj instanceof Class) {
          internalName = JacsalType.typeFromClass((Class)obj).getInternalName();
        }
        mv.visitFieldInsn(GETSTATIC, internalName, "TYPE", "Ljava/lang/Class;");
      }
      else {
        mv.visitLdcInsn(Type.getType("L" + internalName + ";"));
      }
      return ANY;
    }
    else {
      throw new IllegalStateException("Constant of type " + obj.getClass().getName() + " not supported");
    }
  }

  public static void checkedCast(MethodVisitor mv, JacsalType type) {
    final var internalName = type.getInternalName();
    if (internalName != null) {
      mv.visitTypeInsn(CHECKCAST, internalName);
    }
  }

  public static void box(MethodVisitor mv, JacsalType type) {
    if (type.isBoxed()) {
      // Nothing to do if not a primitive
      return;
    }
    switch (type.getType()) {
      case BOOLEAN:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        break;
      case INT:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        break;
      case LONG:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        break;
      case DOUBLE:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        break;
    }
  }

  public static void restoreValue(MethodVisitor mv, int idx, JacsalType type, Runnable loadArray) {
    loadArray.run();            // Get long array or object array onto stack
    Utils.loadConst(mv, idx);
    mv.visitInsn(type.isPrimitive() ? LALOAD : AALOAD);
    if (!type.isPrimitive()) {
      if (!type.is(ANY)) {
        Utils.checkedCast(mv, type);
      }
    }
    else {
      if (type.is(JacsalType.BOOLEAN, JacsalType.INT)) {
        mv.visitInsn(L2I);
      }
      if (type.is(JacsalType.DOUBLE)) {
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false);
      }
    }
  }

  public static void loadContinuationArray(MethodVisitor mv, int contSlot, JacsalType type) {
    mv.visitVarInsn(ALOAD, contSlot);
    mv.visitFieldInsn(GETFIELD, "jacsal/runtime/Continuation",
                      type.isPrimitive() ? "localPrimitives" : "localObjects",
                      type.isPrimitive() ? "[J" : "[Ljava/lang/Object;");
  }

  /**
   * Count how many mandatory parameters are required
   */
  static int mandatoryParamCount(List<Stmt.VarDecl> params) {
    // Count backwards to find last parameter without an initialiser
    for (int i = params.size() - 1; i >= 0; i--) {
      if (params.get(i).declExpr.initialiser == null) {
        return i + 1;
      }
    }
    // All params have an initialiser
    return 0;
  }

  static List concat(Object... objs) {
    var result = new ArrayList<>();
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

  public static Expr.FunDecl createFunDecl(Token start, Token nameToken, JacsalType returnType, List<Stmt.VarDecl> params) {
    Expr.FunDecl funDecl = new Expr.FunDecl(start, nameToken, returnType, params);

    // Build a FunctionDescriptor to unify Jacsal functions with builtin functions
    int mandatoryArgCount = mandatoryParamCount(params);
    FunctionDescriptor descriptor = new FunctionDescriptor(nameToken == null ? null : nameToken.getStringValue(),
                                                           returnType,
                                                           params.size(),
                                                           mandatoryArgCount,
                                                           true);
    funDecl.functionDescriptor = descriptor;
    return funDecl;
  }

  public static String nth(int i) {
    if (i % 10 == 1 && i % 100 != 11) { return i + "st"; }
    if (i % 10 == 2 && i % 100 != 12) { return i + "nd"; }
    if (i % 10 == 3 && i % 100 != 13) { return i + "rd"; }
    return i + "th";
  }

  public static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0,1).toUpperCase() + s.substring(1);
  }

  public static Map mapOf(Object... elems) {
    Map result = new HashMap();
    if ((elems.length % 2) != 0) {
      throw new IllegalStateException("Internal error: mapOf needs even number of args");
    }
    for (int i = 0; i < elems.length; i += 2) {
      result.put(elems[i], elems[i+1]);
    }
    return result;
  }
}
