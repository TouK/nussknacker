package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatusDetails, ScenarioActionName}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus.{
  GeneralProblemStateStatus,
  MultipleJobsRunning
}
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.deployment.DeploymentId

import java.time.Instant
import java.util.UUID

// TODO: more unit tests, tests on higher level than (resolveScenarioStatus) not extractAtMostOneStatus
class InconsistentStateDetectorTest extends AnyFunSuiteLike with Matchers {

  private val instant = Instant.now()

  test("return failed status if two deployments running") {
    val firstDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Running(VersionId(1), instant),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )
    val secondDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Running(VersionId(2), instant.plusSeconds(100)),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      DeploymentStatusDetails(
        status = MultipleJobsRunning(
          firstDeploymentStatus.deploymentIdUnsafe  -> SimpleStateStatus.Running(VersionId(1), instant),
          secondDeploymentStatus.deploymentIdUnsafe -> SimpleStateStatus.Running(VersionId(1), instant)
        ),
        deploymentId = firstDeploymentStatus.deploymentId,
      )
    )
  }

  test("return failed status if two in non-terminal state") {
    val firstDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Running(VersionId(1), instant),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )
    val secondDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      DeploymentStatusDetails(
        status = MultipleJobsRunning(
          firstDeploymentStatus.deploymentIdUnsafe  -> SimpleStateStatus.Running(VersionId(1), instant),
          secondDeploymentStatus.deploymentIdUnsafe -> SimpleStateStatus.Running(VersionId(1), instant)
        ),
        deploymentId = firstDeploymentStatus.deploymentId,
      )
    )
  }

  test("return running status if cancelled job has last-modification date later then running job") {
    val runningDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Running(VersionId(1), instant),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )
    val canceledDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Canceled, Some(DeploymentId(UUID.randomUUID().toString)))
    val duringCancelDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.DuringCancel, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(
      List(runningDeploymentStatus, canceledDeploymentStatus, duringCancelDeploymentStatus)
    ) shouldBe Some(
      DeploymentStatusDetails(
        SimpleStateStatus.Running(VersionId(1), instant),
        runningDeploymentStatus.deploymentId,
      )
    )
  }

  test("return last terminal state if not running") {
    val firstFinishedDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Finished(VersionId(1)),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )
    val secondFinishedDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Finished(VersionId(1)),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )

    InconsistentStateDetector.extractAtMostOneStatus(
      List(firstFinishedDeploymentStatus, secondFinishedDeploymentStatus)
    ) shouldBe Some(firstFinishedDeploymentStatus)
  }

  test("return non-terminal state if not running") {
    val finishedDeploymentStatus =
      DeploymentStatusDetails(
        SimpleStateStatus.Finished(VersionId(1)),
        Some(DeploymentId(UUID.randomUUID().toString)),
      )
    val nonTerminalDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)))

    InconsistentStateDetector.extractAtMostOneStatus(
      List(finishedDeploymentStatus, nonTerminalDeploymentStatus)
    ) shouldBe Some(nonTerminalDeploymentStatus)
  }

}
