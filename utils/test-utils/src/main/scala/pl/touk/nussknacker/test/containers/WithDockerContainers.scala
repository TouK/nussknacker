package pl.touk.nussknacker.test.containers

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.testcontainers.containers.{BindMode, Network}

trait WithDockerContainers { self: Suite with LazyLogging =>

  // dedicated method because withPrefix is mutable
  protected def logConsumer(prefix: String): DebugLevelSlf4jLogConsumer =
    new DebugLevelSlf4jLogConsumer(logger.underlying).withSeparateOutputStreams
      .withPrefix(prefix)

  protected val network: Network = Network.newNetwork

}

final case class FileSystemBind(hostPath: String, containerPath: String, mode: BindMode)
