package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

sealed trait SchemaRegistryError extends RuntimeException
final case class SchemaTopicError(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaVersionError(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaError(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaRegistryUnknownError(message: String, cause: Throwable) extends RuntimeException(message, cause) with SchemaRegistryError