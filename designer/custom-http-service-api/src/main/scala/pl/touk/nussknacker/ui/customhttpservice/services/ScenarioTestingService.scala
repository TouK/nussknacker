package pl.touk.nussknacker.ui.customhttpservice.services

import cats.effect.IO
import io.circe.Json
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait ScenarioTestingService {

  def validateAccess(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
  )(implicit user: LoggedUser): IO[Either[ScenarioTestingError, Unit]]

  def performTestCase(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      testCase: TestCase,
  )(implicit user: LoggedUser): IO[Either[ScenarioTestingError, Json]]

}

sealed trait ScenarioTestingError

object ScenarioTestingError {
  final case class ScenarioNotFound(name: ProcessName)  extends ScenarioTestingError
  final case class Unauthorized()                       extends ScenarioTestingError
  final case class TestExecutionFailed(message: String) extends ScenarioTestingError
}
