package pl.touk.nussknacker.engine.util

import java.time.{Instant, OffsetDateTime, ZonedDateTime}

object TimestampUtils {

  val supportedTypeToMillis: PartialFunction[Any, Long] = {
    case v: Long           => v
    case v: Int            => v
    case v: Instant        => v.toEpochMilli
    case v: ZonedDateTime  => v.toInstant.toEpochMilli
    case v: OffsetDateTime => v.toInstant.toEpochMilli
  }

}
