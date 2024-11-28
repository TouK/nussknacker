package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{
  PeriodicProcessDeployment,
  PeriodicProcessDeploymentStatus
}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessActionId, ProcessingTypeActionServiceStub}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.PeriodicProcessService.PeriodicProcessStatus
import pl.touk.nussknacker.engine.common.periodic._
import pl.touk.nussknacker.engine.common.periodic.service.ProcessConfigEnricher.EnrichedProcessConfig
import pl.touk.nussknacker.engine.common.periodic.service.{
  AdditionalDeploymentDataProvider,
  DeployedEvent,
  FailedOnDeployEvent,
  FailedOnRunEvent,
  FinishedEvent,
  PeriodicProcessEvent,
  PeriodicProcessListener,
  ProcessConfigEnricher,
  ScheduledEvent
}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.engine.management.periodic.flink.db.InMemPeriodicProcessesManager
import pl.touk.nussknacker.engine.management.periodic.flink.db.InMemPeriodicProcessesManager.createPeriodicProcessDeployment

import java.time.temporal.ChronoField
import java.time.{Clock, LocalDate, LocalDateTime}
import java.util.UUID
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class PeriodicProcessServiceTest
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with ScalaFutures
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  import org.scalatest.LoneElement._

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private val processName  = ProcessName("test")
  private val yearNow      = LocalDate.now().get(ChronoField.YEAR)
  private val cronInFuture = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow + 1}")
  private val cronInPast   = CronScheduleProperty(s"0 0 6 6 9 ? ${yearNow - 1}")

  private val canonicalProcess = ScenarioBuilder
    .streaming(processName.value)
    .source("start", "source")
    .emptySink("end", "KafkaSink")

  private val processVersion = ProcessVersion(
    versionId = VersionId(1),
    processName = processName,
    processId = ProcessId(1),
    labels = List.empty,
    user = "testUser",
    modelVersion = None,
  )

  class Fixture {
    val manager                       = new InMemPeriodicProcessesManager(processingType = "testProcessingType")
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val periodicDeploymentHandlerStub = new PeriodicDeploymentHandlerStub
    val events                        = new ArrayBuffer[PeriodicProcessEvent]()
    val additionalData                = Map("testMap" -> "testValue")

    val actionService: ProcessingTypeActionServiceStub = new ProcessingTypeActionServiceStub

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      periodicDeploymentHandler = periodicDeploymentHandlerStub,
      periodicProcessesManager = manager,
      new PeriodicProcessListener {

        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = { case k =>
          events.append(k)
        }

      },
      additionalDeploymentDataProvider = new AdditionalDeploymentDataProvider {

        override def prepareAdditionalData(
            runDetails: PeriodicProcessDeployment
        ): Map[String, String] =
          additionalData + ("runId" -> runDetails.id.value.toString)

      },
      DeploymentRetryConfig(),
      PeriodicExecutionConfig(),
      new ProcessConfigEnricher {

        override def onInitialSchedule(
            initialScheduleData: ProcessConfigEnricher.InitialScheduleData
        ): Future[ProcessConfigEnricher.EnrichedProcessConfig] = {
          Future.successful(
            EnrichedProcessConfig(
              initialScheduleData.inputConfigDuringExecution.withValue(
                "processName",
                ConfigValueFactory.fromAnyRef("testProcessName")
              )
            )
          )
        }

        override def onDeploy(
            deployData: ProcessConfigEnricher.DeployData
        ): Future[ProcessConfigEnricher.EnrichedProcessConfig] = {
          Future.successful(
            EnrichedProcessConfig(
              deployData.inputConfigDuringExecution
                .withValue("runAt", ConfigValueFactory.fromAnyRef(deployData.deployment.runAt.toString))
            )
          )
        }

      },
      Clock.systemDefaultZone(),
      actionService,
      Map.empty,
    )

  }

  test("findToBeDeployed - should return scheduled and to be retried scenarios") {
    val fWithNoRetries = new Fixture
    fWithNoRetries.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.FailedOnDeploy,
      deployMaxRetries = 0
    )
    val scheduledId1 = fWithNoRetries.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.Scheduled,
      deployMaxRetries = 0
    )
    fWithNoRetries.periodicProcessService.findToBeDeployed.futureValue.map(_.id) shouldBe List(scheduledId1)

    val fWithRetries = new Fixture
    val failedId2 = fWithRetries.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.FailedOnDeploy,
      deployMaxRetries = 1
    )
    val scheduledId2 = fWithRetries.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.Scheduled,
      deployMaxRetries = 1
    )
    fWithRetries.periodicProcessService.findToBeDeployed.futureValue.map(_.id) shouldBe List(scheduledId2, failedId2)
  }

  test("findToBeDeployed - should not return scenarios with different processing type") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled, processingType = "other")

    f.periodicProcessService.findToBeDeployed.futureValue shouldBe Symbol("empty")
  }

  // Flink job could disappear from Flink console.
  test("handleFinished - should reschedule scenario if Flink job is missing") {
    val f = new Fixture
    f.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.Deployed,
      runAt = LocalDateTime.now().minusMinutes(11),
      deployedAt = Some(LocalDateTime.now().minusMinutes(10))
    )

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    f.manager.deploymentEntities should have size 2
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Finished,
      PeriodicProcessDeploymentStatus.Scheduled
    )

    val finished :: scheduled :: Nil =
      f.manager.deploymentEntities.map(createPeriodicProcessDeployment(processEntity, _)).toList
    f.events.toList shouldBe List(FinishedEvent(finished, None), ScheduledEvent(scheduled, firstSchedule = false))
  }

  // Flink job could not be available in Flink console if checked too quickly after submit.
  test("handleFinished - shouldn't reschedule scenario if Flink job is missing but not deployed for long enough") {
    val f = new Fixture
    f.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.Deployed,
      runAt = LocalDateTime.now().minusSeconds(11),
      deployedAt = Some(LocalDateTime.now().minusSeconds(10))
    )

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    f.manager.deploymentEntities should have size 1
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Deployed
    )

    val deployed :: Nil =
      f.manager.deploymentEntities.map(createPeriodicProcessDeployment(processEntity, _)).toList
    f.events.toList shouldBe List.empty
  }

  test("handleFinished - should reschedule for finished Flink job") {
    val f               = new Fixture
    val processActionId = randomProcessActionId
    val deploymentId =
      f.manager.addActiveProcess(
        processName,
        PeriodicProcessDeploymentStatus.Deployed,
        processActionId = Some(processActionId)
      )
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))

    f.periodicProcessService.handleFinished.futureValue

    f.actionService.sentActionIds shouldBe Nil

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    f.manager.deploymentEntities should have size 2
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Finished,
      PeriodicProcessDeploymentStatus.Scheduled
    )

    val finished :: scheduled :: Nil =
      f.manager.deploymentEntities.map(createPeriodicProcessDeployment(processEntity, _)).toList
    f.events.toList shouldBe List(
      FinishedEvent(finished, f.delegateDeploymentManagerStub.jobStatus),
      ScheduledEvent(scheduled, firstSchedule = false)
    )
  }

  test("handleFinished - should not mark ExecutionFinished process during deploy") {
    val f               = new Fixture
    val processActionId = randomProcessActionId
    val deploymentId =
      f.manager.addActiveProcess(
        processName,
        PeriodicProcessDeploymentStatus.Deployed,
        processActionId = Some(processActionId)
      )
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.DuringDeploy, Some(deploymentId))

    f.periodicProcessService.handleFinished.futureValue

    f.actionService.sentActionIds shouldBe Nil

  }

  test("handleFinished - should deactivate process if there are no future schedules") {
    val f               = new Fixture
    val processActionId = randomProcessActionId
    val deploymentId = f.manager.addActiveProcess(
      processName,
      PeriodicProcessDeploymentStatus.Deployed,
      scheduleProperty = cronInPast,
      processActionId = Some(processActionId)
    )
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))

    f.periodicProcessService.handleFinished.futureValue

    f.actionService.sentActionIds shouldBe List(processActionId)

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe false
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Finished
    // TODO: active should be false
    val event =
      createPeriodicProcessDeployment(
        processEntity.copy(active = true),
        f.manager.deploymentEntities.loneElement,
      )
    f.events.loneElement shouldBe FinishedEvent(event, f.delegateDeploymentManagerStub.jobStatus)
  }

  test("handleFinished - should not deactivate process if there is future schedule") {
    val f                 = new Fixture
    val scheduleProperty  = MultipleScheduleProperty(Map("schedule1" -> cronInPast, "schedule2" -> cronInFuture))
    val periodicProcessId = f.manager.addOnlyProcess(processName, scheduleProperty)
    f.manager.addOnlyDeployment(
      periodicProcessId,
      status = PeriodicProcessDeploymentStatus.Deployed,
      scheduleName = Some("schedule1"),
      deployedAt = Some(LocalDateTime.now().minusMinutes(10))
    )
    f.manager.addOnlyDeployment(
      periodicProcessId,
      status = PeriodicProcessDeploymentStatus.Scheduled,
      scheduleName = Some("schedule2")
    )

    f.periodicProcessService.handleFinished.futureValue

    f.actionService.sentActionIds shouldBe Nil

    f.manager.processEntities.loneElement.active shouldBe true
    f.manager.deploymentEntities.map(
      _.status
    ) should contain only (PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("handleFinished - should not reschedule scenario with different processing type") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed, processingType = "other")

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    f.manager.deploymentEntities should have size 1
    f.manager.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Deployed)
    f.events.toList shouldBe Symbol("empty")
  }

  test("handle first schedule") {
    val f = new Fixture

    f.periodicProcessService
      .schedule(CronScheduleProperty("0 0 * * * ?"), processVersion, randomProcessActionId)
      .futureValue

    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    ConfigFactory
      .parseString(processEntity.inputConfigDuringExecutionJson)
      .getString("processName") shouldBe processName.value
    val deploymentEntity = f.manager.deploymentEntities.loneElement
    deploymentEntity.status shouldBe PeriodicProcessDeploymentStatus.Scheduled

    f.events.toList shouldBe List(
      ScheduledEvent(
        createPeriodicProcessDeployment(processEntity, deploymentEntity),
        firstSchedule = true
      )
    )
  }

  test("handleFinished - should mark as failed for failed Flink job") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    f.periodicProcessService.handleFinished.futureValue

    // Process is still active and has to be manually canceled by user.
    val processEntity = f.manager.processEntities.loneElement
    processEntity.active shouldBe true
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    val expectedDetails =
      createPeriodicProcessDeployment(processEntity, f.manager.deploymentEntities.head)
    f.events.toList shouldBe List(FailedOnRunEvent(expectedDetails, f.delegateDeploymentManagerStub.jobStatus))
  }

  test("deploy - should deploy and mark as so") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    val toSchedule = createPeriodicProcessDeployment(
      f.manager.processEntities.loneElement,
      f.manager.deploymentEntities.loneElement,
    )

    f.periodicProcessService.deploy(toSchedule).futureValue

    val deploymentEntity = f.manager.deploymentEntities.loneElement
    deploymentEntity.status shouldBe PeriodicProcessDeploymentStatus.Deployed
    ConfigFactory
      .parseString(f.periodicDeploymentHandlerStub.lastDeploymentWithRuntimeParams.value.inputConfigDuringExecutionJson)
      .getString("runAt") shouldBe deploymentEntity.runAt.toString

    val expectedDetails =
      createPeriodicProcessDeployment(f.manager.processEntities.loneElement, deploymentEntity)
    f.events.toList shouldBe List(DeployedEvent(expectedDetails, None))

  }

  test("deploy - should handle failed deployment") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.periodicDeploymentHandlerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    val toSchedule = createPeriodicProcessDeployment(
      f.manager.processEntities.loneElement,
      f.manager.deploymentEntities.loneElement,
    )

    f.periodicProcessService.deploy(toSchedule).futureValue

    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.FailedOnDeploy

    val expectedDetails = createPeriodicProcessDeployment(
      f.manager.processEntities.loneElement,
      f.manager.deploymentEntities.loneElement,
    )
    f.events.toList shouldBe List(FailedOnDeployEvent(expectedDetails, None))
  }

  test("Schedule new scenario only if at least one date in the future") {
    val f = new Fixture

    def tryToSchedule(schedule: ScheduleProperty): Unit =
      f.periodicProcessService
        .schedule(schedule, processVersion, randomProcessActionId)
        .futureValue

    tryToSchedule(cronInFuture) shouldBe (())
    tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInFuture, "s2" -> cronInPast))) shouldBe (())

    intercept[TestFailedException](tryToSchedule(cronInPast)).getCause shouldBe a[PeriodicProcessException]
    intercept[TestFailedException](
      tryToSchedule(MultipleScheduleProperty(Map("s1" -> cronInPast, "s2" -> cronInPast)))
    ).getCause shouldBe a[PeriodicProcessException]
  }

  test("pickMostImportantActiveDeployment - should return correct deployment for multiple schedules") {
    val schedules @ (schedule1 :: schedule2 :: Nil) = List("schedule1", "schedule2")
    val table = Table(
      ("statuses", "expected status", "expected schedule name"),
      (
        List(PeriodicProcessDeploymentStatus.Scheduled, PeriodicProcessDeploymentStatus.Scheduled),
        PeriodicProcessDeploymentStatus.Scheduled,
        schedule1
      ),
      (
        List(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.Scheduled),
        PeriodicProcessDeploymentStatus.Deployed,
        schedule1
      ),
      (
        List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled),
        PeriodicProcessDeploymentStatus.Scheduled,
        schedule2
      ),
      (
        List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Finished),
        PeriodicProcessDeploymentStatus.Finished,
        schedule2
      ),
      (
        List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Scheduled),
        PeriodicProcessDeploymentStatus.Failed,
        schedule1
      ),
      (
        List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Deployed),
        PeriodicProcessDeploymentStatus.Deployed,
        schedule2
      ),
      (
        List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Finished),
        PeriodicProcessDeploymentStatus.Failed,
        schedule1
      ),
      (
        List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Failed),
        PeriodicProcessDeploymentStatus.Failed,
        schedule2
      ),
    )

    forAll(table) {
      (
          statuses: List[PeriodicProcessDeploymentStatus],
          expectedStatus: PeriodicProcessDeploymentStatus,
          expectedScheduleName: String
      ) =>
        val f                 = new Fixture
        val now               = LocalDateTime.now()
        val scheduleProperty  = MultipleScheduleProperty(Map("schedule1" -> cronInPast, "schedule2" -> cronInFuture))
        val periodicProcessId = f.manager.addOnlyProcess(processName, scheduleProperty)
        statuses.zip(schedules).zipWithIndex.foreach { case ((status, schedule), index) =>
          f.manager.addOnlyDeployment(
            periodicProcessId,
            status = status,
            runAt = now.plusDays(index),
            scheduleName = Some(schedule)
          )
        }

        val activeSchedules = f.periodicProcessService.getLatestDeploymentsForActiveSchedules(processName).futureValue
        activeSchedules should have size (schedules.size)

        val deployment = f.periodicProcessService
          .getStatusDetails(processName)
          .futureValue
          .value
          .status
          .asInstanceOf[PeriodicProcessStatus]
          .pickMostImportantActiveDeployment
          .value

        deployment.status shouldBe expectedStatus
        deployment.scheduleName.value shouldBe Some(expectedScheduleName)
    }
  }

  private def randomProcessActionId = ProcessActionId(UUID.randomUUID())
}
