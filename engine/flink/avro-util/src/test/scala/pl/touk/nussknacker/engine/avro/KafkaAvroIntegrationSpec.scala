package pl.touk.nussknacker.engine.avro

import org.apache.avro.generic.{GenericContainer, GenericData}
import org.apache.kafka.clients.producer.RecordMetadata
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, SinkOutputParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schema.{PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkSpec
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.Future

class KafkaAvroIntegrationSpec extends KafkaAvroSpec with KafkaAvroSinkSpec {

  import KafkaAvroIntegrationMockSchemaRegistry._
  import KafkaZookeeperUtils._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  protected val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  protected val env = new StreamExecutionEnvironment(stoppableEnv)
  protected var registrar: FlinkStreamingProcessRegistrar = _

  override def schemaRegistryClient: ConfluentSchemaRegistryClient = confluentSchemaRegistryMockClient

  override protected def topics: List[(String, Int)] = List(
    (inputTopic, 2),
    (outputTopic, 2),
  )

  private lazy val creator: TestKafkaAvroProcessConfigCreator = new TestKafkaAvroProcessConfigCreator {
    override protected def createSchemaProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider[GenericData.Record] =
      ConfluentSchemaRegistryProvider[GenericData.Record](
        MockConfluentSchemaRegistryClientFactory,
        processObjectDependencies,
        useSpecificAvroReader = false,
        formatKey = false
      )
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }

  private def run(process: EspProcess)(action: =>Unit): Unit= {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }

  private def send(obj: Any, topic: String): Future[RecordMetadata] = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj)
  }

  private def consumeOneAvroMessage(topic: String) = {
    val consumer = kafkaClient.createConsumer()
    consumer.consume(topic).map { record =>
      valueDeserializer.deserialize(topic, record.message())
    }.take(1).toList
  }

  private def createAvroProcess(source: SourceAvroParam, sink: SinkAvroParam, filterExpression: Option[String] = None) = {
    val builder = EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-avro",
        TopicParamName -> s"'${source.topic}'",
        SchemaVersionParamName -> parseVersion(source.version)
      )

    filterExpression.foreach(expression => {
      builder.filter("filter", s"$expression")
    })

    builder.emptySink(
      "end",
      "kafka-avro",
      TopicParamName -> s"'${sink.topic}'",
      SchemaVersionParamName -> parseVersion(sink.version),
      SinkOutputParamName -> s"${sink.output}"
    )
  }

  private def parseVersion(version: Option[Int]) =
    version.map(v => s"$v").getOrElse("")

  protected def runAndVerifyResult(process: EspProcess, outputTopic: String, expectedRecord: GenericContainer): Unit =
    run(process) {
      val processed = consumeOneAvroMessage(outputTopic)
      processed shouldEqual List(expectedRecord)
    }

  test("should allow to read (sv1) event in sv1 and save (sv1), output event should be in sv1") {
    val sourceParam = SourceAvroParam(inputTopic, Some(1))
    val sinkParam = SinkAvroParam(outputTopic, Some(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    send(PaymentV1.record, inputTopic)
    runAndVerifyResult(process, outputTopic, PaymentV1.record)
  }

  test("should allow to read (sv1) event in sv2 and save (sv1), output event should be in sv1") {
    val sourceParam = SourceAvroParam(inputTopic, Some(1))
    val sinkParam = SinkAvroParam(outputTopic, Some(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    send(PaymentV2.record, inputTopic)
    runAndVerifyResult(process, outputTopic, PaymentV1.record)
  }

  test("should allow to read (sv2) event in sv1 and save (sv2), output event should be in sv2") {
    val sourceParam = SourceAvroParam(inputTopic, Some(2))
    val sinkParam = SinkAvroParam(outputTopic, Some(2), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    send(PaymentV1.record, inputTopic)
    runAndVerifyResult(process, outputTopic, PaymentV2.record)
  }

  test("should allow to read (sv1) event in sv1 and save (sv2), output event should be in sv2") {
    val sourceParam = SourceAvroParam(inputTopic, Some(1))
    val sinkParam = SinkAvroParam(outputTopic, Some(2), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    send(PaymentV1.record, inputTopic)
    runAndVerifyResult(process, outputTopic, PaymentV2.record)
  }

  test("should deserialize event in svP1 to sv2, filter by new field in sv2 (cnt) and serialize to sv1") {
    val sourceParam = SourceAvroParam(inputTopic, Some(2))
    val sinkParam = SinkAvroParam(outputTopic, Some(1), "#input")
    val filerParam = Some("#input.cnt == 0")
    val process = createAvroProcess(sourceParam, sinkParam, filerParam)

    send(PaymentV1.record, inputTopic)
    runAndVerifyResult(process, outputTopic, PaymentV1.record)
  }
}

case class SourceAvroParam(topic: String, version: Option[Int])
case class SinkAvroParam(topic: String, version: Option[Int], output: String)

object KafkaAvroIntegrationMockSchemaRegistry extends TestMockSchemaRegistry {

  val inputTopic: String = "input"
  val outputTopic: String = "output"

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(inputTopic, PaymentV1.schema, 1, isKey = false)
    .register(inputTopic, PaymentV2.schema, 2, isKey = false)
    .register(outputTopic, PaymentV1.schema, 1, isKey = false)
    .register(outputTopic, PaymentV2.schema, 2, isKey = false)
    .build

  object MockConfluentSchemaRegistryClientFactory extends ConfluentSchemaRegistryClientFactory with Serializable {
    override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
      confluentSchemaRegistryMockClient
  }
}
