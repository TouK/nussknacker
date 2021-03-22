package pl.touk.nussknacker.engine.avro.schemaregistry

sealed trait SchemaRegistryError extends RuntimeException
final case class SchemaSubjectNotFound(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaVersionNotFound(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaRegistryUnknownError(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaRegistryUnsupportedTypeError(message: String) extends RuntimeException(message) with SchemaRegistryError
