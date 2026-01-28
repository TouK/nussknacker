package pl.touk.nussknacker.engine.flink.test.docker

import com.dimafeng.testcontainers.{GenericContainer, LazyContainer}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.Suite
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.containers.{FileSystemBind, WithDockerContainers}

import java.nio.file.{Files, FileSystems, Path, Paths}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import scala.jdk.CollectionConverters._

trait WithFlinkContainers extends WithDockerContainers { self: Suite with StrictLogging =>

  protected val FlinkJobManagerRestPort = 8081

  protected lazy val taskManagerSlotCount = 32

  protected def jobManagerExtraFSBinds: List[FileSystemBind] = List.empty

  protected def taskManagerExtraFSBinds: List[FileSystemBind] = List.empty

  protected def jobManagerRestUrl =
    s"http://${jobManagerContainer.container.getHost}:${jobManagerContainer.container.getMappedPort(FlinkJobManagerRestPort)}"

  protected def flinkContainers: List[LazyContainer[_]] = List(jobManagerContainer, taskManagerContainer)

  protected lazy val savepointDir: Path = prepareSavepointVolumeDir()

  private lazy val flinkImage = prepareFlinkImage()

  private lazy val jobManagerContainer: GenericContainer = {
    logger.debug(s"Running with number TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
    val containerSavepointPath = Paths.get("/tmp/").resolve(savepointDir.getFileName)
    new GenericContainer(
      dockerImage = flinkImage,
      command = "jobmanager" :: Nil,
      exposedPorts = FlinkJobManagerRestPort :: Nil,
      env = Map(
        "SAVEPOINT_DIR_NAME" -> s"${containerSavepointPath.getFileName.toString}",
        //  Nu requires a little bit more metaspace than Flink default allocate based on process size
        "FLINK_PROPERTIES" ->
          s"""jobmanager.memory.jvm-metaspace.size: 400m
             |execution.checkpointing.savepoint-dir: ${containerSavepointPath.toUri.toString}""".stripMargin,
        "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString
      ),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Recover all persisted job graphs.*"))
    ).configure { self =>
      self.withNetwork(network)
      self.withLogConsumer(logConsumer(prefix = "jobmanager"))
      self.withFileSystemBind(savepointDir.toString, containerSavepointPath.toString, BindMode.READ_WRITE)
      jobManagerExtraFSBinds.foreach { bind =>
        self.withFileSystemBind(bind.hostPath, bind.containerPath, bind.mode)
      }
    }
  }

  private lazy val taskManagerContainer: GenericContainer = {
    new GenericContainer(
      dockerImage = flinkImage,
      command = "taskmanager" :: Nil,
      env = Map(
        "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString,
        "JOB_MANAGER_RPC_ADDRESS"           -> jobManagerContainer.container.getContainerInfo.getConfig.getHostName
      ),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Successful registration at resource manager.*"))
    ).configure { self =>
      self.setNetwork(network)
      self.withLogConsumer(logConsumer(prefix = "taskmanager"))
      taskManagerExtraFSBinds.foreach { bind =>
        self.withFileSystemBind(bind.hostPath, bind.containerPath, bind.mode)
      }
    }
  }

  private def prepareFlinkImage(): ImageFromDockerfile = {
    List("Dockerfile", "entrypointWithIP.sh", "config.overrides.yml", "log4j-console.overrides.properties").foldLeft(
      new ImageFromDockerfile()
    ) { case (image, file) =>
      val resource = ResourceLoader.load(s"/docker/$file")

      val withFlinkLibTweaks = resource.replace("${scala.version}", ScalaMajorVersionConfig.scalaMajorVersion)

      image.withFileFromString(file, withFlinkLibTweaks)
    }
  }

  private def prepareSavepointVolumeDir(): Path = {
    val tempDirectoryAttributes =
      if (FileSystems.getDefault.supportedFileAttributeViews().contains("posix")) {
        val allPermissions = PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet.asJava)
        List(allPermissions)
      } else {
        Nil // Windows
      }
    Files.createTempDirectory("nussknackerFlinkSavepointTest", tempDirectoryAttributes: _*)
  }

}
