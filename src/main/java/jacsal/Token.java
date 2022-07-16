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

import java.util.stream.IntStream;

/**
 * This class represents a single token parsed from Jacsal source code.
 * Each token keeps track of where it is in terms of line and column
 * number as well has keeping a reference to the original source code.
 * This makes it possible to output errors showing exactly where in the
 * source code the error occurs including the line of source code itself.
 *
 * The toString() method of the token outputs the exact text that was
 * parsed to generate the token.
 *
 * Tokens keep a reference to the subsequent token in the parse stream.
 * This makes it easy for the Tokeniser to support peeking at an arbitrary
 * number of tokens ahead in the parse stream and then rewind to the current
 * position and continue parsing from there.
 */

public class Token {
  private Token     next;       // Next token in the stream of tokens
  private TokenType type;       // Token type
  private String    source;     // Source code of the script being compiled
  private int       offset;     // The position/offset in source where token starts
  private int       line;       // Line number where token is
  private int       column;     // Column where token is
  private int       length;     // Length of token
  private String    value;      // String value of token (for string literals)
  private boolean   isKeyword;  // Whether token is a keyword or not

  /**
   * Partially construct a token whose type and length is not yet known
   * @param source the source code of the script
   * @param offset the offset in source where token starts
   * @param line   line number of token
   * @param column column number of token
   */
  public Token(String source, int offset, int line, int column) {
    this.source = source;
    this.offset = offset;
    this.line   = line;
    this.column = column;
  }

  /**
   * Set next token (once we have parsed the following one)
   * @param next  the next token
   */
  public void setNext(Token next) {
    this.next = next;
  }

  /**
   * Get next token (if we have already parsed ahead)
   * @return  the next token or null if we have not parsed any further
   */
  public Token getNext() {
    return next;
  }

  /**
   * Set type of token
   * @param type  the TokenType
   * @return the token
   */
  public Token setType(TokenType type) {
    this.type = type;
    return this;
  }

  /**
   * Get the type of the token
   * @return the TokenType for the token
   */
  public TokenType getType() { return type; }

  /**
   * Check if type of token matches type passed in
   * @param type  the type to check
   * @return true if type matches
   */
  public boolean is(TokenType type) {
    return this.type == type;
  }

  /**
   * Check if type of token is not the same as the type passed in
   * @param type  the type
   * @return true if type does not matches
   */
  public boolean isNot(TokenType type) {
    return this.type != type;
  }

  /**
   * Set length of token
   * @param length  the length
   * @return the token
   */
  public Token setLength(int length) {
    this.length = length;
    return this;
  }

  /**
   * Set flag to indicate whether token is a keyword or not. This is
   * used in scenarios where a bare field name can be used and allows
   * keywords to be used as field names
   * @param isKeyword  true if token is a keyword
   * @return true if token is a keyword
   */
  public Token setKeyword(boolean isKeyword) {
    this.isKeyword = isKeyword;
    return this;
  }

  public boolean isKeyword()    { return isKeyword; }
  public int     getLine()      { return line;   }
  public int     getColumn()    { return column; }

  /**
   * Get the line of source code with an additional line showing where token starts (used for errors)
   * @return the source code showing token location
   */
  public String getMarkedSourceLine() {
    return String.format("%s%n%s^", source.split("\n")[line - 1], " ".repeat(column - 1));
  }

  /**
   * Return string value of token
   * @return  the string value of the token
   */
  public String getValue() {
    return value == null ? source.substring(offset, offset + length) : value;
  }

  /**
   * Set string value of token for string literals which may have escape characters
   * and so cannot not be directly reproduced from the source code
   * @param value  the string value
   * @return the token
   */
  public Token setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public String toString() {
    return "Token{" +
           "next=" + next +
           ", value='" + getValue() + '\'' +
           ", line=" + line +
           ", column=" + column +
           ", offset=" + offset +
           ", length=" + length +
           '}';
  }
}
