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


package io.jactl;

////////////////////////////////////////////////////////////////////
// File was generated using GenerateClasses.pl in tools directory
// DO NOT EDIT THIS FILE
////////////////////////////////////////////////////////////////////


import java.util.*;

import io.jactl.Utils;
import io.jactl.runtime.ClassDescriptor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import io.jactl.runtime.FunctionDescriptor;

import static io.jactl.JactlType.HEAPLOCAL;

/**
 * Expr classes for our AST.
 */
abstract class Expr {

  abstract <T> T accept(Visitor<T> visitor);


  Token      location;
  JactlType type;
  boolean    isResolved = false;

  boolean    isConst = false;   // Whether expression consists only of constants
  Object     constValue;        // If expression is only consts then we keep the
                                // result of evaluating the expression during the
                                // resolve phase here.

  boolean    isCallee    = false; // Whether we are the callee in a call expression
  boolean    couldBeNull = true;  // Whether result could be null
  boolean    isAsync     = false; // Whether expression contains an async call

  // Flag that indicates whether result for the Expr is actually used. Most expressions
  // have their value used, for example, when nested within another expression or when
  // the expression is used as a condition for if/while/for or as assignment to a variable.
  // For some expressions (e.g. the top Expr in an expression statement) the result of the
  // expression is ignored so this flag allows us to know whether to leave the result on
  // the stack or not.
  boolean isResultUsed = true;

  // Marker interface to indicate whether MethodCompiler visitor for that element type
  // handles leaving result on stack or not (based on isResultUsed flag) or whether
  // result management needs to be done for it.
  interface ManagesResult {}

  // Whether expression is a direct function call (and not a closure)
  public boolean isFunctionCall() {
    if (!(this instanceof Expr.Identifier)) { return false; }
    var ident = (Expr.Identifier)this;
    return ident.varDecl.type.is(JactlType.FUNCTION) && ident.varDecl.funDecl != null;
  }

  // True if expression is a MapLiteral where all keys are string literals
  public boolean isConstMap() {
    return this instanceof Expr.MapLiteral && ((Expr.MapLiteral)this).literalKeyMap != null;
  }

  // True if expr is "null"
  public boolean isNull() {
    return this instanceof Expr.Literal && ((Expr.Literal)this).value.getType().is(TokenType.NULL);
  }

  // True if this is a "new X()" expression
  public boolean isNewInstance() {
    return this instanceof Expr.MethodCall && ((Expr.MethodCall)this).parent instanceof Expr.InvokeNew;
  }

  // True if expression is "super"
  public boolean isSuper() {
    return this instanceof Expr.Identifier && ((Expr.Identifier)this).identifier.getStringValue().equals(Utils.SUPER_VAR);
  }

  public boolean isThis() {
    return this instanceof Expr.Identifier && ((Expr.Identifier)this).identifier.getStringValue().equals(Utils.THIS_VAR);
  }

  static class Binary extends Expr {
    Expr  left;
    Token operator;
    Expr  right;
    boolean createIfMissing = false;  // Used for field access used as lvalues
    boolean isFieldAccess   = false;  // True if this is a field access expression where field name and type are known
    Token originalOperator;           // When -- or ++ is turned into x = x + 1 this is the actual --/++ op
    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBinary(this); }
    @Override public String toString() { return "Binary[" + "left=" + left + ", " + "operator=" + operator + ", " + "right=" + right + "]"; }
  }

  static class RegexMatch extends Expr {
    Expr    string;
    Token   operator;
    Expr    pattern;
    String  modifiers;
    boolean isSubstitute = false;
    boolean implicitItMatch;   // True if standalone /regex/ which we then implicitly match against "it"
    VarDecl captureArrVarDecl;
    RegexMatch(Expr string, Token operator, Expr pattern, String modifiers, boolean implicitItMatch) {
      this.string = string;
      this.operator = operator;
      this.pattern = pattern;
      this.modifiers = modifiers;
      this.implicitItMatch = implicitItMatch;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitRegexMatch(this); }
    @Override public String toString() { return "RegexMatch[" + "string=" + string + ", " + "operator=" + operator + ", " + "pattern=" + pattern + ", " + "modifiers=" + modifiers + ", " + "implicitItMatch=" + implicitItMatch + "]"; }
  }

  static class RegexSubst extends RegexMatch {
    Expr    replace;
    boolean isComplexReplacement;   // True if replacement string has embedded expressions
    { isSubstitute = true; }
    RegexSubst(Expr string, Token operator, Expr pattern, String modifiers, boolean implicitItMatch, Expr replace, boolean isComplexReplacement) {
      super(string, operator, pattern, modifiers, implicitItMatch);
      this.replace = replace;
      this.isComplexReplacement = isComplexReplacement;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitRegexSubst(this); }
    @Override public String toString() { return "RegexSubst[" + "string=" + string + ", " + "operator=" + operator + ", " + "pattern=" + pattern + ", " + "modifiers=" + modifiers + ", " + "implicitItMatch=" + implicitItMatch + ", " + "replace=" + replace + ", " + "isComplexReplacement=" + isComplexReplacement + "]"; }
  }

  /**
   * Ternary expression - only used for:
   *     cond ? trueExpr : falseExpr
   */
  static class Ternary extends Expr {
    Expr  first;
    Token operator1;
    Expr  second;
    Token operator2;
    Expr  third;
    Ternary(Expr first, Token operator1, Expr second, Token operator2, Expr third) {
      this.first = first;
      this.operator1 = operator1;
      this.second = second;
      this.operator2 = operator2;
      this.third = third;
      this.location = operator1;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitTernary(this); }
    @Override public String toString() { return "Ternary[" + "first=" + first + ", " + "operator1=" + operator1 + ", " + "second=" + second + ", " + "operator2=" + operator2 + ", " + "third=" + third + "]"; }
  }

  static class PrefixUnary extends Expr implements ManagesResult {
    Token operator;
    Expr  expr;
    PrefixUnary(Token operator, Expr expr) {
      this.operator = operator;
      this.expr = expr;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitPrefixUnary(this); }
    @Override public String toString() { return "PrefixUnary[" + "operator=" + operator + ", " + "expr=" + expr + "]"; }
  }

  static class PostfixUnary extends Expr implements ManagesResult {
    Expr  expr;
    Token operator;
    PostfixUnary(Expr expr, Token operator) {
      this.expr = expr;
      this.operator = operator;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitPostfixUnary(this); }
    @Override public String toString() { return "PostfixUnary[" + "expr=" + expr + ", " + "operator=" + operator + "]"; }
  }

  static class Cast extends Expr implements ManagesResult {
    Token       token;
    JactlType  castType;
    Expr        expr;
    Cast(Token token, JactlType castType, Expr expr) {
      this.token = token;
      this.castType = castType;
      this.expr = expr;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitCast(this); }
    @Override public String toString() { return "Cast[" + "token=" + token + ", " + "castType=" + castType + ", " + "expr=" + expr + "]"; }
  }

  static class Call extends Expr {
    Token      token;
    Expr       callee;
    List<Expr> args;

    boolean validateArgsAtCompileTime; // true if we should validate args at compile time rather than at runtime

    // If we are a call to an arbitrary function/closure then it is possible that the
    // function we are calling returns an Iterator:  def f = x.map; f()
    // In this case we need to check that if the result is not immediately used as the
    // target of a chained method call (f().each()) and the result was an Iterator then
    // the result must be converted to a List.
    boolean isMethodCallTarget;
    Call(Token token, Expr callee, List<Expr> args) {
      this.token = token;
      this.callee = callee;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitCall(this); }
    @Override public String toString() { return "Call[" + "token=" + token + ", " + "callee=" + callee + ", " + "args=" + args + "]"; }
  }

  static class MethodCall extends Expr {
    Token      leftParen;
    Expr       parent;
    Token      accessOperator; // Either '.' or '?.'
    String     methodName;     // Either the method name or field name that holds a MethodHandle
    Token      methodNameLocation;
    List<Expr> args;

    FunctionDescriptor methodDescriptor;
    boolean validateArgsAtCompileTime;     // true if we should validate args at compile time rather than at runtime

    // True if result of method call becomes the target of the next method call. This is used so
    // that we can allow Iterators to be the result of a list.map() call which is then itself used
    // as the target of another map() call (or call that can operator on an Iterator). Otherwise
    // we need to convert the Iterator into a List since Iterators don't really exist at the Jactl
    // language level and are only used as an implementation detail for some iteration methods.
    // E.g.: x.map().map().each()
    // This means that the x.map() can return an iterator that is then used in the next .map() which
    // returns another iterator that is used in the .each() call.
    // If the result is not to be used by another method then we convert the Iterator into a List.
    // That way x.map{} which in theory does nothing but might have side effects if the closure has
    // a side effect will still run and cause the side effects to happen even though the end result
    // is not actually used.
    boolean isMethodCallTarget = false;
    MethodCall(Token leftParen, Expr parent, Token accessOperator, String methodName, Token methodNameLocation, List<Expr> args) {
      this.leftParen = leftParen;
      this.parent = parent;
      this.accessOperator = accessOperator;
      this.methodName = methodName;
      this.methodNameLocation = methodNameLocation;
      this.args = args;
      this.location = leftParen;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitMethodCall(this); }
    @Override public String toString() { return "MethodCall[" + "leftParen=" + leftParen + ", " + "parent=" + parent + ", " + "accessOperator=" + accessOperator + ", " + "methodName=" + methodName + ", " + "methodNameLocation=" + methodNameLocation + ", " + "args=" + args + "]"; }
  }

  static class Literal extends Expr {
    Token value;
    Literal(Token value) {
      this.value = value;
      this.location = value;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitLiteral(this); }
    @Override public String toString() { return "Literal[" + "value=" + value + "]"; }
  }

  static class ListLiteral extends Expr {
    Token start;
    List<Expr> exprs = new ArrayList<>();
    ListLiteral(Token start) {
      this.start = start;
      this.location = start;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitListLiteral(this); }
    @Override public String toString() { return "ListLiteral[" + "start=" + start + "]"; }
  }

  static class MapLiteral extends Expr {
    Token start;
    List<Pair<Expr,Expr>> entries = new ArrayList<>();
    boolean               isNamedArgs;    // whether this map is used as named args for function/method/constructor call
    Map<String,Expr>      literalKeyMap;  // Map based on key names if all keys were string literals
    MapLiteral(Token start) {
      this.start = start;
      this.location = start;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitMapLiteral(this); }
    @Override public String toString() { return "MapLiteral[" + "start=" + start + "]"; }
  }

  static class Identifier extends Expr {
    Token              identifier;
    Expr.VarDecl       varDecl;               // for variable references
    boolean            couldBeFunctionCall = false;
    FunctionDescriptor getFuncDescriptor() { return varDecl.funDecl.functionDescriptor; }
    Identifier(Token identifier) {
      this.identifier = identifier;
      this.location = identifier;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitIdentifier(this); }
    @Override public String toString() { return "Identifier[" + "identifier=" + identifier + "]"; }
  }

  static class ClassPath extends Expr {
    Token pkg;
    Token className;
    ClassPath(Token pkg, Token className) {
      this.pkg = pkg;
      this.className = className;
      this.location = pkg;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitClassPath(this); }
    @Override public String toString() { return "ClassPath[" + "pkg=" + pkg + ", " + "className=" + className + "]"; }
  }

  static class ExprString extends Expr {
    Token exprStringStart;
    List<Expr> exprList = new ArrayList<>();
    ExprString(Token exprStringStart) {
      this.exprStringStart = exprStringStart;
      this.location = exprStringStart;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitExprString(this); }
    @Override public String toString() { return "ExprString[" + "exprStringStart=" + exprStringStart + "]"; }
  }

  /**
   * Variable declaration with optional initialiser.
   * This is done as an expression in case value of the assignment
   * is returned implicitly from a function/closure.
   */
  static class VarDecl extends Expr implements ManagesResult {
    Token           name;
    Expr            initialiser;
    Expr.FunDecl    owner;               // Which function variable belongs to (for local vars)
    boolean         isGlobal;            // Whether global (bindings var) or local
    boolean         isHeapLocal;         // Is this a heap local var
    boolean         isPassedAsHeapLocal; // If we are an explicit parameter and HeapLocal is passed to us from wrapper
    boolean         isParam;             // True if variable is a parameter of function (explicit or implicit)
    boolean         isExplicitParam;     // True if explicit declared parameter of function
    boolean         isField;             // True if instance field of a class
    int             slot = -1;           // Which local variable slot
    int             nestingLevel;        // What level of nested function owns this variable (1 is top level)
    Label           declLabel;           // Where variable comes into scope (for debugger)
    Expr.FunDecl    funDecl;             // If type is FUNCTION then this is the function declaration
    ClassDescriptor classDescriptor;     // If type is CLASS then this is the class descriptor
    VarDecl         parentVarDecl;       // If this is a HeapLocal parameter then this is the VarDecl from parent
    VarDecl         originalVarDecl;     // VarDecl for actual original variable declaration

    // If never reassigned we know it is effectively final. This means that if we have "def f = { ... }" and
    // closure is not async and f is never reassigned (isFinal == true) we know that calls to "f()" will never
    // be async.
    boolean      isFinal = true;

    // When we are in the wrapper function we create a variable for every parameter.
    // This points to the parameter so we can turn it into HeapLocal if necessary and
    // to set its type (if it was declared as "var") once we know the type of the initialiser.
    VarDecl      paramVarDecl;

    Type descriptorType() { return isPassedAsHeapLocal ? HEAPLOCAL.descriptorType() : type.descriptorType(); }
    VarDecl(Token name, Expr initialiser) {
      this.name = name;
      this.initialiser = initialiser;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitVarDecl(this); }
    @Override public String toString() { return "VarDecl[" + "name=" + name + ", " + "initialiser=" + initialiser + "]"; }
  }

  /**
   * Function declaration
   * We make this an expression so we can have last statement in a block be a function declaration
   * and have it then returned as the return value of the function.
   */
  static class FunDecl extends Expr implements ManagesResult {
    Token              startToken;   // Either identifier for function decl or start brace for closure
    Token              nameToken;    // Null for closures and script main
    JactlType         returnType;
    List<Stmt.VarDecl> parameters;
    Stmt.Block         block;

    FunctionDescriptor functionDescriptor;
    Expr.VarDecl       varDecl;            // For the variable that we will create to hold our MethodHandle
    Expr.FunDecl       owner;              // If we are nested inside another function then this the enclosing function

    boolean            isWrapper;   // Whether this is the wrapper function or the real one
    Expr.FunDecl       wrapper;     // The wrapper method that handles var arg and named arg invocations

    boolean        isScriptMain = false; // Whether this is the funDecl for the script main function
    Stmt.ClassDecl classDecl = null;     // For init methods this is our ClassDecl
    int            closureCount = 0;
    boolean        isCompiled = false;

    // Nested while loops. Used by Resolver to find target of break/continue stmts.
    Deque<Stmt.While> whileLoops = new ArrayDeque<>();

    // Stack of blocks used during Resolver phase to track variables and which scope they
    // are declared in and used during Parser phase to track function declarations so we
    // can handle forward references during Resolver phase
    Deque<Stmt.Block> blocks = new ArrayDeque<>();

    // Which heap locals from our parent we need passed in to us
    LinkedHashMap<String,Expr.VarDecl>       heapLocalsByName = new LinkedHashMap<>();
    LinkedHashMap<Expr.VarDecl,Expr.VarDecl> heapLocals       = new LinkedHashMap<>();

    // Remember earliest (in the code) forward reference to us so we can make sure that
    // no variables we close over are declared after that reference
    Token earliestForwardReference;

    // Keep track of maximum number of locals needed so we know how big an array to
    // allocate for capturing our state if we suspend
    int          localsCnt = 0;
    int          maxLocals = 0;

    void allocateLocals(int n) { localsCnt += n; maxLocals = maxLocals > localsCnt ? maxLocals : localsCnt; }
    void freeLocals(int n)     { localsCnt -= n; assert localsCnt >= 0;}


    public boolean isClosure()    { return nameToken == null; }
    public boolean isStatic()     { return functionDescriptor.isStatic; }
    public boolean isInitMethod() { return functionDescriptor.isInitMethod; }
    public int     globalsVar()   { return isScriptMain ? (functionDescriptor.isAsync ? 2 : 1) : -1; }
    FunDecl(Token startToken, Token nameToken, JactlType returnType, List<Stmt.VarDecl> parameters) {
      this.startToken = startToken;
      this.nameToken = nameToken;
      this.returnType = returnType;
      this.parameters = parameters;
      this.location = startToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitFunDecl(this); }
    @Override public String toString() { return "FunDecl[" + "startToken=" + startToken + ", " + "nameToken=" + nameToken + ", " + "returnType=" + returnType + ", " + "parameters=" + parameters + "]"; }
  }

  /**
   * When variable used as lvalue in an assignment
   */
  static class VarAssign extends Expr implements ManagesResult {
    Identifier identifierExpr;
    Token      operator;
    Expr       expr;
    VarAssign(Identifier identifierExpr, Token operator, Expr expr) {
      this.identifierExpr = identifierExpr;
      this.operator = operator;
      this.expr = expr;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitVarAssign(this); }
    @Override public String toString() { return "VarAssign[" + "identifierExpr=" + identifierExpr + ", " + "operator=" + operator + ", " + "expr=" + expr + "]"; }
  }

  /**
   * When variable used as lvalue in an assignment of type +=, -=, ...
   */
  static class VarOpAssign extends Expr implements ManagesResult {
    Identifier identifierExpr;
    Token      operator;
    Expr       expr;
    VarOpAssign(Identifier identifierExpr, Token operator, Expr expr) {
      this.identifierExpr = identifierExpr;
      this.operator = operator;
      this.expr = expr;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitVarOpAssign(this); }
    @Override public String toString() { return "VarOpAssign[" + "identifierExpr=" + identifierExpr + ", " + "operator=" + operator + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Used to represent assignment to a field or list element
   */
  static class FieldAssign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;
    FieldAssign(Expr parent, Token accessType, Expr field, Token assignmentOperator, Expr expr) {
      this.parent = parent;
      this.accessType = accessType;
      this.field = field;
      this.assignmentOperator = assignmentOperator;
      this.expr = expr;
      this.location = accessType;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitFieldAssign(this); }
    @Override public String toString() { return "FieldAssign[" + "parent=" + parent + ", " + "accessType=" + accessType + ", " + "field=" + field + ", " + "assignmentOperator=" + assignmentOperator + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Used to represent assignment to a field or list element where assignment is done with
   * one of the +=, -=, *=, ... operations
   */
  static class FieldOpAssign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;

    // true if value before operation should be result - used for post inc/dec of fields
    // where we covert to a binary += or -= and then need the before value as the result
    boolean isPreIncOrDec;
    FieldOpAssign(Expr parent, Token accessType, Expr field, Token assignmentOperator, Expr expr) {
      this.parent = parent;
      this.accessType = accessType;
      this.field = field;
      this.assignmentOperator = assignmentOperator;
      this.expr = expr;
      this.location = accessType;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitFieldOpAssign(this); }
    @Override public String toString() { return "FieldOpAssign[" + "parent=" + parent + ", " + "accessType=" + accessType + ", " + "field=" + field + ", " + "assignmentOperator=" + assignmentOperator + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Marker for when we need an Expr but we already have a value on the stack. This is used
   * for x += y type expressions which turn into x = _Noop_ + y where _Noop_ is used to mark
   * the place where the x value will be inserted into the binary expressions.
   */
  static class Noop extends Expr {
    Token operator;
    Noop(Token operator) {
      this.operator = operator;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitNoop(this); }
    @Override public String toString() { return "Noop[" + "operator=" + operator + "]"; }
  }

  /**
   * Closure definition
   */
  static class Closure extends Expr implements ManagesResult {
    Token        startToken;
    Expr.FunDecl funDecl;
    boolean      noParamsDefined;
    Closure(Token startToken, Expr.FunDecl funDecl, boolean noParamsDefined) {
      this.startToken = startToken;
      this.funDecl = funDecl;
      this.noParamsDefined = noParamsDefined;
      this.location = startToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitClosure(this); }
    @Override public String toString() { return "Closure[" + "startToken=" + startToken + ", " + "funDecl=" + funDecl + ", " + "noParamsDefined=" + noParamsDefined + "]"; }
  }

  static class Return extends Expr {
    Token      returnToken;
    Expr       expr;
    JactlType returnType;      // Return type of the function we are embedded in
    FunDecl    funDecl;
    Return(Token returnToken, Expr expr, JactlType returnType) {
      this.returnToken = returnToken;
      this.expr = expr;
      this.returnType = returnType;
      this.location = returnToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitReturn(this); }
    @Override public String toString() { return "Return[" + "returnToken=" + returnToken + ", " + "expr=" + expr + ", " + "returnType=" + returnType + "]"; }
  }

  /**
   * Break statement
   */
  static class Break extends Expr {
    Token breakToken;
    Token label;
    Stmt.While whileLoop;
    Break(Token breakToken, Token label) {
      this.breakToken = breakToken;
      this.label = label;
      this.location = breakToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBreak(this); }
    @Override public String toString() { return "Break[" + "breakToken=" + breakToken + ", " + "label=" + label + "]"; }
  }

  /**
   * Continue statement
   */
  static class Continue extends Expr{
    Token continueToken;
    Token label;
    Stmt.While whileLoop;
    Continue(Token continueToken, Token label) {
      this.continueToken = continueToken;
      this.label = label;
      this.location = continueToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitContinue(this); }
    @Override public String toString() { return "Continue[" + "continueToken=" + continueToken + ", " + "label=" + label + "]"; }
  }

  static class Print extends Expr {
    Token printToken;
    Expr  expr;
    boolean newLine;    // Whether to print newline
    Print(Token printToken, Expr expr, boolean newLine) {
      this.printToken = printToken;
      this.expr = expr;
      this.newLine = newLine;
      this.location = printToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitPrint(this); }
    @Override public String toString() { return "Print[" + "printToken=" + printToken + ", " + "expr=" + expr + ", " + "newLine=" + newLine + "]"; }
  }

  static class Die extends Expr {
    Token dieToken;
    Expr  expr;
    Die(Token dieToken, Expr expr) {
      this.dieToken = dieToken;
      this.expr = expr;
      this.location = dieToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitDie(this); }
    @Override public String toString() { return "Die[" + "dieToken=" + dieToken + ", " + "expr=" + expr + "]"; }
  }

  static class Eval extends Expr {
    Token evalToken;
    Expr  script;
    Expr  globals;
    Eval(Token evalToken, Expr script, Expr globals) {
      this.evalToken = evalToken;
      this.script = script;
      this.globals = globals;
      this.location = evalToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitEval(this); }
    @Override public String toString() { return "Eval[" + "evalToken=" + evalToken + ", " + "script=" + script + ", " + "globals=" + globals + "]"; }
  }

  /**
   * Used to turn a list of statements into an expression so that we can support "do {...}":
   *   x < max or do { x++; y-- } and return x + y;
   */
  static class Block extends Expr {
    Token      token;
    Stmt.Block block;
    Block(Token token, Stmt.Block block) {
      this.token = token;
      this.block = block;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBlock(this); }
    @Override public String toString() { return "Block[" + "token=" + token + ", " + "block=" + block + "]"; }
  }

  /**
   * Expr for wrapping a type. Used for instanceof, !instanceof, and as
   */
  static class TypeExpr extends Expr {
    Token      token;
    JactlType typeVal;
    TypeExpr(Token token, JactlType typeVal) {
      this.token = token;
      this.typeVal = typeVal;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitTypeExpr(this); }
    @Override public String toString() { return "TypeExpr[" + "token=" + token + ", " + "typeVal=" + typeVal + "]"; }
  }

  ////////////////////////////////////////////////////////////////////

  // = Used when generating wrapper method

  /**
   * Array length
   */
  static class ArrayLength extends Expr implements ManagesResult {
    Token token;
    Expr array;
    ArrayLength(Token token, Expr array) {
      this.token = token;
      this.array = array;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitArrayLength(this); }
    @Override public String toString() { return "ArrayLength[" + "token=" + token + ", " + "array=" + array + "]"; }
  }

  /**
   * Array get
   */
  static class ArrayGet extends Expr implements ManagesResult {
    Token token;
    Expr  array;
    Expr  index;
    ArrayGet(Token token, Expr array, Expr index) {
      this.token = token;
      this.array = array;
      this.index = index;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitArrayGet(this); }
    @Override public String toString() { return "ArrayGet[" + "token=" + token + ", " + "array=" + array + ", " + "index=" + index + "]"; }
  }

  /**
   * LoadParamValue - load value needed for a param.
   * Can be HeapLocal if isPassedAsHeapLocal is set or just a standard value.
   */
  static class LoadParamValue extends Expr implements ManagesResult {
    Token name;
    Expr.VarDecl paramDecl;
    Expr.VarDecl varDecl;
    LoadParamValue(Token name, Expr.VarDecl paramDecl) {
      this.name = name;
      this.paramDecl = paramDecl;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitLoadParamValue(this); }
    @Override public String toString() { return "LoadParamValue[" + "name=" + name + ", " + "paramDecl=" + paramDecl + "]"; }
  }

  /**
   * Invoke a user function - used for invoking actual function at end of varargs wrapper function
   */
  static class InvokeFunDecl extends Expr implements ManagesResult {
    Token        token;
    Expr.FunDecl funDecl;
    List<Expr>   args;
    InvokeFunDecl(Token token, Expr.FunDecl funDecl, List<Expr> args) {
      this.token = token;
      this.funDecl = funDecl;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInvokeFunDecl(this); }
    @Override public String toString() { return "InvokeFunDecl[" + "token=" + token + ", " + "funDecl=" + funDecl + ", " + "args=" + args + "]"; }
  }

  /**
   * Invoke init method of a class
   */
  static class InvokeInit extends Expr {
    Token            token;
    // Whether to use INVOKESPECIAL or INVOKEVIRTUAL. INVOKESPECIAL needed when invoking super.method().
    boolean          invokeSpecial;
    ClassDescriptor  classDescriptor;
    List<Expr>       args;
    InvokeInit(Token token, boolean invokeSpecial, ClassDescriptor classDescriptor, List<Expr> args) {
      this.token = token;
      this.invokeSpecial = invokeSpecial;
      this.classDescriptor = classDescriptor;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInvokeInit(this); }
    @Override public String toString() { return "InvokeInit[" + "token=" + token + ", " + "invokeSpecial=" + invokeSpecial + ", " + "classDescriptor=" + classDescriptor + ", " + "args=" + args + "]"; }
  }

  /**
   * Invoke an internal utility function
   */
  static class InvokeUtility extends Expr {
    Token       token;
    Class       clss;
    String      methodName;
    List<Class> paramTypes;
    List<Expr>  args;
    InvokeUtility(Token token, Class clss, String methodName, List<Class> paramTypes, List<Expr> args) {
      this.token = token;
      this.clss = clss;
      this.methodName = methodName;
      this.paramTypes = paramTypes;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInvokeUtility(this); }
    @Override public String toString() { return "InvokeUtility[" + "token=" + token + ", " + "clss=" + clss + ", " + "methodName=" + methodName + ", " + "paramTypes=" + paramTypes + ", " + "args=" + args + "]"; }
  }

  /**
   * Create a new instance of a class
   */
  static class InvokeNew extends Expr {
    Token      token;
    JactlType className;
    InvokeNew(Token token, JactlType className) {
      this.token = token;
      this.className = className;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInvokeNew(this); }
    @Override public String toString() { return "InvokeNew[" + "token=" + token + ", " + "className=" + className + "]"; }
  }

  /**
   * Load default value of given type onto stack
   */
  static class DefaultValue extends Expr {
    Token      token;
    JactlType varType;
    DefaultValue(Token token, JactlType varType) {
      this.token = token;
      this.varType = varType;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitDefaultValue(this); }
    @Override public String toString() { return "DefaultValue[" + "token=" + token + ", " + "varType=" + varType + "]"; }
  }

  /**
   * For situations where we know the class name up front and don't want to have to create
   * a new JactlType value for it.
   */
  static class InstanceOf extends Expr {
    Token  token;
    Expr   expr;      // The object for which we are checking the type
    String className; // The internal class name to check for
    InstanceOf(Token token, Expr expr, String className) {
      this.token = token;
      this.expr = expr;
      this.className = className;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInstanceOf(this); }
    @Override public String toString() { return "InstanceOf[" + "token=" + token + ", " + "expr=" + expr + ", " + "className=" + className + "]"; }
  }

  /**
   * Cast to given type
   */
  static class CastTo extends Expr {
    Token      token;
    Expr       expr;      // Object being cast
    JactlType castType;  // Type to cast to
    CastTo(Token token, Expr expr, JactlType castType) {
      this.token = token;
      this.expr = expr;
      this.castType = castType;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitCastTo(this); }
    @Override public String toString() { return "CastTo[" + "token=" + token + ", " + "expr=" + expr + ", " + "castType=" + castType + "]"; }
  }

  /**
   * Used in init method to convert an expression into a user instance type. If the expression
   * is not the right type but is a Map/List we invoke the constructor to convert into the right
   * instance type.
   */
  static class ConvertTo extends Expr {
    Token      token;
    JactlType varType;
    Expr       expr;
    Expr       source;
    Expr       offset;
    ConvertTo(Token token, JactlType varType, Expr expr, Expr source, Expr offset) {
      this.token = token;
      this.varType = varType;
      this.expr = expr;
      this.source = source;
      this.offset = offset;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitConvertTo(this); }
    @Override public String toString() { return "ConvertTo[" + "token=" + token + ", " + "varType=" + varType + ", " + "expr=" + expr + ", " + "source=" + source + ", " + "offset=" + offset + "]"; }
  }

  /**
   * Used for times when we need to get the source or offset value from our params (when needsLocation is set).
   * Since we don't declare formal parameters for these we use this as a special place holder.
   */
  static class SpecialVar extends Expr {
    Token   name;          // $source or $offset (Utils.SOURCE_VAR_NAME, Utils.OFFSET_VAR_NAME)
    FunDecl function;     // Our current function
    SpecialVar(Token name) {
      this.name = name;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitSpecialVar(this); }
    @Override public String toString() { return "SpecialVar[" + "name=" + name + "]"; }
  }

  interface Visitor<T> {
    T visitBinary(Binary expr);
    T visitRegexMatch(RegexMatch expr);
    T visitRegexSubst(RegexSubst expr);
    T visitTernary(Ternary expr);
    T visitPrefixUnary(PrefixUnary expr);
    T visitPostfixUnary(PostfixUnary expr);
    T visitCast(Cast expr);
    T visitCall(Call expr);
    T visitMethodCall(MethodCall expr);
    T visitLiteral(Literal expr);
    T visitListLiteral(ListLiteral expr);
    T visitMapLiteral(MapLiteral expr);
    T visitIdentifier(Identifier expr);
    T visitClassPath(ClassPath expr);
    T visitExprString(ExprString expr);
    T visitVarDecl(VarDecl expr);
    T visitFunDecl(FunDecl expr);
    T visitVarAssign(VarAssign expr);
    T visitVarOpAssign(VarOpAssign expr);
    T visitFieldAssign(FieldAssign expr);
    T visitFieldOpAssign(FieldOpAssign expr);
    T visitNoop(Noop expr);
    T visitClosure(Closure expr);
    T visitReturn(Return expr);
    T visitBreak(Break expr);
    T visitContinue(Continue expr);
    T visitPrint(Print expr);
    T visitDie(Die expr);
    T visitEval(Eval expr);
    T visitBlock(Block expr);
    T visitTypeExpr(TypeExpr expr);
    T visitArrayLength(ArrayLength expr);
    T visitArrayGet(ArrayGet expr);
    T visitLoadParamValue(LoadParamValue expr);
    T visitInvokeFunDecl(InvokeFunDecl expr);
    T visitInvokeInit(InvokeInit expr);
    T visitInvokeUtility(InvokeUtility expr);
    T visitInvokeNew(InvokeNew expr);
    T visitDefaultValue(DefaultValue expr);
    T visitInstanceOf(InstanceOf expr);
    T visitCastTo(CastTo expr);
    T visitConvertTo(ConvertTo expr);
    T visitSpecialVar(SpecialVar expr);
  }
}
