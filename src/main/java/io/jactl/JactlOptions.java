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

package io.jactl;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Load ~/.jactlrc file and process options:
 * <ul>
 *   <li>Add listed JARs to class loader</li>
 *   <li>Instantiate JactlEnv instance of given subclass type</li>
 *   <li>Invoke registerFunctions() on listed functionClasses</li>
 * </ul>
 */
public class JactlOptions {
  final static String OPTIONS_FILE       = System.getProperty("user.home") + "/.jactlrc";
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

  private JactlEnv env;

  private JactlOptions(JactlEnv env) {
    this.env = env;
  }

  public JactlEnv getEnvironment() {
    return env;
  }

  public static JactlOptions initOptions() {
    String defaultEnvClass = DefaultEnv.class.getName();
    Map<String,Object> options = new LinkedHashMap<>(Utils.mapOf(ENV_CLASS, defaultEnvClass,
                                                            FUNC_CLASSES, new ArrayList(),
                                                            EXTRA_JARS, new ArrayList()));
    if (!new File(OPTIONS_FILE).exists()) {
      return new JactlOptions(new DefaultEnv());
    }
    try {
      // Run options file as a Jactl script
      Jactl.eval(new String(Files.readAllBytes(Paths.get(OPTIONS_FILE))), options);

      List<String> extraJars = (List<String>) options.get(EXTRA_JARS);
      ClassLoader classLoader = JactlOptions.class.getClassLoader();
      if (extraJars.size() > 0) {
        //ExtendableClassLoader extendableClassLoader = new ExtendableClassLoader(ClassLoader.getSystemClassLoader());
        CustomUrlClassLoader extendableClassLoader = new CustomUrlClassLoader();
        for (String jar : extraJars) {
          jar = jar.replaceAll("~", System.getProperty("user.home"));
          //extendableClassLoader.addJar(jar);
          extendableClassLoader.addURL(Paths.get(jar).toUri().toURL());
        }
        classLoader = extendableClassLoader;
        Thread.currentThread().setContextClassLoader(classLoader);
      }
      String    envClass = (String)options.get(ENV_CLASS);
      Class<?>  clss     = classLoader.loadClass(envClass);
      JactlEnv env      = (JactlEnv) clss.getConstructor().newInstance();
      registerFunctions(classLoader, (List<String>)options.get(FUNC_CLASSES), env);
      return new JactlOptions(env);
    }
    catch (Throwable e) {
      throw new RuntimeException("Error loading options from ~/.jactlrc", e);
    }
  }

  private static void registerFunctions(ClassLoader classLoader, List<String> funcClasses, JactlEnv env) throws Exception {
    for (String className: funcClasses) {
      Class<?> clss              = classLoader.loadClass(className);
      Optional<Method> optMethod = Arrays.stream(clss.getDeclaredMethods())
                                         .filter(m -> m.getName().equals(REGISTER_FUNCTIONS))
                                         .findFirst();
      if (!optMethod.isPresent()) {
        throw new IllegalArgumentException("Could not find method called " + REGISTER_FUNCTIONS + " in class " + className);
      }
      Method method = optMethod.get();
      if (!Modifier.isStatic(method.getModifiers())) {
        throw new IllegalArgumentException("Method " + REGISTER_FUNCTIONS + " in class " + className + " must be static");
      }
      if (method.getParameterTypes().length != 1 || !JactlEnv.class.isAssignableFrom(method.getParameterTypes()[0])) {
        throw new IllegalArgumentException("Method " + REGISTER_FUNCTIONS + " in class " + className + " must take single JactlEnv argument");
      }
      method.invoke(null, env);
    }
  }
}