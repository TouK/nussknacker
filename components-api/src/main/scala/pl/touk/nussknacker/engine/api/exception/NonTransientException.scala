package pl.touk.nussknacker.engine.api.exception

import java.time.Instant

class NonTransientException(
    // TODO: rename/describe what is the purpose of input
    val input: String,
    message: String,
    val timestamp: Instant = Instant.now(),
    cause: Throwable = null,
) extends RuntimeException(message, cause)
