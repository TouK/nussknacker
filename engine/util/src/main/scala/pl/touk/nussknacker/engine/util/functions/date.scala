package pl.touk.nussknacker.engine.util.functions

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

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

  private def localDateTimeToEpochMillis(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

}
