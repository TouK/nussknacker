package pl.touk.nussknacker.ui.process.periodic

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService.{DeploymentStatus, PeriodicProcessStatus}
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessStateDefinitionManager.statusTooltip
import pl.touk.nussknacker.ui.process.periodic.model.{
  PeriodicProcessDeploymentId,
  PeriodicProcessDeploymentState,
  PeriodicProcessDeploymentStatus,
  PeriodicProcessId,
  ScheduleId,
  ScheduleName
}

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

  private def generateDeploymentId = PeriodicProcessDeploymentId(nextDeploymentId.getAndIncrement())

  private def generateScheduleId = ScheduleId(fooProcessId, generateScheduleName)

  private def generateScheduleName = ScheduleName(Some("schedule_" + nextScheduleId.getAndIncrement()))

}
