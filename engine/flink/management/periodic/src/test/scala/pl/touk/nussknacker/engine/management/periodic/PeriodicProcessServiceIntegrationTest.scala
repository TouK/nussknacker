package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.HsqlProcessRepository
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.temporal.ChronoUnit
import java.time.{Clock, Duration, Instant, LocalDateTime, ZoneId}
import scala.collection.mutable.ArrayBuffer

//Integration test with in-memory hsql
class PeriodicProcessServiceIntegrationTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")

  //we truncate to millis, as HSQL stores with that precision...
  private def fixedClock(instant: Instant) =
    Clock.tick(Clock.fixed(instant, ZoneId.systemDefault()), Duration.ofMillis(1))

  //every hour
  private val cron = CronPeriodicProperty("0 0 * * * ?")

  private val clockForSchedule = fixedClock(Instant.now().minus(2, ChronoUnit.HOURS))

  //we use different clock for repository to be able to find process for deployment
  private val clockForRepository = fixedClock(Instant.now())

  class Fixture {
    val (repository, db) = HsqlProcessRepository.prepare(clockForRepository)
    val delegateProcessManagerStub = new ProcessManagerStub
    val jarManagerStub = new JarManagerStub
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    val periodicProcessService = new PeriodicProcessService(
      delegateProcessManager = delegateProcessManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = repository,
      new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = { case k => events.append(k) }
      }, DefaultAdditionalDeploymentDataProvider, clockForSchedule
    )
  }

  test("base flow test") {
    val f = new Fixture

    f.periodicProcessService.schedule(cron,
      ProcessVersion.empty.copy(processName = processName), "{}").futureValue

    val processScheduled = f.periodicProcessService.getScheduledRunDetails(processName).futureValue.get

    processScheduled.state shouldBe PeriodicProcessDeploymentState(None, None, PeriodicProcessDeploymentStatus.Scheduled)
    processScheduled.runAt shouldBe LocalDateTime.now(clockForSchedule).plusHours(1).truncatedTo(ChronoUnit.HOURS)

    val toDeploy = f.periodicProcessService.findToBeDeployed.futureValue.loneElement
    f.periodicProcessService.deploy(toDeploy).futureValue

    val processDeployed = f.periodicProcessService.getScheduledRunDetails(processName).futureValue.get
    processDeployed.id shouldBe processScheduled.id
    processDeployed.state shouldBe PeriodicProcessDeploymentState(Some(LocalDateTime.now(clockForRepository)), None, PeriodicProcessDeploymentStatus.Deployed)
    processDeployed.runAt shouldBe LocalDateTime.now(clockForSchedule).plusHours(1).truncatedTo(ChronoUnit.HOURS)

    f.periodicProcessService.deactivate(processName).futureValue
    f.periodicProcessService.getScheduledRunDetails(processName).futureValue shouldBe None
  }
}
