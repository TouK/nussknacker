package pl.touk.nussknacker.engine.avro.schemaregistry

sealed trait SchemaRegistryError extends RuntimeException
final case class InvalidSchema(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class InvalidSchemaVersion(message: String) extends RuntimeException(message) with SchemaRegistryError
final case class SchemaRegistryUnknownError(message: String) extends RuntimeException(message) with SchemaRegistryError
