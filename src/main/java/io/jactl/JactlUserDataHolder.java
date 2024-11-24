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

public class JactlUserDataHolder {
  Object     userData;
  Stmt.Block block;

  public Token location;
  private Token contextLocation;

  public Object getUserData() {
    return userData;
  }

  public <T> T getUserData(Class<T> cls) {
    return (T)userData;
  }

  public void setUserData(Object data) {
    this.userData = data;
  }

  /**
   * Return declaration corresponding to use of a variable/function
   * @return the VarDecl corresponding to the identifier
   */
  public JactlUserDataHolder getDeclaration() {
    return this;
  }

  public Stmt.Block getBlock()           { return block; }
  public void setBlock(Stmt.Block block) { this.block = block; }

  public Token getLocation() { return location; }

  public void  setContextLocation(Token contextLocation) { this.contextLocation = contextLocation; }
  public Token getContextLocation() { return contextLocation; }
}
