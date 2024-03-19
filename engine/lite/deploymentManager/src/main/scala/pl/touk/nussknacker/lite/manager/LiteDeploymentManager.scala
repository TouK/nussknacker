package pl.touk.nussknacker.lite.manager

import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.deployment.BaseDeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.{ExecutionContext, Future}

trait LiteDeploymentManager extends BaseDeploymentManager {

  protected def modelData: BaseModelData

  protected implicit def executionContext: ExecutionContext

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  ): Future[TestProcess.TestResults[Json]] = {
    Future {
      modelData.asInvokableModelData.withThisAsContextClassLoader {
        // TODO: handle scenario testing in RR as well
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(
          modelData.asInvokableModelData,
          scenarioTestData,
          canonicalProcess,
        )
      }
    }
  }

}
