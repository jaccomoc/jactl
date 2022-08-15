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

  static class Binary extends Expr {
    Expr  left;
    Token operator;
    Expr  right;
    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
      this.location = operator;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBinary(this); }
    @Override public String toString() { return "Binary[" + "left=" + left + ", " + "operator=" + operator + ", " + "right=" + right + "]"; }
  }

  static class PrefixUnary extends Expr {
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

  static class PostfixUnary extends Expr {
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
    @Override public String toString() { return "ListLiteral[" + "start=" + start + ", " + "exprs=" + exprs + "]"; }
  }

  static class MapLiteral extends Expr {
    Token start;
    List<Pair<Expr,Expr>> entries = new ArrayList<>();
    MapLiteral(Token start) {
      this.start = start;
      this.location = start;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitMapLiteral(this); }
    @Override public String toString() { return "MapLiteral[" + "start=" + start + ", " + "entries=" + entries + "]"; }
  }

  static class Identifier extends Expr {
    Token        identifier;
    Expr.VarDecl varDecl;
    Identifier(Token identifier) {
      this.identifier = identifier;
      this.location = identifier;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitIdentifier(this); }
    @Override public String toString() { return "Identifier[" + "identifier=" + identifier + ", " + "varDecl=" + varDecl + "]"; }
  }

  static class ExprString extends Expr {
    Token exprStringStart;
    List<Expr> exprList = new ArrayList<>();
    ExprString(Token exprStringStart) {
      this.exprStringStart = exprStringStart;
      this.location = exprStringStart;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitExprString(this); }
    @Override public String toString() { return "ExprString[" + "exprStringStart=" + exprStringStart + ", " + "exprList=" + exprList + "]"; }
  }

  /**
   * Variable declaration with optional initialiser.
   * This is done as an expression in case value of the assignment
   * is returned implicitly from a function/closure.
   */
  static class VarDecl extends Expr {
    Token      name;
    Expr       initialiser;
    boolean    isGlobal;    // Whether global (bindings var) or local
    int        slot;        // Which local variable slot to use
    Label      declLabel;   // Where variable comes into scope (for debugger)
    VarDecl(Token name, Expr initialiser) {
      this.name = name;
      this.initialiser = initialiser;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitVarDecl(this); }
    @Override public String toString() { return "VarDecl[" + "name=" + name + ", " + "initialiser=" + initialiser + ", " + "declLabel=" + declLabel + "]"; }
  }

  /**
   * When variable used as lvalue in an assignment
   */
  static class VarAssign extends Expr {
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

  interface Visitor<T> {
    T visitBinary(Binary expr);
    T visitPrefixUnary(PrefixUnary expr);
    T visitPostfixUnary(PostfixUnary expr);
    T visitLiteral(Literal expr);
    T visitListLiteral(ListLiteral expr);
    T visitMapLiteral(MapLiteral expr);
    T visitIdentifier(Identifier expr);
    T visitExprString(ExprString expr);
    T visitVarDecl(VarDecl expr);
    T visitVarAssign(VarAssign expr);
  }
}
