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
#  ./GenerateClasses.pl Expr.java > ../java/jacsal/Expr.java
#

package jacsal;

import java.util.List;
import java.util.ArrayList;
import org.objectweb.asm.Label;

/**
 * Expr classes for our AST.
 */
class Expr {

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

  class Binary extends Expr {
    Expr  left;
    Token operator;
    Expr  right;
  }

  class PrefixUnary extends Expr {
    Token operator;
    Expr  expr;
  }

  class PostfixUnary extends Expr {
    Expr  expr;
    Token operator;
  }

  class Literal extends Expr {
    Token value;
  }

  class Identifier extends Expr {
    Token        identifier;
    Expr.VarDecl @varDecl;
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
  class VarDecl extends Expr {
    Token      name;
    Expr       initialiser;
    int        @slot;        // Which local variable slot to use
    Label      @declLabel;   // Where variable comes into scope (for debugger)
  }

  /**
   * When variable used as lvalue in an assignment
   */
  class VarAssign extends Expr {
    Identifier identifierExpr;
    Token      operator;
    Expr       expr;
  }
}
