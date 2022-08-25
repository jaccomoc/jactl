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
 *  - allocates and keeps track of what local variable slots are in use
 *  - evaluates any simple expressions made up of only constants (literals)
 *
 * The Resolver also needs to check for subexpressions where we might have a try/catch
 * required. This could occur, for example, with ?= assignment where we catch NullError
 * during evaluation of the rhs, or where we can throw a Continuation to suspend execution.
 * The problem is that JVM will discard any intermediate values on the stack when the
 * exception is thrown so we can't rely on use of the stack for complex expressions if
 * we know we might need to catch an exception in the middle of the expression.
 *
 * The Resolver finds all such examples where it is possible that an expression needs
 * to catch an exception and marks earlier expressions where we would normally have left
 * something on the stack to preserve these intermediate values in temporary locals
 * instead.
 */
public class Resolver implements Expr.Visitor<JacsalType>, Stmt.Visitor<Void> {

  private final CompileContext      compileContext;
  private final Map<String,Object>  globals;
  private final Deque<Stmt.Block>   blocks = new ArrayDeque<>();
  private final Deque<Stmt.FunDecl> functions = new ArrayDeque<>();

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

  JacsalType resolve(Expr expr) {
    if (expr != null) {
      return expr.accept(this);
    }
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Stmt

  @Override public Void visitScript(Stmt.Script stmt) {
    return resolve(stmt.function);
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    functions.push(stmt);
    stmt.slotIdx = 2;
    stmt.maxSlot = 2;
    try {
      return resolve(stmt.block);
    }
    finally {
      functions.pop();
    }
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    stmt.stmts.forEach(this::resolve);
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    blocks.push(stmt);
    try {
      return resolve(stmt.stmts);
    }
    finally {
      functions.peek().slotIdx -= blocks.peek().slotsUsed;
      blocks.pop();
    }
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    resolve(stmt.declExpr);
    stmt.type = stmt.declExpr.type;
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    resolve(stmt.expr);
    if (!stmt.expr.type.isConvertibleTo(stmt.type)) {
      throw new CompileError("Expression type not compatible with return type of function", stmt.expr.location);
    }
    return null;
  }

  @Override
  public Void visitIf(Stmt.If stmt) {
    resolve(stmt.condtion);
    resolve(stmt.trueStmts);
    resolve(stmt.falseStmts);
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Expr

  @Override public JacsalType visitBinary(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);

    expr.isConst = expr.left.isConst && expr.right.isConst;
    if (expr.left.isConst && expr.left.constValue == null) {
      throw new CompileError("Left-hand side of '" + expr.operator.getStringValue() + "' cannot be null", expr.left.location);
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
        if (expr.operator.isNot(PLUS,STAR)) { throw new IllegalStateException("Internal error: operator " + expr.operator.getStringValue() + " not supported for Strings"); }
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

      // TBD: bitwise operators and boolean operators

      if (expr.left.constValue == null)  { throw new CompileError("Non-numeric operand for left-hand side of '" + expr.operator.getStringValue() + "': cannot be null", expr.operator); }
      if (expr.right.constValue == null) { throw new CompileError("Non-numeric operand for right-hand side of '" + expr.operator.getStringValue() + "': cannot be null", expr.operator); }

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
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getStringValue() + " not supported for ints");
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
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getStringValue() + " not supported for longs");
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
            default: throw new IllegalStateException("Internal error: operator " + expr.operator.getStringValue() + " not supported for doubles");
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
      expr.type = expr.expr.type;
      if (!expr.type.isNumeric() && !expr.type.is(ANY)) {
        throw new CompileError("Prefix operator '" + expr.operator.getStringValue() + "' cannot be applied to type " + expr.expr.type, expr.operator);
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
            throw new CompileError("Prefix operator '" + expr.operator.getStringValue() + "': null value encountered", expr.expr.location);
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
          throw new CompileError("Postfix operator '" + expr.operator.getStringValue() + "': null value encountered", expr.expr.location);
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
    expr.type = ANY;
    return expr.type;
  }

  @Override public JacsalType visitAssign(Expr.Assign expr) {
    return resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);
  }

  @Override public JacsalType visitOpAssign(Expr.OpAssign expr) {
    Expr.Binary valueExpr = (Expr.Binary) expr.expr;

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

  /////////////////////////

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

  private void error(String msg, Token location) {
    throw new CompileError(msg, location);
  }

  private static Expr.VarDecl UNDEFINED = new Expr.VarDecl(null, null);

  private void declare(Token name) {
    String varName = (String)name.getValue();
    Map<String,Expr.VarDecl> vars = getVars();
    if (!compileContext.replMode) {
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
    Stmt.FunDecl function = functions.peek();
    assert function != null;
    Map<String,Expr.VarDecl> vars = getVars();

    // In repl mode we don't have a top level block and we store var types in the compileContext
    // and their actual values will be stored in the globals map.
    if (blocks.size() == 1 && compileContext.replMode) {
      decl.isGlobal = true;
      decl.type = decl.type.boxed();
    }
    else {
      Stmt.Block block = blocks.peek();
      decl.slot = function.slotIdx;
      int slotsUsed = decl.type.is(JacsalType.LONG, JacsalType.DOUBLE) ? 2 : 1;
      function.slotIdx += slotsUsed;
      function.maxSlot = Math.max(function.slotIdx, function.maxSlot);
      block.slotsUsed += slotsUsed;
    }
    vars.put((String)name.getValue(), decl);
  }

  private Map<String,Expr.VarDecl> getVars() {
    // Special case for repl mode where we ignore top level block
    if (compileContext.replMode && blocks.size() == 1) {
      return compileContext.globalVars;
    }
    return blocks.peek().variables;
  }

  private Expr.VarDecl lookup(Token identifier) {
    String name = (String)identifier.getValue();
    Expr.VarDecl varDecl = null;
    for (Iterator<Stmt.Block> it = blocks.descendingIterator(); it.hasNext(); ) {
      Stmt.Block   block   = it.next();
      varDecl = block.variables.get(name);
      if (varDecl != null) {
        break;
      }
    }
    if (varDecl == null && compileContext.replMode) {
      varDecl = compileContext.globalVars.get(name);
    }
    if (varDecl == UNDEFINED) {
      throw new CompileError("Variable initialisation cannot refer to itself", identifier);
    }
    if (varDecl != null) {
      return varDecl;
    }

    error("Reference to unknown variable " + name, identifier);
    return null;
  }

  private void throwIf(boolean condition, String msg, Token location) {
    if (condition) {
      throw new CompileError(msg, location);
    }
  }
}
