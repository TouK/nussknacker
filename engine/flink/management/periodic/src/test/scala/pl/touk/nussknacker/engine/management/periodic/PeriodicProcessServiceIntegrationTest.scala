package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{FinishedStateStatus, RunningStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.HsqlProcessRepository
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.temporal.ChronoUnit
import java.time.{Clock, Duration, Instant, LocalDateTime, ZoneId}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

//Integration test with in-memory hsql
class PeriodicProcessServiceIntegrationTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")

  private val startTime = Instant.parse("2021-04-06T13:18:00Z")

  //we truncate to millis, as HSQL stores with that precision...
  private def fixedClock(instant: Instant) =
    Clock.tick(Clock.fixed(instant, ZoneId.systemDefault()), Duration.ofMillis(1))

  private def localTime(instant: Instant) = LocalDateTime.now(fixedClock(instant))

  //every hour
  private val cron = CronScheduleProperty("0 0 * * * ?")

  class Fixture {
    val hsqlRepo: HsqlProcessRepository = HsqlProcessRepository.prepare
    val delegateProcessManagerStub = new ProcessManagerStub
    val jarManagerStub = new JarManagerStub
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    var failListener = false
    def periodicProcessService(currentTime: Instant) = new PeriodicProcessService(
      delegateProcessManager = delegateProcessManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = hsqlRepo.forClock(fixedClock(currentTime)),
      new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = {
          case k if failListener => throw new Exception(s"$k was ordered to fail")
          case k => events.append(k)
        }
      }, DefaultAdditionalDeploymentDataProvider, fixedClock(currentTime)
    )
  }

  test("base flow test") {
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)

    var currentTime = startTime

    val f = new Fixture
    def service = f.periodicProcessService(currentTime)

    service.schedule(cron,
      ProcessVersion.empty.copy(processName = processName), "{}").futureValue

    val processScheduled = service.getNextScheduledDeployment(processName).futureValue.get

    processScheduled.state shouldBe PeriodicProcessDeploymentState(None, None, PeriodicProcessDeploymentStatus.Scheduled)
    processScheduled.runAt shouldBe localTime(expectedScheduleTime)

    currentTime = timeToTriggerCheck
    
    val toDeploy = service.findToBeDeployed.futureValue.loneElement
    service.deploy(toDeploy).futureValue

    val processDeployed = service.getNextScheduledDeployment(processName).futureValue.get
    processDeployed.id shouldBe processScheduled.id
    processDeployed.state shouldBe PeriodicProcessDeploymentState(Some(LocalDateTime.now(fixedClock(timeToTriggerCheck))), None, PeriodicProcessDeploymentStatus.Deployed)
    processDeployed.runAt shouldBe localTime(expectedScheduleTime)

    service.deactivate(processName).futureValue
    service.getNextScheduledDeployment(processName).futureValue shouldBe None
  }

  test("handle multiple schedules") {
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val timeToTrigger = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    val f = new Fixture
    def service = f.periodicProcessService(currentTime)


    service.schedule(MultipleScheduleProperty(Map(
      "scheduleMinute5" -> CronScheduleProperty("0 5 * * * ?"),
      "scheduleMinute10" -> CronScheduleProperty("0 10 * * * ?"))),
      ProcessVersion.empty.copy(processName = processName), "{}").futureValue

    val processScheduled = service.getNextScheduledDeployment(processName).futureValue.get

    processScheduled.scheduleName shouldBe Some("scheduleMinute5")
    processScheduled.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))

    currentTime = timeToTrigger
    val toDeploy = service.findToBeDeployed.futureValue

    toDeploy should have length 2
    toDeploy.head.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))
    toDeploy.head.scheduleName shouldBe Some("scheduleMinute5")
    toDeploy.last.runAt shouldBe localTime(expectedScheduleTime.plus(10, ChronoUnit.MINUTES))
    toDeploy.last.scheduleName shouldBe Some("scheduleMinute10")

    service.deactivate(processName).futureValue
    service.getNextScheduledDeployment(processName).futureValue shouldBe None
  }

  test("wait until other schedule finishes, before deploying next schedule") {
    val timeToTrigger = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    val f = new Fixture
    def service = f.periodicProcessService(currentTime)


    service.schedule(MultipleScheduleProperty(Map(
      "schedule1" -> CronScheduleProperty("0 5 * * * ?"),
      "schedule2" -> CronScheduleProperty("0 5 * * * ?"))),
      ProcessVersion.empty.copy(processName = processName), "{}").futureValue

    currentTime = timeToTrigger

    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 2

    service.deploy(toDeploy.head)
    f.delegateProcessManagerStub.setStateStatus(RunningStateStatus("running"))

    val toDeployAfterDeploy = service.findToBeDeployed.futureValue
    toDeployAfterDeploy should have length 0

    f.delegateProcessManagerStub.setStateStatus(FinishedStateStatus("finished"))
    service.handleFinished.futureValue

    val toDeployAfterFinish = service.findToBeDeployed.futureValue
    toDeployAfterFinish should have length 1
    toDeployAfterFinish.head.scheduleName shouldBe Some("schedule2")
  }

  test("Should handle failed event handler") {
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)

    var currentTime = startTime

    val f = new Fixture
    def service = f.periodicProcessService(currentTime)

    def tryWithFailedListener[T](action: () => Future[T]): T = {
      f.failListener = true
      intercept[TestFailedException](action().futureValue).getCause shouldBe a[PeriodicProcessException]
      f.failListener = false
      action().futureValue
    }

    tryWithFailedListener {
      () => service.schedule(cron, ProcessVersion.empty.copy(processName = processName), "{}")
    }

    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 1
    service.deploy(toDeploy.head).futureValue
    f.delegateProcessManagerStub.setStateStatus(FinishedStateStatus("running"))

    tryWithFailedListener {
      () => service.deactivate(processName)
    }

  }
}
