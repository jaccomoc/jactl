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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class JsonEncoder {
  static ThreadLocal<JsonEncoder> threadBuffer    = ThreadLocal.withInitial(JsonEncoder::new);
  public static final int         MAX_BUFFER_SIZE = 100_000_000;
  public static final int         MAX_CACHE_SIZE  = 1_000_000;

  String source;
  int    sourceOffset;
  int    length = 128;
  byte[] bytes  = new byte[length];
  int    offset = 0;

  public static JsonEncoder get(String source, int sourceOffset) {
    var buffer = threadBuffer.get();
    buffer.source = source;
    buffer.sourceOffset = sourceOffset;
    buffer.offset = 0;
    return buffer;
  }

  public String finalise() {
    String result = new String(bytes, 0, offset);
    if (length >= MAX_CACHE_SIZE) {
      length = 128;
      bytes = new byte[length];
    }
    return result;
  }

  public void ensureCapacity(int n) {
    if (n + offset >= length) {
      length = length * 2;
      while (n + offset >= length && length < MAX_BUFFER_SIZE) {
        length *= 2;
        if (length > MAX_BUFFER_SIZE) {
          throw new RuntimeError("Exceeded maximum JSON buffer size", source, sourceOffset);
        }
      }
      byte[] buf = new byte[length];
      System.arraycopy(bytes, 0, buf, 0, offset);
      bytes = buf;
    }
  }

  public void writeByte(char b) {
    if (offset >= length) {
      ensureCapacity(1);
    }
    bytes[offset++] = (byte) b;
  }

  public void writeByte(char b, boolean replace) {
    if (replace) {
      bytes[offset - 1] = (byte) b;
    }
    else {
      writeByte(b);
    }
  }

  public void writeObj(Object obj) {
    if (obj == null) {
      writeString("null", false);
      return;
    }
    if (obj instanceof String) {
      writeString((String) obj, true);
      return;
    }
    if (obj instanceof BigDecimal) {
      writeString(((BigDecimal) obj).toPlainString(), false);
      return;
    }
    if (obj instanceof Byte || obj instanceof Integer || obj instanceof Long) {
      writeLong(((Number)obj).longValue());
      return;
    }
    if (obj instanceof Double) {
      writeString(Double.toString((double)obj), false);
      return;
    }
    if (obj instanceof Boolean) {
      writeString(((boolean) obj) ? "true" : "false", false);
      return;
    }
    if (obj instanceof List) {
      List list = (List) obj;
      writeByte('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          writeByte(',');
        }
        writeObj(list.get(i));
      }
      writeByte(']');
      return;
    }
    if (obj instanceof Map) {
      writeByte('{');
      var iterator = ((Map<String, Object>) obj).entrySet().iterator();
      boolean first = true;
      while (iterator.hasNext()) {
        if (!first) {
          writeByte(',');
        }
        else {
          first = false;
        }
        var entry = iterator.next();
        Object value = entry.getValue();
        writeString(entry.getKey(), true);
        writeByte(':');
        writeObj(value);
      }
      writeByte('}');
      return;
    }
    if (obj instanceof JactlObject) {
      ((JactlObject)obj)._$j$writeJson(this);
      return;
    }
  }

  private static byte[] hex = new byte[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  public void writeString(String str) {
    writeString(str, true);
  }

  public void writeBareString(String str) {
    writeString(str, false);
  }

  public void writeString(String str, boolean quotes) {
    if (str == null) {
      writeNull();
      return;
    }
    ensureCapacity(str.length() + 13);
    int startOffset = offset;
    if (quotes) {
      bytes[offset++] = '"';
    }
    int length = str.length();
    for (int i = 0, extra = 0; i < length; i++) {
      if (extra >= 7) {
        ensureCapacity(offset - startOffset + length - i + 13);
        extra = 0;
      }
      int c = str.charAt(i);
      switch (c) {
        case '\\':
        //case '/':
        case '"':
          bytes[offset++] = '\\';
          bytes[offset++] = (byte) c;
          extra++;
          break;
        default:
          if (c >>> 5 > 0 && c < 0x7f) {
            bytes[offset++] = (byte) c;
          }
          else {
            extra++;
            switch (c) {
              case '\b':
                bytes[offset++] = '\\';
                bytes[offset++] = 'b';
                break;
              case '\f':
                bytes[offset++] = '\\';
                bytes[offset++] = 'f';
                break;
              case '\n':
                bytes[offset++] = '\\';
                bytes[offset++] = 'n';
                break;
              case '\r':
                bytes[offset++] = '\\';
                bytes[offset++] = 'r';
                break;
              case '\t':
                bytes[offset++] = '\\';
                bytes[offset++] = 't';
                break;
              default:
                if (c <= 0x9f) {
                  bytes[offset++] = '\\';
                  bytes[offset++] = 'u';
                  bytes[offset++] = '0'; // hex[(c >>> 12) & 0xf];
                  bytes[offset++] = '0'; // hex[(c >>> 8) & 0x0f];
                  bytes[offset++] = hex[(c >>> 4) & 0x0f];
                  bytes[offset++] = hex[c & 0x0f];
                  extra += 4;
                }
                else {
                  bytes[offset++] = (byte)(c & 0xff);
                  if (c > 0xff) {
                    bytes[offset++] = (byte)((c >> 8) & 0xff);
                  }
                }
                break;
            }
          }
      }
    }
    if (quotes) {
      bytes[offset++] = ('"');
    }
  }

  public void writeBoolean(boolean b) {
    if (b) {
      ensureCapacity(4);
      bytes[offset++] = 't';
      bytes[offset++] = 'r';
      bytes[offset++] = 'u';
      bytes[offset++] = 'e';
    }
    else {
      ensureCapacity(5);
      bytes[offset++] = 'f';
      bytes[offset++] = 'a';
      bytes[offset++] = 'l';
      bytes[offset++] = 's';
      bytes[offset++] = 'e';
    }
  }

  // Max Long is 9_223_372_036_854_775_807
  private static long[]   thousands = new long[] { 1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000_000L };
  private static int      thousandsLength = thousands.length;
  private static byte[][] numLessThan1000 = new byte[1000][];
  private static byte[][] numLessThan1000NoLeadZeros = new byte[1000][];
  static {
    for (int i = 0; i < 1000; i++) {
      numLessThan1000[i] = String.format("%03d", i).getBytes();
      numLessThan1000NoLeadZeros[i] = Integer.toString(i).getBytes();
    }
  }
  public void writeLong(long n) {
    ensureCapacity(20);
    if (n < 0) {
      bytes[offset++] = '-';
      n = -n;
    }
    int i;
    for (i = 0; i < thousandsLength && n >= thousands[i]; i++);
    boolean first = true;
    for (; i >= 0; i--) {
      long thousand = i == 0 ? 1 : thousands[i - 1];
      int  num      = (int) (n / thousand);
      n = i == 0 ? 0 : n % thousand;
      if (first) {
        byte[] digits = numLessThan1000NoLeadZeros[num];
        for (int j = 0; j < digits.length; j++) {
          bytes[offset++] = digits[j];
        }
        first = false;
      }
      else {
        byte[] digits = numLessThan1000[num];
        bytes[offset++] = digits[0];
        bytes[offset++] = digits[1];
        bytes[offset++] = digits[2];
      }
    }
  }

  public void writeInt(int n) {
    writeLong(n);
  }

  public void writeDecimal(BigDecimal n) {
    if (n == null) {
      writeNull();
      return;
    }
    writeString(((BigDecimal) n).toPlainString(), false);
  }

  public void writeDouble(double n) {
    writeString(Double.toString(n), false);
  }

  public void writeNull() {
    ensureCapacity(4);
    bytes[offset++] = 'n';
    bytes[offset++] = 'u';
    bytes[offset++] = 'l';
    bytes[offset++] = 'l';
  }
}
