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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for LocalDateTime class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have actually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class LocalDateTimeTests extends BaseTest {
  
  @Test public void instanceOfTest() {
    test("LocalDateTime.parse('2026-02-18T12:11:10') instanceof LocalDateTime", true);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d instanceof LocalDateTime", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d instanceof LocalDateTime", true);
    test("LocalDateTime.parse('2026-02-18T12:13:14') instanceof jactl.time.LocalDateTime", true);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d instanceof jactl.time.LocalDateTime", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d instanceof jactl.time.LocalDateTime", true);
    globals.put("d", LocalDateTime.parse("2026-02-18T12:13:14"));
    test("d instanceof LocalDateTime", true);
  }

  @Test public void atZone() {
    test("LocalDateTime t = LocalDateTime.parse('2026-02-18T12:13:14'); t.atZone(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-02-18T12:13:14Z[UTC]"));
    test("LocalDateTime t = LocalDateTime.parse('2026-02-18T12:13:14'); def f = t.atZone; f(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-02-18T12:13:14Z[UTC]"));
    test("LocalDateTime t = LocalDateTime.parse('2026-02-18T12:13:14'); t.atZone(zoneId:ZoneId.of('UTC'))", ZonedDateTime.parse("2026-02-18T12:13:14Z[UTC]"));
    test("LocalDateTime t = LocalDateTime.parse('2026-02-18T12:13:14'); def f = t.atZone; f(zoneId:ZoneId.of('UTC'))", ZonedDateTime.parse("2026-02-18T12:13:14Z[UTC]"));
  }

  @Test public void getDayOfMonth() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getDayOfMonth()", 2);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-02T12:11:13'); def f = d.getDayOfMonth; f()", 2);
    test("def d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getDayOfMonth()", 2);
    test("def d = LocalDateTime.parse('2026-01-12T12:11:13'); d.getDayOfMonth()", 12);
    test("def d = LocalDateTime.parse('2026-01-12T12:11:13'); def f = d.getDayOfMonth; f()", 12);
  }

  @Test public void getDayOfYear() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getDayOfYear()", 2);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-02T12:11:13'); def f = d.getDayOfYear; f()", 2);
    test("def d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getDayOfYear()", 2);
    test("def d = LocalDateTime.parse('2026-01-12T12:11:13'); d.getDayOfYear()", 12);
    test("def d = LocalDateTime.parse('2026-02-12T12:11:13'); d.getDayOfYear()", 43);
    test("def d = LocalDateTime.parse('2026-01-12T12:11:13'); def f = d.getDayOfYear; f()", 12);
  }

  @Test public void getDayOfWeek() {
    test("def d = LocalDateTime.parse('2026-02-16T12:11:13'); d.getDayOfWeek()", "MONDAY");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-20T12:11:13'); d.getDayOfWeek()", "TUESDAY");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:11:13'); d.getDayOfWeek()", "WEDNESDAY");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:11:13'); d.getDayOfWeek()", "THURSDAY");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:11:13'); def f = d.getDayOfWeek; f()", "THURSDAY");
    test("def d = LocalDateTime.parse('2026-01-23T12:11:13'); d.getDayOfWeek()", "FRIDAY");
    test("def d = LocalDateTime.parse('2026-01-24T12:11:13'); d.getDayOfWeek()", "SATURDAY");
    test("def d = LocalDateTime.parse('2026-01-25T12:11:13'); d.getDayOfWeek()", "SUNDAY");
    test("def d = LocalDateTime.parse('2026-01-25T12:11:13'); def f = d.getDayOfWeek; f()", "SUNDAY");
  }

  @Test void getMonth() {
    test("def d = LocalDateTime.parse('2026-01-19T12:11:13'); d.getMonth()", "JANUARY");
    test("LocalDateTime d = LocalDateTime.parse('2026-02-20T12:11:13'); d.getMonth()", "FEBRUARY");
    test("LocalDateTime d = LocalDateTime.parse('2026-03-21T12:11:13'); d.getMonth()", "MARCH");
    test("LocalDateTime d = LocalDateTime.parse('2026-04-22T12:11:13'); d.getMonth()", "APRIL");
    test("LocalDateTime d = LocalDateTime.parse('2026-05-22T12:11:13'); def f = d.getMonth; f()", "MAY");
    test("def d = LocalDateTime.parse('2026-06-23T12:11:13'); d.getMonth()", "JUNE");
    test("def d = LocalDateTime.parse('2026-07-24T12:11:13'); d.getMonth()", "JULY");
    test("def d = LocalDateTime.parse('2026-08-25T12:11:13'); d.getMonth()", "AUGUST");
    test("def d = LocalDateTime.parse('2026-09-25T12:11:13'); def f = d.getMonth; f()", "SEPTEMBER");
    test("LocalDateTime d = LocalDateTime.parse('2026-10-22T12:11:13'); d.getMonth()", "OCTOBER");
    test("LocalDateTime d = LocalDateTime.parse('2026-11-22T12:11:13'); d.getMonth()", "NOVEMBER");
    test("LocalDateTime d = LocalDateTime.parse('2026-12-22T12:11:13'); d.getMonth()", "DECEMBER");
  }

  @Test void getMonthValue() {
    test("def d = LocalDateTime.parse('2026-01-19T12:11:13'); d.getMonthValue()", 1);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-20T12:11:13'); d.getMonthValue()", 2);
    test("LocalDateTime d = LocalDateTime.parse('2026-03-21T12:11:13'); d.getMonthValue()", 3);
    test("LocalDateTime d = LocalDateTime.parse('2026-04-22T12:11:13'); d.getMonthValue()", 4);
    test("LocalDateTime d = LocalDateTime.parse('2026-05-22T12:11:13'); def f = d.getMonthValue; f()", 5);
    test("def d = LocalDateTime.parse('2026-06-23T12:11:13'); d.getMonthValue()", 6);
    test("def d = LocalDateTime.parse('2026-07-24T12:11:13'); d.getMonthValue()", 7);
    test("def d = LocalDateTime.parse('2026-08-25T12:11:13'); d.getMonthValue()", 8);
    test("def d = LocalDateTime.parse('2026-09-25T12:11:13'); def f = d.getMonthValue; f()", 9);
    test("LocalDateTime d = LocalDateTime.parse('2026-10-22T12:11:13'); d.getMonthValue()", 10);
    test("LocalDateTime d = LocalDateTime.parse('2026-11-22T12:11:13'); d.getMonthValue()", 11);
    test("LocalDateTime d = LocalDateTime.parse('2026-12-22T12:11:13'); d.getMonthValue()", 12);
  }

  @Test public void getYear() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getYear()", 2026);
    test("LocalDateTime d = LocalDateTime.parse('2027-01-02T12:11:13'); def f = d.getYear; f()", 2027);
    test("def d = LocalDateTime.parse('2026-01-02T12:11:13'); d.getYear()", 2026);
    test("def d = LocalDateTime.parse('1970-01-12T12:11:13'); d.getYear()", 1970);
    test("def d = LocalDateTime.parse('1969-02-12T12:11:13'); d.getYear()", 1969);
    test("def d = LocalDateTime.parse('1900-01-12T12:11:13'); def f = d.getYear; f()", 1900);
  }

  @Test void getHour() {
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T13:13:14'); d.getHour()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T22:13:14'); d.getHour()", 22);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = d.getHour; f()", 12);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = d.getHour; f()", 12);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getHour()", 12);
  }

  @Test void getMinute() {
    test("def d = LocalDateTime.parse('2026-02-18T13:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T13:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T22:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T11:13:14'); def f = d.getMinute; f()", 13);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14'); d.getMinute()", 13);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14'); d.getMinute()", 13);
    test("def d = LocalDateTime.parse('2026-02-18T00:13:14'); d.getMinute()", 13);
    test("def d = LocalDateTime.parse('2026-02-18T02:13:14'); def f = d.getMinute; f()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getMinute()", 13);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getMinute()", 13);
  }

  @Test void getSecond() {
    test("def d = LocalDateTime.parse('2026-02-18T13:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T14:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T22:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T11:13:14'); def f = d.getSecond; f()", 14);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14'); d.getSecond()", 14);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14'); d.getSecond()", 14);
    test("def d = LocalDateTime.parse('2026-02-18T00:13:14'); d.getSecond()", 14);
    test("def d = LocalDateTime.parse('2026-02-18T02:13:14'); def f = d.getSecond; f()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getSecond()", 14);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.getSecond()", 14);
  }

  @Test void getNano() {
    test("def d = LocalDateTime.parse('2026-02-18T13:13:14'); d.getNano()", 0);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T14:13:14.123456789'); d.getNano()", 123456789);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T22:13:14.123456789'); d.getNano()", 123456789);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T11:13:14.123456789'); def f = d.getNano; f()", 123456789);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14.123456789'); d.getNano()", 123456789);
    test("def d = LocalDateTime.parse('2026-02-18T03:13:14.123456789'); d.getNano()", 123456789);
    test("def d = LocalDateTime.parse('2026-02-18T00:13:14.123456'); d.getNano()", 123456000);
    test("def d = LocalDateTime.parse('2026-02-18T02:13:14.1234567'); def f = d.getNano; f()", 123456700);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.123456789'); d.getNano()", 123456789);
  }

  @Test public void isAfter() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:14.1').isAfter(d)", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:14').isAfter(d)", false);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.of(2026,02,18,12,13,15).isAfter(d)", true);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = LocalDateTime.parse('2026-02-18T12:13:14.1').isAfter; f(d)", true);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:13').isAfter(d)", false);
  }

  @Test public void isBefore() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:14.1').isBefore(d)", false);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:14').isBefore(d)", false);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.of(2026,02,18,12,13,15).isBefore(d)", false);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = LocalDateTime.parse('2026-02-18T12:13:14.1').isBefore; f(d)", false);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:13').isBefore(d)", true);
  }

  @Test public void isEqual() {
    test("LocalDateTime now = LocalDateTime.now(); now.isEqual(now)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d2 = now.plusDays(1); d2.isEqual(now)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d2 = now.plusDays(1); now.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); now.isEqual(other:now)", true);
    test("def now = LocalDateTime.now(); now.isEqual(now)", true);
    test("def now = LocalDateTime.now(); def f = now.isEqual; f(now)", true);
    test("def now = LocalDateTime.now(); now.isEqual(other:now)", true);
    test("def now = LocalDateTime.now(); def f = now.isEqual; f(other:now)", true);
  }

  @Test public void equalsTest() {
    test("LocalDateTime now = LocalDateTime.now(); now == now", true);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:13') == d", false);
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime.parse('2026-02-18T12:13:14') == d", true);
  }

  @Test public void minus() {
    test("LocalDateTime.parse('2026-02-18T12:13:14').minus(Duration.ofSeconds(0))", LocalDateTime.parse("2026-02-18T12:13:14"));
    test("LocalDateTime.parse('2026-02-18T12:13:14').minus(Duration.ofSeconds(1))", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("TemporalAmount p = Duration.ofSeconds(1); LocalDateTime.parse('2026-02-18T12:13:14').minus(p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); d.minus(p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("def p = Duration.ofSeconds(1); LocalDateTime.parse('2026-02-18T12:13:14').minus(p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); d.minus(amount:p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", LocalDateTime.parse("2026-02-18T12:13:13"));
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", LocalDateTime.parse("2026-02-18T12:13:13"));
  }

  @Test public void minusHours() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusHours(-1); d2 == LocalDateTime.parse('2026-02-18T13:13:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusHours(1); d2 == LocalDateTime.parse('2026-02-18T11:13:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusHours(24); d2 == LocalDateTime.parse('2026-02-17T12:13:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusHours(0); d2 == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusHours(1); LocalDateTime d2 = now.minusHours(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusHours(1); LocalDateTime d2 = now.minusHours(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusHours(1); def d2 = now.minusHours(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusHours(1); LocalDateTime d2 = now.minusHours(hours:1); d1 == d2", true);
  }

  @Test public void minusMinutes() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusMinutes(-1); d2 == LocalDateTime.parse('2026-02-18T12:14:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusMinutes(1); d2 == LocalDateTime.parse('2026-02-18T12:12:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusMinutes(24*60); d2 == LocalDateTime.parse('2026-02-17T12:13:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusMinutes(0); d2 == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMinutes(1); LocalDateTime d2 = now.minusMinutes(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMinutes(1); LocalDateTime d2 = now.minusMinutes(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusMinutes(1); def d2 = now.minusMinutes(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMinutes(1); LocalDateTime d2 = now.minusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void minusNanos() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.000000001'); LocalDateTime d2 = d.minusNanos(-1); d2 == LocalDateTime.parse('2026-02-18T12:13:14.000000002')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.000000001'); LocalDateTime d2 = d.minusNanos(1); d2 == LocalDateTime.parse('2026-02-18T12:13:14.0')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.000000001'); LocalDateTime d2 = d.minusNanos(24*60*60*1000000000L); d2 == LocalDateTime.parse('2026-02-17T12:13:14.000000001')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14.000000001'); LocalDateTime d2 = d.minusNanos(0); d2 == LocalDateTime.parse('2026-02-18T12:13:14.000000001')", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusNanos(1); LocalDateTime d2 = now.minusNanos(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusNanos(1); LocalDateTime d2 = now.minusNanos(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusNanos(1); def d2 = now.minusNanos(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusNanos(1); LocalDateTime d2 = now.minusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void minusSeconds() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusSeconds(-1); d2 == LocalDateTime.parse('2026-02-18T12:13:15')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusSeconds(1); d2 == LocalDateTime.parse('2026-02-18T12:13:13')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusSeconds(24*60*60); d2 == LocalDateTime.parse('2026-02-17T12:13:14')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); LocalDateTime d2 = d.minusSeconds(0); d2 == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusSeconds(1); LocalDateTime d2 = now.minusSeconds(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusSeconds(1); LocalDateTime d2 = now.minusSeconds(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusSeconds(1); def d2 = now.minusSeconds(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusSeconds(1); LocalDateTime d2 = now.minusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void minusDays() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusDays(1); d2.isEqual(LocalDateTime.parse('2026-01-22T12:11:13'))", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusDays(24); d2.isEqual(LocalDateTime.parse('2025-12-30T12:11:13'))", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusDays(1); LocalDateTime d2 = now.minusDays(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusDays(1); LocalDateTime d2 = now.minusDays(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusDays(1); LocalDateTime d2 = now.minusDays(days:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusDays(1); LocalDateTime d2 = now.minusDays(days:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(days:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.minusDays(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
  }

  @Test public void minusMonths() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusMonths(1); d2.isEqual(LocalDateTime.parse('2025-12-23T12:11:13'))", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusMonths(24); d2.isEqual(LocalDateTime.parse('2024-01-23T12:11:13'))", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMonths(1); LocalDateTime d2 = now.minusMonths(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMonths(1); LocalDateTime d2 = now.minusMonths(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusMonths(1); def d2 = now.minusMonths(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMonths(1); LocalDateTime d2 = now.minusMonths(months:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusMonths(1); LocalDateTime d2 = now.minusMonths(months:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusMonths(1); def d2 = now.minusMonths(months:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.minusMonths(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void minusWeeks() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusWeeks(1); d2.isEqual(LocalDateTime.parse('2026-01-16T12:11:13'))", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusWeeks(4); d2.isEqual(LocalDateTime.parse('2025-12-26T12:11:13'))", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusWeeks(1); LocalDateTime d2 = now.minusWeeks(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusWeeks(1); LocalDateTime d2 = now.minusWeeks(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusWeeks(1); def d2 = now.minusWeeks(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusWeeks(1); LocalDateTime d2 = now.minusWeeks(weeks:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusWeeks(1); LocalDateTime d2 = now.minusWeeks(weeks:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusWeeks(1); def d2 = now.minusWeeks(weeks:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.minusWeeks(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
  }

  @Test public void minusYears() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusYears(1); d2.isEqual(LocalDateTime.parse('2025-01-23T12:11:13'))", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-01-23T12:11:13'); LocalDateTime d2 = d.minusYears(4); d2.isEqual(LocalDateTime.parse('2022-01-23T12:11:13'))", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusYears(1); LocalDateTime d2 = now.minusYears(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusYears(1); LocalDateTime d2 = now.minusYears(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusYears(1); def d2 = now.minusYears(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusYears(1); LocalDateTime d2 = now.minusYears(years:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.minusYears(1); LocalDateTime d2 = now.minusYears(years:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.minusYears(1); def d2 = now.minusYears(years:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.minusYears(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
  }

  @Test public void now() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime now2 = LocalDateTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def f = LocalDateTime.now; LocalDateTime now = f(); LocalDateTime now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def now = LocalDateTime.now(); def now2 = LocalDateTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
  }

  @Test public void nowInZone() {
    test("(LocalDateTime.nowInZone(ZoneId.of('Australia/Sydney')).getSecond() + 5)/10", (LocalDateTime.now(ZoneId.of("Australia/Sydney")).getSecond() + 5) / 10);
    test("def f = LocalDateTime.nowInZone; (f(ZoneId.of('Australia/Sydney')).getSecond() + 5) / 10", (LocalDateTime.now(ZoneId.of("Australia/Sydney")).getSecond() + 5) / 10);
  }
  
  @Test public void of() {
    test("LocalDateTime t = LocalDateTime.of(2026,02,18,12,11,13,123456789)", LocalDateTime.of(2026,02,18,12,11,13, 123456789));
    test("def f = LocalDateTime.of; f(2026,02,18,12,11,13,123456789)", LocalDateTime.of(2026,02,18,12,11,13, 123456789));
    test("LocalDateTime t = LocalDateTime.of(2026,02,18,12,11,13)", LocalDateTime.of(2026,02,18,12,11,13));
    test("LocalDateTime t = LocalDateTime.of(2026,02,18,12,11)", LocalDateTime.of(2026,02,18,12,11));
    test("LocalDateTime t = LocalDateTime.of(year:2026,month:02,day:18,hour:12,minute:11,second:13,nano:123456789)", LocalDateTime.of(2026,02,18,12,11,13, 123456789));
    test("def f = LocalDateTime.of; f(year:2026,month:02,day:18,hour:12,minute:11,second:13,nano:123456789)", LocalDateTime.of(2026,02,18,12,11,13, 123456789));
    testError("LocalDateTime t = LocalDateTime.of(2026,02,18,12)", "missing mandatory argument");
    testError("LocalDateTime t = LocalDateTime.of(2026,22,18,12,11)", "invalid value");
  }
  
  @Test public void ofDateAndTime() {
    test("LocalDateTime t = LocalDateTime.ofDateAndTime(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789))", LocalDateTime.of(LocalDate.of(2026,02,18), LocalTime.of(12, 11, 13, 123456789)));
    test("def f = LocalDateTime.ofDateAndTime; f(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789))", LocalDateTime.of(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789)));
    test("def f = LocalDateTime.ofDateAndTime; f(date:LocalDate.of(2026,02,18),time:LocalTime.of(12,11,13,123456789))", LocalDateTime.of(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789)));
    test("LocalDateTime t = LocalDateTime.ofDateAndTime(LocalDate.of(2026,02,18),LocalTime.of(12,11,13))", LocalDateTime.of(LocalDate.of(2026,02,18),LocalTime.of(12,11,13)));
    test("LocalDateTime t = LocalDateTime.ofDateAndTime(date:LocalDate.of(2026,02,18),time:LocalTime.of(12,11,13))", LocalDateTime.of(LocalDate.of(2026,02,18),LocalTime.of(12,11,13)));
    testError("LocalDateTime t = LocalDateTime.ofDateAndTime(LocalDate.of(2026,02,18))", "missing mandatory argument");
  }
  
  @Test public void ofEpochSecond() {
    test("LocalDateTime.ofEpochSecond(0) == LocalDateTime.parse('1970-01-01T00:00:00.0')", true);
    test("LocalDateTime.ofEpochSecond(1,123456789) == LocalDateTime.parse('1970-01-01T00:00:01.123456789')", true);
    test("LocalDateTime.ofEpochSecond(second:3600+1) == LocalDateTime.parse('1970-01-01T01:00:01.0')", true);
    test("def f = LocalDateTime.ofEpochSecond; f(10) == LocalDateTime.parse('1970-01-01T00:00:10')", true);
    test("def f = LocalDateTime.ofEpochSecond; f(second:10) == LocalDateTime.parse('1970-01-01T00:00:10')", true);
    test("LocalDateTime.ofEpochSecond(0) == LocalDateTime.parse('1970-01-01T00:00:00')", true);
    test("LocalDateTime.ofEpochSecond(-1)", LocalDateTime.parse("1969-12-31T23:59:59"));
    testError("LocalDateTime.ofEpochSecond(1000000000000000000L)", "invalid value");
  }

  @Test public void ofInstant() {
    test("LocalDateTime t = LocalDateTime.ofInstant(Instant.parse('2026-02-19T12:13:14.00Z'), ZoneId.of('Australia/Sydney'))", LocalDateTime.ofInstant(Instant.parse("2026-02-19T12:13:14.00Z"), ZoneId.of("Australia/Sydney")));
    test("LocalDateTime t = LocalDateTime.ofInstant(instant:Instant.parse('2026-02-19T12:13:14.00Z'), zoneId:ZoneId.of('Australia/Sydney'))", LocalDateTime.ofInstant(Instant.parse("2026-02-19T12:13:14.00Z"), ZoneId.of("Australia/Sydney")));
    test("def f = LocalDateTime.ofInstant; f(Instant.parse('2026-02-19T12:13:14.00Z'), ZoneId.of('Australia/Sydney'))", LocalDateTime.ofInstant(Instant.parse("2026-02-19T12:13:14.00Z"), ZoneId.of("Australia/Sydney")));
    test("def f = LocalDateTime.ofInstant; f(instant:Instant.parse('2026-02-19T12:13:14.00Z'), zoneId:ZoneId.of('Australia/Sydney'))", LocalDateTime.ofInstant(Instant.parse("2026-02-19T12:13:14.00Z"), ZoneId.of("Australia/Sydney")));
  }
  
  @Test public void parse() {
    test("LocalDateTime.parse('2026-02-18T12:13:14')", LocalDateTime.parse("2026-02-18T12:13:14"));
    test("LocalDateTime.parse(text:'2026-02-18T12:13:14')", LocalDateTime.parse("2026-02-18T12:13:14"));
    test("def f = LocalDateTime.parse; f('2026-02-18T12:13:14')", LocalDateTime.parse("2026-02-18T12:13:14"));
    test("def f = LocalDateTime.parse; f(text:'2026-02-18T12:13:14')", LocalDateTime.parse("2026-02-18T12:13:14"));
    testError("LocalDateTime.parse('2026-02-18T12:13:14:123')", "could not be parsed");
  }

  @Test public void plus() {
    test("LocalDateTime.parse('2026-02-18T12:13:14').plus(Duration.ofSeconds(1))", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("LocalDateTime.parse('2026-02-18T12:13:14').plus(Period.ofDays(1))", LocalDateTime.parse("2026-02-19T12:13:14"));
    test("LocalDateTime.parse('2026-02-18T12:13:14').plus(Period.ofYears(1))", LocalDateTime.parse("2027-02-18T12:13:14"));
    test("TemporalAmount p = Duration.ofSeconds(1); LocalDateTime.parse('2026-02-18T12:13:14').plus(p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); d.plus(p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("def p = Duration.ofSeconds(1); LocalDateTime.parse('2026-02-18T12:13:14').plus(p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); d.plus(amount:p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", LocalDateTime.parse("2026-02-18T12:13:15"));
    test("def d = LocalDateTime.parse('2026-02-18T12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", LocalDateTime.parse("2026-02-18T12:13:15"));
  }

  @Test public void plusDays() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusDays(1); LocalDateTime d2 = now.plusDays(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusDays(1); LocalDateTime d2 = now.plusDays(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusDays(1); def d2 = now.plusDays(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusDays(1); LocalDateTime d2 = now.plusDays(days:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusDays(1); LocalDateTime d2 = now.plusDays(days:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusDays(1); def d2 = now.plusDays(days:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.plusDays(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
  }

  @Test public void plusMonths() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMonths(1); LocalDateTime d2 = now.plusMonths(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMonths(1); LocalDateTime d2 = now.plusMonths(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusMonths(1); def d2 = now.plusMonths(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMonths(1); LocalDateTime d2 = now.plusMonths(months:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMonths(1); LocalDateTime d2 = now.plusMonths(months:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusMonths(1); def d2 = now.plusMonths(months:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.plusMonths(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void plusWeeks() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusWeeks(1); LocalDateTime d2 = now.plusWeeks(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusWeeks(1); LocalDateTime d2 = now.plusWeeks(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusWeeks(1); def d2 = now.plusWeeks(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusWeeks(1); LocalDateTime d2 = now.plusWeeks(weeks:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusWeeks(1); LocalDateTime d2 = now.plusWeeks(weeks:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusWeeks(1); def d2 = now.plusWeeks(weeks:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.plusWeeks(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusWeeks(weeks:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusWeeks(weeks:1000000000000L)", "invalid value");
  }

  @Test public void plusYears() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusYears(1); LocalDateTime d2 = now.plusYears(2); d1.isEqual(d2)", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusYears(1); LocalDateTime d2 = now.plusYears(1); d1.isEqual(d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusYears(1); def d2 = now.plusYears(1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusYears(1); LocalDateTime d2 = now.plusYears(years:1); d1.isEqual(d2)", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusYears(1); LocalDateTime d2 = now.plusYears(years:1); d1.isEqual(other:d2)", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusYears(1); def d2 = now.plusYears(years:1); d1.isEqual(other:d2)", true);
    testError("def now = LocalDateTime.now(); def d1 = now.plusYears(1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
    testError("def now = LocalDateTime.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
  }

  @Test public void plusHours() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusHours(1); LocalDateTime d2 = now.plusHours(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusHours(1); LocalDateTime d2 = now.plusHours(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusHours(1); LocalDateTime d2 = now.plusHours(hours:1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(hours:1); d1 == d2", true);
  }

  @Test public void plusMinutes() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMinutes(1); LocalDateTime d2 = now.plusMinutes(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMinutes(1); LocalDateTime d2 = now.plusMinutes(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusMinutes(1); LocalDateTime d2 = now.plusMinutes(minutes:1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void plusNanos() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusNanos(1); LocalDateTime d2 = now.plusNanos(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusNanos(1); LocalDateTime d2 = now.plusNanos(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusNanos(1); LocalDateTime d2 = now.plusNanos(nanos:1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void plusSeconds() {
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusSeconds(1); LocalDateTime d2 = now.plusSeconds(2); d1 == d2", false);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusSeconds(1); LocalDateTime d2 = now.plusSeconds(1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(1); d1 == d2", true);
    test("LocalDateTime now = LocalDateTime.now(); LocalDateTime d1 = now.plusSeconds(1); LocalDateTime d2 = now.plusSeconds(seconds:1); d1 == d2", true);
    test("def now = LocalDateTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void toLocalDate() {
    test("LocalDateTime.parse('2026-02-18T12:13:14').toLocalDate() == LocalDate.parse('2026-02-18')", true);
    test("def f = LocalDateTime.parse('2026-02-18T12:13:14').toLocalDate; f() == LocalDate.parse('2026-02-18')", true);
    test("def t = LocalDateTime.parse('2026-02-18T12:13:14'); t.toLocalDate() == LocalDate.parse('2026-02-18')", true);
    test("def t = LocalDateTime.parse('2026-02-18T12:13:14'); def f = t.toLocalDate; f() == LocalDate.parse('2026-02-18')", true);
  }

  @Test public void toLocalTime() {
    test("LocalDateTime.parse('2026-02-18T12:13:14').toLocalTime() == LocalTime.parse('12:13:14')", true);
    test("def f = LocalDateTime.parse('2026-02-18T12:13:14').toLocalTime; f() == LocalTime.parse('12:13:14')", true);
    test("def t = LocalDateTime.parse('2026-02-18T12:13:14'); t.toLocalTime() == LocalTime.parse('12:13:14')", true);
    test("def t = LocalDateTime.parse('2026-02-18T12:13:14'); def f = t.toLocalTime; f() == LocalTime.parse('12:13:14')", true);
  }

  @Test public void withDayOfMonth() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withDayOfMonth(22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    testError("LocalDateTime d = LocalDateTime.parse('2026-02-21T12:13:14'); d.withDayOfMonth(30)", "invalid date");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withDayOfMonth(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfMonth; f(22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfMonth; f(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfMonth; f(22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfMonth; f(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
  }

  @Test public void withDayOfYear() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withDayOfYear(42)", LocalDateTime.parse("2026-02-11T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2020-01-21T12:13:14'); d.withDayOfYear(366)", LocalDateTime.parse("2020-12-31T12:13:14"));
    testError("LocalDateTime d = LocalDateTime.parse('2026-02-21T12:13:14'); d.withDayOfYear(366)", "invalid date");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withDayOfYear(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfYear; f(22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfYear; f(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfYear; f(22)", LocalDateTime.parse("2026-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-21T12:13:14'); def f = d.withDayOfYear; f(day:22)", LocalDateTime.parse("2026-01-22T12:13:14"));
  }

  @Test public void withMonth() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withMonth(12)", LocalDateTime.parse("2026-12-21T12:13:14"));
    testError("LocalDateTime d = LocalDateTime.parse('2026-02-21T12:13:14'); d.withMonth(30)", "invalid value");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); d.withMonth(month:2)", LocalDateTime.parse("2026-02-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withMonth; f(2)", LocalDateTime.parse("2026-02-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withMonth; f(month:2)", LocalDateTime.parse("2026-02-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withMonth; f(2)", LocalDateTime.parse("2026-02-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withMonth; f(month:2)", LocalDateTime.parse("2026-02-22T12:13:14"));
  }

  @Test public void withYear() {
    test("LocalDateTime d = LocalDateTime.parse('2026-01-21T12:13:14'); d.withYear(1969)", LocalDateTime.parse("1969-01-21T12:13:14"));
    testError("LocalDateTime d = LocalDateTime.parse('2026-02-21T12:13:14'); d.withYear(3000000000000L)", "invalid value");
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); d.withYear(year:1969)", LocalDateTime.parse("1969-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withYear; f(1969)", LocalDateTime.parse("1969-01-22T12:13:14"));
    test("LocalDateTime d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withYear; f(year:1969)", LocalDateTime.parse("1969-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withYear; f(1969)", LocalDateTime.parse("1969-01-22T12:13:14"));
    test("def d = LocalDateTime.parse('2026-01-22T12:13:14'); def f = d.withYear; f(year:1969)", LocalDateTime.parse("1969-01-22T12:13:14"));
  }

  @Test public void withHour() {
    test("LocalDateTime.parse('2026-02-18T00:00:00').withHour(10)", LocalDateTime.parse("2026-02-18T10:00:00"));
    test("LocalDateTime.parse('2026-02-18T00:00:00').withHour(hour:0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00').withHour; f(0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00').withHour; f(hour:0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withHour(24)", "invalid value");
  }

  @Test public void withMinute() {
    test("LocalDateTime.parse('2026-02-18T00:00:00').withMinute(10)", LocalDateTime.parse("2026-02-18T00:10:00"));
    test("LocalDateTime.parse('2026-02-18T00:00:00').withMinute(minute:0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00').withMinute; f(0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00').withMinute; f(minute:11)", LocalDateTime.parse("2026-02-18T00:11:00"));
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withMinute(-1)", "invalid value");
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withMinute(60)", "invalid value");
  }

  @Test public void withNano() {
    test("LocalDateTime.parse('2026-02-18T00:00:00').withNano(10)", LocalDateTime.parse("2026-02-18T00:00:00.00000001"));
    test("LocalDateTime.parse('2026-02-18T00:00:00.123').withNano(nano:0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00.123').withNano; f(0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:00.123').withNano; f(nano:11)", LocalDateTime.parse("2026-02-18T00:00:00.000000011"));
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withNano(-1)", "invalid value");
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withNano(6000000000L)", "invalid value");
  }

  @Test public void withSecond() {
    test("LocalDateTime.parse('2026-02-18T00:00:01').withSecond(10)", LocalDateTime.parse("2026-02-18T00:00:10"));
    test("LocalDateTime.parse('2026-02-18T00:00:20').withSecond(second:0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:30').withSecond; f(0)", LocalDateTime.parse("2026-02-18T00:00:00"));
    test("def f = LocalDateTime.parse('2026-02-18T00:00:04').withSecond; f(second:11)", LocalDateTime.parse("2026-02-18T00:00:11"));
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withSecond(-1)", "invalid value");
    testError("LocalDateTime.parse('2026-02-18T00:00:00').withSecond(60)", "invalid value");
  }

  @Test public void format() {
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T13:13:14'); d.toString() == d.format(\"yyyy-MM-dd'T'HH:mm:ss\")", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T13:13:14'); '01:13:14' == d.format('hh:mm:ss')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T13:13:14'); '01:13:14' == d.format(format:'hh:mm:ss')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = d.format; d.toString() == f('yyyy-MM-dd\\'T\\'HH:mm:ss')", true);
    test("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); def f = d.format; d.toString() == f(format:'yyyy-MM-dd\\'T\\'HH:mm:ss')", true);
    testError("LocalDateTime d = LocalDateTime.parse('2026-02-18T12:13:14'); d.toString() == d.format('iiii-MM-dd')", "unknown pattern letter");
  }

  @Test public void parseWithFormat() {
    test("LocalDateTime.parseWithFormat('2026-02-18T12/13/14','yyyy-MM-dd\\'T\\'HH/mm/ss')", LocalDateTime.parse("2026-02-18T12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ss")));
    test("def f = LocalDateTime.parseWithFormat; f('2026-02-18T12/13/14','yyyy-MM-dd\\'T\\'HH/mm/ss')", LocalDateTime.parse("2026-02-18T12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ss")));
    test("LocalDateTime.parseWithFormat(text:'2026-02-18T12/13/14',format:'yyyy-MM-dd\\'T\\'HH/mm/ss')", LocalDateTime.parse("2026-02-18T12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ss")));
    test("def f = LocalDateTime.parseWithFormat; f(text:'2026-02-18T12/13/14',format:'yyyy-MM-dd\\'T\\'HH/mm/ss')", LocalDateTime.parse("2026-02-18T12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ss")));
    test("LocalDateTime.parseWithFormat('2026-01-02:12/13/14','yyyy-MM-dd:HH/mm/ss')", LocalDateTime.parse("2026-01-02:12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd:HH/mm/ss")));
    testError("LocalDateTime t = LocalDateTime.parseWithFormat('2026-02-18T13/13/14','yyyy-MM-dd\\'T\\'hh/mm/ss')", "invalid value");
    testError("LocalDateTime t = LocalDateTime.parseWithFormat('13/13/14','iiihh/mm/ss')", "unknown pattern letter");
  }

  @Test public void truncatedToMicros() {
    test("LocalDateTime.ofEpochSecond(1,123456789).truncatedToMicros()", LocalDateTime.ofEpochSecond(1, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
    test("def f = LocalDateTime.ofEpochSecond; f(1, 123456789).truncatedToMicros()", LocalDateTime.ofEpochSecond(1,123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
    test("def f = LocalDateTime.ofEpochSecond(1,123456789).truncatedToMicros; f()", LocalDateTime.ofEpochSecond(1,123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
  }

  @Test public void truncatedToMillis() {
    test("LocalDateTime.ofEpochSecond(1,123456789).truncatedToMillis()", LocalDateTime.ofEpochSecond(1,123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
    test("def f = LocalDateTime.ofEpochSecond; f(1, 123456789).truncatedToMillis()", LocalDateTime.ofEpochSecond(1,123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
    test("def f = LocalDateTime.ofEpochSecond(1,123456789).truncatedToMillis; f()", LocalDateTime.ofEpochSecond(1,123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
  }

  @Test public void truncatedToSeconds() {
    test("LocalDateTime.ofEpochSecond(1, 123456789).truncatedToSeconds()", LocalDateTime.ofEpochSecond(1, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    test("def f = LocalDateTime.ofEpochSecond; f(1, 123456789).truncatedToSeconds()", LocalDateTime.ofEpochSecond(1, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    test("def f = LocalDateTime.ofEpochSecond(1, 123456789).truncatedToSeconds; f()", LocalDateTime.ofEpochSecond(1, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
  }

  @Test public void truncatedToMinutes() {
    test("LocalDateTime.ofEpochSecond(123, 123456789).truncatedToMinutes()", LocalDateTime.ofEpochSecond(123L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
    test("def f = LocalDateTime.ofEpochSecond; f(123, 123456789).truncatedToMinutes()", LocalDateTime.ofEpochSecond(123L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
    test("def f = LocalDateTime.ofEpochSecond(123, 123456789).truncatedToMinutes; f()", LocalDateTime.ofEpochSecond(123L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
  }

  @Test public void truncatedToHours() {
    test("LocalDateTime.ofEpochSecond(7234, 123456789).truncatedToHours()", LocalDateTime.ofEpochSecond(7234L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS));
    test("def f = LocalDateTime.ofEpochSecond; f(7234, 123456789).truncatedToHours()", LocalDateTime.ofEpochSecond(7234L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS));
    test("def f = LocalDateTime.ofEpochSecond(7234, 123456789).truncatedToHours; f()", LocalDateTime.ofEpochSecond(7234L, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS));
  }
  
  @Test public void truncatedToDays() {
    test("LocalDateTime.ofEpochSecond(87400 * 7234, 123456789).truncatedToDays()", LocalDateTime.ofEpochSecond(87400*7234, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS));
    test("def f = LocalDateTime.ofEpochSecond; f(87400 * 7234, 123456789).truncatedToDays()", LocalDateTime.ofEpochSecond(87400*7234, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS));
    test("def f = LocalDateTime.ofEpochSecond(87400 * 7234, 123456789).truncatedToDays; f()", LocalDateTime.ofEpochSecond(87400*7234, 123456789, ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS));
  }
  
  @Test public void toStringTest() {
    test("LocalDateTime.parse('2026-02-18T12:13:14').toString()", "2026-02-18T12:13:14");
    test("def f = LocalDateTime.parse('2026-02-18T12:13:14').toString; f()", "2026-02-18T12:13:14");
  }
}
