package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import pl.touk.nussknacker.DockerBasedInstallationExampleNuEnvironment.{JSON, singletonContainer}
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.containers.ContainerExt._
import ujson.Value

import java.io.{File => JFile}

// Before running tests in this module, a fresh docker image should be built from sources and placed in the local
// registry. If you run tests based on this trait in Intellij Idea and the images is not built, you can do it manually:
// `bash -c "export NUSSKNACKER_SCALA_VERSION=2.12 && sbt dist/Docker/publishLocal"`
trait DockerBasedInstallationExampleNuEnvironment extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  this: Suite =>

  private val bootstrapSetupService = unsafeContainerByServiceName("bootstrap-setup")

  def sendMessageToKafka(topic: String, message: JSON): Unit = {
    val escapedMessage = message.render().replaceAll("\"", "\\\\\"")
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/send-to-topic.sh "$topic" "$escapedMessage" """)
  }

  def readAllMessagesFromKafka(topic: String): List[JSON] = {
    bootstrapSetupService
      .executeBashAndReadStdout(s"""/app/utils/kafka/read-from-topic.sh "$topic" """)
      .split("\n")
      .toList
      .map(ujson.read(_))
  }

  def purgeKafkaTopic(topic: String): Unit = {
    bootstrapSetupService.executeBash(s"""/app/utils/kafka/purge-topic.sh "$topic" """)
  }

  private def unsafeContainerByServiceName(name: String) = singletonContainer
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}

object DockerBasedInstallationExampleNuEnvironment extends LazyLogging {

  type JSON = Value

  val singletonContainer: DockerComposeContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new JFile("examples/installation/docker-compose.yml"),
      new JFile(Resource.getUrl("bootstrap-setup.override.yml").toURI),
      new JFile(Resource.getUrl("batch-nu-designer.override.yml").toURI),
      new JFile(Resource.getUrl("debuggable-nu-designer.override.yml").toURI)
    ),
    env = Map(
      "NUSSKNACKER_VERSION" -> BuildInfo.version
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
