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

/**
 * Enum for different token types supported in Jacsal
 */
public enum TokenType {
  //= Single char tokens
  LEFT_PAREN, RIGHT_PAREN, LEFT_SQUARE, RIGHT_SQUARE, LEFT_BRACE, RIGHT_BRACE,
  BANG, DOLLAR, PERCENT, ACCENT, AMPERSAND, STAR, MINUS, PLUS, SLASH,
  EQUAL, LESS_THAN, GREATER_THAN, QUESTION, COMMA, DOT, GRAVE, BACKSLASH,
  PIPE, COLON, SEMICOLON,
  //SINGLE_QUOTE, DOUBLE_QUOTE,

  //= Double char tokens
  BANG_EQUAL, EQUAL_EQUAL, LESS_THAN_EQUAL, GREATER_THAN_EQUAL, QUESTION_COLON,
  //DOUBLE_SINGLE_QUOTE, DOUBLE_DOUBLE_QUOTE,
  AMPERSAND_AMPERSAND, PIPE_PIPE,
  MINUS_EQUAL, PLUS_EQUAL, SLASH_EQUAL, STAR_EQUAL, AMPERSAND_EQUAL, PIPE_EQUAL,
  ACCENT_EQUAL, GRAVE_EQUAL, QUESTION_EQUAL,
  DOUBLE_LESS_THAN, DOUBLE_GREATER_THAN,

  //= Triple char tokens
  //TRIPLE_SINGLE_QUOTE, TRIPLE_DOUBLE_QUOTE,
  TRIPLE_GREATER_THAN, DOUBLE_LESS_THAN_EQUAL, DOUBLE_GREATER_THAN_EQUAL,

  //= 4 char tokens
  TRIPLE_GREATER_THAN_EQUAL,

  //= Literals
  IDENTIFIER, STRING_CONST, INTEGER_CONST, LONG_CONST, DOUBLE_CONST, DECIMAL_CONST,

  //= Keywords
  DEF, VAR, INT, FLOAT, DOUBLE, STRING, VOID,
  FOR, IF, WHILE, IT,
  CLASS, INTERFACE, EXTENDS, IMPLEMENTS,
  IMPORT, AS,
  INSTANCE_OF, BANG_INSTANCE_OF,
  RETURN,

  //= Special
  EOL,            // End of line
  EOF             // End of file
}
