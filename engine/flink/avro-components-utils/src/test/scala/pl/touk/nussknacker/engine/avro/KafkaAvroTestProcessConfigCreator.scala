package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.specific.SpecificRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schema.{GeneratedAvroClassSample, GeneratedAvroClassWithLogicalTypes}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory}
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

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider

    // For testing SpecificRecord should be used ONLY GENERATED avro classes.
    // Simple implementations e.g. FullNameV1, although they extend SimpleRecordBase, are not recognized as SpecificRecord classes.
    def avroSpecificSourceFactory[V <: SpecificRecord : ClassTag] = new SpecificRecordKafkaAvroSourceFactory[V](schemaRegistryProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))

    val avroGenericSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
    val avroGenericSourceFactoryWithKeySchemaSupport = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = false)
    }

    Map(
      "kafka-avro" -> defaultCategory(avroGenericSourceFactory),
      "kafka-avro-key-value" -> defaultCategory(avroGenericSourceFactoryWithKeySchemaSupport),
      "kafka-avro-specific" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassSample]),
      "kafka-avro-specific-with-logical-types" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassWithLogicalTypes])
    )
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider
    Map(
      "kafka-avro-raw" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies, FlinkKafkaAvroSinkImplFactory)),
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactoryWithEditor(schemaRegistryProvider, processObjectDependencies, FlinkKafkaAvroSinkImplFactory)),
      "sinkForInputMeta" -> defaultCategory(SinkForInputMeta.toSinkFactory)
    )
  }

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def createSchemaRegistryProvider: SchemaRegistryProvider[AvroSchema] =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory)

}

case object SinkForInputMeta extends SinkForType[InputMeta[Any]]
