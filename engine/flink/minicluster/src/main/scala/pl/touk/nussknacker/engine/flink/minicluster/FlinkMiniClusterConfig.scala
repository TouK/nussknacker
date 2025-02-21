package pl.touk.nussknacker.engine.flink.minicluster

import org.apache.flink.configuration.Configuration

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class FlinkMiniClusterConfig(
    config: Configuration = new Configuration,
    streamExecutionEnvConfig: Configuration = new Configuration,
    waitForJobManagerRestAPIAvailableTimeout: FiniteDuration = 10.seconds
)
