package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}

import scala.collection.immutable.List

class SimpleProcessStateSpec extends FunSpec with Matchers with Inside {
  def createProcessState(stateStatus: StateStatus): ProcessState =
    SimpleProcessState(DeploymentId("12"), stateStatus)

  it ("process state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(StateAction.Cancel, StateAction.Pause)
  }

  it ("process state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(StateAction.Deploy)
  }
}
