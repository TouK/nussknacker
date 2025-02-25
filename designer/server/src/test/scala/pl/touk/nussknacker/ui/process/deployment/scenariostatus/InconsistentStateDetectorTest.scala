package pl.touk.nussknacker.ui.process.deployment.scenariostatus

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatusDetails, ScenarioActionName}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.deployment.DeploymentId

import java.util.UUID

// TODO: more unit tests, tests on higher level than (resolveScenarioStatus) not extractAtMostOneStatus
class InconsistentStateDetectorTest extends AnyFunSuiteLike with Matchers {

  test("return failed status if two deployments running") {
    val firstDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val secondDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)), None)

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      DeploymentStatusDetails(
        ProblemStateStatus(
          description = "More than one deployment is running.",
          allowedActions = Set(ScenarioActionName.Cancel),
          tooltip = Some(
            s"Expected one job, instead: ${firstDeploymentStatus.deploymentIdUnsafe} - RUNNING, ${secondDeploymentStatus.deploymentIdUnsafe} - RUNNING"
          ),
        ),
        firstDeploymentStatus.deploymentId,
        None
      )
    )
  }

  test("return failed status if two in non-terminal state") {
    val firstDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val secondDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)), None)

    InconsistentStateDetector.extractAtMostOneStatus(List(firstDeploymentStatus, secondDeploymentStatus)) shouldBe Some(
      DeploymentStatusDetails(
        ProblemStateStatus(
          description = "More than one deployment is running.",
          allowedActions = Set(ScenarioActionName.Cancel),
          tooltip = Some(
            s"Expected one job, instead: ${firstDeploymentStatus.deploymentIdUnsafe} - RUNNING, ${secondDeploymentStatus.deploymentIdUnsafe} - RESTARTING"
          ),
        ),
        firstDeploymentStatus.deploymentId,
        None
      )
    )
  }

  test("return running status if cancelled job has last-modification date later then running job") {
    val runningDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Running, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val canceledDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Canceled, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val duringCancelDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.DuringCancel, Some(DeploymentId(UUID.randomUUID().toString)), None)

    InconsistentStateDetector.extractAtMostOneStatus(
      List(runningDeploymentStatus, canceledDeploymentStatus, duringCancelDeploymentStatus)
    ) shouldBe Some(
      DeploymentStatusDetails(
        SimpleStateStatus.Running,
        runningDeploymentStatus.deploymentId,
        None,
      )
    )
  }

  test("return last terminal state if not running") {
    val firstFinishedDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val secondFinishedDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)), None)

    InconsistentStateDetector.extractAtMostOneStatus(
      List(firstFinishedDeploymentStatus, secondFinishedDeploymentStatus)
    ) shouldBe Some(firstFinishedDeploymentStatus)
  }

  test("return non-terminal state if not running") {
    val finishedDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Finished, Some(DeploymentId(UUID.randomUUID().toString)), None)
    val nonTerminalDeploymentStatus =
      DeploymentStatusDetails(SimpleStateStatus.Restarting, Some(DeploymentId(UUID.randomUUID().toString)), None)

    InconsistentStateDetector.extractAtMostOneStatus(
      List(finishedDeploymentStatus, nonTerminalDeploymentStatus)
    ) shouldBe Some(nonTerminalDeploymentStatus)
  }

}
