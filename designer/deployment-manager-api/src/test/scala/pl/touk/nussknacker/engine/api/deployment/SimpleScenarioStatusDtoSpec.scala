package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{
  ScenarioStatusPresentationDetails,
  ScenarioStatusWithScenarioContext
}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

class SimpleScenarioStatusDtoSpec extends AnyFunSuiteLike with Matchers with Inside {

  def statusPresentation(status: StateStatus): ScenarioStatusPresentationDetails =
    SimpleProcessStateDefinitionManager.statusPresentation(
      ScenarioStatusWithScenarioContext(
        status,
        None,
        None,
      )
    )

  test("scenario state should be during deploy") {
    val state = statusPresentation(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
  }

  test("scenario state should be running") {
    val state = statusPresentation(SimpleStateStatus.Running)
    state.allowedActions shouldBe Set(ScenarioActionName.Cancel, ScenarioActionName.Pause, ScenarioActionName.Deploy)
  }

  test("scenario state should be finished") {
    val state = statusPresentation(SimpleStateStatus.Finished)
    state.allowedActions shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

}
