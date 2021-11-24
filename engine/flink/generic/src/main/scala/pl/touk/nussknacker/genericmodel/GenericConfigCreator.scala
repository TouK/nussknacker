package pl.touk.nussknacker.genericmodel

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{DelayTransformer, PeriodicSourceFactory, PreviousValueTransformer}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class GenericConfigCreator extends EmptyProcessConfigCreator {

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "Default")
  protected val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate-sliding" -> defaultCategory(SlidingAggregateTransformerV2),
    "aggregate-tumbling" -> defaultCategory(TumblingAggregateTransformer),
    "aggregate-session" -> defaultCategory(SessionWindowAggregateTransformer),
    "single-side-join" -> defaultCategory(SingleSideJoinTransformer),
    "delay" -> defaultCategory(DelayTransformer)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "periodic" -> defaultCategory(PeriodicSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(

    )
  }

  import pl.touk.nussknacker.engine.util.functions._

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    ExpressionConfig(
      Map(
        "GEO" -> defaultCategory(geo),
        "NUMERIC" -> defaultCategory(numeric),
        "CONV" -> defaultCategory(conversion),
        "DATE" -> defaultCategory(date),
        "DATE_FORMAT" -> defaultCategory(dateFormat),
        "UTIL" -> defaultCategory(util),
        "MATH" -> defaultCategory(math),
        "AGG" -> defaultCategory(new AggregateHelper)
      ),
      List()
    )
  }

  override def buildInfo(): Map[String, String] = {
    pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => k -> v.toString } + ("name" -> "generic")
  }

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()
  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())
}
