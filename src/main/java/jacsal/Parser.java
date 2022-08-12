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

import java.util.*;
import java.util.stream.Collectors;

import static jacsal.JacsalType.ANY;
import static jacsal.TokenType.*;

/**
 * Recursive descent parser for parsing the Jacsal language.
 * In general we try to avoid too much lookahead in the parsing to make the parsing efficient
 * but there are a couple of places where lookahead is required to disambiguate parts of the
 * grammar.
 * Usually a single token lookahead suffices.
 *
 * After the parsing has been done and the AST generated, the resulting AST needs to be passed
 * to the Resolver which resolves all variable references and does as much validation as possible
 * to validate types of operands in expressions.
 *
 * Finally the Compiler then should be invoked to compile the AST into JVM byte code.
 */
public class Parser {
  Tokeniser          tokeniser;
  Token              firstToken = null;
  List<CompileError> errors     = new ArrayList<>();

  // Stack of blocks so we can always quickly find the current block
  Deque<Stmt.Block> blockStack = new ArrayDeque<>();

  // Stack of function declarations (including nested closures) so we can find current function
  Deque<Stmt.FunDecl> functions = new ArrayDeque<>();

  public Parser(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  public Stmt.Script parse() {
    Stmt.Script script = script();
    if (errors.size() > 1) {
      throw new CompileError(errors);
    }
    if (errors.size() == 1) {
      throw errors.get(0);
    }
    return script;
  }

  public Expr parseExpression() {
    Expr expr = expression();
    expect(EOF);
    return expr;
  }

  ////////////////////////////////////////////

  // = Stmt

  /**
   *# script -> block;
   */
  private Stmt.Script script() {
    Stmt.FunDecl funDecl = new Stmt.FunDecl(new Token(IDENTIFIER, peek()).setValue(Utils.JACSAL_SCRIPT_MAIN),
                                            ANY);
    functions.push(funDecl);
    try {
      Stmt.Block block = block(EOF);
      try {
        explicitReturn(block, ANY);
      }
      catch (CompileError e) {
        // Error could be due to previous error so if there have been previous
        // errors ignore this one
        if (errors.size() == 0) {
          throw e;
        }
      }
      funDecl.block = block;
      return new Stmt.Script(funDecl);
    } finally {
      functions.pop();
    }
  }

  /**
   *# block -> "{" stmts "}"
   *#        | stmts EOF      // Special case for top most script block
   *#        ;
   */
  private Stmt.Block block(TokenType endBlock) {
    Stmt.Stmts stmts = new Stmt.Stmts();
    Stmt.Block block = new Stmt.Block(stmts);
    block.location = peek();
    blockStack.push(block);
    stmts(stmts);
    expect(endBlock);
    blockStack.pop();
    return block;
  }

  /**
   *# stmts -> declaration* ;
   */
  private void stmts(Stmt.Stmts stmts) {
    Stmt previousStmt = null;
    stmts.location = peek();
    while (true) {
      matchAny(EOL);     // Ignore new lines if present
      if (peek().is(EOF, RIGHT_BRACE)) {
        break;
      }

      try {
        Token location    = peek();
        Stmt  declaration = declaration();
        if (declaration != null) {
          stmts.stmts.add(declaration);
          if (previousStmt instanceof Stmt.Return) {
            throw new CompileError("Unreachable statement", location);
          }
          previousStmt = declaration;
        }
      }
      catch (CompileError e) {
        errors.add(e);
        // Consume until end of statement to try to allow further
        // parsing and error checking to occur
        consumeUntil(EOL, EOF, RIGHT_BRACE);
      }
    }
  }

  /**
   *# declaration -> varDecl
   *#              | statement;
   */
  private Stmt declaration() {
    if (matchAny(VAR, DEF, INT, LONG, DOUBLE, DECIMAL, STRING)) {
      return varDecl();
    }
    if (matchAny(SEMICOLON)) {
      // Empty statement
      return null;
    }
    return statement();
  }

  /**
   *# statement -> block
   *#            | exprStatement;
   */
  private Stmt statement() {
    if (matchAny(LEFT_BRACE)) {
      return block(RIGHT_BRACE);
    }
    if (matchAny(RETURN)) {
      return returnStmt();
    }
    return exprStmt();
  }

  /**
   *# varDecl -> ("var" | "int" | "long" | "double" | "Decimal" | "String" ) IDENTIFIER ( "=" expression ) ? ;
   */
  private Stmt.VarDecl varDecl() {
    Token typeToken   = previous();
    Token identifier  = expect(IDENTIFIER);
    Expr  initialiser = null;
    if (matchAny(EQUAL)) {
      initialiser = expression();
    }

    JacsalType type;
    if (typeToken.is(VAR)) {
      if (initialiser == null) {
        unexpected("Initialiser expression required for 'var' declaration");
      }
      // For "var" we need to wait until Resolver works out the actual type so we use null
      // to indicate we don't have a type yet.
      type = null;
    }
    else {
      // Convert token to a JacsalType
      type = typeToken.is(DEF) ? ANY
                               : JacsalType.valueOf(typeToken.getType().toString());
    }

    Expr.VarDecl varDecl = new Expr.VarDecl(identifier, initialiser);
    varDecl.isResultUsed = false;      // Result not used unless last stmt of a function used as implicit return
    varDecl.type = type;
    varDecl.location = typeToken;
    return new Stmt.VarDecl(varDecl);
  }

  /**
   *# exprStmt -> expression;
   */
  private Stmt exprStmt() {
    Token location = peek();
    Expr  expr     = expression();
    expr.isResultUsed = false;    // Expression is a statement so result not used
    Stmt stmt = new Stmt.ExprStmt(expr);
    stmt.location = location;
    if (peek().isNot(EOF, EOL, SEMICOLON, RIGHT_BRACE)) {
      unexpected("Expected end of expression");
    }
    return stmt;
  }

  /**
   *# returnStmt -> "return" expression;
   */
  private Stmt returnStmt() {
    Token       location   = previous();
    Stmt.Return returnStmt = new Stmt.Return(expression());
    returnStmt.location = location;
    // Return statement must convert expression to return type of function
    returnStmt.type = functions.peek().returnType;
    return returnStmt;
  }

  ////////////////////////////////////////////

  // = Expr

  private static List<List<TokenType>> operatorsByPrecedence =
    List.of(
/*
      List.of(AND, OR), */
      List.of(EQUAL), /* STAR_STAR_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PLUS_EQUAL, MINUS_EQUAL,
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
   *# expression -> expression operator expression;
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
      Token operator = previous();
      // Check for lvalue for assignment operators
      if (operator.getType().isAssignmentLike()) {
        if (!(expr instanceof Expr.Identifier)) {
          throw new CompileError("Need a valid lvalue for assignment", operator);
        }
        expr = new Expr.VarAssign((Expr.Identifier) expr, operator, parseExpression(level + 1));
      }
      else {
        expr = new Expr.Binary(expr, operator, parseExpression(level + 1));
      }
    }

    return expr;
  }

  /**
   *# unary -> ( "!" | "--" | "++" | "-" | "+" ) unary ( "--" | "++" )
   *#        | primary;
   */
  private Expr unary() {
    Expr expr;
    if (matchAny(BANG, MINUS_MINUS, PLUS_PLUS, MINUS, PLUS)) {
      expr = new Expr.PrefixUnary(previous(), unary());
    }
    else {
      expr = primary();
    }
    if (matchAny(PLUS_PLUS, MINUS_MINUS)) {
      expr = new Expr.PostfixUnary(expr, previous());
    }
    return expr;
  }

  /**
   *# primary -> INTEGER_CONST | DECIMAL_CONST | DOUBLE_CONST | STRING_CONST | "true" | "false" | "null"
   *#          | exprString
   *#          | IDENTIFIER;
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
   *# exprString -> EXPR_STRING_START ( expression | STRING_CONST ) * EXPR_STRING_END;
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
    Token token = tokeniser.next();
    if (firstToken == null) {
      firstToken = token;
    }
    return token;
  }

  private Token peek() {
    Token token = tokeniser.peek();
    if (firstToken == null) {
      firstToken = token;
    }
    return token;
  }

  private Token previous() {
    return tokeniser.previous();
  }

  /**
   * Check if next token matches any of the given types. If it matches then consume the token and return true. If it
   * does not match one of the types then return false and stay in current position in stream of tokens.
   *
   * @param types the types to match agains
   * @return true if next token matches, false is not
   */
  private boolean matchAny(TokenType... types) {
    for (TokenType type : types) {
      if (peek().is(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean matchAny(List<TokenType> types) {
    for (TokenType type : types) {
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
    error("Unexpected token " + peek().getType() + ": " + msg);
    return null;
  }

  /**
   * Expect one of the given types and throw an error if no match. Consume and return the token matched.
   *
   * @param types types to match against
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

  private void consumeUntil(TokenType... types) {
    while (peek().isNot(types)) {
      advance();
    }
  }

  /**
   * Find last statement and turn it into a return statement if not already a return statement. This is used to turn
   * implicit returns in functions into explicit returns to simplify the job of the Resolver and Compiler phases.
   */
  private Stmt explicitReturn(Stmt stmt, JacsalType returnType) {
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
      if (ifStmt.trueStmts == null || ifStmt.falseStmts == null) {
        throw new CompileError("Missing explicit/implicit return in empty block for " +
                               (ifStmt.trueStmts == null ? "true" : "false") + " condition of if statment", stmt.location);
      }
      ifStmt.trueStmts = explicitReturn(((Stmt.If) stmt).trueStmts, returnType);
      ifStmt.falseStmts = explicitReturn(((Stmt.If) stmt).falseStmts, returnType);
      return stmt;
    }

    // Turn implicit return into explicit return
    if (stmt instanceof Stmt.ExprStmt) {
      Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) stmt;
      Expr          expr     = exprStmt.expr;
      expr.isResultUsed = true;
      Stmt.Return returnStmt = new Stmt.Return(expr);
      returnStmt.type = returnType;
      returnStmt.location = exprStmt.location;
      return returnStmt;
    }

    // If last statement is an assignment then value of assignment is the returned value.
    // We set a flag on the statement so that Compiler knows to leave result on the stack
    // and replace the assignment statement with a return wrapping the assignment expression.
    if (stmt instanceof Stmt.VarDecl) {
      Expr.VarDecl declExpr = ((Stmt.VarDecl) stmt).declExpr;
      declExpr.isResultUsed = true;
      Stmt.Return returnStmt = new Stmt.Return(declExpr);
      returnStmt.location = stmt.location;
      returnStmt.type = returnType;
      return returnStmt;
    }

    // For functions that return ANY there is an implicit "return null" even if last statement
    // does not have a value so replace stmt with list of statements that include the stmt and
    // then a "return null" statement.
    if (returnType.is(ANY)) {
      Stmt.Stmts stmts = new Stmt.Stmts();
      stmts.stmts.add(stmt);
      Stmt.Return returnStmt = new Stmt.Return(new Expr.Literal(new Token(NULL, stmt.location)));
      returnStmt.location = stmt.location;
      returnStmt.type = returnType;
      stmts.stmts.add(returnStmt);
      return stmts;
    }

    // Other statements are not supported for implicit return
    throw new CompileError("Unsupported statement type for implicit return", stmt.location);
  }
}
