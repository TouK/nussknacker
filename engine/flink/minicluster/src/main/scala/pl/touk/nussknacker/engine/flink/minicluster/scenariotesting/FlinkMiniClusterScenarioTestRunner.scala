package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import cats.effect.IO
import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioParallelismOverride._
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster.LegacyFallbackToSingleUseMiniClusterHandler
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker
import cats.effect.unsafe.implicits.global

import scala.concurrent.{ExecutionContext, Future}

class FlinkMiniClusterScenarioTestRunner(
    modelData: BaseModelData,
    sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices]
) {

  // TODO: configurable?
  private val ScenarioTestingParallelism = 1

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[Future[TestResults[Json]]](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkScenarioTestingJob",
    "run"
  )

  private val legacyFallbackToSingleUseMiniClusterHandler =
    new LegacyFallbackToSingleUseMiniClusterHandler(modelData.modelClassLoader, "scenario testing")

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def runTests(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData)(
      implicit ec: ExecutionContext
  ): Future[TestResults[Json]] = {
    legacyFallbackToSingleUseMiniClusterHandler.withSharedOrSingleUseClusterAsync(
      sharedMiniClusterServicesOpt,
      scenario
    ) { miniClusterWithServices =>
      val scenarioWithOverrodeParallelism = sharedMiniClusterServicesOpt
        .map(_ => scenario.overrideParallelism(ScenarioTestingParallelism))
        .getOrElse(scenario)
      miniClusterWithServices
        .createStreamExecutionEnvironment(attached = true)
        .use { env =>
          IO.fromFuture(
            IO(
              jobInvoker.invokeStaticMethod(
                modelData,
                scenarioWithOverrodeParallelism,
                scenarioTestData,
                env
              )
            )
          )
        }
        .unsafeToFuture()
    }
  }

}
