package pl.touk.nussknacker.engine.avro

import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  import org.apache.flink.api.scala._

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaProvider[GenericRecord](processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)

    Map(
      "kafka-avro" -> defaultCategory(avroSourceFactory)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider[Any](processObjectDependencies)

    Map(
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  protected def createSchemaProvider[T:TypeInformation](processObjectDependencies: ProcessObjectDependencies):SchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](processObjectDependencies)
}
