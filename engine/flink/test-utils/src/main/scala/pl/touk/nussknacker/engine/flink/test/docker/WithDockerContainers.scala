package pl.touk.nussknacker.engine.flink.test.docker

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import org.testcontainers.containers.{BindMode, Network}
import org.testcontainers.containers.output.Slf4jLogConsumer

trait WithDockerContainers { self: Suite with LazyLogging =>

  // dedicated method because withPrefix is mutable
  protected def logConsumer(prefix: String): Slf4jLogConsumer =
    new Slf4jLogConsumer(logger.underlying).withPrefix(prefix)

  protected val network: Network = Network.newNetwork

}

final case class FileSystemBind(hostPath: String, containerPath: String, mode: BindMode)
