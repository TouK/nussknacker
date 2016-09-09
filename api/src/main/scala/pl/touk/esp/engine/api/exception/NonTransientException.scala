package pl.touk.esp.engine.api.exception

import java.time.LocalDateTime

case class NonTransientException(input: String, message: String,
                                 timestamp: LocalDateTime = LocalDateTime.now(), cause: Throwable = null)
  extends RuntimeException(message, cause)