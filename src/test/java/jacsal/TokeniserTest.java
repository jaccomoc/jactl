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

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static jacsal.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

class TokeniserTest {

  @Test public void simpleTokens() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      assertEquals(type, tokeniser.next().getType());
      assertEquals(EOF, tokeniser.next().getType());
    };
    doTest.accept("(", LEFT_PAREN);
    doTest.accept(")", RIGHT_PAREN);
    doTest.accept("[", LEFT_SQUARE);
    doTest.accept("]", RIGHT_SQUARE);
    doTest.accept("{", LEFT_BRACE);
    doTest.accept("}", RIGHT_BRACE);
    doTest.accept("!", BANG);
    doTest.accept("$", DOLLAR);
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
    doTest.accept("!=", BANG_EQUAL);
    doTest.accept("==", EQUAL_EQUAL);
    doTest.accept("<=", LESS_THAN_EQUAL);
    doTest.accept(">=", GREATER_THAN_EQUAL);
    doTest.accept("?:", QUESTION_COLON);
    doTest.accept("&&", AMPERSAND_AMPERSAND);
    doTest.accept("||", PIPE_PIPE);
    doTest.accept("-=", MINUS_EQUAL);
    doTest.accept("+=", PLUS_EQUAL);
    doTest.accept("/=", SLASH_EQUAL);
    doTest.accept("*=", STAR_EQUAL);
    doTest.accept("&=", AMPERSAND_EQUAL);
    doTest.accept("|=", PIPE_EQUAL);
    doTest.accept("^=", ACCENT_EQUAL);
    doTest.accept("~=", GRAVE_EQUAL);
    doTest.accept("?=", QUESTION_EQUAL);
    doTest.accept("<<", DOUBLE_LESS_THAN);
    doTest.accept(">>", DOUBLE_GREATER_THAN);
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
    doTest.accept("it", IT);
    doTest.accept("int", INT);
    doTest.accept("flout", FLOAT);
    doTest.accept("double", DOUBLE);
    doTest.accept("String", STRING);
    doTest.accept("void", VOID);
    doTest.accept("for", FOR);
    doTest.accept("if", IF);
    doTest.accept("while", WHILE);
    doTest.accept("class", CLASS);
    doTest.accept("interface", INTERFACE);
    doTest.accept("extends", EXTENDS);
    doTest.accept("implements", IMPLEMENTS);
    doTest.accept("import", IMPORT);
    doTest.accept("as", AS);
    doTest.accept("return", RETURN);
    doTest.accept("instanceof", INSTANCE_OF);
    doTest.accept("!instanceof", BANG_INSTANCE_OF);
  }

  @Test public void identifiers() {
    Consumer<String> doTest = source -> {
      Tokeniser tokeniser = new Tokeniser(source);
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
  }

  @Test public void numericLiterals() {
    BiConsumer<String,TokenType> doTest = (source,type) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      Token     token     = tokeniser.next();
      assertEquals(type,   token.getType());
      String expectedValue = source.endsWith("D") || source.endsWith("L") ? source.substring(0, source.length() - 1) : source;
      assertEquals(expectedValue, token.getValue());
      assertEquals(EOF,    tokeniser.next().getType());
    };

    doTest.accept("0", INTEGER_CONST);
    doTest.accept("1", INTEGER_CONST);
    doTest.accept("20", INTEGER_CONST);
    doTest.accept("30", INTEGER_CONST);
    doTest.accept("1.0", DECIMAL_CONST);
    doTest.accept("0.12345", DECIMAL_CONST);
    doTest.accept(Long.toString(Long.MAX_VALUE), INTEGER_CONST);  // Tokenises as int - we will check for overflow in parser
    doTest.accept("1L", LONG_CONST);
    doTest.accept("1D", DOUBLE_CONST);
    doTest.accept("1.0D", DOUBLE_CONST);
  }

  @Test public void multipleTokens() {
    BiConsumer<String, List<TokenType>> doTest = (source, types) -> {
      Tokeniser tokeniser = new Tokeniser(source);
      for (TokenType type: types) {
        assertEquals(type, tokeniser.next().getType());
      }
      assertEquals(EOF, tokeniser.next().getType());
    };

    doTest.accept("-1", List.of(MINUS, INTEGER_CONST));
    doTest.accept("-1.", List.of(MINUS, INTEGER_CONST, DOT));
    doTest.accept(".012", List.of(DOT, INTEGER_CONST));
    doTest.accept("1.012L", List.of(DECIMAL_CONST, IDENTIFIER));
    doTest.accept("1.0DD;2.0La", List.of(DOUBLE_CONST, IDENTIFIER, SEMICOLON, DECIMAL_CONST, IDENTIFIER));
    doTest.accept("1.012L1D_ab;1.0D2.0La", List.of(DECIMAL_CONST, IDENTIFIER, SEMICOLON, DOUBLE_CONST, DECIMAL_CONST, IDENTIFIER));
    doTest.accept("<<<", List.of(DOUBLE_LESS_THAN, LESS_THAN));
    doTest.accept("<<<<", List.of(DOUBLE_LESS_THAN, DOUBLE_LESS_THAN));
    doTest.accept("<<<<=", List.of(DOUBLE_LESS_THAN, DOUBLE_LESS_THAN_EQUAL));
    doTest.accept(">>>>", List.of(TRIPLE_GREATER_THAN, GREATER_THAN));
    doTest.accept("\n>>>\n>\n\n", List.of(EOL, TRIPLE_GREATER_THAN, EOL, GREATER_THAN, EOL, EOL));
  }

  @Test public void lineColumnNumbers() {
    Tokeniser tokeniser = new Tokeniser("1a b\na\n\na 1.234D c");
    Token token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals("1", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(4, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(1, token.getLine());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(2, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(2, token.getLine());
    assertEquals(2, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(3, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(4, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(DOUBLE_CONST, token.getType());
    assertEquals("1.234", token.getValue());
    assertEquals(4, token.getLine());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(4, token.getLine());
    assertEquals(10, token.getColumn());

    assertEquals(EOF, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void comments() {
    Tokeniser tokeniser = new Tokeniser("/**/1a b\nX//<<>><><\n//\n/**/\n/*asdasd*///asdas\n\nY 1.234D/*a\n*/ /*//*/ c");
    Token token = tokeniser.next();
    assertEquals(INTEGER_CONST, token.getType());
    assertEquals("1", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("b", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(8, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(1, token.getLine());
    assertEquals(9, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("X", token.getValue());
    assertEquals(2, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(2, token.getLine());
    assertEquals(11, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(3, token.getLine());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(4, token.getLine());
    assertEquals(5, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(5, token.getLine());
    assertEquals(18, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOL, token.getType());
    assertEquals(6, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("Y", token.getValue());
    assertEquals(7, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(DOUBLE_CONST, token.getType());
    assertEquals("1.234", token.getValue());
    assertEquals(7, token.getLine());
    assertEquals(3, token.getColumn());
    token = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("c", token.getValue());
    assertEquals(8, token.getLine());
    assertEquals(11, token.getColumn());

    assertEquals(EOF, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.next().getType());
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
    assertEquals(IDENTIFIER, tokeniser.next().getType());
    assertEquals(INTEGER_CONST, tokeniser.next().getType());
    Token token = tokeniser.next();
    assertEquals(LEFT_BRACE, token.getType());
    assertEquals(RIGHT_SQUARE, tokeniser.next().getType());
    tokeniser.rewind(token);
    assertEquals(LEFT_BRACE, tokeniser.next().getType());
    assertEquals(RIGHT_SQUARE, tokeniser.next().getType());
    assertEquals(GREATER_THAN, tokeniser.next().getType());
    assertEquals(EOF, tokeniser.next().getType());
  }

  @Test public void strings() {
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'\n b = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLine());
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
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc'");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\fc", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(6, token.getColumn());
    token = tokeniser.next();
    assertEquals(EOF, token.getType());
  }

  @Test public void stringsUnexpectedEndOfFile() {
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
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
    Tokeniser tokeniser = new Tokeniser("a = 'a\\'b//\\n/*\\t*/\\b\\r\\fc\na = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    try {
      token = tokeniser.next();
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("unexpected end of line in string"));
    }
  }

  @Test public void multilineStrings() {
    Tokeniser tokeniser = new Tokeniser("a = '''a\\'b//\\n/*\\t*/\\b\\r\\f\nc'''\n b = 2");
    Token     token     = tokeniser.next();
    assertEquals(IDENTIFIER, token.getType());
    assertEquals("a", token.getValue());
    assertEquals(1, token.getLine());
    assertEquals(1, token.getColumn());
    token = tokeniser.next();
    assertEquals(EQUAL, token.getType());
    token = tokeniser.next();
    assertEquals(STRING_CONST, token.getType());
    assertEquals("a'b//\n/*\t*/\b\r\f\nc", token.getValue());
    assertEquals(1, token.getLine());
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
}