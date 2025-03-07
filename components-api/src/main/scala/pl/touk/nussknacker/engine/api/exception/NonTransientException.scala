package pl.touk.nussknacker.engine.api.exception

import java.time.Instant

abstract class NonTransientException(
    val input: String,
    message: String,
    val timestamp: Instant = Instant.now(),
    cause: Throwable = null,
) extends RuntimeException(message, cause)
