package pl.touk.nussknacker.engine.management

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.IOUtils
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.slf4j
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.containers.Network
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.deployment.User
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Arrays.asList
import scala.collection.JavaConverters._

trait DockerTest extends BeforeAndAfterAll with  ForAllTestContainer with ExtremelyPatientScalaFutures {
  self: Suite =>

  private val network: Network = Network.newNetwork

  private val sfl4jLogger: slf4j.Logger = LoggerFactory.getLogger("DockerTest")

  private val logConsumer = new Slf4jLogConsumer(sfl4jLogger)

  private val FlinkJobManagerRestPort = 8081

  private val FlinkTaskManagerQueryPort = 9069

  private val kafkaNetworkAlias = "kafka"

  protected lazy val logger: Logger = Logger(sfl4jLogger)

  protected val userToAct: User = User("testUser", "Test User")

  private val kafka =  KafkaContainer().configure { self =>
    self.setNetwork(network)
    self.setNetworkAliases(asList(kafkaNetworkAlias))
  }

  private def prepareFlinkImage(): ImageFromDockerfile = {
    List("Dockerfile", "entrypointWithIP.sh", "conf.yml").foldLeft(new ImageFromDockerfile()) { case (image, file) =>
      val resource = IOUtils.toString(getClass.getResourceAsStream(s"/docker/$file"))
      val withVersionReplaced = resource.replace("${scala.major.version}", ScalaMajorVersionConfig.scalaMajorVersion)
      image.withFileFromString(file, withVersionReplaced)
    }
  }

  //testcontainers expose kafka via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostKafkaAddress: String = kafka.bootstrapServers

  //on flink we have to access kafka via network alias
  protected def dockerKafkaAddress = s"$kafkaNetworkAlias:9092"

  protected def taskManagerSlotCount = 8

  private lazy val jobManagerContainer: GenericContainer = {
    logger.debug(s"Running with number TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
    val savepointDir = prepareVolumeDir()
    new GenericContainer(dockerImage = prepareFlinkImage(),
      command = "jobmanager" :: Nil,
      exposedPorts = FlinkJobManagerRestPort :: Nil,
      env = Map("SAVEPOINT_DIR_NAME" -> savepointDir.getFileName.toString,
                "FLINK_PROPERTIES" -> s"state.savepoints.dir: ${savepointDir.toFile.toURI.toString}",
                "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Recover all persisted job graphs.*"))
    ).configure { self =>
      self.withNetwork(network)
      self.setNetworkAliases(asList("jobmanager"))
      self.withLogConsumer(logConsumer.withPrefix("jobmanager"))
      self.withFileSystemBind(savepointDir.toString, savepointDir.toString, BindMode.READ_WRITE)
    }
  }

  private lazy val taskManagerContainer: GenericContainer = {
    new GenericContainer(dockerImage = prepareFlinkImage(),
      exposedPorts = FlinkTaskManagerQueryPort :: Nil,
      command = "taskmanager" :: Nil,
      env = Map("TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Successful registration at resource manager.*"))
    ).configure { self =>
      self.setNetwork(network)
      self.setNetworkAliases(asList("taskmanager"))
      self.withLogConsumer(logConsumer.withPrefix("taskmanager"))
    }
  }

  override def container: Container = MultipleContainers(kafka, jobManagerContainer, taskManagerContainer)

  private def prepareVolumeDir(): Path = {
    Files.createTempDirectory("dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava))
  }

  def config: Config = ConfigFactory.load()
    .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.container.getContainerIpAddress}:${jobManagerContainer.container.getMappedPort(FlinkJobManagerRestPort)}"))
    .withValue("deploymentConfig.queryableStateProxyUrl", fromAnyRef(s"${taskManagerContainer.container.getContainerIpAddress}:${taskManagerContainer.container.getMappedPort(FlinkTaskManagerQueryPort)}"))
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
    .withValue("modelConfig.kafka.kafkaAddress", fromAnyRef(dockerKafkaAddress))
    .withFallback(additionalConfig)

  //used for signals, etc.
  def configWithHostKafka: Config = config
    .withValue("modelConfig.kafka.kafkaAddress", fromAnyRef(hostKafkaAddress))


  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(config)

  protected def classPath: List[String]

  protected def additionalConfig: Config = ConfigFactory.empty()

}
