package pl.touk.nussknacker.engine.avro.schemaregistry

sealed trait SchemaRegistryError extends RuntimeException
final case class SchemaSubjectNotFound(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaVersionFound(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaRegistryUnknownError(message: String) extends RuntimeException(message) with SchemaRegistryError
