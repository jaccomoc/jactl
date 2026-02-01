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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Tests for LocalTime class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have atually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class LocalTimeTests extends BaseTest {

  @Test public void instanceOfTest() {
    test("LocalTime.parse('12:11:10') instanceof LocalTime", true);
    test("def d = LocalTime.parse('12:13:14'); d instanceof LocalTime", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); d instanceof LocalTime", true);
    test("LocalTime.parse('12:13:14') instanceof jactl.time.LocalTime", true);
    test("def d = LocalTime.parse('12:13:14'); d instanceof jactl.time.LocalTime", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); d instanceof jactl.time.LocalTime", true);
    globals.put("d", LocalTime.parse("12:13:14"));
    test("d instanceof LocalTime", true);
  }
  
  @Test public void atDate() {
    test("LocalTime.parse('12:13:14').atDate(LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("def t = LocalTime.parse('12:13:14'); t.atDate(LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("def t = LocalTime.parse('12:13:14'); def f = t.atDate; f(LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("LocalTime t = LocalTime.parse('12:13:14'); t.atDate(LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("LocalTime t = LocalTime.parse('12:13:14'); t.atDate(date:LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("def t = LocalTime.parse('12:13:14'); t.atDate(date:LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("LocalTime t = LocalTime.parse('12:13:14'); def f = t.atDate; f(LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("LocalTime t = LocalTime.parse('12:13:14'); def f = t.atDate; f(date:LocalDate.parse('2026-01-30'))", LocalTime.parse("12:13:14").atDate(LocalDate.parse("2026-01-30"))); 
    test("LocalTime t = LocalTime.parse('22:13:14'); def f = t.atDate; f(date:LocalDate.parse('2026-01-30'))", LocalTime.parse("22:13:14").atDate(LocalDate.parse("2026-01-30"))); 
  }
  
  @Test void getHour() {
    test("def d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("LocalTime d = LocalTime.parse('13:13:14'); d.getHour()", 13);
    test("LocalTime d = LocalTime.parse('22:13:14'); d.getHour()", 22);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("LocalTime d = LocalTime.parse('12:13:14'); def f = d.getHour; f()", 12);
    test("def d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("def d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("def d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("def d = LocalTime.parse('12:13:14'); def f = d.getHour; f()", 12);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getHour()", 12);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getHour()", 12);
  }

  @Test void getMinute() {
    test("def d = LocalTime.parse('13:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('13:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('22:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('11:13:14'); def f = d.getMinute; f()", 13);
    test("def d = LocalTime.parse('03:13:14'); d.getMinute()", 13);
    test("def d = LocalTime.parse('03:13:14'); d.getMinute()", 13);
    test("def d = LocalTime.parse('00:13:14'); d.getMinute()", 13);
    test("def d = LocalTime.parse('02:13:14'); def f = d.getMinute; f()", 13);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getMinute()", 13);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getMinute()", 13);
  }

  @Test void getSecond() {
    test("def d = LocalTime.parse('13:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('14:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('22:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('11:13:14'); def f = d.getSecond; f()", 14);
    test("def d = LocalTime.parse('03:13:14'); d.getSecond()", 14);
    test("def d = LocalTime.parse('03:13:14'); d.getSecond()", 14);
    test("def d = LocalTime.parse('00:13:14'); d.getSecond()", 14);
    test("def d = LocalTime.parse('02:13:14'); def f = d.getSecond; f()", 14);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getSecond()", 14);
    test("LocalTime d = LocalTime.parse('12:13:14'); d.getSecond()", 14);
  }

  @Test void getNano() {
    test("def d = LocalTime.parse('13:13:14'); d.getNano()", 0);
    test("LocalTime d = LocalTime.parse('14:13:14.123456789'); d.getNano()", 123456789);
    test("LocalTime d = LocalTime.parse('22:13:14.123456789'); d.getNano()", 123456789);
    test("LocalTime d = LocalTime.parse('12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalTime d = LocalTime.parse('11:13:14.123456789'); def f = d.getNano; f()", 123456789);
    test("def d = LocalTime.parse('03:13:14.123456789'); d.getNano()", 123456789);
    test("def d = LocalTime.parse('03:13:14.123456789'); d.getNano()", 123456789);
    test("def d = LocalTime.parse('00:13:14.123456'); d.getNano()", 123456000);
    test("def d = LocalTime.parse('02:13:14.1234567'); def f = d.getNano; f()", 123456700);
    test("LocalTime d = LocalTime.parse('12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalTime d = LocalTime.parse('12:13:14.123456789'); d.getNano()", 123456789);
    test("LocalTime d = LocalTime.parse('12:13:14.123456789'); d.getNano()", 123456789);
  }


  @Test public void isAfter() {
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:14.1').isAfter(d)", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:14').isAfter(d)", false);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.of(12,13,15).isAfter(d)", true);
    test("def d = LocalTime.parse('12:13:14'); def f = LocalTime.parse('12:13:14.1').isAfter; f(d)", true);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:13').isAfter(d)", false);
  }

  @Test public void isBefore() {
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:14.1').isBefore(d)", false);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:14').isBefore(d)", false);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.of(12,13,15).isBefore(d)", false);
    test("def d = LocalTime.parse('12:13:14'); def f = LocalTime.parse('12:13:14.1').isBefore; f(d)", false);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:13').isBefore(d)", true);
  }


  @Test public void equalsTest() {
    test("LocalTime now = LocalTime.now(); now == now", true);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:13') == d", false);
    test("def d = LocalTime.parse('12:13:14'); LocalTime.parse('12:13:14') == d", true);
  }

  @Test public void minus() {
    test("LocalTime.parse('12:13:14').minus(Duration.ofSeconds(0))", LocalTime.parse("12:13:14"));
    test("LocalTime.parse('12:13:14').minus(Duration.ofSeconds(1))", LocalTime.parse("12:13:13"));
    test("TemporalAmount p = Duration.ofSeconds(1); LocalTime.parse('12:13:14').minus(p)", LocalTime.parse("12:13:13"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); d.minus(p)", LocalTime.parse("12:13:13"));
    test("def p = Duration.ofSeconds(1); LocalTime.parse('12:13:14').minus(p)", LocalTime.parse("12:13:13"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", LocalTime.parse("12:13:13"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); d.minus(amount:p)", LocalTime.parse("12:13:13"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", LocalTime.parse("12:13:13"));
    test("def d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(p)", LocalTime.parse("12:13:13"));
    test("def d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.minus; f(amount:p)", LocalTime.parse("12:13:13"));
  }

  @Test public void minusHours() {
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusHours(-1); d2 == LocalTime.parse('13:13:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusHours(1); d2 == LocalTime.parse('11:13:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusHours(24); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusHours(0); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusHours(1); LocalTime d2 = now.minusHours(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusHours(1); LocalTime d2 = now.minusHours(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.minusHours(1); def d2 = now.minusHours(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusHours(1); LocalTime d2 = now.minusHours(hours:1); d1 == d2", true);
  }

  @Test public void minusMinutes() {
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusMinutes(-1); d2 == LocalTime.parse('12:14:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusMinutes(1); d2 == LocalTime.parse('12:12:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusMinutes(24*60); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusMinutes(0); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusMinutes(1); LocalTime d2 = now.minusMinutes(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusMinutes(1); LocalTime d2 = now.minusMinutes(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.minusMinutes(1); def d2 = now.minusMinutes(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusMinutes(1); LocalTime d2 = now.minusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void minusNanos() {
    test("LocalTime d = LocalTime.parse('12:13:14.000000001'); LocalTime d2 = d.minusNanos(-1); d2 == LocalTime.parse('12:13:14.000000002')", true);
    test("LocalTime d = LocalTime.parse('12:13:14.000000001'); LocalTime d2 = d.minusNanos(1); d2 == LocalTime.parse('12:13:14.0')", true);
    test("LocalTime d = LocalTime.parse('12:13:14.000000001'); LocalTime d2 = d.minusNanos(24*60*60*1000000000L); d2 == LocalTime.parse('12:13:14.000000001')", true);
    test("LocalTime d = LocalTime.parse('12:13:14.000000001'); LocalTime d2 = d.minusNanos(0); d2 == LocalTime.parse('12:13:14.000000001')", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusNanos(1); LocalTime d2 = now.minusNanos(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusNanos(1); LocalTime d2 = now.minusNanos(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.minusNanos(1); def d2 = now.minusNanos(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusNanos(1); LocalTime d2 = now.minusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void minusSeconds() {
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusSeconds(-1); d2 == LocalTime.parse('12:13:15')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusSeconds(1); d2 == LocalTime.parse('12:13:13')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusSeconds(24*60*60); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); LocalTime d2 = d.minusSeconds(0); d2 == LocalTime.parse('12:13:14')", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusSeconds(1); LocalTime d2 = now.minusSeconds(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusSeconds(1); LocalTime d2 = now.minusSeconds(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.minusSeconds(1); def d2 = now.minusSeconds(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.minusSeconds(1); LocalTime d2 = now.minusSeconds(seconds:1); d1 == d2", true);
  }

  @Test public void now() {
    test("LocalTime now = LocalTime.now(); LocalTime now2 = LocalTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def f = LocalTime.now; LocalTime now = f(); LocalTime now2 = f(); Duration.between(now2,now).getSeconds() < 5", true);
    test("def now = LocalTime.now(); def now2 = LocalTime.now(); Duration.between(now2,now).getSeconds() < 5", true);
  }
  
  @Test public void nowInZone() {
    test("LocalTime.nowInZone(ZoneId.of('Australia/Sydney')).plusNanos(500000000L).truncatedToSeconds()", LocalTime.now(ZoneId.of("Australia/Sydney")).plusNanos(500000000L).truncatedTo(ChronoUnit.SECONDS));
    test("def f = LocalTime.nowInZone; f(ZoneId.of('Australia/Sydney')).plusNanos(500000000L).truncatedToSeconds()", LocalTime.now(ZoneId.of("Australia/Sydney")).plusNanos(500000000L).truncatedTo(ChronoUnit.SECONDS));
  }

  @Test public void ofNanoOfDay() {
    test("LocalTime.ofNanoOfDay(0) == LocalTime.parse('00:00:00.0')", true);
    test("LocalTime.ofNanoOfDay(1) == LocalTime.parse('00:00:00.000000001')", true);
    test("LocalTime.ofNanoOfDay(nanoOfDay:1000000000L*3600+1) == LocalTime.parse('01:00:00.000000001')", true);
    test("def f = LocalTime.ofNanoOfDay; f(10) == LocalTime.parse('00:00:00.00000001')", true);
    test("def f = LocalTime.ofNanoOfDay; f(nanoOfDay:10) == LocalTime.parse('00:00:00.00000001')", true);
    test("LocalTime.ofNanoOfDay(0) == LocalTime.parse('00:00:00')", true);
    testError("LocalTime.ofNanoOfDay(-1) == LocalTime.parse('00:00:00')", "invalid value");
    testError("LocalTime.ofNanoOfDay(-1000000000000L)", "invalid value");
  }

  @Test public void ofSecondOfDay() {
    test("LocalTime.ofSecondOfDay(0) == LocalTime.parse('00:00:00.0')", true);
    test("LocalTime.ofSecondOfDay(1) == LocalTime.parse('00:00:01')", true);
    test("LocalTime.ofSecondOfDay(secondOfDay:3600+1) == LocalTime.parse('01:00:01.0')", true);
    test("def f = LocalTime.ofSecondOfDay; f(10) == LocalTime.parse('00:00:10')", true);
    test("def f = LocalTime.ofSecondOfDay; f(secondOfDay:10) == LocalTime.parse('00:00:10')", true);
    test("LocalTime.ofSecondOfDay(0) == LocalTime.parse('00:00:00')", true);
    testError("LocalTime.ofSecondOfDay(-1) == LocalTime.parse('00:00:00')", "invalid value");
    testError("LocalTime.ofSecondOfDay(-1000000000000L)", "invalid value");
  }

  @Test public void parse() {
    test("LocalTime.parse('12:13:14')", LocalTime.parse("12:13:14"));
    test("LocalTime.parse(text:'12:13:14')", LocalTime.parse("12:13:14"));
    test("def f = LocalTime.parse; f('12:13:14')", LocalTime.parse("12:13:14"));
    test("def f = LocalTime.parse; f(text:'12:13:14')", LocalTime.parse("12:13:14"));
    testError("LocalTime.parse('12:13:14:123')", "could not be parsed");
  }

  @Test public void plus() {
    test("LocalTime.parse('12:13:14').plus(Duration.ofSeconds(1))", LocalTime.parse("12:13:15"));
    test("TemporalAmount p = Duration.ofSeconds(1); LocalTime.parse('12:13:14').plus(p)", LocalTime.parse("12:13:15"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); d.plus(p)", LocalTime.parse("12:13:15"));
    test("def p = Duration.ofSeconds(1); LocalTime.parse('12:13:14').plus(p)", LocalTime.parse("12:13:15"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", LocalTime.parse("12:13:15"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); d.plus(amount:p)", LocalTime.parse("12:13:15"));
    test("LocalTime d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", LocalTime.parse("12:13:15"));
    test("def d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(p)", LocalTime.parse("12:13:15"));
    test("def d = LocalTime.parse('12:13:14'); Duration p = Duration.ofSeconds(1); def f = d.plus; f(amount:p)", LocalTime.parse("12:13:15"));
  }

  @Test public void plusHours() {
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusHours(1); LocalTime d2 = now.plusHours(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusHours(1); LocalTime d2 = now.plusHours(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusHours(1); LocalTime d2 = now.plusHours(hours:1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusHours(1); def d2 = now.plusHours(hours:1); d1 == d2", true);
  }

  @Test public void plusMinutes() {
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusMinutes(1); LocalTime d2 = now.plusMinutes(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusMinutes(1); LocalTime d2 = now.plusMinutes(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusMinutes(1); LocalTime d2 = now.plusMinutes(minutes:1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusMinutes(1); def d2 = now.plusMinutes(minutes:1); d1 == d2", true);
  }

  @Test public void plusNanos() {
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusNanos(1); LocalTime d2 = now.plusNanos(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusNanos(1); LocalTime d2 = now.plusNanos(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusNanos(1); LocalTime d2 = now.plusNanos(nanos:1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusNanos(1); def d2 = now.plusNanos(nanos:1); d1 == d2", true);
  }

  @Test public void plusSeconds() {
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusSeconds(1); LocalTime d2 = now.plusSeconds(2); d1 == d2", false);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusSeconds(1); LocalTime d2 = now.plusSeconds(1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(1); d1 == d2", true);
    test("LocalTime now = LocalTime.now(); LocalTime d1 = now.plusSeconds(1); LocalTime d2 = now.plusSeconds(seconds:1); d1 == d2", true);
    test("def now = LocalTime.now(); def d1 = now.plusSeconds(1); def d2 = now.plusSeconds(seconds:1); d1 == d2", true);
  }
  
  @Test public void toNanoOfDay() {
    test("LocalTime.parse('00:00:00').toNanoOfDay()", 0L);
    test("LocalTime.parse('00:00:01').toNanoOfDay()", 1000000000L);
    test("LocalTime t = LocalTime.parse('00:00:01'); t.toNanoOfDay()", 1000000000L);
    test("LocalTime t = LocalTime.parse('00:00:01'); def f = t.toNanoOfDay; f()", 1000000000L);
    test("def t = LocalTime.parse('00:00:01'); def f = t.toNanoOfDay; f()", 1000000000L);
  }

  @Test public void toSecondOfDay() {
    test("LocalTime.parse('00:00:00').toSecondOfDay()", 0);
    test("LocalTime.parse('00:00:01').toSecondOfDay()", 1);
    test("LocalTime t = LocalTime.parse('10:00:01'); t.toSecondOfDay()", 10*3600+1);
    test("LocalTime t = LocalTime.parse('00:00:01'); def f = t.toSecondOfDay; f()", 1);
    test("def t = LocalTime.parse('00:00:01'); def f = t.toSecondOfDay; f()", 1);
  }
  
  @Test public void withHour() {
    test("LocalTime.parse('00:00:00').withHour(10)", LocalTime.parse("10:00:00"));
    test("LocalTime.parse('00:00:00').withHour(hour:0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00').withHour; f(0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00').withHour; f(hour:0)", LocalTime.parse("00:00:00"));
    testError("LocalTime.parse('00:00:00').withHour(24)", "invalid value");
  }

  @Test public void withMinute() {
    test("LocalTime.parse('00:00:00').withMinute(10)", LocalTime.parse("00:10:00"));
    test("LocalTime.parse('00:00:00').withMinute(minute:0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00').withMinute; f(0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00').withMinute; f(minute:11)", LocalTime.parse("00:11:00"));
    testError("LocalTime.parse('00:00:00').withMinute(-1)", "invalid value");
    testError("LocalTime.parse('00:00:00').withMinute(60)", "invalid value");
  }

  @Test public void withNano() {
    test("LocalTime.parse('00:00:00').withNano(10)", LocalTime.parse("00:00:00.00000001"));
    test("LocalTime.parse('00:00:00.123').withNano(nano:0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00.123').withNano; f(0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:00.123').withNano; f(nano:11)", LocalTime.parse("00:00:00.000000011"));
    testError("LocalTime.parse('00:00:00').withNano(-1)", "invalid value");
    testError("LocalTime.parse('00:00:00').withNano(6000000000L)", "invalid value");
  }

  @Test public void withSecond() {
    test("LocalTime.parse('00:00:01').withSecond(10)", LocalTime.parse("00:00:10"));
    test("LocalTime.parse('00:00:20').withSecond(second:0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:30').withSecond; f(0)", LocalTime.parse("00:00:00"));
    test("def f = LocalTime.parse('00:00:04').withSecond; f(second:11)", LocalTime.parse("00:00:11"));
    testError("LocalTime.parse('00:00:00').withSecond(-1)", "invalid value");
    testError("LocalTime.parse('00:00:00').withSecond(60)", "invalid value");
  }

  @Test public void format() {
    test("LocalTime d = LocalTime.parse('13:13:14'); d.toString() == d.format('HH:mm:ss')", true);
    test("LocalTime d = LocalTime.parse('13:13:14'); '01:13:14' == d.format('hh:mm:ss')", true);
    test("LocalTime d = LocalTime.parse('13:13:14'); '01:13:14' == d.format(format:'hh:mm:ss')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); def f = d.format; d.toString() == f('HH:mm:ss')", true);
    test("LocalTime d = LocalTime.parse('12:13:14'); def f = d.format; d.toString() == f(format:'HH:mm:ss')", true);
    testError("LocalTime d = LocalTime.parse('12:13:14'); d.toString() == d.format('iiii-MM-dd')", "unknown pattern letter");
    testError("LocalTime d = LocalTime.parse('12:13:14'); d.toString() == d.format('yyyy-MM-dd')", "unsupported field");
  }

  @Test public void parseWithFormat() {
    test("LocalTime.parseWithFormat('12/13/14','HH/mm/ss')", LocalTime.parse("12/13/14", DateTimeFormatter.ofPattern("HH/mm/ss")));
    test("def f = LocalTime.parseWithFormat; f('12/13/14','HH/mm/ss')", LocalTime.parse("12/13/14", DateTimeFormatter.ofPattern("HH/mm/ss")));
    test("LocalTime.parseWithFormat(text:'12/13/14',format:'HH/mm/ss')", LocalTime.parse("12/13/14", DateTimeFormatter.ofPattern("HH/mm/ss")));
    test("def f = LocalTime.parseWithFormat; f(text:'12/13/14',format:'HH/mm/ss')", LocalTime.parse("12/13/14", DateTimeFormatter.ofPattern("HH/mm/ss")));
    test("LocalTime.parseWithFormat('2026-01-02:12/13/14','yyyy-MM-dd:HH/mm/ss')", LocalTime.parse("2026-01-02:12/13/14", DateTimeFormatter.ofPattern("yyyy-MM-dd:HH/mm/ss")));
    testError("LocalTime t = LocalTime.parseWithFormat('13/13/14','hh/mm/ss')", "invalid value");
    testError("LocalTime t = LocalTime.parseWithFormat('13/13/14','iiihh/mm/ss')", "unknown pattern letter");
  }
  
  @Test public void truncatedToMicros() {
    test("LocalTime.ofNanoOfDay(123456789).truncatedToMicros()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MICROS));
    test("def f = LocalTime.ofNanoOfDay; f(123456789).truncatedToMicros()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MICROS));
    test("def f = LocalTime.ofNanoOfDay(123456789).truncatedToMicros; f()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MICROS));
  }

  @Test public void truncatedToMillis() {
    test("LocalTime.ofNanoOfDay(123456789).truncatedToMillis()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MILLIS));
    test("def f = LocalTime.ofNanoOfDay; f(123456789).truncatedToMillis()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MILLIS));
    test("def f = LocalTime.ofNanoOfDay(123456789).truncatedToMillis; f()", LocalTime.ofNanoOfDay(123456789).truncatedTo(ChronoUnit.MILLIS));
  }

  @Test public void truncatedToSeconds() {
    test("LocalTime.ofNanoOfDay(1000000000 + 123456789).truncatedToSeconds()", LocalTime.ofNanoOfDay(1000000000 + 123456789).truncatedTo(ChronoUnit.SECONDS));
    test("def f = LocalTime.ofNanoOfDay; f(1000000000 +123456789).truncatedToSeconds()", LocalTime.ofNanoOfDay(1000000000 + 123456789).truncatedTo(ChronoUnit.SECONDS));
    test("def f = LocalTime.ofNanoOfDay(1000000000 + 123456789).truncatedToSeconds; f()", LocalTime.ofNanoOfDay(1000000000 + 123456789).truncatedTo(ChronoUnit.SECONDS));
  }

  @Test public void truncatedToMinutes() {
    test("LocalTime.ofNanoOfDay(123L * 1000000000 + 123456789).truncatedToMinutes()", LocalTime.ofNanoOfDay(123L * 1000000000 + 123456789).truncatedTo(ChronoUnit.MINUTES));
    test("def f = LocalTime.ofNanoOfDay; f(123L * 1000000000 +123456789).truncatedToMinutes()", LocalTime.ofNanoOfDay(123L * 1000000000 + 123456789).truncatedTo(ChronoUnit.MINUTES));
    test("def f = LocalTime.ofNanoOfDay(123L * 1000000000 + 123456789).truncatedToMinutes; f()", LocalTime.ofNanoOfDay(123L * 1000000000 + 123456789).truncatedTo(ChronoUnit.MINUTES));
  }

  @Test public void truncatedToHours() {
    test("LocalTime.ofNanoOfDay(7234L * 1000000000 + 123456789).truncatedToHours()", LocalTime.ofNanoOfDay(7234L * 1000000000 + 123456789).truncatedTo(ChronoUnit.HOURS));
    test("def f = LocalTime.ofNanoOfDay; f(7234L * 1000000000 +123456789).truncatedToHours()", LocalTime.ofNanoOfDay(7234L * 1000000000 + 123456789).truncatedTo(ChronoUnit.HOURS));
    test("def f = LocalTime.ofNanoOfDay(7234L * 1000000000 + 123456789).truncatedToHours; f()", LocalTime.ofNanoOfDay(7234L * 1000000000 + 123456789).truncatedTo(ChronoUnit.HOURS));
  }
  
  @Test public void until() {
    test("LocalTime.parse('12:13:14').until(LocalTime.parse('12:13:15')) == Duration.ofSeconds(1)", true);
    test("LocalTime.parse('12:13:14').until(LocalTime.parse('12:13:13')) == Duration.ofSeconds(-1)", true);
    test("LocalTime.parse('12:13:14').until(end:LocalTime.parse('12:13:13')) == Duration.ofSeconds(-1)", true);
    test("def f = LocalTime.parse('12:13:14').until; f(LocalTime.parse('12:13:13')) == Duration.ofSeconds(-1)", true);
    test("def f = LocalTime.parse('12:13:14').until; f(end:LocalTime.parse('12:13:13')) == Duration.ofSeconds(-1)", true);
    test("def t = LocalTime.parse('12:13:14'); def f = t.until; f(end:LocalTime.parse('12:13:13')) == Duration.ofSeconds(-1)", true);
  }
}
