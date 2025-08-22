package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.{SinkForType, TestResultsHolder}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

import java.util

class TestFlinkKafkaComponentProvider(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    sinkForInputMetaResultsHolder: => TestResultsHolder[java.util.Map[String @unchecked, _]]
) {

  def createComponents(modelConfig: ModelConfig): List[ComponentDefinition] = {
    val kafkaComponents = new BaseFlinkKafkaComponentProvider {
      override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
        TestFlinkKafkaComponentProvider.this.schemaRegistryClientFactory
    }.createComponents(
      modelConfig.underlyingConfig.getConfig("components.kafka"),
      modelConfig.namingStrategy,
      modelConfig.jsonLikeValuesEnteringMode
    )

    val testComponents = createTestComponents(modelConfig)

    kafkaComponents ::: testComponents
  }

  private def createTestComponents(modelConfig: ModelConfig) = {
    val kafkaComponentsConfig =
      KafkaComponentsConfig.parseConfig(modelConfig.underlyingConfig.getConfig("components.kafka.config"))
    val kafkaConfigWithKeySchemaSupport = kafkaComponentsConfig.copy(useStringForKey = false)
    val universalSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfigWithKeySchemaSupport),
      kafkaConfigWithKeySchemaSupport,
      modelConfig.namingStrategy,
      new FlinkKafkaSourceImplFactory
    )
    List(
      ComponentDefinition("kafka-key-value", universalSourceFactoryWithKeySchemaSupport),
      ComponentDefinition(
        "sinkForInputMeta",
        SinkForType[util.Map[String @unchecked, _]](sinkForInputMetaResultsHolder)
      ),
      ComponentDefinition("extractAndTransformTimestamp", ExtractAndTransformTimestamp)
    )
  }

}
