package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaSubjectNotFound, SchemaVersionFound}

object KafkaAvroFactory {

  final val VersionParamName = "Schema version"
  final val TopicParamName = "topic"

  def handleSchemaRegistryError(exc: SchemaRegistryError): Nothing = {
    val parameter = exc match {
      case _: SchemaSubjectNotFound => Some(`TopicParamName`)
      case _: SchemaVersionFound => Some(`VersionParamName`)
      case _ => None
    }

    throw CustomNodeValidationException(exc, parameter)
  }
}
