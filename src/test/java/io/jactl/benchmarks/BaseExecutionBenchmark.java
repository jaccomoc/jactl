/*
 * Copyright © 2022-2026  James Crawford
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

package io.jactl.benchmarks;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.jactl.DefaultEnv;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import io.jactl.compiler.Compiler;

import javax.tools.*;
import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

public abstract class BaseExecutionBenchmark {
  
  // Java execution state
  protected MethodHandle javaMethod;

  // Jactl execution state
  protected JactlScript jactlScript;

  // Groovy execution state
  protected Script      groovyScript;

  // Shared input/expected output
  protected String      input;
  protected String expectedOutput;
  
  // Source code
  protected String  javaSource;
  protected String  jactlSource;
  protected String  groovySource;

  public void setup() throws Exception {
    setupJava();
    setupJactl();
    setupGroovy();
  }

  public void tearDown() {
    DefaultEnv.shutdown();
  }

  protected void setupJava() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Writer devNull = new Writer() {
      public void write(char[] cbuf, int off, int len) {}
      public void flush() {}
      public void close() {}
    };
    String packageName = null;
    String className = null;
    for (String line: javaSource.split("\n")) {
      if (line.matches("^ *package *[a-z.]*.*$")) {
        packageName = line.replaceAll("^ *package *", "").replaceAll(" *;.*$", "");
      }
      if (line.matches("^ *public class *[a-z.]*.*$")) {
        className = line.replaceAll(".*class *", "").replaceAll("[ {].*$", "");
      }
    }
    StringFile      code        = new StringFile(className + ".java", javaSource);
    TestFileManager fileManager = new TestFileManager(compiler.getStandardFileManager(null, null, null));

    boolean result = compiler.getTask(devNull, fileManager, null, null, null, Arrays.asList(code)).call();
    assert result : "Java compilation failed during setup";

    String fullClassName = packageName + "." + className;
    StringFile classFile = fileManager.getFile(fullClassName);
    assert classFile != null : "Compiled class file not found for " + fullClassName;

    TestClassLoader classLoader = new TestClassLoader(getClass().getClassLoader());
    fileManager.getFiles().forEach(file -> classLoader.defineClass(file.getName().substring(1), file.getBytes()));
    Class<?> cls = classLoader.loadClass(fullClassName);
    Method method = cls.getMethod("run", String.class, PrintWriter.class);
    javaMethod = MethodHandles.lookup().unreflect(method);
  }

  protected void setupJactl() throws IOException {
    JactlContext context  = JactlContext.create().build();
    jactlScript = Compiler.compileScript(jactlSource, context, Collections.singletonMap("source", ""));
  }

  protected void setupGroovy() throws IOException {
    groovyScript = new GroovyShell().parse(groovySource);
  }

  public void javaExecution() throws Throwable {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter           out  = new PrintWriter(baos);
    javaMethod.invokeExact(input, out);
    out.close();
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Java output mismatch: diff=\n" + diff;
  }

  public void jactlExecution() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream           out  = new PrintStream(baos);
    jactlScript.runSync(Collections.singletonMap("source", input), null, out);
    out.close();
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Jactl output mismatch: diff=\n" + diff;
  }

  public void groovyExecution() {
    Binding binding = new Binding();
    binding.setVariable("source", input);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter           out  = new PrintWriter(baos);
    binding.setVariable("out", out);
    groovyScript.setBinding(binding);
    groovyScript.run();
    out.close();
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Groovy output mismatch: diff=\n" + diff;
  }


  ////////////////////////////////////////

  static public String readResource(String resource) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (InputStream in = BaseExecutionBenchmark.class.getResourceAsStream(resource)) {
      int c;
      while ((c = in.read()) != -1) {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  String diff(String expected, String actualOutput) {
    if (!expected.equals(actualOutput)) {
      Iterator<String> expectedIter = Arrays.asList(expected.split("\n")).iterator();
      Iterator<String> actualIter   = Arrays.asList(actualOutput.split("\n")).iterator();

      int line = 1;
      while (expectedIter.hasNext() && actualIter.hasNext()) {
        String prefix = "Line " + line + ":";
        String expectedLine = prefix + expectedIter.next();
        String actualLine   = prefix + actualIter.next();
        if (!expectedLine.equals(actualLine)) {
          return "Expected: " + expectedLine + "\nbut got:  " + actualLine;
        }
        line++;
      }

      if (expectedIter.hasNext()) {
        StringBuilder sb = new StringBuilder();
        sb.append("Actual output missing these lines:");
        while (expectedIter.hasNext()) {
          String prefix = "Line " + line + ":";
          sb.append(prefix).append(expectedIter.next()).append("\n");
          line++;
        }
        return sb.toString();
      }

      if (actualIter.hasNext()) {
        StringBuilder sb = new StringBuilder();
        sb.append("Actual output has these extra lines:");
        while (actualIter.hasNext()) {
          String prefix = "Line " + line + ":";
          sb.append(prefix).append(actualIter.next()).append("\n");
          line++;
        }
        return sb.toString();
      }
    }
    return null;
  }

  //////////////////////////////////////////

  static class StringFile extends SimpleJavaFileObject {
    String                text;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    public StringFile(String name, String text) {
      super(URI.create("string:///" + name), Kind.SOURCE);
      this.text = text;
    }

    public StringFile(String name, Kind kind) {
      super(URI.create("string:///" + name), kind);
    }

    @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return this.text; }
    @Override public OutputStream openOutputStream() { return bos; }
    public byte[] getBytes() { return bos.toByteArray(); }
  }

  static class TestFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private Map<String, StringFile> files = new HashMap<>();

    protected TestFileManager(StandardJavaFileManager fileManager) {
      super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
      StringFile file = new StringFile(className, kind);
      files.put(className, file);
      return file;
    }

    public StringFile getFile(String className) { return files.get(className); }
    
    public List<StringFile> getFiles() { return new ArrayList<>(files.values()); }
  }

  static class TestClassLoader extends ClassLoader {
    private Map<String, Class<?>> classes = new HashMap<>();

    public TestClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      Class<?> cls = classes.get(name);
      if (cls == null) throw new ClassNotFoundException(name);
      return cls;
    }

    public void defineClass(String name, byte[] bytes) {
      classes.put(name, super.defineClass(name, bytes, 0, bytes.length));
    }
  }
}
