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

import io.jactl.JactlType;
import io.jactl.Utils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class that implements the reduce() method that iterates over a collection
 * each time passing to a closure the last result and the current element.
 * The result of the closure is then passed to the next invocation.
 * At the end the result is the last result from the call to closure on the
 * last element.
 * <p>
 * This class is also used for implementing min(), max(), sum(), avg(), join(),
 * and groupBy(), since the bulk of the async handling code is the same.
 * </p>
 */
public class Reducer implements Checkpointable {
  private static int VERSION = 1;
  JactlIterator     iter;
  String            source;
  int               offset;
  Object            value;
  Type              type;
  JactlMethodHandle closure;
  int               counter = 0;
  Object            elem = null;
  String            joinStr = null;

  enum Type {
    REDUCE,
    MIN,
    MAX,
    AVG,
    SUM,
    JOIN,
    GROUP_BY,
    TRANSPOSE
  }

  @Override public void _$j$checkpoint(Checkpointer checkpointer) {
    checkpointer.writeTypeEnum(JactlType.TypeEnum.BUILTIN);
    checkpointer.writeCint(BuiltinFunctions.getClassId(Reducer.class));
    checkpointer.writeCint(VERSION);
    checkpointer.writeObject(iter);
    checkpointer.writeObject(source);
    checkpointer.writeCint(offset);
    checkpointer.writeObject(value);
    checkpointer.writeCint(type.ordinal());
    checkpointer.writeObject(closure);
    checkpointer.writeCint(counter);
    checkpointer.writeObject(elem);
    checkpointer.writeObject(joinStr);
  }

  @Override
  public void _$j$restore(Restorer restorer) {
    restorer.expectTypeEnum(JactlType.TypeEnum.BUILTIN);
    restorer.expectCint(BuiltinFunctions.getClassId(Reducer.class), "Bad class id");
    restorer.expectCint(VERSION, "Bad version");
    iter    = (JactlIterator)restorer.readObject();
    source  = (String)restorer.readObject();
    offset  = restorer.readCint();
    value   = restorer.readObject();
    type    = Type.values()[restorer.readCint()];
    closure = (JactlMethodHandle)restorer.readObject();
    counter = restorer.readCint();
    elem    = restorer.readObject();
    joinStr = (String)restorer.readObject();
  }

  public Reducer() {}

  Reducer(Type type, JactlIterator iter, String source, int offset, Object initialValue, JactlMethodHandle closure) {
    this.type    = type;
    this.iter    = iter;
    this.source  = source;
    this.offset  = offset;
    this.value   = initialValue;
    this.closure = closure;
    if (type == Type.JOIN) {
      this.value = "";
      this.joinStr = (String)initialValue;
    }
  }

  public static JactlMethodHandle reduce$cHandle = RuntimeUtils.lookupMethod(Reducer.class, "reduce$c", Object.class, Continuation.class);
  public static Object reduce$c(Continuation c) {
    return ((Reducer)c.localObjects[0]).reduce(c);
  }

  public Object reduce(Continuation c) {
    int methodLocation = c == null ? 0 : c.methodLocation;
    try {
      boolean   hasNext   = true;
      Object[]  nextValue = null;   // for MIN/MAX/GROUP_BY
      // Implement as a simple state machine since iter.hasNext() and iter.next() can both throw a Continuation.
      // iter.hasNext() can throw if we have chained iterators and hasNext() needs to get the next value of the
      // previous iterator in the chain to see if it has a value.
      // We track our state using the methodLocation that we pass to our own Continuation when/if we throw.
      // Even states are the synchronous behaviour, and the odd states are for handling the async case if the
      // synchronous state throws and is later continued.
      while (true) {
        switch (methodLocation) {
          case 0:                       // Initial state
            hasNext = iter.hasNext();
            methodLocation = 2;         // hasNext() returned synchronously so jump straight to state 2
            break;
          case 1:                       // Continuing after hasNext() threw Continuation last time
            hasNext = (boolean) c.getResult();
            methodLocation = 2;
            break;
          case 2:                      // Have a value for "hasNext"
            if (!hasNext) {            // EXIT: exit loop when underlying iterator has no more elements
              return result();
            }
            elem = iter.next();
            methodLocation = 4;        // iter.next() returned synchronously so jump to state 4
            break;
          case 3:                      // Continuing after iter.next() threw Continuation previous time
            elem = c.getResult();
            methodLocation = 4;
            break;
          case 4:                      // Have result of iter.next()
            elem = RuntimeUtils.mapEntryToList(elem);
            switch (type) {
              case REDUCE:
                value = closure.invoke(null, source, offset, new Object[]{ Arrays.asList(value, elem) });
                break;
              case AVG:
                counter++;    // Fall through
              case SUM:
                value = addNumbers(value, elem, source, offset);
                break;
              case MIN:
              case MAX:
              case GROUP_BY:
                nextValue = closure == null ? new Object[] { elem, elem }
                                            : new Object[] { closure.invoke(null, source, offset, new Object[]{elem}), elem };
                break;
              case JOIN:
                String elemStr = RuntimeUtils.toString(elem);
                value = value.equals("") ? elemStr
                                         : joinStr == null ? ((String)value).concat(elemStr)
                                                           : ((String)value).concat(joinStr).concat(elemStr);
                break;
              case TRANSPOSE:
                List<List> valueList = (List)value;
                int elemListSize = sizeOf(elem);
                // grow if needed
                for (int i = valueList.size(); i < elemListSize; i++) {
                  // New list has to have nulls for all earlier entries
                  List initialNulls = valueList.size() == 0 ? Collections.EMPTY_LIST
                                                            : IntStream.range(0,valueList.get(0).size()).mapToObj(j -> null).collect(Collectors.toList());
                  valueList.add(new ArrayList<>(initialNulls));
                }
                // add elems to each list
                for (int i = 0; i < valueList.size(); i++) {
                  valueList.get(i).add(listGet(elem, i));
                }
                break;
            }
            methodLocation = 6;
            break;
          case 5:
            if (type == Type.MIN || type == Type.MAX || type == Type.GROUP_BY) {
              nextValue = new Object[]{c.getResult(), elem};
            }
            else {
              value = c.getResult();
            }
            methodLocation = 6;
            break;
          case 6:
            if (type == Type.MIN || type == Type.MAX) {
              value = minMax(nextValue, value);
            }
            else if (type == Type.GROUP_BY) {
              if (!(nextValue[0] instanceof String)) {
                throw new RuntimeError("groupBy() closure must return a String value (not '" + RuntimeUtils.className(nextValue[0]) + "')", source, offset);
              }
              String key    = (String) nextValue[0];
              Map    map    = (Map)value;
              List   values = (List)map.get(key);
              if (values == null) {
                values = new ArrayList();
                map.put(key, values);
              }
              values.add(nextValue[1]);   // add elem to end of list for the given key
            }
            methodLocation = 0;
            break;
          default:
            throw new IllegalStateException("Internal error: unexpected state " + methodLocation);
        }
      }
    }
    catch (Continuation cont) {
      throw new Continuation(cont, reduce$cHandle,methodLocation + 1, null, new Object[]{ this });
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }

  private Object minMax(Object[] nextValue, Object value) {
    if (value == null) {
      return nextValue;
    }
    int compare = RuntimeUtils.compareTo(((Object[])value)[0], nextValue[0], source, offset);
    return compare < 0 ? (type == Type.MAX ? nextValue : value)
                       : (type == Type.MAX ? value : nextValue);
  }

  public static Object addNumbers(Object valueObj, Object elemObj, String source, int offset) {
    if (!(elemObj instanceof Number)) {
      throw new RuntimeError("Non-numeric element in list (type is " + RuntimeUtils.className(elemObj) + ")", source, offset);
    }
    if (elemObj instanceof Byte) {
      elemObj = ((int)(byte)elemObj) & 0xff;
    }
    if (valueObj instanceof BigDecimal || elemObj instanceof BigDecimal) {
      BigDecimal value = valueObj instanceof BigDecimal ? (BigDecimal)valueObj : RuntimeUtils.toBigDecimal(valueObj);
      BigDecimal elem  = elemObj instanceof BigDecimal ? (BigDecimal)elemObj : RuntimeUtils.toBigDecimal(elemObj);
      return value.add(elem);
    }
    if (valueObj instanceof Double || elemObj instanceof Double) {
      double value = ((Number)valueObj).doubleValue();
      double elem  = ((Number)elemObj).doubleValue();
      return value + elem;
    }
    if (valueObj instanceof Long || elemObj instanceof Long) {
      return ((Number)valueObj).longValue() + ((Number)elemObj).longValue();
    }
    return ((Number)valueObj).intValue() + ((Number)elemObj).intValue();
  }

  private Object result() {
    switch (type) {
      case JOIN:              return (String)value;
      case MIN: case MAX:     return value == null ? null : ((Object[])value)[1];
      case AVG:
        if (counter == 0) {
          throw new RuntimeError("Empty list for avg() function", source, offset);
        }
        if (value instanceof Double)             { value = BigDecimal.valueOf((double)value); }
        else if (!(value instanceof BigDecimal)) { value = BigDecimal.valueOf(((Number)value).longValue()); }
        return RuntimeUtils.decimalDivide((BigDecimal)value, BigDecimal.valueOf(counter), Utils.DEFAULT_MIN_SCALE, source, offset);
      default:
        return value;
    }
  }

  private int sizeOf(Object object) {
    if (object instanceof List) {
      return ((List)object).size();
    }
    if (object.getClass().isArray()) {
      return Array.getLength(object);
    }
    throw new RuntimeError("Entries in source list must all be lists for transpose()", source, offset);
  }

  private Object listGet(Object object, int idx) {
    if (object instanceof List) {
      return idx < ((List)object).size() ? ((List)object).get(idx) : null;
    }
    if (object.getClass().isArray()) {
      return Array.get(object, idx);
    }
    throw new RuntimeError("Entries in source list must all be lists for transpose()", source, offset);
  }
}
