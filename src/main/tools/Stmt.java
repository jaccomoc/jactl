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
import java.util.Map;
import java.util.HashMap;
import org.objectweb.asm.Label;

/**
 * Stmt classes for our AST.
 */
class Stmt {

  Token      location = null;

  /**
   * Each script is parsed into a Script object. This is true even if the script
   * is just a declaration of a class (or classes) and there are no actual statements
   * to execute outside of the class declaration.
   */
  class Script extends Stmt {
    FunDecl function;
  }

  /**
   * Represents a sequence of statments.
   */
  class Stmts extends Stmt {
    List<Stmt> @stmts = new ArrayList<>();
  }

  /**
   * Represents a block of statments. This class represents a scope for any variables
   * declared by statements within the block.
   */
  class Block extends Stmt {
    Token openBrace;
    Stmts stmts;
    Map<String,Expr.VarDecl> @variables = new HashMap<>();
    int @slotsUsed = 0;   // How many local var slots used by vars in this block
  }

  /**
   * If statement with condition and statement(s) to execute if true
   * and statement(s) to execute if false.
   */
  class If extends Stmt {
    Token ifToken;
    Expr condtion;
    Stmt trueStmt;
    Stmt falseStmt;
  }

  /**
   * Variable declaration with optional initialiser. Statement wraps the corresponding
   * Expr type where the work is done.
   */
  class VarDecl extends Stmt {
    Token typeToken;
    Expr.VarDecl declExpr;
  }

  /**
   * Function declaration
   */
  class FunDecl extends Stmt {
    Token      name;
    JacsalType returnType;
    Block      @block;
    int        @slotIdx;          // Current slot available for allocation
    int        @maxSlot;          // Maximum slot used for local vars
    boolean    @returnValue;      // Value used as implicit return from function
    Stmt.While @currentWhileLoop; // Used during parsing to find target of break/continue stmts
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
  }

  /**
   * Return statement
   */
  class Return extends Stmt {
    Token      returnToken;
    Expr       expr;
    JacsalType returnType;      // Return type of the function we are embedded in
  }

  /**
   * Break statement
   */
  class Break extends Stmt {
    Token breakToken;
    While whileLoop;
  }

  /**
   * Continue statement
   */
  class Continue extends Stmt {
    Token continueToken;
    While whileLoop;
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
   * Print statement
   */
  class Print extends Stmt {
    Token   printToken;
    Expr    expr;
    boolean newLine;    // Whether to print newline
  }
}
