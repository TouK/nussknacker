package pl.touk.esp.engine.api

import java.time.LocalDateTime

case class NonTransientException(input: String, message: String, timestamp: LocalDateTime = LocalDateTime.now()) extends RuntimeException(message)