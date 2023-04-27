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

package io.jactl.runtime;

public class Json {

  public static Object toJsonData;
  public static String toJson(Object obj, String source, int offset) {
    var buf = JsonEncoder.get(source, offset);
    buf.writeObj(obj);
    return buf.finalise();
  }

  public static Object fromJsonData;
  public static Object fromJson(String str, String source, int offset) {
    var decoder = JsonDecoder.get(str, source, offset);
    return decoder.decode();
  }

}
