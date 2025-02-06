package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ScenarioStateVerificationConfig(
    enabled: Boolean = true,
    // TODO: remove after fully migration, see LegacyFallbackToSingleUseMiniClusterHandler
    reuseSharedMiniCluster: Boolean = true,
    timeout: FiniteDuration = 1.minute
)
