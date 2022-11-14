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

package jacsal.runtime;

import jacsal.JacsalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClassDescriptor {

  String                          name;
  String                          pkg;
  String                          packagedName;
  String                          internalName;
  boolean                         isInterface;
  ClassDescriptor                 baseClass;
  List<ClassDescriptor>           interfaces;
  Map<String, JacsalType>         fields;
  Map<String, FunctionDescriptor> methods;

  public ClassDescriptor(String name, boolean isInterface, ClassDescriptor outerClass, ClassDescriptor baseClass, List<ClassDescriptor> interfaces) {
    this.name         = name;
    this.pkg          = outerClass.pkg;
    this.packagedName = outerClass.packagedName + "." + name;
    this.baseClass    = baseClass;
    this.isInterface  = isInterface;
    this.interfaces   = interfaces != null ? interfaces : new ArrayList<>();
    this.internalName = packagedName.replaceAll("\\.", "/");
  }

  public String getPackagedName() { return packagedName; }
  public String getInternalName() { return internalName; }

  public boolean isChildOf(ClassDescriptor clss) {
    if (clss == this) {
      return true;
    }
    if (clss.isInterface) {
      return interfaces.stream().anyMatch(intf -> intf.isChildOf(clss));
    }
    if (baseClass != null) {
      return baseClass.isChildOf(clss);
    }
    return false;
  }
}
