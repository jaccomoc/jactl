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

package io.jactl.time;

import io.jactl.BaseTest;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Tests for ZoneId class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have actually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class ZoneIdTests extends BaseTest {
  
  @Test public void instanceOfTest() {
    test("ZoneId.of('Australia/Sydney') instanceof ZoneId", true);
    test("def z = ZoneId.of('Australia/Sydney'); z instanceof ZoneId", true);
    test("ZoneId z = ZoneId.of('Australia/Sydney'); z instanceof ZoneId", true);
    test("ZoneId.of('Australia/Sydney') instanceof jactl.time.ZoneId", true);
    test("def z = ZoneId.of('Australia/Sydney'); z instanceof jactl.time.ZoneId", true);
    test("ZoneId z = ZoneId.of('Australia/Sydney'); z instanceof jactl.time.ZoneId", true);
    globals.put("z", ZoneId.of("Australia/Sydney"));
    test("z instanceof ZoneId", true);
  }
  
  @Test public void zoneIdTests() {
    test("'Australia/Sydney' in ZoneId.getAvailableZoneIds()", true);
    test("def f = ZoneId.getAvailableZoneIds; 'Australia/Sydney' in f()", true);
    test("ZoneId.isValid('Australia/Sydney')", true);
    test("def f = ZoneId.isValid; f('Australia/Sydney')", true);
    test("ZoneId.isValid('Australia/SydneyXXX')", false);
    test("ZoneId z = ZoneId.of('Australia/Sydney'); z.getId()", "Australia/Sydney");
    test("def z = ZoneId.of('Australia/Sydney'); z.getId()", "Australia/Sydney");
    test("def f = ZoneId.of; ZoneId z = f('Australia/Sydney'); z.getId()", "Australia/Sydney");
    test("def f = ZoneId.of; def z = f('Australia/Sydney'); def g = z.getId; g()", "Australia/Sydney");
    test("ZoneId z1 = ZoneId.of('Australia/Sydney'); ZoneId z2 = ZoneId.of('Australia/Melbourne'); z1.getId() == z2.getId()", false);
    test("ZoneId z1 = ZoneId.of('Australia/Sydney'); ZoneId z2 = ZoneId.of('Australia/Melbourne'); z1 == z2", false);
    test("ZoneId z1 = ZoneId.of('Australia/Sydney'); ZoneId z2 = ZoneId.of('Australia/Sydney'); z1 == z2", true);
    test("ZoneId z = ZoneId.of('Australia/Sydney'); z == z.normalized()", true);
    test("ZoneId z = ZoneId.of('UTC+10:00'); z.normalized() == ZoneId.of('+10:00')", true);
    test("ZoneId z = ZoneId.of('UTC+10:00'); def f = z.normalized; f() == ZoneId.of('+10:00')", true);
    test("def z = ZoneId.of('UTC+10:00'); def f = z.normalized; f() == ZoneId.of('+10:00')", true);
    test("ZoneId z = ZoneId.of('UTC+10:00'); z.normalized() == ZoneId.of('+10:00'); z == ZoneId.of('UTC+10:00')", true);
    test("ZoneId z = ZoneId.of('Australia/Sydney'); z.getId() == z.normalized().getId()", true);
    test("ZoneId.of('XXX') == null", true);
    try {
      // Make sure there is a valid ZoneId.systemDefault() first so tests don't fail just because system
      // on which they are running does not have a valid timezone set
      ZoneId ignore = ZoneId.systemDefault();
      test("ZoneId.isValid(ZoneId.systemDefault().getId())", true);
      test("ZoneId.systemDefault().getId() in ZoneId.getAvailableZoneIds()", true);
      test("def f = ZoneId.systemDefault; f().getId() in ZoneId.getAvailableZoneIds()", true);
    }
    catch (DateTimeException e) {
      // If, for some reason there is no valid systemDefault zone configured
    }
  }

  @Test public void unmodifiableLists() {
    testError("ZoneId.getAvailableZoneIds()[2] = 'xxx'", "attempt to modify an unmodifiable list");
    testError("def x = ZoneId.getAvailableZoneIds(); x[2] = 'xxx'", "attempt to modify an unmodifiable list");
    testError("ZoneId.getAvailableZoneIds().addAt(2, 'xxx')", "attempt to modify an unmodifiable list");
    testError("def x = ZoneId.getAvailableZoneIds(); x.addAt(2, 'xxx')", "attempt to modify an unmodifiable list");
    testError("ZoneId.getAvailableZoneIds().add('xxx')", "attempt to modify an unmodifiable list");
    testError("def x = ZoneId.getAvailableZoneIds(); x.add('xxx')", "attempt to modify an unmodifiable list");
    testError("ZoneId.getAvailableZoneIds().remove(2)", "attempt to modify an unmodifiable list");
    testError("def x = ZoneId.getAvailableZoneIds(); x.remove(2)", "attempt to modify an unmodifiable list");
    testError("def x = ZoneId.getAvailableZoneIds(); def f = x.remove; f(2)", "attempt to modify an unmodifiable list");
  }
  
  @Test public void toStringTest() {
    test("ZoneId.of('Australia/Sydney').toString()", "Australia/Sydney");
    test("def f = ZoneId.of('Australia/Sydney').toString; f()", "Australia/Sydney");
  }
}
