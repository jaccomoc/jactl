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

package io.jactl.runtime;

import io.jactl.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JsonDecoder {
  static final char                     EOS           = 0;
  static final int                      CHAR_BUF_SIZE = 1024;
  static final ThreadLocal<JsonDecoder> threadDecoder = ThreadLocal.withInitial(JsonDecoder::new);
  static final ThreadLocal<char[]>      threadCharBuf = ThreadLocal.withInitial(() -> new char[CHAR_BUF_SIZE]);
  String json;
  int    length;
  int    offset = 0;
  String source;
  int    sourceOffset;

  public static JsonDecoder get(String json, String source, int sourceOffset) {
    JsonDecoder decoder = threadDecoder.get();
    decoder.offset = 0;
    decoder.json = json;
    decoder.length = json.length();
    decoder.source = source;
    decoder.sourceOffset = sourceOffset;
    return decoder;
  }

  public Object decode() {
    Object result = _decode();
    skipWhitespace();
    if (offset != length) {
      throw new RuntimeError("Offset " + offset + ": Extra data found at end of json", source, offset);
    }
    return result;
  }

  public static long[] decodeJactlObj(String json, String source, int sourceOffset, JactlObject obj) {
    JsonDecoder decoder = get(json, source, sourceOffset);
    try {
      // Return flags indicating which optional fields still need to be initialised or null if
      // JSON decode has null value
      return obj._$j$readJson(decoder);
    }
    catch (Continuation e) {
      throw new RuntimeError("Async field initialisation detected during JSON decode", source, sourceOffset);
    }
  }

  public Object _decode() {
    char c = offset < length ? json.charAt(offset++) : 0;
    if (c == '"') return decodeString();
    if (c == '{') return decodeMap();
    if (c <= ' ') {
      c = nextChar();
    }
    switch (c) {
      case EOS:
        return null;
      case '-':
      case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
        return decodeNumber(c);
      case '"':
        return decodeString();
      case '[':
        return decodeList();
      case '{':
        return decodeMap();
      case 't':
        if (offset <= length - 3 && json.startsWith("rue", offset)) {
          offset += 3;
          return true;
        }
        break;
      case 'f':
        if (offset <= length - 4 && json.startsWith("alse", offset)) {
          offset += 4;
          return false;
        }
        break;
      case 'n':
        if (offset <= length - 3 && json.startsWith("ull", offset)) {
          offset += 3;
          return null;
        }
        break;
    }
    error("Unexpected character " + quoted(c));
    return null;
  }

  JactlList decodeList() {
    if (offset >= length) error("Unexpected end of JSON decoding list");
    JactlList list  = RuntimeUtils.createList();
    boolean   first = true;
    while (true) {
      char c = skipWhitespace();
      if (c == EOS) error("Unexpected end of JSON decoding list");
      if (c == ']') {
        break;
      }
      if (!first) {
        if (c != ',') error("Expected ',': unexpected character '" + c + "' while decoding list");
        offset++;
      }
      first = false;
      list.add(_decode());
    }
    offset++;
    return list;
  }

  JactlMap decodeMap() {
    if (offset >= length) error("Unexpected end of JSON decoding map");
    JactlMap map   = RuntimeUtils.createMap();
    boolean  first = true;
    while (true) {
      char c = nextChar();
      if (c == EOS) error("Unexpected end of JSON decoding map");
      if (c == '}') {
        break;
      }
      if (!first) {
        if (c != ',') error("Expected ',': unexpected character '" + c + "' while decoding map");
        c = nextChar();
      }
      first = false;
      if (c != '"') error("Expected '\"' but found " + quoted(c) + " while decoding map key");
      int fieldOffset = offset;
      String field = decodeString();
      c = nextChar();
      if (c != ':') error("Expected ':' but found " + quoted(c) + " while decoding map");
      Object value = _decode();
      if (map.put(field, value) != null) {
        error("Duplicate field '" + field + "' when decoding json", fieldOffset);
      }
    }
    return map;
  }

  static int  MAXINT_DIV_10 = Integer.MAX_VALUE / 10;
  static int  MAXINT_REM_10 = Integer.MAX_VALUE % 10;
  static long MAX_DIV_10    = Long.MAX_VALUE / 10;
  static long MAX_REM_10    = Long.MAX_VALUE % 10;

  Number decodeNumber(char startChar) {
    int     startOffset = offset - 1;
    boolean isNegative  = startChar == '-';
    if (isNegative) {
      if (offset >= length) error("Unexpected end of JSON reading negative number");
      startChar = json.charAt(offset++);
      if (startChar < '0' || startChar > '9') error("Unexpected character '" + startChar + "' while decoding number");
    }
    int result = 0;
    char c      = startChar;
    int  i      = offset;
   WHILE:
    while (true) {
      switch (c) {
        case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
          int digit = c - '0';
          if (result < MAXINT_DIV_10 || result == MAXINT_DIV_10 && digit <= MAXINT_REM_10) {
            result = 10 * result + digit;
          }
          else {
            // Too big for int so now we parse as long
            long longResult = (long)result * 10 + digit;
            c = i < length ? json.charAt(i++) : EOS;
            while (true) {
              switch (c) {
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                  digit = c - '0';
                  if (longResult < MAX_DIV_10 || longResult == MAX_DIV_10 && digit <= MAX_REM_10) {
                    longResult = 10 * longResult + digit;
                  }
                  else {
                    // Number is too big for a long value so break out and return BigDecimal instead
                    break WHILE;
                  }
                  break;
                case 'e': case 'E': case '.':
                  break WHILE;
                default:
                  i--;
                case EOS:
                  offset = i;
                  return isNegative ? -longResult : longResult;
              }
              c = i < length ? json.charAt(i++) : EOS;
            }
          }
          break;
        case 'e': case 'E': case '.':
          break WHILE;

        // All the following cases fall through to the last case (if no errors)
        case 'n':
          if (!isNegative && i - startOffset == 1 && json.startsWith("ull", i)) {
            offset = i + 3;
            return null;
          }
          // Fall through
        default:
          if (!Character.isWhitespace(c)) {
            error("Invalid character " + quoted(c) + " while decoding number");
          }
          // Fall through
        case ',': case '}': case ']':
          i--;
          // Fall through
        case EOS:
          offset = i;
          return isNegative ? -result : result;
      }
      c = i < length ? json.charAt(i++) : EOS;
    }

    // Number became too large or had exponent of decimal place
    WHILE:
    while (i < length) {
      c = json.charAt(i++);
      switch (c) {
        case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
        case 'e': case 'E': case '.':
          break;
        default:
          i--;
          break WHILE;
      }
    }

    String numberStr = json.substring(startOffset, i);
    try {
      offset = i;
      return new BigDecimal(numberStr);
    }
    catch (NumberFormatException e) {
      error("Illegally formatted number: " + numberStr + ": " + e.getMessage());
    }
    return null;
  }

  public String decodeString() {
    int start = offset;
    for (int i = offset; i < length; ) {
      char c = json.charAt(i++);
      if (c == '\\') {
        offset = i - 1;
        return decodeComplexString(start);
      }
      if (c == '\"') {
        offset = i;
        return json.substring(start, i - 1);
      }
    }
    offset = length;
    error("Unterminated string beginning", start);
    return null;
  }

  static int[] hexValue = new int[256];

  static {
    for (char i = 0; i < 256; i++) {
      hexValue[i] = -1;
      if (i >= '0' && i <= '9') hexValue[i] = (i - '0');
      if (i >= 'A' && i <= 'F') hexValue[i] = (i - 'A' + 10);
      if (i >= 'a' && i <= 'f') hexValue[i] = (i - 'a' + 10);
    }
  }

  String decodeComplexString(int start) {
    int    strSize = offset - start;
    char[] buf     = strSize < CHAR_BUF_SIZE ? threadCharBuf.get() : new char[strSize * 2];
    for (int i = 0; start + i < offset; i++) {
      buf[i] = json.charAt(start + i);
    }
    for (int i = offset; i < length; ) {
      char c = json.charAt(i++);
      if (c == '"') {
        offset = i;
        return new String(buf, 0, strSize);
      }
      if (c == '\\') {
        if (i > length - 1) {
          error("Unexpected end of JSON decoding escape sequence");
        }
        c = json.charAt(i++);
        if (strSize + 2 >= buf.length) {
          char[] newBuf = new char[buf.length * 2];
          System.arraycopy(buf, 0, newBuf, 0, strSize);
          buf = newBuf;
        }
        switch (c) {
          case '"':  break;
          case '\\': break;
          case 'b': c = '\b'; break;
          case 'f': c = '\f'; break;
          case 'n': c = '\n'; break;
          case 'r': c = '\r'; break;
          case 't': c = '\t'; break;
          case 'u':
            if (offset > length - 4) {
              error("Unexpected end of JSON decoding escape \\u sequence");
            }
            c = 0;
            for (int j = 0; j < 4; j++) {
              char hexDigit = json.charAt(i++);
              if (hexDigit > 256) error("Bad hex digit " + hexDigit);
              int hex = hexValue[(hexDigit & 0xff)];
              if (hex < 0) error("Bad hex digit " + hexDigit);
              c = (char) ((c << 4) + hex);
            }
            break;
          default:
            // Leave unchanged if unrecognised escape sequence
            buf[strSize++] = '\\';
            break;
        }
      }
      buf[strSize++] = c;
    }
    offset = length;
    error("Unterminated string in JSON data");
    return null;
  }


  public void error(String error, char c) {
    error(error + quoted(c), offset - 1);
  }

  public void error(String error) {
    error(error, offset);
  }

  public void error(String error, int errOffset) {
    throw new RuntimeError("At JSON offset " + errOffset + ": " + error, source, sourceOffset);
  }

  public void missingFields(int flag, long value, JactlObject obj) {
    //error("DEBUG: missing fields: flag=" + flag + ", value=" + value);
    List<Field> fields = Utils.getFields(obj.getClass());
    int count = fields.size() >= (flag + 1) * 64 ? 64 : fields.size() % 64;
    String missingFields = IntStream.range(0,count)
                                    .filter(i -> (value & (1L << i)) != 0)
                                    .mapToObj(i -> fields.get(flag*64 + i).getName())
                                    .collect(Collectors.joining(","));
    error("For class " + obj.getClass().getName() + ", missing mandatory field(s): " + missingFields);
  }

  public char skipWhitespace() {
    for (; offset < length; offset++) {
      char c = json.charAt(offset);
      if (c > ' ') return c;
      if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
        return c;
      }
    }
    return EOS;
  }

  public char nextChar() {
    for (; offset < length; ) {
      char c = json.charAt(offset++);
      if (c > ' ') return c;
      if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
        return c;
      }
    }
    return EOS;
  }

  public void expect(char c) {
    char next = skipWhitespace();
    if (next != c) error("Expecting '" + c + "' but got " + quoted(next));
    offset++;
  }

  private static String quoted(char next) {
    return next == EOS ? "end of json" : "'" + next + "'";
  }

  public boolean expectOrNull(char expected) {
    char next = nextChar();
    if (next == expected) return true;
    if (next == 'n' && json.startsWith("ull", offset)) {
      offset += 3;
      return false;
    }
    error("Expected '" + expected + "' or 'null' but got " + quoted(next));
    return false;
  }

  public String getString() {
    if (expectOrNull('"')) {
      return decodeString();
    }
    return null;
  }

  public boolean getBoolean() {
    char c = nextChar();
    if (c == 't' && offset + 3 <= length && json.startsWith("rue", offset)) {
      offset += 3;
      return true;
    }
    if (c == 'f' && offset + 4 <= length && json.startsWith("alse", offset)) {
      offset += 4;
      return false;
    }
    error("Invalid JSON when looking for boolean value");
    return false;
  }

  public byte getByte() {
    int start = offset;
    int value = getInt();
    if (value >= 256 || value < -128) {
      error("Value " + value + " does not fit in byte", start);
    }
    return (byte)value;
  }

  public int getInt() {
    char c = nextChar();
    int start = offset - 1;
    Number num = decodeNumber(c);
    if (num instanceof Integer) {
      return (int)num;
    }
    if (num instanceof Long) {
      error("Integer overflow reading json", start);
    }
    if (num == null) {
      error("Integer value cannot be null", start);
    }
    error("Floating point number where integer required while parsing json", start);
    return -1;
  }

  public long getLong() {
    char c = nextChar();
    int start = offset - 1;
    Number num = decodeNumber(c);
    if (num instanceof BigDecimal) {
      error("Floating point number where long required while parsing json", start);
    }
    if (num == null) {
      error("Long value cannot be null", start);
    }
    return num.longValue();
  }

  public double getDouble() {
    char c = nextChar();
    int start = offset - 1;
    Number num = decodeNumber(c);
    if (num instanceof Long) {
      return Double.valueOf((long)num);
    }
    if (num == null) {
      error("Double value cannot be null", start);
    }
    return num.doubleValue();
  }

  public BigDecimal getDecimal() {
    char c = nextChar();
    if (c == 'n' && json.startsWith("ull", offset)) {
      offset += 3;
      return null;
    }
    Number num = decodeNumber(c);
    if (num instanceof BigDecimal) {
      return (BigDecimal) num;
    }
    return BigDecimal.valueOf(num.longValue());
  }

  public JactlMap getMap() {
    if (expectOrNull('{')) {
      return decodeMap();
    }
    return null;
  }

  public JactlList getList() {
    if (expectOrNull('[')) {
      return decodeList();
    }
    return null;
  }
}
