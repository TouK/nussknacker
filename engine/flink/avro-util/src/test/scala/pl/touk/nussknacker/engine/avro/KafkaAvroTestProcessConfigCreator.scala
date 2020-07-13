package pl.touk.nussknacker.engine.avro

import org.apache.avro.generic.GenericData
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schema.GeneratedAvroClassWithLogicalTypes
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

import scala.reflect.ClassTag

class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val avroSourceFactory = new KafkaAvroSourceFactory(createSchemaProvider[GenericData.Record](processObjectDependencies), processObjectDependencies, None)
    val avroSpecificSourceFactory = new SpecificRecordKafkaAvroSourceFactory(createSchemaProvider[GeneratedAvroClassWithLogicalTypes](processObjectDependencies), processObjectDependencies, None)

    Map(
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "kafka-avro-specific" -> defaultCategory(avroSpecificSourceFactory)
    )
  }


  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestmp" -> defaultCategory(ExtractAndTransformTimestmp))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaProvider[Any](processObjectDependencies)

    Map(
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def createSchemaProvider[T: ClassTag](processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](processObjectDependencies)


}

object ExtractAndTransformTimestmp extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Long])
  def methodToInvoke(@ParamName("timestampToSet") timestampToSet: Long): FlinkCustomStreamTransformation
    = FlinkCustomStreamTransformation(_.transform("collectTimestamp",
      new AbstractStreamOperator[ValueWithContext[AnyRef]] with OneInputStreamOperator[Context, ValueWithContext[AnyRef]] {
        override def processElement(element: StreamRecord[Context]): Unit = {
          output.collect(new StreamRecord[ValueWithContext[AnyRef]](ValueWithContext(element.getTimestamp.underlying(), element.getValue), timestampToSet))
        }
      }))

}