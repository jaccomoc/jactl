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

import io.jactl.runtime.RuntimeError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.jactl.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

class TokeniserTest {

  @Test public void simpleTokens() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(type, token.getType());
      assertTrue(token.is(type));
      Assertions.assertEquals(EOF, tokeniser.next().getType());
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
    doTest.accept("_", TokenType.UNDERSCORE);
    doTest.accept("->", TokenType.ARROW);
    doTest.accept("!=", TokenType.BANG_EQUAL);
    doTest.accept("==", TokenType.EQUAL_EQUAL);
    doTest.accept("<=", TokenType.LESS_THAN_EQUAL);
    doTest.accept(">=", TokenType.GREATER_THAN_EQUAL);
    doTest.accept("?:", TokenType.QUESTION_COLON);
    doTest.accept("?.", TokenType.QUESTION_DOT);
    doTest.accept("?[", TokenType.QUESTION_SQUARE);
    doTest.accept("${", TokenType.DOLLAR_BRACE);
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
    doTest.accept("byte", TokenType.BYTE);
    doTest.accept("boolean", TokenType.BOOLEAN);
    doTest.accept("long", TokenType.LONG);
    doTest.accept("double", TokenType.DOUBLE);
    doTest.accept("Decimal", TokenType.DECIMAL);
    doTest.accept("String", TokenType.STRING);
    doTest.accept("Object", TokenType.OBJECT);
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
    doTest.accept("const", TokenType.CONST);
    doTest.accept("sealed", TokenType.SEALED);
    doTest.accept("switch", TokenType.SWITCH);
    doTest.accept("default", TokenType.DEFAULT);
    doTest.accept("??", TokenType.QUESTION_QUESTION);
    doTest.accept("%%", TokenType.PERCENT_PERCENT);
    doTest.accept("%%=", TokenType.PERCENT_PERCENT_EQUAL);
  }

  @Test public void eolToken() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(type, token.getType());
      assertTrue(token.is(type));
      Assertions.assertEquals(TokenType.BANG, tokeniser.next().getType());
      Assertions.assertEquals(EOF, tokeniser.next().getType());
    };
    // Need ! because we strip trailing newlines
    doTest.accept("\n!", EOL);
    doTest.accept("\r\n!", EOL);
    doTest.accept("\n!\n\n", EOL);
    doTest.accept("\r\n!\r\n\r\n", EOL);
  }

  @Test public void booleans() {
    Tokeniser tokeniser = new Tokeniser("true false");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.TRUE, token.getType());
    assertEquals(Boolean.TRUE, token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.FALSE, token.getType());
    assertEquals(Boolean.FALSE, token.getValue());
  }

  @Test public void nullSymbol() {
    Tokeniser tokeniser = new Tokeniser("null");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.NULL, token.getType());
    assertEquals(null, token.getValue());
  }

  @Test public void identifiers() {
    BiConsumer<String, Boolean> doTest = (source, isDollar) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      Assertions.assertEquals(isDollar ? TokenType.DOLLAR_IDENTIFIER : TokenType.IDENTIFIER, token.getType());
      assertEquals(source, token.getValue());
      Assertions.assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("_1", false);
    doTest.accept("__1", false);
    doTest.accept("a__1", false);
    doTest.accept("L", false);
    doTest.accept("D", false);
    doTest.accept("Dxw12uioi_", false);
    doTest.accept("__", false);
    doTest.accept("a1", false);
    doTest.accept("$1", true);
    doTest.accept("$123", true);
    doTest.accept("$a", true);
  }

  @Test public void dollarNumIdent() {
    Tokeniser tokeniser = new Tokeniser("$1public");
    Token     token     = tokeniser.next();
    assertEquals(DOLLAR_IDENTIFIER, token.getType());
    assertEquals("$1", token.getValue());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("public", token.getValue());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void numericLiterals() {
    BiConsumer<String, List<Pair<TokenType,Object>>> doTest = (source, typeAndValues) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token[]   token     = { tokeniser.next() };
      typeAndValues.forEach(typeAndValue -> {
        assertEquals(typeAndValue.first, token[0].getType());
        assertEquals(typeAndValue.second, token[0].getValue());
        token[0] = tokeniser.next();
      });
      Assertions.assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("0", Utils.listOf(new Pair<>(TokenType.INTEGER_CONST, 0)));
    doTest.accept("1", Utils.listOf(new Pair<>(TokenType.INTEGER_CONST, 1)));
    doTest.accept("20", Utils.listOf(new Pair<>(TokenType.INTEGER_CONST, 20)));
    doTest.accept("30", Utils.listOf(new Pair<>(TokenType.INTEGER_CONST, 30)));
    doTest.accept("1.0", Utils.listOf(new Pair<>(TokenType.DECIMAL_CONST, new BigDecimal("1.0"))));
    doTest.accept("0.12345", Utils.listOf(new Pair<>(TokenType.DECIMAL_CONST, new BigDecimal("0.12345"))));
    doTest.accept("1L", Utils.listOf(new Pair<>(TokenType.LONG_CONST, 1L)));
    doTest.accept("1L 1.0D", Utils.listOf(new Pair<>(TokenType.LONG_CONST, 1L), new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("1D 1L", Utils.listOf(new Pair<>(TokenType.DOUBLE_CONST, 1D), new Pair<>(TokenType.LONG_CONST, 1L)));
    doTest.accept("1L 1D", Utils.listOf(new Pair<>(TokenType.LONG_CONST, 1L), new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("1D", Utils.listOf(new Pair<>(TokenType.DOUBLE_CONST, 1D)));
    doTest.accept("1.0D", Utils.listOf(new Pair<>(TokenType.DOUBLE_CONST, 1.0D)));
    doTest.accept("" + ((long)Integer.MAX_VALUE + 1L) + "L", Utils.listOf(new Pair<>(TokenType.LONG_CONST, (long)Integer.MAX_VALUE + 1L)));
  }

  @Test public void numericOverflow() {
    BiConsumer<String,Object> doTest = (source,value) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(value, token.getValue());
      Assertions.assertEquals(EOF, tokeniser.next().getType());
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
    Tokeniser tokeniser = new Tokeniser("a.1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      Token token = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      Token token = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("a.1.2.3.b");
    BiFunction<TokenType,String,Token> checkToken = (type, value) -> {
      Token token = tokeniser.next();
      assertEquals(type, token.getType());
      if (type == TokenType.IDENTIFIER || type == TokenType.INTEGER_CONST) {
        assertEquals(value, token.getChars());
      }
      return token;
    };

    Token previous = tokeniser.previous();
    Token token    = checkToken.apply(TokenType.IDENTIFIER, "a");
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
      Tokeniser tokeniser = new Tokeniser(source);
      for (TokenType type: types) {
        assertEquals(type, tokeniser.next().getType());
      }
      Assertions.assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("{}", Utils.listOf(TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE));
    doTest.accept("-1", Utils.listOf(TokenType.MINUS, TokenType.INTEGER_CONST));
    doTest.accept("-1.", Utils.listOf(TokenType.MINUS, TokenType.INTEGER_CONST, TokenType.DOT));
    doTest.accept(".012", Utils.listOf(TokenType.DOT, TokenType.INTEGER_CONST));
    doTest.accept("1.012X", Utils.listOf(TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("1.0DD;2.0Xa", Utils.listOf(TokenType.DOUBLE_CONST, TokenType.IDENTIFIER, TokenType.SEMICOLON, TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("1.012L1D_ab;1.0D2.0Xa", Utils.listOf(TokenType.DECIMAL_CONST, TokenType.IDENTIFIER, TokenType.SEMICOLON, TokenType.DOUBLE_CONST, TokenType.DECIMAL_CONST, TokenType.IDENTIFIER));
    doTest.accept("<<<", Utils.listOf(TokenType.DOUBLE_LESS_THAN, TokenType.LESS_THAN));
    doTest.accept("<<<<", Utils.listOf(TokenType.DOUBLE_LESS_THAN, TokenType.DOUBLE_LESS_THAN));
    doTest.accept("<<<<=", Utils.listOf(TokenType.DOUBLE_LESS_THAN, TokenType.DOUBLE_LESS_THAN_EQUAL));
    doTest.accept(">>>>", Utils.listOf(TokenType.TRIPLE_GREATER_THAN, TokenType.GREATER_THAN));
    doTest.accept("\n>>>\n>\n\n", Utils.listOf(EOL, TokenType.TRIPLE_GREATER_THAN, EOL, TokenType.GREATER_THAN, EOF));
  }

  @Test public void lineNumbers() {
    Tokeniser tokeniser = new Tokeniser("1\n2\n3\n");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOF, token.getType());
  }

  @Test public void lineColumnNumbers() {
    String    source    = "1a b\na\n\na 1.234D c\n{";
    Tokeniser tokeniser = new Tokeniser(source);
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
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
    Assertions.assertEquals(EOL, token.getType());
    assertEquals(4, token.getLineNum());
    assertEquals(11, token.getColumn());

    token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());

    token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
    assertEquals(5, token.getLineNum());
    assertEquals(2, token.getColumn());
    JactlError error = new EOFError("EOF", token);
    //System.out.println(error.getMessage());
    assertTrue(error.getMessage().contains("line 5, column 2"));
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void comments() {
    Tokeniser tokeniser = new Tokeniser("/**/1a b\nX//<<>><><\n//\n/**/\n/*asdasd*///asdas\n\nY 1.234D/*a\n*/ /*//*/ c");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(9, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    assertEquals("X", token.getValue());
    assertEquals(2, token.getLineNum());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
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

    Assertions.assertEquals(EOF, tokeniser.next().getType());
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void initialLineComment() {
    Tokeniser tokeniser = new Tokeniser("//\n\n123");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    assertEquals(1, token.getLineNum());
    assertEquals(3, token.getColumn());
    assertTrue(new RuntimeError("Error", token.getLine(), token.getColumn()).getMessage().contains("line 1, column 4"));
    token = tokeniser.next();
    assertTrue(token.is(INTEGER_CONST));
  }

  @Test public void unexpectedEofInComment() {
    Tokeniser tokeniser = new Tokeniser("/*\n/*asdasd");
    try {
      tokeniser.next();
      fail("Should have thrown an exception");
    }
    catch (CompileError error) {
      assertTrue(error.getMessage().toLowerCase().contains("unexpected end of file in comment"));
    }
  }

  @Test public void unexpectedCharacter() {
    Tokeniser tokeniser = new Tokeniser("a b !<#> c");
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
    Tokeniser tokeniser = new Tokeniser("a 1 { ]>");
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.INTEGER_CONST, tokeniser.next().getType());
    Token prev  = tokeniser.previous();
    Token token = tokeniser.next();
    Assertions.assertEquals(TokenType.LEFT_BRACE, token.getType());
    Assertions.assertEquals(TokenType.RIGHT_SQUARE, tokeniser.next().getType());
    tokeniser.rewind(prev, token);
    Assertions.assertEquals(TokenType.LEFT_BRACE, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.RIGHT_SQUARE, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.GREATER_THAN, tokeniser.next().getType());
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void simpleString() {
    Tokeniser tokeniser = new Tokeniser("'abc'");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void simpleStrings() {
    Tokeniser tokeniser = new Tokeniser("'abc''xyz'");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("abc", token.getValue());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("xyz", token.getValue());
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void strings() {
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'\n b = 2");
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
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void strings2() {
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'");
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
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
  }

  @Test public void stringsUnexpectedEndOfFile() {
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc");
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
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc\na = 2");
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
    Tokeniser tokeniser = new Tokeniser("a = \"a'$b//\n/*\\t*/\\b\\r\\fc\"\na = 2");
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
    Tokeniser tokeniser = new Tokeniser("a = \"a'//\n/*\\t*/\\b\\r\\fc\"\na = 2");
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
    Tokeniser tokeniser = new Tokeniser("'''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(TokenType.STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLineNum());
    assertEquals(1, token.getColumn());
  }

  @Test public void multiLineStrings() {
    Tokeniser tokeniser = new Tokeniser("a = '''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''\n b = 2");
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
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void multiLineStringsWithCarriageReturns() {
    Tokeniser tokeniser = new Tokeniser("a = '''a\\'b//\r\n/*\\t*/\\b\\r\\f\r\nc'''\r\n b = 2");
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
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.EQUAL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.INTEGER_CONST, token.getType());
  }

  @Test public void keywordFollowedByWordChar() {
    Tokeniser tokeniser = new Tokeniser("!instanceof !instanceofx forx while whilex while2");
    BiConsumer<TokenType,String> checkToken = (type,identifier) -> {
      Token token = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("\"This is $abc. a test\"");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void nestedStringExpr() {
    Tokeniser tokeniser = new Tokeniser("\"\"\"This ${is}${abc + \"x${\"\"\"a\"\"\"}\"}. a test\"\"\"");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void multiLineStringInsideSingleLineString() {
    Tokeniser tokeniser = new Tokeniser("\"this is a ${'''\n'''} test\"");
    Token     token     = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("\"for $whilex$while\"");
    Token     token     = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("for { \"${while(true && \"${x}\"){};}\" }");
    Utils.listOf(TokenType.FOR, TokenType.LEFT_BRACE, TokenType.EXPR_STRING_START, TokenType.LEFT_BRACE, TokenType.WHILE, TokenType.LEFT_PAREN, TokenType.TRUE, TokenType.AMPERSAND_AMPERSAND,
            TokenType.EXPR_STRING_START, TokenType.LEFT_BRACE, TokenType.IDENTIFIER, TokenType.RIGHT_BRACE, TokenType.EXPR_STRING_END, TokenType.RIGHT_PAREN,
            TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE, TokenType.SEMICOLON, TokenType.RIGHT_BRACE, TokenType.EXPR_STRING_END, TokenType.RIGHT_BRACE)
      .forEach(type -> assertEquals(type, tokeniser.next().getType()));
  }

  @Test public void unmatchedRightBrace() {
    Tokeniser tokeniser = new Tokeniser("{}}");
    Utils.listOf(TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE).forEach(type -> assertEquals(type, tokeniser.next().getType()));
    try {
      tokeniser.next();
      fail("Expected CompileError");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("does not match any opening brace"));
    }
  }

  @Test public void rightBraceInStringExpr() {
    Tokeniser tokeniser = new Tokeniser("\"${a}}\"");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOF, token.getType());
  }

  @Test public void nestedExprStrings() {
    Tokeniser tokeniser = new Tokeniser("\"$x${\"${2*4}\" + 2}/*\"");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void newLineInExpressionInSingleLineString() {
    Tokeniser tokeniser = new Tokeniser("\"${a = \n2}\"");
    Token     token     = tokeniser.next();
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
    Tokeniser tokeniser = new Tokeniser("+ - *");
    Assertions.assertEquals(TokenType.PLUS, tokeniser.peek().getType());
    Token prev  = tokeniser.previous();
    Token token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.peek().getType());
    tokeniser.rewind(prev, token);
    Assertions.assertEquals(TokenType.PLUS, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.PLUS, token.getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.peek().getType());
    Assertions.assertEquals(TokenType.MINUS, tokeniser.next().getType());
    Assertions.assertEquals(TokenType.STAR, tokeniser.next().getType());
    Assertions.assertEquals(EOF, tokeniser.peek().getType());
    Assertions.assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void peekEmpty() {
    Tokeniser tokeniser = new Tokeniser("");
    Assertions.assertEquals(EOF, tokeniser.peek().getType());
    Token token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
    Assertions.assertEquals(EOF, tokeniser.peek().getType());
  }

  @Test public void peekEmptyComment() {
    Tokeniser tokeniser = new Tokeniser("/* no tokens */");
    Assertions.assertEquals(EOF, tokeniser.peek().getType());
    Token token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
    Assertions.assertEquals(EOF, tokeniser.peek().getType());
  }

  @Test public void newlines() {
    Tokeniser tokeniser = new Tokeniser("\n\na\nb\n\nc\n\n\n\n");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    Assertions.assertEquals(EOL, tokeniser.previous().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.previous().getType());
    Assertions.assertEquals(EOL, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    Assertions.assertEquals(EOL, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
  }

  @Test public void newlinesWithCarriageReturns() {
    Tokeniser tokeniser = new Tokeniser("\r\n\r\na\r\nb\r\n\r\nc\r\n\r\n\r\n\r\n");
    Token     token     = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    Assertions.assertEquals(EOL, tokeniser.previous().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.previous().getType());
    Assertions.assertEquals(EOL, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOL, token.getType());
    Assertions.assertEquals(EOL, tokeniser.previous().getType());
    Assertions.assertEquals(TokenType.IDENTIFIER, tokeniser.peek().getType());
    token = tokeniser.next();
    Assertions.assertEquals(TokenType.IDENTIFIER, token.getType());
    token = tokeniser.next();
    Assertions.assertEquals(EOF, token.getType());
  }

  @Test public void regexStrings() {
    Tokeniser tokeniser = new Tokeniser("a = /a\\/$a${x\n+y}\\n/*2");
    Token     token     = tokeniser.next();
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
    Assertions.assertEquals(EOL, token.getType());
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
    Assertions.assertEquals(EOF, token.getType());

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

  @Test public void numberOverflow() {
    Tokeniser tokeniser = new Tokeniser("12345123451234512345L");
    try {
      Token token = tokeniser.next();
      fail("Expected numeric overflow exception");
    }
    catch (JactlError e) {
      assertTrue(e.getMessage().contains("too large"));
    }
  }
}