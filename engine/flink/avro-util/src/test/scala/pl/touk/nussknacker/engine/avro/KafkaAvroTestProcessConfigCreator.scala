package pl.touk.nussknacker.engine.avro

import org.apache.avro.specific.SpecificRecord
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.schema.{GeneratedAvroClassSample, GeneratedAvroClassWithLogicalTypes}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionHandler
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.SinkForType
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.reflect.ClassTag

object KafkaAvroTestProcessConfigCreator {
  val recordingExceptionHandler = new RecordingExceptionHandler
}

class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  protected val schemaRegistryProvider: SchemaRegistryProvider = createSchemaRegistryProvider

  {
    SinkForInputMeta.clear()
  }

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {

    // For testing SpecificRecord should be used ONLY GENERATED avro classes.
    // Simple implementations e.g. FullNameV1, although they extend SimpleRecordBase, are not recognized as SpecificRecord classes.
    def avroSpecificSourceFactory[V <: SpecificRecord: ClassTag] = new SpecificRecordKafkaAvroSourceFactory[V](schemaRegistryProvider, processObjectDependencies, None)
    val avroGenericSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
    val avroGenericSourceFactoryWithKeySchemaSupport = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None) {
      override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = false)
    }

    Map(
      "kafka-avro" -> defaultCategory(avroGenericSourceFactory),
      "kafka-avro-key-value" -> defaultCategory(avroGenericSourceFactoryWithKeySchemaSupport),
      "kafka-avro-specific" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassSample]),
      "kafka-avro-specific-with-logical-types" -> defaultCategory(avroSpecificSourceFactory[GeneratedAvroClassWithLogicalTypes])
    )
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestmp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "kafka-avro-raw" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)),
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactoryWithEditor(schemaRegistryProvider, processObjectDependencies)),
      "sinkForInputMeta" -> defaultCategory(SinkFactory.noParam(SinkForInputMeta))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def createSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()

}

object ExtractAndTransformTimestamp extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Long])
  def methodToInvoke(@ParamName("timestampToSet") timestampToSet: Long): FlinkCustomStreamTransformation
    = FlinkCustomStreamTransformation(_.transform("collectTimestamp",
      new AbstractStreamOperator[ValueWithContext[AnyRef]] with OneInputStreamOperator[Context, ValueWithContext[AnyRef]] {
        override def processElement(element: StreamRecord[Context]): Unit = {
          output.collect(new StreamRecord[ValueWithContext[AnyRef]](ValueWithContext(element.getTimestamp.underlying(), element.getValue), timestampToSet))
        }
      }))

}

case object SinkForInputMeta extends SinkForType[InputMeta[Any]]
