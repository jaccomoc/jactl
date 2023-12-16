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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExprDecorator implements Expr.Visitor<Expr>, Stmt.Visitor<Void> {

  Function<Expr,Expr> decorator;

  public ExprDecorator(Function<Expr,Expr> decorator) {
    this.decorator = decorator;
  }

  /////////////////////////////////////////

  Void decorate(Stmt stmt) {
    if (stmt != null) {
      stmt.accept(this);
    }
    return null;
  }

  Expr decorate(Expr expr) {
    if (expr != null) {
      Expr result = decorator.apply(expr.accept(this));
      return result;
    }
    return null;
  }

  List<Expr> decorate(List<Expr> exprs) {
    return exprs.stream().map(this::decorate).collect(Collectors.toList());
  }

  ///////////////////////////////////////

  // = Expr


  @Override public Expr visitStackCast(Expr.StackCast expr) {
    return expr;
  }

  @Override public Expr visitSwitch(Expr.Switch expr) {
    expr.subject = decorate(expr.subject);
    // Don't decorate case statements because not all patterns make sense to decorate since
    // we rely on the MethodCompiler.visitMatch to be able to determine from the raw patterns
    // when we can generate code for a table lookup (for example).
    expr.cases.forEach(c -> c.result = decorate(c.result));
    if (expr.defaultCase != null) {
      expr.defaultCase = decorate(expr.defaultCase);
    }
    return expr;
  }

  @Override
  public Expr visitSwitchCase(Expr.SwitchCase expr) {
    // For the moment don't decorate patterns but once we support patterns with "if" expressions
    // then we should decorate the "if" part
    expr.result = decorate(expr.result);
    return expr;
  }

  @Override public Expr visitExprList(Expr.ExprList expr) {
    expr.exprs = expr.exprs.stream().map(this::decorate).collect(Collectors.toList());
    return expr;
  }

  @Override public Expr visitCall(Expr.Call expr) {
    //expr.callee = decorate(expr.callee);
    expr.args = decorate(expr.args);
    return expr;
  }

  @Override public Expr visitMethodCall(Expr.MethodCall expr) {
    if (expr.parent instanceof Expr.Identifier) {
      String ident = ((Expr.Identifier) expr.parent).identifier.getStringValue();
      // Don't decorate if parent looks like it might be a class name because
      // classes aren't values and can't be wrapped in sleep(0, X) for example.
      if (!Character.isUpperCase(ident.charAt(0))) {
        expr.parent = decorate(expr.parent);
      }
    }
    else {
      expr.parent = decorate(expr.parent);
    }
    expr.args = decorate(expr.args);
    return expr;
  }

  @Override public Expr visitBinary(Expr.Binary expr) {
    expr.left = decorate(expr.left);
    expr.right = decorate(expr.right);
    return expr;
  }

  @Override public Expr visitRegexMatch(Expr.RegexMatch expr) {
    expr.string = decorate(expr.string);
    expr.pattern = decorate(expr.pattern);
    return expr;
  }

  @Override public Expr visitRegexSubst(Expr.RegexSubst expr) {
    expr.replace = decorate(expr.replace);
    return expr;
  }

  @Override public Expr visitTernary(Expr.Ternary expr) {
    expr.first = decorate(expr.first);
    expr.second = decorate(expr.second);
    expr.third = decorate(expr.third);
    return expr;
  }

  @Override public Expr visitPrefixUnary(Expr.PrefixUnary expr) {
    //expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitCast(Expr.Cast expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitPostfixUnary(Expr.PostfixUnary expr) {
    //expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitLiteral(Expr.Literal expr) { return expr; }

  @Override public Expr visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs = decorate(expr.exprs);
    return expr;
  }

  @Override public Expr visitMapLiteral(Expr.MapLiteral expr) {
    if (expr.isNamedArgs) {
      expr.entries = expr.entries.stream().map(entry -> new Pair<>(entry.first, decorate(entry.second))).collect(Collectors.toList());
    }
    else {
      expr.entries = expr.entries.stream().map(entry -> new Pair<>(decorate(entry.first), decorate(entry.second))).collect(Collectors.toList());
    }
    return expr;
  }

  @Override public Expr visitIdentifier(Expr.Identifier expr) { return expr; }

  @Override public Expr visitExprString(Expr.ExprString expr) {
    expr.exprList = decorate(expr.exprList);
    return expr;
  }

  @Override public Expr visitVarDecl(Expr.VarDecl expr) {
    expr.initialiser = decorate(expr.initialiser);
    return expr;
  }

  @Override public Expr visitFunDecl(Expr.FunDecl expr) {
    decorate(expr.block);
    return expr;
  }

  @Override public Expr visitVarAssign(Expr.VarAssign expr) {
    //decorate(expr.identifierExpr);
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitVarOpAssign(Expr.VarOpAssign expr) {
    // Difficult to decorate properly due to assumptions that have been made
    //decorate(expr.identifierExpr);
    //expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitFieldAssign(Expr.FieldAssign expr) {
    expr.parent = decorate(expr.parent);
    expr.field  = decorate(expr.field);
    expr.expr   = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitFieldOpAssign(Expr.FieldOpAssign expr) {
    expr.parent = decorate(expr.parent);
    expr.field  = decorate(expr.field);
    //expr.expr   = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitNoop(Expr.Noop expr) { return expr; }

  @Override public Expr visitClosure(Expr.Closure expr) {
    decorate(expr.funDecl);
    return expr;
  }

  @Override public Expr visitReturn(Expr.Return expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitBreak(Expr.Break expr) { return expr; }
  @Override public Expr visitContinue(Expr.Continue expr) { return expr; }

  @Override public Expr visitPrint(Expr.Print expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitDie(Expr.Die expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitEval(Expr.Eval expr) {
    expr.script  = decorate(expr.script);
    expr.globals = decorate(expr.globals);
    return expr;
  }

  @Override public Expr visitBlock(Expr.Block expr) {
    decorate(expr.block);
    return expr;
  }

  @Override public Expr visitArrayLength(Expr.ArrayLength expr)       { return expr; }
  @Override public Expr visitArrayGet(Expr.ArrayGet expr)             { return expr; }
  @Override public Expr visitLoadParamValue(Expr.LoadParamValue expr) { return expr; }

  @Override public Expr visitInvokeFunDecl(Expr.InvokeFunDecl expr) {
    expr.args = decorate(expr.args);
    return expr;
  }

  @Override public Expr visitInvokeUtility(Expr.InvokeUtility expr) {
    decorate(expr.args);
    return expr;
  }

  @Override public Expr visitInvokeNew(Expr.InvokeNew expr) {
    expr.dimensions = expr.dimensions.stream().map(this::decorate).collect(Collectors.toList());
    return expr;
  }

  @Override public Expr visitClassPath(Expr.ClassPath expr)       { return expr; }
  @Override public Expr visitDefaultValue(Expr.DefaultValue expr) { return expr; }
  @Override public Expr visitTypeExpr(Expr.TypeExpr expr)         { return expr; }

  @Override public Expr visitInstanceOf(Expr.InstanceOf expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitConvertTo(Expr.ConvertTo expr) {
    expr.expr = decorate(expr.expr);
    //expr.source = decorate(expr.source);
    //expr.offset = decorate(expr.offset);
    return expr;
  }

  @Override public Expr visitInvokeInit(Expr.InvokeInit expr) {
    expr.args = decorate(expr.args);
    return expr;
  }

  @Override public Expr visitCheckCast(Expr.CheckCast expr) {
    expr.expr = decorate(expr.expr);
    return expr;
  }

  @Override public Expr visitSpecialVar(Expr.SpecialVar expr) {
    return expr;
  }

  ////////////////////////////////////

  // = Stmt

  void decorateStmts(List<Stmt> stmts) {
    stmts.forEach(this::decorate);
  }

  @Override public Void visitImport(Stmt.Import stmt) {
    return null;
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    decorateStmts(stmt.stmts);
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    decorate(stmt.stmts);
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    stmt.condition = decorate(stmt.condition);
    decorate(stmt.trueStmt);
    decorate(stmt.falseStmt);
    return null;
  }

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    decorate(stmt.classBlock);
    stmt.fieldVars.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(),
                                                                entry -> decorate(entry.getValue())));
    stmt.methods.forEach(this::decorate);
    decorate(stmt.scriptMain);
    return null;
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    decorate(stmt.declExpr);
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    decorate(stmt.declExpr);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    stmt.condition = decorate(stmt.condition);
    decorate(stmt.updates);
    decorate(stmt.body);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    decorate(stmt.expr);
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    stmt.expr = decorate(stmt.expr);
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    decorate(stmt.source);
    decorate(stmt.offset);
    return null;
  }
}
