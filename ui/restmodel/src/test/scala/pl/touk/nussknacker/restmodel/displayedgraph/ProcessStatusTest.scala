package pl.touk.nussknacker.restmodel.displayedgraph

import java.time.LocalDateTime

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, ProcessActionType, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.processdetails.ProcessDeploymentAction

class ProcessStatusTest extends FunSuite with Matchers {
  private def sampleState(status: StateStatus, version: Long, errorMessage: Option[String]): ProcessState = SimpleProcessState(
    DeploymentId("12345"),
    status,
    processVersionId = Option(ProcessVersion(version, ProcessName("process"), "user", None)),
    errorMessage = errorMessage
  )

  private def sampleStatus(status: StateStatus, errorMessage: Option[String] = Option.empty): ProcessStatus = ProcessStatus.simple(
    deploymentId = Some("12345"),
    status = status,
    errors = errorMessage
  )

  private def sampleProcessAction(versionId: Long, action: ProcessActionType = ProcessActionType.Deploy): Option[ProcessDeploymentAction] =
    Option(ProcessDeploymentAction(versionId, "test", LocalDateTime.now(), "user", action, Map.empty))

  test("display error when not expected version running") {
    ProcessStatus.create(sampleState(SimpleStateStatus.Canceled, 3, None), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Canceled, Some("Process deployed in version 3 (by user), but currently not working"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 3, None), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Running, Some("Process deployed in version 3 (by user), expected version 5"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, None), None) shouldBe sampleStatus(SimpleStateStatus.Running,  Some("Process deployed in version 5 (by user), should not be deployed"))
  }

  test ("don't display error when process is not running") {
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, None), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Running)
    ProcessStatus.create(sampleState(SimpleStateStatus.DuringDeploy, 3, None), sampleProcessAction(3)) shouldBe sampleStatus(SimpleStateStatus.DuringDeploy)
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 1, Some("Failed?")), sampleProcessAction(1)) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, Some("Failed?")), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Process deployed in version 3 (by user), but currently not working, Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, Some("Failed?")), None) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Failed?"))
  }
}
