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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static jacsal.TokenType.*;

public class Parser {
  Tokeniser tokeniser;

  public Parser(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  public Expr parseExpression() {
    Expr expr = expression();
    expect(EOF);
    return expr;
  }

  ////////////////////////////////////////////

  private static List<List<TokenType>> operatorsByPrecedence =
    List.of(
/*
      List.of(AND, OR),
      List.of(EQUAL, STAR_STAR_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PLUS_EQUAL, MINUS_EQUAL,
              DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN_EQUAL, AMPERSAND_EQUAL,
              PIPE_EQUAL, ACCENT_EQUAL, QUESTION_EQUAL),
      List.of(QUESTION, QUESTION_COLON),
      List.of(PIPE_PIPE),
      List.of(AMPERSAND_AMPERSAND),
      List.of(PIPE),
      List.of(ACCENT),
      List.of(AMPERSAND),
      List.of(EQUAL_EQUAL, BANG_EQUAL, COMPARE, TRIPLE_EQUAL, BANG_EQUAL_EQUAL),
      List.of(LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, IN, BANG_IN, INSTANCE_OF, BANG_INSTANCE_OF, AS),
      List.of(DOUBLE_LESS_THAN, DOUBLE_GREATER_THAN, TRIPLE_GREATER_THAN),
*/
      List.of(MINUS, PLUS),
      List.of(STAR, SLASH, PERCENT)
      //      List.of(STAR_STAR)
      //      List.of(GRAVE, BANG, MINUS_MINUS, PLUS_PLUS),
    );

  /**
   ** expression -> expression operator expression;
   *
   * Recursively parse expressions base on operator precedence.
   * @return the parsed expression
   */
  private Expr expression() {
    return parseExpression(0);
  }

  private Expr parseExpression(int level) {
    if (level == operatorsByPrecedence.size()) {
      return unary();
    }

    var expr = parseExpression(level + 1);

    var operators = operatorsByPrecedence.get(level);
    while (matchAny(operators)) {
      expr = new Expr.Binary(expr, previous(), parseExpression(level + 1));
    }

    return expr;
  }

  /**
   ** unary -> ( "!" | "--" | "++" | "-" | "+" ) unary
   **        | primary;
   */
  private Expr unary() {
    if (matchAny(BANG, /* MINUS_MINUS, PLUS_PLUS, */ MINUS, PLUS)) {
      return new Expr.PrefixUnary(previous(), unary());
    }
    return primary();
  }

  /**
   ** primary -> INTEGER_CONST | DECIMAL_CONST | DOUBLE_CONST | STRING_CONST | "true" | "false" | "null"
   **          | exprString
   **          | IDENTIFIER;
   */
  private Expr primary() {
    if (matchAny(INTEGER_CONST, LONG_CONST, DECIMAL_CONST, DOUBLE_CONST, STRING_CONST, TRUE, FALSE, NULL)) {
      return new Expr.Literal(previous());
    }

    if (peek().is(EXPR_STRING_START)) {
      return exprString();
    }

    if (matchAny(IDENTIFIER)) {
      return new Expr.Identifier(previous());
    }

    if (matchAny(LEFT_PAREN)) {
      Expr nested = expression();
      expect(RIGHT_PAREN);
      return nested;
    }

    return unexpected("Expecting literal or identifier or bracketed expression");
  }

  /**
   ** exprString -> EXPR_STRING_START ( expression | STRING_CONST ) * EXPR_STRING_END;
   */
  private Expr exprString() {
    Expr.ExprString exprString = new Expr.ExprString(expect(EXPR_STRING_START));
    // Turn the EXPR_STRING_START into a string literal and make it the first in our expr list
    exprString.exprList.add(new Expr.Literal(new Token(STRING_CONST, previous())));
    while (!matchAny(EXPR_STRING_END)) {
      if (matchAny(STRING_CONST)) {
        exprString.exprList.add(new Expr.Literal(previous()));
      }
      else {
        exprString.exprList.add(expression());
      }
    }
    return exprString;
  }

  /////////////////////////////////////////////////

  private Token advance() {
    return tokeniser.next();
  }

  private Token peek() {
    return tokeniser.peek();
  }

  private Token previous() {
    return tokeniser.previous();
  }

  /**
   * Check if next token matches any of the given types.
   * If it matches then consume the token and return true.
   * If it does not match one of the types then return false and stay in current position in stream of tokens.
   * @param types  the types to match agains
   * @return true if next token matches, false is not
   */
  private boolean matchAny(TokenType... types) {
    for (TokenType type: types) {
      if (peek().is(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean matchAny(List<TokenType> types) {
    for (TokenType type: types) {
      if (peek().is(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Expr error(String msg) {
    throw new CompileError(msg, peek());
  }

  private Expr unexpected(String msg) {
    throw new CompileError("Unexpected token " + peek() + ": " + msg, peek());
  }

  /**
   * Expect one of the given types and throw an error if no match.
   * Consume and return the token matched.
   * @param types  types to match against
   * @return the matched token or throw error if no match
   */
  private Token expect(TokenType... types) {
    if (matchAny(types)) {
      return previous();
    }
    if (types.length > 1) {
      error("Unexpected token. Expecting one of " + Arrays.stream(types).map(Enum::toString).collect(Collectors.joining(", ")));
    }
    else {
      error("Unexpected token. Expecting " + types[0]);
    }
    return null;
  }
}
