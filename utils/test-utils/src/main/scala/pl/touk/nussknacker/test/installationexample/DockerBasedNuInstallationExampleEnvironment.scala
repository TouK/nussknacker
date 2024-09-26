package pl.touk.nussknacker.test.installationexample

import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.Logger
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import pl.touk.nussknacker.test.containers.ContainerExt.toContainerExt
import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment.{JSON, slf4jLogger}
import ujson.Value
import pl.touk.nussknacker.test.MiscUtils._

import java.io.{File => JFile}
import java.time.Duration

class DockerBasedInstallationExampleNuEnvironment(
    nussknackerImageVersion: String,
    dockerComposeTweakFiles: Iterable[JFile]
) extends DockerComposeContainer(
      composeFiles = new JFile("examples/installation/docker-compose.yml") ::
        DockerBasedInstallationExampleNuEnvironment.getClass
          .getResourceAsStream("/bootstrap-setup.override.yml")
          .toFile ::
        dockerComposeTweakFiles.toList,
      env = Map(
        "NUSSKNACKER_VERSION" -> nussknackerImageVersion
      ),
      logConsumers = Seq(
        ServiceLogConsumer("bootstrap-setup", new Slf4jLogConsumer(slf4jLogger))
      ),
      waitingFor = Some(
        WaitingForService(
          "bootstrap-setup",
          new DockerHealthcheckWaitStrategy().withStartupTimeout(Duration.ofSeconds(120))
        )
      ),
      // Change to 'true' to enable logging
      tailChildContainers = false
    ) {

  start()

  val client: DockerBasedInstallationExampleClient = new DockerBasedInstallationExampleClient(this)
}

object DockerBasedInstallationExampleNuEnvironment extends LazyLogging {

  type JSON = Value

  private def slf4jLogger: Logger = logger.underlying

}

class DockerBasedInstallationExampleClient(env: DockerBasedInstallationExampleNuEnvironment) {

  private val bootstrapSetupService = unsafeContainerByServiceName("bootstrap-setup")

  def deployAndWaitForRunningState(scenarioName: String): Unit = {
    bootstrapSetupService.executeBash(
      s"""/app/utils/nu/deploy-scenario-and-wait-for-running-state.sh "$scenarioName" """
    )
  }

  def sendMessageToKafka(topic: String, message: JSON): Unit = {
    sendMessagesToKafka(topic, message :: Nil)
  }

  def sendMessagesToKafka(topic: String, messages: Iterable[JSON]): Unit = {
    val escapedMessages = messages.map(_.render().replaceAll("\"", "\\\\\"")).mkString("\n")
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/send-to-topic.sh "$topic" "$escapedMessages" """)
  }

  def readAllMessagesFromKafka(topic: String): List[JSON] = {
    bootstrapSetupService
      .executeBashAndReadStdout(s"""/app/utils/kafka/read-from-topic.sh "$topic" """)
      .split("\n")
      .flatMap {
        case ""  => None
        case str => Option(str)
      }
      .toList
      .map(ujson.read(_))
  }

  def purgeKafkaTopic(topic: String): Unit = {
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/purge-topic.sh "$topic" """)
  }

  private def unsafeContainerByServiceName(name: String) = env
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}
