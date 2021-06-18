package pl.touk.nussknacker.engine.api.exception

import java.time.Instant

case class NonTransientException(input: String, message: String,
                                 timestamp: Instant = Instant.now(), cause: Throwable = null)
  extends RuntimeException(message, cause)