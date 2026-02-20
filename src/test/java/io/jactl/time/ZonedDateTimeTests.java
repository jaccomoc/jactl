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

/**
 * Tests for ZonedDateTime class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have actually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class ZonedDateTimeTests extends BaseTest {

  @Test public void instanceOfTest() {
    test("ZonedDateTime.parse('2026-02-18T12:11:10+00:00') instanceof ZonedDateTime", true);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d instanceof ZonedDateTime", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d instanceof ZonedDateTime", true);
    test("ZonedDateTime.parse('2026-02-18T12:13:14+10:00[Australia/Sydney]') instanceof jactl.time.ZonedDateTime", true);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d instanceof jactl.time.ZonedDateTime", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d instanceof jactl.time.ZonedDateTime", true);
    globals.put("d", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    test("d instanceof ZonedDateTime", true);
  }

  @Test public void format() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.toString() == d.format(\"yyyy-MM-dd'T'HH:mm:ss'Z'\")", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); '01:13:14' == d.format('hh:mm:ss')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); '01:13:14' == d.format(format:'hh:mm:ss')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = d.format; d.toString() == f('yyyy-MM-dd\\'T\\'HH:mm:ss\\'Z\\'')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = d.format; d.toString() == f(format:'yyyy-MM-dd\\'T\\'HH:mm:ss\\'Z\\'')", true);
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.toString() == d.format('iiii-MM-dd')", "unknown pattern letter");
  }

  @Test public void getDayOfMonth() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getDayOfMonth()", 2);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); def f = d.getDayOfMonth; f()", 2);
    test("def d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getDayOfMonth()", 2);
    test("def d = ZonedDateTime.parse('2026-01-12T12:11:13Z'); d.getDayOfMonth()", 12);
    test("def d = ZonedDateTime.parse('2026-01-12T12:11:13Z'); def f = d.getDayOfMonth; f()", 12);
  }

  @Test public void getDayOfYear() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getDayOfYear()", 2);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); def f = d.getDayOfYear; f()", 2);
    test("def d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getDayOfYear()", 2);
    test("def d = ZonedDateTime.parse('2026-01-12T12:11:13Z'); d.getDayOfYear()", 12);
    test("def d = ZonedDateTime.parse('2026-02-12T12:11:13Z'); d.getDayOfYear()", 43);
    test("def d = ZonedDateTime.parse('2026-01-12T12:11:13Z'); def f = d.getDayOfYear; f()", 12);
  }

  @Test public void getDayOfWeek() {
    test("def d = ZonedDateTime.parse('2026-02-16T12:11:13Z'); d.getDayOfWeek()", "MONDAY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-20T12:11:13Z'); d.getDayOfWeek()", "TUESDAY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:11:13Z'); d.getDayOfWeek()", "WEDNESDAY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:11:13Z'); d.getDayOfWeek()", "THURSDAY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:11:13Z'); def f = d.getDayOfWeek; f()", "THURSDAY");
    test("def d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); d.getDayOfWeek()", "FRIDAY");
    test("def d = ZonedDateTime.parse('2026-01-24T12:11:13Z'); d.getDayOfWeek()", "SATURDAY");
    test("def d = ZonedDateTime.parse('2026-01-25T12:11:13Z'); d.getDayOfWeek()", "SUNDAY");
    test("def d = ZonedDateTime.parse('2026-01-25T12:11:13Z'); def f = d.getDayOfWeek; f()", "SUNDAY");
  }

  @Test void getMonth() {
    test("def d = ZonedDateTime.parse('2026-01-19T12:11:13Z'); d.getMonth()", "JANUARY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-20T12:11:13Z'); d.getMonth()", "FEBRUARY");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-03-21T12:11:13Z'); d.getMonth()", "MARCH");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-04-22T12:11:13Z'); d.getMonth()", "APRIL");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-05-22T12:11:13Z'); def f = d.getMonth; f()", "MAY");
    test("def d = ZonedDateTime.parse('2026-06-23T12:11:13Z'); d.getMonth()", "JUNE");
    test("def d = ZonedDateTime.parse('2026-07-24T12:11:13Z'); d.getMonth()", "JULY");
    test("def d = ZonedDateTime.parse('2026-08-25T12:11:13Z'); d.getMonth()", "AUGUST");
    test("def d = ZonedDateTime.parse('2026-09-25T12:11:13Z'); def f = d.getMonth; f()", "SEPTEMBER");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-10-22T12:11:13Z'); d.getMonth()", "OCTOBER");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-11-22T12:11:13Z'); d.getMonth()", "NOVEMBER");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-12-22T12:11:13Z'); d.getMonth()", "DECEMBER");
  }

  @Test void getMonthValue() {
    test("def d = ZonedDateTime.parse('2026-01-19T12:11:13Z'); d.getMonthValue()", 1);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-20T12:11:13Z'); d.getMonthValue()", 2);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-03-21T12:11:13Z'); d.getMonthValue()", 3);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-04-22T12:11:13Z'); d.getMonthValue()", 4);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-05-22T12:11:13Z'); def f = d.getMonthValue; f()", 5);
    test("def d = ZonedDateTime.parse('2026-06-23T12:11:13Z'); d.getMonthValue()", 6);
    test("def d = ZonedDateTime.parse('2026-07-24T12:11:13Z'); d.getMonthValue()", 7);
    test("def d = ZonedDateTime.parse('2026-08-25T12:11:13Z'); d.getMonthValue()", 8);
    test("def d = ZonedDateTime.parse('2026-09-25T12:11:13Z'); def f = d.getMonthValue; f()", 9);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-10-22T12:11:13Z'); d.getMonthValue()", 10);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-11-22T12:11:13Z'); d.getMonthValue()", 11);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-12-22T12:11:13Z'); d.getMonthValue()", 12);
  }

  @Test public void getYear() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getYear()", 2026);
    test("ZonedDateTime d = ZonedDateTime.parse('2027-01-02T12:11:13Z'); def f = d.getYear; f()", 2027);
    test("def d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getYear()", 2026);
    test("def d = ZonedDateTime.parse('1970-01-12T12:11:13Z'); d.getYear()", 1970);
    test("def d = ZonedDateTime.parse('1969-02-12T12:11:13Z'); d.getYear()", 1969);
    test("def d = ZonedDateTime.parse('1900-01-12T12:11:13Z'); def f = d.getYear; f()", 1900);
  }

  @Test void getHour() {
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.getHour()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T22:13:14Z'); d.getHour()", 22);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = d.getHour; f()", 12);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = d.getHour; f()", 12);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getHour()", 12);
  }

  @Test void getMinute() {
    test("def d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T22:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T11:13:14Z'); def f = d.getMinute; f()", 13);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14Z'); d.getMinute()", 13);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14Z'); d.getMinute()", 13);
    test("def d = ZonedDateTime.parse('2026-02-18T00:13:14Z'); d.getMinute()", 13);
    test("def d = ZonedDateTime.parse('2026-02-18T02:13:14Z'); def f = d.getMinute; f()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getMinute()", 13);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getMinute()", 13);
  }

  @Test void getSecond() {
    test("def d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T14:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T22:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T11:13:14Z'); def f = d.getSecond; f()", 14);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14Z'); d.getSecond()", 14);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14Z'); d.getSecond()", 14);
    test("def d = ZonedDateTime.parse('2026-02-18T00:13:14Z'); d.getSecond()", 14);
    test("def d = ZonedDateTime.parse('2026-02-18T02:13:14Z'); def f = d.getSecond; f()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getSecond()", 14);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); d.getSecond()", 14);
  }

  @Test void getNano() {
    test("def d = ZonedDateTime.parse('2026-02-18T13:13:14Z'); d.getNano()", 0);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T14:13:14.123456789Z'); d.getNano()", 123456789);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T22:13:14.123456789Z'); d.getNano()", 123456789);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T11:13:14.123456789Z'); def f = d.getNano; f()", 123456789);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14.123456789Z'); d.getNano()", 123456789);
    test("def d = ZonedDateTime.parse('2026-02-18T03:13:14.123456789Z'); d.getNano()", 123456789);
    test("def d = ZonedDateTime.parse('2026-02-18T00:13:14.123456Z'); d.getNano()", 123456000);
    test("def d = ZonedDateTime.parse('2026-02-18T02:13:14.1234567Z'); def f = d.getNano; f()", 123456700);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); d.getNano()", 123456789);
  }

//  @Test public void getOffset() {
//    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getOffset()", "Z");
//    test("ZonedDateTime d = ZonedDateTime.parse('2027-01-02T12:11:13+10:00'); def f = d.getOffset; f()", "+10:00");
//    test("def d = ZonedDateTime.parse('2026-01-02T12:11:13+10:00[Australia/Sydney]'); d.getOffset()", "+10:00[Australia/Sydney]");
//    test("def d = ZonedDateTime.parse('1970-01-12T12:11:13Z'); d.getOffset()", 1970);
//    test("def d = ZonedDateTime.parse('1969-02-12T12:11:13Z'); d.getOffset()", 1969);
//    test("def d = ZonedDateTime.parse('1900-01-12T12:11:13Z'); def f = d.getOffset; f()", 1900);
//  }

  @Test public void getZone() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-02T12:11:13Z'); d.getZone()", ZoneId.of("Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2027-01-02T12:11:13+10:00'); def f = d.getZone; f()", ZoneId.of("+10:00"));
    test("def d = ZonedDateTime.parse('2026-01-02T12:11:13+10:00[Australia/Sydney]'); d.getZone()", ZoneId.of("Australia/Sydney"));
    test("def d = ZonedDateTime.parse('1970-01-12T12:11:13Z'); d.getZone()", ZoneId.of("Z"));
    test("def d = ZonedDateTime.parse('1900-01-12T12:11:13Z'); def f = d.getZone; f()", ZoneId.of("Z"));
  }

  @Test public void equalsTest() {
    test("ZonedDateTime now = ZonedDateTime.now(); now == now", true);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime.parse('2026-02-18T12:13:13Z') == d", false);
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime.parse('2026-02-18T12:13:14Z') == d", true);
  }

  @Test public void minus() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').minus(Duration.ofSeconds(0))", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').minus(Duration.ofSeconds(1))", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("TemporalAmount p = Duration.ofSeconds(1); ZonedDateTime.parse('2026-02-18T12:13:14Z').minus(p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); d.minus(p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("def p = Duration.ofSeconds(1); ZonedDateTime.parse('2026-02-18T12:13:14Z').minus(p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); d.minus(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:13Z"));
  }

  @Test public void minusHours() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusHours(-1); d2 == ZonedDateTime.parse('2026-02-18T13:13:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusHours(1); d2 == ZonedDateTime.parse('2026-02-18T11:13:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusHours(24); d2 == ZonedDateTime.parse('2026-02-17T12:13:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusHours(0); d2 == ZonedDateTime.parse('2026-02-18T12:13:14Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusHours(1); ZonedDateTime d2 = now.minusHours(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusHours(1); ZonedDateTime d2 = now.minusHours(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusHours(1); def d2 = now.minusHours(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusHours(1); ZonedDateTime d2 = now.minusHours(hours:1); d1 == d2", true);
  }

  @Test public void minusMinutes() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusMinutes(-1); d2 == ZonedDateTime.parse('2026-02-18T12:14:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusMinutes(1); d2 == ZonedDateTime.parse('2026-02-18T12:12:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusMinutes(24*60); d2 == ZonedDateTime.parse('2026-02-17T12:13:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusMinutes(0); d2 == ZonedDateTime.parse('2026-02-18T12:13:14Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMinutes(1); ZonedDateTime d2 = now.minusMinutes(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMinutes(1); ZonedDateTime d2 = now.minusMinutes(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusMinutes(1); def d2 = now.minusMinutes(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMinutes(1); ZonedDateTime d2 = now.minusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void minusNanos() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.000000001Z'); ZonedDateTime d2 = d.minusNanos(-1); d2 == ZonedDateTime.parse('2026-02-18T12:13:14.000000002Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.000000001Z'); ZonedDateTime d2 = d.minusNanos(1); d2 == ZonedDateTime.parse('2026-02-18T12:13:14.00Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.000000001Z'); ZonedDateTime d2 = d.minusNanos(24*60*60*1000000000L); d2 == ZonedDateTime.parse('2026-02-17T12:13:14.000000001Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14.000000001Z'); ZonedDateTime d2 = d.minusNanos(0); d2 == ZonedDateTime.parse('2026-02-18T12:13:14.000000001Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusNanos(1); ZonedDateTime d2 = now.minusNanos(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusNanos(1); ZonedDateTime d2 = now.minusNanos(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusNanos(1); def d2 = now.minusNanos(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusNanos(1); ZonedDateTime d2 = now.minusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void minusSeconds() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusSeconds(-1); d2 == ZonedDateTime.parse('2026-02-18T12:13:15Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusSeconds(1); d2 == ZonedDateTime.parse('2026-02-18T12:13:13Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusSeconds(24*60*60); d2 == ZonedDateTime.parse('2026-02-17T12:13:14Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); ZonedDateTime d2 = d.minusSeconds(0); d2 == ZonedDateTime.parse('2026-02-18T12:13:14Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusSeconds(1); ZonedDateTime d2 = now.minusSeconds(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusSeconds(1); ZonedDateTime d2 = now.minusSeconds(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusSeconds(1); def d2 = now.minusSeconds(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusSeconds(1); ZonedDateTime d2 = now.minusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void minusDays() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusDays(1); d2 == ZonedDateTime.parse('2026-01-22T12:11:13Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusDays(24); d2 == ZonedDateTime.parse('2025-12-30T12:11:13Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusDays(1); ZonedDateTime d2 = now.minusDays(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusDays(1); ZonedDateTime d2 = now.minusDays(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusDays(1); ZonedDateTime d2 = now.minusDays(days:1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(days:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.minusDays(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
  }

  @Test public void minusMonths() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusMonths(1); d2 == ZonedDateTime.parse('2025-12-23T12:11:13Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusMonths(24); d2 == ZonedDateTime.parse('2024-01-23T12:11:13Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMonths(1); ZonedDateTime d2 = now.minusMonths(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMonths(1); ZonedDateTime d2 = now.minusMonths(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusMonths(1); def d2 = now.minusMonths(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusMonths(1); ZonedDateTime d2 = now.minusMonths(months:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.minusMonths(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void minusWeeks() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusWeeks(1); d2 == ZonedDateTime.parse('2026-01-16T12:11:13Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusWeeks(4); d2 == ZonedDateTime.parse('2025-12-26T12:11:13Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusWeeks(1); ZonedDateTime d2 = now.minusWeeks(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusWeeks(1); ZonedDateTime d2 = now.minusWeeks(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusWeeks(1); def d2 = now.minusWeeks(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusWeeks(1); ZonedDateTime d2 = now.minusWeeks(weeks:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.minusWeeks(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
  }

  @Test public void minusYears() {
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusYears(1); d2 == ZonedDateTime.parse('2025-01-23T12:11:13Z')", true);
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-23T12:11:13Z'); ZonedDateTime d2 = d.minusYears(4); d2 == ZonedDateTime.parse('2022-01-23T12:11:13Z')", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusYears(1); ZonedDateTime d2 = now.minusYears(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusYears(1); ZonedDateTime d2 = now.minusYears(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.minusYears(1); def d2 = now.minusYears(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.minusYears(1); ZonedDateTime d2 = now.minusYears(years:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.minusYears(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
  }

  @Test public void now() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime now2 = ZonedDateTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
    test("ZonedDateTime now = ZonedDateTime.now(); def f = now.now; ZonedDateTime now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def f = ZonedDateTime.now; ZonedDateTime now = f(); ZonedDateTime now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def now = ZonedDateTime.now(); def now2 = ZonedDateTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def now = ZonedDateTime.now(); def f = now.now; def now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
  }

  @Test public void nowInZone() {
    test("(ZonedDateTime.nowInZone(ZoneId.of('Australia/Sydney')).getSecond() + 5)/10", (ZonedDateTime.now(ZoneId.of("Australia/Sydney")).getSecond() + 5) / 10);
    test("def f = ZonedDateTime.nowInZone; (f(ZoneId.of('Australia/Sydney')).getSecond() + 5) / 10", (ZonedDateTime.now(ZoneId.of("Australia/Sydney")).getSecond() + 5) / 10);
  }

  @Test public void of() {
    test("ZonedDateTime t = ZonedDateTime.of(2026,02,18,12,11,13,123456789, ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.of; f(2026,02,18,12,11,13,123456789, ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.of(year:2026,month:02,day:18,hour:12,minute:11,second:13,nano:123456789, zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.of; f(year:2026,month:02,day:18,hour:12,minute:11,second:13,nano:123456789, zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    testError("ZonedDateTime t = ZonedDateTime.of(2026,02,18,12)", "missing mandatory argument");
    testError("ZonedDateTime t = ZonedDateTime.of(2026,22,18,12,11,0,0,ZoneId.of('UTC'))", "invalid value");
  }
  
  @Test public void ofDateAndTime() {
    test("ZonedDateTime t = ZonedDateTime.ofDateAndTime(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofDateAndTime; f(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateAndTime(date:LocalDate.of(2026,02,18),time:LocalTime.of(12,11,13,123456789),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofDateAndTime; f(date:LocalDate.of(2026,02,18),time:LocalTime.of(12,11,13,123456789),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateAndTime(LocalDate.of(2027,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney')); t.ofDateAndTime(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateAndTime(LocalDate.of(2027,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney')); def f = t.ofDateAndTime; f(LocalDate.of(2026,02,18),LocalTime.of(12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
  }

  @Test public void ofDateTime() {
    test("ZonedDateTime t = ZonedDateTime.ofDateTime(LocalDateTime.of(2026,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofDateTime; f(LocalDateTime.of(2026,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateTime(dateTime:LocalDateTime.of(2026,02,18,12,11,13,123456789),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofDateTime; f(dateTime:LocalDateTime.of(2026,02,18,12,11,13,123456789),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateTime(LocalDateTime.of(2027,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney')); t.ofDateTime(LocalDateTime.of(2026,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofDateTime(LocalDateTime.of(2027,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney')); def f = t.ofDateTime; f(LocalDateTime.of(2026,02,18,12,11,13,123456789), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,12,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
  }

  @Test public void ofInstant() {
    test("ZonedDateTime t = ZonedDateTime.ofInstant(Instant.parse('2026-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofInstant; f(Instant.parse('2026-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofInstant(instant:Instant.parse('2026-02-18T12:11:13.123456789Z'),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("def f = ZonedDateTime.ofInstant; f(instant:Instant.parse('2026-02-18T12:11:13.123456789Z'),zoneId:ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofInstant(Instant.parse('2027-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney')); t.ofInstant(Instant.parse('2026-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
    test("ZonedDateTime t = ZonedDateTime.ofInstant(Instant.parse('2027-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney')); def f = t.ofInstant; f(Instant.parse('2026-02-18T12:11:13.123456789Z'), ZoneId.of('Australia/Sydney'))", ZonedDateTime.of(2026,02,18,23,11,13, 123456789,  ZoneId.of("Australia/Sydney")));
  }

  @Test public void parse() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z')", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    test("ZonedDateTime.parse(text:'2026-02-18T12:13:14Z')", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    test("def f = ZonedDateTime.parse; f('2026-02-18T12:13:14Z')", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    test("def f = ZonedDateTime.parse; f(text:'2026-02-18T12:13:14Z')", ZonedDateTime.parse("2026-02-18T12:13:14Z"));
    testError("ZonedDateTime.parse('2026-02-18T12:13:14:123')", "could not be parsed");
  }

  @Test public void plus() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').plus(Duration.ofSeconds(1))", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').plus(Period.ofDays(1))", ZonedDateTime.parse("2026-02-19T12:13:14Z"));
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').plus(Period.ofYears(1))", ZonedDateTime.parse("2027-02-18T12:13:14Z"));
    test("TemporalAmount p = Duration.ofSeconds(1); ZonedDateTime.parse('2026-02-18T12:13:14Z').plus(p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); d.plus(p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("def p = Duration.ofSeconds(1); ZonedDateTime.parse('2026-02-18T12:13:14Z').plus(p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); d.plus(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
    test("def d = ZonedDateTime.parse('2026-02-18T12:13:14Z'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", ZonedDateTime.parse("2026-02-18T12:13:15Z"));
  }

  @Test public void plusDays() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusDays(1); ZonedDateTime d2 = now.plusDays(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusDays(1); ZonedDateTime d2 = now.plusDays(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusDays(1); def d2 = now.plusDays(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusDays(1); ZonedDateTime d2 = now.plusDays(days:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.plusDays(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
  }

  @Test public void plusMonths() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMonths(1); ZonedDateTime d2 = now.plusMonths(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMonths(1); ZonedDateTime d2 = now.plusMonths(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusMonths(1); def d2 = now.plusMonths(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMonths(1); ZonedDateTime d2 = now.plusMonths(months:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.plusMonths(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void plusWeeks() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusWeeks(1); ZonedDateTime d2 = now.plusWeeks(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusWeeks(1); ZonedDateTime d2 = now.plusWeeks(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusWeeks(1); def d2 = now.plusWeeks(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusWeeks(1); ZonedDateTime d2 = now.plusWeeks(weeks:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.plusWeeks(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusWeeks(weeks:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusWeeks(weeks:1000000000000L)", "invalid value");
  }

  @Test public void plusYears() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusYears(1); ZonedDateTime d2 = now.plusYears(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusYears(1); ZonedDateTime d2 = now.plusYears(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusYears(1); def d2 = now.plusYears(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusYears(1); ZonedDateTime d2 = now.plusYears(years:1); d1 == d2", true);
    testError("def now = ZonedDateTime.now(); def d1 = now.plusYears(1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
    testError("def now = ZonedDateTime.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
  }

  @Test public void plusHours() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusHours(1); ZonedDateTime d2 = now.plusHours(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusHours(1); ZonedDateTime d2 = now.plusHours(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusHours(1); ZonedDateTime d2 = now.plusHours(hours:1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(hours:1); d1 == d2", true);
  }

  @Test public void plusMinutes() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMinutes(1); ZonedDateTime d2 = now.plusMinutes(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMinutes(1); ZonedDateTime d2 = now.plusMinutes(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusMinutes(1); ZonedDateTime d2 = now.plusMinutes(minutes:1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void plusNanos() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusNanos(1); ZonedDateTime d2 = now.plusNanos(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusNanos(1); ZonedDateTime d2 = now.plusNanos(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusNanos(1); ZonedDateTime d2 = now.plusNanos(nanos:1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void plusSeconds() {
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusSeconds(1); ZonedDateTime d2 = now.plusSeconds(2); d1 == d2", false);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusSeconds(1); ZonedDateTime d2 = now.plusSeconds(1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(1); d1 == d2", true);
    test("ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime d1 = now.plusSeconds(1); ZonedDateTime d2 = now.plusSeconds(seconds:1); d1 == d2", true);
    test("def now = ZonedDateTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void toLocalDate() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalDate() == LocalDate.parse('2026-02-18')", true);
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalDate; f() == LocalDate.parse('2026-02-18')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); t.toLocalDate() == LocalDate.parse('2026-02-18')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = t.toLocalDate; f() == LocalDate.parse('2026-02-18')", true);
  }

  @Test public void toLocalTime() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalTime() == LocalTime.parse('12:13:14')", true);
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalTime; f() == LocalTime.parse('12:13:14')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); t.toLocalTime() == LocalTime.parse('12:13:14')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = t.toLocalTime; f() == LocalTime.parse('12:13:14')", true);
  }

  @Test public void toLocalDateTime() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalDateTime() == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14Z').toLocalDateTime; f() == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); t.toLocalDateTime() == LocalDateTime.parse('2026-02-18T12:13:14')", true);
    test("def t = ZonedDateTime.parse('2026-02-18T12:13:14Z'); def f = t.toLocalDateTime; f() == LocalDateTime.parse('2026-02-18T12:13:14')", true);
  }

  @Test public void truncatedToMicros() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMicros()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MICROS));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMicros; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MICROS));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToMicros; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MICROS));
  }

  @Test public void truncatedToMillis() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMillis()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MILLIS));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMillis; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MILLIS));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToMillis; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MILLIS));
  }

  @Test public void truncatedToSeconds() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToSeconds()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.SECONDS));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToSeconds; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.SECONDS));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToSeconds; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.SECONDS));
  }

  @Test public void truncatedToMinutes() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMinutes()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MINUTES));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToMinutes; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MINUTES));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToMinutes; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.MINUTES));
  }

  @Test public void truncatedToHours() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToHours()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.HOURS));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToHours; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.HOURS));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToHours; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.HOURS));
  }

  @Test public void truncatedToDays() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToDays()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.DAYS));
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z').truncatedToDays; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.DAYS));
    test("def z = ZonedDateTime.parse('2026-02-18T12:13:14.123456789Z'); def f = z.truncatedToDays; f()", ZonedDateTime.parse("2026-02-18T12:13:14.123456789Z").truncatedTo(ChronoUnit.DAYS));
  }

  @Test public void withDayOfMonth() {
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfMonth(222)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfMonth(22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-02-21T12:13:14Z'); d.withDayOfMonth(30)", "invalid date");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfMonth(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfMonth; f(22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfMonth; f(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfMonth; f(22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfMonth; f(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
  }

  @Test public void withDayOfYear() {
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfYear(2222)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfYear(42)", ZonedDateTime.parse("2026-02-11T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2020-01-21T12:13:14Z'); d.withDayOfYear(366)", ZonedDateTime.parse("2020-12-31T12:13:14Z"));
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-02-21T12:13:14Z'); d.withDayOfYear(366)", "invalid date");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfYear(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfYear; f(22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfYear; f(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfYear; f(22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); def f = d.withDayOfYear; f(day:22)", ZonedDateTime.parse("2026-01-22T12:13:14Z"));
  }

  @Test public void withMonth() {
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withDayOfMonth(2222)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withMonth(12)", ZonedDateTime.parse("2026-12-21T12:13:14Z"));
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-02-21T12:13:14Z'); d.withMonth(30)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); d.withMonth(month:2)", ZonedDateTime.parse("2026-02-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withMonth; f(2)", ZonedDateTime.parse("2026-02-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withMonth; f(month:2)", ZonedDateTime.parse("2026-02-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withMonth; f(2)", ZonedDateTime.parse("2026-02-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withMonth; f(month:2)", ZonedDateTime.parse("2026-02-22T12:13:14Z"));
  }

  @Test public void withYear() {
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withYear(-22222222222222222L)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withYear(1969)", ZonedDateTime.parse("1969-01-21T12:13:14Z"));
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-02-21T12:13:14Z'); d.withYear(3000000000000L)", "invalid value");
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); d.withYear(year:1969)", ZonedDateTime.parse("1969-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withYear; f(1969)", ZonedDateTime.parse("1969-01-22T12:13:14Z"));
    test("ZonedDateTime d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withYear; f(year:1969)", ZonedDateTime.parse("1969-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withYear; f(1969)", ZonedDateTime.parse("1969-01-22T12:13:14Z"));
    test("def d = ZonedDateTime.parse('2026-01-22T12:13:14Z'); def f = d.withYear; f(year:1969)", ZonedDateTime.parse("1969-01-22T12:13:14Z"));
  }

  @Test public void withHour() {
    test("ZonedDateTime.parse('2026-02-18T00:00:00Z').withHour(10)", ZonedDateTime.parse("2026-02-18T10:00:00Z"));
    test("ZonedDateTime.parse('2026-02-18T00:00:00Z').withHour(hour:0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00Z').withHour; f(0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00Z').withHour; f(hour:0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withHour(24)", "invalid value");
  }

  @Test public void withMinute() {
    testError("ZonedDateTime d = ZonedDateTime.parse('2026-01-21T12:13:14Z'); d.withMinute(222)", "invalid value");
    test("ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute(10)", ZonedDateTime.parse("2026-02-18T00:10:00Z"));
    test("ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute(minute:0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute; f(0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute; f(minute:11)", ZonedDateTime.parse("2026-02-18T00:11:00Z"));
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute(-1)", "invalid value");
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withMinute(60)", "invalid value");
  }

  @Test public void withNano() {
    test("ZonedDateTime.parse('2026-02-18T00:00:00Z').withNano(10)", ZonedDateTime.parse("2026-02-18T00:00:00.00000001Z"));
    test("ZonedDateTime.parse('2026-02-18T00:00:00.123Z').withNano(nano:0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00.123Z').withNano; f(0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:00.123Z').withNano; f(nano:11)", ZonedDateTime.parse("2026-02-18T00:00:00.000000011Z"));
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withNano(-1)", "invalid value");
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withNano(6000000000L)", "invalid value");
  }

  @Test public void withSecond() {
    test("ZonedDateTime.parse('2026-02-18T00:00:01Z').withSecond(10)", ZonedDateTime.parse("2026-02-18T00:00:10Z"));
    test("ZonedDateTime.parse('2026-02-18T00:00:20Z').withSecond(second:0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:30Z').withSecond; f(0)", ZonedDateTime.parse("2026-02-18T00:00:00Z"));
    test("def f = ZonedDateTime.parse('2026-02-18T00:00:04Z').withSecond; f(second:11)", ZonedDateTime.parse("2026-02-18T00:00:11Z"));
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withSecond(-1)", "invalid value");
    testError("ZonedDateTime.parse('2026-02-18T00:00:00Z').withSecond(60)", "invalid value");
  }
  
  @Test public void withEarlierOffsetAtOverlap() {
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]').withEarlierOffsetAtOverlap()", ZonedDateTime.parse("2026-04-05T02:30+11:00[Australia/Sydney]"));
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]'); def f = z.withEarlierOffsetAtOverlap; f()", ZonedDateTime.parse("2026-04-05T02:30+11:00[Australia/Sydney]"));
  }
  
  @Test public void withLaterOffsetAtOverlap() {
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]').withLaterOffsetAtOverlap()", ZonedDateTime.parse("2026-04-05T02:30+10:00[Australia/Sydney]").withLaterOffsetAtOverlap());
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]'); def f = z.withLaterOffsetAtOverlap; f()", ZonedDateTime.parse("2026-04-05T02:30+10:00[Australia/Sydney]").withLaterOffsetAtOverlap());
  }

  @Test public void withFixedOffsetZone() {
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T03:30+10:00[Australia/Sydney]').withFixedOffsetZone()", ZonedDateTime.parse("2026-04-05T03:30+10:00[Australia/Sydney]").withFixedOffsetZone());
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]'); def f = z.withFixedOffsetZone; f()", ZonedDateTime.parse("2026-04-05T02:30+10:00[Australia/Sydney]").withFixedOffsetZone());
  }

  @Test public void withZoneSameInstant() {
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T03:30+10:00[Australia/Sydney]').withZoneSameInstant(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-04-05T03:30+10:00[Australia/Sydney]").withZoneSameInstant(ZoneId.of("UTC")));
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]').withZoneSameInstant(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-04-05T02:30+10:00[Australia/Sydney]").withZoneSameInstant(ZoneId.of("UTC")));
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T02:30+10:00[Australia/Sydney]'); def f = z.withZoneSameInstant; f(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-04-05T02:30+10:00[Australia/Sydney]").withZoneSameInstant(ZoneId.of("UTC")));
  }

  @Test public void withZoneSameLocal() {
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T03:30+10:00[Australia/Sydney]').withZoneSameLocal(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-04-05T03:30+10:00[Australia/Sydney]").withZoneSameLocal(ZoneId.of("UTC")));
    test("ZonedDateTime z = ZonedDateTime.parse('2026-04-05T03:30+10:00[Australia/Sydney]'); def f = z.withZoneSameLocal; f(ZoneId.of('UTC'))", ZonedDateTime.parse("2026-04-05T03:30+10:00[Australia/Sydney]").withZoneSameLocal(ZoneId.of("UTC"))); 
  }

  @Test public void parseWithFormat() {
    test("ZonedDateTime.parseWithFormat('2026-02-18T12/13/14Z','yyyy-MM-dd\\'T\\'HH/mm/ssX')", ZonedDateTime.parse("2026-02-18T12/13/14Z", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ssX")));
    test("def f = ZonedDateTime.parseWithFormat; f('2026-02-18T12/13/14Z','yyyy-MM-dd\\'T\\'HH/mm/ssX')", ZonedDateTime.parse("2026-02-18T12/13/14Z", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ssX")));
    test("ZonedDateTime.parseWithFormat(text:'2026-02-18T12/13/14Z',format:'yyyy-MM-dd\\'T\\'HH/mm/ssX')", ZonedDateTime.parse("2026-02-18T12/13/14Z", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ssX")));
    test("def f = ZonedDateTime.parseWithFormat; f(text:'2026-02-18T12/13/14Z',format:'yyyy-MM-dd\\'T\\'HH/mm/ssX')", ZonedDateTime.parse("2026-02-18T12/13/14Z", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH/mm/ssX")));
    test("ZonedDateTime.parseWithFormat('2026-01-02:12/13/14Z','yyyy-MM-dd:HH/mm/ssX')", ZonedDateTime.parse("2026-01-02:12/13/14Z", DateTimeFormatter.ofPattern("yyyy-MM-dd:HH/mm/ssX")));
    testError("ZonedDateTime t = ZonedDateTime.parseWithFormat('2026-02-18T13/13/14Z','yyyy-MM-dd\\'T\\'hh/mm/ssX')", "invalid value");
    testError("ZonedDateTime t = ZonedDateTime.parseWithFormat('13/13/14','iiihh/mm/ss')", "unknown pattern letter");
  }

  @Test public void toStringTest() {
    test("ZonedDateTime.parse('2026-02-18T12:13:14Z').toString()", "2026-02-18T12:13:14Z");
    test("def f = ZonedDateTime.parse('2026-02-18T12:13:14Z').toString; f()", "2026-02-18T12:13:14Z");
  }

}
