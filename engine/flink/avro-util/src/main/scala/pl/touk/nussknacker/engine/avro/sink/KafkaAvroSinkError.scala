package pl.touk.nussknacker.engine.avro.sink

final case class InvalidSinkOutput(message: String, cause: Throwable) extends RuntimeException(message, cause)
