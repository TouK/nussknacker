package pl.touk.nussknacker.engine.management

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.util.ReflectUtils.StaticMethodRunner

import scala.concurrent.Future

class FlinkProcessTestRunner(modelData: ModelData) extends StaticMethodRunner(modelData.modelClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkTestMain", "run") {

  def test[T](processId: String, json: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future.successful(tryToInvoke(modelData, json, testData, new Configuration(), variableEncoder).asInstanceOf[TestResults[T]])
  }

}
