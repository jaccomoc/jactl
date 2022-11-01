/*
 * Copyright 2022 James Crawford
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
 */

package jacsal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ScriptTest {
  @Test public void runScript() throws Exception {
    String script = readResource("/jacsal/GenerateClasses.jacsal");
    String source = readResource("/Expr.java");
    Compiler.run(script, Map.of("source", source));
  }

  String readResource(String resource) throws URISyntaxException, IOException {
    return Files.readString(Paths.get(getClass().getResource(resource).toURI()));
  }
}
