package pl.touk.nussknacker.genericmodel

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{PreviousValueTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T) = WithCategories(obj, "Default")

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate" -> defaultCategory(SlidingAggregateTransformer),
    "union" -> defaultCategory(UnionTransformer)
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    val avroSourceFactory = new KafkaAvroSourceFactory(kafkaConfig,
      createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory), schemaRegistryClientFactory, None)
    val avroTypedSourceFactory = new KafkaTypedAvroSourceFactory(kafkaConfig,
      createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory), schemaRegistryClientFactory, None)
    Map("kafka-json" -> defaultCategory(new GenericJsonSourceFactory(kafkaConfig)),
        "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(kafkaConfig)),
        "kafka-avro" -> defaultCategory(avroSourceFactory),
        "kafka-typed-avro" -> defaultCategory(avroTypedSourceFactory)
    )
  }

  protected def createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory)
  : DeserializationSchemaFactory[GenericData.Record] =
    new AvroDeserializationSchemaFactory[GenericData.Record](schemaRegistryClientFactory, useSpecificAvroReader = false)

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(kafkaConfig)),
      "kafka-avro" -> defaultCategory(new KafkaSinkFactory(kafkaConfig, createGenericAvroSerializationSchemaFactory(schemaRegistryClientFactory)))
    )
  }

  protected def createGenericAvroSerializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory)
  : SerializationSchemaFactory[Any] =
    new AvroSerializationSchemaFactory(schemaRegistryClientFactory)

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory
    = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler(_))

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(config: Config): ExpressionConfig = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "DATE" -> defaultCategory(date),
        "AVRO" -> defaultCategory(new AvroUtils(schemaRegistryClientFactory, kafkaConfig))
      ),
      List()
    )
  }

  protected def createSchemaRegistryClientFactory: SchemaRegistryClientFactory =
    new SchemaRegistryClientFactory

}