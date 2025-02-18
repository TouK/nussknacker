package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.StatusDetails
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.deployment.DeploymentId

import java.util.UUID

class InconsistentStateDetectorTest extends AnyFunSuiteLike with Matchers {

  test("return failed status if two deployments running") {
    val firstDeploymentStatus = StatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)))
    val secondDeploymentStatus =
      StatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      StatusDetails(
        ProblemStateStatus.MultipleJobsRunning,
        firstDeploymentStatus.deploymentId,
        errors = List(
          s"Expected one job, instead: ${firstDeploymentStatus.deploymentIdUnsafe} - RUNNING, ${secondDeploymentStatus.deploymentIdUnsafe} - RUNNING"
        )
      )
    )
  }

  test("return failed status if two in non-terminal state") {
    val firstDeploymentStatus = StatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)))
    val secondDeploymentStatus =
      StatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      StatusDetails(
        ProblemStateStatus.MultipleJobsRunning,
        firstDeploymentStatus.deploymentId,
        errors = List(
          s"Expected one job, instead: ${firstDeploymentStatus.deploymentIdUnsafe} - RUNNING, ${secondDeploymentStatus.deploymentIdUnsafe} - RESTARTING"
        )
      )
    )
  }

  test("return running status if cancelled job has last-modification date later then running job") {
    val runningDeploymentStatus =
      StatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)))
    val canceledDeploymentStatus =
      StatusDetails(SimpleStateStatus.Canceled, Some(DeploymentId(UUID.randomUUID().toString)))
    val duringCancelDeploymentStatus =
      StatusDetails(SimpleStateStatus.DuringCancel, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(
      List(runningDeploymentStatus, canceledDeploymentStatus, duringCancelDeploymentStatus)
    ) shouldBe Some(
      StatusDetails(
        SimpleStateStatus.Running,
        runningDeploymentStatus.deploymentId,
      )
    )
  }

  test("return last terminal state if not running") {
    val firstFinishedDeploymentStatus =
      StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)))
    val secondFinishedDeploymentStatus =
      StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(
      List(firstFinishedDeploymentStatus, secondFinishedDeploymentStatus)
    ) shouldBe Some(firstFinishedDeploymentStatus)
  }

  test("return non-terminal state if not running") {
    val finishedDeploymentStatus =
      StatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)))
    val nonTerminalDeploymentStatus =
      StatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(
      List(finishedDeploymentStatus, nonTerminalDeploymentStatus)
    ) shouldBe Some(nonTerminalDeploymentStatus)
  }

}
