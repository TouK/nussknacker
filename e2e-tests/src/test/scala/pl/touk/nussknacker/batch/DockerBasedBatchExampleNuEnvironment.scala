package pl.touk.nussknacker.batch

import better.files.Resource
import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import pl.touk.nussknacker.engine.version.BuildInfo

import java.io.{File => JFile}

trait DockerBasedBatchExampleNuEnvironment extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  this: Suite =>

  private lazy val env = DockerBasedBatchExampleNuEnvironment

  private def unsafeContainerByServiceName(name: String) = env.singletonContainer
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

  lazy val nginxServiceUrl: String = {
    val nginxContainer = unsafeContainerByServiceName("nginx")
    s"http://${nginxContainer.getHost}:${nginxContainer.getBoundPortNumbers.get(0)}"
  }

}

object DockerBasedBatchExampleNuEnvironment extends LazyLogging {

  val singletonContainer: DockerComposeContainer = new DockerComposeContainer(
    composeFiles = Seq(
      new JFile("examples/installation/docker-compose.yml"),
      new JFile(Resource.getUrl("batch-setup/scenario-setup.override.yml").toURI),
      new JFile(Resource.getUrl("batch-setup/batch-nu-designer.override.yml").toURI)
    ),
    env = Map(
      "NUSSKNACKER_VERSION" -> BuildInfo.version
    ),
    logConsumers = Seq(
      ServiceLogConsumer("scenario-setup", new Slf4jLogConsumer(logger.underlying))
    ),
    waitingFor = Some(
      WaitingForService("scenario-setup", new LogMessageWaitStrategy().withRegEx("^Setup done!.*"))
    )
  )

  singletonContainer.start()

}
