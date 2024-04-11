package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaTopicError, SchemaVersionError}

object SchemaDeterminerErrorHandler {

  def handleSchemaRegistryError(exc: SchemaDeterminerError)(implicit nodeId: NodeId): CustomNodeError = {
    val parameter = exc.getCause match {
      case _: SchemaTopicError   => Some(topicParamName)
      case _: SchemaVersionError => Some(schemaVersionParamName)
      case _                     => None
    }
    CustomNodeError(exc.getMessage, parameter)
  }

}
