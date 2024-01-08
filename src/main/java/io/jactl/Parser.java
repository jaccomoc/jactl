/*
 * Copyright © 2022,2023 James Crawford
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
 *
 */

package io.jactl;

import io.jactl.runtime.RuntimeUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.jactl.JactlType.ANY;
import static io.jactl.JactlType.UNKNOWN;
import static io.jactl.TokenType.*;

/**
 * Recursive descent parser for parsing the Jactl language.
 * In general, we try to avoid too much lookahead in the parsing to make the parsing efficient
 * but there are a couple of places where lookahead is required to disambiguate parts of the
 * grammar.
 * Usually a single token lookahead suffices, but we support arbitrary amounts of lookahead.
 * <p>
 * After the parsing has been done and the AST generated, the resulting AST needs to be passed
 * to the Resolver which resolves all variable references and does as much validation as possible
 * to validate types of operands in expressions.
 * </p><p>
 * Finally, the Compiler then should be invoked to compile the AST into JVM byte code.
 * </p>
 * To extract the EBNF grammar from the comments grep out lines starting with '  *#'
 */
public class Parser {
  Tokeniser tokeniser;
  List<CompileError>    errors      = new ArrayList<>();
  Deque<Stmt.ClassDecl> classes     = new ArrayDeque<>();
  boolean               ignoreEol   = false;   // Whether EOL should be treated as whitespace or not
  String                packageName;
  JactlContext context;
  int          uniqueVarCnt         = 0;

  // Whether we are currently doing a lookahead in which case we don't bother keeping
  // track of state such as functions declared per block and nested functions.
  // We increment every time we start a lookahead (which can then do another lookahead)
  // and decrement when we finish. If value is 0 then we are not in a lookahead.
  int lookaheadCount = 0;

  public Parser(Tokeniser tokeniser, JactlContext context, String packageName) {
    this.tokeniser   = tokeniser;
    this.context     = context;
    this.packageName = packageName;
  }

  /**
   *<pre>
   *# parseScript ::= packageDecl? importStmt* script
   *</pre>
   * @param scriptClassName the class name to use for the script
   * @return the ClassDecl for the compiled script
   */
  public Stmt.ClassDecl parseScript(String scriptClassName) {
    packageDecl();
    List<Stmt.Import> importStmts = importStmts();
    Token start = peek();
    Stmt.ClassDecl scriptClass = new Stmt.ClassDecl(start.newIdent(scriptClassName), packageName, null, null, false);
    scriptClass.imports = importStmts;
    pushClass(scriptClass);
    try {
      scriptClass.scriptMain = script();
      if (errors.size() > 1) {
        throw new CompileError(errors);
      }
      if (errors.size() == 1) {
        throw errors.get(0);
      }
      return scriptClass;
    }
    finally {
      popClass();
    }
  }

  private boolean isNotBeginEndBlock(Stmt stmt) {
    boolean isBeginEndBlock = stmt instanceof Stmt.Block && (((Stmt.Block) stmt).isBeginBlock || ((Stmt.Block) stmt).isEndBlock);
    return !isBeginEndBlock;
  }

  /**
   *<pre>
   *# parseClass ::= packageDecl? importStmt* classDecl EOF
   *</pre>
   * @return the ClassDecl for the class
   */
  public Stmt.ClassDecl parseClass() {
    packageDecl();
    List<Stmt.Import> importStmts = importStmts();
    expect(CLASS);
    Stmt.ClassDecl classDecl = classDecl();
    classDecl.imports = importStmts;
    matchAnyIgnoreEOL(SEMICOLON);
    expect(EOF);
    return classDecl;
  }

  ////////////////////////////////////////////

  // = Stmt

  private static final TokenType[] simpleTypes = new TokenType[] { BOOLEAN, BYTE, INT, LONG, DOUBLE, DECIMAL, STRING };
  private static final List<TokenType> types = RuntimeUtils.concat(simpleTypes, new TokenType[] { DEF, OBJECT, MAP, LIST });
  private static final List<TokenType> typesAndVar = RuntimeUtils.concat(types, VAR);

  /**
   *<pre>
   *# script ::= block
   *</pre>
   */
  private Stmt.FunDecl script() {
    Token start = peek();
    if (start.is(EOL)) {
      advance();
      start = peek();
    }
    Token scriptName = start.newIdent(Utils.JACTL_SCRIPT_MAIN);
    // Script take a single parameter which is a Map of globals
    Stmt.VarDecl globalsParam = Utils.createParam(start.newIdent(Utils.JACTL_GLOBALS_NAME), JactlType.MAP);
    Stmt.FunDecl funDecl      = parseFunDecl(scriptName, scriptName, ANY, Utils.listOf(globalsParam), EOF, true, false, false);
    Stmt.ClassDecl scriptClass = classes.peek();
    scriptClass.scriptMain     = funDecl;
    List<Stmt> scriptStmts     = scriptClass.scriptMain.declExpr.block.stmts.stmts;
    scriptClass.innerClasses   = scriptStmts.stream()
                                            .filter(stmt -> stmt instanceof Stmt.ClassDecl)
                                            .map(stmt -> (Stmt.ClassDecl)stmt)
                                            .collect(Collectors.toList());
    // Find all BEGIN blocks and move to start and END blocks and move to the end
    List<Stmt> beginBlocks = scriptStmts.stream()
                                        .filter(stmt -> stmt instanceof Stmt.Block)
                                        .filter(stmt -> ((Stmt.Block) stmt).isBeginBlock)
                                        .collect(Collectors.toList());
    List<Stmt> endBlocks = scriptStmts.stream()
                                      .filter(stmt -> stmt instanceof Stmt.Block)
                                      .filter(stmt -> ((Stmt.Block) stmt).isEndBlock)
                                      .collect(Collectors.toList());

    // If we have begin/end blocks or have to wrap script in a while loop for reading from input
    if (beginBlocks.size() > 0 || endBlocks.size() > 0 || context.printLoop() || context.nonPrintLoop()) {
      Stream<Stmt> bodyStream = scriptStmts.stream().filter(this::isNotBeginEndBlock).filter(stmt -> stmt != globalsParam);
      if (context.nonPrintLoop() || context.printLoop()) {
        List<Stmt> body       = bodyStream.collect(Collectors.toList());
        Token      whileToken = body.size() > 0 ? body.get(0).location : start;
        // : while ((it = nextLine()) != null)
        Stmt.While whileStmt = new Stmt.While(whileToken,
                                              new Expr.Binary(
                                         new Expr.VarAssign(new Expr.Identifier(whileToken.newIdent(Utils.IT_VAR)),
                                                            new Token(EQUAL,whileToken),
                                                            new Expr.Call(whileToken,
                                                                          new Expr.Identifier(whileToken.newIdent("nextLine")),
                                                                          Utils.listOf())),
                                         new Token(BANG_EQUAL, whileToken),
                                         new Expr.Literal(new Token(NULL, whileToken).setValue(null))), null);
        if (context.printLoop()) {
          Expr.Print println = new Expr.Print(whileToken, new Expr.Identifier(whileToken.newIdent(Utils.IT_VAR)), true);
          println.isResultUsed = false;
          body.add(new Stmt.ExprStmt(whileToken, println));
        }
        Stmt.Stmts whileBody = new Stmt.Stmts(whileToken);
        whileBody.stmts = body;
        whileStmt.body = whileBody;
        bodyStream = Stream.of(whileStmt);
      }

      scriptClass.scriptMain.declExpr.block.stmts.stmts =
        Stream.of(Stream.of(globalsParam),
                  beginBlocks.stream(),
                  bodyStream,
                  endBlocks.stream())
              .flatMap(s -> s)
              .collect(Collectors.toList());
    }
    return funDecl;
  }

  /**
   *<pre>
   *# packageDecl ::= PACKAGE IDENTIFIER ( DOT IDENTIFIER ) *
   *</pre>
   */
  private void packageDecl() {
    if (matchAny(PACKAGE)) {
      Token        packageToken = previous();
      List<String> packagePath  = new ArrayList<>();
      do {
        packagePath.add(expect(IDENTIFIER).getStringValue());
      } while (matchAny(DOT));
      String pkg = String.join(".", packagePath);
      if (packageName != null && !packageName.isEmpty() && !pkg.equals(packageName)) {
        error("Declared package name of '" + pkg + "' conflicts with package name '" + packageName + "'", packageToken);
      }
      packageName = pkg;
      matchAnyIgnoreEOL(SEMICOLON);
    }
    if (packageName == null) {
      error("Package name not declared or otherwise supplied");
    }
  }

  /**
   *# importStmt ::= IMPORT classPath (AS IDENTIFIER)?
   *#              | IMPORT STATIC classPath DOT ( IDENTIFIER | STAR )
   *#
   */
  List<Stmt.Import> importStmts() {
    List<Stmt.Import> stmts = new ArrayList<>();
    while (matchAnyIgnoreEOL(IMPORT)) {
      Token importToken = previous();
      boolean isStatic = matchAnyIgnoreEOL(STATIC);
      List<Expr> className = new ArrayList<>(Utils.listOf(classPathOrIdentifier(false)));
      int starCount = 0;
      while (matchAnyIgnoreEOL(DOT)) {
        if (starCount > 0) {
          error("Unexpected token '.' after '*'", previous());
        }
        Expr.Identifier identifier = identifier(IDENTIFIER, STAR);
        if (identifier.identifier.is(STAR)) {
          starCount++;
        }
        className.add(identifier);
      }
      if (isStatic && className.size() < 2) {
        error("Expected '.' after class name", className.get(0).location);
      }
      Token as = starCount == 0 && matchAnyIgnoreEOL(AS) ? expect(IDENTIFIER) : null;
      if (!isStatic && as != null && !Character.isUpperCase(as.getStringValue().charAt(0))) {
        error("Alias name for imported class must start with uppercase letter", as);
      }
      stmts.add(new Stmt.Import(importToken, className, as, isStatic));
      expect(EOL,SEMICOLON);
    }
    return stmts;
  }

  /**
   * <pre>
   *# block ::= LEFT_BRACE stmts RIGHT_BRACE
   *#         | stmts EOF      // Special case for top most script block
   *#
   * </pre>
   */
  private Stmt.Block block(TokenType endBlock) {
    return block(previous(), endBlock, () -> declaration());
  }

  private Stmt.Block block(Token openBlock, TokenType endBlock) {
    return block(openBlock, endBlock, () -> declaration());
  }

  private Stmt.Block block(Token openBlock, TokenType endBlock, Supplier<Stmt> stmtSupplier) {
    boolean currentIgnoreEol = ignoreEol;
    ignoreEol = false;
    Stmt.Stmts stmts = new Stmt.Stmts(openBlock);
    Stmt.Block block = new Stmt.Block(openBlock, stmts);
    pushBlock(block);
    try {
      stmts(stmts, stmtSupplier);
      expect(endBlock);
      return block;
    }
    finally {
      popBlock();
      ignoreEol = currentIgnoreEol;
    }
  }

  /**
   * <pre>
   *# stmts ::= declaration*
   * </pre>
   */
  private void stmts(Stmt.Stmts stmts, Supplier<Stmt> stmtSupplier) {
    Stmt previousStmt = null;
    stmts.location = peek();
    while (true) {
      matchAny(EOL);     // Ignore new lines if present
      if (peek().is(EOF, RIGHT_BRACE)) {
        break;
      }

      try {
        Token location    = peek();
        Stmt  declaration = stmtSupplier.get();
        if (declaration != null) {
          stmts.stmts.add(declaration);
          if (previousStmt instanceof Stmt.Return) {
            error("Unreachable statement", location);
          }
          previousStmt = declaration;

          if (!peekIsEOL() && peek().isNot(EOF, SEMICOLON, RIGHT_BRACE)) {
            unexpected("Expecting end of statement");
          }
        }
      }
      catch (CompileError e) {
        throw e;
      }
    }
  }

  /**
   * <pre>
   *# declaration ::= multiVarDecl
   *#               | funDecl
   *#               | varDecl
   *#               | classDecl
   *#               | statement
   * </pre>
   */
  private Stmt declaration() {
    return declaration(false);
  }

  private Stmt declaration(boolean inClassDecl) {
    // Look for multi var declaration: def (x, y) = [1,2]
    //                             or: def (int x, def y) = [1,2]
    if (lookahead(() -> expect(DEF) != null, () -> expect(LEFT_PAREN) != null)) {
      return multiVarDecl(inClassDecl);
    }

    // Look for function declaration: <type> <identifier> "(" ...
    if (isFunDecl(inClassDecl)) {
      return funDecl(inClassDecl);
    }
    if (isVarDecl()) {
      return varDecl(inClassDecl);
    }
    if (matchAny(CLASS)) {
      return classDecl();
    }
    if (matchAny(SEMICOLON)) {
      // Empty statement
      return null;
    }
    if (inClassDecl) {
      error("Expected field, method, or class declaration");
    }
    return statement();
  }

  /**
   * <pre>
   *# statement ::= ifStmt
   *#             | (IDENTIFIER COLON) ? forStmt
   *#             | (IDENTIFIER COLON) ? whileStmt
   *#             | (IDENTIFIER COLON) ? doUntilStmt
   *#             | beginEndBlock
   *#             | LEFT_BRACE stmts RIGHT_BRACE
   *#             | exprStmt
   * </pre>
   */
  private Stmt statement() {
    matchAny(EOL);
    Stmt stmt = null;
    switch (peek().getType()) {
      case LEFT_BRACE:        if (isMapLiteral())       stmt = exprStmt();      break;
      case BEGIN: case END:   if (isAtScriptTopLevel()) return beginEndBlock(); break;
      case IF:                return ifStmt();
      case WHILE:             return whileStmt(null);
      case FOR:               return forStmt(null);
      case DO:                stmt = doUntilStmt(null);                   break;
      case SEMICOLON:         return null;
      case IDENTIFIER:        if (lookaheadNoEOL(IDENTIFIER, COLON)) {
                                Token label = expect(IDENTIFIER);
                                expect(COLON);
                                matchAny(EOL);
                                if (peek().is(WHILE)) return whileStmt(label);
                                if (peek().is(FOR))   return forStmt(label);
                                if (peek().is(DO))    return doUntilStmt(label);
                                unexpected("Labels can only be applied to for, while, and do/until loops");
                              }
                              break;
    }

    if (stmt == null) {
      stmt = exprStmt();
    }
    if (!(stmt instanceof Stmt.ExprStmt)) {
      return stmt;
    }
    Stmt.ExprStmt exprStmt = (Stmt.ExprStmt)stmt;
    // We need to check if exprStmt was a parameterless closure and if so convert back into
    // statement block. The problem is that we can't tell the difference between a closure with
    // no parameters and a code block until after we have parsed them. (If the parameterless
    // closure is called immediately then we know it was a closure and we can tell that it was
    // invoked because expression type won't be a closure.) Otherwise, if the type is a closure
    // and it has no parameters we know it wasn't immediately invoked and we will treat it is a
    // code block.
    if (exprStmt.expr instanceof Expr.Closure) {
      Expr.Closure closure = (Expr.Closure)exprStmt.expr;
      if (closure.noParamsDefined) {
        removeItParameter(closure.funDecl.block);
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
        return new Stmt.If(ifToken, condition, exprStmt, null);
      }
      else {
        return new Stmt.If(ifToken, condition, null, exprStmt);
      }
    }

    // If we have a solitary return expression then convert to return statement
    if (exprStmt.expr instanceof Expr.Return) {
      final Expr.Return returnExpr = (Expr.Return) exprStmt.expr;
      returnExpr.isResultUsed = true;
      return new Stmt.Return(returnExpr.returnToken, returnExpr);
    }
    return exprStmt;
  }

  /**
   * <pre>
   *# type ::= DEF | VAR
   *#          | ( (BOOLEAN | BYTE | INT | LONG | DOUBLE | DECIMAL | STRING | MAP | LIST | OBJECT) (LEFT_SQUARE RIGHT_SQUARE) * )
   *#          | ( className ( LEFT_SQUARE RIGHT_SQUARE ) * )
   * </pre>
   * @param varAllowed       true if "var" is allowed in place of a type name
   * @param ignoreArrays     true if we should ignore "[" after the type
   * @param ignoreArrayError
   */
  JactlType type(boolean varAllowed, boolean ignoreArrays, boolean ignoreArrayError) {
    JactlType type;
    Token     typeToken = null;
    if (varAllowed ? matchAny(typesAndVar) : matchAny(types)) {
      typeToken = previous();
      type = JactlType.valueOf(typeToken.getType());
    }
    else {
      type = JactlType.createInstanceType(className());
    }
    // Allow arrays for everything but "def" and "var"
    if (!ignoreArrayError && typeToken != null && typeToken.is(VAR,DEF) && peek().is(LEFT_SQUARE)) {
      error("Unexpected '[' after " + typeToken.getChars());
    }
    if (!ignoreArrays) {
      while (matchAnyIgnoreEOL(LEFT_SQUARE)) {
        expect(RIGHT_SQUARE);
        type = JactlType.arrayOf(type);
      }
    }
    return type;
  }

  private boolean isFunDecl(boolean inClassDecl) {
    return lookahead(() -> {
      if (inClassDecl) {
        // Allow static or final for methods. Both are not allowed but we will detect that later.
        while (matchAnyIgnoreEOL(STATIC,FINAL)) {}
        matchAny(EOL);
      }
      type(false, false, false);
      if (!matchAnyIgnoreEOL(IDENTIFIER) && !matchKeywordIgnoreEOL()) {
        return false;
      }
      expect(LEFT_PAREN);
      return true;
    });
  }

  /**
   * <pre>
   *# funDecl ::= STATIC? type IDENTIFIER LEFT_PAREN parameters? RIGHT_PAREN block
   * </pre>
   */
  private Stmt.FunDecl funDecl(boolean inClassDecl) {
    boolean isStatic = false;
    boolean isFinal  = false;
    if (inClassDecl) {
      while (matchAnyIgnoreEOL(STATIC,FINAL)) {
        if (previousWas(STATIC)) {
          if (isStatic) { error("'static' cannot appear multiple times", previous()); }
          if (isFinal)  { error("Unexpected token: 'static' cannot be combined with 'final' for methods", previous()); }
          isStatic = true;
        }
        else {
          if (isFinal)  { error("Unexpected token: 'final' cannot appear multiple times", previous()); }
          if (isStatic) { error("Unexpected token: 'static' cannot be combined with 'final' for methods", previous()); }
          isFinal = true;
        }
      }
    }
    Token start = peek();
    JactlType returnType = type(false, false, false);
    Token      name       = expect(IDENTIFIER);
    expect(LEFT_PAREN);
    matchAny(EOL);
    List<Stmt.VarDecl> parameters = parameters(RIGHT_PAREN);
    if (inClassDecl && !isStatic && name.getStringValue().equals(Utils.TO_STRING)) {
      if (!returnType.is(JactlType.STRING, ANY)) {
        error(Utils.TO_STRING + "() must return String or use 'def'", start);
      }
      if (parameters.size() > 0) {
        error(Utils.TO_STRING + "() cannot have parameters", parameters.get(0).name);
      }
    }
    matchAny(EOL);
    expect(LEFT_BRACE);
    matchAny(EOL);
    Stmt.FunDecl funDecl = parseFunDecl(start, name, returnType, parameters, RIGHT_BRACE, false, isStatic, isFinal);
    Utils.createVariableForFunction(funDecl);
    funDecl.declExpr.varDecl.isField = inClassDecl;
    return funDecl;
  }

  /**
   * <pre>
   *# parameters ::= ( varDecl ( COMMA varDecl ) * ) ?
   * </pre>
   */
  private List<Stmt.VarDecl> parameters(TokenType endToken) {
    List<Stmt.VarDecl> parameters = new ArrayList<>();
    while (!matchAny(endToken)) {
      if (parameters.size() > 0) {
        expect(COMMA);
        matchAny(EOL);
      }
      // Check for optional type. Default to "def".
      JactlType type = optionalType();

      // Note that unlike a normal varDecl where commas separate different vars of the same type,
      // with parameters we expect a separate comma for parameter with a separate type for each one
      // so we build up a list of singleVarDecl.
      Stmt.VarDecl varDecl = singleVarDecl(type, false, false);
      varDecl.declExpr.isParam = true;
      parameters.add(varDecl);
      matchAny(EOL);
    }
    return parameters;
  }

  /**
   * This is used in situations where we either have a type followed by an identifier or an identifier
   * on its own.
   * @return the type or ANY if no type specified
   */
  private JactlType optionalType() {
    JactlType type = ANY;
    if (peek().is(typesAndVar)) {
      type = type(true, false, false);
    }
    if (!peek().is(IDENTIFIER)) {
      unexpected("Expected valid type or parameter name");
    }
    // We have an identifier but we don't yet know if it is a parameter name or a class name
    if (lookahead(() -> className() != null, () -> matchAny(IDENTIFIER))) {
      type = JactlType.createInstanceType(className());   // we have a class name
    }
    return type;
  }

  private boolean isVarDecl() {
    return lookahead(() -> {
      boolean isConst = matchAny(CONST);
      while(matchAny(STATIC,FINAL)) {}
      matchAny(CONST);
      if (!isConst || peek().is(simpleTypes)) {
        type(true, false, true);
      }
      return matchError() || matchAnyIgnoreEOL(IDENTIFIER,UNDERSCORE,DOLLAR_IDENTIFIER) || matchKeywordIgnoreEOL();
    });
  }

  /**
   * <pre>
   *# varDecl ::= type singleVarDecl ? ( COMMA singleVarDecl ? ) *
   * </pre>
   * NOTE: we turn either a single Stmt.VarDecl if only one variable declared or we return Stmt.Stmts with
   *       a list of VarDecls if multiple variables declared.
   */
  private Stmt varDecl(boolean inClassDecl) {
    return doVarDecl(inClassDecl);
  }

  private Stmt doVarDecl(boolean inClassDecl) {
    if (matchAny(STATIC,FINAL)) {
      error("Unexpected token: fields/variables cannot be " + previous().getStringValue() + " (perhaps 'const' was intended)", previous());
    }
    boolean isConst = matchAny(CONST);
    JactlType type = isConst ? JactlType.createUnknown() : ANY;
    if (isConst) {
      if (peek().is(simpleTypes)) {
        // Only simple types allowed for constants. If no type given then will work like 'var' and
        // inherit type from initialiser.
        type = JactlType.valueOf(expect(simpleTypes).getType());
      }
      else if (peek().is(types)) {
        unexpected("Constants can only be simple types");
      }
    }
    else {
      type = type(true, false, false);
    }
    Stmt.Stmts stmts = new Stmt.Stmts(peek());
    stmts.stmts.add(singleVarDecl(type, inClassDecl, isConst));
    while (matchAny(COMMA)) {
      matchAny(EOL);
      stmts.stmts.add(singleVarDecl(type, inClassDecl, isConst));
    }
    if (stmts.stmts.size() > 1) {
      // Multiple variables so return list of VarDecls
      return stmts;
    }
    // Single variable so return just that one
    return stmts.stmts.get(0);
  }

  /**
   *# singleVarDecl ::= IDENTIFIER ( EQUAL expression ) ?
   */
  private Stmt.VarDecl singleVarDecl(JactlType type, boolean inClassDecl, boolean isConst) {
    if (isConst && peek().is(LEFT_SQUARE)) {
      // Catches: const int[] x = [1,2,3] (for example)
      error("Constants can only be simple types", previous());
    }
    Token identifier  = expect(IDENTIFIER);
    Expr  initialiser = null;
    Token equalsToken = null;
    if (matchAny(EQUAL)) {
      equalsToken = previous();
      matchAny(EOL);
      initialiser = parseExpression();
    }

    if ((type.is(UNKNOWN) || isConst) && initialiser == null) {
      if (isConst && peek().is(IDENTIFIER,LEFT_SQUARE) && Character.isUpperCase(identifier.getStringValue().charAt(0))) {
        error("Constants can only be simple types", previous());
      }
      error("Initialiser expression required for '" + (isConst ? "const" : "var") + "' declaration", previous());
    }
    if (type.is(UNKNOWN)) {
      // Create a new one in case we are in a multi-variable declaration where we need a separate type per variable
      type = JactlType.createUnknown();
      type.typeDependsOn(initialiser);   // Once initialiser type is known the type will then be known
    }

    // If we have an instance and initialiser is not "new X(...)" then convert to "initilaliser as X" to
    // convert rhs into instance of X.
    //   X x = [i:1,j:2]  ==> X x = [i:1,j:2] as X
    if (type.is(JactlType.INSTANCE) && initialiser != null && !initialiser.isNull() && !initialiser.isNewInstance()) {
      initialiser = asExpr(type, initialiser);
    }

    return createVarDecl(identifier, type, equalsToken, initialiser, inClassDecl, isConst);
  }

  private Stmt.VarDecl createVarDecl(Token identifier, JactlType type, Token assignmentOp, Expr initialiser, boolean inClassDecl, boolean isConst) {
    return new Stmt.VarDecl(identifier, Utils.createVarDeclExpr(identifier, type, assignmentOp, initialiser, inClassDecl, isConst, false));
  }

  private static Expr.Binary asExpr(JactlType type, Expr expr) {
    return new Expr.Binary(expr, new Token(AS, expr.location), new Expr.TypeExpr(expr.location, type));
  }

  /**
   * <pre>
   *# multiVarDecl ::= DEF LEFT_PAREN type? IDENTIFIER (COMMA type? IDENTIFIER ) * RIGHT_PAREN EQUAL LEFT_SQUARE expression (COMMA expression)* RIGHT_SQUARE
   * </pre>
   */
  private Stmt multiVarDecl(boolean isInClassDecl) {
    expect(DEF);
    expect(LEFT_PAREN);
    List<JactlType> types       = new ArrayList<>();
    List<Token>     identifiers = new ArrayList<>();
    Token           firstVarTok = null;
    while (types.isEmpty() || !matchAny(RIGHT_PAREN)) {
      if (!types.isEmpty()) {
        expect(COMMA);
      }
      JactlType type = optionalType();
      firstVarTok = type.is(UNKNOWN) && firstVarTok == null ? previous() : firstVarTok;
      types.add(type);
      identifiers.add(expect(IDENTIFIER));
    }
    matchAny(EOL);
    Stmt.Stmts stmts      = new Stmt.Stmts(peek());
    if (peek().is(EQUAL)) {
      Token      equalToken = expect(EQUAL);
      Token      rhsToken   = peek();
      Token      rhsName    = rhsToken.newIdent(Utils.JACTL_PREFIX + "multiAssign" + uniqueVarCnt++);

      Expr rhsExpr = expression();
      if (firstVarTok != null) {
        if (!(rhsExpr instanceof Expr.ListLiteral)) {
          error("Cannot infer type in multi-assignment (right-hand side is non-list type)", firstVarTok);
        }
        // Add a type dependency on type of subexpr of rhs for each "var"
        List<Expr> list = ((Expr.ListLiteral) rhsExpr).exprs;
        for (int i = 0; i < types.size(); i++) {
          JactlType type = types.get(i);
          if (type.is(UNKNOWN)) {
            if (i >= list.size()) {
              error("Cannot infer type in multi-assignment from given right-hand side (not enough entries in list)", firstVarTok);
            }
            type.typeDependsOn(list.get(i));
          }
        }
      }

      // Create a variable for storing rhs. We don't need it after assignment, but it hangs around until
      // end of current scope - could handle this better but not a high priority since not an actual leak.
      //: def _$jmultiAssignN = expression() as List
      stmts.stmts.add(createVarDecl(rhsName, JactlType.ANY, equalToken, rhsExpr, isInClassDecl, false));
      for (int i = 0; i < identifiers.size(); i++) {
        Token varName = identifiers.get(i);
        // def variable = _$jmultiAssignN[i]
        stmts.stmts.add(createVarDecl(varName, types.get(i), equalToken,
                                      new Expr.Binary(new Expr.Identifier(rhsName),
                                                      new Token(LEFT_SQUARE, equalToken),
                                                      new Expr.Literal(new Token(INTEGER_CONST, equalToken).setValue(i))),
                                      isInClassDecl, false));
      }
    }
    else {
      // No initialisers
      for (int i = 0; i < identifiers.size(); i++) {
        Token varName = identifiers.get(i);
        stmts.stmts.add(createVarDecl(varName, types.get(i), null, null, isInClassDecl, false));
      }
    }
    return stmts;
  }

  /**
   * <pre>
   *# ifStmt ::= IF LEFT_PAREN condition RIGHT_PAREN statement ( ELSE statement ) ?
   * </pre>
   */
  private Stmt.If ifStmt() {
    Token ifToken = expect(IF);
    expect(LEFT_PAREN);
    Expr cond      = condition(true, RIGHT_PAREN);
    Stmt trueStmt  = statement();
    Stmt falseStmt = null;
    if (lookahead(() -> matchAny(EOL) || true, () -> matchAny(ELSE))) {
      matchAny(EOL);
      if (matchAny(ELSE)) {
        falseStmt = statement();
      }
    }
    return new Stmt.If(ifToken, cond, trueStmt, falseStmt);
  }

  /**
   *# whileStmt ::= WHILE LEFT_PAREN condition RIGHT_PAREN statement
   */
  private Stmt whileStmt(Token label) {
    Token whileToken = expect(WHILE);
    expect(LEFT_PAREN);
    Expr       cond      = condition(false, RIGHT_PAREN);
    Stmt.While whileStmt = new Stmt.While(whileToken, cond, label);
    whileStmt.body = statement();
    return stmtBlock(whileToken, whileStmt);
  }

  /**
   *<pre>
   *# doUntilStmt ::= DO BLOCK UNTIL LEFT_PAREN condition RIGHT_PAREN
   *</pre>
   */
  private Stmt doUntilStmt(Token label) {
    Token doToken = peek();
    // We can't tell whether we have a do/until or simple do expression
    // at this point so we will parse as an expression and then check if
    // we have a following "until"
    Stmt.ExprStmt stmt = exprStmt();
    if (stmt.expr instanceof Expr.Block) {
      if (matchAnyIgnoreEOL(UNTIL)) {
        expect(LEFT_PAREN);
        Expr       cond      = condition(false, RIGHT_PAREN);
        Stmt.While whileStmt = new Stmt.While(doToken, cond, label);
        whileStmt.body = stmt;
        whileStmt.isDoUntil = true;
        return stmtBlock(doToken, whileStmt);
      }
      else if (label != null) {
        error("Labels can only be applied to for, while, and do/until loops", label);
      }
    }
    // Not do/while so return our ExprStmt
    return stmt;
  }

  /**
   * <pre>
   *# forStmt ::= FOR LEFT_PAREN declaration SEMICOLON condition SEMICOLON
   *#                         commaSeparatedStatements RIGHT_PAREN statement
   * </pre>
   */
  private Stmt forStmt(Token label) {
    Token forToken = expect(FOR);
    expect(LEFT_PAREN);
    matchAny(EOL);
    Stmt initialisation;
    if (isVarDecl()) {
      initialisation    = varDecl(false);
    }
    else {
      initialisation    = commaSeparatedStatements();
    }
    if (!previous().is(SEMICOLON)) {
      expect(SEMICOLON);
    }
    Expr cond        = condition(false, SEMICOLON);
    Stmt update      = commaSeparatedStatements();
    expect(RIGHT_PAREN);

    Stmt.While whileStmt = new Stmt.While(forToken, cond, label);
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
   * <pre>
   *# commaSeparatedStatements ::= ( statement ( COMMA statement ) * ) ?
   * </pre>
   */
  Stmt.Stmts commaSeparatedStatements() {
    matchAny(EOL);
    if (peek().is(RIGHT_PAREN)) {
      return null;
    }
    Stmt.Stmts stmts = new Stmt.Stmts(previous());
    stmts.stmts.add(statement());
    while (matchAny(COMMA)) {
      stmts.stmts.add(statement());
    }
    return stmts;
  }

  /**
   * <pre>
   *# beginEndBlock ::= (BEGIN | END) block
   * </pre>
   */
  Stmt.Block beginEndBlock() {
    Token blockType = expect(BEGIN, END);
    expect(LEFT_BRACE);
    Stmt.Block block = block(previous(), RIGHT_BRACE);
    block.isBeginBlock = blockType.is(BEGIN);
    block.isEndBlock   = blockType.is(END);
    return block;
  }

  /**
   * <pre>
   *# exprStmt ::= expression
   * </pre>
   */
  private Stmt.ExprStmt exprStmt() {
    Expr  expr     = expression();
    Stmt.ExprStmt stmt = createStmtExpr(expr);
    return stmt;
  }

  private Stmt.ExprStmt createStmtExpr(Expr expr) {
    expr.isResultUsed = false;    // Expression is a statement so result not used
    Stmt.ExprStmt stmt = new Stmt.ExprStmt(expr.location, expr);
    return stmt;
  }

  private Stmt.Block stmtBlock(Token startToken, Stmt... statements) {
    Stmt.Stmts stmts = new Stmt.Stmts(startToken);
    stmts.stmts.addAll(Arrays.asList(statements));
    return new Stmt.Block(startToken, stmts);
  }

  /**
   * <pre>
   *# classDecl ::= CLASS IDENTIFIER (EXTENDS className)? LEFT_BRACE
   *#                ( singleVarDecl | STATIC? funDecl | classDecl ) *
   *#               RIGHT_BRACE
   * </pre>
   */
  private Stmt.ClassDecl classDecl() {
    if (!isClassDeclAllowed()) {
      error("Class declaration not allowed here", previous());
    }
    Token className      = expect(IDENTIFIER);
    Token baseClassToken = null;
    JactlType baseClass = null;
    if (matchAny(EXTENDS)) {
      baseClassToken = peek();
      baseClass = JactlType.createClass(className());
    }
    Token leftBrace = expect(LEFT_BRACE);

    Stmt.ClassDecl classDecl = new Stmt.ClassDecl(className, packageName, baseClassToken, baseClass, false);
    pushClass(classDecl);
    try {
      Stmt.Block classBlock = block(leftBrace, RIGHT_BRACE, () -> declaration(true));

      // We have a block of field, method, and class declarations. We strip out the fields, methods and classes into
      // separate lists:
      List<Stmt> classStmts = classBlock.stmts.stmts;
      List<Stmt.ClassDecl> innerClasses = classBlock.stmts.stmts.stream()
                                                                .filter(stmt -> stmt instanceof Stmt.ClassDecl)
                                                                .map(stmt -> (Stmt.ClassDecl) stmt)
                                                                .collect(Collectors.toList());

      // Since we allow multiple fields in the same declaration we need to check for Stmt.Stmts as well as
      // just Stmt.VarDecl (e.g. int i,j=2  --> Stmt.Stmts(VarDecl i, VarDecl j) so we use flatMap to flatten
      // to a single stream of Stmt objects.
      classDecl.fields = classStmts.stream()
                                   .flatMap(stmt -> stmt instanceof Stmt.Stmts ? ((Stmt.Stmts) stmt).stmts.stream()
                                                                               : Stream.of(stmt))
                                   .filter(stmt -> stmt instanceof Stmt.VarDecl)
                                   .map(stmt -> (Stmt.VarDecl) stmt)
                                   .collect(Collectors.toList());
      classDecl.fields.forEach(field -> classDecl.fieldVars.put(field.name.getStringValue(), field.declExpr));
      classBlock.functions   = classDecl.methods;
      classDecl.innerClasses = innerClasses;
      return classDecl;
    }
    finally {
      popClass();
    }
  }

  ////////////////////////////////////////////

  // = Expr

  private static TokenType[] fieldAccessOp = new TokenType[] { DOT, QUESTION_DOT, LEFT_SQUARE, QUESTION_SQUARE };

  // NOTE: type cast also has same precedence as unaryOps but has no specific operator
  private static List<TokenType> unaryOps = Utils.listOf(QUESTION_QUESTION, GRAVE, BANG, MINUS_MINUS, PLUS_PLUS, MINUS, PLUS /*, (type) */);

  // Operators from least precedence to highest precedence. Each entry in list is
  // a pair of a boolean and a list of the operators at that level of precedence.
  // The boolean indicates whether the operators are left-associative (true) or
  // right-associative (false).
  private static List<Pair<Boolean,List<TokenType>>> operatorsByPrecedence =
    Utils.listOf(
      // These are handled separately in separate productions to parseExpression:
      //      new Pair(true, Utils.listOf(OR)),
      //      new Pair(true, Utils.listOf(AND)),
      //      new Pair(true, Utils.listOf(NOT)),

      new Pair(false, Utils.listOf(EQUAL, QUESTION_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PERCENT_PERCENT_EQUAL, PLUS_EQUAL, MINUS_EQUAL,
      /* STAR_STAR_EQUAL, */ DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN_EQUAL, AMPERSAND_EQUAL,
         PIPE_EQUAL, ACCENT_EQUAL)),
      new Pair(true, Utils.listOf(QUESTION, QUESTION_COLON)),
      new Pair(true, Utils.listOf(PIPE_PIPE)),
      new Pair(true, Utils.listOf(AMPERSAND_AMPERSAND)),
      new Pair(true, Utils.listOf(PIPE)),
      new Pair(true, Utils.listOf(ACCENT)),
      new Pair(true, Utils.listOf(AMPERSAND)),
      new Pair(true, Utils.listOf(EQUAL_EQUAL, BANG_EQUAL, COMPARE, EQUAL_GRAVE, BANG_GRAVE, TRIPLE_EQUAL, BANG_EQUAL_EQUAL)),
      new Pair(true, Utils.listOf(LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, INSTANCE_OF, BANG_INSTANCE_OF, IN, BANG_IN, AS)),
      new Pair(true, Utils.listOf(DOUBLE_LESS_THAN, DOUBLE_GREATER_THAN, TRIPLE_GREATER_THAN)),
      new Pair(true, Utils.listOf(MINUS, PLUS)),
      new Pair(true, Utils.listOf(STAR, SLASH, PERCENT, PERCENT_PERCENT)),
      //      Utils.listOf(STAR_STAR)
      new Pair(true, unaryOps),
      new Pair(true, RuntimeUtils.concat(fieldAccessOp, LEFT_PAREN, LEFT_BRACE))
    );

  // Binary operators which can also sometimes be the start of an expression. We need to know
  // when we are checking if an expression continues on the next line whether the next operator
  // should be treated as part of the current expression or the start of a new one.
  // NOTE: we include '/' and '/=' since they could indicate start of a regex.
  //       '-' is also included since it can be the start of a negative number
  private static Set<TokenType> exprStartingOps = Utils.setOf(LEFT_SQUARE, LEFT_PAREN, LEFT_BRACE, SLASH, SLASH_EQUAL, MINUS);

  /**
   *# condition ::= expression
   * Used for situations where we need a boolean condition.
   */
  private Expr condition(boolean mandatory, TokenType endOfCond) {
    if (!mandatory) {
      if (matchAnyIgnoreEOL(endOfCond)) {
        return new Expr.Literal(new Token(TRUE, peek()).setValue(true));
      }
    }
    Expr expr = expression(true);
    expect(endOfCond);
    return expr;
  }

  /**
   * <pre>
   *# expression ::= orExpression
   * </pre>
   * Note that we handle special case of regex on its own here.
   * A regex as a single statement or single expr where we expect some kind of condition
   * (such as in an if/while) is turned into "it =~ /regex/" to make it a bit like perl syntax
   * where "/pattern/" means "$_ =~ /pattern/".
   */
  private Expr expression() {
    return expression(false);
  }
  private Expr expression(boolean ignoreEol) {
    if (ignoreEol) {
      return (Expr) ignoreNewLines(() -> orExpression());
    }
    return orExpression();
  }

  /**
   * <pre>
   *# orExpression ::= andExpression ( OR andExpression) *
   * </pre>
   */
  private Expr orExpression() {
    Expr expr = andExpression();
    while (matchAnyIgnoreEOL(OR)) {
      expr = new Expr.Binary(expr, new Token(PIPE_PIPE, previous()), andExpression());
    }
    return expr;
  }

  /**
   * <pre>
   *# andExpression ::= notExpression ( AND notExpression ) *
   * </pre>
   */
  private Expr andExpression() {
    Expr expr = notExpression();
    while (matchAnyIgnoreEOL(AND)) {
      expr = new Expr.Binary(expr, new Token(AMPERSAND_AMPERSAND, previous()), notExpression());
    }
    return expr;
  }

  /**
   * <pre>
   *# notExpression ::= NOT * (returnExpr | printExpr | dieExpr | BREAK | CONTINUE | expr)
   * </pre>
   */
  private Expr notExpression() {
    Expr expr;
    if (matchAnyIgnoreEOL(NOT)) {
      Token notToken = previous();
      expr = new Expr.PrefixUnary(new Token(BANG, notToken), notExpression());
    }
    else {
      matchAny(EOL);
      if (matchAny(RETURN))         { expr = returnExpr();                  } else
      if (matchAny(PRINT,PRINTLN))  { expr = printExpr();                   } else
      if (matchAny(DIE))            { expr = dieExpr();                     } else
      if (matchAny(BREAK,CONTINUE)) {
        Token type  = previous();
        Token label = null;
        if (peekNoEOL().is(IDENTIFIER)) {
          label = expect(IDENTIFIER);
        }
        expr = type.is(BREAK) ? new Expr.Break(type, label)
                              : new Expr.Continue(type, label);
      }
      else {
        expr = parseExpression();
      }
    }
    return expr;
  }

  /**
   * <pre>
   * expr ::= LEFT_PAREN expr ( COMMA expr ) * RIGHT_PAREN EQUAL expr
   *        | expr operator expr
   *        | expr QUESTION expr COLON expr
   *        | unary
   *        | primary
   * </pre>
   * Parse expressions (mostly binary operations).
   * We parse based on operator precedence. If we reach highest level of precedence
   * then we return a primaru().
   * We check whether current level of precedence corresponds to the unary ops and
   * return unary() if that is the case.
   * We allow EOL to be treated as whitespace if we are in a bracketed expression
   * (for example) when it normally could signal the end of a statement.
   */
  private Expr parseExpression() {
    return parseExpression(0);
  }

  private Expr parseExpression(int level) {
    matchAny(EOL);

    // If we have reached highest precedence
    if (level == operatorsByPrecedence.size()) {
      return primary();
    }

    // Get list of operators at this level of precedence along with flag
    // indicating whether they are left-associative or not.
    Pair<Boolean, List<TokenType>> operatorsPair     = operatorsByPrecedence.get(level);
    boolean                        isLeftAssociative = operatorsPair.first;
    List<TokenType> operators = operatorsPair.second;

    // If this level of precedence is for our unary operators (includes type cast)
    if (operators == unaryOps) {
      return unary(level);
    }

    Expr expr = parseExpression(level + 1);

    while (matchOp(operators)) {
      Token operator = previous();
      if (operator.is(INSTANCE_OF,BANG_INSTANCE_OF,AS)) {
        Token token = peek();
        JactlType type = type(false, false, false);
        expr = new Expr.Binary(expr, operator, new Expr.TypeExpr(token, type));
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
        expr = createCallExpr(expr, operator, arguments());
        continue;
      }

      // Set flag if next token is '(' so that we can check for x.(y).z below
      boolean bracketedExpression = peek().is(LEFT_PAREN);

      Expr rhs;
      if (operator.is(LEFT_SQUARE,QUESTION_SQUARE)) {
        // '[' and '?[' can be followed by any expression and then a ']'
        rhs = expression(true);
      }
      else {
        if (operator.is(DOT,QUESTION_DOT)) {
          if (peek().is(LEFT_SQUARE,QUESTION_SQUARE)) {
            unexpected("invalid token after '" + operator.getChars() + "'");
          }
        }
        rhs = parseExpression(level + (isLeftAssociative ? 1 : 0));
      }

      // For List/Map literals that are constant values (no expressions) we compile as class
      // static consts if they are used in contexts where it is obvious that the values won't
      // be mutated:
      // I.e. when operator is: 'in', '!in', '==', '!=', '===', '!==', '[', '?['
      if (operator.is(IN,BANG_IN,EQUAL_EQUAL,BANG_EQUAL_EQUAL,TRIPLE_EQUAL,BANG_EQUAL_EQUAL,LEFT_SQUARE,QUESTION_SQUARE)) {
        if (expr.isLiteral() && isConstListOrMap(expr)) {
          expr.isConst = true;
          expr.constValue = literalValue(expr);
          addClassConstant(expr);
        }
        if (rhs.isLiteral() && isConstListOrMap(rhs)) {
          rhs.isConst = true;
          rhs.constValue = literalValue(rhs);
          addClassConstant(rhs);
        }
      }

      // Check for lvalue for assignment operators
      if (operator.getType().isAssignmentLike()) {
        expr = convertToLValue(expr, operator, rhs, false, true);
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
        // by default, we need to strip off the "it =~" and replace with current one
        if (operator.is(EQUAL_GRAVE,BANG_GRAVE)) {
          if (rhs instanceof Expr.RegexMatch && ((Expr.RegexMatch) rhs).implicitItMatch) {
            if (rhs instanceof Expr.RegexSubst && operator.is(BANG_GRAVE)) {
              error("Operator '!~' cannot be used with regex substitution", operator);
            }
            Expr.RegexMatch regex = (Expr.RegexMatch) rhs;
            regex.string = expr;
            regex.operator = operator;
            regex.implicitItMatch = false;
            expr = regex;
          }
          else
          if (isImplicitRegexSubstitute(rhs)) {
            if (operator.is(BANG_GRAVE)) {
              error("Operator '!~' cannot be used with regex substitution", operator);
            }
            Expr.RegexSubst regexSubst = (Expr.RegexSubst)((Expr.VarOpAssign)rhs).expr;
            regexSubst.implicitItMatch = false;
            expr = convertToLValue(expr, operator, regexSubst, false, false);
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
   * <pre>
   * unary ::= ( QUESTION_QUESTION | BANG | MINUS_MINUS | PLUS_PLUS | MINUS | PLUS | GRAVE | LEFT_PAREN type RIGHT_PAREN )
   *                  unary ( MINUS_MINUS | PLUS_PLUS )
   *         | expression
   * </pre>
   */
  private Expr unary(int precedenceLevel) {
    Expr expr;
    matchAny(EOL);
    if (isCast()) {
      // Type cast. Rather than create a separate operator for each type case we just use the
      // token for the type as the operator if we detect a type cast.
      expect(LEFT_PAREN);
      matchAny(EOL);
      Token     typeToken = peek();
      JactlType castType  = type(false, false, false);
      matchAny(EOL);
      expect(RIGHT_PAREN);
      Expr unary = unary(precedenceLevel);
      expr = new Expr.Cast(typeToken, castType, unary);
    }
    else
    if (!isPlusMinusNumber() && matchAny(unaryOps)) {
      // Ignore +number or -number where we want to parse as a number rather than as a unary expression
      // This is to allow us to invoke methods on negative numbers (e.g. -6.toBase(2)) which wouldn't
      // work normally since "." has higher precedence than "-". We will ignore here and deal with this
      // in primary().

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
        expr = convertToLValue(unary, operator, new Expr.Literal(new Token(BYTE_CONST, operator).setValue(1)), false, true);
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
        expr = convertToLValue(expr, operator, new Expr.Literal(new Token(BYTE_CONST, operator).setValue(1)), true, true);
      }
      else {
        expr = new Expr.PostfixUnary(expr, previous());
      }
    }
    return expr;
  }

  private boolean isCast() {
    return lookahead(() -> matchAny(LEFT_PAREN), () -> type(false, false, false) != null, () -> matchAny(RIGHT_PAREN));
  }

  private boolean isPlusMinusNumber() {
    return lookahead(() -> matchAny(PLUS, MINUS) && matchAny(BYTE_CONST, INTEGER_CONST, LONG_CONST, DECIMAL_CONST, DOUBLE_CONST));
  }

  /**
   * <pre>
   *# newInstance ::= NEW classPathOrIdentifier LEFT_PAREN arguments RIGHT_PAREN
   *#                 | NEW type (LEFT_SQUARE expression RIGHT_SQUARE ) ( LEFT_SQUARE expression RIGHT_SQUARE ) *
   *#                         ( LEFT_SQUARE RIGHT_SQUARE ) *
   * </pre>
   */
  private Expr newInstance() {
    Token     token = expect(NEW);
    JactlType type  = type(true, true, false);   // get the type and ignore the square brackets for now
    if (type.is(JactlType.INSTANCE)) {
      if (matchAnyIgnoreEOL(LEFT_PAREN)) {
        Token      leftParen = previous();
        List<Expr> args      = arguments();
        return Utils.createNewInstance(token, type, leftParen, args);
      }
    }
    peekExpect(LEFT_SQUARE);
    boolean seenEmptyBrackets = false;
    List<Expr> dimensions = new ArrayList<>();
    while (matchAnyIgnoreEOL(LEFT_SQUARE)) {
      type = JactlType.arrayOf(type);
      if (matchAnyIgnoreEOL(RIGHT_SQUARE)) {
        if (dimensions.size() == 0) {
          error("Need a size for array allocation");
        }
        seenEmptyBrackets = true;
      }
      else {
        if (!seenEmptyBrackets) {
          // Can't have dimensions after first []
          dimensions.add(expression(true));
        }
        expect(RIGHT_SQUARE);
      }
    }
    return Utils.createNewInstance(token, type, dimensions);
  }

  /**
   * <pre>
   *# expressionList ::= expression ( COMMA expression ) *
   * </pre>
   */
  private List<Expr> expressionList(TokenType endToken) {
    List<Expr> exprs = new ArrayList<>();
    while (!matchAnyIgnoreEOL(endToken)) {
      if (exprs.size() > 0) {
        expect(COMMA);
      }
      exprs.add(expression(true));
      matchAny(EOL);
    }
    return exprs;
  }

  /**
   * <pre>
   *# arguments ::= mapEntries | argList
   * </pre>
   */
  private List<Expr> arguments() {
    // Check for named args
    if (lookahead(() -> mapKey() != null, () -> matchAnyIgnoreEOL(COLON))) {
      // For named args we create a list with single entry being the map literal that represents
      // the name:value pairs.
      Expr.MapLiteral mapEntries = mapEntries(RIGHT_PAREN);
      return Utils.listOf(mapEntries);
    }
    else {
      return argList();
    }
  }

  /**
   * <pre>
   *# argList ::= expressionList ? ( LEFT_BRACE closure RIGHT_BRACE ) *
   * </pre>
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
   * <pre>
   *# primary ::= ((PLUS | MINUS)? INTEGER_CONST | DECIMAL_CONST | DOUBLE_CONST)
   *#           | STRING_CONST
   *#           | TRUE | FALSE | NULL
   *#           | exprString
   *#           | regexSubstitute
   *#           | (IDENTIFIER | DOLLAR_IDENTIFIER | classPath)
   *#           | mapOrListLiteral
   *#           | nestedExpr
   *#           | doBlock
   *#           | closure
   *#           | switchExpr
   *#           | evalExpr
   *#           | newInstance
   * </pre>
   */
  private Expr primary() {
    Token prev = previous();
    matchAny(EOL);
    switch (peek().getType()) {
      case PLUS:          case MINUS:
      case BYTE_CONST:    case INTEGER_CONST: case TRUE:
      case DECIMAL_CONST: case DOUBLE_CONST:  case FALSE:
      case STRING_CONST:  case LONG_CONST:    case NULL:     return literal(prev);

      case SLASH: case SLASH_EQUAL: case EXPR_STRING_START:  return exprString();
      case REGEX_SUBST_START:                                return regexSubstitute();
      case LEFT_BRACE:                                       return mapLiteralOrClosure();
      case LEFT_SQUARE:                                      return mapOrListLiteral();
      case LEFT_PAREN:                                       return nestedExpr();
      case EVAL:                                             return evalExpr();
      case NEW:                                              return newInstance();
      case DO:                                               return doBlock();
      case SWITCH:                                           return switchExpr();
      case IDENTIFIER: case DOLLAR_IDENTIFIER:
      case UNDERSCORE: case STAR:                            return classPathOrIdentifier(true);
    }

    if (prev != null && prev.is(DOT,QUESTION_DOT) && peek().isKeyword()) {
      // Allow keywords to be used in dotted paths. E.g: x.y.while.z
      return new Expr.Literal(new Token(STRING_CONST, advance()).setValue(previous().getChars()));
    }
    return unexpected("Expected start of expression");
  }

  /**
   *# doBlock ::= DO block
   */
  Expr doBlock() {
    expect(DO);
    matchAny(EOL);
    Token leftBrace = expect(LEFT_BRACE);
    return new Expr.Block(leftBrace, block(RIGHT_BRACE));
  }

  /**
   *# nestedExpr ::= LEFT_PAREN expression ( COMMA expression ) * RIGHT_PAREN   // for lhs of multi-assignments
   *#              | LEFT_PAREN expression RIGHT_PAREN
   * Returns a single expression or an ExprList if comma separated.
   * Comma-separated expressions can be used in LHS of multi-assignments.
   */
  private Expr nestedExpr() {
    Token      lparen = expect(LEFT_PAREN);
    List<Expr> exprs    = new ArrayList<>();
    // Keep building list while we have COMMA
    do {
      exprs.add(expression(true));
    } while (matchAnyIgnoreEOL(COMMA));
    matchAny(EOL);
    expect(RIGHT_PAREN);

    // If only one expression then return it but flag it as having been inside parentheses
    if (exprs.size() == 1) {
      Expr expr = exprs.get(0);
      expr.wasNested = true;
      return expr;
    }
    return new Expr.ExprList(lparen, exprs);
  }

  private Expr.Literal literal(Token previous) {
    if (previous != null && previous.is(DOT,QUESTION_DOT) && peek().is(NULL)) {
      // Special case for 'null' when used as field name
      Token current = expect(NULL);
      return new Expr.Literal(current.newLiteral("null"));
    }
    return literal();
  }

  /**
   *# literal ::= plusMinusNumber | BYTE_CONST | INTEGER_CONST | LONG_CONST | DOUBLE_CONST
   *#           | DECIMAL_CONST | STRING_CONST | TRUE | FALSE | NULL
   */
  private Expr.Literal literal() {
    Token current = advance();
    if (current.is(PLUS,MINUS)) {
      return getPlusMinusNumber();
    }
    return new Expr.Literal(current);
  }

  private boolean isLiteral() {
    switch (peek().getType()) {
      case PLUS:          case MINUS:
      case BYTE_CONST:    case INTEGER_CONST: case TRUE:
      case DECIMAL_CONST: case DOUBLE_CONST:  case FALSE:
      case STRING_CONST:  case LONG_CONST:    case NULL:
        return true;
      default:
        return false;
    }
  }

  /**
   *# plusMinusNumber ::= (PLUS | MINUS) (BYTE_CONST | INTEGER_CONST | LONG_CONST | DOUBLE_CONST | DECIMAL_CONST)
   */
  private Expr.Literal getPlusMinusNumber() {
    Token sign = previous();
    Token num  = expect(BYTE_CONST,INTEGER_CONST,LONG_CONST,DOUBLE_CONST,DECIMAL_CONST);
    if (sign.is(MINUS)) {
      num = new Token(num.getType(), num).setValue(RuntimeUtils.negateNumber(num.getValue(), num.getSource(), num.getOffset()));
    }
    return new Expr.Literal(num);
  }

  /**
   * <pre>
   *# classPathOrIdentifier ::= IDENTIFIER | classPath
   * </pre>
   */
  private Expr classPathOrIdentifier(boolean allowDollarDigits) {
    // Can only be classPath if previous token was not "." since we want to avoid treating a.x.y.z.A
    // as a."x.y.z.A" if x.y.z is a package name but a.x.y.z isn't.
    if (!previousWas(DOT)) {
      Expr classPath = classPath();
      if (classPath != null) {
        return classPath;
      }
    }
    Token identifier = allowDollarDigits ? expect(IDENTIFIER, DOLLAR_IDENTIFIER) : expect(IDENTIFIER);
    if (identifier.is(DOLLAR_IDENTIFIER) && !Utils.isDigits(identifier.getStringValue().substring(1))) {
      error("Unexpected token '$': Identifiers cannot begin with '$'", previous());
    }
    return new Expr.Identifier(identifier);
  }

  private Expr.Identifier identifier(TokenType... tokenTypes) {
    Token token = expect(tokenTypes);
    return new Expr.Identifier(token);
  }

  /**
   * <pre>
   *# classPath ::= IDENTIFIER DOT IDENTIFIER (DOT IDENTIFIER) *
   * </pre>
   * We look for a class path like: x.y.z.A
   * where x, y, and z are all in lowercase and A begins with an uppercase.
   * If x.y.z is a package name then return single Expr.ClassName with x.y.z.A as the value.
   * Otherwise return null and leave position unchanged.
   */
  private Expr.ClassPath classPath() {
    List<Token> path  = new ArrayList<>();
    Token previous = previous();
    Token current  = peek();
    while (matchAny(IDENTIFIER)) {
      Token       token = previous();
      if (Utils.isLowerCase(token.getStringValue())) {
        path.add(token);
      }
      else {
        if (Utils.isValidClassName(token.getStringValue())) {
          if (path.size() > 0) {
            // We have a possible class path so check for package name
            String pkg = String.join(".", path.stream().map(p -> p.getStringValue()).collect(Collectors.toList()));
            if (pkg.equals(packageName) || context.packageExists(pkg)) {
              return new Expr.ClassPath(path.get(0).newIdent(pkg), token);
            }
          }
        }
        // not a classpath since class name does not start with upper case or we had no elements in package path
        tokeniser.rewind(previous, current);
        return null;
      }
      if (!matchAny(DOT)) {
        tokeniser.rewind(previous, current);
        return null;
      }
    }
    tokeniser.rewind(previous, current);
    return null;
  }

  /**
   * <pre>
   *# className ::= classPathOrIdentifier ( DOT IDENTIFIER ) *
   * </pre>
   */
  private List<Expr> className() {
    List<Expr> className = new ArrayList<>();

    // Check for lowercase name to check if we have a package name in which case we look for a classpath
    Token next = peek();
    if (next.is(IDENTIFIER) && Utils.isLowerCase(next.getStringValue())) {
      Expr classPath = classPath();
      if (classPath == null) {
        unexpected("Expected valid class name");
      }
      className.add(classPath);
    }

    if (className.size() == 0) {
      Token identifier = expect(IDENTIFIER);
      if (!Utils.isValidClassName(identifier.getStringValue())) {
        unexpected("Expected valid class name");
      }
      className.add(new Expr.Identifier(identifier));
    }

    // We have already parsed package name if any so now match nested class names X.Y.Z
    while (matchAny(DOT)) {
      Token identifier = expect(IDENTIFIER);
      String name = identifier.getStringValue();
      if (!Utils.isValidClassName(name)) {
        unexpected("Expected valid class name");
      }
      className.add(new Expr.Identifier(identifier));
    };
    return className;
  }

  /**
   *# mapOrListLiteral ::= mapLiteral | listLiteral
   */
  private Expr mapOrListLiteral() {
    if (isMapLiteral()) {
      return mapLiteral();
    }
    return listLiteral();
  }

  /**
   * <pre>
   *# listLiteral ::= LEFT_SQUARE ( expression ( COMMA expression ) * ) ? RIGHT_SQUARE
   * </pre>
   */
  private Expr.ListLiteral listLiteral() {
    Token start = expect(LEFT_SQUARE);
    Expr.ListLiteral expr = new Expr.ListLiteral(start);
    while (!matchAnyIgnoreEOL(RIGHT_SQUARE)) {
      if (expr.exprs.size() > 0) {
        expect(COMMA);
      }
      expr.exprs.add(expression(true));
    }
    return expr;
  }

  private Expr mapLiteralOrClosure() {
    if (isMapLiteral()) {
      return mapLiteral();
    }
    expect(LEFT_BRACE);
    return closure();
  }

  /**
   * <pre>
   *# mapLiteral ::= LEFT_SQUARE COLON RIGHT_SQUARE
   *#              | LEFT_BRACE COLON RIGHT_BRACE
   *#              | LEFT_SQUARE mapEntries RIGHT_SQUARE
   *#              | LEFT_BRACE mapEntries RIGHT_BRACE
   * </pre>
   * Return a map literal. For maps we support Groovy map syntax using "[" and "]"
   * as well as JSON syntax using "{" and "}".
   */
  private Expr.MapLiteral mapLiteral() {
    expect(LEFT_SQUARE, LEFT_BRACE);
    TokenType endToken = previous().is(LEFT_BRACE) ? RIGHT_BRACE : RIGHT_SQUARE;
    Expr.MapLiteral mapLiteral = mapEntries(endToken);
    return mapLiteral;
  }

  /**
   *# mapEntries ::= mapKey COLON expression (COMMA mapKey COLON expression) *
   */
  private Expr.MapLiteral mapEntries(TokenType endToken) {
    boolean          isNamedArgs   = endToken.is(RIGHT_PAREN);
    String           paramOrKey    = isNamedArgs ? "Parameter" : "Map key";
    Expr.MapLiteral  expr          = new Expr.MapLiteral(previous());
    Map<String,Expr> literalKeyMap = new HashMap<>();
    if (matchAnyIgnoreEOL(COLON)) {
      // Empty map
      expect(endToken);
    }
    else {
      while (!matchAnyIgnoreEOL(endToken)) {
        if (expr.entries.size() > 0) {
          if (!matchAny(COMMA)) {
            if (endToken.is(RIGHT_BRACE) && expr.entries.size() == 1) {
              unexpected("Label applied to statement that is not for/while/until loop or malformed Map literal using '{}' form.");
            }
            unexpected("Was Expecting ',' while parsing Map literal.");
          }
        }
        Expr key         = mapKey();
        String keyString = null;
        if (key instanceof Expr.Literal) {
          Expr.Literal literal = (Expr.Literal) key;
          Object       value   = literal.value.getValue();
          if (!(value instanceof String)) {
            error(paramOrKey + " must be String not " + RuntimeUtils.className(value), key.location);
          }
          keyString = literal.value.getStringValue();
          if (literalKeyMap.containsKey(keyString)) {
            error(paramOrKey + " '" + keyString + "' occurs multiple times", key.location);
          }
        }
        else {
          if (isNamedArgs) {
            error("Invalid parameter name", previous());
          }
        }
        expect(COLON);
        Expr value = expression(true);
        expr.entries.add(new Pair(key, value));
        if (keyString != null) {
          literalKeyMap.put(keyString, value);
        }
      }
    }
    expr.isNamedArgs = isNamedArgs;
    // If we have had keys that are only string literals then we create a map based on these string keys.
    // This can be used during named arg validation.
    if (literalKeyMap.size() == expr.entries.size()) {
      expr.literalKeyMap = literalKeyMap;
    }
    return expr;
  }

  /**
   * <pre>
   *# mapKey ::= STRING_CONST | IDENTIFIER | LEFT_PAREN expression RIGHT_PAREN | exprString | keyWord
   * </pre>
   */
  private Expr mapKey() {
    matchAny(EOL);
    Token token = peek();
    switch (token.getType()) {
      case STRING_CONST: case IDENTIFIER:    return new Expr.Literal(advance());
      case EXPR_STRING_START:                return exprString();
      case LEFT_PAREN:  {
        advance();
        Expr expr = expression(true);
        expect(RIGHT_PAREN);
        return expr;
      }
    }

    if (peek().isKeyword()) {
      advance();
      return new Expr.Literal(previous().newLiteral(previous().getChars()));
    }

    return null;
  }

  /**
   * <pre>
   *# exprString ::= EXPR_STRING_START ( DOLLAR_IDENTIFIER | DOLLAR_BRACE blockExpr |
   *#                       STRING_CONST ) * EXPR_STRING_END
   *#              | (SLASH|SLASH_EQUAL) ( IDENTIFIER | DOLLAR_BRACE blockExpr | STRING_CONST ) * SLASH
   * </pre>
   * We parse an expression string delimited by " or """ or /
   * For the / version we treat as a multi-line regular expression and don't support escape chars.
   */
  private Expr exprString() {
    Token startToken = peek();
    Expr exprString = parseExprString();
    // If regex then we don't know yet whether we are part of a "x =~ /regex/" or just a /regex/
    // on our own somewhere. We build "it =~ /regex" and if we are actually part of "x =~ /regex/"
    // our parent (parseExpression()) will convert back to "x =~ /regex/".
    // If we detect at resolve time that the standalone /regex/ has no modifiers and should just
    // be a regular expression string then we will fix the problem then.
    if (startToken.is(SLASH,SLASH_EQUAL)) {
      return new Expr.RegexMatch(new Expr.Identifier(startToken.newIdent(Utils.IT_VAR)),
                                 new Token(EQUAL_GRAVE, startToken),
                                 exprString,
                                 previous().getStringValue(),   // modifiers
                                 true);
    }
    return exprString;
  }

  private Expr.ExprString parseExprString() {
    Token startToken = expect(EXPR_STRING_START,SLASH,SLASH_EQUAL);
    Expr.ExprString exprString = new Expr.ExprString(startToken);
    if (startToken.is(EXPR_STRING_START)) {
      if (!startToken.getStringValue().isEmpty()) {
        // Turn the EXPR_STRING_START into a string literal and make it the first in our expr list
        exprString.exprList.add(new Expr.Literal(new Token(STRING_CONST, startToken)));
      }
    }
    else {
      // We either have / or /= so if /= then add a STRING_CONST of '=' to start of our expression string
      if (startToken.is(SLASH_EQUAL)) {
        exprString.exprList.add(new Expr.Literal(new Token(STRING_CONST, startToken).setValue("=")));
      }
      // Tell tokeniser that the SLASH was actually the start of a regex expression string
      tokeniser.startRegex();
    }
    doParseExprString(exprString, EXPR_STRING_END);
    return exprString;
  }

  private void doParseExprString(Expr.ExprString exprString, TokenType endToken) {
    while (!matchAny(endToken)) {
      if (matchAny(STRING_CONST) && !previous().getStringValue().isEmpty()) {
        exprString.exprList.add(new Expr.Literal(previous()));
      }
      else
      if (matchAny(IDENTIFIER,DOLLAR_IDENTIFIER)) {
        exprString.exprList.add(new Expr.Identifier(previous()));
      }
      else
      if (matchAny(LEFT_BRACE)) {
        exprString.exprList.add(exprStringBlockExpr());
      }
      else {
        unexpected("Error in expression string");
      }
    }
  }

  /**
   * <pre>
   *# regexSubstitute ::= REGEX_SUBST_START ( DOLLAR_IDENTIFIER | DOLLAR_BRACE blockExpr RIGHT_BRACE |
   *#                           STRING_CONST ) *
   *#                       REGEX_REPLACE ( DOLLAR_IDENTIFIER | DOLLAR_BRACE blockExpr RIGHT_BRACE |
   *#                           STRING_CONST ) * EXPR_STRING_END
   * </pre>
   */
  private Expr regexSubstitute() {
    Token start = expect(REGEX_SUBST_START);
    Expr.ExprString pattern = new Expr.ExprString(previous());
    doParseExprString(pattern, REGEX_REPLACE);
    Expr.ExprString replace = new Expr.ExprString(previous());
    doParseExprString(replace, EXPR_STRING_END);

    boolean isComplexReplacement = false;

    // If the replacement string has embedded expressions that need evaluating every time then we
    // can't use the standard Java replaceAll mechanism. We check for any embedded expression that
    // is not just a reference to an identifier. We also check for any references to capture vars
    // greater than $9 in the replacement string since Java does not support them natively.
    // Check that we have a simple replacement string:
    if (replace.exprList.stream().allMatch(this::isSimpleIdentOrLiteral)) {
      // Replace all capture vars with a String literal so that we leave them unexpanded and let
      // the Java replaceAll deal with them
      for (int i = 0; i < replace.exprList.size(); i++) {
        Expr expr = replace.exprList.get(i);
        if (expr instanceof Expr.Identifier) {
          Token identifier = ((Expr.Identifier)expr).identifier;
          if (identifier.getStringValue().charAt(0) == '$') {
            // Replace with a Literal
            replace.exprList.set(i, new Expr.Literal(new Token(STRING_CONST, identifier)
                                                       .setValue(identifier.getStringValue())));
          }
        }
      }
    }
    else {
      // We have a more complex replacement string
      isComplexReplacement = true;
    }

    final Expr.Identifier itVar    = new Expr.Identifier(start.newIdent(Utils.IT_VAR));
    final Token           operator = new Token(EQUAL_GRAVE, start);
    final String modifiers = previous().getStringValue();
    final Expr.RegexSubst regexSubst = new Expr.RegexSubst(itVar, operator, pattern, modifiers, true, replace, isComplexReplacement);
    // Modifier 'r' forces the substitute to be a value and not override the original variable
    if (modifiers.indexOf(Utils.REGEX_NON_DESTRUCTIVE) != -1) {
      return regexSubst;
    }
    else {
      // Otherwise assign the result back to the original variable/field
      return convertToLValue(itVar,
                             operator,
                             regexSubst,
                             false,
                             false);
    }
  }

  /**
   * Used inside expression strings. If no return statement there is an implicit
   * return on last statement in block that gives the value to then interpolate
   * into surrounding expression string.
   * If the block contains multiple statements we turn it into the equivalent of
   * an anonymous closure invocation. E.g.:
   *   "${stmt1; stmt2; return val}" -&gt; "${ {stmt1;stmt2;return val}() }"
   */
  private Expr exprStringBlockExpr() {
    Token leftBrace = previous();
    Stmt.Block block = block(RIGHT_BRACE);
    if (block.stmts.stmts.size() == 0) {
      return new Expr.Literal(new Token(NULL, leftBrace));
    }
    // If we have a single ExprStmt then our expression is just the expression inside the statement
    if (block.stmts.stmts.size() == 1) {
      Stmt stmt = block.stmts.stmts.get(0);
      // If we have a single "simple" expression (no return, continue, break, and no regex match/substitutes),
      // then we don't have to bother with a separate closure for the block.
      // If there are regex matches or substitutes we create a closure since they might need their own RegexMatcher.
      if (stmt instanceof Stmt.ExprStmt) {
        Expr expr = ((Stmt.ExprStmt) stmt).expr;
        if (isSimple(expr)) {
          expr.isResultUsed = true;
          return expr;
        }
      }
    }

    // We have more than one statement or statement is more than just a simple expression so convert
    // block into a parameter-less closure and then invoke it.
    Stmt.FunDecl closureFunDecl = convertBlockToClosure(leftBrace, block);
    closureFunDecl.declExpr.isResultUsed = true;
    Expr closure = new Expr.Closure(leftBrace, closureFunDecl.declExpr, true);
    return createCallExpr(closure, leftBrace, Utils.listOf());
  }

  /**
   * <pre>
   *# closure ::= LEFT_BRACE (parameters ARROW ) ? block
   * </pre>
   */
  private Expr closure() {
    Token openBrace = previous();
    matchAny(EOL);
    // We need to do a lookahead to see if we have a parameter list or not
    List<Stmt.VarDecl> parameters;
    boolean noParamsDefined = false;
    if (lookahead(() -> parameters(ARROW) != null)) {
      parameters = parameters(ARROW);
    }
    else {
      // No parameter and no "->" so fill in with default "it" parameter
      noParamsDefined = true;
      parameters = Utils.listOf(createItParam(openBrace));
    }
    Stmt.FunDecl funDecl = parseFunDecl(openBrace, null, ANY, parameters, RIGHT_BRACE, false, false, true);
    funDecl.declExpr.isResultUsed = true;
    return new Expr.Closure(openBrace, funDecl.declExpr, noParamsDefined);
  }

  /**
   * <pre>
   *# returnExpr ::= RETURN expression
   * </pre>
   */
  private Expr.Return returnExpr() {
    Token location = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(NULL, location).setValue(null))
                                    : parseExpression();
    return new Expr.Return(location, expr, null);   // returnType will be set by Resolver
  }

  /**
   * <pre>
   *# printExpr ::= (PRINT | PRINTLN) expr ?
   * </pre>
   */
  private Expr.Print printExpr() {
    Token printToken = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(STRING_CONST, printToken).setValue(""))
                                    : parseExpression();
    return new Expr.Print(printToken, expr, printToken.is(PRINTLN));
  }

  /**
   * <pre>
   *# dieExpr ::= DIE expr ?
   * </pre>
   */
  private Expr.Die dieExpr() {
    Token dieToken = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(STRING_CONST, dieToken).setValue(""))
                                    : parseExpression();
    return new Expr.Die(dieToken, expr);
  }

  /**
   * <pre>
   *# evalExpr ::= EVAL LEFT_PAREN expr ( COMMA expr )? RIGHT_PAREN
   * </pre>
   */
  private Expr.Eval evalExpr() {
    Token evalToken = expect(EVAL);
    expect(LEFT_PAREN);
    Expr script = parseExpression();
    Expr globals = null;
    if (matchAny(COMMA)) {
      globals = parseExpression();
    }
    expect(RIGHT_PAREN);
    return new Expr.Eval(evalToken, script, globals);
  }

  /**
   *# switchExpr ::= SWITCH ( LEFT_PAREN expr RIGHT_PAREN ) ? LEFT_BRACE (switchPatterns|DEFAULT) ARROW expression (SEMICOLON? (switchPatterns|DEFAULT) ARROW expression)* RIGHT_BRACE
   */
  Expr.Switch switchExpr() {
    Token matchToken = expect(SWITCH);
    Expr subject = null;
    if (matchAny(LEFT_PAREN)) {
      subject = expression(true);
      expect(RIGHT_PAREN);
    }
    else {
      subject = new Expr.Identifier(matchToken.newIdent(Utils.IT_VAR));
    }
    expect(LEFT_BRACE);
    Expr                  defaultCase = null;
    List<Expr.SwitchCase> cases       = new ArrayList<>();
    while (!matchAny(RIGHT_BRACE)) {
      List<Pair<Expr,Expr>> patterns = null;
      if (matchAny(DEFAULT)) {
        if (defaultCase != null) {
          unexpected("cannot have multiple 'default' cases in switch expression");
        }
      }
      else {
        patterns = switchPatterns();
      }
      expect(ARROW);
      Expr expr = expression();
      if (expr instanceof Expr.Closure) {
        expr = convertClosureToBlockExpr(expr);
      }
      expr.isResultUsed = true;
      if (patterns == null) {
        defaultCase = expr;
      }
      else {
        Expr.SwitchCase switchCase = new Expr.SwitchCase(patterns, expr);
        switchCase.switchSubject = subject;
        cases.add(switchCase);
      }
      matchAny(SEMICOLON, EOL);
    }

    // Make sure that patterns that are literals don't occur multiple times. Build a map that we won't use
    // and use merge function to detect if key occurs multiple times.
    cases.stream()
         .flatMap(c -> c.patterns.stream().map(p -> p.first))
         .filter(expr -> expr.isConst)
         .collect(Collectors.toMap(expr -> expr.constValue,
                                   expr -> expr,
                                   (expr1, expr2) -> error("Literal match occurs multiple times in switch: " + expr2.constValue, expr2.location)));
    return new Expr.Switch(matchToken, subject, cases, defaultCase);
  }

  private Expr convertClosureToBlockExpr(Expr expr) {
    Expr.Closure closure = (Expr.Closure) expr;
    if (closure.noParamsDefined) {
      // Treat closure as code block since no parameters defined
      Stmt.Block block = closure.funDecl.block;
      removeItParameter(block);
      block = (Stmt.Block)setLastResultIsUsed(block);
      expr = new Expr.Block(block.openBrace, block);
    }
    return expr;
  }

  /**
   *# blockExpr ::= block
   */
  private Expr blockExpr() {
    return convertClosureToBlockExpr(closure());
  }

  /**
   *# switchPatterns ::= switchPatternAndExpr ( COMMA switchPatternAndExpr ) *
   */
  private List<Pair<Expr,Expr>> switchPatterns() {
    List<Pair<Expr,Expr>> patterns = new ArrayList<>();
    do {
      patterns.add(switchPatternAndExpr());
    } while (matchAny(COMMA));
    return patterns;
  }

  /**
   *# switchPatternAndExpr ::= switchPattern ( IF expression ) ?
   */
  private Pair<Expr,Expr> switchPatternAndExpr() {
    Expr switchPattern = switchPattern();
    Expr expr = null;
    if (matchAny(IF)) {
      expr = parseExpression();
      expr.isResultUsed = true;
    }
    return Pair.create(switchPattern, expr);
  }

  /**
   *# switchPattern ::= literal
   *#                 | type
   *#                 | className ( LEFT_PAREN mapOrListPattern RIGHT_PAREN ) ?
   *#                 | exprString
   *#                 | UNDERSCORE
   *#                 | IDENTIFIER
   *#                 | listPattern
   *#                 | mapPattern
   *#                 | DOLLAR_IDENTIFIER
   *#                 | DOLLAR_BRACE blockExpr RIGHT_BRACE
   */
  private Expr switchPattern() {
    matchAny(EOL);
    Expr expr = null;
    if (isLiteral()) {
      expr = literal();
      expr.isConst = true;
      expr.constValue = literalValue(expr);
    }
    else if (peek().is(LEFT_SQUARE)) {
      expr = mapOrListPattern(LEFT_SQUARE, RIGHT_SQUARE, true);
      if (isConstListOrMap(expr)) {
        expr.isConst = true;
        expr.constValue = literalValue(expr);
        addClassConstant(expr);
      }
    }
    else if (peek().is(SLASH)) {
      Expr.RegexMatch regex = (Expr.RegexMatch)exprString();
      expr = regex;
      if (regex.modifiers.isEmpty()) {
        // Not an actual regex, just a slashy string
        expr = regex.pattern;
      }
    }
    else if (isType()) {
      // Note that we can't actually tell the difference between X.Y.Z being a class name and X.Y.Z being
      // a field Z inside the class X.Y. We build a TypeExpr anyway and let the Resolver take care of it.
      expr = typeOrVarDecl();
      // Check for class name and constructor args (named or not)
      if (expr instanceof Expr.TypeExpr && ((Expr.TypeExpr) expr).typeVal.is(JactlType.INSTANCE)) {
        matchAny(EOL);
        if (peek().is(LEFT_PAREN)) {
          expr = new Expr.ConstructorPattern(((Expr.TypeExpr) expr).token, (Expr.TypeExpr) expr, mapOrListPattern(LEFT_PAREN, RIGHT_PAREN, false));
        }
      }
    }
    else if (matchAny(IDENTIFIER)){
      // If there is just a single identifier we return it. Otherwise, we look for a dotted list of identifiers
      // which would be a static field in a class and tunnel this list of identifiers in a TypeExpr as though
      // it were actually a type.
      // This is because if we had X.Y.Z we would detect it as a type and build a TypeExpr (see previous if)
      // so we also build a TypeExpr if we have X.Y.z
      // The Resolver will take care of checking whether X.Y.Z is an actual field reference or a class name
      // and will also, therefore, cope with X.Y.z
      List<Expr> exprs = new ArrayList<>();
      exprs.add(new Expr.Identifier(previous()));
      while (matchAnyIgnoreEOL(DOT)) {
        expect(IDENTIFIER);
        exprs.add(new Expr.Identifier(previous()));
      }
      expr = exprs.size() == 1 ? exprs.get(0) : new Expr.TypeExpr(exprs.get(0).location, JactlType.createInstanceType(exprs));
    }
    else if (peek().is(UNDERSCORE)) {
      expr = identifier(UNDERSCORE);
    }
    else if (matchAny(DOLLAR_IDENTIFIER)) {
      expr = new Expr.Identifier(previous());
    }
    else if (matchAny(DOLLAR_BRACE)) {
      expr = blockExpr();
    }
    else if (isCast()) {
      expect(LEFT_PAREN);
      matchAny(EOL);
      JactlType castType = type(false, false, false);
      matchAny(EOL);
      expect(RIGHT_PAREN);
      if (!isLiteral()) {
        error("Expected simple literal value after type cast");
      }
      Expr.Literal literal = literal();
      literal.isConst = true;
      literal.constValue = castTo(literal.value.getValue(),castType, literal.location);
      literal.value = new Token(JactlType.typeOf(literal.constValue).constTokenType(literal.constValue),literal.location).setValue(literal.constValue);
      expr = literal;
    }
    else {
      unexpected("Expect const or regex or type in match case");
    }
    return expr;
  }

  private Object castTo(Object value, JactlType type, Token token) {
    try {
      return RuntimeUtils.castTo(type.classFromType(), value, token.getSource(), token.getOffset());
    }
    catch (JactlError e) {
      error(e.getErrorMessage(), token);
    }
    return null;
  }

  /**
   *# mapOrListPattern ::= mapPattern | listPattern
   */
  private Expr mapOrListPattern(TokenType startToken, TokenType endToken, boolean starAllowed) {
    if (isMapPattern(startToken, starAllowed)) {
      return mapPattern(startToken, endToken, starAllowed);
    }
    return listPattern(startToken, endToken, starAllowed);
  }

  private boolean isMapPattern(TokenType startToken, boolean starAllowed) {
    // Check for start of a Map literal. We need to lookahead to know the difference between
    // a Map literal using '{' and '}' and a statement block or a closure.
    return lookahead(() -> {
      if (!matchAnyIgnoreEOL(startToken)) { return false; }
      // Allow '*' as first elem in list of key,value pair if doing pattern match in switch expression
      if (starAllowed && matchAnyIgnoreEOL(STAR)) {
        return matchAnyIgnoreEOL(COMMA) && patternMapKey(starAllowed) != null && matchAnyIgnoreEOL(COLON);
      }
      return matchAnyIgnoreEOL(COLON) ||
             patternMapKey(starAllowed) != null &&
             matchAnyIgnoreEOL(COLON);
    });
  }

  /**
   * <pre>
   *# listPattern ::= LEFT_SQUARE ( switchPattern ( COMMA switchPattern ) * ) ? RIGHT_SQUARE
   * </pre>
   */
  private Expr.ListLiteral listPattern(TokenType startToken, TokenType endToken, boolean starAllowed) {
    Token start = expect(startToken);
    Expr.ListLiteral expr = new Expr.ListLiteral(start);
    while (!matchAnyIgnoreEOL(endToken)) {
      if (expr.exprs.size() > 0) {
        expect(COMMA);
      }
      matchAny(EOL);
      expr.exprs.add(starAllowed && peek().is(STAR) ? new Expr.Identifier(expect(STAR))
                                                    : switchPattern());
    }
    return expr;
  }

  /**
   * <pre>
   *# mapPattern ::= LEFT_SQUARE COLON RIGHT_SQUARE
   *#              | LEFT_SQUARE ( patternMapKey COLON switchPattern ) ( COMMA patternMapKey COLON switchPattern ) * RIGHT_SQUARE
   * </pre>
   */
  private Expr.MapLiteral mapPattern(TokenType startToken, TokenType endToken, boolean starAllowed) {
    expect(startToken);
    Expr.MapLiteral mapLiteral = patternMapEntries(endToken, starAllowed);
    return mapLiteral;
  }

  private boolean isExprString() {
    return peek().is(DOUBLE_QUOTE,SLASH,SLASH_EQUAL);
  }

  /**
   *# patternMapKey ::= STRING_CONST | exprString | IDENTIFIER | STAR
   */
  private Expr patternMapKey(boolean starAllowed) {
    if (isExprString()) {
      return exprString();
    }
    return new Expr.Literal(starAllowed ? expect(STRING_CONST, IDENTIFIER, STAR)
                                        : expect(STRING_CONST, IDENTIFIER));
  }

  private Expr.MapLiteral patternMapEntries(TokenType endToken, boolean starAllowed) {
    Expr.MapLiteral  expr          = new Expr.MapLiteral(previous());
    Map<String,Expr> literalKeyMap = new HashMap<>();
    if (matchAnyIgnoreEOL(COLON)) {
      // Empty map
      expect(RIGHT_SQUARE);
    }
    else {
      while (!matchAnyIgnoreEOL(endToken)) {
        if (expr.entries.size() > 0) {
          expect(COMMA);
        }
        Expr   key       = patternMapKey(starAllowed);
        String keyString = key instanceof Expr.Literal ? ((Expr.Literal)key).value.getStringValue() : null;
        if (keyString != null && literalKeyMap.containsKey(keyString)) {
          error("Key '" + keyString + "' occurs multiple times", key.location);
        }
        Expr value = null;
        if (!keyString.equals(STAR.asString)) {
          expect(COLON);
          value = switchPattern();
        }
        expr.entries.add(new Pair(key, value));
        if (keyString != null) {
          literalKeyMap.put(keyString, value);
        }
      }
    }
    expr.literalKeyMap = literalKeyMap;
    return expr;
  }

  private Expr typeOrVarDecl() {
    Expr  expr;
    Token token = peek();
    // We either have a type on its own or a type and an identifier for a binding variable
    JactlType type = type(false, false, false);
    if (matchAny(IDENTIFIER)) {
      expr = Utils.createVarDeclExpr(previous(), type, new Token(EQUAL, previous()), null, false, false, true);
    }
    else {
      // Is a type
      expr = new Expr.TypeExpr(token, type);
    }
    return expr;
  }

  private boolean isType() {
    return lookahead(() -> type(false, false, false) != null);
  }

  /////////////////////////////////////////////////

  private Object ignoreNewLines(Supplier<Object> code) {
    boolean currentIgnoreEol = ignoreEol;
    ignoreEol = true;
    try {
      return code.get();
    }
    finally {
      ignoreEol = currentIgnoreEol;
    }
  }

  /**
   * Operator match. Returns true if next token matches any in list.
   * If there is an EOL as first token then we ignore unless operator is one that could
   * start an expression and we are not already in a nested expression of some sort.
   */
  boolean matchOp(List<TokenType> types) {
    if (!peekIsEOL()) {
      return matchAny(types);
    }
    for (TokenType type: types) {
      if (!ignoreEol && exprStartingOps.contains(type) ? matchAnyNoEOL(type) : matchAnyIgnoreEOL(type)) {
        return true;
      }
    }
    return false;
  }

  private boolean isImplicitRegexSubstitute(Expr rhs) {
    if (rhs instanceof Expr.VarOpAssign) {
      Expr expr = ((Expr.VarOpAssign) rhs).expr;
      return expr instanceof Expr.RegexSubst && ((Expr.RegexSubst) expr).implicitItMatch;
    }
    return false;
  }

  private boolean isEndOfExpression() {
    return peekIsEOL() || peek().is(EOF,AND,OR,RIGHT_BRACE,RIGHT_PAREN,RIGHT_SQUARE,SEMICOLON,IF,UNLESS);
  }

  /**
   * Remove the it parameter declaration from a code block.
   * This is used when converting a closure back into a simple block once we determine that the
   * block is really just a block and not a potential closure declaration.
   * @param block  the code block
   */
  private void removeItParameter(Stmt.Block block) {
    // Remove the default parameter declaration for "it" from the statements in the block
    Stmt itParameter = block.stmts.stmts.remove(0);
    if (!(itParameter instanceof Stmt.VarDecl &&
          ((Stmt.VarDecl) itParameter).declExpr.name.getStringValue().equals(Utils.IT_VAR))) {
      throw new IllegalStateException("Internal error: expecting parameter declaration for 'it' but got " + itParameter);
    }
  }

  /**
   * Check that expression is a simple literal or an identifier.
   * If identifier is a capture var then return true only if <= $9
   */
  private boolean isSimpleIdentOrLiteral(Expr expr) {
    if (expr instanceof Expr.Literal)    { return true; }
    if (expr instanceof Expr.Identifier) {
      Expr.Identifier ident = (Expr.Identifier)expr;
      final String    name  = ident.identifier.getStringValue();
      if (name.charAt(0) == '$') {
        // Check if capture arg is greater than $9
        return name.length() <= 2;
      }
      return true;
    }
    return false;
  }

  private Stmt.VarDecl createItParam(Token token) {
    return Utils.createParam(token.newIdent(Utils.IT_VAR), ANY, new Expr.Literal(new Token(NULL, token)), true, false);
  }

  // Create parameter for class instance initialiser method
  private Stmt.VarDecl createInitParam(Expr.VarDecl varDecl) {
    Stmt.VarDecl paramDecl = Utils.createParam(varDecl.name, varDecl.type, varDecl.initialiser, true, false);
    paramDecl.declExpr.paramVarDecl = varDecl;
    varDecl.initialiser = null;
    return paramDecl;
  }

  private void pushClass(Stmt.ClassDecl classDecl) {
    if (lookaheadCount == 0) { classes.push(classDecl); }
  }

  private void popClass() {
    if (lookaheadCount == 0) { classes.pop(); }
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

  private void pushBlock(Stmt.Block block) {
    if (lookaheadCount == 0 && functionStack().size() > 0) {
      blockStack().push(block);
    }
  }

  private void popBlock() {
    if (lookaheadCount == 0 && functionStack().size() > 0) {
      blockStack().pop();
    }
  }

  private void addFunDecl(Stmt.FunDecl funDecl) {
    if (lookaheadCount == 0) {
      if (functionStack().size() == 0) {
        classes.peek().methods.add(funDecl);
      }
      else {
        blockStack().peek().functions.add(funDecl);
      }
    }
  }

  // Add a constant map/list that we will construct only once in class constructor
  private void addClassConstant(Expr expr) {
    assert expr.isConst;
    if (expr instanceof Expr.ListLiteral || expr instanceof Expr.MapLiteral) {
      classes.peek().classConstants.add(expr.constValue);
    }
  }

  /**
   * Class declaration is only allowed in top level block of a script or another class declaration.
   */
  private boolean isClassDeclAllowed() {
    // Function will either be the top level script or the init method of our enclosing class
    return classes.size() == 0 ||
           functionStack().size() == 0 ||                               // class block
           isAtScriptTopLevel();
  }

  private boolean isAtScriptTopLevel() {
    return functionStack().size() == 1 && functionStack().peek().isScriptMain && blockStack().size() == 1;
  }

  private Stmt.FunDecl parseFunDecl(Token start,
                                    Token name,
                                    JactlType returnType,
                                    List<Stmt.VarDecl> params,
                                    TokenType endToken,
                                    boolean isScriptMain,
                                    boolean isStatic, boolean isFinal) {
    params.forEach(p -> p.declExpr.isExplicitParam = true);
    Expr.FunDecl funDecl = Utils.createFunDecl(start, name, returnType, params, isStatic, false, isFinal);
    Stmt.FunDecl funDeclStmt = new Stmt.FunDecl(start, funDecl);
    if (!isScriptMain && !funDecl.isClosure()) {
      // Add to functions in block, so we can support forward references during Resolver phase
      // or add to Class if we are in a class declaration
      addFunDecl(funDeclStmt);
    }
    funDecl.isScriptMain = isScriptMain;
    pushFunDecl(funDecl);
    try {
      Stmt.Block block = block(peek(), endToken);
      insertStmtsInto(params, block);
      funDecl.block = block;
      return funDeclStmt;
    }
    finally {
      popFunDecl();
    }
  }

  /**
   * Convert a stmt block into an anonymous closure with no parameters.
   * This is used in expression strings when the embedded expression is more than a simple expression.
   */
  private Stmt.FunDecl convertBlockToClosure(Token start, Stmt.Block block) {
    return convertBlockToFunction(start, block, ANY, Utils.listOf(), false);
  }

  private Stmt.FunDecl convertBlockToFunction(Token name, Stmt.Block block, JactlType returnType, List<Stmt.VarDecl> params, boolean isStatic) {
    Expr.FunDecl funDecl = Utils.createFunDecl(name, null, returnType, params, isStatic, false, false);
    insertStmtsInto(params, block);
    funDecl.block = block;
    return new Stmt.FunDecl(name, funDecl);
  }

  private boolean isMapLiteral() {
    // Check for start of a Map literal. We need to lookahead to know the difference between
    // a Map literal using '{' and '}' and a statement block or a closure.
    return lookahead(() -> {
      if (!matchAnyIgnoreEOL(LEFT_SQUARE, LEFT_BRACE)) { return false; }
      TokenType close = previous().is(LEFT_SQUARE) ? RIGHT_SQUARE : RIGHT_BRACE;
      return matchAnyIgnoreEOL(COLON) ||
             mapKey() != null &&
             matchAnyIgnoreEOL(COLON) &&
             // Make sure we are not doing a label for while/for immediately after '{'
             !(close == RIGHT_BRACE && matchAnyIgnoreEOL(WHILE,FOR));
    });
  }

  /////////////////////////////////////////////////

  private Token advance() {
    Token token = tokeniser.next();
    return token;
  }

  private Token peek() {
    Token token = tokeniser.peek();
    if (ignoreEol && token.is(EOL)) {
      // If ignoring EOL then we need to get following token but not consume existing
      // one so save state and then restore after fetching following token
      Token current = token;
      Token prev = previous();
      advance();
      token = tokeniser.peek();
      tokeniser.rewind(prev, current);
    }
    return token;
  }

  private Token peekNoEOL() {
    return tokeniser.peek();
  }

  private boolean peekIsEOL() {
    return tokeniser.peek().is(EOL);
  }

  private Token previous() {
    return tokeniser.previous();
  }

  private boolean previousWas(TokenType... types) {
    Token token = previous();
    return token != null && token.is(types);
  }

  /**
   * Check if next token matches any of the given types. If it matches then consume the token and return true.
   * If it does not match one of the types then return false and stay in current position in stream of tokens.
   *
   * @param types the types to match agains
   * @return true if next token matches, false is not
   */
  private boolean matchAny(TokenType... types) {
    if (ignoreEol) {
      return matchAnyIgnoreEOL(types);
    }

    for (TokenType type : types) {
      if (peek().is(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean matchAny(List<TokenType> types) {
    if (ignoreEol) {
      return matchAnyIgnoreEOL(types);
    }
    return matchAnyNoEOL(types);
  }

  /**
   * Match any type after optionally consuming EOL. If not match then state is
   * unchanged (EOL is not consumed).
   */
  private boolean matchAnyIgnoreEOL(List<TokenType> types) {
    return matchAnyIgnoreEOL(types.toArray(new TokenType[types.size()]));
  }

  private boolean matchAnyNoEOL(TokenType... types) {
    return matchAnyNoEOL(Arrays.asList(types));
  }

  private boolean matchAnyNoEOL(List<TokenType> types) {
    for (TokenType type : types) {
      if (peekNoEOL().is(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  /**
   * Match any type after optionally consuming EOL. If not match then state is
   * unchanged (including not consuming EOL)
   */
  private boolean matchAnyIgnoreEOL(TokenType... types) {
    // Remember current tokens in case we need to rewind
    Token previous = previous();
    Token current  = tokeniser.peek();

    boolean eolConsumed = false;
    if (current.is(EOL)) {
      advance();
      eolConsumed = true;
    }

    for (TokenType type : types) {
      if (type.is(EOL) && eolConsumed) {
        return true;    // we have already advanced
      }
      if (peek().is(type)) {
        advance();
        return true;
      }
    }

    // No match so rewind if necessary
    if (eolConsumed) {
      tokeniser.rewind(previous, current);
    }
    return false;
  }

  private boolean matchKeywordIgnoreEOL() {
    // Remember current tokens in case we need to rewind
    Token previous = previous();
    Token current  = tokeniser.peek();

    boolean eolConsumed = false;
    if (current.is(EOL)) {
      advance();
      eolConsumed = true;
    }

    if (peek().isKeyword()) {
      advance();
      return true;
    }

    // No match so rewind if necessary
    if (eolConsumed) {
      tokeniser.rewind(previous, current);
    }
    return false;
  }

  /**
   * Return true if next token is an illegal token
   */
  boolean matchError() {
    try {
      peek();
      return false;
    }
    catch (CompileError error) {
      return true;
    }
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
   *   if (lookahead(() -&gt; matchAny(LEFT_SQUARE),
   *                 () -&gt; mapKey() != null,
   *                 () -&gt; matchAny(COLON)) {
   *     ...
   *   }
   * @param lambdas  array of lambdas returning true/false
   */
  @SafeVarargs
  private final boolean lookahead(Supplier<Boolean>... lambdas) {
    // Remember current state
    Token previous                   = previous();
    Token current                    = tokeniser.peek();
    boolean currentIgnoreEol         = ignoreEol;
    List<CompileError> currentErrors = new ArrayList<>(errors);

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
      tokeniser.rewind(previous, current);
      errors        = currentErrors;
      lookaheadCount--;
      ignoreEol = currentIgnoreEol;
    }
  }

  /**
   * Lookahead to check if next tokens match given types in order.
   * @types  the sequence of types to match against
   */
  private boolean lookaheadNoEOL(TokenType... types) {
    return lookahead(() -> {
      for (TokenType type: types) {
        if (!matchAnyNoEOL(type)) {
          return false;
        }
      }
      return true;
    });
  }

  /////////////////////////////////////

  private Expr error(String msg) {
    error(msg, peek());
    return null;
  }

  private Expr error(String msg, Token token) {
    // Don't bother with stack trace when doing lookahead
    throw new CompileError(msg, token, lookaheadCount == 0);
  }

  private Expr unexpected(String msg) {
    if (peek().getType().is(EOF)) {
      throw new EOFError("Unexpected EOF: " + msg, peek(), lookaheadCount == 0);
    }
    final String chars = peekIsEOL() ? "EOL" :
                         peek().is(EXPR_STRING_START) ? "'\"'" :
                         peek().is(STRING_CONST) ? "\"'\"" :
                         "'" + peek().getChars() + "'";
    error("Unexpected token " + chars + ": " + msg);
    return null;
  }

  /**
   * Expect one of the given types and throw an error if no match. Consume and return the token matched.
   *
   * @param types types to match against
   * @return the matched token or throw error if no match
   */
  private Token expect(TokenType... types) {
    if (matchAnyIgnoreEOL(types)) {
      return previous();
    }
    expectError(types);
    return null;
  }

  private void expectError(TokenType[] types) {
    if (types.length > 1) {
      unexpected("Expecting one of " +
                 Arrays.stream(types)
                       .map(Enum::toString)
                       .map(t -> "'" + t + "'")
                       .collect(Collectors.joining(", ")));
    }
    else {
      String expected = types[0].is(IDENTIFIER) ? "identifier" : "'" + types[0] + "'";
      unexpected("Expecting " + expected);
    }
  }

  private Token expect(List<TokenType> types) {
    return expect(types.toArray(new TokenType[types.size()]));
  }

  private Token peekExpect(TokenType ...types) {
    if (peek().is(types)) {
      return peek();
    }
    expectError(types);
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
   * We use this to determine whether an embedded expression in an expression string warrants
   * its own closure or not.
   * Return true if expression is a simple expression meaning no return/continue/break statements
   * and no regex matches or substitutes (since we could be in a replace string and further regex
   * matches would need their own RegexMatcher instance).
   */
  private static boolean isSimple(Expr expr) {
    return expr.accept(new Expr.Visitor<Boolean>() {
      boolean isSimple(Expr expr) { return expr.accept(this); }

      @Override public Boolean visitReturn(Expr.Return expr)         { return false; }
      @Override public Boolean visitBreak(Expr.Break expr)           { return false; }
      @Override public Boolean visitContinue(Expr.Continue expr)     { return false; }
      @Override public Boolean visitEval(Expr.Eval expr)             { return false; }
      @Override public Boolean visitBlock(Expr.Block expr)           { return false; }
      @Override public Boolean visitRegexMatch(Expr.RegexMatch expr) { return false; }
      @Override public Boolean visitRegexSubst(Expr.RegexSubst expr) { return false; }

      @Override public Boolean visitNoop(Expr.Noop expr)             { return true; }
      @Override public Boolean visitClosure(Expr.Closure expr)       { return true; }
      @Override public Boolean visitLiteral(Expr.Literal expr)       { return true; }
      @Override public Boolean visitIdentifier(Expr.Identifier expr) { return true; }
      @Override public Boolean visitClassPath(Expr.ClassPath expr)   { return true; }
      @Override public Boolean visitFunDecl(Expr.FunDecl expr)       { return true; }
      @Override public Boolean visitTypeExpr(Expr.TypeExpr expr)     { return true; }
      @Override public Boolean visitInstanceOf(Expr.InstanceOf expr) { return true; }

      @Override public Boolean visitExprList(Expr.ExprList expr)           { return expr.exprs.stream().allMatch(this::isSimple); }
      @Override public Boolean visitCall(Expr.Call expr)                   { return isSimple(expr.callee) && expr.args.stream().allMatch(this::isSimple); }
      @Override public Boolean visitMethodCall(Expr.MethodCall expr)       { return isSimple(expr.parent) && expr.args.stream().allMatch(this::isSimple); }
      @Override public Boolean visitBinary(Expr.Binary expr)               { return isSimple(expr.left) && isSimple(expr.right);  }
      @Override public Boolean visitTernary(Expr.Ternary expr)             { return isSimple(expr.first) && isSimple(expr.second) && isSimple(expr.third); }
      @Override public Boolean visitPrefixUnary(Expr.PrefixUnary expr)     { return isSimple(expr.expr); }
      @Override public Boolean visitPostfixUnary(Expr.PostfixUnary expr)   { return isSimple(expr.expr); }
      @Override public Boolean visitCast(Expr.Cast expr)                   { return isSimple(expr.expr); }
      @Override public Boolean visitListLiteral(Expr.ListLiteral expr)     { return expr.exprs.stream().allMatch(this::isSimple); }
      @Override public Boolean visitMapLiteral(Expr.MapLiteral expr)       { return expr.entries.stream().allMatch(e -> isSimple(e.first) && isSimple(e.second)); }
      @Override public Boolean visitExprString(Expr.ExprString expr)       { return expr.exprList.stream().allMatch(this::isSimple); }
      @Override public Boolean visitVarDecl(Expr.VarDecl expr)             { return isSimple(expr.initialiser); }
      @Override public Boolean visitVarAssign(Expr.VarAssign expr)         { return isSimple(expr.expr); }
      @Override public Boolean visitVarOpAssign(Expr.VarOpAssign expr)     { return isSimple(expr.expr); }
      @Override public Boolean visitFieldAssign(Expr.FieldAssign expr)     { return isSimple(expr.field) && isSimple(expr.parent) && isSimple(expr.expr); }
      @Override public Boolean visitFieldOpAssign(Expr.FieldOpAssign expr) { return isSimple(expr.field) && isSimple(expr.parent) && isSimple(expr.expr); }
      @Override public Boolean visitPrint(Expr.Print expr)                 { return isSimple(expr.expr); }
      @Override public Boolean visitDie(Expr.Die expr)                     { return isSimple(expr.expr); }

      @Override public Boolean visitSwitch(Expr.Switch expr) {
        return isSimple(expr.subject) && expr.cases.stream().allMatch(this::isSimple);
      }

      @Override public Boolean visitSwitchCase(Expr.SwitchCase expr) {
        return expr.patterns.stream().allMatch(pair -> isSimple(pair.first)) && isSimple(expr.result);
      }

      @Override public Boolean visitConstructorPattern(Expr.ConstructorPattern expr) { return false; }

      // These should never occur here since they are never created in Parser (only in Resolver)
      @Override public Boolean visitArrayLength(Expr.ArrayLength expr)       { return true; }
      @Override public Boolean visitArrayGet(Expr.ArrayGet expr)             { return true; }
      @Override public Boolean visitLoadParamValue(Expr.LoadParamValue expr) { return true; }
      @Override public Boolean visitInvokeNew(Expr.InvokeNew expr)           { return true; }
      @Override public Boolean visitDefaultValue(Expr.DefaultValue expr)     { return true; }
      @Override public Boolean visitCheckCast(Expr.CheckCast expr)           { return true; }
      @Override public Boolean visitInvokeFunDecl(Expr.InvokeFunDecl expr)   { return false; }
      @Override public Boolean visitInvokeInit(Expr.InvokeInit expr)         { return false; }
      @Override public Boolean visitInvokeUtility(Expr.InvokeUtility expr)   { return false; }
      @Override public Boolean visitConvertTo(Expr.ConvertTo expr)           { return false; }
      @Override public Boolean visitSpecialVar(Expr.SpecialVar expr)         { return false; }
      @Override public Boolean visitStackCast(Expr.StackCast expr)           { return false; }
    });
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
    // look up to do. (Note that if there is not a method of that name we then
    // we fallback to looking for a field.)
    callee.isCallee = true;

    // Turn x.a.b(args) into Expr.Call(x.a, b, args) so that we can check at compile
    // time if x.a.b is a method (if we know type of a.x) or not.
    if (callee instanceof Expr.Binary) {
      Expr.Binary binaryExpr = (Expr.Binary) callee;
      // Make sure we have a '.' or '.?' which means we might have a method call
      if (binaryExpr.operator.is(DOT, QUESTION_DOT)) {
        // Get potential method name if right-hand side of '.' is a string or identifier
        String methodName = getStringValue(binaryExpr.right);
        if (methodName != null) {
          return new Expr.MethodCall(leftParen, binaryExpr.left, binaryExpr.operator, methodName, binaryExpr.right.location, args);
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
   * Note that when a field path occurs on the left-hand side of an assignment-like operator
   * we mark all parts of the path with createIfMissing so that if the map field or list entry
   * is not there we automatically create the entry.
   * The other thing to note is that operators such as '+=', '-=', etc need the value of the
   * left-hand side in order to compute the final value. The rules are that something like
   * <pre>  x += 5</pre>
   * is equivalent to
   * <pre>   x = x + 5</pre>
   * and similarly for all other such operators.
   * <p>
   * We could, therefore, just mutate our AST to replace the 'x += 5' with the equivalent of
   * 'x = x + 5' but for situations like 'a.b.c.d += 5' we have a couple of issues:
   * </p>
   * <ol>
   *  <li>We would duplicate the work to lookup all of the intermediate fields that result in
   *      a.b.c.d, and</li>
   *  <li>If a.b.c.d did not yet exist and we want to create it as part of the assigment we need
   *      to create it with a suitable default value before it can be used on the rhs.</li>
   * </ol>
   * A better solution is to replace our binary expression with something like this:
   * <pre>  new FieldOpAssign(parent = new Binary('a.b.c'),
   *           accessOperator = '.',
   *           field = new Expr('d'),
   *           expr = new Binary(Noop, '+', '5'))</pre>
   * This way we now have a component in our AST that can do all the work of finding the parent
   * and creating any missing subfields just once and load the original value (or create a default
   * value) before evaluating the rest of the expression. Finally it can store the result back
   * into the right field (or element) of the Map/List.
   * @param variable            the lhs of the assignment
   * @param assignmentOperator  the operator
   * @param rhs                 the rhs of the operator
   * @param beforeValue         true if result should be the before value (for postfix ++,--)
   * @param createIfMissing     true if missing fields in field path should be created
   */
  private Expr convertToLValue(Expr variable, Token assignmentOperator, Expr rhs, boolean beforeValue, boolean createIfMissing) {
    if (variable instanceof Expr.ExprList) {
      return convertToLvalue((Expr.ExprList)variable, assignmentOperator, rhs);
    }
    if (variable.wasNested) {
      // If we had "(x)" instead of "x" then we need to treat as a multi-assign to make sure
      // we treat rhs as a list of values in order that "(x) = [1]" gives x a value of 1 rather than [1].
      variable.wasNested = false;
      return convertToLvalue(new Expr.ExprList(variable.location, Utils.listOf(variable)), assignmentOperator, rhs);
    }

    // Get the arithmetic operator type for assignments such as +=, -=
    // If assignment is just = or ?= then arithmeticOp will be null.
    TokenType arithmeticOp   = arithmeticOperator.get(assignmentOperator.getType());

    // Field path is either a BinaryOp whose last operator is a field access operation
    // (map or list lookup), or fieldPath is just an identifier for a simple variable.
    if (variable instanceof Expr.Identifier) {
      if (((Expr.Identifier)variable).identifier.getStringValue().charAt(0) == '$') {
        error("Capture variable cannot be modified (invalid lvalue)", variable.location);
      }
      if (arithmeticOp == null) {
        // Just a standard = or ?=
        return new Expr.VarAssign((Expr.Identifier) variable, assignmentOperator, rhs);
      }
      final Expr.Noop leftNoop = new Expr.Noop(assignmentOperator);
      if (assignmentOperator.is(EQUAL_GRAVE)) {
        ((Expr.RegexSubst)rhs).string = leftNoop;
        leftNoop.type = JactlType.STRING;
      }
      else {
        Expr.Binary expr = new Expr.Binary(leftNoop, new Token(arithmeticOp, assignmentOperator), rhs);
        expr.originalOperator = assignmentOperator;
        rhs = expr;
      }
      return new Expr.VarOpAssign((Expr.Identifier) variable, assignmentOperator, rhs);
    }

    if (!(variable instanceof Expr.Binary)) {
      error("Invalid left-hand side for '" + assignmentOperator.getChars() + "' operator (invalid lvalue)", variable.location);
    }

    Expr.Binary fieldPath = (Expr.Binary)variable;

    // Make sure lhs is a valid lvalue
    if (!fieldPath.operator.is(fieldAccessOp)) {
      error("Invalid left-hand side for '" + assignmentOperator.getChars() + "' (invalid lvalue)", fieldPath.operator);
    }

    Expr parent = fieldPath.left;

    // Now set createIfMissing flag on all field access operations (but only those at the "top" level)
    // and only up to the last field. So a.b.c only has the a.b lookup changed to set "createIfMissing".
    if (createIfMissing) {
      setCreateIfMissing(parent);
    }

    Token     accessOperator = fieldPath.operator;
    Expr      field          = fieldPath.right;

    // If arithmeticOp is null it means we have a simple = or ?=
    if (arithmeticOp == null) {
      return new Expr.FieldAssign(parent, accessOperator, field, assignmentOperator, rhs);
    }

    // For all other assignment ops like +=, -=, *= we extract the arithmetic operation
    // (+, -, *, ...) and create a new Binary expression with null as lhs (since value
    // will come from the field value) and with the arithmetic op as the operation and
    // the original rhs as the new rhs of the binary operation
    Token           operator = new Token(arithmeticOp, assignmentOperator);
    final Expr.Noop leftNoop = new Expr.Noop(operator);
    if (operator.is(EQUAL_GRAVE)) {
      ((Expr.RegexSubst)rhs).string = leftNoop;
    }
    else {
      Expr.Binary binaryExpr = new Expr.Binary(leftNoop, operator, rhs);
      binaryExpr.originalOperator = assignmentOperator;
      rhs = binaryExpr;
    }
    Expr.FieldOpAssign expr = new Expr.FieldOpAssign(parent, accessOperator, field, assignmentOperator, rhs);

    // For postfix ++ and -- of fields where we convert to a.b.c += 1 etc we need the pre value
    // (before the inc/dec) as the result
    expr.isPreIncOrDec = beforeValue;
    return expr;
  }

  /**
   * Convert a multi-assignment into a list of lvalue assignments
   * @param lhsExprs   the list of variables/field expressions to be assigned to
   * @param equalToken the operator
   * @param rhsExpr    the right-hand side (which should result in a list)
   * @return a new Expr.Block expression with the lvalues assignment statements
   */
  private Expr convertToLvalue(Expr.ExprList lhsExprs, Token equalToken, Expr rhsExpr) {
    Token rhsToken   = rhsExpr.location;
    Token rhsName    = rhsToken.newIdent(Utils.JACTL_PREFIX + "multiAssign" + uniqueVarCnt++);

    Stmt.Stmts stmts = new Stmt.Stmts(rhsToken);

    // Create a variable for storing rhs. We don't need it after assignment, but it hangs around until
    // end of current scope - could handle this better but not a high priority since not an actual leak.
    //: def _$jmultiAssignN = expression()
    stmts.stmts.add(createVarDecl(rhsName, JactlType.ANY, equalToken, rhsExpr, false, false));

    List<Expr> lhs = lhsExprs.exprs;
    // For each expression in lhs create a "lvalue = _$jmultiAssignN[i]"
    for (int i = 0; i < lhs.size(); i++) {
      Expr assignExpr = convertToLValue(lhs.get(i),
                                        equalToken,
                                        new Expr.Binary(new Expr.Identifier(rhsName),
                                                       new Token(LEFT_SQUARE, equalToken),
                                                       new Expr.Literal(new Token(INTEGER_CONST, equalToken).setValue(i))),
                                        false,
                                        true);
      assignExpr.isResultUsed = i == lhs.size() - 1;     // Use result of last assignment
      stmts.stmts.add(new Stmt.ExprStmt(lhs.get(i).location, assignExpr));
    }

    Token      lparen = lhsExprs.token;
    Expr.Block block  = new Expr.Block(lparen, new Stmt.Block(lparen, stmts));
    return block;
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

  /**
   * Check if a list or map is a constant
   */
  private boolean isConstListOrMap(Expr expr) {
    if (expr instanceof Expr.Literal) {
      return true;
    }
    if (expr instanceof Expr.ListLiteral) {
      return ((Expr.ListLiteral) expr).exprs.stream().allMatch(this::isConstListOrMap);
    }
    if (expr instanceof Expr.MapLiteral) {
      return ((Expr.MapLiteral) expr).entries.stream().allMatch(entry -> isConstListOrMap(entry.first) &&
                                                                         isConstListOrMap(entry.second));
    }
    return false;
  }

  /**
   * Get the value of a constant expression (Literal or ListLiteral or MapLiteral)
   * @param expr  the expression
   * @return the constant value
   */
  private Object literalValue(Expr expr) {
    if (expr instanceof Expr.Literal) {
      return ((Expr.Literal) expr).value.getValue();
    }
    if (expr instanceof Expr.ListLiteral) {
      return ((Expr.ListLiteral) expr).exprs.stream().map(this::literalValue).collect(Collectors.toList());
    }
    if (expr instanceof Expr.MapLiteral) {
      Map map = new LinkedHashMap();
      ((Expr.MapLiteral) expr).entries.stream().forEach(pair -> map.put(literalValue(pair.first), literalValue(pair.second)));
      return map;
    }
    error("Only constant values allowed for cases of match expressions", expr.location);
    return null;
  }

  private Stmt setLastResultIsUsed(Stmt stmt) {
    Supplier<Stmt> nullStmt = () -> new Stmt.ExprStmt(stmt.location, new Expr.Literal(new Token(NULL, stmt.location).setValue(null)));

    return stmt.accept(new Stmt.Visitor<Stmt>() {
      @Override public Stmt visitStmts(Stmt.Stmts stmt) {
        if (stmt.stmts.isEmpty()) {
          stmt.stmts.add(nullStmt.get());
        }
        int index = stmt.stmts.size() - 1;
        stmt.stmts.set(index, setLastResultIsUsed(stmt.stmts.get(index)));
        return stmt;
      }

      @Override public Stmt visitBlock(Stmt.Block stmt) {
        // Special case for while block
        if (stmt.stmts.stmts.stream().anyMatch(s -> s instanceof Stmt.While)) {
          Stmt.Stmts stmts = new Stmt.Stmts(stmt.location);
          stmts.stmts.add(stmt);
          stmts.stmts.add(setLastResultIsUsed(nullStmt.get()));
          return stmts;
        }

        stmt.stmts = (Stmt.Stmts)setLastResultIsUsed(stmt.stmts);
        return stmt;
      }

      @Override public Stmt visitIf(Stmt.If stmt) {
        stmt.trueStmt  = stmt.trueStmt == null ? nullStmt.get() : stmt.trueStmt;
        stmt.falseStmt = stmt.falseStmt == null ? nullStmt.get() : stmt.falseStmt;
        stmt.trueStmt  = setLastResultIsUsed(stmt.trueStmt);
        stmt.falseStmt = setLastResultIsUsed(stmt.falseStmt);
        return stmt;
      }

      @Override public Stmt visitClassDecl(Stmt.ClassDecl stmt) { return null; }
      @Override public Stmt visitImport(Stmt.Import stmt)       { return null; }

      @Override public Stmt visitVarDecl(Stmt.VarDecl stmt) {
        stmt.declExpr.isResultUsed = true;
        return stmt;
      }

      @Override public Stmt visitFunDecl(Stmt.FunDecl stmt) {
        stmt.declExpr.isResultUsed = true;
        return stmt;
      }

      @Override public Stmt visitWhile(Stmt.While stmt) { return stmt; }

      @Override public Stmt visitReturn(Stmt.Return stmt) {
        return stmt;
      }

      @Override public Stmt visitExprStmt(Stmt.ExprStmt stmt) {
        stmt.expr.isResultUsed = true;
        return stmt;
      }

      @Override public Stmt visitThrowError(Stmt.ThrowError stmt) {
        return stmt;
      }
    });
  }

  private static final Map<TokenType,TokenType> arithmeticOperator =
    Utils.mapOf(PLUS_PLUS,                 PLUS,
                MINUS_MINUS,               MINUS,
                PLUS_EQUAL,                PLUS,
                MINUS_EQUAL,               MINUS,
                STAR_EQUAL,                STAR,
                SLASH_EQUAL,               SLASH,
                PERCENT_EQUAL,             PERCENT,
                PERCENT_PERCENT_EQUAL,     PERCENT_PERCENT,
                AMPERSAND_EQUAL,           AMPERSAND,
                PIPE_EQUAL,                PIPE,
                ACCENT_EQUAL,              ACCENT,
                DOUBLE_LESS_THAN_EQUAL,    DOUBLE_LESS_THAN,
                DOUBLE_GREATER_THAN_EQUAL, DOUBLE_GREATER_THAN,
                TRIPLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN,
                EQUAL_GRAVE,               EQUAL_GRAVE);

  /*
   *# keyWord ::= UNDERSCORE | DEF | VAR | BOOLEAN | BYTE | INT | LONG | DOUBLE | DECIMAL | STRING | OBJECT | VOID
   *#           | MAP | LIST | FOR | IF | UNLESS | WHILE | ELSE | CONTINUE | BREAK | CLASS | INTERFACE | EXTENDS
   *#           | IMPLEMENTS | PACKAGE | STATIC | IMPORT | AS | TRUE | FALSE | NULL | IN | INSTANCE_OF | RETURN
   *#           | NEW | AND | OR | NOT | DO | PRINT | PRINTLN | BEGIN | END | DIE | EVAL | FINAL | CONST | SEALED
   *#           | SWITCH | DEFAULT
   */

  /**
   * Genearate the EBNF for expressions based on operator precedence.
   * Generates this content:
   *<pre>
   *# expr ::= expr1 ( (EQUAL | QUESTION_EQUAL | STAR_EQUAL | SLASH_EQUAL | PERCENT_EQUAL | PERCENT_PERCENT_EQUAL | PLUS_EQUAL | MINUS_EQUAL | DOUBLE_LESS_THAN_EQUAL | DOUBLE_GREATER_THAN_EQUAL | TRIPLE_GREATER_THAN_EQUAL | AMPERSAND_EQUAL | PIPE_EQUAL | ACCENT_EQUAL) expr1 ) *
   *# expr1 ::= expr2a ( QUESTION expr2a COLON expr2a ) ?
   *# expr2a ::= ( expr2 QUESTION_COLON )* expr2
   *# expr2 ::= ( expr3 PIPE_PIPE )* expr3
   *# expr3 ::= ( expr4 AMPERSAND_AMPERSAND )* expr4
   *# expr4 ::= ( expr5 PIPE )* expr5
   *# expr5 ::= ( expr6 ACCENT )* expr6
   *# expr6 ::= ( expr7 AMPERSAND )* expr7
   *# expr7 ::= ( expr8 (EQUAL_EQUAL | BANG_EQUAL | COMPARE | EQUAL_GRAVE | BANG_GRAVE | TRIPLE_EQUAL | BANG_EQUAL_EQUAL) )* expr8
   *# expr8 ::= ( expr9 (LESS_THAN | LESS_THAN_EQUAL | GREATER_THAN | GREATER_THAN_EQUAL | INSTANCE_OF | BANG_INSTANCE_OF | IN | BANG_IN | AS) )* expr9
   *# expr9 ::= ( expr10 (DOUBLE_LESS_THAN | DOUBLE_GREATER_THAN | TRIPLE_GREATER_THAN) )* expr10
   *# expr10 ::= ( expr11 (MINUS | PLUS) )* expr11
   *# expr11 ::= ( expr12 (STAR | SLASH | PERCENT | PERCENT_PERCENT) )* expr12
   *# expr12 ::= (QUESTION_QUESTION | GRAVE | BANG | MINUS_MINUS | PLUS_PLUS | MINUS | PLUS)? unary
   *# expr13 ::= ( primary (DOT | QUESTION_DOT | LEFT_SQUARE | QUESTION_SQUARE | LEFT_PAREN | LEFT_BRACE) )* primary
   *# unary ::= expr13 (MINUS_MINUS|PLUS_PLUS)?
   *</pre>
   * @param args args
   */
  public static void main(String[] args) {
    System.out.println("  /*");
    int i = 0;
    int count = operatorsByPrecedence.size();
    String unaryNext = null;
    for (Pair<Boolean,List<TokenType>> pair: operatorsByPrecedence) {
      String current   = "expr";
      if (i > 0) {
        current += i;
      }
      String next      = i+1 == count ? "primary" : "expr" + (i + 1);
      String operators = pair.second.stream().map(Enum::name).collect(Collectors.joining(" | "));
      if (pair.second.size() > 1) {
        operators = "(" + operators + ")";
      }
      System.out.print("  *# ");
      if (unaryOps.equals(pair.second)) {
        System.out.println(current + " ::= " + operators + "? unary");
        unaryNext = next;
      }
      else {
        List<TokenType> ops = new ArrayList<>(pair.second);
        if (ops.remove(QUESTION)) {
          System.out.println(current + " ::= " + next + "a ( QUESTION " + next + "a COLON " + next + "a ) ?");
          current = next + "a";
          operators = ops.stream().map(Enum::name).collect(Collectors.joining(" | "));
          System.out.print("  *# ");
        }
        if (pair.first) {
          // Left associative
          System.out.println(current + " ::= ( " + next + " " + operators + " )* " + next);
        }
        else {
          // Right associative
          System.out.println(current + " ::= " + next + " ( " + operators + " " + next + " ) *");
        }
      }
      i++;
    }
    System.out.println("  *# unary ::= " + unaryNext + " (MINUS_MINUS|PLUS_PLUS)?");
    System.out.println("  */");
  }
}
