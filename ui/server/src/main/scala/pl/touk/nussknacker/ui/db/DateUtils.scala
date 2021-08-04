package pl.touk.nussknacker.ui.db

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}

object DateUtils {

  //TODO: refactor to use Instant/ZonedDateTime
  def toLocalDateTime(t: Timestamp): LocalDateTime = {
    LocalDateTime.ofInstant(t.toInstant, ZoneId.systemDefault())
  }

}
