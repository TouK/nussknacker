package pl.touk.nussknacker.engine.benchmarks.e2e

import better.files.Resource
import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import pl.touk.nussknacker.engine.benchmarks.e2e.BaseE2EBenchmark.singletonContainer

import java.io.{File => JFile}

trait BaseE2EBenchmark {

  private val bootstrapSetupService = unsafeContainerByServiceName("bootstrap-setup")

  private def unsafeContainerByServiceName(name: String) = singletonContainer
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}

object BaseE2EBenchmark extends LazyLogging {

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
