package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioParallelismOverride.Ops
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster.LegacyFallbackToSingleUseMiniClusterHandler
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FlinkMiniClusterScenarioStateVerifier(
    modelData: BaseModelData,
    sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices]
) extends LazyLogging {

  private val StateVerificationParallelism = 1

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[Unit](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkScenarioStateVerificationJob",
    "run"
  )

  private val legacyFallbackToSingleUseMiniClusterHandler =
    new LegacyFallbackToSingleUseMiniClusterHandler(modelData.modelClassLoader, "scenario state verification")

  def verify(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      savepointPath: String
  ): Try[Unit] = {
    legacyFallbackToSingleUseMiniClusterHandler.withSharedOrSingleUseCluster(sharedMiniClusterServicesOpt, scenario) {
      miniClusterWithServices =>
        val scenarioWithOverrodeParallelism = sharedMiniClusterServicesOpt
          .map(_ => scenario.overrideParallelism(StateVerificationParallelism))
          .getOrElse(scenario)
        val scenarioName = processVersion.processName
        miniClusterWithServices.withAttachedStreamExecutionEnvironment { env =>
          try {
            logger.info(s"Starting to verify $scenarioName")
            jobInvoker.invokeStaticMethod(
              modelData,
              scenarioWithOverrodeParallelism,
              processVersion,
              savepointPath,
              env
            )
            logger.info(s"Verification of $scenarioName successful")
            Success(())
          } catch {
            case NonFatal(e) =>
              logger.info(s"Failed to verify $scenarioName", e)
              Failure(
                new IllegalArgumentException(
                  "State is incompatible, please stop scenario and start again with clean state",
                  e
                )
              )
          }
        }
    }
  }

}
