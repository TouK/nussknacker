package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.util.ReflectUtils.StaticMethodRunner

import scala.concurrent.Future

class FlinkProcessTestRunner(modelData: ModelData) extends StaticMethodRunner(modelData.jarClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkTestMain", "run") {

  def test(processId: String, json: String, testData: TestData): Future[TestResults] = {
    Future.successful(tryToInvoke(modelData, json, testData).asInstanceOf[TestResults])
  }

}
