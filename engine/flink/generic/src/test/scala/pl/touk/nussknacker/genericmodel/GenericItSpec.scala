package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.spel

class GenericItSpec extends FunSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec {

  import KafkaUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  private val process =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-avro", "topic" -> s"'$InTopic'")
      .filter("name-filter", "#input.first == 'Jan'")
      // TODO: add support for building new records from scratch
      .sink("end", "#input","kafka-avro", "topic" -> s"'$OutTopic'")


  it("should read avro object from kafka, filter and save it to kafka") {
    val givenMatchingObj = {
      val r = new GenericData.Record(RecordSchema)
      r.put("first", "Jan")
      r.put("last", "Kowalski")
      r
    }
    send(givenMatchingObj, InTopic)
    val givenNotMatchingObj = {
      val r = new GenericData.Record(RecordSchema)
      r.put("first", "Zenon")
      r.put("last", "Nowak")
      r
    }
    send(givenNotMatchingObj, InTopic)

    register(process)

    env.execute(process.id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume(OutTopic).map { record =>
      valueDeserializer.deserialize(OutTopic, record.message())
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

  val InTopic: String = "name.input"
  val OutTopic: String = "name.output"

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
    registerSchema(InTopic, isKey = false, RecordSchema)
    registerSchema(OutTopic, isKey = false, RecordSchema)
    mockSchemaRegistry
  }

}