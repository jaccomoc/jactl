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
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.objectweb.asm.Label;

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
    return this instanceof Expr.Identifier &&
           ((Expr.Identifier) this).varDecl.type.is(JacsalType.FUNCTION) &&
           ((Expr.Identifier) this).varDecl.funDecl != null;
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
    Call(Token token, Expr callee, List<Expr> args) {
      this.token = token;
      this.callee = callee;
      this.args = args;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitCall(this); }
    @Override public String toString() { return "Call[" + "token=" + token + ", " + "callee=" + callee + ", " + "args=" + args + "]"; }
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
    Token        identifier;
    Expr.VarDecl varDecl;
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
    boolean      isGlobal = false; // Whether global (bindings var) or local
    boolean      isHeap = false;   // Local vars that are closed over are push to heap
    boolean      isParam = false;  // True if variable is a parameter of function
    int          slot;             // Which local variable slot
    int          nestingLevel;     // What level of nested function owns this variable (1 is top level)
    Label        declLabel;        // Where variable comes into scope (for debugger)
    Expr.FunDecl owner;            // Which function variable belongs to (for local vars)
    Expr.FunDecl funDecl;          // If type is FUNCTION then this is the function declaration
    VarDecl      varDecl;          // If this is a HeapVar parameter then this is the original VarDecl
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
    Token              name;         // Null for closures and script main
    JacsalType         returnType;
    List<Stmt.VarDecl> parameters;
    Stmt.Block         block;

    String             methodName;  // Name of method that we compile into
    Expr.VarDecl       varDecl;     // For the variable that we will create to hold our MethodHandle

    boolean    isStatic = false;
    int        closureCount = 0;
    Stmt.While currentWhileLoop;     // Used by Resolver to find target of break/continue stmts

    // Stack of blocks used during Resolver phase to track variables and which scope they
    // are declared in and used during Parser phase to track function declarations so we
    // can handle forward references during Resolver phase
    Deque<Stmt.Block> blocks = new ArrayDeque<>();

    // Which heap locals from our parent we need passed in to us
    LinkedHashMap<String,Expr.VarDecl> heapVars = new LinkedHashMap<>();

    FunDecl(Token startToken, Token name, JacsalType returnType, List<Stmt.VarDecl> parameters) {
      this.startToken = startToken;
      this.name = name;
      this.returnType = returnType;
      this.parameters = parameters;
      this.location = startToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitFunDecl(this); }
    @Override public String toString() { return "FunDecl[" + "startToken=" + startToken + ", " + "name=" + name + ", " + "returnType=" + returnType + ", " + "parameters=" + parameters + "]"; }
    public boolean isClosure() { return name == null; }
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
    Binary     expr;
    VarOpAssign(Identifier identifierExpr, Token operator, Binary expr) {
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
  static class Closure extends Expr {
    Expr.FunDecl funDecl;
    Closure(Expr.FunDecl funDecl) {
      this.funDecl = funDecl;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitClosure(this); }
    @Override public String toString() { return "Closure[" + "funDecl=" + funDecl + "]"; }
  }

  interface Visitor<T> {
    T visitBinary(Binary expr);
    T visitPrefixUnary(PrefixUnary expr);
    T visitPostfixUnary(PostfixUnary expr);
    T visitCall(Call expr);
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
  }
}
