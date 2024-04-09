package pl.touk.nussknacker.lite.manager

import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.deployment.{BaseDeploymentManager, DMTestScenarioCommand}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.{ExecutionContext, Future}

trait LiteDeploymentManager extends BaseDeploymentManager {

  protected def modelData: BaseModelData

  protected implicit def executionContext: ExecutionContext

  protected def processTestActionCommand(command: DMTestScenarioCommand): Future[TestProcess.TestResults[Json]] = {
    Future {
      modelData.asInvokableModelData.withThisAsContextClassLoader {
        // TODO: handle scenario testing in RR as well
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(
          modelData.asInvokableModelData,
          command.scenarioTestData,
          command.canonicalProcess
        )
      }
    }
  }

}
