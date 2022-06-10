package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object LiteKafkaTestScenarioRunner {
  val DefaultKafkaConfig: Config =
    ConfigFactory
      .empty()
      .withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema-registry:666"))
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true))

  def apply(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition]): LiteKafkaTestScenarioRunner =
    new LiteKafkaTestScenarioRunner(schemaRegistryClient, components, DefaultKafkaConfig)
}

class LiteKafkaTestScenarioRunner(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition], config: Config) extends TestScenarioRunner {

  override type Input = ConsumerRecord[String, Any]
  override type Output = ProducerRecord[String, Any]

  type SerializedInput = ConsumerRecord[String, Array[Byte]]
  type SerializedOutput = ProducerRecord[String, Array[Byte]]

  type AvroInput = ConsumerRecord[String, GenericRecord]
  type AvroOutput = ProducerRecord[String, GenericRecord]

  private val schemasCache = new DefaultCache[String, SchemaData](cacheConfig = CacheConfig())
  private val delegate = LiteTestScenarioRunner(components, config)

  override def runWithData[T<:Input:ClassTag, R<:Output](scenario: EspProcess, data: List[T]): List[R] =
    delegate
      .runWithData[T, R](scenario, data)

  def runWithAvroData(scenario: EspProcess, data: List[AvroInput]): List[AvroOutput] = {
    val serializedData = data.map(serialize)

    delegate
      .runWithData[SerializedInput, SerializedOutput](scenario, serializedData)
      .map(output => {
        val schema = getSchemaData(output.topic()).schema.asInstanceOf[AvroSchema]
        val (_, value) = ConfluentUtils.deserializeSchemaIdAndRecord(output.value(), schema.rawSchema())
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), output.key(), value)
      })
  }

  private def serialize(input: AvroInput ): SerializedInput = {
    val schemaData = getSchemaData(input.topic)
    val value = ConfluentUtils.serializeRecordToBytesArray(input.value(), schemaData.id)
    new ConsumerRecord(input.topic, input.partition, input.offset, input.key, value)
  }

  private def getSchemaData(topic: String) = {
    val subject = ConfluentUtils.topicSubject(topic, false)
    schemasCache.getOrCreate(subject) {
      val schemaMetadata = schemaRegistryClient.getLatestSchemaMetadata(subject)
      val schema = schemaRegistryClient.getSchemaById(schemaMetadata.getId)
      SchemaData(schemaMetadata.getId, schema)
    }
  }

  def registerSchemaAvro(topic: String, schema: Schema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToAvroSchema(schema)
  )

  def registerSchemaAvro(topic: String, strSchema: String): Int =
    registerSchemaAvro(topic, AvroUtils.parseSchema(strSchema))

  case class SchemaData(id: Int, schema: ParsedSchema)
}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset = 1

  def apply[T](topic: String, value: T): ConsumerRecord[String, T] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, null, value)

  def apply[T](topic: String, key: String, value: T): ConsumerRecord[String, T] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, key, value)
}
