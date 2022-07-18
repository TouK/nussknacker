package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.CharSequenceSerializer
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult

object LiteKafkaTestScenarioRunner {
  val DefaultKafkaConfig: Config =
    ConfigFactory
      .empty()
      .withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))
      .withValue("kafka.useStringForKey", ConfigValueFactory.fromAnyRef(true))

  def apply(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition]): LiteKafkaTestScenarioRunner =
    new LiteKafkaTestScenarioRunner(schemaRegistryClient, components, DefaultKafkaConfig)
}

class LiteKafkaTestScenarioRunner(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition], config: Config) extends TestScenarioRunner {

  type SerializedInput = ConsumerRecord[Array[Byte], Array[Byte]]
  type SerializedOutput = ProducerRecord[Array[Byte], Array[Byte]]

  type AvroInput = ConsumerRecord[KafkaAvroElement, KafkaAvroElement]

  private val delegate: LiteTestScenarioRunner = LiteTestScenarioRunner(components, config)
  private val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)
  private val keyStringSerializer = new CharSequenceSerializer
  private val keyStringDeserializer = new StringDeserializer

  def runWithAvroData[K, V](scenario: EspProcess, data: List[AvroInput]): RunnerResult[ProducerRecord[K, V]] = {
    val serializedData = data.map(serializeInput)

    runWithRawData(scenario, serializedData)
      .map{ result =>
        val successes = result
          .successes
          .map{ output =>
            val value = deserialize[V](output.value())
            val key = Option(output.key()).map(deserializeKey[K](output.topic(), _)).getOrElse(null.asInstanceOf[K])
            new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value)
          }

        result.copy(successes = successes)
      }
  }

  def runWithRawData(scenario: EspProcess, data: List[SerializedInput]): RunnerResult[SerializedOutput] =
    delegate
      .runWithData[SerializedInput, SerializedOutput](scenario, data)

  def registerAvroSchema(topic: String, schema: Schema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToAvroSchema(schema)
  )

  private def serializeInput(input: AvroInput): SerializedInput = {
    val value = serialize(input.value())
    val key = Option(input.key()).map(serializeKey(input.topic(), _)).orNull
    new ConsumerRecord(input.topic, input.partition, input.offset, key, value)
  }

  private def serializeKey(topic: String, keyElement: KafkaAvroElement) = if (kafkaConfig.useStringForKey) {
    keyStringSerializer.serialize(topic, keyElement.data)
  } else {
    serialize(keyElement)
  }

  private def serialize(element: KafkaAvroElement): Array[Byte] = {
    val containerData = element.data match {
      case container: GenericContainer => container
      case any =>
        val schema = schemaRegistryClient.getSchemaById(element.schemaId).asInstanceOf[AvroSchema].rawSchema()
        new NonRecordContainer(schema, any)
    }

    ConfluentUtils.serializeContainerToBytesArray(containerData, element.schemaId)
  }

  private def deserializeKey[T](topic: String, payload: Array[Byte]) = if (kafkaConfig.useStringForKey) {
    keyStringDeserializer.deserialize(topic, payload).asInstanceOf[T]
  } else {
    deserialize[T](payload)
  }

  private def deserialize[T](payload: Array[Byte]): T = {
    val schemaId = ConfluentUtils.readId(payload)
    val schema = schemaRegistryClient.getSchemaById(schemaId).asInstanceOf[AvroSchema]
    val (_, data) = ConfluentUtils.deserializeSchemaIdAndData[T](payload, schema.rawSchema())
    data
  }

}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset = 1

  def apply[K, V](topic: String, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, null.asInstanceOf[K], value)

  def apply[K, V](topic: String, key: K, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, key, value)
}

case class KafkaAvroElement(data: AnyRef, schemaId: Int)

object KafkaAvroConsumerRecord {
  def apply(topic: String, value: AnyRef, schemaId: Int): ConsumerRecord[KafkaAvroElement, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(value, schemaId))

  def apply(topic: String, key: AnyRef, keySchemaId: Int, value: AnyRef, valueSchemaId: Int): ConsumerRecord[KafkaAvroElement, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(key, keySchemaId), KafkaAvroElement(value, valueSchemaId))
}
