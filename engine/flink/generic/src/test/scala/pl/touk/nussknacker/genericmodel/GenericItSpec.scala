package pl.touk.nussknacker.genericmodel

import java.nio.charset.StandardCharsets

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class GenericItSpec extends FunSuite with BeforeAndAfterAll with Matchers with KafkaSpec with EitherValues with LazyLogging {

  import KafkaUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  val JsonInTopic: String = "name.json.input"
  val JsonOutTopic: String = "name.json.output"

  private val givenNotMatchingJsonObj =
    """{
      |  "first": "Zenon",
      |  "last": "Nowak"
      |}""".stripMargin
  private val givenMatchingJsonObj =
    """{
      |  "first": "Jan",
      |  "last": "Kowalski"
      |}""".stripMargin

  private val givenNotMatchingAvroObj = {
    val r = new GenericData.Record(RecordSchema)
    r.put("first", "Zenon")
    r.put("last", "Nowak")
    r
  }
  private val givenMatchingAvroObj = {
    val r = new GenericData.Record(RecordSchema)
    r.put("first", "Jan")
    r.put("last", "Kowalski")
    r
  }

  private def jsonProcess(fieldSelection: String) =
    EspProcessBuilder
      .id("json-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-typed-json",
        "topic" -> s"'$JsonInTopic'",
        "type" ->
          """{
            |  "first": "String",
            |  "last": "String"
            |}""".stripMargin
      )
      .filter("name-filter", s"#input.$fieldSelection == 'Jan'")
      .sink("end", "#input","kafka-json", "topic" -> s"'$JsonOutTopic'")

  private val avroProcess =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", "topic" -> s"'$AvroInTopic'")
      .filter("name-filter", "#input.first == 'Jan'")
      .sink("end", "#input","kafka-avro", "topic" -> s"'$AvroOutTopic'")

  private val avroFromScratchProcess =
    EspProcessBuilder
      .id("avro-from-scratch-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", "topic" -> s"'$AvroFromScratchInTopic'")
      .sink("end", s"#AVRO.record({first: #input.first, last: #input.last}, #AVRO.latestValueSchema('$AvroFromScratchOutTopic'))",
        "kafka-avro", "topic" -> s"'$AvroFromScratchOutTopic'")

  private def avroTypedProcess(fieldSelection: String) =
    EspProcessBuilder
      .id("avro-typed-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-typed-avro",
        "topic" -> s"'$AvroTypedInTopic'",
        "schema" -> s"'$RecordSchemaString'"
      )
      .filter("name-filter", s"#input.$fieldSelection == 'Jan'")
      .sink("end", "#input","kafka-avro", "topic" -> s"'$AvroTypedOutTopic'")

  test("should read json object from kafka, filter and save it to kafka") {
    kafkaClient.sendMessage(JsonInTopic, givenNotMatchingJsonObj)
    kafkaClient.sendMessage(JsonInTopic, givenMatchingJsonObj)

    assertThrows[Exception] {
      run(jsonProcess("asdf")){}
    }
    val validJsonProcess = jsonProcess("first")
    run(validJsonProcess) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(JsonOutTopic).map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(1).toList
      processed.map(parseJson) shouldEqual List(parseJson(givenMatchingJsonObj))
    }

  }

  test("should read avro object from kafka, filter and save it to kafka") {
    send(givenNotMatchingAvroObj, AvroInTopic)
    send(givenMatchingAvroObj, AvroInTopic)

    run(avroProcess) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumeOneAvroMessage(AvroOutTopic)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    send(givenMatchingAvroObj, AvroFromScratchInTopic)

    run(avroFromScratchProcess) {
      val processed = consumeOneAvroMessage(AvroFromScratchOutTopic)
      processed shouldEqual List(givenMatchingAvroObj)
    }
  }

  test("should read avro typed object from kafka and save it to kafka") {
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
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "union", Some("outPutVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#input.data1"),
              "branch2" -> List("key" -> "'key2'", "value" -> "#input.data2")
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
        parseJson("""{
                    |  "key" : "key2",
                    |  "branch2" : "from source2"
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

  private lazy val creator = new GenericConfigCreator {
    override protected def createSchemaRegistryClientFactory: SchemaRegistryClientFactory = new SchemaRegistryClientFactory {
      override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
        Registry
    }
  }

  private val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(Registry)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(Registry)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
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


object MockSchemaRegistry {

  val AvroInTopic: String = "name.avro.input"
  val AvroOutTopic: String = "name.avro.output"

  val AvroFromScratchInTopic: String = "name.avro.from-scratch.input"
  val AvroFromScratchOutTopic: String = "name.avro.from-scratch.output"

  val AvroTypedInTopic: String = "name.avro.typed.input"
  val AvroTypedOutTopic: String = "name.avro.typed.output"

  private def parser = new Schema.Parser()

  val RecordSchemaString: String =
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

  val RecordSchema: Schema = parser.parse(RecordSchemaString)

  val Registry: MockSchemaRegistryClient = {
    val mockSchemaRegistry = new MockSchemaRegistryClient
    def registerSchema(topic: String, isKey: Boolean, schema: Schema): Unit = {
      val subject = topic + "-" + (if (isKey) "key" else "value")
      mockSchemaRegistry.register(subject, schema)
    }
    registerSchema(AvroInTopic, isKey = false, RecordSchema)
    registerSchema(AvroOutTopic, isKey = false, RecordSchema)

    registerSchema(AvroFromScratchInTopic, isKey = false, RecordSchema)
    registerSchema(AvroFromScratchOutTopic, isKey = false, RecordSchema)

    registerSchema(AvroTypedInTopic, isKey = false, RecordSchema)
    registerSchema(AvroTypedOutTopic, isKey = false, RecordSchema)
    mockSchemaRegistry
  }

}