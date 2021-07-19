package pl.touk.nussknacker.engine.management

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.StateStatus

import scala.collection.immutable.List

class FlinkProcessStateSpec extends FunSpec with Matchers with Inside {
  def createProcessState(stateStatus: StateStatus): ProcessState =
    ProcessState("12", stateStatus, Some(ProcessVersion.empty), FlinkProcessStateDefinitionManager)

  it ("scenario state should be during deploy") {
    val state = createProcessState(FlinkStateStatus.DuringDeploy)
    state.status.isDuringDeploy shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }

  it ("scenario state should be running") {
    val state = createProcessState(FlinkStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy)
  }

  it ("scenario state should be finished") {
    val state = createProcessState(FlinkStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)
  }

  it ("scenario state should be restarting") {
    val state = createProcessState(FlinkStateStatus.Restarting)
    state.status.isFinished shouldBe false
    state.status.isRunning shouldBe false
    state.status.isDuringDeploy shouldBe false
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }
}
