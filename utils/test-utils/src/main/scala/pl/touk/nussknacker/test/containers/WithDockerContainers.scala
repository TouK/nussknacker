package pl.touk.nussknacker.test.containers

import org.scalatest.Suite
import org.testcontainers.containers.{BindMode, Network}
import pl.touk.nussknacker.test.containers.LogLevelConfigurableScalaLoggingConsumer.LoggerLevel

import java.nio.file.{Files, FileSystems, Path}
import java.nio.file.attribute.PosixFilePermissions

trait WithDockerContainers { self: Suite =>

  // dedicated method because withPrefix is mutable
  protected def logConsumer(prefix: String): LogLevelConfigurableScalaLoggingConsumer =
    new LogLevelConfigurableScalaLoggingConsumer(
      stdoutLogLevel = LoggerLevel.Debug,
      stderrLogLevel = LoggerLevel.Error
    ).withPrefix(prefix)

  protected val network: Network = Network.newNetwork

  /**
   * Creates a temporary directory with possibly insecure permissions.
   * If your directory is writable you may need to also use `chmod` in Docker container.
   *
   * Instead of mounting prefer [[com.dimafeng.testcontainers.GenericContainer.copyFileFromContainer()]]
   * and [[com.dimafeng.testcontainers.GenericContainer.copyFileToContainer()]].
   */
  protected def createMountableTempDirectory(prefix: String, mode: BindMode): Path = {
    val posixPerms = mode match {
      case BindMode.READ_WRITE => "rwxrwxrwx"
      case BindMode.READ_ONLY  => "rwxr-xr-x"
    }

    val tempDirectoryAttributes = {
      if (FileSystems.getDefault.supportedFileAttributeViews().contains("posix")) {
        val allPermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(posixPerms))
        List(allPermissions)
      } else {
        Nil // Windows
      }
    }
    Files.createTempDirectory(prefix, tempDirectoryAttributes: _*)
  }

}

final case class FileSystemBind(hostPath: String, containerPath: String, mode: BindMode)
