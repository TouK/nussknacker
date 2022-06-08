package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.util.test.KafkaTestScenarioRunner.{
  ConsumerRecordWihValue, ProducerRecordWihValue, KafkaInputType, KafkaOutputType, SinkName, SourceName
}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object KafkaTestScenarioRunner {
  type KafkaInputType = ConsumerRecord[String, Any]
  type KafkaOutputType = ProducerRecord[String, Any]

  val SourceName = "test-kafka-source"
  val SinkName = "test-kafka-sink"

  implicit class ConsumerRecordWihValue(record: ConsumerRecord[String, Any]) {
    def withValue(value: Any): ConsumerRecord[String, Any] =
      new ConsumerRecord(record.topic, record.partition, record.offset, record.key, value)
  }

  implicit class ProducerRecordWihValue(record: ProducerRecord[String, Any]) {
    def withValue(value: Any): ProducerRecord[String, Any] =
      new ProducerRecord(record.topic, record.partition, record.timestamp, record.key, value)
  }

  def createConfig(config: Config): Config =
    config.withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))
}

class KafkaTestScenarioRunner(components: List[ComponentDefinition], config: Config,
                              valueSerializer: Option[Serializer[Any]],
                              valueDeserializer: Option[Deserializer[Any]]) extends TestScenarioRunner with SynchronousLiteRunner {

  override type Input = KafkaInputType
  override type Output = KafkaOutputType

  override def runWithData[T<:Input:ClassTag, R<:Output](scenario: EspProcess, data: List[T]): List[R] = {
    List(SourceName, SinkName).foreach(componentName => {
      assert(components.exists(_.name == componentName), s"Missing component: $componentName.")
    })

    val consumeRecords = valueSerializer.map(serializer => data.map(consumer => {
      val value = serializer.serialize(consumer.topic(), consumer.value())
      consumer.withValue(value)
    })).getOrElse(data)

    runSynchronousWithData[KafkaInputType, KafkaOutputType](config, components, scenario, consumeRecords)
      .map(output => {
        valueDeserializer
          .map(deserializer => {
            val value = deserializer.deserialize(output.topic(), output.value().asInstanceOf[Array[Byte]])
            output.withValue(value)
          })
          .getOrElse(output).asInstanceOf[R]
        }
      )
  }

  def runWithResultValue[T<:Input:ClassTag](scenario: EspProcess, data: List[T]): List[Any] =
    runWithData[KafkaInputType, KafkaOutputType](scenario, data).map(_.value())
}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset = 1

  def apply(topic: String, value: Any): ConsumerRecord[String, Any] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, null, value)

  def apply(topic: String, key: String, value: Any): ConsumerRecord[String, Any] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, key, value)
}
