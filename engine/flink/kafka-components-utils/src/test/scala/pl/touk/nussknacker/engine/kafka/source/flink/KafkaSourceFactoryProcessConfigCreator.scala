package pl.touk.nussknacker.engine.kafka.source.flink

import io.circe.{Decoder, Encoder}
import org.apache.kafka.common.serialization.StringDeserializer
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, KafkaSourceFactory}
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
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-jsonKeyJsonValueWithMeta" -> defaultCategory(
        KafkaConsumerRecordSourceHelper
          .jsonKeyValueWithMeta[SampleKey, SampleValue](modelDependencies)
      ),
      "kafka-jsonValueWithMeta" -> defaultCategory(
        KafkaConsumerRecordSourceHelper.jsonValueWithMeta[SampleValue](modelDependencies)
      ),
      "kafka-jsonValueWithMeta-withException" -> defaultCategory(
        KafkaConsumerRecordSourceHelper
          .jsonValueWithMetaWithException[SampleValue](modelDependencies)
      )
    )
  }

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
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
      modelDependencies: ProcessObjectDependencies
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
        modelDependencies: ProcessObjectDependencies
    ): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory =
        new SampleConsumerRecordDeserializationSchemaFactory(createDeserializer[K], createDeserializer[V])
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[K, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelDependencies,
        new FlinkKafkaSourceImplFactory(None)
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    def jsonValueWithMeta[V: ClassTag: Encoder: Decoder](
        modelDependencies: ProcessObjectDependencies,
    ): KafkaSourceFactory[Any, Any] = {

      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
        new StringDeserializer with Serializable,
        createDeserializer[V]
      )
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelDependencies,
        new FlinkKafkaSourceImplFactory(None)
      )
      kafkaSource.asInstanceOf[KafkaSourceFactory[Any, Any]]
    }

    // For scenario when prepareInitialParameters fetches list of available topics form some external repository and an exception occurs.
    def jsonValueWithMetaWithException[V: ClassTag: Encoder: Decoder](
        modelDependencies: ProcessObjectDependencies,
    ): KafkaSourceFactory[Any, Any] = {
      val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
        new StringDeserializer with Serializable,
        createDeserializer[V]
      )
      val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, V]
      val kafkaSource = new KafkaSourceFactory(
        deserializationSchemaFactory,
        formatterFactory,
        modelDependencies,
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
