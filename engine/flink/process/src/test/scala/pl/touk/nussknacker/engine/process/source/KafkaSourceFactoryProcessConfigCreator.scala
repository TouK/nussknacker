package pl.touk.nussknacker.engine.process.source

import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionHandler
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SampleConsumerRecordDeserializationSchemaFactory, SampleConsumerRecordSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatter
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, KafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForStrings
import pl.touk.nussknacker.engine.process.source.KafkaSourceFactoryProcessConfigCreator._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.WithDataList

import scala.reflect.ClassTag


class KafkaSourceFactoryProcessConfigCreator(kafkaConfig: KafkaConfig) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonKeyValueWithMeta[SampleKey, SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-jsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](processObjectDependencies, kafkaConfig))
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForStrings" -> defaultCategory(SinkFactory.noParam(SinkForStrings)),
      "sinkForInputMeta" -> defaultCategory(SinkFactory.noParam(SinkForInputMeta)),
      "sinkForSimpleJsonRecord" -> defaultCategory(SinkFactory.noParam(SinkForSampleValue))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestGenericNodeSource")

}

object KafkaSourceFactoryProcessConfigCreator {
  val recordingExceptionHandler = new RecordingExceptionHandler

  case object SinkForSampleValue extends BasicFlinkSink with WithDataList[SampleValue] {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        add(value.asInstanceOf[SampleValue])
      }
    }

    override def testDataOutput: Option[Any => String] = None
  }

  case object SinkForInputMeta extends BasicFlinkSink with WithDataList[InputMeta[Any]] {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        add(value.asInstanceOf[InputMeta[Any]])
      }
    }

    override def testDataOutput: Option[Any => String] = None
  }

  object KafkaConsumerRecordSourceHelper {

    def jsonKeyValueWithMeta[K: ClassTag:Encoder:Decoder, V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, kafkaConfig: KafkaConfig)
    : KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(createDeserializer[K], createDeserializer[V])
      val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(createSerializer[K], createSerializer[V])
      val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
        deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig),
        serializationSchemaFactory.create("dummyTopic", kafkaConfig)
      )
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        None,
        testDataRecordFormatter,
        processObjectDependencies
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    def jsonValueWithMeta[V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, kafkaConfig: KafkaConfig): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, createDeserializer[V])
      val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(new StringSerializer with Serializable, createSerializer[V])
      val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
        deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig),
        serializationSchemaFactory.create("dummyTopic", kafkaConfig)
      )
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        None,
        testDataRecordFormatter,
        processObjectDependencies
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }
  }
}
