package pl.touk.nussknacker.engine.management

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.Logger
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.slf4j
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.deployment.User
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, KafkaConfigProperties}

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Path}
import java.util.Arrays.asList
import scala.jdk.CollectionConverters._

trait DockerTest extends BeforeAndAfterAll with ForAllTestContainer with ExtremelyPatientScalaFutures {
  self: Suite =>

  private val network: Network = Network.newNetwork

  private val sfl4jLogger: slf4j.Logger = LoggerFactory.getLogger("DockerTest")

  private val logConsumer = new Slf4jLogConsumer(sfl4jLogger)

  private val FlinkJobManagerRestPort = 8081

  private val kafkaNetworkAlias = "kafka"

  protected lazy val logger: Logger = Logger(sfl4jLogger)

  protected val userToAct: User = User("testUser", "Test User")

  private val kafka = KafkaContainer(DockerImageName.parse(s"${KafkaContainer.defaultImage}:7.4.0")).configure { self =>
    self.setNetwork(network)
    self.setNetworkAliases(asList(kafkaNetworkAlias))
  }

  private def prepareFlinkImage(): ImageFromDockerfile = {
    List("Dockerfile", "entrypointWithIP.sh", "conf.yml", "log4j-console.properties").foldLeft(
      new ImageFromDockerfile()
    ) { case (image, file) =>
      val resource = ResourceLoader.load(s"/docker/$file")

      val flinkLibTweakCommand = ScalaMajorVersionConfig.scalaMajorVersion match {
        case "2.12" => ""
        case "2.13" =>
          s"""
            |RUN rm $$FLINK_HOME/lib/flink-scala*.jar
            |RUN wget https://repo1.maven.org/maven2/pl/touk/flink-scala-2-13_2.13/1.1.1/flink-scala-2-13_2.13-1.1.1-assembly.jar -O $$FLINK_HOME/lib/flink-scala-2-13_2.13-1.1.1-assembly.jar
            |RUN chown flink $$FLINK_HOME/lib/flink-scala-2-13_2.13-1.1.1-assembly.jar
            |""".stripMargin
        case v => throw new IllegalStateException(s"unsupported scala version: $v")
      }
      val withFlinkLibTweaks = resource.replace("${scala.version.flink.tweak.commands}", flinkLibTweakCommand)

      image.withFileFromString(file, withFlinkLibTweaks)
    }
  }

  // testcontainers expose kafka via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostKafkaAddress: String = kafka.bootstrapServers

  // on flink we have to access kafka via network alias
  protected def dockerKafkaAddress = s"$kafkaNetworkAlias:9092"

  protected def taskManagerSlotCount = 8

  private lazy val jobManagerContainer: GenericContainer = {
    logger.debug(s"Running with number TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
    val savepointDir = prepareVolumeDir()
    new GenericContainer(
      dockerImage = prepareFlinkImage(),
      command = "jobmanager" :: Nil,
      exposedPorts = FlinkJobManagerRestPort :: Nil,
      env = Map(
        "SAVEPOINT_DIR_NAME"                -> savepointDir.getFileName.toString,
        "FLINK_PROPERTIES"                  -> s"state.savepoints.dir: ${savepointDir.toFile.toURI.toString}",
        "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString
      ),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Recover all persisted job graphs.*"))
    ).configure { self =>
      self.withNetwork(network)
      self.setNetworkAliases(asList("jobmanager"))
      self.withLogConsumer(logConsumer.withPrefix("jobmanager"))
      self.withFileSystemBind(savepointDir.toString, savepointDir.toString, BindMode.READ_WRITE)
    }
  }

  private lazy val taskManagerContainer: GenericContainer = {
    new GenericContainer(
      dockerImage = prepareFlinkImage(),
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
    Files.createTempDirectory(
      "dockerTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava)
    )
  }

  def config: Config = ConfigFactory
    .load()
    .withValue(
      "deploymentConfig.restUrl",
      fromAnyRef(
        s"http://${jobManagerContainer.container.getHost}:${jobManagerContainer.container.getMappedPort(FlinkJobManagerRestPort)}"
      )
    )
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
    .withValue("modelConfig.enableObjectReuse", fromAnyRef(false))
    .withValue(KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"), fromAnyRef(dockerKafkaAddress))
    .withValue(KafkaConfigProperties.property("modelConfig.kafka", "auto.offset.reset"), fromAnyRef("earliest"))
    .withValue("category", fromAnyRef("Category1"))

  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(ConfigWithUnresolvedVersion(config))

  protected def classPath: List[String]

}
