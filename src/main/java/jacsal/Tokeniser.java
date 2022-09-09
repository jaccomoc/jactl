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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jacsal.TokenType.*;

/**
 * This class represents the tokeniser for a given script. It is constructed with the source
 * code and then it parses the source into tokens, returning each token one-by-one. Since
 * tokens have a reference to the next token (once the next one has been parsed), clients
 * can rewind by setting the current token to a previously returned token. This allows us
 * to easily support lookahead by arbitrary amounts.
 *
 * This Tokeniser is more complicated than a more straightforward implemention because we
 * need to keep some state to handle interpolated strings where expressions inside strings
 * can recursively contain other interpolated strings. We keep track of how nested we are in
 * our recursive expressions by matching '{' and '}' and we also keep track of whether we are
 * in a multi-line string expression or not.
 *
 * NOTE: we could try to do this all in the Parser but that makes the parser more complicated
 * and requires shared state between the Parser and the Tokeniser about whether we are in an
 * expression string, how nested we are, and whether each nesting is single or multi-line.
 * The Parser would then need to tell the Tokeniser whether to parse a string or a normal
 * token each time. Keeping this state in the Parser also makes it harder for the Parser to
 * implement lookahead. While it does add complexity to the Tokeniser, it simplifies the job
 * of the Parser.
 */
public class Tokeniser {
  private       Token  currentToken;      // The current token
  private       Token  previousToken;     // The previous token we parsed
  private final String source;            // The source code of the script
  private final int    length;            // Length of source code
  private       int    offset = 0;        // The current position in the source code as offset

  private boolean inString     = false;   // True if parsing expression string content (but not
                                          // expression within string)
  private int     nestedBraces = 0;

  // Stack of expression string states showing whether current nested string type is triple quoted
  // and whether newlines are allowed.
  private final Deque<StringState> stringState = new ArrayDeque<>();
  private static class StringState {
    boolean isTripleQuoted;     // Whether current expression string is triple quoted or not
    boolean newlinesAllowed;    // Whether we currently allow newlines within our expression string
    int     braceLevel;         // At what level of brace nesting does the expression string exist
    StringState(boolean isTripleQuoted, boolean newlinesAllowed) {
      this.isTripleQuoted  = isTripleQuoted;
      this.newlinesAllowed = newlinesAllowed;
    }
  }

  /**
   * Constructor
   * @param source  the source code to tokenise
   */
  public Tokeniser(String source) {
    // Strip trailing new lines so that EOF errors point to somewhere useful
    this.source = source.replaceAll("\\n*$", "");
    this.length = this.source.length();
  }

  /**
   * Get the next token. If we have been rewound then we return from that point in
   * the stream of tokens rather than parsing a new one from the source code.
   * Special case for new lines is that we collapse a series of new lines into one
   * since new lines are syntactially relevant but the number in a row has no
   * significance.
   * @return the next token in the stream of tokens
   */
  public Token next() {
    populateCurrentToken();

    // If we have already returned EOL last time then keep advancing until we get
    // something that is not EOL
    if (currentToken.is(EOL) && previousToken != null && previousToken.is(EOL)) {
      while ((currentToken = parseToken()).is(EOL)) {}
      previousToken.setNext(currentToken);
    }

    Token result  = currentToken;
    previousToken = currentToken;
    currentToken  = previousToken.getNext();

    return result;
  }

  /**
   * Return the previous token
   * @return the previous token
   */
  public Token previous() {
    return previousToken;
  }

  /**
   * Peek at the next token without advancing
   * @return the next token
   */
  public Token peek() {
    populateCurrentToken();
    return currentToken;
  }

  /**
   * Rewind to an early position in the stream of tokens
   * @param token  the token to rewind to
   */
  public void rewind(Token token) {
    currentToken = token;
  }

  //////////////////////////////////////////////////////////////////

  /**
   * If currentToken already has a value then do nothing. If we don't have a current
   * token then read the next token.
   */
  private void populateCurrentToken() {
    if (currentToken == null) {
      currentToken = parseToken();
      if (previousToken != null) {
        previousToken.setNext(currentToken);
      }
    }
  }

  /**
   * This is the main method that reads chars and decides what type of token to return.
   * @return the next token from the source code
   */
  private Token parseToken() {
    skipSpacesAndComments();

    // Create token before knowing type and length so we can record start position
    Token token = createToken();

    int remaining = source.length() - offset;
    if (remaining == 0) {
      return token.setType(EOF);
    }

    int c = charAt(0);

    // The inString flag indicates we are in an expression string and are just after an embedded
    // '$' expression that we have already parsed
    if (inString) {
      return parseNextStringPart(token, remaining, c);
    }

    if (c > 256) throw new CompileError("Unexpected character '" + (char) c + "'", token);

    // Find list of symbols that match o first character
    List<Symbol> symbols = symbolLookup[c];

    // Check for numeric literals
    if (symbols.size() == 1 && symbols.get(0).type == INTEGER_CONST) {
      return parseNumber(token, remaining);
    }

    // Iterate through possible symbols from longest to shortest until we find one that matches
    Optional<Symbol> symbolOptional = symbols.stream().sequential()
                                             .filter(symbol -> symbolMatches(symbol, remaining))
                                             .findFirst();

    // If we did not find matching symbol then we must have an identifier (or an unknown token character)
    if (symbolOptional.isEmpty()) {
      return parseIdentifier(token, remaining);
    }

    // We found matching symbol
    Symbol sym = symbolOptional.get();
    advance(sym.length());

    // Some special handling for specific tokens
    switch (sym.type) {
      case EOL: {
        if (!newLinesAllowed()) {
          throw new CompileError("New line not allowed within single line string", token);
        }
        return token.setType(EOL).setLength(1);
      }
      case SINGLE_QUOTE: {
        // Work out whether we are a multi line string literal or not
        boolean tripleQuoted = available(2) && charAt(0) == '\'' && charAt(1) == '\'';
        advance(tripleQuoted ? 2 : 0);
        Token stringToken = parseString(false, tripleQuoted, newLinesAllowed() && tripleQuoted);
        advance(tripleQuoted ? 3 : 1);
        return stringToken;
      }
      case DOUBLE_QUOTE: {
        // Work out whether we are a multi line string expression or not. If we are the outermost string expression
        // in our nested strings then we can be multi-line. As soon as we hit a single line string expression
        // or are nested within one we can no longer have newlines even if the nested string starts with a triple
        // double quote.
        boolean tripleQuote = available(2) && charAt(0) == '"' && charAt(1) == '"';
        inString = true;
        boolean allowNewLines = newLinesAllowed() && tripleQuote;
        stringState.push(new StringState(tripleQuote, allowNewLines));
        advance(tripleQuote ? 2 : 0);
        token = parseString(true, tripleQuote, allowNewLines);
        return token.setType(EXPR_STRING_START);
      }
      case LEFT_BRACE: {
        nestedBraces++;
        return token.setType(LEFT_BRACE).setLength(1);
      }
      case RIGHT_BRACE: {
        nestedBraces--;
        if (nestedBraces < 0) {
          throw new CompileError("Closing brace '}' does not match any opening brace", token);
        }
        if (stringState.size() > 0 && stringState.peek().braceLevel == nestedBraces) {
          // Go back into string mode if we have found closing '}' of our nested expression within the sring
          inString = true;
        }
        return token.setType(RIGHT_BRACE).setLength(1);
      }
      case TRUE:
      case FALSE: {
        token.setValue(sym.type == TRUE);
        break;
      }
      case NULL: {
        token.setValue(null);
        break;
      }
    }

    return token.setType(sym.type)
                .setLength(sym.length())
                .setKeyword(sym.isKeyword);
  }

  private boolean symbolMatches(Symbol sym, int remaining) {
    if (sym.length() > remaining) return false;

    // Check that if symbol ends in a word character that following char is not a word character
    if (sym.endsWithAlpha() &&
        sym.length() + 1 <= remaining &&
        isIdentifierPart(charAt(sym.length()))) {
      return false;
    }

    // Check if string at current offset matches symbol
    int i = 1;
    while(i < sym.length() && sym.charAt(i) == charAt(i)) { i++; }

    return i == sym.length();
  }

  /**
   * Handle scenario where we are in a string expr (interpolated string). We parse
   * string as STRING_EXPR_START followed by combinations of STRING_CONST or other
   * expressions and then ending with STRING_EXPR_END. Each time, we return a
   * STRING_CONST until we get to a point where we have $identifier or ${...}
   * Note that the STRING_EXPR_START token will have a value that contains initial
   * chars of the string but STRING_EXPR_END is always empty.
   */
  private Token parseNextStringPart(Token token, int remaining, int c) {
    StringState currentStringState = stringState.peek();
    boolean     isTripleQuoted     = currentStringState.isTripleQuoted;
    switch (c) {
      case '$': {
        // We either have an identifer following or as '{'
        if (charAt(1) == '{') {
          inString = false;
          // Remember where we are so that when we see matching '}' we go back into string expr mode
          currentStringState.braceLevel = nestedBraces++;
          advance(1);
          token = createToken().setType(LEFT_BRACE)
                               .setLength(1);
          advance(1);
        }
        else {
          // We know that we have an identifier following the '$' in this case because
          // we would have already swollowed the '$' in previous parseString() call if there
          // was no identifier following the '$'.
          advance(1);
          token = parseIdentifier(createToken(), remaining);
          if (keyWords.contains(token.getChars())) {
            throw new CompileError("Keyword found where identifier expected", token);
          }
        }
        return token;
      }
      case '"': {
        if (!isTripleQuoted || available(3) && charAt(1) == '"' && charAt(2) == '"') {
          advance(isTripleQuoted ? 3 : 1);
          stringState.pop();
          inString = false;
          return token.setType(EXPR_STRING_END)
                      .setLength(3);
        }
        break;
      }
      case '\n': {
        if (!newLinesAllowed()) {
          throw new CompileError("Unexpected new line within single line string", createToken());
        }
        break;
      }
    }
    // No embedded expression or end of string found so parse as STRING_CONST
    return parseString(true, isTripleQuoted, newLinesAllowed());
  }

  private Token parseIdentifier(Token token, int remaining) {
    // Make sure that first character is legal for an identitifer
    int c = charAt(0);
    if (!isIdentifierStart(c)) throw new CompileError("Unexpected character '" + (char)c + "'", token);

    // Search for first char the is not a valid identifier char
    int i = 1;
    while (i < remaining && isIdentifierPart(charAt(i))) { i++; }

    advance(i);
    return token.setType(IDENTIFIER)
                .setLength(i);
  }

  private static boolean isIdentifierStart(int c) {
    return Character.isJavaIdentifierStart(c) && c != '$';
  }

  private static boolean isIdentifierPart(int c) {
    return Character.isJavaIdentifierPart(c) && c != '$';
  }

  private boolean newLinesAllowed() {
     return stringState.size() == 0 || stringState.peek().newlinesAllowed;
  }

  private Token parseNumber(Token token, int remaining) {
    int i = 1;
    while (i < remaining && Character.isDigit(charAt(i))) { i++; }

    // Check for double/decimal number but first check for special case where previous token
    // was a '.' to allow for numbers to be fields in a dotted path. E.g. a.1.2.b
    // In this case we don't want to return 1.2 as the number but 1 followed by '.' followed by 2.
    // If previous token was not a '.' then if following char is a '.' we know we have a decimal
    // number.
    boolean decimal = (previousToken == null || previousToken.isNot(DOT)) &&
                      i + 1 < remaining && charAt(i) == '.' && Character.isDigit(charAt(i + 1));

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
    token.setLength(numberLength);
    String value = token.getStringValue();
    switch (type) {
      case INTEGER_CONST: {
        try {
          token.setValue(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
          throw new CompileError("Number too large for integer constant", token);
        }
        break;
      }
      case LONG_CONST: {
        try {
          token.setValue(Long.parseLong(value));
        }
        catch (NumberFormatException e) {
          throw new CompileError("Number too large for long constant", token);
        }
        break;
      }
      case DOUBLE_CONST: {
        token.setValue(Double.parseDouble(value));
        break;
      }
      case DECIMAL_CONST: {
        token.setValue(new BigDecimal(value));
        break;
      }
    }
    return token.setType(type);
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
          startMultiLineOffset = new Token(source, offset);
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

  private Token parseString(boolean stringExpr, boolean tripleQuoted, boolean newlinesAllowed) {
    char quoteChar = stringExpr ? '"' : '\'';
    Token token = createToken();
    StringBuilder sb = new StringBuilder();
    boolean finished = false;
    for (; !finished && available(1); advance(1)) {
      int c = charAt(0);
      switch (c) {
        case '\'':
        case '"': {
          if (c == quoteChar) {
            // If close quote/quotes
            if (!tripleQuoted || available(3) && charAt(1) == quoteChar && charAt(2) == quoteChar) {
              finished = true;
              continue;
            }
          }
          break;
        }
        case '\n': {
          if (!newlinesAllowed) { throw new CompileError("New line not allowed nested within single line string", token); }
          break;
        }
        case '\\': {
          if (!available(1)) { throw new CompileError("Unexpected end of file after '\\' in string", createToken()); }
          advance(1);
          c = escapedChar(charAt(0));
          break;
        }
        case '$': {
          if (stringExpr) {
            int nextChar = available(2) ? charAt(1) : -1;
            if (nextChar == '{' || isIdentifierStart(nextChar)) {
              finished = true;
              continue;
            }
          }
        }
        default:
          break;
      }
      sb.append((char)c);
    }
    if (!finished) { throw new CompileError("Unexpected end of file in string", token); }

    advance(-1);     // Don't swallow '$' or ending quote
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
    return new Token(source, offset);
  }

  private static boolean isIdentifier(String s) {
    return isIdentifierStart(s.charAt(0)) &&
           s.chars().skip(1).allMatch(Tokeniser::isIdentifierPart);
  }

  //////////////////////////////////////////////////////////////////////

  // = INIT

  private static class Symbol {
    public String    symbol;
    public int       length;
    public TokenType type;
    public boolean   isKeyword;
    public Symbol(String symbol, TokenType type) {
      this.symbol    = symbol;
      this.type      = type;
      this.length    = symbol.length();
      this.isKeyword = isIdentifier(symbol);
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
    new Symbol("--", MINUS_MINUS),
    new Symbol("++", PLUS_PLUS),
    new Symbol("!=", BANG_EQUAL),
    new Symbol("==", EQUAL_EQUAL),
    new Symbol("<=", LESS_THAN_EQUAL),
    new Symbol(">=", GREATER_THAN_EQUAL),
    new Symbol("?:", QUESTION_COLON),
    new Symbol("?.", QUESTION_DOT),
    new Symbol("?[", QUESTION_SQUARE),
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
    new Symbol("<=>", COMPARE),
    new Symbol("===", TRIPLE_EQUAL),
    new Symbol("!==", BANG_EQUAL_EQUAL),
    new Symbol("=~", EQUAL_GRAVE),
    new Symbol("**", STAR_STAR),
    new Symbol("%=", PERCENT_EQUAL),
    new Symbol("**=", STAR_STAR_EQUAL),
    new Symbol("->", ARROW),
    //new Symbol("", IDENTIFIER),
    //new Symbol("", STRING_CONST),
    //new Symbol("", INTEGER_CONST),
    //new Symbol("", LONG_CONST),
    //new Symbol("", DOUBLE_CONST),
    //new Symbol("", DECIMAL_CONST),

    new Symbol("'", SINGLE_QUOTE),
    new Symbol("\"", DOUBLE_QUOTE),
    new Symbol("\n", EOL),

    // Keywords
    new Symbol("true", TRUE),
    new Symbol("false", FALSE),
    new Symbol("null", NULL),
    new Symbol("def", DEF),
    new Symbol("var", VAR),
    new Symbol("boolean", BOOLEAN),
    new Symbol("int", INT),
    new Symbol("long", LONG),
    new Symbol("double", DOUBLE),
    new Symbol("Decimal", DECIMAL),
    new Symbol("String", STRING),
    new Symbol("Map", MAP),
    new Symbol("List", LIST),
    new Symbol("void", VOID),
    new Symbol("for", FOR),
    new Symbol("if", IF),
    new Symbol("else", ELSE),
    new Symbol("while", WHILE),
    new Symbol("continue", CONTINUE),
    new Symbol("break", BREAK),
    new Symbol("class", CLASS),
    new Symbol("interface", INTERFACE),
    new Symbol("extends", EXTENDS),
    new Symbol("implements", IMPLEMENTS),
    new Symbol("import", IMPORT),
    new Symbol("as", AS),
    new Symbol("return", RETURN),
    new Symbol("instanceof", INSTANCE_OF),
    new Symbol("!instanceof", BANG_INSTANCE_OF),
    new Symbol("in", IN),
    new Symbol("!in", BANG_IN),
    new Symbol("and", AND),
    new Symbol("or", OR),
    new Symbol("print", PRINT),
    new Symbol("println", PRINTLN)
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

  private static Set<String> keyWords;

  static {
    symbols.forEach(sym -> symbolLookup[sym.symbol.charAt(0)].add(sym));
    IntStream.range(0, 256).forEach(i -> symbolLookup[i].sort((a, b) -> b.symbol.compareTo(a.symbol)));
    // Map digits to INT for the moment
    IntStream.range(0, 10).forEach(i -> symbolLookup[Integer.toString(i).charAt(0)] = List.of(new Symbol(Integer.toString(i), INTEGER_CONST)));

    keyWords = symbols.stream()
                      .filter(sym -> sym.isKeyword)
                      .map(sym -> sym.symbol)
                      .collect(Collectors.toSet());
  }
}
