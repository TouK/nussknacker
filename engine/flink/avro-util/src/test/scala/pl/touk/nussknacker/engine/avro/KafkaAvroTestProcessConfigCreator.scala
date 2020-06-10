package pl.touk.nussknacker.engine.avro

import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.{FixedKafkaAvroSourceFactory, KafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)
    val avroSourceFactory = KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies)
    val avroFixedSourceFactory = FixedKafkaAvroSourceFactory[GenericData.Record](processObjectDependencies)

    Map(
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "kafka-fixed-avro" -> defaultCategory(avroFixedSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)

    Map(
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val schemaRegistryProvider = createSchemaProvider(processObjectDependencies)
    val globalProcessVariables = Map("AVRO" -> defaultCategory(new AvroUtils(schemaRegistryProvider)))

    ExpressionConfig(globalProcessVariables, List())
  }

  protected def createSchemaProvider(processObjectDependencies: ProcessObjectDependencies):SchemaRegistryProvider[GenericData.Record] =
    ConfluentSchemaRegistryProvider[GenericData.Record](processObjectDependencies)
}
