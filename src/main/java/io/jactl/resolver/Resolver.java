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

package io.jactl.resolver;

import io.jactl.*;
import io.jactl.compiler.LocalLocation;
import io.jactl.runtime.*;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.BOOLEAN;
import static io.jactl.JactlType.BYTE;
import static io.jactl.JactlType.CLASS;
import static io.jactl.JactlType.DECIMAL;
import static io.jactl.JactlType.INT;
import static io.jactl.JactlType.LIST;
import static io.jactl.JactlType.LONG;
import static io.jactl.JactlType.MAP;
import static io.jactl.JactlType.OBJECT_ARR;
import static io.jactl.JactlType.STRING;
import static io.jactl.TokenType.*;
import static io.jactl.Utils.JACTL_PREFIX;

/**
 * The Resolver visits all statements and all expressions and performs the following:
 * <ul>
 *   <li>tracks variables and their type</li>
 *   <li>resolves references to symbols (variables, methods, etc)</li>
 *   <li>tracks what local variables exist in each code block</li>
 *   <li>promotes local variables to closed over variabels when used in nested functions/closures</li>
 *   <li>evaluates any simple expressions made up of only constants (literals)</li>
 *   <li>matches the break/continue statements to their enclosing while/for loop</li>
 *   <li>sets type of return statement to match return type of function that return belongs to</li>
 *   <li>finds all instances of implicit returns from functions/closures and converts them into
 *       an explicit "return" statement</li>
 *   <li>creates the "wrapper function" for all functions/closures</li>
 *   <li>works out whether functions invoke other async functions and are therefore themselves async</li>
 * </ul>
 *
 * <h2>HeapLocal Variables</h2>
 *
 * <p>One of the jobs of the Resolver is to work out which local variables should be truly
 * local (and have a JVM slot allocated) and which should be turned into heap variables
 * because they are referred to by a nested function/closure (i.e. they are "closed over").</p>
 *
 * <p>A heap local variable is stored in a HeapLocal  object which is just a wrapper around the
 * actual value. By using this extra level of indirection we make sure that if the value
 * of the variable changes, all functions using that variable will see the changed value.</p>
 *
 * <p>We pass any such heap local variables as implicit parameters to nested functions that
 * need access to these variables from their parents. Note that sometimes a nested function
 * will access a variable that is not in its immediate parent but from a level higher up so
 * even if the parent has no need of the heap variable it needs it in order to be able to
 * pass it to a nested function that it invokes at some point. In this way nested functions
 * (and functions nested within them) can effect which heap vars need to get passed into the
 * current function from its parent.</p>
 *
 * <p>If we consider a single heap local variable. It can be used in multiple nested functions
 * and its location in the list of implicit parameters for each function can be different.
 * Normally we use the Expr.VarDecl object to track which local variable slot it resides in
 * but with HeapLocal  objects we need to track the slot used in each function so we need a per
 * function object to track the slot for a given HeapLocal . We create a new VarDecl that points
 * to the original VarDecl for every such nested function.</p>
 *
 * <h2>Wrapper Functions</h2>
 *
 * <p>Each function/closure that we create has a wrapper function created for it. Since functions
 * can have optional paremeters with default values the wrapper function is the place where we
 * fill in any missing arguments with the appropriate defaults. The wrapper function also has the
 * job of handling named argument passing when arguments are passed as a Map of name:value pairs.</p>
 *
 * <p>The Resolver creates a new Stmt.FunDecl object that has the "code" for the wrapper function
 * and ensures that the actual function is declared as a nested function within this wrapper
 * function so that closed over variables are passed appropriately as additional arguments to
 * both the wrapper function and the actual function.</p>
 */
public class Resolver implements Expr.Visitor<JactlType>, Stmt.Visitor<Void> {

  private final JactlContext             jactlContext;
  private final Map<String,Object>       globals;
  private final Deque<Stmt.ClassDecl>    classStack       = new ArrayDeque<>();

  private String packageName = null;
  private String scriptName  = null;  // If this is a script

  private Stmt.ClassDecl topLevelClass;      // Top level class (can be script class)
  private Stmt.Block     topBlock;           // Top most block (dummy block used by Intellij plugin)

  private final Map<String, ClassDescriptor> imports           = new HashMap<>();
  private final Map<String, Expr.VarDecl>    importedConstants = new HashMap<>();
  private final Map<String, Expr.VarDecl>    importedConstantsRenamed = new HashMap<>();
  private final Map<String,ClassDescriptor>  localClasses      = new HashMap<>();
  private final Map<String,Stmt.ClassDecl>   localClassDecls   = new HashMap<>();

  private boolean isWhileCondition = false;        // true while resolving while condition
  private int lastLineNumber = -1;
  private boolean isScript = false;

  private List<CompileError> errors = new ArrayList<>();

  boolean testAsync = false;   // Set to true to flag every method/function as potentially aysnc

  private static JactlType TYPE_FOR_BAD_REF = UNKNOWN;

  /**
   * Resolve variables, references, etc
   * @param jactlContext the JactlContext
   * @param globals  map of global variables (which themselves can be Maps or Lists or simple values)
   * @param location location for error
   */
  public Resolver(JactlContext jactlContext, Map<String, Object> globals, Token location) {
    this.jactlContext = jactlContext;
    this.globals      = globals == null ? Utils.mapOf() : globals;
    this.globals.keySet().forEach(name -> {
      if (!jactlContext.globalVars.containsKey(name)) {
        Expr.VarDecl varDecl = createGlobalVarDecl(name, typeOf(globals.get(name)).boxed());
        if (varDecl.type.is(INSTANCE)) {
          ClassDescriptor classDescriptor = jactlContext.getClassDescriptor(varDecl.type.getInternalName());
          if (classDescriptor == null) {
            error("Unknown class " + varDecl.type.getInternalName() + " for global var " + name, location);
          }
          varDecl.type.setClassDescriptor(classDescriptor);
        }
        jactlContext.globalVars.put(name, varDecl);
      }
    });
  }

  public synchronized void resolveClass(Stmt.ClassDecl classDecl) {
    resolveClass(classDecl, true);
  }

  public synchronized List<CompileError> resolveClass(Stmt.ClassDecl classDecl, boolean throwError) {
    this.packageName = classDecl.packageName;
    this.topLevelClass = classDecl;
    if (!classDecl.isScriptClass()) {
      classDecl.isPrimaryClass = true;
    }

    // Create a block that holds the static imports (used by Intellij plugin)
    topBlock             = new Stmt.Block(classDecl.location, new Stmt.Stmts(classDecl.location));
    topBlock.owningClass = this.topLevelClass;
    topBlock.variables   = importedConstantsRenamed;

    classDecl.imports.forEach(this::resolve);
    createClassDescriptors(classDecl, null);
    prepareClass(classDecl);
    resolve(classDecl);
    if (errors.size() > 0 && throwError) {
      if (errors.size() == 1) {
        throw errors.get(0);
      }
      throw new CompileError(errors);
    }
    return errors;
  }

  public synchronized void resolveScript(Stmt.ClassDecl classDecl) {
    resolveScriptOrClass(classDecl, true, classDecl.name.getStringValue(), classDecl.packageName);
  }

  public synchronized List<CompileError> resolveScriptOrClass(Stmt.ClassDecl classDecl, boolean throwError, String scriptName, String packageName) {
    if (classDecl.scriptMain != null) {
      isScript = true;
      this.scriptName = scriptName;
      // For Intellij plugin we parse with a dummy name so we need to set the name here when we know the file name
      classDecl.name.setValue(scriptName);
      if (classDecl.packageName == null || classDecl.packageName.isEmpty()) {
        classDecl.packageName = packageName;
      }
      else if (!classDecl.packageName.equals(packageName)) {
        error("Package name " + classDecl.packageName + " does not match package based on file location (" + packageName + ")", classDecl.packageToken == null ? classDecl.name : classDecl.packageToken);
      }
    }
    resolveClass(classDecl, throwError);
    if (errors.size() > 0 && throwError) {
      throw new CompileError(errors);
    }
    return errors;
  }

  public Map<String,ClassDescriptor> getImports() {
    return imports;
  }

  public Map<String,Expr.VarDecl> getStaticImports() {
    return importedConstants;
  }

  public Stmt.ClassDecl getClassDecl(String name) {
    return localClassDecls.get(name);
  }

  //////////////////////////////////////////////

  Void resolve(Stmt stmt) {
    if (stmt != null) {
      if (!stmt.isResolved) {
        Stmt.Block block = getBlock();
        if (block != null) {
          stmt.setBlock(block);
        }
        stmt.accept(this);
        stmt.isResolved = true;
      }
    }
    return null;
  }

  Void resolve(List<Expr> exprs) {
    if (exprs != null) {
      exprs.forEach(this::resolve);
    }
    return null;
  }

  JactlType resolveClassAllowed(Expr expr) {
    return doResolve(expr);
  }

  JactlType resolve(Expr expr) {
    JactlType type = doResolve(expr);
    classNameValidation(expr);
    return type;
  }

  JactlType doResolve(Expr expr) {
    if (expr != null) {
      if (!expr.isResolved) {
        expr.setBlock(getBlock());
        JactlType result = expr.accept(this);
        JactlType type   = expr.type;
        if (type != null && (type.unboxed().isPrimitive() || type.is(CLASS))
            || expr instanceof Expr.Literal || expr instanceof Expr.MapLiteral
            || expr instanceof Expr.ListLiteral) {
          expr.couldBeNull = false;
        }
        expr.isResolved = true;
        return result;
      }
      return expr.type;
    }
    return null;
  }

  private void resolve(JactlType type) {
    if (type == null)                      { return; }
    type.setBlock(getBlock());
    if (type.is(ARRAY)) {
      resolve(type.getArrayElemType());
      return;
    }
    if (type.is(INSTANCE, CLASS) && type.getClassDescriptor() == null) {
      type.setClassDescriptor(lookupClass(type.getClassName()));
    }
  }

  //////////////////////////////////////////////////////////

  private void createClassDescriptors(Stmt.ClassDecl classDecl, Stmt.ClassDecl outerClassDecl) {
    // NOTE: we only support class declarations at top level of script or directly within another class decl.
    // NOTE: When in repl mode we don't nest classes within the script itself since they will
    //       not then be accessible in subsequent lines being compiled in the repl.
    ClassDescriptor       outerClass = outerClassDecl == null || outerClassDecl.isScriptClass() && jactlContext.replMode ? null : outerClassDecl.classDescriptor;
    List<ClassDescriptor> interfaces = classDecl.interfaces != null ? classDecl.interfaces.stream().map(this::lookupClass).collect(Collectors.toList()) : null;
    boolean               allFieldsAreDefaults = classDecl.fields.stream().allMatch(field -> Utils.isDefaultValue(field.declExpr));
    ClassDescriptor classDescriptor = outerClass == null ? new ClassDescriptor(classDecl.name.getStringValue(), classDecl.isInterface, jactlContext.javaPackage, classDecl.packageName, classDecl.baseClass, interfaces, allFieldsAreDefaults)
                                                         : new ClassDescriptor(classDecl.name.getStringValue(), classDecl.isInterface, jactlContext.javaPackage, outerClass, classDecl.baseClass, interfaces, allFieldsAreDefaults);
    classDecl.classDescriptor = classDescriptor;
    classDescriptor.setIsTopLevelClass(classDecl.isPrimaryClass);
    classDescriptor.setIsScriptClass(classDecl.isScriptClass());
    classDescriptor.setUserData(classDecl);

    String namePath = classDescriptor.getNamePath();
    if (localClasses.containsKey(namePath)) {
      error("Class '" + classDecl.name.getStringValue() + "' already exists", classDecl.location);
      namePath += '$' + errors.size();   // Create a unique name for duplicate
    }

    localClasses.put(namePath, classDescriptor);
    localClassDecls.put(namePath, classDecl);

    // Do same for our inner classes where we now become the outerclass
    classDecl.innerClasses.forEach(innerClass -> createClassDescriptors(innerClass, classDecl));
    classDecl.classDescriptor.addInnerClasses(classDecl.innerClasses.stream().map(decl -> decl.classDescriptor).collect(Collectors.toList()));
  }

  private void prepareClass(Stmt.ClassDecl classDecl) {
    resolve(classDecl.baseClass);
    classStack.push(classDecl);

    ClassDescriptor classDescriptor = classDecl.classDescriptor;
    JactlType       classType       = JactlType.createClass(classDescriptor);

    checkForCyclicInheritance(classDecl, classDescriptor);

    // Find our functions and fields and add them to the ClassDescriptor if not script class
    if (!classDecl.isScriptClass()) {
      classDecl.fieldVars.values().forEach(varDecl -> {
        if (varDecl.isField) {
          String fieldName = varDecl.name.getStringValue();
          if (Functions.lookupMethod(ANY, fieldName) != null) {
            error("Field name '" + fieldName + "' clashes with built-in method of same name", varDecl.name);
          }
          if (varDecl.isConstVar) {
            if (!classDescriptor.addStaticField(fieldName, varDecl.type)) {
              error("Static field '" + fieldName + "' clashes with another field or method of the same name in class " + classDescriptor.getPackagedName(), varDecl.name);
            }
          }
          else if (!classDescriptor.addField(fieldName, varDecl.type, varDecl.initialiser == null)) {
            error("Field '" + fieldName + "' clashes with another field or method of the same name in class " + classDescriptor.getPackagedName(), varDecl.name);
          }
        }
      });

      Stmt.FunDecl       initMethod         = createInitMethod(classDecl);
      FunctionDescriptor initFuncDescriptor = initMethod.declExpr.functionDescriptor;
      initFuncDescriptor.firstArgtype = classType.createInstanceType();
      classDescriptor.setInitMethod(initFuncDescriptor);

      // Add JSON parsing methods if not script class
      if (!classDecl.isScriptClass()) {
        classDecl.methods.add(createFromJsonFunc(classDecl));
        classDecl.methods.add(createMissingFieldsFunc(classDecl));
      }

      classDecl.methods.forEach(funDeclStmt -> {
        Expr.FunDecl funDecl    = funDeclStmt.declExpr;
        String       methodName = funDecl.nameToken.getStringValue();
        FunctionDescriptor functionDescriptor = funDecl.functionDescriptor;
        functionDescriptor.firstArgtype       = classType.createInstanceType();
        if (!classDescriptor.addMethod(methodName, functionDescriptor)) {
          error("Method name '" + methodName + "' clashes with another field or method of the same name in class " + classDescriptor.getPackagedName(), funDecl.nameToken);
        }
        // Instance method
        // NOTE: JACTL_INIT_MISSING method is special since it not invoked as a virtual method (it is always
        // invoked through implementing class or via super when it is an INVOKESPECIAL call)
        if (!funDecl.isStatic() && !functionDescriptor.name.equals(Utils.JACTL_INIT_MISSING)) {
          // We have to treat all non-final instance methods as async since we might later be overridden by a
          // child class that is async and we need the signatures to match (i.e. passing in of continuation).
          // Furthermore, callers may not know what type of subclass an object is and whether its implementation of
          // the method is async or not so we have to assume always that a call to a non-final instance method is async.
          // If a method is final and it does not override a method that is async then we will mark it async (during
          // Analyser phase) only if it invokes something that is async.
          ClassDescriptor    baseClass = classDescriptor.getBaseClass();
          FunctionDescriptor method    = baseClass != null ? baseClass.getMethod(methodName) : null;
          if (!functionDescriptor.isFinal || baseClass != null && method != null && method.isAsync) {
            functionDescriptor.isAsync = true;
          }
        }
      });
      classDecl.methods = RuntimeUtils.concat(initMethod, classDecl.methods);

      // Create varDecl for "this"
      Stmt.VarDecl thisDecl = fieldDecl(classDecl.name, Utils.THIS_VAR, JactlType.createInstanceType(classDescriptor));
      thisDecl.declExpr.slot = 0;                // "this" is always in local var slot 0
      classDecl.thisField = thisDecl.declExpr;
      Stmt.VarDecl thisStmt = thisDecl;

      // If we have a base class then add VarDecls for super fields
      ClassDescriptor baseClass  = classDescriptor.getBaseClass();
      ArrayList<Stmt> superStmts = new ArrayList<Stmt>();
      if (baseClass != null) {
        Stmt.VarDecl superDecl = fieldDecl(classDecl.name, Utils.SUPER_VAR, JactlType.createInstanceType(classDescriptor.getBaseClass()));
        superDecl.declExpr.slot = 0;             // "super" is always in local var slot 0
        superStmts.add(superDecl);
        superStmts.addAll(baseClass.getAllFieldsStream()
                                   .map(e -> fieldDecl(classDecl.name, e.getKey(), e.getValue()))
                                   .collect(Collectors.toList()));
        superStmts.addAll(baseClass.getAllMethods()
                                   .map(e -> methodDecl(classDecl.name, e.getKey(), e.getValue()))
                                   .collect(Collectors.toList()));
      }

      // Now construct class block with:
      //  superStmts
      //  innerClasses
      //  thisStmt
      //  baseClassFields
      //  fields
      //  methods  (including initMethod)
      Stmt.Stmts classStmts = new Stmt.Stmts(classDecl.location);
      classDecl.classBlock = new Stmt.Block(classDecl.name, classStmts);
      classDecl.classBlock.functions = classDecl.methods;
      classStmts.stmts.addAll(superStmts);
      classStmts.stmts.addAll(classDecl.innerClasses);
      classStmts.stmts.add(thisStmt);
//      // Update the field VarDecls to remove initialisation because we will move the
//      // initialisation into a separate init method
//      classDecl.fields.stream()
//                      .filter(decl -> !decl.declExpr.isConstVar)
//                      .forEach(decl -> decl.declExpr.initialiser = null);
      classStmts.stmts.addAll(classDecl.fields);
      classStmts.stmts.addAll(classDecl.methods);
    }

    // Now declare our inner classes
    classDecl.innerClasses.forEach(innerClass -> prepareClass(innerClass));

    classStack.pop();
  }

  private void checkForCyclicInheritance(Stmt.ClassDecl classDecl, ClassDescriptor classDescriptor) {
    // Make sure we don't have circular extends relationship somewhere
    String previousBaseClass = null;
    Set<JactlType> seen = new HashSet<>();
    for (JactlType baseClass = classDecl.baseClass; baseClass != null; baseClass = baseClass.getClassDescriptor().getBaseClassType(true)) {
      if (baseClass.getClassDescriptor() == null) {
        // Means we have detected cyclic inheritance in base class already
        error("Error in base class", classDecl.baseClassToken);
        break;
      }
      if (seen.contains(baseClass) || baseClass.getClassDescriptor().isEquivalent(classDescriptor)) {
        if (previousBaseClass == null) {
          error("Class cannot extend itself", classDecl.baseClassToken);
        }
        else {
          error("Cyclic inheritance", classDecl.baseClassToken);
        }
        // We have recursive based classes so flag it to prevent infinite recursion
        classDescriptor.markCyclicInheritance();
        //classDecl.baseClass = null;
        break;
      }
      seen.add(baseClass);
      previousBaseClass = baseClass.getClassDescriptor().getNamePath();
    }
  }

  private Stmt.VarDecl fieldDecl(Token token, String name, JactlType instanceType) {
    Token        ident   = token.newIdent(name);
    Expr.VarDecl varDecl = new Expr.VarDecl(ident, null, null);
    varDecl.isResultUsed = false;
    varDecl.type = instanceType;
    varDecl.isField = true;
    return new Stmt.VarDecl(ident, varDecl);
  }

  private Stmt.VarDecl methodDecl(Token token, String name, FunctionDescriptor function) {
    Expr.FunDecl funDecl = new Expr.FunDecl(token, token.newIdent(name), function.returnType, null);
    funDecl.functionDescriptor = function;
    funDecl.isResultUsed = false;
    funDecl.isResolved = true;
    funDecl.type = FUNCTION;
    return null;
  }

  private void validateSignatures(Expr.FunDecl funDecl, ClassDescriptor baseClass, FunctionDescriptor baseMethod) {
    if (baseMethod.isStatic || funDecl.isStatic()) {
      // Not overriding if one of the methods is static so no further check required
      return;
    }
    if (baseMethod.isFinal) {
      error("Method " + baseMethod.name + "() is final in base class " + baseClass.getClassName() + " and cannot be overridden", funDecl.location);
    }
    resolve(funDecl.returnType);
    resolve(baseMethod.returnType);
    if (funDecl.returnType.getType() != baseMethod.returnType.getType() ||
        funDecl.returnType.is(INSTANCE) && !baseMethod.returnType.getClassDescriptor().isAssignableFrom(funDecl.returnType.getClassDescriptor())) {
      error("Method " + baseMethod.name + "(): return type '" + funDecl.returnType +
                             "' not compatible with return type of base method '" + baseMethod.returnType + "'",
                             funDecl.location);
    }
    // Make sure all the parameter types are the same
    if (funDecl.parameters.size() != baseMethod.paramCount) {
      error("Overriding method has different number of parameters than base method", funDecl.nameToken);
    }
    int minParamCount = Math.min(funDecl.parameters.size(), baseMethod.paramCount);
    for (int i = 0; i < minParamCount; i++) {
      if (!funDecl.functionDescriptor.paramTypes.get(i).equals(baseMethod.paramTypes.get(i))) {
        error("Overriding method has different parameter type to base method for parameter '" +
                               funDecl.functionDescriptor.paramNames.get(i) + "'", funDecl.parameters.get(i).location);
      }
    }
    for (int i = 0; i < minParamCount; i++) {
      if (!funDecl.functionDescriptor.paramNames.get(i).equals(baseMethod.paramNames.get(i))) {
        error("Overriding method has different parameter name to base method parameter '" +
                               baseMethod.paramNames.get(i) + "'", funDecl.parameters.get(i).location);
      }
    }
  }

  //////////////////////////////////////////////////////////

  // = Stmt


  @Override public Void visitClassDecl(Stmt.ClassDecl classDecl) {
    resolve(classDecl.baseClass);
    classDecl.nestedFunctions = new ArrayDeque<>();
    // Create a dummy function to hold class block
    Expr.FunDecl dummy = new Expr.FunDecl(classDecl.name, classDecl.name, createClass(classDecl.classDescriptor), Utils.listOf());
    dummy.functionDescriptor = new FunctionDescriptor();
    classDecl.nestedFunctions.push(dummy);
    classStack.push(classDecl);
    try {
      resolve(classDecl.classBlock);
      resolve(classDecl.scriptMain);

      // Make sure any references to globals in field initialisers are captured in _j$init and _j$initMissing methods
      classDecl.methods.forEach(f -> {
        String name = f.declExpr.nameToken.getStringValue();
        if (name.equals(Utils.JACTL_INIT) || name.equals(Utils.JACTL_INIT_MISSING)) {
          f.declExpr.globals.putAll(dummy.globals);
          f.declExpr.wrapper.globals.putAll(dummy.globals);
        }
      });
    }
    finally {
      classStack.pop();
    }
    return null;
  }

  @Override public Void visitImport(Stmt.Import stmt) {
    if (stmt.staticImport) {
      Expr fieldExpr = stmt.className.isEmpty() ? null : stmt.className.get(stmt.className.size() - 1);
      if (fieldExpr == null || !(fieldExpr instanceof Expr.Identifier)) {
        return null;
      }
      Expr.Identifier field     = (Expr.Identifier)fieldExpr;
      JactlType       type      = resolveClassPath(stmt.className.subList(0, stmt.className.size() - 1));
      if (type == null) {
        return null;
      }
      field.parentType = type;
      ClassDescriptor classDesc = type.getClassDescriptor();
      String fieldName = field.identifier.getStringValue();
      Consumer<String> defineConst = name -> {
        Token              constName = field.location.newIdent(name);
        FunctionDescriptor funcDesc  = classDesc.getMethod(name);
        if (funcDesc != null && !funcDesc.isStatic) {
          error("Cannot use import static for non-static method", field.location);
        }
        Expr.VarDecl varDecl = null;
        Stmt.ClassDecl classDecl = classDesc.getUserData(Stmt.ClassDecl.class);
        if (funcDesc != null) {
          // If in Intellij plugin we will have a Stmt.ClassDecl we can use to get real FunDecl
          if (classDecl != null) {
            varDecl = classDecl.methods.stream()
                                       .filter(m -> m.declExpr.nameToken.getStringValue().equals(fieldName))
                                       .map(m -> m.declExpr.varDecl)
                                       .findFirst()
                                       .orElse(null);
          }
          if (varDecl == null) {
            // Otherwise create a dummy one
            varDecl = Utils.funcDescriptorToVarDecl(funcDesc, field.location);
          }
          varDecl.constValue = varDecl.funDecl.wrapper.functionDescriptor;
        }
        else {
          JactlType fieldType = classDesc.getStaticField(name);
          if (fieldType == null) {
            error("No class static field/method called '" + name + "' in class " + classDesc.getClassName(), field.location);
          }
          if (classDecl != null) {
            varDecl = classDecl.fieldVars.get(fieldName);
          }
          if (varDecl == null) {
            varDecl = new Expr.VarDecl(constName, null, null);
          }
          varDecl.type       = fieldType;
          if (fieldType != null) {
            varDecl.constValue = classDesc.getStaticFieldValue(name);
          }
        }
        varDecl.isConstVar = true;
        varDecl.isConst        = true;
        name = stmt.as == null ? name : stmt.as.getStringValue();
        if (importedConstants.put(name, varDecl) != null) {
          error("Duplicate constant name '" + name + "'", stmt.location);
        }
        // Create a renamed VarDecl so Intellij plugin can have one with the aliased name
        if (stmt.as != null) {
          Expr.VarDecl newDecl = new Expr.VarDecl(varDecl.name.newIdent(name), varDecl.equals, varDecl.initialiser);
          newDecl.type = varDecl.type;
          newDecl.funDecl = varDecl.funDecl;
          varDecl = newDecl;
        }
        importedConstantsRenamed.put(name, varDecl);
      };
      if (fieldName.equals(STAR.asString)) {
        // Import all static fields and methods
        classDesc.getAllStaticFields().keySet().forEach(name -> defineConst.accept(name));
        classDesc.getAllMethods()
                 .filter(entry -> entry.getValue().isStatic)
                 .forEach(entry -> defineConst.accept(entry.getKey()));
      }
      else {
        defineConst.accept(fieldName);
      }
    }
    else {
      JactlType type = resolveClassPath(stmt.className);
      if (type != null && type.getClassDescriptor() != null) {
        ClassDescriptor classDesc = type.getClassDescriptor();
        String name = stmt.as != null ? stmt.as.getStringValue() : classDesc.getClassName();
        if (imports.put(name, classDesc) != null) {
          error("Class of name '" + name + "' already imported", stmt.as != null ? stmt.as : stmt.className.get(0).location);
        }
      }
    }
    return null;
  }

  private JactlType resolveClassPath(List<Expr> classPath) {
    if (classPath.isEmpty()) {
      return null;
    }
    Consumer<Integer> createError = i -> error("Unknown class " + classPath.subList(0,i+1)
                                                                           .stream()
                                                                           .map(expr -> expr instanceof Expr.ClassPath ? ((Expr.ClassPath) expr).fullClassName() : ((Expr.Identifier)expr).identifier.getStringValue())
                                                                           .collect(Collectors.joining(".")), classPath.get(i).location);

    JactlType classType = null;
    Expr first = classPath.get(0);
    if (first instanceof Expr.ClassPath) {
      resolve(first);
      if (first.type == null) {
          return null;
      }
      classType = first.type;
    }
    else {
      Expr.VarDecl varDecl = lookupClass(((Expr.Identifier) first).identifier.getStringValue(), first.location);
      if (varDecl == null) {
        createError.accept(0);
        return null;
      }
      ClassDescriptor classDesc = varDecl.classDescriptor;
      first.type = classType = (classDesc == null ? null : JactlType.createClass(classDesc));
      if (classType == null) {
        createError.accept(0);
        return null;
      }
    }

    for (int i = 1; i < classPath.size(); i++) {
      Expr part = classPath.get(i);
      part.parentType = classType;
      Expr.Identifier identifier      = (Expr.Identifier) part;
      ClassDescriptor classDescriptor = classType.getClassDescriptor();
      ClassDescriptor inner           = classDescriptor == null ? null : classDescriptor.getInnerClass(identifier.identifier.getStringValue());
      identifier.type = classType = (inner == null ? null : JactlType.createClass(inner));
      if (classType == null) {
        createError.accept(i);
        return null;
      }
    }
    return classType;
  }

  @Override public Void visitFunDecl(Stmt.FunDecl stmt) {
    resolve(stmt.declExpr);
    return null;
  }

  @Override public Void visitStmts(Stmt.Stmts stmt) {
    final Stmt.Block block         = getBlock();
    Stmt.Stmts       previousStmts = block.currentResolvingStmts;
    block.currentResolvingStmts = stmt;

    for (stmt.currentIdx = 0; stmt.currentIdx < stmt.stmts.size(); stmt.currentIdx++) {
      resolve(stmt.stmts.get(stmt.currentIdx));
    }

    block.currentResolvingStmts = previousStmts;
    return null;
  }

  @Override public Void visitBlock(Stmt.Block stmt) {
    Stmt.Block currentBlock      = getBlock();
    stmt.enclosingBlock          = currentBlock;
    stmt.owningClass             = currentClass();
    Expr.FunDecl currentFunction = currentFunction();
    currentFunction.blocks.push(stmt);
    try {
      // We first define our nested functions so that we can support
      // forward references to functions declared at same level as us
      stmt.functions.stream().map(nested -> nested.declExpr).forEach(nested -> {
        nested.varDecl.owner = currentFunction();
        define(nested.nameToken, nested.varDecl);
      });

      resolve(stmt.stmts);
      return null;
    }
    finally {
      currentFunction.blocks.pop();
    }
  }

  private Stmt.ClassDecl currentClass() {
    return classStack.peek();
  }

  @Override public Void visitVarDecl(Stmt.VarDecl stmt) {
    if (stmt.declExpr.isExplicitParam) {
      // When resolving parameter initialisers we can have an initialiser close over another
      // parameter of the same function: E.g.:  def f(x,y={x++}) { x + y() }
      // Hard to see why anyone would do this but we need to handle it just in case.
      // We flag the block as being in the middle of parameter resolution so that if one of
      // its parameters is then closed over we know that we have to convert the parameter
      // into one that is passed as a HeapLocal (and this also means that all invocations will
      // then need to be done via the method wrapper that knows which parameters to convert
      // into HeapLocals before invoking the actual function).
      getBlock().isResolvingParams = true;
    }
    resolve(stmt.declExpr);
    getBlock().isResolvingParams = false;
    return null;
  }

  @Override public Void visitExprStmt(Stmt.ExprStmt stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitReturn(Stmt.Return stmt) {
    resolve(stmt.expr);
    return null;
  }

  @Override public Void visitIf(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.trueStmt);
    resolve(stmt.falseStmt);
    return null;
  }

  @Override public Void visitWhile(Stmt.While stmt) {
    // We need to keep track of what the current while loop is so that break/continue
    // statements within body of the loop can find the right Stmt.While object
    currentFunction().whileLoops.push(stmt);

    isWhileCondition = true;
    resolve(stmt.condition);
    isWhileCondition = false;
    resolve(stmt.updates);

    resolve(stmt.body);

    currentFunction().whileLoops.pop();
    return null;
  }

  @Override public Void visitThrowError(Stmt.ThrowError stmt) {
    resolve(stmt.source);
    resolve(stmt.offset);
    return null;
  }

  //////////////////////////////////////////////////////////

  // = Expr


  @Override
  public JactlType visitStackCast(Expr.StackCast expr) {
    return expr.type = expr.castType;
  }

  @Override public JactlType visitSwitch(Expr.Switch expr) {
    return SwitchResolver.visitSwitch(this, expr);
  }

  @Override public JactlType visitConstructorPattern(Expr.ConstructorPattern expr) {
    return SwitchResolver.visitConstructorPattern(this, expr);
  }

  @Override public JactlType visitSwitchCase(Expr.SwitchCase caseExpr) {
    return SwitchResolver.visitSwitchCase(this, caseExpr);
  }

  @Override public JactlType visitExprList(Expr.ExprList expr) {
    error("Expression lists not supported", expr.location);
    return expr.type = TYPE_FOR_BAD_REF;
  }

  @Override public JactlType visitRegexMatch(Expr.RegexMatch expr) {
    expr.isConst = false;

    // Special case for standalone regex where we need to match against "it" if in a context where variable
    // "it" exists but only if the regex has modifiers after it. Otherwise, we will treat it as a regular
    // expression string. If there are modifiers but not "it" then we generate an error.
    if (expr.implicitItMatch) {
      if ((!expr.modifiers.isEmpty() || expr.isSubstitute) && !variableExists(Utils.IT_VAR)) {
        error("No 'it' variable in this scope to match against", expr.location);
      }
      if (expr.modifiers.isEmpty() && !expr.isSubstitute) {
        // Just a normal expr string
        expr.string = null;
      }
    }

    resolve(expr.string);
    resolve(expr.pattern);

    if (expr.string == null) {
      // Just an expression string
      return expr.type = expr.pattern.type;
    }

    Expr.VarDecl captureArrVar = getVars(false).get(Utils.CAPTURE_VAR);

    // If regex match with 'g' global modifier then make sure we are in condition of a while
    // loop (which also covers for loops). Also make sure that there are no other regex matches
    // in the condition since we rely on keeping state in the capture array variable and if it
    // is being used by another regex match then we will have problems.
    boolean globalMatch = !expr.isSubstitute && expr.modifiers.indexOf(Utils.REGEX_GLOBAL) != -1;
    if (globalMatch) {
      if (!isWhileCondition) {
        error("Cannot use '" + Utils.REGEX_GLOBAL + "' modifier outside of condition for while/for loop",
                               expr.pattern.location);
      }
      else if (findWhileLoop(expr.pattern.location, null).globalRegexMatches++ > 0) {
        error("Regex match with global modifier can only occur once within while/for condition", expr.pattern.location);
      }
    }

    // Check to see if we already have a capture array variable in the current scope. If we already
    // have one then we will reuse it. Otherwise, we will create a new one. Note that we don't want
    // to use one that we have closed over for our own capture vars since this will break code in
    // the parent scope that the closed over capture var belongs to if it relies on the capture vars
    // still being as they were when the nested function/closure was invoked.
    if (captureArrVar == null) {
      Stmt.VarDecl varDecl = createVarDecl(expr.operator, currentFunction(), Utils.CAPTURE_VAR, MATCHER, null);
      Expr.VarDecl declExpr = varDecl.declExpr;
      declare(declExpr);
      define(varDecl.name, declExpr);
      expr.captureArrVarDecl = declExpr;
      // Insert a VarDecl statement before current statement so if we are in a loop the var is
      // created before the loop
      insertStmt(varDecl);
    }
    else {
      expr.captureArrVarDecl = captureArrVar;
    }

    return expr.type = BOOLEAN;
  }

  @Override public JactlType visitRegexSubst(Expr.RegexSubst expr) {
    visitRegexMatch(expr);
    resolve(expr.replace);
    return expr.type = STRING;
  }

  @Override public JactlType visitBinary(Expr.Binary expr) {
    if (expr.operator.is(DOT,QUESTION_DOT)) {
      resolveClassAllowed(expr.left);
    }
    else {
      resolve(expr.left);
    }
    resolve(expr.right);

    if (expr.left == null || expr.right == null) {
      return expr.type = ANY;
    }

    expr.isConst = expr.left.isConst && expr.right.isConst && jactlContext.evaluateConstExprs;

    if (expr.operator.is(QUESTION_COLON, EQUAL_GRAVE, BANG_GRAVE, COMPARE, AS, IN, BANG_IN)) {
      expr.isConst = false;
    }

    if (expr.isConst && expr.left.isConst && expr.left.constValue == null && !expr.operator.getType().isBooleanOperator()) {
      error("Null operand for left-hand side of '" + expr.operator.getChars() + "': cannot be null", expr.left.location);
    }

    // Field access operators
    if (expr.operator.is(DOT, QUESTION_DOT, LEFT_SQUARE, QUESTION_SQUARE)) {
      expr.isConst = false;
      expr.type = ANY;     // default type
      if (!expr.left.type.is(ANY)) {
        // Do some level of validation
        if (expr.operator.is(DOT, QUESTION_DOT, LEFT_SQUARE, QUESTION_SQUARE)) {
          expr.type = getFieldType(expr.left, expr.operator, expr.right, false).first;
          if (expr.operator.is(QUESTION_DOT, QUESTION_SQUARE)) {
            expr.type = expr.type.boxed();         // since with ?. and ?[ we could get null
          }
          // Field access if we have an instance and if field is not a function since some functions might be
          // static functions or built-in functions which aren't fields from a GETFIELD point of view.
          expr.isFieldAccess = expr.left.type.is(INSTANCE) && expr.right instanceof Expr.Literal && !expr.type.is(FUNCTION);
        }
        // '[' and '?['
        if (expr.operator.is(LEFT_SQUARE, QUESTION_SQUARE) && !expr.left.type.is(MAP, LIST, ARRAY, ITERATOR, STRING, INSTANCE)) {
          error("Invalid object type (" + expr.left.type + ") for indexed (or field) access", expr.operator);
        }
      }

      // For arrays, we don't currently support auto-creation
      if (expr.left.type.is(ARRAY)) {
        expr.createIfMissing = false;
      }

      // Since we now know we are doing a map/list lookup we know what parent type should
      // be so if parent type was ANY we can change to Map/List. This is used when field
      // path is an lvalue to create missing fields/elements of the correct type.
      if (expr.left.type.is(ANY) && expr.createIfMissing) {
        expr.left.type = expr.operator.is(DOT, QUESTION_DOT) ? MAP : LIST;
      }
      return expr.type;
    }

    if (expr.operator.is(INSTANCE_OF, BANG_INSTANCE_OF)) {
      assert expr.right instanceof Expr.TypeExpr;
      expr.isConst = false;
      return expr.type = BOOLEAN;
    }

    if (expr.operator.is(AS)) {
      assert expr.right instanceof Expr.TypeExpr;
      expr.isConst = false;
      assert expr.right instanceof Expr.TypeExpr;
      Expr.TypeExpr typeExpr = (Expr.TypeExpr)expr.right;
      if (!expr.left.type.isConvertibleTo(typeExpr.typeVal)) {
        error("Cannot coerce from " + expr.left.type + " to " + typeExpr.typeVal, expr.operator);
      }
      return expr.type = typeExpr.typeVal;
    }

    // Special check for ++/-- operators to get a better error message
    if (expr.originalOperator != null && expr.originalOperator.is(PLUS_PLUS,MINUS_MINUS) && !expr.left.type.isNumeric() && !expr.left.type.is(ANY)) {
      error("Non-numeric operand for " + expr.originalOperator.getChars(), expr.operator);
    }

    expr.type = resultType(expr.left.type, expr.operator, expr.right.type);
    if (expr.isConst) {
      return evaluateConstExpr(expr);
    }
    return expr.type;
  }

  private JactlType resultType(JactlType leftType, Token operator, JactlType rightType) {
    JactlType type = TYPE_FOR_BAD_REF;
    try {
      type = JactlType.result(leftType, operator, rightType);
    }
    catch (CompileError e) {
      error(e);
    }
    return type;
  }

  private Pair<JactlType,Boolean> getFieldType(Expr parent, Token accessOperator, Expr field, boolean fieldsOnly) {
    boolean isStatic = false;
    JactlType type = null;
    if (parent.type.is(ARRAY) && accessOperator.is(LEFT_SQUARE, QUESTION_SQUARE)) {
      if (!(field.type.isNumeric() || field.type.is(ANY))) {
        error("Array index must be numeric, not " + field.type, field.location);
      }
      type = parent.type.getArrayElemType();
    }
    else {
      String fieldName = null;
      if (field instanceof Expr.Literal) {
        Object fieldValue = ((Expr.Literal) field).value.getValue();
        if (fieldValue instanceof String) {
          fieldName = (String) fieldValue;
        }
        else {
          if (parent.type.is(INSTANCE, CLASS, ARRAY)) {
            error("Invalid field name '" + fieldValue + "' for type " + parent.type, field.location);
            return Pair.create(TYPE_FOR_BAD_REF, false);
          }
        }
      }

      if (fieldName == null) {
        if (parent.isSuper()) {
          error("Cannot determine field/method of 'super': dynamic field lookup not supported for super", field.location);
          return Pair.create(TYPE_FOR_BAD_REF, false);
        }
        type = ANY;   // Can't determine type at compile time; wait for runtime
      }
      else {
        if (parent.isSuper() && accessOperator.is(LEFT_SQUARE, QUESTION_SQUARE)) {
          error("Field access for 'super' cannot be performed via '" + accessOperator.getChars() + "]'", accessOperator);
          return Pair.create(TYPE_FOR_BAD_REF, false);
        }

        if (parent.type.is(INSTANCE, CLASS)) {
          // Store parent type so we can get easy access in Intellij plugin
          field.parentType = parent.type;

          // Check for valid field/method name
          ClassDescriptor desc = parent.type.getClassDescriptor();
          type = fieldName != null ? desc.getField(fieldName) : null;
          if (type != null && parent.type.is(CLASS)) {
            error("Static access to non-static field '" + fieldName, field.location);
          }
          if (type == null && fieldName != null) {
            type = desc.getStaticField(fieldName);
            if (type != null) {
              isStatic = true;
            }
          }
          if (type == null && fieldName != null) {
            FunctionDescriptor descriptor = desc.getMethod(fieldName);
            if (descriptor == null) {
              // Finally check if built-in method exists for that name
              descriptor = lookupMethod(parent.type, (String) fieldName);
            }
            if (descriptor != null) {
              // If we only want fields but have found a method then throw error
              if (fieldsOnly) {
                error("Found method where field expected", field.location);
                return Pair.create(TYPE_FOR_BAD_REF, false);
              }
              if (parent.type.is(CLASS) && !descriptor.isStatic) {
                error("Static access to non-static method '" + fieldName + "' for class " + parent.type, field.location);
              }
              type = FUNCTION;
            }
            if (descriptor == null && parent.type.is(CLASS)) {
              // Look for an inner class if parent is a Class
              ClassDescriptor innerClass = parent.type.getClassDescriptor().getInnerClass(fieldName);
              if (innerClass != null) {
                type = JactlType.createClass(innerClass);
                field.type = type;
              }
            }
          }
          if (type == null) {
            field.parentType = null;
            error("No such field " + (fieldsOnly ? "" : "or method ") + "'" + fieldName + "' for " + parent.type, field.location);
            return Pair.create(TYPE_FOR_BAD_REF, false);
          }
        }
        else {
          // Not INSTANCE or CLASS

          // Might be a built-in method...
          if (lookupMethod(parent.type, (String) fieldName) != null) {
            type = FUNCTION;
          }
          else if (parent.type.is(MAP, ANY)) {
            // Either we have a map (whose field could have any value) or we don't know parent type, so we
            // default to ANY and figure it out at runtime
            type = ANY;
          }
          else {
            error("Invalid object type (" + parent.type + ") for field access (and no matching method of that name)", accessOperator);
            return Pair.create(TYPE_FOR_BAD_REF, false);
          }
        }
      }
    }
    return Pair.create(type,isStatic);
  }

  @Override public JactlType visitTernary(Expr.Ternary expr) {
    // This is only used currently for:   first ? second : third
    resolve(expr.first);
    resolve(expr.second);
    resolve(expr.third);
    if (expr.second.isNull() && !expr.third.type.isPrimitive()) {
      expr.second.type = expr.third.type;
    }
    else
    if (expr.third.isNull() && !expr.second.type.isPrimitive()) {
      expr.third.type = expr.second.type;
    }
    else
    if (!expr.third.type.isCastableTo(expr.second.type)) {
      error("Result types of " + expr.second.type + " and " + expr.third.type + " are not compatible", expr.operator2);
    }
    return expr.type = resultType(expr.second.type, expr.operator1, expr.third.type);
  }

  @Override public JactlType visitPrefixUnary(Expr.PrefixUnary expr) {
    resolve(expr.expr);
    expr.isConst = expr.expr.isConst && jactlContext.evaluateConstExprs;
    if (expr.operator.is(BANG,QUESTION_QUESTION)) {
      expr.type = BOOLEAN;
      if (expr.isConst) {
        expr.constValue = expr.operator.is(BANG) ? !Utils.toBoolean(expr.expr.constValue)
                                                 : expr.expr.constValue != null;
      }
      return expr.type;
    }

    expr.type = expr.expr.type.unboxed();
    if (expr.operator.is(GRAVE)) {
      if (!expr.type.is(BYTE, INT, LONG, ANY)) {
        error("Operand for '~' must be int, byte, or long (not " + expr.expr.type + ")", expr.operator);
      }
    }
    if (!expr.type.isNumeric() && !expr.type.is(ANY)) {
      error("Prefix operator '" + expr.operator.getChars() + "' cannot be applied to type " + expr.expr.type, expr.operator);
    }
    if (expr.isConst) {
      expr.constValue = expr.expr.constValue;
      if (expr.operator.is(MINUS)) {
        switch (expr.type.getType()) {
          case BYTE:    expr.constValue = (byte)-((Number)expr.constValue).intValue(); break;
          case INT:     expr.constValue = -(int)expr.constValue;                       break;
          case LONG:    expr.constValue = -(long)expr.constValue;                      break;
          case DOUBLE:  expr.constValue = -(double)expr.constValue;                    break;
          case DECIMAL: expr.constValue = ((BigDecimal)expr.constValue).negate();      break;
        }
      }
      else
      if (expr.operator.is(GRAVE)) {
        switch (expr.type.getType()) {
          case BYTE:      expr.constValue = (byte)~((Number)expr.constValue).intValue();   break;
          case INT:       expr.constValue = ~(int) expr.constValue;                        break;
          case LONG:      expr.constValue = ~(long) expr.constValue;                       break;
        }
      }
      else
      if (expr.operator.is(PLUS_PLUS, MINUS_MINUS)) {
        error(expr.expr.constValue == null ? "Null value encountered where null not allowed" : "Cannot modify a constant value", expr.expr.location);
      }
    }
    return expr.type;
  }

  @Override public JactlType visitPostfixUnary(Expr.PostfixUnary expr) {
    resolve(expr.expr);
    expr.isConst      = expr.expr.isConst && jactlContext.evaluateConstExprs;
    expr.type         = expr.expr.type;
    if (expr.expr.type.isNumeric() || expr.expr.type.is(ANY)) {
      if (expr.isConst) {
        error(expr.expr.constValue == null ? "Null value encountered where null not allowed" : "Cannot modify a constant value", expr.expr.location);
      }
      return expr.type;
    }
    error("Unary operator '" + expr.operator.getChars() + "' cannot be applied to type " + expr.expr.type, expr.operator);
    return null;
  }

  @Override public JactlType visitCast(Expr.Cast expr) {
    resolve(expr.expr);
    resolve(expr.castType);
    expr.type = expr.castType;

    // Special case for single char strings if casting to int or byte
    if (expr.expr.type.is(STRING) && expr.type.is(BYTE,INT)) {
      if (expr.expr.isConst && expr.expr.constValue instanceof String) {
        String value = (String) expr.expr.constValue;
        if (value.length() != 1) {
          error((value.isEmpty() ? "Empty String" : "String with multiple chars") + " cannot be cast to int", expr.location);
        }
        else {
          expr.isConst = true;
          int charVal = value.charAt(0);
          if (expr.type.is(INT)) {
            expr.constValue = charVal;
          }
          else {
            expr.constValue = (byte) charVal;
          }
        }
      }
      return expr.type;
    }

    // Throw error if bad cast
    checkTypeConversion(expr.expr, expr.castType, true, expr.location);

    if (expr.expr.isConst && expr.castType.isSimple()) {
      expr.isConst = true;
      expr.constValue = castToType(expr.expr.constValue, expr.castType, expr.location);
    }

    // We have a cast so our type is the type we are casting to
    return expr.type;
  }

  private Object castToType(Object value, JactlType type, Token location) {
    try {
      return RuntimeUtils.castTo(type.classFromType(), value, location.getSource(), location.getOffset());
    }
    catch (RuntimeError e) {
      error(e.getErrorMessage(), location);
      return null;
    }
  }

  @Override public JactlType visitLiteral(Expr.Literal expr) {
    expr.isConst = true;
    expr.constValue = expr.value.getValue();

    switch (expr.value.getType()) {
      case BYTE_CONST:    return expr.type = BYTE;
      case INTEGER_CONST: return expr.type = INT;
      case LONG_CONST:    return expr.type = LONG;
      case DOUBLE_CONST:  return expr.type = JactlType.DOUBLE;
      case DECIMAL_CONST: return expr.type = DECIMAL;
      case STRING_CONST:  return expr.type = STRING;
      case TRUE:          return expr.type = BOOLEAN;
      case FALSE:         return expr.type = BOOLEAN;
      case NULL:          return expr.type = ANY;
      case IDENTIFIER:    return expr.type = STRING;
      case LIST:          return expr.type = STRING;
      case MAP:           return expr.type = STRING;
      default:
        // In some circumstances (e.g. map keys) we support literals that are keywords
        if (!expr.value.isKeyword() && !expr.value.is(STAR)) {
          throw new IllegalStateException("Internal error: unexpected token for literal - " + expr.value);
        }
        return expr.type = STRING;
    }
  }

  @Override public JactlType visitListLiteral(Expr.ListLiteral expr) {
    expr.exprs.forEach(this::resolve);
    return expr.type = LIST;
  }

  @Override public JactlType visitMapLiteral(Expr.MapLiteral expr) {
    if (!expr.isConst) {
      expr.entries.forEach(entry -> {
        resolve(entry.first);
        resolve(entry.second);
      });
    }
    return expr.type = MAP;
  }

  @Override public JactlType visitVarDecl(Expr.VarDecl expr) {
    if (expr.type.is(OPTIONAL)) {
      // Allow variables to be initialised in for loops by creating dummy VarDecl with UNKNOWN type.
      // Now we have to find the actual VarDecl or create a real variable.
      Expr.VarDecl varDecl = lookup(expr.name.getStringValue(), expr.name, false, true);
      if (varDecl == null) {
        // This is a brand-new variable which will have type corresponding to the initialiser
        expr.type = JactlType.createUnknown();
        expr.type.typeDependsOn(expr.initialiser);
      }
      else {
        // Treat initialiser just like a normal statement since we are reinitialising an existing variable
        expr.initialiser = new Expr.VarAssign(new Expr.Identifier(varDecl.name), expr.initialiser.location, expr.initialiser);
        expr.initialiser.isResultUsed = false;
        resolve(expr.initialiser);
        expr.owner = currentFunction();
        expr.type = JactlType.createUnknown();
        return expr.initialiser.type;
      }
    }
    resolve(expr.type);
    expr.owner = currentFunction();

    // Functions have previously been declared by the block they belong to
    if (expr.funDecl != null) {
      return expr.type = FUNCTION;
    }

    // Declare the variable (but don't define it yet) so that we can detect self-references
    // in the initialiser. E.g. we can catch:  int x = x + 1
    declare(expr);

    resolve(expr.initialiser);
    checkTypeConversion(expr.initialiser, expr.type, false, expr.equals);

    // Track last type assigned so we can supply better completions in Intellij plugin
    expr.lastAssignedType = expr.initialiser == null ? null : expr.initialiser.type;

    // For constants make sure we have a const value since we need the value at compile time
    if (expr.isConstVar) {
      if (expr.initialiser == null || !expr.initialiser.isConst) {
        error("Constants must be initialised to simple constant values", expr.initialiser.location);
      }
      // If constant is a class field
      Stmt.ClassDecl classDesc = currentClass();
      String         varName   = expr.name.getStringValue();
      expr.constValue = castToType(expr.initialiser.constValue, expr.type, expr.location);
      expr.isConst = true;
      if (classDesc != null && classDesc.classDescriptor.getStaticField(varName) != null) {
        classDesc.classDescriptor.setStaticFieldValue(varName, expr.constValue);
      }
    }

    define(expr.name, expr);
    return expr.type;
  }

  @Override public JactlType visitFunDecl(Expr.FunDecl expr) {
    ClassDescriptor classDescriptor   = currentClass().classDescriptor;
    String          implementingClass = classDescriptor.getInternalName();
    expr.functionDescriptor.implementingClassName = implementingClass;
    String functionName = expr.nameToken == null ? null : expr.nameToken.getStringValue();

    if (expr.varDecl != null) {
      expr.varDecl.setBlock(getBlock());
    }

    // Make sure that if overriding a base class method we have same signature (including param names)
    if (functionName != null && !expr.isStatic() && !expr.isInitMethod()) {
      ClassDescriptor    baseClass = classDescriptor.getBaseClass();
      FunctionDescriptor method    = baseClass != null ? baseClass.getMethod(functionName) : null;
      if (baseClass != null) {
        if (method != null) {
          validateSignatures(expr, baseClass, method);
        }
      }
    }

    // If the script main function
      if (getFunctions().size() == 0) {
      expr.functionDescriptor.implementingMethod = functionName;
      return doVisitFunDecl(expr, false);
    }

    // Nested functions
    if (!expr.isWrapper && expr.wrapper == null) {
      // Check if we are nested in a static context
      if (inStaticContext()) {
        expr.functionDescriptor.isStatic = true;
      }

      // Create a name if we are a closure
      Expr.FunDecl parent = currentFunction();
      String methodName = expr.isClosure() ? "$c" + ++parent.closureCount : functionName;

      // Method name is parent + $ + functionName unless at script function level or at top level of a class
      // in which case there is no parent, so we use just functionName
      expr.functionDescriptor.implementingMethod =
        getFunctions().size() == 1 || (expr.varDecl != null && expr.varDecl.isField) ? methodName
                                                                                     : parent.functionDescriptor.implementingMethod + "$" + methodName;

      // Create a wrapper function that takes care of var arg and named argument handling
      expr.wrapper = expr.isInitMethod() ? createInitWrapper(expr) : createVarArgWrapper(expr);
      expr.wrapper.functionDescriptor.implementingMethod = Utils.wrapperName(expr.functionDescriptor.implementingMethod);
      expr.wrapper.functionDescriptor.implementingClassName = implementingClass;
      expr.functionDescriptor.wrapperMethod = expr.wrapper.functionDescriptor.implementingMethod;

      // Resolve the wrapper. The wrapper has us as an embedded statement, so we will
      // get resolved as a nested function of the wrapper and the next time through here
      // our wrapper will be set
      return doVisitFunDecl(expr.wrapper, true);
    }
    else {
      // We are the real function and are being called again as a result of the wrapper resolving
      return doVisitFunDecl(expr, false);
    }
  }

  private JactlType doVisitFunDecl(Expr.FunDecl expr, boolean defineVar) {
    // Only define a variable for the wrapper function. We don't define a variable
    // for the function itself within the wrapper as we never invoke it that way.
    // The variable exists to hold the JactlMethodHandle that points to the wrapper function
    // so we don't want yet another JactlMethodHandle to the actual function since all calls
    // where we would use a JactlMethodHandle have to go through the wrapper function to get
    // the var args/named args treatment.
    if (defineVar) {
      resolve(expr.varDecl);
    }
    Expr.FunDecl parent = currentFunction();
    expr.owner = parent;
    getFunctions().push(expr);
    try {
      resolve(expr.returnType);
      // Add explicit return in places where we would implicity return the result
      explicitReturn(expr.block, expr.returnType);
      resolve(expr.block);
      return expr.type = FUNCTION;
    }
    finally {
      getFunctions().pop();
      if (parent != null) {
        // Check if parent needs to have any additional heap vars passed to it in order for it to
        // be able to pass them to its nested function and add them to the parent.heapLocals map.
        // These vars are the ones that our nested functions close over. They will all be vars that
        // belong to a scope in our parent or in one of its antecedents.
        // We only need add these vars to our parent's list of heap vars if the var's scope is not
        // the parent itself.
        // We need to create new varDecls for every level between the owner and the function that
        // refers to the var. Each varDecl for a nested function will link to the varDecl of its
        // immediate parent. That way each parent will know how to copy the heap var from its own
        // parameter list (or its actual variable decl) into the appropriate arg for invoking the
        // nested function.
        expr.heapLocalsByName.forEach((name, varDecl) -> addHeapLocalToParents(name, varDecl));
      }
    }
  }

  @Override public JactlType visitIdentifier(Expr.Identifier expr) {
    Expr.VarDecl varDecl = null;
    if (expr.couldBeFunctionCall) {
      varDecl = lookupFunction(expr.identifier);
      if (varDecl != null) {
        expr.varDecl = varDecl;
      }
    }
    String name = expr.identifier.getStringValue();
    boolean isSpecialMatchIdentifier   = expr.identifier.is(UNDERSCORE, STAR);
    if (varDecl == null && !isSpecialMatchIdentifier) {
      // Not a function lookup or couldn't find function
      varDecl = lookup(expr.identifier);   // will throw if not found
      expr.varDecl = varDecl;
      if (name.equals(Utils.THIS_VAR) || name.equals(Utils.SUPER_VAR)) {
        expr.couldBeNull = false;
      }
    }

    // For capture vars type is always ANY since value can be either a String or a number
    if (name.charAt(0) == '$' || isSpecialMatchIdentifier) {
      return expr.type = ANY;
    }

    if (varDecl.isConst) {
      expr.isConst = true;
      expr.constValue = varDecl.constValue;
    }

    return expr.type = varDecl.type;
  }

  @Override public JactlType visitLoadParamValue(Expr.LoadParamValue expr) {
    Expr.VarDecl varDecl = lookup(expr.paramDecl.name);
    expr.varDecl = varDecl;
    return expr.type = expr.varDecl.type;
  }

  @Override public JactlType visitVarAssign(Expr.VarAssign expr) {
    if (expr.identifierExpr.isSuper()) {
      error("Cannot assign to 'super'", expr.identifierExpr.location);
    }
    if (jactlContext.replMode && isScriptScope()) {
      // In repl mode if variable doesn't exist yet we create it when first assigned to
      Token  identifier = expr.identifierExpr.identifier;
      String name       = identifier.getStringValue();
      if (lookup(name, identifier, false, true) == null) {
        Expr.VarDecl varDecl = createGlobalVarDecl(name, ANY);
        jactlContext.globalVars.put(name, varDecl);
      }
    }
    expr.type = resolve(expr.identifierExpr);
    if (expr.type.is(FUNCTION) && expr.identifierExpr.varDecl.funDecl != null) {
      error("Cannot assign to function", expr.identifierExpr.location);
    }
    resolve(expr.expr);
    checkTypeConversion(expr.expr, expr.type, false, expr.operator);
    if (expr.operator.is(QUESTION_EQUAL)) {
      // If using ?= have to allow for null being result
      expr.type = ANY;
    }

    if (expr.identifierExpr.varDecl.isConstVar) {
      error("Cannot modify a constant", expr.location);
    }

    // Flag variable as non-final since it has had an assignment to it
    expr.identifierExpr.varDecl.isFinal = false;

    // Track last type assigned so we can supply better completions in Intellij plugin
    expr.identifierExpr.varDecl.lastAssignedType = expr.expr.type;

    return expr.type;
  }

  /**
   * Check if we can convert from 'from' type to 'to' type and generate an error if not.
   * <p>We allow toAndFrom param to dictate whether we check for 'to' being able to
   * be converted to 'from' type. This is because when casting we can do (Y)x where
   * Y extends X and x is instance of X (some subclass) and the cast can downcast
   * and we won't know until runtime whether it succeeds, so at compile time we have to
   * allow it.</p>
   * @param from      the from expr
   * @param to        the type to convert to
   * @param toAndFrom whether to try converting both ways
   */
  private void checkTypeConversion(Expr from, JactlType to, boolean toAndFrom, Token location) {
    if (from == null) {
      return;
    }
    if (jactlContext.isIdePlugin() && from.type.is(INSTANCE) && to.is(INSTANCE) && (from.type.getClassDescriptor() == null || to.getClassDescriptor() == null)) {
      // Allow unresolved class types during interactive editing in IDE
      return;
    }
    if (!from.type.isCastableTo(to) && !(toAndFrom && from.type.isAssignableFrom(to))) {
      error("Cannot convert from " + from.type + " to " + to, location);
    }
    if (to.is(BOOLEAN) && from instanceof Expr.RegexMatch && from.type.is(STRING)) {
      error("Regex string used in boolean context", from.location);
    }
  }

  @Override public JactlType visitVarOpAssign(Expr.VarOpAssign expr) {
    expr.type = resolve(expr.identifierExpr);
    if (expr.identifierExpr.isSuper()) {
      error("Cannot assign to 'super'", expr.identifierExpr.location);
    }
    if (expr.expr instanceof Expr.Binary) {
      Expr.Binary valueExpr = (Expr.Binary) expr.expr;

      // Set the type of the Noop in our binary expression to the variable type and then
      // resolve the binary expression
      valueExpr.left.type = expr.identifierExpr.varDecl.type;
    }
    resolve(expr.expr);

    if (!expr.expr.type.isCastableTo(expr.type)) {
      error("Cannot convert from type of right-hand side (" + expr.expr.type + ") to " + expr.type, expr.operator);
    }

    if (expr.identifierExpr.varDecl.isConstVar) {
      error("Cannot modify a constant", expr.location);
    }

    // Flag variable as non-final since it has had an assignment to it
    expr.identifierExpr.varDecl.isFinal = false;

    return expr.type;
  }

  @Override public JactlType visitNoop(Expr.Noop expr) {
    expr.isConst = false;
    // Type will usually already have been set by parent (e.g. in visitVarOpAssign)
    if (expr.type == null) {
      expr.type = ANY;
    }
    return expr.type;
  }

  @Override public JactlType visitFieldAssign(Expr.FieldAssign expr) {
    resolveClassAllowed(expr.parent);
    return resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);
  }

  @Override public JactlType visitFieldOpAssign(Expr.FieldOpAssign expr) {
    resolveClassAllowed(expr.parent);

    // For FieldOpAssign we will either have something like the following example:
    //  a.b.c.d += 5 ==> new FieldOpAssign(parent = new Binary('a.b.c'),
    //                                     accessOperator = '.',
    //                                     field = new Expr('d'),
    //                                     expr = new Binary(Noop, '+', '5'))
    // Or something like this:
    //  a.b.c.d =~ s/xxx/yyy/ ==> new FieldOpAssign(parent = new Binary('a.b.c'),
    //                                              accessOperator = '.',
    //                                              field = new Expr('d'),
    //                                              expr = new RegexSubst(Noop, '=~', 'xxx', '', 'yyy', false))
    if (expr.expr instanceof Expr.Binary) {
      // We need to set the type of the Noop to the field type (if known) so that when the
      // BinaryOp is resolved it will be able to figure out its type properly. If we don't
      // know the field type (e.g. it comes from a Map) then we will just use ANY as the type
      // and figure it all out at runtime.
      Expr.Binary valueExpr = (Expr.Binary) expr.expr;
      valueExpr.left.type = getFieldType(expr.parent, expr.accessType, expr.field, true).first;
    }
    resolveFieldAssignment(expr, expr.parent, expr.field, expr.expr, expr.accessType);

    return expr.type;
  }

  private JactlType resolveFieldAssignment(Expr expr, Expr parent, Expr field, Expr valueExpr, Token accessType) {
    boolean dottedAcess = accessType.is(DOT, QUESTION_DOT);
    if (dottedAcess && !parent.type.is(ANY, MAP, INSTANCE, CLASS)) {
      error("Invalid object type (" + parent.type + ") for field access", accessType);
    }
    if (!dottedAcess && !parent.type.is(ANY, LIST, ARRAY, MAP, INSTANCE)) {
      if (parent.type.is(STRING)) {
        error("Cannot assign to element of String", accessType);
      }
      error("Invalid object type (" + parent.type + ") for indexed (or field) access", accessType);
    }

    // If we don't already know the parent type then assume type based on type of access being done
    if (parent.type.is(ANY) && parent instanceof Expr.Binary) {
      Expr.Binary binaryParent = (Expr.Binary) parent;
      if (binaryParent.createIfMissing) {
        binaryParent.type = dottedAcess ? MAP : LIST;
      }
    }

    resolve(field);
    resolve(valueExpr);

    if (parent.type.is(INSTANCE, CLASS)) {
      // Type will be the field type (boxed if ?= being used due to possibility of null value)
      Pair<JactlType,Boolean> pair = getFieldType(parent, accessType, field, true);
      JactlType fieldType = pair.first;
      if (fieldType.is(CLASS)) {
        error("Invalid operation on a class", field.location);
      }
      if (pair.second) {
        error ("Cannot modify a constant", field.location);
      }
      if (parent.type.is(CLASS)) {
        error("Cannot modify class field", field.location);
      }
      if (expr instanceof Expr.FieldAssign && ((Expr.FieldAssign) expr).assignmentOperator.is(QUESTION_EQUAL)) {
        fieldType = fieldType.boxed();
      }
      return expr.type = fieldType;
    }

    if (expr instanceof Expr.FieldAssign && ((Expr.FieldAssign)expr).assignmentOperator.is(QUESTION_EQUAL)) {
      return expr.type = ANY;
    }

    if (parent.type.is(LIST,ARRAY) && !field.type.is(ANY) && !field.type.isNumeric()) {
      error("Non-numeric value for index for " + (parent.type.is(LIST) ? "List" : "Array") + " access", field.location);
    }

    // Map, List, or we don't know...
    return expr.type = valueExpr.type.boxed();
  }

  @Override public JactlType visitExprString(Expr.ExprString expr) {
    expr.exprList.forEach(this::resolve);
    return expr.type = STRING;
  }

  @Override
  public JactlType visitReturn(Expr.Return returnExpr) {
    resolve(returnExpr.expr);
    resolve(returnExpr.expr.type);
    resolve(returnExpr.returnType);
    returnExpr.returnType = currentFunction().returnType;
    returnExpr.funDecl = currentFunction();
    if (!returnExpr.expr.type.isCastableTo(returnExpr.returnType)) {
      error("Expression type " + returnExpr.expr.type + " not compatible with function " +
                             currentFunctionName() + "() return type of " +
                             returnExpr.returnType, returnExpr.expr.location);
    }
    // return statement doesn't really have a type or a value since it returns immediately but
    // to keep everything happy we pretend it has a value of type ANY
    return returnExpr.type = ANY;
  }

  @Override public JactlType visitBreak(Expr.Break expr) {
    expr.whileLoop = findWhileLoop(expr.breakToken, expr.label);
    return expr.type = BOOLEAN;
  }

  @Override public JactlType visitContinue(Expr.Continue expr) {
    expr.whileLoop = findWhileLoop(expr.continueToken, expr.label);
    return expr.type = BOOLEAN;
  }

  @Override public JactlType visitPrint(Expr.Print printExpr) {
    resolve(printExpr.expr);
    return printExpr.type = BOOLEAN;
  }

  @Override public JactlType visitDie(Expr.Die expr) {
    resolve(expr.expr);
    return expr.type = BOOLEAN;
  }

  @Override public JactlType visitEval(Expr.Eval expr) {
    resolve(expr.script);
    if (!expr.script.type.is(ANY,STRING)) {
      error("Eval expects a string to evaluate not " + expr.script.type, expr.script.location);
    }
    resolve(expr.globals);
    if (expr.globals != null && !expr.globals.type.is(ANY,MAP)) {
      error("Global vars for eval must be a Map not " + expr.globals.type, expr.globals.location);
    }
    return expr.type = ANY;
  }

  @Override public JactlType visitClosure(Expr.Closure expr) {
    resolve(expr.funDecl);
    return expr.type = FUNCTION;
  }

  @Override public JactlType visitCall(Expr.Call expr) {
    // Special case if we are invoking the function directly (not via a MethodHandle value)
    if (expr.callee instanceof Expr.Identifier) {
      ((Expr.Identifier) expr.callee).couldBeFunctionCall = true;
    }
    resolve(expr.callee);
    resolve(expr.args);
    if (expr.callee.type == null) {
      error("Unknown function", expr.callee.location);
      return expr.type = ANY;
    }

    if (!expr.callee.type.is(FUNCTION,ANY)) {
      error("Expression of type " + expr.callee.type + " cannot be called", expr.callee.location);
    }
    if (expr.callee.isConst && expr.callee.constValue == null) {
      error("Null value for Function", expr.token);
    }

    expr.type = ANY;
    if (expr.callee.isFunctionCall()) {
      // Special case where we know the function directly and get its return type
      FunctionDescriptor func = ((Expr.Identifier)expr.callee).getFuncDescriptor();
      expr.type = func.returnType;
    }
    return expr.type;
  }

  @Override public JactlType visitMethodCall(Expr.MethodCall expr) {
    resolveClassAllowed(expr.parent);
    // Flag as chained method call so that if parent call has a result of Iterator
    // it can remain as an Iterator. Otherwise, calls that result in Iterators have
    // the result converted to a List. It is only if we are chaining method calls
    // that Iterator is allowed to be used this way. E.g; x.map().map().each()
    if (expr.parent instanceof Expr.MethodCall) {
      ((Expr.MethodCall) expr.parent).isMethodCallTarget = true;
    }
    if (expr.parent instanceof Expr.Call) {
      ((Expr.Call) expr.parent).isMethodCallTarget = true;
    }

    expr.args.forEach(this::resolve);

    // See if we have a direct method invocation or not. We need to know the type of the parent
    // (not ANY) and to be able to find the method in the list of registered methods.
    // Then we need to have the exact number of arguments that match the expected parameter count.
    // If we have less args we will do runtime lookup of MethodHandle that gets to wrapper method.
    if (!expr.parent.type.is(ANY)) {
      // For Intellij plugin to easily find class to which this method belongs
      if (expr.methodNameExpr != null) {
        expr.methodNameExpr.parentType = expr.parent.type;
      }

      FunctionDescriptor descriptor = lookupMethod(expr.parent.type, expr.methodName);
      if (descriptor != null) {
        if (expr.parent.type.is(CLASS) && !descriptor.isStatic) {
          error("No static method '" + expr.methodName + "' exists for " + expr.parent.type, expr.location);
        }
        expr.methodDescriptor = descriptor;
        expr.type = descriptor.returnType;
        return expr.type;
      }
      // Could be a field that has a MethodHandle in it
      if (expr.parent.type.is(INSTANCE)) {
        ClassDescriptor classDescriptor = expr.parent.type.getClassDescriptor();
        if (classDescriptor == null) {
          // There has been an earlier error that prevented us creating a class descriptor
          return expr.type = ANY;
        }
        JactlType       fieldType       = classDescriptor.getField(expr.methodName);
        if (fieldType == null || !fieldType.is(FUNCTION,ANY)) {
          error("No such method/field '" + expr.methodName + "' for object of type " + expr.parent.type, expr.methodNameLocation);
        }
      }
      else
      if (!expr.parent.type.is(MAP)) {
        // If we are not a Map then we know at compile time that method does not exist. (If we are a Map then at
        // runtime someone could create a field in the Map with this name so we have to wait until runtime.)
        error("No such method " + expr.methodName + " for object of type " + expr.parent.type, expr.methodNameLocation);
      }
    }

    // Don't know type of parent or couldn't find method
    expr.type = ANY;

    return expr.type;
  }

  @Override public JactlType visitArrayLength(Expr.ArrayLength expr) {
    resolve(expr.array);
    return expr.type = INT;
  }

  @Override public JactlType visitArrayGet(Expr.ArrayGet expr) {
    resolve(expr.array);
    resolve(expr.index);
    return expr.type = expr.array.type.getArrayElemType();
  }

  @Override public JactlType visitInvokeFunDecl(Expr.InvokeFunDecl expr) {
    expr.args.forEach(this::resolve);
    return expr.type = expr.funDecl.returnType;
  }

  @Override
  public JactlType visitInvokeUtility(Expr.InvokeUtility expr) {
    try {
      expr.args.forEach(this::resolve);
      Method method = expr.clss.getDeclaredMethod(expr.methodName, expr.paramTypes.toArray(new Class[expr.paramTypes.size()]));
      return expr.type = JactlType.typeFromClass(method.getReturnType());
    }
    catch (NoSuchMethodException e) {
      error("Could not find method " + expr.methodName + " in class " + expr.clss.getName(), expr.token);
    }
    return null;
  }

  @Override public JactlType visitBlock(Expr.Block expr) {
    resolve(expr.block);
    // Type will be type of last expression in the block
    expr.type = typeOfLastExpr(expr.block);
    setTypeOfLastExpr(expr.block, expr.type, expr.location);
    return expr.type;
  }

  private static JactlType typeOfLastExpr(Stmt expr) {
    return expr.accept(new Stmt.Visitor<JactlType>() {
      @Override public JactlType visitStmts(Stmt.Stmts stmt) { return stmt.stmts.isEmpty() ? ANY : typeOfLastExpr(stmt.stmts.get(stmt.stmts.size() - 1)); }
      @Override public JactlType visitBlock(Stmt.Block stmt) { return typeOfLastExpr(stmt.stmts); }
      @Override public JactlType visitWhile(Stmt.While stmt) { return ANY; }
      @Override public JactlType visitIf(Stmt.If stmt) {
        if (stmt.trueStmt == null || stmt.falseStmt == null) {
          return ANY;
        }
        return JactlType.commonSuperType(typeOfLastExpr(stmt.trueStmt), typeOfLastExpr(stmt.falseStmt));
      }

      @Override public JactlType visitClassDecl(Stmt.ClassDecl stmt) { throw new IllegalStateException("Internal error: Unexpected Stmt type Stmt.ClassDecl"); }
      @Override public JactlType visitImport(Stmt.Import stmt)       { throw new IllegalStateException("Internal error: Unexpected Stmt type Stmt.Import"); }

      @Override public JactlType visitVarDecl(Stmt.VarDecl stmt)   { return stmt.declExpr.type; }
      @Override public JactlType visitFunDecl(Stmt.FunDecl stmt)   { return FUNCTION; }

      @Override public JactlType visitReturn(Stmt.Return stmt)     { return stmt.expr.type; }
      @Override public JactlType visitExprStmt(Stmt.ExprStmt stmt) { return stmt.expr.type; }
      @Override public JactlType visitThrowError(Stmt.ThrowError stmt) { return ANY; }
    });
  }

  private static Stmt setTypeOfLastExpr(Stmt stmt, JactlType type, Token location) {
    BiFunction<Stmt,Expr,Stmt.Stmts> stmtsOf = (original,expr) -> {
      Stmt.Stmts stmts = new Stmt.Stmts(location);
      stmts.stmts.add(original);
      stmts.stmts.add(new Stmt.ExprStmt(expr.location, expr));
      return stmts;
    };
    Function<Stmt,Stmt.Stmts> addCast = s -> {
      // Need to add a special cast
      Expr.StackCast expr = new Expr.StackCast(s.location, type);
      expr.type = type;
      return stmtsOf.apply(s, expr);
    };
    Supplier<Stmt> literalStmtExpr = () -> {
      Expr expr = literalDefaultValue(location, type);
      expr.isResultUsed = true;
      return new Stmt.ExprStmt(location, expr);
    };


    if (stmt == null) {
      return literalStmtExpr.get();
    }

    return stmt.accept(new Stmt.Visitor<Stmt>() {
      @Override public Stmt visitStmts(Stmt.Stmts stmt) {
        if (!stmt.stmts.isEmpty()) {
          stmt.stmts.set(stmt.stmts.size() - 1, setTypeOfLastExpr(stmt.stmts.get(stmt.stmts.size() - 1), type, stmt.location));
        }
        else {
          stmt.stmts.add(literalStmtExpr.get());
        }
        return stmt;
      }
      @Override public Stmt visitBlock(Stmt.Block stmt) {
        stmt.stmts = (Stmt.Stmts)setTypeOfLastExpr(stmt.stmts, type, stmt.location);
        return stmt;
      }
      @Override public Stmt visitWhile(Stmt.While stmt) {
        Stmt.Stmts stmts = new Stmt.Stmts(stmt.location);
        stmts.location = stmt.location;
        stmts.stmts.add(stmt);
        stmts.stmts.add(literalStmtExpr.get());
        return stmts;
      }
      @Override public Stmt visitIf(Stmt.If stmt) {
        stmt.trueStmt = setTypeOfLastExpr(stmt.trueStmt, type, stmt.location);
        stmt.falseStmt = setTypeOfLastExpr(stmt.falseStmt, type, stmt.location);
        return stmt;
      }

      @Override public Stmt visitClassDecl(Stmt.ClassDecl stmt) { throw new IllegalStateException("Internal error: Unexpected Stmt type Stmt.ClassDecl"); }
      @Override public Stmt visitImport(Stmt.Import stmt)       { throw new IllegalStateException("Internal error: Unexpected Stmt type Stmt.Import"); }

      @Override public Stmt visitVarDecl(Stmt.VarDecl stmt)   {
        stmt.declExpr.isResultUsed = true;
        return type.equals(stmt.declExpr.type) ? stmt : addCast.apply(stmt);
      }

      @Override public Stmt visitFunDecl(Stmt.FunDecl stmt)   { return stmt; }

      // For return statement which has no value we need to add a literal with default value after
      // just to fix the compilation which will expect a type on the type stack. (A bit of a hack.)
      @Override public Stmt visitReturn(Stmt.Return stmt)     {
        Expr defaultValue = literalDefaultValue(stmt.expr.location, type);
        defaultValue.isResultUsed = true;
        return stmtsOf.apply(stmt, defaultValue);
      }

      @Override public Stmt visitExprStmt(Stmt.ExprStmt stmt) {
        stmt.expr.isResultUsed = true;
        return type.equals(stmt.expr.type) ? stmt : addCast.apply(stmt);
      }
      @Override public Stmt visitThrowError(Stmt.ThrowError stmt) { return stmt; }
    });
  }

  @Override public JactlType visitInvokeNew(Expr.InvokeNew expr) {
    resolve(expr.instanceType);
    resolve(expr.dimensions);
    expr.couldBeNull = false;
    return expr.type = expr.instanceType;
  }

  @Override public JactlType visitClassPath(Expr.ClassPath expr) {
    ClassDescriptor descriptor = lookupClass(Utils.listOf(expr));
    expr.type = descriptor == null ? TYPE_FOR_BAD_REF : JactlType.createClass(descriptor);
    return expr.type;
  }

  @Override public JactlType visitDefaultValue(Expr.DefaultValue expr) {
    resolve(expr.varType);
    return expr.type = expr.varType;
  }

  @Override public JactlType visitInstanceOf(Expr.InstanceOf expr) {
    resolve(expr.expr);
    return expr.type = BOOLEAN;
  }

  @Override public JactlType visitConvertTo(Expr.ConvertTo expr) {
    resolve(expr.varType);
    resolve(expr.expr);
    resolve(expr.source);
    resolve(expr.offset);
    return expr.type = expr.varType;
  }

  @Override public JactlType visitTypeExpr(Expr.TypeExpr expr) {
    resolve(expr.typeVal);
    expr.isConst = true;
    expr.constValue = expr.typeVal;
    return expr.type = CLASS;
  }

  @Override public JactlType visitInvokeInit(Expr.InvokeInit expr) {
    expr.args.forEach(this::resolve);
    resolve(expr.classDescriptor.getClassType());
    return expr.type = expr.classDescriptor.getClassType();
  }

  @Override public JactlType visitCheckCast(Expr.CheckCast expr) {
    resolve(expr.expr);
    resolve(expr.castType);
    return expr.type = expr.castType;
  }

  @Override public JactlType visitSpecialVar(Expr.SpecialVar expr) {
    expr.function = currentFunction();
    if (!expr.function.functionDescriptor.needsLocation) {
      throw new IllegalStateException("Internal error: reference to " + expr.name.getStringValue() + " from function that does have location passed to it");
    }
    switch (expr.name.getStringValue()) {
      case Utils.SOURCE_VAR_NAME: return expr.type = STRING;
      case Utils.OFFSET_VAR_NAME: return expr.type = INT;
    }
    throw new IllegalStateException("Internal error: unexpected special var name " + expr.name.getStringValue());
  }

  ////////////////////////////////////////////

  public void insertStmt(Stmt.VarDecl declStmt) {
    Stmt.Stmts currentStmts = getBlock().currentResolvingStmts;
    if (currentStmts != null) {
      currentStmts.stmts.add(currentStmts.currentIdx, declStmt);
      currentStmts.currentIdx++;
    }
  }

  private JactlType evaluateConstExpr(Expr.Binary expr) {
    final Object leftValue  = expr.left.constValue;
    final Object rightValue = expr.right.constValue;
    if (expr.type.is(STRING)) {
      if (expr.operator.isNot(PLUS, STAR)) { throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for Strings"); }
      if (expr.operator.is(PLUS)) {
        if (leftValue == null) {
          error("Left-hand side of '+' cannot be null", expr.operator);
        }
        expr.constValue = Utils.toString(leftValue) + Utils.toString(rightValue);
      }
      else {
        if (rightValue == null) {
          error("Right-hand side of string repeat operator must be numeric but was null", expr.operator);
        }
        String lhs    = Utils.toString(leftValue);
        long   length = Utils.toLong(rightValue);
        if (length < 0) {
          error("String repeat count must be >= 0", expr.right.location);
        }
        else {
          expr.constValue = Utils.repeat(lhs, (int) length);
        }
      }
      return expr.type;
    }

    if (expr.operator.is(PLUS) && expr.type.is(MAP)) {
      expr.constValue = RuntimeUtils.mapAdd((Map) leftValue, (Map) rightValue, false);
      return expr.type;
    }

    if (expr.operator.is(PLUS) && expr.type.is(LIST)) {
      expr.constValue = RuntimeUtils.listAdd((List) leftValue, rightValue, false);
    }

    if (expr.operator.is(AMPERSAND_AMPERSAND)) {
      expr.constValue = RuntimeUtils.isTruth(leftValue, false) &&
                        RuntimeUtils.isTruth(rightValue, false);
      return expr.type;
    }
    if (expr.operator.is(PIPE_PIPE)) {
      expr.constValue = RuntimeUtils.isTruth(leftValue, false) ||
                        RuntimeUtils.isTruth(rightValue, false);
      return expr.type;
    }

    if (expr.operator.getType().isBooleanOperator()) {
      if (expr.operator.is(TRIPLE_EQUAL,BANG_EQUAL_EQUAL) && (leftValue instanceof List || leftValue instanceof Map)) {
        expr.constValue = expr.operator.is(TRIPLE_EQUAL) == leftValue.equals(rightValue);
      }
      else {
        try {
          expr.constValue = RuntimeUtils.booleanOp(leftValue, rightValue,
                                                   RuntimeUtils.getOperatorType(expr.operator.getType()),
                                                   expr.operator.getSource(), expr.operator.getOffset());
        }
        catch (RuntimeError e) {
          error(e);
        }
      }
      return expr.type = BOOLEAN;
    }

    if (leftValue == null)  { error("Null operand for left-hand side of '" + expr.operator.getChars(), expr.operator); return expr.type = TYPE_FOR_BAD_REF; }
    if (rightValue == null) { error("Null operand for right-hand side of '" + expr.operator.getChars(), expr.operator); return expr.type = TYPE_FOR_BAD_REF; }

    switch (expr.type.getType()) {
      case BYTE: case INT: {
        int left  = Utils.toInt(leftValue);
        int right = Utils.toInt(rightValue);
        if (expr.type.is(BYTE) && expr.operator.is(DOUBLE_LESS_THAN)) {
          right = right & 0x07;
        }
        if (expr.operator.is(SLASH, PERCENT, PERCENT_PERCENT) && right == 0) {
          error("Divide by zero error", expr.right.location);
          return expr.type = TYPE_FOR_BAD_REF;
        }
        switch (expr.operator.getType()) {
          case PLUS:                expr.constValue = left + right;   break;
          case MINUS:               expr.constValue = left - right;   break;
          case STAR:                expr.constValue = left * right;   break;
          case SLASH:               expr.constValue = left / right;   break;
          case PERCENT_PERCENT:     expr.constValue = left % right;   break;
          case PERCENT:             expr.constValue = ((left % right) + right) % right;   break;
          case AMPERSAND:           expr.constValue = left & right;   break;
          case PIPE:                expr.constValue = left | right;   break;
          case ACCENT:              expr.constValue = left ^ right;   break;
          case DOUBLE_LESS_THAN:    expr.constValue = left << right;  break;
          case DOUBLE_GREATER_THAN: expr.constValue = left >> right;  break;
          case TRIPLE_GREATER_THAN: expr.constValue = left >>> right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for ints");
        }
        if (expr.type.is(BYTE)) {
          expr.constValue = (byte)(int)expr.constValue;
        }
        break;
      }
      case LONG: {
        long left  = Utils.toLong(leftValue);
        long right = Utils.toLong(rightValue);
        if (expr.operator.is(SLASH, PERCENT, PERCENT_PERCENT) && right == 0) {
          error("Divide by zero error", expr.right.location);
          return expr.type = TYPE_FOR_BAD_REF;
        }
        switch (expr.operator.getType()) {
          case PLUS:                expr.constValue = left + right;        break;
          case MINUS:               expr.constValue = left - right;        break;
          case STAR:                expr.constValue = left * right;        break;
          case SLASH:               expr.constValue = left / right;        break;
          case PERCENT_PERCENT:     expr.constValue = left % right;        break;
          case PERCENT:             expr.constValue = ((left % right) + right) % right;   break;
          case AMPERSAND:           expr.constValue = left & right;        break;
          case PIPE:                expr.constValue = left | right;        break;
          case ACCENT:              expr.constValue = left ^ right;        break;
          case DOUBLE_LESS_THAN:    expr.constValue = left << (int)right;  break;
          case DOUBLE_GREATER_THAN: expr.constValue = left >> (int)right;  break;
          case TRIPLE_GREATER_THAN: expr.constValue = left >>> (int)right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for longs");
        }
        break;
      }
      case DOUBLE: {
        double left  = Utils.toDouble(leftValue);
        double right = Utils.toDouble(rightValue);
        switch (expr.operator.getType()) {
          case PLUS:            expr.constValue = left + right;                     break;
          case MINUS:           expr.constValue = left - right;                     break;
          case STAR:            expr.constValue = left * right;                     break;
          case SLASH:           expr.constValue = left / right;                     break;
          case PERCENT_PERCENT: expr.constValue = left % right;                     break;
          case PERCENT:         expr.constValue = ((left % right) + right) % right; break;
          default: throw new IllegalStateException("Internal error: operator " + expr.operator.getChars() + " not supported for doubles");
        }
        break;
      }
      case DECIMAL: {
        BigDecimal left  = Utils.toDecimal(leftValue);
        BigDecimal right = Utils.toDecimal(rightValue);
        if (expr.operator.is(SLASH, PERCENT, PERCENT_PERCENT) && right.stripTrailingZeros() == BigDecimal.ZERO) {
          error("Divide by zero error", expr.right.location);
          return expr.type = TYPE_FOR_BAD_REF;
        }
        expr.constValue = RuntimeUtils.decimalBinaryOperation(left, right, RuntimeUtils.getOperatorType(expr.operator.getType()), jactlContext.minScale,
                                                              expr.operator.getSource(), expr.operator.getOffset());
        break;
      }
    }
    return expr.type;
  }

  private void addHeapLocalToParents(String name, Expr.VarDecl varDecl) {
    Expr.VarDecl childVarDecl = varDecl;

    // Now iterate through parents linking VarDecls until we get to one that already
    // has a var by that name (which will be the actual VarDecl or an intermediate function
    // that already has it being passed in as a parameter).
    for (Iterator<Expr.FunDecl> iter = getFunctions().iterator(); iter.hasNext(); ) {
      // If parent already has this var as a parameter then point child to this and return
      Expr.FunDecl funDecl       = iter.next();
      Expr.VarDecl parentVarDecl = funDecl.heapLocalsByName.get(name);
      if (parentVarDecl != null) {
        childVarDecl.parentVarDecl = parentVarDecl;
        return;
      }

      // If we have reached function that originally declared this var then point child
      // to this one and return
      if (funDecl == varDecl.owner) {
        childVarDecl.parentVarDecl = varDecl.originalVarDecl;
        return;
      }

      // Otherwise build a new VarDecl for an intermediate value and make it a parameter
      // of the current function by adding to its heapLocal Parameters
      parentVarDecl = Utils.createVarDecl(name, varDecl, funDecl);
      funDecl.heapLocalsByName.put(name, parentVarDecl);
      childVarDecl.parentVarDecl = parentVarDecl;

      // Now parent becomes the child for the next iteration
      childVarDecl = parentVarDecl;
    }
    throw new IllegalStateException("Internal error: couldn't find owner of variable " + varDecl.name.getStringValue());
  }

  private static Object incOrDec(boolean isInc, JactlType type, Object val) {
    int incAmount = isInc ? 1 : -1;
    switch (type.getType()) {
      case BYTE:    return (byte)(((byte)val) + incAmount);
      case INT:     return ((int)val) + incAmount;
      case LONG:    return ((long)val) + incAmount;
      case DOUBLE:  return ((double)val) + incAmount;
      case DECIMAL: return ((BigDecimal)val).add(BigDecimal.valueOf(incAmount));
    }
    throw new IllegalStateException("Internal error: unexpected type " + type);
  }

  /////////////////////////

  private Stmt.While findWhileLoop(Token token, Token label) {
    Deque<Stmt.While> whileLoops = currentFunction().whileLoops;
    if (whileLoops.size() == 0) {
      error(token.getChars() + " must be within a while/for loop", token);
    }
    if (label == null) {
      return whileLoops.peek();
    }
    for (Stmt.While whileStmt: whileLoops) {
      if (whileStmt.label.getStringValue().equals(label.getStringValue())) {
        return whileStmt;
      }
    }
    error("Could not find enclosing loop with label " + label.getStringValue(), label);
    return null;
  }

  void error(String msg, SourceLocation location) {
    if (!(location instanceof Location)) {
      if (jactlContext.isIdePlugin()) {
        return;
      }
      //throw new IllegalStateException("Internal error: Expected location of type Location but got " + location.getClass().getName());
    }
    error(new CompileError(msg, (Location) location));
  }

  private void error(RuntimeError error) {
    error(error.getErrorMessage(), error.getLocation());
  }

  private void error(CompileError error) {
    errors.add(error);
    // throw error;
  }

  private static Expr.VarDecl UNDEFINED = new Expr.VarDecl(null, null, null);

  private void declare(Expr.VarDecl decl) {
    if (decl.name == null) {
      return;
    }
    String varName = decl.name.getStringValue();

    // For binding variables make sure we don't shadow an existing variable of same name to prevent
    // confusion. Otherwise, something like [1,2,i] might be mistaken for a list literal which expands
    // existing value of 'i'.
    if (decl.isBindingVar) {
      if (variableExists(varName)) {
        error("Binding variable '" + varName + "' shadows another variable of the same name already declared", decl.location);
      }
    }


    // If we have a field then get class block variables, otherwise get vars for current block
    Map<String, Expr.VarDecl> vars = decl.isField ? currentClass().classBlock.variables : getVars(decl.isParam);
    if (!(jactlContext.replMode && isAtTopLevel())) {
      Expr.VarDecl existingDecl = vars.get(varName);
      // Allow fields to be shadowed by local variables
      if (existingDecl != null && (!existingDecl.isField || decl.isField)) {
        error("Variable '" + varName + "' in scope " + currentFunctionName() +
              " clashes with previously declared " + (existingDecl.isField ? "field" : "variable") + " of same name", decl.name);
      }
    }
    // Add variable with type of UNDEFINED as a marker to indicate variable has been declared but is
    // not yet usable
    vars.put(varName, UNDEFINED);
  }

  private static String id(Object object) {
    return "0x" + Long.toHexString(System.identityHashCode(object));
  }

  private String currentFunctionName() {
    Expr.FunDecl currentFunction = currentFunction();
    if (currentFunction.nameToken == null) {
      return "closure<" + currentFunction.functionDescriptor.implementingMethod + ">";
    }
    if (currentFunction.isScriptMain) {
      return "script";
    }
    return currentFunction.nameToken.getStringValue();
  }

  private void define(Token name, Expr.VarDecl decl) {
    // If we have a field then get class block variables, otherwise get vars for current block
    Map<String, Expr.VarDecl> vars = decl.isField ? currentClass().classBlock.variables : getVars(decl.isParam);

    // In repl mode we don't have a top level block, and we store var types in the compileContext
    // and their actual values will be stored in the globals map.
    if (!decl.isParam && !decl.isConstVar && isAtTopLevel() && jactlContext.replMode) {
      decl.isGlobal = true;
      decl.type = decl.type.boxed();
    }
    // Remember at what nesting level this variable is declared. This allows us to work
    // out when looking up vars whether this belongs to the current function or not.
    decl.nestingLevel = getFunctions().size();
    vars.put(name.getStringValue(), decl);
  }

  private boolean isAtTopLevel() {
    return currentFunction().isScriptMain && currentFunction().blocks.size() == 1;
  }

  // Get variables for current block
  private Map<String,Expr.VarDecl> getVars(boolean isParam) {
    // Special case for repl mode where we ignore top level block
    if (!isParam && jactlContext.replMode && isAtTopLevel()) {
      return jactlContext.globalVars;
    }
    return getBlock().variables;
  }

  /**
   * Get current block within current function.
   * If function has no blocks yet it means we want the enclosing block from
   * the parent function in which current function has been declared.
   * @return the current block
   */
  private Stmt.Block getBlock() {
    Expr.FunDecl funDecl = currentFunction();
    if (funDecl == null) {
      return topBlock;
    }
    if (funDecl.blocks.isEmpty()) {
      funDecl = currentClass().nestedFunctions.stream().skip(1).findFirst().orElse(null);
    }
    Stmt.Block block = funDecl == null ? null : funDecl.blocks.peek();
    return block == null ? topBlock : block;
  }

  private FunctionDescriptor lookupMethod(JactlType type, String methodName) {
    if (type.is(CLASS, INSTANCE)) {
      ClassDescriptor clss = type.getClassDescriptor();
      if (clss != null) {
        FunctionDescriptor funDesc = clss.getMethod(methodName);
        if (funDesc != null) {
          return funDesc;
        }
      }
    }
    return Functions.lookupMethod(type, methodName);
  }

  private ClassDescriptor lookupClass(List<Expr> classNameParts) {
    return lookupClass(classNameParts, false);
  }

  ClassDescriptor lookupClass(List<Expr> classNameParts, boolean checkForExistence) {
    Function<Expr,String> identStr = expr -> ((Expr.Identifier)expr).identifier.getStringValue();

    if (classNameParts == null || classNameParts.size() == 0) {
      throw new IllegalStateException("Internal error: class name is empty");
    }
    String classPkg  = null;
    String firstClass = null;
    Expr firstPart = classNameParts.get(0);
    Token packageToken = firstPart.location;
    Token classToken   = firstPart.location;
    if (firstPart instanceof Expr.ClassPath) {
      Expr.ClassPath classPath = (Expr.ClassPath)firstPart;
      classPkg = classPath.pkg.getStringValue();
      classToken = classPath.className;
      firstClass = classToken.getStringValue();
    }
    else {
      firstClass = identStr.apply(classNameParts.get(0));
    }

    ClassDescriptor descriptor = lookupClass(classPkg, packageToken, firstClass, classToken, "", checkForExistence);
    firstPart.type = descriptor == null ? null : JactlType.createClass(descriptor);
    firstPart.setBlock(getBlock());

    for (Iterator<Expr> partIter = classNameParts.subList(1, classNameParts.size()).iterator(); partIter.hasNext(); ) {
      Expr.Identifier part = (Expr.Identifier)partIter.next();
      descriptor = descriptor == null ? null : descriptor.getInnerClass(part.identifier.getStringValue());
      if (descriptor == null) {
        if (checkForExistence) { return null; }
        error("Unknown inner class", part.location);
      }
      else {
        part.type = JactlType.createClass(descriptor);
        part.setBlock(getBlock());
      }
    }

    return descriptor;
  }

  private Expr.VarDecl lookupClass(String className, Token location) {
    ClassDescriptor classDescriptor = lookupClass(null, null, className, null, "", false);
    if (classDescriptor == null) {
      return null;
    }
    Expr.VarDecl classVarDecl = new Expr.VarDecl(location == null ? null : location.newIdent(className), null, null);
    classVarDecl.classDescriptor = classDescriptor;
    classVarDecl.type = JactlType.createClass(classDescriptor);
    return classVarDecl;
  }

  private ClassDescriptor lookupClass(String classPkg, Token packageToken, String firstClass, Token classToken, String subPath, boolean checkForExistence) {
    String className = null;
    // If no package supplied then we need to search for within current script/class and then if not
    // found check for any imported classes that match
    if (classPkg == null) {
      // Class could be any class (inner or top level) from current class scope upwards or
      // a path that starts from any class in the hierarchy.
      boolean currentScope = true;
      for (Stmt.ClassDecl classStmt : classStack) {
        if (classStmt.name.getStringValue().equals(firstClass)) {
          className = classStmt.classDescriptor.getNamePath() + subPath;
          break;
        }
        if (classStmt.classDescriptor.getInnerClass(firstClass) != null) {
          className = classStmt.classDescriptor.getNamePath() + '$' + firstClass + subPath;
          break;
        }
        if (currentScope) {
          // Check for innerclass of base class if we are in current scope (haven't started to check
          // enclosing scope)
          for (JactlType baseClass = classStmt.baseClass; baseClass != null && baseClass.getClassDescriptor() != null; baseClass = baseClass.getClassDescriptor().getBaseClassType()) {
            if (baseClass.getClassDescriptor().getInnerClass(firstClass) != null) {
              className = baseClass.getClassDescriptor().getNamePath() + '$' + firstClass + subPath;
              break;
            }
          }
          if (className != null) {
            break;
          }
        }
        currentScope = false;
      }
      // If not in current hierarchy and in a script then look for a top level class of that name
      if (className == null && isScript) {
        String          topLevelClass = jactlContext.replMode ? firstClass : scriptName + '$' + firstClass;
        ClassDescriptor topClass      = localClasses.get(topLevelClass);
        if (topClass != null) {
          className = topLevelClass + subPath;
        }
      }
      if (className == null) {
        // Check for imports
        ClassDescriptor importedClass = imports.get(firstClass);
        if (importedClass != null) {
          className = importedClass.getNamePath() + subPath;
          classPkg  = importedClass.getPackageName();
        }
      }
      if (className == null) {
        // Check for class in current package
        if (jactlContext.getClassDescriptor(packageName, firstClass + subPath) != null) {
          className = firstClass + subPath;
        }
      }
      if (className == null) {
        if (classToken != null) {
          if (checkForExistence) { return null; }
          error("Unknown class '" + firstClass + "'", classToken);
        }
        return null;
      }
    }
    else {
      className = firstClass + subPath;
    }

    // If no package supplied or current package
    ClassDescriptor descriptor = null;
    if (classPkg == null) {
      classPkg = packageName;
    }
    if (classPkg.equals(packageName)) {
      descriptor = localClasses.get(className);
      if (descriptor == null && isScript && !jactlContext.replMode) {
        // Could be a.b.c.X in a script where script is the owner of the class so look for a top
        // level class within the script
        descriptor = localClasses.get(scriptName + '$' + className);
      }
    }
    if (descriptor == null) {
      if (jactlContext.packageExists(classPkg)) {
        descriptor = jactlContext.getClassDescriptor(classPkg, className);
      }
      else {
        if (!classPkg.equals(packageName)) {
          if (checkForExistence) { return null; }
          error("Unknown package '" + classPkg + "'", packageToken);
        }
      }
    }

    if (descriptor == null && classToken != null) {
      if (checkForExistence) { return null; }
      error("Unknown class '" + className.replaceAll("\\$", ".") + "' in package " + classPkg, classToken);
    }
    return descriptor;
  }

  private Expr.VarDecl lookupFunction(Token identifier) {
    return lookup(identifier.getStringValue(), identifier, true, false);
  }

  private Expr.VarDecl lookup(Token identifier) {
    return lookup(identifier.getStringValue(), identifier, false, false);
  }

  private boolean variableExists(String name) {
    return lookup(name, null, false, true) != null;
  }

  public static Expr.VarDecl ERROR_VARDECL = new Expr.VarDecl(null, null, null);
  static { ERROR_VARDECL.type = TYPE_FOR_BAD_REF; }

  // We look up variables in our local scope and parent scopes within the same function.
  // If we still can't find the variable we search in parent functions to see if the
  // variable exists there (at which point we close over it and it becomes a heap variable
  // rather than one that is a JVM local).
  Expr.VarDecl lookup(String name, Token location, boolean functionLookup, boolean existenceCheckOnly) {
    // Special case for capture vars which are of form $n where n > 0
    // For these vars we actually look for the $@ capture array var since $n means $@[n]
    if (name.charAt(0) == '$') {
      String substr = name.substring(1);
      name = Utils.isDigits(substr) ? Utils.CAPTURE_VAR : substr;
    }

    Expr.VarDecl varDecl = null;
    Stmt.Block block = null;
    FUNC_LOOP:
    for (Iterator<Expr.FunDecl> funcIt = getFunctions().iterator(); funcIt.hasNext(); ) {
      Expr.FunDecl funDecl = funcIt.next();
      for (Iterator<Stmt.Block> it = funDecl.blocks.iterator(); it.hasNext(); ) {
        block = it.next();
        varDecl = block.variables.get(name);
        if (varDecl != null) {
          break FUNC_LOOP;  // Break out of both loops
        }
      }
    }
    if (varDecl == null) {  block = null;  }

    if (inStaticContext() && (name.equals(Utils.THIS_VAR) || name.equals(Utils.SUPER_VAR))) {
      error("Reference to '" + name + "' in static function", location);
      return ERROR_VARDECL;
    }

    // We haven't found symbol yet so check super classes, class names, constants (from import static),
    // and built-in functions
    if (varDecl == null) { varDecl = lookupClassMember(name);                  }
    if (varDecl == null) { varDecl = lookupClass(name, location);              }
    if (varDecl == null) { varDecl = importedConstants.get(name);                      }
    if (varDecl == null) { varDecl = Functions.getGlobalFunDecl(name); }

    // Finally check for global. If in repl mode then we will allow auto-creation of globals
    // if value does not already exist.
    if (varDecl == null) {
      varDecl = lookupGlobals(name, location);
      if (varDecl != null) {
        // Keep track of which globals are used in this function so that we can create local var
        // aliases for them as an optimisation in MethodCompiler
        currentFunction().globals.put(name, varDecl);
      }
    }

    if (existenceCheckOnly) {
      return varDecl;
    }

    if (varDecl == UNDEFINED) {
      error("Variable initialisation cannot refer to itself", location);
      return ERROR_VARDECL;
    }

    if (varDecl != null) {
      if (varDecl.isConstVar) {
        return varDecl;
      }
      if (varDecl.isField && inStaticContext()) {
        if (varDecl.funDecl == null && !varDecl.isConstVar) {
          error("Reference to non-static field in static function", location);
          return ERROR_VARDECL;
        }
        if (varDecl.funDecl != null && !varDecl.funDecl.isStatic()) {
          error("Reference to non-static method in static function", location);
          return ERROR_VARDECL;
        }
      }

      if (varDecl.type.is(CLASS)) {
        return varDecl;
      }

      if (varDecl.funDecl != null) {
        // Track earliest reference to detect where forward referenced function closes over
        // vars not yet declared at time of reference
        Token reference = varDecl.funDecl.earliestForwardReference;
        if (reference == null || Utils.isEarlier(location, reference)) {
          varDecl.funDecl.earliestForwardReference = location;
        }
      }

      if (varDecl.funDecl != null && varDecl.funDecl.functionDescriptor.isBuiltin) {
        return varDecl;
      }

      // Normal local or global variable (not a closed over var) or a function
      // which we can call directly (if we are being asked to do a function lookup)
      if (varDecl.isGlobal || varDecl.nestingLevel == getFunctions().size() ||
          (functionLookup && varDecl.funDecl != null)) {
        return varDecl;
      }

      // No need to close over fields since they are always visible
      if (varDecl.isField) {
        return varDecl;
      }

      // If we are looking for a function then return null since we found something that wasn't a function
      if (functionLookup) {
        return null;
      }

      // Nesting level is different and variable is a local variable so turn it into
      // a heap var
      varDecl.isHeapLocal = true;

      // If the variable is a parameter and we are resolving the initialisers for parameters of
      // the same function then it means we have a parameter that is closed over by a nested
      // closure/function within the initialiser of another parameter: def f(x,y={x}) {...}
      // We need to know this so we can get the wrapper method to turn the parameter into a
      // HeapLocal _before_ invoking the actual method. Usually we only need do the conversion
      // within the method itself if the parameter is then passed to a nested function but in
      // this case the nested function is created in the parameter initialiser which is compiled
      // within the wrapper method and so the HeapLocal needs to be passed to this nested function
      // before the actual method has been invoked.
      if (block != null && block.isResolvingParams && varDecl.paramVarDecl != null) {
        varDecl.paramVarDecl.isHeapLocal = true;
        varDecl.paramVarDecl.owner = varDecl.owner;
        varDecl.paramVarDecl.originalVarDecl = varDecl;
        varDecl.paramVarDecl.isPassedAsHeapLocal = true;
      }

      Expr.FunDecl currentFunc = currentFunction();

      // Check if we already have it in our list of heapLocal Params
      if (currentFunc.heapLocalsByName.containsKey(name)) {
        return currentFunc.heapLocalsByName.get(name);
      }

      // Add this var to our list of heap vars we need passed in to us.
      // Construct a new VarDecl since we will have our own slot that is local
      // to this function and add it the heapLocal Params of the currentFunc
      Expr.VarDecl newVarDecl = Utils.createVarDecl(name, varDecl, currentFunc);
      currentFunc.heapLocalsByName.put(name, newVarDecl);
      newVarDecl.originalVarDecl = varDecl;

      return newVarDecl;
    }

    if (name.equals(Utils.CAPTURE_VAR)) {
      error("Reference to regex capture variable " + location.getStringValue() + " in scope where no regex match has occurred", location);
      return ERROR_VARDECL;
    }
    else {
      if (!functionLookup) {
        if (currentClass().fieldVars.containsKey(name)) {
          error("Forward reference to field '" + name + "'", location);
        }
        else if (name.equals(Utils.SUPER_VAR)) {
          error("Reference to 'super' in class that does not extend any base class", location);
        }
        else {
          error("Reference to unknown variable '" + name + "'", location);
        }
        return ERROR_VARDECL;
      }
    }
    return null;
  }

  private Expr.VarDecl lookupGlobals(String name, Token location) {
    // Access to globals not allowed in classes. Only allowed in scripts.
    // Even if class is nested within script we don't allow access since it requires
    // access to "this" for the script itself which we don't have.
    Expr.VarDecl global = jactlContext.globalVars.get(name);
    if (isScriptScope() || jactlContext.classAccessToGlobals) {
      return global;
    }
    if (global != null) {
      error("Illegal access to global '" + name + "': access to globals not permitted within class scope", location);
    }
    return null;
  }

  private boolean isScriptScope() {
    return classStack.size() == 1 && currentClass().scriptMain != null;
  }

  private Expr.VarDecl lookupClassMember(String name) {
    if (isScriptScope()) { return null; }
    Stmt.ClassDecl classDecl = currentClass();
    // Note: we have already checked for any of our current fields since our class block is at the bottom of the
    // stack of blocks. We need to check for any fields from our base classes.
    ClassDescriptor baseClass = classDecl.classDescriptor.getBaseClass();
    if (baseClass == null) { return null; }

    return null;
  }

  private boolean inStaticContext() {
    return getFunctions().stream().anyMatch(func -> func.isStatic());
  }

  /////////////////////////////////////////////////

  /**
   * Find last statement and turn it into a return statement if not already a return statement. This is used to turn
   * implicit returns in functions into explicit returns to simplify the job of the Resolver and Compiler phases.
   */
  private Stmt explicitReturn(Stmt stmt, JactlType returnType) {
    stmt.setBlock(getBlock());
    return doExplicitReturn(stmt, returnType);
  }

  private Stmt doExplicitReturn(Stmt stmt, JactlType returnType) {
    Stmt newStmt = stmt.accept(new Stmt.Visitor<Stmt>() {
      @Override public Stmt visitStmts(Stmt.Stmts stmt) {
        List<Stmt> stmts = stmt.stmts;
        if (stmts.isEmpty()) {
          if (returnType.isPrimitive()) {
            error("Implicit return of null not compatible with return type of " + returnType, stmt.location);
          }
          stmts.add(returnStmt(stmt.location, returnType));
        }
        else {
          Stmt newStmt = explicitReturn(stmts.get(stmts.size() - 1), returnType);
          stmts.set(stmts.size() - 1, newStmt);   // Replace implicit return with explicit if necessary
        }
        return stmt;
      }

      @Override public Stmt visitBlock(Stmt.Block stmt) {
        stmt.stmts = (Stmt.Stmts)doExplicitReturn(stmt.stmts, returnType);
        return stmt;
      }

      @Override public Stmt visitIf(Stmt.If stmt) {
        if (stmt.trueStmt == null || stmt.falseStmt == null) {
          if (returnType.isPrimitive()) {
            error("Implicit return of null for " +
                  (stmt.trueStmt == null ? "true" : "false") + " condition of if statement not compatible with return type of " + returnType, stmt.location);
          }
          if (stmt.trueStmt == null) {
            stmt.trueStmt = returnStmt(stmt.ifToken, returnType);
          }
          if (stmt.falseStmt == null) {
            stmt.falseStmt = returnStmt(stmt.ifToken, returnType);
          }
        }
        stmt.trueStmt  = doExplicitReturn(stmt.trueStmt, returnType);
        stmt.falseStmt = doExplicitReturn(stmt.falseStmt, returnType);
        return stmt;
      }

      @Override public Stmt visitVarDecl(Stmt.VarDecl stmt) {
        Expr.VarDecl declExpr = stmt.declExpr;
        // Don't use parameter declarations as value to return
        if (!declExpr.isExplicitParam) {
          declExpr.isResultUsed = true;
          Stmt.Return returnStmt = returnStmt(stmt.location, declExpr, returnType);
          return returnStmt;
        }
        return null;
      }

      @Override public Stmt visitFunDecl(Stmt.FunDecl stmt) {
        // If last statement is a function declaration then we return the MethodHandle for the
        // function as the return value of our function
        // We set a flag on the statement so that Compiler knows to leave result on the stack
        // and replace the assignment statement with a return wrapping the assignment expression.
        Expr.FunDecl declExpr = stmt.declExpr;
        declExpr.isResultUsed = true;
        declExpr.varDecl.isResultUsed = true;
        Stmt.Return returnStmt = returnStmt(stmt.location, declExpr, returnType);
        return returnStmt;
      }

      @Override public Stmt visitReturn(Stmt.Return stmt)        { return stmt; }
      @Override public Stmt visitThrowError(Stmt.ThrowError stmt) { return stmt; }

      @Override public Stmt visitExprStmt(Stmt.ExprStmt stmt) {
        Expr expr = stmt.expr;
        expr.isResultUsed = true;
        Stmt.Return returnStmt = returnStmt(stmt.location, expr, returnType);
        return returnStmt;
      }

      @Override public Stmt visitWhile(Stmt.While stmt)         { return null; }
      @Override public Stmt visitClassDecl(Stmt.ClassDecl stmt) { return null; }
      @Override public Stmt visitImport(Stmt.Import stmt)       { return null; }
    });

    // If we have been able to find something to turn into a return
    if (newStmt != null) {
      return newStmt;
    }

    // For functions that return an object (i.e. not a primitive) there is an implicit "return null"
    // even if last statement does not have a value so replace stmt with list of statements that
    // include the stmt and then a "return null" statement.
    if (returnType.isRef()) {
      Stmt.Stmts stmts = new Stmt.Stmts(stmt.location);
      stmts.stmts.add(stmt);
      stmts.location = stmt.location;
      Stmt.Return returnStmt = returnStmt(stmt.location, new Expr.Literal(new Token(NULL, stmt.location)), returnType);
      stmts.stmts.add(returnStmt);
      return stmts;
    }

    // Other statements are not supported for implicit return
    error("Implicit return of null incompatible with function return type of " + returnType, stmt.location);
    return null;
  }

  /**
   * Create a wrapper function for invoking the real function/method.
   * The job of the wrapper function is to support invocation from places where we don't know
   * how many arguments are required and what their types are.
   * E.g.:
   * <pre>  def f(int x, String y) {...}; def g = f; g(1,'xyz')</pre>
   * When we call via the variable 'g' we don't know anything about which function it points to, and
   * so we pass in an Object[] and invoke the wrapper function which extracts the arguments to then
   * call the real function.
   * <p>The wrapper function also takes care of filling in the default values for any missing paremeters
   * and validates that the number of arguments passed is legal for the function.</p>
   * <p>The wrapper function will also take care of named argument passing.</p>
   */
  private Expr.FunDecl createVarArgWrapper(Expr.FunDecl funDecl) {
    // Copy token so we can set flag that means we won't generate line numbers during code generation
    Token startToken = new Token(funDecl.startToken.getType(), funDecl.startToken);
    startToken.setGenerateLineNumber(false);

    //-----------------------------------
    // Some helper lambdas...
    Function<TokenType,Token>        token       = type -> new Token(type, startToken);
    Function<String,Token>           identToken  = name -> token.apply(IDENTIFIER).setValue(name);
    Function<String,Expr.Identifier> ident       = name -> new Expr.Identifier(identToken.apply(name));
    Function<Object,Expr.Literal>    intLiteral  = value -> new Expr.Literal(new Token(INTEGER_CONST, startToken).setValue(value));
    Function<String,Expr.Literal>    strLiteral  = value -> new Expr.Literal(new Token(STRING_CONST, startToken).setValue(value));
    Expr.Literal                     trueExpr    = new Expr.Literal(token.apply(TRUE).setValue(true));
    Expr.Literal                     falseExpr   = new Expr.Literal(token.apply(FALSE).setValue(false));
    Function<TokenType,Expr.Literal> typeLiteral = type  -> new Expr.Literal(token.apply(type));
    BiFunction<Expr,JactlType,Expr> instOfExpr   = (name,type) -> new Expr.Binary(name,
                                                                                  token.apply(INSTANCE_OF),
                                                                                  new Expr.TypeExpr(startToken, type));
    BiFunction<String,Expr,Stmt>     assignStmt = (name,value) -> {
      Stmt.ExprStmt assign = new Stmt.ExprStmt(startToken, new Expr.VarAssign(ident.apply(name), token.apply(EQUAL), value));
      assign.expr.isResultUsed = false;
      return assign;
    };

    BiFunction<String, JactlType,Stmt.VarDecl> createParam =
      (name,type) -> {
        return Utils.createParam(funDecl.startToken.newIdent(name), type, null, false, true);
      };
    //-----------------------------------

    Expr.Identifier sourceIdent = ident.apply(Utils.SOURCE_VAR_NAME);
    Expr.Identifier offsetIdent = ident.apply(Utils.OFFSET_VAR_NAME);
    Expr.Identifier argsIdent   = ident.apply(Utils.ARGS_VAR_NAME);

    Expr.FunDecl wrapperFunDecl = Utils.createWrapperFunDecl(startToken, funDecl.functionDescriptor.implementingMethod, funDecl.isStatic());
    Stmt.Stmts stmts = new Stmt.Stmts(startToken);
    List<Stmt> stmtList = stmts.stmts;
    stmtList.addAll(wrapperFunDecl.parameters);

    if (false) {
      Expr.Print debugPrint = new Expr.Print(new Token("println", 0).setType(PRINTLN),
                                             new Expr.Literal(new Token(STRING_CONST, startToken).setValue("Wrapper: " + funDecl.nameToken.getStringValue())),
                                             true);
      debugPrint.isResultUsed = false;
      stmts.stmts.add(new Stmt.ExprStmt(startToken, debugPrint));
    }

    //  : boolean _$isObjArr = true
    String isObjArrName = JACTL_PREFIX + "isObjArr";
    stmtList.add(createVarDecl(startToken, wrapperFunDecl, isObjArrName, BOOLEAN, trueExpr));
    Expr.Identifier isObjArrIdent = ident.apply(isObjArrName);

    //  :   int _$argCount = _$args.length
    String argCountName = JACTL_PREFIX + "argCount";
    stmtList.add(createVarDecl(startToken, wrapperFunDecl, argCountName, INT, new Expr.ArrayLength(startToken, argsIdent)));
    final Expr.Identifier argCountIdent = ident.apply(argCountName);

    //  :   Map _$mapCopy = null
    String mapCopyName = JACTL_PREFIX + "mapCopy";
    stmtList.add(createVarDecl(startToken, wrapperFunDecl, mapCopyName, MAP, new Expr.Literal(token.apply(NULL))));
    Expr.Identifier mapCopyIdent = ident.apply(mapCopyName);

    int paramCount = funDecl.parameters.size();
    int mandatoryCount = funDecl.functionDescriptor.mandatoryArgCount;

    Expr.Binary argCountIs1 = new Expr.Binary(argCountIdent, token.apply(EQUAL_EQUAL), intLiteral.apply(1));

    // Special case to handle situation where we have List passed as only arg within the Object[].
    // If there is one parameter (or one mandatory parameter) then we pass the list as a single argument.
    // Otherwise, we treat the List as a list of argument values.
    boolean passListAsList = paramCount == 1 || mandatoryCount == 1;
    boolean treatSingleArgListAsArgs = !passListAsList;
    if (treatSingleArgListAsArgs) {
      //:   if (_$argCount == 1 && _$argArr[0] instanceof List) {
      //:     _$argArr = ((List)_$argArr[0]).toArray()
      //:     _$argCount = _$argArr.length
      //:   }
      Expr       arg0IsList  = instOfExpr.apply(new Expr.ArrayGet(startToken, argsIdent, intLiteral.apply(0)), LIST);
      Stmt.Stmts ifTrueStmts = new Stmt.Stmts(startToken);
      Expr.ArrayGet getArg0     = new Expr.ArrayGet(startToken, argsIdent, intLiteral.apply(0));
      Expr.InvokeUtility toObjectArr = new Expr.InvokeUtility(startToken, RuntimeUtils.class, "listToObjectArray", Utils.listOf(Object.class), Utils.listOf(getArg0));
      ifTrueStmts.stmts.add(assignStmt.apply(Utils.ARGS_VAR_NAME, toObjectArr));
      ifTrueStmts.stmts.add(assignStmt.apply(argCountName, new Expr.ArrayLength(startToken, argsIdent)));
      Stmt.If ifArg0IsList = new Stmt.If(startToken,
                                         new Expr.Binary(argCountIs1, token.apply(AMPERSAND_AMPERSAND), arg0IsList),
                                         ifTrueStmts,
                                         null);
      stmtList.add(ifArg0IsList);
    }

    // For named args we expect a NamedArgsMap unless we are the init method in which case any Map indicates
    // that we should use the map contents as the values for the fields.
    //  :   if (_$argCount == 1 && _$argArr[0] instanceof Map/NamedArgsMap) {
    //  :     _$mapCopy = RuntimeUtils.copyMap(_$argMap)
    //  :     _$isObjArr = false
    //  :   }
    Expr.InstanceOf arg0IsMap = new Expr.InstanceOf(startToken,
                                                    new Expr.ArrayGet(startToken, argsIdent, intLiteral.apply(0)),
                                                    Type.getInternalName(NamedArgsMap.class));
    Stmt.Stmts mapStmts = new Stmt.Stmts(startToken);
    Stmt.If ifArg0IsMap = new Stmt.If(startToken,
                                      new Expr.Binary(argCountIs1, token.apply(AMPERSAND_AMPERSAND), arg0IsMap),
                                      mapStmts,
                                      null);
    mapStmts.stmts.add(assignStmt.apply(mapCopyName, new Expr.InvokeUtility(startToken, RuntimeUtils.class,
                                                                            "copyArg0AsMap",
                                                                            Utils.listOf(Object[].class), Utils.listOf(argsIdent))));
    mapStmts.stmts.add(assignStmt.apply(isObjArrName, falseExpr));
    stmtList.add(ifArg0IsMap);

    if (mandatoryCount > 0) {
      //:   if (_$isObjArr && argCount < mandatoryCount) { throw new RuntimeError("Missing mandatory arguments") }
      Stmt.If throwIf = new Stmt.If(startToken,
                                    new Expr.Binary(isObjArrIdent, token.apply(AMPERSAND_AMPERSAND),
                                                new Expr.Binary(argCountIdent, token.apply(LESS_THAN), intLiteral.apply(mandatoryCount))),
                                    new Stmt.ThrowError(startToken, sourceIdent, offsetIdent,"Missing mandatory arguments"),
                                    null);
      stmtList.add(throwIf);
    }

    //  :   if (_$isObjArr && argCount > paramCount) { throw new RuntimeError("Too many arguments) }
    Stmt.If throwIfTooMany = new Stmt.If(startToken,
                                         new Expr.Binary(isObjArrIdent, token.apply(AMPERSAND_AMPERSAND),
                                                     new Expr.Binary(argCountIdent, token.apply(GREATER_THAN), intLiteral.apply(paramCount))),
                                         new Stmt.ThrowError(startToken, sourceIdent, offsetIdent, "Too many arguments"),
                                         null);
    stmtList.add(throwIfTooMany);

    Expr.Literal falseLiteral = new Expr.Literal(new Token(FALSE, startToken).setValue(false));

    // For each parameter we now either load argument from Object[]/Map or run the initialiser
    // and store value into a local variable.
    for (int i = 0; i < paramCount; i++) {
      Expr.VarDecl       param          = funDecl.parameters.get(i).declExpr;
      final Expr.Literal paramNameIdent = strLiteral.apply(param.name.getStringValue());
      // If we don't have an initialiser then we have mandatory arg so throw error if using named args
      // and arg not present. (If using Object[] then we have already checked for mandatory arg count
      // so no need to recheck each time.)
      Expr initialiser;
      if (param.initialiser == null) {
        param.location.setGenerateLineNumber(false);
        Expr getOrThrow = new Expr.InvokeUtility(startToken, RuntimeUtils.class, RuntimeUtils.REMOVE_OR_THROW,
                                                 Utils.listOf(Map.class, String.class, boolean.class, String.class, int.class),
                                                 Utils.listOf(mapCopyIdent, paramNameIdent,
                                                         falseLiteral,
                                                         sourceIdent, offsetIdent));
        // :   def <param_i> = _$isObjArr ? _$argArr[i] : RuntimeUtils.removeOrThrow(_$mapCopy, param.name, source, offset)
        initialiser = new Expr.Ternary(isObjArrIdent, new Token(QUESTION, param.name),
                                       new Expr.ArrayGet(startToken, argsIdent, intLiteral.apply(i)),
                                       new Token(COLON, param.name),
                                       getOrThrow);
      }
      else {
        // We have a parameter with an initialiser so load value from Object[]/Map if present otherwise
        // use the initialiser
        // :   def <param_i> = !_$isObjArr && _$mapCopy.containsKey(param.name)
        // :                          ? _$mapCopy.remove(param.name)
        // :                          : _$isObjArr && i < _$argCount ? _$argArr[i]
        // :                                                         : param.initialiser
        initialiser = new Expr.Ternary(new Expr.Binary(new Expr.PrefixUnary(new Token(BANG, param.name), isObjArrIdent),
                                                       new Token(AMPERSAND_AMPERSAND, param.name),
                                                       new Expr.InvokeUtility(param.name, Map.class, "containsKey",
                                                                              Utils.listOf(Object.class), Utils.listOf(mapCopyIdent, paramNameIdent))),
                                       new Token(QUESTION, param.name),
                                       new Expr.InvokeUtility(param.name, Map.class, "remove",
                                                              Utils.listOf(Object.class), Utils.listOf(mapCopyIdent, paramNameIdent)),
                                       new Token(COLON, param.name),
                                       new Expr.Ternary(new Expr.Binary(isObjArrIdent, new Token(AMPERSAND_AMPERSAND, param.name), new Expr.Binary(intLiteral.apply(i), token.apply(LESS_THAN), argCountIdent)),
                                                        new Token(QUESTION, param.name),
                                                        new Expr.ArrayGet(param.name, argsIdent, intLiteral.apply(i)),
                                                        new Token(COLON, param.name),
                                                        param.initialiser));
      }
      // If parameter is a user instance type then convert initialiser to the right type (by possibly invoking its
      // constructor).
      if (param.type.is(INSTANCE)) {
        initialiser = new Expr.ConvertTo(startToken, param.type, initialiser, sourceIdent, offsetIdent);
      }
      Stmt.VarDecl varDecl = paramVarDecl(wrapperFunDecl, param, initialiser);
      stmtList.add(varDecl);
    }

    // Check that we don't still have parameter values left in our Map if passing by name
    // : if (!_$isObjArr) {
    // :   RuntimeUtils.checkForExtraArgs(_$mapCopy)
    // : }
    final Stmt.ExprStmt checkForExtraArgs = new Stmt.ExprStmt(startToken,
                                                              new Expr.InvokeUtility(startToken,
                                                                           RuntimeUtils.class,
                                                                           "checkForExtraArgs",
                                                                           Utils.listOf(Map.class, boolean.class, String.class, int.class),
                                                                           Utils.listOf(mapCopyIdent,
                                                                                   falseLiteral,
                                                                                   sourceIdent,
                                                                                   offsetIdent)));
    checkForExtraArgs.expr.isResultUsed = false;
    stmtList.add(new Stmt.If(startToken, isObjArrIdent, null, checkForExtraArgs));

    // Add original function as a statement so that it gets resolved as a nested function of wrapper
    Stmt.FunDecl realFunction = new Stmt.FunDecl(startToken, funDecl);
    realFunction.createVar = false;   // When in wrapper don't create a variable for the MethodHandle
    stmtList.add(realFunction);

    // Now invoke the real function (unless we are the init method). For init method we already initialise
    // the fields in the varargs wrapper, so we don't need to invoke the non-wrapper version of the function.
    Stream<Expr> args = funDecl.parameters.stream()
                                          .map(p -> new Expr.LoadParamValue(startToken.newIdent(p.declExpr.name.getStringValue()), p.declExpr));
    if (funDecl.functionDescriptor.needsLocation) {
      args = Stream.concat(Stream.of(sourceIdent, offsetIdent), args);
    }
    stmtList.add(returnStmt(startToken, new Expr.InvokeFunDecl(startToken, funDecl, args.collect(Collectors.toList())),
                            funDecl.returnType));

    wrapperFunDecl.block      = new Stmt.Block(startToken, stmts);

    // Return type must be ANY since if function is invoked by value, caller won't know return type
    wrapperFunDecl.returnType = ANY;
    wrapperFunDecl.varDecl = funDecl.varDecl;
    return wrapperFunDecl;
  }

  /**
   * Create the initialisation method for a user class.
   * The init method supports initialisation with a list of values for the mandatory field values or (via its
   * wrapper) a map of named arg values to be applied to the corresponding fields.
   * The only way to set values for optional fields (those with initialisers) is via the wrapper using named args.
   * This differs from normal function/methods where all parameter values can be supplied via the arg list version
   * of a function.
   */
  private Stmt.FunDecl createInitMethod(Stmt.ClassDecl classDecl) {
    ClassDescriptor classDescriptor = classDecl.classDescriptor;
    Token           token           = classDecl.name;
    List<Stmt.VarDecl> mandatoryParams = classDescriptor.getAllMandatoryFields()
                                                        .entrySet()
                                                        .stream()
                                                        .map(e -> Utils.createParam(token.newIdent(e.getKey()), e.getValue(), null, true, false))
                                                        .collect(Collectors.toList());
    JactlType    classType     = createClass(classDescriptor);
    Token        initNameToken = token.newIdent(Utils.JACTL_INIT);
    Expr.FunDecl initFunc      = Utils.createFunDecl(token, initNameToken, classType.createInstanceType(), mandatoryParams, false, true, false);
    Stmt.Stmts initStmts       = new Stmt.Stmts(classDecl.name);
    initFunc.block             = new Stmt.Block(token, initStmts);

    // Change name of params to avoid clashing with field names. This allows us to reuse initialiser expressions
    // in the init and init wrapper methods. Otherwise, references to field names in initialisers could resolve to
    // parameter in this method and to field in wrapper method and during resolution one would overwrite the other.
    // Note that we do this after creating our FunDecl in order that the FunctionDescriptor for the init method
    // uses names that match the field names (for error reporting purposes).
    Function<String,String> paramName = name -> JACTL_PREFIX + "p" + name;
    mandatoryParams.forEach(p -> {
      String pname = paramName.apply(p.name.getStringValue());
      p.name = p.name.newIdent(pname);
      p.declExpr.name = p.name;
    });
    initStmts.stmts.addAll(mandatoryParams);

    // Init method supports invocation via:
    //   - explicit params for the mandatory fields,
    //   - List of mandatory fields (via wrapper)
    //   - Map of any fields (via wrapper)
    // Standard invocation with mandatory params will invoke super.init() with mandatory params for base class and
    // assign mandatory values for child class and then run all initialisers for optional fields.
    if (classDescriptor.getBaseClass() != null) {
      // Invoke super.init() if we have a base class
      Set<String> baseMandatoryFields = classDescriptor.getBaseClass().getAllMandatoryFields().keySet();
      Expr.InvokeInit invokeSuperInit = new Expr.InvokeInit(token, true, classDescriptor.getBaseClass(),
                                                            baseMandatoryFields.stream()
                                                                       .map(f -> new Expr.Identifier(token.newIdent(paramName.apply(f))))
                                                                       .collect(Collectors.toList()));
      invokeSuperInit.isResultUsed = false;
      initStmts.stmts.add(new Stmt.ExprStmt(token, invokeSuperInit));
    }

    // Assign values for all fields. Mandatory fields get values from corresponding parameter and optional fields
    // are assigned from their initialiser.
    classDecl.fieldVars.entrySet().stream().filter(e -> !e.getValue().isConstVar).forEach(entry -> {
      Expr.VarDecl varDecl = entry.getValue();
      Token fieldToken     = varDecl.name;
      Token paramToken     = fieldToken.newIdent(paramName.apply(fieldToken.getStringValue()));
      Expr value = varDecl.initialiser == null ? new Expr.Identifier(paramToken)
                                               : varDecl.initialiser;
      Expr.FieldAssign assign = new Expr.FieldAssign(new Expr.Identifier(fieldToken.newIdent(Utils.THIS_VAR)),
                                                     new Token(DOT, fieldToken),
                                                     new Expr.Literal(fieldToken),
                                                     new Token(EQUAL, fieldToken),
                                                     value);
      assign.isResultUsed = false;
      initStmts.stmts.add(new Stmt.ExprStmt(fieldToken, assign));
    });
    // Finally, return "this"
    initStmts.stmts.add(new Stmt.Return(token,
                                        new Expr.Return(token,
                                                        new Expr.Identifier(token.newIdent(Utils.THIS_VAR)),
                                                        classType.createInstanceType())));

    initFunc.classDecl = classDecl;
    Stmt.FunDecl initMethod = new Stmt.FunDecl(initNameToken, initFunc);
    Utils.createVariableForFunction(initMethod);
    return initMethod;
  }

  private Expr.FunDecl createInitWrapper(Expr.FunDecl initMethod) {
    // Wrapper will be a non-standard wrapper in that it will only support named args invocation. It will support
    // values for all fields, both mandatory and optional. It will first invoke base class init wrapper. We need to
    // tell base class wrapper to not check for additional arguments (since they could be for us). When we have
    // named args we always creat a copy of the map so that we can remove args as they are processed and thus check
    // for any additional args at the end. So we will pass this map to our base class init but the rule is that you
    // only check for additional args if you are the one that copied the named args. Additionally, we know to copy
    // the named args if the map type is not NamedArgsMapCopy which is the marker type we used when we copy the map.
    // NOTE: we also support invocation with null for the Object[] args value which is used when auto-creating
    // fields in lvalues like "x.y.z = a". If the Object[] is null we want to invoke the normal init method with
    // no args. If there are mandatory fields then we generate an error.
    Stmt.ClassDecl classDecl = initMethod.classDecl;
    JactlType      classType = JactlType.createClass(classDecl.classDescriptor);
    String    sourceName = Utils.SOURCE_VAR_NAME;
    int    SOURCE_SLOT    = 2;
    String offsetName     = Utils.OFFSET_VAR_NAME;
    int    OFFSET_SLOT    = 3;
    String objectArrName  = JACTL_PREFIX + "objArr";             // Wrapper funcs always have Object[] parameter type.
    Token  classToken     = classDecl.name;
    Token  sourceToken    = classToken.newIdent(sourceName);
    Token  offsetToken    = classToken.newIdent(offsetName);
    Token        objectArrToken = classToken.newIdent(objectArrName);
    Stmt.VarDecl sourceParam    = Utils.createParam(sourceToken, STRING, null, true, false);
    Stmt.VarDecl offsetParam    = Utils.createParam(offsetToken, INT, null, true, false);
    Stmt.VarDecl objectArrParam = Utils.createParam(objectArrToken, OBJECT_ARR, null, true, false);
    Expr.FunDecl initWrapper    = Utils.createFunDecl(classToken, classToken.newIdent(Utils.JACTL_INIT_WRAPPER),
                                                      ANY,   /* wrappers always return Object */
                                                      Utils.listOf(sourceParam,offsetParam,objectArrParam),
                                                      false, true, false);
    initWrapper.functionDescriptor.isWrapper = true;
    initWrapper.isWrapper     = true;
    Stmt.Stmts wrapperSmts    = new Stmt.Stmts(classToken);
    initWrapper.block         = new Stmt.Block(classToken, wrapperSmts);
    wrapperSmts.stmts.addAll(Utils.listOf(objectArrParam, sourceParam, offsetParam));

    // If null value for args or empty array, and we have no mandatory fields then invoke normal init method.
    // Otherwise, generate error.
    Expr.Literal zero            = new Expr.Literal(new Token(INTEGER_CONST, classToken).setValue(0));
    boolean      mandatoryFields = initMethod.functionDescriptor.mandatoryArgCount > 0;
    Stmt.If ifNullArgArr = new Stmt.If(classToken,
                                       new Expr.Binary(new Expr.Binary(new Expr.Identifier(objectArrToken),
                                                                   new Token(EQUAL_EQUAL, classToken),
                                                                   new Expr.Literal(new Token(NULL, classToken).setValue(null))),
                                                   new Token(PIPE_PIPE, classToken),
                                                   new Expr.Binary(new Expr.ArrayLength(classToken, new Expr.Identifier(objectArrToken)),
                                                                   new Token(EQUAL_EQUAL, classToken), zero)),
                                   mandatoryFields ? new Stmt.ThrowError(classToken,
                                                                         new Expr.Identifier(sourceToken),
                                                                         new Expr.Identifier(offsetToken),
                                                                         "Cannot auto-create instance of type " + classType + " as there are mandatory fields")
                                                   : returnStmt(classToken, new Expr.InvokeFunDecl(classToken, initMethod, Utils.listOf()), initMethod.returnType),
                                       null);
    wrapperSmts.stmts.add(ifNullArgArr);

    Expr.ArrayGet   arg0                = new Expr.ArrayGet(classToken, new Expr.Identifier(objectArrToken), zero);
    Expr.InstanceOf instanceOfNamedArgs = new Expr.InstanceOf(classToken, arg0, Type.getInternalName(NamedArgsMapCopy.class));

    String          argMapName  = JACTL_PREFIX + "argMap";
    Expr.Identifier argMapIdent = new Expr.Identifier(classToken.newIdent(argMapName));
    Expr.Ternary initialiser           = new Expr.Ternary(instanceOfNamedArgs,
                                                          new Token(QUESTION, classToken),
                                                          new Expr.CheckCast(classToken, arg0, MAP),
                                                          new Token(COLON, classToken),
                                                          new Expr.InvokeUtility(classToken, RuntimeUtils.class, "copyNamedArgs", Utils.listOf(Object.class), Utils.listOf(arg0)));
    wrapperSmts.stmts.add(createVarDecl(classToken, initWrapper, argMapName, MAP, initialiser));

    // Invoke base class init wrapper first
    ClassDescriptor classDescriptor = classDecl.classDescriptor;
    if (classDescriptor.getBaseClass() != null) {
      // Invoke super._$j$init$$w() if we have a base class
      Expr.MethodCall invokeSuperInitWrapper = new Expr.MethodCall(classToken,
                                                                   new Expr.Identifier(classToken.newIdent(Utils.SUPER_VAR)),
                                                                   new Token(DOT, classToken),
                                                                   Utils.JACTL_INIT,
                                                                   new LocalLocation(SOURCE_SLOT, OFFSET_SLOT),
                                                                   null,
                                                                   Utils.listOf(argMapIdent));
      invokeSuperInitWrapper.isResultUsed              = false;
      invokeSuperInitWrapper.validateArgsAtCompileTime = false;
      wrapperSmts.stmts.add(new Stmt.ExprStmt(classToken, invokeSuperInitWrapper));
    }

    // Assign value to each field from map or from initialiser
    Expr.Literal trueLiteral = new Expr.Literal(new Token(TRUE, classToken).setValue(true));
    classDecl.fieldVars.entrySet().stream().filter(e -> !e.getValue().isConstVar).forEach(entry -> {
      Expr.VarDecl varDecl          = entry.getValue();
      Token        fieldToken       = varDecl.name;
      Expr.Literal fieldNameLiteral = new Expr.Literal(new Token(STRING_CONST, fieldToken).setValue(fieldToken.getStringValue()));
      Expr value = varDecl.initialiser == null ? new Expr.InvokeUtility(fieldToken, RuntimeUtils.class, RuntimeUtils.REMOVE_OR_THROW,
                                                                        Utils.listOf(Map.class, String.class, boolean.class, String.class, int.class),
                                                                        Utils.listOf(argMapIdent, fieldNameLiteral,
                                                                               trueLiteral, new Expr.Identifier(sourceToken), new Expr.Identifier(offsetToken)))
                                               : new Expr.Ternary(new Expr.InvokeUtility(fieldToken, Map.class, "containsKey",
                                                                                        Utils.listOf(Object.class), Utils.listOf(argMapIdent, fieldNameLiteral)),
                                                                 new Token(QUESTION, fieldToken),
                                                                 new Expr.Cast(fieldToken, varDecl.type,
                                                                               new Expr.InvokeUtility(fieldToken, Map.class, "remove",
                                                                                                      Utils.listOf(Object.class), Utils.listOf(argMapIdent, fieldNameLiteral))),
                                                                 new Token(COLON, fieldToken),
                                                                 new Expr.Cast(fieldToken, varDecl.type, varDecl.initialiser));
      Expr.FieldAssign assign = new Expr.FieldAssign(new Expr.Identifier(fieldToken.newIdent(Utils.THIS_VAR)),
                                                     new Token(DOT, fieldToken),
                                                     new Expr.Literal(fieldToken),
                                                     new Token(EQUAL, fieldToken),
                                                     value);

      assign.isResultUsed = false;
      wrapperSmts.stmts.add(new Stmt.ExprStmt(fieldToken, assign));
    });

    // If we created arg map copy (arg0 isn't NamedArgsMapCopy) then check that there are no additional arg values left in named args map
    Expr.InvokeUtility checkForExtraArgs = new Expr.InvokeUtility(classToken, RuntimeUtils.class, "checkForExtraArgs",
                                                                  Utils.listOf(Map.class, boolean.class, String.class, int.class),
                                                                  Utils.listOf(argMapIdent, trueLiteral, new Expr.Identifier(sourceToken),
                                                           new Expr.Identifier(offsetToken)));
    checkForExtraArgs.isResultUsed = false;
    Stmt.If ifStmt = new Stmt.If(classToken,
                                 new Expr.PrefixUnary(new Token(BANG, classToken), instanceOfNamedArgs),
                                 new Stmt.ExprStmt(classToken, checkForExtraArgs),
                                 null);
    wrapperSmts.stmts.add(ifStmt);

    // Add initMethod as statement in wrapper, so it will get resolved when wrapper is resolved
    Stmt.FunDecl realFunction = new Stmt.FunDecl(classToken, initMethod);
    realFunction.createVar = false;   // When in wrapper don't create a variable for the MethodHandle
    wrapperSmts.stmts.add(realFunction);

    // Finally, return "this"
    wrapperSmts.stmts.add(new Stmt.Return(classToken,
                                          new Expr.Return(classToken,
                                                          new Expr.Identifier(classToken.newIdent(Utils.THIS_VAR)),
                                                          classType.createInstanceType())));
    return initWrapper;
  }

  public Stmt.VarDecl createVarDecl(Expr.VarDecl varDecl) {
    return Utils.createVarDecl(currentFunction(), varDecl);
  }

  public Stmt.VarDecl createVarDecl(Token token, String name, JactlType type, Expr init) {
    return createVarDecl(token, currentFunction(), name, type, init);
  }

  private static Stmt.VarDecl createVarDecl(Token token, Expr.FunDecl ownerFunDecl, String name, JactlType type, Expr init) {
    return Utils.createVarDecl(ownerFunDecl, token.newIdent(name), type, init);
  }

  /**
   * Create a varDecl in varargs wrapper for a given parameter. The variable will be initialised with
   * passed in arg or via initialiser if no arg passed in. We move initialiser from the param to the var.
   */
  private Stmt.VarDecl paramVarDecl(Expr.FunDecl ownerFunDecl, Expr.VarDecl param, Expr init) {
    Stmt.VarDecl varDecl = Utils.createVarDecl(ownerFunDecl, param.name, param.type, init);
    varDecl.declExpr.paramVarDecl    = param;
    Expr.Noop noop = new Expr.Noop(param.name);
    noop.originalExpr = param.initialiser;
    param.initialiser                = noop;  // Use Noop rather than null so mandatory arg counting works
    param.initialiser.type           = param.type;
    varDecl.declExpr.isExplicitParam = true;
    varDecl.declExpr.type            = param.type;
    varDecl.declExpr.isField         = ownerFunDecl.isInitMethod();
    return varDecl;
  }

  private Stmt.Return returnStmt(Token token, JactlType type) {
    return new Stmt.Return(token, new Expr.Return(token, literalDefaultValue(token, type), type));
  }

  private Stmt.Return returnStmt(Token token, Expr expr, JactlType type) {
    if (expr instanceof Expr.Return) {
      // Don't need to create yet another Expr.Return if we already have one
      return new Stmt.Return(token, (Expr.Return)expr);
    }
    return new Stmt.Return(token, new Expr.Return(token, expr, type));
  }

  static Expr literalDefaultValue(Token location, JactlType type) {
    Expr expr = _literalDefaultValue(location, type);
    expr.type = type;
    return expr;
  }

  static Expr _literalDefaultValue(Token location, JactlType type) {
    switch (type.getType()) {
      case BOOLEAN: return new Expr.Literal(new Token(FALSE, location).setValue(false));
      case BYTE:    return new Expr.Literal(new Token(BYTE_CONST, location).setValue(0));
      case INT:     return new Expr.Literal(new Token(INTEGER_CONST, location).setValue(0));
      case LONG:    return new Expr.Literal(new Token(LONG_CONST, location).setValue(0));
      case DOUBLE:  return new Expr.Literal(new Token(DOUBLE_CONST, location).setValue(0));
      case DECIMAL: return new Expr.Literal(new Token(DECIMAL_CONST, location).setValue(BigDecimal.ZERO));
      case STRING:  return new Expr.Literal(new Token(STRING_CONST, location).setValue(""));
      case MAP:     return new Expr.MapLiteral(location);
      case LIST:    return new Expr.ListLiteral(location);

      case INSTANCE:
      case ANY:
        return new Expr.Literal(new Token(NULL, location).setValue(null));

      default:      throw new IllegalStateException("Internal error: unexpected type " + type);
    }
  }

  private Deque<Expr.FunDecl> getFunctions() {
    Stmt.ClassDecl classDecl = currentClass();
    if (classDecl == null) {
      return null;
    }
    return classDecl.nestedFunctions;
  }

  private Expr.FunDecl currentFunction() {
    Deque<Expr.FunDecl> functions = getFunctions();
    if (functions == null) {
      return null;
    }
    return functions.peek();
  }

  private static Expr.VarDecl createGlobalVarDecl(String name, JactlType type) {
    Expr.VarDecl varDecl = new Expr.VarDecl(new Token("",0).setType(IDENTIFIER).setValue(name),
                                            null, null);
    varDecl.type = type;
    varDecl.isGlobal = true;
    return varDecl;
  }

  private Stmt.FunDecl createFromJsonFunc(Stmt.ClassDecl classDecl) {
    Token classToken       = classDecl.name;
    JactlType instanceType = JactlType.createInstanceType(Utils.listOf(new Expr.Identifier(classToken)));
    Token        paramName = classToken.newIdent("json");
    Stmt.VarDecl param     = Utils.createParam(paramName, JactlType.STRING);
    Expr.FunDecl funDecl   = Utils.createFunDecl(classToken, classToken.newIdent(Utils.JACTL_FROM_JSON), instanceType, Utils.listOf(param), true, false, true);
    Stmt.FunDecl funDeclStmt = new Stmt.FunDecl(classToken, funDecl);
    Stmt.Stmts   stmts       = new Stmt.Stmts(classToken);
    stmts.stmts.add(param);
    Token objIdent         = classToken.newIdent("obj");
    stmts.stmts.add(Utils.createVarDecl(funDecl, objIdent, instanceType, new Expr.InvokeNew(classToken, instanceType)));
    Token retValIdent      = classToken.newIdent("retVal");
    JactlType flagsType    = JactlType.LONG_ARR;

    // Decode JSON and store in "retVal"
    stmts.stmts.add(Utils.createVarDecl(funDecl, retValIdent, ANY, new Expr.InvokeUtility(classToken, JsonDecoder.class, "decodeJactlObj",
                                                                                          Utils.listOf(String.class, String.class, int.class, JactlObject.class),
                                                                                          Utils.listOf(new Expr.Identifier(paramName),
                                                                                                  new Expr.SpecialVar(classToken.newIdent(Utils.SOURCE_VAR_NAME)),
                                                                                                  new Expr.SpecialVar(classToken.newIdent(Utils.OFFSET_VAR_NAME)),
                                                                                                  new Expr.Identifier(objIdent)))));

    // If retVal is not null then invoke initMissing() and return value, otherwise return null
    stmts.stmts.add(new Stmt.Return(classToken,
                                    new Expr.Return(classToken,
                                                    new Expr.Ternary(new Expr.Identifier(retValIdent),
                                                                     new Token(QUESTION, classToken),
                                                                     new Expr.MethodCall(classToken,
                                                                                         new Expr.Identifier(objIdent),
                                                                                         new Token(DOT, classToken),
                                                                                         Utils.JACTL_INIT_MISSING,
                                                                                         classToken,
                                                                                         null,
                                                                                         Utils.listOf(new Expr.CheckCast(classToken, new Expr.Identifier(retValIdent), flagsType))),
                                                                     new Token(COLON, classToken),
                                                                     new Expr.Literal(new Token(NULL, classToken))),
                                                    instanceType)));
    Stmt.Block block = new Stmt.Block(classToken, stmts);
    funDecl.block = block;
    funDecl.functionDescriptor.needsLocation = true;
    Utils.createVariableForFunction(funDeclStmt);
    return funDeclStmt;
  }

  private Stmt.FunDecl createMissingFieldsFunc(Stmt.ClassDecl classDecl) {
    final String FLAGS     = "flags";
    Token classToken       = classDecl.name;
    JactlType flagsType    = JactlType.LONG_ARR;
    JactlType    instanceType = JactlType.createInstanceType(Utils.listOf(new Expr.Identifier(classToken)));
    Token        flagsParam   = classToken.newIdent(FLAGS);
    Stmt.VarDecl flagsVarDecl = Utils.createParam(flagsParam, flagsType);
    Expr.FunDecl funDecl      = Utils.createFunDecl(classToken, classToken.newIdent(Utils.JACTL_INIT_MISSING), instanceType, Utils.listOf(flagsVarDecl), false, false, false);
    Stmt.FunDecl funDeclStmt  = new Stmt.FunDecl(classToken, funDecl);
    Stmt.Stmts   stmts       = new Stmt.Stmts(classToken);
    stmts.stmts.add(flagsVarDecl);
    Token     thisIdent    = classToken.newIdent(Utils.THIS_VAR);

    // Iterate over flags returned from decodeJactlObj and work out which optional fields have not been
    // set and invoke their initialisers

    // First invoke base class version of the method (if it exists)
    int baseFieldsCount = 0;
    if (classDecl.baseClass != null && classDecl.baseClass.getClassDescriptor() != null && classDecl.classDescriptor.getBaseClass() != null) {
      Expr.MethodCall superMethodCall = new Expr.MethodCall(classToken, new Expr.Identifier(classToken.newIdent(Utils.SUPER_VAR)),
                                                            new Token(DOT, classToken), Utils.JACTL_INIT_MISSING, classToken,
                                                            null, Utils.listOf(new Expr.Identifier(classToken.newIdent(FLAGS))));
      superMethodCall.isResultUsed = false;
      stmts.stmts.add(new Stmt.ExprStmt(classToken, superMethodCall));
      baseFieldsCount = classDecl.classDescriptor.getBaseClass().getFields().size();
    }

    // Only deal with fields in this class since base class invocation will take care of base class fields
    List<Map.Entry<String, JactlType>> fields = classDecl.classDescriptor.getFields();
    for (int i = 0; i < fields.size(); i++) {
      int          fieldIdx = i + baseFieldsCount;
      Expr.VarDecl field    = classDecl.fieldVars.get(fields.get(i).getKey());
      if (field.initialiser == null || Utils.isDefaultValue(field)) {
        // Not optional so nothing to do
        continue;
      }
      long flag = 1L << (fieldIdx % 64);
      Expr.ArrayGet flags = new Expr.ArrayGet(classToken,
                                              new Expr.Identifier(classToken.newIdent(FLAGS)),
                                              new Expr.Literal(new Token(INTEGER_CONST, classToken).setValue(fieldIdx / 64)));
      Expr.FieldAssign assignField = new Expr.FieldAssign(new Expr.Identifier(thisIdent),
                                                          new Token(DOT, classToken),
                                                          new Expr.Literal(classToken.newLiteral(field.name.getStringValue())),
                                                          new Token(EQUAL, classToken),
                                                          field.initialiser);
      assignField.isResultUsed = false;
      stmts.stmts.add(new Stmt.If(classToken,
                                  new Expr.Binary(new Expr.Literal(new Token(LONG_CONST, classToken).setValue(flag)),
                                                  new Token(AMPERSAND, classToken),
                                                  flags),
                                  new Stmt.ExprStmt(classToken, assignField),
                                  null));
    }
    stmts.stmts.add(new Stmt.Return(classToken, new Expr.Return(classToken, new Expr.Identifier(thisIdent), instanceType)));
    Stmt.Block block = new Stmt.Block(classToken, stmts);
    funDecl.block = block;
    Utils.createVariableForFunction(funDeclStmt);
    return funDeclStmt;
  }

  private void classNameValidation(Expr expr) {
    if (expr == null || expr.type == null || expr instanceof Expr.Noop) {
      return;
    }
    if (!(expr instanceof Expr.TypeExpr) && !(expr instanceof Expr.InvokeInit) && !(expr instanceof Expr.ClassPath) && expr.type.is(CLASS)) {
      error("Class name not allowed here", expr.location);
    }
  }
}
