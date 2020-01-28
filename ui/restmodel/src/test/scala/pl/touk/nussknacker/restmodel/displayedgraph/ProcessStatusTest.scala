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
  private def sampleState(status: StateStatus, version: Long, errors: List[String]): ProcessState = SimpleProcessState(
    DeploymentId("12345"),
    status,
    processVersionId = Option(ProcessVersion(version, ProcessName("process"), "user", None)),
    errors = errors
  )

  private def sampleStatus(status: StateStatus, errors: List[String] = List.empty): ProcessStatus = ProcessStatus.simple(
    deploymentId = Some("12345"),
    status = status,
    errors = errors
  )

  private def sampleProcessAction(versionId: Long, action: ProcessActionType = ProcessActionType.Deploy): Option[ProcessDeploymentAction] =
    Option(ProcessDeploymentAction(versionId, "test", LocalDateTime.now(), "user", action, Map.empty))

  test("display error when not expected version running") {
    ProcessStatus.create(sampleState(SimpleStateStatus.Canceled, 3, List.empty), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Canceled, List("Process deployed in version 3 (by user), but currently not working."))
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 3, List.empty), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Running, List("Process deployed in version 3 (by user), expected version 5."))
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, List.empty), None) shouldBe sampleStatus(SimpleStateStatus.Running,  List("Process deployed in version 5 (by user), should not be deployed."))
  }

  test ("don't display error when process is not running") {
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, List.empty), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Running)
    ProcessStatus.create(sampleState(SimpleStateStatus.DuringDeploy, 3, List.empty), sampleProcessAction(3)) shouldBe sampleStatus(SimpleStateStatus.DuringDeploy)
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 1, List("Failed?")), sampleProcessAction(1)) shouldBe sampleStatus(SimpleStateStatus.Failed, List("Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, List("Failed?")), sampleProcessAction(5)) shouldBe sampleStatus(SimpleStateStatus.Failed, List( "Failed?", "Process deployed in version 3 (by user), but currently not working."))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, List("Failed?")), None) shouldBe sampleStatus(SimpleStateStatus.Failed, List("Failed?"))
  }
}
