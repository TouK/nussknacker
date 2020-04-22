package pl.touk.nussknacker.genericmodel

import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.avro.confluent.{ConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryProvider}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SimpleSlidingAggregateTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{PreviousValueTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate" -> defaultCategory(SimpleSlidingAggregateTransformer),
    "union" -> defaultCategory(UnionTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaProvider = ConfluentSchemaRegistryProvider[GenericData.Record](processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory(schemaProvider, processObjectDependencies, None)
    val avroTypedSourceFactory = new KafkaTypedAvroSourceFactory(schemaProvider, processObjectDependencies, None)

    Map(
      "kafka-json" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies)),
      "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "kafka-typed-avro" -> defaultCategory(avroTypedSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider[GenericData.Record](processObjectDependencies)

    Map(
      "kafka-json" -> defaultCategory(new GenericKafkaJsonSink(processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaSinkFactory(schemaRegistryProvider.serializationSchemaFactory, processObjectDependencies))
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
        "DATE" -> defaultCategory(date),
        "AVRO" -> defaultCategory(new AvroUtils(
          ConfluentSchemaRegistryClientFactory,
          processObjectDependencies.config.as[KafkaConfig]("kafka")
        ))
      ),
      List()
    )
  }

  protected def createSchemaProvider[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](processObjectDependencies)

}
