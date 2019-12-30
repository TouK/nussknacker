package pl.touk.nussknacker.restmodel.displayedgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, StateAction}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.defaults.deployment.{DefaultStateStatus, DefaultProcessState}

class ProcessStatusTest extends FunSuite with Matchers {

  private def sampleState(status: StateStatus, version: Long, errorMessage: Option[String]) = DefaultProcessState(
    DeploymentId("12345"),
    status,
    version = Option(ProcessVersion(version, ProcessName("process"), "user", None)),
    errorMessage = errorMessage
  )

  private def sampleStatus(status: StateStatus, allowedActions: List[StateAction] = List.empty, errorMessage: Option[String] = Option.empty) = ProcessStatus(
    deploymentId = Some("12345"),
    status = status.toString(),
    allowedActions = allowedActions,
    startTime = Option.empty,
    attributes = Option.empty,
    errorMessage = errorMessage
  )

  test("display error when not expected version running") {
    ProcessStatus(sampleState(DefaultStateStatus.Running, 5, None), Some(5)) shouldBe sampleStatus(DefaultStateStatus.Running, List(StateAction.Cancel, StateAction.Pause))
    ProcessStatus(sampleState(DefaultStateStatus.Running, 3, None), Some(5)) shouldBe sampleStatus(DefaultStateStatus.Running,  List(StateAction.Cancel, StateAction.Pause), Some("Process deployed in version 3 (by user), expected version 5"))
    ProcessStatus(sampleState(DefaultStateStatus.Running, 5, None), None) shouldBe sampleStatus(DefaultStateStatus.Running, List(StateAction.Cancel, StateAction.Pause), Some("Process deployed in version 5 (by user), should not be deployed"))
    ProcessStatus(sampleState(DefaultStateStatus.Failed, 1, Some("Failed?")), Some(1)) shouldBe sampleStatus(DefaultStateStatus.Failed, List(StateAction.Deploy), Some("Failed?"))
    ProcessStatus(sampleState(DefaultStateStatus.Failed, 3, Some("Failed?")), Some(5)) shouldBe sampleStatus(DefaultStateStatus.Failed, List(StateAction.Deploy), Some("Process deployed in version 3 (by user), expected version 5, Failed?"))
    ProcessStatus(sampleState(DefaultStateStatus.Failed, 3, Some("Failed?")), None) shouldBe sampleStatus(DefaultStateStatus.Failed, List(StateAction.Deploy), Some("Process deployed in version 3 (by user), should not be deployed, Failed?"))
    ProcessStatus(sampleState(DefaultStateStatus.DuringDeploy, 3, None), Some(3)) shouldBe sampleStatus(DefaultStateStatus.DuringDeploy, List(StateAction.Cancel))
  }
}
