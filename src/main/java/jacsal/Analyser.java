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

import jacsal.runtime.FunctionDescriptor;

import java.util.*;

import static jacsal.JacsalType.ANY;

/**
 * Class that analyses the AST for any final tweaks needed before generating the byte code.
 * At the moment the only thing that is done is to work out which method/function invocations
 * need to be treated as potentially async invocations. This allows us to avoid having to
 * generate a lot of code for saving stack/locals state on each invocation and only do it when
 * we know we are invoking an async function or when we have no way of knowing.
 * The reason for needing another pass over the AST to do this (rather than do it all in Resolver)
 * is we need to know whether some variables are "final" or not and we only know this at the end
 * of the resolve phase.
 */
public class Analyser implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  boolean testAsync = false;         // Used in testing to simulate what happens if every call is potentially async
  private final Deque<Expr.FunDecl> functions = new ArrayDeque<>();
  private final Set<Expr.FunDecl>   isAnalysed = new HashSet<>();
  private final Map<Expr.FunDecl,List<Pair<Expr.Call,Expr.FunDecl>>> callDependencies = new HashMap<>();

  void analyseScript(Stmt.ClassDecl classDecl) {
    analyse(classDecl);

    // If we still have call sites where we don't know if function was async or not due to forward references
    // we now need to work them out. We keep looping and marking functions and call sites as async until there
    // are no more do to. At this point the call dependencies should only refer to other functions in the
    // dependency map so if none of them have yet been marked async then none of them are async.
    while (true) {
      boolean resolvedAsyncCall = false;
      for (Expr.FunDecl caller : callDependencies.keySet()) {
        var callSites    = callDependencies.get(caller);
        var newCallSites = new ArrayList<Pair<Expr.Call, Expr.FunDecl>>();
        for (Pair<Expr.Call, Expr.FunDecl> call : callSites) {
          Expr.Call    callSite = call.first;
          Expr.FunDecl callee   = call.second;
          if (callee.functionDescriptor.isAsync) {
            caller.functionDescriptor.isAsync = true;
            callSite.isAsync = true;
            resolvedAsyncCall = true;
          }
          else {
            // Still don't know so add for next round
            newCallSites.add(call);
          }
        }
        callDependencies.put(caller, newCallSites);
      }
      // If no more async calls resolved then we know that all the rest are sync so nothing more to do
      if (!resolvedAsyncCall) {
        break;
      }
    }
  }

  ////////////////////////////////////////

  Void analyse(Stmt stmt) {
    if (stmt != null) {
      return stmt.accept(this);
    }
    return null;
  }

  Void analyse(Expr expr) {
    if (expr != null) {
      return expr.accept(this);
    }
    return null;
  }

  Void analyse(List nodes) {
    if (nodes != null) {
      nodes.forEach(node -> {
        if (node instanceof Expr) {
          analyse((Expr)node);
        }
        else {
          analyse((Stmt)node);
        }
      });
    }
    return null;
  }

  ///////////////////////////////////////

  // = Expr

  @Override public Void visitCall(Expr.Call expr) {
    analyse(expr.callee);
    analyse(expr.args);
    final var currentFunction = functions.peek();
    var funDecl = getFunDecl(expr.callee);

    if (funDecl != null) {
      if (!isAnalysed.contains(funDecl) && !funDecl.functionDescriptor.isBuiltin) {
        // Forward reference to a function so we don't yet know if it will be async or not.
        // Add ourselves to dependency map so we can be re-analysed at the end when we will
        // hopefully know whether callee is async or not.
        addCallDependency(currentFunction, expr, funDecl);
        return null;
      }

      // If function we are invoking is async then we are async
      if (isAsync(funDecl.functionDescriptor, null, expr.args)) {
        currentFunction.functionDescriptor.isAsync = true;
        expr.isAsync = true;
      }
      return null;
    }

    // If we don't know whether we are invoking an async function or not then assume the worst
    currentFunction.functionDescriptor.isAsync = true;
    expr.isAsync = true;
    return null;
  }

  @Override public Void visitMethodCall(Expr.MethodCall expr) {
    analyse(expr.parent);
    analyse(expr.args);
    final var currentFunction = functions.peek();
    if (expr.methodDescriptor != null) {
      // If we are invoking a method that is marked as async then we need to mark ourselves as async
      // so that callers to us (the current function) can know to add code for handling suspend/resume
      // with Continuations.
      if (isAsync(expr.methodDescriptor, expr.parent, expr.args)) {
        currentFunction.functionDescriptor.isAsync = true;
        expr.isAsync = true;
      }
    }
    else {
      // Assume that what we are invoking is async since we have know way of knowing at compile time when
      // invoking through a function value or when doing run-time lookup of the method name
      currentFunction.functionDescriptor.isAsync = true;
      expr.isAsync = true;
    }
    return null;
  }

  @Override public Void visitBinary(Expr.Binary expr) {
    analyse(expr.left);
    analyse(expr.right);
    return null;
  }

  @Override public Void visitRegexMatch(Expr.RegexMatch expr) {
    analyse(expr.string);
    analyse(expr.pattern);
    return null;
  }

  @Override public Void visitRegexSubst(Expr.RegexSubst expr) {
    return analyse(expr.replace);
  }

  @Override public Void visitTernary(Expr.Ternary expr) {
    analyse(expr.first);
    analyse(expr.second);
    analyse(expr.third);
    return null;
  }

  @Override public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    return analyse(expr.expr);
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    return analyse(expr.expr);
  }

  @Override public Void visitLiteral(Expr.Literal expr) { return null; }

  @Override public Void visitListLiteral(Expr.ListLiteral expr) {
    analyse(expr.exprs);
    return null;
  }

  @Override public Void visitMapLiteral(Expr.MapLiteral expr) {
    expr.entries.forEach(entry -> {
      analyse(entry.first);
      analyse(entry.second);
    });
    return null;
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) { return null; }

  @Override public Void visitExprString(Expr.ExprString expr) {
    analyse(expr.exprList);
    return null;
  }

  @Override public Void visitVarDecl(Expr.VarDecl expr) {
    analyse(expr.initialiser);
    return null;
  }

  @Override public Void visitFunDecl(Expr.FunDecl funDecl) {
    // Note: expr.parameters are analysed because they are statements in the block so don't analyse twice

    Expr.FunDecl expr = funDecl;

    // If we have a wrapper then resolve it. Note that it has an embedded FunDecl for us so we will
    // get analysed as part of its statement block. This means we need to check next time not to
    // analyse wrapper if we are already analysiing it.
    if (funDecl.wrapper != null && functions.peek() != funDecl.wrapper) {
      expr = funDecl.wrapper;
    }

    functions.push(expr);
    try {
      analyse(expr.block);
    } finally {
      functions.pop();
    }

    // If we were the wrapper and we are async then function is also marked as async.
    // If we were cleverer we could track better when invoking wrapper and wouldn't need
    // to be so conservative about this...
    if (expr != funDecl) {
      if (expr.functionDescriptor.isAsync) {
        funDecl.functionDescriptor.isAsync = true;
      }
    }
    else {
      // Add function (not wrapper) to set of analysed functions as long as we don't have any unresolved dependencies
      if (!callDependencies.containsKey(funDecl)) {
        isAnalysed.add(funDecl);
      }
    }

    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    analyse(expr.identifierExpr);
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitVarOpAssign(Expr.VarOpAssign expr) {
    analyse(expr.identifierExpr);
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitFieldAssign(Expr.FieldAssign expr) {
    analyse(expr.parent);
    analyse(expr.field);
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitFieldOpAssign(Expr.FieldOpAssign expr) {
    analyse(expr.parent);
    analyse(expr.field);
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitNoop(Expr.Noop expr) { return null; }

  @Override public Void visitClosure(Expr.Closure expr) {
    return analyse(expr.funDecl);
  }

  @Override public Void visitReturn(Expr.Return expr) {
    return analyse(expr.expr);
  }

  @Override public Void visitBreak(Expr.Break expr) { return null; }
  @Override public Void visitContinue(Expr.Continue expr) { return null; }
  @Override public Void visitPrint(Expr.Print expr) {
    return analyse(expr.expr);
  }

  @Override public Void visitBlock(Expr.Block expr) {
    return analyse(expr.block);
  }

  @Override public Void visitArrayLength(Expr.ArrayLength expr) {
    return analyse(expr.array);
  }

  @Override public Void visitArrayGet(Expr.ArrayGet expr) {
    analyse(expr.array);
    analyse(expr.index);
    return null;
  }

  @Override public Void visitLoadParamValue(Expr.LoadParamValue expr) {
    return analyse(expr.paramDecl);
  }

  @Override public Void visitInvokeFunction(Expr.InvokeFunction expr) {
    analyse(expr.args);
    return null;
  }

  @Override public Void visitInvokeUtility(Expr.InvokeUtility expr) {
    analyse(expr.args);
    return null;
  }

  @Override public Void visitInvokeNew(Expr.InvokeNew expr)       { return null; }
  @Override public Void visitClassPath(Expr.ClassPath expr)       { return null; }
  @Override public Void visitDefaultValue(Expr.DefaultValue expr) { return null; }

  ////////////////////////////////////

  // = Stmt

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    return analyse(stmt.stmts);
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    return analyse(stmt.stmts);
  }

  @Override public Void visitIf(Stmt.If stmt) {
    analyse(stmt.condition);
    analyse(stmt.trueStmt);
    analyse(stmt.falseStmt);
    return null;
  }

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    analyse(stmt.classBlock);
    analyse(stmt.scriptMain);
    return null;
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    return analyse(stmt.declExpr);
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    return analyse(stmt.declExpr);
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    analyse(stmt.condition);
    analyse(stmt.updates);
    analyse(stmt.body);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    return analyse(stmt.expr);
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    return analyse(stmt.expr);
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    analyse(stmt.source);
    analyse(stmt.offset);
    return null;
  }

  ///////////////////////////////////////

  private Expr.FunDecl getFunDecl(Expr expr) {
    if (expr == null) { return null; }
    if (expr instanceof Expr.Identifier) {
      Expr.Identifier callee = (Expr.Identifier)expr;
      if (callee.varDecl == null)         { return null; }
      if (!callee.varDecl.isFinal)        { return null; }
      if (callee.varDecl.funDecl != null) { return callee.varDecl.funDecl; }

      if (callee.varDecl.initialiser != null) {
        return getFunDecl(callee.varDecl.initialiser);
      }

      Expr.VarDecl decl = callee.varDecl;
      while (decl.parentVarDecl != null && decl.isFinal) {
        decl = decl.parentVarDecl;
      }
      if (!decl.isFinal) { return null; }
      return getFunDecl(decl.initialiser);
    }

    if (expr instanceof Expr.Closure) {
      return ((Expr.Closure)expr).funDecl;
    }
    return null;
  }

  /**
   * Determine if current call/methodCall potentially calls something that does an async operation.
   * @param arg0  for method calls this is the target object, for function calls this is null
   * @param args  the remaining arguments
   * @return true if call should be treated as async
   */
  private boolean isAsync(FunctionDescriptor func, Expr arg0, List<Expr> args) {
    if (testAsync)                  { return true;  }
    if (!func.isAsync)              { return false; }
    if (func.asyncArgs.size() == 0) {
      // If function has not specified any async args but has flagged itself as async then all calls to it are async
      return true;
    }

    // If function is only async if passed an async arg then check the args. Note that index 0 means the object
    // on whom we are peforming the method call. Other arguments start at index 1 so args.get(index-1) gets the
    // arg value for that index.
    return func.asyncArgs.stream()
                         .map(i -> i == 0 ? arg0 : (args.size() > 0 ? args.get(i-1) : null))
                         .anyMatch(this::isAsyncArg);
  }

  private boolean isAsyncArg(Expr arg) {
    if (arg == null || arg instanceof Expr.Noop) {
      return false;
    }
    if (arg instanceof Expr.Identifier) {
      Expr.VarDecl decl = ((Expr.Identifier) arg).varDecl;
      if (decl == null) {
        throw new IllegalStateException("Internal error: Expr.Identifier with null varDecl");
      }
      return isAsyncArg(decl);
    }

    if (arg instanceof Expr.VarDecl) {
      var decl = (Expr.VarDecl)arg;

      // If not final then we can't assume anything about it
      if (!decl.isFinal) {
        return true;
      }

      if (decl.funDecl != null) {
        return isAsyncArg(decl.funDecl);
      }

      if (decl.initialiser != null) {
        return isAsyncArg(decl.initialiser);
      }

      if (decl.originalVarDecl != null) {
        return isAsyncArg(decl.originalVarDecl);
      }

      // If we have a type that can't be async then return false, otherwise return true
      return arg.type.is(ANY);
    }

    // Check for an initialiser that is a closure/function and return its asyncness
    if (arg instanceof Expr.FunDecl) {
      return ((Expr.FunDecl) arg).functionDescriptor.isAsync;
    }

    // Check for a closure
    if (arg instanceof Expr.Closure) {
      return ((Expr.Closure)arg).funDecl.functionDescriptor.isAsync;
    }

    // If arg is itself a function/method call then it is async if the call is async
    if (arg instanceof Expr.Call && ((Expr.Call)arg).isAsync)             { return true; }
    if (arg instanceof Expr.MethodCall && ((Expr.MethodCall)arg).isAsync) { return true; }

    // If type of variable is ANY then assume worst
    if (arg.type.is(ANY)) { return true; }

    return false;
  }

  private void addCallDependency(Expr.FunDecl caller, Expr.Call callSite, Expr.FunDecl callee) {
    var callees = callDependencies.get(caller);
    if (callees == null) {
      callees = new ArrayList<>();
      callDependencies.put(caller, callees);
    }
    callees.add(new Pair(callSite, callee));
  }
}
