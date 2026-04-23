package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, KafkaContainer}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.scalatest.{BeforeAndAfterAll, TestSuite, TryValues}
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KeyMessage}
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils._
import pl.touk.nussknacker.test.containers.WithDockerContainers
import pl.touk.nussknacker.test.containers.kafka.{WithKafkaContainer, WithSchemaRegistryContainer}

import java.io.File
import java.util.concurrent.TimeoutException
import scala.util.Try

// Created base class and used one test class for each test case because runtime has fixed one Nussknacker scenario
trait BaseNuKafkaRuntimeDockerTest
    extends ForAllTestContainer
    with WithDockerContainers
    with WithKafkaContainer
    with WithSchemaRegistryContainer
    with BeforeAndAfterAll
    with NuKafkaRuntimeTestMixin
    with TryValues {
  self: TestSuite with LazyLogging =>

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  override protected val kafkaAutoCreateTopics: Boolean = false

  protected lazy val schemaRegistryClient = new CachedSchemaRegistryClient(hostSchemaRegistryUrl, 10)

  protected var fixture: NuKafkaRuntimeTestTestCaseFixture = _

  protected var runtimeContainer: GenericContainer = _

  protected def startRuntimeContainer(
      scenarioFile: File,
      checkReady: Boolean = true,
      additionalEnvs: Map[String, String] = Map.empty
  ): Unit = {
    val kafkaEnvs = Map(
      "KAFKA_ADDRESS"           -> containerKafkaAddress,
      "KAFKA_AUTO_OFFSET_RESET" -> "earliest",
      "KAFKA_ERROR_TOPIC"       -> fixture.errorTopic,
      "SCHEMA_REGISTRY_URL"     -> containerSchemaRegistryUrl
    )
    runtimeContainer = NuRuntimeDockerTestUtils.startRuntimeContainer(
      scenarioFile,
      logger.underlying,
      Some(network),
      checkReady,
      kafkaEnvs ++ additionalEnvs
    )
  }

  override protected def kafkaBoostrapServer: String = kafkaContainer.bootstrapServers

  protected lazy val kafkaClient = new KafkaClient(kafkaBoostrapServer, suiteName)

  protected def hostRuntimeApiUrl: String =
    s"http://${runtimeContainer.host}:${runtimeContainer.mappedPort(runtimeApiPort)}"

  protected def consumeFirstError: Option[KeyMessage[String, KafkaExceptionInfo]] = {
    Try(kafkaClient.createConsumer().consumeWithJson[KafkaExceptionInfo](fixture.errorTopic).take(1).headOption)
      .recover { case _: TimeoutException =>
        None
      }
  }.success.value

  override protected def afterAll(): Unit = {
    kafkaClient.shutdown()
    super.afterAll()
  }

}
