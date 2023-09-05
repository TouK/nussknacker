package pl.touk.nussknacker.engine.util

import java.time.{Instant, OffsetDateTime, ZonedDateTime}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object TimestampUtils {

  val supportedTimestampTypes: List[TypingResult] = List(Typed[java.lang.Long], Typed[Long], Typed[Int], Typed[Instant], Typed[OffsetDateTime], Typed[ZonedDateTime])

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
    case v: ZonedDateTime => v.toInstant.toEpochMilli
    case v: OffsetDateTime => v.toInstant.toEpochMilli
    case _ => throw new IllegalArgumentException(errorMsg)
  }
}
