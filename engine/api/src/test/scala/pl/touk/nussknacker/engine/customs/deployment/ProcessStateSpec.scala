package pl.touk.nussknacker.engine.customs.deployment

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, StateAction, StatusState}

import scala.collection.immutable.List

class ProcessStateSpec extends FunSpec with Matchers with Inside {
  def createProcessState(stateStatus: StateStatus) = ProcessState(
    DeploymentId("12"),
    stateStatus,
    Option(ProcessVersion.empty),
    ProcessStateCustomConfigurator.getStatusActions(stateStatus)
  )

  it ("process state should be during deploy") {
    val state = createProcessState(CustomStateStatus.DuringDeploy)
    StatusState.isDuringDeploy(state) shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel)
  }

  it ("process state should be running") {
    val state = createProcessState(CustomStateStatus.Running)
    StatusState.isRunning(state) shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel, StateAction.Pause)
  }

  it ("process state should be finished") {
    val state = createProcessState(CustomStateStatus.Finished)
    StatusState.isFinished(state) shouldBe true
    state.allowedActions shouldBe List(StateAction.Deploy)
  }
}
