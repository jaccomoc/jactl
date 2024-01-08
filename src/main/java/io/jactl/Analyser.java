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

import io.jactl.runtime.ClassDescriptor;
import io.jactl.runtime.FunctionDescriptor;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

import static io.jactl.JactlType.ANY;
import static io.jactl.JactlType.INSTANCE;

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
 * <h2>Parent Expr and Stmt</h2>
 * <p>While analysing Expr and Stmt objects, if an Expr turns out to be async then we
 * automatically mark its parent Expr or Stmt (if one exists) as async so we don't need to
 * check this in each of the visitXXX() methods.</p>
 */
public class Analyser implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  boolean testAsync = false;         // Used in testing to simulate what happens if every call is potentially async

  public static int debugLevel = 0;
  static SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm:ss.SSS");
  private static void debug(String msg) {
    if (debugLevel > 0) {
      _log("debug", msg);
    }
  }
  private static void _log(String level, String msg) {
    System.out.println("[" + level + "]:" + Thread.currentThread().getName() + ":" + timeFmt.format(new Date()) + ": " + msg);
  }

  private final Deque<Stmt.ClassDecl>   classStack        = new ArrayDeque<>();

  private final Map<Expr.FunDecl,List<Pair<Expr,FunctionDescriptor>>> asyncCallDependencies     = new HashMap<>();

  private final JactlContext context;

  private final Deque<Expr> currentExpr = new ArrayDeque<>();
  private final Deque<Stmt> currentStmt = new ArrayDeque<>();

  private boolean isFirstPass = true;       // True if doing first pass through

  public Analyser(JactlContext context) {
    this.context = context;
  }

  public void analyseClass(Stmt.ClassDecl classDecl) {
    isFirstPass = true;
    analyse(classDecl);

    resolveAsyncDependencies();

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
        return null;
      }
      finally {
        currentExpr.pop();
        Expr parent = currentExpr.peek();
        Stmt stmt   = currentStmt.peek();
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


  @Override public Void visitStackCast(Expr.StackCast expr) {
    return null;
  }

  @Override public Void visitSwitch(Expr.Switch expr) {
    analyse(expr.subject);
    expr.cases.forEach(this::analyse);
    if (expr.defaultCase != null) {
      analyse(expr.defaultCase);
    }
    return null;
  }

  @Override public Void visitSwitchCase(Expr.SwitchCase expr) {
    expr.patterns.forEach(p -> {
      analyse(p.first);
      analyse(p.second);
    });
    analyse(expr.result);
    return null;
  }

  @Override public Void visitConstructorPattern(Expr.ConstructorPattern expr) {
    analyse(expr.typeExpr);
    ((Expr.MapLiteral)expr.args).entries.forEach(p -> {
      analyse(p.first);
      analyse(p.second);
    });
    return null;
  }

  @Override public Void visitExprList(Expr.ExprList expr) {
    throw new UnsupportedOperationException("Internal error: expression lists not supported");
  }

  @Override public Void visitCall(Expr.Call expr) {
    analyse(expr.callee);
    expr.args.forEach(this::analyse);
    Expr.FunDecl       funDecl  = getFunDecl(expr.callee);
    FunctionDescriptor function = getFunction(expr.callee);

    if (function != null) {
      if (function.isAsync == null) {
        assert isFirstPass;
        // Forward reference to a function, so we don't yet know if it will be async or not.
        // Add ourselves to dependency map, so we can be re-analysed at the end when we will
        // hopefully know whether callee is async or not.
        addAsyncCallDependency(currentFunction(), expr, function);
      }
      else {
        // If function we are invoking is async then we are async
        if (isAsync(function, null, expr.args)) {
          async(expr);
        }
      }
      if (!function.isBuiltin) {
        resolveHeapLocals(currentFunction(), Utils.isInvokeWrapper(expr, function) ? funDecl.wrapper : funDecl);
      }
    }
    else {
      // If we don't know whether we are invoking an async function or not then assume the worst
      async(expr);
    }

    return null;
  }

  @Override public Void visitMethodCall(Expr.MethodCall expr) {
    analyse(expr.parent);
    expr.args.forEach(this::analyse);
    if (expr.methodDescriptor != null) {
      if (expr.methodDescriptor.isAsync == null) {
        assert isFirstPass;
        addAsyncCallDependency(currentFunction(), expr, expr.methodDescriptor);
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
    return null;
  }

  @Override public Void visitBinary(Expr.Binary expr) {
    analyse(expr.left);
    analyse(expr.right);

    // If auto-creation and we allow async initialisers (which by default we don't)
    if (expr.createIfMissing && context.autoCreateAsync()) {
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
    return null;
  }

  @Override public Void visitRegexMatch(Expr.RegexMatch expr) {
    analyse(expr.string);
    analyse(expr.pattern);
    return null;
  }

  @Override public Void visitRegexSubst(Expr.RegexSubst expr) {
    analyse(expr.string);
    analyse(expr.pattern);
    analyse(expr.replace);
    return null;
  }

  @Override public Void visitTernary(Expr.Ternary expr) {
    analyse(expr.first);
    analyse(expr.second);
    analyse(expr.third);
    return null;
  }

  @Override public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitCast(Expr.Cast expr) {
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    return null;
  }

  @Override public Void visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs.forEach(e -> analyse(e));
    return null;
  }

  @Override public Void visitMapLiteral(Expr.MapLiteral expr) {
    expr.entries.forEach(entry -> {
      analyse(entry.first);
      analyse(entry.second);
    });
    return null;
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) {
    return null;
  }

  @Override public Void visitExprString(Expr.ExprString expr) {
    expr.exprList.forEach(e -> {
      analyse(e);
    });
    return null;
  }

  @Override public Void visitVarDecl(Expr.VarDecl expr) {
    analyse(expr.initialiser);
    return null;
  }

  @Override public Void visitFunDecl(Expr.FunDecl funDecl) {
    // Note: expr.parameters are analysed because they are statements in the block so don't analyse twice

    Expr.FunDecl expr = funDecl;

    // If we have a wrapper then resolve it. Note that it has an embedded FunDecl for us, so we will
    // get analysed as part of its statement block. This means we need to check next time not to
    // analyse wrapper if we are already analysiing it.
    if (funDecl.wrapper != null && getFunctions().peek() != funDecl.wrapper) {
      expr = funDecl.wrapper;
    }

    getFunctions().push(expr);
    debug("+ Analysing " + expr.functionDescriptor.implementingClassName + "." + expr.functionDescriptor.name);
    analyse(expr.block);
    getFunctions().pop();
    debug("- Analysing " + expr.functionDescriptor.implementingClassName + "." + expr.functionDescriptor.name);

    // If still not marked async then we must be sync...
    if (expr.functionDescriptor.isAsync == null) {
      if (!isFirstPass) {
        expr.functionDescriptor.isAsync = false;
      }
    }

    // If we are the wrapper
    if (expr != funDecl) {
      // If we were the wrapper, and we are async then function is also marked as async.
      // If we were cleverer we could track better when invoking wrapper and wouldn't need
      // to be so conservative about this...
      if (expr.functionDescriptor.isAsync != null && expr.functionDescriptor.isAsync) {
        funDecl.functionDescriptor.isAsync = true;
      }
    }

    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    analyse(expr.identifierExpr);
    analyse(expr.expr);
    if (!expr.expr.isNull() && expr.type.is(INSTANCE)) {
      asyncIfTypeIsAsync(expr);
    }
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
    if (!expr.expr.isNull() && expr.type.is(INSTANCE)) {
      asyncIfTypeIsAsync(expr);
    }
    return null;
  }

  @Override public Void visitFieldOpAssign(Expr.FieldOpAssign expr) {
    analyse(expr.parent);
    analyse(expr.field);
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitNoop(Expr.Noop expr) {
    return null;
  }

  @Override public Void visitClosure(Expr.Closure expr) {
    analyse(expr.funDecl);
    return null;
  }

  @Override public Void visitReturn(Expr.Return expr) {
    analyse(expr.expr);
    // If return type not same as expression type then we might need to cast and if instance
    // type has async initialisers then we are therefore potentially async...
    if (expr.expr != null && !expr.expr.isNull() && !expr.expr.type.is(expr.returnType)) {
      if (expr.returnType.is(INSTANCE) && !expr.expr.type.isCastableTo(expr.returnType)) {
        FunctionDescriptor initMethod = expr.returnType.getClassDescriptor().getMethod(Utils.JACTL_INIT);
        if (initMethod.isAsync) {
          async(expr);
        }
      }
    }
    return null;
  }

  @Override public Void visitBreak(Expr.Break expr) { return null; }
  @Override public Void visitContinue(Expr.Continue expr) { return null; }

  @Override public Void visitPrint(Expr.Print expr) {
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitDie(Expr.Die expr) {
    analyse(expr.expr);
    return null;
  }

  @Override public Void visitEval(Expr.Eval expr) {
    analyse(expr.script);
    analyse(expr.globals);
    async(expr);
    return null;
  }

  @Override public Void visitBlock(Expr.Block expr) {
    analyse(expr.block);
    return null;
  }

  @Override public Void visitArrayLength(Expr.ArrayLength expr) {
    analyse(expr.array);
    return null;
  }

  @Override public Void visitArrayGet(Expr.ArrayGet expr) {
    analyse(expr.array);
    analyse(expr.index);
    return null;
  }

  @Override public Void visitLoadParamValue(Expr.LoadParamValue expr) {
    analyse(expr.paramDecl);
    return null;
  }

  @Override public Void visitInvokeFunDecl(Expr.InvokeFunDecl expr) {
    expr.args.forEach(this::analyse);
    return null;
  }

  @Override public Void visitInvokeUtility(Expr.InvokeUtility expr) {
    expr.args.forEach(this::analyse);
    return null;
  }

  @Override public Void visitInvokeNew(Expr.InvokeNew expr) {
    expr.dimensions.forEach(this::analyse);
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
    return null;
  }

  @Override public Void visitConvertTo(Expr.ConvertTo expr) {
    analyse(expr.expr);
    analyse(expr.source);
    analyse(expr.offset);

    // If converting to an instance then we are async if initialiser is async
    if (expr.varType.is(INSTANCE)) {
      FunctionDescriptor initMethod = expr.varType.getClassDescriptor().getInitMethod();
      if (initMethod.isAsync == null) {
        assert isFirstPass;
        addAsyncCallDependency(currentFunction(), expr, initMethod);
      }
      else {
        // If initMethod is async then we need to mark ourselves as async
        if (initMethod.isAsync) {
          async(expr);
        }
      }
    }

    // Also async if expression we are converting from is async
    if (expr.expr.isAsync) {
      async(expr);
    }
    return null;
  }

  @Override public Void visitInvokeInit(Expr.InvokeInit expr) {
    expr.args.forEach(this::analyse);

    FunctionDescriptor initMethod = expr.classDescriptor.getInitMethod();
    if (initMethod.isAsync == null) {
      assert isFirstPass;
      addAsyncCallDependency(currentFunction(), expr, initMethod);
    }
    else {
      // If initMethod is async then we need to mark ourselves as async
      if (isAsync(initMethod, null, expr.args)) {
        async(expr);
      }
    }
    return null;
  }

  @Override public Void visitCheckCast(Expr.CheckCast expr) {
    analyse(expr.expr);
    return null;
  }

  ////////////////////////////////////

  // = Stmt

  @Override public Void visitImport(Stmt.Import stmt) {
    return null;
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    return analyse(stmt.stmts);
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    analyse(stmt.stmts);
    int nonGlobals = (int)stmt.variables.values().stream().filter(v -> !v.isGlobal).count();
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    analyse(stmt.condition);
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
    analyse(stmt.updates);
    analyse(stmt.body);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    analyse(stmt.expr);
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    analyse(stmt.expr);
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    analyse(stmt.source);
    analyse(stmt.offset);
    return null;
  }

  @Override public Void visitSpecialVar(Expr.SpecialVar expr) {
    return null;
  }

  ///////////////////////////////////////

  private void async(Expr expr) {
    // Error if this would make a toString() implementation async.
    // We don't support async toString() in order to support toString() invocations if object leaks into Java
    // domain (by being stored in the globals Map) and to save on the extra generated code needed for invoking
    // async functions and handling suspend/resume.
    FunctionDescriptor functionDescriptor = currentFunction().functionDescriptor;
    if (Utils.TO_STRING.equals(functionDescriptor.name)) {
      throw new CompileError(Utils.TO_STRING + "() cannot invoke anything that is async or potentially async", expr.location);
    }

    functionDescriptor.isAsync = true;
    expr.isAsync = true;

    debug("Set " + functionDescriptor.implementingClassName + "." + functionDescriptor.name + " is async");
  }

  private void asyncIfTypeIsAsync(Expr expr) {
    if (!expr.type.is(INSTANCE)) { return; }
    ClassDescriptor classDescriptor = expr.type.getClassDescriptor();
    ClassDescriptor existingClass   = context.getClassDescriptor(classDescriptor.getInternalName());
    if (existingClass != null) {
      if (existingClass.getInitMethod().isAsync) {
        async(expr);
      }
    }
    else {
      if (classDescriptor.getInitMethod().isAsync == null) {
        assert isFirstPass;
        addAsyncCallDependency(currentFunction(), expr, classDescriptor.getInitMethod());
      }
      else
      if (classDescriptor.getInitMethod().isAsync) {
        async(expr);
      }
    }
  }

  private FunctionDescriptor getFunction(Expr expr) {
    Expr.FunDecl funDecl = getFunDecl(expr);
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
      debug("Test " + func.implementingClassName + "." + func.name + " --> async func with no async args");
      return true;
    }

    boolean result = hasAsyncArg(func, arg0, args);
    debug("Test " + func.implementingClassName + "." + func.name + " --> async args = " + result);
    return result;
  }

  private boolean hasAsyncArg(FunctionDescriptor func, Expr arg0, List<Expr> args) {
    // If function is only async if passed an async arg then check the args.
    // For methods note that index 0 means the object on whom we are performing the method call so other
    // arguments start at index 1 so args.get(index-1) gets the arg value for that index.
    // For calls to built-in functions arg0 is null and counting is as per args list indexing.
    if (Utils.isNamedArgs(args)) {
      Map<String, Expr> namedArgs = Utils.getNamedArgs(args);
      // Lambda to extract ith arg. For methods arg0 is 0th arg and param counting starts at 1:
      Function<Integer, Expr> getrArg = i -> arg0 != null ? (i == 0 ? arg0 : namedArgs.get(func.paramNames.get(i - 1)))
                                                          : namedArgs.get(func.paramNames.get(i));
      return func.asyncArgs.stream()
                           .map(getrArg)
                           .anyMatch(this::isAsyncArg);
    }
    else {
      Function<Integer, Expr> getArg = i -> {
        if (arg0 != null) {
          // Method call
          if (i == 0) {
            return arg0;
          }
          i--;
        }
        return args.size() > i ? args.get(i) : null;
      };

      return func.asyncArgs.stream()
                           .map(getArg)
                           .anyMatch(this::isAsyncArg);
    }
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
      Expr.VarDecl decl = (Expr.VarDecl)arg;

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

  private void resolveHeapLocals(Expr.FunDecl caller, Expr.FunDecl callee) {
    // Find heapLocals that are not in the caller scope or in caller heapLocals
    // and add them to caller's heapLocals (and to its parent etc) until we get to
    // place where variable actually resides.
    callee.heapLocals.entrySet()
                     .stream()
                     .filter(entry -> !isOwnerOrHeapLocal(caller, entry.getValue()))
                     .forEach(entry -> {
                       Expr.VarDecl varDecl = entry.getValue();
                       String       name      = varDecl.name.getStringValue();
                       Expr.VarDecl childDecl = Utils.createVarDecl(name, varDecl, caller);
                       Expr.FunDecl funDecl   = caller.owner;
                       for (; !isOwnerOrHeapLocal(funDecl, varDecl); funDecl = funDecl.owner) {
                         Expr.VarDecl parentDecl = Utils.createVarDecl(name, varDecl, funDecl);
                         childDecl.parentVarDecl = parentDecl;
                         childDecl = parentDecl;
                       }
                       if (funDecl == varDecl.owner) {
                         childDecl.parentVarDecl = varDecl.originalVarDecl;
                       }
                       else {
                         childDecl.parentVarDecl = funDecl.heapLocals.get(varDecl.originalVarDecl);
                       }
                     });
  }

  private boolean isOwnerOrHeapLocal(Expr.FunDecl funDecl, Expr.VarDecl varDecl) {
    if (funDecl == null) throw new IllegalStateException("Internal error: could not find owner function for " + varDecl.name.getStringValue());
    if (varDecl.owner == funDecl) {
      return true;
    }
    return funDecl.heapLocals.containsKey(varDecl.originalVarDecl);
  }

  private void addAsyncCallDependency(Expr.FunDecl caller, Expr callSite, FunctionDescriptor callee) {
    List<Pair<Expr, FunctionDescriptor>> callees = asyncCallDependencies.get(caller);
    if (callees == null) {
      callees = new ArrayList<>();
      asyncCallDependencies.put(caller, callees);
    }
    callees.add(Pair.create(callSite, callee));
  }

  private void resolveAsyncDependencies() {
    // If we still have call sites where we don't know if function was async or not due to forward references
    // we now need to work them out. We keep looping and marking functions and call sites as async until there
    // are no more do to. At this point the call dependencies should only refer to other functions in the
    // dependency map so if none of them have yet been marked async then none of them are async.
    while (true) {
      boolean resolvedAsyncCall = false;
      for (Expr.FunDecl caller : asyncCallDependencies.keySet()) {
        List<Pair<Expr, FunctionDescriptor>>      callSites    = asyncCallDependencies.get(caller);
        ArrayList<Pair<Expr, FunctionDescriptor>> newCallSites = new ArrayList<Pair<Expr,FunctionDescriptor>>();
        for (Pair<Expr, FunctionDescriptor> call: callSites) {
          Expr               callSite = call.first;
          FunctionDescriptor callee   = call.second;
          if (callee.isAsync != null) {
            if (callee.isAsync) {
              caller.functionDescriptor.isAsync = true;
              debug("Set " + caller.functionDescriptor.implementingClassName + "." + caller.functionDescriptor.name + " is async (call to " + callee.implementingClassName + "." + callee.name + " is async)");
              callSite.isAsync = true;
              resolvedAsyncCall = true;
            }
          }
          else {
            // Still don't know so add for next round
            newCallSites.add(call);
          }
        }
        asyncCallDependencies.put(caller, newCallSites);
      }
      // If no more async calls resolved then we know that all the rest are sync so nothing more to do
      if (!resolvedAsyncCall) {
        break;
      }
    }

    // Mark all remaining functions as not async
    for (List<Pair<Expr, FunctionDescriptor>> callSites: asyncCallDependencies.values()) {
      for (Pair<Expr, FunctionDescriptor> callSite: callSites) {
        callSite.second.isAsync = false;
      }
    }
  }

  private Expr.FunDecl currentFunction() {
    return getFunctions().peek();
  }

  private Deque<Expr.FunDecl> getFunctions() {
    return classStack.peek().nestedFunctions;
  }
}
