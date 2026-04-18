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
import io.jactl.CompileError;
import io.jactl.JactlContext;
import io.jactl.Pair;
import io.jactl.Utils;
import io.jactl.compiler.Compiler;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgs = "-ea")
public class CompilationBenchmark {

  private static final int FILE_COUNT = 100;    // How many files to compile at once
  
  // Java compilation state
  private JavaCompiler     javaCompiler;
  private List<StringFile> javaFiles;
  private TestFileManager  javaFileManager;
  private String           javaSource;
  private Writer           devNull;
  private TestClassLoader  javaClassLoader;

  // Jactl compilation state
  private JactlContext jactlContext;
  private String       jactlSource;
  private int          jactlScriptCounter;
  private static Map<String,Object> jactlBindings = Collections.singletonMap("source", "");

  // Groovy compilation state
  private GroovyShell groovyShell;
  private String      groovySource;
  private static Binding groovyBinding = new Binding(Collections.singletonMap("source", ""));
  
  // Line counts
  private int javaLineCount;
  private int jactlLineCount;
  private int groovyLineCount;

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class LineCounter {
    public long linesCompiled;
  }

  @Setup(Level.Trial)
  public void setup() throws IOException {
    // Java
    javaCompiler    = ToolProvider.getSystemJavaCompiler();
    javaFileManager = new TestFileManager(javaCompiler.getStandardFileManager(null, null, null));
    javaSource      = readResource("/io/jactl/benchmarks/GenerateClasses.java");
    javaLineCount   = countLines(javaSource);
    javaFiles = IntStream.range(0, FILE_COUNT)
                         .mapToObj(i -> Pair.of(i, javaSource.replaceAll("class GenerateClasses", "class GenerateClasses" + i)))
                         .map(pair -> new StringFile("GenerateClasses" + pair.first + ".java", pair.second))
                         .collect(Collectors.toList());
    javaClassLoader = new TestClassLoader(this.getClass().getClassLoader());
    devNull         = new Writer() {
      public void write(char[] cbuf, int off, int len) {}
      public void flush() {}
      public void close() {}
    };

    // Jactl
    jactlContext       = JactlContext.create().async(false).build();
    jactlSource        = readResource("/io/jactl/benchmarks/GenerateClasses.jactl");
    jactlLineCount     = countLines(jactlSource);
    jactlScriptCounter = 0;

    // Groovy
    groovyShell     = new GroovyShell(new TestClassLoader(this.getClass().getClassLoader()), groovyBinding);
    groovySource    = readResource("/io/jactl/benchmarks/GenerateClasses.groovy");
    groovyLineCount = countLines(groovySource);
  }

  @Benchmark
  public void javaCompilation(LineCounter counter) {
    boolean result = javaCompiler.getTask(devNull, javaFileManager, null, null, null, javaFiles).call();
    assert result : "Java compilation failed";
    for (int i = 0; i < FILE_COUNT; i++) {
      String     className     = "io.jactl.benchmark.GenerateClasses" + i;
      StringFile compiledClass = javaFileManager.getFile(className);
      assert compiledClass != null : "Couldn't find compiled class with name " + className;
      javaClassLoader.defineClass(className, compiledClass.getBytes());
      counter.linesCompiled += javaLineCount;
    }
  }

  @Benchmark
  public void jactlCompilation(LineCounter counter) {
    try {
      for (int i = 0; i < FILE_COUNT; i++) {
        Compiler.compileScript(jactlSource, jactlContext, "GenerateClasses" + i, Utils.DEFAULT_JACTL_PKG, jactlBindings);
        counter.linesCompiled += jactlLineCount;
      }
    }
    catch (CompileError e) {
      assert false : "Jactl compilation failed: " + e.getMessage();
    }
  }

  @Benchmark
  public void groovyCompilation(LineCounter counter) {
    try {
      for (int i = 0; i < FILE_COUNT; i++) {
        groovyShell.parse(groovySource, "GenerateClasses" + i, groovyBinding);
        counter.linesCompiled += groovyLineCount;
      }
    }
    catch (Exception e) {
      assert false : "Groovy compilation failed: " + e.getMessage();
    }
  }

  public static void main(String[] args) throws Exception {
    Options opts = new OptionsBuilder().include(CompilationBenchmark.class.getSimpleName())
                                       .build();
    new Runner(opts).run();
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

  private static int countLines(String source) {
    return (int) source.chars().filter(c -> c == '\n').count();
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
    private Map<String, StringFile> javaFiles = new HashMap<>();

    protected TestFileManager(StandardJavaFileManager fileManager) {
      super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
      StringFile file = new StringFile(className, kind);
      javaFiles.put(className, file);
      return file;
    }
    
    public StringFile getFile(String className) {
      return javaFiles.get(className);
    }
  }
  
  static class TestClassLoader extends ClassLoader {
    private Map<String, Class<?>> classes = new HashMap<>();
    
    public TestClassLoader(ClassLoader parent) {
      super(parent);
    }
    
    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
      Class<?> clss = classes.get(name);
      if (clss == null) {
        throw new ClassNotFoundException(name);
      }
      return clss;
    }
    
    public void defineClass(String name, byte[] bytes) {
      // Create a new class loader each time so we can define a new instance of an existing type
      Class<?> clss = new TestClassLoader(getParent()).defineClass(name, bytes, 0, bytes.length);
      classes.put(name, clss);
    }
  }

}
