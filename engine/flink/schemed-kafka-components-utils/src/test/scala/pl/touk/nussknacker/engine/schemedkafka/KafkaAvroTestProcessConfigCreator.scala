package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.{SinkForType, TestResultsHolder}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

abstract class KafkaAvroTestProcessConfigCreator(
    sinkForInputMetaResultsHolder: => TestResultsHolder[java.util.Map[String @unchecked, _]]
) extends EmptyProcessConfigCreator {

  override def sourceFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SourceFactory]] = {
    val kafkaConfig      = KafkaConfig.parseConfig(modelConfig.underlyingConfig)
    val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfig)

    val universalSourceFactory = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      universalPayload,
      modelConfig,
      kafkaConfig,
      new FlinkKafkaSourceImplFactory
    )
    val avroGenericSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      universalPayload,
      modelConfig,
      kafkaConfig.copy(useStringForKey = false),
      new FlinkKafkaSourceImplFactory
    )

    Map(
      "kafka"           -> defaultCategory(universalSourceFactory),
      "kafka-key-value" -> defaultCategory(avroGenericSourceFactoryWithKeySchemaSupport)
    )
  }

  override def customStreamTransformers(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SinkFactory]] = {
    val kafkaConfig      = KafkaConfig.parseConfig(modelConfig.underlyingConfig)
    val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfig)

    Map(
      "kafka" -> defaultCategory(
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalPayload,
          modelConfig,
          kafkaConfig,
          FlinkKafkaUniversalSinkImplFactory
        )
      ),
      "sinkForInputMeta" -> defaultCategory(
        SinkForType[java.util.Map[String @unchecked, _]](sinkForInputMetaResultsHolder)
      )
    )
  }

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

}
