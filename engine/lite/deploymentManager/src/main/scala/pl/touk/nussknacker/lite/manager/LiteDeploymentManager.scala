package pl.touk.nussknacker.lite.manager

import cats.effect.{Resource, SyncIO}
import io.circe.Json
import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment.{BaseDeploymentManager, DMTestScenarioCommand}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.concurrent.{ExecutionContext, Future}

sealed trait LightEngineMode
object StreamingMode       extends LightEngineMode
object RequestResponseMode extends LightEngineMode

trait LiteDeploymentManager extends BaseDeploymentManager {

  protected def modelDataProvider: BaseModelDataProvider

  protected implicit def executionContext: ExecutionContext

  protected def mode: LightEngineMode

  protected def testScenario(command: DMTestScenarioCommand): Future[TestProcess.TestResults[Json]] = {
    Future {
      val currentModelData = modelDataProvider.getCurrentModelData().asInvokableModelData
      currentModelData.withModelClassloaderAsContextClassLoader {
        val testRunner = mode match {
          case StreamingMode       => KafkaTransactionalScenarioInterpreter.testRunner
          case RequestResponseMode => FutureBasedRequestResponseScenarioInterpreter.testRunner
        }

        testRunner.runTest(
          currentModelData,
          JobData(command.canonicalProcess.metaData, command.processVersion),
          command.scenarioTestData,
          command.canonicalProcess
        )
      }
    }
  }

  override def scenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies] =
    Resource.pure(EngineScenarioCompilationDependencies.empty)

}
