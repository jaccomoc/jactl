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

import jacsal.runtime.Functions;
import jacsal.runtime.RuntimeUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.BOOLEAN;
import static jacsal.JacsalType.INT;
import static jacsal.JacsalType.LIST;
import static jacsal.JacsalType.MAP;
import static jacsal.JacsalType.STRING;
import static jacsal.TokenType.*;
import static jacsal.TokenType.OBJECT_ARR;

/**
 * The Resolver visits all statements and all expressions and performs the following:
 * <ul>
 *   <li>tracks variables and their type</li>
 *   <li>resolves references to symbols (variables, methods, etc)</li>
 *   <li>tracks what local variables exist in each code block</li>
 *   <li>promotes local variables to closed over variabels when used in nested functions/closures</li>
 *   <li>evaluates any simple expressions made up of only constants (literals)</li>
 *   <li>matches the break/continue statements to their enclosing while/for loop</li>
 *   <li>sets type of return statement to match return type of function that return belongs to</li>
 *   <li>finds all instances of implicit returns from functions/closures and converts them into
 *       an explicit "return" statement</li>
 *   <li>creates the "wrapper function" for all functions/closures</li>
 * </ul>
 *
 * <h2>HeapLocal Variables</h2>
 *
 * <p>One of the jobs of the Resolver is to work out which local variables should be truly
 * local (and have a JVM slot allocated) and which should be turned into heap variables
 * because they are referred to by a nested function/closure (i.e. they are "closed over").</p>
 *
 * <p>A heap local variable is stored in a HeapLocal  object which is just a wrapper around the
 * actual value. By using this extra level of indirection we make sure that if the value
 * of the variable changes, all functions using that variable will see the changed value.</p>
 *
 * <p>We pass any such heap local variables as impicit parameters to nested functions that
 * need access to these variables from their parents. Note that sometimes a nested function
 * will access a variable that is not in its immediate parent but from a level higher up so
 * even if the parent has no need of the heap variable it needs it in order to be able to
 * pass it to a nested function that it invokes at some point. In this way nested functions
 * (and functions nested within them) can effect which heap vars need to get passed into the
 * current function from its parent.</p>
 *
 * <p>If we consider a single heap local variable. It can be used in multiple nested functions
 * and its location in the list of implicit parameters for each function can be different.
 * Normally we use the Expr.VarDecl object to track which local variable slot it resides in
 * but with HeapLocal  objects we need to track the slot used in each function so we need a per
 * function object to track the slot for a given HeapLocal . We create a new VarDecl that points
 * to the original VarDecl for every such nested function.</p>
 *
 * <h2>Wrapper Functions</h2>
 *
 * <p>Each function/closure that we create has a wrapper function created for it. Since functions
 * can have optional paremeters with default values the wrapper function is the place where we
 * fill in any missing arguments with the appropriate defaults. The wrapper function also has the
 * job of handling named argument passing when arguments are passed as a Map of name:value pairs.</p>
 *
 * <p>The Resolver creates a new Stmt.FunDecl object that has the "code" for the wrapper function
 * and ensures that the actual function is declared as a nested function within this wrapper
 * function so that closed over variables are passed appropriately as additional arguments to
 * both the wrapper function and the actual function.</p>
 */
public class Resolver implements Expr.Visitor<JacsalType>, Stmt.Visitor<Void> {

  private final CompileContext        compileContext;
  private final Map<String,Object>    globals;
  private final Deque<Expr.FunDecl>   functions = new ArrayDeque<>();
  private final Deque<Stmt.ClassDecl> classes   = new ArrayDeque<>();

  /**
   * Resolve variables, references, etc
   * @param globals  map of global variables (which themselves can be Maps or Lists or simple values)
   */
  Resolver(CompileContext compileContext, Map<String,Object> globals) {
    this.compileContext = compileContext;
    this.globals        = globals;
    globals.keySet().forEach(global -> {
      if (!compileContext.globalVars.containsKey(global)) {
        Expr.VarDecl varDecl = new Expr.VarDecl(new Token("",0).setType(IDENTIFIER).setValue(global),
                                                null);
        varDecl.type = JacsalType.typeOf(globals.get(global));
        varDecl.isGlobal = true;
        compileContext.globalVars.put(global, varDecl);
      }
    });
  }

  Void resolve(Stmt stmt) {
    if (stmt != null) {
      return stmt.accept(this);
    }
    return null;
  }

  Void resolve(List<Stmt> stmts) {
    if (stmts != null) {
      stmts.forEach(this::resolve);
    }
    return null;
  }

  JacsalType resolve(Expr expr) {
    if (expr != null) {
      return expr.accept(this);
    }
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Stmt

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    classes.push(stmt);
    try {
      stmt.classes.forEach(this::resolve);
      stmt.fields.forEach(this::resolve);

      // Don't resolve methods and closures since that will be done by the FunDecl statements
      // within the scriptMain function
        //stmt.methods.forEach(this::resolve);
        //stmt.closures.forEach(this::resolve);

      resolve(stmt.scriptMain);
    }
    finally {
      classes.pop();
    }
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    resolve(stmt.declExpr);
    return null;
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    final var  block         = getBlock();
    Stmt.Stmts previousStmts = block.currentResolvingStmts;
    block.currentResolvingStmts = stmt;

    for (stmt.currentIdx = 0; stmt.currentIdx < stmt.stmts.size(); stmt.currentIdx++) {
      resolve(stmt.stmts.get(stmt.currentIdx));
    }

    block.currentResolvingStmts = previousStmts;
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    functions.peek().blocks.push(stmt);
    try {
      // We first define our nested functions so that we can support
      // forward references to functions declared at same level as us
      stmt.functions.forEach(nested -> {
        nested.varDecl.owner = functions.peek();
        define(nested.name, nested.varDecl);
      });

      return resolve(stmt.stmts);
    }
    finally {
      functions.peek().blocks.pop();
    }
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    if (stmt.declExpr.isExplicitParam) {
      // When resolving parameter initialisers we can have an initialiser close over another
      // parameter of the same function: E.g.:  def f(x,y={x++}) { x + y() }
      // Hard to see why anyone would do this but we need to handle it just in case.
      // We flag the block as being in the middle of parameter resolution so that if one of
      // its parameters is then closed over we know that we have to convert the parameter
      // into one that is passed as a HeapLocal (and this also means that all invocations will
      // then need to be done via the method wrapper that knows which parameters to convert
      // into HeapLocals before invoking the actual function).
      getBlock().isResolvingParams = true;
    }
    resolve(stmt.declExpr);
    getBlock().isResolvingParams = false;
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    resolve(stmt.condtion);
    resolve(stmt.trueStmt);
    resolve(stmt.falseStmt);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.updates);

    // We need to keep track of what the current while loop is so that break/continue
    // statements within body of the loop can find the right Stmt.While object
    Stmt.While oldWhileStmt = functions.peek().currentWhileLoop;
    functions.peek().currentWhileLoop = stmt;

    resolve(stmt.body);

    functions.peek().currentWhileLoop = oldWhileStmt;   // Restore old one
    return null;
  }

  @Override public Void visitBreak(Stmt.Break stmt) {
    stmt.whileLoop = currentWhileLoop(stmt.breakToken);
    return null;
  }

  @Override public Void visitContinue(Stmt.Continue stmt) {
    stmt.whileLoop = currentWhileLoop(stmt.continueToken);
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    resolve(stmt.source);
    resolve(stmt.offset);
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Expr


  @Override public JacsalType visitRegexMatch(Expr.RegexMatch expr) {
    expr.isConst = false;

    // Special case for standalone regex where we need to match against "it" if in a context where variable
    // "it" exists but only if the regex has modifiers after it. Otherwise we will treat it as a regular
    // expression string. If there are modifiers but not "it" then we generate an error.
    if (expr.implicitItMatch) {
      if ((!expr.modifiers.isEmpty() || expr.isSubstitute) && !variableExists(Utils.IT_VAR)) {
        throw new CompileError("No 'it' variable in this scope to match against", expr.location);
      }
      if (expr.modifiers.isEmpty() && !expr.isSubstitute) {
        // Just a normal expr string
        expr.left = null;
      }
    }

    resolve(expr.left);
    resolve(expr.right);

    if (expr.left == null) {
      // Just an expression string
      return expr.type = expr.right.type;
    }

    // Check to see if we already have a capture array variable in the current scope. If we already
    // have one then we will reuse it. Otherwise we will create a new one. Note that we don't want
    // to use one that we have closed over for our own capture vars since this will break code in
    // the parent scope that the closed over capture var belongs to if it relies on the capture vars
    // still being as they were when the nested function/closure was invoked.
    Expr.VarDecl captureArrVar = getVars().get(Utils.CAPTURE_VAR);
    if (captureArrVar == null) {
      final var captureArrName = new Token(IDENTIFIER, expr.operator).setValue(Utils.CAPTURE_VAR);
      // Allocate our capture array var if we don't already have one in scope
      captureArrVar = new Expr.VarDecl(captureArrName, null);
      captureArrVar.type = MATCHER;
      captureArrVar.owner = functions.peek();
      captureArrVar.isResultUsed = false;
      declare(captureArrName);
      define(captureArrName, captureArrVar);
      expr.captureArrVarDecl = captureArrVar;
      // Insert a VarDecl statement before current statement so that if we are in a loop our CAPTURE_VAR
      // is created before the loop starts
      insertStmt(new Stmt.VarDecl(captureArrName, captureArrVar));
    }
    else {
      expr.captureArrVarDecl = captureArrVar;
    }

    return expr.type = BOOLEAN;
  }

  @Override public JacsalType visitRegexSubst(Expr.RegexSubst expr) {
    visitRegexMatch(expr);
    resolve(expr.replace);
    return expr.type = STRING;
  }

  @Override public JacsalType visitBinary(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);

    expr.isConst = expr.left.isConst && expr.right.isConst;

    if (expr.operator.is(QUESTION_COLON, EQUAL_GRAVE, BANG_GRAVE)) {
      expr.isConst = false;
    }

    if (expr.isConst && expr.left.isConst && expr.left.constValue == null && !expr.operator.getType().isBooleanOperator()) {
      throw new CompileError("Null operand for left-hand side of '" + expr.operator.getChars() + "': cannot be null", expr.left.location);
    }

    // Field access operators
    if (expr.operator.is(DOT,QUESTION_DOT,LEFT_SQUARE,QUESTION_SQUARE)) {
      expr.isConst = false;
      if (!expr.left.type.is(ANY)) {
        // Do some level of validation
        if (expr.operator.is(DOT,QUESTION_DOT) && !expr.left.type.is(MAP)) {
          // If obect is not a map then field access might be for a method of the object.
          // If we the field is a string literal we can validate at compile time by searching for a matching
          // method. If field is some sort of expression then we have to wait for runtime to find out if
          // access was legal or not.
          if (expr.right instanceof Expr.Literal) {
            Object field = ((Expr.Literal)expr.right).value.getValue();
            if (field instanceof String) {
              if (Functions.lookupMethod(expr.left.type, (String)field) == null) {
                throw new CompileError("Invalid object type (" + expr.left.type + ") for field access", expr.operator);
              }
            }
          }
        }
        // '[' and '?['
        if (!expr.left.type.is(MAP,LIST,STRING)) {
          throw new CompileError("Invalid object type (" + expr.left.type + ") for indexed (or field) access", expr.operator);
        }

        // TODO: if we have a concrete type on left-hand side we can work out type of field here
        //       for the moment all field access returns ANY since we don't know what has been
        //       stored in a Map or List
      }

      expr.type = ANY;

      // Since we now know we are doing a map/list lookup we know what parent type should
      // be so if parent type was ANY we can change to Map/List. This is used when field
      // path is an lvalue to create missing fields/elements of the correct type.
      if (expr.left.type.is(ANY) && expr.createIfMissing) {
        expr.left.type = expr.operator.is(DOT,QUESTION_DOT) ? MAP : LIST;
      }
      return expr.type;
    }

    if (expr.operator.is(INSTANCE_OF, BANG_INSTANCE_OF)) {
      if (!(expr.right instanceof Expr.Literal && ((Expr.Literal)expr.right).value.getType().isType())) {
        throw new CompileError("Right-hand side of " + expr.operator.getChars() + " must be a valid type", expr.right.location);
      }
      expr.isConst = false;
      return expr.type = BOOLEAN;
    }

    expr.type = JacsalType.result(expr.left.type, expr.operator, expr.right.type);
    if (expr.isConst) {
      return evaluateConstExpr(expr);
    }
    return expr.type;
  }

  @Override public JacsalType visitTernary(Expr.Ternary expr) {
    // This is only used currently for:   first ? second : third
    resolve(expr.first);
    resolve(expr.second);
    resolve(expr.third);
    if (!expr.third.type.isConvertibleTo(expr.second.type)) {
      throw new CompileError("Result types of " + expr.second.type + " and " + expr.third.type + " are not compatible", expr.operator2);
    }
    return expr.type = JacsalType.result(expr.second.type, expr.operator1, expr.third.type);
  }

  @Override public JacsalType visitPrefixUnary(Expr.PrefixUnary expr) {
    resolve(expr.expr);
    if (expr.operator.getType().isType()) {
      // Type case so if we already know we have a bad cast then throw error
      if (!expr.expr.type.isConvertibleTo(JacsalType.valueOf(expr.operator.getType()))) {
        throw new CompileError("Cannot cast from " + expr.expr.type + " to " + expr.operator.getChars(), expr.operator);
      }

      // We have a cast so our type is the type we are casting to
      return expr.type = JacsalType.valueOf(expr.operator.getType());
    }

    expr.isConst = expr.expr.isConst;
    if (expr.operator.is(BANG)) {
      expr.type = BOOLEAN;
      if (expr.isConst) {
        expr.constValue = !Utils.toBoolean(expr.expr.constValue);
      }
      return expr.type;
    }

    expr.type = expr.expr.type.unboxed();
    if (!expr.type.isNumeric() && !expr.type.is(ANY)) {
      throw new CompileError("Prefix operator " + expr.operator.getChars() + " cannot be applied to type " + expr.expr.type, expr.operator);
    }
    if (expr.isConst) {
      expr.constValue = expr.expr.constValue;
      if (expr.operator.is(MINUS)) {
        switch (expr.type.getType()) {
          case INT:     expr.constValue = -(int)expr.constValue;                  break;
          case LONG:    expr.constValue = -(long)expr.constValue;                 break;
          case DOUBLE:  expr.constValue = -(double)expr.constValue;               break;
          case DECIMAL: expr.constValue = ((BigDecimal)expr.constValue).negate(); break;
        }
      }
      else
      if (expr.operator.is(PLUS_PLUS,MINUS_MINUS)) {
        if (expr.expr.constValue == null) {
          throw new CompileError("Prefix operator '" + expr.operator.getChars() + "': null value encountered", expr.expr.location);
        }
        expr.constValue = incOrDec(expr.operator.is(PLUS_PLUS), expr.type, expr.expr.constValue);
      }
    }
    return expr.type;
  }

  @Override public JacsalType visitPostfixUnary(Expr.PostfixUnary expr) {
    resolve(expr.expr);
    expr.isConst      = expr.expr.isConst;
    expr.type         = expr.expr.type;
    if (expr.expr.type.isNumeric() || expr.expr.type.is(ANY)) {
      if (expr.isConst) {
        if (expr.expr.constValue == null) {
          throw new CompileError("Postfix operator '" + expr.operator.getChars() + "': null value encountered", expr.expr.location);
        }
        // For const expressions, postfix inc/dec is a no-op
        expr.constValue = expr.expr.constValue;
      }
      return expr.type;
    }
    throw new CompileError("Unary operator " + expr.operator.getChars() + " cannot be applied to type " + expr.expr.type, expr.operator);
  }

  @Override public JacsalType visitLiteral(Expr.Literal expr) {
    // Whether we optimise const expressions by evaluating at compile time
    // is controlled by CompileContext (defaults to true).
    expr.isConst    = compileContext.evaluateConstExprs;
    expr.constValue = expr.value.getValue();

    switch (expr.value.getType()) {
      case INTEGER_CONST: return expr.type = INT;
      case LONG_CONST:    return expr.type = JacsalType.LONG;
      case DOUBLE_CONST:  return expr.type = JacsalType.DOUBLE;
      case DECIMAL_CONST: return expr.type = JacsalType.DECIMAL;
      case STRING_CONST:  return expr.type = STRING;
      case TRUE:          return expr.type = BOOLEAN;
      case FALSE:         return expr.type = BOOLEAN;
      case NULL:          return expr.type = ANY;
      case IDENTIFIER:    return expr.type = STRING;
      case OBJECT_ARR:    return expr.type = STRING;
      default:
        // In some circumstances (e.g. map keys) we support literals that are keywords
        if (!expr.value.isKeyword()) {
          throw new IllegalStateException("Internal error: unexpected token for literal - " + expr.value);
        }
        return expr.type = STRING;
    }
  }

  @Override public JacsalType visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs.forEach(this::resolve);
    return expr.type = LIST;
  }

  @Override public JacsalType visitMapLiteral(Expr.MapLiteral expr) {
    expr.entries.forEach(entry -> {
      resolve(entry.first);
      resolve(entry.second);
    });
    return expr.type = MAP;
  }

  @Override public JacsalType visitVarDecl(Expr.VarDecl expr) {
    expr.owner = functions.peek();

    // Functions have previously been declared by the block they belong to
    if (expr.funDecl != null) {
      return expr.type = FUNCTION;
    }

    // Declare the variable (but don't define it yet) so that we can detect self-references
    // in the initialiser. E.g. we can catch:  int x = x + 1
    declare(expr.name);

    JacsalType type = resolve(expr.initialiser);
    if (expr.type == null) {
      // If variable was declared as "var"
      expr.type = type;

      // If variable was a parameter then we have moved the initialiser to the wrapper function
      // and now we need to update the parameter on the real function with its actual type
      if (expr.paramVarDecl != null) {
        expr.paramVarDecl.type = type;
      }
    }
    else
    if (type != null && !type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert initialiser of type " + type + " to type of variable " +
                             expr.name.getStringValue() + " (" + expr.type + ")", expr.initialiser.location);
    }
    define(expr.name, expr);
    return expr.type;
  }

  @Override public JacsalType visitFunDecl(Expr.FunDecl expr) {
    // If the script main function
    if (functions.size() == 0) {
      expr.methodName = expr.name.getStringValue();
      return doVisitFunDecl(expr, false);
    }

    // Nested functions
    if (!expr.isWrapper && expr.wrapper == null) {
      // Create a name if we are a closure
      Expr.FunDecl parent = functions.peek();
      String methodName = expr.isClosure() ? "$c" + ++parent.closureCount : expr.name.getStringValue();

      // Method name is parent + $ + functionName unless at top level in which case
      // there is no parent so we use just functionName
      expr.methodName = functions.size() == 1 ? methodName
                                              : parent.methodName + "$" + methodName;

      // Create a wrapper function that takes var of var arg and named argument handling
      expr.wrapper = createWrapperFunction(expr);
      expr.wrapper.methodName = Utils.wrapperName(expr.methodName);

      // Resolve the wrapper. The wrapper has us as an embeeded statement so we will
      // get resolved as a nested function of the wrapper and the next time through here
      // our wrapper will be set
      return doVisitFunDecl(expr.wrapper, true);
    }
    else {
      // We are the real function and are being called again as a result of the wrapper resolving
      return doVisitFunDecl(expr, false);
    }
  }

  private JacsalType doVisitFunDecl(Expr.FunDecl expr, boolean defineVar) {
    // Only define a variable for the wrapper function. We don't define a variable
    // for the function itself within the wrapper as we never invoke it that way.
    // The variable exists to hold the MethodHandle that points to the wrapper function
    // so we don't want yet another MethodHandle to the actual function since all calls
    // where we would use a MethodHandle have to go through the wrapper function to get
    // the var args/named args treatment.
    if (defineVar) {
      resolve(expr.varDecl);
    }
    Expr.FunDecl parent = functions.peek();
    functions.push(expr);
    try {
      // Add explicit return in places where we would implicity return the result
      explicitReturn(expr.block, expr.returnType);
      resolve(expr.block);
      return expr.type = FUNCTION;
    }
    finally {
      functions.pop();
      if (parent != null) {
        // Check if parent needs to have any additional heap vars passed to it in order for it to
        // be able to pass them to its nested function and add them to the parent.heapLocal s map.
        // These vars are the ones that our nested functions close over. They will all be vars that
        // belong to a scope in our parent or in one of its antecedents.
        // We only need add these vars to our parent's list of heap vars if the var's scope is not
        // the parent itself.
        // We need to create new varDecls for every level between the owner and the function that
        // refers to the var. Each varDecl for a nested function will link to the varDecl of its
        // immediate parent. That way each parent will know how to copy the heap var from its own
        // parameter list (or its actual variable decl) into the appropriate arg for invoking the
        // nested function.
        expr.heapLocalParams.forEach((name, varDecl) -> addHeapLocalToParents(name, varDecl));
      }
    }
  }

  @Override public JacsalType visitIdentifier(Expr.Identifier expr) {
    Expr.VarDecl varDecl = null;
    if (expr.couldBeFunctionCall) {
      varDecl = lookupFunction(expr.identifier);
    }
    if (varDecl == null) {
      // Not a function lookup or couldn't find function
      varDecl = lookup(expr.identifier);
    }
    expr.varDecl = varDecl;

    // For capture vars type is always STRING
    if (expr.identifier.getStringValue().charAt(0) == '$') {
      return expr.type = STRING;
    }

    return expr.type = varDecl.type;
  }

  @Override public JacsalType visitLoadParamValue(Expr.LoadParamValue expr) {
    Expr.VarDecl varDecl = lookup(expr.paramDecl.name);
    expr.varDecl = varDecl;
    return expr.type = expr.varDecl.type;
  }

  @Override public JacsalType visitVarAssign(Expr.VarAssign expr) {
    expr.type = resolve(expr.identifierExpr);
    resolve(expr.expr);
    if (!expr.expr.type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert from type of right hand side (" + expr.expr.type + ") to " + expr.type, expr.operator);
    }
    if (expr.operator.is(QUESTION_EQUAL)) {
      // If using ?= have to allow for null being result when assignment doesn't occur
      expr.type = expr.type.boxed();
    }
    return expr.type;
  }

  @Override public JacsalType visitVarOpAssign(Expr.VarOpAssign expr) {
    expr.type = resolve(expr.identifierExpr);
    if (expr.expr instanceof Expr.Binary) {
      Expr.Binary valueExpr = (Expr.Binary) expr.expr;

      // Set the type of the Noop in our binary expression to the variable type and then
      // resolve the binary expression
      valueExpr.left.type = expr.identifierExpr.varDecl.type;
    }
    resolve(expr.expr);

    if (!expr.expr.type.isConvertibleTo(expr.type)) {
      throw new CompileError("Cannot convert from type of right hand side (" + expr.expr.type + ") to " + expr.type, expr.operator);
    }
    return expr.type;
  }

  @Override public JacsalType visitNoop(Expr.Noop expr) {
    expr.isConst = false;
    // Type will already have been set by parent (e.g. in visitVarOpAssign)
    return expr.type;
  }

  @Override public JacsalType visitAssign(Expr.Assign expr) {
    return resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);
  }

  @Override public JacsalType visitOpAssign(Expr.OpAssign expr) {
    if (expr.expr instanceof Expr.Binary) {
      Expr.Binary valueExpr = (Expr.Binary) expr.expr;
      valueExpr.left.type = ANY;     // for fields use ANY for the moment
    }
    resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);

    return expr.type;
  }

  private JacsalType resolveFieldAssignment(Expr expr, Expr parent, Expr field, Expr valueExpr, Token accessType) {
    resolve(parent);
    // Adjust type of parent based on type of access being done
    if (parent instanceof Expr.Binary) {
      Expr.Binary binaryParent = (Expr.Binary) parent;
      if (binaryParent.createIfMissing) {
        binaryParent.type = accessType.is(DOT, QUESTION_DOT) ? MAP : LIST;
      }
    }
    resolve(field);
    resolve(valueExpr);
    // Type will be type of expression but boxed if primitive since we are storing in a map/list
    return expr.type = valueExpr.type.boxed();
  }

  @Override public JacsalType visitExprString(Expr.ExprString expr) {
    expr.exprList.forEach(this::resolve);
    return expr.type = STRING;
  }

  @Override
  public JacsalType visitReturn(Expr.Return returnExpr) {
    resolve(returnExpr.expr);
    returnExpr.returnType = functions.peek().returnType;
    returnExpr.funDecl = functions.peek();
    if (!returnExpr.expr.type.isConvertibleTo(returnExpr.returnType)) {
      throw new CompileError("Expression type not compatible with return type of function", returnExpr.expr.location);
    }
    return returnExpr.type = ANY;
  }

  @Override public JacsalType visitPrint(Expr.Print printExpr) {
    resolve(printExpr.expr);
    return printExpr.type = BOOLEAN;
  }

  @Override public JacsalType visitClosure(Expr.Closure expr) {
    resolve(expr.funDecl);
    return expr.type = FUNCTION;
  }

  @Override public JacsalType visitCall(Expr.Call expr) {
    // Special case if we are invoking the function directly (not via a MethodHandle value)
    if (expr.callee instanceof Expr.Identifier) {
      ((Expr.Identifier) expr.callee).couldBeFunctionCall = true;
    }
    resolve(expr.callee);
    expr.args.forEach(this::resolve);
    if (!expr.callee.type.is(FUNCTION,ANY)) {
      throw new CompileError("Expression of type " + expr.callee.type + " cannot be called", expr.token);
    }
    if (expr.callee.isConst && expr.callee.constValue == null) {
      throw new CompileError("Null value for Function", expr.token);
    }

    expr.type = ANY;
    if (expr.callee.isFunctionCall()) {
      // Special case where we know the function directly and get its return type
      Expr.FunDecl funDecl = ((Expr.Identifier) expr.callee).varDecl.funDecl;
      expr.type = funDecl.returnType;

      // Validate argument count and types against parameter types
      int mandatoryCount = Utils.mandatoryParamCount(funDecl.parameters);
      int argCount       = expr.args.size();
      int paramCount     = funDecl.parameters.size();
      validateArgCount(argCount, mandatoryCount, paramCount, expr.token);
      for (int i = 0; i < argCount; i++) {
        Expr       arg       = expr.args.get(i);
        JacsalType argType   = arg.type;
        JacsalType paramType = funDecl.parameters.get(i).declExpr.type;
        if (!argType.isConvertibleTo(paramType)) {
          throw new CompileError("Cannot convert " + nth(i + 1) + " argument of type " + argType + " to parameter type of " + paramType, arg.location);
        }
      }
    }
    return null;
  }

  @Override public JacsalType visitMethodCall(Expr.MethodCall expr) {
    resolve(expr.parent);
    // Flag as chained method call so that if parent call has a result of Iterator
    // it can remain as an Iterator. Otherwise calls that result in Iterators have
    // the result converted to a List. It is only if we are chaining method calls
    // that Iterator is allowed to be used this way. E.g; x.map().map().each()
    if (expr.parent instanceof Expr.MethodCall) {
      ((Expr.MethodCall) expr.parent).isMethodCallTarget = true;
    }
    if (expr.parent instanceof Expr.Call) {
      ((Expr.Call) expr.parent).isMethodCallTarget = true;
    }

    expr.args.forEach(this::resolve);

    // See if we have a direct method invocation or not. We need to know the type of the parent
    // (not ANY) and to be able to find the method in the list of registered methods.
    // Then we need to have the exact number of arguments that match the expected parameter count.
    // If we have less args we will do runtime lookup of MethodHandle that gets to wrapper method.
    if (!expr.parent.type.is(ANY)) {
      var descriptor = Functions.lookupMethod(expr.parent.type, expr.methodName);
      if (descriptor != null) {
        expr.methodDescriptor = descriptor;
        validateArgCount(expr.args.size(), descriptor.mandatoryArgCount, descriptor.paramTypes.size(), expr.leftParen);
        for (int i = 0; i < expr.args.size(); i++) {
          Expr arg = expr.args.get(i);
          JacsalType paramType = descriptor.paramTypes.get(i);
          if (!arg.type.isConvertibleTo(paramType)) {
            throw new CompileError("Cannot convert " + nth(i + 1) + " argument of type " + arg.type +
                                   " to parameter type of " + paramType, arg.location);
          }
        }
        expr.type = descriptor.returnType;
        return expr.type;
      }
      if (!expr.parent.type.is(MAP)) {
        throw new CompileError("No such method " + expr.methodName + " for object of type " + expr.parent.type, expr.accessOperator);
      }
    }

    // Don't know type of parent or couldn't find method
    expr.type = ANY;
    return expr.type;
  }

  @Override public JacsalType visitArrayLength(Expr.ArrayLength expr) {
    resolve(expr.array);
    return expr.type = INT;
  }

  @Override public JacsalType visitArrayGet(Expr.ArrayGet expr) {
    resolve(expr.array);
    resolve(expr.index);
    return expr.type = ANY;
  }

  @Override public JacsalType visitInvokeFunction(Expr.InvokeFunction expr) {
    expr.args.forEach(this::resolve);
    return expr.type = expr.funDecl.returnType;
  }

  ////////////////////////////////////////////

  private void insertStmt(Stmt.VarDecl declStmt) {
    var currentStmts = getBlock().currentResolvingStmts;
    currentStmts.stmts.add(currentStmts.currentIdx, declStmt);
    currentStmts.currentIdx++;
  }

  private void validateArgCount(int argCount, int mandatoryCount, int paramCount, Token location) {
    if (argCount < mandatoryCount) {
      String atLeast = mandatoryCount == paramCount ? "" : "at least ";
      throw new CompileError("Missing mandatory arguments (arg count of " + argCount + " but expected " + atLeast + mandatoryCount + ")", location);
    }
    if (argCount > paramCount) {
      throw new CompileError("Too many arguments (passed " + argCount + " but expected only " + paramCount + ")", location);
    }
  }

  private JacsalType evaluateConstExpr(Expr.Binary expr) {
    if (expr.type.is(STRING)) {
      if (expr.operator.isNot(PLUS,STAR)) { throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for Strings"); }
      if (expr.operator.is(PLUS)) {
        if (expr.left.constValue == null) {
          throw new CompileError("Left-hand side of '+' cannot be null", expr.operator);
        }
        expr.constValue = Utils.toString(expr.left.constValue) + Utils.toString(expr.right.constValue);
      }
      else {
        if (expr.right.constValue == null) {
          throw new CompileError("Right-hand side of string repeat operator must be numeric but was null", expr.operator);
        }
        String lhs    = Utils.toString(expr.left.constValue);
        long   length = Utils.toLong(expr.right.constValue);
        if (length < 0) {
          throw new CompileError("String repeat count must be >= 0", expr.right.location);
        }
        expr.constValue = lhs.repeat((int)length);
      }
      return expr.type;
    }

    if (expr.operator.is(PLUS) && expr.type.is(MAP)) {
      expr.constValue = RuntimeUtils.mapAdd((Map)expr.left.constValue, (Map)expr.right.constValue, false);
      return expr.type;
    }

    if (expr.operator.is(PLUS) && expr.type.is(LIST)) {
      expr.constValue = RuntimeUtils.listAdd((List)expr.left.constValue, expr.right.constValue, false);
    }

    if (expr.operator.is(AMPERSAND_AMPERSAND)) {
      expr.constValue = RuntimeUtils.isTruth(expr.left.constValue, false) &&
                        RuntimeUtils.isTruth(expr.right.constValue, false);
      return expr.type;
    }
    if (expr.operator.is(PIPE_PIPE)) {
      expr.constValue = RuntimeUtils.isTruth(expr.left.constValue, false) ||
                        RuntimeUtils.isTruth(expr.right.constValue, false);
      return expr.type;
    }

    if (expr.operator.getType().isBooleanOperator()) {
      expr.constValue = RuntimeUtils.booleanOp(expr.left.constValue, expr.right.constValue,
                                               RuntimeUtils.getOperatorType(expr.operator.getType()),
                                               expr.operator.getSource(), expr.operator.getOffset());
      return expr.type = BOOLEAN;
    }

    if (expr.left.constValue == null)  { throw new CompileError("Null operand for left-hand side of '" + expr.operator.getChars(), expr.operator); }
    if (expr.right.constValue == null) { throw new CompileError("Null operand for right-hand side of '" + expr.operator.getChars(), expr.operator); }

    switch (expr.type.getType()) {
      case INT: {
        int left  = Utils.toInt(expr.left.constValue);
        int right = Utils.toInt(expr.right.constValue);
        throwIf(expr.operator.is(SLASH,PERCENT) && right == 0, "Divide by zero error", expr.right.location);
        switch (expr.operator.getType()) {
          case PLUS:    expr.constValue = left + right; break;
          case MINUS:   expr.constValue = left - right; break;
          case STAR:    expr.constValue = left * right; break;
          case SLASH:   expr.constValue = left / right; break;
          case PERCENT: expr.constValue = left % right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for ints");
        }
        break;
      }
      case LONG: {
        long left  = Utils.toLong(expr.left.constValue);
        long right = Utils.toLong(expr.right.constValue);
        throwIf(expr.operator.is(SLASH,PERCENT) && right == 0, "Divide by zero error", expr.right.location);
        switch (expr.operator.getType()) {
          case PLUS:    expr.constValue = left + right; break;
          case MINUS:   expr.constValue = left - right; break;
          case STAR:    expr.constValue = left * right; break;
          case SLASH:   expr.constValue = left / right; break;
          case PERCENT: expr.constValue = left % right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for longs");
        }
        break;
      }
      case DOUBLE: {
        double left  = Utils.toDouble(expr.left.constValue);
        double right = Utils.toDouble(expr.right.constValue);
        switch (expr.operator.getType()) {
          case PLUS:    expr.constValue = left + right; break;
          case MINUS:   expr.constValue = left - right; break;
          case STAR:    expr.constValue = left * right; break;
          case SLASH:   expr.constValue = left / right; break;
          case PERCENT: expr.constValue = left % right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for doubles");
        }
        break;
      }
      case DECIMAL: {
        BigDecimal left  = Utils.toDecimal(expr.left.constValue);
        BigDecimal right = Utils.toDecimal(expr.right.constValue);
        throwIf(expr.operator.is(SLASH,PERCENT) && right.stripTrailingZeros() == BigDecimal.ZERO, "Divide by zero error", expr.right.location);
        expr.constValue = RuntimeUtils.decimalBinaryOperation(left, right, RuntimeUtils.getOperatorType(expr.operator.getType()), compileContext.maxScale,
                                                              expr.operator.getSource(), expr.operator.getOffset());
        break;
      }
    }
    return expr.type;
  }

  private void addHeapLocalToParents(String name, Expr.VarDecl varDecl) {
    Expr.VarDecl childVarDecl = varDecl;

    // Now iterate through parents linking VarDecls until we get to one that already
    // has a var by that name (which will be the actual VarDecl or an intermediate function
    // that already has it being passed in as a parameter).
    for (Iterator<Expr.FunDecl> iter = functions.iterator(); iter.hasNext(); ) {
      // If parent already has this var as a parameter then point child to this and return
      Expr.FunDecl funDecl       = iter.next();
      Expr.VarDecl parentVarDecl = funDecl.heapLocalParams.get(name);
      if (parentVarDecl != null) {
        childVarDecl.parentVarDecl = parentVarDecl;
        return;
      }

      // If we have reached function that originally declared this var then point child
      // to this one and return
      if (funDecl == varDecl.owner) {
        childVarDecl.parentVarDecl = varDecl.originalVarDecl;
        return;
      }

      // Otherwise build a new VarDecl for an intermediate value and make it a parameter
      // of the current function by adding to its heapLocal Parameters
      parentVarDecl = createVarDecl(name, varDecl, funDecl);
      childVarDecl.parentVarDecl = parentVarDecl;

      // Now parent becomes the child for the next iteration
      childVarDecl = parentVarDecl;
    }
    throw new IllegalStateException("Internal error: couldn't find owner of variable " + varDecl.name.getStringValue());
  }

  private Expr.VarDecl createVarDecl(String name, Expr.VarDecl varDecl, Expr.FunDecl funDecl) {
    Expr.VarDecl newVarDecl    = new Expr.VarDecl(varDecl.name, null);
    newVarDecl.type            = varDecl.type.boxed();
    newVarDecl.owner           = varDecl.owner;
    newVarDecl.isHeapLocal     = true;
    newVarDecl.isParam         = true;
    newVarDecl.originalVarDecl = varDecl.originalVarDecl;

    // We need to check for scenarios where the current function has been used as a forward
    // reference at some point in the code but between the point of the reference and the
    // declaration of our function there is a variable declared that we know close over.
    // Since that variable didn't exist when the original forward reference was made we
    // have to disallow such forward references.
    // E.g.:
    //   def f(x){g(x)}; def v = ... ; def g(x) { v + x }
    // Since g uses v and v does not exist when f invokes g we have to throw an error.
    // To detect such references we remember the earlies reference and check that the
    // variable we are now closing over was not declared after that reference.
    if (funDecl.earliestForwardReference != null) {
      if (isEarlier(funDecl.earliestForwardReference, varDecl.location)) {
        throw new CompileError("Forward reference to function " + funDecl.name.getStringValue() + " that closes over variable " +
                               varDecl.name.getStringValue() + " not yet declared at time of reference",
                               funDecl.earliestForwardReference);
      }
    }

    funDecl.heapLocalParams.put(name, newVarDecl);
    return newVarDecl;
  }

  private static String nth(int i) {
    if (i % 10 == 1 && i % 100 != 11) { return i + "st"; }
    if (i % 10 == 2 && i % 100 != 12) { return i + "nd"; }
    if (i % 10 == 3 && i % 100 != 13) { return i + "rd"; }
    return i + "th";
  }

  private static Object incOrDec(boolean isInc, JacsalType type, Object val) {
    int incAmount = isInc ? 1 : -1;
    switch (type.getType()) {
      case INT:     return ((int)val) + incAmount;
      case LONG:    return ((long)val) + incAmount;
      case DOUBLE:  return ((double)val) + incAmount;
      case DECIMAL: return ((BigDecimal)val).add(BigDecimal.valueOf(incAmount));
    }
    throw new IllegalStateException("Internal error: unexpected type " + type);
  }

  /////////////////////////

  private Stmt.While currentWhileLoop(Token token) {
    Stmt.While whileStmt = functions.peek().currentWhileLoop;
    if (whileStmt == null) {
      throw new CompileError(token.getChars() + " must be within a while/for loop", token);
    }
    return whileStmt;
  }

  private void error(String msg, Token location) {
    throw new CompileError(msg, location);
  }

  private static Expr.VarDecl UNDEFINED = new Expr.VarDecl(null, null);

  private void declare(Token name) {
    String varName = name.getStringValue();
    Map<String,Expr.VarDecl> vars = getVars();
    if (!compileContext.replMode || !isAtTopLevel()) {
      Expr.VarDecl decl = vars.get(varName);
      if (decl != null) {
        error("Variable with name " + varName + " already declared in this scope", name);
      }
    }
    // Add variable with type of UNDEFINED as a marker to indicate variable has been declared but is
    // not yet usable
    vars.put(name.getStringValue(), UNDEFINED);
  }

  private void define(Token name, Expr.VarDecl decl) {
    Expr.FunDecl function = functions.peek();
    assert function != null;
    Map<String,Expr.VarDecl> vars = getVars();

    // In repl mode we don't have a top level block and we store var types in the compileContext
    // and their actual values will be stored in the globals map.
    if (isAtTopLevel() && compileContext.replMode) {
      decl.isGlobal = true;
      decl.type = decl.type.boxed();
    }
    // Remember at what nesting level this variable is declared. This allows us to work
    // out when looking up vars whether this belongs to the current function or not.
    decl.nestingLevel = functions.size();
    vars.put(name.getStringValue(), decl);
  }

  private boolean isAtTopLevel() {
    return functions.size() == 1 && functions.peek().blocks.size() == 1;
  }

  private Map<String,Expr.VarDecl> getVars() {
    // Special case for repl mode where we ignore top level block
    if (compileContext.replMode && isAtTopLevel()) {
      return compileContext.globalVars;
    }
    return getBlock().variables;
  }

  private Stmt.Block getBlock() {
    return functions.peek().blocks.peek();
  }

  private Expr.VarDecl lookupFunction(Token identifier) {
    return lookup(identifier.getStringValue(), identifier, true, false);
  }

  private Expr.VarDecl lookup(Token identifier) {
    return lookup(identifier.getStringValue(), identifier, false, false);
  }

  private boolean variableExists(String name) {
    return lookup(name, null, false, true) != null;
  }

  // We look up variables in our local scope and parent scopes within the same function.
  // If we still can't find the variable we search in parent functions to see if the
  // variable exists there (at which point we close over it and it becomes a heap variable
  // rather than one that is a JVM local).
  private Expr.VarDecl lookup(String name, Token location, boolean functionLookup, boolean existenceCheckOnly) {
    // Special case for capture vars which are of form $n where n > 0
    // For these vars we actually look for the $@ capture array var since $n means $@[n]
    if (name.charAt(0) == '$') {
      name = Utils.CAPTURE_VAR;
    }

    Expr.VarDecl varDecl = null;
    Stmt.Block block = null;
    // Search blocks in each function until we find the variable
    FUNC_LOOP:
    for (Iterator<Expr.FunDecl> funcIt = functions.iterator(); funcIt.hasNext(); ) {
      Expr.FunDecl funDecl = funcIt.next();
      for (Iterator<Stmt.Block> it = funDecl.blocks.iterator(); it.hasNext(); ) {
        block = it.next();
        varDecl = block.variables.get(name);
        if (varDecl != null) {
          break FUNC_LOOP;  // Break out of both loops
        }
      }
    }

    if (varDecl == null) {
      block = null;

      // Look for field in classes
      for (Iterator<Stmt.ClassDecl> it = classes.iterator(); it.hasNext();) {
        varDecl = it.next().fieldVars.get(name);
        if (varDecl != null) {
          break;
        }
      }
    }

    if (varDecl == null /* && compileContext.replMode */) {
      varDecl = compileContext.globalVars.get(name);
    }

    if (existenceCheckOnly) {
      return varDecl;
    }

    if (varDecl == UNDEFINED) {
      throw new CompileError("Variable initialisation cannot refer to itself", location);
    }
    if (varDecl != null) {
      if (varDecl.funDecl != null) {
        // Track earliest reference to detect where forward referenced function closes over
        // vars not yet declared at time of reference
        Token reference = varDecl.funDecl.earliestForwardReference;
        if (reference == null || isEarlier(location, reference)) {
          varDecl.funDecl.earliestForwardReference = location;
        }
      }

      // Normal local or global variable (not a closed over var) or a function
      // which we can call directly (if we are being asked to do a function lookup)
      if (varDecl.isGlobal || varDecl.nestingLevel == functions.size() ||
          (functionLookup && varDecl.funDecl != null)) {
        return varDecl;
      }

      // If we are looking for a function then return null since we found something that wasn't a function
      if (functionLookup) {
        return null;
      }

      // Nesting level is different and variable is a local variable so turn it into
      // a heap var
      varDecl.isHeapLocal = true;

      // If the variable is a parameter and we are resolving the initialisers for parameters of
      // the same function then it means we have a parameter that is closed over by a nested
      // closure/function within the initialiser of another parameter: def f(x,y={x}) {...}
      // We need to know this so we can get the wrapper method to turn the parameter into a
      // HeapLocal _before_ invoking the actual method. Usually we only need do the conversion
      // within the method itself if the parameter is then passed to a nested function but in
      // this case the nested function is created in the parameter initialiser which is compiled
      // within the wrapper method and so the HeapLocal needs to be passed to this nested function
      // before the actual method has been invoked.
      if (block != null && block.isResolvingParams && varDecl.paramVarDecl != null) {
        varDecl.paramVarDecl.isHeapLocal = true;
        varDecl.paramVarDecl.owner = varDecl.owner;
        varDecl.paramVarDecl.originalVarDecl = varDecl;
        varDecl.paramVarDecl.isPassedAsHeapLocal = true;
      }

      Expr.FunDecl currentFunc = functions.peek();

      // Check if we already have it in our list of heapLocal Params
      if (currentFunc.heapLocalParams.containsKey(name)) {
        return currentFunc.heapLocalParams.get(name);
      }

      // Add this var to our list of heap vars we need passed in to us.
      // Construct a new VarDecl since we will have our own slot that is local
      // to this function and add it the heapLocal Params of the currentFunc
      Expr.VarDecl newVarDecl = createVarDecl(name, varDecl, currentFunc);
      newVarDecl.originalVarDecl = varDecl;

      return newVarDecl;
    }

    if (name.equals(Utils.CAPTURE_VAR)) {
      error("Reference to regex capture variable " + location.getStringValue() + " in scope where no regex match has occurred", location);
    }
    else {
      error("Reference to unknown variable " + name, location);
    }
    return null;
  }

  private void throwIf(boolean condition, String msg, Token location) {
    if (condition) {
      throw new CompileError(msg, location);
    }
  }

  /////////////////////////////////////////////////

  /**
   * Find last statement and turn it into a return statement if not already a return statement. This is used to turn
   * implicit returns in functions into explicit returns to simplify the job of the Resolver and Compiler phases.
   */
  private Stmt explicitReturn(Stmt stmt, JacsalType returnType) {
      return doExplicitReturn(stmt, returnType);
  }

  private Stmt doExplicitReturn(Stmt stmt, JacsalType returnType) {
    if (stmt instanceof Stmt.Return) {
      // Nothing to do
      return stmt;
    }

    if (stmt instanceof Stmt.ThrowError) {
      // Nothing to do if last statement throws an exception
      return stmt;
    }

    if (stmt instanceof Stmt.Block) {
      List<Stmt> stmts = ((Stmt.Block) stmt).stmts.stmts;
      if (stmts.size() == 0) {
        throw new CompileError("Missing explicit/implicit return statement for function", stmt.location);
      }
      Stmt newStmt = explicitReturn(stmts.get(stmts.size() - 1), returnType);
      stmts.set(stmts.size() - 1, newStmt);   // Replace implicit return with explicit if necessary
      return stmt;
    }

    if (stmt instanceof Stmt.If) {
      Stmt.If ifStmt = (Stmt.If) stmt;
      if (ifStmt.trueStmt == null || ifStmt.falseStmt == null) {
        if (returnType.isPrimitive()) {
          throw new CompileError("Implicit return of null for  " +
                                 (ifStmt.trueStmt == null ? "true" : "false") + " condition of if statment not compatible with return type of " + returnType, stmt.location);
        }
        if (ifStmt.trueStmt == null) {
          ifStmt.trueStmt = returnStmt(ifStmt.ifToken, new Expr.Literal(new Token(NULL, ifStmt.ifToken)), returnType);
        }
        if (ifStmt.falseStmt == null) {
          ifStmt.falseStmt = returnStmt(ifStmt.ifToken, new Expr.Literal(new Token(NULL, ifStmt.ifToken)), returnType);
        }
      }
      ifStmt.trueStmt  = doExplicitReturn(((Stmt.If) stmt).trueStmt, returnType);
      ifStmt.falseStmt = doExplicitReturn(((Stmt.If) stmt).falseStmt, returnType);
      return stmt;
    }

    // Turn implicit return into explicit return
    if (stmt instanceof Stmt.ExprStmt) {
      Stmt.ExprStmt exprStmt = (Stmt.ExprStmt) stmt;
      Expr          expr     = exprStmt.expr;
      expr.isResultUsed = true;
      Stmt.Return returnStmt = returnStmt(exprStmt.location, expr, returnType);
      return returnStmt;
    }

    // If last statement is an assignment then value of assignment is the returned value.
    // We set a flag on the statement so that Compiler knows to leave result on the stack
    // and replace the assignment statement with a return wrapping the assignment expression.
    if (stmt instanceof Stmt.VarDecl) {
      Expr.VarDecl declExpr = ((Stmt.VarDecl) stmt).declExpr;
      // Don't use parameter declarations as value to return
      if (!declExpr.isExplicitParam) {
        declExpr.isResultUsed = true;
        Stmt.Return returnStmt = returnStmt(stmt.location, declExpr, returnType);
        return returnStmt;
      }
    }

    // If last statement is a function declaration then we return the MethodHandle for the
    // function as the return value of our function
    // We set a flag on the statement so that Compiler knows to leave result on the stack
    // and replace the assignment statement with a return wrapping the assignment expression.
    if (stmt instanceof Stmt.FunDecl) {
      Expr.FunDecl declExpr = ((Stmt.FunDecl)stmt).declExpr;
      declExpr.isResultUsed = true;
      declExpr.varDecl.isResultUsed = true;
      Stmt.Return returnStmt = returnStmt(stmt.location, declExpr, returnType);
      return returnStmt;
    }

    // For functions that return ANY there is an implicit "return null" even if last statement
    // does not have a value so replace stmt with list of statements that include the stmt and
    // then a "return null" statement.
    if (returnType.is(ANY)) {
      Stmt.Stmts stmts = new Stmt.Stmts();
      stmts.stmts.add(stmt);
      stmts.location = stmt.location;
      Stmt.Return returnStmt = returnStmt(stmt.location, new Expr.Literal(new Token(NULL, stmt.location)), returnType);
      stmts.stmts.add(returnStmt);
      return stmts;
    }

    // Other statements are not supported for implicit return
    throw new CompileError("Unsupported statement type " + stmt.getClass().getName()  + " for implicit return", stmt.location);
  }

  /**
   * Create a wrapper function for invoking the real function/method.
   * The job of the wrapper function is to support invocation from places where we don't know
   * how many arguments are required and what their types are.
   * E.g.:
   *   def f(int x, String y) {...}; def g = f; g(1,'xyz')
   * When we call via the variable 'g' we don't anything about which function it points to and
   * so we pass in an Object[] and invoke the wrapper function which extracts the arguments to then
   * call the real function.
   * The wrapper function also takes care of filling in the default values for any missing paremeters
   * and validates that the number of arguments passed is legal for the function.
   * TODO: the wrapper function will also take care of named argument passing once implemented.
   * @param funDecl
   * @return
   */
  private Expr.FunDecl createWrapperFunction(Expr.FunDecl funDecl) {
    Token startToken = funDecl.startToken;

    Function<TokenType,Token>        token      = type -> new Token(type, startToken);
    Function<String,Token>           identToken = name -> token.apply(IDENTIFIER).setValue(name);
    Function<String,Expr.Identifier> ident      = name -> new Expr.Identifier(identToken.apply(name));
    Function<Object,Expr.Literal>    intLiteral = value -> new Expr.Literal(new Token(INTEGER_CONST, startToken).setValue(value));
    Function<TokenType,Expr.Literal> typeLiteral= type  -> new Expr.Literal(token.apply(type));
    BiFunction<Expr,TokenType,Expr>  instOfExpr = (name,type) -> new Expr.Binary(name,
                                                                                 token.apply(INSTANCE_OF),
                                                                                 typeLiteral.apply(type));


    BiFunction<String,JacsalType,Stmt.VarDecl> createParam =
      (name,type) -> {
        Token nameToken = new Token(IDENTIFIER, funDecl.startToken).setValue(name);
        var declExpr = new Expr.VarDecl(nameToken,null);
        declExpr.type = type;
        declExpr.isParam = true;
        declExpr.isExplicitParam = true;
        return new Stmt.VarDecl(startToken,
                                declExpr);
      };

    List<Stmt.VarDecl> wrapperParams = new ArrayList<>();

    Expr.FunDecl wrapperFunDecl = new Expr.FunDecl(startToken,
                                                   identToken.apply(Utils.wrapperName(funDecl.methodName)),
                                                   funDecl.returnType,
                                                   wrapperParams);

    String sourceName = "_$source";
    wrapperParams.add(createParam.apply(sourceName, STRING));
    var sourceIdent = ident.apply(sourceName);

    String offsetName = "_$offset";
    wrapperParams.add(createParam.apply(offsetName, INT));
    var offsetIdent = ident.apply(offsetName);

    String argsName = "_$args";
    wrapperParams.add(createParam.apply(argsName, ANY));
    var argsIdent = ident.apply(argsName);

    Stmt.Stmts stmts = new Stmt.Stmts();
    List<Stmt> stmtList = stmts.stmts;
    stmtList.addAll(wrapperParams);

    //  : if (_$args instanceof Object[]) {
    var ifStmts = new Stmt.Stmts();
    var ifStmt  = new Stmt.If(startToken,
                              instOfExpr.apply(argsIdent, OBJECT_ARR),
                              new Stmt.Block(startToken, ifStmts),
                              new Stmt.ThrowError(startToken, sourceIdent, offsetIdent, "Named arguments not yet supported"));

    //  :   Object[] _$argArr = (Object[])_$args
    String argArrName = "_$argArr";
    ifStmts.stmts.add(varDecl(wrapperFunDecl, argArrName, JacsalType.OBJECT_ARR, argsIdent, startToken));
    // TODO: optimise to do a direct cast to Object[] rather than invoke castToObjectArr since we know it is safe

    //  :   int _$argCount = argArr.length
    var argArr = ident.apply(argArrName);
    String argCountName = "_$argCount";
    ifStmts.stmts.add(varDecl(wrapperFunDecl, argCountName, INT, new Expr.ArrayLength(startToken, argArr), startToken));
    var argCountIdent = ident.apply(argCountName);

    int paramCount = funDecl.parameters.size();

    // Special case to handle situation where Object[] passed as only arg within the Object[]
    // This is to handle iteration over maps where we pass Object[]{key,value} as our single arg
    // value. If the closure has multiple args then the first arg gets the the key and the second
    // one gets the value. If the closure only has a single arg then it is passed the Object[]
    // (which then looks like a list to the code in the closure).
    if (paramCount >= 2) {
      //:   if (_$argCount == 1 && _$argArr[0] instanceof Object[]) {
      //:     _$argArr = (Object[])_$argArr[0]
      //:     _$argCount = _$argArr.length
      //:   }
      var argCountIs1      = new Expr.Binary(argCountIdent, token.apply(EQUAL_EQUAL), intLiteral.apply(1));
      var arg0IsObjArr     = instOfExpr.apply(new Expr.ArrayGet(startToken, argArr, intLiteral.apply(0)), OBJECT_ARR);
      var ifTrueStmts      = new Stmt.Stmts();
      var getArg0          = new Expr.ArrayGet(startToken, argArr, intLiteral.apply(0));
      var assignToArgArr   = new Expr.VarAssign(argArr, token.apply(EQUAL), getArg0);
      assignToArgArr.isResultUsed = false;
      var assignToArgCount = new Expr.VarAssign(argCountIdent, token.apply(EQUAL), new Expr.ArrayLength(startToken, argArr));
      assignToArgCount.isResultUsed = false;
      ifTrueStmts.stmts.add(new Stmt.ExprStmt(startToken, assignToArgArr));
      ifTrueStmts.stmts.add(new Stmt.ExprStmt(startToken, assignToArgCount));

      var ifArg1isObjArr = new Stmt.If(startToken,
                                       new Expr.Binary(argCountIs1, token.apply(AMPERSAND_AMPERSAND), arg0IsObjArr),
                                       ifTrueStmts,
                                       null);
      ifStmts.stmts.add(ifArg1isObjArr);
    }

    int mandatoryCount = Utils.mandatoryParamCount(funDecl.parameters);
    if (mandatoryCount > 0) {
      //:   if (argCount < mandatoryCount) throw new RuntimeError("Missing mandatory arguments")
      var throwIf = new Stmt.If(startToken,
                                new Expr.Binary(argCountIdent, token.apply(LESS_THAN), intLiteral.apply(mandatoryCount)),
                                new Stmt.ThrowError(startToken, sourceIdent, offsetIdent, "Missing mandatory arguments"),
                                null);
      ifStmts.stmts.add(throwIf);
    }

    //  :   if (argCount > paramCount) throw new RuntimeError("Too many arguments)
    var throwIf = new Stmt.If(startToken,
                              new Expr.Binary(argCountIdent, token.apply(GREATER_THAN), intLiteral.apply(paramCount)),
                              new Stmt.ThrowError(startToken, sourceIdent, offsetIdent, "Too many arguments"),
                              null);
    ifStmts.stmts.add(throwIf);

    // For each parameter we now either load argument from Object[] or run the initialiser
    // and store value into a local variable.
    for (int i = 0; i < paramCount; i++) {
      var param = funDecl.parameters.get(i).declExpr;
      // If we have not reached mandatory arg count yet then we just create a var for the parameter
      // from the value in our Object[]
      if (i < mandatoryCount) {
        // :   def <param_i> = _$argArr[i]
        ifStmts.stmts.add(paramVarDecl(wrapperFunDecl, param, new Expr.ArrayGet(startToken, argArr, intLiteral.apply(i))));
      }
      else {
        // We have a parameter with an initialiser so load value from Object[] if present otherwise
        // use the initialiser
        // :   def <param_i> = i < _$argCount ? _$argArr[i] : <param_initialiser>
        var initialiser = new Expr.Ternary(new Expr.Binary(intLiteral.apply(i), token.apply(LESS_THAN), argCountIdent),
                                           new Token(QUESTION, startToken),
                                           new Expr.ArrayGet(startToken, argArr, intLiteral.apply(i)),
                                           new Token(COLON, startToken),
                                           param.initialiser);
        var varDecl = paramVarDecl(wrapperFunDecl, param, initialiser);
        ifStmts.stmts.add(varDecl);
      }
    }

    // Add original function as a statement so that it gets resolved as a nested function of wrapper
    Stmt.FunDecl realFunction = new Stmt.FunDecl(startToken, funDecl);
    realFunction.createVar = false;   // When in wrapper don't create a variable for the MethodHandle
    ifStmts.stmts.add(realFunction);

    // Now invoke the real function
    List<Expr> args = funDecl.parameters.stream()
                                        .map(p -> new Expr.LoadParamValue(p.declExpr.name, p.declExpr))
                                        .collect(Collectors.toList());
    ifStmts.stmts.add(returnStmt(startToken, new Expr.InvokeFunction(startToken, funDecl, args), funDecl.returnType));

    // : }
    stmtList.add(ifStmt);

    wrapperFunDecl.block      = new Stmt.Block(startToken, stmts);
    wrapperFunDecl.isWrapper  = true;

    // Return type must be ANY since if function is invoked by value, caller won't know return type
    wrapperFunDecl.returnType = ANY;
    wrapperFunDecl.varDecl = funDecl.varDecl;
    return wrapperFunDecl;
  }

  private Stmt.VarDecl varDecl(Expr.FunDecl ownerFunDecl, String name, JacsalType type, Expr init, Token token) {
    return varDecl(ownerFunDecl, new Token(IDENTIFIER, token).setValue(name), type, init);
  }

  private Stmt.VarDecl varDecl(Expr.FunDecl ownerFunDecl, Token name, JacsalType type, Expr init) {
    Expr.VarDecl varDecl = new Expr.VarDecl(name, init);
    varDecl.type = type;
    varDecl.isResultUsed = false;
    varDecl.owner = ownerFunDecl;
    return new Stmt.VarDecl(new Token(type == null ? VAR : type.tokenType(), name), varDecl);
  }

  private Stmt.VarDecl paramVarDecl(Expr.FunDecl ownerFunDecl, Expr.VarDecl param, Expr init) {
    Stmt.VarDecl varDecl = varDecl(ownerFunDecl, param.name, param.type, init);
    // Remember our original parameter because if it was declared as a "var" type then
    // we will need to change its type once we have been resolved and worked out what
    // our type is. And since we are using the initialiser from the parameter in the
    // wrapper function and not the real function we should remove the initialiser from
    // the parameter in the real function so we don't resolve it multiple times and we
    // don't compile it multiple times. Since the initialiser will be nulled out this
    // is another reason why we won't be able to work out the actual parameter type for
    // "var" types if we don't link to the parameter here.
    varDecl.declExpr.paramVarDecl = param;
    param.initialiser = new Expr.Noop(param.name);  // Use Noop rather than null so mandatory arg counting works
    varDecl.declExpr.isExplicitParam = true;
    return varDecl;
  }

  /**
   * Return true if t1 is earlier (in the _same_ code) then t2.
   * Return false if t1 is not earlier or if code is not the same.
   */
  boolean isEarlier(Token t1, Token t2) {
    if (!t1.getSource().equals(t2.getSource())) { return false; }
    return t1.getOffset() < t2.getOffset();
  }

  private Stmt.Return returnStmt(Token token, Expr expr, JacsalType type) {
    return new Stmt.Return(token, new Expr.Return(token, expr, type));
  }
}
