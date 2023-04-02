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

package jacsal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Load ~/.jacsalrc file and process options:
 * <ul>
 *   <li>Add listed JARs to class loader</li>
 *   <li>Instantiate JacsalEnv instance of given subclass type</li>
 *   <li>Invoke registerFunctions() on listed functionClasses</li>
 * </ul>
 */
public class JacsalOptions {
  final static String OPTIONS_FILE       = System.getProperty("user.home") + "/.jacsalrc";
  final static String ENV_CLASS          = "environmentClass";
  final static String FUNC_CLASSES       = "functionClasses";
  final static String REGISTER_FUNCTIONS = "registerFunctions";
  final static String EXTRA_JARS         = "extraJars";

  static class CustomUrlClassLoader extends URLClassLoader {
    CustomUrlClassLoader() { super(new URL[0], CustomUrlClassLoader.class.getClassLoader()); }
    @Override protected void addURL(URL url) {
      super.addURL(url);
    }
  }

  private JacsalEnv env;

  private JacsalOptions(JacsalEnv env) {
    this.env = env;
  }

  public JacsalEnv getEnvironment() {
    return env;
  }

  public static JacsalOptions initOptions() {
    String defaultEnvClass = DefaultEnv.class.getName();
    Map<String,Object> options = new LinkedHashMap<>(Map.of(ENV_CLASS, defaultEnvClass,
                                                            FUNC_CLASSES, new ArrayList(),
                                                            EXTRA_JARS, new ArrayList()));
    if (!new File(OPTIONS_FILE).exists()) {
      return new JacsalOptions(new DefaultEnv());
    }
    try {
      // Run options file as a Jacsal script
      Compiler.run(Files.readString(Path.of(OPTIONS_FILE)), options);

      List<String> extraJars = (List<String>) options.get(EXTRA_JARS);
      ClassLoader classLoader = JacsalOptions.class.getClassLoader();
      if (extraJars.size() > 0) {
        //ExtendableClassLoader extendableClassLoader = new ExtendableClassLoader(ClassLoader.getSystemClassLoader());
        CustomUrlClassLoader extendableClassLoader = new CustomUrlClassLoader();
        for (String jar : extraJars) {
          jar = jar.replaceAll("~", System.getProperty("user.home"));
          //extendableClassLoader.addJar(jar);
          extendableClassLoader.addURL(Path.of(jar).toUri().toURL());
        }
        classLoader = extendableClassLoader;
        Thread.currentThread().setContextClassLoader(classLoader);
      }
      String    envClass = (String)options.get(ENV_CLASS);
      Class<?>  clss     = classLoader.loadClass(envClass);
      JacsalEnv env      = (JacsalEnv) clss.getConstructor().newInstance();
      registerFunctions(classLoader, (List<String>)options.get(FUNC_CLASSES), env);
      return new JacsalOptions(env);
    }
    catch (Throwable e) {
      throw new RuntimeException("Error loading options from ~/.jacsalrc", e);
    }
  }

  private static void registerFunctions(ClassLoader classLoader, List<String> funcClasses, JacsalEnv env) throws Exception {
    for (String className: funcClasses) {
      Class<?> clss              = classLoader.loadClass(className);
      Optional<Method> optMethod = Arrays.stream(clss.getDeclaredMethods())
                                         .filter(m -> m.getName().equals(REGISTER_FUNCTIONS))
                                         .findFirst();
      if (optMethod.isEmpty()) {
        throw new IllegalArgumentException("Could not find method called " + REGISTER_FUNCTIONS + " in class " + className);
      }
      Method method = optMethod.get();
      if (!Modifier.isStatic(method.getModifiers())) {
        throw new IllegalArgumentException("Method " + REGISTER_FUNCTIONS + " in class " + className + " must be static");
      }
      if (method.getParameterTypes().length != 1 || !JacsalEnv.class.isAssignableFrom(method.getParameterTypes()[0])) {
        throw new IllegalArgumentException("Method " + REGISTER_FUNCTIONS + " in class " + className + " must take single JacsalEnv argument");
      }
      method.invoke(null, env);
    }
  }
}