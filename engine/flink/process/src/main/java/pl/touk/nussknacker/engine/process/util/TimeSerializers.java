package pl.touk.nussknacker.engine.process.util;

/* Copyright (c) 2016, Martin Grotzke
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.apache.flink.api.common.ExecutionConfig;

/**
 * Copied from com.esotericsoftware.kryo.serializers.TimeSerializers - newer Kryo version
 * <p>
 * Serializers for java.time.*, are added as default serializers if java version is >= 8.
 * <p>
 * Serializers are all private for now because they're not expected to be somehow used/extended/accessed by the user. If there
 * should be a case where this is needed it can be changed - for now the public api should be kept as spall as possible.
 * <p>
 * Implementation note: All serialization is inspired by oracles java.time.Ser.
 */

public class TimeSerializers {

    public static void addDefaultSerializers(ExecutionConfig config) {
        addSerializer(config, Duration.class, new DurationSerializer());
        addSerializer(config, Instant.class, new InstantSerializer());
        addSerializer(config, LocalDate.class, new LocalDateSerializer());
        addSerializer(config, LocalTime.class, new LocalTimeSerializer());
        addSerializer(config, LocalDateTime.class, new LocalDateTimeSerializer());
        addSerializer(config, ZoneOffset.class, new ZoneOffsetSerializer());
        addSerializer(config, ZoneId.class, new ZoneIdSerializer());
        addSerializer(config, OffsetTime.class, new OffsetTimeSerializer());
        addSerializer(config, OffsetDateTime.class, new OffsetDateTimeSerializer());
        addSerializer(config, ZonedDateTime.class, new ZonedDateTimeSerializer());
        addSerializer(config, Year.class, new YearSerializer());
        addSerializer(config, YearMonth.class, new YearMonthSerializer());
        addSerializer(config, MonthDay.class, new MonthDaySerializer());
        addSerializer(config, Period.class, new PeriodSerializer());
    }

    private static <T, Y extends Serializer<T> & Serializable> void addSerializer(ExecutionConfig config, Class<T> klass, Y serializer) {
      config.getRegisteredTypesWithKryoSerializers().put(klass, new ExecutionConfig.SerializableSerializer<>(serializer));
      config.getDefaultKryoSerializers().put(klass, new ExecutionConfig.SerializableSerializer<>(serializer));
    }

    private static class DurationSerializer extends Serializer<Duration> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, Duration duration) {
            out.writeLong(duration.getSeconds());
            out.writeInt(duration.getNano(), true);
        }

        public Duration read(Kryo kryo, Input in, Class<Duration> type) {
            long seconds = in.readLong();
            int nanos = in.readInt(true);
            return Duration.ofSeconds(seconds, nanos);
        }
    }

    private static class InstantSerializer extends Serializer<Instant> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, Instant instant) {
            out.writeLong(instant.getEpochSecond(), true);
            out.writeInt(instant.getNano(), true);
        }

        public Instant read(Kryo kryo, Input in, Class<Instant> type) {
            long seconds = in.readLong(true);
            int nanos = in.readInt(true);
            return Instant.ofEpochSecond(seconds, nanos);
        }
    }

    private static class LocalDateSerializer extends Serializer<LocalDate> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, LocalDate date) {
            write(out, date);
        }

        static void write(Output out, LocalDate date) {
            out.writeInt(date.getYear(), true);
            out.writeByte(date.getMonthValue());
            out.writeByte(date.getDayOfMonth());
        }

        public LocalDate read(Kryo kryo, Input in, Class<LocalDate> type) {
            return read(in);
        }

        static LocalDate read(Input in) {
            int year = in.readInt(true);
            int month = in.readByte();
            int dayOfMonth = in.readByte();
            return LocalDate.of(year, month, dayOfMonth);
        }
    }

    private static class LocalDateTimeSerializer extends Serializer<LocalDateTime> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, LocalDateTime dateTime) {
            LocalDateSerializer.write(out, dateTime.toLocalDate());
            LocalTimeSerializer.write(out, dateTime.toLocalTime());
        }

        public LocalDateTime read(Kryo kryo, Input in, Class<LocalDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            return LocalDateTime.of(date, time);
        }
    }

    private static class LocalTimeSerializer extends Serializer<LocalTime> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, LocalTime time) {
            write(out, time);
        }

        static void write(Output out, LocalTime time) {
            if (time.getNano() == 0) {
                if (time.getSecond() == 0) {
                    if (time.getMinute() == 0) {
                        out.writeByte(~time.getHour());
                    } else {
                        out.writeByte(time.getHour());
                        out.writeByte(~time.getMinute());
                    }
                } else {
                    out.writeByte(time.getHour());
                    out.writeByte(time.getMinute());
                    out.writeByte(~time.getSecond());
                }
            } else {
                out.writeByte(time.getHour());
                out.writeByte(time.getMinute());
                out.writeByte(time.getSecond());
                out.writeInt(time.getNano(), true);
            }
        }

        public LocalTime read(Kryo kryo, Input in, Class<LocalTime> type) {
            return read(in);
        }

        static LocalTime read(Input in) {
            int hour = in.readByte();
            int minute = 0;
            int second = 0;
            int nano = 0;
            if (hour < 0) {
                hour = ~hour;
            } else {
                minute = in.readByte();
                if (minute < 0) {
                    minute = ~minute;
                } else {
                    second = in.readByte();
                    if (second < 0) {
                        second = ~second;
                    } else {
                        nano = in.readInt(true);
                    }
                }
            }
            return LocalTime.of(hour, minute, second, nano);
        }
    }

    private static class ZoneOffsetSerializer extends Serializer<ZoneOffset> implements Serializable {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, ZoneOffset obj) {
            write(out, obj);
        }

        static void write(Output out, ZoneOffset obj) {
            final int offsetSecs = obj.getTotalSeconds();
            int offsetByte = offsetSecs % 900 == 0 ? offsetSecs / 900 : 127; // compress to -72 to +72
            out.writeByte(offsetByte);
            if (offsetByte == 127) {
                out.writeInt(offsetSecs);
            }
        }

        public ZoneOffset read(Kryo kryo, Input in, Class<ZoneOffset> type) {
            return read(in);
        }

        static ZoneOffset read(Input in) {
            int offsetByte = in.readByte();
            return (offsetByte == 127 ? ZoneOffset.ofTotalSeconds(in.readInt()) : ZoneOffset.ofTotalSeconds(offsetByte * 900));
        }
    }

    private static class ZoneIdSerializer extends Serializer<ZoneId> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, ZoneId obj) {
            write(out, obj);
        }

        static void write(Output out, ZoneId obj) {
            out.writeString(obj.getId());
        }

        public ZoneId read(Kryo kryo, Input in, Class<ZoneId> type) {
            return read(in);
        }

        static ZoneId read(Input in) {
            String id = in.readString();
            return ZoneId.of(id);
        }
    }

    private static class OffsetTimeSerializer extends Serializer<OffsetTime> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, OffsetTime obj) {
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneOffsetSerializer.write(out, obj.getOffset());
        }

        public OffsetTime read(Kryo kryo, Input in, Class<OffsetTime> type) {
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneOffset offset = ZoneOffsetSerializer.read(in);
            return OffsetTime.of(time, offset);
        }
    }

    private static class OffsetDateTimeSerializer extends Serializer<OffsetDateTime> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, OffsetDateTime obj) {
            LocalDateSerializer.write(out, obj.toLocalDate());
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneOffsetSerializer.write(out, obj.getOffset());
        }

        public OffsetDateTime read(Kryo kryo, Input in, Class<OffsetDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneOffset offset = ZoneOffsetSerializer.read(in);
            return OffsetDateTime.of(date, time, offset);
        }
    }

    private static class ZonedDateTimeSerializer extends Serializer<ZonedDateTime> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, ZonedDateTime obj) {
            LocalDateSerializer.write(out, obj.toLocalDate());
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneIdSerializer.write(out, obj.getZone());
        }

        public ZonedDateTime read(Kryo kryo, Input in, Class<ZonedDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneId zone = ZoneIdSerializer.read(in);
            return ZonedDateTime.of(date, time, zone);
        }
    }

    private static class YearSerializer extends Serializer<Year> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, Year obj) {
            out.writeInt(obj.getValue(), true);
        }

        public Year read(Kryo kryo, Input in, Class<Year> type) {
            return Year.of(in.readInt(true));
        }
    }

    private static class YearMonthSerializer extends Serializer<YearMonth> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, YearMonth obj) {
            out.writeInt(obj.getYear(), true);
            out.writeByte(obj.getMonthValue());
        }

        public YearMonth read(Kryo kryo, Input in, Class<YearMonth> type) {
            int year = in.readInt(true);
            byte month = in.readByte();
            return YearMonth.of(year, month);
        }
    }

    private static class MonthDaySerializer extends Serializer<MonthDay> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, MonthDay obj) {
            out.writeByte(obj.getMonthValue());
            out.writeByte(obj.getDayOfMonth());
        }

        public MonthDay read(Kryo kryo, Input in, Class<MonthDay> type) {
            byte month = in.readByte();
            byte day = in.readByte();
            return MonthDay.of(month, day);
        }
    }

    private static class PeriodSerializer extends Serializer<Period> implements Serializable  {
        {
            setImmutable(true);
        }

        public void write(Kryo kryo, Output out, Period obj) {
            out.writeInt(obj.getYears(), true);
            out.writeInt(obj.getMonths(), true);
            out.writeInt(obj.getDays(), true);
        }

        public Period read(Kryo kryo, Input in, Class<Period> type) {
            int years = in.readInt(true);
            int months = in.readInt(true);
            int days = in.readInt(true);
            return Period.of(years, months, days);
        }
    }

}
