package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object LiteKafkaTestScenarioRunner {
  val DefaultKafkaConfig: Config =
    ConfigFactory
      .empty()
      .withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))

  def apply(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition]): LiteKafkaTestScenarioRunner =
    new LiteKafkaTestScenarioRunner(schemaRegistryClient, components, DefaultKafkaConfig)
}

class LiteKafkaTestScenarioRunner(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition], config: Config) extends TestScenarioRunner {

  override type Input = ConsumerRecord[Any, Any]
  override type Output = ProducerRecord[Any, Any]

  type SerializedInput = ConsumerRecord[Array[Byte], Array[Byte]]
  type SerializedOutput = ProducerRecord[Array[Byte], Array[Byte]]

  type AvroInput[K, V] = ConsumerRecord[KafkaAvroElement[K], KafkaAvroElement[V]]

  private val delegate = LiteTestScenarioRunner(components, config)

  override def runWithData[T<:Input:ClassTag, R<:Output](scenario: EspProcess, data: List[T]): List[R] =
    delegate
      .runWithData[T, R](scenario, data)

  def runWithAvroData[K, V](scenario: EspProcess, data: List[AvroInput[K, V]]): List[ProducerRecord[K, V]] = {
    val serializedData = data.map(serialize)

    delegate
      .runWithData[SerializedInput, SerializedOutput](scenario, serializedData)
      .map(output => {
        val value = deserialize[V](output.value())
        val key = Option(output.key()).map(deserialize[K]).getOrElse(null.asInstanceOf[K])
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value)
      })
  }

  def registerAvroSchema(topic: String, schema: Schema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToAvroSchema(schema)
  )

  private def serialize[K, V](input: AvroInput[K, V]): SerializedInput = {
    val value = ConfluentUtils.serializeDataToBytesArray(input.value().data, input.value().schemaId)
    val key = Option(input.key()).map(key => ConfluentUtils.serializeDataToBytesArray(key.data, key.schemaId)).orNull
    new ConsumerRecord(input.topic, input.partition, input.offset, key, value)
  }

  private def deserialize[T](payload: Array[Byte]) = {
    val schemaId = ConfluentUtils.readId(payload)
    val schema = schemaRegistryClient.getSchemaById(schemaId).asInstanceOf[AvroSchema]
    val (_, data) = ConfluentUtils.deserializeSchemaIdAndData[T](payload, schema.rawSchema())
    data
  }

  case class SchemaData(id: Int, schema: ParsedSchema)
}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset = 1

  def apply[K, V](topic: String, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, null.asInstanceOf[K], value)

  def apply[K, V](topic: String, key: K, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, key, value)
}

case class KafkaAvroElement[T](data: T, schemaId: Int)

object KafkaAvroConsumerRecord {
  def apply[V](topic: String, value: V, schemaId: Int): ConsumerRecord[KafkaAvroElement[String], KafkaAvroElement[V]] =
    KafkaConsumerRecord(topic, KafkaAvroElement(value, schemaId))

  def apply[K, V](topic: String, key: K, keySchemaId: Int, value: V, valueSchemaId: Int): ConsumerRecord[KafkaAvroElement[K], KafkaAvroElement[V]] =
    KafkaConsumerRecord(topic, KafkaAvroElement(key, keySchemaId), KafkaAvroElement(value, valueSchemaId))
}
