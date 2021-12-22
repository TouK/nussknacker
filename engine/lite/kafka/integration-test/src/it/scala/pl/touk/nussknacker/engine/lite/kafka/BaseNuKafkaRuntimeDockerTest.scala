package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, KafkaContainer, SingleContainer}
import org.testcontainers.containers.{GenericContainer => JavaGenericContainer}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{TestSuite, TryValues}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.{BindMode, Network}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KeyMessage}
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.version.BuildInfo

import java.io.File
import java.util.concurrent.TimeoutException
import scala.util.Try

// Created base class and used one test class for each test case because runtime has fixed one Nussknacker scenario
trait BaseNuKafkaRuntimeDockerTest extends ForAllTestContainer with NuKafkaRuntimeTestMixin with TryValues { self: TestSuite with LazyLogging =>

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

  protected def prepareRuntimeContainer(scenarioFile: File, additionalEnvs: Map[String, String] = Map.empty): GenericContainer = {
    val container = GenericContainer(
      liteKafkaRuntimeDockerName,
      env = Map(
        "KAFKA_ADDRESS" -> dockerNetworkKafkaBoostrapServer,
        "KAFKA_ERROR_TOPIC" -> fixture.errorTopic
      ) ++ additionalEnvs)
    container.underlyingUnsafeContainer.withNetwork(network)
    container.underlyingUnsafeContainer.withFileSystemBind(scenarioFile.toString, "/opt/nussknacker/conf/scenario.json", BindMode.READ_ONLY)
    container.start()
    container.underlyingUnsafeContainer.followOutput(new Slf4jLogConsumer(logger.underlying))
    container
  }

  override protected def kafkaBoostrapServer: String = kafkaContainer.bootstrapServers

  protected def dockerNetworkKafkaBoostrapServer: String = s"$kafkaHostname:9092"

  protected lazy val kafkaClient = new KafkaClient(kafkaBoostrapServer, suiteName)

  protected def consumeFirstError: Option[KeyMessage[String, KafkaExceptionInfo]] = {
    Try(errorConsumer(secondsToWait = 1).take(1).headOption).recover {
      case _: TimeoutException => None
    }
  }.success.value

  protected def errorConsumer(secondsToWait: Int): Stream[KeyMessage[String, KafkaExceptionInfo]] = kafkaClient.createConsumer()
    .consume(fixture.errorTopic, secondsToWait = secondsToWait)
    .map(km => KeyMessage(new String(km.key()), CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](km.message()), km.timestamp))

}
