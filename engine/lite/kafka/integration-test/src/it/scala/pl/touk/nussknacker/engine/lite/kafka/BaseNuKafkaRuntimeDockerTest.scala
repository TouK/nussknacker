package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, KafkaContainer, SingleContainer}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.scalatest.{BeforeAndAfterAll, TestSuite, TryValues}
import org.testcontainers.containers.{Network, GenericContainer => JavaGenericContainer}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KeyMessage}
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils._

import java.io.File
import java.util.concurrent.TimeoutException
import scala.util.Try

// Created base class and used one test class for each test case because runtime has fixed one Nussknacker scenario
trait BaseNuKafkaRuntimeDockerTest extends ForAllTestContainer with BeforeAndAfterAll with NuKafkaRuntimeTestMixin with TryValues {
  self: TestSuite with LazyLogging =>

  private val schemaRegistryHostname = "schemaregistry"
  private val schemaRegistryPort = 8081

  private val network = Network.newNetwork
  private val kafkaHostname = "kafka"

  protected val schemaRegistryContainer = {
    val container = GenericContainer(
      "confluentinc/cp-schema-registry:7.2.1",
      exposedPorts = Seq(schemaRegistryPort),
      env = Map(
        "SCHEMA_REGISTRY_HOST_NAME" -> schemaRegistryHostname,
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> dockerNetworkKafkaBoostrapServer)
    )
    configureNetwork(container, schemaRegistryHostname)
    container
  }

  protected def mappedSchemaRegistryAddress = s"http://localhost:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"
  protected def dockerNetworkSchemaRegistryAddress = s"http://$schemaRegistryHostname:$schemaRegistryPort"

  protected lazy val schemaRegistryClient = new CachedSchemaRegistryClient(mappedSchemaRegistryAddress, 10)

  protected val kafkaContainer: KafkaContainer = {
    val container = KafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "FALSE"))
    configureNetwork(container, kafkaHostname)
    container
  }

  protected def configureNetwork(container: SingleContainer[_ <: JavaGenericContainer[_]], networkAlias: String): Unit = {
    container.underlyingUnsafeContainer.withNetwork(network)
    container.underlyingUnsafeContainer.withNetworkAliases(networkAlias)
  }

  protected var fixture: NuKafkaRuntimeTestTestCaseFixture = _

  protected var runtimeContainer: GenericContainer = _

  protected def startRuntimeContainer(scenarioFile: File, checkReady: Boolean = true, additionalEnvs: Map[String, String] = Map.empty): Unit = {
    val kafkaEnvs = Map(
      "KAFKA_ADDRESS" -> dockerNetworkKafkaBoostrapServer,
      "KAFKA_AUTO_OFFSET_RESET" -> "earliest",
      "CONFIG_FORCE_kafka_lowLevelComponentsEnabled" -> "true",
      "KAFKA_ERROR_TOPIC" -> fixture.errorTopic,
      "SCHEMA_REGISTRY_URL" -> dockerNetworkSchemaRegistryAddress)
    runtimeContainer = NuRuntimeDockerTestUtils.startRuntimeContainer(scenarioFile, logger.underlying, Some(network), checkReady, kafkaEnvs ++ additionalEnvs)
  }

  override protected def kafkaBoostrapServer: String = kafkaContainer.bootstrapServers

  protected def dockerNetworkKafkaBoostrapServer: String = s"$kafkaHostname:9092"

  protected lazy val kafkaClient = new KafkaClient(kafkaBoostrapServer, suiteName)

  protected def mappedRuntimeApiPort: Int = runtimeContainer.mappedPort(runtimeApiPort)

  protected def consumeFirstError: Option[KeyMessage[String, KafkaExceptionInfo]] = {
    Try(errorConsumer(secondsToWait = 1).take(1).headOption).recover {
      case _: TimeoutException => None
    }
  }.success.value

  protected def errorConsumer(secondsToWait: Int): Stream[KeyMessage[String, KafkaExceptionInfo]] = kafkaClient.createConsumer()
    .consume(fixture.errorTopic, secondsToWait = secondsToWait)
    .map(km => KeyMessage(new String(km.key()), CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](km.message()), km.timestamp))

  override protected def afterAll(): Unit = {
    kafkaClient.shutdown()
    super.afterAll()
  }

}