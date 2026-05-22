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
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = "-ea")
public class MonteCarloBenchmark extends BaseExecutionBenchmark {
 
  private Script groovyStaticScript;
  
  public MonteCarloBenchmark() {
    try {
      input = "";
      //expectedOutput = "PI=3.14146204";     // 100_000_000
      //expectedOutput = "PI=3.141548816";    // 500_000_000 
      expectedOutput = "PI=3.13726";        // 1_000_000 
      //expectedOutput = "PI=3.14104";        // 100_000
      javaSource = readResource("/io/jactl/benchmarks/MonteCarloPI.java");
      jactlSource = readResource("/io/jactl/benchmarks/MonteCarloPI.jactl");
      groovySource = readResource("/io/jactl/benchmarks/MonteCarloPI.groovy");
    }
    catch (IOException e) {
      assert false : "Error reading resources: " + e.getMessage();
    }
  }

  @Setup(Level.Trial)
  public void setup() throws Exception {
    super.setup();
    groovyStaticScript = new GroovyShell().parse(readResource("/io/jactl/benchmarks/MonteCarloPIStatic.groovy"));
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    super.tearDown();
  }

  @Benchmark
  public void javaExecution() throws Throwable {
    super.javaExecution();
  }

  @Benchmark
  public void jactlExecution() throws Exception {
    super.jactlExecution();
  }

  @Benchmark
  public void groovyExecution() {
    super.groovyExecution();
  }
  
  @Benchmark
  public void groovyStatic() throws Throwable {
    Binding binding = new Binding();
    binding.setVariable("source", input);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream           out  = new PrintStream(baos);
    binding.setVariable("out", out);
    groovyStaticScript.setBinding(binding);
    groovyStaticScript.run();
    String diff = diff(expectedOutput, baos.toString());
    assert diff == null : "Groovy output mismatch: diff=\n" + diff;
  }

  public static void main(String[] args) throws Exception {
    Options opts = new OptionsBuilder()
                     .include(MonteCarloBenchmark.class.getSimpleName())
                     .build();
    new Runner(opts).run();
  }
}