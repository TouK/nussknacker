package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.StringDeserializer
import org.everit.json.schema.{Schema => EveritSchema}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, UnspecializedTopicName}
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider
import pl.touk.nussknacker.engine.lite.util.test.confluent.{AzureKafkaAvroElementSerde, ConfluentKafkaAvroElementSerde}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{
  ConfluentSchemaRegistryClient,
  MockSchemaRegistryClient
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaId,
  SchemaIdFromMessageExtractor,
  SchemaRegistryClientFactoryWithRegistration,
  SchemaRegistryClientWithRegistration
}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test.{TestScenarioRunner, TestScenarioRunnerBuilder}
import pl.touk.nussknacker.test.KafkaConfigProperties

import java.nio.charset.StandardCharsets
import java.util.Optional

object LiteKafkaTestScenarioRunner {

  implicit class LiteKafkaTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def kafkaLiteBased(baseConfig: Config = ConfigFactory.load()): LiteKafkaTestScenarioRunnerBuilder = {
      val config = baseConfig.withFallback(
        ConfigFactory
          .empty()
          .withValue(KafkaConfigProperties.bootstrapServersProperty(), ConfigValueFactory.fromAnyRef("kafka:666"))
          .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("schema-registry:666"))
          .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("schema-registry:666"))
          .withValue("kafka.topicsExistenceValidationConfig.enabled", fromAnyRef(false))
      )
      val schemaRegistryClient = new MockSchemaRegistryClient
      LiteKafkaTestScenarioRunnerBuilder(
        List.empty,
        Map.empty,
        config,
        MockSchemaRegistryClientFactory.confluentBased(schemaRegistryClient),
        testRuntimeMode = false
      )
    }

  }

}

case class LiteKafkaTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    schemaRegistryClientFactor: SchemaRegistryClientFactoryWithRegistration,
    testRuntimeMode: Boolean
) extends TestScenarioRunnerBuilder[LiteKafkaTestScenarioRunner, LiteKafkaTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(components: List[ComponentDefinition]): LiteKafkaTestScenarioRunnerBuilder =
    copy(components = components)

  override def withExtraGlobalVariables(
      globalVariables: Map[String, AnyRef]
  ): LiteKafkaTestScenarioRunnerBuilder =
    copy(globalVariables = globalVariables)

  override def inTestRuntimeMode: LiteKafkaTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  def withSchemaRegistryClientFactory(
      schemaRegistryClientFactor: SchemaRegistryClientFactoryWithRegistration
  ): LiteKafkaTestScenarioRunnerBuilder =
    copy(schemaRegistryClientFactor = schemaRegistryClientFactor)

  override def build(): LiteKafkaTestScenarioRunner = {
    val modelDependencies             = ProcessObjectDependencies.withConfig(config)
    val mockedKafkaComponentsProvider = new LiteKafkaComponentProvider(schemaRegistryClientFactor)
    val mockedKafkaComponents         = mockedKafkaComponentsProvider.create(config, modelDependencies)
    val schemaRegistryClient          = schemaRegistryClientFactor.create(KafkaConfig.parseConfig(config))
    val serde = schemaRegistryClient match {
      case _: ConfluentSchemaRegistryClient => ConfluentKafkaAvroElementSerde
      case _: AzureSchemaRegistryClient     => AzureKafkaAvroElementSerde
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported schema registry client: ${schemaRegistryClient.getClass}. " +
            s"Kafka tests mechanism is currently supported only for Confluent schema registry implementation"
        )
    }
    new LiteKafkaTestScenarioRunner(
      mockedKafkaComponents ++ components,
      globalVariables,
      config,
      schemaRegistryClient,
      componentUseCase(testRuntimeMode),
      serde
    )
  }

}

class LiteKafkaTestScenarioRunner(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    schemaRegistryClient: SchemaRegistryClientWithRegistration,
    componentUseCase: ComponentUseCase,
    serde: KafkaAvroElementSerde
) extends TestScenarioRunner {

  type SerializedInput  = ConsumerRecord[Array[Byte], Array[Byte]]
  type SerializedOutput = ProducerRecord[Array[Byte], Array[Byte]]

  type StringInput = ConsumerRecord[String, String]
  type AvroInput   = ConsumerRecord[Any, KafkaAvroElement]

  private val delegate: LiteTestScenarioRunner =
    new LiteTestScenarioRunner(components, globalVariables, config, componentUseCase)
  private val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)
  private val keyStringDeserializer    = new StringDeserializer

  def runWithStringData(
      scenario: CanonicalProcess,
      data: List[StringInput]
  ): RunnerListResult[ProducerRecord[String, String]] = {
    val serializedData = data.map(serializeStringInput)
    runWithRawData(scenario, serializedData)
      .map(_.mapSuccesses { output =>
        val value = new String(output.value(), StandardCharsets.UTF_8)
        val key   = Option(output.key()).map(new String(_, StandardCharsets.UTF_8)).getOrElse(null.asInstanceOf[String])
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value, output.headers())
      })
  }

  def runWithAvroData[K, V](
      scenario: CanonicalProcess,
      data: List[AvroInput]
  ): RunnerListResult[ProducerRecord[K, V]] = {
    val serializedData = data.map(serializeAvroInput)

    runWithRawData(scenario, serializedData)
      .map(_.mapSuccesses { output =>
        val value = deserializeAvroData[V](output.value(), output.headers(), isKey = false)
        val key = Option(output.key())
          .map(deserializeAvroKey[K](output.topic(), _, output.headers()))
          .getOrElse(null.asInstanceOf[K])
        new ProducerRecord(output.topic(), output.partition(), output.timestamp(), key, value, output.headers())
      })
  }

  def runWithRawData(scenario: CanonicalProcess, data: List[SerializedInput]): RunnerListResult[SerializedOutput] =
    delegate
      .runWithData[SerializedInput, SerializedOutput](scenario, data)

  def registerJsonSchema(topic: UnspecializedTopicName, schema: EveritSchema): SchemaId =
    schemaRegistryClient.registerSchema(topic, isKey = false, ConfluentUtils.convertToJsonSchema(schema))

  def registerAvroSchema(topic: UnspecializedTopicName, schema: Schema): SchemaId =
    schemaRegistryClient.registerSchema(topic, isKey = false, ConfluentUtils.convertToAvroSchema(schema))

  private def serializeStringInput(input: StringInput): SerializedInput = {
    val key   = Option(input.key()).map(_.getBytes(StandardCharsets.UTF_8)).orNull
    val value = input.value().getBytes(StandardCharsets.UTF_8)
    new ConsumerRecord(input.topic, input.partition, input.offset, key, value)
  }

  private def serializeAvroInput(input: AvroInput): SerializedInput = {
    val value = serializeAvroElement(input.value(), input.headers(), isKey = false)

    val key = Option(input.key()).map {
      case str: String            => str.getBytes(StandardCharsets.UTF_8)
      case avro: KafkaAvroElement => serializeAvroElement(avro, input.headers(), isKey = true)
      case _ => throw new IllegalArgumentException(s"Unexpected key class: ${input.key().getClass}")
    }.orNull

    new ConsumerRecord(
      input.topic,
      input.partition,
      input.offset,
      input.timestamp(),
      input.timestampType(),
      -1,
      -1,
      key,
      value,
      input.headers(),
      Optional.empty[Integer]()
    )
  }

  // We pass headers because they can be mutated by serde
  private def serializeAvroElement(element: KafkaAvroElement, headers: Headers, isKey: Boolean): Array[Byte] = {
    val containerData = element.data match {
      case container: GenericContainer => container
      case any =>
        val schema = schemaRegistryClient.getSchemaById(element.schemaId).schema.asInstanceOf[AvroSchema].rawSchema()
        new NonRecordContainer(schema, any)
    }

    serde.serializeAvroElement(containerData, element.schemaId, headers, isKey)
  }

  // We pass headers because they can be mutated by serde
  private def deserializeAvroKey[T](topic: String, payload: Array[Byte], headers: Headers) =
    if (kafkaConfig.useStringForKey) {
      keyStringDeserializer.deserialize(topic, payload).asInstanceOf[T]
    } else {
      deserializeAvroData[T](payload, headers, isKey = true)
    }

  def deserializeAvroData[T](payload: Array[Byte], headers: Headers, isKey: Boolean): T =
    Option(payload)
      .map { _ =>
        val schemaIdWithBuffer = serde.schemaIdFromMessageExtractor
          .getSchemaId(headers, payload, isKey)
          .getOrElse(throw new SerializationException("Cannot find schemaId in avro data"))
        val schema = schemaRegistryClient.getSchemaById(schemaIdWithBuffer.value).schema.asInstanceOf[AvroSchema]
        val remainingBytes = new Array[Byte](schemaIdWithBuffer.buffer.remaining())
        schemaIdWithBuffer.buffer.get(remainingBytes)
        AvroUtils.deserialize[T](remainingBytes, schema.rawSchema())
      }
      .getOrElse(null.asInstanceOf[T])

}

trait KafkaAvroElementSerde {

  def serializeAvroElement(
      containerData: GenericContainer,
      schemaId: SchemaId,
      headers: Headers,
      isKey: Boolean
  ): Array[Byte]

  def schemaIdFromMessageExtractor: SchemaIdFromMessageExtractor

}

object KafkaConsumerRecord {
  private val DefaultPartition = 1
  private val DefaultOffset    = 1

  def apply[K, V](topic: TopicName.ForSource, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic.name, DefaultPartition, DefaultOffset, null.asInstanceOf[K], value)

  def apply[K, V](topic: TopicName.ForSource, key: K, value: V): ConsumerRecord[K, V] =
    new ConsumerRecord(topic.name, DefaultPartition, DefaultOffset, key, value)
}

case class KafkaAvroElement(data: Any, schemaId: SchemaId)

object KafkaAvroConsumerRecord {

  def apply(topic: TopicName.ForSource, value: Any, schemaId: SchemaId): ConsumerRecord[Any, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(value, schemaId))

  def apply(
      topic: TopicName.ForSource,
      key: Any,
      keySchemaId: SchemaId,
      value: Any,
      valueSchemaId: SchemaId
  ): ConsumerRecord[KafkaAvroElement, KafkaAvroElement] =
    KafkaConsumerRecord(topic, KafkaAvroElement(key, keySchemaId), KafkaAvroElement(value, valueSchemaId))

  def apply(
      topic: TopicName.ForSource,
      key: String,
      value: Any,
      valueSchemaId: SchemaId
  ): ConsumerRecord[Any, KafkaAvroElement] =
    KafkaConsumerRecord(topic, key, KafkaAvroElement(value, valueSchemaId))

}
