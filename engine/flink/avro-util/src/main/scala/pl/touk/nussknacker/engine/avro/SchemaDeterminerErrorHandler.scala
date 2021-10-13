package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}

object SchemaDeterminerErrorHandler {

  def handleSchemaRegistryErrorAndThrowException(exc: SchemaDeterminerError)(implicit nodeId: NodeId): Nothing = {
    val error = handleSchemaRegistryError(exc)
    throw CustomNodeValidationException(exc, error.paramName)
  }

  def handleSchemaRegistryError(exc: SchemaDeterminerError)(implicit nodeId: NodeId): CustomNodeError = {
    val parameter = exc.getCause match {
      case _: SchemaSubjectNotFound => Some(TopicParamName)
      case _: SchemaVersionNotFound => Some(SchemaVersionParamName)
      case _ => None
    }
    CustomNodeError(exc.getMessage, parameter)
  }

}
