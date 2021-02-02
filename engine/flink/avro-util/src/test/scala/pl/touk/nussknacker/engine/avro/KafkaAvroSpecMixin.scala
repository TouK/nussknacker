package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{AbstractConfluentKafkaAvroDeserializer, AbstractConfluentKafkaAvroSerializer}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaRegistryProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, StringKeyedValue}
import pl.touk.nussknacker.engine.graph.{EspProcess, expression}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.{NussknackerAssertions, PatientScalaFutures}

trait KafkaAvroSpecMixin extends FunSuite with FlinkSpec with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions with PatientScalaFutures {

  import KafkaZookeeperUtils._
  import spel.Implicits._

  import collection.JavaConverters._

  protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory

  protected def schemaRegistryClient: CSchemaRegistryClient

  protected def kafkaTopicNamespace: String = getClass.getSimpleName

  override lazy val config: Config = prepareConfig

  protected def prepareConfig: Config = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
      .withValue("kafka.avroKryoGenericRecordSchemaIdSerialization", fromAnyRef(true))
      // we turn off auto registration to do it on our own passing mocked schema registry client
      .withValue(s"kafka.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}", fromAnyRef(false))
  }

  protected var registrar: FlinkProcessRegistrar = _

  protected lazy val testProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))

  protected def executionConfigPreparerChain(modelData: LocalModelData): ExecutionConfigPreparer =
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(config: ExecutionConfig)(metaData: MetaData, processVersion: ProcessVersion): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, confluentClientFactory, kafkaConfig)
        }
      }
    )

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)

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

  protected lazy val schemaRegistryProvider: ConfluentSchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(confluentClientFactory, testProcessObjectDependencies)

  protected def pushMessage(obj: Any, objectTopic: String, topic: Option[String] = None, timestamp: java.lang.Long = null): RecordMetadata = {
    val serializedObj = valueSerializer.serialize(objectTopic, obj)
    kafkaClient.sendRawMessage(topic.getOrElse(objectTopic), Array.empty, serializedObj, None, timestamp).futureValue
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]], obj: AnyRef, topic: String): RecordMetadata = {
    val record = kafkaSerializer.serialize(StringKeyedValue(null, obj), null)
    kafkaClient.sendRawMessage(topic, record.key(), record.value()).futureValue
  }

  protected def consumeAndVerifyMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, expected: List[Any]): Assertion = {
    val result = consumeMessages(kafkaDeserializer, topic, expected.length)
    result shouldBe expected
  }

  protected def consumeMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consumeWithConsumerRecord(topic).map { record =>
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
    consumer.consume(topic).map { record =>
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

  protected lazy val avroSourceFactory: KafkaAvroSourceFactory[Any] = {
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None)
  }

  protected lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    new KafkaAvroSinkFactory(schemaRegistryProvider, testProcessObjectDependencies)
  }

  protected def validationModeParam(validationMode: ValidationMode): expression.Expression = s"'${validationMode.name}'"

  protected def createAvroProcess(source: SourceAvroParam, sink: SinkAvroParam, filterExpression: Option[String] = None): EspProcess = {
    import spel.Implicits._
    val sourceParams = List(TopicParamName -> asSpelExpression(s"'${source.topic}'")) ++ (source match {
      case GenericSourceAvroParam(_, version) => List(SchemaVersionParamName -> asSpelExpression(formatVersionParam(version)))
      case SpecificSourceAvroParam(_) => List.empty
    })

    val baseSinkParams: List[(String, expression.Expression)] = List(
      TopicParamName -> s"'${sink.topic}'",
      SchemaVersionParamName -> formatVersionParam(sink.versionOption),
      SinkKeyParamName -> sink.key,
      SinkValidationModeParameterName -> validationModeParam(sink.validationMode))

    val valueParams = sink.valueEither match {
      case Right(value) => (SinkValueParamName, value) :: Nil
      case Left(fields) => fields
    }

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
      sink.sinkId,
      baseSinkParams ++ valueParams: _*
    )
  }

  protected def formatVersionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) =>s"'$version'"
    }

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, event: Any, expected: AnyRef, useSpecificAvroReader: Boolean = false): Unit =
    runAndVerifyResult(process, topic, List(event), List(expected), useSpecificAvroReader)

  protected def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: AnyRef): Unit =
    runAndVerifyResult(process, topic, events, List(expected), useSpecificAvroReader = false)

  private def runAndVerifyResult(process: EspProcess, topic: TopicConfig, events: List[Any], expected: List[AnyRef], useSpecificAvroReader: Boolean): Unit = {
    kafkaClient.createTopic(topic.input, partitions = 1)
    events.foreach(obj => pushMessage(obj, topic.input))
    kafkaClient.createTopic(topic.output, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topic.output, expected, useSpecificAvroReader)
    }
  }

  protected def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)
    env.withJobRunning(process.id)(action)
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

  case class GenericSourceAvroParam(topic: String, versionOption: SchemaVersionOption) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro"
  }

  case class SpecificSourceAvroParam(topic: String) extends SourceAvroParam {
    override def sourceType: String = "kafka-avro-specific"
  }

  object SourceAvroParam {

    def forGeneric(topicConfig: TopicConfig, versionOption: SchemaVersionOption): SourceAvroParam =
      GenericSourceAvroParam(topicConfig.input, versionOption)

    def forSpecific(topicConfig: TopicConfig): SourceAvroParam =
      SpecificSourceAvroParam(topicConfig.input)

  }

  case class SinkAvroParam(topic: String,
                           versionOption: SchemaVersionOption,
                           valueEither: Either[List[(String, expression.Expression)], expression.Expression],
                           key: String,
                           validationMode: ValidationMode,
                           sinkId: String)

  object SinkAvroParam {
    import spel.Implicits._

    def apply(topicConfig: TopicConfig, version: SchemaVersionOption, value: String, key: String = "", validationMode: ValidationMode = ValidationMode.strict): SinkAvroParam =
      new SinkAvroParam(topicConfig.output, version, Right(value), key, validationMode, "kafka-avro")
  }
}

class SimpleKafkaAvroDeserializer(schemaRegistryClient: CSchemaRegistryClient, _useSpecificAvroReader: Boolean) extends AbstractConfluentKafkaAvroDeserializer {

  this.schemaRegistry = schemaRegistryClient
  this.useSpecificAvroReader = _useSpecificAvroReader

  def deserialize(topic: String, record: Array[Byte]): Any = {
    deserialize(topic, isKey = false, record, null)
  }

  override protected val schemaIdSerializationEnabled: Boolean = true

}

class SimpleKafkaAvroSerializer(schemaRegistryClient: CSchemaRegistryClient) extends AbstractConfluentKafkaAvroSerializer(new DefaultAvroSchemaEvolution) {

  this.schemaRegistry = schemaRegistryClient

  def serialize(topic: String, obj: Any): Array[Byte] = {
    serialize(None, topic, obj, isKey = false)
  }

}
