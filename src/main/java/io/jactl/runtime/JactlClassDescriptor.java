/*
 * Copyright © 2022,2023,2024  James Crawford
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

public class JactlClassDescriptor extends JactlUserDataHolder implements ClassDescriptor {

  protected String                              className;     // Declared name: class Z { }
  protected String                              namePath;      // Name including outerclasses: _$j$Script123$X$Y$Z
  protected String                              pkg;           // a.b.c
  protected String                              packagedName;  // a.b.c._$j$Script123$X$Y$Z
  protected String                              javaPackagedName;  // io.jactl.a.b.c._$j$Script123$X$Y$Z
  protected String                              prettyName;    // a.b.c.X.Y.Z
  protected String                              internalName;  // io/jactl/pkg/a/b/c/_$j$Script123$X$Y$Z
  protected boolean                             isInterface;
  protected JactlType                           baseClass;
  protected boolean                             isCyclicInheritance = false;
  protected List<JactlClassDescriptor>          interfaces;
  protected Map<String, JactlType>              fields              = new LinkedHashMap<>();
  protected Map<String, JactlType>              mandatoryFields = new LinkedHashMap<>();
  protected Set<String>                         finalFields = new HashSet<>();
  protected Map<String, FunctionDescriptor>     methods             = new LinkedHashMap<>();
  protected Map<String, JactlClassDescriptor>   innerClasses        = new LinkedHashMap<>();
  protected JactlClassDescriptor                enclosingClass      = null;
  protected FunctionDescriptor                  initMethod;
  protected boolean                             allFieldsAreDefaults;
  protected boolean                             isTopLevelClass = false;       // Whether class is a top level class in a class file
  protected boolean                             isScriptClass   = false;       // Whether class for a script
  protected Map<String, Pair<JactlType,Object>> staticFields = new LinkedHashMap<>();  // Map of name to Pair<type,value>

  /**
   * @param name                  the Jactl class name without package prefix or outerclass prefix 
   * @param isInterface           always false for the moment
   * @param javaPackage           the Java package that forms the base package for all Jactl classes (from the JavaContext, defaults to "jactl.pkg")
   * @param pkgName               the Jactl package name the class lives in (e.g. "app.lib.utils")
   * @param baseClass             the JactlType for the baseClass or null if there is no baseClass
   * @param interfaces            a list of interfaces that the class implements (at the moment always null)
   * @param allFieldsAreDefaults  whether all fields have defaults - used to decide if mandatory constructor required 
   */
  public JactlClassDescriptor(String name, boolean isInterface, String javaPackage, String pkgName, JactlType baseClass, List<JactlClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
    this(name, name, isInterface, javaPackage, pkgName, baseClass, interfaces, allFieldsAreDefaults);
  }

  /**
   * This constructor is for when we are aliasing an existing Java class to be a built-in Jactl class.
   * @param name                  the Jactl class name without package prefix or outerclass prefix 
   * @param isInterface           always false for the moment
   * @param javaPackage           the Java package that forms the base package for all Jactl classes (from the JavaContext, defaults to "jactl.pkg")
   * @param pkgName               the Jactl package name the class lives in (e.g. "app.lib.utils")
   * @param baseClass             the JactlType for the baseClass or null if there is no baseClass
   * @param interfaces            a list of interfaces that the class implements (at the moment always null)
   * @param allFieldsAreDefaults  whether all fields have defaults - used to decide if mandatory constructor required 
   * @param javaClass             the Java class that acts as our Jactl class (in '.' form)
   */
  public JactlClassDescriptor(String name, boolean isInterface, String javaPackage, String pkgName, JactlType baseClass, List<JactlClassDescriptor> interfaces, boolean allFieldsAreDefaults, String javaClass) {
    this(name, name, isInterface, javaPackage, pkgName, baseClass, interfaces, allFieldsAreDefaults);
    this.javaPackagedName = javaClass;
    this.internalName     = this.javaPackagedName.replace('.', '/');
  }

  /**
   * @param name                  the Jactl class name without package prefix or outerclass prefix 
   * @param isInterface           always false for the moment
   * @param javaPackage           the Java package that forms the base package for all Jactl classes (from the JavaContext, defaults to "jactl.pkg")
   * @param outerClass            the immediate outer class that this class is defined within
   * @param baseClass             the JactlType for the baseClass or null if there is no baseClass
   * @param interfaces            a list of interfaces that the class implements (at the moment always null)
   * @param allFieldsAreDefaults  whether all fields have defaults - used to decide if mandatory constructor required 
   */
  public JactlClassDescriptor(String name, boolean isInterface, String javaPackage, JactlClassDescriptor outerClass, JactlType baseClass, List<JactlClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
    this(name, outerClass.getNamePath() + '$' + name, isInterface, javaPackage, outerClass.getPackageName(), baseClass, interfaces, allFieldsAreDefaults);
  }

  /**
   * @param name                  the Jactl class name without package prefix or outerclass prefix 
   * @param namePath              if an inner class then this will be OuterClass$InnerClassName otherwise same as name
   * @param isInterface           always false for the moment
   * @param javaPackage           the Java package that forms the base package for all Jactl classes (from the JavaContext, defaults to "jactl.pkg")
   * @param pkgName               the Jactl package name the class lives in (e.g. "app.lib.utils")
   * @param baseClass             the JactlType for the baseClass or null if there is no baseClass
   * @param interfaces            a list of interfaces that the class implements (at the moment always null)
   * @param allFieldsAreDefaults  whether all fields have defaults - used to decide if mandatory constructor required 
   */
  JactlClassDescriptor(String name, String namePath, boolean isInterface, String javaPackage, String pkgName, JactlType baseClass, List<JactlClassDescriptor> interfaces, boolean allFieldsAreDefaults) {
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
    this.prettyName           = Utils.pkgPathOf(pkg, prettyName);
    this.packagedName         = Utils.pkgPathOf(pkg, namePath);
    this.javaPackagedName     = Utils.pkgPathOf(javaPackage, packagedName);
    this.internalName         = Utils.pkgPathOf(javaPackage, packagedName).replace('.', '/');
    this.allFieldsAreDefaults = allFieldsAreDefaults;
  }

  @Override public String getSimpleName()    { return className; }
  @Override public String     getPackagedName() { return packagedName; }
  @Override public String     getNamePath()     { return namePath; }
  @Override public String     getPackageName()  { return pkg; }
  
  public String     getJavaPackagedName() { return javaPackagedName; }
  public String     getPrettyName()   { return prettyName; }
  public String     getInternalName() { return internalName; }
  public JactlType  getClassType()    { return JactlType.createClass(this); }
  public JactlType  getInstanceType() { return getClassType().createInstanceType(); }

  public void setInitMethod(FunctionDescriptor initMethod) { this.initMethod = initMethod; }
  public FunctionDescriptor getInitMethod() { return initMethod; }

  // For error situations where problem in base class shouldn't prevent us detecting other errors
  public void resetBaseClass() {
    baseClass = null;
  }
  
  public FunctionDescriptor getMethod(String name) {
    if (name.equals(Utils.JACTL_INIT)) {
      return getInitMethod();
    }
    FunctionDescriptor func = methods.get(name);
    if (func == null && hasBaseClass()) {
      func = getBaseClassDescriptor().getMethod(name);
    }
    return func;
  }

  private boolean hasBaseClass() {
    return getBaseClassDescriptor() != null && !isCyclicInheritance;
  }
  
  public JactlClassDescriptor getBaseClassDescriptor() {
    if (isCyclicInheritance) {
      return null;
    }
    return baseClass == null ? null : (JactlClassDescriptor)baseClass.getClassDescriptor();
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

  @Override
  public JactlType getField(String name) {
    JactlType type = fields.get(name);
    if (type == null && getBaseClass() != null) {
      type = getBaseClass().getField(name);
    }
    return type;
  }

  @Override
  public boolean isFinal(String fieldName) {
    return finalFields.contains(fieldName);
  }

  @Override
  public JactlType getStaticField(String name) {
    Pair<JactlType,Object> f = staticFields.get(name);
    if (f == null) {
      return getBaseClass() == null ? null : getBaseClass().getStaticField(name);
    }
    return f.first;
  }

  @Override
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
           staticFields.put(name, Pair.of(type, null)) == null;
  }

  public void setStaticFieldValue(String name, Object value) {
    JactlType type = getStaticField(name);
    if (type == null) {
      throw new IllegalStateException("Internal error: no static field called " + name);
    }
    staticFields.put(name, Pair.of(type, value));
  }

  public boolean addField(String name, JactlType type, boolean isMandatory, boolean isFinal) {
    if (getField(name) != null)       { return false; }
    if (getMethod(name) != null)      { return false; }
    if (getStaticField(name) != null) { return false; }
    if (isMandatory) {
      mandatoryFields.put(name, type);
    }
    if (isFinal) {
      finalFields.add(name);
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
      allFields = getBaseClassDescriptor().getAllFieldsStream();
    }
    return Stream.concat(allFields, fields.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<String,Pair<JactlType,Object>> getAllStaticFields() {
    return Stream.concat(getBaseClass() != null ? getBaseClassDescriptor().getAllStaticFields().entrySet().stream() : Stream.of(),
                         staticFields.entrySet().stream())
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Stream<Map.Entry<String,JactlType>> getAllFieldsStream() {
    Stream<Map.Entry<String,JactlType>> allFields = Stream.of();
    if (getBaseClass() != null) {
      allFields = getBaseClassDescriptor().getAllFieldsStream();
    }
    return Stream.concat(allFields, fields.entrySet().stream());
  }

  public Stream<Map.Entry<String,FunctionDescriptor>> getAllMethods() {
    Stream<Map.Entry<String,FunctionDescriptor>> allMethods = Stream.of();
    if (getBaseClass() != null) {
      allMethods = getBaseClassDescriptor().getAllMethods();
    }
    return Stream.concat(allMethods, methods.entrySet().stream());
  }

  /**
   * Get mandatory fields for all based classes and this class
   * @return a Map which maps field name to its type
   */
  public Map<String,JactlType> getAllMandatoryFields() {
    LinkedHashMap<String,JactlType> allMandatoryFields = new LinkedHashMap<>();
    if (getBaseClassDescriptor() != null) {
      allMandatoryFields.putAll(getBaseClassDescriptor().getAllMandatoryFields());
    }
    allMandatoryFields.putAll(mandatoryFields);
    return allMandatoryFields;
  }

  public boolean allFieldsAreDefaults() {
    boolean baseFieldsAreDefaults = getBaseClassType() == null || getBaseClassDescriptor().allFieldsAreDefaults();
    return baseFieldsAreDefaults && allFieldsAreDefaults;
  }

  public void addInnerClasses(List<JactlClassDescriptor> classes) {
    innerClasses.putAll(classes.stream().collect(Collectors.toMap(desc -> desc.namePath, desc -> desc, (desc1,desc2) -> desc1)));
    classes.forEach(c -> c.enclosingClass = this);
  }

  public JactlClassDescriptor getInnerClass(String className) {
    return innerClasses.get(namePath + '$' + className);
  }
  public Collection<JactlClassDescriptor> getInnerClasses() {
    return innerClasses.values();
  }

  public JactlClassDescriptor getEnclosingClass() {
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
    if (isCyclicInheritance) {
      return null;
    }
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
    if (clss == null || !(clss instanceof JactlClassDescriptor)) {
      return false;
    }
    return ((JactlClassDescriptor)clss).isSameOrChildOf(this);
  }

  /**
   * Is the same or is a child of base class or implements clss if clss is an interface.
   * @param clss  the class to compare to
   * @return true if clss is the same as us or we are a child
   */
  public boolean isSameOrChildOf(JactlClassDescriptor clss) {
    if (clss == this || clss == JACTL_OBJECT_DESCRIPTOR) {
      return true;
    }
    if (clss.isInterface) {
      return interfaces.stream().anyMatch(intf -> intf.isSameOrChildOf(clss));
    }
    if (hasBaseClass()) {
      return ((JactlClassDescriptor)getBaseClass()).isSameOrChildOf(clss);
    }
    return false;
  }

  private static JactlClassDescriptor JACTL_OBJECT_DESCRIPTOR = new JactlClassDescriptor("JactlObject", "JactlObject", true, "", "", null, Utils.listOf(), true);
  public static JactlClassDescriptor getJactlObjectDescriptor() {
    return JACTL_OBJECT_DESCRIPTOR;
  }
}
