package pl.touk.nussknacker.engine.avro

import java.util.UUID

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalactic.source.Position
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaRegistryProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, MiniClusterResourceFlink_1_7, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

import scala.concurrent.Future

trait KafkaAvroSpecMixin extends FunSuite
  with BeforeAndAfterEach with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures {

  import KafkaZookeeperUtils._
  import org.apache.flink.api.scala._

  import collection.JavaConverters._

  protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory

  protected def schemaRegistryClient: CSchemaRegistryClient

  protected def kafkaTopicNamespace: String = getClass.getSimpleName

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  protected val stoppableEnv: StoppableExecutionEnvironment with MiniClusterResourceFlink_1_7 = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())

  protected val env = new StreamExecutionEnvironment(stoppableEnv)

  protected var registrar: FlinkStreamingProcessRegistrar = _

  protected lazy val processObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected lazy val keySerializer: KafkaAvroSerializer = {
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }


  override protected def afterEach(): Unit = {
    kafkaClient.closeConsumers()
  }

  /**
    * Default Confluent Avro serialization components
    */
  protected lazy val valueDeserializer: KafkaAvroDeserializer = new KafkaAvroDeserializer(schemaRegistryClient)
  protected lazy val valueSerializer: KafkaAvroSerializer = new KafkaAvroSerializer(schemaRegistryClient)

  protected def createSchemaRegistryProvider[T:TypeInformation](useSpecificAvroReader: Boolean, formatKey: Boolean = false): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](
      confluentClientFactory,
      processObjectDependencies,
      useSpecificAvroReader = useSpecificAvroReader,
      formatKey = formatKey
    )

  protected def pushMessage(obj: Any, objectTopic: String, topic: Option[String] = None, timestamp: java.lang.Long = null): Unit = {
    val serializedObj = valueSerializer.serialize(objectTopic, obj)
    val ret = kafkaClient.sendRawMessage(topic.getOrElse(objectTopic), Array.empty, serializedObj, None, timestamp).futureValue
    kafkaClient.createConsumer(groupId = UUID.randomUUID().toString).consume(topic.getOrElse(objectTopic)).head
    logger.info(s"Produced: ${topic.getOrElse(objectTopic)}, offset: ${ret.offset()}")
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: Any, topic: String): Unit = {
    val record = kafkaSerializer.serialize(obj, null)
    val ret = kafkaClient.sendRawMessage(topic, record.key(), record.value()).futureValue
    kafkaClient.createConsumer(groupId = UUID.randomUUID().toString).consume(topic).head
    logger.info(s"Produced: $topic, offset: ${ret.offset()}")
  }

  protected def consumeMessages(topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer(groupId = UUID.randomUUID().toString)
    consumer.consume(topic, 50).map { record =>
      valueDeserializer.deserialize(topic, record.message())
    }.take(count).toList
  }

  protected def consumeMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer(groupId = UUID.randomUUID().toString)
    consumer.consumeWithConsumerRecord(topic, 50).map { record =>
      kafkaDeserializer.deserialize(record)
    }.take(count).toList
  }

  protected def consumeAndVerifyMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, expected: List[Any]): Assertion = {
    val result = consumeMessages(kafkaDeserializer, topic, expected.length)
    result shouldBe expected
  }

  protected def consumeAndVerifyMessages(topic: String, expected: List[Any]): Assertion = {
    val result = consumeMessages(topic, expected.length)
    result shouldBe expected
  }

  protected def consumeAndVerifyMessages(topic: String, expected: Any): Assertion =
    consumeAndVerifyMessages(topic, List(expected))

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    *
    * @param name
    * @param schemas
    * @return
    */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      schemaRegistryClient.register(inputSubject, schema)
      schemaRegistryClient.register(outputSubject, schema)
    })

    topicConfig
  }

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  object TopicConfig {
    private final val inputPrefix = "test.avro.input"
    private final val outputPrefix = "test.avro.output"

    def apply(testName: String, schemas: List[Schema]): TopicConfig = {
      val inputTopic = s"$inputPrefix.$kafkaTopicNamespace.$testName"
      val outputTopic = s"$outputPrefix.$kafkaTopicNamespace.$testName"
      new TopicConfig(inputTopic, outputTopic, schemas, isKey = false)
    }
  }

  protected def createAvroSourceFactory(useSpecificAvroReader: Boolean): KafkaAvroSourceFactory[GenericRecord] = {
    val schemaRegistryProvider = createSchemaRegistryProvider[GenericRecord](useSpecificAvroReader = useSpecificAvroReader)
    new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
  }

  protected def createAvroSinkFactory(useSpecificAvroReader: Boolean): KafkaAvroSinkFactory = {
    val schemaRegistryProvider = createSchemaRegistryProvider[Any](useSpecificAvroReader = useSpecificAvroReader)
    new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
  }
}

case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)
