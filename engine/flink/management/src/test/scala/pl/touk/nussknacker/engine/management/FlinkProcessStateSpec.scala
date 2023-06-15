package pl.touk.nussknacker.engine.management

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inside
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

class FlinkProcessStateSpec extends AnyFunSpec with Matchers with Inside {
  def createProcessState(stateStatus: StateStatus): ProcessState =
    FlinkProcessStateDefinitionManager.processState(stateStatus, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

  it ("scenario state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }

  it ("scenario state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.allowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy)
  }

  it ("scenario state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive, ProcessActionType.Rename)
  }

  it ("scenario state should be restarting") {
    val state = createProcessState(SimpleStateStatus.Restarting)
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }
}
