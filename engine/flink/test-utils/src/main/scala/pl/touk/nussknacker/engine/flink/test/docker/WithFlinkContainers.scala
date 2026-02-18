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

import java.nio.file.Path

trait WithFlinkContainers extends WithDockerContainers { self: Suite with StrictLogging =>

  protected val FlinkJobManagerRestPort = 8081

  protected lazy val taskManagerSlotCount = 32

  protected def jobManagerExtraFSBinds: List[FileSystemBind] = List.empty

  protected def taskManagerExtraFSBinds: List[FileSystemBind] = List.empty

  protected def jobManagerRestUrl =
    s"http://${jobManagerContainer.container.getHost}:${jobManagerContainer.container.getMappedPort(FlinkJobManagerRestPort)}"

  protected def flinkContainers: List[LazyContainer[GenericContainer]] = List(jobManagerContainer, taskManagerContainer)

  protected lazy val savepointDir: Path =
    createMountableTempDirectory("nussknackerFlinkSavepointTest", BindMode.READ_WRITE)

  private lazy val flinkImage = prepareFlinkImage()

  private lazy val jobManagerContainer: GenericContainer = {
    val containerSavepointPath = s"/tmp/${savepointDir.getFileName}"
    new GenericContainer(
      dockerImage = flinkImage,
      command = "jobmanager" :: Nil,
      exposedPorts = FlinkJobManagerRestPort :: Nil,
      env = Map(
        "SAVEPOINT_DIR_PATH" -> containerSavepointPath,
        "FLINK_PROPERTIES" ->
          s"""execution.checkpointing.savepoint-dir: file:$containerSavepointPath
             |""".stripMargin,
      ),
      waitStrategy = Some(new LogMessageWaitStrategy().withRegEx(".*Recover all persisted job graphs.*"))
    ).configure { self =>
      self.withNetwork(network)
      self.withLogConsumer(logConsumer(prefix = "jobmanager"))
      self.withFileSystemBind(savepointDir.toString, containerSavepointPath, BindMode.READ_WRITE)
      jobManagerExtraFSBinds.foreach { bind =>
        self.withFileSystemBind(bind.hostPath, bind.containerPath, bind.mode)
      }
    }
  }

  private lazy val taskManagerContainer: GenericContainer = {
    logger.debug(s"Running with TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
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

}
