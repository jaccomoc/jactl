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

import java.math.BigDecimal;
import java.util.regex.Matcher;

/**
 * Object that wraps an actual value.
 * This is used for local vars that live on the heap to provide a layer of indirection
 * to the value of the variable. We pass these handles around and can then mutate the
 * underlying value via the handle so everyone using the variable sees the same value.
 */
public class HeapLocal extends Number {

  Object value;

  public void   setValue(Object value) { this.value = value; }
  public Object getValue()             { return value; }

  @Override public int    intValue()    { return ((Number)value).intValue();    }
  @Override public long   longValue()   { return ((Number)value).longValue();   }
  @Override public float  floatValue()  { return ((Number)value).floatValue();  }
  @Override public double doubleValue() { return ((Number)value).doubleValue(); }

  public BigDecimal decimalValue() {
    if (value instanceof BigDecimal) {
      return (BigDecimal)value;
    }
    return BigDecimal.valueOf(doubleValue());
  }

  public String stringValue() {
    if (value instanceof String) {
      return (String)value;
    }
    if (value instanceof RegexMatch) {
      return ((RegexMatch)value).pattern;
    }
    return RuntimeUtils.toString(value);
  }

  public Matcher matcherValue() { return (Matcher)value; }
}
