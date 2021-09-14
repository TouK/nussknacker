package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{FinishedStateStatus, RunningStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.HsqlProcessRepository
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.test.PatientScalaFutures
import java.time.temporal.ChronoUnit
import java.time.{Clock, Duration, Instant, LocalDateTime, ZoneOffset}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

//Integration test with in-memory hsql
class PeriodicProcessServiceIntegrationTest extends FunSuite
  with Matchers
  with OptionValues
  with ScalaFutures
  with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")

  private val startTime = Instant.parse("2021-04-06T13:18:00Z")

  //we truncate to millis, as HSQL stores with that precision...
  private def fixedClock(instant: Instant) =
    Clock.tick(Clock.fixed(instant, ZoneOffset.UTC), Duration.ofMillis(1))

  private def localTime(instant: Instant) = LocalDateTime.now(fixedClock(instant))

  private val cronEveryHour = CronScheduleProperty("0 0 * * * ?")

  class Fixture {
    val hsqlRepo: HsqlProcessRepository = HsqlProcessRepository.prepare
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub = new JarManagerStub
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    var failListener = false
    def periodicProcessService(currentTime: Instant) = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = hsqlRepo.forClock(fixedClock(currentTime)),
      new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = {
          case k if failListener => throw new Exception(s"$k was ordered to fail")
          case k => events.append(k)
        }
      },
      DefaultAdditionalDeploymentDataProvider,
      ProcessConfigEnricher.identity,
      fixedClock(currentTime)
    )
  }

  test("base flow test") {
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val (every30MinutesProcessName, cronEvery30Minutes) = (ProcessName("every30Minutes"), CronScheduleProperty("0 30 * * * ?"))
    val (every4HoursProcessName, cronEvery4Hours) = (ProcessName("every4Hours"), CronScheduleProperty("0 0 */4 * * ?"))

    var currentTime = startTime

    val f = new Fixture
    def service = f.periodicProcessService(currentTime)

    service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), "{}").futureValue
    service.schedule(cronEvery30Minutes, ProcessVersion.empty.copy(processName = every30MinutesProcessName), "{}")
    service.schedule(cronEvery4Hours, ProcessVersion.empty.copy(processName = every4HoursProcessName), "{}")

    val processScheduled = service.getLatestDeployment(processName).futureValue.get

    processScheduled.periodicProcess.processVersion.processName shouldBe processName
    processScheduled.state shouldBe PeriodicProcessDeploymentState(None, None, PeriodicProcessDeploymentStatus.Scheduled)
    processScheduled.runAt shouldBe localTime(expectedScheduleTime)

    currentTime = timeToTriggerCheck
    
    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy.map(_.periodicProcess.processVersion.processName) should contain only (processName, every30MinutesProcessName)
    val toDeploy = allToDeploy.find(_.periodicProcess.processVersion.processName == processName).value
    service.deploy(toDeploy).futureValue

    val processDeployed = service.getLatestDeployment(processName).futureValue.get
    processDeployed.id shouldBe processScheduled.id
    processDeployed.state shouldBe PeriodicProcessDeploymentState(Some(LocalDateTime.now(fixedClock(timeToTriggerCheck))), None, PeriodicProcessDeploymentStatus.Deployed)
    processDeployed.runAt shouldBe localTime(expectedScheduleTime)

    service.deactivate(processName).futureValue
    service.getLatestDeployment(processName).futureValue shouldBe None
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

    service.schedule(MultipleScheduleProperty(Map(
      // Same names but scheduled earlier and later.
      "scheduleMinute5" -> CronScheduleProperty("0 15 * * * ?"),
      "scheduleMinute10" -> CronScheduleProperty("0 1 * * * ?"))),
      ProcessVersion.empty.copy(processName = ProcessName("other")), "{}").futureValue

    val processScheduled = service.getLatestDeployment(processName).futureValue.get

    processScheduled.periodicProcess.processVersion.processName shouldBe processName
    processScheduled.scheduleName shouldBe Some("scheduleMinute5")
    processScheduled.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))

    currentTime = timeToTrigger

    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy should have length 4
    val toDeploy = allToDeploy.filter(_.periodicProcess.processVersion.processName == processName)
    toDeploy should have length 2
    toDeploy.head.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))
    toDeploy.head.scheduleName shouldBe Some("scheduleMinute5")
    toDeploy.last.runAt shouldBe localTime(expectedScheduleTime.plus(10, ChronoUnit.MINUTES))
    toDeploy.last.scheduleName shouldBe Some("scheduleMinute10")

    service.deactivate(processName).futureValue
    service.getLatestDeployment(processName).futureValue shouldBe None
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
    f.delegateDeploymentManagerStub.setStateStatus(RunningStateStatus("running"))

    val toDeployAfterDeploy = service.findToBeDeployed.futureValue
    toDeployAfterDeploy should have length 0

    f.delegateDeploymentManagerStub.setStateStatus(FinishedStateStatus("finished"))
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
      () => service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), "{}")
    }

    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 1
    service.deploy(toDeploy.head).futureValue
    f.delegateDeploymentManagerStub.setStateStatus(FinishedStateStatus("running"))

    tryWithFailedListener {
      () => service.deactivate(processName)
    }

  }
}
