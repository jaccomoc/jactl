/*
 * Copyright © 2022-2026 James Crawford
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
import io.jactl.JactlContext;
import io.jactl.JactlType;
import io.jactl.Utils;
import io.jactl.compiler.JactlClassLoader;
import io.jactl.compiler.JactlClassWriter;
import org.objectweb.asm.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

public class JactlClass {

  private String       jactlClassName;
  private String       baseClassName;
  private String       jactlPackage;                       // name of Jactl package for class
  private Class        javaClass;
  private boolean      autoImport;                         // whether scripts automatically import this class by default
  private int          debugLevel = Integer.getInteger("jactl.debug.leval", 0);
  private JactlContext jactlContext = null;
  private BiConsumer<Checkpointer,Object> checkpoint  = null;
  private Function<Restorer,Object>       restore     = null;
  public BiConsumer<JsonEncoder,Object>   jsonEncoder = null;
  public Function<JsonDecoder,Object>     jsonDecoder = null;
  public String                           helperClassName;

  private Map<Class,Class> mappedTypes = new HashMap() {{
    // Always map CharSequence to String until we add proper support for CharSequence
    put(CharSequence.class, String.class);
  }};
  
  private List<RegisteredClassMethod>    methods     = new ArrayList<>();
  
  ///////////////////////////////////////////

  /**
   * A new Jactl class with given package qualified name.
   * @param jactlClass  the name of the Jactl class, including package name (e.g. x.y.SomeClass)
   */
  public JactlClass(String jactlClass) {
    jactlClassName = jactlClass.replaceAll("^.*\\.", "");
    jactlPackage   = jactlClass.replaceAll("\\.[^.]*$", "");
  }
  
  public JactlClass(String jactlClassName, JactlContext context) {
    this(jactlClassName);
    this.jactlContext = context;
  }

  /**
   * Specify the Java class that provides the implementation for this Jactl class.
   * @param javaClass the Java class
   * @return the JactlClass instance
   */
  public JactlClass javaClass(Class<?> javaClass) {
    this.javaClass = javaClass;
    return this;
  }

  /**
   * Specify the name of Jactl class that is base class for this class (if one exists).
   * @param jactlClass  the name of the Jactl class that this class extends
   * @return the JactlClass instance
   */
  public JactlClass baseClass(String jactlClass) {
    this.baseClassName = jactlClass;
    return this;
  }
  
  /**
   * Whether class should be automatically imported when compiling Jactl classes/scripts
   * @param flag  true if class is auto-imported
   * @return the JactlClass instance
   */
  public JactlClass autoImport(boolean flag) {
    autoImport = flag;
    return this;
  }

  /**
   * <p>
   *   Map parameter and return types so that references to base-classes/interfaces that we don't want to
   *   include can be mapped to child classes that we will use instead.
   * </p><p>
   *   For example, if we are using java.time.LocalDate as a Jactl class, there are methods that refer to
   *   the java.time.chrono.ChronoLocalDate interface. Since we know in this case that all instances will
   *   actually be LocalDate objects we map occurrences of this to LocalDate.
   * </p>
   * <p>
   *   NOTE: we automatically map occurrences of CharSequence to String so this mapping is not required to
   *   be explicitly configured.
   * </p>
   * @param srcType the class to be mapped
   * @param dstType the class to map to
   * @return the JactlClass instance
   */
  public JactlClass mapType(Class srcType, Class dstType) {
    mappedTypes.put(srcType, dstType);
    return this;
  }

  /**
   * <p>
   *   Include given Java method as a Jactl method of this class. Since Jactl does not support overloaded methods of
   *   the same name, if you want to include two methods of the same name, you will need to provide different Jactl
   *   names for them.
   * </p>
   * <p>
   *   The last parameter to this method is a list of String/Class pairs giving the parameter name (for named
   *   parameter passing) and the Java class of the parameter. NOTE: the Java class should be the actual parameter
   *   type (not the mapped type) since we use this for looking up the method using reflection.
   * </p>
   * <p>
   *   NOTE: if the method throws exception that are not declared with "throws" in the method declaration (i.e.
   *   unchecked exceptions), then you will need to use the {@link JactlClass#methodCanThrow(String, String, Object...)} 
   *   method instead to add the Java method.
   * </p>
   * @param jactlName      the name to use in Jactl
   * @param javaName       the Java name
   * @param namesAndTypes  a list of "name",Class pairs for the parameters
   * @return the JactlClass instance
   * @throws NoSuchMethodException if method cannot be found
   */
  public JactlClass method(String jactlName, String javaName, Object... namesAndTypes) throws NoSuchMethodException {
    return _method(false, jactlName, javaName, namesAndTypes);
  }

  /**
   * <p>
   *   Include given Java method that can throw an exception as a Jactl method of this class.
   *   By default {@link JactlClass#method(String, String, Object...)} checks if there any checked
   *   exceptions thrown and will automatically build a wrapping method to catch and rethrow these
   *   exceptions with the Jactl specific location of the error. If the method throws unchecked
   *   exceptions then there is no way for Jactl to detect this so use this methodCanThrow() method
   *   to specify that the Java method can throw exceptions that need to be intercepted and rethrown.
   * </p>
   * <p>
   *   Since Jactl does not support overloaded methods of the same name, if you want to include
   *   two methods of the same name, you will need to provide different Jactl names for them.
   * </p>
   * <p>
   *   The last parameter to this method is a list of String/Class pairs giving the parameter name (for named
   *   parameter passing) and the Java class of the parameter. NOTE: the Java class should be the actual parameter
   *   type (not the mapped type) since we use this for looking up the method using reflection.
   * </p>
   * @param jactlName      the name to use in Jactl
   * @param javaName       the Java name
   * @param namesAndTypes  a list of "name",Class pairs for the parameters
   * @return the JactlClass instance
   * @throws NoSuchMethodException if method cannot be found
   */
  public JactlClass methodCanThrow(String jactlName, String javaName, Object... namesAndTypes) throws NoSuchMethodException {
    return _method(true, jactlName, javaName, namesAndTypes);
  }

  /**
   * A BiConsumer&lt;Checkpointer&gt; that can checkpoint the state of an instance of this class using the supplied
   * Checkpointer.
   * @param checkpoint      a BiConsumer&lt;Checkpointer&gt; the will checkpoint the state of an instance 
   * @return the JactlClass instance
   */
  public JactlClass checkpoint(BiConsumer<Checkpointer,Object> checkpoint) {
    this.checkpoint = checkpoint;
    return this;
  }

  /**
   * A function that will read state from a supplied Restorer and use it to reconstruct an instance of this
   * class from the previously checkpointed state.
   * @param restore the Function object
   * @return the JactlClass instance
   */
  public JactlClass restore(Function<Restorer,Object> restore) {
    this.restore = restore;
    return this;
  }

  public JactlClass toJson(BiConsumer<JsonEncoder,Object> encoder) {
    this.jsonEncoder = encoder;
    return this;
  }

  public JactlClass fromJson(Function<JsonDecoder,Object> decoder) {
    this.jsonDecoder = dec -> {
      try {
        return decoder.apply(dec);
      }
      catch (RuntimeError e) {
        throw e;
      }
      catch (RuntimeException e) {
        dec.error(e);
        return null;
      }
    };
    return this;
  }
  
  /**
   * If level is more than 0 then we dump the compiled helper class and run the class checker
   * over it.
   * @param debugLevel the debug level
   * @return the JactlClass instance
   */
  public JactlClass debugLevel(int debugLevel) {
    this.debugLevel = debugLevel;
    return this;
  }

  /**
   * Register the class with the Jactl ecosystem.
   * @return the JactlClass instance
   */
  public JactlType register() {
    if (jsonEncoder != null && jsonDecoder == null || jsonDecoder != null && jsonEncoder == null) {
      throw new IllegalStateException((jsonEncoder != null ? "toJson()" : "fromJson()") + " declared but " + (jsonEncoder != null ? "fromJson()" : "toJson()") + " not declared");
    }

    // We need to build a ClassDescriptor and register it. We do this by using reflection to find all the
    // methods and add them to the ClassDescriptor.
    String               jactlClass      = Utils.pkgPathOf(jactlPackage, jactlClassName);
    JactlClassDescriptor classDescriptor = getRegisteredClasses().getOrCreateClassDescriptor(jactlClass, javaClass.getName());
    if (baseClassName != null) {
      JactlClassDescriptor baseClassDescriptor = getRegisteredClasses().getClassDescriptor(baseClassName);
      if (baseClassDescriptor == null) {
        throw new IllegalStateException("Unknown Jactl class " + baseClassName + " for base class");
      }
      classDescriptor.baseClass = baseClassDescriptor.getClassType();
    }
    getRegisteredClasses().registerClassByJavaName(Type.getInternalName(javaClass), javaClass);
    JactlType       classType       = JactlType.createClass(classDescriptor);
    helperClassName = Utils.pkgPathOf(Utils.JACTL_PKG, jactlPackage, Utils.JACTL_PREFIX + jactlClassName + "Helper");
    Class helperClass = compileWrapperHandlesClass(helperClassName);
    
    if (jsonDecoder != null) {
      // Note that compileWrapperHandleClass has already created the method and implementation for fromJson
      try {
        _method(false, helperClass, Utils.JACTL_FROM_JSON, Utils.JACTL_FROM_JSON, true, "json", String.class);
      }
      catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      }
    }

    methods.forEach(m -> {
      String wrapperHandleField = Utils.staticHandleName(m.name);
      FunctionDescriptor funcDesc = m;
      // If method can throw then we use the wrapper method in the helper class to invoke instead
      if (m.canThrow) {
        JactlFunction m2 = Jactl.method(classType)
                                .name(m.name);
        m.paramNames.forEach(m2::param);
        m2._impl(helperClass, m.name, wrapperHandleField)
          .isStatic(Modifier.isStatic(m.method.getModifiers()));
        m2.init();
        funcDesc = m2;
      }
      else {
        m.type(classType.createInstanceType());
        m.wrapperHandle(helperClassName, wrapperHandleField);
        m.init(mappedTypes);
      }
      classDescriptor.addMethod(funcDesc.name, funcDesc);
      getFunctions().registerFunction(funcDesc);
    });
    if (autoImport) {
      getRegisteredClasses().addAutoImported(jactlClassName, classDescriptor);
    }
    if (checkpoint != null) {
      if (restore == null) {
        throw new IllegalStateException("checkpoint() specified without a matching call to restore() for " + jactlClassName);
      }
      getRegisteredClasses().registerCheckpointer(javaClass, checkpoint);
    }
    if (restore != null) {
      getRegisteredClasses().registerRestorer(javaClass, restore);
    }
    getRegisteredClasses().registerJactlClass(classDescriptor.getJavaPackagedName(), this);
    return classType;
  }

  //////////////////////////////////////////////////////////////////////

  private JactlClass _method(boolean canThrow, String jactlName, String javaName, Object... namesAndTypes) throws NoSuchMethodException {
    return _method(canThrow, javaClass, jactlName, javaName, false, namesAndTypes);
  }
  
  private JactlClass _method(boolean canThrow, Class<?> javaClass, String jactlName, String javaName, boolean needsLocation, Object... namesAndTypes) throws NoSuchMethodException {
    RegisteredClassMethod jactlMethod = new RegisteredClassMethod(javaClass, jactlContext).name(jactlName);
    List<Class> paramTypes = new ArrayList<>();
    if (needsLocation) {
      paramTypes.add(String.class);
      paramTypes.add(int.class);
    }
    for (int i = 0; i < namesAndTypes.length; i += 2) {
      String paramName;
      if (namesAndTypes[i] instanceof String) {
        paramName = (String) namesAndTypes[i];
      }
      else {
        throw new IllegalArgumentException("Expected String for parameter name not " + namesAndTypes[i].getClass() + " at index " + i + " in list of types and names");
      }
      if (namesAndTypes[i + 1] instanceof Class) {
        paramTypes.add((Class)namesAndTypes[i + 1]);
      }
      else {
        throw new IllegalArgumentException("Expected type of Class not " + namesAndTypes[i + 1].getClass() + " at index " + (i+1) + " in list of types and names");
      }
      jactlMethod.param(paramName);
    }
    if (javaClass != null) {
    jactlMethod.impl(javaClass.getMethod(javaName, paramTypes.toArray(new Class[0])));
    if (canThrow) {
      jactlMethod.canThrow(true);
    }
    }
    jactlMethod.needsLocation = needsLocation;
    methods.add(jactlMethod);
    return this;
  }

  private RegisteredClasses getRegisteredClasses() {
    return jactlContext == null ? RegisteredClasses.INSTANCE : jactlContext.getRegisteredClasses();
  }

  private Functions getFunctions() {
    return jactlContext == null ? Functions.INSTANCE : jactlContext.getFunctions();
  }

  private Class compileWrapperHandlesClass(String helperClassName) {
    JactlClassWriter cw = new JactlClassWriter(Utils.JACTL_PKG.replace('.', '/'), debugLevel);
    ClassVisitor     cv = cw.getClassVisitor();

    String internalHelperClassName = helperClassName.replace('.', '/');
    cv.visit(Utils.JAVA_VERSION, ACC_PUBLIC, internalHelperClassName, null, Utils.OBJECT_INTERNAL, new String[0]);
    cv.visitSource(helperClassName + ".java", null);

    // Add MethodHandle fields
    methods.forEach(m -> {
      FieldVisitor handleVar = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, Utils.staticHandleName(m.name), JactlMethodHandle.TYPE_DESCRIPTOR, null, null);
      handleVar.visitEnd();
      if (m.canThrow) {
        // Synthesise a method that will capture any runtime exceptions and wrap them in a RuntimeError with
        // source/offset location information
        createMethodInvoker(cv, m.name, m.method, mappedTypes.getOrDefault(m.method.getReturnType(),m.method.getReturnType()), Arrays.stream(m.method.getParameterTypes()).map(clss -> mappedTypes.getOrDefault(clss, clss)).collect(Collectors.toList()));
        m.implementingClassName  = internalHelperClassName;
        m.isStaticImplementation = true;
        m.implementingMethod     = m.name;
        m.needsLocation          = true;
      }
    });

    // Create handles for toJson() and fromJson() 
    if (jsonEncoder != null && jsonDecoder != null) {
      // create static handle fromJson$sh that will point to our constructed fromJson method
      FieldVisitor handleVar = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, Utils.staticHandleName(Utils.JACTL_FROM_JSON), JactlMethodHandle.TYPE_DESCRIPTOR, null, null);
      handleVar.visitEnd();

      // create fields for the fromJson BiFunction and the toJson Function objects
      handleVar = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, Utils.JACTL_TO_JSON_HANDLER, Utils.BICONSUMER_DESCRIPTOR, null, null);
      handleVar.visitEnd();
      handleVar = cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, Utils.JACTL_FROM_JSON_HANDLER, Utils.FUNCTION_DESCRIPTOR, null, null);
      handleVar.visitEnd();
      
      // Create the fromJson() method
      MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, Utils.JACTL_FROM_JSON, Type.getMethodDescriptor(Type.getType(javaClass), Utils.STRING_TYPE, Utils.INT_TYPE, Utils.STRING_TYPE), null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, internalHelperClassName, Utils.JACTL_FROM_JSON_HANDLER, Utils.FUNCTION_DESCRIPTOR);
      methodVisitor.visitVarInsn(ALOAD, 2);    // JSON String
      methodVisitor.visitVarInsn(ALOAD, 0);    // source
      methodVisitor.visitVarInsn(ILOAD, 1);    // offset
      methodVisitor.visitMethodInsn(INVOKESTATIC, JsonDecoder.INTERNAL_NAME, "get", Type.getMethodDescriptor(Type.getType(JsonDecoder.class), Utils.STRING_TYPE, Utils.STRING_TYPE, Utils.INT_TYPE), false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ASTORE, 3);   // JsonDecoder
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, Utils.FUNCTION_INTERNAL, "apply", Type.getMethodDescriptor(Utils.OBJECT_TYPE, Utils.OBJECT_TYPE), true);
      methodVisitor.visitTypeInsn(CHECKCAST, Type.getInternalName(javaClass));
      methodVisitor.visitVarInsn(ALOAD, 3);   // JsonDecoder
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, JsonDecoder.INTERNAL_NAME, "close", Type.getMethodDescriptor(Utils.VOID_TYPE), false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    
    cv.visitEnd();
    byte[] bytes = cw.toByteArray();
    Class<?> clss = JactlClassLoader.defineClass(helperClassName, bytes);

    if (jsonEncoder != null && jsonDecoder != null) {
      try {
        Field field = clss.getDeclaredField(Utils.JACTL_TO_JSON_HANDLER);
        field.set(null, jsonEncoder);
        field = clss.getDeclaredField(Utils.JACTL_FROM_JSON_HANDLER);
        field.set(null, jsonDecoder);
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    return clss;
  }

  /**
   * Create a static method in helper class that will invoke the actual method but catch any Exception
   * thrown and rethrow as a RuntimeError with source/offset information.
   * @param cv         the ClassVisitor
   * @param methodName the name of this method (can be different to name of target method do to renaming)
   * @param method     the method to invoke
   * @param mappedReturnType the return type to use (may have been mapped from original)
   * @param mappedParamTypes the parameter types to use for new invoker method (may differ from original due to mappings)
   */
  private void createMethodInvoker(ClassVisitor cv, String methodName, Method method, Class mappedReturnType, List<Class> mappedParamTypes) {
    List<Class> paramTypes = new ArrayList<Class>(mappedParamTypes);
    boolean     isStatic    = Modifier.isStatic(method.getModifiers());
    int i = 0;
    if (!isStatic) {
      paramTypes.add(i++, method.getDeclaringClass());
    }
    paramTypes.add(i++, String.class);  // source
    paramTypes.add(i++, int.class);     // offset
    Type[] pTypes = new Type[paramTypes.size()];
    for (int j = 0; j < paramTypes.size(); j++) {
      pTypes[j] = Type.getType(paramTypes.get(j));
    }
    MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, Type.getMethodDescriptor(Type.getType(mappedReturnType), pTypes) , null, null);
    methodVisitor.visitCode();
    Label label0 = new Label();
    Label label1 = new Label();
    Label label2 = new Label();
    methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
    methodVisitor.visitLabel(label0);
    methodVisitor.visitLineNumber(17, label0);
    int slot = 0;
    if (!isStatic) {
      methodVisitor.visitVarInsn(ALOAD, slot++);        // obj
    }
    slot += 2;                                          // skip source/offset args
    // Other arguments
    for (int p = slot; p < paramTypes.size(); p++) {
      slot += Utils.loadSlot(methodVisitor, slot, JactlContext.typeFromClass(paramTypes.get(p), getRegisteredClasses(), jactlContext));
    }
    if (isStatic) {
      methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(method.getDeclaringClass()), method.getName(), Type.getMethodDescriptor(method), false);
    }
    else {
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(method.getDeclaringClass()), method.getName(), Type.getMethodDescriptor(method), false);
    }
    methodVisitor.visitLabel(label1);
    Utils.emitReturn(methodVisitor, JactlContext.typeFromClass(method.getReturnType(), getRegisteredClasses(), jactlContext));
    methodVisitor.visitLabel(label2);
    int exceptionSlot = slot++;
    methodVisitor.visitVarInsn(ASTORE, exceptionSlot);       // Store caught exception
    Label label3 = new Label();
    methodVisitor.visitLabel(label3);
    methodVisitor.visitTypeInsn(NEW, "io/jactl/runtime/RuntimeError");
    methodVisitor.visitInsn(DUP);
    methodVisitor.visitLdcInsn("");
    methodVisitor.visitVarInsn(ALOAD, isStatic ? 0 : 1);    // source
    methodVisitor.visitVarInsn(ILOAD, isStatic ? 1 : 2);    // offset
    methodVisitor.visitVarInsn(ALOAD, exceptionSlot);
    methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/jactl/runtime/RuntimeError", "<init>", "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/Throwable;)V", false);
    methodVisitor.visitInsn(ATHROW);
    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();
  }
  
  //////////////////////////////

  public static void main(String[] args) throws ClassNotFoundException {
    if (args.length != 2) {
      System.err.println("Usage: java -cp jactl.jar io.jactl.runtime.JactlClass <jactl-class-name> <java-class-name>");
      System.exit(1);
    }

    String jactlClassName = args[0];
    String className = args[1];
    Class clss = JactlClassLoader.forName(className);
    final Set<String> excludedMethods = new HashSet<>(Arrays.asList("compareTo", "readObject", "writeObject", "hashCode", "equals", "readExternal", "writeExternal", "toString"));

    System.out.print("    Jactl.createClass(\"" + jactlClassName + "\")\n" +
                     "         .javaClass(" + className + ".class)\n" +
                     "         .autoImport(true)\n" +
                     "         .mapType(ABC.class, XYZ.class)\n");

    Arrays.stream(clss.getMethods())
          .filter(m -> Modifier.isPublic(m.getModifiers()))
          .filter(m -> !m.isBridge())
          .filter(m -> !excludedMethods.contains(m.getName()))
          .sorted(Comparator.comparing(Method::getName))
          .forEach(m -> {
            AtomicInteger argCount = new AtomicInteger(0);
            System.out.println("         .method(\"" + m.getName() + "\", \"" + m.getName() + "\"" + Arrays.stream(m.getParameterTypes()).map(cls -> ", \"arg" + argCount.incrementAndGet() + "\", " + cls.getSimpleName() + ".class").collect(Collectors.joining()) + ")");
          });

    System.out.println("         .register();\n");
  }
}
