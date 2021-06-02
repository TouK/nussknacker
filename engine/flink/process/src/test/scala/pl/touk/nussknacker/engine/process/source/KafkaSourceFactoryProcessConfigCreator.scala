package pl.touk.nussknacker.engine.process.source

import io.circe.{Decoder, Encoder}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.kafka.common.serialization.StringDeserializer
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionHandler
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.{SampleKey, SampleValue, createDeserializer}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SampleConsumerRecordDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{SinkForInputMeta, SinkForStrings}
import pl.touk.nussknacker.engine.process.source.KafkaSourceFactoryProcessConfigCreator._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.WithDataList

import scala.reflect.ClassTag


class KafkaSourceFactoryProcessConfigCreator(kafkaConfig: KafkaConfig) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonKeyValueWithMeta[SampleKey, SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-jsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-jsonValueWithMeta-withException" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMetaWithException[SampleValue](processObjectDependencies, kafkaConfig))
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

  object KafkaConsumerRecordSourceHelper {

    def jsonKeyValueWithMeta[K: ClassTag:Encoder:Decoder, V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, kafkaConfig: KafkaConfig)
    : KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(createDeserializer[K], createDeserializer[V])
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[K, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        None,
        formatterFactory,
        processObjectDependencies
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    def jsonValueWithMeta[V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, kafkaConfig: KafkaConfig): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, createDeserializer[V])
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        None,
        formatterFactory,
        processObjectDependencies
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    // For scenario when prepareInitialParameters fetches list of available topics form some external repository and an exception occurs.
    def jsonValueWithMetaWithException[V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, kafkaConfig: KafkaConfig): KafkaSourceFactory[Any, Any] = {
      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, createDeserializer[V])
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        None,
        formatterFactory,
        processObjectDependencies
      ) {
        override protected def prepareInitialParameters: List[Parameter] = {
          throw new IllegalArgumentException("Checking scenario: fetch topics from external source")
        }
      }
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }
  }
}
