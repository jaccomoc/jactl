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

import jacsal.runtime.Location;
import jacsal.runtime.NullError;
import jacsal.runtime.RuntimeUtils;
import jacsal.runtime.SourceLocation;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.BOOLEAN;
import static jacsal.JacsalType.DECIMAL;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.INT;
import static jacsal.JacsalType.LIST;
import static jacsal.JacsalType.LONG;
import static jacsal.JacsalType.MAP;
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
  private final Expr.FunDecl  funDecl;
  private       int           currentLineNum = 0;
  private       int           minimumSlot = 0;

  private final Deque<Stmt.Block> blocks = new ArrayDeque<>();
  private final List<JacsalType>  locals = new ArrayList<>();

  private static final BigDecimal DECIMAL_MINUS_1 = BigDecimal.valueOf(-1);

  // As we generate the byte code we keep a stack where we track the type of the
  // value that would currently be on the JVM stack at that point in the generated
  // code. This allows us to know when to do appropriate conversions.
  private Deque<JacsalType> stack = new ArrayDeque<>();

  MethodCompiler(ClassCompiler classCompiler, Expr.FunDecl funDecl, MethodVisitor mv) {
    this.classCompiler = classCompiler;
    this.funDecl = funDecl;
    this.mv = mv;
  }

  void compile() {
    // Allow room for object instance at idx 0 and optional heap vars array at idx 1.
    // We know we need heap vars array passed to us if our top level access level is
    // less than our current level (i.e. we access vars from one of our parents).
    if (!funDecl.isStatic) {
      allocateSlot(ANY);     // Object reference
    }

    // Allocate slots for heap local vars passed to us as implicit parameters from
    // parent function
    //funDecl.heapVars.forEach((name,varDecl) -> defineVar(varDecl));

    // Compile statements.
    // NOTE: the first statements will be the parameter declarations which will allocate
    //       the slots for the parameters
    compile(funDecl.block);

    Label endBlock = new Label();
    mv.visitLabel(endBlock);
    //funDecl.heapVars.forEach((name,varDecl)-> undefineVar(varDecl, endBlock));

    if (classCompiler.debug()) {
      mv.visitEnd();
      classCompiler.cv.visitEnd();
    }
    mv.visitMaxs(0, 0);

    check(stack.isEmpty(), "non-empty stack at end of method " + funDecl.methodName + ". Type stack = " + stack);
  }

  /////////////////////////////////////////////

  /**
   * Compile method that handles vararg invocation of actual method. This wrapper method
   * takes an array or a map of arguments and fills in values for missing args before
   * invoking the real method with the exact arguments required.
   */
  void compileWrapper() {
    if (!funDecl.isStatic) {
      allocateSlot(ANY);     // Slot 0 is always "this" for non-static methods
    }

    int sourceSlot = allocateSlot(STRING);
    int offsetSlot = allocateSlot(INT);

    // Args passed as Object and we then check type and store as correct type into another slot
    int arg          = allocateSlot(ANY);   // args as Object
    int arraySlot    = allocateSlot(ANY);   // If args passed as Object[]
    int mapSlot      = allocateSlot(MAP);   // If args passed as Map
    int argCountSlot = allocateSlot(INT);   // Count of args

    loadLocal(0);       // "this" must be first on stack for later invocation

    Label checkType = new Label();
    _loadLocal(arg);
    mv.visitTypeInsn(INSTANCEOF, "[Ljava/lang/Object;");
    Label isMap = new Label();
    mv.visitJumpInsn(IFEQ, isMap);

    // Is Object[] so check count and fill in additional args or throw exception if missing mandatory args
    _loadLocal(arg);
    mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
    _storeLocal(arraySlot);
    _loadLocal(arraySlot);
    mv.visitInsn(ARRAYLENGTH);
    _storeLocal(argCountSlot);
    int mandatoryCount = Utils.mandatoryParamCount(funDecl.parameters);
    Label enoughArgs = new Label();
    if (mandatoryCount > 0) {
      _loadLocal(argCountSlot);
      _loadConst(mandatoryCount);
      mv.visitJumpInsn(IF_ICMPGE, enoughArgs);
      throwError("Missing mandatory arguments", sourceSlot, offsetSlot);
    }
    mv.visitLabel(enoughArgs);   // :enoughArgs
    Label notTooMany = new Label();
    _loadLocal(argCountSlot);
    int paramCount = funDecl.parameters.size();
    _loadConst(paramCount);
    mv.visitJumpInsn(IF_ICMPLE, notTooMany);
    throwError("Too many args", sourceSlot, offsetSlot);
    mv.visitLabel(notTooMany);  // :notTooMany

    // For each parameter we now either load argument from Object[] or put default value
    // onto stack depending on number of arguments passed in Object[]
    // If a parameter has a default value but is mandatory (when passing Object[]) due
    // to subsequent parameters not having a default then we don't bother compiling the
    // code for its default value - we will for named parameter passing (see below) but
    // not for Object[] arg passing since we will never use it.
    for (int i = 0; i < paramCount; i++) {
      // We have already checked that we have enough args so for mandatory args we
      // just load the value without needing to check whether arg is present. We only
      // need check whether we have the argument if this is an optional arg.
      Label useDefaultValue = new Label();
      boolean isOptional    = i >= mandatoryCount;
      if (isOptional) {
        // If we have the argument load it from Object[]
        _loadConst(i);
        _loadLocal(argCountSlot);
        mv.visitJumpInsn(IF_ICMPGE, useDefaultValue);
      }

      // Load value from Object[]
      _loadLocal(arraySlot);
      _loadConst(i);
      mv.visitInsn(AALOAD);
      push(ANY);
      convertTo(funDecl.parameters.get(i).declExpr.type, true, location(sourceSlot, offsetSlot));

      if (isOptional) {
        pop();  // We will put same type back on stack when we load default value
        Label nextArg = new Label();
        mv.visitJumpInsn(GOTO, nextArg);

        // Load default value
        mv.visitLabel(useDefaultValue);   // :useDefaultValue
        Stmt.VarDecl param = funDecl.parameters.get(i);
        compile(param.declExpr.initialiser);
        convertTo(param.declExpr.type, true, param.declExpr.initialiser.location);
        mv.visitLabel(nextArg);           // :nextArg
      }
    }

    Label invocation = new Label();
    mv.visitJumpInsn(GOTO, invocation);

    // TODO: save stack so we can check against other code path
    //Deque<JacsalType> stackCopy = stack;
    //stack = new ArrayDeque<>();

    ////////////////////////////////

    mv.visitLabel(isMap);           // :isMap
    mv.visitInsn(POP);     // For the moment we don't need "this"
    _loadLocal(arg);
    mv.visitTypeInsn(CHECKCAST, "java/util/Map");
    _storeLocal(mapSlot);
    throwError("Named parameter invoction not yet supported", location(sourceSlot, offsetSlot));

    // TODO: check stacks match
    //check(equals(stack, stackCopy), "Mismatch in type stack for Object[] and Map paths");

    /////////////////////////////////

    mv.visitLabel(invocation);     // :invocation
    invokeMethod(funDecl);
    convertToAny();
    emitReturn(ANY);        // Wrapper has to return ANY since when invoked as MethodHandle we
                            // have no idea what its real signature is
    pop();

    if (classCompiler.debug()) {
      mv.visitEnd();
      classCompiler.cv.visitEnd();
    }
    mv.visitMaxs(0, 0);

    check(stack.isEmpty(), "non-empty stack at end of wrapper method " + funDecl.methodName + ". Type stack = " + stack);
  }

  /////////////////////////////////////////////

  // Stmt

  private Void compile(Stmt stmt) {
    if (stmt == null) { return null; }
    return stmt.accept(this);
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
        stmt.variables.values().forEach(v -> undefineVar(v, endBlock));
      }
      blocks.pop();
    }
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    compile(stmt.declExpr);
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    compile(stmt.declExpr);
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    return compile(stmt.expr);
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    compile(stmt.expr);
    convertTo(stmt.returnType, true, stmt.expr.location);
    pop();
    emitReturn(stmt.returnType);
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    compile(stmt.condtion);
    convertTo(BOOLEAN, true, stmt.condtion.location);
    pop();
    Label ifFalse = new Label();
    mv.visitJumpInsn(IFEQ, ifFalse);
    compile(stmt.trueStmt);
    Label end = new Label();
    if (stmt.falseStmt != null) {
      mv.visitJumpInsn(GOTO, end);
    }
    mv.visitLabel(ifFalse);
    compile(stmt.falseStmt);
    mv.visitLabel(end);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    stmt.endLoopLabel = new Label();
    Label loop = new Label();
    if (stmt.updates == null) {
      stmt.continueLabel = loop;
    }
    else {
      stmt.continueLabel = new Label();
    }
    mv.visitLabel(loop);
    compile(stmt.condition);
    convertTo(BOOLEAN, true, stmt.condition.location);
    pop();
    mv.visitJumpInsn(IFEQ, stmt.endLoopLabel);
    compile(stmt.body);
    if (stmt.updates != null) {
      mv.visitLabel(stmt.continueLabel);
      compile(stmt.updates);
    }
    mv.visitJumpInsn(GOTO, loop);
    mv.visitLabel(stmt.endLoopLabel);
    return null;
  }

  @Override public Void visitBreak(Stmt.Break stmt) {
    mv.visitJumpInsn(GOTO, stmt.whileLoop.endLoopLabel);
    return null;
  }

  @Override public Void visitContinue(Stmt.Continue stmt) {
    mv.visitJumpInsn(GOTO, stmt.whileLoop.continueLabel);
    return null;
  }

  @Override public Void visitPrint(Stmt.Print stmt) {
    compile(stmt.expr);
    convertTo(STRING, true, stmt.expr.location);
    invokeStatic(RuntimeUtils.class, stmt.printToken.getChars(), Object.class);
    return null;
  }

  @Override
  public Void visitClassDecl(Stmt.ClassDecl stmt) {
    return null;
  }

  /////////////////////////////////////////////

  // Expr

  private Void compile(Expr expr) {
    if (expr == null) {
      return null;
    }
    if (expr.isConst) {
      if (expr.isResultUsed) {
        loadConst(expr.constValue);
      }
      return null;
    }

    if (expr.location != null && expr.location.getLineNum() != currentLineNum) {
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
      if (!(expr instanceof Expr.ManagesResult)) {
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
      defineVar(expr);
    }
    if (expr.isParam) {
      return null;     // Nothing else to do since value is already stored for parameters
    }

    // If actual function (not closure)
    if (expr.type.is(FUNCTION) && expr.funDecl != null) {
      // Value of the variable will be the handle for the function
      loadClassField(expr.funDecl.isStatic ? Utils.staticHandleName(expr.funDecl.methodName)
                                           : Utils.handleName(expr.funDecl.methodName),
                     FUNCTION,
                     expr.funDecl.isStatic);
    }
    else
    if (expr.initialiser != null) {
      compile(expr.initialiser);
      convertTo(expr.type, true, expr.initialiser.location);
    }
    else {
      loadDefaultValue(expr.type);
    }

    // If value of assignment used as implicit return then duplicate value
    // before storing it so value is left on stack
    if (expr.isResultUsed) {
      dupVal();
    }

    storeVar(expr);
    return null;
  }

  @Override public Void visitFunDecl(Expr.FunDecl expr) {
    compile(expr.varDecl);
    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    boolean isConditionalAssign = expr.operator.is(QUESTION_EQUAL);

    // If doing ?= then if rhs is null or throws a NullError we don't perform
    // the assignment
    if (isConditionalAssign) {
      Label end = new Label();
      tryCatch(NullError.class,
               // Try block: get rhs value and if not null assign
               () -> {
                 compile(expr.expr);

                 // Only need check for null value if not a primitive
                 if (!peek().isPrimitive()) {
                   _dupVal();
                   // If non null then jump to assignment
                   Label haveRhsValue = new Label();
                   mv.visitJumpInsn(IFNONNULL, haveRhsValue);
                   // Pop null off stack if we don't need a value as a result
                   if (!expr.isResultUsed) {
                     _popVal();
                   }
                   mv.visitJumpInsn(GOTO, end);
                   mv.visitLabel(haveRhsValue);    // :haveRhsValue
                 }

                 // If conditional assign then we know by now whether we have a null value or not
                 // so we don't need to check again when checking for type conversion
                 convertTo(expr.identifierExpr.type, !isConditionalAssign, expr.expr.location);
                 if (expr.isResultUsed) {
                   dupVal();
                 }
                 storeVar(expr.identifierExpr.varDecl);
                 if (expr.isResultUsed) {
                   // Need to box if primitive because when we don't do the assign due to NullError
                   // we leave null as the result so we need to box so that type of entire expression
                   // is compatible with use of null
                   box();
                   pop();
                   push(expr.type);
                 }
               },

               // Catch block: if NullError thrown then don't assign and return
               // null if result needed
               () -> {
                 if (expr.isResultUsed) {
                   // Need a null value if no value from rhs
                   loadNull(expr.type);
                 }
               });
      mv.visitLabel(end);
    }
    else {
      // Normal assignment
      compile(expr.expr);
      convertTo(expr.type, true, expr.expr.location);
      if (expr.isResultUsed) {
        dupVal();
      }
      storeVar(expr.identifierExpr.varDecl);
    }

    return null;
  }

  @Override public Void visitVarOpAssign(Expr.VarOpAssign expr) {
    // If we have += or -= with a const value then there are optimisations that are possible
    // (especially if local variable of type int).
    if (expr.operator.is(PLUS_EQUAL,MINUS_EQUAL,PLUS_PLUS,MINUS_MINUS) &&
        !expr.identifierExpr.varDecl.isGlobal    &&
        expr.expr.right.isConst) {
      incOrDecVar(true, expr.operator.is(PLUS_EQUAL,PLUS_PLUS), expr.identifierExpr, expr.expr.right.constValue, expr.isResultUsed, expr.operator);
      return null;
    }

    loadVar(expr.identifierExpr.varDecl);
    compile(expr.expr);
    convertTo(expr.type, true, expr.expr.location);
    if (expr.isResultUsed) {
      dupVal();
    }
    storeVar(expr.identifierExpr.varDecl);
    return null;
  }

  @Override public Void visitNoop(Expr.Noop expr) {
    // Nothing to do
    return null;
  }

  @Override public Void visitAssign(Expr.Assign expr) {
    if (expr.assignmentOperator.is(QUESTION_EQUAL)) {
      Label end = new Label();
      tryCatch(NullError.class,
               // Try block:
               () -> {
                 compile(expr.expr);

                 // Only check for null if not primitive
                 if (!peek().isPrimitive()) {
                   _dupVal();
                   // If non null then jump to assignment
                   Label haveRhsValue = new Label();
                   mv.visitJumpInsn(IFNONNULL, haveRhsValue);
                   // Pop null off stack if we don't need a value as a result
                   if (!expr.isResultUsed) {
                     _popVal();
                   }
                   mv.visitJumpInsn(GOTO, end);
                   mv.visitLabel(haveRhsValue);    // :haveRhsValue
                 }

                 // If conditional assign then we know by now whether we have a null value or not
                 // so we don't need to check again when checking for type conversion
                 convertTo(expr.type, false, expr.expr.location);
                 if (expr.isResultUsed) {
                   dupVal();
                 }

                 compile(expr.parent);
                 swap();
                 compile(expr.field);
                 box();
                 swap();
                 storeField(expr.accessType);

                 if (expr.isResultUsed) {
                   // Just in case our field is a primitive we need to leave a boxed result on stack
                   // to be compatible with non-assigment case when we leave null on the stack
                   box();
                 }
               },
               // Catch block:
               () -> {
                 if (expr.isResultUsed) {
                   // Need a null value if no value from rhs
                   loadNull(expr.type);
                 }
               });
      mv.visitLabel(end);
    }
    else {
      compile(expr.expr);
      convertTo(ANY, true, expr.expr.location);    // Convert to ANY since all map/list entries are ANY
      if (expr.isResultUsed) {
        dupVal();
      }
      compile(expr.parent);
      swap();
      compile(expr.field);
      box();
      swap();
      storeField(expr.accessType);
    }
    return null;
  }

  @Override public Void visitOpAssign(Expr.OpAssign expr) {
    compile(expr.parent);
    compile(expr.field);
    box();

    // Since assignment is +=, -=, etc (rather than just '=' or '?=') we will need parent
    // and field again to get value and to store value so duplicate parent and field on
    // stack to get:
    // ... parent, field, parent, field
    dupVal2();

    Expr.Binary valueExpr = (Expr.Binary) expr.expr;

    // Load the field value onto stack (or suitable default value).
    // Default value will be based on the type of the rhs of the += or -= etc.
    loadField(expr.accessType, false, null, RuntimeUtils.DEFAULT_VALUE);

    // If we need the before value as the result then stash in a temp for later
    int temp = -1;
    if (expr.isResultUsed && expr.isPreIncOrDec) {
      dupVal();
      temp = allocateSlot(expr.type);
      storeLocal(temp);
    }

    // Evaluate expression
    compile(valueExpr);
    convertTo(expr.type, true, expr.location);

    // If we need the after result then stash in a temp
    if (expr.isResultUsed && !expr.isPreIncOrDec) {
      dupVal();
      temp = allocateSlot(expr.type);
      storeLocal(temp);
    }

    // Store result back into field
    storeField(expr.accessType);

    // Retrieve the required value as result if used
    if (expr.isResultUsed) {
      loadLocal(temp);
      freeTemp(temp);
    }

    return null;
  }

  private static TokenType[] numericOperator = new TokenType[] {PLUS, MINUS, STAR, SLASH, PERCENT };

  @Override public Void visitBinary(Expr.Binary expr) {
    // If we don't know the type of one of the operands then we delegate to RuntimeUtils.binaryOp
    if (expr.operator.is(numericOperator) && (expr.left.type.is(ANY) || expr.right.type.is(ANY))) {
      compile(expr.left);
      box();
      compile(expr.right);
      box();
      loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
      loadConst(expr.originalOperator == null ? null : RuntimeUtils.getOperatorType(expr.originalOperator.getType()));
      loadConst(classCompiler.context.maxScale);
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "binaryOp", Object.class, Object.class, String.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
      convertTo(expr.type, true, expr.operator);  // Convert to expected type
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
      convertTo(INT, true, expr.right.location);
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "stringRepeat", String.class, Integer.TYPE, String.class, Integer.TYPE);
      return null;
    }

    // Check for Map/List/String access via field/index
    if (expr.operator.is(DOT,QUESTION_DOT,LEFT_SQUARE,QUESTION_SQUARE)) {
      compile(expr.left);
      compile(expr.right);
      loadField(expr.operator, expr.createIfMissing, expr.type, null);
      return null;
    }

    // Boolean && or ||
    if (expr.operator.is(AMPERSAND_AMPERSAND,PIPE_PIPE)) {
      compile(expr.left);
      convertTo(BOOLEAN, true, expr.left.location);

      // Look for short-cutting && or || (i.e. if we already know the result
      // we don't need to evaluate right hand side)
      if (expr.operator.is(AMPERSAND_AMPERSAND)) {
        // Handle case for &&
        Label isFalse = new Label();
        Label end     = new Label();
        mv.visitJumpInsn(IFEQ, isFalse);    // If first operand is false
        pop();
        compile(expr.right);
        convertTo(BOOLEAN, true, expr.right.location);
        mv.visitJumpInsn(IFEQ, isFalse);
        pop();
        _loadConst(true);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(isFalse);            // :isFalse
        _loadConst(false);
        mv.visitLabel(end);
      }
      else {
        // Handle case for ||
        Label end    = new Label();
        Label isTrue = new Label();
        mv.visitJumpInsn(IFNE, isTrue);
        pop();
        compile(expr.right);
        convertTo(BOOLEAN, true, expr.right.location);
        pop();
        mv.visitJumpInsn(IFNE, isTrue);
        _loadConst(false);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(isTrue);
        _loadConst(true);
        mv.visitLabel(end);
      }
      push(BOOLEAN);
      if (expr.type.isBoxed()) {
        box();
      }
      return null;
    }

    // For remaining comparison operators
    if (expr.operator.isBooleanOperator()) {
      // If we don't have primitive types then delegate to RuntimeUtils.booleanOp
      if (!expr.left.type.isPrimitive() || !expr.right.type.isPrimitive()) {
        compile(expr.left);
        box();
        compile(expr.right);
        box();
        loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
        loadConst(expr.operator.getSource());
        loadConst(expr.operator.getOffset());
        invokeStatic(RuntimeUtils.class, "booleanOp", Object.class, Object.class, String.class, String.class, Integer.TYPE);
        return null;
      }

      // Left with boolean, int, long, double types
      if ((expr.left.type.is(BOOLEAN) || expr.right.type.is(BOOLEAN)) && !expr.left.type.equals(expr.right.type)) {
        // If one type is boolean and the other isn't then we must be doing == or != comparison
        // since Resolver would have already given compile error if other type of comparison
        check(expr.operator.is(EQUAL_EQUAL,BANG_EQUAL), "Unexpected operator " + expr.operator + " comparing boolean and non-boolean");
        // Since types are not compatible we already know that they are not equal so pop values and
        // load result
        compile(expr.left);
        compile(expr.right);
        popVal();
        popVal();
        loadConst(expr.operator.is(BANG_EQUAL));    // true when op is !=
        return null;
      }

      // We have two booleans or two numbers (int,long, or double)
      JacsalType operandType = expr.left.type.is(BOOLEAN)                              ? INT :
                               expr.left.type.is(DOUBLE) || expr.right.type.is(DOUBLE) ? DOUBLE :
                               expr.left.type.is(LONG)   || expr.right.type.is(LONG)   ? LONG :
                                                                                         INT;
      compile(expr.left);
      convertTo(operandType, false, expr.left.location);
      compile(expr.right);
      convertTo(operandType, false, expr.right.location);
      mv.visitInsn(operandType.is(INT) ? ISUB : operandType.is(LONG) ? LCMP : DCMPL);
      pop(2);
      int opCode = expr.operator.is(EQUAL_EQUAL)        ? IFEQ :
                   expr.operator.is(BANG_EQUAL)         ? IFNE :
                   expr.operator.is(LESS_THAN)          ? IFLT :
                   expr.operator.is(LESS_THAN_EQUAL)    ? IFLE :
                   expr.operator.is(GREATER_THAN)       ? IFGT :
                   expr.operator.is(GREATER_THAN_EQUAL) ? IFGE :
                   -1;
      check(opCode != -1, "Unexpected operator " + expr.operator);
      Label resultIsTrue = new Label();
      Label end          = new Label();
      mv.visitJumpInsn(opCode, resultIsTrue);
      _loadConst(false);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(resultIsTrue);   // :resultIsTrue
      _loadConst(true);
      mv.visitLabel(end);            // :end
      push(BOOLEAN);
      return null;
    }

    // Everything else
    compile(expr.left);
    convertTo(expr.type, true, expr.left.location);
    compile(expr.right);
    convertTo(expr.type, true, expr.right.location);

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
      // Check for divide by zero or remainder 0 if int/long (double returns NaN or INFINITY)
      if (expr.operator.is(SLASH,PERCENT) && !expr.type.isBoxedOrUnboxed(DOUBLE)) {
        _dupVal();
        Label nonZero = new Label();
        if (expr.type.isBoxedOrUnboxed(LONG)) {
          _loadConst(0L);
          mv.visitInsn(LCMP);
        }
        mv.visitJumpInsn(IFNE, nonZero);
        _popVal();
        throwError("Divide by zero error", expr.operator);
        mv.visitLabel(nonZero);
      }
      mv.visitInsn(opCode);
      pop(2);
      push(expr.type);
    }
    else
    if (expr.type.is(DECIMAL)) {
      loadConst(opCodes.get(decimalIdx));
      loadConst(classCompiler.context.maxScale);
      loadConst(expr.operator.getSource());
      loadConst(expr.operator.getOffset());
      invokeStatic(RuntimeUtils.class, "decimalBinaryOperation", BigDecimal.class, BigDecimal.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
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
      Expr key = entry.x;
      compile(key);
      convertTo(STRING, true, key.location);
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
      Expr subExpr = expr.exprList.get(i);
      compile(subExpr);
      convertTo(STRING, true, subExpr.location);
      mv.visitInsn(AASTORE);
      pop();
    }
    push(ANY);          // For the String[]
    loadConst(""); // Join string
    swap();
    invokeStatic(String.class, "join", CharSequence.class, CharSequence[].class);
    return null;
  }

  @Override public Void visitClosure(Expr.Closure expr) {
    // Find our MethodHandle and put that on the stack
    loadClassField(Utils.handleName(expr.funDecl.methodName), FUNCTION, false);
    return null;
  }

  @Override public Void visitCall(Expr.Call expr) {
    // If we have an explicit function name and exact number of args then we can invoke
    // directly without needing to go through a method handle or via method wrapper that
    // fills in missing args
    if (expr.callee.isFunctionCall()) {
      Expr.FunDecl funDecl = ((Expr.Identifier) expr.callee).varDecl.funDecl;
      if (expr.args.size() == funDecl.parameters.size()) {
        if (!funDecl.isStatic) {
          // Load this
          loadLocal(0);
        }
        for (int i = 0; i < expr.args.size(); i++) {
          Expr arg = expr.args.get(i);
          compile(arg);
          convertTo(funDecl.parameters.get(i).declExpr.type, !arg.type.isPrimitive(), arg.location);
        }
        invokeMethod(funDecl);
        return null;
      }
    }

    // Need to get the method handle
    compile(expr.callee);
    convertTo(FUNCTION, true, expr.callee.location);

    // Load source and offset
    loadLocation(expr.location);

    // Any arguments will be stored in an Object[]
    // TODO: support named args
    _loadConst(expr.args.size());
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    push(ANY);
    for (int i = 0; i < expr.args.size(); i++) {
      mv.visitInsn(DUP);
      _loadConst(i);
      compile(expr.args.get(i));
      convertToAny();
      mv.visitInsn(AASTORE);
      pop();
    }
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", "(Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/Object;", false);
    pop(4);   // callee, source, offset, Object[]
    push(ANY);

    return null;
  }

  ///////////////////////////////////////////////////////////////

  private void emitReturn(JacsalType returnType) {
    switch (returnType.getType()) {
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
      default: throw new IllegalStateException("Unexpected type " + returnType);
    }
  }

  private void invokeMethod(Expr.FunDecl funDecl) {
    mv.visitMethodInsn(funDecl.isStatic ? INVOKESTATIC : INVOKEVIRTUAL,
                       classCompiler.internalName,
                       funDecl.methodName,
                       getMethodDescriptor(funDecl),
                       false);
    pop(funDecl.parameters.size() + (funDecl.isStatic ? 0 : 1));
    push(funDecl.returnType);
  }

  private String getMethodDescriptor(Expr.FunDecl funDecl) {
    return Type.getMethodDescriptor(funDecl.returnType.descriptorType(),
                                    funDecl.parameters.stream()
                                                      .map(p -> p.declExpr.type.descriptorType())
                                                      .toArray(Type[]::new));
  }

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
      int slot1 = allocateSlot(type1);
      int slot2 = allocateSlot(type2);
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
   * Duplicate top two elemenets on the stack:
   *   ... x, y ->
   *   ... x, y, x, y
   *
   * Note that we need to take care whether any of the types are long or double
   * since the JVM operation dup2 only duplicates the top two slots of the stack
   * rather than the top two values.
   */
  private void dupVal2() {
    JacsalType type1 = pop();
    JacsalType type2 = pop();
    if (slotsNeeded(type1) == 1 && slotsNeeded(type2) == 1) {
      mv.visitInsn(DUP2);
    }
    else {
      int slot1 = allocateSlot(type1);
      int slot2 = allocateSlot(type2);
      _storeLocal(slot1);
      _storeLocal(slot2);
      _loadLocal(slot2);
      _loadLocal(slot1);
      _loadLocal(slot2);
      _loadLocal(slot1);
      freeTemp(slot2);
      freeTemp(slot1);
    }
    stack.push(type2);
    stack.push(type1);
    stack.push(type2);
    stack.push(type1);
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
   * Load null onto stack
   * @param type  the type to put onto type stack for the null
   */
  private void loadNull(JacsalType type) {
    _loadConst(null);
    push(type);
  }

  /**
   * Load constant value onto JVM stack and also push type onto type stack.
   * Supports Boolean, numeric types, BigDecimal, String, and null.
   * @param obj  object to load on stack
   */
  private Void loadConst(Object obj) {
    push(_loadConst(obj));
    return null;
  }

  /**
   * Load object onto JVM stack but don't alter type stack
   */
  private JacsalType _loadConst(Object obj) {
    return Utils.loadConst(mv, obj);
  }

  private void loadDefaultValue(JacsalType type) {
    switch (type.getType()) {
      case BOOLEAN:  loadConst(Boolean.FALSE);     break;
      case INT:      loadConst(0);            break;
      case LONG:     loadConst(0L);           break;
      case DOUBLE:   loadConst( 0D);          break;
      case DECIMAL:  loadConst(BigDecimal.ZERO);   break;
      case STRING:   loadConst("");           break;
      case ANY:      loadConst(null);         break;
      case MAP:
        mv.visitTypeInsn(NEW, "java/util/HashMap");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        push(MAP);
        break;
      case LIST:
        mv.visitTypeInsn(NEW, "java/util/ArrayList");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        push(LIST);
        break;
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  /**
   * Convert given int to actual type required
   * @param type   type needed
   * @param value  int value to turn into type
   * @return the Object for the value
   */
  private Object valueOf(JacsalType type, int value) {
    switch (type.getType()) {
      case INT:      return value;
      case LONG:     return (long)value;
      case DOUBLE:   return (double)value;
      case DECIMAL:  return BigDecimal.valueOf(value);
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  ///////////////////////////////////////////////////////////////

  // = Conversions

  private Void convertTo(JacsalType type, boolean couldBeNull, SourceLocation location) {
    if (type.isBoxedOrUnboxed(BOOLEAN)) { return convertToBoolean(); }   // null is valid for boolean conversion
    if (type.isPrimitive() && !peek().isPrimitive() && couldBeNull) {
      throwIfNull("Cannot convert null value to " + type, location);
    }
    switch (type.getType()) {
      case INT:      return convertToInt(location);
      case LONG:     return convertToLong(location);
      case DOUBLE:   return convertToDouble(location);
      case DECIMAL:  return convertToDecimal(location);
      case STRING:   return convertToString();
      case MAP:      return convertToMap(location);
      case LIST:     return convertToList(location);
      case ANY:      return convertToAny();
      case FUNCTION: return convertToFunction(location);
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

  private Void convertToInt(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToNumber", Object.class, String.class, Integer.TYPE);
      invokeVirtual(Number.class, "intValue");
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeVirtual(Number.class, "intValue");
    }
    else {
      switch (peek().getType()) {
        case BOOLEAN:
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

  private Void convertToLong(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToNumber", Object.class, String.class, Integer.TYPE);
      invokeVirtual(Number.class, "longValue");
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeVirtual(Number.class, "longValue");
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

  private Void convertToDouble(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToNumber", Object.class, String.class, Integer.TYPE);
      invokeVirtual(Number.class, "doubleValue");
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeVirtual(Number.class, "doubleValue");
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

  private Void convertToDecimal(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToDecimal", Object.class, String.class, Integer.TYPE);
    }
    else
    switch (peek().getType()) {
      case INT:
      case LONG:
        convertToLong(location);
        invokeStatic(BigDecimal.class, "valueOf", Long.TYPE);
        break;
      case DOUBLE:
        convertToDouble(location);
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

  private Void convertToMap(SourceLocation location) {
    if (peek().is(MAP)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToMap", Object.class, String.class, Integer.TYPE);
      return null;
    }
    throw new IllegalStateException("Internal error: unexpected type " + peek());
  }

  private Void convertToList(SourceLocation location) {
    if (peek().is(LIST)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToList", Object.class, String.class, Integer.TYPE);
      return null;
    }
    return null;
  }

  private Void convertToFunction(SourceLocation location) {
    if (peek().is(FUNCTION)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeStatic(RuntimeUtils.class, "castToFunction", Object.class, String.class, Integer.TYPE);
      return null;
    }
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

  private void incOrDec(boolean isPrefix, boolean isInc, Expr expr, boolean isResultUsed, Token operator) {
    if (expr instanceof Expr.Identifier) {
      incOrDecVar(isPrefix, isInc, (Expr.Identifier)expr, 1, isResultUsed, operator);
    }
    else {
      compile(expr);
      if (!isPrefix) {
        if (isResultUsed) {
          dupVal();
        }
        incOrDecValue(isInc, 1, operator);
        popVal();
      }
      else {
        incOrDecValue(isInc, 1, operator);
        convertTo(expr.type, true, operator);
        if (!isResultUsed) {
          popVal();
        }
      }
    }
  }

  /**
   * Increment or decrement variable by given amount.
   * Used for --,++ and for += and -= operations
   * @param isPrefix          whether we are a prefix or postfix -- or ++
   * @param isInc             whether we are incrementing/adding or decrementing/subtracting
   * @param identifierExpr    the Expr.Identifier for the local variable
   * @param amount            the amount to inc/dec or null if amount is already on the stack
   * @param isResultUsed      whether the result is used after the inc/dec
   * @param operator          operator
   */
  private void incOrDecVar(boolean isPrefix, boolean isInc, Expr.Identifier identifierExpr, Object amount, boolean isResultUsed, Token operator) {
    Expr.VarDecl varDecl = identifierExpr.varDecl;
    // If postfix and we use the value then load value before doing inc/dec so we leave
    // old value on the stack
    if (!isPrefix && isResultUsed) {
      loadVar(varDecl);
    }

    // Helper to do the inc/dec based on the type of the value
    Consumer<Runnable> incOrDec = (runnable) -> {
      loadVar(varDecl);
      unbox();

      // If amount is null then value is already on the stack so swap otherwise
      // load the inc/dec amount
      if (amount == null) {
        swap();
        convertTo(varDecl.type, true, operator);
      }
      else {
        loadConst(Utils.convertNumberTo(varDecl.type, amount));
      }

      runnable.run();
      storeVar(varDecl);
    };

    switch (varDecl.type.getType()) {
      case INT:
        if (varDecl.type.isBoxed() || amount == null) {
          incOrDec.accept(() -> { mv.visitInsn(isInc ? IADD : ISUB); pop(); });
        }
        else {
          int intAmt = (int)Utils.convertNumberTo(INT, amount);
          mv.visitIincInsn(varDecl.slot, isInc ? intAmt : -intAmt);
        }
        break;
      case LONG:
        incOrDec.accept(() -> { mv.visitInsn(isInc ? LADD : LSUB); pop(); });
        break;
      case DOUBLE:
        incOrDec.accept(() -> { mv.visitInsn(isInc ? DADD : DSUB); pop(); });
        break;
      case DECIMAL:
        incOrDec.accept(() -> invokeVirtual(BigDecimal.class, isInc ? "add" : "subtract", BigDecimal.class));
        break;
      case ANY:
        // When we don't know the type
        loadVar(varDecl);
        if (amount == null) {
          swap();
        }
        incOrDecValue(isInc, amount, operator);
        storeVar(varDecl);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + varDecl.type);
    }
    if (isPrefix && isResultUsed) {
      loadVar(varDecl);
    }
  }

  /**
   * Increment or decrement by given amount the current value on the stack.
   * If amount is null then amount to inc/dec is also on the stack.
   * @param isInc    true if doing inc (false for dec)
   * @param amount   amount to inc/dec or null if value is already on the stack
   * @param operator the increment or decrement operator
   */
  private void incOrDecValue(boolean isInc, Object amount, Token operator) {
    unbox();
    if (peek().is(ANY)) {
      if (amount != null) {
        // Special case if amount is 1
        if (amount instanceof Integer && (int)amount == 1) {
          loadConst(classCompiler.source);
          loadConst(operator.getOffset());
          invokeStatic(RuntimeUtils.class, isInc ? "incNumber" : "decNumber", Object.class, String.class, Integer.TYPE);
          return;
        }

        loadConst(amount);
        box();
      }

      // Invoke binary + or - as needed
      loadConst(RuntimeUtils.getOperatorType(isInc ? PLUS : MINUS));
      loadConst(RuntimeUtils.getOperatorType(operator.getType()));
      loadConst(classCompiler.context.maxScale);
      loadConst(classCompiler.source);
      loadConst(operator.getOffset());
      invokeStatic(RuntimeUtils.class, "binaryOp", Object.class, Object.class, String.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
      return;
    }

    if (amount != null) {
      loadConst(valueOf(peek(), 1));
    }
    switch (peek().getType()) {
      case INT:     mv.visitInsn(isInc ? IADD : ISUB);   pop();                                    break;
      case LONG:    mv.visitInsn(isInc ? LADD : LSUB);   pop();                                    break;
      case DOUBLE:  mv.visitInsn(isInc ? DADD : DSUB);   pop();                                    break;
      case DECIMAL: invokeVirtual(BigDecimal.class, isInc ? "add" : "subtract", BigDecimal.class); break;
      default:      throw new IllegalStateException("Internal error: unexpected type " + peek());
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

  private void throwIfNull(String msg, SourceLocation location) {
    _dupVal();
    Label isNotNull = new Label();
    mv.visitJumpInsn(IFNONNULL, isNotNull);
    _popVal();
    mv.visitTypeInsn(NEW, "jacsal/runtime/NullError");
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocation(location);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/NullError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
    mv.visitLabel(isNotNull);
  }

  private void throwError(String msg, SourceLocation location) {
    mv.visitTypeInsn(NEW, "jacsal/runtime/RuntimeError");
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocation(location);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
  }

  private void throwError(String msg, int sourceSlot, int offsetSlot) {
    mv.visitTypeInsn(NEW, "jacsal/runtime/RuntimeError");
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocal(sourceSlot);
    _loadLocal(offsetSlot);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
  }

  /**
   * Emit code for a try/catch statement.
   * @param exceptionClass  the class to catch
   * @param tryBlock        Runnable that emits code for the try block
   * @param catchBlock      Runnable that emits code for the catch block
   */
  private void tryCatch(Class exceptionClass, Runnable tryBlock, Runnable catchBlock) {
    // We need to check for any values that are on the stack at the point that the try/catch
    // is to be used since the JVM will discard these values if an exception is thrown and
    // will leave just the exception on the stack at the point the exception is caught.
    // If we have values on the stack we save them in temp locations and then put them back
    // onto the stack for the try block to use. After the catch we also restore them so that
    // code in the catch block has the same stack as the code in the try block.
    int[] temps = new int[stack.size()];
    if (stack.size() > 0) {
      for (int i = 0; i < temps.length; i++) {
        temps[i] = allocateSlot(peek());
        storeLocal(temps[i]);
      }
    }

    // Restore stack by loading values from the locals we have stored them in
    Runnable restoreStack = () -> {
      // Restore in reverse order
      for (int i = temps.length - 1; i >= 0; i--) {
        loadLocal(temps[i]);
      }
    };

    Label blockStart = new Label();
    Label blockEnd   = new Label();
    Label catchLabel = new Label();
    mv.visitTryCatchBlock(blockStart, blockEnd, catchLabel, Type.getInternalName(exceptionClass));

    mv.visitLabel(blockStart);   // :blockStart
    restoreStack.run();
    tryBlock.run();

    Label after = new Label();
    mv.visitJumpInsn(GOTO, after);
    mv.visitLabel(blockEnd);     // :blockEnd

    mv.visitLabel(catchLabel);   // :catchLabel
    // Since the catch block runs with the stack being wiped we need to simulate the same
    // thing with our type stack to track what would be happening on the JVM stack. We also
    // verify that the stack at the end of the try block and the stack at the end of the
    // catch block look the same otherwise we will have problems with the JVM code validation
    // that expects that the stack is the same at any point where two different code paths
    // meet, irrespective of which path was chosen.
    // We save the type stack we have after the try block and restore the stack ready for the
    // catch block.
    mv.visitInsn(POP);           // Don't need the exception
    Deque<JacsalType> savedStack = stack;
    stack = new ArrayDeque<>();
    restoreStack.run();
    catchBlock.run();
    mv.visitLabel(after);

    // Validate that the two stacks are the same
    check(equals(stack, savedStack), "Stack after try does not match stack after catch: tryStack=" + savedStack + ", catchStack=" + stack);

    // Free our temps
    Arrays.stream(temps).forEach(i -> freeTemp(i));
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
    mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(clss), methodName, Type.getMethodDescriptor(method), false);
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
   * Define a variable. It will either be a local slot variable or a heap variable
   * if it has been closed over.
   * For local vars of type long and double we use two slots. All others use one slot.
   * For heap vars we allocate an entry in an Object array.
   */
  private void defineVar(Expr.VarDecl var) {
    mv.visitLabel(var.declLabel = new Label());   // Label for debugger
    JacsalType type = var.isHeap ? ANY : var.type;
    var.slot = allocateSlot(type);
    locals.set(var.slot, type);
    if (type.is(LONG, DOUBLE)) {
      locals.set(var.slot + 1, ANY);  // Use ANY to mark slot as used
    }

    // If var is a parameter then we bump the minimum slot that we use when searching
    // for a free slot since we will never unallocate parameters so there is no point
    // searching those slots for a free one
    if (var.isParam) {
      minimumSlot = locals.size();
    }
  }

  /**
   * Undefine variable when no longer needed
   */
  private void undefineVar(Expr.VarDecl varDecl, Label endBlock) {
    JacsalType type = varDecl.isHeap ? ANY : varDecl.type;
    mv.visitLocalVariable(varDecl.name.getStringValue(), type.descriptor(), null,
                          varDecl.declLabel, endBlock, varDecl.slot);
    freeSlot(varDecl.slot);
  }

  private void freeSlot(int slot) {
    int slots = slotsNeeded(locals.get(slot));
    for (int i = 0; i < slots; i++) {
      locals.set(slot + i, null);
    }
  }

  /**
   * Allocate a slot for storing a value of given type.
   * We search for first free gap in our local slots big enough for our type.
   * Supports allocation of double slots for long and double values.
   * @param type the type to allocate
   * @return the slot allocated
   */
  private int allocateSlot(JacsalType type) {
    int i;
    for (i = minimumSlot; i < locals.size(); i += slotsNeeded(locals.get(i))) {
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
    if (slot == -1) { return; }
    freeSlot(slot);
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
    loadClassField(Utils.JACSAL_GLOBALS_NAME, JacsalType.MAP, false);
  }

  private void loadClassField(String fieldName, JacsalType type, boolean isStatic) {
    if (!isStatic) {
      mv.visitVarInsn(ALOAD, 0);
    }
    mv.visitFieldInsn(GETFIELD, classCompiler.internalName, fieldName, type.descriptor());
    push(type);
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
    _storeLocal(slot);
    pop();
  }

  private void _storeLocal(int slot) {
    JacsalType type = locals.get(slot);
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ISTORE, slot); break;
        case INT:       mv.visitVarInsn(ISTORE, slot); break;
        case LONG:      mv.visitVarInsn(LSTORE, slot); break;
        case DOUBLE:    mv.visitVarInsn(DSTORE, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ASTORE, slot);
    }
  }

  /**
   * Load field value from a map or list.
   * Stack is expected to have: ..., parent, field
   * Optionally the defaultType (if non-null) will determine the type of default value to return
   * if the field does not exist.
   * Alternatively the default value can be supplied directly. This is used for the special case of
   * RuntimeUtils.DEFAULT_VALUE which is a value that means use whatever sensible default makes sense
   * in the context. E.g. if doing integer arithmetic use 0 or if doing string concatenation use ''.
   * The createIfMissing flag will create the field if missing of the type specified by defaultType
   * (which must be MAP or LIST in this case).
   * @param accessOperator  the type of field access ('.', '?.', '[', '?[')
   * @param createIfMissing true if we should create the field if it is missing
   * @param defaultType     if non-null create default value of this type if field does not exist
   * @param defaultValue    use this as the default value (only one of defaultType or defaultValue should
   *                       be supplied since they are mutally exclusive)
   */
  private void loadField(Token accessOperator, boolean createIfMissing, JacsalType defaultType, Object defaultValue) {
    check(defaultType == null || defaultValue == null, "Cannot have defaultType and defaultValue specified");
    box();                        // Field name/index passed as Object
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.is(QUESTION_DOT,QUESTION_SQUARE));
    if ((defaultType != null || defaultValue != null) && !createIfMissing) {
      if (defaultType != null) {
        loadDefaultValue(defaultType);
      }
      else {
        // Special case for RuntimeUtils.DEFAULT_VALUE
        if (defaultValue == RuntimeUtils.DEFAULT_VALUE) {
          mv.visitFieldInsn(GETSTATIC, Type.getInternalName(RuntimeUtils.class), "DEFAULT_VALUE", "Ljava/lang/Object;");
          push(ANY);
        }
        else {
          loadConst(defaultValue);
        }
      }
      box();
      loadConst(accessOperator.getSource());
      loadConst(accessOperator.getOffset());
      invokeStatic(RuntimeUtils.class, "loadFieldOrDefault", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, Object.class, String.class, Integer.TYPE);
    }
    else
    if (createIfMissing) {
      check(defaultType.is(MAP,LIST), "Type must be MAP/LIST when createIfMissing set");
      loadConst(defaultType.is(MAP));
      loadConst(accessOperator.getSource());
      loadConst(accessOperator.getOffset());
      invokeStatic(RuntimeUtils.class, "loadOrCreateField", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
    }
    else {
      loadConst(accessOperator.getSource());
      loadConst(accessOperator.getOffset());
      invokeStatic(RuntimeUtils.class, "loadField", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
    }
  }

  private void storeField(Token accessOperator) {
    box();     // Primitive values need to be boxed to store into heap location
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.getSource());
    loadConst(accessOperator.getOffset());
    invokeStatic(RuntimeUtils.class, "storeField", Object.class, Object.class, Object.class, Boolean.TYPE, String.class, Integer.TYPE);
  }

  private Void loadLocal(int slot) {
    if (slot == -1) { return null; }

    _loadLocal(slot);
    JacsalType type = locals.get(slot);
    push(type);
    return null;
  }

  private void _loadLocal(int slot) {
    JacsalType type = locals.get(slot);
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ILOAD, slot); break;
        case INT:       mv.visitVarInsn(ILOAD, slot); break;
        case LONG:      mv.visitVarInsn(LLOAD, slot); break;
        case DOUBLE:    mv.visitVarInsn(DLOAD, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ALOAD, slot);
    }
  }

  ////////////////////////////////////////////////////////

  // = Misc

  private void ntimes(int count, Runnable runnable) {
    for (int i = 0; i < count; i++) {
      runnable.run();
    }
  }

  private void check(boolean condition, String msg) {
    if (!condition) {
      throw new IllegalStateException("Internal error: " + msg);
    }
  }

  private static boolean equals(Deque stack1, Deque stack2) {
    if (stack1.size() != stack2.size()) { return false; }
    Iterator it1 = stack1.iterator();
    Iterator it2 = stack2.iterator();
    while (it1.hasNext() && it2.hasNext()) {
      if (!it1.next().equals(it2.next())) {
        return false;
      }
    }
    return true;
  }

  ///////////////////////////////////

  private static SourceLocation location(int sourceSlot, int offsetSlot) {
    return new LocalLocation(sourceSlot, offsetSlot);
  }

  private static class LocalLocation implements SourceLocation {
    int sourceSlot;
    int offsetSlot;
    LocalLocation(int sourceSlot, int offsetSlot) {
      this.sourceSlot = sourceSlot;
      this.offsetSlot = offsetSlot;
    }
  }

  private void loadLocation(SourceLocation location) {
    _loadLocation(location);
    push(STRING);
    push(INT);
  }

  private void _loadLocation(SourceLocation sourceLocation) {
    if (sourceLocation instanceof LocalLocation) {
      LocalLocation localLocation = (LocalLocation) sourceLocation;
      _loadLocal(localLocation.sourceSlot);
      _loadLocal(localLocation.offsetSlot);
      return;
    }
    if (sourceLocation instanceof Location) {
      Location location = (Location) sourceLocation;
      _loadConst(location.getSource());
      _loadConst(location.getOffset());
      return;
    }
    throw new IllegalStateException("Unexpected SourceLocation type " + sourceLocation.getClass().getName());
  }
}
