package pl.touk.nussknacker.engine.kafka.source.flink

import io.circe.{Decoder, Encoder}
import org.apache.kafka.common.serialization.StringDeserializer
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.{
  createDeserializer,
  SampleKey,
  SampleValue
}
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.{
  KafkaConsumerRecordSourceHelper,
  ResultsHolders
}
import pl.touk.nussknacker.engine.process.helpers.{SinkForType, TestResultsHolder}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{ExtractAndTransformTimestamp, SinkForStrings}

import scala.reflect.ClassTag

class KafkaSourceFactoryProcessConfigCreator(resultsHolders: () => ResultsHolders) extends EmptyProcessConfigCreator {

  override def sourceFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(
        KafkaConsumerRecordSourceHelper
          .jsonKeyValueWithMeta[SampleKey, SampleValue](modelConfig)
      ),
      "kafka-jsonValueWithMeta" -> defaultCategory(
        KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](modelConfig)
      ),
      "kafka-jsonValueWithMeta-withException" -> defaultCategory(
        KafkaConsumerRecordSourceHelper
          .jsonValueWithMetaWithException[SampleValue](modelConfig)
      )
    )
  }

  override def sinkFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForStrings" -> defaultCategory(SinkForStrings(resultsHolders().sinkForStringsResultsHolder)),
      "sinkForInputMeta" -> defaultCategory(
        SinkForType[java.util.Map[String, _]](resultsHolders().sinkForInputMetaResultsHolder)
      ),
      "sinkForSimpleJsonRecord" -> defaultCategory(
        SinkForType[SampleValue](resultsHolders().sinkForSimpleJsonRecordResultsHolder)
      )
    )
  }

  override def customStreamTransformers(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestamp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestGenericNodeSource")

}

object KafkaSourceFactoryProcessConfigCreator {

  class ResultsHolders {
    val sinkForStringsResultsHolder: TestResultsHolder[String] = new TestResultsHolder[String]
    val sinkForInputMetaResultsHolder: TestResultsHolder[java.util.Map[String @unchecked, _]] =
      new TestResultsHolder[java.util.Map[String @unchecked, _]]
    val sinkForSimpleJsonRecordResultsHolder: TestResultsHolder[SampleValue] = new TestResultsHolder[SampleValue]

    def clear(): Unit = {
      sinkForStringsResultsHolder.clear()
      sinkForInputMetaResultsHolder.clear()
      sinkForSimpleJsonRecordResultsHolder.clear()
    }

  }

  object KafkaConsumerRecordSourceHelper {

    def jsonKeyValueWithMeta[K: ClassTag: Encoder: Decoder, V: ClassTag: Encoder: Decoder](
        modelConfig: ModelConfig
    ): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory =
        new SampleConsumerRecordDeserializationSchemaFactory(createDeserializer[K], createDeserializer[V])
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[K, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelConfig,
        new FlinkKafkaSourceImplFactory(None)
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    def jsonValueWithMeta[V: ClassTag: Encoder: Decoder](
        modelConfig: ModelConfig,
    ): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
        new StringDeserializer with Serializable,
        createDeserializer[V]
      )
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelConfig,
        new FlinkKafkaSourceImplFactory(None)
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    // For scenario when prepareInitialParameters fetches list of available topics form some external repository and an exception occurs.
    def jsonValueWithMetaWithException[V: ClassTag: Encoder: Decoder](
        modelConfig: ModelConfig,
    ): KafkaSourceFactory[Any, Any] = {
      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
        new StringDeserializer with Serializable,
        createDeserializer[V]
      )
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelConfig,
        new FlinkKafkaSourceImplFactory(None)
      ) {
        override protected def prepareInitialParameters: List[Parameter] = {
          throw new IllegalArgumentException("Checking scenario: fetch topics from external source")
        }
      }
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

  }

}
