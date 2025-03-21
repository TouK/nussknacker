package pl.touk.nussknacker.engine.management.sample.helper

import pl.touk.nussknacker.engine.api.HideToString

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

object DummyDateFormatHelper extends HideToString {

  def format(temporal: TemporalAccessor): String = {
    val formatter = temporal match {
      case _: Instant        => DateTimeFormatter.ISO_INSTANT
      case _: LocalDate      => DateTimeFormatter.ISO_LOCAL_DATE
      case _: LocalTime      => DateTimeFormatter.ISO_LOCAL_TIME
      case _: LocalDateTime  => DateTimeFormatter.ISO_LOCAL_DATE_TIME
      case _: OffsetDateTime => DateTimeFormatter.ISO_OFFSET_DATE_TIME
      case _: ZonedDateTime  => DateTimeFormatter.ISO_ZONED_DATE_TIME
    }
    formatter.format(temporal)
  }

}
