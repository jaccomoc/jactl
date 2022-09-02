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

import java.math.BigDecimal;
import java.util.*;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.LIST;
import static jacsal.JacsalType.MAP;
import static jacsal.JacsalType.STRING;
import static jacsal.TokenType.*;

/**
 * The Resolver visits all statements and all expressions and performs the following:
 *  - tracks variables and their type
 *  - resolves references to symbols (variables, methods, etc)
 *  - tracks what local variables exist in each code block
 *  - promotes local variables to closed over variabels when used in nested functions/closures
 *  - evaluates any simple expressions made up of only constants (literals)
 *  - matches the break/continue statements to their enclosing while/for loop
 *  - sets type of return statement to match return type of function that return belongs to
 *
 * One of the jobs of the Resolver is to work out which local variables should be truly
 * local (and have a JVM slot allocated) and which should be turned into heap variables
 * because they are referred to by a nested function/closure (i.e. they are "closed over").
 *
 * A heap local variable is stored in a HeapVar object which is just a wrapper around the
 * actual value. By using this extra level of indirection we make sure that if the value
 * of the variable changes, all functions using that variable will see the changed value.
 *
 * We pass any such heap local variables as impicit parameters to nested functions that
 * need access to these variables from their parents. Note that sometimes a nested function
 * will access a variable that is not in its immediate parent but from a level higher up so
 * even if the parent has no need of the heap variable it needs it in order to be able to
 * pass it to a nested function that it invokes at some point. In this way nested functions
 * (and functions nested within them) can effect which heap vars need to get passed into the
 * current function from its parent.
 *
 * If we consider a single heap local variable. It can be used in multiple nested functions
 * and its location in the list of implicit parameters for each function can be different.
 * Normally we use the Expr.VarDecl object to track which local variable slot it resides in
 * but with HeapVar objects we need to track the slot used in each function so we need a per
 * function object to track the slot for a given HeapVar. We create a new VarDecl that points
 * to the original VarDecl for every such nested function.
 */
public class Resolver implements Expr.Visitor<JacsalType>, Stmt.Visitor<Void> {

  private final CompileContext        compileContext;
  private final Map<String,Object>    globals;
  private final Deque<Expr.FunDecl>   functions = new ArrayDeque<>();
  private final Deque<Stmt.ClassDecl> classes   = new ArrayDeque<>();

  /**
   * Resolve variables, references, etc
   * @param globals  map of global variables (which themselves can be Maps or Lists or simple values)
   */
  Resolver(CompileContext compileContext, Map<String,Object> globals) {
    this.compileContext = compileContext;
    this.globals        = globals;
  }

  Void resolve(Stmt stmt) {
    if (stmt != null) {
      return stmt.accept(this);
    }
    return null;
  }

  Void resolve(List<Stmt> stmts) {
    if (stmts != null) {
      stmts.forEach(this::resolve);
    }
    return null;
  }

  JacsalType resolve(Expr expr) {
    if (expr != null) {
      return expr.accept(this);
    }
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Stmt

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    classes.push(stmt);
    try {
      stmt.classes.forEach(this::resolve);
      stmt.fields.forEach(this::resolve);

      // Don't resolve methods and closures since that will be done by the FunDecl statements
      // within the scriptMain function
        //stmt.methods.forEach(this::resolve);
        //stmt.closures.forEach(this::resolve);

      resolve(stmt.scriptMain);
    }
    finally {
      classes.pop();
    }
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    resolve(stmt.declExpr);
    return null;
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    stmt.stmts.forEach(this::resolve);
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    functions.peek().blocks.push(stmt);
    try {
      // We first define our nested functions so that we can support
      // forward references to functions declared at same level as us
      stmt.functions.forEach(nested -> define(nested.name, nested.varDecl));

      return resolve(stmt.stmts);
    }
    finally {
      functions.peek().blocks.pop();
    }
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    resolve(stmt.declExpr);
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    resolve(stmt.expr);
    stmt.returnType = functions.peek().returnType;
    if (!stmt.expr.type.isConvertibleTo(stmt.returnType)) {
      throw new CompileError("Expression type not compatible with return type of function", stmt.expr.location);
    }
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    resolve(stmt.condtion);
    resolve(stmt.trueStmt);
    resolve(stmt.falseStmt);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.updates);

    // We need to keep track of what the current while loop is so that break/continue
    // statements within body of the loop can find the right Stmt.While object
    Stmt.While oldWhileStmt = functions.peek().currentWhileLoop;
    functions.peek().currentWhileLoop = stmt;

    resolve(stmt.body);

    functions.peek().currentWhileLoop = oldWhileStmt;   // Restore old one
    return null;
  }

  @Override public Void visitBreak(Stmt.Break stmt) {
    stmt.whileLoop = currentWhileLoop(stmt.breakToken);
    return null;
  }

  @Override public Void visitContinue(Stmt.Continue stmt) {
    stmt.whileLoop = currentWhileLoop(stmt.continueToken);
    return null;
  }

  @Override public Void visitPrint(Stmt.Print stmt) {
    resolve(stmt.expr);
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Expr

  @Override public JacsalType visitBinary(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);

    expr.isConst = expr.left.isConst && expr.right.isConst;
    if (expr.left.isConst && expr.left.constValue == null && !expr.operator.getType().isBooleanOperator()) {
      throw new CompileError("Left-hand side of '" + expr.operator.getChars() + "' cannot be null", expr.left.location);
    }

    // Field access operators
    if (expr.operator.is(DOT,QUESTION_DOT,LEFT_SQUARE,QUESTION_SQUARE)) {
      expr.isConst = false;
      if (!expr.left.type.is(ANY)) {
        // Do some level of validation
        if (expr.operator.is(DOT,QUESTION_DOT) && !expr.left.type.is(MAP)) {
          throw new CompileError("Invalid object type (" + expr.left.type + ") for field access", expr.operator);
        }
        // '[' and '?['
        if (!expr.left.type.is(MAP,LIST,STRING)) {
          throw new CompileError("Invalid object type (" + expr.left.type + ") for indexed (or field) access", expr.operator);
        }

        // TODO: if we have a concrete type on left-hand side we can work out type of field here
        //       for the moment all field access returns ANY since we don't know what has been
        //       stored in a Map or List
      }

      expr.type = ANY;

      // Since we now know we are doing a map/list lookup we know what parent type should
      // be so if parent type was ANY we can change to Map/List. This is used when field
      // path is an lvalue to create missing fields/elements of the correct type.
      if (expr.left.type.is(ANY) && expr.createIfMissing) {
        expr.left.type = expr.operator.is(DOT,QUESTION_DOT) ? MAP : LIST;
      }
      return expr.type;
    }

    expr.type = JacsalType.result(expr.left.type, expr.operator, expr.right.type);
    if (expr.isConst) {
      if (expr.type.is(STRING)) {
        if (expr.operator.isNot(PLUS,STAR)) { throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for Strings"); }
        if (expr.operator.is(PLUS)) {
          if (expr.left.constValue == null) {
            throw new CompileError("Left-hand side of '+' cannot be null", expr.operator);
          }
          expr.constValue = Utils.toString(expr.left.constValue) + Utils.toString(expr.right.constValue);
        }
        else {
          if (expr.right.constValue == null) {
            throw new CompileError("Right-hand side of string repeat operator must be numeric but was null", expr.operator);
          }
          String lhs    = Utils.toString(expr.left.constValue);
          long   length = Utils.toLong(expr.right.constValue);
          if (length < 0) {
            throw new CompileError("String repeat count must be >= 0", expr.right.location);
          }
          expr.constValue = lhs.repeat((int)length);
        }
        return expr.type;
      }

      if (expr.operator.is(AMPERSAND_AMPERSAND)) {
        expr.constValue = RuntimeUtils.isTruth(expr.left.constValue, false) &&
                          RuntimeUtils.isTruth(expr.right.constValue, false);
        return expr.type;
      }
      if (expr.operator.is(PIPE_PIPE)) {
        expr.constValue = RuntimeUtils.isTruth(expr.left.constValue, false) ||
                          RuntimeUtils.isTruth(expr.right.constValue, false);
        return expr.type;
      }

      if (expr.operator.getType().isBooleanOperator()) {
        expr.constValue = RuntimeUtils.booleanOp(expr.left.constValue, expr.right.constValue,
                                                 RuntimeUtils.getOperatorType(expr.operator.getType()),
                                                 expr.operator.getSource(), expr.operator.getOffset());
        return expr.type = JacsalType.BOOLEAN;
      }

      if (expr.left.constValue == null)  { throw new CompileError("Non-numeric operand for left-hand side of '" + expr.operator.getChars() + "': cannot be null", expr.operator); }
      if (expr.right.constValue == null) { throw new CompileError("Non-numeric operand for right-hand side of '" + expr.operator.getChars() + "': cannot be null", expr.operator); }

      switch (expr.type.getType()) {
        case INT: {
          int left  = Utils.toInt(expr.left.constValue);
          int right = Utils.toInt(expr.right.constValue);
          throwIf(expr.operator.is(SLASH,PERCENT) && right == 0, "Divide by zero error", expr.right.location);
          switch (expr.operator.getType()) {
            case PLUS:    expr.constValue = left + right; break;
            case MINUS:   expr.constValue = left - right; break;
            case STAR:    expr.constValue = left * right; break;
            case SLASH:   expr.constValue = left / right; break;
            case PERCENT: expr.constValue = left % right; break;
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for ints");
          }
          break;
        }
        case LONG: {
          long left  = Utils.toLong(expr.left.constValue);
          long right = Utils.toLong(expr.right.constValue);
          throwIf(expr.operator.is(SLASH,PERCENT) && right == 0, "Divide by zero error", expr.right.location);
          switch (expr.operator.getType()) {
            case PLUS:    expr.constValue = left + right; break;
            case MINUS:   expr.constValue = left - right; break;
            case STAR:    expr.constValue = left * right; break;
            case SLASH:   expr.constValue = left / right; break;
            case PERCENT: expr.constValue = left % right; break;
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for longs");
          }
          break;
        }
        case DOUBLE: {
          double left  = Utils.toDouble(expr.left.constValue);
          double right = Utils.toDouble(expr.right.constValue);
          switch (expr.operator.getType()) {
            case PLUS:    expr.constValue = left + right; break;
            case MINUS:   expr.constValue = left - right; break;
            case STAR:    expr.constValue = left * right; break;
            case SLASH:   expr.constValue = left / right; break;
            case PERCENT: expr.constValue = left % right; break;
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for doubles");
          }
          break;
        }
        case DECIMAL: {
          BigDecimal left  = Utils.toDecimal(expr.left.constValue);
          BigDecimal right = Utils.toDecimal(expr.right.constValue);
          throwIf(expr.operator.is(SLASH,PERCENT) && right.stripTrailingZeros() == BigDecimal.ZERO, "Divide by zero error", expr.right.location);
          expr.constValue = RuntimeUtils.decimalBinaryOperation(left, right, RuntimeUtils.getOperatorType(expr.operator.getType()), compileContext.maxScale,
                                                                expr.operator.getSource(), expr.operator.getOffset());
          break;
        }
      }
    }
    return expr.type;
  }

  @Override public JacsalType visitPrefixUnary(Expr.PrefixUnary expr) {
    resolve(expr.expr);
    expr.isConst      = expr.expr.isConst;
    if (expr.operator.is(BANG)) {
      expr.type = JacsalType.BOOLEAN;
      if (expr.isConst) {
        expr.constValue = !Utils.toBoolean(expr.expr.constValue);
      }
      return expr.type;
    }
    else {
      expr.type = expr.expr.type.unboxed();
      if (!expr.type.isNumeric() && !expr.type.is(ANY)) {
        throw new CompileError("Prefix operator '" + expr.operator.getChars() + "' cannot be applied to type " + expr.expr.type, expr.operator);
      }
      if (expr.isConst) {
        expr.constValue = expr.expr.constValue;
        if (expr.operator.is(MINUS)) {
          switch (expr.type.getType()) {
            case INT:     expr.constValue = -(int)expr.constValue;                  break;
            case LONG:    expr.constValue = -(long)expr.constValue;                 break;
            case DOUBLE:  expr.constValue = -(double)expr.constValue;               break;
            case DECIMAL: expr.constValue = ((BigDecimal)expr.constValue).negate(); break;
          }
        }
        else
        if (expr.operator.is(PLUS_PLUS,MINUS_MINUS)) {
          if (expr.expr.constValue == null) {
            throw new CompileError("Prefix operator '" + expr.operator.getChars() + "': null value encountered", expr.expr.location);
          }
          expr.constValue = incOrDec(expr.operator.is(PLUS_PLUS), expr.type, expr.expr.constValue);
        }
      }
      return expr.type;
    }
  }

  @Override public JacsalType visitPostfixUnary(Expr.PostfixUnary expr) {
    resolve(expr.expr);
    expr.isConst      = expr.expr.isConst;
    expr.type         = expr.expr.type;
    if (expr.expr.type.isNumeric() || expr.expr.type.is(ANY)) {
      if (expr.isConst) {
        if (expr.expr.constValue == null) {
          throw new CompileError("Postfix operator '" + expr.operator.getChars() + "': null value encountered", expr.expr.location);
        }
        // For const expressions, postfix inc/dec is a no-op
        expr.constValue = expr.expr.constValue;
      }
      return expr.type;
    }
    throw new CompileError("Unary operator " + expr.operator + " cannot be applied to type " + expr.expr.type, expr.operator);
  }

  @Override public JacsalType visitLiteral(Expr.Literal expr) {
    // Whether we optimise const expressions by evaluating at compile time
    // is controlled by CompileContext (defaults to true).
    expr.isConst    = compileContext.evaluateConstExprs;
    expr.constValue = expr.value.getValue();

    switch (expr.value.getType()) {
      case INTEGER_CONST: return expr.type = JacsalType.INT;
      case LONG_CONST:    return expr.type = JacsalType.LONG;
      case DOUBLE_CONST:  return expr.type = JacsalType.DOUBLE;
      case DECIMAL_CONST: return expr.type = JacsalType.DECIMAL;
      case STRING_CONST:  return expr.type = STRING;
      case TRUE:          return expr.type = JacsalType.BOOLEAN;
      case FALSE:         return expr.type = JacsalType.BOOLEAN;
      case NULL:          return expr.type = ANY;
      case IDENTIFIER:    return expr.type = STRING;
      default:
        // In some circumstances (e.g. map keys) we support literals that are keywords
        if (!expr.value.isKeyword()) {
          throw new IllegalStateException("Internal error: unexpected token for literal - " + expr.value);
        }
        return expr.type = STRING;
    }
  }

  @Override public JacsalType visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs.forEach(this::resolve);
    return expr.type = LIST;
  }

  @Override public JacsalType visitMapLiteral(Expr.MapLiteral expr) {
    expr.entries.forEach(entry -> {
      resolve(entry.x);
      resolve(entry.y);
    });
    return expr.type = MAP;
  }

  @Override public JacsalType visitVarDecl(Expr.VarDecl expr) {
    // Functions have previously been declared by the block they belong to
    if (expr.funDecl != null) {
      return expr.type = FUNCTION;
    }

    // Declare the variable (but don't define it yet) so that we can detect self-references
    // in the initialiser. E.g. we can catch:  int x = x + 1
    declare(expr.name);

    JacsalType type = resolve(expr.initialiser);
    if (expr.type == null) {
      expr.type = type;
    }
    else
    if (type != null && !type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert initialiser of type " + type + " to type of variable (" + expr.type + ")", expr.initialiser.location);
    }
    define(expr.name, expr);
    return expr.type;
  }

  @Override public JacsalType visitFunDecl(Expr.FunDecl expr) {
    resolve(expr.varDecl);
    Expr.FunDecl parent = functions.peek();
    if (functions.size() > 0) {
      // Generate our method name by appending to parent's name separated by '$'
      // (unless we are at the top level in which case we just use our given name).
      // For closures we use _$cN where N is incrementing count per parent function.
      String methodName = expr.name == null ? "_$c" + ++parent.closureCount : expr.name.getStringValue();
      expr.methodName = functions.size() == 1 ? methodName : functions.peek().methodName + "$" + methodName;
    }
    else {
      // Top level script main function
      expr.methodName = expr.name.getStringValue();
    }

    functions.push(expr);
    try {
      // Add explicit return if needed in places where we would implicity return the result
      explicitReturn(expr.block, expr.returnType);
      resolve(expr.block);
      return expr.type = FUNCTION;
    }
    finally {
      functions.pop();
      if (parent != null) {
        // Check if parent needs to have any additional heap vars passed to it in order to be able
        // to pass to nested function and add them to the parent.heapVars map
        expr.heapVars.entrySet()
                     .stream()
                     .filter(e -> e.getValue().owner != parent)
                     .filter(e -> !parent.heapVars.containsKey(e.getKey()))
                     .forEach(e -> parent.heapVars.put(e.getKey(), varDeclWrapper(e.getValue())));
      }
    }
  }



  @Override public JacsalType visitIdentifier(Expr.Identifier expr) {
    Expr.VarDecl varDecl = lookup(expr.identifier);
    expr.varDecl = varDecl;
    return expr.type = varDecl.type;
  }

  @Override public JacsalType visitVarAssign(Expr.VarAssign expr) {
    expr.type = resolve(expr.identifierExpr);
    resolve(expr.expr);
    if (!expr.expr.type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert from type of right hand side (" + expr.expr.type + ") to " + expr.type, expr.operator);
    }
    if (expr.operator.is(QUESTION_EQUAL)) {
      // If using ?= have to allow for null being result when assignment doesn't occur
      expr.type = expr.type.boxed();
    }
    return expr.type;
  }

  @Override public JacsalType visitVarOpAssign(Expr.VarOpAssign expr) {
    expr.type = resolve(expr.identifierExpr);
    Expr.Binary valueExpr = (Expr.Binary) expr.expr;

    // Set the type of the Noop in our binary expression to the variable type and then
    // resolve the binary expression
    valueExpr.left.type = expr.identifierExpr.varDecl.type;
    resolve(valueExpr);

    if (!expr.expr.type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert from type of right hand side (" + expr.expr.type + ") to " + expr.type, expr.operator);
    }
    return expr.type;
  }

  @Override public JacsalType visitNoop(Expr.Noop expr) {
    expr.isConst = false;
    // Type will already have been set by parent (e.g. in visitVarOpAssign)
    return expr.type;
  }

  @Override public JacsalType visitAssign(Expr.Assign expr) {
    return resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);
  }

  @Override public JacsalType visitOpAssign(Expr.OpAssign expr) {
    Expr.Binary valueExpr = (Expr.Binary) expr.expr;
    valueExpr.left.type = ANY;     // for fields use ANY for the moment
    resolveFieldAssignment(expr, expr.parent, expr.field, valueExpr, expr.accessType);

    return expr.type;
  }

  private JacsalType resolveFieldAssignment(Expr expr, Expr parent, Expr field, Expr valueExpr, Token accessType) {
    resolve(parent);
    // Adjust type of parent based on type of access being done
    if (parent instanceof Expr.Binary) {
      Expr.Binary binaryParent = (Expr.Binary) parent;
      if (binaryParent.createIfMissing) {
        binaryParent.type = accessType.is(DOT, QUESTION_DOT) ? MAP : LIST;
      }
    }
    resolve(field);
    resolve(valueExpr);
    // Type will be type of expression but boxed if primitive since we are storing in a map/list
    return expr.type = valueExpr.type.boxed();
  }

  @Override public JacsalType visitExprString(Expr.ExprString expr) {
    expr.exprList.forEach(this::resolve);
    return expr.type = STRING;
  }

  @Override public JacsalType visitClosure(Expr.Closure expr) {
    resolve(expr.funDecl);
    return expr.type = FUNCTION;
  }

  @Override public JacsalType visitCall(Expr.Call expr) {
    resolve(expr.callee);
    expr.args.forEach(this::resolve);
    if (!expr.callee.type.is(FUNCTION,ANY) || expr.callee.isConst && expr.callee.constValue == null) {
      throw new CompileError("Expression of type " + expr.callee.type + " cannot be called", expr.token);
    }
    expr.type = ANY;
    if (expr.callee.isFunctionCall()) {
      // Special case where we know the function directly and get its return type
      Expr.FunDecl funDecl = ((Expr.Identifier) expr.callee).varDecl.funDecl;
      expr.type = funDecl.returnType;

      // Validate argument count and types against parameter types
      int mandatoryCount = Utils.mandatoryParamCount(funDecl.parameters);
      int argCount       = expr.args.size();
      int paramCount     = funDecl.parameters.size();
      if (argCount < mandatoryCount) {
        String atLeast = mandatoryCount == paramCount ? "" : "at least ";
        throw new CompileError("Missing mandatory arguments (arg count of " + argCount + " but expected " + atLeast + mandatoryCount + ")", expr.token);
      }
      if (argCount > paramCount) {
        throw new CompileError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", expr.token);
      }
      for (int i = 0; i < argCount; i++) {
        Expr       arg       = expr.args.get(i);
        JacsalType argType   = arg.type;
        JacsalType paramType = funDecl.parameters.get(i).declExpr.type;
        if (!argType.isConvertibleTo(paramType)) {
          throw new CompileError(nth(i + 1) + " argument of type " + argType + " cannot be converted to parameter type of " + paramType, arg.location);
        }
      }
    }
    return null;
  }

  ////////////////////////////////////////////

  private static String nth(int i) {
    if (i % 10 == 1 && i % 100 != 11) { return i + "st"; }
    if (i % 10 == 2 && i % 100 != 12) { return i + "nd"; }
    if (i % 10 == 3 && i % 100 != 13) { return i + "rd"; }
    return i + "th";
  }

  private static Object incOrDec(boolean isInc, JacsalType type, Object val) {
    int incAmount = isInc ? 1 : -1;
    switch (type.getType()) {
      case INT:     return ((int)val) + incAmount;
      case LONG:    return ((long)val) + incAmount;
      case DOUBLE:  return ((double)val) + incAmount;
      case DECIMAL: return ((BigDecimal)val).add(BigDecimal.valueOf(incAmount));
    }
    throw new IllegalStateException("Internal error: unexpected type " + type);
  }

  /////////////////////////

  private Stmt.While currentWhileLoop(Token token) {
    Stmt.While whileStmt = functions.peek().currentWhileLoop;
    if (whileStmt == null) {
      throw new CompileError(token.getChars() + " must be within a while/for loop", token);
    }
    return whileStmt;
  }

  private void error(String msg, Token location) {
    throw new CompileError(msg, location);
  }

  private static Expr.VarDecl UNDEFINED = new Expr.VarDecl(null, null);

  private void declare(Token name) {
    String varName = name.getStringValue();
    Map<String,Expr.VarDecl> vars = getVars();
    if (!compileContext.replMode || !isAtTopLevel()) {
      Expr.VarDecl decl = vars.get(varName);
      if (decl != null) {
        error("Variable with name " + varName + " already declared in this scope", name);
      }
    }
    // Add variable with type of UNDEFINED as a marker to indicate variable has been declared but is
    // not yet usable
    vars.put(name.getStringValue(), UNDEFINED);
  }

  private void define(Token name, Expr.VarDecl decl) {
    Expr.FunDecl function = functions.peek();
    assert function != null;
    Map<String,Expr.VarDecl> vars = getVars();

    // In repl mode we don't have a top level block and we store var types in the compileContext
    // and their actual values will be stored in the globals map.
    if (isAtTopLevel() && decl.type != FUNCTION && compileContext.replMode) {
      decl.isGlobal = true;
      decl.type = decl.type.boxed();
    }
    // Remember at what nesting level this variable is declared. If we then later have a
    // nested function that refers to this variable (closes over it) we will promote the
    // variable to a heap variable and use the difference in nesting level to work out
    // where the variable is at runtime.
    decl.nestingLevel = functions.size();
    decl.owner = function;
    vars.put(name.getStringValue(), decl);
  }

  private boolean isAtTopLevel() {
    return functions.size() == 1 && functions.peek().blocks.size() == 1;
  }

  private Map<String,Expr.VarDecl> getVars() {
    // Special case for repl mode where we ignore top level block
    if (compileContext.replMode && isAtTopLevel()) {
      return compileContext.globalVars;
    }
    return functions.peek().blocks.peek().variables;
  }

  // We look up variables in our local scope and parent scopes within the same function.
  // If we still can't find the variable we search in parent functions to see if the
  // variable exists there (at which point we close over it and it becomes a heap variable
  // rather than one that is a JVM local).
  private Expr.VarDecl lookup(Token identifier) {
    String name = identifier.getStringValue();
    Expr.VarDecl varDecl = null;
    // Search blocks in each function until we find the variable
    FUNC_LOOP:
    for (Iterator<Expr.FunDecl> funcIt = functions.iterator(); funcIt.hasNext(); ) {
      Expr.FunDecl funDecl = funcIt.next();
      for (Iterator<Stmt.Block> it = funDecl.blocks.iterator(); it.hasNext(); ) {
        Stmt.Block block = it.next();
        varDecl = block.variables.get(name);
        if (varDecl != null) {
          break FUNC_LOOP;  // Break out of both loops
        }
      }
    }

    // Look for field in classes
    if (varDecl == null) {
      for (Iterator<Stmt.ClassDecl> it = classes.iterator(); it.hasNext();) {
        varDecl = it.next().fieldVars.get(name);
        if (varDecl != null) {
          break;
        }
      }
    }

    if (varDecl == null && compileContext.replMode) {
      varDecl = compileContext.globalVars.get(name);
    }
    if (varDecl == UNDEFINED) {
      throw new CompileError("Variable initialisation cannot refer to itself", identifier);
    }
    if (varDecl != null) {
      if (varDecl.isGlobal || varDecl.type.is(FUNCTION) || varDecl.nestingLevel == functions.size()) {
        // Normal local or global variable (not a closed over var) or a function.
        // Functions can be called from wherever they are visible and not stored in local stack vars.
        return varDecl;
      }

      // If nesting level is different and if variable is a local variable then turn it into
      // a heap var
      varDecl.isHeap = true;

      Expr.FunDecl currentFunc = functions.peek();

      // Add this var to our list of heap vars we need passed in to us
      Expr.VarDecl heapVarDecl = currentFunc.heapVars.get(name);
      if (heapVarDecl == null) {
        // Wrap in another VarDecl so we can have per-function slot allocated
        heapVarDecl = varDeclWrapper(varDecl);
        currentFunc.heapVars.put(name, heapVarDecl);
      }
      // Return VarDecl for our function
      return heapVarDecl;
    }

    error("Reference to unknown variable " + name, identifier);
    return null;
  }

  private void throwIf(boolean condition, String msg, Token location) {
    if (condition) {
      throw new CompileError(msg, location);
    }
  }

  private Expr.VarDecl varDeclWrapper(Expr.VarDecl varDecl) {
    Expr.VarDecl heapVarDecl = new Expr.VarDecl(varDecl.name, null);
    heapVarDecl.type = varDecl.type.boxed();
    heapVarDecl.varDecl = varDecl;
    heapVarDecl.isHeap = true;
    heapVarDecl.isParam = true;
    return heapVarDecl;
  }

  /////////////////////////////////////////////////

  /**
   * Find last statement and turn it into a return statement if not already a return statement. This is used to turn
   * implicit returns in functions into explicit returns to simplify the job of the Resolver and Compiler phases.
   */
  private Stmt explicitReturn(Stmt stmt, JacsalType returnType) {
//    try {
      return doExplicitReturn(stmt, returnType);
//    }
//    catch (CompileError e) {
//      // Error could be due to previous error so if there have been previous
//      // errors ignore this one
//      if (errors.size() == 0) {
//        throw e;
//      }
//    }
//    return null;
  }

  private Stmt doExplicitReturn(Stmt stmt, JacsalType returnType) {
    if (stmt instanceof Stmt.Return) {
      // Nothing to do
      return stmt;
    }

    if (stmt instanceof Stmt.Block) {
      List<Stmt> stmts = ((Stmt.Block) stmt).stmts.stmts;
      if (stmts.size() == 0) {
        throw new CompileError("Missing explicit/implicit return statement for function", stmt.location);
      }
      Stmt newStmt = explicitReturn(stmts.get(stmts.size() - 1), returnType);
      stmts.set(stmts.size() - 1, newStmt);   // Replace implicit return with explicit if necessary
      return stmt;
    }

    if (stmt instanceof Stmt.If) {
      Stmt.If ifStmt = (Stmt.If) stmt;
      if (ifStmt.trueStmt == null || ifStmt.falseStmt == null) {
        if (returnType.isPrimitive()) {
          throw new CompileError("Implicit return of null for  " +
                                 (ifStmt.trueStmt == null ? "true" : "false") + " condition of if statment not compatible with return type of " + returnType, stmt.location);
        }
        if (ifStmt.trueStmt == null) {
          ifStmt.trueStmt = new Stmt.Return(ifStmt.ifToken, new Expr.Literal(new Token(NULL, ifStmt.ifToken)), returnType);
        }
        if (ifStmt.falseStmt == null) {
          ifStmt.falseStmt = new Stmt.Return(ifStmt.ifToken, new Expr.Literal(new Token(NULL, ifStmt.ifToken)), returnType);
        }
      }
      ifStmt.trueStmt  = doExplicitReturn(((Stmt.If) stmt).trueStmt, returnType);
      ifStmt.falseStmt = doExplicitReturn(((Stmt.If) stmt).falseStmt, returnType);
      return stmt;
    }

    // Turn implicit return into explicit return
    if (stmt instanceof Stmt.ExprStmt) {
      Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) stmt;
      Expr          expr     = exprStmt.expr;
      expr.isResultUsed = true;
      Stmt.Return returnStmt = new Stmt.Return(exprStmt.location, expr, returnType);
      return returnStmt;
    }

    // If last statement is an assignment then value of assignment is the returned value.
    // We set a flag on the statement so that Compiler knows to leave result on the stack
    // and replace the assignment statement with a return wrapping the assignment expression.
    if (stmt instanceof Stmt.VarDecl) {
      Expr.VarDecl declExpr = ((Stmt.VarDecl) stmt).declExpr;
      declExpr.isResultUsed = true;
      Stmt.Return returnStmt = new Stmt.Return(stmt.location, declExpr, returnType);
      return returnStmt;
    }

    // If last statement is a function declaration then we return the MethodHandle for the
    // function as the return value of our function
    // We set a flag on the statement so that Compiler knows to leave result on the stack
    // and replace the assignment statement with a return wrapping the assignment expression.
    if (stmt instanceof Stmt.FunDecl) {
      Expr.FunDecl declExpr = ((Stmt.FunDecl)stmt).declExpr;
      declExpr.isResultUsed = true;
      declExpr.varDecl.isResultUsed = true;
      Stmt.Return returnStmt = new Stmt.Return(stmt.location, declExpr, returnType);
      return returnStmt;
    }

    // For functions that return ANY there is an implicit "return null" even if last statement
    // does not have a value so replace stmt with list of statements that include the stmt and
    // then a "return null" statement.
    if (returnType.is(ANY)) {
      Stmt.Stmts stmts = new Stmt.Stmts();
      stmts.stmts.add(stmt);
      stmts.location = stmt.location;
      Stmt.Return returnStmt = new Stmt.Return(stmt.location, new Expr.Literal(new Token(NULL, stmt.location)), returnType);
      stmts.stmts.add(returnStmt);
      return stmts;
    }

    // Other statements are not supported for implicit return
    throw new CompileError("Unsupported statement type for implicit return", stmt.location);
  }

}
