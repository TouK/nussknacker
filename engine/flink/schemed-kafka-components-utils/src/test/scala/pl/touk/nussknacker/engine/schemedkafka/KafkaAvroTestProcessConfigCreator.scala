package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.process.helpers.{SinkForType, TestResultsHolder}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

abstract class KafkaAvroTestProcessConfigCreator(
    sinkForInputMetaResultsHolder: => TestResultsHolder[java.util.Map[String @unchecked, _]]
) extends EmptyProcessConfigCreator {

  private val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    val universalSourceFactory = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      universalPayload,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    val avroGenericSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      universalPayload,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    ) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = false)
    }

    Map(
      "kafka"           -> defaultCategory(universalSourceFactory),
      "kafka-key-value" -> defaultCategory(avroGenericSourceFactoryWithKeySchemaSupport)
    )
  }

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "kafka" -> defaultCategory(
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalPayload,
          modelDependencies,
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
