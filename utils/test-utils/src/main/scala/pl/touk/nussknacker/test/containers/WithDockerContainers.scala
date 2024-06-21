package pl.touk.nussknacker.test.containers

import org.scalatest.Suite
import org.testcontainers.containers.{BindMode, Network}
import pl.touk.nussknacker.test.containers.LogLevelConfigurableSlf4jLogConsumer.LoggerLevel

trait WithDockerContainers { self: Suite =>

  // dedicated method because withPrefix is mutable
  protected def logConsumer(prefix: String): LogLevelConfigurableSlf4jLogConsumer =
    new LogLevelConfigurableSlf4jLogConsumer(
      stdoutLogLevel = LoggerLevel.Debug,
      stderrLogLevel = LoggerLevel.Error
    ).withPrefix(prefix)

  protected val network: Network = Network.newNetwork

}

final case class FileSystemBind(hostPath: String, containerPath: String, mode: BindMode)
