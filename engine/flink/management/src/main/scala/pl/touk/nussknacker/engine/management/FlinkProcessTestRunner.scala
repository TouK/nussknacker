package pl.touk.nussknacker.engine.management

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.StaticMethodRunner

import scala.concurrent.Future

class FlinkProcessTestRunner(modelData: ModelData) extends StaticMethodRunner(modelData.modelClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkTestMain", "run") {

  def test[T](processName: ProcessName, canonicalProcess: CanonicalProcess, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future.successful(tryToInvoke(modelData, canonicalProcess, testData, new Configuration(), variableEncoder).asInstanceOf[TestResults[T]])
  }

}
