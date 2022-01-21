package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, KafkaContainer, SingleContainer}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, TestSuite, TryValues}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.{Wait, WaitStrategy, WaitStrategyTarget}
import org.testcontainers.containers.{BindMode, Network, GenericContainer => JavaGenericContainer}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KeyMessage}
import pl.touk.nussknacker.engine.version.BuildInfo

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeoutException
import scala.util.Try

// Created base class and used one test class for each test case because runtime has fixed one Nussknacker scenario
trait BaseNuKafkaRuntimeDockerTest extends ForAllTestContainer with BeforeAndAfterAll with NuKafkaRuntimeTestMixin with TryValues { self: TestSuite with LazyLogging =>

  private val kafkaHostname = "kafka"

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)
  private val liteKafkaRuntimeDockerName = s"touk/nussknacker-lite-kafka-runtime:$dockerTag"

  private val network = Network.newNetwork

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

  private val runtimeManagementPort = 8558

  protected def startRuntimeContainer(scenarioFile: File, checkReady: Boolean = true, additionalEnvs: Map[String, String] = Map.empty): Unit = {
    runtimeContainer = GenericContainer(
      liteKafkaRuntimeDockerName,
      exposedPorts = Seq(runtimeManagementPort),
      env = Map(
        "KAFKA_ADDRESS" -> dockerNetworkKafkaBoostrapServer,
        "KAFKA_ERROR_TOPIC" -> fixture.errorTopic
      ) ++ sys.env.get("NU_LOG_LEVEL").map("NU_LOG_LEVEL" -> _) ++ additionalEnvs)
    runtimeContainer.underlyingUnsafeContainer.withNetwork(network)
    runtimeContainer.underlyingUnsafeContainer.withFileSystemBind(scenarioFile.toString, "/opt/nussknacker/conf/scenario.json", BindMode.READ_ONLY)
    runtimeContainer.underlyingUnsafeContainer.withFileSystemBind(deploymentDataFile.toString, "/opt/nussknacker/conf/deploymentData.conf", BindMode.READ_ONLY)
    val waitStrategy = if (checkReady) Wait.forHttp("/ready") else DumbWaitStrategy
    runtimeContainer.underlyingUnsafeContainer.setWaitStrategy(waitStrategy)
    runtimeContainer.start()
    runtimeContainer.underlyingUnsafeContainer.followOutput(new Slf4jLogConsumer(logger.underlying))
  }

  override protected def kafkaBoostrapServer: String = kafkaContainer.bootstrapServers

  protected def dockerNetworkKafkaBoostrapServer: String = s"$kafkaHostname:9092"

  protected lazy val kafkaClient = new KafkaClient(kafkaBoostrapServer, suiteName)

  protected def runtimeManagementMappedPort: Int = runtimeContainer.mappedPort(runtimeManagementPort)

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

  object DumbWaitStrategy extends WaitStrategy {
    override def waitUntilReady(waitStrategyTarget: WaitStrategyTarget): Unit = {}
    override def withStartupTimeout(startupTimeout: Duration): WaitStrategy = this
  }

}
