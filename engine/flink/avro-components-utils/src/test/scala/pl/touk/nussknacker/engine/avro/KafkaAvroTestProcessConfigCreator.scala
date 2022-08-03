package pl.touk.nussknacker.engine.avro

import org.apache.avro.specific.SpecificRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schema.{GeneratedAvroClassSample, GeneratedAvroClassWithLogicalTypes}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.sink.flink.{FlinkKafkaAvroSinkImplFactory, FlinkKafkaUniversalSinkImplFactory}
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor, UniversalKafkaSinkFactory}
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory, UniversalKafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.ExtractAndTransformTimestamp
import pl.touk.nussknacker.engine.process.helpers.SinkForType

import scala.reflect.ClassTag

abstract class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  {
    SinkForInputMeta.clear()
  }

  private val universalPayload = ConfluentSchemaBasedSerdeProvider.universal(schemaRegistryClientFactory)

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    val schemaBasedMessagesSerdeProvider = createSchemaBasedMessagesSerdeProvider

    // For testing SpecificRecord should be used ONLY GENERATED avro classes.
    // Simple implementations e.g. FullNameV1, although they extend SimpleRecordBase, are not recognized as SpecificRecord classes.
    def avroSpecificSourceFactory[V <: SpecificRecord : ClassTag] = new SpecificRecordKafkaAvroSourceFactory[V](schemaRegistryClientFactory, schemaBasedMessagesSerdeProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))

    val universalSourceFactory = new UniversalKafkaSourceFactory(schemaRegistryClientFactory, universalPayload, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
    val avroGenericSourceFactory = new KafkaAvroSourceFactory(schemaRegistryClientFactory, schemaBasedMessagesSerdeProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
    val avroGenericSourceFactoryWithKeySchemaSupport = new UniversalKafkaSourceFactory(schemaRegistryClientFactory, universalPayload, processObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = false)
    }

    Map(
      "kafka" -> defaultCategory(universalSourceFactory),
      "kafka-avro" -> defaultCategory(avroGenericSourceFactory),
      "kafka-key-value" -> defaultCategory(avroGenericSourceFactoryWithKeySchemaSupport),
      "kafka-avro-specific" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassSample]),
      "kafka-avro-specific-with-logical-types" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassWithLogicalTypes])
    )
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaBasedMessagesSerdeProvider = createSchemaBasedMessagesSerdeProvider

    Map(
      "kafka" -> defaultCategory(new UniversalKafkaSinkFactory(schemaRegistryClientFactory, universalPayload, processObjectDependencies, FlinkKafkaUniversalSinkImplFactory)),
      "kafka-avro-raw" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryClientFactory, schemaBasedMessagesSerdeProvider, processObjectDependencies, FlinkKafkaAvroSinkImplFactory)),
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactoryWithEditor(schemaRegistryClientFactory, schemaBasedMessagesSerdeProvider, processObjectDependencies, FlinkKafkaAvroSinkImplFactory)),
      "sinkForInputMeta" -> defaultCategory(SinkForInputMeta.toSinkFactory)
    )
  }

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory = CachedConfluentSchemaRegistryClientFactory

  protected def createSchemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(schemaRegistryClientFactory)

}

case object SinkForInputMeta extends SinkForType[InputMeta[Any]]
