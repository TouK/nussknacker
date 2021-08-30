package pl.touk.nussknacker.engine.kafka.source

import io.circe.{Decoder, Encoder}
import org.apache.kafka.common.serialization.StringDeserializer
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.GenericJsonSourceFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryMixin.{SampleKey, SampleValue, createDeserializer}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessConfigCreator._
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessMixin.recordingExceptionHandler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{ExtractAndTransformTimestamp, SinkForStrings}
import pl.touk.nussknacker.engine.process.helpers.SinkForType
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.reflect.ClassTag

class KafkaSourceFactoryProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonKeyValueWithMeta[SampleKey, SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-jsonValueWithMeta" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-jsonValueWithMeta-withException" -> defaultCategory(KafkaConsumerRecordSourceHelper.jsonValueWithMetaWithException[SampleValue](processObjectDependencies, kafkaConfig)),
      "kafka-GenericJsonSourceFactory" -> defaultCategory(new GenericJsonSourceFactory(processObjectDependencies))
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForStrings" -> defaultCategory(SinkFactory.noParam(SinkForStrings)),
      "sinkForInputMeta" -> defaultCategory(SinkFactory.noParam(SinkForInputMeta)),
      "sinkForSimpleJsonRecord" -> defaultCategory(SinkFactory.noParam(SinkForSampleValue))
    )
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestGenericNodeSource")

}

object KafkaSourceFactoryProcessConfigCreator {

  case object SinkForSampleValue extends SinkForType[SampleValue]

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

case object SinkForInputMeta extends SinkForType[InputMeta[Any]]
