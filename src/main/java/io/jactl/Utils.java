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

import io.jactl.runtime.FunctionDescriptor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.BOOLEAN;
import static io.jactl.JactlType.BYTE;
import static io.jactl.JactlType.DECIMAL;
import static io.jactl.JactlType.DOUBLE;
import static io.jactl.JactlType.INT;
import static io.jactl.JactlType.LONG;
import static io.jactl.JactlType.STRING;
import static io.jactl.TokenType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.NEW;

public class Utils {

  public static final String JACTL_PKG           = "jactl.pkg";   // Java base package for Jactl classes
  public static final String DEFAULT_JACTL_PKG   = "";
  public static final String JACTL_PREFIX        = "_$j$";
  public static final String JACTL_SCRIPT_MAIN   = JACTL_PREFIX + "main";
  public static final String JACTL_INIT          = JACTL_PREFIX + "init";
  public static final String JACTL_INIT_WRAPPER  = Utils.wrapperName(JACTL_PREFIX + "init");
  public static final String JACTL_INIT_NOASYNC  = JACTL_PREFIX + "initNoAsync";
  public static final String JACTL_SCRIPT_PREFIX = JACTL_PREFIX + "Script";

  public static final String JACTL_FIELDS_METHODS_MAP           = JACTL_PREFIX + "FieldsAndMethods";
  public static final String JACTL_FIELDS_METHODS_GETTER        = JACTL_PREFIX + "getFieldsAndMethods";
  public static final String JACTL_STATIC_FIELDS_METHODS_MAP    = JACTL_PREFIX + "StaticFieldsAndMethods";
  public static final String JACTL_STATIC_FIELDS_METHODS_GETTER = JACTL_PREFIX + "getStaticFieldsAndMethods";
  public static final String JACTL_STATIC_FIELDS_METHODS_STATIC_GETTER = JACTL_PREFIX + "StaticGetStaticFieldsAndMethods";
  public static final String JACTL_PRETTY_NAME_FIELD            = JACTL_PREFIX + "PackagedName";
  public static final String JACTL_WRITE_JSON                   = JACTL_PREFIX + "writeJson";
  public static final String JACTL_FROM_JSON                    = "fromJson";
  public static final String JACTL_READ_JSON                    = JACTL_PREFIX + "readJson";
  public static final String JACTL_INIT_MISSING                 = JACTL_PREFIX + "initMissingFields";
  public static final String JACTL_CHECKPOINT_FN                = JACTL_PREFIX + "checkpoint";
  public static final String JACTL_RESTORE_FN                   = JACTL_PREFIX + "restore";

  public static final Class JACTL_MAP_TYPE  = LinkedHashMap.class;
  public static final Class JACTL_LIST_TYPE = ArrayList.class;

  public static final String JACTL_GLOBALS_NAME = JACTL_PREFIX + "globals";
  public static final String SOURCE_VAR_NAME    = JACTL_PREFIX + "source";
  public static final String OFFSET_VAR_NAME    = JACTL_PREFIX + "offset";
  public static final String ARGS_VAR_NAME      = JACTL_PREFIX + "args";

  public static final String EVAL_ERROR        = "$error";   // Name of output variable containing error (if any) for eval()

  public static final String IT_VAR            = "it";       // Name of implicit arg for closures
  public static final String PATTERN_VAR       = "_";        // Name of var used in structural pattern matching
  public static final String CAPTURE_VAR       = "$@";       // Name of internal array for capture values of a regex
  public static final String THIS_VAR          = "this";
  public static final String SUPER_VAR         = "super";
  public static final String UNDERSCORE_VAR    = "_";
  public static final String TO_STRING         = "toString";
  public static final int    DEFAULT_MIN_SCALE = 10;

  public static final char   REGEX_GLOBAL           = 'g';
  public static final char   REGEX_CASE_INSENSITIVE = 'i';
  public static final char   REGEX_DOTALL_MODE      = 's';
  public static final char   REGEX_MULTI_LINE_MODE  = 'm';
  public static final char   REGEX_NON_DESTRUCTIVE = 'r';
  public static final char   REGEX_CAPTURE_NUMS    = 'n';

  public static final Object[] EMPTY_OBJ_ARR = new Object[0];

  /**
   * Get the name of the static field that will contain the handle for a given method
   * @param methodName method name
   * @return the name of the static field for the method handle
   */
  public static String staticHandleName(String methodName) {
    return methodName+"$sh";
  }

  /**
   * Get the name of the instance field that will contain the handle for a given method
   * that has been bound to the instance
   * @param methodName the method name
   * @return the name of the instance field for the handle of the method
   */
  public static String handleName(String methodName) {
    return methodName+"$h";
  }

  public static String wrapperName(String methodName) {
    return methodName + "$$w";
  }

  public static String continuationMethod(String methodName) {
    return methodName + "$$c";
  }
  public static String continuationHandle(String methodName) { return methodName + "$ch"; }

  /**
   * Return the numeric type which is the "greater" of the two based on this ordering:
   * <pre>  int, long, double, BigDecimal</pre>
   * @param type1 type 1
   * @param type2 type 2
   * @return the number type that is the "greater" of the two types
   */
  public static JactlType maxNumericType(JactlType type1, JactlType type2) {
    if (!type1.isNumeric() || !type2.isNumeric()) { return null;    }
    if (type1.is(DECIMAL)  || type2.is(DECIMAL))  { return DECIMAL; }
    if (type1.is(DOUBLE)   || type2.is(DOUBLE))   { return DOUBLE;  }
    if (type1.is(LONG)     || type2.is(LONG))     { return LONG;    }
    if (type1.is(INT)      || type2.is(INT))      { return INT;     }
    if (type1.is(BYTE)     && type2.is(BYTE))     { return BYTE;    }
    return null;
  }

  public static Object convertNumberTo(JactlType type, Object number) {
    switch (type.getType()) {
      case BYTE:    return toByte(number);
      case INT:     return toInt(number);
      case LONG:    return toLong(number);
      case DOUBLE:  return toDouble(number);
      case DECIMAL: return toDecimal(number);
      default:      throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  public static boolean toBoolean(Object value) {
    if (value == null)               { return false;              }
    if (value instanceof Boolean)    { return (boolean)value;     }
    if (value instanceof Byte)       { return (byte)value != 0;    }
    if (value instanceof Integer)    { return (int)value != 0;    }
    if (value instanceof Long)       { return (long)value != 0;   }
    if (value instanceof Double)     { return (double)value != 0; }
    if (value instanceof BigDecimal) { return !((BigDecimal)value).stripTrailingZeros().equals(BigDecimal.ZERO); }
    if (value instanceof String)     { return ((String)value).length() > 0; }
    return true;
  }

  static byte toByte(Object value) {
    if (value instanceof Byte) { return (byte)value; }
    return (byte)toInt(value);
  }

  public static int toInt(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;         }
    if (value instanceof Byte)       { return (byte)value & 0xff;             }
    if (value instanceof Integer)    { return (int)value;                     }
    if (value instanceof Long)       { return (int)(long)value;               }
    if (value instanceof Double)     { return ((Double)value).intValue();     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).intValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to int (value=" + value + ")");
  }

  public static long toLong(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;          }
    if (value instanceof Byte)       { return (byte)value & 0xff;              }
    if (value instanceof Integer)    { return (int)value;                      }
    if (value instanceof Long)       { return (long)value;                     }
    if (value instanceof Double)     { return ((Double)value).longValue();     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).longValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to long (value=" + value + ")");
  }

  public static double toDouble(Object value) {
    if (value instanceof Boolean)    { return (boolean)value ? 1 : 0;            }
    if (value instanceof Byte)       { return (byte)value & 0xff;                }
    if (value instanceof Integer)    { return (int)value;                        }
    if (value instanceof Long)       { return (long)value;                       }
    if (value instanceof Double)     { return (double)value;                     }
    if (value instanceof BigDecimal) { return ((BigDecimal)value).doubleValue(); }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to double (value=" + value + ")");
  }

  public static BigDecimal toDecimal(Object value) {
    if (value instanceof Boolean)    { return new BigDecimal((boolean)value ? 1 : 0); }
    if (value instanceof Byte)       { return new BigDecimal((byte)value & 0xff);    }
    if (value instanceof Integer)    { return new BigDecimal((int)value);             }
    if (value instanceof Long)       { return new BigDecimal((long)value);            }
    if (value instanceof Double)     { return BigDecimal.valueOf((double)value);      }
    if (value instanceof BigDecimal) { return (BigDecimal)value;                      }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to decimal (value=" + value + ")");
  }

  public static String toString(Object value) {
    if (value == null) { return "null"; }
    return value.toString();
  }

  public static JactlType loadConst(MethodVisitor mv, Object obj) {
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
    if (obj instanceof Byte || obj instanceof Integer || obj instanceof Short || obj instanceof Character) {
      int value = obj instanceof Character ? (char)obj : ((Number)obj).intValue();
      if (obj instanceof Byte) {
        value = value & 0xff;
      }
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
      if (obj instanceof Byte) {
        return BYTE;
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
    if (obj instanceof Class) {
      Class   clss      = (Class) obj;
      boolean isPrimive = clss.isPrimitive();
      if (isPrimive) {
        String internalName = JactlType.typeFromClass(clss).boxed().getInternalName();
        mv.visitFieldInsn(GETSTATIC, internalName, "TYPE", "Ljava/lang/Class;");
      }
      else {
        mv.visitLdcInsn(Type.getType(clss));
      }
      return ANY;
    }
    else
    if (obj instanceof JactlType) {
      JactlType type = (JactlType)obj;
      if (type.isPrimitive()) {
        String internalName = type.boxed().getInternalName();
        mv.visitFieldInsn(GETSTATIC, internalName, "TYPE", "Ljava/lang/Class;");
      }
      else {
        mv.visitLdcInsn(type.descriptorType());
      }
      return ANY;
    }
    else if (obj instanceof FunctionDescriptor) {
      FunctionDescriptor f = (FunctionDescriptor)obj;
      mv.visitFieldInsn(GETSTATIC, f.implementingClassName, staticHandleName(f.implementingMethod), FUNCTION.descriptor());
      return FUNCTION;
    }
    else {
      throw new IllegalStateException("Constant of type " + obj.getClass().getName() + " not supported");
    }
  }

  public static void checkCast(MethodVisitor mv, JactlType type) {
    if (type.is(ANY)) {
      return;          // No point in casting to Object
    }
    final String internalName = type.getInternalName();
    if (internalName != null) {
      mv.visitTypeInsn(CHECKCAST, internalName);
    }
  }

  public static void newArray(MethodVisitor mv, JactlType type, int numDimensions) {
    switch (type.getArrayElemType().getType()) {
      case BOOLEAN:  mv.visitIntInsn(NEWARRAY, T_BOOLEAN);                         break;
      case BYTE:     mv.visitIntInsn(NEWARRAY, T_BYTE);                            break;
      case INT:      mv.visitIntInsn(NEWARRAY, T_INT);                             break;
      case LONG:     mv.visitIntInsn(NEWARRAY, T_LONG);                            break;
      case DOUBLE:   mv.visitIntInsn(NEWARRAY, T_DOUBLE);                          break;
      case ARRAY:    mv.visitMultiANewArrayInsn(type.descriptor(), numDimensions); break;
      default:
        mv.visitTypeInsn(ANEWARRAY, type.getArrayElemType().getInternalName());
        break;
    }
  }

  public static void storeArrayElement(MethodVisitor mv, JactlType type) {
    switch (type.getType()) {
      case BOOLEAN:  mv.visitInsn(BASTORE); break;
      case BYTE:     mv.visitInsn(BASTORE); break;
      case INT:      mv.visitInsn(IASTORE); break;
      case LONG:     mv.visitInsn(LASTORE); break;
      case DOUBLE:   mv.visitInsn(DASTORE); break;
      default:       mv.visitInsn(AASTORE); break;
    }
  }

  public static void loadArrayElement(MethodVisitor mv, JactlType type) {
    switch (type.getType()) {
      case BOOLEAN:  mv.visitInsn(BALOAD); break;
      case BYTE:     mv.visitInsn(BALOAD); loadConst(mv, 255); mv.visitInsn(IAND); break;
      case INT:      mv.visitInsn(IALOAD); break;
      case LONG:     mv.visitInsn(LALOAD); break;
      case DOUBLE:   mv.visitInsn(DALOAD); break;
      default:       mv.visitInsn(AALOAD); break;
    }
  }

  public static void box(MethodVisitor mv, JactlType type) {
    if (type.isBoxed()) {
      // Nothing to do if not a primitive
      return;
    }
    switch (type.getType()) {
      case BOOLEAN:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        break;
      case BYTE:
        mv.visitMethodInsn(INVOKESTATIC, "io/jactl/runtime/RuntimeUtils", "byteValueOf", "(I)Ljava/lang/Byte;", false);
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
      default: // Nothing to do for other types
    }
  }

  public static void loadStoredValue(MethodVisitor mv, int continuationSlot, int idx, JactlType type) {
    loadContinuationArray(mv, continuationSlot, type);
    //loadArray.run();            // Get long array or object array onto stack
    Utils.loadConst(mv, idx);
    mv.visitInsn(type.isPrimitive() ? LALOAD : AALOAD);
    if (!type.isPrimitive()) {
      if (!type.is(ANY)) {
        Utils.checkCast(mv, type);
      }
    }
    else {
      if (type.is(BYTE)) {
        mv.visitInsn(L2I);
        Utils.loadConst(mv, 255);
        mv.visitInsn(IAND);
      } else if (type.is(BOOLEAN, INT)) {
        mv.visitInsn(L2I);
      } else if (type.is(DOUBLE)) {
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false);
      }
    }
  }

  public static void loadContinuationArray(MethodVisitor mv, int contSlot, JactlType type) {
    mv.visitVarInsn(ALOAD, contSlot);
    mv.visitFieldInsn(GETFIELD, "io/jactl/runtime/Continuation",
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

  public static Expr.FunDecl createFunDecl(Token start, Token nameToken, JactlType returnType, List<Stmt.VarDecl> params, boolean isStatic, boolean isInitMethod, boolean isFinal) {
    Expr.FunDecl funDecl = new Expr.FunDecl(start, nameToken, returnType, params);

    // Build a FunctionDescriptor to unify Jactl functions with built-in functions
    int mandatoryArgCount = mandatoryParamCount(params);
    FunctionDescriptor descriptor = new FunctionDescriptor(nameToken == null ? null : nameToken.getStringValue(),
                                                           returnType,
                                                           params.size(),
                                                           mandatoryArgCount,
                                                           false);
    descriptor.isStatic = isStatic;
    descriptor.mandatoryParams = params.stream()
                                       .filter(p -> p.declExpr.initialiser == null)
                                       .map(p -> p.declExpr.name.getStringValue())
                                       .collect(Collectors.toSet());
    descriptor.paramNames = params.stream().map(p -> p.declExpr.name.getStringValue()).collect(Collectors.toList());
    descriptor.paramTypes = params.stream().map(p -> p.declExpr.type).collect(Collectors.toList());
    descriptor.isInitMethod = isInitMethod;
    descriptor.isFinal = isFinal;
    funDecl.functionDescriptor = descriptor;
    funDecl.isResultUsed = false;
    funDecl.type = FUNCTION;
    params.forEach(p -> p.declExpr.owner = funDecl);
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

  public static Set setOf(Object... elems) {
    return new HashSet(Arrays.asList(elems));
  }

  public static <T> List<T> listOf(T... elems) {
    return Arrays.asList(elems);
  }

  public static Map mapOf(Object... elems) {
    Map result = new LinkedHashMap();
    if ((elems.length % 2) != 0) {
      throw new IllegalStateException("Internal error: mapOf needs even number of args");
    }
    for (int i = 0; i < elems.length; i += 2) {
      result.put(elems[i], elems[i+1]);
    }
    return result;
  }

  public static boolean isLowerCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (!Character.isLowerCase(ch) && !Character.isDigit(ch) && ch != '_') {
        return false;
      }
    }
    return true;
  }

  public static boolean isValidClassName(String s) {
    if (!Character.isUpperCase(s.charAt(0))) {
      return false;
    }
    for (int i = 1; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == '$' || !Character.isJavaIdentifierPart(ch)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isNamedArgs(List<Expr> args) {
    return args.size() == 1 && args.get(0) instanceof Expr.MapLiteral && ((Expr.MapLiteral)args.get(0)).isNamedArgs;
  }

  public static Map<String,Expr> getNamedArgs(List<Expr> args) {
    Function<Expr,String>  argName    = expr -> ((Expr.Literal)expr).value.getStringValue();
    List<Pair<Expr, Expr>> mapEntries = ((Expr.MapLiteral) args.get(0)).entries;
    return mapEntries.stream().collect(Collectors.toMap(entry -> argName.apply(entry.first), entry -> entry.second));
  }

  public static String plural(String word, int count) {
    return count == 1 ? word : (word + "s");
  }

  public static Expr createNewInstance(Token newToken, JactlType className, Token leftParen, List<Expr> args) {
    // We create the instance and then invoke _$j$init on it
    Expr.InvokeNew  invokeNew  = new Expr.InvokeNew(newToken, className);
    Expr.MethodCall invokeInit = new Expr.MethodCall(leftParen, invokeNew, new Token(DOT, newToken), JACTL_INIT, newToken, null, args);
    invokeInit.couldBeNull = false;
    return invokeInit;
  }

  public static Expr createNewInstance(Token newToken, JactlType type, List<Expr> dimensions) {
    Expr.InvokeNew invokeNew = new Expr.InvokeNew(newToken, type);
    invokeNew.dimensions  = dimensions;
    invokeNew.couldBeNull = false;
    return invokeNew;
  }

  public static Stmt.VarDecl createParam(Token name, JactlType type, Expr initialiser, boolean isResultUsed, boolean isExplicitParam) {
    Expr.VarDecl decl  = new Expr.VarDecl(name, new Token(EQUAL,name), initialiser);
    decl.isParam = true;
    decl.isResultUsed = isResultUsed;
    decl.type = type;
    decl.isExplicitParam = isExplicitParam;
    return new Stmt.VarDecl(name, decl);
  }

  public static void createVariableForFunction(Stmt.FunDecl funDecl) {
    // Create a "variable" for the function that will have the JactlMethodHandle as its value
    // so we can get the function by value
    Expr.VarDecl varDecl = new Expr.VarDecl(funDecl.declExpr.nameToken, null, null);
    varDecl.type = FUNCTION;
    varDecl.funDecl = funDecl.declExpr;
    varDecl.isResultUsed = false;
    funDecl.declExpr.varDecl = varDecl;
  }

  public static Map<Character,Object> parseArgs(String[] args, String descriptor, String usage) {
    Consumer<String> error = msg -> {
      System.err.println(msg);
      System.err.println(usage);
      throw new IllegalArgumentException(msg);
    };
    final int OPT_VALUE = 1;
    final int OPT_MULTI = 2;
    Map<Character,Integer> options = new HashMap<>();
    for (int i = 0; i < descriptor.length();) {
      char opt = descriptor.charAt(i++);
      char c;
      int flags = 0;
      while (i < descriptor.length() && !Character.isAlphabetic(c = descriptor.charAt(i))) {
        switch (c) {
          case ':': flags |= OPT_VALUE;    break;
          case '*': flags |= OPT_MULTI;    break;
          default:  throw new IllegalArgumentException("Bad modifier for " + opt + ": '" + c + "'");
        }
        i++;
      }
      options.put(opt, flags);
    }

    boolean optionsFinished = false;
    Map<Character,Object> result = new HashMap<>();
    List<String> files = new ArrayList<>();
    int i;
    ARGS: for (i = 0; i < args.length; i++) {
      if (args[i].startsWith("-") && args[i].length() > 1) {
        for (int j = 1; j < args[i].length(); j++) {
          char opt = args[i].charAt(j);
          if (opt == 'h') {
            System.out.println(usage);
            throw new RuntimeException();
          }
          if (opt == '-') {
            i++;
            break ARGS;
          }
          Integer flags = options.get(opt);
          if (flags == null) {
            error.accept("Unknown option '-" + opt + "'");
          }
          if ((flags & OPT_VALUE) != 0) {
            String value = args[i].length() > j+1 ? args[i].substring(j+1) : i + 1 < args.length ? args[++i] : null;
            if (value == null) {
              error.accept("Missing value for option '-" + opt + "'");
            }
            if ((flags & OPT_MULTI) != 0) {
              List<String> values = (List<String>) result.get(opt);
              if (values == null) {
                values = new ArrayList<>();
                result.put(opt, values);
              }
              values.add(value);
            }
            else {
              if (result.containsKey(opt)) {
                error.accept("Multiple values specified for '-" + opt + "'");
              }
              result.put(opt, value);
            }
            break;
          }
          else {
            if (result.containsKey(opt) && (flags & OPT_MULTI) == 0) {
              error.accept("Multiple values specified for '-" + opt + "'");
            }
            // For options without values count how many times they appear
            Integer count = (Integer) result.get(opt);
            if (count == null) {
              count = 0;
            }
            result.put(opt, count + 1);
          }
        }
      }
      else {
        files.add(args[i]);
      }
    }
    result.put('*', files);                                                     // List of files
    result.put('@', Arrays.asList(Arrays.copyOfRange(args, i, args.length)));   // List of arguments
    return result;
  }

  private static void copyBaseFields(Expr oldExpr, Expr newExpr) {
    newExpr.location      = oldExpr.location;
    newExpr.type          = oldExpr.type;
    newExpr.parentType    = oldExpr.parentType;
    newExpr.isResolved    = oldExpr.isResolved;
    newExpr.isConst       = oldExpr.isConst;
    newExpr.constValue    = oldExpr.constValue;
    newExpr.isCallee      = oldExpr.isCallee;
    newExpr.couldBeNull   = oldExpr.couldBeNull;
    newExpr.isAsync       = oldExpr.isAsync;
    newExpr.isResultUsed  = oldExpr.isResultUsed;
    newExpr.wasNested     = oldExpr.wasNested;
  }

  public static Expr.VarDecl copyVarDecl(Expr.VarDecl varDecl) {
    Expr.VarDecl newDecl = new Expr.VarDecl(varDecl.name, varDecl.equals, varDecl.initialiser);
    copyBaseFields(varDecl, newDecl);
    newDecl.owner                = varDecl.owner;
    newDecl.isGlobal             = varDecl.isGlobal;
    newDecl.isHeapLocal          = varDecl.isHeapLocal;
    newDecl.isPassedAsHeapLocal  = varDecl.isPassedAsHeapLocal;
    newDecl.isParam              = varDecl.isParam;
    newDecl.isExplicitParam      = varDecl.isExplicitParam;
    newDecl.isField              = varDecl.isField;
    newDecl.isConstVar           = varDecl.isConstVar;
    newDecl.isBindingVar         = varDecl.isBindingVar;
    newDecl.slot                 = varDecl.slot;
    newDecl.nestingLevel         = varDecl.nestingLevel;
    newDecl.declLabel            = varDecl.declLabel;
    newDecl.funDecl              = varDecl.funDecl;
    newDecl.classDescriptor      = varDecl.classDescriptor;
    newDecl.parentVarDecl        = varDecl.parentVarDecl;
    newDecl.originalVarDecl      = varDecl.originalVarDecl;
    newDecl.isFinal              = varDecl.isFinal;
    newDecl.lastAssignedType     = varDecl.lastAssignedType;
    newDecl.paramVarDecl         = varDecl.paramVarDecl;
    return newDecl;
  }

  /**
   * Create a new VarDecl that is a copy of the supplied value. This is to allow us to
   * create intermediate HeapLocal VarDecls when passing values down a chain of callers
   * to a callee that needs the value.
   * @param name     the variable name
   * @param varDecl  the original VarDecl
   * @param funDecl  the function to whose HeapLocals we add the VarDecl
   * @return the new VarDecl
   */
  public static Expr.VarDecl createVarDecl(String name, Expr.VarDecl varDecl, Expr.FunDecl funDecl) {
    Expr.VarDecl newVarDecl    = new Expr.VarDecl(varDecl.name, null, null);
    newVarDecl.type            = varDecl.type.boxed();
    newVarDecl.owner           = varDecl.owner;
    newVarDecl.isHeapLocal     = true;
    newVarDecl.isParam         = true;
    newVarDecl.originalVarDecl = varDecl.originalVarDecl;

    // We need to check for scenarios where the current function has been used as a forward
    // reference at some point in the code but between the point of the reference and the
    // declaration of our function there is a variable declared that we know close over.
    // Since that variable didn't exist when the original forward reference was made we
    // have to disallow such forward references.
    // E.g.:
    //   def f(x){g(x)}; def v = ... ; def g(x) { v + x }
    // Since g uses v and v does not exist when f invokes g we have to throw an error.
    // To detect such references we remember the earlies reference and check that the
    // variable we are now closing over was not declared after that reference.
    // NOTE: even if v were another function we still need to disallow this since the
    // JactlMethodHandle for v won't exist at the time that g is invoked.
    if (funDecl.earliestForwardReference != null) {
      if (isEarlier(funDecl.earliestForwardReference, varDecl.location)) {
        throw new CompileError("Forward reference to function " + funDecl.nameToken.getStringValue() + " that closes over variable " +
              varDecl.name.getStringValue() + " not yet declared at time of reference",
              funDecl.earliestForwardReference);
      }
    }

    funDecl.heapLocals.put(varDecl.originalVarDecl == null ? varDecl : varDecl.originalVarDecl, newVarDecl);
    return newVarDecl;
  }

  /**
   * Return true if t1 is earlier (in the _same_ code) then t2.
   * Return false if t1 is not earlier or if code is not the same.
   * @param t1  the first token
   * @param t2  the second token
   * @return true if t1 occurs before t2
   */
  static public boolean isEarlier(Token t1, Token t2) {
    if (!t1.getSource().equals(t2.getSource())) { return false; }
    return t1.getOffset() < t2.getOffset();
  }

  public static boolean isInvokeWrapper(Expr.Call expr, FunctionDescriptor func) {
    return expr.args.size() != func.paramTypes.size() || isNamedArgs(expr.args) || !expr.validateArgsAtCompileTime;
  }

  public static void setReplMode(JactlContext context) {
    context.replMode = true;
  }

  public static Stmt.VarDecl createVarDecl(Expr.FunDecl ownerFunDecl, Token name, JactlType type, Expr init) {
    Expr.VarDecl varDecl = new Expr.VarDecl(name, new Token(EQUAL,name), init);
    if (init != null) {
      init.isResultUsed = true;
    }
    varDecl.type = type;
    return createVarDecl(ownerFunDecl, varDecl);
  }

  public static Stmt.VarDecl createVarDecl(Expr.FunDecl ownerFunDecl, Expr.VarDecl varDecl) {
    varDecl.isResultUsed = false;
    varDecl.owner = ownerFunDecl;
    return new Stmt.VarDecl(varDecl.name, varDecl);
  }

  public static String dumpHex(byte[] buf, int length) {
    String hex = "";
    String readable = "";
    String result = "";
    int idx = 0;
    for (int i = 0; i < length / 16 + 1; i++) {
      for (int j = 0; j < 16; j++) {
        hex += idx < length ? String.format("%02x ", buf[idx]) : "   ";
        readable += (idx < length ? (Character.isISOControl(buf[idx]) ? "." : Character.toString((char)(buf[idx] & 0xff))) : " ") + " ";
        idx++;
      }
      result += String.format("%04x: ", i * 16) + hex + "    " + readable + "\n";
      hex = readable = "";
    }
    return result;
  }

  private static char[] hex = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  public static String toHexString(byte[] buf) {
    StringBuilder sb = new StringBuilder(buf.length * 2);
    for (int i = 0; i < buf.length; i++) {
      int b = buf[i];
      sb.append(hex[(b >>> 4) & 0x0f]);
      sb.append(hex[b & 0x0f]);
    }
    return sb.toString();
  }

  static ThreadLocal<MessageDigest> md5 = ThreadLocal.withInitial(() -> {
    try {
      return MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  });

  public static String md5Hash(String source) {
    return Utils.toHexString(md5.get().digest(source.getBytes()));
  }

  public static boolean isDefaultValue(Expr.VarDecl declExpr) {
    if (declExpr.initialiser == null || !(declExpr.initialiser instanceof Expr.Literal)) {
      return false;
    }
    Object value = ((Expr.Literal) declExpr.initialiser).value.getValue();
    switch (declExpr.type.getType()) {
      case BOOLEAN:        return Boolean.FALSE.equals(value);
      case BYTE: case INT: return Integer.valueOf(0).equals(value);
      case LONG:           return Long.valueOf(0).equals(value);
      case DOUBLE:         return Double.valueOf(0).equals(value);
      default:             return value == null;
    }
  }

  public static Stmt.VarDecl createParam(Token name, JactlType type) {
    Expr.VarDecl declExpr = new Expr.VarDecl(name, null, null);
    declExpr.type         = type;
    declExpr.isParam      = true;
    declExpr.isResultUsed = false;
    return new Stmt.VarDecl(name, declExpr);
  }

  public static List<Field> getFields(Class clss) {
    List<Field> fields = new ArrayList<Field>();
    if (clss.getSuperclass() != null) {
      fields.addAll(getFields(clss.getSuperclass()));
    }
    fields.addAll(Arrays.stream(clss.getFields())
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .collect(Collectors.toList()));
    return fields;
  }

  public static String repeat(String str, int count) {
    if (count == 0) {
      return "";
    }
    if (count < 0) {
      throw new IllegalArgumentException("Count cannot be negative");
    }
    StringBuilder sb = new StringBuilder(count * str.length());
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }

  public static boolean isDigits(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static Expr.VarDecl createVarDeclExpr(Token identifier, JactlType type, Token assignmentOp, Expr initialiser, boolean inClassDecl, boolean isConst, boolean isBindingVar) {
    Expr.VarDecl varDecl = new Expr.VarDecl(identifier, assignmentOp, initialiser);
    varDecl.isResultUsed = false;      // Result not used unless last stmt of a function used as implicit return
    varDecl.type = type;
    varDecl.isField = inClassDecl;
    varDecl.isBindingVar = isBindingVar;
    varDecl.isConstVar = isConst;
    return varDecl;
  }

  public static Expr.VarDecl funcDescriptorToVarDecl(FunctionDescriptor func) {
    return funcDescriptorToVarDecl(func, new Token(null,0));
  }

  public static Expr.VarDecl funcDescriptorToVarDecl(FunctionDescriptor func, Token tok) {
    Function<TokenType,Token> token = t -> new Token(t, tok);
    List<Stmt.VarDecl> params = new ArrayList<>();
    for (int i = 0; i < func.paramTypes.size(); i++) {
      final Expr.VarDecl p = new Expr.VarDecl(tok.newIdent(func.paramNames.get(i)), null, null);
      p.type = func.paramTypes.get(i);
      params.add(new Stmt.VarDecl(token.apply(p.type.tokenType()), p));
    }
    Expr.FunDecl funDecl = new Expr.FunDecl(null, null, func.returnType, params);
    funDecl.functionDescriptor = func;
    funDecl.wrapper = createWrapperFunDecl(tok, func);
    Expr.VarDecl varDecl = new Expr.VarDecl(tok.newIdent(func.name), token.apply(EQUAL), funDecl);
    varDecl.funDecl = funDecl;
    varDecl.type = FUNCTION;
    return varDecl;
  }

  private static Expr.FunDecl createWrapperFunDecl(Token token, FunctionDescriptor func) {
    Expr.FunDecl wrapperFunDecl = createWrapperFunDecl(token, func.implementingMethod, func.isStatic);
    FunctionDescriptor wrapper = wrapperFunDecl.functionDescriptor;
    wrapper.implementingClassName = func.implementingClassName;
    wrapper.implementingMethod    = wrapperName(func.implementingMethod);
    return wrapperFunDecl;
  }

  public static Expr.FunDecl createWrapperFunDecl(Token token, String name, boolean isStatic) {
    List<Stmt.VarDecl> wrapperParams = new ArrayList<>();
    wrapperParams.add(createParam(token.newIdent(SOURCE_VAR_NAME), STRING));
    wrapperParams.add(createParam(token.newIdent(OFFSET_VAR_NAME), INT));
    wrapperParams.add(createParam(token.newIdent(ARGS_VAR_NAME), OBJECT_ARR));
    Expr.FunDecl wrapperFunDecl = createFunDecl(token, token.newIdent(wrapperName(name)), ANY, wrapperParams, isStatic, false, false);
    wrapperFunDecl.functionDescriptor.isWrapper = true;
    wrapperFunDecl.isWrapper = true;
    return wrapperFunDecl;
  }

  public static Method findStaticMethod(Class clss, String methodName) {
    return findMethod(clss, methodName, true);
  }

  public static Method findMethod(Class clss, String methodName, boolean isStatic) {
    if (clss == null) {
      return null;
    }
    List<Method> methods = Arrays.stream(clss.getDeclaredMethods())
                                 .filter(m -> m.getName().equals(methodName))
                                 .filter(m -> isStatic == Modifier.isStatic(m.getModifiers()))
                                 .collect(Collectors.toList());
    if (methods.size() == 0) {
      throw new IllegalArgumentException("Could not find " + (isStatic?"":"non-") + "static method " + methodName + " in class " + clss.getName());
    }
    if (methods.size() > 1) {
      throw new IllegalArgumentException("Found multiple static methods called " + methodName + " in class " + clss.getName());
    }
    return methods.get(0);
  }

}
