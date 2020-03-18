package pl.touk.nussknacker.engine.management.sample.global

import java.time.LocalDateTime
import java.time.Duration
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

object DateHelper extends HideToString {
  @Documentation(description = "Returns current time in milliseconds")
  def nowTimestamp(): Long = System.currentTimeMillis()

  @Documentation(description = "Parses string date param to LocalDateTime")
  def parseDate(@ParamName("dateString") dateString: String): LocalDateTime =
    LocalDateTime.parse(dateString)

  @Documentation(description = "Returns duration between from and to dates")
  def duration(from: LocalDateTime, to: LocalDateTime): Long = {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("Date to must be after date from")
    }

    val duration = Duration.between(from, to)
    Math.abs(duration.toMillis())
  }
}
