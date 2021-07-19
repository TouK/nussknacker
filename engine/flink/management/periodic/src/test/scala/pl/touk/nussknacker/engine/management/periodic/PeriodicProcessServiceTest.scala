package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeployment, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, DeployedEvent, FailedEvent, FinishedEvent, PeriodicProcessEvent, PeriodicProcessListener, ScheduledEvent}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.temporal.{ChronoField, ChronoUnit, TemporalField}
import java.time.{Clock, Instant, LocalDate}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class PeriodicProcessServiceTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  import org.scalatest.LoneElement._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")

  class Fixture {
    val repository = new db.InMemPeriodicProcessesRepository
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub = new JarManagerStub
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    val additionalData = Map("testMap" -> "testValue")
    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = repository,
      new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = { case k => events.append(k) }
      },
      new AdditionalDeploymentDataProvider {
        override def prepareAdditionalData(runDetails: PeriodicProcessDeployment): Map[String, String] =
          additionalData + ("runId" -> runDetails.id.value.toString)
      }, Clock.systemDefaultZone()
    )
  }

  // Flink job could disappear from Flink console.
  test("handleFinished - should reschedule scenario if Flink job is missing") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)

    val finished :: scheduled :: Nil = f.repository.deploymentEntities.map(createPeriodicProcessDeployment(processEntity, _)).toList
    f.events.toList shouldBe List(FinishedEvent(finished, None), ScheduledEvent(scheduled, firstSchedule = false))
  }

  test("handleFinished - should reschedule for finished Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(FlinkStateStatus.Finished)

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)

    val finished :: scheduled :: Nil = f.repository.deploymentEntities.map(createPeriodicProcessDeployment(processEntity, _)).toList
    f.events.toList shouldBe List(FinishedEvent(finished, f.delegateDeploymentManagerStub.jobStatus), ScheduledEvent(scheduled, firstSchedule = false))
  }

  test("handle first schedule") {
    val f = new Fixture

    f.periodicProcessService.schedule(CronScheduleProperty("0 0 * * * ?"), ProcessVersion.empty, "{}").futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    val deploymentEntity = f.repository.deploymentEntities.loneElement
    deploymentEntity.status shouldBe PeriodicProcessDeploymentStatus.Scheduled

    f.events.toList shouldBe List(ScheduledEvent(createPeriodicProcessDeployment(processEntity, deploymentEntity), firstSchedule = true))
  }


  test("handleFinished - should mark as failed for failed Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(FlinkStateStatus.Failed)

    f.periodicProcessService.handleFinished.futureValue

    // Process is still active and has to be manually canceled by user.
    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    val expectedDetails = createPeriodicProcessDeployment(processEntity, f.repository.deploymentEntities.head)
    f.events.toList shouldBe List(FailedEvent(expectedDetails, f.delegateDeploymentManagerStub.jobStatus))
  }

  test("deploy - should deploy and mark as so") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    val toSchedule = createPeriodicProcessDeployment(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)

    f.periodicProcessService.deploy(toSchedule).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Deployed

    val expectedDetails = createPeriodicProcessDeployment(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)
    f.events.toList shouldBe List(DeployedEvent(expectedDetails, None))

  }

  test("deploy - should handle failed deployment") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    val toSchedule = createPeriodicProcessDeployment(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)

    f.periodicProcessService.deploy(toSchedule).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    val expectedDetails = createPeriodicProcessDeployment(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)
    f.events.toList shouldBe List(FailedEvent(expectedDetails, None))
  }

  test("Schedule new scenario only if at least one date in the future") {
    val f = new Fixture
    val yearNow = LocalDate.now().get(ChronoField.YEAR)
    val cronInFuture = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow + 1}")
    val cronInPast = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow - 1}")

    def tryToSchedule(schedule: ScheduleProperty): Unit = f.periodicProcessService.schedule(schedule, ProcessVersion.empty, "{}").futureValue

    tryToSchedule(cronInFuture) shouldBe (())
    tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInFuture, "s2" -> cronInPast))) shouldBe (())

    intercept[TestFailedException](tryToSchedule(cronInPast)).getCause shouldBe a[PeriodicProcessException]
    intercept[TestFailedException](tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInPast, "s2" -> cronInPast)))).getCause shouldBe a[PeriodicProcessException]
  }
}
