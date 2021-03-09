package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.{EitherValues, FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}

import scala.collection.immutable.List

class SimpleProcessStateSpec extends FunSpec with Matchers with Inside with EitherValues {

  def createProcessState(stateStatus: StateStatus): ProcessState =
    SimpleProcessState(ExternalDeploymentId("12"), stateStatus)

  it ("process state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.status.isDuringDeploy shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }

  it ("process state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy)
  }

  it ("process state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)
  }
}
