package pl.touk.nussknacker.engine.management.streaming

import com.dimafeng.testcontainers.LazyContainer
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.StrictLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.apache.avro.{Schema, SchemaBuilder}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DMMakeScenarioSavepointCommand
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.docker.WithSchemaRegistryContainer
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.{KafkaConfigProperties, ValidatedValuesDetailedMessage}

import java.net.URI
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits._

class OptimizedGenericRecordSerializationSpec
    extends AnyFunSuiteLike
    with Matchers
    with FlinkKafkaDockerSpec
    with WithSchemaRegistryContainer
    with ValidatedValuesDetailedMessage
    with StrictLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  override protected val useMiniClusterForDeployment: Boolean = false

  override def containers: List[LazyContainer[_]] = super.containers :+ (schemaRegistryContainer: LazyContainer[_])

  override protected def modelClassPath: List[String] = TestModelClassPaths.scalaClasspath

  override def resolveProcessingTypeConfig(config: Config): Config = super
    .resolveProcessingTypeConfig(config)
    // disable state verification job on DM - it needs to access Schema Registry,
    // but its configured URL is accessible only in containers
    .withValue("deploymentConfig.scenarioStateVerification.enabled", fromAnyRef(false))
    .withValue(
      KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "schema.registry.url"),
      fromAnyRef(containerSchemaRegistryUrl)
    )

  private lazy val schemaRegistryClient = new CachedSchemaRegistryClient(hostSchemaRegistryUrl, 10)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  test("save state when redeploying when optimized Avro serialization is used") {
    val processName = ProcessName("redeploy-kafka")
    val inputTopic  = s"input-${processName.value}"
    val outputTopic = s"output-${processName.value}"
    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)

    val inputEncoder = new TestValueEncoder(
      inputTopic,
      SchemaBuilder.record("schema").fields().requiredString("str").endRecord()
    )

    val testProcess = ScenarioBuilder
      .streaming(processName.value)
      .source(
        "src",
        "kafka",
        "Topic"          -> s"'$inputTopic'".spel,
        "Schema version" -> "'latest'".spel
      )
      .customNode("stateful", "stateVar", "stateful", "groupBy" -> "''".spel)
      .emptySink(
        "end",
        "kafka",
        "Topic"        -> s"'$outputTopic'".spel,
        "Value"        -> "#stateVar.toString".spel,
        "Content type" -> "'PLAIN'".spel
      )

    kafkaClient.sendRawMessage(inputTopic, inputEncoder.encodeAvroMessage(Map("str" -> "message1")))

    val processVersion = ProcessVersion.empty.copy(processName = processName)
    deployProcessAndWaitIfRunning(testProcess, processVersion)
    try {
      // wait until scenario is ready
      val readyRecord = kafkaClient.createConsumer().consumeWithJson[String](outputTopic).take(1).head
      readyRecord.msg shouldBe """[{"str": "message1"}]"""

      logger.info("Deploying new scenario version")
      deployProcessAndWaitIfRunning(testProcess, processVersion.copy(versionId = processVersion.versionId.increase))

      kafkaClient.sendRawMessage(inputTopic, inputEncoder.encodeAvroMessage(Map("str" -> "message2")))

      val messages = kafkaClient.createConsumer().consumeWithJson[String](outputTopic).take(2).toList
      messages.map(_.msg) shouldBe List("""[{"str": "message1"}]""", """[{"str": "message1"}, {"str": "message2"}]""")

      val avroSavepointBind = savepointBind.subdirectory("avro")
      val avroSavepointContainerUri = deploymentManager
        .processCommand(
          DMMakeScenarioSavepointCommand(
            testProcess.name,
            savepointDir = Some(avroSavepointBind.containerPath)
          )
        )
        .map(_.path)
        .map(URI.create)
        .futureValue

      val savepointName = avroSavepointContainerUri.getPath.split('/').last
      val testSavepointPath = avroSavepointBind.subdirectory(savepointName).hostPath
      logger.info(s"Testing savepoint from $testSavepointPath")

      Files.exists(avroSavepointBind.hostPath) shouldBe true
    } finally {
      cancelProcess(processName)
    }
  }

  private class TestValueEncoder(topic: String, schema: Schema) {
    private val avroEncoder        = ToAvroSchemaBasedEncoder(ValidationMode.strict)
    private val subject: String    = ConfluentUtils.topicSubject(new UnspecializedTopicName(topic), isKey = false)
    private val schemaId: SchemaId = SchemaId.fromInt(schemaRegistryClient.register(subject, new AvroSchema(schema)))

    def encodeAvroMessage(fields: Map[String, _]): Array[Byte] = {
      val record = avroEncoder.encodeRecord(fields, schema).validValue
      ConfluentUtils.serializeContainerToBytesArray(record, schemaId)
    }

  }

}
