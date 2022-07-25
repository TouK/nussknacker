package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.everit.json.schema.{Schema => EveritSchema}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult


object LiteJsonSchemaKafkaTestScenarioRunner {
  val DefaultKafkaConfig: Config =
    ConfigFactory
      .empty()
      .withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))

  def apply(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition]): LiteJsonSchemaKafkaTestScenarioRunner =
    new LiteJsonSchemaKafkaTestScenarioRunner(schemaRegistryClient, components, DefaultKafkaConfig)
}

class LiteJsonSchemaKafkaTestScenarioRunner(schemaRegistryClient: SchemaRegistryClient, components: List[ComponentDefinition], config: Config) extends TestScenarioRunner {

  type SerializedInput = ConsumerRecord[Array[Byte], Array[Byte]]
  type SerializedOutput = ProducerRecord[Array[Byte], Array[Byte]]

  type AvroInput = ConsumerRecord[Any, KafkaJsonSchemaElement]

  private val delegate: LiteTestScenarioRunner = LiteTestScenarioRunner(components, config)
  private val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)
  private val keyStringDeserializer = new StringDeserializer

  def runWithAvroData[K, V](scenario: EspProcess, data: List[AvroInput]): RunnerResult[ProducerRecord[K, V]] = {
    val serializedData = data.map(serializeInput)

    runWithRawData(scenario, serializedData)
      .map { result =>
        val successes = result
          .successes
          .map { output =>
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

  def registerJsonSchemaSchema(topic: String, schema: EveritSchema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToJsonSchema(schema)
  )

  def serializeInput(input: AvroInput): SerializedInput = {
    new ConsumerRecord(input.topic, input.partition, input.offset, input.timestamp, input.timestampType, -1, input.serializedKeySize, input.serializedValueSize,null, input.value().data, input.headers())
  }

  def serialize(element: Array[Byte]): Array[Byte] = {
   element
  }

  def deserializeKey[T](topic: String, payload: Array[Byte]) = if (kafkaConfig.useStringForKey) {
    keyStringDeserializer.deserialize(topic, payload).asInstanceOf[T]
  } else {
    deserialize[T](payload)
  }

  private def deserialize[T](payload: Array[Byte]): T =
    Option(payload)
      .map { p =>
        val schemaId = ConfluentUtils.readId(p)
        val schema = schemaRegistryClient.getSchemaById(schemaId).asInstanceOf[AvroSchema]
        val (_, data) = ConfluentUtils.deserializeSchemaIdAndData[T](p, schema.rawSchema())
        data
      }
      .getOrElse(null.asInstanceOf[T])

}




