package pl.touk.nussknacker.genericmodel

import java.nio.charset.StandardCharsets

import cats.data.NonEmptyList
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, MockConfluentSchemaRegistryClientBuilder, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.cache.DefaultCache

class GenericItSpec extends FunSuite with BeforeAndAfterAll with Matchers with KafkaSpec with EitherValues with LazyLogging {

  import KafkaZookeeperUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  lazy val mockProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  lazy val confluentSchemaRegistryClient: ConfluentSchemaRegistryClient = factory.createSchemaRegistryClient(kafkaConfig)

  val JsonInTopic: String = "name.json.input"
  val JsonOutTopic: String = "name.json.output"

  private val givenNotMatchingJsonObj =
    """{
      |  "first": "Zenon",
      |  "last": "Nowak",
      |  "nestMap": { "nestedField": "empty" },
      |  "list1": [ {"listField": "full" } ],
      |  "list2": [ 123 ]
      |}""".stripMargin
  private val givenMatchingJsonObj =
    """{
      |  "first": "Jan",
      |  "last": "Kowalski",
      |  "nestMap": { "nestedField": "empty" },
      |  "list1": [ {"listField": "full" } ],
      |  "list2": [ 123 ]
      |}""".stripMargin

  private val givenNotMatchingAvroObj = BestEffortAvroEncoder.encodeRecordOrError(
    Map("first" ->"Zenon", "last" -> "Nowak"), RecordSchemaV1
  )

  private val givenMatchingAvroObj = BestEffortAvroEncoder.encodeRecordOrError(
    Map("first" ->"Jan", "last" -> "Kowalski"), RecordSchemaV1
  )

  private val givenMatchingAvroObjV2 = BestEffortAvroEncoder.encodeRecordOrError(
      Map("first" ->"Jan", "middle" -> "Tomek", "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenSecondMatchingAvroObj = BestEffortAvroEncoder.encodeRecordOrError(
    Map("firstname" ->"Jan"), SecondRecordSchemaV1
  )

  private def jsonProcess(filter: String) =
    EspProcessBuilder
      .id("json-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-typed-json",
        "topic" -> s"'$JsonInTopic'",
        "type" ->
          """{
            |  "first": "String",
            |  "last": "String",
            |  "nestMap": { "nestedField": "String" },
            |  "list1": {{"listField": "String"}},
            |  "list2": { "Long" }
            |}""".stripMargin
      )
      .filter("name-filter", filter)
      .sink("end", "#input", "kafka-json", "topic" -> s"'$JsonOutTopic'")

  private def avroProcess(topicConfig: TopicConfig, version: Integer) =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-avro",
        KafkaAvroFactory.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroFactory.VersionParamName -> versionParam(version)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .sink(
        "end",
        "#input",
        "kafka-avro",
        KafkaAvroFactory.TopicParamName  -> s"'${topicConfig.output}'"
      )

  private def avroFromScratchProcess(topicConfig: TopicConfig, version: Integer) =
    EspProcessBuilder
      .id("avro-from-scratch-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-avro",
        KafkaAvroFactory.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroFactory.VersionParamName -> versionParam(version)
      )
      .sink(
        "end",
        s"#AVRO.record({first: #input.first, last: #input.last}, #AVRO.latestValueSchema('${topicConfig.output}'))",
        "kafka-avro",
        KafkaAvroFactory.TopicParamName -> s"'${topicConfig.output}'"
      )

  private def avroTypedProcess(topicConfig: TopicConfig, schema: String, fieldSelection: String) =
    EspProcessBuilder
      .id("avro-typed-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-fixed-avro",
        KafkaAvroFactory.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroFactory.SchemaParamName -> s"'$schema'"
      )
      .filter("name-filter", s"#input.$fieldSelection == 'Jan'")
      .sink(
        "end",
        "#input",
        "kafka-avro",
        KafkaAvroFactory.TopicParamName -> s"'${topicConfig.output}'"
      )

  private def versionParam(version: Integer) =
    if (null != version) version.toString else ""

  test("should read json object from kafka, filter and save it to kafka") {
    kafkaClient.sendMessage(JsonInTopic, givenNotMatchingJsonObj)
    kafkaClient.sendMessage(JsonInTopic, givenMatchingJsonObj)

    assertThrows[Exception] {
      run(jsonProcess("#input.nestMap.notExist == ''")) {}
    }

    assertThrows[Exception] {
      run(jsonProcess("#input.list1[0].notExist == ''")) {}
    }

    val validJsonProcess = jsonProcess("#input.first == 'Jan' and " +
      "#input.nestMap.nestedField != 'dummy' and " +
      "#input.list1[0].listField != 'dummy' and " +
      "#input.list2[0] != 15")
    run(validJsonProcess) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(JsonOutTopic).map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(1).toList
      processed.map(parseJson) shouldEqual List(parseJson(givenMatchingJsonObj))
    }
  }

  test("should read avro object from kafka, filter and save it to kafka") {
    val topicConfig = createAndRegisterTopicConfig("read-filter-save", RecordSchemas)

    send(givenNotMatchingAvroObj, topicConfig.input)
    send(givenMatchingAvroObj, topicConfig.input)

    run(avroProcess(topicConfig, 1)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    val topicConfig = createAndRegisterTopicConfig("read-save-scratch", RecordSchemaV1)
    send(givenMatchingAvroObj, topicConfig.input)

    run(avroFromScratchProcess(topicConfig, 1)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read fixed avro typed object from kafka and save it to kafka") {
    val topicConfig = createAndRegisterTopicConfig("fixed", RecordSchemaV1)

    send(givenNotMatchingAvroObj, topicConfig.input)
    send(givenMatchingAvroObj, topicConfig.input)

    assertThrows[Exception] {
      run(avroTypedProcess(topicConfig, RecordSchemaStringV1, "asdf")) {}
    }

    val validAvroTypedProcess = avroTypedProcess(topicConfig, RecordSchemaStringV1, "first")
    run(validAvroTypedProcess) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should merge two streams with union and save it to kafka") {
    val topicIn1: String = "union.json.input1"
    val topicIn2: String = "union.json.input2"
    val topicOut: String = "union.json.output"

    val dataJson1 = """{"data1": "from source1"}"""
    val dataJson2 = """{"data2": "from source2"}"""

    kafkaClient.sendMessage(topicIn1, dataJson1)
    kafkaClient.sendMessage(topicIn2, dataJson2)

    val bizarreBranchName = "?branch .2-"
    val sanitizedBizarreBranchName = "_branch__2_"

    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "kafka-typed-json",
          "topic" -> s"'$topicIn1'",
          "type" -> """{"data1": "String"}""")
        .branchEnd("branch1", "join1"),
      GraphBuilder
        .source("sourceId2", "kafka-typed-json",
          "topic" -> s"'$topicIn2'",
          "type" -> """{"data2": "String"}""")
        .branchEnd(bizarreBranchName, "join1"),
      GraphBuilder
        .branch("join1", "union", Some("outPutVar"),
          List(
            "branch1" -> List("key" -> "'key1'", "value" -> "#input.data1"),
            bizarreBranchName -> List("key" -> "'key2'", "value" -> "#input.data2")
          )
        )
        .filter("always-true-filter", """#outPutVar.key != "not key1 or key2"""")
        .sink("end", "#outPutVar", "kafka-json", "topic" -> s"'$topicOut'")
    ))

    logger.info("Starting union process")
    run(process) {
      logger.info("Waiting for consumer")
      val consumer = kafkaClient.createConsumer().consume(topicOut, 20)
      logger.info("Waiting for messages")
      val processed = consumer.map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(2).toList
      processed.map(parseJson) should contain theSameElementsAs List(
        parseJson(
          s"""{
             |  "key" : "key2",
             |  "$sanitizedBizarreBranchName" : "from source2"
             |}""".stripMargin
        ),
        parseJson(
          """{
            |  "key" : "key1",
            |  "branch1" : "from source1"
            |}""".stripMargin
        )
      )
    }
  }

  test("should read avro object in v1 from kafka and deserialize it to v2, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterTopicConfig("v1.v2.v2", RecordSchemas)
    val result = BestEffortAvroEncoder.encodeRecordOrError(
      Map("first" -> givenMatchingAvroObj.get("first"), "middle" -> null, "last" -> givenMatchingAvroObj.get("last")),
      RecordSchemaV2
    )

    send(givenMatchingAvroObj, topicConfig.input)

    run(avroProcess(topicConfig, 2)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual List(result)
    }
  }

  test("should read avro object in v2 from kafka and deserialize it to v1, filter and save it to kafka in v1") {
    val topicConfig = createAndRegisterTopicConfig("v2.v1.v1", RecordSchemas)
    send(givenMatchingAvroObjV2, topicConfig.input)

    run(avroProcess(topicConfig,1)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should throw exception when record doesn't match to schema") {
    val topicConfig = createAndRegisterTopicConfig("error-record-matching", RecordSchemas)
    val secondTopicConfig = createAndRegisterTopicConfig("error-second-matching", SecondRecordSchemaV1)

    val serializedObj = valueSerializer.serialize(secondTopicConfig.input, givenSecondMatchingAvroObj)
    kafkaClient.sendRawMessage(topicConfig.input, Array.empty, serializedObj)

    assertThrows[Exception] {
      run(avroProcess(topicConfig,1)) {
        val processed = consumeOneAvroMessage(topicConfig.output)
        processed shouldEqual List(givenSecondMatchingAvroObj)
      }
    }
  }

  private def parseJson(str: String) = io.circe.parser.parse(str).right.get

  private def consumeOneAvroMessage(topic: String) = {
    val consumer = kafkaClient.createConsumer()
    consumer.consume(topic).map { record =>
      valueDeserializer.deserialize(topic, record.message())
    }.take(1).toList
  }

  private lazy val creator: GenericConfigCreator = new GenericConfigCreator {
    override protected def createSchemaProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider[GenericData.Record] =
      ConfluentSchemaRegistryProvider[GenericData.Record](
        factory,
        processObjectDependencies,
        useSpecificAvroReader = false,
        formatKey = false
      )
  }

  private val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }

  private def run(process: EspProcess)(action: => Unit): Unit = {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }

  private def send(obj: Any, topic: String) = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj)
  }

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    *
    * @param name
    * @param schemas
    * @return
    */
  private def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = AvroUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = AvroUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      schemaRegistryMockClient.register(inputSubject, schema)
      schemaRegistryMockClient.register(outputSubject, schema)
    })

    topicConfig
  }

  private def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))
}

case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)

object TopicConfig {
  private final val inputPrefix = "test.generic.avro.input."
  private final val outputPrefix = "test.generic.avro.output."

  def apply(testName: String, schemas: List[Schema]): TopicConfig =
    new TopicConfig(inputPrefix + testName, outputPrefix + testName, schemas, isKey = false)
}

object MockSchemaRegistry extends Serializable {

  val RecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV1: Schema = AvroUtils.parseSchema(RecordSchemaStringV1)

  val RecordSchemaStringV2: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV2: Schema = AvroUtils.parseSchema(RecordSchemaStringV2)

  val RecordSchemas = List(RecordSchemaV1, RecordSchemaV2)

  val SecondRecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "firstname", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val SecondRecordSchemaV1: Schema = AvroUtils.parseSchema(SecondRecordSchemaStringV1)

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

  val factory: CachedConfluentSchemaRegistryClientFactory = new CachedConfluentSchemaRegistryClientFactory(DefaultCache.defaultMaximumSize, None, None) {
    override protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
      schemaRegistryMockClient
  }
}
