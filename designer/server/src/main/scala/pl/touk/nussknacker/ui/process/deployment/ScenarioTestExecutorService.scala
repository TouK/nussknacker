package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DMTestScenarioCommand, DeploymentManager}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.security.api.LoggedUser
import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion

import scala.concurrent.{ExecutionContext, Future}

trait ScenarioTestExecutorService {

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
      resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess))
      testResult <- deploymentManager.processCommand(
        DMTestScenarioCommand(processVersion, resolvedProcess, scenarioTestData)
      )
    } yield testResult
  }

}
