package io.jactl.compiler;

import io.jactl.*;
import io.jactl.runtime.BuiltinFunctions;
import io.jactl.runtime.RuntimeUtils;
import org.objectweb.asm.Label;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.LONG;
import static org.objectweb.asm.Opcodes.*;

/**
 * Code for compiling switch expressions
 */
public class SwitchCompiler {
  public static Void visitSwitch(MethodCompiler mc, Expr.Switch expr) {
    mc.compile(expr.subject);
    mc.unbox();
    mc.defineVar(expr.itVar);
    mc.storeVar(expr.itVar);

    // Create the capture var (if needed) plus any others
    Consumer<Collection<Expr.VarDecl>> createVars = varDecls -> varDecls.stream()
                                                                        .filter(varDecl -> !varDecl.name.getValue().equals(Utils.IT_VAR))
                                                                        .forEach(mc::compile);

    createVars.accept(expr.block.variables.values());

    Label end = new Label();
    // Create labels for all results that are not simple, so we only have to generate code for these once.
    Map<Expr,Label> nonSimpleLabels = expr.cases.stream()
                                                .filter(c -> !c.result.isSimple())
                                                .collect(Collectors.toMap(c -> c.result, t -> new Label(), (a, b) -> a));

    // Make sure all binding variables are created and initialised to default values
    expr.cases.forEach(c -> createVars.accept(c.block.variables.values()));

    Function<Triple<Expr,Expr,Expr.SwitchCase>,Boolean> isSimplePattern = pattern -> pattern.first instanceof Expr.Literal &&
                                                                                     !pattern.first.isNull() &&
                                                                                     pattern.second == null;

    List<Triple<Expr,Expr,Expr.SwitchCase>> flattened = expr.cases.stream()
                                                                  .flatMap(c -> c.patterns.stream().map(pair -> Triple.create(pair.first, pair.second, c)))
                                                                  .collect(Collectors.toList());

    for (int i = 0; i < flattened.size(); i++) {
      // Find subList with all literals that we can use emitSwitch with
      int j = i;
      for (; j < flattened.size() && isSimplePattern.apply(flattened.get(j)); j++) {}
      if (j - i > 2) {
        emitSwitch(mc, flattened.subList(i, j).stream().map(t -> Pair.create(t.first, t.third.result)).collect(Collectors.toList()),
                   expr.type, end, nonSimpleLabels, () -> mc.loadVar(expr.itVar));
        i = j - 1;
      }
      else {
        Triple<Expr,Expr,Expr.SwitchCase> pattern = flattened.get(i);
        Expr.VarDecl captureVarDecl = pattern.third.block.variables.get(Utils.CAPTURE_VAR);
        if (captureVarDecl != null) {
          mc.loadDefaultValue(MATCHER);
          mc.storeVar(captureVarDecl);
        }
        compileMatchCase(mc, expr, Pair.create(pattern.first,pattern.second), pattern.third.result, end, nonSimpleLabels);
      }
    }

    // Fall through to default case
    mc.compile(expr.defaultCase);
    mc.convertTo(expr.type, expr, true, expr.defaultCase.location);
    mc.popType();

    if (!nonSimpleLabels.isEmpty()) {
      mc.mv.visitJumpInsn(GOTO, end);
      nonSimpleLabels.forEach((resultExpr, label) -> {
        mc.mv.visitLabel(label);
        mc.compile(resultExpr);
        mc.convertTo(expr.type, expr, true, resultExpr.location);
        mc.popType();
        mc.mv.visitJumpInsn(GOTO, end);
      });
    }

    expr.cases.forEach(c -> {
      if (!c.block.variables.isEmpty()) {
        Label endCase = new Label();
        mc.mv.visitLabel(endCase);
        c.block.variables.values().forEach(v -> mc.undefineVar(v, endCase));
      }
    });

    mc.mv.visitLabel(end);
    Label endBlock = new Label();
    mc.mv.visitLabel(endBlock);
    expr.block.variables.forEach((name, varDecl) -> {
      mc.undefineVar(varDecl, endBlock);
    });
    mc.pushType(expr.type);
    return null;
  }

  private static void compileMatchCase(MethodCompiler mc, Expr.Switch expr, Pair<Expr, Expr> patternPair, Expr result, Label end, Map<Expr, Label> nonSimpleLabels) {
    mc.emitIf(expr.isAsync, MethodCompiler.IfTest.IS_TRUE, () -> {
             Label endCheck = new Label();
             Consumer<JactlType> loadValue = type -> {
               mc.loadVar(expr.itVar);
               if (type != null && !type.is(ANY,LIST)) {
                 mc.convertTo(type, expr.subject, true, expr.subject.location);
               }
             };
             compileMatchCaseTest(mc, loadValue, expr.subject.type, patternPair, expr.subject.location, endCheck);
             if (patternPair.second != null) {
               // If pattern did not match
               mc._dupVal();
               mc.mv.visitJumpInsn(IFEQ, endCheck);
               mc.popVal();
               // Evaluate the "if" expression part
               mc.compile(patternPair.second);
             }
             mc.mv.visitLabel(endCheck);
           },
           () -> {
             if (result.isSimple()) {
               mc.compile(result);
               mc.convertTo(expr.type, expr, true, result.location);
               mc.popType();
               mc.mv.visitJumpInsn(GOTO, end);
             }
             else {
               Label label = nonSimpleLabels.get(result);
               mc.mv.visitJumpInsn(GOTO, label);    // Skip to location where we will generate result for non-simple values
             }
           },
           null);
  }

  private static void compileMatchCaseTest(MethodCompiler mc, Consumer<JactlType> loadValue, JactlType subjectType, Pair<Expr, Expr> patternPair, Token subjectLocation, Label endCheck) {
    Expr pattern = patternPair.first;
    compileMatchCaseTest(mc, subjectType, pattern, subjectLocation, endCheck, loadValue);
  }

  private static void compileMatchCaseTest(MethodCompiler mc, JactlType subjectType, Expr pattern, Token subjectLocation, Label endCheck, Consumer<JactlType> loadValue) {
    Consumer<JactlType> ifCanConvertTo = (caseType) -> {
      // Check if type matches and if not jump to end with false on stack
      if (caseType.is(ANY)) { return; }
      // Check if we can convert
      loadValue.accept(null);
      mc.box();
      mc.loadConst(caseType.boxed());
      mc.invokeMethod(RuntimeUtils.class, RuntimeUtils.IS_PATTERN_COMPATIBLE, Object.class, Class.class);
      mc.dupVal();
      mc.mv.visitJumpInsn(IFEQ, endCheck);
      mc.popType();
      mc.popVal();
    };

    JactlType patternType = pattern.patternType();
    if (isUnderscore(pattern)) {
      // Always match placeholder pattern
      mc.loadConst(true);
    }
    else if (pattern instanceof Expr.TypeExpr || pattern instanceof Expr.Identifier || pattern instanceof Expr.VarDecl) {
      if (subjectType.equals(patternType)) {
        if (pattern instanceof Expr.TypeExpr) {
          mc.loadConst(true);
        }
      }
      else if (!pattern.isStar() && !patternType.is(ANY) && !subjectType.is(patternType) && !(subjectType.isNumeric() && patternType.isNumeric())) {
        loadValue.accept(ANY);
        mc.isInstanceOf(patternType);
        if (!(pattern instanceof Expr.TypeExpr)) {
          mc._dupVal();
          mc.mv.visitJumpInsn(IFEQ, endCheck);  // Not the right type
          mc.popVal();
        }
      }
      if (pattern instanceof Expr.VarDecl || (pattern instanceof Expr.Identifier && ((Expr.Identifier) pattern).firstTimeInPattern)) {
        loadValue.accept(pattern.type);
        mc.convertTo(pattern.type, pattern, true, pattern.location);
        mc.storeVar(pattern);
        mc.loadConst(true);   // Binding variable on its own always matches
      }
      else if (pattern instanceof Expr.Identifier) {
        loadValue.accept(pattern.type);
        mc.convertTo(pattern.type, pattern, true, pattern.location);
        mc.compile(pattern);
        mc.expect(2);
        checkForEquality(mc, pattern.type);
      }
    }
    else if (pattern.isLiteral() || pattern instanceof Expr.ExprString) {
      if (pattern.isConst || pattern instanceof Expr.ExprString) {
        // We assume that Resolver has already checked for static type compatibility so we only need
        // worry about situations where the type is ANY.
        // If type is ANY then we need to check if we can convert to the type of the literal
        if (!patternType.equals(subjectType) && subjectType.is(ANY)) {
          ifCanConvertTo.accept(patternType);
        }
        loadValue.accept(patternType);
        mc.compile(pattern);
        mc.expect(2);
        checkForEquality(mc, patternType);
      }
      else if (pattern instanceof Expr.ListLiteral) {
        if (!subjectType.is(ITERATOR, LIST, ARRAY, ANY)) { throw new IllegalStateException("Internal error: unexpected type " + subjectType); }
        List<Expr> exprs = ((Expr.ListLiteral) pattern).exprs;
        compileDestructuring(mc, subjectType, patternType, exprs.size(), exprs.stream().anyMatch(Expr::isStar), exprs, loadValue, subjectLocation, endCheck);
      }
      else if (pattern instanceof Expr.MapLiteral) {
        if (!subjectType.is(ITERATOR, MAP, ANY)) { throw new IllegalStateException("Internal error: unexpected type " + subjectType); }
        List<Pair<Expr,Expr>> exprs = ((Expr.MapLiteral) pattern).entries;
        compileDestructuring(mc, subjectType, patternType, exprs.size(), exprs.stream().anyMatch(p -> p.first.isStar()), exprs, loadValue, subjectLocation, endCheck);
      }
    } else if (pattern instanceof Expr.ConstructorPattern) {
      if (!subjectType.is(INSTANCE, ANY)) { throw new IllegalStateException("Internal error: unexpected type " + subjectType); }
      List<Pair<Expr,Expr>> exprs = ((Expr.MapLiteral)((Expr.ConstructorPattern) pattern).args).entries;
      compileDestructuring(mc, subjectType, patternType, exprs.size(), false, exprs, loadValue, subjectLocation, endCheck);
    }
    else if (pattern instanceof Expr.RegexMatch) {
      mc.compileRegexMatch((Expr.RegexMatch)pattern, () -> {
        ifCanConvertTo.accept(STRING);
        loadValue.accept(null);
      });
    }
    else {
      loadValue.accept(patternType);
      if (!patternType.isPrimitive()) {
        mc.box();
      }
      mc.compile(pattern);
      mc.expect(2);
      checkForEquality(mc, patternType);
      //throw new UnsupportedOperationException("Unsupported comparison in match: " + pattern.getClass().getName());
    }
  }

  private static boolean isUnderscore(Expr expr) {
    return expr instanceof Expr.Identifier && ((Expr.Identifier) expr).identifier.is(TokenType.UNDERSCORE);
  }

  private static void compileDestructuring(MethodCompiler mc, JactlType parentType, JactlType patternType, int size, boolean hasStar, List exprs, Consumer<JactlType> loadValue, Token subjectLocation, Label endCheck) {
    if (parentType.is(ANY)) {
      // Check for appropriate type
      loadValue.accept(ANY);
      mc.isInstanceOf(patternType.is(LIST) ? new JactlType[]{ ARRAY,LIST } : new JactlType[]{ patternType });
      mc._dupVal();
      mc.mv.visitJumpInsn(IFEQ, endCheck);
      mc.popVal();
    }

    // Do size check first
    if (patternType.is(LIST,MAP)) {
      int     minSize = hasStar && size == 1 ? -1 : size - (hasStar ? 1 : 0);
      int     maxSize = hasStar ? -1 : size;
      if (minSize >= 0 && minSize == maxSize) {
        checkSize(mc, loadValue, subjectLocation, endCheck, minSize, IFNE);
      }
      else {
        if (minSize > 0)  { checkSize(mc, loadValue, subjectLocation, endCheck, minSize, IFLT); }
        if (maxSize >= 0) { checkSize(mc, loadValue, subjectLocation, endCheck, maxSize, IFGT); }
      }
    }
    if (size > 0) {
      boolean seenStar = false;
      Map<JactlType,Integer> subElemSlots;
      JactlType arrayElemType = parentType.getArrayElemType();
      if (patternType.is(INSTANCE)) {
        subElemSlots = ((List<Pair<Expr, Expr>>) exprs).stream()
                                                       .map(pair -> patternType.getClassDescriptor().getField(pair.first.constValue.toString()))
                                                       .distinct()
                                                       .collect(Collectors.toMap(t -> t, t -> mc.stack.allocateSlot(t)));
      }
      else {
        subElemSlots = new HashMap<>();
        JactlType type = parentType.is(ARRAY) ? arrayElemType : ANY;
        subElemSlots.put(type, mc.stack.allocateSlot(type));
        int subListSlot = mc.stack.allocateSlot(LIST);    // for bound '*' wildcards
        mc.loadConst(null);
        mc.storeLocal(subListSlot);
        subElemSlots.put(LIST, subListSlot);
      }
      int subElemSlot;
      for (int i = 0; i < size; i++) {
        Expr subExpr;
        JactlType elemType = null;
        switch (patternType.getType()) {
          case LIST: {
            subExpr = (Expr) exprs.get(i);
            if (isUnderscore(subExpr)) {
              continue;
            }
            if (subExpr.isStar()) {
              seenStar = true;
              if (subExpr instanceof Expr.VarDecl) {
                // Need to bind variable to subList(1) or subList(0,-1) depending
                // on whether we are first or last element in pattern list so create
                // sublist and store in slot
                loadValue.accept(null);        // parent
                if (parentType.is(ANY,ARRAY)) {
                  // Convert to list if not already
                  mc.loadConst(false);
                  mc.loadLocation(subExpr.location);
                  mc.invokeMethod(RuntimeUtils.class, "asList", Object.class, boolean.class, String.class, int.class);
                }
                if (i == 0) {
                  mc.loadLocation(subExpr.location);
                  mc.loadConst(0);
                  mc.loadConst(-1);
                }
                else {
                  mc.loadLocation(subExpr.location);
                  mc.loadConst(1);
                  mc.loadConst(Integer.MAX_VALUE);
                }
                mc.invokeMethod(BuiltinFunctions.class, "listSubList", List.class, String.class, int.class, int.class, int.class);
                subElemSlot = subElemSlots.get(LIST);
                mc.storeLocal(subElemSlot);
                elemType = parentType;
              }
              else {
                // Nothing to do for '*' on its own
                continue;
              }
            }
            else {
              // Get element and store in a local
              loadValue.accept(null);  // get parent
              // Convert index to negative offset from end if needed
              int idx = seenStar ? i - size : i;
              mc.loadConst(idx);
              if (idx < 0) {
                loadValue.accept(null);
                mc.emitLength(subjectLocation);
                mc.expect(2);
                mc.mv.visitInsn(IADD);
                mc.popType();
              }
              mc.unsafeLoadElem(parentType, subjectLocation);
              elemType = parentType.is(ARRAY) ? arrayElemType : ANY;
              subElemSlot = subElemSlots.get(elemType);
              mc.storeLocal(subElemSlot);
            }
            break;
          }
          case MAP: {
            // Map
            Pair<Expr, Expr> keyVal = (Pair) exprs.get(i);
            subExpr = keyVal.second;
            elemType = ANY;
            if (keyVal.first.isStar()) {
              continue;
            }
            if (isUnderscore(subExpr)) {
              // Need to check for presence of key
              loadValue.accept(MAP);
              mc.compile(keyVal.first);
              mc.expect(2);
              mc.invokeMethod(Map.class, "containsKey", Object.class);
              mc._dupVal();
              mc.mv.visitJumpInsn(IFEQ, endCheck);
              mc.popVal();
              continue;
            }
            loadValue.accept(MAP);
            mc.compile(keyVal.first);
            mc.expect(2);
            mc.invokeMethod(Map.class, "get", Object.class);
            mc._dupVal();
            subElemSlot = subElemSlots.get(ANY);
            mc.storeLocal(subElemSlot);
            Label isNotNull = new Label();
            mc.mv.visitJumpInsn(IFNONNULL, isNotNull);
            mc._loadConst(false);
            mc.mv.visitJumpInsn(GOTO, endCheck);
            isNotNull:
            mc.mv.visitLabel(isNotNull);
            break;
          }
          case INSTANCE: {
            // Map
            Pair<Expr, Expr> keyVal = (Pair) exprs.get(i);
            subExpr = keyVal.second;
            if (isUnderscore(subExpr)) {
              // We already know that field exists in class (checked in Resolver)
              // so nothing to do for _ as field value.
              continue;
            }
            loadValue.accept(parentType);
            if (mc.peek().is(ANY)) {
              mc.isInstanceOf(patternType);
              mc._dupVal();
              mc.mv.visitJumpInsn(IFEQ, endCheck);
              mc.popVal();
              loadValue.accept(parentType);
              mc.checkCast(patternType);
            }
            String fieldName = keyVal.first.constValue.toString();
            elemType = patternType.getClassDescriptor().getField(fieldName);
            mc.loadClassField(patternType.getInternalName(), fieldName, elemType, false);
            subElemSlot = subElemSlots.get(elemType);
            mc.storeLocal(subElemSlot);
            if (elemType.isRef()) {
              Label isNotNull = new Label();
              mc._loadLocal(subElemSlot);
              mc.mv.visitJumpInsn(IFNONNULL, isNotNull);
              mc._loadConst(false);
              mc.mv.visitJumpInsn(GOTO, endCheck);
              isNotNull:
              mc.mv.visitLabel(isNotNull);
            }
            break;
          }
          default: throw new IllegalStateException("Internal error: unexpected type " + patternType);
        }
        int finalSubElemSlot = subElemSlot;
        compileMatchCaseTest(mc, elemType, subExpr, subjectLocation, endCheck, t -> {
          mc.loadLocal(finalSubElemSlot);
          if (t != null && !t.is(ANY,LIST)) {
            mc.convertTo(t, subExpr, true, subExpr.location);
          }
        });
        mc.dupVal();
        mc.mv.visitJumpInsn(IFEQ, endCheck);
        mc.popType();
        mc.popVal();
      }
      subElemSlots.forEach((k,v) -> mc.stack.freeSlot(v));
    }
    mc.loadConst(true);
  }

  private static void checkSize(MethodCompiler mc, Consumer<JactlType> loadValue, Token subjectLocation, Label endCheck, int size, int opCode) {
    loadValue.accept(ANY);
    mc.emitLength(subjectLocation);
    mc.loadConst(size);
    mc.expect(2);
    mc.mv.visitInsn(ISUB);
    mc.popType(2);
    mc.pushType(INT);
    // Put false on stack so if we jump to endCheck the match case will fail
    mc.loadConst(false);
    mc.swap();
    mc.mv.visitJumpInsn(opCode, endCheck);
    mc.popType();
    mc.popVal();
  }

  private static void checkForEquality(MethodCompiler mc, JactlType type) {
    if (type.isPrimitive()) {
      int opCode = type.is(BOOLEAN,BYTE,INT) ? ISUB :
                   type.is(LONG)             ? LCMP :
                   DCMPL;
      mc.mv.visitInsn(opCode);
      mc.popType(2);
      Label isTrue = new Label();
      Label end    = new Label();
      mc.mv.visitJumpInsn(IFEQ, isTrue);
      mc._loadConst(false);
      mc.mv.visitJumpInsn(GOTO, end);
      mc.mv.visitLabel(isTrue);
      mc._loadConst(true);
      mc.mv.visitLabel(end);
      mc.pushType(BOOLEAN);
    }
    else {
      mc.invokeMethod(RuntimeUtils.class, RuntimeUtils.SWITCH_EQUALS, Object.class, Object.class);
    }
  }

  private static void emitSwitch(MethodCompiler mc,
                                 List<Pair<Expr, Expr>> cases,
                                 JactlType resultType,
                                 Label end,
                                 Map<Expr, Label> nonSimpleLabels,
                                 Runnable loadSubject) {
    // We need to decide whether to output a TABLESWITCH or a LOOKUPSWITCH statement.
    // TABLESWITCH is faster (if applicable) but can use more space if the values produce
    // a sparse table.
    // We use a heuristic that if the sparse table size would be more than 5 times as big
    // as the number of individual cases then we will use a LOOKUPSWITCH instead. This
    // check only applies if the values are all integral types (numeric and not double/BigDecimal).
    // All other types we will generate a LOOKUPSWITCH for.
    Function<Expr, Boolean> isIntegral = e -> e.isConst && e.constValue instanceof Number &&
                                              !(e.constValue instanceof Double || e.constValue instanceof BigDecimal || e.constValue instanceof Long);

    // Check if all patterns are an integer value in which case we might be able to use a TABLESWITCH
    boolean useTable = cases.stream().allMatch(t -> isIntegral.apply(t.first));

    Label noMatch = new Label();

    loadSubject.run();
    if (useTable) {
      // Build map of unique results so that we can have one label per result
      Map<Expr,Label> switchLabels = cases.stream()
                                          .collect(Collectors.toMap(p -> p.second, p -> nonSimpleLabels.getOrDefault(p.second, new Label()), (a,b) -> a));

      // Sort literal values
      List<Pair<Integer,Label>> sorted = cases.stream()
                                              .map(p -> Pair.create(((Number) p.first.constValue).intValue(), switchLabels.get(p.second)))
                                              .sorted(Comparator.comparingInt(p -> p.first))
                                              .collect(Collectors.toList());

      // Make sure that we don't have so many values that the table would be too large
      int max   = sorted.get(sorted.size() - 1).first;
      int min   = sorted.get(0).first;
      int range = max - min + 1;
      if (range > 5 * sorted.size()) {
        useTable = false;
      }
      if (useTable) {
        // Make sure we have a subject of the right type
        switch (mc.peek().getType()) {
          case ANY: {
            mc.invokeMethod(RuntimeUtils.class, RuntimeUtils.INT_VALUE, Object.class);
            mc._dupVal();
            Label isInt = new Label();
            mc.mv.visitJumpInsn(IFNONNULL, isInt);
            mc.mv.visitInsn(POP);
            mc.mv.visitJumpInsn(GOTO, noMatch);
            mc.mv.visitLabel(isInt);
            mc.invokeMethod(Integer.class, "intValue");
            break;
          }
          case BYTE: case INT:     break;
          default: throw new IllegalStateException("Internal error: Unexpected type for int based switch: " + mc.peek());
        }

        // Have integral values not spaced out too far
        // Populate labels with defaultLabel
        Label[] labels = IntStream.range(0, range).mapToObj(i -> noMatch).toArray(Label[]::new);
        // Override where we have an actual case with the label for the result
        sorted.forEach(t -> labels[t.first - min] = t.second);
        mc.mv.visitTableSwitchInsn(min, max, noMatch, labels);
        mc.popType();
        // For any results that are simple we compile on the spot. Non-simple ones are left for later.
        switchLabels.entrySet().stream().filter(e -> !nonSimpleLabels.containsKey(e.getKey())).forEach(entry -> {
          mc.mv.visitLabel(entry.getValue());
          mc.compile(entry.getKey());
          mc.convertTo(resultType, entry.getKey(), true, entry.getKey().location);
          mc.popType();
          mc.mv.visitJumpInsn(GOTO, end);
        });
        mc.mv.visitLabel(noMatch);
        return;
      }
      // Fall through to using LOOKUPSWITCH if we couldn't use TABLESWITCH
    }

    mc.box();
    mc.mv.visitJumpInsn(IFNULL, noMatch);
    mc.popType();
    loadSubject.run();
    mc.box();
    mc.invokeMethod(Object.class, "hashCode");

    // Annoyingly, we need to support multiple literal values that could hash to the same value so
    // we calculate a list of unique hashCodes combined with a pair of label and a list of pattern/result pairs
    // and then sort on hash code.
    List<Map.Entry<Integer,Pair<Label,List<Pair<Expr,Expr>>>>> hashed =
      cases.stream()
           .collect(Collectors.toMap(p -> p.first.constValue.hashCode(),
                                     p -> Pair.create(new Label(), Utils.listOf(Pair.create(p.first, p.second))),
                                     (a, b) -> { a.second.addAll(b.second); return a; }))
           .entrySet().stream()
           .sorted(Comparator.comparingInt(Map.Entry::getKey))
           .collect(Collectors.toList());

    mc.mv.visitLookupSwitchInsn(noMatch,
                             hashed.stream().mapToInt(Map.Entry::getKey).toArray(),
                             hashed.stream().map(e -> e.getValue().first).toArray(Label[]::new));
    mc.popType();

    hashed.forEach(entry -> {
      mc.mv.visitLabel(entry.getValue().first);
      entry.getValue().second.forEach( pair -> {
        loadSubject.run();
        mc.box();
        mc.compile(pair.first);       // literal value
        mc.box();
        Label notEqual = new Label();
        mc.invokeMethod(RuntimeUtils.class, RuntimeUtils.SWITCH_EQUALS, Object.class, Object.class);
        mc.mv.visitJumpInsn(IFEQ, notEqual);
        mc.popType();
        Label nonSimple = nonSimpleLabels.get(pair.second);
        if (nonSimple == null) {
          mc.compile(pair.second);
          mc.convertTo(resultType, pair.second, true, pair.second.location);
          mc.popType();
          mc.mv.visitJumpInsn(GOTO, end);
        }
        else {
          mc.mv.visitJumpInsn(GOTO, nonSimple);
        }
       isNotLiteral:
        mc.mv.visitLabel(notEqual);
      });
      mc.mv.visitJumpInsn(GOTO, noMatch);
    });

    mc.mv.visitLabel(noMatch);
  }

}
