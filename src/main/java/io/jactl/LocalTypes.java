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

package io.jactl;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.DOUBLE;
import static io.jactl.JactlType.LONG;
import static org.objectweb.asm.Opcodes.*;

/**
 * Track the type of each local slot and the types of what is on the JVM stack.
 */
public class LocalTypes {

  private List<LocalEntry>       locals        = new ArrayList<>();
  private Map<String,LocalEntry> globalAliases = new HashMap<>();
  private int minimumSlot = 0;
  private int maxIndex    = -1;

  private Deque<StackEntry> stack  = new ArrayDeque<>();

  private final MethodVisitor mv;
  private final boolean       isStatic;

  LocalTypes(MethodVisitor mv, boolean isStatic) {
    this.mv = mv;
    this.isStatic = isStatic;
  }

  public boolean isEmpty() {
    return stack.isEmpty();
  }

  public int stackDepth() {
    return stack.size();
  }

  public int getMaxLocals() {
    return maxIndex + 1;
  }

  public LocalTypes copy() {
    var copy = new LocalTypes(mv, isStatic);
    copy.stack = copyStack(copy);
    copy.locals = copyLocals();
    copy.minimumSlot = minimumSlot;
    copy.maxIndex = maxIndex;
    copy.globalAliases = new HashMap<>(globalAliases);
    return copy;
  }

  public StackEntry peek() {
    return stack.peek();
  }

  public JactlType peekType2() {
    StackEntry top = stack.pop();
    JactlType result = stack.peek().type;
    stack.push(top);
    return result;
  }

  public void push(Class clss) {
    push(JactlType.typeFromClass(clss));
  }

  public void push(JactlType type) {
    stack.push(new StackEntry(type));
  }

  public JactlType pop() {
    var entry = stack.pop();
    freeSlot(entry.slot);
    return entry.type;
  }

  public void pop(int count) {
    for (int i = 0; i < count; i++) {
      pop();
    }
  }

  public void setStackType(JactlType type) {
    stack.peek().type = type;
  }

  public void dup() {
    var entry = stack.peek();
    if (entry.slot >= 0) {
      locals.get(entry.slot).refCount++;
    }
    var dup = new StackEntry(entry);
    stack.push(dup);
  }

  /**
   * Swap types on type stack and also swap values on JVM stack
   */
  public void swap() {
    var entry1 = stack.pop();
    var entry2 = stack.pop();
    if (entry1.slot == -1 && entry2.slot == -1) {
      if (slotsNeeded(entry1.type) == 1 && slotsNeeded(entry2.type) == 1) {
        mv.visitInsn(SWAP);
      }
      else {
        int temp1 = allocateSlot(entry1.type);
        int temp2 = allocateSlot(entry2.type);
        _storeLocal(temp1);
        _storeLocal(temp2);
        _loadLocal(temp1);
        _loadLocal(temp2);
        freeSlot(temp2);
        freeSlot(temp1);
      }
      push(entry1.type);
      push(entry2.type);
    }
    else {
      // We have at least one "stack" value in a local var slot so we don't need to manipulate the real
      // stack - we just need to swap the entries on the type stack
      stack.push(entry1);
      stack.push(entry2);
    }
  }

  /**
   * Duplicate whatever is on the JVM stack and duplicate type on type stack.
   * For double and long we need to duplicate top two stack elements
   */
  public void dupVal() {
    dup();
    int slot = stack.peek().slot;
    if (slot == -1) {
      _dupVal();
    }
  }

  /**
   * Duplicate top most element and move it
   * <pre>
   * Before:  ..., y, x
   * After:   ..., x, y, x
   * </pre>
   */
  public void dupValX1() {
    var x = stack.pop();
    var y = stack.pop();
    if (x.isStack() && y.isStack()) {
      int xslots = slotsNeeded(x.type);
      int yslots = slotsNeeded(y.type);
      if (xslots == 1 && yslots == 1) {
        mv.visitInsn(DUP_X1);
      }
      else
      if (xslots == 2 && yslots == 2) {
        mv.visitInsn(DUP2_X2);
      }
      else
      if (xslots == 1 && yslots == 2) {
        mv.visitInsn(DUP_X2);
      }
      else
      if (xslots == 2 && yslots == 1) {
        mv.visitInsn(DUP2_X1);
      }
      else {
        throw new IllegalStateException("Internal error: xslots=" + xslots + ", yslots=" + yslots);
      }
    }
    else
    if (x.isStack() && y.isLocal()) {
      mv.visitInsn(x.type.is(DOUBLE,LONG) ? DUP2 : DUP);
    }
    else {
      // x local or both local
      locals.get(x.slot).refCount++;
    }
    stack.push(x);
    stack.push(y);
    stack.push(new StackEntry(x));
  }

  /**
   * Duplicate value on JVM stack but don't touch type stack
   */
  public void _dupVal() {
    mv.visitInsn(peek().type.is(DOUBLE, LONG) ? DUP2 : DUP);
  }

  /**
   * Duplicate top two elemenets on the stack:
   * <pre>
   *   ... x, y -&gt;
   *   ... x, y, x, y
   * </pre>
   * Note that we need to take care whether any of the types are long or double
   * since the JVM operation dup2 only duplicates the top two slots of the stack
   * rather than the top two values.
   */
  public void dupVal2() {
    var x = stack.pop();
    var y = stack.pop();

    if (x.isStack() && y.isStack()) {
      JactlType type1 = x.type;
      JactlType type2 = y.type;
      if (slotsNeeded(type1) == 1 && slotsNeeded(type2) == 1) {
        mv.visitInsn(DUP2);
      }
      else {
        int slot1 = allocateSlot(type1);
        int slot2 = allocateSlot(type2);
        _storeLocal(slot1);
        _storeLocal(slot2);
        _loadLocal(slot2);
        _loadLocal(slot1);
        _loadLocal(slot2);
        _loadLocal(slot1);
        freeSlot(slot2);
        freeSlot(slot1);
      }
      push(type2);
      push(type1);
      push(type2);
      push(type1);
    }
    else {
      // We have at least one value in local vars
      if (y.isStack()) {
        push(y.type);
        _dupVal();
        stack.push(x);
        locals.get(x.slot).refCount++;
        push(y.type);
        stack.push(new StackEntry(x));
      }
      else {
        // x is on stack
        check(x.isStack(), "x should be on stack (not in locals)");
        stack.push(y);
        locals.get(y.slot).refCount++;
        push(x.type);
        _dupVal();
        stack.push(new StackEntry(y));
        push(x.type);
      }
    }
  }

  /**
   * Pop value off JVM stack and corresponding type off type stack
   */
  public void popVal() {
    if (stack.peek().isStack()) {
      _popVal();
    }
    pop();
  }

  /**
   * Pop value off JVM stack but don't touch type stack
   */
  public void _popVal() {
    _popVal(peek().type);
  }

  private void _popVal(JactlType type) {
    mv.visitInsn(type.is(DOUBLE, LONG) ? POP2 : POP);
  }

  public void _popVal(int n) {
    // Pop real values off stack but leave type stack unchanged
    var iter = stack.iterator();
    for (int i = 0; i < n; i++) {
      check(iter.hasNext(), "stack depth should be at least " + n + " but is only " + stack.size() + ": stack=" + stack);
      var entry = iter.next();
      if (entry.slot != -1) {
        continue;  // Nothing to do since "stack" location is a local var
      }
      _popVal(entry.type);
    }
  }

  /**
   * Expect n entries on the stack.
   * This method will convert any entries that are currently stored in locals
   * back into entries on the real stack.
   * @param count  how many entries to ensure are on the stack
   */
  public void expect(int count) {
    check(stack.size() >= count, "stack size is " + stack.size() + " but expecting at least " + count + " entries");

    var iter = stack.iterator();
    boolean seenEntryOnStack = false;
    boolean needToStoreInLocals = false;
    for (int i = 0; i < count; i++) {
      StackEntry entry = iter.next();
      // Need to store stack values in locals if we have a local value in the middle of our stack
      if (entry.isLocal() && seenEntryOnStack) {
        needToStoreInLocals = true;
      }
      seenEntryOnStack = entry.slot == -1;
    }

    // If there are a mixture of stack and locals then convert all stack to locals
    if (needToStoreInLocals) {
      convertStackToLocals(count);
    }

    // Now iterate in reverse order and turn all locals entries into stack entries
    convertLocalsToStack(count);
  }

  public void convertStackToLocals() {
    convertStackToLocals(stack.size());
  }

  public void convertStackToLocals(int n) {
    // If entry is on the real stack then store in a local
    stack.stream().limit(n).forEachOrdered(StackEntry::convertToLocal);
  }

  public void convertStackToLocalsExcept(int n) {
    // First check if there are any to convert
    if (stack.stream().skip(n).anyMatch(StackEntry::isStack)) {
      // Need to save first n elements and restore afterwards
      convertStackToLocals(n);

      // If entry is on the real stack then store in a local
      stack.stream().skip(n).forEachOrdered(StackEntry::convertToLocal);

      // Restore our first n
      convertLocalsToStack(n);
    }
  }

  /**
   * Make our state same as another stack state by converting stack entries to local entries
   * and converting local entries to stack entries as appropriate.
   * <p>
   * NOTE: only supports a single element that is different to avoid the complicated (and as
   * yet unneeded) generic process of converting multiple entries.</p>
   * @param other  the other set of stack/locals
   * @param n      how many to convert (only 1 is currently supported)
   */
  public void makeSameAs(LocalTypes other, int n) {
    check(n <= 1, "no support for converting more than 1 element at the moment");
    maxIndex = Math.max(maxIndex, other.maxIndex);
    if (stack.size() == 0 && other.stack.size() == 0) { return; }
    if (peek().isStack() && other.peek().isStack())   { return; }
    if (peek().isLocal() && other.peek().isLocal()) {
      check(peek().slot == other.peek().slot, "Slots are different between two stacks:" + "\n this=" + this + "\n other=" + other);
      return;
    }
    if (other.peek().isStack()) {
      convertLocalsToStack(1);
    }
    else {
      int slot = other.peek().slot;
      // Make sure slot is available
      if (slotIsFree(slot)) {
        stack.peek().convertToLocal(slot);
      }
      else {
        throw new IllegalStateException("Internal error: could not allocate slot " + slot + "\n this=" + this + "\n other=" + other);
      }
    }
  }

  private void convertLocalsToStack(int n) {
    // Restore in reverse order
    stack.stream()
         .limit(n)
         .collect(Collectors.toCollection(ArrayDeque::new))
         .descendingIterator()
         .forEachRemaining(StackEntry::convertToStack);
  }

  public void saveLocals(int continuationVar, int globalsVar, int longArr, int objArr) {
    Function<Integer,Boolean>    ignoreEntry = i -> i == continuationVar || i == globalsVar || i == longArr || i == objArr;
    int  startSlot = isStatic ? 0 : 1;
    var entries = IntStream.range(startSlot, locals.size())
                           .filter(i -> !ignoreEntry.apply(i))
                           .mapToObj(i -> locals.get(i))
                           .filter(entry -> entry != null)
                           .filter(entry -> !entry.isGlobalVar)
                           .collect(Collectors.toList());
    boolean savePrimitives = entries.stream().anyMatch(entry -> entry.type.isPrimitive());
    boolean saveObjects    = entries.stream().anyMatch(entry -> !entry.type.isPrimitive());

    if (!savePrimitives) {
      mv.visitInsn(ACONST_NULL);
      _storeLocal(longArr);
    }
    if (!saveObjects) {
      mv.visitInsn(ACONST_NULL);
      _storeLocal(objArr);
    }

    if (!savePrimitives && !saveObjects) {
      return;   // nothing to do
    }

    Utils.loadConst(mv, locals.size());
    if (savePrimitives && saveObjects) {
      mv.visitInsn(DUP);
    }
    if (savePrimitives) {
      mv.visitIntInsn(NEWARRAY, T_LONG);
      _storeLocal(longArr);
    }
    if (saveObjects) {
      mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
      _storeLocal(objArr);
    }

    for (int i = startSlot; i < locals.size(); ) {
      var       entry = locals.get(i);
      JactlType type  = entry == null ? null : entry.type;
      if (entry != null && !ignoreEntry.apply(i) && !entry.isGlobalVar) {
        _loadLocal(type.isPrimitive() ? longArr : objArr, type.isPrimitive() ? LONG_ARR : OBJECT_ARR);
        Utils.loadConst(mv, i);
        _loadLocal(i);
        if (type.isPrimitive()) {
          if (type.is(BOOLEAN,BYTE,INT)) { mv.visitInsn(I2L); }
          if (type.is(DOUBLE)) {
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Double.class),
                               "doubleToRawLongBits",
                               Type.getMethodDescriptor(Type.getType(long.class),
                                                        Type.getType(double.class)),
                               false); }
        }
        mv.visitInsn(type.isPrimitive() ? LASTORE : AASTORE);
      }
      i += type == null ? 1 : slotsNeeded(type);
    }
  }

  public void restoreLocals(int continuationVar, int globalsVar, int longArr, int objArr) {
    int  startSlot = isStatic ? 0 : 1;
    for (int i = startSlot; i < locals.size(); i++) {
      var entry = locals.get(i);
      if (entry == null || entry.isGlobalVar || i == continuationVar || i == globalsVar || i == longArr || i == objArr) {
        continue;
      }
      var type = entry.type;
      Utils.loadStoredValue(mv, continuationVar, i, type);
      _storeLocal(type, i);
      i += slotsNeeded(type) - 1;
    }

  }

  public void freeSlot(int slot) {
    if (slot == -1) { return; }
    var entry = locals.get(slot);
    if (--entry.refCount == 0) {
      int slots = slotsNeeded(entry.type);
      for (int i = 0; i < slots; i++) {
        locals.set(slot + i, null);
      }
    }
  }

  /**
   * Pop top stack element and save in a local temp.
   * NOTE: if top element is already in a local we reuse the existing slot rather than
   * allocate a new slot.
   * @return the slot allocated (or reused) for the temp value
   */
  public int saveInTemp() {
    var entry = stack.peek();
    if (entry.slot != -1) {
      // No need to inc ref count since we would dec when popping stack and then inc again here
      // so net result is no change
      stack.pop();
      return entry.slot;
    }
    int temp = allocateSlot(entry.type);
    storeLocal(temp);
    return temp;
  }

  /**
   * Allocate a slot for storing a value of given type.
   * We search for first free gap in our local slots big enough for our type.
   * Supports allocation of double slots for long and double values.
   * @param type the type to allocate
   * @return the slot allocated
   */
  public int allocateSlot(JactlType type) {
    int i;
    for (i = minimumSlot; i < locals.size(); i += locals.get(i) == null ? 1 : slotsNeeded(localsType(i))) {
      if (slotsFree(i, slotsNeeded(type))) {
        break;
      }
    }
    setSlot(i, type);
    if (i > maxIndex) {
      maxIndex = i;
    }
    return i;
  }

  /**
   * Allocate a slot.
   * Allow optimisation for initial allocation of parameter slots. No need to ever
   * search these slots for a free entry later since they can never be unallocated.
   * @param type     the type
   * @param isParam  true if slot is for a parameter
   * @return the slot
   */
  public int allocateSlot(JactlType type, boolean isParam) {
    int slot = allocateSlot(type);
    if (isParam) {
      minimumSlot = locals.size();
    }
    return slot;
  }

  public void allocateGlobalVarSlot(String name, JactlType type) {
    int slot = allocateSlot(type);
    LocalEntry localEntry = locals.get(slot);
    localEntry.isGlobalVar = true;
    minimumSlot = locals.size();
    globalAliases.put(name, localEntry);
  }

  public int globalVarSlot(String name) {
    LocalEntry localEntry = globalAliases.get(name);
    if (localEntry == null) {
      throw new IllegalStateException("Could not find local alias for global var " + name);
    }
    return localEntry.slot;
  }

  public void loadLocal(int slot) {
    check(slot != -1, "trying to load from unitilialised variable (slot is -1)");
    _loadLocal(slot);
    JactlType type = localsType(slot);
    push(type);
  }

  public void _loadLocal(int slot) {
    JactlType type = localsType(slot);
    _loadLocal(slot, type);
  }

  private void _loadLocal(int slot, JactlType type) {
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ILOAD, slot); break;
        case BYTE:      mv.visitVarInsn(ILOAD, slot); break;
        case INT:       mv.visitVarInsn(ILOAD, slot); break;
        case LONG:      mv.visitVarInsn(LLOAD, slot); break;
        case DOUBLE:    mv.visitVarInsn(DLOAD, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ALOAD, slot);
    }
  }

  public void storeLocal(int slot) {
    expect(1);
    _storeLocal(slot);
    pop();
  }

  public void _storeLocal(int slot) {
    _storeLocal(localsType(slot), slot);
  }

  private void _storeLocal(JactlType type, int slot) {
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ISTORE, slot); break;
        case BYTE:      mv.visitVarInsn(ISTORE, slot); break;
        case INT:       mv.visitVarInsn(ISTORE, slot); break;
        case LONG:      mv.visitVarInsn(LSTORE, slot); break;
        case DOUBLE:    mv.visitVarInsn(DSTORE, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ASTORE, slot);
    }
  }

  private JactlType localsType(int slot) {
    var entry = locals.get(slot);
    check(entry != null, "trying to get type of null entry");
    return entry.type;
  }

  private static int slotsNeeded(JactlType type) {
    if (type == null) {
      return 1;
    }
    return type.is(LONG,DOUBLE) ? 2 : 1;
  }

  /**
   * Check if there are num free slots at given index
   */
  private boolean slotsFree(int idx, int num) {
    while (idx < locals.size() && num > 0 && locals.get(idx) == null) {
      idx++;
      num--;
    }
    return num == 0;
  }

  private void setSlot(int idx, JactlType type) {
    // Grow array if needed
    ntimes(idx + slotsNeeded(type) - locals.size(), () -> locals.add(null));

    // Set first slot to type. For multi-slot types others are marked as ANY.
    for(int i = 0; i < slotsNeeded(type); i++) {
      var entry = new LocalEntry(i == 0 ? type : ANY, idx);
      locals.set(idx + i, entry);
    }
  }

  private boolean slotIsFree(int slot) {
    return slot >= locals.size() || locals.get(slot) == null;
  }

  private void ntimes(int count, Runnable runnable) {
    for (int i = 0; i < count; i++) {
      runnable.run();
    }
  }

  @Override
  public String toString() {
    return "[stack=" + stack + ", " + "locals=" + locals + "]";
  }

  ////////////////////////////////////////////////////

  private Deque<StackEntry> copyStack(LocalTypes other) {
    var copy = stack.stream().map(other::copy).collect(Collectors.toCollection(ArrayDeque::new));
    return copy;
  }

  private List<LocalEntry> copyLocals() {
    var copy = locals.stream().map(local -> local == null ? null : new LocalEntry(local)).collect(Collectors.toList());
    return copy;
  }

  public boolean stackTypesEqual(LocalTypes other) {
    Collection<StackEntry> stack2 = other.stack;
    if (stack.size() != stack2.size()) { return false; }
    for (Iterator<StackEntry> iter1 = stack.iterator(), iter2 = stack2.iterator(); iter1.hasNext() && iter2.hasNext(); ) {
      JactlType type1 = iter1.next().type;
      JactlType type2 = iter2.next().type;
      if (!type1.isCastableTo(type2) && !type2.isCastableTo(type1)) {
        return false;
      }
    }
    return true;
  }

  public boolean stacksEqual(LocalTypes other) {
    return stacksAreMostlyEqual(0, stack, other.stack);
  }

  public boolean localsEqual(LocalTypes other) {
    return stacksAreMostlyEqual(0, locals, other.locals);
  }

  public boolean stacksAreMostlyEqual(int skip, LocalTypes other) {
    return stacksAreMostlyEqual(skip, stack, other.stack);
  }

  private static boolean stacksAreMostlyEqual(int skip, Collection stack1, Collection stack2) {
    Iterator iter1 = stack1.iterator();
    Iterator iter2 = stack2.iterator();
    while (iter1.hasNext() && iter2.hasNext()) {
      var next1 = iter1.next();
      var next2 = iter2.next();
      if (skip-- > 0) {
        continue;
      }
      if (next1 == next2) {
        continue;
      }
      if (next1 == null || next2 == null) {
        return false;
      }
      if (!next1.equals(next2)) {
        return false;
      }
    }
    // Can have different sizes if all remaining values are null
    Iterator iter = iter1.hasNext() ? iter1 : iter2;
    while (iter.hasNext()) {
      if (iter.next() != null) {
        return false;
      }
    }
    return true;
  }


  private static void check(boolean condition, String msg) {
    if (!condition) {
      throw new IllegalStateException("Internal error: " + msg);
    }
  }

  /////////////////////////////////////////////////////

  private static class LocalEntry {
    JactlType type;
    int       slot = -1;
    int       refCount = 1;
    boolean   isGlobalVar;

    LocalEntry(JactlType type, int slot)  { this.type = type; this.slot = slot; }
    LocalEntry(LocalEntry entry) {
      this.type = entry.type;
      this.refCount = entry.refCount;
      this.isGlobalVar = entry.isGlobalVar;
      this.slot = entry.slot;
    }
    @Override public boolean equals(Object that) {
      return type.equals(((LocalEntry)that).type) && refCount == ((LocalEntry)that).refCount &&
             isGlobalVar == ((LocalEntry)that).isGlobalVar;
    }
    @Override public String toString() { return "[" + type + "," + refCount + ",global=" + isGlobalVar + "]"; }
  }

  /**
   * As we generate the byte code we keep a stack where we track the type of the value that
   * would currently be on the JVM stack at that point in the generated code. This allows
   * us to know when to do appropriate conversions.
   * When doing async calls we need to move what ever is on the real stack to local var slots
   * because exception catching discards what is on the stack. We track which slots correspond
   * to the entries in our virtual stack. When the entry is on the real stack we store a value
   * of -1. We also track how many references to each slot exist on our virtual stack. This is
   * to allow us to dup() values without having to actually do anything except increment the
   * reference count. We release the local var slot once the reference count goes to 0.
   */
  public class StackEntry {
    JactlType type;
    int       slot = -1;

    StackEntry(JactlType type)            { this.type = type; }
    StackEntry(JactlType type, int slot)  { this.type = type; this.slot = slot; }
    StackEntry(StackEntry entry)           { this.type = entry.type; this.slot = entry.slot; }

    boolean isLocal()             { return slot != -1; }
    boolean isStack()             { return !isLocal(); }

    void convertToStack()         { if (isLocal()) { _loadLocal(slot); freeSlot(slot); slot = -1; } }
    void convertToLocal()         { if (isStack()) { slot = allocateSlot(type); _storeLocal(slot); } }
    void convertToLocal(int slot) { this.slot = slot; setSlot(slot, type); _storeLocal(slot); }

    @Override public boolean equals(Object obj) {
      return ((StackEntry)obj).type.equals(type) && ((StackEntry)obj).slot == slot;
    }

    @Override public String toString() { return "[" + type + "," + slot + "]"; }
  }

  private StackEntry copy(StackEntry entry) {
    return new StackEntry(entry.type, entry.slot);
  }
}
