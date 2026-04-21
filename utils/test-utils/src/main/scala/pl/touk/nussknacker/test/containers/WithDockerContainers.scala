package pl.touk.nussknacker.test.containers

import com.github.dockerjava.api.model.{Bind, Volume}
import com.typesafe.scalalogging.Logger
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.scalatest.Suite
import org.testcontainers.containers.{BindMode, GenericContainer, Network}
import pl.touk.nussknacker.test.containers.LogLevelConfigurableScalaLoggingConsumer.LoggerLevel

import java.nio.file.{Files, Path}

trait WithDockerContainers { self: Suite =>

  protected val logger: Logger

  // dedicated method because withPrefix is mutable
  protected def logConsumer(prefix: String): LogLevelConfigurableScalaLoggingConsumer =
    new LogLevelConfigurableScalaLoggingConsumer(
      stdoutLogLevel = LoggerLevel.Debug,
      stderrLogLevel = LoggerLevel.Error
    ).withPrefix(prefix)

  protected val network: Network = Network.newNetwork

  protected def bindTempVolume(container: GenericContainer[_], volume: ContainerVolume): Unit = {
    container.getBinds.add(new Bind(volume.volumeName, new Volume(volume.containerPath), volume.mode.accessMode))
    VolumeReaper.registerVolume(volume.volumeName)
  }

  protected def removeVolumesQuietly(volumeNames: Seq[String]): Unit =
    VolumeReaper.removeVolumesQuietly(volumeNames, logger)

  protected def copyDirectoryFromContainer(
      container: GenericContainer[_],
      containerPath: String,
      destinationPath: Path
  ): Unit = {
    container.copyFileFromContainer(
      containerPath,
      (stream: java.io.InputStream) => {
        // Leverage the fact that we got a TarArchiveInputStream
        // Other possible approaches: https://github.com/testcontainers/testcontainers-java/issues/1647
        val tar = stream.asInstanceOf[TarArchiveInputStream]
        Iterator.continually(tar.getNextEntry).takeWhile(_ != null).foreach { entry =>
          val entryPath = destinationPath.resolve(entry.getName)
          if (entry.isDirectory) {
            Files.createDirectories(entryPath)
          } else {
            Files.createDirectories(entryPath.getParent)
            Files.copy(tar, entryPath)
          }
        }
        tar.close()
      }
    )
  }

}

final case class ContainerVolume(volumeName: String, containerPath: String, mode: BindMode = BindMode.READ_WRITE)
