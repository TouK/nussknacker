package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.time.{Instant, OffsetDateTime, ZonedDateTime}

object TimestampUtils {

  val supportedTimestampTypes: Set[TypingResult] = Set(
    Typed[java.lang.Long],
    Typed[Long],
    Typed[Int],
    Typed[Instant],
    Typed[ZonedDateTime],
    Typed[OffsetDateTime],
  )

  def supportedTypeToMillis(value: Any, fieldName: String): Long = value match {
    case v: Long           => v
    case v: Int            => v
    case v: Instant        => v.toEpochMilli
    case v: ZonedDateTime  => v.toInstant.toEpochMilli
    case v: OffsetDateTime => v.toInstant.toEpochMilli
    case other =>
      throw new IllegalArgumentException(
        s"Field $fieldName is of an invalid type for a timestamp field: ${if (other == null) "null" else other.getClass}"
      )
  }

}
