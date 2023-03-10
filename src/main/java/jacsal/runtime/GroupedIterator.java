package jacsal.runtime;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator that groups the elements of list into sublists of given size.
 * E.g. turning a list into a list of pairs.
 */
public class GroupedIterator implements Iterator {
  Iterator     iter;
  String       source;
  int          offset;
  int          size;
  boolean      haveNext = false;
  boolean      finished = false;
  List<Object> group = null;

  GroupedIterator(Iterator iter, String source, int offset, int size) {
    this.iter   = iter;
    this.source = source;
    this.offset = offset;
    this.size   = size;
  }

  private static MethodHandle hasNextHandle = RuntimeUtils.lookupMethod(GroupedIterator.class, "hasNext$c", Object.class, GroupedIterator.class, Continuation.class);

  public static Object hasNext$c(GroupedIterator iter, Continuation c) {
    iter.haveNext = true;
    boolean result = (boolean)c.getResult();
    iter.finished = !result;
    return result;
  }

  @Override public boolean hasNext() {
    try {
      if (group != null) {
        return true;
      }
      if (finished) {
        return false;
      }
      if (!haveNext) {
        haveNext = true;
        finished = !iter.hasNext();
      }
      return !finished;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, hasNextHandle.bindTo(this), 0, null, null);
    }
  }

  private static MethodHandle nextHandle = RuntimeUtils.lookupMethod(GroupedIterator.class, "next$c", Object.class, GroupedIterator.class, Continuation.class);

  public static Object next$c(GroupedIterator iter, Continuation c) {
    return iter.doNext(c);
  }

  @Override public Object next() {
    return doNext(null);
  }

  private Object doNext(Continuation c) {
    int location = c == null ? 0 : c.methodLocation;
    try {
      boolean hasNext = false;
      Object  elem    = null;
      while (!finished && (group == null || group.size() < size)) {
        switch (location) {
          case 0:  hasNext = haveNext || iter.hasNext(); location = 2; break;
          case 1:  hasNext = (boolean)c.getResult();     location = 2; break;
          case 2:
            haveNext = false;
            if (!hasNext) {
              finished = true;
              break;
            }
            elem     = iter.next();
            location = 4;
            break;
          case 3:  elem = c.getResult();                 location = 4; break;
          case 4:
            elem = RuntimeUtils.mapEntryToList(elem);
            group = group == null ? new ArrayList<>(size) : group;
            group.add(elem);
            location = 0;
            break;
        }
      }
      var result = group;
      group = null;
      return result;
    }
    catch (Continuation cont) {
      throw new Continuation(cont, nextHandle.bindTo(this), location + 1, null, null);
    }
    catch (RuntimeError e) {
      throw e;
    }
    catch (Throwable t) {
      throw new RuntimeError("Unexpected error", source, offset, t);
    }
  }
}
