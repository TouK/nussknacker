package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigValueFactory.fromAnyRef
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
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.test.{TestScenarioRunner, TestScenarioRunnerBuilder}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import org.everit.json.schema.{Schema => EveritSchema}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.KafkaConfigProperties

import java.nio.charset.StandardCharsets

object LiteKafkaTestScenarioRunner {

  implicit class LiteKafkaTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def kafkaLiteBased(baseConfig: Config = ConfigFactory.load()): LiteKafkaTestScenarioRunnerBuilder = {
      val config = baseConfig.withFallback(ConfigFactory.empty()
        .withValue(KafkaConfigProperties.bootstrapServersProperty(), ConfigValueFactory.fromAnyRef("kafka:666"))
        .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("schema-registry:666"))
        // we disable default kafka components to replace them by mocked
        .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true)))
      val mockSchemaRegistryClient = new MockSchemaRegistryClient
      val mockedKafkaComponents = new LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(mockSchemaRegistryClient))
      val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
      val mockedComponents = mockedKafkaComponents.create(config, processObjectDependencies)
      LiteKafkaTestScenarioRunnerBuilder(mockedComponents, List.empty, config, mockSchemaRegistryClient)
    }

  }

}

case class LiteKafkaTestScenarioRunnerBuilder(private val mockedKafkaComponents: List[ComponentDefinition], extraComponents: List[ComponentDefinition], config: Config, schemaRegistryClient: SchemaRegistryClient)
  extends TestScenarioRunnerBuilder[LiteKafkaTestScenarioRunner, LiteKafkaTestScenarioRunnerBuilder] {

  override def withExtraComponents(extraComponents: List[ComponentDefinition]): LiteKafkaTestScenarioRunnerBuilder =
    copy(extraComponents = extraComponents)

  override def build(): LiteKafkaTestScenarioRunner = new LiteKafkaTestScenarioRunner(mockedKafkaComponents ++ extraComponents, config, schemaRegistryClient)

}

class LiteKafkaTestScenarioRunner(components: List[ComponentDefinition], config: Config, val schemaRegistryClient: SchemaRegistryClient) extends TestScenarioRunner {

  type SerializedInput = ConsumerRecord[Array[Byte], Array[Byte]]
  type SerializedOutput = ProducerRecord[Array[Byte], Array[Byte]]

  type StringInput = ConsumerRecord[String, String]
  type AvroInput = ConsumerRecord[Any, KafkaAvroElement]

  private val delegate: LiteTestScenarioRunner = new LiteTestScenarioRunner(components, config)
  private val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)
  private val keyStringDeserializer = new StringDeserializer

  def runWithStringData(scenario: CanonicalProcess, data: List[StringInput]): RunnerListResult[ProducerRecord[String, String]] = {
    val serializedData = data.map(serializeStringInput)
    runWithRawData(scenario, serializedData)
      .map(_.mapSuccesses { output =>
        val value = new String(output.value(), StandardCharsets.UTF_8)
        val key = Option(output.key()).map(new String(_, StandardCharsets.UTF_8)).getOrElse(null.asInstanceOf[String])
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value)
      })
  }

  def runWithAvroData[K, V](scenario: CanonicalProcess, data: List[AvroInput]): RunnerListResult[ProducerRecord[K, V]] = {
    val serializedData = data.map(serializeAvroInput)

    runWithRawData(scenario, serializedData)
      .map(_.mapSuccesses { output =>
        val value = deserializeAvroData[V](output.value())
        val key = Option(output.key()).map(deserializeAvroKey[K](output.topic(), _)).getOrElse(null.asInstanceOf[K])
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value)
      })
  }

  def runWithRawData(scenario: CanonicalProcess, data: List[SerializedInput]): RunnerListResult[SerializedOutput] =
    delegate
      .runWithData[SerializedInput, SerializedOutput](scenario, data)

  def registerJsonSchema(topic: String, schema: EveritSchema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToJsonSchema(schema)
  )

  def registerAvroSchema(topic: String, schema: Schema): Int = schemaRegistryClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToAvroSchema(schema)
  )

  private def serializeStringInput(input: StringInput): SerializedInput = {
    val key = Option(input.key()).map(_.getBytes(StandardCharsets.UTF_8)).orNull
    val value = input.value().getBytes(StandardCharsets.UTF_8)
    new ConsumerRecord(input.topic, input.partition, input.offset, key, value)
  }

  private def serializeAvroInput(input: AvroInput): SerializedInput = {
    val value = serializeAvroElement(input.value())

    val key = Option(input.key()).map {
      case str: String => str.getBytes(StandardCharsets.UTF_8)
      case avro: KafkaAvroElement => serializeAvroElement(avro)
      case _ => throw new IllegalArgumentException(s"Unexpected key class: ${input.key().getClass}")
    }.orNull

    new ConsumerRecord(input.topic, input.partition, input.offset, key, value)
  }

  private def serializeAvroElement(element: KafkaAvroElement): Array[Byte] = {
    val containerData = element.data match {
      case container: GenericContainer => container
      case any =>
        val schema = schemaRegistryClient.getSchemaById(element.schemaId).asInstanceOf[AvroSchema].rawSchema()
        new NonRecordContainer(schema, any)
    }

    ConfluentUtils.serializeContainerToBytesArray(containerData, element.schemaId)
  }

  private def deserializeAvroKey[T](topic: String, payload: Array[Byte]) = if (kafkaConfig.useStringForKey) {
    keyStringDeserializer.deserialize(topic, payload).asInstanceOf[T]
  } else {
    deserializeAvroData[T](payload)
  }

  private def deserializeAvroData[T](payload: Array[Byte]): T =
    Option(payload)
      .map { p =>
        val schemaId = ConfluentUtils.readId(p)
        val schema = schemaRegistryClient.getSchemaById(schemaId).asInstanceOf[AvroSchema]
        val (_, data) = ConfluentUtils.deserializeSchemaIdAndData[T](p, schema.rawSchema())
        data
      }
      .getOrElse(null.asInstanceOf[T])

}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset = 1

  def apply[K, V](topic: String, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, null.asInstanceOf[K], value)

  def apply[K, V](topic: String, key: K, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic, DefaultPartition, DefaultOffset, key, value)
}

case class KafkaAvroElement(data: Any, schemaId: Int)

object KafkaAvroConsumerRecord {

  def apply(topic: String, value: Any, schemaId: Int): ConsumerRecord[Any, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(value, schemaId))

  def apply(topic: String, key: Any, keySchemaId: Int, value: Any, valueSchemaId: Int): ConsumerRecord[KafkaAvroElement, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(key, keySchemaId), KafkaAvroElement(value, valueSchemaId))

  def apply(topic: String, key: String, value: Any, valueSchemaId: Int): ConsumerRecord[Any, KafkaAvroElement] =
    KafkaConsumerRecord(topic, key, KafkaAvroElement(value, valueSchemaId))

}
