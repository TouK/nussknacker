package pl.touk.nussknacker.engine.util

import java.time.{Instant, OffsetDateTime, ZonedDateTime}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object TimestampUtils {

  val supportedTimestampTypes: List[TypingResult] = List(Typed[java.lang.Long], Typed[Long], Typed[Int], Typed[Instant], Typed[OffsetDateTime], Typed[ZonedDateTime])

  def supportedTypeToMillis(value: Any, errorMsg: String): Long = value match {
    case v: Long => v
    case v: Int => v * 1000
    case v: Instant => v.toEpochMilli
    case v: ZonedDateTime => v.toInstant.toEpochMilli
    case v: OffsetDateTime => v.toInstant.toEpochMilli
    case _ => throw new IllegalArgumentException(errorMsg)
  }
}
