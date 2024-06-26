package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DMTestScenarioCommand, DeploymentManager}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.security.api.LoggedUser
import io.circe.Json
import scala.concurrent.{ExecutionContext, Future}

trait ScenarioTestExecutorService {

  def testProcess(
      id: ProcessIdWithName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[Json]]

}

class ScenarioTestExecutorServiceImpl(scenarioResolver: ScenarioResolver, deploymentManager: DeploymentManager)
    extends ScenarioTestExecutorService {

  override def testProcess(
      id: ProcessIdWithName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[Json]] = {
    for {
      resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess))
      testResult <- deploymentManager.processCommand(DMTestScenarioCommand(id.name, resolvedProcess, scenarioTestData))
    } yield testResult
  }

}
