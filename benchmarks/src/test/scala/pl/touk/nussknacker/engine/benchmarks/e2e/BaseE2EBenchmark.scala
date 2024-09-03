package pl.touk.nussknacker.engine.benchmarks.e2e

import better.files.Resource
import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import pl.touk.nussknacker.engine.benchmarks.e2e.BaseE2EBenchmark.{JSON, singletonContainer}
import pl.touk.nussknacker.test.containers.ContainerExt.toContainerExt
import ujson.Value

import java.io.{File => JFile}

trait BaseE2EBenchmark {

  private val bootstrapSetupService = unsafeContainerByServiceName("bootstrap-setup")

  def deployAndWaitForRunningState(scenarioName: String): Unit = {
    bootstrapSetupService.executeBash(
      s"""/app/utils/nu/deploy-scenario-and-wait-for-running-state.sh "$scenarioName" """
    )
  }

  def sendMessageToKafka(topic: String, message: JSON): Unit = {
    val escapedMessage = message.render().replaceAll("\"", "\\\\\"")
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/send-to-topic.sh "$topic" "$escapedMessage" """)
  }

  def sendMessagesToKafka(topic: String, messages: Iterable[JSON]): Unit = {
    val escapedMessages = messages.map(_.render().replaceAll("\"", "\\\\\"")).mkString("\n")
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/send-to-topic.sh "$topic" "$escapedMessages" """)
  }

  def readAllMessagesFromKafka(topic: String): List[JSON] = {
    bootstrapSetupService
      .executeBashAndReadStdout(s"""/app/utils/kafka/read-from-topic.sh "$topic" """)
      .split("\n")
      .toList
      .flatMap(str => Option.when(str.nonEmpty)(str))
      .map(ujson.read(_))
  }

  private def unsafeContainerByServiceName(name: String) = singletonContainer
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}

object BaseE2EBenchmark extends LazyLogging {

  type JSON = Value

  val singletonContainer: DockerComposeContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new JFile("examples/installation/docker-compose.yml"),
      new JFile(Resource.getUrl("bootstrap-setup.override.yml").toURI)
    ),
    env = Map(
      "NUSSKNACKER_VERSION" -> "1.17.0-RC1" // todo: BuildInfo.version
    ),
    logConsumers = Seq(
      ServiceLogConsumer("bootstrap-setup", new Slf4jLogConsumer(logger.underlying))
    ),
    waitingFor = Some(
      WaitingForService("bootstrap-setup", new DockerHealthcheckWaitStrategy())
    ),
    // Change to 'true' to enable logging
    tailChildContainers = false
  )

  singletonContainer.start()
}
