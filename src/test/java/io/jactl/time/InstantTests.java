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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;

import static org.junit.jupiter.api.Assertions.fail;

public class InstantTests extends BaseTest {

  @Test public void instanceOfTest() {
    test("Instant.parse('2026-02-18T12:11:10.00Z') instanceof Instant", true);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); d instanceof Instant", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d instanceof Instant", true);
    test("Instant.parse('2026-02-18T12:13:14.00Z') instanceof jactl.time.Instant", true);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); d instanceof jactl.time.Instant", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d instanceof jactl.time.Instant", true);
    globals.put("d", Instant.parse("2026-02-18T12:13:14.00Z"));
    test("d instanceof Instant", true);
  }

  @Test public void atZone() {
    test("Instant.parse('2026-02-18T13:13:14.00Z').atZone(ZoneId.systemDefault())", Instant.parse("2026-02-18T13:13:14.00Z").atZone(ZoneId.systemDefault()));
    test("Instant.parse('2026-02-18T13:13:14.00Z').atZone(ZoneId.of('Australia/Sydney'))", Instant.parse("2026-02-18T13:13:14.00Z").atZone(ZoneId.of("Australia/Sydney")));
    test("Instant.parse('2026-02-18T13:13:14.00Z').atZone(zoneId:ZoneId.of('Australia/Sydney'))", Instant.parse("2026-02-18T13:13:14.00Z").atZone(ZoneId.of("Australia/Sydney")));
    test("def f = Instant.parse('2026-02-18T13:13:14.00Z').atZone; f(ZoneId.of('Australia/Sydney'))", Instant.parse("2026-02-18T13:13:14.00Z").atZone(ZoneId.of("Australia/Sydney")));
    test("def f = Instant.parse('2026-02-18T13:13:14.00Z').atZone; f(zoneId:ZoneId.of('Australia/Sydney'))", Instant.parse("2026-02-18T13:13:14.00Z").atZone(ZoneId.of("Australia/Sydney")));
  }

  @Test void getEpochSecond() {
    test("def d = Instant.parse('2026-02-18T13:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T13:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T14:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T14:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T22:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T22:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T12:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T11:13:14.00Z'); def f = d.getEpochSecond; f()", Instant.parse("2026-02-18T11:13:14.00Z").getEpochSecond());
    test("def d = Instant.parse('2026-02-18T03:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T03:13:14.00Z").getEpochSecond());
    test("def d = Instant.parse('2026-02-18T03:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T03:13:14.00Z").getEpochSecond());
    test("def d = Instant.parse('2026-02-18T00:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T00:13:14.00Z").getEpochSecond());
    test("def d = Instant.parse('2026-02-18T02:13:14.00Z'); def f = d.getEpochSecond; f()", Instant.parse("2026-02-18T02:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T12:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T12:13:14.00Z").getEpochSecond());
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); d.getEpochSecond()", Instant.parse("2026-02-18T12:13:14.00Z").getEpochSecond());
  }

  @Test void getNano() {
    test("def d = Instant.parse('2026-02-18T13:13:14.00Z'); d.getNano()", 0);
    test("Instant d = Instant.parse('2026-02-18T14:13:14.123456789Z'); d.getNano()", 123456789);
    test("Instant d = Instant.parse('2026-02-18T22:13:14.123456789Z'); d.getNano()", 123456789);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("Instant d = Instant.parse('2026-02-18T11:13:14.123456789Z'); def f = d.getNano; f()", 123456789);
    test("def d = Instant.parse('2026-02-18T03:13:14.123456789Z'); d.getNano()", 123456789);
    test("def d = Instant.parse('2026-02-18T03:13:14.123456789Z'); d.getNano()", 123456789);
    test("def d = Instant.parse('2026-02-18T00:13:14.123456Z'); d.getNano()", 123456000);
    test("def d = Instant.parse('2026-02-18T02:13:14.1234567Z'); def f = d.getNano; f()", 123456700);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
  }

  @Test public void isAfter() {
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.000000001Z').isAfter(d)", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.000000001Z').isAfter(other:d)", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.00Z').isAfter(d)", false);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:15.00Z').isAfter(d)", true);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); def f = Instant.parse('2026-02-18T12:13:14.000000001Z').isAfter; f(d)", true);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:13.00Z').isAfter(d)", false);
  }

  @Test public void isBefore() {
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.000000001Z').isBefore(d)", false);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.000000001Z').isBefore(other:d)", false);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.00Z').isBefore(d)", false);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:15.00Z').isBefore(d)", false);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); def f = Instant.parse('2026-02-18T12:13:14.000000001Z').isBefore; f(d)", false);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:13.00Z').isBefore(d)", true);
  }

  @Test public void equalsTest() {
    test("Instant now = Instant.now(); now == now", true);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:13.00Z') == d", false);
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant.parse('2026-02-18T12:13:14.00Z') == d", true);
  }

  @Test public void minus() {
    test("Instant.parse('2026-02-18T12:13:14.00Z').minus(Duration.ofSeconds(0))", Instant.parse("2026-02-18T12:13:14.00Z"));
    test("Instant.parse('2026-02-18T12:13:14.00Z').minus(Duration.ofSeconds(1))", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("TemporalAmount p = Duration.ofSeconds(1); Instant.parse('2026-02-18T12:13:14.00Z').minus(p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); d.minus(p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("def p = Duration.ofSeconds(1); Instant.parse('2026-02-18T12:13:14.00Z').minus(p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); d.minus(amount:p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", Instant.parse("2026-02-18T12:13:13.00Z"));
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", Instant.parse("2026-02-18T12:13:13.00Z"));
  }

  @Test public void minusNanos() {
    test("Instant d = Instant.parse('2026-02-18T12:13:14.000000001Z'); Instant d2 = d.minusNanos(-1); d2 == Instant.parse('2026-02-18T12:13:14.000000002Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.000000001Z'); Instant d2 = d.minusNanos(1); d2 == Instant.parse('2026-02-18T12:13:14.00Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.000000001Z'); Instant d2 = d.minusNanos(24*60*60*1000000000L); d2 == Instant.parse('2026-02-17T12:13:14.000000001Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.000000001Z'); Instant d2 = d.minusNanos(0); d2 == Instant.parse('2026-02-18T12:13:14.000000001Z')", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusNanos(1); Instant d2 = now.minusNanos(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.minusNanos(1); Instant d2 = now.minusNanos(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.minusNanos(1); def d2 = now.minusNanos(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusNanos(1); Instant d2 = now.minusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void minusMillis() {
    test("Instant d = Instant.parse('2026-02-18T12:13:14.001Z'); Instant d2 = d.minusMillis(-1); d2 == Instant.parse('2026-02-18T12:13:14.002Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.001Z'); Instant d2 = d.minusMillis(1); d2 == Instant.parse('2026-02-18T12:13:14.00Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.001Z'); Instant d2 = d.minusMillis(24*60*60*1000L); d2 == Instant.parse('2026-02-17T12:13:14.001Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.001Z'); Instant d2 = d.minusMillis(0); d2 == Instant.parse('2026-02-18T12:13:14.001Z')", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusMillis(1); Instant d2 = now.minusMillis(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.minusMillis(1); Instant d2 = now.minusMillis(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.minusMillis(1); def d2 = now.minusMillis(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusMillis(1); Instant d2 = now.minusMillis(millis:1); d1 == d2", true);
  }

  @Test public void minusSeconds() {
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant d2 = d.minusSeconds(-1); d2 == Instant.parse('2026-02-18T12:13:15.00Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant d2 = d.minusSeconds(1); d2 == Instant.parse('2026-02-18T12:13:13.00Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant d2 = d.minusSeconds(24*60*60); d2 == Instant.parse('2026-02-17T12:13:14.00Z')", true);
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Instant d2 = d.minusSeconds(0); d2 == Instant.parse('2026-02-18T12:13:14.00Z')", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusSeconds(1); Instant d2 = now.minusSeconds(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.minusSeconds(1); Instant d2 = now.minusSeconds(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.minusSeconds(1); def d2 = now.minusSeconds(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.minusSeconds(1); Instant d2 = now.minusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void now() {
    test("Instant now = Instant.now(); Instant now2 = Instant.now(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def f = Instant.now; Instant now = f(); Instant now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def now = Instant.now(); def now2 = Instant.now(); Duration.between(now2,now).getSeconds() < 5", true);
  }

  @Test public void ofEpochSecond() {
    test("Instant.ofEpochSecond(0) == Instant.parse('1970-01-01T00:00:00.00Z')", true);
    test("Instant.ofEpochSecond(1,123456789) == Instant.parse('1970-01-01T00:00:01.123456789Z')", true);
    test("Instant.ofEpochSecond(second:3600+1) == Instant.parse('1970-01-01T01:00:01.00Z')", true);
    test("def f = Instant.ofEpochSecond; f(10) == Instant.parse('1970-01-01T00:00:10.00Z')", true);
    test("def f = Instant.ofEpochSecond; f(second:10) == Instant.parse('1970-01-01T00:00:10.00Z')", true);
    test("Instant.ofEpochSecond(0) == Instant.parse('1970-01-01T00:00:00.00Z')", true);
    test("Instant.ofEpochSecond(-1)", Instant.parse("1969-12-31T23:59:59.00Z"));
    testError("Instant.ofEpochSecond(1000000000000000000L)", "exceeds");
  }

  @Test public void ofEpochMilli() {
    test("Instant.ofEpochMilli(0) == Instant.parse('1970-01-01T00:00:00.00Z')", true);
    test("Instant.ofEpochMilli(milli:3600*1000+1000+1) == Instant.parse('1970-01-01T01:00:01.001Z')", true);
    test("def f = Instant.ofEpochMilli; f(10) == Instant.parse('1970-01-01T00:00:00.010Z')", true);
    test("def f = Instant.ofEpochMilli; f(milli:10) == Instant.parse('1970-01-01T00:00:00.010Z')", true);
    test("Instant.ofEpochMilli(0) == Instant.parse('1970-01-01T00:00:00.00Z')", true);
    test("Instant.ofEpochMilli(-1)", Instant.parse("1969-12-31T23:59:59.999Z"));
  }

  @Test public void parse() {
    test("Instant.parse('2026-02-18T12:13:14.00Z')", Instant.parse("2026-02-18T12:13:14.00Z"));
    test("Instant.parse(text:'2026-02-18T12:13:14.00Z')", Instant.parse("2026-02-18T12:13:14.00Z"));
    test("def f = Instant.parse; f('2026-02-18T12:13:14.00Z')", Instant.parse("2026-02-18T12:13:14.00Z"));
    test("def f = Instant.parse; f(text:'2026-02-18T12:13:14.00Z')", Instant.parse("2026-02-18T12:13:14.00Z"));
    testError("Instant.parse('2026-02-18T12:13:14.00Z:123')", "could not be parsed");
  }

  @Test public void plus() {
    test("Instant.parse('2026-02-18T12:13:14.00Z').plus(Duration.ofSeconds(1))", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("Instant.parse('2026-02-18T12:13:14.00Z').plus(Period.ofDays(1))", Instant.parse("2026-02-19T12:13:14.00Z"));
    testError("Instant.parse('2026-02-18T12:13:14.00Z').plus(Period.ofYears(1))", "unsupported unit: years");
    test("TemporalAmount p = Duration.ofSeconds(1); Instant.parse('2026-02-18T12:13:14.00Z').plus(p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); d.plus(p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("def p = Duration.ofSeconds(1); Instant.parse('2026-02-18T12:13:14.00Z').plus(p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); d.plus(amount:p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("Instant d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", Instant.parse("2026-02-18T12:13:15.00Z"));
    test("def d = Instant.parse('2026-02-18T12:13:14.00Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", Instant.parse("2026-02-18T12:13:15.00Z"));
  }

  @Test public void plusNanos() {
    test("Instant now = Instant.now(); Instant d1 = now.plusNanos(1); Instant d2 = now.plusNanos(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.plusNanos(1); Instant d2 = now.plusNanos(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.plusNanos(1); Instant d2 = now.plusNanos(nanos:1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void plusMillis() {
    test("Instant now = Instant.now(); Instant d1 = now.plusMillis(1); Instant d2 = now.plusMillis(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.plusMillis(1); Instant d2 = now.plusMillis(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusMillis(1); def d2 = now.plusMillis(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.plusMillis(1); Instant d2 = now.plusMillis(millis:1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusMillis(1); def d2 = now.plusMillis(millis:1); d1 == d2", true);
  }

  @Test public void plusSeconds() {
    test("Instant now = Instant.now(); Instant d1 = now.plusSeconds(1); Instant d2 = now.plusSeconds(2); d1 == d2", false);
    test("Instant now = Instant.now(); Instant d1 = now.plusSeconds(1); Instant d2 = now.plusSeconds(1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(1); d1 == d2", true);
    test("Instant now = Instant.now(); Instant d1 = now.plusSeconds(1); Instant d2 = now.plusSeconds(seconds:1); d1 == d2", true);
    test("def now = Instant.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void toEpochMilli() {
    test("Instant.parse('2026-02-18T12:13:14.00Z').toEpochMilli()", Instant.parse("2026-02-18T12:13:14.00Z").toEpochMilli());
    test("def f = Instant.parse('2026-02-18T12:13:14.00Z').toEpochMilli; f()", Instant.parse("2026-02-18T12:13:14.00Z").toEpochMilli());
    test("def t = Instant.parse('2026-02-18T12:13:14.00Z'); t.toEpochMilli()", Instant.parse("2026-02-18T12:13:14.00Z").toEpochMilli());
    test("def t = Instant.parse('2026-02-18T12:13:14.00Z'); def f = t.toEpochMilli; f()", Instant.parse("2026-02-18T12:13:14.00Z").toEpochMilli());
  }

  @Test public void withNano() {
    test("Instant.parse('2026-02-18T00:00:00.00Z').withNano(10)", Instant.parse("2026-02-18T00:00:00.00000001Z"));
    test("Instant.parse('2026-02-18T00:00:00.123Z').withNano(nano:0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withNano; f(0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withNano; f(nano:11)", Instant.parse("2026-02-18T00:00:00.000000011Z"));
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withNano(-1)", "invalid value");
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withNano(6000000000L)", "invalid value");
  }

  @Test public void withMicro() {
    test("Instant.parse('2026-02-18T00:00:00.00Z').withMicro(10)", Instant.parse("2026-02-18T00:00:00.00001Z"));
    test("Instant.parse('2026-02-18T00:00:00.123Z').withMicro(micro:0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withMicro; f(0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withMicro; f(micro:11)", Instant.parse("2026-02-18T00:00:00.000011Z"));
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withMicro(-1)", "invalid value");
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withMicro(6000000000L)", "invalid value");
  }

  @Test public void withMilli() {
    test("Instant.parse('2026-02-18T00:00:00.00Z').withMilli(10)", Instant.parse("2026-02-18T00:00:00.01Z"));
    test("Instant.parse('2026-02-18T00:00:00.123Z').withMilli(milli:0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withMilli; f(0)", Instant.parse("2026-02-18T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-18T00:00:00.123Z').withMilli; f(milli:11)", Instant.parse("2026-02-18T00:00:00.011Z"));
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withMilli(-1)", "invalid value");
    testError("Instant.parse('2026-02-18T00:00:00.00Z').withMilli(6000000000L)", "invalid value");
  }

  @Test public void truncatedToMicros() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMicros()", Instant.parse("2026-02-19T12:13:14.123456Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMicros; f()", Instant.parse("2026-02-19T12:13:14.123456Z"));
  }

  @Test public void truncatedToMillis() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMillis()", Instant.parse("2026-02-19T12:13:14.123Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMillis; f()", Instant.parse("2026-02-19T12:13:14.123Z"));
  }

  @Test public void truncatedToSeconds() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToSeconds()", Instant.parse("2026-02-19T12:13:14.00Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToSeconds; f()", Instant.parse("2026-02-19T12:13:14.00Z"));
  }

  @Test public void truncatedToMinutes() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMinutes()", Instant.parse("2026-02-19T12:13:00.00Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToMinutes; f()", Instant.parse("2026-02-19T12:13:00.00Z"));
  }

  @Test public void truncatedToHours() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToHours()", Instant.parse("2026-02-19T12:00:00.00Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToHours; f()", Instant.parse("2026-02-19T12:00:00.00Z"));
  }

  @Test public void truncatedToDays() {
    test("Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToDays()", Instant.parse("2026-02-19T00:00:00.00Z"));
    test("def f = Instant.parse('2026-02-19T12:13:14.123456789Z').truncatedToDays; f()", Instant.parse("2026-02-19T00:00:00.00Z"));
  }
  
  @Test public void toStringTest() {
    test("Instant.parse('2026-02-18T12:13:14.00Z').toString()", "2026-02-18T12:13:14Z");
    test("def f = Instant.parse('2026-02-18T12:13:14.00Z').toString; f()", "2026-02-18T12:13:14Z");
  }

}
