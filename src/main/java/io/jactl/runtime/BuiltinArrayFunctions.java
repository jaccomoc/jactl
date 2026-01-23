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

import io.jactl.Jactl;
import io.jactl.JactlType;
import org.objectweb.asm.MethodVisitor;

import java.math.BigDecimal;

import static io.jactl.JactlType.*;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;

public class BuiltinArrayFunctions {

  public static void registerFunctions() {
    Jactl.method(JactlType.arrayOf(BOOLEAN))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "booleanArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(BYTE))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "byteArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(INT))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "intArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(LONG))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "longArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(DOUBLE))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "doubleArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(DECIMAL))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "decimalArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(JactlType.arrayOf(STRING))
         .name("size")
         .impl(BuiltinArrayFunctions.class, "stringArrSize")
         .inline("arrSizeInline")
         .register();

    Jactl.method(OBJECT_ARR)
         .name("size")
         .impl(BuiltinArrayFunctions.class, "objectArrSize")
         .inline("arrSizeInline")
         .register();

  }

  //////////////////////////////////////////

  // = size()

  public static void arrSizeInline(MethodVisitor mv) {
    mv.visitInsn(ARRAYLENGTH);
  }

  public static int booleanArrSize(boolean[] arr) { return arr.length; }
  public static int byteArrSize(byte[] arr) { return arr.length; }
  public static int intArrSize(int[] arr) { return arr.length; }
  public static int longArrSize(long[] arr) { return arr.length; }
  public static int doubleArrSize(double[] arr) { return arr.length; }
  public static int decimalArrSize(BigDecimal[] arr) { return arr.length; }
  public static int stringArrSize(String[] arr) { return arr.length; }
  public static int objectArrSize(Object[] arr) { return arr.length; }
}
