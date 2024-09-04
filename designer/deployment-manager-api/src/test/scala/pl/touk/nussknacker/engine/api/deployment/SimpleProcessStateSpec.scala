package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import scala.collection.immutable.List

class SimpleProcessStateSpec extends AnyFunSpec with Matchers with Inside {

  def createProcessState(stateStatus: StateStatus): ProcessState =
    SimpleProcessStateDefinitionManager.processState(StatusDetails(stateStatus, None, Some(ExternalDeploymentId("12"))))

  it("scenario state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
  }

  it("scenario state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.allowedActions shouldBe List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
  }

  it("scenario state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

}
