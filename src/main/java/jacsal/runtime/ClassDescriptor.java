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
import jacsal.Utils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassDescriptor {

  String                          name;
  String                          pkg;
  String                          packagedName;
  String                          internalName;
  boolean                         isInterface;
  JacsalType                      baseClass;
  List<ClassDescriptor>           interfaces;
  Map<String, JacsalType>         fields          = new LinkedHashMap<>();
  Map<String, JacsalType>         mandatoryFields = new LinkedHashMap<>();
  Map<String, FunctionDescriptor> methods         = new LinkedHashMap<>();
  Map<String, ClassDescriptor>    innerClasses    = new LinkedHashMap<>();
  FunctionDescriptor              initMethod;

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, String pkgName, JacsalType baseClass, List<ClassDescriptor> interfaces) {
    this.name         = name;
    this.baseClass    = baseClass;
    this.isInterface  = isInterface;
    this.interfaces   = interfaces != null ? interfaces : new ArrayList<>();
    this.pkg          = pkgName;
    this.packagedName = (pkgName.equals("")?"":(pkgName + ".")) + name;
    this.internalName = ((javaPackage.equals("")?"":(javaPackage + "/")) + packagedName).replaceAll("\\.", "/");
  }

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, ClassDescriptor outerClass, JacsalType baseClass, List<ClassDescriptor> interfaces) {
    this(outerClass.getName() + '$' + name, isInterface, javaPackage, outerClass.getPackageName(), baseClass, interfaces);
  }

  public String     getName()         { return name; }
  public String     getPackageName()  { return pkg; }
  public String     getPackagedName() { return packagedName; }
  public String     getInternalName() { return internalName; }
  public JacsalType getClassType()    { return JacsalType.createClass(this); }
  public JacsalType getInstanceType() { return getClassType().createInstance(); }

  public void setInitMethod(FunctionDescriptor initMethod) { this.initMethod = initMethod; }
  public FunctionDescriptor getInitMethod() { return initMethod; }

  public FunctionDescriptor getMethod(String name) {
    if (name.equals(Utils.JACSAL_INIT)) {
      return getInitMethod();
    }
    var func = methods.get(name);
    if (func == null && baseClass != null) {
      func = baseClass.getClassDescriptor().getMethod(name);
    }
    return func;
  }

  /**
   * Add method to this class descriptor. We allow methods to override methods of the same
   * name in a base class as long as the signatures are identical.
   * @return true if added successfully, false if clash with a field name or if we already
   *         have a method of that name in this class
   */
  public boolean addMethod(String name, FunctionDescriptor fun) {
    if (getField(name) != null)  { return false; }
    return methods.put(name, fun) == null;
  }

  public JacsalType getField(String name) {
    var type = fields.get(name);
    if (type == null && baseClass != null) {
      type = getBaseClass().getField(name);
    }
    return type;
  }

  public boolean addField(String name, JacsalType type, boolean isMandatory) {
    if (getField(name) != null)  { return false; }
    if (getMethod(name) != null) { return false; }
    if (isMandatory) {
      mandatoryFields.put(name, type);
    }
    return fields.put(name, type) == null;
  }

  public List<String> getAllFieldNames() {
    return getAllFields().map(Map.Entry::getKey).collect(Collectors.toList());
  }

  public List<JacsalType> getAllFieldTypes() {
    return getAllFields().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  public Stream<Map.Entry<String,JacsalType>> getAllFields() {
    Stream<Map.Entry<String,JacsalType>> allFields = Stream.of();
    if (getBaseClass() != null) {
      allFields = getBaseClass().getAllFields();
    }
    return Stream.concat(allFields, fields.entrySet().stream());
  }

  public Stream<Map.Entry<String,FunctionDescriptor>> getAllMethods() {
    Stream<Map.Entry<String,FunctionDescriptor>> allMethods = Stream.of();
    if (getBaseClass() != null) {
      allMethods = getBaseClass().getAllMethods();
    }
    return Stream.concat(allMethods, methods.entrySet().stream());
  }

  /**
   * Get mandatory fields for all based classes and this class
   */
  public Map<String,JacsalType> getAllMandatoryFields() {
    LinkedHashMap<String,JacsalType> allMandatoryFields = new LinkedHashMap<>();
    if (baseClass != null) {
      allMandatoryFields.putAll(baseClass.getClassDescriptor().getAllMandatoryFields());
    }
    allMandatoryFields.putAll(mandatoryFields);
    return allMandatoryFields;
  }

  public void addInnerClasses(List<ClassDescriptor> classes) {
    innerClasses.putAll(classes.stream().collect(Collectors.toMap(desc -> desc.name, desc -> desc)));
  }

  public ClassDescriptor getInnerClass(String className) {
    return innerClasses.get(name + '$' + className);
  }

  public boolean isInterface() {
    return isInterface;
  }

  public ClassDescriptor getBaseClass() {
    return baseClass == null ? null : baseClass.getClassDescriptor();
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
      return baseClass.getClassDescriptor().isSameOrChildOf(clss);
    }
    return false;
  }
}
