package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaRegistryError,
  SchemaTopicError,
  SchemaVersionError
}

object SchemaRegistryErrorHandler {

  def handleSchemaRegistryError(exc: SchemaRegistryError)(implicit nodeId: NodeId): CustomNodeError = {
    val parameter = exc.getCause match {
      case _: SchemaTopicError   => Some(topicParamName)
      case _: SchemaVersionError => Some(schemaVersionParamName)
      case _                     => None
    }
    CustomNodeError(exc.getMessage, parameter)
  }

}
