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

package jacsal;

import jacsal.runtime.*;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static jacsal.JacsalType.*;
import static jacsal.JacsalType.BOOLEAN;
import static jacsal.JacsalType.DECIMAL;
import static jacsal.JacsalType.DOUBLE;
import static jacsal.JacsalType.INT;
import static jacsal.JacsalType.LIST;
import static jacsal.JacsalType.LONG;
import static jacsal.JacsalType.LONG_ARR;
import static jacsal.JacsalType.MAP;
import static jacsal.JacsalType.OBJECT_ARR;
import static jacsal.JacsalType.STRING;
import static jacsal.JacsalType.STRING_ARR;
import static jacsal.TokenType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.NEW;

/**
 * This class is the class that does most of the work from a compilation point of view.
 * It converts the AST for a function/method into byte code.
 * <p>
 * <h2>Type Stack</h2>
 * To compile into byte code we keep track of the type of the objects on the JVM stack
 * with a type stack that tries to mirror what would be on the JVM stack at the point
 * where the code is being generated.
 * <p>
 * Methods with names such as loadConst(), loadVar(), storeVar(), dupVal(), popVal(),
 * etc all manipulate the type stack. There are also other versions of some of these
 * methods that begin with '_' such as _loadConst(), _dupVal(), _popVal() that don't
 * touch the type stack. All methods beginning with '_' leave the type stack unchanged.
 * <p>
 * <b>Note:</b> when generating code that has jump/branches we need to be careful not
 * to have leave additional types on the type stack. For example, consider when we are
 * generating code for something like this:
 * <pre>
 *   arr.length() == 0 ? null : arr[0]</pre>
 * We will generate something like this:
 * <pre>
 *   compile("arr.length() == 0")           // puts boolean onto type stack
 *   Label ifFalse = new Label()
 *   mv.visitJumpInsn(IFEQ, ifFalse)
 *   pop();                                 // pop boolean type off stack
 *   compile("null")                        // pushes ANY onto type stack
 *   Label end = new Label()
 *   mv.visitJumpInsn(GOTO, end)
 *   mv.visitLabel(ifFalse)  //:ifFalse//
 *   compile("arr[0]")                      // pushes ANY onto type stack
 *   mv.visitLabel(end)      //:end//
 * </pre>
 * Now we have two ANY types on the type stack even though at runtime there will only be
 * one value. We need to make sure to pop the extra ANY type off the type stack before
 * continuing:
 * <pre>
 *   pop();</pre>
 * <p>
 * <h2>Local Variables</h2>
 * This class tracks what each local variable slot is being used for.
 * Slot 0 will be the instance ("this") if the method is not a static method. Then
 * the next slots will be for any HeapLocalsthat get passed in each time and then after
 * those the next slots are for the actual method parameters. Local variables then take
 * slots as they are declared and release them at the end of their scope. Temporary
 * slots are allocated and freed as needed.
 * <p>
 * Most types require just one slot but long and double types take two slots. This
 * class tracks the types in each slot and hides the callers having to know how many
 * slots are needed.
 * <p>
 * <h2>Method Invocation</h2>
 * Each function/method will have two versions of the method generated:
 * <ul>
 *   <li>The normal method</li>
 *   <li>A varargs wrapper method that takes care of invocations where not all parameter values
 *       are being passed in and also takes care of named argument handling</li>
 * </ul>
 * When passing methods as values or invoking them "dynamically" (when we don't know the type at
 * compile time) we actually use a MethodHandle. The MethodHandle is generated bound to the varargs
 * wrapper method and not the normal method because we don't know at compile time what the parameter
 * types will be.
 * <p>
 * If the method is an async method (invokes code that is async or potentially async) then we also
 * generate a continuation wrapper method for both the normal method and the varargs wrapper.
 * This continuation wrapper is used when continuing execution after being suspended while waiting
 * for an async operation to complete. Its only job is to extract the parameters from the Continuation
 * object and then invoke the real method (normal or varargs wrapper as appropriate).
 * <p>
 * If the function closes over variables declared in an outer scope then these variables are passed
 * in as additional arguments of type HeapLocal and the MethodHandle is bound to these HeapLocals
 * at the time it is created so that the signature of the MethodHandle is always the same.
 * <p>
 * If we look at a concrete example, imagine we have a class X with a single method f() like this:
 * <pre>
 *   class X {
 *     String f(List strings, String separator = '') { strings.join(separator) }
 *   }</pre>
 * We will generate these methods:
 * <pre>
 *   String f(List,String)
 *   Object f$$w(Continuation cont, String source, int offset, Object[] args)</pre>
 * Note that wrapper methods always have the exact same signature, irrespective of the method being
 * wrapped. This means that when invoking them through the MethodHandle we always know what the argument
 * types are:
 * <ul>
 *   <li><b>cont:</b> the Continuation object. This will always be null unless resuming after an async suspend.</li>
 *   <li><b>source:</b> the source code.</li>
 *   <li><b>offset:</b> the offset into the source code where the invocation is (for error reporting).</li>
 *   <li><b>args:</b> the argument values as an Object[]. We also support the args being passed in as a List
 *       in which case args will have length 1 and arg[0] will be the List. Named args are passed in as a Map
 *       and again we detect this by checking for args of length 1 and arg[0] being the Map.</li>
 * </ul>
 * We always pass a Continuation object since we don't know whether the function we will be calling is async
 * or not when invoking via a MethodHandle.
 * <p>
 * The return type of the varargs wrapper function is always of type Object, again because we want to use
 * MethodHandle.invokeExact() for efficient method invocation and the return type is used to determine how
 * to invoke the method.
 * <p>
 * If the function is async then we also generate continuation wrapper functions:
 * <pre>
 *   String f(Continuation cont, List strings, String separator)
 *   String f$$c(Continuation cont)    {
 *     return f(cont,cont.localObjects[2],cont.localObject[3]);
 *   }
 *   Object f$$w(Continuation cont, String source, int offset, Object[] args)
 *   Object f$$w$$c(Continuation cont) {
 *     return f$$w(cont,cont.localObjects[2],cont.localObjects[3],cont.localObjects[4]);
 *   }</pre>
 * When invoking a function, we will invoke the actual "normal" function only when we have values for all
 * of its parameters. If there are missing values (due to optional parameters) or if we are invoking via
 * named args invocation then we will invoke the varargs wrapper function instead. If we don't know what
 * function we are invoking at compile time (invoking via a variable or field that contains a MethodHandle)
 * then we invoke using the MethodHandle that will point to a varargs wrapper function.
 */
public class MethodCompiler implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  private final ClassCompiler classCompiler;
  private final MethodVisitor mv;
  private final Expr.FunDecl  methodFunDecl;
  private       int           currentLineNum = 0;
  private       int           minimumSlot = 0;
  private       int           continuationVar = -1;

  private static class LocalEntry { JacsalType type; int refCount = 1; }

  private final List<LocalEntry> locals         = new ArrayList<>();

  private List<Label>    asyncLocations         = new ArrayList<>();
  private List<Runnable> asyncStateRestorations = new ArrayList<>();

  private static final BigDecimal DECIMAL_MINUS_1 = BigDecimal.valueOf(-1);

  // As we generate the byte code we keep a stack where we track the type of the value that
  // would currently be on the JVM stack at that point in the generated code. This allows
  // us to know when to do appropriate conversions.
  // When doing async calls we need to move what ever is on the real stack to local var slots
  // because exception catching discards what is on the stack. We track which slots correspond
  // to the entries in our virtual stack. When the entry is on the real stack we store a value
  // of -1. We also track how many references to each slot exist on our virtual stack. This is
  // to allow us to dup() values without having to actually do anything except increment the
  // reference count. We release the local var slot once the reference count goes to 0.
  private static class StackEntry {
    JacsalType type;
    int slot = -1;
    StackEntry(JacsalType type) { this.type = type; }
    @Override public boolean equals(Object obj) {
      return ((StackEntry)obj).type.equals(type) && ((StackEntry)obj).slot == slot;
    }
  }
  private Deque<StackEntry> stack       = new ArrayDeque<>();

  private Deque<TryCatch> tryCatches = new ArrayDeque<>();

  MethodCompiler(ClassCompiler classCompiler, Expr.FunDecl funDecl, MethodVisitor mv) {
    this.classCompiler = classCompiler;
    this.methodFunDecl = funDecl;
    this.mv = mv;
  }

  void compile() {
    // Allow room for object instance at idx 0 and optional heap vars array at idx 1.
    // We know we need heap vars array passed to us if our top level access level is
    // less than our current level (i.e. we access vars from one of our parents).
    if (!methodFunDecl.isStatic()) {
      allocateSlot(JacsalType.createInstance(classCompiler.classDecl.classDescriptor));  // this
    }

    // Allocate slots for heap local vars passed to us as implicit parameters from
    // parent function
    methodFunDecl.heapLocalParams.forEach((name, varDecl) -> defineVar(varDecl));

    // If async or wrapper then allocate slot for the Continuation
    if (methodFunDecl.isWrapper || methodFunDecl.functionDescriptor.isAsync) {
      continuationVar = allocateSlot(CONTINUATION);
    }

    // Process parameters in order to allocate their slots in the right order
    methodFunDecl.parameters.forEach(this::compile);

    // Now allocate any additional slot that is needed for parameters that have been
    // converted into heap locals. The original slot will have the stack local value
    // and the second slot will have the HeapLocal with the original value then stored
    // in it.
    methodFunDecl.parameters.stream()
                            .filter(p -> p.declExpr.isHeapLocal)
                            .filter(p -> !p.declExpr.isPassedAsHeapLocal)
                            .forEach(this::promoteToHeapLocal);

    // Check to see if we have been resumed after a suspend
    Label isContinuation = new Label();
    if (methodFunDecl.functionDescriptor.isAsync) {
      _loadLocal(continuationVar);
      mv.visitJumpInsn(IFNONNULL, isContinuation);
    }

    // Compile statements.
    compile(methodFunDecl.block);

    if (methodFunDecl.functionDescriptor.isAsync) {
      mv.visitLabel(isContinuation);      // :isContinuation
      Label defaultLabel = new Label();
      _loadLocal(continuationVar);
      mv.visitFieldInsn(GETFIELD, "jacsal/runtime/Continuation", "methodLocation", "I");
      Label[] labels = IntStream.range(0, asyncLocations.size()).mapToObj(i -> new Label()).toArray(Label[]::new);
      mv.visitLookupSwitchInsn(defaultLabel,
                               IntStream.range(0, asyncLocations.size()).toArray(),
                               labels);
      for (int i = 0; i < asyncLocations.size(); i++) {
        mv.visitLabel(labels[i]);
        asyncStateRestorations.get(i).run();
        mv.visitJumpInsn(GOTO, asyncLocations.get(i));
      }
      mv.visitLabel(defaultLabel);
      throwError("Internal error: Invalid location in continuation", methodFunDecl.location);
    }

//    if (classCompiler.debug()) {
//      mv.visitEnd();
//      classCompiler.cv.visitEnd();
//    }
    mv.visitMaxs(0, 0);

    check(stack.isEmpty(), "non-empty stack at end of method " + methodFunDecl.functionDescriptor.implementingMethod + ". Type stack = " + stack);
  }

  /////////////////////////////////////////////

  //= Stmt

  private int indent = 0;

  private Void compile(Stmt stmt) {
    try {
      if (stmt == null) {
        return null;
      }
      if (classCompiler.annotate()) {
        // Annotate output with where we are by loading and immediately popping a string
        _loadConst("  ".repeat(indent++) + "==> " + stmt.getClass().getName() + "<" + stmt.hashCode() + ">");
        mv.visitInsn(POP);
      }
      return stmt.accept(this);
    }
    finally {
      if (classCompiler.annotate()) {
        _loadConst("  ".repeat(--indent) + "<== " + stmt.getClass().getName() + "<" + stmt.hashCode() + ">");
        mv.visitInsn(POP);
      }
    }
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    stmt.stmts.forEach(this::compile);
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    // Compile all statements except filter out any parameter statements
    // as they will have already been compiled
    stmt.stmts.stmts.stream()
                    .filter(st -> !isParam(st))
                    .forEach(this::compile);

    // Emit description of local variables for debugger
    if (stmt.variables.size() > 0) {
      Label endBlock = new Label();
      mv.visitLabel(endBlock);
      stmt.variables.values().forEach(v -> undefineVar(v, endBlock));
    }
    return null;
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    compile(stmt.declExpr);
    return null;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    if (stmt.createVar) {
      // Don't create variable to hold MethodHandle when in wrapper function
      // since MethodHandle must point to wrapper and we want variable to be
      // in parent scope of the wrapper function.
      compile(stmt.declExpr);
    }
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    return compile(stmt.expr);
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    compile(stmt.expr);
    pop();
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    emitIf(() -> {
             compile(stmt.condition);
             convertToBoolean(false, stmt.condition);
             pop();
           },
           stmt.trueStmt  == null ? null : () -> compile(stmt.trueStmt),
           stmt.falseStmt == null ? null : () -> compile(stmt.falseStmt));
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    stmt.endLoopLabel = new Label();
    Label loop = new Label();
    if (stmt.updates == null) {
      stmt.continueLabel = loop;
    }
    else {
      stmt.continueLabel = new Label();
    }
    mv.visitLabel(loop);
    compile(stmt.condition);
    convertToBoolean(false, stmt.condition);
    pop();
    expect(1);
    mv.visitJumpInsn(IFEQ, stmt.endLoopLabel);
    compile(stmt.body);
    if (stmt.updates != null) {
      mv.visitLabel(stmt.continueLabel);
      compile(stmt.updates);
    }
    mv.visitJumpInsn(GOTO, loop);
    mv.visitLabel(stmt.endLoopLabel);
    return null;
  }

  @Override public Void visitClassDecl(Stmt.ClassDecl stmt) {
    var compiler = new ClassCompiler(classCompiler.source, classCompiler.context, classCompiler.pkg, stmt, classCompiler.sourceName);
    compiler.compileClass();
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    mv.visitTypeInsn(NEW, "jacsal/runtime/RuntimeError");
    push(ANY);
    dupVal();
    loadConst(stmt.msg);
    compile(stmt.source);
    compile(stmt.offset);
    expect(5);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
    pop(5);
    return null;
  }

  /////////////////////////////////////////////

  //= Expr

  private Void compile(Expr expr) {
    if (expr == null) {
      return null;
    }
    if (expr.isConst) {
      if (expr.isResultUsed) {
        loadConst(expr.constValue);
      }
      return null;
    }

    if (expr.location != null && expr.location.getLineNum() != currentLineNum) {
      currentLineNum = expr.location.getLineNum();
      Label label = new Label();
      mv.visitLabel(label);
      mv.visitLineNumber(currentLineNum, label);
    }
    try {
      if (classCompiler.annotate()) {
        _loadConst("  ".repeat(indent++) + "==> " + expr.getClass().getName() + "<" + expr.hashCode() + ">");
        mv.visitInsn(POP);
      }
      expr.accept(this);

      if (!expr.isResultUsed) {
        // If result never used then pop is off the stack. Note that VarDecl and VarAssign are
        // special cases since they already check to see whether they should leave something on
        // the stack.
        if (!(expr instanceof Expr.ManagesResult)) {
          popVal();
        }
      }
      return null;
    }
    finally {
      if (classCompiler.annotate()) {
        _loadConst("  ".repeat(--indent) + "<== " + expr.getClass().getName() + "<" + expr.hashCode() + ">");
        mv.visitInsn(POP);
      }
    }
  }

  @Override public Void visitVarDecl(Expr.VarDecl expr) {
    if (expr.isParam) {
      defineVar(expr);
      return null;     // Nothing else to do since value is already stored for parameters
    }

    if (expr.type.is(FUNCTION) && expr.funDecl != null) {
      // For functions we have to define the var first since it might be used recursively
      defineVar(expr);
      loadBoundMethodHandle(expr.funDecl);
    }
    else {
      if (expr.initialiser != null) {
        compile(expr.initialiser);
        // No need to cast if null
        if (expr.type.isRef() && expr.isNull()) {
          pop();
          push(expr.type);
        }
        else {
          convertTo(expr.type, expr.initialiser, true, expr.initialiser.location);
        }
      }
      else {
        loadDefaultValue(expr.type);
      }

      // For anything that is not a function we define the variable after the initialiser in case the initialiser
      // has async behaviour. Otherwise the async handling would try to restore a value that doesn't yet exist.
      defineVar(expr);
    }

    if (expr.isGlobal || expr.isField) {
      // Flag it as initialised so we can use slot == -1 as a test for uninitialised vars
      expr.slot = -2;
    }

    // If value of assignment used as implicit return then duplicate value
    // before storing it so value is left on stack
    if (expr.isResultUsed) {
      dupVal();
    }

    storeVar(expr);
    return null;
  }

  @Override public Void visitFunDecl(Expr.FunDecl expr) {
    compile(expr.varDecl);
    classCompiler.compileMethod(expr);
    return null;
  }

  @Override public Void visitVarAssign(Expr.VarAssign expr) {
    boolean isConditionalAssign = expr.operator.is(QUESTION_EQUAL);

    // If doing ?= then if rhs is null or throws a NullError we don't perform
    // the assignment
    if (isConditionalAssign) {
      Label end = new Label();
      tryCatch(NullError.class,
               () -> {
                 // Try block: get rhs value and if not null assign
                 compile(expr.expr);
                 expect(1);

                 // Only need check for null value if not a primitive
                 if (!peek().isPrimitive()) {
                   _dupVal();
                   // If non null then jump to assignment
                   Label haveRhsValue = new Label();
                   mv.visitJumpInsn(IFNONNULL, haveRhsValue);
                   // Pop null off stack if we don't need a value as a result
                   if (!expr.isResultUsed) {
                     _popVal();
                   }
                   mv.visitJumpInsn(GOTO, end);
                   mv.visitLabel(haveRhsValue);    // :haveRhsValue
                 }

                 // If conditional assign then we know by now whether we have a null value or not
                 // so we don't need to check again when checking for type conversion
                 convertTo(expr.identifierExpr.type, null, !isConditionalAssign, expr.expr.location);
                 if (expr.isResultUsed) {
                   dupVal();
                 }
                 storeVar(expr.identifierExpr.varDecl);
                 if (expr.isResultUsed) {
                   // Need to box if primitive because when we don't do the assign due to NullError
                   // we leave null as the result so we need to box so that type of entire expression
                   // is compatible with use of null
                   box();
                   pop();
                   push(expr.type);
                 }
               },

               // Catch block: if NullError thrown then don't assign and return
               // null if result needed
               () -> {
                 if (expr.isResultUsed) {
                   // Need a null value if no value from rhs
                   loadNull(expr.type);
                 }
               });
      mv.visitLabel(end);
    }
    else {
      // Normal assignment
      compile(expr.expr);
      convertTo(expr.type, expr.expr, true, expr.expr.location);
      if (expr.isResultUsed) {
        dupVal();
      }
      storeVar(expr.identifierExpr.varDecl);
    }

    return null;
  }

  @Override public Void visitVarOpAssign(Expr.VarOpAssign expr) {
    // If we have += or -= with a const value then there are optimisations that are possible
    // (especially if local variable of type int).
    if (expr.operator.is(PLUS_EQUAL,MINUS_EQUAL,PLUS_PLUS,MINUS_MINUS) &&
        !expr.identifierExpr.varDecl.isGlobal) {
      var binary = (Expr.Binary)expr.expr;
      if (binary.right.isConst) {
        incOrDecVar(true,
                    expr.operator.is(PLUS_EQUAL, PLUS_PLUS),
                    expr.identifierExpr,
                    binary.right.constValue,
                    expr.isResultUsed,
                    expr.operator);
        return null;
      }
    }

    loadVar(expr.identifierExpr.varDecl);
    compile(expr.expr);
    convertTo(expr.type, expr.expr, true, expr.expr.location);
    if (expr.isResultUsed) {
      dupVal();
    }
    storeVar(expr.identifierExpr.varDecl);
    return null;
  }

  @Override public Void visitNoop(Expr.Noop expr) {
    // Nothing to do
    return null;
  }

  @Override public Void visitFieldAssign(Expr.FieldAssign expr) {
    Runnable storeFieldValue = () -> {
      // If we have a user class instance and already know the field
      if (expr.parent.type.is(INSTANCE) && expr.field instanceof Expr.Literal) {
        if (expr.isResultUsed) {
          dupVal();
        }

        compile(expr.parent);
        swap();

        String fieldName     = ((Expr.Literal)expr.field).value.getStringValue();
        JacsalType fieldType = getFieldType(expr.parent.type, fieldName);
        storeClassField(expr.parent.type.getInternalName(), fieldName, fieldType, false);
      }
      else {
        box();
        compile(expr.parent);
        compile(expr.field);
        box();
        storeValueParentField(expr.accessType);
        if (!expr.isResultUsed) {
          popVal();
        }
      }
    };

    if (expr.assignmentOperator.is(QUESTION_EQUAL)) {
      Label end = new Label();
      tryCatch(NullError.class,
               // Try block:
               () -> {
                 compile(expr.expr);

                 // Only check for null if not primitive
                 if (!peek().isPrimitive()) {
                   expect(1);
                   _dupVal();
                   // If non null then jump to assignment
                   Label haveRhsValue = new Label();
                   mv.visitJumpInsn(IFNONNULL, haveRhsValue);
                   // Pop null off stack if we don't need a value as a result
                   if (!expr.isResultUsed) {
                     _popVal();
                   }
                   mv.visitJumpInsn(GOTO, end);
                   mv.visitLabel(haveRhsValue);    // :haveRhsValue
                 }

                 // If conditional assign then we know by now whether we have a null value or not
                 // so we don't need to check again when checking for type conversion
                 convertTo(expr.type, null, false, expr.expr.location);
                 storeFieldValue.run();

                 if (expr.isResultUsed) {
                   // Just in case our field is a primitive we need to leave a boxed result on stack
                   // to be compatible with non-assigment case when we leave null on the stack
                   pop();
                   push(expr.type);
                   box();
                 }
               },
               // Catch block:
               () -> {
                 if (expr.isResultUsed) {
                   // Need a null value if no value from rhs
                   loadNull(expr.type);
                 }
               });
      mv.visitLabel(end);
    }
    else {
      compile(expr.expr);
      convertTo(expr.type, expr.expr, true, expr.expr.location);    // Convert to ANY since all map/list entries are ANY
      storeFieldValue.run();
    }
    return null;
  }

  @Override public Void visitFieldOpAssign(Expr.FieldOpAssign expr) {
    compile(expr.parent);

    boolean isInstanceField = expr.parent.type.is(INSTANCE) && expr.field instanceof Expr.Literal;
    String fieldName     = isInstanceField ? ((Expr.Literal)expr.field).value.getStringValue() : null;
    JacsalType fieldType = isInstanceField ? getFieldType(expr.parent.type, fieldName) : null;
    if (isInstanceField) {
      dupVal();  // Need extra parent ref for later storing of field
      loadClassField(expr.parent.type.getInternalName(), fieldName, fieldType, false);
    }
    else {
      compile(expr.field);
      box();

      // Since assignment is +=, -=, etc (rather than just '=' or '?=') we will need parent
      // and field again to get value and to store value so duplicate parent and field on
      // stack to get:
      // ... parent, field, parent, field
      dupVal2();

      // Load the field value onto stack (or suitable default value).
      // Default value will be based on the type of the rhs of the += or -= etc.
      loadField(expr.accessType, RuntimeUtils.DEFAULT_VALUE, expr.field.location);
    }

    // If we need the before value as the result then stash in a temp for later
    int temp = -1;
    boolean beforeResultUsed = expr.isResultUsed && expr.isPreIncOrDec;
    if (beforeResultUsed) {
      dupVal();
      temp = allocateSlot(expr.type);
      storeLocal(temp);
    }

    // Evaluate expression
    compile(expr.expr);
    convertTo(expr.type, expr.expr, true, expr.location);

    // If we need the after result and we have an instance field then stash in a temp for later retrieval.
    // For non-instsance fields the storeField() will return the after result so no need for a temp.
    boolean afterResultUsed = expr.isResultUsed && !expr.isPreIncOrDec;
    if (afterResultUsed && isInstanceField) {
      dupVal();
      temp = allocateSlot(expr.type);
      storeLocal(temp);
    }

    // Store result back into field
    if (isInstanceField) {
      // Parent ref still on stack from earlier
      storeClassField(expr.parent.type.getInternalName(), fieldName, fieldType, false);
    }
    else {
      storeParentFieldValue(expr.accessType);
      if (!afterResultUsed) {
        popVal();
      }
    }

    // Retrieve the required value from temp if we have used it
    if (temp != -1) {
      loadLocal(temp);
      freeTemp(temp);
    }

    return null;
  }

  private static TokenType[] numericOperator = new TokenType[] {PLUS, MINUS, STAR, SLASH, PERCENT };
  private static Map<TokenType,String> methodNames = Map.of(PLUS,    "plus",
                                                            MINUS,   "minus",
                                                            STAR,    "multiply",
                                                            SLASH,   "divide",
                                                            PERCENT, "remainder");

  @Override public Void visitRegexMatch(Expr.RegexMatch expr) {
    if (expr.string == null) {
      compile(expr.pattern);
      return null;
    }

    boolean globalModifier = expr.modifiers.indexOf('g') != -1;
    String modifiers = expr.modifiers.replaceAll("[fg]", "");
    loadVar(expr.captureArrVarDecl);
    compile(expr.string);
    castToString(expr.string.location);
    compile(expr.pattern);
    castToString(expr.pattern.location);
    expect(3);
    loadConst(globalModifier);
    loadConst(modifiers);
    loadLocation(expr.operator);
    invokeMethod(RuntimeUtils.class, "regexFind", RegexMatcher.class, String.class, String.class, boolean.class, String.class, String.class, int.class);
    if (expr.operator.is(BANG_GRAVE)) {
      _booleanNot();
    }

    return null;
  }

  @Override public Void visitRegexSubst(Expr.RegexSubst expr) {
    boolean globalModifier = expr.modifiers.indexOf('g') != -1;
    String modifiers = expr.modifiers.replaceAll("[fg]", "");

    compile(expr.string);
    castToString(expr.string.location);
    loadVar(expr.captureArrVarDecl);
    swap();
    compile(expr.pattern);
    castToString(expr.pattern.location);

    if (!expr.isComplexReplacement) {
      compile(expr.replace);
      expect(4);
      loadConst(globalModifier);
      loadConst(modifiers);
      loadLocation(expr.operator);
      invokeMethod(RuntimeUtils.class, "regexSubstitute", RegexMatcher.class, String.class, String.class, String.class, boolean.class, String.class, String.class, int.class);
      return null;
    }

    // Complex replacement string so we need to iterate ourselves and re-evaluate the replacement
    // string each time (with the new capture vars)

    // Create a StringBuilder to store result into
    _newInstance(StringBuilder.class);
    int sbVar = allocateSlot(ANY);
    _storeLocal(sbVar);

    // Setup our Matcher and get first result
    expect(3);
    loadConst(globalModifier);
    loadConst(modifiers);
    loadLocation(expr.operator);
    invokeMethod(RuntimeUtils.class, "regexFind", RegexMatcher.class, String.class, String.class, boolean.class, String.class, String.class, int.class);
    int matcherVar = allocateSlot(ANY);
    loadVar(expr.captureArrVarDecl);
    mv.visitFieldInsn(GETFIELD, "jacsal/runtime/RegexMatcher", "matcher", "Ljava/util/regex/Matcher;");
    storeLocal(matcherVar);

    Label done = new Label();
    Label loop = new Label();
    mv.visitLabel(loop);         // :loop
    mv.visitJumpInsn(IFEQ, done);
    pop();
    loadLocal(matcherVar);
    loadLocal(sbVar);
    compile(expr.replace);
    castToString(expr.replace.location);
    invokeMethod(Matcher.class, "appendReplacement", StringBuilder.class, String.class);
    popVal();    // Returns the Matcher which we already have

    // If in loop
    if (globalModifier) {
      loadLocal(matcherVar);
      invokeMethod(Matcher.class, "find");
      pop();
      mv.visitJumpInsn(GOTO, loop);
    }

    mv.visitLabel(done);      // :done
    loadLocal(matcherVar);
    loadLocal(sbVar);
    invokeMethod(Matcher.class, "appendTail", StringBuilder.class);
    popVal();   // Returns the StringBuilder

    loadLocal(sbVar);
    invokeMethod(StringBuilder.class, "toString");

    freeTemp(matcherVar);
    freeTemp(sbVar);
    return null;
  }

  // Map of which opcode to use for common binary operations based on type
  private static final Map<TokenType,List<Object>> opCodesByOperator = Utils.mapOf(
    PLUS,                List.of(IADD,  LADD,  DADD, RuntimeUtils.PLUS),
    MINUS,               List.of(ISUB,  LSUB,  DSUB, RuntimeUtils.MINUS),
    STAR,                List.of(IMUL,  LMUL,  DMUL, RuntimeUtils.STAR),
    SLASH,               List.of(IDIV,  LDIV,  DDIV, RuntimeUtils.SLASH),
    PERCENT,             List.of(IREM,  LREM,  DREM, RuntimeUtils.PERCENT),
    AMPERSAND,           List.of(IAND,  LAND,  -1,   -1),
    PIPE,                List.of(IOR,   LOR,   -1,   -1),
    ACCENT,              List.of(IXOR,  LXOR,  -1,   -1),
    DOUBLE_LESS_THAN,    List.of(ISHL,  LSHL,  -1,   -1),
    DOUBLE_GREATER_THAN, List.of(ISHR,  LSHR,  -1,   -1),
    TRIPLE_GREATER_THAN, List.of(IUSHR, LUSHR, -1,   -1)
  );
  private static final int intIdx = 0;
  private static final int longIdx = 1;
  private static final int doubleIdx = 2;
  private static final int decimalIdx = 3;

  @Override public Void visitBinary(Expr.Binary expr) {
    // If we don't know the type of one of the operands then we delegate to RuntimeUtils.binaryOp
    if (expr.operator.is(numericOperator) && (expr.left.type.is(ANY) || expr.right.type.is(ANY))) {
      compile(expr.left);
      box();
      compile(expr.right);
      box();
      expect(2);
      loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
      loadConst(expr.originalOperator == null ? null : RuntimeUtils.getOperatorType(expr.originalOperator.getType()));
      loadConst(classCompiler.context.maxScale);
      loadLocation(expr.operator);
      String methodName = methodNames.get(expr.operator.getType());
      if (methodName == null) {
        //methodName = "binaryOp";
        throw new IllegalStateException("Internal error: unsupported operator type " + expr.operator.getChars());
      }
      invokeMethod(RuntimeUtils.class, methodName, Object.class, Object.class, String.class, String.class, int.class, String.class, int.class);
      //convertTo(expr.type, true, expr.operator);  // Convert to expected type
      return null;
    }

    //= Handle ?: default operator
    if (expr.operator.is(QUESTION_COLON)) {
      compile(expr.left);
      if (peek().isPrimitive()) {
        convertTo(expr.type, null, false, expr.left.location);
      }
      else {
        pop();
        Label isNotNull = new Label();
        expect(1);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, isNotNull);
        mv.visitInsn(POP);
        compile(expr.right);
        convertTo(expr.type, expr.right, true, expr.right.location);
        Label end = new Label();
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(isNotNull);   // :isNotNull
        convertTo(expr.type, expr.left, true, expr.left.location);
        mv.visitLabel(end);         // :end
      }
      return null;
    }

    //= String concatenation
    if (expr.operator.is(PLUS) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      compile(expr.right);
      convertToString();
      invokeMethod(String.class, "concat", String.class);
      return null;
    }

    //= List + list
    if (expr.operator.is(PLUS) && expr.type.is(LIST)) {
      compile(expr.left);
      compile(expr.right);
      expect(2);
      box();
      loadConst(expr.originalOperator != null && expr.originalOperator.is(PLUS_EQUAL));
      invokeMethod(RuntimeUtils.class, "listAdd", List.class, Object.class, boolean.class);
      return null;
    }

    //= Map + map
    if (expr.operator.is(PLUS) && expr.type.is(MAP)) {
      compile(expr.left);
      compile(expr.right);
      expect(2);
      loadConst(expr.originalOperator != null && expr.originalOperator.is(PLUS_EQUAL));
      invokeMethod(RuntimeUtils.class, "mapAdd", Map.class, Map.class, boolean.class);
      return null;
    }

    //= String repeat
    if (expr.operator.is(STAR) && expr.type.is(STRING)) {
      compile(expr.left);
      convertToString();
      compile(expr.right);
      convertTo(INT, expr.right, true, expr.right.location);
      expect(2);
      loadConst(classCompiler.source);
      loadConst(expr.operator.getOffset());
      invokeMethod(RuntimeUtils.class, "stringRepeat", String.class, Integer.TYPE, String.class, Integer.TYPE);
      return null;
    }

    //= Check for Map/List/String/Instance/Class access via field/index
    if (expr.operator.is(DOT,QUESTION_DOT,LEFT_SQUARE,QUESTION_SQUARE)) {
      if (expr.isFieldAccess) {
        compileFieldAccess(expr);
        return null;
      }

      compile(expr.left);
      if (expr.left.type.is(INSTANCE,JacsalType.CLASS)) {
        if (expr.right instanceof Expr.Literal) {
          // We know the type so get the field/method directly.
          // Note: we know that Resolver has already checked for valid field/method name.
          String     name      = literalString(expr.right);
          var        clss      = expr.left.type.getClassDescriptor();
          // We need to find the method handle for the given method
          var method = clss.getMethod(name);
          if (method == null) {
            throw new IllegalStateException("Internal error: could not find method or field called " + name + " for " + expr.left.type);
          }
          // If method is static then we don't need the instance
          if (method.isStatic && expr.left.type.is(INSTANCE)) {
            popVal();
          }
          // We want the handle to the wrapper method.
          String handleName = Utils.staticHandleName(Utils.wrapperName(name));
          loadClassField(clss.getInternalName(), handleName, FUNCTION, true);
          // If not static then bind to instance
          if (!method.isStatic) {
            swap();
            invokeMethod(MethodHandle.class, "bindTo", Object.class);
          }
          return null;
        }

        // Can't determine method/field at compile time so fall through to runtime lookup
        if (expr.left.type.is(JacsalType.CLASS)) {
          // Need to tell runtime what class it is so load class onto stack
          loadConst(expr.left.type);
        }
      }

      box();
      if (expr.isCallee && expr.operator.is(DOT,QUESTION_DOT)) {
        compile(expr.right);
        loadMethodOrField(expr.operator);
      }
      else {
        if (expr.createIfMissing) {
          // If we are lvalue and auto-creating fields when needed then we need to know if we are a Map/List or
          // an instance since instance creation could result in async behaviour if a field initialiser invokes
          // an async function.
          emitIf(() -> isInstanceOf(JacsalObject.class),
                 () -> loadOrCreateInstanceField(expr),
                 () -> {
                   compile(expr.right);
                   loadOrCreateField(expr.operator, expr.type, expr.right.location);
                 });
        }
        else {
          compile(expr.right);
          loadField(expr.operator, null, expr.right.location);
        }
      }
      return null;
    }

    //= instanceOf and !instanceOf
    if (expr.operator.is(INSTANCE_OF,BANG_INSTANCE_OF)) {
      compile(expr.left);
      box();
      emitInstanceof(expr.right.type);
      if (expr.operator.is(BANG_INSTANCE_OF)) {
        _booleanNot();
      }
      setStackType(BOOLEAN);
      return null;
    }

    //= in and !in
    if (expr.operator.is(IN,BANG_IN)) {
      compile(expr.left);
      box();
      compile(expr.right);
      expect(2);
      loadConst(expr.operator.is(IN));   // true for in, false for !in
      loadLocation(expr.operator);
      invokeMethod(RuntimeUtils.class, "inOperator", Object.class, Object.class, boolean.class, String.class, int.class);
      return null;
    }

    //= as
    if (expr.operator.is(AS)) {
      compile(expr.left);
      if (expr.type.is(BOOLEAN)) {
        convertToBoolean(false);
        return null;
      }
      if (expr.type.is(STRING)) {
        convertToStringOrNull();
        return null;
      }
      if (peek().unboxed().is(BOOLEAN,INT,LONG,DOUBLE,DECIMAL) && expr.type.is(BOOLEAN,INT,LONG,DOUBLE,DECIMAL) ||
          peek().is(ANY,INSTANCE,MAP,LIST) && expr.type.is(INSTANCE)) {
        convertTo(expr.type, expr.left, !peek().isPrimitive(), expr.location);
        return null;
      }
      expect(1);
      box();
      loadLocation(expr.location);
      invokeMethod(RuntimeUtils.class, "as" + Utils.capitalise(expr.type.typeName()), Object.class, String.class, int.class);
      return null;
    }

    //= Boolean && or ||
    if (expr.operator.is(AMPERSAND_AMPERSAND,PIPE_PIPE)) {
      compile(expr.left);
      convertToBoolean(false, expr.left);

      // Look for short-cutting && or || (i.e. if we already know the result
      // we don't need to evaluate right hand side)
      if (expr.operator.is(AMPERSAND_AMPERSAND)) {
        // Handle case for &&
        Label isFalse = new Label();
        Label end     = new Label();
        mv.visitJumpInsn(IFEQ, isFalse);    // If first operand is false
        pop();
        compile(expr.right);
        convertToBoolean(false, expr.right);
        mv.visitJumpInsn(IFEQ, isFalse);
        pop();
        _loadConst(true);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(isFalse);            // :isFalse
        _loadConst(false);
        mv.visitLabel(end);                // :end
      }
      else {
        // Handle case for ||
        Label end    = new Label();
        Label isTrue = new Label();
        mv.visitJumpInsn(IFNE, isTrue);
        pop();
        compile(expr.right);
        convertToBoolean(false, expr.right);
        pop();
        mv.visitJumpInsn(IFNE, isTrue);
        _loadConst(false);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(isTrue);
        _loadConst(true);
        mv.visitLabel(end);
      }
      push(BOOLEAN);
      if (expr.type.isBoxed()) {
        box();
      }
      return null;
    }

    // Compare operator <=>
    if (expr.operator.is(COMPARE)) {
      var maxType = Utils.maxNumericType(expr.left.type, expr.right.type);
      if (maxType != null || expr.left.type.is(BOOLEAN) && expr.right.type.is(BOOLEAN)) {
        // If we have numeric types or boolean
        if (maxType == null || maxType.is(INT)) {
          // There is no ICMP instruction so we turn int and boolean into longs so we can use LCMP
          maxType = LONG;
        }
        compile(expr.left);
        convertTo(maxType, expr.left, false, expr.left.location);
        compile(expr.right);
        convertTo(maxType, expr.right, false, expr.right.location);
        expect(2);
        switch (maxType.getType()) {
          case DECIMAL: invokeMethod(BigDecimal.class, "compareTo", BigDecimal.class);  break;
          case DOUBLE:  mv.visitInsn(DCMPL);    pop(2);   push(INT);                           break;
          case LONG:    mv.visitInsn(LCMP);     pop(2);   push(INT);                           break;
          default: throw new IllegalStateException("Internal error: unexpected type " + maxType.getType());
        }
        return null;
      }
      else {
        compile(expr.left);
        box();
        compile(expr.right);
        expect(2);
        box();
        loadLocation(expr.operator);
        invokeMethod(RuntimeUtils.class, "compareTo", Object.class, Object.class, String.class, int.class);
        return null;
      }
    }

    // For remaining comparison operators
    if (expr.operator.isBooleanOperator()) {
      // If we don't have primitive types then delegate to RuntimeUtils.booleanOp
      if (!expr.left.type.isPrimitive() || !expr.right.type.isPrimitive()) {
        compile(expr.left);
        box();
        compile(expr.right);
        expect(2);
        box();
        loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
        loadConst(expr.operator.getSource());
        loadConst(expr.operator.getOffset());
        invokeMethod(RuntimeUtils.class, "booleanOp", Object.class, Object.class, String.class, String.class, Integer.TYPE);
        return null;
      }

      // Left with boolean, int, long, double types
      if ((expr.left.type.is(BOOLEAN) || expr.right.type.is(BOOLEAN)) && !expr.left.type.equals(expr.right.type)) {
        // If one type is boolean and the other isn't then we must be doing == or != comparison
        // since Resolver would have already given compile error if other type of comparison
        check(expr.operator.is(EQUAL_EQUAL,BANG_EQUAL), "Unexpected operator " + expr.operator + " comparing boolean and non-boolean");
        // Since types are not compatible we already know that they are not equal so pop values and
        // load result
        compile(expr.left);
        compile(expr.right);
        popVal();
        popVal();
        loadConst(expr.operator.is(BANG_EQUAL));    // true when op is !=
        return null;
      }

      // We have two booleans or two numbers (int,long, or double)
      JacsalType operandType = expr.left.type.is(BOOLEAN) ? INT :
                               expr.left.type.is(DOUBLE) || expr.right.type.is(DOUBLE) ? DOUBLE :
                               expr.left.type.is(LONG)   || expr.right.type.is(LONG)   ? LONG :
                                                                                         INT;
      compile(expr.left);
      convertTo(operandType, expr.left, false, expr.left.location);
      compile(expr.right);
      convertTo(operandType, expr.right, false, expr.right.location);
      expect(2);
      mv.visitInsn(operandType.is(INT) ? ISUB : operandType.is(LONG) ? LCMP : DCMPL);
      pop(2);
      int opCode = expr.operator.is(EQUAL_EQUAL)        ? IFEQ :
                   expr.operator.is(BANG_EQUAL)         ? IFNE :
                   expr.operator.is(LESS_THAN)          ? IFLT :
                   expr.operator.is(LESS_THAN_EQUAL)    ? IFLE :
                   expr.operator.is(GREATER_THAN)       ? IFGT :
                   expr.operator.is(GREATER_THAN_EQUAL) ? IFGE :
                   -1;
      check(opCode != -1, "Unexpected operator " + expr.operator);
      Label resultIsTrue = new Label();
      Label end          = new Label();
      mv.visitJumpInsn(opCode, resultIsTrue);
      _loadConst(false);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(resultIsTrue);   // :resultIsTrue
      _loadConst(true);
      mv.visitLabel(end);            // :end
      push(BOOLEAN);
      return null;
    }

    // Everything else
    compile(expr.left);
    if (expr.operator.getType().isBitOperator() && expr.left.type.is(ANY)) {
      // Don't do any conversion yet for bit manipulation ops since if we have ANY we don't know whether
      // we want to an int or long manipulation yet. We need to box any primitives, however, if result
      // is ANY since we are going to pass them to RuntimeUtils a bit later
      box();
    }
    else {
      convertTo(expr.type, expr.left, true, expr.left.location);
    }
    compile(expr.right);
    if (expr.operator.getType().isBitShift()) {
      // Right-hand side of shift has to be an integer
      castToIntOrLong(INT, expr.right.location);
    }
    else {
      convertTo(expr.type, expr.right, true, expr.right.location);
    }

    // If we have a bit operation and we don't know the types yet then delegate to RuntimeUtils
    if (expr.operator.getType().isBitOperator() && expr.type.is(ANY)) {
      expect(2);
      box();  // Box rhs so we can invoke our method
      loadConst(RuntimeUtils.getOperatorType(expr.operator.getType()));
      loadLocation(expr.operator);
      invokeMethod(RuntimeUtils.class, "bitOperation", Object.class, Object.class, String.class, String.class, int.class);
      return null;
    }

    List<Object> opCodes = opCodesByOperator.get(expr.operator.getType());
    if (opCodes == null) {
      throw new UnsupportedOperationException("Unsupported operator " + expr.operator.getType());
    }
    else
    if (expr.type.isBoxedOrUnboxed(INT, LONG, DOUBLE)) {
      expect(2);
      int index = expr.type.isBoxedOrUnboxed(INT)  ? intIdx :
                  expr.type.isBoxedOrUnboxed(LONG) ? longIdx
                                                   : doubleIdx;
      int opCode = (int)opCodes.get(index);
      // Check for divide by zero or remainder 0 if int/long (double returns NaN or INFINITY)
      if (expr.operator.is(SLASH,PERCENT) && !expr.type.isBoxedOrUnboxed(DOUBLE)) {
        _dupVal();
        Label nonZero = new Label();
        if (expr.type.isBoxedOrUnboxed(LONG)) {
          _loadConst(0L);
          mv.visitInsn(LCMP);
        }
        mv.visitJumpInsn(IFNE, nonZero);
        _popVal();
        throwError("Divide by zero error", expr.operator);
        mv.visitLabel(nonZero);
      }
      mv.visitInsn(opCode);
      pop(2);
      push(expr.type);
    }
    else
    if (expr.type.is(DECIMAL)) {
      expect(2);
      loadConst(opCodes.get(decimalIdx));
      loadConst(classCompiler.context.maxScale);
      loadConst(expr.operator.getSource());
      loadConst(expr.operator.getOffset());
      invokeMethod(RuntimeUtils.class, "decimalBinaryOperation", BigDecimal.class, BigDecimal.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
    }
    else {
      throw new IllegalStateException("Internal error: unexpected type " + expr.type + " for operator " + expr.operator.getType());
    }
    return null;
  }

  private static String literalString(Expr e) {
    return ((Expr.Literal) e).value.getValue().toString();
  }

  private void compileFieldAccess(Expr.Binary expr) {
    // Generate a list of our parent nodes from current node up to top most one
    var nodes = new ArrayList<Expr.Binary>();
    for (Expr node = expr;
         node instanceof Expr.Binary && ((Expr.Binary)node).isFieldAccess;
         node = ((Expr.Binary)node).left) {
      nodes.add((Expr.Binary)node);
    }
    Expr.Binary leftMost = nodes.get(nodes.size() - 1);
    // For left most field compile parent and save its value in case we need it once null value found
    compile(leftMost.left);
    if (leftMost.left.couldBeNull) {
      throwIfNull("Trying to access field/element of null object", leftMost.operator);
    }
    boolean checkForNull = false;
    var     lastNode     = leftMost;
    for (ListIterator<Expr.Binary> iter = nodes.listIterator(nodes.size()); iter.hasPrevious(); ) {
      var    node = iter.previous();
      if (checkForNull) {
        throwIfNull("Trying to access field/element of null object" +
                    (lastNode.createIfMissing ? " (could not auto-create due to mandatory fields)" : ""),
                    node.operator);
      }
      if (node.right.isSuper()) {
        continue;
      }
      String fieldName = literalString(node.right);
      var    parentClass = node.left.type.getClassDescriptor();
      var    childType   = parentClass.getField(fieldName);
      // Can only create if createIfMissing flag set and if there are no mandatory fields (i.e. init method
      // has 0 parameters).
      boolean createIfMissing = node.createIfMissing && !(childType.is(INSTANCE) && childType.getClassDescriptor().getInitMethod().paramCount > 0);
      checkForNull = !createIfMissing;
      JacsalType fieldType   = parentClass.getField(fieldName);
      if (fieldType == null) {
        throw new IllegalStateException("Internal error: could not find field '" + fieldName + "' for " + JacsalType.createClass(parentClass));
      }
      if (createIfMissing) {
        dupVal();    // Save parent in case field is null and we need to store a value
        loadClassField(parentClass.getInternalName(), fieldName, fieldType, false);  // Note: we don't support static fields
        Label nonNull = new Label();
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, nonNull);
        popVal();    // Pop null
        // If field is ANY then create Map/List as appropriate. Otherwise create value of right type.
        loadDefaultValueOrNewInstance(fieldType.is(ANY) ? expr.type : fieldType, expr.right.location);
        mv.visitInsn(DUP_X1);     // Copy value and move it two slots
        _storeClassField(parentClass.getInternalName(), fieldName, fieldType, false);
        Label nextField = new Label();
        mv.visitJumpInsn(GOTO, nextField);
        mv.visitLabel(nonNull);            // :nonNull
        // Don't need parent
        swap();
        popVal();

        mv.visitLabel(nextField);         // :nextField
      }
      else {
        loadClassField(parentClass.getInternalName(), fieldName, fieldType, false);  // Note: we don't support static fields
      }
      lastNode = node;
    }
  }

  @Override public Void visitTernary(Expr.Ternary expr) {
    compile(expr.first);
    convertToBoolean(false, expr.first);
    pop();
    Label isFalse = new Label();
    mv.visitJumpInsn(IFEQ, isFalse);
    compile(expr.second);
    convertTo(expr.type, expr.second, true, expr.second.location);
    pop();
    Label end = new Label();
    mv.visitJumpInsn(GOTO, end);
    mv.visitLabel(isFalse);     // :isFalse
    compile(expr.third);
    convertTo(expr.type, expr.third, true, expr.third.location);
    mv.visitLabel(end);         // :end
    return null;
  }

  @Override public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    if (expr.operator.is(PLUS_PLUS,MINUS_MINUS)) {
      boolean isInc = expr.operator.is(PLUS_PLUS);
      incOrDec(true, isInc, expr.expr, expr.isResultUsed, expr.operator);
      return null;
    }

    compile(expr.expr);

    // Type cast?
    if (expr.operator.getType().isType()) {
      convertTo(JacsalType.valueOf(expr.operator.getType()), expr.expr, true, expr.operator);
      return null;
    }

    switch (expr.operator.getType()) {
      case BANG:    convertToBoolean(true, expr.expr);      break;
      case MINUS:   arithmeticNegate(expr.expr.location);           break;
      case PLUS:    /* Nothing to do for unary plus */              break;
      case GRAVE:   arithmeticNot(expr.expr.location);              break;
      default:
        throw new UnsupportedOperationException("Internal error: unknown prefix operator " + expr.operator.getType());
    }
    if (!expr.isResultUsed) {
      popVal();
    }
    return null;
  }

  @Override public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    boolean isInc = expr.operator.is(PLUS_PLUS);
    incOrDec(false, isInc, expr.expr, expr.isResultUsed, expr.operator);
    return null;
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    return loadConst(expr.value.getValue());
  }

  @Override public Void visitListLiteral(Expr.ListLiteral expr) {
    _newInstance(Type.getInternalName(Utils.JACSAL_LIST_TYPE));
    push(LIST);
    expr.exprs.forEach(entry -> {
      dupVal();
      compile(entry);
      box();
      invokeMethod(List.class, "add", Object.class);
      popVal();    // Pop boolean return value of add
    });
    return null;
  }

  @Override public Void visitMapLiteral(Expr.MapLiteral expr) {
    _newInstance(expr.isNamedArgs ? NamedArgsMap.class : Utils.JACSAL_MAP_TYPE);
    push(MAP);
    expr.entries.forEach(entry -> {
      dupVal();
      Expr key = entry.first;
      compile(key);
      convertTo(STRING, key, true, key.location);
      compile(entry.second);
      box();
      invokeMethod(Map.class, "put", Object.class, Object.class);
      popVal();    // Ignore return value from put
    });
    return null;
  }

  @Override public Void visitIdentifier(Expr.Identifier expr) {
    if (expr.varDecl.type.is(JacsalType.CLASS)) {
      // Nothing to do
      //push(expr.type);
      return null;
    }

    final var name = expr.identifier.getStringValue();
    final var isBuiltinFunction = expr.varDecl.funDecl != null && expr.varDecl.funDecl.functionDescriptor.isBuiltin;
    if (!expr.varDecl.isGlobal && expr.varDecl.slot == -1 && !isBuiltinFunction && !expr.varDecl.isField) {
      throw new CompileError("Forward reference to uninitialised value", expr.location);
    }

    if (isBuiltinFunction) {
      // If we have the name of a builtin function then lookup its MethodHandle
      loadConst(name);
      invokeMethod(BuiltinFunctions.class, "lookupMethodHandle", String.class);
    }
    else
    if (name.charAt(0) == '$') {
      loadVar(expr.varDecl);
      // We have a capture var so we need to extract the matching group from the $@ matcher
      loadConst(Integer.parseInt(name.substring(1)));
      invokeMethod(RuntimeUtils.class, "regexGroup", RegexMatcher.class, int.class);
    }
    else {
      loadVar(expr.varDecl);
    }
    return null;
  }

  @Override public Void visitLoadParamValue(Expr.LoadParamValue expr) {
    if (expr.paramDecl.isPassedAsHeapLocal) {
      loadLocal(expr.varDecl.slot);
    }
    else {
      loadVar(expr.varDecl);
    }
    return null;
  }

  @Override
  public Void visitExprString(Expr.ExprString expr) {
    if (expr.exprList.size() == 0) {
      loadConst("");
    }
    else
    if (expr.exprList.size() > 1) {
      _loadConst(expr.exprList.size());
      mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
      push(STRING_ARR);
      for (int i = 0; i < expr.exprList.size(); i++) {
        dupVal();
        loadConst(i);
        Expr subExpr = expr.exprList.get(i);
        compile(subExpr);
        convertToString();
        mv.visitInsn(AASTORE);
        pop(3);     // pop types for the String[], index, and the value
      }
      loadConst(""); // Join string
      swap();
      invokeMethod(String.class, "join", CharSequence.class, CharSequence[].class);
    }
    else {
      compile(expr.exprList.get(0));
      convertToString();
    }
    return null;
  }

  @Override public Void visitClosure(Expr.Closure expr) {
    if (!expr.funDecl.isCompiled) {
      classCompiler.compileMethod(expr.funDecl);
      expr.funDecl.isCompiled = true;
    }
    // Find our MethodHandle and put that on the stack
    loadBoundMethodHandle(expr.funDecl);
    return null;
  }

  @Override public Void visitCall(Expr.Call expr) {
    // If we have an explicit function name and exact number of args then we can invoke directly without
    // needing to go through a method handle or via method wrapper that fills in missing args.
    // The only exception is if there are closed over variables (HeapLocals) that need to be passed to it
    // and those HeapLocals don't exist in our current scope. E.g. when we are invoking another function
    // from within a separate nested function:  def x; def g(){x}; def f(){g()}
    // At the time the body of f is invoking g() it doesn't have direct access to x since it is not itself
    // closing over x and so can't pass it in as a heap local parameter. In this case it will try to get
    // the value of g (the MethodHandle) and invoke g that way.
    boolean isFunctionCall = false;
    FunctionDescriptor functionDescriptor = null;
    if (expr.callee.isFunctionCall()) {
      functionDescriptor = ((Expr.Identifier) expr.callee).getFuncDescriptor();
      // Can only call directly if in same class or is static method. Otherwise we need MethodHandle that
      // was bound to its instance. Functions can be in different classes in repl mode where functions are
      // compiled into separate classes every compile step and then stored in global map.
      isFunctionCall = functionDescriptor.isStatic || functionDescriptor.implementingClass == null || functionDescriptor.implementingClass.equals(classCompiler.internalName);
    }
    if (isFunctionCall) {
      var callee = (Expr.Identifier)expr.callee;
      Expr.FunDecl funDecl = callee.varDecl == null ? null : callee.varDecl.funDecl;

      expr.validateArgsAtCompileTime = validateArgs(expr.args, functionDescriptor, callee.location, funDecl.isInitMethod(), funDecl.returnType);

      if (functionDescriptor.isBuiltin) {
        invokeBuiltinFunction(expr, functionDescriptor);
      }
      else {
        // If user defined function we need to take care of HeapLocals
        invokeUserFunction(expr, funDecl, functionDescriptor);
      }
      return null;
    }

    // Invoking via a method handle so we don't know if we have an async function or not and
    // therefore take the conservative approach of assuming it is async
    invokeMaybeAsync(expr.isAsync, ANY, 0, expr.location,
                     () -> {
                       // Need to get the method handle
                       compile(expr.callee);
                       convertTo(FUNCTION, expr.callee, true, expr.callee.location);

                       // NOTE: we don't have to passed closed over vars here because the MethodHandle we get
                       //       has already had these values bound to it
                       loadNullContinuation();
                       loadLocation(expr.location);

                       // Any arguments will be stored in an Object[]
                       loadArgsAsObjectArr(expr.args);
                     },
                     () -> invokeMethodHandle());

    // Since we might be invoking a function that returns an Iterator (def f = x.map; f()), then
    // we need to check that if not immediately invoking another method call (f().each()) we must
    // convert Iterator to List since Iterator is not a standard type.
    if (!expr.isMethodCallTarget) {
      emitIf(() -> isInstanceOf(ITERATOR),
             () -> invokeMaybeAsync(expr.isAsync, ANY, 1, expr.location, () -> {},
                                    () -> {
                                      loadNullContinuation();
                                      invokeMethod(RuntimeUtils.class, "convertIteratorToList", Object.class, Continuation.class);
                                      pop();
                                      push(ANY);   // Don't know if call occurs so we still have to assume ANY
                                    }),
             null);
    }

    return null;
  }

  @Override public Void visitMethodCall(Expr.MethodCall expr) {
    // If we know what method to invoke
    if (expr.methodDescriptor != null) {
      var method = expr.methodDescriptor;

      expr.validateArgsAtCompileTime = validateArgs(expr.args, method, expr.leftParen, method.isInitMethod, method.returnType);

      // Check for "super" since we will need to do INVOKESPECIAL if invoking via super
      boolean invokeSpecial = expr.parent.isSuper();

      // Need to decide whether to invoke the method or the wrapper. If we have exact number
      // of arguments we can invoke the method directly. If we don't have all the arguments
      // (and method allows optional args) then invoke wrapper which will worry about filling
      // missing values.
      if (expr.args.size() == method.paramCount && !Utils.isNamedArgs(expr.args) && expr.validateArgsAtCompileTime) {
        // We can invoke the method directly as we have exact number of args required
        // TODO: handle var args when we get a builtin method that needs it
        invokeMaybeAsync(expr.isAsync, expr.methodDescriptor.returnType, 0, expr.location,
                         () -> {
                           // Get the instance
                           compile(expr.parent);
                           if (!expr.parent.isSuper() && !expr.parent.isThis() && expr.parent.couldBeNull) {
                             throwIfNull("Cannot invoke method " + expr.methodName + "() on null object", expr.methodNameLocation);
                           }
                           if (method.isBuiltin) {
                             // If we are calling a "method" that is ANY but we have a primitive then we need to box it
                             if (method.firstArgtype.is(ANY)) {
                               box();
                             }
                           }
                           else
                           if (method.isStatic && !expr.parent.type.is(JacsalType.CLASS)) {
                             popVal();
                           }

                           if (method.isAsync) {
                             loadNullContinuation();
                           } // Continuation
                           if (method.needsLocation) {
                             loadLocation(expr.methodNameLocation);
                           }
                           // Get the args
                           for (int i = 0; i < expr.args.size(); i++) {
                             Expr arg = expr.args.get(i);
                             compile(arg);
                             convertTo(method.paramTypes.get(i), arg, !arg.type.isPrimitive(), arg.location);
                           }
                         },
                         () -> invokeUserMethod(method.isStatic, invokeSpecial, method.implementingClass, method.implementingMethod,
                                                expr.type, methodParamTypes(method)));
      }
      else {
        // Need to invoke the wrapper
        invokeMaybeAsync(expr.methodDescriptor.isAsync, expr.methodDescriptor.returnType, 0, expr.location,
                         () -> {
                           compile(expr.parent);                    // Get the instance
                           if (expr.parent.couldBeNull) {
                             throwIfNull("Cannot invoke method " + expr.methodName + "() on null object", expr.methodNameLocation);
                           }
                           if (!method.isBuiltin && method.isStatic && !expr.parent.type.is(JacsalType.CLASS)) {
                             popVal();
                           }
                           if (expr.parent.type.isPrimitive()) {
                             box();
                           }
                           loadNullContinuation();                  // Continuation
                           loadLocation(expr.methodNameLocation);
                           loadArgsAsObjectArr(expr.args);
                         },
                         () -> {
                           var paramTypes = List.of(CONTINUATION, STRING, INT, OBJECT_ARR);
                           if (method.isBuiltin && !method.isGlobalFunction) {
                             paramTypes = Utils.concat(method.firstArgtype, paramTypes);
                           }
                           invokeUserMethod(method.isStatic, invokeSpecial, method.implementingClass, method.wrapperMethod, ANY, paramTypes);
                           // Convert Object returned by wrapper back into return type of the function
                           checkCast(method.returnType);
                           if (method.returnType.isPrimitive()) {
                             unbox();
                           }
                         });
      }
      if (method.returnType.is(ITERATOR) && !expr.isMethodCallTarget) {
        // If Iterator is not going to have another method invoked on it then we need to convert
        // to List since Iterators are not standard Jacsal types.
        invokeMaybeAsync(expr.isAsync, ANY, 1, expr.location, () -> {},
                         () -> {
                           loadNullContinuation();
                           invokeMethod(RuntimeUtils.class, "convertIteratorToList", Object.class, Continuation.class);
                         });
      }
      return null;
    }

    // Since we couldn't call method directly it means that either we didn't know type of parent at
    // compile time or we couldn't find a method of that name. If we didn't know type of parent we
    // will attempt to get the method handle for the field by doing a method lookup and if no method
    // exists we will assume that there is a field of that name that holds a MethodHandle.
    // Since we invoke through a MethodHandle we don't know whether we are async or not so we assume
    // the worst.
    invokeMaybeAsync(true, ANY, 0, expr.location,
                     () -> {
                       compile(expr.parent);           // The instance to invoke method on
                       loadConst(expr.methodName);
                       loadConst(!expr.parent.type.is(ANY));              // Whether must be field
                       loadConst(expr.accessOperator.is(QUESTION_DOT));   // whether x?.a() or x.a()
                       loadArgsAsObjectArr(expr.args);
                       loadLocation(expr.methodNameLocation);
                     },
                     () -> invokeMethod(RuntimeUtils.class, "invokeMethodOrField", Object.class, String.class,
                                        boolean.class, boolean.class, Object[].class, String.class, int.class));

    if (!expr.isMethodCallTarget) {
      // If we are not chaining method calls then it is possible that the method we just invoked returned
      // an Iterator and since Iterators are not standard types we need to convert into a List to make the
      // result usable by other code. So we incoke castToAny which will check for Iterator and convert
      // otherwise it returns the current value.
      emitIf(() -> isInstanceOf(ITERATOR),
             () -> invokeMaybeAsync(true, ANY, 1, expr.location, () -> {},
                                    () -> {
                                      loadNullContinuation();
                                      invokeMethod(RuntimeUtils.class, "convertIteratorToList", Object.class, Continuation.class);
                                      pop();
                                      push(ANY);   // Don't know if call occurs so we still have to assume ANY
                                    }),
             null);
    }
    return null;
  }

  @Override public Void visitArrayLength(Expr.ArrayLength expr) {
    compile(expr.array);
    mv.visitInsn(ARRAYLENGTH);
    pop();
    push(INT);
    return null;
  }

  @Override public Void visitArrayGet(Expr.ArrayGet expr) {
    compile(expr.array);
    compile(expr.index);
    mv.visitInsn(AALOAD);
    pop(2);
    push(ANY);
    return null;
  }

  @Override public Void visitInvokeFunDecl(Expr.InvokeFunDecl expr) {
    if (!expr.funDecl.isStatic()) {
      loadLocal(0);    // this
    }

    // Load HeapLocals that the function needs. Note that its parent is us so we are
    // loading values from our own local slots that are needed by the function.
    expr.funDecl.heapLocalParams.values().forEach(p -> loadLocal(p.parentVarDecl.slot));

    if (expr.funDecl.functionDescriptor.isAsync) {
      loadNullContinuation();
    }

    // Now get the values for all the explicit parameters
    expr.args.forEach(this::compile);

    // Don't need to worry about async behaviour since we are in the wrapper function
    // and this is the very last thing that the wrapper function does. If the real
    // function throws a Continuation then there is no need to continue the wrapper since
    // there is nothing left to do.
    invokeUserMethod(expr.funDecl);
    return null;
  }

  @Override public Void visitInvokeUtility(Expr.InvokeUtility expr) {
    expr.args.forEach(this::compile);
    invokeMethod(expr.clss, expr.methodName, expr.paramTypes.toArray(Class[]::new));
    return null;
  }

  @Override public Void visitReturn(Expr.Return returnExpr) {
    compile(returnExpr.expr);
    convertTo(returnExpr.returnType, returnExpr.expr, true, returnExpr.expr.location);
    pop();
    emitReturn(returnExpr.returnType);
    push(ANY);
    return null;
  }

  @Override public Void visitBreak(Expr.Break stmt) {
    mv.visitJumpInsn(GOTO, stmt.whileLoop.endLoopLabel);
    push(BOOLEAN);
    return null;
  }

  @Override public Void visitContinue(Expr.Continue stmt) {
    mv.visitJumpInsn(GOTO, stmt.whileLoop.continueLabel);
    push(BOOLEAN);
    return null;
  }

  @Override public Void visitPrint(Expr.Print expr) {
    compile(expr.expr);
    convertToString();
    invokeMethod(RuntimeUtils.class, expr.newLine ? "println" : "print", String.class);
    return null;
  }

  @Override public Void visitBlock(Expr.Block expr) {
    compile(expr.block);
    loadConst(true);
    return null;
  }

  @Override public Void visitInvokeNew(Expr.InvokeNew expr) {
    newInstance(expr.type);
    return null;
  }

  @Override public Void visitClassPath(Expr.ClassPath expr) {
    return null;
  }

  @Override public Void visitDefaultValue(Expr.DefaultValue expr) {
    loadDefaultValue(expr.type);
    return null;
  }

  @Override public Void visitInstanceOf(Expr.InstanceOf expr) {
    compile(expr.expr);
    mv.visitTypeInsn(INSTANCEOF, expr.className);
    pop();
    push(BOOLEAN);
    return null;
  }

  @Override public Void visitConvertTo(Expr.ConvertTo expr) {
    int sourceSlot = ((Expr.Identifier) expr.source).varDecl.slot;
    int offsetSlot = ((Expr.Identifier) expr.offset).varDecl.slot;
    var location   = new LocalLocation(sourceSlot, offsetSlot);
    compile(expr.expr);
    convertToInstance(expr.varType, expr.expr, expr.expr.location, location);
    return null;
  }

  private void convertToInstance(JacsalType varType, Expr expr, Location compileTimeLocation, SourceLocation runtimeLocation) {
    JacsalType valueType = peek();

    if (valueType.is(INSTANCE) && valueType.isConvertibleTo(varType)) { return; }  // We have a compatible instance type
    if (expr != null && expr.isNull())                                { return; }  // Null is valid for an Instance

    if (!valueType.is(ANY, MAP)) {
      throw new CompileError("Cannot convert from type " + valueType + " to " + varType, compileTimeLocation);
    }

    int temp = allocateSlot(valueType);
    Label end = new Label();
    storeLocal(temp);
    if (valueType.is(ANY)) {
      // If we don't know type at compile time then check for types at runtime
      _loadLocal(temp);
      mv.visitInsn(DUP);
      mv.visitJumpInsn(IFNULL, end);  // null is allowed as a valid value
      mv.visitInsn(DUP);
      mv.visitTypeInsn(INSTANCEOF, varType.getInternalName());
      mv.visitJumpInsn(IFNE, end);
      mv.visitInsn(DUP);
      mv.visitTypeInsn(INSTANCEOF, Type.getInternalName(Map.class));
      throwIfFalseWithClassName(" is invalid type for constructing " + varType, runtimeLocation);
      mv.visitInsn(POP);
    }

    // We have a Map so now we need to new the instance and invoke init method
    FunctionDescriptor method = varType.getClassDescriptor().getMethod(Utils.JACSAL_INIT);

    invokeMaybeAsync(method.isAsync, ANY, 4 + (method.isAsync ? 1 : 0),
                     compileTimeLocation,
                     () -> {
                       newInstance(varType);
                       loadNullContinuation();   // wrapper functions always have continuation
                       loadLocation(runtimeLocation);
                       _loadConst(1);
                       mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                       push(OBJECT_ARR);
                       dupVal();
                       loadConst(0);
                       loadLocal(temp);
                       mv.visitInsn(AASTORE);
                       pop(3);  // arr + index + value
                     },
                     () -> {
                       // Note: wrapper always has continuation since when invoking as a handle we have
                       // no idea about whether it is async or not.
                       List<JacsalType> paramTypes = List.of(CONTINUATION, STRING, INT, OBJECT_ARR);
                       invokeUserMethod(false, false, method.implementingClass, method.wrapperMethod, ANY,
                                        paramTypes);
                       });
    mv.visitLabel(end);                          // :end
    // Convert Object returned by wrapper back into instance type
    checkCast(method.returnType);
    freeTemp(temp);
  }

  @Override public Void visitTypeExpr(Expr.TypeExpr expr) {
    return null;
  }

  @Override public Void visitInvokeFunction(Expr.InvokeFunction expr) {
    invokeMaybeAsync(expr.functionDescriptor.isAsync, expr.type, 0, expr.location,
                     () -> {
                       if (!expr.functionDescriptor.isStatic) {
                         loadLocal(0);    // this
                       }

                       if (expr.functionDescriptor.isAsync) {
                         loadNullContinuation();
                       }

                       // Now get the values for all the explicit parameters
                       expr.args.forEach(this::compile);
                     },
                     () -> {
                       invokeUserMethod(expr.functionDescriptor, expr.invokeSpecial);
                     }
    );

    return null;
  }

  @Override public Void visitCastTo(Expr.CastTo expr) {
    compile(expr.expr);
    checkCast(expr.type);
    return null;
  }

  ///////////////////////////////////////////////////////////////

  /**
   * Validate number and type of arguments if possible.
   * <p>If we have single arg of type List/ANY then we need to distinguish between the situation where the List
   * is supposed to be a list of args or the List is just the value for the first parameter.
   * If the function has more than one mandatory argument or the type of the first parameter is not compatible
   * with the arg being a list then we assume that the List/ANY must be a list of arg values and we then defer
   * any further validation until runtime (since we usually don't know what is inside the list at compile time).</p>
   * <p>We also allow Lis/ANY to be passed to a function taking no args and then validate at runtime that the
   * List is empty.</p>
   * <p>If the single argument is a Map then we might have named args or we might just have a Map.
   * For normal functions/methods we only support named args which are flagged as named args (by the Parser at
   * compile time). For constructors (init method) we allow arbitray Maps to be used to populate the fields of
   * the object.</p>
   * @return true if args validated and false if validation should be deferred to runtime
   */
  private static boolean validateArgs(List<Expr> args, FunctionDescriptor func, Token location, boolean isInitMethod, JacsalType initClass) {
    int argCount = args.size();
    var arg0Type = argCount > 0 ? args.get(0).type : null;
    if (!isInitMethod && argCount == 1 && arg0Type.is(ANY, LIST)) {
      if (func.paramCount != 1 && func.mandatoryArgCount != 1) {
        if (func.mandatoryArgCount > 1 ||
            func.paramCount == 0 ||
            arg0Type.is(ANY) ||
            !func.paramTypes.get(0).is(LIST)) {
          // Runtime validation if more than one mandatory arg or first arg is compatible with List/ANY
          return false;
        }
      }
    }
    boolean namedArgs = Utils.isNamedArgs(args);
    var paramTypes = func.paramTypes;
    var paramNames = func.paramNames;
    if (namedArgs && paramNames == null) {
      throw new CompileError(func.name + " does not support invocation with named arguments", location);
    }

    if (namedArgs) {
      validateNamedArgs(args, func, location, isInitMethod, initClass);
    }
    else {
      List<String> missingArgs = new ArrayList<>();
      if (func.paramCount >= 0 && argCount > func.paramCount) {
        if (isInitMethod) {
          throw new CompileError("Too many arguments for constructor (passed " + argCount + " but there " + (func.paramCount==1?"is ":"are ") + (func.paramCount == 0 ? "no": "only " + func.paramCount) + " mandatory "
                                 + Utils.plural("field",func.paramCount) + ")", location);
        }
        throw new CompileError("Too many arguments (passed " + argCount + " but expected only " + func.paramCount + ")", location);
      }

      if (argCount < func.mandatoryArgCount) {
        if (func.paramNames == null) {
          if (isInitMethod) {
            throw new CompileError("Missing mandatory arguments for constructor (there are " + func.mandatoryArgCount + "mandatory fields but only passed " + argCount + ")", location);
          }
          throw new CompileError("Missing mandatory arguments (expected " + func.mandatoryArgCount + " but only passed " + argCount + ")", location);
        }
        missingArgs = func.paramNames.subList(argCount, func.mandatoryArgCount);
      }
      else {
        // Validate arguments are of the right type
        for (int i = 0; i < argCount; i++) {
          JacsalType paramType = paramTypes.get(i);
          Expr       argExpr   = args.get(i);
          JacsalType argType   = argExpr.type;
          // Check for varArgs (we already know it must be last param)
          if (paramType.is(OBJECT_ARR)) {
            break;
          }
          validateArg(argExpr, paramType, argExpr.location);
        }
      }
      if (missingArgs.size() > 0) {
        throw new CompileError("Missing mandatory " + Utils.plural(isInitMethod ? "field" : "argument", missingArgs.size()) + ": " + String.join(", ", missingArgs), location);
      }
    }
    return true;
  }

  private static void validateNamedArgs(List<Expr> args, FunctionDescriptor func, Token location, boolean isInitMethod, JacsalType initClass) {
    List<String> missingArgs = new ArrayList<>();

    // For init methods the parameter names and types come from the ClassDescriptor because the init method only
    // has the mandatory parameters while the wrapper (the one taking named arg values) accepts values for all fields.
    var paramNames = isInitMethod ? initClass.getClassDescriptor().getAllFieldNames() : func.paramNames;
    var paramTypes = isInitMethod ? initClass.getClassDescriptor().getAllFieldTypes() : func.paramTypes;

    Function<Expr,String> argName = expr -> ((Expr.Literal)expr).value.getStringValue();
    var mapEntries  = ((Expr.MapLiteral) args.get(0)).entries;
    var namedArgMap = mapEntries.stream().collect(Collectors.toMap(entry -> argName.apply(entry.first), entry -> entry.second));

    for (int i = 0; i < paramNames.size(); i++) {
      String     paramName = paramNames.get(i);
      JacsalType paramType = paramTypes.get(i);
      var        argExpr   = namedArgMap.get(paramName);
      if (argExpr == null) {
        if (func.mandatoryParams.contains(paramName)) {
          missingArgs.add(paramName);
        }
      }
      else {
        validateArg(argExpr, paramType, namedArgMap.get(paramName).location);
        namedArgMap.remove(paramName);
      }
    }

    if (namedArgMap.size() > 0) {
      // Use location of first extraneous argument for error
      var keys = mapEntries.stream()
                           .filter(entry -> namedArgMap.containsKey(argName.apply(entry.first)))
                           .map(entry -> entry.first)
                           .collect(Collectors.toList());
      throw new CompileError("No such " + Utils.plural(isInitMethod ? "field" : "parameter", namedArgMap.size()) + ": "
                             + keys.stream().map(expr -> argName.apply(expr)).collect(Collectors.joining(", ")) +
                             (isInitMethod ? " in object of type " + initClass : ""),
                             keys.get(0).location);
    }
    if (missingArgs.size() > 0) {
      throw new CompileError("Missing mandatory " + Utils.plural(isInitMethod ? "field" : "argument", missingArgs.size()) + ": " + String.join(", ", missingArgs), location);
    }
  }

  private static void validateArg(Expr argExpr, JacsalType paramType, Token location) {
    if (argExpr.type.is(MAP) && paramType.is(INSTANCE)) {
      FunctionDescriptor initMethod = paramType.getClassDescriptor().getInitMethod();
      if (argExpr.isConstMap()) {
        validateNamedArgs(List.of(argExpr), initMethod, argExpr.location, true, paramType);
      }
    }
    else
    if (!argExpr.type.isConvertibleTo(paramType)) {
      throw new CompileError("Cannot convert argument of type " + argExpr.type + " to parameter type of " + paramType, location);
    }
  }

  private static JacsalType getFieldType(JacsalType type, String fieldName) {
    JacsalType fieldType = type.getClassDescriptor().getField(fieldName);
    if (fieldType == null) {
      throw new IllegalStateException("Internal error: couldn't find field '" + fieldName + "' in class " + type);
    }
    return fieldType;
  }

  /**
   * Emit code for an if where there is a condition that emits a boolean expression and then
   * runnables for emitting the statements for the true and false branches.
   * We take care of manipulating the type stack and check that the stacks for the true and
   * false branches match at the end.
   */
  private void emitIf(Runnable condition, Runnable trueStmts, Runnable falseStmts) {
    condition.run();
    Label ifFalse = new Label();
    mv.visitJumpInsn(IFEQ, ifFalse);
    // Save stack
    var savedStack = new ArrayDeque<StackEntry>(stack);
    if (trueStmts != null) {
      trueStmts.run();
    }
    Label end = new Label();
    if (falseStmts != null) {
      mv.visitJumpInsn(GOTO, end);
    }

    mv.visitLabel(ifFalse);               // :ifFalse
    if (falseStmts != null) {
      // Restore stack to how it was before trueStmts ran
      var trueStack = stack;
      stack = savedStack;
      falseStmts.run();
      // Make sure that both branches leave stack the same
      if (!Utils.stacksAreEqual(trueStack, stack)) {
        throw new IllegalStateException("Stack for true/false branches differ:\n true=" + trueStack + "\n false=" + stack);
      }
    }
    mv.visitLabel(end);                   // :end
  }

  private List<JacsalType> methodParamTypes(FunctionDescriptor method) {
    List<JacsalType> types = new ArrayList<>();
    if (method.isBuiltin) {
      // We simulate methods with static functions so we need to add the object we
      // are invoking method on as first arg
      types.add(method.firstArgtype);
    }
    if (method.isAsync || method.isWrapper) { types.add(CONTINUATION); }
    if (method.needsLocation)               { types.addAll(List.of(STRING, INT)); }
    types.addAll(method.paramTypes);
    return types;
  }

  private void invokeUserFunction(Expr.Call expr, Expr.FunDecl funDecl, FunctionDescriptor func) {
    // We invoke wrapper if we don't have enough args since it will fill in missing
    // values for optional parameters
    boolean invokeWrapper = expr.args.size() != func.paramTypes.size() || Utils.isNamedArgs(expr.args) || !expr.validateArgsAtCompileTime;

    // We need to get any HeapLocals that need to be passed in so we can check if we have access to them
    var heapLocals = (invokeWrapper ? funDecl.wrapper.heapLocalParams : funDecl.heapLocalParams).values();

    // Get varDecl for location of HeapLocal that we need for the function we are invoking
    // If we don't have the variable then null will be used for the varDecl
    Function<Expr.VarDecl,Pair<String,Expr.VarDecl>> getVarDecl = p -> {
      String name = p.name.getStringValue();
      if (p.owner == methodFunDecl) return new Pair(name, p.originalVarDecl);
      Expr.VarDecl varDecl = methodFunDecl.heapLocalParams.get(p.name.getStringValue());
      // If not there or if is a different var (with same name)
      varDecl = varDecl == null || varDecl.originalVarDecl != p.originalVarDecl ? null : varDecl;
      return new Pair(name,varDecl);
    };

    var varDeclPairs = heapLocals.stream().map(p -> getVarDecl.apply(p)).collect(Collectors.toList());

    // Make sure either we have variable already in our heap local params or we are the function where
    // the heap local param we need was declared. Look for params we cannot pass in:
    var badVars = varDeclPairs.stream().filter(vp -> vp.second == null).map(vp -> vp.first).collect(Collectors.toList());
    if (badVars.size() > 0) {
      boolean plural = badVars.size() > 1;
      throw new CompileError("Invocation of " + funDecl.nameToken.getStringValue() +
                             " requires passing closed over variable" + (plural?"s ":" ") +
                             String.join(",", badVars.toArray(String[]::new)) +
                             (plural ? " that have" : " that has") +
                             " not been closed over by current " +
                             (methodFunDecl.isClosure() ? "closure" : "function " + methodFunDecl.nameToken.getStringValue()),
                             expr.location);
    }

    invokeMaybeAsync(func.isAsync, invokeWrapper ? ANY : func.returnType, 0, expr.location,
                     () -> {
                       if (!func.isStatic) {
                         // Load this
                         loadLocal(0);
                       }

                       // Now load the HeapLocals onto the stack
                       varDeclPairs.forEach(p -> loadLocal(p.second.slot));

                       if (invokeWrapper) {
                         loadNullContinuation();
                         loadLocation(expr.location);
                         loadArgsAsObjectArr(expr.args);
                       }
                       else {
                         // If invoking directly and async function then load null for the Continuation
                         if (func.isAsync) {
                           loadNullContinuation();
                         }
                         for (int i = 0; i < expr.args.size(); i++) {
                           Expr arg = expr.args.get(i);
                           compile(arg);
                           Expr.VarDecl paramDecl = funDecl.parameters.get(i).declExpr;
                           convertTo(paramDecl.type, arg, !arg.type.isPrimitive(), arg.location);
                           if (paramDecl.isPassedAsHeapLocal) {
                             box();
                             newInstance(HEAPLOCAL);
                             int temp = allocateSlot(HEAPLOCAL);
                             dup();
                             storeLocal(temp);
                             swap();
                             invokeMethod(HeapLocal.class, "setValue", Object.class);
                             loadLocal(temp);
                             freeTemp(temp);
                           }
                         }
                       }
                     },
                     () -> invokeUserMethod(invokeWrapper ? funDecl.wrapper : funDecl)
    );
  }

  private void invokeBuiltinFunction(Expr.Call expr, FunctionDescriptor func) {
    // We invoke wrapper if we don't have enough args since it will fill in missing
    // values for optional parameters. We need last param to be of type Object[] since
    // this means varArgs.
    var     paramTypes = func.paramTypes;
    boolean varArgs    = paramTypes.size() > 0 && paramTypes.get(paramTypes.size() - 1).is(OBJECT_ARR);
    var nonVarArgCount = varArgs ? paramTypes.size() - 1
                                 : paramTypes.size();
    boolean invokeWrapper = expr.args.size() < nonVarArgCount;

    if (invokeWrapper) {
      // Add location types to the front
      paramTypes = List.of(CONTINUATION,STRING,INT,OBJECT_ARR);
      List<JacsalType> finalParamTypes = paramTypes;

      invokeMaybeAsync(true, func.returnType, 0, expr.location,
                       () -> {
                         if (func.needsLocation) {
                           loadLocation(expr.location);
                         }

                         loadNullContinuation();         // Continuation
                         loadLocation(expr.location);
                         loadArgsAsObjectArr(expr.args);
                       },
                       () -> invokeUserMethod(func.isStatic,
                                              false, func.implementingClass == null ? classCompiler.internalName : func.implementingClass,
                                              func.wrapperMethod,
                                              func.returnType,
                                              finalParamTypes));
    }
    else {
      if (func.needsLocation) {
        // Add location types to the front
        paramTypes = Stream.concat(Stream.of(STRING, INT), paramTypes.stream()).collect(Collectors.toList());
      }
      if (func.isAsync) {
        paramTypes = Stream.concat(Stream.of(CONTINUATION), paramTypes.stream()).collect(Collectors.toList());
      }
      List<JacsalType> finalParamTypes = paramTypes;

      invokeMaybeAsync(func.isAsync, func.returnType, 0, expr.location,
                       () -> {
                         int param = 0;
                         if (func.isAsync) {
                           loadNullContinuation();    // Continuation
                           param++;
                         }
                         if (func.needsLocation) {
                           loadLocation(expr.location);
                           param += 2;
                         }

                         int argIdx = 0;
                         for (; argIdx < nonVarArgCount; argIdx++, param++) {
                           Expr       arg       = expr.args.get(argIdx);
                           JacsalType paramType = finalParamTypes.get(param);
                           compile(arg);
                           convertTo(paramType, arg, !arg.type.isPrimitive(), arg.location);
                         }

                         // Load the rest of the args (could be none) as var args into Object[]
                         if (varArgs) {
                           loadArgsAsObjectArr(expr.args.subList(argIdx, expr.args.size()));
                         }
                       },
                       () -> invokeUserMethod(func.isStatic,
                                              false, func.implementingClass == null ? classCompiler.internalName : func.implementingClass,
                                              func.implementingMethod,
                                              func.returnType,
                                              finalParamTypes));
    }
  }

  private void invokeMethodHandle() {
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", "(Ljacsal/runtime/Continuation;Ljava/lang/String;I[Ljava/lang/Object;)Ljava/lang/Object;", false);
    pop(5);   // callee, continuation, source, offset, Object[]
    push(ANY);
  }

  private void loadArgsAsObjectArr(List<Expr> args) {
    _loadConst(args.size());
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    push(OBJECT_ARR);
    for (int i = 0; i < args.size(); i++) {
      dupVal();
      loadConst(i);
      compile(args.get(i));
      box();
      mv.visitInsn(AASTORE);
      pop(3);  // arr + index + value
    }
  }

  private boolean isParam(Stmt st) {
    return st instanceof Stmt.VarDecl &&
           ((Stmt.VarDecl) st).declExpr.isParam;
  }

  private void allocateHeapLocal(int slot) {
    _newInstance(HeapLocal.class);
    _storeLocal(slot);
  }

  private void newInstance(JacsalType type) {
    _newInstance(type.getInternalName());
    push(type);
  }

  private void _newInstance(Class clss) {
    _newInstance(Type.getInternalName(clss));
  }

  private void _newInstance(String className) {
    mv.visitTypeInsn(NEW, className);
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", "()V", false);
  }

  private void promoteToHeapLocal(Stmt.VarDecl param) {
    Expr.VarDecl varDecl = param.declExpr;
    // Load value from old slot
    loadLocal(varDecl.slot);

    // Allocate new slot for the HeapLocal
    varDecl.slot = allocateSlot(HEAPLOCAL);
    allocateHeapLocal(varDecl.slot);

    // Store value into new HeapLocal
    storeVar(varDecl);
  }

  /**
   * Load methodHandle onto stack that has been bound to any heap local params it needs.
   */
  private void loadBoundMethodHandle(Expr.FunDecl funDecl) {
    // The MethodHandle will point to the wrapper function
    Expr.FunDecl wrapperFunDecl = funDecl.wrapper;

    // Load the handle
    boolean isClosure = funDecl.isClosure();
    boolean isStatic  = funDecl.isStatic();
    if (isClosure) {
      if (isStatic) {
        loadClassField(classCompiler.internalName,
                       Utils.staticHandleName(wrapperFunDecl.functionDescriptor.implementingMethod),
                       FUNCTION, true);
      }
      else {
        loadLocal(0);  // "this"
        loadClassField(classCompiler.internalName,
                       Utils.handleName(wrapperFunDecl.functionDescriptor.implementingMethod),
                       FUNCTION,false);
      }
    }
    else {
      // Value of the variable will be the handle for the function
      loadClassField(classCompiler.internalName,
                     Utils.staticHandleName(wrapperFunDecl.functionDescriptor.implementingMethod),
                     FUNCTION,true);

      // For non-static methods we need to bind them to "this" (for closures the constructor has already done this)
      if (!isStatic) {
        loadLocal(0);
        invokeMethod(MethodHandle.class, "bindTo", Object.class);
      }
    }

    // If there are implicit closed over heap var params then bind them so we don't need to pass
    // them every time

    // First check that we don't have forward references to variables that have not yet been initialised
    var badVars = wrapperFunDecl.heapLocalParams.values()
                                                .stream()
                                                .filter(p -> p.parentVarDecl.slot == -1)
                                                .map(p -> p.name.getStringValue())
                                                .collect(Collectors.toList());

    if (badVars.size() > 0) {
      boolean plural = badVars.size() > 1;
      throw new CompileError("Function " + funDecl.nameToken.getStringValue() + " closes over variable" +
                             (plural?"s ":" ") + String.join(",", badVars.toArray(String[]::new)) +
                             " that " + (plural?"have":"has") + " not yet been initialised", funDecl.location);
    }

    wrapperFunDecl.heapLocalParams.values().forEach(p -> {
      // Load HeapLocal  (not the value contained in it) from _our_ copy of this
      // var (p.varDecl.slot not p.slot)
      loadLocal(p.parentVarDecl.slot);
      invokeMethod(MethodHandle.class, "bindTo", Object.class);
    });
  }

  private void emitReturn(JacsalType returnType) {
    switch (returnType.getType()) {
      case BOOLEAN:
      case INT:
        mv.visitInsn(IRETURN);
        break;
      case LONG:
        mv.visitInsn(LRETURN);
        break;
      case DOUBLE:
        mv.visitInsn(DRETURN);
        break;
      case DECIMAL:
      case STRING:
      case ANY:
      case INSTANCE:
        mv.visitInsn(ARETURN);
        break;
      default: throw new IllegalStateException("Unexpected type " + returnType);
    }
  }

  private void invokeUserMethod(Expr.FunDecl funDecl) {
    // Note: param types can include heap locals if funDecl has any
    invokeUserMethod(funDecl.functionDescriptor, methodParamTypes(funDecl), false);
  }

  private void invokeUserMethod(FunctionDescriptor functionDescriptor, boolean isInvokeSpecial) {
    // Note: param types based solely off functionDescriptor so no support for heap locals with this call
    invokeUserMethod(functionDescriptor, methodParamTypes(functionDescriptor), isInvokeSpecial);
  }

  private void invokeUserMethod(FunctionDescriptor functionDescriptor, List<JacsalType> paramTypes, boolean isInvokeSpecial) {
    invokeUserMethod(functionDescriptor.isStatic,
                     isInvokeSpecial, functionDescriptor.implementingClass == null ? classCompiler.internalName : functionDescriptor.implementingClass,
                     functionDescriptor.implementingMethod,
                     functionDescriptor.returnType,
                     paramTypes);
    if (functionDescriptor.isWrapper) {
      // Convert Object returned by wrapper back into return type of the function
      checkCast(functionDescriptor.returnType.boxed());
      if (functionDescriptor.returnType.isPrimitive()) {
        unbox();
      }
    }
  }

  private void invokeUserMethod(boolean isStatic, boolean isInvokeSpecial, String internalClassName, String methodName, JacsalType returnType, List<JacsalType> paramTypes) {
    mv.visitMethodInsn(isStatic ? INVOKESTATIC
                                : isInvokeSpecial ? INVOKESPECIAL
                                                  : INVOKEVIRTUAL,
                       internalClassName,
                       methodName,
                       getMethodDescriptor(returnType, paramTypes),
                       false);
    pop(paramTypes.size() + (isStatic ? 0 : 1));
    push(returnType);
  }

  public static String getMethodDescriptor(Expr.FunDecl funDecl) {
    return getMethodDescriptor(funDecl.returnType, methodParamTypes(funDecl));
  }

  private static String getMethodDescriptor(JacsalType returnType, List<JacsalType> paramTypes) {
    // Method descriptor is return type followed by array of parameter types.
    return Type.getMethodDescriptor(returnType.descriptorType(),
                                    paramTypes.stream().map(p -> p.descriptorType()).toArray(Type[]::new));
  }

  public static MethodType getMethodType(Expr.FunDecl funDecl) {
    return MethodType.methodType(funDecl.returnType.classFromType(),
                                 methodParamTypes(funDecl).stream()
                                                          .map(p -> p.classFromType())
                                                          .toArray(Class[]::new));
  }

  private static List<JacsalType> methodParamTypes(Expr.FunDecl funDecl) {
    // We include a HeapLocal object for each heap var param that the function needs
    // before adding a type for each actual parameter.
    var params = Collections.nCopies(funDecl.heapLocalParams.size(), HEAPLOCAL).stream();
    // Add Continuation after HeapLocals for async functions and for wrappers
    if (funDecl.functionDescriptor.isAsync || funDecl.isWrapper) {
      params = Stream.concat(params, Stream.of(CONTINUATION));
    }
    params = Stream.concat(params, funDecl.parameters.stream()
                                                     .map(p -> p.declExpr.isPassedAsHeapLocal ? HEAPLOCAL : p.declExpr.type));
    return params.collect(Collectors.toList());
  }

  // = Type stack

  private void push(Class clss) {
    if (Void.TYPE.equals(clss))          { /* void */                  return; }
    if (Boolean.TYPE.equals(clss))       { push(BOOLEAN);              return; }
    if (Boolean.class.equals(clss))      { push(BOXED_BOOLEAN);        return; }
    if (Integer.TYPE.equals(clss))       { push(INT);                  return; }
    if (Integer.class.equals(clss))      { push(BOXED_INT);            return; }
    if (Long.TYPE.equals(clss))          { push(LONG);                 return; }
    if (Long.class.equals(clss))         { push(BOXED_LONG);           return; }
    if (Double.TYPE.equals(clss))        { push(DOUBLE);               return; }
    if (Double.class.equals(clss))       { push(BOXED_DOUBLE);         return; }
    if (BigDecimal.class.equals(clss))   { push(DECIMAL);              return; }
    if (String.class.equals(clss))       { push(STRING);               return; }
    if (Map.class.equals(clss))          { push(MAP);                  return; }
    if (List.class.equals(clss))         { push(LIST);                 return; }
    if (MethodHandle.class.equals(clss)) { push(FUNCTION);             return; }
    push(ANY);
  }

  private void push(JacsalType type) {
    stack.push(new StackEntry(type));
  }

  /**
   * Return type of top element on stack
   */
  private JacsalType peek() {
    return stack.peek().type;
  }

  /**
   * Set type of top element on stack
   */
  private void setStackType(JacsalType type) {
    stack.peek().type = type;
  }

  private JacsalType pop() {
    var entry = stack.pop();
    freeTemp(entry.slot);
    return entry.type;
  }

  private void pop(int count) {
    for (int i = 0; i < count; i++) {
      pop();
    }
  }

  private void expect(int count) {
    // TODO:
  }

  private void convertStackToLocals() {
    var iter = stack.iterator();
    while (iter.hasNext()) {
      var entry = iter.next();
      if (entry.slot == -1) {
        entry.slot = allocateSlot(entry.type);
        _storeLocal(entry.slot);
      }
    }
  }

  private void dup() {
    var entry = stack.peek();
    if (entry.slot >= 0) {
      locals.get(entry.slot).refCount++;
    }
    stack.push(stack.peek());
  }

  /**
   * Swap types on type stack and also swap values on JVM stack
   */
  private void swap() {
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
        freeTemp(temp2);
        freeTemp(temp1);
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
  private void dupVal() {
    dup();
    int slot = stack.peek().slot;
    if (slot == -1) {
      _dupVal();
    }
  }

  /**
   * Duplicate value on JVM stack but don't touch type stack
   */
  private void _dupVal() {
    mv.visitInsn(peek().is(DOUBLE, LONG) ? DUP2 : DUP);
  }

  /**
   * Duplicate top two elemenets on the stack:
   *   ... x, y ->
   *   ... x, y, x, y
   *
   * Note that we need to take care whether any of the types are long or double
   * since the JVM operation dup2 only duplicates the top two slots of the stack
   * rather than the top two values.
   */
  private void dupVal2() {
    var x = stack.pop();
    var y = stack.pop();

    if (x.slot == -1 && y.slot == -1) {
      JacsalType type1 = x.type;
      JacsalType type2 = y.type;
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
        freeTemp(slot2);
        freeTemp(slot1);
      }
      push(type2);
      push(type1);
      push(type2);
      push(type1);
    }
    else {
      // We have at least one value in local vars
      if (y.slot == -1) {
        stack.push(x);
        locals.get(x.slot).refCount++;
        push(y.type);
        _dupVal();
        stack.push(x);
        push(y.type);
      }
      else {
        push(x.type);
        _dupVal();
        stack.push(y);
        locals.get(y.slot).refCount++;
        push(x.type);
        stack.push(y);
      }
    }
  }

  /**
   * Pop value off JVM stack and corresponding type off type stack
   */
  private void popVal() {
    _popVal();
    pop();
  }

  /**
   * Pop value off JVM stack but don't touch type stack
   */
  private void _popVal() {
    mv.visitInsn(peek().is(DOUBLE, LONG) ? POP2 : POP);
  }

  /**
   * Box value on the stack based on the type
   */
  private void box() {
    box(peek());
    push(pop().boxed());
  }

  private void box(JacsalType type) {
    if (!type.isBoxed()) {
      expect(1);
      Utils.box(mv, type);
    }
  }

  /**
   * Unbox value on the stack based on the type
   */
  private void unbox() {
    unbox(peek());
    push(pop().unboxed());
  }

  private void unbox(JacsalType type) {
    if (type.isPrimitive()) {
      // Nothing to do if not boxed
      return;
    }
    unboxAlways();
  }

  private void unboxAlways() {
    expect(1);
    switch (peek().getType()) {
      case BOOLEAN:
        invokeMethod(Boolean.class, "booleanValue");
        break;
      case INT:
        invokeMethod(Integer.class, "intValue");
        break;
      case LONG:
        invokeMethod(Long.class, "longValue");
        break;
      case DOUBLE:
        invokeMethod(Double.class, "doubleValue");
        break;
      default:
        // For other types (e.g. DECIMAL) don't do anything if asked to unbox
        return;
    }
    push(pop().unboxed());
  }

  ///////////////////////////////////////////////////////////////

  /**
   * Load null onto stack
   * @param type  the type to put onto type stack for the null
   */
  private void loadNull(JacsalType type) {
    _loadConst(null);
    push(type);
  }

  private void loadNullContinuation() {
    _loadConst(null);
    push(CONTINUATION);
  }

  /**
   * Load constant value onto JVM stack and also push type onto type stack.
   * Supports Boolean, numeric types, BigDecimal, String, and null.
   * @param obj  object to load on stack
   */
  private Void loadConst(Object obj) {
    push(_loadConst(obj));
    return null;
  }

  /**
   * Load object onto JVM stack but don't alter type stack
   */
  private JacsalType _loadConst(Object obj) {
    return Utils.loadConst(mv, obj);
  }

  private void loadDefaultValueOrNewInstance(JacsalType type, Location location) {
    if (type.is(INSTANCE)) {
      // If there is no no-arg constructor (i.e. there is at least one mandatory field) then we have to throw
      // a NullError since field is currently null
      FunctionDescriptor initMethod = type.getClassDescriptor().getInitMethod();
      if (initMethod.paramCount > 0) {
        throwNullError("Null value during field access and type " + type + " cannot be auto-created due to mandatory fields", location);
      }
      else {
        invokeMaybeAsync(initMethod.isAsync, type, 0, location,
                         () -> {
                           newInstance(type);
                           if (initMethod.isAsync) {
                             loadNullContinuation();
                           }
                         },
                         () -> {
                           invokeUserMethod(initMethod, false);
                         });
      }
      return;
    }

    // Other types we fall back to default value
    loadDefaultValue(type);
  }

  private void loadDefaultValue(JacsalType type) {
    switch (type.getType()) {
      case BOOLEAN:     loadConst(Boolean.FALSE);     break;
      case INT:         loadConst(0);            break;
      case LONG:        loadConst(0L);           break;
      case DOUBLE:      loadConst( 0D);          break;
      case DECIMAL:     loadConst(BigDecimal.ZERO);   break;
      case STRING:      loadConst("");           break;
      case ANY:         loadConst(null);         break;
      case INSTANCE:    loadConst(null);         break;
      case OBJECT_ARR:  loadConst(null);         break;
      case FUNCTION:    loadConst(null);         break;   // use null for the moment to indicate identity function
      case MATCHER:
        _newInstance(RegexMatcher.class);
        push(MATCHER);
        break;
      case MAP:
        _newInstance(Utils.JACSAL_MAP_TYPE);
        push(MAP);
        break;
      case LIST:
        _newInstance(Utils.JACSAL_LIST_TYPE);
        push(LIST);
        break;
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  /**
   * Convert given int to actual type required
   * @param type   type needed
   * @param value  int value to turn into type
   * @return the Object for the value
   */
  private Object valueOf(JacsalType type, int value) {
    switch (type.getType()) {
      case INT:      return value;
      case LONG:     return (long)value;
      case DOUBLE:   return (double)value;
      case DECIMAL:  return BigDecimal.valueOf(value);
      default:       throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  ///////////////////////////////////////////////////////////////

  // = Conversions

  private void castToIntOrLong(JacsalType type, Location location) {
    if (peek().isBoxedOrUnboxed(INT,LONG)) {
      // If we already have a primitive int/long then we can convert using standard mechanism
      convertTo(type, null, false, location);
      return;
    }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, type.is(INT) ? "castToIntValue" : "castToLongValue", Object.class, String.class, int.class);
      return;
    }
    throw new IllegalStateException("Internal error: unexpected type " + peek());
  }

  private Void convertTo(JacsalType type, Expr expr, boolean couldBeNull, Location location) {
    expect(1);
    if (type.isBoxedOrUnboxed(BOOLEAN)) { return convertToBoolean(); }   // null is valid for boolean conversion
    if (type.isPrimitive() && !peek().isPrimitive() && couldBeNull) {
      if (expr.isNull()) {
        throw new CompileError("Cannot convert null value to " + type, location);
      }
      throwIfNull("Cannot convert null value to " + type, location);
    }
    switch (type.getType()) {
      case INT:        return convertToInt(location);
      case LONG:       return convertToLong(location);
      case DOUBLE:     return convertToDouble(location);
      case DECIMAL:    return convertToDecimal(location);
      case STRING:     return castToString(location);
      case MAP:        return castToMap(location);
      case LIST:       return castToList(location);
      case ANY:        return convertToAny(location);
      case FUNCTION:   return castToFunction(location);
      case OBJECT_ARR: return castToObjectArr(location);
      case ITERATOR:   return null;  // noop
      case INSTANCE:   return castToInstance(type, expr, location);
      default:         throw new IllegalStateException("Unknown type " + type);
    }
  }

  private Void castToInstance(JacsalType type, Expr expr, Location location) {
    if (peek().is(INSTANCE) && peek().getInternalName().equals(type.getInternalName())) {
      return null;
    }
    if (peek().is(ANY,LIST,MAP)) {
      convertToInstance(type, expr, location, location);
      return null;
    }
    if (peek().is(INSTANCE)) {
      if (peek().isConvertibleTo(type)) {
        // Nothing to do. Already of correct type.
        return null;
      }
    }
    throw new CompileError("Cannot convert from " + peek() + " to " + type, location);
  }

  /**
   * Convert to boolean but first check if source expression is valid to be converted.
   * We don't allow regex strings to be used in a boolean context to avoid confusion
   * between a regex string and a regex match.
   */
  private void convertToBoolean(boolean negated, Expr expr) {
    if (expr instanceof Expr.RegexMatch && ((Expr.RegexMatch) expr).string == null) {
      throw new CompileError("Regex string used in boolean context - add modifier if regex match required", expr.location);
    }
    convertToBoolean(negated);
  }

  private Void convertToBoolean() {
    return convertToBoolean(false);
  }

  /**
   * Convert to boolean and leave a 1 or 0 based on whether truthiness is satisfied.
   * If negated is true we invert the sense of the conversion so that we leave 0 on
   * the stack for "true" and 1 for "false". This allows us to check for "falsiness"
   * more efficiently.
   * @param negated  if true conversion is negated
   */
  private Void convertToBoolean(boolean negated) {
    expect(1);

    Consumer<Integer> emitConvertToBoolean = opCode -> {
      Label isZero = new Label();
      Label end    = new Label();
      mv.visitJumpInsn(opCode, isZero);
      _loadConst(negated ? 0 : 1);
      mv.visitJumpInsn(GOTO, end);
      mv.visitLabel(isZero);
      _loadConst(negated ? 1 : 0);
      mv.visitLabel(end);
    };

    // For any type that is an Object
    if (!peek().isPrimitive()) {
      loadConst(negated ? true : false);
      invokeMethod(RuntimeUtils.class, "isTruth", Object.class, Boolean.TYPE);
      return null;
    }

    switch (peek().getType()) {
      case BOOLEAN:  if (negated) { _booleanNot(); }      break;
      case INT:      emitConvertToBoolean.accept(IFEQ);   break;
      case LONG: {
        _loadConst((long)0);
        mv.visitInsn(LCMP);      // Leave 0 on stack if equal to 0
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      case DOUBLE: {
        _loadConst((double)0);
        mv.visitInsn(DCMPL);
        emitConvertToBoolean.accept(IFEQ);
        break;
      }
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }

    pop();
    push(BOOLEAN);
    return null;
  }

  private Void convertToInt(SourceLocation location) {
    if (peek().is(ANY,STRING)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToInt", Object.class, String.class, Integer.TYPE);
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeMethod(Number.class, "intValue");
    }
    else {
      switch (peek().getType()) {
        case BOOLEAN:
        case INT:                          break;
        case LONG:    mv.visitInsn(L2I);   break;
        case DOUBLE:  mv.visitInsn(D2I);   break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(INT);
    return null;
  }

  private Void convertToLong(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToNumber", Object.class, String.class, Integer.TYPE);
      invokeMethod(Number.class, "longValue");
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeMethod(Number.class, "longValue");
    }
    else {
      switch (peek().getType()) {
        case INT:     mv.visitInsn(I2L);   break;
        case LONG:                         break;
        case DOUBLE:  mv.visitInsn(D2L);   break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(LONG);
    return null;
  }

  private Void convertToDouble(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToNumber", Object.class, String.class, Integer.TYPE);
      invokeMethod(Number.class, "doubleValue");
    }
    else
    if (peek().isBoxed() || peek().is(DECIMAL)) {
      mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
      invokeMethod(Number.class, "doubleValue");
    }
    else {
      switch (peek().getType()) {
        case INT:     mv.visitInsn(I2D);   break;
        case LONG:    mv.visitInsn(L2D);   break;
        case DOUBLE:                       break;
        default:     throw new IllegalStateException("Internal error: unexpected type " + peek());
      }
    }
    pop();
    push(DOUBLE);
    return null;
  }

  private Void convertToDecimal(SourceLocation location) {
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToDecimal", Object.class, String.class, Integer.TYPE);
    }
    else
    switch (peek().getType()) {
      case INT:
      case LONG:
        convertToLong(location);
        invokeMethod(BigDecimal.class, "valueOf", Long.TYPE);
        break;
      case DOUBLE:
        convertToDouble(location);
        invokeMethod(BigDecimal.class, "valueOf", Double.TYPE);
        break;
      case DECIMAL:
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
    pop();
    push(DECIMAL);
    return null;
  }

  private Void castToMap(SourceLocation location) {
    if (peek().is(MAP)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToMap", Object.class, String.class, Integer.TYPE);
      return null;
    }
    throw new IllegalStateException("Internal error: unexpected type " + peek());
  }

  private Void castToList(SourceLocation location) {
    if (peek().is(LIST)) { return null; }
    if (peek().is(ITERATOR,ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToList", Object.class, String.class, Integer.TYPE);
      return null;
    }
    return null;
  }

  private Void castToFunction(SourceLocation location) {
    if (peek().is(FUNCTION)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToFunction", Object.class, String.class, Integer.TYPE);
      return null;
    }
    return null;
  }

  private Void castToObjectArr(SourceLocation location) {
    if (peek().is(OBJECT_ARR)) { return null; }
    if (peek().is(ANY)) {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "castToObjectArr", Object.class, String.class, Integer.TYPE);
      return null;
    }
    return null;
  }

  private Void convertToAny(SourceLocation location) {
    box();
    pop();
    push(ANY);
    return null;
  }

  private void _booleanNot() {
    _loadConst(1);
    mv.visitInsn(IXOR);
  }

  private void arithmeticNot(Token location) {
    expect(1);
    unbox();
    switch (peek().getType()) {
      case INT:
        _loadConst((int)-1);
        mv.visitInsn(IXOR);
        break;
      case LONG:
        _loadConst((long)-1);
        mv.visitInsn(LXOR);
        break;
      case ANY:
        loadLocation(location);
        invokeMethod(RuntimeUtils.class, "arithmeticNot", Object.class, String.class, int.class);
        break;
      default: throw new IllegalStateException("Internal error: Unexpected type " + peek().getType() + " for '~'");
    }
  }

  private void arithmeticNegate(Token location) {
    expect(1);
    unbox();
    switch (peek().getType()) {
      case INT:     mv.visitInsn(INEG);  break;
      case LONG:    mv.visitInsn(LNEG);  break;
      case DOUBLE:  mv.visitInsn(DNEG);  break;
      case DECIMAL:
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/math/BigDecimal", "negate", "()Ljava/math/BigDecimal;", false);
        break;
      case ANY:
        loadConst(classCompiler.source);
        loadConst(location.getOffset());
        invokeMethod(RuntimeUtils.class, "negateNumber", Object.class, String.class, Integer.TYPE);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
  }

  private void incOrDec(boolean isPrefix, boolean isInc, Expr expr, boolean isResultUsed, Token operator) {
    if (expr instanceof Expr.Identifier) {
      incOrDecVar(isPrefix, isInc, (Expr.Identifier)expr, 1, isResultUsed, operator);
    }
    else {
      compile(expr);
      if (!isPrefix) {
        if (isResultUsed) {
          dupVal();
        }
        incOrDecValue(isInc, 1, operator);
        popVal();
      }
      else {
        incOrDecValue(isInc, 1, operator);
        convertTo(expr.type, expr, true, operator);
        if (!isResultUsed) {
          popVal();
        }
      }
    }
  }

  /**
   * Increment or decrement variable by given amount.
   * Used for --,++ and for += and -= operations
   * @param isPrefix          whether we are a prefix or postfix -- or ++
   * @param isInc             whether we are incrementing/adding or decrementing/subtracting
   * @param identifierExpr    the Expr.Identifier for the local variable
   * @param amount            the amount to inc/dec or null if amount is already on the stack
   * @param isResultUsed      whether the result is used after the inc/dec
   * @param operator          operator
   */
  private void incOrDecVar(boolean isPrefix, boolean isInc, Expr.Identifier identifierExpr, Object amount, boolean isResultUsed, Token operator) {
    Expr.VarDecl varDecl = identifierExpr.varDecl;
    // If postfix and we use the value then load value before doing inc/dec so we leave
    // old value on the stack
    if (!isPrefix && isResultUsed) {
      loadVar(varDecl);
    }

    // Helper to do the inc/dec based on the type of the value
    Consumer<Runnable> incOrDec = (runnable) -> {
      loadVar(varDecl);
      unbox();

      // If amount is null then value is already on the stack so swap otherwise
      // load the inc/dec amount
      if (amount == null) {
        swap();
        convertTo(varDecl.type, null, true, operator);
      }
      else {
        loadConst(Utils.convertNumberTo(varDecl.type, amount));
      }

      runnable.run();
      storeVar(varDecl);
    };

    switch (varDecl.type.getType()) {
      case INT:
        if (varDecl.type.isBoxed() || varDecl.isHeapLocal || varDecl.isField || amount == null) {
          incOrDec.accept(() -> { mv.visitInsn(isInc ? IADD : ISUB); pop(); });
        }
        else {
          int intAmt = (int)Utils.convertNumberTo(INT, amount);
          mv.visitIincInsn(varDecl.slot, isInc ? intAmt : -intAmt);
        }
        break;
      case LONG:
        incOrDec.accept(() -> { mv.visitInsn(isInc ? LADD : LSUB); pop(); });
        break;
      case DOUBLE:
        incOrDec.accept(() -> { mv.visitInsn(isInc ? DADD : DSUB); pop(); });
        break;
      case DECIMAL:
        incOrDec.accept(() -> invokeMethod(BigDecimal.class, isInc ? "add" : "subtract", BigDecimal.class));
        break;
      case ANY:
      case STRING:
      case LIST:
        // When we don't know the type
        loadVar(varDecl);
        if (amount == null) {
          swap();
        }
        incOrDecValue(isInc, amount, operator);
        storeVar(varDecl);
        break;
      default:
        throw new IllegalStateException("Internal error: unexpected type " + varDecl.type);
    }
    if (isPrefix && isResultUsed) {
      loadVar(varDecl);
    }
  }

  /**
   * Increment or decrement by given amount the current value on the stack.
   * If amount is null then amount to inc/dec is also on the stack.
   * @param isInc    true if doing inc (false for dec)
   * @param amount   amount to inc/dec or null if value is already on the stack
   * @param operator the increment or decrement operator
   */
  private void incOrDecValue(boolean isInc, Object amount, Token operator) {
    unbox();
    if (peek().is(ANY,STRING,LIST)) {
      if (amount != null) {
        // Special case if amount is 1
        if (amount instanceof Integer && (int)amount == 1) {
          // Note we could actually have a String or List when doing x = x + 1 or x++ or x+= 1
          // so we pass in the operator so we can work out whether we have an error or not
          // since a String or List with ++ should be an error
          loadConst(RuntimeUtils.getOperatorType(operator.getType()));
          loadLocation(operator);
          invokeMethod(RuntimeUtils.class, isInc ? "incNumber" : "decNumber", Object.class, String.class, String.class, Integer.TYPE);
          return;
        }

        loadConst(amount);
        box();
      }

      // Invoke binary + or - as needed
      loadConst(RuntimeUtils.getOperatorType(isInc ? PLUS : MINUS));
      loadConst(RuntimeUtils.getOperatorType(operator.getType()));
      loadConst(classCompiler.context.maxScale);
      loadConst(classCompiler.source);
      loadConst(operator.getOffset());
      invokeMethod(RuntimeUtils.class, isInc ? "plus" : "minus", Object.class, Object.class, String.class, String.class, Integer.TYPE, String.class, Integer.TYPE);
      return;
    }

    if (amount != null) {
      loadConst(valueOf(peek(), 1));
    }
    switch (peek().getType()) {
      case INT:     mv.visitInsn(isInc ? IADD : ISUB);   pop();                                    break;
      case LONG:    mv.visitInsn(isInc ? LADD : LSUB);   pop();                                    break;
      case DOUBLE:  mv.visitInsn(isInc ? DADD : DSUB);   pop();                                    break;
      case DECIMAL: invokeMethod(BigDecimal.class, isInc ? "add" : "subtract", BigDecimal.class); break;
      default:      throw new IllegalStateException("Internal error: unexpected type " + peek());
    }
  }

  private void convertToString() {
    if (peek().is(STRING)) {
      return;
    }
    Label end = null;
    if (peek().isPrimitive()) {
      box();
    }
    invokeMethod(RuntimeUtils.class, "toString", Object.class);
    if (end != null) {
      mv.visitLabel(end);
    }
  }

  private void convertToStringOrNull() {
    if (peek().is(STRING)) {
      return;
    }
    expect(1);
    if (peek().isPrimitive()) {
      box();
    }
    invokeMethod(RuntimeUtils.class, "toStringOrNull", Object.class);
  }

  private Void castToString(SourceLocation location) {
    expect(1);
    if (peek().is(STRING)) {
      return null;
    }
    Label end = null;
    if (peek().isPrimitive()) {
      box();
    }
    loadLocation(location);
    invokeMethod(RuntimeUtils.class, "castToString", Object.class, String.class, int.class);
    if (end != null) {
      mv.visitLabel(end);
    }
    return null;
  }

  private void isInstanceOf(JacsalType type) {
    expect(1);
    mv.visitInsn(DUP);
    mv.visitTypeInsn(INSTANCEOF, type.getInternalName());
  }

  private void isInstanceOf(Class clss) {
    expect(1);
    mv.visitInsn(DUP);
    mv.visitTypeInsn(INSTANCEOF, Type.getInternalName(clss));
  }

  private void emitInstanceof(JacsalType type) {
    expect(1);
    mv.visitTypeInsn(INSTANCEOF, type.getInternalName());
  }

  private void throwIfNull(String msg, SourceLocation location) {
    expect(1);
    _dupVal();
    Label isNotNull = new Label();
    mv.visitJumpInsn(IFNONNULL, isNotNull);
    _popVal();
    throwNullError(msg, location);
    mv.visitLabel(isNotNull);
  }

  private void throwNullError(String msg, SourceLocation location) {
    mv.visitTypeInsn(NEW, "jacsal/runtime/NullError");
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocation(location);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/NullError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
  }

  /**
   * Throw error if false and preprend class name of stack arg to message.
   * Expect stack: ...,value,true/false
   * If true then stack will be: ...,value
   * If false then value and boolean consumed and RuntimeError thrown,
   */
  private void throwIfFalseWithClassName(String msg, SourceLocation location) {
    Label isTrue = new Label();
    mv.visitJumpInsn(IFNE, isTrue);
    mv.visitMethodInsn(INVOKESTATIC, "jacsal/runtime/RuntimeUtils", "className", "(Ljava/lang/Object;)Ljava/lang/String;", false);
    _loadConst(msg);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
    int temp = allocateSlot(STRING);
    _storeLocal(temp);
    mv.visitTypeInsn(NEW, "jacsal/runtime/RuntimeError");
    mv.visitInsn(DUP);
    _loadLocal(temp);
    _loadLocation(location);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
    mv.visitLabel(isTrue);
    freeTemp(temp);
  }

  private void throwError(String msg, SourceLocation location) {
    mv.visitTypeInsn(NEW, Type.getInternalName(RuntimeError.class));
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocation(location);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
  }

  private void throwError(String msg, int sourceSlot, int offsetSlot) {
    mv.visitTypeInsn(NEW, "jacsal/runtime/RuntimeError");
    mv.visitInsn(DUP);
    _loadConst(msg);
    _loadLocal(sourceSlot);
    _loadLocal(offsetSlot);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    mv.visitInsn(ATHROW);
  }

  /**
   * Emit code for a try/catch statement.
   * @param exceptionClass  the class to catch
   * @param tryBlock        Runnable that emits code for the try block
   * @param catchBlock      Runnable that emits code for the catch block
   */
  private void tryCatch(Class exceptionClass, Runnable tryBlock, Runnable catchBlock) {
    // We need to check for any values that are on the stack at the point that the try/catch
    // is to be used since the JVM will discard these values if an exception is thrown and
    // will leave just the exception on the stack at the point the exception is caught.
    // If we have values on the stack we save them in temp locations and then put them back
    // onto the stack for the try block to use. After the catch we also restore them so that
    // code in the catch block has the same stack as the code in the try block.
    int[] temps = new int[stack.size()];
    if (stack.size() > 0) {
      for (int i = 0; i < temps.length; i++) {
        temps[i] = allocateSlot(peek());
        storeLocal(temps[i]);
      }
    }

    // Restore stack by loading values from the locals we have stored them in
    Runnable restoreStack = () -> {
      // Restore in reverse order
      for (int i = temps.length - 1; i >= 0; i--) {
        loadLocal(temps[i]);
      }
    };

    // If we were in a try/catch then terminate previous one (we will reinstate later)
    if (tryCatches.size() > 0) {
      mv.visitLabel(tryCatches.peek().tryBlockEnd);
    }

    Label blockStart = new Label();
    Label blockEnd   = new Label();
    Label catchLabel = new Label();
    mv.visitTryCatchBlock(blockStart, blockEnd, catchLabel, Type.getInternalName(exceptionClass));

    tryCatches.push(new TryCatch(exceptionClass, blockEnd, catchLabel));

    mv.visitLabel(blockStart);   // :blockStart

    // It is possible that we have two conditional expressions nested such that we end the first try block without
    // any instructions in it before starting the nested block. This causes a bytecode validation error so we insert
    // a NOP just to make sure that this can't happen.
    mv.visitInsn(NOP);

    restoreStack.run();
    tryBlock.run();

    TryCatch myTryCatch = tryCatches.pop();

    Label after = new Label();
    mv.visitJumpInsn(GOTO, after);
    mv.visitLabel(myTryCatch.tryBlockEnd);     // :blockEnd
    mv.visitLabel(catchLabel);                 // :catchLabel

    // If we were already in a try/catch then restore the previous try catch
    if (tryCatches.size() > 0) {
      TryCatch tryCatch = tryCatches.peek();
      Label newStart = new Label();
      Label newEnd = new Label();
      tryCatch.tryBlockEnd = newEnd;
      mv.visitTryCatchBlock(newStart, tryCatch.tryBlockEnd, tryCatch.catchBlock, Type.getInternalName(exceptionClass));
      mv.visitLabel(newStart);
    }

    // Since the catch block runs with the stack being wiped we need to simulate the same
    // thing with our type stack to track what would be happening on the JVM stack. We also
    // verify that the stack at the end of the try block and the stack at the end of the
    // catch block look the same otherwise we will have problems with the JVM code validation
    // that expects that the stack is the same at any point where two different code paths
    // meet, irrespective of which path was chosen.
    // We save the type stack we have after the try block and restore the stack ready for the
    // catch block.
    mv.visitInsn(POP);           // Don't need the exception
    var savedStack = stack;
    stack = new ArrayDeque<>();
    restoreStack.run();
    catchBlock.run();
    mv.visitLabel(after);

    // Validate that the two stacks are the same
    check(Utils.stacksAreEqual(stack, savedStack), "Stack after try does not match stack after catch: tryStack=" + savedStack + ", catchStack=" + stack);

    // Free our temps
    Arrays.stream(temps).forEach(i -> freeTemp(i));
  }

  /**
   * Emit code for an async call. We need to capture our state (stack and locals) before
   * invoking the potentially async code so that if it throws a Continuation exception
   * we can then store our state in a Continuation for later resumption.
   * <p>There are two runnables passed to this function:</p>
   * <ul>
   * <li>compileArgs: a runnable that emits code that compiles the arguments for the function</li>
   * <li>invoker:     a runnable that emits the code for the actual invocation</li>
   * </ul>
   * <p>The reason for needing two runnables is that the code for the arguments does not need to be wrapped in the
   * try/catch that we need for the async function invocation. If an argument expression has its own async invocation
   * then that will be dealt with during the argument expression compilation separately. By splitting into two runnables
   * we can insert the try/catch in the right place.</p>
   * <p>Note that the stack values for the arguments are not restored on resumption since the arguments are consumed by
   * the function invocation itself.</p>
   * <p>We also allow a number to be passed that indicate how many of the existing stack values form part of the arugments
   * for the function invocation. Usually this is 0 but there are occasions where the result of a previous call is used
   * as an argument to a subsequent async call. We need to be aware of this so that we don't restore those argument
   * stack values after a continue.</p>
   * @param isAsync                true if async invocation required
   * @param returnType             the type that the invoked function returns
   * @param numArgsAlreadyOnStack  how many arguments for call are already on the stack
   * @param location               caller location
   * @param compileArgs            Runnable that emits code for compiling expressions for the args
   * @param invoker                Runnable that emits code for the method/function invocation
   */
  private void invokeMaybeAsync(boolean isAsync, JacsalType returnType, int numArgsAlreadyOnStack, Location location, Runnable compileArgs, Runnable invoker) {
    if (!isAsync) {
      compileArgs.run();
      invoker.run();
      return;
    }

    // We need to create two arrays to store our stack and locals in:
    //  - one for all the primitives, and
    //  - one for al the objects
    // Note that we store boolean, int, long, and double values all in an array of longs.
    // We create two arrays of the same length (locals size + stack size) and then we have
    // a consistent indexing across both arrays. The type of the stack/local var dictates
    // which array it is stored in (meaning that there is always a wasted entry in the other
    // array for every stack/local var). Note that the indexes of the locals will not match
    // because we use two slots for long/double in locals but only one slot in the long[].
    // NOTE: we don't bother with preserving the "this" (if non-static) and the Continuation
    // object since the MethodHandle will be bound to "this" and the Continuation is supplied
    // when resuming so the current value (usually null) is irrelevant.
    int startSlot = methodFunDecl.isStatic() ? 0 : 1;
    int numLocals = numLocals() - startSlot - 1;
    int size = numLocals + stack.size();
    int longArr = allocateSlot(LONG_ARR);
    int objArr  = allocateSlot(OBJECT_ARR);
    _loadConst(size);
    mv.visitIntInsn(NEWARRAY, T_LONG);
    _storeLocal(longArr);
    _loadConst(size);
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    _storeLocal(objArr);
    var savedStack = new ArrayDeque<>(stack);
    int               slot       = startSlot;   // skip "this"
    for (int i = 0; i < size; i++) {
      if (slot == continuationVar) {
        slot++;                                 // skip Continuation
      }
      final var  isLocalVar = i < numLocals;
      JacsalType type       = isLocalVar ? localsType(slot) : peek();
      loadLocal(type.isPrimitive() ? longArr : objArr);
      if (isLocalVar) {
        // Local var
        loadConst(i);
        loadLocal(slot++);
      }
      else {
        // Stack var
        swap();
        loadConst(i);
        swap();
      }
      if (type.isPrimitive()) {
        if (type.is(BOOLEAN,INT)) { mv.visitInsn(I2L); }
        if (type.is(DOUBLE))      { invokeMethod(Double.class, "doubleToRawLongBits", double.class); }
        if (type.is(LONG,DOUBLE) && isLocalVar) { slot++; }
      }
      mv.visitInsn(type.isPrimitive() ? LASTORE : AASTORE);
      pop(3);
    }

    // Restore stack by loading values from the locals we have stored them in
    Runnable restoreStack = () -> {
      // Restore in reverse order
      var iter = savedStack.descendingIterator();
      for (int i = size - 1; i >= numLocals; i--) {
        JacsalType type = iter.next().type;
        Utils.loadStoredValue(mv, i, type, () -> _loadLocal(type.isPrimitive() ? longArr : objArr));
        push(type);
      }
    };

    restoreStack.run();

    // Compile the args to the function before starting the try/catch block since the async part is in the
    // invocation of the function. If the args compilation requires async behaviour then it will take care
    // of it when compining the expression for the argument.
    compileArgs.run();

    Label blockStart = new Label();
    Label blockEnd   = new Label();
    Label catchLabel = new Label();
    mv.visitTryCatchBlock(blockStart, blockEnd, catchLabel, Type.getInternalName(Continuation.class));

    mv.visitLabel(blockStart);   // :blockStart
    final var methodLocation = asyncLocations.size();
    Label continuation = new Label();
    asyncLocations.add(continuation);

    // Add a runnable for this continuation that restores state. This will be invoked in switch statement that is
    // run when resuming (see MethodCompiler::compile()) before jumping back to place in code where we resume from.
    List<LocalEntry> savedLocals = new ArrayList<>(locals);
    Runnable restoreState = () -> {
      // Restore locals and stack and put result on the stack.
      // No need to restore any parameters as they have been done by the continuation wrapper
      int slot2 = startSlot;
      for (int i = 0; i < numLocals; i++) {
        if (slot2 == continuationVar) {
          slot2++;           // skip Continuation
        }
        JacsalType type = savedLocals.get(slot2).type;
        Utils.loadStoredValue(mv, i, type, () -> Utils.loadContinuationArray(mv, continuationVar, type));
        _storeLocal(type, slot2);
        setSlot(slot2, type);
        slot2 += slotsNeeded(type);
      }
      // Restore stack in reverse order. Don't restore any args that were already on the stack.
      var iter = savedStack.descendingIterator();
      for (int i = size - 1; i >= numLocals + numArgsAlreadyOnStack; i--) {
        JacsalType type = iter.next().type;
        Utils.loadStoredValue(mv, i, type, () -> Utils.loadContinuationArray(mv, continuationVar, type));
      }
      _loadLocal(continuationVar);
      mv.visitFieldInsn(GETFIELD, "jacsal/runtime/Continuation", "result", "Ljava/lang/Object;");
      push(ANY);
      if (returnType.isPrimitive()) {
        convertTo(returnType, null, false, location);
      }
      else {
        checkCast(returnType);
      }
      pop();
    };
    asyncStateRestorations.add(restoreState);

    invoker.run();

    Label after = new Label();
    mv.visitJumpInsn(GOTO, after);
    mv.visitLabel(blockEnd);     // :blockEnd

    mv.visitLabel(catchLabel);   // :catchLabel
    int contVar = allocateSlot(CONTINUATION);
    _storeLocal(contVar);
    // Need to create a new Continuation chained to the one we caught and save our state in it
    mv.visitTypeInsn(NEW, "jacsal/runtime/Continuation");
    mv.visitInsn(DUP);
    _loadLocal(contVar);
    _loadClassField(classCompiler.internalName, Utils.continuationHandle(methodFunDecl.functionDescriptor.implementingMethod), FUNCTION, true);
    if (!methodFunDecl.isStatic()) {
      _loadLocal(0);    // this
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
    }
    _loadConst(methodLocation);
    _loadLocal(longArr);
    _loadLocal(objArr);
    mv.visitMethodInsn(INVOKESPECIAL, "jacsal/runtime/Continuation", "<init>", "(Ljacsal/runtime/Continuation;Ljava/lang/invoke/MethodHandle;I[J[Ljava/lang/Object;)V", false);
    mv.visitInsn(ATHROW);

    mv.visitLabel(continuation);       // :continuation

    mv.visitLabel(after);              // :after

    // Free our temps
    freeTemp(contVar);
    freeTemp(longArr);
    freeTemp(objArr);
  }

  private int numLocals() {
    int lastLocal = 0;
    for (int i = locals.size() - 1; i >= 0; i--) {
      JacsalType type = localsType(i);
      if (type != null) {
        lastLocal = type == ANY && i > 0 && localsType(i-1).is(LONG,DOUBLE) ? i - 1 : i;
        break;
      }
    }
    // Now count them, skipping empty slots
    int localsSize = 0;
    for (int i = 0; i <= lastLocal; i++) {
      JacsalType type = localsType(i);
      if (type != null) {
        localsSize++;
        if (type.is(LONG,DOUBLE)) {
          i++;
        }
      }
    }
    return localsSize;
  }

  //////////////////////////////////////////////////////

  // = Method invocation

  private void invokeMethod(Class<?> clss, String methodName, Class<?>... paramTypes) {
    Method method = findMethod(clss, methodName, paramTypes);
    if (Modifier.isStatic(method.getModifiers())) {
      expect(paramTypes.length);
      mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(clss), methodName, Type.getMethodDescriptor(method), false);
      pop(paramTypes.length);
    }
    else {
      expect(paramTypes.length + 1);
      boolean isInterface = clss.isInterface();
      mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL, Type.getInternalName(clss), methodName,Type.getMethodDescriptor(method), isInterface);
      pop(paramTypes.length + 1);
    }
    if (!method.getReturnType().equals(void.class)) {
      push(method.getReturnType());
    }
  }

  /**
   * Search class for method for given name and given parameter types. If parameter types not supplied
   * then expects that there is only one method of given name.
   * @param clss       class containing method
   * @param methodName name of the method
   * @param paramTypes optional array of paramter types to narrow down search if multiple methods
   */
  private Method findMethod(Class<?> clss, String methodName, Class<?>... paramTypes) {
    try {
      return clss.getDeclaredMethod(methodName, paramTypes);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Internal error: could not find static method " + methodName + " for class " +
                                      clss.getName() + " with param types " + Arrays.toString(paramTypes));
    }
  }

  //////////////////////////////////////////////////////

  // = Local variables

  /**
   * Define a variable. It will either be a field, a local slot variable, or a heap variable
   * if it has been closed over.
   * For local vars of type long and double we use two slots. All others use one slot.
   */
  private void defineVar(Expr.VarDecl var) {
    // Note that we predefine nested functions so to make sure we don't redefine them just check to make sure
    // we don't already have a slot allocated
    if (var.isGlobal || var.slot != -1) {
      return;   // Nothing to do for globals or vars that already have a slot
    }

    JacsalType type = var.isHeapLocal ? HEAPLOCAL : var.type;
    mv.visitLabel(var.declLabel = new Label());   // Label for debugger
    var.slot = allocateSlot(type);

    // If var is a parameter then we bump the minimum slot that we use when searching
    // for a free slot since we will never unallocate parameters so there is no point
    // searching those slots for a free one
    if (var.isParam) {
      minimumSlot = locals.size();
    }

    // Store an empty HeapLocal into the slot if HEAPLOCAL and not already passed as HeapLocal parameter
    if (type.is(HEAPLOCAL) && !var.isPassedAsHeapLocal && !var.isParam) {
      allocateHeapLocal(var.slot);
    }
  }

  /**
   * Undefine variable when no longer needed
   */
  private void undefineVar(Expr.VarDecl varDecl, Label endBlock) {
    if (varDecl.slot == -1 || varDecl.isField) {
      return;
    }
    JacsalType type = varDecl.isHeapLocal ? ANY : varDecl.type;
    mv.visitLocalVariable(varDecl.name.getStringValue(), type.descriptor(), null,
                          varDecl.declLabel, endBlock, varDecl.slot);
    freeSlot(varDecl.slot);
    varDecl.slot = -1;
  }

  private void freeSlot(int slot) {
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
  private int saveInTemp() {
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
  private int allocateSlot(JacsalType type) {
    int i;
    for (i = minimumSlot; i < locals.size(); i += slotsNeeded(localsType(i))) {
      if (slotsFree(i, slotsNeeded(type))) {
        break;
      }
    }
    setSlot(i, type);
    return i;
  }

  /**
   * When no longer needed free up slot(s) used for a temporary value.
   * @param slot the slot where temp resided
   */
  private void freeTemp(int slot) {
    if (slot == -1) { return; }
    freeSlot(slot);
  }

  private static int slotsNeeded(JacsalType type) {
    if (type == null) {
      return 1;
    }
    return type.is(LONG,DOUBLE) ? 2 : 1;
  }

  /**
   * Check if there are num free slots at given index
   */
  private boolean slotsFree(int idx, int num) {
    while (idx < locals.size() && num > 0 && localsType(idx) == null) {
      idx++;
      num--;
    }
    return num == 0;
  }

  private void setSlot(int idx, JacsalType type) {
    // Grow array if needed
    ntimes(idx + slotsNeeded(type) - locals.size(), () -> locals.add(null));

    // Set first slot to type. For multi-slot types others are marked as ANY.
    for(int i = 0; i < slotsNeeded(type); i++) {
      var entry = new LocalEntry();
      entry.type = i == 0 ? type : ANY;
      locals.set(idx + i, entry);
    }
  }

  private void loadGlobals() {
    loadLocal(0);
    loadClassField(classCompiler.internalName, Utils.JACSAL_GLOBALS_NAME, JacsalType.MAP, false);
  }

  private void loadClassField(String internalClassName, String fieldName, JacsalType type, boolean isStatic) {
    _loadClassField(internalClassName, fieldName, type, isStatic);
    if (!isStatic) {
      pop();
    }
    push(type);
  }

  private void _loadClassField(String internalClassName, String fieldName, JacsalType type, boolean isStatic) {
    if (isStatic) {
      mv.visitFieldInsn(GETSTATIC, internalClassName, fieldName, type.descriptor());
    }
    else {
      mv.visitFieldInsn(GETFIELD, internalClassName, fieldName, type.descriptor());
    }
  }

  private void storeClassField(String fieldName, JacsalType type, boolean isStatic) {
    storeClassField(classCompiler.internalName, fieldName, type, isStatic);
  }

  private void storeClassField(String internalClassName, String fieldName, JacsalType type, boolean isStatic) {
    int stackCount = isStatic ? 1 : 2;
    expect(stackCount);
    _storeClassField(internalClassName, fieldName, type, isStatic);
    pop(stackCount);
  }

  private void _storeClassField(String internalClassName, String fieldName, JacsalType type, boolean isStatic) {
    if (isStatic) {
      mv.visitFieldInsn(PUTSTATIC, internalClassName, fieldName, type.descriptor());
    }
    else {
      mv.visitFieldInsn(PUTFIELD, internalClassName, fieldName, type.descriptor());
    }
  }

  /**
   * Load variable from slot or globals or HeapLocal as required.
   */
  private void loadVar(Expr.VarDecl varDecl) {
    if (varDecl.isGlobal) {
      loadGlobals();
      loadConst(varDecl.name.getValue());
      invokeMethod(Map.class, "get", Object.class);
      checkCast(varDecl.type);
      pop();
      push(varDecl.type);
      return;
    }

    if (varDecl.slot < 0 && varDecl.isField) {
      if (varDecl.funDecl == null) {
        // Actual field
        loadLocal(0);
        loadClassField(classCompiler.internalName, varDecl.name.getStringValue(), varDecl.type, false);
      }
      else {
        // Method
        loadBoundMethodHandle(varDecl.funDecl);
      }
      return;
    }

    // Check to see if we should load the value from a HeapLocal rather than directly from
    // the local slot.
    // If we are in normal method and we are a HeapLocal then get the right type out of
    // the HeapLocal.
    // If we are in the wrapper method and we are an explicit param that has not been marked
    // as being isWrapperHeapLocal (because it won't get promoted to HeapLocal until actual
    // method) then treat like a normal local not a HeapLocal.
    if (varDecl.isHeapLocal) {
      loadLocal(varDecl.slot);
      String methodName;
      switch (varDecl.type.getType()) {
        case INT:          methodName = "intValue";          break;
        case LONG:         methodName = "longValue";         break;
        case DOUBLE:       methodName = "doubleValue";       break;
        case DECIMAL:      methodName = "decimalValue";      break;
        case STRING:       methodName = "stringValue";       break;
        case MATCHER:      methodName = "matcherValue";      break;
        default:           methodName = "getValue";          break;
      }
      invokeMethod(jacsal.runtime.HeapLocal.class, methodName);
      return;
    }

    // Otherwise just load the value from the locals slot
    loadLocal(varDecl.slot);
  }

  private void checkCast(JacsalType type) {
    Utils.checkCast(mv, type.boxed());
    pop();
    push(type.boxed());
  }

  private void storeVar(Expr.VarDecl varDecl) {
    if (varDecl.isGlobal) {
      box();
      loadGlobals();
      swap();
      loadConst(varDecl.name.getValue());
      swap();
      invokeMethod(Map.class, "put", Object.class, Object.class);
      // Pop result of put since we are not interested in previous value
      popVal();
      return;
    }

    if (varDecl.slot < 0 && varDecl.isField) {
      loadLocal(0);  // "this"
      swap();
      storeClassField(varDecl.name.getStringValue(), varDecl.type, false);
      return;
    }

    if (varDecl.isHeapLocal) {
      box();
      loadLocal(varDecl.slot);
      swap();
      invokeMethod(HeapLocal.class, "setValue", Object.class);
      return;
    }

    // Otherwise normal local var
    storeLocal(varDecl.slot);
  }

  private void storeLocal(int slot) {
    expect(1);
    _storeLocal(slot);
    pop();
  }

  private void _storeLocal(int slot) {
    _storeLocal(localsType(slot), slot);
  }

  private void _storeLocal(JacsalType type, int slot) {
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ISTORE, slot); break;
        case INT:       mv.visitVarInsn(ISTORE, slot); break;
        case LONG:      mv.visitVarInsn(LSTORE, slot); break;
        case DOUBLE:    mv.visitVarInsn(DSTORE, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ASTORE, slot);
    }
  }

  /**
   * Load field value from a map or list or instance.
   * Stack is expected to have: ..., parent, field
   * A default value can be supplied which will be used if field does not exist. This is used for the
   * special case of RuntimeUtils.DEFAULT_VALUE which is a value that means use whatever sensible
   * default makes sense in the context.
   * E.g. if doing integer arithmetic use 0 or if doing string concatenation use ''.
   * @param accessOperator  the type of field access ('.', '?.', '[', '?[')
   * @param defaultValue    use this as the default value (only one of defaultType or defaultValue should
   *                        be supplied since they are mutally exclusive)
   */
  private void loadField(Token accessOperator, Object defaultValue, Location location) {
    expect(2);
    box();                        // Field name/index passed as Object
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.is(QUESTION_DOT,QUESTION_SQUARE));
    if (defaultValue != null) {
      // Special case for RuntimeUtils.DEFAULT_VALUE
      if (defaultValue == RuntimeUtils.DEFAULT_VALUE) {
        mv.visitFieldInsn(GETSTATIC, Type.getInternalName(RuntimeUtils.class), "DEFAULT_VALUE", "Ljava/lang/Object;");
        push(ANY);
      }
      else {
        loadConst(defaultValue);
      }
      box();
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "loadFieldOrDefault", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, Object.class, String.class, Integer.TYPE);
    }
    else {
      loadLocation(location);
      invokeMethod(RuntimeUtils.class, "loadField", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
    }
  }

  /**
   * Expect on stack: ...,parent,field
   */
  private void loadOrCreateField(Token accessOperator, JacsalType defaultType, Location location) {
    expect(2);
    box();                        // Field name/index passed as Object
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.is(QUESTION_DOT,QUESTION_SQUARE));
    check(defaultType.is(MAP,LIST), "Type must be MAP/LIST when createIfMissing set");
    loadConst(defaultType.is(MAP));
    loadLocation(location);
    invokeMethod(RuntimeUtils.class, "loadOrCreateField", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
  }

  /**
   * Expect on stack: parent
   */
  private void loadOrCreateInstanceField(Expr.Binary expr) {
    check(expr.type.is(MAP,LIST), "Type must be MAP/LIST when createIfMissing set");

    // Parent is already on stack but make sure we have the right type
    JacsalType parentType = createInstance(JacsalObject.class);
    setStackType(parentType);

    Token   accessOperator = expr.operator;
    boolean isMap = expr.type.is(MAP);
    dupVal();                     // Dup parent
    int parentVar = saveInTemp();
    compile(expr.right);
    expect(2);
    box();                        // Field name/index passed as Object
    loadLocation(expr.right.location);
    invokeMethod(RuntimeUtils.class, "getFieldGetter", Object.class, Object.class, String.class, int.class);
    setStackType(JacsalType.createInstance(Field.class));
    dupVal();
    int fieldVar = saveInTemp();
    loadLocal(parentVar);
    invokeMethod(Field.class, "get", Object.class);
    // If value is non-null then nothing else to do
    dupVal();
    Label end = new Label();
    mv.visitJumpInsn(IFNONNULL, end);
    pop();
    popVal();

    // We have no value for the field so we have to construct a default value. Type of field must be Map/List or
    // Instance for us to be doing auto-creation.
    emitIf(() -> {
             loadLocal(fieldVar);
             invokeMethod(Field.class, "getType");
             dupVal();
             loadConst(JacsalObject.class);
             swap();
             invokeMethod(Class.class, "isAssignableFrom", Class.class);
             pop();
           },
           () -> {
             // Construct new object
             loadConst(null);
             invokeMethod(Class.class, "getConstructor", Class[].class);
             loadConst(null);
             invokeMethod(Constructor.class, "newInstance", Object[].class);
             invokeMaybeAsync(true, ANY, 1, expr.right.location,
                              () -> {
                                loadNullContinuation();
                                loadLocation(expr.right.location);
                                loadConst(null);
                              },
                              () -> {
                                invokeMethod(JacsalObject.class, Utils.JACSAL_INIT_WRAPPER, Continuation.class,
                                             String.class, int.class, Object[].class);
                              });
           },
           () -> {
             popVal();     // Don't need the field class
             loadConst(isMap ? MAP : LIST);
             loadLocation(expr.right.location);
             invokeMethod(RuntimeUtils.class, "defaultValue", Class.class, String.class, int.class);
           });

    mv.visitLabel(end);        // :end
    dupVal();
    loadLocal(fieldVar);
    swap();
    loadLocal(parentVar);
    swap();
    tryCatch(IllegalArgumentException.class,
             () -> invokeMethod(Field.class, "set", Object.class, Object.class),
             () -> {
               throwError("Cannot set field to " + (isMap ? "Map" : "List") + ": field type incomptible", expr.right.location);
               pop(3);  // Pop types for field, parent, value
             });

    freeSlot(parentVar);
    freeSlot(fieldVar);
  }

  /**
   * Expect to have on stack: ... parent,field
   */
  private void loadMethodOrField(Token accessOperator) {
    expect(2);
    box();                        // Field name/index passed as Object
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.is(QUESTION_DOT,QUESTION_SQUARE));
    loadConst(accessOperator.getSource());
    loadConst(accessOperator.getOffset());
    invokeMethod(RuntimeUtils.class, "loadMethodOrField", Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
  }

  /**
   * Expect to have on stack: ..., value, parent, field
   */
  private void storeValueParentField(Token accessOperator) {
    expect(3);
    loadConst(true);
    doStoreField(accessOperator);
  }

  /**
   * Expect to have on stack: ..., parent, field, value
   */
  private void storeParentFieldValue(Token accessOperator) {
    expect(3);
    loadConst(false);
    doStoreField(accessOperator);
  }

  private void doStoreField(Token accessOperator) {
    loadConst(accessOperator.is(DOT,QUESTION_DOT));
    loadConst(accessOperator.getSource());
    loadConst(accessOperator.getOffset());
    invokeMethod(RuntimeUtils.class, "storeField", Object.class, Object.class, Object.class, Boolean.TYPE, Boolean.TYPE, String.class, Integer.TYPE);
  }

  private Void loadLocal(int slot) {
    if (slot == -1) {
      throw new IllegalStateException("Internal error: trying to load from unitilialised variable (slot is -1)");
    }

    _loadLocal(slot);
    JacsalType type = localsType(slot);
    push(type);
    return null;
  }

  private void _loadLocal(int slot) {
    JacsalType type = localsType(slot);
    if (type.isPrimitive()) {
      switch (type.getType()) {
        case BOOLEAN:   mv.visitVarInsn(ILOAD, slot); break;
        case INT:       mv.visitVarInsn(ILOAD, slot); break;
        case LONG:      mv.visitVarInsn(LLOAD, slot); break;
        case DOUBLE:    mv.visitVarInsn(DLOAD, slot); break;
      }
    }
    else {
      mv.visitVarInsn(ALOAD, slot);
    }
  }

  private JacsalType localsType(int slot) {
    var entry = locals.get(slot);
    if (entry == null) {
      return null;
    }
    if (entry.refCount < 1) {
      throw new IllegalStateException("Internal error: ref count for slot " + slot + " is " + entry.refCount);
    }
    return entry.type;
  }

  ////////////////////////////////////////////////////////

  // = Misc

  private void ntimes(int count, Runnable runnable) {
    for (int i = 0; i < count; i++) {
      runnable.run();
    }
  }

  private void check(boolean condition, String msg) {
    if (!condition) {
      throw new IllegalStateException("Internal error: " + msg);
    }
  }

  ///////////////////////////////////

  private static SourceLocation location(int sourceSlot, int offsetSlot) {
    return new LocalLocation(sourceSlot, offsetSlot);
  }

  private static class LocalLocation implements SourceLocation {
    int sourceSlot;
    int offsetSlot;
    LocalLocation(int sourceSlot, int offsetSlot) {
      this.sourceSlot = sourceSlot;
      this.offsetSlot = offsetSlot;
    }
  }

  private void loadLocation(SourceLocation location) {
    _loadLocation(location);
    push(STRING);
    push(INT);
  }

  private void _loadLocation(SourceLocation sourceLocation) {
    if (sourceLocation instanceof LocalLocation) {
      LocalLocation localLocation = (LocalLocation) sourceLocation;
      _loadLocal(localLocation.sourceSlot);
      _loadLocal(localLocation.offsetSlot);
      return;
    }
    if (sourceLocation instanceof Location) {
      Location location = (Location) sourceLocation;
      _loadConst(location.getSource());
      _loadConst(location.getOffset());
      return;
    }
    throw new IllegalStateException("Unexpected SourceLocation type " + sourceLocation.getClass().getName());
  }

  private static class TryCatch {
    Class catchClass;
    Label tryBlockEnd;
    Label catchBlock;

    TryCatch(Class catchClass, Label tryBlockEnd, Label catchBlock) {
      this.catchClass = catchClass;
      this.tryBlockEnd = tryBlockEnd;
      this.catchBlock = catchBlock;
    }
  }
}
