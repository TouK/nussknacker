package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, TestScenarioCommand}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ScenarioTestExecutorService {

  /**
    * NU-1455: Variable encoding must be done on the engine, because of classLoader's problems
    */
  def testProcess[T](
      id: ProcessIdWithName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      variableEncoder: Any => T,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]]

}

class ScenarioTestExecutorServiceImpl(scenarioResolver: ScenarioResolver, deploymentManager: DeploymentManager)
    extends ScenarioTestExecutorService {

  override def testProcess[T](
      id: ProcessIdWithName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      variableEncoder: Any => T,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]] = {
    for {
      resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess))
      testResult <- deploymentManager.processCommand(
        TestScenarioCommand(id.name, resolvedProcess, scenarioTestData, variableEncoder)
      )
    } yield testResult
  }

}
