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

package io.jactl.runtime;

import io.jactl.Jactl;
import io.jactl.JactlType;
import io.jactl.Pair;

import java.time.*;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoLocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.Stream;

import static io.jactl.runtime.JactlFunction.MANDATORY;

public class DateTimeClasses {
  private static boolean initialised = false;
  private static boolean dateTimeEnabled = Boolean.parseBoolean(System.getProperty("io.jactl.dateTimeClasses.enabled", "true"));
  private static boolean autoImport = Boolean.parseBoolean(System.getProperty("io.jactl.dateTimeClasses.autoImport", "true"));
  
  public static void register() {
    if (initialised || !dateTimeEnabled) return;
    initialised = true;

    try {
      Jactl.declareClass("jactl.time.ZoneId", ZoneId.class);
      Jactl.declareClass("jactl.time.Instant", Instant.class);
      Jactl.declareClass("jactl.time.Duration", Duration.class);
      Jactl.declareClass("jactl.time.Period", Period.class);
      Jactl.declareClass("jactl.time.LocalDateTime", LocalDateTime.class);
      Jactl.declareClass("jactl.time.ZonedDateTime", ZonedDateTime.class);
      Jactl.declareClass("jactl.time.LocalDate", LocalDate.class);

      Jactl.createClass("jactl.time.TemporalAmount")
           .javaClass(java.time.temporal.TemporalAmount.class)
           .autoImport(autoImport)
           .register();

      Jactl.createClass("jactl.time.Temporal")
           .javaClass(java.time.temporal.Temporal.class)
           .autoImport(autoImport)
           .register();

      // LocalTime
      JactlType localTimeType = Jactl.createClass("jactl.time.LocalTime")
                                     .javaClass(java.time.LocalTime.class)
                                     .baseClass("jactl.time.Temporal")
                                     .autoImport(autoImport)
                                     //.method("adjustInto", "adjustInto", "arg1", Temporal.class)
                                     .method("atDate", "atDate", "date", LocalDate.class)
                                     //.method("atOffset", "atOffset", "arg1", ZoneOffset.class)
                                     //.method("format", "format", "arg1", DateTimeFormatter.class)
                                     //.method("from", "from", "arg1", TemporalAccessor.class)
                                     //.method("get", "get", "arg1", TemporalField.class)
                                     .method("getHour", "getHour")
                                     //.method("getLong", "getLong", "arg1", TemporalField.class)
                                     .method("getMinute", "getMinute")
                                     .method("getNano", "getNano")
                                     .method("getSecond", "getSecond")
                                     .method("isAfter", "isAfter", "other", LocalTime.class)
                                     .method("isBefore", "isBefore", "other", LocalTime.class)
                                     //.method("isSupported", "isSupported", "arg1", TemporalField.class)
                                     //.method("isSupported", "isSupported", "arg1", TemporalUnit.class)
                                     //.method("minus", "minus", "arg1", long.class, "arg2", TemporalUnit.class)
                                     .methodCanThrow("minus", "minus", "amount", TemporalAmount.class)
                                     .methodCanThrow("minusHours", "minusHours", "hours", long.class)
                                     .methodCanThrow("minusMinutes", "minusMinutes", "minutes", long.class)
                                     .methodCanThrow("minusNanos", "minusNanos", "nanos", long.class)
                                     .methodCanThrow("minusSeconds", "minusSeconds", "seconds", long.class)
                                     .method("now", "now")
                                     //.method("now", "now", "arg1", Clock.class)
                                     .method("nowInZone", "now", "zoneId", ZoneId.class)
                                     .methodCanThrow("ofNanoOfDay", "ofNanoOfDay", "nanoOfDay", long.class)
                                     .methodCanThrow("ofSecondOfDay", "ofSecondOfDay", "second", long.class)
                                     .methodCanThrow("parse", "parse", "text", CharSequence.class)
                                     //.method("parseWithFormat", "parse", "arg1", CharSequence.class, "arg2", DateTimeFormatter.class)
                                     .methodCanThrow("plus", "plus", "amount", TemporalAmount.class)
                                     //.method("plus", "plus", "arg1", long.class, "arg2", TemporalUnit.class)
                                     .methodCanThrow("plusHours", "plusHours", "hours", long.class)
                                     .methodCanThrow("plusMinutes", "plusMinutes", "minutes", long.class)
                                     .methodCanThrow("plusNanos", "plusNanos", "nanos", long.class)
                                     .methodCanThrow("plusSeconds", "plusSeconds", "seconds", long.class)
                                     //.method("query", "query", "arg1", TemporalQuery.class)
                                     //.method("range", "range", "arg1", TemporalField.class)
                                     .method("toNanoOfDay", "toNanoOfDay")
                                     .method("toSecondOfDay", "toSecondOfDay")
                                     //.method("truncatedTo", "truncatedTo", "arg1", TemporalUnit.class)
                                     //.method("until", "until", "arg1", Temporal.class, "arg2", TemporalUnit.class)
                                     //.method("with", "with", "arg1", TemporalAdjuster.class)
                                     //.method("with", "with", "arg1", TemporalField.class, "arg2", long.class)
                                     .methodCanThrow("withHour", "withHour", "hour", int.class)
                                     .methodCanThrow("withMinute", "withMinute", "minute", int.class)
                                     .methodCanThrow("withNano", "withNano", "nano", int.class)
                                     .methodCanThrow("withSecond", "withSecond", "second", int.class)
                                     .checkpoint((checkpointer, obj) -> {
                                       LocalTime d = (LocalTime) obj;
                                       checkpointer.writeLong(d.toNanoOfDay());
                                     })
                                     .restore(restorer -> LocalTime.ofNanoOfDay(restorer.readLong()))
                                     .register();

      Jactl.method(localTimeType).name("format").param("format").impl(DateTimeClasses.class, "localTimeFormat").register();
      Jactl.method(localTimeType).name("of").isStatic(true)
           .param("hour").param("minute").param("second", 0).param("nano", 0)
           .impl(DateTimeClasses.class, "localTimeOf").register();
      Jactl.method(localTimeType).name("parseWithFormat").isStatic(true).param("text").param("format")
           .impl(DateTimeClasses.class, "localTimeParseWithFormat").register();
      Jactl.method(localTimeType).name("truncatedToMicros").impl(DateTimeClasses.class, "localTimeTruncatedToMicros").register();
      Jactl.method(localTimeType).name("truncatedToMillis").impl(DateTimeClasses.class, "localTimeTruncatedToMillis").register();
      Jactl.method(localTimeType).name("truncatedToSeconds").impl(DateTimeClasses.class, "localTimeTruncatedToSeconds").register();
      Jactl.method(localTimeType).name("truncatedToMinutes").impl(DateTimeClasses.class, "localTimeTruncatedToMinutes").register();
      Jactl.method(localTimeType).name("truncatedToHours").impl(DateTimeClasses.class, "localTimeTruncatedToHours").register();
      Jactl.method(localTimeType).name("until").param("end").impl(DateTimeClasses.class, "localTimeUntil").register();

      // LocalDate
      JactlType localDateType = Jactl.createClass("jactl.time.LocalDate")
                                     .javaClass(LocalDate.class)
                                     .baseClass("jactl.time.Temporal")
                                     .autoImport(autoImport)
                                     .mapType(ChronoLocalDate.class, LocalDate.class)
                                     .method("atStartOfDay", "atStartOfDay")
                                     .method("atStartOfDayInZone", "atStartOfDay", "zoneId", ZoneId.class)
                                     .method("atTime", "atTime", "time", LocalTime.class)
                                     .method("getDayOfMonth", "getDayOfMonth")
                                     .method("getDayOfYear", "getDayOfYear")
                                     .method("getMonthValue", "getMonthValue")
                                     .method("getYear", "getYear")
                                     .method("isAfter", "isAfter", "other", ChronoLocalDate.class)
                                     .method("isBefore", "isBefore", "other", ChronoLocalDate.class)
                                     .method("isEqual", "isEqual", "other", ChronoLocalDate.class)
                                     .method("isLeapYear", "isLeapYear")
                                     .method("lengthOfMonth", "lengthOfMonth")
                                     .method("lengthOfYear", "lengthOfYear")
                                     .methodCanThrow("minusDays", "minusDays", "days", long.class)
                                     .methodCanThrow("minus", "minus", "amount", TemporalAmount.class)
                                     .methodCanThrow("minusMonths", "minusMonths", "months", long.class)
                                     .methodCanThrow("minusWeeks", "minusWeeks", "weeks", long.class)
                                     .methodCanThrow("minusYears", "minusYears", "years", long.class)
                                     .method("now", "now")
                                     .method("nowInZone", "now", "zone", ZoneId.class)
                                     .methodCanThrow("of", "of", "year", int.class, "month", int.class, "day", int.class)
                                     .methodCanThrow("ofEpochDay", "ofEpochDay", "epochDay", long.class)
                                     .methodCanThrow("ofYearDay", "ofYearDay", "year", int.class, "dayOfYear", int.class)
                                     .methodCanThrow("parse", "parse", "text", CharSequence.class)
                                     .methodCanThrow("plus", "plus", "amount", TemporalAmount.class)
                                     .methodCanThrow("plusDays", "plusDays", "days", long.class)
                                     .methodCanThrow("plusMonths", "plusMonths", "months", long.class)
                                     .methodCanThrow("plusWeeks", "plusWeeks", "weeks", long.class)
                                     .methodCanThrow("plusYears", "plusYears", "years", long.class)
                                     .method("toEpochDay", "toEpochDay")
                                     .method("until", "until", "end", ChronoLocalDate.class)
                                     .methodCanThrow("withDayOfMonth", "withDayOfMonth", "day", int.class)
                                     .methodCanThrow("withDayOfYear", "withDayOfYear", "day", int.class)
                                     .methodCanThrow("withMonth", "withMonth", "month", int.class)
                                     .methodCanThrow("withYear", "withYear", "year", int.class)
                                     //                                     .method("adjustInto", "adjustInto", "adjuster", Temporal.class)
                                     //                                     .method("atTimeOffset", "atTime", "time", OffsetTime.class)
                                     //                                     .method("from", "from", "temporal", TemporalAccessor.class)
                                     //                                     .method("get", "get", "field", TemporalField.class)
                                     //                                     .method("getLong", "getLong", "field", TemporalField.class)
                                     //                                     .method("minus", "minus", "amount", long.class, "unit", TemporalUnit.class)
                                     //                                     .method("of", "of", "arg1", int.class, "arg2", Month.class, "arg3", int.class)
                                     //                                     .method("plus", "plus", "amount", long.class, "unit", TemporalUnit.class)
                                     //                                     .method("query", "query", "arg1", TemporalQuery.class)
                                     //                                     .method("range", "range", "arg1", TemporalField.class)
                                     //                                     .method("until", "until", "exclusive", Temporal.class, "unit", TemporalUnit.class)
                                     //                                     .method("with", "with", "field", TemporalField.class, "value", long.class)
                                     .checkpoint((checkpointer, obj) -> {
                                       LocalDate d = (LocalDate) obj;
                                       checkpointer.writeCInt(d.getYear());
                                       checkpointer.writeCInt(d.getMonthValue());
                                       checkpointer.writeCInt(d.getDayOfMonth());
                                     })
                                     .restore(restorer -> LocalDate.of(restorer.readCInt(), restorer.readCInt(), restorer.readCInt()))
                                     .register();

      Jactl.method(localDateType).name("getDayOfWeek").impl(DateTimeClasses.class, "localDateGetDayOfWeek").register();
      Jactl.method(localDateType).name("getMonth").impl(DateTimeClasses.class, "localDateGetMonth").register();
      Jactl.method(localDateType).name("parseWithFormat").isStatic(true).param("text").param("format")
           .impl(DateTimeClasses.class, "localDateParseWithFormat").register();
      Jactl.method(localDateType).name("format").param("format")
           .impl(DateTimeClasses.class, "localDateFormat").register();

      // LocalDateTime
      JactlType localDateTimeType =
        Jactl.createClass("jactl.time.LocalDateTime")
             .javaClass(LocalDateTime.class)
             .baseClass("jactl.time.Temporal")
             .autoImport(autoImport)
             .mapType(ChronoLocalDateTime.class, LocalDateTime.class)
             .method("atZone", "atZone", "zoneId", ZoneId.class)
             .method("getDayOfMonth", "getDayOfMonth")
             .method("getDayOfYear", "getDayOfYear")
             .method("getHour", "getHour")
             .method("getMinute", "getMinute")
             .method("getMonthValue", "getMonthValue")
             .method("getNano", "getNano")
             .method("getSecond", "getSecond")
             .method("getYear", "getYear")
             .method("isAfter", "isAfter", "other", ChronoLocalDateTime.class)
             .method("isBefore", "isBefore", "other", ChronoLocalDateTime.class)
             .method("isEqual", "isEqual", "other", ChronoLocalDateTime.class)
             .methodCanThrow("minus", "minus", "amount", TemporalAmount.class)
             .methodCanThrow("minusHours", "minusHours", "hours", long.class)
             .methodCanThrow("minusMinutes", "minusMinutes", "minutes", long.class)
             .methodCanThrow("minusNanos", "minusNanos", "nanos", long.class)
             .methodCanThrow("minusSeconds", "minusSeconds", "seconds", long.class)
             .methodCanThrow("minusDays", "minusDays", "days", long.class)
             .methodCanThrow("minusMonths", "minusMonths", "months", long.class)
             .methodCanThrow("minusWeeks", "minusWeeks", "weeks", long.class)
             .methodCanThrow("minusYears", "minusYears", "years", long.class)
             .method("now", "now")
             .method("nowInZone", "now", "zone", ZoneId.class)
             .methodCanThrow("ofDateTime", "of", "date", LocalDate.class, "time", LocalTime.class)
             .methodCanThrow("ofInstant", "ofInstant", "instant", Instant.class, "zoneId", ZoneId.class)
             .methodCanThrow("parse", "parse", "text", CharSequence.class)
             .methodCanThrow("plus", "plus", "amount", TemporalAmount.class)
             .methodCanThrow("plusDays", "plusDays", "days", long.class)
             .methodCanThrow("plusHours", "plusHours", "hours", long.class)
             .methodCanThrow("plusMinutes", "plusMinutes", "minutes", long.class)
             .methodCanThrow("plusMonths", "plusMonths", "months", long.class)
             .methodCanThrow("plusNanos", "plusNanos", "nanos", long.class)
             .methodCanThrow("plusSeconds", "plusSeconds", "seconds", long.class)
             .methodCanThrow("plusWeeks", "plusWeeks", "weeks", long.class)
             .methodCanThrow("plusYears", "plusYears", "years", long.class)
             .method("toLocalDate", "toLocalDate")
             .method("toLocalTime", "toLocalTime")
             .methodCanThrow("withDayOfMonth", "withDayOfMonth", "day", int.class)
             .methodCanThrow("withDayOfYear", "withDayOfYear", "day", int.class)
             .methodCanThrow("withHour", "withHour", "hour", int.class)
             .methodCanThrow("withMinute", "withMinute", "minute", int.class)
             .methodCanThrow("withMonth", "withMonth", "month", int.class)
             .methodCanThrow("withNano", "withNano", "nano", int.class)
             .methodCanThrow("withSecond", "withSecond", "second", int.class)
             .methodCanThrow("withYear", "withYear", "year", int.class)
             //           .method("adjustInto", "adjustInto", "arg1", Temporal.class)
             //           .method("atOffset", "atOffset", "arg1", ZoneOffset.class)
             //           .method("from", "from", "arg1", TemporalAccessor.class)
             //           .method("get", "get", "arg1", TemporalField.class)
             //           .method("getLong", "getLong", "arg1", TemporalField.class)
             //           .method("isSupported", "isSupported", "arg1", TemporalUnit.class)
             //           .method("isSupported", "isSupported", "arg1", TemporalField.class)
             //           .method("minus", "minus", "arg1", long.class, "arg2", TemporalUnit.class)
             //           .method("now", "now", "arg1", Clock.class)
             //           .method("of", "of", "arg1", int.class, "arg2", Month.class, "arg3", int.class, "arg4", int.class, "arg5", int.class, "arg6", int.class, "arg7", int.class)
             //           .method("of", "of", "arg1", int.class, "arg2", Month.class, "arg3", int.class, "arg4", int.class, "arg5", int.class, "arg6", int.class)
             //           .method("of", "of", "arg1", int.class, "arg2", Month.class, "arg3", int.class, "arg4", int.class, "arg5", int.class)
             //           .method("plus", "plus", "arg1", long.class, "arg2", TemporalUnit.class)
             //           .method("query", "query", "arg1", TemporalQuery.class)
             //           .method("range", "range", "arg1", TemporalField.class)
             //           .method("truncatedTo", "truncatedTo", "arg1", TemporalUnit.class)
             //           .method("until", "until", "arg1", Temporal.class, "arg2", TemporalUnit.class)
             //           .method("with", "with", "arg1", TemporalField.class, "arg2", long.class)
             //           .method("with", "with", "arg1", TemporalAdjuster.class)
             .checkpoint((checkpointer, obj) -> {
               LocalDateTime t = (LocalDateTime) obj;
               checkpointer.writeCInt(t.getYear());
               checkpointer.writeCInt(t.getMonthValue());
               checkpointer.writeCInt(t.getDayOfMonth());
               checkpointer.writeCInt(t.getHour());
               checkpointer.writeCInt(t.getMinute());
               checkpointer.writeCInt(t.getSecond());
               checkpointer.writeCInt(t.getNano());
             })
             .restore(r -> LocalDateTime.of(r.readCInt(), r.readCInt(), r.readCInt(),
                                            r.readCInt(), r.readCInt(), r.readCInt(), r.readCInt()))
             .register();
      Jactl.method(localDateTimeType).name("getDayOfWeek").impl(DateTimeClasses.class, "localDateTimeGetDayOfWeek").register();
      Jactl.method(localDateTimeType).name("getMonth").impl(DateTimeClasses.class, "localDateTimeGetMonth").register();
      Jactl.method(localDateTimeType).name("of").isStatic(true)
           .param("year").param("month").param("day")
           .param("hour").param("minute").param("second", 0).param("nano", 0)
           .impl(DateTimeClasses.class, "localDateTimeOf").register();
      Jactl.method(localDateTimeType).name("ofEpochSecond").isStatic(true)
           .param("second").param("nano", 0)
           .impl(DateTimeClasses.class, "localDateTimeOfEpochSecond").register();
      Jactl.method(localDateTimeType).name("format").param("format")
           .impl(DateTimeClasses.class, "localDateTimeFormat").register();
      Jactl.method(localDateTimeType).name("parseWithFormat").isStatic(true).param("text").param("format")
           .impl(DateTimeClasses.class, "localDateTimeParseWithFormat").register();
      Jactl.method(localDateTimeType).name("truncatedToMicros").impl(DateTimeClasses.class, "localDateTimeTruncatedToMicros").register();
      Jactl.method(localDateTimeType).name("truncatedToMillis").impl(DateTimeClasses.class, "localDateTimeTruncatedToMillis").register();
      Jactl.method(localDateTimeType).name("truncatedToSeconds").impl(DateTimeClasses.class, "localDateTimeTruncatedToSeconds").register();
      Jactl.method(localDateTimeType).name("truncatedToMinutes").impl(DateTimeClasses.class, "localDateTimeTruncatedToMinutes").register();
      Jactl.method(localDateTimeType).name("truncatedToHours").impl(DateTimeClasses.class, "localDateTimeTruncatedToHours").register();
      Jactl.method(localDateTimeType).name("truncatedToDays").impl(DateTimeClasses.class, "localDateTimeTruncatedToDays").register();

      // ZoneId
      JactlType zoneIdType = Jactl.createClass("jactl.time.ZoneId")
                                  .javaClass(java.time.ZoneId.class)
                                  .autoImport(autoImport)
                                  //.method("from", "from", "arg1", TemporalAccessor.class)
                                  //.method("getDisplayName", "getDisplayName", "arg1", TextStyle.class, "arg2", Locale.class)
                                  .method("getId", "getId")
                                  //.method("getRules", "getRules")
                                  .method("normalized", "normalized")
                                  //.method("of", "of", "arg1", String.class, "arg2", Map.class)
                                  //.method("ofOffset", "ofOffset", "arg1", String.class, "arg2", ZoneOffset.class)
                                  .methodCanThrow("systemDefault", "systemDefault")
                                  .checkpoint((checkpointer, obj) -> checkpointer.writeObject(((ZoneId) obj).getId()))
                                  .restore(restorer -> ZoneId.of((String) restorer.readObject()))
                                  .register();
      Jactl.method(zoneIdType).name("of").isStatic(true).param("zone").impl(DateTimeClasses.class, "zoneIdOf").register();
      Jactl.method(zoneIdType).name("isValid").isStatic(true).param("zone").impl(DateTimeClasses.class, "zoneIdIsValid").register();
      Jactl.method(zoneIdType).name("getAvailableZoneIds").isStatic(true).impl(DateTimeClasses.class, "zoneIdGetAvailableZoneIds").register();

      // Period
      Jactl.createClass("jactl.time.Period")
           .baseClass("jactl.time.TemporalAmount")
           .javaClass(java.time.Period.class)
           .autoImport(autoImport)
           //.mapType(TemporalAmount.class, Period.class)
           //.method("addTo", "addTo", "arg1", Temporal.class)
           .method("between", "between", "start", LocalDate.class, "end", LocalDate.class)
           //.method("from", "from", "arg1", TemporalAmount.class)
           //.method("get", "get", "arg1", TemporalUnit.class)
           //.method("getChronology", "getChronology")
           .method("getDays", "getDays")
           .method("getMonths", "getMonths")
           //.method("getUnits", "getUnits")
           .method("getYears", "getYears")
           .method("isNegative", "isNegative")
           .method("isZero", "isZero")
           .methodCanThrow("minus", "minus", "other", TemporalAmount.class)
           .methodCanThrow("minusDays", "minusDays", "days", long.class)
           .methodCanThrow("minusMonths", "minusMonths", "months", long.class)
           .methodCanThrow("minusYears", "minusYears", "years", long.class)
           .methodCanThrow("multipliedBy", "multipliedBy", "scalar", int.class)
           .method("negated", "negated")
           .methodCanThrow("normalized", "normalized")
           .method("of", "of", "years", int.class, "months", int.class, "days", int.class)
           .method("ofDays", "ofDays", "days", int.class)
           .method("ofMonths", "ofMonths", "months", int.class)
           .method("ofWeeks", "ofWeeks", "weeks", int.class)
           .method("ofYears", "ofYears", "years", int.class)
           .methodCanThrow("parse", "parse", "text", CharSequence.class)
           .methodCanThrow("plus", "plus", "other", TemporalAmount.class)
           .methodCanThrow("plusDays", "plusDays", "days", long.class)
           .methodCanThrow("plusMonths", "plusMonths", "months", long.class)
           .methodCanThrow("plusYears", "plusYears", "years", long.class)
           //.method("subtractFrom", "subtractFrom", "arg1", Temporal.class)
           .method("toTotalMonths", "toTotalMonths")
           .method("withDays", "withDays", "days", int.class)
           .method("withMonths", "withMonths", "months", int.class)
           .method("withYears", "withYears", "years", int.class)
           .checkpoint((checkpointer, obj) -> {
             Period p = (Period) obj;
             checkpointer.writeCInt(p.getYears());
             checkpointer.writeCInt(p.getMonths());
             checkpointer.writeCInt(p.getDays());
           })
           .restore(restorer -> Period.of(restorer.readCInt(), restorer.readCInt(), restorer.readCInt()))
           .register();

      // Duration
      JactlType durationType
        = Jactl.createClass("jactl.time.Duration")
               .javaClass(Duration.class)
               .baseClass("jactl.time.TemporalAmount")
               .autoImport(autoImport)
               //.mapType(TemporalAmount.class, Duration.class)
               .methodCanThrow("abs", "abs")
               //.method("addTo", "addTo", "arg1", Temporal.class)
               .methodCanThrow("dividedBy", "dividedBy", "amount", long.class)
               //.method("from", "from", "arg1", TemporalAmount.class)
               //.method("get", "get", "arg1", TemporalUnit.class)
               .method("getNano", "getNano")
               .method("getSeconds", "getSeconds")
               //.method("getUnits", "getUnits")
               .method("isNegative", "isNegative")
               .method("isZero", "isZero")
               //.method("minus", "minus", "arg1", long.class, "arg2", TemporalUnit.class)
               .methodCanThrow("minus", "minus", "other", Duration.class)
               .methodCanThrow("minusDays", "minusDays", "days", long.class)
               .methodCanThrow("minusHours", "minusHours", "hours", long.class)
               .methodCanThrow("minusMillis", "minusMillis", "millis", long.class)
               .methodCanThrow("minusMinutes", "minusMinutes", "minutes", long.class)
               .methodCanThrow("minusNanos", "minusNanos", "nanos", long.class)
               .methodCanThrow("minusSeconds", "minusSeconds", "seconds", long.class)
               .methodCanThrow("multipliedBy", "multipliedBy", "amount", long.class)
               .methodCanThrow("negated", "negated")
               //.method("of", "of", "arg1", long.class, "arg2", TemporalUnit.class)
               .methodCanThrow("ofDays", "ofDays", "days", long.class)
               .methodCanThrow("ofHours", "ofHours", "hours", long.class)
               .methodCanThrow("ofMillis", "ofMillis", "millis", long.class)
               .methodCanThrow("ofMinutes", "ofMinutes", "minutes", long.class)
               .methodCanThrow("ofNanos", "ofNanos", "nanos", long.class)
               .methodCanThrow("ofSeconds", "ofSeconds", "seconds", long.class)
               .methodCanThrow("ofSecondsAndNanos", "ofSeconds", "seconds", long.class, "nanos", long.class)
               .methodCanThrow("parse", "parse", "text", CharSequence.class)
               //.method("plus", "plus", "arg1", long.class, "arg2", TemporalUnit.class)
               .methodCanThrow("plus", "plus", "other", Duration.class)
               .methodCanThrow("plusDays", "plusDays", "days", long.class)
               .methodCanThrow("plusHours", "plusHours", "hours", long.class)
               .methodCanThrow("plusMillis", "plusMillis", "millis", long.class)
               .methodCanThrow("plusMinutes", "plusMinutes", "minutes", long.class)
               .methodCanThrow("plusNanos", "plusNanos", "nanos", long.class)
               .methodCanThrow("plusSeconds", "plusSeconds", "seconds", long.class)
               //.methodCanThrow("subtractFrom", "subtractFrom", "other", Temporal.class)
               .method("toDays", "toDays")
               .method("toHours", "toHours")
               .method("toMillis", "toMillis")
               .method("toMinutes", "toMinutes")
               .method("toNanos", "toNanos")
               .methodCanThrow("withNanos", "withNanos", "nanos", int.class)
               .method("withSeconds", "withSeconds", "seconds", long.class)
               .checkpoint((checkpointer, obj) -> {
                 Duration d = (Duration) obj;
                 checkpointer.writeLong(d.getSeconds());
                 checkpointer.writeLong(d.getNano());
               })
               .restore(restorer -> Duration.ofSeconds(restorer.readLong(), restorer.readLong()))
               .register();
      Jactl.method(durationType).name("between").isStatic(true).param("start").param("end").impl(DateTimeClasses.class, "durationBetween").register();

      // Instant

      JactlType instantType =
        Jactl.createClass("jactl.time.Instant")
             .javaClass(java.time.Instant.class)
             .baseClass("jactl.time.Temporal")
             .autoImport(autoImport)
             //.method("adjustInto", "adjustInto", "arg1", Temporal.class)
             //.method("atOffset", "atOffset", "arg1", ZoneOffset.class)
             .method("atZone", "atZone", "zoneId", ZoneId.class)
             //.method("from", "from", "arg1", TemporalAccessor.class)
             //.method("get", "get", "arg1", TemporalField.class)
             .method("getEpochSecond", "getEpochSecond")
             //.method("getLong", "getLong", "arg1", TemporalField.class)
             .method("getNano", "getNano")
             .method("isAfter", "isAfter", "other", Instant.class)
             .method("isBefore", "isBefore", "other", Instant.class)
             //.method("isSupported", "isSupported", "arg1", TemporalField.class)
             //.method("isSupported", "isSupported", "arg1", TemporalUnit.class)
             .methodCanThrow("minus", "minus", "amount", TemporalAmount.class)
             //.method("minus", "minus", "arg1", long.class, "arg2", TemporalUnit.class)
             .methodCanThrow("minusMillis", "minusMillis", "millis", long.class)
             .methodCanThrow("minusNanos", "minusNanos", "nanos", long.class)
             .methodCanThrow("minusSeconds", "minusSeconds", "seconds", long.class)
             .method("now", "now")
             //.method("now", "now", "arg1", Clock.class)
             .methodCanThrow("ofEpochMilli", "ofEpochMilli", "milli", long.class)
             //.method("ofEpochSecond", "ofEpochSecond", "arg1", long.class)
             //.method("ofEpochSecond", "ofEpochSecond", "arg1", long.class, "arg2", long.class)
             .methodCanThrow("parse", "parse", "text", CharSequence.class)
             .methodCanThrow("plus", "plus", "amount", TemporalAmount.class)
             //.method("plus", "plus", "arg1", long.class, "arg2", TemporalUnit.class)
             .methodCanThrow("plusMillis", "plusMillis", "millis", long.class)
             .methodCanThrow("plusNanos", "plusNanos", "nanos", long.class)
             .methodCanThrow("plusSeconds", "plusSeconds", "seconds", long.class)
             //.method("query", "query", "arg1", TemporalQuery.class)
             //.method("range", "range", "arg1", TemporalField.class)
             .method("toEpochMilli", "toEpochMilli")
             //.method("truncatedTo", "truncatedTo", "arg1", TemporalUnit.class)
             //.method("until", "until", "arg1", Temporal.class, "arg2", TemporalUnit.class)
             //.method("with", "with", "arg1", TemporalField.class, "arg2", long.class)
             //.method("with", "with", "arg1", TemporalAdjuster.class)
             .checkpoint((checkpointer, obj) -> {
               Instant instant = (Instant) obj;
               checkpointer.writeLong(instant.getEpochSecond());
               checkpointer.writeCInt(instant.getNano());
             })
             .restore(restorer -> Instant.ofEpochSecond(restorer.readLong(), restorer.readCInt()))
             .register();
      Jactl.method(instantType).name("ofEpochSecond").isStatic(true)
           .param("second").param("nano", 0)
           .impl(DateTimeClasses.class, "instantOfEpochSecond").register();
      Jactl.method(instantType).name("withNano").param("nano").impl(DateTimeClasses.class, "instantWithNano").register();
      Jactl.method(instantType).name("withMilli").param("milli").impl(DateTimeClasses.class, "instantWithMilli").register();
      Jactl.method(instantType).name("withMicro").param("micro").impl(DateTimeClasses.class, "instantWithMicro").register();
      Jactl.method(instantType).name("truncatedToMicros").impl(DateTimeClasses.class, "instantTruncatedToMicros").register();
      Jactl.method(instantType).name("truncatedToMillis").impl(DateTimeClasses.class, "instantTruncatedToMillis").register();
      Jactl.method(instantType).name("truncatedToSeconds").impl(DateTimeClasses.class, "instantTruncatedToSeconds").register();
      Jactl.method(instantType).name("truncatedToMinutes").impl(DateTimeClasses.class, "instantTruncatedToMinutes").register();
      Jactl.method(instantType).name("truncatedToHours").impl(DateTimeClasses.class, "instantTruncatedToHours").register();
      Jactl.method(instantType).name("truncatedToDays").impl(DateTimeClasses.class, "instantTruncatedToDays").register();

      JactlType zonedDateTimeType =
        Jactl.createClass("jactl.time.ZonedDateTime")
             .javaClass(java.time.ZonedDateTime.class)
             .baseClass("jactl.time.Temporal")
             .autoImport(autoImport)
             //.method("from", "from", "arg1", TemporalAccessor.class)
             //.method("get", "get", "arg1", TemporalField.class)
             .method("getDayOfMonth", "getDayOfMonth")
             .method("getDayOfYear", "getDayOfYear")
             .method("getHour", "getHour")
             //.method("getLong", "getLong", "arg1", TemporalField.class)
             .method("getMinute", "getMinute")
             .method("getMonthValue", "getMonthValue")
             .method("getNano", "getNano")
             //.method("getOffset", "getOffset")
             .method("getSecond", "getSecond")
             .method("getYear", "getYear")
             .method("getZone", "getZone")
             //.method("isSupported", "isSupported", "arg1", TemporalField.class)
             //.method("isSupported", "isSupported", "arg1", TemporalUnit.class)
             .methodCanThrow("minus", "minus", "amount", TemporalAmount.class)
             //.method("minus", "minus", "arg1", long.class, "arg2", TemporalUnit.class)
             .methodCanThrow("minusDays", "minusDays", "days", long.class)
             .methodCanThrow("minusHours", "minusHours", "hours", long.class)
             .methodCanThrow("minusMinutes", "minusMinutes", "minutes", long.class)
             .methodCanThrow("minusMonths", "minusMonths", "months", long.class)
             .methodCanThrow("minusNanos", "minusNanos", "nanos", long.class)
             .methodCanThrow("minusSeconds", "minusSeconds", "seconds", long.class)
             .methodCanThrow("minusWeeks", "minusWeeks", "weeks", long.class)
             .methodCanThrow("minusYears", "minusYears", "years", long.class)
             //.method("now", "now", "arg1", Clock.class)
             .method("nowInZone", "now", "arg1", ZoneId.class)
             .method("now", "now")
             .methodCanThrow("of", "of", "year", int.class, "month", int.class, "day", int.class, "hour", int.class, "minute", int.class, "second", int.class, "nano", int.class, "zoneId", ZoneId.class)
             .method("ofDateAndTime", "of", "date", LocalDate.class, "time", LocalTime.class, "zoneId", ZoneId.class)
             .method("ofDateTime", "of", "dateTime", LocalDateTime.class, "zoneId", ZoneId.class)
             .method("ofInstant", "ofInstant", "instant", Instant.class, "zoneId", ZoneId.class)
             //.method("ofInstant", "ofInstant", "arg1", LocalDateTime.class, "arg2", ZoneOffset.class, "arg3", ZoneId.class)
             //.method("ofLocal", "ofLocal", "arg1", LocalDateTime.class, "arg2", ZoneId.class, "arg3", ZoneOffset.class)
             //.method("ofStrict", "ofStrict", "arg1", LocalDateTime.class, "arg2", ZoneOffset.class, "arg3", ZoneId.class)
             .methodCanThrow("parse", "parse", "text", CharSequence.class)
             //.method("plus", "plus", "arg1", long.class, "arg2", TemporalUnit.class)
             .method("plus", "plus", "amount", TemporalAmount.class)
             .method("plusDays", "plusDays", "days", long.class)
             .method("plusHours", "plusHours", "hours", long.class)
             .method("plusMinutes", "plusMinutes", "minutes", long.class)
             .method("plusMonths", "plusMonths", "months", long.class)
             .method("plusNanos", "plusNanos", "nanos", long.class)
             .method("plusSeconds", "plusSeconds", "seconds", long.class)
             .method("plusWeeks", "plusWeeks", "weeks", long.class)
             .method("plusYears", "plusYears", "years", long.class)
             //.method("query", "query", "arg1", TemporalQuery.class)
             //.method("range", "range", "arg1", TemporalField.class)
             .method("toLocalDate", "toLocalDate")
             .method("toLocalDateTime", "toLocalDateTime")
             .method("toLocalTime", "toLocalTime")
             //.method("toOffsetDateTime", "toOffsetDateTime")
             //.method("until", "until", "arg1", Temporal.class, "arg2", TemporalUnit.class)
             //.method("with", "with", "arg1", TemporalAdjuster.class)
             //.method("with", "with", "arg1", TemporalField.class, "arg2", long.class)
             .methodCanThrow("withDayOfMonth", "withDayOfMonth", "day", int.class)
             .methodCanThrow("withDayOfYear", "withDayOfYear", "day", int.class)
             .methodCanThrow("withHour", "withHour", "hour", int.class)
             .methodCanThrow("withMinute", "withMinute", "minute", int.class)
             .methodCanThrow("withMonth", "withMonth", "month", int.class)
             .methodCanThrow("withNano", "withNano", "nano", int.class)
             .methodCanThrow("withSecond", "withSecond", "second", int.class)
             .methodCanThrow("withYear", "withYear", "year", int.class)
             .method("withEarlierOffsetAtOverlap", "withEarlierOffsetAtOverlap")
             .method("withLaterOffsetAtOverlap", "withLaterOffsetAtOverlap")
             .method("withFixedOffsetZone", "withFixedOffsetZone")
             .method("withZoneSameInstant", "withZoneSameInstant", "zoneId", ZoneId.class)
             .method("withZoneSameLocal", "withZoneSameLocal", "zoneId", ZoneId.class)
             .checkpoint((checkpointer, obj) -> {
               ZonedDateTime t = (ZonedDateTime) obj;
               checkpointer.writeCInt(t.getYear());
               checkpointer.writeCInt(t.getMonthValue());
               checkpointer.writeCInt(t.getDayOfMonth());
               checkpointer.writeCInt(t.getHour());
               checkpointer.writeCInt(t.getMinute());
               checkpointer.writeCInt(t.getSecond());
               checkpointer.writeCInt(t.getNano());
               checkpointer.writeCInt(t.getOffset().getTotalSeconds());
               checkpointer.writeObject(t.getZone().getId());
             })
             .restore(r -> ZonedDateTime.ofStrict(LocalDateTime.of(r.readCInt(), r.readCInt(), r.readCInt(),
                                                                   r.readCInt(), r.readCInt(), r.readCInt(), r.readCInt()),
                                                 ZoneOffset.ofTotalSeconds(r.readCInt()),
                                                 ZoneId.of((String)r.readObject())))
             .register();
      Jactl.method(zonedDateTimeType).name("format").param("format")
           .impl(DateTimeClasses.class, "zonedDateTimeFormat").register();
      Jactl.method(zonedDateTimeType).name("parseWithFormat").isStatic(true).param("text").param("format")
           .impl(DateTimeClasses.class, "zonedDateTimeParseWithFormat").register();
      Jactl.method(zonedDateTimeType).name("getDayOfWeek").impl(DateTimeClasses.class, "zonedDateTimeGetDayOfWeek").register();
      Jactl.method(zonedDateTimeType).name("getMonth").impl(DateTimeClasses.class, "zonedDateTimeGetMonth").register();
      Jactl.method(zonedDateTimeType).name("truncatedToMicros").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToMicros").register();
      Jactl.method(zonedDateTimeType).name("truncatedToMillis").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToMillis").register();
      Jactl.method(zonedDateTimeType).name("truncatedToSeconds").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToSeconds").register();
      Jactl.method(zonedDateTimeType).name("truncatedToMinutes").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToMinutes").register();
      Jactl.method(zonedDateTimeType).name("truncatedToHours").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToHours").register();
      Jactl.method(zonedDateTimeType).name("truncatedToDays").impl(DateTimeClasses.class, "zonedDateTimeTruncatedToDays").register();
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  // Per-thread cache of DateTimeFormatter objects keyed on the format
  private static final ThreadLocal<Map<String, DateTimeFormatter>> dateTimeFormatters = ThreadLocal.withInitial(() ->
    new LinkedHashMap(16, 0.75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry eldest) { return size() > dateTimeFormatterCacheSize; }
    });

  public static int dateTimeFormatterCacheSize = Integer.getInteger("jactl.datetime.formatter.cache", 10);

  /////////////////////////////////////////
  
  // LocalTime
  
  public static LocalTime localTimeOf(String source, int offset, int hour, int minute, int second, int nano) {
    try {
      return LocalTime.of(hour, minute, second, nano);
    }
    catch (DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }
  
  /**
   * Implementation for LocalTime.parseWithFormat(String format).
   * Parse time with given format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param text    the text to be parsed
   * @param format  the datetime format
   * @return the parsed LocalTime
   */
  public static LocalTime localTimeParseWithFormat(String source, int offset, String text, String format) {
    try {
      return dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern).parse(text, LocalTime::from);
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  /**
   * Implementation for LocalTime.format(String format).
   * Format time with format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param time    the LocalTime object being formatted
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param format  the datetime format
   * @return the formatted date as a String
   */
  public static String localTimeFormat(LocalTime time, String source, int offset, String format) {
    try {
      return time.format(dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern));
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  public static LocalTime localTimeTruncatedToMicros(LocalTime time) { return time.truncatedTo(ChronoUnit.MICROS); }
  public static LocalTime localTimeTruncatedToMillis(LocalTime time) { return time.truncatedTo(ChronoUnit.MILLIS); }
  public static LocalTime localTimeTruncatedToSeconds(LocalTime time) { return time.truncatedTo(ChronoUnit.SECONDS); }
  public static LocalTime localTimeTruncatedToMinutes(LocalTime time) { return time.truncatedTo(ChronoUnit.MINUTES); }
  public static LocalTime localTimeTruncatedToHours(LocalTime time) { return time.truncatedTo(ChronoUnit.HOURS); }

  public static Duration localTimeUntil(LocalTime time, String source, int offset, LocalTime end) {
    try {
      return Duration.between(time, end);
    }
    catch (DateTimeException|ArithmeticException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }
  
  /////////////////////////////////////
  
  // LocalDate

  public static String localDateGetDayOfWeek(LocalDate date) {
    return date.getDayOfWeek().toString();
  }

  public static String localDateGetMonth(LocalDate date) {
    return date.getMonth().toString();
  }

  /**
   * Implementation for LocalDate.format(String format).
   * Format date with format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param date    the LocalDate object being formatted
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param format  the datetime format
   * @return the formatted date as a String
   */
  public static String localDateFormat(LocalDate date, String source, int offset, String format) {
    try {
      return date.format(dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern));
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  /**
   * Implementation for LocalDate.parseWithFormat(String format).
   * Parse date with given format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param text    the text to be parsed
   * @param format  the datetime format
   * @return the parsed LocalDate
   */
  public static LocalDate localDateParseWithFormat(String source, int offset, String text, String format) {
    try {
      return dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern).parse(text, LocalDate::from);
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  ///////////////////////////////////

  // LocalDateTime

  public static String localDateTimeGetDayOfWeek(LocalDateTime dateTime) {
    return dateTime.getDayOfWeek().toString();
  }

  public static String localDateTimeGetMonth(LocalDateTime dateTime) {
    return dateTime.getMonth().toString();
  }

  public static LocalDateTime localDateTimeOf(String source, int offset, int year, int month, int day, int hour, int minute, int second, int nano) {
    try {
      return LocalDateTime.of(year, month, day, hour, minute, second, nano);
    }
    catch (DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }
  
  public static LocalDateTime localDateTimeOfEpochSecond(String source, int offset, long second, int nano) {
    try {
      return LocalDateTime.ofEpochSecond(second, nano, ZoneOffset.UTC);
    }
    catch (DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  /**
   * Implementation for LocalDateTime.format(String format).
   * Format dateTime with format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param dateTime the LocalDateTime object being formatted
   * @param source   the source code of script calling this function
   * @param offset   the offset in the source code where function is invoked from
   * @param format   the datetime format
   * @return the formatted dateTime as a String
   */
  public static String localDateTimeFormat(LocalDateTime dateTime, String source, int offset, String format) {
    try {
      return dateTime.format(dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern));
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  /**
   * Implementation for LocalDateTime.parseWithFormat(String format).
   * Parse date with given format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param text    the text to be parsed
   * @param format  the datetime format
   * @return the parsed LocalDateTime
   */
  public static LocalDateTime localDateTimeParseWithFormat(String source, int offset, String text, String format) {
    try {
      return dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern).parse(text, LocalDateTime::from);
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  public static LocalDateTime localDateTimeTruncatedToMicros(LocalDateTime time) { return time.truncatedTo(ChronoUnit.MICROS); }
  public static LocalDateTime localDateTimeTruncatedToMillis(LocalDateTime time) { return time.truncatedTo(ChronoUnit.MILLIS); }
  public static LocalDateTime localDateTimeTruncatedToSeconds(LocalDateTime time) { return time.truncatedTo(ChronoUnit.SECONDS); }
  public static LocalDateTime localDateTimeTruncatedToMinutes(LocalDateTime time) { return time.truncatedTo(ChronoUnit.MINUTES); }
  public static LocalDateTime localDateTimeTruncatedToHours(LocalDateTime time) { return time.truncatedTo(ChronoUnit.HOURS); }
  public static LocalDateTime localDateTimeTruncatedToDays(LocalDateTime time) { return time.truncatedTo(ChronoUnit.DAYS); }

  //////////////////////////////////
  
  // Instant
  
  public static Instant instantOfEpochSecond(String source, int offset, long second, int nano) {
    try {
      return Instant.ofEpochSecond(second, nano);
    }
    catch (DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }
  
  public static Instant instantWithNano(Instant instant, String source, int offset, int nano) { return instantWithTemporal(instant, source, offset, ChronoField.NANO_OF_SECOND, nano); }
  public static Instant instantWithMicro(Instant instant, String source, int offset, int micro) { return instantWithTemporal(instant, source, offset, ChronoField.MICRO_OF_SECOND, micro); }
  public static Instant instantWithMilli(Instant instant, String source, int offset, int milli) { return instantWithTemporal(instant, source, offset, ChronoField.MILLI_OF_SECOND, milli); }
  
  private static Instant instantWithTemporal(Instant instant, String source, int offset, TemporalField temporalUnit, long amount) {
    try {
      return instant.with(temporalUnit, amount);
    }
    catch (RuntimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  public static Instant instantTruncatedToMicros(Instant instant) { return instant.truncatedTo(ChronoUnit.MICROS); }
  public static Instant instantTruncatedToMillis(Instant instant) { return instant.truncatedTo(ChronoUnit.MILLIS); }
  public static Instant instantTruncatedToSeconds(Instant instant) { return instant.truncatedTo(ChronoUnit.SECONDS); }
  public static Instant instantTruncatedToMinutes(Instant instant) { return instant.truncatedTo(ChronoUnit.MINUTES); }
  public static Instant instantTruncatedToHours(Instant instant) { return instant.truncatedTo(ChronoUnit.HOURS); }
  public static Instant instantTruncatedToDays(Instant instant) { return instant.truncatedTo(ChronoUnit.DAYS); }

  //////////////////////////////////
  
  // ZonedDateTime

  /**
   * Implementation for ZonedDateTime.format(String format).
   * Format dateTime with format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param dateTime the ZonedDateTime object being formatted
   * @param source   the source code of script calling this function
   * @param offset   the offset in the source code where function is invoked from
   * @param format   the datetime format
   * @return the formatted dateTime as a String
   */
  public static String zonedDateTimeFormat(ZonedDateTime dateTime, String source, int offset, String format) {
    try {
      return dateTime.format(dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern));
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  /**
   * Implementation for ZonedDateTime.parseWithFormat(String format).
   * Parse date with given format based on DateTimeFormatter.
   * We use a per-thread cache of DateTimeFormatters to avoid having to create them every time if there are specific
   * formats used frequently.
   * @param source  the source code of script calling this function
   * @param offset  the offset in the source code where function is invoked from
   * @param text    the text to be parsed
   * @param format  the datetime format
   * @return the parsed ZonedDateTime
   */
  public static ZonedDateTime zonedDateTimeParseWithFormat(String source, int offset, String text, String format) {
    try {
      return dateTimeFormatters.get().computeIfAbsent(format, DateTimeFormatter::ofPattern).parse(text, ZonedDateTime::from);
    }
    catch (IllegalArgumentException|DateTimeException e) {
      throw new RuntimeError(e.getMessage(), source, offset, e);
    }
  }

  public static String zonedDateTimeGetDayOfWeek(ZonedDateTime dateTime) {
    return dateTime.getDayOfWeek().toString();
  }

  public static String zonedDateTimeGetMonth(ZonedDateTime dateTime) {
    return dateTime.getMonth().toString();
  }

  public static ZonedDateTime zonedDateTimeTruncatedToMicros(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.MICROS); }
  public static ZonedDateTime zonedDateTimeTruncatedToMillis(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.MILLIS); }
  public static ZonedDateTime zonedDateTimeTruncatedToSeconds(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.SECONDS); }
  public static ZonedDateTime zonedDateTimeTruncatedToMinutes(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.MINUTES); }
  public static ZonedDateTime zonedDateTimeTruncatedToHours(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.HOURS); }
  public static ZonedDateTime zonedDateTimeTruncatedToDays(ZonedDateTime zonedDateTime) { return zonedDateTime.truncatedTo(ChronoUnit.DAYS); }

  //////////////////////////////////
  
  // ZoneId

  /**
   * Static method for ZoneId to test if a zone id is a valid id
   * @param zoneId the zone id
   * @return true if zone id is in list of available zone ids
   */
  public static boolean zoneIdIsValid(String zoneId) {
    return ZoneId.getAvailableZoneIds().contains(zoneId);
  }
  
  private static final List<String> availableZoneIds = Collections.unmodifiableList(new ArrayList<>(ZoneId.getAvailableZoneIds()));

  /**
   * Static method for ZoneId to get a list of available zone ids
   * @return the list of available zone ids
   */
  public static List<String> zoneIdGetAvailableZoneIds() {
    return availableZoneIds; 
  }

  /**
   * Provide our own ZoneId.of() method that catches the exception that is thrown when
   * the zone does not exist and returns null instead.
   * @param zone the name of the zone
   * @return the ZoneId or null if it does not exist
   */
  public static ZoneId zoneIdOf(String zone) {
    try {
      return ZoneId.of(zone);
    }
    catch (DateTimeException e) {
      return null;
    }
  }
  
  //////////////////////////////////////
  
  // Duration
  
  public static Duration durationBetween(String source, int offset, Temporal start, Temporal end) {
    if (!start.isSupported(ChronoUnit.SECONDS)) {
      throw new RuntimeError("Start of duration cannot be " + RuntimeUtils.className(start) + ": must have resolution of at least seconds", source, offset);
    }
    if (!end.isSupported(ChronoUnit.SECONDS)) {
      throw new RuntimeError("End of duration cannot be " + RuntimeUtils.className(start) + ": must have resolution of at least seconds", source, offset);
    }
    try {
      return Duration.between(start, end);
    }
    catch (DateTimeException|ArithmeticException e) {
      throw new RuntimeError("Error getting duration: " + e.getMessage(), source, offset, e);
    }
  }
  
  public static void main(String[] args) {
    BuiltinFunctions.registerBuiltinFunctions();
    Stream.of("jactl.time.LocalTime",
              "jactl.time.LocalDate",
              "jactl.time.LocalDateTime",
              "jactl.time.ZonedDateTime",
              "jactl.time.Instant",
              "jactl.time.Period",
              "jactl.time.Duration",
              "jactl.time.ZoneId")
          .map(RegisteredClasses.INSTANCE::getClassDescriptor)
          .forEach(desc -> System.out.println(formatClassMethods(desc)));

    Stream.of(JactlType.STRING, JactlType.LIST, JactlType.MAP, JactlType.NUMBER)
          .forEach(type -> {
            System.out.println("## " + type + " Methods");
            System.out.println("```groovy");
            Functions.INSTANCE.getAllMethods(type)
                              .stream()
                              .sorted(Comparator.comparing(pair -> pair.second.name))
                              .flatMap(pair -> pair.second.getAliases().stream().map(alias -> Pair.create(alias, pair.second)))
                              .forEach(pair -> System.out.println(formatFn(pair.first, pair.second)));
            System.out.println("```\n");
          });
  }

  private static String formatClassMethods(ClassDescriptor desc) {
    StringBuilder sb = new StringBuilder();
    sb.append("## ")
      .append(desc.className)
      .append(" Methods")
      .append("\n```groovy\n");
    desc.getAllMethods()
        .sorted(Comparator.comparing(a -> a.getValue().name))
        .forEach(entry -> sb.append(formatFn(entry.getKey(), entry.getValue())).append('\n'));
    sb.append("```\n");
    return sb.toString();
  }
  
  private static String formatFn(String name, FunctionDescriptor fn) {
    JactlFunction jactlFn = fn instanceof JactlFunction ? (JactlFunction)fn : null;
    StringBuilder sb = new StringBuilder();
    sb.append(fn.isStaticMethod ? "static " : "")
      .append(typeName(fn.returnType))
      .append(" ")
      .append(name)
      .append("(");
    for (int i = 0; i < fn.paramCount; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(typeName(fn.paramTypes.get(i)))
        .append(" ")
        .append(fn.paramNames.get(i));
      if (jactlFn != null) {
        Object defVal = jactlFn.defaultVals[i];
        if (defVal != MANDATORY) {
          sb.append("=");
          if (defVal instanceof String) {
            sb.append("'").append(defVal).append("'");
          }
          else {
            sb.append(defVal);
          }
        }
      }
    }
    sb.append(")");
    return sb.toString();
  }
  
  private static String typeName(JactlType type) {
    if(type.is(JactlType.INSTANCE, JactlType.CLASS)) {
      return type.getClassDescriptor().getPackagedName().replaceAll("jactl.time.","");
    }
    return type.toString();
  }
}
