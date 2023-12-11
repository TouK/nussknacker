package pl.touk.nussknacker.engine.schemedkafka

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.process.helpers.SinkForType
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

abstract class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  {
    SinkForInputMeta.clear()
  }

  private val universalPayload = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

  override def sourceFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    val universalSourceFactory = new UniversalKafkaSourceFactory[Any, Any](
      schemaRegistryClientFactory,
      universalPayload,
      processObjectDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    val avroGenericSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory[Any, Any](
      schemaRegistryClientFactory,
      universalPayload,
      processObjectDependencies,
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
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "kafka" -> defaultCategory(
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalPayload,
          processObjectDependencies,
          FlinkKafkaUniversalSinkImplFactory
        )
      ),
      "sinkForInputMeta" -> defaultCategory(SinkForInputMeta.toSinkFactory)
    )
  }

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

}

case object SinkForInputMeta extends SinkForType[InputMeta[Any]]
