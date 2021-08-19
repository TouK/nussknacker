package pl.touk.nussknacker.engine.api.exception

import java.time.Instant

case class BlacklistedPatternInvocationException(message: String,
                                                 timestamp: Instant = Instant.now(), cause: Throwable = null)
  extends RuntimeException(message, cause)