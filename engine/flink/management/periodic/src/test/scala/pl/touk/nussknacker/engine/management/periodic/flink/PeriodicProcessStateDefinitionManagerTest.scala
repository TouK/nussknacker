package pl.touk.nussknacker.engine.management.periodic.flink

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.common.periodic.PeriodicProcessService.{DeploymentStatus, PeriodicProcessStatus}
import pl.touk.nussknacker.engine.common.periodic.PeriodicProcessStateDefinitionManager.statusTooltip
import pl.touk.nussknacker.engine.common.periodic.PeriodicStateStatus
import pl.touk.nussknacker.engine.common.periodic.PeriodicStateStatus.ScheduledStatus

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

class PeriodicProcessStateDefinitionManagerTest extends AnyFunSuite with Matchers {

  private val fooProcessId = PeriodicProcessId(123L)

  private val fooRunAt = LocalDateTime.of(2023, 1, 1, 10, 0)

  private val fooCreatedAt = fooRunAt.minusMinutes(5)

  private val notNamedScheduleId = ScheduleId(fooProcessId, ScheduleName(None))

  private val nextDeploymentId = new AtomicLong()

  private val nextScheduleId = new AtomicLong()

  test("display periodic deployment status for not named schedule") {
    val deploymentStatus = DeploymentStatus(
      generateDeploymentId,
      notNamedScheduleId,
      fooCreatedAt,
      fooRunAt,
      PeriodicProcessDeploymentStatus.Scheduled,
      processActive = true,
      None
    )
    val status = PeriodicProcessStatus(List(deploymentStatus), List.empty)
    statusTooltip(status) shouldEqual "Scheduled at: 2023-01-01 10:00 status: Scheduled"
  }

  test("display sorted periodic deployment status for named schedules") {
    val firstScheduleId = generateScheduleId
    val firstDeploymentStatus = DeploymentStatus(
      generateDeploymentId,
      firstScheduleId,
      fooCreatedAt.minusMinutes(1),
      fooRunAt,
      PeriodicProcessDeploymentStatus.Deployed,
      processActive = true,
      None
    )
    val secScheduleId = generateScheduleId
    val secDeploymentStatus = DeploymentStatus(
      generateDeploymentId,
      secScheduleId,
      fooCreatedAt,
      fooRunAt,
      PeriodicProcessDeploymentStatus.Scheduled,
      processActive = true,
      None
    )
    val status = PeriodicProcessStatus(List(firstDeploymentStatus, secDeploymentStatus), List.empty)
    statusTooltip(status) shouldEqual
      s"""Schedule ${secScheduleId.scheduleName.display} scheduled at: 2023-01-01 10:00 status: Scheduled,
         |Schedule ${firstScheduleId.scheduleName.display} scheduled at: 2023-01-01 10:00 status: Deployed""".stripMargin
  }

  test("not display custom tooltip for perform single execution when latest version is deployed") {
    PeriodicStateStatus.customActionTooltips(
      ProcessStatus(
        stateStatus = ScheduledStatus(nextRunAt = LocalDateTime.now()),
        latestVersionId = VersionId(5),
        deployedVersionId = Some(VersionId(5)),
        currentlyPresentedVersionId = Some(VersionId(5)),
      )
    ) shouldEqual Map.empty
  }

  test(
    "display custom tooltip for perform single execution when deployed version is different than currently displayed"
  ) {
    PeriodicStateStatus.customActionTooltips(
      ProcessStatus(
        stateStatus = ScheduledStatus(nextRunAt = LocalDateTime.now()),
        latestVersionId = VersionId(5),
        deployedVersionId = Some(VersionId(4)),
        currentlyPresentedVersionId = Some(VersionId(5)),
      )
    ) shouldEqual Map(
      ScenarioActionName.RunOffSchedule -> "Version 4 is deployed, but different version 5 is displayed"
    )
  }

  test("display custom tooltip for perform single execution in CANCELED state") {
    PeriodicStateStatus.customActionTooltips(
      ProcessStatus(
        stateStatus = SimpleStateStatus.Canceled,
        latestVersionId = VersionId(5),
        deployedVersionId = Some(VersionId(4)),
        currentlyPresentedVersionId = Some(VersionId(5)),
      )
    ) shouldEqual Map(
      ScenarioActionName.RunOffSchedule -> "Disabled for CANCELED status."
    )
  }

  private def generateDeploymentId = PeriodicProcessDeploymentId(nextDeploymentId.getAndIncrement())

  private def generateScheduleId = ScheduleId(fooProcessId, generateScheduleName)

  private def generateScheduleName = ScheduleName(Some("schedule_" + nextScheduleId.getAndIncrement()))

}
