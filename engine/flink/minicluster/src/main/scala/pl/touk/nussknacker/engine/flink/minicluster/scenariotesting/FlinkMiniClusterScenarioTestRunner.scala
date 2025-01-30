package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}

class FlinkMiniClusterScenarioTestRunner(
    modelData: BaseModelData,
    miniClusterWithServicesOpt: Option[FlinkMiniClusterWithServices]
) {

  // TODO: configurable?
  private val ScenarioTestingParallelism = 1

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val mainRunner = new ReflectiveMethodInvoker[Future[TestResults[Json]]](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkTestMain",
    "run"
  )

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def runTests(canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData)(
      implicit ec: ExecutionContext
  ): Future[TestResults[Json]] = {
    val streamExecutionEnvWithParallelismOverride = miniClusterWithServicesOpt.map(miniClusterWithServices =>
      (miniClusterWithServices.createStreamExecutionEnvironment(attached = true), ScenarioTestingParallelism)
    )
    val resultFuture = mainRunner.invokeStaticMethod(
      streamExecutionEnvWithParallelismOverride,
      modelData,
      canonicalProcess,
      scenarioTestData
    )
    resultFuture.onComplete { _ =>
      streamExecutionEnvWithParallelismOverride.foreach(_._1.close())
    }
    resultFuture
  }

}
