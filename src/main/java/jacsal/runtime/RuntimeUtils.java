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

package jacsal.runtime;

import jacsal.TokenType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RuntimeUtils {

  static final char PLUS = '+';
  static final char MINUS = '-';
  static final char STAR = '*';
  static final char SLASH = '/';
  static final char PERCENT = '%';

  public static char getOperatorType(TokenType op) {
    switch (op) {
      case PLUS:    return PLUS;
      case MINUS:   return MINUS;
      case STAR:    return STAR;
      case SLASH:   return SLASH;
      case PERCENT: return PERCENT;
      default: throw new IllegalStateException("Internal error: operator " + op + " not supported");
    }
  }

  public static BigDecimal decimalBinaryOperation(BigDecimal left, BigDecimal right, char operator, int maxScale) {
    BigDecimal result;
    switch (operator) {
      case PLUS:    result = left.add(right);       break;
      case MINUS:   result = left.subtract(right);  break;
      case STAR:    result = left.multiply(right);  break;
      case PERCENT: result = left.remainder(right); break;
      case SLASH:
        result = left.divide(right, maxScale, RoundingMode.HALF_EVEN);
        break;
      default: throw new IllegalStateException("Internal error: operator " + operator + " not supported for decimals");
    }
    result = result.stripTrailingZeros();
    if (result.scale() > maxScale) {
      result = result.setScale(maxScale, RoundingMode.HALF_EVEN);
    }
    return result;
  }

  public static String stringRepeat(String str, int count, String source, int offset) {
    if (count < 0) {
      throw new RuntimeError("String repeat count must be >= 0", source, offset, true);
    }
    return str.repeat(count);
  }
}
