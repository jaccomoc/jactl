/*
 * Copyright © 2022-2026 James Crawford
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

import java.util.Arrays;

/**
 * Enum for different token types supported in Jactl
 */
public enum TokenType {
  //= Single char tokens
  LEFT_PAREN("("),
  RIGHT_PAREN(")"),
  LEFT_SQUARE("["),
  RIGHT_SQUARE("]"),
  LEFT_BRACE("{"),
  RIGHT_BRACE("}"),
  BANG("!"),
  PERCENT("%"),
  ACCENT("^"),
  AMPERSAND("&"),
  STAR("*"),
  MINUS("-"),
  PLUS("+"),
  SLASH("/"),
  EQUAL("="),
  LESS_THAN("<"),
  GREATER_THAN(">"),
  QUESTION("?"),
  COMMA(","),
  DOT("."),
  GRAVE("~"),
  BACKSLASH("\\"),
  PIPE("|"),
  COLON(":"),
  SEMICOLON(";"),
  SINGLE_QUOTE("'"),
  DOUBLE_QUOTE("\""),
  UNDERSCORE("_"),

  //= Double char tokens
  QUESTION_QUESTION("??"),
  PERCENT_PERCENT("%%"),
  BANG_EQUAL("!="),
  EQUAL_EQUAL("=="),
  LESS_THAN_EQUAL("<="),
  GREATER_THAN_EQUAL(">="),
  QUESTION_COLON("?:"),
  AMPERSAND_AMPERSAND("&&"),
  PIPE_PIPE("||"),
  ARROW("->"),
  MINUS_EQUAL("-="),
  PLUS_EQUAL("+="),
  SLASH_EQUAL("/="),
  STAR_EQUAL("*="),
  AMPERSAND_EQUAL("&="),
  PIPE_EQUAL("|="),
  ACCENT_EQUAL("^="),
  QUESTION_EQUAL("?="),
  DOUBLE_LESS_THAN("<<"),
  DOUBLE_GREATER_THAN(">>"),
  MINUS_MINUS("--"),
  PLUS_PLUS("++"),
  STAR_STAR("**"),
  EQUAL_GRAVE("=~"),
  BANG_GRAVE("!~"),
  PERCENT_EQUAL("%="),
  STAR_STAR_EQUAL("**="),
  QUESTION_DOT("?."),
  QUESTION_SQUARE("?["),
  DOLLAR_BRACE("${"),

  //= Triple char tokens
  PERCENT_PERCENT_EQUAL("%%="),
  TRIPLE_GREATER_THAN(">>>"),
  DOUBLE_LESS_THAN_EQUAL("<<="),
  DOUBLE_GREATER_THAN_EQUAL(">>="),
  TRIPLE_EQUAL("==="),
  BANG_EQUAL_EQUAL("!=="),

  COMPARE("<=>"), // <=>

  //= 4 char tokens
  TRIPLE_GREATER_THAN_EQUAL(">>>="),

  //= Literals
  DOLLAR_IDENTIFIER(),
  IDENTIFIER(),
  STRING_CONST(),
  BYTE_CONST(),
  INTEGER_CONST(),
  LONG_CONST(),
  DOUBLE_CONST(),
  DECIMAL_CONST(),

  //= String expressions
  EXPR_STRING_START(),
  EXPR_STRING_END(),
  REGEX_SUBST_START(),
  REGEX_REPLACE(),

  //= Keywords
  DEF("def"),
  VAR("var"),
  BOOLEAN("boolean"),
  BYTE("byte"),
  INT("int"),
  LONG("long"),
  DOUBLE("double"),
  DECIMAL("Decimal"),
  STRING("String"),
  OBJECT("Object"),
  MAP("Map"),
  LIST("List"),
  FOR("for"),
  UNTIL("until"),
  IF("if"),
  UNLESS("unless"),
  WHILE("while"),
  ELSE("else"),
  CONTINUE("continue"),
  BREAK("break"),
  CLASS("class"),
  EXTENDS("extends"),
  PACKAGE("package"),
  STATIC("static"),
  IMPORT("import"),
  AS("as"),
  TRUE("true"),
  FALSE("false"),
  NULL("null"),
  IN("in"),
  BANG_IN("!in"),
  INSTANCE_OF("instanceof"),
  BANG_INSTANCE_OF("!instanceof"),
  RETURN("return"),
  NEW("new"),
  AND("and"),
  OR("or"),
  NOT("not"),
  DO("do"),
  PRINT("print"),
  PRINTLN("println"),
  BEGIN("BEGIN"),
  END("END"),
  DIE("die"),
  EVAL("eval"),
  CONST("const"),
  SWITCH("switch"),
  DEFAULT("default"),

  // Not used but reserved
  VOID("void"),
  SEALED("sealed"),
  IMPLEMENTS("implements"),
  INTERFACE("interface"),
  FINAL("final"),

  //= Special
  EOL(),            // End of line
  EOF(),            // End of file

  WHITESPACE(),     // Spaces/tabs
  COMMENT(),        // Comment
  ERROR(),          // Error state (e.g. EOF in multi-line comment/string)

  // Internal use only
  NUMBER();

  public final String asString;
  private boolean isAssignmentLike;
  private boolean isNumericOperator;
  private boolean isBooleanOperator;
  private boolean isBitOperator;
  private boolean isBitShift;
  private boolean isNumber;
    
  TokenType() {
    this(null);
  }
  
  TokenType(String str) {
    this.asString = str;
  }

  private void _init() {
    isAssignmentLike  = _isAssignmentLike();
    isNumericOperator = _isNumericOperator();
    isBooleanOperator = _isBooleanOperator();
    isBitOperator     = _isBitOperator();
    isBitShift        = _isBitShift();
    isNumber          = _isNumber();
  }
  
  public boolean is(TokenType type) {
    return this == type;
  }
  
  public boolean is(TokenType... types) {
    for (TokenType type: types) {
      if (this == type) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if operator is an operator that mutates the value of the variable or field
   * that it acts on. This is true for assignment and any assignment like operator such
   * as +=, -=, etc
   * @return true if operator is an assignment operator
   */
  public boolean isAssignmentLike() {
    return isAssignmentLike;
  }

  public boolean isNumericOperator() {
    return this.isNumericOperator;
  }
  
  public boolean isBooleanOperator() {
    return isBooleanOperator;
  }
  
  public boolean isBitOperator() {
    return isBitOperator;
  }
  
  public boolean isBitShift() {
    return isBitShift;
  }
  
  public boolean isNumber() {
    return isNumber;
  }
    
  //////////////////////////
  
  private boolean _isNumericOperator() {
    switch (this) {
      case PLUS:
      case MINUS:
      case STAR:
      case SLASH:
      case PERCENT:
      case PERCENT_PERCENT:
        return true;
      default:
        return false;
    }
  }

  private boolean _isAssignmentLike() {
    return this.is(EQUAL, STAR_STAR_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, PERCENT_PERCENT_EQUAL,
                   PLUS_EQUAL, MINUS_EQUAL, DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL,
                   TRIPLE_GREATER_THAN_EQUAL, AMPERSAND_EQUAL, PIPE_EQUAL, ACCENT_EQUAL, QUESTION_EQUAL);
  }

  private boolean _isBooleanOperator() {
    return this.is(AMPERSAND_AMPERSAND, PIPE_PIPE, BANG, BANG_EQUAL, EQUAL_EQUAL, TRIPLE_EQUAL, BANG_EQUAL_EQUAL,
                   LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, IN, BANG_IN, INSTANCE_OF,
                   BANG_INSTANCE_OF, QUESTION_QUESTION);
  }

  private boolean _isBitOperator() {
    return this.is(AMPERSAND, AMPERSAND_EQUAL, PIPE, PIPE_EQUAL, ACCENT, ACCENT_EQUAL, GRAVE,
                   DOUBLE_LESS_THAN, DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN,
                   DOUBLE_GREATER_THAN_EQUAL, TRIPLE_GREATER_THAN, TRIPLE_GREATER_THAN_EQUAL);
  }

  private boolean _isBitShift() {
    return this.is(DOUBLE_LESS_THAN,DOUBLE_GREATER_THAN,TRIPLE_GREATER_THAN);
  }

  private boolean _isNumber() { return this.is(BYTE_CONST,INTEGER_CONST,LONG_CONST,DOUBLE_CONST,DECIMAL_CONST); }

  public static final TokenType[] TOKENS_WITH_UPPERCASE;
  static{
    TOKENS_WITH_UPPERCASE = Arrays.stream(TokenType.values())
                                  .filter(tt -> tt.asString != null)
                                  .filter(tt -> Character.isUpperCase(tt.asString.charAt(0)))
                                  .toArray(TokenType[]::new);
  }
  
  @Override
  public String toString() {
    return asString != null ? asString : super.toString();
  }

  static {
    for (TokenType type: values()) {
      type._init();
    }
  }
  
  public static void main(String[] args) {
    Arrays.stream(io.jactl.TokenType.values()).forEach(t -> {
      System.out.println("  IElementType " + t.name() + " = new JactlTokenType(\"" + t.name() + "\", TokenType." + t.name() + ");");
    });
  }

}
