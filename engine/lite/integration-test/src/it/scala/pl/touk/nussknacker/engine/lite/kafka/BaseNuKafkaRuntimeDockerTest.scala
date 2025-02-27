package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, KafkaContainer, SingleContainer}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.scalatest.{BeforeAndAfterAll, TestSuite, TryValues}
import org.testcontainers.containers.{GenericContainer => JavaGenericContainer, Network}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KeyMessage}
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeDockerTestUtils._

import java.io.File
import java.util.concurrent.TimeoutException
import scala.util.Try

// Created base class and used one test class for each test case because runtime has fixed one Nussknacker scenario
trait BaseNuKafkaRuntimeDockerTest
    extends ForAllTestContainer
    with BeforeAndAfterAll
    with NuKafkaRuntimeTestMixin
    with TryValues {
  self: TestSuite with LazyLogging =>

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  private val schemaRegistryHostname = "schemaregistry"
  private val schemaRegistryPort     = 8081

  private val network       = Network.newNetwork
  private val kafkaHostname = "kafka"

  protected val schemaRegistryContainer = {
    val container = GenericContainer(
      "confluentinc/cp-schema-registry:7.5.3",
      exposedPorts = Seq(schemaRegistryPort),
      env = Map(
        "SCHEMA_REGISTRY_HOST_NAME"                    -> schemaRegistryHostname,
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> dockerNetworkKafkaBoostrapServer
      )
    )
    configureNetwork(container, schemaRegistryHostname)
    container
  }

  protected def mappedSchemaRegistryAddress =
    s"http://localhost:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"
  protected def dockerNetworkSchemaRegistryAddress = s"http://$schemaRegistryHostname:$schemaRegistryPort"

  protected lazy val schemaRegistryClient = new CachedSchemaRegistryClient(mappedSchemaRegistryAddress, 10)

  protected val kafkaContainer: KafkaContainer = {
    val container = KafkaContainer(DockerImageName.parse(s"${KafkaContainer.defaultImage}:7.5.3"))
      .configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "FALSE"))
    configureNetwork(container, kafkaHostname)
    container
  }

  protected def configureNetwork(
      container: SingleContainer[_ <: JavaGenericContainer[_]],
      networkAlias: String
  ): Unit = {
    container.underlyingUnsafeContainer.withNetwork(network)
    container.underlyingUnsafeContainer.withNetworkAliases(networkAlias)
  }

  protected var fixture: NuKafkaRuntimeTestTestCaseFixture = _

  protected var runtimeContainer: GenericContainer = _

  protected def startRuntimeContainer(
      scenarioFile: File,
      checkReady: Boolean = true,
      additionalEnvs: Map[String, String] = Map.empty
  ): Unit = {
    val kafkaEnvs = Map(
      "KAFKA_ADDRESS"           -> dockerNetworkKafkaBoostrapServer,
      "KAFKA_AUTO_OFFSET_RESET" -> "earliest",
      "KAFKA_ERROR_TOPIC"       -> fixture.errorTopic,
      "SCHEMA_REGISTRY_URL"     -> dockerNetworkSchemaRegistryAddress
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

  protected def dockerNetworkKafkaBoostrapServer: String = s"$kafkaHostname:9092"

  protected lazy val kafkaClient = new KafkaClient(kafkaBoostrapServer, suiteName)

  protected def mappedRuntimeApiPort: Int = runtimeContainer.mappedPort(runtimeApiPort)

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
