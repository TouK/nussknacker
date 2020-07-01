package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaSubjectNotFound, SchemaVersionNotFound}
import pl.touk.nussknacker.engine.avro.sink.InvalidSinkOutput

object KafkaAvroFactory {

  final val SchemaVersionParamName = "Schema version"
  final val SinkOutputParamName = "Output"
  final val TopicParamName = "topic"

  def handleSchemaRegistryError(exc: SchemaRegistryError): Nothing = {
    val parameter = exc match {
      case _: SchemaSubjectNotFound => Some(`TopicParamName`)
      case _: SchemaVersionNotFound => Some(`SchemaVersionParamName`)
      case _ => None
    }

    throw CustomNodeValidationException(exc, parameter)
  }

  def handleError(exc: RuntimeException): Nothing = {
    val parameter = exc match {
      case e: SchemaRegistryError => handleSchemaRegistryError(e)
      case _: InvalidSinkOutput => Some(`SinkOutputParamName`)
      case _ => None
    }

    throw CustomNodeValidationException(exc, parameter)
  }
}
