package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy

import java.io.File

// todo: singleton container
trait NuDockerBasedInstallationExample extends ForAllTestContainer with BeforeAndAfterAll with LazyLogging {
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

  private lazy val specSetupService = container
    .getContainerByServiceName("spec-setup")
    .getOrElse(throw new IllegalStateException("Spec-setup service not available!"))

  def sendMessageToKafka(topic: String, message: String): Unit = {
    val cmd = s"""echo "${message.replaceAll(
        "\"",
        "\\\""
      )}" | /opt/bitnami/kafka/bin/kafka-console-producer.sh --topic "$topic" --bootstrap-server kafka:9092"""
    val exitResult = specSetupService.execInContainer(cmd)
    exitResult.getExitCode match {
      case 0     => logger.error(s"cmd: $cmd - MESSAGE SENT")
      case other => logger.error(s"cmd: $cmd - MESSAGE NOT SENT: $other")
    }
  }

}
