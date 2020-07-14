package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkOutputParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{AbstractConfluentKafkaAvroDeserializer, AbstractConfluentKafkaAvroSerializer}
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

import scala.reflect.ClassTag

trait KafkaAvroSpecMixin extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures {

  import KafkaZookeeperUtils._
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
  protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): SimpleKafkaAvroDeserializer = new SimpleKafkaAvroDeserializer(schemaRegistryClient, useSpecificAvroReader)
  protected lazy val valueSerializer: SimpleKafkaAvroSerializer = new SimpleKafkaAvroSerializer(schemaRegistryClient)

  protected def createSchemaRegistryProvider[T: ClassTag]: ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](confluentClientFactory, testProcessObjectDependencies)

  protected def pushMessage(obj: Any, objectTopic: String, topic: Option[String] = None, timestamp: java.lang.Long = null): RecordMetadata = {
    val serializedObj = valueSerializer.serialize(objectTopic, obj)
    kafkaClient.sendRawMessage(topic.getOrElse(objectTopic), Array.empty, serializedObj, None, timestamp).futureValue
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[AnyRef], obj: AnyRef, topic: String): RecordMetadata = {
    val record = kafkaSerializer.serialize(obj, null)
    kafkaClient.sendRawMessage(topic, record.key(), record.value()).futureValue
  }

  protected def consumeAndVerifyMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, expected: List[Any]): Assertion = {
    val result = consumeMessages(kafkaDeserializer, topic, expected.length)
    result shouldBe expected
  }

  protected def consumeMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consumeWithConsumerRecord(topic, 20).map { record =>
      kafkaDeserializer.deserialize(record)
    }.take(count).toList
  }

  protected def consumeAndVerifyMessage(topic: String, expected: Any, useSpecificAvroReader: Boolean = false): Assertion =
    consumeAndVerifyMessages(topic, List(expected), useSpecificAvroReader)

  protected def consumeAndVerifyMessages(topic: String, expected: List[Any], useSpecificAvroReader: Boolean = false): Assertion = {
    val result = consumeMessages(topic, expected.length, useSpecificAvroReader)
    result shouldBe expected
  }

  private def consumeMessages(topic: String, count: Int, useSpecificAvroReader: Boolean): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    val valueDeserializer = prepareValueDeserializer(useSpecificAvroReader)
    consumer.consume(topic, 20).map { record =>
      valueDeserializer.deserialize(topic, record.message())
    }.take(count).toList
  }

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
      schemaRegistryClient.register(inputSubject, parsedSchema)
      schemaRegistryClient.register(outputSubject, parsedSchema)
    })

    topicConfig
  }

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  protected def createAvroSourceFactory[T: ClassTag]: KafkaAvroSourceFactory[T] = {
    val schemaRegistryProvider = createSchemaRegistryProvider[T]
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None)
  }

  protected lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    val schemaRegistryProvider = createSchemaRegistryProvider[Any]
    new KafkaAvroSinkFactory(schemaRegistryProvider, testProcessObjectDependencies)
  }

  protected def createAvroProcess(source: SourceAvroParam, sink: SinkAvroParam, filterExpression: Option[String] = None): EspProcess = {
    val sourceParams = List(TopicParamName -> asSpelExpression(s"'${source.topic}'")) ++ (source match {
      case GenericSourceAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(parseVersion(version)))
      case SpecificSourceAvroParam(_) => List.empty
    })

    val builder = EspProcessBuilder
      .id(s"avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        source.sourceType,
        sourceParams: _*
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

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, event: Any, expected: GenericContainer, useSpecificAvroReader: Boolean = false): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected), useSpecificAvroReader)

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: GenericContainer): Unit =
    runAndVerifyResult(process, topic, events, List(expected), useSpecificAvroReader = false)

  private def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: List[GenericContainer], useSpecificAvroReader: Boolean): Unit = {
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected, useSpecificAvroReader)
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

  sealed trait SourceAvroParam {
    def topic: String
    def sourceType: String
  }

  case class GenericSourceAvroParam(topic: String, version: Option[Int]) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro"
  }

  case class SpecificSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific"
  }

  object SourceAvroParam {

    def forGeneric(topicConfig: TopicConfig, version: Option[Int]): SourceAvroParam =
      GenericSourceAvroParam(topicConfig.input, version)

    def forSpecific(topicConfig: TopicConfig): SourceAvroParam =
      SpecificSourceAvroParam(topicConfig.input)

  }

  case class SinkAvroParam(topic: String, version: Option[Int], output: String)

  object SinkAvroParam {
    def apply(topicConfig: TopicConfig, version: Option[Int], output: String): SinkAvroParam =
      new SinkAvroParam(topicConfig.output, version, output)
  }
}

class SimpleKafkaAvroDeserializer(schemaRegistryClient: CSchemaRegistryClient, _useSpecificAvroReader: Boolean) extends AbstractConfluentKafkaAvroDeserializer {

  this.schemaRegistry = schemaRegistryClient
  this.useSpecificAvroReader = _useSpecificAvroReader

  def deserialize(topic: String, record: Array[Byte]): Any = {
    deserialize(topic, isKey = false, record, null)
  }

}

class SimpleKafkaAvroSerializer(schemaRegistryClient: CSchemaRegistryClient) extends AbstractConfluentKafkaAvroSerializer(new DefaultAvroSchemaEvolution) {

  this.schemaRegistry = schemaRegistryClient

  def serialize(topic: String, obj: Any): Array[Byte] = {
    serialize(None, topic, obj, isKey = false)
  }

}
