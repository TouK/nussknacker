package pl.touk.nussknacker.restmodel.util

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

object DateUtils {

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def toTimestamp(localDateTime: LocalDateTime) = {
    Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant)
  }

  def toLocalDateTime(t: Timestamp) = {
    LocalDateTime.ofInstant(t.toInstant, ZoneId.systemDefault())
  }

  def toMillis(ldt: LocalDateTime) = {
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

  def parseDateTime(date: String) = {
    LocalDateTime.parse(date, dateTimeFormatter)
  }

}
