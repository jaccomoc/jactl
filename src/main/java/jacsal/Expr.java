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

////////////////////////////////////////////////////////////////////
// File was generated using GenerateClasses.pl in tools directory
// DO NOT EDIT THIS FILE
////////////////////////////////////////////////////////////////////


import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import jacsal.runtime.FunctionDescriptor;

import static jacsal.JacsalType.HEAPLOCAL;

/**
 * Expr classes for our AST.
 */
abstract class Expr {

  abstract <T> T accept(Visitor<T> visitor);


  Token      location;
  JacsalType type;
  boolean    isConst = false;  // Whether expression consists only of constants
  Object     constValue;       // If expression is only consts then we keep the
                               // result of evaluating the expression during the
                               // resolve phase here.

  boolean    isCallee = false; // Whether we are the callee in a call expression

  // Flag that indicates whether result for the Expr is actually used. Most expressions
  // have their value used, for example, when nested within another expression or when
  // the expression is used as a condition for if/while/for or as assignment to a variable.
  // For some expressions (e.g. the top Expr in an expression statement) the result of the
  // expression is ignored so this flag allows us to know whether to leave the result on
  // the stack or not.
  boolean isResultUsed = true;

  // Marker interface to indicate whether MethodCompiler visitor for that element type
  // handles leaving result or not (based on isResultUsed flag) or whether result management
  // needs to be done for it.
  interface ManagesResult {}

  // Whether expression is a direct function call (and not a closure)
  public boolean isFunctionCall() {
    if (!(this instanceof Expr.Identifier)) { return false; }
    var ident = (Expr.Identifier)this;
    return ident.varDecl.type.is(JacsalType.FUNCTION) && ident.varDecl.funDecl != null;
  }

  static class Binary extends Expr {
    Expr  left;
    Token operator;
    Expr  right;
    boolean createIfMissing = false;  // Used for field access used as lvalues
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

  static class Call extends Expr {
    Token      token;
    Expr       callee;
    List<Expr> args;

    boolean isAsync;   // true if potential async invocation

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
    List<Expr> args;

    FunctionDescriptor methodDescriptor;
    boolean isAsync = false;  // true if potential async invocation

    // True if result of method call becomes the target of the next method call. This is used so
    // that we can allow Iterators to be the result of a list.map() call which is then itself used
    // as the target of another map() call (or call that can operator on an Iterator). Otherwise
    // we need to convert the Iterator into a List since Iterators don't really exist at the Jacsal
    // language level and are only used as an implementation detail for some iteration methods.
    // E.g.: x.map().map().each()
    // This means that the x.map() can return an iterator that is then used in the next .map() which
    // returns another iterator that is used in the .each() call.
    // If the result is not to be used by another method then we convert the Iterator into a List.
    // That way x.map{} which in theory does nothing but might have side effects if the closure has
    // a side effect will still run and cause the side effects to happen even though the end result
    // is not actually used.
    boolean isMethodCallTarget = false;
    MethodCall(Token leftParen, Expr parent, Token accessOperator, String methodName, List<Expr> args) {
      this.leftParen = leftParen;
      this.parent = parent;
      this.accessOperator = accessOperator;
      this.methodName = methodName;
      this.args = args;
      this.location = leftParen;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitMethodCall(this); }
    @Override public String toString() { return "MethodCall[" + "leftParen=" + leftParen + ", " + "parent=" + parent + ", " + "accessOperator=" + accessOperator + ", " + "methodName=" + methodName + ", " + "args=" + args + "]"; }
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
    Token        name;
    Expr         initialiser;
    Expr.FunDecl owner;               // Which function variable belongs to (for local vars)
    boolean      isGlobal;            // Whether global (bindings var) or local
    boolean      isHeapLocal;         // Is this a heap local var
    boolean      isPassedAsHeapLocal; // If we are an explicit parameter and HeapLocal is passed to us from wrapper
    boolean      isParam;             // True if variable is a parameter of function (explicit or implicit)
    boolean      isExplicitParam;     // True if explicit declared parameter of function
    int          slot = -1;           // Which local variable slot
    int          nestingLevel;        // What level of nested function owns this variable (1 is top level)
    Label        declLabel;           // Where variable comes into scope (for debugger)
    Expr.FunDecl funDecl;             // If type is FUNCTION then this is the function declaration
    VarDecl      parentVarDecl;       // If this is a HeapLocal parameter then this is the VarDecl from parent
    VarDecl      originalVarDecl;     // VarDecl for actual original variable declaration

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
    JacsalType         returnType;
    List<Stmt.VarDecl> parameters;
    Stmt.Block         block;

    FunctionDescriptor functionDescriptor;
    Expr.VarDecl       varDecl;            // For the variable that we will create to hold our MethodHandle

    boolean            isWrapper;   // Whether this is the wrapper function or the real one
    Expr.FunDecl       wrapper;     // The wrapper method that handles var arg and named arg invocations

    boolean    isScriptMain = false; // Whether this is the funDecl for the script main function
    boolean    isStatic = false;
    int        closureCount = 0;
    Stmt.While currentWhileLoop;     // Used by Resolver to find target of break/continue stmts

    // Stack of blocks used during Resolver phase to track variables and which scope they
    // are declared in and used during Parser phase to track function declarations so we
    // can handle forward references during Resolver phase
    Deque<Stmt.Block> blocks = new ArrayDeque<>();

    // Which heap locals from our parent we need passed in to us
    LinkedHashMap<String,Expr.VarDecl> heapLocalParams = new LinkedHashMap<>();

    // Remember earliest (in the code) forward reference to us so we can make sure that
    // no variables we close over are declared after that reference
    Token earliestForwardReference;

    public boolean isClosure() { return nameToken == null; }
    FunDecl(Token startToken, Token nameToken, JacsalType returnType, List<Stmt.VarDecl> parameters) {
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
  static class Assign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;
    Assign(Expr parent, Token accessType, Expr field, Token assignmentOperator, Expr expr) {
      this.parent = parent;
      this.accessType = accessType;
      this.field = field;
      this.assignmentOperator = assignmentOperator;
      this.expr = expr;
      this.location = accessType;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitAssign(this); }
    @Override public String toString() { return "Assign[" + "parent=" + parent + ", " + "accessType=" + accessType + ", " + "field=" + field + ", " + "assignmentOperator=" + assignmentOperator + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Used to represent assignment to a field or list element where assignment is done with
   * one of the +=, -=, *=, ... operations
   */
  static class OpAssign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;

    // true if value before operation should be result - used for post inc/dec of fields
    // where we covert to a binary += or -= and then need the before value as the result
    boolean isPreIncOrDec;
    OpAssign(Expr parent, Token accessType, Expr field, Token assignmentOperator, Expr expr) {
      this.parent = parent;
      this.accessType = accessType;
      this.field = field;
      this.assignmentOperator = assignmentOperator;
      this.expr = expr;
      this.location = accessType;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitOpAssign(this); }
    @Override public String toString() { return "OpAssign[" + "parent=" + parent + ", " + "accessType=" + accessType + ", " + "field=" + field + ", " + "assignmentOperator=" + assignmentOperator + ", " + "expr=" + expr + "]"; }
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
    JacsalType returnType;      // Return type of the function we are embedded in
    FunDecl    funDecl;
    Return(Token returnToken, Expr expr, JacsalType returnType) {
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
    Stmt.While whileLoop;
    Break(Token breakToken) {
      this.breakToken = breakToken;
      this.location = breakToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBreak(this); }
    @Override public String toString() { return "Break[" + "breakToken=" + breakToken + "]"; }
  }

  /**
   * Continue statement
   */
  static class Continue extends Expr{
    Token continueToken;
    Stmt.While whileLoop;
    Continue(Token continueToken) {
      this.continueToken = continueToken;
      this.location = continueToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitContinue(this); }
    @Override public String toString() { return "Continue[" + "continueToken=" + continueToken + "]"; }
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
   * Invoke a user function - internal use only
   */
  static class InvokeFunction extends Expr implements ManagesResult {
    Token        token;
    Expr.FunDecl funDecl;
    List<Expr>   args;
    InvokeFunction(Token token, Expr.FunDecl funDecl, List<Expr> args) {
      this.token = token;
      this.funDecl = funDecl;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitInvokeFunction(this); }
    @Override public String toString() { return "InvokeFunction[" + "token=" + token + ", " + "funDecl=" + funDecl + ", " + "args=" + args + "]"; }
  }

  /**
   * Invoke a internal utility function
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

  interface Visitor<T> {
    T visitBinary(Binary expr);
    T visitRegexMatch(RegexMatch expr);
    T visitRegexSubst(RegexSubst expr);
    T visitTernary(Ternary expr);
    T visitPrefixUnary(PrefixUnary expr);
    T visitPostfixUnary(PostfixUnary expr);
    T visitCall(Call expr);
    T visitMethodCall(MethodCall expr);
    T visitLiteral(Literal expr);
    T visitListLiteral(ListLiteral expr);
    T visitMapLiteral(MapLiteral expr);
    T visitIdentifier(Identifier expr);
    T visitExprString(ExprString expr);
    T visitVarDecl(VarDecl expr);
    T visitFunDecl(FunDecl expr);
    T visitVarAssign(VarAssign expr);
    T visitVarOpAssign(VarOpAssign expr);
    T visitAssign(Assign expr);
    T visitOpAssign(OpAssign expr);
    T visitNoop(Noop expr);
    T visitClosure(Closure expr);
    T visitReturn(Return expr);
    T visitBreak(Break expr);
    T visitContinue(Continue expr);
    T visitPrint(Print expr);
    T visitBlock(Block expr);
    T visitArrayLength(ArrayLength expr);
    T visitArrayGet(ArrayGet expr);
    T visitLoadParamValue(LoadParamValue expr);
    T visitInvokeFunction(InvokeFunction expr);
    T visitInvokeUtility(InvokeUtility expr);
  }
}
