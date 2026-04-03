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

package io.jactl.resolver;

import io.jactl.Expr;
import io.jactl.runtime.ClassDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Imports {
  public final Map<String, ClassDescriptor> importedClasses          = new HashMap<>();
  public final List<String>                 importedPackages         = new ArrayList<>();
  public final Map<String, Expr.VarDecl>    importedConstants        = new HashMap<>();
  public final Map<String, Expr.VarDecl>    importedConstantsRenamed = new HashMap<>();
  
  private List<String> importStmts = new ArrayList<>();
  
  public void add(String importStmt) {
    importStmts.add(importStmt);
  }
  
  public List<String> getImportStmts() {
    return importStmts;
  } 
}
