package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

object date {

  private val format = DateTimeFormatter.ISO_DATE_TIME

  @Documentation(description = "Current time")
  def now(): LocalDateTime = LocalDateTime.now()

  @Documentation(description = "Current timestamp")
  def nowTimestamp(): Long = System.currentTimeMillis()

  @Documentation(description = "Parse date in ISO format (e.g. '2018-11-12T11:22:33') to timestamp")
  def parseToTimestamp(@ParamName("dateString") dateString: String): Long =
    localDateTimeToEpochMillis(parseToLocalDate(dateString))

  @Documentation(description = "Parse date in ISO format (e.g. '2018-11-12T11:22:33') to date object")
  def parseToLocalDate(@ParamName("dateString") dateString: String): LocalDateTime = LocalDateTime.parse(dateString, format)

  @Documentation(description = "Parse date in ISO format (e.g. '2018-11-12T11:22:33+02[Europe/Warsaw]') to date object")
  def parseToZonedDate(@ParamName("dateString") dateString: String): ZonedDateTime = ZonedDateTime.parse(dateString, format)

  @Documentation(description = "Convert to Instant object")
  def toInstant(@ParamName("timestamp") timestamp: Long): Instant = Instant.ofEpochMilli(timestamp)

  @Documentation(description = "Parse time zone identifier (e.g. UTC, GMT, Europe/Warsaw")
  def zone(@ParamName("zoneId") zoneId: String): ZoneId = ZoneId.of(zoneId)

  private def localDateTimeToEpochMillis(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

}
