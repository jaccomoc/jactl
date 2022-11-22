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

package jacsal.runtime;

import jacsal.JacsalContext;
import jacsal.JacsalType;

import java.util.HashMap;
import java.util.Map;

public class JacsalPackage {
  String                       name;
  Map<String, JacsalPackage>   packages = new HashMap<>();
  Map<String, ClassDescriptor> classes  = new HashMap<>();

  public JacsalPackage() {
    this(null);
  }

  public JacsalPackage(String name) {
    this.name = name;
  }

  public void addPackage(JacsalPackage pkg) {
    packages.put(pkg.name, pkg);
  }

  public JacsalPackage getPackage(String name) {
    JacsalPackage pkg = this;
    for (String part: name.split("\\.")) {
      pkg = pkg.packages.get(part);
      if (pkg == null) {
        return null;
      }
    }
    return pkg;
  }

  private JacsalPackage getOrCreatePackage(String name) {
    JacsalPackage pkg = this;
    for (String part: name.split("\\.")) {
      JacsalPackage newPkg = pkg.packages.get(part);
      if (newPkg == null) {
        newPkg = new JacsalPackage(part);
        pkg.packages.put(part, newPkg);
      }
      pkg = newPkg;
    }
    return pkg;
  }

  public void addClass(ClassDescriptor clss) {
    JacsalPackage pkg = getOrCreatePackage(clss.getPackageName());
    pkg.classes.put(clss.name, clss);
  }

  public ClassDescriptor getClass(String name) {
    return classes.get(name);
  }
}
