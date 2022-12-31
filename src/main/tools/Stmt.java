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
#  ./GenerateClasses.pl Stmt.java > ../java/jacsal/Stmt.java
#

package jacsal;

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
class Stmt {

  Token      location   = null;
  boolean    isResolved = false;
  boolean    isAsync    = false;   // Whether statement contains an async call

  /**
   * Represents a sequence of statments.
   */
  class Stmts extends Stmt {
    List<Stmt> @stmts = new ArrayList<>();

    int @currentIdx;     // Which statement we are currently resolving
  }

  /**
   * Represents a block of statments. This class represents a scope for any variables
   * declared by statements within the block.
   */
  class Block extends Stmt {
    Token                    openBrace;
    Stmts                    stmts;
    List<Stmt.FunDecl>       @functions    = new ArrayList<>();

    Map<String,Expr.VarDecl> @variables  = new LinkedHashMap<>();

    boolean @isBeginBlock;
    boolean @isEndBlock;

    // Used to track which Stmt.Stmts we are currently resolving in case we need to insert a new statement
    // at Resolve time
    Stmt.Stmts               @currentResolvingStmts;

    boolean @isResolvingParams = false;   // Used during resolution to tell if we are resolving function/closure
                                          // parameters so we can tell when we need to convert a declared parameter
                                          // into one that is passed as a HeapLocal (because it is closed over by
                                          // an initialiser for another parameter of the same function).
  }

  /**
   * If statement with condition and statement(s) to execute if true
   * and statement(s) to execute if false.
   */
  class If extends Stmt {
    Token ifToken;
    Expr  condition;
    Stmt  trueStmt;
    Stmt  falseStmt;
  }

  /**
   * Class declaration
   */
  class ClassDecl extends Stmt {
    Token                name;
    String               packageName;
    Token                baseClassToken;
    JacsalType           baseClass;
    boolean              isInterface;
    List<Stmt.Import>    @imports;
    Stmt.Block           @classBlock;
    List<Stmt.FunDecl>   @methods = new ArrayList<>();
    Stmt.FunDecl         @initMethod;
    List<Stmt.ClassDecl> @innerClasses = new ArrayList<>();

    List<List<Expr>>     @interfaces = new ArrayList<>();

    Stmt.FunDecl             @scriptMain;   // Mainline of script
    Map<String,Expr.VarDecl> @fieldVars = new LinkedHashMap<>();
    Expr.VarDecl             @thisField;

    // Used by Parser and Resolver
    Deque<Expr.FunDecl>      @nestedFunctions = new ArrayDeque<>();

    ClassDescriptor @classDescriptor;
  }

  /**
   * Import statement
   */
  class Import extends Stmt {
    Token token;
    List<Expr> className;
    Token as;
  }

  /**
   * Variable declaration with optional initialiser. Statement wraps the corresponding
   * Expr type where the work is done.
   */
  class VarDecl extends Stmt {
    Token        name;
    Expr.VarDecl declExpr;
  }

  /**
   * Function declaration
   */
  class FunDecl extends Stmt {
    Token        startToken;   // Either identifier for function decl or start brace for closure
    Expr.FunDecl declExpr;

    // Create a var that points to MethodHandle (which points to wrapper).
    // Exception is when inside wrapper function we don't create var that points to
    // the function since the MethodHandle must go through the wrapper function.
    boolean      @createVar = true;
  }

  /**
   * While and For loop
   */
  class While extends Stmt {
    Token whileToken;
    Expr  condition;
    Stmt  @body;
    Stmt  @updates;       // used for For loops
    Label @endLoopLabel;  // where to jump to on break stmt
    Label @continueLabel; // where to jump to on a continue stmt
    int   @stackDepth;    // depth of stack where while loop is (used by continue/break)
  }

  /**
   * Return statement
   */
  class Return extends Stmt {
    Token       returnToken;
    Expr.Return expr;
  }

  /**
   * Statements that are just an expression. This can be used, for example, where there
   * is an expression at the end of a function without having to exlicitly have the "return"
   * keyword to indicate that the expression is the return value of the function.
   * Other types of statements that are just an expression include simple assignments. Since
   * an assignment has a value (the value being assigned), an assignment is actually an expression.
   */
  class ExprStmt extends Stmt {
    Token exprLocation;
    Expr expr;
  }

  /**
   * Internal use only - throw RuntimeError
   */
  class ThrowError extends Stmt {
    Token token;
    Expr.Identifier source;
    Expr.Identifier offset;
    String msg;
  }
}
