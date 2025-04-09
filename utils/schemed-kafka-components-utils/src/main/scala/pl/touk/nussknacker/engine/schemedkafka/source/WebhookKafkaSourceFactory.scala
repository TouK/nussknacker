package pl.touk.nussknacker.engine.schemedkafka.source

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}

class WebhookKafkaSourceFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    modelDependencies: ProcessObjectDependencies,
    implProvider: KafkaSourceImplFactory[Any, Any]
) extends UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      schemaBasedMessagesSerdeProvider,
      modelDependencies,
      implProvider
    ) {

  override protected lazy val topicParamName: ParameterName = KafkaUniversalComponentTransformer.endpointParamName
}
