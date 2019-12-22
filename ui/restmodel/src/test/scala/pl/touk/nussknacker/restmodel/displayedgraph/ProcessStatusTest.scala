package pl.touk.nussknacker.restmodel.displayedgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, ProcessStateCustomPresenter, ProcessStateCustoms, StateAction, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName

class ProcessStatusTest extends FunSuite with Matchers {

  private def getTooltip(status: StateStatus): String = ProcessStateCustomPresenter.presentTooltipMessage(status)

  private def getIcon(status: StateStatus): String = ProcessStateCustomPresenter.presentIcon(status)

  private def sampleState(status: StateStatus, version: Long, errorMessage: Option[String]) = ProcessState.custom(
    DeploymentId("12345"),
    status,
    version = Option(ProcessVersion(version, ProcessName("process"), "user", None)),
    errorMessage = errorMessage
  )

  private def sampleStatus(status: StateStatus, icon: String, tooltip: String, allowedActions: List[StateAction] = List.empty, errorMessage: Option[String] = Option.empty) = ProcessStatus(
    deploymentId = Some("12345"),
    status = status.toString(),
    tooltip = tooltip,
    icon = icon,
    allowedActions = allowedActions,
    startTime = Option.empty,
    attributes = Option.empty,
    errorMessage = errorMessage
  )

  test("display error when not expected version running") {
    ProcessStatus(sampleState(StateStatus.Running, 5, None), Some(5)) shouldBe sampleStatus(StateStatus.Running, getIcon(StateStatus.Running), getTooltip(StateStatus.Running), List(StateAction.Cancel, StateAction.Pause))
    ProcessStatus(sampleState(StateStatus.Running, 3, None), Some(5)) shouldBe sampleStatus(StateStatus.Running, getIcon(StateStatus.Running), getTooltip(StateStatus.Running), List(StateAction.Cancel, StateAction.Pause), Some("Process deployed in version 3 (by user), expected version 5"))
    ProcessStatus(sampleState(StateStatus.Running, 5, None), None) shouldBe sampleStatus(StateStatus.Running, getIcon(StateStatus.Running), getTooltip(StateStatus.Running), List(StateAction.Cancel, StateAction.Pause), Some("Process deployed in version 5 (by user), should not be deployed"))
    ProcessStatus(sampleState(StateStatus.Failed, 1, Some("Failed?")), Some(1)) shouldBe sampleStatus(StateStatus.Failed, getIcon(StateStatus.Failed), getTooltip(StateStatus.Failed), List(StateAction.Deploy), Some("Failed?"))
    ProcessStatus(sampleState(StateStatus.Failed, 3, Some("Failed?")), Some(5)) shouldBe sampleStatus(StateStatus.Failed, getIcon(StateStatus.Failed), getTooltip(StateStatus.Failed), List(StateAction.Deploy), Some("Process deployed in version 3 (by user), expected version 5, Failed?"))
    ProcessStatus(sampleState(StateStatus.Failed, 3, Some("Failed?")), None) shouldBe sampleStatus(StateStatus.Failed, getIcon(StateStatus.Failed), getTooltip(StateStatus.Failed), List(StateAction.Deploy), Some("Process deployed in version 3 (by user), should not be deployed, Failed?"))
    ProcessStatus(sampleState(StateStatus.DuringDeploy, 3, None), Some(3)) shouldBe sampleStatus(StateStatus.DuringDeploy, getIcon(StateStatus.DuringDeploy), getTooltip(StateStatus.DuringDeploy), List(StateAction.Cancel))
  }
}
