package pl.touk.nussknacker.genericmodel

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.outer.OuterJoinTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{DelayTransformer, PeriodicSourceFactory, PreviousValueTransformer, UnionTransformer, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate-sliding" -> defaultCategory(SlidingAggregateTransformerV2),
    "aggregate-tumbling" -> defaultCategory(TumblingAggregateTransformer),
    "outer-join" -> defaultCategory(OuterJoinTransformer),
    "union" -> defaultCategory(UnionTransformer),
    "union-memo" -> defaultCategory(UnionWithMemoTransformer),
    "delay" -> defaultCategory(DelayTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)

    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "periodic" -> defaultCategory(PeriodicSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)
    val kafkaAvroSinkFactory = new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)

    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(kafkaAvroSinkFactory),
      "dead-end" -> defaultCategory(SinkFactory.noParam(EmptySink))
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

  protected def createSchemaProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(processObjectDependencies)

}
