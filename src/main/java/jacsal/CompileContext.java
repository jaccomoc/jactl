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

import java.util.HashMap;
import java.util.Map;

public class CompileContext {

  private final DynamicClassLoader classLoader        = new DynamicClassLoader();

  boolean evaluateConstExprs = true;
  int     maxScale           = 20;

  // In repl mode top level vars are stored in the globals map and their type
  // is tracked here. We also allow redefinitions of existing vars. We don't
  // allow shadowing of actual global vars that already exist in the globals map.
  boolean            replMode           = false;

  // In repl mode we keep track of the type of the top level vars here
  // and store their actual values in the globals map that is passed in
  // at run time.
  final Map<String,Expr.VarDecl> globalVars = new HashMap<>();

  // Whether to dump byte code during compilation
  boolean debug = false;

  public CompileContext() {}

  Class<?> loadClass(String name, byte[] bytes) {
    return classLoader.loadClass(name, bytes);
  }

  public CompileContext replMode(boolean mode)            { this.replMode           = mode;  return this; }
  public CompileContext maxScale(int scale)               { this.maxScale           = scale; return this; }
  public CompileContext evaluateConstExprs(boolean value) { this.evaluateConstExprs = value; return this; }
  public CompileContext debug(boolean value)              { this.debug              = value; return this; }

  //////////////////////////////////

  private static class DynamicClassLoader extends ClassLoader {
    Class<?> loadClass(String name, byte[] bytes) {
      return defineClass(name, bytes, 0, bytes.length);
    }
  }
}
