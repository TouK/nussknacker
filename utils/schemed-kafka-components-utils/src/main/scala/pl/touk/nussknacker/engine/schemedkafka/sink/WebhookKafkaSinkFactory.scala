package pl.touk.nussknacker.engine.schemedkafka.sink

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}

class WebhookKafkaSinkFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    modelDependencies: ProcessObjectDependencies,
    implProvider: UniversalKafkaSinkImplFactory
) extends UniversalKafkaSinkFactory(
      schemaRegistryClientFactory,
      schemaBasedMessagesSerdeProvider,
      modelDependencies,
      implProvider
    ) {

  override protected lazy val topicParamName: ParameterName = KafkaUniversalComponentTransformer.endpointParamName
}
