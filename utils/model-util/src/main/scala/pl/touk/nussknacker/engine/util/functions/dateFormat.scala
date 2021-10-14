package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.Documentation

import java.time._
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.TemporalAccessor
import java.util.Locale

object dateFormat extends DateFormatUtils(Locale.getDefault)

class DateFormatUtils(defaultLocale: Locale) {

  @Documentation(description = "Parse LocalTime in ISO-8601 format e.g. '10:15' or '10:15:30'")
  def parseLocalTime(text: String): LocalTime = LocalTime.parse(text)
  @Documentation(description = "Parse LocalDate in ISO-8601 format e.g. '2011-12-03'")
  def parseLocalDate(text: String): LocalDate = LocalDate.parse(text)
  @Documentation(description = "Parse LocalDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30'")
  def parseLocalDateTime(text: String): LocalDateTime = LocalDateTime.parse(text)
  @Documentation(description = "Parse Instant in ISO-8601 format e.g. '2011-12-03T10:15:30Z'")
  def parseInstant(text: String): Instant = Instant.parse(text)
  @Documentation(description = "Parse OffsetDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00'")
  def parseOffsetDateTime(text: String): OffsetDateTime = OffsetDateTime.parse(text)
  @Documentation(description = "Parse ZonedDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00[Europe/Paris]'")
  def parseZonedDateTime(text: String): ZonedDateTime = ZonedDateTime.parse(text)

  @Documentation(description = "Parse LocalTime in DateTimeFormatter format")
  def parseLocalTime(text: String, format: String): LocalTime = LocalTime.parse(text, DateTimeFormatter.ofPattern(format, defaultLocale))
  @Documentation(description = "Parse LocalDate in DateTimeFormatter format")
  def parseLocalDate(text: String, format: String): LocalDate = LocalDate.parse(text, DateTimeFormatter.ofPattern(format, defaultLocale))
  @Documentation(description = "Parse LocalDateTime in DateTimeFormatter format")
  def parseLocalDateTime(text: String, format: String): LocalDateTime = LocalDateTime.parse(text, DateTimeFormatter.ofPattern(format, defaultLocale))
  @Documentation(description = "Parse OffsetDateTime in DateTimeFormatter format")
  def parseOffsetDateTime(text: String, format: String): OffsetDateTime = OffsetDateTime.parse(text, DateTimeFormatter.ofPattern(format, defaultLocale))
  @Documentation(description = "Parse ZonedDateTime in DateTimeFormatter format")
  def parseZonedDateTime(text: String, format: String): ZonedDateTime = ZonedDateTime.parse(text, DateTimeFormatter.ofPattern(format, defaultLocale))

  @Documentation(description = "Parse LocalTime using given formatter")
  def parseLocalTime(text: String, format: DateTimeFormatter): LocalTime = LocalTime.parse(text, format)
  @Documentation(description = "Parse LocalDate using given formatter")
  def parseLocalDate(text: String, format: DateTimeFormatter): LocalDate = LocalDate.parse(text, format)
  @Documentation(description = "Parse LocalDateTime using given formatter")
  def parseLocalDateTime(text: String, format: DateTimeFormatter): LocalDateTime = LocalDateTime.parse(text, format)
  @Documentation(description = "Parse OffsetDateTime using given formatter")
  def parseOffsetDateTime(text: String, format: DateTimeFormatter): OffsetDateTime = OffsetDateTime.parse(text, format)
  @Documentation(description = "Parse ZonedDateTime using given formatter")
  def parseZonedDateTime(text: String, format: DateTimeFormatter): ZonedDateTime = ZonedDateTime.parse(text, format)

  @Documentation(description = "Render LocalTime, LocalDate, LocalDateTime, OffsetDateTime or ZonedDateTime in ISO-8601 format")
  def format(temporal: TemporalAccessor): String = {
    val formatter = temporal match {
      case _: Instant => DateTimeFormatter.ISO_INSTANT
      case _: LocalDate => DateTimeFormatter.ISO_LOCAL_DATE
      case _: LocalTime => DateTimeFormatter.ISO_LOCAL_TIME
      case _: LocalDateTime => DateTimeFormatter.ISO_LOCAL_DATE_TIME
      case _: OffsetDateTime => DateTimeFormatter.ISO_OFFSET_DATE_TIME
      case _: ZonedDateTime => DateTimeFormatter.ISO_ZONED_DATE_TIME
    }
    formatter.format(temporal)
  }

  @Documentation(description = "Creates DateTimeFormatter using given pattern")
  def formatter(pattern: String): DateTimeFormatter = formatter(pattern, defaultLocale)

  @Documentation(description = "Creates DateTimeFormatter using given pattern and locale")
  def formatter(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)

  @Documentation(description = "Creates lenient version of DateTimeFormatter using given pattern")
  def lenientFormatter(pattern: String): DateTimeFormatter =
    lenientFormatter(pattern, defaultLocale)

  @Documentation(description = "Creates lenient version of DateTimeFormatter using given pattern and locale")
  def lenientFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .parseLenient()
      .parseCaseInsensitive()
      .appendPattern(pattern)
      .toFormatter(locale)

}
