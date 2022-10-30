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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static jacsal.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

class TokeniserTest {

  @Test public void simpleTokens() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      var tokeniser = new Tokeniser(source);
      assertEquals(type, tokeniser.next().getType());
      assertEquals(EOF, tokeniser.next().getType());
    };
    doTest.accept("(", LEFT_PAREN);
    doTest.accept(")", RIGHT_PAREN);
    doTest.accept("[", LEFT_SQUARE);
    doTest.accept("]", RIGHT_SQUARE);
    doTest.accept("{", LEFT_BRACE);
    doTest.accept("!", BANG);
    doTest.accept("%", PERCENT);
    doTest.accept("^", ACCENT);
    doTest.accept("&", AMPERSAND);
    doTest.accept("*", STAR);
    doTest.accept("-", MINUS);
    doTest.accept("+", PLUS);
    doTest.accept("/", SLASH);
    doTest.accept("=", EQUAL);
    doTest.accept("<", LESS_THAN);
    doTest.accept(">", GREATER_THAN);
    doTest.accept("?", QUESTION);
    doTest.accept(",", COMMA);
    doTest.accept(".", DOT);
    doTest.accept("~", GRAVE);
    doTest.accept("\\", BACKSLASH);
    doTest.accept("|", PIPE);
    doTest.accept(":", COLON);
    doTest.accept(";", SEMICOLON);
    doTest.accept("->", ARROW);
    doTest.accept("!=", BANG_EQUAL);
    doTest.accept("==", EQUAL_EQUAL);
    doTest.accept("<=", LESS_THAN_EQUAL);
    doTest.accept(">=", GREATER_THAN_EQUAL);
    doTest.accept("?:", QUESTION_COLON);
    doTest.accept("?.", QUESTION_DOT);
    doTest.accept("?[", QUESTION_SQUARE);
    doTest.accept("&&", AMPERSAND_AMPERSAND);
    doTest.accept("||", PIPE_PIPE);
    doTest.accept("-=", MINUS_EQUAL);
    doTest.accept("+=", PLUS_EQUAL);
    doTest.accept("/=", SLASH_EQUAL);
    doTest.accept("*=", STAR_EQUAL);
    doTest.accept("&=", AMPERSAND_EQUAL);
    doTest.accept("|=", PIPE_EQUAL);
    doTest.accept("^=", ACCENT_EQUAL);
    doTest.accept("?=", QUESTION_EQUAL);
    doTest.accept("%=", PERCENT_EQUAL);
    doTest.accept("**=", STAR_STAR_EQUAL);
    doTest.accept("<<", DOUBLE_LESS_THAN);
    doTest.accept(">>", DOUBLE_GREATER_THAN);
    doTest.accept("--", MINUS_MINUS);
    doTest.accept("++", PLUS_PLUS);
    doTest.accept("**", STAR_STAR);
    doTest.accept("=~", EQUAL_GRAVE);
    doTest.accept("!~", BANG_GRAVE);
    doTest.accept("<=>", COMPARE);
    doTest.accept("===", TRIPLE_EQUAL);
    doTest.accept("!==", BANG_EQUAL_EQUAL);
//    doTest.accept("'", SINGLE_QUOTE);
//    doTest.accept("\"", DOUBLE_QUOTE);
//    doTest.accept("''", DOUBLE_SINGLE_QUOTE);
//    doTest.accept("\"\"", DOUBLE_DOUBLE_QUOTE);
//    doTest.accept("'''", TRIPLE_SINGLE_QUOTE);
//    doTest.accept("\"\"\"", TRIPLE_DOUBLE_QUOTE);
    doTest.accept(">>>", TRIPLE_GREATER_THAN);
    doTest.accept("<<=", DOUBLE_LESS_THAN_EQUAL);
    doTest.accept(">>=", DOUBLE_GREATER_THAN_EQUAL);
    doTest.accept(">>>=", TRIPLE_GREATER_THAN_EQUAL);
    doTest.accept("def", DEF);
    doTest.accept("var", VAR);
    doTest.accept("int", INT);
    doTest.accept("boolean", BOOLEAN);
    doTest.accept("long", LONG);
    doTest.accept("double", DOUBLE);
    doTest.accept("Decimal", DECIMAL);
    doTest.accept("String", STRING);
    doTest.accept("Map", MAP);
    doTest.accept("List", LIST);
    doTest.accept("void", VOID);
    doTest.accept("for", FOR);
    doTest.accept("if", IF);
    doTest.accept("else", ELSE);
    doTest.accept("while", WHILE);
    doTest.accept("unless", UNLESS);
    doTest.accept("class", CLASS);
    doTest.accept("interface", INTERFACE);
    doTest.accept("extends", EXTENDS);
    doTest.accept("implements", IMPLEMENTS);
    doTest.accept("import", IMPORT);
    doTest.accept("as", AS);
    doTest.accept("return", RETURN);
    doTest.accept("instanceof", INSTANCE_OF);
    doTest.accept("!instanceof", BANG_INSTANCE_OF);
    doTest.accept("in", IN);
    doTest.accept("!in", BANG_IN);
    doTest.accept("and", AND);
    doTest.accept("not", NOT);
    doTest.accept("or", OR);
    doTest.accept("true", TRUE);
    doTest.accept("false", FALSE);
    doTest.accept("null", NULL);
    doTest.accept("print", PRINT);
    doTest.accept("do", DO);
    doTest.accept("println", PRINTLN);
    doTest.accept("continue", CONTINUE);
    doTest.accept("break", BREAK);
  }

  @Test public void booleans() {
    var tokeniser = new Tokeniser("true false");
    var token = tokeniser.next();
    assertEquals(TRUE, token.getType());
    assertEquals(Boolean.TRUE, token.getValue());
    token = tokeniser.next();
    assertEquals(FALSE, token.getType());
    assertEquals(Boolean.FALSE, token.getValue());
  }

  @Test public void nullSymbol() {
    var tokeniser = new Tokeniser("null");
    var token = tokeniser.next();
    assertEquals(NULL, token.getType());
    assertEquals(null, token.getValue());
  }

  @Test public void identifiers() {
    Consumer<String> doTest = source -> {
      var tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(IDENTIFIER, token.getType());
      assertEquals(source,     token.getValue());
      assertEquals(EOF,        tokeniser.next().getType());
    };

    doTest.accept("_");
    doTest.accept("_1");
    doTest.accept("__1");
    doTest.accept("a__1");
    doTest.accept("L");
    doTest.accept("D");
    doTest.accept("Dxw12uioi_");
    doTest.accept("__");
    doTest.accept("a1");
    doTest.accept("$1");
    doTest.accept("$1234");
  }

  @Test public void numericLiterals() {
    BiConsumer<String, List<Pair<TokenType,Object>>> doTest = (source, typeAndValues) -> {
      var     tokeniser = new Tokeniser(source);
      Token[] token     = { tokeniser.next() };
      typeAndValues.forEach(typeAndValue -> {
        assertEquals(typeAndValue.first, token[0].getType());
        assertEquals(typeAndValue.second, token[0].getValue());
        token[0] = tokeniser.next();
      });
      assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("0", List.of(new Pair<>(INTEGER_CONST, 0)));
    doTest.accept("1", List.of(new Pair<>(INTEGER_CONST, 1)));
    doTest.accept("20", List.of(new Pair<>(INTEGER_CONST, 20)));
    doTest.accept("30", List.of(new Pair<>(INTEGER_CONST, 30)));
    doTest.accept("1.0", List.of(new Pair<>(DECIMAL_CONST, new BigDecimal("1.0"))));
    doTest.accept("0.12345", List.of(new Pair<>(DECIMAL_CONST, new BigDecimal("0.12345"))));
    doTest.accept("1L", List.of(new Pair<>(LONG_CONST, 1L)));
    doTest.accept("1L 1.0D", List.of(new Pair<>(LONG_CONST, 1L), new Pair<>(DOUBLE_CONST, 1.0D)));
    doTest.accept("1D 1L", List.of(new Pair<>(DOUBLE_CONST, 1D), new Pair<>(LONG_CONST, 1L)));
    doTest.accept("1L 1D", List.of(new Pair<>(LONG_CONST, 1L), new Pair<>(DOUBLE_CONST, 1.0D)));
    doTest.accept("1D", List.of(new Pair<>(DOUBLE_CONST, 1D)));
    doTest.accept("1.0D", List.of(new Pair<>(DOUBLE_CONST, 1.0D)));
    doTest.accept("" + ((long)Integer.MAX_VALUE + 1L) + "L", List.of(new Pair<>(LONG_CONST, (long)Integer.MAX_VALUE + 1L)));
  }

  @Test public void numericOverflow() {
    BiConsumer<String,Object> doTest = (source,value) -> {
      var tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(value, token.getValue());
      assertEquals(EOF,    tokeniser.next().getType());
    };

    doTest.accept("123456789.1234D", 123456789.1234);
    doTest.accept("123456789", 123456789);
    doTest.accept("123456789123456789L", 123456789123456789L);
    try {
      doTest.accept("123456789123456789", 123456789123456789L);
      fail("Should have thrown an exception");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("number too large"));
    }
    try {
      doTest.accept("123456789123456789123456789123456789L", 123456789123456789L);
      fail("Should have thrown an exception");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("number too large"));
    }
  }

  @Test public void numbersAndDots() {
    var tokeniser = new Tokeniser("a.1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == IDENTIFIER || type == INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    checkToken.apply(IDENTIFIER, "a");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "1");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "2");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "3");
    checkToken.apply(DOT, null);
    checkToken.apply(IDENTIFIER, "b");
  }

  @Test public void numbersAndDots2() {
    var tokeniser = new Tokeniser("1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == IDENTIFIER || type == INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    checkToken.apply(DECIMAL_CONST, "1.2");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "3");
    checkToken.apply(DOT, null);
    checkToken.apply(IDENTIFIER, "b");
  }

  @Test public void numbersAndDotsAndRewind() {
    var tokeniser = new Tokeniser("a.1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == IDENTIFIER || type == INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    var previous = tokeniser.previous();
    var token = checkToken.apply(IDENTIFIER, "a");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "1");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "2");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "3");
    checkToken.apply(DOT, null);
    checkToken.apply(IDENTIFIER, "b");

    tokeniser.rewind(previous, token);
    assertEquals(previous, tokeniser.previous());
    checkToken.apply(IDENTIFIER, "a");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "1");
    checkToken.apply(DOT, null);
    token = checkToken.apply(INTEGER_CONST, "2");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "3");
    checkToken.apply(DOT, null);
    checkToken.apply(IDENTIFIER, "b");

    tokeniser.rewind(previous, token);
    checkToken.apply(INTEGER_CONST, "2");
    checkToken.apply(DOT, null);
    checkToken.apply(INTEGER_CONST, "3");
    checkToken.apply(DOT, null);
    checkToken.apply(IDENTIFIER, "b");
  }

  @Test public void multipleTokens() {
    BiConsumer<String, List<TokenType>> doTest = (source, types) -> {
      var tokeniser = new Tokeniser(source);
      for (TokenType type: types) {
        assertEquals(type, tokeniser.next().getType());
      }
      assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("{}", List.of(LEFT_BRACE, RIGHT_BRACE));
    doTest.accept("-1", List.of(MINUS, INTEGER_CONST));
    doTest.accept("-1.", List.of(MINUS, INTEGER_CONST, DOT));
    doTest.accept(".012", List.of(DOT, INTEGER_CONST));
    doTest.accept("1.012X", List.of(DECIMAL_CONST, IDENTIFIER));
    doTest.accept("1.0DD;2.0Xa", List.of(DOUBLE_CONST, IDENTIFIER, SEMICOLON, DECIMAL_CONST, IDENTIFIER));
    doTest.accept("1.012L1D_ab;1.0D2.0Xa", List.of(DECIMAL_CONST, IDENTIFIER, SEMICOLON, DOUBLE_CONST, DECIMAL_CONST, IDENTIFIER));
    doTest.accept("<<<", List.of(DOUBLE_LESS_THAN, LESS_THAN));
    doTest.accept("<<<<", List.of(DOUBLE_LESS_THAN, DOUBLE_LESS_THAN));
    doTest.accept("<<<<=", List.of(DOUBLE_LESS_THAN, DOUBLE_LESS_THAN_EQUAL));
    doTest.accept(">>>>", List.of(TRIPLE_GREATER_THAN, GREATER_THAN));
    doTest.accept("\n>>>\n>\n\n", List.of(EOL, TRIPLE_GREATER_THAN, EOL, GREATER_THAN, EOF));
  }

  @Test public void lineNumbers() {
    var tokeniser = new Tokeniser("1\n2\n3\n");
    var token = tokeniser.next();
    assertEquals(1, token.getLineNum());
    token = tokeniser.next();
    assertEquals(1, token.getLineNum());
    token = tokeniser.next();
    assertEquals(2, token.getLineNum());
    token = tokeniser.next();
    assertEquals(2, token.getLineNum());
    token = tokeniser.next();
    assertEquals(3, token.getLineNum());
    token = tokeniser.next();
    assertEquals(3, token.getLineNum());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());
  }

  @Test public void lineColumnNumbers() {
    String source = "1a b\na\n\na 1.234D c\n{";
    var tokeniser = new Tokeniser(source);
    var token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals(1, token.getValue());
    assertEquals("1", token.getChars());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(4, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(2, token.getLineNum());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(4, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(DOUBLE_CONST, token.getType());
    assertEquals(1.234, token.getValue());
    assertEquals("1.234", token.getChars());
    assertEquals(4, token.getLineNum());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(4, token.getLineNum());
    assertEquals(10, token.getColumn());

    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(4, token.getLineNum());
    assertEquals(11, token.getColumn());

    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());

    token = tokeniser.next();
    assertEquals(EOF, token.getType());
    assertEquals(5, token.getLineNum());
    assertEquals(2, token.getColumn());
    JacsalError error = new EOFError("EOF", token);
    //System.out.println(error.getMessage());
    assertTrue(error.getMessage().contains("line 5, column 2"));
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void comments() {
    var tokeniser = new Tokeniser("/**/1a b\nX//<<>><><\n//\n/**/\n/*asdasd*///asdas\n\nY 1.234D/*a\n*/ /*//*/ c");
    var token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals(1, token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(9, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("X", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(2, token.getLineNum());
    assertEquals(11, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("Y", token.getValue());
    assertEquals(7, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(DOUBLE_CONST, token.getType());
    assertEquals(1.234, token.getValue());
    assertEquals("1.234", token.getChars());
    assertEquals(7, token.getLineNum());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(8, token.getLineNum());
    assertEquals(11, token.getColumn());

    assertEquals(EOF, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void unexpectedEofInComment() {
    var tokeniser = new Tokeniser("/*\n/*asdasd");
    try {
      tokeniser.next();
      fail("Should have thrown an exception");
    }
    catch (CompileError error) {
      assertTrue(error.getMessage().toLowerCase().contains("unexpected end of file in comment"));
    }
  }

  @Test public void unexpectedCharacter() {
    var tokeniser = new Tokeniser("a b !<#> c");
    tokeniser.next();
    tokeniser.next();
    tokeniser.next();
    tokeniser.next();
    try {
      tokeniser.next();
      fail("Should have thrown an unexpected character '#' error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("unexpected character '#'"));
    }
  }

  @Test public void rewind() {
    var tokeniser = new Tokeniser("a 1 { ]>");
    assertEquals(IDENTIFIER, tokeniser.next().getType());
    assertEquals(INTEGER_CONST, tokeniser.next().getType());
    var prev  = tokeniser.previous();
    var token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    assertEquals(RIGHT_SQUARE, tokeniser.next().getType());
    tokeniser.rewind(prev, token);
    assertEquals(LEFT_BRACE, tokeniser.next().getType());
    assertEquals(RIGHT_SQUARE, tokeniser.next().getType());
    assertEquals(GREATER_THAN, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void simpleString() {
    var tokeniser = new Tokeniser("'abc'");
    var token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void simpleStrings() {
    var tokeniser = new Tokeniser("'abc''xyz'");
    var token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("xyz", token.getValue());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void strings() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'\n b = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
  }

  @Test public void strings2() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());
  }

  @Test public void stringsUnexpectedEndOfFile() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    try {
      token = tokeniser.next();
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("unexpected end of file in string"));
    }
  }

  @Test public void stringsUnexpectedEndOfLine() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc\na = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    try {
      token = tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("new line not allowed"));
    }
  }

  @Test public void exprStringUnexpectedEndOfLine() {
    var tokeniser = new Tokeniser("a = \"a'$b//\n/*\\t*/\\b\\r\\fc\"\na = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("a'", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    try {
      token = tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("new line not allowed"));
    }
  }

  @Test public void exprStringUnexpectedEndOfLine2() {
    var tokeniser = new Tokeniser("a = \"a'//\n/*\\t*/\\b\\r\\fc\"\na = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    try {
      token = tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("new line not allowed"));
    }
  }

  @Test public void simpleMuliLineString() {
    var tokeniser = new Tokeniser("'''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''");
    Token     token     = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(4, token.getColumn());
  }

  @Test public void multiLineStrings() {
    var tokeniser = new Tokeniser("a = '''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''\n b = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
  }

  @Test public void keywordFollowedByWordChar() {
    var tokeniser = new Tokeniser("!instanceof !instanceofx forx while whilex while2");
    BiConsumer<TokenType,String> checkToken = (type,identifier) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == IDENTIFIER) {
        assertEquals(identifier, token.getValue());
      }
    };

    checkToken.accept(BANG_INSTANCE_OF, null);
    checkToken.accept(BANG, null);
    checkToken.accept(IDENTIFIER, "instanceofx");
    checkToken.accept(IDENTIFIER, "forx");
    checkToken.accept(WHILE, null);
    checkToken.accept(IDENTIFIER, "whilex");
    checkToken.accept(IDENTIFIER, "while2");
  }

  @Test public void stringExpr() {
    var tokeniser = new Tokeniser("\"This is $abc. a test\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("This is ", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals(". a test", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void nestedStringExpr() {
    var tokeniser = new Tokeniser("\"\"\"This ${is}${abc + \"x${\"\"\"a\"\"\"}\"}. a test\"\"\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("This ", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("is", token.getValue());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    assertEquals(PLUS, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("x", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals(". a test", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void multiLineStringInsideSingleLineString() {
    var tokeniser = new Tokeniser("\"this is a ${'''\n'''} test\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    try {
      token = tokeniser.next();
      fail("Should have thrown compile error about new line not being allowed");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("new line not allowed"));
    }
  }

  @Test public void dollarKeywordInStringExpr() {
    var tokeniser = new Tokeniser("\"for $whilex$while\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("whilex", token.getValue());
    try {
      token = tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError error) {
      assertTrue(error.getMessage().toLowerCase().contains("keyword found where identifier expected"));
    }
  }

  @Test public void matchingBraces() {
    var tokeniser = new Tokeniser("for { \"${while(true && \"${x}\"){};}\" }");
    List.of(FOR, LEFT_BRACE, EXPR_STRING_START, LEFT_BRACE, WHILE, LEFT_PAREN, TRUE, AMPERSAND_AMPERSAND,
            EXPR_STRING_START, LEFT_BRACE, IDENTIFIER, RIGHT_BRACE, EXPR_STRING_END, RIGHT_PAREN,
            LEFT_BRACE, RIGHT_BRACE, SEMICOLON, RIGHT_BRACE, EXPR_STRING_END, RIGHT_BRACE)
      .forEach(type -> assertEquals(type, tokeniser.next().getType()));
  }

  @Test public void unmatchedRightBrace() {
    var tokeniser = new Tokeniser("{}}");
    List.of(LEFT_BRACE, RIGHT_BRACE).forEach(type -> assertEquals(type, tokeniser.next().getType()));
    try {
      tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("does not match any opening brace"));
    }
  }

  @Test public void rightBraceInStringExpr() {
    var tokeniser = new Tokeniser("\"${a}}\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("}", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());
  }

  @Test public void nestedExprStrings() {
    var tokeniser = new Tokeniser("\"$x${\"${2*4}\" + 2}/*\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("x", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals(2, token.getValue());
    token = tokeniser.next();
    assertEquals(STAR, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals(4, token.getValue());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    assertEquals(PLUS, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals(2, token.getValue());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("/*", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void newLineInExpressionInSingleLineString() {
    var tokeniser = new Tokeniser("\"${a = \n2}\"");
    var token = tokeniser.next();
    assertEquals(EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    try {
      token = tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("new line not allowed"));
    }
  }

  @Test public void peek() {
    var tokeniser = new Tokeniser("+ - *");
    assertEquals(PLUS, tokeniser.peek().getType());
    var prev  = tokeniser.previous();
    var token = tokeniser.next();
    assertEquals(PLUS, token.getType());
    assertEquals(MINUS, tokeniser.peek().getType());
    tokeniser.rewind(prev, token);
    assertEquals(PLUS, tokeniser.peek().getType());
    token = tokeniser.next();
    assertEquals(PLUS, token.getType());
    assertEquals(MINUS, tokeniser.peek().getType());
    assertEquals(MINUS, tokeniser.next().getType());
    assertEquals(STAR, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.peek().getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void peekEmpty() {
    var tokeniser = new Tokeniser("");
    assertEquals(EOF, tokeniser.peek().getType());
    var token = tokeniser.next();
    assertEquals(EOF, token.getType());
    assertEquals(EOF, tokeniser.peek().getType());
  }

  @Test public void peekEmptyComment() {
    var tokeniser = new Tokeniser("/* no tokens */");
    assertEquals(EOF, tokeniser.peek().getType());
    var token = tokeniser.next();
    assertEquals(EOF, token.getType());
    assertEquals(EOF, tokeniser.peek().getType());
  }

  @Test public void newlines() {
    var tokeniser = new Tokeniser("\n\na\nb\n\nc\n\n\n\n");
    var token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());
  }

  @Test public void regexStrings() {
    var tokeniser = new Tokeniser("a = /a\\/$a${x\n+y}\\n/*2");
    var token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(SLASH, token.getType());
    Token previous   = tokeniser.previous();
    Token slashToken = token;
    tokeniser.startRegex();
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a/", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    token = tokeniser.next();
    assertEquals(PLUS, token.getType());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    token = tokeniser.next();
    assertEquals(RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("\\n", token.getValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    assertEquals(STAR, token.getType());
    token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());

    tokeniser.rewind(previous, slashToken);
    token = tokeniser.next();
    assertEquals(SLASH, token.getType());
    tokeniser.startRegex();
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a/", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
  }

  @Test public void regexSubstitute() {
    Tokeniser tokeniser = new Tokeniser("s/ab/de/g");
    Token token = tokeniser.next();
    assertEquals(REGEX_SUBST_START, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("ab", token.getStringValue());
    token = tokeniser.next();
    assertEquals(REGEX_REPLACE, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("de", token.getStringValue());
    token = tokeniser.next();
    assertEquals(EXPR_STRING_END, token.getType());
    assertEquals("g", token.getStringValue());
  }
}