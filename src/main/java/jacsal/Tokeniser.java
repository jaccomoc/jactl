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
import java.util.regex.Pattern;
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
 *
 * Another quirk is how to handle regex strings which are expression strings delimited by
 * / rather than by """. The other difference is that a regex expression string does not
 * support escaped characters except for escaping / itself so all other backslashes appear
 * as is in the resulting string. The problem is that when we first see a / we don't know
 * if it will be a regex string or just a normal / token. The Tokeniser returns / as a
 * SLASH token and then, if the Parser thinks that it is more likely to be a regex string
 * it can tell the Tokeniser to continue tokenising as a regex string.
 *
 * From a lookahead point of view we don't support the / being a SLASH for a lookahead and
 * then being a regex start (or vice-versa). Once the Tokeniser has moved past the SLASH
 * the mode is set and subsequent tokens are normal tokens or part of the regex expression
 * string. If there is a rewind and then the tokens are replayed back the tokens don't
 * changed. This means that lookahead in the Parser can only work if all lookaheads treat
 * the SLASH the same way.
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
    String  endChars;           // Char sequence which terminates string
    boolean allowNewLines;      // Whether we currently allow newlines within our expression string
    boolean escapeChars;        // Whether escape chars are supported
    boolean isSubstitute;       // True if in pattern part of s/.../.../
    boolean isReplace;          // True if in s/.../.../imsg replacement string
    int     braceLevel;         // At what level of brace nesting does the expression string exist
    int     stringOffset;       // Offset where string starts
  }

  // Valid modifiers that can come immediately after trailing '/' of a regex
  private static final String REGEX_MODIFIERS = "fgims";

  /**
   * Constructor
   * @param source  the source code to tokenise
   */
  public Tokeniser(String source) {
    // Strip trailing new lines so that EOF errors point to somewhere useful
    this.source = source.replaceAll("\\R*$", "");
    this.length = this.source.length();
  }

  /**
   * Get the next token. If we have been rewound then we return from that point in
   * the stream of tokens rather than parsing a new one from the source code.
   * Special case for new lines is that we collapse a series of new lines into one
   * since new lines are syntactically relevant but the number in a row has no
   * significance.
   * @return the next token in the stream of tokens
   */
  public Token next() {
    populateCurrentToken();

    Token result  = currentToken;
    previousToken = currentToken;
    currentToken  = previousToken.getNext();

    if (result.isError()) {
      throw result.getError();
    }
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
    if (currentToken.isError()) {
      throw currentToken.getError();
    }
    return currentToken;
  }

  /**
   * Rewind to an early position in the stream of tokens
   * @param previous  the previous token
   * @param current  the token to rewind to
   */
  public void rewind(Token previous, Token current) {
    previousToken = previous;
    currentToken  = current;
  }

  /**
   * If previous token was a SLASH then this allows Parser to tell Tokeniser to
   * move into expression string mode since Parser is expecting a /..../ style
   * multi-line regex expression string. If we have previously parsed past the
   * SLASH then we ignore this call and will return the previously parsed tokens.
   */
  public void startRegex() {
    if (!previousToken.is(SLASH,SLASH_EQUAL)) {
      throw new IllegalStateException("Internal error: startRegex on Tokeniser invoked when previous token was " + previousToken + " and not SLASH");
    }
    // Only change our state if this is the first time we have parsed the SLASH token. After
    // any rewind we will ignore since our string state should already be in the right state.
    if (previousToken.getNext() == null) {
      pushStringState("/", previousToken.getOffset());
    }
  }

  //////////////////////////////////////////////////////////////////

  private void pushStringState(String endChars, int offset) {
    pushStringState(endChars, offset, false);
  }

  private void pushStringState(String endChars, int offset, boolean isSubstitute) {
    StringState state = new StringState();
    state.endChars = endChars;
    state.allowNewLines = newLinesAllowed() && !endChars.equals("'") && !endChars.equals("\"");
    state.escapeChars = !endChars.equals("/");
    state.isSubstitute = isSubstitute;
    state.isReplace = false;
    state.stringOffset = offset;
    inString = true;
    stringState.push(state);
  }


  /**
   * If currentToken already has a value then do nothing. If we don't have a current
   * token then read the next token.
   */
  private void populateCurrentToken() {
    if (currentToken == null) {
      try {
        currentToken = parseToken();
      }
      catch (CompileError e) {
        currentToken = new Token(e);
      }

      if (previousToken != null) {
        previousToken.setNext(currentToken);
      }
      // If we have already returned EOL last time then keep advancing until we get
      // something that is not EOL
      if (currentToken.is(EOL) && previousToken != null && previousToken.is(EOL)) {
        while ((currentToken = parseToken()).is(EOL)) {}
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
        int stringStart = offset - 1;
        advance(tripleQuoted ? 2 : 0);
        Token stringToken = parseString(false, tripleQuoted ? "'''" : "'", newLinesAllowed() && tripleQuoted, true, false, stringStart);
        advance(tripleQuoted ? 3 : 1);
        return stringToken;
      }
      case DOUBLE_QUOTE: {
        // Work out whether we are a multi line string expression or not. If we are the outermost string expression
        // in our nested strings then we can be multi-line. As soon as we hit a single line string expression
        // or are nested within one we can no longer have newlines even if the nested string starts with a triple
        // double quote.
        boolean tripleQuote = available(2) && charAt(0) == '"' && charAt(1) == '"';
        String  endChars    = tripleQuote ? "\"\"\"" : "\"";
        int stringStart = offset - 1;
        pushStringState(endChars, stringStart);
        advance(tripleQuote ? 2 : 0);
        token = parseString(true, endChars, newLinesAllowed(), true, false, stringStart);
        return token.setType(EXPR_STRING_START);
      }
      case REGEX_SUBST_START: {
        pushStringState("/", offset-1, true);
        return token.setType(REGEX_SUBST_START);
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
    String      endChars           = currentStringState.endChars;
    boolean     escapeChars        = currentStringState.escapeChars;
    boolean     isSubstitute       = currentStringState.isSubstitute;
    switch (c) {
      case '$': {
        // We either have an identifer following or as '{'
        int nextChar = charAt(1);
        if (nextChar == '{') {
          inString = false;
          // Remember where we are so that when we see matching '}' we go back into string expr mode
          currentStringState.braceLevel = nestedBraces++;
          advance(1);
          token = createToken().setType(LEFT_BRACE)
                               .setLength(1);
          advance(1);
        }
        else {
          // If we are in a regex string then we allow '$' without requiring a valid identifier
          if (!escapeChars && !Character.isDigit(nextChar) && !isIdentifierStart(nextChar)) {
            break;
          }

          // We know that we have an identifier following the '$' in this case because
          // we would have already swollowed the '$' in previous parseString() call if there
          // was no identifier following the '$'. For capture vars the '$' is part of the name
          // so we don't advance past the '$'.
          if (!Character.isDigit(nextChar)) {
            advance(1);  // Skip '$'
            remaining--;
          }
          token = parseIdentifier(createToken(), remaining);
          if (keyWords.contains(token.getChars())) {
            throw new CompileError("Keyword found where identifier expected", token);
          }
        }
        return token;
      }

      case '/': {
        if (charsAtEquals(0, endChars.length(), endChars)) {
          advance(1);
          if (isSubstitute) {
            // Return token to indicate we have reached end of pattern part of s/pattern/replacement/
            currentStringState.isSubstitute = false;   // So next '/' will be treated as end
            currentStringState.isReplace = true;
            return token.setType(REGEX_REPLACE).setLength(1);
          }
          else {
            StringBuilder modifierSb = new StringBuilder();
            // Check for modifiers after '/'
            while (available(1) && Character.isAlphabetic(charAt(0))) {
              final var modifier = (char) charAt(0);
              modifierSb.append(modifier);
              advance(1);
            }
            stringState.pop();
            inString = false;
            final var modifiers = modifierSb.toString();
            for (int i = 0; i < modifiers.length(); i++) {
              if (REGEX_MODIFIERS.indexOf(modifiers.charAt(i)) == -1) {
                throw new CompileError("Unrecognised regex modifier '" + modifiers.charAt(i) + "'", createToken());
              }
            }
            return token.setType(EXPR_STRING_END)
                        .setValue(modifiers)
                        .setLength(endChars.length() + modifierSb.length());
          }
        }
        break;
      }
      case '"': {
        if (charsAtEquals(0, endChars.length(), endChars)) {
          advance(endChars.length());
          stringState.pop();
          inString = false;
          return token.setType(EXPR_STRING_END).setLength(endChars.length());
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
    return parseString(true, endChars, newLinesAllowed(), escapeChars, currentStringState.isReplace, currentStringState.stringOffset);
  }

  /**
   * Parse an identifier. Identifier is either:
   *    - a normal identifier:      [_a-zA-Z][_a-zA-Z0-9]*
   *    - a capture var identifier: $[0-9]+
   */
  private Token parseIdentifier(Token token, int remaining) {
    // Make sure that first character is legal for an identitifer
    int startChar = charAt(0);
    if (!isIdentifierStart(startChar) && startChar != '$') {
      throw new CompileError("Unexpected character '" + (char)startChar + "': expecting start of identifier", token);
    }

    // Search for first char the is not a valid identifier char
    int i = 1;
    for (; i < remaining; i++) {
      final var c = charAt(i);
      if (startChar == '$' && !Character.isDigit(c)) {
          break;
      }
      else
      if (startChar != '$' && !isIdentifierPart(c)) {
        break;
      }
    }

    if (startChar == '$') {
      if (i == 1) { throw new CompileError("Unexpected character '$'", token); }
      if (i > 6) {
        throw new CompileError("Capture variable name too large", token);
      }
    }

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
     return stringState.size() == 0 || stringState.peek().allowNewLines;
  }

  private boolean isDigit(int c, int base) {
    switch (base) {
      case 2:  return c == '0' || c == '1';
      case 10: return Character.isDigit(c);
      case 16: return Character.isDigit(c) || "abcdefABCDEF".indexOf(c) != -1;
    }
    throw new IllegalStateException("Internal error: unexpected base " + base);
  }

  private Token parseNumber(Token token, int remaining) {
    // Check for binary or hex number
    int base = charsAtEquals(0,"0b", "0B")  ? 2  :
               charsAtEquals(0, "0x", "0X") ? 16 :
               /* default */ 10;

    int i = base == 10 ? 1 : 2;
    while (i < remaining && isDigit(charAt(i), base)) { i++; }

    // Check for double/decimal number but first check for special case where previous token
    // was a '.' to allow for numbers to be fields in a dotted path. E.g. a.1.2.b
    // In this case we don't want to return 1.2 as the number but 1 followed by '.' followed by 2.
    // If previous token was not a '.' then if following char is a '.' we know we have a decimal
    // number.
    boolean decimal = base == 10 && (previousToken == null || previousToken.isNot(DOT)) &&
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
    String value = token.getStringValue().substring(base == 10 ? 0 : 2);
    if (value.isEmpty()) {
      throw new CompileError("Missing digits for numeric literal", token);
    }
    switch (type) {
      case INTEGER_CONST: {
        try {
          token.setValue(Integer.parseInt(value, base));
        }
        catch (NumberFormatException e) {
          throw new CompileError("Number too large for integer constant", token);
        }
        break;
      }
      case LONG_CONST: {
        try {
          token.setValue(Long.parseLong(value, base));
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

  private boolean charsAtEquals(int lookahead, String... value) {
    for (String str: value) {
      if (charsAtEquals(lookahead, str.length(), str)) {
        return true;
      }
    }
    return false;
  }

  private boolean charsAtEquals(int lookahead, int length, String value) {
    return available(length) && source.substring(offset+lookahead, offset+lookahead+length).equals(value);
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
    if (inString) {
      return;  // can't have comments in a string
    }
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

  private Token parseString(boolean stringExpr, String endChars, boolean newlinesAllowed, boolean escapeChars, boolean isReplace, int stringStart) {
    char endChar = endChars.charAt(0);
    Token token = createToken();
    StringBuilder sb = new StringBuilder();
    boolean finished = false;
    for (; !finished && available(1); advance(1)) {
      int c = charAt(0);
      switch (c) {
        case '/':
        case '\'':
        case '"': {
          if (c == endChar) {
            // If close quote/quotes
            if (charsAtEquals(0, endChars.length(), endChars)) {
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
          if (escapeChars) {
            advance(1);
            c = escapedChar(charAt(0));
          }
          else {
            // Only thing we can escape is the end of string. For everything else leave '\' in the string as is.
            int nextChar = charAt(1);
            if (nextChar == endChar) {
              advance(1);
              c = nextChar;
            }
            if (nextChar == '$') {
              sb.append('\\');
              advance(1);
              c = nextChar;
            }
          }
          break;
        }
        case '$': {
          if (stringExpr) {
            int nextChar = available(2) ? charAt(1) : -1;
            if (nextChar == '{' || isIdentifierStart(nextChar) || Character.isDigit(nextChar)) {
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
    if (!finished) { throw new EOFError("Unexpected end of file in string that started", new Token(source, stringStart)); }

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
    List<Symbol> symbols = Arrays.stream(TokenType.values())
                                 .filter(type -> type.asString != null)
                                 .map(type -> new Symbol(type.asString, type))
                                 .collect(Collectors.toList());

    symbols.forEach(sym -> symbolLookup[sym.charAt(0)].add(sym));

    // Special case for EOL. We cater for both '\n' and '\r\n' indicating EOL.
    symbolLookup['\r'].add(new Symbol("\r\n", EOL));
    symbolLookup['\n'].add(new Symbol("\n", EOL));

    // Regex substitute
    symbolLookup['s'].add(new Symbol("s/", REGEX_SUBST_START));

    IntStream.range(0, 256).forEach(i -> symbolLookup[i].sort((a, b) -> b.symbol.compareTo(a.symbol)));
    // Map digits to INT for the moment
    IntStream.range(0, 10).forEach(i -> symbolLookup[Integer.toString(i).charAt(0)] = List.of(new Symbol(Integer.toString(i), INTEGER_CONST)));

    keyWords = symbols.stream()
                      .filter(sym -> sym.isKeyword)
                      .map(sym -> sym.symbol)
                      .collect(Collectors.toSet());
  }

  public static void main(String[] args) {
    List<String> words = keyWords.stream().sorted().collect(Collectors.toList());
    int i;
    for (i = 0; i < words.size(); i++) {
      System.out.print("| " + words.get(i) + " ");
      if ((i+1) % 5 == 0) {
        System.out.println("|");
      }
    }
    int next = (i/5) * 5 + 5;
    for (; i < next; i++) {
      System.out.print("| ");
    }
    System.out.println("|");
  }
}
