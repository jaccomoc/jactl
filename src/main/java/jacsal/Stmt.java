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
import java.util.LinkedHashMap;

import jacsal.runtime.ClassDescriptor;
import org.objectweb.asm.Label;

/**
 * Stmt classes for our AST.
 */
abstract class Stmt {

  abstract <T> T accept(Visitor<T> visitor);


  Token      location   = null;
  boolean    isResolved = false;

  /**
   * Represents a sequence of statments.
   */
  static class Stmts extends Stmt {
    List<Stmt> stmts = new ArrayList<>();

    int currentIdx;     // Which statement we are currently resolving
    Stmts() {
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitStmts(this); }
    @Override public String toString() { return "Stmts[" + "]"; }
  }

  /**
   * Represents a block of statments. This class represents a scope for any variables
   * declared by statements within the block.
   */
  static class Block extends Stmt {
    Token                    openBrace;
    Stmts                    stmts;
    List<Stmt.FunDecl>       functions  = new ArrayList<>();

    Map<String,Expr.VarDecl> variables  = new LinkedHashMap<>();

    // Used to track which Stmt.Stmts we are currently resolving in case we need to insert a new statement
    // at Resolve time
    Stmt.Stmts               currentResolvingStmts;

    boolean isResolvingParams = false;   // Used during resolution to tell if we are resolving function/closure
                                          // parameters so we can tell when we need to convert a declared parameter
                                          // into one that is passed as a HeapLocal (because it is closed over by
                                          // an initialiser for another parameter of the same function).
    Block(Token openBrace, Stmts stmts) {
      this.openBrace = openBrace;
      this.stmts = stmts;
      this.location = openBrace;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitBlock(this); }
    @Override public String toString() { return "Block[" + "openBrace=" + openBrace + ", " + "stmts=" + stmts + "]"; }
  }

  /**
   * If statement with condition and statement(s) to execute if true
   * and statement(s) to execute if false.
   */
  static class If extends Stmt {
    Token ifToken;
    Expr  condition;
    Stmt  trueStmt;
    Stmt  falseStmt;
    If(Token ifToken, Expr condition, Stmt trueStmt, Stmt falseStmt) {
      this.ifToken = ifToken;
      this.condition = condition;
      this.trueStmt = trueStmt;
      this.falseStmt = falseStmt;
      this.location = ifToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitIf(this); }
    @Override public String toString() { return "If[" + "ifToken=" + ifToken + ", " + "condition=" + condition + ", " + "trueStmt=" + trueStmt + ", " + "falseStmt=" + falseStmt + "]"; }
  }

  /**
   * Class declaration
   */
  static class ClassDecl extends Stmt {
    Token                name;
    String               packageName;
    JacsalType           baseClass;
    boolean              isInterface;
    Stmt.Block           classBlock;
    List<Stmt.FunDecl>   methods = new ArrayList<>();
    Stmt.FunDecl         initMethod;
    List<Stmt.ClassDecl> innerClasses = new ArrayList<>();

    List<List<Expr>>     interfaces = new ArrayList<>();

    Stmt.FunDecl             scriptMain;   // Mainline of script
    Map<String,Expr.VarDecl> fieldVars = new LinkedHashMap<>();
    Expr.VarDecl             thisField;

    // Used by Parser and Resolver
    Deque<Expr.FunDecl>      nestedFunctions = new ArrayDeque<>();

    ClassDescriptor classDescriptor;
    ClassDecl(Token name, String packageName, JacsalType baseClass, boolean isInterface) {
      this.name = name;
      this.packageName = packageName;
      this.baseClass = baseClass;
      this.isInterface = isInterface;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitClassDecl(this); }
    @Override public String toString() { return "ClassDecl[" + "name=" + name + ", " + "packageName=" + packageName + ", " + "baseClass=" + baseClass + ", " + "isInterface=" + isInterface + "]"; }
  }

  /**
   * Variable declaration with optional initialiser. Statement wraps the corresponding
   * Expr type where the work is done.
   */
  static class VarDecl extends Stmt {
    Token        name;
    Expr.VarDecl declExpr;
    VarDecl(Token name, Expr.VarDecl declExpr) {
      this.name = name;
      this.declExpr = declExpr;
      this.location = name;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitVarDecl(this); }
    @Override public String toString() { return "VarDecl[" + "name=" + name + ", " + "declExpr=" + declExpr + "]"; }
  }

  /**
   * Function declaration
   */
  static class FunDecl extends Stmt {
    Token        startToken;   // Either identifier for function decl or start brace for closure
    Expr.FunDecl declExpr;

    // Create a var that points to MethodHandle (which points to wrapper).
    // Exception is when inside wrapper function we don't create var that points to
    // the function since the MethodHandle must go through the wrapper function.
    boolean      createVar = true;
    FunDecl(Token startToken, Expr.FunDecl declExpr) {
      this.startToken = startToken;
      this.declExpr = declExpr;
      this.location = startToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitFunDecl(this); }
    @Override public String toString() { return "FunDecl[" + "startToken=" + startToken + ", " + "declExpr=" + declExpr + "]"; }
  }

  /**
   * While and For loop
   */
  static class While extends Stmt {
    Token whileToken;
    Expr  condition;
    Stmt  body;
    Stmt  updates;       // used for For loops
    Label endLoopLabel;  // where to jump to on break stmt
    Label continueLabel; // where to jump to on a continue stmt
    int   stackDepth;    // depth of stack where while loop is (used by continue/break)
    While(Token whileToken, Expr condition) {
      this.whileToken = whileToken;
      this.condition = condition;
      this.location = whileToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitWhile(this); }
    @Override public String toString() { return "While[" + "whileToken=" + whileToken + ", " + "condition=" + condition + "]"; }
  }

  /**
   * Return statement
   */
  static class Return extends Stmt {
    Token       returnToken;
    Expr.Return expr;
    Return(Token returnToken, Expr.Return expr) {
      this.returnToken = returnToken;
      this.expr = expr;
      this.location = returnToken;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitReturn(this); }
    @Override public String toString() { return "Return[" + "returnToken=" + returnToken + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Statements that are just an expression. This can be used, for example, where there
   * is an expression at the end of a function without having to exlicitly have the "return"
   * keyword to indicate that the expression is the return value of the function.
   * Other types of statements that are just an expression include simple assignments. Since
   * an assignment has a value (the value being assigned), an assignment is actually an expression.
   */
  static class ExprStmt extends Stmt {
    Token exprLocation;
    Expr expr;
    ExprStmt(Token exprLocation, Expr expr) {
      this.exprLocation = exprLocation;
      this.expr = expr;
      this.location = exprLocation;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitExprStmt(this); }
    @Override public String toString() { return "ExprStmt[" + "exprLocation=" + exprLocation + ", " + "expr=" + expr + "]"; }
  }

  /**
   * Internal use only - throw RuntimeError
   */
  static class ThrowError extends Stmt {
    Token token;
    Expr.Identifier source;
    Expr.Identifier offset;
    String msg;
    ThrowError(Token token, Expr.Identifier source, Expr.Identifier offset, String msg) {
      this.token = token;
      this.source = source;
      this.offset = offset;
      this.msg = msg;
      this.location = token;
    }
    @Override <T> T accept(Visitor<T> visitor) { return visitor.visitThrowError(this); }
    @Override public String toString() { return "ThrowError[" + "token=" + token + ", " + "source=" + source + ", " + "offset=" + offset + ", " + "msg=" + msg + "]"; }
  }

  interface Visitor<T> {
    T visitStmts(Stmts stmt);
    T visitBlock(Block stmt);
    T visitIf(If stmt);
    T visitClassDecl(ClassDecl stmt);
    T visitVarDecl(VarDecl stmt);
    T visitFunDecl(FunDecl stmt);
    T visitWhile(While stmt);
    T visitReturn(Return stmt);
    T visitExprStmt(ExprStmt stmt);
    T visitThrowError(ThrowError stmt);
  }
}
