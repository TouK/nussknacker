package pl.touk.esp.ui.util

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}

object DateUtils {

  def now = {
    Timestamp.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant)
  }

  def toLocalDateTime(t: Timestamp) = {
    LocalDateTime.ofInstant(t.toInstant, ZoneId.systemDefault())
  }
}
