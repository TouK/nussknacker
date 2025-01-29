package pl.touk.nussknacker.engine.management.scenariotesting

import io.circe.Json
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}

class FlinkProcessTestRunner(modelData: ModelData, miniClusterWrapperOpt: Option[AutoCloseable]) {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside flinkDeploymentManager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which Flink DM is loaded
  private val mainRunner = new ReflectiveMethodInvoker[TestResults[Json]](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkTestMain",
    "run"
  )

  def runTestsAsync(canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData)(
      implicit ec: ExecutionContext
  ): Future[TestResults[Json]] =
    Future {
      runTests(canonicalProcess, scenarioTestData)
    }

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def runTests(canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] =
    mainRunner.invokeStaticMethod(
      miniClusterWrapperOpt,
      modelData,
      canonicalProcess,
      scenarioTestData
    )

}
