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

import java.time.Duration;

/**
 * Tests for Duration class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have atually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class DurationTests extends BaseTest {
  @Test public void instanceOfTest() {
    test("Duration.ofDays(1) instanceof Duration", true);
    test("Duration.ofSecondsAndNanos(1,2) instanceof Duration", true);
    test("def p = Duration.ofSecondsAndNanos(1,2); p instanceof Duration", true);
    test("Duration p = Duration.ofSecondsAndNanos(1,2); p instanceof Duration", true);
    test("Duration.ofSecondsAndNanos(1,2) instanceof jactl.time.Duration", true);
    test("def p = Duration.ofSecondsAndNanos(1,2); p instanceof jactl.time.Duration", true);
    test("Duration p = Duration.ofSecondsAndNanos(1,2); p instanceof jactl.time.Duration", true);
    globals.put("p", Duration.ofSeconds(1, 2));
    test("p instanceof Duration", true);
  }

  @Test public void abs() {
    test("Duration d = Duration.ofSecondsAndNanos(-1,-2); d.abs() == Duration.ofSecondsAndNanos(1,2)", true);
    test("Duration d = Duration.ofDays(-1); d.abs() == Duration.ofDays(1)", true);
    test("Duration d = Duration.ofDays(1); d.abs() == Duration.ofDays(1)", true);
    test("Duration d = Duration.ofDays(0); d.abs() == Duration.ofDays(0)", true);
    test("Duration d = Duration.ofDays(0); def f = d.abs; f() == Duration.ofDays(0)", true);
    test("def d = Duration.ofDays(0); def f = d.abs; f() == Duration.ofDays(0)", true);
  }
  
  @Test public void between() {
    testError("Duration.between(LocalDate.of(2026,1,2),LocalDate.of(2026,1,3)) == Duration.ofDays(1)", "cannot be LocalDate");
    testError("Duration.between(LocalDateTime.of(2026,1,2,1,2,3),LocalDate.of(2026,1,3)) == Duration.ofDays(1)", "cannot be LocalDate");
    test("Duration.between(LocalDateTime.of(2026,1,2,12,0,0),LocalDateTime.of(2026,1,3,12,0,0)) == Duration.ofDays(1)", true);
    test("Duration.between(LocalDateTime.of(2026,1,2,12,1,2),LocalDateTime.of(2026,1,2,12,2,3)) == Duration.ofSeconds(61)", true);
    test("Duration.between(LocalTime.of(12,1,2),LocalTime.of(12,2,3)) == Duration.ofSeconds(61)", true);
    test("Duration.between(start:LocalTime.of(12,1,2),end:LocalTime.of(12,2,3)) == Duration.ofSeconds(61)", true);
    test("def f = Duration.between; f(LocalTime.of(12,1,2),LocalTime.of(12,2,3)) == Duration.ofSeconds(61)", true);
    test("def f = Duration.between; f(start:LocalTime.of(12,1,2),end:LocalTime.of(12,2,3)) == Duration.ofSeconds(61)", true);
  }
  
  @Test public void dividedBy() {
    testError("Duration d = Duration.ofSeconds(10000L); d.dividedBy(0)", "cannot divide by zero");
    test("Duration d = Duration.ofSeconds(10); d.dividedBy(1)", Duration.ofSeconds(10));
    test("Duration d = Duration.ofSeconds(10); d.dividedBy(10)", Duration.ofSeconds(1));
    test("Duration d = Duration.ofSeconds(-10); d.dividedBy(10)", Duration.ofSeconds(-1));
    test("Duration d = Duration.ofSeconds(10); d.dividedBy(100)", Duration.ofSeconds(0,100000000));
    test("Duration d = Duration.ofSeconds(-10); d.dividedBy(100)", Duration.ofSeconds(0,-100000000));
    test("Duration d = Duration.ofSeconds(10); d.dividedBy(1000000000000L).isZero()", true);
    test("Duration d = Duration.ofSeconds(-10); d.dividedBy(1000000000000L).isZero()", true);
    test("Duration d = Duration.ofSeconds(-10); d.dividedBy(amount:10)", Duration.ofSeconds(-1));
    test("def d = Duration.ofSeconds(-10); d.dividedBy(amount:10)", Duration.ofSeconds(-1));
    test("Duration d = Duration.ofSeconds(-10); def f = d.dividedBy; f(10)", Duration.ofSeconds(-1));
    test("Duration d = Duration.ofSeconds(-10); def f = d.dividedBy; f(amount:10)", Duration.ofSeconds(-1));
    test("def d = Duration.ofSeconds(-10); def f = d.dividedBy; f(amount:10)", Duration.ofSeconds(-1));
  }
  
  @Test public void getNano() {
    test("Duration.ofSecondsAndNanos(1,2).getNano()", 2);
    test("def d = Duration.ofSecondsAndNanos(1,2); d.getNano()", 2);
    test("def d = Duration.ofSecondsAndNanos(1,2); def f = d.getNano; f()", 2);
    test("Duration d = Duration.ofSecondsAndNanos(1,2); def f = d.getNano; f()", 2);
  }

  @Test public void getSeconds() {
    test("Duration.ofSecondsAndNanos(2,3).getSeconds()", 2L);
    test("def d = Duration.ofSecondsAndNanos(2,3); d.getSeconds()", 2L);
    test("def d = Duration.ofSecondsAndNanos(2,3); def f = d.getSeconds; f()", 2L);
    test("Duration d = Duration.ofSecondsAndNanos(2,3); def f = d.getSeconds; f()", 2L);
  }
  
  @Test public void isNegative() {
    test("Duration.ofSeconds(-1).isNegative()", true);
    test("Duration.ofSeconds(1).isNegative()", false);
    test("Duration.ofSecondsAndNanos(1,-1).isNegative()", false);
    test("Duration.ofSecondsAndNanos(0,-1).isNegative()", true);
    test("def f = Duration.ofSecondsAndNanos(0,-1).isNegative; f()", true);
    test("def d = Duration.ofSecondsAndNanos(0,-1); def f = d.isNegative; f()", true);
  }

  @Test public void isZero() {
    test("Duration.ofSeconds(0).isZero()", true);
    test("Duration.ofSeconds(1).isZero()", false);
    test("Duration.ofSecondsAndNanos(1,-1).isZero()", false);
    test("Duration.ofSecondsAndNanos(0,0).isZero()", true);
    test("def f = Duration.ofSecondsAndNanos(0,0).isZero; f()", true);
    test("def d = Duration.ofSecondsAndNanos(0,0); def f = d.isZero; f()", true);
  }
  
  @Test public void minus() {
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(2,3); d.minus(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(2,3); def f = d.minus; f(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(2,3); def f = d.minus; f(other:d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("def d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(2,3); def f = d.minus; f(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("def d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(2,3); def f = d.minus; f(other:d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
  }
  
  @Test public void minusDays() {
    testError("Duration.ofDays(4).minusDays(100000000000000000L) == Duration.ofDays(3)", "overflow");
    test("Duration.ofDays(4).minusDays(1) == Duration.ofDays(3)", true);
    test("Duration.ofDays(4).minusDays(days:1) == Duration.ofDays(3)", true);
    test("def f = Duration.ofDays(4).minusDays; f(1) == Duration.ofDays(3)", true);
    test("def d = Duration.ofDays(4); def f = d.minusDays; f(1) == Duration.ofDays(3)", true);
    test("def d = Duration.ofDays(4); def f = d.minusDays; f(days:1) == Duration.ofDays(3)", true);
  }

  @Test public void minusHours() {
    testError("Duration.ofHours(4).minusHours(100000000000000000L) == Duration.ofHours(3)", "overflow");
    test("Duration.ofHours(4).minusHours(1) == Duration.ofHours(3)", true);
    test("Duration.ofHours(4).minusHours(hours:1) == Duration.ofHours(3)", true);
    test("def f = Duration.ofHours(4).minusHours; f(1) == Duration.ofHours(3)", true);
    test("def d = Duration.ofHours(4); def f = d.minusHours; f(1) == Duration.ofHours(3)", true);
    test("def d = Duration.ofHours(4); def f = d.minusHours; f(hours:1) == Duration.ofHours(3)", true);
  }

  @Test public void minusMillis() {
    test("Duration.ofMillis(4).minusMillis(1) == Duration.ofMillis(3)", true);
    test("Duration.ofMillis(4).minusMillis(millis:1) == Duration.ofMillis(3)", true);
    test("def f = Duration.ofMillis(4).minusMillis; f(1) == Duration.ofMillis(3)", true);
    test("def d = Duration.ofMillis(4); def f = d.minusMillis; f(1) == Duration.ofMillis(3)", true);
    test("def d = Duration.ofMillis(4); def f = d.minusMillis; f(millis:1) == Duration.ofMillis(3)", true);
  }

  @Test public void minusMinutes() {
    test("Duration.ofMinutes(4).minusMinutes(1) == Duration.ofMinutes(3)", true);
    test("Duration.ofMinutes(4).minusMinutes(minutes:1) == Duration.ofMinutes(3)", true);
    test("def f = Duration.ofMinutes(4).minusMinutes; f(1) == Duration.ofMinutes(3)", true);
    test("def d = Duration.ofMinutes(4); def f = d.minusMinutes; f(1) == Duration.ofMinutes(3)", true);
    test("def d = Duration.ofMinutes(4); def f = d.minusMinutes; f(minutes:1) == Duration.ofMinutes(3)", true);
  }

  @Test public void minusNanos() {
    test("Duration.ofNanos(4).minusNanos(1) == Duration.ofNanos(3)", true);
    test("Duration.ofNanos(4).minusNanos(nanos:1) == Duration.ofNanos(3)", true);
    test("def f = Duration.ofNanos(4).minusNanos; f(1) == Duration.ofNanos(3)", true);
    test("def d = Duration.ofNanos(4); def f = d.minusNanos; f(1) == Duration.ofNanos(3)", true);
    test("def d = Duration.ofNanos(4); def f = d.minusNanos; f(nanos:1) == Duration.ofNanos(3)", true);
  }

  @Test public void minusSeconds() {
    test("Duration.ofSeconds(4).minusSeconds(1) == Duration.ofSeconds(3)", true);
    test("Duration.ofSeconds(4).minusSeconds(seconds:1) == Duration.ofSeconds(3)", true);
    test("def f = Duration.ofSeconds(4).minusSeconds; f(1) == Duration.ofSeconds(3)", true);
    test("def d = Duration.ofSeconds(4); def f = d.minusSeconds; f(1) == Duration.ofSeconds(3)", true);
    test("def d = Duration.ofSeconds(4); def f = d.minusSeconds; f(seconds:1) == Duration.ofSeconds(3)", true);
  }

  @Test public void multipliedBy() {
    test("Duration.ofSeconds(3).multipliedBy(1) == Duration.ofSeconds(3)", true);
    test("Duration.ofSeconds(3).multipliedBy(2) == Duration.ofSeconds(6)", true);
    test("Duration.ofSeconds(4).multipliedBy(amount:2) == Duration.ofSeconds(8)", true);
    test("def f = Duration.ofSeconds(-4).multipliedBy; f(2) == Duration.ofSeconds(-8)", true);
    test("def d = Duration.ofSeconds(4); def f = d.multipliedBy; f(1) == Duration.ofSeconds(4)", true);
    test("def d = Duration.ofSeconds(4); def f = d.multipliedBy; f(amount:2) == Duration.ofSeconds(8)", true);
  }

  @Test public void negated() {
    test("Duration d = Duration.ofSecondsAndNanos(-1,-2); d.negated() == Duration.ofSecondsAndNanos(1,2)", true);
    test("Duration d = Duration.ofSecondsAndNanos(-1,2); d.negated() == Duration.ofSecondsAndNanos(1,-2)", true);
    test("Duration d = Duration.ofDays(-1); d.negated() == Duration.ofDays(1)", true);
    test("Duration d = Duration.ofDays(1); d.negated() == Duration.ofDays(-1)", true);
    test("Duration d = Duration.ofDays(0); d.negated() == Duration.ofDays(0)", true);
    test("Duration d = Duration.ofDays(0); def f = d.negated; f() == Duration.ofDays(0)", true);
    test("def d = Duration.ofDays(0); def f = d.negated; f() == Duration.ofDays(0)", true);
  }
  
  @Test public void ofDays() {
    test("Duration.ofDays(1)", Duration.ofDays(1));
    test("Duration.ofDays(2)", Duration.ofDays(2));
    test("Duration.ofDays(-100)", Duration.ofDays(-100));
    test("def f = Duration.ofDays; f(-100)", Duration.ofDays(-100));
    test("def f = Duration.ofDays; f(days:-100)", Duration.ofDays(-100));
  }
  
  @Test public void ofHours() {
    test("Duration.ofHours(1)", Duration.ofHours(1));
    test("Duration.ofHours(2)", Duration.ofHours(2));
    test("Duration.ofHours(-100)", Duration.ofHours(-100));
    test("def f = Duration.ofHours; f(-100)", Duration.ofHours(-100));
    test("def f = Duration.ofHours; f(hours:-100)", Duration.ofHours(-100));
  }
  
  @Test public void ofMillis() {
    test("Duration.ofMillis(1)", Duration.ofMillis(1));
    test("Duration.ofMillis(2)", Duration.ofMillis(2));
    test("Duration.ofMillis(-100)", Duration.ofMillis(-100));
    test("def f = Duration.ofMillis; f(-100)", Duration.ofMillis(-100));
    test("def f = Duration.ofMillis; f(millis:-100)", Duration.ofMillis(-100));
  }
  
  @Test public void ofMinutes() {
    test("Duration.ofMinutes(1)", Duration.ofMinutes(1));
    test("Duration.ofMinutes(2)", Duration.ofMinutes(2));
    test("Duration.ofMinutes(-100)", Duration.ofMinutes(-100));
    test("def f = Duration.ofMinutes; f(-100)", Duration.ofMinutes(-100));
    test("def f = Duration.ofMinutes; f(minutes:-100)", Duration.ofMinutes(-100));
  }
  
  @Test public void ofNanos() {
    test("Duration.ofNanos(1)", Duration.ofNanos(1));
    test("Duration.ofNanos(2)", Duration.ofNanos(2));
    test("Duration.ofNanos(2000000000000L)", Duration.ofNanos(2000000000000L));
    test("Duration.ofNanos(-100)", Duration.ofNanos(-100));
    test("def f = Duration.ofNanos; f(-100)", Duration.ofNanos(-100));
    test("def f = Duration.ofNanos; f(nanos:-100)", Duration.ofNanos(-100));
  }
  
  @Test public void ofSeconds() {
    test("Duration.ofSeconds(1)", Duration.ofSeconds(1));
    test("Duration.ofSeconds(2)", Duration.ofSeconds(2));
    test("Duration.ofSeconds(2000000000000L)", Duration.ofSeconds(2000000000000L));
    test("Duration.ofSeconds(-100)", Duration.ofSeconds(-100));
    test("def f = Duration.ofSeconds; f(-100)", Duration.ofSeconds(-100));
    test("def f = Duration.ofSeconds; f(seconds:-100)", Duration.ofSeconds(-100));
  }
  
  @Test public void ofSecondsAndNanos() {
    test("Duration.ofSecondsAndNanos(1,2)", Duration.ofSeconds(1,2));
    test("Duration.ofSecondsAndNanos(2,3)", Duration.ofSeconds(2,3));
    test("Duration.ofSecondsAndNanos(2000000000000L,0)", Duration.ofSeconds(2000000000000L,0));
    test("Duration.ofSecondsAndNanos(0,2000000000000L)", Duration.ofSeconds(0, 2000000000000L));
    test("Duration.ofSecondsAndNanos(-100,1)", Duration.ofSeconds(-100,1));
    test("def f = Duration.ofSecondsAndNanos; f(-100,1)", Duration.ofSeconds(-100,1));
    test("def f = Duration.ofSecondsAndNanos; f(seconds:-100,nanos:200)", Duration.ofSeconds(-100,200));
  }
  
  @Test public void parse() {
    testError("Duration.parse('xyz')", "text cannot be parsed");
    test("Duration.parse('P1D')", Duration.ofDays(1));
    test("Duration.parse(text:'P1D')", Duration.ofDays(1));
    test("def f = Duration.parse; f('P1D')", Duration.ofDays(1));
    test("def f = Duration.parse; f(text:'P1D')", Duration.ofDays(1));
  }

  @Test public void plus() {
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(-2,-3); d.plus(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(-2,-3); def f = d.plus; f(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("Duration d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(-2,-3); def f = d.plus; f(other:d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("def d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(-2,-3); def f = d.plus; f(d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
    test("def d = Duration.ofSecondsAndNanos(1,2); Duration d2 = Duration.ofSecondsAndNanos(-2,-3); def f = d.plus; f(other:d2) == Duration.ofSecondsAndNanos(-1,-1)", true);
  }

  @Test public void plusDays() {
    testError("Duration.ofDays(4).plusDays(100000000000000000L) == Duration.ofDays(3)", "overflow");
    test("Duration.ofDays(4).plusDays(1) == Duration.ofDays(5)", true);
    test("Duration.ofDays(4).plusDays(days:1) == Duration.ofDays(5)", true);
    test("def f = Duration.ofDays(4).plusDays; f(1) == Duration.ofDays(5)", true);
    test("def d = Duration.ofDays(4); def f = d.plusDays; f(1) == Duration.ofDays(5)", true);
    test("def d = Duration.ofDays(4); def f = d.plusDays; f(days:1) == Duration.ofDays(5)", true);
  }

  @Test public void plusHours() {
    testError("Duration.ofHours(4).plusHours(100000000000000000L) == Duration.ofHours(5)", "overflow");
    test("Duration.ofHours(4).plusHours(1) == Duration.ofHours(5)", true);
    test("Duration.ofHours(4).plusHours(hours:1) == Duration.ofHours(5)", true);
    test("def f = Duration.ofHours(4).plusHours; f(1) == Duration.ofHours(5)", true);
    test("def d = Duration.ofHours(4); def f = d.plusHours; f(1) == Duration.ofHours(5)", true);
    test("def d = Duration.ofHours(4); def f = d.plusHours; f(hours:1) == Duration.ofHours(5)", true);
  }

  @Test public void plusMillis() {
    test("Duration.ofMillis(4).plusMillis(1) == Duration.ofMillis(5)", true);
    test("Duration.ofMillis(4).plusMillis(millis:1) == Duration.ofMillis(5)", true);
    test("def f = Duration.ofMillis(4).plusMillis; f(1) == Duration.ofMillis(5)", true);
    test("def d = Duration.ofMillis(4); def f = d.plusMillis; f(1) == Duration.ofMillis(5)", true);
    test("def d = Duration.ofMillis(4); def f = d.plusMillis; f(millis:1) == Duration.ofMillis(5)", true);
  }

  @Test public void plusMinutes() {
    test("Duration.ofMinutes(4).plusMinutes(1) == Duration.ofMinutes(5)", true);
    test("Duration.ofMinutes(4).plusMinutes(minutes:1) == Duration.ofMinutes(5)", true);
    test("def f = Duration.ofMinutes(4).plusMinutes; f(1) == Duration.ofMinutes(5)", true);
    test("def d = Duration.ofMinutes(4); def f = d.plusMinutes; f(1) == Duration.ofMinutes(5)", true);
    test("def d = Duration.ofMinutes(4); def f = d.plusMinutes; f(minutes:1) == Duration.ofMinutes(5)", true);
  }

  @Test public void plusNanos() {
    test("Duration.ofNanos(4).plusNanos(1) == Duration.ofNanos(5)", true);
    test("Duration.ofNanos(4).plusNanos(nanos:1) == Duration.ofNanos(5)", true);
    test("def f = Duration.ofNanos(4).plusNanos; f(1) == Duration.ofNanos(5)", true);
    test("def d = Duration.ofNanos(4); def f = d.plusNanos; f(1) == Duration.ofNanos(5)", true);
    test("def d = Duration.ofNanos(4); def f = d.plusNanos; f(nanos:1) == Duration.ofNanos(5)", true);
  }

  @Test public void plusSeconds() {
    test("Duration.ofSeconds(4).plusSeconds(1) == Duration.ofSeconds(5)", true);
    test("Duration.ofSeconds(4).plusSeconds(seconds:1) == Duration.ofSeconds(5)", true);
    test("def f = Duration.ofSeconds(4).plusSeconds; f(1) == Duration.ofSeconds(5)", true);
    test("def d = Duration.ofSeconds(4); def f = d.plusSeconds; f(1) == Duration.ofSeconds(5)", true);
    test("def d = Duration.ofSeconds(4); def f = d.plusSeconds; f(seconds:1) == Duration.ofSeconds(5)", true);
  }
 
  @Test public void toDays() {
    test("Duration.ofDays(1).toDays()", 1L);
    test("Duration.ofSeconds(1).toDays()", 0L);
    test("Duration.ofSeconds(-1).toDays()", 0L);
    test("Duration d = Duration.ofSeconds(-1); d.toDays()", 0L);
    test("Duration d = Duration.ofSeconds(-1); def f = d.toDays; f()", 0L);
    test("def f = Duration.ofSeconds(-1).toDays; f()", 0L);
  }

  @Test public void toHours() {
    test("Duration.ofHours(1).toHours()", 1L);
    test("Duration.ofSeconds(1).toHours()", 0L);
    test("Duration.ofSeconds(-1).toHours()", 0L);
    test("Duration d = Duration.ofSeconds(-1); d.toHours()", 0L);
    test("Duration d = Duration.ofSeconds(-1); def f = d.toHours; f()", 0L);
    test("def f = Duration.ofSeconds(-1).toHours; f()", 0L);
  }

  @Test public void toMillis() {
    test("Duration.ofMillis(1).toMillis()", 1L);
    test("Duration.ofSeconds(1).toMillis()", 1000L);
    test("Duration.ofSeconds(-1).toMillis()", -1000L);
    test("Duration d = Duration.ofSeconds(-1); d.toMillis()", -1000L);
    test("Duration d = Duration.ofSeconds(-1); def f = d.toMillis; f()", -1000L);
    test("def f = Duration.ofSeconds(-1).toMillis; f()", -1000L);
  }

  @Test public void toMinutes() {
    test("Duration.ofMinutes(1).toMinutes()", 1L);
    test("Duration.ofSeconds(1).toMinutes()", 0L);
    test("Duration.ofSeconds(-1).toMinutes()", 0L);
    test("Duration d = Duration.ofSeconds(-1); d.toMinutes()", 0L);
    test("Duration d = Duration.ofSeconds(-1); def f = d.toMinutes; f()", 0L);
    test("def f = Duration.ofSeconds(-1).toMinutes; f()", 0L);
  }

  @Test public void toNanos() {
    test("Duration.ofNanos(1).toNanos()", 1L);
    test("Duration.ofSeconds(1).toNanos()", 1000000000L);
    test("Duration.ofSecondsAndNanos(-1,-11).toNanos()", -1000000011L);
    test("Duration d = Duration.ofSeconds(-1); d.toNanos()", -1000000000L);
    test("Duration d = Duration.ofSeconds(-1); def f = d.toNanos; f()", -1000000000L);
    test("def f = Duration.ofSeconds(-1).toNanos; f()", -1000000000L);
  }
  
  @Test public void withNanos() {
    test("Duration.ofSecondsAndNanos(1,2).withNanos(3)", Duration.ofSeconds(1,3));
    test("Duration.ofSecondsAndNanos(1,2).withNanos(nanos:3)", Duration.ofSeconds(1,3));
    test("Duration d = Duration.ofSecondsAndNanos(1,2); d.withNanos(nanos:3)", Duration.ofSeconds(1,3));
    test("Duration d = Duration.ofSecondsAndNanos(1,2); def f = d.withNanos; f(nanos:3)", Duration.ofSeconds(1,3));
    test("def d = Duration.ofSecondsAndNanos(1,2); def f = d.withNanos; f(nanos:3)", Duration.ofSeconds(1,3));
    test("def d = Duration.ofSecondsAndNanos(1,2); def f = d.withNanos; f(3)", Duration.ofSeconds(1,3));
  }

  @Test public void withSeconds() {
    test("Duration.ofSecondsAndNanos(1,2).withSeconds(3)", Duration.ofSeconds(3,2));
    test("Duration.ofSecondsAndNanos(1,2).withSeconds(seconds:3)", Duration.ofSeconds(3,2));
    test("Duration d = Duration.ofSecondsAndNanos(1,2); d.withSeconds(seconds:3)", Duration.ofSeconds(3,2));
    test("Duration d = Duration.ofSecondsAndNanos(1,2); def f = d.withSeconds; f(seconds:3)", Duration.ofSeconds(3,2));
    test("def d = Duration.ofSecondsAndNanos(1,2); def f = d.withSeconds; f(seconds:3)", Duration.ofSeconds(3,2));
    test("def d = Duration.ofSecondsAndNanos(1,2); def f = d.withSeconds; f(3)", Duration.ofSeconds(3,2));
  }

}
