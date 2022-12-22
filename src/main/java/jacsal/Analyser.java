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
import static jacsal.JacsalType.INSTANCE;

/**
 * Class that analyses the AST for any final tweaks needed before generating the byte code.
 * This includes:
 * <ul>
 * <li>
 *   Working out which method/function invocations need to be treated as potentially async
 *   invocations. This allows us to avoid having to generate a lot of code for saving
 *   stack/locals state on each invocation and only do it when we know we are invoking
 *   an async function or when we have no way of knowing. The reason for needing another
 *   pass over the AST to do this (rather than do it all in Resolver) is we need to know
 *   whether some variables are "final" or not and we only know this at the end of the
 *   resolve phase.
 * </li>
 * <li>
 *   While working out what calls are async we also mark the statements that that calls
 *   belong to as async so that the MethodCompiler can work out when to store results on
 *   the stack (non-async) or in local slots (async). This is because async behaviour
 *   involves catching exceptions which results in any previous stack values being
 *   discarded. By storing state in local vars instead of the stack we can then preserve
 *   these local variables in the Continuation object for later restoration after resume.
 * </li>
 * <li>
 *   Counting how many local slots and maximum stack size is needed per method. This
 *   allows us to work out how big an array we need for storing the local values if/when
 *   we suspend and we need to save our state. Note: we just track the expression results
 *   and local vars and then allow a "safety margin" of (at the moment) 10 additional values
 *   that might get pushed onto stack as part of a function call that the expression might
 *   invoke as part of its implementation.
 *   <p>NOTE: we don't use this functionality at the moment since we do the counting instead
 *      in the MethodCompiler phase but leaving this code here just in case we need it one
 *      day.</p>
 * </li>
 * </ul>
 * <h2>Two Passes</h2>
 * <p>
 *   We use two passes to do this analysis. The first pass finds all call sites and
 *   builds up a dependency map between callers and callees for situations where we don't
 *   know whether the callee is async. At the end of this pass we process the dependencies
 *   to work out what is async and what isn't.
 * </p>
 * <p>
 *   The second pass then flags each Expr as async or not based on the results of the first
 *   pass. This allows us to know when evaluating an Expr during the compile phase whether
 *   it or any child of it will invoke an async function.
 * </p>
 */
public class Analyser implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  boolean testAsync = false;         // Used in testing to simulate what happens if every call is potentially async

  private final Deque<Stmt.ClassDecl>   classStack        = new ArrayDeque<>();

  private final Map<Expr.FunDecl,List<Pair<Expr,FunctionDescriptor>>> callDependencies = new HashMap<>();

  private final JacsalContext context;

  private final Deque<Expr> currentExpr = new ArrayDeque<>();
  private final Deque<Stmt> currentStmt = new ArrayDeque<>();

  private boolean isFirstPass = true;       // True if doing first pass through

  Analyser(JacsalContext context) {
    this.context = context;
  }

  void analyseScript(Stmt.ClassDecl classDecl) {
    isFirstPass = true;
    analyse(classDecl);

    // If we still have call sites where we don't know if function was async or not due to forward references
    // we now need to work them out. We keep looping and marking functions and call sites as async until there
    // are no more do to. At this point the call dependencies should only refer to other functions in the
    // dependency map so if none of them have yet been marked async then none of them are async.
    while (true) {
      boolean resolvedAsyncCall = false;
      for (var caller : callDependencies.keySet()) {
        var callSites    = callDependencies.get(caller);
        var newCallSites = new ArrayList<Pair<Expr,FunctionDescriptor>>();
        for (var call: callSites) {
          var callSite = call.first;
          var callee   = call.second;
          if (callee.isAsync != null) {
            if (callee.isAsync) {
              caller.functionDescriptor.isAsync = true;
              callSite.isAsync = true;
              resolvedAsyncCall = true;
            }
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

    // Mark all remaining functions as not async
    for (var callSites: callDependencies.values()) {
      for (var callSite: callSites) {
        callSite.second.isAsync = false;
      }
    }

    isFirstPass = false;
    analyse(classDecl);
  }

  ////////////////////////////////////////

  Void analyse(Stmt stmt) {
    if (stmt != null) {
      try {
        currentStmt.push(stmt);
        stmt.accept(this);
      }
      finally {
        currentStmt.pop();
        Stmt parent = currentStmt.peek();
        if (parent != null && stmt.isAsync) {
          parent.isAsync = true;
        }
      }
    }
    return null;
  }

  Void analyse(Expr expr) {
    if (expr != null) {
      try {
        currentExpr.push(expr);
        expr.accept(this);
        if (getFunctions().peek() != null) {
          allocateLocals(1);  // For the result
          if (!expr.isResultUsed) {
            freeLocals(1);    // Result not used
          }
        }
        return null;
      }
      finally {
        currentExpr.pop();
        var parent = currentExpr.peek();
        var stmt   = currentStmt.peek();
        if (parent != null && expr.isAsync) {
          parent.isAsync = true;
        }
        if (stmt != null && expr.isAsync) {
          stmt.isAsync = true;
        }
      }
    }
    return null;
  }

  Void analyse(List<Stmt> nodes) {
    if (nodes != null) {
      nodes.forEach(this::analyse);
    }
    return null;
  }

  ///////////////////////////////////////

  // = Expr

  @Override public Void visitCall(Expr.Call expr) {
    analyse(expr.callee);
    expr.args.forEach(this::analyse);
    var function = getFunction(expr.callee);

    if (function != null) {
      if (function.isAsync == null) {
        assert isFirstPass;
        // Forward reference to a function so we don't yet know if it will be async or not.
        // Add ourselves to dependency map so we can be re-analysed at the end when we will
        // hopefully know whether callee is async or not.
        addCallDependency(currentFunction(), expr, function);
      }
      else {
        // If function we are invoking is async then we are async
        if (isAsync(function, null, expr.args)) {
          async(expr);
        }
      }
    }
    else {
      // If we don't know whether we are invoking an async function or not then assume the worst
      async(expr);
    }

    freeLocals(1 + expr.args.size());
    return null;
  }

  @Override public Void visitMethodCall(Expr.MethodCall expr) {
    analyse(expr.parent);
    expr.args.forEach(this::analyse);
    if (expr.methodDescriptor != null) {
      if (expr.methodDescriptor.isAsync == null) {
        assert isFirstPass;
        addCallDependency(currentFunction(), expr, expr.methodDescriptor);
      }
      else {
        // If we are invoking a method that is marked as async then we need to mark ourselves as async
        // so that callers to us (the current function) can know to add code for handling suspend/resume
        // with Continuations.
        if (isAsync(expr.methodDescriptor, expr.parent, expr.args)) {
          async(expr);
        }
      }
    }
    else {
      // Assume that what we are invoking is async since we have know way of knowing at compile time when
      // invoking through a function value or when doing run-time lookup of the method name
      async(expr);
    }
    freeLocals(1 + expr.args.size());
    return null;
  }

  @Override public Void visitBinary(Expr.Binary expr) {
    analyse(expr.left);
    analyse(expr.right);
    if (expr.createIfMissing) {
      // Async if class init for field is async
      if (expr.isFieldAccess) {
        if (expr.type.is(INSTANCE)) {
          asyncIfTypeIsAsync(expr);
        }
      }
      else {
        // We have no idea of the field type since we will find out at runtime so have to assume async just in case
        async(expr);
      }
    }

    freeLocals(2);       // Two input values
    return null;
  }

  @Override public Void visitRegexMatch(Expr.RegexMatch expr) {
    analyse(expr.string);
    analyse(expr.pattern);
    freeLocals(expr.string == null ? 1 : 2);
    return null;
  }

  @Override public Void visitRegexSubst(Expr.RegexSubst expr) {
    analyse(expr.string);
    analyse(expr.pattern);
    analyse(expr.replace);
    freeLocals(3);     // 3 input values
    return null;
  }

  @Override public Void visitTernary(Expr.Ternary expr) {
    analyse(expr.first);
    analyse(expr.second);
    analyse(expr.third);
    freeLocals(3);     // 3 input values
    return null;
  }

  @Override public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    return null;
  }

  @Override public Void visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs.forEach(e -> {
      analyse(e);
      freeLocals(1);
    });
    return null;
  }

  @Override public Void visitMapLiteral(Expr.MapLiteral expr) {
    expr.entries.forEach(entry -> {
      analyse(entry.first);
      analyse(entry.second);
      freeLocals(2);
    });
    return null;
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) {
    return null;
  }

  @Override public Void visitExprString(Expr.ExprString expr) {
    expr.exprList.forEach(e -> {
      analyse(e);
      freeLocals(1);
    });
    return null;
  }

  @Override public Void visitVarDecl(Expr.VarDecl expr) {
    analyse(expr.initialiser);
    if (expr.initialiser != null) {
      freeLocals(1);
    }
    if (!expr.isGlobal) {
      allocateLocals(1);
    }
    return null;
  }

  @Override public Void visitFunDecl(Expr.FunDecl funDecl) {
    // Note: expr.parameters are analysed because they are statements in the block so don't analyse twice

    Expr.FunDecl expr = funDecl;

    // If we have a wrapper then resolve it. Note that it has an embedded FunDecl for us so we will
    // get analysed as part of its statement block. This means we need to check next time not to
    // analyse wrapper if we are already analysiing it.
    if (funDecl.wrapper != null && getFunctions().peek() != funDecl.wrapper) {
      expr = funDecl.wrapper;
    }

    getFunctions().push(expr);
    analyse(expr.block);
    assert funDecl.localsCnt == 0;
    getFunctions().pop();

    // If still not marked async then we must be sync...
    if (expr.functionDescriptor.isAsync == null) {
      if (!isFirstPass) {
        expr.functionDescriptor.isAsync = false;
      }
    }

    // If we are the wrapper
    if (expr != funDecl) {
      // If we were the wrapper and we are async then function is also marked as async.
      // If we were cleverer we could track better when invoking wrapper and wouldn't need
      // to be so conservative about this...
      if (expr.functionDescriptor.isAsync != null && expr.functionDescriptor.isAsync) {
        funDecl.functionDescriptor.isAsync = true;
      }

      if (expr.varDecl != null && !expr.varDecl.isGlobal) {
        allocateLocals(1);
      }
    }

    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    analyse(expr.identifierExpr);
    analyse(expr.expr);
    freeLocals(2);
    return null;
  }

  @Override public Void visitVarOpAssign(Expr.VarOpAssign expr) {
    analyse(expr.identifierExpr);
    analyse(expr.expr);
    freeLocals(2);
    return null;
  }

  @Override public Void visitFieldAssign(Expr.FieldAssign expr) {
    analyse(expr.parent);
    analyse(expr.field);
    analyse(expr.expr);
    if (expr.expr.isAsync) {
      async(expr);
    }
    if (expr.type.is(INSTANCE)) {
      asyncIfTypeIsAsync(expr);
    }
    freeLocals(3);
    return null;
  }

  @Override public Void visitFieldOpAssign(Expr.FieldOpAssign expr) {
    analyse(expr.parent);
    analyse(expr.field);
    analyse(expr.expr);
    freeLocals(3);
    return null;
  }

  @Override public Void visitNoop(Expr.Noop expr) {
    return null;
  }

  @Override public Void visitClosure(Expr.Closure expr) {
    analyse(expr.funDecl);
    freeLocals(1);
    return null;
  }

  @Override public Void visitReturn(Expr.Return expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitBreak(Expr.Break expr) { return null; }
  @Override public Void visitContinue(Expr.Continue expr) { return null; }

  @Override public Void visitPrint(Expr.Print expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitBlock(Expr.Block expr) {
    analyse(expr.block);
    return null;
  }

  @Override public Void visitArrayLength(Expr.ArrayLength expr) {
    analyse(expr.array);
    freeLocals(1);
    return null;
  }

  @Override public Void visitArrayGet(Expr.ArrayGet expr) {
    analyse(expr.array);
    analyse(expr.index);
    freeLocals(2);
    return null;
  }

  @Override public Void visitLoadParamValue(Expr.LoadParamValue expr) {
    analyse(expr.paramDecl);
    freeLocals(1);
    return null;
  }

  @Override public Void visitInvokeFunDecl(Expr.InvokeFunDecl expr) {
    expr.args.forEach(this::analyse);
    freeLocals(expr.args.size());
    return null;
  }

  @Override public Void visitInvokeUtility(Expr.InvokeUtility expr) {
    expr.args.forEach(this::analyse);
    freeLocals(expr.args.size());
    return null;
  }

  @Override public Void visitInvokeNew(Expr.InvokeNew expr) {
    return null;
  }

  @Override public Void visitClassPath(Expr.ClassPath expr) { return null; }

  @Override public Void visitTypeExpr(Expr.TypeExpr expr)   {
    return null;
  }

  @Override public Void visitDefaultValue(Expr.DefaultValue expr) {
    return null;
  }

  @Override public Void visitInstanceOf(Expr.InstanceOf expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitConvertTo(Expr.ConvertTo expr) {
    analyse(expr.expr);
    analyse(expr.source);
    analyse(expr.offset);
    freeLocals(3);
    return null;
  }

  @Override public Void visitInvokeFunction(Expr.InvokeFunction expr) {
    expr.args.forEach(this::analyse);
    freeLocals(expr.args.size());
    return null;
  }

  @Override public Void visitCastTo(Expr.CastTo expr) {
    analyse(expr.expr);
    freeLocals(1);
    return null;
  }

  ////////////////////////////////////

  // = Stmt

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    return analyse(stmt.stmts);
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    analyse(stmt.stmts);
    int nonGlobals = (int)stmt.variables.values().stream().filter(v -> !v.isGlobal).count();
    freeLocals(nonGlobals);
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    analyse(stmt.condition);
    freeLocals(1);
    analyse(stmt.trueStmt);
    analyse(stmt.falseStmt);
    return null;
  }

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    stmt.nestedFunctions = new ArrayDeque<>();
    classStack.push(stmt);
    try {
      analyse(stmt.classBlock);
      analyse(stmt.scriptMain);
    }
    finally {
      classStack.pop();
    }
    return null;
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    analyse(stmt.declExpr);
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    analyse(stmt.declExpr);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    analyse(stmt.condition);
    freeLocals(1);
    analyse(stmt.updates);
    analyse(stmt.body);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    analyse(stmt.expr);
    freeLocals(1);
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    analyse(stmt.expr);
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    analyse(stmt.source);
    analyse(stmt.offset);
    freeLocals(2);
    return null;
  }

  ///////////////////////////////////////

  private void async(Expr expr) {
    currentFunction().functionDescriptor.isAsync = true;
    expr.isAsync = true;
  }

  private void asyncIfTypeIsAsync(Expr expr) {
    if (!expr.type.is(INSTANCE)) { return; }
    var classDescriptor = expr.type.getClassDescriptor();
    var existingClass = context.getClassDescriptor(classDescriptor.getInternalName());
    if (existingClass != null) {
      if (existingClass.getInitMethod().isAsync) {
        async(expr);
      }
    }
    else {
      if (classDescriptor.getInitMethod().isAsync == null) {
        assert isFirstPass;
        addCallDependency(currentFunction(), expr, classDescriptor.getInitMethod());
      }
      else
      if (classDescriptor.getInitMethod().isAsync) {
        async(expr);
      }
    }
  }

  private FunctionDescriptor getFunction(Expr expr) {
    var funDecl = getFunDecl(expr);
    if (funDecl == null) {
      return null;
    }
    return funDecl.functionDescriptor;
  }

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
      Boolean isAsync = ((Expr.FunDecl) arg).functionDescriptor.isAsync;
      if (isAsync == null) {
        assert isFirstPass;
      }
      return isAsync != null && isAsync;
    }

    // Check for a closure
    if (arg instanceof Expr.Closure) {
      Boolean isAsync = ((Expr.Closure) arg).funDecl.functionDescriptor.isAsync;
      if (isAsync == null) {
        assert isFirstPass;
      }
      return isAsync != null && isAsync;
    }

    // If arg is itself a function/method call then it is async if the call is async
    if (arg instanceof Expr.Call && ((Expr.Call)arg).isAsync)             { return true; }
    if (arg instanceof Expr.MethodCall && ((Expr.MethodCall)arg).isAsync) { return true; }

    // If type of variable is ANY then assume worst
    if (arg.type.is(ANY)) { return true; }

    return false;
  }

  private void addCallDependency(Expr.FunDecl caller, Expr callSite, FunctionDescriptor callee) {
    var callees = callDependencies.get(caller);
    if (callees == null) {
      callees = new ArrayList<>();
      callDependencies.put(caller, callees);
    }
    callees.add(new Pair(callSite, callee));
  }

  private Expr.FunDecl currentFunction() {
    return getFunctions().peek();
  }

  private Deque<Expr.FunDecl> getFunctions() {
    return classStack.peek().nestedFunctions;
  }

  private void freeLocals(int n) {
    if (getFunctions().peek() != null) {
      getFunctions().peek().freeLocals(n);
    }
  }

  private void allocateLocals(int n) {
    if (getFunctions().peek() != null) {
      getFunctions().peek().allocateLocals(n);
    }
  }
}
