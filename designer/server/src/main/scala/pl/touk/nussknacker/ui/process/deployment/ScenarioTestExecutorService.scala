package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, DMTestScenarioCommand}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ScenarioTestExecutorService {

  // TODO: explicit errors in the method signature
  def testProcess(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[Json]]

}

class ScenarioTestExecutorServiceImpl(scenarioResolver: ScenarioResolver, deploymentManager: DeploymentManager)
    extends ScenarioTestExecutorService {

  override def testProcess(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[Json]] = {
    for {
      resolvedScenarioWithErrors <- scenarioResolver.resolveScenario(canonicalProcess)
      resolvedScenario <- resolvedScenarioWithErrors match {
        case Validated.Valid(scenario) => Future.successful(scenario)
        case Validated.Invalid(e)      => Future.failed(new RuntimeException(e.head.toString))
      }
      testResult <- deploymentManager.processCommand(
        DMTestScenarioCommand(processVersion, resolvedScenario, scenarioTestData)
      )
    } yield testResult
  }

}
