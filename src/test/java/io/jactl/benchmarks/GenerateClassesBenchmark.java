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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = "-ea")
public class GenerateClassesBenchmark extends BaseExecutionBenchmark {

  public GenerateClassesBenchmark() {
    try {
      input = readResource("/Expr.java");
      expectedOutput = readResource("/io/jactl/Expr.java.generated");
      javaSource = readResource("/io/jactl/benchmarks/GenerateClasses.java");
      jactlSource = readResource("/io/jactl/benchmarks/GenerateClasses.jactl");
      groovySource = readResource("/io/jactl/benchmarks/GenerateClasses.groovy");
    }
    catch (IOException e) {
      assert false : "Error reading resources: " + e.getMessage();
    }
  }
  
  @Setup(Level.Trial)
  public void setup() throws Exception {
    super.setup();
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

  public static void main(String[] args) throws Exception {
    Options opts = new OptionsBuilder()
                     .include(GenerateClassesBenchmark.class.getSimpleName())
                     .build();
    new Runner(opts).run();
  }
}
