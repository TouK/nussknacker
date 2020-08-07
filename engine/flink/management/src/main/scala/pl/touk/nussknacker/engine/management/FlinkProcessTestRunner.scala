package pl.touk.nussknacker.engine.management

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.StaticMethodRunner

import scala.concurrent.Future

class FlinkProcessTestRunner(modelData: ModelData) extends StaticMethodRunner(modelData.modelClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkTestMain", "run") {

  def test[T](processName: ProcessName, json: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future.successful(tryToInvoke(modelData, json, testData, new Configuration(), variableEncoder).asInstanceOf[TestResults[T]])
  }

}
