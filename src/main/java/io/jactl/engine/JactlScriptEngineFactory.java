/*
 * Copyright © 2022-2026 James Crawford
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

package io.jactl.engine;

import io.jactl.Utils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JactlScriptEngineFactory implements ScriptEngineFactory {
  
  private static List<String> NAMES           = Collections.unmodifiableList(Utils.listOf("jactl"));
  private static List<String> FILE_EXTENSIONS = Collections.unmodifiableList(Utils.listOf("jactl"));
  private static List<String> MIME_TYPES      = Collections.unmodifiableList(Utils.listOf("application/jactl", "text/jactl"));
  
  @Override public String       getEngineName()      { return "Jactl"; }
  @Override public String       getEngineVersion()   { return "Jactl " + Utils.JACTL_VERSION; }
  @Override public List<String> getExtensions()      { return FILE_EXTENSIONS; }
  @Override public List<String> getMimeTypes()       { return MIME_TYPES; }
  @Override public List<String> getNames()           { return NAMES; }
  @Override public String       getLanguageName()    { return "Jactl"; }
  @Override public String       getLanguageVersion() { return Utils.JACTL_LANGUAGE_VERSION; }

  @Override public Object getParameter(String key) {
    switch (key) {
      case ScriptEngine.ENGINE:           return getEngineName();
      case ScriptEngine.ENGINE_VERSION:   return getEngineVersion();
      case ScriptEngine.LANGUAGE:         return getLanguageName();
      case ScriptEngine.LANGUAGE_VERSION: return getLanguageVersion();
      case ScriptEngine.NAME:             return "jactl";
      case "THREADING":                   return "THREAD-ISOLATED";
      default:                            return null;
    }
  }

  @Override public String getMethodCallSyntax(String obj, String m, String... args) {
    StringBuilder sb = new StringBuilder().append(obj).append('.').append(m).append('(');
    sb.append(Arrays.stream(args).collect(Collectors.joining(", ")));
    sb.append(')');
    return sb.toString();
  }

  @Override public String getOutputStatement(String toDisplay) {
    return "print '" + toDisplay + "';";
  }

  @Override public String getProgram(String... statements) {
    return String.join("\n", statements) + "\n";
  }

  @Override public ScriptEngine getScriptEngine() {
    return new JactlScriptEngine(this);
  }
}
