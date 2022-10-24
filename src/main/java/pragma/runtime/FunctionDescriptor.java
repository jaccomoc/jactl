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

package pragma.runtime;

import pragma.PragmaType;

import java.lang.invoke.MethodHandle;
import java.util.List;

public class FunctionDescriptor {
  public PragmaType       type;            // Type method is for or null for global functions
  public PragmaType       firstArgtype;    // Type of first arg (can be different to type - e.g. ANY)
  public String           name;            // Pragma method/function name
  public PragmaType       returnType;
  public List<PragmaType> paramTypes;
  public int              paramCount;
  public int              mandatoryArgCount;
  public boolean          needsLocation;
  public String           implementingClass;
  public String           implementingMethod;
  public String           wrapperMethod;
  public MethodHandle     wrapperHandle;   // Handle to wrapper: Object wrapper(clss, String source, int offset, Object args)
  public boolean          isStatic = false;
  public boolean          isBuiltin;
  public boolean          isAsync;

  public FunctionDescriptor(String name, PragmaType returnType, int paramCount, int mandatoryArgCount, boolean needsLocation) {
    this.name = name;
    this.returnType = returnType;
    this.paramCount = paramCount;
    this.mandatoryArgCount = mandatoryArgCount;
    this.needsLocation = needsLocation;
  }

  public FunctionDescriptor(PragmaType type, PragmaType firstArgType, String name, PragmaType returnType, List<PragmaType> paramTypes, boolean varargs,
                            int mandatoryArgCount, String implementingClass, String implementingMethod, boolean needsLocation, MethodHandle wrapperHandle) {
    this(name, returnType, varargs ? -1 : paramTypes.size(), mandatoryArgCount, needsLocation);
    this.type = type;
    this.firstArgtype = firstArgType;
    this.paramTypes = paramTypes;
    this.implementingClass = implementingClass;
    this.implementingMethod = implementingMethod;
    this.wrapperHandle = wrapperHandle;
  }

  FunctionDescriptor() {}
}
