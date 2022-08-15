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
import static jacsal.JacsalType.BOOLEAN;
import static jacsal.JacsalType.DECIMAL;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.INT;
import static jacsal.JacsalType.LONG;
import static jacsal.JacsalType.STRING;
import static jacsal.TokenType.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * This class is the class that does most of the work from a compilation point of view.
 * It converts the AST for a function/method into byte code.
 *
 * To compile into byte code we keep track of the type of the objects on the JVM stack
 * with a type stack that tries to mirror what would be on the JVM stack at the point
 * where the code is being generated.
 *
 * Methods with names such as loadConst(), loadVar(), storeVar(), dupVal(), popVal(),
 * etc all manipulate the type stack so that the type stack. There are also other versions
 * of some of these methods that begin with '_' such as _loadConst(), _dupVal(), _popVal()
 * that don't touch the type stack. All methods beginning with '_' leave the type stack
 * unchanged.
 */
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
  private Deque<JacsalType> stack = new ArrayDeque<>();

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
    switch (stmt.type.getType()) {
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
      case ANY:
        mv.visitInsn(ARETURN);
        break;
      default: throw new IllegalStateException("Unexpected type " + stmt.type);
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
      if (!(expr instanceof Expr.VarDecl || expr instanceof Expr.VarAssign ||
            expr instanceof Expr.PrefixUnary || expr instanceof Expr.PostfixUnary)) {
        popVal();
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
    if (!expr.isGlobal) {
      mv.visitLabel(expr.declLabel = new Label());   // Label for debugger
      defineVar(expr.slot, expr.type);
    }
    if (expr.initialiser != null) {
      compile(expr.initialiser);
      convertTo(expr.type);
    }
    else {
      loadConst(defaultValue(expr.type));
    }

    // If value of assignment used as implicit return then duplicate value
    // before storing it so value is left on stack
    if (expr.isResultUsed) {
      dupVal();
    }

    storeVar(expr);
    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    compile(expr.expr);
    convertTo(expr.type);
    if (expr.isResultUsed) {
      dupVal();
    }
    storeVar(expr.identifierExpr.varDecl);
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
      loadConst(classCompiler.context.maxScale);
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "binaryOp", Object.class, Object.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
      convertTo(expr.type);  // Convert to expected type
      return null;
    }

    // String concatenation
    if (expr.operator.is(PLUS) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      compile(expr.right);
      convertToString();
      invokeVirtual(String.class, "concat", String.class);
      return null;
    }

    // String repeat
    if (expr.operator.is(STAR) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      compile(expr.right);
      convertToInt();
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "stringRepeat", String.class, Integer.TYPE, String.class, Integer.TYPE);
      return null;
    }

    // Everything else
    compile(expr.left);
    convertTo(expr.type);
    compile(expr.right);
    convertTo(expr.type);

    List<Object> opCodes = opCodesByOperator.get(expr.operator.getType());
    if (opCodes == null) {
      throw new UnsupportedOperationException("Unsupported operator " + expr.operator.getType());
    }
    else
    if (expr.type.isBoxedOrUnboxed(INT, LONG, DOUBLE)) {
      int index = expr.type.isBoxedOrUnboxed(INT)  ? intIdx :
                  expr.type.isBoxedOrUnboxed(LONG) ? longIdx
                                                   : doubleIdx;
      int opCode = (int)opCodes.get(index);
      mv.visitInsn(opCode);
      pop(2);
      push(expr.type);
    }
    else
    if (expr.type.is(DECIMAL)) {
      loadConst(opCodes.get(decimalIdx));
      loadConst(classCompiler.context.maxScale);
      invokeStatic(RuntimeUtils.class, "decimalBinaryOperation", BigDecimal.class, BigDecimal.class, String.class, Integer.TYPE);
    }
    else {
      throw new IllegalStateException("Internal error: unexpected type " + expr.type + " for operator " + expr.operator.getType());
    }
    return null;
  }

  @Override
  public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    if (expr.operator.is(PLUS_PLUS,MINUS_MINUS)) {
      boolean isInc = expr.operator.is(PLUS_PLUS);
      incOrDec(true, isInc, expr.expr, expr.isResultUsed, expr.expr.location);
      return null;
    }

    compile(expr.expr);
    switch (expr.operator.getType()) {
      case BANG:    convertToBoolean(true);        break;
      case MINUS:   arithmeticNegate(expr.expr.location);  break;
      case PLUS:    /* Nothing to do for unary plus */     break;
      default:
        throw new UnsupportedOperationException("Internal error: unknown prefix operator " + expr.operator.getType());
    }
    pop();
    push(expr.type);
    if (!expr.isResultUsed) {
      popVal();
      pop();
    }
    return null;
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    boolean isInc = expr.operator.is(PLUS_PLUS);
    incOrDec(false, isInc, expr.expr, expr.isResultUsed, expr.expr.location);
    return null;
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    return loadConst(expr.value.getValue());
  }

  @Override public Void visitListLiteral(Expr.ListLiteral expr) {
    mv.visitTypeInsn(NEW, "java/util/ArrayList");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
    push(LIST);
    expr.exprs.forEach(entry -> {
      dupVal();
      compile(entry);
      box();
      invokeInterface(List.class, "add", Object.class);
      popVal();    // Pop boolean return value of add
    });
    return null;
  }

  @Override public Void visitMapLiteral(Expr.MapLiteral expr) {
    mv.visitTypeInsn(NEW, "java/util/HashMap");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
    push(MAP);
    expr.entries.forEach(entry -> {
      dupVal();
      compile(entry.x);
      convertTo(STRING);
      compile(entry.y);
      box();
      invokeInterface(Map.class, "put", Object.class, Object.class);
      popVal();    // Ignore return value from put
    });
    return null;
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) {
    loadVar(expr.varDecl);
    return null;
  }

  @Override
  public Void visitExprString(Expr.ExprString expr) {
    _loadConst(expr.exprList.size());
    mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
    for (int i = 0; i < expr.exprList.size(); i++) {
      mv.visitInsn(DUP);
      _loadConst(i);
      compile(expr.exprList.get(i));
      convertTo(STRING);
      mv.visitInsn(AASTORE);
      pop();
    }
    push(ANY);          // For the String[]
    loadConst(""); // Join string
    swap();
    invokeStatic(String.class, "join", CharSequence.class, CharSequence[].class);
    return null;
  }

  ///////////////////////////////////////////////////////////////

  // = Type stack

  private void push(JacsalType type) {
    stack.push(type);
  }

  private void push(Class clss) {
    if (Void.TYPE.equals(clss))        { /* void */                  return; }
    if (Boolean.TYPE.equals(clss))     { push(BOOLEAN);   return; }
    if (Boolean.class.equals(clss))    { push(BOXED_BOOLEAN);        return; }
    if (Integer.TYPE.equals(clss))     { push(INT);                  return; }
    if (Integer.class.equals(clss))    { push(BOXED_INT);            return; }
    if (Long.TYPE.equals(clss))        { push(LONG);                 return; }
    if (Long.class.equals(clss))       { push(BOXED_LONG);           return; }
    if (Double.TYPE.equals(clss))      { push(DOUBLE);               return; }
    if (Double.class.equals(clss))     { push(BOXED_DOUBLE);         return; }
    if (BigDecimal.class.equals(clss)) { push(DECIMAL);              return; }
    if (String.class.equals(clss))     { push(STRING);               return; }
    if (Map.class.equals(clss))        { push(MAP);                  return; }
    if (List.class.equals(clss))       { push(LIST);                 return; }
    push(ANY);
  }

  private JacsalType peek() {
    return stack.peek();
  }

  private JacsalType pop() {
    return stack.pop();
  }

  private void pop(int count) {
    for (int i = 0; i < count; i++) {
      pop();
    }
  }

  private void dup() {
    push(peek());
  }

  /**
   * Swap types on type stack and also swap values on JVM stack
   */
  private void swap() {
    JacsalType type1 = pop();
    JacsalType type2 = pop();
    if (slotsNeeded(type1) == 1 && slotsNeeded(type2) == 1) {
      mv.visitInsn(SWAP);
    }
    else {
      int slot1 = allocateTemp(type1);
      int slot2 = allocateTemp(type2);
      _storeLocal(slot1);
      _storeLocal(slot2);
      _loadLocal(slot1);
      _loadLocal(slot2);
      freeTemp(slot2);
      freeTemp(slot1);
    }
    stack.push(type1);
    stack.push(type2);
  }

  /**
   * Duplicate whatever is on the JVM stack and duplicate type on type stack.
   * For double and long we need to duplicate top two stack elements
   */
  private void dupVal() {
    dup();
    _dupVal();
  }

  /**
   * Duplicate value on JVM stack but don't touch type stack
   */
  private void _dupVal() {
    mv.visitInsn(peek().is(DOUBLE, LONG) ? DUP2 : DUP);
  }

  /**
   * Pop value off JVM stack and corresponding type off type stack
   */
  private void popVal() {
    _popVal();
    pop();
  }

  /**
   * Pop value off JVM stack but don't touch type stack
   */
  private void _popVal() {
    mv.visitInsn(peek().is(DOUBLE, LONG) ? POP2 : POP);
  }

  /**
   * Box value on the stack based on the type
   */
  private void box() {
    box(peek());
    push(pop().boxed());
  }

  private void box(JacsalType type) {
    if (type.isBoxed()) {
      // Nothing to do if not a primitive
      return;
    }
    switch (peek().getType()) {
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

  /**
   * Unbox value on the stack based on the type
   */
  private void unbox() {
    unbox(peek());
    push(pop().unboxed());
  }

  private void unbox(JacsalType type) {
    if (type.isPrimitive()) {
      // Nothing to do if not boxed
      return;
    }
    unboxAlways();
  }

  private void unboxAlways() {
    switch (peek().getType()) {
      case BOOLEAN:
        invokeVirtual(Boolean.class, "booleanValue");
        break;
      case INT:
        invokeVirtual(Integer.class, "intValue");
        break;
      case LONG:
        invokeVirtual(Long.class, "longValue");
        break;
      case DOUBLE:
        invokeVirtual(Double.class, "doubleValue");
        break;
      default:
        // For other types (e.g. DECIMAL) don't do anything if asked to unbox
        return;
    }
    push(pop().unboxed());
  }

  ///////////////////////////////////////////////////////////////

  /**
   * Load constant value onto JVM stack and also push type onto type stack.
   * Supports Boolean, numeric types, BigDecimal, String, and null.
   * @param obj  object to load on stack
   */
  private Void loadConst(Object obj) {
    if (obj == null) {
      mv.visitInsn(ACONST_NULL);
      push(ANY);
    }
    else
    if (obj instanceof Boolean) {
      mv.visitInsn((boolean)obj ? ICONST_1 : ICONST_0);
      push(BOOLEAN);
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
      push(INT);
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
      push(LONG);
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
      push(DOUBLE);
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
      push(DECIMAL);
    }
    else
    if (obj instanceof String) {
      mv.visitLdcInsn((String)obj);
      push(STRING);
    }
    else {
      throw new IllegalStateException("Constant of type " + obj.getClass().getName() + " not supported");
    }
    return null;
  }

  /**
   * Load object onto JVM stack but don't alter type stack
   */
  private void _loadConst(Object obj) {
    loadConst(obj);
    pop();
  }

  private Object defaultValue(JacsalType type) {
    switch (type.getType()) {
      case BOOLEAN:  return Boolean.FALSE;
      case INT:      return 0;
      case LONG:     return 0L;
      case DOUBLE:   return 0D;
      case DECIMAL:  return BigDecimal.ZERO;
      case STRING:   return "";
      case ANY:      return null;
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  ///////////////////////////////////////////////////////////////

  // = Conversions

  private Void convertTo(JacsalType type) {
    switch (type.getType()) {
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
    return convertToBoolean(false);
  }

  /**
   * Convert to boolean and leave a 1 or 0 based on whether truthiness is satisfied.
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
      _loadConst(negated ? 0 : 1);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(isZero);
      _loadConst(negated ? 1 : 0);
      mv.visitLabel(end);
    };

    // For any type that is an Object
    if (!peek().isPrimitive()) {
      loadConst(negated ? true : false);
      invokeStatic(RuntimeUtils.class, "isTruth", Object.class, Boolean.TYPE);
      return null;
    }

    switch (peek().getType()) {
      case BOOLEAN:  if (negated) { _booleanNot(); }      break;
      case INT:      emitConvertToBoolean.accept(IFEQ);   break;
      case LONG: {
        _loadConst((long)0);
        mv.visitInsn(LCMP);      // Leave 0 on stack if equal to 0
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      case DOUBLE: {
        _loadConst((double)0);
        mv.visitInsn(DCMPL);
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }

    pop();
    push(BOOLEAN);
    return null;
  }

  private Void convertToInt() {
    if (peek().isBoxed()) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false);
    }
    else {
      switch (peek().getType()) {
        case INT:                          break;
        case LONG:    mv.visitInsn(L2I);   break;
        case DOUBLE:  mv.visitInsn(D2I);   break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(INT);
    return null;
  }

  private Void convertToLong() {
    if (peek().isBoxed()) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false);
    }
    else {
      switch (peek().getType()) {
        case INT:     mv.visitInsn(I2L);   break;
        case LONG:                         break;
        case DOUBLE:  mv.visitInsn(D2L);   break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(LONG);
    return null;
  }

  private Void convertToDouble() {
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
    }
    else {
      switch (peek().getType()) {
        case INT:     mv.visitInsn(I2D);   break;
        case LONG:    mv.visitInsn(L2D);   break;
        case DOUBLE:                       break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(DOUBLE);
    return null;
  }

  private Void convertToDecimal() {
    switch (peek().getType()) {
      case INT:
      case LONG:
        convertToLong();
        invokeStatic(BigDecimal.class, "valueOf", Long.TYPE);
        break;
      case DOUBLE:
        convertToDouble();
        invokeStatic(BigDecimal.class, "valueOf", Double.TYPE);
        break;
      case DECIMAL:
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
    pop();
    push(DECIMAL);
    return null;
  }

  private Void convertToAny() {
    box();
    return null;
  }

  private void _booleanNot() {
    _loadConst(1);
    mv.visitInsn(IXOR);
  }

  private void arithmeticNegate(Token location) {
    unbox();
    switch (peek().getType()) {
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
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
  }

  private void incOrDec(boolean isPrefix, boolean isInc, Expr expr, boolean isResultUsed, Token location) {
    if (expr instanceof Expr.Identifier) {
      Expr.VarDecl varDecl = ((Expr.Identifier) expr).varDecl;
      // If postfix and we use the value then load value before doing inc/dec so we leave
      // old value on the stack
      if (!isPrefix && isResultUsed) {
        loadVar(varDecl);
      }

      // Helper to do the inc/dec based on the type of the value
      BiConsumer<Object, Runnable> incOrDec = (plusOrMinusOne, runnable) -> {
        loadVar(varDecl);
        unbox();
        loadConst(plusOrMinusOne);
        runnable.run();
        storeVar(varDecl);
      };

      switch (varDecl.type.getType()) {
        case INT:
          if (varDecl.type.isBoxed()) {
            incOrDec.accept(isInc ? 1 : -1, () -> { mv.visitInsn(IADD); pop(); });
          }
          else {
            // Integer vars have special support and can be incremented in place.
            mv.visitIincInsn(varDecl.slot, isInc ? 1 : -1);
          }
          break;
        case LONG:
          incOrDec.accept(isInc ? 1L : -1L, () -> { mv.visitInsn(LADD); pop(); });
          break;
        case DOUBLE:
          incOrDec.accept(isInc ? 1D : -1D, () -> { mv.visitInsn(DADD); pop(); });
          break;
        case DECIMAL:
          BigDecimal amt = isInc ? BigDecimal.ONE : DECIMAL_MINUS_1;
          incOrDec.accept(amt, () -> invokeVirtual(BigDecimal.class, "add", BigDecimal.class));
          break;
        case ANY:
          // When we don't know the type
          loadVar(varDecl);
          incOrDecValue(isInc, location);
          storeVar(varDecl);
          break;
        default:
          throw new IllegalStateException("Internal error: unexpected type " + varDecl.type);
      }
      if (isPrefix && isResultUsed) {
        loadVar(varDecl);
      }
    }
    else {
      compile(expr);
      if (!isPrefix) {
        if (isResultUsed) {
          dupVal();
        }
        incOrDecValue(isInc, location);
        popVal();
      }
      else {
        incOrDecValue(isInc, location);
        convertTo(expr.type);
        if (!isResultUsed) {
          popVal();
        }
      }
    }
  }

  /**
   * Increment or decrement by one the current value on the stack. This is used
   * when the increment/decrement doesn't actually alter a variable (e.g. ++1).
   * @param isInc    true if doing inc (false for dec)
   * @param location location of expression we are incrementing or decrementing
   */
  private void incOrDecValue(boolean isInc, Token location) {
    unbox();
    switch (peek().getType()) {
      case INT:
        _loadConst(isInc ? 1 : -1);
        mv.visitInsn(IADD);
        break;
      case LONG:
        _loadConst(isInc ? 1L : -1L);
        mv.visitInsn(LADD);
        break;
      case DOUBLE:
        _loadConst(isInc ? 1D : -1D);
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
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
  }

  private Void convertToString() {
    if (peek().is(STRING)) {
      return null;
    }
    Label end = null;
    if (peek().isPrimitive()) {
      box();
    }
    invokeStatic(RuntimeUtils.class, "toString", Object.class);
    if (end != null) {
      mv.visitLabel(end);
    }
    return null;
  }

  private void castToBoxedType(JacsalType type) {
    String boxedClass = type.getBoxedClass();
    if (boxedClass != null) {
      mv.visitTypeInsn(CHECKCAST, boxedClass);
    }
  }

  //////////////////////////////////////////////////////

  // = Method invocation

  /**
   * Emit invokeVirtual call by looking for method with given name (and optionally parameter types).
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of parameter types to narrow down search if multiple methods
   *                   of same name
   */
  private void invokeVirtual(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> !Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), false);
    pop(paramTypes.length + 1);        // Pop types for object and each argument
    push(method.getReturnType());
  }

  /**
   * Emit invokeInterface call by looking for method with given name (and optionally parameter types).
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of parameter types to narrow down search if multiple methods
   *                   of same name
   */
  private void invokeInterface(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, m -> !Modifier.isStatic(m.getModifiers()), paramTypes);
    mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), true);
    pop(paramTypes.length + 1);        // Pop types for object and each argument
    push(method.getReturnType());
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
    pop(paramTypes.length);        // Pop types for each argument
    push(method.getReturnType());
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
    try {
      return clss.getDeclaredMethod(methodName, paramTypes);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Internal error: could not find static method " + methodName + " for class " +
                                      clss.getName() + " with param types " + Arrays.toString(paramTypes));
    }
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

  private static int slotsNeeded(JacsalType type) {
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

  private void loadGlobals() {
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, classCompiler.internalName, Utils.JACSAL_GLOBALS_NAME, Type.getDescriptor(Map.class));
    push(JacsalType.MAP);
  }

  private void loadVar(Expr.VarDecl varDecl) {
    if (varDecl.isGlobal) {
      loadGlobals();
      loadConst(varDecl.name.getValue());
      invokeInterface(Map.class, "get", Object.class);
      castToBoxedType(varDecl.type);
      pop();
      push(varDecl.type);
    }
    else {
      loadLocal(varDecl.slot);
    }
  }

  private void storeVar(Expr.VarDecl varDecl) {
    if (varDecl.isGlobal) {
      box();
      loadGlobals();
      swap();
      loadConst(varDecl.name.getValue());
      swap();
      invokeInterface(Map.class, "put", Object.class, Object.class);
      // Pop result of put since we are not interested in previous value
      popVal();
    }
    else {
      storeLocal(varDecl.slot);
    }
  }

  private void storeLocal(int slot) {
    JacsalType type = locals.get(slot);
    if (type.isPrimitive()) {
      unbox();
    }
    _storeLocal(slot);
    pop();
  }

  private void _storeLocal(int slot) {
    JacsalType type = locals.get(slot);
    switch (type.getType()) {
      case BOOLEAN:   mv.visitVarInsn(ISTORE, slot); break;
      case INT:       mv.visitVarInsn(ISTORE, slot); break;
      case LONG:      mv.visitVarInsn(LSTORE, slot); break;
      case DOUBLE:    mv.visitVarInsn(DSTORE, slot); break;

      case DECIMAL:
      case STRING:
      case ANY:
        mv.visitVarInsn(ASTORE, slot);
        break;

      default: throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  private Void loadLocal(int slot) {
    _loadLocal(slot);
    JacsalType type = locals.get(slot);
    push(type);
    return null;
  }

  private void _loadLocal(int slot) {
    JacsalType type = locals.get(slot);
    switch (type.getType()) {
      case BOOLEAN:   mv.visitVarInsn(ILOAD, slot); break;
      case INT:       mv.visitVarInsn(ILOAD, slot); break;
      case LONG:      mv.visitVarInsn(LLOAD, slot); break;
      case DOUBLE:    mv.visitVarInsn(DLOAD, slot); break;

      case DECIMAL:
      case STRING:
      case ANY:
        mv.visitVarInsn(ALOAD, slot);
        break;

      default: throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  ////////////////////////////////////////////////////////

  // = Misc

  private void ntimes(int count, Runnable runnable) {
    for (int i = 0; i < count; i++) {
      runnable.run();
    }
  }
}
