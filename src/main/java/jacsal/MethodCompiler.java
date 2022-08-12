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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.DECIMAL;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.INT;
import static jacsal.JacsalType.LONG;
import static jacsal.JacsalType.STRING;
import static jacsal.TokenType.*;
import static org.objectweb.asm.Opcodes.*;

public class MethodCompiler implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  private final ClassCompiler classCompiler;
  private final MethodVisitor mv;
  private final Stmt.FunDecl  funDecl;
  private       int           currentLineNum = 0;

  private final Deque<Stmt.Block> blocks = new ArrayDeque<>();
  private final List<JacsalType>  locals = new ArrayList<>();

  private static final BigDecimal DECIMAL_MINUS_1 = BigDecimal.valueOf(-1);

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
    @Override public String toString() {
      return "StackEntry[type=" + type + ",isRef=" + isRef + "]";
    }
  }

  MethodCompiler(ClassCompiler classCompiler, Stmt.FunDecl funDecl, MethodVisitor mv) {
    this.classCompiler = classCompiler;
    this.funDecl = funDecl;
    this.mv = mv;
    // Pre-initialise locals to max size allocated
    ntimes(funDecl.maxSlot, () -> locals.add(null));
  }

  void compile() {
    compile(funDecl.block);

    if (stack.size() > 0) {
      throw new IllegalStateException("Internal error: non-empty stack at end of method. Type stack = " + stack);
    }

    if (classCompiler.debug()) {
      mv.visitEnd();
      classCompiler.cv.visitEnd();
    }
    mv.visitMaxs(0, 0);
  }

  /////////////////////////////////////////////

  // Stmt

  private Void compile(Stmt stmt) {
    return stmt.accept(this);
  }

  @Override public Void visitScript(Stmt.Script stmt) {
    throw new IllegalStateException("Internal error: visitScript() should never be called");
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    stmt.stmts.forEach(this::compile);
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    blocks.push(stmt);
    try {
      return compile(stmt.stmts);
    }
    finally {
      // Emit description of local variables for debugger
      if (stmt.variables.size() > 0) {
        Label endBlock = new Label();
        mv.visitLabel(endBlock);
        stmt.variables.values().forEach(v -> {
          mv.visitLocalVariable((String) v.name.getValue(), v.type.descriptor(), null,
                                v.declLabel, endBlock, v.slot);
        });
      }
      blocks.pop();
    }
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    compile(stmt.declExpr);
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    throw new UnsupportedOperationException();
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    return compile(stmt.expr);
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    compile(stmt.expr);
    convertTo(stmt.type);
    pop();
    switch (stmt.type) {
      case BOOLEAN:
      case INT:
        mv.visitInsn(IRETURN);
        break;
      case LONG:
        mv.visitInsn(LRETURN);
        break;
      case DOUBLE:
        mv.visitInsn(DRETURN);
        break;
      case DECIMAL:
      case STRING:
      case INSTANCE:
      case ANY:
        mv.visitInsn(ARETURN);
        break;
    }
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    throw new UnsupportedOperationException();
  }

  /////////////////////////////////////////////

  // Expr

  private Void compile(Expr expr) {
    if (expr.isConst) {
      if (expr.isResultUsed) {
        loadConst(expr.constValue);
        push(expr.type);
      }
      return null;
    }

    if (expr.location.getLineNum() != currentLineNum) {
      currentLineNum = expr.location.getLineNum();
      Label label = new Label();
      mv.visitLabel(label);
      mv.visitLineNumber(currentLineNum, label);
    }
    expr.accept(this);

    if (!expr.isResultUsed) {
      // If result never used then pop is off the stack. Note that VarDecl and VarAssign are
      // special cases since they already check to see whether they should leave something on
      // the stack.
      if (!(expr instanceof Expr.VarDecl || expr instanceof Expr.VarAssign || expr instanceof Expr.PrefixUnary)) {
        popVal();
        pop();
      }
    }
    return null;
  }

  // Map of which opcode to use for common binary operations based on type
  private static final Map<TokenType,List<Object>> opCodesByOperator = Map.of(
    PLUS,    List.of(IADD, LADD, DADD, RuntimeUtils.PLUS),
    MINUS,   List.of(ISUB, LSUB, DSUB, RuntimeUtils.MINUS),
    STAR,    List.of(IMUL, LMUL, DMUL, RuntimeUtils.STAR),
    SLASH,   List.of(IDIV, LDIV, DDIV, RuntimeUtils.SLASH),
    PERCENT, List.of(IREM, LREM, DREM, RuntimeUtils.PERCENT)
  );
  private static final int intIdx = 0;
  private static final int longIdx = 1;
  private static final int doubleIdx = 2;
  private static final int decimalIdx = 3;

  @Override public Void visitVarDecl(Expr.VarDecl expr) {
    mv.visitLabel(expr.declLabel = new Label());   // Label for debugger
    defineVar(expr.slot, expr.type);
    if (expr.initialiser != null) {
      compile(expr.initialiser);
    }
    else {
      loadConst(defaultValue(expr.type));
      push(expr.type);
    }
    convertTo(expr.type);
    pop();
    push(expr.type);

    // If value of assignment used as implicit return then duplicate value
    // before storing it so value is left on stack
    if (expr.isResultUsed) {
      dupVal();
      dup();
    }

    storeLocal(expr.slot);
    pop();
    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    compile(expr.expr);
    convertTo(expr.type);
    pop();
    push(expr.type);
    if (expr.isResultUsed) {
      dupVal();
      dup();
    }
    storeLocal(expr.identifierExpr);
    pop();
    return null;
  }

  @Override public Void visitBinary(Expr.Binary expr) {
    // If we don't know the type of one of the operands then we delegate to RuntimeUtils.binaryOp
    if (expr.left.type.is(ANY) || expr.right.type.is(ANY)) {
      compile(expr.left);
      box();
      compile(expr.right);
      box();
      loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
      loadConst(classCompiler.context.getMaxScale());
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "binaryOp", Object.class, Object.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
      pop();
      pop();
      push(ANY);             // Result of RuntimeUtils.binaryOp is always ANY
      convertTo(expr.type);  // Convert to expected type
      pop();
      push(expr.type);
      return null;
    }

    // String concatenation
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

    // String repeat
    if (expr.operator.is(STAR) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      pop();
      compile(expr.right);
      convertToInt();
      pop();
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "stringRepeat");
      push(expr.type);
      return null;
    }

    // Everything else
    compile(expr.left);
    convertTo(expr.type);
    pop();
    compile(expr.right);
    convertTo(expr.type);
    pop();

    List<Object> opCodes = opCodesByOperator.get(expr.operator.getType());
    if (opCodes == null) {
      throw new UnsupportedOperationException("Unsupported operator " + expr.operator.getType());
    }
    else
    if (expr.type.is(INT, LONG, DOUBLE)) {
      int index = expr.type.is(INT) ? intIdx :
                  expr.type.is(LONG) ? longIdx
                                     : doubleIdx;
      int opCode = (int)opCodes.get(index);
      mv.visitInsn(opCode);
    }
    else
    if (expr.type.is(DECIMAL)) {
      loadConst(opCodes.get(decimalIdx));
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
    boolean resultIsOnStack = true;
    switch (expr.operator.getType()) {
      case BANG:
        compile(expr.expr);
        convertToBoolean(true);
        pop();
        break;
      case MINUS:
        compile(expr.expr);
        arithmeticNegate(expr.expr.location);
        pop();
        break;
      case PLUS:
        compile(expr.expr);
        pop();
        // Nothing to do for unary plus
        break;
      case PLUS_PLUS:
      case MINUS_MINUS:
        resultIsOnStack = false;
        boolean isInc = expr.operator.is(PLUS_PLUS);
        if (expr.expr instanceof Expr.Identifier) {
          Expr.VarDecl varDecl = ((Expr.Identifier) expr.expr).varDecl;
          BiConsumer<Object,Runnable> incOrDec = (plusOrMinusOne, runnable) -> {
            loadLocal(varDecl.slot);
            loadConst(plusOrMinusOne);
            runnable.run();
            storeLocal(varDecl.slot);
          };
          switch (varDecl.type) {
            case INT:
              // Integer vars have special support and can be incremented in place.
              mv.visitIincInsn(varDecl.slot, isInc ? 1 : -1);
              break;
            case LONG:
              incOrDec.accept(isInc ? 1L : -1L, () -> mv.visitInsn(LADD));
              break;
            case DOUBLE:
              incOrDec.accept(isInc ? 1D : -1D, () -> mv.visitInsn(DADD));
              break;
            case DECIMAL:
              BigDecimal amt = isInc ? BigDecimal.ONE : DECIMAL_MINUS_1;
              incOrDec.accept(amt, () -> invokeVirtual(BigDecimal.class, "add", BigDecimal.class));
              break;
            case ANY:
              loadLocal(varDecl.slot);
              incOrDec(isInc, expr.expr.location);
              storeLocal(varDecl.slot);
              break;
            default: throw new IllegalStateException("Internal error: unexpected type " + varDecl.type);
          }
          if (expr.isResultUsed) {
            resultIsOnStack = true;
            loadLocal(varDecl.slot);
          }
        }
        else {
          compile(expr.expr);
          incOrDec(isInc, expr.expr.location);
          pop();
        }
        break;
      default:
        throw new UnsupportedOperationException("Internal error: unknown prefix operator " + expr.operator.getType());
    }

    push(expr.type);
    if (!expr.isResultUsed && resultIsOnStack) {
      popVal();
      pop();
    }
    return null;
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    throw new UnsupportedOperationException();
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    push(expr.type);
    return loadConst(expr.value.getValue());
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) {
    push(expr.type);
    return loadLocal(expr.varDecl.slot);
  }

  @Override
  public Void visitExprString(Expr.ExprString expr) {
    return null;
  }

  ///////////////////////////////////////////////////////////////

  // = Type stack

  private void push(JacsalType type) {
    stack.push(new StackEntry(type, !type.isPrimitive()));
  }

  private StackEntry peek() {
    return stack.peek();
  }

  private StackEntry pop() {
    return stack.pop();
  }

  private void dup() {
    boolean isRef = peek().isRef;
    push(peek().type);
    peek().isRef = isRef;
  }

  /**
   * Duplicate whatever is on the JVM stack.
   * For double and long we need to duplicate top two stack elements
   */
  private void dupVal() {
    mv.visitInsn(peek().type.is(DOUBLE, LONG) ? DUP2 : DUP);
  }

  private void popVal() {
    mv.visitInsn(peek().type.is(DOUBLE, LONG) ? POP2 : POP);
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

  ///////////////////////////////////////////////////////////////

  /**
   * Load constant value onto JVM stack.
   * Supports Boolean, numeric types, BigDecimal, String, and null.
   * @param obj  object to load on stack
   */
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

  private Object defaultValue(JacsalType type) {
    switch (type) {
      case BOOLEAN:  return Boolean.FALSE;
      case INT:      return 0;
      case LONG:     return 0L;
      case DOUBLE:   return 0D;
      case DECIMAL:  return BigDecimal.ZERO;
      case STRING:   return "";
      case INSTANCE: return null;
      case ANY:      return null;
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  ///////////////////////////////////////////////////////////////

  // = Conversions

  private Void convertTo(JacsalType type) {
    switch (type) {
      case BOOLEAN: return convertToBoolean();
      case INT:     return convertToInt();
      case LONG:    return convertToLong();
      case DOUBLE:  return convertToDouble();
      case DECIMAL: return convertToDecimal();
      case STRING:  return convertToString();
      case ANY:     return convertToAny();
      default:      throw new IllegalStateException("Unknown type " + type);
    }
  }

  private Void convertToBoolean() {
    return convertToBoolean(true);
  }

  /**
   * Convert to boolean and leave a 1 or 0 based on whether truthiness is satsified.
   * If negated is true we invert the sense of the conversion so that we leave 0 on
   * the stack for "true" and 1 for "false". This allows us to check for "falsiness"
   * more efficiently.
   * @param negated  if true conversion is negated
   */
  private Void convertToBoolean(boolean negated) {
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

  private Void convertToInt() {
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

  private Void convertToLong() {
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

  private Void convertToDouble() {
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

  private Void convertToDecimal() {
    switch (peek().type) {
      case INT:
      case LONG:
        convertToLong();
        mv.visitMethodInsn(INVOKESTATIC, "java/math/BigDecimal", "valueOf", "(J)Ljava/math/BigDecimal;", false);
        return null;
      case DOUBLE:
        convertToDouble();
        mv.visitMethodInsn(INVOKESTATIC, "java/math/BigDecimal", "valueOf", "(D)Ljava/math/BigDecimal;", false);
        return null;
      case DECIMAL:
        return null;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void convertToAny() {
    box();
    return null;
  }

  private void booleanNot() {
    loadConst(1);
    mv.visitInsn(IXOR);
  }

  private void arithmeticNegate(Token location) {
    switch (peek().type) {
      case INT:     mv.visitInsn(INEG);  break;
      case LONG:    mv.visitInsn(LNEG);  break;
      case DOUBLE:  mv.visitInsn(DNEG);  break;
      case DECIMAL:
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "negate", "()Ljava/math/BigDecimal;", false);
        break;
      case ANY:
        loadConst(classCompiler.source);
        loadConst(location.getOffset());
        invokeStatic(RuntimeUtils.class, "negateNumber", Object.class, String.class, Integer.TYPE);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  /**
   * Increment or decrement by one the current value on the stack. This is used
   * when the increment/decrement doesn't actually alter a variable (e.g. ++1).
   * @param isInc    true if doing inc (false for dec)
   * @param location location of expression we are incrementing or decrementing
   */
  private void incOrDec(boolean isInc, Token location) {
    switch (peek().type) {
      case INT:
        loadConst(isInc ? 1 : -1);
        mv.visitInsn(IADD);
        break;
      case LONG:
        loadConst(isInc ? 1L : -1L);
        mv.visitInsn(LADD);
        break;
      case DOUBLE:
        loadConst(isInc ? 1D : -1D);
        mv.visitInsn(DADD);
        break;
      case DECIMAL:
        loadConst(isInc ? BigDecimal.ONE : DECIMAL_MINUS_1);
        invokeVirtual(BigDecimal.class, "add", BigDecimal.class);
        break;
      case ANY:
        loadConst(classCompiler.source);
        loadConst(location.getOffset());
        invokeStatic(RuntimeUtils.class, isInc ? "incNumber" : "decNumber", Object.class, String.class, Integer.TYPE);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek().type);
    }
  }

  private Void convertToString() {
    if (peek().type.is(STRING)) {
      return null;
    }
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
    return null;
  }

  //////////////////////////////////////////////////////

  // = Method invocation

  /**
   * Emit invokeVirtual call by looking for method with given name (and optionally parameter types).
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of paramter types to narrow down search if multiple methods
   *                   of same name
   */
  private void invokeVirtual(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> !Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), false);
  }

  /**
   * Emit invokeStatic call by looking for method with given name (and optionally parameter types).
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of paramter types to narrow down search if multiple methods
   *                   of same name
   */
  private void invokeStatic(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), false);
  }

  /**
   * Search class for method for given name and given parameter types. If parameter types not supplied
   * then expects that there is only one method of given name.
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of paramter types to narrow down search if multiple methods
   *                   of same name
   */
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

  //////////////////////////////////////////////////////

  // = Local variables

  /**
   * Define a local variable. For long and double we use two slots.
   * @param slot slot
   * @param type JacsalType
   */
  private void defineVar(int slot, JacsalType type) {
    if (locals.get(slot) != null) {
      throw new IllegalStateException("Internal error: local var slot (idx=" + slot + ") already allocated (type=" + locals.get(slot) + ")");
    }
    locals.set(slot, type);
    if (type.is(LONG,DOUBLE)) {
      locals.set(slot + 1, ANY);  // Use ANY to mark slot as used
    }
  }

  /**
   * Undefine variable when no longer needed
   */
  private void undefineVar(int slot) {
    int slots = slotsNeeded(locals.get(slot));
    for (int i = 0; i < slots; i++) {
      locals.set(slot + i, null);
    }
  }

  /**
   * Allocate a temporary value for storing an intermediate value.
   * We grab the first slot available after the maximum one used for the actual
   * local variables declared in the code.
   * Supports allocation of double slots for long and double values.
   * @param type the type to allocate
   * @return the slot allocated
   */
  private int allocateTemp(JacsalType type) {
    int i;
    for (i = funDecl.maxSlot; i < locals.size(); i += slotsNeeded(locals.get(i))) {
      if (slotsFree(i, slotsNeeded(type))) {
        break;
      }
    }
    setSlot(i, type);
    return i;
  }

  /**
   * When no longer needed free up slot(s) used for a temporary value.
   * @param slot the slot where temp resided
   */
  private void freeTemp(int slot) {
    undefineVar(slot);
  }

  private int slotsNeeded(JacsalType type) {
    if (type == null) {
      return 1;
    }
    return type.is(LONG,DOUBLE) ? 2 : 1;
  }

  /**
   * Check if there are num free slots at given index
   */
  private boolean slotsFree(int idx, int num) {
    while (idx < locals.size() && num > 0 && locals.get(idx) == null) {
      idx++;
      num--;
    }
    return num == 0;
  }

  private void setSlot(int idx, JacsalType type) {
    // Grow array if needed
    ntimes(idx + slotsNeeded(type) - locals.size(), () -> locals.add(null));

    // Set first slot to type. For multi-slot types others are marked as ANY.
    for(int i = 0; i < slotsNeeded(type); i++) {
      locals.set(idx + i, i == 0 ? type : ANY);
    }
  }

  private void storeLocal(Expr.Identifier identifier) {
    storeLocal(identifier.varDecl.slot);
  }

  private void storeLocal(int slot) {
    JacsalType type = locals.get(slot);
    switch (type) {
      case BOOLEAN:   mv.visitVarInsn(ISTORE, slot); break;
      case INT:       mv.visitVarInsn(ISTORE, slot); break;
      case LONG:      mv.visitVarInsn(LSTORE, slot); break;
      case DOUBLE:    mv.visitVarInsn(DSTORE, slot); break;

      case DECIMAL:
      case STRING:
      case INSTANCE:
      case ANY:
        mv.visitVarInsn(ASTORE, slot);
        break;

      default: throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  private Void loadLocal(int slot) {
    JacsalType type = locals.get(slot);
    switch (type) {
      case BOOLEAN:   mv.visitVarInsn(ILOAD, slot); break;
      case INT:       mv.visitVarInsn(ILOAD, slot); break;
      case LONG:      mv.visitVarInsn(LLOAD, slot); break;
      case DOUBLE:    mv.visitVarInsn(DLOAD, slot); break;

      case DECIMAL:
      case STRING:
      case INSTANCE:
      case ANY:
        mv.visitVarInsn(ALOAD, slot);
        break;

      default: throw new IllegalStateException("Internal error: unexpected type " + type);
    }
    return null;
  }

  ////////////////////////////////////////////////////////

  // = Misc

  private void ntimes(int count, Runnable runnable) {
    for (int i = 0; i < count; i++) {
      runnable.run();
    }
  }
}
