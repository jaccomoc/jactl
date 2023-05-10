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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FunctionDescriptor {
  public JactlType        type;            // Type method is for, or null for global functions
  public JactlType        firstArgtype;    // Type of first arg (can be different to type - e.g. ANY)
  public String           name;            // Jactl method/function name
  public JactlType        returnType;
  public List<String>     paramNames = new ArrayList<>();
  public Set<String>      mandatoryParams;
  public List<JactlType>  paramTypes;
  public int              paramCount;
  public int              mandatoryArgCount;
  public String           implementingClassName;
  public String           implementingMethod;
  public String           inlineMethodName;         // Name of method compiler can invoke to inline function (if supported)
  public Method           inlineMethod;
  public boolean          isStatic     = false;     // Whether implementation method itself is static or not (for builtins always true)
  public boolean          isInitMethod = false;     // True if this is a user class instance initialiser
  public boolean          isWrapper    = false;     // True if this is the wrapper func for a user function
  public boolean          isFinal      = false;     // True if user function is declared as final
  public boolean          isVarArgs    = false;     // Varargs if last param is Object[]

  // Used by built-in functions:
  public boolean          needsLocation;
  public String            wrapperMethod;
  public JactlMethodHandle wrapperHandle;             // Handle to wrapper: Object wrapper(Class, Continuation, String, int, Object[])
  public String            wrapperHandleField;        // Name of a static field in implementingClass that we can store MethodHandle in
  public boolean          isGlobalFunction = false;  // For builtins indicates whether global function or method
  public boolean          isBuiltin;
  public Boolean          isAsync = null;            // NOTE: null means unknown. Once known will be set to true/false

  // Async if any of these args are async. If none listed then always async. Counting starts at 0 for the instance
  // itself (e.g. ITERATOR) and then 1 is the first arg and so on.
  public List<Integer>    asyncArgs = new ArrayList<>();

  public FunctionDescriptor(String name, JactlType returnType, int paramCount, int mandatoryArgCount, boolean needsLocation) {
    this.name = name;
    this.returnType = returnType;
    this.paramCount = paramCount;
    this.mandatoryArgCount = mandatoryArgCount;
    this.needsLocation = needsLocation;
  }

  public FunctionDescriptor(JactlType type, JactlType firstArgType, String name, JactlType returnType, List<JactlType> paramTypes, boolean varargs,
                            int mandatoryArgCount, String implementingClass, String implementingMethod, boolean needsLocation, JactlMethodHandle wrapperHandle) {
    this(name, returnType, varargs ? -1 : paramTypes.size(), mandatoryArgCount, needsLocation);
    this.type = type;
    this.firstArgtype = firstArgType;
    this.paramTypes = paramTypes;
    this.implementingClassName = implementingClass;
    this.implementingMethod = implementingMethod;
    this.wrapperHandle = wrapperHandle;
  }

  public FunctionDescriptor() {}
}
