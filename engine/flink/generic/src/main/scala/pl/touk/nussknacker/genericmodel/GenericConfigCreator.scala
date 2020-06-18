package pl.touk.nussknacker.genericmodel

import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SimpleSlidingAggregateTransformerV2, SimpleTumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.{PreviousValueTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate-sliding" -> defaultCategory(SimpleSlidingAggregateTransformerV2),
    "aggregate-tumbling" -> defaultCategory(SimpleTumblingAggregateTransformer),
    "union" -> defaultCategory(UnionTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaProvider[GenericRecord](processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)

    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(avroSourceFactory),
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider[Any](processObjectDependencies)
    val kafkaAvroSinkFactory = new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)

    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(kafkaAvroSinkFactory)
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date)
      ),
      List()
    )
  }

  protected def createSchemaProvider[T:TypeInformation](processObjectDependencies: ProcessObjectDependencies):SchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](processObjectDependencies)

}
