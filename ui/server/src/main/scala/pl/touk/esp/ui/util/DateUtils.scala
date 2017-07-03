package pl.touk.esp.ui.util

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

object DateUtils {

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def now = {
    Timestamp.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant)
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
