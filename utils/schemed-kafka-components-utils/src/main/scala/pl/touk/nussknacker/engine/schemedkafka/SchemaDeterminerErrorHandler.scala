package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}
import pl.touk.nussknacker.engine.api.NodeId

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
