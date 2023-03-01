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

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jacsal.JacsalType.ANY;
import static jacsal.JacsalType.UNKNOWN;
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
 * Finally, the Compiler then should be invoked to compile the AST into JVM byte code.
 */
public class Parser {
  Tokeniser tokeniser;
  Token                 firstToken  = null;
  List<CompileError>    errors      = new ArrayList<>();
  Deque<Stmt.ClassDecl> classes     = new ArrayDeque<>();
  boolean               ignoreEol   = false;   // Whether EOL should be treated as whitespace or not
  String                packageName = null;
  JacsalContext         context;

  // Whether we are currently doing a lookahead in which case we don't bother keeping
  // track of state such as functions declared per block and nested functions.
  // We increment every time we start a lookahead (which can then do another lookahead)
  // and decrement when we finish. If value is 0 then we are not in a lookahead.
  int lookaheadCount = 0;

  public Parser(Tokeniser tokeniser, JacsalContext context, String packageName) {
    this.tokeniser   = tokeniser;
    this.context     = context;
    this.packageName = packageName;
  }

  /**
   *# parseScript -> packageDecl? script;
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
   *# parseClass -> packageDecl? classDecl EOF;
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

  private static final TokenType[] types = new TokenType[] { DEF, BOOLEAN, INT, LONG, DOUBLE, DECIMAL, STRING, MAP, LIST };
  private static final List<TokenType> typesAndVar = Utils.concat(types, VAR);

  /**
   *# script -> block;
   */
  private Stmt.FunDecl script() {
    Token start = peek();
    Token scriptName = start.newIdent(Utils.JACSAL_SCRIPT_MAIN);
    // Script take a single parameter which is a Map of globals
    Token        name     = start.newIdent(Utils.JACSAL_GLOBALS_NAME);
    Expr.VarDecl declExpr = new Expr.VarDecl(name, null);
    declExpr.type = JacsalType.MAP;
    declExpr.isParam = true;
    declExpr.isResultUsed = false;
    Stmt.VarDecl globals  = new Stmt.VarDecl(new Token(MAP, start), declExpr);
    Stmt.FunDecl funDecl  = parseFunDecl(scriptName, scriptName, ANY, List.of(globals), EOF, true, false);
    Stmt.ClassDecl scriptClass = classes.peek();
    scriptClass.scriptMain = funDecl;
    List<Stmt> scriptStmts = scriptClass.scriptMain.declExpr.block.stmts.stmts;
    scriptClass.innerClasses = scriptStmts.stream()
                                          .filter(stmt -> stmt instanceof Stmt.ClassDecl)
                                          .map(stmt -> (Stmt.ClassDecl)stmt)
                                          .collect(Collectors.toList());
    // Find all BEGIN blocks and move to start and END blocks and move to the end
    var beginBlocks = scriptStmts.stream()
                                 .filter(stmt -> stmt instanceof Stmt.Block)
                                 .filter(stmt -> ((Stmt.Block) stmt).isBeginBlock)
                                 .collect(Collectors.toList());
    var endBlocks = scriptStmts.stream()
                               .filter(stmt -> stmt instanceof Stmt.Block)
                               .filter(stmt -> ((Stmt.Block) stmt).isEndBlock)
                               .collect(Collectors.toList());

    // If we have begin/end blocks or have to wrap script in a while loop for reading from input
    if (beginBlocks.size() > 0 || endBlocks.size() > 0 || context.printLoop() || context.nonPrintLoop()) {
      Stream<Stmt> bodyStream = scriptStmts.stream().filter(this::isNotBeginEndBlock).filter(stmt -> stmt != globals);
      if (context.nonPrintLoop() || context.printLoop()) {
        var body = bodyStream.collect(Collectors.toList());
        Token whileToken = body.size() > 0 ? body.get(0).location : start;
        // : while ((it = nextLine()) != null)
        var whileStmt = new Stmt.While(whileToken,
                                       new Expr.Binary(
                                         new Expr.VarAssign(new Expr.Identifier(whileToken.newIdent(Utils.IT_VAR)),
                                                            new Token(EQUAL,whileToken),
                                                            new Expr.Call(whileToken,
                                                                          new Expr.Identifier(whileToken.newIdent("nextLine")),
                                                                          List.of())),
                                         new Token(BANG_EQUAL, whileToken),
                                         new Expr.Literal(new Token(NULL, whileToken).setValue(null))));
        if (context.printLoop()) {
          Expr.Print println = new Expr.Print(whileToken, new Expr.Identifier(whileToken.newIdent(Utils.IT_VAR)), true);
          println.isResultUsed = false;
          body.add(new Stmt.ExprStmt(whileToken, println));
        }
        Stmt.Stmts whileBody = new Stmt.Stmts();
        whileBody.stmts = body;
        whileStmt.body = whileBody;
        bodyStream = Stream.of(whileStmt);
      }

      scriptClass.scriptMain.declExpr.block.stmts.stmts =
        Stream.of(Stream.of(globals),
                  beginBlocks.stream(),
                  bodyStream,
                  endBlocks.stream())
              .flatMap(s -> s)
              .collect(Collectors.toList());
    }
    return funDecl;
  }

  /**
   *# package -> "package" IDENTIFIER ( "." IDENTIFIER ) * ;
   */
  private void packageDecl() {
    if (matchAny(PACKAGE)) {
      Token        packageToken = previous();
      List<String> packagePath  = new ArrayList<>();
      do {
        packagePath.add(expect(IDENTIFIER).getStringValue());
      } while (matchAny(DOT));
      String pkg = String.join(".", packagePath);
      if (packageName != null && !pkg.equals(packageName)) {
        throw new CompileError("Declared package name of '" + pkg + "' conflicts with package name '" + packageName + "'", packageToken);
      }
      packageName = pkg;
      matchAnyIgnoreEOL(SEMICOLON);
    }
    if (packageName == null) {
      throw new CompileError("Package name not declared or otherwise supplied", peek());
    }
  }

  /**
   *# importStmt -> "import" classPath ("as" IDENTIFIER)? ;
   */
  List<Stmt.Import> importStmts() {
    List<Stmt.Import> stmts = new ArrayList<>();
    while (matchAnyIgnoreEOL(IMPORT)) {
      Token importToken = previous();
      List<Expr> className = className();
      Token as = null;
      if (matchAnyIgnoreEOL(AS)) {
         as = expect(IDENTIFIER);
      }
      stmts.add(new Stmt.Import(importToken, className, as));
      matchAnyIgnoreEOL(SEMICOLON);
    }
    return stmts;
  }

  /**
   *# block -> "{" stmts "}"
   *#        | stmts EOF      // Special case for top most script block
   *#        ;
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
    Stmt.Stmts stmts = new Stmt.Stmts();
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
   *# stmts -> declaration* ;
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
            throw new CompileError("Unreachable statement", location);
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
   *# declaration -> funDecl
   *#              | varDecl
   *#              | classDecl
   *#              | statement;
   */
  private Stmt declaration() {
    return declaration(false);
  }

  private Stmt declaration(boolean inClassDecl) {
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
   *# statement -> block
   *#            | ifStmt
   *#            | forStmt
   *#            | whileStmt
   *#            | ("BEGIN" | "END") block
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
    if (isAtScriptTopLevel() && matchAny(BEGIN,END)) { return beginEndBlock();    }
    if (matchAny(IF))                                { return ifStmt();           }
    if (matchAny(WHILE))                             { return whileStmt();        }
    if (matchAny(FOR))                               { return forStmt();          }
    if (peek().is(SEMICOLON))                        { return null;               }

    Stmt.ExprStmt stmt = exprStmt();
    // We need to check if exprStmt was a parameterless closure and if so convert back into
    // statement block. The problem is that we can't tell the difference between a closure with
    // no parameters and a code block until after we have parsed them. (If the parameterless
    // closure is called immediately then we know it was a closure and we can tell that it was
    // invoked because expression type won't be a closure.) Otherwise, if the type is a closure
    // and it has no parameters we know it wasn't immediately invoked and we will treat it is a
    // code block.
    if (stmt.expr instanceof Expr.Closure) {
      Expr.Closure closure = (Expr.Closure)stmt.expr;
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
        return new Stmt.If(ifToken, condition, stmt, null);
      }
      else {
        return new Stmt.If(ifToken, condition, null, stmt);
      }
    }

    // If we have a solitary return expression then convert to return statement
    if (stmt.expr instanceof Expr.Return) {
      final var returnExpr = (Expr.Return) stmt.expr;
      returnExpr.isResultUsed = true;
      return new Stmt.Return(returnExpr.returnToken, returnExpr);
    }
    return stmt;
  }

  /**
   *# type -> "def" | "boolean" | "int" | "long" | "double" | "Decimal" | "String" | "Map" | "List"
   *#         | className
   */
  JacsalType type(boolean varAllowed) {
    if (varAllowed ? matchAny(typesAndVar) : matchAny(types)) {
      return JacsalType.valueOf(previous().getType());
    }
    return JacsalType.createInstance(className());
  }

  private boolean isFunDecl(boolean inClassDecl) {
    return lookahead(() -> {
      if (inClassDecl && matchAny(STATIC)) {
        return true;           // "static" can only appear for function declarations
      }
      else {
        type(false);
      }
      if (!matchAnyIgnoreEOL(IDENTIFIER) && !matchKeywordIgnoreEOL()) {
        return false;
      }
      expect(LEFT_PAREN);
      return true;
    });
  }

  /**
   *# funDecl -> "static"? type IDENTIFIER "(" ( varDecl ( "," varDecl ) * ) ? ")" "{" block "}" ;
   */
  private Stmt.FunDecl funDecl(boolean inClassDecl) {
    boolean isStatic = inClassDecl && matchAny(STATIC);
    Token start = peek();
    JacsalType returnType = type(false);
    Token      name       = expect(IDENTIFIER);
    expect(LEFT_PAREN);
    matchAny(EOL);
    List<Stmt.VarDecl> parameters = parameters(RIGHT_PAREN);
    if (inClassDecl && !isStatic && name.getStringValue().equals(Utils.TO_STRING)) {
      Optional<Stmt.VarDecl> mandatoryParam = parameters.stream().filter(p -> p.declExpr.initialiser == null).findFirst();
      if (mandatoryParam.isPresent()) {
        throw new CompileError(Utils.TO_STRING + "() cannot have mandatory parameters", mandatoryParam.get().name);
      }
    }
    matchAny(EOL);
    expect(LEFT_BRACE);
    matchAny(EOL);
    Stmt.FunDecl funDecl = parseFunDecl(start, name, returnType, parameters, RIGHT_BRACE, false, isStatic);
    Utils.createVariableForFunction(funDecl);
    funDecl.declExpr.varDecl.isField = inClassDecl;
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
      JacsalType type = ANY;
      if (peek().is(typesAndVar)) {
        type = type(true);
      }
      if (!peek().is(IDENTIFIER)) {
        unexpected("Expected valid type or parameter name");
      }
      // We have an identifier but we don't yet know if it is a parameter name or a class name
      if (lookahead(() -> className() != null, () -> matchAny(IDENTIFIER))) {
        type = JacsalType.createInstance(className());   // we have a class name
      }

      // Note that unlike a normal varDecl where commas separate different vars of the same type,
      // with parameters we expect a separate comma for parameter with a separate type for each one
      // so we build up a list of singleVarDecl.
      Stmt.VarDecl varDecl = singleVarDecl(type, false);
      varDecl.declExpr.isParam = true;
      parameters.add(varDecl);
      matchAny(EOL);
    }
    return parameters;
  }

  private boolean isVarDecl() {
    return lookahead(() -> matchAny(typesAndVar) || className() != null,
                     () -> matchAnyIgnoreEOL(IDENTIFIER) || matchKeywordIgnoreEOL());
  }

  /**
   *# varDecl -> ("var" | "boolean" | "int" | "long" | "double" | "Decimal" | "String" | "Map" | "List" )
   *#                      IDENTIFIER ( "=" expression ) ? ( "," IDENTIFIER ( "=" expression ) ? ) * ;
   * NOTE: we turn either a single Stmt.VarDecl if only one variable declared or we return Stmt.Stmts with
   *       a list of VarDecls if multiple variables declared.
   */
  private Stmt varDecl(boolean inClassDecl) {
    return doVarDecl(inClassDecl);
  }

  private Stmt doVarDecl(boolean inClassDecl) {
    JacsalType type = type(true);
    Stmt.Stmts stmts = new Stmt.Stmts();
    stmts.stmts.add(singleVarDecl(type, inClassDecl));
    while (matchAny(COMMA)) {
      matchAny(EOL);
      stmts.stmts.add(singleVarDecl(type, inClassDecl));
    }
    if (stmts.stmts.size() > 1) {
      // Multiple variables so return list of VarDecls
      return stmts;
    }
    // Single variable so return just that one
    return stmts.stmts.get(0);
  }

  private Stmt.VarDecl singleVarDecl(JacsalType type, boolean inClassDecl) {
    Token identifier  = expect(IDENTIFIER);
    Expr  initialiser = null;
    Token equalsToken = null;
    if (matchAny(EQUAL)) {
      equalsToken = previous();
      matchAny(EOL);
      initialiser = parseExpression();
    }

    if (type.is(UNKNOWN)) {
      if (initialiser == null) {
        unexpected("Initialiser expression required for 'var' declaration");
      }
      type.typeDependsOn(initialiser);   // Once initialiser type is known the type will then be known
    }

    // If we have an instance and initialiser is not "new X(...)" then convert to "initilaliser as X" to
    // convert rhs into instance of X.
    //   X x = [i:1,j:2]  ==> X x = [i:1,j:2] as X
    if (type.is(JacsalType.INSTANCE) && initialiser != null && !initialiser.isNull() && !initialiser.isNewInstance()) {
      initialiser = new Expr.Binary(initialiser, new Token(AS, initialiser.location), new Expr.TypeExpr(initialiser.location, type));
    }

    Expr.VarDecl varDecl = new Expr.VarDecl(identifier, initialiser);
    varDecl.isResultUsed = false;      // Result not used unless last stmt of a function used as implicit return
    varDecl.type = type;
    varDecl.isField = inClassDecl;
    return new Stmt.VarDecl(identifier, varDecl);
  }

  /**
   *# ifStmt -> "if" "(" expression ")" statement ( "else" statement ) ? ;
   */
  private Stmt.If ifStmt() {
    Token ifToken = previous();
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
   *# whileStmt -> "while" "(" expression ")" statement ;
   */
  private Stmt.While whileStmt() {
    Token whileToken = previous();
    expect(LEFT_PAREN);
    Expr cond      = condition(false, RIGHT_PAREN);
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
    Expr cond           = condition(false, SEMICOLON);
    Stmt update         = commaSeparatedStatements();
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
   *# beginEndBlock -> ("BEGIN" | "END") "{" statements "}" ;
   */
  Stmt.Block beginEndBlock() {
    Token blockType = previous();
    expect(LEFT_BRACE);
    Stmt.Block block = block(previous(), RIGHT_BRACE);
    block.isBeginBlock = blockType.is(BEGIN);
    block.isEndBlock   = blockType.is(END);
    return block;
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
   *# stmtBlock -> "{" statement * "}"
   */
  private Stmt.Block stmtBlock(Token startToken, Stmt... statements) {
    Stmt.Stmts stmts = new Stmt.Stmts();
    stmts.stmts.addAll(List.of(statements));
    return new Stmt.Block(startToken, stmts);
  }

  /**
   *# classDecl -> "class" IDENTIFIER "extends" className "{"
   *#                ( singleVarDecl | "static"? funDecl | classDecl ) *
   *#               "}" ;
   */
  private Stmt.ClassDecl classDecl() {
    if (!isClassDeclAllowed()) {
      throw new CompileError("Class declaration not allowed here", previous());
    }
    var className = expect(IDENTIFIER);
    Token baseClassToken = null;
    JacsalType baseClass = null;
    if (matchAny(EXTENDS)) {
      baseClassToken = peek();
      baseClass = JacsalType.createClass(className());
    }
    var leftBrace = expect(LEFT_BRACE);

    var classDecl = new Stmt.ClassDecl(className, packageName, baseClassToken, baseClass, false);
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
      List<Stmt.VarDecl> fields = classStmts.stream()
                                            .flatMap(stmt -> stmt instanceof Stmt.Stmts ? ((Stmt.Stmts) stmt).stmts.stream()
                                                                                        : Stream.of(stmt))
                                            .filter(stmt -> stmt instanceof Stmt.VarDecl)
                                            .map(stmt -> (Stmt.VarDecl) stmt)
                                            .collect(Collectors.toList());

      classBlock.functions   = classDecl.methods;
      classDecl.innerClasses = innerClasses;
      fields.forEach(field -> classDecl.fieldVars.put(field.name.getStringValue(), field.declExpr));
      return classDecl;
    }
    finally {
      popClass();
    }
  }

  ////////////////////////////////////////////

  // = Expr

  private static TokenType[] fieldAccessOp = new TokenType[] { DOT, QUESTION_DOT, LEFT_SQUARE, QUESTION_SQUARE };

  // NOTE: type case also has same precendence as unaryOps but has no specific operator
  private static List<TokenType> unaryOps = List.of(GRAVE, BANG, MINUS_MINUS, PLUS_PLUS, MINUS, PLUS /*, (type) */);

  // Operators from least precedence to highest precedence. Each entry in list is
  // a pair of a boolean and a list of the operators at that level of precedene.
  // The boolean indicates whether the operators are left-associative (true) or
  // right-associative (false).
  private static List<Pair<Boolean,List<TokenType>>> operatorsByPrecedence =
    List.of(
      // These are handled separately in separate productions to parseExpression:
      //      new Pair(true, List.of(OR)),
      //      new Pair(true, List.of(AND)),
      //      new Pair(true, List.of(NOT)),

      new Pair(false, List.of(EQUAL, QUESTION_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PERCENT_PERCENT_EQUAL, PLUS_EQUAL, MINUS_EQUAL,
      /* STAR_STAR_EQUAL, */ DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN_EQUAL, AMPERSAND_EQUAL,
         PIPE_EQUAL, ACCENT_EQUAL)),
      new Pair(true, List.of(QUESTION, QUESTION_COLON)),
      new Pair(true, List.of(PIPE_PIPE)),
      new Pair(true, List.of(AMPERSAND_AMPERSAND)),
      new Pair(true, List.of(PIPE)),
      new Pair(true, List.of(ACCENT)),
      new Pair(true, List.of(AMPERSAND)),
      new Pair(true, List.of(EQUAL_EQUAL, BANG_EQUAL, COMPARE, EQUAL_GRAVE, BANG_GRAVE, TRIPLE_EQUAL, BANG_EQUAL_EQUAL)),
      new Pair(true, List.of(LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, INSTANCE_OF, BANG_INSTANCE_OF, IN, BANG_IN, AS)),
      new Pair(true, List.of(DOUBLE_LESS_THAN, DOUBLE_GREATER_THAN, TRIPLE_GREATER_THAN)),
      new Pair(true, List.of(MINUS, PLUS)),
      new Pair(true, List.of(STAR, SLASH, PERCENT, PERCENT_PERCENT)),
      //      List.of(STAR_STAR)
      new Pair(true, unaryOps),
      new Pair(true, Utils.concat(fieldAccessOp, LEFT_PAREN, LEFT_BRACE, NEW))
    );

  // Binary operators which can also sometimes be the start of an expression. We need to know
  // when we are checking if an expression continues on the next line whether the next operator
  // should be treated as part of the current expression or the start of a new one.
  // NOTE: we include '/' and '/=' since they could indicate start of a regex.
  //       '-' is also included since it can be the start of a negative number
  private static Set<TokenType> exprStartingOps = Set.of(LEFT_SQUARE, LEFT_PAREN, LEFT_BRACE, SLASH, SLASH_EQUAL, MINUS);

  /**
   *# condition -> expression ;
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
   *# expression -> orExpression ;
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
   *# orExpression -> andExpression ( "or" andExpression) * ;
   */
  private Expr orExpression() {
    Expr expr = andExpression();
    while (matchAnyIgnoreEOL(OR)) {
      expr = new Expr.Binary(expr, new Token(PIPE_PIPE, previous()), andExpression());
    }
    return expr;
  }

  /**
   *# andExpression -> notExpression ( AND notExpression ) * ;
   */
  private Expr andExpression() {
    Expr expr = notExpression();
    while (matchAnyIgnoreEOL(AND)) {
      expr = new Expr.Binary(expr, new Token(AMPERSAND_AMPERSAND, previous()), notExpression());
    }
    return expr;
  }

  /**
   *# notExpresssion -> NOT * (expr | returnExpr | printExpr | "break" | "continue" ) ;
   */
  private Expr notExpression() {
    Expr expr;
    if (matchAnyIgnoreEOL(NOT)) {
      Token notToken = previous();
      expr = new Expr.PrefixUnary(new Token(BANG, notToken), notExpression());
    }
    else {
      matchAny(EOL);
      if (matchAny(RETURN))        { expr = returnExpr();                  } else
      if (matchAny(PRINT,PRINTLN)) { expr = printExpr();                   } else
      if (matchAny(DIE))           { expr = dieExpr();                     } else
      if (matchAny(BREAK))         { expr = new Expr.Break(previous());    } else
      if (matchAny(CONTINUE))      { expr = new Expr.Continue(previous()); }
      else {
        expr = parseExpression();
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
    var operatorsPair = operatorsByPrecedence.get(level);
    boolean isLeftAssociative = operatorsPair.first;
    List<TokenType> operators = operatorsPair.second;

    // If this level of precedence is for our unary operators (includes type cast)
    if (operators == unaryOps) {
      return unary(level);
    }

    Expr expr;
    if (operators.contains(NEW) && peek().is(NEW)) {
      expr = newInstance();
    }
    else {
      expr = parseExpression(level + 1);
    }

    while (matchOp(operators)) {
      Token operator = previous();
      if (operator.is(INSTANCE_OF,BANG_INSTANCE_OF,AS)) {
        Token token = peek();
        JacsalType type = type(false);
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
        // by default we need to strip off the "it =~" and replace with current one
        if (operator.is(EQUAL_GRAVE,BANG_GRAVE)) {
          if (rhs instanceof Expr.RegexMatch && ((Expr.RegexMatch) rhs).implicitItMatch) {
            if (rhs instanceof Expr.RegexSubst && operator.is(BANG_GRAVE)) {
              throw new CompileError("Operator '!~' cannot be used with regex substitution", operator);
            }
            var regex = (Expr.RegexMatch) rhs;
            regex.string = expr;
            regex.operator = operator;
            regex.implicitItMatch = false;
            expr = regex;
          }
          else
          if (isImplicitRegexSubstitute(rhs)) {
            if (operator.is(BANG_GRAVE)) {
              throw new CompileError("Operator '!~' cannot be used with regex substitution", operator);
            }
            var regexSubst = (Expr.RegexSubst)((Expr.VarOpAssign)rhs).expr;
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
   *# unary -> ( "!" | "--" | "++" | "-" | "+" | "~" | "(" type ")" ) unary ( "--" | "++" )
   *#        | expression;
   */
  private Expr unary(int precedenceLevel) {
    Expr expr;
    matchAny(EOL);
    if (lookahead(() -> matchAny(LEFT_PAREN), () -> type(false) != null, () -> matchAny(RIGHT_PAREN))) {
      // Type cast. Rather than create a separate operator for each type case we just use the
      // token for the type as the operator if we detect a type cast.
      expect(LEFT_PAREN);
      matchAny(EOL);
      Token typeToken = peek();
      var castType = type(false);
      matchAny(EOL);
      expect(RIGHT_PAREN);
      Expr unary = unary(precedenceLevel);
      expr = new Expr.Cast(typeToken, castType, unary);
    }
    else
    if (matchAny(unaryOps)) {
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
        expr = convertToLValue(unary, operator, new Expr.Literal(new Token(INTEGER_CONST, operator).setValue(1)), false, true);
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
        expr = convertToLValue(expr, operator, new Expr.Literal(new Token(INTEGER_CONST, operator).setValue(1)), true, true);
      }
      else {
        expr = new Expr.PostfixUnary(expr, previous());
      }
    }
    return expr;
  }

  /**
   *# newInstance -> "new" classPathOrIdentifier "(" arguments ")" ;
   */
  private Expr newInstance() {
    var token     = expect(NEW);
    var className = JacsalType.createInstance(className());
    var leftParen = expect(LEFT_PAREN);
    var args      = arguments();
    return Utils.createNewInstance(token, className, leftParen, args);
  }

  /**
   *# expressionList -> expression ( "," expression ) * ;
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
   *# arguments -> mapEntry + | argList ;
   */
  private List<Expr> arguments() {
    // Check for named args
    if (lookahead(() -> mapKey() != null, () -> matchAnyIgnoreEOL(COLON))) {
      // For named args we create a list with single entry being the map literal that represents
      // the name:value pairs.
      Expr.MapLiteral mapEntries = mapEntries(RIGHT_PAREN);
      return List.of(mapEntries);
    }
    else {
      return argList();
    }
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
   *#          | (IDENTIFIER | classPath)
   *#          | listOrMapLiteral
   *#          | "(" expression ")"
   *#          | "do" "{" block "}"
   *#          | "{" closure "}"
   *#          ;
   */
  private Expr primary() {
    Supplier<Expr> nestedExpression = () -> {
      Expr nested = expression(true);  // parseExpression(0);
      matchAny(EOL);
      expect(RIGHT_PAREN);
      return nested;
    };

    matchAny(EOL);
    if (matchAny(INTEGER_CONST, LONG_CONST,
                 DECIMAL_CONST, DOUBLE_CONST,
                 STRING_CONST, TRUE, FALSE, NULL))         { return new Expr.Literal(previous());         }
    if (peek().is(SLASH, SLASH_EQUAL, EXPR_STRING_START))  { return exprString();                         }
    if (matchAny(REGEX_SUBST_START))                       { return regexSubstitute();                    }
    if (peek().is(IDENTIFIER))                             { return classPathOrIdentifier();              }
    if (peek().is(LEFT_SQUARE,LEFT_BRACE))                 { if (isMapLiteral()) { return mapLiteral(); } }
    if (matchAny(LEFT_SQUARE))                             { return listLiteral();                        }
    if (matchAny(LEFT_PAREN))                              { return nestedExpression.get();               }
    if (matchAny(LEFT_BRACE))                              { return closure();                            }
    if (matchAny(DO)) {
      matchAny(EOL);
      Token leftBrace = expect(LEFT_BRACE);
      return new Expr.Block(leftBrace, block(RIGHT_BRACE));
    }
    if (previousWas(DOT,QUESTION_DOT) && peek().isKeyword()) {
      // Allow keywords to be used in dotted paths. E.g: x.y.while.z
      return new Expr.Literal(new Token(STRING_CONST, advance()).setValue(previous().getChars()));
    }
    return unexpected("Expected start of expression");
  }

  /**
   *# classPathOrIdentifier -> IDENTIFIER | classPath ;
   */
  private Expr classPathOrIdentifier() {
    // Can only be classPath if previous token was not "." since we want to avoid treating a.x.y.z.A
    // as a."x.y.z.A" if x.y.z is a package name but a.x.y.z isn't.
    if (!previousWas(DOT)) {
      Expr classPath = classPath();
      if (classPath != null) {
        return classPath;
      }
    }
    return new Expr.Identifier(expect(IDENTIFIER));
  }

  /**
   *# className -> IDENTIFIER ( "." IDENTIFIER ) + ;
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
   *# className -> classPathOrIdentifier ( "." IDENTIFIER ) * ;
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
   *# listLiteral -> "[" ( expression ( "," expression ) * ) ? "]"
   */
  private Expr.ListLiteral listLiteral() {
    Expr.ListLiteral expr = new Expr.ListLiteral(previous());
    while (!matchAnyIgnoreEOL(RIGHT_SQUARE)) {
      if (expr.exprs.size() > 0) {
        expect(COMMA);
      }
      expr.exprs.add(expression(true));
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
    expect(LEFT_SQUARE, LEFT_BRACE);
    TokenType endToken = previous().is(LEFT_BRACE) ? RIGHT_BRACE : RIGHT_SQUARE;
    return mapEntries(endToken);
  }

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
          expect(COMMA);
        }
        Expr   key       = mapKey();
        String keyString = null;
        if (key instanceof Expr.Literal) {
          Object keyValue = ((Expr.Literal) key).value.getValue();
          if (!(keyValue instanceof String)) {
            throw new CompileError(paramOrKey + " must be String not " + RuntimeUtils.className(keyValue), key.location);
          }
          keyString = keyValue.toString();
          if (literalKeyMap.containsKey(keyString)) {
            throw new CompileError(paramOrKey + " '" + keyValue.toString() + "' occurs multiple times", key.location);
          }
        }
        else {
          if (isNamedArgs) {
            throw new CompileError("Invalid parameter name", previous());
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
   *# mapKey -> STRING_CONST | IDENTIFIER | "(" expression() + ")" | exprString | keyWord ;
   */
  private Expr mapKey() {
    matchAny(EOL);
    if (matchAny(STRING_CONST,IDENTIFIER)) { return new Expr.Literal(previous()); }
    if (peek().is(EXPR_STRING_START))      { return exprString(); }
    if (peek().isKeyword())                { advance(); return new Expr.Literal(previous()); }
    if (matchAny(LEFT_PAREN)) {
      Expr expr = expression(true);
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
   *# exprString -> EXPR_STRING_START ( "$" IDENTIFIER | "${" blockExpr "}" | STRING_CONST ) * EXPR_STRING_END
   *#             | "/" ( IDENTIFIER | "{" blockExpr "}" | STRING_CONST ) * "/";
   * We parse an expression string delimited by " or """ or /
   * For the / version we treat as a multi-line regular expression and don't support escape chars.
   */
  private Expr exprString() {
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
    parseExprString(exprString, EXPR_STRING_END);
    // If regex then we don't know yet whether we are part of a "x =~ /regex/" or just a /regex/
    // on our own somewhere. We build "it =~ /regex" and if we are actually part of "x =~ /regex/"
    // our parent (parseExpression()) will convert back to "x =~ /regex/".
    // If we detect at resolve time that the standalone /regex/ has no modifiers and should just
    // be a regular expression string then we will fix the problem then.
    if (startToken.is(SLASH)) {
      return new Expr.RegexMatch(new Expr.Identifier(startToken.newIdent(Utils.IT_VAR)),
                                 new Token(EQUAL_GRAVE, startToken),
                                 exprString,
                                 previous().getStringValue(),   // modifiers
                                 true);
    }
    return exprString;
  }

  private void parseExprString(Expr.ExprString exprString, TokenType endToken) {
    while (!matchAny(endToken)) {
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
  }

  /**
   *# regexSubstitute -> REGEX_SUBST_START ( "$" IDENTIFIER | "${" blockExpr "}" | STRING_CONST ) *
   *                        REGEX_REPLACE ( "$" IDENTIFIER | "${" blockExpr "}" | STRING_CONST ) * EXPR_STRING_END;
   */
  private Expr regexSubstitute() {
    Token start = previous();
    Expr.ExprString pattern = new Expr.ExprString(previous());
    parseExprString(pattern, REGEX_REPLACE);
    Expr.ExprString replace = new Expr.ExprString(previous());
    parseExprString(replace, EXPR_STRING_END);

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

    final var itVar       = new Expr.Identifier(start.newIdent(Utils.IT_VAR));
    final var operator    = new Token(EQUAL_GRAVE, start);
    final var modifiers   = previous().getStringValue();
    final var regexSubst = new Expr.RegexSubst(itVar, operator, pattern, modifiers, true, replace, isComplexReplacement);
    // Modifier 'f' forces the substitute to be a value and not override the original variable
    if (modifiers.indexOf('f') != -1) {
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
   *# blockExpression -> "{" block "}"
   *
   * Used inside expression strings. If no return statement there is an implicit
   * return on last statement in block that gives the value to then interpolate
   * into surrounding expression string.
   * If the block contains multiple statements we turn it into the equivalent of
   * an anonymous closure invocation. E.g.:
   *   "${stmt1; stmt2; return val}" --> "${ {stmt1;stmt2;return val}() }"
   */
  private Expr blockExpression() {
    Token leftBrace = previous();
    Stmt.Block block = block(RIGHT_BRACE);
    if (block.stmts.stmts.size() == 0) {
      return new Expr.Literal(new Token(NULL, leftBrace));
    }
    // If we have a single ExprStmt then our expression is just the expression inside the statement
    if (block.stmts.stmts.size() == 1) {
      Stmt stmt = block.stmts.stmts.get(0);
      if (stmt instanceof Stmt.ExprStmt) {
        Expr expr = ((Stmt.ExprStmt) stmt).expr;
        expr.isResultUsed = true;
        return expr;
      }
    }

    // We have more than one statement or statement is more than just an expression so convert
    // block into a parameter-less closure and then invoke it.
    Stmt.FunDecl closureFunDecl = convertBlockToClosure(leftBrace, block);
    closureFunDecl.declExpr.isResultUsed = true;
    Expr closure = new Expr.Closure(leftBrace, closureFunDecl.declExpr, true);
    return createCallExpr(closure, leftBrace, List.of());
  }

  /**
   *# closure -> "{" (parameters "->" ) ? block "}"
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
      parameters = List.of(createItParam(openBrace));
    }
    Stmt.FunDecl funDecl = parseFunDecl(openBrace, null, ANY, parameters, RIGHT_BRACE, false, false);
    funDecl.declExpr.isResultUsed = true;
    return new Expr.Closure(openBrace, funDecl.declExpr, noParamsDefined);
  }

  /**
   # returnExpr -> "return" expression;
   */
  private Expr.Return returnExpr() {
    Token location = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(NULL, location).setValue(null))
                                    : parseExpression();
    return new Expr.Return(location, expr, null);   // returnType will be set by Resolver
  }

  /**
   *# printExpr -> ("print" | "println") expr ?;
   */
  private Expr.Print printExpr() {
    Token printToken = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(STRING_CONST, printToken).setValue(""))
                                    : parseExpression();
    return new Expr.Print(printToken, expr, printToken.is(PRINTLN));
  }

  /**
   *# dieExpr -> die expr ?;
   */
  private Expr.Die dieExpr() {
    Token dieToken = previous();
    Expr expr = isEndOfExpression() ? new Expr.Literal(new Token(STRING_CONST, dieToken).setValue(""))
                                    : parseExpression();
    return new Expr.Die(dieToken, expr);
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
      var       ident = (Expr.Identifier)expr;
      final var name  = ident.identifier.getStringValue();
      if (name.charAt(0) == '$') {
        // Check if capture arg is greater than $9
        return name.length() <= 2;
      }
      return true;
    }
    return false;
  }

  private Stmt.VarDecl createItParam(Token token) {
    return Utils.createParam(token.newIdent(Utils.IT_VAR), ANY, new Expr.Literal(new Token(NULL, token)));
  }

  // Create parameter for class instance initialiser method
  private Stmt.VarDecl createInitParam(Expr.VarDecl varDecl) {
    Stmt.VarDecl paramDecl = Utils.createParam(varDecl.name, varDecl.type, varDecl.initialiser);
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
                                    JacsalType returnType,
                                    List<Stmt.VarDecl> params,
                                    TokenType endToken,
                                    boolean isScriptMain,
                                    boolean isStatic) {
    params.forEach(p -> p.declExpr.isExplicitParam = true);
    Expr.FunDecl funDecl = Utils.createFunDecl(start, name, returnType, params, isStatic, false);
    params.forEach(p -> p.declExpr.owner = funDecl);
    Stmt.FunDecl funDeclStmt = new Stmt.FunDecl(start, funDecl);
    if (!isScriptMain && !funDecl.isClosure()) {
      // Add to functions in block so we can support forward references during Resolver phase
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
    return convertBlockToFunction(start, block, ANY, List.of(), false);
  }

  private Stmt.FunDecl convertBlockToFunction(Token name, Stmt.Block block, JacsalType returnType, List<Stmt.VarDecl> params, boolean isStatic) {
    Expr.FunDecl funDecl = Utils.createFunDecl(name, name, returnType, params, isStatic, false);
    insertStmtsInto(params, block);
    funDecl.block = block;
    return new Stmt.FunDecl(name, funDecl);
  }

  private boolean isMapLiteral() {
    // Check for start of a Map literal. We need to lookahead to know the difference between
    // a Map literal using '{' and '}' and a statement block or a closure.
    return lookahead(() -> matchAnyIgnoreEOL(LEFT_SQUARE, LEFT_BRACE), () -> matchAnyIgnoreEOL(COLON)) ||
           lookahead(() -> matchAnyIgnoreEOL(LEFT_SQUARE, LEFT_BRACE), () -> mapKey() != null, () -> matchAnyIgnoreEOL(COLON));
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
    return matchAnyIgnoreEOL(types.toArray(TokenType[]::new));
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
    Token previous           = previous();
    Token current            = tokeniser.peek();
    boolean currentIgnoreEol = ignoreEol;
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
      tokeniser.rewind(previous, current);
      errors        = currentErrors;
      lookaheadCount--;
      ignoreEol = currentIgnoreEol;
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
    final var chars = peekIsEOL() ? "EOL" : "'" + peek().getChars() + "'";
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
    if (types.length > 1) {
      unexpected("Expecting one of " +
                 Arrays.stream(types)
                       .map(Enum::toString)
                       .map(t -> "'" + t + "'")
                       .collect(Collectors.joining(", ")));
    }
    else {
      var expected = types[0].is(IDENTIFIER) ? "identifier" : "'" + types[0] + "'";
      unexpected("Expecting " + expected);
    }
    return null;
  }

  private Token expect(List<TokenType> types) {
    return expect(types.toArray(TokenType[]::new));
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
   * Note that when a field path occurs on the left hand side of an assignment-like operator
   * we mark all parts of the path with createIfMissing so that if the map field or list entry
   * is not there we automatically create the entry.
   * The other thing to note is that operators such as '+=', '-=', etc need the value of the
   * left hand side in order to compute the final value. The rules are that something like
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
    // Get the arithmetic operator type for assignments such as +=, -=
    // If assignment is just = or ?= then arithmeticOp will be null.
    TokenType arithmeticOp   = arithmeticOperator.get(assignmentOperator.getType());

    // Field path is either a BinaryOp whose last operator is a field access operation
    // (map or list lookup), or fieldPath is just an identifier for a simple variable.
    if (variable instanceof Expr.Identifier) {
      if (arithmeticOp == null) {
        // Just a standard = or ?=
        return new Expr.VarAssign((Expr.Identifier) variable, assignmentOperator, rhs);
      }
      final var leftNoop = new Expr.Noop(assignmentOperator);
      if (assignmentOperator.is(EQUAL_GRAVE)) {
        ((Expr.RegexSubst)rhs).string = leftNoop;
        leftNoop.type = JacsalType.STRING;
      }
      else {
        var expr = new Expr.Binary(leftNoop, new Token(arithmeticOp, assignmentOperator), rhs);
        expr.originalOperator = assignmentOperator;
        rhs = expr;
      }
      return new Expr.VarOpAssign((Expr.Identifier) variable, assignmentOperator, rhs);
    }

    if (!(variable instanceof Expr.Binary)) {
      throw new CompileError("Invalid left-hand side for '" + assignmentOperator.getChars() + "' operator (invalid lvalue)", variable.location);
    }

    Expr.Binary fieldPath = (Expr.Binary)variable;

    // Make sure lhs is a valid lvalue
    if (!fieldPath.operator.is(fieldAccessOp)) {
      throw new CompileError("Invalid left-hand side for '" + assignmentOperator.getChars() + "' (invalid lvalue)", fieldPath.operator);
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
    Token operator = new Token(arithmeticOp, assignmentOperator);
    final var   leftNoop       = new Expr.Noop(operator);
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

  private void setCreateIfMissing(Expr expr) {
    if (expr instanceof Expr.Binary) {
      Expr.Binary binary = (Expr.Binary) expr;
      if (binary.operator.is(fieldAccessOp)) {
        binary.createIfMissing = true;
      }
      setCreateIfMissing(binary.left);
    }
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
}
