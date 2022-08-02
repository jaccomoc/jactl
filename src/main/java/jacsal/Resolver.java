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
import java.util.Map;
import java.util.Set;

import static jacsal.TokenType.*;

/**
 * The Resolver visits all statements and all expressions and performs the following:
 *  - tracks variables and their type
 *  - resolves references to symbols (variables, methods, etc)
 *  - evaluates any expressions made up of only constants (literals)
 */
public class Resolver implements Expr.Visitor<JacsalType> {

  private final CompileContext     compileContext;
  private final Map<String,Object> bindings;

  /**
   * Resolve variables, references, etc
   * @param bindings  map of global variables (which themseovles can be Maps or Lists or simple values)
   */
  Resolver(CompileContext compileContext, Map<String,Object> bindings) {
    this.compileContext = compileContext;
    this.bindings       = bindings;
  }

  JacsalType resolve(Expr expr) {
    return expr.accept(this);
  }

  @Override public JacsalType visitBinary(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    expr.isConst = expr.left.isConst && expr.right.isConst;
    expr.type = JacsalType.result(expr.left.type, expr.operator, expr.right.type);
    if (expr.isConst) {
      switch (expr.type) {
        case STRING: {
          if (expr.operator.isNot(PLUS,STAR)) { throw new IllegalStateException("Internal error: operator " + expr.operator.getStringValue() + " not supported for Strings"); }
          if (expr.operator.is(PLUS)) {
            expr.constValue = Utils.toString(expr.left.constValue) + Utils.toString(expr.right.constValue);
          }
          else {
            String lhs    = Utils.toString(expr.left.constValue);
            long   length = Utils.toLong(expr.right.constValue);
            if (length < 0) {
              throw new CompileError("String repeat count must be >= 0", expr.right.location);
            }
            expr.constValue = lhs.repeat((int)length);
          }
          break;
        }
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
    else
    if (expr.expr.type.isNumeric()) {
      expr.type = expr.expr.type;
      expr.constValue = expr.expr.constValue;
      if (expr.operator.is(MINUS)) {
        switch (expr.type) {
          case INT:     expr.constValue = -(int)expr.constValue;                  break;
          case LONG:    expr.constValue = -(long)expr.constValue;                 break;
          case DOUBLE:  expr.constValue = -(double)expr.constValue;               break;
          case DECIMAL: expr.constValue = ((BigDecimal)expr.constValue).negate(); break;
        }
      }
      return expr.type;
    }
    throw new CompileError("Unary operator '" + expr.operator.getStringValue() + "' cannot be applied to type " + expr.expr.type, expr.operator);
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

  @Override public JacsalType visitIdentifier(Expr.Identifier expr) {
    throw new UnsupportedOperationException();
  }

  @Override public JacsalType visitExprString(Expr.ExprString expr) {
    return expr.type = JacsalType.STRING;
  }

  /////////////////////////

  private static Set<TokenType> numericOperators = Set.of(PLUS, MINUS, STAR, SLASH, PERCENT);

}
