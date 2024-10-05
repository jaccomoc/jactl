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

package io.jactl;

/**
 * Used to capture the name of a function, variable declaration, class, or package.
 * Only exists so that we can distinguish types of IDENTIFIER tokens in Intellij plugin.
 * Class is not otherwise used in Jactl compiler.
 */
public class JactlName extends JactlUserDataHolder {
  private Token    name;
  private NameType type;

  public enum NameType {
    FILE, PACKAGE, CLASS, FUNCTION, METHOD, FIELD, VARIABLE, PARAMETER
  }

  public JactlName(Token name, NameType type) {
    this.name = name;
    this.type = type;
  }

  public Token getName() {
    return name;
  }

  public NameType getType() {
    return type;
  }
}
