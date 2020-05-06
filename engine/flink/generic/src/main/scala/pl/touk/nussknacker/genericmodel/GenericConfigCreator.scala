package pl.touk.nussknacker.genericmodel

import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SimpleSlidingAggregateTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{PreviousValueTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate" -> defaultCategory(SimpleSlidingAggregateTransformer),
    "union" -> defaultCategory(UnionTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)
    val avroSourceFactory = KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies)
    val avroFixedSourceFactory = FixedKafkaAvroSourceFactory[GenericData.Record](processObjectDependencies)

    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "kafka-fixed-avro" -> defaultCategory(avroFixedSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)

    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaSinkFactory(schemaRegistryProvider.serializationSchemaFactory, processObjectDependencies))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)

    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date),
        "AVRO" -> defaultCategory(new AvroUtils(schemaRegistryProvider))
      ),
      List()
    )
  }

  protected def createSchemaProvider(processObjectDependencies: ProcessObjectDependencies):SchemaRegistryProvider[GenericData.Record] =
    ConfluentSchemaRegistryProvider[GenericData.Record](processObjectDependencies)

}
