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

package jacsal;

import java.util.List;
import java.util.ArrayList;

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
    Token identifier;
  }

  class ExprString extends Expr {
    Token exprStringStart;
    List<Expr> @exprList = new ArrayList<>();
  }
}
