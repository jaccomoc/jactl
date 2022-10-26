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

#
# Template file for generating classes.
# Fields that should not be included in constructor should be given name starting
# with '@' to indicate they are an attribute. The '@' will be stripped during
# generation.
# Comments using '#' will be stripped from generated code but everything else will
# remain as is.
#
# To generate the code run the GenerateClasses.pl perl script from this directory:
#  ./GenerateClasses.pl Expr.java > ../java/pragma/Expr.java
#

package pragma;

import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import pragma.runtime.FunctionDescriptor;

import static pragma.PragmaType.HEAPLOCAL;

/**
 * Expr classes for our AST.
 */
class Expr {

  Token      location;
  PragmaType type;
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
    return ident.varDecl.type.is(PragmaType.FUNCTION) && ident.varDecl.funDecl != null;
  }

  class Binary extends Expr {
    Expr  left;
    Token operator;
    Expr  right;
    boolean @createIfMissing = false;  // Used for field access used as lvalues
    Token @originalOperator;           // When -- or ++ is turned into x = x + 1 this is the actual --/++ op
  }

  class RegexMatch extends Expr {
    Expr    string;
    Token   operator;
    Expr    pattern;
    String  modifiers;
    boolean @isSubstitute = false;
    boolean implicitItMatch;   // True if standalone /regex/ which we then implicitly match against "it"
    VarDecl @captureArrVarDecl;
  }

  class RegexSubst extends RegexMatch {
    Expr    replace;
    boolean isComplexReplacement;   // True if replacement string has embedded expressions
    { isSubstitute = true; }
  }

  /**
   * Ternary expression - only used for:
   *     cond ? trueExpr : falseExpr
   */
  class Ternary extends Expr {
    Expr  first;
    Token operator1;
    Expr  second;
    Token operator2;
    Expr  third;
  }

  class PrefixUnary extends Expr implements ManagesResult {
    Token operator;
    Expr  expr;
  }

  class PostfixUnary extends Expr implements ManagesResult {
    Expr  expr;
    Token operator;
  }

  class Call extends Expr {
    Token      token;
    Expr       callee;
    List<Expr> args;

    // If we are a call to an arbitrary function/closure then it is possible that the
    // function we are calling returns an Iterator:  def f = x.map; f()
    // In this case we need to check that if the result is not immediately used as the
    // target of a chained method call (f().each()) and the result was an Iterator then
    // the result must be converted to a List.
    boolean @isMethodCallTarget;
  }

  class MethodCall extends Expr {
    Token      leftParen;
    Expr       parent;
    Token      accessOperator; // Either '.' or '?.'
    String     methodName;     // Either the method name or field name that holds a MethodHandle
    List<Expr> args;

    FunctionDescriptor @methodDescriptor;

    // True if result of method call becomes the target of the next method call. This is used so
    // that we can allow Iterators to be the result of a list.map() call which is then itself used
    // as the target of another map() call (or call that can operator on an Iterator). Otherwise
    // we need to convert the Iterator into a List since Iterators don't really exist at the Pragma
    // language level and are only used as an implementation detail for some iteration methods.
    // E.g.: x.map().map().each()
    // This means that the x.map() can return an iterator that is then used in the next .map() which
    // returns another iterator that is used in the .each() call.
    // If the result is not to be used by another method then we convert the Iterator into a List.
    // That way x.map{} which in theory does nothing but might have side effects if the closure has
    // a side effect will still run and cause the side effects to happen even though the end result
    // is not actually used.
    boolean isMethodCallTarget = false;
  }

  class Literal extends Expr {
    Token value;
  }

  class ListLiteral extends Expr {
    Token start;
    List<Expr> @exprs = new ArrayList<>();
  }

  class MapLiteral extends Expr {
    Token start;
    List<Pair<Expr,Expr>> @entries = new ArrayList<>();
  }

  class Identifier extends Expr {
    Token              identifier;
    Expr.VarDecl       @varDecl;               // for variable references
    boolean            @couldBeFunctionCall = false;
    FunctionDescriptor getFuncDescriptor() { return varDecl.funDecl.functionDescriptor; }
  }

  class ExprString extends Expr {
    Token exprStringStart;
    List<Expr> @exprList = new ArrayList<>();
  }

  /**
   * Variable declaration with optional initialiser.
   * This is done as an expression in case value of the assignment
   * is returned implicitly from a function/closure.
   */
  class VarDecl extends Expr implements ManagesResult {
    Token        name;
    Expr         initialiser;
    Expr.FunDecl @owner;               // Which function variable belongs to (for local vars)
    boolean      @isGlobal;            // Whether global (bindings var) or local
    boolean      @isHeapLocal;         // Is this a heap local var
    boolean      @isPassedAsHeapLocal; // If we are an explicit parameter and HeapLocal is passed to us from wrapper
    boolean      @isParam;             // True if variable is a parameter of function (explicit or implicit)
    boolean      @isExplicitParam;     // True if explicit declared parameter of function
    int          @slot = -1;           // Which local variable slot
    int          @nestingLevel;        // What level of nested function owns this variable (1 is top level)
    Label        @declLabel;           // Where variable comes into scope (for debugger)
    Expr.FunDecl @funDecl;             // If type is FUNCTION then this is the function declaration
    VarDecl      @parentVarDecl;       // If this is a HeapLocal parameter then this is the VarDecl from parent
    VarDecl      @originalVarDecl;     // VarDecl for actual original variable declaration

    // When we are in the wrapper function we create a variable for every parameter.
    // This points to the parameter so we can turn it into HeapLocal if necessary and
    // to set its type (if it was declared as "var") once we know the type of the initialiser.
    VarDecl      @paramVarDecl;

    Type descriptorType() { return isPassedAsHeapLocal ? HEAPLOCAL.descriptorType() : type.descriptorType(); }
  }

  /**
   * Function declaration
   * We make this an expression so we can have last statement in a block be a function declaration
   * and have it then returned as the return value of the function.
   */
  class FunDecl extends Expr implements ManagesResult {
    Token              startToken;   // Either identifier for function decl or start brace for closure
    Token              nameToken;    // Null for closures and script main
    PragmaType         returnType;
    List<Stmt.VarDecl> parameters;
    Stmt.Block         @block;

    FunctionDescriptor @functionDescriptor;
    Expr.VarDecl       @varDecl;            // For the variable that we will create to hold our MethodHandle

    boolean            @isWrapper;   // Whether this is the wrapper function or the real one
    Expr.FunDecl       @wrapper;     // The wrapper method that handles var arg and named arg invocations

    boolean    @isScriptMain = false; // Whether this is the funDecl for the script main function
    boolean    @isStatic = false;
    int        @closureCount = 0;
    Stmt.While @currentWhileLoop;     // Used by Resolver to find target of break/continue stmts

    // Stack of blocks used during Resolver phase to track variables and which scope they
    // are declared in and used during Parser phase to track function declarations so we
    // can handle forward references during Resolver phase
    Deque<Stmt.Block> @blocks = new ArrayDeque<>();

    // Which heap locals from our parent we need passed in to us
    LinkedHashMap<String,Expr.VarDecl> @heapLocalParams = new LinkedHashMap<>();

    // Remember earliest (in the code) forward reference to us so we can make sure that
    // no variables we close over are declared after that reference
    Token @earliestForwardReference;

    public boolean isClosure() { return nameToken == null; }
  }

  /**
   * When variable used as lvalue in an assignment
   */
  class VarAssign extends Expr implements ManagesResult {
    Identifier identifierExpr;
    Token      operator;
    Expr       expr;
  }

  /**
   * When variable used as lvalue in an assignment of type +=, -=, ...
   */
  class VarOpAssign extends Expr implements ManagesResult {
    Identifier identifierExpr;
    Token      operator;
    Expr       expr;
  }

  /**
   * Used to represent assignment to a field or list element
   */
  class Assign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;
  }

  /**
   * Used to represent assignment to a field or list element where assignment is done with
   * one of the +=, -=, *=, ... operations
   */
  class OpAssign extends Expr implements ManagesResult {
    Expr  parent;
    Token accessType;
    Expr  field;
    Token assignmentOperator;
    Expr  expr;

    // true if value before operation should be result - used for post inc/dec of fields
    // where we covert to a binary += or -= and then need the before value as the result
    boolean @isPreIncOrDec;
  }

  /**
   * Marker for when we need an Expr but we already have a value on the stack. This is used
   * for x += y type expressions which turn into x = _Noop_ + y where _Noop_ is used to mark
   * the place where the x value will be inserted into the binary expressions.
   */
  class Noop extends Expr {
    Token operator;
  }

  /**
   * Closure definition
   */
  class Closure extends Expr implements ManagesResult {
    Token        startToken;
    Expr.FunDecl funDecl;
    boolean      noParamsDefined;
  }

  class Return extends Expr implements ManagesResult {
    Token      returnToken;
    Expr       expr;
    PragmaType returnType;      // Return type of the function we are embedded in
    FunDecl    @funDecl;
  }

  class Print extends Expr {
    Token printToken;
    Expr  expr;
    boolean newLine;    // Whether to print newline
  }

  /**
   * Used to turn a list of statements into an expression so that we can support "do {...}":
   *   x < max or do { x++; y-- } and return x + y;
   */
  class Block extends Expr {
    Token      token;
    Stmt.Block block;
  }

  ////////////////////////////////////////////////////////////////////

  // = Used when generating wrapper method

  /**
   * Array length
   */
  class ArrayLength extends Expr implements ManagesResult {
    Token token;
    Expr array;
  }

  /**
   * Array get
   */
  class ArrayGet extends Expr implements ManagesResult {
    Token token;
    Expr  array;
    Expr  index;
  }

  /**
   * LoadParamValue - load value needed for a param.
   * Can be HeapLocal if isPassedAsHeapLocal is set or just a standard value.
   */
  class LoadParamValue extends Expr implements ManagesResult {
    Token name;
    Expr.VarDecl paramDecl;
    Expr.VarDecl @varDecl;
  }

  /**
   * Invoke a user function - internal use only
   */
  class InvokeFunction extends Expr implements ManagesResult {
    Token        token;
    Expr.FunDecl funDecl;
    List<Expr>   args;
  }

  /**
   * Invoke a internal utility function
   */
  class InvokeUtility extends Expr implements ManagesResult {
    Token       token;
    Class       clss;
    String      methodName;
    List<Class> paramTypes;
    List<Expr>  args;
  }
}
