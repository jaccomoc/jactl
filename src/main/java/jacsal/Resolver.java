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

import java.util.Set;

import static jacsal.TokenType.*;

public class Resolver implements Expr.Visitor<JacsalType> {

  private JacsalType resolve(Expr expr) {
    return expr.accept(this);
  }

  @Override public JacsalType visitBinary(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    if (expr.left.type == JacsalType.STRING || expr.right.type == JacsalType.STRING) {
      boolean leftIsString = expr.left.type == JacsalType.STRING;
      if (expr.operator.isNot(PLUS)) {
        throw new CompileError("Invalid operator " + expr.operator.getValue() + " for string argument",
                               leftIsString ? expr.left.location : expr.right.location);
      }
      return expr.type = JacsalType.STRING;
    }
    return expr.type = JacsalType.result(expr.left.type, expr.right.type);
  }


  @Override public JacsalType visitPrefixUnary(Expr.PrefixUnary expr) {
    resolve(expr.expr);
    if (expr.operator.is(BANG)) {
      return expr.type = JacsalType.BOOLEAN;
    }
    if (expr.expr.type == JacsalType.BOOLEAN) {
      return expr.type = JacsalType.INT;
    }
    if (expr.expr.type.isNumeric()) {
      return expr.type = expr.expr.type;
    }
    throw new CompileError("Unary operator " + expr.operator + " cannot be applied to type " + expr.expr.type, expr.operator);
  }

  @Override public JacsalType visitPostfixUnary(Expr.PostfixUnary expr) {
    if (expr.expr.type == JacsalType.BOOLEAN) {
      return expr.type = JacsalType.INT;
    }
    if (expr.expr.type.isNumeric()) {
      return expr.type = expr.expr.type;
    }
    throw new CompileError("Unary operator " + expr.operator + " cannot be applied to type " + expr.expr.type, expr.operator);
  }

  @Override public JacsalType visitLiteral(Expr.Literal expr) {
    switch (expr.value.getType()) {
      case INTEGER_CONST: return expr.type = JacsalType.INT;
      case LONG_CONST:    return expr.type = JacsalType.LONG;
      case DOUBLE_CONST:  return expr.type = JacsalType.DOUBLE;
      case DECIMAL_CONST: return expr.type = JacsalType.DECIMAL;
      case STRING_CONST:  return expr.type = JacsalType.STRING;
      case TRUE:          return expr.type = JacsalType.BOOLEAN;
      case FALSE:         return expr.type = JacsalType.BOOLEAN;
      case NULL:          return expr.type = JacsalType.ANY;
    }
    throw new IllegalStateException("Unknown literal type " + expr.value.getType());
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
