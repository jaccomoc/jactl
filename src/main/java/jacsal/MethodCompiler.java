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

import jacsal.runtime.RuntimeUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.STRING;
import static jacsal.TokenType.*;
import static org.objectweb.asm.Opcodes.*;

public class MethodCompiler implements Expr.Visitor<Void> {

  private ClassCompiler classCompiler;
  private MethodVisitor mv;
  private Expr          expr;

  // As we generate the byte code we keep a stack where we track the type of the
  // value that would currently be on the JVM stack at that point in the generated
  // code. This allows us to know when to do appropriate conversions.
  private Deque<StackEntry> stack = new ArrayDeque<>();
  private static class StackEntry {
    JacsalType type;
    boolean    isRef;   // true if object reference (including boxed primitives)
    StackEntry(JacsalType type, boolean isRef) {
      this.type  = type;
      this.isRef = isRef;
    }
  }

  MethodCompiler(ClassCompiler classCompiler, Expr expr, MethodVisitor mv) {
    this.classCompiler = classCompiler;
    this.expr = expr;
    this.mv = mv;
  }

  void compile() {
    expr.accept(this);

    if (stack.size() > 1) {
      throw new IllegalStateException("Internal error: multiple types on type stack at end of method. Type stack = " + stack);
    }

    if (stack.isEmpty()) {
      mv.visitInsn(ACONST_NULL);
    }
    else {
      // Box primitive if necessary
      box();
    }
    mv.visitInsn(ARETURN);

    if (classCompiler.debug()) {
      mv.visitEnd();
      classCompiler.cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
  }

  /////////////////////////////////////////////

  private Void compile(Expr expr) {
    return expr.accept(this);
  }

  // Map of which opcode to use for common binary operations based on type
  private static final Map<TokenType,List<Object>> opCodesByOperator = Map.of(
    PLUS,    List.of(IADD, LADD, DADD, "add"),
    MINUS,   List.of(ISUB, LSUB, DSUB, "subtract"),
    STAR,    List.of(IMUL, LMUL, DMUL, "multiply"),
    SLASH,   List.of(IDIV, LDIV, DDIV, "divide"),
    PERCENT, List.of(IREM, LREM, DREM, "remainder")
  );
  private static final int intIdx = 0;
  private static final int longIdx = 1;
  private static final int doubleIdx = 2;
  private static final int decimalIdx = 3;

  @Override
  public Void visitBinary(Expr.Binary expr) {
    if (expr.isConst) {
      loadConst(expr.constValue);
      push(expr.type);
      return null;
    }

    if (expr.left.type.is(ANY) || expr.right.type.is(ANY)) {
      throw new UnsupportedOperationException("Dynamic typing not yet supported");
    }

    if (expr.operator.is(PLUS) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      pop();
      compile(expr.right);
      convertToString();
      pop();
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
      push(expr.type);
      return null;
    }

    if (expr.operator.is(STAR) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      pop();
      compile(expr.right);
      castToInt();
      pop();
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "stringRepeat");
      push(expr.type);
      return null;
    }

    compile(expr.left);
    castTo(expr.type);
    pop();
    compile(expr.right);
    castTo(expr.type);
    pop();

    List<Object> opCodes = opCodesByOperator.get(expr.operator.getType());
    if (opCodes == null) {
      throw new UnsupportedOperationException("Unsupported operator " + expr.operator.getType());
    }
    else
    if (expr.type.is(JacsalType.INT,JacsalType.LONG,JacsalType.DOUBLE)) {
      int index = expr.type.is(JacsalType.INT)  ? intIdx :
                  expr.type.is(JacsalType.LONG) ? longIdx
                                                : doubleIdx;
      int opCode = (int)opCodes.get(index);
      mv.visitInsn(opCode);
    }
    else
    if (expr.type.is(DECIMAL)) {
      loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
      loadConst(classCompiler.context.getMaxScale());
      invokeStatic(RuntimeUtils.class, "decimalBinaryOperation");
    }
    else {
      throw new IllegalStateException("Internal error: unexpected type " + expr.type + " for operator " + expr.operator.getType());
    }

    push(expr.type);
    return null;
  }

  @Override
  public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    if (expr.isConst) {
      loadConst(expr.constValue);
      push(expr.type);
      return null;
    }

    compile(expr.expr);
    switch (expr.operator.getType()) {
      case BANG:
        castToBoolean(true);
        break;
      case MINUS:
        arithmeticNegate();
        break;
      case PLUS:
        // Nothing to do for unary plus
        break;
      default:
        throw new UnsupportedOperationException("Internal error: unknown prefix operator " + expr.operator.getType());
    }

    pop();
    push(expr.type);
    return null;
  }

  @Override
  public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    throw new UnsupportedOperationException();
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    push(expr.type);
    return loadConst(expr.value.getValue());
  }

  @Override
  public Void visitIdentifier(Expr.Identifier expr) {
    return null;
  }

  @Override
  public Void visitExprString(Expr.ExprString expr) {
    return null;
  }

  //////////////////////////////////////

  private void push(JacsalType type) {
    stack.push(new StackEntry(type, !type.isPrimitive()));
  }

  private StackEntry peek() {
    return stack.peek();
  }

  private StackEntry pop() {
    return stack.pop();
  }

  /**
   * Duplicate whatever is on the JVM stack.
   * For double and long we need to duplicate top two stack elements
   */
  private void dupVal() {
    mv.visitInsn(peek().type.is(JacsalType.DOUBLE,JacsalType.LONG) ? DUP2 : DUP);
  }

  private void popVal() {
    mv.visitInsn(peek().type.is(JacsalType.DOUBLE,JacsalType.LONG) ? POP2 : POP);
  }

  /**
   * Box value on the stack based on the type
   */
  private void box() {
    if (peek().isRef) {
      // Nothing to do if not a primitive
      return;
    }
    switch (peek().type) {
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
    peek().isRef = true;
  }

  /**
   * Unbox value on the stack based on the type
   */
  private void unbox() {
    if (!peek().isRef) {
      // Nothing to do if not boxed
      return;
    }
    switch (peek().type) {
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
      default:
        // For other types (e.g. DECIMAL) don't do anything if asked to unbox
        return;
    }
    peek().isRef = false;
  }

  private Void loadConst(Object obj) {
    if (obj == null) {
      mv.visitInsn(ACONST_NULL);
    }
    else
    if (obj instanceof Boolean) {
      mv.visitInsn((boolean)obj ? ICONST_1 : ICONST_0);
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
    }
    else
    if (obj instanceof String) {
      mv.visitLdcInsn((String)obj);
    }
    else {
      throw new IllegalStateException("Constant of type " + obj.getClass().getName() + " not supported");
    }
    return null;
  }

  private Void castTo(JacsalType type) {
    switch (type) {
      case BOOLEAN: return castToBoolean();
      case INT:     return castToInt();
      case LONG:    return castToLong();
      case DOUBLE:  return castToDouble();
      case DECIMAL: return castToDecimal();
      case STRING:  return castToString();
      case ANY:     return castToAny();
      default:      throw new IllegalStateException("Unknown type " + type);
    }
  }

  private Void castToBoolean() {
    return castToBoolean(true);
  }

  /**
   * Convert to boolean and leave a 1 or 0 based on whether truthiness is satsified.
   * If negated is true we invert the sense of the conversion so that we leave 0 on
   * the stack for "true" and 1 for "false". This allows us to check for "falsiness"
   * more efficiently.
   * @param negated  if true conversion is negated
   */
  private Void castToBoolean(boolean negated) {
    Consumer<Integer> emitConvertToBoolean = opCode -> {
      Label isZero = new Label();
      Label end    = new Label();
      mv.visitJumpInsn(opCode, isZero);
      loadConst(negated ? 0 : 1);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(isZero);
      loadConst(negated ? 1 : 0);
      mv.visitLabel(end);
    };

    Label endCast = null;   // Used for boxed types and strings

    // For any type that is an Object check first for null
    if (peek().isRef) {
      if (!peek().type.isNumeric() && !peek().type.is(BOOLEAN,STRING)) {
        // For non-numeric and non-string types we are false if null and true otherwise
        emitConvertToBoolean.accept(IFNULL);
        return null;
      }

      // Since we will check for 0 or empty string if not null we need to duplicate the
      // value since the null check will swallow whatever is on the stack
      dupVal();
      endCast = new Label();
      Label nonNull = new Label();
      mv.visitJumpInsn(IFNONNULL, nonNull);
      popVal();
      loadConst(negated ? 1 : 0);
      mv.visitJumpInsn(GOTO, endCast);
      mv.visitLabel(nonNull);

      if (peek().type.is(STRING)) {
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
        mv.visitLabel(endCast);
        return null;
      }

      // For boxed primitives unbox first
      unbox();
    }

    switch (peek().type) {
      case BOOLEAN:  if (negated) { booleanNot(); }       break;
      case INT:      emitConvertToBoolean.accept(IFEQ);   break;
      case LONG: {
        loadConst((long)0);
        mv.visitInsn(LCMP);      // Leave 0 on stack if equal to 0
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      case DOUBLE: {
        loadConst((double)0);
        mv.visitInsn(DCMPL);
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      case DECIMAL: {
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "stripTrailingZeros", "()Ljava/math/BigDecimal;", false);
        loadConst(BigDecimal.ZERO);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "equals", "(Ljava/lang/Object;)Z", false);
        if (!negated) {
          booleanNot();
        }
        break;
      }
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }

    if (endCast != null) {
      mv.visitLabel(endCast);
    }

    return null;
  }

  private Void castToInt() {
    if (peek().isRef) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false);
      return null;
    }

    switch (peek().type) {
      case INT:                          return null;
      case LONG:    mv.visitInsn(L2I);   return null;
      case DOUBLE:  mv.visitInsn(D2I);   return null;
      default:     throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void castToLong() {
    if (peek().isRef) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false);
      return null;
    }

    switch (peek().type) {
      case INT:     mv.visitInsn(I2L);   return null;
      case LONG:                         return null;
      case DOUBLE:  mv.visitInsn(D2L);   return null;
      default:     throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void castToDouble() {
    if (peek().isRef) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
      return null;
    }

    switch (peek().type) {
      case INT:     mv.visitInsn(I2D);   return null;
      case LONG:    mv.visitInsn(L2D);   return null;
      case DOUBLE:                       return null;
      default:     throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void castToDecimal() {
    switch (peek().type) {
      case INT:
      case LONG:
        castToLong();
        mv.visitMethodInsn(INVOKESTATIC, "java/math/BigDecimal", "valueOf", "(J)Ljava/math/BigDecimal;", false);
        return null;
      case DOUBLE:
        castToDouble();
        mv.visitMethodInsn(INVOKESTATIC, "java/math/BigDecimal", "valueOf", "(D)Ljava/math/BigDecimal;", false);
        return null;
      case DECIMAL:
        return null;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void castToString() {
    throw new UnsupportedOperationException("Cast to string not supported");
  }

  private Void castToAny() {
    box();
    return null;
  }

  private void booleanNot() {
    loadConst(1);
    mv.visitInsn(IXOR);
  }

  private void arithmeticNegate() {
    switch (peek().type) {
      case INT:     mv.visitInsn(INEG);  break;
      case LONG:    mv.visitInsn(LNEG);  break;
      case DOUBLE:  mv.visitInsn(DNEG);  break;
      case DECIMAL:
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "negate", "()Ljava/math/BigDecimal;", false);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private void convertToString() {
    Label end = null;
    if (!peek().isRef) {
      box();
    }
    else {
      dupVal();
      Label nonNull = new Label();
      end = new Label();
      mv.visitJumpInsn(IFNONNULL, nonNull);
      popVal();
      loadConst("null");
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(nonNull);
    }
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
    if (end != null) {
      mv.visitLabel(end);
    }
  }

  private void invokeVirtual(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> !Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), false);
  }

  private void invokeStatic(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), false);
  }

  private Method findMethod(Class<?> clss, String methodName, Function<Method,Boolean> filter, Class<?>... paramTypes) {
    Method method = null;
    try {
      method = clss.getDeclaredMethod(methodName, paramTypes);
    }
    catch (NoSuchMethodException e) {
      if (paramTypes.length == 0) {
        Optional<Method> optionalMethod = Arrays.stream(clss.getDeclaredMethods())
                                                .filter(filter::apply)
                                                .filter(m -> m.getName().equals(methodName))
                                                .findFirst();
        if (optionalMethod.isPresent()) {
          method = optionalMethod.get();
        }
      }
    }
    if (method == null) {
      throw new IllegalStateException("Internal error: could not find static method " + methodName + " for class " + clss.getName());
    }
    return method;
  }
}
