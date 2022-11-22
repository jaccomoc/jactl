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

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * All JacsalObjects extend this. It provides runtime access to fields and methods.
 */
public abstract class JacsalObject {
  public final static Map<String,Object>        _$j$FieldsAndMethods = new HashMap<>();
  public final static Map<String, MethodHandle> _$j$StaticMethods    = new HashMap<>();

  public abstract JacsalObject _$j$newInstance$w(Continuation c, String source, int offset, Object[] args);
}
