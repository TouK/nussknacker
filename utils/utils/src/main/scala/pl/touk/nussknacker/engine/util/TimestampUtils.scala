package pl.touk.nussknacker.engine.util

import java.sql.Timestamp
import java.time.{Instant, LocalDate, OffsetDateTime, ZonedDateTime}
import scala.util.Try

object TimestampUtils {
  // some timestamps (direct input from user or in data sources) can be in seconds
  def normalizeTimestampToMillis(l: Long): Long =
    if (l > 31536000000L) { // 1971.01.01 00:00:00 timestamp in milliseconds
      l
    } else { // l is nearly certainly given in seconds
      l * 1000
    }

  def supportedTypeToMillis(value: Any, errorMsg: String): Long = value match {
    case v: Long => normalizeTimestampToMillis(v)
    case v: Int => normalizeTimestampToMillis(v)
    case v: Instant => v.toEpochMilli
    case v: LocalDate => Timestamp.valueOf(v.atStartOfDay()).getTime
    case v: ZonedDateTime => Timestamp.valueOf(v.toLocalDateTime).getTime
    case v: OffsetDateTime => Timestamp.valueOf(v.toLocalDateTime).getTime
    case v => normalizeTimestampToMillis(Try(v.asInstanceOf[Long]).getOrElse {
      Try(v.asInstanceOf[Int].toLong).getOrElse {
        throw new IllegalArgumentException(errorMsg)
      }
    })
  }
}
