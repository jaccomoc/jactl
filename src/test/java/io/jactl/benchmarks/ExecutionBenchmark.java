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

package io.jactl.benchmarks;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.jactl.DefaultEnv;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import io.jactl.compiler.Compiler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = "-ea")
public class ExecutionBenchmark {

  // Java execution state
  private Method javaMethod;

  // Jactl execution state
  private JactlScript jactlScript;

  // Groovy execution state
  private Script groovyScript;

  // Shared input/expected output
  private String input;
  private String expectedOutput;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    input = readResource("/Expr.java");
    expectedOutput  = readResource("/io/jactl/Expr.java.generated").trim();

    setupJava();
    setupJactl();
    setupGroovy();
  }
  
  @TearDown(Level.Trial)
  public void tearDown() {
    DefaultEnv.shutdown();
  }

  @Benchmark
  public void javaExecution() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream           out  = new PrintStream(baos);
    javaMethod.invoke(null, input, out);
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Java output mismatch: diff=\n" + diff;
  }

  @Benchmark
  public void jactlExecution() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream           out  = new PrintStream(baos);
    jactlScript.runSync(Collections.singletonMap("source", input), null, out);
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Jactl output mismatch: diff=\n" + diff;
  }

  @Benchmark
  public void groovyExecution() {
    Binding binding = new Binding();
    binding.setVariable("source", input);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream           out  = new PrintStream(baos);
    binding.setVariable("out", out);
    groovyScript.setBinding(binding);
    groovyScript.run();
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Groovy output mismatch: diff=\n" + diff;
  }

  public static void main(String[] args) throws Exception {
    Options opts = new OptionsBuilder()
        .include(ExecutionBenchmark.class.getSimpleName())
        .build();
    new Runner(opts).run();
  }

  ////////////////////////////////////////

  private String diff(String expected, String actualOutput) {
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


  private void setupJava() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Writer devNull = new Writer() {
      public void write(char[] cbuf, int off, int len) {}
      public void flush() {}
      public void close() {}
    };
    String          javaSource  = readResource("/io/jactl/benchmark/GenerateClasses.java");
    StringFile      code        = new StringFile("GenerateClasses.java", javaSource);
    TestFileManager fileManager = new TestFileManager(compiler.getStandardFileManager(null, null, null));

    boolean result = compiler.getTask(devNull, fileManager, null, null, null, Arrays.asList(code)).call();
    assert result : "Java compilation failed during setup";

    String          className   = "io.jactl.benchmark.GenerateClasses";
    StringFile      classFile   = fileManager.getFile(className);
    assert classFile != null : "Compiled class file not found for " + className;

    TestClassLoader classLoader = new TestClassLoader(getClass().getClassLoader());
    classLoader.defineClass(className, classFile.getBytes());
    Class<?> cls = classLoader.loadClass(className);
    javaMethod = cls.getMethod("generateClasses", String.class, PrintStream.class);
  }

  private void setupJactl() throws IOException {
    JactlContext context    = JactlContext.create().build();
    String       jactlSrc   = readResource("/io/jactl/benchmark/GenerateClasses.jactl");
    jactlScript = Compiler.compileScript(jactlSrc, context, Collections.singletonMap("source", ""));
  }

  private void setupGroovy() throws IOException {
    String groovySrc = readResource("/io/jactl/benchmark/GenerateClasses.groovy");
    groovyScript = new GroovyShell().parse(groovySrc, "GenerateClasses.groovy");
  }

  ////////////////////////////////////////

  private String readResource(String resource) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      int c;
      while ((c = in.read()) != -1) {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  private static int countLines(String s) {
    return (int) s.chars().filter(c -> c == '\n').count();
  }

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
  }

  static class TestClassLoader extends ClassLoader {
    private Map<String, Class<?>> classes = new HashMap<>();

    public TestClassLoader(ClassLoader parent) { super(parent); }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
      Class<?> cls = classes.get(name);
      if (cls == null) throw new ClassNotFoundException(name);
      return cls;
    }

    public void defineClass(String name, byte[] bytes) {
      classes.put(name, super.defineClass(name, bytes, 0, bytes.length));
    }
  }

}
