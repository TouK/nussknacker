package pl.touk.nussknacker.engine.process.source

import io.circe.{Decoder, Encoder}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordDeserializationSchemaFactory, InputMeta, KafkaContextInitializer}
import pl.touk.nussknacker.engine.kafka.serialization.FixedKafkaDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaGenericNodeSourceFactory
import pl.touk.nussknacker.engine.kafka.util.ConsumerRecordToJsonFormatter
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin._
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForStrings
import pl.touk.nussknacker.engine.process.source.KafkaGenericNodeProcessConfigCreator._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.WithDataList

import scala.reflect.ClassTag


class KafkaGenericNodeProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonKeyValueWithMeta[SampleKey, SampleValue](processObjectDependencies)),
      "kafka-jsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](processObjectDependencies))
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

object KafkaGenericNodeProcessConfigCreator {
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

    import scala.reflect.classTag

    def jsonKeyValueWithMeta[K: ClassTag:Encoder:Decoder, V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies)
    : KafkaGenericNodeSourceFactory[Any] = {

      implicit val keyTypeInformation: TypeInformation[K] = TypeInformation.of(classTag[K].runtimeClass.asInstanceOf[Class[K]])
      implicit val valueTypeInformation: TypeInformation[V] = TypeInformation.of(classTag[V].runtimeClass.asInstanceOf[Class[V]])

      val schema = ConsumerRecordDeserializationSchemaFactory.create(
        new EspDeserializationSchema[K](bytes => decodeJsonUnsafe[K](bytes)),
        new EspDeserializationSchema[V](bytes => decodeJsonUnsafe[V](bytes))
      )
      val testDataRecordFormatter = new ConsumerRecordToJsonFormatter
      val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
      val contextInitializer = new KafkaContextInitializer[K, V]

      val kafkaSource = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, Some(contextInitializer))
      kafkaSource.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
    }

    def jsonValueWithMeta[V: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies): KafkaGenericNodeSourceFactory[Any] = {

      implicit val valueTypeInformation: TypeInformation[V] = TypeInformation.of(classTag[V].runtimeClass.asInstanceOf[Class[V]])

      val schema = ConsumerRecordDeserializationSchemaFactory.create(
        new EspDeserializationSchema[V](bytes => decodeJsonUnsafe[V](bytes))
      )

      val testDataRecordFormatter = new ConsumerRecordToJsonFormatter
      val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
      val contextInitializer = new KafkaContextInitializer[String, V]

      val kafkaSource = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, Some(contextInitializer))
      kafkaSource.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
    }
  }
}
