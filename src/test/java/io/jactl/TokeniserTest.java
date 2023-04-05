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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TokeniserTest {

  @Test public void simpleTokens() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      var tokeniser = new Tokeniser(source);
      Token token    = tokeniser.next();
      assertEquals(type, token.getType());
      assertTrue(token.is(type));
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    };
    doTest.accept("(", TokenType.LEFT_PAREN);
    doTest.accept(")", TokenType.RIGHT_PAREN);
    doTest.accept("[", TokenType.LEFT_SQUARE);
    doTest.accept("]", TokenType.RIGHT_SQUARE);
    doTest.accept("{", TokenType.LEFT_BRACE);
    doTest.accept("!", TokenType.BANG);
    doTest.accept("%", TokenType.PERCENT);
    doTest.accept("^", TokenType.ACCENT);
    doTest.accept("&", TokenType.AMPERSAND);
    doTest.accept("*", TokenType.STAR);
    doTest.accept("-", TokenType.MINUS);
    doTest.accept("+", TokenType.PLUS);
    doTest.accept("/", TokenType.SLASH);
    doTest.accept("=", TokenType.EQUAL);
    doTest.accept("<", TokenType.LESS_THAN);
    doTest.accept(">", TokenType.GREATER_THAN);
    doTest.accept("?", TokenType.QUESTION);
    doTest.accept(",", TokenType.COMMA);
    doTest.accept(".", TokenType.DOT);
    doTest.accept("~", TokenType.GRAVE);
    doTest.accept("\\", TokenType.BACKSLASH);
    doTest.accept("|", TokenType.PIPE);
    doTest.accept(":", TokenType.COLON);
    doTest.accept(";", TokenType.SEMICOLON);
    doTest.accept("->", TokenType.ARROW);
    doTest.accept("!=", TokenType.BANG_EQUAL);
    doTest.accept("==", TokenType.EQUAL_EQUAL);
    doTest.accept("<=", TokenType.LESS_THAN_EQUAL);
    doTest.accept(">=", TokenType.GREATER_THAN_EQUAL);
    doTest.accept("?:", TokenType.QUESTION_COLON);
    doTest.accept("?.", TokenType.QUESTION_DOT);
    doTest.accept("?[", TokenType.QUESTION_SQUARE);
    doTest.accept("&&", TokenType.AMPERSAND_AMPERSAND);
    doTest.accept("||", TokenType.PIPE_PIPE);
    doTest.accept("-=", TokenType.MINUS_EQUAL);
    doTest.accept("+=", TokenType.PLUS_EQUAL);
    doTest.accept("/=", TokenType.SLASH_EQUAL);
    doTest.accept("*=", TokenType.STAR_EQUAL);
    doTest.accept("&=", TokenType.AMPERSAND_EQUAL);
    doTest.accept("|=", TokenType.PIPE_EQUAL);
    doTest.accept("^=", TokenType.ACCENT_EQUAL);
    doTest.accept("?=", TokenType.QUESTION_EQUAL);
    doTest.accept("%=", TokenType.PERCENT_EQUAL);
    doTest.accept("**=", TokenType.STAR_STAR_EQUAL);
    doTest.accept("<<", TokenType.DOUBLE_LESS_THAN);
    doTest.accept(">>", TokenType.DOUBLE_GREATER_THAN);
    doTest.accept("--", TokenType.MINUS_MINUS);
    doTest.accept("++", TokenType.PLUS_PLUS);
    doTest.accept("**", TokenType.STAR_STAR);
    doTest.accept("=~", TokenType.EQUAL_GRAVE);
    doTest.accept("!~", TokenType.BANG_GRAVE);
    doTest.accept("<=>", TokenType.COMPARE);
    doTest.accept("===", TokenType.TRIPLE_EQUAL);
    doTest.accept("!==", TokenType.BANG_EQUAL_EQUAL);
//    doTest.accept("'", SINGLE_QUOTE);
//    doTest.accept("\"", DOUBLE_QUOTE);
//    doTest.accept("''", DOUBLE_SINGLE_QUOTE);
//    doTest.accept("\"\"", DOUBLE_DOUBLE_QUOTE);
//    doTest.accept("'''", TRIPLE_SINGLE_QUOTE);
//    doTest.accept("\"\"\"", TRIPLE_DOUBLE_QUOTE);
    doTest.accept(">>>", TokenType.TRIPLE_GREATER_THAN);
    doTest.accept("<<=", TokenType.DOUBLE_LESS_THAN_EQUAL);
    doTest.accept(">>=", TokenType.DOUBLE_GREATER_THAN_EQUAL);
    doTest.accept(">>>=", TokenType.TRIPLE_GREATER_THAN_EQUAL);
    doTest.accept("def", TokenType.DEF);
    doTest.accept("var", TokenType.VAR);
    doTest.accept("int", TokenType.INT);
    doTest.accept("boolean", TokenType.BOOLEAN);
    doTest.accept("long", TokenType.LONG);
    doTest.accept("double", TokenType.DOUBLE);
    doTest.accept("Decimal", TokenType.DECIMAL);
    doTest.accept("String", TokenType.STRING);
    doTest.accept("Map", TokenType.MAP);
    doTest.accept("List", TokenType.LIST);
    doTest.accept("void", TokenType.VOID);
    doTest.accept("for", TokenType.FOR);
    doTest.accept("if", TokenType.IF);
    doTest.accept("else", TokenType.ELSE);
    doTest.accept("while", TokenType.WHILE);
    doTest.accept("unless", TokenType.UNLESS);
    doTest.accept("class", TokenType.CLASS);
    doTest.accept("package", TokenType.PACKAGE);
//    doTest.accept("this", THIS);
//    doTest.accept("super", SUPER);
    doTest.accept("static", TokenType.STATIC);
    doTest.accept("interface", TokenType.INTERFACE);
    doTest.accept("extends", TokenType.EXTENDS);
    doTest.accept("implements", TokenType.IMPLEMENTS);
    doTest.accept("import", TokenType.IMPORT);
    doTest.accept("as", TokenType.AS);
    doTest.accept("return", TokenType.RETURN);
    doTest.accept("new", TokenType.NEW);
    doTest.accept("instanceof", TokenType.INSTANCE_OF);
    doTest.accept("!instanceof", TokenType.BANG_INSTANCE_OF);
    doTest.accept("in", TokenType.IN);
    doTest.accept("!in", TokenType.BANG_IN);
    doTest.accept("and", TokenType.AND);
    doTest.accept("not", TokenType.NOT);
    doTest.accept("or", TokenType.OR);
    doTest.accept("true", TokenType.TRUE);
    doTest.accept("false", TokenType.FALSE);
    doTest.accept("null", TokenType.NULL);
    doTest.accept("print", TokenType.PRINT);
    doTest.accept("do", TokenType.DO);
    doTest.accept("println", TokenType.PRINTLN);
    doTest.accept("continue", TokenType.CONTINUE);
    doTest.accept("break", TokenType.BREAK);
    doTest.accept("BEGIN", TokenType.BEGIN);
    doTest.accept("END", TokenType.END);
    doTest.accept("die", TokenType.DIE);
    doTest.accept("eval", TokenType.EVAL);
    doTest.accept("final", TokenType.FINAL);
    doTest.accept("sealed", TokenType.SEALED);
    doTest.accept("%%", TokenType.PERCENT_PERCENT);
    doTest.accept("%%=", TokenType.PERCENT_PERCENT_EQUAL);
  }

  @Test public void eolToken() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      var tokeniser = new Tokeniser(source);
      Token token    = tokeniser.next();
      assertEquals(type, token.getType());
      assertTrue(token.is(type));
      Assertions.assertEquals(TokenType.BANG, tokeniser.next().getType());
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    };
    // Need ! because we strip trailing newlines
    doTest.accept("\n!", TokenType.EOL);
    doTest.accept("\r\n!", TokenType.EOL);
    doTest.accept("\n!\n\n", TokenType.EOL);
    doTest.accept("\r\n!\r\n\r\n", TokenType.EOL);
  }

  @Test public void booleans() {
    var tokeniser = new Tokeniser("true false");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.TRUE, token.getType());
    assertEquals(Boolean.TRUE, token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.FALSE, token.getType());
    assertEquals(Boolean.FALSE, token.getValue());
  }

  @Test public void nullSymbol() {
    var tokeniser = new Tokeniser("null");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.NULL, token.getType());
    assertEquals(null, token.getValue());
  }

  @Test public void identifiers() {
    Consumer<String> doTest = source -> {
      var tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
      assertEquals(source,     token.getValue());
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    };

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
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    };

    doTest.accept("0", List.of(new Pair<>(TokenType.INTEGER_CONST, 0)));
    doTest.accept("1", List.of(new Pair<>(TokenType.INTEGER_CONST, 1)));
    doTest.accept("20", List.of(new Pair<>(TokenType.INTEGER_CONST, 20)));
    doTest.accept("30", List.of(new Pair<>(TokenType.INTEGER_CONST, 30)));
    doTest.accept("1.0", List.of(new Pair<>(TokenType.DECIMAL_CONST, new BigDecimal("1.0"))));
    doTest.accept("0.12345", List.of(new Pair<>(TokenType.DECIMAL_CONST, new BigDecimal("0.12345"))));
    doTest.accept("1L", List.of(new Pair<>(TokenType.LONG_CONST, 1L)));
    doTest.accept("1L 1.0D", List.of(new Pair<>(TokenType.LONG_CONST, 1L), new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("1D 1L", List.of(new Pair<>(TokenType.DOUBLE_CONST, 1D), new Pair<>(TokenType.LONG_CONST, 1L)));
    doTest.accept("1L 1D", List.of(new Pair<>(TokenType.LONG_CONST, 1L), new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("1D", List.of(new Pair<>(TokenType.DOUBLE_CONST, 1D)));
    doTest.accept("1.0D", List.of(new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("" + ((long)Integer.MAX_VALUE + 1L) + "L", List.of(new Pair<>(TokenType.LONG_CONST, (long)Integer.MAX_VALUE + 1L)));
  }

  @Test public void numericOverflow() {
    BiConsumer<String,Object> doTest = (source,value) -> {
      var tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(value, token.getValue());
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
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
      if (type == TokenType.IDENTIFIER || type == TokenType.INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    checkToken.apply(TokenType.IDENTIFIER, "a");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "1");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "2");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "3");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.IDENTIFIER, "b");
  }

  @Test public void numbersAndDots2() {
    var tokeniser = new Tokeniser("1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == TokenType.IDENTIFIER || type == TokenType.INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    checkToken.apply(TokenType.DECIMAL_CONST, "1.2");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "3");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.IDENTIFIER, "b");
  }

  @Test public void numbersAndDotsAndRewind() {
    var tokeniser = new Tokeniser("a.1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == TokenType.IDENTIFIER || type == TokenType.INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    var previous = tokeniser.previous();
    var token = checkToken.apply(TokenType.IDENTIFIER, "a");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "1");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "2");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "3");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.IDENTIFIER, "b");

    tokeniser.rewind(previous, token);
    assertEquals(previous, tokeniser.previous());
    checkToken.apply(TokenType.IDENTIFIER, "a");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "1");
    checkToken.apply(TokenType.DOT, null);
    token = checkToken.apply(TokenType.INTEGER_CONST, "2");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "3");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.IDENTIFIER, "b");

    tokeniser.rewind(previous, token);
    checkToken.apply(TokenType.INTEGER_CONST, "2");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.INTEGER_CONST, "3");
    checkToken.apply(TokenType.DOT, null);
    checkToken.apply(TokenType.IDENTIFIER, "b");
  }

  @Test public void multipleTokens() {
    BiConsumer<String, List<TokenType>> doTest = (source, types) -> {
      var tokeniser = new Tokeniser(source);
      for (TokenType type: types) {
        assertEquals(type, tokeniser.next().getType());
      }
      Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    };

    doTest.accept("{}", List.of(TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE));
    doTest.accept("-1", List.of(TokenType.MINUS, TokenType.INTEGER_CONST));
    doTest.accept("-1.", List.of(TokenType.MINUS, TokenType.INTEGER_CONST, TokenType.DOT));
    doTest.accept(".012", List.of(TokenType.DOT, TokenType.INTEGER_CONST));
    doTest.accept("1.012X", List.of(TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("1.0DD;2.0Xa", List.of(TokenType.DOUBLE_CONST, TokenType.IDENTIFIER, TokenType.SEMICOLON, TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("1.012L1D_ab;1.0D2.0Xa", List.of(TokenType.DECIMAL_CONST, TokenType.IDENTIFIER, TokenType.SEMICOLON, TokenType.DOUBLE_CONST, TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("<<<", List.of(TokenType.DOUBLE_LESS_THAN, TokenType.LESS_THAN));
    doTest.accept("<<<<", List.of(TokenType.DOUBLE_LESS_THAN, TokenType.DOUBLE_LESS_THAN));
    doTest.accept("<<<<=", List.of(TokenType.DOUBLE_LESS_THAN, TokenType.DOUBLE_LESS_THAN_EQUAL));
    doTest.accept(">>>>", List.of(TokenType.TRIPLE_GREATER_THAN, TokenType.GREATER_THAN));
    doTest.accept("\n>>>\n>\n\n", List.of(TokenType.EOL, TokenType.TRIPLE_GREATER_THAN, TokenType.EOL, TokenType.GREATER_THAN, TokenType.EOF));
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
    Assertions.assertEquals(TokenType.EOF, token.getType());
  }

  @Test public void lineColumnNumbers() {
    String source = "1a b\na\n\na 1.234D c\n{";
    var tokeniser = new Tokeniser(source);
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    assertEquals(1, token.getValue());
    assertEquals("1", token.getChars());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(4, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    assertEquals(2, token.getLineNum());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(4, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.DOUBLE_CONST, token.getType());
    assertEquals(1.234, token.getValue());
    assertEquals("1.234", token.getChars());
    assertEquals(4, token.getLineNum());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(4, token.getLineNum());
    assertEquals(10, token.getColumn());

    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    assertEquals(4, token.getLineNum());
    assertEquals(11, token.getColumn());

    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());

    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
    assertEquals(5, token.getLineNum());
    assertEquals(2, token.getColumn());
    JactlError error = new EOFError("EOF", token);
    //System.out.println(error.getMessage());
    assertTrue(error.getMessage().contains("line 5, column 2"));
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void comments() {
    var tokeniser = new Tokeniser("/**/1a b\nX//<<>><><\n//\n/**/\n/*asdasd*///asdas\n\nY 1.234D/*a\n*/ /*//*/ c");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    assertEquals(1, token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(9, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("X", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    assertEquals(2, token.getLineNum());
    assertEquals(11, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("Y", token.getValue());
    assertEquals(7, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.DOUBLE_CONST, token.getType());
    assertEquals(1.234, token.getValue());
    assertEquals("1.234", token.getChars());
    assertEquals(7, token.getLineNum());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(8, token.getLineNum());
    assertEquals(11, token.getColumn());

    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
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
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.INTEGER_CONST, tokeniser.next().getType());
    var prev  = tokeniser.previous();
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    Assertions.assertEquals(TokenType.RIGHT_SQUARE, tokeniser.next().getType());
    tokeniser.rewind(prev, token);
    Assertions.assertEquals(TokenType.LEFT_BRACE, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.RIGHT_SQUARE, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.GREATER_THAN, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void simpleString() {
    var tokeniser = new Tokeniser("'abc'");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void simpleStrings() {
    var tokeniser = new Tokeniser("'abc''xyz'");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("xyz", token.getValue());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void strings() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'\n b = 2");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void strings2() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
  }

  @Test public void stringsUnexpectedEndOfFile() {
    var tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
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
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
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
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("a'", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
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
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
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
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(4, token.getColumn());
  }

  @Test public void multiLineStrings() {
    var tokeniser = new Tokeniser("a = '''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''\n b = 2");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void multiLineStringsWithCarriageReturns() {
    var tokeniser = new Tokeniser("a = '''a\\'b//\r\n/*\\t*/\\b\\r\\f\r\nc'''\r\n b = 2");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\r\n/*\t*/\b\r\f\r\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void keywordFollowedByWordChar() {
    var tokeniser = new Tokeniser("!instanceof !instanceofx forx while whilex while2");
    BiConsumer<TokenType,String> checkToken = (type,identifier) -> {
      var token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == TokenType.IDENTIFIER) {
        assertEquals(identifier, token.getValue());
      }
    };

    checkToken.accept(TokenType.BANG_INSTANCE_OF, null);
    checkToken.accept(TokenType.BANG, null);
    checkToken.accept(TokenType.IDENTIFIER, "instanceofx");
    checkToken.accept(TokenType.IDENTIFIER, "forx");
    checkToken.accept(TokenType.WHILE, null);
    checkToken.accept(TokenType.IDENTIFIER, "whilex");
    checkToken.accept(TokenType.IDENTIFIER, "while2");
  }

  @Test public void stringExpr() {
    var tokeniser = new Tokeniser("\"This is $abc. a test\"");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("This is ", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals(". a test", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void nestedStringExpr() {
    var tokeniser = new Tokeniser("\"\"\"This ${is}${abc + \"x${\"\"\"a\"\"\"}\"}. a test\"\"\"");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("This ", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("is", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("x", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals(". a test", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void multiLineStringInsideSingleLineString() {
    var tokeniser = new Tokeniser("\"this is a ${'''\n'''} test\"");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
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
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
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
    List.of(TokenType.FOR, TokenType.LEFT_BRACE, TokenType.EXPR_STRING_START, TokenType.LEFT_BRACE, TokenType.WHILE, TokenType.LEFT_PAREN, TokenType.TRUE, TokenType.AMPERSAND_AMPERSAND,
            TokenType.EXPR_STRING_START, TokenType.LEFT_BRACE, TokenType.IDENTIFIER, TokenType.RIGHT_BRACE, TokenType.EXPR_STRING_END, TokenType.RIGHT_PAREN,
            TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE, TokenType.SEMICOLON, TokenType.RIGHT_BRACE, TokenType.EXPR_STRING_END, TokenType.RIGHT_BRACE)
      .forEach(type -> assertEquals(type, tokeniser.next().getType()));
  }

  @Test public void unmatchedRightBrace() {
    var tokeniser = new Tokeniser("{}}");
    List.of(TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE).forEach(type -> assertEquals(type, tokeniser.next().getType()));
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
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("}", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
  }

  @Test public void nestedExprStrings() {
    var tokeniser = new Tokeniser("\"$x${\"${2*4}\" + 2}/*\"");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("x", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    assertEquals(2, token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STAR, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    assertEquals(4, token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    assertEquals(2, token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("/*", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void newLineInExpressionInSingleLineString() {
    var tokeniser = new Tokeniser("\"${a = \n2}\"");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_START, token.getType());
    assertEquals("", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
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
    Assertions.assertEquals(TokenType.PLUS, tokeniser.peek().getType());
    var prev  = tokeniser.previous();
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.peek().getType());
    tokeniser.rewind(prev, token);
    Assertions.assertEquals(TokenType.PLUS, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.peek().getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.STAR, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.peek().getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.next().getType());
  }

  @Test public void peekEmpty() {
    var tokeniser = new Tokeniser("");
    Assertions.assertEquals(TokenType.EOF, tokeniser.peek().getType());
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.peek().getType());
  }

  @Test public void peekEmptyComment() {
    var tokeniser = new Tokeniser("/* no tokens */");
    Assertions.assertEquals(TokenType.EOF, tokeniser.peek().getType());
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
    Assertions.assertEquals(TokenType.EOF, tokeniser.peek().getType());
  }

  @Test public void newlines() {
    var tokeniser = new Tokeniser("\n\na\nb\n\nc\n\n\n\n");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.previous().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
  }

  @Test public void newlinesWithCarriageReturns() {
    var tokeniser = new Tokeniser("\r\n\r\na\r\nb\r\n\r\nc\r\n\r\n\r\n\r\n");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.previous().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    Assertions.assertEquals(TokenType.EOL, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());
  }

  @Test public void regexStrings() {
    var tokeniser = new Tokeniser("a = /a\\/$a${x\n+y}\\n/*2");
    var token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.SLASH, token.getType());
    Token previous   = tokeniser.previous();
    Token slashToken = token;
    tokeniser.startRegex();
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a/", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.RIGHT_BRACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("\\n", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STAR, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EOF, token.getType());

    tokeniser.rewind(previous, slashToken);
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.SLASH, token.getType());
    tokeniser.startRegex();
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a/", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
  }

  @Test public void regexSubstitute() {
    Tokeniser tokeniser = new Tokeniser("s/ab/de/g");
    Token token = tokeniser.next();
    Assertions.assertEquals(TokenType.REGEX_SUBST_START, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("ab", token.getStringValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.REGEX_REPLACE, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("de", token.getStringValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EXPR_STRING_END, token.getType());
    assertEquals("g", token.getStringValue());
  }
}