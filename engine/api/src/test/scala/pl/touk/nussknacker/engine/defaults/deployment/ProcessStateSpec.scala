package pl.touk.nussknacker.engine.defaults.deployment

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, StateAction, StatusState}

import scala.collection.immutable.List

class ProcessStateSpec extends FunSpec with Matchers with Inside {
  val processStateConfigurator = DefaultProcessStateConfigurator

  def createProcessState(stateStatus: StateStatus) = DefaultProcessState(
    DeploymentId("12"),
    stateStatus,
    Option(ProcessVersion.empty)
  )

  it ("process state should be during deploy") {
    val state = createProcessState(DefaultStateStatus.DuringDeploy)
    processStateConfigurator.isDuringDeploy(state.status) shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel)
  }

  it ("process state should be running") {
    val state = createProcessState(DefaultStateStatus.Running)
    processStateConfigurator.isRunning(state.status) shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel, StateAction.Pause)
  }

  it ("process state should be finished") {
    val state = createProcessState(DefaultStateStatus.Finished)
    processStateConfigurator.isFinished(state.status) shouldBe true
    state.allowedActions shouldBe List(StateAction.Deploy)
  }
}
