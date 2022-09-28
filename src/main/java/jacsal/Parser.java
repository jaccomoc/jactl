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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static jacsal.JacsalType.ANY;
import static jacsal.JacsalType.FUNCTION;
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
  Tokeniser tokeniser;
  Token                 firstToken = null;
  List<CompileError>    errors     = new ArrayList<>();
  Deque<Stmt.ClassDecl> classes    = new ArrayDeque<>();

  // Whether we are currently doing a lookahead in which case we don't bother keeping
  // track of state such as functions declared per block and nested functions.
  // We increment every time we start a lookahead (which can then do another lookahead)
  // and decrement when we finish. If value is 0 then we are not in a lookahead.
  int lookaheadCount = 0;

  public Parser(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  public Stmt.ClassDecl parse() {
    Token start = peek();
    Stmt.ClassDecl scriptClass = new Stmt.ClassDecl(new Token(IDENTIFIER, start).setValue(Utils.JACSAL_PREFIX));
    classes.push(scriptClass);
    try {
      scriptClass.scriptMain = script();
      scriptClass.scriptMain.declExpr.isScriptMain = true;
      if (errors.size() > 1) {
        throw new CompileError(errors);
      }
      if (errors.size() == 1) {
        throw errors.get(0);
      }
      return scriptClass;
    }
    finally {
      classes.pop();
    }
  }

  public Expr parseExpression() {
    Expr expr = expression();
    expect(EOF);
    return expr;
  }

  ////////////////////////////////////////////

  // = Stmt

  private static final TokenType[] types = new TokenType[] { DEF, BOOLEAN, INT, LONG, DOUBLE, DECIMAL, STRING, MAP, LIST };

  /**
   *# script -> block;
   */
  private Stmt.FunDecl script() {
    Token start = peek();
    Token scriptName = new Token(IDENTIFIER, start).setValue(Utils.JACSAL_SCRIPT_MAIN);
    // Script take a single parameter which is a Map of globals
    Token        name     = new Token(IDENTIFIER, start).setValue(Utils.JACSAL_GLOBALS_NAME);
    Expr.VarDecl declExpr = new Expr.VarDecl(name, null);
    declExpr.type = JacsalType.MAP;
    declExpr.isParam = true;
    Stmt.VarDecl globals  = new Stmt.VarDecl(new Token(MAP, start), declExpr);
    Stmt.FunDecl funDecl  = parseFunDecl(scriptName, scriptName, ANY, List.of(globals), EOF, true);
    classes.peek().scriptMain = funDecl;
    return funDecl;
  }

  /**
   *# block -> "{" stmts "}"
   *#        | stmts EOF      // Special case for top most script block
   *#        ;
   */
  private Stmt.Block block(TokenType endBlock) {
    Stmt.Stmts stmts = new Stmt.Stmts();
    Stmt.Block block = new Stmt.Block(peek(), stmts);
    blockStack().push(block);
    try {
      stmts(stmts);
      expect(endBlock);
      return block;
    }
    finally {
      blockStack().pop();
    }
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

          if (peek().isNot(EOL, EOF, SEMICOLON, RIGHT_BRACE)) {
            unexpected("Expecting end of statement");
          }
        }
      }
      catch (CompileError e) {
        if (e instanceof EOFError) {
          // Only add error once
          if (errors.stream().noneMatch(err -> err instanceof EOFError)) {
            errors.add(e);
          }
        }
        else {
          errors.add(e);
          // Consume until end of statement to try to allow further
          // parsing and error checking to occur
          consumeUntil(EOL, EOF, RIGHT_BRACE);
        }
      }
    }
  }

  /**
   *# declaration -> funDecl
   *#              | varDecl
   *#              | statement;
   */
  private Stmt declaration() {
    // Look for function declaration: <type> <identifier> "(" ...
    if (lookahead(() -> matchAny(types),
                  () -> matchAny(IDENTIFIER),
                  () -> matchAny(LEFT_PAREN))) {
      return funDecl();
    }
    if (matchAny(types) || matchAny(VAR)) {
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
   *#            | ifStmt
   *#            | forStmt
   *#            | whileStmt
   *#            | exprStatement;
   */
  private Stmt statement() {
    matchAny(EOL);
    // Special case of a Map literal as an expression statement
    if (peek().is(LEFT_BRACE)) {
      if (isMapLiteral()) {
        return exprStmt();
      }
    }
    if (matchAny(IF))            { return ifStmt();           }
    if (matchAny(WHILE))         { return whileStmt();        }
    if (matchAny(FOR))           { return forStmt();          }
    if (matchAny(BREAK))         { return new Stmt.Break(previous()); }
    if (matchAny(CONTINUE))      { return new Stmt.Continue(previous()); }
    if (peek().is(SEMICOLON))    { return null;               }

    Stmt.ExprStmt stmt = exprStmt();
    // We need to check if exprStmt was a parameterless closure and if so convert back into
    // statement block. The problem is that we can't tell the difference between a closure with
    // no parameters and a code block until after we have parsed them. If the parameterless
    // closure is called immediately then we know it was a closure and expression type won't be
    // a closure. Otherwise, if the type is a closure and it has no parameters we know it wasn't
    // immediately invoked and we will treat it is a code block.
    if (stmt.expr instanceof Expr.Closure) {
      Expr.Closure closure = (Expr.Closure)stmt.expr;
      if (closure.noParamsDefined) {
        removeClosure(closure.funDecl);
        // Remove the default parameter declaration for "it" from the statements in the block
        Stmt itParameter = closure.funDecl.block.stmts.stmts.remove(0);
        if (!(itParameter instanceof Stmt.VarDecl &&
              ((Stmt.VarDecl) itParameter).declExpr.name.getStringValue().equals(Utils.IT_VAR))) {
          throw new IllegalStateException("Internal error: expecting parameter declaration for 'it' but got " + itParameter);
        }
        return closure.funDecl.block;   // Treat closure as code block since no parameters
      }
    }
    else
    if (matchAny(IF,UNLESS)) {
      // For all other expressions we allow a following "if" or "unless" so if we have one
      // of those we will convert into an if statement
      Token ifToken = previous();
      Expr  condition = expression();
      if (ifToken.is(IF)) {
        return new Stmt.If(ifToken, condition, stmt, null);
      }
      else {
        return new Stmt.If(ifToken, condition, null, stmt);
      }
    }

    // If we have a solitary return expression then convert to return statement
    if (stmt.expr instanceof Expr.Return) {
      final var returnExpr = (Expr.Return) stmt.expr;
      return new Stmt.Return(returnExpr.returnToken, returnExpr);
    }
    return stmt;
  }

  /**
   *# funDecl -> ("boolean" | "int" | "long" | "double" | "Decimal" | "String" | "Map" | "List")
   *#              IDENTIFIER "(" ( varDecl ( "," varDecl ) * ) ? ")" "{" block "}" ;
   */
  private Stmt.FunDecl funDecl() {
    Token returnTypeToken = expect(types);
    JacsalType returnType = JacsalType.valueOf(returnTypeToken.getType());
    Token name = expect(IDENTIFIER);
    expect(LEFT_PAREN);
    matchAny(EOL);
    List<Stmt.VarDecl> parameters = parameters(RIGHT_PAREN);
    matchAny(EOL);
    expect(LEFT_BRACE);
    matchAny(EOL);
    Stmt.FunDecl funDecl = parseFunDecl(name, name, returnType, parameters, RIGHT_BRACE, false);

    // Create a "variable" for the function that will have the MethodHandle as its value
    // so we can get the function by value
    Expr.VarDecl varDecl = new Expr.VarDecl(funDecl.declExpr.name, null);
    varDecl.type = FUNCTION;
    varDecl.funDecl = funDecl.declExpr;
    varDecl.isResultUsed = false;
    funDecl.declExpr.varDecl = varDecl;
    return funDecl;
  }

  /**
   *# parameters -> ( varDecl ( "," varDecl ) * ) ? ;
   */
  private List<Stmt.VarDecl> parameters(TokenType endToken) {
    List<Stmt.VarDecl> parameters = new ArrayList<>();
    while (!matchAny(endToken)) {
      if (parameters.size() > 0) {
        expect(COMMA);
        matchAny(EOL);
      }
      // Check for optional type. Default to "def".
      Token typeToken = new Token(DEF, previous());
      if (!peek().is(IDENTIFIER)) {
        // Allow "var" or a valid type
        typeToken = peek().is(VAR) ? expect(VAR) : expect(types);
      }
      // Note that unlike a normal varDecl where commas separate different vars of the same type,
      // with parameters we expect a separate comma for parameter with a separate type for each one
      // so we build up a list of singleVarDecl.
      Stmt.VarDecl varDecl = singleVarDecl(typeToken);
      varDecl.declExpr.isParam = true;
      parameters.add(varDecl);
      matchAny(EOL);
    }
    return parameters;
  }

  /**
   *# varDecl -> ("var" | "boolean" | "int" | "long" | "double" | "Decimal" | "String" | "Map" | "List" )
   *#                      IDENTIFIER ( "=" expression ) ? ( "," IDENTIFIER ( "=" expression ) ? ) * ;
   * NOTE: we turn either a single Stmt.VarDecl if only one variable declared or we return Stmt.Stmts with
   *       a list of VarDecls if multiple variables declared.
   */
  private Stmt varDecl() {
    Token typeToken = previous();
    Stmt.Stmts stmts = new Stmt.Stmts();
    stmts.stmts.add(singleVarDecl(typeToken));
    while (matchAny(COMMA)) {
      matchAny(EOL);
      stmts.stmts.add(singleVarDecl(typeToken));
    }
    if (stmts.stmts.size() > 1) {
      // Multiple variables so return list of VarDecls
      return stmts;
    }
    // Single variable so return just that one
    return stmts.stmts.get(0);
  }

  private Stmt.VarDecl singleVarDecl(Token typeToken) {
    Token identifier  = expect(IDENTIFIER);
    Expr  initialiser = null;
    if (matchAny(EQUAL)) {
      matchAny(EOL);
      initialiser = parseExpression(0);
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
                               : JacsalType.valueOf(typeToken.getType());
    }

    Expr.VarDecl varDecl = new Expr.VarDecl(identifier, initialiser);
    varDecl.isResultUsed = false;      // Result not used unless last stmt of a function used as implicit return
    varDecl.type = type;
    return new Stmt.VarDecl(typeToken, varDecl);
  }

  /**
   *# ifStmt -> "if" "(" expression ")" statement ( "else" statement ) ? ;
   */
  private Stmt.If ifStmt() {
    Token ifToken = previous();
    expect(LEFT_PAREN);
    Expr cond      = condition();
    matchAny(EOL);
    expect(RIGHT_PAREN);
    Stmt trueStmt  = statement();
    Stmt falseStmt = null;
    matchAny(EOL);
    if (matchAny(ELSE)) {
      falseStmt = statement();
    }
    return new Stmt.If(ifToken, cond, trueStmt, falseStmt);
  }

  /**
   *# whileStmt -> "while" "(" expression ")" statement ;
   */
  private Stmt.While whileStmt() {
    Token whileToken = previous();
    expect(LEFT_PAREN);
    Expr cond      = condition();
    matchAny(EOL);
    expect(RIGHT_PAREN);
    Stmt.While whileStmt = new Stmt.While(whileToken, cond);
    whileStmt.body = statement();
    return whileStmt;
  }

  /**
   *# forStmt -> "for" "(" declaration ";" expression ";" commaSeparatedStatements ")" statement ;
   */
  private Stmt forStmt() {
    Token forToken = previous();
    expect(LEFT_PAREN);
    Stmt initialisation;
    if (peek().is(types) || peek().is(VAR)) {
      initialisation    = declaration();
    }
    else {
      initialisation    = commaSeparatedStatements();
    }
    if (!previous().is(SEMICOLON)) {
      matchAny(EOL);
      expect(SEMICOLON);
    }
    Expr cond           = condition();
    matchAny(EOL);
    expect(SEMICOLON);
    Stmt update         = commaSeparatedStatements();
    matchAny(EOL);
    Token rightParen = expect(RIGHT_PAREN);

    Stmt.While whileStmt = new Stmt.While(forToken, cond);
    whileStmt.updates = update;
    whileStmt.body = statement();

    Stmt forStmt;
    if (initialisation != null) {
      // If there are initialisers then wrap the while loop in a block that has
      // the initialisers as the first statements (this way any vars declared
      // will have a scope that includes only the for/while loop)
      forStmt = stmtBlock(forToken, initialisation, whileStmt);
    }
    else {
      // If we don't have any initialisation we just turn into a while loop
      forStmt = whileStmt;
    }
    return forStmt;
  }

  /**
   *# commaSeparatedStatements -> ( statement ( "," statement ) * ) ? ;
   */
  Stmt.Stmts commaSeparatedStatements() {
    matchAny(EOL);
    if (peek().is(RIGHT_PAREN)) {
      return null;
    }
    Stmt.Stmts stmts = new Stmt.Stmts();
    stmts.stmts.add(statement());
    while (matchAny(COMMA)) {
      stmts.stmts.add(statement());
    }
    return stmts;
  }

  /**
   *# exprStmt -> expression;
   */
  private Stmt.ExprStmt exprStmt() {
    Token location = peek();
    Expr  expr     = expression();
    expr.isResultUsed = false;    // Expression is a statement so result not used
    Stmt.ExprStmt stmt = new Stmt.ExprStmt(location, expr);
//    if (peek().isNot(EOF, EOL, SEMICOLON, RIGHT_BRACE)) {
//      unexpected("Expected end of expression");
//    }
    return stmt;
  }

  /**
   # returnExpr -> "return" expression;
   */
  private Expr.Return returnExpr() {
    Token location = previous();
    return new Expr.Return(location, parseExpression(0), null);   // returnType will be set by Resolver
  }

  /**
   *# printExpr -> ("print" | "println") expr ?;
   */
  private Expr.Print printExpr() {
    Token printToken = previous();
    return new Expr.Print(printToken, parseExpression(0), printToken.is(PRINTLN));
  }

  private Stmt.Block stmtBlock(Token startToken, Stmt... statements) {
    Stmt.Stmts stmts = new Stmt.Stmts();
    stmts.stmts.addAll(List.of(statements));
    return new Stmt.Block(startToken, stmts);
  }

  ////////////////////////////////////////////

  // = Expr

  private static TokenType[] fieldAccessOp = new TokenType[] { DOT, QUESTION_DOT, LEFT_SQUARE, QUESTION_SQUARE };

  // NOTE: type case also has same precendence as unaryOps but has no specific operator
  private static List<TokenType> unaryOps = List.of(/*GRAVE,*/ BANG, MINUS_MINUS, PLUS_PLUS /*, (type) */);

  // Operators from least precedence to highest precedence. Each entry in list is
  // a pair of a boolean and a list of the operators at that level of precedene.
  // The boolean indicates whether the operators are left-associative (true) or
  // right-associative (false).
  private static List<Pair<Boolean,List<TokenType>>> operatorsByPrecedence =
    List.of(
//      new Pair(true, List.of(OR)),
//      new Pair(true, List.of(AND)),
//      new Pair(true, List.of(NOT)),
      new Pair(false, List.of(EQUAL, QUESTION_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PLUS_EQUAL, MINUS_EQUAL)),
      /* STAR_STAR_EQUAL, DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN_EQUAL, AMPERSAND_EQUAL,
         PIPE_EQUAL, ACCENT_EQUAL), */
      new Pair(true, List.of(QUESTION, QUESTION_COLON)),
      new Pair(true, List.of(PIPE_PIPE)),
      new Pair(true, List.of(AMPERSAND_AMPERSAND)),
      /*
      List.of(PIPE),
      List.of(ACCENT),
      List.of(AMPERSAND),
      */
      new Pair(true, List.of(EQUAL_EQUAL, BANG_EQUAL, COMPARE, EQUAL_GRAVE, BANG_GRAVE /*, TRIPLE_EQUAL, BANG_EQUAL_EQUAL*/)),
      new Pair(true, List.of(LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, INSTANCE_OF, BANG_INSTANCE_OF /*, IN, BANG_IN, AS*/)),
/*
      List.of(DOUBLE_LESS_THAN, DOUBLE_GREATER_THAN, TRIPLE_GREATER_THAN),
*/
      new Pair(true, List.of(MINUS, PLUS)),
      new Pair(true, List.of(STAR, SLASH, PERCENT)),
      //      List.of(STAR_STAR)
      new Pair(true, unaryOps),
      new Pair(true, Utils.concat(fieldAccessOp, LEFT_PAREN, LEFT_BRACE))
    );

  /**
   *# condition -> expression ;
   * Used for situations where we need a boolean condition.
   */
  private Expr condition() {
    return expression();
  }

  /**
   *# expression -> orExpression ;
   * Note that we handle special case of regex on its own here.
   * A regex as a single statement or single expr where we expect some kind of condition
   * (such as in an if/while) is turned into "it =~ /regex/" to make it a bit like perl syntax
   * where "/pattern/" means "$_ =~ /pattern/".
   */
  private Expr expression() {
    return orExpression();
  }

  /**
   *# orExpression -> andExpression ( "or" andExpression) * ;
   */
  private Expr orExpression() {
    Expr expr = andExpression();
    while (matchAny(OR)) {
      expr = new Expr.Binary(expr, new Token(PIPE_PIPE, previous()), andExpression());
    }
    return expr;
  }

  /**
   *# andExpression -> notExpression ( AND notExpression ) * ;
   */
  private Expr andExpression() {
    Expr expr = notExpression();
    while (matchAny(AND)) {
      expr = new Expr.Binary(expr, new Token(AMPERSAND_AMPERSAND, previous()), notExpression());
    }
    return expr;
  }

  /**
   *# notExpresssion -> NOT * (expr | returnExpr | printExpr) ;
   */
  private Expr notExpression() {
    Expr expr;
    if (matchAny(NOT)) {
      Token notToken = previous();
      expr = new Expr.PrefixUnary(new Token(BANG, notToken), notExpression());
    }
    else {
      if (matchAny(RETURN))        { expr = returnExpr(); }
      else
      if (matchAny(PRINT,PRINTLN)) { expr = printExpr();  }
      else {
        expr = parseExpression(0);
      }
    }
    return expr;
  }

  /**
   *# expr -> expr operator expr
   *#       | expr "?" expr ":"" expr
   *#       | unary
   *#       | primary;
   * Parse expressions (mostly binary operations).
   * We parse based on operator precedence. If we reach highest level of precedence
   * then we return a primaru().
   * We check whether current level of precedence corresponds to the unary ops and
   * return unary() if that is the case.
   */
  private Expr parseExpression(int level) {
    matchAny(EOL);

    // If we have reached highest precedence
    if (level == operatorsByPrecedence.size()) {
      return primary();
    }

    // Get list of operators at this level of precedence along with flag
    // indicating whether they are left-associative or not.
    var operatorsPair = operatorsByPrecedence.get(level);
    boolean isLeftAssociative = operatorsPair.first;
    List<TokenType> operators = operatorsPair.second;

    // If this level of precedence is for our unary operators (includes type cast)
    if (operators == unaryOps) {
      return unary(level);
    }

    var expr = parseExpression(level + 1);

    while (matchAny(operators)) {
      Token operator = previous();
      if (operator.is(INSTANCE_OF,BANG_INSTANCE_OF)) {
        Token type = expect(types);
        expr = new Expr.Binary(expr, operator, new Expr.Literal(type));
        continue;
      }

      if (operator.is(QUESTION)) {
        Expr trueExpr = parseExpression(level);
        Token operator2 = expect(COLON);
        Expr falseExpr = parseExpression(level);
        expr = new Expr.Ternary(expr, operator, trueExpr, operator2, falseExpr);
        continue;
      }

      // Call starts with "(" but sometimes can have just "{" if no args before
      // a closure arg
      if (operator.is(LEFT_PAREN,LEFT_BRACE)) {
        List<Expr> args = argList();
        expr = createCallExpr(expr, operator, args);
        continue;
      }

      // Set flag if next token is '(' so that we can check for x.(y).z below
      boolean bracketedExpression = peek().is(LEFT_PAREN);

      Expr rhs;
      if (operator.is(LEFT_SQUARE,QUESTION_SQUARE)) {
        // '[' and '?[' can be followed by any expression and then a ']'
        rhs = parseExpression(0);
      }
      else {
        rhs = parseExpression(level + (isLeftAssociative ? 1 : 0));
      }

      // Check for lvalue for assignment operators
      if (operator.getType().isAssignmentLike()) {
        expr = convertToLValue(expr, operator, rhs, false);
      }
      else {
        // Check for '.' and '?.' where we treat identifiers as literals for field access.
        // In other words x.y is the same as x.'y' even if a variable y exists somewhere.
        // Note: we checked for '(' immediately after the '.' so we can use value of y if
        // expression is something like x.(y)
        if (operator.is(DOT,QUESTION_DOT) && rhs instanceof Expr.Identifier && !bracketedExpression) {
          rhs = new Expr.Literal(((Expr.Identifier) rhs).identifier);
        }

        // If operator is =~ and we had a /regex/ for rhs then since we create "it =~ /regex/"
        // by default we need to strip off the "it =~" and replace with current one
        if (operator.is(EQUAL_GRAVE,BANG_GRAVE)) {
          if (rhs instanceof Expr.RegexMatch && ((Expr.RegexMatch) rhs).implicitItMatch) {
            Expr.RegexMatch regex = (Expr.RegexMatch) rhs;
            regex.left = expr;
            regex.operator = operator;
            regex.implicitItMatch = false;
            expr = regex;
          }
          else {
            expr = new Expr.RegexMatch(expr, operator, rhs, "", false);
          }
        }
        else {
          expr = new Expr.Binary(expr, operator, rhs);
        }
      }
      // Check for closing ']' if required
      if (operator.is(LEFT_SQUARE,QUESTION_SQUARE)) {
        expect(RIGHT_SQUARE);
      }
    }

    return expr;
  }

  /**
   *# unary -> ( "!" | "--" | "++" | "-" | "+" | "(" type ")" ) unary ( "--" | "++" )
   *#        | expression;
   */
  private Expr unary(int precedenceLevel) {
    Expr expr;
    if (lookahead(() -> matchAny(LEFT_PAREN), () -> matchAny(types), () -> matchAny(RIGHT_PAREN))) {
      // Type cast. Rather than create a separate operator for each type case we just use the
      // token for the type as the operator if we detect a type cast.
      expect(LEFT_PAREN);
      Token type = expect(types);
      expect(RIGHT_PAREN);
      Expr unary = unary(precedenceLevel);
      expr = new Expr.PrefixUnary(type, unary);
    }
    else
    if (matchAny(BANG, MINUS_MINUS, PLUS_PLUS, MINUS, PLUS)) {
      Token operator = previous();
      Expr unary = unary(precedenceLevel);
      if (operator.is(PLUS_PLUS,MINUS_MINUS) &&
          unary instanceof Expr.Binary &&
          ((Expr.Binary) unary).operator.is(fieldAccessOp)) {
        // If we are acting on a field (rather than directly on a variable) then
        // we might need to create intermediate fields etc so turn the inc/dec
        // into the equivalent of:
        //   ++a.b.c ==> a.b.c += 1
        //   --a.b.c ==> a.b.c -= 1
        // That way we can use the lvalue mechanism of creating missing fields if
        // necessary.
        expr = convertToLValue(unary, operator, new Expr.Literal(new Token(INTEGER_CONST, operator).setValue(1)),false);
      }
      else {
        expr = new Expr.PrefixUnary(operator, unary);
      }
    }
    else {
      expr = parseExpression(precedenceLevel + 1);
    }
    if (matchAny(PLUS_PLUS, MINUS_MINUS)) {
      Token operator = previous();
      if (expr instanceof Expr.Binary && ((Expr.Binary) expr).operator.is(fieldAccessOp)) {
        // If we are acting on a field (rather than directly on a variable) then
        // we might need to create intermediate fields etc so turn the inc/dec
        // into the equivalent of:
        //   a.b.c++ ==> a.b.c += 1
        //   a.b.c-- ==> a.b.c -= 1
        // That way we can use the lvalue mechanism of creating missing fields if
        // necessary.
        // NOTE: for postfix we need the beforeValue if result is being used.
        expr = convertToLValue(expr, operator, new Expr.Literal(new Token(INTEGER_CONST, operator).setValue(1)),true);
      }
      else {
        expr = new Expr.PostfixUnary(expr, previous());
      }
    }
    return expr;
  }

  /**
   *# expressionList -> expression ( "," expression ) * ;
   */
  private List<Expr> expressionList(TokenType endToken) {
    List<Expr> exprs = new ArrayList<>();
    while (!matchAny(endToken)) {
      if (exprs.size() > 0) {
        expect(COMMA);
      }
      exprs.add(parseExpression(0));
    }
    return exprs;
  }

  /**
   *# argList -> expressionList ? ( "{" closure "}" ) * ;
   */
  private List<Expr> argList() {
    Token token = previous();
    List<Expr> args = new ArrayList<>();
    if (token.is(LEFT_PAREN)) {
      args.addAll(expressionList(RIGHT_PAREN));
      if (peek().is(LEFT_BRACE)) {
        token = expect(LEFT_BRACE);
      }
    }
    // If closure appears immediately after a function call it is passed as last argument.
    // E.g.:  execNtimes(10) { println it } --> execNtimes(10, { println it })
    // In fact any number of closures immediately following will be added to the args and
    // if the function/closure only takes closures as arguments no parentheses are needed
    // at all.
    // E.g. time{ fib(10) }  -->  time({ fib(10) })
    //      ntimes(10){ fib(10) }{ println "done" }  --> ntimes(10, {fib(10)},{ println "done" })
    if (token.is(LEFT_BRACE)) {
      do {
        args.add(closure());
      } while (matchAny(LEFT_BRACE));
    }
    return args;
  }

  /**
   *# primary -> INTEGER_CONST | DECIMAL_CONST | DOUBLE_CONST | STRING_CONST | "true" | "false" | "null"
   *#          | exprString
   *#          | IDENTIFIER
   *#          | listOrMapLiteral
   *#          | "(" expression ")"
   *#          | "{" closure "}"
   *#          ;
   */
  private Expr primary() {
    Supplier<Expr> nestedExpression = () -> {
      Expr nested = expression(); // parseExpression(0);
      matchAny(EOL);
      expect(RIGHT_PAREN);
      return nested;
    };

    matchAny(EOL);
    if (matchAny(INTEGER_CONST, LONG_CONST,
                 DECIMAL_CONST, DOUBLE_CONST,
                 STRING_CONST, TRUE, FALSE, NULL)) { return new Expr.Literal(previous());         }
    if (peek().is(SLASH, EXPR_STRING_START))       { return  exprString();                         }
    if (matchAny(IDENTIFIER))                      { return new Expr.Identifier(previous());      }
    if (peek().is(LEFT_SQUARE,LEFT_BRACE))         { if (isMapLiteral()) { return mapLiteral(); } }
    if (matchAny(LEFT_SQUARE))                     { return listLiteral();                        }
    if (matchAny(LEFT_PAREN))                      { return nestedExpression.get();               }
    if (matchAny(LEFT_BRACE))                      { return closure();                            }

    return unexpected("Expecting literal or identifier or bracketed expression");
  }

  /**
   *# listLiteral -> "[" ( expression ( "," expression ) * ) ? "]"
   */
  private Expr.ListLiteral listLiteral() {
    Expr.ListLiteral expr = new Expr.ListLiteral(previous());
    while (!matchAny(RIGHT_SQUARE)) {
      if (expr.exprs.size() > 0) {
        expect(COMMA);
      }
      expr.exprs.add(parseExpression(0));
    }
    return expr;
  }

  /**
   *# mapLiteral -> "[" ":" "]"
   *#             | "{" ":" "}"
   *#             | "[" ( mapKey ":" expression ) + "]"
   *#             | "{" ( mapKey ":" expression ) + "}"
   *#             ;
   * Return a map literal. For maps we support Groovy map syntax using "[" and "]"
   * as well as JSON syntax using "{" and "}".
   */
  private Expr.MapLiteral mapLiteral() {
    expect(LEFT_SQUARE,LEFT_BRACE);
    Token startToken = previous();
    TokenType endToken = startToken.is(LEFT_BRACE) ? RIGHT_BRACE : RIGHT_SQUARE;
    Expr.MapLiteral expr = new Expr.MapLiteral(startToken);
    if (matchAny(COLON)) {
      // Empty map
      expect(endToken);
    }
    else {
      while (!matchAny(endToken)) {
        if (expr.entries.size() > 0) {
          expect(COMMA);
        }
        Expr key = mapKey();
        expect(COLON);
        Expr value = parseExpression(0);
        expr.entries.add(new Pair(key, value));
      }
    }
    return expr;
  }

  /**
   *# mapKey -> STRING_CONST | IDENTIFIER | "(" expression() + ")" | exprString | keyWord ;
   */
  private Expr mapKey() {
    if (matchAny(STRING_CONST,IDENTIFIER)) { return new Expr.Literal(previous()); }
    if (peek().is(EXPR_STRING_START))      { return exprString(); }
    if (peek().isKeyword())                { advance(); return new Expr.Literal(previous()); }
    if (matchAny(LEFT_PAREN)) {
      Expr expr = parseExpression(0);
      expect(RIGHT_PAREN);
      return expr;
    }
    return null;
  }

  private Expr expectMapKey() {
    Expr expr = mapKey();
    if (expr == null) {
      unexpected("Expected string or identifier");
    }
    return expr;
  }

  /**
   *# exprString -> EXPR_STRING_START ( IDENTIFIER | "{" blockExpr "}" | STRING_CONST ) * EXPR_STRING_END
   *#             | "/" ( IDENTIFIER | "{" blockExpr "}" | STRING_CONST ) * "/";
   * We parse an expression string delimited by " or """ or /
   * For the / version we treat as a multi-line regular expression and don't support escape chars.
   */
  private Expr exprString() {
    Token startToken = expect(EXPR_STRING_START,SLASH);
    Expr.ExprString exprString = new Expr.ExprString(startToken);
    if (startToken.is(EXPR_STRING_START)) {
      if (!startToken.getStringValue().isEmpty()) {
        // <></>urn the EXPR_STRING_START into a string literal and make it the first in our expr list
        exprString.exprList.add(new Expr.Literal(new Token(STRING_CONST, startToken)));
      }
    }
    else {
      // Tell tokeniser that the SLASH was actually the start of a regex expression string
      tokeniser.startRegex();
    }
    while (!matchAny(EXPR_STRING_END)) {
      if (matchAny(STRING_CONST) && !previous().getStringValue().isEmpty()) {
        exprString.exprList.add(new Expr.Literal(previous()));
      }
      else
      if (matchAny(IDENTIFIER)) {
        exprString.exprList.add(new Expr.Identifier(previous()));
      }
      else
      if (matchAny(LEFT_BRACE)) {
        exprString.exprList.add(blockExpression());
      }
      else {
        unexpected("Error in expression string");
      }
    }
    // If regex then we don't know yet whether we are part of a "x =~ /regex/" or just a /regex/
    // on our own somewhere. We build "it =~ /regex" and if we are actually part of "x =~ /regex/"
    // our parent (parseExpression()) will convert back to "x =~ /regex/".
    // If we detect at resolve time that the standalone /regex/ has no modifiers and should just
    // be a regular expression string then we will fix the problem then.
    if (startToken.is(SLASH)) {
      return createItMatch(exprString, previous());
    }
    return exprString;
  }

  /**
   *# blockExpression -> "{" block "}"
   *
   * Used inside expression strings. If no return statement there is an implicit
   * return on last statement in block that gives the value to then interpolate
   * into surrounding expression string.
   */
  private Expr blockExpression() {
    Token leftBrace = previous();
    Stmt.Block block = block(RIGHT_BRACE);
    if (block.stmts.stmts.size() > 1) {
      throw new CompileError("Only single expression allowed", leftBrace);
    }
    if (block.stmts.stmts.size() == 0) {
      return new Expr.Literal(new Token(NULL, leftBrace));
    }
    Stmt stmt = block.stmts.stmts.get(0);
    if (!(stmt instanceof Stmt.ExprStmt)) {
      throw new CompileError("Not an expression", leftBrace);
    }
    Expr expr = ((Stmt.ExprStmt)stmt).expr;
    expr.isResultUsed = true;
    return expr;
  }

  /**
   *# closure -> "{" (parameters "->" ) ? block "}"
   */
  private Expr closure() {
    Token openBrace = previous();
    // We need to do a lookahead to see if we have a parameter list or not
    List<Stmt.VarDecl> parameters;
    boolean noParamsDefined = false;
    if (lookahead(() -> parameters(ARROW) != null)) {
      parameters = parameters(ARROW);
      if (parameters.size() == 0) {
        parameters = List.of(createItParam(openBrace));
      }
    }
    else {
      // No parameter and no "->" so fill in with default "it" parameter
      noParamsDefined = true;
      parameters = List.of(createItParam(openBrace));
    }
    Stmt.FunDecl funDecl = parseFunDecl(openBrace, null, ANY, parameters, RIGHT_BRACE, false);
    funDecl.declExpr.isResultUsed = true;
    return new Expr.Closure(openBrace, funDecl.declExpr, noParamsDefined);
  }

  /////////////////////////////////////////////////

  private Stmt.VarDecl createItParam(Token token) {
    Token        itToken = new Token(IDENTIFIER, token).setValue(Utils.IT_VAR);
    Expr.VarDecl itDecl  = new Expr.VarDecl(itToken, new Expr.Literal(new Token(NULL, token)));
    itDecl.isParam = true;
    itDecl.isResultUsed = false;
    itDecl.type = ANY;
    return new Stmt.VarDecl(new Token(IDENTIFIER, token).setValue("def"), itDecl);
  }

  private Deque<Expr.FunDecl> functionStack() {
    return classes.peek().nestedFunctions;
  }

  private void pushFunDecl(Expr.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      functionStack().push(funDecl);
    }
  }

  private void popFunDecl() {
    if (lookaheadCount == 0) {
      functionStack().pop();
    }
  }

  private Deque<Stmt.Block> blockStack() {
    return functionStack().peek().blocks;
  }

  private void addFunDeclToBlock(Expr.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      blockStack().peek().functions.add(funDecl);
    }
  }

  private void addMethod(Expr.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      classes.peek().methods.add(funDecl);
    }
  }

  private void addClosure(Expr.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      classes.peek().closures.add(funDecl);
    }
  }

  private void removeClosure(Expr.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      classes.peek().closures.remove(funDecl);
    }
  }

  private Stmt.FunDecl parseFunDecl(Token start,
                                    Token name,
                                    JacsalType returnType,
                                    List<Stmt.VarDecl> params,
                                    TokenType endToken,
                                    boolean isScriptMain) {
    params.forEach(p -> p.declExpr.isExplicitParam = true);
    Expr.FunDecl funDecl = new Expr.FunDecl(start, name, returnType, params);
    params.forEach(p -> p.declExpr.owner = funDecl);
    if (!isScriptMain && !funDecl.isClosure()) {
      // Add to functions in block so we can support forward references during Resolver phase
      addFunDeclToBlock(funDecl);
    }
    pushFunDecl(funDecl);
    try {
      Stmt.Block block = block(endToken);
      insertStmtsInto(params, block);
      funDecl.block = block;
      // Add to list of closures/functions
      if (funDecl.isClosure()) {
        addClosure(funDecl);
      }
      else {
        addMethod(funDecl);
      }
      funDecl.isResultUsed = false;
      funDecl.type = FUNCTION;
      return new Stmt.FunDecl(start, funDecl);
    }
    finally {
      popFunDecl();
    }
  }

  private boolean isMapLiteral() {
    // Check for start of a Map literal. We need to lookahead to know the difference between
    // a Map literal using '{' and '}' and a statement block or a closure.
    return lookahead(() -> matchAny(LEFT_SQUARE, LEFT_BRACE), () -> matchAny(COLON)) ||
           lookahead(() -> matchAny(LEFT_SQUARE, LEFT_BRACE), () -> mapKey() != null, () -> matchAny(COLON));
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
   * Check if next token matches any of the given types. If it matches then consume the token and return true.
   * If it does not match one of the types then return false and stay in current position in stream of tokens.
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

  /**
   * If next token is a keyword then return true and advance
   * @return true if next token is a keyword
   */
  private boolean matchKeyword() {
    if (peek().isKeyword()) {
      advance();
      return true;
    }
    return false;
  }

  /**
   * Provide lookahead by remembering current token and state then checking that the list
   * of lambdas all return true. Each lambda should check for a token or invoke one of the
   * productions to partially parse some syntax. If the token is found or production succeeds
   * in parsing then the lambda should return true. If the lamdba returns false or throws a
   * CompileError then it is deemed to have failed and lookahead stops at that point.
   * If all lambdas succeed then the lookahead succeeds and we return true. If we get an
   * error (exception) or if any of the lamdbas returns false then we return false to
   * indicate that the lookahead has failed.
   * Either way, we rewind to the point where we were at and restore our state.
   * Example usage:
   *   // Check for beginning of a map literal
   *   if (lookahead(() -> matchAny(LEFT_SQUARE),
   *                 () -> mapKey() != null,
   *                 () -> matchAny(COLON)) {
   *     ...
   *   }
   * @param lambdas  array of lambdas returning true/false
   */
  @SafeVarargs
  private boolean lookahead(Supplier<Boolean>... lambdas) {
    // Remember current state
    Token current = peek();
    List<CompileError>    currentErrors    = new ArrayList<>(errors);

    // Set flag so that we know not to collect state such as functions per block etc
    // while doing a lookahead
    lookaheadCount++;

    try {
      for (Supplier<Boolean> lamdba: lambdas) {
        try {
          if (!lamdba.get()) {
            return false;
          }
        }
        catch (CompileError e) {
          return false;
        }
      }
      return true;
    }
    finally {
      // Restore state
      tokeniser.rewind(current);
      errors        = currentErrors;
      lookaheadCount--;
    }
  }

  /////////////////////////////////////

  private Expr error(String msg) {
    throw new CompileError(msg, peek());
  }

  private Expr unexpected(String msg) {
    if (peek().getType().is(EOF)) {
      throw new EOFError("Unexpected EOF: " + msg, peek());
    }
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
      unexpected("Expecting one of " + Arrays.stream(types).map(Enum::toString).collect(Collectors.joining(", ")));
    }
    else {
      unexpected("Expecting " + types[0]);
    }
    return null;
  }

  private void consumeUntil(TokenType... types) {
    while (peek().isNot(types)) {
      advance();
    }
  }

  /**
   * Insert given statements at start of stmts for a block
   * @param stmts  list of statements to insert
   * @param block  block in which to insert statements
   */
  private static void insertStmtsInto(List<Stmt.VarDecl> stmts, Stmt.Block block) {
    if (stmts.size() == 0) {
      return;  // Nothing to do
    }
    List<Stmt> originalStmts = block.stmts.stmts;
    block.stmts.stmts = new ArrayList<>(stmts);
    block.stmts.stmts.addAll(originalStmts);
  }

  /**
   * Create a call expr. The result will be an Expr.Call for runtime lookup of
   * method or an Expr.MethodCall if we have enough information that we might be
   * able to do a compile time lookup during Resolver phase.
   */
  private Expr createCallExpr(Expr callee, Token leftParen, List<Expr> args) {
    // When calling a method on an object where we don't know the type we must be
    // able to dynamically lookup up the method. The problem is that the method
    // name might also be the name of a field in a map but we still want to invoke
    // the method rather than look for a method handle in the field.
    // Consider this example:  Map m = [size:{it*it}]; m.size()
    // We want to invoke the Map.size() method not get the value of the size field
    // and invoke it.
    // In order to know whether the field lookup is looking up a field or a method
    // we mark the callee with a flag so we can tell at compile time what type of
    // look up to do. (Note that if there is not method of that name we then fallback
    // to looking for a field.)
    callee.isCallee = true;

    // Turn x.a.b(args) into Expr.Call(x.a, b, args) so that we can check at compile
    // time if x.a.b is a method (if we know type of a.x) or not.
    if (callee instanceof Expr.Binary) {
      Expr.Binary binaryExpr = (Expr.Binary) callee;
      // Make sure we have a '.' or '.?' which means we might have a method call
      if (binaryExpr.operator.is(DOT, QUESTION_DOT)) {
        // Get potential method name if right hand side of '.' is a string or identifier
        String methodName = getStringValue(binaryExpr.right);
        if (methodName != null) {
          return new Expr.MethodCall(leftParen, binaryExpr.left, binaryExpr.operator, methodName, args);
        }
      }
    }

    Expr.Call expr = new Expr.Call(leftParen, callee, args);
    return expr;
  }

  private String getStringValue(Expr expr) {
    if (expr instanceof Expr.Literal) {
      Object value = ((Expr.Literal) expr).value.getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    return null;
  }

  /**
   * Convert an expression that represents some sort of field path into an assignment
   * expression by converting the expression type into a Expr.FieldAssign component.
   * Note that when a field path occurs on the left hand side of an assignment-like operator
   * we mark all parts of the path with createIfMissing so that if the map field or list entry
   * is not there we automatically create the entry.
   * The other thing to note is that operators such as '+=', '-=', etc need the value of the
   * left hand side in order to compute the final value. The rules are that something like
   *    x += 5
   * is equivalent to
   *    x = x + 5
   * and similarly for all other such operators.
   * We could, therefore, just mutate our AST to replace the 'x += 5' with the equivalent of
   * 'x = x + 5' but for situations like 'a.b.c.d += 5' we have a couple of issues:
   *  1. We would duplicate the work to lookup all of the intermediate fields that result in
   *     a.b.c.d, and
   *  2. If a.b.c.d did no yet exist and we want to create it as part of the assigment we need
   *     to create it with a suitable default value before it can be used on the rhs.
   * A better solution is to replace our binary expression with something like this:
   *   new OpAssign(parent = new Binary('a.b.c'),
   *                accessOperator = '.',
   *                field = new Expr('d'),
   *                expr = new Binary(Noop, '+', '5'))
   * This way we now have a component in our AST that can do all the work of finding the parent
   * and creating any missing subfields just once and load the original value (or create a default
   * value) before evaluating the rest of the expression. Finally it can store the result back
   * into the right field (or element) of the Map/List.
   */
  private Expr convertToLValue(Expr variable, Token assignmentOperator, Expr rhs, boolean beforeValue) {
    // Get the arithmetic operator type for assignments such as +=, -=
    // If assignment is just = or ?= then arithmeticOp will be null.
    TokenType arithmeticOp   = arithmeticOperator.get(assignmentOperator.getType());

    // Field path is either a BinaryOp whose last operator is a field access operation
    // (map or list lookup), or fieldPath is just an identifier for a simple variable.
    if (variable instanceof Expr.Identifier) {
      if (arithmeticOp == null) {
        return new Expr.VarAssign((Expr.Identifier) variable, assignmentOperator, rhs);
      }
      Expr.Binary expr = new Expr.Binary(new Expr.Noop(assignmentOperator), new Token(arithmeticOp, assignmentOperator), rhs);
      expr.originalOperator = assignmentOperator;
      return new Expr.VarOpAssign((Expr.Identifier) variable, assignmentOperator,
                                  expr);
    }

    if (!(variable instanceof Expr.Binary)) {
      throw new CompileError("Left hand side of assignment is not a valid lvalue", assignmentOperator);
    }

    Expr.Binary fieldPath = (Expr.Binary)variable;

    // Make sure lhs is a valid lvalue
    if (!fieldPath.operator.is(fieldAccessOp)) {
      throw new CompileError("Cannot assign to value on left-hand side (invalid lvalue)", assignmentOperator);
    }

    Expr parent = fieldPath.left;

    // Now set createIfMissing flag on all field access operations (but only those at the "top" level)
    // and only up to the last field. So a.b.c only has the a.b lookup changed to set "createIfMissing".
    setCreateIfMissing(parent);

    Token     accessOperator = fieldPath.operator;
    Expr      field          = fieldPath.right;

    // If arithmeticOp is null it means we have a simple = or ?=
    if (arithmeticOp == null) {
      return new Expr.Assign(parent, accessOperator, field, assignmentOperator, rhs);
    }

    // For all other assignment ops like +=, -=, *= we extract the arithmetic operation
    // (+, -, *, ...) and create a new Binary expression with null as lhs (since value
    // will come from the field value) and with the arithmetic op as the operation and
    // the original rhs as the new rhs of the binary operation
    Token operator = new Token(arithmeticOp, assignmentOperator);
    Expr.Binary binaryExpr = new Expr.Binary(new Expr.Noop(operator), operator, rhs);
    binaryExpr.originalOperator = assignmentOperator;
    Expr.OpAssign expr = new Expr.OpAssign(parent, accessOperator, field, assignmentOperator,
                                           binaryExpr);

    // For postfix ++ and -- of fields where we convert to a.b.c += 1 etc we need the pre value
    // (before the inc/dec) as the result
    expr.isPreIncOrDec = beforeValue;
    return expr;
  }

  private void setCreateIfMissing(Expr expr) {
    if (expr instanceof Expr.Binary) {
      Expr.Binary binary = (Expr.Binary) expr;
      if (binary.operator.is(fieldAccessOp)) {
        binary.createIfMissing = true;
      }
      setCreateIfMissing(binary.left);
    }
  }

  private Expr.RegexMatch createItMatch(Expr expr, Token regexEnd) {
    Token start = ((Expr.ExprString) expr).exprStringStart;
    Expr.Identifier itIdent = new Expr.Identifier(new Token(IDENTIFIER, start).setValue(Utils.IT_VAR));
    itIdent.optional = true;   // Optional since if "it" does not exist we will use regex as a string
                               // rather than try to match
    final var modifiers = regexEnd.getStringValue();
    return new Expr.RegexMatch(itIdent,
                               new Token(EQUAL_GRAVE, start),
                               expr,
                               modifiers,
                               true);
  }

  private static final Map<TokenType,TokenType> arithmeticOperator =
    Map.of(PLUS_PLUS,     PLUS,
           MINUS_MINUS,   MINUS,
           PLUS_EQUAL,    PLUS,
           MINUS_EQUAL,   MINUS,
           STAR_EQUAL,    STAR,
           SLASH_EQUAL,   SLASH,
           PERCENT_EQUAL, PERCENT);
}
