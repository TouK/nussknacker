package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericRecord}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, SinkOutputParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaRegistryProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, MiniClusterResourceFlink_1_7, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

trait KafkaAvroSpecMixin extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures {

  import KafkaZookeeperUtils._
  import org.apache.flink.api.scala._
  import spel.Implicits._

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

  protected lazy val testProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected lazy val keySerializer: KafkaAvroSerializer = {
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  /**
    * Default Confluent Avro serialization components
    */
  protected lazy val valueDeserializer: KafkaAvroDeserializer = new KafkaAvroDeserializer(schemaRegistryClient)
  protected lazy val valueSerializer: KafkaAvroSerializer = new KafkaAvroSerializer(schemaRegistryClient)

  protected def createSchemaRegistryProvider[T:TypeInformation](useSpecificAvroReader: Boolean, formatKey: Boolean = false): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](
      confluentClientFactory,
      testProcessObjectDependencies,
      useSpecificAvroReader = useSpecificAvroReader,
      formatKey = formatKey
    )

  protected def pushMessage(obj: Any, objectTopic: String, topic: Option[String] = None, timestamp: java.lang.Long = null): RecordMetadata = {
    val serializedObj = valueSerializer.serialize(objectTopic, obj)
    kafkaClient.sendRawMessage(topic.getOrElse(objectTopic), Array.empty, serializedObj, None, timestamp).futureValue
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[AnyRef], obj: AnyRef, topic: String): RecordMetadata = {
    val record = kafkaSerializer.serialize(obj, null)
    kafkaClient.sendRawMessage(topic, record.key(), record.value()).futureValue
  }

  protected def consumeMessages(topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consume(topic, 20).map { record =>
      valueDeserializer.deserialize(topic, record.message())
    }.take(count).toList
  }

  protected def consumeMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consumeWithConsumerRecord(topic, 20).map { record =>
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

  protected def createAvroSourceFactory(useSpecificAvroReader: Boolean): KafkaAvroSourceFactory[GenericRecord] = {
    val schemaRegistryProvider = createSchemaRegistryProvider[GenericRecord](useSpecificAvroReader = useSpecificAvroReader)
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None)
  }

  protected def createAvroSinkFactory(useSpecificAvroReader: Boolean): KafkaAvroSinkFactory = {
    val schemaRegistryProvider = createSchemaRegistryProvider[Any](useSpecificAvroReader = useSpecificAvroReader)
    new KafkaAvroSinkFactory(schemaRegistryProvider, testProcessObjectDependencies)
  }

  protected def createAvroProcess(source: SourceAvroParam, sink: SinkAvroParam, filterExpression: Option[String] = None): EspProcess = {
    val builder = EspProcessBuilder
      .id(s"avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-avro",
        TopicParamName -> s"'${source.topic}'",
        SchemaVersionParamName -> parseVersion(source.version)
      )

    val filteredBuilder = filterExpression
      .map(filter => builder.filter("filter", filter))
      .getOrElse(builder)

    filteredBuilder.emptySink(
      "end",
      "kafka-avro",
      TopicParamName -> s"'${sink.topic}'",
      SchemaVersionParamName -> parseVersion(sink.version),
      SinkOutputParamName -> s"${sink.output}"
    )
  }

  protected def parseVersion(version: Option[Int]): String =
    version.map(v => s"$v").getOrElse("")

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, event: Any, expected: GenericContainer): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected))

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: GenericContainer): Unit =
    runAndVerifyResult(process, topic, events, List(expected))

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: List[GenericContainer]): Unit = {
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected)
    }
  }

  protected def run(process: EspProcess)(action: => Unit): Unit = {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }

  case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)

  object TopicConfig {
    private final val inputPrefix = "test.avro.input"
    private final val outputPrefix = "test.avro.output"

    def apply(input: String, output: String, schema: Schema, isKey: Boolean): TopicConfig =
      new TopicConfig(input, output, List(schema), isKey = isKey)

    def apply(testName: String, schemas: List[Schema]): TopicConfig = {
      val inputTopic = s"$inputPrefix.$kafkaTopicNamespace.$testName"
      val outputTopic = s"$outputPrefix.$kafkaTopicNamespace.$testName"
      new TopicConfig(inputTopic, outputTopic, schemas, isKey = false)
    }
  }

  case class SourceAvroParam(topic: String, version: Option[Int])

  object SourceAvroParam {
    def apply(topicConfig: TopicConfig, version: Option[Int]): SourceAvroParam =
      new SourceAvroParam(topicConfig.input, version)
  }

  case class SinkAvroParam(topic: String, version: Option[Int], output: String)

  object SinkAvroParam {
    def apply(topicConfig: TopicConfig, version: Option[Int], output: String): SinkAvroParam =
      new SinkAvroParam(topicConfig.output, version, output)
  }
}
