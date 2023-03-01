package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ScenarioTestExecutorService {

  def testProcess[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, variableEncoder: Any => T)
                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]]

}

class ScenarioTestExecutorServiceImpl(scenarioResolver: ScenarioResolver,
                                      dispatcher: DeploymentManagerDispatcher) extends ScenarioTestExecutorService {
  override def testProcess[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, variableEncoder: Any => T)
                             (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]] = {
    for {
      resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess, category))
      manager <- dispatcher.deploymentManager(id.id)
      testResult <- manager.test[T](id.name, resolvedProcess, scenarioTestData, variableEncoder)
    } yield testResult
  }

}
