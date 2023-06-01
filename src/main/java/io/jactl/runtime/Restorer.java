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

import io.jactl.JactlContext;
import io.jactl.JactlType;
import io.jactl.Pair;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static io.jactl.JactlType.ARRAY;
import static io.jactl.JactlType.INSTANCE;
import static io.jactl.runtime.Checkpointer.NULL_TYPE;

public class Restorer {
  private byte[]        buf;
  private int           idx;
  private JactlContext  context;

  private int           objTableOffset;
  private Object[]      restoredObjects;

  private CircularBuffer<Pair<Integer,Object>> toBeProcessed = new CircularBuffer<>(127, true);

  private static Restorer get(JactlContext context, byte[] buf) {
    return new Restorer().init(context, buf);
  }

  private Restorer init(JactlContext context, byte[] buf) {
    this.idx = 0;
    this.context = context;
    this.buf = buf;
    expectCint(Checkpointer.VERSION, "Bad checkpointer version");
    int numObjects = _readInt(idx);
    idx += 4;
    objTableOffset = _readInt(idx);
    idx += 4;
    restoredObjects = new Object[numObjects];
    return this;
  }

  public static Object restore(JactlContext context, byte[] buf) {
    Restorer restorer = get(context, buf);
    return restorer.restore();
  }

  private Object restore() {
    Object result = readObject();
    Pair<Integer,Object> objectPair;
    while ((objectPair = toBeProcessed.remove()) != null) {
      restoreObject(objectPair.first, objectPair.second);
    }
    restoredObjects = null;
    return result;
  }

  public JactlType.TypeEnum readTypeEnum() {
    int typeOrd = readCint();
    return JactlType.TypeEnum.values()[typeOrd];
  }

  public JactlType readType() {
    int typeOrd = readCint();
    if (typeOrd == JactlType.TypeEnum.NULL_TYPE.ordinal()) {
      return null;
    }
    var type = JactlType.valueOf(JactlType.TypeEnum.values()[typeOrd]);
    if (type.is(ARRAY)) {
      return JactlType.arrayOf(readType());
    }
    else if (type.is(INSTANCE)) {
      return JactlType.createInstanceType(getJactlClass((String)readObject()));
    }
    return type;
  }

  public void skipType() {
    int typeOrd = readCint();
    if (typeOrd == JactlType.TypeEnum.NULL_TYPE.ordinal()) {
      return;
    }
    var type = JactlType.TypeEnum.values()[typeOrd];
    if (type == JactlType.TypeEnum.ARRAY) {
      skipType();
    }
    else if (type == JactlType.TypeEnum.INSTANCE) {
      idx++;      // Skip ANY
      readCint(); // Skip objId
    }
  }

  public boolean readBoolean() {
    return buf[idx++] != 0;
  }

  public void expectTypeEnum(JactlType.TypeEnum type) {
    int value = buf[idx++];
    if (value != type.ordinal()) {
      throw new IllegalStateException("At offset " + (idx - 1) + ": expected " + type.ordinal() + " but got " + value);
    }
  }

  public void expectCint(int expected, String msg) {
    int errIdx = idx;
    int value = readCint();
    if (value != expected) {
      throw new IllegalStateException("At offset " + errIdx + ": " + msg + ": expected " + expected + " but got " + value);
    }
  }

  public int readCint() {
    int num = 0;
    int i = 0;
    do {
      num += (buf[idx] & 0x7f) << (7 * i++);
    } while ((buf[idx++] & 0x80) != 0);
    return num;
  }

  public long readClong() {
    long num = 0;
    int i = 0;
    do {
      num += (long)(buf[idx] & 0x7f) << (7 * i++);
    } while ((buf[idx++] & 0x80) != 0);
    return num;
  }

  public double readDouble() {
    return Double.longBitsToDouble(_readLong());
  }

  public BigDecimal readDecimal() {
    if (readTypeEnum() == JactlType.TypeEnum.NULL_TYPE) {
      return null;
    }
    String val = readString();
    return new BigDecimal(val);
  }

  public BigDecimal readDecimalObj() {
    String val = readString();
    return new BigDecimal(val);
  }

  private int _readInt(int i) {
    int num = buf[i++] & 0xff;
    num += (buf[i++] & 0xff) << 8;
    num += (buf[i++] & 0xff) << 16;
    num += (buf[i++] & 0xff) << 24;
    return num;
  }

  private long _readLong() {
    long num = buf[idx++] & 0xff;
    num += (buf[idx++] & 0xff) << 8;
    num += (buf[idx++] & 0xff) << 16;
    num += (long) (buf[idx++] & 0xff) << 24;
    num += (long) (buf[idx++] & 0xff) << 32;
    num += (long) (buf[idx++] & 0xff) << 40;
    num += (long) (buf[idx++] & 0xff) << 48;
    num += (long) (buf[idx++] & 0xff) << 56;
    return num;
  }

  private StringBuilder readStringBuilder() {
    return new StringBuilder(readString());
  }

  private String readString() {
    int length = readCint();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append((char)readCint());
    }
    return sb.toString();
  }

  public Object readObject() {
    int ordinal = buf[idx++];
    if (ordinal == NULL_TYPE) {
      return null;
    }

    // For primitive types we create the object now since they have no further fields that
    // need to be restored.
    switch (JactlType.TypeEnum.values()[ordinal]) {
      case BOOLEAN:      return buf[idx++] == 0 ? false : true;
      case INT:          return readCint();
      case LONG:         return readClong();
      case DOUBLE:       return readDouble();
      case DECIMAL:      return readDecimalObj();
      default:           throw new IllegalStateException("Unexpected type " + ordinal);
      case ANY: // Fall through
    }

    int objId = readCint();
    Object restoredObject = restoredObjects[objId];
    if (restoredObject != null) {
      return restoredObject;
    }
    int oldIdx = idx;
    try {
      // Get offset to object
      idx = _readInt(objTableOffset + objId * 4);
      int objOffset = idx;
      Function<Object,Object> add = obj -> { toBeProcessed.add(Pair.create(objOffset, obj)); return obj; };
      ordinal = buf[idx++];
      Object result;

      // For non-primitives we create but don't populate the object and return it and add it to
      // list of objects that need to be restored later. This allows us to cater for circular
      // references.
      switch (JactlType.TypeEnum.values()[ordinal]) {
        case STRING:         result = readString();                                    break;
        case STRING_BUILDER: result = readStringBuilder();                             break;
        case MAP:            result = add.apply(new LinkedHashMap<>());                break;
        case LIST:           result = add.apply(new ArrayList<>());                    break;
        case INSTANCE:       result = add.apply(createInstance());                     break;
        case FUNCTION:       result = add.apply(JactlMethodHandle.create(readCint())); break;
        case ARRAY:          result = add.apply(createArray());                        break;
        case HEAPLOCAL:      result = add.apply(new HeapLocal());                      break;
        case ITERATOR:       result = add.apply(JactlIterator.create(readCint()));     break;
        case CONTINUATION:   result = add.apply(new Continuation());                   break;
        case MATCHER:        result = add.apply(new RegexMatcher());                   break;
        case BUILTIN:        result = add.apply(createBuiltinInstance());              break;
        case NUMBER:
        case CLASS:
        case ANY:
        case UNKNOWN:
        default:
          throw new IllegalStateException("Unexpected type in readObject: " + ordinal);
      }
      restoredObjects[objId] = result;
      return result;
    }
    finally {
      idx = oldIdx;
    }
  }

  private void restoreObject(int objOffset, Object obj) {
    int oldIdx = idx;
    try {
      idx = objOffset;

      if (obj instanceof Checkpointable) {
        ((Checkpointable)obj)._$j$restore(this);
      }
      else {
        int ordinal = buf[idx++];
        switch (JactlType.TypeEnum.values()[ordinal]) {
          case MAP:   restoreMap((Map)obj);     break;
          case LIST:  restoreList((List)obj);   break;
          case ARRAY: restoreArray(obj);        break;
          default:    throw new IllegalStateException("Unexpected type in readObject: " + ordinal);
        }
      }
    }
    finally {
      idx = oldIdx;
    }
  }

  void restoreMap(Map map) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      map.put((String)readObject(), readObject());
    }
  }

  private void restoreList(List list) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      list.add(readObject());
    }
  }

  private void readBooleanArr(boolean[] arr) {
    int size = readCint();
    int b = 0;
    for (int i = 0; i < size; i++) {
      if ((i & 7) == 0) {
        b = buf[idx++];
      }
      arr[i] = (b & 0x1) != 0;
      b >>>= 1;
    }
  }

  private void readIntArr(int[] arr) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      arr[i] = readCint();
    }
  }

  private void readLongArr(long[] arr) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      arr[i] = readClong();
    }
  }

  private void readDoubleArr(double[] arr) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      arr[i] = readDouble();
    }
  }

  private void readObjectArr(Object[] arr) {
    int size = readCint();
    for (int i = 0; i < size; i++) {
      arr[i] = readObject();
    }
  }

  public Object createArray() {
    int numDimensions = buf[idx++];
    int[] dimensions = new int[numDimensions];
    var type = readType();
    int   size    = readCint();
    dimensions[0] = size;
    switch (type.getType()) {
      case BOOLEAN:    return Array.newInstance(boolean.class, dimensions);
      case INT:        return Array.newInstance(int.class, dimensions);
      case LONG:       return Array.newInstance(long.class, dimensions);
      case DOUBLE:     return Array.newInstance(double.class, dimensions);
      case DECIMAL:    return Array.newInstance(BigDecimal.class, dimensions);
      case STRING:     return Array.newInstance(String.class, dimensions);
      case LIST:       return Array.newInstance(List.class, dimensions);
      case MAP:        return Array.newInstance(Map.class, dimensions);
      case INSTANCE:   return Array.newInstance(getJactlClass(type.getInternalName()), dimensions);
      default:         return Array.newInstance(Object.class, dimensions);
    }
  }

  private void restoreArray(Object arr) {
    int numDimensions = buf[idx++];
    var type = readType();
    if (numDimensions == 1) {
      switch (type.getType()) {
        case BOOLEAN:    readBooleanArr((boolean[])arr);     break;
        case INT:        readIntArr((int[])arr);             break;
        case LONG:       readLongArr((long[])arr);           break;
        case DOUBLE:     readDoubleArr((double[])arr);       break;
        default:         readObjectArr((Object[])arr);       break;
      }
    }
    else {
      int size = readCint();
      for (int i = 0; i < size; i++) {
        Array.set(arr, i, readObject());
      }
    }
  }

  public Object createBuiltinInstance() {
    Class clss = BuiltinFunctions.getClass(readCint());
    return newInstance(clss);
  }

  public JactlObject createInstance() {
    String internalClassName = (String)readObject();
    Class  clss              = getJactlClass(internalClassName);
    if (JactlObject.class.isAssignableFrom(clss)) {
      return (JactlObject)newInstance(clss);
    }
    throw new IllegalStateException("Checkpointed object of class " + internalClassName + " is not a JactlObject");
  }

  private Object newInstance(Class clss) {
    try {
      return clss.getConstructor().newInstance();
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassCastException e) {
      throw new IllegalStateException("Error trying to construct JactlObject of type " + clss.getName(), e);
    }
  }

  public Class getJactlClass(String internalName) {
    Class clss = context.getClass(internalName);
    if (clss == null) {
      throw new IllegalStateException("Unknown class: " + internalName);
    }
    return clss;
  }
}
