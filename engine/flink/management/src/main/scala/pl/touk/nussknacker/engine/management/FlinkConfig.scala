package pl.touk.nussknacker.engine.management

import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ValueReader
import org.apache.flink.configuration.{Configuration, CoreOptions, MemorySize, RestOptions, TaskManagerOptions}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

/**
  * FlinkConfig deployment configuration.
  *
  * @param restUrl REST API endpoint of the Flink cluster.
  * @param jobManagerTimeout Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.
  * @param shouldVerifyBeforeDeploy By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.
  * @param shouldCheckAvailableSlots When true {@link FlinkDeploymentManager} checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.
  */
final case class FlinkConfig(
    restUrl: Option[String],
    jobManagerTimeout: FiniteDuration = 1 minute,
    // TODO: move to scenarioTesting
    shouldVerifyBeforeDeploy: Boolean = true,
    shouldCheckAvailableSlots: Boolean = true,
    waitForDuringDeployFinish: FlinkWaitForDuringDeployFinishedConfig =
      FlinkWaitForDuringDeployFinishedConfig(enabled = true, Some(180), Some(1 second)),
    scenarioStateRequestTimeout: FiniteDuration = 3 seconds,
    jobConfigsCacheSize: Int = 1000,
    scenarioTesting: ScenarioTestingConfig = ScenarioTestingConfig()
)

object FlinkConfig {
  // Keep it synchronize with FlinkConfig
  val RestUrlPath = "restUrl"
}

final case class FlinkWaitForDuringDeployFinishedConfig(
    enabled: Boolean,
    maxChecks: Option[Int],
    delay: Option[FiniteDuration]
) {

  def toEnabledConfig: Option[EnabledFlinkWaitForDuringDeployFinishedConfig] =
    if (enabled) {
      (maxChecks, delay) match {
        case (Some(definedMaxChecks), Some(definedDelay)) =>
          Some(EnabledFlinkWaitForDuringDeployFinishedConfig(definedMaxChecks, definedDelay))
        case _ =>
          throw new IllegalArgumentException(
            s"Invalid config: $this. If you want to enable waitForDuringDeployFinish option, hou have to define both maxChecks and delay."
          )
      }
    } else {
      None
    }

}

final case class EnabledFlinkWaitForDuringDeployFinishedConfig(maxChecks: Int, delay: FiniteDuration)

final case class ScenarioTestingConfig(
    reuseMiniClusterForScenarioTesting: Boolean = true,
    reuseMiniClusterForScenarioStateVerification: Boolean = true,
    parallelism: Int = 1,
    miniClusterConfig: Configuration = ScenarioTestingConfig.defaultMiniClusterConfig,
    streamExecutionConfig: Configuration = new Configuration
)

object ScenarioTestingConfig {

  import Ficus._

  implicit val flinkConfigurationValueReader: ValueReader[Configuration] =
    Ficus.mapValueReader[String].map(map => Configuration.fromMap(map.asJava))

  private[nussknacker] val defaultMiniClusterConfig: Configuration = {
    val config = new Configuration
    config.set[Integer](RestOptions.PORT, 0)
    // FIXME: reversing flink default order
    config.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")
    // In some setups we create a few Flink DMs. Each of them creates its own mini cluster.
    // To reduce footprint we decrease off-heap memory buffers size and managed memory
    config.set(TaskManagerOptions.NETWORK_MEMORY_MIN, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.NETWORK_MEMORY_MAX, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.MANAGED_MEMORY_SIZE, MemorySize.parse("50m"))
    config
  }

}
