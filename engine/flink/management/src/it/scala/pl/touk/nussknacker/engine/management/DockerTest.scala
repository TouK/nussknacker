package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.file.{Files, Path}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.scalalogging.LazyLogging
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{ContainerLink, DockerContainer, DockerFactory, DockerReadyChecker, LogLineReceiver, VolumeMapping}
import org.apache.commons.io.FileUtils
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.kafka.KafkaClient

import scala.concurrent.duration._

trait DockerTest extends DockerTestKit with ScalaFutures with LazyLogging {
  self: Suite =>

  private val flinkEsp = "flinkesp:1.7.2"

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  protected var kafkaClient: KafkaClient = _

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(90, Seconds),
    interval = Span(1, Millis)
  )

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  //TODO: make pull request to flink out of it?
  private def prepareDockerImage() = {
    val dir = Files.createTempDirectory("forDockerfile")
    val dirFile = dir.toFile

    List("Dockerfile", "entrypointWithIP.sh", "conf.yml", "docker-entrypoint.sh").foreach { file =>
      FileUtils.copyInputStreamToFile(getClass.getResourceAsStream(s"/docker/$file"), new File(dirFile, file))
    }

    client.build(dir, flinkEsp)
  }

  prepareDockerImage()

  val KafkaPort = 9092
  val ZookeeperDefaultPort = 2181
  val FlinkJobManagerRestPort = 8081

  lazy val zookeeperContainer =
    DockerContainer("wurstmeister/zookeeper:3.4.6", name = Some("zookeeper"))

  def baseFlink(name: String) = DockerContainer(flinkEsp, Some(name))

  lazy val jobManagerContainer: DockerContainer = {
    val savepointDir = prepareVolumeDir()
    baseFlink("jobmanager")
      .withCommand("jobmanager")
      .withEnv("JOB_MANAGER_RPC_ADDRESS_COMMAND=grep $HOSTNAME /etc/hosts | awk '{print $1}'", s"SAVEPOINT_DIR_NAME=${savepointDir.getFileName}")
      .withReadyChecker(DockerReadyChecker.LogLineContains("Recovering all persisted jobs").looped(5, 1 second))
      .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))
      .withVolumes(List(VolumeMapping(savepointDir.toString, savepointDir.toString, rw = true)))
      .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
        logger.debug(s"jobmanager: $s")
      }))
  }

  def buildTaskManagerContainer(additionalLinks: Seq[ContainerLink] = Nil,
                                volumes: Seq[VolumeMapping] = Nil): DockerContainer = {
    val links = List(
      ContainerLink(zookeeperContainer, "zookeeper"),
      ContainerLink(jobManagerContainer, "jobmanager")
    ) ++ additionalLinks
    baseFlink("taskmanager")
      .withCommand("taskmanager")
      .withReadyChecker(DockerReadyChecker.LogLineContains("Successful registration at resource manager").looped(5, 1 second))
      .withLinks(links :_*)
      .withVolumes(volumes)
      .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
        logger.debug(s"taskmanager: $s")
      }))
  }

  protected def ipOfContainer(container: DockerContainer): String = container.getIpAddresses().futureValue.head

  protected def prepareVolumeDir(): Path = {
    import scala.collection.JavaConverters._
    Files.createTempDirectory("dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava))
  }
}
