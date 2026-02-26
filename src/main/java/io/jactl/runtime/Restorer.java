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

import static io.jactl.JactlType.*;
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
    expectCInt(Checkpointer.VERSION, "Bad checkpointer version");
    int numObjects = _readInt(idx);
    idx += 4;
    objTableOffset = _readInt(idx);
    idx += 4;
    restoredObjects = new Object[numObjects];
    return this;
  }

  public JactlContext getContext() {
    return context;
  }

  public static Object restore(JactlContext context, byte[] buf) {
    Restorer restorer = get(context, buf);
    // We checkpoint a two element list (globals, continuation) so restore the globals and return the continuation
    List restored = (List)restorer.restore();
    RuntimeState.setState(context, (Map<String, Object>)restored.get(0), null, null);
    return restored.get(1);
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
    int typeOrd = readCInt();
    return JactlType.TypeEnum.values()[typeOrd];
  }

  public JactlType readType() {
    int typeOrd = readCInt();
    if (typeOrd == JactlType.TypeEnum.NULL_TYPE.ordinal()) {
      return null;
    }
    JactlType type = JactlType.valueOf(JactlType.TypeEnum.values()[typeOrd]);
    if (type.is(ARRAY)) {
      return JactlType.arrayOf(readType());
    }
    else if (type.is(INSTANCE)) {
      return JactlType.createInstanceType(getJactlClass((String)readObject()));
    }
    else if (type.is(CLASS)) {
      return JactlType.createClass(getClassDescriptor((String)readObject()));
    }
    return type;
  }

  public void skipType() {
    int typeOrd = readCInt();
    if (typeOrd == JactlType.TypeEnum.NULL_TYPE.ordinal()) {
      return;
    }
    JactlType.TypeEnum type = JactlType.TypeEnum.values()[typeOrd];
    if (type == JactlType.TypeEnum.ARRAY) {
      skipType();
    }
    else if (type == JactlType.TypeEnum.INSTANCE) {
      idx++;      // Skip ANY
      readCInt(); // Skip objId
    }
  }

  /**
   * Read boolean from buffer
   * @return the boolean value
   */
  public boolean readBoolean() {
    return buf[idx++] != 0;
  }

  public void expectTypeEnum(JactlType.TypeEnum type) {
    int value = buf[idx++];
    if (value != type.ordinal()) {
      throw new IllegalStateException("At offset " + (idx - 1) + ": expected " + type.ordinal() + " but got " + value);
    }
  }

  public void expectCInt(int expected, String msg) {
    int errIdx = idx;
    int value = readCInt();
    if (value != expected) {
      throw new IllegalStateException("At offset " + errIdx + ": " + msg + ": expected " + expected + " but got " + value);
    }
  }
  public static String EXPECT_CINT = "expectCInt";

  /**
   * Read byte from buffer
   * @return the byte
   */
  public byte readByte() {
    return buf[idx++];
  }

  /**
   * Read a compress integer from buffer.
   * We use a more compact representation for integers that are usually low values.
   * For large values this is more expensive but if you know that the values will
   * generally be in the low range then this can save bytes for each integer encoded.
   * @return the integer
   */
  public int readCInt() {
    int num = 0;
    int i = 0;
    do {
      num += (buf[idx] & 0x7f) << (7 * i++);
    } while ((buf[idx++] & 0x80) != 0);
    return num;
  }
  public static String READ_CINT = "readCInt";

  /**
   * Read a compressed long from buffer
   * @return the long
   */
  public long readCLong() {
    long num = 0;
    int i = 0;
    do {
      num += (long)(buf[idx] & 0x7f) << (7 * i++);
    } while ((buf[idx++] & 0x80) != 0);
    return num;
  }
  public static String READ_CLONG = "readCLong";

  /**
   * Read a double from buffer
   * @return the double
   */
  public double readDouble() {
    return Double.longBitsToDouble(readLong());
  }

  /**
   * Read a BigDecimal value from buffer with null support
   * @return the BigDecimal
   */
  public BigDecimal readDecimal() {
    if (readTypeEnum() == JactlType.TypeEnum.NULL_TYPE) {
      return null;
    }
    String val = readString();
    return new BigDecimal(val);
  }

  /**
   * Read a BigDecimal value from the buffer where we know null cannot have been encoded
   * @return the BigDecimal
   */
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

  /**
   * Read long value from buffer
   * @return the long
   */
  public long readLong() {
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

  private StringBuffer readStringBuffer() {
    return new StringBuffer(readString());
  }
  
  private String readString() {
    int length = readCInt();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append((char) readCInt());
    }
    return sb.toString();
  }

  /**
   * Read an object from the buffer
   * @return the object
   */
  public Object readObject() {
    return readObject(false);
  }

  /**
   * Read object will return the actual object (for primitives) or the shell
   * of the object (created but no fields filled in) for non-primitives.
   * This allows us to deal with circular references. We add the object to a
   * list of objects that need to be fully restored at the end.
   * If the restore flag is set we will read *and* restore the object. This
   * is used for Maps where we need the full object for the key so that we
   * can determine the hashCode when adding an object to the map. Note that
   * we don't support circular references for map key objects since hashCode
   * will get a stack overflow in these situations anyway.
   * @param restore  whether to restore the object or not
   * @return
   */
  private Object readObject(boolean restore) {
    int ordinal = buf[idx++];
    if (ordinal == NULL_TYPE) {
      return null;
    }

    // For primitive types we create the object now since they have no further fields that
    // need to be restored.
    switch (JactlType.TypeEnum.values()[ordinal]) {
      case BOOLEAN:      return buf[idx++] == 0 ? false : true;
      case BYTE:         return readByte();
      case INT:          return readCInt();
      case LONG:         return readCLong();
      case DOUBLE:       return readDouble();
      case DECIMAL:      return readDecimalObj();
      default:           throw new IllegalStateException("Unexpected type " + ordinal);
      case ANY: // Fall through
    }

    int objId = readCInt();
    Object restoredObject = restoredObjects[objId];
    if (restoredObject != null) {
      return restoredObject;
    }
    int oldIdx = idx;
    try {
      // Get offset to object
      idx = _readInt(objTableOffset + objId * 4);
      int objOffset = idx;
      // Only add object to list of objects to be processed (restored) if not restoring now
      Function<Object,Object> add = obj -> { if (!restore) { toBeProcessed.add(Pair.create(objOffset, obj)); }; return obj; };
      ordinal = buf[idx++];
      Object result;

      // For non-primitives we create but don't populate the object and return it and add it to
      // list of objects that need to be restored later. This allows us to cater for circular
      // references.
      boolean shouldRestore = restore;
      switch (JactlType.TypeEnum.values()[ordinal]) {
        // Don't restore since these object types are more like primitives and are restored when we read them
        case STRING:         result = readString();            shouldRestore = false;   break;
        case STRING_BUFFER:  result = readStringBuffer();      shouldRestore = false;   break;

        case MAP:            result = add.apply(new LinkedHashMap<>());                 break;
        case LIST:           result = add.apply(new ArrayList<>());                     break;
        case INSTANCE:       result = add.apply(createInstance());                      break;
        case FUNCTION:       result = add.apply(JactlMethodHandle.create(readCInt()));  break;
        case ARRAY:          result = add.apply(createArray());                         break;
        case HEAPLOCAL:      result = add.apply(new HeapLocal());                       break;
        case ITERATOR:       result = add.apply(JactlIterator.create(readCInt()));      break;
        case CONTINUATION:   result = add.apply(new Continuation());                    break;
        case MATCHER:        result = add.apply(new RegexMatcher());                    break;
        case BUILTIN:        result = add.apply(createBuiltinInstance());               break;
        case CLASS:          result = add.apply(createClass());                         break;
        case NUMBER:
        case ANY:
        case UNKNOWN:
        default:
          throw new IllegalStateException("Unexpected type in readObject: " + ordinal);
      }
      restoredObjects[objId] = result;
      if (shouldRestore) {
        restoreObject(objOffset, result);
      }
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
          case MAP:      restoreMap((Map)obj);     break;
          case LIST:     restoreList((List)obj);   break;
          case ARRAY:    restoreArray(obj);        break;
          case CLASS:    break;
          case INSTANCE: break;     // Registered class so already restored
          default:    throw new IllegalStateException("Unexpected type in readObject: " + ordinal);
        }
      }
    }
    finally {
      idx = oldIdx;
    }
  }

  void restoreMap(Map map) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      map.put(readObject(true), readObject());
    }
  }

  private void restoreList(List list) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      list.add(readObject());
    }
  }

  private void readBooleanArr(boolean[] arr) {
    int size = readCInt();
    int b = 0;
    for (int i = 0; i < size; i++) {
      if ((i & 7) == 0) {
        b = buf[idx++];
      }
      arr[i] = (b & 0x1) != 0;
      b >>>= 1;
    }
  }

  private void readByteArr(byte[] arr) {
    int size = readCInt();
    System.arraycopy(buf, idx, arr, 0, size);
    idx += size;
  }

  private void readIntArr(int[] arr) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      arr[i] = readCInt();
    }
  }

  private void readLongArr(long[] arr) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      arr[i] = readCLong();
    }
  }

  private void readDoubleArr(double[] arr) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      arr[i] = readDouble();
    }
  }

  private void readObjectArr(Object[] arr) {
    int size = readCInt();
    for (int i = 0; i < size; i++) {
      arr[i] = readObject();
    }
  }

  private Object createArray() {
    int numDimensions = buf[idx++];
    int[]     dimensions = new int[numDimensions];
    JactlType type       = readType();
    int       size       = readCInt();
    dimensions[0] = size;
    switch (type.getType()) {
      case BOOLEAN:    return Array.newInstance(boolean.class, dimensions);
      case BYTE:       return Array.newInstance(byte.class, dimensions);
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
    int       numDimensions = buf[idx++];
    JactlType type          = readType();
    if (numDimensions == 1) {
      switch (type.getType()) {
        case BOOLEAN:    readBooleanArr((boolean[])arr);     break;
        case BYTE:       readByteArr((byte[])arr);           break;
        case INT:        readIntArr((int[])arr);             break;
        case LONG:       readLongArr((long[])arr);           break;
        case DOUBLE:     readDoubleArr((double[])arr);       break;
        default:         readObjectArr((Object[])arr);       break;
      }
    }
    else {
      int size = readCInt();
      for (int i = 0; i < size; i++) {
        Array.set(arr, i, readObject());
      }
    }
  }

  private Object createBuiltinInstance() {
    Class clss = BuiltinFunctions.getClass(readCInt());
    return newInstance(clss);
  }

  private Class createClass() {
    String internalClassName = (String)readObject();
    return getJactlClass(internalClassName);
  }

  private Object createInstance() {
    String internalClassName = (String)readObject();
    Class  clss              = getJactlClass(internalClassName);
    if (JactlObject.class.isAssignableFrom(clss)) {
      return newInstance(clss);
    }
    Function<Restorer,Object> restoreFn = context.getRegisteredClasses().getRestorer(clss);
    if (restoreFn == null) {
      throw new IllegalStateException("Cannot restore: could not locate a registered restore function for " + internalClassName);
    }
    return restoreFn.apply(this);
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
  
  public ClassDescriptor getClassDescriptor(String internalName) {
    return context.getClassDescriptor(internalName);
  }
}
