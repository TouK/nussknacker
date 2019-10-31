package pl.touk.nussknacker.genericmodel

import java.nio.charset.StandardCharsets

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkStreamingProcessCompiler
import pl.touk.nussknacker.engine.spel

class GenericItSpec extends FunSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec with EitherValues {

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

  it("should read json object from kafka, filter and save it to kafka") {
    kafkaClient.sendMessage(JsonInTopic, givenNotMatchingJsonObj)
    kafkaClient.sendMessage(JsonInTopic, givenMatchingJsonObj)

    assertThrows[Exception] {
      register(jsonProcess("asdf"))
    }
    val validJsonProcess = jsonProcess("first")
    register(validJsonProcess)
    env.execute(validJsonProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume(JsonOutTopic).map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(1).toList
    processed.map(parseJson) shouldEqual List(parseJson(givenMatchingJsonObj))
  }

  it("should read avro object from kafka, filter and save it to kafka") {
    send(givenNotMatchingAvroObj, AvroInTopic)
    send(givenMatchingAvroObj, AvroInTopic)

    register(avroProcess)
    env.execute(avroProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumeOneAvroMessage(AvroOutTopic)
    processed shouldEqual List(givenMatchingAvroObj)
  }

  it("should read avro object from kafka and save new one created from scratch") {
    send(givenMatchingAvroObj, AvroFromScratchInTopic)

    register(avroFromScratchProcess)
    env.execute(avroFromScratchProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumeOneAvroMessage(AvroFromScratchOutTopic)
    processed shouldEqual List(givenMatchingAvroObj)
  }

  it("should read avro typed object from kafka and save it to kafka") {
    send(givenNotMatchingAvroObj, AvroTypedInTopic)
    send(givenMatchingAvroObj, AvroTypedInTopic)

    assertThrows[Exception] {
      register(avroTypedProcess("asdf"))
    }
    val validAvroTypedProcess = avroTypedProcess("first")
    register(validAvroTypedProcess)
    env.execute(validAvroTypedProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumeOneAvroMessage(AvroTypedOutTopic)
    processed shouldEqual List(givenMatchingAvroObj)
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

  private val stoppableEnv = new StoppableExecutionEnvironment(FlinkTestConfiguration.configuration)
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(Registry)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(Registry)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    env.getConfig.disableSysoutLogging()
    registrar = new FlinkStreamingProcessCompiler(creator, config).createFlinkProcessRegistrar()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stoppableEnv.stop()
  }

  private def register(process: EspProcess):Unit= {
    registrar.register(env, process, ProcessVersion.empty)
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