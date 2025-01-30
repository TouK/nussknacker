package pl.touk.nussknacker.engine.flink.minicluster

import org.apache.flink.configuration.Configuration

final case class FlinkMiniClusterConfig(
    // TODO: remove after fully migration, see LegacyAdHocMiniClusterFallbackHandler
    useForScenarioTesting: Boolean = true,
    useForScenarioStateVerification: Boolean = true,
    config: Configuration = new Configuration,
    streamExecutionEnvConfig: Configuration = new Configuration
)
