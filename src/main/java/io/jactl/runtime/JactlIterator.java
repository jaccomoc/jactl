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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static io.jactl.JactlType.ITERATOR;

public abstract class JactlIterator<T> implements Iterator<T>, Checkpointable {
  private static int VERSION = 1;

  enum IteratorType {
    OBJECT_ARR,
    INT_ARR,
    LONG_ARR,
    DOUBLE_ARR,
    BOOLEAN_ARR,
    LIST,
    MAP,
    FILTER,
    FLATMAP,
    MAPPER,
    STREAM,
    UNIQUE,
    LIMIT,
    SKIP,
    GROUPED,
    NEGATIVE_LIMIT,
    NUMBER,
    STRING,
    STRING_SPLIT,
  }

  public static JactlIterator create(int ordinal) {
    switch (IteratorType.values()[ordinal]) {
      case OBJECT_ARR:     return new ObjArrIterator();
      case INT_ARR:        return new IntArrIterator();
      case LONG_ARR:       return new LongArrIterator();
      case DOUBLE_ARR:     return new DoubleArrIterator();
      case BOOLEAN_ARR:    return new BooleanArrIterator();
      case LIST:           return new ListIterator();
      case MAP:            return new MapEntryIterator();
      case FILTER:         return new FilterIterator();
      case FLATMAP:        return new FlatMapIterator();
      case MAPPER:         return new MapIterator();
      case STREAM:         return new StreamIterator();
      case UNIQUE:         return new UniqueIterator();
      case LIMIT:          return new LimitIterator();
      case SKIP:           return new SkipIterator();
      case GROUPED:        return new GroupedIterator();
      case NEGATIVE_LIMIT: return new NegativeLimitIterator();
      case NUMBER:         return new NumberIterator();
      case STRING:         return new StringIterator();
      case STRING_SPLIT:   return new StringSplitIterator();
      default:             throw new IllegalStateException("Unexpected iterator type " + ordinal);
    }
  }

  public static Class classFromType(IteratorType type) {
    switch (type) {
      case OBJECT_ARR:     return ObjArrIterator.class;
      case INT_ARR:        return IntArrIterator.class;
      case LONG_ARR:       return LongArrIterator.class;
      case DOUBLE_ARR:     return DoubleArrIterator.class;
      case BOOLEAN_ARR:    return BooleanArrIterator.class;
      case LIST:           return ListIterator.class;
      case MAP:            return MapEntryIterator.class;
      case FILTER:         return FilterIterator.class;
      case FLATMAP:        return FlatMapIterator.class;
      case MAPPER:         return MapIterator.class;
      case STREAM:         return StreamIterator.class;
      case UNIQUE:         return UniqueIterator.class;
      case LIMIT:          return LimitIterator.class;
      case SKIP:           return SkipIterator.class;
      case GROUPED:        return GroupedIterator.class;
      case NEGATIVE_LIMIT: return NegativeLimitIterator.class;
      case NUMBER:         return NumberIterator.class;
      case STRING:         return StringIterator.class;
      case STRING_SPLIT:   return StringSplitIterator.class;
      default:             throw new IllegalStateException("Unexpected iterator type " + type);
    }
  }

  public static JactlIterator<Object> of(Object... objects) {
    ObjArrIterator iter = new ObjArrIterator();
    iter.arr = objects;
    return iter;
  }

  private static class ObjArrIterator extends JactlIterator<Object> {
    int      idx = 0;
    Object[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Object  next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.OBJECT_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.OBJECT_ARR.ordinal(), "Expected OBJECT_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (Object[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static <T> JactlIterator<T> of(List<T> list) {
    ListIterator<T> iter = new ListIterator<T>();
    iter.list = list;
    return iter;
  }

  private static class ListIterator<T> extends JactlIterator<T> {
    int     idx = 0;
    List<T> list;
    @Override public boolean hasNext() { return idx < list.size(); }
    @Override public T       next()    { return list.get(idx++);   }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.LIST.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(list);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.LIST.ordinal(), "Expected LIST");
      restorer.expectCInt(VERSION, "Bad version");
      list = (List<T>)restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static <K,V> JactlIterator<Map.Entry<K,V>> of(Map<K,V> map) {
    return new MapEntryIterator<K,V>().setMap(map);
  }

  private static class MapEntryIterator<K,V> extends JactlIterator<Map.Entry<K,V>> {
    Map<K,V>                 map;
    Iterator<Map.Entry<K,V>> iter;
    int                      count = 0;
    MapEntryIterator<K,V> setMap(Map<K,V> map)     {
      this.map  = map;
      this.iter = map.entrySet().iterator();
      return this;
    }
    @Override public boolean hasNext() {
      if (iter == null) { initIter(); }
      return iter.hasNext();
    }
    @Override public Map.Entry<K,V> next() {
      if (iter == null) { initIter(); }
      count++;
      return iter.next();
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.MAP.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(map);
      checkpointer.writeCInt(count);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.MAP.ordinal(), "Expected MAP");
      restorer.expectCInt(VERSION, "Bad version");
      map = (Map)restorer.readObject();
      count = restorer.readCInt();
      // Can't initialise iterator until map has been fully populated
    }
    void initIter() {
      // After restore our iterator will be null so initialise and advance to
      // point where we were when we were checkpointed.
      iter = map.entrySet().iterator();
      for (int i = 0; i < count; i++) {
        // Get back to where we were
        iter.next();
      }
    }
  }

  public static JactlIterator<Byte> of(byte[] arr) {
    ByteArrIterator iter = new ByteArrIterator();
    iter.arr = arr;
    return iter;
  }

  private static class ByteArrIterator extends JactlIterator<Byte> {
    int    idx = 0;
    byte[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Byte next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.INT_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.INT_ARR.ordinal(), "Expected INT_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (byte[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static JactlIterator<Integer> of(int[] arr) {
    IntArrIterator iter = new IntArrIterator();
    iter.arr = arr;
    return iter;
  }

  private static class IntArrIterator extends JactlIterator<Integer> {
    int   idx = 0;
    int[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Integer next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.INT_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.INT_ARR.ordinal(), "Expected INT_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (int[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static JactlIterator<Long> of(long[] arr) {
    LongArrIterator iter = new LongArrIterator();
    iter.arr = arr;
    return iter;
  }

  private static class LongArrIterator extends JactlIterator<Long> {
    int   idx = 0;
    long[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Long    next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.LONG_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.LONG_ARR.ordinal(), "Expected LONG_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (long[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static JactlIterator<Double> of(double[] arr) {
    DoubleArrIterator iter = new DoubleArrIterator();
    iter.arr = arr;
    return iter;
  }

  private static class DoubleArrIterator extends JactlIterator<Double> {
    int      idx = 0;
    double[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Double  next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.DOUBLE_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.DOUBLE_ARR.ordinal(), "Expected DOUBLE_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (double[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static JactlIterator<Boolean> of(boolean[] arr) {
    BooleanArrIterator iter = new BooleanArrIterator();
    iter.arr = arr;
    return iter;
  }

  private static class BooleanArrIterator extends JactlIterator<Boolean> {
    int       idx = 0;
    boolean[] arr;
    @Override public boolean hasNext() { return idx < arr.length; }
    @Override public Boolean next()    { return arr[idx++];       }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.BOOLEAN_ARR.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(arr);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.BOOLEAN_ARR.ordinal(), "Expected BOOLEAN_ARR");
      restorer.expectCInt(VERSION, "Bad version");
      arr = (boolean[])restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static NumberIterator numberIterator(Number value) {
    NumberIterator iter = new NumberIterator();
    iter.num = value.longValue();
    return iter;
  }

  private static class NumberIterator extends JactlIterator<Integer> {
    long num;
    int  idx = 0;
    @Override public boolean hasNext() { return idx < num; }
    @Override public Integer next()    { return idx++;     }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.NUMBER.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeClong(num);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.NUMBER.ordinal(), "Expected NUMBER");
      restorer.expectCInt(VERSION, "Bad version");
      num   = restorer.readCLong();
      idx = restorer.readCInt();
    }
  }

  public static StringIterator stringIterator(String str) {
    StringIterator iter = new StringIterator();
    iter.str = str;
    return iter;
  }

  private static class StringIterator extends JactlIterator<String> {
    String str;
    int    idx = 0;
    @Override public boolean hasNext() { return idx < str.length(); }
    @Override public String next()     { return Character.toString(str.charAt(idx++)); }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.STRING.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(str);
      checkpointer.writeCInt(idx);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.STRING.ordinal(), "Expected STRING");
      restorer.expectCInt(VERSION, "Bad version");
      str = (String)restorer.readObject();
      idx = restorer.readCInt();
    }
  }

  public static StringSplitIterator stringSplitIterator(String str, String regex, String modifiers, String source, int offset) {
    return new StringSplitIterator().init(str, regex, modifiers, source, offset);
  }

  private static class StringSplitIterator extends JactlIterator<String> {
    String  str;
    String  regex;
    String  modifiers;
    String  source;
    int     offset;
    int     index    = 0;
    boolean last     = false;
    boolean hasNext  = false;
    boolean findNext = true;
    Matcher matcher;

    StringSplitIterator init(String str, String regex, String modifiers, String source, int offset) {
      this.str = str;
      this.regex = regex;
      this.modifiers = modifiers;
      this.source = source;
      this.offset = offset;
      matcher = RegexMatcher.getMatcher(str, regex, modifiers, source, offset);
      return this;
    }
    @Override public boolean hasNext() {
      if (!findNext) {
        return hasNext;
      }
      findNext = false;
      if (!last && matcher.find(index)) {
        return hasNext = true;
      }
      if (!last) {
        last = true;
        return hasNext = true;
      }
      return false;
    }
    @Override public String next() {
      if (hasNext()) {
        findNext = true;
        if (last) {
          return str.substring(index);
        }
        int    nextIndex = matcher.start();
        String result    = str.substring(index, nextIndex);
        index = matcher.end();
        return result;
      }
      throw new IllegalStateException("Internal error: split() - no more matches");
    }
    @Override public void _$j$checkpoint(Checkpointer checkpointer) {
      checkpointer.writeType(ITERATOR);
      checkpointer.writeCInt(IteratorType.STRING_SPLIT.ordinal());
      checkpointer.writeCInt(VERSION);
      checkpointer.writeObject(str);
      checkpointer.writeObject(regex);
      checkpointer.writeObject(modifiers);
      checkpointer.writeObject(source);
      checkpointer.writeCInt(offset);
      checkpointer.writeCInt(index);
      checkpointer._writeBoolean(last);
      checkpointer._writeBoolean(hasNext);
      checkpointer._writeBoolean(findNext);
    }
    @Override public void _$j$restore(Restorer restorer) {
      restorer.expectTypeEnum(JactlType.TypeEnum.ITERATOR);
      restorer.expectCInt(IteratorType.STRING_SPLIT.ordinal(), "Expected STRING_SPLIT");
      restorer.expectCInt(VERSION, "Bad version");
      str       = (String)restorer.readObject();
      regex     = (String)restorer.readObject();
      modifiers = (String)restorer.readObject();
      source    = (String)restorer.readObject();
      offset    = restorer.readCInt();
      index     = restorer.readCInt();
      last      = restorer.readBoolean();
      hasNext   = restorer.readBoolean();
      findNext  = restorer.readBoolean();
      matcher = RegexMatcher.getMatcher(str, regex, modifiers, source, offset);
    }
  }
}
