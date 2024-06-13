package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import pl.touk.nussknacker.ContainerExt._

import java.io.File

// todo: singleton container
trait NuDockerBasedInstallationExample extends ForAllTestContainer with LazyLogging {
  this: Suite =>

  override val container: DockerComposeContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new File("examples/installation/docker-compose.yml"),
      new File(Resource.getUrl("spec-setup/docker-compose.override.yml").toURI)
    ),
    logConsumers = Seq(
      ServiceLogConsumer("spec-setup", new Slf4jLogConsumer(logger.underlying))
    ),
    waitingFor = Some(
      WaitingForService("spec-setup", new LogMessageWaitStrategy().withRegEx("^Setup done!.*"))
    )
  )

  protected lazy val nussknackerAppClient: NussknackerAppClient = new NussknackerAppClient("localhost", 8080)

  private lazy val specSetupService = unsafeContainerByServiceName("spec-setup")

  def sendMessageToKafka(topic: String, message: String): Unit = {
    val escapedMessage = message.replaceAll("\"", "\\\\\"")
    specSetupService.executeBash(s"""/app/scripts/utils/kafka/send-to-kafka.sh "$topic" "$escapedMessage" """)
  }

  private def unsafeContainerByServiceName(name: String) = container
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}
