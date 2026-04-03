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

package io.jactl.runtime;

import io.jactl.JactlType;
import io.jactl.JactlUserDataGetter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ClassDescriptor extends JactlUserDataGetter {

  /**
   * Simple name of the class (e.g. x.y.z.A.B -&gt; B)
   * @return the simple name of the class
   */
  String getSimpleName();

  /**
   * The full package name (e.g. x.y.z.A.B -&gt; x.y.z.A.B)
   * @return the full package name of the class
   */
  String getPackagedName();

  /**
   * The name of the class without the package (i.e. includes enclosing classes).
   * E.g. x.y.z.A.B -&gt; A.B
   * @return the name path of the class
   */
  String getNamePath();

  /**
   * The package name of the class (e.g. x.y.z.A.B -&gt; x.y.z)
   * @return the package name of the class
   */
  String getPackageName();

  /**
   * The internal name of the class (e.g. x.y.z.A.B -&gt; x/y/z/A$B)
   * @return the internal name of the class
   */
  String getInternalName();

  /**
   * True if this class is assignable from the other class (i.e. other is a subclass)
   * @param other the other class descriptor
   * @return true if this class is assignable from the other class
   */
  boolean isAssignableFrom(ClassDescriptor other);

  /**
   * Get the base class descriptor for this class or null if there is no base class.
   * @return the base class descriptor or null
   */
  ClassDescriptor getBaseClass();

  /**
   * Get the JactlType for this class.
   * @return the JactlType for this class
   */
  JactlType getClassType();
  
  /**
   * Get the JactlType for the base class or null if no base class.
   * @return the JactlType for the base class or null
   */
  JactlType getBaseClassType();

  /**
   * Return class descriptor for an inner class 
   * @param name the name of the inner class
   * @return descriptor or null if no such inner class
   */
  ClassDescriptor getInnerClass(String name);

  default JactlType getField(String name) {
    return null;
  }

  default List<Map.Entry<String,JactlType>> getFields() {
    return Collections.EMPTY_LIST;
  }

  default JactlType getStaticField(String name) {
    return null;
  }

  default Object getStaticFieldValue(String name) {
    return null;
  }

  default List<String> getAllFieldNames() {
    return Collections.EMPTY_LIST;
  }

  default List<JactlType> getAllFieldTypes() {
    return Collections.EMPTY_LIST;
  }

  default boolean isEquivalent(ClassDescriptor other) {
    return this == other || this.getInternalName().equals(other.getInternalName());
  }

  default Object getUserData() {
    return null;
  }

  default <T> T getUserData(Class<T> cls) {
    return null;
  }
}
