package pl.touk.nussknacker.engine.avro.sink

final case class InvalidSinkOutput(message: String) extends RuntimeException(message)
