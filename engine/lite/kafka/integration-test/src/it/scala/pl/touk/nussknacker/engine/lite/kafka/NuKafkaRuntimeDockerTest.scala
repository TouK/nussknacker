package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FunSuite, Matchers}
import org.testcontainers.containers.{BindMode, Network}
import org.testcontainers.containers.output.Slf4jLogConsumer
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils.richConsumer

class NuKafkaRuntimeDockerTest extends FunSuite with ForAllTestContainer with NuKafkaRuntimeTestMixin with Matchers with PatientScalaFutures with LazyLogging {

  private val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)
  private val liteKafkaRuntimeDockerName = s"touk/nussknacker-lite-kafka-runtime:$dockerTag"

  private val network = Network.newNetwork

  private val kafkaContainer = {
    val container = KafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "FALSE"))
    container.configure(_.withNetwork(network).withNetworkAliases("kafka"))
    container
  }

  private var fixture: NuKafkaRuntimeTestTestCaseFixture = _

  override protected def kafkaBoostrapServer: String = kafkaContainer.bootstrapServers

  override val container: Container = {
    kafkaContainer.start()
    fixture = prepareTestCaseFixture("json-ping-pong", NuKafkaRuntimeTestSamples.jsonPingPongScenario)
    val runtimeContainer = {
      val container = GenericContainer(
        liteKafkaRuntimeDockerName,
        env = Map("KAFKA_ADDRESS" -> "kafka:9092"),
      )
      container.underlyingUnsafeContainer.withNetwork(network)
      container.underlyingUnsafeContainer.withFileSystemBind(fixture.scenarioFile.toString, "/opt/nussknacker/conf/scenario.json", BindMode.READ_ONLY)
      container.start()
      container.underlyingUnsafeContainer.followOutput(new Slf4jLogConsumer(logger.underlying))
      container
    }
    MultipleContainers(kafkaContainer, runtimeContainer)
  }

  test("container should start") {
    val kafkaClient = new KafkaClient(kafkaBoostrapServer, "not-used-zk-address", suiteName)
    kafkaClient.sendMessage(fixture.inputTopic, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
    val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1).map(rec => new String(rec.message())).toList
    messages shouldBe List(NuKafkaRuntimeTestSamples.jsonPingMessage)
  }

}