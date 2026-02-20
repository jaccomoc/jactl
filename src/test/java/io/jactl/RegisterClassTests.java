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

package io.jactl;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoPeriod;
import java.time.chrono.Chronology;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;

public class RegisterClassTests extends BaseTest {

  public static class MyLocalDate1 implements ChronoLocalDate {
    private LocalDate date;
    private MyLocalDate1(LocalDate date) { this.date = date; }
    public static MyLocalDate1 now() { return new MyLocalDate1(LocalDate.now()); }
    public int getYear() { return date.getYear(); }
    public int getMonthValue() { return date.getMonthValue(); }
    public int getDayOfMonth() { return date.getDayOfMonth(); }
    public int lengthOfYear() { return date.lengthOfYear(); }
    public boolean isAfter(ChronoLocalDate other) { return this.date.isAfter(((MyLocalDate1)other).date); }
    public MyLocalDate1 plusDays(long days) { return new MyLocalDate1(date.plusDays(days)); }
    public static MyLocalDate1 of(int year, int month, int dayOfMonth) { return new MyLocalDate1(LocalDate.of(year, month, dayOfMonth)); }
    public static MyLocalDate1 parse(CharSequence text) { return new MyLocalDate1(LocalDate.parse(text)); }
    @Override public Chronology getChronology() { return null; }
    @Override public int lengthOfMonth() { return 0; }
    @Override public long until(Temporal endExclusive, TemporalUnit unit) { return 0; }
    @Override public ChronoPeriod until(ChronoLocalDate endDateExclusive) { return null; }
    @Override public long getLong(TemporalField field) { return 0; }
  }
  
  @Test public void autoImport() throws NoSuchMethodException {
    Jactl.createClass("test.jactl.time.LocalDate1")
         .javaClass(MyLocalDate1.class)
         .autoImport(true)
         .mapType(ChronoLocalDate.class, MyLocalDate1.class)
         .method("isAfter", "isAfter", "other", ChronoLocalDate.class)
         .method("getYear", "getYear")
         .method("lengthOfYear", "lengthOfYear")
         .method("now", "now")
         .methodCanThrow("of", "of", "year", int.class, "month", int.class, "day", int.class)
         .methodCanThrow("parse", "parse", "text", CharSequence.class)
         .methodCanThrow("plusDays", "plusDays", "days", long.class)
         .checkpoint((checkpointer,obj) ->  {
           MyLocalDate1 d = (MyLocalDate1) obj;
           checkpointer.writeCInt(d.getYear());
           checkpointer.writeCInt(d.getMonthValue());
           checkpointer.writeCInt(d.getDayOfMonth());
         })
         .restore(restorer -> MyLocalDate1.of(restorer.readCInt(), restorer.readCInt(), restorer.readCInt()))
         .register();
    
    test("test.jactl.time.LocalDate1.now().lengthOfYear() in [365, 366]", true);
    test("def now = LocalDate1.now(); now.lengthOfYear() > 360", true);
    test("LocalDate1 now = LocalDate1.now(); now.lengthOfYear() > 360", true);
    test("def now = LocalDate1.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("LocalDate1 now = LocalDate1.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("def date = LocalDate1.parse('2026-01-15'); date.getYear()", 2026);
    test("LocalDate1 date = LocalDate1.parse('2026-01-15'); date.getYear()", 2026);
    test("def date = LocalDate1.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("LocalDate1 date = LocalDate1.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("def n = LocalDate1.now; n().lengthOfYear() > 360", true);
    test("def len = LocalDate1.now().lengthOfYear; len() > 360", true);
    test("LocalDate1 now = LocalDate1.now(); now.lengthOfYear() > 360", true);
    test("LocalDate1 now = LocalDate1.now(); def x = now.lengthOfYear(); LocalDate1 tomorrow = now.plusDays(1); tomorrow.lengthOfYear() > 360", true);
    test("LocalDate1 now = LocalDate1.now(); def tomorrow = now.plusDays(1); tomorrow.lengthOfYear() > 360", true);
    test("LocalDate1 tomorrow = LocalDate1.now().plusDays(1); tomorrow.lengthOfYear() > 360", true);
    test("def now = LocalDate1.now(); def tomorrow = now.plusDays(1); tomorrow.lengthOfYear() > 360", true);
    test("LocalDate1 now = LocalDate1.now().plusDays(1); now.isAfter(LocalDate1.now())", true);
    test("def now = LocalDate1.now().plusDays(1); now.isAfter(LocalDate1.now())", true);

    testError("def date = LocalDate1.parse('2026-0115'); date.getYear()", "could not be parsed");

    test("LocalDate1 now = LocalDate1.now(); def f = now.now; f().lengthOfYear() in [365,366]", true);
  }

  public static class MyLocalDate2 implements ChronoLocalDate {
    private LocalDate date;
    private MyLocalDate2(LocalDate date) { this.date = date; }
    public static MyLocalDate2 now() { return new MyLocalDate2(LocalDate.now()); }
    public int getYear() { return date.getYear(); }
    public int getMonthValue() { return date.getMonthValue(); }
    public int getDayOfMonth() { return date.getDayOfMonth(); }
    public int lengthOfYear() { return date.lengthOfYear(); }
    public boolean isAfter(ChronoLocalDate other) { return this.date.isAfter(((MyLocalDate2)other).date); }
    public MyLocalDate2 plusDays(long days) { return new MyLocalDate2(date.plusDays(days)); }
    public static MyLocalDate2 of(int year, int month, int dayOfMonth) { return new MyLocalDate2(LocalDate.of(year, month, dayOfMonth)); }
    public static MyLocalDate2 parse(CharSequence text) { return new MyLocalDate2(LocalDate.parse(text)); }
    @Override public Chronology getChronology() { return null; }
    @Override public int lengthOfMonth() { return 0; }
    @Override public long until(Temporal endExclusive, TemporalUnit unit) { return 0; }
    @Override public ChronoPeriod until(ChronoLocalDate endDateExclusive) { return null; }
    @Override public long getLong(TemporalField field) { return 0; }
  }

  @Test public void noAutoImport() throws NoSuchMethodException {
    Jactl.createClass("test.jactl.time.LocalDate2")
         .javaClass(MyLocalDate2.class)
         .autoImport(false)
         .mapType(ChronoLocalDate.class, MyLocalDate2.class)
         .method("getYear", "getYear")
         .method("lengthOfYear", "lengthOfYear")
         .method("now", "now")
         .methodCanThrow("of", "of", "year", int.class, "month", int.class, "day", int.class)
         .methodCanThrow("parse", "parse", "text", CharSequence.class)
         .checkpoint((checkpointer,obj) ->  {
           MyLocalDate2 d = (MyLocalDate2) obj;
           checkpointer.writeCInt(d.getYear());
           checkpointer.writeCInt(d.getMonthValue());
           checkpointer.writeCInt(d.getDayOfMonth());
         })
         .restore(restorer -> MyLocalDate2.of(restorer.readCInt(), restorer.readCInt(), restorer.readCInt()))
         .register();

    test("test.jactl.time.LocalDate2.now().lengthOfYear() in [365, 366]", true);
    testError("def now = LocalDate2.now(); now.lengthOfYear() > 360", "unknown variable 'LocalDate2'");
    testError("LocalDate2 now = LocalDate2.now()", "unknown variable 'LocalDate2'");
    testError("LocalDate2 now = LocalDate2.now(); now.lengthOfYear() > 360", "unknown variable 'LocalDate2'");
    test("import test.jactl.time.LocalDate2; def now = LocalDate2.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("import test.jactl.time.LocalDate2; LocalDate2 now = LocalDate2.now(); now = now.now(); now.lengthOfYear() in [365,366]", true);
    test("import test.jactl.time.LocalDate2; def date = LocalDate2.parse('2026-01-15'); date.getYear()", 2026);
    test("import test.jactl.time.LocalDate2; LocalDate2 date = LocalDate2.parse('2026-01-15'); date.getYear()", 2026);
    test("import test.jactl.time.LocalDate2; def date = LocalDate2.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("import test.jactl.time.LocalDate2; LocalDate2 date = test.jactl.time.LocalDate2.parse(text:'2026-01-15'); date.getYear()", 2026);
    test("import test.jactl.time.LocalDate2; def n = LocalDate2.now; n().lengthOfYear() > 360", true);
    test("def n = test.jactl.time.LocalDate2.now; n().lengthOfYear() > 360", true);
    test("import test.jactl.time.LocalDate2 as XXX; def len = XXX.now().lengthOfYear; len() > 360", true);

    testError("import test.jactl.time.LocalDate2 as XXX; def date = XXX.parse('2026-0115'); date.getYear()", "could not be parsed");
  }

//  @Test public void perContextClass() {
//    // Test adding new class to specific JactlContext and globally
//    fail("not yet implemented");
//  }

  public static class MyLocalDate3 implements ChronoLocalDate {
    private LocalDate date;
    private MyLocalDate3(LocalDate date) { this.date = date; }
    public static MyLocalDate3 now() { return new MyLocalDate3(LocalDate.now()); }
    public int getYear() { return date.getYear(); }
    public int getMonthValue() { return date.getMonthValue(); }
    public int getDayOfMonth() { return date.getDayOfMonth(); }
    public int lengthOfYear() { return date.lengthOfYear(); }
    public boolean isAfter(ChronoLocalDate other) { return this.date.isAfter(((MyLocalDate3)other).date); }
    public MyLocalDate3 plusDays(long days) { return new MyLocalDate3(date.plusDays(days)); }
    public static MyLocalDate3 of(int year, int month, int dayOfMonth) { return new MyLocalDate3(LocalDate.of(year, month, dayOfMonth)); }
    public static MyLocalDate3 parse(CharSequence text) { return new MyLocalDate3(LocalDate.parse(text)); }
    @Override public String toString() { return date.toString(); }
    @Override public Chronology getChronology() { return null; }
    @Override public int lengthOfMonth() { return 0; }
    @Override public long until(Temporal endExclusive, TemporalUnit unit) { return 0; }
    @Override public ChronoPeriod until(ChronoLocalDate endDateExclusive) { return null; }
    @Override public long getLong(TemporalField field) { return 0; }
  }

  @Test public void overloadedClassName() throws NoSuchMethodException {
    skipCheckpointTests = true;
    Jactl.createClass("jactl.time.X__")
         .javaClass(MyLocalDate3.class)
         .autoImport(true)
         .mapType(ChronoLocalDate.class, LocalDate.class)
         .method("parse", "parse", "text", CharSequence.class)
         .method("toString", "toString")
         .register();

    test("X__.parse('2026-01-15').toString()", "2026-01-15");
    test("def x = X__.parse('2026-01-15'); x.toString()", "2026-01-15");
    test("class X__ { const int A=1, B=2, C=3 }; int x = 2; switch (x) { X__.A -> 'a'; X__.B -> 'b'; X__.C -> 'c' }", "b");
    test("package a.b.c; class X__ { const int A=1, B=2, C=3 }; int x = 2; switch (x) { a.b.c.X__.A -> 'a'; X__.B -> 'b'; X__.C -> 'c' }", "b");
    testError(Utils.listOf("package a.b.c; class X__ { const int A=1, B=2, C=3 }"), "package a.b.c; import X__; int x = 2; switch (x) { a.b.c.X__.A -> 'a'; X__.B -> 'b'; X__.C -> 'c' }", "already auto-imported");
    test(Utils.listOf("package a.b.c; class X__ { const int A=1, B=2, C=3 }"), "package a.b.c; import a.b.c.X__ as Y; int x = 2; switch (x) { a.b.c.X__.A -> 'a'; Y.B -> 'b'; Y.C -> 'c' }", "b");

    // Also test toString()
    test("'abc'.substring(1,2).toString()", "b");
    test("def x = 'abc'.substring(1,2); x.toString()", "b");
    test("class Y { int i = 3 }; def y = new Y(); y.toString()", "[i:3]");
  }

  public static class MyLocalDate4 implements ChronoLocalDate {
    private LocalDate date;
    private MyLocalDate4(LocalDate date) { this.date = date; }
    public static MyLocalDate4 now() { return new MyLocalDate4(LocalDate.now()); }
    public int getYear() { return date.getYear(); }
    public int getMonthValue() { return date.getMonthValue(); }
    public int getDayOfMonth() { return date.getDayOfMonth(); }
    public int lengthOfYear() { return date.lengthOfYear(); }
    public boolean isAfter(ChronoLocalDate other) { return this.date.isAfter(((MyLocalDate4)other).date); }
    public MyLocalDate4 plusDays(long days) { return new MyLocalDate4(date.plusDays(days)); }
    public static MyLocalDate4 of(int year, int month, int dayOfMonth) { return new MyLocalDate4(LocalDate.of(year, month, dayOfMonth)); }
    public static MyLocalDate4 parse(CharSequence text) { return new MyLocalDate4(LocalDate.parse(text)); }
    @Override public String toString() { return date.toString(); }
    @Override public Chronology getChronology() { return null; }
    @Override public int lengthOfMonth() { return 0; }
    @Override public long until(Temporal endExclusive, TemporalUnit unit) { return 0; }
    @Override public ChronoPeriod until(ChronoLocalDate endDateExclusive) { return null; }
    @Override public long getLong(TemporalField field) { return 0; }
  }

  @Test public void addFunctionToNewClass() throws NoSuchMethodException {
    Jactl.createClass("test.jactl.time.LocalDate4")
         .javaClass(MyLocalDate4.class)
         .autoImport(true)
         .mapType(ChronoLocalDate.class, MyLocalDate4.class)
         .method("isAfter", "isAfter", "other", ChronoLocalDate.class)
         .method("getYear", "getYear")
         .method("lengthOfYear", "lengthOfYear")
         .method("now", "now")
         .methodCanThrow("of", "of", "year", int.class, "month", int.class, "day", int.class)
         .methodCanThrow("parse", "parse", "text", CharSequence.class)
         .methodCanThrow("plusDays", "plusDays", "days", long.class)
         .checkpoint((checkpointer,obj) ->  {
           MyLocalDate4 d = (MyLocalDate4) obj;
           checkpointer.writeCInt(d.getYear());
           checkpointer.writeCInt(d.getMonthValue());
           checkpointer.writeCInt(d.getDayOfMonth());
         })
         .restore(restorer -> MyLocalDate4.of(restorer.readCInt(), restorer.readCInt(), restorer.readCInt()))
         .register();

    JactlType type = JactlContext.typeFromClass(MyLocalDate4.class, (JactlContext)null);
    Jactl.method(type).name("testMethod").impl(RegisterClassTests.class, "testLocalDateMethod").register();
    test("LocalDate4.now().testMethod()", 3);
  }

  public static int testLocalDateMethod(MyLocalDate4 localDate) {
    return 3;
  }
}
