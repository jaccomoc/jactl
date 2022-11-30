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
import java.util.HashMap;
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
  Map<String, JacsalType>         fields = new HashMap<>();
  Map<String, FunctionDescriptor> methods = new HashMap<>();

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, String pkgName, ClassDescriptor baseClass, List<ClassDescriptor> interfaces) {
    this.name         = name;
    this.baseClass    = baseClass;
    this.isInterface  = isInterface;
    this.interfaces   = interfaces != null ? interfaces : new ArrayList<>();
    this.pkg          = pkgName;
    this.packagedName = (pkgName.equals("")?"":(pkgName + ".")) + name;
    this.internalName = ((javaPackage.equals("")?"":(javaPackage + "/")) + packagedName).replaceAll("\\.", "/");
  }

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, ClassDescriptor outerClass, ClassDescriptor baseClass, List<ClassDescriptor> interfaces) {
    this(outerClass.getName() + '$' + name, isInterface, javaPackage, outerClass.getPackageName(), baseClass, interfaces);
  }

  public String getName()         { return name; }
  public String getPackageName()  { return pkg; }
  public String getPackagedName() { return packagedName; }
  public String getInternalName() { return internalName; }

  public FunctionDescriptor getMethod(String name) { return methods.get(name); }
  public boolean addMethod(String name, FunctionDescriptor fun) {
    if (fields.containsKey(name)) {
      return false;
    }
    return methods.put(name, fun) == null;
  }

  public JacsalType getField(String name) { return fields.get(name); }
  public boolean addField(String name, JacsalType type) {
    if (methods.containsKey(name)) {
      return false;
    }
    return fields.put(name, type) == null;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public ClassDescriptor getBaseClass() {
    return baseClass;
  }

  public boolean isAssignableFrom(ClassDescriptor clss) {
    return clss.isSameOrChildOf(this);
  }

  /**
   * Is the same or is a child of base class or implements clss if clss is an interface.
   */
  public boolean isSameOrChildOf(ClassDescriptor clss) {
    if (clss == this) {
      return true;
    }
    if (clss.isInterface) {
      return interfaces.stream().anyMatch(intf -> intf.isSameOrChildOf(clss));
    }
    if (baseClass != null) {
      return baseClass.isSameOrChildOf(clss);
    }
    return false;
  }
}
