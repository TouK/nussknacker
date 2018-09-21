package pl.touk.nussknacker.genericmodel

import java.nio.charset.StandardCharsets

import argonaut.Parse
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
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.spel

class GenericItSpec extends FunSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec with EitherValues {

  import KafkaUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  val JsonInTopic: String = "name.json.input"
  val JsonOutTopic: String = "name.json.output"

  private val jsonProcess =
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
      .filter("name-filter", "#input.first == 'Jan'")
      .sink("end", "#input","kafka-json", "topic" -> s"'$JsonOutTopic'")

  private val avroProcess =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", "topic" -> s"'$AvroInTopic'")
      .filter("name-filter", "#input.first == 'Jan'")
      // TODO: add support for building new records from scratch
      .sink("end", "#input","kafka-avro", "topic" -> s"'$AvroOutTopic'")

  it("should read json object from kafka, filter and save it to kafka") {
    val givenNotMatchingObj =
      """{
        |  "first": "Zenon",
        |  "last": "Nowak"
        |}""".stripMargin
    kafkaClient.sendMessage(JsonInTopic, givenNotMatchingObj)
    val givenMatchingObj =
      """{
        |  "first": "Jan",
        |  "last": "Kowalski"
        |}""".stripMargin
    kafkaClient.sendMessage(JsonInTopic, givenMatchingObj)

    register(jsonProcess)
    env.execute(jsonProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume(JsonOutTopic).map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(1).toList
    processed.map(parseJson) shouldEqual List(parseJson(givenMatchingObj))
  }

  private def parseJson(str: String) =
    Parse.parse(str).right.value


  it("should read avro object from kafka, filter and save it to kafka") {
    val givenNotMatchingObj = {
      val r = new GenericData.Record(RecordSchema)
      r.put("first", "Zenon")
      r.put("last", "Nowak")
      r
    }
    send(givenNotMatchingObj, AvroInTopic)
    val givenMatchingObj = {
      val r = new GenericData.Record(RecordSchema)
      r.put("first", "Jan")
      r.put("last", "Kowalski")
      r
    }
    send(givenMatchingObj, AvroInTopic)

    register(avroProcess)
    env.execute(avroProcess.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume(AvroOutTopic).map { record =>
      valueDeserializer.deserialize(AvroOutTopic, record.message())
    }.take(1).toList
    processed shouldEqual List(givenMatchingObj)
  }

  private lazy val creator = new GenericConfigCreator {
    override protected def createSchemaRegistryClientFactory: SchemaRegistryClientFactory = new SchemaRegistryClientFactory {
      override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
        Registry
    }
  }

  private val stoppableEnv = new StoppableExecutionEnvironment(FlinkTestConfiguration.configuration)
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(Registry)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(Registry)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    env.getConfig.disableSysoutLogging()
    registrar = new StandardFlinkProcessCompiler(creator, config).createFlinkProcessRegistrar()
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

  private def parser = new Schema.Parser()

  val RecordSchema: Schema = parser.parse(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin)


  val Registry: MockSchemaRegistryClient = {
    val mockSchemaRegistry = new MockSchemaRegistryClient
    def registerSchema(topic: String, isKey: Boolean, schema: Schema): Unit = {
      val subject = topic + "-" + (if (isKey) "key" else "value")
      mockSchemaRegistry.register(subject, schema)
    }
    registerSchema(AvroInTopic, isKey = false, RecordSchema)
    registerSchema(AvroOutTopic, isKey = false, RecordSchema)
    mockSchemaRegistry
  }

}