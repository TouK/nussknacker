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

import java.nio.file.{Files, Path}
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

  private lazy val jobManagerContainer: GenericContainer = {
    logger.debug(s"Running with number TASK_MANAGER_NUMBER_OF_TASK_SLOTS=$taskManagerSlotCount")
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
      self.withLogConsumer(logConsumer(prefix = "jobmanager"))
      self.withFileSystemBind(savepointDir.toString, savepointDir.toString, BindMode.READ_WRITE)
      jobManagerExtraFSBinds.foreach { bind =>
        self.withFileSystemBind(bind.hostPath, bind.containerPath, bind.mode)
      }
    }
  }

  private lazy val taskManagerContainer: GenericContainer = {
    new GenericContainer(
      dockerImage = prepareFlinkImage(),
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
    List("Dockerfile", "entrypointWithIP.sh", "conf.yml", "log4j-console.properties").foldLeft(
      new ImageFromDockerfile()
    ) { case (image, file) =>
      val resource = ResourceLoader.load(s"/docker/$file")

      val flinkLibTweakCommand = ScalaMajorVersionConfig.scalaMajorVersion match {
        case "2.12" => ""
        case "2.13" =>
          s"""
             |RUN rm $$FLINK_HOME/lib/flink-scala*.jar
             |RUN wget https://repo1.maven.org/maven2/pl/touk/flink-scala-2-13_2.13/1.1.2/flink-scala-2-13_2.13-1.1.2-assembly.jar -O $$FLINK_HOME/lib/flink-scala-2-13_2.13-1.1.2-assembly.jar
             |RUN chown flink $$FLINK_HOME/lib/flink-scala-2-13_2.13-1.1.2-assembly.jar
             |""".stripMargin
        case v => throw new IllegalStateException(s"unsupported scala version: $v")
      }
      val withFlinkLibTweaks = resource.replace("${scala.version.flink.tweak.commands}", flinkLibTweakCommand)

      image.withFileFromString(file, withFlinkLibTweaks)
    }
  }

  private def prepareSavepointVolumeDir(): Path = {
    Files.createTempDirectory(
      "nussknackerFlinkSavepointTest",
      PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet[PosixFilePermission].asJava)
    )
  }

}
