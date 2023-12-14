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
import io.jactl.Utils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassDescriptor {

  String                          className;     // Declared name: class Z { }
  String                          namePath;      // Name including outerclasses: _$j$Script123$X$Y$Z
  String                          pkg;           // a.b.c
  String                          packagedName;  // a.b.c._$j$Script123$X$Y$Z
  String                          prettyName;    // a.b.c.X.Y.Z
  String                          internalName;  // io/jactl/pkg/a/b/c/_$j$Script123/X/Y/Z
  boolean                         isInterface;
  JactlType                       baseClass;
  List<ClassDescriptor>           interfaces;
  Map<String, JactlType>          fields          = new LinkedHashMap<>();
  Map<String, JactlType>          mandatoryFields = new LinkedHashMap<>();
  Map<String, FunctionDescriptor> methods         = new LinkedHashMap<>();
  Map<String, ClassDescriptor>    innerClasses    = new LinkedHashMap<>();
  FunctionDescriptor              initMethod;
  boolean                         allFieldsAreDefaults;

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, String pkgName, JactlType baseClass, List<ClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
    this(name, name, isInterface, javaPackage, pkgName, baseClass, interfaces, allFieldsAreDefaults);
  }

  public ClassDescriptor(String name, boolean isInterface, String javaPackage, ClassDescriptor outerClass, JactlType baseClass, List<ClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
    this(name, outerClass.getNamePath() + '$' + name, isInterface, javaPackage, outerClass.getPackageName(), baseClass, interfaces, allFieldsAreDefaults);
  }

  ClassDescriptor(String name, String namePath, boolean isInterface, String javaPackage, String pkgName, JactlType baseClass, List<ClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
    this.className    = name;
    this.namePath     = namePath;
    this.baseClass    = baseClass;
    this.isInterface  = isInterface;
    this.interfaces   = interfaces != null ? interfaces : new ArrayList<>();
    this.pkg          = pkgName;
    this.prettyName   = namePath;
    int idx = namePath.indexOf(Utils.JACTL_SCRIPT_PREFIX);
    // Strip off script name if we are an embedded class
    if (idx != -1) {
      int dollarIdx = namePath.indexOf('$',idx + Utils.JACTL_SCRIPT_PREFIX.length());
      if (dollarIdx != -1) {
        this.prettyName = namePath.substring(0, idx) + namePath.substring(dollarIdx + 1);
        this.prettyName = prettyName.replaceAll("\\$",".");
      }
    }
    else {
      this.prettyName = prettyName.replaceAll("\\$",".");
    }
    this.prettyName = (pkgName.equals("")?"":(pkgName + ".")) + prettyName;
    this.packagedName = (pkgName.equals("")?"":(pkgName + ".")) + namePath;
    this.internalName = ((javaPackage.equals("")?"":(javaPackage + "/")) + packagedName).replaceAll("\\.", "/");
    this.allFieldsAreDefaults = allFieldsAreDefaults;
  }

  public String     getClassName()    { return className; }
  public String     getNamePath()     { return namePath; }
  public String     getPackageName()  { return pkg; }
  public String     getPackagedName() { return packagedName; }
  public String     getPrettyName()   { return prettyName; }
  public String     getInternalName() { return internalName; }
  public JactlType  getClassType()    { return JactlType.createClass(this); }
  public JactlType  getInstanceType() { return getClassType().createInstanceType(); }

  public void setInitMethod(FunctionDescriptor initMethod) { this.initMethod = initMethod; }
  public FunctionDescriptor getInitMethod() { return initMethod; }

  public FunctionDescriptor getMethod(String name) {
    if (name.equals(Utils.JACTL_INIT)) {
      return getInitMethod();
    }
    FunctionDescriptor func = methods.get(name);
    if (func == null && baseClass != null) {
      func = baseClass.getClassDescriptor().getMethod(name);
    }
    if (func == null) {
      func = Functions.lookupMethod(getInstanceType(), name);
    }
    return func;
  }

  /**
   * Add method to this class descriptor. We allow methods to override methods of the same
   * name in a base class as long as the signatures are identical.
   * @param name  the method name
   * @param fun   the FunctionDescriptor for the method
   * @return true if added successfully, false if clash with a field name or if we already
   *         have a method of that name in this class
   */
  public boolean addMethod(String name, FunctionDescriptor fun) {
    if (getField(name) != null)  { return false; }
    return methods.put(name, fun) == null;
  }

  public JactlType getField(String name) {
    JactlType type = fields.get(name);
    if (type == null && baseClass != null) {
      type = getBaseClass().getField(name);
    }
    return type;
  }

  public boolean addField(String name, JactlType type, boolean isMandatory) {
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

  public List<JactlType> getAllFieldTypes() {
    return getAllFields().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  public List<Map.Entry<String,JactlType>> getFields() {
    return new ArrayList<>(fields.entrySet());
  }

  public Stream<Map.Entry<String,JactlType>> getAllFields() {
    Stream<Map.Entry<String,JactlType>> allFields = Stream.of();
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
   * @return a Map which maps field name to its type
   */
  public Map<String,JactlType> getAllMandatoryFields() {
    LinkedHashMap<String,JactlType> allMandatoryFields = new LinkedHashMap<>();
    if (baseClass != null) {
      allMandatoryFields.putAll(baseClass.getClassDescriptor().getAllMandatoryFields());
    }
    allMandatoryFields.putAll(mandatoryFields);
    return allMandatoryFields;
  }

  public boolean allFieldsAreDefaults() {
    boolean baseFieldsAreDefaults = baseClass == null || baseClass.getClassDescriptor().allFieldsAreDefaults();
    return baseFieldsAreDefaults && allFieldsAreDefaults;
  }

  public void addInnerClasses(List<ClassDescriptor> classes) {
    innerClasses.putAll(classes.stream().collect(Collectors.toMap(desc -> desc.namePath, desc -> desc)));
  }

  public ClassDescriptor getInnerClass(String className) {
    return innerClasses.get(namePath + '$' + className);
  }

  public boolean isInterface() {
    return isInterface;
  }

  public JactlType getBaseClassType() {
    return baseClass;
  }

  public ClassDescriptor getBaseClass() {
    return baseClass == null ? null : baseClass.getClassDescriptor();
  }

  public boolean isAssignableFrom(ClassDescriptor clss) {
    return clss.isSameOrChildOf(this);
  }

  /**
   * Is the same or is a child of base class or implements clss if clss is an interface.
   * @param clss  the class to compare to
   * @return true if clss is the same as us or we are a child
   */
  public boolean isSameOrChildOf(ClassDescriptor clss) {
    if (clss == this || clss == JACTL_OBJECT_DESCRIPTOR) {
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

  private static ClassDescriptor JACTL_OBJECT_DESCRIPTOR = new ClassDescriptor("JactlObject", "JactlObject", true, "", "", null, Utils.listOf(), true);
  public static ClassDescriptor getJactlObjectDescriptor() {
    return JACTL_OBJECT_DESCRIPTOR;
  }
}
