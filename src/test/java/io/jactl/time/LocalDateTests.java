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

import static org.junit.jupiter.api.Assertions.fail;

public class LocalDateTests extends BaseTest {

  @Test public void instanceOfTest() {
    test("LocalDate.parse('2026-01-28') instanceof LocalDate", true);
    test("def d = LocalDate.parse('2026-01-28'); d instanceof LocalDate", true);
    test("LocalDate d = LocalDate.parse('2026-01-28'); d instanceof LocalDate", true);
    test("LocalDate.parse('2026-01-28') instanceof jactl.time.LocalDate", true);
    test("def d = LocalDate.parse('2026-01-28'); d instanceof jactl.time.LocalDate", true);
    test("LocalDate d = LocalDate.parse('2026-01-28'); d instanceof jactl.time.LocalDate", true);
    globals.put("d", LocalDate.parse("2026-01-28"));
    test("d instanceof LocalDate", true);
  }

  @Test public void basicDate() {
    test("jactl.time.LocalDate.now().lengthOfYear() in [365, 366]", true);
    test("def now = LocalDate.now(); now.lengthOfYear() > 360", true);
    test("LocalDate now = LocalDate.now(); now.lengthOfYear() > 360", true);
    test("def now = LocalDate.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("LocalDate now = LocalDate.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("def date = LocalDate.parse('2026-01-15'); date.getYear()", 2026);
    test("LocalDate date = LocalDate.parse('2026-01-15'); date.getYear()", 2026);
    test("def date = LocalDate.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("LocalDate date = LocalDate.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("def n = LocalDate.now; n().lengthOfYear() > 360", true);
    test("def len = LocalDate.now().lengthOfYear; len() > 360", true);
    testError("def date = LocalDate.parse('2026-0115'); date.getYear()", "could not be parsed");
  }
  
  @Test public void atStartOfDay() {
    test("LocalDate.now().atStartOfDay()", LocalDate.now().atStartOfDay());
    test("def f = LocalDate.now().atStartOfDay; f()", LocalDate.now().atStartOfDay());
    test("def d = LocalDate.now(); def f = d.atStartOfDay; f()", LocalDate.now().atStartOfDay());
  }

  @Test public void atStartOfDayInZone() {
    // TODO:
    test("LocalDate.now().atStartOfDayInZone(ZoneId.of('Australia/Sydney'))", LocalDate.now().atStartOfDay(ZoneId.of("Australia/Sydney")));
  }
  
  @Test public void atTime() {
    // TODO:
    test("LocalDate d = LocalDate.now().atTime(LocalTime.now())", LocalDate.now().atTime(LocalTime.now()));
  }
  
  @Test public void getDayOfMonth() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.getDayOfMonth()", 2);
    test("LocalDate d = LocalDate.parse('2026-01-02'); def f = d.getDayOfMonth; f()", 2);
    test("def d = LocalDate.parse('2026-01-02'); d.getDayOfMonth()", 2);
    test("def d = LocalDate.parse('2026-01-12'); d.getDayOfMonth()", 12);
    test("def d = LocalDate.parse('2026-01-12'); def f = d.getDayOfMonth; f()", 12);
  }

  @Test public void getDayOfYear() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.getDayOfYear()", 2);
    test("LocalDate d = LocalDate.parse('2026-01-02'); def f = d.getDayOfYear; f()", 2);
    test("def d = LocalDate.parse('2026-01-02'); d.getDayOfYear()", 2);
    test("def d = LocalDate.parse('2026-01-12'); d.getDayOfYear()", 12);
    test("def d = LocalDate.parse('2026-02-12'); d.getDayOfYear()", 43);
    test("def d = LocalDate.parse('2026-01-12'); def f = d.getDayOfYear; f()", 12);
  }

  @Test public void getDayOfWeek() {
    test("def d = LocalDate.parse('2026-01-19'); d.getDayOfWeek()", "MONDAY");
    test("LocalDate d = LocalDate.parse('2026-01-20'); d.getDayOfWeek()", "TUESDAY");
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.getDayOfWeek()", "WEDNESDAY");
    test("LocalDate d = LocalDate.parse('2026-01-22'); d.getDayOfWeek()", "THURSDAY");
    test("LocalDate d = LocalDate.parse('2026-01-22'); def f = d.getDayOfWeek; f()", "THURSDAY");
    test("def d = LocalDate.parse('2026-01-23'); d.getDayOfWeek()", "FRIDAY");
    test("def d = LocalDate.parse('2026-01-24'); d.getDayOfWeek()", "SATURDAY");
    test("def d = LocalDate.parse('2026-01-25'); d.getDayOfWeek()", "SUNDAY");
    test("def d = LocalDate.parse('2026-01-25'); def f = d.getDayOfWeek; f()", "SUNDAY");
  }
  
  @Test void getMonth() {
    test("def d = LocalDate.parse('2026-01-19'); d.getMonth()", "JANUARY");
    test("LocalDate d = LocalDate.parse('2026-02-20'); d.getMonth()", "FEBRUARY");
    test("LocalDate d = LocalDate.parse('2026-03-21'); d.getMonth()", "MARCH");
    test("LocalDate d = LocalDate.parse('2026-04-22'); d.getMonth()", "APRIL");
    test("LocalDate d = LocalDate.parse('2026-05-22'); def f = d.getMonth; f()", "MAY");
    test("def d = LocalDate.parse('2026-06-23'); d.getMonth()", "JUNE");
    test("def d = LocalDate.parse('2026-07-24'); d.getMonth()", "JULY");
    test("def d = LocalDate.parse('2026-08-25'); d.getMonth()", "AUGUST");
    test("def d = LocalDate.parse('2026-09-25'); def f = d.getMonth; f()", "SEPTEMBER");
    test("LocalDate d = LocalDate.parse('2026-10-22'); d.getMonth()", "OCTOBER");
    test("LocalDate d = LocalDate.parse('2026-11-22'); d.getMonth()", "NOVEMBER");
    test("LocalDate d = LocalDate.parse('2026-12-22'); d.getMonth()", "DECEMBER");
  }
  
  @Test void getMonthValue() {
    test("def d = LocalDate.parse('2026-01-19'); d.getMonthValue()", 1);
    test("LocalDate d = LocalDate.parse('2026-02-20'); d.getMonthValue()", 2);
    test("LocalDate d = LocalDate.parse('2026-03-21'); d.getMonthValue()", 3);
    test("LocalDate d = LocalDate.parse('2026-04-22'); d.getMonthValue()", 4);
    test("LocalDate d = LocalDate.parse('2026-05-22'); def f = d.getMonthValue; f()", 5);
    test("def d = LocalDate.parse('2026-06-23'); d.getMonthValue()", 6);
    test("def d = LocalDate.parse('2026-07-24'); d.getMonthValue()", 7);
    test("def d = LocalDate.parse('2026-08-25'); d.getMonthValue()", 8);
    test("def d = LocalDate.parse('2026-09-25'); def f = d.getMonthValue; f()", 9);
    test("LocalDate d = LocalDate.parse('2026-10-22'); d.getMonthValue()", 10);
    test("LocalDate d = LocalDate.parse('2026-11-22'); d.getMonthValue()", 11);
    test("LocalDate d = LocalDate.parse('2026-12-22'); d.getMonthValue()", 12);
  }

  @Test public void getYear() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.getYear()", 2026);
    test("LocalDate d = LocalDate.parse('2027-01-02'); def f = d.getYear; f()", 2027);
    test("def d = LocalDate.parse('2026-01-02'); d.getYear()", 2026);
    test("def d = LocalDate.parse('1970-01-12'); d.getYear()", 1970);
    test("def d = LocalDate.parse('1969-02-12'); d.getYear()", 1969);
    test("def d = LocalDate.parse('1900-01-12'); def f = d.getYear; f()", 1900);
  }
  
  @Test public void isAfter() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); LocalDate.now().isAfter(d)", true);
    test("LocalDate d = LocalDate.parse('2026-01-02'); LocalDate.parse('2026-01-02').isAfter(d)", false);
    test("def d = LocalDate.parse('2026-01-02'); LocalDate.now().isAfter(d)", true);
    test("def d = LocalDate.parse('2026-01-02'); def f = LocalDate.now().isAfter; f(d)", true);
    test("def d = LocalDate.parse('2026-01-02'); LocalDate.parse('2025-01-01').isAfter(d)", false);
  }

  @Test public void isBefore() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); LocalDate.now().isBefore(d)", false);
    test("LocalDate d = LocalDate.parse('2026-01-02'); LocalDate.parse('2026-01-02').isBefore(d)", false);
    test("def d = LocalDate.parse('2026-01-02'); LocalDate.now().isBefore(d)", false);
    test("def d = LocalDate.parse('2026-01-02'); def f = LocalDate.now().isBefore; f(d)", false);
    test("def d = LocalDate.parse('2026-01-02'); LocalDate.parse('2025-01-01').isBefore(d)", true);
  }
  
  @Test public void isEqual() {
    test("LocalDate now = LocalDate.now(); now.isEqual(now)", true);
    test("LocalDate now = LocalDate.now(); now.isEqual(other:now)", true);
    test("def now = LocalDate.now(); now.isEqual(now)", true);
    test("def now = LocalDate.now(); def f = now.isEqual; f(now)", true);
    test("def now = LocalDate.now(); now.isEqual(other:now)", true);
    test("def now = LocalDate.now(); def f = now.isEqual; f(other:now)", true);
  }
  
  @Test public void isLeapYear() {
    test("LocalDate d = LocalDate.parse('2025-01-02'); d.isLeapYear()", false);
    test("LocalDate d = LocalDate.parse('2025-01-02'); def f = d.isLeapYear; f()", false);
    test("def d = LocalDate.parse('2025-01-02'); d.isLeapYear()", false);
    test("def d = LocalDate.parse('2020-01-02'); d.isLeapYear()", true);
    test("def d = LocalDate.parse('2020-01-02'); def f = d.isLeapYear; f()", true);
  }
  
  @Test public void lengthOfMonth() {
    test("def d = LocalDate.parse('2026-01-19'); d.lengthOfMonth()", 31);
    test("LocalDate d = LocalDate.parse('2026-02-20'); d.lengthOfMonth()", 28);
    test("LocalDate d = LocalDate.parse('2020-02-20'); d.lengthOfMonth()", 29);
    test("LocalDate d = LocalDate.parse('2026-03-21'); d.lengthOfMonth()", 31);
    test("LocalDate d = LocalDate.parse('2026-04-22'); d.lengthOfMonth()", 30);
    test("LocalDate d = LocalDate.parse('2026-05-22'); def f = d.lengthOfMonth; f()", 31);
    test("def d = LocalDate.parse('2026-06-23'); d.lengthOfMonth()", 30);
    test("def d = LocalDate.parse('2026-07-24'); d.lengthOfMonth()", 31);
    test("def d = LocalDate.parse('2026-08-25'); d.lengthOfMonth()", 31);
    test("def d = LocalDate.parse('2026-09-25'); def f = d.lengthOfMonth; f()", 30);
    test("LocalDate d = LocalDate.parse('2026-10-22'); d.lengthOfMonth()", 31);
    test("LocalDate d = LocalDate.parse('2026-11-22'); d.lengthOfMonth()", 30);
    test("LocalDate d = LocalDate.parse('2026-12-22'); d.lengthOfMonth()", 31);
  }
  
  @Test public void lengthOfYear() {
    test("LocalDate d = LocalDate.parse('2026-10-22'); d.lengthOfYear()", 365);
    test("LocalDate d = LocalDate.parse('2026-05-22'); def f = d.lengthOfYear; f()", 365);
    test("LocalDate d = LocalDate.parse('2020-05-22'); def f = d.lengthOfYear; f()", 366);
    test("def d = LocalDate.parse('1972-06-23'); d.lengthOfYear()", 366);
  }

  @Test public void minus() {
    test("LocalDate.parse('2026-01-28').minus(Period.of(0,0,1))", LocalDate.parse("2026-01-27"));
    test("TemporalAmount p = Period.of(0,0,1); LocalDate.parse('2026-01-28').minus(p)", LocalDate.parse("2026-01-27"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); d.minus(p)", LocalDate.parse("2026-01-27"));
    test("def p = Period.of(0,0,1); LocalDate.parse('2026-01-28').minus(p)", LocalDate.parse("2026-01-27"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.minus; f(p)", LocalDate.parse("2026-01-27"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); d.minus(amount:p)", LocalDate.parse("2026-01-27"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.minus; f(amount:p)", LocalDate.parse("2026-01-27"));
    test("def d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.minus; f(p)", LocalDate.parse("2026-01-27"));
    test("def d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.minus; f(amount:p)", LocalDate.parse("2026-01-27"));
  }

  @Test public void minusDays() {
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusDays(1); d2.isEqual(LocalDate.parse('2026-01-22'))", true);
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusDays(24); d2.isEqual(LocalDate.parse('2025-12-30'))", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusDays(1); LocalDate d2 = now.minusDays(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusDays(1); LocalDate d2 = now.minusDays(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusDays(1); LocalDate d2 = now.minusDays(days:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusDays(1); LocalDate d2 = now.minusDays(days:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusDays(1); def d2 = now.minusDays(days:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.minusDays(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusDays(days:1000000000000L)", "invalid value");
  }

  @Test public void minusMonths() {
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusMonths(1); d2.isEqual(LocalDate.parse('2025-12-23'))", true);
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusMonths(24); d2.isEqual(LocalDate.parse('2024-01-23'))", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusMonths(1); LocalDate d2 = now.minusMonths(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusMonths(1); LocalDate d2 = now.minusMonths(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusMonths(1); def d2 = now.minusMonths(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusMonths(1); LocalDate d2 = now.minusMonths(months:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusMonths(1); LocalDate d2 = now.minusMonths(months:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusMonths(1); def d2 = now.minusMonths(months:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.minusMonths(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void minusWeeks() {
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusWeeks(1); d2.isEqual(LocalDate.parse('2026-01-16'))", true);
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusWeeks(4); d2.isEqual(LocalDate.parse('2025-12-26'))", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusWeeks(1); LocalDate d2 = now.minusWeeks(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusWeeks(1); LocalDate d2 = now.minusWeeks(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusWeeks(1); def d2 = now.minusWeeks(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusWeeks(1); LocalDate d2 = now.minusWeeks(weeks:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusWeeks(1); LocalDate d2 = now.minusWeeks(weeks:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusWeeks(1); def d2 = now.minusWeeks(weeks:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.minusWeeks(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusWeeks(weeks:1000000000000L)", "invalid value");
  }

  @Test public void minusYears() {
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusYears(1); d2.isEqual(LocalDate.parse('2025-01-23'))", true);
   test("LocalDate d = LocalDate.parse('2026-01-23'); LocalDate d2 = d.minusYears(4); d2.isEqual(LocalDate.parse('2022-01-23'))", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusYears(1); LocalDate d2 = now.minusYears(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusYears(1); LocalDate d2 = now.minusYears(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusYears(1); def d2 = now.minusYears(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusYears(1); LocalDate d2 = now.minusYears(years:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.minusYears(1); LocalDate d2 = now.minusYears(years:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.minusYears(1); def d2 = now.minusYears(years:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.minusYears(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.minusYears(years:1000000000000L)", "invalid value");
  }

  @Test public void nowInZone() {
    test("LocalDate d = LocalDate.nowInZone(ZoneId.of('Australia/Sydney'))", LocalDate.now(ZoneId.of("Australia/Sydney")));
    test("def f = LocalDate.nowInZone; f(ZoneId.of('Europe/Rome'))", LocalDate.now(ZoneId.of("Europe/Rome")));
    for (String id: ZoneId.getAvailableZoneIds()) {
      test("LocalDate.nowInZone(ZoneId.of('" + id + "'))", LocalDate.now(ZoneId.of(id)));
    }
    try {
      ZoneId.systemDefault();
      test("LocalDate.now() == LocalDate.nowInZone(ZoneId.systemDefault())", true);
    }
    catch (DateTimeException ignored) {
      // Just in case system default zone not configured properly
    }
  }
  
  @Test public void of() {
    test("LocalDate.of(2026,1,27) == LocalDate.parse('2026-01-27')", true);
    test("LocalDate.of(year:2026,month:1,day:27) == LocalDate.parse('2026-01-27')", true);
    test("def f = LocalDate.of; f(2026,1,27) == LocalDate.parse('2026-01-27')", true);
    test("def f = LocalDate.of; f(year:2026,month:1,day:27) == LocalDate.parse('2026-01-27')", true);
    testError("LocalDate.of(2026,13,27) == LocalDate.parse('2026-01-27')", "invalid value for monthofyear");
  }
  
  @Test public void ofEpochDay() {
    test("LocalDate.ofEpochDay(10) == LocalDate.parse('1970-01-11')", true);
    test("LocalDate.ofEpochDay(epochDay:10) == LocalDate.parse('1970-01-11')", true);
    test("def f = LocalDate.ofEpochDay; f(10) == LocalDate.parse('1970-01-11')", true);
    test("def f = LocalDate.ofEpochDay; f(epochDay:10) == LocalDate.parse('1970-01-11')", true);
    test("LocalDate.ofEpochDay(0) == LocalDate.parse('1970-01-01')", true);
    test("LocalDate.ofEpochDay(-1) == LocalDate.parse('1969-12-31')", true);
    testError("LocalDate.ofEpochDay(-1000000000000L)", "invalid value");
  }

  @Test public void toEpochDay() {
    test("LocalDate.parse('1970-01-11').toEpochDay() == 10", true);
    test("def f = LocalDate.parse('1970-01-11').toEpochDay; f() == 10", true);
    test("LocalDate.parse('1970-01-01').toEpochDay()", 0L);
    test("LocalDate.parse('1969-12-31').toEpochDay()", -1L);
  }
  
  @Test public void ofYearDay() {
    test("LocalDate.ofYearDay(2026, 32) == LocalDate.parse('2026-02-01')", true);
    test("def f = LocalDate.ofYearDay; f(2026, 32) == LocalDate.parse('2026-02-01')", true);
    test("LocalDate.ofYearDay(year:2026, dayOfYear:32) == LocalDate.parse('2026-02-01')", true);
    test("def f = LocalDate.ofYearDay; f(year:2026, dayOfYear:32) == LocalDate.parse('2026-02-01')", true);
    testError("LocalDate.ofYearDay(2026, 0)", "invalid value for dayofyear");
    testError("LocalDate.ofYearDay(2026, 366)", "not a leap year");
    test("LocalDate.ofYearDay(2020, 366)", LocalDate.parse("2020-12-31"));
  }

  @Test public void plus() {
    test("LocalDate.parse('2026-01-28').plus(Period.of(0,0,1))", LocalDate.parse("2026-01-29"));
    test("TemporalAmount p = Period.of(0,0,1); LocalDate.parse('2026-01-28').plus(p)", LocalDate.parse("2026-01-29"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); d.plus(p)", LocalDate.parse("2026-01-29"));
    test("def p = Period.of(0,0,1); LocalDate.parse('2026-01-28').plus(p)", LocalDate.parse("2026-01-29"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.plus; f(p)", LocalDate.parse("2026-01-29"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); d.plus(amount:p)", LocalDate.parse("2026-01-29"));
    test("LocalDate d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.plus; f(amount:p)", LocalDate.parse("2026-01-29"));
    test("def d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.plus; f(p)", LocalDate.parse("2026-01-29"));
    test("def d = LocalDate.parse('2026-01-28'); Period p = Period.of(0,0,1); def f = d.plus; f(amount:p)", LocalDate.parse("2026-01-29"));
  }

  @Test public void plusDays() {
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusDays(1); LocalDate d2 = now.plusDays(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusDays(1); LocalDate d2 = now.plusDays(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusDays(1); def d2 = now.plusDays(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusDays(1); LocalDate d2 = now.plusDays(days:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusDays(1); LocalDate d2 = now.plusDays(days:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusDays(1); def d2 = now.plusDays(days:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.plusDays(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusDays(days:1000000000000L)", "invalid value");
  }
  
  @Test public void plusMonths() {
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusMonths(1); LocalDate d2 = now.plusMonths(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusMonths(1); LocalDate d2 = now.plusMonths(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusMonths(1); def d2 = now.plusMonths(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusMonths(1); LocalDate d2 = now.plusMonths(months:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusMonths(1); LocalDate d2 = now.plusMonths(months:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusMonths(1); def d2 = now.plusMonths(months:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.plusMonths(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusMonths(months:1000000000000L)", "invalid value");
  }

  @Test public void plusYears() {
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusYears(1); LocalDate d2 = now.plusYears(2); d1.isEqual(d2)", false);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusYears(1); LocalDate d2 = now.plusYears(1); d1.isEqual(d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusYears(1); def d2 = now.plusYears(1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusYears(1); LocalDate d2 = now.plusYears(years:1); d1.isEqual(d2)", true);
   test("LocalDate now = LocalDate.now(); LocalDate d1 = now.plusYears(1); LocalDate d2 = now.plusYears(years:1); d1.isEqual(other:d2)", true);
   test("def now = LocalDate.now(); def d1 = now.plusYears(1); def d2 = now.plusYears(years:1); d1.isEqual(other:d2)", true);
   testError("def now = LocalDate.now(); def d1 = now.plusYears(1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
   testError("def now = LocalDate.now(); def d1 = now.plusYears(years:1000000000000L)", "invalid value");
  }
  
  @Test public void parseWithFormat() {
    test("LocalDate d = LocalDate.parseWithFormat('2026/01/20','yyyy/MM/dd'); d.isEqual(LocalDate.parse('2026-01-20'))", true);
    test("LocalDate d = LocalDate.parseWithFormat(text:'2026/01/20',format:'yyyy/MM/dd'); d.isEqual(LocalDate.parse(text:'2026-01-20'))", true);
    test("LocalDate.parseWithFormat('2026/01/20','yyyy/MM/dd').isEqual(LocalDate.parse('2026-01-20'))", true);
    test("LocalDate.parseWithFormat('2026/01/20 12:11:10','yyyy/MM/dd HH:mm:ss').isEqual(LocalDate.parse('2026-01-20'))", true);
    test("def f = LocalDate.parseWithFormat; f('2026/01/20 12:11:10','yyyy/MM/dd HH:mm:ss').isEqual(LocalDate.parse('2026-01-20'))", true);
    testError("def f = LocalDate.parseWithFormat; f('2026/01/20 12:11:10','ijklmyyyy/MM/dd HH:mm:ss').isEqual(LocalDate.parse('2026-01-20'))", "unknown pattern letter");
  }
  
  @Test public void format() {
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.toString() == d.format('yyyy-MM-dd')", true);
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.toString() == d.format('yyyy-MM-dd')", true);
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.toString() == d.format(pattern:'yyyy-MM-dd')", true);
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.format; d.toString() == f('yyyy-MM-dd')", true);
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.format; d.toString() == f(pattern:'yyyy-MM-dd')", true);
    testError("LocalDate d = LocalDate.parse('2026-01-21'); d.toString() == d.format('yxyziuy-MM-dd')", "unknown pattern letter");
  }
  
  @Test public void until() {
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.until(d.plusDays(10)) == Period.ofDays(10)", true);
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.until(LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("LocalDate d = LocalDate.parse('2026-01-02'); def f = d.until; f(LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("LocalDate d = LocalDate.parse('2026-01-02'); d.until(end:LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("LocalDate d = LocalDate.parse('2026-01-02'); def f = d.until; f(end:LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("def d = LocalDate.parse('2026-01-02'); def end = LocalDate.parse('2027-02-03'); d.until(end) == Period.of(1,1,1)", true);
    test("def d = LocalDate.parse('2026-01-02'); def end = LocalDate.parse('2027-02-03'); d.until(end:end) == Period.of(1,1,1)", true);
    test("def d = LocalDate.parse('2026-01-02'); def end = LocalDate.parse('2027-02-03'); def f = d.until; f(end:end) == Period.of(1,1,1)", true);
  }
  
  @Test public void withDayOfMonth() {
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withDayOfMonth(22)", LocalDate.parse("2026-01-22"));
    testError("LocalDate d = LocalDate.parse('2026-02-21'); d.withDayOfMonth(30)", "invalid date");
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withDayOfMonth(day:22)", LocalDate.parse("2026-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.withDayOfMonth; f(22)", LocalDate.parse("2026-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.withDayOfMonth; f(day:22)", LocalDate.parse("2026-01-22"));
    test("def d = LocalDate.parse('2026-01-21'); def f = d.withDayOfMonth; f(22)", LocalDate.parse("2026-01-22"));
    test("def d = LocalDate.parse('2026-01-21'); def f = d.withDayOfMonth; f(day:22)", LocalDate.parse("2026-01-22"));
  }

  @Test public void withDayOfYear() {
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withDayOfYear(42)", LocalDate.parse("2026-02-11"));
    test("LocalDate d = LocalDate.parse('2020-01-21'); d.withDayOfYear(366)", LocalDate.parse("2020-12-31"));
    testError("LocalDate d = LocalDate.parse('2026-02-21'); d.withDayOfYear(366)", "invalid date");
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withDayOfYear(day:22)", LocalDate.parse("2026-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.withDayOfYear; f(22)", LocalDate.parse("2026-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-21'); def f = d.withDayOfYear; f(day:22)", LocalDate.parse("2026-01-22"));
    test("def d = LocalDate.parse('2026-01-21'); def f = d.withDayOfYear; f(22)", LocalDate.parse("2026-01-22"));
    test("def d = LocalDate.parse('2026-01-21'); def f = d.withDayOfYear; f(day:22)", LocalDate.parse("2026-01-22"));
  }

  @Test public void withMonth() {
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withMonth(12)", LocalDate.parse("2026-12-21"));
    testError("LocalDate d = LocalDate.parse('2026-02-21'); d.withMonth(30)", "invalid value");
    test("LocalDate d = LocalDate.parse('2026-01-22'); d.withMonth(month:2)", LocalDate.parse("2026-02-22"));
    test("LocalDate d = LocalDate.parse('2026-01-22'); def f = d.withMonth; f(2)", LocalDate.parse("2026-02-22"));
    test("LocalDate d = LocalDate.parse('2026-01-22'); def f = d.withMonth; f(month:2)", LocalDate.parse("2026-02-22"));
    test("def d = LocalDate.parse('2026-01-22'); def f = d.withMonth; f(2)", LocalDate.parse("2026-02-22"));
    test("def d = LocalDate.parse('2026-01-22'); def f = d.withMonth; f(month:2)", LocalDate.parse("2026-02-22"));
  }

  @Test public void withYear() {
    test("LocalDate d = LocalDate.parse('2026-01-21'); d.withYear(1969)", LocalDate.parse("1969-01-21"));
    testError("LocalDate d = LocalDate.parse('2026-02-21'); d.withYear(3000000000000L)", "invalid value");
    test("LocalDate d = LocalDate.parse('2026-01-22'); d.withYear(year:1969)", LocalDate.parse("1969-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-22'); def f = d.withYear; f(1969)", LocalDate.parse("1969-01-22"));
    test("LocalDate d = LocalDate.parse('2026-01-22'); def f = d.withYear; f(year:1969)", LocalDate.parse("1969-01-22"));
    test("def d = LocalDate.parse('2026-01-22'); def f = d.withYear; f(1969)", LocalDate.parse("1969-01-22"));
    test("def d = LocalDate.parse('2026-01-22'); def f = d.withYear; f(year:1969)", LocalDate.parse("1969-01-22"));
  }
}
