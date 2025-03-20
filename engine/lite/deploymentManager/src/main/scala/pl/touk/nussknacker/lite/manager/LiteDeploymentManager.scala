package pl.touk.nussknacker.lite.manager

import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.deployment.{BaseDeploymentManager, DMTestScenarioCommand}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.{ExecutionContext, Future}

trait LiteDeploymentManager extends BaseDeploymentManager {

  protected def modelDataProvider: BaseModelDataProvider

  protected implicit def executionContext: ExecutionContext

  protected def testScenario(command: DMTestScenarioCommand): Future[TestProcess.TestResults[Json]] = {
    Future {
      val currentModelData = modelDataProvider.getCurrentModelData().asInvokableModelData
      currentModelData.withThisAsContextClassLoader {
        // TODO: handle scenario testing in RR as well
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(
          currentModelData,
          JobData(command.canonicalProcess.metaData, command.processVersion),
          command.scenarioTestData,
          command.canonicalProcess
        )
      }
    }
  }

}
