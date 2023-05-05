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

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.DOUBLE;
import static io.jactl.JactlType.LONG;
import static io.jactl.TokenType.DOT;
import static io.jactl.TokenType.EQUAL;
import static org.objectweb.asm.Opcodes.*;

public class Utils {

  public static final String JACTL_PKG           = "io/jactl/pkg";
  public static final String DEFAULT_JACTL_PKG   = "";
  public static final String JACTL_PREFIX        = "_$j$";
  public static final String JACTL_SCRIPT_MAIN   = JACTL_PREFIX + "main";
  public static final String JACTL_INIT          = JACTL_PREFIX + "init";
  public static final String JACTL_INIT_WRAPPER  = Utils.wrapperName(JACTL_PREFIX + "init");
  public static final String JACTL_SCRIPT_PREFIX = JACTL_PREFIX + "Script";

  public static final String JACTL_FIELDS_METHODS_MAP    = "_$j$FieldsAndMethods";
  public static final String JACTL_FIELDS_METHODS_GETTER = "_$j$getFieldsAndMethods";
  public static final String JACTL_STATIC_METHODS_MAP    = "_$j$StaticMethods";
  public static final String JACTL_STATIC_METHODS_GETTER = "_$j$getStaticMethods";
  public static final String JACTL_STATIC_METHODS_STATIC_GETTER = "_$j$StaticGetStaticMethods";
  public static final String JACTL_PACKAGED_NAME_FIELD   = "_$j$PackagedName";
  public static final String JACTL_WRITE_JSON            = "_$j$writeJson";
  public static final String JACTL_FROM_JSON             = "fromJson";
  public static final String JACTL_READ_JSON             = "_$j$readJson";
  public static final String JACTL_INIT_MISSING          = "_$j$initMissingFields";

  public static final Class JACTL_MAP_TYPE  = LinkedHashMap.class;
  public static final Class JACTL_LIST_TYPE = ArrayList.class;

  public static final String JACTL_GLOBALS_NAME   = JACTL_PREFIX + "globals";
  public static final String JACTL_GLOBALS_OUTPUT = "$output";   // Name of global to use as PrintStream for print/println
  public static final String JACTL_GLOBALS_INPUT  = "$input";    // Name of global to use when reading from stdin

  public static final String SOURCE_VAR_NAME   = "$source";
  public static final String OFFSET_VAR_NAME   = "$offset";

  public static final String EVAL_ERROR        = "$error";   // Name of output variable containing error (if any) for eval()

  public static final String IT_VAR            = "it";       // Name of implicit arg for closures
  public static final String CAPTURE_VAR       = "$@";       // Name of internal array for capture values of a regex
  public static final String THIS_VAR          = "this";
  public static final String SUPER_VAR         = "super";
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

  static String continuationMethod(String methodName) {
    return methodName + "$$c";
  }
  static String continuationHandle(String methodName) { return methodName + "$ch"; }

  /**
   * Return the numeric type which is the "greater" of the two based on this ordering:
   *   int, long, double, BigDecimal
   */
  static JactlType maxNumericType(JactlType type1, JactlType type2) {
    if (!type1.isNumeric() || !type2.isNumeric()) { return null;    }
    if (type1.is(DECIMAL)  || type2.is(DECIMAL))  { return DECIMAL; }
    if (type1.is(DOUBLE)   || type2.is(DOUBLE))   { return DOUBLE;  }
    if (type1.is(LONG)     || type2.is(LONG))     { return LONG;    }
    if (type1.is(INT)      && type2.is(INT))      { return INT;     }
    return null;
  }

  static Object convertNumberTo(JactlType type, Object number) {
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

  static JactlType loadConst(MethodVisitor mv, Object obj) {
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
    else {
      throw new IllegalStateException("Constant of type " + obj.getClass().getName() + " not supported");
    }
  }

  public static void checkCast(MethodVisitor mv, JactlType type) {
    if (type.is(ANY)) {
      return;          // No point in casting to Object
    }
    final var internalName = type.getInternalName();
    if (internalName != null) {
      mv.visitTypeInsn(CHECKCAST, internalName);
    }
  }

  public static void newArray(MethodVisitor mv, JactlType type, int numDimensions) {
    switch (type.getArrayType().getType()) {
      case BOOLEAN:  mv.visitIntInsn(NEWARRAY, T_BOOLEAN);                         break;
      case INT:      mv.visitIntInsn(NEWARRAY, T_INT);                             break;
      case LONG:     mv.visitIntInsn(NEWARRAY, T_LONG);                            break;
      case DOUBLE:   mv.visitIntInsn(NEWARRAY, T_DOUBLE);                          break;
      case ARRAY:    mv.visitMultiANewArrayInsn(type.descriptor(), numDimensions); break;
      default:
        mv.visitTypeInsn(ANEWARRAY, type.getArrayType().getInternalName());
        break;
    }
  }

  public static void storeArrayElement(MethodVisitor mv, JactlType type) {
    switch (type.getType()) {
      case BOOLEAN:  mv.visitInsn(BASTORE); break;
      case INT:      mv.visitInsn(IASTORE); break;
      case LONG:     mv.visitInsn(LASTORE); break;
      case DOUBLE:   mv.visitInsn(DASTORE); break;
      default:       mv.visitInsn(AASTORE); break;
    }
  }

  public static void loadArrayElement(MethodVisitor mv, JactlType type) {
    switch (type.getType()) {
      case BOOLEAN:  mv.visitInsn(BALOAD); break;
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
      if (type.is(JactlType.BOOLEAN, JactlType.INT)) {
        mv.visitInsn(L2I);
      }
      if (type.is(JactlType.DOUBLE)) {
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

  public static boolean isLowerCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isLowerCase(s.charAt(i))) {
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
    Function<Expr,String> argName    = expr -> ((Expr.Literal)expr).value.getStringValue();
    var                   mapEntries = ((Expr.MapLiteral) args.get(0)).entries;
    return mapEntries.stream().collect(Collectors.toMap(entry -> argName.apply(entry.first), entry -> entry.second));
  }

  public static String plural(String word, int count) {
    return count == 1 ? word : (word + "s");
  }

  public static Expr createNewInstance(Token newToken, JactlType className, Token leftParen, List<Expr> args) {
    // We create the instance and then invoke _$j$init on it
    var invokeNew  = new Expr.InvokeNew(newToken, className);
    var invokeInit = new Expr.MethodCall(leftParen, invokeNew, new Token(DOT, newToken), JACTL_INIT, newToken, args);
    invokeInit.couldBeNull = false;
    return invokeInit;
  }

  public static Expr createNewInstance(Token newToken, JactlType type, List<Expr> dimensions) {
    var invokeNew = new Expr.InvokeNew(newToken, type);
    invokeNew.dimensions  = dimensions;
    invokeNew.couldBeNull = false;
    return invokeNew;
  }

  public static Stmt.VarDecl createParam(Token name, JactlType type, Expr initialiser) {
    Expr.VarDecl decl  = new Expr.VarDecl(name, new Token(EQUAL,name), initialiser);
    decl.isParam = true;
    decl.isResultUsed = false;
    decl.type = type;
    return new Stmt.VarDecl(name, decl);
  }

  public static void createVariableForFunction(Stmt.FunDecl funDecl) {
    // Create a "variable" for the function that will have the MethodHandle as its value
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
    result.put('*', files);                                                 // List of files
    result.put('@', List.of(Arrays.copyOfRange(args, i, args.length)));     // List of arguments
    return result;
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
    // MethodHandle for v won't exist at the time that g is invoked.
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
    init.isResultUsed = true;
    varDecl.type = type;
    varDecl.isResultUsed = false;
    varDecl.owner = ownerFunDecl;
    return new Stmt.VarDecl(name, varDecl);
  }
}
