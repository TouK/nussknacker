package pl.touk.nussknacker.engine.management

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, StateAction}
import pl.touk.nussknacker.engine.api.deployment.StateStatus

import scala.collection.immutable.List

class FlinkProcessStateSpec extends FunSpec with Matchers with Inside {
  def createProcessState(stateStatus: StateStatus): ProcessState =
    ProcessState("12", stateStatus, Some(ProcessVersion.empty), FlinkProcessStateDefinitionManager.statusActions(stateStatus))

  it ("process state should be during deploy") {
    val state = createProcessState(FlinkStateStatus.DuringDeploy)
    state.status.isDuringDeploy shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel)
  }

  it ("process state should be running") {
    val state = createProcessState(FlinkStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel, StateAction.Pause)
  }

  it ("process state should be finished") {
    val state = createProcessState(FlinkStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(StateAction.Deploy)
  }

  it ("process state should be restarting") {
    val state = createProcessState(FlinkStateStatus.Restarting)
    state.status.isFinished shouldBe false
    state.status.isRunning shouldBe false
    state.status.isDuringDeploy shouldBe false
    state.allowedActions shouldBe List(StateAction.Cancel)
  }
}
