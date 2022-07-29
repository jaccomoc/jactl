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
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

  @Test public void literals() {
    BiConsumer<String,Object> doTest = (code,expected) -> assertEquals(expected, Compiler.run(code, null));
    doTest.accept("42",42);
    doTest.accept("0", 0);
    doTest.accept("1", 1);
    doTest.accept("2", 2);
    doTest.accept("3", 3);
    doTest.accept("4", 4);
    doTest.accept("5", 5);
    doTest.accept("6", 6);
    doTest.accept(Byte.toString(Byte.MAX_VALUE), (int)Byte.MAX_VALUE);
    doTest.accept(Integer.toString(Byte.MAX_VALUE + 1), (int)Byte.MAX_VALUE + 1);
    doTest.accept(Short.toString(Short.MAX_VALUE), (int)Short.MAX_VALUE);
    doTest.accept(Integer.toString(Short.MAX_VALUE + 1), (int)Short.MAX_VALUE + 1);
    doTest.accept(Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    try {
      doTest.accept("" + (Integer.MAX_VALUE + 1L), "ERROR");
      fail("Should have thrown an error");
    }
    catch (CompileError e) {
      assertTrue(e.getMessage().toLowerCase().contains("number too large"));
    }
    doTest.accept("" + ((long)Integer.MAX_VALUE + 1L) + "L", (long)Integer.MAX_VALUE + 1L);
    doTest.accept("1D", 1D);
    doTest.accept("1.0D", 1.0D);
    doTest.accept("0.0D", 0.0D);
    doTest.accept("0.1D", 0.1D);
    doTest.accept("1.0", new BigDecimal("1.0"));
    doTest.accept("0.0", new BigDecimal("0.0"));
    doTest.accept("0.1", new BigDecimal("0.1"));
    doTest.accept("0.123456789123456789", new BigDecimal("0.123456789123456789"));
    doTest.accept("''", "");
    doTest.accept("'123'", "123");
    doTest.accept("'1\\'23'", "1'23");
    doTest.accept("'''1'23'''", "1'23");
    doTest.accept("'''1'23''\\''''", "1'23'''");
    doTest.accept("'\\t\\f\\b\\r\\n'", "\t\f\b\r\n");
    doTest.accept("true", true);
    doTest.accept("false", false);
    doTest.accept("null", null);
  }
}