/*
 * Copyright © 2022-2026 James Crawford
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

package io.jactl.compiler;

import io.jactl.*;
import io.jactl.runtime.*;
import org.objectweb.asm.Label;

import java.util.ArrayList;
import java.util.List;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.LONG;
import static org.objectweb.asm.Opcodes.*;

public class PipelineCompiler {
  public static boolean inlineable(Expr.MethodCall expr) {
    switch (expr.methodName) {
      case "map":
      case "filter":
      case "flatMap":
      case "fmap":
        return expr.args.size() <= 1;
      case "sum":
      case "avg":
      case "limit":
      case "skip":
      case "mapWithIndex":
      case "mapi":
      case "size":
      case "each":
      case "min":
      case "max":
      case "join":
      case "sort":
      case "anyMatch":
      case "allMatch":
      case "noneMatch":
        return true;
      default:
        return false;
    }
  }

  public static boolean isCollapsing(String methodName) {
    switch (methodName) {
      case "sum":
      case "avg":
      case "size":
      case "each":
      case "min":
      case "max":
      case "join":
      case "sort":
      case "anyMatch":
      case "allMatch":
      case "noneMatch":
        return true;
      default:
        return false;
    }
  }

  private static InlineFn createInlineFn(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) {
    switch (expr.methodName) {
      case "map":           return new InlineMap(expr, args, methodCompiler);
      case "mapWithIndex":  return new InlineMapWithIndex(expr, args, methodCompiler);
      case "mapi":          return new InlineMapWithIndex(expr, args, methodCompiler);
      case "flatMap":       return new InlineFlatMap(expr, args, methodCompiler);
      case "fmap":          return new InlineFlatMap(expr, args, methodCompiler);
      case "filter":        return new InlineFilter(expr, args, methodCompiler);
      case "sum":           return new InlineAvgSum(expr, methodCompiler, false);
      case "avg":           return new InlineAvgSum(expr, methodCompiler, true);
      case "skip":          return new InlineSkip(expr, args.get(0), methodCompiler);
      case "limit":         return new InlineLimit(expr, args.get(0), methodCompiler);
      case "size":          return new InlineSize(expr, methodCompiler);
      case "each":          return new InlineEach(expr, args, methodCompiler);
      case "min":           return new InlineMinMax(expr, args, methodCompiler, true);
      case "max":           return new InlineMinMax(expr, args, methodCompiler, false);
      case "join":          return new InlineJoin(expr, args, methodCompiler);
      case "sort":          return new InlineSort(expr, args, methodCompiler);
      case "anyMatch":      return new InlineAnyMatch(expr, args, methodCompiler);
      case "allMatch":      return new InlineAllMatch(expr, args, methodCompiler);
      case "noneMatch":     return new InlineNoneMatch(expr, args, methodCompiler);
      default:
        throw new UnsupportedOperationException("Unsupported method " + expr.methodName);
    }
  }

  public static List<InlineFn> createPipeline(List<Expr.MethodCall> exprs, List<List<Expr>> args, MethodCompiler methodCompiler) {
    List<InlineFn> fns = new ArrayList<>();
    for (int i = 0; i < exprs.size(); i++) {
      fns.add(createInlineFn(exprs.get(i), args.get(i), methodCompiler));
    }
    return fns;
  }
  
  // Compile an inline pipeline from the given expressions.
  // Iterator is on the stack.
  public static void compilePipeline(Expr parent, MethodCompiler methodCompiler, List<Expr.MethodCall> exprs, List<InlineFn> fns) {
    // Normally, if a method call is on a type that is unknown we assume the worst and mark it as async.
    // Given that we have already done a runtime check for iterable, we only flag functions as async
    // if they invoke an async closure.
    boolean isAsync = parent.isAsync || exprs.stream().anyMatch(e -> e.args.stream().anyMatch(a -> a.isAsync));

    // If last function returns an Iterator or needs a list to post process then we need to convert back to a list
    InlineFn lastFn        = fns.get(fns.size() - 1);
    boolean  convertToList = !lastFn.isCollapsing() || lastFn.isPostProcessing();

    if (methodCompiler.asyncEnabled() && isAsync) {
      compilePipelineAsync(methodCompiler, exprs, fns, convertToList);
    }
    else {
      compilePipelineSync(methodCompiler, exprs, fns, convertToList);
    }
  }
  
  public static void compilePipelineSync(MethodCompiler methodCompiler, List<Expr.MethodCall> exprs, List<InlineFn> fns, boolean convertToList) {
    // Allocate variable for our iterator
    int iteratorSlot = methodCompiler.stack.allocateSlot(ITERATOR);
    methodCompiler.storeLocal(iteratorSlot);
    
    int resultListSlot = -1;
    if (convertToList) {
      resultListSlot = methodCompiler.stack.allocateSlot(LIST);
      methodCompiler.loadDefaultValue(LIST);
      methodCompiler.storeLocal(resultListSlot);
    }

    fns.forEach(InlineFn::initialise);
    
    Label LOOP = new Label();
    Label END  = new Label();
    Pair<Label,Label> labels = initLabels(fns, LOOP, END);
    Label loopLabel = labels.first;
    Label endLabel  = labels.second;

    methodCompiler.mv.visitLabel(LOOP);              // :LOOP
    fns.forEach(InlineFn::compileLimitCheck);
    methodCompiler.loadLocal(iteratorSlot);
    methodCompiler.invokeMethod(MethodCompiler.ITERATOR_HAS_NEXT_METHOD);
    methodCompiler.popType();
    methodCompiler.mv.visitJumpInsn(IFEQ, endLabel);
    
    methodCompiler.loadLocal(iteratorSlot);
    methodCompiler.invokeMethod(MethodCompiler.ITERATOR_NEXT_METHOD);

    for (int i = 0; i < fns.size(); i++) {
      InlineFn fn = fns.get(i);
      fn.compile(-1, -1, fns, i);
    }

    if (convertToList) {
      methodCompiler.box();
      methodCompiler.loadLocal(resultListSlot);
      methodCompiler.swap();
      methodCompiler.invokeMethod(MethodCompiler.LIST_ADD_METHOD);
      methodCompiler.popVal();     // Don't need result of list.add()
    }
    
    methodCompiler.mv.visitJumpInsn(GOTO, loopLabel);
    methodCompiler.mv.visitLabel(END);               // :END
    
    fns.forEach(InlineFn::finish);
    
    if (convertToList) {
      methodCompiler.loadLocal(resultListSlot);
      fns.forEach(InlineFn::postProcess);
      methodCompiler.stack.freeSlot(resultListSlot);
    }
    else {
      Expr.MethodCall lastExpr = exprs.get(exprs.size() - 1);
      methodCompiler.pushType(lastExpr.type.boxed());
    }
    
    fns.forEach(InlineFn::cleanUp);
    methodCompiler.stack.freeSlot(iteratorSlot);
  }

  private static Pair<Label,Label> initLabels(List<InlineFn> fns, Label LOOP, Label END) {
    // Set start of loop and end of loop labels on each fn.
    // Each fn gets the chance to replace the label with their own one
    // to handle negative skip/limit and flatMap scenarios.
    // For the start label we propagate forward but for the end label
    // we propagate from last to first.
    Label loopLabel = LOOP;
    Label endLabel = END;
    for (int i = 0; i < fns.size(); i++) {
      loopLabel = fns.get(i).setStartLabel(loopLabel);
      endLabel  = fns.get(fns.size() - 1 - i).setEndLabel(endLabel);
    }
    return Pair.of(loopLabel, endLabel);
  }

  public static void compilePipelineAsync(MethodCompiler methodCompiler, List<Expr.MethodCall> exprs, List<InlineFn> fns, boolean convertToList) {
    // Allocate variable for our iterator
    int iteratorSlot = methodCompiler.stack.allocateSlot(ITERATOR);
    methodCompiler.storeLocal(iteratorSlot);

    int valueSlot = methodCompiler.stack.allocateSlot(ANY);
    methodCompiler.loadConst(null);
    methodCompiler.storeLocal(valueSlot);
    
    int resultListSlot = convertToList ? methodCompiler.stack.allocateSlot(LIST) : -1;
    if (convertToList) {
      methodCompiler.loadDefaultValue(LIST);
      methodCompiler.storeLocal(resultListSlot);
    }
    
    int contResultSlot = methodCompiler.stack.allocateSlot(ANY);
    methodCompiler.loadConst(null);
    methodCompiler.storeLocal(contResultSlot);

    fns.forEach(InlineFn::initialise);

    Label CONTINUE = new Label();
    Label LOOP = new Label();
    Label END  = new Label();
    Pair<Label,Label> labels = initLabels(fns, LOOP, END);
    Label loopLabel = labels.first;
    Label endLabel  = labels.second;
    
    Label[] switchLabels = new Label[exprs.size() * 2 + 4 + 1];
    for (int i = 0; i < switchLabels.length; i++) {
      switchLabels[i] = new Label();
    }
    Label defaultLabel = switchLabels[switchLabels.length - 1];
    
    int locationSlot = methodCompiler.stack.allocateSlot(INT);
    methodCompiler.invokeMaybeAsync(true, ANY, 0, exprs.get(0).location, 
      () -> {
        // LOOP:
        methodCompiler.mv.visitLabel(LOOP);
        methodCompiler.loadConst(0);
        methodCompiler.storeLocal(locationSlot);
      },
      () -> {
        // CONTINUE:
        methodCompiler.mv.visitLabel(CONTINUE);
    
        methodCompiler._loadLocal(locationSlot);
        methodCompiler.mv.visitTableSwitchInsn(0, switchLabels.length - 1, defaultLabel, switchLabels);
        int index = 0;
        // case 0:
        methodCompiler.mv.visitLabel(switchLabels[index++]);
        fns.forEach(InlineFn::compileLimitCheck);
        methodCompiler.loadLocal(iteratorSlot);
        methodCompiler.invokeMethod(MethodCompiler.ITERATOR_HAS_NEXT_METHOD);
        methodCompiler.popType();
        methodCompiler.mv.visitJumpInsn(IFEQ, endLabel);
        methodCompiler.mv.visitIincInsn(locationSlot, 2);
        methodCompiler.mv.visitJumpInsn(GOTO, switchLabels[2]);  // Jump to label for next()

        // case 1:  async handler for hasNext()
        methodCompiler.mv.visitLabel(switchLabels[index++]);
        methodCompiler.loadLocal(contResultSlot);
        methodCompiler.checkCast(BOXED_BOOLEAN);
        methodCompiler.unbox();
        methodCompiler.popType();
        methodCompiler.mv.visitJumpInsn(IFEQ, endLabel);
        methodCompiler.mv.visitIincInsn(locationSlot, 1);

        // case 2: next()
        methodCompiler.mv.visitLabel(switchLabels[index++]);
        methodCompiler.loadLocal(iteratorSlot);
        methodCompiler.invokeMethod(MethodCompiler.ITERATOR_NEXT_METHOD);
        methodCompiler.storeLocal(valueSlot);
        methodCompiler.mv.visitIincInsn(locationSlot, 2);
        methodCompiler.mv.visitJumpInsn(GOTO, switchLabels[4]);  // Jump to label for first expr in pipeline

        // case 3: async handler for next()
        methodCompiler.mv.visitLabel(switchLabels[index++]);
        methodCompiler.loadLocal(contResultSlot);
        methodCompiler.storeLocal(valueSlot);
        methodCompiler.mv.visitIincInsn(locationSlot, 1);

        // case 4: for each expr: case index: --> normal, case index+1: async handler
        for (int i = 0; i < fns.size(); i++) {
          InlineFn fn = fns.get(i);
          // sync handler for fn
          methodCompiler.mv.visitLabel(switchLabels[index++]);
          methodCompiler.loadLocal(valueSlot);
          fn.compile(valueSlot, locationSlot, fns, i);
          if (!fn.isCollapsing()) {
            methodCompiler.storeLocal(valueSlot);
          }
          methodCompiler.mv.visitIincInsn(locationSlot, 2);
          methodCompiler.mv.visitJumpInsn(GOTO, switchLabels[index + 1]);  // Jump to next fn
          
          // async handler for fn
          methodCompiler.mv.visitLabel(switchLabels[index++]);
          fn.asyncResumed(contResultSlot, valueSlot);
          if (!fn.isCollapsing()) {
            methodCompiler.storeLocal(valueSlot);
          }
          methodCompiler.mv.visitIincInsn(locationSlot, 1);
        }
        
        // default:
        methodCompiler.mv.visitLabel(switchLabels[index++]);

        if (convertToList) {
          methodCompiler.loadLocal(resultListSlot);
          methodCompiler.loadLocal(valueSlot);
          methodCompiler.invokeMethod(MethodCompiler.LIST_ADD_METHOD);
          methodCompiler.popVal();     // Don't need result of list.add()
        }

        methodCompiler.mv.visitJumpInsn(GOTO, loopLabel);
      });
    
    // If we get here it means we had an async invocation and we have been resumed here.
    // We use the locationSlot to work out where we were up to. The "result" of the continuation
    // will be on the satck we always put it into the resultSlot and the location works out whether
    // the result is a boolean (for a filter operation) or is the mapped value (for map etc).
    methodCompiler._storeLocal(contResultSlot);
    
    // Increment location and jump back to start of loop where switch statement
    // will then route to appropriate label to continue from
    methodCompiler.mv.visitIincInsn(locationSlot, 1);
    methodCompiler.mv.visitJumpInsn(GOTO, CONTINUE);
    
    // END:
    methodCompiler.mv.visitLabel(END);
    
    fns.forEach(InlineFn::finish);
    
    if (convertToList) {
      methodCompiler.loadLocal(resultListSlot);
      fns.forEach(InlineFn::postProcess);
      methodCompiler.stack.freeSlot(resultListSlot);
    }

    fns.forEach(InlineFn::cleanUp);
    methodCompiler.stack.freeSlot(iteratorSlot);
    methodCompiler.stack.freeSlot(contResultSlot);
    methodCompiler.stack.freeSlot(locationSlot);
    methodCompiler.stack.freeSlot(valueSlot);
    
    Expr.MethodCall lastExpr = exprs.get(exprs.size() - 1);
    if (!convertToList) {
      methodCompiler.pushType(lastExpr.type.boxed());
    }
  }

  private static List<JactlType> CLOSURE_STACK_TYPES = Utils.listOf(FUNCTION, CONTINUATION, STRING, INT, ANY);
  
  // Expect on stack: Object arg, MethodHandle closure
  private static void invokeClosure(MethodCompiler methodCompiler, Token location) {
    Utils.checkCast(methodCompiler.mv, FUNCTION);
    methodCompiler.swap();
    methodCompiler.loadNullContinuation();
    methodCompiler.swap();
    methodCompiler.loadConst(location.getSource());
    methodCompiler.swap();
    methodCompiler.loadConst(location.getOffset());
    methodCompiler.swap();
    methodCompiler.invokeMethodHandle(CLOSURE_STACK_TYPES);
  }
  
  //////////////////////////////////////////////////////////////////
  
  public static class InlineFn {
    Expr.MethodCall expr;
    MethodCompiler methodCompiler;
    Label loopLabel;
    Label endLabel;
    InlineFn(Expr.MethodCall expr, MethodCompiler methodCompiler) {
      this.expr = expr;
      this.methodCompiler = methodCompiler;
    }
    boolean isCollapsing() { return false; }   // True for functions like sum that collapse pipeline into a single value
    boolean canBeDirect()  { return false; }   // True for functions like size() that can he a direct, more efficient, implementation
    void initialise() {}
    
    void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {}
    void compileLimitCheck() {}                // Allow limit() to short circuit if limit reached
    void finish() {}
    void cleanUp() {}
    boolean isPostProcessing() { return false; }
    void postProcess() {}
    void storeLocal(int valueSlot) {}
    void asyncResumed(int contResultSlot, int valueSlot) {
      if (!isCollapsing()) {
        // The value object should be on the stack after asyncResumed completes so by default
        // just load it from its existing slot
        methodCompiler.loadLocal(valueSlot);
      }
    }
    Label setStartLabel(Label loop) {
      loopLabel = loop;
      return loop;
    }
    Label setEndLabel(Label end) {
      endLabel = end;
      return end;
    }
  }

  private static abstract class InlineClosureInvoker extends InlineFn {
    int closureSlot = -1;
    boolean isAsyncClosure = false;
    List<Expr> args;
    InlineClosureInvoker(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { 
      super(expr, methodCompiler); 
      this.args = args;
    }
    @Override void initialise() {
      if (!args.isEmpty()) {
        Expr arg;
        if (Utils.isNamedArgs(args)) {
          // Assume single named arg which is the closure
          arg = ((Expr.MapLiteral)args.get(0)).entries.get(0).second;
        }
        else {
          arg = args.get(0);
        }
        isAsyncClosure = arg.isAsync;
        methodCompiler.compile(arg);
        methodCompiler.dupVal();
        closureSlot = methodCompiler.stack.allocateSlot(FUNCTION);
        if (!methodCompiler.peek().is(FUNCTION)) {
          methodCompiler.tryCatch(ClassCastException.class, false,
                   () -> {
                     Utils.checkCast(methodCompiler.mv, FUNCTION);
                     methodCompiler.storeLocal(closureSlot);
                   },
                   () -> {
                     methodCompiler._throwWithClassName(" cannot be cast to function", arg.location);
                   });
        }
        else {
          methodCompiler.storeLocal(closureSlot);
        }
        methodCompiler.popVal();
      }
    }
    @Override void cleanUp() {
      methodCompiler.stack.freeSlot(closureSlot);
    }
  }

  private static class InlineMap extends InlineClosureInvoker {
    InlineMap(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      if (closureSlot >= 0) {
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
    }
    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler.loadLocal(contResultSlot);
    }
  }

  private static class InlineEach extends InlineClosureInvoker {
    InlineEach(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override boolean isCollapsing() { return true; }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      if (closureSlot >= 0) {
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      methodCompiler.popVal();
    }
    @Override void finish() {
      super.finish();
      methodCompiler._loadConst(null);
    }
  }

  private static class InlineMapWithIndex extends InlineClosureInvoker {
    int indexSlot = -1;
    InlineMapWithIndex(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override void initialise() {
      super.initialise();
      indexSlot = methodCompiler.stack.allocateSlot(INT);
      methodCompiler.loadConst(0);
      methodCompiler.storeLocal(indexSlot);
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      methodCompiler.mv.visitTypeInsn(NEW, "java/util/ArrayList");
      methodCompiler.mv.visitInsn(DUP);
      methodCompiler._loadConst(2);
      methodCompiler.mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V", false);
      methodCompiler.mv.visitInsn(DUP_X1);
      methodCompiler.mv.visitInsn(SWAP);
      methodCompiler.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
      methodCompiler.mv.visitInsn(POP);
      methodCompiler.mv.visitInsn(DUP);
      methodCompiler.loadLocal(indexSlot);
      methodCompiler.box();
      methodCompiler.popType(2);
      methodCompiler.mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
      methodCompiler.mv.visitInsn(POP);
      methodCompiler.pushType(ANY);
      if (closureSlot >= 0) {
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      methodCompiler.mv.visitIincInsn(indexSlot, 1);
    }
    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler.loadLocal(contResultSlot);
      methodCompiler.mv.visitIincInsn(indexSlot, 1);
    }

    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler.stack.freeSlot(indexSlot);
    }
  }

  private static class InlineFlatMap extends InlineClosureInvoker {
    Label subLoop = new Label();
    Label asyncCont = new Label();
    int subIterSlot = -1;
    InlineFlatMap(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override void initialise() {
      super.initialise();
      subIterSlot = methodCompiler.stack.allocateSlot(ITERATOR);
      methodCompiler.loadConst(null);
      methodCompiler.storeLocal(subIterSlot);
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      if (closureSlot >= 0) {
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      methodCompiler.mv.visitLabel(asyncCont);    // :ASYNC CONT
      methodCompiler.invokeMethod(RuntimeUtils.CREATE_ITERATOR_FLATMAP_METHOD);
      methodCompiler.storeLocal(subIterSlot);
      
      methodCompiler.mv.visitLabel(subLoop);      // :SUB LOOP
      // Set location since we could have jumped to this label and location will not have been updated
      if (locationSlot != -1) {
        methodCompiler.loadConst(4 + idx * 2);
        methodCompiler.storeLocal(locationSlot);
      }
      
      // Need to allow for a subsequent limit in the pipeline to short circuit us
      for (int i = idx + 1; i < fns.size(); i++) {
        fns.get(i).compileLimitCheck();
      }
      methodCompiler.loadLocal(subIterSlot);
      methodCompiler.invokeMethod(MethodCompiler.ITERATOR_HAS_NEXT_METHOD);
      methodCompiler.popType();
      // Jump back to top of loop for next value if this sub iter has run out
      methodCompiler.mv.visitJumpInsn(IFEQ, loopLabel);
      methodCompiler.loadLocal(subIterSlot);
      methodCompiler.invokeMethod(MethodCompiler.ITERATOR_NEXT_METHOD);
    }
    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler.loadLocal(contResultSlot);
      methodCompiler.mv.visitJumpInsn(GOTO, asyncCont);
    }
    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler.stack.freeSlot(subIterSlot);
    }
    @Override Label setStartLabel(Label loop) {
      loopLabel = loop;
      return subLoop;
    }
  }

  private static class InlineFilter extends InlineClosureInvoker {
    InlineFilter(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      methodCompiler.dupVal();
      if (closureSlot >= 0) {
        // Invoke closure and check truthiness of its result
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      doFilter();
    }

    private void doFilter() {
      methodCompiler.loadConst(false);
      methodCompiler.invokeMethod(RuntimeUtils.IS_TRUTH_METHOD);
      methodCompiler.popType();
      Label FILTER_PASSED = new Label();
      methodCompiler.mv.visitJumpInsn(IFNE, FILTER_PASSED);
      methodCompiler._popVal();
      methodCompiler.mv.visitJumpInsn(GOTO, loopLabel);
      methodCompiler.mv.visitLabel(FILTER_PASSED);
    }

    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler.loadLocal(valueSlot);
      methodCompiler.loadLocal(contResultSlot);
      doFilter();
    }
  }
  
  private abstract static class InlineAnyAllNoneMatch extends InlineClosureInvoker {
    int resultSlot = -1;
    InlineAnyAllNoneMatch(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler);}
    abstract boolean defaultValue();   // default result
    abstract boolean resultOnBreak();  // result if we break out of loop early
    abstract int jumpInstruction();    // jump instruction that determines whether we continue
    @Override void initialise() {
      super.initialise();
      resultSlot = methodCompiler.stack.allocateSlot(BOOLEAN);
      methodCompiler.loadConst(defaultValue());
      methodCompiler.storeLocal(resultSlot);
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      if (closureSlot != -1) {
        // Invoke closure and check truthiness of its result
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      checkResult();
    }
    private void checkResult() {
      methodCompiler.loadConst(false);
      methodCompiler.invokeMethod(RuntimeUtils.IS_TRUTH_METHOD);
      methodCompiler.popType();
      Label CONTINUE = new Label();
      methodCompiler.mv.visitJumpInsn(jumpInstruction(), CONTINUE);
      methodCompiler.loadConst(resultOnBreak());
      methodCompiler.storeLocal(resultSlot);
      methodCompiler.mv.visitJumpInsn(GOTO, endLabel);
      methodCompiler.mv.visitLabel(CONTINUE);
    }
    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler.loadLocal(contResultSlot);
      checkResult();
    }
    @Override boolean isCollapsing() { return true; }
    @Override void finish() {
      methodCompiler.loadLocal(resultSlot);
      methodCompiler.box();
      methodCompiler.popType();
    }
    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler.stack.freeSlot(resultSlot);
    }
  }
  
  private static class InlineAnyMatch extends InlineAnyAllNoneMatch {
    InlineAnyMatch(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override boolean defaultValue()  { return false; }
    @Override boolean resultOnBreak() { return true; }
    @Override int jumpInstruction()   { return IFEQ; }
  }

  private static class InlineAllMatch extends InlineAnyAllNoneMatch {
    InlineAllMatch(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override boolean defaultValue()  { return true; }
    @Override boolean resultOnBreak() { return false; }
    @Override int jumpInstruction()   { return IFNE; }
  }
  
  private static class InlineNoneMatch extends InlineAnyAllNoneMatch {
    InlineNoneMatch(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override boolean defaultValue()  { return true; }
    @Override boolean resultOnBreak() { return false; }
    @Override int jumpInstruction()   { return IFEQ; }
  }
  
  private static class InlineAvgSum extends InlineFn {
    int sumSlot   = -1;
    int countSlot = -1;
    boolean isAvg;
    InlineAvgSum(Expr.MethodCall expr, MethodCompiler methodCompiler, boolean isAvg) { super(expr, methodCompiler); this.isAvg = isAvg; }
    @Override boolean isCollapsing() { return true; }
    @Override void initialise() {
      sumSlot = methodCompiler.stack.allocateSlot(ANY);
      methodCompiler.loadConst(0);
      methodCompiler.box();
      methodCompiler.storeLocal(sumSlot);
      if (isAvg) {
        countSlot = methodCompiler.stack.allocateSlot(LONG);
        methodCompiler.loadConst(0L);
        methodCompiler.storeLocal(countSlot);
      }
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      methodCompiler.loadLocal(sumSlot);
      methodCompiler.swap();
      methodCompiler.loadLocation(expr.location);
      methodCompiler.invokeMethod(Reducer.ADD_NUMBERS_METHOD);
      methodCompiler.storeLocal(sumSlot);
      if (isAvg) {
        methodCompiler.loadLocal(countSlot);
        methodCompiler._loadConst(1L);
        methodCompiler.mv.visitInsn(LADD);
        methodCompiler.storeLocal(countSlot);
      }
    }
    @Override void finish() {
      if (isAvg) {
        methodCompiler.loadLocal(sumSlot);
        methodCompiler.loadLocal(countSlot);
        methodCompiler.loadLocation(expr.location);
        methodCompiler.invokeMethod(RuntimeUtils.CALCULATE_AVERAGE);
        methodCompiler.popType();
      }
      else {
        methodCompiler._loadLocal(sumSlot);
      }
    }
    @Override void cleanUp() {
      methodCompiler.stack.freeSlot(sumSlot);
      methodCompiler.stack.freeSlot(countSlot);
    }
  }

  private static class InlineSort extends InlineClosureInvoker {
    InlineSort(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, args, methodCompiler); }
    @Override boolean isPostProcessing() { return true; }
    @Override void postProcess() {
      if (closureSlot == -1) {
        methodCompiler.loadNullContinuation();
        methodCompiler.loadLocation(expr.location);
        methodCompiler.loadConst(null);
        methodCompiler.invokeMethod(BuiltinFunctions.LIST_SORT);
      }
      else {
        methodCompiler.invokeMaybeAsync(expr.isAsync, LIST, 1, expr.location,
                                        () -> {
                                          methodCompiler.loadNullContinuation();
                                          methodCompiler.loadLocation(expr.location);
                                          methodCompiler.loadLocal(closureSlot);
                                        },
                                        () -> methodCompiler.invokeMethod(BuiltinFunctions.LIST_SORT));
      }
    }
  }
  
  private static class InlineJoin extends InlineFn {
    int bufferSlot = -1;
    int joinSlot   = -1;
    int isFirstSlot = -1;
    List<Expr> args;
    InlineJoin(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler) { super(expr, methodCompiler); this.args = args; }
    @Override boolean isCollapsing() { return true; }
    @Override void initialise() {
      bufferSlot = methodCompiler.stack.allocateSlot(ANY);
      methodCompiler.mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodCompiler.mv.visitInsn(DUP);
      methodCompiler.mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodCompiler._storeLocal(bufferSlot);
      if (!args.isEmpty()) {
        isFirstSlot = methodCompiler.stack.allocateSlot(BOOLEAN);
        methodCompiler.loadConst(true);
        methodCompiler.storeLocal(isFirstSlot);
        methodCompiler.desiredType = STRING;
        Expr arg = args.get(0);
        methodCompiler.compile(arg);
        methodCompiler.convertTo(STRING, arg, arg.couldBeNull, arg.location);
        joinSlot = methodCompiler.stack.allocateSlot(STRING);
        methodCompiler.storeLocal(joinSlot);
      }
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      methodCompiler.invokeMethod(RuntimeUtils.TO_STRING_METHOD);
      methodCompiler.loadLocal(bufferSlot);
      methodCompiler.mv.visitTypeInsn(CHECKCAST, "java/lang/StringBuilder");
      if (joinSlot != -1) {
        methodCompiler._loadLocal(isFirstSlot);
        Label IS_FIRST = new Label();
        methodCompiler.mv.visitJumpInsn(IFNE, IS_FIRST);
        methodCompiler._loadLocal(joinSlot);
        methodCompiler.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        methodCompiler.mv.visitLabel(IS_FIRST);    // :IS_FIRST
      }
      methodCompiler.swap();
      methodCompiler.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      methodCompiler.mv.visitInsn(POP);
      methodCompiler.popType(2);
      if (isFirstSlot != -1) {
        methodCompiler.loadConst(false);
        methodCompiler.storeLocal(isFirstSlot);
      }
    }
    @Override void finish() {
      methodCompiler._loadLocal(bufferSlot);
      methodCompiler.mv.visitTypeInsn(CHECKCAST, "java/lang/StringBuilder");
      methodCompiler.mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }
    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler.stack.freeSlot(bufferSlot);
      methodCompiler.stack.freeSlot(joinSlot);
      methodCompiler.stack.freeSlot(isFirstSlot);
    }
  }

  private static class InlineMinMax extends InlineClosureInvoker {
    int minMaxValueSlot;
    int minMaxObjectSlot;
    boolean isMin;
    Label asyncCont = new Label();
    InlineMinMax(Expr.MethodCall expr, List<Expr> args, MethodCompiler methodCompiler, boolean isMin) { super(expr, args, methodCompiler); this.isMin = isMin; }
    @Override boolean isCollapsing() { return true; }
    @Override void initialise() {
      super.initialise();
      minMaxValueSlot  = methodCompiler.stack.allocateSlot(ANY);
      methodCompiler.loadConst(null);
      methodCompiler.storeLocal(minMaxValueSlot);
      minMaxObjectSlot = methodCompiler.stack.allocateSlot(ANY);
      methodCompiler.loadConst(null);
      methodCompiler.storeLocal(minMaxObjectSlot);
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      if (valueSlot == -1) {
        methodCompiler.dupVal();
      }
      if (closureSlot != -1) {
        methodCompiler.loadLocal(closureSlot);
        invokeClosure(methodCompiler, expr.location);
      }
      methodCompiler.mv.visitLabel(asyncCont);
      if (locationSlot != -1) {
        methodCompiler.loadConst(4 + idx * 2);
        methodCompiler.storeLocal(locationSlot);
      }
      if (valueSlot != -1) {
        methodCompiler.loadLocal(valueSlot);
        methodCompiler.swap();
      }
      Label STORE_NEW_OBJ_VALUE = new Label();
      methodCompiler._loadLocal(minMaxValueSlot);
      methodCompiler.mv.visitJumpInsn(IFNULL, STORE_NEW_OBJ_VALUE);
      methodCompiler.dupVal();
      methodCompiler.loadLocal(minMaxValueSlot);
      methodCompiler.loadLocation(expr.location);
      methodCompiler.invokeMethod(RuntimeUtils.COMPARE_TO_METHOD);
      methodCompiler.popType();
      Label END = new Label();
      methodCompiler.mv.visitJumpInsn(isMin ? IFLT : IFGT, STORE_NEW_OBJ_VALUE);
      methodCompiler._popVal();      
      methodCompiler._popVal();      
      methodCompiler.mv.visitJumpInsn(GOTO, END);
      methodCompiler.mv.visitLabel(STORE_NEW_OBJ_VALUE);   // :STORE_NEW_OBJ_VALUE
      methodCompiler.storeLocal(minMaxValueSlot);
      methodCompiler.storeLocal(minMaxObjectSlot);
      methodCompiler.mv.visitLabel(END);                   // :END
    }
    @Override void asyncResumed(int contResultSlot, int valueSlot) {
      methodCompiler._loadLocal(contResultSlot);
      methodCompiler.mv.visitJumpInsn(GOTO, asyncCont);
    }
    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler._loadLocal(minMaxObjectSlot);
      methodCompiler.stack.freeSlot(minMaxValueSlot);
      methodCompiler.stack.freeSlot(minMaxObjectSlot);
    }
  }

  private static class InlineSize extends InlineFn {
    int sizeSlot = -1;
    InlineSize(Expr.MethodCall expr, MethodCompiler methodCompiler) { super(expr, methodCompiler); }
    @Override boolean isCollapsing() { return true; }
    @Override boolean canBeDirect()  { return true; }
    @Override void initialise() {
      super.initialise();
      sizeSlot = methodCompiler.stack.allocateSlot(LONG);
      methodCompiler.loadConst(0L);
      methodCompiler.storeLocal(sizeSlot);
    }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      methodCompiler.popVal();
      methodCompiler._loadLocal(sizeSlot);
      methodCompiler.mv.visitInsn(LCONST_1);
      methodCompiler.mv.visitInsn(LADD);
      methodCompiler._storeLocal(sizeSlot);
    }
    @Override void finish() {
      methodCompiler.loadLocal(sizeSlot);
      methodCompiler.box();
      methodCompiler.popType();
    }
    @Override void cleanUp() {
      super.cleanUp();
      methodCompiler.stack.freeSlot(sizeSlot);
    }
  }

  private static abstract class InlineSkipLimit extends InlineFn {
    int argSlot;
    int isNegativeSlot = -1;
    int bufferSlot = -1;
    boolean constNegative = false;
    boolean constPositive = false;
    Expr    argExpr;
    InlineSkipLimit(Expr.MethodCall expr, Expr argExpr, MethodCompiler methodCompiler) {
      super(expr,  methodCompiler);
      this.argExpr = argExpr;
    }
    @Override void initialise() { 
      boolean isLimit = this instanceof InlineLimit;
      methodCompiler.desiredType = INT;
      methodCompiler.compile(argExpr);
      methodCompiler.convertTo(INT, argExpr, argExpr.couldBeNull, argExpr.location);
      argSlot = methodCompiler.stack.allocateSlot(INT);
      methodCompiler.storeLocal(argSlot);
      constNegative = argExpr.isConst && argExpr.constValue instanceof Number && ((Number) argExpr.constValue).longValue() < 0;
      constPositive = argExpr.isConst && argExpr.constValue instanceof Number && ((Number) argExpr.constValue).longValue() >= 0;
      
      // We need a CircularBuffer if we have a negative value for the argument so we can buffer
      // values until we know we can let them through.
      // For limit(-x) we buffer x+1 elements and when buffer is full we let value out of buffer
      // for every new value. Once we get to the last value we throw away the remaining values
      // in the buffer. We use x+1 to make end of iteration easier to deal with.
      // For skip(-x) we buffer x elements and only release elements at the end since at the end
      // the last x elements are the only ones we will let through.
      if (constNegative) {
        bufferSlot = methodCompiler.stack.allocateSlot(ANY);
        methodCompiler.mv.visitTypeInsn(NEW, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.mv.visitInsn(DUP);
        methodCompiler._loadConst(-((Number)argExpr.constValue).intValue() + (isLimit ? 1 : 0));
        methodCompiler.mv.visitMethodInsn(INVOKESPECIAL, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME, "<init>", "(I)V", false);
        methodCompiler._storeLocal(bufferSlot);
      }
      else if (!argExpr.isConst) {
        bufferSlot = methodCompiler.stack.allocateSlot(ANY);
        methodCompiler._loadLocal(argSlot);
        methodCompiler.mv.visitInsn(ICONST_M1);
        methodCompiler.mv.visitInsn(ISHR);       // We now have 1 for negative value or 0 for postive value
        methodCompiler.mv.visitInsn(DUP);
        isNegativeSlot = methodCompiler.stack.allocateSlot(BOOLEAN);
        methodCompiler._storeLocal(isNegativeSlot);
        Label isPositive = new Label();
        methodCompiler.mv.visitJumpInsn(IFEQ, isPositive);
        // We have a negative arg
        methodCompiler.mv.visitTypeInsn(NEW, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.mv.visitInsn(DUP);
        methodCompiler._loadLocal(argSlot);
        methodCompiler.mv.visitInsn(INEG);
        if (isLimit) {
          methodCompiler._loadConst(1);
          methodCompiler.mv.visitInsn(IADD);
        }
        methodCompiler.mv.visitMethodInsn(INVOKESPECIAL, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME, "<init>", "(I)V", false);
        methodCompiler._storeLocal(bufferSlot);
        Label finish = new Label();
        methodCompiler.mv.visitJumpInsn(GOTO, finish);
        methodCompiler.mv.visitLabel(isPositive);  // :isPositive
        methodCompiler.loadConst(null);
        methodCompiler.storeLocal(bufferSlot);
        methodCompiler.mv.visitLabel(finish);      // :finish
      }
    }
    @Override void cleanUp() {
      methodCompiler.stack.freeSlot(argSlot);
      methodCompiler.stack.freeSlot(bufferSlot);
      methodCompiler.stack.freeSlot(isNegativeSlot);
    }
  }
  
  private static class InlineSkip extends InlineSkipLimit {
    Label SUBLOOP = new Label();
    InlineSkip(Expr.MethodCall expr, Expr argExpr, MethodCompiler methodCompiler) { super(expr, argExpr, methodCompiler); }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      Label IS_POSITIVE = new Label();
      Label NEXT = new Label();
      if (!argExpr.isConst) {
        // Need to check if we have a negative value
        methodCompiler._loadLocal(isNegativeSlot);
        methodCompiler.mv.visitJumpInsn(IFEQ, IS_POSITIVE);
      }
      if (constNegative || !argExpr.isConst) {
        // Put value into circular buffer and jump back to top of loop.
        // When main loop finishes it will jump to SUBLOOP and, we will then feed
        // values from circular buffer to subsequent steps in pipeline
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.swap();
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_ADD_METHOD);
        methodCompiler.mv.visitJumpInsn(GOTO, loopLabel);
        
        // Main loop will jump here once finished. Any subsequent fns will jump
        // back to main loop (if they need to) and it then sees it has finished 
        // and jumps here again.
        methodCompiler.mv.visitLabel(SUBLOOP);             // :SUBLOOP
        if (locationSlot != -1) {
          methodCompiler.loadConst(4 + idx * 2);
          methodCompiler.storeLocal(locationSlot);
        }
        
        if (!constNegative) {
          methodCompiler._loadLocal(isNegativeSlot);
          methodCompiler.mv.visitJumpInsn(IFEQ, endLabel);   // positive skip so nothing to do
        }
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_SIZE_METHOD);
        methodCompiler.mv.visitJumpInsn(IFLE, endLabel);
        methodCompiler.popType();
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_REMOVE_METHOD);
        methodCompiler.mv.visitJumpInsn(GOTO, NEXT);
      }
      if (!constNegative) {
        methodCompiler.mv.visitLabel(IS_POSITIVE);    // :IS_POSITIVE
        methodCompiler.mv.visitIincInsn(argSlot, -1);
        methodCompiler._loadLocal(argSlot);
        Label NOT_SKIPPED = new Label();
        methodCompiler.mv.visitJumpInsn(IFLT, NOT_SKIPPED);
        methodCompiler.mv.visitInsn(POP);
        methodCompiler.mv.visitJumpInsn(GOTO, loopLabel);
        methodCompiler.mv.visitLabel(NOT_SKIPPED);
      }
      methodCompiler.mv.visitLabel(NEXT);
    }
    @Override Label setEndLabel(Label loop) {
      super.setEndLabel(loop);
      // If we potentially have a negative arg then we need parent loop to jump to us
      // when done so that we can then release the buffered values from our CircularBuffer
      if (constNegative || !argExpr.isConst) {
        return SUBLOOP;
      }
      return loop;
    }
  }

  private static class InlineLimit extends InlineSkipLimit {
    InlineLimit(Expr.MethodCall expr, Expr argExpr, MethodCompiler methodCompiler) { super(expr, argExpr, methodCompiler); }
    @Override void compile(int valueSlot, int locationSlot, List<InlineFn> fns, int idx) {
      Label IS_POSITIVE = new Label();
      Label NEXT = new Label();
      if (!argExpr.isConst) {
        // Need to check if we have a negative value
        methodCompiler._loadLocal(isNegativeSlot);
        methodCompiler.mv.visitJumpInsn(IFEQ, IS_POSITIVE);
      }
      if (constNegative || !argExpr.isConst) {
        // For negative limit we always add to our CircularBuffer and when
        // buffer is full, for every value in, we allow one value out. We
        // make our buffer size n+1 so we can always add and then check 
        // after if we should remove rather than having to check size first.
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.swap();
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_ADD_METHOD);
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_FREE_METHOD);
        methodCompiler.mv.visitJumpInsn(IFNE, loopLabel);      // Jump back to top of loop if still space in buffer
        methodCompiler.popType();
        methodCompiler.loadLocal(bufferSlot);
        methodCompiler.mv.visitTypeInsn(CHECKCAST, CircularBuffer.CIRCULAR_BUFFER_INTERNAL_NAME);
        methodCompiler.invokeMethod(CircularBuffer.CIRCULAR_BUFFER_REMOVE_METHOD);
        methodCompiler.mv.visitJumpInsn(GOTO, NEXT);
      }
      if (!constNegative) {
        methodCompiler.mv.visitLabel(IS_POSITIVE);    // :IS_POSITIVE
        methodCompiler.mv.visitIincInsn(argSlot, -1);
        methodCompiler._loadLocal(argSlot);
        Label NOT_LIMITED = new Label();
        methodCompiler.mv.visitJumpInsn(IFGE, NOT_LIMITED);
        methodCompiler.mv.visitInsn(POP);
        methodCompiler.mv.visitJumpInsn(GOTO, endLabel);
        methodCompiler.mv.visitLabel(NOT_LIMITED);
      }
      methodCompiler.mv.visitLabel(NEXT);
    }
    @Override void compileLimitCheck() {
      if (!constNegative) {
        Label IS_NEGATIVE = new Label();
        if (!argExpr.isConst) {
          methodCompiler._loadLocal(isNegativeSlot);
          methodCompiler.mv.visitJumpInsn(IFNE, IS_NEGATIVE);
        }
        methodCompiler._loadLocal(argSlot);
        methodCompiler.mv.visitJumpInsn(IFLE, endLabel);
        methodCompiler.mv.visitLabel(IS_NEGATIVE);
      }
    }
  }
}
