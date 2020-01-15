package pl.touk.nussknacker.restmodel.displayedgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName

class ProcessStatusTest extends FunSuite with Matchers {
  private def sampleState(status: StateStatus, version: Long, errorMessage: Option[String]): ProcessState = SimpleProcessState(
    DeploymentId("12345"),
    status,
    version = Option(ProcessVersion(version, ProcessName("process"), "user", None)),
    errorMessage = errorMessage
  )

  private def sampleStatus(status: StateStatus, errorMessage: Option[String] = Option.empty) = ProcessStatus.simple(
    deploymentId = Some("12345"),
    status = status,
    errorMessage = errorMessage
  )

  test("display error when not expected version running") {
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, None), Some(5)) shouldBe sampleStatus(SimpleStateStatus.Running)
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 3, None), Some(5)) shouldBe sampleStatus(SimpleStateStatus.Running, Some("Process deployed in version 3 (by user), expected version 5"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Running, 5, None), None) shouldBe sampleStatus(SimpleStateStatus.Running,  Some("Process deployed in version 5 (by user), should not be deployed"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 1, Some("Failed?")), Some(1)) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, Some("Failed?")), Some(5)) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Process deployed in version 3 (by user), expected version 5, Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.Failed, 3, Some("Failed?")), None) shouldBe sampleStatus(SimpleStateStatus.Failed, Some("Process deployed in version 3 (by user), should not be deployed, Failed?"))
    ProcessStatus.create(sampleState(SimpleStateStatus.DuringDeploy, 3, None), Some(3)) shouldBe sampleStatus(SimpleStateStatus.DuringDeploy)
  }
}
