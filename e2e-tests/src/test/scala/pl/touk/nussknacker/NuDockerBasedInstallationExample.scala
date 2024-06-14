package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import pl.touk.nussknacker.ContainerExt._
import ujson.Value

import java.io.{File => JFile}

// todo: singleton container
trait NuDockerBasedInstallationExample extends ForAllTestContainer with LazyLogging {
  this: Suite =>

  override val container: DockerComposeContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new JFile("examples/installation/docker-compose.yml"),
      new JFile(Resource.getUrl("spec-setup/docker-compose.override.yml").toURI)
    ),
    logConsumers = Seq(
      ServiceLogConsumer("spec-setup", new Slf4jLogConsumer(logger.underlying))
    ),
    waitingFor = Some(
      WaitingForService("spec-setup", new LogMessageWaitStrategy().withRegEx("^Setup done!.*"))
    )
  )

  private lazy val specSetupService = unsafeContainerByServiceName("spec-setup")

  def loadFlinkStreamingScenarioFromResource(scenarioName: String, scenarioJsonFile: File): Unit = {
    val escapedScenarioJson = scenarioJsonFile.contentAsString().replaceAll("\"", "\\\\\"")
    specSetupService.executeBash(
      s"/app/scripts/utils/nu/load-scenario-from-json.sh \"$scenarioName\" \"${escapedScenarioJson}\" "
    )
  }

  def deployAndWaitForRunningState(scenarioName: String): Unit = {
    specSetupService.executeBash(
      s"""/app/scripts/utils/nu/deploy-scenario-and-wait-for-running-state.sh "$scenarioName" """
    )
  }

  type JSON = Value

  def sendMessageToKafka(topic: String, message: JSON): Unit = {
    val escapedMessage = message.render().replaceAll("\"", "\\\\\"")
    specSetupService.executeBash(s"""/app/scripts/utils/kafka/send-to-topic.sh "$topic" "$escapedMessage" """)
  }

  def readMessagesFromKafka(topic: String): List[JSON] = {
    val stdout = specSetupService.executeBashAndReadStdout(s"""/app/scripts/utils/kafka/read-from-topic.sh "$topic" """)
    stdout
      .split("\n")
      .toList
      .map(ujson.read(_))
  }

  private def unsafeContainerByServiceName(name: String) = container
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}
