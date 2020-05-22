package pl.touk.nussknacker.genericmodel

import java.nio.charset.StandardCharsets

import cats.data.NonEmptyList
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
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
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class GenericItSpec extends FunSuite with BeforeAndAfterAll with Matchers with KafkaSpec with EitherValues with LazyLogging {

  import KafkaAvroFactory._
  import KafkaZookeeperUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  lazy val mockProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

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

  private val givenNotMatchingAvroObj =
    BestEffortAvroEncoder.encodeRecordOrError(Map("first" -> "Zenon", "last" -> "Nowak"), FullName.schema)

  private val givenMatchingAvroObj =
    BestEffortAvroEncoder.encodeRecordOrError(Map("first" -> "Jan", "last" -> "Kowalski"), FullName.schema)

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
      .sink("end", "#input","kafka-json", "topic" -> s"'$JsonOutTopic'")

  private def avroProcess(version: Integer) =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", TopicParamName -> s"'$AvroInTopic'", SchemaVersionParamName -> versionParam(version))
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink("end", "kafka-avro",
        TopicParamName -> s"'$AvroOutTopic'",
        SinkOutputParamName -> "#input",
        SchemaVersionParamName -> ""
      )

  private def avroFromScratchProcess(version: Integer) =
    EspProcessBuilder
      .id("avro-from-scratch-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", TopicParamName -> s"'$AvroFromScratchInTopic'", SchemaVersionParamName -> versionParam(version))
      .emptySink("end", "kafka-avro",
        TopicParamName-> s"'$AvroFromScratchOutTopic'",
        SinkOutputParamName -> s"#AVRO.record({first: #input.first, last: #input.last}, #AVRO.latestValueSchema('$AvroFromScratchOutTopic'))",
        SchemaVersionParamName -> ""
      )

  private def avroTypedProcess(fieldSelection: String) =
    EspProcessBuilder
      .id("avro-typed-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-fixed-avro",
        TopicParamName -> s"'$AvroTypedInTopic'",
        FixedSchemaParamName -> s"'${FullName.stringSchema}'"
      )
      .filter("name-filter", s"#input.$fieldSelection == 'Jan'")
      .emptySink("end", "kafka-avro",
        TopicParamName -> s"'$AvroTypedOutTopic'",
        SinkOutputParamName -> "#input",
        SchemaVersionParamName -> ""
      )

  private def versionParam(version: Integer) =
    if (null != version) version.toString else ""

  test("should read json object from kafka, filter and save it to kafka") {
    kafkaClient.sendMessage(JsonInTopic, givenNotMatchingJsonObj)
    kafkaClient.sendMessage(JsonInTopic, givenMatchingJsonObj)

    assertThrows[Exception] {
      run(jsonProcess("#input.nestMap.notExist == ''")){}
    }

    assertThrows[Exception] {
      run(jsonProcess("#input.list1[0].notExist == ''")){}
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
    send(givenNotMatchingAvroObj, AvroInTopic)
    send(givenMatchingAvroObj, AvroInTopic)

    run(avroProcess(1)) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumeOneAvroMessage(AvroOutTopic)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    send(givenMatchingAvroObj, AvroFromScratchInTopic)

    run(avroFromScratchProcess(1)) {
      val processed = consumeOneAvroMessage(AvroFromScratchOutTopic)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read fixed avro typed object from kafka and save it to kafka") {
    send(givenNotMatchingAvroObj, AvroTypedInTopic)
    send(givenMatchingAvroObj, AvroTypedInTopic)

    assertThrows[Exception] {
      run(avroTypedProcess("asdf")){}
    }

    val validAvroTypedProcess = avroTypedProcess("first")

    run(validAvroTypedProcess) {
      val processed = consumeOneAvroMessage(AvroTypedOutTopic)
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
            "type" -> """{"data1": "String"}""" )
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
          .sink("end", "#outPutVar","kafka-json", "topic" -> s"'$topicOut'")
      ))

    logger.info("Starting union process")
    run(process) {
      logger.info("Waiting for consumer")
      val consumer = kafkaClient.createConsumer().consume(topicOut, 20)
      logger.info("Waiting for messages")
      val processed = consumer.map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(2).toList
      processed.map(parseJson) should contain theSameElementsAs List(
        parseJson(s"""{
                    |  "key" : "key2",
                    |  "$sanitizedBizarreBranchName" : "from source2"
                    |}""".stripMargin
        ),
        parseJson("""{
                    |  "key" : "key1",
                    |  "branch1" : "from source1"
                    |}""".stripMargin
        )
      )
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
        MockConfluentSchemaRegistryClientFactory,
        processObjectDependencies,
        useSpecificAvroReader = false,
        formatKey = false
      )
  }

  private val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(confluentSchemaRegistryMockClient.client)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(confluentSchemaRegistryMockClient.client)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }

  private def run(process: EspProcess)(action: =>Unit):Unit= {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }

  private def send(obj: Any, topic: String) = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj)
  }
}

object MockSchemaRegistry extends Serializable {

  val AvroInTopic: String = "name.avro.input"
  val AvroOutTopic: String = "name.avro.output"

  val AvroFromScratchInTopic: String = "name.avro.from-scratch.input"
  val AvroFromScratchOutTopic: String = "name.avro.from-scratch.output"

  val AvroTypedInTopic: String = "name.avro.typed.input"
  val AvroTypedOutTopic: String = "name.avro.typed.output"

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(AvroInTopic, FullName.schema, 1, isKey = false)
    .register(AvroOutTopic, FullName.schema, 1, isKey = false)
    .register(AvroFromScratchInTopic, FullName.schema, 1, isKey = false)
    .register(AvroFromScratchOutTopic, FullName.schema, 1, isKey = false)
    .register(AvroTypedInTopic, FullName.schema, 1, isKey = false)
    .register(AvroTypedOutTopic, FullName.schema, 1, isKey = false)
    .build

  object MockConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory with Serializable {
    override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
      confluentSchemaRegistryMockClient
  }
}

object FullName {
  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.genericmodel.schema",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val schema: Schema = AvroUtils.parseSchema(stringSchema)
}
