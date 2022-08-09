package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.{ Inside}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import scala.collection.immutable.List

class SimpleProcessStateSpec extends AnyFunSpec with Matchers with Inside {

  def createProcessState(stateStatus: StateStatus): ProcessState =
    SimpleProcessStateDefinitionManager.processState(stateStatus, Some(ExternalDeploymentId("12")))

  it ("scenario state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.status.isDuringDeploy shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  }

  it ("scenario state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy)
  }

  it ("scenario state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)
  }
}
