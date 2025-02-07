package pl.touk.nussknacker.engine.flink.minicluster

import org.apache.flink.configuration.Configuration

final case class FlinkMiniClusterConfig(
    config: Configuration = new Configuration,
    streamExecutionEnvConfig: Configuration = new Configuration
)
