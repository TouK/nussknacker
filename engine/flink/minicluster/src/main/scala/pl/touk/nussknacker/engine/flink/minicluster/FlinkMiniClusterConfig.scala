package pl.touk.nussknacker.engine.flink.minicluster

import org.apache.flink.configuration.Configuration

final case class FlinkMiniClusterConfig(
    // TODO: remove after fully migration, see LegacySingleUseMiniClusterFallbackHandler
    reuseMiniClusterForScenarioTesting: Boolean = true,
    reuseMiniClusterForScenarioStateVerification: Boolean = true,
    config: Configuration = new Configuration,
    streamExecutionEnvConfig: Configuration = new Configuration
)
