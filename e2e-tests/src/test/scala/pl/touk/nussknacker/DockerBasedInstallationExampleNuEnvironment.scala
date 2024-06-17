package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import pl.touk.nussknacker.ContainerExt._
import pl.touk.nussknacker.DockerBasedInstallationExampleNuEnvironment.JSON
import ujson.Value

import java.io.{File => JFile}

trait DockerBasedInstallationExampleNuEnvironment
    extends ForAllTestContainer
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with LazyLogging {
  this: Suite =>

  override val container: DockerComposeContainer = DockerBasedInstallationExampleNuEnvironment.singletonContainer

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

  def sendMessageToKafka(topic: String, message: JSON): Unit = {
    val escapedMessage = message.render().replaceAll("\"", "\\\\\"")
    specSetupService.executeBash(s"""/app/scripts/utils/kafka/send-to-topic.sh "$topic" "$escapedMessage" """)
  }

  def readAllMessagesFromKafka(topic: String): List[JSON] = {
    val stdout = specSetupService.executeBashAndReadStdout(s"""/app/scripts/utils/kafka/read-from-topic.sh "$topic" """)
    stdout
      .split("\n")
      .toList
      .map(ujson.read(_))
  }

  def purgeKafkaTopic(topic: String): Unit = {
    specSetupService.executeBash(s"""/app/scripts/utils/kafka/purge-topic.sh "$topic" """)
  }

  private def unsafeContainerByServiceName(name: String) = container
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}

object DockerBasedInstallationExampleNuEnvironment extends LazyLogging {

  type JSON = Value

  val singletonContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new JFile("examples/installation/docker-compose.yml"),
      new JFile(Resource.getUrl("spec-setup/spec-setup.override.yml").toURI),
      new JFile(Resource.getUrl("spec-setup/debuggable-nu-designer.override.yml").toURI)
    ),
    logConsumers = Seq(
      ServiceLogConsumer("spec-setup", new Slf4jLogConsumer(logger.underlying))
    ),
    waitingFor = Some(
      WaitingForService("spec-setup", new LogMessageWaitStrategy().withRegEx("^Setup done!.*"))
    )
  )

}
