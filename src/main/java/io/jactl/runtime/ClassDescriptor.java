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

import io.jactl.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassDescriptor extends JactlUserDataHolder {

  String                          className;     // Declared name: class Z { }
  String                          namePath;      // Name including outerclasses: _$j$Script123$X$Y$Z
  String                          pkg;           // a.b.c
  String                          packagedName;  // a.b.c._$j$Script123$X$Y$Z
  String                          javaPackagedName;  // io.jactl.a.b.c._$j$Script123$X$Y$Z
  String                          prettyName;    // a.b.c.X.Y.Z
  String                          internalName;  // io/jactl/pkg/a/b/c/_$j$Script123$X$Y$Z
  boolean                         isInterface;
  JactlType                       baseClass;
  boolean                         isCyclicInheritance = false;
  List<ClassDescriptor>           interfaces;
  Map<String, JactlType>          fields          = new LinkedHashMap<>();
  Map<String, JactlType>          mandatoryFields = new LinkedHashMap<>();
  Map<String, FunctionDescriptor> methods         = new LinkedHashMap<>();
  Map<String, ClassDescriptor>    innerClasses    = new LinkedHashMap<>();
  ClassDescriptor                 enclosingClass  = null;
  FunctionDescriptor              initMethod;
  boolean                         allFieldsAreDefaults;
  boolean                         isTopLevelClass = false;       // Whether class is a top level class in a class file
  boolean                         isScriptClass   = false;       // Whether class for a script
  Map<String, Pair<JactlType,Object>> staticFields = new LinkedHashMap<>();  // Map of name to Pair<type,value>

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
    this.pkg          = pkgName == null ? "" : pkgName;
    this.prettyName   = namePath;
    int idx = namePath.indexOf(Utils.JACTL_SCRIPT_PREFIX);
    if (idx != -1 ) {
      int dollarIdx = namePath.indexOf('$', Utils.JACTL_SCRIPT_PREFIX.length());
      if (dollarIdx != -1) {
        idx = dollarIdx + 1;
      }
      else {
        // No embedded class so leave as ScriptXYZ
        this.prettyName = namePath.substring(Utils.JACTL_PREFIX.length());
        idx = -1;
      }
    }
    else {
      // If not script prefix then could be Jactl$$ prefix which is used by IntelliJ plugin
      idx = namePath.indexOf("Jactl$$");
      if (idx != -1) {
        int dollarIdx = namePath.indexOf('$', "Jactl$$".length());
        if (dollarIdx != -1) {
          idx = dollarIdx + 1;
        }
        else {
          this.prettyName = namePath;
          idx = -1;
        }
      }
    }
    // Strip off script name if we are an embedded class
    if (idx != -1) {
      this.prettyName = namePath.substring(idx).replace('$','.');
    }
    else {
      this.prettyName = prettyName.replace('$','.');
    }
    this.prettyName = (pkg.equals("")?"":(pkg + ".")) + prettyName;
    this.packagedName = (pkg.equals("")?"":(pkg + ".")) + namePath;
    this.javaPackagedName = (javaPackage.equals("") ? "" : javaPackage + ".") + packagedName;
    this.internalName = ((javaPackage.equals("")?"":(javaPackage + "/")) + packagedName).replace('.', '/');
    this.allFieldsAreDefaults = allFieldsAreDefaults;
  }

  public String     getClassName()    { return className; }
  public String     getNamePath()     { return namePath; }
  public String     getPackageName()  { return pkg; }
  public String     getPackagedName() { return packagedName; }
  public String     getJavaPackagedName() { return javaPackagedName; }
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
    if (func == null && hasBaseClass()) {
      func = baseClass.getClassDescriptor().getMethod(name);
    }
    return func;
  }

  private boolean hasBaseClass() {
    return baseClass != null && baseClass.getClassDescriptor() != null && !isCyclicInheritance;
  }

  public void setIsTopLevelClass(boolean isTopLevelClass) {
    this.isTopLevelClass = isTopLevelClass;
  }

  public boolean isTopLevelClass() {
    return isTopLevelClass;
  }

  public void setIsScriptClass(boolean isScriptClasss) {
    this.isScriptClass = isScriptClasss;
  }

  public boolean isScriptClass() {
    return isScriptClass;
  }

  public boolean isEquivalent(ClassDescriptor other) {
    if (this == other || this.internalName.equals(other.internalName)) {
      return true;
    }
    return false;
//    // For IDE check if we have a ClassDecl
//    Stmt.ClassDecl classDecl = other.getUserData(Stmt.ClassDecl.class);
//    String otherName = (classDecl.packageName.isEmpty() ? "" : classDecl.packageName + ".") + classDecl.name.getStringValue();
//    return packagedName.equals(otherName);
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
    if (getField(name) != null)       { return false; }
    if (getStaticField(name) != null) { return false; }
    return methods.put(name, fun) == null;
  }

  public JactlType getField(String name) {
    JactlType type = fields.get(name);
    if (type == null && getBaseClass() != null) {
      type = getBaseClass().getField(name);
    }
    return type;
  }

  public JactlType getStaticField(String name) {
    Pair<JactlType,Object> f = staticFields.get(name);
    if (f == null) {
      return getBaseClass() == null ? null : getBaseClass().getStaticField(name);
    }
    return f.first;
  }

  public Object getStaticFieldValue(String name) {
    Pair<JactlType,Object> f = staticFields.get(name);
    if (f == null) {
      if (getBaseClass() != null) {
        return getBaseClass().getStaticFieldValue(name);
      }
      throw new IllegalStateException("Internal error: static field " + name + " does not exist");
    }
    return f.second;
  }

  /**
   * Add a static field of given type
   * @param name name of the field
   * @param type type of the field
   * @return true if no field of that name already exists
   */
  public boolean addStaticField(String name, JactlType type) {
    return getField(name) == null && getMethod(name) == null &&
           staticFields.put(name, Pair.create(type,null)) == null;
  }

  public void setStaticFieldValue(String name, Object value) {
    JactlType type = getStaticField(name);
    if (type == null) {
      throw new IllegalStateException("Internal error: no static field called " + name);
    }
    staticFields.put(name, Pair.create(type,value));
  }

  public boolean addField(String name, JactlType type, boolean isMandatory) {
    if (getField(name) != null)       { return false; }
    if (getMethod(name) != null)      { return false; }
    if (getStaticField(name) != null) { return false; }
    if (isMandatory) {
      mandatoryFields.put(name, type);
    }
    return fields.put(name, type) == null;
  }

  public List<String> getAllFieldNames() {
    return getAllFieldsStream().map(Map.Entry::getKey).collect(Collectors.toList());
  }

  public List<JactlType> getAllFieldTypes() {
    return getAllFieldsStream().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  public List<Map.Entry<String,JactlType>> getFields() {
    return new ArrayList<>(fields.entrySet());
  }

  public Map<String,JactlType> getAllFields() {
    Stream<Map.Entry<String,JactlType>> allFields = Stream.of();
    if (getBaseClass() != null) {
      allFields = getBaseClass().getAllFieldsStream();
    }
    return Stream.concat(allFields, fields.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String,Pair<JactlType,Object>> getAllStaticFields() {
    return Stream.concat(getBaseClass() != null ? getBaseClass().getAllStaticFields().entrySet().stream() : Stream.of(),
                         staticFields.entrySet().stream())
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Stream<Map.Entry<String,JactlType>> getAllFieldsStream() {
    Stream<Map.Entry<String,JactlType>> allFields = Stream.of();
    if (getBaseClass() != null) {
      allFields = getBaseClass().getAllFieldsStream();
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
    if (getBaseClass() != null) {
      allMandatoryFields.putAll(getBaseClass().getAllMandatoryFields());
    }
    allMandatoryFields.putAll(mandatoryFields);
    return allMandatoryFields;
  }

  public boolean allFieldsAreDefaults() {
    boolean baseFieldsAreDefaults = getBaseClassType() == null || getBaseClassType().getClassDescriptor().allFieldsAreDefaults();
    return baseFieldsAreDefaults && allFieldsAreDefaults;
  }

  public void addInnerClasses(List<ClassDescriptor> classes) {
    innerClasses.putAll(classes.stream().collect(Collectors.toMap(desc -> desc.namePath, desc -> desc, (desc1,desc2) -> desc1)));
    classes.forEach(c -> c.enclosingClass = this);
  }

  public ClassDescriptor getInnerClass(String className) {
    return innerClasses.get(namePath + '$' + className);
  }
  public Collection<ClassDescriptor> getInnerClasses() {
    return innerClasses.values();
  }

  public ClassDescriptor getEnclosingClass() {
    return enclosingClass;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public JactlType getBaseClassType() {
    return getBaseClassType(false);
  }

  public JactlType getBaseClassType(boolean ignoreCyclicInheritance) {
    if (isCyclicInheritance && !ignoreCyclicInheritance) {
      return null;
    }
    return baseClass;
  }

  public ClassDescriptor getBaseClass() {
    return getBaseClassType() == null ? null : getBaseClassType().getClassDescriptor();
  }

  /**
   * For error situations where we have recursive base class hierarchy allow resetting
   * of base class so that further error processing can occur.
   */
  public void markCyclicInheritance() {
    isCyclicInheritance = true;
  }

  public boolean isAssignableFrom(ClassDescriptor clss) {
    if (clss == null) {
      return false;
    }
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
    if (hasBaseClass()) {
      return getBaseClass().isSameOrChildOf(clss);
    }
    return false;
  }

  private static ClassDescriptor JACTL_OBJECT_DESCRIPTOR = new ClassDescriptor("JactlObject", "JactlObject", true, "", "", null, Utils.listOf(), true);
  public static ClassDescriptor getJactlObjectDescriptor() {
    return JACTL_OBJECT_DESCRIPTOR;
  }
}
