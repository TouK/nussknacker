package pl.touk.nussknacker.test.installationexample

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.Logger
import org.slf4j.MarkerFactory.getIMarkerFactory
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy
import org.testcontainers.utility.LogUtils
import pl.touk.nussknacker.test.MiscUtils._
import pl.touk.nussknacker.test.WithTestHttpClientCreator
import pl.touk.nussknacker.test.containers.ContainerExt.toContainerExt
import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment.{slf4jLogger, JSON}
import sttp.client3._
import sttp.model.MediaType
import ujson.Value

import java.io.{File => JFile}
import java.time.Duration
import java.util
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

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
        ServiceLogConsumer("bootstrap-setup", new Slf4jLogConsumer(slf4jLogger)),
        ServiceLogConsumer("designer", new Slf4jLogConsumer(slf4jLogger)),
      ),
      waitingFor = Some(
        WaitingForService(
          "bootstrap-setup",
          new DockerHealthcheckWaitStrategy().withStartupTimeout(Duration.ofSeconds(150))
        )
      ),
      // Change to 'true' to enable logging
      tailChildContainers = false
    ) {

  Try(start()) match {
    case Failure(ex) =>
      // There is no way currently to automatically capture logs from containers before all services from the docker
      // compose started. When one of the services is not healthy, there won't be any logs captured. That's why we do
      // the capture manually.
      captureAllContainerLogs()
      throw ex
    case Success(()) =>
  }

  private val (dockerBasedInstallationExampleClient, closeHandler) =
    DockerBasedInstallationExampleClient.create(this).allocated.unsafeRunSync()

  val client: DockerBasedInstallationExampleClient = dockerBasedInstallationExampleClient

  override def stop(): Unit = {
    closeHandler.unsafeRunSync()
    super.stop()
  }

  private def captureAllContainerLogs() = {
    val dockerClient = DockerClientFactory.lazyClient()
    getNuDockerComposeContainers(dockerClient).foreach { container =>
      val logs = LogUtils.getOutput(dockerClient, container.getId)
      slf4jLogger.info(getIMarkerFactory.getMarker(container.getNames.mkString(",")), logs)
    }
  }

  private def getNuDockerComposeContainers(dockerClient: DockerClient) = {
    dockerClient
      .listContainersCmd()
      .exec()
      .asInstanceOf[util.ArrayList[Container]]
      .asScala
      .filter { container =>
        // dummy method of how to distinguish if the container is Nu docker-compose related container
        container.labels.asScala.get("com.docker.compose.project.working_dir") match {
          case Some(value) =>
            value.contains("nussknacker")
          case None => false
        }
      }
      .toList
  }

}

object DockerBasedInstallationExampleNuEnvironment extends LazyLogging {

  type JSON = Value

  private def slf4jLogger: Logger = logger.underlying

}

object DockerBasedInstallationExampleClient extends WithTestHttpClientCreator {

  def create(env: DockerBasedInstallationExampleNuEnvironment): Resource[IO, DockerBasedInstallationExampleClient] = {
    createHttpClient(sslContext = None)
      .map(new DockerBasedInstallationExampleClient(env, _))
  }

}

class DockerBasedInstallationExampleClient private (
    env: DockerBasedInstallationExampleNuEnvironment,
    sttpBackend: SttpBackend[Identity, Any]
) {

  private val bootstrapSetupService = unsafeContainerByServiceName("bootstrap-setup")
  private val nginxService          = unsafeContainerByServiceName("nginx")

  def deployAndWaitForRunningState(scenarioName: String): Unit = {
    bootstrapSetupService.executeBash(
      s"""/app/utils/nu/deploy-scenario-and-wait-for-deployed-state.sh "$scenarioName" """
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

  def sendHttpRequest(serviceSlug: String, payload: JSON): Either[Throwable, HttpResponse] = {
    val response = sttp.client3.basicRequest
      .post(uri"http://${nginxService.getHost}:8181/scenario/$serviceSlug")
      .contentType(MediaType.ApplicationJson)
      .body(payload.render())
      .response(asStringAlways)
      .send(sttpBackend)

    Try(ujson.read(response.body)).toEither
      .map(body => HttpResponse(response.code.code, ujson.read(body)))
  }

  private def unsafeContainerByServiceName(name: String) = env
    .getContainerByServiceName(name)
    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))

}

final case class HttpResponse(status: Int, body: JSON)
