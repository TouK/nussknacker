package pl.touk.nussknacker.engine.management

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{
  ScenarioStatusPresentationDetails,
  ScenarioStatusWithScenarioContext
}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus

class FlinkScenarioStatusDtoSpec extends AnyFunSuiteLike with Matchers with Inside {

  def statusPresentation(stateStatus: StateStatus): ScenarioStatusPresentationDetails =
    FlinkProcessStateDefinitionManager.statusPresentation(
      ScenarioStatusWithScenarioContext(
        stateStatus,
        None,
        None,
      )
    )

  test("scenario state should be during deploy") {
    val state = statusPresentation(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe Set(ScenarioActionName.Cancel)
  }

  test("scenario state should be running") {
    val state = statusPresentation(SimpleStateStatus.Running)
    state.allowedActions shouldBe Set(ScenarioActionName.Cancel, ScenarioActionName.Pause, ScenarioActionName.Deploy)
  }

  test("scenario state should be finished") {
    val state = statusPresentation(SimpleStateStatus.Finished)
    state.allowedActions shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

  test("scenario state should be restarting") {
    val state = statusPresentation(SimpleStateStatus.Restarting)
    state.allowedActions shouldBe Set(ScenarioActionName.Cancel)
  }

}
