package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaParseError, SchemaRegistryError, SchemaSubjectNotFound, SchemaVersionNotFound}

object KafkaAvroFactory {

  final val SchemaVersionParamName = "Schema version"
  final val FixedSchemaParamName = "Schema"
  final val SinkOutputParamName = "Output"
  final val TopicParamName = "topic"

  def handleSchemaRegistryError(exc: SchemaRegistryError): Nothing = {
    val parameter = exc match {
      case _: SchemaSubjectNotFound => Some(`TopicParamName`)
      case _: SchemaVersionNotFound => Some(`SchemaVersionParamName`)
      case _: SchemaParseError => Some(`FixedSchemaParamName`)
      case _ => None
    }

    throw CustomNodeValidationException(exc, parameter)
  }
}
