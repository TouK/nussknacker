package pl.touk.nussknacker.engine.management

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inside
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ScenarioActionName, StateStatus, StatusDetails}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

class FlinkProcessStateSpec extends AnyFunSpec with Matchers with Inside {

  def createProcessState(stateStatus: StateStatus): ProcessState =
    FlinkProcessStateDefinitionManager.processState(
      StatusDetails(stateStatus, None, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))
    )

  it("scenario state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe List(ScenarioActionName.Cancel)
  }

  it("scenario state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.allowedActions shouldBe List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
  }

  it("scenario state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

  it("scenario state should be restarting") {
    val state = createProcessState(SimpleStateStatus.Restarting)
    state.allowedActions shouldBe List(ScenarioActionName.Cancel)
  }

}
