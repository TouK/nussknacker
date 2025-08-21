package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.{SinkForType, TestResultsHolder}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

// FIXME abr: use FlinkKafkaComponentProvider instead
class KafkaAvroTestComponentProvider(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    sinkForInputMetaResultsHolder: => TestResultsHolder[java.util.Map[String @unchecked, _]]
) {

  def createComponents(modelConfig: ModelConfig): List[ComponentDefinition] = {
    val kafkaConfig = KafkaConfig.parseConfig(modelConfig.underlyingConfig)
    val universalSourceFactory = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfig),
      kafkaConfig,
      modelConfig.namingStrategy,
      new FlinkKafkaSourceImplFactory
    )

    val kafkaConfigWithKeySchemaSupport = kafkaConfig.copy(useStringForKey = false)
    val avroGenericSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfigWithKeySchemaSupport),
      kafkaConfigWithKeySchemaSupport,
      modelConfig.namingStrategy,
      new FlinkKafkaSourceImplFactory
    )
    val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfig)

    List(
      ComponentDefinition("kafka", universalSourceFactory),
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalPayload,
          kafkaConfig,
          modelConfig.namingStrategy,
          modelConfig.jsonLikeValuesEnteringMode,
          FlinkKafkaUniversalSinkImplFactory
        )
      ),
      // non-productional
      ComponentDefinition("kafka-key-value", avroGenericSourceFactoryWithKeySchemaSupport),
      ComponentDefinition(
        "sinkForInputMeta",
        SinkForType[java.util.Map[String @unchecked, _]](sinkForInputMetaResultsHolder)
      ),
      ComponentDefinition("extractAndTransformTimestamp", ExtractAndTransformTimestamp)
    )
  }

}
