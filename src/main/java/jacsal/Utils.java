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

public class Utils {

  static boolean toBoolean(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean) {
      return (boolean)value;
    }
    if (value instanceof Integer) {
      return (int)value != 0;
    }
    if (value instanceof Long) {
      return (long)value != 0;
    }
    if (value instanceof Double) {
      return (double)value != 0;
    }
    if (value instanceof BigDecimal) {
      return !((BigDecimal)value).stripTrailingZeros().equals(BigDecimal.ZERO);
    }
    return true;
  }

  static int toInt(Object value) {
    if (value instanceof Boolean) {
      return (boolean)value ? 1 : 0;
    }
    if (value instanceof Integer) {
      return (int)value;
    }
    if (value instanceof Long) {
      return (int)(long)value;
    }
    if (value instanceof Double) {
      return ((Double)value).intValue();
    }
    if (value instanceof BigDecimal) {
      return ((BigDecimal)value).intValue();
    }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to int (value=" + value + ")");
  }

  static long toLong(Object value) {
    if (value instanceof Boolean) {
      return (boolean)value ? 1 : 0;
    }
    if (value instanceof Integer) {
      return (int)value;
    }
    if (value instanceof Long) {
      return (long)value;
    }
    if (value instanceof Double) {
      return ((Double)value).longValue();
    }
    if (value instanceof BigDecimal) {
      return ((BigDecimal)value).longValue();
    }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to long (value=" + value + ")");
  }

  static double toDouble(Object value) {
    if (value instanceof Boolean) {
      return (boolean)value ? 1 : 0;
    }
    if (value instanceof Integer) {
      return (int)value;
    }
    if (value instanceof Long) {
      return (long)value;
    }
    if (value instanceof Double) {
      return (double)value;
    }
    if (value instanceof BigDecimal) {
      return ((BigDecimal)value).doubleValue();
    }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to double (value=" + value + ")");
  }

  static BigDecimal toDecimal(Object value) {
    if (value instanceof Boolean) {
      return new BigDecimal((boolean)value ? 1 : 0);
    }
    if (value instanceof Integer) {
      return new BigDecimal((int)value);
    }
    if (value instanceof Long) {
      return new BigDecimal((long)value);
    }
    if (value instanceof Double) {
      return BigDecimal.valueOf((double)value);
    }
    if (value instanceof BigDecimal) {
      return (BigDecimal)value;
    }
    throw new IllegalStateException("Trying to convert type " + value.getClass().getName() + " to decimal (value=" + value + ")");
  }

}
