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

import io.jactl.runtime.Location;

import java.util.List;

import static io.jactl.TokenType.*;

/**
 * This class represents a single token parsed from Jactl source code.
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

public class Token extends Location {
  private Token     next;         // Next token in the stream of tokens
  private TokenType type;         // Token type
  private int       length;       // Length of token
  private Object    value;        // Value of token - int, long, string, etc if literal
  private boolean   isKeyword;    // Whether token is a keyword or not

  private JactlError error;

  /**
   * Partially construct a token whose type and length is not yet known
   * @param source the source code of the script
   * @param offset the offset in source where token starts
   */
  public Token(String source, int offset) {
    super(source, offset);
  }

  /**
   * Partially construct a token whose type and length is not yet known
   * @param source  the source code of the script
   * @param offset  the offset in source where token starts
   * @param line    the line of source code
   * @param lineNum the line number
   * @param col     the columne
   */
  public Token(String source, int offset, String line, int lineNum, int col) {
    super(source, offset, line, lineNum, col);
  }

  /**
   * Construct a new token of different type from an existing token
   * @param type   the new token type
   * @param token  the token to copy values from
   */
  public Token(TokenType type, Token token) {
    super(token);
    this.type      = type;
    this.length    = token.length;
    this.value     = token.value;
    this.isKeyword = token.isKeyword;
  }

  /**
   * Construct a Token that wraps an exception. This is used when throwing Tokeniser
   * exceptions during lookahead to be able to remember what exceptions was thrown
   * and reproduce it after rewind.
   * @param e  the exception to wrap
   */
  public Token(JactlError e) {
    super(e.getLocation());
    this.error = e;
    this.type = EOF;
  }

  public boolean isError() {
    return error != null;
  }

  public JactlError getError() {
    return error;
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
   * Check if type of token matches any of types passed in
   * @param types  the types to check
   * @return true if type matches one of the supplied types
   */
  public boolean is(TokenType... types) {
    for (TokenType type: types) {
      if (type == this.type) {
        return true;
      }
    }
    return false;
  }

  public boolean is(List<TokenType> types) {
    return is(types.toArray(new TokenType[types.size()]));
  }

  /**
   * Check if type of token is not the same as the types passed in
   * @param types  the types
   * @return true if type does not match any of supplied types
   */
  public boolean isNot(TokenType... types) {
    for (TokenType type: types) {
      if (this.type == type) {
        return false;
      }
    }
    return true;
  }

  public boolean isNumericOperator() {
    return getType().isNumericOperator();
  }

  public boolean isBooleanOperator() {
    return getType().isBooleanOperator();
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

  public Token setEnd(int endOffset) {
    this.length = endOffset - offset;
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

  public boolean isKeyword()    {
    return isKeyword;
  }

  /**
   * Return value of token. For literals returns the Integer, Long, Double, etc.
   * For non-literals returns the string representation of the token.
   * @return  the value of the token
   */
  public Object getValue() {
    if (is(NULL)) {
      return null;
    }
    return value == null ? getChars() : value;
  }

  /**
   * Get the value of the token when value is a String
   * @return the string value of the token
   */
  public String getStringValue() {
    return (String)getValue();
  }

  /**
   * Get the actual characters of the token in the source
   * @return the actual characters parsed for the token
   */
  public String getChars() {
    return source.substring(getOffset(), getOffset() + length);
  }

  public Token newIdent(String name) {
    return new Token(IDENTIFIER, this).setValue(name);
  }

  public Token newLiteral(String value) {
    return new Token(STRING_CONST, this).setValue(value);
  }

  /**
   * Set value of token for literals.
   * @param value  the string value
   * @return the token
   */
  public Token setValue(Object value) {
    this.value = value;
    return this;
  }

  @Override
  public String toString() {
    return "Token{" +
           "type='" + type +
           "', value='" + getValue() + '\'' +
           '}';
  }

}
