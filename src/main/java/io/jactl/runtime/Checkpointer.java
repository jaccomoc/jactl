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

import java.math.BigDecimal;
import java.util.*;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.TypeEnum.STRING_BUILDER;

/**
 * Class for saving checkpoint state. Saves into a buffer with layout of:
 * <pre>
 *            byte  version
 *            int32 objTableOffset
 *            objects...
 * objTable:  int32 obj0offset
 *            int32 obj1offset
 *            ...
 * </pre>
 * We generally store numbers in a compressed form where we only use as many
 * bytes as need with the top bit of a byte determining whether the number
 * extends into the next byte. We call these number types intc and longc.
 */
public class Checkpointer {
  private static ThreadLocal<Checkpointer> checkpointerThreadLocal = ThreadLocal.withInitial(Checkpointer::new);
  static int  VERSION = 1;
  static byte NULL_TYPE = -1;

  private static int  MAX_CACHE_SIZE      = 1024 * 16;
  private static int  MAX_CHECKPOINT_SIZE = 1024 * 1024 * 128;

  private byte[]                            buf;
  private int                               idx;
  private String                            source;
  private int                               offset;
  private int                               objId;
  private Object[]                          objects;
  private IdentityHashMap<Object,Integer>   objectIds;
  private int[]                             offsets;

  public static Checkpointer get(String source, int offset) {
    return checkpointerThreadLocal.get().init(source, offset);
    //return new Checkpointer().init(source, offset);
  }

  private Checkpointer() {}

  private Checkpointer init(String source, int offset) {
    this.source  = source;
    this.offset  = offset;
    this.idx     = 0;
    this.objId   = 0;
    if (this.objects == null) {
      this.objects = new Object[MAX_CACHE_SIZE / 4];
    }
    if (this.objectIds == null) {
      this.objectIds = new IdentityHashMap<>(MAX_CACHE_SIZE / 4);
    }
    if (this.offsets == null) {
      this.offsets = new int[MAX_CACHE_SIZE / 4];
    }
    if (this.buf == null) {
      this.buf = new byte[MAX_CACHE_SIZE];
    }
    this._writeCint(VERSION);
    this._writeInt(0);         // number of objects encoded (i.e. size of object table)
    this._writeInt(0);         // offset to object table which we fill in at the end
    return this;
  }

  public void reset() {
    if (buf.length > MAX_CACHE_SIZE) {
      buf = null;
    }
    if (offsets.length > MAX_CACHE_SIZE / 4) {
      offsets = null;
    }
    if (objects.length > MAX_CACHE_SIZE / 4) {
      objects = null;
    }
    objectIds = null;
    idx       = 0;
  }

  public byte[] getBuffer() {
    return buf;
  }

  public int getLength() {
    return idx;
  }

  public void checkpoint(Object obj) {
    writeObject(obj);
    for (int i = 0; i < objId; i++) {
      checkpointObj(i, objects[i]);
    }
    // Add object table
    int objTableOffset = idx;
    idx = 1;
    _writeInt(objId);
    _writeInt(objTableOffset);
    idx = objTableOffset;
    for (int i = 0; i < objId; i++) {
      writeInt(offsets[i]);
    }
  }

  private void checkpointObj(int id, Object obj) {
    if (id >= offsets.length) {
      int[] newOffsets = new int[offsets.length * 2];
      System.arraycopy(offsets, 0, newOffsets, 0, offsets.length);
      offsets = newOffsets;
    }
    offsets[id] = idx;
    if (obj instanceof Checkpointable) {
      ((Checkpointable)obj)._$j$checkpoint(this);
    }
    else if (obj instanceof Map)            { writeMap((Map)obj);       }
    else if (obj instanceof List)           { writeList((List)obj);     }
    else if (obj instanceof String)         { writeString((String)obj); }
    else if (obj.getClass().isArray())      { writeArray(obj);          }
    else if (obj instanceof StringBuilder)  { writeStringBuilder((StringBuilder)obj); }
    else {
      throw new RuntimeError("Cannot checkpoint object of type " + RuntimeUtils.className(obj), source, offset);
    }
  }

  private void ensureCapacity(int n) {
    if (idx + n < buf.length) {
      return;
    }
    int length = buf.length;
    do {
      length <<= 1;
      if (length > MAX_CHECKPOINT_SIZE) {
        throw new IllegalStateException("Checkpoint size too large (greater than " + MAX_CHECKPOINT_SIZE + ")");
      }
    } while (idx + n >= length);
    byte[] b = new byte[length];
    System.arraycopy(buf, 0, b, 0, idx);
    buf = b;
  }

  public void writeObject(Object value) {
    if (value == null) {
      // Use ordinal of -1 to signify null
      writeByte(NULL_TYPE);
    }
    else
    if (value instanceof Number && !(value instanceof HeapLocal)) {
      if      (value instanceof Integer)    { writeIntObj((int)value);                        }
      else if (value instanceof Long)       { writeLongObj((long)value);                      }
      else if (value instanceof BigDecimal) { writeDecimalObj((BigDecimal)value);             }
      else /*  value instanceof Double */   { writeDoubleObj(((Number)value).doubleValue());  }
    }
    else if (value instanceof Boolean)      { writeBooleanObj((boolean)value);                }
    else {
      writeType(ANY);
      writeCint(getId(value));
    }
  }

  private Integer getId(Object value) {
    Integer id = objectIds.get(value);
    if (id == null) {
      id = objId++;
      objectIds.put(value, id);
      if (id >= objects.length) {
        Object[] newObjects = new Object[objects.length * 2];
        System.arraycopy(objects, 0, newObjects, 0, objects.length);
        objects = newObjects;
      }
      objects[id] = value;
    }
    return id;
  }

  void writeMap(Map<String,Object> map) {
    int size = map.size();
    ensureCapacity(1 + 5);
    buf[idx++] = (byte)MAP.getType().ordinal();
    _writeCint(size);
    for (var iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      writeObject(entry.getKey());
      writeObject(entry.getValue());
    }
  }

  private void writeList(List list) {
    writeType(LIST);
    writeCint(list.size());
    for (int i = 0; i < list.size(); i++) {
      writeObject(list.get(i));
    }
  }

  private void writeArray(Object value) {
    if (value instanceof boolean[])         { writeBooleanArr((boolean[])value); }
    else if (value instanceof int[])        { writeIntArr((int[])value);         }
    else if (value instanceof long[])       { writeLongArr((long[])value);       }
    else if (value instanceof double[])     { writeDoubleArr((double[])value);   }
    else /*  value instanceof Object[] */   { writeObjectArr((Object[])value);   }
  }

  private void writeBooleanArr(boolean[] arr) {
    ensureCapacity(1 + 3 + 5 + arr.length/8 + 1);
    buf[idx++] = (byte)ARRAY.getType().ordinal();
    buf[idx++] = 1;
    buf[idx++] = (byte)BOOLEAN.getType().ordinal();
    _writeCint(arr.length);
    for (int i = 0, mask = 1, bits = 0; i < arr.length; i++) {
      bits |= (arr[i] ? mask : 0);
      mask <<= 1;
      if ((i & 0x7) == 7 || i == arr.length - 1) {
        buf[idx++] = (byte)bits;
        bits = 0;
        mask = 1;
      }
    }
  }

  private void writeIntArr(int[] arr) {
    ensureCapacity(3 + 5 + arr.length * 5);
    buf[idx++] = (byte)ARRAY.getType().ordinal();
    buf[idx++] = 1;
    buf[idx++] = (byte)INT.getType().ordinal();
    _writeCint(arr.length);
    for (int i = 0; i < arr.length; i++) {
      _writeCint(arr[i]);
    }
  }

  private void writeLongArr(long[] arr) {
    ensureCapacity(3 + 5 + arr.length * 10);
    buf[idx++] = (byte)ARRAY.getType().ordinal();
    buf[idx++] = 1;
    buf[idx++] = (byte)LONG.getType().ordinal();
    _writeCint(arr.length);
    for (int i = 0; i < arr.length; i++) {
      long v = arr[i];
      _writeLongc(v);
    }
  }

  private void writeDoubleArr(double[] arr) {
    ensureCapacity(1 + 3 + 5 + arr.length * 8);
    buf[idx++] = (byte)ARRAY.getType().ordinal();
    buf[idx++] = 1;
    buf[idx++] = (byte)DOUBLE.getType().ordinal();
    _writeCint(arr.length);
    for (int i = 0; i < arr.length; i++) {
      _writeLong(Double.doubleToRawLongBits(arr[i]));
    }
  }

  private void writeObjectArr(Object[] arr) {
    int dimensions = 1;
    Class componentType;
    for (componentType = arr.getClass().getComponentType(); componentType.isArray(); componentType = componentType.getComponentType()) {
      dimensions++;
    }
    ensureCapacity(2);
    buf[idx++] = (byte)ARRAY.getType().ordinal();
    buf[idx++] = (byte)dimensions;
    writeType(JactlType.typeFromClass(componentType));
    writeCint(arr.length);
    for (int i = 0; i < arr.length; i++) {
      writeObject(arr[i]);
    }
  }

  public void writeIntObj(Integer i) {
    ensureCapacity(6);
    _writeType(INT);
    _writeCint(i);
  }

  public void writeLongObj(Long v) {
    ensureCapacity(6);
    _writeType(LONG);
    _writeLongc(v);
  }

  public void _writeBoolean(boolean b) {
    writeByte(b ? (byte)1 : 0);
  }

  public void writeBoolean(boolean b) {
    writeByte(b ? (byte)1 : (byte)0);
  }

  public void writeBooleanObj(boolean b) {
    ensureCapacity(2);
    _writeType(BOOLEAN);
    buf[idx++] = b ? (byte)1 : (byte)0;
  }

  public void writeDouble(double d) {
    ensureCapacity(8);
    _writeLong(Double.doubleToRawLongBits(d));
  }

  public void writeDoubleObj(double d) {
    ensureCapacity(9);
    _writeType(DOUBLE);
    _writeLong(Double.doubleToRawLongBits(d));
  }

  public void writeDecimal(BigDecimal v) {
    if (v == null) {
      writeTypeEnum(TypeEnum.NULL_TYPE);
    }
    else {
      String str = v.toString();
      ensureCapacity(1 + 5 + str.length());
      buf[idx++] = (byte)TypeEnum.STRING.ordinal();
      _writeCint(str.length());
      for (int i = 0; i < str.length(); i++) {
        _writeCint(str.charAt(i));
      }
    }
  }

  public void writeDecimalObj(BigDecimal v) {
    if (v == null) {
      writeTypeEnum(TypeEnum.NULL_TYPE);
    }
    else {
      writeType(DECIMAL);
      _writeString(v.toString());
    }
  }

  public void writeInt(int num) {
    ensureCapacity(4);
    _writeInt(num);
  }

  private void _writeInt(int num) {
    buf[idx++] = (byte)(num & 0xff); num >>>= 8;
    buf[idx++] = (byte)(num & 0xff); num >>>= 8;
    buf[idx++] = (byte)(num & 0xff); num >>>= 8;
    buf[idx++] = (byte)(num & 0xff); num >>>= 8;
  }

  public void writeCint(int num) {
    ensureCapacity(5);
    _writeCint(num);
  }

  private void _writeCint(int num) {
    // Assumes someone has already ensured capacity
    byte b = (byte)(num & 0x7f);
    if (b == num) {
      buf[idx++] = b;
    }
    else {
      do {
        int mask = num >= 128 || num < 0 ? 0x80 : 0;
        buf[idx++] = (byte) ((num & 0x7f) | mask);
        num >>>= 7;
      } while (num != 0);
    }
  }

  private void _writeLongc(long num) {
    // Assumes someone has already ensured capacity
    do {
      int mask = num >= 128 || num < 0 ? 0x80 : 0;
      buf[idx++] = (byte)((num & 0x7f) | mask);
      num >>>= 7;
    } while (num != 0);
  }

  private void writeStringBuilder(StringBuilder sb) {
    writeByte((byte)STRING_BUILDER.ordinal());
    _writeString(sb.toString());
  }

  private void writeString(String str) {
    ensureCapacity(1 + 5 + str.length() * 2);
    buf[idx++] = ((byte)JactlType.TypeEnum.STRING.ordinal());
    _writeCint(str.length());
    for (int i = 0; i < str.length(); i++) {
      _writeCint(str.charAt(i));
    }
  }

  // Only for internal use. Should use writeObject() when checkpointing string fields.
  private void _writeString(String str) {
    ensureCapacity(5 + str.length() * 2);
    _writeCint(str.length());
    for (int i = 0; i < str.length(); i++) {
      _writeCint(str.charAt(i));
    }
  }

  private void _writeLong(long v) {
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
    buf[idx++] = (byte)(v & 0xff); v >>>= 8;
  }

  public void writeClong(long num) {
    ensureCapacity(10);
    _writeLongc(num);
  }

  public void writeByte(byte b) {
    ensureCapacity(1);
    buf[idx++] = b;
  }

  public void writeTypeEnum(JactlType.TypeEnum type) {
    writeByte((byte)type.ordinal());
  }

  public void writeType(JactlType type) {
    if (type == null) {
      writeTypeEnum(TypeEnum.NULL_TYPE);
      return;
    }
    TypeEnum enumType = type.getType();
    writeTypeEnum(enumType);
    if (enumType == JactlType.TypeEnum.ARRAY) {
      writeType(type.getArrayType());
    }
    else if (enumType == JactlType.TypeEnum.INSTANCE) {
      writeObject(type.getInternalName());
    }
  }

  private void _writeType(JactlType type) {
    buf[idx++] = (byte)type.getType().ordinal();
  }
}
