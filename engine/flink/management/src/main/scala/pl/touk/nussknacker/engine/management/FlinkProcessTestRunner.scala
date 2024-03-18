package pl.touk.nussknacker.engine.management

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.StaticMethodRunner

import scala.concurrent.{ExecutionContext, Future}

class FlinkProcessTestRunner(modelData: ModelData)
    extends StaticMethodRunner(
      modelData.modelClassLoader.classLoader,
      "pl.touk.nussknacker.engine.process.runner.FlinkTestMain",
      "run"
    ) {

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def test[T](canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T)(
      implicit ec: ExecutionContext
  ): Future[TestResults[T]] =
    Future {
      tryToInvoke(modelData, canonicalProcess, scenarioTestData, new Configuration(), variableEncoder)
        .asInstanceOf[TestResults[T]]
    }

}
