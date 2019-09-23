package pl.touk.nussknacker.restmodel.displayedgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, RunningState}
import pl.touk.nussknacker.engine.api.process.ProcessName

class ProcessStatusTest extends FunSuite with Matchers {

  private def sampleState(version: Option[Long],
                          message: Option[String], state: RunningState.Value) = ProcessState(DeploymentId("12345"), state, "testStatus", 1000L,
                            version.map(ProcessVersion(_, ProcessName(""), "user1", None)), message)

  private def sampleStatus(isRunning: Boolean,
                           isDeployInProgress: Boolean, message: Option[String]) = ProcessStatus(Some("12345"), "testStatus", 1000L, isRunning, isDeployInProgress, message)

  test("display error when not expected version running") {


    ProcessStatus(sampleState(None, None, RunningState.Running), None) shouldBe sampleStatus(isRunning = true, isDeployInProgress = false, None)
    ProcessStatus(sampleState(None, None, RunningState.Running), Some(5)) shouldBe sampleStatus(isRunning = true, isDeployInProgress = false, None)
    ProcessStatus(sampleState(Some(5), None, RunningState.Running), Some(5)) shouldBe sampleStatus(isRunning = true, isDeployInProgress = false, None)
    ProcessStatus(sampleState(Some(3), None, RunningState.Running), Some(5)) shouldBe sampleStatus(isRunning = false, isDeployInProgress = false, Some("Process deployed in version 3, expected version 5"))
    ProcessStatus(sampleState(None, Some("Failed?"), RunningState.Error), None) shouldBe sampleStatus(isRunning = false, isDeployInProgress = false, Some("Failed?"))
    ProcessStatus(sampleState(Some(3), Some("Failed?"), RunningState.Running), Some(5)) shouldBe sampleStatus(isRunning = false, isDeployInProgress = false, Some("Process deployed in version 3, expected version 5, Failed?"))
    ProcessStatus(sampleState(Some(3), None, RunningState.Running), None) shouldBe sampleStatus(isRunning = false, isDeployInProgress = false, Some("Process deployed in version 3, should not be deployed"))

  }

}
