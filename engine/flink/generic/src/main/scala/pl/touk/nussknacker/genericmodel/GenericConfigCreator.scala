package pl.touk.nussknacker.genericmodel

import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SimpleSlidingAggregateTransformerV2, SimpleTumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.{PreviousValueTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T) = WithCategories(obj, "Default")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate" -> defaultCategory(SimpleSlidingAggregateTransformerV2),
    "aggregate-tumbling" -> defaultCategory(SimpleTumblingAggregateTransformer),
    "union" -> defaultCategory(UnionTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    val avroSourceFactory = new KafkaAvroSourceFactory(
      createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory),
      schemaRegistryClientFactory, None, processObjectDependencies = processObjectDependencies)
    val avroTypedSourceFactory = new KafkaTypedAvroSourceFactory(
      createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory), schemaRegistryClientFactory,
      None, processObjectDependencies = processObjectDependencies)
    Map("kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
        "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
        "kafka-avro" -> defaultCategory(avroSourceFactory),
        "kafka-typed-avro" -> defaultCategory(avroTypedSourceFactory)
    )
  }

  protected def createGenericAvroDeserializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory)
  : DeserializationSchemaFactory[GenericData.Record] =
    new AvroDeserializationSchemaFactory[GenericData.Record](schemaRegistryClientFactory, useSpecificAvroReader = false)

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaSinkFactory(
        createGenericAvroSerializationSchemaFactory(schemaRegistryClientFactory),
        processObjectDependencies))
    )
  }

  protected def createGenericAvroSerializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory)
  : SerializationSchemaFactory[Any] =
    new AvroSerializationSchemaFactory(schemaRegistryClientFactory)

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory
    = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    val schemaRegistryClientFactory = createSchemaRegistryClientFactory
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date),
        "AVRO" -> defaultCategory(new AvroUtils(schemaRegistryClientFactory, kafkaConfig))
      ),
      List()
    )
  }

  protected def createSchemaRegistryClientFactory: SchemaRegistryClientFactory =
    new SchemaRegistryClientFactory

}
