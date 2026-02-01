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

import java.time.Period;

/**
 * Tests for Period class.
 * <p>
 * NOTE: we are not testing the functionality since each method call directly
 * invokes an already existing Java method which already have their own exhaustive
 * tests. We are only testing that exposing of the methods we are intending to expose
 * have atually been exposed and can be invoked in a Jactl script.
 * </p>
 */
public class PeriodTests extends BaseTest {

  @Test public void instanceOfTest() {
    test("Period.of(1,2,3) instanceof Period", true);
    test("def p = Period.of(1,2,3); p instanceof Period", true);
    test("Period p = Period.of(1,2,3); p instanceof Period", true);
    test("Period.of(1,2,3) instanceof jactl.time.Period", true);
    test("def p = Period.of(1,2,3); p instanceof jactl.time.Period", true);
    test("Period p = Period.of(1,2,3); p instanceof jactl.time.Period", true);
    globals.put("p", Period.of(1, 2, 3));
    test("p instanceof Period", true);
  }

  @Test public void between() {
    test("Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-02-02'))", Period.of(0,1,0));
    test("Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("def f = Period.between; f(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("Period.between(start:LocalDate.parse('2026-01-02'), end:LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
    test("def f = Period.between; f(start:LocalDate.parse('2026-01-02'), end:LocalDate.parse('2027-02-03')) == Period.of(1,1,1)", true);
  }

  @Test public void getDays() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.getDays()", 0);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-02-02')); p.getDays()", 0);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-02-03')); p.getDays()", 1);
    test("def f = Period.between(LocalDate.parse('2026-02-02'), LocalDate.parse('2026-03-03')).getDays; f()", 1);
    test("Period.between(LocalDate.parse('2027-02-03'), LocalDate.parse('2026-03-02')).getDays()", -1);
    test("Period.between(LocalDate.parse('2026-02-03'), LocalDate.parse('2027-03-02')).getDays()", 27);
  }

  @Test public void getMonths() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.getMonths()", 0);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-01-02')); p.getMonths()", 0);
    test("def p = Period.between(LocalDate.parse('2026-04-02'), LocalDate.parse('2026-02-03')); p.getMonths()", -1);
    test("def f = Period.between(LocalDate.parse('2026-02-02'), LocalDate.parse('2026-03-03')).getMonths; f()", 1);
    test("Period.between(LocalDate.parse('2027-02-03'), LocalDate.parse('2026-12-02')).getMonths()", -2);
    test("Period.between(LocalDate.parse('2026-02-03'), LocalDate.parse('2027-03-02')).getMonths()", 0);
  }

  @Test public void getYears() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.getYears()", 0);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-01-02')); p.getYears()", 1);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-01')); p.getYears()", 0);
    test("def f = Period.between(LocalDate.parse('2020-02-02'), LocalDate.parse('2026-03-03')).getYears; f()", 6);
    test("Period.between(LocalDate.parse('2027-02-03'), LocalDate.parse('2024-12-02')).getYears()", -2);
    test("Period.between(LocalDate.parse('2026-02-03'), LocalDate.parse('2027-02-02')).getYears()", 0);
  }

  @Test public void isNegative() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.isNegative()", false);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-01-02')); p.isNegative()", false);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-01')); p.isNegative()", true);
    test("def f = Period.between(LocalDate.parse('2020-02-02'), LocalDate.parse('2026-03-03')).isNegative; f()", false);
    test("Period.between(LocalDate.parse('2027-02-03'), LocalDate.parse('2024-12-02')).isNegative()", true);
    test("Period.between(LocalDate.parse('2026-02-03'), LocalDate.parse('2027-02-02')).isNegative()", false);
  }

  @Test public void isZero() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.isZero()", true);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2027-01-02')); p.isZero()", false);
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-01')); p.isZero()", false);
    test("def f = Period.between(LocalDate.parse('2020-02-02'), LocalDate.parse('2026-03-03')).isZero; f()", false);
    test("Period.between(LocalDate.parse('2027-02-03'), LocalDate.parse('2024-12-02')).isZero()", false);
    test("Period.between(LocalDate.parse('2026-02-03'), LocalDate.parse('2027-02-02')).isZero()", false);
  }

  @Test public void minus() {
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(1,2,3); p1.minus(p2).isZero()", true);
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); p1.minus(p2)", Period.of(-1,-1,-1));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); def f = p1.minus; f(p2)", Period.of(-1,-1,-1));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); p1.minus(other:p2)", Period.of(-1,-1,-1));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); def f = p1.minus; f(other:p2)", Period.of(-1,-1,-1));
  }

  @Test public void minusDays() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusDays(1)", Period.ofDays(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusDays; f(1)", Period.ofDays(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusDays(days:1)", Period.ofDays(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusDays; f(days:1)", Period.ofDays(-1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusDays(1000)", Period.of(6,0,-1000));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusDays(-1000)", Period.of(6,0,1000));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusDays(-100000000000L)", "integer overflow");
  }

  @Test public void minusMonths() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusMonths(1)", Period.ofMonths(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusMonths; f(1)", Period.ofMonths(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusMonths(months:1)", Period.ofMonths(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusMonths; f(months:1)", Period.ofMonths(-1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusMonths(1000)", Period.of(6,-1000, 0));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusMonths(-1000)", Period.of(6,1000, 0));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusMonths(-100000000000L)", "integer overflow");
  }

  @Test public void minusYears() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusYears(1)", Period.ofYears(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusYears; f(1)", Period.ofYears(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.minusYears(years:1)", Period.ofYears(-1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.minusYears; f(years:1)", Period.ofYears(-1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-03')); p.minusYears(1000)", Period.of(6 - 1000,0, 1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusYears(-1000)", Period.of(6 + 1000, 0, 0));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.minusYears(-100000000000L)", "integer overflow");
  }

  @Test public void multipliedBy() {
    test("def p = Period.of(1,2,3); p.multipliedBy(1)", Period.of(1,2,3));
    test("def p = Period.of(1,2,3); p.multipliedBy(0)", Period.of(0,0,0));
    test("def p = Period.of(1,2,3); p.multipliedBy(2)", Period.of(2,4,6));
    test("def p = Period.of(1,2,3); p.multipliedBy(-2)", Period.of(-2,-4,-6));
    test("def p = Period.of(1,2,3); p.multipliedBy(scalar:2)", Period.of(2,4,6));
    test("def p = Period.of(1,2,3); def f = p.multipliedBy; f(2)", Period.of(2,4,6));
    test("def p = Period.of(1,2,3); def f = p.multipliedBy; f(scalar:2)", Period.of(2,4,6));
  }

  @Test public void negated() {
    test("def p = Period.of(1,2,3); p.multipliedBy(1).negated()", Period.of(-1,-2,-3));
    test("def p = Period.of(1,2,3); p.multipliedBy(0).negated()", Period.of(0,0,0));
    test("def p = Period.of(1,2,3); p.multipliedBy(2).negated()", Period.of(-2,-4,-6));
    test("def p = Period.of(1,2,3); p.multipliedBy(-2).negated()", Period.of(2,4,6));
    test("def p = Period.of(1,2,3); def f = p.multipliedBy(-2).negated; f()", Period.of(2,4,6));
  }
  
  @Test public void normalized() {
    test("Period.of(1,2,3).normalized()", Period.of(1,2,3));
    test("Period.of(1,20,3).normalized()", Period.of(2,8,3));
    test("Period.of(1,-20,3).normalized()", Period.of(0,-8,3));
    test("Period.of(1,-20,-3).normalized()", Period.of(0,-8,-3));
    test("Period.of(1,-20,-100).normalized()", Period.of(0,-8,-100));
    test("def f = Period.of(1,-20,-100).normalized; f()", Period.of(0,-8,-100));
  }
  
  @Test public void of() {
    test("Period.of(0,0,0).isZero()", true);
    test("Period.of(0,0,0) == Period.between(LocalDate.parse('2026-01-01'),LocalDate.parse('2026-01-01'))", true);
    test("Period.of(1,2,3)", Period.of(1,2,3));
    test("def f = Period.of; f(1,2,3)", Period.of(1,2,3));
    test("Period.of(months:2, years:1,days:3)", Period.of(1,2,3));
    test("def f = Period.of; f(months:2,years:1,days:3)", Period.of(1,2,3));
  }
  
  @Test public void ofDays() {
    test("Period.ofDays(0).isZero()", true);
    test("Period.ofDays(0) == Period.between(LocalDate.parse('2026-01-01'),LocalDate.parse('2026-01-01'))", true);
    test("Period.ofDays(3)", Period.ofDays(3));
    test("def f = Period.ofDays; f(-3)", Period.ofDays(-3));
    test("Period.ofDays(days:3)", Period.ofDays(3));
    test("def f = Period.ofDays; f(days:3)", Period.ofDays(3));
  }
  
  @Test public void ofMonths() {
    test("Period.ofMonths(0).isZero()", true);
    test("Period.ofMonths(0) == Period.between(LocalDate.parse('2026-01-01'),LocalDate.parse('2026-01-01'))", true);
    test("Period.ofMonths(3)", Period.ofMonths(3));
    test("def f = Period.ofMonths; f(-3)", Period.ofMonths(-3));
    test("Period.ofMonths(months:3)", Period.ofMonths(3));
    test("def f = Period.ofMonths; f(months:3)", Period.ofMonths(3));
  }
  
  @Test public void ofWeeks() {
    test("Period.ofWeeks(0).isZero()", true);
    test("Period.ofWeeks(0) == Period.between(LocalDate.parse('2026-01-01'),LocalDate.parse('2026-01-01'))", true);
    test("Period.ofWeeks(3)", Period.ofWeeks(3));
    test("def f = Period.ofWeeks; f(-3)", Period.ofWeeks(-3));
    test("Period.ofWeeks(weeks:3)", Period.ofWeeks(3));
    test("def f = Period.ofWeeks; f(weeks:3)", Period.ofWeeks(3));
  }
  
  @Test public void ofYears() {
    test("Period.ofYears(0).isZero()", true);
    test("Period.ofYears(0) == Period.between(LocalDate.parse('2026-01-01'),LocalDate.parse('2026-01-01'))", true);
    test("Period.ofYears(3)", Period.ofYears(3));
    test("def f = Period.ofYears; f(-3)", Period.ofYears(-3));
    test("Period.ofYears(years:3)", Period.ofYears(3));
    test("def f = Period.ofYears; f(years:3)", Period.ofYears(3));
  }
  
  @Test public void parse() {
    test("Period.parse('P1Y') == Period.ofYears(1)", true);
    test("Period.parse('P1Y2M3D') == Period.of(1,2,3)", true);
    test("def f = Period.parse; f('P1Y2M3D') == Period.of(1,2,3)", true);
    test("Period.parse(text:'P1Y2M3D') == Period.of(1,2,3)", true);
    test("def f = Period.parse; f(text:'P1Y2M3D') == Period.of(1,2,3)", true);
    testError("Period.parse('ZXP1Y') == Period.ofYears(1)", "text cannot be parsed");
  }

  @Test public void plus() {
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(1,2,3); p1.plus(p2) == Period.of(2,4,6)", true);
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); p1.plus(p2)", Period.of(3,5,7));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); def f = p1.plus; f(p2)", Period.of(3,5,7));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); p1.plus(other:p2)", Period.of(3,5,7));
    test("def p1 = Period.of(1,2,3); def p2 = Period.of(2,3,4); def f = p1.plus; f(other:p2)", Period.of(3,5,7));
  }

  @Test public void plusDays() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusDays(1)", Period.ofDays(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusDays; f(1)", Period.ofDays(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusDays(days:1)", Period.ofDays(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusDays; f(days:1)", Period.ofDays(1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusDays(1000)", Period.of(6,0,1000));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusDays(-1000)", Period.of(6,0,-1000));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusDays(-100000000000L)", "integer overflow");
  }

  @Test public void plusMonths() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusMonths(1)", Period.ofMonths(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusMonths; f(1)", Period.ofMonths(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusMonths(months:1)", Period.ofMonths(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusMonths; f(months:1)", Period.ofMonths(1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusMonths(1000)", Period.of(6,1000, 0));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusMonths(-1000)", Period.of(6,-1000, 0));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusMonths(-100000000000L)", "integer overflow");
  }

  @Test public void plusYears() {
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusYears(1)", Period.ofYears(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusYears; f(1)", Period.ofYears(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); p.plusYears(years:1)", Period.ofYears(1));
    test("def p = Period.between(LocalDate.parse('2026-01-02'), LocalDate.parse('2026-01-02')); def f = p.plusYears; f(years:1)", Period.ofYears(1));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusYears(1000)", Period.of(6 + 1000, 0, 0));
    test("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusYears(-1000)", Period.of(6 - 1000, 0, 0));
    testError("def p = Period.between(LocalDate.parse('2020-01-02'), LocalDate.parse('2026-01-02')); p.plusYears(-100000000000L)", "integer overflow");
  }

  @Test public void toTotalMonths() {
    test("Period.of(2,1,0).toTotalMonths()", 25L);
    test("Period.of(2,1,1).toTotalMonths()", 25L);
    test("Period.of(2,1,1000).toTotalMonths()", 25L);
    test("Period.of(2,-1,1000).toTotalMonths()", 23L);
    test("def f = Period.of(2,-1,1000).toTotalMonths; f()", 23L);
    test("def p = Period.of(2,-1,1000); p.toTotalMonths()", 23L);
    test("def p = Period.of(2,-1,1000); def f = p.toTotalMonths; f()", 23L);
  }
  
  @Test public void withDays() {
    test("Period.of(1,2,3).withDays(4)", Period.of(1,2,4));
    test("Period.of(1,2,3).withDays(days:4)", Period.of(1,2,4));
    test("def f = Period.of(1,2,3).withDays; f(4)", Period.of(1,2,4));
    test("def f = Period.of(1,2,3).withDays; f(days:4)", Period.of(1,2,4));
  }

  @Test public void withMonths() {
    test("Period.of(1,2,3).withMonths(4)", Period.of(1,4,3));
    test("Period.of(1,2,3).withMonths(months:4)", Period.of(1,4,3));
    test("def f = Period.of(1,2,3).withMonths; f(4)", Period.of(1,4,3));
    test("def f = Period.of(1,2,3).withMonths; f(months:4)", Period.of(1,4,3));
  }

  @Test public void withYears() {
    test("Period.of(1,2,3).withYears(4)", Period.of(4,2,3));
    test("Period.of(1,2,3).withYears(years:4)", Period.of(4,2,3));
    test("def f = Period.of(1,2,3).withYears; f(4)", Period.of(4,2,3));
    test("def f = Period.of(1,2,3).withYears; f(years:4)", Period.of(4,2,3));
  }
}
