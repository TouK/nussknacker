package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Collections

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{ContainerLink, DockerContainer, DockerFactory, DockerReadyChecker, LogLineReceiver, VolumeMapping}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.deployment.User
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures

import scala.concurrent.duration._

trait DockerTest extends DockerTestKit with ExtremelyPatientScalaFutures with LazyLogging {
  self: Suite =>

  override val StartContainersTimeout: FiniteDuration = 5.minutes
  override val StopContainersTimeout: FiniteDuration = 2.minutes


  private val flinkEsp = s"flinkesp:1.10.0-scala_${ScalaMajorVersionConfig.scalaMajorVersion}"

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  protected val userToAct = User("testUser", "Test User")

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  //TODO: make pull request to flink out of it?
  private def prepareDockerImage() = {
    val dir = Files.createTempDirectory("forDockerfile")
    val dirFile = dir.toFile

    List("Dockerfile", "entrypointWithIP.sh", "conf.yml").foreach { file =>
      val resource = IOUtils.toString(getClass.getResourceAsStream(s"/docker/$file"))
      val withVersionReplaced = resource.replace("${scala.major.version}", ScalaMajorVersionConfig.scalaMajorVersion)
      FileUtils.writeStringToFile(new File(dirFile, file), withVersionReplaced)
    }

    client.build(dir, flinkEsp)
  }

  prepareDockerImage()

  val KafkaPort = 9092
  val ZookeeperDefaultPort = 2181
  val FlinkJobManagerRestPort = 8081
  val taskManagerSlotCount = 8

  lazy val zookeeperContainer =
    DockerContainer("wurstmeister/zookeeper:3.4.6", name = Some("zookeeper"))

  def baseFlink(name: String) = DockerContainer(flinkEsp, Some(name))

  lazy val jobManagerContainer: DockerContainer = {
    val savepointDir = prepareVolumeDir()
    baseFlink("jobmanager")
      .withCommand("jobmanager")
      .withEnv(s"SAVEPOINT_DIR_NAME=${savepointDir.getFileName}", s"TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
      .withReadyChecker(DockerReadyChecker.LogLineContains("Recover all persisted job graphs").looped(5, 1 second))
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
      .withEnv(s"TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
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

  def config: Config = ConfigFactory.load()
    .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.getIpAddresses().futureValue.head}:$FlinkJobManagerRestPort"))
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(Collections.singletonList(classPath)))
    .withFallback(additionalConfig)

  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(config)

  protected def classPath: String

  protected def additionalConfig: Config = ConfigFactory.empty()

}
