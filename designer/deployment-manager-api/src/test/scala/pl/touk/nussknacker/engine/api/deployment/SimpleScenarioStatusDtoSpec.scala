package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{ScenarioStatusPresentationDetails, ScenarioStatusWithScenarioContext}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

class SimpleScenarioStatusDtoSpec extends AnyFunSpec with Matchers with Inside {

  def createInput(status: StateStatus): ScenarioStatusPresentationDetails =
    SimpleProcessStateDefinitionManager.statusPresentation(
      ScenarioStatusWithScenarioContext(
        StatusDetails(status, None, Some(ExternalDeploymentId("12"))),
        VersionId(1),
        None,
        None,
      )
    )

  it("scenario state should be during deploy") {
    val state = createInput(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
  }

  it("scenario state should be running") {
    val state = createInput(SimpleStateStatus.Running)
    state.allowedActions shouldBe List(ScenarioActionName.Cancel, ScenarioActionName.Pause, ScenarioActionName.Deploy)
  }

  it("scenario state should be finished") {
    val state = createInput(SimpleStateStatus.Finished)
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

}
