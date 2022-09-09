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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.LONG;
import static org.objectweb.asm.Opcodes.*;

public class Utils {

  public static final String JACSAL_PKG         = "jacsal/pkg";
  public static final String JACSAL_PREFIX      = "_$j$";
  public static final String JACSAL_SCRIPT_MAIN = JACSAL_PREFIX + "main";

  public static final String JACSAL_GLOBALS_NAME = JACSAL_PREFIX + "globals";

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
      if (obj instanceof Object[]) {
        result.addAll(Arrays.asList((Object[])obj));
      }
      else {
        result.add(obj);
      }
    }
    return result;
  }

}
