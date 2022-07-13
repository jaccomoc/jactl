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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jacsal.TokenType.*;

/**
 * This class represents the tokeniser for a given script. It is constructed
 * with the source code and then it parses the source into tokens, returning
 * each token one-by-one.
 * Since tokens have a reference to the next token (once the next one has been
 * parsed), clients can rewind by setting the current token to a previously
 * returned token. This allows us to easily support lookahead by arbitrary
 * amounts.
 */
public class Tokeniser {
  private Token  currentToken;      // The current token
  private Token  previousToken;     // The previous token we parsed
  private String source;            // The source code of the script
  private int    length;            // Length of source code
  private int    line   = 1;        // The current line number
  private int column = 1;        // The current column number
  private int offset = 0;        // The current position in the source code as offset

  /**
   * Constructor
   * @param source  the source code to tokenise
   */
  public Tokeniser(String source) {
    this.source = source;
    this.length = source.length();
  }

  /**
   * Get the next token
   * @return the next token in the stream of tokens
   */
  public Token next() {
    if (currentToken == null) {
      currentToken = parseToken();
      if (previousToken != null) {
        previousToken.setNext(currentToken);
      }
    }

    Token result  = currentToken;
    previousToken = currentToken;
    currentToken  = previousToken.getNext();

    return result;
  }

  /**
   * Rewind to an early position in the stream of tokens
   * @param token  the token to rewind to
   */
  public void rewind(Token token) {
    currentToken = token;
  }

  //////////////////////////////////////////////////////////////////

  private Token parseToken() {
    skipSpacesAndComments();

    // Create token before knowing type and length so we can record start position
    Token token = createToken();
    int remaining = source.length() - offset;

    if (remaining == 0) {
      return token.setType(EOF);
    }

    int c = charAt(0);

    switch (c) {
      case '\n':
        line++;
        column = 0;
        advance(1);
        return token.setType(EOL).setLength(1);
      case '\'':
        return parseString();
      case '"':
        throw new UnsupportedOperationException("Interpolated strings not supported");
//        return parseInterpolatedString();
    }

    if (c > 256) throw new CompileError("Unexpected character '" + (char)c + "'", token);

    // Find list of symbols that match o first character
    List<Symbol> symbols = symbolLookup[c];

    //
    // Check for numeric literals
    //
    if (symbols.size() == 1 && symbols.get(0).type == INTEGER_CONST) {
      int i = 1;
      while (i < remaining && Character.isDigit(charAt(i))) { i++; }

      // Check for double/decimal number
      boolean decimal = i + 1 < remaining && charAt(i) == '.' && Character.isDigit(charAt(i+1));

      if (decimal) {
        i += 2;  // Skip '.' and first digit since we have already checked for their presence above
        while (i < remaining && Character.isDigit(charAt(i))) { i++; }
      }

      // Now check if there is a trailing 'L' or 'D'
      // NOTE: by default a decimal point implies a BigDecimal but 'D' will make it a native double
      TokenType type = decimal ? DECIMAL_CONST : INTEGER_CONST;
      int numberLength = i;
      if (i < remaining) {
        int nextChar = charAt(i);
        if (nextChar == 'L' && !decimal) {
          i++;
          type = LONG_CONST;
        }
        else
        if (nextChar == 'D') {
          i++;
          type = DOUBLE_CONST;
        }
      }
      advance(i);
      return token.setType(type)
                  .setLength(numberLength);
    }

    //
    // Iterate through possible symbols from longest to shortest until we find one that matches
    //
    for (Symbol sym: symbols) {
      if (sym.length() > remaining) continue;

      // Check that if symbol ends in a word character that following char is not a word character
      if (sym.endsWithAlpha() && sym.length() + 1 <= remaining && Character.isAlphabetic(charAt(sym.length()))) continue;

      // Check if string at current offset matches symbol
      int i = 1;
      for (; i < sym.length(); i++) {
        if (sym.charAt(i) != charAt(i)) break;
      }
      if (i != sym.length()) continue;

      // Found matching symbol so return the token
      advance(sym.length());
      return token.setType(sym.type)
                  .setLength(sym.length());
    }

    // Only remaining possibility is that token is an identifier so make sure we have start of an identifier
    if (!(c == '_' || Character.isAlphabetic(c))) throw new CompileError("Unexpected character '" + (char)c + "'", token);

    int i = 1;
    for (; i < remaining; i++) {
      int nextChar = charAt(i);
      if (!(Character.isAlphabetic(nextChar) || Character.isDigit(nextChar) || nextChar == '_')) {
        break;
      }
    }

    advance(i);
    return token.setType(IDENTIFIER)
                .setLength(i);
  }

  /**
   * Get character given number of positions ahead of current position
   * @param lookahead  how many characters ahead of current offset
   * @return character at given location
   */
  private int charAt(int lookahead) {
    return source.charAt(offset + lookahead);
  }

  private boolean available(int avail) {
    return offset + avail <= length;
  }

  /**
   * Advance given number of characters once we have parsed a valid token.
   * For the moment this can only be used when we know for sure that there are no new lines
   * being skipped over as we don't change the current line.
   * @param count  the number of chars
   */
  private void advance(int count) {
    column += count;
    offset += count;
  }

  private boolean isSpace(int c) {
    return c == ' ' || c == '\t' || c == '\r';
  }

  private enum Mode { CODE, LINE_COMMENT, MULTI_LINE_COMMENT }

  private void skipSpacesAndComments() {
    Token startMultiLineOffset = null;
    Mode mode                  = Mode.CODE;
    for (; available(1); advance(1)) {
      int c = charAt(0);
      if (isSpace(c)) { continue; }

      if (mode == Mode.LINE_COMMENT) {
        if (c == '\n') { break; }
        continue;
      }

      if (mode == Mode.MULTI_LINE_COMMENT) {
        if (c == '\n') {
          line++;
          column = 0;
        }
        else
        if (c == '*' && available(2) && charAt(1) == '/') {
          advance(1);
          mode = Mode.CODE;
        }
        continue;
      }

      // CODE mode
      if (c == '/' && available(2)) {
        int nextChar = charAt(1);
        if (nextChar == '/' || nextChar == '*') {
          mode = nextChar == '/' ? Mode.LINE_COMMENT : Mode.MULTI_LINE_COMMENT;
          startMultiLineOffset = new Token(source, offset, line, column);
          advance(1);
          continue;
        }
      }

      // If we have reached this far then we are not in a comment and we have something that
      // is not whitespace
      break;
    }

    if (!available(1) && mode == Mode.MULTI_LINE_COMMENT) {
      throw new CompileError("Unexpected end of file in comment", startMultiLineOffset);
    }
  }

  private Token parseString() {
    // Work out whether we are a multi line string literal or not
    boolean multiLine = available(3) && charAt(1) == '\'' && charAt(2) == '\'';
    advance(multiLine ? 3 : 1);
    Token token = createToken();
    StringBuilder sb = new StringBuilder();
    boolean finished = false;
    for (; !finished && available(1); advance(1)) {
      int c = charAt(0);
      switch (c) {
        case '\'': {
          // If close quote/quotes
          if (!multiLine || available(3) && charAt(1) == '\'' && charAt(2) == '\'') {
            finished = true;
            continue;
          }
          break;
        }
        case '\n': {
          if (!multiLine) { throw new CompileError("Unexpected end of line in string", token); }
          line++;
          column = 0;
          break;
        }
        case '\\': {
          if (!available(1)) { throw new CompileError("Unexpected end of file after '\\' in string", createToken()); }
          advance(1);
          c = escapedChar(charAt(0));
          break;
        }
        default:
          break;
      }
      sb.append((char)c);
    }
    if (!finished) { throw new CompileError("Unexpected end of file in string", token); }

    // We have already advanced by 1 inside loop so skip final 2 single quotes for multi-line strings
    advance(multiLine ? 2 : 0);
    return token.setType(STRING_CONST)
                .setValue(sb.toString());
  }

  private int escapedChar(int c) {
    switch (c) {
      case 't': return '\t';
      case 'b': return '\b';
      case 'n': return '\n';
      case 'r': return '\r';
      case 'f': return '\f';
      default:  return c;
    }
  }

  private Token createToken() {
    return new Token(source, offset, line, column);
  }

  //////////////////////////////////////////////////////////////////////

  // = INIT

  private static class Symbol {
    public String    symbol;
    public int       length;
    public TokenType type;
    public Symbol(String symbol, TokenType type) {
      this.symbol = symbol;
      this.type   = type;
      this.length = symbol.length();
    }
    public boolean endsWithAlpha()    { return Character.isAlphabetic(symbol.charAt(symbol.length() - 1)); }
    public int     length()           { return length; }
    public char    charAt(int offset) { return symbol.charAt(offset); }
  }

  private static List<Symbol> symbols = List.of(
    new Symbol("(", LEFT_PAREN),
    new Symbol(")", RIGHT_PAREN),
    new Symbol("[", LEFT_SQUARE),
    new Symbol("]", RIGHT_SQUARE),
    new Symbol("{", LEFT_BRACE),
    new Symbol("}", RIGHT_BRACE),
    new Symbol("!", BANG),
    new Symbol("$", DOLLAR),
    new Symbol("%", PERCENT),
    new Symbol("^", ACCENT),
    new Symbol("&", AMPERSAND),
    new Symbol("*", STAR),
    new Symbol("-", MINUS),
    new Symbol("+", PLUS),
    new Symbol("/", SLASH),
    new Symbol("=", EQUAL),
    new Symbol("<", LESS_THAN),
    new Symbol(">", GREATER_THAN),
    new Symbol("?", QUESTION),
    new Symbol(",", COMMA),
    new Symbol(".", DOT),
    new Symbol("~", GRAVE),
    new Symbol("\\", BACKSLASH),
    new Symbol("|", PIPE),
    new Symbol(":", COLON),
    new Symbol(";", SEMICOLON),
    new Symbol("!=", BANG_EQUAL),
    new Symbol("==", EQUAL_EQUAL),
    new Symbol("<=", LESS_THAN_EQUAL),
    new Symbol(">=", GREATER_THAN_EQUAL),
    new Symbol("?:", QUESTION_COLON),
    new Symbol("&&", AMPERSAND_AMPERSAND),
    new Symbol("||", PIPE_PIPE),
    new Symbol("-=", MINUS_EQUAL),
    new Symbol("+=", PLUS_EQUAL),
    new Symbol("/=", SLASH_EQUAL),
    new Symbol("*=", STAR_EQUAL),
    new Symbol("&=", AMPERSAND_EQUAL),
    new Symbol("|=", PIPE_EQUAL),
    new Symbol("^=", ACCENT_EQUAL),
    new Symbol("~=", GRAVE_EQUAL),
    new Symbol("?=", QUESTION_EQUAL),
    new Symbol("<<", DOUBLE_LESS_THAN),
    new Symbol(">>", DOUBLE_GREATER_THAN),
    new Symbol(">>>", TRIPLE_GREATER_THAN),
    new Symbol("<<=", DOUBLE_LESS_THAN_EQUAL),
    new Symbol(">>=", DOUBLE_GREATER_THAN_EQUAL),
    new Symbol(">>>=", TRIPLE_GREATER_THAN_EQUAL),
    //new Symbol("", IDENTIFIER),
    //new Symbol("", STRING_CONST),
    //new Symbol("", INTEGER_CONST),
    //new Symbol("", LONG_CONST),
    //new Symbol("", DOUBLE_CONST),
    //new Symbol("", DECIMAL_CONST),
    new Symbol("def", DEF),
    new Symbol("var", VAR),
    new Symbol("it", IT),
    new Symbol("int", INT),
    new Symbol("flout", FLOAT),
    new Symbol("double", DOUBLE),
    new Symbol("String", STRING),
    new Symbol("void", VOID),
    new Symbol("for", FOR),
    new Symbol("if", IF),
    new Symbol("while", WHILE),
    new Symbol("class", CLASS),
    new Symbol("interface", INTERFACE),
    new Symbol("extends", EXTENDS),
    new Symbol("implements", IMPLEMENTS),
    new Symbol("import", IMPORT),
    new Symbol("as", AS),
    new Symbol("return", RETURN),
    new Symbol("instanceof", INSTANCE_OF),
    new Symbol("!instanceof", BANG_INSTANCE_OF)
    //    new Symbol("'", SINGLE_QUOTE),
    //    new Symbol("\"", DOUBLE_QUOTE),
    //    new Symbol("''", DOUBLE_SINGLE_QUOTE),
    //    new Symbol("\"\"", DOUBLE_DOUBLE_QUOTE),
    //    new Symbol("'''", TRIPLE_SINGLE_QUOTE),
    //    new Symbol("\"\"\"", TRIPLE_DOUBLE_QUOTE),
  );

  //
  // Array of symbols keyed on first letter for efficient lookup. Each element in the array
  // is a list of symbols reverse sorted (long strings first) by their string representation.
  //
  private static List<Symbol>[] symbolLookup = IntStream.range(0, 256)
                                                        .mapToObj(i -> new ArrayList<Symbol>())
                                                        .collect(Collectors.toList())
                                                        .toArray(new List[0]);

  static {
    symbols.forEach(sym -> symbolLookup[sym.symbol.charAt(0)].add(sym));
    IntStream.range(0, 256).forEach(i -> symbolLookup[i].sort((a, b) -> b.symbol.compareTo(a.symbol)));
    // Map digits to INT for the moment
    IntStream.range(0, 10).forEach(i -> symbolLookup[Integer.toString(i).charAt(0)] = List.of(new Symbol(Integer.toString(i), INTEGER_CONST)));
  }
}
