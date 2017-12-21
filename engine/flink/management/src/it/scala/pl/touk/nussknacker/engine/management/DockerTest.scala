package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util.Collections

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{ContainerLink, DockerContainer, DockerFactory, DockerReadyChecker, LogLineReceiver, VolumeMapping}
import org.apache.commons.io.FileUtils
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

trait DockerTest extends DockerTestKit with ScalaFutures with LazyLogging {
  self: Suite =>

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(90, Seconds),
    interval = Span(1, Millis)
  )

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  //TODO: make pull request to flink out of it?
  private def prepareDockerImage() = {
    val dir = Files.createTempDirectory("forDockerfile")
    val dirFile = dir.toFile

    List("Dockerfile", "entrypointWithIP.sh", "conf.yml").foreach { file =>
      FileUtils.copyInputStreamToFile(getClass.getResourceAsStream(s"/docker/$file"), new File(dirFile, file))
    }

    client.build(dir, "flinkesp:1.3.1")
  }

  prepareDockerImage()

  val KafkaPort = 9092
  val ZookeeperDefaultPort = 2181
  val FlinkJobManagerPort = 6123

  lazy val zookeeperContainer =
    DockerContainer("wurstmeister/zookeeper:3.4.6", name = Some("zookeeper"))

  lazy val kafkaContainer = DockerContainer("wurstmeister/kafka:0.10.2.1", name = Some("kafka"))
    .withEnv(s"KAFKA_ADVERTISED_PORT=$KafkaPort",
              s"KAFKA_ZOOKEEPER_CONNECT=zookeeper:$ZookeeperDefaultPort",
              "KAFKA_BROKER_ID=0",
              "HOSTNAME_COMMAND=grep $HOSTNAME /etc/hosts | awk '{print $1}'")
    .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))
    .withReadyChecker(DockerReadyChecker.LogLineContains("started (kafka.server.KafkaServer)").looped(5, 1 second))

  def baseFlink(name: String) = DockerContainer("flinkesp:1.3.1", Some(name))

  lazy val jobManagerContainer = {
    val savepointDirName = prepareSavepointDirName()
    val savepointDir = "/tmp/" + savepointDirName
    baseFlink("jobmanager")
      .withCommand("jobmanager")
      .withEnv("JOB_MANAGER_RPC_ADDRESS_COMMAND=grep $HOSTNAME /etc/hosts | awk '{print $1}'", s"SAVEPOINT_DIR_NAME=$savepointDirName")
      .withReadyChecker(DockerReadyChecker.LogLineContains("New leader reachable").looped(5, 1 second))
      .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))
      .withVolumes(List(VolumeMapping(savepointDir, savepointDir, true)))
      .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
        logger.info(s"jobmanager: $s")
      }))
  }

  lazy val taskManagerContainer = baseFlink("taskmanager")
    .withCommand("taskmanager")
    .withReadyChecker(DockerReadyChecker.LogLineContains("Starting TaskManager actor").looped(5, 1 second))
    .withLinks(
      ContainerLink(kafkaContainer, "kafka"),
      ContainerLink(zookeeperContainer, "zookeeper"),
      ContainerLink(jobManagerContainer, "jobmanager"))
    .withLogLineReceiver(LogLineReceiver(withErr = true, s => {
      logger.info(s"taskmanager: $s")
    }))

  def config : Config = ConfigFactory.load()
    .withValue("processConfig.kafka.zkAddress", fromAnyRef(s"${ipOfContainer(zookeeperContainer)}:$ZookeeperDefaultPort"))
    .withValue("processConfig.kafka.kafkaAddress", fromAnyRef(s"${ipOfContainer(kafkaContainer)}:$KafkaPort"))
    .withValue("flinkConfig.customConfig.high-availability.zookeeper.quorum", fromAnyRef(s"${ipOfContainer(zookeeperContainer)}:$ZookeeperDefaultPort"))

  private def ipOfContainer(container: DockerContainer) = container.getIpAddresses().futureValue.head

  abstract override def dockerContainers: List[DockerContainer] =
    List(zookeeperContainer, kafkaContainer, jobManagerContainer, taskManagerContainer) ++ super.dockerContainers

  private def prepareSavepointDirName() : String = {
    import scala.collection.JavaConversions._
    val tempDir = Files.createTempDirectory("dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission]))
    tempDir.toFile.getName
  }

  protected lazy val processManager = FlinkProcessManager(config)


}
