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

import org.objectweb.asm.MethodVisitor;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

import static jacsal.JacsalType.BOOLEAN;
import static jacsal.TokenType.TRUE;
import static org.objectweb.asm.Opcodes.*;

public class MethodCompiler implements Expr.Visitor<Void> {

  private ClassCompiler classCompiler;
  private MethodVisitor mv;
  private Expr          expr;

  // As we generate the byte code we keep a stack where we track the type of the
  // value that would currently be on the JVM stack at that point in the generated
  // code. This allows us to know when to do appropriate conversions.
  private Deque<StackEntry> stack = new ArrayDeque<>();
  private static class StackEntry {
    JacsalType type;
    boolean    isRef;   // true if object reference (including boxed primitives)
    StackEntry(JacsalType type, boolean isRef) {
      this.type  = type;
      this.isRef = isRef;
    }
  }

  MethodCompiler(ClassCompiler classCompiler, Expr expr, MethodVisitor mv) {
    this.classCompiler = classCompiler;
    this.expr = expr;
    this.mv = mv;
  }

  void compile() {
    expr.accept(this);

    if (stack.size() > 1) {
      throw new IllegalStateException("Internal error: multiple types on type stack at end of method. Type stack = " + stack);
    }

    if (stack.isEmpty()) {
      mv.visitInsn(ACONST_NULL);
    }
    else {
      // Box primitive if necessary
      box();
    }
    mv.visitInsn(ARETURN);

    mv.visitMaxs(0, 0);
  }

  /////////////////////////////////////////////

  @Override
  public Void visitBinary(Expr.Binary expr) {
    if (expr.isConst) {
      loadConst(expr.constValue);
    }
    else {
      throw new UnsupportedOperationException();
    }
    push(expr.type);
    return null;
  }

  @Override
  public Void visitPrefixUnary(Expr.PrefixUnary expr) {
    if (expr.isConst) {
      loadConst(expr.constValue);
    }
    else {
      throw new UnsupportedOperationException();
    }
    push(expr.type);
    return null;
  }

  @Override
  public Void visitPostfixUnary(Expr.PostfixUnary expr) {
    throw new UnsupportedOperationException();
  }

  @Override public Void visitLiteral(Expr.Literal expr) {
    push(expr.type);
    return loadConst(expr.value.getValue());
  }

  @Override
  public Void visitIdentifier(Expr.Identifier expr) {
    return null;
  }

  @Override
  public Void visitExprString(Expr.ExprString expr) {
    return null;
  }

  //////////////////////////////////////

  private void push(JacsalType type) {
    stack.push(new StackEntry(type, !type.isPrimitive()));
  }

  private StackEntry peek() {
    return stack.peek();
  }

  private StackEntry pop() {
    return stack.pop();
  }

  /**
   * Box value on the stack based on the type
   */
  private void box() {
    if (peek().isRef) {
      // Nothing to do if not a primitive
      return;
    }
    switch (peek().type) {
      case BOOLEAN:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        break;
      case INT:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        break;
      case LONG:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        break;
      case DOUBLE:
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        break;
    }
    peek().isRef = true;
  }

  private Void loadConst(Object obj) {
    if (obj == null) {
      mv.visitInsn(ACONST_NULL);
    }
    else
    if (obj instanceof Boolean) {
      mv.visitInsn((boolean)obj ? ICONST_1 : ICONST_0);
    }
    else
    if (obj instanceof Integer) {
      int value = (int)obj;
      switch (value) {
        case -1:  mv.visitInsn(ICONST_M1);   break;
        case 0:   mv.visitInsn(ICONST_0);    break;
        case 1:   mv.visitInsn(ICONST_1);    break;
        case 2:   mv.visitInsn(ICONST_2);    break;
        case 3:   mv.visitInsn(ICONST_3);    break;
        case 4:   mv.visitInsn(ICONST_4);    break;
        case 5:   mv.visitInsn(ICONST_5);    break;
        default:
          if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)   { mv.visitIntInsn(BIPUSH, value); break; }
          if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) { mv.visitIntInsn(SIPUSH, value); break; }
          mv.visitLdcInsn(value);
          break;
      }
    }
    else
    if (obj instanceof BigDecimal) {
      mv.visitTypeInsn(NEW, "java/math/BigDecimal");
      mv.visitInsn(DUP);
      mv.visitLdcInsn(((BigDecimal)obj).toPlainString());
      mv.visitMethodInsn(INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V", false);
    }
    else {
      mv.visitLdcInsn(obj);
    }
    return null;
  }
}
