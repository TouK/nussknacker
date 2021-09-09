package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeployment, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service.ProcessConfigEnricher.EnrichedProcessConfig
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, DeployedEvent, FailedEvent, FinishedEvent, PeriodicProcessEvent, PeriodicProcessListener, ProcessConfigEnricher, ScheduledEvent}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.temporal.ChronoField
import java.time.{Clock, LocalDate, LocalDateTime}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class PeriodicProcessServiceTest extends FunSuite
  with Matchers
  with OptionValues
  with ScalaFutures
  with PatientScalaFutures
  with TableDrivenPropertyChecks {

  import org.scalatest.LoneElement._
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")
  private val yearNow = LocalDate.now().get(ChronoField.YEAR)
  private val cronInFuture = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow + 1}")
  private val cronInPast = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow - 1}")
  private val processJson = ProcessMarshaller.toJson(ProcessCanonizer.canonize(
    EspProcessBuilder
      .id(processName.value)
      .exceptionHandler()
      .source("start", "source")
      .sink("end", "#input", "KafkaSink")
  )).noSpaces

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
      },
      new ProcessConfigEnricher {
        override def onInitialSchedule(initialScheduleData: ProcessConfigEnricher.InitialScheduleData): Future[ProcessConfigEnricher.EnrichedProcessConfig] = {
          Future.successful(EnrichedProcessConfig(initialScheduleData.inputConfigDuringExecution.withValue("processName", ConfigValueFactory.fromAnyRef(initialScheduleData.canonicalProcess.metaData.id))))
        }

        override def onDeploy(deployData: ProcessConfigEnricher.DeployData): Future[ProcessConfigEnricher.EnrichedProcessConfig] = {
          Future.successful(EnrichedProcessConfig(deployData.inputConfigDuringExecution.withValue("runAt", ConfigValueFactory.fromAnyRef(deployData.deployment.runAt.toString))))
        }
      },
      Clock.systemDefaultZone()
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

  test("handleFinished - should deactivate process if there are no future schedules") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed, scheduleProperty = cronInPast)
    f.delegateDeploymentManagerStub.setStateStatus(FlinkStateStatus.Finished)

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Finished
    // TODO: active should be false
    val event = createPeriodicProcessDeployment(processEntity.copy(active = true), f.repository.deploymentEntities.loneElement)
    f.events.loneElement shouldBe FinishedEvent(event, f.delegateDeploymentManagerStub.jobStatus)
  }

  test("handleFinished - should not deactivate process if there is future schedule") {
    val f = new Fixture
    val scheduleProperty = MultipleScheduleProperty(Map("schedule1" -> cronInPast, "schedule2" -> cronInFuture))
    val periodicProcessId = f.repository.addOnlyProcess(processName, scheduleProperty)
    f.repository.addOnlyDeployment(periodicProcessId, status = PeriodicProcessDeploymentStatus.Deployed, scheduleName = Some("schedule1"))
    f.repository.addOnlyDeployment(periodicProcessId, status = PeriodicProcessDeploymentStatus.Scheduled, scheduleName = Some("schedule2"))

    f.periodicProcessService.handleFinished.futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.map(_.status) should contain only (PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("handle first schedule") {
    val f = new Fixture

    f.periodicProcessService.schedule(CronScheduleProperty("0 0 * * * ?"), ProcessVersion.empty, processJson).futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    ConfigFactory.parseString(processEntity.inputConfigDuringExecutionJson).getString("processName") shouldBe processName.value
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

    val deploymentEntity = f.repository.deploymentEntities.loneElement
    deploymentEntity.status shouldBe PeriodicProcessDeploymentStatus.Deployed
    ConfigFactory.parseString(f.jarManagerStub.lastDeploymentWithJarData.value.inputConfigDuringExecutionJson).getString("runAt") shouldBe deploymentEntity.runAt.toString

    val expectedDetails = createPeriodicProcessDeployment(f.repository.processEntities.loneElement, deploymentEntity)
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

    def tryToSchedule(schedule: ScheduleProperty): Unit = f.periodicProcessService.schedule(schedule, ProcessVersion.empty, processJson).futureValue

    tryToSchedule(cronInFuture) shouldBe (())
    tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInFuture, "s2" -> cronInPast))) shouldBe (())

    intercept[TestFailedException](tryToSchedule(cronInPast)).getCause shouldBe a[PeriodicProcessException]
    intercept[TestFailedException](tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInPast, "s2" -> cronInPast)))).getCause shouldBe a[PeriodicProcessException]
  }

  test("getLatestDeployment - should return correct deployment for multiple schedules") {
    val schedules@(schedule1 :: schedule2 :: Nil) = List("schedule1", "schedule2")
    val table = Table(
      ("statuses", "expected status", "expected schedule name"),
      (List(PeriodicProcessDeploymentStatus.Scheduled, PeriodicProcessDeploymentStatus.Scheduled), PeriodicProcessDeploymentStatus.Scheduled, schedule1),
      (List(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.Scheduled), PeriodicProcessDeploymentStatus.Deployed, schedule1),
      (List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled), PeriodicProcessDeploymentStatus.Scheduled, schedule2),
      (List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Finished), PeriodicProcessDeploymentStatus.Finished, schedule2),
      (List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Scheduled), PeriodicProcessDeploymentStatus.Failed, schedule1),
      (List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Deployed), PeriodicProcessDeploymentStatus.Deployed, schedule2),
      (List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Finished), PeriodicProcessDeploymentStatus.Failed, schedule1),
      (List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Failed), PeriodicProcessDeploymentStatus.Failed, schedule2),
    )

    forAll(table) { (statuses: List[PeriodicProcessDeploymentStatus], expectedStatus: PeriodicProcessDeploymentStatus, expectedScheduleName: String) =>
      val f = new Fixture
      val now = LocalDateTime.now()
      val scheduleProperty = MultipleScheduleProperty(Map("schedule1" -> cronInPast, "schedule2" -> cronInFuture))
      val periodicProcessId = f.repository.addOnlyProcess(processName, scheduleProperty)
      statuses.zip(schedules).zipWithIndex.foreach { case ((status, schedule), index) =>
        f.repository.addOnlyDeployment(periodicProcessId, status = status, runAt = now.plusDays(index), scheduleName = Some(schedule))
      }

      val deployment = f.periodicProcessService.getLatestDeployment(processName).futureValue.value

      deployment.state.status shouldBe expectedStatus
      deployment.scheduleName shouldBe Some(expectedScheduleName)
    }
  }
}
