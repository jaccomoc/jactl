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

import io.jactl.runtime.RuntimeState;
import io.jactl.runtime.RuntimeUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptTest {
  @Test public void generateExprClasses() throws Exception {
    String                script   = readResource("/io/jactl/GenerateClasses.jactl");
    String                source   = readResource("/Expr.java");
    String                expected = readResource("/io/jactl/Expr.java.generated").trim();
    ByteArrayOutputStream baos   = new ByteArrayOutputStream();
    PrintStream           out    = new PrintStream(baos);
    RuntimeState.setOutput(out);
    Jactl.eval(script, Utils.mapOf("source", source));
    String actualOutput = baos.toString().trim();
    diff(expected, actualOutput);
  }

  @Test public void generateStmtClasses() throws Exception {
    String                script   = readResource("/io/jactl/GenerateClasses.jactl");
    String                source   = readResource("/Stmt.java");
    String                expected = readResource("/io/jactl/Stmt.java.generated").trim();
    ByteArrayOutputStream baos   = new ByteArrayOutputStream();
    PrintStream           out    = new PrintStream(baos);
    RuntimeState.setOutput(out);

    Jactl.eval(script, Utils.mapOf("source", source));
    String actualOutput = baos.toString().trim();

    diff(expected, actualOutput);
  }

  private void diff(String expected, String actualOutput) {
    if (!expected.equals(actualOutput)) {
      Iterator<String> expectedIter = Arrays.asList(expected.split("\n")).iterator();
      Iterator<String> actualIter   = Arrays.asList(actualOutput.split("\n")).iterator();

      int line = 1;
      while (expectedIter.hasNext() && actualIter.hasNext()) {
        String prefix = "Line " + line + ":";
        assertEquals(prefix + expectedIter.next(), prefix + actualIter.next());
        line++;
      }

      if (expectedIter.hasNext()) {
        System.out.println("Actual output missing these lines:");
        while (expectedIter.hasNext()) {
          String prefix = "Line " + line + ":";
          System.out.println(prefix + expectedIter.next());
          line++;
        }
        fail("Actual output missing lines at the end");
      }

      if (actualIter.hasNext()) {
        System.out.println("Actual output has these extra lines:");
        while (actualIter.hasNext()) {
          String prefix = "Line " + line + ":";
          System.out.println(prefix + actualIter.next());
          line++;
        }
        fail("Actual output has additional lines at the end");
      }
      assertTrue(!expectedIter.hasNext() && !actualIter.hasNext());
    }
  }

  String readResource(String resource) throws URISyntaxException, IOException {
    return new String(Files.readAllBytes(Paths.get(getClass().getResource(resource).toURI())));
  }
}
