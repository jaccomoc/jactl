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

import static jacsal.TokenType.*;

/**
 * The Resolver visits all statements and all expressions and performs the following:
 *  - tracks variables and their type
 *  - resolves references to symbols (variables, methods, etc)
 *  - allocates and keeps track of what local variable slots are in use
 *  - evaluates any expressions made up of only constants (literals)
 */
public class Resolver implements Expr.Visitor<JacsalType>, Stmt.Visitor<Void> {

  private final CompileContext      compileContext;
  private final Map<String,Object>  bindings;
  private final Deque<Stmt.Block>   blocks = new ArrayDeque<>();
  private final Deque<Stmt.FunDecl> functions = new ArrayDeque<>();

  /**
   * Resolve variables, references, etc
   * @param bindings  map of global variables (which themseovles can be Maps or Lists or simple values)
   */
  Resolver(CompileContext compileContext, Map<String,Object> bindings) {
    this.compileContext = compileContext;
    this.bindings       = bindings;
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
    expr.type = JacsalType.result(expr.left.type, expr.operator, expr.right.type);
    if (expr.isConst) {
      if (expr.type.is(JacsalType.STRING)) {
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

      switch (expr.type) {
        case INT: {
          int left  = Utils.toInt(expr.left.constValue);
          int right = Utils.toInt(expr.right.constValue);
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
          expr.constValue = RuntimeUtils.decimalBinaryOperation(left, right, RuntimeUtils.getOperatorType(expr.operator.getType()), compileContext.getMaxScale());
          break;
        }
      }
    }
    return expr.type;
  }

  @Override public JacsalType visitPrefixUnary(Expr.PrefixUnary expr) {
    resolve(expr.expr);
    expr.isConst = expr.expr.isConst;
    if (expr.operator.is(BANG)) {
      expr.type = JacsalType.BOOLEAN;
      if (expr.isConst) {
        expr.constValue = !Utils.toBoolean(expr.expr.constValue);
      }
      return expr.type;
    }
    else {
      expr.type = expr.expr.type;
      if (!expr.type.isNumeric() && !expr.type.is(JacsalType.ANY)) {
        throw new CompileError("Prefix operator '" + expr.operator.getStringValue() + "' cannot be applied to type " + expr.expr.type, expr.operator);
      }
      if (expr.isConst) {
        expr.constValue = expr.expr.constValue;
        if (expr.operator.is(MINUS)) {
          switch (expr.type) {
            case INT:     expr.constValue = -(int)expr.constValue;                  break;
            case LONG:    expr.constValue = -(long)expr.constValue;                 break;
            case DOUBLE:  expr.constValue = -(double)expr.constValue;               break;
            case DECIMAL: expr.constValue = ((BigDecimal)expr.constValue).negate(); break;
          }
        }
        else
        if (expr.operator.is(PLUS_PLUS,MINUS_MINUS)) {
          int incAmount = expr.operator.is(PLUS_PLUS) ? 1 : -1;
          switch (expr.type) {
            case INT:     expr.constValue = ((int)expr.constValue) + incAmount;     break;
            case LONG:    expr.constValue = ((long)expr.constValue) + incAmount;    break;
            case DOUBLE:  expr.constValue = ((double)expr.constValue) + incAmount;  break;
            case DECIMAL:
              expr.constValue = ((BigDecimal)expr.constValue).add(BigDecimal.valueOf(incAmount));
              break;
          }
        }
      }
      return expr.type;
    }
  }

  @Override public JacsalType visitPostfixUnary(Expr.PostfixUnary expr) {
    resolve(expr.expr);
    expr.isConst = expr.expr.isConst;
    if (expr.expr.type == JacsalType.BOOLEAN) {
      return expr.type = JacsalType.INT;
    }
    if (expr.expr.type.isNumeric()) {
      return expr.type = expr.expr.type;
    }
    throw new CompileError("Unary operator " + expr.operator + " cannot be applied to type " + expr.expr.type, expr.operator);
  }

  @Override public JacsalType visitLiteral(Expr.Literal expr) {
    // Whether we optimise const expressions by evaluating at compile time
    // is controlled by CompileContext (defaults to true).
    expr.isConst    = compileContext.evaluateConstExprs();
    expr.constValue = expr.value.getValue();

    switch (expr.value.getType()) {
      case INTEGER_CONST: return expr.type = JacsalType.INT;
      case LONG_CONST:    return expr.type = JacsalType.LONG;
      case DOUBLE_CONST:  return expr.type = JacsalType.DOUBLE;
      case DECIMAL_CONST: return expr.type = JacsalType.DECIMAL;
      case STRING_CONST:  return expr.type = JacsalType.STRING;
      case TRUE:          return expr.type = JacsalType.BOOLEAN;
      case FALSE:         return expr.type = JacsalType.BOOLEAN;
      case NULL:          return expr.type = JacsalType.ANY;
      default:
        throw new IllegalStateException("Unknown literal type " + expr.value.getType());
    }
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
    return null;
  }

  @Override public JacsalType visitExprString(Expr.ExprString expr) {
    return expr.type = JacsalType.STRING;
  }

  /////////////////////////

  private void error(String msg, Token location) {
    throw new CompileError(msg, location);
  }

  private static Expr.VarDecl UNDEFINED = new Expr.VarDecl(null, null);

  private void declare(Token name) {
    var        block   = blocks.peek();
    String     varName = (String)name.getValue();
    assert block != null;
    Expr.VarDecl decl = block.variables.get(varName);
    if (decl != null) {
      error("Variable with name " + varName + " already declared in this scope", name);
    }
    // Add variable with type of UNDEFINED as a marker to indicate variable has been declared but is
    // not yet usable
    block.variables.put(name.getStringValue(), UNDEFINED);
  }

  private void define(Token name, Expr.VarDecl decl) {
    Stmt.FunDecl function = functions.peek();
    assert function != null;
    Stmt.Block block = blocks.peek();
    assert block != null;

    decl.slot = function.slotIdx;
    int slotsUsed = decl.type.is(JacsalType.LONG,JacsalType.DOUBLE) ? 2 : 1;
    function.slotIdx += slotsUsed;
    function.maxSlot = Math.max(function.slotIdx, function.maxSlot);
    block.slotsUsed  += slotsUsed;
    block.variables.put((String)name.getValue(), decl);
  }

  private Expr.VarDecl lookup(Token identifier) {
    String name = (String)identifier.getValue();
    for (Iterator<Stmt.Block> it = blocks.descendingIterator(); it.hasNext(); ) {
      Stmt.Block   block   = it.next();
      Expr.VarDecl varDecl = block.variables.get(name);
      if (varDecl != null) {
        return varDecl;
      }
    }
    error("Reference to unknown variable " + name, identifier);
    return null;
  }

}
