package pl.touk.nussknacker.e2e

import com.dimafeng.testcontainers.{DockerComposeContainer, ForAllTestContainer, ServiceLogConsumer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import pl.touk.nussknacker.engine.flink.test.docker.WithDockerContainers

import java.io.File

// todo: singleton container
trait NuDockerBasedInstallationExample extends ForAllTestContainer with WithDockerContainers with LazyLogging {
  this: Suite =>

  val test = new DockerComposeContainer(
    composeFiles = new File("examples/installation/docker-compose.yml"),
    waitingFor = Some(WaitingForService("nginx", new DockerHealthcheckWaitStrategy)),
    logConsumers = Seq(ServiceLogConsumer("nginx", logConsumer("test")))
  )

  override def container: DockerComposeContainer = test

  override def afterStart(): Unit = {
    val ll = test.getContainerByServiceName("akhq")
    ll.get.execInContainer()
    super.afterStart()
  }

}
