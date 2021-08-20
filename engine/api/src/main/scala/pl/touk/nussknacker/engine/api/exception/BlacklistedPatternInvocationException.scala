package pl.touk.nussknacker.engine.api.exception

import java.time.Instant

case class BlacklistedPatternInvocationException(message: String,
                                                 timestamp: Instant = Instant.now())
  extends RuntimeException(message)