package pl.touk.nussknacker.engine.util

import java.sql.Timestamp
import java.time.{Instant, LocalDate, OffsetDateTime, ZonedDateTime}
import scala.util.Try

object TimestampUtils {
  def supportedTypeToMillis(value: Any, errorMsg: String): Long = value match {
    case v: Long => v
    case v: Int => v * 1000
    case v: Instant => v.toEpochMilli
    case v: LocalDate => Timestamp.valueOf(v.atStartOfDay()).getTime
    case v: ZonedDateTime => Timestamp.valueOf(v.toLocalDateTime).getTime
    case v: OffsetDateTime => Timestamp.valueOf(v.toLocalDateTime).getTime
    case v => Try(v.asInstanceOf[Long]).getOrElse {
      Try(v.asInstanceOf[Int].toLong * 1000).getOrElse {
        throw new IllegalArgumentException(errorMsg)
      }
    }
  }
}
